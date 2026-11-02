package dev.grove.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.ui.PdfPreview
import dev.grove.reader.ui.PdfToolbar
import dev.grove.reader.ui.ReaderDocumentHeader
import dev.grove.reader.ui.Paper
import dev.grove.reader.ui.GroveApp
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPreviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun pdfDownloadStaysInTheContentAreaAndContinuesAcrossBottomTabs() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("login","reader").putString("accountId","100").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        val source=PdfFixture.write(File("build/reports/fixtures/pdf-download-source.pdf").apply{parentFile!!.mkdirs()})
        val release=CountDownLatch(1);val finished=CountDownLatch(1)
        val api=GitHubClient(app.cacheDir.resolve("github"),token={"test-token"},account={"100"},connectionFactory={url->object:HttpURLConnection(url){
            override fun connect()=Unit
            override fun disconnect(){if(getRequestProperty("Accept")=="application/vnd.github.raw+json")finished.countDown()}
            override fun usingProxy()=false
            override fun getResponseCode()=200
            override fun getContentLengthLong()=source.length()
            override fun getInputStream():java.io.InputStream {
                if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return object:java.io.FilterInputStream(source.inputStream()) {
                    override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                        check(release.await(30,TimeUnit.SECONDS)){"Test download was not released"}
                        return super.read(bytes,offset,length)
                    }
                }
                return JSONObject().put("path","guide.pdf").put("size",source.length()).put("encoding","none").toString().byteInputStream()
            }
        }})
        val model=GroveViewModel(app)
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        try {
            compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","guide.pdf"))}
            compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();model.state.value.downloadProgress!=null}
            compose.onNodeWithText("0%").assertIsDisplayed()
            compose.onNodeWithText("文件加载中").assertIsDisplayed()
            compose.onNodeWithText("正在下载 PDF").assertDoesNotExist()
            compose.onNodeWithText("guide.pdf").assertIsDisplayed()
            compose.runOnIdle{model.toggleImmersive()}
            compose.onNodeWithText("我的").assertIsDisplayed()
            assertEquals(PreviewKind.PDF,model.state.value.downloadProgress!!.kind)
            val bounds=compose.onNodeWithTag("file-download").fetchSemanticsNode().boundsInRoot
            val region=compose.onNodeWithTag("file-loading-region").fetchSemanticsNode().boundsInRoot
            val navigation=compose.onNodeWithText("我的").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(bounds.top>=region.top&&bounds.bottom<=region.bottom)
            org.junit.Assert.assertTrue(region.bottom<=navigation.top)
            compose.runOnIdle {
                val image=Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
                val canvas=Canvas(image);canvas.translate(-bounds.left,-bounds.top)
                compose.activity.window.decorView.draw(canvas)
                File("build/reports/ui/pdf-content-download-v11.png").apply{parentFile!!.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
                val view=compose.activity.window.decorView
                val full=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                view.draw(Canvas(full))
                File("build/reports/ui/pdf-file-loading-simplified-v13.png").outputStream().use{full.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            compose.onNodeWithText("我的").performClick()
            org.junit.Assert.assertEquals(1L,finished.count)
            compose.onNodeWithText("足迹").performClick()
            compose.onNodeWithText("文件加载中").assertIsDisplayed()
            compose.onNodeWithText("0%").assertIsDisplayed()
            compose.onNodeWithText("我的").performClick()
            release.countDown()
            compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();finished.count==0L}
            compose.onNodeWithTag("file-download").assertDoesNotExist()
            assertEquals(null,model.state.value.downloadProgress)
            assertEquals(null,model.state.value.document)
            assertEquals(Destination.PROFILE,model.state.value.destination)
            org.junit.Assert.assertFalse(app.cacheDir.resolve("github").walkTopDown().any{it.extension=="part"})
            org.junit.Assert.assertTrue(app.cacheDir.resolve("github/attachments").walkTopDown().any{it.isFile&&it.length()==source.length()})
        }finally{release.countDown()}
    }
    @Test fun zoomUsesTenPercentStepsInBothDirectionsAndCanResetWithoutCycling() {
        val zoom=mutableIntStateOf(100);val page=mutableIntStateOf(0)
        val immersive=mutableStateOf(false)
        val doc=Document(RepoId("owner","repo"),"main","guide.pdf","",Attachment(PreviewKind.PDF,"application/pdf","/unused-test.pdf"))
        compose.setContent{MaterialTheme{Column(Modifier.fillMaxWidth().background(Paper)){
            ReaderDocumentHeader(doc,false,immersive.value,false,{},{immersive.value=!immersive.value},{},zoom.intValue,{zoom.intValue=it})
            HorizontalDivider()
            PdfToolbar(page.intValue,12,{page.intValue=it})
        }}}
        compose.onNodeWithContentDescription("缩小 10%").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上一页").assertIsNotEnabled()
        compose.onNodeWithContentDescription("放大 10%").performClick()
        compose.onNodeWithText("110%").assertIsDisplayed()
        compose.onNodeWithContentDescription("缩小 10%").performClick()
        compose.onNodeWithText("100%").assertIsDisplayed()
        repeat(3){compose.onNodeWithContentDescription("放大 10%").performClick()}
        compose.onNodeWithText("130%").assertIsDisplayed()
        compose.onNodeWithContentDescription("下一页").performClick()
        compose.onNodeWithText("2 / 12").assertIsDisplayed()
        compose.onNodeWithText("130%").assertIsDisplayed()
        val zoomBounds=compose.onNodeWithTag("pdf-zoom-controls").fetchSemanticsNode().boundsInRoot
        val fullscreenBounds=compose.onNodeWithContentDescription("专注阅读").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("PDF 缩放控件应紧靠全屏左侧",zoomBounds.right<=fullscreenBounds.left)
        org.junit.Assert.assertTrue(kotlin.math.abs(zoomBounds.center.y-fullscreenBounds.center.y)<2)
        compose.onNode(hasContentDescription("放大 10%") and hasAnyAncestor(hasTestTag("pdf-toolbar"))).assertDoesNotExist()
        val bounds=compose.onNodeWithTag("pdf-toolbar").fetchSemanticsNode().boundsInRoot
        val headerBounds=compose.onNodeWithTag("reader-document-header").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val image=Bitmap.createBitmap(headerBounds.width.toInt(),(bounds.bottom-headerBounds.top).toInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.translate(-headerBounds.left,-headerBounds.top)
            compose.activity.window.decorView.draw(canvas)
            File("build/reports/ui/pdf-tight-zoom-v17.png").apply{parentFile!!.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        compose.onNodeWithContentDescription("专注阅读").performClick()
        compose.onNodeWithText("130%").assertIsDisplayed()
        compose.onNodeWithContentDescription("退出专注阅读").performClick()
        compose.onNodeWithText("130%").assertIsDisplayed()
        compose.runOnIdle{zoom.intValue=290}
        compose.onNodeWithContentDescription("放大 10%").performClick()
        compose.onNodeWithText("300%").assertIsDisplayed()
        compose.onNodeWithContentDescription("放大 10%").assertIsNotEnabled()
        compose.onNodeWithText("300%").performClick()
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithContentDescription("缩小 10%").assertIsNotEnabled()
        assertEquals(1,page.intValue)
    }
    @Test fun aMissingCacheFileShowsAnErrorInsteadOfAnEmptyPage() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val file=app.cacheDir.resolve("missing-preview.pdf").apply{delete()}
        compose.setContent{PdfPreview(file,Modifier.fillMaxSize())}
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();compose.onAllNodesWithText("PDF 无法读取，文件可能已损坏。").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("PDF 无法读取，文件可能已损坏。").assertIsDisplayed()
    }
}
