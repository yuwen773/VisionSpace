<template>
  <div id="storageConfigPage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <svg class="title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
            <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
            <line x1="12" y1="22.08" x2="12" y2="12"></line>
          </svg>
          <span>存储配置管理</span>
        </h1>
        <p class="page-subtitle">管理系统对象存储配置，支持多平台切换</p>
      </div>
      <div class="header-actions">
        <button @click="openAddModal" class="toolbar-btn primary">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          添加配置
        </button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon total">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
            <line x1="8" y1="21" x2="16" y2="21"></line>
            <line x1="12" y1="17" x2="12" y2="21"></line>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ configList.length }}</span>
          <span class="stat-label">存储平台</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ activeCount }}</span>
          <span class="stat-label">已激活</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon warning">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-value">{{ configList.length - activeCount }}</span>
          <span class="stat-label">未激活</span>
        </div>
      </div>
    </div>

    <!-- 配置卡片列表 -->
    <div class="config-grid" v-if="configList.length > 0">
      <div
        v-for="item in configList"
        :key="item.id"
        class="config-card"
        :class="{ 'active-card': item.isActive === 1 }"
      >
        <div class="card-header">
          <div class="platform-info">
            <div class="platform-icon" :class="getPlatformClass(item.platform)">
              <svg v-if="item.platform === 'minio'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
              <svg v-else-if="item.platform === 'cos'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
              <svg v-else-if="item.platform === 'oss'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              </svg>
            </div>
            <div class="platform-name">
              <span class="name">{{ item.platformName }}</span>
              <span class="platform-type">{{ getPlatformName(item.platform) }}</span>
            </div>
          </div>
          <div class="status-badge" :class="item.isActive === 1 ? 'enabled' : 'disabled'">
            <span class="status-dot"></span>
            {{ item.isActive === 1 ? '已激活' : '未激活' }}
          </div>
        </div>

        <div class="card-body">
          <div class="config-item">
            <span class="config-label">Bucket</span>
            <span class="config-value">{{ item.bucket }}</span>
          </div>
          <div class="config-item" v-if="item.region">
            <span class="config-label">Region</span>
            <span class="config-value">{{ item.region }}</span>
          </div>
          <div class="config-item" v-if="item.endpoint">
            <span class="config-label">Endpoint</span>
            <span class="config-value endpoint">{{ item.endpoint }}</span>
          </div>
          <div class="config-item" v-if="item.domain">
            <span class="config-label">域名</span>
            <span class="config-value domain">{{ item.domain }}</span>
          </div>
          <div class="config-item" v-if="item.basePath">
            <span class="config-label">基础路径</span>
            <span class="config-value">{{ item.basePath }}</span>
          </div>
        </div>

        <div class="card-footer">
          <button class="action-btn" @click="openEditModal(item)" title="编辑">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
            </svg>
          </button>
          <button
            class="action-btn success"
            :disabled="item.isActive === 1"
            @click="handleSetActive(item)"
            title="激活"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          </button>
          <button
            class="action-btn"
            :disabled="item.isDefault === 1"
            @click="handleSetDefault(item)"
            title="设为默认"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
          </button>
          <a-popconfirm
            title="确定要删除此配置吗？此操作不可恢复。"
            ok-text="删除"
            cancel-text="取消"
            @confirm="handleDelete(item)"
            placement="topRight"
          >
            <button class="action-btn danger" title="删除">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"></polyline>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
              </svg>
            </button>
          </a-popconfirm>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
        <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
        <line x1="12" y1="22.08" x2="12" y2="12"></line>
      </svg>
      <p class="empty-text">暂无存储配置</p>
      <p class="empty-hint">点击上方按钮添加第一个存储平台配置</p>
    </div>

    <!-- 刷新按钮 -->
    <div class="refresh-section" v-if="configList.length > 0">
      <button class="toolbar-btn" @click="handleRefresh">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"></path>
          <path d="M21 3v5h-5"></path>
          <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"></path>
          <path d="M8 16H3v5"></path>
        </svg>
        刷新缓存
      </button>
    </div>

    <!-- 添加/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="600px"
      class="storage-modal"
      @ok="handleSubmit"
    >
      <div class="modal-form">
        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            基本信息
          </h3>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">平台名称 <span class="required">*</span></label>
              <a-input
                v-model:value="formData.platformName"
                placeholder="如：测试环境 MinIO"
                class="form-input"
              />
            </div>
            <div class="form-item">
              <label class="form-label">平台类型 <span class="required">*</span></label>
              <a-select
                v-model:value="formData.platform"
                placeholder="请选择平台"
                class="form-input"
                @change="onPlatformChange"
              >
                <a-select-option value="minio">MinIO</a-select-option>
                <a-select-option value="cos">腾讯云 COS</a-select-option>
                <a-select-option value="oss">阿里云 OSS</a-select-option>
                <a-select-option value="obs">华为云 OBS</a-select-option>
              </a-select>
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            访问凭证
          </h3>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">AccessKey <span class="required">*</span></label>
              <a-input
                v-model:value="formData.accessKey"
                placeholder="AccessKey"
                class="form-input"
              />
            </div>
            <div class="form-item">
              <label class="form-label">SecretKey <span class="required">*</span></label>
              <a-input-password
                v-model:value="formData.secretKey"
                placeholder="SecretKey"
                class="form-input"
              />
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <ellipse cx="12" cy="5" rx="9" ry="3"></ellipse>
              <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path>
              <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path>
            </svg>
            存储设置
          </h3>
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">Bucket <span class="required">*</span></label>
              <a-input
                v-model:value="formData.bucket"
                placeholder="Bucket 名称"
                class="form-input"
              />
            </div>
            <div class="form-item">
              <label class="form-label">基础路径</label>
              <a-input
                v-model:value="formData.basePath"
                placeholder="存储路径前缀，如：images/"
                class="form-input"
              />
            </div>
          </div>
        </div>

        <div class="form-section" v-if="showEndpoint || showRegion">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="2" y1="12" x2="22" y2="12"></line>
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path>
            </svg>
            地域配置
          </h3>
          <div class="form-row">
            <div class="form-item" v-if="showEndpoint">
              <label class="form-label">Endpoint <span class="required">*</span></label>
              <a-input
                v-model:value="formData.endpoint"
                placeholder="如：http://localhost:9000"
                class="form-input"
              />
            </div>
            <div class="form-item" v-if="showRegion">
              <label class="form-label">Region <span class="required">*</span></label>
              <a-input
                v-model:value="formData.region"
                placeholder="如：ap-guangzhou"
                class="form-input"
              />
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path>
              <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path>
            </svg>
            访问域名
          </h3>
          <div class="form-item full-width">
            <label class="form-label">自定义域名</label>
            <a-input
              v-model:value="formData.domain"
              placeholder="自定义域名（可选）"
              class="form-input"
            />
          </div>
        </div>

        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
            </svg>
            状态设置
          </h3>
          <div class="toggle-item">
            <div class="toggle-info">
              <span class="toggle-label">立即启用</span>
              <span class="toggle-hint">启用后此配置将可用于文件上传</span>
            </div>
            <a-switch v-model:checked="formData.isActiveEnabled" />
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getStorageConfigListUsingGet,
  addStorageConfigUsingPost,
  updateStorageConfigUsingPost,
  deleteStorageConfigUsingPost,
  setActivePlatformUsingPost,
  setDefaultPlatformUsingPost,
  refreshStorageConfigUsingPost,
  type StorageConfigVO,
  type StorageConfigAddRequest,
} from '@/api/storageConfigController'

