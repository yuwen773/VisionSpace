<template>
  <div id="userManagePage">
    <!-- 页面头部 -->
    <AdminPageHeader title="用户管理" subtitle="管理系统用户和权限" size="lg">
      <template #icon>
        <Users :size="19" :stroke-width="1.6" />
      </template>
    </AdminPageHeader>

    <!-- 筛选工具栏 -->
    <div class="table-toolbar">
      <div class="search-box">
        <svg
          class="search-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <a-input
          v-model:value="searchParams.userAccount"
          aria-label="按用户账号搜索"
          placeholder="搜索用户账号..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="search-box">
        <svg
          class="search-icon"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.35-4.35"></path>
        </svg>
        <a-input
          v-model:value="searchParams.userName"
          aria-label="按用户名搜索"
          placeholder="搜索用户名..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-buttons">
        <div v-if="searchParams.id" class="active-filter">
          <span>仅看用户 ID {{ searchParams.id }}</span>
          <button type="button" aria-label="清除用户 ID 筛选" @click="clearUserIdFilter">×</button>
        </div>
        <a-select
          v-model:value="searchParams.userRole"
          aria-label="按用户角色筛选"
          placeholder="角色筛选"
          class="role-filter"
          allow-clear
          @change="doSearch"
        >
          <a-select-option value="admin">管理员</a-select-option>
          <a-select-option value="moderator">内容处置员</a-select-option>
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
      <div v-if="loadError" class="load-error" role="alert">
        <div class="load-error-text">
          <strong>用户列表没能加载</strong>
          <span>网络或服务暂时不可用，下方不是最新数据。</span>
        </div>
        <button class="toolbar-btn" @click="fetchData">重试</button>
      </div>
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        @change="doTableChange"
        :loading="loading"
        :rowKey="(record: API.UserVO) => record.id"
        :scroll="tableScroll"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userName'">
            <div class="user-cell">
              <a-avatar :src="record.userAvatar" :size="36" class="user-avatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="user-info">
                <span class="user-name">{{ record.userName || '未设置' }}</span>
                <span class="user-account">@{{ record.userAccount }}</span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <span class="role-tag" :class="roleTagClass(record.userRole)">
              {{ roleText(record.userRole) }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'vipNumber'">
            <span class="vip-info" v-if="record.vipNumber">
              <svg class="vip-icon" viewBox="0 0 24 24" fill="currentColor">
                <path
                  d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"
                />
              </svg>
              VIP {{ record.vipNumber }}
            </span>
            <span class="vip-expire" v-else-if="record.vipExpireTime">
              VIP {{ dayjs(record.vipExpireTime).format('YYYY-MM') }}
            </span>
            <span class="vip-none" v-else>-</span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-tooltip title="编辑">
                <button class="action-btn" aria-label="编辑用户" @click="openEditModal(record)">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                  </svg>
                </button>
              </a-tooltip>
              <button
                class="action-btn danger"
                aria-label="删除用户"
                @click="confirmDelete(record)"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"></polyline>
                  <path
                    d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
                  ></path>
                </svg>
              </button>
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
          <label for="edit-user-account">用户账号</label>
          <a-input
            id="edit-user-account"
            :value="currentUser.userAccount"
            disabled
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label for="edit-user-name">用户名</label>
          <a-input
            id="edit-user-name"
            v-model:value="editForm.userName"
            placeholder="请输入用户名"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label for="edit-user-profile">用户简介</label>
          <a-textarea
            id="edit-user-profile"
            v-model:value="editForm.userProfile"
            placeholder="请输入用户简介"
            :rows="3"
            class="form-input"
          />
        </div>
        <div class="form-item">
          <label for="edit-user-role">用户角色</label>
          <a-select id="edit-user-role" v-model:value="editForm.userRole" class="form-input">
            <a-select-option value="admin">管理员</a-select-option>
            <a-select-option value="moderator">内容处置员</a-select-option>
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
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { Users } from 'lucide-vue-next'
import { ref, reactive, onMounted, computed } from 'vue'
import {
  deleteUserUsingPost,
  listUserVoByPageUsingPost,
  updateUserUsingPost,
} from '@/api/userController.ts'
import { message, Modal } from 'ant-design-vue'
import { UserOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { useRoute, useRouter } from 'vue-router'
import { useAdminTableDensity } from '@/composables/useAdminTableDensity'
import { useUndoable } from '@/composables/useUndoable'

const density = useAdminTableDensity()
const route = useRoute()
const router = useRouter()
const undo = useUndoable()

// 手机上收掉 VIP 与注册时间：这两个字段只在处置账号时才查，平时看「谁、什么角色」就够
const columns = computed(() => {
  const user = { title: '用户', dataIndex: 'userName', width: 200 }
  const role = { title: '角色', dataIndex: 'userRole', width: 120 }
  const vip = { title: 'VIP', dataIndex: 'vipNumber', width: 120 }
  const createTime = { title: '注册时间', dataIndex: 'createTime', width: 140, sorter: true }
  const action = { title: '操作', key: 'action', width: 100 }

  if (density.value === 'phone') {
    return [{ ...user, width: 170 }, { ...role, width: 90 }, action]
  }
  return [user, role, vip, createTime, action]
})

const tableScroll = computed(() => ({ x: density.value === 'phone' ? 360 : 680 }))

/** 角色文案。处置角色独立于平台管理员，两者互不继承 */
const roleText = (role?: string) => {
  const map: Record<string, string> = {
    admin: '管理员',
    moderator: '内容处置员',
    user: '普通用户',
  }
  return map[role ?? ''] || '普通用户'
}

/** 角色标签用 token 紫 / 墨，不再借用 Ant 预设的 blue / purple */
const roleTagClass = (role?: string) => {
  const map: Record<string, string> = { admin: 'admin', moderator: 'moderator' }
  return map[role ?? ''] || 'user'
}

const dataList = ref<API.UserVO[]>([])
const total = ref<number>(0)
const loading = ref(false)
const loadError = ref(false)
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
      loadError.value = false
    } else {
      loadError.value = true
      message.error('获取数据失败：' + res.data.message)
    }
  } catch {
    loadError.value = true
    message.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 空间管理的所有者列会带着 userId 跳过来，落地即按该用户过滤
  const userId = Number(route.query.userId)
  if (Number.isFinite(userId) && userId > 0) {
    searchParams.id = userId
  }
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
  searchParams.id = undefined
  searchParams.current = 1
  router.replace({ query: {} })
  fetchData()
}

