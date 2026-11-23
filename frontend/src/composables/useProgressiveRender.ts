import { getCurrentInstance, onUnmounted, ref } from 'vue'

export interface ProgressiveRenderOptions {
  /** 把 Markdown 源串转成最终 HTML；`streaming` 为 true 时可以做降级渲染 */
  render: (content: string, streaming: boolean) => string | Promise<string>
  /** 两次重绘之间的最短间隔（毫秒），默认 16ms */
  minInterval?: number
  /** 自适应间隔的上限（毫秒），默认 1000ms */
  maxInterval?: number
}

/**
 * 流式内容的渐进式渲染。
 *
 * <p>思路与 DeepSeek chat-demo 的节流一致：按上一次渲染耗时估算下一次允许
 * 重绘的时刻，冷却期内到达的 token 合并成最新内容，只渲染一次；渲染耗时
 * 越长，后续间隔越大，避免「渲染比 token 到达还慢」时队列越积越长。
 *
 * <p>流结束后（`streaming=false`）取消排队的中间帧，立即渲染终稿。
 * 异步渲染带代次校验，过期结果不会覆盖新内容。
 */
export function useProgressiveRender(options: ProgressiveRenderOptions) {
  const html = ref('')
  const minInterval = options.minInterval ?? 16
  const maxInterval = options.maxInterval ?? 1000

  let timer: ReturnType<typeof setTimeout> | null = null
  let latest = ''
  let latestStreaming = false
  let lastStartedAt = 0
  let lastDuration = 0
  let epoch = 0
  let running = false
  let disposed = false

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const schedule = () => {
    if (disposed) return
    // 终稿立即渲染，并丢弃还在排队的中间帧
    if (!latestStreaming) {
      clearTimer()
      void run()
      return
    }
    if (timer) return
    const interval = Math.min(maxInterval, Math.max(minInterval, lastDuration * 2))
    const wait = Math.max(0, lastStartedAt + interval - Date.now())
    timer = setTimeout(() => {
      timer = null
      void run()
    }, wait)
  }

  const run = async () => {
    if (disposed || running) return
    running = true
    const content = latest
    const streaming = latestStreaming
    const myEpoch = ++epoch
    const startedAt = Date.now()
    lastStartedAt = startedAt
    try {
      const next = await options.render(content, streaming)
      if (disposed || myEpoch !== epoch) return
      html.value = next
      lastDuration = Date.now() - startedAt
    } catch (e) {
      // 单次渲染失败不应打断流式输出，下一帧会重试
      console.error('渲染消息失败:', e)
    } finally {
      running = false
      // 渲染期间又来了新内容：按最新状态再排一次
      if (!disposed && (latest !== content || latestStreaming !== streaming)) schedule()
    }
  }

  /** 内容或流式状态变化时调用。 */
  const update = (content: string, streaming: boolean) => {
    latest = content
    latestStreaming = streaming
    schedule()
  }

  /** 卸载前调用，让在途渲染的结果失效。 */
  const dispose = () => {
    disposed = true
    clearTimer()
    epoch++
  }

  // 在组件外调用（例如单元测试）时没有生命周期可挂
  if (getCurrentInstance()) onUnmounted(dispose)

  return { html, update, dispose }
}
