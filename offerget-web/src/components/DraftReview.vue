<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Check,
  ExternalLink,
  RefreshCw,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import {
  approveDraft,
  approveDrafts,
  fetchDrafts,
  rejectDraft,
  updateDraft,
} from '@/api/crawl'
import { formatDate } from '@/utils/format'

const emit = defineEmits(['changed'])

const drafts = ref([])
const loading = ref(true)
const selected = ref([])
const busyId = ref(null)

const dialog = ref(false)
const editing = ref(null)
const form = reactive({
  jobTitle: '',
  infoType: 'POSITION',
  batch: '其他',
  city: '',
  deadline: null,
  applyUrl: '',
  tags: '',
  description: '',
})

const BATCHES = ['提前批', '正式批', '补录', '其他']

async function load() {
  loading.value = true
  try {
    drafts.value = await fetchDrafts('DRAFT')
  }
  catch (error) {
    ElMessage.error(error.message || '加载待审核条目失败')
  }
  finally {
    loading.value = false
  }
}

function onSelectionChange(rows) {
  selected.value = rows
}

function confidenceClass(value) {
  if (value == null) {
    return 'text-slate-400'
  }
  if (value >= 0.8) {
    return 'text-emerald-600 font-bold'
  }
  if (value >= 0.65) {
    return 'text-amber-600 font-bold'
  }
  return 'text-rose-500 font-bold'
}

async function approve(row) {
  busyId.value = row.id
  try {
    await approveDraft(row.id)
    ElMessage.success('已写入校招信息库')
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '确认失败')
  }
  finally {
    busyId.value = null
  }
}

async function reject(row) {
  try {
    await ElMessageBox.confirm(`驳回「${row.jobTitle}」？`, '驳回采集条目', {
      type: 'warning',
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
    })
  }
  catch {
    return
  }
  busyId.value = row.id
  try {
    await rejectDraft(row.id)
    ElMessage.success('已驳回')
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '驳回失败')
  }
  finally {
    busyId.value = null
  }
}

async function approveSelected() {
  if (!selected.value.length) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将 ${selected.value.length} 条写入校招信息库？同公司同名岗位会更新期限而不是重复添加。`,
      '批量确认',
      { confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }
  try {
    const approved = await approveDrafts(selected.value.map((row) => row.id))
    ElMessage.success(`已确认 ${approved.length} 条`)
    await load()
    emit('changed')
  }
  catch (error) {
    ElMessage.error(error.message || '批量确认失败')
  }
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    jobTitle: row.jobTitle,
    infoType: row.infoType || 'POSITION',
    batch: row.batch || '其他',
    city: row.city || '',
    deadline: row.deadline || null,
    applyUrl: row.applyUrl || '',
    tags: row.tags?.join(',') || '',
    description: row.description || '',
  })
  dialog.value = true
}

async function saveEdit() {
  if (!editing.value) {
    return
  }
  if (!form.jobTitle.trim()) {
    ElMessage.warning('岗位名称不能为空')
    return
  }
  try {
    await updateDraft(editing.value.id, {
      jobTitle: form.jobTitle.trim(),
      infoType: form.infoType,
      batch: form.batch,
      city: form.city || null,
      deadline: form.deadline || null,
      applyUrl: form.applyUrl.trim() || null,
      tags: form.tags.trim() || null,
      description: form.description.trim() || null,
    })
    dialog.value = false
    ElMessage.success('已保存')
    await load()
  }
  catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载待审核…" class="space-y-5">
    <section class="soft-card p-5">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-sm font-bold text-slate-800">待审核</p>
          <p class="mt-0.5 text-xs text-slate-400">
            AI 从官网采集到的岗位与时间线，确认后才会进入校招信息库。
          </p>
        </div>
        <div class="flex items-center gap-2">
          <el-button :disabled="!selected.length" @click="approveSelected">
            批量确认（{{ selected.length }}）
          </el-button>
          <el-button @click="load">
            <RefreshCw class="mr-1 h-4 w-4" />
            刷新
          </el-button>
        </div>
      </div>
    </section>

    <section v-if="drafts.length" class="soft-card overflow-hidden p-2">
      <el-table
        :data="drafts"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="px-6 py-3 text-xs leading-5 text-slate-600">
              <p class="font-bold text-slate-500">原文证据</p>
              <p class="mt-1 whitespace-pre-wrap">{{ row.evidence || '模型没有给出证据片段' }}</p>
              <p class="mt-3 font-bold text-slate-500">来源页</p>
              <a
                :href="row.sourceUrl"
                target="_blank"
                rel="noopener"
                class="text-blue-600 underline decoration-blue-300"
              >
                {{ row.sourceUrl }}
              </a>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="公司 / 岗位" min-width="220">
          <template #default="{ row }">
            <p class="text-sm font-bold text-slate-800">{{ row.jobTitle }}</p>
            <p class="text-xs text-slate-400">
              {{ row.company }}<span v-if="row.city"> · {{ row.city }}</span>
            </p>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="row.infoType === 'PROJECT' ? 'warning' : 'info'" size="small">
              {{ row.infoType === 'PROJECT' ? '招聘项目' : '岗位' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="批次" width="90">
          <template #default="{ row }">
            <span class="text-xs text-slate-500">{{ row.batch }}</span>
          </template>
        </el-table-column>
        <el-table-column label="截止" width="110">
          <template #default="{ row }">
            <span class="text-xs text-slate-500">
              {{ row.deadline ? formatDate(row.deadline) : '未注明' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="86">
          <template #default="{ row }">
            <span class="text-xs" :class="confidenceClass(row.confidence)">
              {{ row.confidence == null ? '-' : `${Math.round(row.confidence * 100)}%` }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="64">
          <template #default="{ row }">
            <a
              :href="row.sourceUrl"
              target="_blank"
              rel="noopener"
              class="text-blue-500 transition hover:text-blue-700"
            >
              <ExternalLink class="h-4 w-4" />
            </a>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="success"
              size="small"
              :loading="busyId === row.id"
              @click="approve(row)"
            >
              确认
            </el-button>
            <el-button link type="danger" size="small" @click="reject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <EmptyState
      v-else-if="!loading"
      :icon="Check"
      title="没有待审核的条目"
      description="在「我的关注」里点「采集」，抓到的岗位会先出现在这里等你确认。"
    />

    <el-dialog v-model="dialog" title="编辑采集条目" width="520px">
      <el-form label-width="84px" label-position="left">
        <el-form-item label="岗位名称" required>
          <el-input v-model="form.jobTitle" maxlength="120" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.infoType">
            <el-radio value="POSITION">岗位</el-radio>
            <el-radio value="PROJECT">招聘项目</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="批次">
          <el-select v-model="form.batch" class="w-full">
            <el-option v-for="item in BATCHES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" maxlength="50" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="form.deadline"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="未注明"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item label="投递链接">
          <el-input v-model="form.applyUrl" maxlength="300" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="逗号分隔" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
