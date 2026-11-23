<template>
  <div
    class="agent-chat-input"
    :class="{
      focused: isFocused,
      'has-content': inputText.length > 0 || attachments.length > 0,
      'is-dragging': dragging,
    }"
    @dragenter.prevent="onDragEnter"
    @dragover.prevent="onDragOver"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
  >
    <div class="input-container">
      <!-- Attachment preview strip -->
      <div v-if="attachments.length > 0" class="attachment-strip">
        <div
          v-for="att in attachments"
          :key="att.id"
          class="attachment-card"
          :class="att.status"
          :title="att.status === 'error' ? att.error : att.name"
        >
          <!-- Image preview -->
          <template v-if="att.previewUrl">
            <div class="img-preview" @click="openPreview(att.previewUrl!)">
              <img :src="att.previewUrl" :alt="att.name" />
              <div v-if="att.status === 'uploading'" class="upload-state">
                <span class="upload-spinner" aria-hidden="true"></span>
              </div>
              <div v-else-if="att.status === 'error'" class="upload-state error">上传失败</div>
              <div v-else class="img-zoom-hint">
                <svg
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
              </div>
            </div>
            <button
              v-if="att.status === 'error'"
              class="retry-badge"
              @click.stop="retryUpload(att)"
              aria-label="重试上传"
            >
              重试
            </button>
            <button class="remove-badge" @click="removeAttachment(att)" aria-label="移除">
              &times;
            </button>
          </template>
          <!-- File chip -->
          <template v-else>
            <div class="file-icon">
              <svg
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z" />
                <polyline points="13 2 13 9 20 9" />
              </svg>
            </div>
            <span class="file-name">{{ truncateName(att.name) }}</span>
            <button class="remove-badge" @click="removeAttachment(att)" aria-label="移除">
              &times;
            </button>
          </template>
        </div>
      </div>

      <!-- Input row -->
      <div class="input-row">
        <!-- Left toolbar -->
        <div class="toolbar-left">
          <button
            class="toolbar-btn attach-btn"
            :disabled="!attachmentsEnabled"
            @click="triggerFilePicker"
            aria-label="添加附件"
            :title="
              attachmentsEnabled ? '上传图片（JPG/PNG/WebP，最大 2MB）' : '当前模式下不能上传图片'
            "
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"
              />
            </svg>
          </button>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          multiple
          :accept="ATTACHMENT_ACCEPT_ATTR"
          class="hidden-input"
          @change="handleFileSelect"
        />

        <!-- Textarea -->
        <div class="textarea-wrap">
          <textarea
            ref="textareaRef"
            v-model="inputText"
            :placeholder="
              inputText.length === 0 && attachments.length === 0
                ? 'Enter 发送，Shift+Enter 换行，Ctrl+V 粘贴图片'
                : ''
            "
            :rows="1"
            class="chat-textarea"
            @input="autoResize"
            @focus="isFocused = true"
            @blur="isFocused = false"
            @keydown="handleKeydown"
            @paste="handlePaste"
          />
          <transition name="count-fade">
            <span v-if="inputText.length > 0" class="char-count">{{ inputText.length }}</span>
          </transition>
        </div>

        <!-- Send / Stop -->
        <transition name="action-swap" mode="out-in">
          <button
            v-if="!loading"
            key="send"
            class="action-btn send-btn"
            :disabled="!canSend"
            @click="handleSend"
            aria-label="发送"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <line x1="22" y1="2" x2="11" y2="13" />
              <polygon points="22 2 15 22 11 13 2 9 22 2" />
            </svg>
          </button>

          <button
            v-else
            key="stop"
            class="action-btn stop-btn"
            @click="handleStop"
            aria-label="停止生成"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
              <rect x="4" y="4" width="16" height="16" rx="3" />
            </svg>
          </button>
        </transition>
      </div>
    </div>

    <!-- Hint -->
    <div class="input-hint">
      <span class="disclaimer">内容由AI生成，仅供参考</span>
    </div>

    <!-- 拖拽遮罩：用计数器判断何时真正离开，避免子元素反复触发 dragleave -->
    <transition name="drag-mask">
      <div v-if="dragging" class="drag-mask" aria-hidden="true">
        <div class="drag-mask-inner">松开即可上传图片</div>
      </div>
    </transition>

    <!-- Full-size image preview lightbox -->
    <ImagePreview v-model:open="previewOpen" :url="lightboxUrl" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import ImagePreview from '@/components/ImagePreview.vue'
