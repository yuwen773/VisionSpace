<template>
  <!--    弹窗组件-->
  <a-modal
    class="image-out-painting"
    v-model:open="visible"
    title="AI 扩图"
    :footer="false"
    @cancel="closeModal"
  >
    <a-row :gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" :alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          :alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-flex justify="center" gap="16">
      <a-button type="primary" ghost @click="createTask" :loading="Boolean(taskId)"
        >AI 扩图</a-button
      >
      <a-button v-if="resultImageUrl" type="primary" @click="handleUpload" :loading="uploadLoading"
        >应用结果</a-button
      >
    </a-flex>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
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
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

const uploadLoading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async () => {
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
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    }
  } catch (err: any) {
    message.error('图片上传失败：', err.message)
  }
  uploadLoading.value = false
}

// ai扩图
const resultImageUrl = ref<string>()
//保存任务ID
const taskId = ref<string>()

const createTask = async () => {
  if (!props.picture?.id) {
    alert('请上传所需扩图图片')
    return
  }
  const res = await createPictureOutPaintingTaskUsingPost({
    pictureId: props.picture.id,
    //根据需要设置扩图参数
    parameters: {
      xScale: 2,
      yScale: 2,
    },
  })
  if (res.data.code === 0 && res.data.data) {
    message.success('扩图任务开始，请耐心等待，不要退出界面')
    taskId.value = res.data.data.output?.taskId
    // 开启轮询
    startPolling()
  } else {
    message.error('扩图任务失败' + res.data.message)
  }
}

// 轮询定时器
let pollingTimer: NodeJS.Timeout = null

const startPolling = () => {
  if (!taskId.value) {
    return
  }
  pollingTimer = setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({
        taskId: taskId.value,
      })
      if (res.data.code === 0 && res.data.data) {
        const taskResult = res.data.data.output
        if (taskResult?.taskStatus === 'SUCCEEDED') {
          message.success('扩图任务成功')
          resultImageUrl.value = taskResult.outputImageUrl
          // 清理轮询
          clearPolling()
        } else if (taskResult?.taskStatus === 'FAILED') {
          message.error('扩图任务失败:' + taskResult.message)
          // 清理轮询
          clearPolling()
        }
      } else {
        message.error('扩图任务失败' + res.data.message)
        // 清理轮询
        clearPolling()
      }
    } catch (err: any) {
      message.error('轮询失败' + err.message)
      // 清理轮询
      clearPolling()
    }
  }, 3000)
}
// 设置了定时器一定要清理
const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
    taskId.value = ''
  }
}
</script>

<style lang="less">
.image-out-painting {
  text-align: center;
}
</style>
