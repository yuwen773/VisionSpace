import { useMotionValue } from 'motion-v'

export const useVisualEnhance = () => {
  const springConfig = {
    type: 'spring' as const,
    stiffness: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--spring-stiffness') || '380'),
    damping: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--spring-damping') || '26')
  }

  const stagger = (delay = 0.06) => ({
    transition: { ...springConfig, delay }
  })

  return {
    springConfig,
    stagger,
    createMotion: useMotionValue
  }
}
