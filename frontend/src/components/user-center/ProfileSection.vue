<template>
  <div class="glass-section">
    <h2 class="section-title">个人资料</h2>

    <!-- 头像 -->
    <div class="avatar-row">
      <a-avatar :src="loginUser.userAvatar" :size="64" class="avatar-preview" />
      <div class="avatar-info">
        <span class="field-label">头像</span>
        <a-button class="change-avatar-btn" @click="showAvatarModal">更换头像</a-button>
      </div>
    </div>

    <div class="fields-list">
      <!-- 昵称 -->
      <div class="field-item">
        <span class="field-label">昵称</span>
        <div v-if="editingField !== 'userName'" class="field-value" @click="startEdit('userName')">
          <span>{{ loginUser.userName }}</span>
          <svg class="edit-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
        </div>
        <div v-else class="field-edit">
          <a-input v-model:value="editForm.userName" class="edit-input" @pressEnter="saveField('userName')" />
          <a-button size="small" @click="cancelEdit">取消</a-button>
          <a-button size="small" type="primary" :loading="saving" @click="saveField('userName')">保存</a-button>
        </div>
      </div>

      <!-- 简介 -->
      <div class="field-item">
        <span class="field-label">简介</span>
        <div v-if="editingField !== 'userProfile'" class="field-value" @click="startEdit('userProfile')">
          <span>{{ loginUser.userProfile || '暂无简介' }}</span>
          <svg class="edit-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
        </div>
        <div v-else class="field-edit">
          <a-input v-model:value="editForm.userProfile" class="edit-input" @pressEnter="saveField('userProfile')" />
          <a-button size="small" @click="cancelEdit">取消</a-button>
          <a-button size="small" type="primary" :loading="saving" @click="saveField('userProfile')">保存</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/userLogin'
import { updateUserUsingPost } from '@/api/userController'

const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

type EditableField = 'userName' | 'userProfile'
const editingField = ref<EditableField | null>(null)
const saving = ref(false)
const editForm = ref<Record<EditableField, string>>({
  userName: '',
  userProfile: ''
})

const startEdit = (field: EditableField) => {
  editForm.value[field] = loginUser[field] ?? ''
  editingField.value = field
}
const cancelEdit = () => {
  editingField.value = null
}

const fieldLabels: Record<EditableField, string> = {
  userName: '昵称',
  userProfile: '简介',
}

const saveField = async (field: EditableField) => {
  saving.value = true
  try {
    const res = await updateUserUsingPost({ id: loginUser.id, [field]: editForm.value[field] })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, [field]: editForm.value[field] })
      message.success(`${fieldLabels[field]}修改成功`)
      editingField.value = null
    }
  } catch (e) {
    message.error('修改失败')
  } finally {
    saving.value = false
  }
}

const showAvatarModal = () => {
  message.info('头像上传功能待实现')
}
</script>

<style scoped>
/* Avatar Row */
.avatar-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding-bottom: var(--space-5);
  margin-bottom: var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
}

.avatar-preview {
  border: 2px solid var(--border-accent);
  box-shadow: var(--shadow-glow-purple);
}

.avatar-info {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.field-label {
  font-weight: 600;
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.change-avatar-btn {
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  height: 28px;
  padding: 0 14px;
}

/* Fields */
.fields-list {
  display: flex;
  flex-direction: column;
}

.field-item {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--border-subtle);
}

.field-item:last-child {
  border-bottom: none;
}

.field-item .field-label {
  width: 64px;
  flex-shrink: 0;
}

.field-value {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  color: var(--text-primary);
  font-weight: 500;
}

.field-value:hover {
  background: var(--bg-hover);
}

.edit-icon {
  width: 14px;
  height: 14px;
  opacity: 0;
  color: var(--color-primary);
  transition: opacity var(--transition-fast);
  flex-shrink: 0;
}

.field-value:hover .edit-icon {
  opacity: 1;
}

.field-edit {
  flex: 1;
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.edit-input {
  flex: 1;
}
</style>
