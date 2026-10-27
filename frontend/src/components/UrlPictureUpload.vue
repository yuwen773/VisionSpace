<template>
  <div class="url-picture-upload">
    <!-- URL 输入区域 -->
    <div class="url-input-section" v-if="!localPreviewUrl">
      <div class="url-icon">
        <svg
          width="32"
          height="32"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
      </div>
      <p class="url-hint">输入图片链接，快速导入网络图片</p>
      <div class="url-input-wrap">
        <input
          v-model="fileUrl"
          type="text"
          class="url-input"
          placeholder="https://example.com/image.jpg"
          @keydown.enter="handleUpload"
          autocomplete="off"
        />
        <button
          class="url-submit-btn"
          :class="{ loading: loading }"
          @click="handleUpload"
          :disabled="!fileUrl || loading"
        >
          <span v-if="!loading" class="btn-text">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M5 12h14M12 5l7 7-7 7" />
            </svg>
            导入
          </span>
          <span v-else class="btn-loading">
            <svg
              class="spinner"
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="10" stroke-opacity="0.3" />
              <path d="M12 2a10 10 0 0 1 10 10" />
            </svg>
          </span>
        </button>
      </div>
      <p class="url-error" v-if="error">{{ error }}</p>
    </div>

    <!-- 图片预览 -->
    <div class="url-preview" v-else>
      <div class="preview-image-wrap">
        <img :src="localPreviewUrl" alt="预览" class="preview-image" />
        <div class="preview-glow"></div>
      </div>
      <button class="change-url-btn" @click="resetUpload">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
          <polyline points="17 8 12 3 7 8" />
          <line x1="12" y1="3" x2="12" y2="15" />
        </svg>
        <span>更换链接</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByUrlUsingPost } from '@/api/pictureController.ts'
import { toId } from '@/utils'

interface Props {
  picture?: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  spaceId?: string | number
}

const props = defineProps<Props>()
const fileUrl = ref<string>()
const localPreviewUrl = ref<string>()
const loading = ref<boolean>(false)
const error = ref<string>()

// 上传图片
const handleUpload = async () => {
  if (!fileUrl.value?.trim()) {
    error.value = '请输入图片链接'
    return
  }

  error.value = undefined
  loading.value = true

  try {
    const params: API.PictureUploadRequest = { fileUrl: fileUrl.value }
    if (props.picture?.id) {
      params.id = props.picture.id
    }
    const spaceId = toId(props.spaceId)
    if (spaceId !== undefined) {
      params.spaceId = spaceId
    }

    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      localPreviewUrl.value = res.data.data.url
      message.success('图片导入成功')
      props.onSuccess?.(res.data.data)
    } else {
      error.value = res.data.message || '导入失败，请检查链接是否有效'
    }
  } catch (err: any) {
    error.value = err.message || '图片导入失败'
    message.error('图片导入失败')
  } finally {
    loading.value = false
  }
}

// 重置上传
const resetUpload = () => {
  fileUrl.value = undefined
  localPreviewUrl.value = undefined
  error.value = undefined
}
</script>

<style scoped lang="less">
/* ========== Theme-aware Variables (Adapts to Aurora/Ziyan) ========== */
.url-picture-upload {
  --upload-bg: transparent;
  --upload-icon-bg: var(--color-info-bg);
  --upload-icon-border: var(--color-border-accent);
  --upload-icon-color: var(--color-accent-purple);
  --upload-hint-color: var(--color-text-tertiary);
  --upload-input-bg: var(--color-bg-tertiary);
  --upload-input-border: var(--color-border-default);
  --upload-input-focus-border: var(--color-accent-purple);
  --upload-input-color: var(--color-text-primary);
  --upload-input-placeholder: var(--color-text-tertiary);
  --upload-btn-bg: var(--gradient-aurora);
  --upload-btn-shadow: var(--shadow-glow-sm);
  --upload-error-bg: var(--color-error-bg);
  --upload-error-border: var(--color-error-light);
  --upload-error-color: var(--color-error);
  --upload-preview-bg: rgba(0, 0, 0, 0.05);
  --upload-preview-shadow: var(--shadow-lg);
  --upload-change-btn-bg: var(--color-bg-elevated, var(--color-bg-secondary));
  --upload-change-btn-border: var(--color-border-default);
  --upload-change-btn-color: var(--color-text-secondary);
  --upload-change-btn-hover-bg: var(--color-bg-hover);
  --upload-change-btn-hover-border: var(--color-accent-purple);
  --upload-change-btn-hover-color: var(--color-text-primary);
  --upload-glow-color: var(--color-star-glow, rgba(168, 85, 247, 0.2));
}

