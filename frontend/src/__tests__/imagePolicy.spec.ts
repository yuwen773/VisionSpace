import { describe, it, expect, afterEach, vi } from 'vitest'
import { isLoadableImage } from '@/utils/imagePolicy'
import { normalizeOrigin, parseOriginList } from '@/utils/hostList'
import { buildCsp } from '@/utils/csp'

/**
 * 受控图片源：配置写法、CSP 与运行期策略必须是同一套语义。
 *
 * 审查发现的不一致：`.env.example` 写的是完整 origin（`https://cdn.internal.example`），
 * 而运行期按 `hostname` 比较，导致文档里的写法永远匹配不上——
 * 配置了受控图床却一张图都加载不出来，使用者只能放宽策略绕过。
 */
const ENV_KEY = 'VITE_CSP_IMAGE_HOSTS'

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('normalizeOrigin', () => {
  it('接受完整 origin，并丢弃路径与末尾斜杠', () => {
    expect(normalizeOrigin('https://cdn.internal.example')).toBe('https://cdn.internal.example')
    expect(normalizeOrigin('https://cdn.internal.example/')).toBe('https://cdn.internal.example')
    expect(normalizeOrigin('https://cdn.internal.example/a/b?c=1')).toBe(
      'https://cdn.internal.example',
    )
  })

  it('保留非默认端口——它是 origin 的一部分', () => {
    expect(normalizeOrigin('https://cdn.internal.example:8443/x')).toBe(
      'https://cdn.internal.example:8443',
    )
    // 显式默认端口与省略写法等价
    expect(normalizeOrigin('https://cdn.internal.example:443')).toBe('https://cdn.internal.example')
  })

  it('裸主机名不猜协议，直接判为非法', () => {
    expect(normalizeOrigin('cdn.internal.example')).toBeNull()
    expect(normalizeOrigin('not-a-url')).toBeNull()
  })

  it('非 http(s) 协议与空值判为非法', () => {
    expect(normalizeOrigin('javascript:alert(1)')).toBeNull()
    expect(normalizeOrigin('ftp://cdn.internal.example')).toBeNull()
    expect(normalizeOrigin('')).toBeNull()
    expect(normalizeOrigin(undefined)).toBeNull()
  })

  it('清单解析会丢弃非法项并合并重复项', () => {
    expect(
      parseOriginList(
        'https://a.internal.example, cdn.b.example, https://a.internal.example/, https://b.internal.example',
      ),
    ).toEqual(['https://a.internal.example', 'https://b.internal.example'])
  })
})

describe('isLoadableImage：文档里的写法必须真的生效', () => {
  it('配置成完整 origin 时，该源的图片可以加载', () => {
    vi.stubEnv(ENV_KEY, 'https://cdn.internal.example')
    expect(isLoadableImage('https://cdn.internal.example/photo.png')).toBe(true)
    expect(isLoadableImage('https://cdn.internal.example')).toBe(true)
  })

  it('带端口的源按 origin 匹配', () => {
    vi.stubEnv(ENV_KEY, 'https://cdn.internal.example:8443')
    expect(isLoadableImage('https://cdn.internal.example:8443/a.png')).toBe(true)
    // 端口不同就是不同 origin
    expect(isLoadableImage('https://cdn.internal.example/a.png')).toBe(false)
  })

  it('协议不同就是不同 origin', () => {
    vi.stubEnv(ENV_KEY, 'https://cdn.internal.example')
    expect(isLoadableImage('http://cdn.internal.example/a.png')).toBe(false)
  })

  it('未配置的第三方主机一律不加载', () => {
    vi.stubEnv(ENV_KEY, '')
    expect(isLoadableImage('https://evil.example/a.png')).toBe(false)
    expect(isLoadableImage('https://images.unsplash.com/a.png')).toBe(false)
  })

  it('配置里混入非法项时只丢弃该项，不放宽成通配', () => {
    vi.stubEnv(ENV_KEY, 'cdn.internal.example, https://ok.internal.example')
    expect(isLoadableImage('https://cdn.internal.example/a.png')).toBe(false)
    expect(isLoadableImage('https://ok.internal.example/a.png')).toBe(true)
    expect(isLoadableImage('https://anything.example/a.png')).toBe(false)
  })

  it('站内地址与本地引用照常可用', () => {
    vi.stubEnv(ENV_KEY, '')
    expect(isLoadableImage('/assets/a.png')).toBe(true)
    expect(isLoadableImage('./a.png')).toBe(true)
    // 裸相对路径同样由浏览器按当前文档解析，属于同源
    expect(isLoadableImage('a.png')).toBe(true)
    expect(isLoadableImage('images/a.png')).toBe(true)
    expect(isLoadableImage('data:image/png;base64,AAAA')).toBe(true)
    expect(isLoadableImage(`${window.location.origin}/api/picture/1`)).toBe(true)
  })

  it('协议相对地址不能冒充本地路径', () => {
    vi.stubEnv(ENV_KEY, '')
    expect(isLoadableImage('//evil.example/a.png')).toBe(false)
  })

  it('反斜杠伪装的站内路径不能通过（浏览器把 \\ 当 /）', () => {
    vi.stubEnv(ENV_KEY, '')
    // `/\evil.example/a.png` 字面上以 / 开头，但 URL 解析器对 http(s) 会把
    // `\` 归一成 `/`，实际请求发往 evil.example。
    expect(isLoadableImage('/\\evil.example/a.png')).toBe(false)
    expect(isLoadableImage('\\\\evil.example/a.png')).toBe(false)
  })

  it('前缀伪装不能通过（origin 是完整比较，不是 startsWith）', () => {
    vi.stubEnv(ENV_KEY, 'https://cdn.internal.example')
    expect(isLoadableImage('https://cdn.internal.example.evil.example/a.png')).toBe(false)
    expect(isLoadableImage('https://cdn.internal.example@evil.example/a.png')).toBe(false)
  })
})

describe('CSP 与运行期策略对同一份配置给出同样的结论', () => {
  it('配置里的源同时出现在 img-src 里', () => {
    vi.stubEnv(ENV_KEY, 'https://cdn.internal.example, https://cdn2.internal.example:8443')
    const csp = buildCsp({ imageHosts: import.meta.env.VITE_CSP_IMAGE_HOSTS })
    expect(csp).toContain('https://cdn.internal.example')
    expect(csp).toContain('https://cdn2.internal.example:8443')
  })

  it('非法配置不会被 CSP 放行', () => {
    const csp = buildCsp({ imageHosts: 'cdn.internal.example, *' })
    expect(csp).not.toContain('cdn.internal.example')
    expect(csp).not.toMatch(/img-src[^;]*\*/)
  })
})
