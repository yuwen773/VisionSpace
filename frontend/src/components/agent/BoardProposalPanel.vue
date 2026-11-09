<template>
  <section class="proposal-panel" aria-label="候选板保存提案">
    <header>
      <small>AGENT · PERSONAL DRAFT</small>
      <h3>让 Agent 整理候选板</h3>
    </header>
    <template v-if="boardId && !task && !pendingPlan">
      <label
        >整理要求<textarea
          v-model="instruction"
          aria-label="整理要求"
          maxlength="2000"
          placeholder="例如：取消选择第一张，将其余素材分组"
        />
      </label>
      <button
        data-action="plan-board"
        :disabled="busy || disabled || !threadId || !instruction.trim()"
        @click="plan"
      >
        生成保存提案
      </button>
      <p v-if="disabled">请先保存本页修改，再让 Agent 整理已保存的板。</p>
    </template>
    <p v-if="busy" role="status">正在核对任务与当前权限…</p>
    <template v-if="task">
      <p class="proposal-status">
        {{ labels[task.status] }}<span v-if="task.revision"> · 版本 {{ task.revision }}</span>
      </p>
      <p v-if="task.reasonCode">{{ reasons[task.reasonCode] || '请读取当前状态，重新审阅提案' }}</p>
      <template v-if="task.current && task.target">
        <p>来源版本 {{ task.expectedRevision }} · 提案版本 {{ task.proposalRevision }}</p>
        <p class="proposal-effect">
          只保存个人草稿：选图、顺序和分组。原图保持不变，不向团队共享。
        </p>
        <div class="proposal-groups">
          <span>当前分组：{{ task.current.groups.map((g) => g.name).join('、') || '无' }}</span
          ><span>提案分组：{{ task.target.groups.map((g) => g.name).join('、') || '无' }}</span>
        </div>
        <table>
          <caption>
            当前 → 提案
          </caption>
          <thead>
            <tr>
              <th>素材</th>
              <th>当前</th>
              <th>提案</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in differences" :key="row.id">
              <th>
                <img
                  v-if="row.contentRef"
                  class="proposal-preview"
                  :src="apiUrl(`/api${row.contentRef}`)"
                  :alt="row.label"
                  @error="refresh"
                />{{ row.label }}
              </th>
              <td>{{ row.before }}</td>
              <td>{{ row.after }}</td>
            </tr>
          </tbody>
        </table>
      </template>
      <p v-else>当前无法展示私人差异。本人有效会话仍可核对状态或取消未决提案。</p>
      <div class="proposal-actions">
        <button data-action="read-task" :disabled="busy" @click="refresh">核对任务状态</button>
        <template v-if="task.status === 'AWAITING_CONFIRMATION'">
          <button data-action="cancel-proposal" :disabled="busy" @click="decide('CANCEL')">
            取消提案
          </button>
          <button
            v-if="task.canConfirm && task.target"
            class="confirm"
            data-action="confirm-proposal"
            :disabled="busy"
            @click="decide('CONFIRM')"
          >
            确认保存个人草稿
          </button>
        </template>
        <button
          v-else-if="task.boardId"
          data-action="new-proposal"
          :disabled="busy"
          @click="newProposal"
        >
          整理新版
        </button>
      </div>
    </template>
    <button v-if="pendingPlan" data-action="retry-plan" :disabled="busy" @click="submitPlan">
      核对原提案请求
    </button>
    <p v-if="error" role="status">{{ error }}</p>
    <small>停止聊天展示不会取消提案；取消须点击“取消提案”。</small>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'
import {
  planBoard,
  readTask,
  decideProposal,
  type BoardPlan,
  type BoardTask,
} from '@/api-custom/boardProposal'
import type { AssetBoard, BoardItem } from '@/api-custom/assetBoard'
import { apiUrl } from '@/apiOrigin'
const props = defineProps<{
  boardId?: string
  revision?: number
  threadId?: string
  disabled?: boolean
  privacyEpoch?: number
}>()
const emit = defineEmits<{
  boardAvailable: [boardId: string]
  closePreview: []
  sessionBound: [threadId: string]
}>()
const route = useRoute(),
  router = useRouter(),
  login = useLoginUserStore()
