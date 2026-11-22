<template>
  <div id="addPicturePage" class="aw-page" :class="{ 'is-light': isLight }">
    <!-- 氛围层 -->
    <EditorialAtmosphere variant="award" :light="isLight" />

    <div class="aw-shell ap-shell">
      <!-- 顶部信息条 -->
      <header class="ap-bar">
        <button class="aw-back" type="button" @click="goBack">
          <ArrowLeft :size="14" :stroke-width="1.8" />
          <span>返回</span>
        </button>

        <div class="ap-bar-meta">
          <span class="aw-mono">{{ isEdit ? 'EDIT MODE' : 'UPLOAD MODE' }}</span>
          <span class="ap-bar-dot" aria-hidden="true"></span>
          <span class="aw-mono header-space ap-bar-space">{{ spaceName || '未选择空间' }}</span>
        </div>
      </header>

      <!-- 引言 -->
      <section class="ap-intro">
        <div class="ap-intro-main">
          <p class="aw-kicker">{{ isEdit ? 'REVISE — 校对作品' : 'NEW WORK — 新作品' }}</p>
          <h1 class="aw-display ap-title">
            {{ isEdit ? '把作品，' : '让作品，' }}<br />
            <span class="aw-display-quiet">{{ isEdit ? '再校对一次。' : '找到它的位置。' }}</span>
          </h1>
        </div>

        <div class="ap-intro-side">
          <p class="aw-lede">
            一张图片进入 VisionSpace
            之前，先给它一个名字、一段来处。颜色与情绪会被记住，下一次检索就从这里开始。
          </p>
          <dl class="ap-facts">
            <div>
              <dt class="aw-mono">FORMAT</dt>
              <dd>JPG / PNG</dd>
            </div>
            <div>
              <dt class="aw-mono">LIMIT</dt>
              <dd>≤ 2 MB</dd>
            </div>
            <div>
              <dt class="aw-mono">SPACE</dt>
              <dd class="ap-facts-space">{{ spaceName || '—' }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <!-- 上传方式 -->
      <div class="ap-modes" role="tablist" aria-label="上传方式">
        <button
          type="button"
          role="tab"
          class="ap-mode"
          :class="{ active: uploadType === 'file' }"
          :aria-selected="uploadType === 'file'"
          @click="uploadType = 'file'"
        >
          <span class="ap-mode-no">01</span>
          <span>本地文件</span>
        </button>
        <button
          type="button"
          role="tab"
          class="ap-mode"
          :class="{ active: uploadType === 'url' }"
          :aria-selected="uploadType === 'url'"
          @click="uploadType = 'url'"
        >
          <span class="ap-mode-no">02</span>
          <span>图片链接</span>
        </button>
        <span class="ap-modes-note aw-mono">{{
          uploadType === 'file' ? 'FROM DEVICE' : 'FROM URL'
        }}</span>
      </div>

      <!-- 显影台 + 作品标签 -->
      <div class="ap-stage" :class="{ 'has-image': !!picture }">
        <section class="ap-table-wrap" aria-label="图片上传区">
          <div
            class="ap-table"
            :class="{ 'is-filled': !!picture, 'is-dragover': isDragOver }"
            @dragover.prevent="onDragOver"
            @dragleave="onDragLeave"
            @drop="onDrop"
          >
            <span class="ap-corner c-tl" aria-hidden="true"></span>
            <span class="ap-corner c-tr" aria-hidden="true"></span>
            <span class="ap-corner c-bl" aria-hidden="true"></span>
            <span class="ap-corner c-br" aria-hidden="true"></span>

            <div class="ap-table-inner">
              <PictureUpload
                v-if="uploadType === 'file' && !picture"
                :picture="picture"
                :spaceId="spaceId"
                :on-success="onSuccess"
                class="ap-upload"
              />
              <UrlPictureUpload
                v-else-if="uploadType === 'url' && !picture"
                :picture="picture"
                :spaceId="spaceId"
                :on-success="onSuccess"
                class="ap-upload"
              />

              <figure v-if="picture" class="ap-print">
                <img
                  :src="contentSrc(picture.url)"
                  :alt="picture.name || '作品预览'"
                  class="ap-print-img"
                />
                <button type="button" class="ap-replace" @click="changeImage">
                  <RefreshCw :size="14" :stroke-width="1.8" />
                  <span>更换图片</span>
                </button>
              </figure>
            </div>
          </div>

          <div class="ap-table-foot">
            <div class="ap-foot-meta">
              <span class="aw-mono ap-foot-name">LIGHT TABLE / 显影台</span>
              <span v-if="picture" class="aw-mono ap-print-meta">
                <span>{{ picture.picWidth }} × {{ picture.picHeight }}</span>
                <i class="ap-sep" aria-hidden="true">/</i>
                <span>{{ (picture.picFormat || 'IMG').toUpperCase() }}</span>
                <i class="ap-sep" aria-hidden="true">/</i>
                <span>{{ formatSize(picture.picSize || 0) }}</span>
              </span>
              <span v-else class="aw-mono ap-print-meta">NO FILE — 等待一张图片</span>
            </div>

            <div v-if="picture" class="ap-quick">
              <button type="button" class="ap-quick-btn" title="裁剪编辑" @click="doEditPicture">
                <Crop :size="15" :stroke-width="1.7" />
                <span>裁剪</span>
              </button>
              <button type="button" class="ap-quick-btn" title="AI 扩图" @click="doImagePainting">
                <Scan :size="15" :stroke-width="1.7" />
                <span>AI 扩图</span>
              </button>
            </div>
          </div>
        </section>

        <Transition name="ap-form">
          <aside v-if="picture" class="ap-label" aria-labelledby="ap-label-title">
            <header class="ap-label-head">
              <span class="aw-mono">WALL LABEL / 作品标签</span>
              <h2 id="ap-label-title" class="ap-label-title">作品信息</h2>
              <p class="ap-label-sub">名字与描述会和图像一起进入检索。</p>
            </header>

            <form class="ap-form" @submit.prevent="handSubmit">
              <div class="ap-field">
                <div class="ap-field-head">
                  <label class="ap-field-label" for="ap-name">
                    <span class="ap-field-no">01</span>
                    作品名称
                  </label>
                  <span class="aw-mono ap-field-hint">REQUIRED</span>
                </div>
                <input
                  id="ap-name"
                  v-model="pictureForm.name"
                  type="text"
                  class="ap-input"
                  placeholder="给它一个名字"
                  autocomplete="off"
                />
              </div>

              <div class="ap-field">
                <div class="ap-field-head">
                  <label class="ap-field-label" for="ap-introduction">
                    <span class="ap-field-no">02</span>
                    作品简介
                  </label>
                  <span class="aw-mono ap-field-hint">OPTIONAL</span>
                </div>
                <textarea
                  id="ap-introduction"
                  v-model="pictureForm.introduction"
                  class="ap-input ap-textarea"
                  placeholder="这张图片从哪里来，想留下什么？"
                  rows="3"
                ></textarea>
              </div>

              <div class="ap-field-row">
                <div class="ap-field">
                  <div class="ap-field-head">
                    <label class="ap-field-label" for="ap-category">
                      <span class="ap-field-no">03</span>
                      分类
                    </label>
                  </div>
                  <input
                    id="ap-category"
                    v-model="pictureForm.category"
                    type="text"
                    class="ap-input"
                    placeholder="选择或输入"
                    list="categoryList"
                    autocomplete="off"
                  />
                  <datalist id="categoryList">
                    <option v-for="cat in categoryOptions" :key="cat.value" :value="cat.value" />
                  </datalist>
                </div>

                <div class="ap-field">
                  <div class="ap-field-head">
                    <label class="ap-field-label" for="ap-tags">
                      <span class="ap-field-no">04</span>
                      标签
                    </label>
                  </div>
                  <input
                    id="ap-tags"
                    v-model="tagInput"
                    type="text"
                    class="ap-input"
                    placeholder="输入后回车"
                    autocomplete="off"
                    @keydown.enter.prevent="addTag"
                  />
                </div>
              </div>

              <div v-if="pictureForm.tags?.length" class="ap-tags">
                <span v-for="tag in pictureForm.tags" :key="tag" class="ap-tag">
                  {{ tag }}
                  <button
                    type="button"
                    class="ap-tag-remove"
                    :aria-label="`移除标签 ${tag}`"
                    @click="removeTag(tag)"
                  >
                    <X :size="11" :stroke-width="2" />
                  </button>
                </span>
              </div>

              <div class="ap-actions">
                <button
                  type="submit"
                  class="aw-btn aw-btn--primary ap-submit"
                  :disabled="!pictureForm.name?.trim()"
                >
                  <span>{{ isEdit ? '保存修改' : '创建作品' }}</span>
                  <ArrowRight :size="17" :stroke-width="1.8" />
                </button>
                <button type="button" class="aw-btn aw-btn--quiet ap-cancel" @click="goBack">
                  取消
                </button>
              </div>
            </form>
          </aside>
        </Transition>
      </div>
    </div>

    <!-- 图片编辑弹窗 -->
    <ImageCropper
      ref="imageCropperRef"
      :imageUrl="contentSrc(picture?.url)"
      :picture="picture"
      :on-success="onCropperSuccess"
      :space="space"
      :spaceId="spaceId"
    />

    <!-- AI 扩图弹窗 -->
    <ImageOutPainting
      ref="imageOutPaintingRef"
      :picture="picture"
      :spaceId="spaceId"
      :space="space"
      :onSuccess="onImageOutPaintingSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import EditorialAtmosphere from '@/components/EditorialAtmosphere.vue'
import PictureUpload from '@/components/PictureUpload.vue'
import UrlPictureUpload from '@/components/UrlPictureUpload.vue'
import ImageCropper from '@/components/ImageCropper.vue'
import ImageOutPainting from '@/components/ImageOutPainting.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowLeft, ArrowRight, Crop, RefreshCw, Scan, X } from 'lucide-vue-next'
import {
  editPictureUsingPost,
  getPictureVoByIdUsingGet,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { useRoute, useRouter } from 'vue-router'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { contentSrc, formatSize, toId } from '@/utils'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const route = useRoute()
const { currentTheme } = useTheme()

/* 两套主题共用一套版式，只换纸面与墨色 */
const isLight = computed(() => currentTheme.value === 'pop')
const isEdit = computed(() => !!route.query.id)

// Refs
const imageCropperRef = ref()
const imageOutPaintingRef = ref()

// 上传方式
const uploadType = ref<'file' | 'url'>('file')
const picture = ref<API.PictureVO>()
const pictureForm = reactive<API.PictureEditRequest>({})
const space = ref<API.SpaceVO>()
const isDragOver = ref(false)

// 标签输入
const tagInput = ref('')

// 空间ID
const spaceId = computed(() => toId(route.query?.spaceId as string | undefined))
const spaceName = ref<string>('')

// 上传成功
const onSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
  pictureForm.name = newPicture.name || ''
  pictureForm.introduction = newPicture.introduction || ''
  pictureForm.category = newPicture.category || ''
  pictureForm.tags = newPicture.tags || []
  isDragOver.value = false
}

// 裁剪成功
const onCropperSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// AI 扩图成功
const onImageOutPaintingSuccess = (newPicture: API.PictureVO) => {
  picture.value = newPicture
}

// 添加标签
const addTag = () => {
  const tag = tagInput.value.trim()
  if (tag && !pictureForm.tags?.includes(tag)) {
    if (!pictureForm.tags) pictureForm.tags = []
    pictureForm.tags.push(tag)
  }
  tagInput.value = ''
}

// 移除标签
const removeTag = (tag: string) => {
  pictureForm.tags = pictureForm.tags?.filter((t) => t !== tag)
}

// 更换图片
const changeImage = () => {
  picture.value = undefined
  pictureForm.name = ''
  pictureForm.introduction = ''
  pictureForm.category = ''
  pictureForm.tags = []
}

// 拖拽反馈（真正的投放由上传组件处理）
const onDragOver = () => {
  isDragOver.value = true
}

const onDragLeave = (event: DragEvent) => {
  const current = event.currentTarget as HTMLElement | null
  const next = event.relatedTarget as Node | null
  if (!current || !next || !current.contains(next)) {
    isDragOver.value = false
  }
}

const onDrop = () => {
  isDragOver.value = false
}

// 提交表单
const handSubmit = async () => {
  const pictureId = picture.value?.id
  if (!pictureId) {
    message.error('请先上传图片')
    return
  }
  if (!pictureForm.name?.trim()) {
    message.warning('请先给作品起个名字')
    return
  }
  try {
    const res = await editPictureUsingPost({
      id: pictureId,
      ...pictureForm,
      name: pictureForm.name.trim(),
    })
    if (res.data.code === 0 && res.data.data) {
      message.success(isEdit.value ? '修改成功' : '创建成功')
      router.push(`/picture/${pictureId}`)
    } else {
      message.error('操作失败：' + res.data.message)
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// 分类和标签选项
const categoryOptions = ref<any[]>([])

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => ({
        value: data,
        label: data,
      }))
    }
  } catch (error) {
    console.error('获取标签分类列表失败')
  }
}

