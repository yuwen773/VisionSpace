<template>
  <div id="spaceUserManagePage">
    <!-- 紫漾氛围背景 -->
    <div class="ambient-bg">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
      <div class="gradient-orb orb-3"></div>
      <div class="floating-shapes">
        <div class="shape-dot dot-1"></div>
        <div class="shape-dot dot-2"></div>
        <div class="shape-dot dot-3"></div>
      </div>
    </div>

    <!-- 页面内容 -->
    <div class="page-content">
      <!-- 页面头部 -->
      <div class="page-header">
        <button @click="goBack" class="back-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
          <span>返回</span>
        </button>
        <div class="header-info">
          <h1 class="page-title">空间成员管理</h1>
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
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow-x: hidden;
}

/* ========== 紫漾氛围背景 ========== */
.ambient-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.5;
  animation: float-orb 25s ease-in-out infinite;
}

.orb-1 {
  width: 700px;
  height: 700px;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.25) 0%, transparent 70%);
  top: -250px;
  right: -150px;
  animation-delay: 0s;
}

.orb-2 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.2) 0%, transparent 70%);
  top: 30%;
  left: -200px;
  animation-delay: -8s;
}

.orb-3 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.15) 0%, transparent 70%);
  bottom: -100px;
  right: 10%;
  animation-delay: -16s;
}

@keyframes float-orb {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(40px, -40px) scale(1.05); }
  50% { transform: translate(-30px, 30px) scale(0.95); }
  75% { transform: translate(25px, 15px) scale(1.03); }
}

.floating-shapes {
  position: absolute;
  inset: 0;
}

.shape-dot {
  position: absolute;
  border-radius: 50%;
  opacity: 0.4;
  animation: float-dot 15s ease-in-out infinite;
}

.dot-1 {
  width: 12px;
  height: 12px;
  background: var(--color-primary);
  top: 15%;
  left: 10%;
  animation-delay: 0s;
}

.dot-2 {
  width: 8px;
  height: 8px;
  background: var(--color-secondary);
  top: 25%;
  right: 20%;
  animation-delay: -3s;
}

.dot-3 {
  width: 16px;
  height: 16px;
  background: var(--color-violet);
  bottom: 30%;
  left: 15%;
  animation-delay: -6s;
}

@keyframes float-dot {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  25% { transform: translate(10px, -15px) rotate(90deg); }
  50% { transform: translate(-5px, 10px) rotate(180deg); }
  75% { transform: translate(15px, 5px) rotate(270deg); }
}

/* ========== 页面内容 ========== */
.page-content {
  position: relative;
  z-index: 1;
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 32px 80px;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-card);
  border: 2px solid var(--border-default);
  border-radius: 40px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-sm);

  &:hover {
    background: var(--bg-hover);
    border-color: var(--color-primary);
    transform: translateX(-4px);
    box-shadow: var(--shadow-glow-purple);
  }
}

.header-info {
  flex: 1;
}

.page-title {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  background: linear-gradient(135deg, #a855f7 0%, #ec4899 50%, #f472b6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

/* ========== 工具栏 ========== */
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-card);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.member-count {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);

  .count-icon {
    width: 16px;
    height: 16px;
    color: var(--color-primary);
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
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
    color: var(--text-tertiary);
    pointer-events: none;
  }

  .search-input {
    width: 240px;
    padding-left: 36px;
    background: var(--bg-tertiary) !important;
    border: 1px solid var(--border-default) !important;
    color: var(--text-primary) !important;
    border-radius: 12px;

    &::placeholder {
      color: var(--text-tertiary);
    }

    &:focus {
      border-color: var(--color-primary) !important;
      box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.1) !important;
    }
  }
}

/* ========== 添加成员卡片 ========== */
.add-member-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-card);
}

.add-member-form {
  .form-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-secondary);
    margin-bottom: 16px;

    .label-icon {
      width: 16px;
      height: 16px;
      color: var(--color-primary);
    }
  }

  .form-row {
    display: flex;
    align-items: flex-end;
    gap: 16px;
    flex-wrap: wrap;
  }

  :deep(.ant-form-item) {
    margin-bottom: 0;
  }

  :deep(.ant-form-item-label) {
    > label {
      color: var(--text-secondary);
      font-weight: 500;
      font-size: 14px;
    }
  }
}

.form-input {
  width: 200px;
  background: var(--bg-tertiary) !important;
  border: 1px solid var(--border-default) !important;
  color: var(--text-primary) !important;
  border-radius: 12px;

  &::placeholder {
    color: var(--text-tertiary);
  }

  &:focus {
    border-color: var(--color-primary) !important;
    box-shadow: 0 0 0 3px rgba(168, 85, 247, 0.1) !important;
  }
}

