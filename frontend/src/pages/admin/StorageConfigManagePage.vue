<template>
  <div id="storageConfigPage">
    <!-- 页面头部 -->
    <AdminPageHeader title="存储配置管理" subtitle="管理系统对象存储配置，支持多平台切换">
      <template #icon>
        <HardDrive :size="19" :stroke-width="1.6" />
      </template>
      <template #extra>
        <div class="header-actions">
          <button @click="openAddModal" class="toolbar-btn primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            添加配置
          </button>
        </div>
      </template>
    </AdminPageHeader>

    <!-- 统计卡片：只保留「平台总数 / 本实例生效 / 未激活」三种口径 -->
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
          <span class="stat-value">{{ statsUnavailable ? '—' : configList.length }}</span>
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
          <span class="stat-value">
            {{ switchStatus ? switchStatus.localDefaultPlatform || '—' : '—' }}
          </span>
          <span class="stat-label">
            本实例生效
            <template v-if="switchStatus">（v{{ switchStatus.localEffectiveVersion }}）</template>
            <template v-if="switchStatus && !switchStatus.localSynced">· 版本漂移</template>
          </span>
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
          <span class="stat-value">{{
            statsUnavailable ? '—' : configList.length - activeCount
          }}</span>
          <span class="stat-label">未激活</span>
        </div>
      </div>
    </div>

    <!-- 待确认切换：整宽告警条，不再挤在统计卡片里 -->
    <div v-if="switchStatus?.phase === 'pending'" class="switch-alert" role="alert">
      <div class="switch-alert-main">
        <strong>切换待确认：{{ switchStatus.pendingPlatform }}</strong>
        <span>
          本实例已切到 v{{ switchStatus.localEffectiveVersion }}，还需
          {{
            Math.max(0, (switchStatus.expectedInstances ?? 1) - (switchStatus.confirmCount ?? 0))
          }}
          个实例确认；全部确认前不会晋升为完整生效。
        </span>
      </div>
      <div class="switch-alert-actions">
        <a-button size="small" type="primary" @click="handleConfirmSwitch">确认生效</a-button>
        <a-button size="small" danger @click="handleRollbackSwitch">回滚</a-button>
      </div>
    </div>

    <!-- 加载骨架：首次加载时不要把空状态当成结果 -->
    <div v-if="loadingConfig && configList.length === 0" class="config-grid" aria-busy="true">
      <div v-for="n in 3" :key="`skeleton-${n}`" class="config-card skeleton-card">
        <a-skeleton active :paragraph="{ rows: 4 }" />
      </div>
    </div>

    <!-- 配置卡片列表 -->
    <div class="config-grid" v-else-if="configList.length > 0">
      <div
        v-for="item in configList"
        :key="item.id"
        class="config-card"
        :class="{ 'active-card': item.isActive === 1 }"
      >
        <div class="card-header">
          <div class="platform-info">
            <div class="platform-icon" :class="getPlatformClass(item.platform)">
              <svg
                v-if="item.platform === 'minio'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                ></path>
              </svg>
              <svg
                v-else-if="item.platform === 'cos'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                ></path>
              </svg>
              <svg
                v-else-if="item.platform === 'oss'"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                ></path>
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                ></path>
              </svg>
            </div>
            <div class="platform-name">
              <span class="name">{{ item.platformName }}</span>
              <span class="platform-type">{{ getPlatformName(item.platform) }}</span>
            </div>
          </div>
          <div class="status-badge" :class="statusOf(item).key">
            <span class="status-dot"></span>
            {{ statusOf(item).label }}
          </div>
        </div>

        <div class="card-body">
          <div class="config-item">
            <span class="config-label">Bucket</span>
            <a-tooltip :title="item.bucket" placement="topLeft">
              <span class="config-value mono">{{ item.bucket }}</span>
            </a-tooltip>
          </div>
          <div class="config-item" v-if="item.region">
            <span class="config-label">Region</span>
            <a-tooltip :title="item.region" placement="topLeft">
              <span class="config-value mono">{{ item.region }}</span>
            </a-tooltip>
          </div>
          <div class="config-item" v-if="item.endpoint">
            <span class="config-label">Endpoint</span>
            <a-tooltip :title="item.endpoint" placement="topLeft">
              <span class="config-value mono url">{{ item.endpoint }}</span>
            </a-tooltip>
          </div>
          <div class="config-item" v-if="item.domain">
            <span class="config-label">域名</span>
            <a-tooltip :title="item.domain" placement="topLeft">
              <span class="config-value mono url">{{ item.domain }}</span>
            </a-tooltip>
          </div>
          <div class="config-item" v-if="item.basePath">
            <span class="config-label">基础路径</span>
            <a-tooltip :title="item.basePath" placement="topLeft">
              <span class="config-value mono">{{ item.basePath }}</span>
            </a-tooltip>
          </div>
        </div>

        <div class="card-footer">
          <button class="action-btn labeled" @click="openEditModal(item)" aria-label="编辑配置">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
            </svg>
            编辑
          </button>
          <button
            class="action-btn success labeled"
            :disabled="item.isActive === 1"
            :aria-label="`切换到此平台：${item.platformName}`"
            title="切换到此平台（候选验证后生效）"
            @click="openSwitchModal(item)"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            切换
          </button>
          <button
            class="action-btn danger labeled"
            aria-label="删除配置"
            @click="confirmDelete(item)"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path
                d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
              ></path>
            </svg>
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path
          d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
        ></path>
        <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
        <line x1="12" y1="22.08" x2="12" y2="12"></line>
      </svg>
      <p class="empty-text">{{ loadError ? '存储配置加载失败' : '暂无存储配置' }}</p>
      <p class="empty-hint">
        {{
          loadError ? '网络或服务暂时不可用，请稍后重试。' : '点击上方按钮添加第一个存储平台配置'
        }}
      </p>
      <button v-if="loadError" class="toolbar-btn" @click="loadConfigList">重试</button>
    </div>
    <!-- 添加/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="600px"
      class="storage-modal"
      :confirm-loading="submitLoading"
      @ok="handleSubmit"
    >
      <div class="modal-form">
        <div class="form-section">
          <h3 class="section-title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
              <line x1="8" y1="21" x2="16" y2="21"></line>
              <line x1="12" y1="17" x2="12" y2="21"></line>
            </svg>
            平台与凭证
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
          <div class="form-row">
            <div class="form-item">
              <label class="form-label">
                AccessKey <span v-if="!editingConfig" class="required">*</span>
              </label>
              <a-input
                v-model:value="formData.accessKey"
                :placeholder="
                  editingConfig && formData.hasAccessKey ? '已配置，留空表示不修改' : 'AccessKey'
                "
                class="form-input"
              />
              <span v-if="editingConfig && formData.hasAccessKey" class="form-hint">
                当前已配置（******）；填写新值才会替换
              </span>
            </div>
            <div class="form-item">
              <label class="form-label">
                SecretKey <span v-if="!editingConfig" class="required">*</span>
              </label>
              <a-input-password
                v-model:value="formData.secretKey"
                :placeholder="
                  editingConfig && formData.hasSecretKey ? '已配置，留空表示不修改' : 'SecretKey'
                "
                class="form-input"
              />
              <span v-if="editingConfig && formData.hasSecretKey" class="form-hint">
                当前已配置（******）；填写新值才会替换
              </span>
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
            存储配置
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
          <div class="form-row" v-if="showEndpoint || showRegion">
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
            域名与状态
          </h3>
          <div class="form-item">
            <label class="form-label">自定义域名</label>
            <a-input
              v-model:value="formData.domain"
              placeholder="自定义域名（可选）"
              class="form-input"
            />
          </div>
          <div class="toggle-item toggle-item--spaced">
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
  <!-- 切换确认弹窗：期望实例数由运维按部署实例数填写，不在此硬编码 -->
  <a-modal
    v-model:open="switchModalVisible"
    title="切换生效平台"
    class="storage-modal"
    :confirm-loading="switchLoading"
    ok-text="验证并切换"
    cancel-text="取消"
    @ok="handleSetActive"
  >
    <p class="switch-hint">
      切换前服务端会完整验证候选配置；失败将保留当前生效平台。多实例部署时，
      每个实例都需确认后切换才晋升为完整生效。
    </p>
    <div class="form-item">
      <label class="form-label">期望确认实例数</label>
      <a-input-number
        v-model:value="expectedInstancesInput"
        :min="1"
        :max="1000"
        class="form-input"
      />
      <span class="form-hint">单实例部署保持 1 即可；与部署的实例总数一致。</span>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { HardDrive } from 'lucide-vue-next'
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useUndoable } from '@/composables/useUndoable'
import {
  getStorageConfigListUsingGet,
  addStorageConfigUsingPost,
  updateStorageConfigUsingPost,
  deleteStorageConfigUsingPost,
  setActivePlatformUsingPost,
  refreshStorageConfigUsingPost,
  confirmStorageSwitchUsingPost,
  rollbackStorageSwitchUsingPost,
  getStorageSwitchStatusUsingGet,
  type StorageConfigVO,
  type StorageConfigAddRequest,
  type StorageConfigUpdateRequest,
  type StorageSwitchStatus,
} from '@/api-custom/storage'

