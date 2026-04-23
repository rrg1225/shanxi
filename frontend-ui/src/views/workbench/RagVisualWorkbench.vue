<template>
  <div class="workbench">
    <header class="page-header">
      <div>
        <h1>实验二：知识注入与幻觉消除虚拟仿真（RAG 3D 可视化）</h1>
        <p>上传文档 -> 向量化建模 -> 3D 召回展示 -> 增强问答验证。</p>
      </div>
      <div class="header-actions">
        <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
        <el-tag type="success">Backend: 8082</el-tag>
        <el-tag type="warning">AI Gateway: 8000</el-tag>
      </div>
    </header>

    <section class="toolbar-card">
      <el-form :inline="true" class="toolbar-form">
        <el-form-item label="Tenant ID">
          <el-input-number v-model="tenantId" :min="1" />
        </el-form-item>
        <el-form-item label="Owner User ID">
          <el-input-number v-model="ownerUserId" :min="1" />
        </el-form-item>
        <el-form-item label="Teacher User ID">
          <el-input-number v-model="teacherUserId" :min="1" />
        </el-form-item>
        <el-form-item label="知识库权限">
          <el-select v-model="visibility" style="width: 140px">
            <el-option label="私有" value="PRIVATE" />
            <el-option label="公共" value="PUBLIC" />
          </el-select>
        </el-form-item>
      </el-form>
    </section>

    <section class="grid-main">
      <div class="main-panel">
        <div class="panel-title">实验二：高维向量空间召回仿真</div>
        <KnowledgeUniverse @prepare-learning-lab="handlePrepareLearningLab" />
      </div>

      <aside class="side-stack">
        <div class="panel">
          <div class="panel-title">文档上传与预处理</div>
          <el-upload
            drag
            :auto-upload="false"
            :show-file-list="false"
            accept=".txt,.md,.pdf"
            :on-change="handleFileChange"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          </el-upload>

          <div v-if="selectedFile" class="upload-meta">当前文件：{{ selectedFile.name }}</div>

          <div class="upload-actions">
            <el-button type="primary" :loading="uploading" @click="submitUpload">上传并处理</el-button>
          </div>

          <div v-if="uploading || uploadPercent > 0" class="upload-progress">
            <el-progress :percentage="uploadPercent" :stroke-width="10" />
            <div v-if="uploadPhaseLabel" class="upload-phase">{{ uploadPhaseLabel }}</div>
          </div>

          <el-alert v-if="lastUploadSummary" type="success" :closable="false" class="upload-summary" :title="lastUploadSummary" />
        </div>

        <div class="panel">
          <div class="panel-title">实验二：检索增强生成验证</div>
          <el-input v-model="query" type="textarea" :rows="5" placeholder="请输入检索问题" />
          <div class="search-actions">
            <el-button type="primary" :loading="searching" @click="submitSearch">开始检索</el-button>
          </div>

          <div v-if="searchResults.length" class="result-list">
            <div v-for="item in searchResults" :key="item.chunkId" class="result-item">
              <div class="result-header">
                <span>Chunk #{{ item.chunkIndex }}</span>
                <span>Score: {{ item.score?.toFixed(4) }}</span>
              </div>
              <div class="result-text">{{ item.text }}</div>
            </div>
          </div>
        </div>

      </aside>
    </section>
    <el-dialog v-model="showDemoModal" title="实验二 · 玩法演示" width="560px">
      <p>这里将放置 RAG 检索与 3D 向量空间联动演示视频。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RagVisualWorkbench' })

