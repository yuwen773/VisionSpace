# 用户中心设计方案

## 1. 概述

为 VisionSpace 项目新增用户中心页面，提供独立的个人资料管理、图片统计查看、会员权益展示功能。

## 2. 页面结构

- **页面路径**: `/user/center`
- **布局**: 左侧菜单 + 右侧内容区
- **主题**: 紫漾+极光双主题，与系统保持一致
- **访问权限**: 仅登录用户可访问，未登录自动跳转登录页

## 3. 菜单导航

| 菜单项 | 描述 |
|--------|------|
| 个人资料 | 头像、昵称、简介的查看与内联编辑 |
| 我的图片 | 统计数据 + 最近上传图片 |
| 会员权益 | Mock 展示会员状态 |

## 4. 功能详情

### 4.1 个人资料

**展示内容**:
- 头像（圆形裁剪）
- 昵称
- 简介
- 账号（只读）
- 创建时间（只读）

**编辑交互**:
- 头像：点击头像弹出上传框，选择图片后保存
- 昵称：点击昵称进入编辑状态，显示输入框 + 确认/取消按钮
- 简介：点击简介进入编辑状态，显示文本框 + 确认/取消按钮
- 编辑完成后点击确认才真正保存

**API 调用**:
| 接口 | 用途 |
|------|------|
| `GET /user/get/vo` | 获取当前用户信息 |
| `POST /user/update` | 更新用户资料 |

### 4.2 我的图片

**统计卡片**:
- 上传数量
- 收藏数量
- 审核通过率

**最近图片**:
- 网格展示最近上传的 6 张图片
- 点击跳转图片详情页

**API 调用**:
| 接口 | 用途 |
|------|------|
| `GET /user/picture/stats` | 新增 — 获取图片统计数据 |
| `GET /user/picture/recent` | 新增 — 获取最近上传图片 |

### 4.3 会员权益（Mock）

**展示内容**:
- 会员等级标签（普通用户 / VIP）
- 会员到期时间

**说明**: 纯展示数据，暂无真实会员业务逻辑。

## 5. 后端改动

### 5.1 新增接口

**UserController**:

```java
/**
 * 获取用户图片统计
 */
@GetMapping("/picture/stats")
public BaseResponse<UserPictureStatsResponse> getUserPictureStats(HttpServletRequest request);

/**
 * 获取用户最近上传图片
 */
@GetMapping("/picture/recent")
public BaseResponse<List<PictureVO>> getUserRecentPictures(@RequestParam("count") int count, HttpServletRequest request);
```

### 5.2 新增 DTO

- `UserPictureStatsResponse` — 用户图片统计响应

## 6. 前端改动

### 6.1 新增页面

- `frontend/src/pages/user/UserCenterPage.vue` — 用户中心主页面

### 6.2 路由配置

```javascript
{
  path: '/user/center',
  name: 'UserCenter',
  component: () => import('../pages/user/UserCenterPage.vue'),
  meta: { requiresAuth: true }
}
```

## 7. 设计原则

- YAGNI: 仅实现上述功能，不做扩展
- 复用现有 UserController 接口
- 前端使用 Vue 3 + TypeScript + Ant Design Vue
- 主题样式继承系统现有变量
