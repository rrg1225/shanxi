export interface PromptGalleryItem {
  id: string
  title: string
  content: string
  tags: string[]
  useCount: number
  /** 最近使用时间（毫秒时间戳） */
  lastUsedTime: number
  /** 置顶：在列表中优先展示 */
  pinned?: boolean
}

/** 大学生场景 · 爆款咒语库初始数据（可在页面内更新 useCount / lastUsedTime） */
export function createInitialPromptGallery(): PromptGalleryItem[] {
  const now = Date.now()
  const day = 86400000

  return [
    {
      id: 'c4-contest-guide',
      title: '计算机设计大赛 · 选题与作品亮点包装',
      content: `你是「中国大学生计算机设计大赛」指导教练。我的作品方向是：【在此填写主题，如：智慧校园 / 医疗健康 / 传统文化数字化】。
请按以下结构输出（语言简洁、可照抄到申报书）：
1) 选题价值：问题背景、用户痛点、创新点一句话
2) 技术路线：架构图式文字说明（前端/后端/数据/部署）
3) 亮点提炼：3 条可答辩的「评委友好」卖点
4) 风险与对策：2 个常见质疑 + 应对话术
5) 下一步：7 天里程碑任务清单（每天 3 条以内）`,
      tags: ['竞赛', '编程', '学习'],
      useCount: 428,
      lastUsedTime: now - 2 * 3600000,
      pinned: true,
    },
    {
      id: 'mcm-data-analysis',
      title: '数学建模 · 数据分析与假设合理性',
      content: `你是数学建模国赛/省赛教练。题目摘要如下：【粘贴题目或你自己的理解】。
请协助我完成：
1) 数据预处理建议（缺失值、异常值、无量纲化）
2) 可尝试的模型清单（每类模型写清适用条件与优缺点）
3) 假设与符号说明模板（可直接放进论文）
4) 敏感性分析思路（改哪些参数、预期现象）
5) 若数据不足，给出可接受的简化假设及在论文中如何诚实表述`,
      tags: ['数学', '学习', '竞赛'],
      useCount: 391,
      lastUsedTime: now - 26 * 3600000,
      pinned: true,
    },
    {
      id: 'kaoyan-english-sentence',
      title: '考研英语 · 长难句拆解与翻译',
      content: `你是考研英语阅读教练。请对下面句子做「考场可用」的拆解（不要堆砌术语）：
【粘贴长难句】

输出格式：
1) 主干：谁 + 做了什么 / 是什么
2) 修饰关系：用箭头或缩进标出定语/状语/从句所修饰的对象
3) 生词与熟词僻义（若有）
4) 直译 + 通顺译文
5) 同类型结构迁移：再造 1 个相似句让我练习`,
      tags: ['英语', '学习', '考研'],
      useCount: 512,
      lastUsedTime: now - 45 * 60000,
    },
    {
      id: 'spring-boot-error',
      title: 'Spring Boot · 报错日志诊断与修复路径',
      content: `你是资深 Java / Spring Boot 工程师。请根据我提供的报错信息定位问题并给出可执行修复步骤。

【粘贴完整报错栈或关键几行 + 相关配置/代码片段 + Spring Boot 版本】

输出要求：
1) 一句话结论：最可能的根因
2) 分步排查：从「最常见」到「较少见」，每步说明如何验证
3) 修复方案：给出修改后的代码/配置示例（标注文件路径习惯）
4) 如何避免再次发生（最佳实践 2–3 条）`,
      tags: ['编程', 'Java', '排错'],
      useCount: 603,
      lastUsedTime: now - 15 * 60000,
    },
    {
      id: 'intern-resume-star',
      title: '实习/秋招 · 项目经历 STAR 改写',
      content: `你是互联网校招简历教练。请将我的项目经历改写成 2–3 条高信息密度的 STAR  bullet（中文），突出量化结果与技术难点。

【粘贴原始经历：项目背景、技术栈、你负责的部分、成果（可模糊）】

要求：
1) 每条不超过 3 行，动词开头，尽量含数字或百分比
2) 指出可补充的「可量化缺口」并给我追问清单
3) 给一版「保守真实」和一版「在可核实范围内略强化」的对比（标注差异点）`,
      tags: ['求职', '学习'],
      useCount: 356,
      lastUsedTime: now - 3 * day,
    },
    {
      id: 'ds-algo-interview',
      title: '数据结构 · 算法题思路与复杂度说明',
      content: `你是算法助教。题目如下：【粘贴 LeetCode/笔试题描述或你自己的概括】

请输出：
1) 思路讲解：像给同学讲题一样，从朴素想法到优化
2) 推荐算法与时空复杂度
3) 边界情况清单
4) 若用【指定语言，如 Java/C++】实现，给出核心代码骨架（带注释）
5) 同类题推荐（2 道，附题意关键词）`,
      tags: ['编程', '学习'],
      useCount: 287,
      lastUsedTime: now - 5 * day,
    },
    {
      id: 'course-report-outline',
      title: '课程大作业 · 报告大纲与学术表达',
      content: `你是高校课程助教。课程：【填写课程名】，作业要求：【粘贴要求或摘要】。

请生成：
1) 报告目录（多级标题，符合大多数高校格式）
2) 每一节应写什么的提示（要点列表即可）
3) 可引用的「中立」学术句式模板 5 条（避免空洞）
4) 学术诚信提醒：哪些属于合理引用、哪些需要改写/标注`,
      tags: ['学习', '写作'],
      useCount: 198,
      lastUsedTime: now - 12 * day,
    },
    {
      id: 'paper-reading-notes',
      title: '文献阅读 · 论文笔记与组会汇报稿',
      content: `你是研究生文献阅读助手。论文信息：【标题 + 领域 + 你关心的点】；若可粘贴摘要更好。

请整理：
1) 一句话贡献 + 与相关工作的差异
2) 方法框架图式描述（输入→模块→输出）
3) 实验结论：作者声称证明了什么、你存疑的点
4) 可追问导师的 3 个问题
5) 3 分钟组会口头汇报稿（口语化小抄）`,
      tags: ['学习', '考研'],
      useCount: 244,
      lastUsedTime: now - 8 * day,
    },
    {
      id: 'sql-homework-debug',
      title: '数据库课程 · SQL 作业排错与优化',
      content: `你是数据库课程助教。表结构：【粘贴建表语句或文字描述】；我的 SQL：【粘贴】；报错或结果不对：【描述】。

请：
1) 指出语法/逻辑错误点（标出行号若可）
2) 给出修正后的 SQL
3) 若数据量大，说明可加的索引或改写思路（课程作业可接受范围内）
4) 用 2 个自测用例帮我验证（含期望结果）`,
      tags: ['编程', '学习', '排错'],
      useCount: 175,
      lastUsedTime: now - 20 * day,
    },
  ]
}

/** 快速筛选：与 UI 上 Tag 按钮对应的标签关键字 */
export const GALLERY_FILTER_KEYS = ['全部', '编程', '学习', '求职'] as const
export type GalleryFilterKey = (typeof GALLERY_FILTER_KEYS)[number]
