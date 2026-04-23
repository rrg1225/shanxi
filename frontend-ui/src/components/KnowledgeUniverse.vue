<template>
  <div class="knowledge-universe">
    <div ref="hostRef" class="graph-host">
      <div ref="threeMountRef" class="three-mount"></div>
    </div>

    <div class="universe-badge">
      <div class="title">Knowledge Universe</div>
      <div class="meta">节点 {{ graphData.nodes.length }} · 关系 {{ graphData.links.length }}</div>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :with-header="false"
      direction="rtl"
      size="360px"
      class="knowledge-drawer"
    >
      <div v-if="selectedNode" class="drawer-content">
        <div class="drawer-head-tags">
          <div class="drawer-tag">知识卡片</div>
          <el-tag :type="statusTagType(drawerNodeStatus)" effect="dark" size="small" class="drawer-status-tag">
            学习进度：{{ statusLabelText(drawerNodeStatus) }}
          </el-tag>
        </div>
        <h3>{{ selectedNode.name }}</h3>
        <p class="subtitle">{{ selectedNode.subtitle }}</p>
        <p class="description">{{ selectedNode.description }}</p>

        <div class="stats-grid">
          <div class="stat-item">
            <div class="label">难度等级</div>
            <div class="value">{{ selectedNode.level }}</div>
          </div>
          <div class="stat-item">
            <div class="label">推荐时长</div>
            <div class="value">{{ selectedNode.duration }}</div>
          </div>
          <div class="stat-item">
            <div class="label">重要程度</div>
            <div class="value">{{ selectedNode.importance }}/10</div>
          </div>
          <div class="stat-item">
            <div class="label">关联知识</div>
            <div class="value">{{ selectedNode.relatedTopics.join(' / ') }}</div>
          </div>
        </div>

        <el-button type="primary" class="summon-btn" @click="enterLearningLab">
          🚀 进入学习空间 (Learning Lab)
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'
import { KNOWLEDGE_GROUP_ANCHORS } from '@/data/knowledgeMapGraph'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'

type KnowledgeNodeStatus = 'locked' | 'learning' | 'mastered'

interface KnowledgeNode {
  id: string
  name: string
  subtitle: string
  description: string
  level: '入门' | '进阶' | '高阶'
  duration: string
  importance: number
  relatedTopics: string[]
  symbolSize: number
  value: number
  category: string
  status?: KnowledgeNodeStatus
  /** 星系簇 id，与 knowledgeMapGraph 中 group 一致时启用聚类力 */
  group?: string
  /** 1 领域枢纽 · 2 子方向 · 3 技术点 */
  knowledgeLevel?: 1 | 2 | 3
  /** 掌握度 0–1，影响节点发光 */
  mastery?: number
}

interface KnowledgeLink {
  source: string
  target: string
  value: number
  note: string
}

interface GraphMockData {
  nodes: KnowledgeNode[]
  links: KnowledgeLink[]
}

const props = withDefaults(
  defineProps<{
    mockData?: GraphMockData
    learningLabFrom?: 'knowledge-map' | 'knowledge-starry' | 'knowledge-universe' | 'rag-visual'
    /** drawer：点击打开内置抽屉；panel：仅派发 node-click，由页面侧栏承接，并启用运镜 / 选中淡化 */
    interactionMode?: 'drawer' | 'panel'
    /** panel 模式下由父组件同步：为 null 时清除选中与高亮 */
    syncedSelectionId?: string | null
  }>(),
  {
    mockData: undefined,
    learningLabFrom: 'rag-visual',
    interactionMode: 'drawer',
    syncedSelectionId: undefined,
  },
)

const emit = defineEmits<{
  (e: 'prepare-learning-lab', node: KnowledgeNode): void
  (e: 'node-click', payload: { node: KnowledgeNode }): void
}>()

const router = useRouter()
const learningStore = useGlobalLearningContextStore()

const hostRef = ref<HTMLDivElement | null>(null)
const threeMountRef = ref<HTMLDivElement | null>(null)
const drawerVisible = ref(false)
const selectedNode = ref<KnowledgeNode | null>(null)

function hashPickStatus(id: string): KnowledgeNodeStatus {
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

/** 确定性 [0,1)，用于初始位置抖动，避免同 id 漂移 */
function hashFloat(id: string, salt: string): number {
  let h = 2166136261
  const s = `${id}\0${salt}`
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0) / 4294967296
}

function normalizeStatus(node: KnowledgeNode): KnowledgeNodeStatus {
  return node.status ?? hashPickStatus(node.id)
}

