<template>
  <div class="ks-root">
    <div class="ks-stack">
      <header class="ks-hero">
        <p class="ks-hero__kicker">Knowledge Universe</p>
        <h1 class="ks-hero__title">知识星空</h1>
        <p class="ks-hero__lead">
          专注<strong>学习路径拓扑</strong>：点击节点将<strong>运镜聚焦</strong>并打开右侧情报面板；Feed 流聚合书目、RAG 片段与学习路径。
          <router-link class="ks-hero__link" :to="{ name: 'ResourceMarketplace' }">资源广场</router-link>
          可按节点联动参考书目。
        </p>
      </header>

      <div class="ks-stage">
        <div class="ks-universe-panel">
          <KnowledgeUniverse
            learning-lab-from="knowledge-starry"
            interaction-mode="panel"
            :mock-data="graphMock"
            :synced-selection-id="syncedSelectionId"
            @node-click="onGraphNodeClick"
          />
        </div>

        <aside
          class="ks-glass-rail"
          :class="{ 'ks-glass-rail--open': panelOpen }"
          aria-label="知识点详情与内容流"
        >
          <div class="ks-glass-rail__surface">
            <div class="ks-glass-rail__scanline" aria-hidden="true" />

            <template v-if="selectedNode">
              <header class="ks-glass-rail__head">
                <div class="ks-glass-rail__head-top">
                  <span class="ks-glass-rail__chip">{{ domainLabel }}</span>
                  <button type="button" class="ks-glass-rail__close" title="关闭面板" @click="closeDetailPanel">
                    ✕
                  </button>
                </div>
                <h2 class="ks-glass-rail__title">{{ selectedNode.name }}</h2>
                <p class="ks-glass-rail__sub">{{ selectedNode.subtitle }}</p>

                <div class="ks-glass-rail__mastery">
                  <div class="ks-glass-rail__mastery-row">
                    <span class="ks-glass-rail__mastery-label">掌握度</span>
                    <span class="ks-glass-rail__mastery-val">{{ masteryPct }}%</span>
                  </div>
                  <div class="ks-glass-rail__mastery-track">
                    <div class="ks-glass-rail__mastery-fill" :style="{ width: `${masteryPct}%` }" />
                  </div>
                </div>

                <el-button type="primary" class="ks-glass-rail__lab" @click="enterLearningLab">
                  进入学习空间
                </el-button>
              </header>

              <div class="ks-glass-rail__feed">
                <KnowledgeContentFeed
                  embedded
                  :node-id="selectedNode.id"
                  :node-name="selectedNode.name"
                  :tenant-id="tenantId"
                  :user-level="userLevelStr"
                  :expert="feedExpert"
                />
              </div>
            </template>
          </div>
        </aside>
      </div>

      <footer class="ks-foot">
        <router-link class="ks-foot__cta" :to="{ name: 'ResourceMarketplace' }">
          在资源广场查看当前学习节点的参考书目 →
        </router-link>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'KnowledgeStarrySky' })

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import KnowledgeUniverse from '@/components/KnowledgeUniverse.vue'
import KnowledgeContentFeed from '@/components/KnowledgeContentFeed.vue'
import { KNOWLEDGE_MAP_LINKS, KNOWLEDGE_MAP_NODES } from '@/data/knowledgeMapGraph'
import type { KnowledgeMapNode } from '@/data/knowledgeMapGraph'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

type GraphNodePayload = KnowledgeMapNode & { status?: 'locked' | 'learning' | 'mastered' }

/** 与 KnowledgeUniverse 的 emit 对齐（group 等在默认图谱外可为空） */
type UniverseClickPayload = {
  node: {
    id: string
    name: string
    subtitle: string
    description: string
    category: string
    relatedTopics: string[]
    group?: string
    mastery?: number
    knowledgeLevel?: 1 | 2 | 3
    status?: 'locked' | 'learning' | 'mastered'
  }
}

const router = useRouter()
const learningStore = useGlobalLearningContextStore()

const graphMock = computed(() => ({
  nodes: KNOWLEDGE_MAP_NODES,
  links: KNOWLEDGE_MAP_LINKS,
}))

const selectedNode = ref<GraphNodePayload | null>(null)
const syncedSelectionId = computed(() => selectedNode.value?.id ?? null)
const panelOpen = computed(() => selectedNode.value != null)

const tenantId = ref(1)

