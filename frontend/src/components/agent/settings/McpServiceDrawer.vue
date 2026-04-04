<template>
  <a-drawer
    v-model:open="drawerVisible"
    title="MCP 服务管理"
    placement="right"
    width="520"
    :closable="true"
    class="mcp-service-drawer"
  >
    <template #extra>
      <a-button type="primary" @click="openCreateDrawer">
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
        </template>
        添加服务
      </a-button>
    </template>

    <!-- 加载状态 -->
    <a-spin :spinning="loading">
      <!-- 空状态 -->
      <div v-if="!loading && mcpServerList.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="48" height="48">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
            <line x1="8" y1="21" x2="16" y2="21"></line>
            <line x1="12" y1="17" x2="12" y2="21"></line>
          </svg>
        </div>
        <p class="empty-text">暂无 MCP 服务</p>
        <p class="empty-hint">点击上方"添加服务"创建一个新的 MCP 服务</p>
      </div>

      <!-- 服务列表 -->
      <a-list v-else :data-source="mcpServerList" item-layout="horizontal" class="mcp-server-list">
        <template #renderItem="{ item }">
          <a-list-item class="mcp-server-item">
            <template #actions>
              <a-button type="link" size="small" @click="openEditDrawer(item)">编辑</a-button>
              <a-popconfirm
                title="确定要删除此 MCP 服务吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(item.id)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
            <a-list-item-meta>
              <template #title>
                <div class="server-title">
                  <span class="server-name">{{ item.name }}</span>
                  <a-tag :color="item.enabled ? 'green' : 'default'" class="status-tag">
                    {{ item.enabled ? '已启用' : '已禁用' }}
                  </a-tag>
                </div>
              </template>
              <template #description>
                <div class="server-description">
                  <span v-if="item.description" class="description-text">{{ item.description }}</span>
                  <span v-else class="description-text no-desc">暂无描述</span>
                  <span class="server-type">{{ getServerTypeLabel(item.type) }}</span>
                </div>
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>

    <!-- 创建/编辑抽屉 -->
    <a-drawer
      v-model:open="formDrawerVisible"
      :title="isEditing ? '编辑 MCP 服务' : '创建 MCP 服务'"
      placement="right"
      width="480"
      :closable="true"
      class="mcp-form-drawer"
    >
      <template #extra>
        <a-space>
          <a-button @click="toggleConfigMode">
            {{ configMode === 'form' ? '切换到 JSON 配置' : '切换到表单配置' }}
          </a-button>
        </a-space>
      </template>

      <!-- JSON 模式 -->
      <div v-if="configMode === 'json'" class="json-config-area">
        <p class="json-hint">输入 JSON 格式的 MCP 服务配置：</p>
        <a-textarea
          v-model:value="jsonConfig"
          placeholder='{"name": "my-mcp-server", "type": "stdio", "command": "npx", "args": ["-y", "@example/mcp-server"]}'
          :rows="12"
          class="json-textarea"
        />
        <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
      </div>

      <!-- 表单模式 -->
      <a-form
        v-else
        ref="formRef"
        name="mcp-server-form"
        layout="vertical"
        :model="formData"
        :rules="formRules"
        class="mcp-form"
      >
        <a-form-item label="服务名称" name="name">
          <a-input v-model:value="formData.name" placeholder="请输入服务名称" />
        </a-form-item>

        <a-form-item label="服务描述" name="description">
          <a-textarea v-model:value="formData.description" placeholder="请输入服务描述（可选）" :rows="3" />
        </a-form-item>

        <a-form-item label="服务类型" name="type">
          <a-select v-model:value="formData.type" placeholder="请选择服务类型">
            <a-select-option value="stdio">STDIO</a-select-option>
            <a-select-option value="http">HTTP</a-select-option>
            <a-select-option value="sse">SSE</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="是否启用" name="enabled">
          <a-switch v-model:checked="formData.enabled" />
        </a-form-item>

        <template v-if="formData.type === 'stdio'">
          <a-form-item label="命令" name="command">
            <a-input v-model:value="formData.command" placeholder="例如：npx, node, python" />
          </a-form-item>

          <a-form-item label="参数" name="args">
            <a-textarea
              v-model:value="formData.argsText"
              placeholder="输入参数，每行一个（例如：-y&#10;@example/mcp-server）"
              :rows="4"
            />
          </a-form-item>

          <a-form-item label="环境变量" name="env">
            <a-textarea
              v-model:value="formData.envText"
              placeholder="输入环境变量，格式：KEY=VALUE，每行一个"
              :rows="4"
            />
          </a-form-item>
        </template>

        <template v-if="formData.type === 'http' || formData.type === 'sse'">
          <a-form-item label="URL" name="url">
            <a-input v-model:value="formData.url" placeholder="例如：https://api.example.com/mcp" />
          </a-form-item>

          <a-form-item label="请求头" name="headers">
            <a-textarea
              v-model:value="formData.headersText"
              placeholder="输入请求头，格式：KEY: VALUE，每行一个"
              :rows="4"
            />
          </a-form-item>
        </template>

        <a-form-item label="超时时间（毫秒）" name="timeout">
          <a-input-number
            v-model:value="formData.timeout"
            :min="1000"
            :max="300000"
            :step="1000"
            placeholder="默认 60000"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>

      <template #footer>
        <div class="drawer-footer">
          <a-button @click="formDrawerVisible = false">取消</a-button>
          <a-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEditing ? '保存' : '创建' }}
          </a-button>
        </div>
      </template>
    </a-drawer>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'

