import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { warn } from 'vue'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'

//创建axios实例
const myAxios = axios.create({
  baseURL,
  timeout: 5000,
  withCredentials: true,
})

// 添加请求拦截器
myAxios.interceptors.request.use(
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
myAxios.interceptors.response.use(
  function (response) {
    // 2xx 范围内的状态码都会触发该函数。
    // 对响应数据做点什么
    const { data } = response
    if (data.code === 0 || data.code === 200) {
      return response
    }
    if (data.code === 404) {
      warn(data.message)
    }
    // 必须始终返回 response：调用方普遍直接读 res.data.code，
    // 返回 undefined 会在下游抛出 TypeError。
    return response
  },
  function (error) {
    // 超出 2xx 范围的状态码都会触发该函数。
    // 对响应错误做些什么
    return Promise.reject(error)
  },
)

/**
 * 生成器（@umijs/openapi）约定的请求函数签名：`request<T>(url, config) => Promise<AxiosResponse<T>>`。
 *
 * 生成产物里还会带一个 `requestType: 'form'` 字段（umi-request 的约定），axios 不认识它，
 * 这里在类型上放行、在运行时丢弃——文件上传的实际数据由生成代码自己组装成 FormData。
 */
export interface GeneratedRequestConfig extends AxiosRequestConfig {
  requestType?: 'form' | 'json'
}

function request<T = any>(
  url: string,
  config?: GeneratedRequestConfig,
): Promise<AxiosResponse<T>> {
  const { requestType, ...axiosConfig } = config ?? {}
  void requestType
  return myAxios.request<T>({ url, ...axiosConfig })
}

export default request
