import { getCurrentInstance, h, onBeforeUnmount } from 'vue'
import { notification } from 'ant-design-vue'

export interface UndoableOptions {
  /** 同一条记录的稳定标识；重复触发时先提交上一笔，避免两次操作互相覆盖 */
  key: string
  message: string
  description?: string
  /** 撤销窗口结束后真正执行的操作 */
  commit: () => Promise<void>
  /** 用户点击撤销时调用，用于把本地状态恢复原样 */
  undo?: () => void
  /** 撤销窗口，默认 5 秒 */
  duration?: number
}

interface PendingEntry {
  timer: number
  commit: () => Promise<void>
  notificationKey: string
}

/**
 * 带撤销窗口的高危操作。
 *
 * 管理端的删除、禁用、标记解决都不是「再点一次就能反悔」的动作：与其让操作员在确认框里
 * 赌自己没点错，不如先让界面立刻反映结果，再给 5 秒撤销。窗口结束时才真正落库；
 * 离开页面时会把未决操作立即提交，不会静默丢弃。
 */
export function useUndoable() {
  const pending = new Map<string, PendingEntry>()

  const clearEntry = (key: string) => {
    const entry = pending.get(key)
    if (!entry) return null
    window.clearTimeout(entry.timer)
    pending.delete(key)
    return entry
  }

  const schedule = (options: UndoableOptions) => {
    const { key, message, description, commit, undo, duration = 5000 } = options
    // 同一记录再次操作：上一笔立即生效，避免撤销栈里出现两个互相矛盾的版本
    const previous = clearEntry(key)
    if (previous) {
      notification.close(previous.notificationKey)
      void previous.commit()
    }

    const notificationKey = `undoable-${key}-${Date.now()}`
    const timer = window.setTimeout(() => {
      pending.delete(key)
      void commit()
    }, duration)

    pending.set(key, { timer, commit, notificationKey })

    notification.info({
      key: notificationKey,
      message,
      description: description ?? `${Math.round(duration / 1000)} 秒内可撤销`,
      duration: duration / 1000,
      placement: 'bottomRight',
      btn: () =>
        h(
          'button',
          {
            type: 'button',
            class: 'admin-undo-btn',
            onClick: () => {
              clearEntry(key)
              notification.close(notificationKey)
              undo?.()
            },
          },
          '撤销',
        ),
    })
  }

  /** 立即提交所有未决操作，用于离开页面或切换数据源之前 */
  const flush = () => {
    const entries = [...pending.values()]
    pending.clear()
    for (const entry of entries) {
      window.clearTimeout(entry.timer)
      notification.close(entry.notificationKey)
      void entry.commit()
    }
  }

  // 只有挂在组件里才需要卸载钩子；单测里直接调用时跳过
  if (getCurrentInstance()) {
    onBeforeUnmount(flush)
  }

  return { schedule, flush }
}
