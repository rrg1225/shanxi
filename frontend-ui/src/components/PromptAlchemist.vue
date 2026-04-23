<template>
  <div class="prompt-alchemist">
    <aside class="control-panel">
      <h2>实验一：LLM生成原理与解码策略仿真</h2>

      <section class="prompt-market" aria-label="实验一模板库">
        <div class="market-head">
          <div class="market-head__titles">
            <div class="market-title">实验一模板库</div>
            <div class="market-subtitle">搜索、分类与热门排序 · 一键填入调试区</div>
          </div>
        </div>

        <el-input
          v-model="gallerySearch"
          class="market-search"
          clearable
          placeholder="搜索标题或标签，如：Spring Boot、数学建模…"
          :prefix-icon="Search"
        />

        <div class="sort-tabs" role="tablist" aria-label="排序方式">
          <button
            type="button"
            role="tab"
            class="sort-tab"
            :class="{ 'sort-tab--active': gallerySort === 'hot' }"
            :aria-selected="gallerySort === 'hot'"
            @click="gallerySort = 'hot'"
          >
            🔥 热门推荐
          </button>
          <button
            type="button"
            role="tab"
            class="sort-tab"
            :class="{ 'sort-tab--active': gallerySort === 'recent' }"
            :aria-selected="gallerySort === 'recent'"
            @click="gallerySort = 'recent'"
          >
            🕒 最近使用
          </button>
        </div>

        <div class="filter-tags" role="group" aria-label="分类筛选">
          <button
            v-for="key in galleryFilterKeys"
            :key="key"
            type="button"
            class="filter-tag"
            :class="{ 'filter-tag--active': galleryFilter === key }"
            @click="galleryFilter = key"
          >
            {{ key }}
          </button>
        </div>

        <div class="gallery-scroll">
          <div v-if="displayedGallery.length === 0" class="gallery-empty">没有匹配的模板，试试换个关键词或分类</div>
          <button
            v-for="item in displayedGallery"
            :key="item.id"
            type="button"
            class="gallery-card"
            @click="applyGalleryItem(item)"
          >
            <div class="gallery-card__top">
              <div class="gallery-card__title-row">
                <span v-if="item.pinned" class="pin-badge" title="置顶">📌</span>
                <span class="gallery-card__title">{{ item.title }}</span>
              </div>
              <div class="gallery-card__tags">
                <span v-for="tag in item.tags" :key="`${item.id}-${tag}`" class="tag-chip">{{ tag }}</span>
              </div>
            </div>
            <div class="gallery-card__meta">
              <span class="heat" :class="heatClass(item.useCount)">
                <span class="heat__icon">🔥</span>
                <span class="heat__val">{{ item.useCount }}</span>
                <span class="heat__label">次使用</span>
              </span>
              <span class="recent-label">{{ formatRelativeTime(item.lastUsedTime) }}</span>
            </div>
          </button>
        </div>
      </section>

      <el-form label-position="top">
        <el-form-item label="Prompt 调试区">
          <el-input
            v-model="prompt"
            type="textarea"
            :rows="8"
            placeholder="从左侧模板库点选卡片，或在此直接编辑 Prompt"
          />
        </el-form-item>

        <el-form-item label="Temperature">
          <el-slider
            v-model="temperature"
            :min="0"
            :max="2"
            :step="0.1"
            show-input
          />
        </el-form-item>

        <el-form-item label="Top-P">
          <el-slider
            v-model="topP"
            :min="0"
            :max="1"
            :step="0.05"
            show-input
          />
        </el-form-item>

        <div class="actions">
          <el-button type="primary" :loading="loading" @click="fetchProbabilityTree">
            生成概率树
          </el-button>
          <el-button @click="loadMockData">加载 Mock</el-button>
        </div>
      </el-form>
    </aside>

    <section class="tree-panel">
      <div class="tree-toolbar">
        <div class="tree-title-group">
          <span class="tree-title">词汇预测概率树</span>
          <span class="tree-subtitle">从左到右：主语义 -> 分支候选 -> 细粒度词汇</span>
        </div>
        <div class="tree-right">
          <span class="tree-meta" v-if="treeData">根节点：{{ treeData.token }}</span>
          <div class="legend">
            <span class="legend-dot hot"></span>
            <span class="legend-text">高概率</span>
            <span class="legend-dot cold"></span>
            <span class="legend-text">低概率</span>
          </div>
          <div class="zoom-actions">
            <button class="zoom-btn" @click="zoomOut">-</button>
            <button class="zoom-btn" @click="resetZoom">重置视图</button>
            <button class="zoom-btn" @click="zoomIn">+</button>
          </div>
        </div>
      </div>
      <svg ref="svgRef" class="tree-svg"></svg>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import * as d3 from 'd3'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { postExperimentLog } from '@/api/modules/experimentLog'
