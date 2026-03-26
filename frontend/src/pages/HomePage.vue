<template>
  <div id="homePage">
    <!-- Hero 区域 -->
    <div class="hero-section">
      <div class="hero-bg">
        <div class="hero-shape shape-sun"></div>
        <div class="hero-shape shape-star"></div>
        <div class="hero-shape shape-cloud"></div>
      </div>

      <div class="hero-content">
        <div class="hero-badge animate-bounce">
          <span class="badge-icon">🎨</span>
          <span>创意无限</span>
        </div>
        <h1 class="hero-title">
          <span class="title-line title-coral">发现</span>
          <span class="title-line title-sunshine">精彩瞬间</span>
          <span class="title-line title-mint">分享灵感</span>
        </h1>
        <p class="hero-description">
          📸 捕捉美好瞬间，💾 安全云端存储，🤝 与团队协同创作
        </p>

        <!-- 搜索框 -->
        <div class="search-container animate-bounce">
          <div class="search-wrapper pop-card">
            <a-input
              v-model:value="searchParams.searchText"
              placeholder="🔍 搜索图片名称、简介或标签..."
              size="large"
              class="search-input"
              @search="doSearch"
            >
            </a-input>
            <button @click="doSearch" class="search-button pop-btn primary">
              <span>搜索</span>
              <span class="btn-icon">→</span>
            </button>
          </div>
        </div>

        <!-- 统计信息 -->
        <div class="hero-stats">
          <div class="stat-item stat-coral animate-float">
            <span class="stat-number">{{ total }}+</span>
            <span class="stat-label">图片</span>
          </div>
          <div class="stat-item stat-sunshine animate-float" style="animation-delay: 0.5s">
            <span class="stat-number">∞</span>
            <span class="stat-label">存储</span>
          </div>
          <div class="stat-item stat-mint animate-float" style="animation-delay: 1s">
            <span class="stat-number">∞</span>
            <span class="stat-label">创意</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分类标签筛选 -->
    <div class="filter-section">
      <div class="filter-tabs pop-card">
        <a-tabs v-model:activeKey="selectedCategory" @change="doSearch" class="category-tabs">
          <a-tab-pane key="all">
            <template #tab>
              <span class="tab-item">
                <span>🎯</span>
                <span>全部</span>
              </span>
            </template>
          </a-tab-pane>
          <a-tab-pane v-for="category in categoryList" :key="category">
            <template #tab>
              <span class="tab-item">
                <span>📁</span>
                <span>{{ category }}</span>
              </span>
            </template>
          </a-tab-pane>
        </a-tabs>
      </div>

      <!-- 标签筛选 -->
      <div class="filter-tags" v-if="tagList.length > 0">
        <div class="tags-label">
          <span>🏷️</span>
          <span>热门标签</span>
        </div>
        <a-space :size="[0, 12]" wrap class="tags-list">
          <a-checkable-tag
            v-for="(tag, index) in displayTags"
            :key="tag"
            v-model:checked="selectedTag[index]"
            @change="doSearch"
            class="filter-tag"
          >
            {{ tag }}
          </a-checkable-tag>
          <a-tag v-if="hasSelectedTags" @click="cleanTags" class="clear-tag">
            ✕ 清除
          </a-tag>
        </a-space>
      </div>
    </div>

    <!-- 图片列表 -->
    <div class="gallery-section">
      <div class="gallery-header">
        <h2 class="gallery-title">
          <span>🖼️</span>
          <span>图片广场</span>
        </h2>
        <div class="gallery-info">
          <span class="info-text">共 {{ total }} 张精彩图片</span>
        </div>
      </div>

      <PictureList
        :dataList="dataList"
        :loading="loading"
        :showOp="true"
        :canEdit="true"
        :canDelete="true"
        :onReload="fetchData"
      />

      <!-- 分页器 -->
      <div class="pagination-wrapper" v-if="total > 0">
        <a-pagination
          v-model:current="searchParams.current"
          v-model:pageSize="searchParams.pageSize"
          :pageSizeOptions="['12', '24', '48']"
          :total="total"
          :showSizeChanger="true"
          :showQuickJumper="true"
          :showTotal="(total) => `共 ${total} 张`"
          @change="onPageChange"
          class="pop-pagination"
        />
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && dataList.length === 0" class="empty-state">
        <div class="empty-icon">📭</div>
        <h3 class="empty-title">暂无图片</h3>
        <p class="empty-description">
          {{ emptyMessage }}
        </p>
        <a-button type="primary" size="large" @click="goToUpload" v-if="selectedCategory === 'all' && !hasSelectedTags" class="pop-btn primary">
          <span>📸</span>
          <span>上传第一张图片</span>
        </a-button>
        <a-button size="large" @click="resetFilters" v-else class="pop-btn outline">
          <span>🔄</span>
          <span>清除筛选</span>
        </a-button>
      </div>
    </div>

    <!-- 底部波浪 -->
    <div class="wave-section wave-decoration">
      <svg viewBox="0 0 1440 120" preserveAspectRatio="none" style="width: 100%; height: 60px;">
        <path d="M0,64L48,32L96,64L144,32L192,64L240,32L288,64L336,32L384,64L432,32L480,64L528,32L576,64L624,32L672,64L720,32L768,64L816,32L864,64L912,32L960,64L1008,32L1056,64L1104,32L1152,64L1200,32L1248,64L1296,32L1344,64L1392,32L1440,64V120H0V64Z" fill="var(--color-mint)"/>
        <path d="M0,96L48,64L96,96L144,64L192,96L240,64L288,96L336,64L384,96L432,64L480,96L528,64L576,96L624,64L672,96L720,64L768,96L816,64L864,96L912,64L960,96L1008,64L1056,96L1104,64L1152,96L1200,64L1248,96L1296,64L1344,96L1392,64L1440,96V120H0V96Z" fill="var(--color-sky)" opacity="0.4"/>
      </svg>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 24,
  sortOrder: 'descend',
  sortField: 'createTime',
})

