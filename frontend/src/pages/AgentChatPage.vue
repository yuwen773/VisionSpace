<template>
  <div class="agent-chat-page">
    <AgentChatLayout :left-collapsed="leftCollapsed" :right-expanded="resourcePanelVisible">
      <!-- 左栏 -->
      <template #left>
        <LeftSidebar
          :histories="histories"
          :active-id="currentThreadId"
          @new-chat="handleNewChat"
          @select="handleSelectHistory"
          @collapse="leftCollapsed = true"
          @settings="settingsModalVisible = true"
        />
      </template>

      <!-- 中间 -->
      <template #center>
        <!-- 左折叠按钮组 -->
        <div v-if="leftCollapsed" class="collapsed-actions">
          <button
            class="toggle-left-btn"
            @click="leftCollapsed = false"
            aria-label="展开侧边栏"
            title="展开侧边栏"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            >
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>
          <button
            class="quick-new-chat-btn"
            @click="handleNewChat"
            aria-label="新对话"
            title="新对话"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
            >
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
        </div>

        <!-- 右栏切换按钮 -->
        <button
          class="toggle-right-btn"
          :class="{ active: resourcePanelVisible }"
          @click="resourcePanelVisible = !resourcePanelVisible"
          aria-label="切换资源面板"
          title="资源面板"
        >
          <svg
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
            <circle cx="8.5" cy="8.5" r="1.5" />
            <polyline points="21 15 16 10 5 21" />
          </svg>
          <span v-if="totalResourceCount > 0" class="resource-badge">{{ totalResourceCount }}</span>
        </button>

        <!-- 恢复状态：区分「可继续」「等待确认」「结果未知待核对」「不可恢复」 -->
        <RecoveryBanner
          :state="recoveryState"
          :busy="recoveryBusy"
          @resume="handleResume"
          @refresh="loadRecoveryState"
          @review="handleReviewAction"
        />

        <form class="asset-search-controls" @submit.prevent="handleAssetSearch">
          <label class="asset-mode"><input v-model="assetMode" type="checkbox" />站内找图</label>
          <template v-if="assetMode">
            <select v-model="assetSpaceId" aria-label="选择团队空间" required>
              <option value="" disabled>选择团队空间</option>
              <option v-for="team in assetTeams" :key="team.id" :value="team.id">
                {{ team.name }}
              </option>
            </select>
            <label><input v-model="landscapeOnly" type="checkbox" />横图</label>
            <label><input v-model="bluePurpleOnly" type="checkbox" />蓝 / 紫主色</label>
            <button type="submit" :disabled="assetBusy || !assetSpaceId">
              {{ assetBusy ? '查找中…' : '查找素材' }}
            </button>
            <small>也可以在下方描述找图条件</small>
          </template>
          <p v-if="assetError || agentStream.protocolError.value" role="status">
            {{ assetError || agentStream.protocolError.value }}
          </p>
        </form>
        <!-- 消息列表 -->
        <AgentMessageList
          :messages="messages"
          :loading="streaming || historyLoading"
          :session-id="currentThreadId || threadId"
          :asset-results="assetResults"
          @refresh-asset="refreshAsset"
          @preview-asset="handlePreviewImage"
          @asset-unavailable="handleAssetUnavailable"
          @confirm="handleConfirm"
          @cancel="handleCancel"
          @send="handleSend"
          @toggle-resources="toggleResourcePanel"
        />

        <!-- 底部区域：TodoList + 输入框 -->
        <div class="bottom-area">
          <AgentTodoList
            v-if="showTodoList"
            :steps="todoSteps"
            :current-step="currentTodoStep"
            @toggle="toggleTodoListExpanded"
          />
          <AgentChatInput :loading="streaming" @send="handleSend" @stop="abort" />
        </div>
      </template>

      <!-- 右栏 -->
      <template #right>
        <AgentResourcePanel
          :images="panelImages"
          :links="panelLinks"
          :asset-results="Object.values(assetResults)"
          :privacy-epoch="privateAssetEpoch"
          :thread-id="currentThreadId || threadId"
          @session-bound="bindProposalSession"
          @private-access-denied="clearAssetResults"
          @close-preview="closeAssetPreview"
          @refresh-asset="refreshAsset"
          @asset-unavailable="handleAssetUnavailable"
          @close="resourcePanelVisible = false"
          @preview-image="handlePreviewImage"
          @download-image="handleDownloadImage"
        />
      </template>
    </AgentChatLayout>

    <!-- 图片预览弹窗 -->
    <ImagePreview v-model:open="previewVisible" :url="previewUrl" />

    <!-- MCP 服务管理抽屉 -->
    <SettingsModal v-model:open="settingsModalVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import AgentChatLayout from '@/components/agent/AgentChatLayout.vue'
