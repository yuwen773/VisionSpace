<template>
  <div id="shareModal">
    <a-modal
      v-model:open="visable"
      :title="null"
      :footer="null"
      @cancel="closeModal"
      :closable="true"
      width="480px"
      class="share-modal"
    >
      <div class="share-content">
        <!-- 标题 -->
        <div class="share-header">
          <Share2 :size="28" class="header-icon" />
          <h3 class="header-title">{{ title }}</h3>
        </div>

        <!-- 分享链接 -->
        <div class="share-section">
          <div class="section-label">
            <Link :size="18" />
            <span>复制分享链接</span>
          </div>
          <div class="link-wrapper pop-card">
            <a-typography-link
              :copyable="{ text: link, tooltips: ['复制', '已复制'] }"
              class="share-link"
            >
              {{ truncateLink(link) }}
            </a-typography-link>
          </div>
        </div>

        <!-- 二维码 -->
        <div class="share-section">
          <div class="section-label">
            <Smartphone :size="18" />
            <span>手机扫码查看</span>
          </div>
          <div class="qrcode-wrapper pop-card">
            <a-qrcode :value="link" :size="200" :status="visable ? 'active' : 'inactive'" />
          </div>
        </div>

        <!-- 底部提示 -->
        <div class="share-footer">
          <span class="footer-text"><Lightbulb :size="14" style="vertical-align: middle; margin-right: 4px;" /> 分享给好友，一起欣赏精彩图片</span>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Share2, Link, Smartphone, Lightbulb } from 'lucide-vue-next'

interface Props {
  link: string
  title: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '分享图片',
  link: '',
})

const visable = ref<boolean>(false)

// 截断链接显示
const truncateLink = (url: string) => {
  if (url.length > 50) {
    return url.substring(0, 25) + '...' + url.substring(url.length - 20)
  }
  return url
}

// 打开弹窗
const openModal = () => {
  visable.value = true
}

// 关闭弹窗
const closeModal = () => {
  visable.value = false
}

// 暴露函数给父组件
defineExpose({
  openModal
})
</script>

<style scoped lang="less">
#shareModal {
  :deep(.ant-modal-content) {
    border: 3px solid var(--border-bold);
    border-radius: var(--radius-2xl);
    box-shadow: var(--shadow-pop-lg);
    overflow: hidden;
  }

  :deep(.ant-modal-close) {
    top: var(--space-4);
    right: var(--space-4);
    width: 36px;
    height: 36px;
    border-radius: var(--radius-full);
    background: var(--bg-tertiary);
    border: 2px solid var(--border-light);

    &:hover {
      background: var(--color-coral-light);
      border-color: var(--color-coral);
      color: white;
    }

    .ant-modal-close-x {
      width: 36px;
      height: 36px;
      line-height: 36px;
      font-weight: 700;
    }
  }
}

/* ========== 分享内容 ========== */
.share-content {
  padding: var(--space-6) var(--space-4);
}

/* ========== 头部 ========== */
.share-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
  padding-bottom: var(--space-4);
  border-bottom: 3px dashed var(--border-light);
}

.header-icon {
  display: flex;
  align-items: center;
}

.header-title {
  font-family: var(--font-display);
  font-size: var(--text-2xl);
  font-weight: 800;
  margin: 0;
  background: var(--gradient-pop);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* ========== 分享区域 ========== */
.share-section {
  margin-bottom: var(--space-6);
}

.section-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: var(--space-4);
}

/* ========== 链接区域 ========== */
.link-wrapper {
  padding: var(--space-4);
  background: var(--bg-secondary);

  &::before {
    display: none;
  }
}

.share-link {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--color-sky);
  font-weight: 600;
  word-break: break-all;

  &:hover {
    color: var(--color-grape);
  }
}

/* ========== 二维码区域 ========== */
.qrcode-wrapper {
  display: flex;
  justify-content: center;
  padding: var(--space-6);
  background: var(--bg-secondary);

  &::before {
    display: none;
  }

  :deep(.ant-qrcode) {
    border: 3px solid var(--border-bold);
    border-radius: var(--radius-xl);
    padding: var(--space-4);
    background: white;
  }
}

/* ========== 底部 ========== */
.share-footer {
  margin-top: var(--space-6);
  padding-top: var(--space-4);
  border-top: 2px dashed var(--border-light);
  text-align: center;
}

.footer-text {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: 600;
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  :deep(.ant-modal) {
    max-width: calc(100vw - var(--space-8)) !important;
  }

  .share-content {
    padding: var(--space-4);
  }

  .header-title {
    font-size: var(--text-xl);
  }

  .qrcode-wrapper {
    padding: var(--space-4);

    :deep(.ant-qrcode) {
      canvas {
        width: 160px !important;
        height: 160px !important;
      }
    }
  }
}
</style>
