<template>
  <div class="user-profile">
    <header class="profile-hero">
      <div class="profile-hero__identity">
        <el-avatar :size="72" class="profile-avatar" :icon="UserFilled" />
        <div class="profile-hero__text">
          <div class="profile-hero__name-row">
            <h1 class="profile-nickname">星云旅人</h1>
            <span class="profile-level">Lv.{{ expMeta.level }}</span>
          </div>
          <p class="profile-tagline">个人学习中心 · 能力全景与成长轨迹</p>
        </div>
      </div>
      <div class="profile-xp">
        <div class="profile-xp__row">
          <span class="profile-xp__label">本周经验</span>
          <span class="profile-xp__value">{{ expMeta.percent }}%</span>
        </div>
        <el-progress
          :percentage="expMeta.percent"
          :stroke-width="14"
          :show-text="false"
          striped
          striped-flow
          color="#6366f1"
          class="profile-xp__bar"
        />
        <p class="profile-xp__hint">
          称号「{{ expMeta.title }}」· 本级还需 {{ EXP_PER_LEVEL - expMeta.percent }} 点经验升级
        </p>
      </div>
    </header>

    <section class="study-metrics" aria-label="学习时长统计">
      <article class="study-metric-card">
        <p class="study-metric-card__label">累计学习时长</p>
        <p class="study-metric-card__value">{{ totalStudyMinutes }} 分钟</p>
      </article>
      <article class="study-metric-card">
        <p class="study-metric-card__label">近 7 天学习</p>
        <p class="study-metric-card__value">{{ weekStudyMinutes }} 分钟</p>
      </article>
      <article class="study-metric-card">
        <p class="study-metric-card__label">学习记录次数</p>
        <p class="study-metric-card__value">{{ totalStudySessions }} 次</p>
      </article>
    </section>

    <section class="study-calendar data-card" aria-label="学习日历热力图">
      <div class="data-card__head">
        <h2 class="data-card__title">学习时长日历</h2>
        <span class="data-card__sub">从进入页面开始计时 · 按天累计学习分钟</span>
      </div>
      <div ref="calendarHostRef" class="chart-host chart-host--calendar" />
    </section>

    <div class="profile-core">
      <section class="data-card" aria-labelledby="radar-heading">
        <div class="data-card__head">
          <h2 id="radar-heading" class="data-card__title">AI 能力雷达</h2>
          <span class="data-card__sub">四维能力模型 · 随工作台埋点实时累积</span>
        </div>
        <div ref="radarHostRef" class="chart-host" />
      </section>

      <section class="data-card" aria-labelledby="activity-heading">
        <div class="data-card__head">
          <h2 id="activity-heading" class="data-card__title">学习活跃度</h2>
          <span class="data-card__sub">近 7 天 · 各类型行为次数（堆叠柱）</span>
        </div>
        <div ref="activityHostRef" class="chart-host chart-host--activity" />
      </section>
    </div>

    <section class="study-log" aria-label="学习内容记录">
      <div class="study-log__head">
        <h2 class="study-log__title">学习内容记录</h2>
        <span class="study-log__sub">自动记录页面停留时长与学习主题</span>
      </div>
      <ul v-if="recentStudyRecords.length" class="study-log__list">
        <li v-for="item in recentStudyRecords" :key="item.id" class="study-log__item">
          <div class="study-log__main">
            <p class="study-log__topic">{{ item.label }}</p>
            <p class="study-log__content">{{ item.content }}</p>
          </div>
          <div class="study-log__meta">
            <span class="study-log__duration">{{ formatStudyDuration(item.durationSec) }}</span>
            <span class="study-log__time">{{ formatStudyTime(item.at) }}</span>
          </div>
        </li>
      </ul>
      <p v-else class="study-log__empty">暂无学习记录。进入任意工作台页面停留一段时间后将自动生成。</p>
    </section>

    <section class="badge-wall" aria-label="成就徽章">
      <h2 class="badge-wall__title">成就徽章</h2>
      <div class="badge-wall__row">
        <div class="glow-badge glow-badge--violet">
          <div class="glow-badge__ring" />
          <el-icon class="glow-badge__icon" :size="28"><Reading /></el-icon>
          <span class="glow-badge__name">RAG 探索者</span>
        </div>
        <div class="glow-badge glow-badge--cyan">
          <div class="glow-badge__ring" />
          <el-icon class="glow-badge__icon" :size="28"><ChatDotRound /></el-icon>
          <span class="glow-badge__name">百问不倦</span>
        </div>
        <div class="glow-badge glow-badge--amber">
          <div class="glow-badge__ring" />
          <el-icon class="glow-badge__icon" :size="28"><Trophy /></el-icon>
          <span class="glow-badge__name">深空先锋</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'UserProfile' })