// 获取已有图片数据
const getOldPicture = async () => {
  const id = route.query.id
  if (id) {
    try {
      const res = await getPictureVoByIdUsingGet({ id: toId(id as string) as number })
      if (res.data.code === 0 && res.data.data) {
        picture.value = res.data.data
        pictureForm.name = picture.value.name || ''
        pictureForm.introduction = picture.value.introduction || ''
        pictureForm.category = picture.value.category || ''
        pictureForm.tags = picture.value.tags || []
      }
    } catch (error) {
      message.error('获取图片信息失败')
    }
  }
}

// 获取空间信息
const getOldSpace = async () => {
  if (spaceId.value) {
    try {
      const res = await getSpaceVoByIdUsingGet({ id: spaceId.value as number })
      if (res.data.code === 0 && res.data.data) {
        space.value = res.data.data
        spaceName.value = res.data.data.spaceName || String(spaceId.value)
      }
    } catch (error) {
      spaceName.value = String(spaceId.value)
    }
  }
}

// 图片编辑
const doEditPicture = () => {
  imageCropperRef.value?.openModal()
}

// AI 扩图
const doImagePainting = () => {
  imageOutPaintingRef.value?.openModal()
}

// 返回
const goBack = () => {
  router.back()
}

onMounted(() => {
  getTagCategoryOptions()
  getOldPicture()
  getOldSpace()
})
</script>

