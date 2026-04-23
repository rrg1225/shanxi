/**
 * 知识星空 · 计算机 / 软件工程 分层知识网络 Mock
 * 连线语义：source → target 多为「前置 / 先修」或「强关联」；value 表示关联强度 0–1
 *
 * 节点字段说明：
 * - group: 四大核心领域 id，用于力导向「星系团」聚类
 * - knowledgeLevel: 层级 1=领域枢纽 2=子方向 3=具体技术点（决定默认 symbolSize 权重）
 * - mastery: 掌握度 0–1（决定发光/强调程度，可由学习进度回写）
 */

export type KnowledgeDomainId = 'ai-llm' | 'java-backend' | 'frontend' | 'cs-theory'

export type KnowledgeHierarchyLevel = 1 | 2 | 3

export interface KnowledgeMapNode {
  id: string
  name: string
  subtitle: string
  description: string
  /** 课程难度（抽屉展示），与 knowledgeLevel 独立 */
  level: '入门' | '进阶' | '高阶'
  duration: string
  importance: number
  relatedTopics: string[]
  symbolSize: number
  value: number
  category: string
  materialTags?: string[]
  /** 所属核心领域（星系簇 id） */
  group: KnowledgeDomainId
  /** 知识分层：1 领域枢纽 · 2 子方向 · 3 技术点 */
  knowledgeLevel: KnowledgeHierarchyLevel
  /** 掌握度 0–1，用于渲染发光强度等 */
  mastery: number
}

export interface KnowledgeMapLink {
  source: string
  target: string
  value: number
  note: string
}

export interface PublicMaterialItem {
  id: string
  title: string
  category: string
  docType?: string
  chunkCount?: number
  hint?: string
}

/** 各 group 在 3D 力导向中的目标锚点（星系中心），供前端聚类力使用 */
/** 星系中心间距略大于原布局，减轻四簇挤在中间、簇内重叠的问题 */
export const KNOWLEDGE_GROUP_ANCHORS: Record<KnowledgeDomainId, { x: number; y: number; z: number }> = {
  'ai-llm': { x: -198, y: 40, z: 138 },
  'java-backend': { x: 192, y: 34, z: 145 },
  frontend: { x: -158, y: 46, z: -152 },
  'cs-theory': { x: 172, y: 42, z: -142 },
}

function hash01(id: string): number {
  let h = 2166136261
  for (let i = 0; i < id.length; i += 1) {
    h ^= id.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return (h >>> 0) / 4294967296
}

function slug(s: string): string {
  return s
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9\u4e00-\u9fff-]/gi, '')
    .slice(0, 48)
}

interface SubDirSpec {
  id: string
  name: string
  topics: string[]
}

interface DomainSpec {
  groupId: KnowledgeDomainId
  groupName: string
  hubName: string
  subdirs: SubDirSpec[]
}

