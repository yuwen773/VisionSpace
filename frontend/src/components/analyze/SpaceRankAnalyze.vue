<template>
  <div class="space-rank-analyze">
    <a-card title="空间图片分类分析">
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
import { getSpaceRankAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const dataList = ref<API.Space[]>()

//获取数据
const fetchData = async () => {
  loading.value = true
  const res = await getSpaceRankAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    topN: 10,
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
  const spaceNames = dataList.value?.map((item) => item.spaceName)
  const usageData = dataList.value?.map((item) => (item.totalSize / (1024 * 1024)).toFixed(2)) // 转为 MB

  return {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: spaceNames,
    },
    yAxis: {
      type: 'value',
      name: '空间使用量 (MB)',
    },
    series: [
      {
        name: '空间使用量 (MB)',
        type: 'bar',
        data: usageData,
        itemStyle: {
          color: '#5470C6', // 自定义柱状图颜色
        },
      },
    ],
  }
})
</script>

<style scoped>
.space-rank-analyze {
  margin-bottom: 20px;
}
</style>
