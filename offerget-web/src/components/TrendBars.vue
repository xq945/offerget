<script setup>
import { computed } from 'vue'

const props = defineProps({
  trend: { type: Array, default: () => [] },
})

const total = computed(() => props.trend.reduce((sum, item) => sum + item.count, 0))
const max = computed(() => Math.max(1, ...props.trend.map((item) => item.count)))

function todayString() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const today = todayString()
</script>

<template>
  <div>
    <div class="flex items-end justify-between">
      <div>
        <p class="text-sm font-bold text-slate-800">本周投递趋势</p>
        <p class="mt-1 text-xs text-slate-400">按天查看投递节奏，避免截止日前集中处理</p>
      </div>
      <div class="text-right">
        <p class="text-2xl font-black text-slate-950">
          {{ total }}<span class="ml-1 text-xs font-semibold text-slate-400">个</span>
        </p>
        <p class="text-[11px] text-slate-400">本周</p>
      </div>
    </div>

    <div class="mt-5 flex h-44 items-end gap-2 sm:gap-3">
      <div v-for="item in trend" :key="item.date" class="flex min-w-0 flex-1 flex-col items-center gap-2">
        <span class="text-xs font-bold" :class="item.count ? 'text-blue-600' : 'text-slate-300'">{{ item.count }}</span>
        <div class="flex h-28 w-full items-end justify-center">
          <div
            class="w-full max-w-9 rounded-t-lg transition-all duration-500"
            :class="item.count ? 'bg-gradient-to-t from-blue-600 to-sky-400 shadow-[0_8px_16px_rgba(37,99,235,0.18)]' : 'bg-slate-100'"
            :style="{ height: `${Math.max((item.count / max) * 100, 6)}%` }"
          />
        </div>
        <span
          class="rounded-full px-1.5 py-0.5 text-[11px] font-semibold"
          :class="item.date === today ? 'bg-blue-600 text-white' : 'text-slate-400'"
        >
          {{ item.label }}
        </span>
      </div>
    </div>
  </div>
</template>
