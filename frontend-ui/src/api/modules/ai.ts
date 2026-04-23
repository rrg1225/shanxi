import { API_BASE, httpErrorMessage, request } from '@/api/client'

export interface UploadDocumentParams {
  file: File
  tenantId: number
  ownerUserId: number
  teacherUserId?: number
  visibility?: 'PUBLIC' | 'PRIVATE'
  chunkSize?: number
  chunkOverlap?: number
  /** SSE 进度（percent 0–100） */
  onProgress?: (e: UploadProgressEvent) => void
  signal?: AbortSignal
}

export interface UploadProgressEvent {
  percent: number
  phase: string
}

export interface ChunkPreview {
  chunkId: number
  chunkIndex: number
  textPreview: string
  coords: number[]
}

export interface DocumentProcessResponse {
  documentId: number
  chunkCount: number
  embedDim: number
  embeddingModel: string
  chunks: ChunkPreview[]
}

export interface RagSearchRequest {
  query: string
  tenantId: number
  visibilityMode?: 'PUBLIC_ONLY' | 'PRIVATE_ONLY' | 'BOTH'
  ownerUserId?: number
  topK?: number
}

export interface RagSearchHit {
  chunkId: number
  documentId: number
  chunkIndex: number
  text: string
  score: number
  coords: number[]
  visibility?: number
  ownerUserId?: number
  teacherUserId?: number
}

export interface RagSearchResponse {
  query: string
  topK: number
  results: RagSearchHit[]
}

export interface TutorHintRequest {
  module: string
  lastError: string
  recentAction: string
  userLevel: string
  extraContext?: Record<string, unknown>
}

export interface TutorHintResponse {
  tips: string[]
}

export interface AgentArenaRunRequest {
  topic: string
}

function wrapFetchError(e: unknown): Error {
  if (e instanceof TypeError || (e instanceof Error && /fetch|network|Failed to fetch|Load failed/i.test(e.message))) {
    return new Error(
      '无法连接后端（Failed to fetch）。请确认 backend-services 已启动；开发环境建议将 .env 中 VITE_BACKEND_BASE_URL 留空以走 Vite 代理。',
    )
  }
  return e instanceof Error ? e : new Error(String(e))
}

/**
 * 文档上传（SSE）：后端异步处理并向量化，通过 progress 事件推送百分比。
 */
export async function uploadDocument(params: UploadDocumentParams): Promise<DocumentProcessResponse> {
  const form = new FormData()
  form.append('file', params.file)
  form.append('tenantId', String(params.tenantId))
  form.append('ownerUserId', String(params.ownerUserId))
  if (params.teacherUserId !== undefined) form.append('teacherUserId', String(params.teacherUserId))
  form.append('visibility', params.visibility || 'PRIVATE')
  form.append('chunkSize', String(params.chunkSize ?? 800))
  form.append('chunkOverlap', String(params.chunkOverlap ?? 150))

  let response: Response
  try {
    response = await fetch(`${API_BASE}/api/rag/documents/upload`, {
      method: 'POST',
      body: form,
      headers: { Accept: 'text/event-stream' },
      signal: params.signal,
    })
  } catch (e) {
    throw wrapFetchError(e)
  }

  if (!response.ok) {
    const text = await response.text()
    throw new Error(httpErrorMessage(response.status, text))
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('响应不支持流式读取')
  }

  const decoder = new TextDecoder()
  let byteBuf = ''
  let completePayload: DocumentProcessResponse | null = null

  const dispatchSseBlock = (block: string) => {
    let evName = ''
    const dataLines: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) {
        evName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
    const dataStr = dataLines.join('\n').trim()
    if (!dataStr) return
    if (evName === 'progress') {
      try {
        params.onProgress?.(JSON.parse(dataStr) as UploadProgressEvent)
      } catch {
        /* ignore */
      }
      return
    }
    if (evName === 'complete') {
      completePayload = JSON.parse(dataStr) as DocumentProcessResponse
      return
    }
    if (evName === 'error') {
      let msg = dataStr
      try {
        const j = JSON.parse(dataStr) as { message?: string }
        if (j.message) msg = j.message
      } catch {
        /* 非 JSON 则整段作为文案 */
      }
      throw new Error(msg || '上传失败')
    }
  }

  const findEventBoundary = (s: string): { sep: number; len: number } | null => {
    const crlf = s.indexOf('\r\n\r\n')
    const lf = s.indexOf('\n\n')
    if (crlf === -1 && lf === -1) return null
    if (crlf === -1) return { sep: lf, len: 2 }
    if (lf === -1) return { sep: crlf, len: 4 }
    return crlf < lf ? { sep: crlf, len: 4 } : { sep: lf, len: 2 }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (value) {
      byteBuf += decoder.decode(value, { stream: true })
    }
    for (;;) {
      const b = findEventBoundary(byteBuf)
      if (!b) break
      const raw = byteBuf.slice(0, b.sep).trimEnd()
      byteBuf = byteBuf.slice(b.sep + b.len)
      if (raw) {
        dispatchSseBlock(raw)
      }
    }
    if (done) break
  }

  const tail = byteBuf.trimEnd()
  if (tail) {
    dispatchSseBlock(tail)
  }

  if (!completePayload) {
    throw new Error('上传未完成：未收到 complete 事件')
  }
  return completePayload
}

