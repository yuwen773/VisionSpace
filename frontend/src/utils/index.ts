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
  let colorValue = input.startsWith('0x') ? input.slice(2) : input;

  // 解析并格式化为6位十六进制
  const hexColor = parseInt(colorValue, 16).toString(16).padStart(6, '0');

  return `#${hexColor}`;
}