import { computed, ref } from 'vue'
import { UploadFilled, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import KnowledgeUniverse from '@/components/KnowledgeUniverse.vue'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'
import { useWorkbenchProgressStore, type RagVisualStep } from '@/stores/workbenchProgress'
import { ragSearch, uploadDocument, type RagSearchHit } from '@/api/modules/ai'
import { postExperimentLog } from '@/api/modules/experimentLog'
import { getMockDocumentProcessResponse, getMockRagSearchResponse, type VectorChunkMock } from '@/mocks/aiMocks'

const tenantId = ref(1)
const ownerUserId = ref(1001)
const teacherUserId = ref(2001)
const visibility = ref<'PUBLIC' | 'PRIVATE'>('PRIVATE')

const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadPhaseLabel = ref('')
const searching = ref(false)

const phaseLabels: Record<string, string> = {
  accepted: '已接收，准备调用网关',
  gateway: '文档解析与向量化（ai-gateway）…',
  gateway_done: '网关处理完成',
  persist: '写入元数据…',
  done: '完成',
}

const query = ref('RAG 实验流程如何设计，并如何可视化展示检索结果？')
const searchResults = ref<RagSearchHit[]>([])
const vectorChunks = ref<Array<{ id: number | string; x: number; y: number; z: number; label?: string; score?: number; cluster?: string | number }>>([])
const lastUploadSummary = ref('')
const showDemoModal = ref(false)

const progressStore = useWorkbenchProgressStore()
const ragVisualStep = computed<RagVisualStep>(() => progressStore.ragVisualStep)

const learningStore = useGlobalLearningContextStore()

async function logOp(opType: string, opPayload?: Record<string, unknown>) {
  try {
    await postExperimentLog({
      tenantId: tenantId.value,
      userId: ownerUserId.value,
      opType,
      opPayload,
      createdAtMillis: Date.now(),
    })
  } catch {
    // 埋点失败不影响功能
  }
}

const vectorHits = computed(() =>
  searchResults.value.slice(0, 3).map((item) => ({
    chunkId: item.chunkId,
    score: item.score,
  })),
)

function handlePrepareLearningLab(node: {
  id: string
  name: string
  subtitle: string
  relatedTopics: string[]
}) {
  learningStore.patchContext({
    currentModule: '3D 知识图谱',
    recentAction: `进入学习空间：${node.name}`,
    lastError: '',
    extraContext: {
      source: 'RagVisualKnowledgeUniverse',
      knowledgeNodeId: node.id,
      selectedBook: node.name,
      subtitle: node.subtitle,
      relatedTopics: node.relatedTopics,
      topHits: vectorHits.value.length,
      ragStep: ragVisualStep.value,
    },
  })
}

function handleFileChange(file: { raw?: File }) {
  selectedFile.value = file.raw ?? null
}

async function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  uploadPercent.value = 0
  uploadPhaseLabel.value = ''
  try {
    const data = await uploadDocument({
      file: selectedFile.value,
      tenantId: tenantId.value,
      ownerUserId: ownerUserId.value,
      teacherUserId: visibility.value === 'PUBLIC' ? teacherUserId.value : undefined,
      visibility: visibility.value,
      chunkSize: 800,
      chunkOverlap: 150,
      onProgress: ({ percent, phase }) => {
        uploadPercent.value = Math.min(100, Math.max(0, percent))
        uploadPhaseLabel.value = phaseLabels[phase] ?? phase
      },
    })

    vectorChunks.value = data.chunks.map((item, index) => ({
      id: item.chunkId,
      x: item.coords?.[0] ?? index * 2,
      y: item.coords?.[1] ?? 0,
      z: item.coords?.[2] ?? 0,
      label: item.textPreview,
      cluster: Math.floor(index / 4),
    }))

    lastUploadSummary.value = `文档 ${selectedFile.value.name} 已处理完成，共 ${data.chunkCount} 个 chunks`
    learningStore.patchContext({
      currentModule: '知识库处理',
      recentAction: `完成文档向量化：${selectedFile.value.name}`,
      lastError: '',
      extraContext: {
        documentId: data.documentId,
        chunkCount: data.chunkCount,
        embeddingModel: data.embeddingModel,
      },
    })

    void logOp('UPLOAD_DOCUMENT', {
      fileName: selectedFile.value.name,
      documentId: data.documentId,
      chunkCount: data.chunkCount,
      embeddingModel: data.embeddingModel,
    })

    ElMessage.success('文档上传并处理完成')
  } catch (error) {
    const message = error instanceof Error ? error.message : '上传失败'

    const mockDoc = getMockDocumentProcessResponse({
      tenantId: tenantId.value,
      ownerUserId: ownerUserId.value,
      chunkCount: 90,
    })

    vectorChunks.value = mockDoc.chunks.map((item, index) => ({
      id: item.chunkId,
      x: item.coords?.[0] ?? index * 2,
      y: item.coords?.[1] ?? 0,
      z: item.coords?.[2] ?? 0,
      label: item.textPreview,
      cluster: Math.floor(index / 4),
    }))

    lastUploadSummary.value = `Mock 文档处理完成（真实上传失败：${message}）`
    learningStore.patchContext({
      currentModule: '知识库处理',
      lastError: message,
      recentAction: '切换 Mock chunks',
      extraContext: { documentId: mockDoc.documentId, chunkCount: mockDoc.chunkCount },
    })

    ElMessage.warning('上传失败，已切换到 Mock 展示')
  } finally {
    uploading.value = false
    uploadPercent.value = 0
    uploadPhaseLabel.value = ''
  }
}

