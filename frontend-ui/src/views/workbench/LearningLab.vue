<template>
  <div class="learning-lab">
    <header class="lab-navbar">
      <div class="lab-navbar__left">
        <el-button class="glass-btn" :icon="ArrowLeft" @click="goBackToGraph">
          {{ backButtonLabel }}
        </el-button>
        <div class="lab-navbar__titles">
          <span class="lab-navbar__eyebrow">沉浸式学习空间</span>
          <h1 class="lab-navbar__title">{{ nodeTitle }}</h1>
        </div>
      </div>
      <div class="lab-navbar__progress">
        <span class="lab-navbar__progress-label">本章进度</span>
        <el-progress
          type="dashboard"
          :percentage="chapterProgress"
          :width="56"
          :stroke-width="5"
          color="#6366f1"
          class="lab-progress-ring"
        />
      </div>
    </header>

    <div class="lab-body">
      <article class="lab-read glass-panel">
        <div
          ref="readInnerRef"
          class="lab-read__inner notion-read"
          @mouseup.stop="onReadMouseUp"
          @scroll.passive="hideHighlightToolbar"
        >
          <p class="notion-read__meta">精读文稿 · Mock</p>
          <h2 class="notion-read__h1">{{ nodeTitle }}</h2>
          <p class="notion-read__lead">
            本页模拟从 3D 知识图谱进入后的章节精读体验：左侧为结构化正文，右侧为 AI
            陪练。真实接入时可将正文替换为 RAG 检索结果或 CMS 内容。
          </p>

          <h3 class="notion-read__h2">为什么需要「检索增强生成」</h3>
          <p class="notion-read__p">
            大语言模型在开放域问答上表现突出，但对<strong>私有知识</strong>与<strong>时效性事实</strong>容易产生幻觉。RAG
            通过在回答前检索相关文档片段，把「可核查的证据」注入上下文，从而在可控成本内提升事实性与可追溯性。
          </p>

          <h3 class="notion-read__h2">一条最小 RAG 流水线</h3>
          <p class="notion-read__p">
            典型流程包括：文档切分（chunking）→ 向量化（embedding）→ 向量库存储 → 查询向量化 → Top-K
            相似检索 →（可选）重排 → 将片段与问题一并送入 LLM 生成答案。
          </p>

          <div class="notion-read__code-wrap">
            <div class="notion-read__code-label">示例 · Python 伪代码</div>
            <pre class="notion-read__pre"><code>chunks = split_text(doc, chunk_size=800, overlap=150)
vectors = embed(chunks)
index.upsert(ids, vectors, metadata)

def answer(question: str) -> str:
    qv = embed([question])[0]
    hits = index.search(qv, top_k=6)
    context = "\n\n".join(h.text for h in hits)
    return llm.chat(system=RAG_SYSTEM, user=f"上下文：\n{context}\n\n问题：{question}")</code></pre>
          </div>

          <h3 class="notion-read__h2">阅读提示</h3>
          <p class="notion-read__p">
            关注检索片段与最终回答之间的对齐关系：模型是否引用了正确段落？是否出现「上下文有但回答未用」或「上下文无却强行编造」的情况——这也是你做测验与调试时的检查清单。
          </p>

          <!-- 划词提问：相对阅读区绝对定位，黑底毛玻璃工具条 -->
          <div
            v-show="highlightToolbarVisible"
            ref="highlightToolbarRef"
            class="hl-ask-toolbar"
            role="toolbar"
            aria-label="划词快捷提问"
            :style="highlightToolbarStyle"
            @mousedown.prevent
          >
            <button type="button" class="hl-ask-toolbar__btn" title="解释" @click="onHighlightAction('explain')">
              <span class="hl-ask-toolbar__ico" aria-hidden="true">✨</span>
              <span class="hl-ask-toolbar__lbl">解释</span>
            </button>
            <button type="button" class="hl-ask-toolbar__btn" title="总结" @click="onHighlightAction('summarize')">
              <span class="hl-ask-toolbar__ico" aria-hidden="true">📝</span>
              <span class="hl-ask-toolbar__lbl">总结</span>
            </button>
            <button type="button" class="hl-ask-toolbar__btn" title="考考我" @click="onHighlightAction('quiz')">
              <span class="hl-ask-toolbar__ico" aria-hidden="true">❓</span>
              <span class="hl-ask-toolbar__lbl">考考我</span>
            </button>
          </div>
        </div>

        <footer class="lab-read__footer">
          <el-button
            type="primary"
            size="large"
            class="cta-quiz glass-cta"
            :loading="quizTriggerLoading"
            @click="requestAiQuiz"
          >
            🎓 我已学完，请求 AI 测验
          </el-button>
        </footer>
      </article>

      <aside class="lab-tutor glass-panel" aria-label="AI 陪练">
        <GlobalAiTutor embedded />
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, CSSProperties, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import GlobalAiTutor from '@/components/GlobalAiTutor.vue'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

