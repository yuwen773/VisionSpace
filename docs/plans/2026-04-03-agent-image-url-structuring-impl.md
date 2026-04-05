# Agent 图片 URL 结构化实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 前端传 `{ message, imageUrls }` 分开字段，后端拼接 `<image-analysis>` 标签；前端 UserMessage 解析标签渲染 64×64 小图。

**Architecture:**
- 前端 `useAgentStream` 改为传 `imageUrls` 数组，不拼接文本
- 后端 `AgentChatRequest` 新增 `imageUrls`，`ImageAgent` 拼接标签后存入消息表
- `UserMessage.vue` 内部解析 `<image-analysis>` 标签渲染图片，与文字统一处理

**Tech Stack:** Spring Boot, Vue 3 + TypeScript, Ant Design Vue

---

## Task 1: 后端 — AgentChatRequest 新增 imageUrls 字段

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/model/dto/agent/AgentChatRequest.java`

**Step 1: 添加 imageUrls 字段**

```java
/**
 * 用户上传的图片 URL 列表（后端拼接为 <image-analysis> 标签）
 */
private List<String> imageUrls;
```

添加 getter/setter（或使用 Lombok 的 `@Data` 自动生成）。

**Step 2: 验证编译**

```bash
mvn compile -pl . -q
```
预期：BUILD SUCCESS

**Step 3: 提交**
```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/AgentChatRequest.java
git commit -m "feat(agent): AgentChatRequest 新增 imageUrls 字段"
```

---

## Task 2: 后端 — ImageAgent 拼接标签

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/agent/ImageAgent.java`

**Step 1: 修改 stream 方法签名和标签拼接逻辑**

在 `stream(String userMessage, String threadId, Long userId)` 方法中：

1. 新增参数 `List<String> imageUrls`
2. 如果 `imageUrls` 不为空，按以下格式拼接：
   ```
   <image-analysis>{url1}</image-analysis>
   <image-analysis>{url2}</image-analysis>

   {userMessage}
   ```
3. 所有带标签的 URL 拼在原始消息**之前**

**Step 2: 修改 run 方法（chat 接口）**

同上，新增 `imageUrls` 参数并拼接标签。

**Step 3: 验证编译**

```bash
mvn compile -pl . -q
```
预期：BUILD SUCCESS

**Step 4: 提交**
```bash
git add src/main/java/com/yuwen/visionspace/agent/ImageAgent.java
git commit -m "feat(agent): ImageAgent 拼接 <image-analysis> 标签"
```

---

## Task 3: 后端 — AgentController 传递 imageUrls

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/AgentController.java`

**Step 1: 修改 chat 和 chatStream 方法**

- `chat(AgentChatRequest)` → 传 `request.getImageUrls()` 给 `imageAgent.run()`
- `chatStream(AgentChatRequest, HttpServletRequest)` → 传 `request.getImageUrls()` 给 `imageAgent.stream()`

**Step 2: 验证编译**

```bash
mvn compile -pl . -q
```
预期：BUILD SUCCESS

**Step 3: 提交**
```bash
git add src/main/java/com/yuwen/visionspace/controller/AgentController.java
git commit -m "feat(agent): AgentController 传递 imageUrls 给 Agent"
```

---

## Task 4: 前端 — useAgentStream 改传分开字段

**Files:**
- Modify: `frontend/src/composables/useAgentStream.ts`

**Step 1: 修改 sendMessage**

当前逻辑：
```typescript
uploadedUrls = await Promise.all(files.map(f => uploadAgentImage(f)))
const imageUrlsText = uploadedUrls.map(url => `用户上传了图片：${url}`).join('\n')
fullMessage = `${imageUrlsText}\n\n${message}`
body = JSON.stringify({ message: fullMessage, threadId })
```

改为：
```typescript
uploadedUrls = await Promise.all(files.map(f => uploadAgentImage(f)))
body = JSON.stringify({
  message,
  imageUrls: uploadedUrls,  // 直接传数组，不拼接文本
  threadId
})
```

**Step 2: 验证类型**

确保 `AgentChatRequest` 类型定义（前端 ts）包含 `imageUrls: string[]` 字段。

**Step 3: 提交**
```bash
git add frontend/src/composables/useAgentStream.ts
git commit -m "feat(frontend): useAgentStream 传 imageUrls 数组而非拼接文本"
```

---

## Task 5: 前端 — UserMessage 解析标签渲染图片

**Files:**
- Modify: `frontend/src/components/agent/message/UserMessage.vue`

**Step 1: 添加解析逻辑**

在 `<script setup>` 中新增：

```typescript
const IMAGE_TAG_REGEX = /<image-analysis>([^<]+)<\/image-analysis>/g

interface ParsedImage {
  url: string
}

