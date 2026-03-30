<template>
  <div id="addPicturePage">
    <!-- 紫漾梦幻氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="floating-shapes">
        <div class="shape-dot dot-1"></div>
        <div class="shape-dot dot-2"></div>
        <div class="shape-dot dot-3"></div>
        <div class="shape-dot dot-4"></div>
        <div class="shape-dot dot-5"></div>
      </div>
    </div>

    <!-- 顶部导航栏 -->
    <header class="page-header">
      <button @click="goBack" class="back-btn">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <span>返回</span>
      </button>

      <div class="header-center">
        <div class="brand-indicator">
          <span class="indicator-dot"></span>
          <span class="indicator-text">{{ route.query.id ? '编辑模式' : '上传通道' }}</span>
        </div>
      </div>

      <div class="header-space">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
        </svg>
        <span>{{ spaceName }}</span>
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- Hero 上传区域 -->
      <section class="upload-hero">
        <div class="hero-text">
          <h1 class="hero-title">
            <span class="title-word" v-for="(word, i) in titleWords" :key="i"
                  :style="{ animationDelay: `${i * 0.12}s` }">
              {{ word }}
            </span>
          </h1>
          <p class="hero-subtitle">{{ route.query.id ? '更新你的视觉作品' : '将你的视觉作品传送到 VisionSpace ✨' }}</p>
        </div>

        <!-- 上传方式切换 -->
        <div class="upload-mode-toggle visible">
          <button
            class="mode-btn"
            :class="{ active: uploadType === 'file' }"
            @click="uploadType = 'file'"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <span>本地上传</span>
          </button>
          <button
            class="mode-btn"
            :class="{ active: uploadType === 'url' }"
            @click="uploadType = 'url'"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
            </svg>
            <span>URL 上传</span>
          </button>
        </div>
      </section>

      <!-- 上传内容区 -->
      <div class="upload-content" :class="{ 'has-image': !!picture }">
        <!-- 左侧：上传区域 -->
        <div class="upload-section">
          <!-- 上传 Portal -->
          <div class="upload-portal" :class="{ 'drag-over': isDragOver, 'has-file': !!picture }">
            <!-- Portal 边框光环 -->
            <div class="portal-ring outer"></div>
            <div class="portal-ring inner"></div>
            <div class="portal-glow"></div>

            <!-- 上传组件 -->
            <div class="portal-content">
              <PictureUpload
                v-if="uploadType === 'file' && !picture"
                :picture="picture"
                :spaceId="spaceId"
                :on-success="onSuccess"
                class="upload-component"
              />
              <UrlPictureUpload
                v-else-if="uploadType === 'url' && !picture"
                :picture="picture"
                :spaceId="spaceId"
                :on-success="onSuccess"
                class="upload-component"
              />

              <!-- 图片预览 -->
              <div v-if="picture" class="preview-portal">
                <div class="preview-image-wrap">
                  <img :src="picture.url" :alt="picture.name" class="preview-image" />
                  <div class="preview-overlay">
                    <button class="preview-action-btn" @click="changeImage">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                        <polyline points="17 8 12 3 7 8"/>
                        <line x1="12" y1="3" x2="12" y2="15"/>
                      </svg>
                      <span>更换图片</span>
                    </button>
                  </div>
                </div>

                <!-- 快速操作按钮 -->
                <div class="quick-actions">
                  <button class="quick-action-btn" @click="doEditPicture" title="裁剪编辑">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="6" cy="6" r="3"/>
                      <circle cx="6" cy="18" r="3"/>
                      <line x1="20" y1="4" x2="8.12" y2="15.88"/>
                      <line x1="14.47" y1="14.48" x2="20" y2="20"/>
                      <line x1="8.12" y1="8.12" x2="12" y2="12"/>
                    </svg>
                    <span>裁剪</span>
                  </button>
                  <button class="quick-action-btn" @click="doImagePainting" title="AI 扩图">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 2a10 10 0 0 1 10 10c0 5.52-4.48 10-10 10S2 17.52 2 12"/>
                      <path d="M12 8v4l2 2"/>
                      <path d="M2.5 12a9.5 9.5 0 0 1 1.5-3"/>
                    </svg>
                    <span>AI 扩图</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 装饰粒子 -->
            <div class="portal-particles">
              <span v-for="i in 6" :key="i" class="particle" :style="{ animationDelay: `${i * 0.3}s` }"></span>
            </div>
          </div>
        </div>

        <!-- 右侧：图片信息表单 -->
        <Transition name="form-reveal">
          <div v-if="picture" class="info-section">
            <div class="info-card">
              <div class="card-shine"></div>

              <div class="card-header">
                <div class="header-icon-wrap">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 20h9"/>
                    <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
                  </svg>
                </div>
                <div class="header-text">
                  <h3 class="card-title">作品信息</h3>
                  <p class="card-subtitle">完善图片详情，让作品更具价值</p>
                </div>
              </div>

              <form class="info-form" @submit.prevent="handSubmit">
                <div class="form-group">
                  <label class="form-label">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
                      <line x1="7" y1="7" x2="7.01" y2="7"/>
                    </svg>
                    <span>作品名称</span>
                  </label>
                  <input
                    v-model="pictureForm.name"
                    type="text"
                    class="form-input"
                    placeholder="给你的作品起个名字"
                    autocomplete="off"
                  />
                </div>

                <div class="form-group">
                  <label class="form-label">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      <polyline points="14 2 14 8 20 8"/>
                      <line x1="16" y1="13" x2="8" y2="13"/>
                      <line x1="16" y1="17" x2="8" y2="17"/>
                    </svg>
                    <span>作品简介</span>
                  </label>
                  <textarea
                    v-model="pictureForm.introduction"
                    class="form-textarea"
                    placeholder="讲述这张图片背后的故事..."
                    rows="3"
                  ></textarea>
                </div>

                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                      </svg>
                      <span>分类</span>
                    </label>
                    <input
                      v-model="pictureForm.category"
                      type="text"
                      class="form-input"
                      placeholder="选择分类"
                      list="categoryList"
                      autocomplete="off"
                    />
                    <datalist id="categoryList">
                      <option v-for="cat in categoryOptions" :key="cat.value" :value="cat.value" />
                    </datalist>
                  </div>

                  <div class="form-group">
                    <label class="form-label">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
                        <line x1="7" y1="7" x2="7.01" y2="7"/>
                      </svg>
                      <span>标签</span>
                    </label>
                    <input
                      v-model="tagInput"
                      type="text"
                      class="form-input"
                      placeholder="输入标签后回车"
                      @keydown.enter.prevent="addTag"
                      autocomplete="off"
                    />
                    <div class="tag-list" v-if="pictureForm.tags?.length">
                      <span
                        v-for="tag in pictureForm.tags"
                        :key="tag"
                        class="tag-item"
                      >
                        {{ tag }}
                        <button type="button" class="tag-remove" @click="removeTag(tag)">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                            <line x1="18" y1="6" x2="6" y2="18"/>
                            <line x1="6" y1="6" x2="18" y2="18"/>
                          </svg>
                        </button>
                      </span>
                    </div>
                  </div>
                </div>

                <div class="form-actions">
                  <button type="submit" class="submit-btn primary">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                    <span>{{ route.query.id ? '保存修改' : '创建作品' }}</span>
                  </button>
                  <button type="button" class="submit-btn secondary" @click="goBack">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="18" y1="6" x2="6" y2="18"/>
                      <line x1="6" y1="6" x2="18" y2="18"/>
                    </svg>
                    <span>取消</span>
                  </button>
                </div>
              </form>
            </div>
          </div>
        </Transition>
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

