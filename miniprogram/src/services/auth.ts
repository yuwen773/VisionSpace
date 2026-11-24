import { toStoredProfile, type StoredProfile } from '../logic/auth'
import { request } from './http'
import { saveSession } from './session'
import type { LoginUserVO, WxLoginVO } from './types'

/**
 * 登录 / 绑定 / 登出。
 *
 * 微信 code 一次性有效：每次尝试都要重新取 code，不能缓存复用。
 */
export function fetchWxLoginCode(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    wx.login({
      success(result) {
        if (typeof result.code === 'string' && result.code !== '') {
          resolve(result.code)
        } else {
          reject(new Error('微信登录失败，请重试'))
        }
      },
      fail(error) {
        console.warn('wx.login 失败', error.errMsg)
        reject(new Error('微信登录失败，请重试'))
      },
    })
  })
}

export function loginWithWxCode(code: string): Promise<WxLoginVO> {
  return request<WxLoginVO>({ path: '/user/wx/login', method: 'POST', data: { code } })
}

export function bindWithCredentials(
  code: string,
  userAccount: string,
  userPassword: string,
): Promise<WxLoginVO> {
  return request<WxLoginVO>({
    path: '/user/wx/bind',
    method: 'POST',
    data: { code, userAccount: userAccount.trim(), userPassword },
  })
}

export function fetchLoginUser(): Promise<LoginUserVO> {
  return request<LoginUserVO>({ path: '/user/get/login', auth: true })
}

export function logout(): Promise<boolean> {
  return request<boolean>({ path: '/user/logout', method: 'POST', auth: true })
}

/** 登录/绑定成功后落盘，返回展示用的本地用户。 */
export function persistSession(result: WxLoginVO): StoredProfile | null {
  const profile = toStoredProfile(result.user)
  if (profile === null) {
    return null
  }
  saveSession(result.token, result.tokenName, profile)
  return profile
}
