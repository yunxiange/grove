package dev.grove.reader

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.ReadingStore
import dev.grove.reader.domain.*
import dev.grove.reader.ui.GroveApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MobiReaderUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun webView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{webView(view.getChildAt(it))}else null
    @Test fun chaptersReuseTheReaderAndRejectStaleBridgeEventsWhileSavingWholeBookProgress() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("accountId","100").putString("login","reader").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        val bytes=listOf(File("../reader/fixtures/mobi/alice.azw3"),File("reader/fixtures/mobi/alice.azw3")).first{it.isFile}.readBytes()
        val model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("mobi-ui"),account={"100"},connectionFactory={url->object:HttpURLConnection(url){
            override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
            override fun getResponseCode()=200
            override fun getInputStream()=JSONObject().put("path","books/reading.azw3").put("size",bytes.size)
                .put("encoding","base64").put("content",Base64.getEncoder().encodeToString(bytes)).toString().byteInputStream()
        }})
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","books/reading.azw3"))}
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.waitForIdle()
        compose.runOnIdle{assertEquals(model.state.value.error,PreviewKind.MOBI,model.state.value.document?.attachment?.kind)}
        lateinit var reader:WebView
        compose.waitUntil(15_000){var ready=false;compose.runOnIdle{
            webView(compose.activity.window.decorView)?.let{reader=it;ready=true}
        };ready}
        fun payload()=JSONObject(reader.javaClass.getDeclaredField("payload").apply{isAccessible=true}.get(reader) as String)
        fun bridge(name:String,id:String,json:String)=compose.runOnIdle {
            val target=shadowOf(reader).getJavascriptInterface("Grove")
            target.javaClass.getDeclaredMethod(name,String::class.java,String::class.java).apply{isAccessible=true}.invoke(target,id,json)
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun state(index:Int,title:String)=JSONObject().put("bookTitle","示例书籍").put("author","示例作者")
            .put("total",3).put("section",index).put("title",title).put("progress",0.0).put("contents",JSONArray()
                .put(JSONObject().put("title","封面").put("index",0))
                .put(JSONObject().put("title","第一章").put("index",1).put("href","filepos:100"))
                .put(JSONObject().put("title","第二章").put("index",2).put("href","filepos:200"))).toString()
        assertEquals("mobi",payload().getString("kind"))
        val first=payload().getString("id")
        bridge("bookState",first,state(0,"封面"))
        compose.onNodeWithContentDescription("章节目录，第 1 章，共 3 章").assertIsDisplayed()
        compose.onNodeWithContentDescription("上一章").assertIsNotEnabled()
        compose.onNodeWithContentDescription("下一章").performClick()
        compose.runOnIdle{assertSame(reader,webView(compose.activity.window.decorView))}
        val second=payload().getString("id");assertNotEquals(first,second)
        // While the next chapter is being prepared, keep the current paper
        // visible without flashing an indeterminate line along its top edge.
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.onNodeWithText("本章 0%").assertIsDisplayed()
        bridge("bookState",first,state(2,"迟到的章节"))
        compose.onNodeWithText("迟到的章节").assertDoesNotExist()
        bridge("bookState",second,state(1,"第一章"))
        compose.onNodeWithContentDescription("章节目录，第 2 章，共 3 章").assertIsDisplayed()
        compose.runOnIdle {
            val target=shadowOf(reader).getJavascriptInterface("Grove")
            val progress=target.javaClass.getDeclaredMethod("progress",String::class.java,Float::class.javaPrimitiveType).apply{isAccessible=true}
            progress.invoke(target,first,1f);shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1f/3f,model.state.value.progress,.001f)
            progress.invoke(target,second,.5f);shadowOf(Looper.getMainLooper()).idle()
            model.flushProgress()
            assertEquals(.5f,model.state.value.progress,.001f)
            assertEquals(.5f,ReadingStore(app,"100").resume()!!.progress,.001f)
        }
        compose.onNodeWithText("本章 50%").assertIsDisplayed()
        compose.onNodeWithContentDescription("章节目录，第 2 章，共 3 章").performClick()
        compose.runOnIdle{assertSame("目录面板应保留同一个阅读器",reader,webView(compose.activity.window.decorView))}
        compose.onNodeWithText("第二章").assertIsEnabled().performClick()
        compose.runOnIdle{assertSame("选择目录后应复用阅读器",reader,webView(compose.activity.window.decorView))}
        compose.waitUntil(10_000){var ready=false;compose.runOnIdle{ready=payload().optString("bookHref")=="filepos:200"};ready}
        assertEquals("filepos:200",payload().getString("bookHref"))
        bridge("bookState",payload().getString("id"),state(2,"第二章"))
        compose.onNodeWithContentDescription("下一章").assertIsNotEnabled()
        compose.onNodeWithContentDescription("阅读设置").performClick()
        compose.onNodeWithContentDescription("关闭阅读偏好").performClick()
        compose.runOnIdle{assertSame(reader,webView(compose.activity.window.decorView))}
    }
}
