<template>
  <div class="workbench">
    <header class="page-header">
      <div>
        <h1>实验二：知识注入与幻觉消除虚拟仿真（RAG 蓝图连线）</h1>
        <p>连线会驱动右侧 3D 视觉的展示阶段（在 rag-visual 中生效）。</p>
      </div>
      <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
    </header>

    <el-card class="lab-guide" shadow="never">
      <template #header>
        <div class="lab-guide__title">实验指导书</div>
      </template>
      <div class="lab-guide__content">
        <p>
          <strong>【实验目的】</strong
          >构建检索增强生成（RAG）链路，验证外部高维知识注入对消除大模型“幻觉”的有效性。
        </p>
        <p>
          <strong>【实验操作】</strong
          >1. 连线构建数据流；2. 观察右侧高维向量空间召回仿真；3. 在底部进行生成对比测试。
        </p>
      </div>
    </el-card>

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

    <section class="main-panel split">
      <div class="pane pane--blueprint">
        <RagBlueprint :tenant-id="tenantId" :user-id="ownerUserId" @step-change="onStepChange" />
      </div>
      <div class="pane pane--vector">
        <VectorSpace3D
          :chunks="vectorChunks"
          :retrieval-hits="retrievalHitsFor3d"
          :active-query="retrievalQueryFor3d"
          :visual-step="ragVisualStep"
          :auto-rotate="false"
          edges-api-base=""
        />
      </div>
    </section>

    <section class="compare-panel">
      <header class="compare-panel__head">
        <h3>幻觉消除效果对比</h3>
      </header>
      <div class="compare-panel__controls">
        <el-input
          v-model="testQuestion"
          type="textarea"
          :rows="3"
          placeholder="输入测试问题，例如：RAG 流程中的 Chunk 与检索排序分别起什么作用？"
        />
        <el-button type="primary" :loading="isGenerating" :disabled="!testQuestion.trim()" @click="runContrastTest">
          运行对比实验
        </el-button>
      </div>
      <div class="compare-panel__results">
        <article class="result-card result-card--base">
          <h4>❌ 基础模型盲答（易产生幻觉）</h4>
          <p>{{ baseModelAnswer || '点击“运行对比实验”后查看基线输出。' }}</p>
        </article>
        <article class="result-card result-card--rag">
          <h4>✅ RAG 知识注入后（消除幻觉）</h4>
          <p>{{ ragEnhancedAnswer || '将使用当前召回结果拼接 Context 后生成增强答案。' }}</p>
        </article>
      </div>
    </section>

    <el-dialog v-model="showDemoModal" title="知识外脑 · 玩法演示" width="560px">
      <p>这里将放置 RAG 蓝图搭建演示视频，后续可嵌入完整教学流程。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'RagBuildWorkbench' })

import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import RagBlueprint from '@/components/RagBlueprint.vue'
import VectorSpace3D from '@/components/VectorSpace3D.vue'
import type { ChunkPoint } from '@/components/VectorSpace3D.vue'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'
import { useWorkbenchProgressStore, type RagVisualStep } from '@/stores/workbenchProgress'

const tenantId = ref(1)
const ownerUserId = ref(1001)
const teacherUserId = ref(2001)
const visibility = ref<'PUBLIC' | 'PRIVATE'>('PRIVATE')
const showDemoModal = ref(false)

const learningStore = useGlobalLearningContextStore()
const { ragSimulatedChunks, ragRetrievalDemo } = storeToRefs(learningStore)

const vectorChunks = computed<ChunkPoint[]>(() => ragSimulatedChunks.value as ChunkPoint[])
const retrievalHitsFor3d = computed(() => ragRetrievalDemo.value?.hits ?? [])
const retrievalQueryFor3d = computed(() => ragRetrievalDemo.value?.query ?? '')

const progressStore = useWorkbenchProgressStore()
const { ragVisualStep } = storeToRefs(progressStore)

const testQuestion = ref('')
const baseModelAnswer = ref('')
const ragEnhancedAnswer = ref('')
const isGenerating = ref(false)

