<template>
  <div id="addSpacePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <button @click="goBack" class="pop-btn outline back-btn">
          <span>←</span>
          <span>返回</span>
        </button>
        <div class="header-info">
          <h1 class="page-title">
            {{ route.query.id ? '✏️ 编辑' : '✨ 创建' }}{{ SPACE_TYPE_MAP[spaceType] }}
          </h1>
          <p class="page-subtitle">🔧 配置您的云端存储空间</p>
        </div>
      </div>
    </div>

    <div class="content-wrapper">
      <a-row :gutter="[40, 40]">
        <!-- 创建/编辑表单 -->
        <a-col :xs="24" :lg="14">
          <div class="form-card pop-card">
            <div class="card-header">
              <span class="header-icon">☁️</span>
              <h3 class="card-title">空间配置</h3>
            </div>
            <a-form
              name="spaceForm"
              layout="vertical"
              :model="spaceForm"
              @finish="handSubmit"
              class="space-form"
            >
              <a-form-item
                name="spaceName"
                label="🏷️ 空间名称"
                :rules="[{ required: true, message: '请输入空间名称' }]"
              >
                <a-input
                  v-model:value="spaceForm.spaceName"
                  placeholder="请输入空间名称"
                  allow-clear
                  size="large"
                />
              </a-form-item>

              <a-form-item
                name="spaceLevel"
                label="⭐ 空间级别"
                :rules="[{ required: true, message: '请选择空间级别' }]"
              >
                <a-select
                  v-model:value="spaceForm.spaceLevel"
                  :options="SPACE_LEVEL_OPTIONS"
                  placeholder="请选择空间级别"
                  allow-clear
                  size="large"
                />
              </a-form-item>

              <a-form-item class="submit-form-item">
                <button
                  :loading="loading"
                  html-type="submit"
                  class="pop-btn primary submit-btn"
                >
                  <span>✅</span>
                  <span>{{ route.query.id ? '保存修改' : '创建空间' }}</span>
                </button>
              </a-form-item>
            </a-form>
          </div>
        </a-col>

        <!-- 空间级别介绍 -->
        <a-col :xs="24" :lg="10">
          <div class="info-card pop-card">
            <div class="card-header">
              <span class="header-icon">ℹ️</span>
              <h3 class="card-title">空间级别介绍</h3>
            </div>

            <div class="info-content">
              <div class="contact-alert pop-tag sunshine">
                <span>💬</span>
                <span>如需升级空间级别，请联系管理员</span>
              </div>

              <div class="level-list">
                <div
                  v-for="level in spaceLevelList"
                  :key="level.level"
                  class="level-item"
                  :class="`level-${level.level}`"
                >
                  <div class="level-header">
                    <span class="level-tag pop-tag" :class="getLevelTagClass(level.level)">
                      {{ level.text }}
                    </span>
                  </div>
                  <div class="level-details">
                    <div class="detail-row">
                      <span class="detail-label">💾 存储大小：</span>
                      <span class="detail-value">{{ formatSize(level.maxSize) }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">🖼️ 图片数量：</span>
                      <span class="detail-value">{{ level.maxCount }} 张</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  addSpaceUsingPost,
  getSpaceVoByIdUsingGet,
  listSpaceLevelUsingGet,
  updateSpaceUsingPost,
} from '@/api/spaceController.ts'
import { useRouter, useRoute } from 'vue-router'
import {
  SPACE_LEVEL_OPTIONS,
  SPACE_TYPE_ENUM,
  SPACE_TYPE_MAP,
  SPACE_LEVEL_ENUM,
} from '@/constants/space.ts'
import { formatSize } from '@/utils'

const router = useRouter()
const route = useRoute()

const loading = ref<boolean>(false)
const space = ref<API.SpaceVO>()
const spaceLevelList = ref<API.SpaceLevel[]>([])

const spaceForm = reactive<API.SpaceAddRequest | API.SpaceEditRequest>({})

