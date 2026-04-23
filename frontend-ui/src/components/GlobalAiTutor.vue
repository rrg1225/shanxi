<template>
  <div class="global-ai-tutor" :class="{ 'global-ai-tutor--embedded': embedded }">
    <div v-if="!embedded" class="global-ai-tutor__tour" aria-hidden="true">
    <el-tour v-model="showTour" :mask="false" @close="finishTour">
      <el-tour-step
        target="#tour-home"
        title="学习门户首页"
        description="默认进入学习门户：三大学习入口与最近活动。侧栏可收起，留出更多内容区。"
      />
      <el-tour-step
        target="#tour-prompt"
        title="咒语实验室"
        description="爆款咒语库与 Prompt 调试、概率树——用一句话把 AI 用对场景。"
      />
      <el-tour-step
        target="#tour-rag"
        title="知识库 · RAG 蓝图"
        description="在蓝图里拖拽连线搭建 RAG 流程，并与 3D 可视化阶段的展示联动。"
      />
      <el-tour-step
        target="#tour-rag-visual"
        title="知识库 · 3D 可视化"
        description="上传文档、做检索，并在 3D 知识宇宙里探索节点；可召唤导师陪你读。"
      />
      <el-tour-step
        target="#tour-agent"
        title="AI 竞技场"
        description="这里是 AI 竞技场，围观多个智能体在线整活与协作。"
      />
    </el-tour>
    </div>

    <div class="global-ai-tutor__controls" :class="{ 'global-ai-tutor__controls--embedded': embedded }">
    <transition :name="embedded ? 'none' : 'slide-up'">
      <div
        v-if="showInfraTroubleshoot"
        class="infra-bubble"
        :class="{ 'infra-bubble--embedded': embedded }"
        role="status"
      >
        <div class="infra-bubble__title">一键排查</div>
        <p class="infra-bubble__lead">
          检测到连接或鉴权异常（{{ infraErrorKind }}）。可先按下列步骤自检，或切换到「学习建议」获取引导。
        </p>
        <ul class="infra-bubble__list">
          <li v-for="(line, i) in infraTroubleshootLines" :key="i">{{ line }}</li>
        </ul>
        <div class="infra-bubble__actions">
          <el-button size="small" type="primary" @click="onInfraRunAdvice">获取连接引导</el-button>
          <el-button size="small" @click="dismissInfraBubble">已了解</el-button>
        </div>
      </div>
    </transition>

    <transition :name="embedded ? 'none' : 'slide-up'">
      <div
        v-if="selectionDiscuss.visible && selectionDiscuss.text"
        class="selection-chip"
        :class="{ 'selection-chip--embedded': embedded }"
      >
        <span class="selection-chip__preview" :title="selectionDiscuss.text">
          {{ selectionDiscuss.preview }}
        </span>
        <el-button size="small" type="primary" round @click="discussSelection">以此发起讨论</el-button>
        <button type="button" class="selection-chip__close" aria-label="关闭" @click="clearSelectionDiscuss">
          ×
        </button>
      </div>
    </transition>

    <button
      v-if="!embedded"
      class="floating-btn"
      @click="togglePanel"
      :title="expanded ? '收起导师' : '打开导师'"
    >
      <span class="icon">🧠</span>
      <span class="label">专属 AI 实验助教</span>
    </button>

    <transition :name="embedded ? 'none' : 'slide-up'">
      <section v-show="embedded || expanded" class="tutor-panel" :class="{ 'tutor-panel--embedded': embedded }">
        <header class="panel-header">
          <div>
            <h3>{{ embedded ? '章节专属 AI 实验助教' : '专属 AI 实验助教' }}</h3>
            <p class="panel-meta">
              <template v-if="embedded">
                支持 AI 原理学习 / 实验操作指导 (基于网关 qwen3-max 流式输出)
              </template>
              <template v-else>
                支持 AI 原理学习 / 实验操作指导 (基于网关 qwen3-max 流式输出)
              </template>
            </p>
          </div>
          <button v-if="!embedded" type="button" class="close-btn" @click="expanded = false">×</button>
        </header>

        <div v-if="showContextAwareBadge" class="context-aware-strip" role="status">
          <el-icon class="context-aware-strip__ico" :size="14"><View /></el-icon>
          <span class="context-aware-strip__text">已感知当前页面状态</span>
        </div>

        <div class="mode-switch">
          <button
            class="mode-btn"
            :class="{ active: tutorMode === 'chat' }"
            @click="tutorMode = 'chat'"
          >
            学习知识（聊天）
          </button>
          <button
            class="mode-btn"
            :class="{ active: tutorMode === 'advice' }"
            @click="tutorMode = 'advice'"
          >
            学习建议
          </button>
        </div>

        <div v-if="tutorMode === 'advice'" class="snapshot-box">
          <div class="snapshot-title">学习快照</div>
          <div class="snapshot-grid">
            <div class="snapshot-item">
              <div class="snapshot-label">当前模块</div>
              <div class="snapshot-value">{{ currentModule || '未识别' }}</div>
            </div>
            <div class="snapshot-item">
              <div class="snapshot-label">最近操作</div>
              <div class="snapshot-value">{{ recentAction || '暂无' }}</div>
            </div>
            <div class="snapshot-item">
              <div class="snapshot-label">学习水平</div>
              <div class="snapshot-value">{{ userLevel || 'beginner' }}</div>
            </div>
            <div class="snapshot-item">
              <div class="snapshot-label">错误状态</div>
              <div class="snapshot-value" :class="{ danger: !!lastError }">
                {{ lastError ? '存在异常，需要引导' : '正常学习中' }}
              </div>
            </div>
          </div>
        </div>

        <div v-if="tutorMode === 'chat'" class="chat-section">
          <div class="chat-list" v-if="chatHistory.length > 0">
            <div
              v-for="(msg, idx) in chatHistory"
              :key="`${idx}-${msg.role}`"
              class="chat-item"
              :class="msg.role"
            >
              <div class="chat-role">{{ msg.role === 'user' ? '我' : 'AI 实验助教' }}</div>
              <div
                v-if="msg.role === 'user'"
                class="chat-content chat-content--plain"
              >
                {{ msg.content }}
              </div>
              <div
                v-else
                class="chat-content chat-content--md tutor-md"
                v-html="renderAssistantHtml(msg.content)"
              />
            </div>
          </div>
          <div v-else class="chat-empty">在这里提学习问题，例如：RAG 检索增强是如何抑制幻觉的？</div>

          <el-input
            v-model="questionInput"
            type="textarea"
            :rows="3"
            placeholder="请输入实验问题（Ctrl+Enter 发送）"
            @keyup.ctrl.enter="askLearningKnowledge"
          />
        </div>

        <div class="actions">
          <el-button
            v-if="tutorMode === 'chat'"
            type="primary"
            :loading="loading"
            @click="askLearningKnowledge"
          >
            发送提问
          </el-button>
          <el-button
            v-else
            type="primary"
            :loading="loading"
            @click="askLearningAdvice"
          >
            获取学习建议
          </el-button>
          <el-button v-if="tutorMode === 'chat'" @click="clearChat">清空聊天</el-button>
          <el-button v-else @click="clearTips">清空建议</el-button>
        </div>

        <div class="tips-box" v-if="tutorMode === 'advice' && tipsMarkdown">
          <div class="tips-title">导师提示</div>
          <div class="tips-body tutor-md" v-html="renderAssistantHtml(tipsMarkdown)" />
        </div>
      </section>
    </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import DOMPurify from 'dompurify'
