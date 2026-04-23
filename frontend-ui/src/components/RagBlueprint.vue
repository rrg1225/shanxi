<template>
  <div class="rag-blueprint">
    <div class="toolbar">
      <div>
        <h2>RAG 蓝图编辑器</h2>
        <p>节点内可调参；切分策略会同步右侧 3D 点云密度。「测试检索」演示查询向量 → Top-K 连线。</p>
      </div>
      <el-button type="primary" @click="resetGraph">重置流程</el-button>
    </div>

    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      class="rag-flow"
      fit-view-on-init
      :default-viewport="{ zoom: 1.05 }"
      :min-zoom="0.4"
      :max-zoom="1.8"
      @connect="onConnect"
      @node-drag-stop="onNodeDragStop"
    >
      <Background pattern-color="#d7e3ff" :gap="18" />
      <MiniMap pannable zoomable />
      <Controls />

      <template #node-ragChunkPanel="nodeProps">
        <div class="rag-node" @pointerdown.stop>
          <Handle class="rag-handle rag-handle--in" type="target" :position="Position.Left" />
          <div class="rag-node__title">{{ nodeProps.data.label }}</div>
          <p class="rag-node__desc">{{ nodeProps.data.description }}</p>
          <div class="rag-node__fields">
            <span class="rag-field-label">chunk_size</span>
            <el-input-number
              :model-value="nodeProps.data.chunkSize ?? 800"
              :min="200"
              :max="2000"
              :step="50"
              size="small"
              controls-position="right"
              class="rag-field-ctl"
              @update:model-value="(v: number | undefined) => onChunkField(nodeProps.id, 'chunkSize', Number(v))"
            />
            <span class="rag-field-label">overlap</span>
            <el-input-number
              :model-value="nodeProps.data.chunkOverlap ?? 150"
              :min="0"
              :max="Math.min(900, (nodeProps.data.chunkSize ?? 800) - 1)"
              :step="10"
              size="small"
              controls-position="right"
              class="rag-field-ctl"
              @update:model-value="(v: number | undefined) => onChunkField(nodeProps.id, 'chunkOverlap', Number(v))"
            />
          </div>
          <Handle class="rag-handle rag-handle--out" type="source" :position="Position.Right" />
        </div>
      </template>

      <template #node-ragEmbedPanel="nodeProps">
        <div class="rag-node" @pointerdown.stop>
          <Handle class="rag-handle rag-handle--in" type="target" :position="Position.Left" />
          <div class="rag-node__title">{{ nodeProps.data.label }}</div>
          <p class="rag-node__desc">{{ nodeProps.data.description }}</p>
          <div class="rag-node__fields">
            <span class="rag-field-label">模型</span>
            <el-select
              :model-value="nodeProps.data.embeddingModel ?? 'text-embedding-v3'"
              size="small"
              class="rag-field-ctl rag-field-ctl--full"
              @update:model-value="(v: string) => onEmbedModel(nodeProps.id, v)"
            >
              <el-option label="text-embedding-v3" value="text-embedding-v3" />
              <el-option label="text-embedding-v2" value="text-embedding-v2" />
            </el-select>
          </div>
          <Handle class="rag-handle rag-handle--out" type="source" :position="Position.Right" />
        </div>
      </template>

      <template #node-ragVectorPanel="nodeProps">
        <div class="rag-node rag-node--readonly" @pointerdown.stop>
          <Handle class="rag-handle rag-handle--in" type="target" :position="Position.Left" />
          <div class="rag-node__title">{{ nodeProps.data.label }}</div>
          <p class="rag-node__desc">{{ nodeProps.data.description }}</p>
          <div class="rag-node__hint">
            与 Pinia 中模拟分片数联动：<strong>{{ learningStore.ragSimulatedChunks.length }}</strong> 向量点
          </div>
          <Handle class="rag-handle rag-handle--out" type="source" :position="Position.Right" />
        </div>
      </template>

      <template #node-ragSearchPanel="nodeProps">
        <div class="rag-node" @pointerdown.stop>
          <Handle class="rag-handle rag-handle--in" type="target" :position="Position.Left" />
          <div class="rag-node__title">{{ nodeProps.data.label }}</div>
          <p class="rag-node__desc">{{ nodeProps.data.description }}</p>
          <div class="rag-node__fields">
            <span class="rag-field-label">Top-K</span>
            <el-input-number
              :model-value="nodeProps.data.topK ?? 3"
              :min="1"
              :max="12"
              :step="1"
              size="small"
              controls-position="right"
              class="rag-field-ctl"
              @update:model-value="(v: number | undefined) => onTopKChange(nodeProps.id, Number(v))"
            />
          </div>
          <el-button
            type="primary"
            size="small"
            class="rag-test-btn"
            :loading="retrievalRunning"
            @click.stop="onTestRetrieval"
          >
            测试检索（执行流）
          </el-button>
          <Handle class="rag-handle rag-handle--out" type="source" :position="Position.Right" />
        </div>
      </template>
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  VueFlow,
  useVueFlow,
  Handle,
  Position,
  type Connection,
  type Edge,
  type Node,
  type NodeDragEvent,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { ElMessage } from 'element-plus'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { postExperimentLog } from '@/api/modules/experimentLog'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

