<template>
  <div id="userCenterPage">
    <!-- Floating decorative orbs -->
    <div class="bg-orb bg-orb-1" />
    <div class="bg-orb bg-orb-2" />
    <div class="bg-orb bg-orb-3" />

    <!-- Hero Banner -->
    <div class="hero-banner">
      <div class="hero-inner">
        <div class="hero-avatar-wrap">
          <a-avatar :src="loginUser.userAvatar" :size="88" class="hero-avatar" />
          <div class="avatar-ring" />
        </div>
        <div class="hero-info">
          <h1 class="hero-name">{{ loginUser.userName }}</h1>
          <p class="hero-bio">{{ loginUser.userProfile || '暂无简介' }}</p>
          <div class="hero-tags">
            <span class="hero-tag">
              {{ loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
            </span>
          </div>
        </div>
      </div>
      <!-- Hero decoration -->
      <div class="hero-glow" />
    </div>

    <!-- Main Layout -->
    <div class="main-layout">
      <!-- Sidebar -->
      <div class="glass-sider">
        <a-menu
          v-model:selectedKeys="currentMenu"
          :items="menuItems"
          class="glass-menu"
        />
      </div>

      <!-- Content -->
      <div class="glass-content">
        <Transition name="fade-slide" mode="out-in">
          <ProfileSection v-if="currentMenu[0] === 'profile'" key="profile" />
          <PicturesSection v-else-if="currentMenu[0] === 'pictures'" key="pictures" @goToPicture="goToPictureDetail" />
          <VipSection v-else-if="currentMenu[0] === 'vip'" key="vip" />
          <FeedbackSection v-else-if="currentMenu[0] === 'feedback'" key="feedback" />
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'
import ProfileSection from '@/components/user-center/ProfileSection.vue'
import PicturesSection from '@/components/user-center/PicturesSection.vue'
import VipSection from '@/components/user-center/VipSection.vue'
import FeedbackSection from '@/components/user-center/FeedbackSection.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

type MenuKey = 'profile' | 'pictures' | 'vip' | 'feedback'
const currentMenu = ref<MenuKey[]>(['profile'])

interface MenuItem {
  key: MenuKey
  label: string
}
const menuItems: MenuItem[] = [
  { key: 'profile', label: '个人资料' },
  { key: 'pictures', label: '我的图片' },
  { key: 'vip', label: '会员权益' },
  { key: 'feedback', label: '意见反馈' },
]

const goToPictureDetail = (id: number) => {
  router.push(`/picture/${id}`)
}
</script>

<style scoped>
#userCenterPage {
  position: relative;
  min-height: 100vh;
  background: var(--bg-primary);
  background-image: var(--pattern-dots-subtle);
  background-size: 100% 100%, 24px 24px;
  overflow: hidden;
  padding-bottom: var(--space-12);
}

/* ========== Floating Orbs ========== */
.bg-orb {
  position: fixed;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(60px);
  z-index: 0;
}
.bg-orb-1 {
  width: 300px;
  height: 300px;
  background: linear-gradient(135deg, rgba(168, 85, 247, 0.2), rgba(236, 72, 153, 0.12));
  top: -60px;
  right: 10%;
  animation: float 6s ease-in-out infinite;
}
.bg-orb-2 {
  width: 220px;
  height: 220px;
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.15), rgba(244, 114, 182, 0.1));
  bottom: 15%;
  left: 5%;
  animation: float 8s ease-in-out infinite 2s;
}
.bg-orb-3 {
  width: 160px;
  height: 160px;
  background: linear-gradient(135deg, rgba(236, 72, 153, 0.12), rgba(168, 85, 247, 0.08));
  top: 40%;
  right: 5%;
  animation: float 7s ease-in-out infinite 1s;
}

/* ========== Hero Banner ========== */
.hero-banner {
  position: relative;
  margin: var(--space-6);
  padding: var(--space-8) var(--space-10);
  border-radius: var(--radius-3xl);
  background: var(--gradient-violet);
  overflow: hidden;
  z-index: 1;
  box-shadow: var(--shadow-glow-purple);
}

.hero-inner {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-6);
  z-index: 2;
}

.hero-glow {
  position: absolute;
  top: -50%;
  right: -10%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

.hero-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.hero-avatar {
  border: 3px solid rgba(255, 255, 255, 0.6);
  box-shadow: 0 0 24px rgba(255, 255, 255, 0.3);
}

.avatar-ring {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.2);
  animation: pulse-glow 3s ease-in-out infinite;
}

.hero-info {
  color: white;
}

.hero-name {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 700;
  color: white;
  margin: 0;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.hero-bio {
  font-size: var(--text-sm);
  color: rgba(255, 255, 255, 0.8);
  margin-top: var(--space-1);
  font-weight: 500;
}

.hero-tags {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 14px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: 700;
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.25);
}

/* ========== Main Layout ========== */
.main-layout {
  display: flex;
  gap: var(--space-6);
  margin: 0 var(--space-6);
  position: relative;
  z-index: 1;
}

/* ========== Glass Sidebar ========== */
.glass-sider {
  width: 200px;
  flex-shrink: 0;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-2xl);
  padding: var(--space-3);
  box-shadow: var(--shadow-card);
}

.glass-menu {
  background: transparent !important;
  border: none !important;
}

.glass-menu :deep(.ant-menu-item) {
  border-radius: var(--radius-xl) !important;
  margin: 2px 0 !important;
  font-weight: 600;
  color: var(--text-secondary) !important;
  transition: all var(--transition-fast) !important;
}

.glass-menu :deep(.ant-menu-item:hover) {
  background: rgba(168, 85, 247, 0.08) !important;
  color: var(--color-primary) !important;
}

.glass-menu :deep(.ant-menu-item-selected) {
  background: var(--gradient-sunset) !important;
  color: white !important;
  box-shadow: var(--shadow-glow-purple);
}

.glass-menu :deep(.ant-menu-item-selected::after) {
  display: none;
}

/* ========== Glass Content ========== */
.glass-content {
  flex: 1;
  min-width: 0;
}

/* ========== Transition ========== */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateX(12px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

/* ========== Responsive ========== */
@media (max-width: 768px) {
  .hero-banner {
    margin: var(--space-4);
    padding: var(--space-6);
  }

  .hero-inner {
    flex-direction: column;
    text-align: center;
  }

  .hero-tags {
    justify-content: center;
  }

  .main-layout {
    flex-direction: column;
    gap: var(--space-4);
    margin: 0 var(--space-4);
  }

  .glass-sider {
    width: 100%;
  }

  .glass-sider :deep(.ant-menu) {
    display: flex;
    justify-content: center;
  }

  .glass-sider :deep(.ant-menu-item) {
    display: inline-flex;
  }
}

@media (max-width: 480px) {
  .hero-name {
    font-size: var(--text-2xl);
  }
}
</style>
