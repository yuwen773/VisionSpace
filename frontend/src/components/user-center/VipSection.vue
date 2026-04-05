<template>
  <div class="glass-section">
    <h2 class="section-title">会员权益</h2>

    <div class="vip-card">
      <!-- Badge -->
      <div class="vip-badge-wrap">
        <div class="vip-badge" :class="{ active: isVip }">
          <svg v-if="isVip" class="vip-crown" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M2 20h20M5 20V9l4 3 3-6 3 6 4-3v11" />
          </svg>
          <span>{{ isVip ? 'VIP' : '普通用户' }}</span>
        </div>
      </div>

      <!-- Info -->
      <div class="vip-info">
        <p class="vip-expire">{{ isVip ? `到期时间：${vipExpireTime}` : '升级 VIP 解锁更多权益' }}</p>
        <div v-if="!isVip" class="vip-perks">
          <div v-for="perk in vipPerks" :key="perk" class="perk-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <span>{{ perk }}</span>
          </div>
        </div>
      </div>

      <!-- Decorative shimmer -->
      <div class="vip-shimmer" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

// TODO: 接入真实 VIP 接口
const isVip = ref(false)
const vipExpireTime = ref('2026-12-31')

const vipPerks = ['无限上传空间', '高清原图下载', 'AI 扩图无限制']
</script>

<style scoped>
.vip-card {
  position: relative;
  text-align: center;
  padding: var(--space-8) var(--space-6);
  background: var(--gradient-cream);
  border-radius: var(--radius-2xl);
  border: 1px solid var(--border-subtle);
  overflow: hidden;
}

.vip-badge-wrap {
  margin-bottom: var(--space-5);
}

.vip-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  padding: var(--space-3) var(--space-8);
  border-radius: var(--radius-full);
  color: var(--color-text-tertiary);
  background: var(--bg-tertiary);
  border: 2px solid var(--border-default);
  transition: all var(--transition-slow);
}

.vip-badge.active {
  background: var(--gradient-sunset);
  color: white;
  border: none;
  box-shadow: var(--shadow-glow-purple);
}

.vip-crown {
  width: 22px;
  height: 22px;
}

.vip-info {
  position: relative;
  z-index: 1;
}

.vip-expire {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  font-weight: 500;
}

.vip-perks {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-5);
  align-items: center;
}

.perk-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 500;
}

.perk-item svg {
  width: 16px;
  height: 16px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.vip-shimmer {
  position: absolute;
  top: 0;
  left: -100%;
  width: 200%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(168, 85, 247, 0.04) 40%,
    rgba(236, 72, 153, 0.06) 50%,
    rgba(168, 85, 247, 0.04) 60%,
    transparent 100%
  );
  animation: vip-shimmer-slide 4s ease-in-out infinite;
}

@keyframes vip-shimmer-slide {
  0% { transform: translateX(-50%); }
  100% { transform: translateX(50%); }
}
</style>
