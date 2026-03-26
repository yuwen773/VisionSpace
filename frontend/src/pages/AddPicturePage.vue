<template>
  <div id="addPicturePage">
    <div class="page-header">
      <div class="header-content">
        <button @click="goBack" class="pop-btn outline back-btn">
          <span>←</span>
          <span>返回</span>
        </button>
        <div class="header-info">
          <h1 class="page-title">
            {{ route.query.id ? '✏️ 编辑图片' : '📸 上传图片' }}
          </h1>
          <p class="page-subtitle">📁 保存至空间: {{ spaceName }}</p>
        </div>
      </div>
    </div>

    <div class="upload-container">
      <div class="upload-main">
        <!-- 上传方式标签页 -->
        <div class="upload-card pop-card">
          <a-tabs v-model:activeKey="uploadType" class="upload-tabs">
            <a-tab-pane key="file">
              <template #tab>
                <span class="tab-label">
                  <span>☁️</span>
                  <span>本地上传</span>
                </span>
              </template>
              <PictureUpload :picture="picture" :spaceId="spaceId" :on-success="onSuccess" />
            </a-tab-pane>
            <a-tab-pane key="url">
              <template #tab>
                <span class="tab-label">
                  <span>🔗</span>
                  <span>URL 上传</span>
                </span>
              </template>
              <UrlPictureUpload :picture="picture" :spaceId="spaceId" :on-success="onSuccess" />
            </a-tab-pane>
          </a-tabs>
        </div>

        <!-- 图片预览和操作 -->
        <div v-if="picture" class="preview-section">
          <div class="preview-card pop-card">
            <div class="preview-image">
              <img :src="picture.url" :alt="picture.name" />
            </div>
            <div class="preview-actions">
              <button @click="doEditPicture" class="action-btn pop-btn outline">
                <span>✂️</span>
                <span>裁剪编辑</span>
              </button>
              <button @click="doImagePainting" class="action-btn pop-btn primary">
                <span>🎨</span>
                <span>AI 扩图</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 图片信息表单 -->
        <div v-if="picture" class="form-section">
          <div class="form-card pop-card">
            <div class="card-header">
              <span class="header-icon">📝</span>
              <h3 class="card-title">图片信息</h3>
            </div>
            <a-form
              name="pictureInfo"
              layout="vertical"
              :model="pictureForm"
              @finish="handSubmit"
            >
              <a-row :gutter="16">
                <a-col :span="24">
                  <a-form-item name="name" label="🏷️ 图片名称">
                    <a-input
                      v-model:value="pictureForm.name"
                      placeholder="请输入图片名称"
                      allow-clear
                      size="large"
                    />
                  </a-form-item>
                </a-col>

                <a-col :span="24">
                  <a-form-item name="introduction" label="📄 图片简介">
                    <a-textarea
                      v-model:value="pictureForm.introduction"
                      placeholder="请输入图片简介"
                      allow-clear
                      :auto-size="{ minRows: 3, maxRows: 6 }"
                    />
                  </a-form-item>
                </a-col>

                <a-col :xs="24" :sm="12">
                  <a-form-item name="category" label="📂 分类">
                    <a-auto-complete
                      v-model:value="pictureForm.category"
                      placeholder="请输入或选择分类"
                      :options="categoryOptions"
                      allow-clear
                      size="large"
                    >
                      <template #option="{ value, label }">
                        <span>{{ label }}</span>
                      </template>
                    </a-auto-complete>
                  </a-form-item>
                </a-col>

                <a-col :xs="24" :sm="12">
                  <a-form-item name="tags" label="🏷️ 标签">
                    <a-select
                      v-model:value="pictureForm.tags"
                      placeholder="请选择或输入标签"
                      mode="tags"
                      :options="tagOptions"
                      allow-clear
                      size="large"
                      :maxTagCount="3"
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item class="submit-form-item">
                <a-space :size="12">
                  <button html-type="submit" class="pop-btn primary submit-btn">
                    <span>✅</span>
                    <span>{{ route.query.id ? '保存修改' : '创建图片' }}</span>
                  </button>
                  <button @click="goBack" class="pop-btn outline">
                    <span>❌</span>
                    <span>取消</span>
                  </button>
                </a-space>
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
    </div>

    <!-- 图片编辑弹窗 -->
    <ImageCropper
      ref="imageCropperRef"
      :imageUrl="picture?.url"
      :picture="picture"
      :on-success="onCropperSuccess"
      :space="space"
      :spaceId="spaceId"
    />

    <!-- AI 扩图弹窗 -->
    <ImageOutPainting
      ref="imageOutPaintingRef"
      :picture="picture"
      :spaceId="spaceId"
      :onSuccess="onImageOutPaintingSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import PictureUpload from '@/components/PictureUpload.vue'
import UrlPictureUpload from '@/components/UrlPictureUpload.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import ImageOutPainting from '@/components/ImageOutPainting.vue'
import { reactive, ref, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { useRouter, useRoute } from 'vue-router'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'

const router = useRouter()
const route = useRoute()

// 上传方式
const uploadType = ref<'file' | 'url'>('file')
const picture = ref<API.PictureVO>()
const pictureForm = reactive<API.PictureEditRequest>({})
const space = ref<API.SpaceVO>()

// 图片上传成功
const onSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
  pictureForm.name = newPicture.name
  pictureForm.introduction = newPicture.introduction
  pictureForm.category = newPicture.category
  pictureForm.tags = newPicture.tags
}

