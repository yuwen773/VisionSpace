import { describe, expect, it } from 'vitest'
import { describeError, NETWORK_ERROR_CODE } from '../src/logic/errors'

describe('describeError', () => {
  it('maps auth failures and flags login', () => {
    expect(describeError(40100).loginRequired).toBe(true)
    expect(describeError(40102).loginRequired).toBe(true)
    expect(describeError(40103).loginRequired).toBe(true)
  })

  it('marks permission and not-found errors as permanent', () => {
    expect(describeError(40101).retryable).toBe(false)
    expect(describeError(40300).retryable).toBe(false)
    expect(describeError(40400).retryable).toBe(false)
    expect(describeError(40400).message).toContain('不存在')
  })

  it('marks network and server errors as retryable', () => {
    expect(describeError(NETWORK_ERROR_CODE).retryable).toBe(true)
    expect(describeError(50000).retryable).toBe(true)
  })

  it('prefers the server message for parameter errors', () => {
    expect(describeError(40000, '账号或密码错误').message).toBe('账号或密码错误')
  })

  it('falls back for unknown codes instead of leaking the raw code', () => {
    const error = describeError(49999)
    expect(error.retryable).toBe(true)
    expect(error.message).not.toContain('49999')
  })
})
