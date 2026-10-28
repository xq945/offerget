<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChevronDown,
  MapPin,
  Plus,
  Search,
  Send,
  Sparkles,
  Wallet,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import JobFormDrawer from '@/components/JobFormDrawer.vue'
import JobStatusTag from '@/components/JobStatusTag.vue'
import MatchScoreRing from '@/components/MatchScoreRing.vue'
import { changeJobStatus, deleteJob, fetchJob, fetchJobs } from '@/api/jobs'
import { fetchResumes } from '@/api/resumes'
import {
  STATUS_OPTIONS,
  companyInitial,
  companyTone,
  deadlineText,
  deadlineTone,
  formatDate,
  statusMeta,
} from '@/utils/format'

const route = useRoute()
const router = useRouter()

const tabs = [{ value: 'ALL', label: '全部' }, ...STATUS_OPTIONS]

const jobs = ref([])
const resumes = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(8)
const status = ref('ALL')
const keyword = ref('')
const loading = ref(true)

const drawerVisible = ref(false)
const editingJob = ref(null)

let searchTimer = null

async function loadJobs() {
  loading.value = true
  try {
    const data = await fetchJobs({
      page: page.value,
      size: size.value,
      status: status.value === 'ALL' ? undefined : status.value,
      keyword: keyword.value.trim() || undefined,
    })
    jobs.value = data.records
    total.value = data.total
  }
  catch (error) {
    ElMessage.error(error.message || '加载岗位失败')
  }
  finally {
    loading.value = false
  }
}

async function loadResumes() {
  try {
    resumes.value = await fetchResumes()
  }
  catch {
    resumes.value = []
  }
}

function setStatus(value) {
  if (status.value === value) {
    return
  }
  status.value = value
  page.value = 1
  loadJobs()
}

function onKeywordInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadJobs()
  }, 350)
}

function openCreate() {
  editingJob.value = null
  drawerVisible.value = true
}

async function openEdit(job) {
  try {
    editingJob.value = await fetchJob(job.id)
    drawerVisible.value = true
  }
  catch (error) {
    ElMessage.error(error.message || '加载岗位详情失败')
  }
}

async function changeStatus(job, nextStatus) {
  try {
    await changeJobStatus(job.id, nextStatus)
    ElMessage.success(`已标记为「${statusMeta(nextStatus).label}」`)
    loadJobs()
  }
  catch (error) {
    ElMessage.error(error.message || '状态更新失败')
  }
}