const DEFAULT_GRAPH_MOCK: GraphMockData = {
  nodes: [
    {
      id: 'math-modeling',
      name: '数学建模指南',
      subtitle: '从问题抽象到模型验证的全流程',
      description: '面向初学者的建模路线图，覆盖常见模型、数据清洗和结果解释，适合作为跨学科项目起点。',
      level: '入门',
      duration: '6-8 小时',
      importance: 10,
      relatedTopics: ['统计基础', '优化方法', '可视化表达'],
      symbolSize: 56,
      value: 10,
      category: 'method',
    },
    {
      id: 'spring-boot',
      name: 'Spring Boot 实战',
      subtitle: '构建企业级后端服务的实战指南',
      description: '围绕 API 设计、数据库集成与部署实践，帮助你快速搭建可上线的 Java 后端系统。',
      level: '进阶',
      duration: '10-12 小时',
      importance: 9,
      relatedTopics: ['RESTful API', 'MySQL', '微服务'],
      symbolSize: 52,
      value: 9,
      category: 'backend',
    },
    {
      id: 'english-sentence',
      name: '考研英语长难句',
      subtitle: '复杂句结构拆解与阅读提速',
      description: '针对长难句进行语法分层和语义聚焦训练，提升阅读理解速度与准确率。',
      level: '入门',
      duration: '4-6 小时',
      importance: 8,
      relatedTopics: ['语法树', '同位语', '定语从句'],
      symbolSize: 48,
      value: 8,
      category: 'language',
    },
    {
      id: 'rag-thinking',
      name: 'RAG 检索思维',
      subtitle: '让 AI 回答更可控、更有依据',
      description: '掌握切分、嵌入、召回、重排等核心概念，建立知识外脑系统化思维。',
      level: '进阶',
      duration: '5-7 小时',
      importance: 9,
      relatedTopics: ['向量数据库', '相似度检索', '提示工程'],
      symbolSize: 54,
      value: 9,
      category: 'ai',
    },
    {
      id: 'agent-collaboration',
      name: '多智能体协作',
      subtitle: '把大任务拆给多个 AI 角色执行',
      description: '学习如何让分析者、审阅者、执行者协同工作，提升复杂问题解决效率。',
      level: '高阶',
      duration: '7-9 小时',
      importance: 8,
      relatedTopics: ['任务编排', '角色分工', '评审闭环'],
      symbolSize: 46,
      value: 8,
      category: 'ai',
    },
  ],
  links: [
    { source: 'math-modeling', target: 'rag-thinking', value: 0.88, note: '数据建模 -> 检索策略' },
    { source: 'spring-boot', target: 'rag-thinking', value: 0.91, note: '后端服务支撑知识检索' },
    { source: 'english-sentence', target: 'rag-thinking', value: 0.73, note: '文本解析能力迁移' },
    { source: 'rag-thinking', target: 'agent-collaboration', value: 0.84, note: '检索增强多 Agent 输出质量' },
    { source: 'spring-boot', target: 'agent-collaboration', value: 0.67, note: '工程化落地 Agent 流程' },
  ],
}

const drawerNodeStatus = computed<KnowledgeNodeStatus>(() =>
  selectedNode.value ? normalizeStatus(selectedNode.value) : 'learning',
)

const graphData = computed<GraphMockData>(() => {
  const base = props.mockData ?? DEFAULT_GRAPH_MOCK
  return {
    nodes: base.nodes.map((n) => ({
      ...n,
      status: normalizeStatus(n),
    })),
    links: base.links,
  }
})

function statusLabelText(s: KnowledgeNodeStatus) {
  const map: Record<KnowledgeNodeStatus, string> = {
    locked: '未解锁',
    learning: '学习中',
    mastered: '已掌握',
  }
  return map[s]
}

function statusTagType(s: KnowledgeNodeStatus): 'info' | 'primary' | 'success' {
  if (s === 'locked') return 'info'
  if (s === 'learning') return 'primary'
  return 'success'
}

function getCategoryColor(category: string): THREE.Color {
  const colorMap: Record<string, string> = {
    method: '#7dd3fc',
    backend: '#86efac',
    language: '#f9a8d4',
    ai: '#c4b5fd',
    基础: '#7dd3fc',
    算法基础: '#86efac',
    'AI 基础': '#c4b5fd',
    RAG: '#a5b4fc',
    工程: '#5eead4',
    Agent: '#f9a8d4',
    综合: '#fde047',
    人工智能与大模型: '#c4b5fd',
    'Java 后端架构': '#86efac',
    大前端工程: '#7dd3fc',
    计算机基础理论: '#fcd34d',
    'ai-llm': '#c4b5fd',
    'java-backend': '#86efac',
    frontend: '#7dd3fc',
    'cs-theory': '#fcd34d',
  }
  return new THREE.Color(colorMap[category] ?? '#93c5fd')
}

function colorForGraphNode(node: KnowledgeNode): THREE.Color {
  if (node.group && node.group in KNOWLEDGE_GROUP_ANCHORS) {
    return getCategoryColor(node.group)
  }
  return getCategoryColor(node.category)
}

// —— Three.js 场景（视觉层，数据接口与父组件保持不变）——

interface SimNode extends KnowledgeNode {
  x: number
  y: number
  z: number
  vx: number
  vy: number
  vz: number
}

let renderer: THREE.WebGLRenderer | null = null
let labelRenderer: CSS2DRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let starField: THREE.Points | null = null
let graphRoot: THREE.Group | null = null
let rafId = 0
let clock: THREE.Clock | null = null

const nodeMeshes = new Map<
  string,
  {
    group: THREE.Group
    core: THREE.Mesh
    glow: THREE.Mesh
    data: KnowledgeNode
    baseR: number
    isCore: boolean
  }