import { marked, Renderer } from 'marked'
import type { Tokens } from 'marked'
import mermaid from 'mermaid'

/**
 * 你可替换为项目中的真实 Store。
 * 这里约定全局上下文 store 提供：
 * - currentModule: string
 * - lastError: string
 * - recentAction: string
 * - userLevel: string
 */
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'
import type { TutorHintRequest } from '@/api/modules/ai'

const props = withDefaults(
  defineProps<{
    /** 嵌入页面右侧时：无悬浮球、面板常开、铺满容器 */
    embedded?: boolean
  }>(),
  { embedded: false },
)

/** 与 backend application.yml / ai-gateway QWEN_SYSTEM_PROMPT 对齐；可通过 VITE_MENTOR_SYSTEM_PROMPT 覆盖 */
const MENTOR_SYSTEM_PROMPT =
  import.meta.env.VITE_MENTOR_SYSTEM_PROMPT ||
  '你是《AI生成原理与优化实战》虚拟仿真实验平台的专属 AI 实验助教。请围绕 AI 底层原理解析与优化实战指导作答，保持专业、耐心、启发式风格，支持 Markdown。讲解流程时优先给出结构化步骤，必要时使用 ```mermaid（flowchart TD/LR）展示，并尽量贴合当前实验上下文。'

const PROMPT_TEST_PATH = '/api/ai/prompt-test'

type TutorMode = 'chat' | 'advice'
type ChatRole = 'user' | 'assistant'

interface ChatMessage {
  role: ChatRole
  content: string
}

const WELCOME_MESSAGE = `你好！👋
我是本《AI生成原理与优化实战》仿真平台的专属 AI 实验助教，很高兴为你导读！
当前你正在进行相关实验模块的操作。作为你的助教，我主要为你提供**AI底层原理解析**与**优化实战指导**。

如果你在实验中遇到任何疑惑，比如：
- **“大模型的 Temperature 与 Top-P 参数是如何影响生成概率分布的？”**
- **“RAG 检索增强架构具体是如何消除大模型幻觉的？”**
- **“多智能体（Agent）在复杂代码审查中是如何进行链式反思的？”**
……都可以随时问我！😊

💡 小提示：为了帮你更直观地理解，我会结合文字讲解、流程图（Mermaid）和生活化的类比。现在，你在哪个实验环节遇到了问题？`

