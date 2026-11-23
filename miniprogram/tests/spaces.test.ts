import { describe, expect, it } from 'vitest'
import {
  canUploadToSpace,
  defaultPersonalSpaceName,
  mergeSpaceSummaries,
  pickDefaultSpace,
  roleLabel,
  spaceTypeLabel,
  type SpaceLike,
} from '../src/logic/spaces'

const personal: SpaceLike = { id: '1', spaceName: '我的空间', spaceType: 0, userId: '9' }
const teamA: SpaceLike = { id: '2', spaceName: '设计组', spaceType: 1, userId: '1' }
const teamB: SpaceLike = { id: 3, spaceName: '运营组', spaceType: 1, userId: '1' }

describe('mergeSpaceSummaries', () => {
  it('merges roles by space id and normalises string ids', () => {
    const result = mergeSpaceSummaries(
      [teamA, personal],
      [{ spaceId: '2', spaceRole: 'admin' }, { spaceId: '1', spaceRole: 'creator' }],
    )
    expect(result.map((space) => space.id)).toEqual([1, 2])
    expect(result[0]).toMatchObject({ name: '我的空间', role: 'creator', isPersonal: true })
    expect(result[1]).toMatchObject({ name: '设计组', role: 'admin', isPersonal: false })
  })

  it('sorts the personal space first and keeps server order inside groups', () => {
    const result = mergeSpaceSummaries([teamA, teamB, personal], [])
    expect(result.map((space) => space.name)).toEqual(['我的空间', '设计组', '运营组'])
  })

  it('leaves role empty when the membership is missing', () => {
    expect(mergeSpaceSummaries([teamA], [])[0].role).toBe('')
  })

  it('falls back to a readable name and skips entries without an id', () => {
    const result = mergeSpaceSummaries(
      [{ id: '', spaceName: 'x', spaceType: 1 }, { id: '4', spaceName: '   ', spaceType: 1 }],
      [],
    )
    expect(result).toHaveLength(1)
    expect(result[0].name).toBe('空间 4')
  })
})

describe('pickDefaultSpace', () => {
  it('prefers the personal space', () => {
    const spaces = mergeSpaceSummaries([teamA, personal], [])
    expect(pickDefaultSpace(spaces)?.name).toBe('我的空间')
  })

  it('falls back to the first space, or null when empty', () => {
    expect(pickDefaultSpace(mergeSpaceSummaries([teamA], []))?.name).toBe('设计组')
    expect(pickDefaultSpace([])).toBeNull()
  })
})

describe('spaceTypeLabel', () => {
  it('labels personal and team spaces, with a short form for badges', () => {
    expect(spaceTypeLabel(0)).toBe('个人空间')
    expect(spaceTypeLabel(1)).toBe('团队空间')
    expect(spaceTypeLabel(1, true)).toBe('团队')
    expect(spaceTypeLabel(null)).toBe('个人空间')
  })

  it('falls back to a neutral label for unknown types', () => {
    expect(spaceTypeLabel(9)).toBe('空间')
  })
})

describe('roleLabel', () => {
  it('marks the owner as creator even without a membership row', () => {
    const space = mergeSpaceSummaries([personal], [])[0]
    expect(roleLabel(space, 9)).toBe('创建者')
  })

  it('maps membership roles', () => {
    const [space] = mergeSpaceSummaries([teamA], [{ spaceId: '2', spaceRole: 'editor' }])
    expect(roleLabel(space, 9)).toBe('可编辑')
    expect(roleLabel(mergeSpaceSummaries([teamA], [{ spaceId: 2, spaceRole: 'admin' }])[0], 9)).toBe('管理员')
    expect(roleLabel(mergeSpaceSummaries([teamA], [{ spaceId: 2, spaceRole: 'viewer' }])[0], 9)).toBe('只读')
  })

  it('falls back to a neutral label when nothing matches', () => {
    const space = mergeSpaceSummaries([teamA], [])[0]
    expect(roleLabel(space, null)).toBe('成员')
  })
})

describe('canUploadToSpace', () => {
  it('lets the owner upload without a membership row', () => {
    const [space] = mergeSpaceSummaries([personal], [])
    expect(canUploadToSpace(space, 9)).toBe(true)
  })

  it('lets admin/editor members upload but not viewers', () => {
    const admin = mergeSpaceSummaries([teamA], [{ spaceId: 2, spaceRole: 'admin' }])[0]
    const editor = mergeSpaceSummaries([teamA], [{ spaceId: 2, spaceRole: 'editor' }])[0]
    const viewer = mergeSpaceSummaries([teamA], [{ spaceId: 2, spaceRole: 'viewer' }])[0]
    expect(canUploadToSpace(admin, 9)).toBe(true)
    expect(canUploadToSpace(editor, 9)).toBe(true)
    expect(canUploadToSpace(viewer, 9)).toBe(false)
  })

  it('does not treat an unknown viewer as the owner', () => {
    const [space] = mergeSpaceSummaries([personal], [])
    expect(canUploadToSpace(space, null)).toBe(false)
  })
})

describe('defaultPersonalSpaceName', () => {
  it('uses the nickname and trims to the backend limit', () => {
    expect(defaultPersonalSpaceName('小鱼')).toBe('小鱼的空间')
    expect(defaultPersonalSpaceName('  ')).toBe('我的空间')
    expect(defaultPersonalSpaceName(null)).toBe('我的空间')
    expect(defaultPersonalSpaceName('x'.repeat(40)).length).toBe(30)
  })
})
