<template>
  <RecordSection
    index="04"
    title="AI 偏好"
    label="MEMORY"
    lede="你显式设置的偏好始终优先于 AI 从反馈中推断的结论。自动学习默认关闭，开启后才会积累偏好；任何一项都可以随时清除。"
  >
    <!-- 自动学习 -->
    <div class="learn-row">
      <div class="learn-copy">
        <p class="learn-title">自动学习</p>
        <p class="uc-hint">
          {{
            autoLearn
              ? '开启中：AI 会从你的反馈里学习偏好。'
              : '关闭中：AI 不会记录或学习任何偏好。'
          }}
        </p>
      </div>
      <button
        type="button"
        role="switch"
        class="uc-switch"
        :class="{ 'is-on': autoLearn }"
        :aria-checked="autoLearn"
        :disabled="toggleLoading"
        @click="onToggleAutoLearn"
      >
        <span class="switch-track" aria-hidden="true"><span class="switch-thumb"></span></span>
        <span class="mono switch-state">{{ toggleLoading ? '…' : autoLearn ? 'ON' : 'OFF' }}</span>
      </button>
    </div>

    <!-- 显式设置 -->
    <div class="uc-subhead explicit-heading">
      <span>显式设置</span>
      <span class="mono">EXPLICIT</span>
    </div>

    <div class="uc-form-row">
      <label class="uc-label" for="uc-liked-styles">
        <span>喜欢的风格</span>
        <span class="mono">LIKED STYLES</span>
      </label>
      <a-select
        id="uc-liked-styles"
        v-model:value="explicitForm.liked_styles"
        mode="tags"
        placeholder="输入并回车添加，例如：水彩、极简"
        class="uc-tags"
        :max-tag-count="8"
        aria-label="喜欢的风格"
      />
    </div>

    <div class="uc-form-row">
      <label class="uc-label" for="uc-disliked-styles">
        <span>不喜欢的风格</span>
        <span class="mono">DISLIKED STYLES</span>
      </label>
      <a-select
        id="uc-disliked-styles"
        v-model:value="explicitForm.disliked_styles"
        mode="tags"
        placeholder="输入并回车添加，例如：过于写实"
        class="uc-tags"
        :max-tag-count="8"
        aria-label="不喜欢的风格"
      />
    </div>

    <div class="uc-form-row">
      <label class="uc-label" for="uc-liked-colors">
        <span>喜欢的色调</span>
        <span class="mono">LIKED COLORS</span>
      </label>
      <a-select
        id="uc-liked-colors"
        v-model:value="explicitForm.liked_colors"
        mode="tags"
        placeholder="输入并回车添加，例如：亮色系"
        class="uc-tags"
        :max-tag-count="8"
        aria-label="喜欢的色调"
      />
    </div>

    <div class="uc-form-row">
      <label class="uc-label" for="uc-notes">
        <span>补充说明</span>
        <span class="mono">{{ explicitForm.notes.length }} / 2000</span>
      </label>
      <textarea
        id="uc-notes"
        v-model="explicitForm.notes"
        class="uc-field"
        rows="4"
        maxlength="2000"
        placeholder="想长期让 AI 记住的要求，例如：整体偏亮，避免暗色调"
      ></textarea>
    </div>

    <div class="uc-actions">
      <button type="button" class="uc-btn uc-btn--solid" :disabled="saving" @click="doSaveExplicit">
        {{ saving ? '保存中…' : '保存设置' }}
      </button>
      <button
        type="button"
        class="uc-btn uc-btn--danger"
        :disabled="clearing || confirmingClear"
        @click="askClear"
      >
        清除全部偏好
      </button>
    </div>

    <div v-if="confirmingClear" class="clear-confirm" role="group" aria-label="确认清除全部偏好">
      <p>清除后，AI 学到的偏好与你的设置都会被删除，且无法恢复。确定继续？</p>
      <div class="uc-actions">
        <button
          ref="confirmButton"
          type="button"
          class="uc-btn uc-btn--danger"
          :disabled="clearing"
          @click="doClear"
        >
          {{ clearing ? '清除中…' : '确认清除' }}
        </button>
        <button type="button" class="uc-btn uc-btn--quiet" @click="confirmingClear = false">
          取消
        </button>
      </div>
    </div>

    <!-- 学习产物 -->
    <div class="uc-subhead learned-heading">
      <span>AI 学到的偏好</span>
      <span class="mono">LEARNED</span>
    </div>

    <div v-if="hasLearned" class="learned-block">
      <div v-if="learnedLists.liked_styles.length" class="learned-row">
        <span class="learned-key mono">喜欢的风格</span>
        <span class="learned-tags">
          <span v-for="item in learnedLists.liked_styles" :key="`ls-${item}`" class="uc-chip">{{
            item
          }}</span>
        </span>
      </div>
      <div v-if="learnedLists.disliked_styles.length" class="learned-row">
        <span class="learned-key mono">不喜欢的风格</span>
        <span class="learned-tags">
          <span v-for="item in learnedLists.disliked_styles" :key="`ds-${item}`" class="uc-chip">{{
            item
          }}</span>
        </span>
      </div>
      <div v-if="learnedLists.liked_colors.length" class="learned-row">
        <span class="learned-key mono">喜欢的色调</span>
        <span class="learned-tags">
          <span v-for="item in learnedLists.liked_colors" :key="`lc-${item}`" class="uc-chip">{{
            item
          }}</span>
        </span>
      </div>
      <p class="uc-hint">
        共 {{ feedbackHistory.length }} 条反馈记录；清除偏好会同时删除这些学习产物。
      </p>
    </div>

    <p v-else class="uc-hint learned-empty">
      {{
        autoLearn
          ? '还没有学到偏好。反馈积累后，AI 学到的内容会显示在这里。'
          : '自动学习关闭，AI 不会积累新的偏好。'
      }}
    </p>
  </RecordSection>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  clearPreferenceUsingPost,
  getPreferenceUsingGet,
  setAutoLearnUsingPost,
  updatePreferenceUsingPost,
} from '@/api/agentPreferenceController'
import RecordSection from '@/components/user-center/RecordSection.vue'