const configList = ref<StorageConfigVO[]>([])
const modalVisible = ref(false)
const editingConfig = ref<StorageConfigVO | null>(null)

const activeCount = computed(() => configList.value.filter(item => item.isActive === 1).length)

const formData = reactive<StorageConfigAddRequest & { isActiveEnabled: boolean }>({
  platform: '',
  platformName: '',
  endpoint: '',
  region: '',
  bucket: '',
  accessKey: '',
  secretKey: '',
  basePath: '',
  domain: '',
  isActiveEnabled: false,
})

const showEndpoint = computed(() => formData.platform === 'minio' || formData.platform === 'oss')
const showRegion = computed(() => formData.platform === 'cos' || formData.platform === 'oss')

const getPlatformClass = (platform: string) => {
  const classMap: Record<string, string> = {
    minio: 'minio',
    cos: 'cos',
    oss: 'oss',
    obs: 'obs',
  }
  return classMap[platform] || 'default'
}

const getPlatformName = (platform: string) => {
  const nameMap: Record<string, string> = {
    minio: 'MinIO',
    cos: '腾讯云 COS',
    oss: '阿里云 OSS',
    obs: '华为云 OBS',
  }
  return nameMap[platform] || platform
}

const onPlatformChange = () => {
  formData.endpoint = ''
  formData.region = ''
}

