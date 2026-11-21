<template>
  <RecordSection
    index="05"
    title="意见反馈"
    label="FEEDBACK"
    lede="你遇到的问题和建议是我们改进的依据。选择一个类型，写下发生了什么；最多可以附五张截图。"
  >
    <form class="feedback-form" novalidate @submit.prevent="doFeedbackSubmit">
      <!-- 类型 -->
      <fieldset class="type-fieldset">
        <legend class="uc-label">
          <span>反馈类型</span>
          <span class="mono">TYPE</span>
        </legend>
        <div class="type-cards">
          <label
            v-for="(item, index) in feedbackTypeOptions"
            :key="item.value"
            class="type-card"
            :class="{ 'is-active': feedbackForm.type === item.value }"
          >
            <input
              v-model="feedbackForm.type"
              class="type-input sr-only"
              type="radio"
              name="uc-feedback-type"
              :value="item.value"
            />
            <span class="type-index mono">{{ String(index + 1).padStart(2, '0') }}</span>
            <component :is="item.icon" :size="22" :stroke-width="1.5" class="type-icon" />
            <span class="type-name">{{ item.label }}</span>
            <Check
              v-if="feedbackForm.type === item.value"
              :size="15"
              :stroke-width="1.8"
              class="type-check"
              aria-hidden="true"
            />
          </label>
        </div>
      </fieldset>

      <!-- 标题 -->
      <div class="uc-form-row">
        <label class="uc-label" for="uc-feedback-title">
          <span>标题</span>
          <span class="mono">{{ feedbackForm.title.length }} / 50</span>
        </label>
        <input
          id="uc-feedback-title"
          v-model="feedbackForm.title"
          class="uc-field"
          type="text"
          maxlength="50"
          placeholder="请简要描述你的问题"
          @input="formError = ''"
        />
      </div>

      <!-- 描述 -->
      <div class="uc-form-row">
        <label class="uc-label" for="uc-feedback-content">
          <span>详细描述</span>
          <span class="mono">{{ feedbackForm.content.length }} / 2000</span>
        </label>
        <textarea
          id="uc-feedback-content"
          v-model="feedbackForm.content"
          class="uc-field"
          rows="6"
          maxlength="2000"
          placeholder="请详细描述你的问题或建议……"
          @input="formError = ''"
        ></textarea>
      </div>

      <!-- 附件 -->
      <div class="uc-form-row">
        <span class="uc-label">
          <span>附件截图</span>
          <span class="mono">{{ feedbackForm.attachments.length }} / 5 · 单张 ≤ 5MB</span>
        </span>
        <div class="upload-list">
          <div
            v-for="(attachment, index) in feedbackForm.attachments"
            :key="attachment.id ?? index"
            class="upload-item"
          >
            <img :src="contentSrc(attachment.url)" alt="反馈附件截图" />
            <button
              type="button"
              class="upload-remove"
              :aria-label="`移除第 ${index + 1} 张附件`"
              @click="removeFeedbackPicture(index)"
            >
              <X :size="12" :stroke-width="2.2" />
            </button>
          </div>
          <button
            v-if="feedbackForm.attachments.length < 5"
            type="button"
            class="upload-trigger"
            @click="triggerFeedbackUpload"
          >
            <Plus :size="20" :stroke-width="1.5" aria-hidden="true" />
            <span class="mono">ADD IMAGE</span>
          </button>
        </div>
        <input
          ref="feedbackFileInputRef"
          type="file"
          accept="image/*"
          multiple
          hidden
          @change="handleFeedbackFileChange"
        />
      </div>

      <p v-if="formError" class="uc-status uc-status--error" role="alert">{{ formError }}</p>

      <div class="uc-actions submit-row">
        <button type="submit" class="uc-btn uc-btn--solid" :disabled="feedbackSubmitting">
          {{ feedbackSubmitting ? '提交中…' : '提交反馈' }}
        </button>
        <span class="mono submit-note">FEEDBACK GOES TO THE VISIONSPACE TEAM</span>
      </div>
    </form>
  </RecordSection>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { Lightbulb, Flag, Ticket, Plus, X, Check } from 'lucide-vue-next'
import { addFeedbackUsingPost, uploadFeedbackAttachmentUsingPost } from '@/api/feedbackController'
import { attachmentIdsOf } from '@/api-custom/resourceProtection'
import { contentSrc } from '@/utils'
import RecordSection from '@/components/user-center/RecordSection.vue'

const feedbackTypeOptions = [
  { value: 1, label: '产品建议', icon: Lightbulb },
  { value: 2, label: '内容举报', icon: Flag },
  { value: 3, label: '工单支持', icon: Ticket },
]

const feedbackForm = ref({
  type: 1,
  title: '',
  content: '',
  // 附件只保存服务端登记的标识与受控入口：源站地址不进入表单
  attachments: [] as API.FeedbackAttachmentVO[],
})

