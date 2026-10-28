<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  FileText,
  Lightbulb,
  MessageSquareMore,
  Send,
  TrendingUp,
  Trophy,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import StatCard from '@/components/StatCard.vue'
import { fetchReview } from '@/api/review'

const router = useRouter()

const loading = ref(true)
const review = ref(null)

const stages = computed(() => {
  const funnel = review.value?.funnel
  if (!funnel) {
    return []
  }
  const total = Math.max(funnel.total, 1)
  return [
    { key: 'total', label: '全部岗位', value: funnel.total, rate: 100, tone: 'from-slate-400 to-slate-300' },
    { key: 'applied', label: '已投递', value: funnel.applied, rate: funnel.applyRate, tone: 'from-blue-600 to-sky-400' },
    { key: 'written', label: '进入笔试', value: funnel.writtenTest, rate: funnel.writtenRate, tone: 'from-violet-500 to-indigo-400' },
    { key: 'interview', label: '进入面试', value: funnel.interview, rate: funnel.interviewRate, tone: 'from-amber-500 to-orange-400' },
    { key: 'offer', label: '拿到 Offer', value: funnel.offer, rate: funnel.offerRate, tone: 'from-emerald-500 to-teal-400' },
  ].map((stage) => ({
    ...stage,
    width: Math.max((stage.value / total) * 100, stage.value > 0 ? 8 : 0),
  }))
})

const cards = computed(() => {
  const funnel = review.value?.funnel
  return [
    {
      label: '管理岗位',
      value: funnel?.total ?? 0,
      hint: '覆盖所有已录入的目标岗位',
      icon: FileText,
      tone: 'bg-gradient-to-br from-slate-500 to-slate-400',
      badge: '',
    },
    {
      label: '已投递',
      value: funnel?.applied ?? 0,
      hint: `投递覆盖率 ${funnel?.applyRate ?? 0}%`,
      icon: Send,
      tone: 'bg-gradient-to-br from-blue-600 to-sky-400',
      badge: '',
    },
    {
      label: '进入面试',
      value: funnel?.interview ?? 0,
      hint: `笔试 → 面试 ${funnel?.interviewRate ?? 0}%`,
      icon: MessageSquareMore,
      tone: 'bg-gradient-to-br from-amber-500 to-orange-400',
      badge: '',
    },
    {
      label: 'Offer',
      value: funnel?.offer ?? 0,
      hint: `面试 → Offer ${funnel?.offerRate ?? 0}%`,
      icon: Trophy,
      tone: 'bg-gradient-to-br from-emerald-500 to-teal-400',
      badge: '',
    },
  ]
})

const monthlyMax = computed(() =>
  Math.max(1, ...(review.value?.monthly || []).map((point) => point.count)),
)

async function loadReview() {
  loading.value = true
  try {
    review.value = await fetchReview()
  }
  catch (error) {
    ElMessage.error(error.message || '加载复盘数据失败')
  }
  finally {
    loading.value = false
  }
}

