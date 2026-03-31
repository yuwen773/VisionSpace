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
      <a-button
        type="primary"
        :loading="loading"
        :disabled="!inputText.trim()"
        @click="handleSend"
        class="send-button"
      >
        <template #icon>
          <SendOutlined v-if="!loading" />
        </template>
        发送
      </a-button>
    </div>
    <div class="input-hint">
      <span>Enter 发送，Shift+Enter 换行</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SendOutlined } from '@ant-design/icons-vue'

interface Props {
  loading?: boolean
  placeholder?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  placeholder: '输入您的需求...',
})

const emit = defineEmits(['send'])

const inputText = ref('')

const handleSend = () => {
  if (!inputText.value.trim() || inputText.value.trim().length === 0) return
  emit('send', inputText.value.trim())
  inputText.value = ''
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
</style>
