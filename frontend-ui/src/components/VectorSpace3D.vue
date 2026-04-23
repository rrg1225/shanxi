<template>
  <div class="vector-space-3d course-map">
    <div ref="containerRef" class="canvas-container"></div>

    <div class="overlay-panel overlay-panel--stats">
      <div class="panel-title">课程地图 · 3D 向量空间</div>
      <div class="panel-row">节点 {{ normalizedChunks.length }}</div>
      <div class="panel-row">图谱边 {{ apiEdges.length }}（API）</div>
      <div class="panel-row">检索：{{ activeQuery || '—' }}</div>
      <div v-if="edgesLoading" class="panel-row panel-row--muted">边数据加载中…</div>
      <div v-else-if="edgesError" class="panel-row panel-row--warn">{{ edgesError }}</div>
      <div
        v-else-if="edgesApiUnavailableHint"
        class="panel-row panel-row--muted"
      >
        {{ edgesApiUnavailableHint }}
      </div>
    </div>

    <div class="overlay-panel overlay-panel--controls">
      <div class="controls-title">图谱筛选</div>
      <div class="slider-row">
        <label class="slider-label">minScore {{ minScore.toFixed(2) }}</label>
        <el-slider
          v-model="minScore"
          :min="0.5"
          :max="0.99"
          :step="0.01"
          :show-tooltip="true"
          @input="scheduleFetchEdges"
        />
      </div>
      <div class="slider-row">
        <label class="slider-label">每节点最多边数 {{ maxEdgesPerNode }}</label>
        <el-slider
          v-model="maxEdgesPerNode"
          :min="1"
          :max="12"
          :step="1"
          :show-tooltip="true"
          @input="scheduleFetchEdges"
        />
      </div>
    </div>

    <el-drawer
      v-model="drawerOpen"
      direction="rtl"
      size="400px"
      :with-header="false"
      class="course-drawer"
      modal-class="course-drawer-modal"
    >
      <div v-if="selectedChunk" class="drawer-inner">
        <div class="drawer-kicker">知识节点</div>
        <h2 class="drawer-title">{{ nodeTitle(selectedChunk) }}</h2>
        <p class="drawer-intro">{{ nodeIntro(selectedChunk) }}</p>
        <div class="drawer-meta">
          <span class="pill" :class="`pill--${selectedChunk.status}`">{{ statusLabel(selectedChunk.status) }}</span>
          <span v-if="selectedChunk.status === 'learning'" class="pill pill--progress">
            进度 {{ Math.round((selectedChunk.learningProgress ?? 0.5) * 100) }}%
          </span>
        </div>
        <div class="drawer-actions">
          <el-button class="action-btn action-btn--emphasis" type="primary" @click="onEnterLearningLab">
            🚀 进入沉浸式学习空间
          </el-button>
          <el-button class="action-btn" type="primary" plain @click="onReadingPlan">
            📚 一键生成阅读计划
          </el-button>
          <el-button class="action-btn" type="primary" plain @click="onQuizMe">
            🤔 考考我这个知识点
          </el-button>
          <el-button class="action-btn" @click="onSearchDocs"> 🔍 搜索相关文档 </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as THREE from 'three'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

type VisualStep = 'text-split' | 'vector-db' | 'similarity-search'

export type CourseNodeStatus = 'locked' | 'learning' | 'mastered'

export interface ChunkPoint {
  id: string | number
  label?: string
  x: number
  y: number
  z: number
  score?: number
  cluster?: string | number
  color?: string
  /** 课程地图：节点状态 */
  status?: CourseNodeStatus
  /** learning 时进度环 0–1 */
  learningProgress?: number
  /** 抽屉简介（可选） */
  intro?: string
}

interface RetrievalHit {
  chunkId: string | number
  score?: number
}

export interface KnowledgeGraphEdgeDTO {
  sourceChunkId: number
  targetChunkId: number
  similarityScore?: number
}

