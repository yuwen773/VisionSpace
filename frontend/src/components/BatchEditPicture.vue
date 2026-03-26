<template>
  <div id="batchEditPicture">
    <div>
      <a-modal v-model:open="visable" title="批量编辑图片" :footer="false" @cancel="closeModal">
        <a-typography-paragraph type="secondary"> 对当前页面图片生效 </a-typography-paragraph>
        <!--    批量创建表单-->
        <a-form name="basic" layout="vertical" :model="formData" @finish="handSubmit">
          <a-form-item name="category" label="分类：">
            <a-auto-complete
              v-model:value="formData.category"
              placeholder="请输入分类"
              :options="categoryOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item name="tags" label="标签：">
            <a-select
              v-model:value="formData.tags"
              placeholder="请输入标签"
              mode="tags"
              :options="tagOptions"
              allow-clear
            />
          </a-form-item>
          <a-form-item name="nameRule" label="命名规则">
            <a-input
              v-model:value="formData.nameRule"
              placeholder="请输入命名规则，输入{序号}可动态生成"
              allow-clear
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" html-type="submit" style="width: 100%; margin-bottom: 60px"
              >提交</a-button
            >
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  editPictureByBatchUsingPost,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { useRoute } from 'vue-router'

interface Props {
  pictureList: API.PictureVO[]
  spaceId: number | string
  onSuccess: () => void
}

const props = withDefaults(defineProps<Props>(), {})

const visable = ref<boolean>(false)

// 打开弹窗
const openModal = () => {
  visable.value = true
}
// 关闭弹窗
const closeModal = () => {
  visable.value = false
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

const route = useRoute()

const picture = ref<API.PictureVO>()

// 当跳转到编辑图片的时候，getOldPicture通过图片id获取图片的相关编辑信息，放到formData，再：v-model绑定输入框
const formData = reactive<API.PictureEditByBatchRequest>({
  category: '',
  tags: [],
  nameRule: '',
})

const handSubmit = async (value: any) => {
  if (!props.pictureList) {
    alert('未选择批量操作的图片，请重新操作')
    return
  }
  const res = await editPictureByBatchUsingPost({
    ...value,
    spaceId: props.spaceId,
    pictureIdList: props.pictureList?.map((picture) => picture.id) ?? [],
  })
  if (res.data.code === 0 && res.data.data) {
    message.success('批量操作成功')
    closeModal()
    props.onSuccess?.()
  } else {
    message.error('批量操作失败,' + res.data.message)
  }
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
</script>

<style scoped></style>
