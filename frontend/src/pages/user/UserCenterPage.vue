<template>
  <div id="userCenterPage" :class="{ 'is-light': isLight }">
    <a class="skip-link" href="#uc-body">跳到主要内容</a>

    <!-- 气氛层：静态网格与两团极淡的光，不参与滚动 -->
    <div class="uc-atmosphere" aria-hidden="true">
      <div class="uc-grid"></div>
      <div class="uc-wash uc-wash-a"></div>
      <div class="uc-wash uc-wash-b"></div>
    </div>

    <div class="uc-shell">
      <!-- ============ 档案封面 ============ -->
      <header class="masthead" aria-labelledby="uc-name">
        <div class="masthead-copy">
          <div class="masthead-meta">
            <span class="mono">{{ archiveId }}</span>
            <span class="meta-rule" aria-hidden="true"></span>
            <span class="mono">{{ memberSince }}</span>
            <span class="meta-rule" aria-hidden="true"></span>
            <span class="mono">{{ roleLabel }}</span>
          </div>

          <h1 id="uc-name" class="masthead-name">{{ displayName }}</h1>

          <p class="masthead-bio">{{ bio }}</p>

          <p v-if="isVip" class="masthead-vip mono">MEMBER · VIP · 有效至 {{ vipExpireLabel }}</p>

          <dl class="ledger" :aria-busy="archiveLoading">
            <div class="ledger-item">
              <dt class="mono">上传 / UPLOADS</dt>
              <dd>{{ statsLoading ? '—' : formatCount(stats.uploadCount) }}</dd>
            </div>
            <div class="ledger-item">
              <dt class="mono">收藏 / LIKES</dt>
              <dd>{{ statsLoading ? '—' : formatCount(stats.likeCount) }}</dd>
            </div>
            <div class="ledger-item">
              <dt class="mono">通过 / PASS RATE</dt>
              <dd>{{ statsLoading ? '—' : formatRate(stats.reviewPassRate) }}</dd>
            </div>
          </dl>

          <p v-if="archiveError" class="archive-error" role="status">
            作品数据暂时没有加载出来。
            <button type="button" class="uc-btn uc-btn--quiet" @click="loadArchive">重试</button>
          </p>

          <div class="masthead-actions">
            <button type="button" class="uc-btn uc-btn--solid" @click="router.push('/add_picture')">
              上传作品
              <ArrowUpRight :size="16" :stroke-width="1.6" aria-hidden="true" />
            </button>
            <button type="button" class="uc-btn uc-btn--quiet" @click="router.push('/explore')">
              浏览图库
              <ArrowRight :size="15" :stroke-width="1.6" aria-hidden="true" />
            </button>
          </div>
        </div>

        <!-- 接触印样：最近六件作品 -->
        <div class="contact-sheet" :class="{ 'is-loading': archiveLoading }">
          <div class="sheet-head">
            <span class="mono">最近 / RECENT PLATES</span>
            <span class="mono">{{ recentPictures.length.toString().padStart(2, '0') }}</span>
          </div>

          <ul class="plates" aria-label="最近上传的作品">
            <li v-for="slot in sheetSlots" :key="slot.key">
              <button
                v-if="slot.picture"
                type="button"
                class="plate"
                :aria-label="`打开作品：${slot.picture.name || '未命名'}`"
                @click="openPicture(slot.picture.id)"
              >
                <img
                  :src="contentSrc(slot.picture.thumbnailUrl || slot.picture.url)"
                  :alt="slot.picture.name || '作品缩略图'"
                  loading="lazy"
                  decoding="async"
                />
                <span class="plate-no mono" aria-hidden="true">{{ slot.no }}</span>
                <span class="plate-caption">
                  <span class="plate-name">{{ slot.picture.name || '未命名作品' }}</span>
                  <span class="mono">VIEW</span>
                </span>
              </button>
              <div v-else class="plate plate--empty" aria-hidden="true">
                <span class="mono">{{ slot.no }}</span>
              </div>
            </li>
          </ul>

          <p v-if="!archiveLoading && recentPictures.length === 0" class="sheet-empty">
            还没有作品。
            <button type="button" class="uc-btn uc-btn--quiet" @click="router.push('/add_picture')">
              上传第一张
            </button>
          </p>
        </div>
      </header>

      <!-- ============ 索引 + 记录 ============ -->
      <div id="uc-body" class="uc-body">
        <nav
          ref="railRef"
          class="index-rail"
          :class="{ 'is-faded-left': railFadedLeft, 'is-faded-right': railFadedRight }"
          aria-label="用户中心分区"
        >
          <div class="rail-head mono">INDEX</div>
          <div
            ref="railListRef"
            class="rail-list"
            role="tablist"
            :aria-orientation="railOrientation"
            @scroll.passive="updateRailEdges"
          >
            <button
              v-for="(item, i) in sections"
              :id="`uc-tab-${item.key}`"
              :key="item.key"
              type="button"
              role="tab"
              class="rail-item"
              :aria-selected="activeSection === item.key"
              aria-controls="uc-panel"
              :tabindex="activeSection === item.key ? 0 : -1"
              @click="selectSection(item.key)"
              @keydown="onRailKey($event, i)"
            >
              <span class="rail-no mono">{{ item.no }}</span>
              <span class="rail-label">{{ item.label }}</span>
              <span class="rail-hint mono">{{ item.hint }}</span>
            </button>
            <span
              class="rail-marker"
              :style="{ transform: `translateY(${activeIndex * 56}px)` }"
              aria-hidden="true"
            ></span>
          </div>
          <p class="rail-foot mono">VS / {{ archiveId }}</p>
        </nav>

        <div
          id="uc-panel"
          class="record-panel"
          role="tabpanel"
          :aria-labelledby="`uc-tab-${activeSection}`"
          tabindex="0"
        >
          <Transition name="record-swap" mode="out-in">
            <ProfileSection v-if="activeSection === 'profile'" key="profile" />
            <PicturesSection
              v-else-if="activeSection === 'pictures'"
              key="pictures"
              :stats="stats"
              :pictures="recentPictures"
              :loading="archiveLoading"
              @go-to-picture="openPicture"
              @go-to-add="router.push('/add_picture')"
            />
            <VipSection v-else-if="activeSection === 'vip'" key="vip" />
            <PreferenceSection v-else-if="activeSection === 'preference'" key="preference" />
            <FeedbackSection v-else key="feedback" />
          </Transition>
        </div>
      </div>

      <footer class="uc-foot">
        <span class="mono">VISIONSPACE · 个人档案</span>
        <nav aria-label="档案出口">
          <button type="button" class="uc-btn uc-btn--quiet" @click="router.push('/explore')">
            探索图库
          </button>
          <button type="button" class="uc-btn uc-btn--quiet" @click="router.push('/agent')">
            SpaceMind
          </button>
          <button type="button" class="uc-btn uc-btn--quiet" @click="router.push('/')">
            返回首页
          </button>
        </nav>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ArrowRight, ArrowUpRight } from 'lucide-vue-next'
