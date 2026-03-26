<template>
  <div id="addPictureBatchPage">
    <h2 style="font-size: 22px; margin: 15px 5px">批量创建</h2>
    <!--    表单-->
    <div style="margin: 15px 5px">
      <a-form name="basic" layout="vertical" :model="formData" @finish="handSubmit">
        <a-form-item name="searchText" label="关键词：">
          <a-input v-model:value="formData.searchText" placeholder="请输入关键词" allow-clear />
        </a-form-item>
        <a-form-item name="count" label="抓取数量：">
          <a-input-number
            v-model:value="formData.count"
            placeholder="请输入数量"
            style="min-width: 180px"
            allow-clear
            :min="1"
            :max="30"
          />
        </a-form-item>
        <a-form-item name="namePrefix" label="名称前缀：">
          <a-input
            v-model:value="formData.namePrefix"
            placeholder="请输入名称前缀(会自动补充序号)"
            allow-clear
          />
        </a-form-item>
        <a-form-item>
          <a-button
            :loading="loading"
            type="primary"
            html-type="submit"
            style="width: 100%; margin-bottom: 60px"
          >
            批量提交
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureByBatchUsingPost } from '@/api/pictureController.ts'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()

const loading = ref<boolean>(false)

const formData = reactive<API.PictureUploadByBatchRequest>({ count: 10 })

const handSubmit = async (value: any) => {
  try {
    loading.value = true
    const res = await uploadPictureByBatchUsingPost({ ...formData })
    if (res.data.code === 0 && res.data.data) {
      message.success(`创建成功,共${res.data.data}条`)
      router.push(`/`)
    } else {
      message.error('创建失败,' + res.data.message)
    }
    loading.value = false
  } catch (error) {
    console.log(error)
  }
}
</script>

<style scoped>
#addPictureBatchPage {
  width: 720px;
  margin: 0 auto;
}
</style>
