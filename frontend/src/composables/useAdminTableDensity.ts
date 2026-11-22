import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 管理端表格的宽度档位：
 * - wide   宽屏，全列
 * - medium 中屏，收掉次要列
 * - narrow 窄屏，只留关键列，操作列仍固定
 * - phone  手机，只留关键列且不固定操作列（固定列会吃掉本就所剩无几的正文宽度）
 */
export type AdminTableDensity = 'wide' | 'medium' | 'narrow' | 'phone'

/**
 * 跟踪视口宽度，给后台表格做列裁剪。
 *
 * 后台表格列多，若只靠横向滚动 + 固定操作列，宽屏以下会把「所有者 / 创建时间」
 * 压在固定列下面，看起来像被截断。这里统一四个断点，页面按档位直接决定列集合
 * 与操作列是否固定。
 */
export function useAdminTableDensity(): Ref<AdminTableDensity> {
  const density = ref<AdminTableDensity>('wide')

  const measure = () => {
    const width = window.innerWidth
    density.value =
      width >= 1184 ? 'wide' : width >= 901 ? 'medium' : width >= 641 ? 'narrow' : 'phone'
  }

  measure()

  onMounted(() => {
    measure()
    window.addEventListener('resize', measure, { passive: true })
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', measure)
  })

  return density
}