import type { HistoryItem } from '@/components/agent/LeftSidebar.vue'
import LeftSidebar from '@/components/agent/LeftSidebar.vue'
import AgentMessageList from '@/components/agent/AgentMessageList.vue'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'
import AgentTodoList from '@/components/agent/AgentTodoList.vue'
import AgentResourcePanel from '@/components/agent/AgentResourcePanel.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import SettingsModal from '@/components/agent/settings/SettingsModal.vue'
import RecoveryBanner from '@/components/agent/RecoveryBanner.vue'
import { searchAssets, readAssetResult, type AssetResult } from '@/api-custom/assetSearch'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController'
import { useLoginUserStore } from '@/stores/userLogin'
import { useRoute, useRouter } from 'vue-router'
import type { ResourceData, ImageResource, LinkResource } from '@/components/agent/types'
import { AgentStreamError, useAgentStream } from '@/composables/useAgentStream'
import {
  confirmAction,
  dismissAction,
  getHistory,
  getRecoveryState,
  getSessions,
  resumeSession,
  uploadAgentImage,
  type AgentImage,
  type HistoryMessage,
  type RecoveryState,
  type SessionItem,
} from '@/api-custom/agent'

const THREAD_ID_KEY = 'agent_current_thread_id'
const DATE_LOCALE = 'zh-CN'
const TIME_OPTIONS = { hour: '2-digit', minute: '2-digit' } as const

const generateThreadId = () =>
  typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

const loadThreadId = (): string => {
  return localStorage.getItem(THREAD_ID_KEY) || ''
}

const saveThreadId = (id: string) => {
  if (id) localStorage.setItem(THREAD_ID_KEY, id)
  else localStorage.removeItem(THREAD_ID_KEY)
}

// State
const threadId = ref(generateThreadId())
const currentThreadId = ref('')
const leftCollapsed = ref(false)
const resourcePanelVisible = ref(false)
const panelImages = ref<ImageResource[]>([])
const panelLinks = ref<LinkResource[]>([])
const todoListExpanded = ref(false)
const showTodoList = ref(false)
const historyLoading = ref(false)
const assetMode = ref(false)
const assetSpaceId = ref('')
const landscapeOnly = ref(true)
const bluePurpleOnly = ref(false)
const assetTeams = ref<{ id: string; name: string }[]>([])
const assetResults = ref<Record<string, AssetResult>>({})
const privateAssetEpoch = ref(0)
const assetBusy = ref(false)
const assetError = ref('')
const assetRequests = new Map<string, Promise<void>>()
let assetEpoch = 0
const route = useRoute()
const router = useRouter()
const loginStore = useLoginUserStore()
let teamRequestEpoch = 0
let assetPageActive = true

const loadAssetTeams = async () => {
  const epoch = ++teamRequestEpoch
  const accountId = loginStore.loginUser.id
  try {
    const response = await listMyTeamSpaceUsingPost()
    if (!assetPageActive || epoch !== teamRequestEpoch || accountId !== loginStore.loginUser.id)
      return
    assetTeams.value =
      response.data.code === 0
        ? (response.data.data ?? [])
            .filter((team) => team.spaceId)
            .map((team) => ({
              id: String(team.spaceId),
              name: team.space?.spaceName || '团队空间',
            }))
        : []
  } catch {
    if (epoch === teamRequestEpoch) assetTeams.value = []
  }
}

