<template>
  <canvas ref="canvasRef" class="theme-transition-canvas fixed inset-0 pointer-events-none z-[9999]" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const canvasRef = ref<HTMLCanvasElement>()
let animationId: number | null = null
let particles: Particle[] = []

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  size: number
  opacity: number
  color: string
}

const COLORS_AURORA = ['#2268f5', '#6e35eb', '#a855f7', '#00d4aa']
const COLORS_POP = ['#a855f7', '#ec4899', '#f472b6', '#fb923c']

const createParticles = (theme: string) => {
  const canvas = canvasRef.value
  if (!canvas) return
  const colors = theme === 'aurora' ? COLORS_AURORA : COLORS_POP
  particles = []
  const count = 60

  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 8,
      vy: (Math.random() - 0.5) * 8,
      size: Math.random() * 4 + 2,
      opacity: 1,
      color: colors[Math.floor(Math.random() * colors.length)]
    })
  }
}

const animate = () => {
  const canvas = canvasRef.value
  const ctx = canvas?.getContext('2d')
  if (!canvas || !ctx) return

  ctx.clearRect(0, 0, canvas.width, canvas.height)

  particles.forEach(p => {
    p.x += p.vx
    p.y += p.vy
    p.opacity -= 0.02
    p.vx *= 0.98
    p.vy *= 0.98

    if (p.opacity > 0) {
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = p.color
      ctx.globalAlpha = p.opacity
      ctx.fill()
    }
  })

  particles = particles.filter(p => p.opacity > 0)

  if (particles.length > 0) {
    animationId = requestAnimationFrame(animate)
  }
}

const handleThemeChange = (e: CustomEvent) => {
  const canvas = canvasRef.value
  if (!canvas) return
  canvas.width = window.innerWidth
  canvas.height = window.innerHeight
  createParticles(e.detail.theme)
  animate()
}

onMounted(() => {
  window.addEventListener('theme-changed', handleThemeChange as EventListener)
})

onUnmounted(() => {
  window.removeEventListener('theme-changed', handleThemeChange as EventListener)
  if (animationId) cancelAnimationFrame(animationId)
})
</script>

<style scoped>
.theme-transition-canvas {
  mix-blend-mode: screen;
}
</style>
