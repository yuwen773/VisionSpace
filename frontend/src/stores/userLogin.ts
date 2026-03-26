import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

export const useLoginUserStore = defineStore('userLogin', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  //向服务器获取用户信息
  async function fetchUserLogin() {
    const res = await getLoginUserUsingGet()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
    }
  }

  //设置用户登录
  function setUserLogin(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  return { loginUser, fetchUserLogin, setUserLogin }
})
