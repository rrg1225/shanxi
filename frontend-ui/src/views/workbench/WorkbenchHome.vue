<template>
  <div class="portal-home">
    <section class="hero">
      <div class="hero__glow" aria-hidden="true" />
      <div class="hero__inner">
        <p class="hero__kicker">学习门户</p>
        <h1 class="hero__title">你好，继续今天的学习旅程</h1>
        <p class="hero__lead">
          用一套界面串联 <strong>提示词</strong>、<strong>知识库 RAG</strong> 与 <strong>智能体协作</strong>——像用 SaaS
          产品一样，按目标进入模块即可。
        </p>
      </div>
    </section>

    <section class="feature-deck" aria-label="核心学习入口">
      <router-link
        v-for="card in featureCards"
        :key="card.to"
        :to="card.to"
        class="feature-card"
        :class="`feature-card--${card.tone}`"
      >
        <div class="feature-card__icon-wrap">
          <component :is="card.icon" class="feature-card__icon" />
        </div>
        <h2 class="feature-card__title">{{ card.title }}</h2>
        <p class="feature-card__subtitle">{{ card.subtitle }}</p>
        <p class="feature-card__one-liner">{{ card.oneLiner }}</p>
        <span class="feature-card__cta">
          进入模块
          <span class="feature-card__arrow">→</span>
        </span>
      </router-link>
    </section>

    <section class="activity-panel">
      <div class="activity-panel__head">
        <h3 class="activity-panel__title">最近活动</h3>
        <span class="activity-panel__hint">基于你访问过的学习页面</span>
      </div>
      <ul v-if="activities.length" class="activity-list">
        <li v-for="item in activities" :key="item.id" class="activity-row">
          <router-link :to="item.path" class="activity-row__link">
            <span class="activity-row__dot" />
            <span class="activity-row__label">{{ item.label }}</span>
            <span class="activity-row__time">{{ formatTime(item.at) }}</span>
          </router-link>
        </li>
      </ul>
      <p v-else class="activity-empty">暂无访问记录。从上方三个入口开始，记录会自动出现在这里。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'WorkbenchIndex' })

import { computed } from 'vue'
import { MagicStick, Reading, Cpu } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useGlobalLearningContextStore } from '@/stores/globalLearningContext'

const learningStore = useGlobalLearningContextStore()
const { recentActivities } = storeToRefs(learningStore)

const activities = computed(() => recentActivities.value.slice(0, 8))

const featureCards = [
  {
    to: '/workbench/prompt',
    title: '实验一：LLM生成原理与解码策略仿真',
    subtitle: '提示词 · 模板 · 概率树',
    oneLiner: '用结构化咒语驱动模型，边调参边看词汇预测树。',
    icon: MagicStick,
    tone: 'violet',
  },
  {
    to: '/workbench/rag-build',
    title: '实验二：知识注入与幻觉消除虚拟仿真',
    subtitle: '蓝图 · 文档 · 检索链路',
    oneLiner: '从流程蓝图到 3D 星空，把「文档进库—召回—可视化」跑通。',
    icon: Reading,
    tone: 'ocean',
  },
  {
    to: '/workbench/agent-arena',
    title: '实验三：多智能体协同优化教学案例',
    subtitle: '多智能体 · 协作实验',
    oneLiner: '在多角色协作里理解任务拆分、对话编排与结果汇总。',
    icon: Cpu,
    tone: 'amber',
  },
]

function formatTime(ts: number) {
  const d = Date.now() - ts
  if (d < 60000) return '刚刚'
  if (d < 3600000) return `${Math.floor(d / 60000)} 分钟前`
  if (d < 86400000) return `${Math.floor(d / 3600000)} 小时前`
  return `${Math.floor(d / 86400000)} 天前`
}
</script>

<style scoped>
.portal-home {
  max-width: 1100px;
  margin: 0 auto;
  padding-bottom: 32px;
}

