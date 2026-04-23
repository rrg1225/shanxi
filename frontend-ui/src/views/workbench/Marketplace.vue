<template>
  <div class="mplaza">
    <div class="mplaza__inner">
      <!-- 最近使用 -->
      <section class="mplaza-recent" aria-labelledby="recent-heading">
        <h2 id="recent-heading" class="mplaza-section-title">最近使用的资源</h2>
        <div v-if="recentItems.length === 0" class="mplaza-recent__empty">暂无记录，在下方卡片点击「一键使用」即可出现在此。</div>
        <el-scrollbar v-else class="mplaza-recent__scroll">
          <div class="mplaza-recent__track">
            <button
              v-for="r in recentItems"
              :key="`${r.kind}-${r.id}`"
              type="button"
              class="mplaza-recent-chip"
              @click="applyRecent(r)"
            >
              <span class="mplaza-recent-chip__glyph" aria-hidden="true">{{ kindGlyph(r.kind) }}</span>
              <span class="mplaza-recent-chip__text">{{ r.title }}</span>
            </button>
          </div>
        </el-scrollbar>
      </section>

      <!-- 搜索 + 分类 -->
      <div class="mplaza-toolbar">
        <el-input
          v-model="searchQuery"
          class="mplaza-search"
          placeholder="搜索书名、模板或蓝图关键词…"
          clearable
          :prefix-icon="Search"
        />
        <div class="mplaza-tags" role="group" aria-label="资源分类">
          <button
            v-for="cat in categoryOptions"
            :key="cat"
            type="button"
            class="mplaza-tag"
            :class="{ 'mplaza-tag--active': activeCategory === cat }"
            @click="activeCategory = cat"
          >
            {{ cat }}
          </button>
        </div>
      </div>

      <!-- Tabs + 网格 -->
      <el-tabs v-model="activeTab" class="mplaza-tabs">
        <el-tab-pane label="📚 专业书籍" name="books" />
        <el-tab-pane label="🪄 实验一：LLM生成原理与解码策略仿真" name="prompts" />
        <el-tab-pane label="🏗️ 实验二：知识注入与幻觉消除虚拟仿真" name="rag" />
      </el-tabs>

      <p v-if="vaultError && !isGridLoading" class="mplaza-hint" role="status">{{ vaultError }}</p>

      <Transition name="fade" mode="out-in">
        <div v-if="isGridLoading" key="skeleton" class="mplaza-state mplaza-state--skeleton">
          <div class="mplaza-skel-grid" aria-hidden="true">
            <div v-for="n in 6" :key="n" class="mplaza-skel-card">
              <el-skeleton animated>
                <template #template>
                  <el-skeleton-item variant="image" class="mplaza-skel-card__img" />
                  <div class="mplaza-skel-card__body">
                    <el-skeleton-item variant="h3" class="mplaza-skel-card__line mplaza-skel-card__line--title" />
                    <el-skeleton-item variant="text" class="mplaza-skel-card__line" />
                    <el-skeleton-item variant="text" class="mplaza-skel-card__line mplaza-skel-card__line--short" />
                  </div>
                </template>
              </el-skeleton>
            </div>
          </div>
        </div>

        <div
          v-else-if="!isLoading && !vaultLoading && filteredItems.length === 0"
          key="empty"
          class="mplaza-state mplaza-state--empty"
        >
          <el-empty description="这里还是知识的荒原，暂无相关资源">
            <el-button type="primary" round class="mplaza-empty-cta" @click="onContributeFirst">
              🚀 去贡献第一份资料
            </el-button>
          </el-empty>
        </div>

        <div v-else key="grid" class="mplaza-grid">
          <article v-for="item in filteredItems" :key="`${item.kind}-${item.id}`" class="mplaza-card">
            <div class="mplaza-card__shine" aria-hidden="true" />
            <div
              class="mplaza-card__cover"
              :style="{ '--hue': String(item.coverHue), '--glow': item.coverGlow }"
            >
              <span class="mplaza-card__glyph" aria-hidden="true">{{ item.coverGlyph }}</span>
              <span v-if="item.badge" class="mplaza-card__badge">{{ item.badge }}</span>
            </div>
            <div class="mplaza-card__body">
              <span class="mplaza-card__cat">{{ item.categoryTag }}</span>
              <h3 class="mplaza-card__title">{{ item.title }}</h3>
              <p class="mplaza-card__desc">{{ item.subtitle }}</p>
              <el-button type="primary" class="mplaza-card__cta" round @click="onUseOneClick(item)">
                一键使用
              </el-button>
            </div>
          </article>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ResourceMarketplace' })

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { fetchPublicVault, type PublicVaultItemDTO } from '@/api/modules/ai'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

