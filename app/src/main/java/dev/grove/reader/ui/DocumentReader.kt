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
import android.webkit.WebResourceError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.Heading
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayInputStream
import dev.grove.reader.domain.PreviewKind
import dev.grove.reader.data.EpubArchive

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentReader(document: Document,fontSize: Int,warm: Boolean,initialProgress: Float,
    onLink: (String)->Unit,onProgress: (String,Float)->Unit,onHeadings: (String,List<Heading>)->Unit,
    heading: String?,modifier: Modifier=Modifier,
    onImage:(String)->Unit=onLink,
    imageResource:((Document,String)->Pair<String,ByteArray>?)?=null,
    onImageLongPress:((Document,String)->Unit)?=null,
    endPage:Int?=null,onEnd:(()->Unit)?=null,diagnostics:PreviewDiagnostics?=null,onPageSwipe:((Int)->Unit)?=null,
    bookRequest:MobiRequest?=null,onBookState:((String)->Unit)?=null,onBookLink:((String)->Unit)?=null,
    canSwipePrevious:Boolean=true,canSwipeNext:Boolean=true) {
    val link by rememberUpdatedState(onLink)
    val progressCallback by rememberUpdatedState(onProgress)
    val headingsCallback by rememberUpdatedState(onHeadings)
    val imageCallback by rememberUpdatedState(onImage)
    val imageLongPressCallback by rememberUpdatedState(onImageLongPress)
    val currentDocument by rememberUpdatedState(document)
    val resourceCallback by rememberUpdatedState(imageResource)
    val endCallback by rememberUpdatedState(onEnd)
    val currentEndPage by rememberUpdatedState(endPage)
    val currentDiagnostics by rememberUpdatedState(diagnostics)
    val swipeCallback by rememberUpdatedState(onPageSwipe)
    val bookCallback by rememberUpdatedState(onBookState)
    val bookLinkCallback by rememberUpdatedState(onBookLink)
    val renderKey=document.key+(bookRequest?.let{":mobi:${it.sequence}"}?:"")
    val currentRenderKey by rememberUpdatedState(renderKey)
    var previewError by remember(document.key){mutableStateOf<String?>(null)}
    var attempt by remember{mutableIntStateOf(0)}
    val showError by rememberUpdatedState<(String)->Unit>({previewError=it})
    // Reading position is restored only when the document changes, not after every scroll event.
    val initial = remember(document.key) { initialProgress }
    val payload=remember(document,fontSize,warm,endPage,onPageSwipe!=null,bookRequest,canSwipePrevious,canSwipeNext) { JSONObject().put("id",renderKey).put("content",document.content)
        .put("url",document.url).put("fontSize",fontSize).put("warm",warm).put("progress",initial.toDouble())
        .put("kind",document.attachment?.kind?.name?.lowercase()?:"markdown").put("endPage",endPage?:JSONObject.NULL)
        .put("swipePages",onPageSwipe!=null)
        .put("canSwipePrevious",canSwipePrevious).put("canSwipeNext",canSwipeNext)
        .put("bookSection",bookRequest?.section?:-1).put("bookHref",bookRequest?.href?:JSONObject.NULL)
        .put("bookProgress",bookRequest?.progress?.toDouble()?:0.0)
        .put("attachmentMimeType",document.attachment?.mimeType?:"")
        .put("attachmentUrl",document.attachment?.let{"https://appassets.androidplatform.net/attachment/"+File(it.localPath).name}?:"").toString() }
    Box(modifier) {
    key(attempt) {
    AndroidView(modifier=Modifier.fillMaxSize(),factory={context->
        val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/epub/"){path->
                val attachment=currentDocument.attachment
                val file=attachment?.let{File(it.localPath)}
                val resource=if(attachment?.kind==PreviewKind.EPUB&&file!=null&&path.startsWith(file.name+"/"))
                    runCatching{EpubArchive.resource(file,path.removePrefix(file.name+"/"))}.getOrNull() else null
                if(resource!=null)WebResourceResponse(resource.first,null,200,"OK",mapOf("Cache-Control" to "no-store"),ByteArrayInputStream(resource.second))
                else WebResourceResponse("text/plain","utf-8",404,"Not Found",emptyMap(),ByteArrayInputStream(ByteArray(0)))
            }
            .addPathHandler("/attachment/"){path->
                val attachment=currentDocument.attachment
                val file=attachment?.let{File(it.localPath)}
                if(file!=null&&path==file.name&&file.isFile&&file.length()>0) {
                    currentDiagnostics?.attachmentResult="200 · ${file.length()} 字节 · ${attachment.mimeType}"
                    WebResourceResponse(attachment.mimeType,null,200,"OK",mapOf("Cache-Control" to "no-store"),file.inputStream())
                } else {
                    currentDiagnostics?.attachmentResult="404 · 缓存不存在或未匹配"
                    WebResourceResponse("text/plain","utf-8",404,"Not Found",emptyMap(),ByteArrayInputStream(ByteArray(0)))
                }
            }.build()
        PaperWebView(context).apply {
            currentDiagnostics?.webViewVersion=WebViewCompat.getCurrentWebViewPackage(context)?.let{"${it.packageName} ${it.versionName}"}?:"无法获取版本"
            onBoundsChanged={w,h->currentDiagnostics?.viewport="$w × $h px"}
            onRenderResult={id,result->if(id==currentRenderKey)currentDiagnostics?.renderResult=result}
            onFrameState={currentDiagnostics?.frameResult=it}
            onFailure={showError(it)}
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
                @JavascriptInterface fun readerReady() {post {currentDiagnostics?.rendererReady=true;rendererReady()}}
                @JavascriptInterface fun previewStatus(id:String,status:String) {post {
                    if(id==currentRenderKey)currentDiagnostics?.contentResult=status.take(160)
                }}
                @JavascriptInterface fun previewPresented(id:String) {post {
                    if(id==currentRenderKey) {
                        if(currentDocument.attachment!=null)scrollTo(0,0)
                        presentFrame()
                    }
                }}
                @JavascriptInterface fun renderFailed(id:String) {post {
                    if(id.isBlank()||id==currentRenderKey)fail("预览加载失败，请重新加载。")
                }}
                @JavascriptInterface fun openLink(url:String) {post {link(url)}}
                @JavascriptInterface fun openImage(url:String) {post {imageCallback(url)}}
                @JavascriptInterface fun copyCode(text:String) {post {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("代码",text))
                }}
                @JavascriptInterface fun progress(id:String,value:Float) {post {if(id==currentRenderKey)progressCallback(currentDocument.key,value)}}
                @JavascriptInterface fun bookState(id:String,json:String) {post {
                    if(id==currentRenderKey&&currentDocument.attachment?.kind==PreviewKind.MOBI&&json.length<=1_000_000)bookCallback?.invoke(json)
                }}
                @JavascriptInterface fun bookLink(id:String,href:String) {post {
                    if(id==currentRenderKey&&currentDocument.attachment?.kind==PreviewKind.MOBI&&href.length<=200)bookLinkCallback?.invoke(href)
                }}
                @JavascriptInterface fun swipePage(id:String,direction:Int) {post {
                    if(id==currentRenderKey&&currentDocument.attachment?.kind?.ebook==true&&(direction==1||direction == -1))swipeCallback?.invoke(direction)
                }}
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
                override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                    currentDiagnostics?.pageLoaded=false;currentDiagnostics?.rendererReady=false;resetRenderer()
                }
                override fun onPageFinished(view:WebView?,url:String?) {currentDiagnostics?.pageLoaded=true;render()}
                override fun onReceivedError(view:WebView?,request:WebResourceRequest,error:WebResourceError) {
                    if(request.isForMainFrame||(request.url.host=="appassets.androidplatform.net"&&request.url.path.orEmpty().endsWith(".js")))
                        fail("预览组件加载失败，请重新加载。")
                }
                override fun onReceivedHttpError(view:WebView?,request:WebResourceRequest,response:WebResourceResponse) {
                    if(request.isForMainFrame||(request.url.host=="appassets.androidplatform.net"&&request.url.path.orEmpty().endsWith(".js")))
                        fail("预览组件加载失败，请重新加载。")
                }
            }
            resetRenderer()
            loadUrl("https://appassets.androidplatform.net/assets/reader/index.html")
        }
    },update={view->
        val layer=if(currentDiagnostics?.softwareRendering==true)android.view.View.LAYER_TYPE_SOFTWARE else android.view.View.LAYER_TYPE_NONE
        if(view.layerType!=layer){view.setLayerType(layer,null);view.requestLayout();view.presentFrame()}
        if(view.payload!=payload) {view.payload=payload;view.render()}
        if(heading!=null&&view.lastHeading!=heading) {view.lastHeading=heading;view.evaluateJavascript("window.scrollToHeading(${JSONObject.quote(heading)})",null)}
        if(heading==null)view.lastHeading=null
    },onRelease={view->view.releaseRenderer();view.removeJavascriptInterface("Grove");view.stopLoading();view.destroy()})
    }
    previewError?.let{message->
        Column(Modifier.fillMaxSize().background(Paper).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Text(message,color=Muted)
            TextButton(onClick={previewError=null;attempt++}){Text("重新加载预览")}
        }
    }
    }
}

