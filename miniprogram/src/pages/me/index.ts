import { APP_VERSION } from '../../config/env'
import { toStoredProfile } from '../../logic/auth'
import { isLoginExpired } from '../../logic/errors'
import { fetchLoginUser, logout } from '../../services/auth'
import { resolveContentUrl } from '../../services/content'
import { ApiError } from '../../services/http'
import { openLogin } from '../../services/navigation'
import { clearSession, getStoredUser, getToken, saveUser } from '../../services/session'

Page({
  data: {
    loggedIn: false,
    displayName: '',
    avatarUrl: '',
    initial: '',
    userId: '',
    version: APP_VERSION,
    busy: false,
  },

  onShow() {
    this.refresh()
  },

  async refresh(): Promise<void> {
    const token = getToken()
    const user = getStoredUser()
    if (token === '' || user === null) {
      this.setData({ loggedIn: false, displayName: '', avatarUrl: '', initial: '', userId: '' })
      return
    }
    this.applyUser(user.id, user.userName, user.userAvatar)
    try {
      const fresh = await fetchLoginUser()
      const profile = toStoredProfile(fresh)
      if (profile !== null) {
        saveUser(profile)
        this.applyUser(profile.id, profile.userName, profile.userAvatar)
      }
    } catch (error) {
      if (error instanceof ApiError && isLoginExpired(error.code)) {
        clearSession()
        this.setData({ loggedIn: false, displayName: '', avatarUrl: '', initial: '', userId: '' })
        return
      }
      // 网络类错误保留本地缓存展示
      console.warn('刷新用户信息失败', error)
    }
  },

  applyUser(id: string, userName: string, userAvatar: string): void {
    const name = userName || `用户 ${id}`
    this.setData({
      loggedIn: true,
      displayName: name,
      avatarUrl: resolveContentUrl(userAvatar),
      initial: name.slice(0, 1),
      userId: id,
    })
  },

  onLoginTap() {
    openLogin()
  },

  async onLogout() {
    if (this.data.busy) {
      return
    }
    const confirmed = await new Promise<boolean>((resolve) => {
      wx.showModal({
        title: '退出登录',
        content: '退出后需要重新微信登录才能查看空间。',
        confirmText: '退出',
        success(result) {
          resolve(result.confirm)
        },
        fail() {
          resolve(false)
        },
      })
    })
    if (!confirmed) {
      return
    }
    this.setData({ busy: true })
    try {
      await logout()
    } catch (error) {
      // 令牌已失效时后端返回“未登录”，本地照常清理即可
      console.warn('退出登录请求失败，继续清理本地状态', error)
    } finally {
      clearSession()
      this.setData({ busy: false, loggedIn: false, displayName: '', avatarUrl: '', initial: '', userId: '' })
      wx.showToast({ title: '已退出登录', icon: 'none' })
    }
  },
})
