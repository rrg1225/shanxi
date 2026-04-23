<template>

  <div
    class="workbench-shell"
    :class="{
      'workbench-shell--beginner': isBeginner && !isImmersiveLab,
      'workbench-shell--nav-collapsed': isExpert && sidebarCollapsed && !isImmersiveLab,
      'workbench-shell--immersive-lab': isImmersiveLab,
    }"
  >
    <aside v-if="isExpert && !isImmersiveLab" class="side-nav" :class="{ 'side-nav--collapsed': sidebarCollapsed }">
      <div class="side-nav__top">
        <div v-if="!sidebarCollapsed" class="brand-block">
          <div class="brand">学习门户</div>
          <p class="brand-tip">模块化学习与实验</p>
        </div>
        <div v-else class="brand-mark" title="学习门户">学</div>
        <button
          type="button"
          class="nav-collapse-toggle"
          :title="sidebarCollapsed ? '展开导航' : '收起导航'"
          :aria-expanded="!sidebarCollapsed"
          @click="toggleSidebar"
        >
          <el-icon :size="18">
            <Fold v-if="!sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
        </button>
      </div>

      <nav class="menu-list" aria-label="学习门户导航">
        <router-link
          id="tour-home"
          to="/workbench/index"
          class="menu-item menu-item--primary"
          title="学习首页"
        >
          <el-icon class="menu-ico"><House /></el-icon>
          <span class="menu-text">学习首页</span>
        </router-link>

        <router-link
          id="tour-marketplace"
          to="/workbench/marketplace"
          class="menu-item menu-item--primary menu-item--marketplace"
          title="资源广场"
        >
          <span class="menu-ico menu-ico--emoji" aria-hidden="true">💡</span>
          <span class="menu-text">资源广场</span>
        </router-link>

        <div class="nav-section">
          <div class="nav-section__label">知识库</div>
          <router-link
            id="tour-rag"
            to="/workbench/rag-build"
            class="menu-item menu-item--sub"
            title="实验二：知识注入与幻觉消除虚拟仿真"
          >
            <el-icon class="menu-ico"><Document /></el-icon>
            <span class="menu-text">实验二：知识注入与幻觉消除虚拟仿真</span>
          </router-link>
          <router-link
            id="tour-rag-visual"
            to="/workbench/rag-visual"
            class="menu-item menu-item--sub"
            title="实验二：知识注入与幻觉消除虚拟仿真"
          >
            <el-icon class="menu-ico"><View /></el-icon>
            <span class="menu-text">实验二：知识注入与幻觉消除虚拟仿真</span>
          </router-link>
          <router-link
            id="tour-knowledge-starry"
            to="/workbench/knowledge-universe"
            class="menu-item menu-item--sub"
            title="前置学习：AI 核心原理知识图谱"
          >
            <el-icon class="menu-ico"><Connection /></el-icon>
            <span class="menu-text">前置学习：AI 核心原理知识图谱</span>
          </router-link>
        </div>

        <div class="nav-section">
          <div class="nav-section__label">实验室</div>
          <router-link
            id="tour-prompt"
            to="/workbench/prompt"
            class="menu-item menu-item--sub"
            title="实验一：LLM生成原理与解码策略仿真"
          >
            <el-icon class="menu-ico"><MagicStick /></el-icon>
            <span class="menu-text">实验一：LLM生成原理与解码策略仿真</span>
          </router-link>
          <router-link
            id="tour-agent"
            to="/workbench/agent-arena"
            class="menu-item menu-item--sub"
            title="实验三：多智能体协同优化教学案例"
          >
            <el-icon class="menu-ico"><Cpu /></el-icon>
            <span class="menu-text">实验三：多智能体协同优化教学案例</span>
          </router-link>
        </div>
      </nav>

      <div class="side-nav__footer">
        <router-link
          to="/workbench/profile"
          class="menu-item menu-item--profile"
          title="个人学习中心"
        >
          <span class="menu-ico menu-ico--emoji" aria-hidden="true">📊</span>
          <span class="menu-text">我的成就</span>
        </router-link>
      </div>
    </aside>



    <section class="main-content" :class="{ 'main-content--immersive-lab': isImmersiveLab }">

      <header v-if="!isImmersiveLab" class="top-header">

        <div class="top-header__text">

          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>

        </div>

        <div class="top-header__controls">
          <button
            type="button"
            class="theme-toggle"
            :title="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
            :aria-label="isDarkTheme ? '切换为浅色模式' : '切换为深色模式'"
            @click="learningStore.toggleTheme()"
          >
            <el-icon :size="20">
              <Sunny v-if="isDarkTheme" />
              <Moon v-else />
            </el-icon>
          </button>

          <div class="mode-toggle" role="group" aria-label="工作台显示模式">

            <span class="mode-toggle__label" :class="{ 'mode-toggle__label--active': isBeginner }">新手</span>

            <button

              type="button"

              class="mode-switch"

              :class="{ 'mode-switch--expert': isExpert }"

              role="switch"

              :aria-checked="isExpert"

              @click="learningStore.toggleUserMode()"

            >

              <span class="mode-switch__thumb" />

            </button>

            <span class="mode-toggle__label" :class="{ 'mode-toggle__label--active': isExpert }">专家</span>

          </div>
        </div>

      </header>

      <main v-if="isImmersiveLab" class="page-body page-body--immersive-lab">
        <router-view />
      </main>

      <main v-else-if="isExpert" class="page-body">

        <router-view v-slot="{ Component }">
          <keep-alive
            :include="[
              'WorkbenchIndex',
              'UserProfile',
              'KnowledgeMap',
              'KnowledgeStarrySky',
              'ResourceMarketplace',
              'RagBuildWorkbench',
              'RagVisualWorkbench',
              'PromptWorkbench',
              'AgentArenaWorkbench',
            ]"
          >
            <component :is="Component" v-if="Component" :key="String(route.name ?? route.path)" />
          </keep-alive>
        </router-view>

      </main>



      <main v-else class="page-body learning-dashboard">
        <section class="dash-welcome">
          <p class="dash-welcome__eyebrow">Beginner Mode</p>
          <h2 class="dash-welcome__title">按任务推进，今天也能稳定进步</h2>
          <p class="dash-welcome__desc">先完成任务卡，再从下方入口进入模块。每一次操作都会累计经验值并解锁更高等级。</p>
        </section>

        <div class="dash-overview-grid">
          <section class="dash-section dash-today" aria-labelledby="today-heading">

          <h2 id="today-heading" class="dash-heading">

            <span class="dash-heading__icon">📋</span>

            今日任务

          </h2>

          <ul class="task-cards">

            <li

              v-for="task in todayTasks"

              :key="task.id"

              class="task-card"

              :class="{ 'task-card--done': task.done }"

            >

              <span class="task-card__badge">{{ task.done ? '已完成' : '待完成' }}</span>

              <span class="task-card__title">{{ task.title }}</span>

            </li>

          </ul>

          </section>



          <section class="dash-section dash-progress" aria-labelledby="progress-heading">

          <h2 id="progress-heading" class="dash-heading">

            <span class="dash-heading__icon">⚔️</span>

            成长进度

          </h2>

          <div class="xp-panel">

            <div class="xp-panel__top">

              <div class="xp-panel__level">

                <span class="xp-panel__level-tag">Lv.{{ progressMeta.level }}</span>

                <span class="xp-panel__level-name">{{ progressMeta.title }}</span>

              </div>

              <span class="xp-panel__percent">{{ progressMeta.percent }}%</span>

            </div>

            <div class="xp-bar" role="progressbar" :aria-valuenow="progressMeta.percent" aria-valuemin="0" aria-valuemax="100">

              <div class="xp-bar__fill" :style="{ width: `${progressMeta.percent}%` }">

                <span class="xp-bar__shine" />

              </div>

            </div>

            <p class="xp-panel__hint">再完成几个小任务，就能解锁下一档能力加成。</p>

          </div>

          </section>
        </div>



        <section class="dash-section dash-shortcuts dash-surface" aria-labelledby="shortcuts-heading">

          <h2 id="shortcuts-heading" class="dash-heading dash-heading--subtle">从这里出发</h2>

          <div class="shortcut-grid">

            <button type="button" class="shortcut-card shortcut-card--home" @click="enterExpertRoute('/workbench/index')">

              <span class="shortcut-card__emoji">🏠</span>

              <span class="shortcut-card__title">工作台首页</span>

              <span class="shortcut-card__desc">查看知识库与其他功能分区，选对入口再深入</span>

            </button>

            <button type="button" class="shortcut-card shortcut-card--map" @click="enterExpertRoute('/workbench/knowledge-universe')">

              <span class="shortcut-card__emoji">🗺️</span>

              <span class="shortcut-card__title">前置学习：AI 核心原理知识图谱</span>

              <span class="shortcut-card__desc">在前置学习知识图谱的 3D 拓扑中探索节点与关联</span>

            </button>

            <button type="button" class="shortcut-card shortcut-card--prompt" @click="enterExpertRoute('/workbench/prompt')">

              <span class="shortcut-card__emoji">✨</span>

              <span class="shortcut-card__title">实验一：LLM生成原理与解码策略仿真</span>

              <span class="shortcut-card__desc">打开 Prompt 实验室，套用模板快速上手</span>

            </button>

            <button type="button" class="shortcut-card shortcut-card--arena" @click="enterExpertRoute('/workbench/agent-arena')">

              <span class="shortcut-card__emoji">🎯</span>

              <span class="shortcut-card__title">实验三：多智能体协同优化教学案例</span>

              <span class="shortcut-card__desc">体验多智能体协作，把理论变实战</span>

            </button>

          </div>

        </section>

      </main>

    </section>



    <GlobalAiTutor v-if="!isImmersiveLab" />

  </div>

