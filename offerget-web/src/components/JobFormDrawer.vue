<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { createJob, updateJob } from '@/api/jobs'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  job: { type: Object, default: null },
  resumes: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const formRef = ref()
const saving = ref(false)

const form = reactive({
  company: '',
  title: '',
  city: '',
  salaryRange: '',
  jobType: '校招',
  source: '官网',
  deadline: '',
  resumeId: null,
  jdText: '',
  note: '',
})

const rules = {
  company: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  title: [{ required: true, message: '请输入岗位名称', trigger: 'blur' }],
}

const jobTypes = ['校招', '实习', '社招']
const sources = ['官网', 'BOSS', '牛客', '内推', '其他']

function reset() {
  const job = props.job
  form.company = job?.company || ''
  form.title = job?.title || ''
  form.city = job?.city || ''
  form.salaryRange = job?.salaryRange || ''
  form.jobType = job?.jobType || '校招'
  form.source = job?.source || '官网'
  form.deadline = job?.deadline || ''
  form.resumeId = job?.resumeId ?? null
  form.jdText = job?.jdText || ''
  form.note = job?.note || ''
  formRef.value?.clearValidate()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      reset()
    }
  },
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  try {
    await formRef.value?.validate()
  }
  catch {
    return
  }

  saving.value = true
  try {
    const payload = {
      company: form.company,
      title: form.title,
      city: form.city,
      salaryRange: form.salaryRange,
      jobType: form.jobType,
      source: form.source,
      deadline: form.deadline || null,
      resumeId: form.resumeId,
      jdText: form.jdText,
      note: form.note,
    }
    if (props.job) {
      await updateJob(props.job.id, payload)
      ElMessage.success('岗位已更新')
    }
    else {
      await createJob(payload)
      ElMessage.success('岗位已添加')
    }
    emit('saved')
    close()
  }
  catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="job ? '编辑岗位' : '添加岗位'"
    size="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="pb-4">
      <div class="grid gap-4 sm:grid-cols-2">
        <el-form-item label="公司名称" prop="company">
          <el-input v-model="form.company" placeholder="如：字节跳动" maxlength="100" />
        </el-form-item>
        <el-form-item label="岗位名称" prop="title">
          <el-input v-model="form.title" placeholder="如：产品经理校招生" maxlength="120" />
        </el-form-item>
        <el-form-item label="工作城市">
          <el-input v-model="form.city" placeholder="如：北京" maxlength="50" />
        </el-form-item>
        <el-form-item label="薪资范围">
          <el-input v-model="form.salaryRange" placeholder="如：20-35K" maxlength="50" />
        </el-form-item>
        <el-form-item label="岗位类型">
          <el-select v-model="form.jobType" class="w-full">
            <el-option v-for="type in jobTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </el-form-item>
        <el-form-item label="信息来源">
          <el-select v-model="form.source" class="w-full">
            <el-option v-for="source in sources" :key="source" :label="source" :value="source" />
          </el-select>
        </el-form-item>
        <el-form-item label="投递截止日期">
          <el-date-picker
            v-model="form.deadline"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择截止日期"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item label="绑定简历版本">
          <el-select v-model="form.resumeId" placeholder="选择简历版本" clearable class="w-full">
            <el-option
              v-for="resume in resumes"
              :key="resume.id"
              :label="resume.versionLabel ? `${resume.name}（${resume.versionLabel}）` : resume.name"
              :value="resume.id"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="岗位 JD">
        <el-input
          v-model="form.jdText"
          type="textarea"
          :rows="7"
          maxlength="8000"
          show-word-limit
          placeholder="粘贴岗位职责与任职要求，AI 匹配会基于 JD 提取关键词"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="如：内推码、面试安排、需要准备的材料"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="flex justify-end gap-2">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">
          {{ job ? '保存修改' : '添加岗位' }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>
