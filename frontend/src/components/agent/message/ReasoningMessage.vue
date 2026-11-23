<template>
  <div class="reasoning-message">
    <button class="reasoning-header" @click="collapsed = !collapsed">
      <span class="reasoning-icon" :class="{ collapsed }">
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </span>
      <span class="reasoning-label">思考过程</span>
      <span v-if="collapsed" class="reasoning-preview">{{ preview }}</span>
    </button>
    <div v-show="!collapsed" class="reasoning-content">
      <div class="markdown-body" v-html="renderedContent" @click="onContentClick"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMarkdown } from '@/composables/useMarkdown'
import { useProgressiveRender } from '@/composables/useProgressiveRender'
import { handleContentClick } from '@/utils/diagramEvents'

interface Props {
  content: string
  /** 思考内容仍在流式输出：按节流渲染，结束后立即出终稿 */
  streaming?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  streaming: false,
})

const { render } = useMarkdown()
const collapsed = ref(false)

const { html: renderedContent, update } = useProgressiveRender({
  render: (content) => render(content),
})

watch(
  [() => props.content, () => props.streaming],
  ([content, streaming]) => update(content ?? '', Boolean(streaming)),
  { immediate: true },
)

// 思考过程里也会出现代码块，复制按钮按同一套事件委托处理
const onContentClick = (e: MouseEvent) => {
  handleContentClick(e)
}
const preview = computed(
  () => props.content.slice(0, 50) + (props.content.length > 50 ? '...' : ''),
)
</script>

<style scoped>
.reasoning-message {
  margin: 4px 16px;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-subtle);
}

.reasoning-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 2px 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.reasoning-header:hover {
  color: var(--color-text-primary);
}

.reasoning-icon {
  display: flex;
  transition: transform 0.2s ease;
}

.reasoning-icon.collapsed {
  transform: rotate(-90deg);
}

.reasoning-label {
  font-weight: 500;
  color: var(--color-text-tertiary);
}

.reasoning-preview {
  margin-left: 8px;
  color: var(--color-text-tertiary);
  font-size: 11px;
  opacity: 0.7;
}

.reasoning-content {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-subtle);
}

.markdown-body {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
  padding-left: 2px;
}

.markdown-body :deep(p) {
  margin: 0 0 4px 0;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(code) {
  background: var(--color-bg-secondary);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0 0 4px 0;
  padding-left: 16px;
}
/* ============ 响应式 ============ */
@media (max-width: 640px) {
  .reasoning-message {
    padding: 6px 10px;
  }
}
</style>
