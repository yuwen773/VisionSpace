<template>
  <div class="url-picture-upload">
    <div class="ap-url-inner">
      <span class="ap-url-mark" aria-hidden="true">
        <Link2 :size="28" :stroke-width="1.3" />
      </span>

      <p class="ap-url-hint">
        粘贴一个图片地址，VisionSpace 会替你把文件取回来，<br />
        之后的命名与归档和本地文件一样。
      </p>

      <div class="ap-url-row">
        <label class="ap-url-label aw-mono" for="ap-url-input">IMAGE URL</label>
        <div class="ap-url-field">
          <input
            id="ap-url-input"
            v-model="fileUrl"
            type="text"
            class="ap-url-input"
            placeholder="https://example.com/image.jpg"
            autocomplete="off"
            @keydown.enter.prevent="handleUpload"
          />
          <button
            type="button"
            class="aw-btn aw-btn--primary ap-url-submit"
            :disabled="!fileUrl || loading"
            @click="handleUpload"
          >
            <span>{{ loading ? '导入中' : '导入' }}</span>
            <ArrowRight v-if="!loading" :size="16" :stroke-width="1.8" />
          </button>
        </div>
      </div>

      <p v-if="error" class="ap-url-error" role="alert">{{ error }}</p>

      <p class="ap-url-note aw-mono">DIRECT LINK · JPG / PNG · ≤ 2 MB</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowRight, Link2 } from 'lucide-vue-next'
import { uploadPictureByUrlUsingPost } from '@/api/pictureController.ts'
import { toId } from '@/utils'

interface Props {
  picture?: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  spaceId?: string | number
}

const props = defineProps<Props>()
const fileUrl = ref<string>()
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
    const params: API.PictureUploadRequest = { fileUrl: fileUrl.value.trim() }
    if (props.picture?.id) {
      params.id = props.picture.id
    }
    const spaceId = toId(props.spaceId)
    if (spaceId !== undefined) {
      params.spaceId = spaceId
    }

    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
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
</script>

<style scoped lang="less">
.url-picture-upload {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ap-url-inner {
  width: 100%;
  max-width: 560px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
  text-align: center;
}

.ap-url-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  border: 1px solid var(--aw-line-2);
  border-radius: 50%;
  color: var(--aw-ink-2);
  transition:
    color 0.4s var(--aw-ease),
    border-color 0.4s var(--aw-ease),
    transform 0.5s var(--aw-ease),
    background-color 0.4s var(--aw-ease);
}

.url-picture-upload:hover .ap-url-mark {
  color: var(--aw-accent);
  border-color: var(--aw-accent);
  background: var(--aw-accent-soft);
  transform: translateY(-2px);
}

.ap-url-hint {
  margin: 0;
  font-size: 14px;
  line-height: 1.85;
  color: var(--aw-ink-2);
  text-wrap: pretty;
}

.ap-url-row {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
  text-align: left;
}

.ap-url-label {
  font-size: 9.5px;
  letter-spacing: 0.18em;
}

.ap-url-field {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  border-bottom: 1px solid var(--aw-line);
  transition: border-color 0.4s var(--aw-ease);
}

.ap-url-field:focus-within {
  border-bottom-color: var(--aw-accent);
}

.ap-url-input {
  flex: 1;
  min-width: 0;
  padding: 11px 0;
  border: 0;
  background: transparent;
  color: var(--aw-ink);
  font-family: var(--aw-mono);
  font-size: 13px;
  letter-spacing: 0.02em;
  outline: none;
}

.ap-url-input::placeholder {
  color: var(--aw-ink-3);
  font-family: var(--aw-mono);
}

.ap-url-submit {
  flex-shrink: 0;
  margin-bottom: 6px;
  padding: 10px 20px;
  font-size: 13px;
}

.ap-url-error {
  width: 100%;
  margin: 0;
  text-align: left;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--aw-danger);
}

.ap-url-note {
  margin: 0;
  font-size: 9.5px;
  letter-spacing: 0.16em;
}

@media (max-width: 480px) {
  .ap-url-field {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    border-bottom: 0;
  }

  .ap-url-input {
    border-bottom: 1px solid var(--aw-line);
  }

  .ap-url-submit {
    margin-bottom: 0;
  }
}
</style>
