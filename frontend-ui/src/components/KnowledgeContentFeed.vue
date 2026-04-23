<template>
  <section
    class="kcf"
    :class="{ 'kcf--expert': expert, 'kcf--embedded': embedded }"
    :aria-label="nodeName ? `节点内容：${nodeName}` : '知识节点内容'"
  >
    <header v-if="!embedded" class="kcf__head">
      <div class="kcf__head-main">
        <span class="kcf__pulse" aria-hidden="true" />
        <div>
          <p class="kcf__kicker">NODE FEED</p>
          <h3 class="kcf__title">{{ nodeName || '未选择节点' }}</h3>
        </div>
      </div>
      <p class="kcf__api-tag" :class="{ 'kcf__api-tag--expert': expert }">
        {{ expert ? 'EXPERT · LIVE' : 'STANDARD · LIVE' }}
      </p>
    </header>

    <p v-if="loadError && !loading" class="kcf__error" role="alert">{{ loadError }}</p>
    <p v-if="loading" class="kcf__status">正在请求公共文档列表（/api/rag/documents）与导师大纲（/api/ai/tutor/hint）…</p>

    <div class="kcf__block">
      <h4 class="kcf__block-title"><span aria-hidden="true">📚</span> 推荐书籍</h4>
      <p class="kcf__outline-hint kcf__outline-hint--tight">来自公共知识库，按节点分类与标签弱匹配。</p>
      <div v-if="!loading && feed && feed.books.length" class="kcf__scroll" role="list">
        <article v-for="b in feed.books" :key="b.id" class="kcf-book" role="listitem">
          <div class="kcf-book__cover">
            <img :src="b.coverUrl" :alt="`《${b.title}》封面`" loading="lazy" width="140" height="190" />
            <div class="kcf-book__shine" aria-hidden="true" />
          </div>
          <div class="kcf-book__meta">
            <p class="kcf-book__name">{{ b.title }}</p>
            <div class="kcf-book__progress">
              <div class="kcf-book__track">
                <div class="kcf-book__fill" :style="{ width: `${b.progress}%` }" />
              </div>
              <span class="kcf-book__pct">{{ b.progress }}%</span>
            </div>
          </div>
        </article>
      </div>
      <p v-else-if="!loading" class="kcf__empty">
        暂无与当前节点标签匹配的公共文档；可在管理端上传并设为公开，或切换其他节点。
      </p>
    </div>

    <div class="kcf__block kcf__block--outline">
      <h4 class="kcf__block-title"><span aria-hidden="true">💡</span> 知识解析</h4>
      <el-alert
        v-if="!loading && feed?.tutorGatewayUnavailable"
        class="kcf__alert"
        type="warning"
        :closable="false"
        show-icon
        title="AI 大纲暂不可用"
        description="未连接 ai-gateway（默认 8000）时，后端会返回运维说明而非知识点。下方已改为节点内置摘要；启动网关并执行 npm run gateway 后刷新即可恢复 AI 生成。"
      />
      <p
        v-if="!loading && feed && !feed.tutorGatewayUnavailable"
        class="kcf__outline-hint"
      >
        由后端导师服务（/api/ai/tutor/hint）结合大模型生成要点。
      </p>
      <p
        v-else-if="!loading && feed?.tutorGatewayUnavailable"
        class="kcf__outline-hint"
      >
        以下为图谱节点元数据拼装的本地摘要（非实时模型）。
      </p>
      <ol v-if="!loading && feed && feed.outline.length" class="kcf-outline">
        <li v-for="(line, i) in feed.outline" :key="i" class="kcf-outline__item">
          <span class="kcf-outline__idx">{{ String(i + 1).padStart(2, '0') }}</span>
          <span class="kcf-outline__text">{{ line }}</span>
        </li>
      </ol>
      <p v-else-if="!loading" class="kcf__empty">暂无大纲（请确认 backend-services 与 ai-gateway 可用）。</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { fetchKnowledgeNodeFeed, type KnowledgeFeedPayload } from '@/api/knowledgeMapFeed'
