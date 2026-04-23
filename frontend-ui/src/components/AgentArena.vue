<template>
  <div class="agent-arena">
    <header class="arena-header">
      <div class="arena-header__titles">
        <h2>综合实验：基于多智能体(Agent)的软件工程自动化案例</h2>
        <p class="arena-hint">
          需启动后端（含 WebSocket）；黑板内容由「开始协作演示」触发。角色卡片可随时点击切换视角。
        </p>
      </div>
      <div class="arena-header__actions">
        <div class="arena-header__buttons">
          <el-button type="primary" size="small" :loading="demoLoading" :disabled="!wsConnected" @click="startCollaborationDemo">
            开始协作演示
          </el-button>
          <el-button size="small" :icon="Download" @click="exportCrewAiScript">
            导出 CrewAI 脚本
          </el-button>
        </div>
        <div
          class="status"
          :title="
            wsConnected
              ? 'WebSocket 已接通，可点击「开始协作演示」拉取多智能体发言。'
              : '未连接：请确认 backend-services 已启动，且开发环境通过 Vite 代理 /ws（或直接指向正确端口）。'
          "
        >
          <span :class="['dot', wsConnected ? 'online' : 'offline']"></span>
          {{ wsConnected ? '已连接' : '未连接' }}
        </div>
      </div>
    </header>

    <section class="case-brief">
      <p class="case-brief__title">【案例背景】</p>
      <p>
        本案例模拟企业真实 Code Review 与优化流程。引入多智能体协同机制，设立“生成者”、“审查员”与“指令优化师”角色，突破单体模型能力瓶颈。
      </p>
    </section>

    <section class="agent-row">
      <button
        v-for="agent in agents"
        :key="agent.role"
        type="button"
        :class="['agent-card', { active: activeRole === agent.role }]"
        @click="selectAgent(agent.role)"
      >
        <div class="avatar">{{ agent.emoji }}</div>
        <div class="meta">
          <div class="name">{{ agent.name }}</div>
          <div class="role">{{ agent.role }}</div>
        </div>
      </button>
    </section>

    <section class="arena-metrics">
      <div class="metric-pill">
        <span class="metric-pill__label">总消息</span>
        <strong>{{ totalMessageCount }}</strong>
      </div>
      <div class="metric-pill metric-pill--warn">
        <span class="metric-pill__label">监督层消息</span>
        <strong>{{ reviewerMessageCount }}</strong>
      </div>
      <div class="metric-pill metric-pill--focus">
        <span class="metric-pill__label">反思链命中</span>
        <strong>{{ reflectionMessageCount }}</strong>
      </div>
      <div class="metric-pill metric-pill--ratio">
        <span class="metric-pill__label">反思链命中率</span>
        <strong>{{ reflectionHitRate }}</strong>
      </div>
    </section>

    <section class="blackboard-panel">
      <div class="blackboard-title">
        <span>协作黑板</span>
        <div class="review-toggle">
          <span class="review-toggle__label">评审模式</span>
          <el-switch v-model="reviewFriendlyMode" inline-prompt active-text="仅监督层" inactive-text="全部消息" />
        </div>
      </div>
      <div ref="boardRef" class="blackboard-content">
        <div v-for="line in displayBoardLines" :key="line.id" class="line-item">
          <template v-if="isReflectionLine(line)">
            <el-collapse class="reflection-collapse">
              <el-collapse-item :name="line.id">
                <template #title>
                  <span class="reflection-title">🔧 展开查看 Agent 内部反思与打磨链 (Reflection Chain)</span>
                </template>
                <div class="reflection-body">
                  <span class="line-role">[{{ line.agentName }}]</span>
                  <el-tag :type="roleTagType(line.role)" size="small" effect="dark">{{ roleTagText(line.role) }}</el-tag>
                  <p class="line-text line-text--block">{{ line.text }}</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </template>
          <template v-else>
            <span class="line-role">[{{ line.agentName }}]</span>
            <el-tag :type="roleTagType(line.role)" size="small" effect="dark">{{ roleTagText(line.role) }}</el-tag>
            <span class="line-text">{{ line.text }}</span>
          </template>
        </div>
        <div v-if="typingBuffer && (!reviewFriendlyMode || activeRole === 'reviewer')" class="line-item typing">
          <span class="line-role">[{{ activeAgentName }}]</span>
          <el-tag :type="roleTagType(activeRole)" size="small" effect="dark">{{ roleTagText(activeRole) }}</el-tag>
          <span class="line-text">{{ typingBuffer }}<span class="cursor">|</span></span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { runAgentArenaDemo } from '@/api/modules/ai'
