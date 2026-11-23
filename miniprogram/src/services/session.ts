/**
 * 小程序登录态存储。
 *
 * M1 的 `/user/wx/login` 签发 Sa-Token，小程序把它放本地存储，
 * 请求时通过 `satoken` 头发送；`<image>` 无法带自定义头，因此图片 URL
 * 需要在查询参数里携带同一个令牌（见 services/content.ts）。
 */
const TOKEN_STORAGE_KEY = 'vs_satoken'

export function getToken(): string {
  try {
    const token = wx.getStorageSync(TOKEN_STORAGE_KEY)
    return typeof token === 'string' ? token : ''
  } catch (error) {
    console.warn('读取登录令牌失败', error)
    return ''
  }
}

export function setToken(token: string): void {
  if (typeof token !== 'string' || token === '') {
    return
  }
  wx.setStorageSync(TOKEN_STORAGE_KEY, token)
}

export function clearToken(): void {
  try {
    wx.removeStorageSync(TOKEN_STORAGE_KEY)
  } catch (error) {
    console.warn('清除登录令牌失败', error)
  }
}
