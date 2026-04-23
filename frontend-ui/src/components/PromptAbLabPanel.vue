<template>
  <div class="prompt-ab">
    <el-card class="lab-guide" shadow="never">
      <template #header>
        <div class="lab-guide__title">实验一：大语言模型生成原理与解码策略仿真</div>
      </template>
      <div class="lab-guide__content">
        <p><strong>【实验目的】</strong>探究 Temperature 与 Top-P 参数对模型生成概率分布及文本多样性的影响。</p>
        <p>
          <strong>【实验原理】</strong
          >大模型是基于概率的下一个词预测器。温度控制概率分布的平滑度，Top-P 截断低概率词汇。
        </p>
        <p>
          <strong>【实验操作】</strong
          >调节下方左右两侧的参数滑块，观察在相同提示词下，确定性输出与发散性输出的差异。
        </p>
      </div>
    </el-card>

    <div class="prompt-ab__shared">
      <div class="field-label">用户 Prompt（A/B 共用）</div>
      <el-input v-model="userPrompt" type="textarea" :rows="5" placeholder="输入要对比的指令或模板…" />

      <div class="field-label row-gap">实验备注（保存快照时使用）</div>
      <el-input v-model="snapshotLabel" placeholder="例如：高温度更发散 / 调 top_p 对比" clearable />
    </div>

    <div class="prompt-ab__split">
      <section class="pane pane--a">
        <header class="pane-head">
          <h3>方案 A（基准采样）</h3>
          <span class="pane-hint">左侧参数固定为对照组</span>
        </header>
        <div class="pane-controls">
          <div class="ctl">
            <span>Temperature</span>
            <el-slider v-model="temperatureA" :min="0" :max="2" :step="0.1" show-input />
          </div>
          <div class="ctl">
            <span>Top-P</span>
            <el-slider v-model="topPA" :min="0" :max="1" :step="0.1" show-input />
          </div>
        </div>
        <div class="out-label">模型输出 A</div>
        <el-input v-model="outputA" type="textarea" :rows="12" readonly placeholder="点击「生成 A/B」后显示" class="out-area" />
      </section>

      <section class="pane pane--b">
        <header class="pane-head">
          <h3>方案 B（对比采样）</h3>
          <span class="pane-hint">调整 top_p / temperature 与 A 对比</span>
        </header>
        <div class="pane-controls">
          <div class="ctl">
            <span>Temperature</span>
            <el-slider v-model="temperatureB" :min="0" :max="2" :step="0.1" show-input />
          </div>
          <div class="ctl">
            <span>Top-P</span>
            <el-slider v-model="topPB" :min="0" :max="1" :step="0.1" show-input />
          </div>
        </div>
        <div class="out-label">模型输出 B</div>
        <el-input v-model="outputB" type="textarea" :rows="12" readonly placeholder="点击「生成 A/B」后显示" class="out-area" />
      </section>
    </div>

    <div class="prompt-ab__actions">
      <el-button type="primary" :loading="runningAb" :disabled="!userPrompt.trim()" @click="runBoth">
        并发生成 A / B
      </el-button>
      <el-button type="success" :loading="runningJudge" :disabled="!canJudge" @click="runJudge"> 自我评测（裁判模型） </el-button>
      <el-button :loading="saving" :disabled="!userPrompt.trim()" @click="saveSnapshot"> 保存本组实验 </el-button>
      <el-button @click="loadSnapshots" :loading="loadingList">刷新快照列表</el-button>
    </div>

    <div v-if="judgeResult" class="judge-card">
      <div class="judge-title">
        评测结果
        <el-tag v-if="judgeResult.mock" size="small" type="info">演示/解析回退</el-tag>
        <el-tag v-else size="small" type="success">{{ judgeResult.judgeModel || 'qwen3-max' }}</el-tag>
      </div>
      <p class="judge-verdict">
        倾向：<strong>{{ judgeResult.verdict || '—' }}</strong>
      </p>
      <div v-if="judgeResult.scores" class="judge-scores">
        <div>
          <span>A 准确性 {{ judgeResult.scores.A?.accuracy ?? '—' }}</span>
          <span>简洁度 {{ judgeResult.scores.A?.conciseness ?? '—' }}</span>
        </div>
        <div>
          <span>B 准确性 {{ judgeResult.scores.B?.accuracy ?? '—' }}</span>
          <span>简洁度 {{ judgeResult.scores.B?.conciseness ?? '—' }}</span>
        </div>
      </div>
      <p class="judge-reason">{{ judgeResult.reason }}</p>
    </div>

    <div class="snapshots">
      <div class="snapshots__title">已保存快照（点击「回滚」恢复参数与输出）</div>
      <el-empty v-if="!snapshots.length && !loadingList" description="暂无记录" />
      <ul v-else class="snapshots__list">
        <li v-for="row in snapshots" :key="String(row.id)" class="snap-row">
          <div class="snap-meta">
            <span class="snap-id">#{{ row.id }}</span>
            <span class="snap-time">{{ row.gmtCreated || '' }}</span>
          </div>
          <div class="snap-preview">{{ snapshotPreview(row) }}</div>
          <el-button size="small" type="primary" link @click="rollback(row)">回滚到该配置</el-button>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listExperimentSnapshots,
  postPromptAbEval,
  saveExperimentSnapshot,
  type ExperimentRecordRow,
  type ExperimentSnapshotPayload,
  type PromptAbEvalResult,
} from '@/api/modules/promptAbExperiment'

