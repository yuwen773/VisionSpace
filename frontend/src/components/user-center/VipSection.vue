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
        <p class="vip-expire">
          {{ isVip ? `到期时间：${formatExpire(vipExpireTime)}` : '升级 VIP 解锁更多权益' }}
        </p>
        <div v-if="!isVip" class="vip-perks">
          <div v-for="perk in vipPerks" :key="perk" class="perk-item">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            <span>{{ perk }}</span>
          </div>
        </div>
      </div>

      <!-- Redeem -->
      <div class="vip-redeem">
        <a-input-group compact class="redeem-group">
          <a-input
            v-model:value="vipCode"
            class="redeem-input"
            placeholder="输入兑换码"
            :maxlength="32"
            :disabled="redeeming"
            allow-clear
            @pressEnter="onRedeem"
          />
          <a-button type="primary" :loading="redeeming" :disabled="!vipCode.trim()" @click="onRedeem">
            兑换
          </a-button>
        </a-input-group>
        <p v-if="redeemMessage" class="redeem-message" :class="redeemStatus">
          <CheckCircleOutlined v-if="redeemStatus === 'success'" />
          <CloseCircleOutlined v-else />
          {{ redeemMessage }}
        </p>
      </div>

      <!-- Decorative shimmer -->
      <div class="vip-shimmer" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { exchangeVipUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import dayjs from 'dayjs'

const loginUserStore = useLoginUserStore()

// VIP 资格独立于 userRole：只看到期时间是否在将来
const vipExpireTime = computed(() => loginUserStore.loginUser?.vipExpireTime)
const isVip = computed(() => {
  if (!vipExpireTime.value) return false
  return dayjs(vipExpireTime.value).isAfter(dayjs())
})

const vipPerks = ['无限上传空间', '高清原图下载', 'AI 扩图无限制']

const vipCode = ref('')
const redeeming = ref(false)
const redeemMessage = ref('')
const redeemStatus = ref<'success' | 'error'>('success')

function formatExpire(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD') : ''
}

async function onRedeem() {
  const code = vipCode.value.trim()
  if (!code || redeeming.value) return
  redeeming.value = true
  redeemMessage.value = ''
  try {
    const res = await exchangeVipUsingPost({ vipCode: code })
    const result = res.data.data
    if (res.data.code === 0 && result?.status === 'success') {
      redeemStatus.value = 'success'
      redeemMessage.value = `兑换成功，会员到期 ${formatExpire(result.vipExpireTime)}`
      vipCode.value = ''
      // 刷新登录态，徽标与到期时间立即反映新权益
      await loginUserStore.fetchUserLogin()
    } else if (result?.status === 'already_used') {
      redeemStatus.value = 'error'
      redeemMessage.value = '该兑换码已被使用'
    } else if (result?.status === 'invalid') {
      redeemStatus.value = 'error'
      redeemMessage.value = '兑换码无效或已失效'
    } else {
      redeemStatus.value = 'error'
      redeemMessage.value = res.data.message || '兑换失败，请稍后重试'
    }
  } catch {
    redeemStatus.value = 'error'
    redeemMessage.value = '兑换失败，请稍后重试'
  } finally {
    redeeming.value = false
  }
}

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

.vip-redeem {
  position: relative;
  z-index: 1;
  margin-top: var(--space-6);
}

.redeem-group {
  display: flex;
  max-width: 320px;
  margin: 0 auto;
}

.redeem-input {
  flex: 1;
}

.redeem-message {
  margin-top: var(--space-3);
  font-size: var(--text-sm);
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.redeem-message.success {
  color: var(--color-success, #16a34a);
}

.redeem-message.error {
  color: var(--color-danger, #dc2626);
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