const props = withDefaults(
  defineProps<{
    chunks: ChunkPoint[]
    retrievalHits?: RetrievalHit[]
    activeQuery?: string
    backgroundColor?: string
    linkDistance?: number
    autoRotate?: boolean
    visualStep?: VisualStep
    /** 知识图谱边 API，默认走 Vite 代理 */
    edgesApiBase?: string
  }>(),
  {
    retrievalHits: () => [],
    activeQuery: '',
    backgroundColor: '#020412',
    linkDistance: 20,
    autoRotate: true,
    visualStep: 'text-split',
    edgesApiBase: '/api/v1/knowledge-graph/edges',
  },
)

const emit = defineEmits<{
  (e: 'search-related-docs', chunk: ChunkPoint): void
}>()

const learningStore = useGlobalLearningContextStore()
const router = useRouter()

const containerRef = ref<HTMLDivElement | null>(null)

const scene = shallowRef<THREE.Scene | null>(null)
const camera = shallowRef<THREE.PerspectiveCamera | null>(null)
const renderer = shallowRef<THREE.WebGLRenderer | null>(null)
const orbitGroup = shallowRef<THREE.Group | null>(null)

const pointsLocked = shallowRef<THREE.Points | null>(null)
const pointsLearning = shallowRef<THREE.Points | null>(null)
const pointsMastered = shallowRef<THREE.Points | null>(null)
const glowMastered = shallowRef<THREE.Points | null>(null)

const apiLineSegments = shallowRef<THREE.LineSegments | null>(null)
const clusterLines = shallowRef<THREE.LineSegments | null>(null)
const querySphere = shallowRef<THREE.Mesh | null>(null)
const queryLinks = shallowRef<THREE.LineSegments | null>(null)

const learningRingMeshes: THREE.Mesh[] = []
const learningRingPositions: THREE.Vector3[] = []

let animationId = 0
let resizeObserver: ResizeObserver | null = null
let clock: THREE.Clock | null = null
let raycaster: THREE.Raycaster | null = null
const pointer = new THREE.Vector2()

let edgesFetchTimer: ReturnType<typeof setTimeout> | null = null

const minScore = ref(0.8)
const maxEdgesPerNode = ref(5)
const apiEdges = ref<KnowledgeGraphEdgeDTO[]>([])
const edgesLoading = ref(false)
const edgesError = ref('')
/** 边接口不可用但已静默降级（不显示红色 HTTP 报错） */
const edgesApiUnavailableHint = ref('')

const drawerOpen = ref(false)
const selectedChunk = ref<ChunkPoint | null>(null)

const retrievalHits = computed(() => props.retrievalHits)

const showClusterLinks = computed(() => props.visualStep === 'vector-db' || props.visualStep === 'similarity-search')
const showQueryCapture = computed(() => props.visualStep === 'similarity-search')

function inferStatus(chunk: ChunkPoint, index: number): CourseNodeStatus {
  if (chunk.status) return chunk.status
  const n = Number(chunk.id) + index * 13
  const r = Math.abs(n) % 10
  if (r < 3) return 'locked'
  if (r < 7) return 'learning'
  return 'mastered'
}

function inferLearningProgress(chunk: ChunkPoint, index: number): number {
  if (typeof chunk.learningProgress === 'number') {
    return Math.min(1, Math.max(0.05, chunk.learningProgress))
  }
  const base = 0.35 + ((Number(chunk.id) + index) % 5) * 0.12
  return Math.min(0.95, base)
}

const normalizedChunks = computed<ChunkPoint[]>(() =>
  props.chunks.map((c, i) => ({
    ...c,
    status: inferStatus(c, i),
    learningProgress: inferLearningProgress(c, i),
  })),
)

function nodeTitle(c: ChunkPoint) {
  return c.label ? String(c.label).slice(0, 80) : `节点 ${c.id}`
}

function nodeIntro(c: ChunkPoint) {
  if (c.intro) return c.intro
  return `这里是「${nodeTitle(c)}」。完成相邻节点的学习可解锁更多关联；在专家模式下可查看向量与检索细节。`
}

function statusLabel(s: CourseNodeStatus | undefined) {
  const map: Record<CourseNodeStatus, string> = {
    locked: '未解锁',
    learning: '学习中',
    mastered: '已掌握',
  }
  return s ? map[s] : '未知'
}

