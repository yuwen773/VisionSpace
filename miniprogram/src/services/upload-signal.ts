/**
 * 「刚刚上传成功」的一次性信号。
 *
 * 上传页无法直接把结果塞回上一页（页面栈里的实例不可靠地共享），
 * 用模块级标记让空间详情在自己 `onShow` 时按 spaceId 消费一次，
 * 消费即清零，避免每次切回都重新拉取。
 */
let uploadedSpaceId = 0

export function markSpaceUploaded(spaceId: number): void {
  uploadedSpaceId = spaceId
}

export function consumeSpaceUploaded(spaceId: number): boolean {
  if (uploadedSpaceId !== spaceId) {
    return false
  }
  uploadedSpaceId = 0
  return true
}
