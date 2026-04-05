<template>
  <canvas ref="canvasRef" class="ambient-bg shader-canvas fixed inset-0 -z-10" />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ShaderMount, meshGradientFragmentShader, getShaderColorFromString } from '@paper-design/shaders'
import { useTheme } from '@/composables/useTheme'

const canvasRef = ref<HTMLCanvasElement>()
const { currentTheme } = useTheme()
let shader: ShaderMount | null = null

const AURORA_COLORS = ['#030712', '#0a0f1a', '#2268f5', '#6e35eb', '#a855f7']
const POP_COLORS = ['#FAF7FF', '#F5F3FF', '#a855f7', '#ec4899', '#f472b6']

const initShader = () => {
  if (!canvasRef.value) return
  const colors = currentTheme.value === 'aurora' ? AURORA_COLORS : POP_COLORS
  const vec4Colors = colors.map(c => getShaderColorFromString(c))
  shader = new ShaderMount(canvasRef.value, meshGradientFragmentShader, {
    u_colors: vec4Colors,
    u_colorsCount: colors.length,
    u_distortion: 0.72,
    u_swirl: 0.25,
    u_grainMixer: 0.06,
    u_grainOverlay: 0.06,
    u_fit: 2, // cover
    u_scale: 1,
    u_rotation: 0,
    u_offsetX: 0,
    u_offsetY: 0
  }, 0.18) // speed
}

onMounted(() => {
  initShader()
})

watch(currentTheme, () => {
  shader?.stop?.()
  initShader()
})

onUnmounted(() => shader?.stop?.())
</script>