type RagNodeType =
  | 'document-parse'
  | 'text-split'
  | 'embedding'
  | 'vector-db'
  | 'similarity-search'

interface RagNodeData {
  label: string
  description: string
  type: RagNodeType
  chunkSize?: number
  chunkOverlap?: number
  topK?: number
  embeddingModel?: string
}

type RagVisualStep = 'text-split' | 'vector-db' | 'similarity-search'

const emit = defineEmits<{
  (e: 'step-change', step: RagVisualStep): void
}>()

const props = defineProps<{
  tenantId: number
  userId: number
}>()

const learningStore = useGlobalLearningContextStore()
const retrievalRunning = ref(false)

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
    /* ignore */
  }
}

const initialNodes: Node<RagNodeData>[] = [
  {
    id: 'document-parse',
    type: 'input',
    position: { x: 60, y: 120 },
    data: {
      label: '文档解析',
      description: '上传 PDF / TXT / 网页并做清洗',
      type: 'document-parse',
    },
  },
  {
    id: 'text-split',
    type: 'ragChunkPanel',
    position: { x: 300, y: 100 },
    data: {
      label: '文本切分',
      description: 'Chunk Size / Overlap',
      type: 'text-split',
      chunkSize: 800,
      chunkOverlap: 150,
    },
  },
  {
    id: 'embedding',
    type: 'ragEmbedPanel',
    position: { x: 560, y: 100 },
    data: {
      label: '向量化',
      description: 'Embedding 编码',
      type: 'embedding',
      embeddingModel: 'text-embedding-v3',
    },
  },
  {
    id: 'vector-db',
    type: 'ragVectorPanel',
    position: { x: 820, y: 100 },
    data: {
      label: '向量库',
      description: 'Milvus 存储与索引',
      type: 'vector-db',
    },
  },
  {
    id: 'similarity-search',
    type: 'ragSearchPanel',
    position: { x: 1080, y: 100 },
    data: {
      label: '相似度检索',
      description: 'Top-K 召回',
      type: 'similarity-search',
      topK: 3,
    },
  },
]

const initialEdges: Edge[] = [
  { id: 'e1', source: 'document-parse', target: 'text-split', animated: true, label: 'clean text' },
  { id: 'e2', source: 'text-split', target: 'embedding', animated: true, label: 'chunks' },
  { id: 'e3', source: 'embedding', target: 'vector-db', animated: true, label: 'vectors' },
  { id: 'e4', source: 'vector-db', target: 'similarity-search', animated: true, label: 'recall' },
]

/** Vue Flow 的 Node 泛型过深，这里用宽松类型避免 vue-tsc 实例化溢出 */
const nodes = ref(structuredClone(initialNodes) as any[])
const edges = ref(structuredClone(initialEdges) as Edge[])

const { addEdges } = useVueFlow()

function patchNodeData(id: string, patch: Partial<RagNodeData>) {
  nodes.value = nodes.value.map((n: any) => {
    if (n.id !== id) return n
    const base = (n.data ?? {}) as RagNodeData
    return { ...n, data: { ...base, ...patch } }
  })
}

function syncChunkStrategyFromGraph() {
  const split = nodes.value.find((n) => n.id === 'text-split')
  if (!split?.data) return
  const cs = split.data.chunkSize ?? 800
  const co = split.data.chunkOverlap ?? 150
  learningStore.applyRagChunkStrategy(cs, co)
}

function onChunkField(id: string, key: 'chunkSize' | 'chunkOverlap', value: number) {
  if (!Number.isFinite(value)) return
  patchNodeData(id, { [key]: value })
  if (id === 'text-split') {
    const n = nodes.value.find((x) => x.id === 'text-split')
    if (n?.data) {
      const cs = n.data.chunkSize ?? 800
      let co = n.data.chunkOverlap ?? 150
      co = Math.min(cs - 1, Math.max(0, co))
      if (co !== n.data.chunkOverlap) patchNodeData(id, { chunkOverlap: co })
      learningStore.applyRagChunkStrategy(cs, co)
      logOp('RAG_BLUEPRINT_CHUNK_PARAM', { chunkSize: cs, chunkOverlap: co })
    }
  }
}

