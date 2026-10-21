import { generateService } from '@umijs/openapi'

// schemaPath 指向本地后端（mvn spring-boot:run 后 http://localhost:8081/api/v3/api-docs）。
// 可用 OPENAPI_SCHEMA_PATH 覆盖，例如部署环境：
//   OPENAPI_SCHEMA_PATH=https://example.com/api/v3/api-docs npm run openapi2ts
generateService({
  requestLibPath: "import request from '@/request'",
  schemaPath: process.env.OPENAPI_SCHEMA_PATH || 'http://localhost:8081/api/v3/api-docs',
  serversPath: './src',
})
