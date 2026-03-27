<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="cropper-modal-overlay" @click.self="closeModal">
        <div class="cropper-modal">
          <!-- 背景光效 -->
          <div class="modal-glow"></div>

          <!-- 头部 -->
          <div class="modal-header">
            <div class="header-title">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="6" cy="6" r="3"/>
                <circle cx="6" cy="18" r="3"/>
                <line x1="20" y1="4" x2="8.12" y2="15.88"/>
                <line x1="14.47" y1="14.48" x2="20" y2="20"/>
                <line x1="8.12" y1="8.12" x2="12" y2="12"/>
              </svg>
              <span>裁剪编辑</span>
            </div>
            <button class="close-btn" @click="closeModal">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>

          <!-- 裁剪区域 -->
          <div class="cropper-container">
            <vue-cropper
              ref="cropperRef"
              :img="imageUrl"
              output-type="png"
              :info="true"
              :can-move="true"
              :can-move-box="true"
              :fixed-box="false"
              :auto-crop="true"
              :center-box="true"
            />
          </div>

          <!-- 操作工具栏 -->
          <div class="cropper-toolbar">
            <div class="toolbar-group">
              <button class="tool-btn" @click="rotateLeft" :disabled="!canEdit" title="向左旋转">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M2.5 2v6h6"/>
                  <path d="M2.5 8a10 10 0 1 1 1.5 6"/>
                </svg>
                <span>左旋</span>
              </button>
              <button class="tool-btn" @click="rotateRight" :disabled="!canEdit" title="向右旋转">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21.5 2v6h-6"/>
                  <path d="M21.5 8a10 10 0 1 0-1.5 6"/>
                </svg>
                <span>右旋</span>
              </button>
              <button class="tool-btn" @click="changeScale(1)" :disabled="!canEdit" title="放大">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  <line x1="11" y1="8" x2="11" y2="14"/>
                  <line x1="8" y1="11" x2="14" y2="11"/>
                </svg>
                <span>放大</span>
              </button>
              <button class="tool-btn" @click="changeScale(-1)" :disabled="!canEdit" title="缩小">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"/>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  <line x1="8" y1="11" x2="14" y2="11"/>
                </svg>
                <span>缩小</span>
              </button>
            </div>

            <!-- 团队空间协作状态 -->
            <div class="collab-status" v-if="isTeamSpace">
              <div class="status-indicator" :class="{ editing: editingUser }"></div>
              <span v-if="editingUser">{{ editingUser.userName }} 正在编辑</span>
              <span v-else>可进入编辑</span>
            </div>
          </div>

          <!-- 协作操作 -->
          <div class="collab-actions" v-if="isTeamSpace">
            <button
              v-if="canEnterEdit && !editingUser"
              class="action-btn secondary"
              @click="enterEdit"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              <span>进入编辑</span>
            </button>
            <button
              v-if="canExistEdit"
              class="action-btn danger"
              @click="exitEdit"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                <polyline points="16 17 21 12 16 7"/>
                <line x1="21" y1="12" x2="9" y2="12"/>
              </svg>
              <span>退出编辑</span>
            </button>
          </div>

          <!-- 底部操作按钮 -->
          <div class="modal-footer">
            <button class="action-btn outline" @click="closeModal">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
              <span>取消</span>
            </button>
            <button
              class="action-btn primary"
              @click="handleConfirm"
              :disabled="!canEdit || loading"
            >
              <svg v-if="!loading" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <svg v-else class="spinner" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" stroke-opacity="0.3"/>
                <path d="M12 2a10 10 0 0 1 10 10"/>
              </svg>
              <span>{{ loading ? '处理中...' : '确认裁剪' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebSocket.ts'
import { PICTURE_EDIT_ACTION_ENUM, PICTURE_EDIT_MESSAGE_TYPE_ENUM } from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

interface Props {
  imageUrl?: string
  picture: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  space?: API.SpaceVO
  spaceId?: number
}

const props = defineProps<Props>()

const visible = ref<boolean>(false)

// 是否为团队空间
const isTeamSpace = computed(() => {
  return props.space?.spaceType === SPACE_TYPE_ENUM.TEAM
})

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

const cropperRef = ref()

// 图片缩放
const changeScale = (num: number) => {
  cropperRef.value?.changeScale(num)
  if (num > 0) {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_IN)
  } else {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
  }
}

// 向左旋转
const rotateLeft = () => {
  cropperRef.value?.rotateLeft()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}

// 向右旋转
const rotateRight = () => {
  cropperRef.value?.rotateRight()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
}

// 确认裁切
const handleConfirm = () => {
  cropperRef.value?.getCropBlob((blob: Blob) => {
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    handleUpload({ file })
  })
}

const loading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    if (props.spaceId) {
      params.spaceId = props.spaceId
    }
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片裁剪成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    }
  } catch (err: any) {
    message.error('图片上传失败：' + err.message)
  }
  loading.value = false
}

