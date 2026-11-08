import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AgentChatPage from '../AgentChatPage.vue'
import { useLoginUserStore } from '@/stores/userLogin'

const network = vi.hoisted(() => ({ listTeams: vi.fn() }))
vi.mock('@/api/spaceUserController', () => ({
  listMyTeamSpaceUsingPost: () => network.listTeams(),
}))
vi.mock('@/api-custom/agent', async (original) => ({
  ...(await original<typeof import('@/api-custom/agent')>()),
  getSessions: async () => ({ data: { code: 0, data: [] } }),
  getRecoveryState: async () => ({ data: { code: 0, data: null } }),
}))
vi.mock('@microsoft/fetch-event-source', () => ({
  fetchEventSource: async (
    _url: string,
    options: { onopen: (response: Response) => Promise<void> },
  ) => {
    await options.onopen(
      new Response(JSON.stringify({ code: 50001, message: 'RULE_NOT_READY' }), {
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  },
}))
vi.mock('ant-design-vue', async (original) => ({
  ...(await original<typeof import('ant-design-vue')>()),
  message: { error: vi.fn(), warning: vi.fn() },
}))

type TeamResponse = {
  data: { code: number; data: { spaceId: string; space: { spaceName: string } }[] }
}
let wrapper: VueWrapper | undefined

async function openPage() {
  const pinia = createPinia()
  const store = useLoginUserStore(pinia)
  store.setUserLogin({ id: 1, userName: 'A' })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/agent', component: AgentChatPage }],
  })
  await router.push('/agent')
  await router.isReady()
  wrapper = mount(AgentChatPage, {
    global: {
      plugins: [pinia, router],
      stubs: {
        AgentChatLayout: { template: '<main><slot name="center" /><slot name="right" /></main>' },
        AgentChatInput: {
          template: "<button @click=\"$emit('send', '找蓝紫横图', [])\">发送查询</button>",
        },
        AgentMessageList: true,
        AgentResourcePanel: true,
        ImagePreview: true,
        SettingsModal: true,
        RecoveryBanner: true,
        AgentTodoList: true,
        LeftSidebar: true,
      },
    },
  })
  await flushPromises()
  return store
}

beforeEach(() => {
  localStorage.clear()
  network.listTeams.mockReset()
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
})

describe('找图页面的身份与错误边界', () => {
  it('账号切换后不显示旧账号迟到的团队列表，并加载当前账号团队', async () => {
    let resolveOld: (response: TeamResponse) => void = () => {
      throw new Error('not initialized')
    }
    const oldResponse = new Promise<TeamResponse>((resolve) => {
      resolveOld = resolve
    })
    network.listTeams.mockReturnValueOnce(oldResponse).mockResolvedValue({
      data: { code: 0, data: [{ spaceId: '202', space: { spaceName: 'B 的团队' } }] },
    })
    const store = await openPage()
    store.setUserLogin({ id: 2, userName: 'B' })
    await flushPromises()
    resolveOld({
      data: { code: 0, data: [{ spaceId: '101', space: { spaceName: 'A 的私人团队' } }] },
    })
    await flushPromises()
    await wrapper!.get('.asset-mode input').setValue(true)
    expect(wrapper!.get('select').text()).not.toContain('A 的私人团队')
    expect(wrapper!.get('select').text()).toContain('B 的团队')
  })

  it('自然语言找图的未就绪错误给出可操作文案', async () => {
    network.listTeams.mockResolvedValue({
      data: { code: 0, data: [{ spaceId: '101', space: { spaceName: 'A 的团队' } }] },
    })
    await openPage()
    await wrapper!.get('.asset-mode input').setValue(true)
    await wrapper!.get('select').setValue('101')
    // 用输入组件的公开 send 事件调用页面，避免执行直接检索按钮。
    const input = wrapper!.findAll('button').find((button) => button.text() === '发送查询')!
    await input.trigger('click')
    await flushPromises()
    expect(wrapper!.text()).toContain('颜色筛选规则尚未就绪，请先使用横图筛选')
    expect(wrapper!.text()).not.toContain('RULE_NOT_READY')
  })
})
