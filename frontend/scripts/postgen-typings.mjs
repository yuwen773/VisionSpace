// openapi2ts 的生成后补丁。
//
// 生成器（@umijs/openapi 1.14.1）不给类型名加尾下划线，而库内既有代码有 51 处
// 引用 `API.BaseResponseBoolean_` 这种旧写法。这个脚本补上别名，避免为了命名
// 风格去改一大片业务代码。
//
// 别名是纯转发（`type X_ = X`），不引入第二份定义，因此不会产生类型漂移。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
// OPENAPI_SERVERS_PATH 让契约检查脚本把别名补丁也写到临时目录，与正式生成走同一套规则。
const serversRoot = process.env.OPENAPI_SERVERS_PATH
  ? path.resolve(process.env.OPENAPI_SERVERS_PATH)
  : path.resolve(here, '../src')
const typingsPath = path.join(serversRoot, 'api/typings.d.ts')

/** 需要补尾下划线别名的基础类型（旧生成器会产出带下划线的名字） */
const ALIAS_TARGETS = [
  'BaseResponseAdminDashboardStatsVO',
  'BaseResponseBoolean',
  'BaseResponseCreateOutPaintingTaskResponse',
  'BaseResponseFeedbackVO',
  'BaseResponseGetOutPaintingTaskResponse',
  'BaseResponseInteger',
  'BaseResponseListImageSearchResult',
  'BaseResponseListPictureVO',
  'BaseResponseListSpace',
  'BaseResponseListSpaceCategoryAnalyzeResponse',
  'BaseResponseListSpaceLevel',
  'BaseResponseListSpaceSizeAnalyzeResponse',
  'BaseResponseListSpaceTagAnalyzeResponse',
  'BaseResponseListSpaceUserAnalyzeResponse',
  'BaseResponseListSpaceUserVO',
  'BaseResponseLoginUserVO',
  'BaseResponseLong',
  'BaseResponsePageFeedbackVO',
  'BaseResponsePagePicture',
  'BaseResponsePagePictureVO',
  'BaseResponsePageSpace',
  'BaseResponsePageSpaceVO',
  'BaseResponsePageUserVO',
  'BaseResponsePicture',
  'BaseResponsePictureTagCategory',
  'BaseResponsePictureVO',
  'BaseResponseSpace',
  'BaseResponseSpaceUsageAnalyzeResponse',
  'BaseResponseSpaceUser',
  'BaseResponseSpaceVO',
  'BaseResponseString',
  'BaseResponseUser',
  'BaseResponseUserPictureStatsResponse',
  'BaseResponseUserVO',
  'PageFeedbackVO',
  'PagePicture',
  'PagePictureVO',
  'PageSpace',
  'PageSpaceVO',
  'PageUserVO',
]

const MARKER = '// === 生成后补丁：尾下划线别名（scripts/postgen-typings.mjs） ==='

let source = fs.readFileSync(typingsPath, 'utf8')

// 幂等：重复运行先去掉上一轮的补丁段
const markerIndex = source.indexOf(MARKER)
if (markerIndex !== -1) {
  source = source.slice(0, markerIndex).trimEnd() + '\n'
}

const declared = new Set(
  [...source.matchAll(/^ {2}type ([A-Za-z0-9_]+) =/gm)].map((match) => match[1])
)

const aliases = ALIAS_TARGETS.filter((name) => declared.has(name))
  .map((name) => `  type ${name}_ = ${name}`)

const skipped = ALIAS_TARGETS.filter((name) => !declared.has(name))

if (aliases.length > 0) {
  const closingIndex = source.lastIndexOf('}')
  if (closingIndex === -1) {
    throw new Error('typings.d.ts 结构异常：找不到命名空间结尾')
  }
  const block = ['', `  ${MARKER}`, ...aliases, ''].join('\n')
  source = source.slice(0, closingIndex) + block + source.slice(closingIndex)
  fs.writeFileSync(typingsPath, source, 'utf8')
}

console.log(`[postgen] 补充 ${aliases.length} 个尾下划线别名`)
if (skipped.length > 0) {
  console.log(`[postgen] 跳过（spec 中已无对应类型）: ${skipped.join(', ')}`)
}
