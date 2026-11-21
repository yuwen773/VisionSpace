<template>
  <div id="pictureDetailPage" :style="pageVars">
    <div class="wall" aria-hidden="true">
      <div class="wall-wash"></div>
      <div class="wall-grain"></div>
    </div>

    <div class="page-shell">
      <!-- 顶部工具条：返回 + 作品操作 -->
      <nav class="page-rail" aria-label="作品操作">
        <button type="button" class="rail-back" @click="goBack">
          <ArrowLeft :size="18" :stroke-width="1.75" aria-hidden="true" />
          <span>返回</span>
        </button>

        <div class="rail-tools">
          <button
            type="button"
            class="rail-tool"
            title="分享作品"
            aria-label="分享作品"
            @click="doShare"
          >
            <Share2 :size="18" :stroke-width="1.75" aria-hidden="true" />
          </button>
          <button
            type="button"
            class="rail-tool rail-tool-download"
            title="下载原图"
            aria-label="下载原图"
            @click="doDownload"
          >
            <Download :size="18" :stroke-width="1.75" aria-hidden="true" />
          </button>
          <button
            v-if="canEdit"
            type="button"
            class="rail-tool"
            title="编辑作品"
            aria-label="编辑作品"
            @click="doEdit"
          >
            <Pencil :size="18" :stroke-width="1.75" aria-hidden="true" />
          </button>
          <button
            v-if="canDelete"
            type="button"
            class="rail-tool rail-tool-danger"
            title="删除作品"
            aria-label="删除作品"
            @click="doDelete"
          >
            <Trash2 :size="18" :stroke-width="1.75" aria-hidden="true" />
          </button>
        </div>
      </nav>

      <main v-if="!loadError" class="detail-grid">
        <!-- 作品：让画面占据主位 -->
        <section class="stage" aria-label="作品画面">
          <button
            v-if="!imageError"
            type="button"
            class="plate"
            :class="{ 'is-ready': imageLoaded, 'cursor-on': cursorOn }"
            :aria-label="`全屏查看《${picture.name || '未命名作品'}》`"
            @click="toggleFullscreen"
            @pointermove="onPlateMove"
            @pointerleave="onPlateLeave"
          >
            <span class="plate-mat">
              <span v-if="!imageLoaded" class="plate-skeleton" aria-hidden="true">
                <span class="skeleton-shimmer"></span>
              </span>

              <img
                v-if="imageSrc"
                :key="imageKey"
                :src="imageSrc"
                :alt="picture.name || '作品预览'"
                class="plate-image"
                :class="{ visible: imageLoaded }"
                draggable="false"
                @load="onImageLoad"
                @error="onImageError"
              />

              <span
                v-if="imageLoaded"
                class="plate-hint"
                :class="{ hidden: cursorOn }"
                aria-hidden="true"
              >
                <Maximize2 :size="14" :stroke-width="2" />
                点击放大
              </span>
            </span>

            <span
              ref="cursorEl"
              v-if="imageLoaded"
              class="plate-cursor"
              :class="{ visible: cursorOn }"
              aria-hidden="true"
            >
              <ArrowUpRight :size="22" :stroke-width="1.75" />
            </span>
          </button>

          <div v-else class="plate-fallback" role="alert">
            <ImageOff :size="28" :stroke-width="1.5" aria-hidden="true" />
            <span class="fallback-title">画面暂时无法加载</span>
            <span class="fallback-hint">文件可能已移动，或当前网络无法取回内容</span>
            <button type="button" class="fallback-retry" @click="retryImage">
              <RefreshCw :size="14" :stroke-width="2" aria-hidden="true" />
              重新加载
            </button>
          </div>

          <div class="stage-rule" :class="{ visible: imageLoaded }" aria-hidden="true"></div>

          <dl class="spec-strip" :class="{ visible: imageLoaded }">
            <div v-if="picture.picWidth && picture.picHeight" class="spec">
              <dt>尺寸</dt>
              <dd>{{ picture.picWidth }} × {{ picture.picHeight }}</dd>
            </div>
            <div v-if="ratioLabel" class="spec">
              <dt>比例</dt>
              <dd>{{ ratioLabel }}</dd>
            </div>
            <div v-if="picture.picFormat" class="spec">
              <dt>格式</dt>
              <dd>{{ picture.picFormat.toUpperCase() }}</dd>
            </div>
            <div v-if="picture.picSize" class="spec">
              <dt>体积</dt>
              <dd>{{ formatSize(picture.picSize) }}</dd>
            </div>
          </dl>
        </section>

        <!-- 说明牌：标题、出处、参数、动作 -->
        <aside class="placard" :class="{ ready: placardReady }">
          <template v-if="loading">
            <div class="sk-line sk-title" aria-hidden="true"></div>
            <div class="sk-line sk-title short" aria-hidden="true"></div>
            <div class="sk-line sk-text" aria-hidden="true"></div>
            <div class="sk-line sk-text short" aria-hidden="true"></div>
            <div class="sk-byline" aria-hidden="true">
              <span class="sk-avatar"></span>
              <span class="sk-line sk-name"></span>
            </div>
            <span class="visually-hidden" role="status">正在加载作品信息</span>
          </template>

          <template v-else>
            <header class="placard-head reveal" style="--i: 0">
              <h1 class="work-title">{{ picture.name || '未命名作品' }}</h1>
              <p v-if="picture.introduction" class="work-intro">{{ picture.introduction }}</p>
            </header>

            <div class="byline reveal" style="--i: 1">
              <a-avatar
                :src="picture.user?.userAvatar"
                :size="44"
                class="byline-avatar"
                :alt="picture.user?.userName || '作者头像'"
              />
              <div class="byline-text">
                <span class="byline-name">{{ picture.user?.userName || '未知作者' }}</span>
                <span v-if="picture.user?.userAccount" class="byline-handle"
                  >@{{ picture.user.userAccount }}</span
                >
              </div>
            </div>

            <dl v-if="hasFacts" class="facts reveal" style="--i: 2">
              <div v-if="picture.category" class="fact">
                <dt>分类</dt>
                <dd>{{ picture.category }}</dd>
              </div>

              <div v-if="artHex" class="fact">
                <dt>主色</dt>
                <dd>
                  <button
                    type="button"
                    class="color-copy"
                    :aria-label="`复制主色色值 ${artHex}`"
                    @click="copyColor"
                  >
                    <span class="color-swatch" :style="{ backgroundColor: artHex }"></span>
                    <span class="color-hex">{{ artHex }}</span>
                    <Check
                      v-if="copyState === 'copied'"
                      :size="14"
                      :stroke-width="2"
                      class="copy-icon copied"
                      aria-hidden="true"
                    />
                    <Copy
                      v-else
                      :size="14"
                      :stroke-width="1.75"
                      class="copy-icon"
                      aria-hidden="true"
                    />
                  </button>
                </dd>
              </div>

              <div v-if="picture.tags?.length" class="fact">
                <dt>标签</dt>
                <dd class="tag-list">
                  <span v-for="tag in picture.tags" :key="tag" class="tag-chip">{{ tag }}</span>
                </dd>
              </div>
            </dl>

            <div class="actions reveal" style="--i: 3">
              <button
                type="button"
                class="btn btn-primary"
                :disabled="!imageLoaded"
                @click="toggleFullscreen"
              >
                <Maximize2 :size="18" :stroke-width="1.9" aria-hidden="true" />
                全屏预览
              </button>
              <button type="button" class="btn btn-secondary" @click="doDownload">
                <Download :size="18" :stroke-width="1.9" aria-hidden="true" />
                下载原图
              </button>
            </div>

            <div class="placard-foot reveal" style="--i: 4">
              <PictureGrantPanel :picture-id="rawPictureId" />
            </div>
          </template>
        </aside>
      </main>

      <section v-else class="state-page" role="alert">
        <span class="state-icon">
          <ImageOff :size="30" :stroke-width="1.5" aria-hidden="true" />
        </span>
        <h1 class="state-title">作品信息没有取回来</h1>
        <p class="state-hint">{{ loadError }}</p>
        <div class="state-actions">
          <button type="button" class="btn btn-primary" @click="fetchDetailPicture">
            <RefreshCw :size="17" :stroke-width="2" aria-hidden="true" />
            重新加载
          </button>
          <button type="button" class="btn btn-secondary" @click="goBack">
            <ArrowLeft :size="17" :stroke-width="2" aria-hidden="true" />
            返回画廊
          </button>
        </div>
      </section>
    </div>

    <!-- 移动端：把主要动作固定在拇指够得着的位置 -->
    <div v-if="!loading && !loadError" class="mobile-actions">
      <button
        type="button"
        class="btn btn-primary"
        :disabled="!imageLoaded"
        @click="toggleFullscreen"
      >
        <Maximize2 :size="18" :stroke-width="1.9" aria-hidden="true" />
        全屏预览
      </button>
      <button type="button" class="btn btn-secondary" @click="doDownload" aria-label="下载原图">
        <Download :size="18" :stroke-width="1.9" aria-hidden="true" />
      </button>
    </div>

    <ImagePreview v-model:open="isFullscreen" :url="imageSrc" :alt="picture.name" />

    <ShareModal ref="shareModalRef" :link="shareLink" title="分享图片" />

    <span class="visually-hidden" aria-live="polite">{{
      copyState === 'copied' ? '主色色值已复制' : ''
    }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeft,
  ArrowUpRight,
  Check,
  Copy,
  Download,
  ImageOff,
  Maximize2,
  Pencil,
  RefreshCw,
  Share2,
  Trash2,
} from 'lucide-vue-next'
import { deletePictureUsingPost, getPictureVoByIdUsingGet } from '@/api/pictureController.ts'
import { contentSrc, downloadImage, formatSize, toId } from '@/utils'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import ShareModal from '@/components/ShareModal.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import PictureGrantPanel from '@/components/picture/PictureGrantPanel.vue'
import { usePictureAction } from '@/composables/usePictureAction'
import { ActionTypeEnum, ActionSourceEnum } from '@/api-custom/picture'

