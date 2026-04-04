<template>
  <div id="userCenterPage">
    <div class="user-center-container">
      <!-- 左侧菜单 -->
      <div class="left-sider">
        <a-menu v-model:selectedKeys="currentMenu" :items="menuItems" @click="handleMenuClick" />
      </div>

      <!-- 右侧内容 -->
      <div class="right-content">
        <!-- 个人资料 -->
        <div v-if="currentMenu[0] === 'profile'" class="content-section">
          <h2>个人资料</h2>
          <!-- 头像 -->
          <div class="avatar-section">
            <a-avatar :src="loginUser.userAvatar" :size="80" />
            <a-button @click="showAvatarModal">更换头像</a-button>
          </div>
          <!-- 昵称 -->
          <div class="field-item">
            <span class="label">昵称</span>
            <div v-if="!editingNickname" class="value" @click="startEditNickname">
              {{ loginUser.userName }}
            </div>
            <div v-else class="edit-input">
              <a-input v-model:value="editForm.userName" />
              <a-button @click="cancelEditNickname">取消</a-button>
              <a-button type="primary" @click="saveNickname">保存</a-button>
            </div>
          </div>
          <!-- 简介 -->
          <div class="field-item">
            <span class="label">简介</span>
            <div v-if="!editingProfile" class="value" @click="startEditProfile">
              {{ loginUser.userProfile || '暂无简介' }}
            </div>
            <div v-else class="edit-input">
              <a-input v-model:value="editForm.userProfile" />
              <a-button @click="cancelEditProfile">取消</a-button>
              <a-button type="primary" @click="saveProfile">保存</a-button>
            </div>
          </div>
        </div>

        <!-- 我的图片 -->
        <div v-if="currentMenu[0] === 'pictures'" class="content-section">
          <h2>我的图片</h2>
          <!-- 统计卡片 -->
          <div class="stats-cards">
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.uploadCount }}</div>
              <div class="stat-label">上传数量</div>
            </a-card>
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.likeCount }}</div>
              <div class="stat-label">收藏数量</div>
            </a-card>
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.reviewPassRate }}%</div>
              <div class="stat-label">审核通过率</div>
            </a-card>
          </div>
          <!-- 最近图片 -->
          <div class="recent-pictures">
            <h3>最近上传</h3>
            <div class="picture-grid">
              <img v-for="pic in recentPictures" :key="pic.id"
                   :src="pic.thumbnailUrl || pic.url"
                   @click="goToPictureDetail(pic.id)" />
            </div>
          </div>
        </div>

        <!-- 会员权益 -->
        <div v-if="currentMenu[0] === 'vip'" class="content-section">
          <h2>会员权益</h2>
          <a-card class="vip-card">
            <div class="vip-badge">{{ isVip ? 'VIP' : '普通用户' }}</div>
            <div class="vip-expire">到期时间：{{ vipExpireTime || '永久' }}</div>
          </a-card>
        </div>

        <!-- 意见反馈 -->
        <div v-if="currentMenu[0] === 'feedback'" class="content-section">
          <h2>意见反馈</h2>
          <p class="feedback-subtitle">您的问题和建议是我们改进的动力</p>

          <!-- 反馈类型 -->
          <div class="form-section">
            <label class="form-label">反馈类型</label>
            <div class="type-cards">
              <div
                v-for="type in feedbackTypeOptions"
                :key="type.value"
                class="type-card"
                :class="{ active: feedbackForm.type === type.value }"
                @click="feedbackForm.type = type.value"
              >
                <span class="type-icon">{{ type.icon }}</span>
                <span class="type-label">{{ type.label }}</span>
              </div>
            </div>
          </div>

          <!-- 标题 -->
          <div class="form-section">
            <label class="form-label">标题</label>
            <a-input
              v-model:value="feedbackForm.title"
              placeholder="请简要描述您的问题"
              :maxlength="50"
              show-count
            />
          </div>

          <!-- 详细描述 -->
          <div class="form-section">
            <label class="form-label">详细描述</label>
            <a-textarea
              v-model:value="feedbackForm.content"
              placeholder="请详细描述您的问题或建议..."
              :rows="6"
              :maxlength="2000"
              show-count
            />
          </div>

          <!-- 附件截图 -->
          <div class="form-section">
            <label class="form-label">附件截图（可选，最多5张）</label>
            <div class="upload-list">
              <div
                v-for="(url, index) in feedbackForm.pictureUrls"
                :key="index"
                class="upload-item"
              >
                <img :src="url" alt="附件" class="upload-preview" />
                <span class="upload-remove" @click="removeFeedbackPicture(index)">x</span>
              </div>
              <div v-if="feedbackForm.pictureUrls.length < 5" class="upload-trigger" @click="triggerFeedbackUpload">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 5v14M5 12h14" />
                </svg>
                <span>上传图片</span>
              </div>
            </div>
            <input
              ref="feedbackFileInputRef"
              type="file"
              accept="image/*"
              multiple
              style="display: none"
              @change="handleFeedbackFileChange"
            />
          </div>

          <!-- 提交 -->
          <div class="form-actions">
            <a-button
              type="primary"
              size="large"
              :loading="feedbackSubmitting"
              @click="doFeedbackSubmit"
            >
              提交反馈
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'
import { getUserPictureStatsUsingGet, getUserRecentPicturesUsingGet, updateUserUsingPost } from '@/api/userController'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

