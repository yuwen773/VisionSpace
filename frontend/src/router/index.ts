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
    },
    {
      path: '/user/register',
      name: 'register',
      component: () => import('@/pages/user/UserRegisterPage.vue'),
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
          meta: { showFooter: true },
        },
        {
          path: 'add_picture',
          name: 'AddPicture',
          component: () => import('@/pages/AddPicturePage.vue'),
        },
        {
          path: 'picture/:id',
          name: 'PictureDetail',
          component: () => import('@/pages/PictureDetailPage.vue'),
          props: true,
        },
        {
          path: 'add_space',
          name: 'AddSpace',
          component: () => import('@/pages/user/AddSpacePage.vue'),
        },
        {
          path: 'my_space',
          name: 'MySpace',
          component: () => import('@/pages/MySpacePage.vue'),
          meta: { showFooter: true },
        },
        {
          path: 'space/:id',
          name: 'SpaceDetail',
          component: () => import('@/pages/SpaceDetailPage.vue'),
          props: true,
        },
        {
          path: 'space_analyze',
          name: 'SpaceAnalyze',
          component: () => import('@/pages/SpaceAnalyzePage.vue'),
          props: true,
          meta: { showFooter: true },
        },
        {
          path: 'spaceUserManage/:spaceId',
          name: 'SpaceUserManage',
          component: () => import('@/pages/admin/SpaceUserManagePage.vue'),
          props: true,
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
        },
        {
          path: 'user_manage',
          name: 'UserManage',
          component: () => import('@/pages/admin/UserManagePage.vue'),
        },
        {
          path: 'picture_manage',
          name: 'PictureManage',
          component: () => import('@/pages/admin/PictureManagePage.vue'),
        },
        {
          path: 'space_manage',
          name: 'SpaceManage',
          component: () => import('@/pages/admin/SpaceManagePage.vue'),
        },
        {
          path: 'storage_config_manage',
          name: 'StorageConfigManage',
          component: () => import('@/pages/admin/StorageConfigManagePage.vue'),
        },
        {
          path: 'feedback_manage',
          name: 'FeedbackManage',
          component: () => import('@/pages/admin/FeedbackManagePage.vue'),
        },
      ],
    },
  ],
})

export default router