import { getMockTokenProbabilityTree } from '@/mocks/aiMocks'
import {
  createInitialPromptGallery,
  GALLERY_FILTER_KEYS,
  type GalleryFilterKey,
  type PromptGalleryItem,
} from '@/mocks/promptGallery'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

interface TokenTreeNode {
  token: string
  probability: number
  children?: TokenTreeNode[]
}

const svgRef = ref<SVGSVGElement | null>(null)
const props = defineProps<{
  tenantId: number
  userId: number
}>()
const learningStore = useGlobalLearningContextStore()
const { theme } = storeToRefs(learningStore)
const treeData = ref<TokenTreeNode | null>(null)
const prompt = ref('请拆解一个 RAG 实验流程')
const temperature = ref(0.7)
const topP = ref(0.9)
const loading = ref(false)

const galleryItems = ref<PromptGalleryItem[]>(createInitialPromptGallery())
const gallerySearch = ref('')
const gallerySort = ref<'hot' | 'recent'>('hot')
const galleryFilter = ref<GalleryFilterKey>('全部')
const galleryFilterKeys = GALLERY_FILTER_KEYS

const displayedGallery = computed(() => {
  const q = gallerySearch.value.trim().toLowerCase()
  let list = galleryItems.value.filter((item) => {
    if (galleryFilter.value !== '全部' && !item.tags.includes(galleryFilter.value)) return false
    if (!q) return true
    if (item.title.toLowerCase().includes(q)) return true
    return item.tags.some((t) => t.toLowerCase().includes(q))
  })

  list = [...list].sort((a, b) => {
    const pin = Number(!!b.pinned) - Number(!!a.pinned)
    if (pin !== 0) return pin
    if (gallerySort.value === 'hot') return b.useCount - a.useCount
    return b.lastUsedTime - a.lastUsedTime
  })

  return list
})

function heatClass(useCount: number) {
  if (useCount >= 400) return 'heat--high'
  if (useCount >= 250) return 'heat--mid'
  return 'heat--low'
}