// Refs
const imageCropperRef = ref()
const imageOutPaintingRef = ref()

// 上传方式
const uploadType = ref<'file' | 'url'>('file')
const picture = ref<API.PictureVO>()
const pictureForm = reactive<API.PictureEditRequest>({})
const space = ref<API.SpaceVO>()
const isDragOver = ref(false)

// 标签输入
const tagInput = ref('')

const titleWords = computed(() =>
  route.query.id ? ['编辑', '作品'] : ['上传', '作品']
)

// 空间ID
const spaceId = computed(() => route.query?.spaceId)
const spaceName = ref<string>('')
const imageLoaded = computed(() => !!picture.value)

// 上传成功
const onSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
  pictureForm.name = newPicture.name || ''
  pictureForm.introduction = newPicture.introduction || ''
  pictureForm.category = newPicture.category || ''
  pictureForm.tags = newPicture.tags || []
}

// 裁剪成功
const onCropperSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// AI 扩图成功
const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// 添加标签
const addTag = () => {
  const tag = tagInput.value.trim()
  if (tag && !pictureForm.tags?.includes(tag)) {
    if (!pictureForm.tags) pictureForm.tags = []
    pictureForm.tags.push(tag)
  }
  tagInput.value = ''
}

// 移除标签
const removeTag = (tag: string) => {
  pictureForm.tags = pictureForm.tags?.filter(t => t !== tag)
}

// 更换图片
const changeImage = () => {
  picture.value = undefined
  pictureForm.name = ''
  pictureForm.introduction = ''
  pictureForm.category = ''
  pictureForm.tags = []
}

