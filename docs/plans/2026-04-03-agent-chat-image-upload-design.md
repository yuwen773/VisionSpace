# Agent 聊天图片上传功能设计

## 1. 背景与目标

当前 Agent 对话已支持图片上传功能，但存在以下问题：
- 前端 UI 已完整实现（文件选择、粘贴、预览）
- 后端 Agent 接口只接收纯文本消息，无法处理图片
- 需要补齐后端上传/删除能力，使 Agent 能理解用户上传的图片

核心原则：对话图片不入库 `picture` 表，只作为对话上下文素材。

## 2. 设计方案

### 2.1 上传接口

- **路径**：`POST /agent/image/upload`
- **参数**：`@RequestPart("file") MultipartFile file`
- **存储**：使用现有 `FileStorageService`，路径前缀 `agent-chat/YYYY-MM-DD/{uuid}.{suffix}`
- **限制**：校验文件后缀（jpeg/png/jpg/webp）、大小限制（2MB）
- **不入库**：`picture` 表，保持数据隔离
- **返回**：`{ url: "https://..." }`

### 2.2 删除接口

- **路径**：`POST /agent/image/upload/delete`
- **参数**：JSON body `{ url: "https://..." }`
- **实现**：使用现有 `FileStorageService.delete()` 方法，根据 URL 或 path 删除文件
- **用途**：用户取消发送时，前端回调清理已上传文件

### 2.3 前端交互流程

```
用户选择图片
  → POST /agent/image/upload → { url }（pending 状态）
    ↓
用户发送消息 → 把 url 拼进消息文本 → POST /agent/image/chat/stream
用户移除图片 → POST /agent/image/upload/delete { url } → 删除已上传文件
```

消息文本格式：
```
用户上传了图片：https://xxx.jpg
用户描述：想要找类似风格的图
```

### 2.4 Agent 侧

Agent 收到的消息中包含图片 URL，Agent 自动调用 `analyzeImageFeatures` 工具分析图片特征，`ImageAgent.stream()` 实现无需修改。

## 3. 实现任务

### 后端

1. 新建 `AgentImageUploadController`
   - `POST /agent/image/upload` — 文件上传
   - `POST /agent/image/upload/delete` — 文件删除

2. 新建 `AgentImageUploadService`
   - `uploadImage(MultipartFile)` — 上传到 `agent-chat/` 前缀，返回 URL
   - `deleteImage(String url)` — 根据 URL 删除文件

### 前端

1. `useAgentStream.ts` — 发送消息前先上传所有 pending 图片，拿到 URL 后拼进消息文本
2. `AgentChatInput.vue` — 移除图片时回调删除接口
3. 取消发送时批量清理所有 pending 图片

## 4. 存储路径

统一使用 `agent-chat/` 前缀，便于未来定期清理过期对话图片：

```
agent-chat/
  ├── 2026-04-03/
  │   ├── abc123.png
  │   └── def456.jpg
  └── 2026-04-04/
      └── ...
```

## 5. 错误处理

- 上传失败：前端提示错误，不发送消息
- 删除失败：记录日志，不阻塞前端流程（文件可由定时任务兜底清理）
- Agent 处理图片时图片不可达：返回分析失败提示
