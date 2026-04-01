<template>
  <div class="user-message">
    <div class="message-content">
      <div class="message-text">{{ content }}</div>
      <div class="message-time">{{ displayTime }}</div>
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

const displayTime = computed(() => {
  if (props.time) return props.time
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.user-message {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 8px 16px;
}

.message-content {
  max-width: 70%;
  padding: 11px 16px;
  border-radius: 18px 18px 4px 18px;
  background: var(--gradient-aurora);
  color: white;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.message-text {
  font-size: 14px;
  line-height: 1.55;
}

.message-time {
  font-size: 11px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
