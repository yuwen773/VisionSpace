import { ref, onUnmounted } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { deleteAgentImage, type AgentImage } from '@/api-custom/agent'
import { API_ROOT, CSRF_HEADER, readCsrfToken } from '@/apiOrigin'
import { extractImages, extractLinks } from '@/utils/markdownResources'

export interface MessageDTO {
  schemaVersion?: number
  eventId?: string
  type?: string
  payload?: {
    resultId?: string
    taskId?: string
    proposalId?: string
    proposalRevision?: number
    boardId?: string
    expectedRevision?: number
    revision?: number
    operationId?: string
    reasonCode?: string
  }
  messageType:
    | 'user'
    | 'assistant'
    | 'tool-request'
    | 'tool-response'
    | 'mcp-confirm'
    | 'tool-confirm'
    | 'reasoning'
    | 'action-unknown'
    | 'error'
    | 'done'
  content?: string
  toolCalls?: any[]
  toolName?: string
}

export interface ToolCallArg {
  name: string
  args: Record<string, any>
}

/**
 * 流式入口的业务错误（未登录、会话不属于当前用户、参数不合法）。
 * 与网络异常分开，页面据此给出可操作的提示。
 */
export class AgentStreamError extends Error {
  constructor(
    message: string,
    readonly code?: number,
  ) {
    super(message)
    this.name = 'AgentStreamError'
  }
}

export interface StreamMessage {
  /** 'action-unknown' 表示外部结果未知，需要用户核对后才能继续 */
  type: string
  content: string
  node: string
  isLoading?: boolean
  toolName?: string
  toolCalls?: ToolCallArg[]
  time?: string
  /** 这条消息携带的图片资源标识；图片按受控入口渲染 */
  attachmentIds?: string[]
  resultId?: string
  taskId?: string
  boardId?: string
  revision?: number
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  const images = ref<{ url: string; title?: string }[]>([])
  const links = ref<{ url: string; title: string; snippet: string; domain: string }[]>([])
  const protocolError = ref('')
  let abortController: AbortController | null = null
  /**
   * 流代次。切换会话、取消或重新发送时递增，
   * 在途请求的回调据此判断自己是否已经过期，避免旧流写进新会话。
   */
  let streamEpoch = 0

  const extractResources = (content: string) => {
    if (!content) return

    const seenImageUrls = new Set(images.value.map((img) => img.url))
    for (const image of extractImages(content)) {
      if (seenImageUrls.has(image.url)) continue
      images.value.push({ url: image.url, title: image.title || '图片' })
      seenImageUrls.add(image.url)
    }

    const seenLinkUrls = new Set(links.value.map((link) => link.url))
    for (const link of extractLinks(content)) {
      if (seenLinkUrls.has(link.url)) continue
      links.value.push(link)
      seenLinkUrls.add(link.url)
    }
  }

  const pushMessage = (msg: StreamMessage) => {
    messages.value.push(msg)
  }

  const updateMessage = (index: number, updates: Partial<StreamMessage>) => {
    Object.assign(messages.value[index], updates)
  }

