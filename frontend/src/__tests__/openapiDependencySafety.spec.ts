// @vitest-environment node
import { createRequire } from 'node:module'
import { describe, expect, it } from 'vitest'

const require = createRequire(import.meta.url)
const generatorRequire = createRequire(require.resolve('@umijs/openapi/package.json'))
const mock = generatorRequire('mockjs')

describe('OpenAPI generator mock dependency', () => {
  it.each(['__proto__', 'constructor'])('does not pollute prototypes through %s', (key) => {
    const originalPrototype = Object.getPrototypeOf(mock.Random)
    const payload = JSON.parse(
      key === '__proto__'
        ? '{"__proto__":{"vs47PrototypePolluted":"unexpected"}}'
        : '{"constructor":{"prototype":{"vs47PrototypePolluted":"unexpected"}}}',
    )
    try {
      mock.Random.extend(payload)
      expect(Reflect.get(Object.prototype, 'vs47PrototypePolluted')).toBeUndefined()
    } finally {
      Reflect.deleteProperty(Object.prototype, 'vs47PrototypePolluted')
      Reflect.deleteProperty(mock.Random, 'constructor')
      Object.setPrototypeOf(mock.Random, originalPrototype)
    }
  })

  it('supports the generator custom random values', () => {
    try {
      mock.Random.extend({ vs47FixedValue: () => 'generator-compatible' })
      expect(mock.mock('@vs47FixedValue')).toBe('generator-compatible')
    } finally {
      Reflect.deleteProperty(mock.Random, 'vs47FixedValue')
    }
  })
})
