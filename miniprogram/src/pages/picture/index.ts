import { errorMessage } from '../../logic/errors'
import { formatCount, formatDate, formatFileSize } from '../../logic/gallery'
import { ACTION_VIEW, createReportGate } from '../../logic/telemetry'
import { resolveContentUrl } from '../../services/content'
import { fetchPicture } from '../../services/gallery'
import { ApiError } from '../../services/http'
import { getToken } from '../../services/session'
import { reportOnce } from '../../services/telemetry'
import type { PictureVO } from '../../services/types'

interface CountItem {
  key: string
  label: string
  value: string
}

Page({
  data: {
    id: 0,
    loading: true,
    error: '',
    needsLogin: false,
    picture: null as PictureVO | null,
    imageUrl: '',
    title: '',
    author: '',
    category: '',
    dateText: '',
    sizeText: '',
    description: '',
    tags: [] as string[],
    counts: [] as CountItem[],
    canShare: false,
    imageError: false,
  },

  gate: createReportGate(),

  onLoad(options: Record<string, string | undefined>) {
    const id = Number(options.id)
    if (!Number.isFinite(id) || id <= 0) {
      this.setData({ loading: false, error: '作品不存在或已下线' })
      return
    }
    this.setData({ id })
    this.load()
  },

  onShow() {
    // 从登录页返回后自动重试一次，避免用户停在无权限页
    if (this.data.needsLogin && getToken() !== '') {
      this.load()
    }
  },

  async load(): Promise<void> {
    // 先收起转发菜单：加载失败或非公开作品时不能留下可转发的入口
    wx.hideShareMenu({})
    this.setData({ loading: true, error: '', needsLogin: false, imageError: false })
    try {
      const picture = await fetchPicture(this.data.id)
      if (picture.quarantined === true) {
        this.setData({ loading: false, error: '该作品已被隔离，暂时无法查看' })
        return
      }
      const inSpace = picture.spaceId !== null && picture.spaceId !== undefined
      const imageUrl = resolveContentUrl(
        picture.previewUrl || picture.url || picture.thumbnailUrl,
        inSpace,
      )
      if (imageUrl === '') {
        this.setData({ loading: false, error: '该作品暂无可查看的图片' })
        return
      }
      // 只有公开图库（无空间归属）的作品才可分享，避免接收方无权限；
      // 隔离作品已在上面提前返回
      const canShare = !inSpace
      const title = picture.name || '未命名作品'
      this.setData({
        loading: false,
        picture,
        imageUrl,
        title,
        author: picture.user?.userName ?? '',
        category: picture.category ?? '',
        dateText: formatDate(picture.createTime),
        sizeText: formatFileSize(picture.picSize),
        description: picture.introduction ?? '',
        tags: picture.tags ?? [],
        counts: buildCounts(picture),
        canShare,
      })
      wx.setNavigationBarTitle({ title })
      if (canShare) {
        wx.showShareMenu({ menus: ['shareAppMessage'] })
        // 空间图片的行为上报后端返回 403，只统计公开作品
        reportOnce(this.gate, this.data.id, ACTION_VIEW)
      }
    } catch (error) {
      console.warn('作品详情加载失败', error)
      const needsLogin =
        error instanceof ApiError && (error.code === 40100 || error.code === 40101)
      this.setData({
        loading: false,
        needsLogin,
        error: needsLogin ? '该作品仅对空间成员可见' : errorMessage(error),
      })
    }
  },

  onPreview() {
    const picture = this.data.picture
    if (!picture) {
      return
    }
    const inSpace = picture.spaceId !== null && picture.spaceId !== undefined
    const original = resolveContentUrl(picture.url || picture.previewUrl, inSpace)
    if (original === '') {
      return
    }
    wx.previewImage({ urls: [original], current: original })
  },

  onImageError() {
    this.setData({ imageError: true })
  },

  onRetry() {
    this.load()
  },

  onLoginTap() {
    wx.navigateTo({ url: '/pages/login/index' })
  },

  onBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/discover/index' })
  },

  onShareAppMessage() {
    return {
      title: this.data.title || 'VisionSpace',
      path: `/pages/picture/index?id=${this.data.id}`,
    }
  },
})

function buildCounts(picture: PictureVO): CountItem[] {
  return [
    { key: 'view', label: '浏览', value: formatCount(picture.viewCount) },
    { key: 'like', label: '点赞', value: formatCount(picture.likeCount) },
    { key: 'collect', label: '收藏', value: formatCount(picture.collectCount) },
    { key: 'share', label: '分享', value: formatCount(picture.shareCount) },
  ]
}
