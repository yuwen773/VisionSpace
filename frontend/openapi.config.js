import { generateService } from '@umijs/openapi'
import { pathToFileURL } from 'node:url'

// schemaPath 指向本地后端（mvn spring-boot:run 后 http://localhost:8081/api/v3/api-docs）。
// 可用 OPENAPI_SCHEMA_PATH 覆盖，例如部署环境：
//   OPENAPI_SCHEMA_PATH=https://example.com/api/v3/api-docs npm run openapi2ts
//
// ⚠️ 生成器会 rimraf 整个 serversPath/api 目录，手写客户端放在 src/api-custom/。
//
// 命名对齐：库内既有产物（由旧版生成器产出）使用 `xxxUsingPost` 函数名、
// `BaseResponseXxx_` 类型名、`mcpController.ts` 文件名。直接升级生成器会一次性
// 改掉 45 个文件的 230 处引用，所以这里用 hook 复刻旧命名：
//   1. 函数名/类型名 = operationId + HTTP 方法后缀（springdoc 不给 operationId 加后缀）
//   2. operationId 里的 VO 还原成 Vo（旧产物是 getPictureVoByIdUsingGet）
//   3. tag 改写为 kebab-case，使文件名落到 mcpController.ts / pictureAction.ts 等

const METHOD_SUFFIX = {
  get: 'UsingGet',
  post: 'UsingPost',
  put: 'UsingPut',
  delete: 'UsingDelete',
  patch: 'UsingPatch',
}

/** 旧产物把 VO 写作 Vo，与当前 springdoc 的 operationId 不一致 */
const normalizeVo = (name) => name.replace(/VO/g, 'Vo')

/**
 * tag -> 文件名。生成器对 tag 再做一次 camelCase，所以这里用 kebab-case 输入。
 * 未列出的 tag 走默认规则：去掉 -controller 后缀并转小驼峰。
 */
const TAG_RENAME = {
  'mcp-server-controller': 'mcp-controller',
  'picture-action-controller': 'picture-action',
  'picture-recommend-controller': 'picture-recommend',
  'admin-stats-controller': 'admin-stats-controller',
  'space-analyze-controller': 'space-analyze-controller',
  'space-user-controller': 'space-user-controller',
  'storage-config-controller': 'storage-config-controller',
  'agent-image-upload-controller': 'agent-image-upload-controller',
  'agent-attachment-controller': 'agent-attachment-controller',
}

// springdoc 输出对象属性的顺序不稳定，生成器又按 spec 顺序写字段，
// 导致同样的契约两次生成出不同文件。排序后再生成，产物才可逐字节对账。
const sortKeys = (value) => {
  if (Array.isArray(value)) return value.map(sortKeys)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, sortKeys(value[key])]),
    )
  }
  return value
}

const renameTags = (spec) => {
  for (const pathItem of Object.values(spec.paths || {})) {
    for (const operation of Object.values(pathItem)) {
      if (operation && Array.isArray(operation.tags)) {
        operation.tags = operation.tags.map((tag) => TAG_RENAME[tag] || tag)
      }
    }
  }
  return sortKeys(spec)
}

const legacyName = (data) =>
  normalizeVo(data.operationId) + (METHOD_SUFFIX[data.method.toLowerCase()] || '')

// 生成配置单独导出，scripts/openapi-check.mjs 复用同一份，避免检查与生成规则漂移。
export const openApiConfig = {
  requestLibPath: "import request from '@/request'",
  schemaPath: process.env.OPENAPI_SCHEMA_PATH || 'http://localhost:8081/api/v3/api-docs',
  serversPath: './src',
  hook: {
    afterOpenApiDataInited: renameTags,
    customFunctionName: legacyName,
    customTypeName: legacyName,
  },
}

// 直接执行（npm run openapi2ts）时才写盘；被 import 时不产生副作用。
const entry = process.argv[1] ? pathToFileURL(process.argv[1]).href : ''
if (entry === import.meta.url) {
  generateService(openApiConfig)
}
