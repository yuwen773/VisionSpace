/**
 * 小程序登录态存储。
 *
 * M1 的 `/user/wx/*` 签发 Sa-Token；小程序把令牌与脱敏用户信息放本地存储。
 * 请求走 `satoken` 头；`<image>` 无法带自定义头，空间图片的 URL 需要在查询参数
 * 里携带同一个令牌（见 services/content.ts）。
 */
import { toStoredProfile, type ProfileLike, type StoredProfile } from '../logic/auth'

const TOKEN_STORAGE_KEY = 'vs_satoken'
const TOKEN_NAME_KEY = 'vs_token_name'
const TOKEN_QUERY_NAME_KEY = 'vs_token_query_name'
const USER_STORAGE_KEY = 'vs_user'
const DEFAULT_TOKEN_NAME = 'satoken'

export function getToken(): string {
  try {
    const token = wx.getStorageSync(TOKEN_STORAGE_KEY)
    return typeof token === 'string' ? token : ''
  } catch (error) {
    console.warn('读取登录令牌失败', error)
    return ''
  }
}

/** 令牌字段名由服务端下发（`tokenName`），默认 satoken。 */
export function getTokenName(): string {
  try {
    const name = wx.getStorageSync(TOKEN_NAME_KEY)
    return typeof name === 'string' && name !== '' ? name : DEFAULT_TOKEN_NAME
  } catch (error) {
    return DEFAULT_TOKEN_NAME
  }
}

/** `<image>` 用的查询参数名；服务端未下发时与请求头名保持一致。 */
export function getTokenQueryName(): string {
  try {
    const name = wx.getStorageSync(TOKEN_QUERY_NAME_KEY)
    if (typeof name === 'string' && name !== '') {
      return name
    }
  } catch (error) {
    console.warn('读取令牌查询参数名失败', error)
  }
  return getTokenName()
}

export function getStoredUser(): StoredProfile | null {
  try {
    const value = wx.getStorageSync(USER_STORAGE_KEY) as ProfileLike | ''
    if (typeof value !== 'object' || value === null) {
      return null
    }
    // 缓存可能来自旧版本或被手动改过，统一走一遍校验
    return toStoredProfile(value)
  } catch (error) {
    console.warn('读取本地用户失败', error)
    return null
  }
}

/** 登录/绑定成功后一次性写入令牌、令牌名与脱敏用户。 */
export function saveSession(
  token: string,
  tokenName: string,
  user: StoredProfile,
  tokenQueryName = '',
): void {
  if (token === '') {
    return
  }
  wx.setStorageSync(TOKEN_STORAGE_KEY, token)
  wx.setStorageSync(TOKEN_NAME_KEY, tokenName || DEFAULT_TOKEN_NAME)
  wx.setStorageSync(TOKEN_QUERY_NAME_KEY, tokenQueryName || tokenName || DEFAULT_TOKEN_NAME)
  wx.setStorageSync(USER_STORAGE_KEY, user)
}

/** 刷新本地用户信息（令牌不变）。 */
export function saveUser(user: StoredProfile): void {
  wx.setStorageSync(USER_STORAGE_KEY, user)
}

export function clearToken(): void {
  try {
    wx.removeStorageSync(TOKEN_STORAGE_KEY)
  } catch (error) {
    console.warn('清除登录令牌失败', error)
  }
}

export function clearSession(): void {
  clearToken()
  try {
    wx.removeStorageSync(USER_STORAGE_KEY)
    wx.removeStorageSync(TOKEN_NAME_KEY)
    wx.removeStorageSync(TOKEN_QUERY_NAME_KEY)
  } catch (error) {
    console.warn('清除本地登录信息失败', error)
  }
}