const DOMAIN_SPECS: DomainSpec[] = [
  {
    groupId: 'ai-llm',
    groupName: '人工智能与大模型',
    hubName: 'AI 与大模型体系',
    subdirs: [
      {
        id: 'foundation',
        name: '大模型基础',
        topics: [
          'Transformer 架构',
          'Tokenizer 与词表',
          '上下文窗口与位置编码',
          '温度与 Top-P 采样',
          'KV Cache 与推理优化',
          '模型评测与对齐',
        ],
      },
      {
        id: 'rag',
        name: 'RAG 与知识增强',
        topics: [
          '文档切分与 Chunk 策略',
          '向量嵌入与相似度',
          'Milvus / Faiss 检索',
          'LangChain 编排',
          'LlamaIndex 索引',
          '重排序与混合检索',
          'GraphRAG 概览',
        ],
      },
      {
        id: 'agent',
        name: 'Agent 与工具调用',
        topics: [
          'ReAct 推理循环',
          'Function Calling',
          'MCP 协议',
          '多智能体协作',
          '记忆与状态管理',
          '工具安全与沙箱',
        ],
      },
      {
        id: 'local',
        name: '本地与私有化部署',
        topics: [
          'Ollama 本地推理',
          'vLLM 高吞吐服务',
          'LoRA / QLoRA 微调',
          'GGUF 量化与推理',
          'GPU 显存与批处理',
        ],
      },
    ],
  },
  {
    groupId: 'java-backend',
    groupName: 'Java 后端架构',
    hubName: 'Java 后端体系',
    subdirs: [
      {
        id: 'spring',
        name: 'Spring 核心',
        topics: [
          'Spring Boot 自动配置',
          'Spring MVC 与 REST',
          'Spring WebFlux 响应式',
          'Bean 作用域与生命周期',
          '配置与 Profile',
          'Actuator 与可观测',
        ],
      },
      {
        id: 'security',
        name: '安全与认证',
        topics: [
          'Spring Security 过滤器链',
          'JWT 无状态认证',
          'OAuth2 / OIDC',
          'RBAC 权限模型',
          'CSRF 与 CORS',
          '接口限流与熔断',
        ],
      },
      {
        id: 'data',
        name: '数据与持久化',
        topics: [
          'JPA / Hibernate',
          'MyBatis 与动态 SQL',
          '声明式事务',
          'HikariCP 连接池',
          'Redis 缓存模式',
          '分库分表入门',
        ],
      },
      {
        id: 'micro',
        name: '微服务与交付',
        topics: [
          'Spring Cloud Gateway',
          'Nacos 注册配置',
          'OpenFeign 调用',
          'Kafka 异步消息',
          'Docker 镜像与编排',
          'Kubernetes 入门',
        ],
      },
    ],
  },
  {
    groupId: 'frontend',
    groupName: '大前端工程',
    hubName: '大前端体系',
    subdirs: [
      {
        id: 'tooling',
        name: '工程化与工具链',
        topics: [
          'Vite 与 HMR',
          'Webpack 与 Loader',
          'pnpm / Monorepo',
          'ESLint 与 Prettier',
          'TypeScript 严格模式',
          '单元测试与 E2E',
        ],
      },
      {
        id: 'vue',
        name: 'Vue 生态',
        topics: [
          'Vue 3 Composition API',
          'Pinia 状态管理',
          'Vue Router 导航守卫',
          'Teleport 与 Suspense',
          '自定义指令与插件',
        ],
      },
      {
        id: 'react',
        name: 'React 生态',
        topics: [
          'React 18 并发特性',
          'Hooks 与闭包陷阱',
          'Zustand / Redux',
          'React Router v6',
          'Server Components 概念',
        ],
      },
      {
        id: 'viz',
        name: '可视化与多端',
        topics: [
          'ECharts 与大数据量',
          'Three.js 场景图',
          'WebGL 与着色器基础',
          '响应式与移动端适配',
          'PWA 与 Service Worker',
          'WebSocket 实时通信',
        ],
      },
    ],
  },
  {
    groupId: 'cs-theory',
    groupName: '计算机基础理论',
    hubName: '计算机科学基础',
    subdirs: [
      {
        id: 'dsa',
        name: '数据结构与算法',
        topics: [
          '链表与双指针',
          '栈与队列应用',
          '二叉树与遍历',
          '堆与优先队列',
          '图与最短路',
          '排序算法对比',
          '哈希与字符串',
          '动态规划入门',
        ],
      },
      {
        id: 'os',
        name: '操作系统',
        topics: [
          '进程与线程模型',
          '调度与上下文切换',
          '虚拟内存与分页',
          '文件系统与 inode',
          '死锁与同步原语',
        ],
      },
      {
        id: 'net',
        name: '计算机网络',
        topics: [
          'TCP 握手与拥塞控制',
          'HTTP/1.1 与 HTTP/2',
          'HTTPS 与 TLS',
          'DNS 与 CDN',
          'WebSocket 协议',
        ],
      },
      {
        id: 'db',
        name: '数据库与事务',
        topics: [
          '关系模型与 SQL',
          'B+ 树与索引',
          '事务与 ACID',
          '隔离级别与幻读',
          '范式与反范式',
          '查询计划与优化',
        ],
      },
    ],
  },
]

