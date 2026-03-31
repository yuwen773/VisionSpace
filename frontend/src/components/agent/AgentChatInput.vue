<template>
  <div class="agent-chat-input">
    <div class="input-container">
      <a-textarea
        v-model:value="inputText"
        :rows="1"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        :placeholder="placeholder"
        @pressEnter="handleSend"
        class="chat-textarea"
      />
      <div v-if="inputText.length > 0" class="char-count">
        {{ inputText.length }}
      </div>
      <a-button
        v-if="!loading"
        type="primary"
        :disabled="!inputText.trim()"
        @click="handleSend"
        class="send-button"
      >
        <template #icon>
          <SendOutlined />
        </template>
        发送
      </a-button>
      <a-button
        v-else
        danger
        @click="handleStop"
        class="stop-button"
      >
        <template #icon>
          <StopOutlined />
        </template>
        停止
      </a-button>
    </div>
    <div class="input-hint">
      <span>Enter 发送，Shift+Enter 换行</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SendOutlined, StopOutlined } from '@ant-design/icons-vue'

interface Props {
  loading?: boolean
  placeholder?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  placeholder: '输入您的需求...',
})

const emit = defineEmits(['send', 'stop'])

const inputText = ref('')

const handleSend = () => {
  if (!inputText.value.trim() || inputText.value.trim().length === 0) return
  emit('send', inputText.value.trim())
  inputText.value = ''
}

const handleStop = () => {
  emit('stop')
}
</script>

<style scoped>
.agent-chat-input {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--color-bg-primary);
}

.input-container {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  position: relative;
}

.chat-textarea {
  flex: 1;
  border-radius: 20px;
  padding: 10px 16px;
  background: var(--color-bg-elevated);
  border-color: var(--color-border-default);
}

.chat-textarea:focus {
  border-color: var(--color-primary-500);
  box-shadow: var(--shadow-glow-sm);
}

.send-button {
  border-radius: 20px;
  padding: 8px 20px;
}

.input-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: center;
  margin-top: 6px;
}

.char-count {
  position: absolute;
  right: 80px;
  bottom: 8px;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.stop-button {
  border-radius: 20px;
  padding: 8px 20px;
}
</style>