const instruction = ref(''),
  error = ref(''),
  busy = ref(false)
const task = ref<BoardTask>(),
  pendingPlan = ref<BoardPlan>()
let epoch = 0
const labels: Record<BoardTask['status'], string> = {
  AWAITING_CONFIRMATION: '等待你的决定',
  COMMITTED: '已提交',
  CANCELLED: '已取消',
  CONFLICT: '发生冲突，需要新提案',
  REJECTED: '引用不可用，保存已拒绝',
}
const reasons: Record<string, string> = {
  INVALID_OPERATION_STATE: '该任务已经结束，未执行新的保存',
  REVISION_CONFLICT: '板已更新，请读取新版后重新提案',
  REFERENCE_STALE: '素材依据已变化，请重新检索',
  PROPOSAL_SUPERSEDED: '已被新提案替代',
  RESOURCE_UNAVAILABLE: '当前引用或空间不可用',
}
const describe = (item: BoardItem | undefined, board: AssetBoard) =>
  item
    ? `第 ${item.position + 1} 项 · ${item.selected ? '已选' : '未选'} · ${board.groups.find((g) => g.groupId === item.groupId)?.name || '未分组'}`
    : '移除/未加入'
const differences = computed(() => {
  const before = task.value?.current,
    after = task.value?.target
  if (!before || !after) return []
  const ids = new Set([...before.items, ...after.items].map((i) => i.boardItemId))
  return [...ids].map((id, index) => {
    const prior = before.items.find((i) => i.boardItemId === id)
    const next = after.items.find((i) => i.boardItemId === id)
    return {
      id,
      label: `素材 ${index + 1}`,
      before: describe(prior, before),
      after: describe(next, after),
      contentRef: (prior || next)?.display?.contentRef,
    }
  })
})
function stripPrivate() {
  if (task.value) {
    const t = task.value
    task.value = {
      schemaVersion: 1,
      type: t.type,
      taskId: t.taskId,
      proposalId: t.proposalId,
      proposalRevision: t.proposalRevision,
      operationId: t.operationId,
      status: t.status,
      canConfirm: false,
    }
  }
  emit('closePreview')
}
function accept(t: BoardTask) {
  task.value = t
  if (t.threadId) emit('sessionBound', t.threadId)
  if (t.boardId && t.current && t.target) emit('boardAvailable', t.boardId)
}
async function refresh() {
  const id =
    task.value?.taskId || (typeof route.query.task === 'string' ? route.query.task : undefined)
  if (!id) return
  const started = ++epoch
  stripPrivate()
  busy.value = true
  try {
    const res = await readTask(id)
    if (started !== epoch) return
    if (
      res.data.code === 0 &&
      res.data.data?.schemaVersion === 1 &&
      res.data.data.type === 'BOARD_SAVE_PROPOSAL'
    ) {
      accept(res.data.data)
      error.value = ''
    } else {
      task.value = undefined
      pendingPlan.value = undefined
      error.value = '任务不可访问，请检查当前登录和会话'
    }
  } catch {
    if (started === epoch) error.value = '状态暂时无法核对，请重试；不会自动保存'
  } finally {
    if (started === epoch) busy.value = false
  }
}
async function plan() {
  if (!props.boardId || !props.revision || !props.threadId || props.disabled) return
  pendingPlan.value = {
    proposalRequestId: crypto.randomUUID(),
    threadId: props.threadId,
    boardId: props.boardId,
    expectedRevision: props.revision,
    message: instruction.value,
  }
  await submitPlan()
}
async function submitPlan() {
  if (!pendingPlan.value || busy.value) return
  const started = ++epoch
  busy.value = true
  error.value = ''
  try {
    const res = await planBoard(pendingPlan.value)
    if (started !== epoch) return
    if (
      res.data.code !== 0 ||
      res.data.data?.schemaVersion !== 1 ||
      res.data.data.type !== 'BOARD_SAVE_PROPOSAL'
    ) {
      if ([40100, 40101, 40300].includes(res.data.code)) {
        pendingPlan.value = undefined
        stripPrivate()
      }
      error.value = '提案未完成，请核对当前权限与板版本'
      return
    }
    accept(res.data.data)
    pendingPlan.value = undefined
    await router.replace({ query: { ...route.query, task: res.data.data.taskId } })
  } catch {
    if (started === epoch) error.value = '提案结果未知，请核对原请求；不会自动保存'
  } finally {
    if (started === epoch) busy.value = false
  }
}
async function decide(decision: 'CONFIRM' | 'CANCEL') {
  const t = task.value
  if (
    !t ||
    busy.value ||
    t.status !== 'AWAITING_CONFIRMATION' ||
    (decision === 'CONFIRM' && !t.canConfirm)
  )
    return
  const started = ++epoch
  stripPrivate()
  busy.value = true
  error.value = ''
  try {
    const res = await decideProposal(t.proposalId, {
      operationId: t.operationId,
      proposalRevision: t.proposalRevision,
      decision,
    })
    if (started !== epoch) return
    if (
      res.data.code === 0 &&
      res.data.data?.type === 'BOARD_SAVE_PROPOSAL' &&
      res.data.data.schemaVersion === 1
    )
      accept(res.data.data)
    else {
      error.value = '决定未完成，请核对任务状态'
      if ([40100, 40101, 40300].includes(res.data.code)) task.value = undefined
    }
  } catch {
    if (started === epoch) error.value = '决定结果未知，请核对任务；原操作标识保持不变'
  } finally {
    if (started === epoch) busy.value = false
  }
}
async function newProposal() {
  await router.replace({
    query: { ...route.query, task: undefined, board: task.value?.boardId || props.boardId },
  })
}
watch(
  () => route.query.task,
  (id) => {
    if (typeof id === 'string' && id !== task.value?.taskId) void refresh()
  },
  { immediate: true },
)
watch(
  () => login.loginUser.id,
  () => {
    epoch++
    task.value = undefined
    pendingPlan.value = undefined
    instruction.value = ''
    busy.value = false
    error.value = ''
    emit('closePreview')
  },
)
watch(
  () => props.privacyEpoch,
  () => {
    pendingPlan.value = undefined
    void refresh()
  },
)
onMounted(() => window.addEventListener('focus', refresh))
onUnmounted(() => {
  epoch++
  window.removeEventListener('focus', refresh)
})
</script>

