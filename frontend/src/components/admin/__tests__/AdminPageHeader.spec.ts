import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'

/**
 * 页头是六个管理页共用的唯一一份实现：标题/副标题的文字来源、两档字号、
 * 右侧操作区是否参与布局，都由这里决定。
 */
describe('AdminPageHeader', () => {
  it('渲染标题与副标题', () => {
    const wrapper = mount(AdminPageHeader, {
      props: { title: '反馈管理', subtitle: '处理用户提交的反馈' },
    })

    expect(wrapper.find('header.admin-page-header').exists()).toBe(true)
    expect(wrapper.find('.page-title').text()).toContain('反馈管理')
    expect(wrapper.find('.page-subtitle').text()).toBe('处理用户提交的反馈')
  })

  it('没有副标题时不留下空段落', () => {
    const wrapper = mount(AdminPageHeader, { props: { title: '图片管理' } })

    expect(wrapper.find('.page-subtitle').exists()).toBe(false)
  })

  it('默认大号，md 切换小号', () => {
    expect(mount(AdminPageHeader, { props: { title: 'A' } }).classes()).toContain('size-lg')
    expect(mount(AdminPageHeader, { props: { title: 'A', size: 'md' } }).classes()).toContain(
      'size-md',
    )
  })

  it('只有给了操作区才标记 has-extra，且不额外包一层容器', () => {
    const plain = mount(AdminPageHeader, { props: { title: 'A' } })
    expect(plain.classes()).not.toContain('has-extra')

    const withExtra = mount(AdminPageHeader, {
      props: { title: 'A' },
      slots: { extra: '<div class="header-actions">操作</div>' },
    })
    expect(withExtra.classes()).toContain('has-extra')
    // 页面自己的操作区直接是 flex 子项，其样式（含响应式）继续由页面负责
    expect(withExtra.find('header > .header-actions').exists()).toBe(true)
  })

  it('图标插槽紧挨标题文字', () => {
    const wrapper = mount(AdminPageHeader, {
      props: { title: 'A' },
      slots: { icon: '<svg data-test="icon" />' },
    })

    const title = wrapper.find('.page-title')
    expect(title.find('[data-test="icon"]').exists()).toBe(true)
    expect(title.find('.title-text').text()).toBe('A')
  })
})
