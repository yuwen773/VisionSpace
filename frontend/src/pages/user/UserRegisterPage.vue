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
        <AuthTextField
          id="register-account"
          v-model="formState.userAccount"
          label="账号"
          placeholder="设置你的账号"
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
        <AuthTextField
          id="register-password"
          v-model="formState.userPassword"
          label="密码"
          type="password"
          placeholder="至少 8 位"
          autocomplete="new-password"
        />
      </a-form-item>

      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请输入确认密码' },
          { min: 8, message: '确认密码不能小于8位' },
          { validator: validateConfirmPassword, trigger: 'change' },
        ]"
      >
        <AuthTextField
          id="register-confirm"
          v-model="formState.checkPassword"
          label="确认密码"
          type="password"
          placeholder="再输入一次"
          autocomplete="new-password"
        />
      </a-form-item>

      <AuthSubmitButton :loading="submitting" label="创建账号" loading-label="创建中" />
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
import AuthShell from '@/components/auth/AuthShell.vue'
import AuthTextField from '@/components/auth/AuthTextField.vue'
import AuthSubmitButton from '@/components/auth/AuthSubmitButton.vue'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const submitting = ref(false)

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