>()
const linkObjects: Array<{
  curve: THREE.LineCurve3
  tube: THREE.Mesh
  mat: THREE.MeshBasicMaterial
  flow: THREE.Mesh
  flowMat: THREE.MeshBasicMaterial
  a: THREE.Vector3
  b: THREE.Vector3
  colorA: THREE.Color
  colorB: THREE.Color
  strength: number
  sourceId: string
  targetId: string
}> = []

let hoverLabel: CSS2DObject | null = null
let hoverLabelDiv: HTMLDivElement | null = null
let hoveredId: string | null = null

/** panel 模式：当前聚焦节点与一跳邻居（边高亮） */
let selectionFocusId: string | null = null
const selectionRelatedIds = new Set<string>()

let cameraFly:
  | {
      startCam: THREE.Vector3
      endCam: THREE.Vector3
      startTarget: THREE.Vector3
      endTarget: THREE.Vector3
      t0: number
      duration: number
    }
  | null = null

function computeRelatedIds(nodeId: string): Set<string> {
  const s = new Set<string>([nodeId])
  for (const l of graphData.value.links) {
    if (l.source === nodeId) s.add(l.target)
    if (l.target === nodeId) s.add(l.source)
  }
  return s
}

function applyGraphSelection(nodeId: string) {
  selectionFocusId = nodeId
  selectionRelatedIds.clear()
  for (const id of computeRelatedIds(nodeId)) selectionRelatedIds.add(id)
}

function clearGraphSelection() {
  selectionFocusId = null
  selectionRelatedIds.clear()
  cameraFly = null
}

function startCameraFlyToNode(nodeId: string) {
  if (!camera || !controls) return
  const entry = nodeMeshes.get(nodeId)
  if (!entry) return
  const focus = new THREE.Vector3()
  entry.group.getWorldPosition(focus)
  const startCam = camera.position.clone()
  const startTarget = controls.target.clone()
  let dir = startCam.clone().sub(focus)
  if (dir.lengthSq() < 9) dir.set(0.48, 0.55, 1)
  dir.normalize()
  const dist = 76
  const endCam = focus.clone().add(dir.multiplyScalar(dist))
  const endTarget = focus.clone()
  cameraFly = {
    startCam,
    endCam,
    startTarget,
    endTarget,
    t0: typeof performance !== 'undefined' ? performance.now() : Date.now(),
    duration: 740,
  }
}

watch(
  () => props.syncedSelectionId,
  (id) => {
    if (props.interactionMode !== 'panel') return
    if (id == null || id === '') clearGraphSelection()
  },
)

function computeCoreIds(nodes: KnowledgeNode[]): Set<string> {
  const k = Math.max(2, Math.ceil(nodes.length * 0.28))
  const out = new Set<string>()
  const tier1 = nodes.filter((n) => n.knowledgeLevel === 1)
  const tier2 = nodes.filter((n) => n.knowledgeLevel === 2)
  const rest = [...nodes]
    .filter((n) => n.knowledgeLevel !== 1 && n.knowledgeLevel !== 2)
    .sort((a, b) => b.importance + b.value - (a.importance + a.value))
  for (const n of tier1) out.add(n.id)
  for (const n of tier2) {
    if (out.size >= k) break
    out.add(n.id)
  }
  for (const n of rest) {
    if (out.size >= k) break
    out.add(n.id)
  }
  return out
}

function groupAnchorFor(node: KnowledgeNode): { x: number; y: number; z: number } | null {
  const g = node.group
  if (!g || !(g in KNOWLEDGE_GROUP_ANCHORS)) return null
  return KNOWLEDGE_GROUP_ANCHORS[g as keyof typeof KNOWLEDGE_GROUP_ANCHORS]
}

