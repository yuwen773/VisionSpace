import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

export const useLoginUserStore = defineStore('userLogin', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  //向服务器获取用户信息
  // 返回请求是否完成（不代表已登录）：后端不可用时返回 false 并保持匿名态，
  // 调用方（尤其是路由守卫）不能因为一次网络失败把整个首屏卡死。
  async function fetchUserLogin(): Promise<boolean> {
    try {
      const res = await getLoginUserUsingGet()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
      }
      return true
    } catch {
      return false
    }
  }

  //设置用户登录
  function setUserLogin(newLoginUser: API.LoginUserVO) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchUserLogin, setUserLogin }
})
