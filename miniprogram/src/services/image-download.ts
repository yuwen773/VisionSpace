import { ImageQueue } from '../logic/image-queue'
import { getToken, getTokenName } from './session'

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

/** 单张图片的鉴权下载：请求头带令牌，成功返回本地临时路径。 */
export function fetchImageFile(url: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
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
 */
export const imageQueue = new ImageQueue({
  maxConcurrency: 4,
  fetch: fetchImageFile,
})

/** 登出时清空缓存，避免上一个账号的私有图片路径留在内存里。 */
export function clearDownloadedImages(): void {
  imageQueue.clear()
}
