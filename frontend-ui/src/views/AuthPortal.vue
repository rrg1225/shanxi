<template>
  <div class="auth-page">
    <div class="auth-mesh-overlay" />
    <div class="auth-bg-glow auth-bg-glow-left" />
    <div class="auth-bg-glow auth-bg-glow-right" />

    <div class="auth-layout">
      <section class="auth-brand">
        <p class="auth-brand-tag">AI Learning Workspace</p>
        <h1>欢迎来到你的智能学习工作台</h1>
        <p class="auth-brand-desc">
          通过一个账号连接提示词实验、RAG 知识库与多智能体协作，让学习与创作更连贯。
        </p>

        <div class="auth-brand-stats">
          <div class="brand-stat-card">
            <strong>12+</strong>
            <span>学习模块</span>
          </div>
          <div class="brand-stat-card">
            <strong>3 大</strong>
            <span>核心场景</span>
          </div>
          <div class="brand-stat-card">
            <strong>24h</strong>
            <span>云端同步</span>
          </div>
        </div>

        <ul class="auth-feature-list">
          <li>咒语实验室：快速测试和沉淀 Prompt 模板</li>
          <li>RAG 蓝图：从文档到知识图谱的一站式链路</li>
          <li>AI 竞技场：多智能体协作与对比实验</li>
        </ul>

        <div class="auth-brand-trust">
          <span class="auth-trust-pill">企业级数据隔离</span>
          <span class="auth-trust-pill">可追踪学习闭环</span>
          <span class="auth-trust-pill">多端体验一致</span>
        </div>

      </section>

      <section class="auth-card">
        <div class="auth-card-shine" />
        <div class="auth-card-header">
          <h2>{{ isLoginMode ? '登录账号' : '注册新账号' }}</h2>
          <p>{{ isLoginMode ? '继续你的学习任务与实验进度' : '创建账号，开启你的学习工作台' }}</p>
        </div>

        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <el-form
          v-if="isLoginMode"
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="邮箱 / 手机号" prop="account">
            <el-input v-model="loginForm.account" placeholder="请输入邮箱或手机号" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="auth-action-row">
            <el-checkbox v-model="loginForm.rememberMe">7 天内免登录</el-checkbox>
            <button type="button" class="auth-link auth-link--btn" @click="openForgotPasswordDialog">
              忘记密码？
            </button>
          </div>

          <el-button type="primary" class="auth-submit" :loading="submitting" @click="handleLogin">
            登录并进入工作台
          </el-button>
        </el-form>

        <el-form
          v-else
          ref="registerFormRef"
          :model="registerForm"
          :rules="registerRules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="registerForm.nickname" placeholder="例如：学习探索者" />
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input v-model="registerForm.email" placeholder="请输入常用邮箱" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="registerForm.password"
              type="password"
              show-password
              placeholder="至少 6 位，建议字母+数字"
            />
          </el-form-item>

          <div class="auth-password-strength">
            <span class="strength-label">密码强度</span>
            <div class="strength-track">
              <div class="strength-fill" :style="{ width: `${passwordStrength.score}%` }" :class="passwordStrength.className" />
            </div>
            <span class="strength-value">{{ passwordStrength.label }}</span>
          </div>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="registerForm.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入密码"
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <el-form-item prop="agreed">
            <el-checkbox v-model="registerForm.agreed">
              我已阅读并同意服务协议与隐私政策
            </el-checkbox>
          </el-form-item>

          <el-button type="primary" class="auth-submit" :loading="submitting" @click="handleRegister">
            注册并开始使用
          </el-button>
        </el-form>

        <p class="auth-switch-tip">
          {{ isLoginMode ? '还没有账号？' : '已有账号？' }}
          <span class="auth-link" @click="toggleMode">
            {{ isLoginMode ? '去注册' : '去登录' }}
          </span>
        </p>

        <p class="auth-footnote">登录即代表你同意平台服务条款与数据安全协议。</p>
      </section>
    </div>

    <el-dialog
      v-model="forgotPwdVisible"
      title="找回密码"
      width="420px"
      align-center
      destroy-on-close
      class="auth-forgot-dialog"
      @closed="resetForgotPasswordForm"
    >
      <p class="auth-forgot-tip">
        请输入注册时使用的邮箱或手机号，我们将发送重置链接（当前为演示环境，仅模拟发送）。
      </p>
      <el-form
        ref="forgotPwdFormRef"
        :model="forgotPwdForm"
        :rules="forgotPwdRules"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="邮箱 / 手机号" prop="account">
          <el-input v-model="forgotPwdForm.account" placeholder="与登录账号一致" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="forgotPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="forgotPwdSubmitting" @click="handleForgotPasswordSubmit">
          发送重置链接
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