const assetFailureMessage = (reason?: string, code?: number) => {
  if (code === 40100) return '请登录后再查找团队素材'
  if (code === 40101) return '当前素材或会话不可用，请检查权限或新建对话'
  const messages: Record<string, string> = {
    RULE_NOT_READY: '颜色筛选规则尚未就绪，请先使用横图筛选',
    INVALID_OPERATION_STATE: '当前会话有未完成任务，请新建对话后找图',
    INVALID_REQUEST: '请选择横图或蓝 / 紫主色条件，或重新描述找图需求',
    RESOURCE_UNAVAILABLE: '当前素材或会话不可用，请检查权限或新建对话',
  }
  return messages[reason ?? ''] ?? '站内找图失败，请稍后重试'
}

const clearAssetResults = (clearBoard = true) => {
  if (clearBoard) privateAssetEpoch.value++
  assetEpoch++
  assetRequests.clear()
  assetResults.value = {}
  previewVisible.value = false
  previewUrl.value = ''
}

const handleAssetUnavailable = (id: string) => {
  delete assetResults.value[id]
  previewVisible.value = false
  assetError.value = '素材预览不可用，请刷新状态后重试'
}

const refreshAsset = (id: string): Promise<void> => {
  if (assetRequests.has(id)) return assetRequests.get(id)!
  const epoch = assetEpoch
  delete assetResults.value[id]
  const task = (async () => {
    try {
      const res = await readAssetResult(id)
      if (epoch !== assetEpoch) return
      if (res.data.code !== 0 || res.data.data?.schemaVersion !== 1) {
        clearAssetResults([40100, 40101, 40300].includes(res.data.code))
        assetError.value = '检索结果不可用，请检查当前权限或重新查找'
        return
      }
      assetResults.value[id] = res.data.data
      assetError.value = ''
    } catch {
      if (epoch === assetEpoch) assetError.value = '读取检索结果失败，请重试'
    } finally {
      if (epoch === assetEpoch) assetRequests.delete(id)
    }
  })()
  assetRequests.set(id, task)
  return task
}

const handleAssetSearch = async () => {
  if (!assetSpaceId.value || assetBusy.value) return
  assetBusy.value = true
  const epoch = assetEpoch
  try {
    const res = await searchAssets({
      spaceId: assetSpaceId.value,
      criteria: {
        ...(landscapeOnly.value ? { orientation: 'LANDSCAPE' as const } : {}),
        ...(bluePurpleOnly.value ? { dominantColorFamily: 'BLUE_OR_PURPLE' as const } : {}),
      },
    })
    if (epoch !== assetEpoch) return
    if (res.data.code !== 0 || res.data.data?.schemaVersion !== 1) {
      clearAssetResults([40100, 40101, 40300].includes(res.data.code))
      assetError.value = assetFailureMessage(res.data.message, res.data.code)
      return
    }
    const result = res.data.data
    assetResults.value[result.resultId] = result
    pushMessage({ type: 'asset-result', resultId: result.resultId, content: '', node: 'agent' })
    resourcePanelVisible.value = true
    assetError.value = ''
    await router.replace({ query: { ...route.query, assetResult: result.resultId } })
  } catch {
    assetError.value = '查找素材失败，请重试'
  } finally {
    assetBusy.value = false
  }
}

// Sessions
const sessions = ref<SessionItem[]>([])

const loadSessions = async () => {
  try {
    const res = await getSessions({ limit: 20 })
    sessions.value = res?.data?.data || []
  } catch (e) {
    console.warn('加载会话列表失败:', e)
  }
}

const histories = computed<HistoryItem[]>(() =>
  sessions.value.map((s) => ({
    id: s.sessionId,
    title: s.title || '新对话',
    time: s.updatedTime ? new Date(s.updatedTime).toLocaleString(DATE_LOCALE) : '',
  })),
)