interface Props {
  id: number | string
}

const props = defineProps<Props>()
const pictureId = computed(() => toId(props.id))
// 授权面板按原始标识定位资源：图片标识是雪花值，超出 JavaScript 安全整数范围，
// 转成 number 会丢精度，服务端就找不到资源
const rawPictureId = computed(() =>
  props.id === undefined || props.id === null ? '' : String(props.id),
)
const router = useRouter()
const { report: reportAction } = usePictureAction()

// State
const picture = ref<API.PictureVO>({})
const loading = ref(true)
const loadError = ref('')
const imageLoaded = ref(false)
const imageError = ref(false)
const imageKey = ref(0)
const isFullscreen = ref(false)
const copyState = ref<'idle' | 'copied'>('idle')
let copyTimer: ReturnType<typeof setTimeout> | undefined

// 作品自身的颜色：墙面、投影与细线都从它取色，而不是写死品牌紫
const imageSrc = computed(() => contentSrc(picture.value.previewUrl || picture.value.url))
const artHex = computed(() => normalizeHex(picture.value.picColor))
const artRgb = computed(() => hexToRgb(artHex.value))
// 竖幅作品不该被拉成一条横幅：画框宽度按画面的长宽比收窄，说明条跟着画框走
const artRatio = computed(() => {
  const width = picture.value.picWidth
  const height = picture.value.picHeight
  if (!width || !height) return 1.5
  return Math.min(Math.max(width / height, 0.28), 3)
})
const pageVars = computed<Record<string, string>>(() => {
  const vars: Record<string, string> = { '--art-ratio': String(artRatio.value) }
  if (artRgb.value && artHex.value) {
    vars['--art'] = artHex.value
    vars['--art-rgb'] = artRgb.value.join(', ')
  }
  return vars
})