import { deleteAgentImage, uploadAgentImage, type AgentImage } from '@/api-custom/agent'
import { ATTACHMENT_ACCEPT_ATTR, validateAttachment } from '@/utils/attachmentPolicy'

type AttachmentStatus = 'uploading' | 'done' | 'error'

interface Attachment {
  id: string
  file: File
  name: string
  previewUrl: string | null
  status: AttachmentStatus
  resource?: AgentImage
  error?: string
  /**
   * 已从草稿移除。上传是跨会话异步的，不能靠"是否还在当前数组里"判断，
   * 否则切到别的会话再回来时状态会被误判。
   */
  removed?: boolean
}

interface Draft {
  text: string
  attachments: Attachment[]
}

interface Props {
  loading?: boolean
  /** 当前会话标识：草稿按会话隔离，切换会话不丢输入 */
  sessionId?: string
  /** 站内找图模式不允许上传附件 */
  attachmentsEnabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  sessionId: '',
  attachmentsEnabled: true,
})

const emit = defineEmits<{
  (e: 'send', text: string, uploaded: AgentImage[]): void
  (e: 'stop'): void
}>()

const inputText = ref('')
const isFocused = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const attachments = ref<Attachment[]>([])
const previewOpen = ref(false)
const lightboxUrl = ref('')

// 草稿按会话隔离：只放内存，与 chat-demo 的做法一致，离开页面即丢弃
const drafts = new Map<string, Draft>()
let activeSession = props.sessionId || ''

const openPreview = (url: string) => {
  lightboxUrl.value = url
  previewOpen.value = true
}

const canSend = computed(() => {
  // 只要还有上传中或失败的附件，就不允许发送：发出去会缺图
  if (attachments.value.some((att) => att.status !== 'done')) return false
  return Boolean(inputText.value.trim()) || attachments.value.length > 0
})

// Truncate filename: keep extension, middle-truncate the name part
const truncateName = (name: string, maxLen = 16): string => {
  if (name.length <= maxLen) return name
  const dotIdx = name.lastIndexOf('.')
  const ext = dotIdx >= 0 ? name.slice(dotIdx) : ''
  const base = dotIdx >= 0 ? name.slice(0, dotIdx) : name
  const keep = maxLen - ext.length - 3 // 3 for "..."
  if (keep <= 0) return name.slice(0, maxLen)
  return base.slice(0, Math.ceil(keep / 2)) + '...' + base.slice(-Math.floor(keep / 2)) + ext
}

