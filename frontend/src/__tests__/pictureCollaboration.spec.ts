import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ImageCropper from '@/components/ImageCropper.vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

const { sockets } = vi.hoisted(() => ({
  sockets: [] as {
    handlers: Record<string, (data: unknown) => void>
    sendMessage: ReturnType<typeof vi.fn>
  }[],
}))
vi.mock('@/stores/userLogin.ts', () => ({
  useLoginUserStore: () => ({ loginUser: { id: '101', userName: 'owner' } }),
}))
vi.mock('@/api/pictureController.ts', () => ({ uploadPictureUsingPost: vi.fn() }))
vi.mock('ant-design-vue', () => ({ message: { info: vi.fn(), error: vi.fn(), success: vi.fn() } }))
vi.mock('@/utils/pictureEditWebSocket.ts', () => ({
  default: class {
    handlers: Record<string, (data: unknown) => void> = {}
    sendMessage = vi.fn()
    constructor() {
      sockets.push(this)
    }
    connect() {}
    disconnect() {}
    on(type: string, callback: (data: unknown) => void) {
      this.handlers[type] = callback
    }
  },
}))

describe('图片协作的连接编辑权', () => {
  let wrapper: ReturnType<typeof mount>
  beforeEach(async () => {
    vi.clearAllMocks()
    sockets.length = 0
    wrapper = mount(ImageCropper, {
      props: { picture: { id: 1 }, space: { spaceType: 1 } },
      global: {
        stubs: {
          'vue-cropper': {
            template: '<div />',
            methods: {
              getCropBlob(callback: (blob: Blob) => void) {
                callback(new Blob(['image']))
              },
            },
          },
          teleport: true,
        },
      },
    })
    ;(wrapper.vm as unknown as { openModal(): void }).openModal()
    await nextTick()
    sockets[0].handlers.INFO({ type: 'INFO', selfSessionId: 'tab-b', message: '连接已建立' })
  })
  afterEach(() => wrapper.unmount())

  const enter = (sessionId: string) => {
    if (sessionId === 'tab-b')
      sockets[0].handlers.INFO({ type: 'INFO', leaseToken: 'private-token' })
    sockets[0].handlers.ENTER_EDIT({
      type: 'ENTER_EDIT',
      sessionId,
      user: { id: '101', userName: 'owner' },
      message: '开始编辑',
    })
  }

  it('没有当前连接的保存凭据时不能编辑', async () => {
    sockets[0].handlers.ENTER_EDIT({ type: 'ENTER_EDIT', sessionId: 'tab-b', user: { id: '101' } })
    await nextTick()
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeDefined()
  })

  it('裁剪保存使用扁平表单发送图片标识和本连接凭据', async () => {
    vi.mocked(uploadPictureUsingPost).mockResolvedValue({
      data: { code: 0, data: { id: 1 } },
    } as never)
    enter('tab-b')
    await nextTick()
    await wrapper.get('.modal-footer .action-btn.primary').trigger('click')
    const call = vi.mocked(uploadPictureUsingPost).mock.calls.at(-1)
    expect(call).toBeDefined()
    const form = call?.[2]?.data as FormData
    expect(form.get('id')).toBe('1')
    expect(form.get('collaborationToken')).toBe('private-token')
    expect(form.get('file')).toBeInstanceOf(File)
  })

  it('保存因租约失效被拒绝时显示服务端原因', async () => {
    vi.mocked(uploadPictureUsingPost).mockResolvedValue({
      data: { code: 40101, message: '协作编辑权已失效' },
    } as never)
    enter('tab-b')
    await nextTick()
    await wrapper.get('.modal-footer .action-btn.primary').trigger('click')
    await nextTick()
    expect(message.error).toHaveBeenCalledWith('协作编辑权已失效')
  })

  it('同用户另一标签持有租约时，本标签的工具保持禁用', async () => {
    enter('tab-a')
    await nextTick()
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.collab-actions .danger').exists()).toBe(false)
    expect(wrapper.get('.collab-status').text()).toContain('owner 正在编辑')
  })

  it('竞争拒绝保留其他连接的持有者提示', async () => {
    enter('tab-a')
    sockets[0].handlers.ERROR({ type: 'ERROR', message: '其他用户正在编辑' })
    await nextTick()
    expect(wrapper.get('.collab-status').text()).toContain('owner 正在编辑')
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeDefined()
  })

  it('仅本连接获得租约时启用工具，失去编辑权后立即禁用', async () => {
    enter('tab-b')
    await nextTick()
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeUndefined()
    sockets[0].handlers.ERROR({ type: 'ERROR', message: '登录会话已失效', leaseLost: true })
    await nextTick()
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeDefined()
  })

  it('背压或非法动作错误不撤销仍有效的编辑权', async () => {
    enter('tab-b')
    sockets[0].handlers.ERROR({ type: 'ERROR', message: '协作消息过多，请稍后重试' })
    await nextTick()
    expect(wrapper.get('button[title="放大"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('.collab-actions .danger').exists()).toBe(true)
  })
})
