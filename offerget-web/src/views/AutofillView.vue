<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  BriefcaseBusiness,
  ClipboardCopy,
  Copy,
  GraduationCap,
  Info,
  RefreshCw,
  Sparkles,
  UserRound,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import { fetchAutofill, fetchResumes } from '@/api/resumes'

const resumes = ref([])
const resumeId = ref(null)
const data = ref(null)
const loading = ref(false)

const fields = computed(() => {
  const item = data.value
  if (!item) {
    return []
  }
  return [
    { label: '姓名', value: item.name },
    { label: '手机号', value: item.phone },
    { label: '邮箱', value: item.email },
    { label: '所在城市', value: item.city },
    { label: '学校', value: item.school },
    { label: '专业', value: item.major },
    { label: '学历', value: item.degree },
    { label: '毕业年份', value: item.graduationYear ? `${item.graduationYear} 届` : null },
  ]
})

async function loadResumes() {
  try {
    resumes.value = await fetchResumes()
    const preferred = resumes.value.find((resume) => resume.isDefault) || resumes.value[0]
    if (preferred) {
      resumeId.value = preferred.id
    }
  }
  catch (error) {
    ElMessage.error(error.message || '加载简历失败')
  }
}

async function loadAutofill() {
  if (!resumeId.value) {
    data.value = null
    return
  }
  loading.value = true
  try {
    data.value = await fetchAutofill(resumeId.value)
  }
  catch (error) {
    ElMessage.error(error.message || '提取网申信息失败')
  }
  finally {
    loading.value = false
  }
}

async function copy(text, label = '') {
  if (!text) {
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`已复制${label || '内容'}`)
  }
  catch {
    ElMessage.warning('浏览器拒绝了剪贴板权限，请手动选择文本复制')
  }
}

function allText() {
  const item = data.value
  if (!item) {
    return ''
  }
  const lines = fields.value
    .filter((field) => field.value)
    .map((field) => `${field.label}：${field.value}`)
  if (item.skills?.length) {
    lines.push(`技能：${item.skills.join('、')}`)
  }
  for (const group of [
    { title: '实习经历', items: item.internships },
    { title: '项目经历', items: item.projects },
  ]) {
    if (group.items?.length) {
      lines.push('', `${group.title}：`)
      for (const experience of group.items) {
        if (experience.organization || experience.title) {
          lines.push(`${experience.organization || ''} ${experience.title || ''}`.trim())
        }
        for (const bullet of experience.bullets || []) {
          lines.push(`- ${bullet}`)
        }
      }
    }
  }
  return lines.join('\n')
}

watch(resumeId, loadAutofill)

onMounted(async () => {
  await loadResumes()
  if (resumeId.value) {
    await loadAutofill()
  }
})
</script>

