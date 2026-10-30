package dev.grove.reader

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.ui.PdfPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfPreviewUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun aMissingCacheFileShowsAnErrorInsteadOfAnEmptyPage() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val file=app.cacheDir.resolve("missing-preview.pdf").apply{delete()}
        compose.setContent{PdfPreview(file,Modifier.fillMaxSize())}
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();compose.onAllNodesWithText("PDF 无法读取，文件可能已损坏。").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("PDF 无法读取，文件可能已损坏。").assertIsDisplayed()
    }
}
