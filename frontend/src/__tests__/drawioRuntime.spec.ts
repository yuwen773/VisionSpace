import { describe, it, expect, beforeAll } from 'vitest'
import { renderDrawioBlock, __resetMxRuntimeForTests } from '@/composables/diagramRenderers'

/**
 * draw.io 运行时的资源生命周期。
 *
 * 这里用**真实的 mxgraph**（不是注入的假渲染器），因为要测的正是库自身
 * 往 document 上挂了什么、我们有没有解绑。注入 loader 会把这段逻辑绕过去，
 * 测出来永远是通过的。
 *
 * 观测方式：在 document 的 addEventListener / removeEventListener 上装探针，
 * 统计"加了但没移除"的监听器。探针必须在 mxgraph 被 import 之前装好——
 * 库在模块初始化阶段就会创建两个图实例（编解码器的模板对象），
 * 它们同样会挂 document 监听器。
 */
type Entry = { type: string; listener: EventListenerOrEventListenerObject }

const added: Entry[] = []
const removed: Entry[] = []

beforeAll(() => {
  const proto = EventTarget.prototype
  const originalAdd = proto.addEventListener
  const originalRemove = proto.removeEventListener

  proto.addEventListener = function (
    this: EventTarget,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ) {
    if (this === document) added.push({ type, listener })
    return originalAdd.call(this, type, listener, options)
  }
  proto.removeEventListener = function (
    this: EventTarget,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | EventListenerOptions,
  ) {
    if (this === document) removed.push({ type, listener })
    return originalRemove.call(this, type, listener, options)
  }
})

/** 当前仍挂在 document 上、由被观测代码添加的监听器。 */
function liveListeners(): Entry[] {
  return added.filter((a) => !removed.some((r) => r.type === a.type && r.listener === a.listener))
}

const DRAWIO_XML =
  '<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>' +
  '<mxCell id="2" value="节点 A" style="rounded=0;whiteSpace=wrap;html=1;" vertex="1" parent="1">' +
  '<mxGeometry x="40" y="40" width="100" height="40" as="geometry"/></mxCell>' +
  '</root></mxGraphModel>'

/** 真实运行时渲染一次，返回产物。 */
async function renderReal(xml: string): Promise<string> {
  return renderDrawioBlock(xml)
}

describe('draw.io 运行时：监听器生命周期', () => {
  it('探针本身有效：加一个再移除，计数应当归零', () => {
    const fn = () => {}
    const before = liveListeners().length
    document.addEventListener('keydown', fn)
    expect(liveListeners().length).toBe(before + 1)
    document.removeEventListener('keydown', fn)
    expect(liveListeners().length).toBe(before)
  })

  it('首次加载运行时并渲染后，document 上不留监听器', async () => {
    __resetMxRuntimeForTests()
    const out = await renderReal(DRAWIO_XML)

    // 渲染本身必须成功，否则"没有监听器"可能只是压根没跑
    expect(out).toContain('<svg')
    expect(out).not.toContain('diagram-error')

    // 库在模块初始化阶段创建的编解码器模板实例也要释放，
    // 否则它们会带着 document 监听器活到页面结束。
    expect(liveListeners().map((l) => l.type)).toEqual([])
  })

  it('重复渲染不累积监听器', async () => {
    for (let i = 0; i < 3; i++) {
      const out = await renderReal(DRAWIO_XML)
      expect(out).toContain('<svg')
    }
    expect(liveListeners().map((l) => l.type)).toEqual([])
  })

  it('解码失败时同样不留监听器', async () => {
    const out = await renderReal('<not-a-graph/>')
    expect(out).toContain('diagram-error')
    expect(liveListeners().map((l) => l.type)).toEqual([])
  })

  it('空模型（只有两层空节点）按失败处理并释放实例', async () => {
    const out = await renderReal(
      '<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>',
    )
    expect(out).toContain('diagram-error')
    expect(liveListeners().map((l) => l.type)).toEqual([])
  })
})
