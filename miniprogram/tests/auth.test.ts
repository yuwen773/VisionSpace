import { describe, expect, it } from 'vitest'
import {
  CODE_WX_NOT_BOUND,
  needsBind,
  toStoredProfile,
  validateCredentials,
} from '../src/logic/auth'

describe('validateCredentials', () => {
  it('accepts an account of 4+ chars and a password of 8+ chars', () => {
    expect(validateCredentials('demo', 'password1')).toEqual({ valid: true, message: '' })
  })

  it('rejects blank input', () => {
    expect(validateCredentials('', '').valid).toBe(false)
    expect(validateCredentials('demo', '').valid).toBe(false)
    expect(validateCredentials('   ', 'password1').valid).toBe(false)
  })

  it('matches the backend minimum lengths', () => {
    expect(validateCredentials('abc', 'password1').valid).toBe(false)
    expect(validateCredentials('demo', 'pass123').valid).toBe(false)
  })

  it('trims the account before measuring it', () => {
    expect(validateCredentials('  abcd  ', 'password1').valid).toBe(true)
  })
})

describe('needsBind', () => {
  it('only treats 40102 as bind-required', () => {
    expect(needsBind(CODE_WX_NOT_BOUND)).toBe(true)
    expect(needsBind(40000)).toBe(false)
    expect(needsBind(40100)).toBe(false)
  })
})

describe('toStoredProfile', () => {
  it('keeps only id, name and avatar, normalising a string id', () => {
    expect(toStoredProfile({ id: '7', userName: '小鱼', userAvatar: '/a.png' })).toEqual({
      id: '7',
      userName: '小鱼',
      userAvatar: '/a.png',
    })
  })

  it('keeps 19-digit snowflake ids as strings instead of rounding them', () => {
    expect(toStoredProfile({ id: '2038608856269361154' })?.id).toBe('2038608856269361154')
  })

  it('fills missing fields with empty strings', () => {
    expect(toStoredProfile({ id: 8, userName: null })).toEqual({
      id: '8',
      userName: '',
      userAvatar: '',
    })
  })

  it('rejects ids that cannot be parsed', () => {
    expect(toStoredProfile({ id: '' })).toBeNull()
    expect(toStoredProfile({ id: 0 })).toBeNull()
    expect(toStoredProfile({ id: 1.5 })).toBeNull()
  })
})
