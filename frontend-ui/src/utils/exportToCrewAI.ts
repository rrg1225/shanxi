/**
 * 将竞技场中的智能体节点与连线导出为可运行的 CrewAI（Python）脚本骨架。
 * 依赖拓扑序解析 Task 先后关系；边上 source → target 表示 target 任务依赖 source 任务的输出（context）。
 */

export interface CrewAiExportAgent {
  /** 节点唯一 id（与边的 sourceId/targetId 对应） */
  id: string
  /** CrewAI Agent.role 字段：职能标签 */
  role: string
  /** 可选展示名，写入注释 */
  displayName?: string
  goal: string
  backstory: string
}

export interface CrewAiExportEdge {
  sourceId: string
  targetId: string
}

export interface BuildCrewAiPythonOptions {
  /** 写入文件头注释的场景说明 */
  sceneDescription?: string
  /** 协作主题，写入各 Task 描述 */
  topic?: string
}

function sanitizePythonIdentifier(raw: string): string {
  const s = raw.replace(/[^a-zA-Z0-9_]/g, '_').replace(/_+/g, '_').replace(/^_|_$/g, '')
  const base = s.length > 0 ? s : 'node'
  return /^[0-9]/.test(base) ? `_${base}` : base
}

function escapeTripleDoubleQuoted(s: string): string {
  return s.replace(/\\/g, '\\\\').replace(/"""/g, '\\"\\"\\"')
}

/** 对 agents 全量节点做 Kahn 拓扑排序；未知边端点会被忽略；存在环时按 agents 声明顺序追加剩余节点 */
export function topologicalAgentIds(agents: CrewAiExportAgent[], edges: CrewAiExportEdge[]): string[] {
  const ids = agents.map((a) => a.id)
  const rank = new Map<string, number>()
  ids.forEach((id, i) => rank.set(id, i))
  const idSet = new Set(ids)
  const adj = new Map<string, string[]>()
  const indeg = new Map<string, number>()
  for (const id of ids) {
    adj.set(id, [])
    indeg.set(id, 0)
  }
  for (const e of edges) {
    if (!idSet.has(e.sourceId) || !idSet.has(e.targetId)) continue
    adj.get(e.sourceId)!.push(e.targetId)
    indeg.set(e.targetId, (indeg.get(e.targetId) ?? 0) + 1)
  }
  const q: string[] = ids.filter((id) => (indeg.get(id) ?? 0) === 0)
  q.sort((a, b) => (rank.get(a) ?? 0) - (rank.get(b) ?? 0))
  const order: string[] = []
  while (q.length > 0) {
    const u = q.shift()!
    order.push(u)
    for (const v of adj.get(u) ?? []) {
      const d = (indeg.get(v) ?? 0) - 1
      indeg.set(v, d)
      if (d === 0) {
        q.push(v)
        q.sort((a, b) => (rank.get(a) ?? 0) - (rank.get(b) ?? 0))
      }
    }
  }
  if (order.length < ids.length) {
    const rest = ids.filter((id) => !order.includes(id))
    rest.sort((a, b) => (rank.get(a) ?? 0) - (rank.get(b) ?? 0))
    order.push(...rest)
  }
  return order
}

function predecessorsMap(edges: CrewAiExportEdge[], agentIds: Set<string>): Map<string, string[]> {
  const pred = new Map<string, string[]>()
  for (const e of edges) {
    if (!agentIds.has(e.sourceId) || !agentIds.has(e.targetId)) continue
    if (!pred.has(e.targetId)) pred.set(e.targetId, [])
    pred.get(e.targetId)!.push(e.sourceId)
  }
  return pred
}

/**
 * 生成 CrewAI Python 源码（Agent / Task / Crew / sequential kickoff）。
 */
export function buildCrewAiPythonScript(
  agents: CrewAiExportAgent[],
  edges: CrewAiExportEdge[],
  options?: BuildCrewAiPythonOptions,
): string {
  if (!agents.length) {
    return '# No agents to export.\n'
  }

  const scene = options?.sceneDescription ?? '多智能体协作流程（由 Agent Arena 导出）'
  const topic = options?.topic ?? '请围绕当前主题进行分工协作并完成可交付成果。'
  const idSet = new Set(agents.map((a) => a.id))
  const predMap = predecessorsMap(edges, idSet)
  const topoIds = topologicalAgentIds(agents, edges)

  const agentVarById = new Map<string, string>()
  const taskVarById = new Map<string, string>()
  for (const a of agents) {
    const av = `agent_${sanitizePythonIdentifier(a.id)}`
    const tv = `task_${sanitizePythonIdentifier(a.id)}`
    agentVarById.set(a.id, av)
    taskVarById.set(a.id, tv)
  }

  const lines: string[] = []
  lines.push('#!/usr/bin/env python3')
  lines.push('"""')
  lines.push(`Generated CrewAI script — ${scene}`)
  lines.push('Install: pip install crewai')
  lines.push('Run: python agent_arena_crewai_export.py')
  lines.push('"""')
  lines.push('')
  lines.push('from crewai import Agent, Crew, Process, Task')
  lines.push('')
  lines.push('')
  lines.push('def build_crew():')
  lines.push('    """Construct agents, tasks (with dependency context), and sequential Crew."""')

  for (const a of agents) {
    const av = agentVarById.get(a.id)!
    const roleStr = escapeTripleDoubleQuoted(a.role)
    const goalStr = escapeTripleDoubleQuoted(a.goal)
    const storyStr = escapeTripleDoubleQuoted(a.backstory)
    const comment = a.displayName ? `  # ${a.displayName}` : ''
    lines.push(`    ${av} = Agent(${comment}`)
    lines.push(`        role="""${roleStr}""",`)
    lines.push(`        goal="""${goalStr}""",`)
    lines.push(`        backstory="""${storyStr}""",`)
    lines.push('        verbose=True,')
    lines.push('        allow_delegation=False,')
    lines.push('    )')
    lines.push('')
  }

  const topicEscaped = escapeTripleDoubleQuoted(topic)

  for (const id of topoIds) {
    const a = agents.find((x) => x.id === id)!
    const tv = taskVarById.get(id)!
    const av = agentVarById.get(id)!
    const preds = predMap.get(id) ?? []
    const ctxVars = preds.map((p) => taskVarById.get(p)!).filter(Boolean)
    const descCore = `围绕协作主题完成「${a.role}」职责：在遵守角色目标与背景的前提下输出本环节成果，供下游任务使用。`
    const descStr = escapeTripleDoubleQuoted(`${descCore}\n\n协作主题：${topic}`)
    const expStr = escapeTripleDoubleQuoted(`${a.role} 环节的结构化输出（要点明确、可被子任务引用）。`)

    lines.push(`    ${tv} = Task(`)
    lines.push(`        description="""${descStr}""",`)
    lines.push(`        expected_output="""${expStr}""",`)
    lines.push(`        agent=${av},`)
    if (ctxVars.length > 0) {
      lines.push(`        context=[${ctxVars.join(', ')}],`)
    }
    lines.push('    )')
    lines.push('')
  }

  const agentList = agents.map((a) => agentVarById.get(a.id)!).join(', ')
  const taskList = topoIds.map((id) => taskVarById.get(id)!).join(', ')

  lines.push('    crew = Crew(')
  lines.push(`        agents=[${agentList}],`)
  lines.push(`        tasks=[${taskList}],`)
  lines.push('        process=Process.sequential,')
  lines.push('        verbose=True,')
  lines.push('    )')
  lines.push('    return crew')
  lines.push('')
  lines.push('')
  lines.push('def main() -> None:')
  lines.push(`    _topic = """${topicEscaped}"""`)
  lines.push('    _ = _topic  # 可在 Task 描述中已包含主题；此处保留变量便于你改为 kickoff(inputs=...)')
  lines.push('    crew = build_crew()')
  lines.push('    result = crew.kickoff()')
  lines.push('    print(result)')
  lines.push('')
  lines.push('')
  lines.push("if __name__ == '__main__':")
  lines.push('    main()')
  lines.push('')

  return lines.join('\n')
}

/**
 * 使用 Blob 触发浏览器下载 .py 文件。
 */
export function downloadPythonFile(filename: string, pythonSource: string): void {
  const safeName = filename.endsWith('.py') ? filename : `${filename}.py`
  const blob = new Blob([pythonSource], { type: 'text/x-python;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = safeName
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
