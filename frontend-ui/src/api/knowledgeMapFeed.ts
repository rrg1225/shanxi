import { fetchTutorHints, listRagDocuments, type RagDocumentListItem } from '@/api/modules/ai'
import type { KnowledgeMapNode } from '@/data/knowledgeMapGraph'
import { ragDocumentProgress } from '@/utils/ragDocumentMeta'

export interface KnowledgeFeedBook {
  id: string
  title: string
  coverUrl: string
  progress: number
}

export interface KnowledgeFeedPayload {
  books: KnowledgeFeedBook[]
  outline: string[]
  /** 为 true 时 outline 为节点内置摘要，非大模型生成 */
  tutorGatewayUnavailable?: boolean
}

/** 与 TutorServiceImpl 降级文案对齐：勿把运维说明当作「知识点大纲」展示 */
function isTutorOfflineFallbackTips(tips: string[]): boolean {
  const first = (tips[0] || '').trim()
  return first.startsWith('当前无法连接 AI 推理服务')
}

function buildLocalOutlineFromNode(node: KnowledgeMapNode): string[] {
  const lines: string[] = [
    `【目标】${node.subtitle}`,
    `【说明】${node.description}`,
    `【难度 / 学时】${node.level} · ${node.duration}`,
    `【关联】${node.relatedTopics.join('、')}`,
  ]
  return lines.map((s) => s.trim()).filter(Boolean)
}

/** 按节点 category / materialTags 与公共文档标题、分类做弱匹配 */
export function filterPublicDocumentsForNode(
  node: KnowledgeMapNode,
  docs: RagDocumentListItem[],
): RagDocumentListItem[] {
  const tags = (node.materialTags || [])
    .map((t) => t.toLowerCase().trim())
    .filter((t) => t && t !== 'general')
  const catHint = (node.category || '').toLowerCase().trim()

  const scored = docs.map((d) => {
    const cat = (d.category || '').toLowerCase()
    const title = (d.title || '').toLowerCase()
    let score = 0
    if (catHint) {
      if (cat.includes(catHint) || catHint.includes(cat)) score += 4
      if (title.includes(catHint)) score += 3
    }
    for (const tag of tags) {
      if (tag.length >= 2 && (cat.includes(tag) || title.includes(tag))) score += 3
    }
    return { d, score }
  })

  const filtered = scored.filter((x) => x.score > 0).sort((a, b) => b.score - a.score)
  if (filtered.length > 0) {
    return filtered.map((x) => x.d).slice(0, 12)
  }

  /** 无标签命中时：用分类名做一次宽松包含（避免列表全空） */
  if (catHint) {
    const loose = docs.filter((d) => {
      const c = (d.category || '').toLowerCase()
      const t = (d.title || '').toLowerCase()
      return c.includes(catHint.slice(0, 2)) || t.includes(catHint.slice(0, 2))
    })
    if (loose.length) return loose.slice(0, 8)
  }

  return []
}

function toFeedBooks(docs: RagDocumentListItem[]): KnowledgeFeedBook[] {
  return docs.map((d) => ({
    id: String(d.id),
    title: d.title?.trim() || `文档 #${d.id}`,
    coverUrl: `https://picsum.photos/seed/rag-doc-${d.id}/140/190`,
    progress: ragDocumentProgress(d),
  }))
}

export async function fetchKnowledgeNodeFeed(params: {
  tenantId: number
  node: KnowledgeMapNode
  userLevel: string
  signal?: AbortSignal
}): Promise<KnowledgeFeedPayload> {
  const { tenantId, node, userLevel, signal } = params

  const [docs, hints] = await Promise.all([
    listRagDocuments({ tenantId, scope: 'public' }, { signal }),
    fetchTutorHints(
      {
        module: '知识图谱 · 节点大纲',
        lastError: '',
        recentAction: `知识点：「${node.name}」（${node.subtitle}）。请输出 5-8 条结构化学习大纲要点，每条独立一行，聚焦核心概念、先修关系与实践路径；避免空话。`,
        userLevel,
        extraContext: {
          knowledgeNodeId: node.id,
          description: node.description,
          relatedTopics: node.relatedTopics,
          level: node.level,
          category: node.category,
        },
      },
      { signal },
    ),
  ])

  const matched = filterPublicDocumentsForNode(node, docs || [])
  const books = toFeedBooks(matched)
  const rawTips = (hints?.tips || []).map((t) => t.trim()).filter(Boolean)

  if (isTutorOfflineFallbackTips(rawTips)) {
    return {
      books,
      outline: buildLocalOutlineFromNode(node),
      tutorGatewayUnavailable: true,
    }
  }

  return { books, outline: rawTips, tutorGatewayUnavailable: false }
}