import { storeToRefs } from 'pinia'
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { ChatDotRound, Reading, Trophy, UserFilled } from '@element-plus/icons-vue'
import {
  EXP_PER_LEVEL,
  getExpProgressMeta,
  useGlobalLearningContextStore,
  type ActivityKind,
  type ActivityLogEntry,
} from '@/stores/globalLearningContext'

const learningStore = useGlobalLearningContextStore()
const { userExp, skillRadar, activityLog, studyRecords } = storeToRefs(learningStore)

const expMeta = computed(() => getExpProgressMeta(userExp.value))
const recentStudyRecords = computed(() => studyRecords.value.slice(0, 12))
const totalStudyMinutes = computed(() =>
  Math.round(studyRecords.value.reduce((acc, item) => acc + item.durationSec, 0) / 60),
)
const totalStudySessions = computed(() => studyRecords.value.length)
const weekStudyMinutes = computed(() => {
  const now = Date.now()
  const weekMs = 7 * 24 * 60 * 60 * 1000
  const totalSec = studyRecords.value
    .filter((x) => now - x.at <= weekMs)
    .reduce((acc, item) => acc + item.durationSec, 0)
  return Math.round(totalSec / 60)
})

const radarHostRef = ref<HTMLElement | null>(null)
const activityHostRef = ref<HTMLElement | null>(null)
const calendarHostRef = ref<HTMLElement | null>(null)
const radarChart = shallowRef<ECharts | null>(null)
const activityChart = shallowRef<ECharts | null>(null)
const calendarChart = shallowRef<ECharts | null>(null)

const CHART_TEXT = '#c7d2fe'
const CHART_LINE = 'rgba(129, 140, 248, 0.45)'
const CHART_ACCENT = '#22d3ee'
const CHART_GLOW = '#a78bfa'

const ACTIVITY_TYPE_ORDER: ActivityKind[] = ['prompt', 'rag', 'agent', 'read']
const ACTIVITY_TYPE_LABEL: Record<ActivityKind, string> = {
  prompt: '提示词',
  rag: 'RAG',
  agent: 'Agent',
  read: '阅读',
}

function dateKeyFromDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 近 7 天（含今天）的日期键与短标签 */
function last7DaySlots(): { key: string; label: string }[] {
  const out: { key: string; label: string }[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() - i)
    out.push({
      key: dateKeyFromDate(d),
      label: `${d.getMonth() + 1}/${d.getDate()}`,
    })
  }
  return out
}

function aggregateActivityByDay(log: ActivityLogEntry[]) {
  const slots = last7DaySlots()
  const counts: Record<string, Record<ActivityKind, number>> = {}
  for (const s of slots) {
    counts[s.key] = { prompt: 0, rag: 0, agent: 0, read: 0 }
  }
  for (const e of log) {
    if (!counts[e.date]) continue
    counts[e.date][e.type] += 1
  }
  return { slots, counts }
}

const radarValues = computed(() => [
  skillRadar.value.prompt,
  skillRadar.value.rag,
  skillRadar.value.agent,
  skillRadar.value.read,
])

function buildRadarOption(values: number[]): echarts.EChartsOption {
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    radar: {
      indicator: [
        { name: '提示词工程', max: 100 },
        { name: '知识检索', max: 100 },
        { name: 'Agent 编排', max: 100 },
        { name: '理论基础', max: 100 },
      ],
      center: ['50%', '52%'],
      radius: '62%',
      axisName: {
        color: CHART_TEXT,
        fontSize: 12,
        fontWeight: 600,
      },
      splitLine: { lineStyle: { color: CHART_LINE } },
      splitArea: {
        areaStyle: {
          color: ['rgba(30, 27, 75, 0.35)', 'rgba(49, 46, 129, 0.2)'],
        },
      },
      axisLine: { lineStyle: { color: CHART_LINE } },
    },
    series: [
      {
        type: 'radar',
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: {
          width: 2,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
            { offset: 0, color: CHART_ACCENT },
            { offset: 1, color: CHART_GLOW },
          ]),
        },
        itemStyle: {
          color: CHART_ACCENT,
          borderColor: '#fff',
          borderWidth: 1,
          shadowBlur: 12,
          shadowColor: 'rgba(34, 211, 238, 0.55)',
        },
        areaStyle: {
          color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
            { offset: 0, color: 'rgba(34, 211, 238, 0.45)' },
            { offset: 1, color: 'rgba(99, 102, 241, 0.08)' },
          ]),
        },
        data: [{ value: values, name: '当前能力' }],
      },
    ],
  }
}