const loadConfigList = async () => {
  try {
    const res = await getStorageConfigListUsingGet({})
    if (res.data.code === 0) {
      configList.value = res.data.data || []
    }
  } catch (e) {
    message.error('加载配置列表失败')
  }
}

const openAddModal = () => {
  editingConfig.value = null
  Object.assign(formData, {
    id: undefined,
    platform: '',
    platformName: '',
    endpoint: '',
    region: '',
    bucket: '',
    accessKey: '',
    secretKey: '',
    basePath: '',
    domain: '',
    isActiveEnabled: false,
  })
  modalVisible.value = true
}

const openEditModal = (item: StorageConfigVO) => {
  editingConfig.value = item
  Object.assign(formData, {
    id: item.id,
    platform: item.platform,
    platformName: item.platformName,
    endpoint: item.endpoint || '',
    region: item.region || '',
    bucket: item.bucket,
    accessKey: item.accessKey || '',
    secretKey: item.secretKey || '',
    basePath: item.basePath || '',
    domain: item.domain || '',
    isActiveEnabled: item.isActive === 1,
  })
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!formData.platform || !formData.bucket || !formData.accessKey || !formData.secretKey) {
    message.error('请填写必填项')
    return
  }
  try {
    const payload = {
      ...formData,
      isActive: formData.isActiveEnabled ? 1 : 0,
    }
    delete (payload as any).isActiveEnabled

    let res
    if (editingConfig.value?.id) {
      res = await updateStorageConfigUsingPost({ id: editingConfig.value.id, ...payload } as any)
    } else {
      res = await addStorageConfigUsingPost(payload as any)
    }
    if (res.data.code === 0) {
      message.success(editingConfig.value?.id ? '更新成功' : '添加成功')
      modalVisible.value = false
      await loadConfigList()
      await handleRefresh()
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (e) {
    message.error('操作失败')
  }
}

const handleSetActive = async (item: StorageConfigVO) => {
  try {
    const res = await setActivePlatformUsingPost({ id: item.id! })
    if (res.data.code === 0) {
      message.success('激活成功')
      await loadConfigList()
    } else {
      message.error(res.data.message || '激活失败')
    }
  } catch (e) {
    message.error('激活失败')
  }
}

const handleSetDefault = async (item: StorageConfigVO) => {
  try {
    const res = await setDefaultPlatformUsingPost({ id: item.id! })
    if (res.data.code === 0) {
      message.success('设置默认成功')
      await loadConfigList()
    } else {
      message.error(res.data.message || '设置失败')
    }
  } catch (e) {
    message.error('设置失败')
  }
}

const handleDelete = async (item: StorageConfigVO) => {
  if (!item.id) return
  try {
    const res = await deleteStorageConfigUsingPost({ id: item.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      await loadConfigList()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch (e) {
    message.error('删除失败')
  }
}

const handleRefresh = async () => {
  try {
    const res = await refreshStorageConfigUsingPost()
    if (res.data.code === 0) {
      message.success('缓存刷新成功')
    } else {
      message.error(res.data.message || '刷新失败')
    }
  } catch (e) {
    message.error('刷新失败')
  }
}

onMounted(() => {
  loadConfigList()
})
</script>

<style scoped lang="less">
#storageConfigPage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--space-6);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-3xl);
  font-weight: 700;
  margin: 0 0 var(--space-2) 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: #c9d1d9;

  .title-icon {
    width: 32px;
    height: 32px;
    color: #58a6ff;
  }
}

.page-subtitle {
  font-size: var(--text-sm);
  color: #8b949e;
  margin: 0;
}

.header-actions {
  flex-shrink: 0;
}

/* ========== 统计卡片 ========== */
.stats-row {
  display: flex;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
  animation: slideUp 0.5s ease-out 0.1s backwards;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
  flex: 1;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);

  svg {
    width: 24px;
    height: 24px;
  }

  &.total {
    background: rgba(88, 166, 255, 0.15);
    border: 1px solid rgba(88, 166, 255, 0.3);
    color: #58a6ff;
  }

  &.active {
    background: rgba(63, 185, 80, 0.15);
    border: 1px solid rgba(63, 185, 80, 0.3);
    color: #3fb950;
  }

  &.warning {
    background: rgba(210, 153, 34, 0.15);
    border: 1px solid rgba(210, 153, 34, 0.3);
    color: #d29922;
  }
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-size: var(--text-2xl);
  font-weight: 700;
  color: #c9d1d9;
  line-height: 1;
}

.stat-label {
  font-size: var(--text-sm);
  color: #8b949e;
}

/* ========== 配置卡片网格 ========== */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.config-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-fast);
  animation: slideUp 0.5s ease-out backwards;

  &:hover {
    border-color: #8b949e;
  }

  &.active-card {
    border-color: rgba(35, 134, 54, 0.5);
    box-shadow: 0 0 0 1px rgba(35, 134, 54, 0.3);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4);
  background: #21262d;
  border-bottom: 1px solid #30363d;
}