</template>



<script setup lang="ts">

import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { Connection, Cpu, Document, Expand, Fold, House, MagicStick, Moon, Sunny, View } from '@element-plus/icons-vue'
import GlobalAiTutor from '@/components/GlobalAiTutor.vue'
import { getExpProgressMeta, useGlobalLearningContextStore } from '@/stores/globalLearningContext'

const SIDEBAR_COLLAPSED_KEY = 'ai-workbench-sidebar-collapsed'

function readSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}



const learningStore = useGlobalLearningContextStore()

const { userMode, todayTasks, userExp, theme } = storeToRefs(learningStore)

const isDarkTheme = computed(() => theme.value === 'dark')

const router = useRouter()
const route = useRoute()

const isBeginner = computed(() => userMode.value === 'beginner')

const isExpert = computed(() => userMode.value === 'expert')

const isImmersiveLab = computed(() => route.meta.immersiveLab === true)

const sidebarCollapsed = ref(readSidebarCollapsed())

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sidebarCollapsed.value ? '1' : '0')
  } catch {
    /* ignore */
  }
}

watch(
  [() => route.fullPath, isExpert],
  () => {
    const path = route.path
    if (!path.startsWith('/workbench')) return
    if (isImmersiveLab.value) {
      const label = (route.meta.title as string) || '沉浸式学习'
      learningStore.recordWorkbenchVisit({ label, path })
      learningStore.transitionStudyContext({ label, path })
      return
    }
    const label = (route.meta.title as string) || '浏览页面'
    if (isExpert.value) {
      learningStore.recordWorkbenchVisit({ label, path })
      learningStore.applyRouteForTodayTasks(path)
    }
    learningStore.transitionStudyContext({ label, path })
  },
  { immediate: true },
)

