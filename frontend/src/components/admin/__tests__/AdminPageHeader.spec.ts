import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'

/**
 * 页头是六个管理页共用的唯一一份实现：标题/副标题的文字来源、两档字号、
 * 右侧操作区是否参与布局，都由这里决定。
 *
 * 页头的编号与分区代号取自当前路由，因此挂载时要带一个内存路由，
 * 与仓库里其它组件测试的做法一致。
 */
type HeaderProps = { title: string; subtitle?: string; size?: 'lg' | 'md' }

async function mountHeader(props: HeaderProps, slots?: Record<string, string>) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/feedback_manage', component: { template: '<div />' } }],
  })
  await router.push('/admin/feedback_manage')
  await router.isReady()
  return mount(AdminPageHeader, { props, slots, global: { plugins: [router] } })
}

describe('AdminPageHeader', () => {
  it('渲染标题与副标题', async () => {
    const wrapper = await mountHeader({ title: '反馈管理', subtitle: '处理用户提交的反馈' })

    expect(wrapper.find('header.admin-page-header').exists()).toBe(true)
    expect(wrapper.find('.page-title').text()).toContain('反馈管理')
    expect(wrapper.find('.page-subtitle').text()).toBe('处理用户提交的反馈')
  })

  it('没有副标题时不留下空段落', async () => {
    const wrapper = await mountHeader({ title: '图片管理' })

    expect(wrapper.find('.page-subtitle').exists()).toBe(false)
  })

  it('默认大号，md 切换小号', async () => {
    expect((await mountHeader({ title: 'A' })).classes()).toContain('size-lg')
    expect((await mountHeader({ title: 'A', size: 'md' })).classes()).toContain('size-md')
  })

  it('只有给了操作区才标记 has-extra，操作区落在 .header-extra 里', async () => {
    const plain = await mountHeader({ title: 'A' })
    expect(plain.classes()).not.toContain('has-extra')
    expect(plain.find('.header-extra').exists()).toBe(false)

    const withExtra = await mountHeader(
      { title: 'A' },
      { extra: '<div class="header-actions">操作</div>' },
    )
    expect(withExtra.classes()).toContain('has-extra')
    // 页面自己的操作区在这一块里排版，其样式（含响应式）继续由页面负责
    expect(withExtra.find('header > .header-extra > .header-actions').exists()).toBe(true)
  })

  it('图标插槽紧挨标题文字', async () => {
    const wrapper = await mountHeader({ title: 'A' }, { icon: '<svg data-test="icon" />' })

    const title = wrapper.find('.page-title')
    expect(title.find('[data-test="icon"]').exists()).toBe(true)
    expect(title.find('.title-text').text()).toBe('A')
  })

  it('编号与分区代号取自当前路由', async () => {
    const wrapper = await mountHeader({ title: '反馈管理' })

    expect(wrapper.find('.kicker-no').text()).toBe('06')
    expect(wrapper.find('.kicker-text').text()).toContain('FEEDBACK')
  })
})