const bindProposalSession = (id: string) => {
  currentThreadId.value = id
  threadId.value = id
  saveThreadId(id)
  void loadSessions()
}

const loadHistoryMessages = async (sessionId: string) => {
  historyLoading.value = true
  try {
    const res = await getHistory(sessionId, { limit: 100 })
    // 共享 request 实例对业务错误也返回 response（不 reject），所以先看 code
    const code = res.data?.code
    if (code === 40100 || code === 40101) {
      // 本地缓存的会话已失效或不再属于当前账号：清掉它并回到新对话，
      // 否则每次进页面都会重复失败
      saveThreadId('')
      currentThreadId.value = ''
      threadId.value = generateThreadId()
      messages.value = []
      clearAssetResults()
      message.warning('该会话不可用，已为你新建对话')
      return
    }
    const data = res.data?.data || []
    if (!data?.messages) {
      return
    }

    messages.value = data.messages.map((m) => {
      let type: string
      if (m.role === 'USER') {
        type = 'user'
      } else {
        switch (m.subType) {
          case 'board-proposal':
            type = 'board-proposal'
            break
          case 'mcp-confirm':
          case 'tool-confirm':
            type = 'tool-confirm'
            break
          case 'asset-result':
            type = 'asset-result'
            break
          case 'reasoning':
            type = 'reasoning'
            break
          case 'tool-call':
            type = 'tool-request'
            break
          case 'tool-result':
            type = 'tool-response'
            break
          default:
            type = 'assistant'
        }
      }
      return {
        type,
        content: m.content,
        resultId: type === 'asset-result' ? m.content : undefined,
        taskId: type === 'board-proposal' ? m.content : undefined,
        // 历史图片按受控入口渲染：正文里不再保留源站地址
        attachmentIds: m.attachmentIds ?? [],
        toolName:
          m.subType === 'tool-call' || m.subType === 'tool-result'
            ? m.content.split(':')[0] || '工具'
            : undefined,
        node: 'agent',
        isLoading: false,
        time: m.createdTime
          ? new Date(m.createdTime).toLocaleTimeString(DATE_LOCALE, TIME_OPTIONS)
          : '',
      }
    })
    await Promise.all(
      [...new Set(messages.value.filter((m) => m.resultId).map((m) => m.resultId!))].map(
        refreshAsset,
      ),
    )
  } catch (e) {
    console.warn('加载历史消息失败:', e)
    message.warning('历史消息加载失败')
  } finally {
    historyLoading.value = false
  }
}

// 恢复状态
const recoveryState = ref<RecoveryState | null>(null)
const recoveryBusy = ref(false)

const loadRecoveryState = async () => {
  const id = currentThreadId.value || threadId.value
  if (!id) {
    recoveryState.value = null
    return
  }
  try {
    const res = await getRecoveryState(id)
    recoveryState.value = res.data?.code === 0 ? (res.data.data ?? null) : null
  } catch {
    // 状态查询失败不阻塞聊天；用户仍可正常发消息
    recoveryState.value = null
  }
}

const handleResume = async (version: number | null) => {
  const id = currentThreadId.value || threadId.value
  if (!id || recoveryBusy.value) return
  recoveryBusy.value = true
  try {
    const res = await resumeSession(id, version)
    const code = res.data?.code
    if (code !== 0) {
      message.warning(res.data?.message || '无法继续执行，请刷新状态后重试')
      return
    }
    if (res.data?.data) {
      pushMessage({
        type: 'assistant',
        content: res.data.data,
        node: 'agent',
        time: new Date().toLocaleTimeString(DATE_LOCALE, TIME_OPTIONS),
      })
    }
  } catch {
    message.error('继续执行失败，请重试')
  } finally {
    recoveryBusy.value = false
    await loadRecoveryState()
  }
}

