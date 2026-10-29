<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Bot,
  BriefcaseBusiness,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  FileText,
  History,
  LoaderCircle,
  MessageSquareText,
  Play,
  RotateCcw,
  Send,
  SkipForward,
  Sparkles,
  Trophy,
  UserRound,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import MatchScoreRing from '@/components/MatchScoreRing.vue'
import { fetchJobs } from '@/api/jobs'
import { fetchResumes } from '@/api/resumes'
import {
  abandonMockInterview,
  answerMockInterview,
  createMockInterview,
  fetchMockInterview,
  fetchMockInterviews,
  finishMockInterview,
  reverseMockInterview,
} from '@/api/mockInterviews'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const TYPE_LABELS = {
  INTRO: '自我介绍',
  PROJECT: '项目深挖',
  TECHNICAL: '岗位基础',
  BEHAVIORAL: '行为面试',
  PRESSURE: '压力追问',
  REVERSE: '反问',
}

const TYPE_TONES = {
  INTRO: 'bg-slate-100 text-slate-600',
  PROJECT: 'bg-blue-50 text-blue-700',
  TECHNICAL: 'bg-violet-50 text-violet-700',
  BEHAVIORAL: 'bg-amber-50 text-amber-700',
  PRESSURE: 'bg-rose-50 text-rose-600',
  REVERSE: 'bg-emerald-50 text-emerald-700',
}

const INTERVIEW_TYPE_LABELS = {
  COMPREHENSIVE: '综合面',
  TECHNICAL: '技术面',
  BEHAVIORAL: '行为面',
}

const DIFFICULTY_LABELS = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战',
}

const STATUS_LABELS = {
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  ABANDONED: '已放弃',
}

const RISK_LABELS = {
  OFF_TOPIC: '答非所问',
  NO_METRICS: '缺少量化',
  VAGUE: '表达空泛',
  EXAGGERATED: '疑似夸大',
  LOGIC_JUMP: '逻辑跳跃',
}

const DIMENSION_LABELS = {
  jobUnderstanding: '岗位理解',
  projectDepth: '项目深度',
  technicalFoundation: '技术基础',
  logic: '表达逻辑',
  starCompleteness: 'STAR 完整性',
}

const SEVERITY_LABELS = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

const loading = ref(true)
const starting = ref(false)
const submitting = ref(false)
const finishing = ref(false)
const reverseLoading = ref(false)
const openingSessionId = ref(null)

const stage = ref('prepare')
const jobs = ref([])
const resumes = ref([])
const history = ref([])
const jobId = ref(null)
const resumeId = ref(null)
const interviewType = ref('COMPREHENSIVE')
const difficulty = ref('MEDIUM')

const session = ref(null)
const report = ref(null)
const transcript = ref([])
const currentQuestion = ref(null)
const pendingFollowUp = ref(null)
const awaitingReverse = ref(false)
const reverseAsked = ref(0)
const answer = ref('')
const reverseInput = ref('')
const chatBox = ref(null)
const chatBottom = ref(null)
let bubbleSeq = 0

const selectedJob = computed(() => jobs.value.find((job) => job.id === jobId.value) || null)
const activeInterview = computed(() =>
  history.value.find(
    (item) => item.jobId === jobId.value && item.status === 'IN_PROGRESS',
  ),
)
const recentHistory = computed(() => history.value.slice(0, 6))
const progressPercent = computed(() => {
  const progress = session.value?.progress
  if (!progress || !progress.total) {
    return 0
  }
  return Math.round((progress.answered / progress.total) * 100)
})
const reverseQuestion = computed(
  () => session.value?.questions?.find((item) => item.type === 'REVERSE') || null,
)
const currentSeq = computed(() => {
  if (!currentQuestion.value) {
    return null
  }
  return currentQuestion.value.seq
})
const reportDimensions = computed(() => {
  const dimensions = report.value?.dimensions || {}
  return Object.keys(DIMENSION_LABELS).map((key) => ({
    key,
    label: DIMENSION_LABELS[key],
    value: dimensions[key] ?? null,
  }))
})
const reportQuestions = computed(() => report.value?.questions || [])
const reportTrend = computed(() => report.value?.trend || [])
const reportReverse = computed(() => report.value?.reverse || null)
const hasReport = computed(() => Boolean(report.value))

function typeLabel(type) {
  return TYPE_LABELS[type] || type || '面试'
}

function typeTone(type) {
  return TYPE_TONES[type] || TYPE_TONES.INTRO
}

function bubbleTagTone(tag) {
  if (tag === '追问') {
    return 'bg-amber-50 text-amber-700'
  }
  if (tag === '反问') {
    return 'bg-emerald-50 text-emerald-700'
  }
  if (tag === '开场' || tag === '面试官' || tag === '面试官回答') {
    return 'bg-slate-100 text-slate-500'
  }
  return typeTone(tag)
}

function riskLabel(flag) {
  return RISK_LABELS[flag] || flag
}

function severityLabel(severity) {
  return SEVERITY_LABELS[severity] || severity
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status
}

function interviewTypeLabel(type) {
  return INTERVIEW_TYPE_LABELS[type] || type
}

function difficultyLabel(value) {
  return DIFFICULTY_LABELS[value] || value
}

function jobLabel(job) {
  return `${job.company} · ${job.title}`
}

function resumeLabel(resume) {
  return resume.versionLabel ? `${resume.name}（${resume.versionLabel}）` : resume.name
}

function questionAverage(question) {
  const values = Object.values(question?.score?.dimensions || {}).filter(
    (value) => value !== null && value !== undefined,
  )
  if (!values.length) {
    return null
  }
  return Math.round(values.reduce((sum, value) => sum + value, 0) / values.length)
}

