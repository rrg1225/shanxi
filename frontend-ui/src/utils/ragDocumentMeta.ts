import type { RagDocumentListItem } from '@/api/modules/ai'

/** 与知识图谱侧栏一致：用状态 + 分块数估算学习进度 */
export function ragDocumentProgress(item: RagDocumentListItem): number {
  const s = (item.status || '').toUpperCase()
  if (['INDEXED', 'READY', 'COMPLETED', 'DONE', 'SUCCESS'].some((x) => s.includes(x))) return 100
  if (['PROCESSING', 'PENDING', 'RUNNING'].some((x) => s.includes(x))) return 45
  if (['FAILED', 'ERROR'].some((x) => s.includes(x))) return 0
  const c = item.chunkCount ?? 0
  return c > 0 ? Math.min(95, 20 + Math.min(c, 50)) : 15
}