type VaultKind = 'book' | 'prompt' | 'rag_blueprint'
type CategoryTag = '全部' | '编程' | '学习' | '求职'

interface VaultItem {
  id: string
  kind: VaultKind
  title: string
  subtitle: string
  categoryTag: CategoryTag
  coverHue: number
  coverGlow: string
  coverGlyph: string
  badge?: string
  payload?: string
}

const RECENT_STORAGE_KEY = 'resource-marketplace-recent-v1'
const RECENT_MAX = 12

const router = useRouter()
const learningStore = useGlobalLearningContextStore()

const searchQuery = ref('')
const activeCategory = ref<CategoryTag>('全部')
const activeTab = ref<'books' | 'prompts' | 'rag'>('books')
/** 模拟首屏网络延迟，与 vault 请求并行，用于骨架屏 */
const isLoading = ref(true)
const vaultLoading = ref(false)
const vaultError = ref('')
const items = ref<VaultItem[]>([])

const categoryOptions: CategoryTag[] = ['全部', '编程', '学习', '求职']

/** 8 条大模型场景 Mock，接口可用后由 {@link fetchPublicVault} 替换 */
const MOCK_VAULT: VaultItem[] = [
  {
    id: 'bk-rag-sys',
    kind: 'book',
    title: 'RAG 系统设计与工程实践',
    subtitle: '索引、召回、重排到评测闭环，适合搭建企业级检索增强流水线。',
    categoryTag: '学习',
    coverHue: 220,
    coverGlow: 'rgba(99, 102, 241, 0.45)',
    coverGlyph: '📘',
    badge: '精读',
  },
  {
    id: 'bk-prompt-craft',
    kind: 'book',
    title: '大模型提示工程精要',
    subtitle: 'Few-shot、角色约束与工具调用编排，写出可复现的高质量 Prompt。',
    categoryTag: '编程',
    coverHue: 265,
    coverGlow: 'rgba(167, 139, 250, 0.42)',
    coverGlyph: '✨',
    badge: '畅销',
  },
  {
    id: 'bk-llm-interview',
    kind: 'book',
    title: 'LLM 岗面试：原理与手写题',
    subtitle: 'Attention、KV Cache、RLHF 高频考点 + 白板推导与系统设计题。',
    categoryTag: '求职',
    coverHue: 32,
    coverGlow: 'rgba(251, 191, 36, 0.38)',
    coverGlyph: '🎯',
    badge: '求职',
  },
  {
    id: 'pt-cot',
    kind: 'prompt',
    title: 'CoT 分步推理 · 调试版',
    subtitle: '强制模型输出「假设—验证—结论」，降低跳步与幻觉，适合复杂问答。',
    categoryTag: '编程',
    coverHue: 195,
    coverGlow: 'rgba(56, 189, 248, 0.4)',
    coverGlyph: '🧩',
    payload:
      '请按以下步骤回答：① 重述问题并列出已知；② 逐步推理（每步一行）；③ 给出最终结论并标注不确定处。',
  },
  {
    id: 'pt-doc2story',
    kind: 'prompt',
    title: '技术文档 → 用户故事生成器',
    subtitle: '把 API/设计说明转产品语系的用户故事与验收标准，方便评审对齐。',
    categoryTag: '学习',
    coverHue: 160,
    coverGlow: 'rgba(45, 212, 191, 0.38)',
    coverGlyph: '📝',
    payload:
      '角色：资深产品经理。输入为技术文档片段。输出：用户故事（As a… I want… So that…）+ 验收标准列表。',
  },
  {
    id: 'pt-star-resume',
    kind: 'prompt',
    title: 'STAR 简历项目描述',
    subtitle: '把零散经历压成面试官爱听的 Situation–Task–Action–Result。',
    categoryTag: '求职',
    coverHue: 340,
    coverGlow: 'rgba(244, 114, 182, 0.35)',
    coverGlyph: '💼',
    payload:
      '根据我提供的项目要点，用 STAR 法则各写 2–3 句，突出量化结果；语气专业克制。',
  },
  {
    id: 'rag-paper-pipeline',
    kind: 'rag_blueprint',
    title: '论文精读 + 引用溯源流水线',
    subtitle: '切块引用、页码对齐与「回答必带来源」策略，适合文献问答场景。',
    categoryTag: '学习',
    coverHue: 210,
    coverGlow: 'rgba(129, 140, 248, 0.4)',
    coverGlyph: '🔗',
    badge: '蓝图',
  },
  {
    id: 'rag-code-copilot',
    kind: 'rag_blueprint',
    title: '代码库语义检索 Copilot',
    subtitle: 'Repo 向量化 + 符号边界切块 + 混合检索，支撑「按仓库问答」的 IDE 插件。',
    categoryTag: '编程',
    coverHue: 175,
    coverGlow: 'rgba(34, 211, 238, 0.38)',
    coverGlyph: '⚡',
    badge: '蓝图',
  },
]

