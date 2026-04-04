# User Center Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 新增用户中心页面，提供个人资料管理、图片统计查看、会员权益展示功能

**Architecture:**
- 后端：复用现有 UserController，新增 `/user/picture/stats` 和 `/user/picture/recent` 接口
- 前端：新增 `UserCenterPage.vue`，使用左侧菜单 + 右侧内容布局
- 用户中心入口：GlobalHeader 下拉菜单添加"用户中心"选项

**Tech Stack:** Spring Boot + Vue 3 + TypeScript + Ant Design Vue

---

## Task 1: 后端 - 新增用户图片统计 DTO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/vo/UserPictureStatsResponse.java`

**Step 1: 创建 DTO 类**

```java
package com.yuwen.visionspace.model.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserPictureStatsResponse implements Serializable {
    private Long uploadCount;      // 上传数量
    private Long likeCount;       // 收藏数量（暂无实现，预留）
    private Long reviewPassCount; // 审核通过数量
    private Double reviewPassRate; // 审核通过率
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/vo/UserPictureStatsResponse.java
git commit -m "feat(user): add UserPictureStatsResponse DTO"
```

---

## Task 2: 后端 - UserController 新增接口

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/UserController.java`

**Step 1: 添加新接口方法**

在 UserController 末尾添加：

```java
/**
 * 获取用户图片统计
 */
@GetMapping("/picture/stats")
public BaseResponse<UserPictureStatsResponse> getUserPictureStats(HttpServletRequest request) {
    User loginUser = userService.getLoginUser(request);
    UserPictureStatsResponse stats = userService.getUserPictureStats(loginUser);
    return ResultUtils.success(stats);
}

/**
 * 获取用户最近上传图片
 */
@GetMapping("/picture/recent")
public BaseResponse<List<PictureVO>> getUserRecentPictures(@RequestParam("count") int count, HttpServletRequest request) {
    User loginUser = userService.getLoginUser(request);
    List<PictureVO> recentPictures = pictureService.getUserRecentPictures(loginUser.getId(), count);
    return ResultUtils.success(recentPictures);
}
```

**注意：** 需要注入 `PictureService` 和 `UserService`

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/controller/UserController.java
git commit -m "feat(user): add picture stats and recent pictures endpoints"
```

---

## Task 3: 后端 - UserService 新增方法

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/UserService.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/UserServiceImpl.java`

**Step 1: 在 UserService 接口添加方法签名**

```java
/**
 * 获取用户图片统计
 */
