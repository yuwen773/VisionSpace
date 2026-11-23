import { describe, expect, it } from 'vitest'
import { withTokenQuery } from '../src/logic/url'

describe('withTokenQuery', () => {
  it('adds the first query parameter', () => {
    expect(withTokenQuery('https://example.test/api/picture/content/1/thumbnail', 'abc')).toBe(
      'https://example.test/api/picture/content/1/thumbnail?satoken=abc',
    )
  })

  it('appends to an existing query string', () => {
    expect(withTokenQuery('https://example.test/x?v=1', 'abc')).toBe(
      'https://example.test/x?v=1&satoken=abc',
    )
  })

  it('encodes the token', () => {
    expect(withTokenQuery('https://example.test/x', 'a b+c')).toBe(
      'https://example.test/x?satoken=a%20b%2Bc',
    )
  })

  it('leaves the URL untouched without a token', () => {
    expect(withTokenQuery('https://example.test/x', '')).toBe('https://example.test/x')
    expect(withTokenQuery('', 'abc')).toBe('')
  })
})
