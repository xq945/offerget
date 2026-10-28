<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Menu } from 'lucide-vue-next'

import NotificationBell from '@/components/NotificationBell.vue'
import WorkspaceSidebar from '@/components/WorkspaceSidebar.vue'

const route = useRoute()

const mobileOpen = ref(false)

const todayLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date()),
)

</script>

<template>
  <div class="min-h-screen page-bg text-slate-950">
    <!-- 桌面侧栏 -->
    <aside class="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-blue-100/70 lg:block print:hidden">
      <WorkspaceSidebar />
    </aside>

    <!-- 移动端抽屉 -->
    <transition
      enter-active-class="transition duration-200"
      enter-from-class="opacity-0"
      leave-active-class="transition duration-150"
      leave-to-class="opacity-0"
    >
      <div v-if="mobileOpen" class="fixed inset-0 z-40 lg:hidden print:hidden">
        <div class="absolute inset-0 bg-slate-950/30 backdrop-blur-sm" @click="mobileOpen = false" />
        <div class="absolute inset-y-0 left-0 w-[280px] border-r border-blue-100/70 shadow-2xl">
          <WorkspaceSidebar @click="mobileOpen = false" />
        </div>
      </div>
    </transition>

    <div class="lg:pl-64 print:pl-0">
      <header class="sticky top-0 z-20 border-b border-blue-100/70 bg-white/76 backdrop-blur print:hidden">
        <div class="flex h-16 items-center justify-between gap-4 px-5 sm:px-8 lg:px-10">
          <div class="flex min-w-0 items-center gap-3">
            <button
              class="grid h-9 w-9 shrink-0 place-items-center rounded-lg border border-blue-100 bg-white text-slate-600 lg:hidden"
              @click="mobileOpen = true"
            >
              <Menu class="h-4.5 w-4.5" />
            </button>
            <div class="min-w-0">
              <p class="text-[11px] font-bold uppercase tracking-[0.18em] text-blue-500">Workspace</p>
              <h1 class="truncate text-lg font-black text-slate-950">{{ route.meta.title }}</h1>
            </div>
          </div>

          <div class="flex items-center gap-3">
            <span class="hidden rounded-full border border-blue-100 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-500 xl:block">
              {{ todayLabel }}
            </span>

            <NotificationBell />

          </div>
        </div>
      </header>

      <main class="mx-auto w-full max-w-[1400px] px-5 py-6 sm:px-8 lg:px-10 lg:py-8 print:max-w-none print:p-0">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>
