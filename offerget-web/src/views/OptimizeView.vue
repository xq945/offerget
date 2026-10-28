<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  CheckCircle2,
  History,
  PenLine,
  Sparkles,
  Wand2,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import MatchScoreRing from '@/components/MatchScoreRing.vue'
import { fetchJobs } from '@/api/jobs'
import { fetchResumes } from '@/api/resumes'
import { fetchOptimizeHistory, runOptimize } from '@/api/ai'
import { formatDateTime } from '@/utils/format'

const route = useRoute()

const jobs = ref([])
const resumes = ref([])
const jobId = ref(null)
const resumeId = ref(null)
const result = ref(null)
const history = ref([])
const optimizing = ref(false)
const loading = ref(true)

function refreshHistory() {
  result.value = null
  history.value = []
  if (!jobId.value) {
    return
  }
  fetchOptimizeHistory(jobId.value)
    .then((records) => {
      history.value = records
      if (records.length) {
        result.value = records[0]
        resumeId.value = records[0].resumeId
      }
    })
    .catch(() => {})
}

async function startOptimize() {
  if (!jobId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  if (!resumeId.value) {
    ElMessage.warning('请先选择简历，或去「简历管理」上传一份')
    return
  }
  optimizing.value = true
  try {
    result.value = await runOptimize({ jobId: jobId.value, resumeId: resumeId.value })
    ElMessage.success('优化建议已生成')
    history.value = await fetchOptimizeHistory(jobId.value)
  }
  catch (error) {
    ElMessage.error(error.message || '生成优化建议失败')
  }
  finally {
    optimizing.value = false
  }
}

watch(jobId, refreshHistory)

onMounted(async () => {
  try {
    const [jobPage, resumeList] = await Promise.all([
      fetchJobs({ page: 1, size: 100 }),
      fetchResumes(),
    ])
    jobs.value = jobPage.records
    resumes.value = resumeList

    const queryJobId = Number(route.query.jobId)
    if (queryJobId && jobs.value.some((job) => job.id === queryJobId)) {
      jobId.value = queryJobId
    }
    else if (jobs.value.length) {
      jobId.value = jobs.value[0].id
    }

    const defaultResume = resumeList.find((resume) => resume.isDefault) || resumeList[0]
    if (defaultResume) {
      resumeId.value = defaultResume.id
    }
  }
  catch (error) {
    ElMessage.error(error.message || '加载优化数据失败')
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载优化数据…" class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">简历优化</h2>
        <p class="mt-1 text-sm text-slate-500">对照 JD 缺口，按简历段落给出可执行的补充建议。</p>
      </div>
    </section>

    <section class="soft-card p-5">
      <div class="flex items-center gap-2">
        <span class="grid h-9 w-9 place-items-center rounded-xl bg-emerald-50 text-emerald-600">
          <Wand2 class="h-4.5 w-4.5" />
        </span>
        <div>
          <p class="text-sm font-bold text-slate-800">选择岗位与简历</p>
          <p class="text-xs text-slate-400">本地规则生成，不会调用外部模型或上传简历</p>
        </div>
      </div>

      <div class="mt-4 grid gap-3 md:grid-cols-[1fr_1fr_auto]">
        <el-select v-model="jobId" filterable placeholder="选择岗位" class="w-full">
          <el-option
            v-for="job in jobs"
            :key="job.id"
            :label="`${job.company} · ${job.title}`"
            :value="job.id"
          />
        </el-select>

        <el-select v-model="resumeId" filterable placeholder="选择简历" class="w-full">
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="resume.versionLabel ? `${resume.name}（${resume.versionLabel}）` : resume.name"
            :value="resume.id"
          />
        </el-select>

        <el-button
          type="primary"
          size="large"
          :loading="optimizing"
          :disabled="!jobs.length || !resumes.length"
          @click="startOptimize"
        >
          <Sparkles v-if="!optimizing" class="mr-1.5 h-4 w-4" />
          生成优化建议
        </el-button>
      </div>

      <p v-if="!jobs.length || !resumes.length" class="mt-3 text-xs font-semibold text-amber-600">
        {{ !jobs.length ? '还没有岗位' : '' }}{{ !jobs.length && !resumes.length ? '，' : '' }}{{ !resumes.length ? '还没有简历' : '' }}，请先补充后再优化。
      </p>
    </section>

    <section v-if="result" class="grid gap-5 xl:grid-cols-[minmax(0,320px)_1fr]">
      <div class="soft-card flex flex-col items-center p-6 text-center">
        <div class="flex items-center gap-3">
          <div class="flex flex-col items-center">
            <MatchScoreRing :score="result.scoreBefore" :size="86" :stroke="9" :show-label="false" />
            <span class="mt-1 text-[10px] font-semibold text-slate-400">优化前</span>
          </div>
          <ArrowRight class="h-5 w-5 text-slate-300" />
          <div class="flex flex-col items-center">
            <MatchScoreRing :score="result.scoreAfter" :size="112" :stroke="9" />
            <span class="mt-1 text-[10px] font-semibold text-emerald-600">预估优化后</span>
          </div>
        </div>

        <p class="mt-4 text-sm font-bold text-slate-800">{{ result.company }} · {{ result.jobTitle }}</p>
        <p class="mt-1 text-xs text-slate-400">简历版本：{{ result.resumeName }}</p>
        <p class="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-xs leading-5 text-emerald-700">{{ result.summary }}</p>
        <p class="mt-3 text-[11px] text-slate-400">生成时间：{{ formatDateTime(result.createdAt) }}</p>
      </div>

      <div class="space-y-4">
        <div class="soft-card p-5">
          <div class="flex items-center gap-2">
            <CheckCircle2 class="h-4 w-4 text-emerald-500" />
            <p class="text-sm font-bold text-slate-800">
              建议补充的关键词
              <span class="ml-1 text-xs font-semibold text-slate-400">{{ result.addedKeywords.length }}</span>
            </p>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <span
              v-for="keyword in result.addedKeywords"
              :key="keyword"
              class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-100"
            >
              {{ keyword }}
            </span>
            <span v-if="!result.addedKeywords.length" class="text-xs text-slate-400">
              简历已覆盖 JD 中的主要关键词，无需补充。
            </span>
          </div>
        </div>

        <div
          v-for="section in result.sections"
          :key="section.title"
          class="soft-card p-5"
        >
          <div class="flex items-center justify-between">
            <p class="text-sm font-bold text-slate-800">{{ section.title }}</p>
            <span class="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-bold text-amber-600">
              {{ section.keywords.length }} 个待补关键词
            </span>
          </div>

          <p v-if="section.original" class="mt-3 line-clamp-4 rounded-xl bg-slate-50 px-3 py-2.5 text-xs leading-5 text-slate-500">
            {{ section.original }}
          </p>

          <p class="mt-3 flex items-start gap-2 text-xs leading-6 text-slate-600">
            <PenLine class="mt-1 h-3.5 w-3.5 shrink-0 text-blue-500" />
            {{ section.suggestion }}
          </p>

          <div class="mt-3 flex flex-wrap gap-1.5">
            <span
              v-for="keyword in section.keywords"
              :key="keyword"
              class="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-700 ring-1 ring-inset ring-amber-100"
            >
              {{ keyword }}
            </span>
          </div>
        </div>

        <div v-if="!result.sections.length" class="soft-card p-5 text-xs leading-6 text-slate-500">
          当前简历与 JD 的关键词覆盖度较高，建议把最相关的项目前置，并补充可量化的结果。
        </div>
      </div>
    </section>

    <EmptyState
      v-else-if="!loading && (!jobs.length || !resumes.length)"
      :icon="Sparkles"
      title="先准备岗位和简历"
      description="添加至少一个岗位并上传简历后，就可以生成分段优化建议。"
      action-label="去添加岗位"
      @action="$router.push({ name: 'jobs', query: { create: '1' } })"
    />

    <EmptyState
      v-else-if="!loading"
      :icon="Wand2"
      title="还没有优化记录"
      description="选择岗位与简历后点击「生成优化建议」，结果会保存在这里。"
    />

    <section v-if="history.length" class="soft-card p-5">
      <div class="flex items-center gap-2">
        <History class="h-4 w-4 text-slate-400" />
        <p class="text-sm font-bold text-slate-800">优化历史</p>
      </div>
      <ul class="mt-2 divide-y divide-blue-50">
        <li v-for="item in history" :key="item.id" class="flex items-center gap-3 py-2.5">
          <span class="w-16 shrink-0 text-xs font-bold text-slate-500">
            {{ item.scoreBefore }} <span class="text-emerald-500">→ {{ item.scoreAfter }}</span>
          </span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-xs font-semibold text-slate-600">{{ item.jobTitle }} · {{ item.resumeName }}</p>
            <p class="text-[11px] text-slate-400">{{ formatDateTime(item.createdAt) }}</p>
          </div>
          <button class="text-[11px] font-bold text-blue-600 hover:underline" @click="result = item">查看</button>
        </li>
      </ul>
    </section>
  </div>
</template>