<template>
  <div class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">自动填充</h2>
        <p class="mt-1 text-sm text-slate-500">从简历中提取网申常用字段，逐项复制到招聘系统，减少重复填写。</p>
      </div>
      <el-button :loading="loading" @click="loadAutofill">
        <RefreshCw class="mr-1.5 h-4 w-4" />
        重新提取
      </el-button>
    </section>

    <section class="soft-card p-5">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex items-center gap-2">
          <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
            <ClipboardCopy class="h-4.5 w-4.5" />
          </span>
          <div>
            <p class="text-sm font-bold text-slate-800">选择简历</p>
            <p class="text-xs text-slate-400">优先使用在线简历的结构化内容，上传简历则按文本提取</p>
          </div>
        </div>
        <el-select v-model="resumeId" placeholder="选择简历" class="w-full sm:!w-72">
          <el-option
            v-for="resume in resumes"
            :key="resume.id"
            :label="resume.versionLabel ? `${resume.name}（${resume.versionLabel}）` : resume.name"
            :value="resume.id"
          />
        </el-select>
      </div>

      <div class="mt-4 flex items-start gap-2 rounded-xl border border-blue-100 bg-blue-50/50 px-3 py-2.5">
        <Info class="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
        <p class="text-xs leading-5 text-slate-500">
          这里只做信息整理，不会自动填写第三方网申表单。请复制后核对，再粘贴到招聘系统。
        </p>
      </div>
    </section>

    <template v-if="data">
      <section v-loading="loading" element-loading-text="正在提取网申信息…" class="grid gap-5 xl:grid-cols-[1.1fr_1fr]">
        <div class="soft-card p-5">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="grid h-8 w-8 place-items-center rounded-lg bg-blue-50 text-blue-600">
                <UserRound class="h-4 w-4" />
              </span>
              <p class="text-sm font-bold text-slate-800">基本信息</p>
            </div>
            <button
              class="inline-flex items-center gap-1 rounded-full bg-blue-600 px-3 py-1.5 text-[11px] font-bold text-white transition hover:bg-blue-700"
              @click="copy(allText(), '全部网申信息')"
            >
              <Copy class="h-3 w-3" />
              复制全部
            </button>
          </div>

          <div class="mt-3 grid gap-2 sm:grid-cols-2">
            <div
              v-for="field in fields"
              :key="field.label"
              class="flex items-center justify-between gap-2 rounded-xl border border-blue-50 bg-white/70 px-3 py-2"
            >
              <div class="min-w-0">
                <p class="text-[10px] text-slate-400">{{ field.label }}</p>
                <p class="truncate text-sm font-semibold" :class="field.value ? 'text-slate-700' : 'text-slate-300'">
                  {{ field.value || '未识别' }}
                </p>
              </div>
              <button
                class="shrink-0 text-slate-300 transition hover:text-blue-600 disabled:opacity-30"
                :disabled="!field.value"
                @click="copy(field.value, field.label)"
              >
                <Copy class="h-3.5 w-3.5" />
              </button>
            </div>
          </div>

          <div class="mt-4 flex items-center gap-2">
            <span class="grid h-8 w-8 place-items-center rounded-lg bg-violet-50 text-violet-600">
              <Sparkles class="h-4 w-4" />
            </span>
            <p class="text-sm font-bold text-slate-800">技能</p>
          </div>
          <div class="mt-2.5 flex flex-wrap gap-1.5">
            <span
              v-for="skill in data.skills"
              :key="skill"
              class="rounded-full bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700"
            >
              {{ skill }}
            </span>
            <span v-if="!data.skills?.length" class="text-xs text-slate-400">未识别到技能关键词</span>
          </div>
        </div>

        <div class="space-y-5">
          <div class="soft-card p-5">
            <div class="flex items-center gap-2">
              <span class="grid h-8 w-8 place-items-center rounded-lg bg-emerald-50 text-emerald-600">
                <BriefcaseBusiness class="h-4 w-4" />
              </span>
              <p class="text-sm font-bold text-slate-800">实习经历</p>
            </div>
            <div v-if="data.internships?.length" class="mt-3 space-y-3">
              <div v-for="(experience, index) in data.internships" :key="index" class="rounded-xl border border-blue-50 bg-white/70 p-3">
                <p v-if="experience.organization || experience.title" class="text-xs font-bold text-slate-700">
                  {{ [experience.organization, experience.title, experience.period].filter(Boolean).join(' · ') }}
                </p>
                <ul class="mt-1 space-y-1">
                  <li v-for="(bullet, index2) in experience.bullets" :key="index2" class="flex gap-1.5 text-[11px] leading-5 text-slate-600">
                    <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-emerald-400" />
                    {{ bullet }}
                  </li>
                </ul>
              </div>
            </div>
            <p v-else class="mt-3 text-xs text-slate-400">简历中没有识别到实习经历。</p>
          </div>

          <div class="soft-card p-5">
            <div class="flex items-center gap-2">
              <span class="grid h-8 w-8 place-items-center rounded-lg bg-amber-50 text-amber-600">
                <GraduationCap class="h-4 w-4" />
              </span>
              <p class="text-sm font-bold text-slate-800">项目经历</p>
            </div>
            <div v-if="data.projects?.length" class="mt-3 space-y-3">
              <div v-for="(experience, index) in data.projects" :key="index" class="rounded-xl border border-blue-50 bg-white/70 p-3">
                <p v-if="experience.organization || experience.title" class="text-xs font-bold text-slate-700">
                  {{ [experience.organization, experience.title, experience.period].filter(Boolean).join(' · ') }}
                </p>
                <ul class="mt-1 space-y-1">
                  <li v-for="(bullet, index2) in experience.bullets" :key="index2" class="flex gap-1.5 text-[11px] leading-5 text-slate-600">
                    <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-amber-400" />
                    {{ bullet }}
                  </li>
                </ul>
              </div>
            </div>
            <p v-else class="mt-3 text-xs text-slate-400">简历中没有识别到项目经历。</p>
          </div>
        </div>
      </section>
    </template>

    <EmptyState
      v-else-if="!loading"
      :icon="ClipboardCopy"
      title="还没有可提取的简历"
      description="上传一份文字版简历，或使用简历制作器创建在线简历后，这里会自动生成网申信息卡。"
      action-label="去简历管理"
      @action="$router.push({ name: 'resumes' })"
    />
  </div>
</template>
