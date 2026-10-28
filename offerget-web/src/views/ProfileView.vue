<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  BriefcaseBusiness,
  CalendarDays,
  FileText,
  KeyRound,
  Lock,
  Mail,
  PenLine,
  ShieldCheck,
  Sparkles,
  UserRound,
} from 'lucide-vue-next'

import { changePassword, fetchProfile, updateProfile } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import { formatDateFull } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const saving = ref(false)
const changing = ref(false)
const editing = ref(false)
const editingPassword = ref(false)
const profile = ref(null)
const profileFormRef = ref()
const passwordFormRef = ref()

const form = reactive({
  nickname: '',
  email: '',
  graduationYear: 2027,
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        }
        else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

const yearOptions = Array.from({ length: 8 }, (_, index) => 2025 + index)

async function loadProfile() {
  loading.value = true
  try {
    const data = await fetchProfile()
    profile.value = data
    form.nickname = data.nickname
    form.email = data.email || ''
    form.graduationYear = data.graduationYear || 2027
  }
  catch (error) {
    ElMessage.error(error.message || '加载个人资料失败')
  }
  finally {
    loading.value = false
  }
}

async function saveProfile() {
  try {
    await profileFormRef.value?.validate()
  }
  catch {
    return
  }
  saving.value = true
  try {
    const data = await updateProfile({
      nickname: form.nickname,
      email: form.email || null,
      graduationYear: form.graduationYear,
    })
    profile.value = data
    userStore.updateUser({
      id: data.id,
      username: data.username,
      nickname: data.nickname,
      email: data.email,
      avatar: data.avatar,
      graduationYear: data.graduationYear,
    })
    editing.value = false
    ElMessage.success('资料已更新')
  }
  catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
  finally {
    saving.value = false
  }
}

function startEdit() {
  form.nickname = profile.value.nickname
  form.email = profile.value.email || ''
  form.graduationYear = profile.value.graduationYear || 2027
  editing.value = true
  profileFormRef.value?.clearValidate()
}

function cancelEdit() {
  editing.value = false
  profileFormRef.value?.clearValidate()
}

function startPasswordEdit() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  editingPassword.value = true
  passwordFormRef.value?.clearValidate()
}