const handleReviewAction = async (actionId: string, decision: 'confirm' | 'dismiss') => {
  const id = currentThreadId.value || threadId.value
  if (!id || recoveryBusy.value) return
  recoveryBusy.value = true
  try {
    const res =
      decision === 'confirm' ? await confirmAction(id, actionId) : await dismissAction(id, actionId)
    if (res.data?.code !== 0) {
      message.warning(res.data?.message || '核对失败，请刷新后重试')
      return
    }
    recoveryState.value = res.data.data ?? null
  } catch {
    message.error('核对失败，请重试')
  } finally {
    recoveryBusy.value = false
  }
}

onMounted(async () => {
  const savedThreadId = loadThreadId()
  currentThreadId.value = savedThreadId
  threadId.value = savedThreadId || threadId.value
  await Promise.all([
    loadSessions(),
    savedThreadId ? loadHistoryMessages(savedThreadId) : Promise.resolve(),
  ])
  if (savedThreadId) {
    await loadRecoveryState()
  }
  await loadAssetTeams()
  if (!assetPageActive) return
  if (typeof route.query.board === 'string' || typeof route.query.task === 'string')
    resourcePanelVisible.value = true
  else if (typeof route.query.assetResult === 'string') {
    const id = route.query.assetResult
    await refreshAsset(id)
    if (assetResults.value[id]) {
      pushMessage({ type: 'asset-result', content: '', resultId: id, node: 'agent' })
      resourcePanelVisible.value = true
    }
  }
  window.addEventListener('focus', refreshVisibleAssets)
})
const refreshVisibleAssets = () => {
  closeAssetPreview()
  if (Object.keys(assetResults.value).length) {
    void Promise.all(Object.keys(assetResults.value).map(refreshAsset))
  }
}
const closeAssetPreview = () => {
  previewVisible.value = false
  previewUrl.value = ''
}
onUnmounted(() => {
  assetPageActive = false
  teamRequestEpoch++
  clearAssetResults()
  window.removeEventListener('focus', refreshVisibleAssets)
})

// Todo steps
const todoSteps = ref([
  { title: '探索', description: '分析用户需求', status: 'pending' as const },
  { title: '评审', description: '检查素材质量', status: 'pending' as const },
  { title: '生成', description: '生成图片', status: 'pending' as const },
  { title: '优化', description: '等待用户反馈', status: 'pending' as const },
])

const currentTodoStep = ref(0)

// Agent stream
const agentStream = useAgentStream()
const messages = agentStream.messages
const streaming = agentStream.isStreaming
const sendMessage = agentStream.sendMessage
const abort = agentStream.abort
const pushMessage = agentStream.pushMessage
const resetStream = agentStream.reset

const totalResourceCount = computed(
  () =>
    panelImages.value.length +
    panelLinks.value.length +
    Object.values(assetResults.value).reduce((sum, result) => sum + result.items.length, 0),
)
watch(
  () => messages.value.map((m) => m.resultId).filter((id): id is string => !!id),
  (ids) => {
    for (const id of new Set(ids)) if (!assetResults.value[id]) void refreshAsset(id)
  },
)
watch(
  () => loginStore.loginUser.id,
  () => {
    teamRequestEpoch++
    clearAssetResults()
    abort()
    resetStream()
    assetTeams.value = []
    assetSpaceId.value = ''
    if (loginStore.loginUser.id) void loadAssetTeams()
  },
)
watch(resourcePanelVisible, (visible) => {
  if (visible) refreshVisibleAssets()
})

// Watch streaming state
watch(streaming, (isStreaming) => {
  if (isStreaming) {
    showTodoList.value = !assetMode.value
  }
})

