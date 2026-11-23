/**
 * 账号密码校验（纯逻辑），规则与后端 `UserServiceImpl.authenticateForPassword`
 * 保持一致：账号至少 4 位、密码至少 8 位。前端先拦一道，避免无意义的请求。
 */
import { toNumberOrNull } from './long'

export interface CredentialCheck {
  valid: boolean
  message: string
}

export function validateCredentials(account: string, password: string): CredentialCheck {
  const userAccount = account.trim()
  if (userAccount === '' || password === '') {
    return { valid: false, message: '请输入账号和密码' }
  }
  if (userAccount.length < 4) {
    return { valid: false, message: '账号至少 4 位' }
  }
  if (password.length < 8) {
    return { valid: false, message: '密码至少 8 位' }
  }
  return { valid: true, message: '' }
}

/** 登录返回 40102 表示微信未绑定，这时才引导去绑定账号密码。 */
export const CODE_WX_NOT_BOUND = 40102

export function needsBind(code: number): boolean {
  return code === CODE_WX_NOT_BOUND
}

export interface ProfileLike {
  id?: number | string | null
  userName?: string | null
  userAvatar?: string | null
}

export interface StoredProfile {
  id: number
  userName: string
  userAvatar: string
}

/** 只把展示需要的三项落到本地，避免把账号/角色等写进缓存。 */
export function toStoredProfile(profile: ProfileLike): StoredProfile | null {
  const id = toNumberOrNull(profile.id)
  if (id === null || id <= 0) {
    return null
  }
  return {
    id,
    userName: typeof profile.userName === 'string' ? profile.userName : '',
    userAvatar: typeof profile.userAvatar === 'string' ? profile.userAvatar : '',
  }
}
