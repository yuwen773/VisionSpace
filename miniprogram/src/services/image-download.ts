import { API_BASE_URL } from '../config/env'
import { fetchLoginUser } from './auth'
import { isTrustedContentUrl } from '../logic/image-url'
import { ImageQueue } from '../logic/image-queue'
import { getStoredUser, getToken, getTokenName } from './session'

/**
 * 私有图片的鉴权下载。
 *
 * 空间图片的内容接口只认令牌，`<image>` 不能带请求头，所以先把文件下载到本地
 * （`wx.downloadFile` 支持请求头），再用本地路径渲染。公开图库不需要鉴权，
 * 直接用 URL 展示，不走这里。
 *
 * 令牌只出现在请求头里，不进 URL：查询参数里的令牌会被 nginx 访问日志、
 * Referer 与图片缓存记录下来，等于把凭据散出去。
 */

/** 下载失败时抛出的错误，携带 HTTP 状态码供页面判断是否需要重新登录。 */
export class ImageDownloadError extends Error {
  readonly statusCode: number

  constructor(statusCode: number, message: string) {
    super(message)
    this.name = 'ImageDownloadError'
    this.statusCode = statusCode
  }
}

/**
 * 单张图片的鉴权下载：请求头带令牌，成功返回本地临时路径。
 *
 * 只给本站内容地址发令牌：图片地址来自服务端返回的数据，一旦被改成外链，
 * 带着鉴权头去下载就等于把登录令牌送给第三方域名。
 */
export function fetchImageFile(url: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    if (!isTrustedContentUrl(url, API_BASE_URL)) {
      reject(new ImageDownloadError(0, '图片地址不在本站域名内'))
      return
    }
    const token = getToken()
    if (token === '') {
      reject(new ImageDownloadError(401, '未登录，无法下载私有图片'))
      return
    }
    wx.downloadFile({
      url,
      header: { [getTokenName()]: token },
      timeout: 30000,
      success: (response) => {
        if (response.statusCode === 200 && response.tempFilePath) {
          resolve(response.tempFilePath)
          return
        }
        reject(new ImageDownloadError(response.statusCode, `图片下载失败（${response.statusCode}）`))
      },
      fail: (error) => {
        reject(new ImageDownloadError(0, error.errMsg || '图片下载失败'))
      },
    })
  })
}

/**
 * 全局队列：限流、去重、缓存都在这里。
 *
 * 列表页与详情页共用一个队列，同一张图不会被重复下载。
 * 缓存带账号归属与 5 分钟有效期：换账号立即失效，撤权后最多 5 分钟就会重新鉴权；
 * 条数上限 60，避免微信临时文件配额（约 10MB）被无限增长的缓存吃满。
 */
export const imageQueue = new ImageQueue({
  maxConcurrency: 4,
  fetch: fetchImageFile,
  maxCached: 60,
  cacheTtlMs: 5 * 60 * 1000,
  owner: () => getStoredUser()?.id ?? '',
  // 登出/换账号/单条作废时把本地文件也删掉，只丢引用的话私有图片仍留在磁盘上。
  // 删不掉（文件已被微信回收）不影响后续流程。
  dispose: (path) => {
    try {
      wx.getFileSystemManager().unlink({ filePath: path, fail: () => undefined })
    } catch (error) {
      console.warn('删除本地图片失败', error)
    }
  },
})

/** 登出时清空缓存，避免上一个账号的私有图片路径留在内存里。 */
export function clearDownloadedImages(): void {
  imageQueue.clear()
}

/** 下载失败后的处理结论。 */
export interface ImageFailureAction {
  /** 登录态已失效：调用方应清掉列表并引导重新登录。 */
  needsLogin: boolean
}

/** 探活结果按「令牌 + 时间」记住，一页几十张图同时 403 时不会打几十次探活请求。 */
const PROBE_TTL_MS = 30 * 1000
let probe: { token: string; at: number; alive: boolean } | null = null

async function sessionAlive(): Promise<boolean> {
  const token = getToken()
  if (token === '') {
    return false
  }
  const now = Date.now()
  if (probe !== null && probe.token === token && now - probe.at < PROBE_TTL_MS) {
    return probe.alive
  }
  let alive: boolean
  try {
    await fetchLoginUser()
    alive = true
  } catch {
    alive = false
  }
  probe = { token, at: now, alive }
  return alive
}

/**
 * 私有图片下载失败后的统一处理。
 *
 * 内容接口只在「完全没带登录态」时回 401；令牌过期更常见的是 403——服务端按匿名
 * 身份判定「无权读取该图片」。两者在客户端看起来一样，处理却相反：一个要重新登录，
 * 一个只该让这张图下次重新鉴权。所以 403 且本地还有令牌时，用一次 `/user/get/login`
 * 探活把两者分开；否则令牌过期时整页只显示「图片暂不可用」，用户永远等不到登录引导。
 */
export async function resolveImageFailure(url: string, error: unknown): Promise<ImageFailureAction> {
  if (!(error instanceof ImageDownloadError)) {
    return { needsLogin: false }
  }
  if (error.statusCode === 401) {
    clearDownloadedImages()
    return { needsLogin: true }
  }
  if (error.statusCode !== 403) {
    return { needsLogin: false }
  }
  if (!(await sessionAlive())) {
    clearDownloadedImages()
    return { needsLogin: true }
  }
  // 登录态还在：只是这一张不该看得见（被移出成员、作品被隔离），作废它的缓存
  imageQueue.invalidate(url)
  return { needsLogin: false }
}