interface RecentEntry {
  id: string
  kind: VaultKind
  title: string
  at: number
}

function normalizeKind(k: string | undefined): VaultKind | null {
  const u = String(k || '').toLowerCase()
  if (u === 'book' || u === 'books') return 'book'
  if (u === 'prompt' || u === 'prompts' || u === 'spell') return 'prompt'
  if (u === 'rag_blueprint' || u === 'rag' || u === 'blueprint') return 'rag_blueprint'
  return null
}

function normalizeCategory(s: string | undefined): CategoryTag {
  const t = String(s || '').trim()
  if (t === '编程' || t === '学习' || t === '求职') return t
  return '学习'
}

function dtoToVaultItem(row: PublicVaultItemDTO): VaultItem | null {
  const kind = normalizeKind(row.kind)
  if (!kind || !row.id || !row.title) return null
  const hue = typeof row.coverHue === 'number' ? row.coverHue : 210 + (row.id.length * 17) % 120
  return {
    id: String(row.id),
    kind,
    title: row.title,
    subtitle: row.subtitle?.trim() || '公共库资源',
    categoryTag: normalizeCategory(row.categoryTag),
    coverHue: hue,
    coverGlow: 'rgba(99, 102, 241, 0.38)',
    coverGlyph: row.coverGlyph?.trim() || (kind === 'book' ? '📚' : kind === 'prompt' ? '🪄' : '🏗️'),
    badge: row.badge,
    payload: row.payload,
  }
}

function readRecent(): RecentEntry[] {
  try {
    const raw = localStorage.getItem(RECENT_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (x): x is RecentEntry =>
          x &&
          typeof x === 'object' &&
          typeof (x as RecentEntry).id === 'string' &&
          typeof (x as RecentEntry).title === 'string' &&
          typeof (x as RecentEntry).at === 'number',
      )
      .slice(0, RECENT_MAX)
  } catch {
    return []
  }
}

function writeRecent(list: RecentEntry[]) {
  try {
    localStorage.setItem(RECENT_STORAGE_KEY, JSON.stringify(list.slice(0, RECENT_MAX)))
  } catch {
    /* ignore */
  }
}

