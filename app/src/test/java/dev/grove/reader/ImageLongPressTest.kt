package dev.grove.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.grove.reader.domain.*
import dev.grove.reader.ui.DocumentReader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWebView

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ImageLongPressTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun webView(view:View):WebView?=if(view is WebView)view else if(view is ViewGroup)
        (0 until view.childCount).firstNotNullOfOrNull{webView(view.getChildAt(it))}else null

    @Test fun inlineAndLinkedImagesOfferSaveWithoutOpeningImageOrLink() {
        val doc=Document(RepoId("owner","repo"),"main","README.md","![picture](image.png)")
        val current=mutableStateOf(doc)
        var target:ImageRequest?=null
        var opened=false
        compose.setContent {
            DocumentReader(current.value,17,false,0f,{opened=true},{_,_->},{_,_->},null,Modifier.fillMaxSize(),
                onImage={opened=true},onImageLongPress={d,url->target=ImageRequest(d,url)})
        }
        compose.runOnIdle {
            val view=webView(compose.activity.window.decorView)!!
            assertNotNull("WebView 必须注册长按回调",shadowOf(view).onLongClickListener)
            for(type in listOf(WebView.HitTestResult.IMAGE_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(type,doc.rawRoot+"image.png"))
                assertEquals(type,view.hitTestResult.type)
                // Robolectric's WebView provider stubs performLongClick; dispatch its registered listener.
                assertTrue(shadowOf(view).onLongClickListener.onLongClick(view))
                assertEquals(doc,target!!.document);assertEquals(doc.rawRoot+"image.png",target!!.url)
            }
            assertFalse(opened)
            target=null
            shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(WebView.HitTestResult.UNKNOWN_TYPE,null))
            shadowOf(view).onLongClickListener.onLongClick(view)
            assertNull(target)
        }
        val preview=doc.copy(path="picture.png",content="",attachment=Attachment(PreviewKind.IMAGE,"image/png","/cached-image"))
        compose.runOnIdle{current.value=preview}
        compose.runOnIdle {
            val view=webView(compose.activity.window.decorView)!!
            val source="https://appassets.androidplatform.net/attachment/cached-image"
            shadowOf(view).setHitTestResult(ShadowWebView.createHitTestResult(WebView.HitTestResult.IMAGE_TYPE,source))
            assertTrue(shadowOf(view).onLongClickListener.onLongClick(view))
            assertEquals(preview,target!!.document)
            assertEquals(source,target!!.url)
        }
    }
}