<style scoped lang="less">
#addPicturePage {
  padding-bottom: clamp(64px, 10vh, 120px);
}

.ap-shell {
  padding-top: 0;
}

/* ========== 顶部信息条 ========== */
.ap-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 26px 0 18px;
  border-bottom: 1px solid var(--aw-line);
}

.ap-bar-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.ap-bar-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--aw-accent);
  box-shadow: 0 0 0 4px var(--aw-accent-soft);
  flex-shrink: 0;
}

.ap-bar-space {
  color: var(--aw-ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 34ch;
}

/* ========== 引言 ========== */
.ap-intro {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: clamp(32px, 6vw, 96px);
  align-items: end;
  padding: clamp(44px, 8vh, 92px) 0 clamp(36px, 6vh, 64px);
}

.ap-title {
  margin-top: 22px;
}

.ap-intro-side {
  display: flex;
  flex-direction: column;
  gap: 30px;
  padding-bottom: 4px;
}

.ap-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, auto));
  justify-content: start;
  gap: clamp(20px, 3vw, 44px);
  margin: 0;
  padding-top: 18px;
  border-top: 1px solid var(--aw-line);
}

.ap-facts div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.ap-facts dt {
  margin: 0;
}

.ap-facts dd {
  margin: 0;
  font-family: var(--aw-serif);
  font-size: 15px;
  font-weight: 500;
  color: var(--aw-ink);
  white-space: nowrap;
}