type AuthTab = 'login' | 'register'

const router = useRouter()
const activeTab = ref<AuthTab>('login')
const submitting = ref(false)

const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()
const forgotPwdFormRef = ref<FormInstance>()

const loginForm = reactive({
  account: '',
  password: '',
  rememberMe: true,
})

const registerForm = reactive({
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
  agreed: false,
})

const forgotPwdVisible = ref(false)
const forgotPwdSubmitting = ref(false)
const forgotPwdForm = reactive({
  account: '',
})

const forgotPwdRules: FormRules = {
  account: [{ required: true, message: '请输入邮箱或手机号', trigger: 'blur' }],
}

const isLoginMode = computed(() => activeTab.value === 'login')
const passwordStrength = computed(() => {
  const pwd = registerForm.password
  if (!pwd) return { score: 10, label: '未设置', className: 'is-weak' }

  let points = 0
  if (pwd.length >= 6) points += 25
  if (pwd.length >= 10) points += 25
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) points += 25
  if (/\d/.test(pwd) && /[^A-Za-z0-9]/.test(pwd)) points += 25

  if (points <= 25) return { score: 30, label: '较弱', className: 'is-weak' }
  if (points <= 50) return { score: 55, label: '一般', className: 'is-medium' }
  if (points <= 75) return { score: 78, label: '良好', className: 'is-good' }
  return { score: 100, label: '很强', className: 'is-strong' }
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请再次输入密码'))
    return
  }
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const validateAgreement = (_rule: unknown, value: boolean, callback: (error?: Error) => void) => {
  if (!value) {
    callback(new Error('请先同意协议'))
    return
  }
  callback()
}

const loginRules: FormRules = {
  account: [{ required: true, message: '请输入邮箱或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const registerRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度需在 2-20 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: ['blur', 'change'] }],
  agreed: [{ validator: validateAgreement, trigger: 'change' }],
}

const mockSubmit = async () => {
  submitting.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 600))
  submitting.value = false
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  await mockSubmit()
  localStorage.setItem('learning_workspace_auth', JSON.stringify({ account: loginForm.account, at: Date.now() }))
  ElMessage.success('登录成功，正在进入工作台')
  router.push('/workbench/index')
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  await mockSubmit()
  localStorage.setItem(
    'learning_workspace_auth',
    JSON.stringify({ account: registerForm.email, nickname: registerForm.nickname, at: Date.now() }),
  )
  ElMessage.success('注册成功，欢迎加入')
  router.push('/workbench/index')
}

const toggleMode = () => {
  activeTab.value = isLoginMode.value ? 'register' : 'login'
}

function openForgotPasswordDialog() {
  forgotPwdForm.account = loginForm.account.trim()
  forgotPwdVisible.value = true
}

function resetForgotPasswordForm() {
  forgotPwdForm.account = ''
  forgotPwdFormRef.value?.resetFields()
}

async function handleForgotPasswordSubmit() {
  if (!forgotPwdFormRef.value) return
  const valid = await forgotPwdFormRef.value.validate().catch(() => false)
  if (!valid) return

  forgotPwdSubmitting.value = true
  await new Promise((resolve) => window.setTimeout(resolve, 800))
  forgotPwdSubmitting.value = false

  ElMessage.success(`已向「${forgotPwdForm.account}」发送重置指引（演示模式，无真实邮件）`)
  forgotPwdVisible.value = false
}
</script>

<style scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  padding: 40px 28px;
  background: var(--wb-shell-bg);
  overflow: hidden;
}

.auth-mesh-overlay {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(circle at center, black 28%, transparent 88%);
  pointer-events: none;
}

.auth-bg-glow {
  position: absolute;
  z-index: 0;
  border-radius: 999px;
  filter: blur(26px);
  pointer-events: none;
}

.auth-bg-glow-left {
  width: 420px;
  height: 420px;
  left: -120px;
  top: -80px;
  background: rgba(99, 102, 241, 0.26);
  animation: floatGlow 10s ease-in-out infinite;
}

