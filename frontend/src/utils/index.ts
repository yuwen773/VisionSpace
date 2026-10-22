import { saveAs } from 'file-saver'

export const formatSize = (size: number) => {
  if (!size && size !== 0) return '未知'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

export function downloadImage(url?: string, fileName?: string): void {
  if (!url) return
  saveAs(url, fileName)
}


export function toHexColor(input:string) {
  if (!input) return '#000000';

  // 去掉0x前缀和#前缀
  const colorValue = input.startsWith('0x') ? input.slice(2) : input;

  // 解析并格式化为6位十六进制
  const hexColor = parseInt(colorValue, 16).toString(16).padStart(6, '0');

  return `#${hexColor}`;
}

export function lerp(a: number, b: number, t: number): number {
  return (1 - t) * a + t * b;
}

/**
 * 把路由参数（可能是 string）转换为后端 Long 类型所需的 number。
 * 无效输入返回 undefined，便于直接作为可选参数传递。
 */
export function toId(value: number | string | null | undefined): number | undefined {
  if (value === null || value === undefined || value === '') return undefined
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : undefined
}

export function clamp(v: number, min: number, max: number): number {
  return Math.min(Math.max(v, min), max);
}

export function debounce<T extends (...args: unknown[]) => void>(func: T, wait: number) {
  let timeout: number;
  return function (this: unknown, ...args: Parameters<T>) {
    window.clearTimeout(timeout);
    timeout = window.setTimeout(() => func.apply(this, args), wait);
  };
}
