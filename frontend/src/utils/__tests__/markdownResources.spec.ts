import { describe, expect, it } from 'vitest'
import { extractImages, extractLinks, extractResources, isImageUrl } from '../markdownResources'

describe('消息正文的资源解析', () => {
  it('显式图片语法进图片清单，不进链接清单', () => {
    const content = '看这张 ![山间晨雾](https://cdn.example.com/a.jpg) 的照片'
    expect(extractImages(content)).toEqual([
      { url: 'https://cdn.example.com/a.jpg', title: '山间晨雾' },
    ])
    // 负向后行断言必须排除图片语法，否则同一条回复会同时进两个面板
    expect(extractLinks(content)).toEqual([])
  })

  it('没有替代文字的图片仍然被识别', () => {
    expect(extractImages('![](https://cdn.example.com/b.png)')).toEqual([
      { url: 'https://cdn.example.com/b.png', title: undefined },
    ])
  })

  it('指向图片的普通链接也算图片资源', () => {
    const content = '[原图](https://cdn.example.com/c.webp) 与 [参考](https://example.com/page)'
    expect(extractImages(content)).toEqual([
      { url: 'https://cdn.example.com/c.webp', title: '原图' },
    ])
    expect(extractLinks(content)).toEqual([
      {
        url: 'https://cdn.example.com/c.webp',
        title: '原图',
        snippet: '原图',
        domain: 'cdn.example.com',
      },
      {
        url: 'https://example.com/page',
        title: '参考',
        snippet: '参考',
        domain: 'example.com',
      },
    ])
  })

  it('同一地址只保留一次，解析不出域名的不算链接', () => {
    const content = '[一](https://example.com/x) [二](https://example.com/x) [坏](/relative)'
    expect(extractLinks(content)).toHaveLength(1)
  })

  it('空正文不产生任何资源', () => {
    expect(extractResources('')).toEqual({ images: [], links: [] })
  })
})

describe('图片地址判定', () => {
  it('按扩展名与已知图床域名判断', () => {
    expect(isImageUrl('https://cdn.example.com/a.JPG?w=800')).toBe(true)
    expect(isImageUrl('https://images.pexels.com/photos/1')).toBe(true)
    expect(isImageUrl('https://example.com/article')).toBe(false)
    expect(isImageUrl('')).toBe(false)
  })
})
