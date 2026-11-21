<template>
  <AuthShell
    eyebrow="Create Account"
    title="创建你的空间"
    description="一个账号，收藏、整理，并与视觉灵感对话。"
    quote-first="让每一件作品"
    quote-second="都有归处"
  >
    <a-form
      :model="formState"
      name="register"
      autocomplete="on"
      class="auth-form"
      @finish="handleSubmit"
    >
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <label class="field-label" for="register-account">账号</label>
        <a-input
          id="register-account"
          v-model:value="formState.userAccount"
          placeholder="设置你的账号"
          size="large"
          autocomplete="username"
        />
      </a-form-item>

      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码' },
          { min: 8, message: '密码不能小于8位' },
        ]"
      >
        <label class="field-label" for="register-password">密码</label>
        <a-input
          id="register-password"
          v-model:value="formState.userPassword"
          :type="showPassword ? 'text' : 'password'"
          placeholder="至少 8 位"
          size="large"
          autocomplete="new-password"
        >
          <template #suffix>
            <button
              type="button"
              class="field-toggle"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            >
              <EyeOff v-if="showPassword" :size="16" aria-hidden="true" />
              <Eye v-else :size="16" aria-hidden="true" />
            </button>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请输入确认密码' },
          { min: 8, message: '确认密码不能小于8位' },
          { validator: validateConfirmPassword, trigger: 'change' },
        ]"
      >
        <label class="field-label" for="register-confirm">确认密码</label>
        <a-input
          id="register-confirm"
          v-model:value="formState.checkPassword"
          :type="showConfirm ? 'text' : 'password'"
          placeholder="再输入一次"
          size="large"
          autocomplete="new-password"
        >
          <template #suffix>
            <button
              type="button"
              class="field-toggle"
              :aria-label="showConfirm ? '隐藏确认密码' : '显示确认密码'"
              :aria-pressed="showConfirm"
              @click="showConfirm = !showConfirm"
            >
              <EyeOff v-if="showConfirm" :size="16" aria-hidden="true" />
              <Eye v-else :size="16" aria-hidden="true" />
            </button>
          </template>
        </a-input>
      </a-form-item>

      <button type="submit" class="auth-submit" :disabled="submitting">
        <Loader2 v-if="submitting" :size="17" class="spin" aria-hidden="true" />
        <span>{{ submitting ? '创建中' : '创建账号' }}</span>
        <span v-if="!submitting" class="submit-arrow" aria-hidden="true">
          <ArrowRight :size="17" />
        </span>
      </button>
    </a-form>

    <template #footer>
      <p class="auth-legal">注册即表示你同意我们的服务条款和隐私政策。</p>
      <p class="auth-switch-line">
        已有账号？
        <router-link to="/user/login" class="auth-link">立即登录</router-link>
      </p>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import router from '@/router'
import { ArrowRight, Eye, EyeOff, Loader2 } from 'lucide-vue-next'
import AuthShell from '@/components/auth/AuthShell.vue'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const submitting = ref(false)
const showPassword = ref(false)
const showConfirm = ref(false)

/** 确认密码随输入实时校验，不必等到提交才提示 */
const validateConfirmPassword = async (_rule: unknown, value: string) => {
  if (value && value !== formState.userPassword) {
    throw new Error('两次输入的密码不一致')
  }
}

const handleSubmit = async (values: any) => {
  if (submitting.value) return
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    const res = await userRegisterUsingPost(values)
    if (res.data.code === 0 && res.data.data) {
      message.success('注册成功，请登录')
      await router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      message.error('注册失败：' + res.data.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>