const ratioLabel = computed(() => {
  const width = picture.value.picWidth
  const height = picture.value.picHeight
  if (!width || !height) return ''
  const divisor = gcd(Math.round(width), Math.round(height))
  return `${Math.round(width / divisor)} : ${Math.round(height / divisor)}`
})

const hasFacts = computed(() =>
  Boolean(picture.value.category || artHex.value || picture.value.tags?.length),
)
const placardReady = computed(() => !loading.value && !loadError.value)

// Cursor badge: 只在指针设备上跟随，作为“可放大的画面”提示
const cursorEl = ref<HTMLElement>()
const cursorOn = ref(false)
let queuedPoint: { x: number; y: number } | null = null
let cursorFrame = 0

const paintCursor = () => {
  cursorFrame = 0
  if (!queuedPoint || !cursorEl.value) return
  cursorEl.value.style.transform = `translate3d(${queuedPoint.x}px, ${queuedPoint.y}px, 0)`
  queuedPoint = null
}

const onPlateMove = (event: PointerEvent) => {
  if (event.pointerType !== 'mouse' || imageError.value || !imageLoaded.value) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  cursorOn.value = true
  queuedPoint = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  if (!cursorFrame) cursorFrame = requestAnimationFrame(paintCursor)
}

const onPlateLeave = () => {
  cursorOn.value = false
}

// Fetch picture detail
const fetchDetailPicture = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getPictureVoByIdUsingGet({ id: pictureId.value as number })
    if (res.data.code === 0 && res.data.data) {
      imageLoaded.value = false
      imageError.value = false
      picture.value = res.data.data
      // 详情页浏览：与曝光、点击一样进入同一套事件通道
      if (pictureId.value !== undefined) {
        await reportAction(pictureId.value, ActionTypeEnum.VIEW, ActionSourceEnum.DETAIL)
      }
    } else {
      loadError.value = res.data.message || '获取图片详情失败'
    }
  } catch (error: any) {
    loadError.value = error?.message || '网络请求失败，请稍后再试'
  } finally {
    loading.value = false
  }
}

const onImageLoad = () => {
  imageLoaded.value = true
  imageError.value = false
}

const onImageError = () => {
  if (!imageSrc.value) return
  imageLoaded.value = false
  imageError.value = true
}