onMounted(loadReview)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在生成投递复盘…" class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">投递复盘</h2>
        <p class="mt-1 text-sm text-slate-500">用转化率和关键词缺口，看清投递节奏卡在哪一步。</p>
      </div>
      <button
        class="inline-flex items-center justify-center gap-1.5 rounded-xl border border-blue-100 bg-white px-4 py-2.5 text-sm font-bold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
        @click="router.push({ name: 'jobs' })"
      >
        返回投递列表
        <ArrowRight class="h-4 w-4" />
      </button>
    </section>

    <template v-if="review">
      <section v-if="review.funnel.total > 0" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
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

      <div v-if="review.funnel.total > 0" class="grid gap-5 xl:grid-cols-[1.25fr_1fr]">
        <!-- 漏斗 -->
        <section class="soft-card p-5 sm:p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-bold text-slate-800">投递漏斗</p>
              <p class="mt-1 text-xs text-slate-400">按岗位当前状态与事件记录推算各环节数量</p>
            </div>
            <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
              <TrendingUp class="h-4.5 w-4.5" />
            </span>
          </div>

          <div class="mt-5 space-y-3.5">
            <div v-for="stage in stages" :key="stage.key">
              <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-slate-600">{{ stage.label }}</span>
                <span class="text-slate-400">
                  <span class="font-bold text-slate-800">{{ stage.value }}</span>
                  <span class="ml-1.5">{{ stage.key === 'total' ? '' : `环节转化 ${stage.rate}%` }}</span>
                </span>
              </div>
              <div class="mt-1.5 h-3 overflow-hidden rounded-full bg-blue-50">
                <div
                  class="h-3 rounded-full bg-gradient-to-r transition-all duration-500"
                  :class="stage.tone"
                  :style="{ width: `${stage.width}%` }"
                />
              </div>
            </div>
          </div>

          <div class="mt-5 grid grid-cols-3 gap-3 border-t border-blue-100/70 pt-4 text-center">
            <div>
              <p class="text-lg font-black text-slate-900">{{ review.funnel.applyRate }}%</p>
              <p class="text-[11px] text-slate-400">投递覆盖率</p>
            </div>
            <div>
              <p class="text-lg font-black text-slate-900">{{ review.funnel.writtenRate }}%</p>
              <p class="text-[11px] text-slate-400">投递 → 笔试</p>
            </div>
            <div>
              <p class="text-lg font-black text-slate-900">{{ review.funnel.offerRate }}%</p>
              <p class="text-[11px] text-slate-400">面试 → Offer</p>
            </div>
          </div>
        </section>

        <!-- 月度趋势 -->
        <section class="soft-card p-5 sm:p-6">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-bold text-slate-800">近 6 个月投递</p>
              <p class="mt-1 text-xs text-slate-400">观察投递节奏是否稳定</p>
            </div>
            <span class="rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-bold text-blue-600">
              共 {{ review.monthly.reduce((sum, point) => sum + point.count, 0) }} 个
            </span>
          </div>

          <div class="mt-6 flex h-44 items-end gap-2 sm:gap-3">
            <div v-for="point in review.monthly" :key="point.month" class="flex min-w-0 flex-1 flex-col items-center gap-2">
              <span class="text-xs font-bold" :class="point.count ? 'text-blue-600' : 'text-slate-300'">{{ point.count }}</span>
              <div class="flex h-28 w-full items-end justify-center">
                <div
                  class="w-full max-w-8 rounded-t-lg transition-all duration-500"
                  :class="point.count ? 'bg-gradient-to-t from-blue-600 to-sky-400' : 'bg-slate-100'"
                  :style="{ height: `${Math.max((point.count / monthlyMax) * 100, 6)}%` }"
                />
              </div>
              <span class="text-[11px] text-slate-400">{{ point.label }}</span>
            </div>
          </div>

          <p class="mt-4 rounded-xl bg-blue-50/60 px-3 py-2 text-xs leading-5 text-slate-500">
            平均匹配分：
            <span class="font-bold text-slate-700">{{ review.averageScore ?? '暂无' }}</span>
            {{ review.averageScore ? '（建议优先投递 70 分以上的岗位）' : '（完成一次 AI 匹配后显示）' }}
          </p>
        </section>
      </div>

      <div v-if="review.funnel.total > 0" class="grid gap-5 xl:grid-cols-2">
        <section class="soft-card p-5 sm:p-6">
          <div class="flex items-center gap-2">
            <span class="grid h-8 w-8 place-items-center rounded-lg bg-amber-50 text-amber-500">
              <Lightbulb class="h-4 w-4" />
            </span>
            <div>
              <p class="text-sm font-bold text-slate-800">高频缺失关键词</p>
              <p class="text-xs text-slate-400">汇总 AI 匹配记录中未覆盖的 JD 关键词</p>
            </div>
          </div>

          <div v-if="review.missingKeywords.length" class="mt-4 flex flex-wrap gap-2">
            <span
              v-for="item in review.missingKeywords"
              :key="item.keyword"
              class="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1.5 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-100"
            >
              {{ item.keyword }}
              <span class="rounded-full bg-white px-1.5 py-0.5 text-[10px] font-bold text-amber-500">{{ item.count }}</span>
            </span>
          </div>
          <p v-else class="mt-4 text-xs leading-5 text-slate-400">
            还没有匹配记录，去「AI 匹配」生成一次报告后，这里会汇总你最常缺失的关键词。
          </p>

          <button
            class="mt-4 inline-flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700"
            @click="router.push({ name: 'ai-match' })"
          >
            去做 AI 匹配
            <ArrowRight class="h-3.5 w-3.5" />
          </button>
        </section>

        <section class="soft-card p-5 sm:p-6">
          <div class="flex items-center gap-2">
            <span class="grid h-8 w-8 place-items-center rounded-lg bg-blue-50 text-blue-600">
              <TrendingUp class="h-4 w-4" />
            </span>
            <div>
              <p class="text-sm font-bold text-slate-800">复盘结论</p>
              <p class="text-xs text-slate-400">根据当前数据自动生成</p>
            </div>
          </div>

          <ol class="mt-4 space-y-3">
            <li v-for="(insight, index) in review.insights" :key="index" class="flex gap-3">
              <span class="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-blue-50 text-[11px] font-bold text-blue-600">
                {{ index + 1 }}
              </span>
              <p class="text-sm leading-6 text-slate-600">{{ insight }}</p>
            </li>
          </ol>
        </section>
      </div>

      <EmptyState
        v-else
        :icon="FileText"
        title="还没有可以复盘的数据"
        description="先添加并投递 3-5 个岗位，这里会生成漏斗、转化率和关键词缺口分析。"
        action-label="去添加岗位"
        @action="router.push({ name: 'jobs', query: { create: '1' } })"
      />
    </template>
  </div>
</template>
