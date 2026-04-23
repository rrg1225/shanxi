import { request } from '@/api/client'

export interface PromptAbEvalScores {
  A?: { accuracy?: number; conciseness?: number }
  B?: { accuracy?: number; conciseness?: number }
}

export interface PromptAbEvalResult {
  verdict?: string
  scores?: PromptAbEvalScores
  reason?: string
  judgeModel?: string
  mock?: boolean
  error?: string
}

export async function postPromptAbEval(body: {
  userTask: string
  outputA: string
  outputB: string
  paramsA?: Record<string, unknown>
  paramsB?: Record<string, unknown>
}): Promise<PromptAbEvalResult> {
  return request<PromptAbEvalResult>('/api/ai/prompt-ab-eval', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export interface ExperimentSnapshotPayload {
  version?: number
  label?: string
  userPrompt?: string
  temperatureA?: number
  topPA?: number
  temperatureB?: number
  topPB?: number
  outputA?: string
  outputB?: string
  judge?: PromptAbEvalResult | null
  savedAtMillis?: number
}

export interface ExperimentRecordRow {
  id: string | number
  userId?: number
  experimentType?: string
  inputPayloadJson?: string
  status?: string
  gmtCreated?: string
}

export async function saveExperimentSnapshot(userId: number, payload: ExperimentSnapshotPayload, experimentType = 'PROMPT_AB') {
  return request<{ code: string; id: string | number }>('/api/experiment/record/snapshot', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId,
      experimentType,
      payload: { ...payload, savedAtMillis: Date.now() },
    }),
  })
}

export async function listExperimentSnapshots(userId: number, experimentType = 'PROMPT_AB', limit = 20) {
  const q = new URLSearchParams({ userId: String(userId), experimentType, limit: String(limit) })
  return request<ExperimentRecordRow[]>(`/api/experiment/record/list?${q.toString()}`)
}
