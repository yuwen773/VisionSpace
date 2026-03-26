<template>
  <div class="space-tag-analyze">
    <a-card title="空间图片标签分析">
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
import 'echarts-wordcloud'
import { computed, ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceTagAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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
const dataList = ref<API.SpaceTagAnalyzeResponse>()

//获取数据
const fetchData = async () => {
  loading.value = true
  const res = await getSpaceTagAnalyzeUsingPost({
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
  // 安全处理 dataList.value
  const dataArray = Array.isArray(dataList.value) ? dataList.value : []

  const tagData = dataArray
    .filter((item) => item && item.tag && item.count) // 过滤有效数据
    .map((item) => ({
      name: item.tag?.toString() || '', // 确保 name 是字符串
      value: Number(item.count) || 0, // 确保 value 是数字
    }))
    .filter((item) => item.value > 0) // 过滤掉值为0的数据

  // 如果没有数据，返回空图表配置
  if (tagData.length === 0) {
    return {
      tooltip: {
        trigger: 'item',
      },
      series: [
        {
          type: 'wordCloud',
          data: [],
        },
      ],
    }
  }

  // const tagData = dataList.value.map((item) => ({
  //   name: item.tag,
  //   value: item.count,
  // }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        // 安全处理 formatter
        if (!params) return ''
        return `${params.name || '未知'}: ${params.value || 0} 次`
      },
    },
    series: [
      {
        type: 'wordCloud',
        gridSize: 10,
        sizeRange: [12, 50],
        rotationRange: [-90, 90],
        rotationStep: 45, // 添加旋转步长
        shape: 'circle',
        width: '100%',
        height: '100%',
        textStyle: {
          color: () =>
            `rgb(${Math.round(Math.random() * 255)}, ${Math.round(
              Math.random() * 255,
            )}, ${Math.round(Math.random() * 255)})`,
        },
        emphasis: {
          focus: 'self',
          textStyle: {
            shadowBlur: 10,
            shadowColor: '#333',
          },
        },
        data: tagData,
      },
    ],
  }
})
</script>

<style scoped>
.space-tag-analyze {
  margin-bottom: 20px;
}
</style>
