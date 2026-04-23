export const API_BASE = import.meta.env.VITE_BACKEND_BASE_URL || ''

export function httpErrorMessage(status: number, bodyText: string): string {
  const raw = bodyText.trim()
  if (raw.startsWith('{')) {
    try {
      const j = JSON.parse(raw) as { message?: string; code?: string }
      if (j.message) {
        return j.code ? `${j.message} (${j.code})` : j.message
      }
    } catch {
      /* ignore */
    }
  }
  /** Vite 代理在上游未启动时曾返回空 body + 500；现已尽量改为 503+JSON */
  if (status === 503 || status === 502) {
    return raw || `上游服务不可用（${status}）。请确认 backend-services(8082) 与 ai-gateway(8000) 已启动。`
  }
  if (status === 500 && !raw) {
    return '请求失败（500，无响应体）。多为 Vite 代理无法连接 8082/8000：请先启动 backend-services 与 ai-gateway，并重启 npm run dev。'
  }
  return raw || `Request failed: ${status}`
}

function wrapFetchError(e: unknown): Error {
  if (e instanceof TypeError || (e instanceof Error && /fetch|network|Failed to fetch|Load failed/i.test(e.message))) {
    return new Error(
      '无法连接后端（Failed to fetch）。请确认 backend-services 已启动；开发环境建议将 .env 中 VITE_BACKEND_BASE_URL 留空以走 Vite 代理。',
    )
  }
  return e instanceof Error ? e : new Error(String(e))
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, init)
  } catch (e) {
    throw wrapFetchError(e)
  }
  if (!response.ok) {
    const text = await response.text()
    throw new Error(httpErrorMessage(response.status, text))
  }
  return response.json() as Promise<T>
}

export async function uploadRequest<T>(path: string, formData: FormData): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      body: formData,
    })
  } catch (e) {
    throw wrapFetchError(e)
  }
  if (!response.ok) {
    const text = await response.text()
    throw new Error(httpErrorMessage(response.status, text))
  }
  return response.json() as Promise<T>
}

