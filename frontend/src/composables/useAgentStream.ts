import { ref, onUnmounted } from 'vue'

export interface StreamMessage {
  type: string      // AGENT_MODEL_STREAMING / AGENT_MODEL_FINISHED / AGENT_TOOL_FINISHED
  content: string   // 消息内容
  node: string      // 节点名称
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  let abortController: AbortController | null = null

  /**
   * 发送消息并接收 SSE 流
   * @param message 用户消息
   * @param threadId 线程 ID
   */
  const sendMessage = async (message: string, threadId: string) => {
    isStreaming.value = true
    messages.value = []

    // 构建 SSE URL
    const baseUrl = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'
    const url = `${baseUrl}/api/agent/image/chat/stream?message=${encodeURIComponent(message)}&threadId=${encodeURIComponent(threadId)}`

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'text/event-stream',
        },
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

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6))
              if (data.type && data.content !== undefined) {
                messages.value.push({
                  type: data.type,
                  content: data.content,
                  node: data.node || '',
                })
              }
            } catch (e) {
              // 忽略解析错误
            }
          }
        }
      }
    } catch (error) {
      console.error('SSE stream error:', error)
      throw error
    } finally {
      isStreaming.value = false
    }
  }

  /**
   * 中止当前流
   */
  const abort = () => {
    abortController?.abort()
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
