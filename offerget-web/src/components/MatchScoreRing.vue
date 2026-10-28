<script setup>
import { computed } from 'vue'

import { scoreTone } from '@/utils/format'

const props = defineProps({
  score: { type: Number, default: null },
  size: { type: Number, default: 112 },
  stroke: { type: Number, default: 8 },
  showLabel: { type: Boolean, default: true },
})

const radius = 42
const circumference = 2 * Math.PI * radius

const tone = computed(() => scoreTone(props.score))
const dash = computed(() => {
  const value = Math.min(Math.max(props.score ?? 0, 0), 100)
  return (circumference * value) / 100
})
</script>

<template>
  <div class="relative inline-flex items-center justify-center" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg viewBox="0 0 100 100" class="h-full w-full -rotate-90">
      <circle cx="50" cy="50" :r="radius" fill="none" stroke="#eaf1fe" :stroke-width="stroke" />
      <circle
        cx="50"
        cy="50"
        :r="radius"
        fill="none"
        :stroke="tone.ring"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="`${dash} ${circumference}`"
        class="transition-all duration-700"
      />
    </svg>
    <div class="absolute inset-0 flex flex-col items-center justify-center">
      <span class="font-black leading-none" :class="tone.text" :style="{ fontSize: `${size * 0.26}px` }">
        {{ score ?? '—' }}
      </span>
      <span v-if="showLabel" class="mt-1 text-[10px] font-semibold text-slate-400">{{ tone.label }}</span>
    </div>
  </div>
</template>