const retryImage = () => {
  imageError.value = false
  imageLoaded.value = false
  imageKey.value += 1
}

// Permission checks
const createPermissionChecker = (permission: string) => {
  return computed(() => (picture.value.permissionList ?? []).includes(permission))
}

const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// Share
const shareModalRef = ref()
const shareLink = ref<string>('')

const doShare = () => {
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${props.id}`
  shareModalRef.value?.openModal()
}

// Download
const doDownload = () => {
  downloadImage(picture.value.url)
}

// Edit
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: { id: pictureId.value, spaceId: picture.value.spaceId },
  })
}

// Delete
const doDelete = async () => {
  const id = pictureId.value
  if (id === undefined) return

  try {
    const res = await deletePictureUsingPost({ id })
    if (res.data.code === 0) {
      message.success('图片已删除')
      router.push({ name: 'home' })
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// Go back
const goBack = () => {
  router.back()
}

// Fullscreen
const toggleFullscreen = () => {
  if (!imageLoaded.value) return
  isFullscreen.value = !isFullscreen.value
}

const copyColor = async () => {
  if (!artHex.value) return
  try {
    await navigator.clipboard.writeText(artHex.value)
    copyState.value = 'copied'
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => (copyState.value = 'idle'), 1600)
  } catch {
    message.error('复制失败，请手动选择色值')
  }
}

function normalizeHex(input?: string): string {
  if (!input) return ''
  const raw = input.trim().replace(/^#/, '').replace(/^0x/i, '')
  if (!/^[0-9a-f]{6}$/i.test(raw)) return ''
  return `#${raw.toLowerCase()}`
}

function hexToRgb(hex: string): [number, number, number] | null {
  if (!hex) return null
  const value = hex.replace('#', '')
  return [
    parseInt(value.slice(0, 2), 16),
    parseInt(value.slice(2, 4), 16),
    parseInt(value.slice(4, 6), 16),
  ]
}

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b)
}

onMounted(() => {
  fetchDetailPicture()
})

onBeforeUnmount(() => {
  clearTimeout(copyTimer)
  if (cursorFrame) cancelAnimationFrame(cursorFrame)
})

watch(
  () => props.id,
  () => {
    imageLoaded.value = false
    imageError.value = false
    fetchDetailPicture()
  },
)
</script>

<style scoped lang="less">
@import url('https://fonts.googleapis.cn/css2?family=Noto+Serif+SC:wght@600;700&display=swap');

/* =========================================================
   作品详情：画面主导，说明牌退后，颜色从作品里长出来
   ========================================================= */

#pictureDetailPage {
  --ease-expo: cubic-bezier(0.16, 1, 0.3, 1);
  --stage-max: calc(100vh - 230px);
  --plate-width: min(100%, calc(var(--stage-max) * var(--art-ratio, 1.5)));

  /* 没有主色时的回退：紫漾主题取品牌紫 */
  --art: var(--color-primary, var(--color-accent-purple, #a855f7));
  --art-rgb: 168, 85, 247;

  /* 展墙本身就是作品颜色的延伸：整面墙都染上一点画面的色相 */
  --wall-high: 0.3;
  --wall-low: 0.15;
  --wall-flat: 0.07;
  --wall-floor: 0.15;

  position: relative;
  z-index: 1;
  min-height: 100vh;
  padding: clamp(4px, 1vw, 12px) 0 clamp(56px, 8vw, 96px);
  color: var(--text-primary);
  font-family: var(--font-body);
}

:global(.theme-aurora) #pictureDetailPage {
  --art: var(--color-primary-500, #2268f5);
  --art-rgb: 34, 104, 245;
  --art-soft: rgba(var(--art-rgb), 0.24);
  --art-wash: rgba(var(--art-rgb), 0.38);
  --plate-bg: color-mix(in srgb, var(--art) 16%, #070b14);
  --plate-shadow:
    0 44px 100px -40px rgba(var(--art-rgb), 0.62), 0 24px 60px -36px rgba(0, 0, 0, 0.9);
  --wall-high: 0.46;
  --wall-low: 0.26;
  --wall-flat: 0.13;
  --wall-floor: 0.24;
}

#pictureDetailPage {
  --art-soft: rgba(var(--art-rgb), 0.16);
  --art-wash: rgba(var(--art-rgb), 0.26);
  --plate-bg: color-mix(in srgb, var(--art) 8%, var(--bg-card));
  --plate-shadow:
    0 38px 84px -44px rgba(var(--art-rgb), 0.62), 0 18px 40px -28px rgba(30, 27, 75, 0.32);
}

/* ========== 墙面 ========== */
.wall {
  /* 墙面盖住整份文档，而不是只盖住首屏：滚动到说明牌底部时，
     房间的颜色也不该断成一条直线 */
  position: absolute;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
}

.wall-wash {
  position: absolute;
  inset: -12% -8%;
  background:
    radial-gradient(
      ellipse 68% 58% at 28% 6%,
      rgba(var(--art-rgb), var(--wall-high)) 0%,
      transparent 62%
    ),
    radial-gradient(
      ellipse 58% 54% at 82% 68%,
      rgba(var(--art-rgb), var(--wall-low)) 0%,
      transparent 66%
    ),
    linear-gradient(
      180deg,
      rgba(var(--art-rgb), var(--wall-flat)) 0%,
      rgba(var(--art-rgb), var(--wall-floor)) 100%
    );
  filter: blur(38px) saturate(112%);
}

.wall-grain {
  position: absolute;
  inset: 0;
  opacity: 0.05;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='180'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3'/%3E%3C/filter%3E%3Crect width='180' height='180' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 180px 180px;
}

/* ========== 页面骨架 ========== */
.page-shell {
  width: 100%;
  max-width: 1480px;
  margin: 0 auto;
  padding: 0 clamp(4px, 2.4vw, 32px);
}

.page-rail {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 44px;
  padding-bottom: 12px;
  margin-bottom: clamp(20px, 3vw, 36px);
  border-bottom: 1px solid var(--border-subtle);
}

.rail-back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 2px;
  border: 0;
  background: none;
  color: var(--text-secondary);
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  border-radius: 8px;
  transition:
    color 0.25s ease,
    gap 0.35s var(--ease-expo);

  &:hover {
    color: var(--text-primary);
    gap: 12px;
  }

  &:focus-visible {
    outline: 2px solid var(--art);
    outline-offset: 4px;
  }
}

