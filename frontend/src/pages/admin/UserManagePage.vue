<template>
  <div id="userManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <svg class="title-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
          <span>用户管理</span>
        </h1>
        <p class="page-subtitle">管理系统用户和权限</p>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="search-box">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <a-input
          v-model:value="searchParams.userAccount"
          placeholder="搜索用户账号..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="search-box">
        <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <a-input
          v-model:value="searchParams.userName"
          placeholder="搜索用户名..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-buttons">
        <a-select
          v-model:value="searchParams.userRole"
          placeholder="角色筛选"
          class="role-filter"
          allow-clear
        >
          <a-select-option value="admin">管理员</a-select-option>
          <a-select-option value="user">普通用户</a-select-option>
        </a-select>
        <button class="toolbar-btn primary" @click="doSearch">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.35-4.35"></path>
          </svg>
          搜索
        </button>
        <button class="toolbar-btn" @click="resetSearch">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"></path>
            <path d="M21 3v5h-5"></path>
            <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"></path>
            <path d="M8 16H3v5"></path>
          </svg>
          重置
        </button>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        @change="doTableChange"
        :loading="loading"
        :rowKey="(record: API.UserVO) => record.id"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar :src="record.userAvatar" :size="42" class="user-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
          </template>
          <template v-else-if="column.dataIndex === 'userName'">
            <div class="user-info">
              <span class="user-name">{{ record.userName || '未设置' }}</span>
              <span class="user-account">@{{ record.userAccount }}</span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'userProfile'">
            <span class="user-profile">{{ record.userProfile || '暂无简介' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <span class="role-tag" :class="record.userRole === 'admin' ? 'admin' : 'user'">
              <svg v-if="record.userRole === 'admin'" class="tag-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
              </svg>
              <svg v-else class="tag-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
              {{ record.userRole === 'admin' ? '管理员' : '普通用户' }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'vipCode'">
            <span class="vip-tag" v-if="record.vipCode">
              <svg class="vip-icon" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
              </svg>
              VIP {{ record.vipNumber }}
            </span>
            <span class="vip-tag basic" v-else>普通用户</span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="编辑用户">
                <button class="action-btn" @click="openEditModal(record)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
              </a-tooltip>
              <a-popconfirm
                title="确定要删除此用户吗？此操作不可恢复。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
                placement="topRight"
              >
                <a-tooltip title="删除用户">
                  <button class="action-btn danger">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"></polyline>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                      <line x1="10" y1="11" x2="10" y2="17"></line>
                      <line x1="14" y1="11" x2="14" y2="17"></line>
                    </svg>
                  </button>
                </a-tooltip>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 编辑用户弹窗 -->
    <a-modal
      v-model:open="editModalVisible"
      title="编辑用户"
      :footer="null"
      width="480px"
      class="edit-modal"
      @cancel="closeEditModal"
    >
      <div class="edit-form" v-if="currentUser">
        <div class="form-avatar">
          <a-avatar :src="currentUser.userAvatar" :size="72">
            <template #icon><UserOutlined /></template>
          </a-avatar>
        </div>
        <div class="form-item">
          <label>用户账号</label>
          <a-input :value="currentUser.userAccount" disabled class="form-input" />
        </div>
        <div class="form-item">
          <label>用户名</label>
          <a-input v-model:value="editForm.userName" placeholder="请输入用户名" class="form-input" />
        </div>
        <div class="form-item">
          <label>用户简介</label>
          <a-textarea v-model:value="editForm.userProfile" placeholder="请输入用户简介" :rows="3" class="form-input" />
        </div>
        <div class="form-item">
          <label>用户角色</label>
          <a-select v-model:value="editForm.userRole" class="form-input">
            <a-select-option value="admin">管理员</a-select-option>
            <a-select-option value="user">普通用户</a-select-option>
          </a-select>
        </div>
        <div class="form-actions">
          <button class="modal-btn cancel" @click="closeEditModal">取消</button>
          <button class="modal-btn primary" @click="doUpdate" :loading="updateLoading">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
              <polyline points="17 21 17 13 7 13 7 21"></polyline>
              <polyline points="7 3 7 8 15 8"></polyline>
            </svg>
            保存修改
          </button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { deleteUserUsingPost, listUserVoByPageUsingPost, updateUserUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80, sorter: true },
  { title: '用户', dataIndex: 'userName', width: 180 },
  { title: '简介', dataIndex: 'userProfile', ellipsis: true },
  { title: '角色', dataIndex: 'userRole', width: 120 },
  { title: 'VIP', dataIndex: 'vipCode', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 160, sorter: true },
  { title: '操作', key: 'action', width: 120, fixed: 'right' as const },
]

const dataList = ref<API.UserVO[]>([])
const total = ref<number>(0)
const loading = ref(false)
const editModalVisible = ref(false)
const updateLoading = ref(false)
const currentUser = ref<API.UserVO | null>(null)

const editForm = reactive({
  userName: '',
  userProfile: '',
  userRole: 'user',
})

const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  sortOrder: 'descend',
  sortField: 'createTime',
})

