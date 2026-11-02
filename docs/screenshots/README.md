# 界面图

`recent-projects.png`、`favorites-search.png`、`discovery.png`、`profile.png` 和 `profile-more.png` 来自 0.4.1 的 Compose / Robolectric 原生图形测试。

`file-picker.png` 已更新为 0.5.0 的实际选文件面板：搜索默认收起，点击搜索图标展开；仓库标题为单行，分支与 Issues 位于右上角更多菜单。

以上图片使用测试账号与示例项目，不含真实用户数据。它们展示实际 Compose 页面，仍不能替代真机验收；WebView 文档内容需在设备中验证。

README 使用 `overview.png` 展示四页叠放的效果，发现页置于前景。布局源文件为 `../product-overview.html`；截图内容保持原样，通过 HTML / CSS 排版生成组合图。

重新运行原生页面测试可更新原图：

```sh
./gradlew :app:testDebugUnitTest --tests '*ProfileRepositoriesUiTest'
./gradlew :app:testDebugUnitTest --tests 'dev.grove.reader.FilePickerUiTest.bottomFilesOpensPickerWhileKeepingReaderAndDocument'
cp app/build/reports/ui/file-picker-search-collapsed-v14.png docs/screenshots/file-picker.png
```

生成的原图位于 `app/build/reports/ui/`。更新四个首页 PNG 后，用 Chromium 在 1200 × 700、2 倍像素密度下打开 `docs/product-overview.html`，截取整个页面保存为此目录的 `overview.png`。
