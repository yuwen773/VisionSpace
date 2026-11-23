# VisionSpace 微信小程序

原生小程序 + TypeScript。当前为 **M2：公开图库**，包含：

- 发现 tab：热门 / 最新 / 精选 / 随机榜单、关键词搜索、分类筛选
- 作品详情：大图预览、作者与元信息、只读计数、公开作品分享
- 匿名行为上报（曝光 / 点击 / 浏览），来源标记为 `MINI_PROGRAM(4)`
- 跟随系统主题：深色极光 / 浅色紫漾

登录绑定、空间浏览与上传在后续里程碑（M3 / M4）实现。

## 本地开发

1. 安装依赖（只用于类型定义与单测；小程序代码由开发者工具的 TypeScript 编译插件处理）：

   ```bash
   cd miniprogram
   npm install
   ```

2. 用微信开发者工具「导入项目」，目录选 `miniprogram/`，把 `project.config.json`
   里的 `appid` 换成自己的 AppID（`touristappid` 仅游客模式）。

3. 请求域名：
   - 默认直连 `https://visionspace.space`（已 ICP 备案，体验版同样要求备案域名）。
   - 想连本地后端时，在开发者工具控制台执行
     `wx.setStorageSync('vs_api_base', 'http://127.0.0.1:8081')`，并在「详情 →
     本地设置」勾选「不校验合法域名」。清除该存储项即恢复默认。

4. 合法域名配置（开发管理 → 开发设置 → 服务器域名）：把
   `https://visionspace.space` 加到 request 合法域名。图片通过同一域名加载，
   `<image>` 也需要该域名可访问。

## 检查

```bash
npm run type-check   # src 与 tests 两套 tsconfig
npm test             # Vitest，无 DOM 纯逻辑
```

CI 见 `.github/workflows/miniprogram.yml`，只跑类型检查与逻辑单测。小程序没有
浏览器自动化，UI 走下面的真机清单。

## M2 真机清单

发现页：

- [ ] 首次进入默认「热门」榜单，卡片双列排布，图片不变形
- [ ] 依次切换 最新 / 精选 / 随机：列表重置、无重复条目
- [ ] 上拉加载更多：分页无重复；到底显示「没有更多了」
- [ ] 关键词搜索：回车触发，结果来自检索接口；清空关键词回到当前榜单
- [ ] 分类筛选：点分类立即刷新；切榜单会清空关键词与分类
- [ ] 下拉刷新：回到当前榜单/筛选条件的第一页
- [ ] 断网或后端 5xx：显示错误文案与「重新加载」，恢复后重试成功
- [ ] 图片无内容入口（isolated / 无文件）时显示占位，不出现破图

详情页：

- [ ] 大图按宽度铺满，点击进入系统预览并可缩放
- [ ] 标题、作者、分类、日期、文件体积展示正确
- [ ] 计数（浏览/点赞/收藏/分享）只读展示，无点赞按钮
- [ ] 公开作品出现「分享给朋友」，转发卡片打开后仍能看到该作品
- [ ] 非公开作品（构造 `spaceId` 数据）不出现分享入口，匿名打开给出无权限提示

主题：

- [ ] 系统切深色：导航栏、tabBar、页面背景、卡片、文字全部跟随极光配色
- [ ] 系统切浅色：恢复紫漾配色，无残留深色块

埋点：

- [ ] 后端 `picture_action` 中出现 `source=4` 的曝光/点击/浏览记录
- [ ] 同一张图在一次页面停留中不重复上报曝光

## 目录

```
src/
  app.ts / app.json / app.wxss / theme.json / sitemap.json
  config/env.ts            API 域名、版本号
  services/                请求层、内容地址、登录令牌、线格式类型
  logic/                   feed（分页状态机）、gallery（展示）、telemetry（埋点）、errors
  components/picture-card/ 作品卡片
  pages/discover|picture|me
tests/                     Vitest
```

`logic/` 与 `services/` 不依赖页面生命周期，纯逻辑改动应同时补 `tests/`。
