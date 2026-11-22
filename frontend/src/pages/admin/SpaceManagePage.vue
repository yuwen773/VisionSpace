<template>
  <div id="spaceManagePage">
    <!-- 页面头部 -->
    <AdminPageHeader title="空间管理" subtitle="管理系统所有空间、配额和访问权限">
      <template #icon>
        <Cloud :size="19" :stroke-width="1.6" />
      </template>
      <template #extra>
        <div class="header-actions">
          <button @click="openCreateDrawer" class="toolbar-btn primary">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            创建空间
          </button>
        </div>
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
          v-model:value="searchParams.spaceName"
          aria-label="按空间名称搜索"
          placeholder="搜索空间名称..."
          class="search-input"
          @pressEnter="doSearch"
          allow-clear
        />
      </div>
      <div class="filter-selects">
        <a-select
          v-model:value="searchParams.spaceType"
          aria-label="按空间类型筛选"
          placeholder="空间类型"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="0">私人空间</a-select-option>
          <a-select-option :value="1">团队空间</a-select-option>
        </a-select>
        <a-select
          v-model:value="searchParams.spaceLevel"
          aria-label="按空间级别筛选"
          placeholder="空间级别"
          class="filter-select"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="0">普通版</a-select-option>
          <a-select-option :value="1">专业版</a-select-option>
          <a-select-option :value="2">旗舰版</a-select-option>
        </a-select>
        <a-select
          v-model:value="searchParams.status"
          aria-label="按空间状态筛选"
          placeholder="状态"
          class="filter-select status-filter"
          allow-clear
          @change="doSearch"
        >
          <a-select-option :value="1">已启用</a-select-option>
          <a-select-option :value="0">已禁用</a-select-option>
        </a-select>
        <a-select
          v-model:value="searchParams.userKeyword"
          aria-label="按所有者姓名或账号筛选"
          placeholder="所有者姓名 / 账号"
          class="filter-select owner-filter"
          show-search
          allow-clear
          :filter-option="false"
          :not-found-content="ownerSearching ? '搜索中…' : '输入姓名或账号'"
          @search="searchOwners"
          @change="doSearch"
        >
          <a-select-option v-for="owner in ownerOptions" :key="owner.id" :value="owner.userAccount">
            {{ owner.userName }} · {{ owner.userAccount }}
          </a-select-option>
        </a-select>
      </div>
      <div class="filter-buttons">
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

    <!-- 批量操作工具栏 -->
    <div class="batch-toolbar" v-if="selectedRowKeys.length > 0">
      <div class="batch-info">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 11 12 14 22 4"></polyline>
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
        </svg>
        <span
          >已选择 <strong>{{ selectedRowKeys.length }}</strong> 个空间</span
        >
      </div>
      <div class="batch-actions">
        <button
          class="batch-btn"
          :disabled="selectedRowKeys.length !== 1"
          :title="selectedRowKeys.length === 1 ? '编辑所选空间' : '请只选择一个空间'"
          @click="batchEdit"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
          </svg>
          编辑所选
        </button>
        <button class="batch-btn" @click="batchDisable">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
          </svg>
          批量禁用
        </button>
        <button class="batch-btn" @click="batchEnable">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="16 12 12 8 8 12"></polyline>
            <line x1="12" y1="16" x2="12" y2="8"></line>
          </svg>
          批量启用
        </button>
        <a-popconfirm
          :title="`确定删除选中的 ${selectedRowKeys.length} 个空间吗？删除后 5 秒内可撤销。`"
          ok-text="删除"
          cancel-text="取消"
          @confirm="batchDelete"
          placement="topRight"
        >
          <button class="batch-btn danger">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"></polyline>
              <path
                d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
              ></path>
            </svg>
            批量删除
          </button>
        </a-popconfirm>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="table-card">
      <div v-if="loadError" class="load-error" role="alert">
        <div class="load-error-text">
          <strong>空间列表没能加载</strong>
          <span>网络或服务暂时不可用，下方不是最新数据。</span>
        </div>
        <button class="toolbar-btn" @click="fetchData">重试</button>
      </div>
      <a-table
        :columns="columns"
        :data-source="dataList"
        :pagination="pagination"
        :loading="loading"
        :rowKey="(record: AdminSpaceVO) => record.id"
        :row-selection="rowSelection"
        @change="doTableChange"
        :scroll="tableScroll"
        class="data-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'spaceName'">
            <div class="space-name-cell">
              <span class="space-name">{{ record.spaceName }}</span>
              <a-tooltip :title="record.id" placement="topLeft">
                <span class="space-id">{{ record.id }}</span>
              </a-tooltip>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceType'">
            <div class="center-cell">
              <span class="type-tag" :class="record.spaceType === 0 ? 'private' : 'team'">
                <svg
                  v-if="record.spaceType === 0"
                  class="tag-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <svg
                  v-else
                  class="tag-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
                {{ record.spaceType === 0 ? '私人' : '团队' }}
              </span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'spaceLevel'">
            <div class="center-cell">
              <span class="level-tag" :class="getLevelClass(record.spaceLevel)">
                {{ SPACE_LEVEL_MAP[record.spaceLevel] }}
              </span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'capacity'">
            <div class="capacity-cell">
              <div class="capacity-main">
                <div class="capacity-bar-track">
                  <div
                    class="capacity-bar-fill"
                    :class="getCapacityClass(record)"
                    :style="{ width: getCapacityPercent(record) + '%' }"
                  ></div>
                </div>
                <span class="capacity-percent" :class="getCapacityClass(record)">
                  {{ getCapacityPercent(record) }}%
                </span>
              </div>
              <div class="capacity-stats">
                <span class="stat-item">
                  <svg
                    class="stat-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="7 10 12 15 17 10" />
                    <line x1="12" y1="15" x2="12" y2="3" />
                  </svg>
                  {{ formatSize(record.totalSize) }} / {{ formatSize(record.maxSize) }}
                </span>
                <span class="stat-divider"></span>
                <span class="stat-item">
                  <svg
                    class="stat-icon"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    stroke-width="2"
                  >
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" />
                    <polyline points="21 15 16 10 5 21" />
                  </svg>
                  {{ record.totalCount }} / {{ record.maxCount }} 张
                </span>
              </div>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'userId'">
            <div class="user-cell">
              <button
                v-if="record.user"
                class="owner-link"
                :title="`在用户管理中查看 ${record.user.userName ?? record.userId}`"
                @click="goToOwner(record.userId)"
              >
                <span class="owner-name">{{ record.user.userName || '未命名用户' }}</span>
                <span class="owner-id">{{ shortUserId(record.userId) }}</span>
              </button>
              <span v-else class="owner-unknown"
                >{{ shortUserId(record.userId) }} · 用户已不存在</span
              >
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <div class="center-cell">
              <span class="status-tag" :class="record.status === 1 ? 'enabled' : 'disabled'">
                <span class="status-dot"></span>
                {{ record.status === 1 ? '已启用' : '已禁用' }}
              </span>
            </div>
          </template>
          <template v-else-if="column.dataIndex === 'createTime'">
            <span class="time-text">{{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm') }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-dropdown trigger="click" placement="bottomRight">
              <button
                class="action-btn labeled"
                :aria-label="`打开「${record.spaceName}」的操作菜单`"
              >
                操作
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </button>
              <template #overlay>
                <a-menu @click="(info: any) => onRowAction(String(info.key), record)">
                  <a-menu-item key="analyze">分析空间</a-menu-item>
                  <a-menu-item key="edit">编辑空间</a-menu-item>
                  <a-menu-item key="toggle">
                    {{ record.status === 1 ? '禁用空间' : '启用空间' }}
                  </a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="delete" danger>删除空间</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 创建 / 编辑抽屉：管理端不再把操作员丢进用户侧的表单 -->
    <a-drawer
      v-model:open="drawerVisible"
      :title="drawerMode === 'create' ? '创建空间' : '编辑空间'"
      width="480"
      class="space-drawer"
      :destroy-on-close="true"
    >
      <a-form layout="vertical" :model="drawerForm" @finish="submitDrawer">
        <a-form-item label="空间名称" required>
          <a-input
            v-model:value="drawerForm.spaceName"
            :maxlength="30"
            show-count
            placeholder="例如：产品设计资料"
          />
        </a-form-item>

        <a-form-item v-if="drawerMode === 'create'" label="所有者" required>
          <a-select
            v-model:value="drawerForm.ownerAccount"
            show-search
            :filter-option="false"
            placeholder="输入姓名或账号搜索用户"
            :not-found-content="ownerSearching ? '搜索中…' : '输入姓名或账号'"
            @search="searchOwners"
            @change="onOwnerChange"
          >
            <a-select-option
              v-for="owner in ownerOptions"
              :key="owner.id"
              :value="owner.userAccount"
            >
              {{ owner.userName }} · {{ owner.userAccount }}
            </a-select-option>
          </a-select>
          <p class="form-hint">空间记在该用户名下，操作记录里保留你的管理员身份。</p>
        </a-form-item>

        <a-form-item v-if="drawerMode === 'create'" label="空间类型">
          <a-radio-group v-model:value="drawerForm.spaceType">
            <a-radio :value="0">私人空间</a-radio>
            <a-radio :value="1">团队空间</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="空间级别">
          <a-select v-model:value="drawerForm.spaceLevel">
            <a-select-option :value="0">普通版 · 100 张 / 100 MB</a-select-option>
            <a-select-option :value="1">专业版 · 1000 张 / 1 GB</a-select-option>
            <a-select-option :value="2">旗舰版 · 10000 张 / 10 GB</a-select-option>
          </a-select>
        </a-form-item>

        <div v-if="drawerMode === 'edit'" class="form-grid">
          <a-form-item label="最大图片数">
            <a-input-number v-model:value="drawerForm.maxCount" :min="1" :precision="0" />
          </a-form-item>
          <a-form-item label="最大容量（MB）">
            <a-input-number v-model:value="drawerForm.maxSizeMB" :min="1" :precision="0" />
          </a-form-item>
        </div>

        <p v-if="drawerMode === 'edit'" class="form-hint">
          容量会按级别给出默认值，这里可以覆盖；已用容量不受影响。
        </p>
      </a-form>

      <template #footer>
        <div class="drawer-footer">
          <a-button @click="drawerVisible = false">取消</a-button>
          <a-button type="primary" :loading="drawerSubmitting" @click="submitDrawer">
            {{ drawerMode === 'create' ? '创建空间' : '保存修改' }}
          </a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup>
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { Cloud } from 'lucide-vue-next'
import { ref, reactive, onMounted, computed } from 'vue'
import { deleteSpaceUsingPost } from '@/api/spaceController.ts'
import { message, Modal } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { SPACE_LEVEL_MAP, SPACE_LEVEL_ENUM } from '@/constants/space.ts'
import { formatSize } from '@/utils'
import { useAdminTableDensity } from '@/composables/useAdminTableDensity'
import { useUndoable } from '@/composables/useUndoable'
import {
  adminAddSpace,
  listAdminSpaces,
  searchAdminUsers,
  updateAdminSpace,
  type AdminSpaceOwnerVO,
  type AdminSpaceVO,
} from '@/api-custom/adminConsole'

const router = useRouter()
const undo = useUndoable()

// 雪花 ID 过长，列表里只保留尾号，完整值交给 tooltip
const shortUserId = (userId?: string | number) => {
  const id = String(userId ?? '')
  if (!id) return '—'
  return id.length > 12 ? `ID ${id.slice(-6)}` : id
}

const density = useAdminTableDensity()

// 列集合按视口档位裁剪：宽屏全列；中屏收掉类型、级别与所有者；窄屏只留名称、容量与操作。
// 手机上正文只剩两百多像素，固定操作列会把名称压在下面，因此改为不固定、整表横向滚动，
// 同时收掉勾选列（批量操作在那块屏幕上本来也无从下手）。
const columns = computed(() => {
  const name = { title: '空间名称', dataIndex: 'spaceName', width: 150, ellipsis: true }
  const type = { title: '类型', dataIndex: 'spaceType', width: 86, align: 'center' as const }
  const level = { title: '级别', dataIndex: 'spaceLevel', width: 86, align: 'center' as const }
  const capacity = { title: '容量使用', dataIndex: 'capacity', width: 296 }
  const owner = { title: '所有者', dataIndex: 'userId', width: 150 }
  const status = { title: '状态', dataIndex: 'status', width: 96, align: 'center' as const }
  const createTime = { title: '创建时间', dataIndex: 'createTime', width: 148, sorter: true }
  const action = { title: '操作', key: 'action', width: 96 }
  const pinnedAction = { ...action, fixed: 'right' as const }

  if (density.value === 'phone') return [name, capacity, action]
  if (density.value === 'narrow') return [name, capacity, status, pinnedAction]
  if (density.value === 'medium') return [name, capacity, owner, status, pinnedAction]
  return [name, type, level, capacity, owner, status, createTime, pinnedAction]
})

const tableScroll = computed(() => {
  if (density.value === 'wide') return { x: 1108 }
  if (density.value === 'medium') return { x: 788 }
  if (density.value === 'narrow') return { x: 638 }
  return { x: 542 }
})

interface SpaceFilters {
  current: number
  pageSize: number
  sortOrder: 'ascend' | 'descend'
  sortField: string
  spaceName?: string
  spaceType?: number
  spaceLevel?: number
  status?: number
  userKeyword?: string
}

const dataList = ref<AdminSpaceVO[]>([])
const total = ref<number>(0)
const loading = ref(false)
const loadError = ref(false)
const selectedRowKeys = ref<(string | number)[]>([])

const searchParams = reactive<SpaceFilters>({
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
  showTotal: (total: number) => `共 ${total} 个空间`,
}))

const rowSelection = computed(() => {
  if (density.value === 'phone') return undefined
  return {
    selectedRowKeys: selectedRowKeys.value,
    columnTitle: '选择',
    onChange: (keys: (string | number)[]) => {
      selectedRowKeys.value = keys
    },
  }
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listAdminSpaces({
      current: searchParams.current,
      pageSize: searchParams.pageSize,
      sortField: searchParams.sortField,
      sortOrder: searchParams.sortOrder,
      spaceName: searchParams.spaceName || undefined,
      spaceType: searchParams.spaceType,
      spaceLevel: searchParams.spaceLevel,
      status: searchParams.status,
      userKeyword: searchParams.userKeyword || undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = res.data.data.total ?? 0
      loadError.value = false
    } else {
      loadError.value = true
      message.error('获取数据失败：' + res.data.message)
    }
  } catch {
    // 保留上一次成功的数据，同时明确标出「不是最新」
    loadError.value = true
    message.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

const doTableChange = (
  pag: { current?: number; pageSize?: number },
  filters: Record<string, unknown>,
  sorter: { field?: string; order?: 'ascend' | 'descend' },
) => {
  searchParams.current = pag.current ?? 1
  searchParams.pageSize = pag.pageSize ?? 10
  if (sorter.field) {
    searchParams.sortField = sorter.field
    searchParams.sortOrder = sorter.order === 'ascend' ? 'ascend' : 'descend'
  }
  fetchData()
}

const doSearch = () => {
  searchParams.current = 1
  selectedRowKeys.value = []
  fetchData()
}

const resetSearch = () => {
  searchParams.spaceName = undefined
  searchParams.spaceType = undefined
  searchParams.spaceLevel = undefined
  searchParams.status = undefined
  searchParams.userKeyword = undefined
  searchParams.current = 1
  selectedRowKeys.value = []
  fetchData()
}

/* ---------- 所有者搜索：筛选器与创建抽屉共用同一份候选 ---------- */
const ownerOptions = ref<AdminSpaceOwnerVO[]>([])
const ownerSearching = ref(false)
let ownerSearchTimer: number | undefined

const searchOwners = (keyword: string) => {
  window.clearTimeout(ownerSearchTimer)
  const kw = keyword.trim()
  if (!kw) {
    ownerOptions.value = []
    return
  }
  ownerSearchTimer = window.setTimeout(async () => {
    ownerSearching.value = true
    try {
      const res = await searchAdminUsers(kw)
      if (res.data.code === 0 && res.data.data) {
        ownerOptions.value = res.data.data
      } else {
        ownerOptions.value = []
      }
    } catch {
      ownerOptions.value = []
      message.error('用户搜索失败')
    } finally {
      ownerSearching.value = false
    }
  }, 280)
}

const confirmDelete = (record: AdminSpaceVO) => {
  Modal.confirm({
    title: `删除空间「${record.spaceName ?? record.id}」？`,
    content: '空间与其成员关系会被移除。5 秒内可撤销。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => applyDelete(record),
  })
}

const applyDelete = (record: AdminSpaceVO) => {
  const index = dataList.value.findIndex((row) => row.id === record.id)
  if (index < 0) return
  dataList.value.splice(index, 1)
  total.value = Math.max(0, total.value - 1)
  selectedRowKeys.value = selectedRowKeys.value.filter((k) => k !== record.id)

  undo.schedule({
    key: `space-delete-${record.id}`,
    message: `「${record.spaceName ?? record.id}」已删除`,
    commit: async () => {
      try {
        const res = await deleteSpaceUsingPost({ id: record.id })
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

const toggleStatus = (record: AdminSpaceVO) => {
  const previous = record.status ?? 1
  const next = previous === 1 ? 0 : 1
  record.status = next

  undo.schedule({
    key: `space-status-${record.id}`,
    message: `「${record.spaceName ?? record.id}」已${next === 1 ? '启用' : '禁用'}`,
    commit: async () => {
      try {
        const res = await updateAdminSpace({ id: record.id, status: next })
        if (res.data.code !== 0) {
          record.status = previous
          message.error(res.data.message || '操作失败，已恢复原状态')
        }
      } catch {
        record.status = previous
        message.error('操作失败，已恢复原状态')
      }
    },
    undo: () => {
      record.status = previous
    },
  })
}

const batchEdit = () => {
  const target = dataList.value.find((row) => row.id === selectedRowKeys.value[0])
  if (selectedRowKeys.value.length === 1 && target) {
    openEditDrawer(target)
  } else {
    message.warning('请只选择一个空间进行编辑')
  }
}

const batchSetStatus = (status: number) => {
  const ids = [...selectedRowKeys.value] as number[]
  if (!ids.length) return
  const rows = dataList.value.filter((row) => ids.includes(row.id))
  const previous = new Map(rows.map((row) => [row.id, row.status ?? 1]))
  rows.forEach((row) => (row.status = status))
  selectedRowKeys.value = []

  undo.schedule({
    key: `space-batch-status-${ids.join('-')}`,
    message: `已${status === 1 ? '启用' : '禁用'} ${ids.length} 个空间`,
    commit: async () => {
      const results = await Promise.allSettled(ids.map((id) => updateAdminSpace({ id, status })))
      const failed = results.filter((r) => {
        if (r.status === 'rejected') return true
        return r.value?.data?.code !== 0
      })
      if (failed.length) {
        message.error(`${failed.length} 个空间更新失败`)
      }
      await fetchData()
    },
    undo: () => {
      rows.forEach((row) => (row.status = previous.get(row.id)))
    },
  })
}

const batchDisable = () => batchSetStatus(0)
const batchEnable = () => batchSetStatus(1)

const batchDelete = () => {
  const ids = [...selectedRowKeys.value] as number[]
  if (!ids.length) return
  const removed = dataList.value
    .map((row, index) => ({ row, index }))
    .filter(({ row }) => ids.includes(row.id))
  dataList.value = dataList.value.filter((row) => !ids.includes(row.id))
  total.value = Math.max(0, total.value - removed.length)
  selectedRowKeys.value = []

  undo.schedule({
    key: `space-batch-delete-${ids.join('-')}`,
    message: `已删除 ${removed.length} 个空间`,
    commit: async () => {
      const results = await Promise.allSettled(ids.map((id) => deleteSpaceUsingPost({ id })))
      const failed = results.filter((r) => {
        if (r.status === 'rejected') return true
        return r.value?.data?.code !== 0
      })
      if (failed.length) {
        message.error(`${failed.length} 个空间删除失败`)
      }
      await fetchData()
    },
    undo: () => {
      removed
        .slice()
        .sort((a, b) => a.index - b.index)
        .forEach(({ row, index }) => {
          dataList.value.splice(Math.min(index, dataList.value.length), 0, row)
        })
      total.value += removed.length
    },
  })
}

const getLevelClass = (level: number) => {
  const classMap: Record<number, string> = {
    [SPACE_LEVEL_ENUM.COMMON]: 'basic',
    [SPACE_LEVEL_ENUM.PROFESSIONAL]: 'pro',
    [SPACE_LEVEL_ENUM.FLAGSHIP]: 'flagship',
  }
  return classMap[level] || 'basic'
}

const getCapacityPercent = (record: AdminSpaceVO) => {
  if (!record.maxSize || record.maxSize === 0) return 0
  return Math.min(100, Math.round(((record.totalSize ?? 0) / record.maxSize) * 100))
}

const getCapacityClass = (record: AdminSpaceVO) => {
  const percent = getCapacityPercent(record)
  if (percent >= 90) return 'danger'
  if (percent >= 70) return 'warning'
  return 'normal'
}

const goToAnalyze = (id: number) => {
  router.push(`/space_analyze?spaceId=${id}`)
}

const goToOwner = (userId?: number) => {
  if (!userId) return
  router.push({ path: '/admin/user_manage', query: { userId: String(userId) } })
}

/* ---------- 管理端创建 / 编辑抽屉 ---------- */
type DrawerMode = 'create' | 'edit'

const drawerVisible = ref(false)
const drawerMode = ref<DrawerMode>('create')
const drawerSubmitting = ref(false)
const editingId = ref<number | null>(null)
const drawerForm = reactive({
  spaceName: '',
  ownerAccount: '',
  ownerId: null as number | null,
  spaceType: 0,
  spaceLevel: 0,
  maxCount: 100,
  maxSizeMB: 100,
})

const openCreateDrawer = () => {
  drawerMode.value = 'create'
  editingId.value = null
  drawerForm.spaceName = ''
  drawerForm.ownerAccount = ''
  drawerForm.ownerId = null
  drawerForm.spaceType = 0
  drawerForm.spaceLevel = SPACE_LEVEL_ENUM.COMMON
  drawerForm.maxCount = 100
  drawerForm.maxSizeMB = 100
  ownerOptions.value = []
  drawerVisible.value = true
}

const openEditDrawer = (record: AdminSpaceVO) => {
  drawerMode.value = 'edit'
  editingId.value = record.id
  drawerForm.spaceName = record.spaceName ?? ''
  drawerForm.spaceLevel = record.spaceLevel ?? SPACE_LEVEL_ENUM.COMMON
  drawerForm.maxCount = record.maxCount ?? 100
  drawerForm.maxSizeMB = Math.max(1, Math.round((record.maxSize ?? 0) / 1024 / 1024))
  drawerVisible.value = true
}

const onOwnerChange = (account: string) => {
  const owner = ownerOptions.value.find((item) => item.userAccount === account)
  drawerForm.ownerId = owner?.id ?? null
}

const submitDrawer = async () => {
  if (drawerSubmitting.value) return
  const spaceName = drawerForm.spaceName.trim()
  if (!spaceName) {
    message.warning('请填写空间名称')
    return
  }

  drawerSubmitting.value = true
  try {
    if (drawerMode.value === 'create') {
      if (!drawerForm.ownerId) {
        message.warning('请先搜索并选择所有者')
        return
      }
      const res = await adminAddSpace({
        spaceName,
        userId: drawerForm.ownerId,
        spaceType: drawerForm.spaceType,
        spaceLevel: drawerForm.spaceLevel,
      })
      if (res.data.code === 0) {
        message.success('空间已创建')
        drawerVisible.value = false
        await fetchData()
      } else {
        message.error(res.data.message || '创建失败')
      }
    } else if (editingId.value) {
      const res = await updateAdminSpace({
        id: editingId.value,
        spaceName,
        spaceLevel: drawerForm.spaceLevel,
        maxCount: drawerForm.maxCount,
        maxSize: drawerForm.maxSizeMB * 1024 * 1024,
      })
      if (res.data.code === 0) {
        message.success('空间已更新')
        drawerVisible.value = false
        await fetchData()
      } else {
        message.error(res.data.message || '保存失败')
      }
    }
  } catch {
    message.error(drawerMode.value === 'create' ? '创建失败' : '保存失败')
  } finally {
    drawerSubmitting.value = false
  }
}

const onRowAction = (key: string, record: AdminSpaceVO) => {
  if (key === 'analyze') goToAnalyze(record.id)
  else if (key === 'edit') openEditDrawer(record)
  else if (key === 'toggle') toggleStatus(record)
  else if (key === 'delete') confirmDelete(record)
}
</script>

<style scoped lang="less">
#spaceManagePage {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0;
}

.header-actions {
  flex-shrink: 0;
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
  flex: 0 0 200px;

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

.filter-selects {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.filter-select {
  width: 130px;

  &.status-filter {
    width: 104px;
  }

  &.owner-filter {
    width: 190px;
  }

  :deep(.ant-select-selector) {
    background: transparent !important;
    border-color: var(--admin-border-default) !important;
    color: var(--admin-text-primary) !important;
  }
}

.filter-buttons {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
  margin-left: auto;
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

/* ========== 批量操作工具栏 ========== */
.batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--admin-space-4);
  margin-bottom: var(--admin-space-4);
  padding: var(--admin-space-3) var(--admin-space-4);
  background: var(--admin-primary-bg);
  border: 1px solid color-mix(in srgb, var(--admin-primary) 30%, transparent);
  border-radius: var(--admin-radius-sm);
  animation: slideUp 0.3s var(--admin-ease);
}

.batch-info {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);
  color: var(--admin-text-primary);
  font-size: var(--admin-text-sm);

  svg {
    width: 16px;
    height: 16px;
    color: var(--admin-primary);
  }

  strong {
    color: var(--admin-primary);
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: var(--admin-space-2);
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--admin-space-2);
  height: 32px;
  padding: 0 var(--admin-space-3);
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

  &.success {
    color: var(--admin-success);
    border-color: color-mix(in srgb, var(--admin-success) 45%, transparent);
    background: var(--admin-success-bg);

    &:hover {
      background: color-mix(in srgb, var(--admin-success) 18%, transparent);
      border-color: var(--admin-success);
    }
  }

  &.danger {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: color-mix(in srgb, var(--admin-danger) 45%, transparent);

    &:hover {
      background: color-mix(in srgb, var(--admin-danger) 18%, transparent);
      border-color: var(--admin-danger);
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
    letter-spacing: 0.08em;
    text-transform: uppercase;
    padding: var(--admin-space-3) var(--admin-space-4);
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

  :deep(.ant-pagination) {
    padding: var(--admin-space-4);
    background: transparent;
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
        background: transparent;
        border-color: var(--admin-border-default);
        color: var(--admin-text-secondary);

        &:hover {
          background: var(--admin-primary-bg);
          border-color: var(--admin-primary);
        }
      }
    }

    .ant-pagination-options {
      .ant-select {
        .ant-select-selector {
          background: transparent;
          border-color: var(--admin-border-default);
          color: var(--admin-text-primary);
        }
      }
    }
  }
}

/* ========== 单元格样式 ========== */
.center-cell {
  display: flex;
  justify-content: center;
}

.space-name-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;

  .space-name {
    font-weight: 600;
    font-size: var(--admin-text-base);
    color: var(--admin-text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .space-id {
    font-size: var(--admin-text-xs);
    /* 空间 ID 是对账时的引用键，不能淡到纸面里看不出来 */
    color: var(--admin-text-tertiary);
    font-family: var(--admin-font-mono);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: default;
  }
}

.type-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 2px 9px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-sm);
  white-space: nowrap;

  .tag-icon {
    width: 12px;
    height: 12px;
  }

  &.private {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border: 1px solid color-mix(in srgb, var(--admin-primary) 26%, transparent);
  }

  &.team {
    color: var(--admin-text-primary);
    background: var(--admin-bg-tertiary);
    border: 1px solid var(--admin-border-default);
  }
}

.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 9px;
  font-size: var(--admin-text-xs);
  font-weight: 600;
  border-radius: var(--admin-radius-sm);
  white-space: nowrap;

  &.basic {
    color: var(--admin-text-secondary);
    background: transparent;
    border: 1px solid var(--admin-border-default);
  }

  &.pro {
    color: var(--admin-warning);
    background: var(--admin-warning-bg);
    border: 1px solid color-mix(in srgb, var(--admin-warning) 30%, transparent);
  }

  &.flagship {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border: 1px solid color-mix(in srgb, var(--admin-danger) 30%, transparent);
  }
}

.capacity-cell {
  display: flex;
  flex-direction: column;
  gap: var(--admin-space-2);
}

.capacity-main {
  display: flex;
  align-items: center;
  gap: var(--admin-space-3);
}

.capacity-bar-track {
  flex: 1;
  height: 4px;
  background: var(--admin-bg-tertiary);
  border-radius: 0;
  overflow: hidden;
}

.capacity-bar-fill {
  height: 100%;
  border-radius: 0;
  transition: width var(--admin-transition-slow);
  min-width: 0;

  &.normal {
    background: var(--admin-success);
  }

  &.warning {
    background: var(--admin-warning);
  }

  &.danger {
    background: var(--admin-danger);
  }
}

.capacity-percent {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  font-weight: 500;
  color: var(--admin-text-secondary);
  min-width: 42px;
  text-align: right;
  font-variant-numeric: tabular-nums;

  &.normal {
    color: var(--admin-success);
  }
  &.warning {
    color: var(--admin-warning);
  }
  &.danger {
    color: var(--admin-danger);
  }
}

.capacity-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--admin-font-mono);
  font-size: 10px;
  color: var(--admin-text-secondary);
  font-variant-numeric: tabular-nums;
}

.stat-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  white-space: nowrap;
}

.stat-icon {
  width: 12px;
  height: 12px;
  opacity: 0.5;
  flex-shrink: 0;
}

.stat-divider {
  width: 1px;
  height: 10px;
  background: var(--admin-border-default);
  flex-shrink: 0;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  .owner-link {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    padding: 0;
    background: none;
    border: none;
    text-align: left;
    cursor: pointer;

    &:hover .owner-name {
      color: var(--admin-primary);
      text-decoration: underline;
      text-underline-offset: 2px;
    }
  }

  .owner-name {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--admin-text-sm);
    color: var(--admin-text-primary);
  }

  .owner-id {
    font-family: var(--admin-font-mono);
    font-size: 10px;
    letter-spacing: 0.04em;
    color: var(--admin-text-tertiary);
  }

  .owner-unknown {
    font-size: var(--admin-text-xs);
    color: var(--admin-text-tertiary);
  }
}

/* 状态列：与反馈管理的状态标签同一套圆点 + 文字 */
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 999px;
    background: currentColor;
  }

  &.enabled {
    color: var(--admin-success);
  }

  &.disabled {
    color: var(--admin-text-tertiary);
  }
}

