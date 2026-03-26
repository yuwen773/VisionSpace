<template>
  <div class="url-picture-upload">
    <a-input-group compact>
      <a-input
        v-model:value="fileUrl"
        style="width: calc(100% - 100px)"
        placeholder="请输入图片地址"
      />
      <a-button type="primary" :loading="loading" style="width: 100px" @click="handleUpload"
        >提交</a-button
      >
    </a-input-group>
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByUrlUsingPost } from '@/api/pictureController.ts'

interface Props {
  picture: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  spaceId?: string
}

const props = defineProps<Props>()
const fileUrl = ref<string>()
const loading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async () => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = { fileUrl: fileUrl.value }
    if (props.picture) {
      params.id = props.picture.id
    }
    if (props.spaceId) {
      params.spaceId = props.spaceId
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
    }
  } catch (err: any) {
    message.error('图片上传失败：', err.message)
  }
  loading.value = false
}
</script>
<style scoped lang="less">
.url-picture-upload {
  margin-bottom: 16px;
}

.url-picture-upload img {
  max-height: 480px;
}

.url-picture-upload .img-wrapper {
  text-align: center;
  margin-top: 16px;
}
</style>