import { useLoginUserStore } from '@/stores/userLogin'
import { useTheme } from '@/composables/useTheme'
import { getUserPictureStatsUsingGet, getUserRecentPicturesUsingGet } from '@/api/userController'
import { contentSrc } from '@/utils'
import ProfileSection from '@/components/user-center/ProfileSection.vue'
import PicturesSection from '@/components/user-center/PicturesSection.vue'
import VipSection from '@/components/user-center/VipSection.vue'
import PreferenceSection from '@/components/user-center/PreferenceSection.vue'
import FeedbackSection from '@/components/user-center/FeedbackSection.vue'

type SectionKey = 'profile' | 'pictures' | 'vip' | 'preference' | 'feedback'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser
const { currentTheme } = useTheme()
const isLight = computed(() => currentTheme.value === 'pop')

const sections: { key: SectionKey; no: string; label: string; hint: string }[] = [
  { key: 'profile', no: '01', label: '个人资料', hint: 'IDENTITY' },
  { key: 'pictures', no: '02', label: '我的图片', hint: 'ARCHIVE' },
  { key: 'vip', no: '03', label: '会员权益', hint: 'MEMBER' },
  { key: 'preference', no: '04', label: 'AI 偏好', hint: 'MEMORY' },
  { key: 'feedback', no: '05', label: '意见反馈', hint: 'FEEDBACK' },
]

const activeSection = ref<SectionKey>('profile')
const activeIndex = computed(() => sections.findIndex((item) => item.key === activeSection.value))
const railRef = ref<HTMLElement>()
const railListRef = ref<HTMLElement>()
const railFadedLeft = ref(false)
const railFadedRight = ref(false)

