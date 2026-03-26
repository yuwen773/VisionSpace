<template>
  <div class="space-usage-analyze">
    <a-flex gap="middle">
      <a-card title="存储空间" style="width: 50%">
        <div style="height: 320px; text-align: center">
          <h3>
            {{ formatSize(data?.usedSize) }}/{{
              data?.maxSize ? formatSize(data?.maxSize) : '无限制'
            }}
          </h3>
          <a-progress type="dashboard" :percent="data?.sizeUsageRatio ?? 0"></a-progress>
        </div>
      </a-card>
      <a-card title="图片数量" style="width: 50%">
        <div style="height: 320px; text-align: center">
          <h3>{{ data?.usedCount }}/{{ data?.maxCount ?? '无限制' }}</h3>
          <a-progress type="dashboard" :percent="data?.sizeUsageRatio ?? 0"></a-progress>
        </div>
      </a-card>
    </a-flex>
  </div>
</template>

<script setup lang="ts">
import 'echarts'
import { ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceUsageAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { formatSize } from '../../utils'

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

//加载状态
const loading = ref<boolean>(false)

// 图标数据
const data = ref<API.SpaceUsageAnalyzeResponse>({})

//获取数据
const fetchData = async () => {
  loading.value = true
  const res = await getSpaceUsageAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
  })
  if (res.data.code === 0 && res.data.data) {
    data.value = res.data.data
  } else {
    message.error('获取数据失败' + res.data.message)
  }
  loading.value = false
}
// 这时候不能只获取一次数据了，必须监听数据，在数据变化的时候即使更新页面
watchEffect(() => {
  fetchData()
})
</script>

<style scoped>
.space-usage-analyze {
  margin-bottom: 20px;
}
</style>