const clearUserIdFilter = () => {
  searchParams.id = undefined
  searchParams.current = 1
  router.replace({ query: {} })
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

const confirmDelete = (record: API.UserVO) => {
  Modal.confirm({
    title: `删除用户「${record.userName || record.userAccount}」？`,
    content: '该用户的登录与个人资料会被移除。5 秒内可撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => applyDelete(record),
  })
}

const applyDelete = (record: API.UserVO) => {
  const id = record.id
  if (!id) return
  const index = dataList.value.findIndex((row) => row.id === id)
  if (index < 0) return
  dataList.value.splice(index, 1)
  total.value = Math.max(0, total.value - 1)

  undo.schedule({
    key: `user-delete-${id}`,
    message: `「${record.userName || record.userAccount}」已删除`,
    commit: async () => {
      try {
        const res = await deleteUserUsingPost({ id })
        if (res.data.code !== 0) {
          message.error(res.data.message || '删除失败，已恢复')
        }
      } catch {
        message.error('删除失败，已恢复')
      } finally {
        await fetchData()
      }
    },
    undo: () => {
      dataList.value.splice(Math.min(index, dataList.value.length), 0, record)
      total.value += 1
    },
  })
}
</script>

<style scoped lang="less">
#userManagePage {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0;
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
  border-radius: var(--admin-radius-sm);
  animation: slideUp 0.45s var(--admin-ease) 0.05s backwards;
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
    background: transparent;
    border: 1px solid var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 2px var(--admin-primary-bg);
    }
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  margin-left: auto;
}

/* 从空间管理所有者列跳过来时的可见筛选条件 */
.active-filter {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 6px 0 10px;
  font-size: var(--admin-text-xs);
  color: var(--admin-primary);
  background: var(--admin-primary-bg);
  border: 1px solid color-mix(in srgb, var(--admin-primary) 26%, transparent);
  border-radius: var(--admin-radius-sm);
  white-space: nowrap;

  button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    padding: 0;
    color: inherit;
    background: transparent;
    border: none;
    border-radius: var(--admin-radius-sm);
    cursor: pointer;

    &:hover {
      background: var(--admin-bg-hover);
    }

    &:focus-visible {
      outline: 2px solid var(--admin-primary);
      outline-offset: 1px;
    }
  }
}