.role-select {
  width: 140px;

  :deep(.ant-select-selector) {
    background: var(--bg-tertiary) !important;
    border: 1px solid var(--border-default) !important;
    color: var(--text-primary) !important;
    border-radius: 12px !important;
  }

  :deep(.ant-select-arrow) {
    color: var(--text-secondary);
  }
}

.submit-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  color: white;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(168, 85, 247, 0.3);

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 20px rgba(168, 85, 247, 0.4);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

/* ========== 表格容器 ========== */
.table-card {
  background: var(--bg-card);
  border: 1px solid var(--border-default);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

/* ========== 表格样式 ========== */
.data-table {
  :deep(.ant-table) {
    background: transparent;
    color: var(--text-primary);
  }

  :deep(.ant-table-wrapper) {
    .ant-table-container {
      border: none;
    }
  }

  :deep(.ant-table-thead > tr > th) {
    background: var(--bg-tertiary);
    border-bottom: 1px solid var(--border-default);
    color: var(--text-secondary);
    font-weight: 600;
    font-size: 14px;
    padding: 16px;
  }

  :deep(.ant-table-tbody > tr > td) {
    border-bottom: 1px solid var(--border-subtle);
    color: var(--text-primary);
    padding: 16px;
  }

  :deep(.ant-table-tbody > tr) {
    transition: background 0.2s ease;

    &:hover > td {
      background: var(--bg-hover);
    }
  }

  :deep(.ant-pagination) {
    padding: 16px;
    background: var(--bg-card);
    margin: 0;

    .ant-pagination-item {
      background: var(--bg-tertiary);
      border-color: var(--border-default);

      a {
        color: var(--text-primary);
      }

      &:hover {
        background: var(--bg-hover);
        border-color: var(--color-primary);
      }

      &.ant-pagination-item-active {
        background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
        border-color: var(--color-primary);

        a {
          color: white;
        }
      }
    }

    .ant-pagination-prev,
    .ant-pagination-next {
      .ant-pagination-item-link {
        background: var(--bg-tertiary);
        border-color: var(--border-default);
        color: var(--text-primary);

        &:hover {
          background: var(--bg-hover);
          border-color: var(--color-primary);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: var(--bg-tertiary);
          border-color: var(--border-default);
          color: var(--text-primary);
        }
      }
    }
  }
}

/* ========== 用户信息 ========== */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  border: 2px solid var(--border-default);
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  .user-name {
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-account {
    font-size: 12px;
    color: var(--text-tertiary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}

/* ========== 角色单元格 ========== */
.role-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 20px;

  .badge-icon {
    width: 12px;
    height: 12px;
  }

  &.admin {
    background: rgba(168, 85, 247, 0.15);
    color: var(--color-primary);
    border: 1px solid rgba(168, 85, 247, 0.3);
  }

  &.editor {
    background: rgba(16, 185, 129, 0.15);
    color: var(--color-mint);
    border: 1px solid rgba(16, 185, 129, 0.3);
  }

  &.viewer {
    background: rgba(139, 148, 158, 0.15);
    color: var(--text-secondary);
    border: 1px solid rgba(139, 148, 158, 0.3);
  }
}

/* ========== 权限标签 ========== */
.permissions-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.permission-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  cursor: default;
  transition: all 0.2s ease;

  &.view {
    background: rgba(168, 85, 247, 0.1);
    color: var(--color-primary);
    border: 1px solid rgba(168, 85, 247, 0.2);
  }

  &.upload {
    background: rgba(16, 185, 129, 0.1);
    color: var(--color-mint);
    border: 1px solid rgba(16, 185, 129, 0.2);
  }

  &.edit {
    background: rgba(245, 158, 11, 0.1);
    color: var(--color-sunshine);
    border: 1px solid rgba(245, 158, 11, 0.2);
  }

  &.delete {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.2);
  }

  &.manage {
    background: rgba(236, 72, 153, 0.1);
    color: var(--color-secondary);
    border: 1px solid rgba(236, 72, 153, 0.2);
  }

  &:hover {
    transform: translateY(-1px);
  }
}

/* ========== 时间文本 ========== */
.time-text {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-tertiary);
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--text-secondary);
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;

  svg {
    width: 14px;
    height: 14px;
  }

  &:hover {
    color: var(--color-primary);
    background: rgba(168, 85, 247, 0.1);
    border-color: rgba(168, 85, 247, 0.3);
  }

  &.danger:hover {
    color: #ef4444;
    background: rgba(239, 68, 68, 0.1);
    border-color: rgba(239, 68, 68, 0.3);
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .page-content {
    padding: 0 16px 60px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
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

  :deep(.ant-table) {
    font-size: 14px;
  }
}
</style>
