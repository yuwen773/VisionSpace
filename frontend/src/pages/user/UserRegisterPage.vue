<template>
  <div id="userRegisterPage">
    <!-- 装饰性形状背景 -->
    <div class="decoration-bg">
      <div class="shape shape-1 animate-float"></div>
      <div class="shape shape-2 animate-float" style="animation-delay: 0.5s"></div>
      <div class="shape shape-3 animate-float" style="animation-delay: 1s"></div>
      <div class="shape shape-4 animate-float" style="animation-delay: 1.5s"></div>
      <div class="shape shape-5 animate-float" style="animation-delay: 2s"></div>
      <div class="shape shape-6 animate-spin" style="animation-duration: 30s"></div>
      <div class="shape shape-7 animate-spin" style="animation-duration: 40s"></div>
    </div>

    <!-- 注册卡片 -->
    <div class="register-container animate-bounce">
      <div class="register-card pop-card">
        <!-- Logo 区域 -->
        <div class="logo-section">
          <div class="logo-wrapper">
            <img src="@/assets/logo.svg" alt="Logo" class="logo-image" />
            <div class="logo-badge bg-sunshine">NEW</div>
          </div>
          <h1 class="logo-title">创建账号</h1>
          <p class="logo-subtitle">✨ 加入 VisionSpace，开启创意之旅 ✨</p>
        </div>

        <!-- 注册表单 -->
        <a-form
          :model="formState"
          name="basic"
          autocomplete="off"
          @finish="handleSubmit"
          class="register-form"
        >
          <a-form-item
            name="userAccount"
            :rules="[{ required: true, message: '请输入账号' }]"
          >
            <a-input
              v-model:value="formState.userAccount"
              placeholder="🎯 用户账号"
              size="large"
              class="pop-input"
            >
              <template #prefix>
                <span class="input-icon">👤</span>
              </template>
            </a-input>
          </a-form-item>

          <a-form-item
            name="userPassword"
            :rules="[
              { required: true, message: '请输入密码' },
              { min: 8, message: '密码不能小于8位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.userPassword"
              placeholder="🔒 密码"
              size="large"
              class="pop-input"
            >
              <template #prefix>
                <span class="input-icon">🔑</span>
              </template>
            </a-input-password>
          </a-form-item>

          <a-form-item
            name="checkPassword"
            :rules="[
              { required: true, message: '请输入确认密码' },
              { min: 8, message: '确认密码不能小于8位' },
            ]"
          >
            <a-input-password
              v-model:value="formState.checkPassword"
              placeholder="🔐 确认密码"
              size="large"
              class="pop-input"
            >
              <template #prefix>
                <span class="input-icon">🛡️</span>
              </template>
            </a-input-password>
          </a-form-item>

          <div class="form-tips">
            <span>已有账号？</span>
            <router-link to="/user/login" class="tips-link">
              🚀 立即登录
            </router-link>
          </div>

          <a-form-item>
            <button html-type="submit" class="pop-btn primary">
              <span class="btn-icon">🎉</span>
              <span>创建账号</span>
            </button>
          </a-form-item>
        </a-form>

        <!-- 特性展示 -->
        <div class="features">
          <div class="feature-item">
            <span class="feature-icon">☁️</span>
            <span class="feature-text">无限空间</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">📸</span>
            <span class="feature-text">智能分类</span>
          </div>
          <div class="feature-item">
            <span class="feature-icon">👥</span>
            <span class="feature-text">团队协作</span>
          </div>
        </div>

        <!-- 底部装饰 -->
        <div class="card-decoration">
          <div class="deco-dot deco-dot-1"></div>
          <div class="deco-dot deco-dot-2"></div>
          <div class="deco-dot deco-dot-3"></div>
        </div>
      </div>
    </div>

    <!-- 波浪装饰 -->
    <div class="wave-bottom wave-decoration">
      <svg viewBox="0 0 1440 320" preserveAspectRatio="none" style="width: 100%; height: 80px;">
        <path d="M0,192L48,160L96,192L144,160L192,192L240,160L288,192L336,160L384,192L432,160L480,192L528,160L576,192L624,160L672,192L720,160L768,192L816,160L864,192L912,160L960,192L1008,160L1056,192L1104,160L1152,192L1200,160L1248,192L1296,160L1344,192L1392,160L1440,192V320H0V192Z" fill="var(--color-mint)"/>
        <path d="M0,224L48,192L96,224L144,192L192,224L240,192L288,224L336,192L384,224L432,192L480,224L528,192L576,224L624,192L672,224L720,192L768,224L816,192L864,224L912,192L960,224L1008,192L1056,224L1104,192L1152,224L1200,192L1248,224L1296,192L1344,224L1392,192L1440,224V320H0V224Z" fill="var(--color-sky)" opacity="0.6"/>
      </svg>
    </div>

    <!-- 底部版权 -->
    <div class="footer-text">
      <p>🎨 注册即表示您同意我们的服务条款和隐私政策</p>
      <p class="copyright">&copy; 2024 VisionSpace. All rights reserved.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const handleSubmit = async (values: any) => {
  if (values.userPassword !== values.checkPassword) {
    message.error('🔐 两次输入密码不一致')
    return
  }
  const res = await userRegisterUsingPost(values)
  if (res.data.code === 0 && res.data.data) {
    message.success('🎉 注册成功！请登录')
    await router.push({
      path: '/user/login',
      replace: true,
    })
  } else {
    message.error('😅 注册失败：' + res.data.message)
  }
}
</script>

<style scoped lang="less">
#userRegisterPage {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
  background: var(--bg-primary);
  padding: var(--space-6) var(--space-6) var(--space-16);
}

/* ========== 装饰背景形状 ========== */
.decoration-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.shape {
  position: absolute;
  opacity: 0.15;
}

