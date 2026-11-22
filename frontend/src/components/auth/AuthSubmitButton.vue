<template>
  <button type="submit" class="auth-submit" :disabled="loading">
    <Loader2 v-if="loading" :size="17" class="spin" aria-hidden="true" />
    <span>{{ loading ? (loadingLabel ?? label) : label }}</span>
    <span v-if="!loading" class="submit-arrow" aria-hidden="true">
      <ArrowRight :size="17" />
    </span>
  </button>
</template>

<script setup lang="ts">
import { ArrowRight, Loader2 } from 'lucide-vue-next'

/**
 * 认证表单的提交按钮：等待态只换文案与图标，尺寸不变。
 */
withDefaults(
  defineProps<{
    label: string
    loadingLabel?: string
    loading?: boolean
  }>(),
  { loading: false },
)
</script>

<style scoped lang="less">
.auth-submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  width: 100%;
  min-height: 3.125rem;
  padding: 0.75rem 1.25rem;
  border: none;
  border-radius: 0.5rem;
  background: var(--auth-accent);
  color: #fff;
  font-family: inherit;
  font-size: 0.9375rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    background 0.25s ease,
    transform 0.25s var(--auth-ease);

  &:hover:not(:disabled) {
    background: var(--auth-accent-strong);
  }

  &:active:not(:disabled) {
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.65;
    cursor: progress;
  }

  &:focus-visible {
    outline: 2px solid var(--auth-accent);
    outline-offset: 3px;
  }
}

.submit-arrow {
  display: inline-flex;
  transition: transform 0.3s var(--auth-ease);

  .auth-submit:hover:not(:disabled) & {
    transform: translateX(3px);
  }
}

.spin {
  animation: auth-spin 0.9s linear infinite;
}

@keyframes auth-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (pointer: coarse) {
  .auth-submit {
    min-height: 3rem;
  }
}
</style>
