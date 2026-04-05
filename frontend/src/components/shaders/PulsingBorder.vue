<template>
  <div class="relative" ref="containerRef">
    <slot />
    <canvas ref="canvasRef" class="shader-canvas absolute inset-0 rounded-[var(--radius-2xl)]" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import {
  ShaderMount,
  getShaderColorFromString,
  getShaderNoiseTexture,
  pulsingBorderFragmentShader,
  PulsingBorderAspectRatios,
} from '@paper-design/shaders'

const props = withDefaults(defineProps<{
  colors?: string[]
  speed?: number
  pulse?: number
  bloom?: number
  roundness?: number
  thickness?: number
  intensity?: number
  smoke?: number
  smokeSize?: number
}>(), {
  colors: () => ['#a855f7', '#7c3aed'],
  speed: 0.45,
  pulse: 0.38,
  bloom: 0.55,
  roundness: 0,
  thickness: 0.5,
  intensity: 0.5,
  smoke: 0,
  smokeSize: 0.5,
})

const containerRef = ref<HTMLElement>()
const canvasRef = ref<HTMLCanvasElement>()
let shader: ShaderMount | null = null

const parseColor = (color: string): [number, number, number, number] => {
  if (color.startsWith('var(')) {
    const varName = color.match(/var\(--([^)]+)\)/)?.[1]
    if (varName) {
      const value = getComputedStyle(document.documentElement).getPropertyValue(varName).trim()
      return getShaderColorFromString(value || '#000000')
    }
  }
  return getShaderColorFromString(color)
}

onMounted(() => {
  if (!canvasRef.value || !containerRef.value) return

  const noiseTexture = getShaderNoiseTexture()
  const parsedColors = props.colors.map(parseColor)

  shader = new ShaderMount(containerRef.value, pulsingBorderFragmentShader, {
    u_noiseTexture: noiseTexture,
    u_colorBack: [0, 0, 0, 0],
    u_colors: parsedColors,
    u_colorsCount: parsedColors.length,
    u_roundness: props.roundness,
    u_thickness: props.thickness,
    u_intensity: props.intensity,
    u_bloom: props.bloom,
    u_pulse: props.pulse,
    u_smoke: props.smoke,
    u_smokeSize: props.smokeSize,
    u_marginLeft: 0,
    u_marginRight: 0,
    u_marginTop: 0,
    u_marginBottom: 0,
    u_aspectRatio: PulsingBorderAspectRatios.auto,
    u_spots: 4,
    u_spotSize: 0.5,
  }, {}, props.speed)
})

onUnmounted(() => {
  shader?.dispose()
})
</script>