function handleWindowBeforeUnload() {
  learningStore.flushActiveStudyRecord()
}

onMounted(() => {
  window.addEventListener('beforeunload', handleWindowBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleWindowBeforeUnload)
  learningStore.flushActiveStudyRecord()
})

const pageTitle = computed(() => {
  if (isBeginner.value) return '今天也要进步一点点 ✨'
  return (route.meta.title as string) || 'AI 学习工作台'
})

const pageSubtitle = computed(() => {
  if (isBeginner.value) {
    return '跟着看板走，不知不觉就把 Prompt、RAG 玩明白了。'
  }
  return (
    (route.meta.subtitle as string) ||
    '知识库与其他功能分区入口，左侧菜单可快速切换。'
  )
})

const progressMeta = computed(() => getExpProgressMeta(userExp.value))



function enterExpertRoute(path: string) {

  learningStore.setUserMode('expert')

  router.push(path)

}

</script>



<style scoped>

.workbench-shell {
  --nav-rail-width: 260px;
  min-height: 100vh;
  background: var(--wb-shell-bg);
  display: grid;
  grid-template-columns: var(--nav-rail-width) minmax(0, 1fr);
  transition: grid-template-columns 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.workbench-shell--nav-collapsed {
  --nav-rail-width: 76px;
}



.workbench-shell--beginner {

  grid-template-columns: minmax(0, 1fr);

}

.workbench-shell--immersive-lab {
  grid-template-columns: minmax(0, 1fr);
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
}

.main-content--immersive-lab {
  min-height: 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-body--immersive-lab {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}



.side-nav {
  position: relative;
  z-index: 2;
  min-height: 100vh;
  border-right: 1px solid var(--wb-sidebar-border);
  background: var(--wb-sidebar-bg);
  backdrop-filter: blur(14px);
  padding: 16px 12px 20px;
  display: flex;
  flex-direction: column;
  box-shadow: 4px 0 32px rgba(0, 0, 0, 0.12);
  transition: background 0.25s ease, border-color 0.2s ease;
}

.side-nav__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 18px;
}

.brand-block {
  min-width: 0;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 800;
  color: var(--accent-link);
  background: var(--accent-primary-soft);
  border: 1px solid var(--border-default);
  flex-shrink: 0;
}

.nav-collapse-toggle {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--wb-nav-item-border);
  border-radius: 10px;
  background: var(--wb-nav-item-bg);
  color: var(--wb-nav-item-text);
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}

