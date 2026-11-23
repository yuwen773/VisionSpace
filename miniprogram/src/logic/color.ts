/**
 * 颜色检索相关的纯函数：色卡、十六进制归一化、取平均色、前景色对比。
 * 不依赖 wx / canvas API，可直接单测。
 */

/** 色卡：高饱和主色 + 两个中性色，够覆盖常见检索意图。 */
export const COLOR_PALETTE = [
  '#ef4444',
  '#f97316',
  '#f59e0b',
  '#22c55e',
  '#14b8a6',
  '#3b82f6',
  '#6366f1',
  '#a855f7',
  '#ec4899',
  '#78716c',
  '#0f172a',
  '#f8fafc',
] as const

const HEX_PATTERN = /^[0-9a-f]{6}$/

/**
 * 归一化颜色输入：接受 `#abc` / `abc` / `#AABBCC`，返回小写 `#rrggbb`。
 * 非法值返回 null，调用方据此禁用检索按钮，而不是把脏参数发给后端。
 */
export function normalizeHex(input: string | null | undefined): string | null {
  if (typeof input !== 'string') {
    return null
  }
  const raw = input.trim().replace(/^#/, '').toLowerCase()
  if (!HEX_PATTERN.test(raw)) {
    return null
  }
  return `#${raw}`
}

/**
 * 取图片平均色：跳过透明像素，返回 `#rrggbb`；没有可用像素时返回 null。
 * 输入是 canvas `getImageData().data`（RGBA，0-255）。
 */
export function averageColor(
  rgba: Uint8ClampedArray | number[],
  width: number,
  height: number,
): string | null {
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0) {
    return null
  }
  const pixelCount = Math.min(rgba.length / 4, width * height)
  let red = 0
  let green = 0
  let blue = 0
  let counted = 0
  for (let index = 0; index < pixelCount; index += 1) {
    const offset = index * 4
    const alpha = rgba[offset + 3]
    if (typeof alpha !== 'number' || alpha < 128) {
      continue
    }
    red += rgba[offset]
    green += rgba[offset + 1]
    blue += rgba[offset + 2]
    counted += 1
  }
  if (counted === 0) {
    return null
  }
  return toHex(red / counted, green / counted, blue / counted)
}

/** 色块上的文字/对勾颜色：亮底用深色，暗底用白色。 */
function toHex(red: number, green: number, blue: number): string {
  return `#${channel(red)}${channel(green)}${channel(blue)}`
}

function channel(value: number): string {
  const clamped = Math.max(0, Math.min(255, Math.round(value)))
  return clamped.toString(16).padStart(2, '0')
}
