<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  Bell,
  BriefcaseBusiness,
  CalendarClock,
  CheckCircle2,
  Database,
  FileText,
  LayoutDashboard,
  ListChecks,
  Send,
  Sparkles,
  Target,
  Wand2,
  Zap,
} from 'lucide-vue-next'

import BrandLogo from '@/components/BrandLogo.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const primaryCta = computed(() => (userStore.isLoggedIn ? '进入工作台' : '登录 / 注册'))
const primaryTarget = computed(() => (userStore.isLoggedIn ? '/app/dashboard' : '/login'))

const features = [
  {
    icon: Send,
    title: '我的投递',
    desc: '集中管理目标岗位、截止日期、投递状态与下一步动作，不再靠表格手工维护。',
    tone: 'bg-blue-50 text-blue-600',
    to: '/app/jobs',
  },
  {
    icon: FileText,
    title: '简历工作区',
    desc: '按岗位方向维护多版简历，投递时精准绑定，面试反馈沉淀到对应版本。',
    tone: 'bg-violet-50 text-violet-600',
    to: '/app/resumes',
  },
  {
    icon: Sparkles,
    title: 'AI 匹配',
    desc: '识别 JD 关键词，输出命中与缺失项，明确简历该补充什么项目表达。',
    tone: 'bg-amber-50 text-amber-600',
    to: '/app/ai-match',
  },
  {
    icon: Wand2,
    title: '简历优化',
    desc: '结合简历、岗位 JD 与匹配报告，产出可直接复制的改写方向。',
    tone: 'bg-emerald-50 text-emerald-600',
    to: '/app/optimize',
  },
]

const values = [
  {
    icon: LayoutDashboard,
    title: '统一管理',
    desc: '岗位、简历、AI 分析与投递进度收进一个工作台，用一条主线串起整个校招流程。',
  },
  {
    icon: Database,
    title: '数据闭环',
    desc: '账号、岗位与简历版本自动关联，换设备登录后依旧能接上之前的进度。',
  },
  {
    icon: ListChecks,
    title: '行动清单',
    desc: '从今日待办到投递复盘，每一个动作都有明确的结果，而不是停留在"投了很多"。',
  },
]

const mockStats = [
  { label: '投递岗位', value: 28, hint: '本周 +6', tone: 'text-blue-600' },
  { label: '面试数量', value: 15, hint: '进行中 +3', tone: 'text-violet-600' },
  { label: '笔试提醒', value: 7, hint: '已同步 +2', tone: 'text-amber-600' },
  { label: 'Offer', value: 2, hint: '稳定 +1', tone: 'text-emerald-600' },
]

const mockBars = [42, 68, 52, 84, 46, 72, 34]

const mockDeadlines = [
  { company: '字节跳动', role: '产品经理校招生', left: '剩 2 天' },
  { company: '腾讯', role: '数据分析师', left: '剩 3 天' },
  { company: '阿里巴巴', role: '解决方案架构师', left: '剩 6 天' },
]

const mockMenu = ['总览', '我的投递', '简历库', 'AI 匹配']
</script>

