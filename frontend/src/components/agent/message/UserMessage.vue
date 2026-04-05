<template>
  <div class="user-message">
    <div class="message-content">
      <!-- Image grid -->
      <div v-if="images && images.length > 0" class="message-images" :class="`grid-${Math.min(images.length, 4)}`">
        <div v-for="(img, i) in images.slice(0, 4)" :key="i" class="img-thumb">
          <img :src="img" alt="附件图片" />
        </div>
        <div v-if="images.length > 4" class="img-more">+{{ images.length - 4 }}</div>
      </div>
      <div v-if="content" class="message-text">{{ content }}</div>
      <div class="message-time">{{ displayTime }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  content: string
  time?: string
  images?: string[]
}

const props = withDefaults(defineProps<Props>(), {
  content: '',
  time: '',
  images: () => [],
})

const displayTime = computed(() => {
  if (props.time) return props.time
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})
</script>

<style scoped>
.user-message {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 8px 16px;
}

.message-content {
  max-width: 70%;
  padding: 10px 16px 8px;
  /* 深空霓虹气泡：深色底 + 顶部微光 + 左侧描边 */
  background: var(--color-bg-elevated);
  border: 1px solid;
  border-color: transparent;
  border-radius: 18px 18px 4px 18px;
  position: relative;
  overflow: hidden;
  word-break: break-word;
}

/* 顶部渐变光带 — 模拟深空霓虹 */
.message-content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(34, 104, 245, 0.5) 20%,
    rgba(110, 53, 235, 0.5) 50%,
    rgba(168, 85, 247, 0.3) 80%,
    transparent 100%
  );
  pointer-events: none;
}

/* 左侧边缘微光 */
.message-content::after {
  content: '';
  position: absolute;
  top: 8px;
  left: 0;
  bottom: 8px;
  width: 1.5px;
  background: linear-gradient(
    180deg,
    rgba(34, 104, 245, 0.4) 0%,
    rgba(110, 53, 235, 0.2) 50%,
    transparent 100%
  );
  border-radius: 1px;
  pointer-events: none;
}

.message-text {
  font-size: 14px;
  line-height: 1.55;
  color: var(--color-text-primary);
  padding: 2px 0;
}

.message-time {
  font-size: 11px;
  opacity: 0.5;
  margin-top: 4px;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-tertiary);
}

/* ============ Image Grid ============ */
.message-images {
  display: grid;
  gap: 4px;
  margin-bottom: 6px;
  border-radius: 12px;
  overflow: hidden;
}

.message-images.grid-1 { grid-template-columns: 1fr; }
.message-images.grid-2 { grid-template-columns: 1fr 1fr; }
.message-images.grid-3 { grid-template-columns: 1fr 1fr; }
.message-images.grid-4 { grid-template-columns: 1fr 1fr; }

.img-thumb {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.1);
}

.img-thumb img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  display: block;
}

/* 3-image layout: first image spans full width */
.grid-3 .img-thumb:first-child {
  grid-column: 1 / -1;
}

.img-more {
  position: absolute;
  bottom: 4px;
  right: 4px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 8px;
}

.message-images:has(.img-more) {
  position: relative;
}
</style>
