import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EditorialAtmosphere from '@/components/EditorialAtmosphere.vue'

/**
 * 环境层是三个版面的同一层东西：固定底层 + 光斑 + 网格 + 噪点（+ 暗角）。
 * 这里锁住的正是此前三份实现之间不一致的地方——有几个光斑、有没有暗角、浅色怎么开。
 */
describe('EditorialAtmosphere', () => {
  it('展厅版两枚光斑、没有暗角', () => {
    const wrapper = mount(EditorialAtmosphere, { props: { variant: 'award' } })

    expect(wrapper.classes()).toContain('is-award')
    expect(wrapper.findAll('.aw-aurora')).toHaveLength(2)
    expect(wrapper.find('.aw-vignette').exists()).toBe(false)
  })

  it('落地页与探索页三枚光斑带暗角', () => {
    for (const variant of ['landing', 'explore'] as const) {
      const wrapper = mount(EditorialAtmosphere, { props: { variant } })

      expect(wrapper.classes()).toContain(`is-${variant}`)
      expect(wrapper.findAll('.aw-aurora')).toHaveLength(3)
      expect(wrapper.find('.aw-vignette').exists()).toBe(true)
    }
  })

  it('浅色纸面由 light 属性显式开启，而不是靠祖先页面上的类', () => {
    const dark = mount(EditorialAtmosphere, { props: { variant: 'landing' } })
    expect(dark.classes()).not.toContain('is-light')

    const light = mount(EditorialAtmosphere, {
      props: { variant: 'landing', light: true },
    })
    expect(light.classes()).toContain('is-light')
  })

  it('对辅助技术隐藏，且不吃掉指针事件', () => {
    const wrapper = mount(EditorialAtmosphere, { props: { variant: 'award' } })

    expect(wrapper.attributes('aria-hidden')).toBe('true')
  })
})
