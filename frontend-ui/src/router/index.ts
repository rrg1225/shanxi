import { createRouter, createWebHistory } from 'vue-router'

const AiWorkbench = () => import('@/views/AiWorkbench.vue')
const WorkbenchHome = () => import('@/views/workbench/WorkbenchHome.vue')
const PromptWorkbench = () => import('@/views/workbench/PromptWorkbench.vue')
const RagBuildWorkbench = () => import('@/views/workbench/RagBuildWorkbench.vue')
const RagVisualWorkbench = () => import('@/views/workbench/RagVisualWorkbench.vue')
const AgentArenaWorkbench = () => import('@/views/workbench/AgentArenaWorkbench.vue')
const LearningLab = () => import('@/views/workbench/LearningLab.vue')
const Marketplace = () => import('@/views/workbench/Marketplace.vue')
const KnowledgeStarrySky = () => import('@/views/workbench/KnowledgeStarrySky.vue')
const KnowledgeMap = () => import('@/views/workbench/KnowledgeMap.vue')
const UserProfile = () => import('@/views/workbench/UserProfile.vue')
const AuthPortal = () => import('@/views/AuthPortal.vue')

declare module 'vue-router' {
  interface RouteMeta {
    /** 顶栏主标题（专家模式） */
    title?: string
    /** 顶栏副标题 */
    subtitle?: string
    /** 沉浸式学习页：隐藏侧栏、顶栏与全局悬浮导师，由子页自管布局 */
    immersiveLab?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/workbench/index',
    },
    {
      path: '/auth',
      name: 'AuthPortal',
      component: AuthPortal,
      meta: {
        title: '登录 / 注册',
        subtitle: '欢迎使用智能学习工作台',
      },
    },
    {
      path: '/login',
      redirect: '/auth',
    },
    {
      path: '/register',
      redirect: '/auth',
    },
    {
      path: '/workbench',
      component: AiWorkbench,
      children: [
        {
          path: '',
          redirect: '/workbench/index',
        },
        {
          path: 'home',
          redirect: '/workbench/index',
        },
        {
          path: 'index',
          name: 'WorkbenchIndex',
          component: WorkbenchHome,
          meta: {
            title: '学习门户',
            subtitle: '选择学习路径：提示词、知识库检索或多智能体实验。',
          },
        },
        {
          path: 'profile',
          name: 'UserProfile',
          component: UserProfile,
          meta: {
            title: '个人学习中心',
            subtitle: '能力雷达、学习节奏与成就徽章一览。',
          },
        },
        {
          path: 'marketplace',
          name: 'ResourceMarketplace',
          component: Marketplace,
          meta: {
            title: '资源广场',
            subtitle: '专业书籍、Prompt 模板与 RAG 蓝图，一键接入工作台。',
          },
        },
        {
          path: 'prompt',
          name: 'PromptWorkbench',
          component: PromptWorkbench,
          meta: {
            title: '实验一：LLM生成原理与解码策略仿真',
            subtitle: '爆款咒语库、Prompt 调试与词汇预测概率树。',
          },
        },
        {
          path: 'prompt-workbench',
          redirect: '/workbench/prompt',
        },
        {
          path: 'rag-build',
          name: 'RagBuildWorkbench',
          component: RagBuildWorkbench,
          meta: {
            title: '实验二：知识注入与幻觉消除虚拟仿真',
            subtitle: '拖拽节点连线，搭建 RAG 流程并与 3D 阶段联动。',
          },
        },
        {
          path: 'rag-build-workbench',
          redirect: '/workbench/rag-build',
        },
        {
          path: 'rag-visual',
          name: 'RagVisualWorkbench',
          component: RagVisualWorkbench,
          meta: {
            title: '实验二：知识注入与幻觉消除虚拟仿真',
            subtitle: '文档处理、向量检索与 3D 知识图谱/星空探索。',
          },
        },
        {
          path: 'rag-visual-workbench',
          redirect: '/workbench/rag-visual',
        },
        {
          path: 'knowledge-universe',
          name: 'KnowledgeMap',
          component: KnowledgeMap,
          meta: {
            title: '前置学习：AI 核心原理知识图谱',
            subtitle: '全屏沉浸：学习路径拓扑与节点探索。',
            immersiveLab: true,
          },
        },
        {
          path: 'knowledge-map',
          name: 'KnowledgeStarrySky',
          component: KnowledgeStarrySky,
          meta: {
            title: '前置学习：AI 核心原理知识图谱',
            subtitle: '学习路径拓扑全屏探索；参考书目请至资源广场。',
          },
        },
        {
          path: 'learning-resources',
          redirect: '/workbench/marketplace',
        },
        {
          path: 'agent-arena',
          name: 'AgentArenaWorkbench',
          component: AgentArenaWorkbench,
          meta: {
            title: '实验三：多智能体协同优化教学案例',
            subtitle: '多智能体协作与实验玩法。',
          },
        },
        {
          path: 'agent-arena-workbench',
          redirect: '/workbench/agent-arena',
        },
        {
          path: 'learning-lab/:nodeId',
          name: 'LearningLab',
          component: LearningLab,
          meta: {
            title: '沉浸式学习空间',
            subtitle: '节点精读与 AI 陪练',
            immersiveLab: true,
          },
        },
      ],
    },
  ],
})

export default router