.time-text {
  font-family: var(--admin-font-mono);
  font-size: var(--admin-text-xs);
  color: var(--admin-text-secondary);
  white-space: nowrap;
}

/* ========== 操作按钮 ========== */
.action-buttons {
  display: flex;
  justify-content: flex-start;
  gap: var(--admin-space-1);
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--admin-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--admin-radius-md);
  cursor: pointer;
  transition: all var(--admin-transition-fast);

  svg {
    width: 15px;
    height: 15px;
  }

  &:hover {
    color: var(--admin-primary);
    background: var(--admin-primary-bg);
    border-color: color-mix(in srgb, var(--admin-primary) 22%, transparent);
  }

  &.success:hover {
    color: var(--admin-success);
    background: var(--admin-success-bg);
    border-color: color-mix(in srgb, var(--admin-success) 26%, transparent);
  }

  &.warning:hover {
    color: var(--admin-warning);
    background: var(--admin-warning-bg);
    border-color: color-mix(in srgb, var(--admin-warning) 26%, transparent);
  }

  &.danger:hover {
    color: var(--admin-danger);
    background: var(--admin-danger-bg);
    border-color: color-mix(in srgb, var(--admin-danger) 26%, transparent);
  }

  /* 四个图标动作收进带文字的菜单触发器，操作不再靠猜图标 */
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