<style scoped>
.proposal-panel {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-primary);
  border-radius: 12px;
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
}
header small {
  color: var(--color-text-secondary);
  letter-spacing: 0.08em;
  font-size: 10px;
}
h3 {
  font-size: 16px;
  line-height: 1.4;
  margin: 5px 0 14px;
  font-family: var(--font-heading);
}
label,
textarea,
.proposal-groups span {
  display: block;
}
textarea {
  width: 100%;
  min-height: 72px;
  margin: 8px 0;
  padding: 8px;
  box-sizing: border-box;
  background: var(--color-bg-primary);
  color: inherit;
  border: 1px solid var(--color-border);
  border-radius: 6px;
}
button {
  padding: 7px 10px;
  background: var(--color-bg-primary);
  color: inherit;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
.confirm {
  background: var(--color-primary);
  color: var(--color-text-on-primary, white);
}
.proposal-status {
  font-weight: 600;
}
.proposal-effect {
  padding: 10px;
  border-left: 2px solid var(--color-primary);
}
p,
td,
th,
small,
.proposal-groups {
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  table-layout: fixed;
}
td,
th {
  text-align: left;
  padding: 7px 4px;
  border-bottom: 1px solid var(--color-border);
  vertical-align: top;
}
caption {
  caption-side: top;
  text-align: left;
  color: var(--color-text-secondary);
}
.proposal-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.proposal-preview {
  display: block;
  width: 100%;
  max-height: 64px;
  object-fit: contain;
  border-radius: 4px;
  margin-bottom: 4px;
}
</style>