// 空间ID
const spaceId = computed(() => route.query?.spaceId)
const spaceName = ref<string>('')

// 提交表单
const handSubmit = async (value: any) => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    message.error('😅 请先上传图片')
    return
  }
  try {
    const res = await editPictureUsingPost({ id: pictureId, spaceId: spaceId.value, ...value })
    if (res.data.code === 0 && res.data.data) {
      message.success(route.query.id ? '✅ 修改成功' : '🎉 创建成功')
      router.push(`/picture/${pictureId}`)
    } else {
      message.error('😅 操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 操作失败')
  }
}

// 获取分类和标签选项
const categoryOptions = ref<any[]>([])
const tagOptions = ref<any[]>([])

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
      tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
    }
  } catch (error) {
    message.error('😅 获取标签分类列表失败')
  }
}

// 获取已有图片数据
const getOldPicture = async () => {
  const id = route.query.id
  if (id) {
    try {
      const res = await getPictureVoByIdUsingGet({ id: id as string })
      if (res.data.code === 0 && res.data.data) {
        picture.value = res.data.data
        pictureForm.name = picture.value.name
        pictureForm.introduction = picture.value.introduction
        pictureForm.category = picture.value.category
        pictureForm.tags = picture.value.tags
      }
    } catch (error) {
      message.error('😅 获取图片信息失败')
    }
  }
}

// 获取空间信息
const getOldSpace = async () => {
  if (spaceId.value) {
    try {
      const res = await getSpaceVoByIdUsingGet({ id: spaceId.value as string })
      if (res.data.code === 0 && res.data.data) {
        space.value = res.data.data
        spaceName.value = res.data.data.spaceName || spaceId.value as string
      }
    } catch (error) {
      spaceName.value = spaceId.value as string
    }
  }
}

// 图片编辑
const imageCropperRef = ref()
const doEditPicture = () => {
  imageCropperRef.value?.openModal()
}

const onCropperSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// AI 扩图
const imageOutPaintingRef = ref()
const doImagePainting = () => {
  imageOutPaintingRef.value?.openModal()
}

const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// 返回
const goBack = () => {
  router.back()
}

// 初始化
onMounted(() => {
  getTagCategoryOptions()
  getOldPicture()
  getOldSpace()
})

import { watchEffect } from 'vue'
watchEffect(() => {
  getOldSpace()
})
</script>

<style scoped lang="less">
#addPicturePage {
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--space-8);
}

.header-content {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.back-btn {
  flex-shrink: 0;
  height: 48px;
}

.header-info {
  flex: 1;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 800;
  margin: 0;
  line-height: 1.2;
}

.page-subtitle {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin: var(--space-2) 0 0 0;
  font-weight: 600;
}

/* ========== 上传容器 ========== */
.upload-container {
  display: flex;
  flex-direction: column;
  gap: var(--space-8);
}

.upload-main {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

/* ========== 上传卡片 ========== */
.upload-card {
  padding: var(--space-6);

  &::before {
    display: none;
  }
}

.upload-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: var(--space-6);
  }

  :deep(.ant-tabs-tab) {
    padding: var(--space-3) var(--space-5);
    border-radius: var(--radius-full);
    border: 3px solid var(--border-light);
    font-weight: 700;
    margin-right: var(--space-3);

    &:hover {
      background: var(--bg-hover);
    }
  }

  :deep(.ant-tabs-tab-active) {
    background: var(--color-sky);
    border-color: var(--color-sky);
    color: white;
  }

  :deep(.ant-tabs-ink-bar) {
    display: none;
  }
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 700;

  span:first-child {
    font-size: var(--text-xl);
  }
}

/* ========== 预览区域 ========== */
.preview-section {
  display: flex;
  justify-content: center;
}

.preview-card {
  width: 100%;
  max-width: 600px;
  padding: var(--space-6);

  &::before {
    display: none;
  }
}

.preview-image {
  border-radius: var(--radius-xl);
  overflow: hidden;
  margin-bottom: var(--space-6);
  border: 3px solid var(--border-bold);

  img {
    width: 100%;
    height: auto;
    display: block;
  }
}

.preview-actions {
  display: flex;
  gap: var(--space-4);
}

.action-btn {
  flex: 1;
  height: 48px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);

  span:first-child {
    font-size: var(--text-lg);
  }
}

/* ========== 表单区域 ========== */
.form-card {
  padding: var(--space-6);

  &::before {
    display: none;
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-4);
  border-bottom: 3px dashed var(--border-light);
}

.header-icon {
  font-size: var(--text-2xl);
}

.card-title {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  font-weight: 700;
  margin: 0;
}

.submit-form-item {
  margin-top: var(--space-6);

  :deep(.ant-form-item-control-input) {
    display: flex;
    justify-content: center;
  }
}

.submit-btn {
  min-width: 160px;
  height: 52px;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  #addPicturePage {
    padding: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .preview-actions {
    flex-direction: column;
  }
}
</style>
