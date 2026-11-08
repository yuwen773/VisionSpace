<template>
  <section class="asset-result" :data-result-id="result.resultId" aria-label="站内找图结果">
    <header class="result-heading">
      <div>
        <span class="eyebrow">团队素材</span>
        <h4>检索结果</h4>
      </div>
      <button type="button" @click="$emit('refresh')">刷新状态</button>
    </header>
    <p v-if="result.items.length === 0" class="result-empty">未找到符合条件的素材</p>
    <div v-else class="result-grid">
      <article v-for="item in result.items" :key="item.position" class="asset-item">
        <template v-if="item.status !== 'UNAVAILABLE' && item.assetRef">
          <button
            v-if="item.display"
            type="button"
            class="asset-preview"
            aria-label="预览素材"
            @click="$emit('preview', contentUrl(item.display.contentRef))"
          >
            <img
              :src="contentUrl(item.display.contentRef)"
              alt="团队素材预览"
              loading="lazy"
              @error="$emit('previewError')"
            />
          </button>
          <div class="asset-facts">
            <strong v-if="item.verifiedFacts?.width && item.verifiedFacts?.height"
              >{{ item.verifiedFacts.width }} × {{ item.verifiedFacts.height }}</strong
            >
            <span v-if="item.verifiedFacts?.dominantColor" class="color-fact">
              <i :style="{ backgroundColor: item.verifiedFacts.dominantColor }" />{{
                item.verifiedFacts.dominantColor
              }}
            </span>
            <span v-if="item.status === 'STALE'" class="stale">依据已过期，请重新检索</span>
            <span v-else>符合筛选条件</span>
          </div>
        </template>
        <p v-else class="unavailable">素材不可用</p>
      </article>
    </div>
    <footer>检索依据来自图片记录</footer>
  </section>
</template>

<script setup lang="ts">
import type { AssetResult } from '@/api-custom/assetSearch'
import { apiUrl } from '@/apiOrigin'

defineProps<{ result: AssetResult }>()
defineEmits<{ refresh: []; preview: [url: string]; previewError: [] }>()
const contentUrl = (ref: string) => apiUrl(ref.startsWith('/api/') ? ref : `/api${ref}`)
</script>

<style scoped>
.asset-result {
  padding: 16px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 14px;
  background: var(--color-bg-elevated);
}
.result-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}
.eyebrow {
  color: var(--color-text-tertiary);
  font-size: 11px;
  letter-spacing: 0.12em;
}
h4 {
  margin: 4px 0 0;
  font-family: var(--font-display);
  color: var(--color-text-primary);
}
.result-heading button {
  border: 0;
  background: transparent;
  color: var(--color-primary-500);
  cursor: pointer;
  white-space: nowrap;
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}
.asset-item {
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: 10px;
}
.asset-preview {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: var(--color-bg-primary);
  cursor: zoom-in;
}
.asset-preview img {
  display: block;
  width: 100%;
  aspect-ratio: 16/10;
  object-fit: cover;
}
.asset-facts {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.asset-facts strong {
  color: var(--color-text-primary);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}
.color-fact {
  display: flex;
  align-items: center;
  gap: 6px;
}
.color-fact i {
  width: 10px;
  height: 10px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 50%;
}
.stale {
  color: var(--color-warning, #a66a20);
}
.unavailable,
.result-empty {
  padding: 20px 12px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}
footer {
  margin-top: 12px;
  color: var(--color-text-tertiary);
  font-size: 10px;
}
button:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 3px;
}
</style>
