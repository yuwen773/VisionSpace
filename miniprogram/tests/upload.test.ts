import { describe, expect, it } from 'vitest'
import {
  COMPRESS_STEPS,
  compressStepAt,
  defaultUploadName,
  fileExtension,
  hasAllowedExtension,
  isWithinUploadLimit,
  MAX_UPLOAD_BYTES,
  MAX_UPLOAD_NAME_LENGTH,
  normalizeUploadName,
  pendingUploadIndices,
  uploadQueueLabel,
  type UploadStatus,
} from '../src/logic/upload'

describe('isWithinUploadLimit', () => {
  it('accepts known sizes up to 2MB', () => {
    expect(isWithinUploadLimit(1)).toBe(true)
    expect(isWithinUploadLimit(MAX_UPLOAD_BYTES)).toBe(true)
  })

  it('rejects oversize and unknown sizes', () => {
    expect(isWithinUploadLimit(MAX_UPLOAD_BYTES + 1)).toBe(false)
    expect(isWithinUploadLimit(0)).toBe(false)
    expect(isWithinUploadLimit(Number.NaN)).toBe(false)
  })
})

describe('compressStepAt', () => {
  it('walks the ladder and stops after the last step', () => {
    expect(compressStepAt(0)?.quality).toBe(80)
    expect(compressStepAt(COMPRESS_STEPS.length - 1)?.compressedWidth).toBe(1200)
    expect(compressStepAt(COMPRESS_STEPS.length)).toBeNull()
    expect(compressStepAt(-1)).toBeNull()
  })

  it('only downscales in the last two steps', () => {
    expect(compressStepAt(2)?.compressedWidth).toBeUndefined()
    expect(compressStepAt(3)?.compressedWidth).toBe(1600)
  })
})

describe('fileExtension', () => {
  it('returns the lower-case extension with the dot', () => {
    expect(fileExtension('wxfile://tmp/a.JPG')).toBe('.jpg')
    expect(fileExtension('x.webp')).toBe('.webp')
  })

  it('returns an empty string when there is no extension', () => {
    expect(fileExtension('noext')).toBe('')
    expect(fileExtension('a/b/')).toBe('')
  })
})

describe('hasAllowedExtension', () => {
  it('accepts the formats the backend template allows', () => {
    expect(hasAllowedExtension('wxfile://tmp/a.JPG')).toBe(true)
    expect(hasAllowedExtension('x.jpeg')).toBe(true)
    expect(hasAllowedExtension('x.png')).toBe(true)
    expect(hasAllowedExtension('x.webp')).toBe(true)
  })

  it('rejects formats that must be transcoded first', () => {
    expect(hasAllowedExtension('x.heic')).toBe(false)
    expect(hasAllowedExtension('x.gif')).toBe(false)
    expect(hasAllowedExtension('noext')).toBe(false)
  })
})

describe('defaultUploadName', () => {
  it('keeps human file names', () => {
    expect(defaultUploadName(0, 'wxfile://tmp/海边日落.jpg')).toBe('海边日落')
  })

  it('falls back for machine-generated names', () => {
    expect(defaultUploadName(2, 'wxfile://tmp/tmp_1a2b3c.jpg')).toBe('作品 3')
    expect(defaultUploadName(0, 'wxfile://tmp/1a2b3c4d5e6f.png')).toBe('作品 1')
    expect(defaultUploadName(0, '')).toBe('作品 1')
  })

  it('truncates long names to the input limit', () => {
    expect(defaultUploadName(0, `x/${'名'.repeat(60)}.jpg`).length).toBe(MAX_UPLOAD_NAME_LENGTH)
  })
})

describe('normalizeUploadName', () => {
  it('collapses whitespace and trims', () => {
    expect(normalizeUploadName('  山间   清晨 ', 'x')).toBe('山间 清晨')
  })

  it('falls back when the name is empty', () => {
    expect(normalizeUploadName('   ', '作品 1')).toBe('作品 1')
  })

  it('caps the length', () => {
    expect(normalizeUploadName('名'.repeat(60), 'x').length).toBe(MAX_UPLOAD_NAME_LENGTH)
  })
})

describe('pendingUploadIndices', () => {
  const items = (...statuses: UploadStatus[]) => statuses.map((status) => ({ status }))

  it('collects ready and failed items in list order', () => {
    expect(pendingUploadIndices(items('done', 'ready', 'uploading', 'failed'))).toEqual([1, 3])
  })

  it('returns nothing when everything is done', () => {
    expect(pendingUploadIndices(items('done', 'done'))).toEqual([])
  })
})

describe('uploadQueueLabel', () => {
  const items = (...statuses: UploadStatus[]) => statuses.map((status) => ({ status }))

  it('counts ready and failed as pending', () => {
    expect(uploadQueueLabel(items('ready', 'failed', 'done'))).toEqual({ pending: 2, text: '上传 2 张' })
  })

  it('calls a pure failure queue a retry', () => {
    expect(uploadQueueLabel(items('failed', 'failed', 'done'))).toEqual({
      pending: 2,
      text: '重试失败项（2）',
    })
  })

  it('has nothing to do when the queue is finished', () => {
    expect(uploadQueueLabel(items('done'))).toEqual({ pending: 0, text: '开始上传' })
    expect(uploadQueueLabel([])).toEqual({ pending: 0, text: '开始上传' })
  })
})
