/**
 * 在构建与开发阶段把一条策略写进 index.html 的 <meta http-equiv>。
 *
 * 为什么要从环境变量生成而不是写死：CSP 必须收紧到"只允许部署实际需要的目标"，
 * 而 PlantUML 自托管地址是部署期配置。写死会让两种环境各自漂移。
 *
 * 为什么用构建期注入而不是服务端响应头：本项目前端是静态产物，
 * 部署脚本不带响应头时，meta 仍然生效（见 README 的部署说明）。
 */
import type { Plugin } from 'vite'
import { parseHostList } from './hostList'

export type CspOptions = {
  /** 受控 PlantUML 自托管服务地址；留空则不允许任何外部图片目标 */
  plantumlServer?: string
  /** 额外允许的图片主机（例如受控对象存储域名），逗号分隔 */
  imageHosts?: string
}

/** 默认保留既有字体来源，避免 CSP 导致排版回退。 */
const DEFAULT_FONT_STYLE_ORIGINS = [
  'https://fonts.googleapis.cn',
  'https://fonts.googleapis.com',
  'https://fonts.gstatic.cn',
]
const DEFAULT_FONT_FILE_ORIGINS = ['https://fonts.gstatic.com', 'https://fonts.gstatic.cn']

/**
 * 首页展示用的既有装饰图源（LandingPage / DomeGallery / RollingGallery）。
 * 它们与对话内容无关：由组件常量直接给出，不来自 LLM 输出。
 * 对话正文里的图片仍受 VITE_CSP_IMAGE_HOSTS 约束，见 imagePolicy。
 */
const EXISTING_DECOR_IMAGE_ORIGINS = [
  'https://images.unsplash.com',
  'https://plus.unsplash.com',
  'https://picsum.photos',
  // picsum 会把请求重定向到 CDN 主机，图片本身仍是同一张
  'https://fastly.picsum.photos',
  'https://pbs.twimg.com',
]

function originOf(raw: string | undefined): string | null {
  if (!raw) return null
  try {
    const u = new URL(raw.trim())
    if (u.protocol !== 'https:' && u.protocol !== 'http:') return null
    return u.origin
  } catch {
    return null
  }
}

export function buildCsp(options: CspOptions = {}): string {
  const plantuml = originOf(options.plantumlServer)
  const extraImages = parseHostList(options.imageHosts)

  const imgSrc = ["'self'", 'data:', 'blob:', ...EXISTING_DECOR_IMAGE_ORIGINS, ...extraImages]
  if (plantuml) imgSrc.push(plantuml)

  // connect-src 只放同源：SSE/上传/协作都走同源 /api
  const directives = [
    "default-src 'self'",
    // 内联样式是 Vue Scoped CSS 与 Mermaid 产物所需；脚本不允许内联与求值
    // 字体样式表只携带字体名，与对话内容无关，保留以避免排版回退
    "script-src 'self'",
    `style-src 'self' 'unsafe-inline' ${DEFAULT_FONT_STYLE_ORIGINS.join(' ')}`,
    `img-src ${imgSrc.join(' ')}`,
    `font-src 'self' data: ${DEFAULT_FONT_FILE_ORIGINS.join(' ')}`,
    "connect-src 'self'",
    "object-src 'none'",
    "frame-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "worker-src 'self' blob:",
  ]
  return directives.join('; ')
}

export function cspPlugin(options: CspOptions = {}): Plugin {
  const csp = buildCsp(options)
  return {
    name: 'visionspace-csp',
    transformIndexHtml() {
      return [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: csp },
          injectTo: 'head-prepend',
        },
      ]
    },
  }
}
