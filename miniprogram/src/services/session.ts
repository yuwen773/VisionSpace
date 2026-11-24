/**
 * 小程序登录态存储。
 *
 * M1 的 `/user/wx/*` 签发 Sa-Token；小程序把令牌与脱敏用户信息放本地存储。
 * 请求走 `satoken` 头；私有图片用 `wx.downloadFile` 带同一个头下载到本地，
 * 令牌不进 URL（见 services/image-download.ts）。
 */
import { toStoredProfile, type ProfileLike, type StoredProfile } from '../logic/auth'

const TOKEN_STORAGE_KEY = 'vs_satoken'
const TOKEN_NAME_KEY = 'vs_token_name'
const USER_STORAGE_KEY = 'vs_user'

export function getToken(): string {
  try {
    const token = wx.getStorageSync(TOKEN_STORAGE_KEY)
    if (typeof token !== 'string' || token === '') {
      return ''
    }
    // 没有字段名就拼不出鉴权头，等同于未登录
    return getTokenName() === '' ? '' : token
  } catch (error) {
    console.warn('读取登录令牌失败', error)
    return ''
  }
}

/**
 * 令牌字段名由服务端下发（`tokenName`），客户端不写死。
 *
 * 服务端的头名是可配置的，写死 `satoken` 一旦和部署配置不一致，请求会带着
 * 服务端不认的头静默变成匿名请求。
 */
export function getTokenName(): string {
  try {
    const name = wx.getStorageSync(TOKEN_NAME_KEY)
    return typeof name === 'string' ? name : ''
  } catch (error) {
    console.warn('读取令牌字段名失败', error)
    return ''
  }
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
export function saveSession(token: string, tokenName: string, user: StoredProfile): void {
  if (token === '' || tokenName === '') {
    // 缺字段名的登录态发不出鉴权头，存下来只会让后续请求处处 401
    console.warn('登录响应缺少令牌或字段名，未保存登录态')
    return
  }
  wx.setStorageSync(TOKEN_STORAGE_KEY, token)
  wx.setStorageSync(TOKEN_NAME_KEY, tokenName)
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
  } catch (error) {
    console.warn('清除本地登录信息失败', error)
  }
}