const configList = ref<StorageConfigVO[]>([])
const loadingConfig = ref(true)
const loadError = ref(false)
const undo = useUndoable()
const modalVisible = ref(false)
const editingConfig = ref<StorageConfigVO | null>(null)
const submitLoading = ref(false)
// 真实生效状态：区分持久激活配置与运行时快照（#40）
const switchStatus = ref<StorageSwitchStatus | null>(null)
// 切换弹窗：目标配置与运维填写的期望实例数（P2-8：不在前端硬编码实例数）
const switchModalVisible = ref(false)
const switchLoading = ref(false)
const switchTarget = ref<StorageConfigVO | null>(null)
const expectedInstancesInput = ref<number>(1)

const openSwitchModal = (item: StorageConfigVO) => {
  switchTarget.value = item
  expectedInstancesInput.value = 1
  switchModalVisible.value = true
}

const activeCount = computed(() => configList.value.filter((item) => item.isActive === 1).length)

// 加载中或失败时统计显示「—」，不把未知渲染成 0
const statsUnavailable = computed(() => loadingConfig.value || loadError.value)

/**
 * 卡片状态只用三个词：本实例生效 / 已激活，待确认 / 未激活。
 * 「生效」以服务端给出的本实例默认平台为准，而不是前端按 isActive 猜。
 */
