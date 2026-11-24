# 方向确认 · VisionSpace 小程序视觉改版

日期：2026-11-24
范围：现有 7 个页面的整体视觉改版（`pages/discover` `/picture` `/space` `/space-detail` `/upload` `/me` `/login`），不新增功能。

## 摆给用户看的三版

| 方向 | 文件 | 截图 |
|---|---|---|
| 一 · 暗房（杉本博司式：黑场、单张、只留编号） | `dir-1.offline.html` | `shots/dir-1.png` |
| 二 · 编辑成册（Irma Boom 式：刊头、图版号、图注、页码） | `dir-2.offline.html` | `shots/dir-2.png` |
| 三 · 检索台（Raycast 式：命令面板、结果行、等宽数字） | `dir-3.offline.html` | `shots/dir-3.png` |

另有配色对照版 `dir-1-aurora.html`（暗房换成极光 token）、`dir-2-pop.html`（编辑成册换成紫漾 token），用来回答「和 web 风格不太搭」这个疑问。

## 用户的选择（原话）

> 暗房可以作为暗黑主题呀
> 编辑成册明亮主题
>
> 但是我有一个疑问啊
>
> 当前似乎和web的风格不太搭？

> 收口

「收口」指的是我提出的合并方案：不要「一个主题一套版式」，而是**一套版式、两套配色**——浏览页用「编辑成册」的目录结构（刊头、图版号、图注、页码、空间即一辑），「一屏一张」只留给图片详情页；深色 = 极光，浅色 = 紫漾，字号字体统一。

## 定稿

- 交付文件：`final.offline.html`（断网可开，1.4 MB，浅色/深色右上角切换）
- 源文件：`src/final.src.html` → `python compose.py final`
- 截图：`shots/final.png`（浅色）、`shots/final-dark.png`（深色），单屏 `shots/final-p0..6.png`、`shots/final-dark-p0..6.png`
- 色值来源：`miniprogram/src/styles/tokens.wxss` 与 `frontend/src/styles/design-tokens*.css`（浅色 `#FAF7FF`/`#9333EA`，深色 `#030712`/`#6AA3FF`）
- 字体：系统 PingFang（小程序只能吃系统字体），数字用等宽字
- 校验：`offline.py` 像素差 0.0%；`design_lint.py check` 无红灯（黄灯为有意保留：元信息中点连接、图版号与空间序号、系统字体当标题）

## 落地情况

- 63a3c54 只交设计稿与交付包，当时没有动 `miniprogram/src`。
- 用户在会话里续说「逐个开始行动吧，做完之后做一次 review」，授权了三步：提交设计目录、改 token、按定稿重排七个页面。对应提交 63a3c54 / 77071b9 / b1d3084。
- 定稿与实现的差异（哪些示意数据换成了真实字段、哪些没做）记在 `IMPLEMENTATION.md`。

## 入库范围

`shots/`、拼装出来的 `*.html`（`dir-2/3.html`、`*-pop/aurora.html`、除 `final.offline.html` 外的离线版、`final.html`）与 `_design/screens/` 都不入库，用 `compose.py`、`shot.mjs`、`offline.py`、`mp_handoff.py` 重建。
`final.offline.html` 是交付物，单独保留；方向一的源文件在 `src/dir-1.src.html`，`dir-1.html` 由 `python compose.py dir-1` 重建。
