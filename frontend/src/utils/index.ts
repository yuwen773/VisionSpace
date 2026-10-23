import { saveAs } from 'file-saver'
import { apiUrl } from '@/apiOrigin'

export const formatSize = (size: number) => {
  if (!size && size !== 0) return '未知'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}

/** 服务端签发的受控内容入口（图片内容、反馈附件）。只有这些前缀才需要补 API 源站。 */
const CONTROLLED_CONTENT_PREFIXES = ['/picture/content/', '/feedback/attachment/']

export function isControlledContentUrl(url?: string | null): boolean {
  if (!url) return false
  return CONTROLLED_CONTENT_PREFIXES.some((prefix) => url.startsWith(prefix))
}

/**
 * 后端返回的受控内容入口是站内相对路径（/picture/content/...、/feedback/attachment/...），
 * 浏览器渲染前要补上 API 源站与 context-path。其他地址原样返回。
 */
export function contentSrc(url?: string | null): string {
  if (!url) return ''
  if (isControlledContentUrl(url)) {
    return apiUrl(`/api${url}`)
  }
  return url
}

/**
 * 下载图片。传入的地址若是本服务的受控内容入口，走服务端鉴权后取内容；
 * 图片内容入口额外追加 download 参数以拿到服务端给出的文件名。
 * 反馈附件入口只按 inline 返回，直接保存即可。源站地址不再直接使用。
 */
export function downloadImage(url?: string, fileName?: string): void {
  if (!url) return
  if (url.startsWith('/picture/content/')) {
    const target = `${contentSrc(url)}${url.includes('?') ? '&' : '?'}download=true`
    saveAs(target, fileName)
    return
  }
  if (url.startsWith('/feedback/attachment/')) {
    saveAs(contentSrc(url), fileName)
    return
  }
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
