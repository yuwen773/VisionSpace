<template>
  <section v-if="visible" class="grant-panel">
    <header class="panel-header">
      <h3 class="panel-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
        访问授权
      </h3>
      <span class="panel-hint">授权只对当前资源生效，并且必须设置到期时间</span>
    </header>

    <!-- 授权列表 -->
    <div v-if="grants.length" class="grant-list">
      <div v-for="grant in grants" :key="grant.id" class="grant-item">
        <div class="grant-head">
          <span class="grantee-name">
            {{ grant.granteeUserName || `用户 ${grant.granteeUserId ?? '-'}` }}
          </span>
          <a-tag :color="statusColor(grant.status)">{{ grantStatusText(grant.status) }}</a-tag>
          <a-tag v-if="grant.status === 'ACTIVE' && grantExpiresSoon(grant)" color="orange">
            即将到期
          </a-tag>
        </div>
        <div class="grant-meta">
          <span>到期时间：{{ formatDateTime(grant.expiresTime) }}</span>
          <span v-if="grant.grantedByUserName">授权人：{{ grant.grantedByUserName }}</span>
          <span v-if="grant.reason">理由：{{ grant.reason }}</span>
          <span v-if="grant.status === 'REVOKED'">
            已于 {{ formatDateTime(grant.revokeTime) }} 撤销<template v-if="grant.revokeReason">（{{ grant.revokeReason }}）</template>
          </span>
        </div>
        <a-button
          v-if="canManage && grant.status === 'ACTIVE'"
          size="small"
          danger
          class="revoke-btn"
          @click="openRevoke(grant)"
        >
          撤销
        </a-button>
      </div>
    </div>
    <p v-else class="grant-empty">暂无授权记录</p>

    <!-- 授予表单 -->
    <div v-if="canManage" class="grant-form">
      <a-input-number
        v-model:value="form.granteeUserId"
        :min="1"
        :precision="0"
        placeholder="被授权用户 ID"
        class="form-field"
      />
      <a-input-number
        v-model:value="form.validityDays"
        :min="1"
        :max="GRANT_MAX_VALIDITY_DAYS"
        :precision="0"
        placeholder="有效天数"
        class="form-field"
      />
      <a-input
        v-model:value="form.reason"
        placeholder="授权理由（可选）"
        :maxlength="500"
        class="form-field reason"
      />
      <a-button type="primary" :loading="submitting" @click="doGrant">授予只读访问</a-button>
    </div>
    <p v-if="canManage" class="form-tip">
      有效期 {{ GRANT_MIN_VALIDITY_DAYS }}–{{ GRANT_MAX_VALIDITY_DAYS }} 天，默认
      {{ GRANT_DEFAULT_VALIDITY_DAYS }} 天；到期后自动失效。
    </p>

    <!-- 撤销确认 -->
    <a-modal
      v-model:open="revokeVisible"
      title="撤销授权"
      ok-text="确认撤销"
      cancel-text="取消"
      :ok-button-props="{ danger: true }"
      @ok="doRevoke"
    >
      <p class="revoke-target">
        即将撤销：{{ revokeTarget?.granteeUserName || `用户 ${revokeTarget?.granteeUserId ?? '-'}` }}
      </p>
      <a-textarea
        v-model:value="revokeReason"
        :rows="3"
        :maxlength="500"
        placeholder="撤销理由（可选）"
      />
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  GRANT_DEFAULT_VALIDITY_DAYS,
  GRANT_MAX_VALIDITY_DAYS,
  createGrantUsingPost,
  formatDateTime,
  grantExpiresSoon,
  grantExpiresTime,
  grantStatusText,
  listGrantsUsingGet,
  revokeGrantUsingPost,
} from '@/api-custom/resourceProtection'
import { useLoginUserStore } from '@/stores/userLogin'

/** 授权至少给一天，避免出现事实上的即时永久授权 */
const GRANT_MIN_VALIDITY_DAYS = 1