function formatRelativeTime(ts: number) {
  const diff = Date.now() - ts
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr} 小时前`
  const day = Math.floor(hr / 24)
  if (day < 7) return `${day} 天前`
  return `${Math.floor(day / 7)} 周前`
}

let resizeObserver: ResizeObserver | null = null
let currentTransform: d3.ZoomTransform = d3.zoomIdentity
let zoomBehavior: d3.ZoomBehavior<SVGSVGElement, unknown> | null = null
let zoomSvgSelection: d3.Selection<SVGSVGElement, unknown, null, undefined> | null = null

let logTimer: number | undefined
async function logOp(opType: string, opPayload?: Record<string, unknown>) {
  try {
    await postExperimentLog({
      tenantId: props.tenantId,
      userId: props.userId,
      opType,
      opPayload,
      createdAtMillis: Date.now(),
    })
  } catch {
    // 日志埋点失败不影响页面功能
  }
}

watch([temperature, topP], () => {
  window.clearTimeout(logTimer)
  logTimer = window.setTimeout(() => {
    logOp('ADJUST_PARAM', {
      component: 'PromptAlchemist',
      temperature: temperature.value,
      top_p: topP.value,
    })
  }, 350)
  if (treeData.value) renderTree()
})

watch(temperature, () => {
  learningStore.onTemperatureAdjusted()
})

watch(theme, () => {
  if (treeData.value) {
    void nextTick(() => renderTree())
  }
})

async function fetchProbabilityTree() {
  loading.value = true
  let timeout: number | undefined
  const controller = new AbortController()
  try {
    logOp('RUN_PROB_TREE', {
      prompt: prompt.value,
      temperature: temperature.value,
      top_p: topP.value,
    })
    timeout = window.setTimeout(() => controller.abort(), 9000)

    // 约定后端返回：
    // {
    //   token: "ROOT",
    //   probability: 1,
    //   children: [
    //     { token: "RAG", probability: 0.42, children: [...] },
    //     { token: "知识库", probability: 0.21 }
    //   ]
    // }
    const response = await fetch('/api/ai/token-probability-tree', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        prompt: prompt.value,
        temperature: temperature.value,
        top_p: topP.value,
      }),
      signal: controller.signal,
    })

    window.clearTimeout(timeout)
    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`)
    }

    treeData.value = (await response.json()) as TokenTreeNode
    await nextTick()
    renderTree()
    learningStore.onProbabilityTreeSuccess()
    learningStore.recordActivity('prompt', 10)
    ElMessage.success({ message: '✨ 提示词实验成功！经验值 +10', grouping: true })
  } catch (error) {
    // 网络波动：无缝切换到静态 Mock 数据，确保演示不中断
    const msg = error instanceof Error ? error.message : '概率树加载失败'
    learningStore.onProbabilityTreeFailure(msg)
    treeData.value = getMockTokenProbabilityTree({
      prompt: prompt.value,
      temperature: temperature.value,
      top_p: topP.value,
    })
    await nextTick()
    renderTree()
    ElMessage.warning('概率树加载失败，已切换到 Mock 渲染')
  } finally {
    if (timeout !== undefined) window.clearTimeout(timeout)
    loading.value = false
  }
}

function applyGalleryItem(item: PromptGalleryItem) {
  prompt.value = item.content
  const row = galleryItems.value.find((x) => x.id === item.id)
  if (row) {
    row.useCount += 1
    row.lastUsedTime = Date.now()
  }

  logOp('APPLY_PROMPT_GALLERY', {
    component: 'PromptAlchemist',
    galleryId: item.id,
    galleryTitle: item.title,
    useCount: row?.useCount ?? item.useCount,
  })
  learningStore.patchContext({
    currentModule: '实验一：LLM生成原理与解码策略仿真',
    recentAction: `套用模板：${item.title}`,
    lastError: '',
    extraContext: {
      galleryId: item.id,
      galleryTitle: item.title,
      tags: item.tags,
    },
  })
  ElMessage.success(`已填入：${item.title}`)
}

function loadMockData() {
  logOp('LOAD_MOCK', {
    component: 'PromptAlchemist',
  })
  treeData.value = {
    token: 'ROOT',
    probability: 1,
    children: [
      {
        token: 'RAG',
        probability: 0.44,
        children: [
          { token: '检索', probability: 0.23 },
          { token: '增强', probability: 0.13 },
          { token: '知识库', probability: 0.08 },
        ],
      },
      {
        token: '实验',
        probability: 0.31,
        children: [
          { token: '流程', probability: 0.16 },
          { token: '设计', probability: 0.09 },
          { token: '节点', probability: 0.06 },
        ],
      },
      {
        token: 'AI',
        probability: 0.25,
        children: [
          { token: '拆解', probability: 0.12 },
          { token: '推理', probability: 0.08 },
          { token: '可视化', probability: 0.05 },
        ],
      },
    ],
  }
  nextTick(() => renderTree())
}