function disposePoints(p: THREE.Points | null) {
  if (!p) return
  p.removeFromParent()
  p.geometry.dispose()
  ;(p.material as THREE.Material).dispose()
}

function clearLearningRings(parent: THREE.Group) {
  learningRingMeshes.forEach((m) => {
    parent.remove(m)
    m.geometry.dispose()
    ;(m.material as THREE.Material).dispose()
  })
  learningRingMeshes.length = 0
  learningRingPositions.length = 0
}

function clearLineSeg(obj: THREE.LineSegments | null) {
  if (!obj) return
  obj.removeFromParent()
  obj.geometry.dispose()
  ;(obj.material as THREE.Material).dispose()
}

function buildPointsSubsets(parent: THREE.Group) {
  disposePoints(pointsLocked.value)
  disposePoints(pointsLearning.value)
  disposePoints(pointsMastered.value)
  disposePoints(glowMastered.value)
  pointsLocked.value = null
  pointsLearning.value = null
  pointsMastered.value = null
  glowMastered.value = null

  clearLearningRings(parent)

  const chunks = normalizedChunks.value
  if (chunks.length === 0) return

  const locked: ChunkPoint[] = []
  const learning: ChunkPoint[] = []
  const mastered: ChunkPoint[] = []
  chunks.forEach((c) => {
    if (c.status === 'locked') locked.push(c)
    else if (c.status === 'learning') learning.push(c)
    else mastered.push(c)
  })

  const n = chunks.length
  /** 点数多时缩小粒子、略降不透明度，减轻加法混合的「糊成一团」 */
  const denseT = Math.min(1, Math.max(0, (n - 22) / 50))
  const sizeMul = 1 - denseT * 0.42
  const opacityMul = 1 - denseT * 0.28
  const useSoftBlend = denseT > 0.55

  const addPoints = (list: ChunkPoint[], colorHex: string, size: number, opacity: number) => {
    if (list.length === 0) return null
    const positions = new Float32Array(list.length * 3)
    const colors = new Float32Array(list.length * 3)
    const col = new THREE.Color(colorHex)
    list.forEach((c, i) => {
      positions[i * 3] = c.x
      positions[i * 3 + 1] = c.y
      positions[i * 3 + 2] = c.z
      colors[i * 3] = col.r
      colors[i * 3 + 1] = col.g
      colors[i * 3 + 2] = col.b
    })
    const geom = new THREE.BufferGeometry()
    geom.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geom.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    const mat = new THREE.PointsMaterial({
      size: size * sizeMul,
      vertexColors: true,
      transparent: true,
      opacity: opacity * opacityMul,
      depthWrite: useSoftBlend,
      blending: useSoftBlend ? THREE.NormalBlending : THREE.AdditiveBlending,
      sizeAttenuation: true,
    })
    const pts = new THREE.Points(geom, mat)
    parent.add(pts)
    return pts
  }

  pointsLocked.value = addPoints(locked, '#64748b', 1.9, 0.72)
  pointsLearning.value = addPoints(learning, '#38bdf8', 2.35, 0.88)
  pointsMastered.value = addPoints(mastered, '#fbbf24', 2.85, 0.92)

  if (mastered.length) {
    const positions = new Float32Array(mastered.length * 3)
    const colors = new Float32Array(mastered.length * 3)
    const col = new THREE.Color('#fde68a')
    mastered.forEach((c, i) => {
      positions[i * 3] = c.x
      positions[i * 3 + 1] = c.y
      positions[i * 3 + 2] = c.z
      colors[i * 3] = col.r
      colors[i * 3 + 1] = col.g
      colors[i * 3 + 2] = col.b
    })
    const geom = new THREE.BufferGeometry()
    geom.setAttribute('position', new THREE.BufferAttribute(positions, 3))
    geom.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    const glowOpacity = (0.2 - denseT * 0.08) * opacityMul
    const mat = new THREE.PointsMaterial({
      size: (5.2 - denseT * 1.8) * sizeMul,
      vertexColors: true,
      transparent: true,
      opacity: Math.max(0.06, glowOpacity),
      depthWrite: false,
      blending: useSoftBlend ? THREE.NormalBlending : THREE.AdditiveBlending,
    })
    glowMastered.value = new THREE.Points(geom, mat)
    parent.add(glowMastered.value)
  }

  const maxRings = n > 38 ? 8 : n > 28 ? 12 : 64
  let ringBudget = maxRings
  learning.forEach((c) => {
    if (ringBudget <= 0) return
    ringBudget -= 1
    const p = Math.min(0.98, Math.max(0.08, c.learningProgress ?? 0.5))
    const inner = 4.0
    const outer = 5.6
    const ringGeom = new THREE.RingGeometry(inner, outer, 48, 1, -Math.PI / 2, Math.PI * 2 * p)
    const ringMat = new THREE.MeshBasicMaterial({
      color: '#7dd3fc',
      side: THREE.DoubleSide,
      transparent: true,
      opacity: 0.92,
    })
    const mesh = new THREE.Mesh(ringGeom, ringMat)
    mesh.position.set(c.x, c.y, c.z)
    parent.add(mesh)
    learningRingMeshes.push(mesh)
    learningRingPositions.push(new THREE.Vector3(c.x, c.y, c.z))
  })
}