const autoResize = () => {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const handlePaste = (e: ClipboardEvent) => {
  if (!props.attachmentsEnabled) return
  const items = e.clipboardData?.items
  if (!items) return

  const files: File[] = []
  for (const item of items) {
    if (item.kind === 'file') {
      const file = item.getAsFile()
      if (file) files.push(file)
    }
  }
  if (files.length > 0) {
    e.preventDefault()
    addFiles(files)
  }
}

const validate = (file: File): string | null => validateAttachment(file)

const upload = async (att: Attachment) => {
  att.removed = false
  att.status = 'uploading'
  att.error = undefined
  try {
    const resource = await uploadAgentImage(att.file)
    // 上传期间被移除：这张图不会随消息发送，直接回收
    if (att.removed) {
      void deleteAgentImage(resource.resourceId).catch(() => {})
      return
    }
    att.resource = resource
    att.status = 'done'
  } catch (error) {
    if (att.removed) return
    att.status = 'error'
    att.error = error instanceof Error ? error.message : '上传失败'
  }
}

const addFiles = (files: File[]) => {
  if (!props.attachmentsEnabled) {
    message.warning('当前模式下不能上传图片')
    return
  }
  const accepted: Attachment[] = []
  for (const file of files) {
    const error = validate(file)
    if (error) {
      message.error(`${file.name}：${error}`)
      continue
    }
    accepted.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      file,
      name: file.name,
      previewUrl: URL.createObjectURL(file),
      status: 'uploading',
    })
  }
  attachments.value.push(...accepted)
  // 上传必须操作数组里的响应式代理：直接改原始对象不会驱动视图
  const added = attachments.value.slice(attachments.value.length - accepted.length)
  // 每个附件独立上传：一个失败不影响其他文件
  for (const att of added) void upload(att)
}

const retryUpload = (att: Attachment) => {
  if (att.status !== 'error') return
  void upload(att)
}

const removeAttachment = (att: Attachment) => {
  const index = attachments.value.indexOf(att)
  if (index === -1) return
  attachments.value.splice(index, 1)
  att.removed = true
  if (att.previewUrl) URL.revokeObjectURL(att.previewUrl)
  // 已上传但未发送的图片要回收，避免留下孤儿资源
  if (att.status === 'done' && att.resource) {
    void deleteAgentImage(att.resource.resourceId).catch(() => {})
  }
}

const triggerFilePicker = () => {
  if (!props.attachmentsEnabled) return
  fileInputRef.value?.click()
}

const handleFileSelect = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (!input.files) return
  addFiles(Array.from(input.files))
  input.value = ''
}

const clearDraft = () => {
  inputText.value = ''
  attachments.value.forEach((a) => {
    a.removed = true
    if (a.previewUrl) URL.revokeObjectURL(a.previewUrl)
  })
  attachments.value = []
  drafts.set(activeSession, { text: '', attachments: [] })
  nextTick(() => {
    if (textareaRef.value) textareaRef.value.style.height = 'auto'
  })
}

const handleSend = () => {
  if (!canSend.value) return
  const uploaded = attachments.value
    .map((att) => att.resource)
    .filter((resource): resource is AgentImage => Boolean(resource))
  emit('send', inputText.value.trim(), uploaded)
  clearDraft()
}

const handleStop = () => {
  emit('stop')
}

// ============ 拖拽上传 ============
const dragDepth = ref(0)
const dragging = computed(() => dragDepth.value > 0)

const carriesFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types ?? []).includes('Files')

const onDragEnter = (e: DragEvent) => {
  if (!props.attachmentsEnabled || !carriesFiles(e)) return
  dragDepth.value++
}

const onDragOver = (e: DragEvent) => {
  if (props.attachmentsEnabled && carriesFiles(e)) e.preventDefault()
}

const onDragLeave = () => {
  dragDepth.value = Math.max(0, dragDepth.value - 1)
}

const onDrop = (e: DragEvent) => {
  dragDepth.value = 0
  if (!props.attachmentsEnabled) return
  const files = Array.from(e.dataTransfer?.files ?? [])
  if (files.length > 0) addFiles(files)
}

// ============ 草稿隔离 ============
const saveDraft = () => {
  drafts.set(activeSession, { text: inputText.value, attachments: attachments.value })
}

watch(
  () => props.sessionId,
  (next) => {
    const key = next || ''
    if (key === activeSession) return
    saveDraft()
    activeSession = key
    const draft = drafts.get(key)
    inputText.value = draft?.text ?? ''
    attachments.value = draft?.attachments ?? []
    nextTick(autoResize)
  },
)

