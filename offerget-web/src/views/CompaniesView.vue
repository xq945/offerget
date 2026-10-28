<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Building2,
  CalendarClock,
  CheckCircle2,
  ExternalLink,
  MapPin,
  Plus,
  Search,
} from 'lucide-vue-next'

import DraftReview from '@/components/DraftReview.vue'
import WatchCompanies from '@/components/WatchCompanies.vue'
import { fetchCompanies, importCompany } from '@/api/companies'
import {
  companyInitial,
  companyTone,
  deadlineText,
  formatDate,
} from '@/utils/format'

const router = useRouter()

const companies = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(8)
const industry = ref('全部')
const city = ref('')
const keyword = ref('')
const loading = ref(true)
const importingId = ref(null)
const tab = ref('library')

const tabs = [
  { key: 'library', label: '校招信息库' },
  { key: 'watch', label: '我的关注' },
  { key: 'review', label: '待审核' },
]

function onCrawlChanged() {
  if (tab.value === 'library') {
    loadCompanies()
  }
}

const industries = ['全部', '互联网', '硬件', '国企', '金融', '人工智能']
const cities = ['北京', '上海', '深圳', '杭州', '南京', '合肥']

let searchTimer = null

const BATCH_TONE = {
  提前批: 'bg-blue-50 text-blue-600 ring-blue-100',
  正式批: 'bg-emerald-50 text-emerald-600 ring-emerald-100',
  补录: 'bg-amber-50 text-amber-600 ring-amber-100',
}

async function loadCompanies() {
  loading.value = true
  try {
    const data = await fetchCompanies({
      page: page.value,
      size: size.value,
      industry: industry.value === '全部' ? undefined : industry.value,
      city: city.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    companies.value = data.records
    total.value = data.total
  }
  catch (error) {
    ElMessage.error(error.message || '加载校招信息失败')
  }
  finally {
    loading.value = false
  }
}

function setIndustry(value) {
  industry.value = value
}

function onKeywordInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadCompanies()
  }, 350)
}

async function addToJobs(company) {
  importingId.value = company.id
  try {
    await importCompany(company.id)
    company.imported = true
    ElMessage.success('已加入我的投递')
  }
  catch (error) {
    if (error.code === 40900) {
      company.imported = true
    }
    ElMessage.error(error.message || '加入投递失败')
  }
  finally {
    importingId.value = null
  }
}

function openApply(link) {
  if (link) {
    window.open(link, '_blank', 'noopener')
  }
}

function extraTags(company) {
  return (company.tags || []).filter(
    (tag) => tag !== company.industry && tag !== company.city,
  )
}

watch([city, industry], () => {
  page.value = 1
  loadCompanies()
})

onMounted(loadCompanies)
</script>