function hashStatus(id: string): 'locked' | 'learning' | 'mastered' {
  let h = 2166136261
  for (let i = 0; i < id.length; i += 1) {
    h ^= id.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  const r = (h >>> 0) % 3
  if (r === 0) return 'locked'
  if (r === 1) return 'learning'
  return 'mastered'
}

function onGraphNodeClick(payload: UniverseClickPayload) {
  const n = payload.node as GraphNodePayload
  selectedNode.value = n
  const st = n.status ?? hashStatus(n.id)
  learningStore.setCurrentContext('知识星空 · 学习路径', {
    knowledgeNodeId: n.id,
    knowledgeNodeName: n.name,
    group: n.group,
    category: n.category,
    mastery: n.mastery,
    knowledgeLevel: n.knowledgeLevel,
    status: st,
    subtitle: n.subtitle,
  })
}

function closeDetailPanel() {
  selectedNode.value = null
  learningStore.clearCurrentContext()
}

function enterLearningLab() {
  const n = selectedNode.value
  if (!n) return
  const st = n.status ?? hashStatus(n.id)
  if (st === 'locked') {
    ElMessage.warning('请先完成前置知识的学习！')
    return
  }
  learningStore.patchContext({
    currentModule: '知识星空 · 学习路径',
    recentAction: `进入学习空间：${n.name}`,
    lastError: '',
    extraContext: {
      source: 'KnowledgeStarrySky',
      knowledgeNodeId: n.id,
      knowledgeNodeName: n.name,
      subtitle: n.subtitle,
      description: n.description,
      relatedTopics: n.relatedTopics,
    },
  })
  router.push({
    name: 'LearningLab',
    params: { nodeId: n.id },
    query: { title: n.name, from: 'knowledge-starry' },
  })
}

const masteryPct = computed(() =>
  selectedNode.value
    ? Math.round(Math.min(1, Math.max(0, selectedNode.value.mastery ?? 0)) * 100)
    : 0,
)

const domainLabel = computed(() => {
  const n = selectedNode.value
  if (!n) return ''
  return n.category || n.group || '未分类'
})

const userLevelStr = computed(() => learningStore.userLevel || 'beginner')
const feedExpert = computed(() => learningStore.userMode === 'expert')
</script>

<style scoped>
.ks-root {
  min-height: calc(100vh - 132px);
  margin: -20px -24px -24px;
  width: calc(100% + 48px);
  max-width: none;
  background:
    radial-gradient(ellipse 120% 80% at 10% -20%, rgba(99, 102, 241, 0.2), transparent 50%),
    radial-gradient(ellipse 90% 60% at 100% 0%, rgba(56, 189, 248, 0.1), transparent 45%),
    linear-gradient(168deg, #0b1020 0%, #111827 42%, #0f172a 100%);
  border-top: 1px solid rgba(71, 85, 105, 0.5);
  color: #e2e8f0;
}

.ks-stack {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 132px);
  padding: 20px 24px 24px;
}

.ks-hero {
  flex-shrink: 0;
  margin-bottom: 14px;
}

.ks-hero__kicker {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #818cf8;
}

.ks-hero__title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 800;
  color: #f8fafc;
  letter-spacing: -0.02em;
}

.ks-hero__lead {
  margin: 0;
  max-width: 920px;
  font-size: 13px;
  line-height: 1.55;
  color: #94a3b8;
}

.ks-hero__lead strong {
  color: #c4b5fd;
}

.ks-hero__link {
  color: #38bdf8;
  font-weight: 600;
  text-decoration: none;
}

.ks-hero__link:hover {
  text-decoration: underline;
}

/* 星图 + 侧栏：无缝科幻布局 */
.ks-stage {
  flex: 1;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  min-height: 400px;
  gap: 0;
  position: relative;
}

.ks-universe-panel {
  flex: 1;
  min-width: 0;
  min-height: 380px;
  border-radius: 20px 0 0 20px;
  overflow: hidden;
  position: relative;
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-right: none;
  box-shadow: 0 28px 64px rgba(0, 0, 0, 0.45);
}

.ks-universe-panel::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  pointer-events: none;
  background: linear-gradient(to top, rgba(15, 23, 42, 0.55), transparent);
  z-index: 1;
}

.ks-universe-panel :deep(.knowledge-universe) {
  width: 100%;
  height: 100% !important;
  min-height: 0 !important;
  border-radius: 20px 0 0 20px;
}