// Handlers
const handleSend = async (text: string, files: File[] = []) => {
  if ((!text.trim() && files.length === 0) || streaming.value) return
  if (assetMode.value && (!assetSpaceId.value || files.length)) {
    assetError.value = '请先选择团队空间；站内找图使用空间内已有素材'
    return
  }

  // 先上传再发消息：上传拿到的是资源标识，绑定到会话的是标识而不是地址
  let uploaded: AgentImage[] = []
  if (files.length > 0) {
    try {
      uploaded = await Promise.all(files.map((f) => uploadAgentImage(f)))
    } catch {
      message.error('图片上传失败，无法发送消息')
      return
    }
  }

  pushMessage({
    type: 'user',
    content: text,
    node: 'user',
    attachmentIds: uploaded.map((item) => item.resourceId),
    time: new Date().toLocaleTimeString(DATE_LOCALE, TIME_OPTIONS),
  })

  try {
    await sendMessage(
      text,
      threadId.value,
      uploaded,
      assetMode.value ? assetSpaceId.value : undefined,
    )
    saveThreadId(threadId.value)
    currentThreadId.value = threadId.value
  } catch (error) {
    // 业务拒绝（未登录、会话不属于当前用户）给出后端文案，其余按网络失败处理
    if (assetMode.value) {
      clearAssetResults(
        error instanceof AgentStreamError && [40100, 40101, 40300].includes(error.code ?? 0),
      )
      assetError.value = assetFailureMessage(
        error instanceof Error ? error.message : undefined,
        error instanceof AgentStreamError ? error.code : undefined,
      )
      message.warning(assetError.value)
    } else {
      message.error(error instanceof AgentStreamError ? error.message : '发送失败，请重试')
    }
  } finally {
    // 执行可能停在待确认或待核对，刷新一次状态让提示与实际一致
    await loadRecoveryState()
  }
}

const handleNewChat = () => {
  saveThreadId('')
  currentThreadId.value = ''
  threadId.value = generateThreadId()
  resetStream()
  clearAssetResults(false)
  showTodoList.value = false
  resourcePanelVisible.value = false
  recoveryState.value = null
}

const handleSelectHistory = async (id: string) => {
  currentThreadId.value = id
  threadId.value = id
  saveThreadId(id)
  resetStream()
  clearAssetResults(false)
  await loadHistoryMessages(id)
  await loadRecoveryState()
}

const toggleResourcePanel = (data?: ResourceData) => {
  if (data) {
    panelImages.value = data.images
    panelLinks.value = data.links
  }
  resourcePanelVisible.value = !resourcePanelVisible.value
}

const toggleTodoListExpanded = () => {
  todoListExpanded.value = !todoListExpanded.value
}

const handleConfirm = () => {}
const handleCancel = () => {}

// 图片预览
const previewVisible = ref(false)
const previewUrl = ref('')
const settingsModalVisible = ref(false)

const handlePreviewImage = (url: string) => {
  previewUrl.value = url
  previewVisible.value = true
}

const handleDownloadImage = (url: string) => {
  window.open(url, '_blank')
}
</script>

<style scoped>
.asset-search-controls {
  padding: 10px 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  color: var(--color-text-secondary);
  font-size: 12px;
  border-bottom: 1px solid var(--color-border-subtle);
}
.asset-search-controls label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.asset-search-controls select,
.asset-search-controls button {
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  padding: 7px 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
}
.asset-search-controls button {
  cursor: pointer;
}
.asset-search-controls p {
  flex-basis: 100%;
  margin: 0;
  color: var(--color-warning, #a66a20);
}
.asset-search-controls small {
  color: var(--color-text-tertiary);
}
.agent-chat-page {
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-primary);
  position: relative;
  display: flex;
  flex-direction: column;
}

.collapsed-actions {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toggle-left-btn,
.quick-new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-left-btn:hover,
.quick-new-chat-btn:hover {
  color: var(--color-text-primary);
  border-color: var(--color-border-default);
}

.quick-new-chat-btn:hover {
  color: var(--color-primary-500);
  border-color: var(--color-primary-500);
}

.toggle-right-btn {
  position: absolute;
  top: 14px;
  right: 20px;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.toggle-right-btn:hover {
  color: var(--color-text-primary);
  border-color: var(--color-border-default);
}

.toggle-right-btn.active {
  color: var(--color-primary-500);
  border-color: var(--color-primary-500);
  background: rgba(168, 85, 247, 0.08);
}

.resource-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary-500);
  color: white;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.bottom-area {
  flex-shrink: 0;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
}
</style>
