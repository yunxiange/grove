package dev.grove.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.grove.reader.domain.FileDownloadProgress
import dev.grove.reader.ui.FileDownloadNotice
import dev.grove.reader.ui.Paper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubDownloadUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun downloadNoticeShowsPercentageAndBytesOrAnUnknownTotal() {
        val progress=mutableStateOf(FileDownloadProgress(35L*1024*1024,100L*1024*1024))
        val running=mutableStateOf(true)
        compose.setContent{MaterialTheme{Column(Modifier.fillMaxWidth().background(Paper).padding(vertical=12.dp)){FileDownloadNotice(progress.value,running.value)}}}
        compose.onNodeWithText("正在下载 EPUB").assertDoesNotExist()
        compose.onNodeWithText("35%").assertIsDisplayed()
        compose.onNodeWithText("35.0 MB / 100.0 MB").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("file-download").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val image=Bitmap.createBitmap(bounds.width.toInt(),bounds.height.toInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.translate(-bounds.left,-bounds.top)
            compose.activity.window.decorView.draw(canvas)
            File("build/reports/ui/epub-download-progress-v7.png").apply{parentFile!!.mkdirs()}.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
            progress.value=FileDownloadProgress(12L*1024*1024,null)
        }
        compose.onNodeWithText("已下载 12.0 MB").assertIsDisplayed()
        compose.onNodeWithText("下载中").assertDoesNotExist()
        compose.onNodeWithText("35%").assertDoesNotExist()
        compose.runOnIdle{running.value=false}
        compose.onNodeWithText("等待重试").assertIsDisplayed()
        compose.onNodeWithText("下载中").assertDoesNotExist()
    }
}
