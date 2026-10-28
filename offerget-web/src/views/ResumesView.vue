<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChevronDown,
  FileText,
  Plus,
  Star,
  UploadCloud,
} from 'lucide-vue-next'

import EmptyState from '@/components/EmptyState.vue'
import {
  deleteResume,
  fetchResume,
  fetchResumes,
  setDefaultResume,
  updateResume,
  uploadResume,
} from '@/api/resumes'
import { formatDate, formatDateTime, formatFileSize } from '@/utils/format'

const router = useRouter()

const resumes = ref([])
const loading = ref(true)

const fileInput = ref(null)
const uploadDialog = ref(false)
const pendingFile = ref(null)
const uploadForm = reactive({ name: '', versionLabel: '自定义' })
const uploading = ref(false)

const previewDrawer = ref(false)
const previewData = ref(null)
const previewLoading = ref(false)

const renameDialog = ref(false)
const renameTarget = ref(null)
const renameForm = reactive({ name: '', versionLabel: '' })
const renaming = ref(false)

const versionTags = ['产品版', '运营版', '后端版', '前端版', '算法版', '数据版', '自定义']

const allowedExtensions = ['pdf', 'doc', 'docx', 'txt']

async function loadResumes() {
  loading.value = true
  try {
    resumes.value = await fetchResumes()
  }
  catch (error) {
    ElMessage.error(error.message || '加载简历失败')
  }
  finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

function acceptFile(file) {
  if (!file) {
    return
  }
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowedExtensions.includes(extension)) {
    ElMessage.warning('仅支持 PDF、DOC、DOCX、TXT 格式')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('文件不能超过 10MB')
    return
  }
  pendingFile.value = file
  uploadForm.name = file.name.replace(/\.[^.]+$/, '')
  uploadForm.versionLabel = '自定义'
  uploadDialog.value = true
}

function onFileChange(event) {
  const file = event.target.files?.[0]
  acceptFile(file)
  event.target.value = ''
}

function onDrop(event) {
  acceptFile(event.dataTransfer?.files?.[0])
}

async function submitUpload() {
  if (!pendingFile.value) {
    return
  }
  uploading.value = true
  try {
    await uploadResume(pendingFile.value, uploadForm.name.trim(), uploadForm.versionLabel)
    ElMessage.success('简历上传成功')
    uploadDialog.value = false
    pendingFile.value = null
    loadResumes()
  }
  catch (error) {
    ElMessage.error(error.message || '上传失败')
  }
  finally {
    uploading.value = false
  }
}

async function preview(resume) {
  previewDrawer.value = true
  previewLoading.value = true
  try {
    previewData.value = await fetchResume(resume.id)
  }
  catch (error) {
    ElMessage.error(error.message || '加载简历内容失败')
    previewDrawer.value = false
  }
  finally {
    previewLoading.value = false
  }
}

async function makeDefault(resume) {
  try {
    await setDefaultResume(resume.id)
    ElMessage.success('已设为默认版本')
    loadResumes()
  }
  catch (error) {
    ElMessage.error(error.message || '设置失败')
  }
}

function openRename(resume) {
  renameTarget.value = resume
  renameForm.name = resume.name
  renameForm.versionLabel = resume.versionLabel || ''
  renameDialog.value = true
}

async function submitRename() {
  if (!renameForm.name.trim()) {
    ElMessage.warning('请输入简历名称')
    return
  }
  renaming.value = true
  try {
    await updateResume(renameTarget.value.id, {
      name: renameForm.name.trim(),
      versionLabel: renameForm.versionLabel,
    })
    ElMessage.success('已保存')
    renameDialog.value = false
    loadResumes()
  }
  catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
  finally {
    renaming.value = false
  }
}