async function confirmDelete(job) {
  try {
    await ElMessageBox.confirm(
      `删除「${job.company} · ${job.title}」后不可恢复，确定继续吗？`,
      '删除岗位',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }

  try {
    await deleteJob(job.id)
    ElMessage.success('岗位已删除')
    if (jobs.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    loadJobs()
  }
  catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

function handleCommand(command, job) {
  if (command === 'edit') {
    openEdit(job)
  }
  else if (command === 'match') {
    router.push({ name: 'ai-match', query: { jobId: job.id } })
  }
  else if (command === 'delete') {
    confirmDelete(job)
  }
  else if (command.startsWith('status:')) {
    changeStatus(job, command.slice(7))
  }
}

function goMatch(job) {
  router.push({ name: 'ai-match', query: { jobId: job.id } })
}

function onSaved() {
  loadJobs()
  loadResumes()
}

watch(
  () => route.query.keyword,
  (value) => {
    const next = typeof value === 'string' ? value : ''
    if (next !== keyword.value) {
      keyword.value = next
      page.value = 1
      loadJobs()
    }
  },
)

onMounted(() => {
  if (typeof route.query.keyword === 'string') {
    keyword.value = route.query.keyword
  }
  if (route.query.create === '1') {
    editingJob.value = null
    drawerVisible.value = true
    router.replace({ name: 'jobs', query: route.query.keyword ? { keyword: route.query.keyword } : {} })
  }
  loadJobs()
  loadResumes()
})
</script>

<template>
  <div class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">我的投递</h2>
        <p class="mt-1 text-sm text-slate-500">集中管理目标岗位、截止日期、投递情况和下一步动作。</p>
      </div>
      <button
        class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-blue-600 to-sky-500 px-4 py-2.5 text-sm font-bold text-white shadow-[0_14px_28px_rgba(37,99,235,0.24)] transition hover:brightness-105"
        @click="openCreate"
      >
        <Plus class="h-4 w-4" />
        添加岗位
      </button>
    </section>

    <section class="soft-card p-4">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            class="rounded-full px-3.5 py-1.5 text-xs font-bold transition"
            :class="status === tab.value ? 'bg-blue-600 text-white shadow-[0_8px_16px_rgba(37,99,235,0.2)]' : 'bg-blue-50/70 text-slate-500 hover:bg-blue-50 hover:text-blue-700'"
            @click="setStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>

        <div class="relative w-full lg:w-64">
          <Search class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索公司或岗位"
            class="w-full rounded-full border border-blue-100 bg-white py-2 pl-9 pr-3 text-sm text-slate-700 outline-none transition placeholder:text-slate-400 focus:border-blue-400 focus:ring-4 focus:ring-blue-500/10"
            @input="onKeywordInput"
            @keyup.enter="page = 1; loadJobs()"
          />
        </div>
      </div>
    </section>

    <section v-loading="loading" element-loading-text="正在加载岗位…" class="min-h-40 space-y-3">
      <template v-if="jobs.length">
        <div class="flex items-center justify-between px-1">
          <p class="text-xs text-slate-400">共 {{ total }} 个岗位</p>
          <p class="text-xs text-slate-400">第 {{ page }} 页</p>
        </div>

        <article
          v-for="job in jobs"
          :key="job.id"
          class="group rounded-2xl border border-blue-100/80 bg-white/85 p-4 shadow-[0_8px_20px_rgba(37,99,235,0.05)] transition duration-200 hover:border-blue-200 hover:shadow-[0_16px_32px_rgba(37,99,235,0.1)]"
        >
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center">
            <span
              class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br text-base font-black text-white shadow-sm"
              :class="companyTone(job.company)"
            >
              {{ companyInitial(job.company) }}
            </span>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <button class="truncate text-base font-bold text-slate-900 transition hover:text-blue-700" @click="openEdit(job)">
                  {{ job.title }}
                </button>
                <JobStatusTag :status="job.status" size="sm" />
                <span v-if="job.resumeName" class="rounded-full bg-slate-50 px-2 py-0.5 text-[10px] font-semibold text-slate-400">
                  简历：{{ job.resumeName }}
                </span>
              </div>
              <p class="mt-1 text-sm font-semibold text-slate-500">{{ job.company }}</p>

              <div class="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400">
                <span v-if="job.city" class="inline-flex items-center gap-1">
                  <MapPin class="h-3.5 w-3.5" />
                  {{ job.city }}
                </span>
                <span v-if="job.salaryRange" class="inline-flex items-center gap-1">
                  <Wallet class="h-3.5 w-3.5" />
                  {{ job.salaryRange }}
                </span>
                <span v-if="job.jobType">{{ job.jobType }}</span>
                <span v-if="job.source">来源：{{ job.source }}</span>
                <span
                  v-if="job.deadline"
                  class="font-semibold"
                  :class="deadlineTone(job.daysLeft)"
                >
                  截止 {{ formatDate(job.deadline) }} · {{ deadlineText(job.daysLeft) }}
                </span>
              </div>
            </div>

            <div class="flex items-center justify-between gap-3 lg:flex-col lg:items-end">
              <div class="flex items-center gap-2">
                <MatchScoreRing v-if="job.matchScore !== null" :score="job.matchScore" :size="46" :stroke="10" :show-label="false" />
                <span v-else class="rounded-full bg-slate-50 px-2.5 py-1 text-[11px] font-semibold text-slate-400">
                  待匹配
                </span>
                <button
                  class="grid h-9 w-9 place-items-center rounded-lg border border-blue-100 bg-white text-blue-500 transition hover:border-blue-200 hover:bg-blue-50"
                  title="AI 匹配"
                  @click="goMatch(job)"
                >
                  <Sparkles class="h-4 w-4" />
                </button>
              </div>

              <el-dropdown trigger="click" @command="(command) => handleCommand(command, job)">
                <button class="inline-flex items-center gap-1 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700">
                  操作
                  <ChevronDown class="h-3.5 w-3.5" />
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑岗位</el-dropdown-item>
                    <el-dropdown-item command="match">AI 匹配</el-dropdown-item>
                    <el-dropdown-item
                      v-for="option in STATUS_OPTIONS.filter((item) => item.value !== job.status)"
                      :key="option.value"
                      :command="`status:${option.value}`"
                    >
                      标记为{{ option.label }}
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除岗位</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </article>

        <div class="flex justify-center pt-2">
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            layout="prev, pager, next"
            background
            @current-change="(value) => { page = value; loadJobs() }"
          />
        </div>
      </template>

      <EmptyState
        v-else-if="!loading && keyword"
        :icon="Search"
        title="没有找到匹配的岗位"
        description="换个关键词试试，或清空搜索条件查看全部岗位。"
        action-label="清空搜索"
        @action="keyword = ''; page = 1; loadJobs()"
      />

      <EmptyState
        v-else-if="!loading"
        :icon="Send"
        title="还没有添加岗位"
        description="从第一个目标岗位开始，把截止日期、JD 和简历版本都收进工作台。"
        action-label="添加第一个岗位"
        @action="openCreate"
      />
    </section>

    <JobFormDrawer
      v-model="drawerVisible"
      :job="editingJob"
      :resumes="resumes"
      @saved="onSaved"
    />
  </div>
</template>