.auth-bg-glow-right {
  width: 380px;
  height: 380px;
  right: -120px;
  bottom: -100px;
  background: rgba(16, 185, 129, 0.2);
  animation: floatGlow 12s ease-in-out infinite reverse;
}

.auth-layout {
  position: relative;
  z-index: 1;
  max-width: 1120px;
  margin: 0 auto;
  min-height: calc(100vh - 80px);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 24px;
  align-items: center;
}

.auth-brand {
  padding-right: 32px;
}

.auth-brand-tag {
  display: inline-flex;
  margin: 0 0 10px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--wb-nav-active-text);
  background: var(--accent-primary-soft);
  backdrop-filter: blur(8px);
  border: 1px solid color-mix(in srgb, var(--accent-primary) 35%, transparent);
}

.auth-brand h1 {
  margin: 0 0 14px;
  font-size: 38px;
  line-height: 1.2;
  color: transparent;
  background: linear-gradient(120deg, var(--text-primary), var(--wb-nav-active-text) 45%, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
}

.auth-brand-desc {
  margin: 0;
  max-width: 560px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.auth-brand-stats {
  margin-top: 22px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  max-width: 560px;
}

.brand-stat-card {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--bg-surface) 72%, transparent);
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.brand-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: color-mix(in srgb, var(--accent-primary) 42%, var(--border-subtle));
}

.brand-stat-card strong {
  display: block;
  font-size: 20px;
  line-height: 1.2;
}

.brand-stat-card span {
  font-size: 12px;
  color: var(--text-secondary);
}

.auth-feature-list {
  margin: 26px 0 0;
  padding-left: 18px;
  color: var(--text-secondary);
  line-height: 1.9;
}

.auth-brand-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.auth-trust-pill {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--accent-primary-soft) 32%, transparent);
}


.auth-card {
  position: relative;
  padding: 26px;
  border-radius: 20px;
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--bg-overlay) 92%, rgba(255, 255, 255, 0.12)),
    color-mix(in srgb, var(--bg-overlay) 84%, rgba(99, 102, 241, 0.1))
  );
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(14px);
  overflow: hidden;
}

.auth-card-shine {
  position: absolute;
  width: 240px;
  height: 240px;
  top: -160px;
  right: -80px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.42) 0%, transparent 72%);
  pointer-events: none;
}

.auth-card-header h2 {
  margin: 0 0 6px;
  font-size: 26px;
}

.auth-card-header p {
  margin: 0 0 18px;
  color: var(--text-secondary);
  font-size: 14px;
}

.auth-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 13px;
}

.auth-submit {
  width: 100%;
  height: 44px;
  font-weight: 600;
  letter-spacing: 0.2px;
  border: none;
  background-image: linear-gradient(120deg, #4f46e5, #6366f1 42%, #0ea5e9);
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.28);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.auth-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 28px rgba(79, 70, 229, 0.32);
  filter: brightness(1.03);
}

.auth-password-strength {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 10px;
  margin: -2px 0 14px;
}

.strength-label,
.strength-value {
  font-size: 12px;
  color: var(--text-secondary);
}

.strength-track {
  height: 8px;
  border-radius: 99px;
  background: var(--bg-muted);
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.2s ease;
}

.strength-fill.is-weak {
  background: #ef4444;
}

.strength-fill.is-medium {
  background: #f59e0b;
}

.strength-fill.is-good {
  background: #22c55e;
}

.strength-fill.is-strong {
  background: #10b981;
}

.auth-switch-tip {
  margin: 18px 0 0;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.auth-link {
  color: var(--accent-link);
  cursor: pointer;
  transition: color 0.2s ease;
}

.auth-link:hover {
  text-decoration: underline;
  color: var(--accent-primary);
}

.auth-link--btn {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
}

.auth-forgot-tip {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-secondary);
}

.auth-footnote {
  margin: 10px 0 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent-primary) 80%, transparent), 0 0 0 4px rgba(99, 102, 241, 0.16) !important;
  transform: translateY(-1px);
}

:deep(.el-tabs__item.is-active) {
  font-weight: 700;
}

@keyframes floatGlow {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(0, -12px, 0) scale(1.04);
  }
}


@media (max-width: 980px) {
  .auth-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .auth-brand {
    padding-right: 0;
  }

  .auth-brand h1 {
    font-size: 30px;
  }

  .auth-brand-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .auth-page {
    padding: 24px 14px;
  }

  .auth-card {
    padding: 20px;
  }
}
</style>
