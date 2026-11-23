/**
 * 后端 `BaseResponse.code` 到用户可读文案的映射（纯逻辑）。
 * 只收录小程序会碰到的码；未知码优先用服务端 message，其次用兜底文案。
 */
export const NETWORK_ERROR_CODE = -1
export const CODE_NOT_LOGIN = 40100
export const CODE_NO_AUTH = 40101

/** 令牌缺失或过期；后端已据此清理本地令牌。 */
export function isLoginExpired(code: number): boolean {
  return code === CODE_NOT_LOGIN
}

/** 已登录但没有该资源权限。 */
export function isPermissionDenied(code: number): boolean {
  return code === CODE_NO_AUTH
}

export interface ClientError {
  code: number
  message: string
  retryable: boolean
  loginRequired: boolean
}

const FALLBACK_MESSAGE = '网络开小差了，请稍后再试'

export function describeError(code: number, message?: string | null): ClientError {
  const serverMessage = typeof message === 'string' ? message.trim() : ''
  switch (code) {
    case 0:
      return { code, message: '', retryable: false, loginRequired: false }
    case NETWORK_ERROR_CODE:
      return { code, message: '连接失败，请检查网络后重试', retryable: true, loginRequired: false }
    case 40000:
      return { code, message: serverMessage || '请求参数有误', retryable: false, loginRequired: false }
    case 40100:
      return { code, message: '登录已失效，请重新登录', retryable: false, loginRequired: true }
    case 40101:
      return { code, message: '没有访问权限', retryable: false, loginRequired: false }
    case 40102:
      return { code, message: '该微信尚未绑定账号', retryable: false, loginRequired: true }
    case 40103:
      return { code, message: '该微信已绑定其他账号', retryable: false, loginRequired: true }
    case 40300:
      return { code, message: '没有访问权限', retryable: false, loginRequired: false }
    case 40400:
      return { code, message: '内容不存在或已下线', retryable: false, loginRequired: false }
    case 50000:
      return { code, message: '服务暂时不可用，请稍后再试', retryable: true, loginRequired: false }
    case 50001:
      // OPERATION_ERROR 被多处以 50001 返回（建空间重复、上传超配额等），
      // 具体原因以服务端文案为准，不能在这里写死成上传额度
      return { code, message: serverMessage || '操作失败，请稍后再试', retryable: false, loginRequired: false }
    default:
      return { code, message: serverMessage || FALLBACK_MESSAGE, retryable: true, loginRequired: false }
  }
}

/**
 * 把任意异常转成用户可读文案。
 *
 * ApiError 的 message 已经是 describeError 的结果；其它异常（含非 Error）
 * 统一退回服务不可用的兜底文案，不把原始英文错误抛给用户。
 */
export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim() !== '') {
    return error.message
  }
  return describeError(50000).message
}