  /** 结束仍在流式中的气泡：取消或错误后不能让它一直显示光标。 */
  const finishStreamingMessages = () => {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if ((msg.type === 'assistant' || msg.type === 'reasoning') && msg.isLoading) {
        msg.isLoading = false
      }
    }
  }

  /** 中止在途请求并让它的所有回调失效。 */
  const invalidateStream = () => {
    finishStreamingMessages()
    streamEpoch++
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isStreaming.value = false
  }

  const reset = () => {
    invalidateStream()
    messages.value = []
    images.value = []
    links.value = []
    protocolError.value = ''
  }

  /**
   * 发送消息。
   *
   * @param message  用户输入的文字
   * @param threadId 会话标识
   * @param uploaded 已上传的附件；绑定到会话后图片保留，不会因为流结束被删除
   */
  const sendMessage = async (
    message: string,
    threadId: string,
    uploaded: AgentImage[] = [],
    assetSpaceId?: string,
  ) => {
    // 上一段流即使还没完全收尾，也不再允许写入。
    // 调用方负责在发送前清空消息列表：用户消息是先 push 再发送的，
    // 如果这里 reset 会把刚加进去的用户气泡一起清掉。
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    const epoch = ++streamEpoch
    const controller = new AbortController()
    abortController = controller
    isStreaming.value = true
    /** 本次请求是否仍是当前有效的那一段流。 */
    const isCurrent = () => epoch === streamEpoch

    // 与 REST/上传共用同一个来源，生产走同源 HTTPS，不会出现混合内容
    const url = `${API_ROOT}/agent/image/chat/stream`

    let assistantMsgIndex = -1
    let reasoningMsgIndex = -1
    let streamContent = ''
    let lastAssistantContent = ''
    // 服务端是否已经接收这次请求：接收后图片已经绑定到会话，任何结束方式
    // （成功、异常、取消、断开）都不再由前端删除
    let serverAccepted = false
    const seenResults = new Set<string>()
    const seenEvents = new Set<string>()
    const boardVersions = new Map<string, number>()

    /** 结束思考块：思考内容在下一个消息块出现时就不再流式了。 */
    const closeReasoning = () => {
      if (reasoningMsgIndex !== -1) {
        updateMessage(reasoningMsgIndex, { isLoading: false })
        reasoningMsgIndex = -1
      }
    }

    /**
     * 结束当前正文气泡。工具调用、确认、错误都会打断正文流，
     * 此后不再有内容写进这个气泡，光标必须立刻停。
     */
    const closeAssistant = () => {
      if (assistantMsgIndex !== -1) {
        updateMessage(assistantMsgIndex, { isLoading: false })
        assistantMsgIndex = -1
      }
    }

    // 同时接受 JSON：归属或登录校验失败时后端返回的是普通 JSON 错误，
    // 只声明 text/event-stream 会把它变成无法读取的 406。
    // 流式 POST 属于状态修改：带上双提交 CSRF 令牌。
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      accept: 'text/event-stream, application/json',
      [CSRF_HEADER]: readCsrfToken(),
      'X-VS-AI-Event-Version': '1',
    }
    const body = JSON.stringify({
      message,
      // 绑定的是资源标识而不是地址：服务端按归属记录解析可信地址
      attachmentIds: uploaded.length > 0 ? uploaded.map((item) => item.resourceId) : undefined,
      threadId,
      assetSpaceId,
    })

    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers,
        body,
        signal: controller.signal,
        credentials: 'include',
        openWhenHidden: true,

        async onopen(response) {
          if (!response.ok) {
            throw new AgentStreamError('发送失败，请重试', response.status)
          }
          // 归属或登录校验在建立流之前完成，拒绝时后端返回的是普通 JSON 错误。
          // 把它当成空流会让用户以为消息发出去了，这里必须显式失败。
          const contentType = response.headers.get('content-type') || ''
          if (!contentType.includes('text/event-stream')) {
            const payload = await response.json().catch(() => null)
            throw new AgentStreamError(
              payload?.message || '无法访问该会话，请新建对话后重试',
              payload?.code,
            )
          }
          // 流已经建立：服务端已把附件绑定到会话，此后不再由前端删除图片
          serverAccepted = true
        },

        onmessage(event) {
          if (!isCurrent()) return
          if (!event.data) return

          try {
            const msgDto: MessageDTO = JSON.parse(event.data)

            if (msgDto.schemaVersion !== undefined) {
              const p = msgDto.payload
              const validId = (id: unknown): id is string =>
                typeof id === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(id)
              if (msgDto.schemaVersion === 1 && msgDto.eventId && p) {
                if (seenEvents.has(msgDto.eventId)) return
                if (
                  msgDto.type === 'CONFIRMATION_REQUIRED' &&
                  validId(p.taskId) &&
                  validId(p.proposalId) &&
                  validId(p.boardId) &&
                  p.proposalRevision === 1 &&
                  Number.isSafeInteger(p.expectedRevision) &&
                  p.expectedRevision! >= 1
                ) {
                  seenEvents.add(msgDto.eventId)
                  if (!messages.value.some((m) => m.taskId === p.taskId))
                    pushMessage({
                      type: 'board-proposal',
                      taskId: p.taskId,
                      content: '',
                      node: 'agent',
                    })
                  return
                }
                if (
                  ['BOARD_UPDATED', 'OPERATION_COMMITTED'].includes(msgDto.type || '') &&
                  validId(p.boardId) &&
                  Number.isSafeInteger(p.revision) &&
                  p.revision! >= 1 &&
                  (msgDto.type !== 'OPERATION_COMMITTED' || validId(p.operationId))
                ) {
                  seenEvents.add(msgDto.eventId)
                  if (p.revision! > (boardVersions.get(p.boardId) || 0)) {
                    boardVersions.set(p.boardId, p.revision!)
                    pushMessage({
                      type: 'board-updated',
                      boardId: p.boardId,
                      revision: p.revision,
                      content: '',
                      node: 'agent',
                    })
                  }
                  return
                }
                if (
                  msgDto.type === 'OPERATION_NEEDS_REVIEW' &&
                  validId(p.taskId) &&
                  validId(p.operationId) &&
                  typeof p.reasonCode === 'string'
                ) {
                  seenEvents.add(msgDto.eventId)
                  pushMessage({
                    type: 'action-unknown',
                    content: '外部动作结果未知，请在恢复面板核对',
                    node: 'agent',
                  })
                  return
                }
              }
              const resultId = msgDto.payload?.resultId
              if (
                msgDto.schemaVersion !== 1 ||
                msgDto.type !== 'SEARCH_RESULTS_READY' ||
                !msgDto.eventId ||
                !resultId ||
                !/^[a-zA-Z0-9-]{1,64}$/.test(resultId)
              ) {
                protocolError.value = '结果格式已变化，请刷新页面后重新读取状态'
                return
              }
              if (!seenResults.has(resultId)) {
                seenResults.add(resultId)
                pushMessage({ type: 'asset-result', content: '', node: 'agent', resultId })
              }
              return
            }

            switch (msgDto.messageType) {
              case 'assistant':
                closeReasoning()
                streamContent += msgDto.content || ''
                lastAssistantContent = msgDto.content || ''
                if (assistantMsgIndex === -1) {
                  assistantMsgIndex = messages.value.length
                  pushMessage({
                    type: 'assistant',
                    content: streamContent,
                    node: 'agent',
                    isLoading: true,
                  })
                } else {
                  updateMessage(assistantMsgIndex, { content: streamContent })
                }
                break

              case 'reasoning':
                if (reasoningMsgIndex === -1) {
                  reasoningMsgIndex = messages.value.length
                  pushMessage({
                    type: 'reasoning',
                    content: msgDto.content || '',
                    node: 'agent',
                    isLoading: true,
                  })
                } else {
                  updateMessage(reasoningMsgIndex, {
                    content: messages.value[reasoningMsgIndex].content + (msgDto.content || ''),
                  })
                }
                break

              case 'tool-request': {
                closeReasoning()
                closeAssistant()
                streamContent = ''
                const toolCalls = msgDto.toolCalls || []
                for (const tc of toolCalls) {
                  const name = tc.name || '工具'
                  let parsedArgs: Record<string, any> = {}
                  try {
                    parsedArgs = JSON.parse(tc.arguments || '{}')
                  } catch {
                    parsedArgs = { _raw: tc.arguments }
                  }
                  pushMessage({
                    type: 'tool-request',
                    content: '',
                    node: 'agent',
                    toolName: name,
                    toolCalls: [{ name, args: parsedArgs }],
                  })
                }
                break
              }

              case 'tool-confirm':
              case 'mcp-confirm':
                closeReasoning()
                closeAssistant()
                streamContent = ''
                if (
                  !messages.value.some(
                    (m) => m.type === 'tool-confirm' && m.content === (msgDto.content || ''),
                  )
                ) {
                  pushMessage({
                    type: 'tool-confirm',
                    content: msgDto.content || '',
                    node: 'agent',
                  })
                }
                break

              // 外部结果未知：与明确失败分开。这里不当作错误重试，
              // 用户需要先核对动作是否真的发生。
              case 'action-unknown':
                closeReasoning()
                closeAssistant()
                streamContent = ''
                pushMessage({
                  type: 'action-unknown',
                  content: msgDto.content || '',
                  node: 'agent',
                  toolName: msgDto.toolName,
                })
                break

              case 'tool-response': {
                closeReasoning()
                closeAssistant()
                streamContent = ''
                const toolContent = msgDto.content || ''
                pushMessage({
                  type: 'tool-response',
                  content: toolContent,
                  node: 'agent',
                  toolName: msgDto.toolName,
                })
                extractResources(toolContent)
                break
              }

              // 服务端运行期失败：单独落成一条错误消息，
              // 而不是让流无声结束，用户至少知道这次没有成功。
              case 'error':
                closeReasoning()
                // 失败前已经流出的半个回答要停止光标，否则看起来还在生成
                closeAssistant()
                streamContent = ''
                pushMessage({
                  type: 'error',
                  content: msgDto.content || '系统繁忙，请稍后重试',
                  node: 'agent',
                })
                break

              // 显式结束帧：与 onclose 做同一件事，保持幂等
              case 'done':
                closeReasoning()
                closeAssistant()
                break
            }
          } catch (e) {
            console.error('JSON parse error:', e)
          }
        },

        onclose() {
          if (!isCurrent()) return
          // Extract resources from final accumulated assistant content
          if (lastAssistantContent) {
            extractResources(streamContent)
          }
          closeReasoning()
          closeAssistant()
        },

        onerror(err) {
          if (!isCurrent()) return
          console.error('SSE error:', err)
          closeReasoning()
          closeAssistant()
          throw err
        },
      })
    } catch (error) {
      // 已被新请求或会话切换取代的流：它的失败与当前界面无关
      if (!isCurrent()) return
      throw error
    } finally {
      // 请求没有被服务端接收时，附件还没有绑定到会话：这时丢弃它。
      // 服务端已接收的附件一律不删——流成功、异常、取消与断开都不删已绑定的图片。
      if (!serverAccepted && uploaded.length > 0) {
        for (const item of uploaded) {
          deleteAgentImage(item.resourceId).catch(() => {})
        }
      }
      // 只有当前流才有权改写共享的流式状态；过期流不能把新流的 loading 关掉
      if (isCurrent()) {
        isStreaming.value = false
        abortController = null
      }
    }
  }

  const abort = () => {
    invalidateStream()
  }

  onUnmounted(() => {
    abort()
  })

  return {
    messages,
    isStreaming,
    images,
    links,
    protocolError,
    sendMessage,
    abort,
    pushMessage,
    reset,
  }
}
