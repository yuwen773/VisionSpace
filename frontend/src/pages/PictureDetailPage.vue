<template>
  <div id="pictureDetailPage">
    <!-- 返回导航 -->
    <div class="back-nav">
      <button @click="goBack" class="pop-btn outline back-btn">
        <span>←</span>
        <span>返回</span>
      </button>
    </div>

    <!-- 图片详情内容 -->
    <div class="detail-content">
      <a-row :gutter="[32, 32]">
        <!-- 图片预览 -->
        <a-col :xs="24" :md="16" :xl="18">
          <div class="preview-card pop-card">
            <div class="preview-header">
              <span class="header-icon">🖼️</span>
              <h3 class="preview-title">图片预览</h3>
            </div>
            <div class="preview-image">
              <a-image
                :src="picture.previewUrl || picture.url"
                :preview="true"
                class="main-image"
              />
            </div>
          </div>
        </a-col>

        <!-- 图片信息 -->
        <a-col :xs="24" :md="8" :xl="6">
          <div class="info-card pop-card">
            <div class="info-header">
              <span class="header-icon">ℹ️</span>
              <h3 class="info-title">图片信息</h3>
            </div>

            <!-- 作者信息 -->
            <div class="author-section">
              <div class="author-info">
                <a-avatar :src="picture.user?.userAvatar" :size="48" class="author-avatar" />
                <div class="author-details">
                  <div class="author-name">{{ picture.user?.userName || '未知用户' }}</div>
                  <div class="author-account">@{{ picture.user?.userAccount }}</div>
                </div>
              </div>
            </div>

            <!-- 图片详情 -->
            <div class="info-list">
              <div class="info-item">
                <span class="info-label">🏷️ 名称</span>
                <span class="info-value">{{ picture.name || '未命名' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">📄 简介</span>
                <span class="info-value">{{ picture.introduction || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">📂 分类</span>
                <span v-if="picture.category" class="pop-tag sunshine">{{ picture.category }}</span>
                <span v-else class="info-value">默认</span>
              </div>
              <div class="info-item">
                <span class="info-label">🏷️ 标签</span>
                <div class="tags-container">
                  <span v-if="picture.tags?.length" class="tags-list">
                    <span v-for="tag in picture.tags" :key="tag" class="pop-tag coral">{{ tag }}</span>
                  </span>
                  <span v-else class="info-value">-</span>
                </div>
              </div>
              <div class="info-item">
                <span class="info-label">📐 格式</span>
                <span class="info-value mono">{{ picture.picFormat || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">📏 尺寸</span>
                <span class="info-value mono">{{ picture.picWidth }} × {{ picture.picHeight }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">📐 宽高比</span>
                <span class="info-value mono">{{ picture.picScale || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">💾 大小</span>
                <span class="info-value mono">{{ formatSize(picture.picSize) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">🎨 主色调</span>
                <div class="color-info">
                  <span class="info-value mono">{{ picture.picColor || '-' }}</span>
                  <div
                    v-if="picture.picColor"
                    class="color-preview"
                    :style="{ backgroundColor: toHexColor(picture.picColor) }"
                  />
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="action-section">
              <div class="action-buttons">
                <button @click="doShare" class="pop-btn secondary action-btn">
                  <span>📤</span>
                  <span>分享图片</span>
                </button>
                <button @click="doDownload" class="pop-btn outline action-btn">
                  <span>⬇️</span>
                  <span>免费下载</span>
                </button>
                <button v-if="canEdit" @click="doEdit" class="pop-btn outline action-btn">
                  <span>✏️</span>
                  <span>编辑图片</span>
                </button>
                <button
                  v-if="canDelete"
                  @click="doDelete"
                  class="action-btn pop-btn"
                  style="background: var(--color-pink); border-color: var(--color-pink-dark);"
                >
                  <span>🗑️</span>
                  <span>删除图片</span>
                </button>
              </div>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- 分享弹窗 -->
    <ShareModal ref="shareModalRef" :link="shareLink" title="分享图片" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import ShareModal from '@/components/ShareModal.vue'

interface Props {
  id: number | string
}

const props = defineProps<Props>()
const router = useRouter()
const picture = ref<API.PictureVO>({})

const fetchDetailPicture = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({ id: props.id })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('😅 获取图片详情失败')
    }
  } catch (error: any) {
    message.error('😅 ' + error.message)
  }
}

onMounted(() => {
  fetchDetailPicture()
})

// 权限检查
const createPermissionChecker = (permission: string) => {
  return computed(() => (picture.value.permissionList ?? []).includes(permission))
}

const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 分享
const shareModalRef = ref()
const shareLink = ref<string>('')

const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${props.id}`
  shareModalRef.value?.openModal()
}

// 下载
const doDownload = () => {
  downloadImage(picture.value.url)
}

// 编辑
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: { id: props.id, spaceId: picture.value.spaceId },
  })
}

// 删除
const doDelete = async () => {
  const id = props.id
  if (!id) return

  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 图片已删除')
      router.push({ name: 'home' })
    } else {
      message.error('😅 删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 删除失败')
  }
}

// 返回
const goBack = () => {
  router.back()
}
</script>

<style scoped lang="less">
#pictureDetailPage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ========== 返回导航 ========== */
.back-nav {
  margin-bottom: var(--space-6);
}

.back-btn {
  height: 44px;
}

/* ========== 预览卡片 ========== */
.preview-card {
  padding: var(--space-6);
  position: sticky;
  top: var(--space-6);

  &::before {
    display: none;
  }
}

.preview-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 3px dashed var(--border-light);
}

.header-icon {
  font-size: var(--text-xl);
}

.preview-title {
  font-size: var(--text-lg);
  font-weight: 700;
  margin: 0;
}

.preview-image {
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  border: 3px solid var(--border-light);

  :deep(.ant-image) {
    width: 100%;
  }

  :deep(img) {
    max-height: 70vh;
    object-fit: contain;
  }
}

/* ========== 信息卡片 ========== */
.info-card {
  padding: var(--space-6);

  &::before {
    display: none;
  }
}

.info-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-4);
  border-bottom: 3px dashed var(--border-light);
}

.info-title {
  font-size: var(--text-lg);
  font-weight: 700;
  margin: 0;
}

/* ========== 作者信息 ========== */
.author-section {
  padding: var(--space-4);
  background: var(--gradient-fresh);
  border-radius: var(--radius-xl);
  margin-bottom: var(--space-5);
  border: 2px solid var(--border-light);
}

.author-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.author-avatar {
  border: 3px solid var(--border-bold);
}

.author-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.author-name {
  font-weight: 700;
  color: white;
}

.author-account {
  font-size: var(--text-sm);
  color: rgba(255, 255, 255, 0.8);
  font-weight: 600;
}

/* ========== 信息列表 ========== */
.info-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.info-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 600;
}

.info-value {
  color: var(--text-primary);
  font-weight: 600;
  word-break: break-all;

  &.mono {
    font-family: var(--font-mono);
    font-size: var(--text-sm);
  }
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.pop-tag {
  padding: 4px 10px;
  font-size: var(--text-xs);
}

.color-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.color-preview {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-lg);
  border: 3px solid var(--border-bold);
  box-shadow: var(--shadow-sm);
  flex-shrink: 0;
}

/* ========== 操作区域 ========== */
.action-section {
  margin-top: var(--space-6);
  padding-top: var(--space-6);
  border-top: 3px dashed var(--border-light);
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.action-btn {
  width: 100%;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  font-weight: 700;

  span:first-child {
    font-size: var(--text-lg);
  }

  &:hover {
    transform: translate(-2px, -2px);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  #pictureDetailPage {
    padding: var(--space-4);
  }

  .preview-card {
    position: static;
  }

  .preview-image {
    min-height: 300px;
  }

  :deep(.ant-image) {
    img {
      max-height: 50vh !important;
    }
  }
}
</style>
