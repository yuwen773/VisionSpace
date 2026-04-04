<template>
  <div class="glass-section">
    <h2 class="section-title">我的图片</h2>

    <!-- Stats -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-value text-gradient">{{ pictureStats.uploadCount ?? 0 }}</div>
        <div class="stat-label">上传数量</div>
        <div class="stat-bar bar-upload" />
      </div>
      <div class="stat-card">
        <div class="stat-value text-gradient-pink">{{ pictureStats.likeCount ?? 0 }}</div>
        <div class="stat-label">收藏数量</div>
        <div class="stat-bar bar-like" />
      </div>
      <div class="stat-card">
        <div class="stat-value text-gradient-violet">{{ pictureStats.reviewPassRate ?? 0 }}%</div>
        <div class="stat-label">审核通过率</div>
        <div class="stat-bar bar-pass" />
      </div>
    </div>

    <!-- Recent Pictures -->
    <div class="recent-section">
      <h3 class="recent-title">最近上传</h3>
      <a-spin :spinning="loading">
        <div v-if="recentPictures.length > 0" class="picture-grid">
          <div
            v-for="pic in recentPictures"
            :key="pic.id"
            class="picture-item"
            @click="$emit('goToPicture', pic.id!)"
          >
            <img :src="pic.thumbnailUrl || pic.url" :alt="pic.name" />
            <div class="picture-overlay">
              <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            </div>
          </div>
        </div>
        <a-empty v-else description="暂无上传记录" />
      </a-spin>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUserPictureStatsUsingGet, getUserRecentPicturesUsingGet } from '@/api/userController'

defineEmits<{
  goToPicture: [id: number]
}>()

const pictureStats = ref<API.UserPictureStatsResponse>({
  uploadCount: 0,
  likeCount: 0,
  reviewPassRate: 0
})

const recentPictures = ref<API.RecentPictureVO[]>([])
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const [statsRes, picturesRes] = await Promise.all([
      getUserPictureStatsUsingGet(),
      getUserRecentPicturesUsingGet(6)
    ])
    if (statsRes.data.code === 0) {
      pictureStats.value = statsRes.data.data ?? pictureStats.value
    }
    if (picturesRes.data.code === 0) {
      recentPictures.value = picturesRes.data.data ?? []
    }
  } catch (e) {
    // 静默处理，已展示空状态
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
  margin-bottom: var(--space-8);
}

.stat-card {
  text-align: center;
  padding: var(--space-5) var(--space-4);
  background: var(--bg-secondary);
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-subtle);
  position: relative;
  overflow: hidden;
  transition: all var(--transition-base);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-glow-purple);
  border-color: var(--border-accent);
}

.stat-value {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 700;
  line-height: 1;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  margin-top: var(--space-2);
  font-weight: 600;
}

.stat-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 0 0 var(--radius-xl) var(--radius-xl);
}

.bar-upload {
  background: var(--gradient-sunset);
}

.bar-like {
  background: var(--gradient-rose);
}

.bar-pass {
  background: var(--gradient-violet);
}

/* Recent Pictures */
.recent-title {
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--space-4);
}

.picture-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}

.picture-item {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-xl);
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--border-subtle);
  transition: all var(--transition-base);
}

.picture-item:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-glow-purple);
  border-color: var(--border-accent);
}

.picture-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.picture-overlay {
  position: absolute;
  inset: 0;
  background: rgba(139, 92, 246, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.picture-overlay svg {
  width: 28px;
  height: 28px;
}

.picture-item:hover .picture-overlay {
  opacity: 1;
}

/* Responsive */
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
  }

  .picture-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .picture-grid {
    grid-template-columns: 1fr;
  }
}
</style>