const expanded = ref(false)
const loading = ref(false)
const tips = ref<string[]>([])
const tutorMode = ref<TutorMode>('advice')
const questionInput = ref('')
const chatHistory = ref<ChatMessage[]>([{ role: 'assistant', content: WELCOME_MESSAGE }])
const isFirstVisit = ref(false)
const showTour = ref(false)
const firstVisitStorageKey = 'ai-workbench-first-visit-v1'

const route = useRoute()
const ctxStore = useGlobalLearningContextStore()
const {
  currentModule,
  lastError,
  recentAction,
  userLevel,
  extraContext,
  autoTutorRequested,
  tutorChatNonce,
  tutorChatSeedPrompt,
  currentActiveContext,
  theme,
} = storeToRefs(ctxStore)

function escapeHtml(s: string) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

class TutorMarkdownRenderer extends Renderer {
  override code(token: Tokens.Code): string {
    const lang = token.lang?.toLowerCase().trim()
    if (lang === 'mermaid') {
      return `<div class="tutor-mermaid-block"><pre class="mermaid">${escapeHtml(token.text)}</pre></div>`
    }
    return super.code(token)
  }
}

marked.use({
  gfm: true,
  breaks: true,
  renderer: new TutorMarkdownRenderer(),
})

function sanitizeTutorHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['class', 'id', 'target', 'rel'],
  })
}

function renderAssistantHtml(markdown: string): string {
  const raw = markdown?.trim() ? marked.parse(markdown, { async: false }) : ''
  return typeof raw === 'string' ? sanitizeTutorHtml(raw) : ''
}

let mermaidScheduled = 0
async function runMermaidDiagrams(root: HTMLElement | null) {
  const host = root ?? document.body
  const nodes = host.querySelectorAll<HTMLElement>('.tutor-mermaid-block pre.mermaid:not([data-processed])')
  if (!nodes.length) return
  try {
    await mermaid.run({ nodes: [...nodes] })
  } catch (e) {
    console.warn('[GlobalAiTutor] mermaid run:', e)
  }
}

function scheduleMermaid(root: HTMLElement | null, debounceMs: number) {
  window.clearTimeout(mermaidScheduled)
  mermaidScheduled = window.setTimeout(() => {
    void runMermaidDiagrams(root)
  }, debounceMs)
}

function tutorFallbackLines(err: unknown): string[] {
  const m = err instanceof Error ? err.message : String(err)
  if (/无法连接后端|Failed to fetch|fetch/i.test(m)) {
    return [
      '无法连接后端 API。',
      '请确认：① backend-services 已启动（默认 8082）；② 前端 .env.development 中 VITE_BACKEND_BASE_URL 留空并重启 npm run dev，使 /api 走 Vite 代理。',
    ]
  }
  if (/PROXY_UPSTREAM_DOWN|ai-gateway|503|502|无法连接 ai-gateway/i.test(m)) {
    return [
      '无法连接 ai-gateway（流式导师专用，端口 8000）。',
      '请在项目根目录执行：cd ai-gateway && pip install -r requirements.txt && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000',
      `详情：${m.slice(0, 240)}`,
    ]
  }
  if (/\b401\b|UNAUTHORIZED|未授权|鉴权|TOKEN|登录|JWT|FORBIDDEN|\b403\b/i.test(m)) {
    return [
      '请求未授权或登录已失效（401/403）。',
      '请检查：① 网关或后端的 API Key / Token 配置；② 浏览器是否携带有效会话；③ 代理是否把鉴权头转发到上游。',
      `详情摘录：${m.slice(0, 220)}`,
    ]
  }
  if (/500|Request failed: 5/.test(m)) {
    return [
      '导师流式接口异常（常见：ai-gateway 未启动，或 Vite 代理连不上 8000）。',
      '请启动 ai-gateway（默认 http://127.0.0.1:8000），并确认 /api/ai/prompt-test 可访问；再启动 backend-services（8082）。',
      `详情摘录：${m.slice(0, 200)}`,
    ]
  }
  return ['暂时无法获取导师回复。', m.slice(0, 280)]
}

const showInfraTroubleshoot = ref(false)