const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  fetchData()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const fetchData = async () => {
  loading.value = true
  const params = {
    ...searchParams,
    tags: [] as string[],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  selectedTag.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(tagList.value[index])
    }
  })

  try {
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0)
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
  getTagCategoryOptions()
})

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const categoryList = ref<string[]>([])
const tagList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const selectedTag = ref<boolean[]>([])

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      categoryList.value = res.data.data.categoryList ?? []
      tagList.value = res.data.data.tagList ?? []
      selectedTag.value = new Array(tagList.value.length).fill(false)
    }
  } catch (error) {
    message.error('获取标签分类列表失败')
  }
}

const cleanTags = () => {
  if (selectedTag.value.length == 0) return
  selectedTag.value = []
  fetchData()
}

const hasSelectedTags = computed(() => selectedTag.value.some((tag) => tag))

const displayTags = computed(() => tagList.value.slice(0, 12))

const emptyMessage = computed(() => {
  if (selectedCategory.value !== 'all' || hasSelectedTags.value) {
    return '没有找到匹配的图片，试试其他筛选条件吧'
  }
  return '开始上传你的第一张图片，开启创意之旅！'
})

const resetFilters = () => {
  selectedCategory.value = 'all'
  selectedTag.value = []
  searchParams.searchText = ''
  doSearch()
}

const goToUpload = () => {
  router.push('/add_picture')
}
</script>

<style scoped lang="less">
#homePage {
  min-height: 100vh;
  background: var(--bg-primary);
  width: 100%;
  position: relative;
}

/* ========== Hero 区域 ========== */
.hero-section {
  position: relative;
  padding: var(--space-16) var(--space-6) var(--space-12);
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.hero-shape {
  position: absolute;
  opacity: 0.08;
}

.shape-sun {
  width: 200px;
  height: 200px;
  background: var(--color-sunshine);
  border-radius: 50%;
  top: -50px;
  right: 10%;
}

.shape-star {
  width: 100px;
  height: 100px;
  background: var(--color-coral);
  clip-path: polygon(50% 0%, 61% 35%, 98% 38%, 79% 59%, 20% 68%, 2% 37%, 39% 23%, 75% 6%, 74% 98%, 23% 89%, 58% 69%, 21% 100%, 6% 77%, 75% 26%, 35% 7%, 100% 0%);
  top: 100px;
  left: 5%;
}

.shape-cloud {
  width: 150px;
  height: 80px;
  background: var(--color-sky);
  border-radius: 50%;
  top: 50%;
  left: -30px;
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  background: var(--color-off-white);
  border: 3px solid var(--border-bold);
  border-radius: var(--radius-full);
  font-weight: 700;
  font-size: var(--text-sm);
  color: var(--text-primary);
  margin-bottom: var(--space-6);
}

.badge-icon {
  font-size: var(--text-xl);
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(36px, 8vw, 64px);
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 var(--space-6) 0;
}

.title-line {
  display: block;
  transition: transform var(--transition-bounce);

  &:hover {
    transform: scale(1.1) rotate(-2deg);
  }
}

.title-coral {
  color: var(--color-coral);
  text-shadow: 2px 2px 0 var(--color-coral-light);
}

.title-sunshine {
  color: #F4C430;
  text-shadow: 2px 2px 0 var(--color-sunshine-light);
}

.title-mint {
  color: var(--color-mint);
  text-shadow: 2px 2px 0 var(--color-mint-light);
}

.hero-description {
  font-size: var(--text-lg);
  color: var(--text-secondary);
  margin-bottom: var(--space-10);
  font-weight: 600;
}

/* ========== 搜索框 ========== */
.search-container {
  margin-bottom: var(--space-10);
}

.search-wrapper {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-2);

  .search-input {
    flex: 1;

    :deep(.ant-input) {
      height: 56px;
      border-radius: var(--radius-2xl);
      border: 3px solid var(--border-bold);
      font-size: var(--text-lg);
      font-weight: 600;
      background: var(--bg-secondary);
      transition: all var(--transition-bounce);

      &:hover {
        border-color: var(--color-sunshine);
        transform: translate(-2px, -2px);
        box-shadow: 4px 4px 0 rgba(255, 217, 61, 0.2);
      }

      &:focus,
      &:focus-within {
        border-color: var(--color-sky);
        background: var(--bg-secondary);
        box-shadow: 4px 4px 0 rgba(77, 150, 255, 0.2);
        transform: translate(-1px, -1px);
      }
    }
  }

  .search-button {
    width: 120px;
  }
}

