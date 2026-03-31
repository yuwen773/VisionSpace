import { ref, onUnmounted } from 'vue'

export interface StreamMessage {
  type: string
  content: string
  node: string
}

export function useAgentStream() {
  const messages = ref<StreamMessage[]>([])
  const isStreaming = ref(false)
  let abortController: AbortController | null = null

  const sendMessage = async (message: string, threadId: string) => {
    isStreaming.value = true
    messages.value = []

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

        // 按行分割处理 SSE 事件
        const lines = buffer.split('\n')
        // 保留最后一行（可能不完整）
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed.startsWith('data:')) {
            continue
          }

          // 提取 JSON 部分（去掉 "data:" 前缀）
          let jsonStr = trimmed.slice(5).trimStart()

          // 解析 JSON
          try {
            const data = JSON.parse(jsonStr)
            if (data.type && data.content !== undefined) {
              // 过滤无效的 content
              if (data.content === null || data.content === undefined) {
                continue
              }
              messages.value.push({
                type: data.type,
                content: String(data.content),
                node: data.node || '',
              })
            }
          } catch (e) {
            // JSON 解析失败，跳过这一行
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