function isInfraCriticalError(msg: string): boolean {
  if (!msg?.trim()) return false
  return (
    /\b401\b|UNAUTHORIZED|未授权|TOKEN|登录|鉴权|JWT|FORBIDDEN|403/i.test(msg) ||
    /\b502\b|BAD\s*GATEWAY|网关错误|PROXY_UPSTREAM|503|504/i.test(msg) ||
    /无法连接\s*ai-gateway|无法连接后端|ECONNREFUSED|AI-GATEWAY（流式/i.test(msg)
  )
}

const infraErrorKind = computed(() => {
  const m = lastError.value || ''
  if (/\b401\b|未授权|UNAUTHORIZED|鉴权|TOKEN|登录/i.test(m)) return '401 / 鉴权'
  if (/\b502\b|BAD\s*GATEWAY|503|504|PROXY_UPSTREAM|网关/i.test(m)) return '502 / 网关'
  return '网络或服务'
})

const infraTroubleshootLines = computed(() =>
  tutorFallbackLines(new Error(lastError.value || 'unknown')),
)

function dismissInfraBubble() {
  showInfraTroubleshoot.value = false
  ctxStore.patchContext({ lastError: '' })
}

async function onInfraRunAdvice() {
  tutorMode.value = 'advice'
  if (!props.embedded) expanded.value = true
  await askLearningAdvice()
}

watch(
  lastError,
  (msg) => {
    if (!msg?.trim()) {
      showInfraTroubleshoot.value = false
      return
    }
    if (isInfraCriticalError(msg)) {
      showInfraTroubleshoot.value = true
      if (!props.embedded) expanded.value = true
      tutorMode.value = 'advice'
    }
  },
  { immediate: true },
)

const selectionDiscuss = ref({
  visible: false,
  text: '',
  preview: '',
})

const tipsMarkdown = computed(() => (tips.value.length ? tips.value.join('\n\n') : ''))

function clearSelectionDiscuss() {
  selectionDiscuss.value = { visible: false, text: '', preview: '' }
}

function discussSelection() {
  const t = selectionDiscuss.value.text.trim()
  if (!t) return
  tutorMode.value = 'chat'
  if (!props.embedded) expanded.value = true
  questionInput.value = `请结合我选中的这段内容讲解或答疑：\n\n「${t}」`
  clearSelectionDiscuss()
  window.getSelection()?.removeAllRanges()
  void nextTick()
}

function onDocumentMouseUp() {
  if (!allowSelectionDiscuss.value) return
  window.setTimeout(() => {
    const sel = window.getSelection()
    const text = sel?.toString().trim() ?? ''
    if (text.length < 3 || text.length > 6000) {
      clearSelectionDiscuss()
      return
    }
    const anchor = sel?.anchorNode
    if (!anchor) return
    const baseEl =
      anchor.nodeType === Node.TEXT_NODE ? anchor.parentElement : (anchor as unknown as HTMLElement)
    if (baseEl?.closest?.('input, textarea, [contenteditable="true"], .global-ai-tutor')) {
      clearSelectionDiscuss()
      return
    }
    const preview = text.length > 42 ? `${text.slice(0, 42)}…` : text
    selectionDiscuss.value = { visible: true, text, preview }
  }, 10)
}

const allowSelectionDiscuss = computed(
  () => props.embedded || route.path.startsWith('/workbench'),
)

const showContextAwareBadge = computed(
  () => !!(props.embedded || expanded.value) && currentActiveContext.value != null,
)

function summarizeContextDataBrief(data: unknown): string {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    return '相关细节暂不可用。'
  }
  const o = data as Record<string, unknown>
  const kn =
    typeof o.knowledgeNodeName === 'string'
      ? o.knowledgeNodeName.trim()
      : typeof o.nodeName === 'string'
        ? o.nodeName.trim()
        : ''
  const kid = typeof o.knowledgeNodeId === 'string' ? o.knowledgeNodeId.trim() : ''
  if (kn || kid) {
    const g = typeof o.group === 'string' ? o.group : typeof o.category === 'string' ? o.category : ''
    const mastery = typeof o.mastery === 'number' ? Math.round(Math.min(1, Math.max(0, o.mastery)) * 100) : null
    const bits = [g ? `领域「${g}」` : '', mastery != null ? `掌握度约 ${mastery}%` : ''].filter(Boolean)
    return `正在查看知识节点「${kn || kid}」${bits.length ? `（${bits.join(' · ')}）` : ''}。`
  }
  const p = typeof o.prompt === 'string' ? o.prompt.trim() : ''
  const snippet = p.length > 120 ? `${p.slice(0, 120)}…` : p || '（调试区文案较短）'
  const t = typeof o.temperature === 'number' ? o.temperature : '—'
  const tp = typeof o.topP === 'number' ? o.topP : '—'
  return `当前调试区 Prompt 摘要：「${snippet}」；采样参数 Temperature=${t}，Top-P=${tp}。`
}

