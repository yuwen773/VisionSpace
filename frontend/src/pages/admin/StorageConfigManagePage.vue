<template>
  <div class="storage-config-page">
    <a-card title="存储配置管理">
      <template #extra>
        <a-button type="primary" @click="openAddModal">添加配置</a-button>
      </template>

      <!-- 存储平台卡片列表 -->
      <div class="card-list">
        <a-card
          v-for="item in configList"
          :key="item.id"
          :class="{ 'active-card': item.isActive === 1 }"
          class="storage-card"
        >
          <template #title>
            <span>{{ item.platformName }}</span>
            <a-tag :color="item.isActive === 1 ? 'green' : 'default'" class="ml-2">
              {{ item.isActive === 1 ? '已激活' : '未激活' }}
            </a-tag>
          </template>
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="平台">{{ item.platform }}</a-descriptions-item>
            <a-descriptions-item label="Bucket">{{ item.bucket }}</a-descriptions-item>
            <a-descriptions-item label="Region">{{ item.region || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Endpoint">{{ item.endpoint || '-' }}</a-descriptions-item>
            <a-descriptions-item label="域名">{{ item.domain || '-' }}</a-descriptions-item>
          </a-descriptions>
          <div class="card-actions">
            <a-button size="small" type="link" @click="openEditModal(item)">编辑</a-button>
            <a-button
              size="small"
              type="link"
              :disabled="item.isActive === 1"
              @click="handleSetActive(item)"
            >
              激活
            </a-button>
            <a-button
              size="small"
              type="link"
              :disabled="item.isDefault === 1"
              @click="handleSetDefault(item)"
            >
              设为默认
            </a-button>
          </div>
        </a-card>
      </div>

      <!-- 统计和刷新 -->
      <div class="footer-actions">
        <span class="stat-text">共 {{ configList.length }} 个平台配置</span>
        <a-button type="primary" ghost @click="handleRefresh">刷新缓存</a-button>
      </div>
    </a-card>

    <!-- 添加/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="600px"
      @ok="handleSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" class="storage-form">
        <a-form-item label="平台名称" required>
          <a-input v-model:value="formData.platformName" placeholder="如：测试环境 MinIO" />
        </a-form-item>
        <a-form-item label="平台类型" required>
          <a-select v-model:value="formData.platform" placeholder="请选择平台" @change="onPlatformChange">
            <a-select-option value="minio">MinIO</a-select-option>
            <a-select-option value="cos">腾讯云 COS</a-select-option>
            <a-select-option value="oss">阿里云 OSS</a-select-option>
            <a-select-option value="obs">华为云 OBS</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formData.platform === 'minio' || formData.platform === 'oss'" label="Endpoint" required>
          <a-input v-model:value="formData.endpoint" placeholder="如：http://localhost:9000" />
        </a-form-item>
        <a-form-item v-if="formData.platform === 'cos' || formData.platform === 'oss'" label="Region" required>
          <a-input v-model:value="formData.region" placeholder="如：ap-guangzhou" />
        </a-form-item>
        <a-form-item label="Bucket" required>
          <a-input v-model:value="formData.bucket" placeholder="Bucket 名称" />
        </a-form-item>
        <a-form-item label="AccessKey" required>
          <a-input v-model:value="formData.accessKey" placeholder="AccessKey" />
        </a-form-item>
        <a-form-item label="SecretKey" required>
          <a-input v-model:value="formData.secretKey" placeholder="SecretKey" type="password" />
        </a-form-item>
        <a-form-item label="基础路径">
          <a-input v-model:value="formData.basePath" placeholder="存储路径前缀，如：images/" />
        </a-form-item>
        <a-form-item label="域名">
          <a-input v-model:value="formData.domain" placeholder="自定义域名（可选）" />
        </a-form-item>
        <a-form-item label="立即启用">
          <a-switch v-model:checked="formData.isActiveEnabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

<style scoped>
.storage-config-page {
  padding: 24px;
}

.card-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
}

.storage-card {
  width: 300px;
}

.storage-card.active-card {
  border-color: #52c41a;
}

.card-actions {
  margin-top: 12px;
  display: flex;
  gap: 4px;
}

.footer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.stat-text {
  color: #666;
}

.storage-form {
  padding: 16px 0;
}

.ml-2 {
  margin-left: 8px;
}
</style>
