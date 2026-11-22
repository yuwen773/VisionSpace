import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { Input } from 'ant-design-vue'
import AuthTextField from '../AuthTextField.vue'
import AuthSubmitButton from '../AuthSubmitButton.vue'

const mountField = (props: Record<string, unknown>, modelValue = '') =>
  mount(AuthTextField, {
    props: { id: 'account', label: '账号', modelValue, ...props },
    global: { components: { 'a-input': Input } },
  })

describe('认证表单输入行', () => {
  it('标签与控件用同一个 id 关联', () => {
    const wrapper = mountField({ placeholder: '输入账号' })
    expect(wrapper.get('label').attributes('for')).toBe('account')
    expect(wrapper.get('input').attributes('id')).toBe('account')
    expect(wrapper.get('label').text()).toBe('账号')
  })

  it('输入时把值交给父组件', async () => {
    const wrapper = mountField({})
    await wrapper.get('input').setValue('yuwen')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['yuwen'])
  })

  it('密码字段默认遮蔽，切换后露出明文', async () => {
    const wrapper = mountField({ type: 'password', label: '密码' })
    expect(wrapper.get('input').attributes('type')).toBe('password')

    const toggle = wrapper.get('button.field-toggle')
    expect(toggle.attributes('aria-label')).toBe('显示密码')
    expect(toggle.attributes('aria-pressed')).toBe('false')

    await toggle.trigger('click')
    expect(wrapper.get('input').attributes('type')).toBe('text')
    expect(toggle.attributes('aria-label')).toBe('隐藏密码')
    expect(toggle.attributes('aria-pressed')).toBe('true')
  })

  it('确认密码的无障碍文案带上字段名', () => {
    const wrapper = mountField({ type: 'password', label: '确认密码' })
    expect(wrapper.get('button.field-toggle').attributes('aria-label')).toBe('显示确认密码')
  })
})

describe('认证提交按钮', () => {
  it('提交中禁用并换成等待文案', async () => {
    const wrapper = mount(AuthSubmitButton, {
      props: { label: '登录', loadingLabel: '登录中' },
    })
    const button = wrapper.get('button')
    expect(button.attributes('type')).toBe('submit')
    expect(button.text()).toContain('登录')
    expect(button.attributes('disabled')).toBeUndefined()

    await wrapper.setProps({ loading: true })
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').text()).toContain('登录中')
  })
})
