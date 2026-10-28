<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  CalendarClock,
  CircleAlert,
  MessageSquareMore,
  PenLine,
} from 'lucide-vue-next'

import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/api/notifications'

const router = useRouter()

const visible = ref(false)
const loading = ref(false)
const items = ref([])
const unreadCount = ref(0)

let timer = null

async function load() {
  loading.value = true
  try {
    const data = await fetchNotifications()
    items.value = data.items
    unreadCount.value = data.unreadCount
  }
  catch {
    // 通知加载失败不打断主流程
  }
  finally {
    loading.value = false
  }
}

async function handleAllRead() {
  if (!unreadCount.value) {
    return
  }
  try {
    await markAllNotificationsRead()
    items.value = items.value.map((item) => ({ ...item, read: true }))
    unreadCount.value = 0
    ElMessage.success('已全部标为已读')
  }
  catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

async function handleClick(item) {
  if (!item.read) {
    try {
      await markNotificationRead(item.key)
      item.read = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    catch {
      // 已失效的提醒忽略即可
    }
  }
  visible.value = false
  if (item.company) {
    router.push({ name: 'jobs', query: { keyword: item.company } })
  }
}

function iconOf(item) {
  if (item.type.startsWith('INTERVIEW')) {
    return MessageSquareMore
  }
  if (item.type.startsWith('TEST')) {
    return PenLine
  }
  if (item.type === 'OVERDUE') {
    return CircleAlert
  }
  return CalendarClock
}

function toneOf(level) {
  if (level === 'HIGH') {
    return 'bg-rose-50 text-rose-500'
  }
  if (level === 'MEDIUM') {
    return 'bg-amber-50 text-amber-500'
  }
  return 'bg-blue-50 text-blue-500'
}

onMounted(() => {
  load()
  timer = setInterval(load, 60000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <el-popover
    v-model:visible="visible"
    trigger="click"
    placement="bottom-end"
    :width="400"
    popper-class="notification-popover"
    @show="load"
  >
    <template #reference>
      <button
        class="relative grid h-9 w-9 place-items-center rounded-full border border-blue-100 bg-white text-slate-500 transition hover:text-blue-600"
        title="提醒"
      >
        <Bell class="h-4.5 w-4.5" />
        <span
          v-if="unreadCount > 0"
          class="absolute -right-1.5 -top-1.5 grid min-w-4.5 place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold leading-4.5 text-white ring-2 ring-white"
        >
          {{ unreadCount > 99 ? '99+' : unreadCount }}
        </span>
      </button>
    </template>

    <div class="-m-3">
      <div class="flex items-center justify-between border-b border-blue-50 px-4 py-3">
        <div class="flex items-center gap-2">
          <p class="text-sm font-bold text-slate-800">提醒</p>
          <span v-if="unreadCount > 0" class="rounded-full bg-rose-50 px-2 py-0.5 text-[10px] font-bold text-rose-500">
            {{ unreadCount }} 条未读
          </span>
        </div>
        <button
          class="text-[11px] font-semibold transition"
          :class="unreadCount ? 'text-blue-600 hover:text-blue-700' : 'cursor-not-allowed text-slate-300'"
          :disabled="!unreadCount"
          @click="handleAllRead"
        >
          全部已读
        </button>
      </div>

      <div class="max-h-[380px] overflow-y-auto">
        <ul v-if="items.length" class="divide-y divide-blue-50/70">
          <li v-for="item in items" :key="item.key">
            <button
              class="flex w-full items-start gap-3 px-4 py-3 text-left transition hover:bg-blue-50/40"
              @click="handleClick(item)"
            >
              <span class="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-lg" :class="toneOf(item.level)">
                <component :is="iconOf(item)" class="h-4 w-4" />
              </span>
              <div class="min-w-0 flex-1">
                <p class="truncate text-xs font-bold" :class="item.read ? 'text-slate-500' : 'text-slate-800'">
                  {{ item.title }}
                </p>
                <p class="mt-0.5 line-clamp-2 text-[11px] leading-5 text-slate-400">
                  {{ item.description }}
                </p>
              </div>
              <span v-if="!item.read" class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-rose-500" />
            </button>
          </li>
        </ul>

        <div v-else class="flex flex-col items-center px-4 py-10 text-center">
          <span class="grid h-10 w-10 place-items-center rounded-full bg-blue-50 text-blue-300">
            <Bell class="h-4.5 w-4.5" />
          </span>
          <p class="mt-3 text-xs text-slate-400">暂无提醒</p>
          <p class="mt-1 text-[11px] text-slate-300">面试、笔试与岗位截止前会在这里提示</p>
        </div>
      </div>

      <div class="border-t border-blue-50 px-4 py-2">
        <p class="text-[10px] text-slate-300">提醒每天 0 点重置，已读状态按天计算</p>
      </div>
    </div>
  </el-popover>
</template>
