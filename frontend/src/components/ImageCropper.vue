<template>
  <!--    弹窗组件-->
  <a-modal
    class="image-cropper"
    v-model:open="visible"
    title="编辑图片"
    :footer="false"
    @cancel="closeModal"
  >
    <!--    图片裁切组件-->
    <vue-cropper
      ref="cropperRef"
      :img="imageUrl"
      output-type="png"
      :info="true"
      :can-move="true"
      :can-move-box="true"
      :fixed-box="false"
      :auto-crop="true"
      :center-box="true"
    />
    <div style="margin-bottom: 16px" />
    <!--    协同编辑操作-->
    <div class="image-edit-action">
      <a-space v-if="isTeamSpace">
        <a-button v-if="editingUser" disabled>{{ editingUser.userName }}正在编辑</a-button>
        <a-button v-if="canEnterEdit" type="primary" @click="enterEdit">进入编辑</a-button>
        <a-button v-if="canExistEdit" danger ghost @click="exitEdit">退出编辑</a-button>
      </a-space>
    </div>
    <div style="margin-bottom: 16px" />
    <!--    图片操作-->
    <div class="image-copper-action">
      <a-space>
        <a-button @click="rotateLeft" :disabled="!canEdit">向左旋转</a-button>
        <a-button @click="rotateRight" :disabled="!canEdit">向右旋转</a-button>
        <a-button @click="changeScale(1)" :disabled="!canEdit">放大</a-button>
        <a-button @click="changeScale(-1)" :disabled="!canEdit">缩小</a-button>
        <a-button type="primary" @click="handleConfirm" :loading="loading" :disabled="!canEdit"
          >确认</a-button
        >
      </a-space>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { useLoginUserStore } from '@/stores/userLogin.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebSocket.ts'
import { PICTURE_EDIT_ACTION_ENUM, PICTURE_EDIT_MESSAGE_TYPE_ENUM } from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

interface Props {
  imageUrl?: string
  picture: API.PictureVO
  onSuccess?: (newPicture: API.PictureVO) => void
  space?: API.SpaceVO
  spaceId?: number
}
const props = defineProps<Props>()

const visible = ref<boolean>(false)

//是否为团队空间
const isTeamSpace = computed(() => {
  return props.space?.spaceType === SPACE_TYPE_ENUM.TEAM
})

// 打开弹窗
const openModal = () => {
  visible.value = true
}
// 关闭弹窗
const closeModal = () => {
  visible.value = false
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
}

// 暴露函数给父组件
defineExpose({
  openModal,
})

const cropperRef = ref()

// 图片缩放
const changeScale = (num: number) => {
  cropperRef.value.changeScale(num)
  if (num > 0) {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_IN)
  } else {
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
  }
}
// 向左旋转
const rotateLeft = () => {
  cropperRef.value.rotateLeft()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}
// 向右旋转
const rotateRight = () => {
  cropperRef.value.rotateRight()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
}

// 确认裁切
const handleConfirm = () => {
  cropperRef.value.getCropBlob((blob: Blob) => {
    // blob为已经裁切好的图片
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    handleUpload({ file })
  })
}

const loading = ref<boolean>(false)

// 上传图片的操作
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    if (props.spaceId) {
      params.spaceId = props.spaceId
    }
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    }
  } catch (err: any) {
    message.error('图片上传失败：' + err.message)
  }
  loading.value = false
}

//---------------实时编辑----------------
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

//正在编辑的用户
const editingUser = ref<API.UserVO>()

//当前用户是否可以进入编辑
const canEnterEdit = computed(() => {
  return !editingUser.value
})

//正在编辑的用户是本人，可以退出编辑
const canExistEdit = computed(() => {
  return editingUser.value?.id === loginUser.id
})

//可以点击编辑图片的操作按钮
const canEdit = computed(() => {
  if (!isTeamSpace.value) {
    //如果不是团队空间，那么直接就可以编辑
    return true
  }
  //如果是团队空间，那么只有编辑者可以编辑
  return editingUser.value?.id === loginUser.id
})

//编写webSocket逻辑
let websocket: PictureEditWebSocket | null

//初始化websocket的连接，绑定监听事件
const initWebsocket = () => {
  const pictureId = props.picture?.id
  if (!pictureId || !visible.value) {
    return
  }
  //防止之前的连接未释放
  if (websocket) {
    websocket.disconnect()
  }
  //创建websocket实例
  websocket = new PictureEditWebSocket(pictureId)
  //建立连接
  websocket.connect()

  // 好像还没实现
  // websocket.on('open', (msg) => {
  //   console.log('连接成功', msg)
  //   if (msg.user) {
  //     //同步实时编辑的用户
  //     editingUser.value = msg.user
  //   }
  // })

  //监听一系列事件
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => {
    console.log('收到通知信息', msg)
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => {
    console.log('收到错误通知', msg)
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
    console.log('收到进入编辑状态消息', msg)
    editingUser.value = msg.user
    message.info(msg.message)
    switch (msg.message) {
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT:
        rotateLeft()
        break
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT:
        rotateRight()
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN:
        changeScale(1)
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT:
        changeScale(-1)
        break
    }
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
    console.log('收到编辑操作消息', msg)
    message.info(msg.message)
    editingUser.value = msg.user
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
    console.log('收到退出编辑状态消息', msg)
    message.info(msg.message)
    editingUser.value = undefined
  })
}

watch(visible, () => {
  //只有团队空间才初始化websocket
  if (isTeamSpace.value) initWebsocket()
})

onUnmounted(() => {
  //断开websocket连接
  if (websocket) {
    websocket.disconnect()
  }
  editingUser.value = undefined
})

//进入编辑状态
const enterEdit = () => {
  if (websocket) {
    //发送进入编辑状态的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT,
    })
  }
}

//退出编辑状态
const exitEdit = () => {
  if (websocket) {
    //发送退出编辑状态的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT,
    })
  }
}

//编辑图片操作
const editAction = (action: string) => {
  if (websocket) {
    //发送编辑操作的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION,
      editAction: action,
    })
  }
}
</script>

<style lang="less">
.image-cropper {
  text-align: center;

  .vue-cropper {
    height: 400px !important;
  }
}
</style>
