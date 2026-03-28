<template>
  <div id="spaceUserManagePage">
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
          <span>空间成员管理</span>
        </h1>
        <p class="page-subtitle">管理空间成员和角色权限</p>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <span class="member-count">
          <svg class="count-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
          共 {{ dataList.length }} 位成员
        </span>
      </div>
      <div class="toolbar-right">
        <div class="search-box">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="m21 21-4.35-4.35"></path>
          </svg>
          <a-input
            v-model:value="searchKeyword"
            placeholder="搜索成员..."
            class="search-input"
            allow-clear
          />
        </div>
      </div>
    </div>

    <!-- 添加成员表单 -->
    <div class="add-member-card">
      <div class="add-member-form">
        <div class="form-label">
          <svg class="label-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="8.5" cy="7" r="4"></circle>
            <line x1="20" y1="8" x2="20" y2="14"></line>
            <line x1="23" y1="11" x2="17" y2="11"></line>
          </svg>
          添加成员
        </div>
        <div class="form-row">
          <a-form-item label="用户ID" name="userId">
            <a-input
              v-model:value="formData.userId"
              placeholder="请输入用户ID"
              class="form-input"
            />
          </a-form-item>
          <a-form-item label="角色" name="spaceRole">
            <a-select
              v-model:value="formData.spaceRole"
              :options="SPACE_ROLE_OPTIONS"
              placeholder="选择角色"
              class="role-select"
            />
          </a-form-item>
          <button html-type="submit" class="submit-btn" @click="handleSubmit" :loading="submitLoading">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
              <circle cx="8.5" cy="7" r="4"></circle>
              <line x1="20" y1="8" x2="20" y2="14"></line>
              <line x1="23" y1="11" x2="17" y2="11"></line>
            </svg>
            添加成员
          </button>
        </div>
      </div>
    </div>

    <!-- 成员列表 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="filteredDataList"
        :loading="loading"
        :pagination="paginationConfig"
        :rowKey="(record: API.SpaceUserVO) => record.id"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userInfo'">
            <div class="user-info">
              <a-avatar :src="record.user?.userAvatar" :size="44" class="user-avatar">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <div class="user-details">
                <div class="user-name">{{ record.user?.userName || '未知用户' }}</div>
                <div class="user-account">@{{ record.user?.userAccount || record.userId }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceRole'">
            <div class="role-cell">
              <a-select
                v-model:value="record.spaceRole"
                :options="SPACE_ROLE_OPTIONS"
                :loading="roleEditLoading"
                @change="(value: string) => editSpaceRole(value, record)"
                class="role-select"
              />
              <span class="role-badge" :class="record.spaceRole">
                <svg v-if="record.spaceRole === 'admin'" class="badge-icon" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/>
                </svg>
                <svg v-else-if="record.spaceRole === 'editor'" class="badge-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                </svg>
                <svg v-else class="badge-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
                {{ SPACE_ROLE_MAP[record.spaceRole] || record.spaceRole }}
              </span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'permissions'">
            <div class="permissions-cell">
              <a-tooltip
                v-for="perm in getPermissionsForRole(record.spaceRole)"
                :key="perm"
                :title="PERMISSION_MAP[perm] || perm"
              >
                <span class="permission-tag" :class="perm.split(':')[1]">
                  {{ PERMISSION_TAG_MAP[perm] || perm.split(':')[1] }}
                </span>
              </a-tooltip>
            </div>
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
                ok-text="移除"
                cancel-text="取消"
                @confirm="doDelete(record.id)"
                placement="topRight"
              >
                <a-tooltip title="移除成员">
                  <button class="action-btn danger">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                      <circle cx="8.5" cy="7" r="4"></circle>
                      <line x1="18" y1="8" x2="23" y2="13"></line>
                      <line x1="23" y1="8" x2="18" y2="13"></line>
                    </svg>
                  </button>
                </a-tooltip>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 返回按钮 -->
    <div class="back-section">
      <button @click="goBack" class="back-btn">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="19" y1="12" x2="5" y2="12"></line>
          <polyline points="12 19 5 12 12 5"></polyline>
        </svg>
        返回空间详情
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { UserOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { SPACE_ROLE_OPTIONS, SPACE_ROLE_MAP } from '@/constants/space.ts'
import {
  addSpaceUserUsingPost,
  deleteSpaceUserUsingPost,
  editSpaceUserUsingPost,
  listSpaceUserUsingPost,
} from '@/api/spaceUserController.ts'

// 权限映射
const PERMISSION_MAP: Record<string, string> = {
  'spaceUser:manage': '成员管理',
  'picture:view': '图片查看',
  'picture:upload': '图片上传',
  'picture:edit': '图片编辑',
  'picture:delete': '图片删除',
}

const PERMISSION_TAG_MAP: Record<string, string> = {
  'spaceUser:manage': '管理',
  'picture:view': '查看',
  'picture:upload': '上传',
  'picture:edit': '编辑',
  'picture:delete': '删除',
}

// 角色对应的权限
const ROLE_PERMISSIONS: Record<string, string[]> = {
  viewer: ['picture:view'],
  editor: ['picture:view', 'picture:upload', 'picture:edit'],
  admin: ['spaceUser:manage', 'picture:view', 'picture:upload', 'picture:edit', 'picture:delete'],
}

interface Props {
  spaceId: string
}

const props = defineProps<Props>()
const router = useRouter()

const columns = [
  { title: '成员', dataIndex: 'userInfo', width: 280 },
  { title: '角色', dataIndex: 'spaceRole', width: 200 },
  { title: '权限', dataIndex: 'permissions', width: 320 },
  { title: '加入时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 80, fixed: 'right' as const },
]

const dataList = ref<API.SpaceUserVO[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const roleEditLoading = ref(false)
const searchKeyword = ref('')
const formData = reactive<API.SpaceUserAddRequest & { spaceRole?: string }>({
  userId: undefined,
  spaceRole: 'viewer',
})

const searchParams = ref({
  current: 1,
  pageSize: 20,
})

const paginationConfig = computed(() => ({
  current: searchParams.value.current,
  pageSize: searchParams.value.pageSize,
  total: dataList.value.length,
  showSizeChanger: true,
  position: ['bottomCenter'] as const,
  showTotal: (total: number) => `共 ${total} 位成员`,
  onChange: (page: number, pageSize: number) => {
    searchParams.value.current = page
    searchParams.value.pageSize = pageSize
  },
}))

// 根据角色获取权限列表
const getPermissionsForRole = (role: string | undefined) => {
  if (!role) return []
  return ROLE_PERMISSIONS[role] || []
}

// 过滤数据
const filteredDataList = computed(() => {
  if (!searchKeyword.value) return dataList.value
  const keyword = searchKeyword.value.toLowerCase()
  return dataList.value.filter((item) => {
    const userName = item.user?.userName?.toLowerCase() || ''
    const userAccount = item.user?.userAccount?.toLowerCase() || ''
    const userId = String(item.userId || '')
    return userName.includes(keyword) || userAccount.includes(keyword) || userId.includes(keyword)
  })
})

const fetchData = async () => {
  if (!props.spaceId) return
  loading.value = true
  try {
    const res = await listSpaceUserUsingPost({ spaceId: props.spaceId })
    if (res.data.code === 0) {
      dataList.value = res.data.data ?? []
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

const handleSubmit = async () => {
  const spaceId = props.spaceId
  if (!spaceId) return
  if (!formData.userId) {
    message.warning('请输入用户ID')
    return
  }
  submitLoading.value = true
  try {
    const res = await addSpaceUserUsingPost({
      ...formData,
      spaceId: spaceId,
    })
    if (res.data.code === 0) {
      message.success('成员添加成功')
      Object.assign(formData, { userId: undefined, spaceRole: 'viewer' })
      await fetchData()
    } else {
      message.error('添加失败：' + res.data.message)
    }
  } catch (error) {
    message.error('添加失败')
  } finally {
    submitLoading.value = false
  }
}

const editSpaceRole = async (value: string, record: API.SpaceUserVO) => {
  roleEditLoading.value = true
  try {
    const res = await editSpaceUserUsingPost({
      id: record.id,
      spaceRole: value,
    })
    if (res.data.code === 0) {
      message.success('角色修改成功')
    } else {
      message.error('修改失败：' + res.data.message)
      await fetchData()
    }
  } catch (error) {
    message.error('修改失败')
    await fetchData()
  } finally {
    roleEditLoading.value = false
  }
}

const doDelete = async (id: number | undefined) => {
  if (!id) return
  try {
    const res = await deleteSpaceUserUsingPost({ id })
    if (res.data.code === 0) {
      message.success('成员已移除')
      await fetchData()
    } else {
      message.error('移除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('移除失败')
  }
}

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="less">
#spaceUserManagePage {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding: var(--admin-space-6);
}

/* ========== 页面头部 ========== */
.page-header {
  margin-bottom: var(--admin-space-6);
  animation: fadeIn 0.5s ease-out;
}

.header-content {
  text-align: left;
}

.page-title {
  font-family: var(--admin-font);
  font-size: var(--admin-text-3xl);
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
  justify-content: space-between;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-4);
  background: var(--admin-bg-secondary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  animation: slideUp 0.5s ease-out 0.1s backwards;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--admin-space-4);
}

.member-count {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  color: var(--admin-text-secondary);

  .count-icon {
    width: 16px;
    height: 16px;
    color: var(--admin-primary);
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.search-box {
  position: relative;

  .search-icon {
    position: absolute;
    left: 12px;
    top: 50%;
    transform: translateY(-50%);
    width: 16px;
    height: 16px;
    color: var(--admin-text-secondary);
    pointer-events: none;
  }

  .search-input {
    width: 240px;
    padding-left: 36px;
    background: var(--admin-bg-primary);
    border: 1px solid var(--admin-border-default);
    color: var(--admin-text-primary);

    &::placeholder {
      color: var(--admin-text-disabled);
    }

    &:focus {
      border-color: var(--admin-primary);
      box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
    }
  }
}

/* ========== 添加成员卡片 ========== */
.add-member-card {
  background: var(--admin-bg-secondary);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-lg);
  padding: var(--admin-space-5);
  margin-bottom: var(--admin-space-4);
  animation: slideUp 0.5s ease-out 0.15s backwards;
}

.add-member-form {
  .form-label {
    display: flex;
    align-items: center;
    gap: var(--admin-space-2);
    font-size: var(--admin-text-sm);
    font-weight: 600;
    color: var(--admin-text-secondary);
    margin-bottom: var(--admin-space-4);

    .label-icon {
      width: 16px;
      height: 16px;
      color: var(--admin-primary);
    }
  }

  .form-row {
    display: flex;
    align-items: flex-end;
    gap: var(--admin-space-4);
    flex-wrap: wrap;
  }

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }

  :deep(.ant-form-item-label) {
    > label {
      color: var(--admin-text-secondary);
      font-weight: 500;
      font-size: var(--admin-text-sm);
    }
  }
}

.form-input {
  width: 200px;
  background: var(--admin-bg-primary) !important;
  border-color: var(--admin-border-default) !important;
  color: var(--admin-text-primary) !important;

  &::placeholder {
    color: var(--admin-text-disabled);
  }

  &:focus {
    border-color: var(--admin-primary) !important;
    box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15) !important;
  }
}

.role-select {
  width: 140px;

  :deep(.ant-select-selector) {
    background: var(--admin-bg-primary) !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }

  :deep(.ant-select-arrow) {
    color: var(--admin-text-secondary);
  }
}

.submit-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  padding: var(--admin-space-2) var(--admin-space-5);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  color: #ffffff;
  background: var(--admin-success);
  border: 1px solid var(--admin-success);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: #2ea043;
    border-color: #2ea043;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* ========== 表格容器 ========== */
.table-card {
  background: var(--admin-bg-secondary);
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
    background: var(--admin-bg-hover);
    border-bottom: 1px solid var(--admin-border-default);
    color: var(--admin-text-secondary);
    font-weight: 600;
    font-size: var(--admin-text-sm);
    padding: var(--admin-space-4);
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--admin-bg-hover);
    color: var(--admin-text-primary);
    padding: var(--admin-space-4);
  }

  :deep(.ant-table-tbody > tr) {
    transition: background var(--admin-transition-fast);

    &:hover > td {
      background: var(--admin-bg-hover);
    }
  }

  :deep(.ant-pagination) {
    padding: var(--admin-space-4);
    background: var(--admin-bg-secondary);
    margin: 0;

    .ant-pagination-item {
      background: var(--admin-bg-hover);
      border-color: var(--admin-border-default);

      a {
        color: var(--admin-text-primary);
      }

      &:hover {
        background: var(--admin-border-default);
        border-color: var(--admin-text-secondary);
      }

      &.ant-pagination-item-active {
        background: var(--admin-success);
        border-color: var(--admin-success);

        a {
          color: #ffffff;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: var(--admin-bg-hover);
        border-color: var(--admin-border-default);
        color: var(--admin-text-primary);

        &:hover {
          background: var(--admin-border-default);
          border-color: var(--admin-text-secondary);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: var(--admin-bg-hover);
          border-color: var(--admin-border-default);
          color: var(--admin-text-primary);
        }
      }
    }
  }
}

/* ========== 用户信息 ========== */
.user-info {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.user-avatar {
  border: 2px solid var(--admin-border-default);
  flex-shrink: 0;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  .user-name {
    font-weight: 600;
    color: var(--admin-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-account {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

/* ========== 角色单元格 ========== */
.role-cell {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.role-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-full);
  text-transform: uppercase;
  letter-spacing: 0.3px;

  .badge-icon {
    width: 12px;
    height: 12px;
  }

  &.admin {
    background: rgba(88, 166, 255, 0.15);
    color: var(--admin-primary);
    border: 1px solid rgba(88, 166, 255, 0.3);
  }

  &.editor {
    background: rgba(63, 185, 80, 0.15);
    color: #3fb950;
    border: 1px solid rgba(63, 185, 80, 0.3);
  }

  &.viewer {
    background: rgba(139, 148, 158, 0.15);
    color: var(--admin-text-secondary);
    border: 1px solid rgba(139, 148, 158, 0.3);
  }
}

/* ========== 权限标签 ========== */
.permissions-cell {
  display: flex;
  flex-wrap: wrap;
  gap: var(--admin-space-2);
}

.permission-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: var(--admin-text-xs);
  font-weight: 500;
  border-radius: var(--admin-radius-sm);
  cursor: default;
  transition: all var(--admin-transition-fast);

  &.view {
    background: rgba(88, 166, 255, 0.1);
    color: var(--admin-primary);
    border: 1px solid rgba(88, 166, 255, 0.2);
  }

  &.upload {
    background: rgba(63, 185, 80, 0.1);
    color: #3fb950;
    border: 1px solid rgba(63, 185, 80, 0.2);
  }

  &.edit {
    background: rgba(210, 153, 34, 0.1);
    color: #d29922;
    border: 1px solid rgba(210, 153, 34, 0.2);
  }

  &.delete {
    background: rgba(248, 81, 73, 0.1);
    color: var(--admin-danger);
    border: 1px solid rgba(248, 81, 73, 0.2);
  }

  &.manage {
    background: rgba(163, 113, 247, 0.1);
    color: #a371f7;
    border: 1px solid rgba(163, 113, 247, 0.2);
  }

  &:hover {
    transform: translateY(-1px);
  }
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
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
  background: var(--admin-bg-hover);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    color: var(--admin-primary);
    background: rgba(88, 166, 255, 0.1);
    border-color: rgba(88, 166, 255, 0.3);
  }

  &.danger:hover {
    color: var(--admin-danger);
    background: rgba(248, 81, 73, 0.1);
    border-color: rgba(248, 81, 73, 0.3);
  }
}

/* ========== 返回区域 ========== */
.back-section {
  display: flex;
  justify-content: flex-start;
  margin-top: var(--admin-space-6);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  padding: var(--admin-space-2) var(--admin-space-4);
  font-size: var(--admin-text-sm);
  font-weight: 500;
  color: var(--admin-text-primary);
  background: var(--admin-bg-hover);
  border: 1px solid var(--admin-border-default);
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    background: var(--admin-border-default);
    border-color: var(--admin-text-secondary);
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
  #spaceUserManagePage {
    padding: var(--admin-space-4);
  }

  .page-title {
    font-size: var(--admin-text-2xl);
  }

  .table-toolbar {
    flex-direction: column;
    align-items: stretch;

    .toolbar-right {
      width: 100%;
    }

    .search-box {
      width: 100%;

      .search-input {
        width: 100%;
      }
    }
  }

  .add-member-card {
    .form-row {
      flex-direction: column;
      align-items: stretch;
    }

    .form-input,
    .role-select {
      width: 100%;
    }

    .submit-btn {
      width: 100%;
      justify-content: center;
    }
  }

  .table-wrapper {
    padding: var(--admin-space-4);
  }

  :deep(.ant-table) {
    font-size: var(--admin-text-sm);
  }
}
</style>
