<template>
  <div id="userManagePage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">
          <span>👥</span>
          <span>用户管理</span>
        </h1>
        <p class="page-subtitle">管理系统用户和权限</p>
      </div>
    </div>

    <!-- 搜索表单 -->
    <div class="filter-section pop-card">
      <a-form layout="inline" :model="searchParams" @finish="doSearch" class="filter-form">
        <a-form-item label="🎯 用户账号">
          <a-input
            v-model:value="searchParams.userAccount"
            placeholder="请输入用户账号"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item label="👤 用户名">
          <a-input
            v-model:value="searchParams.userName"
            placeholder="请输入用户名"
            allow-clear
            class="form-input"
          />
        </a-form-item>
        <a-form-item>
          <button html-type="submit" class="pop-btn primary search-btn">
            <span>🔍</span>
            <span>搜索</span>
          </button>
        </a-form-item>
      </a-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper pop-card">
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        @change="doTableChange"
        :loading="loading"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'userAvatar'">
            <a-avatar :src="record.userAvatar" :size="40" class="user-avatar" />
          </template>
          <template v-else-if="column.dataIndex === 'userRole'">
            <span class="role-tag pop-tag" :class="record.userRole === 'admin' ? 'grape' : 'coral'">
              {{ record.userRole === 'admin' ? '👑 管理员' : '👤 普通用户' }}
            </span>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <div class="action-buttons">
              <a-popconfirm
                title="确定要删除此用户吗？"
                ok-text="🗑️ 删除"
                cancel-text="❌ 取消"
                @confirm="doDelete(record.id)"
              >
                <button class="action-btn pop-btn" style="background: var(--color-pink); border-color: var(--color-pink-dark);">
                  <span>🗑️</span>
                </button>
              </a-popconfirm>
            </div>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { deleteUserUsingPost, listUserVoByPageUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '账号', dataIndex: 'userAccount' },
  { title: '用户名', dataIndex: 'userName' },
  { title: '头像', dataIndex: 'userAvatar', width: 100 },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '角色', dataIndex: 'userRole', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 80 },
]

const dataList = ref<API.UserVO[]>([])
const total = ref<number>(0)
const loading = ref(false)

const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  sortOrder: 'ascend',
  sortField: 'createTime',
})

const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: Number(total.value),
  showSizeChanger: true,
  position: ['bottomCenter'] as const,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条`,
}))

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUserVoByPageUsingPost(searchParams)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
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

const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const doDelete = async (id: string) => {
  if (!id) return
  try {
    const res = await deleteUserUsingPost({ id })
    if (res.data.code === 0) {
      message.success('🗑️ 用户已删除')
      await fetchData()
    } else {
      message.error('😅 删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('😅 删除失败')
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

/* ========== 搜索表单区域 ========== */
.filter-section {
  margin-bottom: var(--space-6);
  padding: var(--space-5);
  animation: slideUp 0.6s ease-out 0.1s backwards;

  &::before {
    display: none;
  }
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  justify-content: center;

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

.search-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

/* ========== 表格容器 ========== */
.table-wrapper {
  padding: var(--space-6);
  animation: slideUp 0.6s ease-out 0.2s backwards;

  &::before {
    display: none;
  }
}

/* ========== 表格样式 ========== */
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

  :deep(.ant-table-placeholder) {
    .ant-empty-description {
      color: var(--text-tertiary);
      font-weight: 600;
    }
  }
}

/* ========== 头像 ========== */
.user-avatar {
  border: 3px solid var(--border-bold);
}

/* ========== 角色标签 ========== */
.role-tag {
  padding: 4px 12px;
  font-size: var(--text-xs);
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

.time-text {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--text-tertiary);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  #userManagePage {
    padding: var(--space-4);
  }

  .page-title {
    font-size: var(--text-2xl);
  }

  .filter-form {
    :deep(.ant-form-item) {
      display: block;
      margin-right: 0;
      margin-bottom: var(--space-3);
    }
  }

  .form-input {
    min-width: 100%;
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
