<script setup lang="ts">
import { motion } from 'motion-v'
import PulsingBorder from './shaders/PulsingBorder.vue'
import NeuroNoiseOverlay from './shaders/NeuroNoiseOverlay.vue'
import { useVisualEnhance } from '@/composables/useVisualEnhance'
import type { API } from '@/api/typings'

const { springConfig } = useVisualEnhance()

interface Props {
  pic: API.PictureVO
  showCard?: boolean
  imageLoaded?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showCard: false,
  imageLoaded: false
})

const emit = defineEmits<{
  (e: 'click', pic: API.PictureVO): void
  (e: 'mouseenter', id: string | number): void
  (e: 'mouseleave', id: string | number): void
}>()
</script>

<template>
  <motion.div
    class="pop-card motion-pop-card picture-card"
    :initial="{ opacity: 0, y: 40, scale: 0.92 }"
    :animate="{ opacity: 1, y: 0, scale: 1 }"
    :whileHover="{ y: -12, scale: 1.04, boxShadow: 'var(--shadow-glow-md)' }"
    :transition="springConfig"
    @click="emit('click', pic)"
    @mouseenter="emit('mouseenter', pic.id)"
    @mouseleave="emit('mouseleave', pic.id)"
  >
    <PulsingBorder>
      <div class="picture-container relative overflow-hidden">
        <img
          :src="pic.thumbnailUrl || pic.url"
          class="picture-image"
          :class="{ 'image-loaded': imageLoaded }"
        />
        <NeuroNoiseOverlay />
      </div>
    </PulsingBorder>
  </motion.div>
</template>
