import { API_BASE_URL, API_PREFIX } from '../config/env'
import { describeError, NETWORK_ERROR_CODE } from '../logic/errors'
import { getToken, clearToken } from './session'

export const TOKEN_HEADER = 'satoken'
const REQUEST_TIMEOUT_MS = 15000

type RequestMethod = 'GET' | 'POST'

export interface RequestOptions {
  /** 以 / 开头的后端路径，例如 /picture/tag_category */
  path: string
  method?: RequestMethod
  /** GET 会序列化成查询串，POST 作为 JSON 请求体 */
  data?: Record<string, unknown>
  /** 是否携带令牌；公开接口保持 false，避免无谓的凭据外泄 */
  auth?: boolean
  timeout?: number
}

export class ApiError extends Error {
  readonly code: number
  readonly retryable: boolean
  readonly loginRequired: boolean

  constructor(code: number, message: string, retryable: boolean, loginRequired: boolean) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.retryable = retryable
    this.loginRequired = loginRequired
  }
}

interface BaseResponse<T> {
  code: number
  data: T
  message?: string
}

/**
 * 统一请求入口：拆 BaseResponse、把非 0 code 转成 ApiError。
 * 业务层只需要 try/catch ApiError，不必再关心 HTTP 状态码。
 */
export function request<T>(options: RequestOptions): Promise<T> {
  const auth = options.auth === true
  return new Promise<T>((resolve, reject) => {
    wx.request({
      url: `${API_BASE_URL}${API_PREFIX}${options.path}`,
      method: options.method ?? 'GET',
      data: options.data,
      timeout: options.timeout ?? REQUEST_TIMEOUT_MS,
      header: buildHeader(auth),
      success(response) {
        const body = response.data as unknown
        if (!isBaseResponse<T>(body)) {
          reject(toApiError(50000, '响应格式异常'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        if (body.code === 40100 && auth) {
          clearToken()
        }
        reject(toApiError(body.code, body.message))
      },
      fail(error) {
        console.warn('请求失败', options.path, error.errMsg)
        reject(toApiError(NETWORK_ERROR_CODE))
      },
    })
  })
}

function buildHeader(auth: boolean): Record<string, string> {
  const header: Record<string, string> = { 'content-type': 'application/json' }
  if (auth) {
    const token = getToken()
    if (token !== '') {
      header[TOKEN_HEADER] = token
    }
  }
  return header
}

function isBaseResponse<T>(body: unknown): body is BaseResponse<T> {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as { code?: unknown }).code === 'number'
  )
}

function toApiError(code: number, serverMessage?: string): ApiError {
  const described = describeError(code, serverMessage)
  return new ApiError(described.code, described.message, described.retryable, described.loginRequired)
}