UserPictureStatsResponse getUserPictureStats(User user);
```

**Step 2: 在 UserServiceImpl 实现方法**

注入 `PictureMapper` 和 `SpaceMapper`，实现：

```java
@Override
public UserPictureStatsResponse getUserPictureStats(User user) {
    UserPictureStatsResponse stats = new UserPictureStatsResponse();
    // 查询该用户上传的图片总数
    Long uploadCount = pictureService.count(new QueryWrapper<Picture>()
        .eq("userId", user.getId()));
    stats.setUploadCount(uploadCount);

    // 查询审核通过数量
    Long reviewPassCount = pictureService.count(new QueryWrapper<Picture>()
        .eq("userId", user.getId())
        .eq("reviewStatus", 1)); // 1=通过
    stats.setReviewPassCount(reviewPassCount);

    // 计算通过率
    if (uploadCount > 0) {
        stats.setReviewPassRate(Math.round(reviewPassCount * 100.0 / uploadCount * 100) / 100.0);
    } else {
        stats.setReviewPassRate(0.0);
    }

    // 收藏数量暂无实现，预留
    stats.setLikeCount(0L);

    return stats;
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/UserService.java
git add src/main/java/com/yuwen/visionspace/service/impl/UserServiceImpl.java
git commit -m "feat(user): implement getUserPictureStats method"
```

---

## Task 4: 后端 - PictureService 新增方法

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/service/PictureService.java`
- Modify: `src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java`

**Step 1: 在 PictureService 接口添加方法签名**

```java
/**
 * 获取用户最近上传图片
 */
List<PictureVO> getUserRecentPictures(Long userId, int count);
```

**Step 2: 在 PictureServiceImpl 实现方法**

```java
@Override
public List<PictureVO> getUserRecentPictures(Long userId, int count) {
    QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("userId", userId)
                .orderByDesc("createTime")
                .last("LIMIT " + count);
    List<Picture> pictures = this.list(queryWrapper);
    return pictures.stream().map(picture -> {
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        return pictureVO;
    }).collect(Collectors.toList());
}
```

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/service/PictureService.java
git add src/main/java/com/yuwen/visionspace/service/impl/PictureServiceImpl.java
git commit -m "feat(picture): add getUserRecentPictures method"
```

---

## Task 5: 前端 - 新增用户中心页面

**Files:**
- Create: `frontend/src/pages/user/UserCenterPage.vue`

**Step 1: 创建页面组件**

页面结构：
- 左侧菜单（a-menu）：个人资料 / 我的图片 / 会员权益
- 右侧内容区：根据选中菜单显示不同内容

```vue
<template>
  <div id="userCenterPage">
    <div class="user-center-container">
      <!-- 左侧菜单 -->
      <div class="left-sider">
        <a-menu v-model:selectedKeys="currentMenu" :items="menuItems" @click="handleMenuClick" />
      </div>

      <!-- 右侧内容 -->
      <div class="right-content">
        <!-- 个人资料 -->
        <div v-if="currentMenu[0] === 'profile'" class="content-section">
          <h2>个人资料</h2>
          <!-- 头像 -->
          <div class="avatar-section">
            <a-avatar :src="loginUser.userAvatar" :size="80" />
            <a-button @click="showAvatarModal">更换头像</a-button>
          </div>
          <!-- 昵称 -->
          <div class="field-item">
            <span class="label">昵称</span>
            <div v-if="!editingNickname" class="value" @click="startEditNickname">
              {{ loginUser.userName }}
            </div>
            <div v-else class="edit-input">
              <a-input v-model:value="editForm.userName" />
              <a-button @click="cancelEditNickname">取消</a-button>
              <a-button type="primary" @click="saveNickname">保存</a-button>
            </div>
          </div>
          <!-- 简介 -->
          <div class="field-item">
            <span class="label">简介</span>
            <div v-if="!editingProfile" class="value" @click="startEditProfile">
              {{ loginUser.userProfile || '暂无简介' }}
            </div>
            <div v-else class="edit-input">
              <a-input v-model:value="editForm.userProfile" />
              <a-button @click="cancelEditProfile">取消</a-button>
              <a-button type="primary" @click="saveProfile">保存</a-button>
            </div>
          </div>
        </div>

        <!-- 我的图片 -->
        <div v-if="currentMenu[0] === 'pictures'" class="content-section">
          <h2>我的图片</h2>
          <!-- 统计卡片 -->
          <div class="stats-cards">
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.uploadCount }}</div>
              <div class="stat-label">上传数量</div>
            </a-card>
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.likeCount }}</div>
              <div class="stat-label">收藏数量</div>
            </a-card>
            <a-card class="stat-card">
              <div class="stat-value">{{ pictureStats.reviewPassRate }}%</div>
              <div class="stat-label">审核通过率</div>
            </a-card>
          </div>
          <!-- 最近图片 -->
          <div class="recent-pictures">
            <h3>最近上传</h3>
            <div class="picture-grid">
              <img v-for="pic in recentPictures" :key="pic.id"
                   :src="pic.thumbnailUrl || pic.url"
                   @click="goToPictureDetail(pic.id)" />
            </div>
          </div>
        </div>

        <!-- 会员权益 -->
        <div v-if="currentMenu[0] === 'vip'" class="content-section">
          <h2>会员权益</h2>
          <a-card class="vip-card">
            <div class="vip-badge">{{ isVip ? 'VIP' : '普通用户' }}</div>
            <div class="vip-expire">到期时间：{{ vipExpireTime || '永久' }}</div>
          </a-card>
        </div>
      </div>
    </div>
  </div>
</template>
```

**Step 2: Commit**

```bash
git add frontend/src/pages/user/UserCenterPage.vue
git commit -m "feat(user): add UserCenterPage with profile, pictures, vip tabs"
```

---

## Task 6: 前端 - 添加路由

**Files:**
- Modify: `frontend/src/router/index.ts`

**Step 1: 导入并添加路由**

在 import 部分添加：
```typescript
import UserCenterPage from '@/pages/user/UserCenterPage.vue'
```

在 BasicLayout children 中添加：
```typescript
{
  path: 'user/center',
  name: 'UserCenter',
  component: UserCenterPage,
  meta: { requiresAuth: true }
}
```

**Step 2: Commit**

```bash
git add frontend/src/router/index.ts
git commit -m "feat(router): add /user/center route"
```

---

## Task 7: 前端 - GlobalHeader 添加用户中心入口

**Files:**
- Modify: `frontend/src/components/GlobalHeader.vue`

**Step 1: 在 handleUserMenuClick 添加用户中心跳转**

```typescript
const handleUserMenuClick = ({ key }: { key: string }) => {
  if (key === 'my_space') {
    router.push('/my_space')
  } else if (key === 'feedback') {
    router.push('/feedback')
  } else if (key === 'user_center') {
    router.push('/user/center')
  } else if (key === 'admin') {
    router.push('/admin')
  } else if (key === 'logout') {
    doLogout()
  }
}
```

**Step 2: 在下拉菜单添加用户中心选项**

在 `dropdown-menu-content` 的 user-menu 中添加：
```vue
<a-menu-item key="user_center" class="menu-item">
  <div class="menu-item-inner">
    <span class="menu-icon">👤</span>
    <span class="menu-text">用户中心</span>
  </div>
  <div class="menu-item-shine"></div>