const QUIZ_PROMPT =
  '我已经阅读完当前章节，请根据 RAG 知识库出 2 道选择题考考我。'

/** 演示用：nodeId → 展示标题（真实项目可由 API 返回） */
const NODE_TITLE_MAP: Record<string, string> = {
  'llm-finetune-basics': '大模型微调基础',
  'rag-intro': 'RAG 检索增强导论',
  'embedding-101': '向量与 Embedding 入门',
  'math-modeling': '数学建模指南',
  'spring-boot': 'Spring Boot 实战',
  'english-sentence': '考研英语长难句',
  'rag-thinking': 'RAG 检索思维',
  'agent-collaboration': '多智能体协作',
}

const route = useRoute()
const router = useRouter()
const learningStore = useGlobalLearningContextStore()

const quizTriggerLoading = ref(false)

/** 划词提问 */
const readInnerRef = ref<HTMLElement | null>(null)
const highlightToolbarRef = ref<HTMLElement | null>(null)
const highlightToolbarVisible = ref(false)
const highlightToolbarStyle = ref<CSSProperties>({})
const highlightBuffer = ref('')
const TOOLBAR_H = 40
const TOOLBAR_GAP = 10

const nodeId = computed(() => decodeURIComponent(String(route.params.nodeId || '')))

const nodeTitle = computed(() => {
  const fromQuery = typeof route.query.title === 'string' ? route.query.title.trim() : ''
  if (fromQuery) return fromQuery
  const id = nodeId.value
  if (NODE_TITLE_MAP[id]) return NODE_TITLE_MAP[id]
  if (!id) return '知识节点'
  return id.replace(/-/g, ' ')
})

const backButtonLabel = computed(() => {
  if (route.query.from === 'knowledge-universe') return '返回知识星空'
  if (route.query.from === 'knowledge-starry') return '返回知识星空'
  if (route.query.from === 'knowledge-map' || route.query.from === 'learning-resources')
    return '返回资源广场'
  if (route.query.from === 'rag-visual') return '返回 RAG 3D 可视化'
  return '返回 3D 可视化'
})

/** Mock：按节点简单哈希出固定进度，避免每页都是同一数字 */
const chapterProgress = computed(() => {
  let h = 0
  for (let i = 0; i < nodeId.value.length; i++) {
    h = (h * 31 + nodeId.value.charCodeAt(i)) >>> 0
  }
  return 38 + (h % 52)
})

function goBackToGraph() {
  if (route.query.from === 'knowledge-universe') {
    router.push({ name: 'KnowledgeMap' })
    return
  }
  if (route.query.from === 'knowledge-starry') {
    router.push({ name: 'KnowledgeStarrySky' })
    return
  }
  if (route.query.from === 'knowledge-map' || route.query.from === 'learning-resources') {
    router.push({ name: 'ResourceMarketplace' })
    return
  }
  if (route.query.from === 'rag-visual') {
    router.push({ name: 'RagVisualWorkbench' })
    return
  }
  router.push({ name: 'RagVisualWorkbench' })
}

function isNodeInsideReadRoot(node: Node | null): boolean {
  const root = readInnerRef.value
  if (!root || !node) return false
  const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as HTMLElement)
  return !!(el && root.contains(el))
}

function hideHighlightToolbar() {
  highlightToolbarVisible.value = false
  highlightToolbarStyle.value = {}
}

/**
 * 根据当前选区更新工具条位置（相对 .lab-read__inner 绝对定位，选区上方居中）
 */
