// 全局复制函数，供代码块的复制按钮调用
;(window as any).copyCode = async function (btn: HTMLButtonElement) {
  const codeBlock = btn.closest('.code-block')
  if (!codeBlock) return
  const code = codeBlock.querySelector('code')?.textContent || ''
  try {
    await navigator.clipboard.writeText(code)
    btn.textContent = '已复制'
    setTimeout(() => {
      btn.textContent = '复制'
    }, 2000)
  } catch (err) {
    btn.textContent = '复制失败'
  }
}
