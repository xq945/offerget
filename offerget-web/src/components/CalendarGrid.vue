<script setup>
import { computed, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

import { eventMeta } from '@/utils/format'

const props = defineProps({
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  events: { type: Array, default: () => [] },
})

const emit = defineEmits(['change'])

const weekLabels = ['一', '二', '三', '四', '五', '六', '日']

function two(value) {
  return String(value).padStart(2, '0')
}

function keyOf(date) {
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`
}

function todayKey() {
  return keyOf(new Date())
}

const selectedDate = ref(todayKey())

watch(
  () => [props.year, props.month],
  () => {
    const now = new Date()
    const isCurrentMonth = now.getFullYear() === props.year && now.getMonth() + 1 === props.month
    selectedDate.value = isCurrentMonth ? todayKey() : `${props.year}-${two(props.month)}-01`
  },
)

const eventsByDate = computed(() => {
  const map = {}
  for (const event of props.events) {
    if (!map[event.date]) {
      map[event.date] = []
    }
    map[event.date].push(event)
  }
  return map
})

const cells = computed(() => {
  const first = new Date(props.year, props.month - 1, 1)
  const offset = (first.getDay() + 6) % 7
  const start = new Date(props.year, props.month - 1, 1 - offset)
  const today = todayKey()

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + index)
    const key = keyOf(date)
    return {
      key,
      day: date.getDate(),
      inMonth: date.getMonth() === props.month - 1,
      isToday: key === today,
      events: eventsByDate.value[key] || [],
    }
  })
})

const selectedEvents = computed(() => eventsByDate.value[selectedDate.value] || [])

const monthEventCount = computed(() =>
  props.events.filter((event) => String(event.date).startsWith(`${props.year}-${two(props.month)}`)).length,
)

function move(delta) {
  const target = new Date(props.year, props.month - 1 + delta, 1)
  emit('change', target.getFullYear(), target.getMonth() + 1)
}
</script>

<template>
  <div class="soft-card p-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="text-sm font-bold text-slate-800">投递台历</p>
        <p class="mt-1 text-xs text-slate-400">按月汇总截止、投递、笔面试和结果事件</p>
      </div>
      <div class="flex items-center gap-2">
        <button class="grid h-8 w-8 place-items-center rounded-lg border border-blue-100 bg-white text-slate-500 transition hover:text-blue-600" @click="move(-1)">
          <ChevronLeft class="h-4 w-4" />
        </button>
        <span class="min-w-24 text-center text-sm font-bold text-slate-700">{{ year }} 年 {{ month }} 月</span>
        <button class="grid h-8 w-8 place-items-center rounded-lg border border-blue-100 bg-white text-slate-500 transition hover:text-blue-600" @click="move(1)">
          <ChevronRight class="h-4 w-4" />
        </button>
      </div>
    </div>

    <div class="mt-4 grid gap-5 lg:grid-cols-[minmax(0,1fr)_260px]">
      <div>
        <div class="grid grid-cols-7 gap-1">
          <span v-for="label in weekLabels" :key="label" class="py-1 text-center text-[11px] font-bold text-slate-400">
            周{{ label }}
          </span>
          <button
            v-for="cell in cells"
            :key="cell.key"
            class="relative flex h-11 flex-col items-center justify-center rounded-lg text-xs font-semibold transition"
            :class="[
              cell.inMonth ? 'text-slate-700' : 'text-slate-300',
              selectedDate === cell.key ? 'bg-blue-600 text-white shadow-[0_8px_16px_rgba(37,99,235,0.24)]' : 'hover:bg-blue-50',
              cell.isToday && selectedDate !== cell.key ? 'ring-1 ring-inset ring-blue-300' : '',
            ]"
            @click="selectedDate = cell.key"
          >
            <span>{{ cell.day }}</span>
            <span v-if="cell.events.length" class="absolute bottom-1 flex gap-0.5">
              <span
                v-for="(event, index) in cell.events.slice(0, 3)"
                :key="index"
                class="h-1 w-1 rounded-full"
                :class="selectedDate === cell.key ? 'bg-white/80' : eventMeta(event.type).dot"
              />
            </span>
          </button>
        </div>
        <div class="mt-3 flex flex-wrap items-center gap-3 text-[11px] text-slate-400">
          <span v-for="(meta, key) in { DEADLINE: null, APPLY: null, WRITTEN_TEST: null, INTERVIEW: null, RESULT: null }" :key="key" class="flex items-center gap-1">
            <span class="h-1.5 w-1.5 rounded-full" :class="eventMeta(key).dot" />
            {{ eventMeta(key).label }}
          </span>
        </div>
      </div>

      <div class="self-start rounded-2xl border border-blue-100 bg-blue-50/40 p-4">
        <div class="flex items-center justify-between">
          <p class="text-xs font-bold text-slate-600">{{ selectedDate }}</p>
          <span class="rounded-full bg-white px-2 py-0.5 text-[10px] font-bold text-blue-600">{{ selectedEvents.length }} 项</span>
        </div>
        <ul v-if="selectedEvents.length" class="mt-3 space-y-3">
          <li v-for="event in selectedEvents" :key="event.id" class="flex items-start gap-2">
            <span class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full" :class="eventMeta(event.type).dot" />
            <div class="min-w-0">
              <p class="truncate text-xs font-semibold text-slate-700">{{ event.title }}</p>
              <p class="mt-0.5 text-[11px] text-slate-400">{{ event.time }} · {{ eventMeta(event.type).label }}</p>
            </div>
          </li>
        </ul>
        <p v-else class="mt-3 text-xs leading-5 text-slate-400">这一天没有安排，可以集中处理高匹配岗位或更新简历版本。</p>
        <p class="mt-4 border-t border-blue-100 pt-3 text-[11px] text-slate-400">
          本月共 {{ monthEventCount }} 个事件
        </p>
      </div>
    </div>
  </div>
</template>
