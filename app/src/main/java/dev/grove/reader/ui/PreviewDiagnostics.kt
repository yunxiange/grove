package dev.grove.reader.ui

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.grove.reader.BuildConfig
import dev.grove.reader.domain.Document
import java.io.File

/** Device-side preview evidence; never includes document contents, URLs, or credentials. */
class PreviewDiagnostics(private val document:Document) {
    var webViewVersion by mutableStateOf("尚未创建")
    var viewport by mutableStateOf("尚未布局")
    var pageLoaded by mutableStateOf(false)
    var rendererReady by mutableStateOf(false)
    var renderResult by mutableStateOf("尚未发送")
    var attachmentResult by mutableStateOf("尚未请求")
    var contentResult by mutableStateOf("尚无反馈")
    var frameResult by mutableStateOf("尚未提交")
    var softwareRendering by mutableStateOf(false)
    fun report():String {
        val attachment=document.attachment
        val file=attachment?.let{File(it.localPath)}
        return buildString {
            appendLine("Grove 预览诊断 v2 · ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("类型：${attachment?.kind} / ${attachment?.mimeType}")
            appendLine("缓存：${if(file?.isFile==true)"存在"else"缺失"}，${file?.length()?:0} 字节")
            appendLine("WebView：$webViewVersion")
            appendLine("绘制模式：${if(softwareRendering)"软件兼容"else"默认"}")
            appendLine("显示区域：$viewport")
            appendLine("页面完成：$pageLoaded")
            appendLine("渲染器就绪：$rendererReady")
            appendLine("渲染请求：$renderResult")
            appendLine("附件读取：$attachmentResult")
            appendLine("画面提交：$frameResult")
            append("内容反馈：$contentResult")
        }
    }
}
