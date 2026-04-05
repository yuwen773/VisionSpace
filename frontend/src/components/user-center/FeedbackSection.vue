<template>
  <div class="glass-section">
    <h2 class="section-title">意见反馈</h2>
    <p class="feedback-subtitle">您的问题和建议是我们改进的动力</p>

    <!-- 反馈类型 -->
    <div class="form-group">
      <label class="form-label">反馈类型</label>
      <div class="type-cards">
        <div
          v-for="item in feedbackTypeOptions"
          :key="item.value"
          class="type-card"
          :class="{ active: feedbackForm.type === item.value }"
          @click="feedbackForm.type = item.value"
        >
          <component :is="item.icon" :size="24" class="type-icon" />
          <span class="type-name">{{ item.label }}</span>
          <div v-if="feedbackForm.type === item.value" class="type-glow" />
        </div>
      </div>
    </div>

    <!-- 标题 -->
    <div class="form-group">
      <label class="form-label">标题</label>
      <a-input
        v-model:value="feedbackForm.title"
        placeholder="请简要描述您的问题"
        :maxlength="50"
        show-count
        class="glass-input"
      />
    </div>

    <!-- 详细描述 -->
    <div class="form-group">
      <label class="form-label">详细描述</label>
      <a-textarea
        v-model:value="feedbackForm.content"
        placeholder="请详细描述您的问题或建议..."
        :rows="6"
        :maxlength="2000"
        show-count
        class="glass-input"
      />
    </div>

    <!-- 附件截图 -->
    <div class="form-group">
      <label class="form-label">附件截图（可选，最多5张）</label>
      <div class="upload-list">
        <div v-for="(url, index) in feedbackForm.pictureUrls" :key="index" class="upload-item">
          <img :src="url" alt="附件" />
          <span class="upload-remove" @click="removeFeedbackPicture(index)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </span>
        </div>
        <div v-if="feedbackForm.pictureUrls.length < 5" class="upload-trigger" @click="triggerFeedbackUpload">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 5v14M5 12h14" />
          </svg>
          <span>上传图片</span>
        </div>
      </div>
      <input
        ref="feedbackFileInputRef"
        type="file"
        accept="image/*"
        multiple
        style="display: none"
        @change="handleFeedbackFileChange"
      />
    </div>

    <!-- 提交 -->
    <div class="form-actions">
      <a-button
        type="primary"
        size="large"
        :loading="feedbackSubmitting"
        class="submit-btn"
        @click="doFeedbackSubmit"
      >
        提交反馈
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { Lightbulb, Flag, Ticket } from 'lucide-vue-next'
import { addFeedbackUsingPost, uploadFeedbackAttachment } from '@/api/feedbackController'

const feedbackTypeOptions = [
  { value: 1, label: '产品建议', icon: Lightbulb },
  { value: 2, label: '内容举报', icon: Flag },
  { value: 3, label: '工单支持', icon: Ticket },
]

const feedbackForm = ref({
  type: 1,
  title: '',
  content: '',
  pictureUrls: [] as string[],
})

const feedbackSubmitting = ref(false)
const feedbackFileInputRef = ref<HTMLInputElement | null>(null)

const triggerFeedbackUpload = () => {
  feedbackFileInputRef.value?.click()
}

const handleFeedbackFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files) return

  const maxUploads = Math.min(files.length, 5 - feedbackForm.value.pictureUrls.length)
  const uploadPromises: Promise<void>[] = []

  for (let i = 0; i < maxUploads; i++) {
    const file = files[i]
    if (!file.type.startsWith('image/')) {
      message.warning(`${file.name} 不是图片文件，已跳过`)
      continue
    }
    if (file.size > 5 * 1024 * 1024) {
      message.warning(`${file.name} 超过 5MB，已跳过`)
      continue
    }
    uploadPromises.push(
      (async () => {
        try {
          const res = await uploadFeedbackAttachment(file)
          if (res.data.code === 0 && res.data.data) {
            feedbackForm.value.pictureUrls.push(res.data.data)
          }
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : '图片上传失败'
          message.error(msg)
        }
      })()
    )
  }

  await Promise.all(uploadPromises)
  ;(e.target as HTMLInputElement).value = ''
}

const removeFeedbackPicture = (index: number) => {
  feedbackForm.value.pictureUrls.splice(index, 1)
}

const doFeedbackSubmit = async () => {
  if (!feedbackForm.value.title.trim()) {
    message.warning('请输入标题')
    return
  }
  if (!feedbackForm.value.content.trim()) {
    message.warning('请输入详细描述')
    return
  }

  feedbackSubmitting.value = true
  try {
    const res = await addFeedbackUsingPost({
      type: feedbackForm.value.type,
      title: feedbackForm.value.title,
      content: feedbackForm.value.content,
      pictureUrls: feedbackForm.value.pictureUrls,
    })
    if (res.data.code === 0) {
      message.success('反馈提交成功！感谢您的宝贵意见')
      feedbackForm.value = { type: 1, title: '', content: '', pictureUrls: [] }
    } else {
      message.error(res.data.message || '提交失败')
    }
  } catch (err) {
    message.error('提交失败，请重试')
  } finally {
    feedbackSubmitting.value = false
  }
}
</script>

<style scoped>
.feedback-subtitle {
  color: var(--color-text-tertiary);
  font-size: var(--text-sm);
  margin-bottom: var(--space-6);
}

/* Form */
.form-group {
  margin-bottom: var(--space-5);
}

.form-label {
  display: block;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-2);
  font-size: var(--text-sm);
}

/* Type Cards */
.type-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
}

.type-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  background: var(--bg-secondary);
  border: 2px solid var(--border-subtle);
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--transition-fast);
  overflow: hidden;
}

.type-card:hover {
  border-color: var(--color-primary-light);
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.type-card.active {
  border-color: var(--color-primary);
  background: rgba(168, 85, 247, 0.06);
}

.type-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, rgba(168, 85, 247, 0.08), transparent 70%);
  pointer-events: none;
}

.type-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.type-name {
  font-weight: 700;
  color: var(--text-primary);
  font-size: var(--text-sm);
}

/* Upload */
.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.upload-item {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.upload-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.upload-remove:hover {
  background: rgba(239, 68, 68, 0.8);
}

.upload-remove svg {
  width: 12px;
  height: 12px;
  color: white;
}

.upload-trigger {
  width: 96px;
  height: 96px;
  border: 2px dashed var(--border-default);
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all var(--transition-fast);
  background: var(--bg-secondary);
}

.upload-trigger:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(168, 85, 247, 0.04);
}

.upload-trigger svg {
  width: 22px;
  height: 22px;
}

.upload-trigger span {
  font-size: var(--text-xs);
  font-weight: 600;
}

/* Submit */
.form-actions {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}

.submit-btn {
  min-width: 180px;
  height: 44px;
  border-radius: var(--radius-full);
  font-weight: 700;
  font-size: var(--text-base);
  box-shadow: var(--shadow-glow-purple);
}

/* Responsive */
@media (max-width: 768px) {
  .type-cards {
    grid-template-columns: 1fr;
  }

  .upload-list {
    justify-content: center;
  }
}
</style>