<template>
  <div class="relative">
    <!-- 背景光晕 -->
    <div class="pointer-events-none absolute inset-x-0 top-0 h-[720px] overflow-hidden">
      <div class="absolute -left-40 top-10 h-[30rem] w-[30rem] rounded-full bg-blue-200/40 blur-3xl" />
      <div class="absolute right-[-10rem] top-32 h-[34rem] w-[34rem] rounded-full bg-sky-200/40 blur-3xl" />
      <div class="absolute left-1/2 top-[-14rem] h-[28rem] w-[52rem] -translate-x-1/2 rounded-full bg-white/70 blur-3xl" />
    </div>

    <!-- 顶部导航 -->
    <header class="sticky top-0 z-50 border-b border-blue-100/70 bg-white/76 backdrop-blur">
      <div class="mx-auto flex h-16 w-full max-w-[1240px] items-center justify-between px-5 sm:px-8">
        <BrandLogo />
        <div class="flex items-center gap-2.5">
          <router-link
            :to="primaryTarget"
            class="hidden items-center gap-1.5 rounded-lg border border-blue-100 bg-white/80 px-4 py-2 text-sm font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700 sm:inline-flex"
          >
            {{ userStore.isLoggedIn ? '我的工作台' : '登录 / 注册' }}
          </router-link>
          <router-link
            :to="userStore.isLoggedIn ? '/app/dashboard' : '/login'"
            class="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-r from-blue-600 to-sky-500 px-4 py-2 text-sm font-bold text-white shadow-[0_12px_24px_rgba(37,99,235,0.22)] transition hover:brightness-105"
          >
            进入工作台
            <ArrowRight class="h-4 w-4" />
          </router-link>
        </div>
      </div>
    </header>

    <main class="relative">
      <!-- Hero -->
      <section class="mx-auto w-full max-w-[1240px] px-5 pb-16 pt-14 sm:px-8 sm:pt-20">
        <div class="grid items-center gap-12 lg:grid-cols-[1.02fr_1fr]">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full border border-blue-200 bg-white/82 px-4 py-2 text-xs font-semibold text-blue-700 shadow-sm backdrop-blur">
              <Sparkles class="h-3.5 w-3.5" />
              专为大学生打造的校招投递工作台
            </span>

            <h1 class="mt-6 text-[40px] font-black leading-[1.08] tracking-tight text-slate-950 sm:text-[52px] xl:text-[60px]">
              别再用
              <span class="relative inline-block text-blue-600">
                Excel
                <svg class="absolute -bottom-1.5 left-0 h-3.5 w-full text-blue-500" viewBox="0 0 100 12" preserveAspectRatio="none" aria-hidden="true">
                  <path d="M2 9 Q 50 1 98 7" stroke="currentColor" stroke-width="4" fill="none" stroke-linecap="round" />
                </svg>
              </span>
              <br class="hidden sm:block" />
              硬扛校招了
            </h1>

            <p class="mt-5 text-sm font-bold tracking-wide text-slate-500 sm:text-base">
              科学规划 <span class="mx-1 text-blue-300">·</span> 精准匹配
              <span class="mx-1 text-blue-300">·</span> 高效投递
              <span class="mx-1 text-blue-300">·</span> 拿下心仪 Offer
            </p>

            <p class="mt-5 max-w-xl text-sm leading-7 text-slate-600 sm:text-base">
              OfferGet 把目标岗位、简历版本、JD 分析、AI 匹配、面试跟进和投递进度整理成一个清晰的工作台，
              让你每天打开就知道该推进什么，而不是翻十几个表格找信息。
            </p>

            <div class="mt-8 flex flex-wrap items-center gap-3">
              <router-link
                to="/login"
                class="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-blue-600 to-sky-500 px-6 py-3.5 text-sm font-bold text-white shadow-[0_18px_34px_rgba(37,99,235,0.26)] transition hover:brightness-105"
              >
                <Send class="h-4 w-4" />
                开始管理岗位
              </router-link>
              <router-link
                :to="userStore.isLoggedIn ? '/app/dashboard' : '/login'"
                class="inline-flex items-center gap-2 rounded-xl border border-blue-100 bg-white/85 px-6 py-3.5 text-sm font-bold text-slate-700 shadow-sm backdrop-blur transition hover:border-blue-200 hover:text-blue-700"
              >
                <Zap class="h-4 w-4 text-blue-500" />
                免费体验工作台
              </router-link>
            </div>

            <div class="mt-7 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs font-semibold text-slate-500">
              <span class="inline-flex items-center gap-1.5">
                <CheckCircle2 class="h-4 w-4 text-emerald-500" />
                登录即用，无需配置
              </span>
              <span class="inline-flex items-center gap-1.5">
                <Database class="h-4 w-4 text-blue-500" />
                数据存在自己的部署环境
              </span>
              <span class="inline-flex items-center gap-1.5">
                <Bell class="h-4 w-4 text-amber-500" />
                截止与面试自动提醒
              </span>
            </div>
          </div>

          <!-- 产品 Mockup -->
          <div class="relative mx-auto w-full max-w-[660px]">
            <div class="rounded-[26px] border border-blue-100 bg-white/85 p-3 shadow-[0_30px_80px_rgba(37,99,235,0.18)] backdrop-blur">
              <div class="flex items-center gap-1.5 px-2 pb-2.5">
                <span class="h-2 w-2 rounded-full bg-rose-300" />
                <span class="h-2 w-2 rounded-full bg-amber-300" />
                <span class="h-2 w-2 rounded-full bg-emerald-300" />
                <span class="ml-2 flex-1 rounded-full bg-slate-100 px-3 py-1 text-[9px] text-slate-400">
                  offerget.local/app/dashboard
                </span>
              </div>

              <div class="overflow-hidden rounded-2xl border border-blue-50 bg-[#f7faff] p-3">
                <div class="flex gap-3">
                  <!-- 迷你侧栏 -->
                  <aside class="hidden w-28 shrink-0 rounded-xl bg-white p-2 shadow-sm sm:block">
                    <div class="flex items-center gap-1.5 px-1 pb-2">
                      <span class="grid h-5 w-5 place-items-center rounded-md bg-gradient-to-br from-blue-600 to-sky-400 text-white">
                        <BriefcaseBusiness class="h-3 w-3" />
                      </span>
                      <span class="text-[10px] font-black text-slate-800">OfferGet</span>
                    </div>
                    <div
                      v-for="(item, index) in mockMenu"
                      :key="item"
                      class="mb-0.5 flex items-center gap-1.5 rounded-md px-1.5 py-1 text-[9px] font-semibold"
                      :class="index === 0 ? 'bg-blue-600 text-white' : 'text-slate-400'"
                    >
                      <span class="h-1 w-1 rounded-full" :class="index === 0 ? 'bg-white' : 'bg-slate-300'" />
                      {{ item }}
                    </div>
                    <div class="mt-3 rounded-lg bg-blue-50/70 p-1.5">
                      <p class="text-[8px] font-bold text-blue-600">2027 届校招</p>
                      <p class="mt-0.5 text-[7px] text-slate-400">投递节奏进行中</p>
                    </div>
                  </aside>

                  <!-- 迷你内容区 -->
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between">
                      <div>
                        <p class="text-[8px] font-bold text-blue-500">校招总览</p>
                        <p class="text-[11px] font-black text-slate-800">今天也在推进投递</p>
                      </div>
                      <span class="rounded-full bg-white px-2 py-0.5 text-[7px] font-semibold text-slate-400">本周</span>
                    </div>

                    <div class="mt-2 grid grid-cols-2 gap-1.5">
                      <div v-for="stat in mockStats" :key="stat.label" class="rounded-lg bg-white p-1.5 shadow-sm">
                        <p class="text-[7px] font-semibold text-slate-400">{{ stat.label }}</p>
                        <p class="text-sm font-black" :class="stat.tone">{{ stat.value }}</p>
                        <p class="text-[6px] text-slate-400">{{ stat.hint }}</p>
                      </div>
                    </div>

                    <div class="mt-1.5 rounded-lg bg-white p-2 shadow-sm">
                      <div class="flex items-center justify-between">
                        <p class="text-[8px] font-bold text-slate-600">本周投递趋势</p>
                        <p class="text-[7px] text-slate-400">6 个</p>
                      </div>
                      <div class="mt-1.5 flex h-14 items-end gap-1">
                        <div
                          v-for="(bar, index) in mockBars"
                          :key="index"
                          class="flex-1 rounded-t-sm"
                          :class="index === 3 ? 'bg-gradient-to-t from-blue-600 to-sky-400' : 'bg-blue-100'"
                          :style="{ height: `${bar}%` }"
                        />
                      </div>
                      <div class="mt-1 flex justify-between text-[6px] text-slate-300">
                        <span v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day">{{ day }}</span>
                      </div>
                    </div>

                    <div class="mt-1.5 rounded-lg bg-white p-2 shadow-sm">
                      <div class="flex items-center justify-between">
                        <p class="text-[8px] font-bold text-slate-600">3 天内截止</p>
                        <span class="rounded-full bg-rose-50 px-1.5 py-0.5 text-[6px] font-bold text-rose-500">3 个</span>
                      </div>
                      <div
                        v-for="item in mockDeadlines"
                        :key="item.company"
                        class="mt-1 flex items-center gap-1.5 rounded-md bg-blue-50/40 px-1.5 py-1"
                      >
                        <span class="grid h-4 w-4 place-items-center rounded bg-gradient-to-br from-blue-500 to-sky-400 text-[6px] font-bold text-white">
                          {{ item.company.slice(0, 1) }}
                        </span>
                        <div class="min-w-0 flex-1">
                          <p class="truncate text-[7px] font-semibold text-slate-600">{{ item.company }}</p>
                          <p class="truncate text-[6px] text-slate-400">{{ item.role }}</p>
                        </div>
                        <span class="shrink-0 text-[6px] font-bold text-rose-400">{{ item.left }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 浮动：今日待办 -->
            <div class="absolute -left-7 bottom-2 hidden w-44 rounded-2xl border border-blue-100 bg-white/95 p-3 shadow-[0_18px_40px_rgba(37,99,235,0.18)] backdrop-blur sm:block">
              <div class="flex items-center gap-1.5">
                <CalendarClock class="h-3.5 w-3.5 text-blue-500" />
                <p class="text-[10px] font-bold text-slate-700">今日待办</p>
              </div>
              <div class="mt-2 space-y-1.5">
                <div class="rounded-lg bg-blue-50/70 px-2 py-1.5">
                  <p class="text-[8px] font-semibold text-slate-600">产品体验实习 · 截止今天</p>
                </div>
                <div class="rounded-lg bg-amber-50/70 px-2 py-1.5">
                  <p class="text-[8px] font-semibold text-slate-600">训练营笔试 · 19:00</p>
                </div>
              </div>
            </div>

            <!-- 浮动：AI 匹配 -->
            <div class="absolute -right-6 -top-6 hidden w-44 rounded-2xl border border-blue-100 bg-white/95 p-3 shadow-[0_18px_40px_rgba(37,99,235,0.18)] backdrop-blur md:block">
              <div class="flex items-center justify-between">
                <p class="text-[10px] font-bold text-slate-700">AI 匹配建议</p>
                <span class="rounded-full bg-emerald-50 px-1.5 py-0.5 text-[7px] font-bold text-emerald-600">已更新</span>
              </div>
              <div class="mt-2 space-y-2">
                <div>
                  <div class="flex items-center justify-between text-[8px]">
                    <span class="text-slate-500">产品运营</span>
                    <span class="font-bold text-blue-600">82%</span>
                  </div>
                  <div class="mt-1 h-1 rounded-full bg-blue-100">
                    <div class="h-1 w-[82%] rounded-full bg-gradient-to-r from-blue-600 to-sky-400" />
                  </div>
                </div>
                <div>
                  <div class="flex items-center justify-between text-[8px]">
                    <span class="text-slate-500">后端开发</span>
                    <span class="font-bold text-amber-500">66%</span>
                  </div>
                  <div class="mt-1 h-1 rounded-full bg-amber-100">
                    <div class="h-1 w-[66%] rounded-full bg-gradient-to-r from-amber-400 to-orange-400" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 功能卡片 -->
      <section class="mx-auto w-full max-w-[1240px] px-5 pb-16 sm:px-8">
        <div class="mb-7 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p class="text-xs font-bold uppercase tracking-[0.18em] text-blue-500">核心能力</p>
            <h2 class="mt-2 text-2xl font-black text-slate-950 sm:text-3xl">从投递到复盘，一条主线走完</h2>
          </div>
          <p class="max-w-md text-sm leading-6 text-slate-500">
            每一个模块都围绕"下一步该做什么"设计，不做只展示不行动的报表。
          </p>
        </div>

        <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <article
            v-for="feature in features"
            :key="feature.title"
            class="group rounded-2xl border border-blue-100 bg-white/78 p-5 shadow-[0_10px_24px_rgba(37,99,235,0.06)] backdrop-blur transition duration-200 hover:-translate-y-1 hover:border-blue-200 hover:shadow-[0_20px_40px_rgba(37,99,235,0.12)]"
          >
            <span class="grid h-11 w-11 place-items-center rounded-xl" :class="feature.tone">
              <component :is="feature.icon" class="h-5 w-5" />
            </span>
            <h3 class="mt-4 text-base font-bold text-slate-900">{{ feature.title }}</h3>
            <p class="mt-2 text-xs leading-6 text-slate-500">{{ feature.desc }}</p>
            <router-link
              :to="userStore.isLoggedIn ? feature.to : '/login'"
              class="mt-4 inline-flex items-center gap-1 text-[11px] font-bold text-blue-600 transition hover:text-blue-700"
            >
              查看模块
              <ArrowRight class="h-3 w-3 transition group-hover:translate-x-0.5" />
            </router-link>
          </article>
        </div>
      </section>

      <!-- 价值主张 -->
      <section class="border-y border-blue-100/70 bg-white/50 py-16 backdrop-blur">
        <div class="mx-auto w-full max-w-[1240px] px-5 sm:px-8">
          <div class="grid gap-5 md:grid-cols-3">
            <div v-for="(value, index) in values" :key="value.title" class="rounded-2xl border border-blue-100/80 bg-white/80 p-6">
              <div class="flex items-center justify-between">
                <span class="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-blue-600">
                  <component :is="value.icon" class="h-5 w-5" />
                </span>
                <span class="text-3xl font-black text-blue-100">0{{ index + 1 }}</span>
              </div>
              <h3 class="mt-4 text-base font-bold text-slate-900">{{ value.title }}</h3>
              <p class="mt-2 text-sm leading-6 text-slate-500">{{ value.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 底部 CTA -->
      <section class="mx-auto w-full max-w-[1240px] px-5 py-16 sm:px-8">
        <div class="relative overflow-hidden rounded-[28px] bg-gradient-to-br from-blue-600 via-blue-500 to-sky-400 px-6 py-12 text-center text-white shadow-[0_30px_70px_rgba(37,99,235,0.28)] sm:px-10">
          <div class="pointer-events-none absolute -left-16 -top-20 h-56 w-56 rounded-full border border-white/20" />
          <div class="pointer-events-none absolute -bottom-24 right-10 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
          <div class="relative">
            <span class="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/10 px-4 py-1.5 text-xs font-semibold backdrop-blur">
              <Target class="h-3.5 w-3.5" />
              2027 届校招进行时
            </span>
            <h2 class="mt-4 text-2xl font-black sm:text-3xl">把下一个 Offer，放进进度表里</h2>
            <p class="mx-auto mt-3 max-w-lg text-sm leading-6 text-blue-50/85">
              现在创建账号，导入第一批目标岗位，用 AI 匹配先看清差距，再决定把时间投在哪里。
            </p>
            <div class="mt-7 flex flex-wrap items-center justify-center gap-3">
              <router-link
                to="/login"
                class="inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 text-sm font-bold text-blue-700 shadow-sm transition hover:bg-blue-50"
              >
                免费开始使用
                <ArrowRight class="h-4 w-4" />
              </router-link>
              <span class="text-xs text-blue-50/70">演示账号 demo / demo123</span>
            </div>
          </div>
        </div>
      </section>
    </main>

    <footer class="border-t border-blue-100/70 bg-white/60">
      <div class="mx-auto flex w-full max-w-[1240px] flex-col gap-5 px-5 py-8 sm:px-8 md:flex-row md:items-center md:justify-between">
        <div class="flex items-center gap-3">
          <BrandLogo size="sm" />
          <span class="text-xs text-slate-400">大学生校招投递管理工具</span>
        </div>
        <div class="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-slate-400">
          <span>Vue 3 + Spring Boot 实现</span>
          <span>本地规则匹配</span>
          <span>个人自用部署</span>
        </div>
      </div>
    </footer>
  </div>
</template>