/* 毛玻璃悬浮轨：不遮盖左侧星野，仅贴合右缘 */
.ks-glass-rail {
  flex: 0 0 auto;
  width: 0;
  max-width: min(432px, 44vw);
  transition:
    width 0.5s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.35s ease;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
  border-radius: 0 20px 20px 0;
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-left: 1px solid rgba(56, 189, 248, 0.22);
  box-shadow:
    -12px 0 48px rgba(2, 6, 23, 0.5),
    inset 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.ks-glass-rail--open {
  width: min(432px, 44vw);
  opacity: 1;
  pointer-events: auto;
}

.ks-glass-rail__surface {
  position: relative;
  width: min(432px, 44vw);
  height: 100%;
  min-height: 380px;
  display: flex;
  flex-direction: column;
  padding: 18px 18px 20px;
  box-sizing: border-box;
  background: linear-gradient(
    165deg,
    rgba(15, 23, 42, 0.42) 0%,
    rgba(15, 23, 42, 0.28) 45%,
    rgba(30, 27, 75, 0.2) 100%
  );
  backdrop-filter: blur(18px) saturate(1.35);
  -webkit-backdrop-filter: blur(18px) saturate(1.35);
}

.ks-glass-rail__scanline {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(56, 189, 248, 0.03) 2px,
    rgba(56, 189, 248, 0.03) 4px
  );
  opacity: 0.5;
  border-radius: inherit;
}

.ks-glass-rail__head {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  margin-bottom: 14px;
}

.ks-glass-rail__head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.ks-glass-rail__chip {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #67e8f9;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid rgba(34, 211, 238, 0.35);
  background: rgba(6, 78, 59, 0.2);
  max-width: 70%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ks-glass-rail__close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(15, 23, 42, 0.45);
  color: #94a3b8;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.ks-glass-rail__close:hover {
  color: #f1f5f9;
  border-color: rgba(129, 140, 248, 0.55);
  background: rgba(49, 46, 129, 0.35);
}

.ks-glass-rail__title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #f8fafc;
  line-height: 1.25;
}

.ks-glass-rail__sub {
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.45;
  color: #94a3b8;
}

.ks-glass-rail__mastery {
  margin-bottom: 14px;
}

.ks-glass-rail__mastery-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
}

.ks-glass-rail__mastery-label {
  font-size: 11px;
  font-weight: 700;
  color: #818cf8;
  letter-spacing: 0.06em;
}

.ks-glass-rail__mastery-val {
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: #22d3ee;
}

.ks-glass-rail__mastery-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(51, 65, 85, 0.65);
  overflow: hidden;
}

.ks-glass-rail__mastery-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6366f1, #22d3ee, #a5f3fc);
  box-shadow: 0 0 12px rgba(34, 211, 238, 0.45);
  transition: width 0.45s cubic-bezier(0.22, 1, 0.36, 1);
}

.ks-glass-rail__lab {
  width: 100%;
  font-weight: 700;
  border-radius: 12px;
  --el-button-bg-color: rgba(79, 70, 229, 0.95);
  --el-button-border-color: rgba(129, 140, 248, 0.65);
}

.ks-glass-rail__feed {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  margin-right: -4px;
}

.ks-glass-rail__feed::-webkit-scrollbar {
  width: 6px;
}

.ks-glass-rail__feed::-webkit-scrollbar-thumb {
  background: rgba(99, 102, 241, 0.35);
  border-radius: 999px;
}

.ks-foot {
  flex-shrink: 0;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(51, 65, 85, 0.6);
}

.ks-foot__cta {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: #a5b4fc;
  text-decoration: none;
  transition: color 0.15s ease;
}

.ks-foot__cta:hover {
  color: #c4b5fd;
}

@media (max-width: 960px) {
  .ks-stage {
    flex-direction: column;
  }

  .ks-universe-panel {
    border-radius: 20px 20px 0 0;
    border-right: 1px solid rgba(99, 102, 241, 0.35);
    min-height: 340px;
  }

  .ks-universe-panel :deep(.knowledge-universe) {
    border-radius: 20px 20px 0 0;
  }

  .ks-glass-rail {
    max-width: none;
    border-radius: 0 0 20px 20px;
    border-left: 1px solid rgba(99, 102, 241, 0.35);
    border-top: none;
  }

  .ks-glass-rail--open {
    width: 100%;
  }

  .ks-glass-rail__surface {
    width: 100%;
    min-height: 280px;
  }
}

@media (max-width: 768px) {
  .ks-root {
    margin: -16px -16px -20px;
    width: calc(100% + 32px);
  }

  .ks-stack {
    padding: 16px;
  }
}
</style>
