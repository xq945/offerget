<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  BriefcaseBusiness,
  Lightbulb,
  MessageSquareMore,
  Plus,
  Send,
  Sparkles,
  Trophy,
} from 'lucide-vue-next'

import CalendarGrid from '@/components/CalendarGrid.vue'
import EmptyState from '@/components/EmptyState.vue'
import JobStatusTag from '@/components/JobStatusTag.vue'
import MatchScoreRing from '@/components/MatchScoreRing.vue'
import StatCard from '@/components/StatCard.vue'
import TrendBars from '@/components/TrendBars.vue'
import { fetchCalendar, fetchOverview } from '@/api/dashboard'
import { useUserStore } from '@/stores/user'
import {
  companyInitial,
  companyTone,
  deadlineText,
  deadlineTone,
  eventMeta,
  greeting,
} from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const overview = ref(null)
const calendarEvents = ref([])
const currentYear = ref(new Date().getFullYear())
const currentMonth = ref(new Date().getMonth() + 1)

const stats = computed(() => overview.value?.stats ?? {
  totalJobs: 0,
  appliedCount: 0,
  interviewingCount: 0,
  offerCount: 0,
  weeklyApplied: 0,
})

const cards = computed(() => [
  {
    label: '总岗位数',
    value: stats.value.totalJobs,
    hint: '覆盖互联网、国企、研究所等方向',
    icon: BriefcaseBusiness,
    tone: 'bg-gradient-to-br from-blue-600 to-sky-400',
    badge: `本周新增 ${stats.value.weeklyApplied}`,
  },
  {
    label: '已投递数量',
    value: stats.value.appliedCount,
    hint: '含笔试、面试、Offer 阶段',
    icon: Send,
    tone: 'bg-gradient-to-br from-violet-500 to-indigo-400',
    badge: '',
  },
  {
    label: '面试中数量',
    value: stats.value.interviewingCount,
    hint: '一面、二面、HR 面需要跟进',
    icon: MessageSquareMore,
    tone: 'bg-gradient-to-br from-amber-500 to-orange-400',
    badge: '',
  },
  {
    label: 'Offer 数量',
    value: stats.value.offerCount,
    hint: '继续比较城市、薪资和成长性',
    icon: Trophy,
    tone: 'bg-gradient-to-br from-emerald-500 to-teal-400',
    badge: '',
  },
])

async function loadAll() {
  loading.value = true
  try {
    const [data, events] = await Promise.all([
      fetchOverview(),
      fetchCalendar(currentYear.value, currentMonth.value),
    ])
    overview.value = data
    calendarEvents.value = events
  }
  catch (error) {
    ElMessage.error(error.message || '加载总览失败')
  }
  finally {
    loading.value = false
  }
}

async function changeMonth(year, month) {
  currentYear.value = year
  currentMonth.value = month
  try {
    calendarEvents.value = await fetchCalendar(year, month)
  }
  catch (error) {
    ElMessage.error(error.message || '加载台历失败')
  }
}

