import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AssetBoardEditor from '../AssetBoardEditor.vue'
import type { AssetResult } from '@/api-custom/assetSearch'
import { useLoginUserStore } from '@/stores/userLogin'

const api = vi.hoisted(() => ({
  save: vi.fn(),
  read: vi.fn(),
  operation: vi.fn(),
  task: vi.fn(),
  plan: vi.fn(),
  decide: vi.fn(),
}))
vi.mock('@/api-custom/boardProposal', () => ({
  readTask: api.task,
  planBoard: api.plan,
  decideProposal: api.decide,
}))
vi.mock('@/api-custom/assetBoard', () => ({
  saveBoard: api.save,
  readBoard: api.read,
  readBoardOperation: api.operation,
}))
let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})
const result: AssetResult = {
  schemaVersion: 1,
  resultId: 'r1',
  spaceId: '9007199254740999',
  observedAt: '',
  criteria: { orientation: 'LANDSCAPE' },
  ruleVersion: 'orientation-v1',
  items: [
    {
      position: 0,
      status: 'AVAILABLE',
      assetRef: { kind: 'INTERNAL', pictureId: '9007199254741001' },
      sourceVersion: 'v1',
      verifiedFacts: { width: 1920, height: 1080 },
      matches: [],
      interpretations: [],
    },
  ],
}
async function open(url = '/agent') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', component: { template: '<div />' } }],
  })
  await router.push(url)
  wrapper = mount(AssetBoardEditor, {
    props: { results: [result] },
    global: { plugins: [createPinia(), router] },
  })
  await flushPromises()
  return wrapper
}
describe('个人候选素材板', () => {
  const persisted = {
    schemaVersion: 1,
    boardId: 'b1',
    spaceId: result.spaceId,
    title: '服务端标题',
    revision: 1,
    groups: [],
    items: [],
  }
  const proposalTask = {
    schemaVersion: 1,
    type: 'BOARD_SAVE_PROPOSAL',
    taskId: 'task1',
    proposalId: 'p1',
    proposalRevision: 1,
    operationId: 'op1',
    status: 'AWAITING_CONFIRMATION',
    boardId: 'b1',
    expectedRevision: 1,
    current: persisted,
    target: persisted,
    canConfirm: true,
  }
  it('keeps unsaved board edits when a task focus refresh loads the same board', async () => {
    api.read.mockResolvedValue({ data: { code: 0, data: persisted } })
    api.task.mockResolvedValue({ data: { code: 0, data: proposalTask } })
    const w = await open('/agent?board=b1&task=task1')
    await w.get('[aria-label="板标题"]').setValue('本页未保存的标题')
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(w.get<HTMLInputElement>('[aria-label="板标题"]').element.value).toBe('本页未保存的标题')
    expect(w.text()).toContain('未保存修改')
    expect(api.save).not.toHaveBeenCalled()
  })
  it('keeps edits begun while the authoritative task read is still in flight', async () => {
    api.read.mockResolvedValue({ data: { code: 0, data: persisted } })
    api.task.mockResolvedValue({ data: { code: 0, data: proposalTask } })
    const w = await open('/agent?board=b1&task=task1')
    let finish: ((value: unknown) => void) | undefined
    api.task.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    await w.get('[aria-label="板标题"]').setValue('任务读取期间的新编辑')
    finish!({ data: { code: 0, data: proposalTask } })
    await flushPromises()
    expect(w.get<HTMLInputElement>('[aria-label="板标题"]').element.value).toBe(
      '任务读取期间的新编辑',
    )
    expect(w.text()).toContain('未保存修改')
    expect(api.save).not.toHaveBeenCalled()
  })
  it('retains only the operation identifier for minimal verification after access is revoked', async () => {
    api.save.mockRejectedValue(new Error('lost response'))
    const w = await open()
    await w.get('[data-action="import"]').trigger('click')
    await w.get('[data-action="save"]').trigger('click')
    await flushPromises()
    const operationId = api.save.mock.calls[0]?.[1].operationId
    await w.setProps({ results: [], privacyEpoch: 1 })
    expect(w.text()).not.toContain('1920')
    api.operation.mockResolvedValue({
      data: { code: 0, data: { schemaVersion: 1, operationId, status: 'COMMITTED' } },
    })
    await w.get('[data-action="check-operation"]').trigger('click')
    await flushPromises()
    expect(api.operation).toHaveBeenCalledWith(operationId)
    expect(w.text()).toContain('操作已提交')
    expect(api.read).not.toHaveBeenCalled()
    expect(w.find('[data-action="retry"]').exists()).toBe(false)
    api.operation.mockResolvedValue({
      data: {
        code: 0,
        data: { schemaVersion: 1, operationId, status: 'COMMITTED', boardId: 'b1', revision: 1 },
      },
    })
    api.read.mockResolvedValue({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          boardId: 'b1',
          spaceId: result.spaceId,
          title: '恢复访问的板',
          revision: 2,
          groups: [],
          items: [],
        },
      },
    })
    await w.get('[data-action="check-operation"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-action="check-operation"]').exists()).toBe(false)
    expect(w.find('[data-action="save"]').exists()).toBe(true)
  })
  it('ignores a delayed older read after a newer board projection has loaded', async () => {
    const board = {
      schemaVersion: 1,
      boardId: 'b1',
      spaceId: result.spaceId,
      title: '版本一',
      revision: 1,
      groups: [],
      items: [],
    }
    api.read.mockResolvedValue({ data: { code: 0, data: board } })
    const w = await open('/agent?board=b1')
    let finish: ((value: unknown) => void) | undefined
    api.read.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    api.read.mockResolvedValueOnce({
      data: { code: 0, data: { ...board, title: '版本二', revision: 2 } },
    })
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    finish?.({ data: { code: 0, data: board } })
    await flushPromises()
    expect(w.text()).toContain('版本 2')
    expect(w.get<HTMLInputElement>('input[aria-label="板标题"]').element.value).toBe('版本二')
  })
  it('hides imported historical facts when the source projection becomes unavailable', async () => {
    const w = await open()
    await w.get('[data-action="import"]').trigger('click')
    expect(w.text()).toContain('1920')
    await w.setProps({ results: [{ ...result, items: [{ position: 0, status: 'UNAVAILABLE' }] }] })
    expect(w.text()).not.toContain('1920')
    expect(w.text()).toContain('素材不可用')
  })
  it('ignores an old-account board response arriving after identity changes', async () => {
    let finish: ((value: unknown) => void) | undefined
    api.read.mockImplementation(
      () =>
        new Promise((resolve) => {
          finish = resolve
        }),
    )
    const w = await open('/agent?board=b1')
    useLoginUserStore().setUserLogin({ id: 2, userName: 'new account' })
    await w.setProps({ results: [] })
    finish?.({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          boardId: 'b1',
          spaceId: result.spaceId,
          title: '旧账户私人板',
          revision: 99,
          groups: [],
          items: [],
        },
      },
    })
    await flushPromises()
    expect(w.text()).not.toContain('99')
    expect(w.get<HTMLInputElement>('input[aria-label="板标题"]').element.value).not.toContain(
      '旧账户',
    )
  })
  it('restores a board and removes groups without removing or reidentifying their items', async () => {
    api.read.mockResolvedValue({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          boardId: 'b1',
          spaceId: result.spaceId,
          title: '已保存草稿',
          revision: 7,
          groups: [{ groupId: 'g1', position: 0, name: '<b>横图</b>' }],
          items: [
            { ...result.items[0], boardItemId: 'i1', selected: true, groupId: 'g1' },
            {
              ...result.items[0],
              boardItemId: 'i2',
              selected: true,
              position: 1,
              assetRef: { kind: 'INTERNAL', pictureId: '9007199254741002' },
            },
          ],
        },
      },
    })
    const w = await open('/agent?board=b1')
    expect(w.text()).toContain('版本 7')
    expect(w.find('b').exists()).toBe(false)
    await w.get('[aria-label="删除分组"]').trigger('click')
    await w.findAll('[aria-label="下移素材"]')[0]!.trigger('click')
    api.save.mockRejectedValue(new Error('stop after recording request'))
    await w.get('[data-action="save"]').trigger('click')
    await flushPromises()
    expect(api.save.mock.calls[0]?.[0]).toBe('b1')
    expect(api.save.mock.calls[0]?.[1]).toMatchObject({
      expectedRevision: 7,
      groups: [],
      items: [
        { boardItemId: 'i2', position: 0, selected: true },
        { boardItemId: 'i1', position: 1, selected: true },
      ],
    })
    expect(
      api.save.mock.calls[0]?.[1].items.every((item: { groupId?: string }) => !item.groupId),
    ).toBe(true)
  })
  it('keeps the same operation after an unknown response and NOT_RECORDED', async () => {
    api.save.mockRejectedValueOnce(new Error('connection lost')).mockResolvedValueOnce({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          operationId: 'op',
          status: 'COMMITTED',
          boardId: 'b1',
          revision: 1,
        },
      },
    })
    api.operation.mockResolvedValue({
      data: { code: 0, data: { schemaVersion: 1, operationId: 'op', status: 'NOT_RECORDED' } },
    })
    api.read.mockResolvedValue({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          boardId: 'b1',
          spaceId: result.spaceId,
          title: '恢复板',
          revision: 1,
          groups: [],
          items: [],
        },
      },
    })
    const w = await open()
    await w.get('[data-action="import"]').trigger('click')
    await w.get('[data-action="save"]').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('保存结果未知')
    await w.get('[data-action="check-operation"]').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('只能重试原操作')
    await w.get('[data-action="retry"]').trigger('click')
    await flushPromises()
    expect(api.save.mock.calls[1]).toEqual(api.save.mock.calls[0])
    expect(w.get<HTMLInputElement>('input[aria-label="板标题"]').element.value).toBe('恢复板')
  })
  it('explicitly imports a snapshot and saves selection without changing gallery assets', async () => {
    api.save.mockResolvedValue({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          operationId: 'op',
          status: 'COMMITTED',
          boardId: 'b1',
          revision: 1,
        },
      },
    })
    api.read.mockResolvedValue({
      data: {
        code: 0,
        data: {
          schemaVersion: 1,
          boardId: 'b1',
          spaceId: result.spaceId,
          title: '我的候选板',
          revision: 1,
          groups: [],
          items: [],
        },
      },
    })
    const w = await open()
    await w.get('[data-action="import"]').trigger('click')
    expect(w.text()).toContain('未保存')
    expect(w.text()).toContain('1920')
    await w.get('input[type="checkbox"]').setValue(false)
    await w.get('[data-action="save"]').trigger('click')
    await flushPromises()
    expect(api.save).toHaveBeenCalledOnce()
    const [boardId, body] = api.save.mock.calls[0]!
    expect(boardId).toBeUndefined()
    expect(body.resultId).toBe('r1')
    expect(body.items[0]).toMatchObject({
      sourcePosition: 0,
      sourceVersion: 'v1',
      selected: false,
      position: 0,
    })
    expect(w.text()).toContain('版本 1')
  })
})