//---------------实时编辑----------------
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

//正在编辑的用户
const editingUser = ref<API.UserVO>()

//当前用户是否可以进入编辑
const canEnterEdit = computed(() => {
  return !editingUser.value
})

//正在编辑的用户是本人，可以退出编辑
const canExistEdit = computed(() => {
  return editingUser.value?.id === loginUser.id
})

//可以点击编辑图片的操作按钮
const canEdit = computed(() => {
  if (!isTeamSpace.value) {
    return true
  }
  return editingUser.value?.id === loginUser.id
})

//编写webSocket逻辑
let websocket: PictureEditWebSocket | null

//初始化websocket的连接，绑定监听事件
const initWebsocket = () => {
  const pictureId = props.picture?.id
  if (!pictureId || !visible.value) {
    return
  }
  if (websocket) {
    websocket.disconnect()
  }
  websocket = new PictureEditWebSocket(pictureId)
  websocket.connect()

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => {
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => {
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
    editingUser.value = msg.user
    message.info(msg.message)
    switch (msg.message) {
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT:
        rotateLeft()
        break
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT:
        rotateRight()
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN:
        changeScale(1)
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT:
        changeScale(-1)
        break
    }
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
    message.info(msg.message)
    editingUser.value = msg.user
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
    message.info(msg.message)
    editingUser.value = undefined
  })
}

watch(visible, () => {
  if (isTeamSpace.value) initWebsocket()
})

onUnmounted(() => {
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
})

//进入编辑状态
const enterEdit = () => {
  if (websocket) {
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT,
    })
  }
}

//退出编辑状态
const exitEdit = () => {
  if (websocket) {
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT,
    })
  }
}

//编辑图片操作
const editAction = (action: string) => {
  if (websocket) {
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION,
      editAction: action,
    })
  }
}
</script>

<style scoped lang="less">
/* ========== Aurora (Dark) Theme ========== */
[data-theme="aurora"] {
  --modal-bg: rgba(15, 23, 42, 0.95);
  --modal-border: rgba(255, 255, 255, 0.1);
  --modal-glow-color: rgba(168, 85, 247, 0.12);
  --modal-title-color: white;
  --modal-text-color: rgba(255, 255, 255, 0.7);
  --modal-text-secondary: rgba(255, 255, 255, 0.5);
  --modal-bg-hover: rgba(255, 255, 255, 0.06);
  --modal-bg-active: rgba(255, 255, 255, 0.1);
  --modal-bg-section: rgba(0, 0, 0, 0.3);
  --modal-border-subtle: rgba(255, 255, 255, 0.06);
  --modal-btn-hover: rgba(168, 85, 247, 0.2);
  --modal-btn-border: rgba(168, 85, 247, 0.4);
  --accent-primary: #a855f7;
  --accent-secondary: #f472b6;
  --accent-info: #60a5fa;
  --accent-danger: #f87171;
  --shadow-modal: 0 0 0 1px rgba(168, 85, 247, 0.2), 0 25px 80px rgba(0, 0, 0, 0.6), 0 0 100px rgba(168, 85, 247, 0.15);
}

/* ========== Pop (Light) Theme ========== */
[data-theme="pop"] {
  --modal-bg: #ffffff;
  --modal-border: var(--border-light);
  --modal-glow-color: rgba(155, 93, 229, 0.08);
  --modal-title-color: var(--text-primary);
  --modal-text-color: var(--text-secondary);
  --modal-text-secondary: var(--text-tertiary);
  --modal-bg-hover: var(--bg-hover);
  --modal-bg-active: var(--bg-active);
  --modal-bg-section: var(--bg-tertiary);
  --modal-border-subtle: var(--border-light);
  --modal-btn-hover: rgba(155, 93, 229, 0.1);
  --modal-btn-border: rgba(155, 93, 229, 0.3);
  --accent-primary: var(--color-grape);
  --accent-secondary: var(--color-pink);
  --accent-info: var(--color-sky);
  --accent-danger: var(--color-coral);
  --shadow-modal: 0 4px 30px rgba(0, 0, 0, 0.12), 0 0 0 1px var(--border-light);
}

/* ========== Modal 遮罩 ========== */
.cropper-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(12px);
}

