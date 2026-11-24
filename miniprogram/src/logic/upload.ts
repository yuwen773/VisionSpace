/**
 * 上传队列的纯逻辑：数量/体积上限、压缩档位、默认名称。
 * 不依赖 wx API，可直接单测。
 */

/** 一屏最多选 9 张，与微信 `chooseMedia` 的常用上限一致。 */
export const MAX_UPLOAD_COUNT = 9

/** 后端 `FilePictureUpload` 硬限制 2MB，客户端必须压到这个值以下再发。 */
export const MAX_UPLOAD_BYTES = 2 * 1024 * 1024

/** picture.name 是 varchar(128)，40 字对移动端输入已经足够。 */
export const MAX_UPLOAD_NAME_LENGTH = 40

export interface CompressStep {
  quality: number
  /**
   * 仅对超过 2MB 的图再缩边（基础库 2.26+）；旧版本会忽略该参数，
   * 后续档位只是继续降质量。
   */
  compressedWidth?: number
}

export const COMPRESS_STEPS: readonly CompressStep[] = [
  { quality: 80 },
  { quality: 60 },
  { quality: 40 },
  { quality: 25, compressedWidth: 1600 },
  { quality: 20, compressedWidth: 1200 },
]

export function compressStepAt(index: number): CompressStep | null {
  return COMPRESS_STEPS[index] ?? null
}

/** 未知大小（NaN/0）不能当成合格，否则会把超限文件直接发出去。 */
export function isWithinUploadLimit(bytes: number): boolean {
  return Number.isFinite(bytes) && bytes > 0 && bytes <= MAX_UPLOAD_BYTES
}

const ALLOWED_EXTENSIONS = new Set(['jpeg', 'png', 'jpg', 'webp'])

/** 小写扩展名（带点）；没有扩展名返回空串。 */
export function fileExtension(path: string): string {
  const match = /\.[a-z0-9]+$/i.exec(path)
  return match === null ? '' : match[0].toLowerCase()
}

/** 后端只接受 jpeg/png/jpg/webp，其它格式（如 HEIC）必须先转码。 */
export function hasAllowedExtension(path: string): boolean {
  return ALLOWED_EXTENSIONS.has(fileExtension(path).slice(1))
}

const MACHINE_NAME = /^(tmp|temp|wxfile|image|img)[_-]/i
const HEX_NAME = /^[0-9a-f]{8,}$/i

/**
 * 从临时文件路径猜默认作品名。微信给的多是 `tmp_xxx.jpg` 这类机器名，
 * 取不到像样的名字就退回「作品 N」，让用户自己改。
 */
export function defaultUploadName(index: number, path: string): string {
  const base = path.split(/[\\/]/).pop() ?? ''
  const extension = fileExtension(base)
  const candidate = (extension === '' ? base : base.slice(0, -extension.length)).trim()
  if (candidate === '' || MACHINE_NAME.test(candidate) || HEX_NAME.test(candidate)) {
    return `作品 ${index + 1}`
  }
  return candidate.slice(0, MAX_UPLOAD_NAME_LENGTH)
}

/** 名称归一化：折叠空白、去首尾空格；空名回退到默认名。 */
export function normalizeUploadName(raw: string, fallback: string): string {
  const name = raw.replace(/\s+/g, ' ').trim()
  if (name === '') {
    return fallback
  }
  return name.slice(0, MAX_UPLOAD_NAME_LENGTH)
}

export type UploadStatus = 'ready' | 'preparing' | 'uploading' | 'done' | 'failed'

export interface UploadStatusLike {
  status: UploadStatus
}

/** 还欠一次上传的条目下标：未传过的和失败待重试的，保持列表顺序。 */
export function pendingUploadIndices(items: readonly UploadStatusLike[]): number[] {
  const indices: number[] = []
  items.forEach((item, index) => {
    if (item.status === 'ready' || item.status === 'failed') {
      indices.push(index)
    }
  })
  return indices
}

/**
 * 队列按钮文案。还有没传过的新图时按总数说，纯失败队列才叫「重试」。
 * 返回的 pending 同时用于按钮禁用判断。
 */
export function uploadQueueLabel(items: readonly UploadStatusLike[]): {
  pending: number
  text: string
} {
  let ready = 0
  let failed = 0
  for (const item of items) {
    if (item.status === 'ready') {
      ready += 1
    } else if (item.status === 'failed') {
      failed += 1
    }
  }
  if (ready + failed === 0) {
    return { pending: 0, text: '开始上传' }
  }
  return {
    pending: ready + failed,
    text: ready === 0 ? `重试失败项（${failed}）` : `上传 ${ready + failed} 张`,
  }
}
