# Grove 开发指南

## 构建

需要 JDK 17+（本机验证使用 JDK 21）、Android SDK 35 与 Build Tools 35.0.0。
通过 Android Studio 打开本目录，或设置 `JAVA_HOME` 和 Android SDK 后执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

SDK 路径放在本机 `local.properties` 的 `sdk.dir`，该文件不提交到 Git。

构建产物：`app/build/outputs/apk/debug/app-debug.apk`。

当前网络访问 Maven Central 会被限流时，可启用 Google 提供的 Central 镜像：

```sh
./gradlew -Pgrove.centralMirror=google :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

### 重新构建阅读器

生成的 HTML、CSS、JavaScript、KaTeX 字体已随工程提交，正常构建 APK 不需要 Node.js 或在线 CDN。
修改 `reader/src/` 后使用 Node.js 22.12+：

```sh
cd reader
npm ci
npm test
npm run build
```

### GitHub 正式发布包

```sh
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintRelease
```

未签名产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。先使用 Android Build Tools 的 `zipalign` 对齐，再使用 `apksigner` 签名并校验。v0.4.1 发布包为不可调试的 Release 构建；为兼容 v0.3.2 / v0.4.0 及已安装的测试包，继续使用原开发签名证书，而非另换应用商店专用证书。签名密钥不进入仓库或发布附件，后续覆盖升级必须保留证书连续性。

## 启用真实登录

应用开发者只需注册一次，普通用户不需要创建应用、填写 Client ID 或提供个人访问令牌。工程默认使用已注册的 Grove 应用；以下步骤仅用于部署自己的应用身份。

1. 到 [GitHub App 注册页](https://github.com/settings/apps/new) 创建 GitHub App（已有 OAuth App 可保留，但不能提供这里所需的细分只读权限）。
2. 启用 **Device Flow**，保留用户令牌过期设置。无需 webhook，可关闭 Active。
3. Repository permissions：**Contents: Read-only、Issues: Read-only、Metadata: Read-only**。Account permissions：**Starring: Read and write**，用于添加 / 取消 Star；仓库内容和 Issues 保持只读，其他权限不申请。
4. 安装范围选择 **Any account**，以便任何用户登录并授权自己的仓库。
5. 从应用设置页取公开的 **Client ID** 和应用链接中的 **slug**，在本机 `~/.gradle/gradle.properties` 设置：

```properties
grove.githubClientId=你的GitHubAppClientID
grove.githubAppSlug=你的应用slug
```

6. GitHub 要求开发者至少生成一份应用私钥后才能安装 App。通过应用设置页生成并妥善离线保管，此私钥不加入工程、APK 或任何用户设备，也不用于移动端 Device Flow。
7. 重新构建 APK。每位用户在「我的」登录，并通过「更多 → 仓库授权」安装 App。选择 **All repositories** 可授权该账号当前及以后新建的全部仓库；组织或其他账号名下的仓库仍需所属账号安装授权。返回「我的项目」刷新。修改 Star 权限后，已授权用户可能需要重新授权。

只有用户本人和 GitHub App 都获准访问的私有仓库可被读取。组织仓库可能需要组织管理员批准安装，并遵守组织的访问策略；仅登录不会自动获得全部私有仓库权限。

Client ID 和 slug 都是公开标识；APK 无需 Client Secret、私钥或后端代理。工程内的公开配置可由 Gradle 属性覆盖。Device Flow 获得的用户令牌可直接用 refresh token 续期。令牌由 Android Keystore 加密保存，不进入日志或文件缓存。退出账号清除该账号的临时附件，切换到独立的访客书库；账号书签仍保留在应用私有存储中，重新登录同一账号后恢复。

参考：[用户令牌与安装授权](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app)、[令牌续期](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens)。

### 文件预览

- 图片：PNG、JPEG、GIF、WebP、BMP、ICO、AVIF、SVG，至多 10 MB。具体解码能力受 Android System WebView 版本影响。
- SVG 默认按预览区宽度等比显示，长图可纵向滚动；双击切换放大与适宽，普通图片保留原尺寸及视口上限。图片尺寸和高度上限使用解码尺寸与实际视口像素，避免部分 WebView 将 `vh` 高度约束算为零而显示空白。
- 预览诊断默认关闭，可在「我的 → 更多 → 开发者选项」开启；开关按本机账号保存，开启后图片和表格页显示诊断入口，关闭后隐藏入口并停止采集诊断状态。
- PDF：至多 20 MB，原生 `PdfRenderer` 逐页显示，支持分页和放大；不支持密码输入、批注和文本检索。
- 表格：XLSX、XLS、CSV、TSV，也可读取含宏的 OOXML 工作簿中的单元格值，至多 10 MB；不执行宏、公式或外部链接，不还原 Excel 图表和全部排版。
- 表格先显示 200 行，可继续加载；每张工作表至多 5,000 行、50 列。压缩工作簿解压上限 50 MB / 3,000 个 ZIP 条目。
- 私有仓库附件和相对图片使用带授权的 GitHub API 获取。外站图片请求不会附带 GitHub 令牌。附件只在应用私有缓存中临时保存，不传给第三方在线文档服务。
- 图片保存支持仓库相对图片、HTTPS 图片和内嵌 data 图片，至多 10 MB。原图预览优先读取现有缓存。用户手动保存后的文件属于设备共享存储，退出登录后继续保留。

## 工程结构

阅读页底部「文件」打开快捷选文件面板，默认定位当前文件所在目录，支持进入文件夹、返回上级、回到根目录、当前目录筛选及当前文件标记。关闭面板保留文档、进度和专注阅读状态；选择新文件后关闭面板并继续阅读。上方「文件」Tab 保留完整文件浏览页。面板使用独立的加载、错误和取消状态，不更改完整文件页的目录，不通过切换 Tab 实现。

底部导航为「足迹、收藏、发现、我的」，足迹页标题为「访问记录」。每个导航在当前账号会话内保存自己的仓库页面、文件目录、阅读 / 源码模式、Issue 详情、阅读进度和返回记录；切换导航不等于返回入口。在足迹内浏览项目时，再次点击已选中的「足迹」可直接回到访问记录首页，保存阅读进度并清空该导航的逐页返回记录；已在首页时重复点击不做操作，其他导航重复点击保持当前页面。页面内返回仍逐层退回入口；个人页的收藏 / 书签快捷入口会明确打开收藏首页。恢复导航时只恢复页面状态，不回滚共享的 Star、书签、访问记录和设置。界面通过 SaveableStateHolder 保留列表及源码滚动位置，PDF 页码与缩放可恢复。加载中切走会取消请求，返回时重试该页面原来的请求；退出或切换账号会清空暂停的页面会话。此页面会话保留不等于进程重启后的全路由恢复，本机阅读位置仍由 ReadingStore 持久保存。

- `app/.../domain/Models.kt`：仓库标识、文档、书签和搜索条件。
- `app/.../data/`：GitHub 请求、公共响应缓存、本机阅读存储与授权。
- `app/.../GroveViewModel.kt`：页面状态、请求取消、分页和阅读恢复。
- `app/.../ui/GroveApp.kt`：Compose 原生界面。
- `app/.../ui/DocumentReader.kt`：本地 WebView、受限原生桥接及附件资源加载。
- `app/.../ui/PdfPreview.kt`：原生 PDF 分页渲染。
- `app/.../data/ImageStore.kt`：图片导出、MediaStore 发布和失败清理。
- `reader/src/`：Markdown 排版、消毒、目录与图表渲染。

## 当前边界

v0.4.1 已作为 GitHub 正式版本发布，使用签名的非调试 Release APK；签名兼容性说明见上文，尚未上架应用商店。
已通过无线 ADB 在 OnePlus 8T（Android 14、WebView 156）验证图片预览零高度问题及修复；PDF、Excel 和其他原生交互仍需分别进行设备验收，浏览器原型不等于原生 UI 的测试结果。

匿名请求受 GitHub 额度限制。公共 API 响应有 ETag 与离线回退缓存；权限错误不会回退旧数据。
目前展示单目录至多 1,000 个条目、文本文件最大 1 MB、分支前 100 个；不支持全仓库内容搜索、多个账号同时登录或跨设备同步。

阅读器只执行本地打包脚本；不可信 Markdown HTML 经 DOMPurify 处理，脚本和表单被移除。Mermaid 使用 strict 模式。网络图片仍需要网络连接。WebView 需支持现代 JavaScript（建议更新 Android System WebView）。

## 验证

JVM 测试覆盖仓库输入、链接解析、书签标识、搜索口径、文件类型分流、账号附件隔离，以及私有响应不落入公共缓存、相对图片通过授权 API 获取、外站图片不携带凭据、下载中切换账号。Star 测试覆盖 GET 状态区分、PUT / DELETE 方法及空请求体、权限错误和访客保护。
阅读器集成测试覆盖恶意 HTML、相对图片/文档、中文重复锚点、表格、任务列表、公式、图表代码块和 XLS/XLSX 多工作表读取。浏览器验证页 `reader/preview.html` 使用实际打包资源，可检查图片和 Excel 交互。
Compose / Robolectric 页面测试覆盖发现页打开项目后返回的列表索引和滚动偏移、底部导航往返、筛选重置、下拉刷新与分页替换、空列表刷新、网络失败及重复刷新保护。网络使用测试响应，运行实际 Compose 页面和 ViewModel。
分页测试覆盖收藏下拉刷新及全部 Star 搜索重建、书签重新读取、各分页列表滚动续载、仅含 PR 的 Issues 页、并发防重、失败暂停与重试以及末页停止。WebView 桥接测试拒绝过期或重复页通知；阅读器测试验证评论追加后保持阅读位置，以及表格追加行保留横向位置。
快捷文件面板测试覆盖保留阅读 Tab、目录导航和筛选、选文件后关闭、重新打开时定位文件目录、错误重试、专注阅读及原文件不重载、关闭后拒绝迟到结果，以及上方完整文件页仍可正常浏览。
图片保存测试覆盖实际 WebView 注册的长按回调、原图预览切换、私有图片请求授权、外站凭据隔离、缓存和内嵌图片、无效资源拒绝、MediaStore 完整写入后发布、SVG 保存到下载目录、文件选择器 URI 写入及失败清理。
SVG 测试覆盖保留原文件字节与 XML 源码、UTF-8 / UTF-16 和中文、较大文件保护，以及实际页面的预览 / 源码切换和剪贴板复制。预览继续使用图片加载方式，源码作为普通文本显示。
最近项目测试覆盖持久化、去重、账号隔离、视图模式保存、排序偏好、置顶 / 取消置顶、移除后保留书签及旧阅读记录迁移。保留最近 30 个未置顶项目；置顶项目独立保留，不随新增访问记录淘汰，置顶组内仍按所选方式排序。完整 Star 搜索测试覆盖跨页、索引复用、失败重试及退出清理。页面测试验证个人页入口、纸面风格更多功能面板、全部项目、最近置顶排序移除、空书签切换、按需展开搜索、完整 Star 搜索与 GitHub 发现搜索。发现搜索保留当前领域、语言及新晋筛选条件；搜索关键词不受推荐列表的 10 Star 门槛限制，仍过滤归档仓库及 Fork，结果受 GitHub 搜索数量限制。`app/build/reports/ui/` 中的图片使用测试数据在 Robolectric 原生图形模式下渲染，不能代替真机截图。
当前未连接 Android 设备；原生 PDF、Android Keystore、真实触摸手感与完整登录仍需在设备上验收。真实私有仓库读取须完成应用权限配置和仓库安装授权后验证，单元测试不能代替此步骤。
