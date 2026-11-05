import PictureEditWebSocket, { type PictureEditMessage } from './pictureEditWebSocket'

/** 为团队图片的一次替换取得连接专属租约；调用方完成后必须释放。 */
export function acquirePictureEditLease(
  pictureId: number,
): Promise<{ token: string; release: () => void }> {
  return new Promise((resolve, reject) => {
    const socket = new PictureEditWebSocket(pictureId)
    let finished = false
    const timer = window.setTimeout(() => fail('获取编辑权超时'), 10000)
    function fail(reason: string) {
      if (finished) return
      finished = true
      window.clearTimeout(timer)
      socket.disconnect()
      reject(new Error(reason))
    }
    socket.on('open', () => socket.sendMessage({ type: 'ENTER_EDIT' }))
    socket.on<PictureEditMessage>('INFO', (message) => {
      if (finished || !message?.leaseToken) return
      finished = true
      window.clearTimeout(timer)
      resolve({ token: message.leaseToken, release: () => socket.disconnect() })
    })
    socket.on<PictureEditMessage>('ERROR', (message) => fail(message?.message || '无法获取编辑权'))
    socket.on('close', () => fail('协作连接已关闭'))
    socket.connect()
  })
}