<template>
  <div class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">校招信息库</h2>
        <p class="mt-1 text-sm text-slate-500">汇总企业校招批次与截止时间，看到合适的岗位直接加入投递。</p>
      </div>
      <span
        v-if="tab === 'library'"
        class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white/80 px-4 py-2 text-xs font-semibold text-slate-500"
      >
        <Building2 class="h-4 w-4 text-blue-500" />
        共 {{ total }} 个校招岗位
      </span>
    </section>

    <section class="flex flex-wrap gap-1.5 rounded-2xl bg-white/70 p-1.5 ring-1 ring-blue-100">
      <button
        v-for="item in tabs"
        :key="item.key"
        class="flex-1 rounded-xl px-4 py-2 text-xs font-bold transition sm:flex-none"
        :class="tab === item.key
          ? 'bg-blue-600 text-white shadow-[0_8px_16px_rgba(37,99,235,0.2)]'
          : 'text-slate-500 hover:bg-blue-50 hover:text-blue-700'"
        @click="tab = item.key"
      >
        {{ item.label }}
      </button>
    </section>

    <template v-if="tab === 'library'">
      <section class="soft-card p-4">
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="item in industries"
          :key="item"
          class="rounded-full px-3.5 py-1.5 text-xs font-bold transition"
          :class="industry === item
            ? 'bg-blue-600 text-white shadow-[0_8px_16px_rgba(37,99,235,0.2)]'
            : 'bg-blue-50/70 text-slate-500 hover:bg-blue-50 hover:text-blue-700'"
          @click="setIndustry(item)"
        >
          {{ item }}
        </button>
      </div>

      <div class="mt-3 flex flex-col gap-2 sm:flex-row">
        <el-select v-model="city" placeholder="全部城市" clearable class="sm:!w-40">
          <el-option v-for="item in cities" :key="item" :label="item" :value="item" />
        </el-select>
        <div class="relative flex-1">
          <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索企业或岗位"
            class="w-full rounded-full border border-blue-100 bg-white py-2 pl-9 pr-3 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-500/10"
            @input="onKeywordInput"
            @keyup.enter="page = 1; loadCompanies()"
          />
        </div>
      </div>
    </section>

    <section v-loading="loading" element-loading-text="正在加载校招信息…" class="min-h-40 space-y-4">
      <div v-if="companies.length" class="grid gap-4 lg:grid-cols-2">
        <article
          v-for="company in companies"
          :key="company.id"
          class="soft-card flex flex-col p-5 transition hover:-translate-y-0.5 hover:shadow-[0_20px_40px_rgba(37,99,235,0.12)]"
        >
          <div class="flex items-start gap-3">
            <span
              class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br text-base font-black text-white shadow-sm"
              :class="companyTone(company.company)"
            >
              {{ companyInitial(company.company) }}
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="truncate text-base font-bold text-slate-900">{{ company.company }}</h3>
                <span
                  class="rounded-full px-2 py-0.5 text-[10px] font-bold ring-1 ring-inset"
                  :class="BATCH_TONE[company.batch] || BATCH_TONE.正式批"
                >
                  {{ company.batch }}
                </span>
                <span
                  v-if="company.infoType === 'PROJECT'"
                  class="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-600"
                >
                  招聘项目
                </span>
              </div>
              <p class="mt-0.5 truncate text-sm font-semibold text-slate-500">{{ company.jobTitle }}</p>
            </div>
            <span
              class="shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold"
              :class="company.expired ? 'bg-slate-100 text-slate-400' : 'bg-rose-50 text-rose-500'"
            >
              {{ company.expired ? '已截止' : deadlineText(company.daysLeft) }}
            </span>
          </div>

          <p class="mt-3 line-clamp-2 text-xs leading-5 text-slate-500">{{ company.description }}</p>

          <div class="mt-3 flex flex-wrap items-center gap-1.5">
            <span class="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-semibold text-blue-600">
              <MapPin class="h-3 w-3" />
              {{ company.city }}
            </span>
            <span class="rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-semibold text-violet-600">{{ company.industry }}</span>
            <span
              v-for="tag in extraTags(company)"
              :key="tag"
              class="rounded-full bg-slate-50 px-2 py-0.5 text-[10px] font-semibold text-slate-400"
            >
              {{ tag }}
            </span>
          </div>

          <div class="mt-4 flex items-center justify-between gap-2 border-t border-blue-100/70 pt-3">
            <span class="inline-flex items-center gap-1 text-[11px] text-slate-400">
              <CalendarClock class="h-3.5 w-3.5" />
              截止 {{ formatDate(company.deadline) }}
            </span>

            <div class="flex items-center gap-2">
              <button
                v-if="company.applyUrl"
                class="inline-flex items-center gap-1 rounded-lg border border-blue-100 bg-white px-2.5 py-1.5 text-[11px] font-semibold text-slate-500 transition hover:border-blue-200 hover:text-blue-700"
                @click="openApply(company.applyUrl)"
              >
                官网
                <ExternalLink class="h-3 w-3" />
              </button>

              <button
                v-if="company.infoType === 'PROJECT'"
                class="inline-flex items-center gap-1 rounded-lg bg-amber-50 px-3 py-1.5 text-[11px] font-bold text-amber-700 transition hover:bg-amber-100"
                @click="openApply(company.applyUrl || company.sourceUrl)"
              >
                查看项目
                <ExternalLink class="h-3 w-3" />
              </button>
              <button
                v-else-if="company.imported"
                class="inline-flex items-center gap-1 rounded-lg bg-emerald-50 px-3 py-1.5 text-[11px] font-bold text-emerald-600"
                @click="router.push({ name: 'jobs', query: { keyword: company.company } })"
              >
                <CheckCircle2 class="h-3.5 w-3.5" />
                已加入投递
              </button>
              <button
                v-else-if="company.expired"
                class="rounded-lg bg-slate-100 px-3 py-1.5 text-[11px] font-bold text-slate-400"
                disabled
              >
                已截止
              </button>
              <button
                v-else
                class="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-[11px] font-bold text-white shadow-[0_8px_16px_rgba(37,99,235,0.2)] transition hover:bg-blue-700 disabled:opacity-60"
                :disabled="importingId === company.id"
                @click="addToJobs(company)"
              >
                <Plus class="h-3.5 w-3.5" />
                {{ importingId === company.id ? '加入中…' : '加入我的投递' }}
              </button>
            </div>
          </div>
        </article>
      </div>

      <EmptyState
        v-else-if="!loading"
        :icon="Building2"
        title="没有找到匹配的校招信息"
        description="换个行业、城市或关键词试试，也可以清空筛选查看全部。"
      />

      <div v-if="total > size" class="flex justify-center pt-2">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="(value) => { page = value; loadCompanies() }"
        />
      </div>
      </section>
    </template>

    <WatchCompanies v-else-if="tab === 'watch'" @changed="onCrawlChanged" />
    <DraftReview v-else @changed="onCrawlChanged" />
  </div>
</template>
