<template>
  <div class="glass-section">
    <h2 class="section-title">AI 偏好</h2>
    <p class="preference-subtitle">
      您显式设置的偏好始终优先于 AI 从反馈中推断的结论；自动学习默认关闭，开启后才会积累偏好。
    </p>

    <!-- 自动学习开关 -->
    <div class="form-group learn-toggle">
      <div>
        <label class="form-label">自动学习</label>
        <p class="learn-hint">
          {{
            autoLearn ? '开启中：AI 会从您的反馈里学习偏好' : '关闭中：AI 不会记录或学习任何偏好'
          }}
        </p>
      </div>
      <a-switch :checked="autoLearn" :loading="toggleLoading" @change="doToggleAutoLearn" />
    </div>

    <!-- 显式设置 -->
    <div class="form-group">
      <label class="form-label">喜欢的风格</label>
      <a-select
        v-model:value="explicitForm.liked_styles"
        mode="tags"
        placeholder="输入并回车添加，例如：水彩、极简"
        class="glass-input"
        :max-tag-count="8"
      />
    </div>
    <div class="form-group">
      <label class="form-label">不喜欢的风格</label>
      <a-select
        v-model:value="explicitForm.disliked_styles"
        mode="tags"
        placeholder="输入并回车添加，例如：过于写实"
        class="glass-input"
        :max-tag-count="8"
      />
    </div>
    <div class="form-group">
      <label class="form-label">喜欢的色调</label>
      <a-select
        v-model:value="explicitForm.liked_colors"
        mode="tags"
        placeholder="输入并回车添加，例如：亮色系"
        class="glass-input"
        :max-tag-count="8"
      />
    </div>
    <div class="form-group">
      <label class="form-label">补充说明</label>
      <a-textarea
        v-model:value="explicitForm.notes"
        placeholder="想长期让 AI 记住的要求，例如：整体偏亮，避免暗色调"
        :rows="3"
        :maxlength="2000"
        show-count
        class="glass-input"
      />
    </div>

    <div class="form-actions">
      <a-button type="primary" :loading="saving" @click="doSaveExplicit">保存设置</a-button>
      <a-popconfirm
        title="清除后 AI 学到的偏好与您的设置都会被删除，且无法恢复。确定清除？"
        ok-text="清除"
        cancel-text="取消"
        :ok-button-props="{ danger: true }"
        @confirm="doClear"
      >
        <a-button danger :loading="clearing">
          <Trash2 :size="14" style="vertical-align: -2px; margin-right: 4px" />
          清除全部偏好
        </a-button>
      </a-popconfirm>
    </div>

    <!-- 自动学习产物（只读展示） -->
    <div v-if="hasLearned" class="form-group">
      <label class="form-label">AI 学到的偏好</label>
      <div class="learned-block">
        <div v-if="learnedLists.liked_styles.length" class="learned-row">
          <span class="learned-key">喜欢的风格</span>
          <a-tag v-for="item in learnedLists.liked_styles" :key="`ls-${item}`">{{ item }}</a-tag>
        </div>
        <div v-if="learnedLists.disliked_styles.length" class="learned-row">
          <span class="learned-key">不喜欢的风格</span>
          <a-tag v-for="item in learnedLists.disliked_styles" :key="`ds-${item}`">{{ item }}</a-tag>
        </div>
        <div v-if="learnedLists.liked_colors.length" class="learned-row">
          <span class="learned-key">喜欢的色调</span>
          <a-tag v-for="item in learnedLists.liked_colors" :key="`lc-${item}`">{{ item }}</a-tag>
        </div>
        <p class="learned-hint">
          共 {{ feedbackHistory.length }} 条反馈记录；清除偏好会同时删除这些学习产物。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { Trash2 } from 'lucide-vue-next'
import {
  clearAgentPreference,
  getAgentPreference,
  setAgentAutoLearn,
  updateAgentPreference,
  type PreferenceExplicit,
  type PreferenceView,
} from '@/api/agentPreference'

const autoLearn = ref(false)
const toggleLoading = ref(false)
const saving = ref(false)
const clearing = ref(false)
const feedbackHistory = ref<Record<string, unknown>[]>([])

const explicitForm = reactive<PreferenceExplicit>({
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

const lastView = ref<PreferenceView>()

const loadPreference = async () => {
  const res = await getAgentPreference()
  if (res.data?.code !== 0) {
    message.error(res.data?.message ?? '偏好加载失败')
    return
  }
  const view = res.data.data
  lastView.value = view
  autoLearn.value = view?.autoLearn ?? false
  explicitForm.liked_styles = view?.explicit?.liked_styles ?? []
  explicitForm.disliked_styles = view?.explicit?.disliked_styles ?? []
  explicitForm.liked_colors = view?.explicit?.liked_colors ?? []
  explicitForm.notes = view?.explicit?.notes ?? ''
  feedbackHistory.value = view?.feedbackHistory ?? []
}

const doToggleAutoLearn = async (checked: boolean | string | number) => {
  toggleLoading.value = true
  try {
    const res = await setAgentAutoLearn(Boolean(checked))
    if (res.data?.code !== 0) {
      message.error(res.data?.message ?? '开关设置失败')
      return
    }
    autoLearn.value = Boolean(checked)
    lastView.value = res.data.data
    message.success(checked ? '已开启自动学习' : '已关闭自动学习')
  } finally {
    toggleLoading.value = false
  }
}

const doSaveExplicit = async () => {
  saving.value = true
  try {
    const res = await updateAgentPreference({
      liked_styles: explicitForm.liked_styles,
      disliked_styles: explicitForm.disliked_styles,
      liked_colors: explicitForm.liked_colors,
      notes: explicitForm.notes,
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

const doClear = async () => {
  clearing.value = true
  try {
    const res = await clearAgentPreference()
    if (res.data?.code !== 0) {
      message.error(res.data?.message ?? '清除失败')
      return
    }
    message.success('已清除全部偏好')
    await loadPreference()
  } finally {
    clearing.value = false
  }
}

onMounted(loadPreference)
</script>

<style scoped>
.preference-subtitle {
  margin: calc(var(--space-2) * -1) 0 var(--space-5);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.learn-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  background: var(--glass-bg);
}

.learn-toggle .form-label {
  margin-bottom: 2px;
}

.learn-hint {
  margin: 0;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.learned-block {
  padding: var(--space-3) var(--space-4);
  border: 1px dashed var(--glass-border);
  border-radius: var(--radius-xl);
}

.learned-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.learned-key {
  min-width: 84px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--text-secondary);
}

.learned-hint {
  margin: var(--space-2) 0 0;
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.form-actions {
  display: flex;
  gap: var(--space-3);
  align-items: center;
}
</style>