function syncHighlightToolbarFromSelection() {
  const root = readInnerRef.value
  if (!root) {
    hideHighlightToolbar()
    return
  }

  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) {
    hideHighlightToolbar()
    return
  }

  const raw = sel.toString()
  const text = raw.replace(/\u00a0/g, ' ').trim()
  if (!text.length) {
    hideHighlightToolbar()
    return
  }

  if (!isNodeInsideReadRoot(sel.anchorNode) || !isNodeInsideReadRoot(sel.focusNode)) {
    hideHighlightToolbar()
    return
  }

  const range = sel.getRangeAt(0)
  const rr = range.getBoundingClientRect()
  if (rr.width === 0 && rr.height === 0) {
    hideHighlightToolbar()
    return
  }

  const innerRect = root.getBoundingClientRect()
  const scrollTop = root.scrollTop
  const scrollLeft = root.scrollLeft

  const centerX = rr.left - innerRect.left + scrollLeft + rr.width / 2
  const topAbove = rr.top - innerRect.top + scrollTop - TOOLBAR_GAP - TOOLBAR_H

  highlightBuffer.value = text
  highlightToolbarStyle.value = {
    left: `${Math.max(8, Math.min(centerX, root.clientWidth - 8))}px`,
    top: `${Math.max(8, topAbove)}px`,
    transform: 'translateX(-50%)',
  }
  highlightToolbarVisible.value = true
}

function onReadMouseUp() {
  window.requestAnimationFrame(() => {
    syncHighlightToolbarFromSelection()
  })
}

function onDocumentMouseUp() {
  window.requestAnimationFrame(() => {
    syncHighlightToolbarFromSelection()
  })
}

/** 点击空白处（不含工具条）时关闭悬浮条 */
function onDocumentMouseDown(e: MouseEvent) {
  const t = e.target
  if (!(t instanceof Node)) return
  if (highlightToolbarRef.value?.contains(t)) return
  hideHighlightToolbar()
}

type HighlightAskKind = 'explain' | 'summarize' | 'quiz'

function buildHighlightPrompt(kind: HighlightAskKind, quote: string): string {
  const title = nodeTitle.value
  const q = quote.trim()
  switch (kind) {
    case 'explain':
      return `请结合当前章节《${title}》的上下文，为我详细解释下面这段话在讲什么、涉及哪些关键概念，并适当联系 RAG / 大模型应用：\n\n「${q}」`
    case 'summarize':
      return `请结合当前章节《${title}》的上下文，用简洁的要点列表为我总结下面这段话的核心信息：\n\n「${q}」`
    case 'quiz':
      return `请结合当前章节《${title}》的上下文，围绕下面这段话出 1～2 道小测验题（可选择题或简答），并附简短答案要点，考考我是否真正理解：\n\n「${q}」`
    default:
      return q
  }
}

function onHighlightAction(kind: HighlightAskKind) {
  const quote = highlightBuffer.value.trim()
  if (!quote) {
    hideHighlightToolbar()
    return
  }

  hideHighlightToolbar()
  window.getSelection()?.removeAllRanges()

  const prompt = buildHighlightPrompt(kind, quote)
  learningStore.patchContext({
    currentModule: nodeTitle.value,
    recentAction: `划词提问：${kind === 'explain' ? '解释' : kind === 'summarize' ? '总结' : '考考我'}`,
    extraContext: {
      learningLab: true,
      nodeId: nodeId.value,
      highlightToAsk: true,
      highlightKind: kind,
      highlightSnippet: quote.slice(0, 200),
    },
  })
  learningStore.seedTutorChatPrompt(prompt)
  ElMessage.success('已发送至右侧陪练导师')
}

function requestAiQuiz() {
  quizTriggerLoading.value = true
  try {
    learningStore.patchContext({
      currentModule: nodeTitle.value,
      recentAction: '完成章节精读，请求 RAG 测验',
      extraContext: {
        learningLab: true,
        nodeId: nodeId.value,
        source: 'learning-lab-quiz-cta',
      },
    })
    learningStore.seedTutorChatPrompt(QUIZ_PROMPT)
    ElMessage.success('已向陪练导师发送测验请求')
  } finally {
    window.setTimeout(() => {
      quizTriggerLoading.value = false
    }, 400)
  }
}

onMounted(() => {
  learningStore.patchContext({
    currentModule: nodeTitle.value,
    recentAction: '沉浸式精读：' + nodeTitle.value,
    extraContext: {
      learningLab: true,
      nodeId: nodeId.value,
    },
  })
  document.addEventListener('mouseup', onDocumentMouseUp)
  document.addEventListener('mousedown', onDocumentMouseDown, true)
})

onUnmounted(() => {
  document.removeEventListener('mouseup', onDocumentMouseUp)
  document.removeEventListener('mousedown', onDocumentMouseDown, true)
  hideHighlightToolbar()
})
</script>

