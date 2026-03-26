<template>
  <div id="mySpacePage">
    <!-- 装饰性形状背景 -->
    <div class="decoration-bg">
      <div class="shape shape-1 animate-float"></div>
      <div class="shape shape-2 animate-float" style="animation-delay: 0.5s"></div>
      <div class="shape shape-3 animate-float" style="animation-delay: 1s"></div>
    </div>

    <!-- 加载卡片 -->
    <div class="loading-container animate-bounce">
      <div class="loading-card pop-card">
        <!-- 加载动画 -->
        <div class="loading-animation">
          <div class="cloud cloud-1">☁️</div>
          <div class="cloud cloud-2">☁️</div>
          <div class="cloud cloud-3">☁️</div>
        </div>

        <!-- 加载文字 -->
        <h1 class="loading-title">🎨 正在前往我的空间...</h1>
        <p class="loading-subtitle">马上就好，请稍等片刻 ✨</p>

        <!-- 加载进度装饰 -->
        <div class="loading-dots">
          <span class="dot dot-1"></span>
          <span class="dot dot-2"></span>
          <span class="dot dot-3"></span>
        </div>
      </div>
    </div>

    <!-- 波浪装饰 -->
    <div class="wave-bottom wave-decoration">
      <svg viewBox="0 0 1440 200" preserveAspectRatio="none" style="width: 100%; height: 100px;">
        <path d="M0,96L48,112L96,96L144,64L192,48L240,64L288,96L336,112L384,96L432,64L480,48L528,64L576,96L624,112L672,96L720,64L768,48L816,64L864,96L912,112L960,96L1008,64L1056,48L1104,64L1152,96L1200,112L1248,96L1296,64L1344,48L1392,64L1440,96V200H0V96Z" fill="var(--color-mint)" opacity="0.4"/>
        <path d="M0,128L48,144L96,128L144,96L192,80L240,96L288,128L336,144L384,128L432,96L480,80L528,96L576,128L624,144L672,128L720,96L768,80L816,96L864,128L912,144L960,128L1008,96L1056,80L1104,96L1152,128L1200,144L1248,128L1296,96L1344,80L1392,96L1440,128V200H0V128Z" fill="var(--color-sky)" opacity="0.3"/>
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { onMounted } from 'vue'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 检查用户是否有个人空间
const checkUserSpace = async () => {
  const loginUser = loginUserStore.loginUser.id
  if (!loginUser) {
    router.replace('/user/login')
    return
  }
  const res = await listSpaceVoByPageUsingPost({
    userId: loginUser,
    current: 1,
    pageSize: 1,
    spaceType: SPACE_TYPE_ENUM.PRIVATE,
  })
  if (res.data.code === 0 && res.data.data) {
    if (res.data.data.records.length > 0) {
      const space = res.data.data.records[0]
      router.replace(`/space/${space.id}`)
    } else {
      router.push('/add_space')
      message.warning('😅 您还没有创建空间，请先创建个人空间')
    }
  } else {
    message.error('😅 跳转我的空间失败：' + res.data.message)
  }
}

onMounted(() => {
  checkUserSpace()
})
</script>

<style scoped lang="less">
#mySpacePage {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
  background: var(--bg-primary);
  padding: var(--space-6);
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
  opacity: 0.12;
}

.shape-1 {
  width: 100px;
  height: 100px;
  background: var(--color-coral);
  border-radius: 50%;
  top: 15%;
  left: 15%;
}

.shape-2 {
  width: 70px;
  height: 70px;
  background: var(--color-sunshine);
  border-radius: var(--radius-xl);
  top: 60%;
  right: 20%;
}

.shape-3 {
  width: 50px;
  height: 50px;
  background: var(--color-mint);
  border-radius: 50%;
  bottom: 25%;
  left: 25%;
}

/* ========== 加载容器 ========== */
.loading-container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 480px;
}

/* ========== 加载卡片 ========== */
.loading-card {
  background: var(--bg-card);
  padding: var(--space-12) var(--space-8);
  text-align: center;
}

/* ========== 加载动画 ========== */
.loading-animation {
  position: relative;
  height: 100px;
  margin-bottom: var(--space-8);
}

.cloud {
  position: absolute;
  font-size: var(--text-4xl);
  animation: cloudFloat 3s ease-in-out infinite;

  &.cloud-1 {
    left: 20%;
    animation-delay: 0s;
  }

  &.cloud-2 {
    left: 50%;
    transform: translateX(-50%);
    animation-delay: 0.5s;
    font-size: var(--text-6xl);
  }

  &.cloud-3 {
    right: 20%;
    animation-delay: 1s;
  }
}

@keyframes cloudFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}

/* ========== 加载文字 ========== */
.loading-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  margin: 0 0 var(--space-4) 0;
  background: var(--gradient-pop);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.loading-subtitle {
  font-size: var(--text-lg);
  color: var(--text-secondary);
  margin: 0 0 var(--space-8) 0;
  font-weight: 600;
}

/* ========== 加载点 ========== */
.loading-dots {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
}

.dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  animation: dotBounce 1.4s ease-in-out infinite;

  &.dot-1 {
    background: var(--color-coral);
    animation-delay: 0s;
  }

  &.dot-2 {
    background: var(--color-sunshine);
    animation-delay: 0.2s;
  }

  &.dot-3 {
    background: var(--color-mint);
    animation-delay: 0.4s;
  }
}

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
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

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .loading-card {
    padding: var(--space-8) var(--space-6);
  }

  .loading-title {
    font-size: var(--text-xl);
  }

  .loading-subtitle {
    font-size: var(--text-base);
  }

  .cloud {
    font-size: var(--text-3xl);

    &.cloud-2 {
      font-size: var(--text-4xl);
    }
  }
}

/* ========== 动画 ========== */
@keyframes bounce-in {
  0% {
    opacity: 0;
    transform: scale(0.3);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}
</style>
