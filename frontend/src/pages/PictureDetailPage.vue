<template>
  <div id="pictureDetailPage">
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
    <header class="detail-header">
      <button @click="goBack" class="back-btn">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M19 12H5M12 19l-7-7 7-7"/>
        </svg>
        <span>返回</span>
      </button>

      <div class="header-actions">
        <button class="action-icon-btn" @click="doShare" title="分享">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="18" cy="5" r="3"/>
            <circle cx="6" cy="12" r="3"/>
            <circle cx="18" cy="19" r="3"/>
            <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
            <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
          </svg>
        </button>
        <button class="action-icon-btn" @click="doDownload" title="下载">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
        </button>
        <button v-if="canEdit" @click="doEdit" class="action-icon-btn" title="编辑">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </button>
        <button v-if="canDelete" @click="doDelete" class="action-icon-btn delete" title="删除">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
        </button>
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="main-container">
      <!-- 左侧：图片画廊区 -->
      <section class="gallery-section">
        <!-- 图片展示区 -->
        <div class="image-stage" ref="imageStage">
          <!-- 主图片 -->
          <div
            class="main-image-wrapper"
            :class="{ loaded: imageLoaded }"
            @click="toggleFullscreen"
          >
            <!-- 加载骨架 -->
            <div class="image-skeleton" v-if="!imageLoaded">
              <div class="skeleton-shimmer"></div>
            </div>

            <!-- 实际图片 -->
            <img
              :src="picture.previewUrl || picture.url"
              :alt="picture.name"
              class="main-image"
              @load="onImageLoad"
              :class="{ visible: imageLoaded }"
            />

            <!-- 悬停时的缩放提示 -->
            <div class="zoom-hint">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <path d="m21 21-4.35-4.35"/>
                <path d="M11 8v6M8 11h6"/>
              </svg>
              <span>点击全屏预览</span>
            </div>
          </div>

          <!-- 图片装饰边框 -->
          <div class="image-frame frame-top"></div>
          <div class="image-frame frame-bottom"></div>
          <div class="image-frame frame-left"></div>
          <div class="image-frame frame-right"></div>

          <!-- 底部图片信息条 -->
          <div class="image-info-bar" :class="{ visible: imageLoaded }">
            <div class="info-bar-item">
              <span class="info-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <polyline points="21 15 16 10 5 21"/>
                </svg>
              </span>
              <span class="info-text">{{ picture.picWidth }} × {{ picture.picHeight }}</span>
            </div>
            <div class="info-divider"></div>
            <div class="info-bar-item">
              <span class="info-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                </svg>
              </span>
              <span class="info-text">{{ picture.picFormat?.toUpperCase() }}</span>
            </div>
            <div class="info-divider"></div>
            <div class="info-bar-item">
              <span class="info-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                </svg>
              </span>
              <span class="info-text">{{ formatSize(picture.picSize) }}</span>
            </div>
            <div class="info-divider"></div>
            <div class="info-bar-item">
              <span class="info-icon">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                  <polyline points="22,6 12,13 2,6"/>
                </svg>
              </span>
              <span class="info-text">{{ picture.picScale }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：信息面板 -->
      <aside class="info-panel" :class="{ visible: imageLoaded }">
        <!-- 作者卡片 -->
        <div class="author-card" :style="{ animationDelay: '0.1s' }">
          <div class="author-glow"></div>
          <div class="author-content">
            <a-avatar
              :src="picture.user?.userAvatar"
              :size="56"
              class="author-avatar"
            />
            <div class="author-info">
              <span class="author-name">{{ picture.user?.userName || '未知用户' }}</span>
              <span class="author-account">@{{ picture.user?.userAccount }}</span>
            </div>
          </div>
          <div class="author-decoration">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
              <circle cx="20" cy="20" r="18" stroke="url(#authorGradZiyan)" stroke-width="2" stroke-dasharray="4 4"/>
              <defs>
                <linearGradient id="authorGradZiyan" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#a855f7"/>
                  <stop offset="100%" stop-color="#ec4899"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
        </div>

        <!-- 图片标题 -->
        <div class="title-section" :style="{ animationDelay: '0.2s' }">
          <h1 class="picture-title">{{ picture.name || '未命名作品' }}</h1>
          <p class="picture-intro" v-if="picture.introduction">{{ picture.introduction }}</p>
        </div>

        <!-- 元数据网格 -->
        <div class="meta-grid" :style="{ animationDelay: '0.3s' }">
          <div class="meta-card" v-if="picture.category">
            <div class="meta-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="meta-content">
              <span class="meta-label">分类</span>
              <span class="meta-value">{{ picture.category }}</span>
            </div>
          </div>

          <div class="meta-card color-card" v-if="picture.picColor">
            <div class="meta-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="13.5" cy="6.5" r="3"/>
                <circle cx="17.5" cy="10.5" r="3"/>
                <circle cx="8.5" cy="7.5" r="3"/>
                <circle cx="6.5" cy="12.5" r="3"/>
                <path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.5-2.5-4.5L12 22z"/>
              </svg>
            </div>
            <div class="meta-content">
              <span class="meta-label">主色调</span>
              <div class="color-value">
                <span class="meta-value">{{ picture.picColor }}</span>
                <span class="color-swatch" :style="{ backgroundColor: toHexColor(picture.picColor) }"></span>
              </div>
            </div>
          </div>

          <div class="meta-card" v-if="picture.tags?.length">
            <div class="meta-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/>
                <line x1="7" y1="7" x2="7.01" y2="7"/>
              </svg>
            </div>
            <div class="meta-content">
              <span class="meta-label">标签</span>
              <div class="tags-wrap">
                <span class="tag-chip" v-for="tag in picture.tags" :key="tag">{{ tag }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 操作按钮组 -->
        <div class="action-group" :style="{ animationDelay: '0.4s' }">
          <button class="action-btn primary" @click="toggleFullscreen">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
            </svg>
            <span>全屏预览</span>
          </button>
          <button class="action-btn secondary" @click="doDownload">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            <span>免费下载</span>
          </button>
        </div>

        <!-- 缩放信息 -->
        <div class="zoom-tips" :style="{ animationDelay: '0.5s' }">
          <div class="tip-item">
            <kbd>Scroll</kbd>
            <span>缩放图片</span>
          </div>
          <div class="tip-item">
            <kbd>Drag</kbd>
            <span>移动视图</span>
          </div>
        </div>
      </aside>
    </div>

    <!-- 全屏预览模态框 -->
    <ImagePreview v-model:open="isFullscreen" :url="picture.previewUrl || picture.url" :alt="picture.name" />

    <!-- 分享弹窗 -->
    <ShareModal ref="shareModalRef" :link="shareLink" title="分享图片" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import ShareModal from '@/components/ShareModal.vue'
import ImagePreview from '@/components/ImagePreview.vue'

interface Props {
  id: number | string
}

const props = defineProps<Props>()
const router = useRouter()

// Refs
const imageStage = ref<HTMLElement>()

// State
const picture = ref<API.PictureVO>({})
const imageLoaded = ref(false)
const isFullscreen = ref(false)

// Fetch picture detail
const fetchDetailPicture = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({ id: props.id })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败')
    }
  } catch (error: any) {
    message.error(error.message)
  }
}

