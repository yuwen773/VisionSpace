<template>
  <div class="space-size-analyze">
    <a-card title="空间图片大小分析">
      <v-chart
        :option="options"
        style="height: 320px; max-width: 100%"
        :loading="loading"
      ></v-chart>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceSizeAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const dataList = ref<API.SpaceSizeAnalyzeResponse>()

//获取数据
const fetchData = async () => {
  loading.value = true
  const res = await getSpaceSizeAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
  })
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data ?? []
  } else {
    message.error('获取数据失败' + res.data.message)
  }
  loading.value = false
}
// 这时候不能只获取一次数据了，必须监听数据，在数据变化的时候即使更新页面
watchEffect(() => {
  fetchData()
})

// 图标选项
const options = computed(() => {
  const pieData = dataList.value?.map((item) => ({
    name: item.sizeRange,
    value: item.count,
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)',
    },
    legend: {
      top: 'bottom',
    },
    series: [
      {
        name: '图片大小',
        type: 'pie',
        radius: '50%',
        data: pieData,
      },
    ],
  }
})
</script>

<style scoped>
.space-size-analyze {
  margin-bottom: 20px;
}
</style>
