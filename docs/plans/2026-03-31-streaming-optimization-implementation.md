# 流式消息优化实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 对齐参考项目 SSE 最佳实践，实现规范的前后端流式接口

**Architecture:** 后端新增 AgentRunResponse DTO 和 MessageType 枚举，改造 AgentController 流式接口；前端改造 useAgentStream 支持 chunk 累积和 abortController

**Tech Stack:** Java 8 + Spring Boot 2.7 + Vue 3 + TypeScript

---

## 任务清单

| # | 任务 | 文件 |
|---|------|------|
| 1 | 创建 AgentRunResponse DTO | `src/main/java/com/yuwen/visionspace/model/dto/agent/AgentRunResponse.java` |
| 2 | 创建 MessageType 枚举 | `src/main/java/com/yuwen/visionspace/model/dto/agent/MessageType.java` |
| 3 | 创建 MessageDTO | `src/main/java/com/yuwen/visionspace/model/dto/agent/MessageDTO.java` |
| 4 | 改造 AgentController 流式接口 | `src/main/java/com/yuwen/visionspace/controller/AgentController.java` |
| 5 | 验证后端构建 | `mvn compile` |
| 6 | 改造 useAgentStream.ts 类型和逻辑 | `frontend/src/composables/useAgentStream.ts` |
| 7 | 改造 AgentChatPage.vue 适配新结构 | `frontend/src/pages/AgentChatPage.vue` |
| 8 | 改造 AgentChatInput.vue stop 按钮 | `frontend/src/components/agent/AgentChatInput.vue` |
| 9 | 验证前端构建 | `npx vite build` |
| 10 | 提交所有更改 | git commit + push |

---

## Task 1: 创建 AgentRunResponse DTO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/dto/agent/AgentRunResponse.java`

**Step 1: Create the file**

```java
package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 流式响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点名称 (agent/heartbeat)
     */
    private String node;

    /**
     * Agent 名称
     */
    private String agent;

    /**
     * 流式文本片段
     */
    private String chunk;

    /**
     * 完整消息
     */
    private MessageDTO message;

    /**
     * Token 使用统计
     */
    private Object tokenUsage;

    /**
     * 错误标志
     */
    private Boolean error;

    /**
     * 错误类型
     */
    private String errorType;

    /**
     * 错误信息
     */
    private String errorMessage;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/AgentRunResponse.java
git commit -m "feat(agent): 添加 AgentRunResponse DTO"
```

---

## Task 2: 创建 MessageType 枚举

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/dto/agent/MessageType.java`

**Step 1: Create the file**

```java
package com.yuwen.visionspace.model.dto.agent;

/**
 * 消息类型枚举
 */
public enum MessageType {
    /**
     * 用户消息
     */
    USER("user"),

    /**
     * AI 助手消息
     */
    ASSISTANT("assistant"),

    /**
     * 工具调用请求
     */
    TOOL_REQUEST("tool-request"),

    /**
     * 需要用户确认
     */
    TOOL_CONFIRM("tool-confirm"),

    /**
     * 工具响应
     */
    TOOL_RESPONSE("tool");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/MessageType.java
git commit -m "feat(agent): 添加 MessageType 枚举"
```

---

## Task 3: 创建 MessageDTO

**Files:**
- Create: `src/main/java/com/yuwen/visionspace/model/dto/agent/MessageDTO.java`

**Step 1: Create the file**

```java
package com.yuwen.visionspace.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 消息 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     */
    private MessageType messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 工具调用列表
     */
    private List<Map<String, Object>> toolCalls;

    /**
     * 工具名称
     */
    private String toolName;
}
```

**Step 2: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/model/dto/agent/MessageDTO.java
git commit -m "feat(agent): 添加 MessageDTO"
```

---

## Task 4: 改造 AgentController 流式接口

**Files:**
- Modify: `src/main/java/com/yuwen/visionspace/controller/AgentController.java`

**Step 1: Read current file and prepare changes**

需要改动的内容：
1. 接口从 GET 改为 POST
2. 使用 AgentChatRequest 作为 body
3. 返回结构改为 AgentRunResponse 格式
4. 添加心跳机制（每 30 秒）
5. 添加统一错误处理

**新的 chatStream 方法：**

