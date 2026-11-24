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
})