function layoutGraph3D(nodes: KnowledgeNode[], links: KnowledgeLink[], iters = 240): SimNode[] {
  const n = nodes.length
  const byId = new Map<string, SimNode>()
  const sim: SimNode[] = nodes.map((d, i) => {
    const anchor = groupAnchorFor(d)
    let x: number
    let y: number
    let z: number
    if (anchor) {
      const spread = 40 + (d.knowledgeLevel === 3 ? 16 : d.knowledgeLevel === 2 ? 8 : 0)
      x = anchor.x + (hashFloat(d.id, 'jx') - 0.5) * spread * 2.15
      y = anchor.y + (hashFloat(d.id, 'jy') - 0.5) * spread * 1.35
      z = anchor.z + (hashFloat(d.id, 'jz') - 0.5) * spread * 2.15
    } else {
      const t = (2 * Math.PI * i) / Math.max(n, 1)
      const r = 95 + (i % 5) * 4
      x = r * Math.cos(t) * 0.92
      y = ((i * 37) % 100) - 50
      z = r * Math.sin(t) * 0.75
    }
    const sn: SimNode = {
      ...d,
      x,
      y,
      z,
      vx: 0,
      vy: 0,
      vz: 0,
    }
    byId.set(d.id, sn)
    return sn
  })

  for (let step = 0; step < iters; step++) {
    const alpha = 1 - step / iters
    for (let i = 0; i < sim.length; i++) {
      for (let j = i + 1; j < sim.length; j++) {
        let dx = sim[i].x - sim[j].x
        let dy = sim[i].y - sim[j].y
        let dz = sim[i].z - sim[j].z
        let distSq = dx * dx + dy * dy + dz * dz
        if (distSq < 1) distSq = 1
        const gi = sim[i].group
        const gj = sim[j].group
        const sameG = Boolean(gi && gj && gi === gj)
        const crossG = Boolean(gi && gj && gi !== gj)
        let repelMul = 1
        /* 同簇内也保持较强斥力，避免「一团糊」；跨簇略加强，四簇更分开 */
        if (sameG) repelMul = 0.94
        if (crossG) repelMul = 1.45
        const f = (5600 / (distSq * Math.sqrt(distSq))) * repelMul
        dx *= f
        dy *= f
        dz *= f
        const rp = alpha * 0.018
        sim[i].vx += dx * rp
        sim[i].vy += dy * rp
        sim[i].vz += dz * rp
        sim[j].vx -= dx * rp
        sim[j].vy -= dy * rp
        sim[j].vz -= dz * rp
      }
    }
    for (const l of links) {
      const a = byId.get(l.source)
      const b = byId.get(l.target)
      if (!a || !b) continue
      let dx = b.x - a.x
      let dy = b.y - a.y
      let dz = b.z - a.z
      const dist = Math.sqrt(dx * dx + dy * dy + dz * dz) || 1
      const target = 58 + (1 - l.value) * 68
      const f = ((dist - target) / dist) * 0.11 * alpha
      a.vx += dx * f
      a.vy += dy * f
      a.vz += dz * f
      b.vx -= dx * f
      b.vy -= dy * f
      b.vz -= dz * f
    }
    for (const s of sim) {
      const ac = groupAnchorFor(s)
      if (ac) {
        const tier = s.knowledgeLevel ?? 3
        const pull = (tier === 1 ? 0.0064 : tier === 2 ? 0.0049 : 0.0036) * alpha
        s.vx += (ac.x - s.x) * pull
        s.vy += (ac.y - s.y) * pull
        s.vz += (ac.z - s.z) * pull
      }
      s.vx *= 0.78
      s.vy *= 0.78
      s.vz *= 0.78
      s.x += s.vx
      s.y += s.vy
      s.z += s.vz
      s.x -= s.x * 0.0016 * alpha
      s.y -= s.y * 0.0016 * alpha
      s.z -= s.z * 0.0016 * alpha
    }
  }
  return sim
}

function disposeGraphScene() {
  if (!graphRoot) return
  clearGraphSelection()
  if (hoverLabel?.parent) {
    hoverLabel.parent.remove(hoverLabel)
  }
  hoveredId = null
  for (const [, entry] of nodeMeshes) {
    graphRoot.remove(entry.group)
    entry.group.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry?.dispose()
        if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose())
        else obj.material?.dispose()
      }
    })
  }
  nodeMeshes.clear()
  for (const lo of linkObjects) {
    lo.tube.geometry.dispose()
    lo.mat.dispose()
    lo.flow.geometry.dispose()
    lo.flowMat.dispose()
    graphRoot.remove(lo.tube)
    graphRoot.remove(lo.flow)
  }
  linkObjects.length = 0
}

function buildStarField(count: number): THREE.Points {
  const geo = new THREE.BufferGeometry()
  const pos = new Float32Array(count * 3)
  const sizes = new Float32Array(count)
  for (let i = 0; i < count; i++) {
    const u = Math.random()
    const v = Math.random()
    const theta = 2 * Math.PI * u
    const phi = Math.acos(2 * v - 1)
    const r = 420 + Math.random() * 900
    const sinP = Math.sin(phi)
    pos[i * 3] = r * sinP * Math.cos(theta)
    pos[i * 3 + 1] = r * sinP * Math.sin(theta)
    pos[i * 3 + 2] = r * Math.cos(phi)
    sizes[i] = 0.6 + Math.random() * 1.8
  }
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3))
  geo.setAttribute('size', new THREE.BufferAttribute(sizes, 1))
  const mat = new THREE.ShaderMaterial({
    uniforms: {
      uTime: { value: 0 },
      uPixelRatio: { value: typeof window !== 'undefined' ? window.devicePixelRatio : 1 },
    },
    vertexShader: `
      attribute float size;
      uniform float uPixelRatio;
      varying float vAlpha;
      void main() {
        vAlpha = 0.35 + 0.65 * fract(sin(dot(position, vec3(12.9898,78.233,45.164))) * 43758.5453);
        vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = size * uPixelRatio * (320.0 / -mvPosition.z);
        gl_Position = projectionMatrix * mvPosition;
      }
    `,
    fragmentShader: `
      varying float vAlpha;
      void main() {
        vec2 c = gl_PointCoord - vec2(0.5);
        float d = length(c);
        if (d > 0.5) discard;
        float soft = smoothstep(0.5, 0.15, d);
        vec3 col = mix(vec3(0.55, 0.75, 1.0), vec3(0.9, 0.95, 1.0), vAlpha);
        gl_FragColor = vec4(col, soft * 0.55 * vAlpha);
      }
    `,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  })
  return new THREE.Points(geo, mat)
}

