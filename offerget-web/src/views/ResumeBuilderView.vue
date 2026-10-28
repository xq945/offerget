<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  BriefcaseBusiness,
  GraduationCap,
  Plus,
  Printer,
  Save,
  Sparkles,
  Trash2,
} from 'lucide-vue-next'

import { fetchResume, saveBuilderResume } from '@/api/resumes'

const route = useRoute()
const router = useRouter()

const editingId = ref(null)
const saving = ref(false)
const loading = ref(false)

const form = reactive({
  name: '我的在线简历',
  versionLabel: '制作器版',
  basic: { name: '', phone: '', email: '', city: '', jobIntention: '' },
  educations: [createEducation()],
  internships: [],
  projects: [],
  skills: [],
  summary: '',
})

const skillOptions = [
  'Axure', 'Figma', 'SQL', 'Python', 'Java', 'Spring Boot', 'MySQL', 'Redis',
  '数据分析', '用户调研', '需求分析', 'PRD', '竞品分析', 'Docker', 'Linux', 'Git',
  'Vue', 'JavaScript', '项目管理', '用户增长',
]

function createEducation() {
  return { school: '', major: '', degree: '本科', startDate: '', endDate: '', description: '' }
}

function createExperience() {
  return { organization: '', title: '', startDate: '', endDate: '', bulletsText: '' }
}

function addEducation() {
  form.educations.push(createEducation())
}

function removeEducation(index) {
  form.educations.splice(index, 1)
}

function addExperience(type) {
  form[type].push(createExperience())
}

function removeExperience(type, index) {
  form[type].splice(index, 1)
}

const previewEducations = computed(() =>
  form.educations.filter((item) => item.school || item.major || item.description),
)
const previewInternships = computed(() =>
  form.internships.filter((item) => item.organization || item.title || item.bulletsText),
)
const previewProjects = computed(() =>
  form.projects.filter((item) => item.organization || item.title || item.bulletsText),
)

function bulletsOf(item) {
  return (item.bulletsText || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
}

function contactLine() {
  const values = [form.basic.phone, form.basic.email, form.basic.city].filter(Boolean)
  return values.length ? values.join(' · ') : '电话 · 邮箱 · 城市'
}

function toExperiencePayload(item) {
  return {
    organization: item.organization,
    title: item.title,
    startDate: item.startDate,
    endDate: item.endDate,
    bullets: bulletsOf(item),
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入简历名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      versionLabel: form.versionLabel.trim(),
      basic: { ...form.basic },
      educations: form.educations.filter((item) => item.school || item.major || item.description),
      internships: form.internships
        .filter((item) => item.organization || item.title || item.bulletsText)
        .map(toExperiencePayload),
      projects: form.projects
        .filter((item) => item.organization || item.title || item.bulletsText)
        .map(toExperiencePayload),
      skills: form.skills,
      summary: form.summary,
    }
    const saved = await saveBuilderResume(editingId.value, payload)
    editingId.value = saved.id
    ElMessage.success('简历已保存，可在简历管理中查看并用于 AI 匹配')
  }
  catch (error) {
    ElMessage.error(error.message || '保存简历失败')
  }
  finally {
    saving.value = false
  }
}

function printResume() {
  window.print()
}

function fillSample() {
  form.basic = {
    name: '张同学',
    phone: '13800138000',
    email: 'zhang@example.com',
    city: '杭州',
    jobIntention: '产品经理',
  }
  form.educations = [{
    school: '浙江大学',
    major: '信息管理与信息系统',
    degree: '本科',
    startDate: '2023.09',
    endDate: '2027.06',
    description: '主修课程：数据结构、数据库原理、产品设计与用户研究',
  }]
  form.internships = [{
    organization: '字节跳动',
    title: '产品实习生',
    startDate: '2026.06',
    endDate: '2026.09',
    bulletsText: '完成校园场景用户调研与需求分析，输出 3 份 PRD 并推动上线，日活提升 12%\n使用 SQL 完成数据分析，搭建用户分层看板',
  }]
  form.projects = [{
    organization: '校园二手交易平台',
    title: '产品负责人',
    startDate: '2025.09',
    endDate: '2026.01',
    bulletsText: '完成竞品分析与原型设计（Axure），组织 5 人团队完成两轮迭代上线',
  }]
  form.skills = ['Axure', 'Figma', 'SQL', '数据分析', '用户调研', 'PRD']
  form.summary = '对校园产品有热情，擅长从数据和用户反馈中定位问题并推动落地。'
}