const recentItems = ref<RecentEntry[]>(readRecent())

function pushRecent(entry: RecentEntry) {
  const rest = recentItems.value.filter((x) => !(x.id === entry.id && x.kind === entry.kind))
  recentItems.value = [{ ...entry, at: Date.now() }, ...rest].slice(0, RECENT_MAX)
  writeRecent(recentItems.value)
}

const tabKindFilter = computed(() => {
  if (activeTab.value === 'books') return (k: VaultKind) => k === 'book'
  if (activeTab.value === 'prompts') return (k: VaultKind) => k === 'prompt'
  return (k: VaultKind) => k === 'rag_blueprint'
})

const filteredItems = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  return items.value.filter((it) => {
    if (!tabKindFilter.value(it.kind)) return false
    if (activeCategory.value !== '全部' && it.categoryTag !== activeCategory.value) return false
    if (!q) return true
    return (
      it.title.toLowerCase().includes(q) ||
      it.subtitle.toLowerCase().includes(q) ||
      it.id.toLowerCase().includes(q)
    )
  })
})

/** 专业书籍原始列表（未按搜索/分类筛选），用于空数据判断 */
const bookList = computed(() => items.value.filter((it) => it.kind === 'book'))

const isGridLoading = computed(() => isLoading.value || vaultLoading.value)

function kindGlyph(kind: VaultKind): string {
  if (kind === 'book') return '📚'
  if (kind === 'prompt') return '🪄'
  return '🏗️'
}

async function loadVault() {
  vaultLoading.value = true
  vaultError.value = ''
  try {
    const rows = await fetchPublicVault()
    if (Array.isArray(rows) && rows.length > 0) {
      const mapped = rows.map(dtoToVaultItem).filter((x): x is VaultItem => x != null)
      if (mapped.length > 0) {
        items.value = mapped
        return
      }
    }
    items.value = MOCK_VAULT
    vaultError.value = '公共库返回为空，已展示本地演示数据。'
  } catch {
    items.value = MOCK_VAULT
    vaultError.value = '无法连接 /api/v1/rag/public-vault，已展示本地演示数据。'
  } finally {
    vaultLoading.value = false
  }
}

function findItem(kind: VaultKind, id: string): VaultItem | undefined {
  return items.value.find((x) => x.kind === kind && x.id === id)
}

function onUseOneClick(item: VaultItem) {
  pushRecent({ id: item.id, kind: item.kind, title: item.title, at: Date.now() })
  if (item.kind === 'book') {
    learningStore.patchContext({
      currentModule: '资源广场 · 专业书籍',
      recentAction: `一键使用：${item.title}`,
      lastError: '',
      extraContext: {
        source: 'ResourceMarketplace',
        vaultItemId: item.id,
        vaultKind: item.kind,
        subtitle: item.subtitle,
      },
    })
    ElMessage.success({ message: `已载入「${item.title}」到学习上下文，可在实验一或实验二中继续。`, duration: 3200 })
    return
  }
  if (item.kind === 'prompt') {
    const body = item.payload?.trim() || item.subtitle
    learningStore.patchContext({
      currentModule: '资源广场 · 实验一',
      recentAction: `套用模板：${item.title}`,
      lastError: '',
      extraContext: {
        source: 'ResourceMarketplace',
        vaultItemId: item.id,
        promptTemplate: body,
      },
    })
    learningStore.seedTutorChatPrompt(`我想在实验一中使用模板「${item.title}」。请根据下列指令帮我改写或补全：\n\n${body}`)
    ElMessage.success({ message: '已写入工作台上下文并打开导师对话，正在跳转实验一…', duration: 2400 })
    void router.push({ name: 'PromptWorkbench' })
    return
  }
  learningStore.patchContext({
    currentModule: '资源广场 · 实验二',
    recentAction: `套用蓝图：${item.title}`,
    lastError: '',
    extraContext: {
      source: 'ResourceMarketplace',
      vaultItemId: item.id,
      vaultKind: item.kind,
      blueprintHint: item.subtitle,
    },
  })
  ElMessage.success({ message: `「${item.title}」已标记，正在打开实验二工作台…`, duration: 2200 })
  void router.push({ name: 'RagBuildWorkbench' })
}

