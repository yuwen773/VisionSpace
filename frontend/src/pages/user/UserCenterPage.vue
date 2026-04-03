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
  { key: 'vip', label: '会员权益' }
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
</style>
