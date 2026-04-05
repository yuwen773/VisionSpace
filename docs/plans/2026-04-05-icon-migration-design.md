# Icon 迁移设计：Emoji → Lucide Icons

日期: 2026-04-05

## 背景

当前项目使用 Emoji 字符作为导航菜单、侧边栏、按钮等 UI 元素的图标。Emoji 存在跨平台渲染不一致、无法精确控制颜色/大小、专业感不足等问题，不适合作为产品级 UI 图标方案。

项目有两套主题：极光（深空黑科技风）和紫漾（紫粉梦幻风），Ant Design Icons 的写实商务风格不匹配。

## 方案选型

**选择 Lucide Icons (`lucide-vue-next`)**

- stroke-based 线条风格，与现有内联 SVG 风格统一
- Vue 3 原生组件，tree-shakeable
- `currentColor` 继承，自动跟随主题色
- 1800+ 图标，MIT 开源

## 迁移范围

### 需要替换（约 50 处，15 个文件）

| 位置 | 当前 Emoji | 替换为 Lucide |
|---|---|---|
| 导航菜单 | 🏠 📸 ☁️ 👥 🚀 | Home Camera Cloud Users Rocket |
| Admin 侧边栏 | 📊 👥 🖼️ ☁️ 💾 💬 | BarChart3 Users Image Cloud HardDrive MessageSquare |
| 下拉菜单 | 🏠 💬 ⚙️ 👋 | Home MessageSquare Settings LogOut |
| 登录/注册 | 👤 🔑 ✨ 🎉 🚀 🛡️ 🎨 | User KeyRound Sparkles PartyPopper Rocket Shield Palette |
| 反馈 | 💡 🚩 🎫 | Lightbulb Flag Ticket |
| 图片操作 | 📤 🔍 🗑️ 📁 📦 📐 📸 | Upload Search Trash2 Folder Package Ruler Camera |
| 主题切换 | 🌌 💜 🌙 | Moon Sparkles |
| 分享弹窗 | 📤 🔗 📱 💡 | Upload Link Smartphone Lightbulb |

### 不迁移

- **Toast/message 中的 emoji** — 提示文案中 emoji 合理
- **已有内联 SVG** — Agent 聊天 UI 等已和 Lucide 风格一致
- **装饰性 emoji** — MySpacePage 云朵动画等视觉装饰

## 涉及文件

| 文件 | 改动类型 |
|---|---|
| `GlobalHeader.vue` | 导航菜单、下拉菜单、登录按钮 |
| `AdminSidebar.vue` | 侧边栏菜单 emoji → 组件引用 |
| `AdminHeader.vue` | 面包屑、下拉菜单 |
| `UserLoginPage.vue` | 输入框图标、按钮 |
| `UserRegisterPage.vue` | 同上 |
| `FeedbackPage.vue` | 反馈类型选择 |
| `DashboardPage.vue` | 活动类型 |
| `AddPicturePage.vue` | 标题文案 |
| `AddSpacePage.vue` | 标题文案 |
| `ShareModal.vue` | 分享操作 |
| `PictureList.vue` | 图片操作按钮 |
| `PictureUpload.vue` | 上传区域 |
| `useTheme.ts` | 主题 icon 配置 |
| `FeedbackSection.vue` | 反馈类型 |

## 技术方案

### 安装

```bash
cd frontend && npm install lucide-vue-next
```

### 使用方式

```vue
<script setup>
import { Home, Camera } from 'lucide-vue-next'
</script>

<template>
  <Home :size="18" />
</template>
```

### 侧边栏数据驱动改造

```ts
import { BarChart3, Users } from 'lucide-vue-next'

const menuItems = [
  { path: '/admin/dashboard', label: '仪表盘', icon: BarChart3 },
  { path: '/admin/user_manage', label: '用户管理', icon: Users },
]
```

### 主题适配

Lucide 默认使用 `currentColor`，自动跟随 CSS `color` 属性，两套主题无需额外配置。
