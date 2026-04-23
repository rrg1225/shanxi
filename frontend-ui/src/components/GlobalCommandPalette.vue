<template>
  <Teleport to="body">
    <Transition name="palette-fade">
      <div
        v-if="open"
        class="gcp-overlay"
        role="dialog"
        aria-modal="true"
        aria-label="命令面板"
        @click.self="close"
      >
        <div class="gcp-panel" @click.stop>
          <div class="gcp-input-wrap">
            <span class="gcp-input-icon" aria-hidden="true">⌘</span>
            <input
              ref="inputRef"
              v-model="query"
              type="search"
              class="gcp-input"
              placeholder="搜索命令、页面或操作…"
              autocomplete="off"
              spellcheck="false"
              @keydown.down.prevent="moveActive(1)"
              @keydown.up.prevent="moveActive(-1)"
              @keydown.enter.prevent="runActive"
            />
            <kbd class="gcp-hint">Esc</kbd>
          </div>

          <ul class="gcp-list" role="listbox">
            <li
              v-for="(item, index) in filteredItems"
              :key="item.id"
              class="gcp-item"
              :class="{ 'gcp-item--active': index === activeIndex }"
              role="option"
              :aria-selected="index === activeIndex"
              @mouseenter="activeIndex = index"
              @click="selectItem(item)"
            >
              <span class="gcp-item-label">{{ item.label }}</span>
              <span class="gcp-item-tag">{{ item.category }}</span>
            </li>
            <li v-if="filteredItems.length === 0" class="gcp-empty">没有匹配项</li>
          </ul>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useMagicKeys } from '@vueuse/core'

type CommandItem = {
  id: string
  label: string
  category: string
}

const MOCK_ITEMS: CommandItem[] = [
  { id: 'nav-starry', label: '跳转到前置学习：AI 核心原理知识图谱', category: '导航' },
  { id: 'nav-prompt', label: '打开实验一：LLM生成原理与解码策略仿真', category: '导航' },
  { id: 'action-tutor', label: '🤖 召唤 AI 导师', category: '操作' },
]

const open = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const filteredItems = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return MOCK_ITEMS
  return MOCK_ITEMS.filter(
    (i) =>
      i.label.toLowerCase().includes(q) || i.category.toLowerCase().includes(q),
  )
})

watch(filteredItems, () => {
  activeIndex.value = 0
})

function close() {
  open.value = false
  query.value = ''
  activeIndex.value = 0
}

function toggle() {
  open.value = !open.value
  if (!open.value) {
    query.value = ''
    activeIndex.value = 0
  }
}

function moveActive(delta: number) {
  const len = filteredItems.value.length
  if (len === 0) return
  activeIndex.value = (activeIndex.value + delta + len) % len
}

function runActive() {
  const item = filteredItems.value[activeIndex.value]
  if (item) selectItem(item)
}

function selectItem(item: CommandItem) {
  // Mock：仅演示，可在此接入 router.push 或事件总线
  console.log('[CommandPalette]', item.id, item.label)
  close()
}

const { meta, ctrl, k, escape } = useMagicKeys({
  passive: false,
  onEventFired(e) {
    if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
      e.preventDefault()
    }
  },
})

watch(
  () => (meta.value && k.value) || (ctrl.value && k.value),
  (pressed) => {
    if (pressed) toggle()
  },
)

watch(escape, (v) => {
  if (v && open.value) close()
})

watch(open, async (isOpen) => {
  if (isOpen) {
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
})
</script>

<style scoped>
.gcp-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: min(12vh, 120px) 1rem 2rem;
  background: rgba(6, 8, 14, 0.72);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.gcp-panel {
  width: min(640px, 100%);
  border-radius: 16px;
  border: 1px solid rgba(120, 140, 200, 0.18);
  background: linear-gradient(
    165deg,
    rgba(22, 26, 38, 0.96) 0%,
    rgba(12, 14, 22, 0.98) 100%
  );
  box-shadow:
    0 0 0 1px rgba(0, 0, 0, 0.35),
    0 24px 80px rgba(0, 0, 0, 0.55),
    0 0 120px rgba(88, 120, 255, 0.08);
  overflow: hidden;
}

.gcp-input-wrap {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.85rem 1rem 0.85rem 1.1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.gcp-input-icon {
  font-size: 0.85rem;
  color: rgba(160, 175, 210, 0.55);
  font-family: ui-monospace, monospace;
  user-select: none;
}

.gcp-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: #e8ecff;
  font-size: 1.05rem;
  font-weight: 450;
  letter-spacing: 0.01em;
  caret-color: #7c9eff;
}

.gcp-input::placeholder {
  color: rgba(150, 165, 200, 0.4);
}

.gcp-input:focus {
  box-shadow: none;
}

.gcp-input-wrap:focus-within {
  box-shadow: inset 0 0 0 1px rgba(100, 140, 255, 0.25),
    0 0 24px rgba(80, 120, 255, 0.22), 0 0 48px rgba(60, 100, 220, 0.12);
  border-radius: 12px;
  margin: 0.35rem 0.5rem;
  padding: 0.5rem 0.85rem 0.5rem 0.95rem;
  border-bottom-color: transparent;
}

.gcp-hint {
  font-size: 0.7rem;
  padding: 0.2rem 0.45rem;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(180, 195, 230, 0.55);
  font-family: ui-monospace, 'SF Mono', monospace;
  background: rgba(0, 0, 0, 0.25);
}

.gcp-list {
  list-style: none;
  margin: 0;
  padding: 0.4rem;
  max-height: min(52vh, 360px);
  overflow-y: auto;
}

.gcp-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0.85rem;
  margin: 2px 0;
  border-radius: 10px;
  cursor: pointer;
  color: #c8d0f0;
  transition:
    background 0.18s ease,
    color 0.18s ease,
    transform 0.15s ease;
}

.gcp-item:hover,
.gcp-item--active {
  background: rgba(90, 130, 255, 0.22);
  color: #f0f4ff;
}

.gcp-item--active {
  box-shadow: inset 0 0 0 1px rgba(120, 160, 255, 0.35);
}

.gcp-item-label {
  font-size: 0.95rem;
  line-height: 1.35;
}

.gcp-item-tag {
  flex-shrink: 0;
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: rgba(140, 165, 220, 0.65);
  padding: 0.2rem 0.45rem;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.35);
}

.gcp-empty {
  padding: 1.25rem 1rem;
  text-align: center;
  color: rgba(150, 165, 200, 0.45);
  font-size: 0.9rem;
}

.palette-fade-enter-active,
.palette-fade-leave-active {
  transition: opacity 0.2s ease;
}

.palette-fade-enter-active .gcp-panel,
.palette-fade-leave-active .gcp-panel {
  transition:
    transform 0.22s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.2s ease;
}

.palette-fade-enter-from,
.palette-fade-leave-to {
  opacity: 0;
}

.palette-fade-enter-from .gcp-panel,
.palette-fade-leave-to .gcp-panel {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}
</style>