function renderTree() {
  const svgEl = svgRef.value
  const data = treeData.value
  if (!svgEl || !data) return

  const isDark =
    typeof document !== 'undefined' && document.documentElement.getAttribute('data-theme') === 'dark'
  const linkLo = isDark ? '#4d4d5c' : '#adc4ff'
  const linkHi = isDark ? '#10a37f' : '#7da2ff'
  const labelFill = isDark ? '#ececf1' : '#0f172a'
  const labelHalo = isDark ? '#202123' : '#ffffff'
  const pctFill = isDark ? '#c5c5d2' : '#334155'
  const badgeBg = isDark ? 'rgba(32, 33, 35, 0.88)' : 'rgba(15, 23, 42, 0.78)'
  const badgeStroke = isDark ? 'rgba(16, 163, 127, 0.35)' : 'rgba(255,255,255,0.35)'

  const width = svgEl.clientWidth || 980
  const height = svgEl.clientHeight || 640
  const margin = { top: 38, right: 96, bottom: 34, left: 86 }

  const svg = d3.select(svgEl)
  svg.selectAll('*').remove()
  svg.attr('viewBox', `0 0 ${width} ${height}`)
  svg.append('defs')
    .append('filter')
    .attr('id', 'nodeGlow')
    .append('feGaussianBlur')
    .attr('stdDeviation', 1.8)

  const root = d3.hierarchy<TokenTreeNode>(data)
  const treeLayout = d3.tree<TokenTreeNode>().size([
    height - margin.top - margin.bottom,
    width - margin.left - margin.right,
  ])

  const layoutRoot = treeLayout(root)

  const gViewport = svg.append('g').attr('class', 'tree-viewport')
  const g = gViewport
    .append('g')
    .attr('transform', `translate(${margin.left},${margin.top})`)

  const linkStroke = d3.scaleLinear<string>().domain([0, 2]).range([linkLo, linkHi])

  // 连线（轻渐变层次）
  g.append('g')
    .selectAll('path')
    .data(layoutRoot.links())
    .join('path')
    .attr('d', (d) =>
      d3
        .linkHorizontal<d3.HierarchyPointLink<TokenTreeNode>, d3.HierarchyPointNode<TokenTreeNode>>()
        .x((point) => point.y)
        .y((point) => point.x)(d) ?? '',
    )
    .attr('fill', 'none')
    .attr('stroke', (d) => linkStroke(d.source.depth))
    .attr('stroke-opacity', 0.7)
    .attr('stroke-linecap', 'round')
    .attr('stroke-width', (d) => Math.max(1.4, 2.8 - d.source.depth * 0.5))

  const maxProbability = d3.max(root.descendants(), (d) => d.data.probability) ?? 1
  const radiusScale = d3.scaleLinear().domain([0, maxProbability]).range([8, 22])
  const colorScale = d3
    .scaleSequential(d3.interpolateTurbo)
    .domain([0, maxProbability])

  const nodes = g
    .append('g')
    .selectAll('g')
    .data(layoutRoot.descendants())
    .join('g')
    .attr('transform', (d) => `translate(${d.y},${d.x})`)

  const keyNodes = layoutRoot.descendants().filter((d) => d.depth <= 1)

  nodes
    .append('circle')
    .attr('r', (d) => radiusScale(d.data.probability) + 4)
    .attr('fill', (d) => colorScale(d.data.probability))
    .attr('opacity', 0.14)
    .attr('filter', 'url(#nodeGlow)')

  nodes
    .append('circle')
    .attr('r', (d) => radiusScale(d.data.probability))
    .attr('fill', (d) => colorScale(d.data.probability))
    .attr('stroke', labelHalo)
    .attr('stroke-width', 1.8)

  nodes
    .append('text')
    .attr('x', (d) => (d.children && d.children.length > 0 ? 0 : radiusScale(d.data.probability) + 12))
    .attr('dy', (d) => (d.children && d.children.length > 0 ? -(radiusScale(d.data.probability) + 10) : 4))
    .attr('text-anchor', (d) => (d.children && d.children.length > 0 ? 'middle' : 'start'))
    .attr('font-size', (d) => (d.depth <= 1 ? 18 : 14))
    .attr('font-weight', 600)
    .attr('fill', labelFill)
    .attr('paint-order', 'stroke')
    .attr('stroke', labelHalo)
    .attr('stroke-width', 3)
    .attr('stroke-linejoin', 'round')
    .text((d) => d.data.token)

  // 叶子节点右侧补充百分比（轻量样式，避免拥挤）
  nodes
    .filter((d) => !d.children || d.children.length === 0)
    .append('text')
    .attr('x', (d) => radiusScale(d.data.probability) + 12)
    .attr('dy', 22)
    .attr('text-anchor', 'start')
    .attr('font-size', 11)
    .attr('font-weight', 700)
    .attr('fill', pctFill)
    .attr('paint-order', 'stroke')
    .attr('stroke', labelHalo)
    .attr('stroke-width', 2)
    .text((d) => `${(d.data.probability * 100).toFixed(1)}%`)

  // 只对关键层级展示百分比底板，避免叶子节点拥挤遮挡
  const keyNodeBadges = g
    .append('g')
    .selectAll('g')
    .data(keyNodes)
    .join('g')
    .attr('transform', (d) => `translate(${d.y},${d.x})`)

  keyNodeBadges
    .append('rect')
    .attr('x', -24)
    .attr('y', (d) => radiusScale(d.data.probability) + 8)
    .attr('width', 48)
    .attr('height', 18)
    .attr('rx', 9)
    .attr('fill', badgeBg)
    .attr('stroke', badgeStroke)
    .attr('stroke-width', 1)

  keyNodeBadges
    .append('text')
    .attr('dy', (d) => radiusScale(d.data.probability) + 21)
    .attr('text-anchor', 'middle')
    .attr('font-size', 11.5)
    .attr('font-weight', 700)
    .attr('fill', '#fff')
    .attr('paint-order', 'stroke')
    .attr('stroke', isDark ? 'rgba(0,0,0,0.4)' : 'rgba(15, 23, 42, 0.45)')
    .attr('stroke-width', 1)
    .text((d) => `${(d.data.probability * 100).toFixed(1)}%`)

  // 简单 tooltip
  nodes.append('title').text((d) => `${d.data.token}\n概率: ${(d.data.probability * 100).toFixed(2)}%`)

  // 支持拖拽平移 + 滚轮缩放
  zoomBehavior = d3
    .zoom<SVGSVGElement, unknown>()
    .scaleExtent([0.65, 2.8])
    .on('zoom', (event) => {
      currentTransform = event.transform
      gViewport.attr('transform', currentTransform.toString())
    })

  zoomSvgSelection = svg as d3.Selection<SVGSVGElement, unknown, null, undefined>
  zoomSvgSelection.call(zoomBehavior)
  zoomSvgSelection.call(zoomBehavior.transform, currentTransform)
}

