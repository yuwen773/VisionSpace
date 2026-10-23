/**
 * 图表框内的事件处理（预览/代码切换、复制）。
 *
 * 这些按钮是被 v-html 插入的，拿不到 Vue 事件绑定，所以由宿主元素做事件委托。
 * 监听挂在消息体自身而不是 document 上：组件卸载时元素消失，监听随之释放，
 * 不会在多条消息间累积。
 */

/** 切换同一个图表框内的"预览 / 代码"面板。 */
export function handleDiagramTabClick(e: Event): void {
  const target = e.target as HTMLElement | null
  const tabButton = target?.closest<HTMLElement>('.diagram-tab')
  if (!tabButton) return

  const wrapper = tabButton.closest<HTMLElement>('.diagram-wrapper')
  if (!wrapper) return

  const showPreview = tabButton.dataset.tab === 'preview'
  const previewPanel = wrapper.querySelector<HTMLElement>('.diagram-preview')
  const codePanel = wrapper.querySelector<HTMLElement>('.diagram-code')

  if (previewPanel) previewPanel.style.display = showPreview ? 'block' : 'none'
  if (codePanel) codePanel.style.display = showPreview ? 'none' : 'block'

  wrapper.querySelector('.diagram-tab[data-tab="preview"]')?.classList.toggle('active', showPreview)
  wrapper.querySelector('.diagram-tab[data-tab="code"]')?.classList.toggle('active', !showPreview)
}

/** 复制按钮：把所属代码块的文本写入剪贴板，并给出短暂反馈。 */
export async function handleCopyButtonClick(btn: HTMLButtonElement): Promise<void> {
  const block = btn.closest('.code-block')
  const text = block?.querySelector('code')?.textContent ?? ''
  try {
    await navigator.clipboard.writeText(text)
    btn.textContent = '已复制'
    btn.classList.add('copied')
    setTimeout(() => {
      btn.textContent = '复制'
      btn.classList.remove('copied')
    }, 2000)
  } catch {
    btn.textContent = '复制失败'
  }
}

/** 消息体上的统一点击入口。 */
export function handleContentClick(e: Event): void {
  const target = e.target as HTMLElement | null
  if (!target) return

  const copyBtn = target.closest<HTMLButtonElement>('.copy-btn')
  if (copyBtn) {
    void handleCopyButtonClick(copyBtn)
    return
  }

  handleDiagramTabClick(e)
}
