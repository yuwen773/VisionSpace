<template>
  <div id="pictureSearchForm">
    <!--    搜索表单-->
    <a-form layout="inline" :model="searchParams" @finish="doSearch" name="searchForm">
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="category" label="分类：">
        <a-auto-complete
          v-model:value="searchParams.category"
          placeholder="请输入分类"
          :options="categoryOptions"
          allow-clear
          style="width: 210px"
        />
      </a-form-item>
      <a-form-item name="tags" label="标签：">
        <a-select
          v-model:value="searchParams.tags"
          placeholder="请输入标签"
          mode="tags"
          :options="tagOptions"
          allow-clear
          style="width: 210px"
        />
      </a-form-item>
      <a-form-item label="日期" name="dataRange">
        <a-range-picker
          style="width: 400px"
          show-time
          :placeholder="['编辑开始时间', '编辑结束时间']"
          v-model:aria-valuemax="dataRange"
          format="YYYY/MM/DD HH:mm:ss"
          :presets="rangePresets"
          @change="onRangeChange"
        />
      </a-form-item>
      <a-form-item name="name" label="名称：">
        <a-input v-model:value="searchParams.name" placeholder="请输入名称" allow-clear />
      </a-form-item>
      <a-form-item name="introduction" label="简介：">
        <a-input v-model:value="searchParams.introduction" placeholder="请输入简介" allow-clear />
      </a-form-item>
      <a-form-item name="picWidth" label="宽度：">
        <a-input-number v-model:value="searchParams.picWidth" />
      </a-form-item>
      <a-form-item name="picHeight" label="高度：">
        <a-input-number v-model:value="searchParams.picHeight" />
      </a-form-item>
      <a-form-item name="picFormat" label="格式：">
        <a-input v-model:value="searchParams.picFormat" placeholder="请输入格式" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="reset" style="width: 70px" @click="doClear"
            >重置</a-button
          >
          <a-button type="primary" html-type="submit" style="width: 70px">搜索</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>
<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { listPictureTagCategoryUsingGet, listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import type { RangeValue } from 'ant-design-vue/es/vc-picker/interface'


interface Props {
  onSearch?: (searchParams?: API.PictureQueryRequest) => void
}

const props = defineProps<Props>()

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({})

const doSearch = () => {
  props.onSearch?.(searchParams)
}

const categoryOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])
// 获取分类和标签选项
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
    tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
  } else {
    message.error('获取标签分类列表失败')
  }
}
onMounted(() => {
  getTagCategoryOptions()
})

const dataRange = ref<[]>([])
// 日期范围更改触发
const onRangeChange = (dates: RangeValue, dateStrings: string[]) => {
  searchParams.startEditTime = dates[0].toDate()
  searchParams.endEditTime = dates[1].toDate()
}

const rangePresets = ref([
  { label: '过去7天', value: [dayjs().add(-7, 'd'), dayjs()] },
  { label: '过去14天', value: [dayjs().add(-14, 'd'), dayjs()] },
  { label: '过去30天', value: [dayjs().add(-30, 'd'), dayjs()] },
  { label: '过去90天', value: [dayjs().add(-90, 'd'), dayjs()] },
])

// 重置表单
const doClear = () => {
  // 取消所有对象的值
  Object.keys(searchParams).forEach((key) => {
    searchParams[key] = undefined
  })
  // 日期筛选项单独置空
  dataRange.value = []
  props.onSearch?.(searchParams)
}

defineExpose({
  doClear
})
</script>

<style lang="less" scoped>
#pictureSearchForm {
  .ant-form-item {
    margin-bottom: 10px;
  }
}
</style>
