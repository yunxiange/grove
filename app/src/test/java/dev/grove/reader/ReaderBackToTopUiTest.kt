package dev.grove.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.grove.reader.ui.ReaderBackToTop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
class ReaderBackToTopUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun sourceReaderShowsTheActionAfterScrollingAndReturnsToZero() {
        val scroll=ScrollState(0)
        compose.setContent{MaterialTheme{Box(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                repeat(100){Text("阅读内容 $it",Modifier.height(28.dp))}
            }
            ReaderBackToTop(scroll)
        }}}
        compose.onNodeWithContentDescription("回到顶部").assertDoesNotExist()
        compose.runOnIdle{scroll.dispatchRawDelta(scroll.viewportSize.toFloat())}
        compose.onNodeWithContentDescription("回到顶部").assertIsDisplayed()
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.waitForIdle()
        compose.runOnIdle{assertEquals(0,scroll.value)}
        compose.onNodeWithContentDescription("回到顶部").assertDoesNotExist()
    }
}
