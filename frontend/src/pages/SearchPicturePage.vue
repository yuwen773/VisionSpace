<template>
  <div id="searchPicturePage">
    <h2 style="margin-bottom: 16px">以图搜图</h2>
    <h3 style="margin-bottom: 16px">原图</h3>
    <a-card hoverable style="width: 240px">
      <template #cover>
        <img :alt="picture.name" :src="picture.url" style="height: 180px; object-fit: cover" />
      </template>
    </a-card>
    <h3 style="margin: 16px 0">识图结果</h3>

    <!--图片列表-->
    <a-list
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :loading="loading"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <a :href="picture.fromUrl" target="_blank">
            <!--单张图片-->
            <a-card hoverable style="width: 240px">
              <template #cover>
                <img
                  :alt="picture.name"
                  :src="picture.thumbUrl"
                  style="height: 180px; object-fit: cover"
                />
              </template>
            </a-card>
          </a>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import {
  getPictureVoByIdUsingGet,
  searchPictureByPictureUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const pictureId = computed(() => {
  return route.query?.pictureId
})

const picture = ref<API.PictureVO>({})

// 获取老数据
const fetchDetailPicture = async () => {
  try {
    const id = pictureId.value
    if (id) {
      const res = await getPictureVoByIdUsingGet({ id })
      if (res.data.code === 0 && res.data.data) {
        picture.value = res.data.data
      } else {
        message.error('获取图片详情失败')
      }
    }
  } catch (error: any) {
    message.error(error.message)
  }
}
onMounted(() => {
  fetchDetailPicture()
})

const loading = ref<boolean>(true)
//识图结果
const dataList = ref<API.ImageSearchResult[]>([])
const fetchResultData = async () => {
  loading.value = true
  try {
    const res = await searchPictureByPictureUsingPost({
      pictureId: pictureId.value,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
    } else {
      message.error('以图搜图失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('以图搜图失败，' + e.message)
  }
  loading.value = false
}
onMounted(() => {
  fetchResultData()
})
</script>

<style scoped>
#pictureDetailPage {
  margin-top: 16px;
}
</style>