```java
/**
 * 流式对话接口
 */
@PostMapping(value = "/image/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatStream(@RequestBody AgentChatRequest request) {
    String threadId = request.getThreadId();
    if (threadId == null || threadId.isBlank()) {
        threadId = UUID.randomUUID().toString();
    }

    log.info("Agent 流式对话请求, threadId={}, message={}", threadId, request.getMessage());

    RunnableConfig config = RunnableConfig.builder()
            .threadId(threadId)
            .build();

    Flux<NodeOutput> stream = imageAgent.stream(request.getMessage(), threadId);

    // 心跳 Flux：每 30 秒发送一次
    Flux<ServerSentEvent<String>> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
            .map(tick -> ServerSentEvent.<String>builder()
                    .data(String.format("{\"node\":\"heartbeat\"}"))
                    .build());

    // 主要数据流
    Flux<ServerSentEvent<String>> dataFlux = stream
            // 过滤 AGENT_MODEL_FINISHED 避免重复
            .filter(output -> !(output instanceof StreamingOutput<?> so
                    && so.getOutputType() != null
                    && so.getOutputType().name().equals("AGENT_MODEL_FINISHED")))
            .map(output -> {
                AgentRunResponse response = new AgentRunResponse();
                response.setNode(output.node() != null ? output.node() : "agent");
                response.setAgent("image-agent");

                if (output instanceof StreamingOutput<?> streamingOutput) {
                    Object msg = streamingOutput.message();
                    if (msg instanceof AssistantMessage assistantMessage) {
                        if (assistantMessage.hasToolCalls()) {
                            // 工具调用 → message
                            MessageDTO messageDTO = new MessageDTO();
                            messageDTO.setMessageType(MessageType.TOOL_REQUEST);
                            messageDTO.setContent(assistantMessage.getText());
                            response.setMessage(messageDTO);
                        } else {
                            // 文本 → chunk
                            response.setChunk(assistantMessage.getText());
                        }
                    }
                } else if (output instanceof InterruptionMetadata interruptionMetadata) {
                    // 中断 → tool-confirm
                    MessageDTO messageDTO = new MessageDTO();
                    messageDTO.setMessageType(MessageType.TOOL_CONFIRM);
                    StringBuilder sb = new StringBuilder();
                    for (InterruptionMetadata.ToolFeedback feedback : interruptionMetadata.toolFeedbacks()) {
                        sb.append("工具: ").append(feedback.getName()).append("\n");
                        sb.append("评估: ").append(feedback.getDescription()).append("\n");
                    }
                    messageDTO.setContent(sb.toString());
                    response.setMessage(messageDTO);
                }

                String json = toJson(response);
                return ServerSentEvent.<String>builder().data(json).build();
            })
            // 结束信号
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                    .data("{\"error\":false}")
                    .build()))
            // 错误处理
            .onErrorResume(error -> {
                log.error("流式对话错误", error);
                String errorJson = String.format(
                        "{\"error\":true,\"errorType\":\"%s\",\"errorMessage\":\"%s\"}",
                        error.getClass().getSimpleName(),
                        error.getMessage() != null ? error.getMessage() : "Unknown error"
                );
                return Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(errorJson)
                        .build());
            });

    // 合并心跳和数据流
    return Flux.merge(dataFlux, heartbeatFlux);
}

/**
 * 对象转 JSON
 */
private String toJson(Object obj) {
    try {
        return new ObjectMapper().writeValueAsString(obj);
    } catch (JsonProcessingException e) {
        log.error("JSON 序列化失败", e);
        return "{}";
    }
}
```

**需要添加的 import：**

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuwen.visionspace.model.dto.agent.AgentRunResponse;
import com.yuwen.visionspace.model.dto.agent.MessageDTO;
import com.yuwen.visionspace.model.dto.agent.MessageType;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Duration;
```

**Step 2: 验证编译**

Run: `cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev" && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add src/main/java/com/yuwen/visionspace/controller/AgentController.java
git commit -m "feat(agent): 改造流式接口为 POST + 统一响应结构"
```

---

## Task 5: 验证后端构建

**Files:**
- None (build verification)

**Step 1: Run Maven compile**

Run: `cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev" && mvn compile -q`
Expected: BUILD SUCCESS

**Step 2: Commit (if not already committed)**

```bash
git add -A
git commit -m "chore(agent): 验证后端构建"
```

---

## Task 6: 改造 useAgentStream.ts

**Files:**
- Modify: `frontend/src/composables/useAgentStream.ts`

**Step 1: Read current file**

**新的类型定义：**

```typescript
export interface AgentRunResponse {
  node: string
  agent?: string
  chunk?: string
  message?: MessageDTO
  tokenUsage?: any
  error?: boolean
  errorType?: string
  errorMessage?: string
}

export interface MessageDTO {
  messageType: 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool'
  content?: string
  toolCalls?: any[]
  toolName?: string
}

export interface StreamMessage {
  type: string
  content: string
  node: string
  isLoading?: boolean
  toolName?: string
}
```

**新的 sendMessage 实现：**