.role-filter {
  width: 140px;

  :deep(.ant-select-selector) {
    background: transparent !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }
}

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

/* ========== 表格容器 ========== */
.table-card {
  background: var(--admin-bg-primary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  overflow: hidden;
  animation: slideUp 0.45s var(--admin-ease) 0.1s backwards;
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
    background: transparent;
    border-bottom: 1px solid var(--admin-border-strong);
    color: var(--admin-text-tertiary);
    font-family: var(--admin-font-mono);
    font-weight: 500;
    font-size: 11px;
    padding: var(--admin-space-3) var(--admin-space-4);
    text-transform: uppercase;
    letter-spacing: 0.08em;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--admin-border-subtle);
    color: var(--admin-text-primary);
    padding: var(--admin-space-3) var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--admin-transition-fast);

    &:hover > td {
      background: var(--admin-bg-tertiary);
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
      background: transparent;
      border-color: var(--admin-border-default);

      a {
        color: var(--admin-text-secondary);
      }

      &:hover {
        background: var(--admin-primary-bg);
        border-color: var(--admin-primary);
      }

      &.ant-pagination-item-active {
        background: var(--admin-primary);
        border-color: var(--admin-primary);

        a {
          color: var(--admin-accent-ink);
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

/* ========== 用户单元格 ========== */
.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  flex-shrink: 0;
  background: var(--admin-primary);
}

.user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;

  .user-name {
    font-weight: 600;
    color: var(--admin-text-primary);
    white-space: nowrap;
  }

  .user-account {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-tertiary);
  }
}

/* ========== 角色标签 ========== */
/* 只使用 token 里的紫与墨：管理员=主色，内容处置员=次级墨色，普通用户=弱化 */
.role-tag {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  letter-spacing: 0.02em;
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-sm);
  color: var(--admin-text-secondary);

  &.admin {
    color: var(--admin-primary);
    border-color: color-mix(in srgb, var(--admin-primary) 38%, transparent);
    background: var(--admin-primary-bg);
  }

  &.moderator {
    color: var(--admin-text-primary);
    border-color: var(--admin-border-strong);
    background: var(--admin-bg-hover);
  }

  &.user {
    color: var(--admin-text-tertiary);
  }
}

/* ========== VIP 信息 ========== */
.vip-info {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--admin-warning);
  font-size: var(--admin-text-sm);

  .vip-icon {
    width: 13px;
    height: 13px;
    color: var(--admin-warning);
  }
}

.vip-expire {
  color: var(--admin-text-secondary);
  font-size: var(--admin-text-sm);
}

.vip-none {
  color: var(--admin-text-disabled);
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
  white-space: nowrap;
}

/* 列表加载失败：保留旧数据，同时明确说明它不是最新的 */
.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--admin-space-4);
  padding: var(--admin-space-3) var(--admin-space-4);
  background: var(--admin-danger-bg);
  border-bottom: 1px solid color-mix(in srgb, var(--admin-danger) 24%, transparent);

  .load-error-text {
    display: flex;
    flex-direction: column;
    gap: 2px;

    strong {
      font-size: var(--admin-text-sm);
      color: var(--admin-text-primary);
    }

    span {
      font-size: var(--admin-text-xs);
      color: var(--admin-text-secondary);
    }
  }
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
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-sm);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 15px;
    height: 15px;
  }

  &:hover {
    color: var(--admin-primary);
    background: var(--admin-bg-hover);
    border-color: var(--admin-border-default);
  }

  &.danger:hover {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: color-mix(in srgb, var(--admin-danger) 30%, transparent);
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
      background: transparent;
      border: 1px solid var(--admin-border-default);
      color: var(--admin-text-primary);

      &:focus {
        border-color: var(--admin-primary);
        box-shadow: 0 0 0 2px var(--admin-primary-bg);
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
  height: 36px;
  padding: 0 var(--admin-space-5);
  font-size: var(--admin-text-sm);
  font-weight: 600;
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

  &.cancel {
    color: var(--admin-text-primary);
    background: transparent;
    border: 1px solid var(--admin-border-default);

    &:hover {
      background: var(--admin-bg-hover);
    }
  }

  &.primary {
    color: var(--admin-accent-ink);
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
@media (max-width: 1024px) {
  .table-toolbar {
    flex-wrap: wrap;

    .search-box {
      flex: 1 1 240px;
    }
  }
}

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
}
</style>