// 横向索引条只在还有内容被裁掉的一侧显示渐隐
const updateRailEdges = () => {
  const el = railListRef.value
  if (!el) return
  railFadedLeft.value = el.scrollLeft > 2
  railFadedRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 2
}

// ≤860px 索引条改为横向滚动的 tab 栏，方向与键盘操作要一起换
const compactQuery = window.matchMedia('(max-width: 860px)')
const isCompact = ref(compactQuery.matches)
const railOrientation = computed(() => (isCompact.value ? 'horizontal' : 'vertical'))
const onCompactChange = (event: MediaQueryListEvent) => {
  isCompact.value = event.matches
  nextTick(updateRailEdges)
}
compactQuery.addEventListener('change', onCompactChange)
onBeforeUnmount(() => compactQuery.removeEventListener('change', onCompactChange))

const sectionFromHash = (): SectionKey => {
  const key = window.location.hash.replace('#', '') as SectionKey
  return sections.some((item) => item.key === key) ? key : 'profile'
}

const selectSection = (key: SectionKey) => {
  activeSection.value = key
  if (!isCompact.value) return
  // 横向索引条里，点选靠右的分区时把它带回可视范围中间
  nextTick(() => {
    const index = sections.findIndex((item) => item.key === key)
    const buttons = railRef.value?.querySelectorAll<HTMLButtonElement>('.rail-item')
    buttons?.[index]?.scrollIntoView({ block: 'nearest', inline: 'center' })
    updateRailEdges()
  })
}

const onRailKey = (event: KeyboardEvent, index: number) => {
  const nextKeys = ['ArrowDown', 'ArrowRight']
  const prevKeys = ['ArrowUp', 'ArrowLeft']
  if (![...nextKeys, ...prevKeys, 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  let next = index
  if (nextKeys.includes(event.key)) next = (index + 1) % sections.length
  if (prevKeys.includes(event.key)) next = (index - 1 + sections.length) % sections.length
  if (event.key === 'Home') next = 0
  if (event.key === 'End') next = sections.length - 1
  selectSection(sections[next].key)
  nextTick(() => {
    const buttons = railRef.value?.querySelectorAll<HTMLButtonElement>('.rail-item')
    buttons?.[next]?.focus()
  })
}

watch(activeSection, (key) => {
  if (window.location.hash !== `#${key}`) {
    window.history.replaceState(null, '', `#${key}`)
  }
})

const displayName = computed(() => loginUser.userName || loginUser.userAccount || '未命名用户')
const bio = computed(() => loginUser.userProfile || '还没有写下简介。')
const roleLabel = computed(() => {
  const role = loginUser.userRole
  if (role === 'admin') return '平台管理员'
  if (role === 'moderator') return '内容处置员'
  return '个人创作者'
})

const archiveId = computed(() => {
  const year = loginUser.createTime
    ? dayjs(loginUser.createTime).format('YYYY')
    : dayjs().format('YYYY')
  const id = loginUser.id ? String(loginUser.id).padStart(4, '0') : '0000'
  return `VS-${year}-${id}`
})

const memberSince = computed(() => {
  if (!loginUser.createTime) return 'SINCE —'
  return `SINCE ${dayjs(loginUser.createTime).format('YYYY.MM')}`
})

const isVip = computed(
  () => Boolean(loginUser.vipExpireTime) && dayjs(loginUser.vipExpireTime).isAfter(dayjs()),
)
const vipExpireLabel = computed(() =>
  loginUser.vipExpireTime ? dayjs(loginUser.vipExpireTime).format('YYYY.MM.DD') : '—',
)

const stats = ref<API.UserPictureStatsResponse>({
  uploadCount: 0,
  likeCount: 0,
  reviewPassRate: 0,
})
const recentPictures = ref<API.PictureVO[]>([])
const archiveLoading = ref(true)
const statsLoading = ref(true)
const archiveError = ref(false)

const formatCount = (value?: number) =>
  typeof value === 'number' ? value.toLocaleString('zh-CN') : '0'
const formatRate = (value?: number) => (typeof value === 'number' ? `${value}%` : '—')

const sheetSlots = computed(() =>
  Array.from({ length: 6 }, (_, i) => ({
    key: recentPictures.value[i]?.id ?? `empty-${i}`,
    no: String(i + 1).padStart(2, '0'),
    picture: recentPictures.value[i],
  })),
)

const openPicture = (id?: number) => {
  if (id) router.push(`/picture/${id}`)
}

const loadArchive = async () => {
  archiveLoading.value = true
  statsLoading.value = true
  archiveError.value = false
  const [statsResult, picturesResult] = await Promise.allSettled([
    getUserPictureStatsUsingGet(),
    getUserRecentPicturesUsingGet({ count: 6 }),
  ])
  if (statsResult.status === 'fulfilled' && statsResult.value.data.code === 0) {
    stats.value = statsResult.value.data.data ?? stats.value
    statsLoading.value = false
  } else {
    archiveError.value = true
  }
  if (picturesResult.status === 'fulfilled' && picturesResult.value.data.code === 0) {
    recentPictures.value = picturesResult.value.data.data ?? []
  } else {
    archiveError.value = true
  }
  archiveLoading.value = false
  statsLoading.value = false
}

onMounted(() => {
  activeSection.value = sectionFromHash()
  loadArchive()
  nextTick(updateRailEdges)
  document.fonts?.ready.then(updateRailEdges)
  window.addEventListener('resize', updateRailEdges)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateRailEdges)
})
</script>

<style scoped>
#userCenterPage {
  /* 页面自有调色：深空暗色的档案纸 */
  --uc-bg: #05060c;
  --uc-bg-raised: #0a0c15;
  --uc-ink: #f4f1ea;
  --uc-ink-2: rgba(244, 241, 234, 0.74);
  --uc-ink-3: rgba(244, 241, 234, 0.52);
  --uc-line: rgba(244, 241, 234, 0.12);
  --uc-line-strong: rgba(244, 241, 234, 0.24);
  --uc-accent: #b7a2ff;
  --uc-accent-deep: #6f4bd8;
  --uc-positive: #86e3c8;
  --uc-danger: #ff8fa3;
  --uc-serif: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --uc-sans: 'Noto Sans SC', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  --uc-mono: 'JetBrains Mono', 'SFMono-Regular', 'Consolas', monospace;
  --uc-ease: cubic-bezier(0.22, 1, 0.36, 1);

  position: relative;
  min-height: 100vh;
  background: var(--uc-bg);
  color: var(--uc-ink);
  font-family: var(--uc-sans);
  font-weight: 400;
  overflow-x: clip;
  isolation: isolate;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#userCenterPage ::selection {
  background: rgba(183, 162, 255, 0.3);
  color: #fff;
}