const statusOf = (item: StorageConfigVO): { key: string; label: string } => {
  const status = switchStatus.value
  if (status?.phase === 'pending' && status.pendingPlatform === item.platform) {
    return { key: 'pending', label: '已激活，待确认' }
  }
  if (item.isActive === 1) {
    if (status?.localDefaultPlatform && status.localDefaultPlatform === item.platform) {
      return { key: 'local', label: '本实例生效' }
    }
    return { key: 'enabled', label: '已激活' }
  }
  return { key: 'disabled', label: '未激活' }
}

const formData = reactive<
  StorageConfigAddRequest & {
    isActiveEnabled: boolean
    hasAccessKey: boolean
    hasSecretKey: boolean
  }
>({
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
  hasAccessKey: false,
  hasSecretKey: false,
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
  loadingConfig.value = true
  try {
    const res = await getStorageConfigListUsingGet({})
    if (res.data.code === 0) {
      configList.value = res.data.data || []
      loadError.value = false
    } else {
      loadError.value = true
      message.error(res.data.message || '加载配置列表失败')
    }
  } catch (e) {
    loadError.value = true
    message.error('加载配置列表失败')
  } finally {
    loadingConfig.value = false
  }
}

const loadSwitchStatus = async () => {
  try {
    const res = await getStorageSwitchStatusUsingGet()
    if (res.data.code === 0) {
      switchStatus.value = res.data.data
    }
  } catch (e) {
    // 状态加载失败不阻塞配置管理，仅在下一次操作时暴露
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
    hasAccessKey: false,
    hasSecretKey: false,
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
    // 服务端只返回遮蔽值，编辑时密钥留空表示不修改
    accessKey: '',
    secretKey: '',
    basePath: item.basePath || '',
    domain: item.domain || '',
    isActiveEnabled: item.isActive === 1,
    hasAccessKey: item.hasAccessKey === true,
    hasSecretKey: item.hasSecretKey === true,
  })
  modalVisible.value = true
}