async function confirmDelete(resume) {
  try {
    await ElMessageBox.confirm(
      `删除「${resume.name}」后不可恢复，确定继续吗？`,
      '删除简历',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  }
  catch {
    return
  }
  try {
    await deleteResume(resume.id)
    ElMessage.success('简历已删除')
    loadResumes()
  }
  catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

function handleCommand(command, resume) {
  if (command === 'default') {
    makeDefault(resume)
  }
  else if (command === 'customize') {
    router.push({ name: 'resume-customize', query: { resumeId: resume.id } })
  }
  else if (command === 'rename') {
    openRename(resume)
  }
  else if (command === 'delete') {
    confirmDelete(resume)
  }
}

onMounted(loadResumes)
</script>

<template>
  <div class="space-y-5">
    <section class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 class="text-2xl font-black text-slate-950">简历管理</h2>
        <p class="mt-1 text-sm text-slate-500">按岗位方向维护多版简历，投递时精准绑定对应版本。</p>
      </div>
      <button
        class="inline-flex items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-blue-600 to-sky-500 px-4 py-2.5 text-sm font-bold text-white shadow-[0_14px_28px_rgba(37,99,235,0.24)] transition hover:brightness-105"
        @click="pickFile"
      >
        <Plus class="h-4 w-4" />
        上传简历
      </button>
    </section>

    <input ref="fileInput" type="file" accept=".pdf,.doc,.docx,.txt" class="hidden" @change="onFileChange" />

    <section
      class="cursor-pointer rounded-2xl border-2 border-dashed border-blue-200 bg-blue-50/40 px-5 py-5 transition hover:border-blue-300 hover:bg-blue-50/70"
      @click="pickFile"
      @dragover.prevent
      @drop.prevent="onDrop"
    >
      <div class="flex flex-col items-center justify-between gap-3 sm:flex-row">
        <div class="flex items-center gap-3">
          <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-white text-blue-500 shadow-sm">
            <UploadCloud class="h-5 w-5" />
          </span>
          <div class="text-center sm:text-left">
            <p class="text-sm font-bold text-slate-700">拖拽简历到此处，或点击上传</p>
            <p class="mt-0.5 text-xs text-slate-400">支持 PDF / DOC / DOCX / TXT，单文件不超过 10MB</p>
          </div>
        </div>
        <span class="rounded-full bg-white px-4 py-2 text-xs font-bold text-blue-600 shadow-sm">
          选择文件
        </span>
      </div>
    </section>

    <section v-loading="loading" element-loading-text="正在加载简历…" class="min-h-32">
      <div v-if="resumes.length" class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article v-for="resume in resumes" :key="resume.id" class="soft-card flex flex-col p-5">
          <div class="flex items-start gap-3">
            <span class="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-blue-50 text-blue-600">
              <FileText class="h-5 w-5" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <h3 class="truncate text-sm font-bold text-slate-800">{{ resume.name }}</h3>
                <span v-if="resume.isDefault" class="inline-flex shrink-0 items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-600">
                  <Star class="h-3 w-3" />
                  默认
                </span>
              </div>
              <p class="mt-0.5 truncate text-xs text-slate-400">
                {{ resume.versionLabel || '未标注版本' }} · {{ formatFileSize(resume.fileSize) }}
              </p>
            </div>
          </div>

          <p class="mt-3 line-clamp-3 min-h-15 text-xs leading-5 text-slate-500">
            {{ resume.contentPreview || '未解析到文本内容，请确认文件内容可复制。' }}
          </p>

          <div class="mt-auto flex items-center justify-between gap-2 pt-4">
            <span class="text-[11px] text-slate-400">{{ formatDate(resume.createdAt) }}</span>
            <div class="flex items-center gap-1.5">
              <button
                class="rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700"
                @click="preview(resume)"
              >
                预览
              </button>
              <el-dropdown trigger="click" @command="(command) => handleCommand(command, resume)">
                <button class="inline-flex items-center gap-1 rounded-lg border border-blue-100 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-blue-200 hover:text-blue-700">
                  更多
                  <ChevronDown class="h-3.5 w-3.5" />
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="!resume.isDefault" command="default">设为默认</el-dropdown-item>
                    <el-dropdown-item command="customize">AI 定制</el-dropdown-item>
                    <el-dropdown-item command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </article>
      </div>

      <EmptyState
        v-else-if="!loading"
        :icon="FileText"
        title="还没有上传简历"
        description="上传 PDF 或 DOCX 后，系统会解析文本并用于 AI 匹配分析。"
        action-label="上传第一份简历"
        @action="pickFile"
      />
    </section>

    <!-- 上传确认 -->
    <el-dialog v-model="uploadDialog" title="上传简历" width="460px">
      <div v-if="pendingFile" class="mb-4 flex items-center gap-3 rounded-xl border border-blue-100 bg-blue-50/50 p-3">
        <FileText class="h-5 w-5 shrink-0 text-blue-500" />
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-slate-700">{{ pendingFile.name }}</p>
          <p class="text-xs text-slate-400">{{ formatFileSize(pendingFile.size) }}</p>
        </div>
      </div>

      <el-form label-position="top">
        <el-form-item label="简历名称">
          <el-input v-model="uploadForm.name" maxlength="100" placeholder="如：产品经理版-校招" />
        </el-form-item>
        <el-form-item label="版本标签">
          <el-select v-model="uploadForm.versionLabel" filterable allow-create default-first-option class="w-full">
            <el-option v-for="tag in versionTags" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-drawer v-model="previewDrawer" title="简历内容" size="560px">
      <div v-loading="previewLoading">
        <div v-if="previewData" class="space-y-3">
          <div class="rounded-xl border border-blue-100 bg-blue-50/40 p-3">
            <p class="text-sm font-bold text-slate-800">{{ previewData.name }}</p>
            <p class="mt-1 text-xs text-slate-400">
              {{ previewData.versionLabel || '未标注版本' }} · {{ formatDateTime(previewData.createdAt) }}
            </p>
          </div>
          <pre class="whitespace-pre-wrap break-words rounded-xl border border-slate-100 bg-slate-50/60 p-4 font-sans text-sm leading-6 text-slate-600">{{ previewData.contentText || '未解析到文本内容' }}</pre>
        </div>
      </div>
    </el-drawer>

    <!-- 重命名 -->
    <el-dialog v-model="renameDialog" title="编辑简历信息" width="420px">
      <el-form label-position="top">
        <el-form-item label="简历名称">
          <el-input v-model="renameForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="版本标签">
          <el-input v-model="renameForm.versionLabel" maxlength="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialog = false">取消</el-button>
        <el-button type="primary" :loading="renaming" @click="submitRename">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