.rail-tools {
  display: flex;
  align-items: center;
  gap: 4px;
}

.rail-tool {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    color 0.22s ease,
    background-color 0.22s ease,
    border-color 0.22s ease,
    transform 0.22s var(--ease-expo);

  &:hover {
    color: var(--text-primary);
    background: var(--art-soft);
    border-color: var(--border-subtle);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }

  &:focus-visible {
    outline: 2px solid var(--art);
    outline-offset: 2px;
  }

  &.rail-tool-danger:hover {
    color: var(--color-error, #dc2626);
    background: color-mix(in srgb, var(--color-error, #dc2626) 12%, transparent);
    border-color: color-mix(in srgb, var(--color-error, #dc2626) 32%, transparent);
  }
}

/* ========== 主栅格 ========== */
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(320px, 27vw, 396px);
  gap: clamp(32px, 5vw, 88px);
  align-items: start;
}

/* ========== 画面 ========== */
.stage {
  position: relative;
  display: flex;
  flex-direction: column;
  /* 竖幅与方幅贴着版心左缘悬挂，留白落在画面与说明牌之间，
     而不是把画面夹在两侧等宽的空白里 */
  align-items: flex-start;
  min-width: 0;
}

.plate {
  position: relative;
  z-index: 1;
  display: block;
  width: var(--plate-width);
  max-width: 100%;
  padding: 0;
  border: 0;
  background: none;
  cursor: zoom-in;
  text-align: left;
  -webkit-appearance: none;
  appearance: none;

  /* 作品的光落在墙上：画面自己的颜色扩散到周围，而不是一块装饰光斑 */
  &::before {
    content: '';
    position: absolute;
    z-index: -1;
    inset: -7% -8%;
    background: radial-gradient(ellipse at 50% 46%, var(--art-wash), transparent 68%);
    filter: blur(54px);
    opacity: 0.85;
    pointer-events: none;
  }

  &:focus-visible {
    outline: 2px solid var(--art);
    outline-offset: 6px;
  }
}

.plate-mat {
  position: relative;
  display: block;
  overflow: hidden;
  /* 作品不裁角：照片就是照片，圆角与描边只会把它变成一张卡片 */
  border-radius: 0;
  background: var(--plate-bg);
  box-shadow: var(--plate-shadow);

  .plate.is-ready & {
    animation: plate-in 1s var(--ease-expo) both;
  }
}

@keyframes plate-in {
  from {
    clip-path: inset(5% 4% 5% 4%);
    opacity: 0.55;
    transform: translateY(14px) scale(0.995);
  }
  to {
    clip-path: inset(0 0 0 0);
    opacity: 1;
    transform: none;
  }
}

.plate-image {
  display: block;
  width: 100%;
  height: auto;
  max-height: var(--stage-max);
  object-fit: contain;
  opacity: 0;
  transform: scale(1.035);
  transition:
    opacity 0.6s ease 0.12s,
    transform 1.2s var(--ease-expo);

  &.visible {
    opacity: 1;
    transform: scale(1);
  }

  .plate:hover & {
    transform: scale(1.012);
    transition: transform 1.1s var(--ease-expo);
  }
}

.plate-skeleton {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 3 / 2;
  min-height: 260px;
  overflow: hidden;
  background: color-mix(in srgb, var(--art) 8%, var(--bg-tertiary));
}

.skeleton-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 20%,
    rgba(var(--art-rgb), 0.16) 46%,
    rgba(255, 255, 255, 0.24) 52%,
    transparent 78%
  );
  background-size: 220% 100%;
  animation: shimmer 1.8s ease-in-out infinite;
}

@keyframes shimmer {
  0% {
    background-position: 140% 0;
  }
  100% {
    background-position: -60% 0;
  }
}

.plate-hint {
  position: absolute;
  right: 16px;
  bottom: 16px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 14px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-primary) 72%, transparent);
  border: 1px solid var(--border-default);
  backdrop-filter: blur(14px) saturate(160%);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  opacity: 0;
  transform: translateY(6px);
  transition:
    opacity 0.35s ease,
    transform 0.45s var(--ease-expo);

  .plate:hover &,
  .plate:focus-visible & {
    opacity: 1;
    transform: none;
  }

  &.hidden,
  .plate:hover &.hidden {
    opacity: 0;
    transform: translateY(6px);
  }
}

