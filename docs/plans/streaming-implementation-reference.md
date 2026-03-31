# 流式对话实现参考文档

> 来源: spring-ai-alibaba deepresearch + spring-ai-alibaba-studio
> 生成日期: 2026-03-31

---

## 一、核心架构

### 1.1 技术栈

| 项目 | 后端 | 前端 | 流式协议 |
|------|------|------|----------|
| deepresearch | Spring Boot 3.5.7 + Spring AI 1.1.2 | Next.js 15 + React 19 | SSE |
| spring-ai-alibaba-studio | Spring Boot 2.7 + Spring AI | Next.js + React | SSE |

### 1.2 数据流架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端                                  │
├─────────────────────────────────────────────────────────────┤
│  UI 组件 (消息列表)                                          │
│       │                                                      │
│  StreamContext (状态管理)                                    │
│       │                                                      │
│  API Client (SSE 解析)                                       │
│       │                                                      │
│  fetch + ReadableStream                                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP POST + SSE
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                        后端                                  │
├─────────────────────────────────────────────────────────────┤
│  Controller (/run_sse)                                       │
│       │                                                      │
│  Agent.stream() → Flux<NodeOutput>                          │
│       │                                                      │
│  ServerSentEvent<String> 封装                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、后端实现

### 2.1 SSE 端点 (Spring)

```java
@PostMapping(value = "/run_sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> agentRunSse(@RequestBody AgentRunRequest request) {
    Agent agent = agentLoader.loadAgent(request.appName);
    RunnableConfig runnableConfig = RunnableConfig.builder()
            .threadId(request.threadId)
            .addMetadata("user_id", request.userId)
            .build();

    return executeAgent(request.newMessage.toUserMessage(), agent, runnableConfig);
}
```

### 2.2 流式响应结构

```java
public class AgentRunResponse {
    protected String node;           // 当前节点名称 (如 "heartbeat")
    protected String agent;          // Agent 名称
    protected Usage tokenUsage;      // Token 使用量
    protected MessageDTO message;   // 完整消息 (工具调用等)
    protected String chunk;          // 流式文本块 (增量文本)
}
```

### 2.3 核心转换逻辑

```java
private Flux<ServerSentEvent<String>> executeAgent(...) {
    Flux<NodeOutput> agentStream = agent.stream(userMessage, runnableConfig);

    return agentStream
        // 1. 过滤 AGENT_MODEL_FINISHED 避免重复
        .filter(nodeOutput -> !(nodeOutput instanceof StreamingOutput<?> so
                && so.getOutputType() == OutputType.AGENT_MODEL_FINISHED))
        .map(nodeOutput -> {
            AgentRunResponse agentResponse;

            if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                Message message = streamingOutput.message();
                if (message instanceof AssistantMessage assistantMessage) {
                    if (!assistantMessage.hasToolCalls()) {
                        // 2. 文本流式输出 → chunk 字段
                        agentResponse = new AgentRunResponse(
                            node, agentName, tokenUsage,
                            null,                    // message
                            assistantMessage.getText() // chunk
                        );
                    } else {
                        // 3. 工具调用 → message 字段
                        agentResponse = new AgentRunResponse(
                            node, agentName, tokenUsage,
                            MessageDTO.fromAssistantMessage(assistantMessage),
                            null                     // chunk
                        );
                    }
                }
            } else if (nodeOutput instanceof InterruptionMetadata interruptionMetadata) {
                // 4. Human-in-Loop 中断 → tool-confirm
                agentResponse = new AgentRunResponse(
                    node, agentName, tokenUsage,
                    MessageDTO.MessageDTOFactory.fromInterruptionMetadata(interruptionMetadata),
                    null
                );
            }

            String jsonData = mapper.writeValueAsString(agentResponse);
            return ServerSentEvent.<String>builder().data(jsonData).build();
        })
        .onErrorResume(error -> {
            // 5. 错误处理
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
        });
}
```

### 2.4 关键设计点