function appendBubble(role, text, options = {}) {
  if (!text) {
    return null
  }
  bubbleSeq += 1
  const item = { id: bubbleSeq, role, text, ...options }
  transcript.value.push(item)
  return item
}

function scrollChat() {
  nextTick(() => {
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  })
}

function syncResumeForJob() {
  const job = selectedJob.value
  if (job?.resumeId && resumes.value.some((resume) => resume.id === job.resumeId)) {
    resumeId.value = job.resumeId
    return
  }
  if (resumes.value.some((resume) => resume.id === resumeId.value)) {
    return
  }
  const fallback = resumes.value.find((resume) => resume.isDefault) || resumes.value[0]
  resumeId.value = fallback?.id || null
}

async function loadAll() {
  loading.value = true
  try {
    const [jobPage, resumeList, records] = await Promise.all([
      fetchJobs({ page: 1, size: 100 }),
      fetchResumes(),
      fetchMockInterviews(),
    ])
    jobs.value = jobPage.records || []
    resumes.value = resumeList || []
    history.value = records || []

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
      syncResumeForJob()
    }
  }
  catch (error) {
    ElMessage.error(error.message || '加载模拟面试数据失败')
  }
  finally {
    loading.value = false
  }
}

async function refreshHistory() {
  try {
    history.value = await fetchMockInterviews()
  }
  catch {
    // 历史刷新失败不影响当前面试
  }
}

function buildTranscript(data) {
  transcript.value = []
  bubbleSeq = 0
  appendBubble('interviewer', data.opening, { tag: '开场' })

  const mains = (data.questions || []).filter((question) => question.type !== 'REVERSE')
  for (const question of mains) {
    const visible = question.status !== 'PENDING'
      || question.id === data.currentQuestionId
      || Boolean(question.answer)
    if (!visible) {
      continue
    }
    appendBubble('interviewer', question.question, {
      tag: typeLabel(question.type),
      basis: question.basisQuote,
    })
    if (question.answer) {
      appendBubble('user', question.answer)
    }
    for (const followUp of question.followups || []) {
      appendBubble('interviewer', followUp.question, { tag: '追问' })
      if (followUp.answer) {
        appendBubble('user', followUp.answer)
      }
    }
  }

  const progress = data.progress || { answered: 0, total: 0 }
  const allDone = progress.total > 0 && progress.answered >= progress.total
  if (allDone && data.status === 'IN_PROGRESS') {
    const reverse = (data.questions || []).find((question) => question.type === 'REVERSE')
    appendBubble('interviewer', reverse?.question || '我的问题问完了，你有什么想问我的吗？', {
      tag: '反问',
    })
    for (const item of reverse?.followups || []) {
      appendBubble('user', item.question)
      appendBubble('interviewer', item.answer, { tag: '面试官回答' })
    }
  }

  awaitingReverse.value = allDone
  currentQuestion.value = null
  pendingFollowUp.value = null
  if (!allDone) {
    const current = mains.find((question) => question.id === data.currentQuestionId)
    if (current) {
      currentQuestion.value = current
      pendingFollowUp.value = data.currentFollowUp || null
    }
  }
  scrollChat()
}