.url-picture-upload {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--upload-bg);
}

/* ========== URL 输入区域 ========== */
.url-input-section {
  width: 100%;
  max-width: 500px;
  padding: 40px 32px;
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fade-in 0.5s ease;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.url-icon {
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--upload-icon-bg);
  border: 1px solid var(--upload-icon-border);
  border-radius: 20px;
  color: var(--upload-icon-color);
  margin-bottom: 20px;
  animation: pulse-icon 3s ease-in-out infinite;
}

@keyframes pulse-icon {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 var(--upload-icon-border);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 0 20px 5px var(--upload-glow-color);
  }
}

.url-hint {
  font-size: 14px;
  color: var(--upload-hint-color);
  margin: 0 0 20px 0;
  text-align: center;
}

.url-input-wrap {
  display: flex;
  gap: 12px;
  width: 100%;
  padding: 6px;
  background: var(--upload-input-bg);
  border: 1px solid var(--upload-input-border);
  border-radius: 16px;
  transition: all 0.3s ease;

  &:focus-within {
    border-color: var(--upload-input-focus-border);
    box-shadow: 0 0 30px var(--upload-glow-color);
  }
}

.url-input {
  flex: 1;
  padding: 14px 18px;
  background: transparent;
  border: none;
  outline: none;
  color: var(--upload-input-color);
  font-size: 15px;
  min-width: 0;

  &::placeholder {
    color: var(--upload-input-placeholder);
  }
}

.url-submit-btn {
  padding: 14px 24px;
  background: var(--upload-btn-bg);
  border: none;
  border-radius: 12px;
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  flex-shrink: 0;
  box-shadow: var(--upload-btn-shadow);

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 6px 25px var(--upload-glow-color);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &.loading {
    background: var(--upload-icon-color);
    opacity: 0.7;
  }
}

.btn-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.url-error {
  margin: 12px 0 0 0;
  padding: 10px 16px;
  background: var(--upload-error-bg);
  border: 1px solid var(--upload-error-border);
  border-radius: 10px;
  color: var(--upload-error-color);
  font-size: 13px;
  text-align: center;
  animation: shake 0.5s ease;
}

@keyframes shake {
  0%,
  100% {
    transform: translateX(0);
  }
  20%,
  60% {
    transform: translateX(-5px);
  }
  40%,
  80% {
    transform: translateX(5px);
  }
}

/* ========== 图片预览 ========== */
.url-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: fade-in 0.5s ease;
}

.preview-image-wrap {
  position: relative;
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.preview-image {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
  border-radius: 16px;
  box-shadow: var(--upload-preview-shadow);
}

.preview-glow {
  position: absolute;
  inset: -20px;
  background: radial-gradient(circle at center, var(--upload-glow-color) 0%, transparent 60%);
  pointer-events: none;
  z-index: -1;
}

.change-url-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 20px;
  padding: 10px 20px;
  background: var(--upload-change-btn-bg);
  backdrop-filter: blur(10px);
  border: 1px solid var(--upload-change-btn-border);
  border-radius: 30px;
  color: var(--upload-change-btn-color);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    color: var(--upload-hint-color);
    transition: color 0.3s ease;
  }

  &:hover {
    background: var(--upload-change-btn-hover-bg);
    border-color: var(--upload-change-btn-hover-border);
    color: var(--upload-change-btn-hover-color);

    svg {
      color: var(--upload-change-btn-hover-color);
    }
  }
}
</style>