.platform-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.platform-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);

  svg {
    width: 20px;
    height: 20px;
  }

  &.minio {
    background: rgba(88, 166, 255, 0.15);
    border: 1px solid rgba(88, 166, 255, 0.3);
    color: #58a6ff;
  }

  &.cos {
    background: rgba(168, 85, 247, 0.15);
    border: 1px solid rgba(168, 85, 247, 0.3);
    color: #a855f7;
  }

  &.oss {
    background: rgba(210, 153, 34, 0.15);
    border: 1px solid rgba(210, 153, 34, 0.3);
    color: #d29922;
  }

  &.obs {
    background: rgba(248, 81, 73, 0.15);
    border: 1px solid rgba(248, 81, 73, 0.3);
    color: #f85149;
  }

  &.default {
    background: rgba(139, 148, 158, 0.15);
    border: 1px solid rgba(139, 148, 158, 0.3);
    color: #8b949e;
  }
}

.platform-name {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .name {
    font-weight: 600;
    color: #c9d1d9;
    font-size: var(--text-sm);
  }

  .platform-type {
    font-size: var(--text-xs);
    color: #8b949e;
  }
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-full);

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  &.enabled {
    color: #3fb950;
    background: rgba(63, 185, 80, 0.15);
    border: 1px solid rgba(63, 185, 80, 0.3);

    .status-dot {
      background: #3fb950;
    }
  }

  &.disabled {
    color: #8b949e;
    background: rgba(139, 148, 158, 0.15);
    border: 1px solid rgba(139, 148, 158, 0.3);

    .status-dot {
      background: #8b949e;
    }
  }
}

.card-body {
  padding: var(--space-4);
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: var(--space-2) 0;
  border-bottom: 1px solid #21262d;

  &:last-child {
    border-bottom: none;
  }
}

.config-label {
  font-size: var(--text-xs);
  color: #8b949e;
  flex-shrink: 0;
}

.config-value {
  font-size: var(--text-xs);
  color: #c9d1d9;
  font-family: var(--font-mono);
  text-align: right;
  word-break: break-all;
  max-width: 180px;

  &.endpoint,
  &.domain {
    color: #58a6ff;
  }
}