/** 拼入流式请求：隐式 system 增补，让模型「开场」即理解用户处境 */
function buildImplicitAwarenessInset(): string {
  const ctx = currentActiveContext.value
  if (!ctx) return ''
  const brief = summarizeContextDataBrief(ctx.data)
  const opener = `我注意到你正在「${ctx.page}」页面，${brief}有什么我可以帮你的吗？`
  return `\n\n【上下文感知｜须自然融入回答】${opener}\n请结合该情境用中文作答；若涉及知识图谱节点，可给出前置学习、实验与延伸阅读建议；若与 Prompt / 采样参数相关，请给出可执行建议。\n`
}

function mentorSystemWithContext(): string {
  const merged = `${MENTOR_SYSTEM_PROMPT}${buildImplicitAwarenessInset()}`
  return merged.length > 20000 ? `${merged.slice(0, 20000)}\n[system context truncated]` : merged
}

const advicePayload = computed<TutorHintRequest>(() => {
  return {
    module: '学习建议',
    lastError: lastError.value || '',
    recentAction: '请基于当前页面状态给出学习建议、学习方式和下一步行动',
    userLevel: userLevel.value || 'beginner',
    extraContext: {
      tutorMode: 'advice',
      currentModule: currentModule.value || '',
      recentAction: recentAction.value || '',
      lastError: lastError.value || '',
      userLevel: userLevel.value || 'beginner',
      snapshot: extraContext.value || {},
    },
  }
})

function togglePanel() {
  if (props.embedded) return
  expanded.value = !expanded.value
}

watch(
  () => props.embedded,
  (v) => {
    if (v) {
      expanded.value = true
      tutorMode.value = 'chat'
    }
  },
  { immediate: true },
)

function finishTour() {
  showTour.value = false
  if (!isFirstVisit.value) return
  isFirstVisit.value = false
  localStorage.setItem(firstVisitStorageKey, '0')
}

function buildChatPayload(question: string): TutorHintRequest {
  return {
    module: '学习知识问答',
    lastError: lastError.value || '',
    recentAction: question,
    userLevel: userLevel.value || 'beginner',
    extraContext: {
      tutorMode: 'chat',
      goal: '知识讲解与问答',
      currentModule: currentModule.value || '',
      pageContext: extraContext.value || {},
    },
  }
}

/** 与 TutorServiceImpl.buildPrompt 对齐，走网关流式（不再经后端聚合） */
function buildAdviceStreamPrompt(): string {
  const p = advicePayload.value
  let sb =
    '请基于以下学生上下文，给出 3-5 条简洁、可执行的引导建议。使用 Markdown（列表、加粗）；若涉及流程或 RAG 管线，可附 ```mermaid 流程图（flowchart TD/LR）。\n'
  sb += `当前模块：${p.module}\n`
  sb += `最近报错：${p.lastError}\n`
  sb += `最近操作：${p.recentAction}\n`
  sb += `学生水平：${p.userLevel}\n`
  sb += `额外上下文：${JSON.stringify(p.extraContext ?? {})}\n`
  const merged = `${mentorSystemWithContext()}\n\n${sb}`
  return merged.length > 18000 ? `${merged.slice(0, 18000)}\n[context truncated]` : merged
}

function buildChatStreamPrompt(question: string): string {
  const p = buildChatPayload(question)
  const ctx =
    `当前模块：${p.module}\n最近报错：${p.lastError}\n学生水平：${p.userLevel}\n` +
    `上下文：${JSON.stringify(p.extraContext ?? {})}\n`
  const merged = `${mentorSystemWithContext()}\n\n${ctx}\n请用中文清晰回答学生的问题（可适当使用 Markdown）：\n\n${question}`
  return merged.length > 18000 ? `${merged.slice(0, 18000)}\n[truncated]` : merged
}

/**
 * 解析 ai-gateway SSE：跳过注释行、合并多行 data；主体为 JSON { token, done, error }。
 * 非 JSON 的 data 行（极少见）当作纯文本 token，便于兼容扩展。
 */
async function consumeSseStream(response: Response, onToken: (t: string) => void): Promise<void> {
  const reader = response.body?.getReader()
  if (!reader) throw new Error('响应不支持流式读取')

  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    for (;;) {
      const sep = buffer.indexOf('\n\n')
      if (sep < 0) break
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)

      let errEvent = false
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        const t = line.trimEnd()
        if (!t || t.startsWith(':')) continue
        if (t.startsWith('event:') && t.toLowerCase().includes('error')) {
          errEvent = true
          continue
        }
        const m = t.match(/^data:\s?(.*)$/)
        if (m) dataLines.push(m[1])
      }

      const raw = dataLines.join('\n').trim()
      if (!raw || raw === '[DONE]') continue

      let j: { token?: string; done?: boolean; error?: string }
      try {
        j = JSON.parse(raw) as { token?: string; done?: boolean; error?: string }
      } catch {
        if (!errEvent) onToken(raw)
        continue
      }

      if (errEvent || j.error) {
        throw new Error(typeof j.error === 'string' ? j.error : '流式推理出错')
      }
      if (j.token) onToken(j.token)
      if (j.done) return
    }
  }
}

