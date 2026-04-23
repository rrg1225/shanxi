import type {
  DocumentProcessResponse,
  RagSearchResponse,
  RagSearchHit,
} from '@/api/modules/ai'

export interface VectorChunkMock {
  id: number | string
  x: number
  y: number
  z: number
  label?: string
  cluster?: string | number
}

export interface TokenTreeNodeMock {
  token: string
  probability: number
  children?: TokenTreeNodeMock[]
}

function hashStringToSeed(input: string): number {
  let h = 2166136261
  for (let i = 0; i < input.length; i += 1) {
    h ^= input.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a |= 0
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function pickFrom<T>(arr: T[], rnd: () => number) {
  return arr[Math.floor(rnd() * arr.length)]
}

function generateClusterCoords(opts: {
  count: number
  clusterCount: number
  seed: string
  centerSpread: number
  radius: number
}): VectorChunkMock[] {
  const rnd = mulberry32(hashStringToSeed(opts.seed))
  const clusters: Array<{ cx: number; cy: number; cz: number }> = []

  for (let i = 0; i < opts.clusterCount; i += 1) {
    // 保证每个 cluster 内部距离相对集中，便于 VectorSpace3D 连线
    const cx = (rnd() - 0.5) * opts.centerSpread
    const cy = (rnd() - 0.5) * (opts.centerSpread * 0.35)
    const cz = (rnd() - 0.5) * opts.centerSpread
    clusters.push({ cx, cy, cz })
  }

  const chunks: VectorChunkMock[] = []
  for (let i = 0; i < opts.count; i += 1) {
    const cluster = i % opts.clusterCount
    const { cx, cy, cz } = clusters[cluster]
    const r = opts.radius * Math.pow(rnd(), 0.55)
    const theta = rnd() * Math.PI * 2
    const phi = Math.acos(2 * rnd() - 1)
    const x = cx + r * Math.sin(phi) * Math.cos(theta)
    const y = cy + r * Math.sin(phi) * Math.sin(theta)
    const z = cz + r * Math.cos(phi)

    chunks.push({
      id: i + 10000,
      x,
      y,
      z,
      label: `Chunk #${i}`,
      cluster,
    })
  }
  return chunks
}

export function getMockDocumentProcessResponse(args: {
  tenantId: number
  ownerUserId: number
  chunkCount?: number
  seedKey?: string
}): DocumentProcessResponse {
  const chunkCount = args.chunkCount ?? 40
  const clusterCount = Math.max(4, Math.min(10, Math.floor(chunkCount / 6)))

  const chunks = generateClusterCoords({
    count: chunkCount,
    clusterCount,
    seed: args.seedKey ?? `${args.tenantId}-${args.ownerUserId}-doc`,
    centerSpread: 70,
    radius: 9,
  })

  const mockChunks = chunks.map((c, idx) => ({
    chunkId: Number(c.id),
    chunkIndex: idx,
    textPreview: `Mock 文本块（用于演示）#${idx}：RAG 流程节点/参数配置与可视化映射`,
    coords: [c.x, c.y, c.z],
  }))

  return {
    documentId: 900000000000000000 + args.tenantId * 100000 + args.ownerUserId,
    chunkCount: mockChunks.length,
    embedDim: 1024,
    embeddingModel: 'text-embedding-v3',
    chunks: mockChunks,
  }
}

export function getMockRagSearchResponse(args: {
  query: string
  vectorChunks: VectorChunkMock[]
  topK?: number
}): RagSearchResponse {
  const topK = args.topK ?? 6
  const rnd = mulberry32(hashStringToSeed(args.query))
  const candidates = args.vectorChunks.slice()
  candidates.sort(() => rnd() - 0.5)

  const results: RagSearchHit[] = candidates.slice(0, topK).map((c, idx) => {
    // 分数做一个递减的“像 Top-K”：越靠前越像更相关
    const score = Math.max(0.1, 0.95 - idx * 0.13 + (rnd() - 0.5) * 0.03)
    return {
      chunkId: Number(c.id),
      documentId: 900000000000000000,
      chunkIndex: idx,
      text: `Mock 检索命中：${args.query} -> Chunk #${idx}`,
      score,
      coords: [c.x, c.y, c.z],
      visibility: 0,
      ownerUserId: 1001,
      teacherUserId: 2001,
    }
  })

  return {
    query: args.query,
    topK,
    results,
  }
}

export function getMockTokenProbabilityTree(args: {
  prompt: string
  temperature: number
  top_p: number
}): TokenTreeNodeMock {
  const rnd = mulberry32(hashStringToSeed(`${args.prompt}|${args.temperature}|${args.top_p}`))
  const roots = ['ROOT', 'RAG', '实验', '可视化', '推理', '拆解']

  const nodeToken = pickFrom(roots, rnd)
  const childrenA = [
    { token: '检索', probability: 0.24 + rnd() * 0.08 },
    { token: '增强', probability: 0.14 + rnd() * 0.06 },
    { token: '知识库', probability: 0.10 + rnd() * 0.05 },
  ].slice(0, 3)

  const childrenB = [
    { token: '流程', probability: 0.16 + rnd() * 0.06 },
    { token: '节点', probability: 0.11 + rnd() * 0.05 },
    { token: '设计', probability: 0.09 + rnd() * 0.04 },
  ].slice(0, 3)

  const normalize2 = (arr: TokenTreeNodeMock[]) => {
    const sum = arr.reduce((acc, x) => acc + x.probability, 0)
    if (sum <= 0) return arr
    return arr.map((x) => ({ ...x, probability: x.probability / sum }))
  }

  const a = normalize2(childrenA as unknown as TokenTreeNodeMock[])
  const b = normalize2(childrenB as unknown as TokenTreeNodeMock[])

  return {
    token: nodeToken === 'ROOT' ? 'ROOT' : 'ROOT',
    probability: 1,
    children: [
      {
        token: 'RAG',
        probability: 0.46 + (rnd() - 0.5) * 0.08,
        children: a,
      },
      {
        token: '实验',
        probability: 0.34 + (rnd() - 0.5) * 0.08,
        children: b,
      },
      {
        token: '推理',
        probability: 0.20 + (rnd() - 0.5) * 0.06,
        children: [
          { token: '拆解', probability: 0.11 + rnd() * 0.05 },
          { token: '总结', probability: 0.08 + rnd() * 0.04 },
        ],
      },
    ],
  }
}