const BAR_COLORS: Record<ActivityKind, string> = {
  prompt: '#22d3ee',
  rag: '#818cf8',
  agent: '#c084fc',
  read: '#fbbf24',
}

function buildActivityBarOption(log: ActivityLogEntry[]): echarts.EChartsOption {
  const { slots, counts } = aggregateActivityByDay(log)
  const xLabels = slots.map((s) => s.label)
  const series = ACTIVITY_TYPE_ORDER.map((t) => ({
    name: ACTIVITY_TYPE_LABEL[t],
    type: 'bar' as const,
    stack: 'total',
    emphasis: { focus: 'series' as const },
    itemStyle: { color: BAR_COLORS[t] },
    data: slots.map((s) => counts[s.key][t]),
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    legend: {
      textStyle: { color: CHART_TEXT, fontSize: 11 },
      top: 0,
      itemWidth: 10,
      itemHeight: 10,
    },
    grid: { left: 40, right: 12, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: xLabels,
      axisLine: { lineStyle: { color: CHART_LINE } },
      axisLabel: { color: CHART_TEXT, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: CHART_LINE, opacity: 0.35 } },
      axisLabel: { color: CHART_TEXT, fontSize: 11 },
    },
    series,
  }
}

function buildStudyCalendarOption(records: { date: string; durationSec: number }[]): echarts.EChartsOption {
  const year = String(new Date().getFullYear())
  const byDate = new Map<string, number>()
  for (const r of records) {
    if (!r.date.startsWith(`${year}-`)) continue
    const mins = Math.max(1, Math.round(r.durationSec / 60))
    byDate.set(r.date, (byDate.get(r.date) || 0) + mins)
  }
  const data = Array.from(byDate.entries())
  const max = Math.max(30, ...data.map((x) => x[1]))
  return {
    backgroundColor: 'transparent',
    tooltip: {
      formatter: (params) => {
        const value = Array.isArray((params as { value?: unknown }).value)
          ? ((params as { value: unknown[] }).value as [string, number])
          : null
        if (!value) return '暂无学习记录'
        return `${value[0]}<br/>学习 ${value[1]} 分钟`
      },
    },
    visualMap: {
      min: 0,
      max,
      orient: 'horizontal',
      left: 'center',
      top: 6,
      text: ['高', '低'],
      textStyle: { color: CHART_TEXT, fontSize: 11 },
      inRange: {
        color: ['#1f2937', '#1d4ed8', '#22d3ee', '#86efac'],
      },
      calculable: false,
    },
    calendar: {
      top: 44,
      left: 24,
      right: 14,
      cellSize: ['auto', 13],
      range: year,
      splitLine: { show: false },
      itemStyle: { borderWidth: 1, borderColor: 'rgba(148, 163, 184, 0.22)' },
      dayLabel: { color: CHART_TEXT, firstDay: 1, nameMap: 'ZH' },
      monthLabel: { color: CHART_TEXT },
      yearLabel: { show: false },
    },
    series: {
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data,
    },
  }
}

function formatStudyDuration(sec: number): string {
  if (sec < 60) return `${sec} 秒`
  const h = Math.floor(sec / 3600)
  const m = Math.round((sec % 3600) / 60)
  if (h <= 0) return `${m} 分钟`
  if (m <= 0) return `${h} 小时`
  return `${h} 小时 ${m} 分钟`
}

function formatStudyTime(ts: number): string {
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function resizeCharts() {
  radarChart.value?.resize()
  activityChart.value?.resize()
  calendarChart.value?.resize()
}

useResizeObserver(radarHostRef, () => {
  radarChart.value?.resize()
})

useResizeObserver(activityHostRef, () => {
  activityChart.value?.resize()
})

useResizeObserver(calendarHostRef, () => {
  calendarChart.value?.resize()
})

async function initCharts() {
  await nextTick()
  if (radarHostRef.value && !radarChart.value) {
    radarChart.value = echarts.init(radarHostRef.value, undefined, { renderer: 'canvas' })
    radarChart.value.setOption(buildRadarOption(radarValues.value))
  }
  if (activityHostRef.value && !activityChart.value) {
    activityChart.value = echarts.init(activityHostRef.value, undefined, { renderer: 'canvas' })
    activityChart.value.setOption(buildActivityBarOption(activityLog.value))
  }
  if (calendarHostRef.value && !calendarChart.value) {
    calendarChart.value = echarts.init(calendarHostRef.value, undefined, { renderer: 'canvas' })
    calendarChart.value.setOption(buildStudyCalendarOption(studyRecords.value))
  }
  resizeCharts()
}

/** 响应式：雷达图随 Pinia skillRadar 更新 */
watch(
  radarValues,
  (vals) => {
    if (!radarChart.value) return
    radarChart.value.setOption({
      series: [{ data: [{ value: vals, name: '当前能力' }] }],
    })
  },
  { deep: true },
)

/** 响应式：柱状图随 activityLog 更新 */
watch(
  activityLog,
  (log) => {
    if (!activityChart.value) return
    activityChart.value.setOption(buildActivityBarOption(log), { notMerge: true })
  },
  { deep: true },
)

watch(
  studyRecords,
  (records) => {
    if (!calendarChart.value) return
    calendarChart.value.setOption(buildStudyCalendarOption(records), { notMerge: true })
  },
  { deep: true },
)

function disposeCharts() {
  radarChart.value?.dispose()
  activityChart.value?.dispose()
  calendarChart.value?.dispose()
  radarChart.value = null
  activityChart.value = null
  calendarChart.value = null
}

onMounted(() => {
  void initCharts()
})

onActivated(() => {
  resizeCharts()
})

onBeforeUnmount(() => {
  disposeCharts()
})
</script>

<style scoped>
.user-profile {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding-bottom: 28px;
}

.profile-hero {
  display: flex;
  flex-wrap: wrap;
  align-items: stretch;
  gap: 22px;
  padding: 22px 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.96) 0%, rgba(30, 27, 75, 0.94) 42%, rgba(76, 29, 149, 0.9) 100%);
  border: 1px solid rgba(129, 140, 248, 0.28);
  box-shadow:
    0 24px 64px rgba(15, 23, 42, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.profile-hero__identity {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 1 1 240px;
  min-width: 0;
}

.profile-avatar {
  flex-shrink: 0;
  background: linear-gradient(145deg, #312e81, #7c3aed);
  color: #e0e7ff;
  border: 2px solid rgba(167, 139, 250, 0.55);
  box-shadow: 0 0 28px rgba(129, 140, 248, 0.45);
}

.profile-hero__text {
  min-width: 0;
}

.profile-hero__name-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.profile-nickname {
  margin: 0;
  font-size: 1.55rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: #f8fafc;
  text-shadow: 0 2px 20px rgba(99, 102, 241, 0.35);
}

.profile-level {
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  padding: 4px 10px;
  border-radius: 999px;
  color: #fef08a;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(250, 204, 21, 0.45);
  box-shadow: 0 0 18px rgba(250, 204, 21, 0.2);
}

.profile-tagline {
  margin: 8px 0 0;
  font-size: 0.88rem;
  color: rgba(199, 210, 254, 0.82);
}

.profile-xp {
  flex: 1 1 280px;
  min-width: 0;
  padding: 4px 4px 0;
  align-self: center;
}

.profile-xp__row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 8px;
}

.profile-xp__label {
  font-size: 0.8rem;
  font-weight: 700;
  color: rgba(199, 210, 254, 0.75);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.profile-xp__value {
  font-size: 1.35rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: #a5f3fc;
}

.profile-xp__bar :deep(.el-progress-bar__outer) {
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.55);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.35);
}

.profile-xp__bar :deep(.el-progress-bar__inner) {
  border-radius: 999px;
  background-image: linear-gradient(90deg, #22d3ee, #818cf8 45%, #c084fc);
  box-shadow: 0 0 20px rgba(34, 211, 238, 0.35);
}

.profile-xp__hint {
  margin: 10px 0 0;
  font-size: 0.78rem;
  color: rgba(165, 180, 252, 0.72);
}

.profile-core {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.study-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.study-metric-card {
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid rgba(129, 140, 248, 0.25);
  background: linear-gradient(145deg, rgba(30, 41, 59, 0.82) 0%, rgba(30, 27, 75, 0.74) 100%);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.22);
}

.study-metric-card__label {
  margin: 0;
  color: rgba(199, 210, 254, 0.75);
  font-size: 12px;
}

.study-metric-card__value {
  margin: 8px 0 0;
  color: #ecfeff;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.01em;
}

@media (max-width: 900px) {
  .study-metrics {
    grid-template-columns: 1fr;
  }

  .profile-core {
    grid-template-columns: 1fr;
  }
}

.data-card {
  border-radius: 18px;
  padding: 16px 16px 8px;
  background: linear-gradient(165deg, rgba(15, 23, 42, 0.92) 0%, rgba(30, 27, 75, 0.88) 100%);
  border: 1px solid rgba(99, 102, 241, 0.22);
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.28);
}

.data-card__head {
  margin-bottom: 6px;
  padding: 0 4px;
}

.data-card__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 800;
  color: #e0e7ff;
}

