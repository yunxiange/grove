package dev.grove.reader.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.Heading
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayInputStream
import dev.grove.reader.domain.PreviewKind

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentReader(document: Document,fontSize: Int,warm: Boolean,initialProgress: Float,
    onLink: (String)->Unit,onProgress: (String,Float)->Unit,onHeadings: (String,List<Heading>)->Unit,
    heading: String?,modifier: Modifier=Modifier,
    onImage:(String)->Unit=onLink,
    imageResource:((Document,String)->Pair<String,ByteArray>?)?=null,
    onImageLongPress:((Document,String)->Unit)?=null,
    endPage:Int?=null,onEnd:(()->Unit)?=null) {
    val link by rememberUpdatedState(onLink)
    val progressCallback by rememberUpdatedState(onProgress)
    val headingsCallback by rememberUpdatedState(onHeadings)
    val imageCallback by rememberUpdatedState(onImage)
    val imageLongPressCallback by rememberUpdatedState(onImageLongPress)
    val currentDocument by rememberUpdatedState(document)
    val resourceCallback by rememberUpdatedState(imageResource)
    val endCallback by rememberUpdatedState(onEnd)
    val currentEndPage by rememberUpdatedState(endPage)
    // Reading position is restored only when the document changes, not after every scroll event.
    val initial = remember(document.key) { initialProgress }
    val payload=remember(document,fontSize,warm,endPage) { JSONObject().put("id",document.key).put("content",document.content)
        .put("url",document.url).put("fontSize",fontSize).put("warm",warm).put("progress",initial.toDouble())
        .put("kind",document.attachment?.kind?.name?.lowercase()?:"markdown").put("endPage",endPage?:JSONObject.NULL)
        .put("attachmentUrl",document.attachment?.let{"https://appassets.androidplatform.net/attachment/"+File(it.localPath).name}?:"").toString() }
    AndroidView(modifier=modifier,factory={context->
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/attachment/"){path->
                val attachment=currentDocument.attachment
                val file=attachment?.let{File(it.localPath)}
                if(file!=null&&path==file.name&&file.exists())WebResourceResponse(attachment.mimeType,null,file.inputStream())
                else WebResourceResponse("text/plain","utf-8",404,"Not Found",emptyMap(),ByteArrayInputStream(ByteArray(0)))
            }.build()
        PaperWebView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(249,247,241))
            settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.domStorageEnabled=false;settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportMultipleWindows(false)
            settings.setSupportZoom(true);settings.builtInZoomControls=true;settings.displayZoomControls=false
            setOnLongClickListener {
                val hit=hitTestResult
                val source=hit?.extra
                val callback=imageLongPressCallback
                if(callback!=null&&!source.isNullOrBlank()&&hit.type in setOf(WebView.HitTestResult.IMAGE_TYPE,WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                    callback(currentDocument,source);true
                }else false
            }
            addJavascriptInterface(object {
                @JavascriptInterface fun openLink(url:String) {post {link(url)}}
                @JavascriptInterface fun openImage(url:String) {post {imageCallback(url)}}
                @JavascriptInterface fun copyCode(text:String) {post {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("代码",text))
                }}
                @JavascriptInterface fun progress(id:String,value:Float) {post {progressCallback(id,value)}}
                @JavascriptInterface fun reachedEnd(id:String,page:Int) {post {
                    if(id==currentDocument.key&&page==currentEndPage&&lastEnd!=(id to page)) {
                        lastEnd=id to page;endCallback?.invoke()
                    }
                }}
                @JavascriptInterface fun headings(id:String,json:String) {
                    val values=runCatching {JSONArray(json).let {a->(0 until a.length()).map {i->a.getJSONObject(i).let {
                        Heading(it.getString("id"),it.getString("text"),it.getInt("level"))}}}}.getOrDefault(emptyList())
                    post {headingsCallback(id,values)}
                }
            },"Grove")
            webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest):WebResourceResponse? {
                    loader.shouldInterceptRequest(request.url)?.let{return it}
                    val url=request.url.toString();val doc=currentDocument
                    if(url.startsWith(doc.rawRoot)&&resourceCallback!=null)return try {
                        resourceCallback?.invoke(doc,url)?.let{WebResourceResponse(it.first,null,ByteArrayInputStream(it.second))}
                    }catch(_:Exception){WebResourceResponse("text/plain","utf-8",403,"Unavailable",emptyMap(),ByteArrayInputStream(ByteArray(0)))}
                    return null
                }
                override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest):Boolean {link(request.url.toString());return true}
                override fun onPageFinished(view:WebView?,url:String?) {ready=true;render()}
            }
            loadUrl("https://appassets.androidplatform.net/assets/reader/index.html")
        }
    },update={view->
        if(view.payload!=payload) {view.payload=payload;view.render()}
        if(heading!=null&&view.lastHeading!=heading) {view.lastHeading=heading;view.evaluateJavascript("window.scrollToHeading(${JSONObject.quote(heading)})",null)}
        if(heading==null)view.lastHeading=null
    },onRelease={view->view.removeJavascriptInterface("Grove");view.stopLoading();view.destroy()})
}

private class PaperWebView(context:Context):WebView(context) {
    var lastEnd:Pair<String,Int>?=null
    var ready=false
    var payload=""
    private var rendered=""
    var lastHeading:String?=null
    fun render() {if(ready&&payload.isNotBlank()&&rendered!=payload) {
        rendered=payload
        evaluateJavascript("window.renderDocument&&window.renderDocument($payload)",null)
    }}
}