/** 界面编辑用的显式设置形状（提交时包进 PreferenceUpdateRequest.explicit） */
interface PreferenceExplicitForm {
  liked_styles: string[]
  disliked_styles: string[]
  liked_colors: string[]
  notes: string
}

const autoLearn = ref(false)
const toggleLoading = ref(false)
const saving = ref(false)
const clearing = ref(false)
const confirmingClear = ref(false)
const confirmButton = ref<HTMLButtonElement>()
const feedbackHistory = ref<Record<string, unknown>[]>([])

const explicitForm = reactive<PreferenceExplicitForm>({
  liked_styles: [],
  disliked_styles: [],
  liked_colors: [],
  notes: '',
})

const learnedLists = computed(() => {
  const learned = (lastView.value?.learned ?? {}) as Record<string, unknown>
  const asList = (value: unknown) => (Array.isArray(value) ? value.map(String) : [])
  return {
    liked_styles: asList(learned['liked_styles']),
    disliked_styles: asList(learned['disliked_styles']),
    liked_colors: asList(learned['liked_colors']),
  }
})

const hasLearned = computed(
  () =>
    learnedLists.value.liked_styles.length > 0 ||
    learnedLists.value.disliked_styles.length > 0 ||
    learnedLists.value.liked_colors.length > 0 ||
    feedbackHistory.value.length > 0,
)

const lastView = ref<API.PreferenceViewVO>()

const loadPreference = async () => {
  const res = await getPreferenceUsingGet()
  if (res.data?.code !== 0) {
    message.error(res.data?.message ?? '偏好加载失败')
    return
  }
  const view = res.data.data
  lastView.value = view
  autoLearn.value = view?.autoLearn ?? false
  const explicit = view?.explicit ?? {}
  explicitForm.liked_styles = Array.isArray(explicit['liked_styles'])
    ? explicit['liked_styles'].map(String)
    : []
  explicitForm.disliked_styles = Array.isArray(explicit['disliked_styles'])
    ? explicit['disliked_styles'].map(String)
    : []
  explicitForm.liked_colors = Array.isArray(explicit['liked_colors'])
    ? explicit['liked_colors'].map(String)
    : []
  explicitForm.notes = typeof explicit['notes'] === 'string' ? explicit['notes'] : ''
  feedbackHistory.value = view?.feedbackHistory ?? []
}