// MCP Server 类型定义
export interface McpServerVO {
  id?: number
  name: string
  description?: string
  type: 'stdio' | 'http' | 'sse'
  enabled?: boolean
  command?: string
  args?: string[]
  env?: Record<string, string>
  url?: string
  headers?: Record<string, string>
  timeout?: number
  status?: number
  createTime?: string
  updateTime?: string
}

interface McpServerFormData {
  id?: number
  name: string
  description: string
  type: 'stdio' | 'http' | 'sse'
  enabled: boolean
  command: string
  argsText: string
  envText: string
  url: string
  headersText: string
  timeout: number
}

// API 函数 - 这些将在 Task 20 中实现
// 暂时使用 any 类型，后续会替换为正确的 API 函数
const listMcpServersUsingGet = async () => {
  // TODO: Task 20 - 替换为实际 API 调用
  // return await listMcpServersUsingGet(params)
  return { data: { code: 0, data: [] } }
}

const addMcpServerUsingPost = async (data: any) => {
  // TODO: Task 20 - 替换为实际 API 调用
  // return await addMcpServerUsingPost(data)
  return { data: { code: 0 } }
}

const updateMcpServerUsingPost = async (data: any) => {
  // TODO: Task 20 - 替换为实际 API 调用
  // return await updateMcpServerUsingPost(data)
  return { data: { code: 0 } }
}

const deleteMcpServerUsingPost = async (id: number) => {
  // TODO: Task 20 - 替换为实际 API 调用
  // return await deleteMcpServerUsingPost({ id })
  return { data: { code: 0 } }
}

const props = withDefaults(
  defineProps<{
    open: boolean
  }>(),
  {
    open: false,
  }
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const drawerVisible = computed({
  get: () => props.open,
  set: (value) => emit('update:open', value),
})

// 状态
const loading = ref(false)
const submitting = ref(false)
const mcpServerList = ref<McpServerVO[]>([])
const formDrawerVisible = ref(false)
const isEditing = ref(false)
const configMode = ref<'form' | 'json'>('form')
const jsonConfig = ref('')
const jsonError = ref('')
const formRef = ref<FormInstance>()

// 表单数据
const defaultFormData: McpServerFormData = {
  id: undefined,
  name: '',
  description: '',
  type: 'stdio',
  enabled: true,
  command: '',
  argsText: '',
  envText: '',
  url: '',
  headersText: '',
  timeout: 60000,
}

const formData = reactive<McpServerFormData>({ ...defaultFormData })

// 表单验证规则
const formRules = {
  name: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择服务类型', trigger: 'change' }],
  command: [{ required: true, message: '请输入命令', trigger: 'blur' }],
  url: [{ required: true, message: '请输入 URL', trigger: 'blur' }],
}

// 加载 MCP 服务器列表
const loadMcpServerList = async () => {
  loading.value = true
  try {
    const res = await listMcpServersUsingGet()
    if (res.data.code === 0) {
      mcpServerList.value = res.data.data || []
    } else {
      message.error('加载 MCP 服务列表失败: ' + res.data.message)
    }
  } catch (error) {
    message.error('加载 MCP 服务列表失败')
    console.error('Load MCP servers error:', error)
  } finally {
    loading.value = false
  }
}

// 打开创建抽屉
const openCreateDrawer = () => {
  isEditing.value = false
  resetForm()
  formDrawerVisible.value = true
}

// 打开编辑抽屉
const openEditDrawer = (server: McpServerVO) => {
  isEditing.value = true
  resetForm()
  // 填充表单数据
  formData.id = server.id
  formData.name = server.name
  formData.description = server.description || ''
  formData.type = server.type || 'stdio'
  formData.enabled = server.enabled ?? true
  formData.command = server.command || ''
  formData.argsText = (server.args || []).join('\n')
  formData.envText = Object.entries(server.env || {})
    .map(([key, value]) => `${key}=${value}`)
    .join('\n')
  formData.url = server.url || ''
  formData.headersText = Object.entries(server.headers || {})
    .map(([key, value]) => `${key}: ${value}`)
    .join('\n')
  formData.timeout = server.timeout || 60000
  formDrawerVisible.value = true
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, defaultFormData)
  jsonConfig.value = ''
  jsonError.value = ''
  formRef.value?.resetFields()
}