onMounted(async () => {
  const id = Number(route.query.id)
  if (!id) {
    return
  }
  loading.value = true
  try {
    const resume = await fetchResume(id)
    if (!resume.contentJson) {
      ElMessage.warning('该简历不是在线简历，无法在制作器中编辑')
      router.replace({ name: 'resumes' })
      return
    }
    const data = JSON.parse(resume.contentJson)
    editingId.value = id
    form.name = data.name || resume.name
    form.versionLabel = data.versionLabel || resume.versionLabel || '制作器版'
    form.basic = { name: '', phone: '', email: '', city: '', jobIntention: '', ...(data.basic || {}) }
    form.educations = (data.educations?.length ? data.educations : [createEducation()])
      .map((item) => ({ ...createEducation(), ...item }))
    form.internships = (data.internships || [])
      .map((item) => ({ ...createExperience(), ...item, bulletsText: (item.bullets || []).join('\n') }))
    form.projects = (data.projects || [])
      .map((item) => ({ ...createExperience(), ...item, bulletsText: (item.bullets || []).join('\n') }))
    form.skills = data.skills || []
    form.summary = data.summary || ''
  }
  catch (error) {
    ElMessage.error(error.message || '加载简历失败')
  }
  finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载简历…" class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between print:hidden">
      <div>
        <button class="inline-flex items-center gap-1 text-xs font-semibold text-slate-400 transition hover:text-blue-600" @click="router.back()">
          <ArrowLeft class="h-3.5 w-3.5" />
          返回
        </button>
        <h2 class="mt-2 text-2xl font-black text-slate-950">简历制作器</h2>
        <p class="mt-1 text-sm text-slate-500">填写表单实时预览，保存后会自动拼接文本用于 AI 匹配。</p>
      </div>
      <div class="flex flex-wrap gap-2">
        <el-button @click="fillSample">填入示例</el-button>
        <el-button @click="printResume">
          <Printer class="mr-1.5 h-4 w-4" />
          打印 / 导出 PDF
        </el-button>
        <el-button type="primary" :loading="saving" @click="save">
          <Save class="mr-1.5 h-4 w-4" />
          保存简历
        </el-button>
      </div>
    </section>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,520px)] print:block">
      <!-- 表单 -->
      <div class="space-y-4 print:hidden">
        <section class="soft-card p-5">
          <p class="text-sm font-bold text-slate-800">简历信息</p>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            <el-input v-model="form.name" placeholder="简历名称，如：产品经理版-校招">
              <template #prepend>名称</template>
            </el-input>
            <el-input v-model="form.versionLabel" placeholder="版本标签，如：产品版">
              <template #prepend>版本</template>
            </el-input>
          </div>
        </section>

        <section class="soft-card p-5">
          <p class="text-sm font-bold text-slate-800">基本信息</p>
          <div class="mt-3 grid gap-3 sm:grid-cols-2">
            <el-input v-model="form.basic.name" placeholder="姓名" />
            <el-input v-model="form.basic.jobIntention" placeholder="求职意向，如：产品经理" />
            <el-input v-model="form.basic.phone" placeholder="手机号" />
            <el-input v-model="form.basic.email" placeholder="邮箱" />
            <el-input v-model="form.basic.city" placeholder="所在城市" class="sm:col-span-2" />
          </div>
        </section>

        <section class="soft-card p-5">
          <div class="flex items-center justify-between">
            <p class="flex items-center gap-2 text-sm font-bold text-slate-800">
              <GraduationCap class="h-4 w-4 text-blue-500" />
              教育背景
            </p>
            <button class="inline-flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700" @click="addEducation">
              <Plus class="h-3.5 w-3.5" />
              添加
            </button>
          </div>

          <div
            v-for="(education, index) in form.educations"
            :key="index"
            class="mt-3 rounded-xl border border-blue-50 bg-blue-50/30 p-3"
          >
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-slate-400">教育经历 {{ index + 1 }}</span>
              <button class="text-slate-300 transition hover:text-rose-500" @click="removeEducation(index)">
                <Trash2 class="h-3.5 w-3.5" />
              </button>
            </div>
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              <el-input v-model="education.school" placeholder="学校" />
              <el-input v-model="education.major" placeholder="专业" />
              <el-input v-model="education.degree" placeholder="学历，如：本科" />
              <div class="flex gap-2">
                <el-input v-model="education.startDate" placeholder="开始，如 2023.09" />
                <el-input v-model="education.endDate" placeholder="结束，如 2027.06" />
              </div>
            </div>
            <el-input
              v-model="education.description"
              class="mt-2"
              type="textarea"
              :rows="2"
              placeholder="主修课程、奖学金、校园经历等（可选）"
            />
          </div>
        </section>

        <section v-for="block in [
          { key: 'internships', title: '实习经历', icon: BriefcaseBusiness },
          { key: 'projects', title: '项目经历', icon: Sparkles },
        ]" :key="block.key" class="soft-card p-5">
          <div class="flex items-center justify-between">
            <p class="flex items-center gap-2 text-sm font-bold text-slate-800">
              <component :is="block.icon" class="h-4 w-4 text-blue-500" />
              {{ block.title }}
            </p>
            <button class="inline-flex items-center gap-1 text-xs font-bold text-blue-600 hover:text-blue-700" @click="addExperience(block.key)">
              <Plus class="h-3.5 w-3.5" />
              添加
            </button>
          </div>

          <p v-if="!form[block.key].length" class="mt-3 text-xs text-slate-400">
            还没有{{ block.title }}，点击右上角添加。
          </p>

          <div
            v-for="(item, index) in form[block.key]"
            :key="index"
            class="mt-3 rounded-xl border border-blue-50 bg-blue-50/30 p-3"
          >
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-slate-400">{{ block.title }} {{ index + 1 }}</span>
              <button class="text-slate-300 transition hover:text-rose-500" @click="removeExperience(block.key, index)">
                <Trash2 class="h-3.5 w-3.5" />
              </button>
            </div>
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              <el-input v-model="item.organization" :placeholder="block.key === 'projects' ? '项目名称' : '公司名称'" />
              <el-input v-model="item.title" :placeholder="block.key === 'projects' ? '担任角色' : '岗位名称'" />
              <el-input v-model="item.startDate" placeholder="开始，如 2026.06" />
              <el-input v-model="item.endDate" placeholder="结束，如 2026.09" />
            </div>
            <el-input
              v-model="item.bulletsText"
              class="mt-2"
              type="textarea"
              :rows="3"
              placeholder="每行一条，写清动作、方法与量化结果"
            />
          </div>
        </section>

        <section class="soft-card p-5">
          <p class="text-sm font-bold text-slate-800">技能</p>
          <el-select
            v-model="form.skills"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入技能，回车添加"
            class="mt-3 w-full"
          >
            <el-option v-for="skill in skillOptions" :key="skill" :label="skill" :value="skill" />
          </el-select>

          <p class="mt-4 text-sm font-bold text-slate-800">自我评价</p>
          <el-input
            v-model="form.summary"
            class="mt-2"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="一句话说明你的优势与求职方向"
          />
        </section>
      </div>

      <!-- A4 预览 -->
      <div class="print-area print:w-full">
        <div class="resume-paper sticky top-24 rounded-2xl border border-blue-100 bg-white p-6 shadow-[0_20px_50px_rgba(37,99,235,0.12)]">
          <header class="border-b-2 border-slate-900 pb-3">
            <div class="flex flex-wrap items-end justify-between gap-2">
              <h1 class="text-2xl font-black text-slate-900">{{ form.basic.name || '你的姓名' }}</h1>
              <span class="text-xs font-semibold text-slate-500">{{ form.basic.jobIntention || '求职意向' }}</span>
            </div>
            <p class="mt-2 text-xs text-slate-500">{{ contactLine() }}</p>
          </header>

          <section v-if="previewEducations.length" class="mt-4">
            <h2 class="text-xs font-black uppercase tracking-[0.18em] text-blue-700">教育背景</h2>
            <div v-for="(education, index) in previewEducations" :key="index" class="mt-2">
              <div class="flex flex-wrap items-baseline justify-between gap-2 text-xs font-bold text-slate-800">
                <span>
                  {{ education.school || '学校' }}
                  <span class="ml-1 font-normal text-slate-500">{{ education.major }}</span>
                  <span v-if="education.degree" class="ml-1 rounded bg-slate-100 px-1 py-0.5 text-[9px] font-semibold text-slate-500">{{ education.degree }}</span>
                </span>
                <span class="text-[10px] font-normal text-slate-400">
                  {{ [education.startDate, education.endDate].filter(Boolean).join(' - ') }}
                </span>
              </div>
              <p v-if="education.description" class="mt-0.5 text-xs leading-5 text-slate-600">{{ education.description }}</p>
            </div>
          </section>

          <section v-if="previewInternships.length" class="mt-4">
            <h2 class="text-xs font-black uppercase tracking-[0.18em] text-blue-700">实习经历</h2>
            <div v-for="(item, index) in previewInternships" :key="index" class="mt-2">
              <div class="flex flex-wrap items-baseline justify-between gap-2 text-xs font-bold text-slate-800">
                <span>{{ item.organization || '公司' }} <span class="ml-1 font-normal text-slate-500">{{ item.title }}</span></span>
                <span class="text-[10px] font-normal text-slate-400">
                  {{ [item.startDate, item.endDate].filter(Boolean).join(' - ') }}
                </span>
              </div>
              <ul v-if="bulletsOf(item).length" class="mt-1 space-y-0.5">
                <li v-for="(bullet, index2) in bulletsOf(item)" :key="index2" class="flex gap-1.5 text-xs leading-5 text-slate-600">
                  <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-400" />
                  {{ bullet }}
                </li>
              </ul>
            </div>
          </section>

          <section v-if="previewProjects.length" class="mt-4">
            <h2 class="text-xs font-black uppercase tracking-[0.18em] text-blue-700">项目经历</h2>
            <div v-for="(item, index) in previewProjects" :key="index" class="mt-2">
              <div class="flex flex-wrap items-baseline justify-between gap-2 text-xs font-bold text-slate-800">
                <span>{{ item.organization || '项目' }} <span class="ml-1 font-normal text-slate-500">{{ item.title }}</span></span>
                <span class="text-[10px] font-normal text-slate-400">
                  {{ [item.startDate, item.endDate].filter(Boolean).join(' - ') }}
                </span>
              </div>
              <ul v-if="bulletsOf(item).length" class="mt-1 space-y-0.5">
                <li v-for="(bullet, index2) in bulletsOf(item)" :key="index2" class="flex gap-1.5 text-xs leading-5 text-slate-600">
                  <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-400" />
                  {{ bullet }}
                </li>
              </ul>
            </div>
          </section>

          <section v-if="form.skills.length" class="mt-4">
            <h2 class="text-xs font-black uppercase tracking-[0.18em] text-blue-700">技能</h2>
            <p class="mt-1.5 text-xs leading-5 text-slate-600">{{ form.skills.join(' · ') }}</p>
          </section>

          <section v-if="form.summary" class="mt-4">
            <h2 class="text-xs font-black uppercase tracking-[0.18em] text-blue-700">自我评价</h2>
            <p class="mt-1.5 text-xs leading-5 text-slate-600">{{ form.summary }}</p>
          </section>

          <p v-if="!form.basic.name && !previewEducations.length && !previewInternships.length" class="mt-8 text-center text-xs text-slate-300">
            左侧填写后，这里会实时生成 A4 简历预览
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
@media print {
  @page {
    size: A4;
    margin: 14mm;
  }

  body,
  .page-bg {
    background: #ffffff !important;
  }

  .print-area {
    grid-column: 1 / -1 !important;
    width: 100% !important;
  }

  .resume-paper {
    position: static !important;
    /* A4 宽 210mm，扣除 @page 左右各 14mm 边距 */
    width: 180mm !important;
    max-width: 180mm !important;
    margin: 0 auto !important;
    padding: 0 !important;
    border: none !important;
    border-radius: 0 !important;
    box-shadow: none !important;
  }
}
</style>
