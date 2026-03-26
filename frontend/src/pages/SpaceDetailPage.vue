<template>
  <div id="spaceDetailPage">
    <a-flex justify="space-between">
      <h2 class="title">{{ space.spaceName }} ({{ SPACE_TYPE_MAP[space.spaceType] }})</h2>
      <a-space size="middle">
        <a-button v-if="canUploadPicture" type="primary" :href="`/add_picture?spaceId=${id}`"
          >+创建图片</a-button
        >
        <a-button
          v-if="canManageSpaceUsers"
          :icon="h(TeamOutlined)"
          :href="`/spaceUserManage/${id}`"
          type="primary"
          ghost
        >
          成员管理
        </a-button>
        <a-button
          v-if="canManageSpaceUsers"
          :icon="h(BarChartOutlined)"
          :href="`/space_analyze?spaceId=${id}`"
          type="primary"
          ghost
          >空间分析</a-button
        >
        <a-button v-if="canEditPicture" :icon="h(EditOutlined)" @click="doBatchEdit"
          >批量编辑图片</a-button
        >
        <a-tooltip
          :title="`当前占用空间${formatSize(space.totalSize)}/${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :percent="
              space.totalSize && space.maxSize
                ? parseFloat(((space.totalSize * 100) / space.maxSize).toFixed(1))
                : 0
            "
            :size="40"
          />
        </a-tooltip>
      </a-space>
    </a-flex>
    <!--    搜索表单-->
    <div style="margin-bottom: 16px" />
    <PictureSearchForm :onSearch="onSearch" ref="doResetPictureSearchFormRef" />

    <!--    按照颜色搜图-->
    <a-form-item label="按颜色搜索">
      <color-picker format="hex" @pureColorChange="onColorChange" />
    </a-form-item>

    <!--图片列表-->
    <PictureList
      :canEdit="canEditPicture"
      :canDelete="canDeletePicture"
      :dataList="dataList"
      :loading="loading"
      :show-op="true"
      :on-reload="
        () => {
          fetchData()
        }
      "
    />
    <!--分页-->
    <a-pagination
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
      style="text-align: center ;margin-bottom: 10px"
    />

    <BatchEditPicture
      ref="batchEditPictureModalRef"
      :spaceId="id"
      :pictureList="dataList"
      :onSuccess="onBatchEditPictureSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, h, watch, computed } from 'vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import {
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { formatSize } from '@/utils'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import BatchEditPicture from '@/components/BatchEditPicture.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import { EditOutlined, BarChartOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_MAP } from '../constants/space.ts'

interface Props {
  // 接收route中的spaceId
  id: number | string
}

const props = defineProps<Props>()
const space = ref<API.SpaceVO>({})

//定义权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (space.value.permissionList ?? []).includes(permission)
  })
}

const canManageSpaceUsers = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 获取空间详情
const fetchDetailSpace = async () => {
  try {
    const id = props.id
    if (id) {
      const res = await getSpaceVoByIdUsingGet({ id })
      if (res.data.code === 0) {
        space.value = res.data.data
      } else {
        message.error('获取空间详情失败')
      }
    }
  } catch (error: any) {
    message.error(error.message)
  }
}
onMounted(() => {
  fetchDetailSpace()
})

// 获取图片
//定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)
// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1, //当前页码
  pageSize: 12,
  sortOrder: 'descend',
  sortField: 'createTime',
})
// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    //dataList.value是当前页面的图片数据
    dataList.value = res.data.data.records ?? []
    total.value = Number(res.data.data.total ?? 0)
  } else {
    message.error('获取数据失败' + res.data.message)
  }
  loading.value = false
}
// 页面加载时获取一次数据
onMounted(() => {
  fetchData()
})

//分页器
const onPageChange = (page: number, pageSize: number) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

// 搜索条件
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  fetchData()
}

// 按颜色搜索
const onColorChange = async (color: string) => {
  loading.value = true
  const res = await searchPictureByColorUsingPost({ picColor: color, spaceId: props.id })
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data ?? []
    //dataList.value是当前页面的图片数据
    dataList.value = data
    total.value = Number(data.length)
  } else {
    message.error('按颜色搜图出错' + res.data.message)
  }
  loading.value = false
}

// 批量编辑
const batchEditPictureModalRef = ref()
const doResetPictureSearchFormRef = ref()

// 批量编辑图片成功
const onBatchEditPictureSuccess = () => {
  if (doResetPictureSearchFormRef.value) doResetPictureSearchFormRef.value.doClear()
  fetchData()
}

// 打开编辑弹窗
const doBatchEdit = () => {
  if (batchEditPictureModalRef.value) batchEditPictureModalRef.value.openModal()
}

// 空间ID发生改变时必须重新获取数据
watch(
  () => props.id,
  () => {
    fetchDetailSpace()
    fetchData()
  },
)
</script>

<style scoped lang="less">
#spaceDetailPage {
  margin-top: 16px;
}

.title {
  display: flex;
  align-items: center;
  height: 40px; // 设置与右侧按钮/进度条相同的高度
  margin: 0;
}
</style>