const props = defineProps<{
  userId: number
}>()

const userPrompt = ref('')
const snapshotLabel = ref('')

const temperatureA = ref(0.7)
const temperatureB = ref(0.7)
const topPA = ref(0.9)
const topPB = ref(0.9)

const outputA = ref('')
const outputB = ref('')

const runningAb = ref(false)
const runningJudge = ref(false)
const saving = ref(false)
const loadingList = ref(false)

const judgeResult = ref<PromptAbEvalResult | null>(null)

const snapshots = ref<ExperimentRecordRow[]>([])

const canJudge = computed(
  () => !!userPrompt.value.trim() && (!!outputA.value.trim() || !!outputB.value.trim()),
)

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

async function streamOne(side: 'A' | 'B'): Promise<string> {
  const temperature = side === 'A' ? temperatureA.value : temperatureB.value
  const top_p = side === 'A' ? topPA.value : topPB.value
  const res = await fetch('/api/ai/prompt-test', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({
      prompt: userPrompt.value,
      side,
      temperature,
      top_p,
      temperatureA: temperatureA.value,
      temperatureB: temperatureB.value,
      topPA: topPA.value,
      topPB: topPB.value,
    }),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(text || `HTTP ${res.status}`)
  }
  let acc = ''
  await consumeSseFull(res, (t) => {
    acc += t
  })
  return acc
}

async function runBoth() {
  if (!userPrompt.value.trim()) return
  runningAb.value = true
  judgeResult.value = null
  outputA.value = ''
  outputB.value = ''
  try {
    const [a, b] = await Promise.all([streamOne('A'), streamOne('B')])
    outputA.value = a
    outputB.value = b
    ElMessage.success('A / B 生成完成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '生成失败')
  } finally {
    runningAb.value = false
  }
}

async function runJudge() {
  if (!canJudge.value) return
  runningJudge.value = true
  try {
    judgeResult.value = await postPromptAbEval({
      userTask: userPrompt.value,
      outputA: outputA.value,
      outputB: outputB.value,
      paramsA: { temperature: temperatureA.value, topP: topPA.value },
      paramsB: { temperature: temperatureB.value, topP: topPB.value },
    })
    ElMessage.success('评测完成')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '评测失败')
  } finally {
    runningJudge.value = false
  }
}

