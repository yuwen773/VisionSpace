/**
 * 附件准入策略。
 *
 * <p>与后端 `AgentImageUploadService` 保持一致：只收 JPG / PNG / WebP，
 * 单文件上限 2MB。前端提前拦截，避免把注定被拒的请求发出去。
 */

export const ACCEPTED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

export const MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024

/** `<input accept>` 用：同时给 MIME 与常见扩展名，兼容不同系统的文件选择框 */
export const ATTACHMENT_ACCEPT_ATTR = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp'

/** 返回拒绝原因；通过校验时返回 null。 */
export function validateAttachment(file: File): string | null {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type as (typeof ACCEPTED_IMAGE_TYPES)[number])) {
    return '仅支持 JPG / PNG / WebP'
  }
  if (file.size > MAX_ATTACHMENT_BYTES) return '图片大小不能超过 2MB'
  return null
}
