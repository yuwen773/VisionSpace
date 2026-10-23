/**
 * mxgraph npm 包没有自带类型声明。
 * 该包的 main 导出一个工厂函数：调用后返回包含 mxGraph/mxCodec/mxUtils 等
 * 类型的对象（见 javascript/dist/build.js 的 UMD 包装）。
 */
declare module 'mxgraph' {
  type MxFactory = (options: Record<string, unknown>) => Record<string, any>
  const factory: MxFactory
  export default factory
}
