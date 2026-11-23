/**
 * 小程序登录态存储。
 *
 * M1 的 `/user/wx/*` 签发 Sa-Token；小程序把令牌与脱敏用户信息放本地存储。
 * 请求走 `satoken` 头；`<image>` 无法带自定义头，空间图片的 URL 需要在查询参数
 * 里携带同一个令牌（见 services/content.ts）。
 */
const TOKEN_STORAGE_KEY = 'vs_satoken'
const TOKEN_NAME_KEY = 'vs_token_name'
const USER_STORAGE_KEY = 'vs_user'
const DEFAULT_TOKEN_NAME = 'satoken'

export interface StoredUser {
  id: number
  userName: string
  userAvatar: string
}

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

export function getStoredUser(): StoredUser | null {
  try {
    const value = wx.getStorageSync(USER_STORAGE_KEY) as Partial<StoredUser> | ''
    if (typeof value !== 'object' || value === null) {
      return null
    }
    const id = Number(value.id)
    if (!Number.isFinite(id) || id <= 0) {
      return null
    }
    return {
      id,
      userName: typeof value.userName === 'string' ? value.userName : '',
      userAvatar: typeof value.userAvatar === 'string' ? value.userAvatar : '',
    }
  } catch (error) {
    console.warn('读取本地用户失败', error)
    return null
  }
}

/** 登录/绑定成功后一次性写入令牌、令牌名与脱敏用户。 */
export function saveSession(token: string, tokenName: string, user: StoredUser): void {
  if (token === '') {
    return
  }
  wx.setStorageSync(TOKEN_STORAGE_KEY, token)
  wx.setStorageSync(TOKEN_NAME_KEY, tokenName || DEFAULT_TOKEN_NAME)
  wx.setStorageSync(USER_STORAGE_KEY, user)
}

/** 刷新本地用户信息（令牌不变）。 */
export function saveUser(user: StoredUser): void {
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
