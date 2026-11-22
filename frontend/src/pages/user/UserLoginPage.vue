<template>
  <AuthShell
    eyebrow="Member Access"
    title="欢迎回来"
    description="继续你的灵感旅程。登录后即可收藏作品、整理空间，并与 SpaceMind 对话。"
    quote-first="在光影之间"
    quote-second="寻找共鸣"
  >
    <a-form
      :model="formState"
      name="login"
      autocomplete="on"
      class="auth-form"
      @finish="handleSubmit"
    >
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
        <AuthTextField
          id="login-account"
          v-model="formState.userAccount"
          label="账号"
          placeholder="输入你的账号"
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
          id="login-password"
          v-model="formState.userPassword"
          label="密码"
          type="password"
          placeholder="输入密码"
          autocomplete="current-password"
        />
      </a-form-item>

      <AuthSubmitButton :loading="submitting" label="登录" loading-label="登录中" />
    </a-form>

    <template #footer>
      <p class="auth-switch-line">
        还没有账号？
        <router-link to="/user/register" class="auth-link">创建一个</router-link>
      </p>
    </template>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { userLoginUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import router from '@/router'
import AuthShell from '@/components/auth/AuthShell.vue'
import AuthTextField from '@/components/auth/AuthTextField.vue'
import AuthSubmitButton from '@/components/auth/AuthSubmitButton.vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const loginUserStore = useLoginUserStore()
const submitting = ref(false)

const handleSubmit = async (values: any) => {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await userLoginUsingPost(values)
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchUserLogin()
      // 确保用户信息获取成功
      if (!loginUserStore.loginUser.id) {
        message.error('获取用户信息失败，请重新登录')
        return
      }
      message.success('登录成功，欢迎回来')
      // 登录成功后跳转回原页面（防止开放重定向攻击）
      const redirect = router.currentRoute.value.query.redirect as string
      if (redirect && !redirect.startsWith('http://') && !redirect.startsWith('https://')) {
        await router.push({ path: redirect, replace: true })
      } else if (loginUserStore.loginUser.userRole === 'admin') {
        await router.push({ path: '/admin/dashboard', replace: true })
      } else {
        await router.push({ path: '/', replace: true })
      }
    } else {
      message.error(res.data.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>
