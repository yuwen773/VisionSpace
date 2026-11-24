import { averageColor } from '../logic/color'

const SAMPLE_SIZE = 32

/**
 * 从相册/相机图片取平均色。
 *
 * 用离屏 canvas 缩到 32×32 再取平均，既快又能避开单像素噪点。
 * 部分旧机型不支持离屏 canvas 2d，失败时由调用方提示改用色卡。
 */
export function sampleImageColor(tempFilePath: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    try {
      const canvas = wx.createOffscreenCanvas({
        type: '2d',
        width: SAMPLE_SIZE,
        height: SAMPLE_SIZE,
      })
      const context = canvas.getContext('2d')
      const image = canvas.createImage()
      image.onload = () => {
        try {
          context.drawImage(image, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
          const { data } = context.getImageData(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
          const color = averageColor(data, SAMPLE_SIZE, SAMPLE_SIZE)
          if (color === null) {
            reject(new Error('未能识别图片颜色'))
            return
          }
          resolve(color)
        } catch (error) {
          console.warn('读取图片像素失败', error)
          reject(new Error('取色失败，请改用色卡'))
        }
      }
      image.onerror = () => reject(new Error('图片读取失败'))
      image.src = tempFilePath
    } catch (error) {
      console.warn('创建离屏画布失败', error)
      reject(new Error('当前设备不支持取色，请改用色卡'))
    }
  })
}

/** 选一张本地图片并返回其平均色。 */
export function pickImageColor(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      success(result) {
        const file = result.tempFiles[0]
        if (!file) {
          reject(new Error('没有选择图片'))
          return
        }
        sampleImageColor(file.tempFilePath).then(resolve, reject)
      },
      fail(error) {
        if (error.errMsg.includes('cancel')) {
          reject(new Error('已取消'))
          return
        }
        reject(new Error('选择图片失败'))
      },
    })
  })
}
