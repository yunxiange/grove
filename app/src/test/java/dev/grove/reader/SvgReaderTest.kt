package dev.grove.reader

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
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
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w393dp-h851dp")
class SvgReaderTest {
    @get:Rule val compose=createComposeRule()
    @Test fun svgOpensInPreviewAndCanSwitchToCopyableSourceAndBack() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().putString("library","[]").commit()
        val source="<svg xmlns=\"http://www.w3.org/2000/svg\"><text>中文 &amp; SVG</text><script>neverExecute()</script></svg>"
        val api=GitHubClient(app.cacheDir.resolve("svg-ui-test"),connectionFactory={url->
            val bytes=source.toByteArray()
            val response=JSONObject().put("path","drawing.svg").put("size",bytes.size).put("encoding","base64")
                .put("content",Base64.getEncoder().encodeToString(bytes)).toString().toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream()=response.inputStream()
            }
        })
        val model=GroveViewModel(app)
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.openBookmark(Bookmark(RepoId("owner","repo"),"main","drawing.svg"))}
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.onNodeWithText("预览").assertIsSelected()
        compose.onNodeWithText(source).assertDoesNotExist()
        compose.onNodeWithText("源码").performClick().assertIsSelected()
        compose.onNodeWithText(source).assertIsDisplayed()
        compose.onNodeWithContentDescription("复制 SVG 源码").performClick()
        compose.runOnIdle {
            val clipboard=app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(source,clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithText("预览").performClick().assertIsSelected()
        compose.onNodeWithText(source).assertDoesNotExist()
        assertFalse(model.state.value.raw)
    }
}