interface Props {
  pictureId?: number
}

const props = defineProps<Props>()
const loginUserStore = useLoginUserStore()

const visible = ref(false)
const grants = ref<API.ResourceGrantVO[]>([])
const submitting = ref(false)
const revokeVisible = ref(false)
const revokeReason = ref('')
const revokeTarget = ref<API.ResourceGrantVO | null>(null)

const form = reactive({
  granteeUserId: undefined as number | undefined,
  validityDays: GRANT_DEFAULT_VALIDITY_DAYS as number | undefined,
  reason: '',
})

/**
 * 平台管理员只能审计授权记录，不能授予或撤销：
 * 管理身份本身不构成对私人资源的授权依据。
 */
const canManage = computed(() => visible.value && loginUserStore.loginUser.userRole !== 'admin')

const statusColor = (status?: string) => {
  const map: Record<string, string> = { ACTIVE: 'green', REVOKED: 'red', EXPIRED: 'default' }
  return map[status ?? ''] ?? 'default'
}

const loadGrants = async () => {
  if (!props.pictureId) return
  const res = await listGrantsUsingGet({ pictureId: props.pictureId })
  if (res.data.code === 0) {
    visible.value = true
    grants.value = res.data.data ?? []
  } else {
    // 没有授权查看资格：整个面板不出现，也不暴露资源授权信息
    visible.value = false
    grants.value = []
  }
}

const doGrant = async () => {
  if (!props.pictureId) return
  if (!form.granteeUserId || form.granteeUserId <= 0) {
    message.warning('请填写被授权用户 ID')
    return
  }
  const days = form.validityDays ?? GRANT_DEFAULT_VALIDITY_DAYS
  submitting.value = true
  try {
    const res = await createGrantUsingPost({
      pictureId: props.pictureId,
      granteeUserId: form.granteeUserId,
      expiresTime: grantExpiresTime(days).toISOString(),
      reason: form.reason || undefined,
    })
    if (res.data.code === 0) {
      message.success('已授予限时只读访问')
      form.granteeUserId = undefined
      form.validityDays = GRANT_DEFAULT_VALIDITY_DAYS
      form.reason = ''
      await loadGrants()
    } else {
      message.error(res.data.message || '授权失败')
    }
  } catch {
    message.error('授权失败')
  } finally {
    submitting.value = false
  }
}

const openRevoke = (grant: API.ResourceGrantVO) => {
  revokeTarget.value = grant
  revokeReason.value = ''
  revokeVisible.value = true
}

const doRevoke = async () => {
  const target = revokeTarget.value
  if (!target?.id) return
  submitting.value = true
  try {
    const res = await revokeGrantUsingPost({
      grantId: target.id,
      reason: revokeReason.value || undefined,
    })
    if (res.data.code === 0) {
      message.success('授权已撤销')
      revokeVisible.value = false
      await loadGrants()
    } else {
      message.error(res.data.message || '撤销失败')
    }
  } catch {
    message.error('撤销失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadGrants)
watch(() => props.pictureId, loadGrants)
</script>

<style scoped lang="less">
.grant-panel {
  margin-top: var(--space-5);
  padding: var(--space-4);
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
}

.panel-header {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  flex-wrap: wrap;
  margin-bottom: var(--space-3);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--color-text-primary);

  svg {
    width: 15px;
    height: 15px;
    color: var(--color-primary);
  }
}

.panel-hint {
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.grant-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.grant-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3);
  background: var(--color-bg-tertiary, var(--color-bg-secondary));
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-md);
}

.grant-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
  min-width: 160px;
}

.grantee-name {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--color-text-primary);
}

.grant-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.grant-empty {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.revoke-btn {
  flex-shrink: 0;
}

.grant-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.form-field {
  width: 150px;

  &.reason {
    flex: 1;
    min-width: 180px;
  }
}

.form-tip {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  color: var(--color-text-tertiary);
}

.revoke-target {
  margin-bottom: var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-primary);
}
</style>
