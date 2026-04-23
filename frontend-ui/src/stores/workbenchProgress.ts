import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 经验值结算、雷达技能点、按日 activityLog 见 {@link useGlobalLearningContextStore}.recordActivity
 */

export type RagVisualStep = 'text-split' | 'vector-db' | 'similarity-search'

export const useWorkbenchProgressStore = defineStore('workbenchProgress', () => {
  const ragVisualStep = ref<RagVisualStep>('text-split')

  function setRagVisualStep(step: RagVisualStep) {
    ragVisualStep.value = step
  }

  return {
    ragVisualStep,
    setRagVisualStep,
  }
})