.data-card__sub {
  display: block;
  margin-top: 4px;
  font-size: 0.75rem;
  color: rgba(165, 180, 252, 0.65);
}

.chart-host {
  width: 100%;
  height: 300px;
}

.chart-host--activity {
  height: 260px;
}

.chart-host--calendar {
  height: 180px;
}

.study-calendar {
  padding-bottom: 14px;
}

.study-log {
  border-radius: 18px;
  border: 1px solid rgba(99, 102, 241, 0.22);
  background: linear-gradient(165deg, rgba(15, 23, 42, 0.92) 0%, rgba(30, 27, 75, 0.88) 100%);
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.28);
  padding: 16px;
}

.study-log__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 12px;
}

.study-log__title {
  margin: 0;
  font-size: 16px;
  color: #e0e7ff;
}

.study-log__sub {
  font-size: 12px;
  color: rgba(165, 180, 252, 0.75);
}

.study-log__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.study-log__item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 12px;
  border-radius: 12px;
  border: 1px solid rgba(129, 140, 248, 0.24);
  background: rgba(15, 23, 42, 0.34);
}

@media (max-width: 760px) {
  .study-log__item {
    flex-direction: column;
    align-items: flex-start;
  }

  .study-log__meta {
    align-items: flex-start;
  }
}

