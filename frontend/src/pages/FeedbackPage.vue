<template>
  <div id="feedbackPage">
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
    </div>

    <div class="page-container">
      <div class="page-header">
        <h1 class="page-title">意见反馈</h1>
        <p class="page-subtitle">您的问题和建议是我们改进的动力</p>
      </div>

      <div class="feedback-form-card">
        <div class="form-section">
          <label class="form-label">反馈类型</label>
          <div class="type-cards">
            <div
              v-for="type in typeOptions"
              :key="type.value"
              class="type-card"
              :class="{ active: form.type === type.value }"
              @click="form.type = type.value"
            >
              <span class="type-icon">{{ type.icon }}</span>
              <span class="type-label">{{ type.label }}</span>
            </div>
          </div>
        </div>

        <div class="form-section">
          <label class="form-label">标题</label>
          <a-input
            v-model:value="form.title"
            placeholder="请简要描述您的问题"
            :maxlength="50"
            show-count
          />
        </div>

        <div class="form-section">
          <label class="form-label">详细描述</label>
          <a-textarea
            v-model:value="form.content"
            placeholder="请详细描述您的问题或建议..."
            :rows="6"
            :maxlength="2000"
            show-count
          />
        </div>

        <div class="form-section">
          <label class="form-label">附件截图（可选，最多5张）</label>
          <div class="upload-list">
            <div
              v-for="(url, index) in form.pictureUrls"
              :key="index"
              class="upload-item"
            >
              <img :src="url" alt="附件" class="upload-preview" />
              <span class="upload-remove" @click="removePicture(index)">×</span>
            </div>
            <div v-if="form.pictureUrls.length < 5" class="upload-trigger" @click="triggerUpload">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 5v14M5 12h14"/>
              </svg>
              <span>上传图片</span>
            </div>
          </div>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            multiple
            style="display: none"
            @change="handleFileChange"
          />
        </div>

        <div class="form-actions">
          <button class="submit-btn" @click="doSubmit" :disabled="submitting">
            <span v-if="!submitting">提交反馈</span>
            <span v-else>提交中...</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { addFeedbackUsingPost, uploadFeedbackAttachment } from '@/api/feedbackController'

const router = useRouter()
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const typeOptions = [
  { value: 1, label: '产品建议', icon: '💡' },
  { value: 2, label: '内容举报', icon: '🚩' },
  { value: 3, label: '工单支持', icon: '🎫' },
]

const form = reactive({
  type: 1,
  title: '',
  content: '',
  pictureUrls: [] as string[],
})

const triggerUpload = () => {
  fileInputRef.value?.click()
}

const handleFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files) return

  // 并行上传多个文件
  const uploadPromises: Promise<void>[] = []
  const maxUploads = Math.min(files.length, 5 - form.pictureUrls.length)

  for (let i = 0; i < maxUploads; i++) {
    const file = files[i]
    uploadPromises.push(
      (async () => {
        try {
          const res = await uploadFeedbackAttachment(file)
          if (res.data.code === 0 && res.data.data) {
            form.pictureUrls.push(res.data.data)
          }
        } catch (err: any) {
          message.error(err.response?.data?.message || '图片上传失败')
        }
      })()
    )
  }

  await Promise.all(uploadPromises)
}

const removePicture = (index: number) => {
  form.pictureUrls.splice(index, 1)
}

