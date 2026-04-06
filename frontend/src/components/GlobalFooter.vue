<template>
  <!-- 底部 -->
  <footer class="global-footer">
    <!-- 链接网格 -->
    <div class="footer-grid">
      <!-- Logo 列 -->
      <div class="footer-brand">
        <div class="footer-logo">
          <img src="@/assets/logo.svg" alt="VisionSpace" />
          <span class="footer-logo-text text-gradient">VisionSpace</span>
        </div>
        <p class="footer-description">企业级智能协同图片空间</p>
      </div>

      <!-- 产品列 -->
      <div class="footer-section">
        <h4 class="footer-title">产品</h4>
        <ul class="footer-links">
          <li><a href="/">主页</a></li>
          <li><a href="/add_space">创建空间</a></li>
          <li><a href="/add_picture">上传图片</a></li>
        </ul>
      </div>

      <!-- 支持列 -->
      <div class="footer-section">
        <h4 class="footer-title">支持</h4>
        <ul class="footer-links">
          <li><a href="#">帮助中心</a></li>
          <li><a href="#">API 文档</a></li>
          <li><a href="#">联系我们</a></li>
        </ul>
      </div>

      <!-- 关于列 -->
      <div class="footer-section">
        <h4 class="footer-title">关于</h4>
        <ul class="footer-links">
          <li><a href="#">关于我们</a></li>
          <li><a href="#">隐私政策</a></li>
          <li><a href="#">服务条款</a></li>
        </ul>
      </div>
    </div>

    <div class="footer-bottom">
      <p>&copy; 2026 VisionSpace. Made with ❤️ by 周can & yuwen</p>
    </div>
  </footer>
</template>

<script setup lang="ts"></script>

<style lang="less" scoped>
.global-footer {
  background: var(--glass-bg);
  backdrop-filter: var(--glass-blur);
  border-top: 1px solid var(--color-border-subtle);
  padding: var(--space-12) var(--space-6) var(--space-6);
  width: 100%;
}

/* ========== Logo 区域 ========== */
.footer-brand {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  margin-left: var(--space-10);
}

.footer-grid {
  max-width: var(--container-2xl);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.8fr 1fr 1fr 1fr;
  gap: var(--space-6) var(--space-5);
  margin-bottom: var(--space-10);
}

.footer-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.footer-logo img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
}

.footer-logo-text {
  font-family: var(--font-brand);
  font-size: var(--text-3xl);
  font-weight: var(--font-bold);
}

.footer-description {
  font-size: var(--text-lg);
  color: var(--color-text-tertiary);
  line-height: var(--leading-relaxed);
}

.footer-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--space-2);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.footer-links {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.footer-links li a {
  font-size: var(--text-sm);
  color: var(--color-text-tertiary);
  transition: all var(--transition-fast);
  display: inline-block;

  &:hover {
    color: var(--color-primary-400);
    transform: translateX(4px);
  }
}

.footer-bottom {
  max-width: var(--container-2xl);
  margin: 0 auto;
  padding-top: var(--space-6);
  border-top: 1px solid var(--color-border-subtle);
  text-align: center;

  p {
    font-size: var(--text-sm);
    color: var(--color-text-disabled);
    margin: 0;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .footer-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-6);
  }

  .footer-brand {
    grid-column: span 3;
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    margin-bottom: var(--space-4);
    margin-left: var(--space-10);
  }

  .footer-logo {
    margin-right: var(--space-4);
  }

  .footer-logo img {
    width: 44px;
    height: 44px;
  }

  .footer-logo-text {
    font-size: var(--text-xl);
  }

  .footer-description {
    font-size: var(--text-base);
  }

  .footer-section {
    gap: var(--space-5);
  }

  .footer-title {
    font-size: var(--text-base);
    margin-bottom: var(--space-3);
  }

  .footer-links {
    gap: var(--space-3);
  }

  .footer-links li a {
    font-size: var(--text-sm);
  }
}

@media (max-width: 640px) {
  .footer-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }

  .footer-brand {
    grid-column: span 3;
  }
}
</style>