const parsedImages = computed<ParsedImage[]>(() => {
  if (!props.content) return []
  const urls: ParsedImage[] = []
  const regex = new RegExp(IMAGE_TAG_REGEX)
  let match
  while ((match = regex.exec(props.content)) !== null) {
    urls.push({ url: match[1].trim() })
  }
  return urls
})

// 渲染用的图片列表（最多4张）
const displayImages = computed(() => parsedImages.value.slice(0, 4))
const extraCount = computed(() => Math.max(0, parsedImages.value.length - 4))
```

**Step 2: 修改模板**

将 `images` prop 的使用改为 `displayImages`：

```html
<!-- 图片网格 -->
<div v-if="displayImages.length > 0" class="message-images" :class="`grid-${Math.min(displayImages.length, 4)}`">
  <div v-for="(img, i) in displayImages" :key="i" class="img-thumb" @click="emit('preview', img.url)">
    <img :src="img.url" alt="附件图片" />
  </div>
  <div v-if="extraCount > 0" class="img-more">+{{ extraCount }}</div>
</div>
```

**Step 3: 暴露 preview 事件**

```typescript
const emit = defineEmits<{
  (e: 'preview', url: string): void
}>()
```

**Step 4: 移除 images prop（或保留兼容）**

`images` prop 不再主动传入，可以移除或保留作为兜底（历史消息无标签解析时）。

**Step 5: 提交**
```bash
git add frontend/src/components/agent/message/UserMessage.vue
git commit -m "feat(frontend): UserMessage 解析 <image-analysis> 标签渲染图片"
```

---

## Task 6: 前端 — AgentMessageList 传 content 而非 images

**Files:**
- Modify: `frontend/src/components/agent/AgentMessageList.vue`

**Step 1: 修改 UserMessage 调用**

当前：
```html
<UserMessage :content="msg.content" :time="msg.time" :images="msg.images" />
```

改为：
```html
<UserMessage :content="msg.content" :time="msg.time" @preview="onImagePreview" />
```

添加 preview 处理：
```typescript
const onImagePreview = (url: string) => {
  // 复用 AgentChatInput 的 lightbox 逻辑
  // 可以通过 emit 到父组件，或用 provide/inject
}
```

**Step 2: 提交**
```bash
git add frontend/src/components/agent/AgentMessageList.vue
git commit -m "feat(frontend): AgentMessageList 传 content 给 UserMessage 解析图片"
```

---

## Task 7: 前端 — AgentChatPage 清理 pushMessage

**Files:**
- Modify: `frontend/src/pages/AgentChatPage.vue`

**Step 1: 移除 pushMessage 中的 images 参数**

当前 `handleSend` 中：
```typescript
pushMessage({
  type: 'user',
  content: text,
  time: new Date().toLocaleTimeString(...),
  images: imagePreviews.length > 0 ? imagePreviews : undefined,
})
```

改为：
```typescript
pushMessage({
  type: 'user',
  content: text,
  time: new Date().toLocaleTimeString(...),
})
```

**Step 2: 提交**
```bash
git add frontend/src/pages/AgentChatPage.vue
git commit -m "feat(frontend): AgentChatPage pushMessage 不再传 images 字段"
```

---

## Task 8: 编译验证

**Step 1: 后端编译**

```bash
cd "D:\Work\code\VisionSpace\.worktrees\feature-agent-dev" && mvn compile -pl . -q
```
预期：BUILD SUCCESS

**Step 2: 前端编译**

```bash
cd "D:\Work\code\VisionSpace\.worktrees\feature-agent-dev\frontend" && npm run build 2>&1 | tail -10
```
预期：无 error

---

## Task 9: 接口测试

手动测试：

**上传图片 + 发送消息**
```bash
# 1. 上传图片
curl -X POST http://localhost:8081/api/agent/image/upload \
  -H "Authorization: Bearer ${token}" \
  -F "file=@/path/to/test.jpg"
# 预期：返回 { code: 0, data: "https://.../agent-chat/2026-04-03/xxx.png" }

# 2. 发送消息（带 imageUrls）
curl -X POST http://localhost:8081/api/agent/image/chat/stream \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"message": "分析这张图", "imageUrls": ["https://.../agent-chat/2026-04-03/xxx.png"], "threadId": "test-123"}'
# 预期：SSE 流式返回，用户消息 content 中包含 <image-analysis>...</image-analysis> 标签
```

**查看历史消息**
```bash
curl "http://localhost:8081/api/agent/history/test-123" \
  -H "Authorization: Bearer ${token}"
# 预期：消息 content 中包含 <image-analysis> 标签
```

---

## 注意事项

1. `ChatHistoryService.saveUserMessage` 本身不需要改动，它只负责存储 content，拼接逻辑在 ImageAgent 层完成
2. 历史消息已存储带标签内容，查询时原样返回，前端解析逻辑可复用
3. 图片预览 lightbox 机制：UserMessage 的 preview 事件需要向上传递，最终在 AgentChatLayout 或 AgentChatInput 的 lightbox 机制中处理
