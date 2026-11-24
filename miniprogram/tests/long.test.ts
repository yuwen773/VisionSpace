import { describe, expect, it } from 'vitest'
import { isValidId, toIdString, toNumberOrNull } from '../src/logic/long'

const SNOWFLAKE = '2038608856269361154'

describe('toIdString', () => {
  it('keeps 19-digit snowflake ids exact', () => {
    expect(toIdString(SNOWFLAKE)).toBe(SNOWFLAKE)
  })

  it('accepts small numbers and positive decimal strings', () => {
    expect(toIdString(42)).toBe('42')
    expect(toIdString(' 42 ')).toBe('42')
  })

  it('rejects values that are not positive integers', () => {
    expect(toIdString(0)).toBeNull()
    expect(toIdString(-1)).toBeNull()
    expect(toIdString(1.5)).toBeNull()
    expect(toIdString('')).toBeNull()
    expect(toIdString('  ')).toBeNull()
    expect(toIdString('abc')).toBeNull()
    expect(toIdString(null)).toBeNull()
    expect(toIdString(undefined)).toBeNull()
  })
})

describe('isValidId', () => {
  it('accepts only unsigned decimal strings without leading zero', () => {
    expect(isValidId(SNOWFLAKE)).toBe(true)
    expect(isValidId('1')).toBe(true)
    expect(isValidId('0')).toBe(false)
    expect(isValidId('012')).toBe(false)
    expect(isValidId('1.5')).toBe(false)
    expect(isValidId('')).toBe(false)
  })
})

describe('toNumberOrNull', () => {
  it('accepts numbers and decimal strings', () => {
    expect(toNumberOrNull(12)).toBe(12)
    expect(toNumberOrNull('12')).toBe(12)
  })

  it('rejects blanks and non-numeric text', () => {
    expect(toNumberOrNull('')).toBeNull()
    expect(toNumberOrNull('  ')).toBeNull()
    expect(toNumberOrNull('abc')).toBeNull()
    expect(toNumberOrNull(null)).toBeNull()
    expect(toNumberOrNull(undefined)).toBeNull()
    expect(toNumberOrNull(Number.NaN)).toBeNull()
  })
})