// 切换配置模式
const toggleConfigMode = () => {
  if (configMode.value === 'form') {
    // 从表单模式切换到 JSON 模式
    jsonConfig.value = buildJsonFromForm()
    jsonError.value = ''
  } else {
    // 从 JSON 模式切换到表单模式
    if (jsonError.value) {
      message.error('请先修复 JSON 格式错误')
      return
    }
    parseJsonToForm(jsonConfig.value)
  }
  configMode.value = configMode.value === 'form' ? 'json' : 'form'
}

// 从表单数据构建 JSON
const buildJsonFromForm = (): string => {
  const config: Record<string, any> = {
    name: formData.name,
    type: formData.type,
    enabled: formData.enabled,
  }

  if (formData.description) {
    config.description = formData.description
  }

  if (formData.type === 'stdio') {
    config.command = formData.command
    if (formData.argsText) {
      config.args = formData.argsText.split('\n').filter((line) => line.trim())
    }
    if (formData.envText) {
      config.env = {}
      formData.envText.split('\n').forEach((line) => {
        const [key, ...valueParts] = line.split('=')
        if (key && valueParts.length > 0) {
          config.env[key.trim()] = valueParts.join('=').trim()
        }
      })
    }
  } else if (formData.type === 'http' || formData.type === 'sse') {
    config.url = formData.url
    if (formData.headersText) {
      config.headers = {}
      formData.headersText.split('\n').forEach((line) => {
        const [key, ...valueParts] = line.split(':')
        if (key && valueParts.length > 0) {
          config.headers[key.trim()] = valueParts.join(':').trim()
        }
      })
    }
  }

  if (formData.timeout && formData.timeout !== 60000) {
    config.timeout = formData.timeout
  }

  return JSON.stringify(config, null, 2)
}

// 解析 JSON 到表单数据
const parseJsonToForm = (jsonStr: string) => {
  try {
    const config = JSON.parse(jsonStr)
    formData.name = config.name || ''
    formData.description = config.description || ''
    formData.type = config.type || 'stdio'
    formData.enabled = config.enabled ?? true
    formData.command = config.command || ''
    formData.argsText = (config.args || []).join('\n')
    formData.envText = Object.entries(config.env || {})
      .map(([key, value]) => `${key}=${value}`)
      .join('\n')
    formData.url = config.url || ''
    formData.headersText = Object.entries(config.headers || {})
      .map(([key, value]) => `${key}: ${value}`)
      .join('\n')
    formData.timeout = config.timeout || 60000
    jsonError.value = ''
  } catch (e) {
    jsonError.value = 'JSON 格式错误，请检查'
  }
}