</a-menu-item>
```

**Step 3: Commit**

```bash
git add frontend/src/components/GlobalHeader.vue
git commit -m "feat(header): add user center menu item"
```

---

## Task 8: 前端 - 添加用户中心 API 调用

**Files:**
- Modify: `frontend/src/api/userController.ts`

**Step 1: 添加新接口类型和调用方法**

```typescript
export interface UserPictureStatsResponse {
  uploadCount?: number;
  likeCount?: number;
  reviewPassCount?: number;
  reviewPassRate?: number;
}

export const getUserPictureStatsUsingGet = () => {
  return axios.request({
    url: '/user/picture/stats',
    method: 'get'
  });
};

export const getUserRecentPicturesUsingGet = (count: number) => {
  return axios.request({
    url: '/user/picture/recent',
    method: 'get',
    params: { count }
  });
};
```

**Step 2: Commit**

```bash
git add frontend/src/api/userController.ts
git commit -m "feat(api): add user picture stats and recent pictures API"
```

---

## Task 9: 前端 - 完善 UserCenterPage 逻辑

**Files:**
- Modify: `frontend/src/pages/user/UserCenterPage.vue`

**Step 1: 添加 script 部分**

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'
import { getUserPictureStatsUsingGet, getUserRecentPicturesUsingGet } from '@/api/userController'
import { userUpdateUsingPost } from '@/api/userController'
import { UploadOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

const currentMenu = ref(['profile'])
const menuItems = [
  { key: 'profile', label: '个人资料' },
  { key: 'pictures', label: '我的图片' },
  { key: 'vip', label: '会员权益' }
]

// 编辑状态
const editingNickname = ref(false)
const editingProfile = ref(false)
const editForm = ref({
  userName: '',
  userProfile: ''
})

// 图片统计
const pictureStats = ref({
  uploadCount: 0,
  likeCount: 0,
  reviewPassCount: 0,
  reviewPassRate: 0
})

// 最近图片
const recentPictures = ref<any[]>([])

// 会员状态（mock）
const isVip = ref(false)
const vipExpireTime = ref('2026-12-31')

// 加载数据
const loadPictureStats = async () => {
  try {
    const res = await getUserPictureStatsUsingGet()
    if (res.data.code === 0) {
      pictureStats.value = res.data.data
    }
  } catch (e) {
    console.error('加载图片统计失败', e)
  }
}

const loadRecentPictures = async () => {
  try {
    const res = await getUserRecentPicturesUsingGet(6)
    if (res.data.code === 0) {
      recentPictures.value = res.data.data
    }
  } catch (e) {
    console.error('加载最近图片失败', e)
  }
}

// 菜单切换
const handleMenuClick = ({ key }: { key: string }) => {
  if (key === 'pictures') {
    loadPictureStats()
    loadRecentPictures()
  }
}

// 编辑昵称
const startEditNickname = () => {
  editForm.value.userName = loginUser.userName
  editingNickname.value = true
}
const cancelEditNickname = () => {
  editingNickname.value = false
}
const saveNickname = async () => {
  try {
    const res = await userUpdateUsingPost({ id: loginUser.id, userName: editForm.value.userName })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, userName: editForm.value.userName })
      message.success('昵称修改成功')
      editingNickname.value = false
    }
  } catch (e) {
    message.error('修改失败')
  }
}

// 编辑简介
const startEditProfile = () => {
  editForm.value.userProfile = loginUser.userProfile
  editingProfile.value = true
}
const cancelEditProfile = () => {
  editingProfile.value = false
}
const saveProfile = async () => {
  try {
    const res = await userUpdateUsingPost({ id: loginUser.id, userProfile: editForm.value.userProfile })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, userProfile: editForm.value.userProfile })
      message.success('简介修改成功')
      editingProfile.value = false
    }
  } catch (e) {
    message.error('修改失败')
  }
}

// 跳转图片详情
const goToPictureDetail = (id: number) => {
  router.push(`/picture/${id}`)
}

// 头像上传（待实现）
const showAvatarModal = () => {
  message.info('头像上传功能待实现')
}
</script>
```

**Step 2: Commit**

```bash
git add frontend/src/pages/user/UserCenterPage.vue
git commit -m "feat(user): implement UserCenterPage full logic"
```

---

## Task 10: 集成测试

**Step 1: 启动后端**

```bash
mvn spring-boot:run
```

**Step 2: 启动前端**

```bash
cd frontend && npm run dev
```

**Step 3: 手动测试流程**

1. 登录账号
2. 点击头像下拉菜单 → "用户中心"
3. 验证三个 Tab（个人资料/我的图片/会员权益）切换正常
4. 测试编辑昵称和简介
5. 验证图片统计数据正确显示

---

## Summary

| Task | File | Action |
|------|------|--------|
| 1 | `UserPictureStatsResponse.java` | Create |
| 2 | `UserController.java` | Modify |
| 3 | `UserService.java` + Impl | Modify |
| 4 | `PictureService.java` + Impl | Modify |
| 5 | `UserCenterPage.vue` | Create |
| 6 | `router/index.ts` | Modify |
| 7 | `GlobalHeader.vue` | Modify |
| 8 | `userController.ts` | Modify |
| 9 | `UserCenterPage.vue` | Modify (script) |
| 10 | - | Test |
