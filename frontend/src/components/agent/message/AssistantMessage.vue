<template>
  <div class="assistant-message">
    <div class="message-avatar" aria-hidden="true">
      <span class="agent-orb"></span>
    </div>
    <div class="message-content">
      <div class="message-header">
        <span class="agent-name">智能助手</span>
      </div>
      <div class="message-body markdown-body" v-html="renderedContent"></div>

      <!-- 内联缩略图 -->
      <div v-if="images && images.length > 0" class="message-images">
        <img
          v-for="(img, index) in images"
          :key="index"
          :src="img.url"
          :alt="img.title || '图片'"
          @click="emit('previewImage', img.url)"
        />
      </div>

      <!-- 资源触发按钮 -->
      <button
        v-if="hasResources"
        class="resource-trigger-btn"
        @click="emit('toggleResources')"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21 15 16 10 5 21" />
        </svg>
        {{ images?.length || 0 }} 张图片 · {{ linkCount || 0 }} 个来源
      </button>

      <!-- 操作按钮栏（类 Grok） -->
      <div class="message-actions">
        <button class="action-icon-btn" title="重新生成" @click="emit('regenerate')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10" />
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
          </svg>
        </button>
        <button class="action-icon-btn" title="复制" @click="copyContent">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
          </svg>
        </button>
        <button class="action-icon-btn" title="分享" @click="emit('share')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="18" cy="5" r="3" />
            <circle cx="6" cy="12" r="3" />
            <circle cx="18" cy="19" r="3" />
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
          </svg>
        </button>
        <button class="action-icon-btn" :class="{ liked: liked }" title="点赞" @click="liked = !liked">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" :fill="liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
            <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14z" />
            <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
          </svg>
        </button>
      </div>

      <div v-if="isLoading" class="typing-cursor" aria-hidden="true">|</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'

interface Props {
  content: string
  isLoading?: boolean
  images?: { url: string; title?: string }[]
  linkCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  isLoading: false,
  images: undefined,
  linkCount: undefined,
})

const emit = defineEmits<{
  (e: 'previewImage', url: string): void
  (e: 'toggleResources'): void
  (e: 'regenerate'): void
  (e: 'share'): void
}>()

const { render } = useMarkdown()
const renderedContent = computed(() => render(props.content))

const liked = ref(false)

const hasResources = computed(() =>
  (props.images && props.images.length > 0) || (props.linkCount && props.linkCount > 0)
)

const copyContent = async () => {
  try {
    await navigator.clipboard.writeText(props.content)
  } catch {
    // fallback
  }
}
</script>

<style scoped>
.assistant-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 16px;
}

.message-avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-orb {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gradient-aurora);
  display: block;
}

[data-theme="aurora"] .agent-orb {
  box-shadow: 0 0 14px rgba(34, 104, 245, 0.3);
}

[data-theme="pop"] .agent-orb {
  box-shadow: 0 0 10px rgba(168, 85, 247, 0.2);
}

.message-content {
  max-width: min(680px, 100%);
  padding: 10px 16px;
  border-radius: 18px 18px 18px 4px;
  border: 1px solid var(--color-border-subtle);
  position: relative;
}

[data-theme="aurora"] .message-content {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-default);
}

[data-theme="pop"] .message-content {
  background: var(--color-bg-elevated);
  border-color: var(--color-border-subtle);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.agent-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-primary-500);
}

.message-body {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-primary);
  word-break: break-word;
}

/* Markdown styles */
.markdown-body :deep(p) {
  margin: 0 0 8px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(a) {
  color: var(--color-primary-500);
  text-decoration: none;
  font-weight: 500;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.markdown-body :deep(code:not(.hljs)) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-mono);
}

.markdown-body :deep(.hljs.code-block) {
  background: var(--color-bg-primary);
  border-radius: 8px;
  margin: 8px 0;
  overflow: hidden;
}

.markdown-body :deep(.code-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-subtle);
}

.markdown-body :deep(.code-lang) {
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.markdown-body :deep(.copy-btn) {
  background: transparent;
  border: 1px solid var(--color-border-default);
  color: var(--color-text-secondary);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
}

.markdown-body :deep(.copy-btn:hover) {
  background: var(--color-bg-hover, var(--color-bg-secondary));
  color: var(--color-text-primary);
}

.markdown-body :deep(.hljs.code-block code) {
  display: block;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  font-family: var(--font-mono);
}

.typing-cursor {
  display: inline;
  animation: blink 1s step-end infinite;
  color: var(--color-primary-500);
  font-weight: 300;
  margin-left: 1px;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* 内联缩略图 */
.message-images {
  display: flex;
  gap: 6px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.message-images img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.15s ease;
  border: 1px solid var(--color-border-subtle);
}

.message-images img:hover {
  transform: scale(1.05);
}

/* 资源触发按钮 */
.resource-trigger-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 10px;
  padding: 5px 12px;
  border: 1px solid var(--color-border-default);
  border-radius: 16px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.resource-trigger-btn:hover {
  border-color: var(--color-primary-500);
  color: var(--color-primary-500);
}

/* 操作按钮栏 */
.message-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--color-border-subtle);
}

.action-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-icon-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.action-icon-btn.liked {
  color: var(--color-primary-500);
}
</style>
