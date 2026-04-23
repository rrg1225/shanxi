import { defineStore } from 'pinia'
import { ref } from 'vue'

export type WorkbenchUserMode = 'beginner' | 'expert'

/** 全局浅色 / 深色主题，持久化至 localStorage */
export type AppUiTheme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'ai-workbench-theme'

export function readStoredTheme(): AppUiTheme {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (raw === 'dark' || raw === 'light') return raw
  } catch {
    /* ignore */
  }
  return 'light'
}

export function applyThemeToDocument(theme: AppUiTheme) {
  if (typeof document === 'undefined') return
  document.documentElement.setAttribute('data-theme', theme)
}

/** 每多少经验升一级（本级内进度条 0–99 对应余数） */
export const EXP_PER_LEVEL = 100

export type WorkbenchTaskType = 'visit_page' | 'use_prompt' | 'use_agent'

export interface WorkbenchTodayTask {
  id: string
  title: string
  type: WorkbenchTaskType
  done: boolean
  expReward: number
}

export interface WorkbenchActivityItem {
  id: string
  label: string
  path: string
  at: number
}

export interface StudyRecordItem {
  id: string
  date: string
  label: string
  path: string
  content: string
  durationSec: number
  at: number
}

/** 全局 AI 导师：当前页面上下文（由各工作台页面注入） */
export interface CurrentActiveContext {
  page: string
  data: unknown
}

/** 埋点类型 → 雷达图维度：提示词工程 / 知识检索 / Agent 编排 / 理论基础 */
export type ActivityKind = 'prompt' | 'rag' | 'agent' | 'read'

export interface ActivityLogEntry {
  /** 本地日历日 YYYY-MM-DD */
  date: string
  type: ActivityKind
  expReward: number
  at: number
}

export interface SkillRadarScores {
  prompt: number
  rag: number
  agent: number
  read: number
}

/** RAG 蓝图 ↔ 3D 向量空间：模拟 chunk 点（与 VectorSpace3D ChunkPoint 对齐） */
export interface RagSimulatedChunkPoint {
  id: string | number
  label?: string
  x: number
  y: number
  z: number
  cluster?: string | number
  score?: number
  intro?: string
}

export interface RagPipelineParams {
  chunkSize: number
  chunkOverlap: number
  topK: number
  embeddingModel: string
}

/** 「测试检索」执行流：驱动 3D 查询向量 → Top-K 高亮连线动画 */
export interface RagRetrievalDemoState {
  nonce: number
  query: string
  hits: { chunkId: string | number; score?: number }[]
  startedAt: number
}

/**
 * 由 chunk 策略生成 3D 点云密度/分布（chunk 越小点越多；overlap 越大簇越紧）
 */
export function generateRagSimulatedChunks(
  chunkSize: number,
  chunkOverlap: number,
): RagSimulatedChunkPoint[] {
  const size = Math.min(2000, Math.max(200, Math.round(Number(chunkSize) || 800)))
  const overlap = Math.min(size - 1, Math.max(0, Math.round(Number(chunkOverlap) || 0)))
  const overlapRatio = size > 0 ? overlap / size : 0
  /** 略压低上限 + 平滑曲线，小块时不会瞬间挤满一坨点 */
  const density = Math.min(
    72,
    Math.max(14, Math.round(26000 / (size + 140))),
  )
  const clusterCount = Math.max(3, Math.round(6 + overlapRatio * 14))
  /** 点变多时整体「摊开」，避免叠在同一片弧带上 */
  const volumeBoost = 1 + (density / 72) * 0.42
  const out: RagSimulatedChunkPoint[] = []
  let idx = 0
  for (let c = 0; c < clusterCount && out.length < density; c += 1) {
    const baseAngle = (c / clusterCount) * Math.PI * 2
    const radial = (8 + c * 5 + (1 - overlapRatio) * 20) * volumeBoost
    const ptsInCluster = Math.max(2, Math.floor(density / clusterCount))
    for (let k = 0; k < ptsInCluster && out.length < density; k += 1) {
      const jitter = ((1 - overlapRatio) * 7 + density * 0.028) * volumeBoost
      const ang = baseAngle + (k / Math.max(1, ptsInCluster)) * 0.9
      const rr = radial + (Math.sin(idx * 12.9898) * 0.5 + 0.5 - 0.5) * jitter
      const x = rr * Math.cos(ang)
      const z = rr * Math.sin(ang) * 0.88
      const y =
        (((idx * 17) % 13) - 6 + (overlapRatio - 0.18) * 10) * (0.85 + volumeBoost * 0.35)
      out.push({
        id: `rag-sim-${idx}`,
        label: `Chunk #${idx}`,
        x,
        y,
        z,
        cluster: c,
      })
      idx += 1
    }
  }
  return out
}