const feedbackSubmitting = ref(false)
const formError = ref('')
const feedbackFileInputRef = ref<HTMLInputElement | null>(null)

const triggerFeedbackUpload = () => {
  feedbackFileInputRef.value?.click()
}

const handleFeedbackFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files) return

  const maxUploads = Math.min(files.length, 5 - feedbackForm.value.attachments.length)
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
          const res = await uploadFeedbackAttachmentUsingPost({}, file)
          // 上传接口返回附件标识与受控入口，不返回源站地址
          const attachment = res.data.data
          if (res.data.code === 0 && attachment?.id) {
            feedbackForm.value.attachments.push(attachment)
          } else {
            message.error(res.data.message || '图片上传失败')
          }
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : '图片上传失败'
          message.error(msg)
        }
      })(),
    )
  }

  await Promise.all(uploadPromises)
  ;(e.target as HTMLInputElement).value = ''
}

const removeFeedbackPicture = (index: number) => {
  feedbackForm.value.attachments.splice(index, 1)
}

const doFeedbackSubmit = async () => {
  if (!feedbackForm.value.title.trim()) {
    formError.value = '请填写标题，让团队知道这条反馈关于什么。'
    return
  }
  if (!feedbackForm.value.content.trim()) {
    formError.value = '请补充详细描述，方便我们复现或理解。'
    return
  }
  formError.value = ''

  feedbackSubmitting.value = true
  try {
    const res = await addFeedbackUsingPost({
      type: feedbackForm.value.type,
      title: feedbackForm.value.title,
      content: feedbackForm.value.content,
      // 附件标识原样提交：服务端按十进制字符串给出，转成 number 会丢精度，
      // 按类型过滤会让附件静默丢失
      attachmentIds: attachmentIdsOf(feedbackForm.value.attachments),
    })
    if (res.data.code === 0) {
      message.success('反馈提交成功，感谢你的意见')
      feedbackForm.value = { type: 1, title: '', content: '', attachments: [] }
    } else {
      formError.value = res.data.message || '提交失败，请稍后重试。'
    }
  } catch {
    formError.value = '提交失败，请稍后重试。'
  } finally {
    feedbackSubmitting.value = false
  }
}
</script>

<style scoped>
.feedback-form {
  max-width: 780px;
}

/* ============ 类型 ============ */
.type-fieldset {
  margin: 0 0 var(--space-6);
  padding: 0;
  border: none;
}

.type-fieldset legend {
  padding: 0;
  width: 100%;
}

.type-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.type-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 16px 16px;
  border: 1px solid var(--uc-line-strong);
  background: transparent;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast);
}

.type-card:hover {
  border-color: var(--uc-ink-3);
}

.type-card.is-active {
  border-color: var(--uc-accent);
  background: rgba(183, 162, 255, 0.06);
}

.is-light .type-card.is-active {
  background: rgba(124, 58, 237, 0.06);
}

.type-input:focus-visible + .type-index {
  outline: 2px solid var(--uc-accent);
  outline-offset: 3px;
}

.type-index {
  font-size: 9px;
  color: var(--uc-ink-3);
}

.type-card.is-active .type-index {
  color: var(--uc-accent);
}

.type-icon {
  color: var(--uc-ink-2);
}

.type-card.is-active .type-icon {
  color: var(--uc-accent);
}

.type-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--uc-ink);
}

.type-check {
  position: absolute;
  top: 14px;
  right: 14px;
  color: var(--uc-accent);
}

/* ============ 附件 ============ */
.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.upload-item {
  position: relative;
  width: 92px;
  height: 92px;
  border: 1px solid var(--uc-line);
  overflow: hidden;
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
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(5, 6, 12, 0.72);
  color: #fff;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.upload-remove:hover {
  background: rgba(185, 28, 28, 0.9);
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 92px;
  height: 92px;
  padding: 0;
  border: 1px dashed var(--uc-line-strong);
  background: transparent;
  color: var(--uc-ink-3);
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.upload-trigger:hover {
  border-color: var(--uc-accent);
  color: var(--uc-accent);
}

.upload-trigger .mono {
  font-size: 8px;
}

/* ============ 提交 ============ */
.submit-row {
  margin-top: var(--space-6);
}

.submit-note {
  font-size: 8px;
  letter-spacing: 0.14em;
}

@media (max-width: 640px) {
  .type-cards {
    grid-template-columns: minmax(0, 1fr);
  }

  .type-card {
    flex-direction: row;
    align-items: center;
    gap: 12px;
    padding: 14px;
  }

  .type-index {
    order: -1;
  }

  .type-name {
    flex: 1;
  }

  .type-check {
    position: static;
    margin-left: auto;
  }
}
</style>