.ap-facts-space {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 18ch;
}

/* ========== 上传方式 ========== */
.ap-modes {
  display: flex;
  align-items: center;
  gap: clamp(20px, 3vw, 40px);
  border-bottom: 1px solid var(--aw-line);
  margin-bottom: clamp(26px, 4vh, 44px);
}

.ap-mode {
  position: relative;
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 0;
  border: 0;
  background: none;
  color: var(--aw-ink-3);
  font-family: var(--aw-sans);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition: color 0.35s var(--aw-ease);
}

.ap-mode::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 1px;
  background: var(--aw-ink);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.5s var(--aw-ease);
}

.ap-mode:hover {
  color: var(--aw-ink-2);
}

.ap-mode.active {
  color: var(--aw-ink);
}

.ap-mode.active::after {
  transform: scaleX(1);
}

.ap-mode:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 4px;
}

.ap-mode-no {
  font-family: var(--aw-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--aw-accent);
}

.ap-modes-note {
  margin-left: auto;
}

/* ========== 舞台 ========== */
.ap-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: clamp(32px, 5vw, 72px);
  align-items: start;
  animation: ap-stage-in 0.9s var(--aw-ease) 0.1s both;
}

.ap-stage.has-image {
  grid-template-columns: minmax(0, 1.42fr) minmax(350px, 0.82fr);
}