function pickHitText(hit: unknown): string {
  if (!hit || typeof hit !== 'object') return ''
  const candidate = hit as Record<string, unknown>
  const text =
    candidate.text ??
    candidate.content ??
    candidate.chunkText ??
    candidate.snippet ??
    candidate.summary ??
    candidate.title
  return typeof text === 'string' ? text.trim() : ''
}

function buildRagContext(): string {
  const texts = retrievalHitsFor3d.value.map((h) => pickHitText(h)).filter((t) => !!t)
  return texts.slice(0, 6).join('\n')
}

async function consumeSseFull(response: Response, onToken: (t: string) => void): Promise<void> {
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

async function streamPrompt(prompt: string, temperature = 0.6, topP = 0.9): Promise<string> {
  const res = await fetch('/api/ai/prompt-test', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ prompt, temperature, top_p: topP }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `HTTP ${res.status}`)
  }
  let acc = ''
  await consumeSseFull(res, (t) => {
    acc += t
  })
  return acc.trim()
}

async function runContrastTest() {
  if (!testQuestion.value.trim()) return
  isGenerating.value = true
  baseModelAnswer.value = ''
  ragEnhancedAnswer.value = ''
  const question = testQuestion.value.trim()
  const context = buildRagContext()

  const basePrompt = `仅根据通用预训练知识回答，不可引用外部检索材料。\n问题：${question}`
  const ragPrompt = `你将基于给定 Context 回答，若信息不足请明确说明。\nContext:\n${context || '（当前无召回内容）'}\n\n问题：${question}`

  try {
    const [baseAnswer, ragAnswer] = await Promise.all([streamPrompt(basePrompt, 0.75, 0.9), streamPrompt(ragPrompt, 0.55, 0.9)])
    baseModelAnswer.value = baseAnswer || '基础模型未返回有效内容。'
    ragEnhancedAnswer.value = ragAnswer || 'RAG 增强模型未返回有效内容。'
    ElMessage.success('对比实验完成')
  } catch (e) {
    const msg = e instanceof Error ? e.message : '运行对比实验失败'
    ElMessage.error(msg)
    if (!baseModelAnswer.value) baseModelAnswer.value = `请求失败：${msg}`
    if (!ragEnhancedAnswer.value) ragEnhancedAnswer.value = `请求失败：${msg}`
  } finally {
    isGenerating.value = false
  }
}

function onStepChange(step: RagVisualStep) {
  progressStore.setRagVisualStep(step)
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
.lab-guide,
.toolbar-card,
.main-panel,
.compare-panel {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
  padding: 20px 24px;
}
.demo-btn {
  flex-shrink: 0;
}
.lab-guide {
  border-color: #bfdbfe;
  background: #eff6ff;
}
.lab-guide__title {
  font-size: 15px;
  font-weight: 800;
  color: #1e3a8a;
}
.lab-guide__content {
  color: #1e3a8a;
  line-height: 1.7;
  font-size: 13px;
}
.lab-guide__content p {
  margin: 0 0 6px;
}
.lab-guide__content p:last-child {
  margin-bottom: 0;
}
.toolbar-card {
  padding: 16px 20px;
}
.toolbar-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.main-panel {
  padding: 16px;
}
.main-panel.split {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(380px, 1fr);
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 1200px) {
  .main-panel.split {
    grid-template-columns: 1fr;
  }
}
.pane--blueprint {
  min-height: 0;
}
.pane--vector {
  min-height: 520px;
}
.pane--vector :deep(.vector-space-3d) {
  height: 100%;
  min-height: 520px;
}
.compare-panel {
  padding: 18px 20px;
}
.compare-panel__head h3 {
  margin: 0;
  font-size: 16px;
  color: #0f172a;
}
.compare-panel__controls {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: start;
}
@media (max-width: 900px) {
  .compare-panel__controls {
    grid-template-columns: 1fr;
  }
}
.compare-panel__results {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
@media (max-width: 1100px) {
  .compare-panel__results {
    grid-template-columns: 1fr;
  }
}
.result-card {
  border: 1px solid #dbe5f2;
  border-radius: 12px;
  padding: 12px 14px;
  min-height: 180px;
  background: #f8fafc;
}
.result-card h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.result-card p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 13px;
}
.result-card--base {
  border-color: #fecaca;
  background: #fef2f2;
}
.result-card--rag {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
</style>

