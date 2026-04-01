<template>
  <header class="agent-header">
    <div class="header-left">
      <div class="header-orb" aria-hidden="true">
        <span class="orb-core"></span>
      </div>
      <div class="header-title-group">
        <h1 class="header-title">智能助手</h1>
        <span class="header-subtitle">VisionSpace Agent</span>
      </div>
    </div>

    <div class="header-actions">
      <button class="icon-btn" @click="emit('openHistory')" title="历史记录" aria-label="历史记录">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10" />
          <polyline points="12 6 12 12 16 14" />
        </svg>
      </button>

      <button class="new-chat-btn" @click="emit('newChat')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </svg>
        <span>新对话</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'newChat'): void
  (e: 'openHistory'): void
}>()
</script>

<style scoped>
.agent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  position: relative;
  z-index: 10;
  border-bottom: 1px solid var(--color-border-subtle);
  background: var(--glass-bg, rgba(10, 15, 26, 0.75));
  backdrop-filter: var(--glass-blur, blur(20px));
  -webkit-backdrop-filter: var(--glass-blur, blur(20px));
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* Agent Orb — animated identity marker */
.header-orb {
  position: relative;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.orb-core {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: var(--gradient-aurora);
  animation: orbPulse 3s ease-in-out infinite;
}

[data-theme="aurora"] .orb-core {
  box-shadow: 0 0 20px rgba(34, 104, 245, 0.35), 0 0 40px rgba(110, 53, 235, 0.15);
}

[data-theme="pop"] .orb-core {
  box-shadow: 0 0 16px rgba(168, 85, 247, 0.25), 0 0 32px rgba(236, 72, 153, 0.1);
}

@keyframes orbPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

.header-title-group {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.header-title {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
  letter-spacing: -0.01em;
}

.header-subtitle {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-text-tertiary);
  letter-spacing: 0.03em;
}

/* Action buttons */
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: all var(--transition-base, 200ms ease);
}

.icon-btn:hover {
  background: var(--color-bg-hover, var(--color-bg-secondary));
  color: var(--color-text-primary);
}

[data-theme="aurora"] .icon-btn:hover {
  background: var(--color-bg-hover, #243044);
}

.new-chat-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: none;
  border-radius: 10px;
  background: var(--gradient-aurora);
  color: white;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-base, 200ms ease);
  letter-spacing: 0.01em;
}

.new-chat-btn:hover {
  transform: translateY(-1px);
  filter: brightness(1.1);
}

[data-theme="aurora"] .new-chat-btn:hover {
  box-shadow: 0 0 20px rgba(34, 104, 245, 0.3);
}

[data-theme="pop"] .new-chat-btn:hover {
  box-shadow: 0 4px 16px rgba(168, 85, 247, 0.25);
}

.new-chat-btn:active {
  transform: translateY(0);
}
</style>