.plate-cursor {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin: -36px 0 0 -36px;
  border-radius: 50%;
  background: var(--text-primary);
  color: var(--bg-primary);
  pointer-events: none;
  opacity: 0;
  scale: 0.4;
  transition:
    opacity 0.25s ease,
    scale 0.4s var(--ease-expo);
  will-change: transform;

  &.visible {
    opacity: 0.92;
    scale: 1;
  }
}

.plate-fallback {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: var(--plate-width);
  max-width: 100%;
  min-height: clamp(300px, 58vh, 560px);
  padding: 32px;
  border-radius: 0;
  background: var(--plate-bg);
  box-shadow: var(--plate-shadow);
  color: var(--text-secondary);
  text-align: center;

  .fallback-title {
    color: var(--text-primary);
    font-size: 16px;
    font-weight: 600;
  }

  .fallback-hint {
    max-width: 34ch;
    color: var(--text-tertiary);
    font-size: 13px;
    line-height: 1.6;
  }

  .fallback-retry {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-top: 8px;
    padding: 8px 14px;
    border: 1px solid var(--border-default);
    border-radius: 999px;
    background: transparent;
    color: var(--text-primary);
    font-family: inherit;
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition:
      border-color 0.2s ease,
      background-color 0.2s ease;

    &:hover {
      border-color: var(--art);
      background: var(--art-soft);
    }

    &:focus-visible {
      outline: 2px solid var(--art);
      outline-offset: 3px;
    }
  }
}

.stage-rule {
  position: relative;
  z-index: 1;
  width: var(--plate-width);
  max-width: 100%;
  height: 1px;
  margin-top: clamp(20px, 2.6vw, 28px);
  background: linear-gradient(
    90deg,
    rgba(var(--art-rgb), 0.72) 0%,
    var(--border-default) 46%,
    transparent 100%
  );
  opacity: 0;
  transform: scaleX(0.96);
  transform-origin: left center;
  transition:
    opacity 0.8s ease 0.25s,
    transform 1s var(--ease-expo) 0.25s;

  &.visible {
    opacity: 1;
    transform: none;
  }
}

.spec-strip {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  width: var(--plate-width);
  max-width: 100%;
  gap: clamp(24px, 4vw, 56px);
  margin: 0;
  padding: 18px 0 0;
  opacity: 0;
  transform: translateY(10px);
  transition:
    opacity 0.7s ease 0.3s,
    transform 0.8s var(--ease-expo) 0.3s;

  &.visible {
    opacity: 1;
    transform: none;
  }
}

.spec {
  display: flex;
  flex-direction: column;
  gap: 5px;

  dt {
    color: var(--text-tertiary);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.22em;
  }

  dd {
    margin: 0;
    color: var(--text-secondary);
    font-family: var(--font-mono);
    font-size: 12.5px;
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.01em;
  }
}