async function saveSnapshot() {
  saving.value = true
  try {
    const payload: ExperimentSnapshotPayload = {
      version: 1,
      label: snapshotLabel.value.trim() || undefined,
      userPrompt: userPrompt.value,
      temperatureA: temperatureA.value,
      topPA: topPA.value,
      temperatureB: temperatureB.value,
      topPB: topPB.value,
      outputA: outputA.value,
      outputB: outputB.value,
      judge: judgeResult.value,
    }
    const r = await saveExperimentSnapshot(props.userId, payload)
    ElMessage.success(`已保存快照 #${r.id}`)
    await loadSnapshots()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function loadSnapshots() {
  loadingList.value = true
  try {
    snapshots.value = await listExperimentSnapshots(props.userId, 'PROMPT_AB', 25)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载列表失败')
  } finally {
    loadingList.value = false
  }
}

function parsePayload(row: ExperimentRecordRow): ExperimentSnapshotPayload | null {
  const raw = row.inputPayloadJson
  if (!raw?.trim()) return null
  try {
    return JSON.parse(raw) as ExperimentSnapshotPayload
  } catch {
    return null
  }
}

function snapshotPreview(row: ExperimentRecordRow): string {
  const p = parsePayload(row)
  if (!p) return row.inputPayloadJson?.slice(0, 80) ?? '—'
  const label = p.label ? `「${p.label}」 ` : ''
  const prev = (p.userPrompt || '').slice(0, 56)
  return `${label}${prev}${(p.userPrompt?.length || 0) > 56 ? '…' : ''}`
}

function rollback(row: ExperimentRecordRow) {
  const p = parsePayload(row)
  if (!p) {
    ElMessage.warning('无法解析快照内容')
    return
  }
  userPrompt.value = p.userPrompt ?? ''
  if (typeof p.temperatureA === 'number') temperatureA.value = p.temperatureA
  if (typeof p.topPA === 'number') topPA.value = p.topPA
  if (typeof p.temperatureB === 'number') temperatureB.value = p.temperatureB
  if (typeof p.topPB === 'number') topPB.value = p.topPB
  outputA.value = p.outputA ?? ''
  outputB.value = p.outputB ?? ''
  judgeResult.value = p.judge ?? null
  snapshotLabel.value = p.label ?? ''
  ElMessage.success('已回滚到该快照（可继续编辑或重新生成）')
}

onMounted(() => {
  void loadSnapshots()
})
</script>

<style scoped>
.prompt-ab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lab-guide {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
}

.lab-guide__title {
  font-size: 15px;
  font-weight: 800;
  color: #1e3a8a;
}

.lab-guide__content {
  font-size: 13px;
  line-height: 1.7;
  color: #1e3a8a;
}

.lab-guide__content p {
  margin: 0 0 6px;
}

.lab-guide__content p:last-child {
  margin-bottom: 0;
}

.prompt-ab__shared {
  padding: 12px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.field-label {
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 6px;
}

.row-gap {
  margin-top: 12px;
}

.prompt-ab__split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 960px) {
  .prompt-ab__split {
    grid-template-columns: 1fr;
  }
}

.pane {
  border: 1px solid #dbe5f2;
  border-radius: 14px;
  padding: 14px;
  background: #fff;
}

.pane--a {
  border-top: 3px solid #6366f1;
}

.pane--b {
  border-top: 3px solid #0ea5e9;
}

.pane-head h3 {
  margin: 0;
  font-size: 15px;
}

.pane-hint {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}

.pane-controls {
  margin: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ctl span {
  display: block;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.out-label {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 6px;
}

.out-area :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 12px;
}

.prompt-ab__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.judge-card {
  border: 1px solid #bbf7d0;
  border-radius: 12px;
  padding: 12px 14px;
  background: #f0fdf4;
}

.judge-title {
  font-weight: 800;
  color: #14532d;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.judge-verdict {
  margin: 0 0 8px;
  font-size: 14px;
}

.judge-scores {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #166534;
  margin-bottom: 8px;
}

.judge-scores > div {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.judge-reason {
  margin: 0;
  font-size: 13px;
  color: #14532d;
  line-height: 1.5;
}

.snapshots {
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
  padding: 12px;
  background: #fff;
}

.snapshots__title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 10px;
}

.snapshots__list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.snap-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
}

.snap-row:last-child {
  border-bottom: none;
}

.snap-meta {
  font-size: 11px;
  color: #64748b;
  min-width: 140px;
}

.snap-id {
  font-weight: 800;
  color: #4338ca;
  margin-right: 8px;
}

.snap-preview {
  flex: 1;
  min-width: 160px;
  font-size: 12px;
  color: #0f172a;
}
</style>