function openSession(data) {
  session.value = data
  report.value = data.report || null
  reverseAsked.value = (data.questions || []).find((item) => item.type === 'REVERSE')
    ?.followups?.length || 0
  answer.value = ''
  reverseInput.value = ''
  if (data.status === 'COMPLETED') {
    stage.value = 'report'
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  stage.value = 'chat'
  buildTranscript(data)
}

async function openSessionById(id) {
  if (openingSessionId.value) {
    return
  }
  openingSessionId.value = id
  try {
    const data = await fetchMockInterview(id)
    openSession(data)
  }
  catch (error) {
    ElMessage.error(error.message || '打开面试记录失败')
  }
  finally {
    openingSessionId.value = null
  }
}

async function beginInterview() {
  if (!jobId.value) {
    ElMessage.warning('请先选择岗位')
    return
  }
  if (!resumeId.value) {
    ElMessage.warning('请先选择简历，或去「简历管理」上传一份')
    return
  }
  if (activeInterview.value) {
    await openSessionById(activeInterview.value.id)
    return
  }

  starting.value = true
  try {
    const data = await createMockInterview({
      jobId: jobId.value,
      resumeId: resumeId.value,
      interviewType: interviewType.value,
      difficulty: difficulty.value,
    })
    openSession(data)
    await refreshHistory()
    ElMessage.success('面试题已生成，开始作答吧')
  }
  catch (error) {
    ElMessage.error(error.message || '创建模拟面试失败')
  }
  finally {
    starting.value = false
  }
}

function onAnswerKeydown(event) {
  if (event.isComposing) {
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitAnswer(false)
  }
}

async function submitAnswer(skip) {
  const question = currentQuestion.value
  if (!question || submitting.value) {
    return
  }
  const text = answer.value.trim()
  if (!skip && !text) {
    ElMessage.warning('先写下你的回答，或点击「跳过本题」')
    return
  }

  submitting.value = true
  const wasFollowUp = Boolean(pendingFollowUp.value)
  const userText = skip ? '（跳过本题）' : text
  const userBubble = appendBubble('user', userText)
  answer.value = ''
  scrollChat()

  try {
    const result = await answerMockInterview(session.value.id, {
      questionId: question.id,
      answer: skip ? '' : text,
      skip,
    })

    if (wasFollowUp) {
      const last = question.followups?.[question.followups.length - 1]
      if (last) {
        last.answer = skip ? '（跳过）' : text
      }
    }
    else {
      question.answer = skip ? '（跳过）' : text
    }

    if (result.action === 'FOLLOW_UP') {
      question.status = 'ASKED'
      appendBubble('interviewer', result.reply, { tag: '面试官' })
      appendBubble('interviewer', result.followUp, { tag: '追问' })
      if (!question.followups) {
        question.followups = []
      }
      question.followups.push({ question: result.followUp, answer: null })
      pendingFollowUp.value = result.followUp
      session.value.progress = result.progress
    }
    else if (result.action === 'NEXT' && result.nextQuestion) {
      question.status = skip && !wasFollowUp ? 'SKIPPED' : 'ANSWERED'
      appendBubble('interviewer', result.reply, { tag: '面试官' })
      appendBubble('interviewer', result.nextQuestion.question, {
        tag: typeLabel(result.nextQuestion.type),
        basis: result.nextQuestion.basisQuote,
      })
      currentQuestion.value = result.nextQuestion
      pendingFollowUp.value = null
      const nextInSession = session.value.questions.find(
        (item) => item.id === result.nextQuestion.id,
      )
      if (nextInSession) {
        nextInSession.status = 'ASKED'
      }
      session.value.currentQuestionId = result.nextQuestion.id
      session.value.progress = result.progress
    }
    else {
      question.status = skip && !wasFollowUp ? 'SKIPPED' : 'ANSWERED'
      appendBubble(
        'interviewer',
        result.reply || '我的问题问完了，你有什么想问我的吗？',
        { tag: '面试官' },
      )
      awaitingReverse.value = true
      currentQuestion.value = null
      pendingFollowUp.value = null
      appendBubble('interviewer', reverseQuestion.value?.question
        || '我的问题问完了，你有什么想问我的吗？', { tag: '反问' })
      session.value.progress = result.progress
      session.value.currentQuestionId = reverseQuestion.value?.id || null
    }
    scrollChat()
  }
  catch (error) {
    if (userBubble) {
      transcript.value = transcript.value.filter((item) => item.id !== userBubble.id)
    }
    answer.value = skip ? '' : text
    ElMessage.error(error.message || '提交回答失败')
  }
  finally {
    submitting.value = false
  }
}

function onReverseKeydown(event) {
  if (event.isComposing) {
    return
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitReverse()
  }
}

async function submitReverse() {
  const text = reverseInput.value.trim()
  if (!text || reverseLoading.value) {
    return
  }
  reverseLoading.value = true
  const userBubble = appendBubble('user', text)
  reverseInput.value = ''
  scrollChat()
  try {
    const result = await reverseMockInterview(session.value.id, { question: text })
    appendBubble('interviewer', result.reply, { tag: '面试官回答' })
    reverseAsked.value = result.asked
    const reverse = reverseQuestion.value
    if (reverse) {
      if (!reverse.followups) {
        reverse.followups = []
      }
      reverse.followups.push({ question: text, answer: result.reply })
    }
    if (!result.canAskMore) {
      ElMessage.info('反问次数已用完，可以结束面试查看报告')
    }
    scrollChat()
  }
  catch (error) {
    if (userBubble) {
      transcript.value = transcript.value.filter((item) => item.id !== userBubble.id)
    }
    reverseInput.value = text
    ElMessage.error(error.message || '反问失败')
  }
  finally {
    reverseLoading.value = false
  }
}

async function finishInterview() {
  if (finishing.value || reverseLoading.value || !session.value) {
    return
  }
  finishing.value = true
  try {
    const data = await finishMockInterview(session.value.id)
    session.value = data
    report.value = data.report
    stage.value = 'report'
    await refreshHistory()
    window.scrollTo({ top: 0, behavior: 'smooth' })
    if (!data.report) {
      ElMessage.warning('报告生成异常，请稍后重试')
    }
  }
  catch (error) {
    ElMessage.error(error.message || '生成面试报告失败')
  }
  finally {
    finishing.value = false
  }
}

async function finishEarly() {
  const progress = session.value?.progress
  try {
    await ElMessageBox.confirm(
      `已完成 ${progress?.answered || 0}/${progress?.total || 0} 题，提前结束将按已作答内容生成报告。`,
      '结束面试',
      { confirmButtonText: '生成报告', cancelButtonText: '继续面试', type: 'warning' },
    )
  }
  catch {
    return
  }
  await finishInterview()
}

async function abandonById(id) {
  try {
    await ElMessageBox.confirm(
      '放弃后本次作答不再生成报告，确定放弃吗？',
      '放弃面试',
      { confirmButtonText: '放弃', cancelButtonText: '取消', type: 'warning' },
    )
  }
  catch {
    return
  }
  try {
    await abandonMockInterview(id)
    if (session.value?.id === id) {
      resetToPrepare()
    }
    await refreshHistory()
    ElMessage.success('已放弃本次面试')
  }
  catch (error) {
    ElMessage.error(error.message || '放弃面试失败')
  }
}

function resetToPrepare() {
  stage.value = 'prepare'
  session.value = null
  report.value = null
  transcript.value = []
  currentQuestion.value = null
  pendingFollowUp.value = null
  awaitingReverse.value = false
  reverseAsked.value = 0
  answer.value = ''
  reverseInput.value = ''
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openHistory(item) {
  if (item.status === 'ABANDONED') {
    ElMessage.info('该面试已放弃，没有报告')
    return
  }
  openSessionById(item.id)
}

function goJobs() {
  router.push({ name: 'jobs' })
}

function goResumes() {
  router.push({ name: 'resumes' })
}

function goOptimize() {
  if (!session.value) {
    return
  }
  router.push({
    name: 'optimize',
    query: { jobId: session.value.jobId, resumeId: session.value.resumeId },
  })
}

function goCustomize() {
  if (!session.value) {
    return
  }
  router.push({
    name: 'resume-customize',
    query: { jobId: session.value.jobId, resumeId: session.value.resumeId },
  })
}

watch(jobId, syncResumeForJob, { flush: 'sync' })

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载模拟面试…" class="space-y-5">
    <!-- 准备 -->
    <template v-if="stage === 'prepare'">
      <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 class="text-2xl font-black text-slate-950">模拟面试</h2>
          <p class="mt-1 text-sm text-slate-500">基于岗位 JD 与简历出题，练表达、找出回答里的漏洞。</p>
        </div>
        <span class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white/80 px-3 py-1.5 text-xs font-semibold text-slate-500">
          <Sparkles class="h-3.5 w-3.5 text-blue-500" />
          结束后统一生成报告
        </span>
      </section>

      <div class="grid gap-5 xl:grid-cols-[1.08fr_0.92fr]">
        <section class="soft-card p-5">
          <div class="flex items-center gap-2.5">
            <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
              <MessageSquareText class="h-4.5 w-4.5" />
            </span>
            <div>
              <p class="text-sm font-bold text-slate-800">选择面试信息</p>
              <p class="text-xs text-slate-400">固定题单 + 动态追问，面试过程中不给提示</p>
            </div>
          </div>

          <div v-if="!jobs.length" class="mt-5">
            <EmptyState
              :icon="BriefcaseBusiness"
              title="还没有可面试的岗位"
              description="先在「我的投递」添加一个岗位并填写 JD，再回来开始模拟面试。"
              action-label="去添加岗位"
              @action="goJobs"
            />
          </div>
          <div v-else-if="!resumes.length" class="mt-5">
            <EmptyState
              :icon="FileText"
              title="还没有可用简历"
              description="上传一份文字版简历，或使用简历制作器创建在线简历。"
              action-label="去简历管理"
              @action="goResumes"
            />
          </div>
          <template v-else>
            <div class="mt-4 grid gap-3 sm:grid-cols-2">
              <label class="block">
                <span class="text-[11px] font-bold text-slate-400">目标岗位</span>
                <el-select v-model="jobId" filterable class="mt-1 w-full" placeholder="选择岗位">
                  <el-option
                    v-for="job in jobs"
                    :key="job.id"
                    :label="jobLabel(job)"
                    :value="job.id"
                  />
                </el-select>
              </label>
              <label class="block">
                <span class="text-[11px] font-bold text-slate-400">使用简历</span>
                <el-select v-model="resumeId" filterable class="mt-1 w-full" placeholder="选择简历">
                  <el-option
                    v-for="resume in resumes"
                    :key="resume.id"
                    :label="resumeLabel(resume)"
                    :value="resume.id"
                  />
                </el-select>
              </label>
              <label class="block">
                <span class="text-[11px] font-bold text-slate-400">面试类型</span>
                <el-select v-model="interviewType" class="mt-1 w-full">
                  <el-option label="综合面" value="COMPREHENSIVE" />
                  <el-option label="技术面" value="TECHNICAL" />
                  <el-option label="行为面" value="BEHAVIORAL" />
                </el-select>
              </label>
              <label class="block">
                <span class="text-[11px] font-bold text-slate-400">难度</span>
                <el-select v-model="difficulty" class="mt-1 w-full">
                  <el-option label="基础" value="EASY" />
                  <el-option label="进阶" value="MEDIUM" />
                  <el-option label="挑战" value="HARD" />
                </el-select>
              </label>
            </div>

            <div v-if="selectedJob?.jdText" class="mt-4 rounded-2xl border border-blue-100 bg-blue-50/40 p-4">
              <p class="flex items-center gap-1.5 text-[11px] font-bold text-blue-600">
                <BookOpen class="h-3.5 w-3.5" />
                JD 预览
              </p>
              <p class="mt-2 line-clamp-4 whitespace-pre-line text-xs leading-5 text-slate-600">
                {{ selectedJob.jdText }}
              </p>
            </div>

            <div v-if="activeInterview" class="mt-4 rounded-2xl border border-amber-200 bg-amber-50/70 p-4">
              <p class="flex items-center gap-1.5 text-sm font-bold text-amber-700">
                <CircleAlert class="h-4 w-4" />
                该岗位有一场进行中的模拟面试
              </p>
              <p class="mt-1 text-xs text-amber-600">
                已完成 {{ activeInterview.answered }}/{{ activeInterview.totalQuestions }} 题，继续上次进度或放弃后重新开始。
              </p>
              <div class="mt-3 flex flex-wrap gap-2">
                <button
                  class="inline-flex items-center gap-1.5 rounded-full bg-amber-500 px-4 py-2 text-xs font-bold text-white transition hover:bg-amber-600"
                  @click="openSessionById(activeInterview.id)"
                >
                  <Play class="h-3.5 w-3.5" />
                  继续面试
                </button>
                <button
                  class="rounded-full border border-amber-200 bg-white px-4 py-2 text-xs font-bold text-amber-700 transition hover:bg-amber-50"
                  @click="abandonById(activeInterview.id)"
                >
                  放弃并重新开始
                </button>
              </div>
            </div>

            <button
              class="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-2xl bg-blue-600 px-5 py-3 text-sm font-bold text-white shadow-[0_14px_28px_rgba(37,99,235,0.24)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="starting || Boolean(activeInterview)"
              @click="beginInterview"
            >
              <LoaderCircle v-if="starting" class="h-4 w-4 animate-spin" />
              <Play v-else class="h-4 w-4" />
              {{ starting ? '正在生成题单，约需 10-20 秒…' : (activeInterview ? '请先继续或放弃上次面试' : '开始模拟面试') }}
            </button>

            <p class="mt-3 flex items-start gap-1.5 text-[11px] leading-5 text-slate-400">
              <CircleAlert class="mt-0.5 h-3.5 w-3.5 shrink-0" />
              岗位 JD、简历与你的回答会发送至 DeepSeek，仅用于本次模拟面试与评分，不会用于其他用途。
            </p>
          </template>
        </section>

        <section class="soft-card p-5">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2.5">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-violet-50 text-violet-600">
                <History class="h-4.5 w-4.5" />
              </span>
              <div>
                <p class="text-sm font-bold text-slate-800">面试记录</p>
                <p class="text-xs text-slate-400">进行中的会话可随时恢复</p>
              </div>
            </div>
            <button
              class="rounded-full border border-blue-100 bg-white px-3 py-1.5 text-[11px] font-bold text-slate-500 transition hover:text-blue-600"
              @click="refreshHistory"
            >
              刷新
            </button>
          </div>

          <div v-if="!recentHistory.length" class="mt-5">
            <EmptyState
              :icon="History"
              title="还没有面试记录"
              description="选一个岗位开始第一场模拟面试，结束后这里会保存报告。"
            />
          </div>
          <div v-else class="mt-4 space-y-2">
            <button
              v-for="item in recentHistory"
              :key="item.id"
              class="flex w-full items-center gap-3 rounded-2xl border border-blue-100/80 bg-white/70 p-3 text-left transition hover:border-blue-200 hover:bg-blue-50/40"
              @click="openHistory(item)"
            >
              <span
                class="grid h-9 w-9 shrink-0 place-items-center rounded-xl text-xs font-black"
                :class="item.status === 'COMPLETED'
                  ? 'bg-emerald-50 text-emerald-600'
                  : item.status === 'IN_PROGRESS'
                    ? 'bg-blue-50 text-blue-600'
                    : 'bg-slate-100 text-slate-400'"
              >
                {{ item.status === 'COMPLETED' && item.totalScore !== null ? item.totalScore : (item.status === 'IN_PROGRESS' ? '…' : '—') }}
              </span>
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm font-bold text-slate-800">
                  {{ item.company }} · {{ item.jobTitle }}
                </p>
                <p class="mt-0.5 truncate text-[11px] text-slate-400">
                  {{ statusLabel(item.status) }} · {{ item.answered }}/{{ item.totalQuestions }} 题 · {{ formatDateTime(item.createdAt) }}
                </p>
              </div>
              <LoaderCircle v-if="openingSessionId === item.id" class="h-4 w-4 animate-spin text-blue-500" />
              <ArrowRight v-else class="h-4 w-4 shrink-0 text-slate-300" />
            </button>
          </div>
        </section>
      </div>
    </template>

    <!-- 面试 -->
    <template v-else-if="stage === 'chat' && session">
      <section class="soft-card p-5">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex min-w-0 items-center gap-3">
            <span class="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-blue-600 to-sky-400 text-white">
              <BriefcaseBusiness class="h-5 w-5" />
            </span>
            <div class="min-w-0">
              <p class="truncate text-base font-black text-slate-900">
                {{ session.company }} · {{ session.jobTitle }}
              </p>
              <div class="mt-1 flex flex-wrap items-center gap-2 text-[11px] font-semibold text-slate-500">
                <span class="rounded-full bg-blue-50 px-2 py-0.5 text-blue-600">{{ interviewTypeLabel(session.interviewType) }}</span>
                <span class="rounded-full bg-violet-50 px-2 py-0.5 text-violet-600">{{ difficultyLabel(session.difficulty) }}</span>
                <span>{{ session.resumeName }}</span>
              </div>
            </div>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <div class="min-w-40">
              <div class="flex items-center justify-between text-[11px] font-bold text-slate-400">
                <span>进度</span>
                <span>{{ session.progress?.answered || 0 }}/{{ session.progress?.total || 0 }}</span>
              </div>
              <div class="mt-1.5 h-2 overflow-hidden rounded-full bg-blue-50">
                <div
                  class="h-full rounded-full bg-gradient-to-r from-blue-600 to-sky-400 transition-all duration-500"
                  :style="{ width: `${progressPercent}%` }"
                />
              </div>
            </div>
            <button
              class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white px-3.5 py-2 text-xs font-bold text-slate-500 transition hover:border-rose-200 hover:text-rose-500"
              @click="abandonById(session.id)"
            >
              放弃
            </button>
            <button
              class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white px-3.5 py-2 text-xs font-bold text-slate-600 transition hover:border-blue-200 hover:text-blue-700 disabled:opacity-50"
              :disabled="finishing || !(session.progress?.answered > 0)"
              @click="finishEarly"
            >
              <LoaderCircle v-if="finishing" class="h-3.5 w-3.5 animate-spin" />
              <CheckCircle2 v-else class="h-3.5 w-3.5" />
              提前结束
            </button>
          </div>
        </div>
      </section>

      <section class="soft-card overflow-hidden">
        <div ref="chatBox" class="max-h-[58vh] space-y-5 overflow-y-auto px-4 py-5 sm:px-6">
          <div
            v-for="item in transcript"
            :key="item.id"
            class="flex gap-2.5"
            :class="item.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <span
              v-if="item.role === 'interviewer'"
              class="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-blue-50 text-blue-600"
            >
              <Bot class="h-4 w-4" />
            </span>
            <div class="max-w-[86%]">
              <div
                class="mb-1 flex items-center gap-2"
                :class="item.role === 'user' ? 'justify-end' : 'justify-start'"
              >
                <span class="text-[10px] font-bold text-slate-400">
                  {{ item.role === 'user' ? '我' : '面试官' }}
                </span>
                <span
                  v-if="item.tag"
                  class="rounded-full px-2 py-0.5 text-[10px] font-bold"
                  :class="item.role === 'user' ? 'bg-blue-100 text-blue-600' : bubbleTagTone(item.tag)"
                >
                  {{ item.tag }}
                </span>
              </div>
              <div
                class="whitespace-pre-line rounded-2xl px-4 py-3 text-sm leading-6"
                :class="item.role === 'user'
                  ? 'rounded-tr-md bg-blue-600 text-white shadow-[0_10px_20px_rgba(37,99,235,0.18)]'
                  : 'rounded-tl-md border border-blue-100 bg-white text-slate-700'"
              >
                {{ item.text }}
              </div>
              <p
                v-if="item.basis"
                class="mt-1.5 flex items-start gap-1 text-[11px] leading-4 text-slate-400"
              >
                <BookOpen class="mt-0.5 h-3 w-3 shrink-0" />
                <span class="line-clamp-2">本题依据：{{ item.basis }}</span>
              </p>
            </div>
            <span
              v-if="item.role === 'user'"
              class="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-slate-900 text-white"
            >
              <UserRound class="h-4 w-4" />
            </span>
          </div>

          <div v-if="submitting" class="flex items-center gap-2.5 pl-11 text-xs font-semibold text-slate-400">
            <span class="flex gap-1">
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400 [animation-delay:-0.2s]" />
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400 [animation-delay:-0.1s]" />
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-blue-400" />
            </span>
            面试官正在思考…
          </div>
          <div ref="chatBottom" />
        </div>

        <div class="border-t border-blue-100/80 bg-blue-50/30 p-4 sm:p-5">
          <template v-if="awaitingReverse">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-xs font-bold text-slate-600">
                <span class="rounded-full bg-emerald-50 px-2 py-0.5 text-emerald-600">反问环节</span>
                <span class="ml-2 text-slate-400">可以向面试官提问，最多 2 个问题</span>
              </p>
              <button
                class="inline-flex items-center gap-1.5 rounded-full bg-emerald-600 px-4 py-2 text-xs font-bold text-white transition hover:bg-emerald-700 disabled:opacity-60"
                :disabled="finishing || reverseLoading"
                @click="finishInterview"
              >
                <LoaderCircle v-if="finishing" class="h-3.5 w-3.5 animate-spin" />
                <Trophy v-else class="h-3.5 w-3.5" />
                {{ finishing ? '正在生成报告…' : '结束面试并查看报告' }}
              </button>
            </div>

            <div v-if="reverseAsked < 2" class="mt-3 flex flex-col gap-2 sm:flex-row">
              <textarea
                v-model="reverseInput"
                rows="2"
                maxlength="300"
                placeholder="你想问面试官什么？例如：这个岗位入职后前三个月的主要目标是什么？"
                class="min-h-[64px] flex-1 resize-none rounded-2xl border border-blue-100 bg-white px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-blue-300"
                @keydown="onReverseKeydown"
              />
              <button
                class="inline-flex h-fit items-center justify-center gap-1.5 self-end rounded-2xl bg-blue-600 px-5 py-3 text-sm font-bold text-white transition hover:bg-blue-700 disabled:opacity-60"
                :disabled="reverseLoading || !reverseInput.trim()"
                @click="submitReverse"
              >
                <LoaderCircle v-if="reverseLoading" class="h-4 w-4 animate-spin" />
                <Send v-else class="h-4 w-4" />
                提问
              </button>
            </div>
            <p v-else class="mt-3 text-xs text-slate-400">反问次数已用完，点击上方按钮生成报告。</p>
          </template>

          <template v-else-if="currentQuestion">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-xs font-bold text-slate-500">
                第 {{ currentSeq }} 题
                <span class="ml-1 rounded-full px-2 py-0.5 text-[10px]" :class="typeTone(currentQuestion.type)">
                  {{ typeLabel(currentQuestion.type) }}
                </span>
                <span v-if="pendingFollowUp" class="ml-2 text-[11px] font-semibold text-amber-600">正在回答追问</span>
              </p>
              <span class="text-[11px] font-semibold text-slate-400">{{ answer.length }}/1000</span>
            </div>
            <textarea
              v-model="answer"
              rows="3"
              maxlength="1000"
              :disabled="submitting"
              placeholder="先想清楚结论，再分点讲背景、你的动作和结果；Enter 发送，Shift+Enter 换行"
              class="mt-2 min-h-[86px] w-full resize-none rounded-2xl border border-blue-100 bg-white px-4 py-3 text-sm leading-6 text-slate-700 outline-none transition focus:border-blue-300 disabled:bg-slate-50"
              @keydown="onAnswerKeydown"
            />
            <div class="mt-2 flex flex-wrap items-center justify-between gap-2">
              <button
                class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white px-3.5 py-2 text-xs font-bold text-slate-500 transition hover:border-slate-300 hover:text-slate-700 disabled:opacity-50"
                :disabled="submitting"
                @click="submitAnswer(true)"
              >
                <SkipForward class="h-3.5 w-3.5" />
                跳过本题
              </button>
              <button
                class="inline-flex items-center gap-1.5 rounded-2xl bg-blue-600 px-5 py-2.5 text-sm font-bold text-white shadow-[0_10px_20px_rgba(37,99,235,0.2)] transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="submitting || !answer.trim()"
                @click="submitAnswer(false)"
              >
                <LoaderCircle v-if="submitting" class="h-4 w-4 animate-spin" />
                <Send v-else class="h-4 w-4" />
                提交回答
              </button>
            </div>
          </template>

          <div v-else class="py-4 text-center text-xs text-slate-400">
            <LoaderCircle class="mx-auto h-4 w-4 animate-spin" />
            <p class="mt-2">正在同步面试进度…</p>
          </div>
        </div>
      </section>
    </template>

    <!-- 报告 -->
    <template v-else-if="stage === 'report' && session">
      <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <button
            class="mb-2 inline-flex items-center gap-1 text-xs font-bold text-slate-400 transition hover:text-blue-600"
            @click="resetToPrepare"
          >
            <ArrowLeft class="h-3.5 w-3.5" />
            返回准备页
          </button>
          <h2 class="text-2xl font-black text-slate-950">模拟面试报告</h2>
          <p class="mt-1 text-sm text-slate-500">
            {{ session.company }} · {{ session.jobTitle }} · {{ session.resumeName }}
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white px-4 py-2 text-xs font-bold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
            @click="goOptimize"
          >
            <Sparkles class="h-3.5 w-3.5" />
            去简历优化
          </button>
          <button
            class="inline-flex items-center gap-1.5 rounded-full border border-blue-100 bg-white px-4 py-2 text-xs font-bold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
            @click="goCustomize"
          >
            <FileText class="h-3.5 w-3.5" />
            去简历定制
          </button>
          <button
            class="inline-flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-bold text-white transition hover:bg-blue-700"
            @click="resetToPrepare"
          >
            <RotateCcw class="h-3.5 w-3.5" />
            再来一次
          </button>
        </div>
      </section>

      <template v-if="hasReport">
        <section class="soft-card p-5 sm:p-6">
          <div class="grid gap-5 lg:grid-cols-[auto_1fr] lg:items-center">
            <div class="flex items-center justify-center">
              <MatchScoreRing :score="report.totalScore" :size="136" :stroke="9" :show-label="false" />
            </div>
            <div>
              <p class="text-sm font-black text-slate-800">总体评价</p>
              <p class="mt-2 text-sm leading-6 text-slate-600">{{ report.summary }}</p>
              <div class="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
                <div
                  v-for="item in reportDimensions"
                  :key="item.key"
                  class="rounded-xl border border-blue-50 bg-blue-50/40 px-2 py-2 text-center"
                >
                  <p class="text-[10px] font-bold text-slate-400">{{ item.label }}</p>
                  <p class="mt-1 text-lg font-black text-slate-800">{{ item.value ?? '—' }}</p>
                </div>
              </div>
            </div>
          </div>
        </section>

        <div class="grid gap-5 xl:grid-cols-[1fr_0.85fr]">
          <section class="soft-card p-5">
            <p class="flex items-center gap-2 text-sm font-bold text-slate-800">
              <CircleAlert class="h-4 w-4 text-amber-500" />
              优先改进
            </p>
            <div v-if="report.topImprovements?.length" class="mt-3 space-y-2">
              <div
                v-for="(issue, index) in report.topImprovements"
                :key="index"
                class="rounded-xl border border-slate-100 bg-white/80 p-3"
              >
                <div class="flex items-start gap-2">
                  <span
                    class="mt-0.5 shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-bold"
                    :class="issue.severity === 'HIGH'
                      ? 'bg-rose-50 text-rose-600'
                      : issue.severity === 'MEDIUM'
                        ? 'bg-amber-50 text-amber-600'
                        : 'bg-slate-100 text-slate-500'"
                  >
                    {{ severityLabel(issue.severity) }}
                  </span>
                  <p class="text-xs leading-5 text-slate-600">{{ issue.comment }}</p>
                </div>
                <p v-if="issue.quote" class="mt-1.5 line-clamp-2 text-[11px] leading-4 text-slate-400">
                  原回答：{{ issue.quote }}
                </p>
              </div>
            </div>
            <p v-else class="mt-3 text-xs text-slate-400">本次没有识别出明显问题，继续保持。</p>

            <div v-if="report.suggestions?.length" class="mt-5">
              <p class="text-sm font-bold text-slate-800">可执行建议</p>
              <ul class="mt-2 space-y-1.5">
                <li
                  v-for="(suggestion, index) in report.suggestions"
                  :key="index"
                  class="flex items-start gap-2 text-xs leading-5 text-slate-600"
                >
                  <CheckCircle2 class="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-500" />
                  {{ suggestion }}
                </li>
              </ul>
            </div>
          </section>

          <section class="soft-card p-5">
            <p class="flex items-center gap-2 text-sm font-bold text-slate-800">
              <CircleAlert class="h-4 w-4 text-rose-500" />
              风险提示
            </p>
            <div v-if="report.risks?.length" class="mt-3 flex flex-wrap gap-2">
              <span
                v-for="risk in report.risks"
                :key="risk.flag"
                class="rounded-full bg-rose-50 px-3 py-1.5 text-[11px] font-bold text-rose-600"
              >
                {{ riskLabel(risk.flag) }} · {{ risk.quotes?.length || 0 }} 处
              </span>
            </div>
            <p v-else class="mt-3 text-xs text-slate-400">没有发现明显的高风险回答。</p>

            <div v-if="reportReverse" class="mt-5 border-t border-blue-50 pt-4">
              <p class="text-sm font-bold text-slate-800">反问环节</p>
              <p class="mt-1 text-xs leading-5 text-slate-500">{{ reportReverse.comment }}</p>
              <div v-if="reportReverse.items?.length" class="mt-3 space-y-2">
                <div
                  v-for="(item, index) in reportReverse.items"
                  :key="index"
                  class="rounded-xl border border-blue-50 bg-blue-50/30 p-3"
                >
                  <p class="text-xs font-bold text-slate-700">问：{{ item.question }}</p>
                  <p class="mt-1 text-xs leading-5 text-slate-500">答：{{ item.reply }}</p>
                </div>
              </div>
            </div>

            <div v-if="reportTrend.length > 1" class="mt-5 border-t border-blue-50 pt-4">
              <p class="text-sm font-bold text-slate-800">同岗位趋势</p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                <span
                  v-for="(point, index) in reportTrend"
                  :key="`${point.date}-${point.totalScore}-${index}`"
                  class="rounded-full border border-blue-100 bg-white px-3 py-1 text-[11px] font-bold text-slate-500"
                >
                  {{ point.date }} · {{ point.totalScore }}
                </span>
              </div>
            </div>
          </section>
        </div>

        <section class="soft-card p-5">
          <p class="flex items-center gap-2 text-sm font-bold text-slate-800">
            <MessageSquareText class="h-4 w-4 text-blue-500" />
            逐题回顾
          </p>
          <div class="mt-3 space-y-2.5">
            <details
              v-for="question in reportQuestions"
              :key="question.id"
              class="group rounded-2xl border border-blue-100/80 bg-white/75 p-4"
            >
              <summary class="flex cursor-pointer list-none items-start gap-3 [&::-webkit-details-marker]:hidden">
                <span class="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-blue-50 text-xs font-black text-blue-600">
                  {{ question.seq }}
                </span>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-bold leading-6 text-slate-800">{{ question.question }}</p>
                  <div class="mt-1.5 flex flex-wrap items-center gap-2 text-[10px] font-bold">
                    <span class="rounded-full px-2 py-0.5" :class="typeTone(question.type)">
                      {{ typeLabel(question.type) }}
                    </span>
                    <span v-if="questionAverage(question) !== null" class="rounded-full bg-slate-100 px-2 py-0.5 text-slate-500">
                      单题 {{ questionAverage(question) }}
                    </span>
                    <span
                      v-for="flag in question.riskFlags"
                      :key="flag"
                      class="rounded-full bg-rose-50 px-2 py-0.5 text-rose-500"
                    >
                      {{ riskLabel(flag) }}
                    </span>
                  </div>
                </div>
                <ChevronDown class="mt-1 h-4 w-4 shrink-0 text-slate-300 transition group-open:rotate-180" />
              </summary>

              <div class="mt-4 space-y-4 border-t border-blue-50 pt-4">
                <div>
                  <p class="text-[10px] font-bold uppercase tracking-wider text-slate-400">我的回答</p>
                  <p class="mt-1 whitespace-pre-line text-sm leading-6 text-slate-600">
                    {{ question.answer || '（未作答）' }}
                  </p>
                </div>

                <div v-if="question.followups?.length">
                  <p class="text-[10px] font-bold uppercase tracking-wider text-slate-400">追问记录</p>
                  <div class="mt-2 space-y-2">
                    <div
                      v-for="(followUp, index) in question.followups"
                      :key="index"
                      class="rounded-xl bg-slate-50/80 p-2.5"
                    >
                      <p class="text-xs font-bold text-slate-600">问：{{ followUp.question }}</p>
                      <p class="mt-1 text-xs leading-5 text-slate-500">答：{{ followUp.answer || '（未回答）' }}</p>
                    </div>
                  </div>
                </div>

                <div v-if="question.score?.highlights?.length">
                  <p class="text-[10px] font-bold uppercase tracking-wider text-emerald-500">亮点</p>
                  <ul class="mt-1.5 space-y-1.5">
                    <li
                      v-for="(point, index) in question.score.highlights"
                      :key="index"
                      class="text-xs leading-5 text-slate-600"
                    >
                      <span class="text-emerald-600">✓</span> {{ point.comment }}
                      <span class="text-slate-400">（{{ point.quote }}）</span>
                    </li>
                  </ul>
                </div>

                <div v-if="question.score?.issues?.length">
                  <p class="text-[10px] font-bold uppercase tracking-wider text-rose-500">问题</p>
                  <ul class="mt-1.5 space-y-1.5">
                    <li
                      v-for="(issue, index) in question.score.issues"
                      :key="index"
                      class="text-xs leading-5 text-slate-600"
                    >
                      <span class="text-rose-500">·</span> {{ issue.comment }}
                      <span class="text-slate-400">（{{ issue.quote }}）</span>
                    </li>
                  </ul>
                </div>

                <div v-if="question.score?.suggestions?.length">
                  <p class="text-[10px] font-bold uppercase tracking-wider text-blue-500">建议</p>
                  <ul class="mt-1.5 space-y-1">
                    <li
                      v-for="(suggestion, index) in question.score.suggestions"
                      :key="index"
                      class="text-xs leading-5 text-slate-600"
                    >
                      · {{ suggestion }}
                    </li>
                  </ul>
                </div>

                <div v-if="question.score" class="grid grid-cols-2 gap-2 sm:grid-cols-5">
                  <div
                    v-for="(value, key) in question.score.dimensions"
                    :key="key"
                    class="rounded-lg bg-blue-50/50 px-2 py-1.5 text-center"
                  >
                    <p class="text-[10px] text-slate-400">{{ DIMENSION_LABELS[key] || key }}</p>
                    <p class="text-sm font-black text-slate-700">{{ value ?? '—' }}</p>
                  </div>
                </div>
              </div>
            </details>
          </div>
        </section>
      </template>
      <EmptyState
        v-else
        :icon="Trophy"
        title="报告暂时无法展示"
        description="逐题评分可能未完成，可以稍后从面试记录重新打开。"
      />
    </template>
  </div>
</template>
