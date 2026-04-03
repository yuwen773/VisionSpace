# 用户反馈功能设计方案

## 一、功能概述

在现有图片分享平台中增加用户反馈系统，支持三种反馈类型（产品建议/内容举报/工单支持），用户通过头像菜单提交带截图的反馈，管理员在独立后台统一处理。

## 二、数据库设计

### 反馈表 `user_feedback`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| userId | Long | 反馈用户ID（必须登录） |
| type | Integer | 1=产品建议 2=内容举报 3=工单支持 |
| title | String | 反馈标题（最多50字） |
| content | String | 反馈内容（最多2000字） |
| pictureUrls | String | 附件图片URLs（JSON数组，最多5张） |
| status | Integer | 0=待处理 1=处理中 2=已解决 |
| handlerId | Long | 处理人管理员ID |
| handlerNote | String | 管理员处理备注 |
| handleTime | Date | 处理时间 |
| createTime | Date | 提交时间 |
| updateTime | Date | 更新时间 |
| isDelete | Integer | 逻辑删除 |

## 三、后端接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | /feedback/add | 用户提交反馈 | 登录用户 |
| GET | /feedback/get/{id} | 查看单条反馈详情 | 登录用户（只能看自己的，管理员可看所有） |
| POST | /feedback/list/page | 分页查询反馈列表（支持按type/status筛选） | 管理员 |
| POST | /feedback/update/status | 管理员更新反馈状态和处理备注 | 管理员 |

### DTO 设计

**FeedbackAddRequest**
- type: Integer（必填，1/2/3）
- title: String（必填，最多50字）
- content: String（必填，最多2000字）
- pictureUrls: String[]（可选，最多5张图片URL）

**FeedbackUpdateStatusRequest**
- id: Long（必填）
- status: Integer（必填，0/1/2）
- handlerNote: String（可选，处理备注）

**FeedbackQueryRequest**
- type: Integer（可选，筛选类型）
- status: Integer（可选，筛选状态）
- current: Long
- pageSize: Long

### VO 设计

**FeedbackVO**
- 包含 entity 所有字段 + userVO（提交人信息）+ handlerVO（处理人信息）

## 四、前端页面

### 4.1 反馈提交页 `src/pages/FeedbackPage.vue`

**入口**：用户头像下拉菜单 → "意见反馈"

**页面风格**：深色沉浸式 + 毛玻璃效果，与 HomePage 风格一致

**表单字段**：
- 反馈类型：单选卡片（产品建议 / 内容举报 / 工单支持）
- 标题：文本输入框
- 描述：多行文本域
- 附件图片：上传组件，支持1-5张图片

**交互**：
- 提交后显示成功提示
- 可选：返回上一页

### 4.2 反馈管理页 `src/pages/admin/FeedbackManagePage.vue`

**页面风格**：简洁卡片式布局，与现有 admin 页面风格一致

**布局**：
- 左侧筛选项：全部类型 / 待处理 / 处理中 / 已解决
- 右侧列表：卡片式展示反馈摘要

**列表字段**：提交人头像+昵称、类型标签、标题、状态标签、提交时间

**详情展开**：
- 完整内容 + 附件图片
- 处理状态更新下拉框（待处理→处理中→已解决）
- 处理备注输入框

## 五、配置影响

- 图片附件存储：复用现有存储服务（StorageConfig），不新建存储配置
- API 路径：`/api/feedback/*`（context-path 已在 application.yml 配置）

## 六、验收标准

1. 用户可在下拉菜单找到反馈入口并成功提交反馈
2. 反馈提交后可在管理后台看到列表
3. 管理员可更新状态和填写处理备注
4. 截图上传正常（1-5张）
5. 用户端页面风格与现有页面一致
6. 管理端页面风格与现有 admin 页面一致
