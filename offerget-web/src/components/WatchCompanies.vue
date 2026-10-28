<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Building2,
  Globe,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import {
  createWatch,
  deleteWatch,
  discoverDomain,
  fetchWatches,
  runAllCrawl,
  runCrawl,
  updateWatch,
} from '@/api/crawl'
import { formatDateTime } from '@/utils/format'

const emit = defineEmits(['changed'])

const watches = ref([])
const loading = ref(true)
const runningId = ref(null)
const discoveringId = ref(null)
const runningAll = ref(false)

const dialog = ref(false)
const editingId = ref(null)
const form = reactive({
  company: '',
  officialDomain: '',
  industry: '',
  city: '',
  targetGraduationYear: null,
})

const domainDialog = ref(false)
const domainCandidates = ref([])
const domainTarget = ref(null)

async function load() {
  loading.value = true
  try {
    watches.value = await fetchWatches()
  }
  catch (error) {
    ElMessage.error(error.message || '加载关注公司失败')
  }
  finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    company: '',
    officialDomain: '',
    industry: '',
    city: '',
    targetGraduationYear: null,
  })
  dialog.value = true
}

function openEdit(watch) {
  editingId.value = watch.id
  Object.assign(form, {
    company: watch.company,
    officialDomain: watch.officialDomain || '',
    industry: watch.industry || '',
    city: watch.city || '',
    targetGraduationYear: watch.targetGraduationYear || null,
  })
  dialog.value = true
}