const doSubmit = async () => {
  if (!form.title.trim()) {
    message.warning('请输入标题')
    return
  }
  if (!form.content.trim()) {
    message.warning('请输入详细描述')
    return
  }

  submitting.value = true
  try {
    const res = await addFeedbackUsingPost({
      type: form.type,
      title: form.title,
      content: form.content,
      pictureUrls: form.pictureUrls,
    })
    if (res.data.code === 0) {
      message.success('反馈提交成功！')
      router.back()
    } else {
      message.error(res.data.message || '提交失败')
    }
  } catch (err) {
    message.error('提交失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="less" scoped>
#feedbackPage {
  min-height: calc(100vh - 200px);
  position: relative;
}

.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.15;
}

.orb-1 {
  width: 600px; height: 600px;
  background: radial-gradient(circle, #a855f7 0%, transparent 70%);
  top: -200px; right: -200px;
}

.orb-2 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, #ec4899 0%, transparent 70%);
  bottom: -150px; left: -150px;
}

.orb-3 {
  width: 400px; height: 400px;
  background: radial-gradient(circle, #9333ea 0%, transparent 70%);
  top: 50%; left: 50%;
  transform: translate(-50%, -50%);
}

.page-container {
  position: relative;
  z-index: 1;
  max-width: 700px;
  margin: 0 auto;
  padding: var(--space-8) var(--space-4);
}

.page-header {
  text-align: center;
  margin-bottom: var(--space-8);
}

.page-title {
  font-size: 2rem;
  font-weight: 800;
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: var(--space-2);
}

.page-subtitle {
  color: var(--color-text-tertiary);
  font-size: 1rem;
}

.feedback-form-card {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
}

.form-section {
  margin-bottom: var(--space-6);
}

.form-label {
  display: block;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--space-3);
}

:deep(.ant-input),
:deep(.ant-input-affix-wrapper) {
  background: var(--glass-bg-light) !important;
  backdrop-filter: var(--glass-blur-light);
  border-color: var(--color-border-default) !important;
  color: var(--color-text-primary) !important;
  border-radius: var(--radius-lg) !important;
  padding: 10px 16px;
  transition: all var(--transition-base);
}

:deep(.ant-input:hover),
:deep(.ant-input-affix-wrapper:hover) {
  border-color: var(--color-primary-500) !important;
}

:deep(.ant-input:focus),
:deep(.ant-input-affix-wrapper-focused) {
  border-color: var(--color-primary-500) !important;
  box-shadow: 0 0 0 3px rgba(34, 104, 245, 0.15) !important;
}

:deep(.ant-input::placeholder) {
  color: var(--color-text-disabled) !important;
}

:deep(.ant-input-textarea textarea) {
  background: var(--glass-bg-light) !important;
  border-color: var(--color-border-default) !important;
  color: var(--color-text-primary) !important;
  border-radius: var(--radius-lg) !important;
  padding: 12px 16px;
}

:deep(.ant-input-textarea textarea:hover) {
  border-color: var(--color-primary-500) !important;
}

:deep(.ant-input-textarea textarea:focus) {
  border-color: var(--color-primary-500) !important;
  box-shadow: 0 0 0 3px rgba(34, 104, 245, 0.15) !important;
}

:deep(.ant-input-number) {
  background: var(--glass-bg-light) !important;
  border-color: var(--color-border-default) !important;
  color: var(--color-text-primary) !important;
  border-radius: var(--radius-lg) !important;
}

.type-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-5);
  background: var(--color-bg-secondary);
  border: 2px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--color-primary);
  }

  &.active {
    border-color: var(--color-primary);
    background: rgba(168, 85, 247, 0.1);
  }

  .type-icon {
    font-size: 1.5rem;
  }

  .type-label {
    font-weight: 600;
    color: var(--color-text-primary);
  }
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.upload-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.upload-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
}

.upload-trigger {
  width: 100px;
  height: 100px;
  border: 2px dashed var(--color-border-default);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--color-primary);
    color: var(--color-primary);
  }

  svg {
    width: 24px;
    height: 24px;
  }

  span {
    font-size: 12px;
  }
}

.form-actions {
  display: flex;
  justify-content: center;
  margin-top: var(--space-8);
}

.submit-btn {
  padding: var(--space-3) var(--space-10);
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-secondary-500));
  color: white;
  border: none;
  border-radius: var(--radius-full);
  font-weight: 700;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.4);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}
</style>
