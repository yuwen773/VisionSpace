<template>
  <section class="record" :aria-labelledby="headingId">
    <header class="record-head">
      <div class="record-heading">
        <span class="record-index mono" aria-hidden="true">{{ index }}</span>
        <h2 :id="headingId" class="record-title">{{ title }}</h2>
      </div>
      <span class="record-label mono">{{ label }}</span>
    </header>
    <p v-if="lede" class="record-lede">{{ lede }}</p>
    <div class="record-body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    index: string
    title: string
    label: string
    lede?: string
  }>(),
  { lede: '' },
)

const headingId = computed(() => `uc-record-${props.index}`)
</script>

<style scoped>
.record {
  padding-top: clamp(8px, 1.6vh, 18px);
}

.record-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-6);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--uc-line);
}

.record-heading {
  display: flex;
  align-items: baseline;
  gap: clamp(12px, 1.4vw, 20px);
  min-width: 0;
}

.record-index {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--uc-accent);
  flex-shrink: 0;
}

.record-title {
  margin: 0;
  font-family: var(--uc-serif);
  font-size: clamp(30px, 3.4vw, 46px);
  font-weight: 600;
  line-height: 1.16;
  letter-spacing: 0.01em;
  color: var(--uc-ink);
  text-wrap: balance;
}

.record-label {
  flex-shrink: 0;
  font-size: 10px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--uc-ink-3);
  text-align: right;
}

.record-lede {
  max-width: 60ch;
  margin: var(--space-4) 0 0;
  font-size: var(--text-base);
  line-height: 1.9;
  color: var(--uc-ink-2);
  text-wrap: pretty;
}

.record-body {
  padding-top: clamp(24px, 4vh, 40px);
}

@media (max-width: 640px) {
  .record-head {
    align-items: baseline;
    flex-wrap: wrap;
    gap: var(--space-2);
  }

  .record-label {
    flex-basis: 100%;
    text-align: left;
  }
}
</style>
