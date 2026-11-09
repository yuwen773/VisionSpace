<template>
  <section class="board-editor" aria-label="个人候选素材板">
    <header>
      <span>PERSONAL COLLECTION</span>
      <h3>候选素材板</h3>
      <small>{{ revision ? `已保存 · 版本 ${revision}` : '个人整理草稿' }}</small>
    </header>
    <p v-if="validating" role="status">正在核对当前访问权限…</p>
    <fieldset v-else :disabled="busy || !!pending">
      <div class="board-import">
        <select v-model="sourceId" aria-label="选择来源快照">
          <option v-for="result in results" :key="result.resultId" :value="result.resultId">
            {{ result.resultId.slice(0, 8) }} · {{ result.items.length }} 张素材
          </option></select
        ><button data-action="import" :disabled="!sourceId" @click="importResult">
          加入候选板
        </button>
      </div>
      <label
        >板标题<input v-model="title" aria-label="板标题" maxlength="100" @input="dirty = true"
      /></label>
      <p class="board-note">
        {{ dirty ? '未保存修改 · 离开页面将丢失' : '明确保存后可刷新恢复；未保存编辑不承诺恢复' }}
      </p>
      <div class="groups">
        <label>新分组<input v-model="groupName" aria-label="新分组名称" maxlength="100" /></label
        ><button :disabled="groups.length >= 20 || !groupName.trim()" @click="addGroup">
          添加分组
        </button>
        <div v-for="(group, index) in groups" :key="group.groupId" class="group-row">
          <input
            v-model="group.name"
            aria-label="分组名称"
            maxlength="100"
            @input="dirty = true"
          /><button aria-label="上移分组" :disabled="index === 0" @click="moveGroup(index, -1)">
            ↑</button
          ><button
            aria-label="下移分组"
            :disabled="index === groups.length - 1"
            @click="moveGroup(index, 1)"
          >
            ↓</button
          ><button aria-label="删除分组" @click="removeGroup(group.groupId)">删组</button>
        </div>
      </div>
      <ol>
        <li
          v-for="(item, index) in items"
          :key="item.boardItemId || `${item.resultId}-${item.sourcePosition}`"
        >
          <template v-if="item.status !== 'UNAVAILABLE'"
            ><label
              ><input
                v-model="item.selected"
                type="checkbox"
                aria-label="选择素材"
                @change="dirty = true"
              /><strong
                >{{ item.verifiedFacts?.width }} × {{ item.verifiedFacts?.height }}</strong
              ></label
            >
            <button
              v-if="item.display"
              class="board-preview"
              aria-label="预览候选素材"
              @click="emit('preview', contentUrl(item.display.contentRef))"
            >
              <img
                :src="contentUrl(item.display.contentRef)"
                alt="候选素材预览"
                @error="previewFailed"
              />
            </button>
            <small v-if="item.status === 'STALE'">依据过期，请重新检索后补充</small>
            <select v-model="item.groupId" aria-label="素材分组" @change="dirty = true">
              <option :value="undefined">未分组</option>
              <option v-for="group in groups" :key="group.groupId" :value="group.groupId">
                {{ group.name }}
              </option>
            </select> </template
          ><span v-else>素材不可用，请显式移除</span>
          <div class="item-actions">
            <button aria-label="上移素材" :disabled="index === 0" @click="moveItem(index, -1)">
              ↑</button
            ><button
              aria-label="下移素材"
              :disabled="index === items.length - 1"
              @click="moveItem(index, 1)"
            >
              ↓</button
            ><button aria-label="移除素材" @click="removeItem(index)">移除</button>
          </div>
        </li>
      </ol>
    </fieldset>
    <p v-if="error" role="status">{{ error }}</p>
    <button v-if="conflict" data-action="read-latest" :disabled="busy" @click="readLatest">
      读取最新版（放弃本页修改）
    </button>
    <template v-if="pending || minimalOperationId"
      ><button data-action="check-operation" :disabled="busy" @click="checkOperation">
        核对保存结果</button
      ><button
        v-if="pending && retryAllowed"
        data-action="retry"
        :disabled="busy"
        @click="submitPending"
      >
        重试原保存
      </button></template
    >
    <button
      v-else
      data-action="save"
      class="save-board"
      :disabled="busy || validating || !dirty"
      @click="save"
    >
      {{ busy ? '保存中…' : '保存候选板' }}
    </button>
    <BoardProposalPanel
      :board-id="boardId"
      :revision="revision"
      :thread-id="threadId"
      :disabled="dirty || busy || validating || !!pending"
      :privacy-epoch="privacyEpoch"
      @committed="load"
      @close-preview="emit('closePreview')"
      @session-bound="emit('sessionBound', $event)"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router'