function cancelPasswordEdit() {
  editingPassword.value = false
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

async function submitPassword() {
  try {
    await passwordFormRef.value?.validate()
  }
  catch {
    return
  }
  changing.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改，请使用新密码重新登录')
    userStore.logout()
    router.replace({ name: 'login' })
  }
  catch (error) {
    ElMessage.error(error.message || '修改密码失败')
  }
  finally {
    changing.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div v-loading="loading" element-loading-text="正在加载个人资料…" class="space-y-5">
    <section>
      <h2 class="text-2xl font-black text-slate-950">个人中心</h2>
      <p class="mt-1 text-sm text-slate-500">管理账号资料、登录密码，查看你的使用数据。</p>
    </section>

    <div v-if="profile" class="grid gap-5 xl:grid-cols-[minmax(0,340px)_1fr]">
      <!-- 资料概览 -->
      <div class="space-y-5">
        <div class="soft-card p-6">
          <div class="flex flex-col items-center text-center">
            <span class="grid h-20 w-20 place-items-center rounded-full bg-gradient-to-br from-blue-600 to-sky-400 text-3xl font-black text-white shadow-[0_16px_32px_rgba(37,99,235,0.28)]">
              {{ profile.nickname.slice(0, 1) }}
            </span>
            <p class="mt-4 text-lg font-black text-slate-900">{{ profile.nickname }}</p>
            <p class="mt-1 text-xs text-slate-400">@{{ profile.username }}</p>
            <div class="mt-3 flex flex-wrap items-center justify-center gap-2 text-[11px]">
              <span class="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 font-semibold text-blue-600">
                {{ profile.graduationYear }} 届校招
              </span>
              <span class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2.5 py-1 font-semibold text-emerald-600">
                <ShieldCheck class="h-3 w-3" />
                已登录
              </span>
            </div>
            <p class="mt-4 inline-flex items-center gap-1.5 text-xs text-slate-400">
              <CalendarDays class="h-3.5 w-3.5" />
              {{ formatDateFull(profile.createdAt) }} 加入 · 已使用 {{ profile.stats.daysSinceRegister }} 天
            </p>
          </div>

          <div class="mt-5 grid grid-cols-2 gap-3 border-t border-blue-100/70 pt-5">
            <div class="rounded-xl bg-blue-50/60 p-3 text-center">
              <p class="text-xl font-black text-slate-900">{{ profile.stats.jobCount }}</p>
              <p class="mt-0.5 text-[11px] text-slate-500">管理岗位</p>
            </div>
            <div class="rounded-xl bg-violet-50/60 p-3 text-center">
              <p class="text-xl font-black text-slate-900">{{ profile.stats.resumeCount }}</p>
              <p class="mt-0.5 text-[11px] text-slate-500">简历版本</p>
            </div>
            <div class="rounded-xl bg-amber-50/60 p-3 text-center">
              <p class="text-xl font-black text-slate-900">{{ profile.stats.matchCount }}</p>
              <p class="mt-0.5 text-[11px] text-slate-500">AI 匹配</p>
            </div>
            <div class="rounded-xl bg-emerald-50/60 p-3 text-center">
              <p class="text-xl font-black text-slate-900">{{ profile.stats.optimizationCount }}</p>
              <p class="mt-0.5 text-[11px] text-slate-500">简历优化</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 表单区 -->
      <div class="space-y-5">
        <div class="soft-card p-5 sm:p-6">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-blue-50 text-blue-600">
                <UserRound class="h-4.5 w-4.5" />
              </span>
              <div>
                <p class="text-sm font-bold text-slate-800">基本资料</p>
                <p class="text-xs text-slate-400">昵称会显示在侧栏与工作台欢迎语中</p>
              </div>
            </div>
            <button
              v-if="!editing"
              class="inline-flex shrink-0 items-center gap-1 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-bold text-blue-600 transition hover:border-blue-200 hover:bg-blue-50"
              @click="startEdit"
            >
              <PenLine class="h-3.5 w-3.5" />
              修改资料
            </button>
          </div>

          <!-- 只读展示 -->
          <div v-if="!editing" class="mt-4 grid gap-3 sm:grid-cols-2">
            <div class="rounded-xl border border-blue-50 bg-white/70 px-3.5 py-3">
              <p class="text-[11px] text-slate-400">昵称</p>
              <p class="mt-0.5 truncate text-sm font-semibold text-slate-800">{{ profile.nickname }}</p>
            </div>
            <div class="rounded-xl border border-blue-50 bg-white/70 px-3.5 py-3">
              <p class="text-[11px] text-slate-400">用户名</p>
              <p class="mt-0.5 truncate text-sm font-semibold text-slate-800">{{ profile.username }}</p>
            </div>
            <div class="rounded-xl border border-blue-50 bg-white/70 px-3.5 py-3">
              <p class="text-[11px] text-slate-400">邮箱</p>
              <p class="mt-0.5 truncate text-sm font-semibold" :class="profile.email ? 'text-slate-800' : 'text-slate-300'">
                {{ profile.email || '未设置' }}
              </p>
            </div>
            <div class="rounded-xl border border-blue-50 bg-white/70 px-3.5 py-3">
              <p class="text-[11px] text-slate-400">毕业年份</p>
              <p class="mt-0.5 truncate text-sm font-semibold text-slate-800">{{ profile.graduationYear }} 届</p>
            </div>
          </div>

          <!-- 编辑表单 -->
          <el-form v-else ref="profileFormRef" :model="form" :rules="rules" label-position="top" class="mt-4">
            <div class="grid gap-4 sm:grid-cols-2">
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="form.nickname" maxlength="20" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input :model-value="profile.username" disabled />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="form.email" placeholder="用于接收通知（可选）">
                  <template #prefix>
                    <Mail class="h-4 w-4 text-slate-400" />
                  </template>
                </el-input>
              </el-form-item>
              <el-form-item label="毕业年份">
                <el-select v-model="form.graduationYear" class="w-full">
                  <el-option v-for="year in yearOptions" :key="year" :label="`${year} 届`" :value="year" />
                </el-select>
              </el-form-item>
            </div>
            <div class="flex justify-end gap-2">
              <el-button @click="cancelEdit">取消</el-button>
              <el-button type="primary" :loading="saving" @click="saveProfile">保存资料</el-button>
            </div>
          </el-form>
        </div>

        <div class="soft-card p-5 sm:p-6">
          <div class="flex items-center justify-between gap-3">
            <div class="flex items-center gap-2">
              <span class="grid h-9 w-9 place-items-center rounded-xl bg-amber-50 text-amber-600">
                <KeyRound class="h-4.5 w-4.5" />
              </span>
              <div>
                <p class="text-sm font-bold text-slate-800">登录密码</p>
                <p class="text-xs text-slate-400">修改成功后需要使用新密码重新登录</p>
              </div>
            </div>
            <button
              v-if="!editingPassword"
              class="inline-flex shrink-0 items-center gap-1 rounded-lg border border-blue-100 bg-white px-3 py-1.5 text-xs font-bold text-blue-600 transition hover:border-blue-200 hover:bg-blue-50"
              @click="startPasswordEdit"
            >
              <KeyRound class="h-3.5 w-3.5" />
              修改密码
            </button>
          </div>

          <!-- 只读展示 -->
          <div v-if="!editingPassword" class="mt-4 flex items-start gap-3 rounded-xl border border-blue-50 bg-white/70 px-3.5 py-3">
            <Lock class="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
            <div>
              <p class="text-sm font-semibold text-slate-700">密码已设置</p>
              <p class="mt-0.5 text-xs leading-5 text-slate-400">建议定期更换密码，修改成功后当前登录会自动退出。</p>
            </div>
          </div>

          <!-- 编辑表单 -->
          <el-form v-else ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="mt-4">
            <div class="grid gap-4 sm:grid-cols-3">
              <el-form-item label="原密码" prop="oldPassword">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码" prop="newPassword">
                <el-input v-model="passwordForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认新密码" prop="confirmPassword">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
              </el-form-item>
            </div>
            <div class="flex justify-end gap-2">
              <el-button @click="cancelPasswordEdit">取消</el-button>
              <el-button type="primary" :loading="changing" @click="submitPassword">确认修改</el-button>
            </div>
          </el-form>
        </div>

        <div class="grid gap-4 sm:grid-cols-3">
          <div class="rounded-2xl border border-blue-100 bg-white/70 p-4">
            <BriefcaseBusiness class="h-5 w-5 text-blue-500" />
            <p class="mt-2 text-xs leading-5 text-slate-500">岗位数据按账号隔离，其他用户无法访问你的投递记录。</p>
          </div>
          <div class="rounded-2xl border border-blue-100 bg-white/70 p-4">
            <FileText class="h-5 w-5 text-violet-500" />
            <p class="mt-2 text-xs leading-5 text-slate-500">简历文件存储在自己的部署环境，不会上传到第三方服务。</p>
          </div>
          <div class="rounded-2xl border border-blue-100 bg-white/70 p-4">
            <Sparkles class="h-5 w-5 text-amber-500" />
            <p class="mt-2 text-xs leading-5 text-slate-500">AI 匹配与优化均为本地规则引擎，结果仅用于辅助判断。</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
