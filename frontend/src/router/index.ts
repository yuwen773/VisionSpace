import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import BasicLayout from '@/layout/BasicLayout.vue'
import AdminLayout from '../layout/AdminLayout.vue'
import AddPicturePage from '@/pages/AddPicturePage.vue'
import AddPictureBatchPage from '@/pages/AddPictureBatchPage.vue'
import PictureManagePage from '@/pages/admin/PictureManagePage.vue'
import PictureDetailPage from '@/pages/PictureDetailPage.vue'
import SpaceManagePage from '@/pages/admin/SpaceManagePage.vue'
import AddSpacePage from '@/pages/AddSpacePage.vue'
import MySpacePage from '@/pages/MySpacePage.vue'
import SpaceDetailPage from '@/pages/SpaceDetailPage.vue'
import SearchPicturePage from '@/pages/SearchPicturePage.vue'
import SpaceAnalyzePage from '@/pages/SpaceAnalyzePage.vue'
import SpaceUserManagePage from '@/pages/admin/SpaceUserManagePage.vue'
import StorageConfigManagePage from '@/pages/admin/StorageConfigManagePage.vue'
import DashboardPage from '@/pages/admin/DashboardPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import AgentChatPage from '@/pages/AgentChatPage.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 独立页面（无布局）
    {
      path: '/user/login',
      name: 'login',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: 'register',
      component: UserRegisterPage,
    },
    // 用户端路由（BasicLayout 包裹）
    {
      path: '/',
      component: BasicLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: HomePage,
          meta: { showFooter: true },
        },
        {
          path: 'add_picture',
          name: 'AddPicture',
          component: AddPicturePage,
        },
        {
          path: 'add_picture/batch',
          name: 'AddPictureBatch',
          component: AddPictureBatchPage,
        },
        {
          path: 'picture/:id',
          name: 'PictureDetail',
          component: PictureDetailPage,
          props: true,
        },
        {
          path: 'add_space',
          name: 'AddSpace',
          component: AddSpacePage,
        },
        {
          path: 'my_space',
          name: 'MySpace',
          component: MySpacePage,
          meta: { showFooter: true },
        },
        {
          path: 'space/:id',
          name: 'SpaceDetail',
          component: SpaceDetailPage,
          props: true,
        },
        {
          path: 'search_picture',
          name: 'SearchPicture',
          component: SearchPicturePage,
          props: true,
        },
        {
          path: 'space_analyze',
          name: 'SpaceAnalyze',
          component: SpaceAnalyzePage,
          props: true,
          meta: { showFooter: true },
        },
        {
          path: 'feedback',
          name: 'Feedback',
          component: () => import('@/pages/FeedbackPage.vue'),
        },
        {
          path: 'spaceUserManage/:spaceId',
          name: 'SpaceUserManage',
          component: SpaceUserManagePage,
          props: true,
        },
        {
          path: 'agent',
          name: 'AgentChat',
          component: AgentChatPage,
          meta: { title: '智能助手', fullScreen: true },
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
          component: DashboardPage,
        },
        {
          path: 'user_manage',
          name: 'UserManage',
          component: UserManagePage,
        },
        {
          path: 'picture_manage',
          name: 'PictureManage',
          component: PictureManagePage,
        },
        {
          path: 'space_manage',
          name: 'SpaceManage',
          component: SpaceManagePage,
        },
        {
          path: 'storage_config_manage',
          name: 'StorageConfigManage',
          component: StorageConfigManagePage,
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
