/**
 * 空间列表的纯逻辑：把「我可见的空间」与「我在空间里的角色」合并成一份展示模型。
 *
 * 后端把空间列表与成员关系拆成两个接口，页面只能自己拼；拼法与排序放这里，
 * 便于单测，也避免两个页面各写一套。
 */
import { toNumberOrNull } from './long'

export interface SpaceLike {
  id: number | string
  spaceName: string | null
  spaceType: number | null
  userId?: number | string | null
}

export interface MembershipLike {
  spaceId: number | string
  spaceRole: string | null
}

export interface SpaceSummary {
  id: number
  name: string
  /** 0 = 个人空间，1 = 团队空间 */
  spaceType: number
  role: string
  isPersonal: boolean
  ownerId: number | null
}

export const PERSONAL_SPACE_TYPE = 0
export const TEAM_SPACE_TYPE = 1

const SPACE_TYPE_LABELS: Record<number, { full: string; short: string }> = {
  [PERSONAL_SPACE_TYPE]: { full: '个人空间', short: '个人' },
  [TEAM_SPACE_TYPE]: { full: '团队空间', short: '团队' },
}

/** 空间类型文案；未知类型（例如接口新增枚举）退回中性文案。 */
export function spaceTypeLabel(
  spaceType: number | null | undefined,
  short = false,
): string {
  const label = SPACE_TYPE_LABELS[spaceType ?? PERSONAL_SPACE_TYPE]
  if (label === undefined) {
    return '空间'
  }
  return short ? label.short : label.full
}

/**
 * 合并并按「个人空间在前、团队在后，组内保持服务端顺序」排序。
 * 成员关系缺失时 role 为空串，页面按只读处理。
 */
export function mergeSpaceSummaries(
  spaces: readonly SpaceLike[],
  memberships: readonly MembershipLike[],
): SpaceSummary[] {
  const roleBySpaceId = new Map<number, string>()
  for (const membership of memberships) {
    const id = toNumberOrNull(membership.spaceId)
    if (id !== null && typeof membership.spaceRole === 'string') {
      roleBySpaceId.set(id, membership.spaceRole)
    }
  }
  const summaries: SpaceSummary[] = []
  for (const space of spaces) {
    const id = toNumberOrNull(space.id)
    if (id === null) {
      continue
    }
    const spaceType = space.spaceType ?? PERSONAL_SPACE_TYPE
    summaries.push({
      id,
      name: (space.spaceName ?? '').trim() || `空间 ${id}`,
      spaceType,
      role: roleBySpaceId.get(id) ?? '',
      isPersonal: spaceType === PERSONAL_SPACE_TYPE,
      ownerId: toNumberOrNull(space.userId ?? null),
    })
  }
  // Array.prototype.sort 是稳定的，组内顺序保持接口返回顺序
  return summaries.sort((left, right) => Number(right.isPersonal) - Number(left.isPersonal))
}

/** 默认进入个人空间；没有个人空间时退回第一个。 */
export function pickDefaultSpace(spaces: readonly SpaceSummary[]): SpaceSummary | null {
  return spaces.find((space) => space.isPersonal) ?? spaces[0] ?? null
}

const ROLE_LABELS: Record<string, string> = {
  admin: '管理员',
  editor: '可编辑',
  viewer: '只读',
}

/** 角色展示文案：所有者优先于 space_user 里的角色。 */
export function roleLabel(space: SpaceSummary, currentUserId: number | null): string {
  if (currentUserId !== null && space.ownerId === currentUserId) {
    return '创建者'
  }
  return ROLE_LABELS[space.role] ?? '成员'
}

/** 默认个人空间名：`<昵称>的空间`，截到后端 30 字上限。 */
export function defaultPersonalSpaceName(userName: string | null | undefined): string {
  const name = typeof userName === 'string' ? userName.trim() : ''
  const base = name === '' ? '我' : name
  return `${base}的空间`.slice(0, 30)
}
