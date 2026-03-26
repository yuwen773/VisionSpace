<template>
  <div class="space-user-analyze">
    <a-card title="空间图片用户分析">
      <v-chart :option="options" style="height: 320px; max-width: 100%" :loading="loading" />
      <template #extra>
        <a-space>
          <a-segmented v-model:value="timeDimension" :options="timeDimensionOptions" />
          <a-input-search placeholder="请输入userId" enter-button="搜索用户" @search="doSearch" />
        </a-space>
      </template>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceUserAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

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

//时间维度
const timeDimension = ref<'day' | 'week' | 'month'>('day')

//分段选择器选项
const timeDimensionOptions = [
  {
    label: '日',
    value: 'day',
  },
  {
    label: '周',
    value: 'week',
  },
  {
    label: '月',
    value: 'month',
  },
]

//用户id选项
const userId = ref<string>()

const doSearch = (value: string) => {
  userId.value = value
}

// 图标数据
const dataList = ref<API.SpaceUserAnalyzeResponse>()

//获取数据
const fetchData = async () => {
  loading.value = true
  const res = await getSpaceUserAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    timeDimension: timeDimension.value,
    userId: userId.value,
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
  const periods = dataList.value?.map((item) => item.period) // 时间区间
  const counts = dataList.value?.map((item) => item.count) // 上传数量

  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: periods, name: '时间区间' },
    yAxis: { type: 'value', name: '上传数量' },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: true, // 平滑折线
        emphasis: {
          focus: 'series',
        },
      },
    ],
  }
})
</script>

<style scoped>
.space-user-analyze {
  margin-bottom: 20px;
}
</style>