1. **chunk vs message 分离**:
   - `chunk`: 流式文本片段，用于文本的增量更新
   - `message`: 完整消息结构，用于工具调用、中断等

2. **心跳机制**: `node === "heartbeat"` 用于保持连接，前端跳过处理

3. **过滤 AGENT_MODEL_FINISHED**: 避免重复的流结束事件

---

## 三、前端实现 (Vue 3 适配)

### 3.1 API 客户端

```typescript
// types/agent.ts
export interface AgentRunResponse {
  node: string;
  agent: string;
  chunk?: string;
  message?: MessageDTO;
  tokenUsage?: Usage;
}

export interface MessageDTO {
  messageType: 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool';
  content?: string;
  toolCalls?: ToolCall[];
  toolName?: string;
}
```

```typescript
// composables/useAgentStream.ts
export const useAgentStream = () => {
  const messages = ref<any[]>([]);
  const isStreaming = ref(false);
  const abortController = ref<AbortController | null>(null);

  const sendMessage = async (content: string, threadId?: string) => {
    isStreaming.value = true;
    abortController.value = new AbortController();

    // 1. 发起 SSE 请求
    const response = await fetch('/api/agent/run_sse', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({
        appName: 'default',
        userId: getCurrentUserId(),
        threadId: threadId || generateThreadId(),
        newMessage: { content },
      }),
      signal: abortController.value.signal,
    });

    // 2. 处理 SSE 流
    const reader = response.body?.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.trim().startsWith('data:')) {
            const data = line.slice(5).trim();
            if (data && data !== '[DONE]') {
              const agentResponse: AgentRunResponse = JSON.parse(data);
              handleAgentResponse(agentResponse);
            }
          }
        }
      }
    } finally {
      isStreaming.value = false;
    }
  };

  // 3. 处理响应
  const handleAgentResponse = (response: AgentRunResponse) => {
    // 跳过心跳
    if (response.node === 'heartbeat') return;

    // 处理错误
    if ('error' in response && response.error) {
      console.error('Agent error:', response.errorMessage);
      return;
    }

    // 处理文本 chunk
    if (response.chunk) {
      if (messages.value.length === 0 ||
          messages.value[messages.value.length - 1].type !== 'assistant') {
        // 创建新消息
        messages.value.push({
          type: 'assistant',
          content: response.chunk,
          isLoading: true,
        });
      } else {
        // 追加到现有消息
        const lastMsg = messages.value[messages.value.length - 1];
        lastMsg.content += response.chunk;
      }
    }

    // 处理完整消息
    if (response.message) {
      const msgType = response.message.messageType;
      if (msgType === 'tool-request') {
        messages.value.push({
          type: 'tool-request',
          toolName: response.message.toolName,
          content: response.message.content,
        });
      } else if (msgType === 'tool-confirm') {
        messages.value.push({
          type: 'tool-confirm',
          content: response.message.content,
        });
      } else if (msgType === 'tool') {
        messages.value.push({
          type: 'tool-response',
          toolName: response.message.toolName,
          content: response.message.content,
        });
      }
    }
  };

  // 4. 停止生成
  const stop = () => {
    if (abortController.value) {
      abortController.value.abort();
      abortController.value = null;
    }
    isStreaming.value = false;
  };

  return { messages, isStreaming, sendMessage, stop };
};
```

### 3.2 消息类型定义

```typescript
// types/message.ts
export type MessageType = 'user' | 'assistant' | 'tool-request' | 'tool-confirm' | 'tool';

export interface BaseMessage {
  type: MessageType;
  content: string;
  time?: string;
}

export interface UserMessage extends BaseMessage {
  type: 'user';
}

export interface AssistantMessage extends BaseMessage {
  type: 'assistant';
  isLoading?: boolean;
  toolCalls?: ToolCall[];
}

export interface ToolRequestMessage extends BaseMessage {
  type: 'tool-request';
  toolName: string;
}

export interface ToolConfirmMessage extends BaseMessage {
  type: 'tool-confirm';
}

export interface ToolResponseMessage extends BaseMessage {
  type: 'tool-response';
  toolName: string;
}

export type Message = UserMessage | AssistantMessage | ToolRequestMessage | ToolConfirmMessage | ToolResponseMessage;
```

