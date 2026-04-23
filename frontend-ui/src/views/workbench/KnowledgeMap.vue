<template>
  <div class="knowledge-map-page">
    <div class="knowledge-map-page__stars" aria-hidden="true" />
    <div class="knowledge-map-page__glow" aria-hidden="true" />

    <div class="knowledge-map-page__graph">
      <KnowledgeUniverse
        learning-lab-from="knowledge-universe"
        :mock-data="graphMock"
      />
    </div>

    <button type="button" class="knowledge-map-page__fab" title="返回学习门户" @click="goPortal">
      <el-icon class="knowledge-map-page__fab-icon"><House /></el-icon>
      <span class="knowledge-map-page__fab-text">返回门户</span>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * 知识星空 · 沉浸探索
 * 全屏星野 + KnowledgeUniverse（节点点击由子组件内 el-drawer 展示 Mock 详情）
 */
defineOptions({ name: 'KnowledgeMap' })

import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { House } from '@element-plus/icons-vue'
import KnowledgeUniverse from '@/components/KnowledgeUniverse.vue'
import { KNOWLEDGE_MAP_LINKS, KNOWLEDGE_MAP_NODES } from '@/data/knowledgeMapGraph'

const router = useRouter()

const graphMock = computed(() => ({
  nodes: KNOWLEDGE_MAP_NODES,
  links: KNOWLEDGE_MAP_LINKS,
}))

function goPortal() {
  router.push({ name: 'WorkbenchIndex' })
}
</script>

<style scoped>
.knowledge-map-page {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #03040a;
}

/* 深色星空底：渐变 + 星点层 */
.knowledge-map-page__stars {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-color: #05060c;
  background-image:
    radial-gradient(1.5px 1.5px at 8% 12%, rgba(255, 255, 255, 0.9), transparent),
    radial-gradient(1px 1px at 22% 28%, rgba(255, 255, 255, 0.55), transparent),
    radial-gradient(1.2px 1.2px at 41% 8%, rgba(200, 220, 255, 0.75), transparent),
    radial-gradient(1px 1px at 63% 19%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.4px 1.4px at 78% 34%, rgba(255, 255, 255, 0.85), transparent),
    radial-gradient(1px 1px at 91% 11%, rgba(255, 255, 255, 0.45), transparent),
    radial-gradient(1px 1px at 15% 44%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.2px 1.2px at 33% 52%, rgba(220, 230, 255, 0.7), transparent),
    radial-gradient(1px 1px at 52% 61%, rgba(255, 255, 255, 0.55), transparent),
    radial-gradient(1.3px 1.3px at 71% 48%, rgba(255, 255, 255, 0.8), transparent),
    radial-gradient(1px 1px at 88% 56%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1px 1px at 6% 68%, rgba(255, 255, 255, 0.45), transparent),
    radial-gradient(1.2px 1.2px at 27% 79%, rgba(255, 255, 255, 0.65), transparent),
    radial-gradient(1px 1px at 48% 88%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.4px 1.4px at 66% 72%, rgba(200, 210, 255, 0.75), transparent),
    radial-gradient(1px 1px at 84% 81%, rgba(255, 255, 255, 0.55), transparent),
    radial-gradient(1px 1px at 95% 92%, rgba(255, 255, 255, 0.4), transparent),
    radial-gradient(1.1px 1.1px at 12% 91%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.3px 1.3px at 38% 96%, rgba(255, 255, 255, 0.7), transparent),
    radial-gradient(1px 1px at 56% 38%, rgba(255, 255, 255, 0.35), transparent),
    radial-gradient(1.2px 1.2px at 73% 7%, rgba(255, 255, 255, 0.6), transparent),
    radial-gradient(1px 1px at 19% 61%, rgba(255, 255, 255, 0.45), transparent),
    radial-gradient(1px 1px at 59% 74%, rgba(255, 255, 255, 0.5), transparent),
    radial-gradient(1.4px 1.4px at 44% 22%, rgba(255, 255, 255, 0.65), transparent);
  background-size: 100% 100%;
  pointer-events: none;
}

.knowledge-map-page__glow {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 90% 55% at 50% 110%, rgba(59, 130, 246, 0.12), transparent 52%),
    radial-gradient(ellipse 50% 40% at 15% 20%, rgba(99, 102, 241, 0.14), transparent 45%),
    radial-gradient(ellipse 45% 35% at 85% 25%, rgba(56, 189, 248, 0.1), transparent 42%);
}

.knowledge-map-page__graph {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.knowledge-map-page__graph :deep(.knowledge-universe) {
  width: 100%;
  height: 100% !important;
  min-height: 0 !important;
  border-radius: 0;
  border: none;
  box-shadow: none;
  background: transparent;
}

.knowledge-map-page__graph :deep(.knowledge-universe::before) {
  opacity: 0.35;
}

/* 弱化子组件角标，避免破坏沉浸感 */
.knowledge-map-page__graph :deep(.universe-badge) {
  opacity: 0.85;
  backdrop-filter: blur(8px);
  background: rgba(15, 23, 42, 0.35);
  border-radius: 12px;
  padding: 8px 12px;
  border: 1px solid rgba(99, 102, 241, 0.25);
}

.knowledge-map-page__fab {
  position: absolute;
  z-index: 20;
  left: 20px;
  bottom: 22px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: 1px solid rgba(129, 140, 248, 0.45);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(12px);
  color: #e0e7ff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.35),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset;
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    transform 0.15s ease;
}

.knowledge-map-page__fab:hover {
  background: rgba(30, 41, 59, 0.82);
  border-color: rgba(165, 180, 252, 0.65);
  transform: translateY(-1px);
}

.knowledge-map-page__fab:active {
  transform: translateY(0);
}

.knowledge-map-page__fab-icon {
  font-size: 18px;
}

.knowledge-map-page__fab-text {
  letter-spacing: 0.02em;
}

@media (max-width: 520px) {
  .knowledge-map-page__fab-text {
    display: none;
  }

  .knowledge-map-page__fab {
    padding: 12px;
    border-radius: 50%;
  }
}
</style>