const USER_MODE_STORAGE_KEY = 'ai-workbench-user-mode'
const ACTIVITY_STORAGE_KEY = 'ai-workbench-recent-activity-v1'
const USER_EXP_STORAGE_KEY = 'ai-workbench-user-exp-v1'
const TODAY_TASKS_STORAGE_KEY = 'ai-workbench-today-tasks-v1'
const SKILL_RADAR_STORAGE_KEY = 'ai-workbench-skill-radar-v1'
const BEHAVIOR_ACTIVITY_LOG_KEY = 'ai-workbench-behavior-activity-log-v1'
const STUDY_RECORD_STORAGE_KEY = 'ai-workbench-study-record-v1'
const MAX_ACTIVITY_ITEMS = 20
const MAX_BEHAVIOR_LOG = 2500
const MAX_STUDY_RECORDS = 300

const DEFAULT_SKILL_RADAR: SkillRadarScores = {
  prompt: 22,
  rag: 22,
  agent: 22,
  read: 22,
}

function localDateKey(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function readStoredSkillRadar(): SkillRadarScores {
  try {
    const raw = localStorage.getItem(SKILL_RADAR_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_SKILL_RADAR }
    const parsed = JSON.parse(raw) as Partial<SkillRadarScores>
    if (!parsed || typeof parsed !== 'object') return { ...DEFAULT_SKILL_RADAR }
    const merge = (k: keyof SkillRadarScores): number => {
      const n = Number(parsed[k])
      return Number.isFinite(n) ? Math.min(100, Math.max(0, Math.round(n))) : DEFAULT_SKILL_RADAR[k]
    }
    return {
      prompt: merge('prompt'),
      rag: merge('rag'),
      agent: merge('agent'),
      read: merge('read'),
    }
  } catch {
    return { ...DEFAULT_SKILL_RADAR }
  }
}

function persistSkillRadar(scores: SkillRadarScores) {
  try {
    localStorage.setItem(SKILL_RADAR_STORAGE_KEY, JSON.stringify(scores))
  } catch {
    /* ignore */
  }
}

function readStoredBehaviorActivityLog(): ActivityLogEntry[] {
  try {
    const raw = localStorage.getItem(BEHAVIOR_ACTIVITY_LOG_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (x): x is ActivityLogEntry =>
          x &&
          typeof x === 'object' &&
          typeof (x as ActivityLogEntry).date === 'string' &&
          typeof (x as ActivityLogEntry).at === 'number' &&
          typeof (x as ActivityLogEntry).expReward === 'number' &&
          ['prompt', 'rag', 'agent', 'read'].includes(String((x as ActivityLogEntry).type)),
      )
      .slice(-MAX_BEHAVIOR_LOG)
  } catch {
    return []
  }
}

function persistBehaviorActivityLog(entries: ActivityLogEntry[]) {
  try {
    localStorage.setItem(BEHAVIOR_ACTIVITY_LOG_KEY, JSON.stringify(entries.slice(-MAX_BEHAVIOR_LOG)))
  } catch {
    /* ignore */
  }
}

function readStoredStudyRecords(): StudyRecordItem[] {
  try {
    const raw = localStorage.getItem(STUDY_RECORD_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (x): x is StudyRecordItem =>
          x &&
          typeof x === 'object' &&
          typeof (x as StudyRecordItem).id === 'string' &&
          typeof (x as StudyRecordItem).date === 'string' &&
          typeof (x as StudyRecordItem).label === 'string' &&
          typeof (x as StudyRecordItem).path === 'string' &&
          typeof (x as StudyRecordItem).content === 'string' &&
          typeof (x as StudyRecordItem).durationSec === 'number' &&
          typeof (x as StudyRecordItem).at === 'number',
      )
      .slice(0, MAX_STUDY_RECORDS)
  } catch {
    return []
  }
}

function persistStudyRecords(records: StudyRecordItem[]) {
  try {
    localStorage.setItem(STUDY_RECORD_STORAGE_KEY, JSON.stringify(records.slice(0, MAX_STUDY_RECORDS)))
  } catch {
    /* ignore */
  }
}

function inferStudyContentByPath(path: string): string {
  if (path.startsWith('/workbench/prompt')) return '提示词训练与模型调优'
  if (path.startsWith('/workbench/rag-build')) return 'RAG 蓝图与流程设计'
  if (path.startsWith('/workbench/rag-visual')) return '知识检索与3D可视化探索'
  if (path.startsWith('/workbench/agent-arena')) return '多智能体协作实验'
  if (path.startsWith('/workbench/knowledge-universe') || path.startsWith('/workbench/knowledge-map')) return '课程地图与知识星空探索'
  if (path.startsWith('/workbench/marketplace')) return '资源浏览与学习材料筛选'
  if (path.startsWith('/workbench/profile')) return '个人能力复盘与成长分析'
  return '综合学习与工作台浏览'
}

const DEFAULT_TODAY_TASKS: WorkbenchTodayTask[] = [
  {
    id: 'task-knowledge-map',
    title: '访问一次知识星空或资源广场',
    type: 'visit_page',
    done: false,
    expReward: 35,
  },
  {
    id: 'task-prompt-lab',
    title: '在咒语实验室进行一次 Prompt 调试或模型对话',
    type: 'use_prompt',
    done: false,
    expReward: 40,
  },
  {
    id: 'task-agent-arena',
    title: '进入 AI 竞技场体验多智能体协作',
    type: 'use_agent',
    done: false,
    expReward: 35,
  },
]

/** 访问路径 → 今日任务 id（用于自动勾选） */
const ROUTE_TO_TASK_ID: { prefix: string; taskId: string }[] = [
  { prefix: '/workbench/knowledge-universe', taskId: 'task-knowledge-map' },
  { prefix: '/workbench/knowledge-map', taskId: 'task-knowledge-map' },
  { prefix: '/workbench/marketplace', taskId: 'task-knowledge-map' },
  { prefix: '/workbench/prompt', taskId: 'task-prompt-lab' },
  { prefix: '/workbench/agent-arena', taskId: 'task-agent-arena' },
]

function readStoredActivities(): WorkbenchActivityItem[] {
  try {
    const raw = localStorage.getItem(ACTIVITY_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as WorkbenchActivityItem[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function readStoredUserMode(): WorkbenchUserMode {
  try {
    const raw = localStorage.getItem(USER_MODE_STORAGE_KEY)
    if (raw === 'beginner' || raw === 'expert') return raw
  } catch {
    /* ignore */
  }
  return 'beginner'
}

function readStoredUserExp(): number {
  try {
    const raw = localStorage.getItem(USER_EXP_STORAGE_KEY)
    if (raw == null || raw === '') return 0
    const n = Number(raw)
    return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 0
  } catch {
    return 0
  }
}

function persistUserExp(value: number) {
  try {
    localStorage.setItem(USER_EXP_STORAGE_KEY, String(value))
  } catch {
    /* ignore */
  }
}

function readStoredTaskDoneMap(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(TODAY_TASKS_STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object') return {}
    const out: Record<string, boolean> = {}
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (typeof v === 'boolean') out[k] = v
    }
    return out
  } catch {
    return {}
  }
}

function persistTaskDoneMap(tasks: WorkbenchTodayTask[]) {
  try {
    const map: Record<string, boolean> = {}
    for (const t of tasks) map[t.id] = t.done
    localStorage.setItem(TODAY_TASKS_STORAGE_KEY, JSON.stringify(map))
  } catch {
    /* ignore */
  }
}

function mergeTodayTasksFromStorage(): WorkbenchTodayTask[] {
  const doneMap = readStoredTaskDoneMap()
  return DEFAULT_TODAY_TASKS.map((t) => ({
    ...t,
    done: doneMap[t.id] ?? t.done,
  }))
}

/** 由经验值推导等级、本级进度百分比、称号（供看板等使用） */
export function getExpProgressMeta(exp: number) {
  const safe = Number.isFinite(exp) && exp >= 0 ? Math.floor(exp) : 0
  const level = Math.floor(safe / EXP_PER_LEVEL) + 1
  const percent = safe % EXP_PER_LEVEL
  const titles = [
    '见习学徒',
    '见习指挥官',
    '熟练协作者',
    '进阶参谋',
    '架构操盘手',
    '传奇向导',
  ]
  const title = titles[Math.min(level - 1, titles.length - 1)] ?? '成长旅人'
  return { level, percent, title }
}

export const useGlobalLearningContextStore = defineStore('globalLearningContext', () => {
  const currentModule = ref('RAG 蓝图')
  const lastError = ref('')
  const recentAction = ref('进入实验页面')
  const userLevel = ref('beginner')
  /** 全局主题：与 html[data-theme] 联动 */
  const theme = ref<AppUiTheme>(readStoredTheme())
  applyThemeToDocument(theme.value)
  /** 工作台 UI：新手看板 vs 极客全功能 */
  const userMode = ref<WorkbenchUserMode>(readStoredUserMode())
  const extraContext = ref<Record<string, unknown>>({})

  // Tutor 自动触发信号：当学生在同一步连续多次试错且结果不理想
  const temperatureTrialStreak = ref(0)
  const probabilityTreeFailureStreak = ref(0)
  const autoTutorRequested = ref(false)

  /** 课程地图等场景：向 AI 导师注入一条聊天并自动发送（GlobalAiTutor 监听 nonce） */
  const tutorChatSeedPrompt = ref('')
  const tutorChatNonce = ref(0)

  /** 上下文感知：用户当前所在页面及页面相关数据（供 GlobalAiTutor 拼接隐式 system 提示） */
  const currentActiveContext = ref<CurrentActiveContext | null>(null)

  const recentActivities = ref<WorkbenchActivityItem[]>(readStoredActivities())

  const userExp = ref(readStoredUserExp())
  const todayTasks = ref<WorkbenchTodayTask[]>(mergeTodayTasksFromStorage())

  /** 四维技能点（0–100），供个人中心雷达图 */
  const skillRadar = ref<SkillRadarScores>(readStoredSkillRadar())
  /** 行为埋点日志（按日聚合热力/柱状图） */
  const activityLog = ref<ActivityLogEntry[]>(readStoredBehaviorActivityLog())
  /** 学习时长与学习内容记录 */
  const studyRecords = ref<StudyRecordItem[]>(readStoredStudyRecords())
  const activeStudyStartedAt = ref<number | null>(null)
  const activeStudyPath = ref('')
  const activeStudyLabel = ref('')

  /** RAG 蓝图 / 3D 联动 */
  const ragPipelineParams = ref<RagPipelineParams>({
    chunkSize: 800,
    chunkOverlap: 150,
    topK: 3,
    embeddingModel: 'text-embedding-v3',
  })
  const ragSimulatedChunks = ref<RagSimulatedChunkPoint[]>(
    generateRagSimulatedChunks(800, 150),
  )
  /** 递增以通知订阅方「向量空间已按新策略重算」 */
  const ragVectorSpaceRevision = ref(0)
  const ragRetrievalDemo = ref<RagRetrievalDemoState | null>(null)

  function applyRagChunkStrategy(chunkSize: number, chunkOverlap: number) {
    const cs = Math.min(2000, Math.max(200, Math.round(chunkSize)))
    const co = Math.min(cs - 1, Math.max(0, Math.round(chunkOverlap)))
    ragPipelineParams.value = {
      ...ragPipelineParams.value,
      chunkSize: cs,
      chunkOverlap: co,
    }
    ragSimulatedChunks.value = generateRagSimulatedChunks(cs, co)
    ragVectorSpaceRevision.value += 1
  }

  function setRagPipelineParams(partial: Partial<RagPipelineParams>) {
    if (partial.chunkSize != null || partial.chunkOverlap != null) {
      applyRagChunkStrategy(
        partial.chunkSize ?? ragPipelineParams.value.chunkSize,
        partial.chunkOverlap ?? ragPipelineParams.value.chunkOverlap,
      )
    }
    if (partial.topK != null) {
      ragPipelineParams.value.topK = Math.min(12, Math.max(1, Math.round(partial.topK)))
    }
    if (partial.embeddingModel != null) {
      ragPipelineParams.value.embeddingModel = partial.embeddingModel
    }
  }

  /** 以当前模拟点云为「库」，从原点查询向量演示 Top-K（供 3D 高亮流） */
  function triggerRagRetrievalSimulation(query?: string) {
    const k = Math.min(12, Math.max(1, ragPipelineParams.value.topK))
    const chunks = ragSimulatedChunks.value
    const q = (query ?? 'RAG 蓝图：测试检索').trim()
    const scored = chunks
      .map((c) => ({ c, dist: Math.sqrt(c.x * c.x + c.y * c.y + c.z * c.z) }))
      .sort((a, b) => a.dist - b.dist)
      .slice(0, k)
    ragRetrievalDemo.value = {
      nonce: Date.now(),
      query: q,
      hits: scored.map((s, i) => ({
        chunkId: s.c.id,
        score: Math.max(0.52, 1 - i * 0.07),
      })),
      startedAt: typeof performance !== 'undefined' ? performance.now() : Date.now(),
    }
  }

  function clearRagRetrievalDemo() {
    ragRetrievalDemo.value = null
  }

  function flushActiveStudyRecord() {
    if (!activeStudyStartedAt.value || !activeStudyPath.value) return
    const now = Date.now()
    const durationSec = Math.floor((now - activeStudyStartedAt.value) / 1000)
    activeStudyStartedAt.value = null
    if (durationSec < 15 || durationSec > 4 * 3600) return

    const record: StudyRecordItem = {
      id: `${now}-${Math.random().toString(36).slice(2, 9)}`,
      date: localDateKey(new Date(now)),
      label: activeStudyLabel.value || '学习活动',
      path: activeStudyPath.value,
      content: inferStudyContentByPath(activeStudyPath.value),
      durationSec,
      at: now,
    }
    studyRecords.value = [record, ...studyRecords.value].slice(0, MAX_STUDY_RECORDS)
    persistStudyRecords(studyRecords.value)
  }

  function transitionStudyContext(payload: { label: string; path: string }) {
    const path = payload.path.split('?')[0]
    if (activeStudyPath.value && activeStudyPath.value !== path) {
      flushActiveStudyRecord()
    }
    activeStudyPath.value = path
    activeStudyLabel.value = payload.label
    activeStudyStartedAt.value = Date.now()
  }

  function recordWorkbenchVisit(payload: { label: string; path: string }) {
    const path = payload.path.split('?')[0]
    const item: WorkbenchActivityItem = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      label: payload.label,
      path,
      at: Date.now(),
    }
    const rest = recentActivities.value.filter((x) => x.path !== path)
    recentActivities.value = [item, ...rest].slice(0, MAX_ACTIVITY_ITEMS)
    try {
      localStorage.setItem(ACTIVITY_STORAGE_KEY, JSON.stringify(recentActivities.value))
    } catch {
      /* ignore */
    }
  }

  /** 根据当前访问的工作台路径，自动完成对应今日任务（仅首次发放经验） */
  function applyRouteForTodayTasks(path: string) {
    const normalized = path.split('?')[0] || path
    for (const { prefix, taskId } of ROUTE_TO_TASK_ID) {
      if (normalized === prefix || normalized.startsWith(`${prefix}/`)) {
        completeTask(taskId)
        break
      }
    }
  }

  function completeTask(taskId: string) {
    const list = todayTasks.value
    const task = list.find((t) => t.id === taskId)
    if (!task || task.done) return

    task.done = true
    const add = Math.max(0, Math.floor(task.expReward))
    userExp.value += add
    persistUserExp(userExp.value)
    persistTaskDoneMap(list)
  }

  /**
   * 真实行为埋点 + 经验结算：总经验、对应雷达维度技能点、按日 activityLog
   */
  function recordActivity(activityType: ActivityKind, expReward: number) {
    const add = Math.max(0, Math.floor(expReward))
    userExp.value += add
    persistUserExp(userExp.value)

    const skillBump = Math.min(100, Math.max(1, Math.ceil(add / 2)))
    const prev = skillRadar.value
    const next: SkillRadarScores = { ...prev }
    next[activityType] = Math.min(100, prev[activityType] + skillBump)
    skillRadar.value = next
    persistSkillRadar(next)

    const entry: ActivityLogEntry = {
      date: localDateKey(new Date()),
      type: activityType,
      expReward: add,
      at: Date.now(),
    }
    const log = [...activityLog.value, entry]
    activityLog.value = log.slice(-MAX_BEHAVIOR_LOG)
    persistBehaviorActivityLog(activityLog.value)
  }

  function patchContext(payload: Partial<{
    currentModule: string
    lastError: string
    recentAction: string
    userLevel: string
    extraContext: Record<string, unknown>
  }>) {
    if (payload.currentModule !== undefined) currentModule.value = payload.currentModule
    if (payload.lastError !== undefined) lastError.value = payload.lastError
    if (payload.recentAction !== undefined) recentAction.value = payload.recentAction
    if (payload.userLevel !== undefined) userLevel.value = payload.userLevel
    if (payload.extraContext !== undefined) extraContext.value = payload.extraContext
  }

  function onTemperatureAdjusted() {
    temperatureTrialStreak.value += 1
  }

  function onProbabilityTreeSuccess() {
    probabilityTreeFailureStreak.value = 0
    // 成功后清空试错计数，避免频繁触发
    temperatureTrialStreak.value = 0
    autoTutorRequested.value = false
  }

  function setUserMode(mode: WorkbenchUserMode) {
    userMode.value = mode
    try {
      localStorage.setItem(USER_MODE_STORAGE_KEY, mode)
    } catch {
      /* ignore */
    }
  }

  function toggleUserMode() {
    setUserMode(userMode.value === 'beginner' ? 'expert' : 'beginner')
  }

  function setTheme(next: AppUiTheme) {
    theme.value = next
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next)
    } catch {
      /* ignore */
    }
    applyThemeToDocument(next)
  }

  function toggleTheme() {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  function seedTutorChatPrompt(prompt: string) {
    const p = prompt.trim()
    if (!p) return
    tutorChatSeedPrompt.value = p
    tutorChatNonce.value += 1
  }

  function setCurrentContext(page: string, data: unknown) {
    currentActiveContext.value = { page, data }
  }

  function clearCurrentContext() {
    currentActiveContext.value = null
  }

  function onProbabilityTreeFailure(errMsg: string) {
    probabilityTreeFailureStreak.value += 1

    // 连续 3 次：认为“调整温度 -> 结果不理想”
    if (temperatureTrialStreak.value >= 3 && probabilityTreeFailureStreak.value >= 3) {
      autoTutorRequested.value = true
      patchContext({
        currentModule: 'Prompt 炼丹炉',
        lastError: errMsg,
        recentAction: 'Temperature 连续试错触发导师',
        extraContext: {
          ...extraContext.value,
          temperatureTrialStreak: temperatureTrialStreak.value,
          probabilityTreeFailureStreak: probabilityTreeFailureStreak.value,
          trigger: 'autoTutor',
        },
      })
    }
  }

  return {
    currentModule,
    lastError,
    recentAction,
    userLevel,
    theme,
    userMode,
    extraContext,
    temperatureTrialStreak,
    probabilityTreeFailureStreak,
    autoTutorRequested,
    tutorChatSeedPrompt,
    tutorChatNonce,
    currentActiveContext,
    recentActivities,
    userExp,
    todayTasks,
    skillRadar,
    activityLog,
    studyRecords,
    ragPipelineParams,
    ragSimulatedChunks,
    ragVectorSpaceRevision,
    ragRetrievalDemo,
    applyRagChunkStrategy,
    setRagPipelineParams,
    triggerRagRetrievalSimulation,
    clearRagRetrievalDemo,
    recordWorkbenchVisit,
    recordActivity,
    transitionStudyContext,
    flushActiveStudyRecord,
    applyRouteForTodayTasks,
    completeTask,
    patchContext,
    setUserMode,
    toggleUserMode,
    setTheme,
    toggleTheme,
    seedTutorChatPrompt,
    setCurrentContext,
    clearCurrentContext,
    onTemperatureAdjusted,
    onProbabilityTreeSuccess,
    onProbabilityTreeFailure,
  }
})

