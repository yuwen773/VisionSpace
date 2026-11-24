import { needsBind, validateCredentials } from '../../logic/auth'
import { errorMessage } from '../../logic/errors'
import {
  bindWithCredentials,
  fetchWxLoginCode,
  loginWithWxCode,
  persistSession,
} from '../../services/auth'
import { ApiError } from '../../services/http'
import type { WxLoginVO } from '../../services/types'

type Stage = 'auto' | 'bind' | 'error'

Page({
  data: {
    stage: 'auto' as Stage,
    loading: true,
    submitting: false,
    account: '',
    password: '',
    error: '',
  },

  onLoad() {
    this.autoLogin()
  },

  /**
   * 静默登录：微信 code 换 openid；未绑定（40102）才展示账号密码绑定表单。
   * 其余错误留在本页，允许重试，不把用户弹回上一个页面。
   */
  async autoLogin(): Promise<void> {
    this.setData({ stage: 'auto', loading: true, error: '' })
    try {
      const code = await fetchWxLoginCode()
      const result = await loginWithWxCode(code)
      this.finish(result)
    } catch (error) {
      if (error instanceof ApiError && needsBind(error.code)) {
        this.setData({ stage: 'bind', loading: false })
        return
      }
      console.warn('微信登录失败', error)
      this.setData({ stage: 'error', loading: false, error: errorMessage(error) })
    }
  },

  async onBindSubmit(): Promise<void> {
    if (this.data.submitting) {
      return
    }
    const { account, password } = this.data
    const check = validateCredentials(account, password)
    if (!check.valid) {
      this.setData({ error: check.message })
      return
    }
    this.setData({ submitting: true, error: '' })
    try {
      // code 一次性：绑定必须重新取一个
      const code = await fetchWxLoginCode()
      const result = await bindWithCredentials(code, account, password)
      this.finish(result)
    } catch (error) {
      console.warn('绑定账号失败', error)
      this.setData({ error: errorMessage(error) })
    } finally {
      this.setData({ submitting: false })
    }
  },

  finish(result: WxLoginVO): void {
    if (persistSession(result) === null) {
      this.setData({ stage: 'error', loading: false, error: '登录信息异常，请重试' })
      return
    }
    this.setData({ loading: false })
    wx.showToast({ title: '已登录', icon: 'success', duration: 800 })
    setTimeout(() => this.goBack(), 800)
  },

  goBack(): void {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/space/index' })
  },

  onAccountInput(event: WechatMiniprogram.Input) {
    this.setData({ account: event.detail.value })
  },

  onPasswordInput(event: WechatMiniprogram.Input) {
    const value = event.detail.value
    this.setData({ password: value })
  },

  onRetry() {
    this.autoLogin()
  },
})
