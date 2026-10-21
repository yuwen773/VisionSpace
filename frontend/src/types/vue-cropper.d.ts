/**
 * vue-cropper 1.1.4 的 typings 入口（lib/typings/index.d.ts）反向 import 了
 * 未随包发布的 lib/vue-cropper.vue 类型，导致 vue-tsc 报 TS7016。
 * 这里按实际用法（main.ts 里 app.use 注册 + ImageCropper 里的 <vue-cropper>）
 * 声明最小接口。
 */
declare module 'vue-cropper' {
  import type { Plugin } from 'vue'

  const VueCropper: Plugin
  export default VueCropper
}