const applyAutoLearn = async (checked: boolean) => {
  toggleLoading.value = true
  try {
    const res = await setAutoLearnUsingPost({ enabled: checked })
    if (res.data?.code !== 0) {
      message.error(res.data?.message ?? '开关设置失败')
      return
    }
    autoLearn.value = checked
    lastView.value = res.data.data
    message.success(checked ? '已开启自动学习' : '已关闭自动学习')
  } finally {
    toggleLoading.value = false
  }
}

const onToggleAutoLearn = () => {
  if (toggleLoading.value) return
  applyAutoLearn(!autoLearn.value)
}

const doSaveExplicit = async () => {
  saving.value = true
  try {
    const res = await updatePreferenceUsingPost({
      explicit: {
        liked_styles: explicitForm.liked_styles,
        disliked_styles: explicitForm.disliked_styles,
        liked_colors: explicitForm.liked_colors,
        notes: explicitForm.notes,
      },
    })
    if (res.data?.code !== 0) {
      message.error(res.data?.message ?? '保存失败')
      return
    }
    lastView.value = res.data.data
    message.success('偏好设置已保存')
  } finally {
    saving.value = false
  }
}

const askClear = () => {
  confirmingClear.value = true
  nextTick(() => confirmButton.value?.focus())
}

const doClear = async () => {
  clearing.value = true
  try {
    const res = await clearPreferenceUsingPost()
    if (res.data?.code !== 0) {
      message.error(res.data?.message ?? '清除失败')
      return
    }
    message.success('已清除全部偏好')
    confirmingClear.value = false
    await loadPreference()
  } finally {
    clearing.value = false
  }
}

onMounted(loadPreference)
</script>

<style scoped>
/* ============ 自动学习 ============ */
.learn-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-5) var(--space-5);
  border: 1px solid var(--uc-line);
  margin-bottom: clamp(28px, 4vh, 40px);
}

.learn-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 500;
  color: var(--uc-ink);
}

.learn-copy .uc-hint {
  margin: 0;
}

.uc-switch {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px;
  border: none;
  background: transparent;
  color: var(--uc-ink-2);
  cursor: pointer;
}

.switch-track {
  position: relative;
  display: block;
  width: 46px;
  height: 26px;
  border: 1px solid var(--uc-line-strong);
  border-radius: 999px;
  background: transparent;
  transition:
    background var(--transition-base),
    border-color var(--transition-base);
}

.switch-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--uc-ink-3);
  transition:
    transform 0.24s var(--uc-ease),
    background var(--transition-base);
}

.uc-switch.is-on .switch-track {
  background: var(--uc-accent);
  border-color: var(--uc-accent);
}

.uc-switch.is-on .switch-thumb {
  transform: translateX(20px);
  background: #0b0716;
}

.uc-switch.is-on .switch-state {
  color: var(--uc-accent);
}

.uc-switch:disabled {
  opacity: 0.5;
  cursor: wait;
}

.switch-state {
  min-width: 26px;
  font-size: 9px;
}

/* ============ 显式设置 ============ */
.explicit-heading {
  padding-top: var(--space-2);
}

/* ============ 清除确认 ============ */
.clear-confirm {
  margin-top: var(--space-5);
  padding: var(--space-5);
  border: 1px solid color-mix(in srgb, var(--uc-danger) 45%, transparent);
}

.clear-confirm p {
  margin: 0 0 var(--space-4);
  font-size: 14px;
  line-height: 1.8;
  color: var(--uc-ink-2);
}

/* ============ 学习产物 ============ */
.learned-heading {
  padding-top: clamp(32px, 5vh, 48px);
}

.learned-block {
  padding: var(--space-4) 0 0;
  border-top: 1px solid var(--uc-line);
}

.learned-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  padding: 10px 0;
  border-bottom: 1px solid var(--uc-line);
}

.learned-key {
  flex: 0 0 110px;
  font-size: 9px;
}

.learned-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.learned-empty {
  padding: var(--space-4) 0 0;
  border-top: 1px solid var(--uc-line);
}

@media (max-width: 640px) {
  .learn-row {
    align-items: flex-start;
    flex-direction: row;
    padding: var(--space-4);
  }

  .learned-row {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