.mono {
  font-family: var(--uc-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--uc-ink-3);
}

.skip-link {
  position: absolute;
  top: -60px;
  left: 24px;
  z-index: 40;
  padding: 10px 16px;
  background: var(--uc-ink);
  color: var(--uc-bg);
  font-size: 13px;
  text-decoration: none;
  transition: top var(--transition-fast);
}

.skip-link:focus {
  top: 12px;
}

/* ============ 气氛层 ============ */
.uc-atmosphere {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: radial-gradient(120% 80% at 50% -10%, #0b0d1c 0%, var(--uc-bg) 58%, #03040a 100%);
}

.uc-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(244, 241, 234, 0.026) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(244, 241, 234, 0.026) 1px, transparent 1px);
  background-size: 96px 96px;
  mask-image: radial-gradient(ellipse 72% 60% at 50% 24%, #000 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(ellipse 72% 60% at 50% 24%, #000 0%, transparent 78%);
}

.uc-wash {
  position: absolute;
  border-radius: 50%;
  filter: blur(110px);
}

.uc-wash-a {
  width: 54vw;
  height: 40vw;
  top: -20vw;
  right: -14vw;
  background: radial-gradient(circle, rgba(111, 75, 216, 0.26) 0%, transparent 70%);
}

.uc-wash-b {
  width: 36vw;
  height: 34vw;
  bottom: -16vw;
  left: -12vw;
  background: radial-gradient(circle, rgba(134, 227, 200, 0.08) 0%, transparent 70%);
}

/* ============ 外壳 ============ */
.uc-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1360px;
  margin: 0 auto;
  padding: 0 clamp(20px, 5vw, 64px);
}

/* ============ 封面 ============ */
.masthead {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 42%);
  gap: clamp(32px, 6vw, 88px);
  align-items: end;
  padding: clamp(44px, 8vh, 96px) 0 clamp(40px, 6vh, 72px);
  border-bottom: 1px solid var(--uc-line);
}

.masthead-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  min-height: 16px;
}

.meta-rule {
  width: 28px;
  height: 1px;
  background: var(--uc-line-strong);
}

