<template>
  <div class="agent-chat-page signature-page">
    <AgentChatLayout
      :left-collapsed="leftCollapsed"
      :right-expanded="resourcePanelVisible"
      @close-overlays="closeOverlays"
    >
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

        <!-- 会话标题栏：给三栏布局一个稳定的顶部锚点 -->
        <header class="chat-topbar" :class="{ 'has-left-toggle': leftCollapsed }">
          <div class="chat-identity">
            <span class="chat-identity-name">SpaceMind</span>
            <span class="chat-identity-sep" aria-hidden="true"></span>
            <span class="chat-identity-title">{{ activeThreadTitle }}</span>
          </div>
        </header>

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
          :loading="streaming || historyLoading || regenerating"
          :busy="regenerating"
          :session-id="currentThreadId || threadId"
          :asset-results="assetResults"
          @refresh-asset="refreshAsset"
          @preview-asset="handlePreviewImage"
          @asset-unavailable="handleAssetUnavailable"
          @send="handleSend"
          @toggle-resources="toggleResourcePanel"
          @regenerate="handleRegenerate"
        />

        <!-- 底部区域：TodoList + 输入框 -->
        <div class="bottom-area">
          <AgentTodoList
            v-if="showTodoList"
            :steps="todoSteps"
            :current-step="currentTodoStep"
            @toggle="toggleTodoListExpanded"
          />
          <AgentChatInput
            :loading="streaming"
            :busy="regenerating"
            :session-id="currentThreadId || threadId"
            :attachments-enabled="!assetMode"
            @send="handleSend"
            @stop="abort"
          />
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
import { feedbackUsingPost } from '@/api/agentController'
import { useLoginUserStore } from '@/stores/userLogin'
import { useRoute, useRouter } from 'vue-router'
import type { ResourceData, ImageResource, LinkResource } from '@/components/agent/types'
import { AgentStreamError, useAgentStream } from '@/composables/useAgentStream'
import {
  confirmAction,
  deleteAgentImage,
  dismissAction,
  getHistory,
  getRecoveryState,
  getSessions,
  resumeSession,
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
// 窄屏（平板/手机）下左右栏改为浮层：默认收起左栏，否则会盖住对话区
const narrowQuery = window.matchMedia('(max-width: 1024px)')
const isNarrowViewport = () => narrowQuery.matches
const handleNarrowChange = (event: MediaQueryListEvent) => {
  if (event.matches) {
    leftCollapsed.value = true
    resourcePanelVisible.value = false
  }
}
narrowQuery.addEventListener('change', handleNarrowChange)
onUnmounted(() => narrowQuery.removeEventListener('change', handleNarrowChange))
const threadId = ref(generateThreadId())
const currentThreadId = ref('')
// 窄屏默认收起历史栏，避免侧栏抽屉把消息区压成窄缝
const leftCollapsed = ref(isNarrowViewport())
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
    // 窄屏下资源面板是全屏浮层，自动展开会打断对话，交给右上角按钮
    resourcePanelVisible.value = !isNarrowViewport()
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

const activeThreadTitle = computed(() => {
  const active = sessions.value.find((s) => s.sessionId === currentThreadId.value)
  return active?.title || (currentThreadId.value ? '对话记录' : '新对话')
})

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
const handleSend = async (text: string, uploaded: AgentImage[] = []) => {
  if ((!text.trim() && uploaded.length === 0) || streaming.value) return
  if (regenerating.value) {
    message.info('正在重新生成，请稍候')
    return
  }
  if (assetMode.value && uploaded.length) {
    // 站内找图不用上传；若仍有残留资源，回收后拒绝发送，避免孤儿图片
    for (const item of uploaded) void deleteAgentImage(item.resourceId).catch(() => {})
    assetError.value = '请先选择团队空间；站内找图使用空间内已有素材'
    return
  }
  if (assetMode.value && !assetSpaceId.value) {
    assetError.value = '请先选择团队空间；站内找图使用空间内已有素材'
    return
  }

  // 清空上一轮的消息与资源，再放入本轮用户消息；
  // sendMessage 不再负责清空，否则会把刚 push 的用户气泡一起删掉
  resetStream()
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

// 「重新生成」走反馈端点：服务端基于当前会话历史再生成一版回复。
// 没有分支模型，新回复追加在末尾；服务端同时把回复写进历史。
const regenerating = ref(false)
const handleRegenerate = async () => {
  const id = currentThreadId.value || threadId.value
  if (!id || streaming.value || regenerating.value) return
  regenerating.value = true
  // 请求期间可能切到别的会话：结果只属于发起时的那个会话
  const isSameSession = () => (currentThreadId.value || threadId.value) === id
  try {
    const res = await feedbackUsingPost(
      {
        threadId: id,
        satisfied: false,
        reason: '重新生成',
        // 后端枚举按大写序列化，发送小写会直接 400
        action: 'REGENERATE',
      },
      // 重新生成要等模型完整跑完，默认 5 秒超时必然失败
      { timeout: 120000 },
    )
    if (!isSameSession()) return
    if (res.data?.code !== 0) {
      message.warning(res.data?.message || '重新生成失败，请稍后重试')
      return
    }
    const content = res.data?.data
    if (content) {
      pushMessage({
        type: 'assistant',
        content,
        node: 'agent',
        time: new Date().toLocaleTimeString(DATE_LOCALE, TIME_OPTIONS),
      })
    }
  } catch (error) {
    if (!isSameSession()) return
    message.error(error instanceof Error ? error.message : '重新生成失败，请重试')
  } finally {
    if (isSameSession()) regenerating.value = false
    await loadRecoveryState()
  }
}

const handleNewChat = () => {
  saveThreadId('')
  currentThreadId.value = ''
  threadId.value = generateThreadId()
  // 先中止在途流：resetStream 只清本地状态，不会停止仍在校验/生成的请求
  abort()
  // 在途的重新生成属于旧会话，不能让它的 busy 状态带到新会话
  regenerating.value = false
  resetStream()
  clearAssetResults(false)
  showTodoList.value = false
  resourcePanelVisible.value = false
  recoveryState.value = null
}

const handleSelectHistory = async (id: string) => {
  if (isNarrowViewport()) leftCollapsed.value = true
  // 重选当前会话不做任何事：不能中止在途流，也不能清掉重新生成的忙状态
  if (id === (currentThreadId.value || threadId.value)) return
  // 切换会话必须先停下旧流，否则旧会话的输出会继续写进新会话
  abort()
  regenerating.value = false
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

// 窄屏浮层遮罩点击后收起两侧栏
const closeOverlays = () => {
  leftCollapsed.value = true
  resourcePanelVisible.value = false
}

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
/* 会话标题栏：三栏布局的顶部锚点，给左右两个浮动按钮留出空间 */
.chat-topbar {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  height: 52px;
  padding: 0 64px 0 clamp(16px, 3vw, 24px);
  border-bottom: 1px solid var(--color-border-subtle);
  background: color-mix(in srgb, var(--color-bg-secondary) 55%, transparent);
}

.chat-topbar.has-left-toggle {
  padding-left: 60px;
}

.chat-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.chat-identity-name {
  flex-shrink: 0;
  font-family: var(--font-display);
  font-size: 0.85rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--color-text-primary);
}

.chat-identity-sep {
  flex-shrink: 0;
  width: 1px;
  height: 12px;
  background: var(--color-border-strong);
}

.chat-identity-title {
  overflow: hidden;
  font-size: 0.78rem;
  color: var(--color-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .chat-identity-title {
    display: none;
  }

  .chat-identity-sep {
    display: none;
  }
}

/* 站内找图：降为一条工具带，不与消息争注意力 */
.asset-search-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  padding: 10px clamp(16px, 3vw, 24px);
  border-bottom: 1px solid var(--color-border-subtle);
  background: color-mix(in srgb, var(--color-bg-secondary) 72%, transparent);
  color: var(--color-text-tertiary);
  font-family: var(--font-body);
  font-size: 0.75rem;
  letter-spacing: 0.01em;
}

.asset-search-controls label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.asset-search-controls input[type='checkbox'] {
  accent-color: var(--color-primary);
}

.asset-search-controls select,
.asset-search-controls button {
  border: 1px solid var(--color-border-default);
  border-radius: 10px;
  padding: 6px 10px;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
  font-family: inherit;
  font-size: 0.75rem;
}

.asset-search-controls button {
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    color 0.18s ease;
}

.asset-search-controls button:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.asset-search-controls button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.asset-search-controls p {
  flex-basis: 100%;
  margin: 0;
  color: var(--color-warning);
}

.asset-search-controls small {
  color: var(--color-text-tertiary);
}

.agent-chat-page {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-primary);
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
.quick-new-chat-btn,
.toggle-right-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-bg-elevated) 86%, transparent);
  color: var(--color-text-tertiary);
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition:
    color 0.18s ease,
    border-color 0.18s ease,
    background-color 0.18s ease;
}

.toggle-right-btn {
  position: absolute;
  top: 14px;
  right: 16px;
  z-index: 20;
}

.toggle-left-btn:hover,
.quick-new-chat-btn:hover,
.toggle-right-btn:hover {
  color: var(--color-primary);
  border-color: color-mix(in srgb, var(--color-primary) 40%, transparent);
}

.toggle-right-btn.active {
  color: var(--color-primary);
  border-color: color-mix(in srgb, var(--color-primary) 50%, transparent);
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
}

.resource-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-primary-600);
  color: #ffffff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.bottom-area {
  flex-shrink: 0;
  border-top: 1px solid var(--color-border-subtle);
  background: var(--color-bg-primary);
}
</style>