// 提交表单
const handleSubmit = async () => {
  if (configMode.value === 'json') {
    // JSON 模式提交
    try {
      JSON.parse(jsonConfig.value)
      jsonError.value = ''
    } catch (e) {
      jsonError.value = 'JSON 格式错误，请检查'
      return
    }

    submitting.value = true
    try {
      const config = JSON.parse(jsonConfig.value)
      const res = isEditing.value
        ? await updateMcpServerUsingPost({ ...config, id: formData.id })
        : await addMcpServerUsingPost(config)

      if (res.data.code === 0) {
        message.success(isEditing.value ? '更新成功' : '创建成功')
        formDrawerVisible.value = false
        loadMcpServerList()
      } else {
        message.error(res.data.message || '操作失败')
      }
    } catch (error) {
      message.error('操作失败')
      console.error('Submit error:', error)
    } finally {
      submitting.value = false
    }
  } else {
    // 表单模式提交
    try {
      await formRef.value?.validate()
    } catch (error) {
      return
    }

    submitting.value = true
    try {
      const submitData: Record<string, any> = {
        name: formData.name,
        type: formData.type,
        enabled: formData.enabled,
      }

      if (formData.description) {
        submitData.description = formData.description
      }

      if (formData.type === 'stdio') {
        submitData.command = formData.command
        if (formData.argsText) {
          submitData.args = formData.argsText.split('\n').filter((line) => line.trim())
        }
        if (formData.envText) {
          submitData.env = {}
          formData.envText.split('\n').forEach((line) => {
            const [key, ...valueParts] = line.split('=')
            if (key && valueParts.length > 0) {
              submitData.env[key.trim()] = valueParts.join('=').trim()
            }
          })
        }
      } else if (formData.type === 'http' || formData.type === 'sse') {
        submitData.url = formData.url
        if (formData.headersText) {
          submitData.headers = {}
          formData.headersText.split('\n').forEach((line) => {
            const [key, ...valueParts] = line.split(':')
            if (key && valueParts.length > 0) {
              submitData.headers[key.trim()] = valueParts.join(':').trim()
            }
          })
        }
      }

      if (formData.timeout && formData.timeout !== 60000) {
        submitData.timeout = formData.timeout
      }

      if (isEditing.value) {
        submitData.id = formData.id
        const res = await updateMcpServerUsingPost(submitData)
        if (res.data.code === 0) {
          message.success('更新成功')
          formDrawerVisible.value = false
          loadMcpServerList()
        } else {
          message.error(res.data.message || '更新失败')
        }
      } else {
        const res = await addMcpServerUsingPost(submitData)
        if (res.data.code === 0) {
          message.success('创建成功')
          formDrawerVisible.value = false
          loadMcpServerList()
        } else {
          message.error(res.data.message || '创建失败')
        }
      }
    } catch (error) {
      message.error('操作失败')
      console.error('Submit error:', error)
    } finally {
      submitting.value = false
    }
  }
}

// 删除 MCP 服务
const handleDelete = async (id?: number) => {
  if (!id) return
  try {
    const res = await deleteMcpServerUsingPost(id)
    if (res.data.code === 0) {
      message.success('删除成功')
      loadMcpServerList()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch (error) {
    message.error('删除失败')
    console.error('Delete error:', error)
  }
}

// 获取服务类型标签
const getServerTypeLabel = (type?: string) => {
  const typeMap: Record<string, string> = {
    stdio: 'STDIO',
    http: 'HTTP',
    sse: 'SSE',
  }
  return typeMap[type || ''] || '未知'
}

// 监听抽屉打开，加载数据
watch(
  () => props.open,
  (newVal) => {
    if (newVal) {
      loadMcpServerList()
    }
  }
)
</script>

<style scoped>
.mcp-service-drawer :deep(.ant-drawer-header) {
  border-bottom: 1px solid var(--color-border-subtle);
}

.mcp-service-drawer :deep(.ant-drawer-body) {
  padding: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  color: var(--color-text-tertiary);
  opacity: 0.5;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text-primary);
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.mcp-server-list :deep(.ant-list-item) {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-subtle);
}

.mcp-server-list :deep(.ant-list-item:last-child) {
  border-bottom: none;
}

.server-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-name {
  font-weight: 500;
  font-size: 14px;
}

.status-tag {
  font-size: 12px;
}

.server-description {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.description-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.description-text.no-desc {
  color: var(--color-text-tertiary);
  font-style: italic;
}

.server-type {
  font-size: 12px;
  color: var(--color-text-tertiary);
  background: var(--color-bg-secondary);
  padding: 2px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}

/* 表单抽屉样式 */
.mcp-form-drawer :deep(.ant-drawer-header) {
  border-bottom: 1px solid var(--color-border-subtle);
}

.mcp-form-drawer :deep(.ant-drawer-body) {
  padding: 16px;
}

.mcp-form-drawer :deep(.ant-drawer-footer) {
  border-top: 1px solid var(--color-border-subtle);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.json-config-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.json-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.json-textarea {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

.json-error {
  color: #ff4d4f;
  font-size: 13px;
}

.mcp-form :deep(.ant-form-item-label > label) {
  font-weight: 500;
}
</style>
