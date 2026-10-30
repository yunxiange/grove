package dev.grove.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.RepoId
import dev.grove.reader.ui.DocumentReader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class CommentPaginationReaderTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun webView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{webView(view.getChildAt(it))}else null
    @Test fun endBridgeIgnoresDuplicateAndStalePagesAndUsesCurrentDocument() {
        val original=Document(RepoId("owner","repo"),"main","issue-1.md","Discussion")
        val document=mutableStateOf(original)
        val page=mutableStateOf<Int?>(1)
        var loaded=0
        compose.setContent {
            DocumentReader(document.value,17,false,0f,{},{_,_->},{_,_->},null,Modifier.fillMaxSize(),
                endPage=page.value,onEnd={loaded++})
        }
        fun reached(id:String,value:Int) {
            compose.runOnIdle {
                val bridge=shadowOf(webView(compose.activity.window.decorView)!!).getJavascriptInterface("Grove")
                bridge.javaClass.getDeclaredMethod("reachedEnd",String::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(bridge,id,value)
            }
            compose.waitForIdle()
        }
        reached(original.key,1);reached(original.key,1);assertEquals(1,loaded)
        compose.runOnIdle{page.value=null}
        reached(original.key,2);assertEquals(1,loaded)
        compose.runOnIdle{page.value=2;document.value=original.copy(content="Discussion plus comments")}
        reached(original.key,1);reached(original.key,2);assertEquals(2,loaded)
        compose.runOnIdle{document.value=original.copy(path="issue-2.md");page.value=1}
        reached(original.key,1);assertEquals(2,loaded)
        reached(document.value.key,1);assertEquals(3,loaded)
    }
}
