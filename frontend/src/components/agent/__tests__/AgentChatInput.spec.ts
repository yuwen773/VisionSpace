import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import AgentChatInput from '@/components/agent/AgentChatInput.vue'

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

const uploadAgentImage = vi.fn()
const deleteAgentImage = vi.fn()
vi.mock('@/api-custom/agent', () => ({
  uploadAgentImage: (file: File) => uploadAgentImage(file),
  deleteAgentImage: (id: string | number) => deleteAgentImage(id),
}))

const pngFile = (name = 'a.png', size = 1024) =>
  new File([new Uint8Array(size)], name, { type: 'image/png' })

const selectFiles = async (wrapper: ReturnType<typeof mount>, files: File[]) => {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

describe('AgentChatInput 的附件状态机', () => {
  beforeEach(() => {
    uploadAgentImage.mockReset()
    deleteAgentImage.mockReset()
    uploadAgentImage.mockResolvedValue({ resourceId: 'r1', url: 'https://cdn.invalid/a.png' })
    deleteAgentImage.mockResolvedValue(undefined)
    if (typeof URL.createObjectURL !== 'function') {
      URL.createObjectURL = vi.fn(() => 'blob:mock')
      URL.revokeObjectURL = vi.fn()
    }
  })

  const mountInput = (props: Record<string, unknown> = {}) =>
    mount(AgentChatInput, {
      props: { sessionId: 's1', ...props },
      global: { stubs: { ImagePreview: true } },
    })

  it('选完文件立即逐张上传，并显示完成状态', async () => {
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])

    expect(uploadAgentImage).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.attachment-card')).toHaveLength(1)
    expect(wrapper.find('.attachment-card.done').exists()).toBe(true)
  })

  it('拒绝后端不支持的格式与超过 2MB 的图片', async () => {
    const wrapper = mountInput()
    await selectFiles(wrapper, [
      new File(['x'], 'a.pdf', { type: 'application/pdf' }),
      pngFile('big.png', 3 * 1024 * 1024),
    ])

    expect(uploadAgentImage).not.toHaveBeenCalled()
    expect(wrapper.findAll('.attachment-card')).toHaveLength(0)
  })

  it('发送时带上已上传的资源标识，而不是 File', async () => {
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])
    await wrapper.find('textarea').setValue('看看这张图')
    await wrapper.find('.send-btn').trigger('click')

    const emitted = wrapper.emitted('send')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0]).toBe('看看这张图')
    expect(emitted![0][1]).toEqual([{ resourceId: 'r1', url: 'https://cdn.invalid/a.png' }])
  })

  it('上传失败可单独重试，且上传中/失败时不允许发送', async () => {
    uploadAgentImage.mockRejectedValueOnce(new Error('网络错误'))
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])
    await wrapper.find('textarea').setValue('看看')

    expect(wrapper.find('.attachment-card.error').exists()).toBe(true)
    await wrapper.find('.send-btn').trigger('click')
    expect(wrapper.emitted('send')).toBeFalsy()

    await wrapper.find('.retry-badge').trigger('click')
    await flushPromises()
    expect(wrapper.find('.attachment-card.done').exists()).toBe(true)
  })

  it('拖拽文件走与选择文件相同的入口', async () => {
    const wrapper = mountInput()
    await wrapper.trigger('drop', {
      dataTransfer: { files: [pngFile()], types: ['Files'] },
    })
    await flushPromises()

    expect(uploadAgentImage).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.attachment-card')).toHaveLength(1)
  })

  it('草稿按会话隔离，切走再切回能恢复', async () => {
    const wrapper = mountInput()
    await wrapper.find('textarea').setValue('会话 A 的草稿')

    await wrapper.setProps({ sessionId: 's2' })
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')

    await wrapper.find('textarea').setValue('会话 B 的草稿')
    await wrapper.setProps({ sessionId: 's1' })
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('会话 A 的草稿')
  })

  it('站内找图模式下禁用附件入口', async () => {
    const wrapper = mountInput({ attachmentsEnabled: false })
    await selectFiles(wrapper, [pngFile()])

    expect(uploadAgentImage).not.toHaveBeenCalled()
    expect(wrapper.find('.attach-btn').attributes('disabled')).toBeDefined()
  })

  it('移除已上传但未发送的附件会回收资源', async () => {
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])

    await wrapper.find('.remove-badge').trigger('click')
    expect(deleteAgentImage).toHaveBeenCalledWith('r1')
  })

  it('后台请求进行中时禁止发送，草稿原样保留', async () => {
    const wrapper = mountInput({ busy: true })
    await wrapper.find('textarea').setValue('这句话不能丢')

    expect(wrapper.find('.send-btn').attributes('disabled')).toBeDefined()
    await wrapper.find('.send-btn').trigger('click')

    expect(wrapper.emitted('send')).toBeFalsy()
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('这句话不能丢')
  })

  it('上传期间切走会话，完成后仍按附件自身状态落位', async () => {
    let resolveUpload: (value: unknown) => void = () => {}
    uploadAgentImage.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveUpload = resolve
      }),
    )
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])
    expect(wrapper.find('.attachment-card.uploading').exists()).toBe(true)

    await wrapper.setProps({ sessionId: 's2' })
    resolveUpload({ resourceId: 'r1', url: 'https://cdn.invalid/a.png' })
    await flushPromises()

    await wrapper.setProps({ sessionId: 's1' })
    expect(wrapper.find('.attachment-card.done').exists()).toBe(true)
  })

  it('上传期间移除附件，完成后回收刚创建的资源', async () => {
    let resolveUpload: (value: unknown) => void = () => {}
    uploadAgentImage.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveUpload = resolve
      }),
    )
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])

    await wrapper.find('.remove-badge').trigger('click')
    resolveUpload({ resourceId: 'r-late', url: 'https://cdn.invalid/late.png' })
    await flushPromises()

    expect(deleteAgentImage).toHaveBeenCalledWith('r-late')
  })

  it('离开页面时回收所有未发送的已上传资源', async () => {
    const wrapper = mountInput()
    await selectFiles(wrapper, [pngFile()])
    // 切到另一个会话：已上传的附件留在 s1 的草稿里
    await wrapper.setProps({ sessionId: 's2' })

    wrapper.unmount()

    expect(deleteAgentImage).toHaveBeenCalledTimes(1)
    expect(deleteAgentImage).toHaveBeenCalledWith('r1')
  })
})
