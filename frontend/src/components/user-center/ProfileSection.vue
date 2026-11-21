<template>
  <RecordSection
    index="01"
    title="个人资料"
    label="IDENTITY"
    lede="这些信息会出现在你的档案页上。昵称与简介可以随时改写；账号、身份和加入时间由系统记录。"
  >
    <div class="profile-grid">
      <!-- 肖像 -->
      <div class="portrait">
        <div
          class="portrait-frame"
          :class="{ 'is-dragging': isDragging }"
          @dragover.prevent="isDragging = true"
          @dragleave.prevent="isDragging = false"
          @drop.prevent="onDrop"
        >
          <img
            v-if="loginUser.userAvatar && !avatarError"
            :src="loginUser.userAvatar"
            :alt="`${displayName} 的头像`"
            @error="avatarError = true"
          />
          <span v-else class="portrait-initial" aria-hidden="true">{{ initial }}</span>
          <span class="portrait-tag mono">PORTRAIT / 01</span>
          <span class="portrait-drop mono">松开以更换头像</span>
        </div>

        <div class="portrait-actions">
          <button
            type="button"
            class="uc-btn uc-btn--line"
            :disabled="avatarUploading"
            @click="triggerAvatarUpload"
          >
            {{ avatarUploading ? '上传中…' : '更换头像' }}
          </button>
          <span class="mono portrait-note">JPG / PNG / WEBP · ≤ 2MB</span>
        </div>

        <input
          ref="avatarInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          @change="handleAvatarChange"
        />
      </div>

      <!-- 记录 -->
      <div class="identity">
        <div class="uc-subhead">
          <span>可编辑</span>
          <span class="mono">EDITABLE</span>
        </div>

        <div class="field-row">
          <span class="field-label mono">昵称 / NAME</span>
          <div v-if="editingField !== 'userName'" class="field-display">
            <span class="field-value">{{ loginUser.userName || '—' }}</span>
            <button
              type="button"
              class="uc-btn uc-btn--quiet field-edit"
              :aria-label="`编辑昵称，当前为 ${loginUser.userName || '空'}`"
              @click="startEdit('userName')"
            >
              编辑
            </button>
          </div>
          <div v-else class="field-edit">
            <label class="sr-only" for="uc-edit-userName">昵称</label>
            <input
              id="uc-edit-userName"
              ref="editInput"
              v-model="editForm.userName"
              class="uc-field"
              maxlength="40"
              @keydown.enter.prevent="saveField('userName')"
              @keydown.esc="cancelEdit"
            />
            <div class="uc-actions field-actions">
              <button
                type="button"
                class="uc-btn uc-btn--solid"
                :disabled="saving"
                @click="saveField('userName')"
              >
                保存
              </button>
              <button type="button" class="uc-btn uc-btn--quiet" @click="cancelEdit">取消</button>
            </div>
          </div>
        </div>

        <div class="field-row">
          <span class="field-label mono">简介 / BIO</span>
          <div v-if="editingField !== 'userProfile'" class="field-display">
            <span class="field-value field-value--prose">{{
              loginUser.userProfile || '还没有写下简介。'
            }}</span>
            <button
              type="button"
              class="uc-btn uc-btn--quiet field-edit"
              aria-label="编辑简介"
              @click="startEdit('userProfile')"
            >
              编辑
            </button>
          </div>
          <div v-else class="field-edit">
            <label class="sr-only" for="uc-edit-userProfile">简介</label>
            <textarea
              id="uc-edit-userProfile"
              ref="editInput"
              v-model="editForm.userProfile"
              class="uc-field"
              rows="3"
              maxlength="200"
              @keydown.esc="cancelEdit"
            ></textarea>
            <div class="uc-actions field-actions">
              <button
                type="button"
                class="uc-btn uc-btn--solid"
                :disabled="saving"
                @click="saveField('userProfile')"
              >
                保存
              </button>
              <button type="button" class="uc-btn uc-btn--quiet" @click="cancelEdit">取消</button>
            </div>
          </div>
        </div>

        <div class="uc-subhead record-heading">
          <span>账号记录</span>
          <span class="mono">ON RECORD</span>
        </div>

        <dl class="record-list">
          <div class="record-row">
            <dt class="mono">账号 / ACCOUNT</dt>
            <dd>{{ loginUser.userAccount || '—' }}</dd>
          </div>
          <div class="record-row">
            <dt class="mono">身份 / ROLE</dt>
            <dd>{{ roleLabel }}</dd>
          </div>
          <div class="record-row">
            <dt class="mono">加入 / SINCE</dt>
            <dd>{{ joinLabel }}</dd>
          </div>
        </dl>
      </div>
    </div>
  </RecordSection>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import dayjs from 'dayjs'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/userLogin'
import { updateUserUsingPost, uploadAvatarUsingPost } from '@/api/userController'
import RecordSection from '@/components/user-center/RecordSection.vue'

const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

type EditableField = 'userName' | 'userProfile'
const editingField = ref<EditableField | null>(null)
const saving = ref(false)
const editForm = ref<Record<EditableField, string>>({
  userName: '',
  userProfile: '',
})
const editInput = ref<HTMLInputElement | HTMLTextAreaElement>()

const displayName = computed(() => loginUser.userName || loginUser.userAccount || '未命名用户')
const initial = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => {
  if (loginUser.userRole === 'admin') return '平台管理员'
  if (loginUser.userRole === 'moderator') return '内容处置员'
  return '个人创作者'
})
const joinLabel = computed(() =>
  loginUser.createTime ? dayjs(loginUser.createTime).format('YYYY.MM.DD') : '—',
)