function buildApiEdges(parent: THREE.Group) {
  clearLineSeg(apiLineSegments.value)
  apiLineSegments.value = null

  const chunks = normalizedChunks.value
  if (chunks.length === 0 || apiEdges.value.length === 0) return

  const posById = new Map<string, { x: number; y: number; z: number }>()
  chunks.forEach((c) => posById.set(String(c.id), { x: c.x, y: c.y, z: c.z }))

  const seg: number[] = []
  const col: number[] = []

  for (const e of apiEdges.value) {
    const a = posById.get(String(e.sourceChunkId))
    const b = posById.get(String(e.targetChunkId))
    if (!a || !b) continue
    seg.push(a.x, a.y, a.z, b.x, b.y, b.z)
    const t = e.similarityScore ?? 0.75
    const ca = new THREE.Color('#38bdf8').multiplyScalar(0.35 + t * 0.45)
    const cb = new THREE.Color('#a78bfa').multiplyScalar(0.35 + t * 0.45)
    col.push(ca.r, ca.g, ca.b, cb.r, cb.g, cb.b)
  }

  if (seg.length === 0) return

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(seg, 3))
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(col, 3))
  const material = new THREE.LineBasicMaterial({
    vertexColors: true,
    transparent: true,
    opacity: 0.38,
    blending: THREE.AdditiveBlending,
  })
  apiLineSegments.value = new THREE.LineSegments(geometry, material)
  parent.add(apiLineSegments.value)
}

function clearClusterLinks() {
  clearLineSeg(clusterLines.value)
  clusterLines.value = null
}

function buildClusterLinks(parent: THREE.Group) {
  clearClusterLinks()

  const segments: number[] = []
  const colors: number[] = []
  const chunks = normalizedChunks.value
  const cn = chunks.length
  const denseT = Math.min(1, Math.max(0, (cn - 22) / 50))
  /** 点密时缩短连边距离、每点少连几条，避免蛛网 */
  const distanceLimit = props.linkDistance * (1 - denseT * 0.38)
  const maxLocal = cn > 42 ? 2 : cn > 30 ? 3 : 4

  for (let i = 0; i < chunks.length; i += 1) {
    const a = chunks[i]
    let localLinked = 0
    for (let j = i + 1; j < chunks.length; j += 1) {
      if (localLinked >= maxLocal) break
      const b = chunks[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const dz = a.z - b.z
      const distance = Math.sqrt(dx * dx + dy * dy + dz * dz)
      if (distance > distanceLimit) continue
      segments.push(a.x, a.y, a.z, b.x, b.y, b.z)
      const alpha = 1 - distance / distanceLimit
      const palette = ['#5eead4', '#60a5fa', '#c084fc', '#f472b6', '#facc15', '#fb7185']
      const ca = new THREE.Color(palette[i % palette.length]).multiplyScalar(0.35 + alpha * 0.35)
      const cb = new THREE.Color(palette[j % palette.length]).multiplyScalar(0.35 + alpha * 0.35)
      colors.push(ca.r, ca.g, ca.b, cb.r, cb.g, cb.b)
      localLinked += 1
    }
  }

  if (segments.length === 0) return

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(segments, 3))
  geometry.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
  const material = new THREE.LineBasicMaterial({
    vertexColors: true,
    transparent: true,
    opacity: 0.2 * (1 - denseT * 0.45),
    blending: THREE.AdditiveBlending,
  })
  clusterLines.value = new THREE.LineSegments(geometry, material)
  parent.add(clusterLines.value)
}

