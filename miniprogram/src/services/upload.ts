/**
 * 图片上传（multipart）。`wx.uploadFile` 不走 `request`，但响应仍是
 * `BaseResponse`，这里复用同一套错误映射，页面只处理 ApiError。
 *
 * 后端字段见 `PictureController#uploadPicture`：part 名固定 `file`，
 * `spaceId` / `picName` 作为普通表单字段。
 */
import { API_BASE_URL, API_PREFIX } from '../config/env'
import { isLoginExpired, NETWORK_ERROR_CODE } from '../logic/errors'
import type { PictureVO } from './types'
import { clearToken, getToken, getTokenName } from './session'
import { ApiError, isBaseResponse, toApiError } from './http'

const UPLOAD_TIMEOUT_MS = 60000

/** 队列串行上传，单张的整体超时；压缩耗时不计入。 */
export interface UploadPictureParams {
  filePath: string
  /** 19 位雪花空间 id，按字符串传给后端，避免精度丢失。 */
  spaceId: string
  picName: string
  onProgress?: (percent: number) => void
}

export function uploadPictureFile(params: UploadPictureParams): Promise<PictureVO> {
  const token = getToken()
  const header: Record<string, string> = {}
  if (token !== '') {
    header[getTokenName()] = token
  }
  return new Promise<PictureVO>((resolve, reject) => {
    const task = wx.uploadFile({
      url: `${API_BASE_URL}${API_PREFIX}/picture/upload`,
      filePath: params.filePath,
      name: 'file',
      formData: {
        spaceId: params.spaceId,
        picName: params.picName,
      },
      header,
      timeout: UPLOAD_TIMEOUT_MS,
      success(response) {
        const parsed = parseResponse(response.statusCode, response.data)
        if (parsed instanceof ApiError) {
          if (isLoginExpired(parsed.code)) {
            clearToken()
          }
          reject(parsed)
          return
        }
        resolve(parsed)
      },
      fail(error) {
        console.warn('上传请求失败', error.errMsg)
        reject(toApiError(NETWORK_ERROR_CODE))
      },
    })
    if (typeof params.onProgress === 'function') {
      task.onProgressUpdate((event) => {
        params.onProgress?.(event.progress)
      })
    }
  })
}

/** 只把「确定的业务错误」当 ApiError 返回，成功时直接给 data。 */
function parseResponse(statusCode: number, raw: string): PictureVO | ApiError {
  if (statusCode < 200 || statusCode >= 300) {
    return toApiError(50000, '上传服务暂时不可用')
  }
  let body: unknown
  try {
    body = JSON.parse(raw)
  } catch (error) {
    console.warn('上传响应不是合法 JSON', error)
    return toApiError(50000, '响应格式异常')
  }
  if (!isBaseResponse<PictureVO>(body)) {
    return toApiError(50000, '响应格式异常')
  }
  if (body.code !== 0) {
    return toApiError(body.code, body.message)
  }
  return body.data
}
