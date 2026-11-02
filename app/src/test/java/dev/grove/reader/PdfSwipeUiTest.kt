package dev.grove.reader

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.grove.reader.ui.PdfPageImage
import dev.grove.reader.ui.PdfToolbar
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfSwipeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val page=mutableIntStateOf(0)
    private fun setup(zoom:Float=1f) {
        val bitmap=Bitmap.createBitmap(600,1800,Bitmap.Config.ARGB_8888)
        compose.setContent{Column(Modifier.fillMaxSize()){
            PdfToolbar(page.intValue,3,{page.intValue=it})
            PdfPageImage(bitmap,page.intValue,zoom,{page.intValue=(page.intValue+it).coerceIn(0,2)},Modifier.weight(1f).fillMaxWidth())
        }}
    }
    private fun viewport()=compose.onNodeWithTag("pdf-page-viewport")
    @Test fun horizontalSwipesTurnPagesInBothDirectionsAndStopAtBookEnds() {
        setup()
        viewport().performTouchInput{swipeRight()};assertEquals(0,page.intValue)
        viewport().performTouchInput{swipeLeft()};compose.onNodeWithText("2 / 3").assertIsDisplayed()
        viewport().performTouchInput{swipeRight()};compose.onNodeWithText("1 / 3").assertIsDisplayed()
        repeat(3){viewport().performTouchInput{swipeLeft()}}
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
    }
    @Test fun verticalScrollsAndSmallDragsDoNotTurnPages() {
        setup()
        viewport().performTouchInput{swipeUp()}
        assertEquals(0,page.intValue)
        val scroll=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(hasTestTag("pdf-page-viewport")))
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("纵向拖动必须继续滚动当前页",scroll.value()>0)
        viewport().performTouchInput{down(center);moveBy(Offset(-15f,0f));up()}
        assertEquals(0,page.intValue)
    }
    @Test fun zoomedPagesPanBeforeTurningAndRequireAnOutwardSwipeAtTheEdge() {
        setup(zoom=2f)
        viewport().performTouchInput{swipe(Offset(width*.7f,centerY),Offset(width*.5f,centerY),durationMillis=1000)}
        assertEquals(0,page.intValue)
        val scroll=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("pdf-page-viewport")))
            .fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("放大后先横向移动页面",scroll.value()>0)
        assertTrue("此时尚未到达页边",scroll.value()<scroll.maxValue())
        viewport().performTouchInput{swipeLeft()};assertEquals(0,page.intValue)
        viewport().performTouchInput{swipeLeft()};compose.onNodeWithText("2 / 3").assertIsDisplayed()
        viewport().performTouchInput{swipeRight()};compose.onNodeWithText("1 / 3").assertIsDisplayed()
    }
    @Test fun multiTouchDoesNotTurnPages() {
        setup()
        viewport().performTouchInput {
            down(0,Offset(width*.8f,height*.5f));down(1,Offset(width*.8f,height*.5f+40))
            moveTo(0,Offset(width*.2f,height*.5f));moveTo(1,Offset(width*.2f,height*.5f+40))
            up(0);up(1)
        }
        assertEquals(0,page.intValue)
    }
}
