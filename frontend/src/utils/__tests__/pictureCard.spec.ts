import { describe, it, expect } from 'vitest'
import { pictureAspectRatio, pictureImageSrc } from '@/utils/pictureCard'

describe('pictureCard', () => {
  describe('pictureImageSrc', () => {
    it('优先缩略图', () => {
      expect(
        pictureImageSrc({
          thumbnailUrl: 'https://cdn.example.com/t.webp',
          url: 'https://cdn.example.com/o.jpg',
        }),
      ).toBe('https://cdn.example.com/t.webp')
    })

    it('没有缩略图时回落原图', () => {
      expect(pictureImageSrc({ thumbnailUrl: null, url: 'https://cdn.example.com/o.jpg' })).toBe(
        'https://cdn.example.com/o.jpg',
      )
    })

    it('受控内容入口仍走 contentSrc 加前缀', () => {
      expect(pictureImageSrc({ url: '/picture/content/9' })).toContain('/api/picture/content/9')
    })

    it('两者都没有时返回空串，不拼出半个地址', () => {
      expect(pictureImageSrc({})).toBe('')
      expect(pictureImageSrc({ thumbnailUrl: '', url: null })).toBe('')
    })
  })

  describe('pictureAspectRatio', () => {
    it('按高宽比换算百分比', () => {
      expect(pictureAspectRatio({ picWidth: 1200, picHeight: 600 })).toBe('50%')
      expect(pictureAspectRatio({ picWidth: 800, picHeight: 1200 })).toBe('150%')
    })

    it('缺宽或高时按 4:3 兜底', () => {
      expect(pictureAspectRatio({})).toBe('75%')
      expect(pictureAspectRatio({ picWidth: 800 })).toBe('75%')
      expect(pictureAspectRatio({ picWidth: 0, picHeight: 0 })).toBe('75%')
    })
  })
})