### 3.3 消息渲染组件映射

```vue
<!-- AgentMessageList.vue -->
<template v-for="(msg, index) in messages" :key="index">
  <!-- User Message -->
  <transition v-if="msg.type === 'user'" name="message-slide">
    <UserMessage :content="msg.content" :time="msg.time" />
  </transition>

  <!-- Assistant Message -->
  <transition v-else-if="msg.type === 'assistant'" name="message-slide">
    <AssistantMessage :content="msg.content" :isLoading="msg.isLoading" />
  </transition>

  <!-- Tool Request -->
  <transition v-else-if="msg.type === 'tool-request'" name="message-slide">
    <ToolRequestMessage :toolName="msg.toolName" :description="msg.content" />
  </transition>

  <!-- Tool Confirm (Human-in-Loop) -->
  <transition v-else-if="msg.type === 'tool-confirm'" name="message-slide">
    <ToolConfirmMessage :message="msg.content" @confirm="emit('confirm')" @cancel="emit('cancel')" />
  </transition>

  <!-- Tool Response -->
  <transition v-else-if="msg.type === 'tool-response'" name="message-slide">
    <ToolResponseMessage :toolName="msg.toolName" :content="msg.content" />
  </transition>
</template>
```

---

## 四、关键实现细节

### 4.1 SSE 解析 (ReadableStream)

```typescript
private async *_processSSEStream(response: Response): AsyncGenerator<AgentRunResponse> {
  const reader = response.body?.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    // 解码并追加到缓冲区
    buffer += decoder.decode(value, { stream: true });

    // 按行分割
    const lines = buffer.split('\n');
    // 保留未完成的行
    buffer = lines.pop() || '';

    for (const line of lines) {
      // 解析 data: 前缀
      if (line.trim().startsWith('data:')) {
        const data = line.slice(5).trim();
        if (data && data !== '[DONE]') {
          yield JSON.parse(data);
        }
      }
    }
  }
}
```

### 4.2 消息累积模式

```typescript
// 首次收到 chunk → 创建新消息
if (isFirstChunk) {
  messages.value.push({
    type: 'assistant',
    content: response.chunk,
    isLoading: true,
  });
  isFirstChunk = false;
}
// 后续 chunk → 追加到末尾
else {
  const lastMsg = messages.value[messages.value.length - 1];
  lastMsg.content += response.chunk;
}
```

### 4.3 AbortController 取消

```typescript
// 停止生成
const stop = () => {
  if (abortController.value) {
    abortController.value.abort();
    abortController.value = null;
  }
  isStreaming.value = false;
};

// 组件卸载时自动取消
onUnmounted(() => {
  stop();
});
```

### 4.4 错误处理

```typescript
try {
  // SSE 流式处理...
} catch (error: any) {
  if (error.name === 'AbortError') {
    console.log('Request cancelled');
  } else {
    console.error('Stream error:', error);
    messages.value.push({
      type: 'error',
      content: error.message || 'Unknown error',
    });
  }
} finally {
  isStreaming.value = false;
}
```

---

## 五、可借鉴的最佳实践

### 5.1 消息类型视觉设计

| 类型 | 样式 | 场景 |
|------|------|------|
| user | 右对齐，渐变背景 | 用户输入 |
| assistant | 左对齐，深色背景 | AI 回复 |
| tool-request | 蓝色边框 + 工具图标 | 工具调用请求 |
| tool-confirm | 绿色强调 | 需要用户确认 |
| tool-response | 灰色背景，可折叠 | 工具执行结果 |

### 5.2 流式体验优化

1. **打字指示器**: 流式传输时显示三点跳动动画
2. **自动滚动**: 新消息到达时自动滚动到底部
3. **未读提示**: 用户向上滚动时显示"滚动到底部"按钮
4. **消息动画**: 新消息滑入效果

