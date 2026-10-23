/**
 * mxgraph npm 包没有自带类型声明。
 * 该包的 main 导出一个工厂函数：调用后返回包含 mxGraph/mxCodec/mxUtils 等
 * 类型的对象（见 javascript/dist/build.js 的 UMD 包装）。
 */
declare module 'mxgraph' {
  /** 调用后返回 mxGraph / mxCodec / mxUtils 等类型；具体接口由调用方收窄。 */
  type MxFactory = (options: Record<string, unknown>) => Record<string, unknown>
  const factory: MxFactory
  export default factory
}
