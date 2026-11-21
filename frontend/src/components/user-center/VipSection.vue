<template>
  <RecordSection
    index="03"
    title="会员权益"
    label="MEMBER"
    lede="会员资格只看到期时间，与账号角色无关。兑换成功后，到期时间与页首徽标会立即更新。"
  >
    <!-- 通行证 -->
    <div class="pass" :class="{ 'pass--vip': isVip }">
      <svg
        class="pass-guilloche"
        viewBox="0 0 640 240"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <path
          v-for="line in guillocheLines"
          :key="line.id"
          :d="line.d"
          fill="none"
          stroke="currentColor"
          stroke-width="0.8"
        />
      </svg>

      <div class="pass-main">
        <span class="mono pass-kicker">VISIONSPACE / MEMBER PASS</span>
        <p class="pass-status">{{ isVip ? 'VIP' : '普通用户' }}</p>
        <p class="pass-holder">{{ holderName }}</p>
        <div class="pass-meta">
          <span>
            <span class="mono">状态 / STATUS</span>
            <b>{{ isVip ? '有效' : '未开通' }}</b>
          </span>
          <span>
            <span class="mono">{{ isVip ? '有效至 / UNTIL' : '升级后 / AFTER' }}</span>
            <b>{{ isVip ? formatExpire(vipExpireTime) : '—' }}</b>
          </span>
        </div>
      </div>

      <div class="pass-stub">
        <span class="mono">NO.</span>
        <span class="pass-no">{{ passNumber }}</span>
        <span class="mono">VS</span>
      </div>
    </div>

    <div class="member-grid">
      <!-- 权益 -->
      <div class="perks">
        <div class="uc-subhead">
          <span>{{ isVip ? '已生效的权益' : '开通后可用' }}</span>
          <span class="mono">PRIVILEGES</span>
        </div>
        <ol class="perk-list">
          <li v-for="(perk, index) in vipPerks" :key="perk" class="perk-row">
            <span class="perk-no mono">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="perk-name">{{ perk }}</span>
            <Check
              v-if="isVip"
              :size="15"
              :stroke-width="1.6"
              class="perk-check"
              aria-hidden="true"
            />
          </li>
        </ol>
        <p class="uc-hint">
          兑换码由活动或运营方发放。会员到期后，上传与扩图会回到普通用户的限制，不会删除已有作品。
        </p>
      </div>

      <!-- 兑换 -->
      <div class="redeem">
        <div class="uc-subhead">
          <span>兑换通行证</span>
          <span class="mono">REDEEM</span>
        </div>
        <form class="redeem-form" @submit.prevent="onRedeem">
          <label class="uc-label" for="uc-vip-code">兑换码 / CODE</label>
          <div class="redeem-row">
            <input
              id="uc-vip-code"
              v-model="vipCode"
              class="uc-field redeem-input"
              type="text"
              inputmode="text"
              autocomplete="off"
              spellcheck="false"
              placeholder="输入兑换码"
              :maxlength="32"
              :disabled="redeeming"
            />
            <button
              type="submit"
              class="uc-btn uc-btn--solid"
              :disabled="redeeming || !vipCode.trim()"
            >
              {{ redeeming ? '兑换中…' : '兑换' }}
            </button>
          </div>
        </form>
        <p
          v-if="redeemMessage"
          class="uc-status"
          :class="redeemStatus === 'success' ? 'uc-status--ok' : 'uc-status--error'"
          role="status"
          aria-live="polite"
        >
          <CircleCheck v-if="redeemStatus === 'success'" :size="15" :stroke-width="1.7" />
          <CircleX v-else :size="15" :stroke-width="1.7" />
          {{ redeemMessage }}
        </p>
      </div>
    </div>
  </RecordSection>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import dayjs from 'dayjs'