import { buildCrewAiPythonScript, downloadPythonFile, type CrewAiExportAgent, type CrewAiExportEdge } from '@/utils/exportToCrewAI'

type AgentRole = 'analyst' | 'reviewer' | 'writer'

interface AgentProfile {
  role: AgentRole
  name: string
  emoji: string
  goal: string
  backstory: string
}

interface WsMessage {
  role: AgentRole
  content: string
  event?: 'thinking' | 'final'
}

interface BoardLine {
  id: string
  role: AgentRole
  agentName: string
  text: string
}

const COLLABORATION_TOPIC = '请围绕一个 RAG 实验流程进行协作分析'

/**
 * 当前竞技场「画布」上的智能体节点（固定三角色）及流水线依赖：分析师 → 审查员 → 撰写者。
 * 导出时映射为 CrewAI 的 Agent 与带 context 的 Task 顺序。
 */
const agents: AgentProfile[] = [
  {
    role: 'analyst',
    name: '数据分析师',
    emoji: '📊',
    goal: '从原始材料中提炼可验证的要点、指标与实验假设，形成结构化分析结论。',
    backstory:
      '你长期负责数据与实验设计，习惯先澄清问题边界再输出条理清晰的分析框架，供团队后续评审与成文引用。',
  },
  {
    role: 'reviewer',
    name: '审查员',
    emoji: '🛡️',
    goal: '在分析师结论基础上识别逻辑漏洞、风险与偏见，提出可执行的修订建议。',
    backstory:
      '你扮演质量与合规视角，关注论证链是否闭合、假设是否过强，并确保结论与证据匹配。',
  },
  {
    role: 'writer',
    name: '撰写者',
    emoji: '✍️',
    goal: '整合分析与审查意见，输出面向读者的终稿说明（可分节、条列，便于落地）。',
    backstory:
      '你擅长把多角色意见收敛成一篇连贯文档，保留关键取舍理由并突出行动建议。',
  },
]

const AGENT_PIPELINE_EDGES: { source: AgentRole; target: AgentRole }[] = [
  { source: 'analyst', target: 'reviewer' },
  { source: 'reviewer', target: 'writer' },
]

const props = withDefaults(
  defineProps<{
    wsUrl?: string
    typingIntervalMs?: number
    maxLines?: number
  }>(),
  {
    wsUrl: '',
    typingIntervalMs: 18,
    maxLines: 120,
  },
)

const wsConnected = ref(false)
const activeRole = ref<AgentRole>('analyst')
const boardLines = ref<BoardLine[]>([])
const typingBuffer = ref('')
const boardRef = ref<HTMLDivElement | null>(null)
const demoLoading = ref(false)
const reviewFriendlyMode = ref(false)

let ws: WebSocket | null = null
let typingTimer: number | null = null
const pendingQueue: WsMessage[] = []
let isTyping = false
let reconnectTimer: number | null = null
let disposed = false

const activeAgentName = computed(() => agents.find((a) => a.role === activeRole.value)?.name || 'Agent')
const displayBoardLines = computed(() =>
  reviewFriendlyMode.value ? boardLines.value.filter((line) => isReflectionLine(line)) : boardLines.value,
)
const totalMessageCount = computed(() => boardLines.value.length)
const reviewerMessageCount = computed(() => boardLines.value.filter((line) => line.role === 'reviewer').length)
const reflectionMessageCount = computed(() => boardLines.value.filter((line) => isReflectionLine(line)).length)
const reflectionHitRate = computed(() => {
  if (totalMessageCount.value === 0) return '0%'
  return `${Math.round((reflectionMessageCount.value / totalMessageCount.value) * 100)}%`
})

function roleTagText(role: AgentRole): '[执行层]' | '[监督层]' {
  return role === 'reviewer' ? '[监督层]' : '[执行层]'
}

function roleTagType(role: AgentRole): 'primary' | 'warning' {
  return role === 'reviewer' ? 'warning' : 'primary'
}