```typescript
const sendMessage = async (message: string, threadId: string) => {
  isStreaming.value = true
  messages.value = []
  abortController = new AbortController()

  const baseUrl = import.meta.env.VITE_APP_API_BASE_URL || 'http://localhost:8081'
  const url = `${baseUrl}/api/agent/image/chat/stream`

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({
        message: message,
        threadId: threadId,
      }),
      signal: abortController.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('ReadableStream not supported')
    }

    const decoder = new TextDecoder()
    let buffer = ''
    let accumulatedContent = ''
    let isFirstChunk = true

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed.startsWith('data:')) {
          continue
        }

        let jsonStr = trimmed.slice(5).trimStart()
        if (!jsonStr) continue

        try {
          const data: AgentRunResponse = JSON.parse(jsonStr)

          // 跳过心跳
          if (data.node === 'heartbeat') continue

          // 处理错误
          if (data.error) {
            messages.value.push({
              type: 'error',
              content: data.errorMessage || 'Unknown error',
              node: 'error',
            })
            continue
          }

          // 处理文本 chunk
          if (data.chunk) {
            if (isFirstChunk) {
              messages.value.push({
                type: 'assistant',
                content: data.chunk,
                node: data.node || 'agent',
                isLoading: true,
              })
              isFirstChunk = false
            } else {
              accumulatedContent += data.chunk
              const lastMsg = messages.value[messages.value.length - 1]
              if (lastMsg && lastMsg.type === 'assistant') {
                lastMsg.content = accumulatedContent
              }
            }
          }

          // 处理完整消息
          if (data.message) {
            const msgType = data.message.messageType
            if (msgType === 'tool-request') {
              messages.value.push({
                type: 'tool-request',
                content: data.message.content || '',
                node: data.node || 'agent',
                toolName: data.message.toolName,
              })
            } else if (msgType === 'tool-confirm') {
              messages.value.push({
                type: 'tool-confirm',
                content: data.message.content || '',
                node: data.node || 'agent',
              })
            } else if (msgType === 'tool') {
              messages.value.push({
                type: 'tool-response',
                content: data.message.content || '',
                node: data.node || 'agent',
                toolName: data.message.toolName,
              })
            }
            // 重置 chunk 累积
            isFirstChunk = true
            accumulatedContent = ''
          }
        } catch (e) {
          // JSON 解析失败，跳过
        }
      }
    }

    // 标记最后一条消息为完成
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.type === 'assistant') {
      lastMsg.isLoading = false
    }
  } catch (error: any) {
    if (error.name === 'AbortError') {
      console.log('Request was aborted')
    } else {
      console.error('SSE stream error:', error)
      throw error
    }
  } finally {
    isStreaming.value = false
    abortController = null
  }
}
```

**更新 abort 函数：**

```typescript
const abort = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  isStreaming.value = false
}
```

**Step 2: 验证 TypeScript**

Run: `cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev/frontend" && npx tsc --noEmit 2>&1 | head -20`
Expected: 无错误或仅已有类型错误

**Step 3: Commit**

```bash
git add frontend/src/composables/useAgentStream.ts
git commit -m "feat(agent): 改造 useAgentStream 支持 chunk 累积和 abortController"
```

---

## Task 7: 改造 AgentChatPage.vue

**Files:**
- Modify: `frontend/src/pages/AgentChatPage.vue`

**Step 1: Read and analyze current file**

需要修改 `displayMessages` 计算属性，适配新的消息结构。

**新的 displayMessages：**

```typescript
const displayMessages = computed(() => {
  const result: any[] = [...userMessages.value]

  let accumulatedAssistant = ''

  for (const msg of messages.value) {
    if (msg.type === 'assistant') {
      // 累积 assistant 消息
      if (accumulatedAssistant) {
        accumulatedAssistant += msg.content
      } else {
        accumulatedAssistant = msg.content
      }
    } else {
      // 非 assistant 消息，先输出累积的
      if (accumulatedAssistant) {
        result.push({ type: 'assistant', content: accumulatedAssistant })
        accumulatedAssistant = ''
      }

      if (msg.type === 'tool-request' || msg.type === 'tool-response' || msg.type === 'tool-confirm' || msg.type === 'error') {
        result.push({
          type: msg.type,
          content: msg.content,
          toolName: msg.toolName,
        })
      }
    }
  }

  // 输出最后累积的 assistant 消息
  if (accumulatedAssistant) {
    result.push({ type: 'assistant', content: accumulatedAssistant })
  }

  return result
})
```

**Step 2: 验证构建**

Run: `cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev/frontend" && npx vite build 2>&1 | tail -10`
Expected: dist/index.html 等输出

**Step 3: Commit**

```bash
git add frontend/src/pages/AgentChatPage.vue
git commit -m "feat(agent): 适配新的流式消息结构"
```

---

## Task 8: 改造 AgentChatInput.vue

**Files:**
- Modify: `frontend/src/components/agent/AgentChatInput.vue`

**Step 1: Read current file and add stop handler**

**Step 2: Commit**

```bash
git add frontend/src/components/agent/AgentChatInput.vue
git commit -m "feat(agent): AgentChatInput 支持 stop 按钮"
```

---

## Task 9: 验证前端构建

**Files:**
- None (build verification)

**Step 1: Run Vite build**

Run: `cd "D:/Work/code/VisionSpace/.worktrees/feature-agent-dev/frontend" && npx vite build 2>&1 | tail -15`
Expected: 无错误

**Step 2: Commit (if needed)**

```bash
git add -A
git commit -m "chore(agent): 验证前端构建"
```

---

## Task 10: 提交所有更改并推送

**Step 1: Push to remote**

```bash
git push origin feature/agent-dev
```

---

## 执行方式选择

**1. Subagent-Driven (当前会话)** — 我按任务逐个派发子代理实现，任务间 review，速度快

**2. Parallel Session (新会话)** — 在新会话中使用 executing-plans，批量执行带检查点

你选择哪种方式？