/* ========== Modal 主体 ========== */
.cropper-modal {
  position: relative;
  width: 100%;
  max-width: 720px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: var(--modal-bg);
  border: 1px solid var(--modal-border);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}

.modal-glow {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: radial-gradient(circle at 50% 0%, var(--modal-glow-color) 0%, transparent 50%);
  pointer-events: none;
}

/* ========== 头部 ========== */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--modal-border-subtle);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: 700;
  color: var(--modal-title-color);

  svg {
    color: var(--accent-primary);
  }
}

.close-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--modal-bg-hover);
  border: 1px solid var(--modal-border);
  border-radius: 10px;
  color: var(--modal-text-color);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: var(--accent-danger);
    border-color: var(--accent-danger);
    color: white;
  }
}

/* ========== 裁剪容器 ========== */
.cropper-container {
  flex: 1;
  min-height: 400px;
  padding: 20px;
  background: var(--modal-bg-section);

  :deep(.vue-cropper) {
    height: 400px !important;
  }
}

/* ========== 工具栏 ========== */
.cropper-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 16px 24px;
  border-top: 1px solid var(--modal-border-subtle);
  border-bottom: 1px solid var(--modal-border-subtle);
}

.toolbar-group {
  display: flex;
  gap: 8px;
}

.tool-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 16px;
  background: var(--modal-bg-hover);
  border: 1px solid var(--modal-border);
  border-radius: 12px;
  color: var(--modal-text-color);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  svg {
    transition: transform 0.3s ease;
  }

  &:hover:not(:disabled) {
    background: var(--modal-btn-hover);
    border-color: var(--modal-btn-border);
    color: var(--modal-title-color);

    svg {
      transform: scale(1.1);
    }
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.collab-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--modal-bg-hover);
  border-radius: 20px;
  font-size: 13px;
  color: var(--modal-text-color);
}

.status-indicator {
  width: 8px;
  height: 8px;
  background: var(--modal-text-secondary);
  border-radius: 50%;

  &.editing {
    background: var(--accent-secondary);
    animation: pulse-status 1.5s ease-in-out infinite;
  }
}

@keyframes pulse-status {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.8); }
}

/* ========== 协作操作 ========== */
.collab-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--modal-border-subtle);
}

/* ========== 底部操作 ========== */
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
}

/* ========== 按钮样式 ========== */
.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &.primary {
    background: var(--accent-primary);
    border: none;
    color: white;
    box-shadow: 0 4px 20px color-mix(in srgb, var(--accent-primary) 40%, transparent);

    &:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 6px 30px color-mix(in srgb, var(--accent-primary) 50%, transparent);
    }
  }

  &.secondary {
    background: color-mix(in srgb, var(--accent-info) 15%, transparent);
    border: 1px solid color-mix(in srgb, var(--accent-info) 30%, transparent);
    color: var(--accent-info);

    &:hover {
      background: color-mix(in srgb, var(--accent-info) 25%, transparent);
      border-color: color-mix(in srgb, var(--accent-info) 50%, transparent);
    }
  }

  &.danger {
    background: color-mix(in srgb, var(--accent-danger) 15%, transparent);
    border: 1px solid color-mix(in srgb, var(--accent-danger) 30%, transparent);
    color: var(--accent-danger);

    &:hover {
      background: color-mix(in srgb, var(--accent-danger) 25%, transparent);
      border-color: color-mix(in srgb, var(--accent-danger) 50%, transparent);
    }
  }

  &.outline {
    background: var(--modal-bg-hover);
    border: 1px solid var(--modal-border);
    color: var(--modal-text-color);

    &:hover {
      background: var(--modal-bg-active);
      border-color: var(--modal-text-secondary);
    }
  }
}

.spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== 过渡动画 ========== */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;

  .cropper-modal {
    transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  }
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;

  .cropper-modal {
    opacity: 0;
    transform: scale(0.95) translateY(20px);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .cropper-modal-overlay {
    padding: 12px;
  }

  .cropper-modal {
    max-height: 85vh;
  }

  .modal-header {
    padding: 16px 20px;
  }

  .cropper-container {
    min-height: 300px;
    padding: 12px;

    :deep(.vue-cropper) {
      height: 300px !important;
    }
  }

  .cropper-toolbar {
    flex-wrap: wrap;
    gap: 12px;
    padding: 12px 16px;
  }

  .tool-btn {
    padding: 8px 12px;

    span {
      display: none;
    }
  }

  .modal-footer {
    padding: 16px 20px;
  }

  .action-btn {
    padding: 10px 16px;
    font-size: 13px;
  }
}
</style>