async function streamGatewayPrompt(
  prompt: string,
  temperature: number,
  topP: number,
  onToken: (t: string) => void,
): Promise<void> {
  /** 仅 ai-gateway 提供该路由；不可拼 VITE_BACKEND_BASE_URL（Spring 8082 无此接口，会 4xx/5xx） */
  const res = await fetch(PROMPT_TEST_PATH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ prompt, temperature, top_p: topP }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    let msg = text || `流式请求失败: ${res.status}`
    if (text.trim().startsWith('{')) {
      try {
        const j = JSON.parse(text) as { message?: string; detail?: string }
        msg = j.message || j.detail || msg
      } catch {
        /* keep msg */
      }
    }
    const stamped = `HTTP ${res.status}: ${msg}`
    ctxStore.patchContext({ lastError: stamped.slice(0, 800) })
    throw new Error(msg)
  }
  await consumeSseStream(res, onToken)
}

async function askLearningAdvice() {
  loading.value = true
  tips.value = []
  let acc = ''
  try {
    await streamGatewayPrompt(buildAdviceStreamPrompt(), 0.4, 0.85, (t) => {
      acc += t
      tips.value = [acc]
    })

    tips.value = acc.trim() ? [acc.trim()] : ['暂无建议']
    ctxStore.patchContext({ lastError: '' })

    ElMessage.success('导师建议已更新')
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    ctxStore.patchContext({ lastError: msg.slice(0, 800) })
    tips.value = tutorFallbackLines(err)
    ElMessage.warning('导师流式输出失败，已显示排查说明')
  } finally {
    loading.value = false
  }
}

async function askLearningKnowledge() {
  const question = questionInput.value.trim()
  if (!question) {
    ElMessage.warning('请先输入问题')
    return
  }

  chatHistory.value.push({ role: 'user', content: question })
  questionInput.value = ''
  loading.value = true

  // 必须用新对象替换数组项，才能触发视图增量刷新；直接改 push 进去的 plain object 不会响应式更新
  const assistantIndex = chatHistory.value.length
  chatHistory.value.push({ role: 'assistant', content: '' })

  try {
    await streamGatewayPrompt(buildChatStreamPrompt(question), 0.55, 0.9, (t) => {
      const cur = chatHistory.value[assistantIndex]
      if (cur?.role === 'assistant') {
        chatHistory.value[assistantIndex] = { role: 'assistant', content: cur.content + t }
      }
    })
    const finalAssistant = chatHistory.value[assistantIndex]
    if (finalAssistant?.role === 'assistant' && !finalAssistant.content.trim()) {
      chatHistory.value[assistantIndex] = {
        role: 'assistant',
        content: '我暂时没有新的回答，你可以换个角度再问我。',
      }
    }
    ctxStore.patchContext({ lastError: '' })
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    ctxStore.patchContext({ lastError: msg.slice(0, 800) })
    chatHistory.value[assistantIndex] = {
      role: 'assistant',
      content: tutorFallbackLines(err).join('\n'),
    }
    ElMessage.warning('导师流式输出失败，已显示排查说明')
  } finally {
    loading.value = false
  }
}

function clearTips() {
  tips.value = []
}

function clearChat() {
  chatHistory.value = [{ role: 'assistant', content: WELCOME_MESSAGE }]
}

// 自动触发导师弹窗：当学生连续“温度试错失败”时，由 PromptAlchemist 推送信号到 store
watch(
  () => autoTutorRequested.value,
  async (v) => {
    if (!v) return
    tutorMode.value = 'advice'
    if (!expanded.value) expanded.value = true
    await askLearningAdvice()
    autoTutorRequested.value = false
  },
)

// 课程地图等：外部注入聊天 Prompt 并自动发送
watch(
  () => tutorChatNonce.value,
  async () => {
    const prompt = tutorChatSeedPrompt.value.trim()
    if (!prompt) return
    tutorMode.value = 'chat'
    if (!expanded.value) expanded.value = true
    questionInput.value = prompt
    await nextTick()
    await askLearningKnowledge()
  },
)

function tutorMarkdownMountEl(): HTMLElement | null {
  return document.querySelector('.global-ai-tutor')
}

watch(
  () => [chatHistory.value, tips.value, tutorMode.value, loading.value],
  () => {
    const root = tutorMarkdownMountEl()
    scheduleMermaid(root, loading.value ? 480 : 32)
  },
  { deep: true },
)