const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: Number(total.value),
  showSizeChanger: true,
  position: ['bottomCenter'] as const,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 位用户`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPageUsingPost(searchParams)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
    } else {
      message.error('获取数据失败：' + res.data.message)
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

const doTableChange = (pag: any, filters: any, sorter: any) => {
  searchParams.current = pag.current
  searchParams.pageSize = pag.pageSize
  if (sorter.field) {
    searchParams.sortField = sorter.field
    searchParams.sortOrder = sorter.order === 'ascend' ? 'ascend' : 'descend'
  }
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const resetSearch = () => {
  searchParams.userAccount = undefined
  searchParams.userName = undefined
  searchParams.userRole = undefined
  searchParams.current = 1
  fetchData()
}

const openEditModal = (record: API.UserVO) => {
  currentUser.value = record
  editForm.userName = record.userName || ''
  editForm.userProfile = record.userProfile || ''
  editForm.userRole = record.userRole || 'user'
  editModalVisible.value = true
}

const closeEditModal = () => {
  editModalVisible.value = false
  currentUser.value = null
}

const doUpdate = async () => {
  if (!currentUser.value?.id) return
  updateLoading.value = true
  try {
    const res = await updateUserUsingPost({
      id: currentUser.value.id,
      userName: editForm.userName,
      userProfile: editForm.userProfile,
      userRole: editForm.userRole,
    })
    if (res.data.code === 0) {
      message.success('用户信息已更新')
      closeEditModal()
      await fetchData()
    } else {
      message.error('更新失败：' + res.data.message)
    }
  } catch (error) {
    message.error('更新失败')
  } finally {
    updateLoading.value = false
  }
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deleteUserUsingPost({ id })
    if (res.data.code === 0) {
      message.success('用户已删除')
      await fetchData()
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}
</script>

<style scoped lang="less">
#userManagePage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--admin-space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--admin-space-8);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--admin-font);
  font-size: var(--admin-text-xl);
  font-weight: 700;
  margin: 0 0 var(--admin-space-2) 0;
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  color: var(--admin-text-primary);

  .title-icon {
    width: 32px;
    height: 32px;
    color: var(--admin-primary);
  }
}

.page-subtitle {
  font-size: var(--admin-text-sm);
  color: var(--admin-text-secondary);
  margin: 0;
}

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-4);
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  animation: slideUp 0.5s ease-out 0.1s backwards;
}

.search-box {
  position: relative;
  flex: 0 0 220px;

  .search-icon {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: var(--admin-text-tertiary);
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding-left: 36px;
    background: var(--admin-bg-tertiary);
    border: 1px solid var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.15);
    }
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  margin-left: auto;
}

.role-filter {
  width: 140px;

  :deep(.ant-select-selector) {
    background: var(--admin-bg-tertiary) !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }
}

.toolbar-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  padding: var(--admin-space-2) var(--admin-space-4);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  color: var(--admin-text-primary);
  background: var(--admin-bg-tertiary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all 0.15s ease;

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-strong);
  }

  &.primary {
    color: #ffffff;
    background: var(--admin-primary);
    border-color: var(--admin-primary);

    &:hover {
      background: var(--admin-primary-hover);
      border-color: var(--admin-primary-hover);
    }
  }
}

/* ========== 表格容器 ========== */
.table-card {
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  overflow: hidden;
  animation: slideUp 0.5s ease-out 0.2s backwards;
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
    color: var(--admin-text-primary);
  }

  :deep(.ant-table-wrapper) {
    .ant-table-container {
      border: none;
    }
  }

  :deep(.ant-table-thead > tr > th) {
    background: var(--admin-bg-secondary);
    border-bottom: 1px solid var(--admin-border-default);
    color: var(--admin-text-secondary);
    font-weight: 600;
    font-size: var(--admin-text-sm);
    padding: var(--admin-space-4);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--admin-border-subtle);
    color: var(--admin-text-primary);
    padding: var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background 0.15s ease;

    &:hover > td {
      background: var(--admin-bg-hover);
    }
  }

  :deep(.ant-table-column-sorter) {
    color: var(--admin-text-tertiary);
  }

  :deep(.ant-pagination) {
    padding: var(--admin-space-4);
    background: var(--admin-bg-primary);
    margin: 0;

    .ant-pagination-item {
      background: var(--admin-bg-tertiary);
      border-color: var(--admin-border-default);

      a {
        color: var(--admin-text-secondary);
      }

      &:hover {
        background: var(--admin-bg-hover);
        border-color: var(--admin-border-strong);
      }

      &.ant-pagination-item-active {
        background: var(--admin-primary);
        border-color: var(--admin-primary);

        a {
          color: #ffffff;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: var(--admin-bg-tertiary);
        border-color: var(--admin-border-default);
        color: var(--admin-text-secondary);

        &:hover {
          background: var(--admin-bg-hover);
          border-color: var(--admin-border-strong);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: var(--admin-bg-tertiary);
          border-color: var(--admin-border-default);
          color: var(--admin-text-primary);
        }
      }
    }
  }
}

/* ========== 用户信息 ========== */
.user-avatar {
  border: 2px solid var(--admin-border-default);
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .user-name {
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  .user-account {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-tertiary);
  }
}

.user-profile {
  color: var(--admin-text-secondary);
  font-size: var(--admin-text-sm);
}

/* ========== 角色标签 ========== */
.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  text-transform: uppercase;
  letter-spacing: 0.3px;

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.admin {
    background: var(--admin-primary-bg);
    color: var(--admin-primary);
    border: 1px solid rgba(9, 105, 218, 0.2);
  }

  &.user {
    background: var(--admin-bg-tertiary);
    color: var(--admin-text-secondary);
    border: 1px solid var(--admin-border-default);
  }
}

/* ========== VIP 标签 ========== */
.vip-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  background: #fff8c5;
  color: #9a6700;
  border: 1px solid rgba(154, 103, 0, 0.2);

  .vip-icon {
    width: 10px;
    height: 10px;
  }

  &.basic {
    background: var(--admin-bg-tertiary);
    color: var(--admin-text-tertiary);
    border: 1px solid var(--admin-border-default);
  }
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-tertiary);
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--admin-space-2);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: var(--admin-bg-tertiary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all 0.15s ease;

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border-color: rgba(9, 105, 218, 0.3);
  }

  &.danger:hover {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: rgba(207, 34, 46, 0.3);
  }
}

/* ========== 编辑弹窗 ========== */
.edit-modal {
  :deep(.ant-modal-content) {
    background: var(--admin-bg-primary);
    border: 1px solid var(--admin-border-default);
  }

  :deep(.ant-modal-header) {
    background: var(--admin-bg-primary);
    border-bottom: 1px solid var(--admin-border-subtle);

    .ant-modal-title {
      color: var(--admin-text-primary);
      font-weight: 600;
    }
  }

  :deep(.ant-modal-close) {
    color: var(--admin-text-secondary);

    &:hover {
      color: var(--admin-text-primary);
    }
  }
}

.edit-form {
  padding: var(--admin-space-4) 0;

  .form-avatar {
    display: flex;
    justify-content: center;
    margin-bottom: var(--admin-space-6);

    :deep(.ant-avatar) {
      border: 3px solid var(--admin-border-default);
    }
  }

  .form-item {
    margin-bottom: var(--admin-space-5);

    label {
      display: block;
      margin-bottom: var(--admin-space-2);
      font-size: var(--admin-text-sm);
      font-weight: 500;
      color: var(--admin-text-secondary);
    }

    .form-input {
      background: var(--admin-bg-tertiary);
      border: 1px solid var(--admin-border-default);
      color: var(--admin-text-primary);

      &:focus {
        border-color: var(--admin-primary);
        box-shadow: 0 0 0 3px rgba(9, 105, 218, 0.15);
      }

      &::placeholder {
        color: var(--admin-text-disabled);
      }
    }
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--admin-space-3);
    margin-top: var(--admin-space-6);
    padding-top: var(--admin-space-4);
    border-top: 1px solid var(--admin-border-subtle);
  }
}

.modal-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  padding: var(--admin-space-2) var(--admin-space-5);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all 0.15s ease;

  svg {
    width: 14px;
    height: 14px;
  }

  &.cancel {
    color: var(--admin-text-primary);
    background: var(--admin-bg-tertiary);
    border: 1px solid var(--admin-border-default);

    &:hover {
      background: var(--admin-bg-hover);
    }
  }

  &.primary {
    color: #ffffff;
    background: var(--admin-primary);
    border: 1px solid var(--admin-primary);

    &:hover {
      background: var(--admin-primary-hover);
      border-color: var(--admin-primary-hover);
    }
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
@media (max-width: 768px) {
  #userManagePage {
    padding: var(--admin-space-4);
  }

  .table-toolbar {
    flex-wrap: wrap;

    .search-box {
      flex: 1 1 100%;
    }

    .filter-buttons {
      width: 100%;
      margin-left: 0;
      justify-content: flex-end;
    }
  }

  .page-title {
    font-size: var(--admin-text-lg);
  }
}
</style>