// 提交表单
const handSubmit = async () => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    message.error('请先上传图片')
    return
  }
  try {
    const res = await editPictureUsingPost({ id: pictureId, spaceId: spaceId.value, ...pictureForm })
    if (res.data.code === 0 && res.data.data) {
      message.success(route.query.id ? '修改成功' : '创建成功')
      router.push(`/picture/${pictureId}`)
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// 分类和标签选项
const categoryOptions = ref<any[]>([])

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
    }
  } catch (error) {
    console.error('获取标签分类列表失败')
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
        pictureForm.name = picture.value.name || ''
        pictureForm.introduction = picture.value.introduction || ''
        pictureForm.category = picture.value.category || ''
        pictureForm.tags = picture.value.tags || []
      }
    } catch (error) {
      message.error('获取图片信息失败')
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
        spaceName.value = res.data.data.spaceName || String(spaceId.value)
      }
    } catch (error) {
      spaceName.value = String(spaceId.value)
    }
  }
}

// 图片编辑
const doEditPicture = () => {
  imageCropperRef.value?.openModal()
}

// AI 扩图
const doImagePainting = () => {
  imageOutPaintingRef.value?.openModal()
}

// 返回
const goBack = () => {
  router.back()
}

onMounted(() => {
  getTagCategoryOptions()
  getOldPicture()
  getOldSpace()
})
</script>

<style scoped lang="less">
/* Aurora 主题变量别名 */
#addPicturePage {
  /* 颜色别名 */
  --color-primary: var(--color-primary-500);
  --color-primary-dark: var(--color-primary-600);
  --color-primary-light: var(--color-primary-300);
  --color-secondary: var(--color-secondary-500);
  --color-secondary-dark: var(--color-secondary-600);
  --color-violet: var(--color-accent-purple);
  --color-pink: var(--color-accent-pink);
  --color-rose: var(--color-accent-pink);
  --color-cyan: var(--color-accent-cyan);
  --color-cyan-dark: var(--color-accent-cyan-dark);

  /* 背景和卡片 */
  --bg-primary: var(--color-bg-primary);
  --bg-secondary: var(--color-bg-secondary);
  --bg-tertiary: var(--color-bg-tertiary);
  --bg-elevated: var(--color-bg-elevated);
  --bg-hover: var(--color-bg-hover);
  --bg-card: var(--gradient-card);

  /* 文字颜色 */
  --text-primary: var(--color-text-primary);
  --text-secondary: var(--color-text-secondary);
  --text-tertiary: var(--color-text-tertiary);
  --text-disabled: var(--color-text-disabled);

  /* 边框颜色 */
  --border-default: var(--color-border-default);
  --border-subtle: var(--color-border-subtle);
  --border-strong: var(--color-border-strong);
  --border-accent: var(--color-border-accent);

  /* 阴影 */
  --shadow-card: 0 4px 20px rgba(0, 0, 0, 0.4);
  --shadow-card-hover: 0 8px 30px rgba(0, 0, 0, 0.5);
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.5);
  --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.6);
  --shadow-glow-purple: 0 0 30px rgba(110, 53, 235, 0.35);

  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 紫漾氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.5;
  animation: float-orb 25s ease-in-out infinite;
}

.orb-1 {
  width: 700px;
  height: 700px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.25) 0%, transparent 70%);
  top: -200px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%);
  top: 30%;
  left: -150px;
  animation-delay: -8s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
  bottom: -100px;
  right: 20%;
  animation-delay: -16s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(40px, -40px) scale(1.05); }
  50% { transform: translate(-30px, 30px) scale(0.95); }
  75% { transform: translate(25px, 15px) scale(1.03); }
}

/* 浮动装饰点 */
.floating-shapes {
  position: absolute;
  inset: 0;
}

.shape-dot {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
  animation: float-dot 15s ease-in-out infinite;
}

.dot-1 {
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  top: 15%;
  left: 10%;
  animation-delay: 0s;
}

.dot-2 {
  width: 8px;
  height: 8px;
  background: var(--color-secondary);
  top: 25%;
  right: 20%;
  animation-delay: -3s;
}

.dot-3 {
  width: 16px;
  height: 16px;
  background: var(--color-violet);
  bottom: 30%;
  left: 15%;
  animation-delay: -6s;
}

.dot-4 {
  width: 10px;
  height: 10px;
  background: var(--color-pink);
  top: 60%;
  right: 25%;
  animation-delay: -9s;
}

.dot-5 {
  width: 6px;
  height: 6px;
  background: var(--color-primary-light);
  bottom: 20%;
  right: 40%;
  animation-delay: -12s;
}