function goCreateJob() {
  router.push({ name: 'jobs', query: { create: '1' } })
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载工作台…" class="space-y-5">
    <template v-if="overview">
      <!-- 欢迎区 -->
      <section class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-blue-600 via-blue-500 to-sky-400 p-6 text-white shadow-[0_24px_60px_rgba(37,99,235,0.24)] sm:p-8">
        <div class="pointer-events-none absolute -right-16 -top-20 h-56 w-56 rounded-full border border-white/20" />
        <div class="pointer-events-none absolute -bottom-24 right-24 h-48 w-48 rounded-full bg-white/10 blur-2xl" />
        <div class="relative flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full border border-white/25 bg-white/10 px-3.5 py-1.5 text-xs font-semibold backdrop-blur">
              <Sparkles class="h-3.5 w-3.5" />
              {{ userStore.graduationYear }} 届校招
            </span>
            <h2 class="mt-3 text-2xl font-black sm:text-3xl">
              {{ greeting() }}，{{ userStore.displayName }}
            </h2>
            <p class="mt-2 text-sm text-blue-50/85">
              投递节奏持续推进中 · 今天有 {{ overview.todos.length }} 项待办，{{ overview.deadlines.length }} 个岗位即将截止
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <button
              class="inline-flex items-center gap-1.5 rounded-xl bg-white px-4 py-2.5 text-sm font-bold text-blue-700 shadow-sm transition hover:bg-blue-50"
              @click="goCreateJob"
            >
              <Plus class="h-4 w-4" />
              添加岗位
            </button>
            <button
              class="inline-flex items-center gap-1.5 rounded-xl border border-white/30 bg-white/10 px-4 py-2.5 text-sm font-bold text-white backdrop-blur transition hover:bg-white/20"
              @click="router.push({ name: 'ai-match' })"
            >
              <Sparkles class="h-4 w-4" />
              AI 匹配
            </button>
          </div>
        </div>
      </section>

      <!-- 统计卡 -->
      <section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          v-for="card in cards"
          :key="card.label"
          :label="card.label"
          :value="card.value"
          :hint="card.hint"
          :icon="card.icon"
          :tone="card.tone"
          :badge="card.badge"
        />
      </section>

      <!-- 趋势 + 即将截止 -->
      <section class="grid gap-5 xl:grid-cols-[1.55fr_1fr]">
        <div class="soft-card p-5 sm:p-6">
          <TrendBars :trend="overview.trend" />
        </div>

        <div class="soft-card flex flex-col p-5 sm:p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-bold text-slate-800">3 天内截止</p>
              <p class="mt-1 text-xs text-slate-400">临近的投递节点优先处理</p>
            </div>
            <span class="rounded-full bg-rose-50 px-2.5 py-1 text-[11px] font-bold text-rose-500">
              {{ overview.deadlines.length }} 个
            </span>
          </div>

          <div v-if="overview.deadlines.length" class="mt-4 flex-1 space-y-2.5">
            <button
              v-for="item in overview.deadlines"
              :key="item.jobId"
              class="flex w-full items-center gap-3 rounded-xl border border-blue-50 bg-white/70 p-3 text-left transition hover:border-blue-200 hover:bg-white"
              @click="router.push({ name: 'jobs', query: { keyword: item.company } })"
            >
              <span
                class="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-gradient-to-br text-sm font-bold text-white"
                :class="companyTone(item.company)"
              >
                {{ companyInitial(item.company) }}
              </span>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-bold text-slate-800">{{ item.company }} · {{ item.title }}</p>
                <p class="mt-0.5 text-xs font-semibold" :class="deadlineTone(item.daysLeft)">
                  {{ deadlineText(item.daysLeft) }}
                </p>
              </div>
              <MatchScoreRing v-if="item.matchScore !== null" :score="item.matchScore" :size="40" :stroke="11" :show-label="false" />
              <JobStatusTag v-else :status="item.status" size="sm" />
            </button>
          </div>
          <EmptyState
            v-else
            class="mt-4 flex-1"
            title="近 3 天没有即将截止的岗位"
            description="可以优先处理高匹配的长期机会，或补充新的目标岗位。"
          />
        </div>
      </section>

      <!-- 待办 + AI 建议 -->
      <section class="grid gap-5 xl:grid-cols-2">
        <div class="soft-card p-5 sm:p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-bold text-slate-800">今日待办</p>
              <p class="mt-1 text-xs text-slate-400">根据截止、笔面试与跟进状态自动生成</p>
            </div>
            <span class="rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-bold text-blue-600">
              {{ overview.todos.length }} 项
            </span>
          </div>

          <ul v-if="overview.todos.length" class="mt-4 space-y-3">
            <li
              v-for="item in overview.todos"
              :key="item.id"
              class="flex items-start gap-3 rounded-xl border border-blue-50 bg-white/70 p-3"
            >
              <span class="w-11 shrink-0 pt-0.5 text-xs font-bold text-blue-600">{{ item.time }}</span>
              <div class="min-w-0 flex-1 border-l-2 border-blue-100 pl-3">
                <p class="truncate text-sm font-semibold text-slate-700">{{ item.title }}</p>
                <p class="mt-0.5 truncate text-xs text-slate-400">{{ item.tag }}</p>
              </div>
              <span class="shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold" :class="eventMeta(item.type).tone">
                {{ eventMeta(item.type).label }}
              </span>
            </li>
          </ul>
          <EmptyState
            v-else
            class="mt-4"
            title="今天没有必须处理的岗位事项"
            description="可以继续补充高匹配岗位，或更新简历版本。"
          />
        </div>

        <div class="soft-card p-5 sm:p-6">
          <div class="flex items-center gap-2">
            <span class="grid h-8 w-8 place-items-center rounded-lg bg-amber-50 text-amber-500">
              <Lightbulb class="h-4 w-4" />
            </span>
            <div>
              <p class="text-sm font-bold text-slate-800">AI 建议</p>
              <p class="text-xs text-slate-400">基于截止日期、匹配度和简历版本生成</p>
            </div>
          </div>

          <ul class="mt-4 space-y-3">
            <li v-for="(suggestion, index) in overview.suggestions" :key="index" class="flex gap-3">
              <span class="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-blue-50 text-[11px] font-bold text-blue-600">
                {{ index + 1 }}
              </span>
              <p class="text-sm leading-6 text-slate-600">{{ suggestion }}</p>
            </li>
          </ul>

          <button
            class="mt-4 inline-flex items-center gap-1 text-xs font-bold text-blue-600 transition hover:text-blue-700"
            @click="router.push({ name: 'ai-match' })"
          >
            去做一次 AI 匹配
            <ArrowRight class="h-3.5 w-3.5" />
          </button>
        </div>
      </section>

      <!-- 台历 -->
      <CalendarGrid
        :year="currentYear"
        :month="currentMonth"
        :events="calendarEvents"
        @change="changeMonth"
      />
    </template>

    <EmptyState
      v-else-if="!loading"
      title="工作台加载失败"
      description="请确认后端服务已启动，然后重试。"
      action-label="重新加载"
      @action="loadAll"
    />
  </div>
</template>
