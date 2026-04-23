<template>
  <div class="workbench">
    <header class="page-header">
      <div>
        <h1>实验一：LLM生成原理与解码策略仿真</h1>
        <p>围绕采样参数、概率树与提示词工程，开展生成机制可视化实验。</p>
      </div>
      <el-button class="demo-btn" :icon="VideoPlay" @click="showDemoModal = true">📺 玩法演示</el-button>
    </header>

    <section class="toolbar-card">
      <el-form :inline="true" class="toolbar-form">
        <el-form-item label="Tenant ID">
          <el-input-number v-model="tenantId" :min="1" />
        </el-form-item>
        <el-form-item label="Owner User ID">
          <el-input-number v-model="ownerUserId" :min="1" />
        </el-form-item>
        <el-form-item label="Teacher User ID">
          <el-input-number v-model="teacherUserId" :min="1" />
        </el-form-item>
      </el-form>
    </section>

    <section class="main-panel">
      <el-tabs v-model="labTab" class="lab-tabs">
        <el-tab-pane label="标准炼丹（概率树）" name="standard">
          <PromptAlchemist ref="alchemistRef" :tenant-id="tenantId" :user-id="ownerUserId" />
        </el-tab-pane>
        <el-tab-pane label="解码参数 A/B 对比测试" name="ab">
          <p class="ab-intro">
            左右分屏：左侧为基准采样（A），右侧调整 <strong>temperature / top_p</strong>（B）并发生成；可一键
            <strong>自我评测</strong>（qwen3-max 裁判）并将参数快照存入实验记录表，支持回滚。
          </p>
          <PromptAbLabPanel :user-id="ownerUserId" />
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="showDemoModal" title="实验一 · 玩法演示" width="560px">
      <p>这里将放置 Prompt 入门演示视频，后续可直接嵌入 B 站或本地教学视频。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PromptWorkbench' })

import { onBeforeUnmount, ref, unref, watchEffect } from 'vue'
import type { Ref } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import PromptAlchemist from '@/components/PromptAlchemist.vue'
import PromptAbLabPanel from '@/components/PromptAbLabPanel.vue'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

const PROMPT_LAB_PAGE = '实验一：LLM生成原理与解码策略仿真'

type AlchemistExposed = {
  prompt: Ref<string>
  temperature: Ref<number>
  topP: Ref<number>
}

const learningStore = useGlobalLearningContextStore()
const alchemistRef = ref<AlchemistExposed | null>(null)

const tenantId = ref(1)
const ownerUserId = ref(1001)
const teacherUserId = ref(2001)
const showDemoModal = ref(false)
const labTab = ref<'standard' | 'ab'>('standard')

function pushPromptLabContext() {
  const a = alchemistRef.value
  if (!a) return
  learningStore.setCurrentContext(PROMPT_LAB_PAGE, {
    prompt: unref(a.prompt),
    temperature: unref(a.temperature),
    topP: unref(a.topP),
    tenantId: tenantId.value,
    ownerUserId: ownerUserId.value,
    teacherUserId: teacherUserId.value,
  })
}

watchEffect(() => {
  if (labTab.value !== 'standard') return
  const a = alchemistRef.value
  if (!a) return
  unref(a.prompt)
  unref(a.temperature)
  unref(a.topP)
  tenantId.value
  ownerUserId.value
  teacherUserId.value
  pushPromptLabContext()
})

onBeforeUnmount(() => {
  learningStore.clearCurrentContext()
})
</script>

<style scoped>
.workbench {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.page-header {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
  padding: 20px 24px;
}
.demo-btn {
  flex-shrink: 0;
}
.toolbar-card {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  padding: 16px 20px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
}
.toolbar-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.main-panel {
  background: #fff;
  border: 1px solid #dde6f2;
  border-radius: 18px;
  padding: 16px;
  box-shadow: 0 10px 26px rgb(15 23 42 / 6%);
}

.lab-tabs :deep(.el-tabs__content) {
  padding-top: 12px;
}

.ab-intro {
  margin: 0 0 14px;
  font-size: 13px;
  color: #475569;
  line-height: 1.55;
}
</style>

