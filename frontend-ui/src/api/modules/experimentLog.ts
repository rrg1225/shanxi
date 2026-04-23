import { request } from '@/api/client'

export interface ExperimentOpLogMessage {
  tenantId: number
  userId: number
  opType: string
  opPayload?: Record<string, unknown>
  createdAtMillis?: number
}

export async function postExperimentLog(payload: ExperimentOpLogMessage) {
  return request<{ code: string; message?: string }>('/api/experiment/log', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