const handleSubmit = async () => {
  const editing = Boolean(editingConfig.value?.id)
  const accessKeyMissing = !editing && !formData.accessKey
  const secretKeyMissing = !editing && !formData.secretKey
  if (
    !formData.platformName ||
    !formData.platform ||
    !formData.bucket ||
    accessKeyMissing ||
    secretKeyMissing
  ) {
    message.error('请填写必填项')
    return
  }
  submitLoading.value = true
  try {
    const { isActiveEnabled, hasAccessKey, hasSecretKey, ...submitData } = formData
    const payload = {
      ...submitData,
      isActive: isActiveEnabled ? 1 : 0,
    }

    let res
    if (editingConfig.value?.id) {
      const update: StorageConfigUpdateRequest = { id: editingConfig.value.id, ...payload }
      // 留空表示不修改；服务端据此保留原密钥
      if (!submitData.accessKey) delete update.accessKey
      if (!submitData.secretKey) delete update.secretKey
      res = await updateStorageConfigUsingPost(update)
    } else {
      res = await addStorageConfigUsingPost(payload)
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
  } finally {
    submitLoading.value = false
  }
}

const handleSetActive = async () => {
  if (!switchTarget.value?.id) return
  switchLoading.value = true
  try {
    // 候选验证在服务端完成：失败保留当前生效平台，响应呈现真实切换状态；
    // 期望实例数由运维在弹窗中按部署规模填写，不由前端硬编码
    const res = await setActivePlatformUsingPost({
      targetConfigId: switchTarget.value.id,
      expectedInstances: expectedInstancesInput.value,
    })
    if (res.data.code === 0) {
      const status: StorageSwitchStatus = res.data.data
      if (status.phase === 'pending') {
        message.info(
          `切换已生效于本实例（v${status.localEffectiveVersion}），等待其余实例确认` +
            `（${status.confirmCount}/${status.expectedInstances}）`,
        )
      } else {
        message.success(
          `已切换到 ${switchTarget.value.platformName}（v${status.localEffectiveVersion}）`,
        )
      }
      switchModalVisible.value = false
      await Promise.all([loadConfigList(), loadSwitchStatus()])
    } else {
      message.error(res.data.message || '切换失败：已保留当前生效平台')
    }
  } catch {
    message.error('切换失败：已保留当前生效平台')
  } finally {
    switchLoading.value = false
  }
}

const handleConfirmSwitch = async () => {
  try {
    const res = await confirmStorageSwitchUsingPost()
    if (res.data.code === 0) {
      const status: StorageSwitchStatus = res.data.data
      if (status.phase === 'completed') {
        message.success(`切换已确认生效（v${status.activeVersion}）`)
      } else {
        message.info(`确认已记录（${status.confirmCount}/${status.expectedInstances}）`)
      }
      await Promise.all([loadConfigList(), loadSwitchStatus()])
    } else {
      message.error(res.data.message || '确认失败')
    }
  } catch {
    message.error('确认失败')
  }
}

const handleRollbackSwitch = async () => {
  try {
    const res = await rollbackStorageSwitchUsingPost()
    if (res.data.code === 0) {
      message.success('已回滚：保留旧快照')
      await Promise.all([loadConfigList(), loadSwitchStatus()])
    } else {
      message.error(res.data.message || '回滚失败')
    }
  } catch {
    message.error('回滚失败')
  }
}

const confirmDelete = (item: StorageConfigVO) => {
  Modal.confirm({
    title: `删除存储配置「${item.platformName}」？`,
    content: '删除后该平台不能再用于上传。5 秒内可撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => applyDelete(item),
  })
}

const applyDelete = (item: StorageConfigVO) => {
  if (!item.id) return
  const index = configList.value.findIndex((row) => row.id === item.id)
  if (index < 0) return
  configList.value.splice(index, 1)

  undo.schedule({
    key: `storage-delete-${item.id}`,
    message: `「${item.platformName}」已删除`,
    commit: async () => {
      try {
        const res = await deleteStorageConfigUsingPost({ id: item.id as number })
        if (res.data.code !== 0) {
          message.error(res.data.message || '删除失败，已恢复')
        }
      } catch {
        message.error('删除失败，已恢复')
      } finally {
        await loadConfigList()
      }
    },
    undo: () => {
      configList.value.splice(Math.min(index, configList.value.length), 0, item)
    },
  })
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
  loadSwitchStatus()
})
</script>

