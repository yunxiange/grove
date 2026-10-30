package dev.grove.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.net.Uri
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import dev.grove.reader.domain.*
import dev.grove.reader.ui.DocumentReader
import dev.grove.reader.ui.PreviewDiagnostics
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PreviewReadinessTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun webView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{webView(view.getChildAt(it))}else null
    private fun request(url:String)=object:WebResourceRequest {
        override fun getUrl()=Uri.parse(url)
        override fun isForMainFrame()=false
        override fun isRedirect()=false
        override fun hasGesture()=false
        override fun getMethod()="GET"
        override fun getRequestHeaders()=emptyMap<String,String>()
    }
    @Test fun pageFinishedBeforeScriptsReadyCannotConsumePendingPreview() {
        val doc=Document(RepoId("owner","repo"),"main","drawing.svg","<svg/>",Attachment(PreviewKind.IMAGE,"image/svg+xml","/cached-svg"))
        compose.setContent{DocumentReader(doc,17,false,0f,{},{_,_->},{_,_->},null,Modifier.fillMaxSize())}
        compose.runOnIdle {
            val view=webView(compose.activity.window.decorView)!!
            val shadow=shadowOf(view)
            shadow.webViewClient.onPageFinished(view,"https://appassets.androidplatform.net/assets/reader/index.html")
            assertNull("页面完成不等于渲染脚本就绪，不能提前消费待显示的附件",shadow.lastEvaluatedJavascript)
            val bridge=shadow.getJavascriptInterface("Grove")
            bridge.javaClass.getDeclaredMethod("readerReady").apply{isAccessible=true}.invoke(bridge)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val script=shadowOf(webView(compose.activity.window.decorView)!!).lastEvaluatedJavascript
            assertTrue(script.replace("\\/","/").contains("attachment/cached-svg"))
            assertTrue(script.replace("\\/","/").contains("\"attachmentMimeType\":\"image/svg+xml\""))
            assertTrue(script.contains("renderDocument"))
        }
    }
    @Test fun rejectedRenderShowsRetryInsteadOfAQuietBlankPage() {
        val doc=Document(RepoId("owner","repo"),"main","image.png","",Attachment(PreviewKind.IMAGE,"image/png","/cached-image"))
        compose.setContent{DocumentReader(doc,17,false,0f,{},{_,_->},{_,_->},null,Modifier.fillMaxSize())}
        compose.runOnIdle {
            val shadow=shadowOf(webView(compose.activity.window.decorView)!!)
            val bridge=shadow.getJavascriptInterface("Grove")
            bridge.javaClass.getDeclaredMethod("readerReady").apply{isAccessible=true}.invoke(bridge)
        }
        compose.waitForIdle()
        compose.runOnIdle{shadowOf(webView(compose.activity.window.decorView)!!).lastEvaluatedJavascriptCallback.onReceiveValue("false")}
        compose.onNodeWithText("预览启动失败，请重新加载。").assertIsDisplayed()
        compose.onNodeWithText("重新加载预览").performClick()
        compose.onNodeWithText("预览启动失败，请重新加载。").assertDoesNotExist()
    }
    @Test fun imageSvgAndSpreadsheetUrlsServeTheirActualCacheBytesWithCorrectMime() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val file=app.cacheDir.resolve("preview-resource").apply{writeBytes("preview-bytes".toByteArray())}
        val doc=Document(RepoId("owner","repo"),"main","image.png","",Attachment(PreviewKind.IMAGE,"image/png",file.absolutePath))
        val current=mutableStateOf(doc)
        compose.setContent{DocumentReader(current.value,17,false,0f,{},{_,_->},{_,_->},null,Modifier.fillMaxSize())}
        for((kind,mime) in listOf(PreviewKind.IMAGE to "image/png",PreviewKind.IMAGE to "image/svg+xml",PreviewKind.SPREADSHEET to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            compose.runOnIdle{current.value=doc.copy(attachment=Attachment(kind,mime,file.absolutePath))}
            compose.runOnIdle {
                val view=webView(compose.activity.window.decorView)!!
                val client=shadowOf(view).webViewClient
                val response=client.shouldInterceptRequest(view,request("https://appassets.androidplatform.net/attachment/${file.name}"))!!
                assertEquals(200,response.statusCode);assertEquals(mime,response.mimeType)
                assertArrayEquals(file.readBytes(),response.data.use{it.readBytes()})
                assertEquals(404,client.shouldInterceptRequest(view,request("https://appassets.androidplatform.net/attachment/not-the-current-file"))!!.statusCode)
            }
        }
        compose.runOnIdle {
            file.delete()
            val view=webView(compose.activity.window.decorView)!!
            assertEquals(404,shadowOf(view).webViewClient.shouldInterceptRequest(view,request("https://appassets.androidplatform.net/attachment/${file.name}"))!!.statusCode)
        }
    }
    @Test fun presentedAttachmentResetsNativeScrollAndRequestsAFrameButIgnoresStaleDocuments() {
        val doc=Document(RepoId("owner","repo"),"main","drawing.svg","<svg/>",Attachment(PreviewKind.IMAGE,"image/svg+xml","/cached-svg"))
        val diagnostics=PreviewDiagnostics(doc)
        compose.setContent{DocumentReader(doc,17,false,0f,{},{_,_->},{_,_->},null,Modifier.fillMaxSize(),diagnostics=diagnostics)}
        fun present(id:String) {
            compose.runOnIdle {
                val bridge=shadowOf(webView(compose.activity.window.decorView)!!).getJavascriptInterface("Grove")
                bridge.javaClass.getDeclaredMethod("previewPresented",String::class.java).apply{isAccessible=true}.invoke(bridge,id)
            }
            compose.waitForIdle()
        }
        compose.runOnIdle{webView(compose.activity.window.decorView)!!.scrollTo(0,1200)}
        present("old-document")
        compose.runOnIdle{assertEquals(1200,webView(compose.activity.window.decorView)!!.scrollY)}
        present(doc.key)
        compose.runOnIdle{
            assertEquals(0,webView(compose.activity.window.decorView)!!.scrollY)
            assertEquals("已请求显示帧",diagnostics.frameResult)
        }
        compose.runOnIdle{diagnostics.softwareRendering=true}
        compose.runOnIdle{assertEquals(View.LAYER_TYPE_SOFTWARE,webView(compose.activity.window.decorView)!!.layerType)}
        compose.runOnIdle{diagnostics.softwareRendering=false}
        compose.runOnIdle{assertEquals(View.LAYER_TYPE_NONE,webView(compose.activity.window.decorView)!!.layerType)}
    }
}
