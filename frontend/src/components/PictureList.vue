<template>
  <div id="picture-list">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <a-spin size="large" />
      <p class="loading-text"><Camera :size="20" style="vertical-align: middle;" /> 加载中...</p>
    </div>

    <!-- 图片列表 -->
    <div v-else class="picture-grid">
      <PictureCardEnhanced
        v-for="picture in dataList"
        :key="picture.id"
        :pic="picture"
        :show-card="showCardMap[picture.id]"
        :image-loaded="imageLoadedStates[picture.id]"
        @click="doClickPicture(picture)"
        @mouseenter="() => setCardShow(picture.id, true)"
        @mouseleave="() => setCardShow(picture.id, false)"
      />
    </div>

    <!-- 分享弹窗 -->
    <ShareModal ref="shareModalRef" :link="shareLink" title="分享图片" />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'
import { ref, reactive } from 'vue'
import { PIC_REVIEW_STATUS_ENUM, PIC_REVIEW_STATUS_MAP } from '@/constants/picture'
import { formatSize } from '@/utils'
import { Camera, Share2, Search, Pencil, Trash2, Folder, Package, Ruler } from 'lucide-vue-next'
import PictureCardEnhanced from './PictureCardEnhanced.vue'

const router = useRouter()

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  canEdit?: boolean
  canDelete?: boolean
  onReload?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  onReload: () => {},
  canEdit: false,
  canDelete: false,
})

// 点击图片跳转
const doClickPicture = (picture: API.PictureVO) => {
  router.push(`/picture/${picture.id}`)
}

// 每个卡片独立的显示状态
const showCardMap = reactive<Record<string | number, boolean>>({})

// 图片加载状态
const imageLoadedStates = reactive<Record<string | number, boolean>>({})

// 设置卡片操作显示状态
const setCardShow = (id: string | number, show: boolean) => {
  showCardMap[id] = show
}

// 分享图片
const shareModalRef = ref()
const shareLink = ref<string>('')

const doShare = (picture, e) => {
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// 图片搜索
const doSearch = (picture, e) => {
  e.stopPropagation()
  window.open(`/search_picture?pictureId=${picture.id}`)
}

// 编辑
const doEdit = (picture, e) => {
  e.stopPropagation()
  router.push({
    path: `/add_picture`,
    query: { id: picture.id, spaceId: picture.spaceId },
  })
}

// 删除图片
const doDelete = async (picture, e) => {
  e.stopPropagation()
  const id = picture.id
  if (!id) {
    return
  }

  // 确认删除
  const confirmed = await new Promise((resolve) => {
    message.confirm({
      title: '⚠️ 确认删除',
      content: `确定要删除图片 "${picture.name}" 吗？`,
      okText: '🗑️ 删除',
      okType: 'danger',
      cancelText: '❌ 取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })

  if (!confirmed) return

  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 图片已删除')
      props.onReload?.()
    } else {
      message.error(`😅 删除失败: ${res.data.message}`)
    }
  } catch (error) {
    message.error('😅 删除失败')
  }
}

// 获取审核状态样式
const getReviewStatusClass = (status: number) => {
  const classMap: Record<number, string> = {
    [PIC_REVIEW_STATUS_ENUM.REVIEWING]: 'status-reviewing',
    [PIC_REVIEW_STATUS_ENUM.PASS]: 'status-pass',
    [PIC_REVIEW_STATUS_ENUM.REJECT]: 'status-reject',
  }
  return classMap[status] || ''
}

// 获取审核状态文本
const getReviewStatusText = (status: number) => {
  return PIC_REVIEW_STATUS_MAP[status] || '未知'
}
</script>

<style scoped lang="less">
#picture-list {
  width: 100%;
}

/* ========== 加载状态 ========== */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-20);
}

.loading-text {
  margin-top: var(--space-4);
  font-size: var(--text-lg);
  color: var(--text-secondary);
  font-weight: 700;
}

/* ========== 图片网格 ========== */
.picture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-6);
}

/* ========== 图片卡片 ========== */
.picture-card {
  background: var(--bg-card);
  border: 3px solid var(--border-bold);
  border-radius: var(--radius-2xl);
  overflow: hidden;
  transition: all var(--transition-bounce);
  cursor: pointer;
  position: relative;

  &::before {
    display: none;
  }

  &:hover,
  &.card-hover {
    transform: translate(-3px, -3px);
    box-shadow: 6px 6px 0 rgba(0, 0, 0, 0.15);
  }
}

/* ========== 图片容器 ========== */
.picture-container {
  position: relative;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  background: var(--bg-tertiary);
  border-bottom: 3px solid var(--border-light);
}

.picture-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity var(--transition-slow), transform var(--transition-bounce);

  &.image-loaded {
    opacity: 1;
  }

  .picture-card:hover & {
    transform: scale(1.05);
  }
}

/* ========== 悬浮遮罩 ========== */
.picture-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(45, 52, 54, 0.9) 0%, transparent 60%);
  display: flex;
  align-items: flex-end;
  padding: var(--space-4);
}

.mask-enter-active,
.mask-leave-active {
  transition: opacity var(--transition-base);
}

.mask-enter-from,
.mask-leave-to {
  opacity: 0;
}

.mask-actions {
  display: flex;
  gap: var(--space-2);
  width: 100%;
  flex-wrap: wrap;
}

.action-btn {
  flex: 1;
  min-width: 60px;
  height: 40px;
  border-radius: var(--radius-lg);
  font-size: var(--text-xs);
  font-weight: 700;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  transition: all var(--transition-bounce);

  span:first-child {
    font-size: var(--text-base);
  }

  &:hover {
    transform: translateY(-2px);
  }
}

/* ========== 审核状态标签 ========== */
.review-status-badge {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  border: 2px solid var(--border-bold);
  font-size: var(--text-xs);
  font-weight: 700;

  &.status-reviewing {
    background: var(--color-sunshine);
    color: var(--text-primary);
  }

  &.status-pass {
    background: var(--color-mint);
    color: white;
  }

  &.status-reject {
    background: var(--color-coral);
    color: white;
  }
}

/* ========== 图片信息 ========== */
.picture-info {
  padding: var(--space-4);
}

.picture-name {
  font-size: var(--text-base);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 var(--space-3) 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picture-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.meta-category {
  font-size: var(--text-xs);
  padding: 4px 10px;
}

.meta-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);

  :deep(.ant-tag) {
    margin: 0;
    font-size: var(--text-xs);
    padding: 4px 8px;
    background: var(--bg-secondary);
    border: 2px solid var(--border-light);
    color: var(--text-primary);
    font-weight: 600;
    border-radius: var(--radius-md);
  }
}

.tags-more {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
  padding: 4px 6px;
  font-weight: 600;
}

.picture-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-3);
  border-top: 2px dashed var(--border-light);
}

.picture-size,
.picture-dimensions {
  font-size: var(--text-xs);
  color: var(--text-secondary);
  font-weight: 600;
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .picture-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: var(--space-4);
  }

  .action-btn {
    height: 36px;
    font-size: 10px;
  }

  .picture-info {
    padding: var(--space-3);
  }

  .picture-name {
    font-size: var(--text-sm);
  }
}
</style>
