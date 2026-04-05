<template>
  <canvas ref="canvasRef" class="shader-canvas absolute inset-0 opacity-30 mix-blend-screen rounded-[var(--radius-2xl)]" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { createShader } from '@paper-design/shaders'

const canvasRef = ref<HTMLCanvasElement>()
let shader: any = null

onMounted(() => {
  if (!canvasRef.value) return
  shader = createShader(canvasRef.value, {
    type: 'neuro-noise',
    colors: ['#a855f7', '#2268f5'],
    speed: 0.12,
    grain: 0.65,
    distortion: 0.3
  })
})

onUnmounted(() => shader?.stop?.())
</script>
