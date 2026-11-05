import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ImageOutPainting from '@/components/ImageOutPainting.vue'
import { acquirePictureEditLease } from '@/utils/acquirePictureEditLease'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))
vi.mock('@/utils/acquirePictureEditLease', () => ({ acquirePictureEditLease: vi.fn() }))
vi.mock('@/api/pictureController.ts', () => ({
  createPictureOutPaintingTaskUsingPost: vi.fn(),
  getPictureOutPaintingTaskUsingGet: vi.fn(),
  uploadPictureByUrlUsingPost: vi.fn(),
}))

describe('团队扩图替换', () => {
  afterEach(() => vi.useRealTimers())

  it('应用结果前取得租约，保存后释放', async () => {
    vi.useFakeTimers()
    const release = vi.fn()
    vi.mocked(acquirePictureEditLease).mockResolvedValue({ token: 'private-token', release })
    vi.mocked(createPictureOutPaintingTaskUsingPost).mockResolvedValue({
      data: { code: 0, data: { output: { taskId: 'task' } } },
    } as never)
    vi.mocked(getPictureOutPaintingTaskUsingGet).mockResolvedValue({
      data: {
        code: 0,
        data: {
          output: { taskStatus: 'SUCCEEDED', outputImageUrl: 'https://example.invalid/image.png' },
        },
      },
    } as never)
    vi.mocked(uploadPictureByUrlUsingPost).mockResolvedValue({
      data: { code: 0, data: { id: 42 } },
    } as never)
    const wrapper = mount(ImageOutPainting, {
      props: { picture: { id: 42 }, space: { spaceType: 1 }, spaceId: 7 },
      global: { stubs: { teleport: true } },
    })
    try {
      ;(wrapper.vm as unknown as { openModal(): void }).openModal()
      await wrapper.vm.$nextTick()
      await wrapper.get('.action-btn.secondary').trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(3000)
      await flushPromises()
      await wrapper.get('.action-btn.primary').trigger('click')
      await flushPromises()
      expect(acquirePictureEditLease).toHaveBeenCalledWith(42)
      expect(uploadPictureByUrlUsingPost).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 42,
          spaceId: 7,
          collaborationToken: 'private-token',
        }),
      )
      expect(release).toHaveBeenCalledOnce()
    } finally {
      wrapper.unmount()
    }
  })
})