private class PaperWebView(context:Context):WebView(context) {
    var lastEnd:Pair<String,Int>?=null
    private var ready=false
    private var disposed=false
    var onFailure:(String)->Unit={}
    var onBoundsChanged:(Int,Int)->Unit={_,_->}
    var onRenderResult:(String,String)->Unit={_,_->}
    var onFrameState:(String)->Unit={}
    private var frameRequest=0L
    private var drawPending=false
    var payload=""
    private var rendered=""
    private var inFlight:String?=null
    private val readyTimeout=Runnable{if(!ready&&!disposed)onFailure("预览组件未能启动，请重新加载或更新 Android System WebView。")}
    var lastHeading:String?=null
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);onBoundsChanged(w,h)}
    override fun onDraw(canvas:android.graphics.Canvas) {
        super.onDraw(canvas)
        if(drawPending&&!disposed){drawPending=false;onFrameState("已执行绘制")}
    }
    fun presentFrame() {
        if(disposed)return
        onFrameState("已请求显示帧")
        postInvalidateOnAnimation()
        postVisualStateCallback(++frameRequest,object:VisualStateCallback() {
            override fun onComplete(requestId:Long) {
                if(disposed||requestId!=frameRequest)return
                onFrameState("内容帧已就绪，等待绘制")
                drawPending=true;invalidate()
            }
        })
    }
    fun resetRenderer() {ready=false;rendered="";inFlight=null;removeCallbacks(readyTimeout);postDelayed(readyTimeout,15000)}
    fun rendererReady() {if(!disposed){ready=true;removeCallbacks(readyTimeout);render()}}
    fun releaseRenderer(){disposed=true;removeCallbacks(readyTimeout)}
    fun fail(message:String){if(!disposed)onFailure(message)}
    fun render() {if(!disposed&&ready&&payload.isNotBlank()&&rendered!=payload&&inFlight==null) {
        val requested=payload
        val id=JSONObject(requested).optString("id")
        onRenderResult(id,"已发送，等待 WebView 返回")
        inFlight=requested
        evaluateJavascript("(function(){if(typeof window.renderDocument!=='function')return false;window.renderDocument($requested);return true;})()"){accepted->
            if(disposed)return@evaluateJavascript
            inFlight=null
            if(accepted=="true") {onRenderResult(id,"已受理");rendered=requested;if(payload!=requested)render()}
            else {onRenderResult(id,"未受理：${accepted?:"无返回"}");ready=false;onFailure("预览启动失败，请重新加载。")}
        }
    }}
}