onUnmounted(() => {
  // 离开页面时把所有未发送的预览地址回收掉
  for (const draft of drafts.values()) {
    draft.attachments.forEach((a) => {
      if (a.previewUrl) URL.revokeObjectURL(a.previewUrl)
    })
  }
  attachments.value.forEach((a) => {
    if (a.previewUrl) URL.revokeObjectURL(a.previewUrl)
  })
})
</script>

<style scoped>
/* ============ Root ============ */
.agent-chat-input {
  padding: 10px clamp(16px, 4vw, 24px) 8px;
  position: relative;
  z-index: 10;
}

/* ============ Input Container ============ */
.input-container {
  border-radius: 16px;
  border: 1px solid var(--color-border-default);
  background: var(--color-bg-elevated);
  transition:
    border-color 0.2s ease,
    box-shadow 0.25s ease,
    transform 0.25s ease;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.agent-chat-input.focused .input-container {
  border-color: var(--color-primary);
  box-shadow:
    0 0 0 4px color-mix(in srgb, var(--color-primary) 14%, transparent),
    var(--shadow-md);
}

/* ============ Attachment Strip ============ */
.attachment-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 12px 4px;
}

.attachment-card {
  position: relative;
  animation: cardIn 0.3s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: scale(0.85) translateY(6px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* -- Image preview (larger) -- */
.img-preview {
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  border: 1.5px solid var(--color-border-subtle);
  background: var(--color-bg-tertiary);
  cursor: pointer;
  position: relative;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.img-preview:hover {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 20%, transparent);
  transform: scale(1.04);
}

.img-preview:hover .img-zoom-hint {
  opacity: 1;
}

.img-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.img-zoom-hint {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s ease;
}

/* -- File chip -- */
.attachment-card:has(.file-icon) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px 8px 12px;
  border-radius: 10px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border-subtle);
}

