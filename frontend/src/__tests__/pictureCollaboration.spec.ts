import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ImageCropper from '@/components/ImageCropper.vue'

const { sockets } = vi.hoisted(() => ({
  sockets: [] as { handlers: Record<string, (data: any) => void>; sendMessage: ReturnType<typeof vi.fn> }[],
}))
vi.mock('@/stores/userLogin.ts', () => ({
  useLoginUserStore: () => ({ loginUser: { id: '101', userName: 'owner' } }),
}))
vi.mock('@/api/pictureController.ts', () => ({ uploadPictureUsingPost: vi.fn() }))
vi.mock('ant-design-vue', () => ({ message: { info: vi.fn(), error: vi.fn() } }))
vi.mock('@/utils/pictureEditWebSocket.ts', () => ({
  default: class {
    handlers: Record<string, (data: any) => void> = {}
    sendMessage = vi.fn()
    constructor() { sockets.push(this) }
    connect() {}
    disconnect() {}
    on(type: string, callback: (data: any) => void) { this.handlers[type] = callback }
  },
}))

describe('图片协作的连接编辑权', () => {
  let wrapper: ReturnType<typeof mount>
  beforeEach(async () => {
    sockets.length = 0
    wrapper = mount(ImageCropper, {
      props: { picture: { id: 1 }, space: { spaceType: 1 } },
      global: { stubs: { 'vue-cropper': true, teleport: true } },
    })
    ;(wrapper.vm as unknown as { openModal(): void }).openModal()
    await nextTick()
    sockets[0].handlers.INFO({ type: 'INFO', selfSessionId: 'tab-b', message: '连接已建立' })
  })
  afterEach(() => wrapper.unmount())

  const enter = (sessionId: string) => sockets[0].handlers.ENTER_EDIT({
    type: 'ENTER_EDIT', sessionId, user: { id: '101', userName: 'owner' }, message: '开始编辑',
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
