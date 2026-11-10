import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import BoardProposalPanel from '../BoardProposalPanel.vue'
import { useLoginUserStore } from '@/stores/userLogin'

const api = vi.hoisted(() => ({ plan: vi.fn(), read: vi.fn(), decide: vi.fn() }))
vi.mock('@/api-custom/boardProposal', () => ({
  planBoard: api.plan,
  readTask: api.read,
  decideProposal: api.decide,
}))
let wrapper: VueWrapper | undefined
afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})
const task = {
  schemaVersion: 1,
  type: 'BOARD_SAVE_PROPOSAL',
  taskId: 'task1',
  proposalId: 'proposal1',
  proposalRevision: 1,
  operationId: 'operation1',
  status: 'AWAITING_CONFIRMATION',
  expectedRevision: 1,
  canConfirm: true,
  boardId: 'board1',
  current: {
    title: '私人草稿',
    groups: [],
    items: [{ boardItemId: 'i1', position: 0, selected: true }],
  },
  target: {
    title: '私人草稿',
    groups: [{ groupId: 'g1', position: 0, name: '<script>private</script>' }],
    items: [{ boardItemId: 'i1', position: 0, selected: false, groupId: 'g1' }],
  },
}
async function open(url = '/agent') {
  const pinia = createPinia()
  useLoginUserStore(pinia).loginUser = { id: 101 }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', component: { template: '<div />' } }],
  })
  await router.push(url)
  wrapper = mount(BoardProposalPanel, {
    props: { boardId: 'board1', revision: 1, threadId: 'thread1' },
    global: { plugins: [pinia, router] },
  })
  await flushPromises()
  return { router, pinia }
}
it('displays exact before/after selection and only sends the server-bound explicit decision', async () => {
  api.plan.mockResolvedValue({ data: { code: 0, data: task } })
  api.decide.mockResolvedValue({
    data: { code: 0, data: { ...task, status: 'COMMITTED', revision: 2, canConfirm: false } },
  })
  await open()
  await wrapper!.get('[aria-label="整理要求"]').setValue('取消选择并分组')
  await wrapper!.get('[data-action="plan-board"]').trigger('click')
  await flushPromises()
  expect(api.decide).not.toHaveBeenCalled()
  expect(wrapper!.text()).toContain('只保存个人草稿')
  expect(wrapper!.text()).toContain('已选')
  expect(wrapper!.text()).toContain('未选')
  expect(wrapper!.text()).toContain('<script>private</script>')
  expect(wrapper!.find('script').exists()).toBe(false)
  await wrapper!.get('[data-action="confirm-proposal"]').trigger('click')
  await flushPromises()
  expect(api.decide).toHaveBeenCalledWith('proposal1', {
    operationId: 'operation1',
    proposalRevision: 1,
    decision: 'CONFIRM',
  })
  expect(wrapper!.text()).toContain('已提交')
})

it('restores a minimal revoked task and allows explicit cancellation without private differences', async () => {
  const minimal = {
    schemaVersion: 1,
    type: 'BOARD_SAVE_PROPOSAL',
    taskId: 'task1',
    proposalId: 'proposal1',
    proposalRevision: 1,
    operationId: 'operation1',
    status: 'AWAITING_CONFIRMATION',
    canConfirm: false,
  }
  api.read.mockResolvedValue({ data: { code: 0, data: minimal } })
  api.decide.mockResolvedValue({ data: { code: 0, data: { ...minimal, status: 'CANCELLED' } } })
  await open('/agent?task=task1')
  expect(wrapper!.find('[data-action="confirm-proposal"]').exists()).toBe(false)
  expect(wrapper!.text()).not.toContain('私人草稿')
  await wrapper!.get('[data-action="cancel-proposal"]').trigger('click')
  await flushPromises()
  expect(api.decide).toHaveBeenCalledWith('proposal1', {
    operationId: 'operation1',
    proposalRevision: 1,
    decision: 'CANCEL',
  })
  expect(wrapper!.text()).toContain('已取消')
  expect(api.plan).not.toHaveBeenCalled()
})

it('discards an old private response after the account changes', async () => {
  let resolve: ((response: { data: { code: number; data: typeof task } }) => void) | undefined
  api.read.mockReturnValue(
    new Promise((r) => {
      resolve = r
    }),
  )
  const { pinia } = await open('/agent?task=task1')
  useLoginUserStore(pinia).loginUser = { id: 202 }
  await flushPromises()
  resolve!({ data: { code: 0, data: task } })
  await flushPromises()
  expect(wrapper!.text()).not.toContain('private')
  expect(wrapper!.find('[data-action="confirm-proposal"]').exists()).toBe(false)
  expect(api.decide).not.toHaveBeenCalled()
})

it('bounds automatic task reads after preview errors and waits for explicit image retry', async () => {
  const withPreview = {
    ...task,
    current: {
      ...task.current,
      items: task.current.items.map((i) => ({
        ...i,
        display: { contentRef: '/agent/tasks/task1/items/i1/content' },
      })),
    },
  }
  api.read.mockResolvedValue({ data: { code: 0, data: withPreview } })
  await open('/agent?task=task1')
  const image = wrapper!.get('img')
  await Promise.all(Array.from({ length: 5 }, () => image.trigger('error')))
  await flushPromises()
  expect(api.read).toHaveBeenCalledTimes(2)
  expect(wrapper!.find('img').exists()).toBe(false)
  expect(wrapper!.text()).toContain('预览暂不可用')
  await wrapper!.get('[data-action="retry-preview"]').trigger('click')
  await flushPromises()
  expect(wrapper!.find('img').exists()).toBe(true)
  await wrapper!.get('img').trigger('error')
  await flushPromises()
  expect(api.read).toHaveBeenCalledTimes(3)
  expect(wrapper!.find('img').exists()).toBe(false)
  expect(api.decide).not.toHaveBeenCalled()
})

it('retains the original planning request for explicit retry after a provider outage', async () => {
  api.plan
    .mockResolvedValueOnce({ data: { code: 50001, message: 'PLANNING_UNAVAILABLE' } })
    .mockResolvedValueOnce({ data: { code: 0, data: task } })
  await open()
  await wrapper!.get('[aria-label="整理要求"]').setValue('整理个人草稿')
  await wrapper!.get('[data-action="plan-board"]').trigger('click')
  await flushPromises()
  expect(wrapper!.text()).toContain('整理服务暂不可用')
  await wrapper!.get('[data-action="retry-plan"]').trigger('click')
  await flushPromises()
  expect(api.plan).toHaveBeenCalledTimes(2)
  expect(api.plan.mock.calls[1]?.[0]).toEqual(api.plan.mock.calls[0]?.[0])
  expect(api.decide).not.toHaveBeenCalled()
})
