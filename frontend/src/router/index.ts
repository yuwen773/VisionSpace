import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '@/layout/BasicLayout.vue'
import AdminLayout from '@/layout/AdminLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 独立页面（无布局）
    {
      path: '/user/login',
      name: 'login',
      component: () => import('@/pages/user/UserLoginPage.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/user/register',
      name: 'register',
      component: () => import('@/pages/user/UserRegisterPage.vue'),
      meta: { title: '注册' },
    },
    // 用户端路由（BasicLayout 包裹）
    {
      path: '/',
      component: BasicLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/pages/LandingPage.vue'),
          meta: { fullWidth: true, showFooter: false },
        },
        {
          path: 'explore',
          name: 'Explore',
          component: () => import('@/pages/ExplorePage.vue'),
          meta: { showFooter: true, title: '探索图库' },
        },
        {
          path: 'add_picture',
          name: 'AddPicture',
          component: () => import('@/pages/AddPicturePage.vue'),
          meta: { title: '添加图片' },
        },
        {
          path: 'picture/:id',
          name: 'PictureDetail',
          component: () => import('@/pages/PictureDetailPage.vue'),
          props: true,
          meta: { title: '图片详情' },
        },
        {
          path: 'add_space',
          name: 'AddSpace',
          component: () => import('@/pages/user/AddSpacePage.vue'),
          meta: { title: '创建空间' },
        },
        {
          path: 'my_space',
          name: 'MySpace',
          component: () => import('@/pages/MySpacePage.vue'),
          meta: { showFooter: true, title: '我的空间' },
        },
        {
          path: 'space/:id',
          name: 'SpaceDetail',
          component: () => import('@/pages/SpaceDetailPage.vue'),
          props: true,
          meta: { title: '空间详情' },
        },
        {
          path: 'space_analyze',
          name: 'SpaceAnalyze',
          component: () => import('@/pages/SpaceAnalyzePage.vue'),
          props: true,
          meta: { showFooter: true, title: '空间分析' },
        },
        {
          path: 'spaceUserManage/:spaceId',
          name: 'SpaceUserManage',
          component: () => import('@/pages/admin/SpaceUserManagePage.vue'),
          props: true,
          meta: { title: '空间成员' },
        },
        {
          path: 'agent',
          name: 'AgentChat',
          component: () => import('@/pages/AgentChatPage.vue'),
          meta: { title: 'SpaceMind', fullScreen: true },
        },
        {
          path: 'user/center',
          name: 'UserCenter',
          component: () => import('@/pages/user/UserCenterPage.vue'),
          meta: { title: '用户中心', requiresAuth: true },
        },
        {
          // 违规资源处置：独立于平台管理员的处置角色
          path: 'moderation',
          name: 'Moderation',
          component: () => import('@/pages/moderation/ModerationPage.vue'),
          meta: { title: '内容处置', requiresAuth: true },
        },
      ],
    },
    // 管理端路由（AdminLayout 包裹）
    {
      path: '/admin',
      component: AdminLayout,
      redirect: '/admin/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/pages/admin/DashboardPage.vue'),
          meta: { title: '控制台' },
        },
        {
          path: 'user_manage',
          name: 'UserManage',
          component: () => import('@/pages/admin/UserManagePage.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'picture_manage',
          name: 'PictureManage',
          component: () => import('@/pages/admin/PictureManagePage.vue'),
          meta: { title: '图片管理' },
        },
        {
          path: 'space_manage',
          name: 'SpaceManage',
          component: () => import('@/pages/admin/SpaceManagePage.vue'),
          meta: { title: '空间管理' },
        },
        {
          path: 'storage_config_manage',
          name: 'StorageConfigManage',
          component: () => import('@/pages/admin/StorageConfigManagePage.vue'),
          meta: { title: '存储配置' },
        },
        {
          path: 'feedback_manage',
          name: 'FeedbackManage',
          component: () => import('@/pages/admin/FeedbackManagePage.vue'),
          meta: { title: '反馈管理' },
        },
      ],
    },
  ],
})

const APP_NAME = 'VisionSpace'
const DEFAULT_TITLE = 'VisionSpace · 视觉发现与 AI 创作空间'

// 每条路由在 meta.title 里声明页面名，导航后同步 document.title，
// 否则所有页面共用 index.html 的标题，历史记录与辅助技术都无法区分。
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${APP_NAME}` : DEFAULT_TITLE
})

export default router
