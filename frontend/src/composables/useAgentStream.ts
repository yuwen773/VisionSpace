import { ref, onUnmounted } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { deleteAgentImage, type AgentImage } from '@/api-custom/agent'
import { API_ROOT, CSRF_HEADER, readCsrfToken } from '@/apiOrigin'

export interface MessageDTO {
  messageType:
    | 'user'
    | 'assistant'
    | 'tool-request'
    | 'tool-response'
    | 'mcp-confirm'
    | 'reasoning'
    | 'action-unknown'
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
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  const images = ref<{ url: string; title?: string }[]>([])
  const links = ref<{ url: string; title: string; snippet: string; domain: string }[]>([])
  let abortController: AbortController | null = null

  const extractResources = (content: string) => {
    if (!content) return

    const seenImageUrls = new Set(images.value.map((img) => img.url))
    for (const match of content.matchAll(/!\[([^\]]*)\]\(([^)]+)\)/g)) {
      const url = match[2]
      if (!seenImageUrls.has(url)) {
        images.value.push({ url, title: match[1] || '图片' })
        seenImageUrls.add(url)
      }
    }

    const seenLinkUrls = new Set(links.value.map((l) => l.url))
    // Negative lookahead (?!!) excludes image syntax ![alt](url)
    for (const match of content.matchAll(/(?!!)\[([^\]]+)\]\(([^)]+)\)/g)) {
      const url = match[2]
      if (!seenLinkUrls.has(url)) {
        try {
          links.value.push({
            url,
            title: match[1],
            snippet: match[1],
            domain: new URL(url).hostname,
          })
        } catch {
          // invalid URL
        }
      }
    }
  }

  const pushMessage = (msg: StreamMessage) => {
    messages.value.push(msg)
  }

  const updateMessage = (index: number, updates: Partial<StreamMessage>) => {
    Object.assign(messages.value[index], updates)
  }

  const reset = () => {
    messages.value = []
    images.value = []
    links.value = []
  }

  /**
   * 发送消息。
   *
   * @param message  用户输入的文字
   * @param threadId 会话标识
   * @param uploaded 已上传的附件；绑定到会话后图片保留，不会因为流结束被删除
   */
  const sendMessage = async (message: string, threadId: string, uploaded: AgentImage[] = []) => {
    isStreaming.value = true
    reset()
    abortController = new AbortController()

    // 与 REST/上传共用同一个来源，生产走同源 HTTPS，不会出现混合内容
    const url = `${API_ROOT}/agent/image/chat/stream`

    let assistantMsgIndex = -1
    let reasoningMsgIndex = -1
    let streamContent = ''
    let lastAssistantContent = ''
    // 服务端是否已经接收这次请求：接收后图片已经绑定到会话，任何结束方式
    // （成功、异常、取消、断开）都不再由前端删除
    let serverAccepted = false

    // 同时接受 JSON：归属或登录校验失败时后端返回的是普通 JSON 错误，
    // 只声明 text/event-stream 会把它变成无法读取的 406。
    // 流式 POST 属于状态修改：带上双提交 CSRF 令牌。
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      accept: 'text/event-stream, application/json',
      [CSRF_HEADER]: readCsrfToken(),
    }
    const body = JSON.stringify({
      message,
      // 绑定的是资源标识而不是地址：服务端按归属记录解析可信地址
      attachmentIds: uploaded.length > 0 ? uploaded.map((item) => item.resourceId) : undefined,
      threadId,
    })

    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers,
        body,
        signal: abortController.signal,
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
          if (!event.data) return

          try {
            const msgDto: MessageDTO = JSON.parse(event.data)

            switch (msgDto.messageType) {
              case 'assistant':
                reasoningMsgIndex = -1
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
                  pushMessage({ type: 'reasoning', content: msgDto.content || '', node: 'agent' })
                } else {
                  updateMessage(reasoningMsgIndex, {
                    content: messages.value[reasoningMsgIndex].content + (msgDto.content || ''),
                  })
                }
                break

              case 'tool-request': {
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
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

              case 'mcp-confirm':
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
                streamContent = ''
                pushMessage({ type: 'mcp-confirm', content: msgDto.content || '', node: 'agent' })
                break

              // 外部结果未知：与明确失败分开。这里不当作错误重试，
              // 用户需要先核对动作是否真的发生。
              case 'action-unknown':
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
                streamContent = ''
                pushMessage({
                  type: 'action-unknown',
                  content: msgDto.content || '',
                  node: 'agent',
                  toolName: msgDto.toolName,
                })
                break

              case 'tool-response': {
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
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
            }
          } catch (e) {
            console.error('JSON parse error:', e)
          }
        },

        onclose() {
          // Extract resources from final accumulated assistant content
          if (lastAssistantContent) {
            extractResources(streamContent)
          }
          if (assistantMsgIndex !== -1) {
            updateMessage(assistantMsgIndex, { isLoading: false })
          }
        },

        onerror(err) {
          console.error('SSE error:', err)
          if (assistantMsgIndex !== -1) {
            updateMessage(assistantMsgIndex, { isLoading: false })
          }
          throw err
        },
      })
    } finally {
      isStreaming.value = false
      abortController = null
      // 请求没有被服务端接收时，附件还没有绑定到会话：这时丢弃它。
      // 服务端已接收的附件一律不删——流成功、异常、取消与断开都不删已绑定的图片。
      if (!serverAccepted && uploaded.length > 0) {
        for (const item of uploaded) {
          deleteAgentImage(item.resourceId).catch(() => {})
        }
      }
    }
  }

  const abort = () => {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isStreaming.value = false
  }

  onUnmounted(() => {
    abort()
  })

  return {
    messages,
    isStreaming,
    images,
    links,
    sendMessage,
    abort,
    pushMessage,
    reset,
  }
}