<style scoped lang="less">
#storageConfigPage {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0;
}

.header-actions {
  flex-shrink: 0;
}

/* ========== 统计卡片 ========== */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 0;
  margin-bottom: var(--admin-space-6);
  border-top: 1px solid var(--admin-border-default);
  border-left: 1px solid var(--admin-border-default);
  animation: slideUp 0.45s var(--admin-ease) 0.05s backwards;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--admin-space-4);
  min-width: 0;
  padding: var(--admin-space-4) var(--admin-space-5);
  background: color-mix(in srgb, var(--admin-bg-primary) 72%, transparent);
  border-right: 1px solid var(--admin-border-default);
  border-bottom: 1px solid var(--admin-border-default);
  transition: background var(--admin-transition-base);

  &:hover {
    background: var(--admin-bg-primary);
  }
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: var(--admin-radius-sm);
  background: var(--admin-primary-bg);
  border: 1px solid color-mix(in srgb, var(--admin-primary) 22%, transparent);
  color: var(--admin-primary);

  svg {
    width: 18px;
    height: 18px;
  }

  &.total {
    background: var(--admin-primary-bg);
    border-color: color-mix(in srgb, var(--admin-primary) 22%, transparent);
    color: var(--admin-primary);
  }

  &.active {
    background: var(--admin-success-bg);
    border-color: color-mix(in srgb, var(--admin-success) 22%, transparent);
    color: var(--admin-success);
  }

  &.warning {
    background: var(--admin-warning-bg);
    border-color: color-mix(in srgb, var(--admin-warning) 22%, transparent);
    color: var(--admin-warning);
  }
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.stat-value {
  font-family: var(--admin-serif);
  font-size: var(--admin-text-2xl);
  font-weight: 600;
  line-height: 1;
  color: var(--admin-text-primary);
}

.stat-label {
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
}

.pending-actions {
  display: flex;
  gap: var(--admin-space-2);
  margin-top: var(--admin-space-2);
}

/* ========== 配置卡片网格 ========== */
.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-6);
}

.config-card {
  display: flex;
  flex-direction: column;
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  overflow: hidden;
  transition:
    border-color var(--admin-transition-fast),
    transform var(--admin-transition-base),
    box-shadow var(--admin-transition-base);
  animation: slideUp 0.45s var(--admin-ease) backwards;

  &:hover {
    border-color: var(--admin-border-strong);
    transform: translateY(-2px);
    box-shadow: var(--admin-shadow-md);
  }

  &.active-card {
    border-color: var(--admin-success);
    box-shadow: inset 3px 0 0 var(--admin-success);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--admin-space-4);
  background: transparent;
  border-bottom: 1px solid var(--admin-border-default);
}

.platform-info {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.platform-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--admin-radius-sm);
  background: var(--admin-bg-tertiary);
  border: 1px solid var(--admin-border-default);
  color: var(--admin-text-secondary);

  svg {
    width: 18px;
    height: 18px;
  }

  &.minio {
    color: var(--admin-primary);
    border-color: color-mix(in srgb, var(--admin-primary) 26%, transparent);
    background: var(--admin-primary-bg);
  }

  &.cos {
    color: var(--admin-text-primary);
    border-color: var(--admin-border-strong);
  }

  &.oss {
    color: var(--admin-warning);
    border-color: color-mix(in srgb, var(--admin-warning) 26%, transparent);
    background: var(--admin-warning-bg);
  }

  &.obs {
    color: var(--admin-danger);
    border-color: color-mix(in srgb, var(--admin-danger) 26%, transparent);
    background: var(--admin-danger-bg);
  }

  &.default {
    color: var(--admin-text-secondary);
  }
}