function buildQueryCapture(parent: THREE.Group) {
  if (querySphere.value) {
    parent.remove(querySphere.value)
    querySphere.value.geometry.dispose()
    ;(querySphere.value.material as THREE.Material).dispose()
    querySphere.value = null
  }
  if (queryLinks.value) {
    parent.remove(queryLinks.value)
    queryLinks.value.geometry.dispose()
    ;(queryLinks.value.material as THREE.Material).dispose()
    queryLinks.value = null
  }

  if (!showQueryCapture.value) return
  if (!props.activeQuery || retrievalHits.value.length === 0) return

  const sphereGeometry = new THREE.SphereGeometry(3.2, 24, 24)
  const sphereMaterial = new THREE.MeshBasicMaterial({
    color: '#ffd166',
    transparent: true,
    opacity: 0.95,
  })
  const sphere = new THREE.Mesh(sphereGeometry, sphereMaterial)
  sphere.position.set(0, 0, 0)
  querySphere.value = sphere
  parent.add(sphere)

  const linePositions: number[] = []
  const chunks = normalizedChunks.value
  retrievalHits.value.forEach((hit) => {
    const chunk = chunks.find((item) => String(item.id) === String(hit.chunkId))
    if (!chunk) return
    linePositions.push(0, 0, 0, chunk.x, chunk.y, chunk.z)
  })

  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.Float32BufferAttribute(linePositions, 3))
  const material = new THREE.LineDashedMaterial({
    color: '#7cf7ff',
    dashSize: 2.4,
    gapSize: 1.6,
    transparent: true,
    opacity: 0.95,
  })
  const lines = new THREE.LineSegments(geometry, material)
  lines.computeLineDistances()
  queryLinks.value = lines
  parent.add(queryLinks.value)
}

function addBackgroundParticles(s: THREE.Scene) {
  const count = 1200
  const positions = new Float32Array(count * 3)
  for (let i = 0; i < count; i += 1) {
    const radius = 220 + Math.random() * 180
    const theta = Math.random() * Math.PI * 2
    const phi = Math.acos(2 * Math.random() - 1)
    positions[i * 3] = radius * Math.sin(phi) * Math.cos(theta)
    positions[i * 3 + 1] = radius * Math.sin(phi) * Math.sin(theta)
    positions[i * 3 + 2] = radius * Math.cos(phi)
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  const material = new THREE.PointsMaterial({
    color: '#3a4f89',
    size: 0.9,
    transparent: true,
    opacity: 0.75,
    depthWrite: false,
  })
  const particles = new THREE.Points(geometry, material)
  s.add(particles)
}

function createLights(s: THREE.Scene) {
  const ambient = new THREE.AmbientLight('#89a6ff', 0.55)
  s.add(ambient)
  const pointLight = new THREE.PointLight('#68c5ff', 2.2, 300)
  pointLight.position.set(0, 0, 0)
  s.add(pointLight)
  const rimLight = new THREE.PointLight('#c084fc', 1.6, 260)
  rimLight.position.set(80, 40, 60)
  s.add(rimLight)
}

function handleResize() {
  const el = containerRef.value
  const c = camera.value
  const r = renderer.value
  if (!el || !c || !r) return
  const width = el.clientWidth || 960
  const height = el.clientHeight || 680
  c.aspect = width / height
  c.updateProjectionMatrix()
  r.setSize(width, height)
}

function pickChunkFromEvent(event: PointerEvent): ChunkPoint | null {
  if (!camera.value || !renderer.value || !raycaster) return null
  const el = renderer.value.domElement
  const rect = el.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera.value)

  const candidates: THREE.Points[] = []
  if (pointsLearning.value) candidates.push(pointsLearning.value)
  if (pointsMastered.value) candidates.push(pointsMastered.value)

  let best: { distance: number; index: number; list: ChunkPoint[] } | null = null

  for (const pts of candidates) {
    const hits = raycaster.intersectObject(pts, false)
    if (hits.length && hits[0].index !== undefined) {
      const idx = hits[0].index
      const list =
        pts === pointsLearning.value
          ? normalizedChunks.value.filter((c) => c.status === 'learning')
          : normalizedChunks.value.filter((c) => c.status === 'mastered')
      const chunk = list[idx]
      if (chunk && (!best || hits[0].distance < best.distance)) {
        best = { distance: hits[0].distance, index: idx, list }
      }
    }
  }

  if (!best) return null
  const chunk = best.list[best.index]
  return chunk ?? null
}