export interface RagDocumentListItem {
  id: number
  tenantId?: number
  ownerUserId?: number
  title?: string
  category?: string
  docType?: string
  chunkCount?: number
  status?: string
  isPublic?: boolean
  kbScope?: string
}

/** 资源广场：按知识节点拉取公共书目 {@code GET /api/v1/rag/books/by-node/{nodeId}} */
export async function fetchBooksByKnowledgeNode(
  nodeId: string,
  tenantId: number,
  options?: { signal?: AbortSignal },
) {
  const q = new URLSearchParams({ tenantId: String(tenantId) })
  return request<RagDocumentListItem[]>(
    `/api/v1/rag/books/by-node/${encodeURIComponent(nodeId)}?${q.toString()}`,
    { signal: options?.signal },
  )
}

export async function listRagDocuments(
  params: {
    tenantId: number
    scope?: 'all' | 'public' | 'private'
    ownerUserId?: number
    kbScope?: string
  },
  options?: { signal?: AbortSignal },
) {
  const sp = new URLSearchParams()
  sp.set('tenantId', String(params.tenantId))
  if (params.scope) sp.set('scope', params.scope)
  if (params.ownerUserId != null) sp.set('ownerUserId', String(params.ownerUserId))
  if (params.kbScope) sp.set('kbScope', params.kbScope)
  return request<RagDocumentListItem[]>(`/api/rag/documents?${sp.toString()}`, {
    signal: options?.signal,
  })
}

export async function ragSearch(payload: RagSearchRequest, options?: { signal?: AbortSignal }) {
  return request<RagSearchResponse>('/api/rag/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: options?.signal,
  } as RequestInit)
}

export async function fetchTutorHints(payload: TutorHintRequest, options?: { signal?: AbortSignal }) {
  return request<TutorHintResponse>('/api/ai/tutor/hint', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: options?.signal,
  } as RequestInit)
}

export async function runAgentArenaDemo(payload: AgentArenaRunRequest) {
  return request<{ code: string; message: string }>('/api/agent-arena/run-demo', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** 资源广场：公共资源库（书籍 / Prompt / RAG 蓝图等） */
export type PublicVaultKind = 'book' | 'prompt' | 'rag_blueprint'

export interface PublicVaultItemDTO {
  id: string
  kind: PublicVaultKind | string
  title: string
  subtitle?: string
  categoryTag?: string
  coverHue?: number
  coverGlyph?: string
  badge?: string
  /** 一键使用时可下发的模板正文或蓝图 JSON（可选） */
  payload?: string
}

/**
 * GET /api/v1/rag/public-vault
 * 联调时由后端返回 {@link PublicVaultItemDTO}[]；当前页面在失败或空数组时回退 Mock。
 */
export async function fetchPublicVault(options?: { signal?: AbortSignal }) {
  return request<PublicVaultItemDTO[]>('/api/v1/rag/public-vault', {
    signal: options?.signal,
  })
}

