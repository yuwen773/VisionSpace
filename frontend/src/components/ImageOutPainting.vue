<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="outpainting-modal-overlay" @click.self="closeModal">
        <div class="outpainting-modal">
          <!-- 背景光效 -->
          <div class="modal-glow"></div>

          <!-- 头部 -->
          <div class="modal-header">
            <div class="header-title">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
                <path d="M12 8v4l2 2"/>
                <path d="M2.5 12a9.5 9.5 0 0 1 1.5-3"/>
              </svg>
              <span>AI 扩图</span>
            </div>
            <button class="close-btn" @click="closeModal">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>

          <!-- 图片对比区域 -->
          <div class="comparison-container">
            <div class="comparison-panel">
              <div class="panel-label">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <polyline points="21 15 16 10 5 21"/>
                </svg>
                <span>原始图片</span>
              </div>
              <div class="panel-image">
                <img :src="picture?.url" :alt="picture?.name" />
              </div>
            </div>

            <div class="comparison-arrow">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14M12 5l7 7-7 7"/>
              </svg>
            </div>

            <div class="comparison-panel">
              <div class="panel-label result">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
                  <path d="M12 8v4l2 2"/>
                </svg>
                <span>扩图结果</span>
              </div>
              <div class="panel-image" :class="{ loading: !!taskId && !resultImageUrl }">
                <img v-if="resultImageUrl" :src="resultImageUrl" :alt="picture?.name" />
                <div v-else-if="taskId && !resultImageUrl" class="loading-placeholder">
                  <div class="loading-spinner"></div>
                  <span>AI 正在扩展图片...</span>
                </div>
                <div v-else class="empty-placeholder">
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                    <circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21 15 16 10 5 21"/>
                  </svg>
                  <span>点击下方按钮开始扩图</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 扩图参数提示 -->
          <div class="parameters-hint">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="16" x2="12" y2="12"/>
              <line x1="12" y1="8" x2="12.01" y2="8"/>
            </svg>
            <span>扩图参数：水平 2x，垂直 2x</span>
          </div>

          <!-- 操作按钮 -->
          <div class="modal-footer">
            <button class="action-btn outline" @click="closeModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
              <span>取消</span>
            </button>
            <button
              class="action-btn secondary"
              @click="createTask"
              :disabled="!!taskId"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
                <path d="M12 8v4l2 2"/>
              </svg>
              <span>{{ taskId ? '扩图中...' : 'AI 扩图' }}</span>
            </button>
            <button
              v-if="resultImageUrl"
              class="action-btn primary"
              @click="handleUpload"
              :disabled="uploadLoading"
            >
              <svg v-if="!uploadLoading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <svg v-else class="spinner" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" stroke-opacity="0.3"/>
                <path d="M12 2a10 10 0 0 1 10 10"/>
              </svg>
              <span>{{ uploadLoading ? '应用中...' : '应用结果' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'

interface Props {
  picture?: API.PictureVO
  spaceId?: string
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const visible = ref<boolean>(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
  clearPolling()
  resultImageUrl.value = undefined
  taskId.value = undefined
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

const uploadLoading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async () => {
  if (!resultImageUrl.value) return

  uploadLoading.value = true
  try {
    const params: API.PictureUploadRequest = {
      fileUrl: resultImageUrl.value,
      spaceId: props.spaceId,
    }
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('扩图应用成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    }
  } catch (err: any) {
    message.error('图片上传失败：' + err.message)
  }
  uploadLoading.value = false
}

// ai扩图
const resultImageUrl = ref<string>()
const taskId = ref<string>()

const createTask = async () => {
  if (!props.picture?.id) {
    message.warning('请先上传图片')
    return
  }
  if (taskId.value) return

  const res = await createPictureOutPaintingTaskUsingPost({
    pictureId: props.picture.id,
    parameters: {
      xScale: 2,
      yScale: 2,
    },
  })
  if (res.data.code === 0 && res.data.data) {
    message.success('扩图任务已创建，请耐心等待')
    taskId.value = res.data.data.output?.taskId
    startPolling()
  } else {
    message.error('扩图任务创建失败：' + res.data.message)
  }
}

// 轮询定时器
let pollingTimer: ReturnType<typeof setInterval> | null = null

const startPolling = () => {
  if (!taskId.value) return

  pollingTimer = setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({
        taskId: taskId.value,
      })
      if (res.data.code === 0 && res.data.data) {
        const taskResult = res.data.data.output
        if (taskResult?.taskStatus === 'SUCCEEDED') {
          message.success('扩图完成！')
          resultImageUrl.value = taskResult.outputImageUrl
          clearPolling()
        } else if (taskResult?.taskStatus === 'FAILED') {
          message.error('扩图失败：' + taskResult.message)
          clearPolling()
        }
      } else {
        message.error('查询扩图状态失败')
        clearPolling()
      }
    } catch (err: any) {
      message.error('轮询失败：' + err.message)
      clearPolling()
    }
  }, 3000)
}

const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
  taskId.value = undefined
}

onUnmounted(() => {
  clearPolling()
})
</script>

<style scoped lang="less">
/* ========== Aurora (Dark) Theme ========== */
[data-theme="aurora"] {
  --modal-bg: rgba(15, 23, 42, 0.95);
  --modal-border: rgba(255, 255, 255, 0.1);
  --modal-glow-color: rgba(168, 85, 247, 0.12);
  --modal-title-color: white;
  --modal-text-color: rgba(255, 255, 255, 0.7);
  --modal-text-secondary: rgba(255, 255, 255, 0.5);
  --modal-bg-hover: rgba(255, 255, 255, 0.06);
  --modal-bg-active: rgba(255, 255, 255, 0.1);
  --modal-bg-section: rgba(0, 0, 0, 0.3);
  --modal-border-subtle: rgba(255, 255, 255, 0.06);
  --modal-btn-hover: rgba(168, 85, 247, 0.2);
  --modal-btn-border: rgba(168, 85, 247, 0.4);
  --accent-primary: #a855f7;
  --accent-secondary: #f472b6;
  --accent-info: #60a5fa;
  --accent-danger: #f87171;
  --shadow-modal: 0 0 0 1px rgba(168, 85, 247, 0.2), 0 25px 80px rgba(0, 0, 0, 0.6), 0 0 100px rgba(168, 85, 247, 0.15);
}

/* ========== Pop (Light) Theme ========== */
[data-theme="pop"] {
  --modal-bg: #ffffff;
  --modal-border: var(--border-light);
  --modal-glow-color: rgba(155, 93, 229, 0.08);
  --modal-title-color: var(--text-primary);
  --modal-text-color: var(--text-secondary);
  --modal-text-secondary: var(--text-tertiary);
  --modal-bg-hover: var(--bg-hover);
  --modal-bg-active: var(--bg-active);
  --modal-bg-section: var(--bg-tertiary);
  --modal-border-subtle: var(--border-light);
  --modal-btn-hover: rgba(155, 93, 229, 0.1);
  --modal-btn-border: rgba(155, 93, 229, 0.3);
  --accent-primary: var(--color-grape);
  --accent-secondary: var(--color-pink);
  --accent-info: var(--color-sky);
  --accent-danger: var(--color-coral);
  --shadow-modal: 0 4px 30px rgba(0, 0, 0, 0.12), 0 0 0 1px var(--border-light);
}

/* ========== Modal 遮罩 ========== */
.outpainting-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(12px);
}

