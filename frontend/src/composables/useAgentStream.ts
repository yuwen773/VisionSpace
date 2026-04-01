import { ref, onUnmounted } from 'vue'

export interface AgentRunResponse {
  node: string
  agent?: string
  chunk?: string
  message?: MessageDTO
  tokenUsage?: any
  error?: boolean
  errorType?: string
  errorMessage?: string
}

export interface MessageDTO {
  messageType: 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool'
  content?: string
  toolCalls?: any[]
  toolName?: string
}

export interface StreamMessage {
  type: string
  content: string
  node: string
  isLoading?: boolean
  toolName?: string
}

/**
 * 等待浏览器实际绘制一帧，确保用户能看到更新
 */
const waitForPaint = (): Promise<void> =>
  new Promise(resolve => requestAnimationFrame(resolve))

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  let abortController: AbortController | null = null

  const sendMessage = async (message: string, threadId: string) => {
    isStreaming.value = true
    messages.value = []
    abortController = new AbortController()

    const baseUrl = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'
    const url = `${baseUrl}/api/agent/image/chat/stream`

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify({
          message: message,
          threadId: threadId,
        }),
        signal: abortController.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('ReadableStream not supported')
      }

      const decoder = new TextDecoder()
      let buffer = ''
      let streamContent = ''
      let isFirstChunk = true
      let lastRenderTime = 0
      const RENDER_INTERVAL = 30 // ms, 最小渲染间隔

      let _readCount = 0
      let _chunkCount = 0

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        _readCount++
        console.log(`[SSE] reader.read() #${_readCount}: ${value?.length ?? 0} bytes, chunks so far: ${_chunkCount}`)

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) {
            continue
          }

          let jsonStr = trimmed.slice(5).trimStart()
          if (!jsonStr) continue

          try {
            const data: AgentRunResponse = JSON.parse(jsonStr)

            // 跳过心跳
            if (data.node === 'heartbeat') continue

            // 处理错误
            if (data.error) {
              messages.value.push({
                type: 'error',
                content: data.errorMessage || 'Unknown error',
                node: 'error',
              })
              continue
            }

            // 处理文本 chunk
            if (data.chunk) {
              streamContent += data.chunk
              _chunkCount++

              if (isFirstChunk) {
                messages.value.push({
                  type: 'assistant',
                  content: streamContent,
                  node: data.node || 'agent',
                  isLoading: true,
                })
                isFirstChunk = false
              } else {
                // 直接修改属性，Vue 3 响应式代理自动追踪
                const lastMsg = messages.value[messages.value.length - 1]
                if (lastMsg && lastMsg.type === 'assistant') {
                  lastMsg.content = streamContent
                }
              }

              // 每个 chunk 都等一帧，让浏览器有机会绘制
              await waitForPaint()
            }

            // 处理完整消息
            if (data.message) {
              const msgType = data.message.messageType
              if (msgType === 'tool-request') {
                messages.value.push({
                  type: 'tool-request',
                  content: data.message.content || '',
                  node: data.node || 'agent',
                  toolName: data.message.toolName,
                })
              } else if (msgType === 'tool-confirm') {
                messages.value.push({
                  type: 'tool-confirm',
                  content: data.message.content || '',
                  node: data.node || 'agent',
                })
              } else if (msgType === 'tool') {
                messages.value.push({
                  type: 'tool-response',
                  content: data.message.content || '',
                  node: data.node || 'agent',
                  toolName: data.message.toolName,
                })
              }
              // 重置 chunk 累积
              isFirstChunk = true
              streamContent = ''
            }
          } catch (e) {
            // JSON 解析失败，跳过
          }
        }
      }

      // 标记最后一条消息为完成
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.type === 'assistant') {
        lastMsg.isLoading = false
      }
    } catch (error: any) {
      if (error.name === 'AbortError') {
        console.log('Request was aborted')
      } else {
        console.error('SSE stream error:', error)
        throw error
      }
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
    sendMessage,
    abort,
  }
}
