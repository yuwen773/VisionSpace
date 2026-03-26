import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
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

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
    },
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
    {
      path: '/admin/user_manage',
      name: 'UserManage',
      component: UserManagePage,
    },
    {
      path: '/add_picture',
      name: 'AddPicture',
      component: AddPicturePage,
    },
    {
      path: '/add_picture/batch',
      name: 'AddPictureBatch',
      component: AddPictureBatchPage,
    },
    {
      path: '/admin/picture_manage',
      name: 'PictureManage',
      component: PictureManagePage,
    },
    {
      path: '/picture/:id',
      name: 'PictureDetail',
      component: PictureDetailPage,
      props: true,
    },
    {
      path: '/admin/space_manage',
      name: 'SpaceManage',
      component: SpaceManagePage,
    },
    {
      path: '/add_space',
      name: 'AddSpace',
      component: AddSpacePage,
    },
    {
      path: '/my_space',
      name: 'MySpace',
      component: MySpacePage,
    },
    {
      path: '/space/:id',
      name: 'SpaceDetail',
      component: SpaceDetailPage,
      props: true,
    },
    {
      path: '/search_picture',
      name: 'SearchPicture',
      component: SearchPicturePage,
      props: true,
    },
    {
      path: '/space_analyze',
      name: 'SpaceAnalyze',
      component: SpaceAnalyzePage,
      props: true,
    },
    {
      path: '/spaceUserManage/:spaceId',
      name: 'SpaceUserManage',
      component: SpaceUserManagePage,
      props: true,
    },
    {
      path: '/admin/storage_config_manage',
      name: 'StorageConfigManage',
      component: StorageConfigManagePage,
    },
  ],
})

export default router
