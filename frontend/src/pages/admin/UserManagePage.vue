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
  padding: var(--space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--space-8);
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

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
  padding: var(--space-4);
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
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
    color: #8b949e;
    pointer-events: none;
  }

  .search-input {
    width: 100%;
    padding-left: 36px;
    background: #0d1117;
    border: 1px solid #30363d;
    color: #c9d1d9;

    &::placeholder {
      color: #6e7681;
    }

    &:focus {
      border-color: #58a6ff;
      box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
    }
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-left: auto;
}

.role-filter {
  width: 140px;

  :deep(.ant-select-selector) {
    background: #0d1117 !important;
    border-color: #30363d !important;
    color: #c9d1d9 !important;
  }
}

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

/* ========== 表格容器 ========== */
.table-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: var(--radius-lg);
  overflow: hidden;
  animation: slideUp 0.5s ease-out 0.2s backwards;
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
    color: #c9d1d9;
  }

  :deep(.ant-table-wrapper) {
    .ant-table-container {
      border: none;
    }
  }

  :deep(.ant-table-thead > tr > th) {
    background: #21262d;
    border-bottom: 1px solid #30363d;
    color: #8b949e;
    font-weight: 600;
    font-size: var(--text-sm);
    padding: var(--space-4);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid #21262d;
    color: #c9d1d9;
    padding: var(--space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--transition-fast);

    &:hover > td {
      background: #1c2128;
    }
  }

  :deep(.ant-table-column-sorter) {
    color: #8b949e;
  }

  :deep(.ant-pagination) {
    padding: var(--space-4);
    background: #161b22;
    margin: 0;

    .ant-pagination-item {
      background: #21262d;
      border-color: #30363d;

      a {
        color: #c9d1d9;
      }

      &:hover {
        background: #30363d;
        border-color: #8b949e;
      }

      &.ant-pagination-item-active {
        background: #238636;
        border-color: #238636;

        a {
          color: #ffffff;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: #21262d;
        border-color: #30363d;
        color: #c9d1d9;

        &:hover {
          background: #30363d;
          border-color: #8b949e;
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: #21262d;
          border-color: #30363d;
          color: #c9d1d9;
        }
      }
    }
  }
}

/* ========== 用户信息 ========== */
.user-avatar {
  border: 2px solid #30363d;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .user-name {
    font-weight: 600;
    color: #c9d1d9;
  }

  .user-account {
    font-size: var(--text-xs);
    color: #8b949e;
  }
}

.user-profile {
  color: #8b949e;
  font-size: var(--text-sm);
}

/* ========== 角色标签 ========== */
.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-full);
  text-transform: uppercase;
  letter-spacing: 0.3px;

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.admin {
    background: rgba(88, 166, 255, 0.15);
    color: #58a6ff;
    border: 1px solid rgba(88, 166, 255, 0.3);
  }

  &.user {
    background: rgba(139, 148, 158, 0.15);
    color: #8b949e;
    border: 1px solid rgba(139, 148, 158, 0.3);
  }
}

/* ========== VIP 标签 ========== */
.vip-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--text-xs);
  font-weight: 600;
  border-radius: var(--radius-full);
  background: rgba(211, 152, 75, 0.15);
  color: #d29922;
  border: 1px solid rgba(211, 152, 75, 0.3);

  .vip-icon {
    width: 10px;
    height: 10px;
  }

  &.basic {
    background: rgba(139, 148, 158, 0.1);
    color: #6e7681;
    border: 1px solid rgba(139, 148, 158, 0.2);
  }
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: #8b949e;
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--space-2);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: #8b949e;
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
    color: #58a6ff;
    background: rgba(88, 166, 255, 0.1);
    border-color: rgba(88, 166, 255, 0.3);
  }

  &.danger:hover {
    color: #f85149;
    background: rgba(248, 81, 73, 0.1);
    border-color: rgba(248, 81, 73, 0.3);
  }
}

/* ========== 编辑弹窗 ========== */
.edit-modal {
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

  :deep(.ant-modal-close) {
    color: #8b949e;

    &:hover {
      color: #c9d1d9;
    }
  }
}

.edit-form {
  padding: var(--space-4) 0;

  .form-avatar {
    display: flex;
    justify-content: center;
    margin-bottom: var(--space-6);

    :deep(.ant-avatar) {
      border: 3px solid #30363d;
    }
  }

  .form-item {
    margin-bottom: var(--space-5);

    label {
      display: block;
      margin-bottom: var(--space-2);
      font-size: var(--text-sm);
      font-weight: 500;
      color: #8b949e;
    }

    .form-input {
      background: #0d1117;
      border: 1px solid #30363d;
      color: #c9d1d9;

      &:focus {
        border-color: #58a6ff;
        box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
      }

      &::placeholder {
        color: #6e7681;
      }
    }
  }

  .form-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--space-3);
    margin-top: var(--space-6);
    padding-top: var(--space-4);
    border-top: 1px solid #21262d;
  }
}

.modal-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-5);
  font-size: var(--text-sm);
  font-weight: 500;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &.cancel {
    color: #c9d1d9;
    background: #21262d;
    border: 1px solid #30363d;

    &:hover {
      background: #30363d;
    }
  }

  &.primary {
    color: #ffffff;
    background: #238636;
    border: 1px solid #238636;

    &:hover {
      background: #2ea043;
      border-color: #2ea043;
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
    padding: var(--space-4);
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
    font-size: var(--text-2xl);
  }
}
</style>