.nav-collapse-toggle:hover {
  background: var(--bg-muted);
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.brand {
  font-size: 18px;
  font-weight: 800;
  color: var(--wb-brand-text);
  letter-spacing: -0.02em;
}

.brand-tip {
  margin: 6px 0 0;
  color: var(--wb-brand-tip);
  font-size: 12px;
  line-height: 1.4;
}

.menu-ico {
  flex-shrink: 0;
  font-size: 18px;
}

.menu-text {
  min-width: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--wb-nav-item-text);
  padding: 10px 12px;
  border: 1px solid var(--wb-nav-item-border);
  border-radius: 12px;
  background: var(--wb-nav-item-bg);
  transition: all 0.2s ease;
}

.side-nav--collapsed .brand-block,
.side-nav--collapsed .brand-tip,
.side-nav--collapsed .nav-section__label,
.side-nav--collapsed .menu-text {
  display: none;
}

.side-nav--collapsed .side-nav__top {
  flex-direction: column;
  align-items: center;
}

.side-nav--collapsed .nav-collapse-toggle {
  order: -1;
}

.side-nav--collapsed .menu-item {
  justify-content: center;
  padding-left: 10px;
  padding-right: 10px;
}

.side-nav--collapsed .menu-item--sub {
  margin-left: 0;
  border-left: none;
}

.side-nav--collapsed .menu-list {
  align-items: center;
}

.side-nav--collapsed .nav-section {
  align-items: center;
  width: 100%;
}



.menu-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
}

.side-nav__footer {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid var(--wb-sidebar-border);
}

.menu-item--profile {
  font-weight: 700;
  border-color: var(--accent-primary);
  background: var(--accent-primary-soft);
}

.menu-item--profile.router-link-active {
  border-color: var(--accent-primary);
  background: var(--wb-nav-active-bg);
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-section__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 4px 2px 0;
}

.menu-item--primary {
  font-weight: 800;
  border-color: var(--border-strong);
  background: var(--bg-muted);
}