@keyframes ap-stage-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

/* ========== 显影台 ========== */
.ap-table-wrap {
  min-width: 0;
}

.ap-table {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: clamp(380px, 56vh, 640px);
  padding: clamp(22px, 3vw, 44px);
  border: 1px solid var(--aw-line);
  background: linear-gradient(
    180deg,
    rgba(242, 238, 230, 0.038) 0%,
    rgba(242, 238, 230, 0.01) 100%
  );
  box-shadow: inset 0 0 90px rgba(185, 166, 255, 0.05);
  transition:
    border-color 0.5s var(--aw-ease),
    box-shadow 0.5s var(--aw-ease);
}

.ap-table.is-dragover {
  border-color: var(--aw-accent);
  box-shadow:
    inset 0 0 120px rgba(185, 166, 255, 0.14),
    0 0 0 1px var(--aw-accent);
}

.ap-table.is-filled {
  min-height: 0;
  padding: clamp(16px, 2.4vw, 32px);
}

.ap-corner {
  position: absolute;
  width: 18px;
  height: 18px;
  border-style: solid;
  border-color: var(--aw-line-2);
  transition:
    border-color 0.5s var(--aw-ease),
    transform 0.5s var(--aw-ease);
  pointer-events: none;
}

.c-tl {
  top: 10px;
  left: 10px;
  border-width: 1px 0 0 1px;
}

.c-tr {
  top: 10px;
  right: 10px;
  border-width: 1px 1px 0 0;
}

.c-bl {
  bottom: 10px;
  left: 10px;
  border-width: 0 0 1px 1px;
}

.c-br {
  bottom: 10px;
  right: 10px;
  border-width: 0 1px 1px 0;
}

.ap-table:hover .ap-corner,
.ap-table.is-dragover .ap-corner {
  border-color: var(--aw-accent);
}

.ap-table:hover .c-tl {
  transform: translate(-2px, -2px);
}

.ap-table:hover .c-tr {
  transform: translate(2px, -2px);
}

.ap-table:hover .c-bl {
  transform: translate(-2px, 2px);
}

.ap-table:hover .c-br {
  transform: translate(2px, 2px);
}