function makeNodeSphere(
  node: KnowledgeNode,
  status: KnowledgeNodeStatus,
  coreIds: Set<string>,
): { group: THREE.Group; core: THREE.Mesh; glow: THREE.Mesh; baseR: number; isCore: boolean } {
  const isCore = coreIds.has(node.id)
  const cat = colorForGraphNode(node)
  const mastery =
    typeof node.mastery === 'number' ? Math.min(1, Math.max(0, node.mastery)) : 0.5
  const baseR = 5 + (node.symbolSize / 64) * 7.5
  const rCore = isCore ? baseR * 1.42 : baseR
  const rGlow = rCore * 1.55

  let color = cat.clone()
  let emissiveIntensity = isCore ? 0.88 + mastery * 0.2 : 0.28 + mastery * 0.52
  let opacity = 1
  if (status === 'locked') {
    color = new THREE.Color('#475569')
    emissiveIntensity = 0.08
    opacity = 0.45
  } else if (status === 'mastered') {
    color = new THREE.Color('#fde047')
    emissiveIntensity = isCore ? 1.02 + mastery * 0.12 : 0.48 + mastery * 0.38
  }

  const coreGeo = new THREE.SphereGeometry(rCore, 40, 40)
  const coreMat = new THREE.MeshStandardMaterial({
    color,
    emissive: color,
    emissiveIntensity,
    metalness: 0.35,
    roughness: 0.28,
    transparent: opacity < 1,
    opacity,
  })
  const core = new THREE.Mesh(coreGeo, coreMat)

  const glowGeo = new THREE.SphereGeometry(rGlow, 28, 28)
  const glowStrength = status === 'locked' ? 0.06 : isCore ? 0.2 + mastery * 0.12 : 0.07 + mastery * 0.16
  const glowMat = new THREE.MeshBasicMaterial({
    color,
    transparent: true,
    opacity: glowStrength,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  })
  const glow = new THREE.Mesh(glowGeo, glowMat)

  const group = new THREE.Group()
  group.add(glow)
  group.add(core)
  group.userData.nodeId = node.id

  return { group, core, glow, baseR: rCore, isCore }
}

