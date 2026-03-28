import axios from 'axios'
import { warn } from 'vue'
const devUrl = 'http://localhost:8081' // 开发
const prodUrl = 'http://8.163.10.130' //config .env 生产：实际部署
//创建axios实例
const myAxios = axios.create({
  baseURL: devUrl,
  timeout: 5000,
  withCredentials: true,
})

// 添加请求拦截器
axios.interceptors.request.use(
  function (config) {
    // 在发送请求之前做些什么
    return config
  },
  function (error) {
    // 对请求错误做些什么
    return Promise.reject(error)
  },
)

// 添加响应拦截器
axios.interceptors.response.use(
  function (response) {
    // 2xx 范围内的状态码都会触发该函数。
    // 对响应数据做点什么
    const { data } = response
    if (data.code === 0 || data.code === 200) {
      return data
    }
    if (data.code === 404) {
      warn(data.message)
      return
    }
    return response
  },
  function (error) {
    // 超出 2xx 范围的状态码都会触发该函数。
    // 对响应错误做点什么
    return Promise.reject(error)
  },
)

export default myAxios
// request.ts module   xx         export
