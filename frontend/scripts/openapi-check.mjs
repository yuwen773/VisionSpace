// 由 scripts/openapi-contract.ps1 调用：用与 npm run openapi2ts 完全相同的生成配置，
// 但输出到 OPENAPI_SERVERS_PATH 指定的临时目录，避免 rimraf 掉入库的 src/api。
//
// 契约来自 OPENAPI_SCHEMA_PATH（@umijs/openapi 支持本地 JSON 文件），因此不再需要
// localhost:8081 上有活后端。
import { generateService } from '@umijs/openapi'
import { openApiConfig } from '../openapi.config.js'

const serversPath = process.env.OPENAPI_SERVERS_PATH
if (!serversPath) {
  console.error('OPENAPI_SERVERS_PATH is required')
  process.exit(2)
}

await generateService({
  ...openApiConfig,
  schemaPath: process.env.OPENAPI_SCHEMA_PATH || openApiConfig.schemaPath,
  serversPath,
})

// 与 npm run openapi2ts 一样补尾下划线别名并格式化，否则检查会因纯格式差异假失败。
const { execFileSync } = await import('node:child_process')
const { fileURLToPath } = await import('node:url')
const here = fileURLToPath(new URL('.', import.meta.url))
execFileSync(process.execPath, [fileURLToPath(new URL('./postgen-typings.mjs', import.meta.url))], {
  stdio: 'inherit',
  env: process.env,
})
execFileSync(
  process.execPath,
  [
    fileURLToPath(new URL('../node_modules/prettier/bin/prettier.cjs', import.meta.url)),
    '--log-level',
    'warn',
    // 临时目录不在 frontend/ 下，必须显式指定项目配置，否则会用 prettier 默认值。
    '--config',
    fileURLToPath(new URL('../.prettierrc.json', import.meta.url)),
    '--write',
    `${serversPath}/api`,
  ],
  { stdio: 'inherit', cwd: here },
)