function buildKnowledgeGraph(data: GraphMockData) {
  if (!scene || !graphRoot) return

  disposeGraphScene()

  const coreIds = computeCoreIds(data.nodes)
  const sim = layoutGraph3D(data.nodes, data.links)
  const byId = new Map(sim.map((s) => [s.id, s]))

  for (const s of sim) {
    const st = s.status ?? normalizeStatus(s)
    const { group, core, glow, baseR, isCore } = makeNodeSphere(s, st, coreIds)
    group.position.set(s.x, s.y, s.z)
    graphRoot.add(group)
    nodeMeshes.set(s.id, { group, core, glow, data: s, baseR, isCore })
    const coreMat = core.material as THREE.MeshStandardMaterial
    const glowMat = glow.material as THREE.MeshBasicMaterial
    group.userData.visualBase = {
      coreOpacity: coreMat.opacity,
      emissiveIntensity: coreMat.emissiveIntensity,
      glowOpacity: glowMat.opacity,
    }
  }

  for (const link of data.links) {
    const sa = byId.get(link.source)
    const sb = byId.get(link.target)
    if (!sa || !sb) continue
    const a = new THREE.Vector3(sa.x, sa.y, sa.z)
    const b = new THREE.Vector3(sb.x, sb.y, sb.z)
    const curve = new THREE.LineCurve3(a, b)
    const tubularSegments = 24
    const tubeGeo = new THREE.TubeGeometry(curve, tubularSegments, 0.22 + link.value * 0.35, 8, false)
    const posAttr = tubeGeo.getAttribute('position') as THREE.BufferAttribute
    const colors = new Float32Array(posAttr.count * 3)
    const cA = colorForGraphNode(sa)
    const cB = colorForGraphNode(sb)
    for (let i = 0; i < posAttr.count; i++) {
      const t = i / Math.max(posAttr.count - 1, 1)
      const c = cA.clone().lerp(cB, t)
      colors[i * 3] = c.r
      colors[i * 3 + 1] = c.g
      colors[i * 3 + 2] = c.b
    }
    tubeGeo.setAttribute('color', new THREE.BufferAttribute(colors, 3))

    const mat = new THREE.MeshBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: 0.38 + link.value * 0.28,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
    const tube = new THREE.Mesh(tubeGeo, mat)
    graphRoot.add(tube)

    const flowGeo = new THREE.SphereGeometry(1.1 + link.value * 0.9, 14, 14)
    const flowMat = new THREE.MeshBasicMaterial({
      color: 0xffffff,
      transparent: true,
      opacity: 0.92,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
    const flow = new THREE.Mesh(flowGeo, flowMat)
    graphRoot.add(flow)

    linkObjects.push({
      curve,
      tube,
      mat,
      flow,
      flowMat,
      a: a.clone(),
      b: b.clone(),
      colorA: cA,
      colorB: cB,
      strength: link.value,
      sourceId: link.source,
      targetId: link.target,
    })
  }
}

function ensureHoverLabel(): CSS2DObject {
  if (hoverLabel) return hoverLabel
  hoverLabelDiv = document.createElement('div')
  hoverLabelDiv.className = 'ku-hover-label'
  hoverLabel = new CSS2DObject(hoverLabelDiv)
  return hoverLabel
}

function initThree() {
  const mount = threeMountRef.value
  const host = hostRef.value
  if (!mount || !host) return

  const w = host.clientWidth || 920
  const h = host.clientHeight || 640

  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x030711, 0.00115)

  camera = new THREE.PerspectiveCamera(52, w / h, 0.5, 5000)
  camera.position.set(0, 312, 198)
  camera.lookAt(0, 0, 0)

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(w, h)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  mount.appendChild(renderer.domElement)

  labelRenderer = new CSS2DRenderer()
  labelRenderer.setSize(w, h)
  labelRenderer.domElement.style.position = 'absolute'
  labelRenderer.domElement.style.inset = '0'
  labelRenderer.domElement.style.pointerEvents = 'none'
  mount.appendChild(labelRenderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.minDistance = 80
  controls.maxDistance = 780
  controls.target.set(0, 0, 0)
  controls.update()

  const amb = new THREE.AmbientLight(0x6a7a9a, 0.35)
  scene.add(amb)
  const dir = new THREE.DirectionalLight(0xc8d8ff, 0.85)
  dir.position.set(120, 220, 80)
  scene.add(dir)
  const pt = new THREE.PointLight(0x5eead4, 1.1, 1200, 1.2)
  pt.position.set(-140, 80, -60)
  scene.add(pt)

  starField = buildStarField(5200)
  scene.add(starField)

  graphRoot = new THREE.Group()
  scene.add(graphRoot)

  clock = new THREE.Clock()

  buildKnowledgeGraph(graphData.value)

  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('click', onPointerClick)
}

function onPointerMove(ev: PointerEvent) {
  if (!camera || !graphRoot || !hostRef.value) return
  const rect = renderer!.domElement.getBoundingClientRect()
  const x = ((ev.clientX - rect.left) / rect.width) * 2 - 1
  const y = -((ev.clientY - rect.top) / rect.height) * 2 + 1
  const ray = new THREE.Raycaster()
  ray.setFromCamera(new THREE.Vector2(x, y), camera)
  const meshes: THREE.Object3D[] = []
  for (const [, v] of nodeMeshes) meshes.push(v.core)
  const hits = ray.intersectObjects(meshes, false)
  if (hits.length > 0) {
    const id = hits[0].object.parent?.userData.nodeId as string | undefined
    if (id) {
      hoveredId = id
      const entry = nodeMeshes.get(id)
      if (entry) {
        const lbl = ensureHoverLabel()
        if (lbl.parent && lbl.parent !== entry.group) {
          lbl.parent.remove(lbl)
        }
        hoverLabelDiv!.textContent = entry.data.name
        entry.group.add(lbl)
        lbl.position.set(0, entry.baseR * 2.1, 0)
      }
      return
    }
  }
  hoveredId = null
  if (hoverLabel && hoverLabel.parent) {
    hoverLabel.parent.remove(hoverLabel)
  }
}

function onPointerClick(ev: MouseEvent) {
  if (!camera || !graphRoot) return
  const rect = renderer!.domElement.getBoundingClientRect()
  const x = ((ev.clientX - rect.left) / rect.width) * 2 - 1
  const y = -((ev.clientY - rect.top) / rect.height) * 2 + 1
  const ray = new THREE.Raycaster()
  ray.setFromCamera(new THREE.Vector2(x, y), camera)
  const meshes: THREE.Object3D[] = []
  for (const [, v] of nodeMeshes) meshes.push(v.core)
  const hits = ray.intersectObjects(meshes, false)
  if (hits.length === 0) return
  const id = hits[0].object.parent?.userData.nodeId as string | undefined
  if (!id) return
  const hit = graphData.value.nodes.find((item) => item.id === id)
  if (hit) emit('node-click', { node: hit })
  if (props.interactionMode === 'panel') {
    applyGraphSelection(id)
    startCameraFlyToNode(id)
    return
  }
  openNodeDrawer(id)
}

function animate() {
  rafId = requestAnimationFrame(animate)
  const t = clock?.getElapsedTime() ?? 0

  if (cameraFly && camera && controls) {
    const now = typeof performance !== 'undefined' ? performance.now() : Date.now()
    const elapsed = now - cameraFly.t0
    const u = Math.min(1, elapsed / cameraFly.duration)
    const e = 1 - (1 - u) ** 3
    camera.position.lerpVectors(cameraFly.startCam, cameraFly.endCam, e)
    controls.target.lerpVectors(cameraFly.startTarget, cameraFly.endTarget, e)
    if (u >= 1) cameraFly = null
  }

  if (starField) {
    starField.rotation.y += 0.00022
    starField.rotation.x += 0.00006
    const mat = starField.material as THREE.ShaderMaterial
    if (mat.uniforms?.uTime) mat.uniforms.uTime.value = t
  }

  const flowSpeed = 0.38
  const panelSel = props.interactionMode === 'panel' && selectionFocusId
  for (let i = 0; i < linkObjects.length; i++) {
    const lo = linkObjects[i]
    const u = (t * flowSpeed * (0.65 + lo.strength * 0.5) + i * 0.17) % 1
    const p = lo.curve.getPointAt(u)
    lo.flow.position.copy(p)
    const pulse = 0.75 + 0.25 * Math.sin(t * 3.2 + i)
    const edgeLit =
      !panelSel ||
      selectionRelatedIds.has(lo.sourceId) ||
      selectionRelatedIds.has(lo.targetId)
    const dimE = edgeLit ? 1 : 0.14
    lo.flowMat.opacity = (0.55 + lo.strength * 0.35 * pulse) * dimE
    lo.flow.scale.setScalar(0.85 + lo.strength * 0.45)
    const c = lo.colorA.clone().lerp(lo.colorB, u)
    lo.flowMat.color.copy(c)
    const baseOp = 0.28 + lo.strength * 0.26 + 0.06 * Math.sin(t * 2 + i * 0.5)
    lo.mat.opacity = baseOp * dimE
  }

  for (const [id, entry] of nodeMeshes) {
    const ud = entry.group.userData.visualBase as
      | { coreOpacity: number; emissiveIntensity: number; glowOpacity: number }
      | undefined
    const base = entry.isCore ? 1.02 : 1
    const isSel = panelSel && id === selectionFocusId
    const isRel = panelSel && selectionRelatedIds.has(id)
    const isDim = Boolean(panelSel && !isSel && !isRel)
    const isHover = id === hoveredId

    let breathe = 1
    if (isSel) breathe = 1 + 0.1 * Math.sin(t * 3.6)
    else if (isHover) breathe = 1 + 0.14 * Math.sin(t * 4.2)

    const selScale = isSel ? 1.14 : 1
    entry.group.scale.setScalar(base * breathe * selScale)

    if (ud) {
      const coreMat = entry.core.material as THREE.MeshStandardMaterial
      const glowMat = entry.glow.material as THREE.MeshBasicMaterial
      if (isDim) {
        coreMat.opacity = ud.coreOpacity * 0.32
        coreMat.emissiveIntensity = ud.emissiveIntensity * 0.22
        glowMat.opacity = ud.glowOpacity * 0.18
      } else if (isSel) {
        coreMat.opacity = Math.min(1, ud.coreOpacity * 1.05)
        coreMat.emissiveIntensity = ud.emissiveIntensity * 1.45
        glowMat.opacity = Math.min(0.55, ud.glowOpacity * 2.1)
      } else {
        coreMat.opacity = ud.coreOpacity
        const hovE = isHover ? 1.14 : 1
        coreMat.emissiveIntensity = ud.emissiveIntensity * hovE
        const glowMul = isHover ? 1.45 : isRel ? 1.18 : 1
        glowMat.opacity = Math.min(0.48, ud.glowOpacity * glowMul)
      }
    } else if (id === hoveredId) {
      const g = entry.glow.material as THREE.MeshBasicMaterial
      g.opacity = entry.isCore ? 0.38 : 0.22
    } else {
      const g = entry.glow.material as THREE.MeshBasicMaterial
      g.opacity = entry.isCore ? 0.22 : 0.12
    }
  }

  controls?.update()
  if (scene && camera && renderer) renderer.render(scene, camera)
  if (scene && camera && labelRenderer) labelRenderer.render(scene, camera)
}

function resizeThree() {
  const host = hostRef.value
  if (!host || !camera || !renderer || !labelRenderer) return
  const w = host.clientWidth
  const h = host.clientHeight
  if (w < 2 || h < 2) return
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
  labelRenderer.setSize(w, h)
}

let resizeRaf = 0
function scheduleResize() {
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  resizeRaf = requestAnimationFrame(() => {
    resizeRaf = 0
    resizeThree()
  })
}

let chartResizeObserver: ResizeObserver | null = null

function openNodeDrawer(nodeId: string) {
  const hit = graphData.value.nodes.find((item) => item.id === nodeId)
  if (!hit) return
  const st = hit.status ?? normalizeStatus(hit)
  if (st === 'locked') {
    ElMessage.warning('请先完成前置知识的学习！')
    return
  }
  selectedNode.value = hit
  drawerVisible.value = true
}

function enterLearningLab() {
  if (!selectedNode.value) return
  const n = selectedNode.value
  if (
    props.learningLabFrom === 'knowledge-map' ||
    props.learningLabFrom === 'knowledge-starry' ||
    props.learningLabFrom === 'knowledge-universe'
  ) {
    const moduleLabel =
      props.learningLabFrom === 'knowledge-map'
        ? '资源广场 · 知识图谱'
        : props.learningLabFrom === 'knowledge-starry'
          ? '知识星空 · 学习路径'
          : '知识星空 · 沉浸探索'
    learningStore.patchContext({
      currentModule: moduleLabel,
      recentAction: `进入学习空间：${n.name}`,
      lastError: '',
      extraContext: {
        source: 'KnowledgeUniverse',
        knowledgeNodeId: n.id,
        knowledgeNodeName: n.name,
        subtitle: n.subtitle,
        description: n.description,
        relatedTopics: n.relatedTopics,
      },
    })
  } else {
    emit('prepare-learning-lab', n)
  }
  drawerVisible.value = false
  router.push({
    name: 'LearningLab',
    params: { nodeId: n.id },
    query: { title: n.name, from: props.learningLabFrom },
  })
}

function teardownThree() {
  cancelAnimationFrame(rafId)
  if (renderer?.domElement) {
    renderer.domElement.removeEventListener('pointermove', onPointerMove)
    renderer.domElement.removeEventListener('click', onPointerClick)
  }
  disposeGraphScene()
  if (starField) {
    starField.geometry.dispose()
    ;(starField.material as THREE.Material).dispose()
    scene?.remove(starField)
    starField = null
  }
  graphRoot = null
  nodeMeshes.clear()
  linkObjects.length = 0
  hoverLabel = null
  hoverLabelDiv = null
  controls?.dispose()
  controls = null
  renderer?.dispose()
  if (renderer?.domElement?.parentElement) renderer.domElement.parentElement.removeChild(renderer.domElement)
  if (labelRenderer?.domElement?.parentElement) {
    labelRenderer.domElement.parentElement.removeChild(labelRenderer.domElement)
  }
  labelRenderer = null
  renderer = null
  camera = null
  scene = null
  clock = null
  chartResizeObserver?.disconnect()
  chartResizeObserver = null
}

watch(
  () => props.mockData,
  () => {
    nextTick(() => {
      if (graphRoot) buildKnowledgeGraph(graphData.value)
    })
  },
  { deep: true },
)

onMounted(() => {
  nextTick(() => {
    nextTick(() => {
      initThree()
      scheduleResize()
      animate()
      if (hostRef.value && typeof ResizeObserver !== 'undefined') {
        chartResizeObserver = new ResizeObserver(() => scheduleResize())
        chartResizeObserver.observe(hostRef.value)
      }
      window.addEventListener('resize', scheduleResize)
    })
  })
})

onActivated(() => {
  nextTick(() => scheduleResize())
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', scheduleResize)
  teardownThree()
})
</script>

<style scoped>
.knowledge-universe {
  position: relative;
  width: 100%;
  height: 760px;
  border: 1px solid #1e293b;
  border-radius: 20px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 10%, rgb(30 64 175 / 24%), transparent 35%),
    radial-gradient(circle at 80% 90%, rgb(15 118 110 / 18%), transparent 42%),
    radial-gradient(circle at center, #0b1229 0%, #050914 62%, #02050d 100%);
  box-shadow:
    inset 0 0 90px rgb(56 189 248 / 8%),
    0 16px 40px rgb(2 6 23 / 46%);
}

.graph-host {
  position: relative;
  width: 100%;
  height: 100%;
}

.three-mount {
  position: absolute;
  inset: 0;
}

.three-mount :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
}

.three-mount :deep(.ku-hover-label) {
  padding: 4px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #e0f2fe;
  background: rgba(2, 6, 23, 0.82);
  border: 1px solid rgba(94, 234, 212, 0.45);
  box-shadow: 0 0 18px rgba(56, 189, 248, 0.35);
  white-space: nowrap;
  pointer-events: none;
}

.universe-badge {
  position: absolute;
  top: 16px;
  left: 16px;
  padding: 10px 14px;
  border: 1px solid rgb(148 163 184 / 25%);
  border-radius: 12px;
  background: rgb(2 6 23 / 58%);
  color: #dbeafe;
  backdrop-filter: blur(8px);
  z-index: 2;
  pointer-events: none;
}

.universe-badge .title {
  font-weight: 700;
  font-size: 14px;
}

.universe-badge .meta {
  margin-top: 4px;
  color: #bfdbfe;
  font-size: 12px;
}

:deep(.knowledge-drawer .el-drawer) {
  background: rgb(15 23 42 / 78%);
  border-left: 1px solid rgb(125 211 252 / 20%);
  box-shadow: -16px 0 36px rgb(2 6 23 / 45%);
  backdrop-filter: blur(14px);
}

.drawer-content {
  color: #e2e8f0;
}

.drawer-head-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.drawer-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgb(59 130 246 / 20%);
  color: #bfdbfe;
  font-size: 12px;
  border: 1px solid rgb(125 211 252 / 35%);
}

.drawer-status-tag {
  font-weight: 700;
}

.drawer-content h3 {
  margin: 12px 0 4px;
  font-size: 24px;
  color: #f8fafc;
}

.subtitle {
  margin: 0;
  color: #93c5fd;
}

.description {
  margin-top: 12px;
  line-height: 1.75;
  color: #e2e8f0;
}

.stats-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.stat-item {
  border: 1px solid rgb(148 163 184 / 20%);
  border-radius: 12px;
  padding: 10px;
  background: rgb(30 41 59 / 42%);
}

.stat-item .label {
  font-size: 12px;
  color: #94a3b8;
}

.stat-item .value {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 700;
  color: #e0f2fe;
}

.summon-btn {
  margin-top: 18px;
  width: 100%;
  height: 42px;
  font-weight: 700;
}
</style>
