import { beforeEach, describe, expect, it, vi } from 'vitest'
import { acquirePictureEditLease } from '@/utils/acquirePictureEditLease'

type FakeSocket = {
  handlers: Record<string, (value?: { message?: string; leaseToken?: string }) => void>
  sendMessage: ReturnType<typeof vi.fn>
  disconnect: ReturnType<typeof vi.fn>
}
const { sockets } = vi.hoisted(() => ({ sockets: [] as FakeSocket[] }))
vi.mock('@/utils/pictureEditWebSocket', () => ({
  default: class {
    handlers: FakeSocket['handlers'] = {}
    sendMessage = vi.fn()
    disconnect = vi.fn()
    constructor(public pictureId: number) {
      sockets.push(this)
    }
    on(type: string, handler: FakeSocket['handlers'][string]) {
      this.handlers[type] = handler
    }
    connect() {}
  },
}))

describe('团队图片替换租约', () => {
  beforeEach(() => {
    sockets.length = 0
  })

  it('只接受定向保存凭据并在完成后释放连接', async () => {
    const pending = acquirePictureEditLease(42)
    const socket = sockets[0]
    socket.handlers.open()
    expect(socket.sendMessage).toHaveBeenCalledWith({ type: 'ENTER_EDIT' })
    socket.handlers.INFO({ message: '已连接' })
    socket.handlers.INFO({ leaseToken: 'holder-only' })
    const lease = await pending
    expect(lease.token).toBe('holder-only')
    lease.release()
    expect(socket.disconnect).toHaveBeenCalledOnce()
  })

  it('竞争失败时释放连接并返回原因', async () => {
    const pending = acquirePictureEditLease(42)
    sockets[0].handlers.ERROR({ message: '其他用户正在编辑' })
    await expect(pending).rejects.toThrow('其他用户正在编辑')
    expect(sockets[0].disconnect).toHaveBeenCalledOnce()
  })
})
