/**
 * 页面跳转的薄封装。登录入口在四个页面复用，集中一处避免文案/路径漂移。
 *
 * 登录页成功后自行决定返回：有上一页就 `navigateBack`，否则切到空间 tab。
 */
const LOGIN_PAGE = '/pages/login/index'

export function openLogin(): void {
  wx.navigateTo({ url: LOGIN_PAGE })
}
