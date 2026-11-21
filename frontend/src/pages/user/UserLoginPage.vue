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
        <label class="field-label" for="login-account">账号</label>
        <a-input
          id="login-account"
          v-model:value="formState.userAccount"
          placeholder="输入你的账号"
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
        <label class="field-label" for="login-password">密码</label>
        <a-input
          id="login-password"
          v-model:value="formState.userPassword"
          :type="showPassword ? 'text' : 'password'"
          placeholder="输入密码"
          size="large"
          autocomplete="current-password"
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

      <button type="submit" class="auth-submit" :disabled="submitting">
        <Loader2 v-if="submitting" :size="17" class="spin" aria-hidden="true" />
        <span>{{ submitting ? '登录中' : '登录' }}</span>
        <span v-if="!submitting" class="submit-arrow" aria-hidden="true">
          <ArrowRight :size="17" />
        </span>
      </button>
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
import { ArrowRight, Eye, EyeOff, Loader2 } from 'lucide-vue-next'
import AuthShell from '@/components/auth/AuthShell.vue'

const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const loginUserStore = useLoginUserStore()
const submitting = ref(false)
const showPassword = ref(false)

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