.menu-item--marketplace {
  margin-top: 0;
  border-color: rgba(234, 179, 8, 0.45);
  background: linear-gradient(135deg, rgba(254, 249, 195, 0.35), var(--bg-muted));
}

html[data-theme='dark'] .menu-item--marketplace {
  border-color: rgba(251, 191, 36, 0.35);
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.12), var(--wb-nav-item-bg));
}

.menu-ico--emoji {
  font-size: 16px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
}

.menu-item--sub {
  font-size: 13px;
  padding: 9px 12px 9px 14px;
  border-radius: 10px;
  margin-left: 4px;
  border-left: 3px solid var(--border-subtle);
}

.menu-item--sub.router-link-active {
  border-left-color: var(--accent-primary);
}

.menu-item.router-link-active {
  color: var(--wb-nav-active-text);
  border-color: var(--accent-primary);
  background: var(--wb-nav-active-bg);
  font-weight: 700;
}



.main-content {

  display: flex;

  flex-direction: column;

  min-width: 0;

}



.top-header {
  padding: 18px 24px;
  border-bottom: 1px solid var(--wb-header-border);
  background: var(--wb-header-bg);
  backdrop-filter: blur(16px);

  display: flex;

  align-items: flex-start;

  justify-content: space-between;

  gap: 20px;

  flex-wrap: wrap;
  transition: background 0.25s ease, border-color 0.2s ease;
}

.top-header__controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.theme-toggle {
  width: 42px;
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  border: 1px solid var(--wb-theme-toggle-border);
  background: var(--wb-theme-toggle-bg);
  color: var(--text-primary);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    transform 0.15s ease,
    box-shadow 0.2s ease;
}

.theme-toggle:hover {
  background: var(--wb-theme-toggle-hover);
  border-color: var(--accent-primary);
  color: var(--accent-primary);
  box-shadow: var(--shadow-md);
}

.theme-toggle:active {
  transform: scale(0.96);
}

.top-header__text h1 {

  margin: 0 0 8px;

  font-size: 22px;

  color: var(--text-primary);

  letter-spacing: -0.02em;

}



.top-header__text p {

  margin: 0;

  color: var(--text-secondary);

  font-size: 14px;

  max-width: 520px;

  line-height: 1.5;

}



.mode-toggle {

  display: flex;

  align-items: center;

  gap: 10px;

  padding: 8px 14px;

  border-radius: 999px;

  background: var(--wb-mode-toggle-bg);

  border: 1px solid var(--wb-mode-toggle-border);

  flex-shrink: 0;

  transition: background 0.2s ease, border-color 0.2s ease;

}



.mode-toggle__label {

  font-size: 13px;

  font-weight: 600;

  color: var(--text-muted);

  transition: color 0.2s ease;

}



.mode-toggle__label--active {

  color: var(--accent-primary);

}



.mode-switch {

  position: relative;

  width: 48px;

  height: 28px;

  border-radius: 999px;

  border: none;

  cursor: pointer;

  background: linear-gradient(135deg, #6366f1, #818cf8);

  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.2);

  transition: background 0.25s ease, box-shadow 0.2s ease;

}



.mode-switch:focus-visible {

  outline: 2px solid var(--accent-primary);

  outline-offset: 3px;

}



.mode-switch--expert {

  background: linear-gradient(135deg, #10a37f, #059669);

}



.mode-switch__thumb {

  position: absolute;

  top: 3px;

  left: 3px;

  width: 22px;

  height: 22px;

  border-radius: 50%;

  background: #fff;

  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.2);

  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1);

}



.mode-switch--expert .mode-switch__thumb {

  transform: translateX(20px);

}



.page-body {

  padding: 20px 24px 32px;

}



/* —— Learning Dashboard —— */

.learning-dashboard {

  max-width: 1080px;

  margin: 0 auto;

  display: flex;

  flex-direction: column;

  gap: 22px;

}