function onEmbedModel(id: string, model: string) {
  patchNodeData(id, { embeddingModel: model })
  learningStore.setRagPipelineParams({ embeddingModel: model })
  learningStore.ragVectorSpaceRevision += 1
  logOp('RAG_BLUEPRINT_EMBED_MODEL', { embeddingModel: model })
}

function onTopKChange(id: string, topK: number) {
  if (!Number.isFinite(topK)) return
  patchNodeData(id, { topK })
  learningStore.setRagPipelineParams({ topK })
}

async function onTestRetrieval() {
  const search = nodes.value.find((n) => n.id === 'similarity-search')
  const k = search?.data?.topK ?? 3
  learningStore.setRagPipelineParams({ topK: k })
  retrievalRunning.value = true
  try {
    learningStore.patchContext({
      currentModule: 'RAG 蓝图',
      recentAction: '测试检索：查询向量 → Top-K',
      extraContext: {
        ragBlueprint: true,
        topK: k,
        simChunks: learningStore.ragSimulatedChunks.length,
      },
    })
    learningStore.triggerRagRetrievalSimulation('RAG 检索分数怎么解读？')
    emit('step-change', 'similarity-search')
    ElMessage.success({ message: '执行流：已在 3D 视图演示 Top-K 连线', grouping: true })
    logOp('RAG_BLUEPRINT_TEST_RETRIEVAL', { topK: k })
  } finally {
    window.setTimeout(() => {
      retrievalRunning.value = false
    }, 400)
  }
}

function onConnect(connection: Connection) {
  addEdges([
    {
      ...connection,
      id: `edge-${connection.source}-${connection.target}-${Date.now()}`,
      animated: true,
    },
  ])

  const target = String(connection.target)
  if (target === 'text-split') emit('step-change', 'text-split')
  if (target === 'vector-db') emit('step-change', 'vector-db')
  if (target === 'similarity-search') emit('step-change', 'similarity-search')

  logOp('CONNECT_RAG_NODE', {
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  })
}

function onNodeDragStop(event: NodeDragEvent) {
  const node = event.node as Node<RagNodeData>
  const label = node.data?.label ?? node.id
  ElMessage.success(`${label} 已移动到 (${Math.round(node.position.x)}, ${Math.round(node.position.y)})`)
}

function resetGraph() {
  nodes.value = structuredClone(initialNodes)
  edges.value = []
  learningStore.clearRagRetrievalDemo()
  learningStore.applyRagChunkStrategy(800, 150)
  learningStore.setRagPipelineParams({ topK: 3, embeddingModel: 'text-embedding-v3' })
  emit('step-change', 'text-split')
  logOp('RESET_RAG_BLUEPRINT', { component: 'RagBlueprint' })
}

onMounted(() => {
  syncChunkStrategyFromGraph()
  emit('step-change', 'text-split')
  logOp('INIT_RAG_BLUEPRINT', { component: 'RagBlueprint' })
})
</script>

<style scoped>
.rag-blueprint {
  height: 760px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.toolbar h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.toolbar p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.rag-flow {
  flex: 1;
  border: 1px solid #dbe5f4;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
}

.rag-node {
  min-width: 200px;
  max-width: 240px;
  padding: 10px 12px 12px;
  border-radius: 14px;
  border: 1px solid #6366f1;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.12);
  font-size: 12px;
  color: #1e293b;
  position: relative;
}

.rag-node--readonly {
  border-color: #94a3b8;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.rag-node__title {
  font-weight: 800;
  font-size: 13px;
  margin-bottom: 4px;
  color: #312e81;
}

.rag-node__desc {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.rag-node__fields {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 6px 8px;
  align-items: center;
}

.rag-field-label {
  font-size: 10px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.rag-field-ctl {
  width: 100% !important;
}

.rag-field-ctl--full {
  grid-column: 1 / -1;
}

.rag-node__hint {
  margin: 0;
  font-size: 11px;
  color: #475569;
  line-height: 1.4;
}

.rag-test-btn {
  width: 100%;
  margin-top: 10px;
  font-weight: 700;
  border-radius: 10px !important;
}

.rag-handle--in {
  left: -6px !important;
}

.rag-handle--out {
  right: -6px !important;
}

:deep(.vue-flow__node-input),
:deep(.vue-flow__node-output) {
  min-width: 180px;
  border-radius: 14px;
  border: 1px solid #90a8ff;
  background: #fff;
  box-shadow: 0 12px 24px rgb(59 130 246 / 10%);
  padding: 12px 14px;
  font-size: 13px;
  color: #1f2937;
}

:deep(.vue-flow__node-default .vue-flow__node-label),
:deep(.vue-flow__node-input .vue-flow__node-label),
:deep(.vue-flow__node-output .vue-flow__node-label) {
  font-weight: 700;
}

:deep(.vue-flow__edge-path) {
  stroke: #5b7cff;
  stroke-width: 2;
}
</style>
