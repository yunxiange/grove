package dev.grove.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.ReadingStore
import dev.grove.reader.domain.*
import dev.grove.reader.ui.GroveApp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubAndCacheUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app:Application
    private lateinit var model:GroveViewModel
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("login","reader").putString("accountId","100").putString("token","encrypted-test-fixture").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        app.cacheDir.listFiles().orEmpty().forEach{it.deleteRecursively()}
        model=GroveViewModel(app)
        installBook(EpubFixture.bytes())
        compose.setContent{GroveApp(model)}
    }
    private fun installBook(bytes:ByteArray) {
        val api=GitHubClient(app.cacheDir.resolve("github"),account={"100"},connectionFactory={url->
            val json=JSONObject().put("path","books/reading.epub").put("size",bytes.size).put("encoding","base64")
                .put("content",Base64.getEncoder().encodeToString(bytes)).toString().toByteArray()
            object:HttpURLConnection(url){
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream()=json.inputStream()
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
    }
    private fun awaitText(text:String) {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun awaitChapter(position:Int) {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();compose.onAllNodesWithContentDescription("章节目录，第 $position 章，共 2 章").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun snapshot(name:String)=compose.runOnIdle {
        val view=compose.activity.window.decorView
        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);view.draw(canvas)
        ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?.draw(canvas)
        File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun sectionSnapshot(tag:String,name:String) {
        val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val image=Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.translate(-bounds.left,-bounds.top)
            compose.activity.window.decorView.draw(canvas)
            File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    private fun webView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{webView(view.getChildAt(it))}else null
    private fun awaitReaderPath(path:String) {
        compose.waitUntil(10_000){var ready=false;compose.runOnIdle{
            val reader=webView(compose.activity.window.decorView)
            if(reader!=null) {
                val payload=reader.javaClass.getDeclaredField("payload").apply{isAccessible=true}.get(reader) as String
                ready=JSONObject(payload).getString("id").endsWith(path)
            }
        };ready}
    }
    @Test fun coverAndPrefaceDoNotShowChapterTitlesBeforeTheBodyChapters() {
        installBook(EpubFixture.bytes(EpubFixture.frontMatterEntries()))
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.epub"))}
        awaitText("封面");awaitReaderPath("text/front.xhtml")
        compose.onNodeWithText("第 1 章").assertDoesNotExist()
        compose.onNodeWithText("第一章").assertDoesNotExist()
        sectionSnapshot("epub-chapter-header","epub-front-matter-cover-header-v8")
        compose.onNodeWithContentDescription("下一章").performClick()
        awaitText("序言");awaitReaderPath("text/preface.xhtml")
        compose.onNodeWithText("第 2 章").assertDoesNotExist()
        compose.onNodeWithText("第一章").assertDoesNotExist()
        compose.onNodeWithContentDescription("下一章").performClick()
        awaitText("第一章");awaitReaderPath("text/one.xhtml")
        sectionSnapshot("epub-chapter-header","epub-body-chapter-one-header-v8")
        compose.onNodeWithContentDescription("下一章").performClick()
        awaitText("第二章");awaitReaderPath("text/two.xhtml")
        compose.onNodeWithContentDescription("上一章").performClick()
        awaitText("第一章");awaitReaderPath("text/one.xhtml")
    }
    private fun installPausedDownload(release:CountDownLatch,finished:CountDownLatch) {
        val bytes=EpubFixture.bytes()
        val api=GitHubClient(app.cacheDir.resolve("github"),account={"100"},connectionFactory={url->object:HttpURLConnection(url){
            private val raw get()=getRequestProperty("Accept")=="application/vnd.github.raw+json"
            override fun connect()=Unit
            override fun disconnect(){if(raw)finished.countDown()}
            override fun usingProxy()=false
            override fun getResponseCode()=200
            override fun getContentLengthLong()=bytes.size.toLong()
            override fun getInputStream():java.io.InputStream {
                if(raw)return object:java.io.FilterInputStream(bytes.inputStream()) {
                    override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                        check(release.await(30,TimeUnit.SECONDS)){"Test download was not released"}
                        return super.read(buffer,offset,length)
                    }
                }
                return JSONObject().put("path","books/large.epub").put("size",bytes.size).put("encoding","none").toString().byteInputStream()
            }
        }})
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
    }
    @Test fun epubDownloadShowsRealProgressAndClearsItWhenReady() {
        val release=CountDownLatch(1);val finished=CountDownLatch(1)
        installPausedDownload(release,finished)
        try {
            compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/large.epub"))}
            awaitText("0%")
            compose.onNodeWithText("0%").assertIsDisplayed()
            assertEquals(EpubFixture.bytes().size.toLong(),model.state.value.downloadProgress!!.totalBytes)
            release.countDown()
            awaitChapter(1)
            compose.onNodeWithTag("file-download").assertDoesNotExist()
            assertNull(model.state.value.downloadProgress)
        }finally{release.countDown()}
    }
    @Test fun backDuringDownloadRestoresPreviousDocumentAndRejectsLateUpdates() {
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.epub"))}
        awaitChapter(1)
        val release=CountDownLatch(1);val finished=CountDownLatch(1)
        installPausedDownload(release,finished)
        try {
            compose.runOnIdle{model.openFile("books/large.epub")}
            awaitText("0%")
            compose.runOnIdle{model.back()}
            release.countDown()
            compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();finished.count==0L}
            awaitChapter(1)
            compose.onNodeWithTag("file-download").assertDoesNotExist()
            assertEquals("books/reading.epub",model.state.value.document!!.path)
            assertNull(model.state.value.downloadProgress)
            assertFalse(app.cacheDir.walkTopDown().any{it.extension=="part"})
        }finally{release.countDown()}
    }
    @Test fun switchingEpubChaptersKeepsTheExistingWebView() {
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.epub"))}
        awaitChapter(1)
        compose.waitUntil(10_000){var found=false;compose.runOnIdle{found=webView(compose.activity.window.decorView)!=null};found}
        lateinit var original:WebView
        compose.runOnIdle{original=webView(compose.activity.window.decorView)!!}
        compose.onNodeWithContentDescription("下一章").performClick()
        awaitChapter(2)
        compose.waitUntil(10_000){var found=false;compose.runOnIdle{found=webView(compose.activity.window.decorView)!=null};found}
        compose.runOnIdle{assertSame("切换章节应保留阅读器，不能销毁后重载整个 WebView",original,webView(compose.activity.window.decorView))}
        compose.waitUntil(10_000){var ready=false;compose.runOnIdle{
            val payload=original.javaClass.getDeclaredField("payload").apply{isAccessible=true}.get(original) as String
            ready=JSONObject(payload).getString("id").endsWith("two.xhtml")
        };ready}
        fun progress(id:String,value:Float) {
            compose.runOnIdle {
                val bridge=shadowOf(original).getJavascriptInterface("Grove")
                bridge.javaClass.getDeclaredMethod("progress",String::class.java,Float::class.javaPrimitiveType).apply{isAccessible=true}.invoke(bridge,id,value)
            }
            compose.waitForIdle()
        }
        progress("owner/repo:main:books/reading.epub/OPS/text/one.xhtml",.8f)
        assertEquals("旧章节迟到的回调不应覆盖新章节进度",.5f,model.state.value.progress,.001f)
        progress("owner/repo:main:books/reading.epub/OPS/text/two.xhtml",.5f)
        assertEquals(.75f,model.state.value.progress,.001f)
        compose.onNodeWithContentDescription("上一章").performClick()
        awaitChapter(1)
        compose.waitForIdle()
        compose.runOnIdle{assertSame(original,webView(compose.activity.window.decorView))}
    }
    @Test fun epubSwipeBridgeTurnsChaptersAndRejectsStaleOrOutOfRangeRequests() {
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.epub"))}
        awaitChapter(1);awaitReaderPath("text/one.xhtml")
        lateinit var original:WebView
        compose.runOnIdle{original=webView(compose.activity.window.decorView)!!}
        fun swipe(path:String,direction:Int) {
            compose.runOnIdle {
                val bridge=shadowOf(original).getJavascriptInterface("Grove")
                bridge.javaClass.getDeclaredMethod("swipePage",String::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}
                    .invoke(bridge,"owner/repo:main:books/reading.epub/OPS/text/$path.xhtml",direction)
            }
            compose.waitForIdle()
        }
        swipe("one",-1);compose.onNodeWithContentDescription("上一章").assertIsNotEnabled()
        swipe("one",1);awaitChapter(2);awaitReaderPath("text/two.xhtml")
        swipe("one",-1);swipe("two",0);swipe("two",1)
        compose.onNodeWithContentDescription("下一章").assertIsNotEnabled()
        swipe("two",-1);awaitChapter(1);awaitReaderPath("text/one.xhtml")
        compose.runOnIdle{assertSame(original,webView(compose.activity.window.decorView))}
    }
    @Test fun epubOpensFromRepositoryHasContentsAndSavesWholeBookProgress() {
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.epub"))}
        awaitChapter(1)
        compose.onNodeWithText("1 / 2 章").assertDoesNotExist()
        compose.onNodeWithContentDescription("上一章").assertIsNotEnabled()
        compose.onNodeWithContentDescription("下一章").assertIsEnabled()
        val nextBounds=compose.onNodeWithContentDescription("下一章").fetchSemanticsNode().boundsInRoot
        val readerHeader=compose.onNodeWithText("reading.epub").fetchSemanticsNode().boundsInRoot
        assertTrue("切章操作应恢复到顶部",nextBounds.top>readerHeader.bottom&&nextBounds.top<readerHeader.bottom+64)
        assertTrue("切章按钮应有至少 48dp 的点击高度",nextBounds.height>=48)
        val directoryBounds=compose.onNodeWithText(" 目录",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val progressBounds=compose.onNodeWithText("全书约0%",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("进度应与目录保持同一行",kotlin.math.abs(directoryBounds.center.y-progressBounds.center.y)<4)
        val countBounds=compose.onNodeWithText("1/2章",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("章节数应放到底部进度区",countBounds.top>readerHeader.bottom+200)
        assertTrue("章节数和百分比应同排",kotlin.math.abs(countBounds.center.y-progressBounds.center.y)<4)
        compose.onNodeWithText("全书约0%").assertHasNoClickAction()
        compose.onNodeWithContentDescription("阅读设置").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多阅读选项").assertDoesNotExist()
        compose.onNodeWithText("本章 0%").assertIsDisplayed()
        snapshot("epub-reader-restored-v4")
        sectionSnapshot("epub-chapter-header","epub-chapter-header-v4")
        sectionSnapshot("epub-reading-toolbar","epub-reading-status-v4")
        compose.onNodeWithText(" 目录").performClick()
        compose.onNodeWithText("纸上漫游").assertIsDisplayed()
        compose.onNodeWithText("1 / 2 章").assertIsDisplayed()
        snapshot("epub-contents")
        compose.onNodeWithText("第二章").performClick()
        awaitChapter(2)
        compose.onNodeWithContentDescription("下一章").assertIsNotEnabled()
        compose.onNodeWithContentDescription("上一章").assertIsEnabled()
        compose.waitUntil(10_000){var found=false;compose.runOnIdle{found=webView(compose.activity.window.decorView)!=null};found}
        compose.runOnIdle {
            val view=webView(compose.activity.window.decorView)!!
            val bridge=shadowOf(view).getJavascriptInterface("Grove")
            bridge.javaClass.getDeclaredMethod("progress",String::class.java,Float::class.javaPrimitiveType).apply{isAccessible=true}
                .invoke(bridge,"owner/repo:main:books/reading.epub/OPS/text/two.xhtml",.5f)
        }
        compose.waitForIdle()
        compose.runOnIdle{model.flushProgress()}
        assertEquals(.75f,model.state.value.progress,.001f)
        compose.onNodeWithText("本章 50%").assertIsDisplayed()
        compose.onNodeWithText("全书约75%").assertIsDisplayed()
        sectionSnapshot("epub-chapter-progress","epub-chapter-progress-v4")
        sectionSnapshot("epub-reading-toolbar","epub-reading-status-v4-progress")
        assertEquals("books/reading.epub",ReadingStore(app,"100").resume()!!.path)
        assertEquals(.75f,ReadingStore(app,"100").resume()!!.progress,.001f)
        compose.onNodeWithContentDescription("收藏文档").performClick()
        assertEquals("books/reading.epub",model.state.value.bookmarks.single().path)
        compose.onNodeWithText("我的").performClick();compose.onNodeWithText("足迹").performClick()
        awaitChapter(2)
    }
    @Test fun cachePanelClearsFilesButKeepsAccountBookmarksVisitsProgressAndSettings() {
        val store=ReadingStore(app,"100")
        val bookmark=Bookmark(RepoId("owner","repo"),"main","README.md",.4f)
        store.saveBookmarks(listOf(bookmark));store.saveProgress(bookmark);store.recordVisit(bookmark.repo);store.fontSize=21
        app.cacheDir.resolve("github/attachments/book").apply{parentFile!!.mkdirs();writeBytes(ByteArray(1024))}
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithContentDescription("更多功能").performClick()
        compose.onNodeWithText("缓存管理").performClick()
        awaitText("1.0 KB")
        snapshot("cache-management")
        compose.onNodeWithText("一键清理").performClick()
        awaitText("缓存已清理。")
        compose.onNodeWithText("0 B").assertIsDisplayed()
        assertEquals("encrypted-test-fixture",app.getSharedPreferences("grove.account",0).getString("token",null))
        assertEquals("reader",model.state.value.username)
        assertEquals(listOf(bookmark),store.bookmarks());assertEquals(bookmark,store.resume())
        assertEquals(bookmark.repo,store.recentVisits().single().repo);assertEquals(21,store.fontSize)
    }
}