function onPointerDown(event: PointerEvent) {
  const c = pickChunkFromEvent(event)
  if (!c) return
  selectedChunk.value = c
  drawerOpen.value = true
}

async function loadEdges() {
  const base = props.edgesApiBase?.trim()
  if (!base) {
    apiEdges.value = []
    edgesError.value = ''
    edgesApiUnavailableHint.value = ''
    rebuildGraphLayers()
    return
  }

  edgesLoading.value = true
  edgesError.value = ''
  edgesApiUnavailableHint.value = ''
  try {
    const u = new URL(base, window.location.origin)
    u.searchParams.set('minScore', String(minScore.value))
    u.searchParams.set('maxEdgesPerNode', String(maxEdgesPerNode.value))
    const res = await fetch(u.toString(), { method: 'GET' })
    if (!res.ok) {
      apiEdges.value = []
      edgesApiUnavailableHint.value =
        res.status >= 500
          ? '图谱边服务暂不可用，已用邻近节点连线演示'
          : `边接口未就绪（${res.status}），已用邻近连线`
      return
    }
    const data = (await res.json()) as KnowledgeGraphEdgeDTO[]
    apiEdges.value = Array.isArray(data) ? data : []
  } catch {
    apiEdges.value = []
    edgesApiUnavailableHint.value = '无法连接图谱边服务，已用邻近节点连线演示'
  } finally {
    edgesLoading.value = false
    rebuildGraphLayers()
  }
}

function scheduleFetchEdges() {
  if (edgesFetchTimer) window.clearTimeout(edgesFetchTimer)
  edgesFetchTimer = window.setTimeout(() => {
    void loadEdges()
  }, 320)
}

function rebuildGraphLayers() {
  const parent = orbitGroup.value
  if (!parent) return
  buildPointsSubsets(parent)
  buildApiEdges(parent)
  if (apiEdges.value.length === 0 && showClusterLinks.value) buildClusterLinks(parent)
  else clearClusterLinks()
  buildQueryCapture(parent)
}

function patchLearningContextForNode(c: ChunkPoint, action: string) {
  learningStore.patchContext({
    currentModule: '课程地图 · 3D',
    recentAction: action,
    lastError: '',
    extraContext: {
      courseNodeId: c.id,
      courseNodeTitle: nodeTitle(c),
      courseNodeStatus: c.status,
    },
  })
}

function onEnterLearningLab() {
  const c = selectedChunk.value
  if (!c) return
  patchLearningContextForNode(c, `沉浸式学习：${nodeTitle(c)}`)
  drawerOpen.value = false
  router.push({
    name: 'LearningLab',
    params: { nodeId: String(c.id) },
  })
}

function onReadingPlan() {
  const c = selectedChunk.value
  if (!c) return
  const t = nodeTitle(c)
  patchLearningContextForNode(c, `阅读计划：${t}`)
  drawerOpen.value = false
  learningStore.seedTutorChatPrompt(
    `请基于知识点「${t}」为我生成一份循序渐进的阅读/学习小节计划（含学习目标、推荐顺序与每步预计耗时）。`,
  )
}

function onQuizMe() {
  const c = selectedChunk.value
  if (!c) return
  const t = nodeTitle(c)
  patchLearningContextForNode(c, `测验：${t}`)
  drawerOpen.value = false
  learningStore.seedTutorChatPrompt(
    `请围绕「${t}」出 3 道由浅入深的检查题（附参考答案，答案放在最后）。`,
  )
}

