export interface PromptTemplate {
  id: string
  title: string
  icon: string
  description: string
  prompt: string
}

export const promptTemplates: PromptTemplate[] = [
  {
    id: 'essay-polish',
    title: '论文润色专家',
    icon: '📝',
    description: '让你的课程论文更像期刊风格，逻辑更清晰。',
    prompt:
      '你是高校论文润色专家。请在不改变原意的前提下，优化下面内容的学术表达、逻辑连贯性和术语准确性。输出：1) 润色后文本 2) 关键修改说明 3) 可继续优化建议。',
  },
  {
    id: 'debug-helper',
    title: '代码 Debug 助手',
    icon: '🐞',
    description: '快速定位报错根因，并给出可执行修复方案。',
    prompt:
      '你是资深全栈开发者。请基于我提供的报错信息和代码片段，按“问题定位 -> 根因分析 -> 修复步骤 -> 最小可运行示例 -> 预防建议”输出答案，要求步骤清晰、可直接复制执行。',
  },
  {
    id: 'mock-interview',
    title: '面试模拟官',
    icon: '🎤',
    description: '按真实面试节奏提问并即时点评。',
    prompt:
      '你现在是互联网公司面试官，请针对“应届生全栈岗位”发起 5 轮模拟面试。每轮包含：1 个问题、我作答后的评分标准、优秀回答示例、常见失分点。语气专业但不过度打击。',
  },
  {
    id: 'exam-coach',
    title: '考前冲刺教练',
    icon: '🚀',
    description: '把复习任务拆成可执行的日计划。',
    prompt:
      '你是大学生考前冲刺教练。请根据“考试科目、剩余天数、每天可学习时长”制定复习计划。输出包含：每日任务清单、重点优先级、错题复盘机制、最后 48 小时冲刺方案。',
  },
  {
    id: 'presentation-master',
    title: '汇报 PPT 导演',
    icon: '📊',
    description: '把零散信息整理成有说服力的展示结构。',
    prompt:
      '你是课程汇报顾问。请将我的项目资料整理成 10 页以内的 PPT 结构，要求包含：故事线、每页标题、核心要点、可视化建议、演讲词提示。受众是老师和同学，风格简洁有逻辑。',
  },
]
