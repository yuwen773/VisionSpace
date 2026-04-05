# VisionSpace 全新品牌介绍首页（LandingPage）设计文档

**日期**: 2026/04/05
**状态**: 已批准
**版本**: v1.0

---

## 1. 概述与目标

**页面目标**：在用户打开的 5 秒内，建立强烈品牌印象 —— "这是一个有灵魂、有生命力、充满视觉灵感的 AI 图片平台"。

**技术选型**：Vue Bits 组件（MIT 开源协议，copy-paste 方式集成到 `src/components/vue-bits/`）

---

## 2. 推荐组件组合（5 个）

| 组件 | 用途 | 性能开销 | 位置 |
|------|------|----------|------|
| **Splash Cursor** | 全站鼠标粒子溅射 | 轻量 | 全局，开启 |
| **Floating Lines** | Hero 主背景 | 中等 | Hero Section |
| **Image Trail** | 标题区域 + AI Magic 灵动感 | 轻量 | Hero + AI Magic |
| **Card Swap** | 功能卡片悬停翻转 | 轻量 | Signature Experiences |
| **Circular Gallery** | 圆形旋转画廊 | 较重（移动端降级） | Visual Universe |

所有组件颜色通过 CSS Variables（`--color-primary`、`--color-secondary` 等）注入，自动适配双主题。

---

## 3. 页面结构（6 大板块）

### 3.1 Hero Section（全屏沉浸式开场）

- **高度**: 100vh
- **背景**: Floating Lines
- **内容**:
  - 超大品牌标题 + Slogan（"你的视觉灵感宇宙"）
  - Splash Cursor（全屏轻度）
  - Image Trail（标题区域跟随鼠标）
  - 两个醒目 CTA 按钮：
    - 「进入探索」→ 跳转 `/explore`
    - 「唤醒 SpaceMind」→ 跳转 `/agent`
- **风格**: 极简、高冲击力、视觉优先
- **注意**: 不放置搜索胶囊和快捷入口

### 3.2 Brand Essence（品牌灵魂）

- **布局**: 左右结构
- **左侧**: 品牌故事文案（3-4 段，突出 AI + 协作 + 灵感）
- **右侧**: Orbit Images（缓慢环绕的代表性图片，宇宙感）
- **背景**: 轻微渐变 + 玻璃质感

### 3.3 Signature Experiences（核心体验亮点）

- **布局**: 横向 4 个大卡片
- **组件**: Card Swap（悬停时卡片翻转展示更多信息）
- **内容**:
  1. 智能色彩搜索
  2. AI Agent
  3. 空间实时协作
  4. AI 扩图

### 3.4 Visual Universe（视觉宇宙展示）← 重点板块

- **标题**: "来自全球创作者的灵感时刻"
- **组件**: Circular Gallery（圆形旋转画廊）
- **内容**: 8-12 张精选高质量图片（真实数据）
- **交互**: 支持自动旋转 + hover 交互
- **备选**: 移动端降级为静态 Masonry 布局

### 3.5 AI Magic（AI 魔法时刻）

- **标题**: "让 AI 成为你的视觉伙伴"
- **布局**: 左右结构
- **左侧**: AI Agent 对话示例（真实数据）
- **右侧**: 扩图前后对比 + Image Trail 增强效果

### 3.6 Final CTA（结束号召）

- **内容**:
  - 大标题: "准备好开启你的视觉之旅了吗？"
  - 两个大按钮: "立即进入探索" / "免费体验 SpaceMind"
- **视觉**: 渐变背景 + 轻微粒子效果收尾

---

## 4. 双主题适配

| 主题 | 风格描述 | 组件表现 |
|------|----------|----------|
| **Aurora（极光）** | 冷调蓝紫 + 更强线条流动感 | Floating Lines 流速更快、颜色更冷 |
| **Pop（紫漾）** | 粉紫柔光 + 更梦幻粒子和旋转速度 | Circular Gallery 旋转更柔缓、粒子更梦幻 |

所有组件颜色通过 CSS Variables 统一注入，无需硬编码颜色值。

---

## 5. 性能处理

- **Circular Gallery**: 移动端自动降低动画帧率或切换为静态 Masonry 布局
- **Splash Cursor**: 移动端禁用（touch 设备无鼠标事件）
- **Image Trail**: 降级为纯 CSS hover 效果（减少 JS 计算）
- **Floating Lines**: 移动端减少线条数量

---

## 6. 文件结构

```
frontend/src/
├── components/vue-bits/
│   ├── animations/
│   │   ├── SplashCursor.vue
│   │   ├── ImageTrail.vue
│   │   └── FloatingLines.vue
│   ├── components/
│   │   ├── CardSwap.vue
│   │   └── CircularGallery.vue
│   └── OrbitImages.vue
│
├── pages/
│   └── LandingPage.vue  (重写)
│
└── styles/
    └── (design tokens 已存在)
```

---

## 7. 数据来源

- **Visual Universe 画廊**: 调用图片列表 API，选取热门/精选图片
- **AI Magic 对话示例**: 调用 AI Agent 历史记录 API
- **Brand Essence 图片**: 从图库中选取 3-5 张代表性图片用于 Orbit Images

---

## 8. 路由配置（已存在，无需修改）

| 路由 | 页面 | 状态 |
|------|------|------|
| `/` | LandingPage.vue | 新设计 |
| `/explore` | ExplorePage.vue | 现有 |