.dash-welcome {

  border: 1px solid var(--border-subtle);

  background: linear-gradient(

    130deg,

    color-mix(in srgb, var(--bg-surface) 88%, transparent),

    color-mix(in srgb, var(--accent-primary-soft) 34%, transparent)

  );

  border-radius: 18px;

  padding: 18px 20px;

  box-shadow: var(--shadow-sm);

}

.dash-welcome__eyebrow {

  margin: 0 0 8px;

  font-size: 11px;

  letter-spacing: 0.08em;

  text-transform: uppercase;

  color: var(--accent-link);

  font-weight: 700;

}

.dash-welcome__title {

  margin: 0 0 8px;

  font-size: 22px;

  letter-spacing: -0.02em;

  color: var(--text-primary);

}

.dash-welcome__desc {

  margin: 0;

  font-size: 14px;

  color: var(--text-secondary);

  line-height: 1.55;

}

.dash-overview-grid {

  display: grid;

  grid-template-columns: 1.2fr 1fr;

  gap: 18px;

  align-items: stretch;

}

.dash-surface {

  border-radius: 20px;

  border: 1px solid var(--border-subtle);

  background: color-mix(in srgb, var(--bg-surface) 88%, transparent);

  box-shadow: var(--shadow-sm);

  padding: 18px;

}



.dash-section {

  animation: dash-in 0.45s ease backwards;

}



.dash-section:nth-child(2) {

  animation-delay: 0.06s;

}



.dash-section:nth-child(3) {

  animation-delay: 0.12s;

}



@keyframes dash-in {

  from {

    opacity: 0;

    transform: translateY(10px);

  }

  to {

    opacity: 1;

    transform: translateY(0);

  }

}



.dash-heading {

  margin: 0 0 14px;

  font-size: 17px;

  font-weight: 800;

  color: var(--text-primary);

  display: flex;

  align-items: center;

  gap: 8px;

}



.dash-heading--subtle {

  font-size: 14px;

  font-weight: 700;

  color: var(--text-secondary);

  text-transform: uppercase;

  letter-spacing: 0.06em;

}



.dash-heading__icon {

  font-size: 1.1em;

}



.task-cards {

  list-style: none;

  margin: 0;

  padding: 0;

  display: flex;

  flex-direction: column;

  gap: 10px;

}



.task-card {

  display: flex;

  align-items: center;

  gap: 14px;

  padding: 14px 16px;

  border-radius: 16px;

  background: var(--bg-surface);

  border: 1px solid var(--border-subtle);

  box-shadow: var(--shadow-sm);

  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

}



.task-card:hover {

  transform: translateY(-1px);

  box-shadow: var(--shadow-md);

  border-color: var(--accent-primary);

}



.task-card--done {

  opacity: 0.92;

  border-color: rgba(16, 163, 127, 0.45);

  background: var(--accent-primary-soft);

}



.task-card__badge {

  flex-shrink: 0;

  font-size: 12px;

  font-weight: 700;

  padding: 4px 10px;

  border-radius: 999px;

  background: #fef3c7;

  color: #b45309;

}



.task-card--done .task-card__badge {

  background: #dcfce7;

  color: #15803d;

}



.task-card__title {

  font-size: 14px;

  font-weight: 600;

  color: var(--text-primary);

}



.xp-panel {

  padding: 20px 22px;

  border-radius: 20px;

  background: var(--wb-xp-panel-bg);

  color: #e0e7ff;

  box-shadow:

    0 20px 50px rgba(0, 0, 0, 0.35),

    inset 0 1px 0 rgba(255, 255, 255, 0.12);

  border: 1px solid var(--wb-xp-panel-border);

}



.xp-panel__top {

  display: flex;

  justify-content: space-between;

  align-items: baseline;

  margin-bottom: 14px;

}



.xp-panel__level {

  display: flex;

  flex-direction: column;

  gap: 4px;

}



.xp-panel__level-tag {

  display: inline-block;

  font-size: 12px;

  font-weight: 800;

  letter-spacing: 0.04em;

  color: #fde68a;

}