.study-log__topic {
  margin: 0;
  font-size: 14px;
  color: #f8fafc;
  font-weight: 700;
}

.study-log__content {
  margin: 4px 0 0;
  font-size: 12px;
  color: rgba(199, 210, 254, 0.78);
}

.study-log__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  flex-shrink: 0;
}

.study-log__duration {
  font-size: 13px;
  font-weight: 700;
  color: #67e8f9;
}

.study-log__time {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(148, 163, 184, 0.95);
}

.study-log__empty {
  margin: 0;
  padding: 14px 6px;
  color: rgba(165, 180, 252, 0.75);
  font-size: 13px;
}

.badge-wall {
  padding: 20px 22px 22px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.55);
  border: 1px solid rgba(226, 232, 240, 0.95);
  backdrop-filter: blur(12px);
}

.badge-wall__title {
  margin: 0 0 16px;
  font-size: 0.95rem;
  font-weight: 800;
  color: #1e1b4b;
  letter-spacing: -0.01em;
}

.badge-wall__row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 20px;
}

.glow-badge {
  position: relative;
  flex: 0 1 160px;
  min-height: 132px;
  padding: 20px 14px 16px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  text-align: center;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.06) 0%, rgba(255, 255, 255, 0.65) 100%);
  border: 1px solid rgba(199, 210, 254, 0.45);
  overflow: hidden;
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease;
}

.glow-badge:hover {
  transform: translateY(-4px);
}

.glow-badge__ring {
  position: absolute;
  inset: -40%;
  background: conic-gradient(from 210deg, transparent, var(--badge-glow, #818cf8), transparent 55%);
  opacity: 0.22;
  animation: badge-spin 10s linear infinite;
  pointer-events: none;
}

.glow-badge--violet {
  --badge-glow: #a78bfa;
  box-shadow: 0 0 0 1px rgba(129, 140, 248, 0.15), 0 12px 36px rgba(99, 102, 241, 0.2);
}

.glow-badge--violet:hover {
  box-shadow: 0 0 0 1px rgba(129, 140, 248, 0.35), 0 20px 48px rgba(99, 102, 241, 0.35);
}

.glow-badge--cyan {
  --badge-glow: #22d3ee;
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.15), 0 12px 36px rgba(34, 211, 238, 0.18);
}

.glow-badge--cyan:hover {
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.35), 0 20px 48px rgba(34, 211, 238, 0.3);
}

.glow-badge--amber {
  --badge-glow: #fbbf24;
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.2), 0 12px 36px rgba(245, 158, 11, 0.2);
}

.glow-badge--amber:hover {
  box-shadow: 0 0 0 1px rgba(251, 191, 36, 0.4), 0 20px 48px rgba(245, 158, 11, 0.32);
}

.glow-badge__icon {
  position: relative;
  z-index: 1;
  color: #312e81;
  filter: drop-shadow(0 2px 8px rgba(99, 102, 241, 0.35));
}

.glow-badge__name {
  position: relative;
  z-index: 1;
  font-size: 0.88rem;
  font-weight: 800;
  color: #1e1b4b;
  line-height: 1.3;
}

@keyframes badge-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