.hero {
  position: relative;
  border-radius: 24px;
  padding: 36px 40px 40px;
  margin-bottom: 28px;
  overflow: hidden;
  background: linear-gradient(135deg, #312e81 0%, #4c1d95 42%, #7c3aed 100%);
  color: #e0e7ff;
  box-shadow:
    0 24px 60px rgba(76, 29, 149, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.14);
}

.hero__glow {
  position: absolute;
  width: 420px;
  height: 420px;
  right: -120px;
  top: -160px;
  background: radial-gradient(circle, rgba(251, 191, 36, 0.35) 0%, transparent 65%);
  pointer-events: none;
}

.hero__inner {
  position: relative;
  z-index: 1;
  max-width: 640px;
}

.hero__kicker {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #fde68a;
}

.hero__title {
  margin: 0 0 14px;
  font-size: clamp(26px, 4vw, 34px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.2;
  color: #fff;
}

.hero__lead {
  margin: 0;
  font-size: 16px;
  line-height: 1.65;
  color: rgba(224, 231, 255, 0.92);
}

.hero__lead strong {
  color: #fff;
  font-weight: 700;
}

.feature-deck {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 28px;
}

@media (max-width: 960px) {
  .feature-deck {
    grid-template-columns: 1fr;
  }
}

.feature-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-decoration: none;
  color: inherit;
  padding: 26px 24px 22px;
  border-radius: 20px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 12px 36px rgba(15, 23, 42, 0.08);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
  min-height: 260px;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.12);
  border-color: rgba(99, 102, 241, 0.35);
}

.feature-card--violet {
  background: linear-gradient(165deg, #fff 0%, #f5f3ff 55%, #ede9fe 100%);
}

.feature-card--ocean {
  background: linear-gradient(165deg, #fff 0%, #eff6ff 50%, #e0f2fe 100%);
}

.feature-card--amber {
  background: linear-gradient(165deg, #fff 0%, #fffbeb 50%, #fef3c7 100%);
}

.feature-card__icon-wrap {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}

.feature-card--violet .feature-card__icon-wrap {
  color: #6d28d9;
  background: linear-gradient(135deg, #ede9fe, #ddd6fe);
}

.feature-card--ocean .feature-card__icon-wrap {
  color: #0369a1;
  background: linear-gradient(135deg, #e0f2fe, #bae6fd);
}

.feature-card--amber .feature-card__icon-wrap {
  color: #b45309;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
}

.feature-card__icon {
  width: 26px;
  height: 26px;
}

.feature-card__title {
  margin: 0 0 6px;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.feature-card__subtitle {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #6366f1;
}

.feature-card--ocean .feature-card__subtitle {
  color: #0284c7;
}

.feature-card--amber .feature-card__subtitle {
  color: #d97706;
}

.feature-card__one-liner {
  margin: 0 0 auto;
  padding-bottom: 18px;
  font-size: 14px;
  line-height: 1.55;
  color: #475569;
  flex: 1;
}

.feature-card__cta {
  font-size: 13px;
  font-weight: 700;
  color: #4338ca;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.feature-card--ocean .feature-card__cta {
  color: #0369a1;
}

.feature-card--amber .feature-card__cta {
  color: #b45309;
}

.feature-card__arrow {
  transition: transform 0.2s ease;
}

.feature-card:hover .feature-card__arrow {
  transform: translateX(4px);
}

.activity-panel {
  border-radius: 20px;
  padding: 22px 24px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(226, 232, 240, 0.95);
  box-shadow: 0 10px 32px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(8px);
}

.activity-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
}

.activity-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
}

.activity-panel__hint {
  font-size: 12px;
  color: #94a3b8;
}

.activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.activity-row__link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  text-decoration: none;
  color: #334155;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.18s ease;
}

.activity-row__link:hover {
  background: rgba(241, 245, 249, 0.95);
}

.activity-row__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  flex-shrink: 0;
}

.activity-row__label {
  flex: 1;
  min-width: 0;
}

.activity-row__time {
  font-size: 12px;
  color: #94a3b8;
  flex-shrink: 0;
}

.activity-empty {
  margin: 8px 0 0;
  font-size: 14px;
  color: #94a3b8;
  line-height: 1.5;
  padding: 12px 4px;
}
</style>