.platform-name {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;

  .name {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    font-weight: 600;
    color: var(--admin-text-primary);
    font-size: var(--admin-text-sm);
  }

  .platform-type {
    font-family: var(--admin-font-mono);
    font-size: 8px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--admin-text-tertiary);
  }
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 2px 8px;
  font-family: var(--admin-font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  border-radius: var(--admin-radius-sm);

  .status-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
  }

  &.enabled {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border: 1px solid color-mix(in srgb, var(--admin-success) 26%, transparent);

    .status-dot {
      background: var(--admin-success);
    }
  }

  /* 本实例真正在用的那一份：比「已激活」再强一档，用主色描边 */
  &.local {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border: 1px solid color-mix(in srgb, var(--admin-primary) 32%, transparent);

    .status-dot {
      background: var(--admin-primary);
    }
  }

  /* 已写入但还没等到全部实例确认 */
  &.pending {
    color: var(--admin-warning);
    background: var(--admin-warning-bg);
    border: 1px solid color-mix(in srgb, var(--admin-warning) 30%, transparent);

    .status-dot {
      background: var(--admin-warning);
    }
  }

  &.disabled {
    color: var(--admin-text-tertiary);
    background: transparent;
    border: 1px solid var(--admin-border-default);

    .status-dot {
      background: var(--admin-text-tertiary);
    }
  }
}

.card-body {
  /* 同一行卡片等高，正文撑开剩余空间，操作栏才会齐底对齐 */
  flex: 1;
  padding: var(--admin-space-4);
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 9px 0;
  border-bottom: 1px solid var(--admin-border-subtle);

  &:last-child {
    border-bottom: none;
  }
}

.config-label {
  font-family: var(--admin-font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  flex-shrink: 0;
}

.config-value {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.mono {
    font-family: var(--admin-font-mono);
  }

  &.url {
    color: var(--admin-primary);
  }
}

.card-footer {
  display: flex;
  gap: var(--admin-space-2);
  padding: var(--admin-space-3) var(--admin-space-4);
  background: transparent;
  border-top: 1px solid var(--admin-border-subtle);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--admin-text-tertiary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast);

  svg {
    width: 15px;
    height: 15px;
  }

  &:hover:not(:disabled) {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border-color: color-mix(in srgb, var(--admin-primary) 22%, transparent);
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }

  &.success:hover:not(:disabled) {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border-color: color-mix(in srgb, var(--admin-success) 22%, transparent);
  }

  &.danger:hover:not(:disabled) {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: color-mix(in srgb, var(--admin-danger) 22%, transparent);
  }

  &.labeled {
    width: auto;
    gap: 6px;
    padding: 0 10px;
    font-size: var(--admin-text-xs);
    font-weight: 600;

    svg {
      width: 13px;
      height: 13px;
    }
  }
}

/* ========== 待确认切换告警条 ========== */
.switch-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-4);
  background: var(--admin-warning-bg);
  border: 1px solid color-mix(in srgb, var(--admin-warning) 34%, transparent);
  border-radius: var(--admin-radius-sm);

  .switch-alert-main {
    display: flex;
    flex-direction: column;
    gap: 3px;
    min-width: 0;

    strong {
      font-size: var(--admin-text-sm);
      color: var(--admin-text-primary);
    }

    span {
      font-size: var(--admin-text-xs);
      color: var(--admin-text-secondary);
    }
  }

  .switch-alert-actions {
    display: flex;
    flex-shrink: 0;
    gap: var(--admin-space-2);
  }
}

.skeleton-card {
  pointer-events: none;
  padding: var(--admin-space-4);
  min-height: 220px;

  &:hover {
    transform: none;
    box-shadow: none;
    border-color: var(--admin-border-default);
  }
}

