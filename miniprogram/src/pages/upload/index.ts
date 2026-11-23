import { errorMessage } from '../../logic/errors'
import { formatFileSize } from '../../logic/gallery'
import {
  defaultUploadName,
  MAX_UPLOAD_COUNT,
  MAX_UPLOAD_NAME_LENGTH,
  normalizeUploadName,
} from '../../logic/upload'
import { ApiError } from '../../services/http'
import { openLogin } from '../../services/navigation'
import { getToken } from '../../services/session'
import { markSpaceUploaded } from '../../services/upload-signal'
import { uploadPictureFile } from '../../services/upload'
import { prepareUploadFile, releasePreparedFile } from '../../services/upload-image'

type ItemStatus = 'ready' | 'preparing' | 'uploading' | 'done' | 'failed'

interface UploadItem {
  key: string
  path: string
  name: string
  fallbackName: string
  bytes: number
  sizeText: string
  /** 已压缩并落到用户目录；重试时跳过压缩 */
  prepared: boolean
  status: ItemStatus
  statusText: string
  progress: number
  message: string
}

interface PickedFile {
  tempFilePath: string
  size: number
}

const STATUS_TEXT: Record<'ready' | 'preparing' | 'done' | 'failed', string> = {
  ready: '待上传',
  preparing: '压缩中…',
  done: '已上传',
  failed: '上传失败',
}