.shape-1 {
  width: 120px;
  height: 120px;
  background: var(--color-coral);
  border-radius: 50%;
  top: 10%;
  left: 10%;
}

.shape-2 {
  width: 80px;
  height: 80px;
  background: var(--color-sunshine);
  border-radius: var(--radius-xl);
  top: 60%;
  left: 5%;
}

.shape-3 {
  width: 60px;
  height: 60px;
  background: var(--color-mint);
  border-radius: 50%;
  top: 30%;
  right: 15%;
}

.shape-4 {
  width: 100px;
  height: 100px;
  background: var(--color-sky);
  border-radius: var(--radius-2xl);
  bottom: 20%;
  right: 10%;
}

.shape-5 {
  width: 0;
  height: 0;
  border-left: 50px solid transparent;
  border-right: 50px solid transparent;
  border-bottom: 80px solid var(--color-grape);
  top: 15%;
  right: 30%;
}

.shape-6 {
  width: 40px;
  height: 40px;
  background: var(--color-pink);
  border-radius: 50%;
  top: 40%;
  left: 60%;
}

.shape-7 {
  width: 70px;
  height: 70px;
  background: var(--color-sunshine);
  border-radius: var(--radius-full);
  bottom: 30%;
  left: 20%;
}

/* ========== 注册容器 ========== */
.register-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 500px;
}

/* ========== 注册卡片 ========== */
.register-card {
  background: var(--bg-card);
  padding: var(--space-10);
  position: relative;
}

/* ========== Logo 区域 ========== */
.logo-section {
  text-align: center;
  margin-bottom: var(--space-8);
}

.logo-wrapper {
  position: relative;
  display: inline-block;
  margin-bottom: var(--space-6);
}

.logo-image {
  width: 80px;
  height: 80px;
  border: 4px solid var(--border-bold);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-pop);
  transition: all var(--transition-bounce);

  &:hover {
    transform: rotate(-5deg) scale(1.05);
  }
}

.logo-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  padding: 6px 12px;
  border: 2px solid var(--border-bold);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 700;
  color: white;
  animation: pulse-pop 2s infinite;
}

.logo-title {
  font-family: var(--font-brand);
  font-size: var(--text-4xl);
  font-weight: 800;
  margin: 0 0 var(--space-3) 0;
  background: var(--gradient-pop);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.logo-subtitle {
  font-size: var(--text-lg);
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
}

/* ========== 表单 ========== */
.register-form {
  :deep(.ant-form-item) {
    margin-bottom: var(--space-5);
  }
}

.pop-input {
  .input-icon {
    font-size: var(--text-xl);
  }
}

/* ========== 提示 ========== */
.form-tips {
  text-align: center;
  margin-bottom: var(--space-6);
  font-weight: 600;
  color: var(--text-secondary);

  .tips-link {
    color: var(--color-grape);
    font-weight: 700;
    margin-left: var(--space-2);
    transition: all var(--transition-bounce);

    &:hover {
      color: var(--color-coral);
      transform: scale(1.1);
    }
  }
}

/* ========== 按钮 ========== */
.pop-btn {
  width: 100%;
  height: 52px;
  font-size: var(--text-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);

  .btn-icon {
    font-size: var(--text-xl);
  }
}

/* ========== 特性展示 ========== */
.features {
  display: flex;
  justify-content: space-around;
  margin-top: var(--space-8);
  padding-top: var(--space-6);
  border-top: 3px dashed var(--border-light);
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  animation: featureFadeIn 0.6s ease-out backwards;
}

.feature-item:nth-child(1) {
  animation-delay: 0.1s;
}

.feature-item:nth-child(2) {
  animation-delay: 0.2s;
}

.feature-item:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes featureFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.feature-icon {
  font-size: var(--text-2xl);
  padding: var(--space-3);
  background: var(--gradient-fresh);
  border-radius: var(--radius-xl);
  transition: all var(--transition-bounce);

  .feature-item:hover & {
    transform: scale(1.1) rotate(5deg);
  }
}

.feature-text {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-weight: 600;
}

/* ========== 卡片装饰 ========== */
.card-decoration {
  position: absolute;
  bottom: var(--space-6);
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  gap: var(--space-4);
}

.deco-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  animation: pulse-pop 2s infinite;

  &.deco-dot-1 {
    background: var(--color-coral);
    animation-delay: 0s;
  }

  &.deco-dot-2 {
    background: var(--color-sunshine);
    animation-delay: 0.5s;
  }

  &.deco-dot-3 {
    background: var(--color-mint);
    animation-delay: 1s;
  }
}

/* ========== 波浪装饰 ========== */
.wave-bottom {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1;
}

/* ========== 底部版权 ========== */
.footer-text {
  position: relative;
  z-index: 1;
  margin-top: var(--space-8);
  text-align: center;

  p {
    color: var(--text-secondary);
    font-size: var(--text-xs);
    margin-bottom: var(--space-1);
    font-weight: 600;
  }

  .copyright {
    opacity: 0.7;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  #userRegisterPage {
    padding: var(--space-4);
  }

  .register-card {
    padding: var(--space-8) var(--space-6);
  }

  .logo-title {
    font-size: var(--text-3xl);
  }

  .logo-subtitle {
    font-size: var(--text-base);
  }

  .features {
    flex-direction: column;
    gap: var(--space-4);
  }

  .feature-item {
    flex-direction: row;
    justify-content: center;
  }
}

/* ========== 动画 ========== */
@keyframes bounce-in {
  0% {
    opacity: 0;
    transform: scale(0.3) rotate(-10deg);
  }
  50% {
    transform: scale(1.05) rotate(5deg);
  }
  100% {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(5deg);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