/* ========== 统计信息 ========== */
.hero-stats {
  display: flex;
  justify-content: center;
  gap: var(--space-8);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-6);
  background: var(--bg-secondary);
  border: 3px solid var(--border-bold);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-pop);
  transition: all var(--transition-bounce);

  &:hover {
    transform: translateY(-4px);
    box-shadow: 6px 6px 0 rgba(0, 0, 0, 0.15);
  }
}

.stat-number {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 800;
}

.stat-label {
  font-size: var(--text-sm);
  font-weight: 600;
  text-transform: uppercase;
}

.stat-coral { color: var(--color-coral); }
.stat-sunshine { color: #F4C430; }
.stat-mint { color: var(--color-mint); }

/* ========== 筛选区域 ========== */
.filter-section {
  max-width: var(--container-xl);
  margin: 0 auto var(--space-8);
  padding: 0 var(--space-6);
}

.filter-tabs {
  padding: var(--space-2);
  margin-bottom: var(--space-6);
}

.category-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: 0;
  }

  :deep(.ant-tabs-tab) {
    padding: var(--space-3) var(--space-5);
    border-radius: var(--radius-full);
    font-weight: 700;
    background: var(--bg-secondary);
    border: 3px solid var(--border-light);

    &:hover {
      background: var(--bg-hover);
    }
  }

  :deep(.ant-tabs-tab-active) {
    background: var(--color-sky);
    border-color: var(--color-sky);
    color: white;
  }
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);

  span:first-child {
    font-size: var(--text-xl);
  }
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--bg-secondary);
  border: 3px solid var(--border-bold);
  border-radius: var(--radius-2xl);
}

.tags-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: 700;
  font-size: var(--text-base);
}

.tags-list {
  flex: 1;
}

.filter-tag {
  font-weight: 600;
  padding: 6px 14px;
}

.clear-tag {
  cursor: pointer;
  font-weight: 700;
  background: var(--color-coral-light);
  border-color: var(--color-coral);

  &:hover {
    background: var(--color-coral);
  }
}

/* ========== 画廊区域 ========== */
.gallery-section {
  max-width: var(--container-xl);
  margin: 0 auto var(--space-12);
  padding: 0 var(--space-6);
}

.gallery-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-8);
}

.gallery-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 800;
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.info-text {
  font-size: var(--text-base);
  color: var(--text-secondary);
  font-weight: 600;
}

/* ========== 分页器 ========== */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: var(--space-10);
}

.pop-pagination {
  :deep(.ant-pagination-item) {
    background: var(--bg-secondary);
    border: 3px solid var(--border-bold);
    border-radius: var(--radius-full);
    font-weight: 700;

    &:hover {
      border-color: var(--color-sunshine);
      transform: translateY(-2px);
    }
  }

  :deep(.ant-pagination-item-active) {
    background: var(--gradient-sunset);
    border-color: var(--color-coral-dark);
  }
}

/* ========== 空状态 ========== */
.empty-state {
  text-align: center;
  padding: var(--space-20) var(--space-6);
}

.empty-icon {
  font-size: 80px;
  margin-bottom: var(--space-6);
  animation: pulse-pop 2s infinite;
}

.empty-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 700;
  margin: 0 0 var(--space-4) 0;
}

.empty-description {
  font-size: var(--text-lg);
  color: var(--text-secondary);
  margin-bottom: var(--space-8);
}

/* ========== 波浪装饰 ========== */
.wave-section {
  position: relative;
  z-index: 1;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .hero-title {
    font-size: var(--text-4xl);
  }

  .hero-description {
    font-size: var(--text-base);
  }

  .search-wrapper {
    flex-direction: column;

    .search-button {
      width: 100%;
    }
  }

  .hero-stats {
    flex-direction: column;
    gap: var(--space-4);
    width: 100%;
  }

  .stat-item {
    flex-direction: row;
    justify-content: center;
  }

  .filter-tags {
    flex-direction: column;
    align-items: flex-start;
  }

  .gallery-header {
    flex-direction: column;
    gap: var(--space-4);
  }
}

/* ========== 动画 ========== */
@keyframes bounce-in {
  0% {
    opacity: 0;
    transform: scale(0.3);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-15px);
  }
}
</style>
