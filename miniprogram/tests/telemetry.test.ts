import { describe, expect, it } from 'vitest'
import {
  ACTION_CLICK,
  ACTION_IMPRESSION,
  ACTION_VIEW,
  actionEvent,
  createReportGate,
  isAnonymousAction,
  isValidEventId,
  makeEventId,
  SOURCE_MINI_PROGRAM,
} from '../src/logic/telemetry'

describe('makeEventId', () => {
  it('produces an id accepted by the backend pattern', () => {
    const id = makeEventId(1_700_000_000_000, 'a1b2c3!@#')
    expect(id).toMatch(/^[A-Za-z0-9_.:-]{1,64}$/)
    expect(id.startsWith('mp-')).toBe(true)
  })

  it('stays within the 64 character limit', () => {
    const id = makeEventId(1_700_000_000_000, 'x'.repeat(100))
    expect(id.length).toBeLessThanOrEqual(64)
    expect(isValidEventId(id)).toBe(true)
  })

  it('differs when entropy differs', () => {
    const base = 1_700_000_000_000
    expect(makeEventId(base, 'aaaa')).not.toBe(makeEventId(base, 'bbbb'))
  })
})

describe('actionEvent', () => {
  it('always tags events with the mini program source', () => {
    expect(actionEvent(12, ACTION_CLICK, 'mp-1')).toEqual({
      pictureId: 12,
      actionType: ACTION_CLICK,
      source: SOURCE_MINI_PROGRAM,
      eventId: 'mp-1',
    })
  })
})

describe('createReportGate', () => {
  it('lets each picture/action pair through exactly once', () => {
    const gate = createReportGate()
    expect(gate.tryAcquire(1, ACTION_IMPRESSION)).toBe(true)
    expect(gate.tryAcquire(1, ACTION_IMPRESSION)).toBe(false)
    expect(gate.tryAcquire(1, ACTION_CLICK)).toBe(true)
    expect(gate.tryAcquire(2, ACTION_IMPRESSION)).toBe(true)
    expect(gate.size()).toBe(3)
  })

  it('rejects anonymous actions the backend forbids', () => {
    const gate = createReportGate()
    expect(gate.tryAcquire(1, 3)).toBe(false)
    expect(gate.size()).toBe(0)
  })

  it('rejects invalid picture ids', () => {
    const gate = createReportGate()
    expect(gate.tryAcquire(0, ACTION_IMPRESSION)).toBe(false)
    expect(gate.tryAcquire(-1, ACTION_VIEW)).toBe(false)
    expect(gate.tryAcquire(1.5, ACTION_VIEW)).toBe(false)
  })

  it('can be reset', () => {
    const gate = createReportGate()
    gate.tryAcquire(1, ACTION_IMPRESSION)
    gate.reset()
    expect(gate.size()).toBe(0)
    expect(gate.tryAcquire(1, ACTION_IMPRESSION)).toBe(true)
  })
})

describe('isAnonymousAction', () => {
  it('allows only impression, click and view', () => {
    expect([0, 1, 2].every(isAnonymousAction)).toBe(true)
    expect([3, 4, 5, 6].some(isAnonymousAction)).toBe(false)
  })
})
