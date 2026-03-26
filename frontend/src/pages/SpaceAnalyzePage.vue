<template>
  <div id="spaceAnalyzePage">
    <h2 style="margin: 16px 5px">
      空间图库分析 -
      <span v-if="queryAll">全部空间</span>
      <span v-else-if="queryPublic">公共空间</span>
      <span v-else>
        <a :href="`/space/${spaceId}`" target="_blank">空间ID{{ spaceId }}</a>
      </span>
    </h2>
    <a-row :gutter="[16, 16]">
      <!--      空间使用分析-->
      <a-col :xs="24" :md="12">
        <SpaceUsageAnalyze :spaceId="spaceId" :query-all="queryAll" :query-public="queryPublic" />
      </a-col>
      <!--      空间分类分析-->
      <a-col :xs="24" :md="12">
        <SpaceCategoryAnalyze
          :spaceId="spaceId"
          :query-all="queryAll"
          :query-public="queryPublic"
        />
      </a-col>
      <!--      空间标签分析-->
      <a-col :xs="24" :md="12">
        <SpaceTagAnalyze :spaceId="spaceId" :query-all="queryAll" :query-public="queryPublic" />
      </a-col>
      <!--      图片大小分析-->
      <a-col :xs="24" :md="12">
        <SpaceSizeAnalyze :spaceId="spaceId" :query-all="queryAll" :query-public="queryPublic" />
      </a-col>
      <!--      用户上传分析-->
      <a-col :xs="24" :md="12">
        <SpaceUserAnalyze :spaceId="spaceId" :query-all="queryAll" :query-public="queryPublic" />
      </a-col>
      <!--      空间排行分析-->
      <a-col :xs="24" :md="12">
        <SpaceRankAnalyze
          v-if="isAdmin"
          :spaceId="spaceId"
          :query-all="queryAll"
          :query-public="queryPublic"
        />
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import SpaceUsageAnalyze from '@/components/analyze/SpaceUsageAnalyze.vue'
import SpaceCategoryAnalyze from '@/components/analyze/SpaceCategoryAnalyze.vue'
import SpaceTagAnalyze from '@/components/analyze/SpaceTagAnalyze.vue'
import SpaceSizeAnalyze from '@/components/analyze/SpaceSizeAnalyze.vue'
import SpaceUserAnalyze from '@/components/analyze/SpaceUserAnalyze.vue'
import SpaceRankAnalyze from '@/components/analyze/SpaceRankAnalyze.vue'
import { useRoute } from 'vue-router'
import { computed } from 'vue'
import { useLoginUserStore } from '@/stores/userLogin.ts'

const route = useRoute()

const spaceId = computed(() => {
  return route.query?.spaceId as string
})

const queryAll = computed(() => {
  return !!route.query?.queryAll
})

const queryPublic = computed(() => {
  return !!route.query?.queryPublic
})

// 判断是否为管理员
const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})
</script>

<style scoped lang="less">
#spaceAnalyzePage {
  margin-bottom: 60px;
}
</style>