import { CircleCheck, CircleX, Check } from 'lucide-vue-next'
import { exchangeVipUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import RecordSection from '@/components/user-center/RecordSection.vue'

const loginUserStore = useLoginUserStore()

// VIP 资格独立于 userRole：只看到期时间是否在将来
const vipExpireTime = computed(() => loginUserStore.loginUser?.vipExpireTime)
const isVip = computed(() => {
  if (!vipExpireTime.value) return false
  return dayjs(vipExpireTime.value).isAfter(dayjs())
})

const holderName = computed(
  () => loginUserStore.loginUser?.userName || loginUserStore.loginUser?.userAccount || '未命名',
)
const passNumber = computed(() => {
  const id = loginUserStore.loginUser?.id
  return id ? String(id).padStart(6, '0') : '000000'
})

// 通行证底纹：手工排布的同心波纹，纯静态，不随主题变化形状
const guillocheLines = Array.from({ length: 11 }, (_, i) => {
  const offset = i * 22 - 110
  return {
    id: i,
    d: `M-40,${120 + offset} C80,${60 + offset} 180,${180 + offset} 300,${120 + offset} S520,${60 + offset} 680,${120 + offset}`,
  }
})

const vipPerks = ['无限上传空间', '高清原图下载', 'AI 扩图无限制']

const vipCode = ref('')
const redeeming = ref(false)
const redeemMessage = ref('')
const redeemStatus = ref<'success' | 'error'>('success')

function formatExpire(value?: string) {
  return value ? dayjs(value).format('YYYY.MM.DD') : ''
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
/* ============ 通行证 ============ */
.pass {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 168px;
  min-height: 224px;
  border: 1px solid var(--uc-line-strong);
  background: var(--uc-bg-raised);
  color: var(--uc-ink);
  overflow: hidden;
}

.pass-guilloche {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  color: var(--uc-accent);
  opacity: 0.14;
  pointer-events: none;
}

.pass-main {
  position: relative;
  z-index: 1;
  padding: clamp(22px, 3.4vw, 34px);
  min-width: 0;
}

.pass-kicker {
  font-size: 9px;
  letter-spacing: 0.2em;
}

.pass-status {
  margin: clamp(16px, 3vh, 26px) 0 0;
  font-family: var(--uc-serif);
  font-size: clamp(38px, 5vw, 62px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.02em;
}

.pass-holder {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--uc-ink-2);
}

.pass-meta {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(24px, 4vw, 56px);
  margin-top: clamp(20px, 3vh, 32px);
  padding-top: 16px;
  border-top: 1px solid var(--uc-line);
}

.pass-meta span {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pass-meta b {
  font-size: 14px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--uc-ink);
}

.pass-stub {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  padding: 22px 16px;
  border-left: 1px dashed var(--uc-line-strong);
  background: rgba(244, 241, 234, 0.02);
}

.pass-stub::before,
.pass-stub::after {
  content: '';
  position: absolute;
  left: -9px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--uc-bg);
  border: none;
}

.pass-stub::before {
  top: -8px;
}

.pass-stub::after {
  bottom: -8px;
}

.pass-no {
  font-family: var(--uc-mono);
  font-size: clamp(16px, 2vw, 22px);
  line-height: 1;
  color: var(--uc-ink);
  writing-mode: vertical-rl;
  text-orientation: upright;
  letter-spacing: 0.06em;
}

/* 已开通：唯一使用深色面的对象，颜色仍收在紫色系内 */
.pass--vip {
  border-color: transparent;
  background:
    radial-gradient(110% 140% at 92% 0%, rgba(183, 162, 255, 0.24) 0%, transparent 58%),
    linear-gradient(118deg, #120a26 0%, #231044 50%, #3b1d70 100%);
  color: #fff;
}

.pass--vip .pass-guilloche {
  color: rgba(255, 255, 255, 0.55);
  opacity: 0.28;
}

.pass--vip .mono {
  color: rgba(255, 255, 255, 0.86);
}

.pass--vip .pass-holder {
  color: rgba(255, 255, 255, 0.9);
}

.pass--vip .pass-meta {
  border-top-color: rgba(255, 255, 255, 0.28);
}

.pass--vip .pass-meta b {
  color: #fff;
}

.pass--vip .pass-stub {
  border-left-color: rgba(255, 255, 255, 0.36);
  background: rgba(0, 0, 0, 0.16);
}

.pass--vip .pass-no {
  color: #fff;
}

.pass--vip .pass-stub::before,
.pass--vip .pass-stub::after {
  background: var(--uc-bg);
  border-color: transparent;
}

/* ============ 权益 + 兑换 ============ */
.member-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(280px, 0.85fr);
  gap: clamp(28px, 5vw, 64px);
  margin-top: clamp(36px, 5vh, 56px);
}

.perk-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--uc-line);
}

.perk-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 15px 0;
  border-bottom: 1px solid var(--uc-line);
}

.perk-no {
  flex-shrink: 0;
  font-size: 9px;
  color: var(--uc-ink-3);
}

.perk-name {
  flex: 1;
  font-size: 15px;
  color: var(--uc-ink);
}

.perk-check {
  flex-shrink: 0;
  color: var(--uc-positive);
}

.redeem-form {
  margin-top: var(--space-2);
}

.redeem-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.redeem-input {
  flex: 1;
  min-width: 0;
  font-family: var(--uc-mono);
  font-size: 14px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.redeem-row .uc-btn {
  flex-shrink: 0;
  min-height: 46px;
}

@media (max-width: 900px) {
  .member-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .pass {
    grid-template-columns: minmax(0, 1fr);
  }

  .pass-stub {
    flex-direction: row;
    align-items: center;
    border-left: none;
    border-top: 1px dashed var(--uc-line-strong);
    padding: 14px 20px;
  }

  .pass-stub::before,
  .pass-stub::after {
    left: auto;
    top: -9px;
  }

  .pass-stub::before {
    left: -8px;
  }

  .pass-stub::after {
    right: -8px;
    bottom: auto;
  }

  .pass-no {
    writing-mode: horizontal-tb;
  }

  .redeem-row {
    flex-direction: column;
  }
}
</style>