@keyframes float-dot {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  25% { transform: translate(10px, -15px) rotate(90deg); }
  50% { transform: translate(-5px, 10px) rotate(180deg); }
  75% { transform: translate(15px, 5px) rotate(270deg); }
}

/* ========== 页面头部 ========== */
.page-header {
  position: relative;
  z-index: 10;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 32px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateX(-4px);
    box-shadow: var(--shadow-glow-purple);

    svg {
      transform: translateX(-2px);
    }
  }

  svg {
    transition: transform 0.3s ease;
  }
}

.header-center {
  display: flex;
  align-items: center;
}

.brand-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(168, 85, 247, 0.1);
  border: 1px solid var(--color-primary-light);
  border-radius: 20px;
}

.indicator-dot {
  width: 8px;
  height: 8px;
  background: var(--color-primary);
  border-radius: 50%;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.8); }
}

.indicator-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary-dark);
  letter-spacing: 1px;
}

.header-space {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  box-shadow: var(--shadow-sm);

  svg {
    color: var(--text-tertiary);
  }
}

/* ========== 主内容区 ========== */
.main-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px 32px 80px;
}

/* ========== Hero 区域 ========== */
.upload-hero {
  text-align: center;
  margin-bottom: 48px;
}

.hero-text {
  margin-bottom: 32px;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(36px, 6vw, 64px);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 16px 0;
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.title-word {
  display: inline-block;
  animation: title-reveal 0.8s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  opacity: 0;
  transform: translateY(30px);

  &:nth-child(1) {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 50%, var(--color-violet) 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  &:nth-child(2) {
    background: linear-gradient(135deg, var(--color-secondary) 0%, var(--color-secondary-dark) 50%, var(--color-pink) 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

@keyframes title-reveal {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-subtitle {
  font-size: clamp(14px, 2vw, 18px);
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
  letter-spacing: 1px;
  animation: fade-up 0.8s ease-out 0.3s forwards;
  opacity: 0;
  transform: translateY(20px);
}

@keyframes fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 上传模式切换 ========== */
.upload-mode-toggle {
  display: flex;
  justify-content: center;
  gap: 12px;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.5s ease;

  &.visible {
    opacity: 1;
    transform: translateY(0);
  }
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  svg {
    color: var(--text-secondary);
    transition: color 0.3s ease;
  }

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    color: var(--text-primary);

    svg {
      color: var(--text-primary);
    }
  }

  &.active {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    border-color: transparent;
    color: white;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

    svg {
      color: white;
    }
  }
}

/* ========== 上传内容区 ========== */
.upload-content {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
  transition: all 0.5s ease;

  &.has-image {
    grid-template-columns: 1fr 420px;

    @media (max-width: 1024px) {
      grid-template-columns: 1fr;
    }
  }
}

/* ========== 上传 Portal ========== */
.upload-section {
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-portal {
  position: relative;
  width: 100%;
  max-width: 560px;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border-radius: 32px;
  border: 2px solid var(--border-default);
  overflow: hidden;
  transition: all 0.4s ease;
  box-shadow: var(--shadow-card);

  &.drag-over {
    border-color: var(--color-primary);
    box-shadow: 0 0 60px rgba(168, 85, 247, 0.3);
    transform: scale(1.02);

    .portal-ring {
      animation-play-state: running;
    }

    .portal-glow {
      opacity: 1;
    }
  }

  &.has-file {
    aspect-ratio: auto;
    min-height: 400px;
  }
}

.portal-ring {
  position: absolute;
  border-radius: 50%;
  border: 2px solid transparent;
  opacity: 0.6;

  &.outer {
    width: calc(100% + 40px);
    height: calc(100% + 40px);
    border-top-color: rgba(168, 85, 247, 0.4);
    border-right-color: rgba(168, 85, 247, 0.2);
    animation: rotate-ring 8s linear infinite paused;
  }

  &.inner {
    width: calc(100% + 20px);
    height: calc(100% + 20px);
    border-top-color: rgba(236, 72, 153, 0.3);
    border-right-color: rgba(236, 72, 153, 0.15);
    animation: rotate-ring 6s linear infinite reverse paused;
  }
}

@keyframes rotate-ring {
  to { transform: rotate(360deg); }
}

.portal-glow {
  position: absolute;
  inset: -50%;
  background: radial-gradient(circle at center, rgba(168, 85, 247, 0.1) 0%, transparent 50%);
  opacity: 0;
  transition: opacity 0.4s ease;
  pointer-events: none;
}

.portal-content {
  position: relative;
  z-index: 2;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

.upload-component {
  width: 100%;
  height: 100%;
}

/* ========== 预览区域 ========== */
.preview-portal {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.preview-image-wrap {
  position: relative;
  flex: 1;
  border-radius: 20px;
  overflow: hidden;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  background: rgba(250, 247, 255, 0.8);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.preview-image-wrap:hover .preview-overlay {
  opacity: 1;
}

.preview-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 16px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-md);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: scale(1.05);
    box-shadow: var(--shadow-glow-purple);
  }
}

.quick-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  justify-content: center;
}

.quick-action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 30px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  svg {
    color: var(--text-tertiary);
    transition: color 0.3s ease;
  }

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary-light);
    color: var(--text-primary);

    svg {
      color: var(--color-primary);
    }
  }
}

/* ========== Portal 装饰粒子 ========== */
.portal-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  width: 4px;
  height: 4px;
  background: var(--color-primary);
  border-radius: 50%;
  opacity: 0.6;

  &:nth-child(1) { top: 20%; left: 10%; animation: float-particle 4s ease-in-out infinite; }
  &:nth-child(2) { top: 30%; right: 15%; animation: float-particle 5s ease-in-out infinite 0.5s; }
  &:nth-child(3) { bottom: 25%; left: 20%; animation: float-particle 4.5s ease-in-out infinite 1s; }
  &:nth-child(4) { bottom: 35%; right: 10%; animation: float-particle 5.5s ease-in-out infinite 1.5s; }
  &:nth-child(5) { top: 50%; left: 5%; animation: float-particle 4s ease-in-out infinite 2s; }
  &:nth-child(6) { top: 40%; right: 8%; animation: float-particle 4.8s ease-in-out infinite 2.5s; }
}

@keyframes float-particle {
  0%, 100% { transform: translate(0, 0) scale(1); opacity: 0.6; }
  50% { transform: translate(10px, -15px) scale(1.2); opacity: 1; }
}

/* ========== 信息卡片 ========== */
.info-section {
  position: relative;
}

.info-card {
  position: relative;
  padding: 32px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-2px);
  }
}