function zoomIn() {
  if (!zoomSvgSelection || !zoomBehavior) return
  zoomSvgSelection.transition().duration(220).call(zoomBehavior.scaleBy, 1.15)
}

function zoomOut() {
  if (!zoomSvgSelection || !zoomBehavior) return
  zoomSvgSelection.transition().duration(220).call(zoomBehavior.scaleBy, 0.87)
}

function resetZoom() {
  if (!zoomSvgSelection || !zoomBehavior) return
  currentTransform = d3.zoomIdentity
  zoomSvgSelection.transition().duration(260).call(zoomBehavior.transform, d3.zoomIdentity)
}

function setupResizeObserver() {
  if (!svgRef.value) return
  resizeObserver = new ResizeObserver(() => renderTree())
  resizeObserver.observe(svgRef.value)
}

onMounted(() => {
  loadMockData()
  setupResizeObserver()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  zoomBehavior = null
  zoomSvgSelection = null
})

/** 供 PromptWorkbench 等父组件同步「上下文感知」导师状态 */
defineExpose({
  prompt,
  temperature,
  topP,
})
</script>

<style scoped>
.prompt-alchemist {
  display: grid;
  grid-template-columns: minmax(340px, 400px) 1fr;
  gap: 20px;
  min-height: 720px;
  align-items: stretch;
}

.control-panel,
.tree-panel {
  background: var(--pa-panel-bg);
  border: 1px solid var(--pa-panel-border);
  border-radius: 16px;
  box-shadow: var(--shadow-md);
  transition: background 0.25s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.control-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.control-panel h2 {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.prompt-market {
  margin: 8px 0 18px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--pa-market-border);
  background: var(--pa-market-bg);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.06) inset,
    var(--shadow-sm);
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.market-head {
  margin-bottom: 12px;
}

.market-title {
  font-size: 15px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.market-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.market-search {
  margin-bottom: 12px;
}

.market-search :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--pa-input-outline);
  transition: box-shadow 0.2s ease;
}

.market-search :deep(.el-input__wrapper:hover),
.market-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent-primary) inset;
}

