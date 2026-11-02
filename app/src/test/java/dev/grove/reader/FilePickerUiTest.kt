package dev.grove.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import dev.grove.reader.ui.GroveApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.net.HttpURLConnection
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilePickerUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var model:GroveViewModel
    private var failDirectory=false
    @Volatile private var directoryGate:CountDownLatch?=null
    @Volatile private var documentGate:CountDownLatch?=null
    private val directoryStarted=CountDownLatch(1)
    private fun entry(path:String,folder:Boolean=false)=JSONObject().put("path",path).put("type",if(folder)"dir"else"file").put("size",100)
    private fun document(path:String)=JSONObject().put("path",path).put("encoding","base64").put("size",20)
        .put("content",Base64.getEncoder().encodeToString("# ${path.substringAfterLast('/')}\n\nRead quietly.".toByteArray()))
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().commit()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("file-picker-ui").apply{deleteRecursively()},connectionFactory={url->
            val directory=url.path.endsWith("/contents/")||url.path.endsWith("/contents/docs")
            val body=when {
                url.path.endsWith("/readme") -> document("README.md").toString()
                url.path.endsWith("/issues") -> JSONArray().toString()
                url.path.endsWith("/branches") -> JSONArray(listOf(JSONObject().put("name","main"),JSONObject().put("name","feature/reading"))).toString()
                url.path.endsWith("/contents/") -> JSONArray(listOf(entry("docs",true),entry("README.md"),entry("LICENSE"))).toString()
                url.path.endsWith("/contents/docs") -> JSONArray(listOf(entry("docs/guide.md"),entry("docs/notes.md"))).toString()
                url.path.contains("/contents/") -> document(url.path.substringAfter("/contents/")).toString()
                else -> JSONObject().put("name","project").put("owner",JSONObject().put("login","owner")).put("default_branch","main").toString()
            }.toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=if(directory&&failDirectory)500 else 200
                override fun getInputStream():java.io.InputStream {
                    if(directory)directoryGate?.let{directoryStarted.countDown();it.await(10,TimeUnit.SECONDS)}
                    else if(url.path.contains("/contents/"))documentGate?.let{check(it.await(15,TimeUnit.SECONDS))}
                    return body.inputStream()
                }
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.openRepository(RepoId("owner","project"))};awaitLoad()
    }
    @After fun releasePendingDirectory(){directoryGate?.countDown();documentGate?.countDown()}
    private fun awaitLoad() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.waitForIdle()
    }
    private fun awaitPicker() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();model.state.value.filePicker?.loading==false}
        compose.waitForIdle()
    }
    private fun openPicker(){compose.onNodeWithText(" 文件").performClick();awaitPicker()}
    private fun snapshotHeader(name:String,bottom:Float) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,bottom.toInt(),Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun snapshot(name:String="file-picker") {
        compose.runOnIdle {
            val base=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);base.draw(canvas)
            ShadowDialog.getLatestDialog()!!.window!!.decorView.draw(canvas)
            File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun markdownAndTextLoadingShowOnlyABarWithoutDownloadNumbersOrHints() {
        for(path in listOf("docs/guide.md","LICENSE.txt")) {
            val release=CountDownLatch(1);documentGate=release
            try {
                compose.runOnIdle{model.openFile(path)}
                compose.onNodeWithTag("document-loading").assertIsDisplayed()
                compose.onNodeWithTag("file-download").assertDoesNotExist()
                compose.onNodeWithText("文件加载中").assertDoesNotExist()
                compose.onNodeWithText("已下载",substring=true).assertDoesNotExist()
                compose.onNodeWithText("0%").assertDoesNotExist()
                compose.onNodeWithText("可切换页面，下载会继续").assertDoesNotExist()
                compose.onNodeWithText("重试时会优先接着已下载的部分继续").assertDoesNotExist()
                compose.onNodeWithText("我的").assertIsDisplayed()
                if(path.endsWith(".md"))snapshotHeader("markdown-loading-bar-v18",compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom)
                release.countDown();awaitLoad()
                assertEquals(path,model.state.value.document?.path)
                compose.onNodeWithTag("document-loading").assertDoesNotExist()
            }finally{release.countDown();documentGate=null}
        }
    }
    @Test fun bottomFilesOpensPickerWhileKeepingReaderAndDocument() {
        val original=model.state.value.document
        openPicker()
        assertEquals("底部文件入口不能切换到文件 Tab",RepoTab.READ,model.state.value.tab)
        assertEquals(original,model.state.value.document)
        compose.onNodeWithText("选择文件").assertIsDisplayed()
        compose.onNodeWithText("正在阅读").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索文件").assertIsDisplayed()
        snapshot("file-picker-search-collapsed-v14")
    }
    @Test fun folderNavigationAndDismissalPreserveReadingPositionAndFullFilesTab() {
        compose.runOnIdle{model.progress(model.state.value.document!!.key,.42f)}
        val original=model.state.value.document
        openPicker()
        compose.onNodeWithText("docs").performClick();awaitPicker()
        assertEquals("docs",model.state.value.filePicker!!.directory)
        compose.onNodeWithText("guide.md").assertIsDisplayed()
        assertEquals("",model.state.value.directory)
        assertEquals(RepoTab.READ,model.state.value.tab)
        compose.onNode(hasContentDescription("返回") and hasAnyAncestor(isDialog())).performClick();awaitPicker()
        assertEquals("",model.state.value.filePicker!!.directory)
        compose.onNodeWithContentDescription("关闭文件面板").performClick()
        assertNull(model.state.value.filePicker)
        assertEquals(original,model.state.value.document)
        assertEquals(.42f,model.state.value.progress,0.001f)
    }
    @Test fun selectingFileClosesPickerAndNextPickerStartsInThatFolder() {
        openPicker();compose.onNodeWithText("docs").performClick();awaitPicker()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索文件").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("guide")
        compose.onNodeWithText("notes.md").assertDoesNotExist()
        snapshot("file-picker-search-expanded-v14")
        compose.onNodeWithContentDescription("收起文件搜索").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("notes.md").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索文件").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("guide")
        compose.onNodeWithText("guide.md").performClick();awaitLoad()
        assertNull(model.state.value.filePicker)
        assertEquals("docs/guide.md",model.state.value.document!!.path)
        assertEquals(RepoTab.READ,model.state.value.tab)
        openPicker()
        assertEquals("docs",model.state.value.filePicker!!.directory)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("正在阅读").assertIsDisplayed()
    }
    @Test fun pickerFailureAndRetryStayInsideThePanel() {
        val original=model.state.value.document
        failDirectory=true;openPicker()
        assertNotNull(model.state.value.filePicker!!.error)
        assertNull(model.state.value.error)
        assertEquals(original,model.state.value.document)
        failDirectory=false;compose.onNodeWithText("重试").performClick();awaitPicker()
        assertNull(model.state.value.filePicker!!.error)
        compose.onNodeWithText("docs").assertIsDisplayed()
        assertEquals(RepoTab.READ,model.state.value.tab)
    }
    @Test fun immersiveReaderStaysImmersiveAndCurrentFileDoesNotReload() {
        compose.runOnIdle{model.toggleImmersive();model.progress(model.state.value.document!!.key,.55f)}
        val original=model.state.value.document
        openPicker()
        compose.onNode(hasText("README.md") and hasClickAction()).performClick()
        assertNull(model.state.value.filePicker)
        assertSame(original,model.state.value.document)
        assertTrue(model.state.value.immersive)
        assertEquals(.55f,model.state.value.progress,.001f)
        openPicker();compose.onNodeWithText("docs").performClick();awaitPicker()
        compose.onNodeWithText("guide.md").performClick();awaitLoad()
        assertTrue(model.state.value.immersive)
        assertEquals("docs/guide.md",model.state.value.document!!.path)
    }
    @Test fun internalFullDirectoryBrowserStillNavigatesWithoutATopTab() {
        compose.onNodeWithText("文件").assertDoesNotExist()
        compose.runOnIdle{model.loadDirectory("")};awaitLoad()
        assertEquals(RepoTab.FILES,model.state.value.tab)
        assertNull(model.state.value.filePicker)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索文件").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("READ")
        compose.onNodeWithText("docs").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起文件搜索").performClick()
        compose.onNodeWithText("docs").performClick();awaitLoad()
        assertEquals("docs",model.state.value.directory)
        compose.onNodeWithText("guide.md").performClick();awaitLoad()
        assertEquals("docs/guide.md",model.state.value.document!!.path)
    }
    @Test fun repositoryDefaultsToReadingAndIssuesIsAnOnDemandHeaderAction() {
        assertEquals(RepoTab.READ,model.state.value.tab)
        compose.onNodeWithText("阅读").assertDoesNotExist()
        compose.onNodeWithText("文件").assertDoesNotExist()
        compose.onNodeWithText("Issues").assertDoesNotExist()
        compose.onNodeWithText(" 文件").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看 Issues").assertDoesNotExist()
        compose.onNodeWithText("main").assertDoesNotExist()
        snapshotHeader("repository-single-line-header-v6",compose.onNodeWithText("README.md").fetchSemanticsNode().boundsInRoot.bottom+12)
        val original=model.state.value.document!!
        compose.runOnIdle{model.progress(original.key,.6f)}
        compose.onNodeWithText("源码").performClick()
        compose.onNodeWithContentDescription("更多仓库操作").performClick()
        compose.onNodeWithText("当前分支：main").assertIsDisplayed()
        compose.onNodeWithText("切换分支").assertIsDisplayed()
        snapshot("repository-more-actions-v6")
        compose.onNodeWithText("查看 Issues").performClick();awaitLoad()
        assertEquals(RepoTab.ISSUES,model.state.value.tab)
        compose.onNodeWithText("Issues").assertIsDisplayed()
        compose.onNodeWithText("还没有讨论。").assertIsDisplayed()
        snapshotHeader("repository-issues-header-v5",compose.onNodeWithText("Open").fetchSemanticsNode().boundsInRoot.bottom+12)
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(RepoTab.READ,model.state.value.tab)
        assertSame(original,model.state.value.document)
        assertTrue(model.state.value.raw)
        assertEquals(.6f,model.state.value.progress,.001f)
        openPicker()
        compose.onNodeWithText("选择文件").assertIsDisplayed()
        assertEquals(RepoTab.READ,model.state.value.tab)
    }
    @Test fun branchSwitchIsAvailableFromRepositoryMoreAndReturnsToReading() {
        compose.onNodeWithContentDescription("更多仓库操作").performClick()
        compose.onNodeWithText("切换分支").performClick();awaitLoad()
        compose.onNodeWithText("feature/reading").performClick();awaitLoad()
        assertEquals("feature/reading",model.state.value.ref)
        assertEquals("feature/reading",model.state.value.document!!.ref)
        assertEquals(RepoTab.READ,model.state.value.tab)
        compose.onNodeWithContentDescription("更多仓库操作").assertIsDisplayed()
    }
    @Test fun closingWhileLoadingCancelsLateResultsWithoutChangingTheReader() {
        val original=model.state.value.document
        directoryGate=CountDownLatch(1)
        compose.onNodeWithText(" 文件").performClick()
        compose.waitUntil(10_000){directoryStarted.count==0L}
        val job=GroveViewModel::class.java.getDeclaredField("filePickerJob").apply{isAccessible=true}.get(model) as Job
        compose.onNodeWithContentDescription("关闭文件面板").performClick()
        directoryGate!!.countDown()
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();job.isCompleted}
        assertTrue(job.isCancelled)
        assertNull(model.state.value.filePicker)
        assertEquals(original,model.state.value.document)
        assertEquals(RepoTab.READ,model.state.value.tab)
    }
}