const startEdit = (field: EditableField) => {
  editForm.value[field] = loginUser[field] ?? ''
  editingField.value = field
  nextTick(() => {
    editInput.value?.focus()
    editInput.value?.select?.()
  })
}

const cancelEdit = () => {
  editingField.value = null
}

const fieldLabels: Record<EditableField, string> = {
  userName: '昵称',
  userProfile: '简介',
}

const saveField = async (field: EditableField) => {
  if (saving.value) return
  saving.value = true
  try {
    const res = await updateUserUsingPost({ id: loginUser.id, [field]: editForm.value[field] })
    if (res.data.code === 0) {
      loginUserStore.setUserLogin({ ...loginUser, [field]: editForm.value[field] })
      message.success(`${fieldLabels[field]}修改成功`)
      editingField.value = null
    } else {
      message.error(res.data.message || `${fieldLabels[field]}修改失败`)
    }
  } catch {
    message.error(`${fieldLabels[field]}修改失败`)
  } finally {
    saving.value = false
  }
}

const avatarUploading = ref(false)
const avatarError = ref(false)
const isDragging = ref(false)
const avatarInput = ref<HTMLInputElement>()

const triggerAvatarUpload = () => {
  avatarInput.value?.click()
}

const uploadAvatar = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('文件大小不能超过 2MB')
    return
  }
  avatarUploading.value = true
  try {
    const res = await uploadAvatarUsingPost(file)
    if (res.data.code === 0) {
      await loginUserStore.fetchUserLogin()
      avatarError.value = false
      message.success('头像更换成功')
    } else {
      message.error(res.data.message || '头像上传失败')
    }
  } catch {
    message.error('头像上传失败')
  } finally {
    avatarUploading.value = false
    if (avatarInput.value) avatarInput.value.value = ''
  }
}

const handleAvatarChange = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) await uploadAvatar(file)
}

const onDrop = async (event: DragEvent) => {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) await uploadAvatar(file)
}
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(200px, 260px) minmax(0, 1fr);
  gap: clamp(28px, 5vw, 64px);
  align-items: start;
}

/* ============ 肖像 ============ */
.portrait {
  min-width: 0;
}

.portrait-frame {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 4 / 5;
  border: 1px solid var(--uc-line);
  background:
    repeating-linear-gradient(
      -45deg,
      transparent 0,
      transparent 13px,
      rgba(244, 241, 234, 0.04) 13px,
      rgba(244, 241, 234, 0.04) 14px
    ),
    var(--uc-bg-raised);
  overflow: hidden;
  transition: border-color var(--transition-fast);
}

.is-light .portrait-frame {
  background:
    repeating-linear-gradient(
      -45deg,
      transparent 0,
      transparent 13px,
      rgba(124, 58, 237, 0.06) 13px,
      rgba(124, 58, 237, 0.06) 14px
    ),
    var(--uc-bg-raised);
}

.portrait-frame.is-dragging {
  border-color: var(--uc-accent);
}

.portrait-frame img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.92) contrast(1.02);
}

.portrait-initial {
  font-family: var(--uc-serif);
  font-size: clamp(64px, 8vw, 96px);
  font-weight: 500;
  color: var(--uc-ink-3);
  line-height: 1;
}

.portrait-tag {
  position: absolute;
  top: 10px;
  left: 12px;
  font-size: 8px;
  color: rgba(255, 255, 255, 0.66);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.45);
}

.portrait-initial + .portrait-tag {
  color: var(--uc-ink-3);
  text-shadow: none;
}

.portrait-drop {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(5, 6, 12, 0.78);
  color: #f4f1ea;
  font-size: 10px;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.portrait-frame.is-dragging .portrait-drop {
  opacity: 1;
}

.portrait-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  margin-top: var(--space-4);
}

.portrait-note {
  font-size: 8px;
}

/* ============ 记录 ============ */
.identity {
  min-width: 0;
}

.field-row {
  padding: 0 0 var(--space-6);
  margin-bottom: var(--space-6);
  border-bottom: 1px solid var(--uc-line);
}

.field-row:last-of-type {
  margin-bottom: var(--space-2);
}

.field-label {
  display: block;
  margin-bottom: 10px;
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--uc-ink-3);
}

.field-display {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-6);
}

.field-value {
  min-width: 0;
  font-size: clamp(19px, 1.7vw, 24px);
  line-height: 1.5;
  font-weight: 500;
  color: var(--uc-ink);
  word-break: break-word;
}

.field-value--prose {
  font-size: 16px;
  font-weight: 400;
  line-height: 1.9;
  color: var(--uc-ink-2);
  max-width: 56ch;
  text-wrap: pretty;
}

.field-edit {
  flex-shrink: 0;
  font-size: 12px;
}

.field-actions {
  margin-top: var(--space-3);
}

.record-heading {
  padding-top: var(--space-8);
}

.record-list {
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--uc-line);
}

.record-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: var(--space-4);
  align-items: baseline;
  padding: 14px 0;
  border-bottom: 1px solid var(--uc-line);
}

.record-row dt {
  font-size: 9px;
  letter-spacing: 0.16em;
}

.record-row dd {
  margin: 0;
  font-size: 14px;
  color: var(--uc-ink-2);
  font-variant-numeric: tabular-nums;
  word-break: break-word;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .portrait {
    max-width: 280px;
  }
}

@media (max-width: 520px) {
  .field-display {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .record-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 4px;
  }
}
</style>
