# Grove · 墨与纸

一个偏爱阅读的 GitHub 客户端。用暖白纸面、紧凑排版和清晰导航，浏览项目、阅读文档、收藏值得再看的内容。

**目前仅支持 Android 8.0+ · Kotlin + Jetpack Compose · 开发版 0.4.1**

<p align="center">
  <img src="docs/screenshots/overview.png" width="900" alt="Grove 产品界面：收藏搜索、最近访问、发现和个人页，四张实际页面叠放展示">
</p>

<sub>实际 Compose 页面，使用示例数据在 Robolectric 原生图形模式下渲染；尚未完成真机验收。</sub>

## 可以做什么

| 场景 | 功能 |
| --- | --- |
| 阅读项目 | README、目录、文本文件、分支切换；阅读目录、字号、纸色与专注阅读；底部文件面板快捷切换文档 |
| 阅读 Markdown | 表格、任务列表、代码高亮、Mermaid、KaTeX 公式与相对图片链接 |
| 查看附件 | 图片缩放与长按保存、SVG 预览 / 源码切换、PDF 分页、Excel / CSV 多工作表 |
| 管理收藏 | GitHub Star 添加与移除；点击右上角搜索全部 Star、本机文档书签；下拉刷新、上拉加载 |
| 浏览发现 | 按领域和语言筛选高星项目、近 30 天新项目；支持 GitHub 项目搜索，返回时保留滚动位置 |
| 接着阅读 | 最近访问可置顶、排序和移除；按账号记住列表 / 卡片模式、书签与阅读位置 |
| 查看讨论 | 只读 Issues、Open / Closed 筛选、详情与评论滚动加载 |

仓库页目前展示最近访问；登录后的全部账号项目位于「我的 → 我的项目」。访客可以阅读公开仓库，自己的 GitHub 账号授权后可访问获准的私有仓库。

## 下载体验版

- [下载 Android APK · v0.4.1](https://github.com/yunxiange/grove/releases/download/v0.4.1/Grove-0.4.1-debug.apk)
- [版本说明与附件](https://github.com/yunxiange/grove/releases/tag/v0.4.1) · [SHA-256 校验文件](https://github.com/yunxiange/grove/releases/download/v0.4.1/Grove-0.4.1-SHA256.txt)

目前提供使用开发签名的预发布 APK，支持 Android 8.0+；尚未完成真机验收。下载后可在 Android 设备上直接安装。

## 本地构建

需要 **JDK 17+、Android SDK 35、Build Tools 35.0.0**。用 Android Studio 打开项目，或设置好 `JAVA_HOME` 与 Android SDK 后执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。阅读器资源已打包进工程，普通 Android 构建无需 Node.js。

修改 `reader/src/` 时，需要 Node.js 22.12+，在 `reader/` 内执行 `npm ci && npm test && npm run build`。

- [开发指南：构建、GitHub 授权、文件预览与测试](docs/development.md)
- [界面图来源与叠放布局](docs/screenshots/README.md)

## 当前状态

这是可安装的开发版本，尚未发布到应用商店。普通用户无需创建 GitHub App 或填写 Client ID；Star 写入和私有仓库读取需要 GitHub App 的相应权限与安装授权，当前仍需完成配置核验及真实设备验收。私有仓库仅在用户与应用均获准访问时可读，组织仓库可能需要管理员批准。

项目内容和 Issues 为只读，Star 支持写入。本机书签和阅读记录暂不支持跨设备同步。GitHub 登录凭据由 Android Keystore 加密保存；工程中的 Client ID 和应用 slug 是公开标识，APK 不包含应用私钥或 Client Secret。

匿名访问受 GitHub API 额度限制。预览大小、表格行列与分支数量等边界见[开发指南](docs/development.md#文件预览)。