.file-icon {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.file-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* -- Remove button -- */
.remove-badge {
  position: absolute;
  top: -6px;
  right: -6px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-bg-elevated);
  border-radius: 50%;
  background: var(--color-bg-tertiary);
  color: var(--color-text-tertiary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  padding: 0;
  transition: all 0.15s ease;
}

.remove-badge:hover {
  background: #ef4444;
  border-color: #ef4444;
  color: white;
  transform: scale(1.1);
}

/* File chip variant: remove badge positioned inline */
.attachment-card:has(.file-icon) .remove-badge {
  position: static;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 4px;
  background: transparent;
  font-size: 14px;
}

.attachment-card:has(.file-icon) .remove-badge:hover {
  background: color-mix(in srgb, var(--color-error) 12%, transparent);
  color: var(--color-error);
}

/* ============ 上传状态 ============ */
.attachment-card.error .img-preview {
  border-color: var(--color-error);
}

.upload-state {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.attachment-card.error .upload-state {
  background: color-mix(in srgb, var(--color-error) 78%, transparent);
}

.upload-spinner {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: upload-spin 0.8s linear infinite;
}

@keyframes upload-spin {
  to {
    transform: rotate(360deg);
  }
}

.retry-badge {
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  padding: 2px 8px;
  border: none;
  border-radius: 8px;
  background: var(--color-error);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 6px color-mix(in srgb, var(--color-error) 35%, transparent);
}

.retry-badge:hover {
  filter: brightness(1.08);
}

/* ============ 拖拽遮罩 ============ */
.drag-mask {
  position: absolute;
  inset: 6px;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  border: 2px dashed var(--color-primary);
  background: color-mix(in srgb, var(--color-bg-elevated) 86%, transparent);
  backdrop-filter: blur(6px);
  pointer-events: none;
}

.drag-mask-inner {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
}

.drag-mask-enter-active,
.drag-mask-leave-active {
  transition: opacity 0.15s ease;
}

.drag-mask-enter-from,
.drag-mask-leave-to {
  opacity: 0;
}

.hidden-input {
  display: none;
}

/* ============ Input Row ============ */
.input-row {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px 6px 6px;
}

/* ============ Left Toolbar ============ */
.toolbar-left {
  display: flex;
  align-items: center;
  padding-top: 2px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
}

.toolbar-btn:hover {
  background: var(--color-bg-hover, var(--color-bg-tertiary));
  color: var(--color-text-secondary);
}

.toolbar-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.toolbar-btn:disabled:hover {
  background: transparent;
  color: var(--color-text-tertiary);
}

.attach-btn:hover {
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

/* ============ Textarea ============ */
.textarea-wrap {
  flex: 1;
  position: relative;
  min-width: 0;
}

.chat-textarea {
  width: 100%;
  padding: 9px 44px 9px 6px;
  font-family: var(--font-body);
  font-size: 15px;
  line-height: 1.6;
  color: var(--color-text-primary);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  overflow-y: auto;
  min-height: 24px;
}

.chat-textarea::placeholder {
  color: var(--color-text-tertiary);
  opacity: 0.8;
}

.chat-textarea::-webkit-scrollbar {
  width: 3px;
}

.chat-textarea::-webkit-scrollbar-thumb {
  background: var(--color-border-default);
  border-radius: 3px;
}

.char-count {
  position: absolute;
  right: 10px;
  bottom: 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
  pointer-events: none;
  opacity: 0.6;
  background: var(--color-bg-elevated);
  padding: 1px 4px;
  border-radius: 4px;
}

.count-fade-enter-active,
.count-fade-leave-active {
  transition: opacity 150ms ease;
}

.count-fade-enter-from,
.count-fade-leave-to {
  opacity: 0;
}

/* ============ Send / Stop Buttons ============ */
.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  flex-shrink: 0;
  margin-bottom: 1px;
  transition:
    background-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

/* -- Send: 单一强调色实底，不使用外发光环 -- */
.send-btn {
  background: var(--color-primary-600);
  color: #ffffff;
  box-shadow: 0 4px 14px color-mix(in srgb, var(--color-primary) 28%, transparent);
}

.send-btn:disabled {
  background: var(--color-bg-tertiary);
  color: var(--color-text-tertiary);
  box-shadow: none;
  cursor: not-allowed;
}

.send-btn:not(:disabled):hover {
  background: var(--color-primary-700);
  transform: translateY(-1px);
  box-shadow: 0 6px 20px color-mix(in srgb, var(--color-primary) 34%, transparent);
}

.send-btn:not(:disabled):active {
  transform: scale(0.94);
  box-shadow: none;
}

/* -- Stop：表示「正在输出」的状态色 -- */
.stop-btn {
  background: var(--color-error);
  color: #ffffff;
  animation: stop-breathe 1.8s ease-in-out infinite;
}

.stop-btn:hover {
  filter: brightness(1.08);
  transform: scale(1.04);
}

.stop-btn:active {
  transform: scale(0.94);
  animation: none;
}

@keyframes stop-breathe {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.82;
  }
}

/* Button swap transition */
.action-swap-enter-active {
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

.action-swap-leave-active {
  transition: all 0.15s ease;
}

.action-swap-enter-from {
  opacity: 0;
  transform: scale(0.8) rotate(-10deg);
}

.action-swap-leave-to {
  opacity: 0;
  transform: scale(0.8) rotate(10deg);
}

/* ============ Hint (disclaimer) ============ */
.input-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 6px;
  font-size: 10px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.02em;
  opacity: 0.45;
  transition: opacity 0.2s ease;
}

.agent-chat-input.focused .input-hint {
  opacity: 0.6;
}

.disclaimer {
  font-style: italic;
}
/* ============ 响应式 ============ */
@media (max-width: 640px) {
  .agent-chat-input {
    padding: 6px 10px 4px;
  }

  .input-container {
    border-radius: 16px;
  }

  .attachment-strip {
    padding: 8px 10px 4px;
  }
}
</style>
