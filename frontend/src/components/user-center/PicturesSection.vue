<template>
  <RecordSection
    index="02"
    title="我的图片"
    label="ARCHIVE"
    lede="你的上传、收藏与审核记录。最近六件作品同时作为封面印样出现在页首；点开任意一张继续查看。"
  >
    <!-- 最近上传 -->
    <div class="sheet">
      <div class="uc-subhead sheet-head">
        <span>最近上传</span>
        <span class="mono sheet-count">
          <span>RECENT / {{ pictures.length.toString().padStart(2, '0') }}</span>
          <span v-if="!loading" class="sheet-pass">
            PASSED {{ stats.reviewPassCount ?? 0 }} / {{ stats.uploadCount ?? 0 }}
          </span>
        </span>
      </div>

      <p v-if="loading" class="sheet-status mono" role="status">正在读取档案…</p>

      <ul v-if="pictures.length" class="archive-grid">
        <li v-for="(pic, index) in pictures" :key="pic.id">
          <button
            type="button"
            class="archive-item"
            :aria-label="`打开作品：${pic.name || '未命名'}`"
            @click="pic.id && $emit('goToPicture', pic.id)"
          >
            <span class="archive-frame">
              <img
                :src="contentSrc(pic.thumbnailUrl || pic.url)"
                :alt="pic.name || '作品缩略图'"
                loading="lazy"
                decoding="async"
              />
              <span class="archive-open mono" aria-hidden="true">OPEN →</span>
            </span>
            <span class="archive-caption">
              <span class="archive-name">{{ pic.name || '未命名作品' }}</span>
              <span class="archive-no mono">{{ String(index + 1).padStart(2, '0') }}</span>
            </span>
          </button>
        </li>
      </ul>

      <div v-else-if="!loading" class="archive-empty">
        <span class="archive-empty-mark mono" aria-hidden="true">Ø</span>
        <div>
          <p class="archive-empty-title">还没有上传作品。</p>
          <p class="uc-hint">
            上传第一张图片，它会出现在这里和页首的接触印样上。之后可以按颜色或语义把它找回来。
          </p>
          <button type="button" class="uc-btn uc-btn--solid empty-action" @click="$emit('goToAdd')">
            上传作品
          </button>
        </div>
      </div>
    </div>
  </RecordSection>
</template>

<script setup lang="ts">
import { contentSrc } from '@/utils'
import RecordSection from '@/components/user-center/RecordSection.vue'

defineProps<{
  stats: API.UserPictureStatsResponse
  pictures: API.PictureVO[]
  loading: boolean
}>()

defineEmits<{
  goToPicture: [id: number]
  goToAdd: []
}>()
</script>

<style scoped>
/* ============ 档案网格 ============ */
.sheet-head {
  gap: var(--space-4);
}

.sheet-count {
  display: flex;
  align-items: baseline;
  gap: clamp(12px, 2vw, 24px);
  text-align: right;
}

.sheet-pass {
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--uc-ink-3);
}

.sheet-status {
  margin: 0 0 var(--space-4);
  font-size: 10px;
}

.archive-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(14px, 2.4vw, 28px);
  margin: 0;
  padding: 0;
  list-style: none;
}

.archive-item {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--uc-ink);
  text-align: left;
  cursor: pointer;
}

.archive-frame {
  position: relative;
  display: block;
  aspect-ratio: 4 / 5;
  border: 1px solid var(--uc-line);
  background: var(--uc-bg-raised);
  overflow: hidden;
}

.archive-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.88) contrast(1.02);
  transition:
    transform 0.6s var(--uc-ease),
    filter 0.4s ease;
}

.archive-item:hover .archive-frame img,
.archive-item:focus-visible .archive-frame img {
  transform: scale(1.035);
  filter: saturate(1) contrast(1.02);
}

.archive-open {
  position: absolute;
  right: 10px;
  bottom: 10px;
  font-size: 9px;
  letter-spacing: 0.16em;
  color: #fff;
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 0.26s var(--uc-ease),
    transform 0.26s var(--uc-ease);
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.55);
}

.archive-item:hover .archive-open,
.archive-item:focus-visible .archive-open {
  opacity: 1;
  transform: translateY(0);
}

.archive-caption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0 0;
}

.archive-name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--uc-ink-2);
}

.archive-no {
  flex-shrink: 0;
  font-size: 9px;
}

/* ============ 空状态 ============ */
.archive-empty {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: clamp(20px, 4vw, 40px);
  align-items: start;
  padding: clamp(28px, 5vw, 48px);
  border: 1px solid var(--uc-line);
  background-image: repeating-linear-gradient(
    -45deg,
    transparent 0,
    transparent 15px,
    rgba(244, 241, 234, 0.035) 15px,
    rgba(244, 241, 234, 0.035) 16px
  );
}

.is-light .archive-empty {
  background-image: repeating-linear-gradient(
    -45deg,
    transparent 0,
    transparent 15px,
    rgba(124, 58, 237, 0.05) 15px,
    rgba(124, 58, 237, 0.05) 16px
  );
}

.archive-empty-mark {
  font-family: var(--uc-serif);
  font-size: 56px;
  line-height: 0.9;
  color: var(--uc-ink-3);
}

.archive-empty-title {
  margin: 0 0 10px;
  font-family: var(--uc-serif);
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 600;
  color: var(--uc-ink);
}

.empty-action {
  margin-top: var(--space-5);
}

@media (max-width: 900px) {
  .archive-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .archive-empty {
    grid-template-columns: minmax(0, 1fr);
  }

  .archive-empty-mark {
    font-size: 40px;
  }
}
</style>
