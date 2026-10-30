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
                    return body.inputStream()
                }
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.openRepository(RepoId("owner","project"))};awaitLoad()
    }
    @After fun releasePendingDirectory(){directoryGate?.countDown()}
    private fun awaitLoad() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.waitForIdle()
    }
    private fun awaitPicker() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();model.state.value.filePicker?.loading==false}
        compose.waitForIdle()
    }
    private fun openPicker(){compose.onNodeWithText(" 文件").performClick();awaitPicker()}
    private fun snapshot() {
        compose.runOnIdle {
            val base=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);base.draw(canvas)
            ShadowDialog.getLatestDialog()!!.window!!.decorView.draw(canvas)
            File("build/reports/ui/file-picker.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun bottomFilesOpensPickerWhileKeepingReaderAndDocument() {
        val original=model.state.value.document
        openPicker()
        assertEquals("底部文件入口不能切换到文件 Tab",RepoTab.READ,model.state.value.tab)
        assertEquals(original,model.state.value.document)
        compose.onNodeWithText("选择文件").assertIsDisplayed()
        compose.onNodeWithText("正在阅读").assertIsDisplayed()
        snapshot()
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
        compose.onNode(hasSetTextAction()).performTextInput("guide")
        compose.onNodeWithText("notes.md").assertDoesNotExist()
        compose.onNodeWithText("guide.md").performClick();awaitLoad()
        assertNull(model.state.value.filePicker)
        assertEquals("docs/guide.md",model.state.value.document!!.path)
        assertEquals(RepoTab.READ,model.state.value.tab)
        openPicker()
        assertEquals("docs",model.state.value.filePicker!!.directory)
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
    @Test fun topFilesTabStillOpensTheFullBrowser() {
        compose.onNodeWithText("文件").performClick();awaitLoad()
        assertEquals(RepoTab.FILES,model.state.value.tab)
        assertNull(model.state.value.filePicker)
        compose.onNodeWithText("docs").performClick();awaitLoad()
        assertEquals("docs",model.state.value.directory)
        compose.onNodeWithText("guide.md").performClick();awaitLoad()
        assertEquals("docs/guide.md",model.state.value.document!!.path)
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
