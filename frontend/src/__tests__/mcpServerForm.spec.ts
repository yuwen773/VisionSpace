import { describe, expect, it } from 'vitest'
import { config, shallowMount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import McpServerForm from '@/components/agent/settings/McpServerForm.vue'

// 只验证能力开关如何影响模板与默认类型，不需要真实的 Ant Design 组件：
// 全局不注册 Antd，未解析的 a-* 标签会原样渲染，正好可以直接断言属性。
config.global.renderStubDefaultSlot = true

const textareaStub = defineComponent({
  props: { value: { type: String, default: '' } },
  setup: (props) => () => h('textarea', { value: props.value }),
})

function mountForm(props: { mode: 'create'; initMode?: 'form' | 'json'; stdioDisabled: boolean }) {
  return shallowMount(McpServerForm, {
    props,
    global: { stubs: { 'a-textarea': textareaStub } },
  })
}

describe('MCP 服务表单与部署能力一致', () => {
  it('STDIO 被禁用时 JSON 模板不再示例本地进程', async () => {
    const wrapper = mountForm({ mode: 'create', initMode: 'json', stdioDisabled: true })
    await nextTick()
    const template = (wrapper.find('textarea').element as HTMLTextAreaElement).value

    expect(template).toContain('"sse"')
    expect(template).not.toContain('"stdio"')
    expect(template).not.toContain('"command"')
    wrapper.unmount()
  })

  it('STDIO 可用时保留原有模板', async () => {
    const wrapper = mountForm({ mode: 'create', initMode: 'json', stdioDisabled: false })
    await nextTick()
    const template = (wrapper.find('textarea').element as HTMLTextAreaElement).value

    expect(template).toContain('"stdio"')
    expect(template).toContain('"command"')
    wrapper.unmount()
  })

  it('STDIO 被禁用时默认落到远程类型，不展示本地进程字段', () => {
    const wrapper = mountForm({ mode: 'create', stdioDisabled: true })
    const labels = wrapper.findAll('a-form-item').map((item) => item.attributes('label'))

    expect(wrapper.text()).toContain('当前部署不支持 STDIO')
    expect(labels).toContain('URL')
    // 本地进程的「命令 / 参数」字段不应出现
    expect(labels).not.toContain('命令')
    expect(labels).not.toContain('参数')
    wrapper.unmount()
  })
})
