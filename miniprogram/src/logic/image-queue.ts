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
  /** 本地路径缓存条数上限，默认 60；超出按写入顺序淘汰最早的。 */
  maxCached?: number
  /** 缓存有效期（毫秒），默认 5 分钟；过期后重新下载，撤权不会一直用旧文件。 */
  cacheTtlMs?: number
  /** 当前账号标识；与下载时记录的不一致，缓存即视为失效（账号隔离）。 */
  owner?: () => string
  /** 当前时间来源，测试可注入。 */
  now?: () => number
  /**
   * 删除一条不再需要的本地文件（小程序里是 `wx.getFileSystemManager().unlink`）。
   *
   * 只在「确定不再使用」时调用（登出、换账号、单条作废）。按容量淘汰与 TTL 过期
   * 不删文件：卡片里可能还渲染着这个路径，删掉会变成破图；微信自己会回收临时文件。
   */
  dispose?: (path: string) => void
}

interface WaitingTask {
  url: string
  resolve: (path: string) => void
  reject: (error: Error) => void
}

/** 一条已下载记录：谁下的、什么时候下的、落在哪个本地文件。 */
interface CacheEntry {
  path: string
  owner: string
  at: number
}

const DEFAULT_CONCURRENCY = 4
const DEFAULT_MAX_CACHED = 60
const DEFAULT_TTL_MS = 5 * 60 * 1000

export class ImageQueue {
  private readonly fetch: (url: string) => Promise<string>

  private readonly maxConcurrency: number

  private readonly onSettled?: (url: string, path: string | null) => void

  private readonly maxCached: number

  private readonly cacheTtlMs: number

  private readonly owner: () => string

  private readonly now: () => number

  private readonly dispose: (path: string) => void

  /** 已下载完成的本地路径，带归属与时间。 */
  private readonly resolved = new Map<string, CacheEntry>()

  /** 正在下载中的任务，key 是 URL。 */
  private readonly inflight = new Map<string, Promise<string>>()

  /** 排队等待的任务。 */
  private readonly waiting: WaitingTask[] = []

  /** 正在下载中的任务，key 是 URL；清空时用来把它们的 Promise 一起结算掉。 */
  private readonly running = new Map<string, WaitingTask>()

  private active = 0

  /**
   * 清空的代数。
   *
   * 清空时还在飞的下载没法真正取消（wx.downloadFile 没有取消句柄），
   * 它们落地后会晚到一步：不带代数判断就会把上一个账号的本地路径写回缓存。
   */
  private generation = 0

  constructor(options: ImageQueueOptions) {
    this.fetch = options.fetch
    this.maxConcurrency = Math.max(1, options.maxConcurrency ?? DEFAULT_CONCURRENCY)
    this.onSettled = options.onSettled
    this.maxCached = Math.max(1, options.maxCached ?? DEFAULT_MAX_CACHED)
    this.cacheTtlMs = Math.max(0, options.cacheTtlMs ?? DEFAULT_TTL_MS)
    this.owner = options.owner ?? (() => '')
    this.now = options.now ?? (() => Date.now())
    this.dispose = options.dispose ?? (() => undefined)
  }

  /**
   * 已下载完成的本地路径；还没下载、下载失败、已过期或换了账号都返回空串。
   *
   * 过期与换号都在这里顺手删掉记录：调用方拿到空串就会重新下载，
   * 不会因为缓存一直命中而看不到撤权后的结果。
   */
  cached(url: string): string {
    const entry = this.resolved.get(url)
    if (entry === undefined) {
      return ''
    }
    if (entry.owner !== this.owner() || this.now() - entry.at > this.cacheTtlMs) {
      this.resolved.delete(url)
      return ''
    }
    return entry.path
  }

  /** 撤掉一条缓存；下载返回 401/403 时调用，下一次请求会重新鉴权。 */
  invalidate(url: string): void {
    const entry = this.resolved.get(url)
    this.resolved.delete(url)
    if (entry !== undefined) {
      this.dispose(entry.path)
    }
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
    const cached = this.cached(url)
    if (cached !== '') {
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

  /**
   * 清空缓存与排队状态；登出时调用，避免把上一个账号的私有图片留在内存里。
   *
   * 排队中与下载中的任务都会被拒绝，调用方不会拿到永远不落地的 Promise；
   * 已发出的下载仍会跑完，但结果会被代数判断丢掉、文件当场删除。
   * 已下载的文件同时从本地删除：只丢引用的话，上一个账号的私有图片还留在磁盘上。
   */
  clear(): void {
    this.generation += 1
    const cancelled = [...this.running.values(), ...this.waiting]
    this.running.clear()
    this.waiting.length = 0
    this.resolved.forEach((entry) => this.dispose(entry.path))
    this.resolved.clear()
    this.inflight.clear()
    this.active = 0
    cancelled.forEach((task) => task.reject(new Error('图片下载已取消')))
  }

  /**
   * 写入缓存并维持容量上限。
   *
   * 微信的临时文件总量有限（约 10MB），缓存无上限增长最终会让新图下载失败；
   * Map 保持插入顺序，超出上限就淘汰最早写入的那条。
   */
  private remember(url: string, path: string): void {
    this.resolved.delete(url)
    this.resolved.set(url, { path, owner: this.owner(), at: this.now() })
    while (this.resolved.size > this.maxCached) {
      const oldest = this.resolved.keys().next()
      if (oldest.done === true) {
        break
      }
      this.resolved.delete(oldest.value)
    }
  }

  private pump(): void {
    while (this.active < this.maxConcurrency && this.waiting.length > 0) {
      const task = this.waiting.shift()
      if (task === undefined) {
        return
      }
      const generation = this.generation
      this.active += 1
      this.running.set(task.url, task)
      this.fetch(task.url)
        .then(
          (path) => {
            if (generation === this.generation) {
              this.remember(task.url, path)
              this.onSettled?.(task.url, path)
            } else {
              // 这一代已被清空：结果没人要了，文件也当场删掉，不留在磁盘上
              this.dispose(path)
            }
            // 先摘掉 inflight 再兑现：调用方 await 回来后立刻重试同一 URL 时，
            // 不能拿到上一次已经完成的 Promise
            this.forget(task.url, generation)
            task.resolve(path)
          },
          (error: unknown) => {
            if (generation === this.generation) {
              this.onSettled?.(task.url, null)
            }
            this.forget(task.url, generation)
            task.reject(error instanceof Error ? error : new Error(String(error)))
          },
        )
        .finally(() => {
          // 清空之后 running/inflight/active 都已经重置，这一代的结果不能再改它们
          if (generation !== this.generation) {
            return
          }
          this.active -= 1
          this.pump()
        })
    }
  }

  /** 下载结束后移除进行中记录；已清空的一代不再改动任何状态。 */
  private forget(url: string, generation: number): void {
    if (generation !== this.generation) {
      return
    }
    this.running.delete(url)
    this.inflight.delete(url)
  }
}
