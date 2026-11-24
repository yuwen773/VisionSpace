# VisionSpace 作品集 · 设计交付包

给 WorkBuddy（或写原生微信小程序的人）照着实现用。设计稿按 375 宽画：**1px = 2rpx**，`screens/` 里的图是 750 宽，1 个像素就是 1rpx。
设计稿只管长相和交互；数据、登录、云服务按需求另说。

## 页面

| # | 页面路径 | 标题 | 导航栏 | tab | 截图 |
|---|---|---|---|---|---|
| 1 | `pages/discover/index` |  | 自定义（navigationStyle: custom） | 发现 | `screens/01-discover.png` |
| 2 | `pages/picture/index` |  | 自定义（navigationStyle: custom） | — | `screens/02-picture.png` |
| 3 | `pages/space/index` |  | 自定义（navigationStyle: custom） | 空间 | `screens/03-space.png` |
| 4 | `pages/space-detail/index` |  | 自定义（navigationStyle: custom） | — | `screens/04-space-detail.png` |
| 5 | `pages/upload/index` |  | 自定义（navigationStyle: custom） | — | `screens/05-upload.png` |
| 6 | `pages/me/index` |  | 自定义（navigationStyle: custom） | 我的 | `screens/06-me.png` |
| 7 | `pages/login/index` |  | 自定义（navigationStyle: custom） | — | `screens/07-login.png` |

## app.json 骨架（照这个起，按实际补全）

```json
{
  "pages": [
    "pages/discover/index",
    "pages/picture/index",
    "pages/space/index",
    "pages/space-detail/index",
    "pages/upload/index",
    "pages/me/index",
    "pages/login/index"
  ],
  "window": {
    "navigationBarTitleText": "",
    "navigationStyle": "custom"
  },
  "lazyCodeLoading": "requiredComponents"
}
```

## 设计参数

颜色（按出现次数）：`#1E1B4B`、`#5F6472`、`#9333EA`、`#6B7280`、`#FAF7FF`、`#000000 85%`、`#F5F3FF`、`#DDD6FE`、`#FFFFFF`

字号：16px＝32rpx、12px＝24rpx、14px＝28rpx、13px＝26rpx、15px＝30rpx、24px＝48rpx、17px＝34rpx、26px＝52rpx、34px＝68rpx、28px＝56rpx

圆角：999px＝1998rpx、44px＝88rpx、8px＝16rpx、10px＝20rpx、29px＝58rpx

字体：-apple-system、ui-monospace


完整数据在 `tokens.json`。

## 图片

- `images/01-b9eaf5b490.jpg` 66KB
- `images/02-d835c20039.jpg` 137KB
- `images/03-84aeadfe4c.jpg` 196KB
- `images/04-47e506407b.jpg` 58KB
- `images/05-1b32bdbc6d.jpg` 97KB
- `images/06-30046b3f0c.jpg` 40KB
- `images/07-98fbe0c885.jpg` 82KB
- `images/08-db53ec327d.jpg` 166KB

合计约 842KB。主包上限 2MB：单张超过 200KB 的先压成 JPG；放不下的放云存储，用网络地址。

转发卡片（onShareAppMessage 的 imageUrl，5:4）：`share-1.png`

## 体检

- 没有发现问题

## 实现约定

- 原生小程序（WXML / WXSS / JS），不引 React、不写 HTML 标签；尺寸按 px×2 写成 rpx（或按 375 基准换成 vw）。
- 右上角胶囊是微信的：自定义导航栏时，标题和按钮避开它（用 wx.getMenuButtonBoundingClientRect 取真实位置），别写死。
- 本地图片用 `<image>`，不要写进 WXSS 的 background-image。
- 非系统字体只保留设计里真正用到的字，裁成子集再 wx.loadFontFace；加载失败要能回退到系统字体。
- loading、空状态、出错三种状态照设计稿做；设计稿没画的按同一套颜色和字号补。
- 先用后登录：浏览不要求登录，到了要存数据、下单那一步再登录。弹窗和浮层都要能关。
- 和设计稿做不一样的地方，逐条写下来并说明原因。