/* ========== 创建 / 编辑抽屉 ========== */
.space-drawer {
  .form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--admin-space-4);
  }

  .form-hint {
    margin: -6px 0 var(--admin-space-4);
    font-size: var(--admin-text-xs);
    color: var(--admin-text-tertiary);
  }

  .drawer-footer {
    display: flex;
    justify-content: flex-end;
    gap: var(--admin-space-3);
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
      flex: 1 1 100%;
    }

    .filter-selects {
      flex-wrap: wrap;
    }

    .filter-buttons {
      width: 100%;
      margin-left: 0;
      justify-content: flex-end;
    }
  }
}

@media (max-width: 768px) {
  #spaceManagePage {
    padding: 0;
  }

  .batch-toolbar {
    flex-direction: column;
    align-items: flex-start;

    .batch-actions {
      width: 100%;
      justify-content: flex-end;
    }
  }
}

/* 手机上筛选区按整行铺开，按钮等宽铺满，避免右侧留出一大片空白 */
@media (max-width: 640px) {
  .table-toolbar {
    gap: var(--admin-space-3);
  }

  .filter-selects {
    width: 100%;
  }

  .filter-select {
    flex: 1 1 calc(50% - var(--admin-space-3) / 2);
  }

  .owner-filter {
    flex: 1 1 100%;
    width: auto;
  }

  .filter-buttons {
    width: 100%;

    .toolbar-btn {
      flex: 1;
      justify-content: center;
    }
  }
}
</style>