function applyRecent(r: RecentEntry) {
  const hit = findItem(r.kind, r.id)
  if (hit) {
    onUseOneClick(hit)
    return
  }
  ElMessage.info('该资源不在当前列表中，可能已下架。')
}

function onContributeFirst() {
  ElMessage.info({ message: '资料贡献入口即将开放，敬请期待。', duration: 2800 })
}

onMounted(() => {
  isLoading.value = true
  window.setTimeout(() => {
    isLoading.value = false
  }, 1500)
  void loadVault()
})
</script>

<style scoped>
.mplaza {
  min-height: calc(100vh - 132px);
  margin: -20px -24px -24px;
  width: calc(100% + 48px);
  max-width: none;
  background:
    radial-gradient(ellipse 100% 80% at 0% -10%, rgba(99, 102, 241, 0.18), transparent 55%),
    radial-gradient(ellipse 80% 50% at 100% 0%, rgba(56, 189, 248, 0.1), transparent 45%),
    linear-gradient(165deg, #0c1222 0%, #111827 38%, #0f172a 100%);
  border-top: 1px solid rgba(51, 65, 85, 0.55);
  color: #e2e8f0;
}

.mplaza__inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 24px 32px;
}

.mplaza-section-title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #94a3b8;
}

.mplaza-recent {
  margin-bottom: 28px;
}

.mplaza-recent__empty {
  font-size: 13px;
  color: #64748b;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.45);
  border: 1px dashed rgba(71, 85, 105, 0.6);
}

.mplaza-recent__scroll {
  width: 100%;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.35);
  border: 1px solid rgba(51, 65, 85, 0.55);
}

.mplaza-recent__scroll :deep(.el-scrollbar__wrap) {
  overflow-y: hidden;
}

.mplaza-recent__track {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  min-height: 52px;
  align-items: center;
}

.mplaza-recent-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(129, 140, 248, 0.35);
  background: rgba(30, 41, 59, 0.65);
  backdrop-filter: blur(10px);
  color: #e0e7ff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    transform 0.12s ease;
}

.mplaza-recent-chip:hover {
  border-color: rgba(165, 180, 252, 0.55);
  background: rgba(51, 65, 85, 0.75);
  transform: translateY(-1px);
}

.mplaza-recent-chip__glyph {
  font-size: 14px;
}

.mplaza-recent-chip__text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mplaza-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  margin-bottom: 20px;
}

.mplaza-search {
  flex: 1;
  min-width: 220px;
  max-width: 420px;
}

.mplaza-search :deep(.el-input__wrapper) {
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(71, 85, 105, 0.65);
  box-shadow: none;
}

.mplaza-search :deep(.el-input__inner) {
  color: #f1f5f9;
}

.mplaza-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mplaza-tag {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid rgba(71, 85, 105, 0.75);
  background: rgba(15, 23, 42, 0.4);
  color: #94a3b8;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}

.mplaza-tag:hover {
  color: #cbd5e1;
  border-color: rgba(99, 102, 241, 0.45);
}

.mplaza-tag--active {
  color: #f8fafc;
  border-color: rgba(129, 140, 248, 0.65);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.35), rgba(79, 70, 229, 0.22));
  box-shadow: 0 0 0 1px rgba(99, 102, 241, 0.2);
}

.mplaza-tabs {
  margin-bottom: 8px;
}

.mplaza-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
}

.mplaza-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(51, 65, 85, 0.5);
}

.mplaza-tabs :deep(.el-tabs__item) {
  color: #94a3b8;
  font-weight: 600;
}

