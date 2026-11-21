<template>
  <div class="picture-upload">
    <a-upload
      list-type="picture-card"
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
      class="ap-uploader"
    >
      <div class="ap-uploader-empty">
        <span class="ap-uploader-mark" :class="{ 'is-loading': loading }" aria-hidden="true">
          <LoadingOutlined v-if="loading" class="animate-spin" />
          <Camera v-else :size="30" :stroke-width="1.3" />
        </span>
        <span class="ap-uploader-main">{{ loading ? '正在上传' : '拖入图片，或点击选择' }}</span>
        <span class="ap-uploader-sub aw-mono">
          {{ loading ? 'UPLOADING — 请稍候' : 'JPG / PNG · ≤ 2 MB' }}
        </span>
      </div>
    </a-upload>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { LoadingOutlined } from '@ant-design/icons-vue'
import { Camera } from 'lucide-vue-next'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { toId } from '@/utils'

interface Props {
  picture?: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  spaceId?: number | string
}

const props = defineProps<Props>()

const loading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    const spaceId = toId(props.spaceId)
    if (spaceId !== undefined) {
      params.spaceId = spaceId
    }
    const res = await uploadPictureUsingPost({ pictureUploadRequest: params }, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
    } else {
      message.error(res.data.message || '上传失败')
    }
  } catch (err: any) {
    message.error('图片上传失败：' + err.message)
  }
  loading.value = false
}

// 上传图片前的校验
const beforeUpload = (file: NonNullable<UploadProps['fileList']>[number]) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('不支持上传该格式文件')
  }
  const isLt2M = (file.size ?? 0) / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
  }
  return isJpgOrPng && isLt2M
}
</script>

<style scoped lang="less">
.picture-upload {
  width: 100%;
  height: 100%;
  display: flex;
}

.ap-uploader {
  width: 100% !important;
  height: 100% !important;

  :deep(.ant-upload-wrapper) {
    width: 100%;
    height: 100%;
  }

  :deep(.ant-upload.ant-upload-select) {
    width: 100% !important;
    height: 100% !important;
    min-height: clamp(300px, 44vh, 520px);
    margin: 0 !important;
    border: 0 !important;
    border-radius: 0 !important;
    background: transparent !important;
    overflow: visible;
    transition: none;

    &:hover {
      border: 0 !important;
      background: transparent !important;
      box-shadow: none !important;
      transform: none !important;
    }
  }
}

.ap-uploader-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  width: 100%;
  height: 100%;
  min-height: clamp(300px, 44vh, 520px);
  padding: 24px;
  text-align: center;
  cursor: pointer;
}

.ap-uploader-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  margin-bottom: 4px;
  border: 1px solid var(--aw-line-2);
  border-radius: 50%;
  color: var(--aw-ink-2);
  transition:
    color 0.4s var(--aw-ease),
    border-color 0.4s var(--aw-ease),
    transform 0.5s var(--aw-ease),
    background-color 0.4s var(--aw-ease);
}

.ap-uploader:hover .ap-uploader-mark {
  color: var(--aw-accent);
  border-color: var(--aw-accent);
  background: var(--aw-accent-soft);
  transform: translateY(-2px);
}

.ap-uploader-mark.is-loading {
  color: var(--aw-accent);
  border-color: var(--aw-accent);
}

.ap-uploader-main {
  font-family: var(--aw-sans);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--aw-ink);
}

.ap-uploader-sub {
  color: var(--aw-ink-3);
}

@media (max-width: 480px) {
  .ap-uploader-empty,
  .ap-uploader :deep(.ant-upload.ant-upload-select) {
    min-height: 260px;
  }
}
</style>
