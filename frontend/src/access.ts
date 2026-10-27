import router from '@/router'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import { message } from 'ant-design-vue'

let firstFetchLoginUser: boolean = true

// 需要登录才能访问的路由
const loginRequiredPaths = ['/picture/']

// 需要特定角色才能访问的路由前缀。角色互不继承：
// 处置角色不是平台管理员，平台管理员也不因管理身份获得私人资源的处置权。
const roleRequiredPaths: { prefix: string; role: string }[] = [
  { prefix: '/admin', role: 'admin' },
  { prefix: '/moderation', role: 'moderator' },
]

router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新时,首次加载时,能等待终端返回用户信息后再校验
  if (firstFetchLoginUser) {
    await loginUserStore.fetchUserLogin()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  const toUrl = to.fullPath

  // 按角色隔离的路由鉴权
  const roleRequired = roleRequiredPaths.find((item) => toUrl.startsWith(item.prefix))
  if (roleRequired) {
    if (!loginUser || loginUser.userRole !== roleRequired.role) {
      message.error('对不起,您没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }

  // 登录后才能访问的路由（如图片详情）
  const needLogin = loginRequiredPaths.some((path) => toUrl.startsWith(path))
  if (needLogin && (!loginUser || !loginUser.id)) {
    message.error('请先登录后再访问')
    next(`/user/login?redirect=${to.fullPath}`)
    return
  }

  next()
})
