/**
 * 后端 Long 字段统一序列化为十进制字符串，客户端所有数值入口都走这里。
 *
 * 两类值必须分开处理：
 * - **id**：19 位雪花 id 超过 JS 安全整数范围，转成 number 会静默丢精度，
 *   所以 id 一律保留十进制字符串；只接受正整数文本。
 * - **计数/体积**：用于展示，允许转 number；非法值返回 null。
 */


/** 计数、体积等展示值；非法输入返回 null。 */
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

const ID_PATTERN = /^[1-9][0-9]*$/

/**
 * 把线格式 id 归一化成十进制字符串；`0`、负数、小数、空串一律视为非法。
 * 不转 number，避免 19 位 id 被舍入成另一个 id。
 */
export function toIdString(value: number | string | null | undefined): string | null {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? String(value) : null
  }
  if (typeof value === 'string') {
    const text = value.trim()
    return ID_PATTERN.test(text) ? text : null
  }
  return null
}

export function isValidId(id: string): boolean {
  return ID_PATTERN.test(id)
}
