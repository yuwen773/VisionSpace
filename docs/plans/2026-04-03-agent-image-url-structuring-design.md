# Agent 图片 URL 结构化设计

## 背景

当前实现中，前端将图片 URL 以明文拼接在消息文本中（`用户上传了图片：\n{url}`），存在以下问题：
- 消息内容不结构化，LLM 难以区分普通文本和图片 URL
- 前端消息渲染依赖独立的 `images` prop，图片与文字分离处理
- 格式不语义化，扩展性差

## 设计目标

1. 用结构化标签替代明文 URL，提升可读性和可解析性
2. 后端掌握消息格式控制权，前端只传数据
3. 前端消息渲染统一解析标签，无需独立 prop

## 消息格式

**标签名：** `<image-analysis>`

**结构：** 每个 URL 一个标签

**示例：**
```
<image-analysis>https://yuwen-1325795131.cos.ap-guangzhou.myqcloud.com/agent-chat/2026-04-03/abc.png</image-analysis>

用户输入的文字内容
```

## 前后端交互

### 发送消息

**前端 → 后端：**
```json
{
  "message": "用户文字",
  "imageUrls": ["url1", "url2"],
  "threadId": "xxx"
}
```

**后端处理：**
- 将 `imageUrls` 拼接为 `<image-analysis>{url}</image-analysis>\n` 格式
- 拼接到消息文本开头
- 存入消息表

### 消息存储（入库内容）

```
<image-analysis>url1</image-analysis>
<image-analysis>url2</image-analysis>

用户文字
```

### 流式响应

后端在 SSE 流式输出中，`messageType=assistant` 时携带的 `content` 字段即为存入的消息内容，原样返回。

## 前端渲染

### UserMessage 组件改动

- `content` prop 中解析 `<image-analysis>` 标签
- 提取 URL 列表，渲染 64×64 小图缩略图
- 点击小图弹出 lightbox 预览（复用 `AgentChatInput` 的 `lightboxUrl` 机制）
- 超过 4 张显示 `+N`（与当前 `UserMessage` 行为一致）

### 不再需要的 prop

- `images: string[]` prop 可以移除（或保留向后兼容，历史消息无图片时兜底）

## 改动范围

### 后端
- `AgentChatRequest` — 新增 `imageUrls` 字段
- `ChatHistoryService` — 保存消息时拼接标签
- 历史消息返回内容格式不变（已原样存储带标签内容）

### 前端
- `useAgentStream.ts` — `sendMessage` 改为传 `{ message, imageUrls, threadId }`，不再拼接文本
- `AgentMessageList.vue` — 传入 `content` 即可，移除独立 `images` prop
- `UserMessage.vue` — 内部解析 `<image-analysis>` 标签渲染图片
- `AgentChatPage.vue` — `pushMessage` 不再传 `images` 字段
