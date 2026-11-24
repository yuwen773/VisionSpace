/**
 * 上传前的图片准备：压缩到 2MB 以内，并落到用户目录。
 *
 * 微信临时文件路径随时可能被回收，且后缀决定后端用哪种上传模板校验；
 * 因此压缩后统一复制成 `<user>/vs-upload-*.jpg|png`，上传结束后由页面清理。
 */
import {
  compressStepAt,
  fileExtension,
  hasAllowedExtension,
  isWithinUploadLimit,
  type CompressStep,
} from '../logic/upload'

export interface PreparedUpload {
  path: string
  bytes: number
}

/** 已经是允许格式且没超限的图片原样使用，避免二次压缩损失画质。 */
export async function prepareUploadFile(sourcePath: string): Promise<PreparedUpload> {
  const sourceBytes = await fileSize(sourcePath)
  if (hasAllowedExtension(sourcePath) && isWithinUploadLimit(sourceBytes)) {
    return { path: await persistFile(sourcePath, fileExtension(sourcePath)), bytes: sourceBytes }
  }
  return compressToLimit(sourcePath, 0)
}

async function compressToLimit(sourcePath: string, stepIndex: number): Promise<PreparedUpload> {
  const step = compressStepAt(stepIndex)
  if (step === null) {
    throw new Error('图片压缩后仍超过 2MB，请换一张')
  }
  let compressed: string
  try {
    compressed = await compressImage(sourcePath, step)
  } catch (error) {
    console.warn('图片压缩失败', error)
    throw new Error('图片处理失败，请换一张')
  }
  const bytes = await fileSize(compressed)
  if (isWithinUploadLimit(bytes)) {
    // compressImage 输出恒为 jpg（文档只保证对 jpg 生效），后缀以实际结果为准
    return { path: await persistFile(compressed, fileExtension(compressed) || '.jpg'), bytes }
  }
  // 每次都从原图重压：连续压缩会叠加画质损失，降档也降不干净
  return compressToLimit(sourcePath, stepIndex + 1)
}

function compressImage(src: string, step: CompressStep): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    wx.compressImage({
      src,
      quality: step.quality,
      ...(step.compressedWidth === undefined ? {} : { compressedWidth: step.compressedWidth }),
      success(result) {
        if (typeof result.tempFilePath === 'string' && result.tempFilePath !== '') {
          resolve(result.tempFilePath)
          return
        }
        reject(new Error('压缩结果为空'))
      },
      fail(error) {
        reject(error)
      },
    })
  })
}

function fileSize(path: string): Promise<number> {
  return new Promise<number>((resolve, reject) => {
    wx.getFileSystemManager().getFileInfo({
      filePath: path,
      success(result) {
        resolve(result.size)
      },
      fail() {
        reject(new Error('读取图片大小失败'))
      },
    })
  })
}

function persistFile(tempPath: string, extension: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const suffix = extension === '' ? '.jpg' : extension
    const target = `${wx.env.USER_DATA_PATH}/vs-upload-${Date.now()}-${Math.floor(
      Math.random() * 1e6,
    )}${suffix}`
    wx.getFileSystemManager().saveFile({
      tempFilePath: tempPath,
      filePath: target,
      success(result) {
        resolve(result.savedFilePath)
      },
      fail(error) {
        console.warn('保存待上传文件失败', error)
        reject(new Error('图片处理失败，请换一张'))
      },
    })
  })
}

/** 删除本页保存到用户目录的临时文件；临时/系统路径一律不碰。 */
export function releasePreparedFile(path: string): void {
  if (!path.startsWith(wx.env.USER_DATA_PATH)) {
    return
  }
  wx.getFileSystemManager().unlink({
    filePath: path,
    fail() {
      // 文件可能已被系统回收，忽略即可
    },
  })
}