/* ========== 说明牌 ========== */
.placard {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.reveal {
  opacity: 0;
  transform: translateY(14px);

  .placard.ready & {
    animation: rise 0.85s var(--ease-expo) forwards;
    animation-delay: calc(var(--i) * 70ms + 120ms);
  }
}

@keyframes rise {
  to {
    opacity: 1;
    transform: none;
  }
}

.work-title {
  margin: 0 0 18px;
  color: var(--text-primary);
  /* 有宋体骨架的展示字：中文标题也有自己的字形，而不是回落到系统黑体 */
  font-family: 'Noto Serif SC', 'Songti SC', 'STSong', 'Source Han Serif SC', 'SimSun', serif;
  font-size: clamp(2rem, 3.1vw, 3.05rem);
  font-weight: 700;
  line-height: 1.22;
  letter-spacing: 0.005em;
  text-wrap: balance;
  overflow-wrap: anywhere;
}

.work-intro {
  max-width: 46ch;
  margin: 0;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.85;
  letter-spacing: 0.005em;
}

.byline {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: clamp(24px, 3vw, 34px) 0;
  padding-top: clamp(20px, 2.4vw, 26px);
  border-top: 1px solid var(--border-subtle);
}

.byline-avatar {
  flex-shrink: 0;
  border: 1px solid var(--border-default);
  box-shadow: 0 2px 10px -4px rgba(30, 27, 75, 0.35);
}

.byline-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.byline-name {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.byline-handle {
  color: var(--text-tertiary);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.02em;
}

.facts {
  margin: 0;
  border-top: 1px solid var(--border-subtle);
}

.fact {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 16px;
  align-items: baseline;
  padding: 15px 0;
  border-bottom: 1px solid var(--border-subtle);

  dt {
    color: var(--text-tertiary);
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.3em;
    white-space: nowrap;
  }

  dd {
    margin: 0;
    color: var(--text-primary);
    font-size: 14px;
    line-height: 1.6;
  }
}

.color-copy {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 4px 8px 4px 4px;
  margin-left: -4px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: none;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease;

  &:hover {
    background: var(--art-soft);
    border-color: var(--border-subtle);

    .copy-icon {
      color: var(--art);
    }
  }

  &:focus-visible {
    outline: 2px solid var(--art);
    outline-offset: 2px;
  }
}

.color-swatch {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.32),
    0 0 0 1px rgba(var(--art-rgb), 0.56);
}

.color-hex {
  color: var(--text-secondary);
  text-transform: uppercase;
}

.copy-icon {
  color: var(--text-tertiary);
  transition: color 0.2s ease;

  &.copied {
    color: var(--color-success, #10b981);
  }
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.tag-chip {
  padding: 4px 10px;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 12.5px;
  letter-spacing: 0.02em;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background-color 0.2s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--art) 55%, var(--border-default));
    background: var(--art-soft);
    color: var(--text-primary);
  }
}

/* ========== 动作 ========== */
.actions {
  display: flex;
  gap: 10px;
  margin-top: clamp(26px, 3vw, 34px);
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  height: 52px;
  padding: 0 18px;
  border-radius: 12px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    background-color 0.24s ease,
    border-color 0.24s ease,
    color 0.24s ease,
    box-shadow 0.3s ease,
    transform 0.25s var(--ease-expo);

  svg {
    flex-shrink: 0;
    transition: transform 0.35s var(--ease-expo);
  }

  &:hover svg {
    transform: scale(1.08);
  }

  &:active {
    transform: translateY(1px);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.45;
    transform: none;
    box-shadow: none;

    &:hover svg {
      transform: none;
    }
  }

  &:focus-visible {
    outline: 2px solid var(--art);
    outline-offset: 3px;
  }
}

.btn-primary {
  flex: 1.15;
  border: 1px solid var(--text-primary);
  background: var(--text-primary);
  color: var(--bg-primary);
  box-shadow: 0 10px 24px -14px rgba(30, 27, 75, 0.75);

  &:hover {
    background: color-mix(in srgb, var(--text-primary) 88%, var(--art));
    border-color: transparent;
    box-shadow: 0 16px 30px -16px rgba(var(--art-rgb), 0.6);
    transform: translateY(-1px);
  }
}

.btn-secondary {
  flex: 1;
  border: 1px solid var(--border-strong);
  background: transparent;
  color: var(--text-primary);

  &:hover {
    background: var(--art-soft);
    border-color: color-mix(in srgb, var(--art) 60%, var(--border-strong));
    transform: translateY(-1px);
  }
}

.btn-primary:disabled:hover {
  background: var(--text-primary);
  border-color: var(--text-primary);
}

.btn-secondary:disabled:hover {
  background: transparent;
  border-color: var(--border-strong);
}

.placard-foot {
  margin-top: clamp(28px, 3.4vw, 40px);
  padding-top: clamp(20px, 2.6vw, 28px);
  border-top: 1px solid var(--border-subtle);

  /* 授权面板属于说明牌的附属信息：去掉它自带的卡片外壳，
     按说明牌的细线行来排，免得一块白卡从墙面里跳出来 */
  :deep(.grant-panel) {
    margin-top: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
  }

  :deep(.panel-title svg) {
    color: var(--art);
  }

  :deep(.grant-list) {
    gap: 0;
  }

  :deep(.grant-item) {
    padding: 13px 0;
    border: 0;
    border-bottom: 1px solid var(--border-subtle);
    border-radius: 0;
    background: transparent;
  }
}

