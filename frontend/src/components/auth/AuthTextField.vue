<template>
  <label class="field-label" :for="id">{{ label }}</label>
  <a-input
    :id="id"
    v-model:value="model"
    :type="inputType"
    :placeholder="placeholder"
    size="large"
    :autocomplete="autocomplete"
  >
    <template v-if="type === 'password'" #suffix>
      <button
        type="button"
        class="field-toggle"
        :aria-label="`${revealed ? '隐藏' : '显示'}${revealLabel ?? label}`"
        :aria-pressed="revealed"
        @click="revealed = !revealed"
      >
        <EyeOff v-if="revealed" :size="16" aria-hidden="true" />
        <Eye v-else :size="16" aria-hidden="true" />
      </button>
    </template>
  </a-input>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'

/**
 * 认证表单的一行输入：标签、控件的 id 与说明文案绑在一起，
 * 密码字段自带可见性切换。样式随组件走，外壳不再认识这些类名。
 */
const props = withDefaults(
  defineProps<{
    id: string
    label: string
    type?: 'text' | 'password'
    placeholder?: string
    autocomplete?: string
    /** 无障碍文案里的对象名，省略时用 label */
    revealLabel?: string
  }>(),
  { type: 'text' },
)

// 可选的表单字段（如确认密码）也要能直接绑定
const model = defineModel<string>()

const revealed = ref(false)
const inputType = computed(() =>
  props.type === 'password' && revealed.value ? 'text' : props.type,
)
</script>

<style scoped lang="less">
.field-label {
  display: block;
  margin-bottom: 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

/* 自绘的密码可见性切换：antd 自带图标 tabindex=-1，键盘无法操作 */
.field-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  margin-right: -0.25rem;
  padding: 0;
  border: none;
  border-radius: 0.25rem;
  background: transparent;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: color 0.2s ease;

  &:hover {
    color: var(--color-text-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--auth-accent);
    outline-offset: 2px;
  }
}
</style>
