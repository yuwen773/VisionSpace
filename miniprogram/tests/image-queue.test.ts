import { describe, expect, it } from 'vitest'
import { ImageQueue } from '../src/logic/image-queue'

/** 可控的下载桩：手动决定每个 URL 何时完成。 */
function createFetcher() {
  const resolvers = new Map<string, (path: string) => void>()
  const rejecters = new Map<string, (error: Error) => void>()
  const started: string[] = []
  return {
    started,
    resolvers,
    rejecters,
    fetch: (url: string) => {
      started.push(url)
      return new Promise<string>((resolve, reject) => {
        resolvers.set(url, resolve)
        rejecters.set(url, reject)
      })
    },
  }
}

/** 让 Promise 链（then/catch/finally）跑完若干轮。 */
async function flush(): Promise<void> {
  for (let i = 0; i < 6; i += 1) {
    await Promise.resolve()
  }
}

describe('ImageQueue', () => {
  it('limits how many downloads run at the same time', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ maxConcurrency: 2, fetch: fetcher.fetch })

    queue.requestMany(['a', 'b', 'c'])
    expect(fetcher.started).toEqual(['a', 'b'])
    expect(queue.pendingCount).toBe(3)

    fetcher.resolvers.get('a')?.('/tmp/a')
    await flush()
    expect(fetcher.started).toEqual(['a', 'b', 'c'])
  })

  it('downloads the same url only once and reuses the cached path', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch })

    const first = queue.request('same')
    const second = queue.request('same')
    expect(fetcher.started).toEqual(['same'])

    fetcher.resolvers.get('same')?.('/tmp/same')
    expect(await first).toBe('/tmp/same')
    expect(await second).toBe('/tmp/same')
    expect(queue.cached('same')).toBe('/tmp/same')

    // 已缓存的地址不再触发下载
    expect(await queue.request('same')).toBe('/tmp/same')
    expect(fetcher.started).toEqual(['same'])
  })

  it('reports failures instead of leaving the request hanging', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch })

    const pending = queue.request('broken')
    fetcher.rejecters.get('broken')?.(new Error('网络错误'))

    await expect(pending).rejects.toThrow('网络错误')
    expect(queue.cached('broken')).toBe('')
  })

  it('notifies onSettled with the local path or null', async () => {
    const fetcher = createFetcher()
    const settled: Array<[string, string | null]> = []
    const queue = new ImageQueue({
      fetch: fetcher.fetch,
      onSettled: (url, path) => settled.push([url, path]),
    })

    queue.request('ok').catch(() => undefined)
    queue.request('bad').catch(() => undefined)
    fetcher.resolvers.get('ok')?.('/tmp/ok')
    fetcher.rejecters.get('bad')?.(new Error('失败'))

    await flush()
    expect(settled).toEqual([
      ['ok', '/tmp/ok'],
      ['bad', null],
    ])
  })

  it('rejects an empty url without starting a download', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch })
    await expect(queue.request('')).rejects.toThrow('图片地址为空')
    expect(fetcher.started).toEqual([])
  })

  it('drops cached paths and pending tasks on clear', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch })

    queue.request('a')
    fetcher.resolvers.get('a')?.('/tmp/a')
    await flush()
    expect(queue.cached('a')).toBe('/tmp/a')

    queue.clear()
    expect(queue.cached('a')).toBe('')
    expect(queue.pendingCount).toBe(0)
  })

  it('settles queued and in-flight requests when cleared', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ maxConcurrency: 1, fetch: fetcher.fetch })

    const running = queue.request('running')
    const queued = queue.request('queued')
    queue.clear()

    // 登出不能留下永远不落地的 Promise：两个请求都必须被拒绝
    await expect(running).rejects.toThrow('图片下载已取消')
    await expect(queued).rejects.toThrow('图片下载已取消')
    expect(queue.pendingCount).toBe(0)
    expect(fetcher.started).toEqual(['running'])
  })

  it('does not let a download that finishes after clear repopulate the cache', async () => {
    const fetcher = createFetcher()
    const settled: Array<[string, string | null]> = []
    const queue = new ImageQueue({
      fetch: fetcher.fetch,
      onSettled: (url, path) => settled.push([url, path]),
    })

    queue.request('late').catch(() => undefined)
    queue.clear()
    fetcher.resolvers.get('late')?.('/tmp/late')
    await flush()

    // 上一个账号的下载晚到一步，不能写回缓存，也不该再回调页面
    expect(queue.cached('late')).toBe('')
    expect(settled).toEqual([])
  })

  it('treats entries older than the ttl as missing', async () => {
    const fetcher = createFetcher()
    let now = 1_000
    const queue = new ImageQueue({ fetch: fetcher.fetch, cacheTtlMs: 500, now: () => now })

    const pending = queue.request('a')
    fetcher.resolvers.get('a')?.('/tmp/a')
    await pending
    expect(queue.cached('a')).toBe('/tmp/a')

    // 撤权后不能一直吃旧文件：过期即失效，下一次请求重新走鉴权
    now += 600
    expect(queue.cached('a')).toBe('')
    queue.request('a').catch(() => undefined)
    expect(fetcher.started).toEqual(['a', 'a'])
  })

  it('never serves an image cached for another account', async () => {
    const fetcher = createFetcher()
    let owner = 'user-a'
    const queue = new ImageQueue({ fetch: fetcher.fetch, owner: () => owner })

    const pending = queue.request('private')
    fetcher.resolvers.get('private')?.('/tmp/private')
    await pending
    expect(queue.cached('private')).toBe('/tmp/private')

    // 换账号后同一个 URL 也不能命中上一个账号的本地文件
    owner = 'user-b'
    expect(queue.cached('private')).toBe('')
  })

  it('keeps the cache within its capacity limit', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch, maxCached: 2 })

    for (const url of ['a', 'b', 'c']) {
      const pending = queue.request(url)
      fetcher.resolvers.get(url)?.(`/tmp/${url}`)
      await pending
    }

    // 微信临时文件总量有限，最旧的先淘汰
    expect(queue.cached('a')).toBe('')
    expect(queue.cached('b')).toBe('/tmp/b')
    expect(queue.cached('c')).toBe('/tmp/c')
  })

  it('re-downloads after an entry is invalidated', async () => {
    const fetcher = createFetcher()
    const queue = new ImageQueue({ fetch: fetcher.fetch })

    const pending = queue.request('a')
    fetcher.resolvers.get('a')?.('/tmp/a')
    await pending

    queue.invalidate('a')
    expect(queue.cached('a')).toBe('')
    queue.request('a').catch(() => undefined)
    expect(fetcher.started).toEqual(['a', 'a'])
  })

  it('deletes the local files it no longer references', async () => {
    const fetcher = createFetcher()
    const disposed: string[] = []
    const queue = new ImageQueue({ fetch: fetcher.fetch, dispose: (path) => disposed.push(path) })

    for (const url of ['a', 'b']) {
      const pending = queue.request(url)
      fetcher.resolvers.get(url)?.(`/tmp/${url}`)
      await pending
    }

    // 单条作废要删文件，否则撤权后旧图还留在磁盘上
    queue.invalidate('a')
    expect(disposed).toEqual(['/tmp/a'])

    // 登出清空时把剩下的也删掉
    queue.clear()
    expect(disposed).toEqual(['/tmp/a', '/tmp/b'])
  })

  it('deletes a file whose download lands after clear', async () => {
    const fetcher = createFetcher()
    const disposed: string[] = []
    const queue = new ImageQueue({ fetch: fetcher.fetch, dispose: (path) => disposed.push(path) })

    queue.request('late').catch(() => undefined)
    queue.clear()
    fetcher.resolvers.get('late')?.('/tmp/late')
    await flush()

    expect(queue.cached('late')).toBe('')
    expect(disposed).toEqual(['/tmp/late'])
  })

  it('keeps evicted files: a rendered card may still point at them', async () => {
    const fetcher = createFetcher()
    const disposed: string[] = []
    const queue = new ImageQueue({
      fetch: fetcher.fetch,
      maxCached: 1,
      dispose: (path) => disposed.push(path),
    })

    for (const url of ['a', 'b']) {
      const pending = queue.request(url)
      fetcher.resolvers.get(url)?.(`/tmp/${url}`)
      await pending
    }

    expect(queue.cached('a')).toBe('')
    expect(disposed).toEqual([])
  })
})
