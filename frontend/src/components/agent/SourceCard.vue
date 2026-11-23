<template>
  <a
    class="source-card"
    :class="{ compact }"
    :href="link.url"
    target="_blank"
    rel="noopener noreferrer nofollow"
    :title="link.title || link.domain"
  >
    <span class="source-favicon" aria-hidden="true">
      <img
        v-if="link.faviconUrl && faviconLoadable && faviconOk"
        :src="link.faviconUrl"
        alt=""
        referrerpolicy="no-referrer"
        loading="lazy"
        @error="faviconOk = false"
      />
      <span v-else class="source-initial">{{ initial }}</span>
    </span>
    <span class="source-body">
      <span class="source-title">{{ link.title || link.domain }}</span>
      <span v-if="link.snippet" class="source-snippet">{{ link.snippet }}</span>
      <span class="source-meta">
        <span v-if="link.index" class="source-index">{{ link.index }}</span>
        <span class="source-domain">{{ link.domain }}</span>
      </span>
    </span>
  </a>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { isLoadableImage } from '@/utils/imagePolicy'
import type { LinkResource } from './types'

interface Props {
  link: LinkResource
  /** 悬停预览用紧凑版：收紧留白 */
  compact?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  compact: false,
})

const faviconOk = ref(true)

// 站点图标同样是第三方请求：只有受控清单里的地址才实际发起，
// 否则直接退回域名首字母（详见 utils/imagePolicy.ts）
const faviconLoadable = computed(
  () => Boolean(props.link.faviconUrl) && isLoadableImage(props.link.faviconUrl as string),
)

const initial = computed(() => {
  const host = props.link.domain.replace(/^www\./, '')
  return (host[0] || '?').toUpperCase()
})
</script>

<style scoped>
.source-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
  background: var(--color-bg-elevated);
  text-decoration: none;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}

.source-card:hover {
  border-color: var(--color-primary-500);
  transform: translateY(-1px);
}

.source-card.compact {
  padding: 8px 10px;
  align-items: center;
}

.source-favicon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: 5px;
  overflow: hidden;
  background: var(--color-bg-tertiary);
}

.source-card.compact .source-favicon {
  margin-top: 0;
}

.source-favicon img {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.source-initial {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text-secondary);
}

.source-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.source-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-snippet {
  font-size: 12px;
  line-height: 1.45;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.source-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.source-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 5px;
  background: color-mix(in srgb, var(--color-primary-500) 12%, transparent);
  color: var(--color-primary-500);
  font-weight: 700;
  font-size: 10px;
}

.source-domain {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