const handSubmit = async () => {
  loading.value = true
  try {
    const spaceId = space.value?.id
    let res
    if (spaceId) {
      res = await updateSpaceUsingPost({ id: spaceId, ...spaceForm })
    } else {
      res = await addSpaceUsingPost({ ...spaceForm, spaceType: spaceType.value })
    }
    if (res.data.code === 0) {
      message.success(spaceId ? '✅ 修改成功' : '🎉 创建成功')
      if (spaceId) {
        router.push(`/space/${spaceId}`)
      } else {
        router.push('/my_space')
      }
    } else {
      message.error('😅 ' + (spaceId ? '修改失败' : '创建失败') + '：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

const getOldSpace = async () => {
  const id = route.query?.id
  if (id) {
    try {
      const res = await getSpaceVoByIdUsingGet({ id })
      if (res.data.code === 0 && res.data.data) {
        const data = res.data.data
        space.value = data
        spaceForm.spaceName = data.spaceName
        spaceForm.spaceLevel = data.spaceLevel
      }
    } catch (error) {
      message.error('😅 获取空间信息失败')
    }
  }
}

const fetchSpaceLevelList = async () => {
  try {
    const res = await listSpaceLevelUsingGet()
    if (res.data.code === 0 && res.data.data) {
      spaceLevelList.value = res.data.data
    }
  } catch (error) {
    message.error('😅 获取空间级别信息失败')
  }
}

const spaceType = computed(() => {
  if (route.query?.type) {
    return Number(route.query?.type)
  }
  return SPACE_TYPE_ENUM.PRIVATE
})

const getLevelTagClass = (level: number) => {
  const classMap: Record<number, string> = {
    [SPACE_LEVEL_ENUM.BASIC]: 'coral',
    [SPACE_LEVEL_ENUM.PROFESSIONAL]: 'sky',
    [SPACE_LEVEL_ENUM.FLAGSHIP]: 'sunshine',
  }
  return classMap[level] || 'coral'
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  getOldSpace()
  fetchSpaceLevelList()
})
</script>

<style scoped lang="less">
#addSpacePage {
  max-width: var(--container-2xl);
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
  margin: 0 0 var(--space-2) 0;
}

.page-subtitle {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
}

/* ========== 内容区域 ========== */
.content-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

/* ========== 卡片通用样式 ========== */
.form-card,
.info-card {
  padding: var(--space-8);
  height: 100%;

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
  font-size: var(--text-xl);
  font-weight: 700;
  margin: 0;
}

/* ========== 表单 ========== */
.space-form {
  :deep(.ant-form-item) {
    margin-bottom: var(--space-6);
  }

  :deep(.ant-form-item-label > label) {
    font-weight: 700;
    color: var(--text-primary);
  }
}

.submit-form-item {
  margin-top: var(--space-8);

  :deep(.ant-form-item-control-input) {
    display: block;
  }
}

.submit-btn {
  width: 100%;
  height: 52px;
  font-size: var(--text-lg);
}

/* ========== 信息卡片 ========== */
.info-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-6);
}

.contact-alert {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4);
  font-size: var(--text-sm);
  font-weight: 600;
}

/* ========== 级别列表 ========== */
.level-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.level-item {
  padding: var(--space-4);
  border-radius: var(--radius-xl);
  border: 3px solid var(--border-light);
  background: var(--bg-tertiary);
  transition: all var(--transition-bounce);

  &:hover {
    border-color: var(--color-sunshine);
    transform: translate(-2px, -2px);
    box-shadow: var(--shadow-pop);
  }
}

.level-header {
  margin-bottom: var(--space-3);
}

.level-tag {
  font-weight: 700;
  padding: 6px 14px;
}

.level-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--text-sm);
}

.detail-label {
  color: var(--text-secondary);
  font-weight: 600;
}

.detail-value {
  color: var(--text-primary);
  font-weight: 700;
  font-family: var(--font-mono);
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .content-wrapper {
    :deep(.ant-col) {
      width: 100%;
    }
  }
}

@media (max-width: 640px) {
  #addSpacePage {
    padding: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .form-card,
  .info-card {
    padding: var(--space-6);
  }
}
</style>
