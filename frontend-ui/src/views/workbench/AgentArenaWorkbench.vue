<template>
  <div class="workbench">
    <header class="page-header">
      <div>
        <h1>实验三：多智能体协同优化教学案例</h1>
        <p>通过生成、审查与优化协作链，展示软件工程任务的自动化拆解能力。</p>
      </div>
      <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
    </header>

    <section class="arena-panel">
      <AgentArena :ws-url="agentWsUrl" />
    </section>

    <el-dialog v-model="showDemoModal" title="实验三 · 玩法演示" width="560px">
      <p>这里将放置多智能体协作演示视频，后续可嵌入完整实战案例。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'AgentArenaWorkbench' })

import { ref } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import AgentArena from '@/components/AgentArena.vue'

const showDemoModal = ref(false)

/** 与 AgentArena 内逻辑一致：开发环境默认走当前站点的 /ws 代理 */
function resolveAgentArenaWsUrl(): string {
  const fromEnv = import.meta.env.VITE_AGENT_WS_URL
  if (typeof fromEnv === 'string' && fromEnv.trim().length > 0) return fromEnv.trim()
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    const { protocol, hostname, port } = window.location
    const wsProto = protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProto}//${hostname}:${port}/ws/agent-arena`
  }
  return 'ws://127.0.0.1:8082/ws/agent-arena'
}

const agentWsUrl = resolveAgentArenaWsUrl()
</script>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header,
.arena-panel {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
}

.page-header {
  padding: 20px 24px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.page-header h1 {
  margin: 0 0 8px;
}

.page-header p {
  margin: 0;
  color: #64748b;
}

.arena-panel {
  padding: 16px;
}

.demo-btn {
  flex-shrink: 0;
}
</style>