function isReflectionLine(line: BoardLine): boolean {
  if (line.role === 'reviewer') return true
  const text = line.text.toLowerCase()
  return /critic|review|reviewer|反思|打磨|复盘|审查|纠错|风险|修订/.test(text)
}

function resolveWsUrl(): string {
  if (props.wsUrl && props.wsUrl.trim().length > 0) return props.wsUrl.trim()
  const fromEnv = import.meta.env.VITE_AGENT_WS_URL
  if (typeof fromEnv === 'string' && fromEnv.trim().length > 0) return fromEnv.trim()
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    const { protocol, hostname, port } = window.location
    const wsProto = protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProto}//${hostname}:${port}/ws/agent-arena`
  }
  return 'ws://127.0.0.1:8082/ws/agent-arena'
}

function selectAgent(role: AgentRole) {
  activeRole.value = role
}

function collectArenaGraphForExport(): { exportAgents: CrewAiExportAgent[]; exportEdges: CrewAiExportEdge[] } {
  const exportAgents: CrewAiExportAgent[] = agents.map((a) => ({
    id: a.role,
    role: `${a.name}（${a.role}）`,
    displayName: `${a.name} · ${a.role}`,
    goal: a.goal,
    backstory: a.backstory,
  }))
  const exportEdges: CrewAiExportEdge[] = AGENT_PIPELINE_EDGES.map((e) => ({
    sourceId: e.source,
    targetId: e.target,
  }))
  return { exportAgents, exportEdges }
}

function exportCrewAiScript() {
  const { exportAgents, exportEdges } = collectArenaGraphForExport()
  const py = buildCrewAiPythonScript(exportAgents, exportEdges, {
    sceneDescription: 'Agent Arena 虚拟黑板 · 多智能体流水线导出',
    topic: COLLABORATION_TOPIC,
  })
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  downloadPythonFile(`agent_arena_crewai_${stamp}.py`, py)
  ElMessage.success('已生成并下载 CrewAI Python 脚本')
}

async function startCollaborationDemo() {
  if (!wsConnected.value) {
    ElMessage.warning('请先等待 WebSocket 连接成功')
    return
  }
  demoLoading.value = true
  try {
    await runAgentArenaDemo({ topic: COLLABORATION_TOPIC })
    ElMessage.success('演示已触发，黑板将逐条出现各角色发言')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '触发演示失败，请检查后端与 AI 网关')
  } finally {
    demoLoading.value = false
  }
}

function getAgentName(role: AgentRole) {
  return agents.find((a) => a.role === role)?.name || role
}

function scrollToBottom() {
  if (!boardRef.value) return
  boardRef.value.scrollTop = boardRef.value.scrollHeight
}

function enqueueMessage(msg: WsMessage) {
  pendingQueue.push(msg)
  if (!isTyping) consumeQueue()
}

function consumeQueue() {
  if (pendingQueue.length === 0) {
    isTyping = false
    return
  }
  isTyping = true

  const current = pendingQueue.shift()!
  activeRole.value = current.role
  typingBuffer.value = ''

  let idx = 0
  const content = current.content ?? ''
  const step = () => {
    idx += 1
    typingBuffer.value = content.slice(0, idx)
    scrollToBottom()

    if (idx >= content.length) {
      boardLines.value.push({
        id: `${Date.now()}-${Math.random()}`,
        role: current.role,
        agentName: getAgentName(current.role),
        text: content,
      })
      if (boardLines.value.length > props.maxLines) {
        boardLines.value.splice(0, boardLines.value.length - props.maxLines)
      }
      typingBuffer.value = ''
      window.setTimeout(consumeQueue, 80)
      return
    }
    typingTimer = window.setTimeout(step, props.typingIntervalMs)
  }
  step()
}

function disconnectWs() {
  if (reconnectTimer != null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.close()
    ws = null
  }
  wsConnected.value = false
}

function connectWs() {
  disconnectWs()
  if (disposed) return

  const url = resolveWsUrl()
  try {
    ws = new WebSocket(url)
  } catch {
    wsConnected.value = false
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    wsConnected.value = true
  }

  ws.onclose = () => {
    wsConnected.value = false
    if (!disposed) scheduleReconnect()
  }

  ws.onerror = () => {
    wsConnected.value = false
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as WsMessage
      if (!data?.role || !data?.content) return
      enqueueMessage(data)
    } catch {
      // 忽略非法消息
    }
  }
}

