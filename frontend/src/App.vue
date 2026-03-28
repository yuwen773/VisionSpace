<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
// 权限校验
import './access.ts'
</script>

<style>
/* 根容器基础样式 */
#app {
  min-height: 100vh;
  width: 100%;
  background: var(--color-bg-primary);
  color: var(--color-text-primary);
  font-family: var(--font-body);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  position: relative;
}

/* 防止页面双滚动条 */
html, body {
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: var(--color-bg-primary);
}
</style>