const currentMenu = ref(['profile'])
const menuItems = [
  { key: 'profile', label: '个人资料' },
  { key: 'pictures', label: '我的图片' },
  { key: 'vip', label: '会员权益' },
  { key: 'feedback', label: '意见反馈' }
]

// 编辑状态
const editingNickname = ref(false)
const editingProfile = ref(false)
const editForm = ref({
  userName: '',
  userProfile: ''
})

// 图片统计
const pictureStats = ref({
  uploadCount: 0,
  likeCount: 0,
  reviewPassCount: 0,
  reviewPassRate: 0
})

// 最近图片
const recentPictures = ref<any[]>([])

// 会员状态（mock）
const isVip = ref(false)
const vipExpireTime = ref('2026-12-31')

// 加载数据
const loadPictureStats = async () => {
  try {
    const res = await getUserPictureStatsUsingGet()
    if (res.data.code === 0) {
      pictureStats.value = res.data.data
    }
  } catch (e) {
    console.error('加载图片统计失败', e)
  }
}

const loadRecentPictures = async () => {
  try {
    const res = await getUserRecentPicturesUsingGet(6)
    if (res.data.code === 0) {
      recentPictures.value = res.data.data
    }
  } catch (e) {
    console.error('加载最近图片失败', e)
  }
}

// 菜单切换
const handleMenuClick = ({ key }: { key: string }) => {
  if (key === 'pictures') {
    loadPictureStats()
    loadRecentPictures()
  }
}

// 编辑昵称
const startEditNickname = () => {
  editForm.value.userName = loginUser.userName
  editingNickname.value = true
}
const cancelEditNickname = () => {
  editingNickname.value = false
}
const saveNickname = async () => {
  try {
    const res = await updateUserUsingPost({ id: loginUser.id, userName: editForm.value.userName })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, userName: editForm.value.userName })
      message.success('昵称修改成功')
      editingNickname.value = false
    }
  } catch (e) {
    message.error('修改失败')
  }
}

// 编辑简介
const startEditProfile = () => {
  editForm.value.userProfile = loginUser.userProfile
  editingProfile.value = true
}
const cancelEditProfile = () => {
  editingProfile.value = false
}
const saveProfile = async () => {
  try {
    const res = await updateUserUsingPost({ id: loginUser.id, userProfile: editForm.value.userProfile })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, userProfile: editForm.value.userProfile })
      message.success('简介修改成功')
      editingProfile.value = false
    }
  } catch (e) {
    message.error('修改失败')
  }
}

// 跳转图片详情
const goToPictureDetail = (id: number) => {
  router.push(`/picture/${id}`)
}

// 头像上传（待实现）
const showAvatarModal = () => {
  message.info('头像上传功能待实现')
}

// ========== 意见反馈 ==========
import { addFeedbackUsingPost, uploadFeedbackAttachment } from '@/api/feedbackController'

const feedbackTypeOptions = [
  { value: 1, label: '产品建议', icon: '💡' },
  { value: 2, label: '内容举报', icon: '🚩' },
  { value: 3, label: '工单支持', icon: '🎫' },
]

const feedbackForm = ref({
  type: 1,
  title: '',
  content: '',
  pictureUrls: [] as string[],
})

const feedbackSubmitting = ref(false)
const feedbackFileInputRef = ref<HTMLInputElement | null>(null)

const triggerFeedbackUpload = () => {
  feedbackFileInputRef.value?.click()
}

const handleFeedbackFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files) return

  const maxUploads = Math.min(files.length, 5 - feedbackForm.value.pictureUrls.length)
  const uploadPromises: Promise<void>[] = []

  for (let i = 0; i < maxUploads; i++) {
    const file = files[i]
    uploadPromises.push(
      (async () => {
        try {
          const res = await uploadFeedbackAttachment(file)
          if (res.data.code === 0 && res.data.data) {
            feedbackForm.value.pictureUrls.push(res.data.data)
          }
        } catch (err: any) {
          message.error(err.response?.data?.message || '图片上传失败')
        }
      })()
    )
  }

  await Promise.all(uploadPromises)
  // 清空 input 以便重复选择同一文件
  ;(e.target as HTMLInputElement).value = ''
}

const removeFeedbackPicture = (index: number) => {
  feedbackForm.value.pictureUrls.splice(index, 1)
}

const doFeedbackSubmit = async () => {
  if (!feedbackForm.value.title.trim()) {
    message.warning('请输入标题')
    return
  }
  if (!feedbackForm.value.content.trim()) {
    message.warning('请输入详细描述')
    return
  }

  feedbackSubmitting.value = true
  try {
    const res = await addFeedbackUsingPost({
      type: feedbackForm.value.type,
      title: feedbackForm.value.title,
      content: feedbackForm.value.content,
      pictureUrls: feedbackForm.value.pictureUrls,
    })
    if (res.data.code === 0) {
      message.success('反馈提交成功！感谢您的宝贵意见')
      // 重置表单
      feedbackForm.value = { type: 1, title: '', content: '', pictureUrls: [] }
    } else {
      message.error(res.data.message || '提交失败')
    }
  } catch (err) {
    message.error('提交失败，请重试')
  } finally {
    feedbackSubmitting.value = false
  }
}

</script>

<style scoped>
#userCenterPage {
  min-height: 100vh;
  background: var(--color-bg-primary);
  padding: var(--space-6);
}

.user-center-container {
  display: flex;
  gap: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}

.left-sider {
  width: 200px;
  flex-shrink: 0;
}

.right-content {
  flex: 1;
  min-width: 0;
}

.content-section {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl);
  padding: var(--space-8);
}

.content-section h2 {
  font-size: var(--text-2xl);
  font-weight: 700;
  margin-bottom: var(--space-6);
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.field-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.field-item .label {
  width: 80px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.field-item .value {
  flex: 1;
  cursor: pointer;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}

.field-item .value:hover {
  background: var(--color-bg-hover);
}

.edit-input {
  flex: 1;
  display: flex;
  gap: var(--space-2);
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: 700;
  color: var(--color-primary);
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin-top: var(--space-2);
}

.recent-pictures h3 {
  font-size: var(--text-lg);
  font-weight: 600;
  margin-bottom: var(--space-4);
}

.picture-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.picture-grid img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform var(--transition-fast);
}

.picture-grid img:hover {
  transform: scale(1.05);
}

.vip-card {
  text-align: center;
  padding: var(--space-6);
}

.vip-badge {
  display: inline-block;
  font-size: var(--text-2xl);
  font-weight: 700;
  padding: var(--space-2) var(--space-6);
  background: linear-gradient(135deg, var(--color-primary), var(--color-secondary));
  color: white;
  border-radius: var(--radius-full);
  margin-bottom: var(--space-4);
}

.vip-expire {
  color: var(--color-text-tertiary);
}

/* ========== 意见反馈 ========== */
.feedback-subtitle {
  color: var(--color-text-tertiary);
  margin-bottom: var(--space-6);
}

.type-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-3);
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  background: var(--color-bg-tertiary);
  border: 2px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.2s;
}

.type-card:hover {
  border-color: var(--color-primary);
}

.type-card.active {
  border-color: var(--color-primary);
  background: rgba(168, 85, 247, 0.1);
}

.type-icon {
  font-size: 1.5rem;
}

.type-label {
  font-weight: 600;
  color: var(--color-text-primary);
  font-size: var(--text-sm);
}

.form-section {
  margin-bottom: var(--space-5);
}

.form-label {
  display: block;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
}

.upload-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.upload-item {
  position: relative;
  width: 100px;
  height: 100px;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.upload-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
}

.upload-trigger {
  width: 100px;
  height: 100px;
  border: 2px dashed var(--color-border-default);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}

.upload-trigger:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.upload-trigger svg {
  width: 24px;
  height: 24px;
}

.upload-trigger span {
  font-size: 12px;
}

.form-actions {
  display: flex;
  justify-content: center;
  margin-top: var(--space-6);
}
</style>
