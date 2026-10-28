<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight,
  CheckCircle2,
  FilePen,
  History,
  Sparkles,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import MatchScoreRing from '@/components/MatchScoreRing.vue'
import { fetchJobs } from '@/api/jobs'
import { fetchResumes } from '@/api/resumes'
import {
  applyCustomization,
  fetchAiConfig,
  fetchCustomizeHistory,
  runCustomize,
} from '@/api/ai'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const jobs = ref([])
const resumes = ref([])
const jobId = ref(null)
const resumeId = ref(null)
const result = ref(null)
const history = ref([])
const edits = reactive({})
const accepted = reactive({})
const aiConfigured = ref(true)
const loading = ref(true)
const generating = ref(false)
const applying = ref(false)

const TYPE_LABELS = {
  INTERNSHIP: '实习经历',
  PROJECT: '项目经历',
  SKILLS: '技能',
  SUMMARY: '自我评价',
  TEXT: '简历段落',
}

const acceptedCount = computed(() => result.value
  ? result.value.sections.filter((section) => accepted[section.key]).length
  : 0)

const canApply = computed(() => result.value
  && result.value.status === 'DRAFT'
  && acceptedCount.value > 0)

function typeLabel(type) {
  return TYPE_LABELS[type] || type
}

function resetEdits(data) {
  Object.keys(edits).forEach((key) => delete edits[key])
  Object.keys(accepted).forEach((key) => delete accepted[key])
  if (!data) {
    return
  }
  data.sections.forEach((section) => {
    edits[section.key] = section.rewritten || section.original
    accepted[section.key] = true
  })
}

function loadRecord(record) {
  result.value = record
  resetEdits(record)
}

function refreshHistory() {
  result.value = null
  resetEdits(null)
  history.value = []
  if (!jobId.value) {
    return
  }
  fetchCustomizeHistory(jobId.value)
    .then((records) => {
      history.value = records
      if (records.length) {
        loadRecord(records[0])
      }
    })
    .catch(() => {})
}