import { useLoginUserStore } from '@/stores/userLogin'
import { apiUrl } from '@/apiOrigin'
import type { AssetResult } from '@/api-custom/assetSearch'
import BoardProposalPanel from './BoardProposalPanel.vue'
import {
  saveBoard,
  readBoard,
  readBoardOperation,
  type BoardSave,
  type BoardGroup,
  type BoardOperation,
} from '@/api-custom/assetBoard'

const props = defineProps<{ results: AssetResult[]; privacyEpoch?: number; threadId?: string }>()
const emit = defineEmits<{
  preview: [url: string]
  accessDenied: []
  closePreview: []
  sessionBound: [threadId: string]
}>()
const router = useRouter()
const route = useRoute()
const loginStore = useLoginUserStore()
type DraftItem = AssetResult['items'][number] & {
  boardItemId?: string
  resultId?: string
  sourcePosition?: number
  selected: boolean
  groupId?: string
}
const sourceId = ref('')
const boardId = ref<string>()
const revision = ref<number>()
const title = ref('我的候选板')
const items = ref<DraftItem[]>([])
const groups = ref<BoardGroup[]>([])
const dirty = ref(false)
const busy = ref(false)
const error = ref('')
const initialResult = ref<string>()
const pending = ref<{ boardId?: string; body: BoardSave }>()
const minimalOperationId = ref<string>()
const retryAllowed = ref(false)
const validating = ref(false)
const conflict = ref(false)
const groupName = ref('')
const boardSpaceId = ref<string>()
let epoch = 0
let readSequence = 0
const contentUrl = (ref: string) => apiUrl(`/api${ref}`)
watch(
  () => props.results,
  (results) => {
    if (!sourceId.value || !results.some((r) => r.resultId === sourceId.value))
      sourceId.value = results[0]?.resultId || ''
    items.value = items.value.map((item) => {
      if (item.boardItemId) return item
      const snapshot = results.find((r) => r.resultId === item.resultId)
      if (!snapshot) return item
      const fresh = snapshot.items.find((i) => i.position === item.sourcePosition)
      if (!fresh || fresh.status === 'UNAVAILABLE')
        return {
          resultId: item.resultId,
          sourcePosition: item.sourcePosition,
          sourceVersion: item.sourceVersion,
          position: item.position,
          selected: item.selected,
          status: 'UNAVAILABLE',
        }
      return {
        ...fresh,
        resultId: item.resultId,
        sourcePosition: item.sourcePosition,
        selected: item.selected,
        groupId: item.groupId,
      }
    })
    if (items.value.some((item) => item.status === 'UNAVAILABLE')) {
      emit('closePreview')
      title.value = '候选素材板'
      groups.value = groups.value.map((g) => ({ ...g, name: '不可用分组' }))
    }
  },
  { immediate: true },
)
function importResult() {
  const source = props.results.find((r) => r.resultId === sourceId.value)
  if (!source) return
  if (boardSpaceId.value && boardSpaceId.value !== source.spaceId) {
    error.value = '只能加入同一个团队空间的快照'
    return
  }
  boardSpaceId.value = source.spaceId
  if (!initialResult.value) initialResult.value = source.resultId
  for (const item of source.items) {
    if (
      item.status !== 'AVAILABLE' ||
      items.value.some((existing) => existing.assetRef?.pictureId === item.assetRef?.pictureId)
    )
      continue
    items.value.push({
      ...item,
      resultId: source.resultId,
      sourcePosition: item.position,
      selected: true,
    })
  }
  dirty.value = true
}
function addGroup() {
  groups.value.push({
    groupId: crypto.randomUUID(),
    position: groups.value.length,
    name: groupName.value.trim(),
  })
  groupName.value = ''
  dirty.value = true
}
function removeGroup(id: string) {
  groups.value = groups.value.filter((g) => g.groupId !== id)
  items.value.forEach((i) => {
    if (i.groupId === id) i.groupId = undefined
  })
  dirty.value = true
}
function moveItem(index: number, delta: number) {
  const item = items.value.splice(index, 1)[0]
  if (item) items.value.splice(index + delta, 0, item)
  dirty.value = true
}
function removeItem(index: number) {
  items.value.splice(index, 1)
  dirty.value = true
}
function moveGroup(index: number, delta: number) {
  const group = groups.value.splice(index, 1)[0]
  if (group) groups.value.splice(index + delta, 0, group)
  dirty.value = true
}
function clearPrivate(keepOperation = false) {
  const operationId = pending.value?.body.operationId || minimalOperationId.value
  epoch++
  items.value = []
  groups.value = []
  title.value = '我的候选板'
  boardId.value = undefined
  revision.value = undefined
  boardSpaceId.value = undefined
  initialResult.value = undefined
  pending.value = undefined
  minimalOperationId.value = keepOperation ? operationId : undefined
  retryAllowed.value = false
  groupName.value = ''
  dirty.value = false
  busy.value = false
  validating.value = false
  conflict.value = false
}
function deny() {
  clearPrivate(true)
  error.value = '候选板不可用，请检查当前登录与空间权限'
  emit('accessDenied')
}
async function load(id: string, preserveEdits = false) {
  const started = epoch
  const sequence = ++readSequence
  validating.value = true
  try {
    const response = await readBoard(id)
    if (started !== epoch || sequence !== readSequence) return
    if (response.data.code !== 0 || response.data.data?.schemaVersion !== 1) {
      deny()
      return
    }
    const current = response.data.data
    if (boardId.value === current.boardId && revision.value && current.revision < revision.value)
      return
    if (current.items.some((item) => item.status === 'UNAVAILABLE')) emit('closePreview')
    boardId.value = current.boardId
    boardSpaceId.value = current.spaceId
    if (preserveEdits && dirty.value) {
      items.value = items.value.map((item) => {
        if (!item.boardItemId) return item
        const fresh = current.items.find((f) => f.boardItemId === item.boardItemId)
        if (!fresh || fresh.status === 'UNAVAILABLE')
          return {
            boardItemId: item.boardItemId,
            position: item.position,
            status: 'UNAVAILABLE',
            selected: false,
          }
        return { ...fresh, selected: item.selected, groupId: item.groupId }
      })
      if (!current.title) {
        title.value = '候选素材板'
        groups.value = current.groups
      }
    } else {
      title.value = current.title || '候选素材板'
      items.value = current.items.map((item) => ({ ...item, selected: item.selected ?? false }))
      groups.value = current.groups
      revision.value = current.revision
      dirty.value = false
    }
  } catch {
    if (started === epoch && sequence === readSequence) {
      clearPrivate(true)
      error.value = '无法核对当前访问权限，请重新读取候选板'
    }
  } finally {
    if (started === epoch && sequence === readSequence) validating.value = false
  }
}
async function readLatest() {
  if (boardId.value && (!dirty.value || window.confirm('读取最新版将放弃本页未保存修改，继续？'))) {
    conflict.value = false
    await load(boardId.value)
  }
}
function previewFailed() {
  if (boardId.value) void load(boardId.value, true)
  else deny()
}
async function save() {
  if (busy.value || validating.value) return
  if (items.value.some((item) => item.status !== 'AVAILABLE')) {
    error.value = '请显式移除不可用或过期素材，或从新快照补充'
    return
  }
  const body: BoardSave = {
    operationId: crypto.randomUUID(),
    title: title.value,
    groups: groups.value.map((g, position) => ({ ...g, position })),
    ...(boardId.value ? { expectedRevision: revision.value } : { resultId: initialResult.value }),
    items: items.value.map((item, position) => ({
      ...(item.boardItemId
        ? { boardItemId: item.boardItemId }
        : { resultId: item.resultId, sourcePosition: item.sourcePosition }),
      sourceVersion: item.sourceVersion || '',
      selected: item.selected,
      position,
      ...(item.groupId ? { groupId: item.groupId } : {}),
    })),
  }
  pending.value = { boardId: boardId.value, body: JSON.parse(JSON.stringify(body)) }
  await submitPending()
}
async function acceptReceipt(receipt: BoardOperation) {
  const started = epoch
  if (!receipt.boardId) {
    deny()
    error.value = '操作已提交；当前无权读取候选板'
    return
  }
  await router.replace({
    query: { ...route.query, assetResult: undefined, board: receipt.boardId },
  })
  if (started !== epoch) return
  await load(receipt.boardId)
  if (started !== epoch || !boardId.value) return
  minimalOperationId.value = undefined
  pending.value = undefined
  retryAllowed.value = false
  error.value = ''
}
async function submitPending() {
  if (!pending.value || busy.value) return
  busy.value = true
  const started = epoch
  try {
    const response = await saveBoard(pending.value.boardId, pending.value.body)
    if (started !== epoch) return
    if (
      response.data.code === 0 &&
      response.data.data?.schemaVersion === 1 &&
      response.data.data.status === 'COMMITTED'
    )
      await acceptReceipt(response.data.data)
    else if (response.data.code === 50000) error.value = '保存结果未知，请核对后再试'
    else if ([40100, 40101, 40300].includes(response.data.code)) deny()
    else {
      conflict.value = response.data.message === 'REVISION_CONFLICT'
      error.value = conflict.value
        ? '版本冲突，请读取最新版后由你决定修改'
        : '保存被拒绝，请检查分组、来源与字段限制'
      pending.value = undefined
    }
  } catch {
    if (started === epoch) error.value = '保存结果未知，请核对后再试'
  } finally {
    if (started === epoch) busy.value = false
  }
}
async function checkOperation() {
  const operationId = pending.value?.body.operationId || minimalOperationId.value
  if (!operationId || busy.value) return
  busy.value = true
  const started = epoch
  try {
    const response = await readBoardOperation(operationId)
    if (started !== epoch) return
    if ([40100, 40101, 40300].includes(response.data.code)) {
      deny()
      return
    }
    if (response.data.code !== 0 || response.data.data?.schemaVersion !== 1) {
      error.value = '无法核对保存结果，请检查登录状态'
      return
    }
    if (response.data.data.status === 'COMMITTED') await acceptReceipt(response.data.data)
    else {
      retryAllowed.value = !!pending.value
      error.value = pending.value
        ? '尚未查到提交记录，不能认定未保存；只能重试原操作'
        : '尚未查到提交记录；已清空私人内容，请保留操作标识继续核对'
    }
  } catch {
    if (started === epoch) error.value = '保存结果未知，请稍后继续核对'
  } finally {
    if (started === epoch) busy.value = false
  }
}
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (dirty.value || pending.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
const onFocus = () => {
  if (boardId.value) void load(boardId.value, true)
}
onBeforeRouteLeave(
  () =>
    (!dirty.value && !pending.value) ||
    window.confirm('本页有未保存修改或待核对操作，离开将丢失本页状态，继续？'),
)
watch(
  () => loginStore.loginUser.id,
  () => {
    clearPrivate()
    error.value = ''
  },
)
watch(
  () => props.privacyEpoch,
  () => clearPrivate(true),
)
onMounted(() => {
  if (typeof route.query.board === 'string') void load(route.query.board)
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('focus', onFocus)
})
onUnmounted(() => {
  clearPrivate()
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('focus', onFocus)
})
</script>

<style scoped>
.board-editor {
  margin: 12px;
  padding: 18px;
  border: 1px solid var(--color-border-subtle);
  border-top: 3px solid var(--color-primary-500);
  border-radius: 12px;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
}
fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
header span {
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--color-text-tertiary);
}
h3 {
  margin: 6px 0;
  font-family: var(--font-display);
}
small,
.board-note {
  color: var(--color-text-secondary);
  font-size: 11px;
}
.board-import {
  display: flex;
  gap: 6px;
  margin: 16px 0;
}
select,
input:not([type='checkbox']) {
  min-width: 0;
  width: 100%;
  padding: 8px;
  background: var(--color-bg-primary);
  color: inherit;
  border: 1px solid var(--color-border-subtle);
  border-radius: 6px;
}
label {
  font-size: 12px;
}
ol {
  padding: 0;
  list-style: none;
}
li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-subtle);
}
button {
  padding: 7px 9px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 6px;
  background: var(--color-bg-primary);
  color: inherit;
  cursor: pointer;
  white-space: nowrap;
}
.save-board {
  width: 100%;
  color: var(--color-primary-500);
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 2px;
}
.groups {
  display: grid;
  gap: 8px;
}
.group-row,
.item-actions {
  display: flex;
  gap: 4px;
}
.board-preview {
  padding: 0;
  overflow: hidden;
  width: 100%;
}
.board-preview img {
  width: 100%;
  max-height: 130px;
  object-fit: cover;
  display: block;
}
li {
  flex-wrap: wrap;
}
li select {
  flex: 1;
}
.item-actions {
  margin-left: auto;
}
</style>
