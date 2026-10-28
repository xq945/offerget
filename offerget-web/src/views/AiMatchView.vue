<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CheckCircle2,
  CircleAlert,
  FileText,
  History,
  Lightbulb,
  Sparkles,
} from 'lucide-vue-next'

import MatchScoreRing from '@/components/MatchScoreRing.vue'
import { fetchJobs } from '@/api/jobs'
import { fetchResumes } from '@/api/resumes'
import { fetchLatestMatch, fetchMatchHistory, runMatch } from '@/api/ai'
import { formatDateTime, scoreTone } from '@/utils/format'

const route = useRoute()

const jobs = ref([])
const resumes = ref([])
const jobId = ref(null)
const resumeId = ref(null)
const result = ref(null)
const history = ref([])
const matching = ref(false)
const loading = ref(true)

const steps = [
  { title: '添加目标岗位', desc: '录入公司、岗位和完整 JD，越完整匹配越准确。' },
  { title: '上传简历版本', desc: '支持 PDF / DOCX，系统会自动解析文本参与匹配。' },
  { title: '生成匹配报告', desc: '查看命中与缺失关键词，按建议针对性修改简历。' },
]

function refreshHistory() {
  result.value = null
  history.value = []
  if (!jobId.value) {
    return
  }

  Promise.all([fetchLatestMatch(jobId.value), fetchMatchHistory(jobId.value)])
    .then(([latest, records]) => {
      result.value = latest
      history.value = records
      if (latest?.resumeId) {
        resumeId.value = latest.resumeId
      }
    })
    .catch(() => {})
}

async function startMatch() {
  if (!jobId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  if (!resumeId.value) {
    ElMessage.warning('请先选择简历，或去「简历管理」上传一份')
    return
  }

  matching.value = true
  try {
    result.value = await runMatch({ jobId: jobId.value, resumeId: resumeId.value })
    ElMessage.success('匹配完成')
    history.value = await fetchMatchHistory(jobId.value)
  }
  catch (error) {
    ElMessage.error(error.message || '匹配失败，请稍后重试')
  }
  finally {
    matching.value = false
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
    ElMessage.error(error.message || '加载匹配数据失败')
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载匹配数据…" class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">AI 匹配</h2>
        <p class="mt-1 text-sm text-slate-500">对照岗位 JD 提取关键词，定位简历命中项与待补齐的差距。</p>
      </div>
    </section>

    <section class="soft-card p-5">
      <div class="flex items-center gap-2">
        <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
          <Sparkles class="h-4.5 w-4.5" />
        </span>
        <div>
          <p class="text-sm font-bold text-slate-800">选择岗位与简历</p>
          <p class="text-xs text-slate-400">本地规则引擎分析，不上传简历到外部服务</p>
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
          :loading="matching"
          :disabled="!jobs.length || !resumes.length"
          @click="startMatch"
        >
          <Sparkles v-if="!matching" class="mr-1.5 h-4 w-4" />
          开始匹配
        </el-button>
      </div>

      <p v-if="!jobs.length || !resumes.length" class="mt-3 text-xs font-semibold text-amber-600">
        {{ !jobs.length ? '还没有岗位' : '' }}{{ !jobs.length && !resumes.length ? '，' : '' }}{{ !resumes.length ? '还没有简历' : '' }}，请先补充后再进行匹配。
      </p>
    </section>

    <!-- 匹配结果 -->
    <section v-if="result" class="grid gap-5 xl:grid-cols-[minmax(0,320px)_1fr]">
      <div class="soft-card flex flex-col items-center p-6 text-center">
        <MatchScoreRing :score="result.score" :size="164" :stroke="9" />
        <p class="mt-4 text-sm font-bold text-slate-800">{{ result.company }} · {{ result.jobTitle }}</p>
        <p class="mt-1 text-xs text-slate-400">简历版本：{{ result.resumeName }}</p>
        <p class="mt-4 rounded-xl bg-blue-50 px-4 py-3 text-xs leading-5 text-slate-600">{{ result.summary }}</p>
        <p class="mt-3 text-[11px] text-slate-400">匹配时间：{{ formatDateTime(result.createdAt) }}</p>
      </div>

      <div class="space-y-4">
        <div class="soft-card p-5">
          <div class="flex items-center gap-2">
            <CheckCircle2 class="h-4 w-4 text-emerald-500" />
            <p class="text-sm font-bold text-slate-800">
              命中关键词
              <span class="ml-1 text-xs font-semibold text-slate-400">{{ result.matched.length }}</span>
            </p>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <span
              v-for="keyword in result.matched"
              :key="keyword"
              class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-inset ring-emerald-100"
            >
              {{ keyword }}
            </span>
            <span v-if="!result.matched.length" class="text-xs text-slate-400">暂未命中关键词</span>
          </div>

          <div class="mt-5 flex items-center gap-2">
            <CircleAlert class="h-4 w-4 text-amber-500" />
            <p class="text-sm font-bold text-slate-800">
              缺失关键词
              <span class="ml-1 text-xs font-semibold text-slate-400">{{ result.missing.length }}</span>
            </p>
          </div>
          <div class="mt-3 flex flex-wrap gap-2">
            <span
              v-for="keyword in result.missing"
              :key="keyword"
              class="rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 ring-1 ring-inset ring-amber-100"
            >
              {{ keyword }}
            </span>
            <span v-if="!result.missing.length" class="text-xs text-slate-400">关键词覆盖完整</span>
          </div>
        </div>

        <div class="soft-card p-5">
          <div class="flex items-center gap-2">
            <Lightbulb class="h-4 w-4 text-blue-500" />
            <p class="text-sm font-bold text-slate-800">优化建议</p>
          </div>
          <ul class="mt-3 space-y-2.5">
            <li v-for="(suggestion, index) in result.suggestions" :key="index" class="flex gap-2.5">
              <span class="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-blue-50 text-[10px] font-bold text-blue-600">
                {{ index + 1 }}
              </span>
              <p class="text-xs leading-5 text-slate-600">{{ suggestion }}</p>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- 引导 -->
    <section v-else class="soft-card p-6 sm:p-8">
      <div class="grid gap-5 sm:grid-cols-3">
        <div v-for="(step, index) in steps" :key="step.title" class="rounded-2xl border border-blue-50 bg-blue-50/40 p-4">
          <span class="grid h-8 w-8 place-items-center rounded-full bg-white text-sm font-black text-blue-600 shadow-sm">
            {{ index + 1 }}
          </span>
          <p class="mt-3 text-sm font-bold text-slate-700">{{ step.title }}</p>
          <p class="mt-1 text-xs leading-5 text-slate-500">{{ step.desc }}</p>
        </div>
      </div>
      <p class="mt-5 flex items-center justify-center gap-2 text-xs text-slate-400">
        <FileText class="h-4 w-4" />
        简历文本只用于本地关键词匹配，不会发送到任何外部服务
      </p>
    </section>

    <!-- 历史记录 -->
    <section v-if="history.length" class="soft-card p-5">
      <div class="flex items-center gap-2">
        <History class="h-4 w-4 text-slate-400" />
        <p class="text-sm font-bold text-slate-800">匹配历史</p>
      </div>
      <ul class="mt-2 divide-y divide-blue-50">
        <li v-for="item in history" :key="item.id" class="flex items-center gap-3 py-2.5">
          <span class="w-10 shrink-0 text-sm font-black" :class="scoreTone(item.score).text">{{ item.score }}</span>
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