### 5.3 Markdown 渲染

```typescript
// 使用 markdown-it + highlight.js
import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js';

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight: (str: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value;
      } catch (__) {}
    }
    return ''; // 使用默认转义
  },
});
```

### 5.4 滚动保持

```typescript
// 使用 @vueuse/core
import { useScrollBottom } from '@vueuse/core';

const containerRef = ref<HTMLElement>();
const { isAtBottom, scrollToBottom } = useScrollBottom(containerRef);

// 新消息时自动滚动
watch(() => messages.value.length, () => {
  if (isAtBottom.value) {
    nextTick(() => scrollToBottom());
  }
});
```

---

## 六、文件结构参考

```
frontend/src/
├── components/agent/
│   ├── AgentChatPage.vue        # 主页面
│   ├── AgentChatHeader.vue      # 头部
│   ├── AgentChatInput.vue       # 输入框
│   ├── AgentMessageList.vue     # 消息列表
│   ├── HistoryPanel.vue         # 历史记录面板
│   ├── IterationProgress.vue    # 进度可视化
│   └── message/
│       ├── UserMessage.vue          # 用户消息
│       ├── AssistantMessage.vue     # AI 消息
│       ├── ToolRequestMessage.vue   # 工具请求
│       ├── ToolConfirmMessage.vue   # 工具确认
│       └── ToolResponseMessage.vue  # 工具响应
├── composables/
│   ├── useAgentStream.ts        # 流式对话
│   └── useMarkdown.ts           # Markdown 渲染
├── types/
│   └── agent.ts                 # 类型定义
└── styles/
    └── code-theme.css           # 代码高亮主题
```

---

## 七、后端接口适配

### 7.1 请求格式

```json
POST /api/agent/run_sse
Content-Type: application/json
Accept: text/event-stream

{
  "appName": "default",
  "userId": "user123",
  "threadId": "thread456",
  "newMessage": {
    "content": "用户输入的消息"
  }
}
```

### 7.2 响应格式 (SSE)

```
data: {"node":"agent","agent":"my-agent","chunk":"Hello"}

data: {"node":"agent","agent":"my-agent","chunk":" world"}

data: {"node":"agent","agent":"my-agent","message":{"messageType":"tool-request","toolName":"search","content":"正在搜索..."}}

data: {"node":"agent","agent":"my-agent","message":{"messageType":"tool","toolName":"search","content":"搜索结果: xxx"}}

data: {"node":"agent","agent":"my-agent","chunk":"是的，我找到了相关信息。"}

data: {"error":false}
```

### 7.3 工具调用确认流程

```
1. 后端发送 tool-request 消息
2. 前端显示确认对话框
3. 用户确认/取消
4. 前端调用 resumeFeedback API
5. 后端继续流式输出
```

---

## 八、当前项目集成状态

### 8.1 已实现

- ✅ `useAgentStream.ts` - SSE 流式接收
- ✅ `AssistantMessage.vue` - Markdown 渲染 + 代码高亮
- ✅ 消息类型区分 (user/assistant/tool-request/tool-confirm/tool)
- ✅ 停止生成按钮

### 8.2 待优化

- [ ] 消息打字效果 (当前是直接更新，可增加逐字动画)
- [ ] 滚动体验增强 (未读数提示)
- [ ] 工具调用确认面板完善
- [ ] 错误边界处理

---

## 参考文件

| 来源 | 文件 | 说明 |
|------|------|------|
| spring-ai-alibaba-studio | `.../controller/ExecutionController.java` | 后端 SSE 端点 |
| spring-ai-alibaba-studio | `agent-chat-ui/src/lib/spring-ai-api.ts` | 前端 SSE 解析 |
| spring-ai-alibaba-studio | `agent-chat-ui/src/providers/Stream.tsx` | 流式状态管理 |
| deepresearch | `agent-chat-ui/src/providers/Thread.tsx` | 消息渲染入口 |
| deepresearch | `agent-chat-ui/src/components/thread/messages/ai.tsx` | AI 消息组件 |
