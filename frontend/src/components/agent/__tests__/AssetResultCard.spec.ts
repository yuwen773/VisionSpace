import { mount } from '@vue/test-utils'
import { describe, expect, it, beforeAll, vi } from 'vitest'
import AssetResultCard from '../AssetResultCard.vue'
import AgentMessageList from '../AgentMessageList.vue'
import AgentResourcePanel from '../AgentResourcePanel.vue'
import type { AssetResult } from '@/api-custom/assetSearch'

beforeAll(() => {
  HTMLElement.prototype.scrollTo = vi.fn()
})

describe('站内检索快照卡片', () => {
  it('展示检索依据和过期状态，不渲染不可用项的私人内容', async () => {
    const wrapper = mount(AssetResultCard, {
      props: {
        result: {
          schemaVersion: 1,
          resultId: 'snapshot-1',
          spaceId: '9007199254741001',
          observedAt: '2026-11-07T00:00:00Z',
          criteria: { orientation: 'LANDSCAPE' },
          ruleVersion: 'orientation-v1',
          items: [
            {
              position: 0,
              status: 'STALE',
              assetRef: { kind: 'INTERNAL', pictureId: '9007199254741002' },
              sourceVersion: 'v1',
              verifiedFacts: { width: 1920, height: 1080 },
              matches: [
                {
                  criterion: 'LANDSCAPE',
                  status: 'MATCHED',
                  evidence: { rule: 'WIDTH_GT_HEIGHT' },
                },
              ],
              interpretations: [],
              display: { contentRef: '/api/agent/asset-results/snapshot-1/items/0/content' },
            },
            { position: 1, status: 'UNAVAILABLE' },
          ],
        },
      },
    })
    expect(wrapper.text()).toContain('1920 × 1080')
    expect(wrapper.text()).toContain('依据已过期')
    expect(wrapper.text()).toContain('素材不可用')
    expect(wrapper.findAll('img')).toHaveLength(1)
    expect(wrapper.get('img').attributes('src')).toBe(
      '/api/agent/asset-results/snapshot-1/items/0/content',
    )
    expect(wrapper.text()).not.toContain('9007199254741002')
    await wrapper.get('img').trigger('error')
    expect(wrapper.emitted('previewError')).toHaveLength(1)
    expect(wrapper.emitted('refresh')).toBeUndefined()
  })

  it('聊天和资源面板使用同一快照，结构化消息不再抽取 Markdown 图片', () => {
    const result: AssetResult = {
      schemaVersion: 1,
      resultId: 'snapshot-1',
      spaceId: '123',
      observedAt: '2026-11-07T00:00:00Z',
      criteria: { orientation: 'LANDSCAPE' },
      ruleVersion: 'orientation-v1',
      items: [],
    }
    const chat = mount(AgentMessageList, {
      props: {
        messages: [
          {
            type: 'asset-result',
            resultId: result.resultId,
            content: '![duplicate](https://untrusted.invalid/image.jpg)',
          },
        ],
        assetResults: { [result.resultId]: result },
      },
    })
    const panel = mount(AgentResourcePanel, { props: { assetResults: [result] } })
    expect(chat.findAll('[data-result-id="snapshot-1"]')).toHaveLength(1)
    expect(panel.findAll('[data-result-id="snapshot-1"]')).toHaveLength(1)
    expect(chat.text()).toContain('未找到符合条件的素材')
    expect(chat.findAll('img')).toHaveLength(0)
    expect(chat.html()).not.toContain('untrusted.invalid')
    chat.unmount()
    panel.unmount()
  })
})