async function submitSearch() {
  searching.value = true
  try {
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(), 10000)

    const data = await ragSearch(
      {
        query: query.value,
        tenantId: tenantId.value,
        visibilityMode: 'BOTH',
        ownerUserId: ownerUserId.value,
        topK: 6,
      },
      { signal: controller.signal },
    )

    window.clearTimeout(timeout)

    searchResults.value = data.results || []

    if (data.results?.length) {
      const hitMap = new Map(data.results.map((item) => [String(item.chunkId), item]))
      vectorChunks.value = vectorChunks.value.map((chunk) => {
        const hit = hitMap.get(String(chunk.id))
        if (!hit) return chunk
        return {
          ...chunk,
          x: hit.coords?.[0] ?? chunk.x,
          y: hit.coords?.[1] ?? chunk.y,
          z: hit.coords?.[2] ?? chunk.z,
          score: hit.score,
        }
      })
    }

    learningStore.patchContext({
      currentModule: 'RAG 检索',
      recentAction: `执行检索：${query.value}`,
      lastError: '',
      extraContext: {
        topK: data.topK,
        hitCount: data.results?.length ?? 0,
      },
    })

    void logOp('RAG_SEARCH', {
      query: query.value,
      topK: data.topK,
      hitCount: data.results?.length ?? 0,
    })

    ElMessage.success('检索完成')
  } catch (error) {
    const message = error instanceof Error ? error.message : '检索失败'

    if (vectorChunks.value.length === 0) {
      const mockDoc = getMockDocumentProcessResponse({
        tenantId: tenantId.value,
        ownerUserId: ownerUserId.value,
        chunkCount: 110,
      })

      vectorChunks.value = mockDoc.chunks.map((item, index) => ({
        id: item.chunkId,
        x: item.coords?.[0] ?? index * 2,
        y: item.coords?.[1] ?? 0,
        z: item.coords?.[2] ?? 0,
        label: item.textPreview,
        cluster: Math.floor(index / 4),
      }))
    }

    const mockRag = getMockRagSearchResponse({
      query: query.value,
      topK: 6,
      vectorChunks: vectorChunks.value.map((c): VectorChunkMock => ({
        id: c.id,
        x: c.x,
        y: c.y,
        z: c.z,
        label: c.label,
        cluster: c.cluster,
      })),
    })

    searchResults.value = mockRag.results

    const hitMap = new Map(searchResults.value.map((item) => [String(item.chunkId), item]))
    vectorChunks.value = vectorChunks.value.map((chunk) => {
      const hit = hitMap.get(String(chunk.id))
      if (!hit) return chunk
      return {
        ...chunk,
        x: hit.coords?.[0] ?? chunk.x,
        y: hit.coords?.[1] ?? chunk.y,
        z: hit.coords?.[2] ?? chunk.z,
        score: hit.score,
      }
    })

    learningStore.patchContext({
      currentModule: 'RAG 检索',
      lastError: message,
      recentAction: '切换 Mock 检索结果',
      extraContext: { hitCount: mockRag.results.length },
    })

    ElMessage.warning('检索失败，已切换到 Mock 展示')
  } finally {
    searching.value = false
  }
}
</script>

<style scoped>
.workbench {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.page-header,
.toolbar-card,
.panel,
.main-panel {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  padding: 20px 24px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.demo-btn {
  margin-right: 4px;
}
.toolbar-card {
  padding: 16px 20px;
}
.toolbar-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.grid-main {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(380px, 0.7fr);
  gap: 18px;
  align-items: start;
}
.main-panel {
  padding: 16px;
}
.side-stack {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-height: calc(100vh - 220px);
  overflow: auto;
}
.panel {
  padding: 16px;
}
.panel-title {
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 700;
}
.upload-meta,
.upload-summary {
  margin-top: 10px;
}
.upload-actions,
.search-actions {
  margin-top: 10px;
}
.upload-progress {
  margin-top: 14px;
}
.upload-phase {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(71, 85, 105, 1);
}
.result-list {
  margin-top: 12px;
}
.result-item {
  border-bottom: 1px dashed rgba(148, 163, 184, 0.35);
  padding: 10px 0;
}
.result-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(71, 85, 105, 1);
  margin-bottom: 6px;
}
.result-text {
  font-size: 13px;
  color: rgba(15, 23, 42, 0.95);
}

@media (max-width: 1200px) {
  .grid-main {
    grid-template-columns: 1fr;
  }

  .side-stack {
    max-height: none;
    overflow: visible;
  }
}
</style>

