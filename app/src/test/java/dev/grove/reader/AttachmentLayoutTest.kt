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
import dev.grove.reader.domain.Bookmark
import dev.grove.reader.domain.RepoId
import dev.grove.reader.ui.GroveApp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.HttpURLConnection
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AttachmentLayoutTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun findWebView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{findWebView(view.getChildAt(it))}else null
    @Test fun svgPreviewHasAllocatedComposeViewportAndInspectableDeviceDiagnostics() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().commit()
        val svg="<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"300\" height=\"200\"><rect width=\"300\" height=\"200\" fill=\"red\"/></svg>"
        val model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("attachment-layout"),connectionFactory={url->
            val body=JSONObject().put("path","drawing.svg").put("encoding","base64").put("size",svg.length)
                .put("content",Base64.getEncoder().encodeToString(svg.toByteArray())).toString().toByteArray()
            object:HttpURLConnection(url){
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream()=body.inputStream()
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","drawing.svg"))}
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        fun assertViewport()=compose.runOnIdle {
            val view=findWebView(compose.activity.window.decorView)
            assertNotNull("预览应存在真实 WebView",view)
            // Robolectric does not measure the embedded WebView provider itself. Verify
            // Compose's allocated host bounds; actual device bounds go into diagnostics.
            val host=view!!.parent as View
            assertTrue("宿主宽度应超过 200px，实际 ${host.width}",host.width>200)
            assertTrue("宿主高度应超过 200px，实际 ${host.height}",host.height>200)
            assertTrue(view.isShown)
        }
        assertViewport()
        compose.onNodeWithContentDescription("预览诊断").assertDoesNotExist()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithContentDescription("更多功能").performClick()
        compose.onNodeWithText("开发者选项").performClick()
        compose.onNode(isToggleable()).assertIsOff()
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);view.draw(canvas)
            ShadowDialog.getLatestDialog()?.window?.decorView?.draw(canvas)
            File("build/reports/ui/developer-options.png").apply{parentFile?.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.runOnIdle{assertTrue(ReadingStore(app).previewDiagnosticsEnabled)}
        compose.onNodeWithContentDescription("关闭开发者选项").performClick()
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithContentDescription("预览诊断").performClick()
        compose.onNodeWithText("复制诊断").assertIsDisplayed()
        compose.onNodeWithText("复制诊断").performClick()
        compose.runOnIdle {
            val clipboard=app.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val report=clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(report.contains("image/svg+xml"));assertTrue(report.contains("缓存：存在"))
            assertFalse(report.contains(svg));assertFalse(report.contains("raw.githubusercontent.com"))
        }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("源码").performClick()
        compose.onNodeWithText(svg).assertIsDisplayed()
        compose.onNodeWithText("预览").performClick()
        assertViewport()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithContentDescription("更多功能").performClick()
        compose.onNodeWithText("开发者选项").performClick()
        compose.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("关闭开发者选项").performClick()
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithContentDescription("预览诊断").assertDoesNotExist()
        compose.runOnIdle{assertFalse(ReadingStore(app).previewDiagnosticsEnabled)}
    }
}