.masthead-name {
  margin: clamp(18px, 2.4vh, 28px) 0 0;
  font-family: var(--uc-serif);
  font-size: clamp(52px, 8vw, 116px);
  font-weight: 600;
  line-height: 0.98;
  letter-spacing: 0.01em;
  color: var(--uc-ink);
  text-wrap: balance;
  word-break: break-word;
}

.masthead-bio {
  max-width: 46ch;
  margin: clamp(18px, 2.4vh, 26px) 0 0;
  font-size: clamp(15px, 1.2vw, 17px);
  line-height: 1.85;
  color: var(--uc-ink-2);
  text-wrap: pretty;
}

.masthead-vip {
  margin: var(--space-3) 0 0;
  color: var(--uc-positive);
}

.ledger {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: clamp(28px, 4vh, 44px) 0 0;
  padding: 0;
  border-top: 1px solid var(--uc-line);
  border-bottom: 1px solid var(--uc-line);
}

.ledger-item {
  padding: 18px 20px 20px 0;
  min-width: 0;
}

.ledger-item + .ledger-item {
  padding-left: 20px;
  border-left: 1px solid var(--uc-line);
}

.ledger dt {
  display: flex;
  align-items: flex-end;
  min-height: 26px;
  margin: 0;
  font-size: 9px;
  line-height: 1.3;
  letter-spacing: 0.16em;
}

.ledger dd {
  margin: 8px 0 0;
  font-family: var(--uc-serif);
  font-size: clamp(26px, 3vw, 40px);
  font-weight: 600;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--uc-ink);
}

.archive-error {
  margin: var(--space-4) 0 0;
  font-size: 13px;
  color: var(--uc-ink-2);
}

.masthead-actions {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  margin-top: clamp(24px, 3.4vh, 36px);
}

/* ============ 接触印样 ============ */
.contact-sheet {
  min-width: 0;
}

.sheet-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--uc-line);
}

.plates {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.plates li {
  min-width: 0;
}

.plates li:nth-child(even) {
  margin-top: 18px;
}

.plate {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 3 / 4;
  padding: 0;
  border: 1px solid var(--uc-line);
  background: var(--uc-bg-raised);
  cursor: pointer;
  overflow: hidden;
  color: var(--uc-ink);
  text-align: left;
}

.plate img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.86) contrast(1.03);
  transition:
    transform 0.6s var(--uc-ease),
    filter 0.4s ease;
}

.plate:hover img,
.plate:focus-visible img {
  transform: scale(1.035);
  filter: saturate(1) contrast(1.03);
}

.plate-no {
  position: absolute;
  top: 8px;
  left: 10px;
  font-size: 9px;
  color: rgba(255, 255, 255, 0.72);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

.plate-caption {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 10px 9px;
  background: rgba(5, 6, 12, 0.86);
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  transform: translateY(100%);
  transition: transform 0.32s var(--uc-ease);
}

.plate:hover .plate-caption,
.plate:focus-visible .plate-caption {
  transform: translateY(0);
}

.plate-name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.92);
}

.plate-caption .mono {
  font-size: 9px;
  color: rgba(255, 255, 255, 0.62);
}

.plate--empty {
  display: flex;
  align-items: flex-end;
  justify-content: flex-start;
  padding: 10px;
  cursor: default;
  background-image: repeating-linear-gradient(
    -45deg,
    transparent 0,
    transparent 11px,
    rgba(244, 241, 234, 0.045) 11px,
    rgba(244, 241, 234, 0.045) 12px
  );
}

.plate--empty .mono {
  font-size: 9px;
  color: var(--uc-ink-3);
}

.contact-sheet.is-loading .plate {
  opacity: 0.55;
}

.sheet-empty {
  margin: var(--space-4) 0 0;
  font-size: 13px;
  color: var(--uc-ink-3);
}

/* ============ 索引 + 记录 ============ */
.uc-body {
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  gap: clamp(32px, 5vw, 76px);
  align-items: start;
  padding: clamp(32px, 5vh, 56px) 0 clamp(48px, 8vh, 96px);
}

.index-rail {
  position: sticky;
  top: calc(72px + 28px);
  align-self: start;
  min-width: 0;
}

.rail-head {
  padding-bottom: 12px;
  border-bottom: 1px solid var(--uc-line);
  font-size: 9px;
}

.rail-list {
  position: relative;
  border-left: 1px solid var(--uc-line);
}