watch(
  expanded,
  (open) => {
    if (open) {
      void nextTick(() => scheduleMermaid(tutorMarkdownMountEl(), 60))
    }
  },
)

function applyMermaidTheme() {
  mermaid.initialize({
    startOnLoad: false,
    theme: theme.value === 'dark' ? 'dark' : 'neutral',
    securityLevel: 'loose',
    fontFamily: 'inherit',
  })
}

watch(theme, () => {
  applyMermaidTheme()
  scheduleMermaid(tutorMarkdownMountEl(), 80)
})

onMounted(() => {
  applyMermaidTheme()
  document.addEventListener('mouseup', onDocumentMouseUp)

  if (props.embedded) return
  const firstVisitFlag = localStorage.getItem(firstVisitStorageKey)
  isFirstVisit.value = firstVisitFlag === null || firstVisitFlag === '1'

  if (isFirstVisit.value) {
    localStorage.setItem(firstVisitStorageKey, '1')
    window.setTimeout(() => {
      showTour.value = true
    }, 400)
  }
})

onUnmounted(() => {
  document.removeEventListener('mouseup', onDocumentMouseUp)
  window.clearTimeout(mermaidScheduled)
})
</script>

<style scoped>
/* 容器不拦截点击，避免挡住知识图谱等主界面；引导与悬浮控件单独恢复点击 */
.global-ai-tutor {
  position: fixed;
  right: 22px;
  bottom: 26px;
  z-index: 1200;
  pointer-events: none;
}

.global-ai-tutor__tour {
  pointer-events: auto;
}

.global-ai-tutor__controls {
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.infra-bubble {
  width: min(340px, calc(100vw - 48px));
  margin-bottom: 10px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid var(--tutor-infra-border);
  background: var(--tutor-infra-bg);
  box-shadow: var(--shadow-md);
  text-align: left;
  transition: background 0.25s ease, border-color 0.2s ease;
}

.infra-bubble--embedded {
  width: 100%;
  margin: 0 0 12px;
}

.infra-bubble__title {
  font-size: 14px;
  font-weight: 800;
  color: #fca5a5;
  margin: 0 0 6px;
}

html[data-theme='light'] .infra-bubble__title {
  color: #9a3412;
}

.infra-bubble__lead {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.45;
}

.infra-bubble__list {
  margin: 0 0 10px;
  padding-left: 18px;
  font-size: 11px;
  color: var(--text-primary);
  line-height: 1.5;
}

.infra-bubble__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selection-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(360px, calc(100vw - 40px));
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 999px;
  border: 1px solid var(--accent-primary);
  background: var(--bg-overlay);
  box-shadow: var(--shadow-md);
  pointer-events: auto;
}

.selection-chip--embedded {
  align-self: stretch;
  max-width: none;
  border-radius: 14px;
}

.selection-chip__preview {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  color: var(--accent-link);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.selection-chip__close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--text-muted);
  padding: 0 4px;
}

.tutor-md :deep(h1),
.tutor-md :deep(h2),
.tutor-md :deep(h3) {
  margin: 0.6em 0 0.35em;
  font-weight: 800;
  line-height: 1.25;
  color: var(--tutor-md-heading);
}

.tutor-md :deep(h1) {
  font-size: 1.05rem;
}
.tutor-md :deep(h2) {
  font-size: 1rem;
}
.tutor-md :deep(h3) {
  font-size: 0.95rem;
}

.tutor-md :deep(p) {
  margin: 0.4em 0;
  line-height: 1.55;
  color: var(--text-primary);
}

.tutor-md :deep(ul),
.tutor-md :deep(ol) {
  margin: 0.35em 0;
  padding-left: 1.25em;
}

.tutor-md :deep(pre:not(.mermaid)) {
  margin: 0.5em 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--bg-code);
  color: var(--text-on-dark);
  font-size: 11px;
  overflow: auto;
  border: 1px solid var(--border-subtle);
}

.tutor-md :deep(code) {
  font-family: ui-monospace, monospace;
  font-size: 0.9em;
}

.tutor-md :deep(p code),
.tutor-md :deep(li code) {
  background: var(--tutor-inline-code-bg);
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--text-primary);
}

.tutor-md :deep(.tutor-mermaid-block) {
  margin: 10px 0;
  padding: 10px;
  border-radius: 12px;
  background: var(--tutor-mermaid-bg);
  border: 1px solid var(--tutor-mermaid-border);
  overflow: auto;
}

.tutor-md :deep(.tutor-mermaid-block svg) {
  max-width: 100%;
  height: auto;
}

.chat-content--plain {
  white-space: pre-wrap;
}

.chat-content--md {
  white-space: normal;
}

.tips-body {
  padding: 10px 12px 12px;
  font-size: 13px;
  line-height: 1.55;
}