.card-footer {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  background: #21262d;
  border-top: 1px solid #30363d;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #8b949e;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover:not(:disabled) {
    color: #58a6ff;
    background: rgba(88, 166, 255, 0.1);
    border-color: rgba(88, 166, 255, 0.3);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &.success:hover:not(:disabled) {
    color: #3fb950;
    background: rgba(63, 185, 80, 0.1);
    border-color: rgba(63, 185, 80, 0.3);
  }

  &.danger:hover:not(:disabled) {
    color: #f85149;
    background: rgba(248, 81, 73, 0.1);
    border-color: rgba(248, 81, 73, 0.3);
  }
}

/* ========== 空状态 ========== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
  background: #161b22;
  border: 1px dashed #30363d;
  border-radius: var(--radius-lg);
  animation: fadeIn 0.5s ease-out;

  svg {
    width: 64px;
    height: 64px;
    color: #30363d;
    margin-bottom: var(--space-4);
  }

  .empty-text {
    font-size: var(--text-lg);
    font-weight: 600;
    color: #c9d1d9;
    margin: 0 0 var(--space-2) 0;
  }

  .empty-hint {
    font-size: var(--text-sm);
    color: #8b949e;
    margin: 0;
  }
}

/* ========== 刷新区域 ========== */
.refresh-section {
  display: flex;
  justify-content: flex-start;
  animation: slideUp 0.5s ease-out 0.3s backwards;
}

/* ========== 工具栏按钮 ========== */
.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-sm);
  font-weight: 500;
  color: #c9d1d9;
  background: #21262d;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: #30363d;
    border-color: #8b949e;
  }

  &.primary {
    color: #ffffff;
    background: #238636;
    border-color: #238636;

    &:hover {
      background: #2ea043;
      border-color: #2ea043;
    }
  }
}

/* ========== 弹窗表单 ========== */
.storage-modal {
  :deep(.ant-modal-content) {
    background: #161b22;
    border: 1px solid #30363d;
  }

  :deep(.ant-modal-header) {
    background: #161b22;
    border-bottom: 1px solid #30363d;

    .ant-modal-title {
      color: #c9d1d9;
      font-weight: 600;
    }
  }

  :deep(.ant-modal-footer) {
    border-top: 1px solid #30363d;
  }

  :deep(.ant-modal-close) {
    color: #8b949e;
  }
}

.modal-form {
  padding: var(--space-2) 0;
}

.form-section {
  margin-bottom: var(--space-5);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid #21262d;

  &:last-child {
    margin-bottom: 0;
    padding-bottom: 0;
    border-bottom: none;
  }
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: 600;
  color: #c9d1d9;
  margin: 0 0 var(--space-4) 0;

  svg {
    width: 16px;
    height: 16px;
    color: #58a6ff;
  }
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);

  &.full-width {
    grid-column: 1 / -1;
  }
}

.form-label {
  font-size: var(--text-sm);
  color: #c9d1d9);

  .required {
    color: #f85149;
  }
}

.form-input {
  :deep(.ant-input) {
    background: #0d1117;
    border-color: #30363d;
    color: #c9d1d9;

    &::placeholder {
      color: #6e7681;
    }

    &:focus {
      border-color: #58a6ff;
      box-shadow: 0 0 0 2px rgba(88, 166, 255, 0.15);
    }
  }

  :deep(.ant-input-password-icon) {
    color: #8b949e;
  }

  :deep(.ant-select-selector) {
    background: #0d1117 !important;
    border-color: #30363d !important;
    color: #c9d1d9 !important;
  }

  :deep(.ant-select-arrow) {
    color: #8b949e;
  }
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: #0d1117;
  border: 1px solid #30363d;
  border-radius: var(--radius-md);
}

.toggle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.toggle-label {
  font-size: var(--text-sm);
  color: #c9d1d9;
}

.toggle-hint {
  font-size: var(--text-xs);
  color: #8b949e;
}

:deep(.ant-switch) {
  background: #30363d;

  &.ant-switch-checked {
    background: #238636;
  }
}

/* ========== 动画 ========== */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .stats-row {
    flex-wrap: wrap;
  }

  .stat-card {
    flex: 1 1 calc(50% - var(--space-2));
  }
}

@media (max-width: 768px) {
  #storageConfigPage {
    padding: var(--space-4);
  }

  .page-header {
    flex-direction: column;
    gap: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .stat-card {
    flex: 1 1 100%;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
