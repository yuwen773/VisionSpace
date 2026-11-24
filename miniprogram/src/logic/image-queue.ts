/**
 * 私有图片的鉴权下载队列（纯逻辑，不依赖 wx API，可单测）。
 *
 * 背景：空间图片的内容接口只认令牌，而 `<image>` 发不了自定义请求头，
 * 早期做法是把令牌塞进 URL 查询参数——令牌会进 nginx 日志、Referer 与图片缓存，
 * 属于不该外泄的凭据。改用 `wx.downloadFile` 带鉴权头下载后拿本地路径展示。
 *
 * 队列负责三件事：
 *   * 限流：私有缩略图可能有几十张，一次性全打出去既慢又容易被服务端限流；
 *   * 去重：同一张图在列表与详情里同时出现时只下载一次；
 *   * 缓存：下载完成的本地路径按 URL 记住，滚回列表时不必重新下载。
 */

export interface ImageQueueOptions {
  /** 同时下载的最大数量，默认 4。 */
  maxConcurrency?: number
  /** 实际下载动作，由调用方注入（小程序里是 wx.downloadFile）。 */
  fetch: (url: string) => Promise<string>
  /** 单张下载结束后的回调；失败时 path 为 null。 */
  onSettled?: (url: string, path: string | null) => void
}

interface WaitingTask {
  url: string
  resolve: (path: string) => void
  reject: (error: Error) => void
}

const DEFAULT_CONCURRENCY = 4

export class ImageQueue {
  private readonly fetch: (url: string) => Promise<string>

  private readonly maxConcurrency: number

  private readonly onSettled?: (url: string, path: string | null) => void

  /** 已下载完成的本地路径。 */
  private readonly resolved = new Map<string, string>()

  /** 正在下载中的任务，key 是 URL。 */
  private readonly inflight = new Map<string, Promise<string>>()

  /** 排队等待的任务。 */
  private readonly waiting: WaitingTask[] = []

  private active = 0

  constructor(options: ImageQueueOptions) {
    this.fetch = options.fetch
    this.maxConcurrency = Math.max(1, options.maxConcurrency ?? DEFAULT_CONCURRENCY)
    this.onSettled = options.onSettled
  }

  /** 已下载完成的本地路径；还没下载或下载失败都返回空串。 */
  cached(url: string): string {
    return this.resolved.get(url) ?? ''
  }

  /** 尚未完成的下载数量（含排队中的）。 */
  get pendingCount(): number {
    return this.inflight.size
  }

  /**
   * 请求下载一张图片。
   *
   * 同一个 URL 并发调用只会真正下载一次，后续调用共享同一个 Promise。
   */
  request(url: string): Promise<string> {
    if (url === '') {
      return Promise.reject(new Error('图片地址为空'))
    }
    const cached = this.resolved.get(url)
    if (cached !== undefined) {
      return Promise.resolve(cached)
    }
    const inflight = this.inflight.get(url)
    if (inflight !== undefined) {
      return inflight
    }
    const task = new Promise<string>((resolve, reject) => {
      this.waiting.push({ url, resolve, reject })
    })
    this.inflight.set(url, task)
    this.pump()
    return task
  }

  /** 批量排队；失败只记录，不向外抛，避免一张图失败拖垮整页。 */
  requestMany(urls: string[]): void {
    urls.forEach((url) => {
      this.request(url).catch(() => undefined)
    })
  }

  /** 清空缓存与排队状态；登出时调用，避免把上一个账号的私有图片留在内存里。 */
  clear(): void {
    this.resolved.clear()
    this.inflight.clear()
    this.waiting.length = 0
    this.active = 0
  }

  private pump(): void {
    while (this.active < this.maxConcurrency && this.waiting.length > 0) {
      const task = this.waiting.shift()
      if (task === undefined) {
        return
      }
      this.active += 1
      this.fetch(task.url)
        .then((path) => {
          this.resolved.set(task.url, path)
          this.onSettled?.(task.url, path)
          task.resolve(path)
        })
        .catch((error: unknown) => {
          this.onSettled?.(task.url, null)
          task.reject(error instanceof Error ? error : new Error(String(error)))
        })
        .finally(() => {
          this.inflight.delete(task.url)
          this.active -= 1
          this.pump()
        })
    }
  }
}
