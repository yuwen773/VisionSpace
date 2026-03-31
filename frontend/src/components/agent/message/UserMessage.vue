<template>
  <div class="user-message">
    <div class="message-content">
      <div class="message-text">{{ content }}</div>
      <div class="message-time">{{ time }}</div>
    </div>
    <div class="message-avatar">
      <a-avatar :src="avatar" :size="32" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  content: string
  time?: string
  avatar?: string
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  time: '',
  avatar: '',
})

const time = computed(() => {
  if (props.time) return props.time
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.user-message {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 16px 16px 4px 16px;
  background: var(--gradient-aurora);
  color: var(--color-text-inverse);
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.message-avatar {
  flex-shrink: 0;
}
</style>
