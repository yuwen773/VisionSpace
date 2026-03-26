<template>
  <div id="spaceUserManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <span>👥</span>
          <span>空间成员管理</span>
        </h1>
        <p class="page-subtitle">管理空间成员和角色权限</p>
      </div>
    </div>

    <!-- 添加成员表单 -->
    <div class="form-section pop-card">
      <h3 class="section-title">
        <span>➕</span>
        <span>添加成员</span>
      </h3>
      <a-form layout="inline" :model="formData" @finish="handleSubmit" class="add-member-form">
        <a-form-item label="👤 用户ID" name="userId">
          <a-input
            v-model:value="formData.userId"
            placeholder="请输入用户ID"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item>
          <button html-type="submit" class="pop-btn primary submit-btn">
            <span>➕</span>
            <span>添加成员</span>
          </button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 成员列表 -->
    <div class="table-wrapper pop-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        class="data-table"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userInfo'">
            <div class="user-info">
              <a-avatar :src="record.user?.userAvatar" :size="40" class="user-avatar" />
              <div class="user-details">
                <div class="user-name">{{ record.user?.userName || '未知用户' }}</div>
                <div class="user-account">@{{ record.user?.userAccount || record.userId }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceRole'">
            <a-select
              v-model:value="record.spaceRole"
              :options="SPACE_ROLE_OPTIONS"
              @change="(value: any) => editSpaceRole(value, record)"
              class="role-select"
            />
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">
              {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}
            </span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-popconfirm
                title="确定要移除此成员吗？"
                ok-text="🗑️ 移除"
                cancel-text="❌ 取消"
                @confirm="doDelete(record.id)"
              >
                <button class="action-btn pop-btn" style="background: var(--color-pink);">
                  <span>🗑️</span>
                </button>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 返回按钮 -->
    <div class="back-section">
      <button @click="goBack" class="pop-btn outline back-btn">
        <span>←</span>
        <span>返回空间详情</span>
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { SPACE_ROLE_OPTIONS } from '@/constants/space.ts'
import {
  addSpaceUserUsingPost,
  deleteSpaceUserUsingPost,
  editSpaceUserUsingPost,
  listSpaceUserUsingPost,
} from '@/api/spaceUserController.ts'

interface Props {
  spaceId: string
}

const props = defineProps<Props>()
const router = useRouter()

const columns = [
  { title: '成员信息', dataIndex: 'userInfo', width: 300 },
  { title: '角色', dataIndex: 'spaceRole', width: 200 },
  { title: '加入时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 100 },
]

const dataList = ref<API.SpaceUserVO[]>([])
const loading = ref(false)
const formData = reactive<API.SpaceUserAddRequest>({})

const fetchData = async () => {
  if (!props.spaceId) return
  loading.value = true
  try {
    const res = await listSpaceUserUsingPost({ spaceId: props.spaceId })
    if (res.data.code === 0) {
      dataList.value = res.data.data ?? []
    } else {
      message.error('😅 获取数据失败：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

const handleSubmit = async () => {
  const spaceId = props.spaceId
  if (!spaceId) return
  try {
    const res = await addSpaceUserUsingPost({
      ...formData,
      spaceId: spaceId,
    })
    if (res.data.code === 0) {
      message.success('🎉 添加成员成功')
      Object.assign(formData, {})
      await fetchData()
    } else {
      message.error('😅 添加成员失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 添加成员失败')
  }
}

const editSpaceRole = async (value: any, record: any) => {
  try {
    const res = await editSpaceUserUsingPost({
      id: record.id,
      spaceRole: value,
    })
    if (res.data.code === 0) {
      message.success('✅ 修改角色成功')
    } else {
      message.error('😅 修改角色失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 修改角色失败')
  }
}

const doDelete = async (id: number) => {
  if (!id) return
  try {
    const res = await deleteSpaceUserUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 已移除成员')
      await fetchData()
    } else {
      message.error('😅 移除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 移除失败')
  }
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="less">
#spaceUserManagePage {
  max-width: var(--container-xl);
  margin: 0 auto;
  padding: var(--space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--space-8);
  animation: fadeIn 0.6s ease-out;
}

.header-content {
  text-align: center;
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--text-4xl);
  font-weight: 800;
  margin: 0 0 var(--space-3) 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  background: var(--gradient-pop);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;

  span:first-child {
    -webkit-text-fill-color: initial;
    background: none;
  }
}

.page-subtitle {
  font-size: var(--text-base);
  color: var(--text-secondary);
  margin: 0;
  font-weight: 600;
}

/* ========== 表单区域 ========== */
.form-section {
  margin-bottom: var(--space-6);
  padding: var(--space-6);
  animation: slideUp 0.6s ease-out 0.1s backwards;

  &::before {
    display: none;
  }
}

.section-title {
  font-size: var(--text-lg);
  font-weight: 700;
  margin: 0 0 var(--space-5) 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-primary);
}

.add-member-form {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }

  :deep(.ant-form-item-label) {
    font-weight: 700;
    color: var(--text-primary);
  }
}

.form-input {
  min-width: 200px;
}

.submit-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

/* ========== 表格区域 ========== */
.table-wrapper {
  padding: var(--space-6);
  margin-bottom: var(--space-8);
  animation: slideUp 0.6s ease-out 0.2s backwards;

  &::before {
    display: none;
  }
}

.data-table {
  :deep(.ant-table) {
    background: transparent;
  }

  :deep(.ant-table-container) {
    border-radius: var(--radius-xl);
    border: 3px solid var(--border-light);
    overflow: hidden;
  }

  :deep(.ant-table-thead > tr > th) {
    background: var(--gradient-fresh);
    border-bottom: 3px solid var(--border-bold);
    color: white;
    font-weight: 700;
    padding: var(--space-4);
    text-align: center;
  }

  :deep(.ant-table-tbody > tr) {
    transition: all var(--transition-bounce);

    &:hover > td {
      background: var(--bg-hover);
    }

    > td {
      border-bottom: 2px solid var(--border-light);
      color: var(--text-secondary);
      padding: var(--space-4);
      text-align: center;
      font-weight: 600;
    }
  }
}

/* ========== 用户信息 ========== */
.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.user-avatar {
  border: 3px solid var(--border-bold);
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.user-name {
  font-weight: 700;
  color: var(--text-primary);
}

.user-account {
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ========== 角色选择器 ========== */
.role-select {
  min-width: 120px;
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
}

.action-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    transform: translate(-2px, -2px);
    box-shadow: var(--shadow-pop);
  }
}

/* ========== 返回区域 ========== */
.back-section {
  display: flex;
  justify-content: center;
}

.back-btn {
  padding: 0 var(--space-6);
  height: 48px;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);

  &:hover {
    transform: translate(-2px, -2px);
    box-shadow: var(--shadow-pop);
  }
}

.time-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-tertiary);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  #spaceUserManagePage {
    padding: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .add-member-form {
    :deep(.ant-form-item) {
      width: 100%;
    }
  }

  .form-input {
    min-width: 100%;
  }

  .user-info {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-wrapper {
    padding: var(--space-4);
  }

  :deep(.ant-table) {
    font-size: var(--text-sm);
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
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