/* ========== 空状态 ========== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--admin-space-12) var(--admin-space-6);
  background: var(--admin-bg-primary);
  border: 1px dashed var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  animation: fadeIn 0.45s var(--admin-ease);

  svg {
    width: 54px;
    height: 54px;
    color: var(--admin-border-strong);
    margin-bottom: var(--admin-space-4);
  }

  .empty-text {
    font-family: var(--admin-serif);
    font-size: var(--admin-text-lg);
    font-weight: 600;
    color: var(--admin-text-primary);
    margin: 0 0 var(--admin-space-2) 0;
  }

  .empty-hint {
    font-size: var(--admin-text-sm);
    color: var(--admin-text-tertiary);
    margin: 0;
  }
}

/* ========== 刷新区域 ========== */
.refresh-section {
  display: flex;
  justify-content: flex-start;
  animation: slideUp 0.45s var(--admin-ease) 0.15s backwards;
}

/* ========== 工具栏按钮 ========== */
.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  height: 36px;
  padding: 0 var(--admin-space-4);
  font-size: var(--admin-text-sm);
  font-weight: 600;
  color: var(--admin-text-primary);
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition:
    color var(--admin-transition-fast),
    background var(--admin-transition-fast),
    border-color var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-strong);
  }

  &.primary {
    color: var(--admin-accent-ink);
    background: var(--admin-primary);
    border-color: var(--admin-primary);

    &:hover {
      background: var(--admin-primary-hover);
      border-color: var(--admin-primary-hover);
    }
  }
}

/* ========== 弹窗表单 ========== */
.storage-modal {
  :deep(.ant-modal-content) {
    background: var(--admin-bg-primary);
    border: 1px solid var(--admin-border-default);
  }

  :deep(.ant-modal-header) {
    background: transparent;
    border-bottom: 1px solid var(--admin-border-subtle);

    .ant-modal-title {
      color: var(--admin-text-primary);
      font-family: var(--admin-serif);
      font-weight: 600;
    }
  }

  :deep(.ant-modal-footer) {
    border-top: 1px solid var(--admin-border-subtle);
  }

  :deep(.ant-modal-close) {
    color: var(--admin-text-tertiary);
  }
}

.modal-form {
  padding: var(--admin-space-2) 0;
}

.switch-hint {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-secondary);
  margin: 0 0 var(--admin-space-4) 0;
  line-height: 1.5;
}

.form-section {
  margin-bottom: var(--admin-space-5);
  padding-bottom: var(--admin-space-5);
  border-bottom: 1px solid var(--admin-border-subtle);

  &:last-child {
    margin-bottom: 0;
    padding-bottom: 0;
    border-bottom: none;
  }
}

.section-title {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);
  font-family: var(--admin-font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--admin-text-tertiary);
  margin: 0 0 var(--admin-space-4) 0;

  svg {
    width: 15px;
    height: 15px;
    color: var(--admin-primary);
  }
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--admin-space-4);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: var(--admin-space-2);
}

.form-label {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-primary);

  .required {
    color: var(--admin-danger);
  }
}

.form-hint {
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
  line-height: 1.4;
}

.form-input {
  :deep(.ant-input) {
    background: transparent;
    border-color: var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 2px var(--admin-primary-bg);
    }
  }

  :deep(.ant-input-password-icon) {
    color: var(--admin-text-tertiary);
  }

  :deep(.ant-select-selector) {
    background: transparent !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }

  :deep(.ant-select-arrow) {
    color: var(--admin-text-tertiary);
  }
}

.toggle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--admin-space-3) var(--admin-space-4);
  background: transparent;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);

  &--spaced {
    margin-top: var(--admin-space-4);
  }
}

.toggle-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.toggle-label {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-primary);
}

.toggle-hint {
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
}

:deep(.ant-switch) {
  background: var(--admin-border-default);

  &.ant-switch-checked {
    background: var(--admin-primary);
  }
}

/* ========== 动画 ========== */
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
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
@media (max-width: 768px) {
  #storageConfigPage {
    padding: 0;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
