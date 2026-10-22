import { describe, expect, it } from 'vitest'
import { AxiosError, AxiosHeaders, type AxiosAdapter } from 'axios'
import request from '@/request'

describe('shared request instance', () => {
  it('preserves credentials, JSON binding and the response for business errors', async () => {
    const adapter: AxiosAdapter = async (config) => {
      expect(config.withCredentials).toBe(true)
      expect(config.baseURL).toBe((import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, ''))
      expect(config.url).toBe('/api/picture/delete')
      expect(config.data).toBe('{"id":"87001"}')
      expect(config).not.toHaveProperty('requestType')
      return {
        data: { code: 40101, message: '无权限' },
        status: 200,
        statusText: 'OK',
        headers: new AxiosHeaders(),
        config,
      }
    }
    const response = await request('/api/picture/delete', {
      method: 'POST',
      data: { id: '87001' },
      requestType: 'json',
      adapter,
    })
    expect(response.data).toEqual({ code: 40101, message: '无权限' })
  })

  it('preserves successful responses and rejects transport errors', async () => {
    const response = await request('/api/user/get/login', {
      adapter: async (config) => ({
        data: { code: 0, data: { id: '7' } },
        status: 200,
        statusText: 'OK',
        headers: new AxiosHeaders(),
        config,
      }),
    })
    expect(response.data).toEqual({ code: 0, data: { id: '7' } })
    const failure = new AxiosError('unavailable', 'ERR_NETWORK')
    await expect(
      request('/api/user/get/login', {
        adapter: async () => {
          throw failure
        },
      }),
    ).rejects.toBe(failure)
  })
})