import { getKnowledgeMapNodeById } from '@/data/knowledgeMapGraph'

defineOptions({ name: 'KnowledgeContentFeed' })

const props = defineProps<{
  nodeId: string | null
  nodeName: string
  expert?: boolean
  /** 嵌入侧栏时隐藏顶部 NODE FEED 标题区，由外层统一展示节点信息 */
  embedded?: boolean
  tenantId: number
  userLevel: string
}>()

const feed = shallowRef<KnowledgeFeedPayload | null>(null)
const loading = ref(false)
const loadError = ref('')

let abortController: AbortController | null = null

async function loadFeed() {
  const id = props.nodeId
  if (!id) {
    feed.value = null
    loadError.value = ''
    return
  }
  const node = getKnowledgeMapNodeById(id)
  if (!node) {
    feed.value = null
    loadError.value = ''
    return
  }

  abortController?.abort()
  abortController = new AbortController()
  const { signal } = abortController

  loading.value = true
  loadError.value = ''
  try {
    feed.value = await fetchKnowledgeNodeFeed({
      tenantId: props.tenantId,
      node,
      userLevel: props.userLevel,
      signal,
    })
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') return
    feed.value = { books: [], outline: [] }
    loadError.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.nodeId, props.tenantId, props.userLevel] as const,
  () => {
    void loadFeed()
  },
  { immediate: true },
)
</script>

<style scoped>
.kcf {
  --kcf-border: rgba(99, 102, 241, 0.28);
  --kcf-glow: rgba(56, 189, 248, 0.12);
  border-radius: 16px;
  padding: 16px 18px 20px;
  background:
    linear-gradient(145deg, rgba(15, 23, 42, 0.55) 0%, rgba(30, 27, 75, 0.22) 100%),
    radial-gradient(ellipse 70% 50% at 0% 0%, var(--kcf-glow), transparent 55%);
  border: 1px solid rgba(71, 85, 105, 0.45);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.kcf--embedded {
  padding: 12px 14px 16px;
  border-radius: 14px;
  background: transparent;
  border-color: rgba(99, 102, 241, 0.2);
  box-shadow: none;
}

.kcf--expert {
  --kcf-border: rgba(34, 211, 238, 0.35);
  --kcf-glow: rgba(34, 211, 238, 0.14);
  border-color: rgba(34, 211, 238, 0.25);
  background:
    linear-gradient(160deg, rgba(8, 15, 35, 0.92) 0%, rgba(15, 23, 42, 0.75) 100%),
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 2px,
      rgba(34, 211, 238, 0.03) 2px,
      rgba(34, 211, 238, 0.03) 4px
    );
  font-family: 'JetBrains Mono', 'SF Mono', ui-monospace, monospace;
}

.kcf__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.kcf__head-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.kcf__pulse {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #22d3ee;
  box-shadow: 0 0 12px rgba(34, 211, 238, 0.85);
  animation: kcf-pulse 2.4s ease-in-out infinite;
}

@keyframes kcf-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.55;
    transform: scale(0.92);
  }
}

.kcf__kicker {
  margin: 0 0 4px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: #818cf8;
}

.kcf--expert .kcf__kicker {
  color: #22d3ee;
}

.kcf__title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #f1f5f9;
  letter-spacing: -0.02em;
  line-height: 1.25;
  word-break: break-word;
}

.kcf__api-tag {
  margin: 0;
  flex-shrink: 0;
  padding: 4px 10px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  color: #a5b4fc;
  border: 1px solid rgba(129, 140, 248, 0.4);
  border-radius: 8px;
  background: rgba(49, 46, 129, 0.35);
}

.kcf__api-tag--expert {
  color: #a5f3fc;
  border-color: rgba(34, 211, 238, 0.35);
  background: rgba(6, 78, 59, 0.25);
}

.kcf__error {
  margin: 0 0 12px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.45;
  color: #fecaca;
  border-radius: 10px;
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.35);
}