async function startCustomize() {
  if (!jobId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  if (!resumeId.value) {
    ElMessage.warning('请先选择简历，或去「简历管理」上传一份')
    return
  }
  generating.value = true
  try {
    const data = await runCustomize({ jobId: jobId.value, resumeId: resumeId.value })
    loadRecord(data)
    ElMessage.success('改写完成，请逐段审阅后生成新版本')
    history.value = await fetchCustomizeHistory(jobId.value)
  }
  catch (error) {
    ElMessage.error(error.message || 'AI 改写失败')
  }
  finally {
    generating.value = false
  }
}

async function applySelected() {
  if (!canApply.value) {
    return
  }
  const sections = result.value.sections
    .filter((section) => accepted[section.key] && edits[section.key] && edits[section.key].trim())
    .map((section) => ({ key: section.key, rewritten: edits[section.key].trim() }))
  if (!sections.length) {
    ElMessage.warning('至少采用一段改写内容')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将采用 ${sections.length} 段改写生成新简历版本，原版本保持不变。`,
      '生成新版本',
      { confirmButtonText: '生成', cancelButtonText: '再看看' },
    )
  }
  catch {
    return
  }
  applying.value = true
  try {
    await applyCustomization(result.value.id, { sections })
    ElMessage.success('已生成新简历版本')
    router.push({ name: 'resumes' })
  }
  catch (error) {
    ElMessage.error(error.message || '生成新版本失败')
  }
  finally {
    applying.value = false
  }
}

watch(jobId, refreshHistory)

onMounted(async () => {
  try {
    const [jobPage, resumeList, config] = await Promise.all([
      fetchJobs({ page: 1, size: 100 }),
      fetchResumes(),
      fetchAiConfig().catch(() => ({ configured: false })),
    ])
    jobs.value = jobPage.records
    resumes.value = resumeList
    aiConfigured.value = config?.configured !== false

    const queryJobId = Number(route.query.jobId)
    if (queryJobId && jobs.value.some((job) => job.id === queryJobId)) {
      jobId.value = queryJobId
    }
    else if (jobs.value.length) {
      jobId.value = jobs.value[0].id
    }

    const queryResumeId = Number(route.query.resumeId)
    if (queryResumeId && resumes.value.some((resume) => resume.id === queryResumeId)) {
      resumeId.value = queryResumeId
    }
    else {
      const defaultResume = resumes.value.find((resume) => resume.isDefault) || resumes.value[0]
      if (defaultResume) {
        resumeId.value = defaultResume.id
      }
    }
  }
  catch (error) {
    ElMessage.error(error.message || '加载定制数据失败')
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载定制数据…" class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">简历定制</h2>
        <p class="mt-1 text-sm text-slate-500">按目标岗位 JD 逐段改写，确认后生成新版本，原版本保持不变。</p>
      </div>
    </section>

    <section class="soft-card p-5">
      <div class="flex items-center gap-2">
        <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
          <FilePen class="h-4.5 w-4.5" />
        </span>
        <div>
          <p class="text-sm font-bold text-slate-800">选择岗位与简历</p>
          <p class="text-xs text-slate-400">改写时简历内容会发送至 DeepSeek，仅用于本次生成</p>
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
          :loading="generating"
          :disabled="!jobs.length || !resumes.length || !aiConfigured"
          @click="startCustomize"
        >
          <Sparkles v-if="!generating" class="mr-1.5 h-4 w-4" />
          开始定制
        </el-button>
      </div>

      <p v-if="!aiConfigured" class="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-700">
        未检测到 DeepSeek 密钥，请在 application-local.yml 中配置 api-key 后重启后端。
      </p>
      <p v-else-if="!jobs.length || !resumes.length" class="mt-3 text-xs font-semibold text-amber-600">
        {{ !jobs.length ? '还没有岗位' : '' }}{{ !jobs.length && !resumes.length ? '，' : '' }}{{ !resumes.length ? '还没有简历' : '' }}，请先补充后再定制。
      </p>
    </section>

    <template v-if="result">
      <section
        v-if="result.warnings && result.warnings.length"
        class="rounded-2xl border border-rose-200 bg-rose-50/70 p-4"
      >
        <div class="flex items-center gap-2">
          <span class="grid h-6 w-6 place-items-center rounded-full bg-rose-100 text-xs font-black text-rose-600">!</span>
          <p class="text-sm font-bold text-rose-700">事实校验发现 {{ result.warnings.length }} 处可疑内容</p>
        </div>
        <p v-for="warning in result.warnings" :key="warning" class="mt-1 pl-8 text-xs text-rose-600">· {{ warning }}</p>
        <p class="mt-2 pl-8 text-[11px] text-rose-500">以上内容原文中没有出现，请逐段核对后再决定是否采用。</p>
      </section>

      <section class="grid gap-5 xl:grid-cols-[minmax(0,300px)_1fr]">
        <div class="soft-card flex flex-col p-6">
          <div class="flex items-center justify-center gap-3">
            <div class="flex flex-col items-center">
              <MatchScoreRing :score="result.scoreBefore" :size="80" :stroke="8" :show-label="false" />
              <span class="mt-1 text-[10px] font-semibold text-slate-400">改写前</span>
            </div>
            <ArrowRight class="h-5 w-5 text-slate-300" />
            <div class="flex flex-col items-center">
              <MatchScoreRing :score="result.scoreAfter" :size="104" :stroke="8" />
              <span class="mt-1 text-[10px] font-semibold text-blue-600">改写后</span>
            </div>
          </div>
          <p class="mt-3 text-center text-[11px] text-slate-400">按当前采用的段落真实重算</p>

          <p class="mt-4 text-sm font-bold text-slate-800">{{ result.company }} · {{ result.jobTitle }}</p>
          <p class="mt-1 text-xs text-slate-400">原简历：{{ result.resumeName }}</p>
          <p v-if="result.summary" class="mt-4 rounded-xl bg-blue-50 px-4 py-3 text-xs leading-5 text-blue-700">
            {{ result.summary }}
          </p>
          <p class="mt-3 text-[11px] text-slate-400">生成时间：{{ formatDateTime(result.createdAt) }}</p>

          <div class="mt-auto pt-4">
            <el-button
              type="primary"
              class="w-full"
              :loading="applying"
              :disabled="!canApply"
              @click="applySelected"
            >
              <CheckCircle2 v-if="!applying" class="mr-1.5 h-4 w-4" />
              生成新版本（{{ acceptedCount }} 段）
            </el-button>
            <p v-if="result.status !== 'DRAFT'" class="mt-2 text-center text-[11px] text-slate-400">
              该记录已应用，原简历版本未变
            </p>
          </div>
        </div>

        <div class="space-y-4">
          <article v-for="section in result.sections" :key="section.key" class="soft-card p-5">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="text-sm font-bold text-slate-800">{{ section.label }}</p>
                <p class="mt-0.5 text-xs text-slate-400">{{ typeLabel(section.type) }}</p>
              </div>
              <el-switch v-model="accepted[section.key]" active-text="采用" inactive-text="忽略" />
            </div>

            <div class="mt-3 grid gap-3 lg:grid-cols-2">
              <div class="rounded-xl bg-slate-50 p-3">
                <p class="text-[11px] font-bold text-slate-400">原文</p>
                <pre class="mt-1 whitespace-pre-wrap text-xs leading-5 text-slate-600">{{ section.original }}</pre>
              </div>
              <div class="rounded-xl bg-blue-50/70 p-3">
                <p class="text-[11px] font-bold text-blue-500">改写后（可编辑）</p>
                <el-input
                  v-model="edits[section.key]"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 8 }"
                  :disabled="!accepted[section.key]"
                  class="mt-1"
                />
              </div>
            </div>

            <p v-if="section.rationale" class="mt-2 text-xs text-slate-400">改写思路：{{ section.rationale }}</p>
            <div
              v-if="section.warnings && section.warnings.length"
              class="mt-2 rounded-lg bg-rose-50 px-3 py-2 text-xs text-rose-600"
            >
              <p v-for="warning in section.warnings" :key="warning">· {{ warning }}</p>
            </div>
          </article>
        </div>
      </section>
    </template>

    <EmptyState
      v-else-if="!loading && !history.length"
      :icon="FilePen"
      title="还没有定制记录"
      description="选择一个岗位和简历，AI 会按 JD 逐段改写，你确认后才生成新版本。"
    />

    <section v-if="history.length" class="soft-card p-5">
      <div class="flex items-center gap-2">
        <History class="h-4 w-4 text-slate-400" />
        <p class="text-sm font-bold text-slate-800">历史记录</p>
      </div>
      <div class="mt-3 space-y-2">
        <button
          v-for="item in history"
          :key="item.id"
          class="flex w-full items-center justify-between gap-3 rounded-xl border border-blue-100 bg-white px-4 py-3 text-left transition hover:border-blue-200 hover:bg-blue-50/50"
          @click="loadRecord(item)"
        >
          <div class="min-w-0">
            <p class="truncate text-xs font-bold text-slate-700">{{ item.company }} · {{ item.jobTitle }}</p>
            <p class="mt-0.5 truncate text-[11px] text-slate-400">
              {{ item.resumeName }} · {{ formatDateTime(item.createdAt) }}
            </p>
          </div>
          <div class="flex shrink-0 items-center gap-3">
            <span class="text-xs font-bold text-slate-500">{{ item.scoreBefore }} → {{ item.scoreAfter }}</span>
            <span
              class="rounded-full px-2 py-0.5 text-[10px] font-bold"
              :class="item.status === 'APPLIED' ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'"
            >
              {{ item.status === 'APPLIED' ? '已应用' : '待应用' }}
            </span>
          </div>
        </button>
      </div>
    </section>
  </div>
</template>