.ap-table-inner {
  width: 100%;
  min-height: clamp(300px, 44vh, 520px);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 上传组件铺满显影台 */
.ap-upload {
  width: 100%;
  height: 100%;
}

.ap-upload :deep(.ant-upload-wrapper),
.ap-upload :deep(.ant-upload-select) {
  width: 100% !important;
  height: 100% !important;
}

.ap-upload :deep(.ant-upload) {
  width: 100% !important;
  height: 100% !important;
  min-height: clamp(300px, 44vh, 520px) !important;
  min-width: 0 !important;
  max-width: none !important;
  margin: 0 !important;
  border: 0 !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  transform: none !important;
}

.ap-upload :deep(.ant-upload:hover) {
  border: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  transform: none !important;
}

.ap-print {
  position: relative;
  margin: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: ap-print-in 0.9s var(--aw-ease) both;
}

@keyframes ap-print-in {
  from {
    opacity: 0;
    transform: scale(0.985);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.ap-print-img {
  display: block;
  max-width: 100%;
  max-height: clamp(340px, 60vh, 620px);
  width: auto;
  height: auto;
  object-fit: contain;
  box-shadow: 0 34px 70px -38px rgba(0, 0, 0, 0.75);
}

.ap-replace {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border: 1px solid var(--aw-line-2);
  border-radius: 999px;
  background: var(--aw-bg);
  color: var(--aw-ink-2);
  font-family: var(--aw-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
  opacity: 0.72;
  transition:
    opacity 0.35s var(--aw-ease),
    color 0.35s var(--aw-ease),
    border-color 0.35s var(--aw-ease),
    background-color 0.35s var(--aw-ease);
}

.ap-replace:hover {
  opacity: 1;
  color: var(--aw-ink);
  border-color: var(--aw-accent);
  background: var(--aw-accent-soft);
}

.ap-replace:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 3px;
}

.ap-table-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 14px 2px 0;
}

.ap-foot-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  min-width: 0;
}

.ap-foot-name {
  color: var(--aw-ink-2);
}

.ap-print-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ap-sep {
  font-style: normal;
  color: var(--aw-line-2);
}

.ap-quick {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ap-quick-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border: 1px solid var(--aw-line);
  border-radius: 999px;
  background: transparent;
  color: var(--aw-ink-2);
  font-family: var(--aw-sans);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color 0.35s var(--aw-ease),
    border-color 0.35s var(--aw-ease),
    background-color 0.35s var(--aw-ease),
    transform 0.35s var(--aw-ease);
}

.ap-quick-btn svg {
  transition: transform 0.4s var(--aw-ease);
}

.ap-quick-btn:hover {
  color: var(--aw-ink);
  border-color: var(--aw-line-2);
  background: var(--aw-accent-soft);
  transform: translateY(-1px);
}

.ap-quick-btn:hover svg {
  transform: rotate(-8deg) scale(1.06);
}

.ap-quick-btn:focus-visible {
  outline: 2px solid var(--aw-accent);
  outline-offset: 3px;
}

/* ========== 作品标签（表单） ========== */
.ap-label {
  position: sticky;
  top: 28px;
  align-self: start;
  padding: clamp(24px, 2.8vw, 36px);
  border: 1px solid var(--aw-line);
  border-left: 1px solid var(--aw-line-2);
  background: linear-gradient(
    180deg,
    rgba(242, 238, 230, 0.045) 0%,
    rgba(242, 238, 230, 0.012) 100%
  );
}

.ap-label-head {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--aw-line);
}

.ap-label-title {
  margin: 0;
  font-family: var(--aw-serif);
  font-size: clamp(22px, 2.1vw, 28px);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--aw-ink);
}

.ap-label-sub {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--aw-ink-3);
}

.ap-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-top: 24px;
}

.ap-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.ap-field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.ap-field-label {
  display: inline-flex;
  align-items: baseline;
  gap: 9px;
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.03em;
  color: var(--aw-ink-2);
  cursor: pointer;
}

.ap-field-no {
  font-family: var(--aw-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--aw-accent);
}

.ap-field-hint {
  font-size: 9px;
  letter-spacing: 0.14em;
  opacity: 0.75;
}

.ap-input {
  width: 100%;
  padding: 11px 0;
  border: 0;
  border-bottom: 1px solid var(--aw-line);
  border-radius: 0;
  background: transparent;
  color: var(--aw-ink);
  font-family: var(--aw-sans);
  font-size: 15.5px;
  line-height: 1.5;
  outline: none;
  transition:
    border-color 0.4s var(--aw-ease),
    background-color 0.4s var(--aw-ease);
}

.ap-input::placeholder {
  color: var(--aw-ink-3);
}

.ap-input:hover {
  border-bottom-color: var(--aw-line-2);
}

.ap-input:focus {
  border-bottom-color: var(--aw-accent);
}