.rail-item {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 12px;
  width: 100%;
  height: 56px;
  padding: 0 12px 0 18px;
  border: none;
  background: transparent;
  color: var(--uc-ink-2);
  font-family: var(--uc-sans);
  font-size: 14px;
  font-weight: 400;
  text-align: left;
  cursor: pointer;
  transition:
    color var(--transition-fast),
    background var(--transition-fast);
}

.rail-item:hover {
  color: var(--uc-ink);
  background: rgba(244, 241, 234, 0.035);
}

.rail-item[aria-selected='true'] {
  color: var(--uc-ink);
  font-weight: 600;
}

.rail-no {
  font-size: 10px;
  color: var(--uc-ink-3);
  transition: color var(--transition-fast);
}

.rail-item[aria-selected='true'] .rail-no {
  color: var(--uc-accent);
}

.rail-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

.rail-hint {
  font-size: 8px;
  letter-spacing: 0.14em;
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity 0.24s var(--uc-ease),
    transform 0.24s var(--uc-ease);
}

.rail-item[aria-selected='true'] .rail-hint,
.rail-item:hover .rail-hint {
  opacity: 1;
  transform: translateX(0);
}

.rail-marker {
  position: absolute;
  top: 0;
  left: -1px;
  width: 2px;
  height: 56px;
  background: var(--uc-accent);
  transition: transform 0.28s var(--uc-ease);
  pointer-events: none;
}

.rail-foot {
  margin: var(--space-6) 0 0;
  font-size: 9px;
  color: var(--uc-ink-3);
}

.record-panel {
  min-width: 0;
  outline: none;
}

/* 分区切换：一次向上的遮罩擦拭，不重复入场编排 */
.record-swap-enter-active {
  transition:
    opacity 0.26s var(--uc-ease),
    transform 0.26s var(--uc-ease),
    clip-path 0.34s var(--uc-ease);
}

.record-swap-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}

.record-swap-enter-from {
  opacity: 0;
  transform: translateY(14px);
  clip-path: inset(0 0 100% 0);
}

.record-swap-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* ============ 页脚 ============ */
.uc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-6) 0 var(--space-12);
  border-top: 1px solid var(--uc-line);
}

.uc-foot nav {
  display: flex;
  align-items: center;
  gap: var(--space-6);
}

/* ============ 亮色：同一版式，换成暖纸与墨字 ============ */
#userCenterPage.is-light {
  --uc-bg: #faf7ff;
  --uc-bg-raised: #f2edff;
  --uc-ink: #1e1b4b;
  --uc-ink-2: rgba(30, 27, 75, 0.82);
  --uc-ink-3: rgba(30, 27, 75, 0.68);
  --uc-line: rgba(124, 58, 237, 0.16);
  --uc-line-strong: rgba(124, 58, 237, 0.32);
  --uc-accent: #7c3aed;
  --uc-accent-deep: #6d28d9;
  --uc-positive: #047857;
  --uc-danger: #b91c1c;
}

#userCenterPage.is-light .uc-atmosphere {
  background: radial-gradient(120% 80% at 50% -10%, #ffffff 0%, var(--uc-bg) 58%);
}

#userCenterPage.is-light .uc-grid {
  background-image:
    linear-gradient(to right, rgba(124, 58, 237, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(124, 58, 237, 0.06) 1px, transparent 1px);
}

#userCenterPage.is-light .uc-wash-a {
  background: radial-gradient(circle, rgba(168, 85, 247, 0.12) 0%, transparent 70%);
}

#userCenterPage.is-light .uc-wash-b {
  background: radial-gradient(circle, rgba(34, 211, 238, 0.07) 0%, transparent 70%);
}

#userCenterPage.is-light .plate img {
  filter: saturate(0.92) contrast(1.01);
}

#userCenterPage.is-light .plate--empty {
  background-image: repeating-linear-gradient(
    -45deg,
    transparent 0,
    transparent 11px,
    rgba(124, 58, 237, 0.07) 11px,
    rgba(124, 58, 237, 0.07) 12px
  );
}

#userCenterPage.is-light .plate-caption {
  background: rgba(30, 27, 75, 0.9);
}

#userCenterPage.is-light .plate-no {
  color: rgba(255, 255, 255, 0.86);
}

