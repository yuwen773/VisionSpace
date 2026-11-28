import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import GlobalHeader from '@/components/GlobalHeader.vue'

/**
 * 冒烟测试：页头是全局唯一入口，任何一次改版都要保证
 * 品牌、主导航、未登录入口三块都还在。
 */
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/explore', component: { template: '<div />' } },
  ],
})

const mountHeader = (path = '/') =>
  router.push(path).then(() =>
    mount(GlobalHeader, {
      global: {
        plugins: [router],
        stubs: {
          'a-menu': true,
          'a-dropdown': true,
          'a-avatar': true,
          'a-drawer': true,
        },
      },
    }),
  )

describe('GlobalHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('渲染品牌、主导航与登录入口', async () => {
    const wrapper = await mountHeader()

    expect(wrapper.find('#cosmic-header').exists()).toBe(true)
    expect(wrapper.text()).toContain('VisionSpace')
    expect(wrapper.find('nav[aria-label="主导航"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('登录')
    expect(wrapper.text()).toContain('注册')
  })

  it('主题开关说明的是下一步动作', async () => {
    const wrapper = await mountHeader()
    const toggle = wrapper.find('.vs-mode')
    expect(toggle.exists()).toBe(true)
    expect(toggle.attributes('aria-label')).toBeTruthy()
  })
})