.sort-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.sort-tab {
  flex: 1;
  border: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  border-radius: 12px;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    color 0.18s ease;
}

.sort-tab:hover {
  border-color: var(--border-strong);
  background: var(--bg-muted);
}

.sort-tab--active {
  border-color: var(--accent-primary);
  background: var(--accent-primary-soft);
  color: var(--accent-link);
  box-shadow: 0 6px 16px rgba(16, 163, 127, 0.15);
}

html[data-theme='light'] .sort-tab--active {
  color: #1d4ed8;
  box-shadow: 0 6px 16px rgb(59 130 246 / 12%);
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.filter-tag {
  border: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  padding: 5px 11px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.16s ease;
}

.filter-tag:hover {
  background: var(--bg-muted);
}

.filter-tag--active {
  background: var(--pa-chip-active-bg);
  border-color: var(--accent-primary);
  color: var(--pa-chip-active-fg);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
}

.gallery-scroll {
  flex: 1;
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
  scrollbar-gutter: stable;
}

.gallery-scroll::-webkit-scrollbar {
  width: 6px;
}
.gallery-scroll::-webkit-scrollbar-thumb {
  background: var(--text-muted);
  opacity: 0.5;
  border-radius: 999px;
}

.gallery-empty {
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
  padding: 28px 12px;
  border: 1px dashed var(--border-default);
  border-radius: 14px;
  background: var(--bg-muted);
}

.gallery-card {
  width: 100%;
  text-align: left;
  border: 1px solid var(--border-subtle);
  border-radius: 14px;
  padding: 14px 14px 12px;
  background: var(--bg-elevated);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
  box-shadow: var(--shadow-sm);
}

.gallery-card:hover {
  transform: translateY(-2px);
  border-color: var(--accent-primary);
  box-shadow:
    0 12px 28px rgba(16, 163, 127, 0.12),
    0 0 0 1px var(--accent-primary-soft);
}

.gallery-card:active {
  transform: translateY(0);
}

.gallery-card__title-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.pin-badge {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.5;
}

.gallery-card__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.45;
}

.gallery-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.tag-chip {
  font-size: 10px;
  font-weight: 600;
  text-transform: none;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--bg-muted);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
}

.gallery-card__meta {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.heat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgb(254 243 199 / 0.5);
  border: 1px solid rgb(253 224 71 / 0.35);
}

.heat--high {
  color: #b45309;
  background: linear-gradient(135deg, rgb(254 243 199 / 0.85), rgb(254 215 170 / 0.65));
}

.heat--mid {
  color: #c2410c;
}

.heat--low {
  color: #78716c;
  background: rgb(245 245 244 / 0.8);
  border-color: rgb(214 211 209 / 0.6);
}

.heat__icon {
  font-size: 12px;
}

.heat__label {
  font-weight: 600;
  opacity: 0.85;
}

.recent-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
  white-space: nowrap;
}

.tree-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 20px;
  border-bottom: 1px solid var(--pa-tree-toolbar-border);
}

.tree-title-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tree-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
}

.tree-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
}

.tree-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.tree-meta {
  font-size: 12px;
  color: var(--text-secondary);
}

.legend {
  display: flex;
  align-items: center;
  gap: 6px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-dot.hot {
  background: #ff6b00;
}

.legend-dot.cold {
  background: #ffd166;
}

.legend-text {
  font-size: 11px;
  color: var(--text-secondary);
}

.zoom-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.zoom-btn {
  border: 1px solid var(--border-default);
  background: var(--bg-elevated);
  color: var(--text-primary);
  border-radius: 8px;
  font-size: 12px;
  padding: 3px 8px;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.zoom-btn:hover {
  background: var(--bg-muted);
  border-color: var(--accent-primary);
}

.tree-svg {
  width: 100%;
  height: 100%;
  min-height: 640px;
  cursor: grab;
  background: var(--pa-tree-canvas-bg);
  transition: background 0.3s ease;
}

.tree-svg:active {
  cursor: grabbing;
}

.actions {
  display: flex;
  gap: 12px;
}

@media (max-width: 1200px) {
  .prompt-alchemist {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .tree-svg {
    min-height: 520px;
  }
}
</style>

