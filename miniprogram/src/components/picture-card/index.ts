import type { GalleryCard } from '../../logic/gallery'

Component({
  properties: {
    card: {
      type: Object,
    },
  },

  data: {
    failed: false,
  },

  observers: {
    // 复用同一个组件实例时（列表刷新），重置图片失败状态
    card() {
      if (this.data.failed) {
        this.setData({ failed: false })
      }
    },
  },

  methods: {
    onImageError() {
      this.setData({ failed: true })
    },

    onTap() {
      const card = this.data.card as GalleryCard | undefined
      if (!card || card.broken) {
        return
      }
      this.triggerEvent('open', { id: card.id })
    },
  },
})
