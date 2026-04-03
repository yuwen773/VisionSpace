# Agent 聊天图片上传功能实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 前端上传的图片能正确传递给 Agent，Agent 自动调用 `analyzeImageFeatures` 分析图片内容

**Architecture:**
- 后端新增 `AgentImageUploadController` + `AgentImageUploadService`，提供上传/删除接口
- 图片使用 `FileStorageService.putObject()` 上传至 `agent-chat/` 前缀，不入库 `picture` 表
- 前端发送消息前先上传图片拿 URL，拼接进消息文本发给 Agent
- 前端移除图片时调用删除接口清理已上传文件

**Tech Stack:** Spring Boot, x-file-storage, Vue 3 + TypeScript, Ant Design Vue

---

## Task 1: 创建 AgentImageUploadService

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/service/AgentImageUploadService.java`

**Step 1: 写 Service 框架**

```java
@Service
public class AgentImageUploadService {

    @Resource
    private PictureStorageService pictureStorageService;

    private static final String UPLOAD_PATH_PREFIX = "agent-chat";

    public String uploadImage(MultipartFile file) {
        // 1. 校验后缀、大小
        // 2. 生成文件名 agent-chat/YYYY-MM-DD/{uuid}.{suffix}
        // 3. 用 pictureStorageService.putObject() 上传，不做缩略图压缩
        // 4. 返回 url
    }

    public void deleteImage(String imageUrl) {
        // 1. 从 URL 中提取存储路径 key（agent-chat/xxx）
        // 2. 调用 pictureStorageService.deleteObject(key)
    }
}
```

**Step 2: 写完整实现**

文件后缀允许：jpeg, png, jpg, webp
大小限制：2MB

**Step 3: 提交**
```bash
git add src/main/java/com/yuwen/visionspace/agent/service/AgentImageUploadService.java
git commit -m "feat(agent): 添加 Agent 图片上传服务"
```

---

## Task 2: 创建 AgentImageUploadController

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/agent/controller/AgentImageUploadController.java`

**Step 1: 写 Controller**

```java
@RestController
@RequestMapping("/agent/image")
public class AgentImageUploadController {

    @Resource
    private AgentImageUploadService agentImageUploadService;

    @PostMapping("/upload")
    public BaseResponse<String> upload(@RequestPart("file") MultipartFile file) {
        String url = agentImageUploadService.uploadImage(file);
        return ResultUtils.success(url);
    }

    @PostMapping("/upload/delete")
    public BaseResponse<Boolean> delete(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        agentImageUploadService.deleteImage(url);
        return ResultUtils.success(true);
    }
}
```

**Step 2: 提交**
```bash
git add src/main/java/com/yuwen/visionspace/agent/controller/AgentImageUploadController.java
git commit -m "feat(agent): 添加 Agent 图片上传/删除接口"
```

---

## Task 3: 前端 — useAgentStream 添加上传逻辑

**Files:**
- Modify: `frontend/src/composables/useAgentStream.ts`

**Step 1: 添加上传方法**

在 `useAgentStream` composable 中新增：
```typescript
// 上传单张图片，返回 URL
async function uploadImage(file: File): Promise<string>

// 发送消息：先上传所有图片，再发消息
async function sendMessage(text: string, threadId: string, files?: File[]): Promise<void>
```

发送消息时逻辑：
1. 如果有 files，先全部上传（并发），拿到 URL 列表
2. 把 URL 拼接进消息文本：`用户上传了图片：\n${urls.join('\n')}\n\n${text}`
3. 发给 `/api/agent/image/chat/stream`

**Step 2: 提交**
```bash
git add frontend/src/composables/useAgentStream.ts
git commit -m "feat(frontend): 发送消息前先上传图片"
```

---

## Task 4: 前端 — 移除图片时删除已上传文件

**Files:**
- Modify: `frontend/src/components/agent/AgentChatInput.vue`

**Step 1: 处理附件移除**

在 `AgentChatInput.vue` 中，当用户移除一个图片附件时：
- 如果该图片已经上传（拿到了 URL），调用 `POST /api/agent/image/upload/delete { url }` 删除
- 从 pending 图片列表中移除

关键：区分"已上传但未发送"和"从未上传"两种状态

**Step 2: 提交**
```bash
git add frontend/src/components/agent/AgentChatInput.vue
git commit -m "feat(frontend): 移除图片时回调删除接口"
```

---

## Task 5: 前端 — 发送失败时清理已上传图片

**Files:**
- Modify: `frontend/src/composables/useAgentStream.ts`

**Step 1: 错误处理**

如果 `/api/agent/image/chat/stream` 调用失败，把本次 pending 的图片全部删除（调用删除接口），避免留下无用文件。

**Step 2: 提交**
```bash
git add frontend/src/composables/useAgentStream.ts
git commit -m "fix(frontend): 发送失败时清理已上传图片"
```

---

## Task 6: 编译验证

**Step 1: 后端编译**

```bash
mvn compile -pl .
```
预期：BUILD SUCCESS

**Step 2: 前端编译**

```bash
cd frontend && npm run build
```
预期：无 error

---

## Task 7: 接口测试

用 curl 手动测试：

**上传**
```bash
curl -X POST http://localhost:8081/agent/image/upload \
  -H "Authorization: Bearer ${token}" \
  -F "file=@/path/to/test.jpg"
```
预期：返回 `{ url: "https://..." }`

**删除**
```bash
curl -X POST http://localhost:8081/agent/image/upload/delete \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://..."}'
```
预期：返回 `{ code: 0 }`

---

## 注意事项

1. 删除接口的 key 从 URL 中提取，用 `String.substringAfter` + `String.substringBefore` 或正则去掉域名部分，保留 `agent-chat/...` 路径
2. 上传使用 `putObject`（原始文件），不要用 `putPictureObject`（会做 WebP 压缩和缩略图），因为对话图片不需要压缩
3. 前端上传时用 `FormData` 而非 JSON，和后端 `@RequestPart("file") MultipartFile` 对应
4. 前端 API 路径前缀 `/api`，实际请求 `/api/agent/image/upload` → 后端 `/agent/image/upload`
