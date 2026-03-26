import { generateService } from '@umijs/openapi'

generateService({
  requestLibPath: "import request from '@/request'",
  schemaPath: 'http://121.199.77.168:8090/api/v2/api-docs',
  serversPath: './src',
})
// npm install