.ap-textarea {
  min-height: 84px;
  resize: none;
  font-family: var(--aw-sans);
}

.ap-field-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 22px;
}

.ap-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: -8px;
}

.ap-tag {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px;
  border: 1px solid var(--aw-line-2);
  border-radius: 999px;
  color: var(--aw-ink-2);
  font-family: var(--aw-mono);
  font-size: 10.5px;
  letter-spacing: 0.06em;
  animation: ap-tag-in 0.35s var(--aw-ease) both;
}

@keyframes ap-tag-in {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.ap-tag-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 15px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--aw-ink-3);
  cursor: pointer;
  transition:
    color 0.3s var(--aw-ease),
    transform 0.3s var(--aw-ease),
    background-color 0.3s var(--aw-ease);
}

.ap-tag-remove:hover {
  color: var(--aw-danger);
  background: var(--aw-accent-soft);
  transform: rotate(90deg);
}

.ap-actions {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-top: 6px;
}

.ap-submit {
  flex: 1;
}

.ap-cancel {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--aw-ink-3);
}

/* 表单入场 */
.ap-form-enter-active {
  transition:
    opacity 0.7s var(--aw-ease),
    transform 0.7s var(--aw-ease);
}

.ap-form-leave-active {
  transition:
    opacity 0.25s ease,
    transform 0.25s ease;
}

.ap-form-enter-from {
  opacity: 0;
  transform: translateX(26px);
}

.ap-form-leave-to {
  opacity: 0;
  transform: translateX(14px);
}

/* ========== 浅色主题 ========== */
#addPicturePage.is-light .ap-table {
  background: linear-gradient(180deg, #ffffff 0%, rgba(255, 255, 255, 0.62) 100%);
  box-shadow: inset 0 0 80px rgba(168, 85, 247, 0.05);
}

#addPicturePage.is-light .ap-table.is-dragover {
  box-shadow:
    inset 0 0 110px rgba(168, 85, 247, 0.1),
    0 0 0 1px var(--aw-accent);
}

#addPicturePage.is-light .ap-label {
  background: linear-gradient(180deg, #ffffff 0%, rgba(255, 255, 255, 0.66) 100%);
}

#addPicturePage.is-light .ap-print-img {
  box-shadow: 0 30px 60px -36px rgba(76, 29, 149, 0.34);
}

#addPicturePage.is-light .ap-replace {
  background: rgba(255, 255, 255, 0.9);
}

/* ========== 响应式 ========== */
@media (max-width: 1080px) {
  .ap-intro {
    grid-template-columns: 1fr;
    gap: 28px;
    align-items: start;
  }

  .ap-intro-side {
    max-width: 640px;
  }

  .ap-stage.has-image {
    grid-template-columns: 1fr;
  }

  .ap-label {
    position: static;
  }
}

@media (max-width: 820px) {
  .ap-title {
    font-size: clamp(30px, 6.4vw, 44px);
  }

  .ap-table {
    min-height: 380px;
  }

  .ap-table-inner,
  .ap-upload :deep(.ant-upload) {
    min-height: 300px !important;
  }

  .ap-print-img {
    max-height: 58vh;
  }
}

@media (max-width: 640px) {
  .ap-bar {
    padding-top: 18px;
  }

  .ap-bar-meta .aw-mono:first-child {
    display: none;
  }

  .ap-bar-space {
    max-width: 24ch;
  }

  .ap-modes-note {
    display: none;
  }

  .ap-facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 16px;
  }

  .ap-field-row {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .ap-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .ap-cancel {
    align-self: center;
  }

  .ap-table-foot {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }

  .ap-foot-meta {
    gap: 10px;
  }
}

@media (max-width: 480px) {
  .ap-table {
    min-height: 320px;
    padding: 16px;
  }

  .ap-table-inner,
  .ap-upload :deep(.ant-upload) {
    min-height: 260px !important;
  }

  .ap-print-meta {
    font-size: 10px;
  }

  .ap-replace span {
    display: none;
  }

  .ap-replace {
    padding: 9px;
  }
}
</style>