/* ============ 响应式 ============ */
@media (max-width: 1080px) {
  .masthead {
    grid-template-columns: minmax(0, 1fr) minmax(260px, 40%);
    gap: clamp(24px, 4vw, 48px);
  }

  .uc-body {
    grid-template-columns: 188px minmax(0, 1fr);
    gap: clamp(24px, 4vw, 44px);
  }

  .rail-hint {
    display: none;
  }
}

@media (max-width: 860px) {
  .masthead {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
  }

  .contact-sheet {
    max-width: 560px;
  }

  .uc-body {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-6);
  }

  .index-rail {
    position: sticky;
    top: 72px;
    z-index: 6;
    margin: 0 calc(-1 * clamp(20px, 5vw, 64px));
    padding: 0 clamp(20px, 5vw, 64px);
    background: var(--uc-bg);
  }

  .rail-head,
  .rail-foot {
    display: none;
  }

  .rail-list {
    display: flex;
    gap: 0;
    border-left: none;
    border-bottom: 1px solid var(--uc-line);
    overflow-x: auto;
    scrollbar-width: none;
  }

  .rail-list::-webkit-scrollbar {
    display: none;
  }

  /* 两侧渐隐：只在还有内容被裁掉的一侧出现，柔化被裁切的标签 */
  .index-rail::before,
  .index-rail::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    width: 56px;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.18s linear;
  }

  .index-rail::before {
    left: 0;
    background: linear-gradient(to left, transparent, var(--uc-bg) 50%);
  }

  .index-rail::after {
    right: 0;
    background: linear-gradient(to right, transparent, var(--uc-bg) 50%);
  }

  .index-rail.is-faded-left::before,
  .index-rail.is-faded-right::after {
    opacity: 1;
  }

  .rail-item {
    flex: 0 0 auto;
    width: auto;
    height: 48px;
    padding: 0 16px;
    gap: 8px;
    border-bottom: 2px solid transparent;
  }

  .rail-item[aria-selected='true'] {
    border-bottom-color: var(--uc-accent);
  }

  .rail-hint {
    display: none;
  }

  .rail-marker {
    display: none;
  }

  .record-panel {
    padding-top: var(--space-2);
  }
}

@media (max-width: 640px) {
  .masthead {
    padding-top: var(--space-8);
  }

  .masthead-name {
    font-size: clamp(44px, 13vw, 64px);
  }

  .ledger-item {
    padding: 14px 12px 16px 0;
  }

  .ledger-item + .ledger-item {
    padding-left: 12px;
  }

  .ledger dd {
    font-size: clamp(22px, 6vw, 30px);
  }

  .plates {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .masthead-actions {
    gap: var(--space-4);
    flex-wrap: wrap;
  }

  .uc-foot {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }

  .uc-foot nav {
    flex-wrap: wrap;
    gap: var(--space-4);
  }
}

@media (prefers-reduced-motion: reduce) {
  #userCenterPage *,
  #userCenterPage *::before,
  #userCenterPage *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>

<!-- 子分区共用的控件词汇（作用域限定在用户中心内） -->
<style lang="less">
#userCenterPage .uc-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 42px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: 2px;
  background: transparent;
  color: var(--uc-ink);
  font-family: var(--uc-sans);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

#userCenterPage .uc-btn svg {
  flex-shrink: 0;
  transition: transform 0.24s var(--uc-ease);
}

#userCenterPage .uc-btn:hover svg {
  transform: translate(1px, -1px);
}

#userCenterPage .uc-btn--solid {
  background: var(--uc-ink);
  border-color: var(--uc-ink);
  color: var(--uc-bg);
}

#userCenterPage .uc-btn--solid:hover {
  background: var(--uc-accent);
  border-color: var(--uc-accent);
  color: #0b0716;
}

/* 亮色主题的强调紫更浅，悬停时改用白字保证对比度 */
#userCenterPage.is-light .uc-btn--solid:hover {
  color: #fff;
}

#userCenterPage .uc-btn--line {
  border-color: var(--uc-line-strong);
  color: var(--uc-ink);
}

#userCenterPage .uc-btn--line:hover {
  border-color: var(--uc-accent);
  color: var(--uc-accent);
}

#userCenterPage .uc-btn--quiet {
  min-height: 0;
  padding: 0;
  border: none;
  color: var(--uc-ink-2);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 4px;
  font-weight: 400;
}

#userCenterPage .uc-btn--quiet:hover {
  color: var(--uc-ink);
  background: transparent;
}