.mplaza-tabs :deep(.el-tabs__item.is-active) {
  color: #a5b4fc;
}

.mplaza-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(90deg, #6366f1, #38bdf8);
  height: 3px;
  border-radius: 3px;
}

.mplaza-hint {
  margin: 0 0 14px;
  font-size: 12px;
  color: #fbbf24;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.mplaza-state {
  min-height: 200px;
}

.mplaza-state--empty {
  padding: 24px 16px 40px;
  border-radius: 16px;
  border: 1px dashed rgba(71, 85, 105, 0.45);
  background: rgba(15, 23, 42, 0.35);
}

.mplaza-state--empty :deep(.el-empty__description) {
  color: #94a3b8;
}

.mplaza-state--empty :deep(.el-empty__image) {
  opacity: 0.85;
}

.mplaza-empty-cta {
  font-weight: 700;
  margin-top: 4px;
}

.mplaza-skel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.mplaza-skel-card {
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(71, 85, 105, 0.45);
  background: rgba(15, 23, 42, 0.42);
}

.mplaza-skel-card :deep(.el-skeleton) {
  --el-skeleton-color: rgba(51, 65, 85, 0.45);
  --el-skeleton-to-color: rgba(71, 85, 105, 0.65);
}

.mplaza-skel-card__img {
  width: 100% !important;
  height: 120px !important;
  border-radius: 0 !important;
}

.mplaza-skel-card__body {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.mplaza-skel-card__line {
  width: 100% !important;
  height: 14px !important;
  margin: 0 !important;
}

.mplaza-skel-card__line--title {
  width: 55% !important;
  height: 18px !important;
}

.mplaza-skel-card__line--short {
  width: 72% !important;
}

.mplaza-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.mplaza-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(16px);
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.mplaza-card:hover {
  transform: translateY(-3px);
  border-color: rgba(129, 140, 248, 0.45);
  box-shadow:
    0 18px 48px rgba(0, 0, 0, 0.35),
    0 0 0 1px rgba(99, 102, 241, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.mplaza-card__shine {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(
    125deg,
    transparent 40%,
    rgba(255, 255, 255, 0.04) 48%,
    transparent 56%
  );
  opacity: 0;
  transition: opacity 0.25s ease;
}

.mplaza-card:hover .mplaza-card__shine {
  opacity: 1;
}

.mplaza-card__cover {
  position: relative;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(
    145deg,
    hsla(var(--hue), 62%, 42%, 0.95),
    hsla(calc(var(--hue) + 40), 55%, 28%, 0.92)
  );
  box-shadow: inset 0 -20px 40px rgba(0, 0, 0, 0.2);
}

.mplaza-card__glyph {
  font-size: 44px;
  filter: drop-shadow(0 6px 16px rgba(0, 0, 0, 0.35));
}

.mplaza-card__badge {
  position: absolute;
  top: 10px;
  right: 10px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #f8fafc;
}

.mplaza-card__body {
  padding: 16px 18px 18px;
  display: flex;
  flex-direction: column;
  flex: 1;
  gap: 8px;
}

.mplaza-card__cat {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #38bdf8;
}

.mplaza-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: #f8fafc;
  line-height: 1.35;
  letter-spacing: -0.02em;
}

.mplaza-card__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.55;
  color: #94a3b8;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

.mplaza-card__cta {
  align-self: flex-start;
  margin-top: 8px;
  font-weight: 700;
  border: none;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 8px 22px rgba(79, 70, 229, 0.35);
}

.mplaza-card__cta:hover {
  filter: brightness(1.06);
}

@media (max-width: 640px) {
  .mplaza {
    margin: -16px -16px -20px;
    width: calc(100% + 32px);
  }

  .mplaza__inner {
    padding: 16px 16px 24px;
  }

  .mplaza-grid {
    grid-template-columns: 1fr;
  }
}
</style>