function onSearchDocs() {
  const c = selectedChunk.value
  if (!c) return
  patchLearningContextForNode(c, `搜索相关文档：${nodeTitle(c)}`)
  emit('search-related-docs', c)
  ElMessage.info('已记录学习上下文。请在侧栏「RAG 检索」中输入关键词，或接入文档搜索。')
  drawerOpen.value = false
}

function animate() {
  animationId = window.requestAnimationFrame(animate)
  const s = scene.value
  const c = camera.value
  const r = renderer.value
  const og = orbitGroup.value
  if (!s || !c || !r) return

  const elapsed = clock?.getElapsedTime() ?? 0

  if (props.autoRotate && og) {
    og.rotation.y = elapsed * 0.04
    og.rotation.x = Math.sin(elapsed * 0.18) * 0.03
  }

  if (glowMastered.value) {
    ;(glowMastered.value.material as THREE.PointsMaterial).opacity = 0.16 + Math.sin(elapsed * 1.4) * 0.05
  }

  if (clusterLines.value) {
    ;(clusterLines.value.material as THREE.LineBasicMaterial).opacity = 0.14 + Math.sin(elapsed * 0.8) * 0.05
  }

  if (apiLineSegments.value) {
    ;(apiLineSegments.value.material as THREE.LineBasicMaterial).opacity = 0.32 + Math.sin(elapsed * 0.6) * 0.06
  }

  if (querySphere.value) {
    const pulse = 1 + Math.sin(elapsed * 3.2) * 0.12
    querySphere.value.scale.setScalar(pulse)
  }

  if (queryLinks.value) {
    const m = queryLinks.value.material
    if (m instanceof THREE.LineDashedMaterial) {
      m.scale = 1 + Math.sin(elapsed * 3.8) * 0.14
      m.opacity = 0.52 + Math.sin(elapsed * 4.5) * 0.28
    } else if (m instanceof THREE.LineBasicMaterial) {
      m.opacity = 0.55 + Math.sin(elapsed * 4.5) * 0.3
    }
  }

  const cam = c
  for (let i = 0; i < learningRingMeshes.length; i += 1) {
    const mesh = learningRingMeshes[i]
    const pos = learningRingPositions[i]
    if (pos) {
      mesh.position.copy(pos)
      mesh.lookAt(cam.position)
    }
  }

  c.lookAt(0, 0, 0)
  r.render(s, c)
}

function initThree() {
  const el = containerRef.value
  if (!el) return
  const width = el.clientWidth || 960
  const height = el.clientHeight || 680

  const s = new THREE.Scene()
  s.background = new THREE.Color(props.backgroundColor)
  s.fog = new THREE.Fog(props.backgroundColor, 140, 380)
  scene.value = s

  const cam = new THREE.PerspectiveCamera(60, width / height, 0.1, 1000)
  cam.position.set(0, 20, 120)
  camera.value = cam

  const ren = new THREE.WebGLRenderer({ antialias: true, alpha: false })
  ren.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  ren.setSize(width, height)
  ren.outputColorSpace = THREE.SRGBColorSpace
  renderer.value = ren

  createLights(s)
  addBackgroundParticles(s)

  const og = new THREE.Group()
  orbitGroup.value = og
  s.add(og)

  raycaster = new THREE.Raycaster()
  raycaster.params.Points = { threshold: 12 }

  rebuildGraphLayers()

  clock = new THREE.Clock()
  el.innerHTML = ''
  el.appendChild(ren.domElement)
  ren.domElement.addEventListener('pointerdown', onPointerDown)

  resizeObserver = new ResizeObserver(() => handleResize())
  resizeObserver.observe(el)

  animate()
}

function disposeObject3D(object: THREE.Object3D | null) {
  if (!object) return
  object.traverse((child) => {
    const mesh = child as THREE.Mesh
    if ('geometry' in mesh && mesh.geometry) mesh.geometry.dispose()
    if ('material' in mesh && mesh.material) {
      const material = mesh.material
      if (Array.isArray(material)) material.forEach((m) => m.dispose())
      else material.dispose()
    }
  })
}