.floating-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: 1px solid var(--tutor-floating-border);
  border-radius: 999px;
  background: var(--tutor-floating-bg);
  color: var(--tutor-floating-text);
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

.floating-btn:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-1px);
}

.icon {
  font-size: 18px;
}

.tutor-panel {
  width: 380px;
  margin-top: 12px;
  border: 1px solid var(--tutor-panel-border);
  border-radius: 16px;
  background: var(--tutor-panel-bg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  transition: background 0.25s ease, border-color 0.2s ease;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.panel-meta {
  line-height: 1.45;
}

.panel-meta code {
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--tutor-inline-code-bg);
  color: var(--text-primary);
}

.context-aware-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 14px 10px;
  padding: 7px 11px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--accent-link);
  background: var(--bg-overlay);
  border: 1px solid var(--accent-primary);
  border-radius: 10px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: var(--shadow-sm);
}

.context-aware-strip__ico {
  flex-shrink: 0;
  color: var(--accent-primary);
}

.context-aware-strip__text {
  line-height: 1.35;
}

.tutor-panel--embedded .context-aware-strip {
  margin: 0 14px 10px;
  background: var(--bg-overlay);
  border-color: var(--border-default);
}

.mode-switch {
  display: flex;
  gap: 8px;
  padding: 10px 14px 0;
}

.mode-btn {
  border: 1px solid var(--border-default);
  background: var(--bg-elevated);
  color: var(--text-primary);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.mode-btn.active {
  background: var(--accent-primary-soft);
  border-color: var(--accent-primary);
  color: var(--accent-link);
  font-weight: 700;
}

html[data-theme='light'] .mode-btn.active {
  color: #1d4ed8;
}

.close-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  color: var(--text-muted);
}

.snapshot-box {
  margin: 12px 14px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--bg-muted);
}

.snapshot-title {
  font-size: 12px;
  color: var(--text-secondary);
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.snapshot-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px;
}

.snapshot-item {
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  background: var(--bg-elevated);
  padding: 8px;
}

.snapshot-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.snapshot-value {
  font-size: 12px;
  color: var(--text-primary);
  line-height: 1.4;
}

.snapshot-value.danger {
  color: #f87171;
  font-weight: 700;
}

html[data-theme='light'] .snapshot-value.danger {
  color: #b91c1c;
}

.chat-section {
  margin: 12px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-list {
  max-height: 220px;
  overflow: auto;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  padding: 8px;
  background: var(--bg-muted);
}

.chat-empty {
  border: 1px dashed var(--border-default);
  border-radius: 10px;
  padding: 10px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-muted);
}

.chat-item {
  margin-bottom: 8px;
}

.chat-role {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 2px;
}

.chat-content {
  white-space: pre-wrap;
  line-height: 1.5;
  font-size: 13px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
}

.chat-item.user .chat-content {
  background: var(--tutor-chat-user-bg);
  color: var(--tutor-chat-user-fg);
  border-color: rgba(16, 163, 127, 0.25);
}

.chat-item.assistant .chat-content {
  background: var(--tutor-chat-assistant-bg);
  color: var(--tutor-chat-assistant-fg);
  border-color: rgba(99, 102, 241, 0.2);
}

.actions {
  display: flex;
  gap: 10px;
  padding: 2px 14px 10px;
}

.tips-box {
  margin: 0 14px 14px;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  background: var(--bg-muted);
}

.tips-title {
  font-size: 12px;
  color: var(--accent-link);
  padding: 8px 10px;
  border-bottom: 1px solid var(--border-subtle);
}

.tips-box ul {
  margin: 0;
  padding: 10px 20px 12px;
}

.tips-box li {
  margin-bottom: 6px;
  color: var(--text-primary);
  line-height: 1.5;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.24s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

.none-enter-active,
.none-leave-active {
  transition: none;
}

.global-ai-tutor--embedded {
  position: relative;
  right: auto;
  bottom: auto;
  z-index: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
  pointer-events: auto;
}

.global-ai-tutor__controls--embedded {
  width: 100%;
  height: 100%;
  min-height: 0;
  align-items: stretch;
}

.tutor-panel--embedded {
  width: 100%;
  max-width: none;
  height: 100%;
  min-height: 0;
  margin-top: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-overlay);
  backdrop-filter: blur(16px);
  border: 1px solid var(--tutor-panel-border);
  box-shadow: var(--shadow-md);
}

.tutor-panel--embedded .panel-header {
  flex-shrink: 0;
}

.tutor-panel--embedded .chat-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin: 12px 14px 8px;
}

.tutor-panel--embedded .chat-list {
  flex: 1;
  min-height: 120px;
  max-height: none;
}

.tutor-panel--embedded .actions {
  flex-shrink: 0;
}
</style>