function scheduleReconnect() {
  if (reconnectTimer != null) window.clearTimeout(reconnectTimer)
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectWs()
  }, 1800)
}

onMounted(() => {
  disposed = false
  connectWs()
})

watch(
  () => props.wsUrl,
  () => {
    if (disposed) return
    connectWs()
  },
)

onBeforeUnmount(() => {
  disposed = true
  if (typingTimer) window.clearTimeout(typingTimer)
  disconnectWs()
})
</script>

<style scoped>
.agent-arena {
  border: 1px solid #dbe4f0;
  border-radius: 16px;
  background: #fff;
  overflow: hidden;
}

.arena-header {
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  border-bottom: 1px solid #edf2f8;
}

.arena-header__titles {
  min-width: 0;
  flex: 1;
}

.arena-header h2 {
  margin: 0;
  font-size: 18px;
}

.arena-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.arena-header__actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.arena-header__buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.case-brief {
  margin: 12px 18px 0;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  border-radius: 12px;
  color: #78350f;
  font-size: 13px;
  line-height: 1.6;
}

.case-brief p {
  margin: 0;
}

.case-brief__title {
  font-weight: 700;
  margin-bottom: 4px !important;
}

.status {
  font-size: 12px;
  color: #4b5563;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.dot.online {
  background: #22c55e;
}

.dot.offline {
  background: #ef4444;
}

.agent-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #edf2f8;
}

.arena-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px 18px;
  border-bottom: 1px solid #edf2f8;
  background: #f8fafc;
}

.metric-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1e3a8a;
  font-size: 12px;
}

.metric-pill strong {
  font-size: 14px;
}

.metric-pill__label {
  opacity: 0.92;
}

.metric-pill--warn {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.metric-pill--focus {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.metric-pill--ratio {
  border-color: #c4b5fd;
  background: #f5f3ff;
  color: #5b21b6;
}

.agent-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e7eef6;
  border-radius: 12px;
  background: #f8fbff;
  transition: all 0.2s ease;
  cursor: pointer;
  text-align: left;
  font: inherit;
  width: 100%;
  box-sizing: border-box;
}

.agent-card:hover {
  border-color: #c7d7f0;
  background: #f0f5ff;
}

.agent-card.active {
  border-color: #7c9dff;
  box-shadow: 0 0 0 2px rgb(124 157 255 / 16%);
  background: #eef3ff;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #d9e4f2;
  font-size: 20px;
}

.name {
  font-weight: 600;
  color: #1f2937;
}

.role {
  font-size: 12px;
  color: #6b7280;
}

.blackboard-panel {
  background: radial-gradient(circle at top, #1b2f2a 0%, #0f1b18 42%, #0a1311 100%);
  color: #dff6ea;
}

.blackboard-title {
  padding: 10px 16px;
  font-size: 13px;
  border-bottom: 1px solid rgb(219 234 254 / 15%);
  color: #b7f0d4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.review-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #d1fae5;
}

.review-toggle__label {
  font-size: 12px;
  color: #a7f3d0;
}

.review-toggle :deep(.el-switch__label) {
  color: #e2e8f0;
}

.blackboard-content {
  height: 340px;
  overflow: auto;
  padding: 12px 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.line-item {
  margin-bottom: 10px;
  line-height: 1.55;
}

.line-role {
  color: #7dd3fc;
  margin-right: 8px;
}

.line-item :deep(.el-tag) {
  margin-right: 8px;
  vertical-align: middle;
}

.line-text--block {
  margin: 8px 0 0;
  white-space: pre-wrap;
  color: #e3f9ec;
}

.reflection-collapse {
  border: 1px dashed #facc15;
  border-radius: 10px;
  background: rgb(250 204 21 / 8%);
}

.reflection-collapse :deep(.el-collapse-item__header) {
  background: transparent;
  border-bottom: 1px dashed rgb(250 204 21 / 45%);
  color: #fde68a;
  font-weight: 600;
  padding: 0 10px;
}

.reflection-collapse :deep(.el-collapse-item__wrap) {
  background: transparent;
}

.reflection-body {
  padding: 6px 10px 8px;
}

.reflection-title {
  font-size: 12px;
}

.typing .line-text {
  color: #e3f9ec;
}

.cursor {
  margin-left: 2px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