Page({
  data: {
    spaceId: 0,
    spaceName: '空间',
    maxCount: MAX_UPLOAD_COUNT,
    maxNameLength: MAX_UPLOAD_NAME_LENGTH,
    items: [] as UploadItem[],
    busy: false,
    pendingCount: 0,
    actionText: '开始上传',
  },

  /** 队列运行标志：setData 之外的第二道锁，避免连点重复入队。 */
  running: false,
  loginExpired: false,

  onLoad(options: Record<string, string | undefined>) {
    const spaceId = Number(options.spaceId)
    if (!Number.isFinite(spaceId) || spaceId <= 0) {
      this.setData({ spaceId: 0 })
      return
    }
    const spaceName = options.spaceName ? decodeURIComponent(options.spaceName) : '空间'
    this.setData({ spaceId, spaceName })
    wx.setNavigationBarTitle({ title: `上传到${spaceName}` })
  },

  onUnload() {
    for (const item of this.data.items) {
      releasePreparedFile(item.path)
    }
  },

  onChoose() {
    if (this.running) {
      return
    }
    const remaining = MAX_UPLOAD_COUNT - this.data.items.length
    if (remaining <= 0) {
      wx.showToast({ title: `最多上传 ${MAX_UPLOAD_COUNT} 张`, icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remaining,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (result) => {
        this.appendFiles(result.tempFiles)
      },
      fail: (error) => {
        if (!/cancel/i.test(error.errMsg)) {
          console.warn('选择图片失败', error.errMsg)
        }
      },
    })
  },

  appendFiles(files: readonly PickedFile[]): void {
    const items = this.data.items.slice()
    for (const file of files) {
      if (items.length >= MAX_UPLOAD_COUNT) {
        break
      }
      const index = items.length
      const fallbackName = defaultUploadName(index, file.tempFilePath)
      items.push({
        key: `${Date.now()}-${index}-${Math.floor(Math.random() * 1e4)}`,
        path: file.tempFilePath,
        name: fallbackName,
        fallbackName,
        bytes: file.size,
        sizeText: formatFileSize(file.size),
        prepared: false,
        status: 'ready',
        statusText: STATUS_TEXT.ready,
        progress: 0,
        message: '',
      })
    }
    this.setData({ items })
    this.refreshAction()
  },

  onNameInput(event: WechatMiniprogram.Input) {
    const index = Number(event.currentTarget.dataset.index)
    this.patchItem(index, { name: String(event.detail.value ?? '') })
  },

  onRemove(event: WechatMiniprogram.TouchEvent) {
    if (this.running) {
      return
    }
    const index = Number(event.currentTarget.dataset.index)
    const items = this.data.items.slice()
    const removed = items[index]
    if (removed === undefined) {
      return
    }
    items.splice(index, 1)
    releasePreparedFile(removed.path)
    this.setData({ items })
    this.refreshAction()
  },

  onStart() {
    if (this.running || this.data.spaceId <= 0) {
      return
    }
    if (getToken() === '') {
      openLogin()
      return
    }
    const indices: number[] = []
    this.data.items.forEach((item, index) => {
      if (item.status === 'ready' || item.status === 'failed') {
        indices.push(index)
      }
    })
    if (indices.length === 0) {
      return
    }
    void this.runQueue(indices)
  },

  async runQueue(indices: number[]): Promise<void> {
    this.running = true
    this.loginExpired = false
    this.setData({ busy: true })
    this.refreshAction()
    let succeeded = 0
    let failed = 0
    for (const index of indices) {
      const ok = await this.uploadOne(index)
      if (ok) {
        succeeded += 1
      } else {
        failed += 1
      }
      if (this.loginExpired) {
        break
      }
    }
    this.running = false
    this.setData({ busy: false })
    this.refreshAction()
    if (this.loginExpired) {
      wx.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
      openLogin()
      return
    }
    if (failed === 0) {
      wx.showToast({ title: `已上传 ${succeeded} 张，待审核`, icon: 'success' })
      markSpaceUploaded(this.data.spaceId)
      setTimeout(() => {
        wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/space/index' }) })
      }, 600)
      return
    }
    wx.showModal({
      title: '部分图片未上传',
      content: `${succeeded} 张成功，${failed} 张失败，可点「重试」继续。`,
      showCancel: false,
    })
  },

  async uploadOne(index: number): Promise<boolean> {
    const item = this.data.items[index]
    if (item === undefined) {
      return false
    }
    let path = item.path
    if (!item.prepared) {
      this.patchItem(index, {
        status: 'preparing',
        statusText: STATUS_TEXT.preparing,
        progress: 0,
        message: '',
      })
      try {
        const prepared = await prepareUploadFile(item.path)
        path = prepared.path
        this.patchItem(index, {
          path,
          bytes: prepared.bytes,
          sizeText: formatFileSize(prepared.bytes),
          prepared: true,
          status: 'ready',
          statusText: STATUS_TEXT.ready,
        })
      } catch (error) {
        console.warn('图片准备失败', error)
        this.patchItem(index, {
          status: 'failed',
          statusText: STATUS_TEXT.failed,
          message: errorMessage(error),
        })
        return false
      }
    }
    const current = this.data.items[index]
    if (current === undefined) {
      return false
    }
    const picName = normalizeUploadName(current.name, current.fallbackName)
    this.patchItem(index, {
      status: 'uploading',
      statusText: '上传中…',
      progress: 0,
      message: '',
    })
    try {
      await uploadPictureFile({
        filePath: path,
        spaceId: this.data.spaceId,
        picName,
        onProgress: (percent) => {
          this.patchItem(index, { progress: percent, statusText: `上传中 ${percent}%` })
        },
      })
      this.patchItem(index, {
        status: 'done',
        statusText: STATUS_TEXT.done,
        progress: 100,
        message: '',
      })
      return true
    } catch (error) {
      console.warn('图片上传失败', error)
      if (error instanceof ApiError && error.code === 40100) {
        this.loginExpired = true
      }
      this.patchItem(index, {
        status: 'failed',
        statusText: STATUS_TEXT.failed,
        progress: 0,
        message: errorMessage(error),
      })
      return false
    }
  },

  patchItem(index: number, patch: Partial<UploadItem>): void {
    const items = this.data.items.slice()
    const current = items[index]
    if (current === undefined) {
      return
    }
    items[index] = { ...current, ...patch }
    this.setData({ items })
    this.refreshAction()
  },

  refreshAction(): void {
    const items = this.data.items
    const ready = items.filter((item) => item.status === 'ready').length
    const failed = items.filter((item) => item.status === 'failed').length
    const pending = ready + failed
    let actionText = '开始上传'
    if (pending > 0) {
      // 还有没传过的新图时按总数说，纯失败队列才叫「重试」
      actionText = ready === 0 ? `重试失败项（${failed}）` : `上传 ${pending} 张`
    }
    this.setData({ pendingCount: pending, actionText })
  },
})