.xp-panel__level-name {

  font-size: 20px;

  font-weight: 800;

  color: #fff;

  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.2);

}



.xp-panel__percent {

  font-size: 26px;

  font-weight: 800;

  color: #fef08a;

  font-variant-numeric: tabular-nums;

}



.xp-bar {

  height: 14px;

  border-radius: 999px;

  background: rgba(15, 23, 42, 0.35);

  overflow: hidden;

  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.25);

}



.xp-bar__fill {

  position: relative;

  height: 100%;

  border-radius: inherit;

  background: linear-gradient(90deg, #fbbf24, #f59e0b 40%, #fcd34d);

  box-shadow: 0 0 20px rgba(251, 191, 36, 0.6);

  transition: width 0.8s cubic-bezier(0.34, 1.2, 0.64, 1);

}



.xp-bar__shine {

  position: absolute;

  inset: 0;

  background: linear-gradient(

    105deg,

    transparent 0%,

    rgba(255, 255, 255, 0.45) 45%,

    transparent 70%

  );

  animation: xp-shine 2.5s ease-in-out infinite;

}



@keyframes xp-shine {

  0%,

  100% {

    transform: translateX(-100%);

  }

  50% {

    transform: translateX(100%);

  }

}



.xp-panel__hint {

  margin: 14px 0 0;

  font-size: 13px;

  opacity: 0.88;

  line-height: 1.45;

}



.shortcut-grid {

  display: grid;

  grid-template-columns: repeat(2, minmax(0, 1fr));

  gap: 16px;

}



.shortcut-card {

  text-align: left;

  border: none;

  cursor: pointer;

  padding: 24px 22px;

  border-radius: 20px;

  color: var(--text-primary);

  transition:

    transform 0.2s ease,

    box-shadow 0.2s ease;

  display: flex;

  flex-direction: column;

  gap: 8px;

  min-height: 132px;

  position: relative;

  overflow: hidden;

}



.shortcut-card::after {

  content: '';

  position: absolute;

  inset: -40% -20%;

  background: radial-gradient(circle, rgba(255, 255, 255, 0.35) 0%, transparent 65%);

  opacity: 0;

  transition: opacity 0.3s ease;

  pointer-events: none;

}



.shortcut-card:hover {

  transform: translateY(-4px) scale(1.01);

  box-shadow: var(--shadow-lg);

}



.shortcut-card:hover::after {

  opacity: 1;

}



.shortcut-card--home {

  background: var(--wb-shortcut-home);

  border: 1px solid var(--border-default);

}



.shortcut-card--map {

  background: var(--wb-shortcut-map);

  border: 1px solid var(--border-default);

}



.shortcut-card--prompt {

  background: var(--wb-shortcut-prompt);

  border: 1px solid var(--border-default);

}



.shortcut-card--arena {

  background: var(--wb-shortcut-arena);

  border: 1px solid var(--border-default);

}



.shortcut-card__emoji {

  font-size: 32px;

  line-height: 1;

}



.shortcut-card__title {

  font-size: 17px;

  font-weight: 800;

  letter-spacing: -0.02em;

}



.shortcut-card__desc {

  font-size: 13px;

  color: var(--text-secondary);

  line-height: 1.45;

}

.dash-shortcuts.dash-surface {

  padding: 18px 18px 20px;

}



@media (max-width: 960px) {

  .learning-dashboard {

    max-width: 760px;

    gap: 16px;

  }

  .dash-overview-grid {

    grid-template-columns: 1fr;

  }

  .shortcut-grid {

    grid-template-columns: 1fr;

  }

  .workbench-shell:not(.workbench-shell--beginner) {

    grid-template-columns: 1fr;

  }



  .side-nav {

    border-right: none;

    border-bottom: 1px solid var(--wb-sidebar-border);

  }



  .menu-list {
    flex-direction: column;
    gap: 12px;
  }



  .top-header {

    flex-direction: column;

    align-items: stretch;

  }



  .top-header__controls {

    align-self: flex-end;

    justify-content: flex-end;

  }

}

</style>


