<template>
  <div class="picture-upload">
    <a-upload
      list-type="picture-card"
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
      class="pop-upload"
    >
      <div v-if="picture?.url" class="uploaded-image">
        <img :src="picture?.url" alt="avatar" />
        <div class="image-overlay">
          <span class="overlay-text"><Camera :size="20" style="vertical-align: middle; margin-right: 4px;" /> 点击更换</span>
        </div>
      </div>
      <div v-else class="upload-placeholder">
        <div class="upload-icon">
          <loading-outlined v-if="loading" class="animate-spin" />
          <Camera v-else :size="40" class="icon-plus" />
        </div>
        <div class="upload-text">
          <span class="text-main">点击或拖拽上传</span>
          <span class="text-sub">支持 JPG、PNG 格式</span>
        </div>
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

interface Props {
  picture: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  spaceId?: string
}

const props = defineProps<Props>()

const loading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    if (props.spaceId) {
      params.spaceId = props.spaceId
    }
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('🎉 图片上传成功')
      props.onSuccess?.(res.data.data)
    } else {
      message.error('😅 ' + (res.data.message || '上传失败'))
    }
  } catch (err: any) {
    message.error('😅 图片上传失败：' + err.message)
  }
  loading.value = false
}

// 上传图片前的校验
const beforeUpload = (file: UploadProps['fileList'][number]) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJpgOrPng) {
    message.error('😅 不支持上传该格式文件')
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('😅 图片大小不能超过2MB')
  }
  return isJpgOrPng && isLt2M
}
</script>

<style scoped lang="less">
.picture-upload {
  width: 100%;
}

.pop-upload {
  width: 100% !important;
  height: 100% !important;

  :deep(.ant-upload) {
    width: 100% !important;
    height: 100% !important;
    min-height: 280px;
    min-width: 280px;
    max-width: 500px;
    border: 3px dashed var(--border-bold);
    border-radius: var(--radius-2xl);
    background: var(--bg-tertiary);
    transition: all var(--transition-bounce);

    &:hover {
      border-color: var(--color-sunshine);
      background: var(--bg-hover);
      transform: translate(-2px, -2px);
      box-shadow: var(--shadow-pop);
    }
  }
}

/* ========== 已上传的图片 ========== */
.uploaded-image {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 280px;
  overflow: hidden;
  border-radius: var(--radius-xl);

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  &:hover .image-overlay {
    opacity: 1;
  }
}

.image-overlay {
  position: absolute;
  inset: 0;
  background: rgba(45, 52, 54, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-smooth);
}

.overlay-text {
  color: white;
  font-weight: 700;
  font-size: var(--text-lg);
}

/* ========== 上传占位符 ========== */
.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  padding: var(--space-8);
  height: 100%;
  min-height: 280px;
}

.upload-icon {
  color: var(--color-coral);

  .icon-plus {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.upload-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  text-align: center;
}

.text-main {
  font-weight: 700;
  color: var(--text-primary);
  font-size: var(--text-lg);
}

.text-sub {
  font-size: var(--text-sm);
  color: var(--text-tertiary);
  font-weight: 600;
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .pop-upload :deep(.ant-upload) {
    min-height: 220px;
    min-width: 220px;
  }

  .uploaded-image,
  .upload-placeholder {
    min-height: 220px;
  }

  .upload-icon {
    font-size: var(--text-4xl);
  }

  .text-main {
    font-size: var(--text-base);
  }
}
</style>
