<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Building2,
  FilePen,
  FileText,
  History,
  LayoutDashboard,
  LogOut,
  MessagesSquare,
  PenLine,
  Send,
  Sparkle,
  Sparkles,
  UserRound,
  Wand2,
} from 'lucide-vue-next'

import BrandLogo from '@/components/BrandLogo.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const groups = [
  {
    title: '工作台',
    items: [
      { name: 'dashboard', label: '校招总览', icon: LayoutDashboard },
      { name: 'jobs', label: '我的投递', icon: Send },
    ],
  },
  {
    title: '求职工具',
    items: [
      { name: 'companies', label: '校招信息库', icon: Building2 },
      { name: 'resumes', label: '简历管理', icon: FileText },
      { name: 'resume-builder', label: '简历制作', icon: PenLine },
      { name: 'resume-autofill', label: '自动填充', icon: Wand2 },
      { name: 'ai-match', label: 'AI 匹配', icon: Sparkles },
      { name: 'optimize', label: '简历优化', icon: Sparkle },
      { name: 'resume-customize', label: '简历定制', icon: FilePen },
      { name: 'mock-interview', label: '模拟面试', icon: MessagesSquare },
      { name: 'review', label: '投递复盘', icon: History },
    ],
  },
  {
    title: '账号',
    items: [
      { name: 'profile', label: '个人中心', icon: UserRound },
    ],
  },
]

function isActive(item) {
  return route.name === item.name
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }
  catch {
    return
  }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="flex h-full flex-col bg-white/85 backdrop-blur">
    <div class="flex h-16 shrink-0 items-center px-5">
      <BrandLogo size="sm" />
    </div>

    <nav class="flex-1 space-y-6 overflow-y-auto px-3 pb-6 pt-2">
      <div v-for="group in groups" :key="group.title">
        <p class="px-3 pb-2 text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">
          {{ group.title }}
        </p>
        <router-link
          v-for="item in group.items"
          :key="item.name"
          :to="item.to || { name: item.name }"
          class="group mb-1 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition-all duration-200"
          :class="isActive(item)
            ? 'bg-blue-600 text-white shadow-[0_12px_24px_rgba(37,99,235,0.24)]'
            : 'text-slate-600 hover:bg-blue-50 hover:text-blue-700'"
        >
          <component :is="item.icon" class="h-4.5 w-4.5 shrink-0" />
          <span class="truncate">{{ item.label }}</span>
        </router-link>
      </div>
    </nav>

    <div class="shrink-0 border-t border-blue-100/70 p-3">
      <div class="flex items-center gap-1.5 rounded-2xl bg-gradient-to-br from-blue-50 to-sky-50/60 p-2.5">
        <router-link
          :to="{ name: 'profile' }"
          class="flex min-w-0 flex-1 items-center gap-2.5 rounded-xl p-0.5 transition hover:opacity-80"
        >
          <span class="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-gradient-to-br from-blue-600 to-sky-400 text-sm font-bold text-white">
            {{ userStore.displayName.slice(0, 1) }}
          </span>
          <div class="min-w-0">
            <p class="truncate text-sm font-bold text-slate-800">{{ userStore.displayName }}</p>
            <p class="truncate text-xs text-slate-500">{{ userStore.graduationYear }} 届校招</p>
          </div>
        </router-link>
        <button
          class="grid h-8 w-8 shrink-0 place-items-center rounded-lg text-slate-400 transition hover:bg-white hover:text-rose-500"
          title="退出登录"
          @click="logout"
        >
          <LogOut class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>