#userCenterPage .uc-btn--quiet svg {
  transition: transform 0.24s var(--uc-ease);
}

#userCenterPage .uc-btn--quiet:hover svg {
  transform: translateX(2px);
}

#userCenterPage .uc-btn--danger {
  border-color: color-mix(in srgb, var(--uc-danger) 45%, transparent);
  color: var(--uc-danger);
}

#userCenterPage .uc-btn--danger:hover {
  border-color: var(--uc-danger);
  background: color-mix(in srgb, var(--uc-danger) 8%, transparent);
}

#userCenterPage .uc-btn:disabled {
  opacity: 0.42;
  cursor: not-allowed;
  transform: none;
}

#userCenterPage .uc-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
  font-family: var(--uc-mono);
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--uc-ink-3);
}

#userCenterPage .uc-field {
  display: block;
  width: 100%;
  min-height: 46px;
  padding: 11px 14px;
  border: 1px solid var(--uc-line-strong);
  border-radius: 2px;
  background: transparent;
  color: var(--uc-ink);
  font-family: var(--uc-sans);
  font-size: 15px;
  line-height: 1.6;
  caret-color: var(--uc-accent);
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast);
}

#userCenterPage textarea.uc-field {
  min-height: 112px;
  resize: vertical;
}

#userCenterPage .uc-field::placeholder {
  color: var(--uc-ink-3);
  opacity: 1;
}

#userCenterPage .uc-field:hover {
  border-color: var(--uc-ink-3);
}

#userCenterPage .uc-field:focus {
  outline: none;
  border-color: var(--uc-accent);
}

#userCenterPage .uc-field:focus-visible {
  outline: 2px solid var(--uc-accent);
  outline-offset: 1px;
}

#userCenterPage .uc-form-row {
  margin-bottom: var(--space-6);
}

#userCenterPage .uc-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--uc-ink-3);
  text-wrap: pretty;
}

#userCenterPage .uc-actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

#userCenterPage .uc-subhead {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 var(--space-4);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--uc-line);
  font-size: 13px;
  font-weight: 600;
  color: var(--uc-ink);
  letter-spacing: 0.02em;
}

#userCenterPage .uc-subhead .mono {
  font-weight: 400;
}

#userCenterPage .uc-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--uc-line-strong);
  border-radius: 999px;
  background: transparent;
  color: var(--uc-ink-2);
  font-size: 12px;
  line-height: 1.5;
}

#userCenterPage .uc-status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
}

#userCenterPage .uc-status--ok {
  color: var(--uc-positive);
}

#userCenterPage .uc-status--error {
  color: var(--uc-danger);
}

#userCenterPage :focus-visible {
  outline: 2px solid var(--uc-accent);
  outline-offset: 3px;
}

#userCenterPage .sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

#userCenterPage .mono {
  font-family: var(--uc-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--uc-ink-3);
}

/* Ant Design 多选标签：只保留平面纸面质感，弹层沿用全局主题 */
#userCenterPage .uc-tags .ant-select-selector {
  min-height: 46px;
  padding: 6px 10px !important;
  background: transparent !important;
  border: 1px solid var(--uc-line-strong) !important;
  border-radius: 2px !important;
  box-shadow: none !important;
}

#userCenterPage .uc-tags.ant-select-focused .ant-select-selector,
#userCenterPage .uc-tags:hover .ant-select-selector {
  border-color: var(--uc-accent) !important;
}

#userCenterPage .uc-tags .ant-select-selection-item {
  margin: 3px 6px 3px 0 !important;
  padding: 1px 9px !important;
  background: transparent !important;
  border: 1px solid var(--uc-line-strong) !important;
  border-radius: 999px !important;
  color: var(--uc-ink) !important;
  font-size: 13px !important;
  line-height: 22px !important;
}

#userCenterPage .uc-tags .ant-select-selection-item-remove {
  color: var(--uc-ink-3) !important;
}

#userCenterPage .uc-tags .ant-select-selection-placeholder {
  color: var(--uc-ink-3) !important;
  font-size: 14px !important;
}

#userCenterPage .uc-tags .ant-select-selection-search-input {
  color: var(--uc-ink) !important;
  caret-color: var(--uc-accent);
}

#userCenterPage .uc-tags .ant-select-arrow {
  color: var(--uc-ink-3) !important;
}

@media (max-width: 860px) {
  #userCenterPage .uc-btn {
    min-height: 44px;
  }
}
</style>
