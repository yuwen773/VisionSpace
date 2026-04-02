import { ref, onUnmounted } from 'vue'
import { fetchEventSource } from '@microsoft/fetch-event-source'

export interface MessageDTO {
  messageType: 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool' | 'reasoning'
  content?: string
  toolCalls?: any[]
  toolName?: string
}

export interface ToolCallArg {
  name: string
  args: Record<string, any>
}

export interface StreamMessage {
  type: string
  content: string
  node: string
  isLoading?: boolean
  toolName?: string
  toolCalls?: ToolCallArg[]
  time?: string
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  const images = ref<{ url: string; title?: string }[]>([])
  const links = ref<{ url: string; title: string; snippet: string; domain: string }[]>([])
  let abortController: AbortController | null = null
  let seenImageUrls = new Set<string>()
  let seenLinkUrls = new Set<string>()

  const extractResources = (content: string) => {
    if (!content) return

    for (const match of content.matchAll(/!\[([^\]]*)\]\(([^)]+)\)/g)) {
      const url = match[2]
      if (!seenImageUrls.has(url)) {
        images.value.push({ url, title: match[1] || '图片' })
        seenImageUrls.add(url)
      }
    }

    // Negative lookahead (?!!) excludes image syntax ![alt](url)
    for (const match of content.matchAll(/(?!!)\[([^\]]+)\]\(([^)]+)\)/g)) {
      const url = match[2]
      if (!seenLinkUrls.has(url)) {
        try {
          links.value.push({ url, title: match[1], snippet: match[1], domain: new URL(url).hostname })
          seenLinkUrls.add(url)
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
    seenImageUrls = new Set<string>()
    seenLinkUrls = new Set<string>()
  }

  const sendMessage = async (message: string, threadId: string, files?: File[]) => {
    isStreaming.value = true
    reset()
    abortController = new AbortController()

    const baseUrl = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'
    const url = `${baseUrl}/api/agent/image/chat/stream`

    let assistantMsgIndex = -1
    let reasoningMsgIndex = -1
    let streamContent = ''
    let lastAssistantContent = ''

    // Build body: FormData when files present, JSON otherwise
    const headers: Record<string, string> = {}
    let body: BodyInit

    if (files && files.length > 0) {
      const formData = new FormData()
      formData.append('message', message)
      formData.append('threadId', threadId)
      files.forEach((file) => {
        formData.append('files', file)
      })
      body = formData
      // Don't set Content-Type — browser sets multipart boundary automatically
    } else {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify({ message, threadId })
    }

    try {
      await fetchEventSource(url, {
        method: 'POST',
        headers,
        body,
        signal: abortController.signal,
        credentials: 'include',
        openWhenHidden: true,

        async onopen(response) {
          if (response.ok) return
          throw new Error('Failed to open connection')
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
                  pushMessage({ type: 'assistant', content: streamContent, node: 'agent', isLoading: true })
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
                  pushMessage({ type: 'tool-request', content: '', node: 'agent', toolName: name, toolCalls: [{ name, args: parsedArgs }] })
                }
                break
              }

              case 'tool-confirm':
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
                streamContent = ''
                pushMessage({ type: 'tool-confirm', content: msgDto.content || '', node: 'agent' })
                break

              case 'tool': {
                assistantMsgIndex = -1
                reasoningMsgIndex = -1
                streamContent = ''
                const toolContent = msgDto.content || ''
                pushMessage({ type: 'tool-response', content: toolContent, node: 'agent', toolName: msgDto.toolName })
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