async function save() {
  if (!form.company.trim()) {
    ElMessage.warning('请输入公司名称')
    return
  }
  const payload = {
    company: form.company.trim(),
    officialDomain: form.officialDomain.trim() || null,
    industry: form.industry || null,
    city: form.city || null,
    targetGraduationYear: form.targetGraduationYear || null,
  }
  try {
    if (editingId.value) {
      await updateWatch(editingId.value, payload)
    }
    else {
      await createWatch(payload)
    }
    dialog.value = false
    ElMessage.success('已保存')
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

async function remove(watch) {
  try {
    await ElMessageBox.confirm(
      `删除关注「${watch.company}」及它的采集候选？已入库的岗位不受影响。`,
      '删除关注',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }
  try {
    await deleteWatch(watch.id)
    ElMessage.success('已删除')
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

async function discover(watch) {
  discoveringId.value = watch.id
  try {
    const candidates = await discoverDomain(watch.id)
    if (!candidates.length) {
      ElMessage.warning('没有找到明显的官网候选，请手动填写域名')
      return
    }
    domainCandidates.value = candidates
    domainTarget.value = watch
    domainDialog.value = true
  }
  catch (error) {
    ElMessage.error(error.message || '域名发现失败')
  }
  finally {
    discoveringId.value = null
  }
}

async function pickDomain(candidate) {
  const watch = domainTarget.value
  if (!watch) {
    return
  }
  try {
    await updateWatch(watch.id, {
      company: watch.company,
      officialDomain: candidate.domain,
      industry: watch.industry,
      city: watch.city,
      targetGraduationYear: watch.targetGraduationYear,
    })
    domainDialog.value = false
    ElMessage.success('已设置官网域名')
    await load()
  }
  catch (error) {
    ElMessage.error(error.message || '设置失败')
  }
}

async function runAll() {
  if (!watches.value.some((item) => item.officialDomain)) {
    ElMessage.warning('还没有可采集的公司，请先确认官网域名')
    return
  }
  runningAll.value = true
  try {
    const results = await runAllCrawl()
    const ok = results.filter((item) => item.success).length
    const created = results.reduce((sum, item) => sum + item.created, 0)
    const updated = results.reduce((sum, item) => sum + item.updated, 0)
    ElMessage.success(`采集完成：${ok}/${results.length} 家成功，新增 ${created}，更新 ${updated}`)
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '批量采集失败')
  }
  finally {
    runningAll.value = false
  }
}

async function run(watch) {
  if (!watch.officialDomain) {
    ElMessage.warning('请先确认官网域名')
    return
  }
  runningId.value = watch.id
  try {
    const result = await runCrawl(watch.id)
    ElMessage.success(
      `采集完成：新增 ${result.created}，更新 ${result.updated}，无变化 ${result.skipped}`,
    )
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '采集失败')
  }
  finally {
    runningId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载关注公司…" class="space-y-5">
    <section class="soft-card p-5">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-sm font-bold text-slate-800">我的关注</p>
          <p class="mt-0.5 text-xs text-slate-400">
            按官网定向采集岗位与项目时间线，抓到的内容先进入「待审核」。
          </p>
        </div>
        <div class="flex items-center gap-2">
          <el-button
            :loading="runningAll"
            :disabled="!watches.length"
            @click="runAll"
          >
            <RefreshCw v-if="!runningAll" class="mr-1 h-4 w-4" />
            全部采集
          </el-button>
          <el-button type="primary" @click="openCreate">
            <Plus class="mr-1 h-4 w-4" />
            添加关注公司
          </el-button>
        </div>
      </div>
    </section>

    <section v-if="watches.length" class="space-y-3">
      <article v-for="watch in watches" :key="watch.id" class="soft-card p-5">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="truncate text-sm font-bold text-slate-800">{{ watch.company }}</h3>
              <span
                v-if="watch.officialDomain"
                class="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-600"
              >
                域名已确认
              </span>
              <span v-else class="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-600">
                待确认域名
              </span>
              <span
                v-if="watch.draftCount"
                class="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-600"
              >
                待审核 {{ watch.draftCount }}
              </span>
            </div>
            <p class="mt-1 truncate text-xs text-slate-500">
              {{ watch.officialDomain || '未设置官网域名' }}
              <span v-if="watch.industry"> · {{ watch.industry }}</span>
              <span v-if="watch.city"> · {{ watch.city }}</span>
              <span v-if="watch.targetGraduationYear"> · {{ watch.targetGraduationYear }} 届</span>
            </p>
            <p class="mt-0.5 text-[11px] text-slate-400">
              上次采集：{{ watch.lastCrawledAt ? formatDateTime(watch.lastCrawledAt) : '还没有采集过' }}
            </p>
          </div>

          <div class="flex shrink-0 flex-wrap items-center gap-1.5">
            <button
              class="inline-flex items-center gap-1 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700 disabled:opacity-60"
              :disabled="discoveringId === watch.id"
              @click="discover(watch)"
            >
              <Globe class="h-3.5 w-3.5" />
              {{ discoveringId === watch.id ? '查找中…' : '找官网' }}
            </button>
            <button
              class="inline-flex items-center gap-1 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
              @click="openEdit(watch)"
            >
              编辑
            </button>
            <button
              class="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-bold text-white transition hover:bg-blue-700 disabled:opacity-60"
              :disabled="runningId === watch.id || !watch.officialDomain"
              @click="run(watch)"
            >
              <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': runningId === watch.id }" />
              {{ runningId === watch.id ? '采集中…' : '采集' }}
            </button>
            <button
              class="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-slate-400 transition hover:bg-rose-50 hover:text-rose-500"
              @click="remove(watch)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </article>
    </section>

    <EmptyState
      v-else-if="!loading"
      :icon="Building2"
      title="还没有关注任何公司"
      description="添加目标公司后，系统会按官网定向抓取校招岗位与投递时间线。"
    />

    <el-dialog
      v-model="dialog"
      :title="editingId ? '编辑关注公司' : '添加关注公司'"
      width="460px"
    >
      <el-form label-width="84px" label-position="left">
        <el-form-item label="公司名称" required>
          <el-input v-model="form.company" placeholder="如：字节跳动" maxlength="100" />
        </el-form-item>
        <el-form-item label="官网域名">
          <el-input v-model="form.officialDomain" placeholder="如 jobs.bytedance.com" maxlength="200" />
        </el-form-item>
        <el-form-item label="行业">
          <el-input v-model="form.industry" placeholder="如：互联网" maxlength="50" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="如：北京" maxlength="50" />
        </el-form-item>
        <el-form-item label="目标届别">
          <el-input-number
            v-model="form.targetGraduationYear"
            :min="2020"
            :max="2100"
            controls-position="right"
            placeholder="默认取毕业年份"
          />
        </el-form-item>
      </el-form>
      <p class="pl-[84px] text-[11px] text-slate-400">
        不确定域名可以先留空，保存后用「找官网」自动搜索。
      </p>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="domainDialog" title="选择招聘官网" width="560px">
      <p class="mb-3 text-xs text-slate-500">
        以下是搜索「{{ domainTarget?.company }} 校园招聘 官网」得到的候选，挑一个最像官方招聘站的：
      </p>
      <div class="space-y-2">
        <button
          v-for="candidate in domainCandidates"
          :key="candidate.domain"
          class="w-full rounded-xl border border-blue-100 bg-white p-3 text-left transition hover:border-blue-300 hover:bg-blue-50/60"
          @click="pickDomain(candidate)"
        >
          <p class="text-sm font-bold text-slate-800">{{ candidate.domain }}</p>
          <p class="mt-0.5 truncate text-xs text-slate-500">{{ candidate.title }}</p>
          <p class="mt-0.5 truncate text-[11px] text-slate-400">{{ candidate.url }}</p>
        </button>
      </div>
    </el-dialog>
  </div>
</template>