.card-shine {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 30%, rgba(168, 85, 247, 0.08) 0%, transparent 50%);
  pointer-events: none;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.header-icon-wrap {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.1);
  border: 1px solid var(--color-primary-light);
  border-radius: 14px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.header-text {
  flex: 1;
}

.card-title {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 6px 0;
}

.card-subtitle {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

/* ========== 表单 ========== */
.info-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
}

.form-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;

  svg {
    color: var(--color-primary);
  }
}

.form-input,
.form-textarea {
  padding: 14px 18px;
  background: var(--bg-tertiary);
  border: 2px solid var(--border-default);
  border-radius: 12px;
  color: var(--text-primary);
  font-size: 15px;
  transition: all 0.3s ease;
  outline: none;

  &::placeholder {
    color: var(--text-tertiary);
  }

  &:focus {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.1);
  }
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(168, 85, 247, 0.1);
  border: 1px solid var(--color-primary-light);
  border-radius: 20px;
  font-size: 13px;
  color: var(--color-primary-dark);
  animation: tag-appear 0.3s ease;
}

@keyframes tag-appear {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.tag-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  background: rgba(168, 85, 247, 0.2);
  border: none;
  border-radius: 50%;
  color: var(--color-primary-dark);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(239, 68, 68, 0.2);
    color: var(--color-rose);
  }
}

/* ========== 提交按钮 ========== */
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.submit-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 24px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    transition: transform 0.3s ease;
  }

  &.primary {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    border: none;
    color: white;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);

      svg {
        transform: scale(1.1);
      }
    }
  }

  &.secondary {
    background: var(--bg-card);
    border: 1px solid var(--border-default);
    color: var(--text-primary);

    &:hover {
      background: var(--bg-hover);
      border-color: var(--color-primary);

      svg {
        transform: rotate(90deg);
      }
    }
  }
}

/* ========== 表单过渡动画 ========== */
.form-reveal-enter-active {
  transition: all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.form-reveal-leave-active {
  transition: all 0.3s ease;
}

.form-reveal-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.form-reveal-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .page-header {
    padding: 16px 20px;
  }

  .header-space span {
    display: none;
  }

  .header-space {
    padding: 10px;
  }

  .back-btn span {
    display: none;
  }

  .back-btn {
    padding: 10px;
  }

  .main-content {
    padding: 16px 20px 60px;
  }

  .upload-portal {
    max-width: 100%;
    aspect-ratio: 4/3;
  }

  .info-card {
    padding: 24px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
  }
}
</style>