.kcf__status {
  margin: 0 0 14px;
  font-size: 12px;
  color: #93c5fd;
  line-height: 1.5;
}

.kcf--expert .kcf__status {
  font-size: 11px;
  color: #67e8f9;
}

.kcf__block {
  margin-bottom: 20px;
}

.kcf__block:last-child {
  margin-bottom: 0;
}

.kcf__block-title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 800;
  color: #e2e8f0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.kcf--expert .kcf__block-title {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #94a3b8;
}

.kcf__outline-hint {
  margin: -4px 0 12px;
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
}

.kcf__alert {
  margin: 0 0 12px;
}

.kcf__outline-hint--tight {
  margin-top: -8px;
  margin-bottom: 10px;
}

.kcf--expert .kcf__outline-hint {
  font-size: 10px;
  color: #64748b;
}

.kcf__scroll {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 8px;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
}

.kcf__scroll::-webkit-scrollbar {
  height: 6px;
}

.kcf__scroll::-webkit-scrollbar-thumb {
  background: rgba(99, 102, 241, 0.35);
  border-radius: 999px;
}

.kcf-book {
  flex: 0 0 auto;
  width: 148px;
  scroll-snap-align: start;
  border-radius: 14px;
  border: 1px solid rgba(71, 85, 105, 0.55);
  background: rgba(15, 23, 42, 0.65);
  overflow: hidden;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.kcf-book:hover {
  border-color: rgba(129, 140, 248, 0.55);
  box-shadow: 0 12px 28px rgba(2, 6, 23, 0.45);
}

.kcf--expert .kcf-book {
  border-color: rgba(34, 211, 238, 0.22);
  background: rgba(8, 15, 35, 0.85);
}

.kcf--expert .kcf-book:hover {
  border-color: rgba(34, 211, 238, 0.45);
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.15), 0 12px 32px rgba(2, 6, 23, 0.55);
}

.kcf-book__cover {
  position: relative;
  aspect-ratio: 140 / 190;
  background: #0f172a;
}

.kcf-book__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.kcf-book__shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(125deg, transparent 40%, rgba(255, 255, 255, 0.08) 50%, transparent 60%);
  pointer-events: none;
}

.kcf-book__meta {
  padding: 10px 10px 12px;
}

.kcf-book__name {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: #e2e8f0;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kcf--expert .kcf-book__name {
  font-size: 11px;
  font-weight: 600;
}

.kcf-book__progress {
  display: flex;
  align-items: center;
  gap: 6px;
}

.kcf-book__track {
  flex: 1;
  height: 5px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid rgba(51, 65, 85, 0.75);
  overflow: hidden;
}

.kcf-book__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #6366f1, #22d3ee);
}

.kcf--expert .kcf-book__fill {
  background: linear-gradient(90deg, #0891b2, #22d3ee);
}

.kcf-book__pct {
  font-size: 10px;
  font-weight: 700;
  color: #93c5fd;
  width: 32px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.kcf--expert .kcf-book__pct {
  color: #67e8f9;
}

.kcf-outline {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kcf-outline__item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(51, 65, 85, 0.65);
  background: rgba(30, 41, 59, 0.45);
}

.kcf--expert .kcf-outline__item {
  border-color: rgba(34, 211, 238, 0.15);
  background: rgba(8, 15, 35, 0.72);
}

.kcf-outline__idx {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 800;
  color: #6366f1;
  font-variant-numeric: tabular-nums;
  opacity: 0.9;
}

.kcf--expert .kcf-outline__idx {
  color: #22d3ee;
}

.kcf-outline__text {
  font-size: 13px;
  line-height: 1.55;
  color: #cbd5e1;
}

.kcf--expert .kcf-outline__text {
  font-size: 12px;
  line-height: 1.6;
  color: #e2e8f0;
}

.kcf__empty {
  margin: 0;
  padding: 14px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
  border-radius: 12px;
  border: 1px dashed rgba(71, 85, 105, 0.55);
  background: rgba(15, 23, 42, 0.35);
}
</style>