function buildNodesAndLinks(): { nodes: KnowledgeMapNode[]; links: KnowledgeMapLink[] } {
  const nodes: KnowledgeMapNode[] = []
  const links: KnowledgeMapLink[] = []

  const pushNode = (n: KnowledgeMapNode) => {
    nodes.push(n)
  }
  const pushLink = (l: KnowledgeMapLink) => {
    links.push(l)
  }

  for (const domain of DOMAIN_SPECS) {
    const hubId = `${domain.groupId}--hub`
    const hM = 0.55 + hash01(hubId) * 0.4
    pushNode({
      id: hubId,
      name: domain.hubName,
      subtitle: `${domain.groupName} · 领域总览`,
      description: `「${domain.hubName}」汇总 ${domain.groupName} 下的核心学习路径，建议先建立全局地图再深入子方向。`,
      level: '进阶',
      duration: '20–40 h',
      importance: 10,
      relatedTopics: domain.subdirs.map((s) => s.name),
      symbolSize: 58 + Math.round(hM * 8),
      value: 10,
      category: domain.groupName,
      group: domain.groupId,
      knowledgeLevel: 1,
      mastery: Math.round(hM * 100) / 100,
      materialTags: [domain.groupId, '体系'],
    })

    for (const sub of domain.subdirs) {
      const subId = `${domain.groupId}--${sub.id}`
      const sM = 0.45 + hash01(subId) * 0.45
      pushNode({
        id: subId,
        name: sub.name,
        subtitle: `${domain.groupName} · ${sub.name}`,
        description: `「${sub.name}」连接 ${sub.topics.length} 个关键技术点，可按顺序或按项目需要跳跃学习。`,
        level: '进阶',
        duration: '12–24 h',
        importance: 8 + Math.floor(hash01(subId + 'i') * 2),
        relatedTopics: sub.topics.slice(0, 4),
        symbolSize: 46 + Math.round(sM * 10),
        value: 8,
        category: domain.groupName,
        group: domain.groupId,
        knowledgeLevel: 2,
        mastery: Math.round(sM * 100) / 100,
        materialTags: [sub.id, domain.groupId],
      })
      pushLink({
        source: hubId,
        target: subId,
        value: 0.92,
        note: '领域枢纽 → 子方向',
      })

      let prevLeafId: string | null = null
      for (const topic of sub.topics) {
        const leafId = `${domain.groupId}--${sub.id}--${slug(topic)}`
        const tM = 0.2 + hash01(leafId) * 0.75
        const tier3Level: '入门' | '进阶' | '高阶' =
          hash01(leafId + 'lv') < 0.45 ? '入门' : hash01(leafId + 'lv') < 0.82 ? '进阶' : '高阶'
        pushNode({
          id: leafId,
          name: topic,
          subtitle: `${sub.name} · 技术点`,
          description: `「${topic}」属于 ${domain.groupName} / ${sub.name}。建议结合官方文档与小型实验掌握；可与相邻节点组成最小闭环项目。`,
          level: tier3Level,
          duration: `${3 + Math.floor(hash01(leafId + 'd') * 8)}–${6 + Math.floor(hash01(leafId + 'd2') * 10)} h`,
          importance: 5 + Math.floor(tM * 4),
          relatedTopics: [sub.name, domain.groupName],
          symbolSize: 34 + Math.round(tM * 14) + (tier3Level === '高阶' ? 4 : 0),
          value: 5 + Math.round(tM * 3),
          category: domain.groupName,
          group: domain.groupId,
          knowledgeLevel: 3,
          mastery: Math.round(tM * 100) / 100,
          materialTags: [slug(topic), sub.id],
        })
        pushLink({
          source: subId,
          target: leafId,
          value: 0.78 + hash01(leafId + 'e') * 0.15,
          note: '子方向 → 技术点',
        })
        if (prevLeafId) {
          pushLink({
            source: prevLeafId,
            target: leafId,
            value: 0.42 + hash01(prevLeafId + leafId) * 0.25,
            note: '同方向递进',
          })
        }
        prevLeafId = leafId
      }
    }
  }

  const cross: KnowledgeMapLink[] = [
    { source: 'frontend--vue--vue-3-composition-api', target: 'java-backend--spring--spring-mvc-与-rest', value: 0.72, note: '前后端分离 · Vue 调用 REST API' },
    { source: 'frontend--tooling--typescript-严格模式', target: 'java-backend--spring--spring-boot-自动配置', value: 0.55, note: '类型思维 → 后端配置与契约' },
    { source: 'ai-llm--rag--langchain-编排', target: 'ai-llm--local--ollama-本地推理', value: 0.8, note: 'LangChain 编排本地大模型' },
    { source: 'ai-llm--rag--milvus--faiss-检索', target: 'java-backend--data--redis-缓存模式', value: 0.5, note: '向量检索与缓存层协同' },
    { source: 'ai-llm--agent--function-calling', target: 'java-backend--spring--spring-mvc-与-rest', value: 0.58, note: '工具调用对接后端接口' },
    { source: 'cs-theory--net--http11-与-http2', target: 'java-backend--spring--spring-mvc-与-rest', value: 0.68, note: 'HTTP 协议与 Spring MVC' },
    { source: 'cs-theory--net--websocket-协议', target: 'frontend--viz--websocket-实时通信', value: 0.75, note: '协议与前端实时通道' },
    { source: 'cs-theory--db--b-树与索引', target: 'java-backend--data--jpa--hibernate', value: 0.52, note: '索引直觉 → ORM 与持久层' },
    { source: 'java-backend--security--jwt-无状态认证', target: 'frontend--vue--vue-router-导航守卫', value: 0.62, note: 'JWT 与前端路由鉴权' },
    { source: 'java-backend--data--redis-缓存模式', target: 'ai-llm--rag--重排序与混合检索', value: 0.48, note: '缓存与检索链路加速' },
    { source: 'frontend--viz--threejs-场景图', target: 'cs-theory--dsa--图与最短路', value: 0.4, note: '场景图与图论直觉' },
    { source: 'ai-llm--foundation--transformer-架构', target: 'cs-theory--dsa--动态规划入门', value: 0.35, note: '序列模型与 DP 思维（弱关联）' },
    { source: 'java-backend--micro--docker-镜像与编排', target: 'ai-llm--local--vllm-高吞吐服务', value: 0.54, note: '容器化部署推理服务' },
    { source: 'frontend--react--react-18-并发特性', target: 'cs-theory--os--进程与线程模型', value: 0.43, note: '并发 UI 与线程模型对照' },
    { source: 'cs-theory--os--虚拟内存与分页', target: 'java-backend--data--分库分表入门', value: 0.38, note: '内存/存储层次与数据分片' },
  ]

  const nodeIds = new Set(nodes.map((n) => n.id))
  for (const c of cross) {
    if (nodeIds.has(c.source) && nodeIds.has(c.target)) {
      pushLink(c)
    }
  }

  return { nodes, links }
}

const built = buildNodesAndLinks()

export const KNOWLEDGE_MAP_NODES: KnowledgeMapNode[] = built.nodes
export const KNOWLEDGE_MAP_LINKS: KnowledgeMapLink[] = built.links

export function getKnowledgeMapNodeById(id: string | null | undefined): KnowledgeMapNode | null {
  if (!id) return null
  return KNOWLEDGE_MAP_NODES.find((n) => n.id === id) ?? null
}

/** API 不可用时，按节点 id 展示的示例公共资料（节选，避免文件过大） */
export const MOCK_PUBLIC_MATERIALS_BY_NODE: Record<string, PublicMaterialItem[]> = (() => {
  const sample: Record<string, PublicMaterialItem[]> = {}
  const pick = KNOWLEDGE_MAP_NODES.filter((n) => n.knowledgeLevel <= 2).slice(0, 12)
  for (const n of pick) {
    sample[n.id] = [
      {
        id: `${n.id}-pub-1`,
        title: `《${n.name}》导读 · 公共知识库`,
        category: n.category,
        docType: 'TEXT',
        chunkCount: 8 + (n.id.length % 8),
        hint: '图谱节点关联资料',
      },
    ]
  }
  return sample
})()