onMounted(() => {
  initThree()
  void loadEdges()
})

watch(
  () => normalizedChunks.value,
  () => {
    rebuildGraphLayers()
  },
  { deep: true },
)

watch(
  () => [props.retrievalHits, props.activeQuery],
  () => {
    rebuildGraphLayers()
  },
  { deep: true },
)

watch(
  () => props.visualStep,
  () => {
    rebuildGraphLayers()
  },
)

onBeforeUnmount(() => {
  window.cancelAnimationFrame(animationId)
  if (edgesFetchTimer) window.clearTimeout(edgesFetchTimer)
  resizeObserver?.disconnect()

  renderer.value?.domElement.removeEventListener('pointerdown', onPointerDown)

  const og = orbitGroup.value
  const s = scene.value
  if (og && s) {
    disposeObject3D(og)
    s.remove(og)
  }

  disposeObject3D(scene.value)
  renderer.value?.dispose()

  orbitGroup.value = null
  scene.value = null
  camera.value = null
  renderer.value = null
  clock = null
  raycaster = null
})
</script>

<style scoped>
.vector-space-3d {
  position: relative;
  width: 100%;
  height: 760px;
  overflow: hidden;
  border: 1px solid #182033;
  border-radius: 20px;
  background: radial-gradient(circle at center, #08101f 0%, #020412 58%, #010208 100%);
  box-shadow:
    inset 0 0 80px rgb(86 149 255 / 10%),
    0 12px 30px rgb(2 6 23 / 24%);
}

.canvas-container {
  width: 100%;
  height: 100%;
  cursor: crosshair;
}

.overlay-panel {
  position: absolute;
  min-width: 220px;
  padding: 14px 16px;
  border: 1px solid rgb(148 163 184 / 18%);
  border-radius: 14px;
  background: rgb(15 23 42 / 58%);
  color: #dbeafe;
  backdrop-filter: blur(10px);
  z-index: 2;
}

.overlay-panel--stats {
  top: 16px;
  left: 16px;
}

.overlay-panel--controls {
  bottom: 16px;
  right: 16px;
  width: min(320px, calc(100% - 32px));
}

.panel-title {
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 700;
}

.panel-row {
  margin-top: 6px;
  font-size: 12px;
  color: #bfdbfe;
}

.panel-row--muted {
  color: #94a3b8;
}

.panel-row--warn {
  color: #fecaca;
}

.controls-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 10px;
  color: #e0e7ff;
}

.slider-row {
  margin-bottom: 12px;
}

.slider-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.drawer-inner {
  padding: 22px 20px 28px;
  color: #e2e8f0;
  min-height: 100%;
  background: linear-gradient(165deg, rgba(30, 27, 75, 0.92), rgba(15, 23, 42, 0.94));
}

.drawer-kicker {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #a5b4fc;
  margin-bottom: 8px;
}

.drawer-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 800;
  line-height: 1.3;
  color: #f8fafc;
}

.drawer-intro {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.6;
  color: #cbd5e1;
}

.drawer-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 22px;
}

.pill {
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgb(51 65 85 / 0.6);
  border: 1px solid rgb(148 163 184 / 0.35);
}

.pill--locked {
  color: #94a3b8;
}

.pill--learning {
  color: #7dd3fc;
  border-color: rgb(125 211 252 / 0.5);
}

.pill--mastered {
  color: #fde68a;
  border-color: rgb(253 224 71 / 0.45);
  box-shadow: 0 0 16px rgb(253 224 71 / 0.2);
}

.pill--progress {
  color: #bae6fd;
}

.drawer-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-btn {
  justify-content: flex-start;
  width: 100%;
  border-radius: 12px !important;
  font-weight: 600;
}

.action-btn--emphasis {
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.35);
}
</style>

<style>
/* 半透明抽屉遮罩 — 非 scoped 以便作用到 teleport 根 */
.course-drawer-modal.el-overlay {
  background-color: rgba(2, 6, 23, 0.45) !important;
  backdrop-filter: blur(4px);
}

.course-drawer .el-drawer__body {
  padding: 0 !important;
  background: transparent !important;
}
</style>