/* ========== 加载 / 失败态 ========== */
.sk-line {
  height: 14px;
  margin-bottom: 12px;
  border-radius: 6px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--text-tertiary) 16%, var(--bg-secondary)) 0%,
    color-mix(in srgb, var(--text-tertiary) 26%, var(--bg-secondary)) 50%,
    color-mix(in srgb, var(--text-tertiary) 16%, var(--bg-secondary)) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.6s ease-in-out infinite;

  &.sk-title {
    height: 34px;
    width: 86%;
    margin-bottom: 14px;
  }

  &.sk-title.short {
    width: 54%;
    margin-bottom: 26px;
  }

  &.sk-text {
    width: 100%;
  }

  &.sk-text.short {
    width: 72%;
    margin-bottom: 30px;
  }

  &.sk-name {
    width: 42%;
    margin-bottom: 0;
  }
}

.sk-byline {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 22px;
  border-top: 1px solid var(--border-subtle);
}

.sk-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--text-tertiary) 16%, var(--bg-secondary));
}

@keyframes sk-shimmer {
  0% {
    background-position: 160% 0;
  }
  100% {
    background-position: -60% 0;
  }
}

/* ========== 整体失败态 ========== */
.state-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 62vh;
  padding: 48px 0 80px;
  text-align: center;
}

.state-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: 4px;
  border-radius: 50%;
  background: var(--art-soft);
  color: var(--text-primary);
}

.state-title {
  margin: 0;
  color: var(--text-primary);
  font-family: 'Noto Serif SC', 'Songti SC', 'STSong', 'Source Han Serif SC', 'SimSun', serif;
  font-size: clamp(1.6rem, 2.8vw, 2.2rem);
  font-weight: 700;
  line-height: 1.28;
  letter-spacing: 0.005em;
  text-wrap: balance;
}

.state-hint {
  max-width: 44ch;
  margin: 0;
  color: var(--text-tertiary);
  font-size: 14px;
  line-height: 1.7;
}

.state-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;

  .btn {
    flex: none;
    min-width: 152px;
  }
}

/* ========== 移动端动作条 ========== */
.mobile-actions {
  display: none;
}

/* ========== 浏览器自带表面 ========== */
#pictureDetailPage ::selection {
  background: var(--art-soft);
  color: var(--text-primary);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* ========== 响应式 ========== */
@media (max-width: 1100px) {
  #pictureDetailPage {
    --stage-max: 70vh;
  }

  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 40px;
  }

  .placard {
    position: static;
    max-width: 640px;
  }

  .plate-image {
    max-height: var(--stage-max);
  }
}

@media (max-width: 640px) {
  #pictureDetailPage {
    padding-bottom: 112px;
  }

  .page-shell {
    padding: 0 16px;
  }

  .page-rail {
    margin-bottom: 18px;
  }

  .rail-tool-download {
    display: none;
  }

  .work-title {
    font-size: clamp(1.75rem, 7.4vw, 2.3rem);
  }

  .work-intro {
    font-size: 14.5px;
    line-height: 1.8;
  }

  .spec-strip {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px 24px;
  }

  .fact {
    grid-template-columns: 52px minmax(0, 1fr);
    gap: 12px;
  }

  .placard {
    max-width: none;
  }

  .plate-cursor {
    display: none;
  }

  .actions {
    display: none;
  }

  .mobile-actions {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 40;
    display: flex;
    gap: 10px;
    padding: 12px clamp(16px, 4vw, 24px) calc(12px + env(safe-area-inset-bottom));
    background: color-mix(in srgb, var(--bg-primary) 82%, transparent);
    border-top: 1px solid var(--border-subtle);
    backdrop-filter: blur(18px) saturate(170%);

    .btn-primary {
      flex: 1;
    }

    .btn-secondary {
      flex: 0 0 60px;
      padding: 0;
    }
  }
}

/* ========== 降低动态效果 ========== */
@media (prefers-reduced-motion: reduce) {
  #pictureDetailPage *,
  #pictureDetailPage *::before,
  #pictureDetailPage *::after {
    animation-duration: 0.001ms !important;
    animation-delay: 0ms !important;
    transition-duration: 0.001ms !important;
  }

  .plate-mat {
    clip-path: none;
    opacity: 1;
    transform: none;
  }

  .plate-image {
    opacity: 1;
    transform: none;
  }

  .plate-cursor {
    display: none;
  }

  .reveal {
    opacity: 1;
    transform: none;
  }
}
</style>