// Image load handler
const onImageLoad = () => {
  imageLoaded.value = true
}

// Permission checks
const createPermissionChecker = (permission: string) => {
  return computed(() => (picture.value.permissionList ?? []).includes(permission))
}

const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// Share
const shareModalRef = ref()
const shareLink = ref<string>('')

const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${props.id}`
  shareModalRef.value?.openModal()
}

// Download
const doDownload = () => {
  downloadImage(picture.value.url)
}

// Edit
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: { id: props.id, spaceId: picture.value.spaceId },
  })
}

// Delete
const doDelete = async () => {
  const id = props.id
  if (!id) return

  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('图片已删除')
      router.push({ name: 'home' })
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// Go back
const goBack = () => {
  router.back()
}

// Fullscreen
const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

onMounted(() => {
  fetchDetailPicture()
})

watch(() => props.id, () => {
  fetchDetailPicture()
})
</script>

<style scoped lang="less">
#pictureDetailPage {
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
  top: -250px;
  right: -150px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%);
  top: 30%;
  left: -200px;
  animation-delay: -8s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
  bottom: -100px;
  right: 10%;
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

/* ========== 顶部导航栏 ========== */
.detail-header {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 32px;
  margin-bottom: 24px;
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

.header-actions {
  display: flex;
  gap: 8px;
}

.action-icon-btn {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 12px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateY(-2px);
    box-shadow: var(--shadow-glow-purple);
  }

  &.delete:hover {
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.5);
  }
}

/* ========== 主容器 ========== */
.main-container {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 48px;
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 48px 60px;
  min-height: 100vh;

  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 90px 24px 40px;
  }
}

/* ========== 画廊区 ========== */
.gallery-section {
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-stage {
  position: relative;
  width: 100%;
  max-width: 1000px;
}

.main-image-wrapper {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  cursor: zoom-in;
  opacity: 0;
  transform: scale(0.95);
  transition: all 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: var(--shadow-card);

  &.loaded {
    opacity: 1;
    transform: scale(1);
  }

  &:hover .zoom-hint {
    opacity: 1;
  }
}

.image-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    var(--bg-tertiary) 0%,
    var(--bg-secondary) 50%,
    var(--bg-tertiary) 100%
  );
  background-size: 200% 100%;
  animation: skeleton 1.5s infinite;
}

@keyframes skeleton {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.main-image {
  display: block;
  width: 100%;
  max-height: 75vh;
  object-fit: contain;
  opacity: 0;
  transition: opacity 0.4s ease;

  &.visible {
    opacity: 1;
  }
}

.zoom-hint {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 20px;
  color: var(--text-primary);
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
  box-shadow: var(--shadow-md);
}

/* 图片装饰边框 */
.image-frame {
  position: absolute;
  pointer-events: none;

  &.frame-top {
    top: -3px;
    left: 10%;
    right: 10%;
    height: 3px;
    background: linear-gradient(90deg, transparent, var(--color-primary), var(--color-secondary), transparent);
  }

  &.frame-bottom {
    bottom: -3px;
    left: 10%;
    right: 10%;
    height: 3px;
    background: linear-gradient(90deg, transparent, var(--color-sky), var(--color-mint), transparent);
  }

  &.frame-left {
    left: -3px;
    top: 10%;
    bottom: 10%;
    width: 3px;
    background: linear-gradient(180deg, transparent, var(--color-coral), transparent);
  }

  &.frame-right {
    right: -3px;
    top: 10%;
    bottom: 10%;
    width: 3px;
    background: linear-gradient(180deg, transparent, var(--color-sunshine), transparent);
  }
}

/* 底部信息条 */
.image-info-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-top: 20px;
  padding: 16px 24px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  opacity: 0;
  transform: translateY(10px);
  transition: all 0.5s ease 0.3s;
  box-shadow: var(--shadow-sm);

  &.visible {
    opacity: 1;
    transform: translateY(0);
  }
}

.info-bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-icon {
  color: var(--text-tertiary);
  display: flex;
}

.info-text {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-mono);
}

.info-divider {
  width: 1px;
  height: 20px;
  background: var(--border-default);
}

/* ========== 信息面板 ========== */
.info-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
  opacity: 0;
  transform: translateX(20px);
  transition: all 0.6s ease;

  &.visible {
    opacity: 1;
    transform: translateX(0);
  }

  > div {
    opacity: 0;
    animation: panel-reveal 0.5s ease forwards;
  }
}

@keyframes panel-reveal {
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 作者卡片 */
.author-card {
  position: relative;
  padding: 20px;
  background: var(--bg-card);
  border-radius: 20px;
  border: 1px solid var(--border-default);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  transition: all 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-card-hover);
    transform: translateY(-2px);
  }
}

.author-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 30% 30%, rgba(168, 85, 247, 0.08) 0%, transparent 50%);
  pointer-events: none;
}

.author-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  z-index: 1;
}

.author-avatar {
  border: 3px solid var(--color-primary-light);
  box-shadow: 0 0 20px rgba(168, 85, 247, 0.2);
}

.author-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.author-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.author-account {
  font-size: 13px;
  color: var(--text-tertiary);
}

.author-decoration {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0.3;
  animation: spin-slow 20s linear infinite;
}

@keyframes spin-slow {
  to { transform: translateY(-50%) rotate(360deg); }
}

/* 标题区 */
.title-section {
  padding: 0 4px;
}

.picture-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 12px 0;
  line-height: 1.3;
}

.picture-intro {
  font-size: 15px;
  color: var(--text-tertiary);
  margin: 0;
  line-height: 1.6;
}

/* 元数据网格 */
.meta-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 14px;
  border: 1px solid var(--border-subtle);
  transition: all 0.3s ease;

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary-light);
    transform: translateX(4px);
    box-shadow: var(--shadow-sm);
  }
}

.meta-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(168, 85, 247, 0.1);
  border-radius: 10px;
  color: var(--color-primary);
  flex-shrink: 0;
}

.meta-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.meta-label {
  font-size: 12px;
  color: var(--text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.color-value {
  display: flex;
  align-items: center;
  gap: 10px;
}

.color-swatch {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  border: 2px solid var(--border-default);
  box-shadow: 0 0 15px currentColor;
}

.tags-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-chip {
  padding: 5px 12px;
  background: rgba(168, 85, 247, 0.1);
  border: 1px solid var(--color-primary-light);
  border-radius: 20px;
  font-size: 13px;
  color: var(--color-primary-dark);
  transition: all 0.2s ease;

  &:hover {
    background: rgba(168, 85, 247, 0.2);
    transform: translateY(-2px);
  }
}

/* 操作按钮组 */
.action-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 8px;
}

.action-btn {
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

  &.primary {
    background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
    border: none;
    color: white;
    box-shadow: 0 4px 20px rgba(168, 85, 247, 0.3);

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 30px rgba(168, 85, 247, 0.4);
    }
  }

  &.secondary {
    background: var(--bg-card);
    border: 1px solid var(--border-default);
    color: var(--text-primary);

    &:hover {
      background: var(--bg-hover);
      border-color: var(--color-primary);
      transform: translateY(-2px);
    }
  }
}

/* 缩放提示 */
.zoom-tips {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 16px;
  margin-top: auto;
}

.tip-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-tertiary);
}

kbd {
  padding: 4px 8px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 6px;
  font-size: 11px;
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .detail-header {
    padding: 12px 16px;
  }

  .back-btn span {
    display: none;
  }

  .main-container {
    padding: 0 16px 40px;
  }

  .image-info-bar {
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 16px;
  }

  .info-divider {
    display: none;
  }

  .picture-title {
    font-size: 22px;
  }

  .author-card {
    padding: 16px;
  }

  .author-decoration {
    display: none;
  }

  .zoom-tips {
    display: none;
  }
}
</style>