/* ========== Modal 主体 ========== */
.outpainting-modal {
  position: relative;
  width: 100%;
  max-width: 800px;
  display: flex;
  flex-direction: column;
  background: var(--modal-bg);
  border: 1px solid var(--modal-border);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}

.modal-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 50% 0%, var(--modal-glow-color) 0%, transparent 50%);
  pointer-events: none;
}

/* ========== 头部 ========== */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--modal-border-subtle);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--modal-title-color);

  svg {
    color: var(--accent-primary);
  }
}

.close-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--modal-bg-hover);
  border: 1px solid var(--modal-border);
  border-radius: 10px;
  color: var(--modal-text-color);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--accent-danger);
    border-color: var(--accent-danger);
    color: white;
  }
}

/* ========== 图片对比区域 ========== */
.comparison-container {
  display: flex;
  align-items: stretch;
  gap: 20px;
  padding: 24px;
}

.comparison-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--modal-bg-hover);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--modal-text-color);
  width: fit-content;

  svg {
    color: var(--modal-text-secondary);
  }

  &.result {
    background: var(--modal-btn-hover);
    color: var(--accent-primary);

    svg {
      color: var(--accent-primary);
    }
  }
}

.panel-image {
  flex: 1;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--modal-bg-section);
  border: 1px solid var(--modal-border-subtle);
  border-radius: 16px;
  overflow: hidden;

  img {
    max-width: 100%;
    max-height: 300px;
    object-fit: contain;
  }

  &.loading {
    background: var(--modal-btn-hover);
    border-color: var(--modal-btn-border);
  }
}

.loading-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  color: var(--modal-text-color);
  font-size: 14px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--modal-btn-border);
  border-top-color: var(--accent-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--modal-text-secondary);

  svg {
    opacity: 0.5;
  }

  span {
    font-size: 13px;
  }
}

.comparison-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--modal-text-secondary);
  flex-shrink: 0;
}

/* ========== 参数提示 ========== */
.parameters-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0 24px;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--accent-info) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent-info) 20%, transparent);
  border-radius: 12px;
  font-size: 13px;
  color: var(--accent-info);
}

/* ========== 底部操作 ========== */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--modal-border-subtle);
}

/* ========== 按钮样式 ========== */
.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &.primary {
    background: var(--accent-primary);
    border: none;
    color: white;
    box-shadow: 0 4px 20px color-mix(in srgb, var(--accent-primary) 40%, transparent);

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 6px 30px color-mix(in srgb, var(--accent-primary) 50%, transparent);
    }
  }

  &.secondary {
    background: color-mix(in srgb, var(--accent-info) 15%, transparent);
    border: 1px solid color-mix(in srgb, var(--accent-info) 30%, transparent);
    color: var(--accent-info);

    &:hover:not(:disabled) {
      background: color-mix(in srgb, var(--accent-info) 25%, transparent);
      border-color: color-mix(in srgb, var(--accent-info) 50%, transparent);
    }
  }

  &.outline {
    background: var(--modal-bg-hover);
    border: 1px solid var(--modal-border);
    color: var(--modal-text-color);

    &:hover {
      background: var(--modal-bg-active);
      border-color: var(--modal-text-secondary);
    }
  }
}

.spinner {
  animation: spin 1s linear infinite;
}

/* ========== 过渡动画 ========== */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;

  .outpainting-modal {
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;

  .outpainting-modal {
    opacity: 0;
    transform: scale(0.95) translateY(20px);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .outpainting-modal-overlay {
    padding: 12px;
  }

  .comparison-container {
    flex-direction: column;
    padding: 16px;
    gap: 16px;
  }

  .comparison-arrow {
    transform: rotate(90deg);
  }

  .panel-image {
    min-height: 150px;
  }

  .modal-footer {
    padding: 16px 20px;
    flex-wrap: wrap;
  }

  .action-btn {
    flex: 1;
    justify-content: center;
    min-width: 120px;
  }
}
</style>
