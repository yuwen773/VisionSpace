/**
 * 后端 Long 字段统一序列化为十进制字符串，客户端所有数值入口都走这里。
 * 单独成模块，避免展示层与领域层互相依赖。
 */
export function toNumberOrNull(value: number | string | null | undefined): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}