<style scoped>
.learning-lab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 14px 18px 18px;
  gap: 12px;
  background:
    radial-gradient(ellipse 100% 80% at 0% 0%, rgba(99, 102, 241, 0.18), transparent 55%),
    radial-gradient(ellipse 80% 60% at 100% 10%, rgba(56, 189, 248, 0.14), transparent 50%),
    linear-gradient(165deg, #f1f5f9 0%, #eef2ff 40%, #f8fafc 100%);
}

.lab-navbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.35);
  backdrop-filter: blur(18px);
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.08);
}

.lab-navbar__left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.lab-navbar__titles {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.lab-navbar__eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
}

.lab-navbar__title {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab-navbar__progress {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.lab-navbar__progress-label {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}

.lab-progress-ring :deep(.el-progress__text) {
  font-size: 11px !important;
  font-weight: 700;
  color: #4338ca !important;
}

.glass-btn {
  border-radius: 12px !important;
  background: rgba(255, 255, 255, 0.65) !important;
  border: 1px solid rgba(129, 140, 248, 0.45) !important;
  color: #3730a3 !important;
  font-weight: 600;
  backdrop-filter: blur(10px);
}

.lab-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 14px;
}

.glass-panel {
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(186, 200, 255, 0.4);
  backdrop-filter: blur(20px);
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.1);
  min-height: 0;
}

.lab-read {
  flex: 0 0 70%;
  max-width: 70%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.lab-read__inner {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 28px 32px 20px;
}

.hl-ask-toolbar {
  position: absolute;
  z-index: 20;
  display: flex;
  align-items: stretch;
  gap: 2px;
  padding: 4px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.35);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow:
    0 12px 32px rgba(0, 0, 0, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
  user-select: none;
}

.hl-ask-toolbar__btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 6px 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #e2e8f0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
  white-space: nowrap;
}

.hl-ask-toolbar__btn:hover {
  background: rgba(99, 102, 241, 0.35);
  color: #f8fafc;
}

.hl-ask-toolbar__btn:active {
  background: rgba(99, 102, 241, 0.5);
}

.hl-ask-toolbar__ico {
  font-size: 13px;
  line-height: 1;
}

.hl-ask-toolbar__lbl {
  line-height: 1.2;
}

.notion-read {
  max-width: 720px;
  margin: 0 auto;
}

.notion-read__meta {
  margin: 0 0 8px;
  font-size: 12px;
  color: #94a3b8;
  font-weight: 600;
}

.notion-read__h1 {
  margin: 0 0 12px;
  font-size: 1.75rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
  line-height: 1.2;
}

.notion-read__lead {
  margin: 0 0 28px;
  font-size: 1.05rem;
  line-height: 1.65;
  color: #475569;
}

.notion-read__h2 {
  margin: 28px 0 10px;
  font-size: 1.15rem;
  font-weight: 750;
  color: #1e293b;
}

.notion-read__h2:first-of-type {
  margin-top: 0;
}

.notion-read__p {
  margin: 0 0 14px;
  font-size: 15px;
  line-height: 1.75;
  color: #334155;
}

.notion-read__code-wrap {
  margin: 20px 0;
  border-radius: 12px;
  border: 1px solid rgba(100, 116, 139, 0.2);
  background: rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.notion-read__code-label {
  padding: 8px 14px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #64748b;
  border-bottom: 1px solid rgba(100, 116, 139, 0.15);
  background: rgba(255, 255, 255, 0.5);
}

.notion-read__pre {
  margin: 0;
  padding: 16px 18px;
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 12.5px;
  line-height: 1.55;
  color: #0f172a;
}

.lab-read__footer {
  flex-shrink: 0;
  padding: 16px 24px 22px;
  border-top: 1px solid rgba(148, 163, 184, 0.25);
  background: rgba(255, 255, 255, 0.35);
  display: flex;
  justify-content: center;
}

.cta-quiz {
  min-width: 280px;
  border-radius: 14px !important;
  font-weight: 700;
  font-size: 15px;
  padding: 22px 28px !important;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 50%, #7c3aed 100%) !important;
  border: none !important;
  box-shadow: 0 12px 36px rgba(79, 70, 229, 0.35);
}

.glass-cta:hover {
  filter: brightness(1.05);
}

.lab-tutor {
  flex: 1;
  min-width: 0;
  padding: 12px;
  display: flex;
  flex-direction: column;
}

.lab-tutor :deep(.global-ai-tutor) {
  flex: 1;
  min-height: 0;
}
</style>
