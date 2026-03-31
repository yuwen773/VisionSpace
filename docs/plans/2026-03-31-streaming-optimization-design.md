# 流式消息优化设计方案

> 日期：2026-03-31
> 分支：feature/agent-dev
> 状态：已审批

---

## 1. 目标

对齐 spring-ai-alibaba 参考项目（deepresearch + spring-ai-alibaba-studio）的 SSE 最佳实践，实现规范的前后端流式接口。

## 2. 问题分析

### 后端问题
- 使用 GET 方法（参考项目用 POST）
- 响应结构缺少 `chunk`/`message` 分离
- 没有心跳机制
- 没有过滤重复事件

### 前端问题
- 每条 SSE data 当作独立消息 push，没有累积 chunk
- `abortController` 未实际使用
- 缺少逐字打字动画

## 3. 技术方案

### 3.1 后端改动

**接口改为 POST**

```java
@PostMapping(value = "/image/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestBody AgentChatRequest request)
```

**统一响应结构 AgentRunResponse**

```java
public class AgentRunResponse {
    private String node;              // 节点名 (agent/heartbeat)
    private String agent;            // Agent 名
    private String chunk;            // 流式文本片段
    private MessageDTO message;      // 完整消息
    private Usage tokenUsage;        // Token 统计
    private Boolean error;           // 错误标志
    private String errorType;       // 错误类型
    private String errorMessage;     // 错误信息
}
```

**消息类型枚举**

```java
public enum MessageType {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL_REQUEST("tool-request"),    // 工具调用请求
    TOOL_CONFIRM("tool-confirm"),    // 需要用户确认
    TOOL_RESPONSE("tool");           // 工具响应
}
```

**过滤逻辑**
- 过滤 `AGENT_MODEL_FINISHED` 避免重复
- 添加心跳节点 `node = "heartbeat"`（每 30 秒）

**错误处理**

```java
.onErrorResume(error -> {
    return Flux.just(
        ServerSentEvent.<String>builder()
            .event("error")
            .data(String.format(
                "{\"error\":true,\"errorType\":\"%s\",\"errorMessage\":\"%s\"}",
                error.getClass().getSimpleName(),
                error.getMessage()
            ))
            .build()
    );
})
```

### 3.2 前端改动

**类型定义更新**

```typescript
export interface AgentRunResponse {
  node: string;
  agent?: string;
  chunk?: string;
  message?: MessageDTO;
  tokenUsage?: Usage;
  error?: boolean;
  errorType?: string;
  errorMessage?: string;
}

export interface MessageDTO {
  messageType: 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool';
  content?: string;
  toolCalls?: ToolCall[];
  toolName?: string;
}
```

**chunk 累积逻辑**

```typescript
let accumulatedContent = ''
let isFirstChunk = true

for await (const chunk of stream) {
  if (chunk.node === 'heartbeat') continue

  if (chunk.error) {
    messages.value.push({ type: 'error', content: chunk.errorMessage })
    continue
  }

  if (chunk.chunk) {
    if (isFirstChunk) {
      messages.value.push({ type: 'assistant', content: chunk.chunk })
      isFirstChunk = false
    } else {
      accumulatedContent += chunk.chunk
      const lastMsg = messages.value[messages.value.length - 1]
      lastMsg.content = accumulatedContent
    }
  }

  if (chunk.message) {
    messages.value.push({ type: chunk.message.messageType, ... })
    isFirstChunk = true
    accumulatedContent = ''
  }
}
```

**AbortController 实际使用**

```typescript
const abortController = new AbortController()

const response = await fetch(url, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(request),
  signal: abortController.signal,  // 实际传入
})

const stop = () => {
  abortController.abort()
}
```

**打字动画（CSS 光标 + 逐字显示）**
- 保持现有光标闪烁效果
- chunk 累积时不立即渲染，等完整单词/句子

## 4. 数据流

```
┌─────────────┐                      ┌─────────────┐
│   前端      │                      │   后端      │
├─────────────┤                      ├─────────────┤
│ POST /stream                      │              │
│ + JSON body                      │              │
│ <─────────────── SSE stream ──────────────────>
│               { node: "heartbeat" }  ← 每30秒
│               { chunk: "Hello" }
│               { chunk: " world" }
│               { message: { tool-request } }
│               { chunk: "Finished" }
│               { error: false }  ← 结束信号
└─────────────┘                      └─────────────┘
```

## 5. 文件改动清单

| 文件 | 改动 |
|------|------|
| `AgentController.java` | POST + 统一响应结构 + 心跳 + 错误处理 |
| `AgentRunResponse.java` | 新增响应 DTO 类 |
| `MessageType.java` | 新增消息类型枚举 |
| `useAgentStream.ts` | chunk 累积 + 类型更新 + abortController |
| `AgentChatPage.vue` | 适配新消息结构 |
| `AgentChatInput.vue` | stop 按钮调用 abort |

## 6. 参考

- `docs/plans/streaming-implementation-reference.md` - 流式实现参考文档
