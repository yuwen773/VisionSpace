// 手写 API 客户端：私有资源保护（单资源限时授权、违规资源隔离、反馈附件）。
//
// 生成版 pictureGrantController.ts / resourceModerationController.ts 提供了基础调用，
// 这里补上生成器给不出的东西：
//   1. 授权有效期的业务约束（默认 7 天、最长 30 天，与服务端上限一致）；
//   2. 授权、附件、隔离状态与资源类型的展示文案；
//   3. 一个入口统一导出，避免页面各写各的 import 路径。
//
// 本模块只做展示层约束与文案，不承担鉴权：能不能授权、能不能读内容一律由服务端判定。
import {
  createGrantUsingPost,
  listGrantsUsingGet,
  listMyGrantsUsingGet,
  revokeGrantUsingPost,
} from '@/api/pictureGrantController'
import {
  historyUsingGet,
  listQuarantinedUsingGet,
  quarantineUsingPost,
  releaseUsingPost,
} from '@/api/resourceModerationController'

export {
  createGrantUsingPost,
  listGrantsUsingGet,
  listMyGrantsUsingGet,
  revokeGrantUsingPost,
  historyUsingGet,
  listQuarantinedUsingGet,
  quarantineUsingPost,
  releaseUsingPost,
}

/** 单次授权的默认有效期（天） */
export const GRANT_DEFAULT_VALIDITY_DAYS = 7

/** 单次授权的最长有效期（天）；与服务端 ResourceAccessGrantServiceImpl.MAX_VALIDITY 一致 */
export const GRANT_MAX_VALIDITY_DAYS = 30

/** 授权状态 */
export type GrantStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED' | 'UNKNOWN'

/** 授权状态文案 */
const GRANT_STATUS_TEXT: Record<string, string> = {
  ACTIVE: '生效中',
  REVOKED: '已撤销',
  EXPIRED: '已过期',
}

/** 反馈附件状态文案 */
const ATTACHMENT_STATUS_TEXT: Record<string, string> = {
  UPLOADED: '待提交',
  BOUND: '已随反馈提交',
  LEGACY_VERIFIED: '归属已确认',
  BLOCKED: '归属不明，已阻断',
}

/** 资源类型文案 */
const RESOURCE_TYPE_TEXT: Record<string, string> = {
  picture: '图片',
  'feedback-attachment': '反馈附件',
}

/** 隔离状态文案 */
const ISOLATION_STATUS_TEXT: Record<string, string> = {
  QUARANTINED: '隔离中',
  RELEASED: '已恢复',
}

/**
 * 计算授权到期时间。
 *
 * 有效天数被夹在 1 到最长有效期之间：授权必须有限期，
 * 也不允许客户端提交超过服务端上限的到期时间。
 */
export function grantExpiresTime(
  days: number = GRANT_DEFAULT_VALIDITY_DAYS,
  now: Date = new Date(),
): Date {
  const clamped = Math.min(Math.max(Math.floor(days) || 0, 1), GRANT_MAX_VALIDITY_DAYS)
  return new Date(now.getTime() + clamped * 24 * 60 * 60 * 1000)
}

/** 授权状态文案；未知状态回退为“未知状态”，不猜测含义 */
export function grantStatusText(status?: string | null): string {
  return GRANT_STATUS_TEXT[status ?? ''] ?? '未知状态'
}

/** 授权是否仍然可读：只有生效中且未到期的授权才算 */
export function isGrantActive(grant?: { status?: string; expiresTime?: string } | null): boolean {
  if (!grant || grant.status !== 'ACTIVE') return false
  if (!grant.expiresTime) return false
  return new Date(grant.expiresTime).getTime() > Date.now()
}

/** 授权是否临近到期（24 小时内）。用于提示用户续期或撤销 */
export function grantExpiresSoon(
  grant?: { expiresTime?: string } | null,
  now: Date = new Date(),
): boolean {
  if (!grant?.expiresTime) return false
  const expires = new Date(grant.expiresTime).getTime()
  if (!Number.isFinite(expires)) return false
  return expires > now.getTime() && expires - now.getTime() <= 24 * 60 * 60 * 1000
}

/** 反馈附件状态文案 */
export function attachmentStatusText(status?: string | null): string {
  return ATTACHMENT_STATUS_TEXT[status ?? ''] ?? '未知状态'
}

/** 附件是否可读入口：归属不明或未确认的附件没有受控入口 */
export function attachmentReadable(attachment?: { url?: string | null } | null): boolean {
  return Boolean(attachment?.url)
}

/** 资源类型文案 */
export function resourceTypeText(resourceType?: string | null): string {
  return RESOURCE_TYPE_TEXT[resourceType ?? ''] ?? '未知资源'
}

/** 隔离状态文案 */
export function isolationStatusText(status?: string | null): string {
  return ISOLATION_STATUS_TEXT[status ?? ''] ?? '未知状态'
}

/** 把服务端返回的时间字段格式化为本地可读文本 */
export function formatDateTime(value?: string | number | Date | null): string {
  if (!value) return '-'
  const date = value instanceof Date ? value : new Date(value)
  if (!Number.isFinite(date.getTime())) return '-'
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
