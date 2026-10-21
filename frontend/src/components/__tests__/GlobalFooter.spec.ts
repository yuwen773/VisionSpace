import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GlobalFooter from '@/components/GlobalFooter.vue'

/**
 * 冒烟测试：验证 SFC 编译 + 挂载链路可用。
 * 同时锁住一个真实约束——页脚不能链到已被删除的路由。
 */
describe('GlobalFooter', () => {
  it('能正常渲染', () => {
    const wrapper = mount(GlobalFooter)
    expect(wrapper.find('footer.global-footer').exists()).toBe(true)
    expect(wrapper.text()).toContain('VisionSpace')
  })

  it('不指向已删除的路由', () => {
    const wrapper = mount(GlobalFooter)
    const hrefs = wrapper.findAll('a').map((a) => a.attributes('href') ?? '')

    // 这几条路由已在清理中移除，页脚若还链过去就是死链
    for (const dead of ['/test', '/feedback', '/search_picture', '/add_picture/batch']) {
      expect(hrefs).not.toContain(dead)
    }
  })
})
