package dev.grove.reader.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.abs

private class PdfSession(file:File):AutoCloseable {
    private val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer=try{PdfRenderer(descriptor)}catch(e:Exception){descriptor.close();throw e}
    private var closed=false
    private val cache=LinkedHashMap<Pair<Int,Int>,Bitmap>(4,.75f,true)
    val pages get()=renderer.pageCount
    @Synchronized fun bitmap(page:Int,width:Int):Bitmap {
        check(!closed)
        val key=page to width
        cache[key]?.let{return it}
        val image=renderer.openPage(page).use {p->
            val factor=min(width.toFloat()/p.width,sqrt(4_000_000f/(p.width.toFloat()*p.height)))
            Bitmap.createBitmap((p.width*factor).toInt().coerceAtLeast(1),(p.height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE);p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        cache[key]=image
        while(cache.size>3||cache.values.sumOf{it.width.toLong()*it.height}>8_000_000L)cache.remove(cache.keys.first())
        return image
    }
    @Synchronized override fun close(){if(!closed){closed=true;cache.clear();renderer.close()}}
}

@Composable
fun PdfPreview(file:File,modifier:Modifier=Modifier,zoomPercent:Int=100) {
    var error by remember(file.absolutePath){mutableStateOf<String?>(null)}
    val session by produceState<PdfSession?>(null,file.absolutePath) {
        var opened:PdfSession?=null
        try {
            withContext(Dispatchers.IO){opened=PdfSession(file)}
            require(opened!!.pages>0)
            value=opened
            awaitCancellation()
        }
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=if(e is SecurityException)"此 PDF 已加密，暂不支持输入密码。"else"PDF 无法读取，文件可能已损坏。"}
        finally {withContext(NonCancellable+Dispatchers.IO){opened?.close()}}
    }
    var page by rememberSaveable(file.absolutePath){mutableIntStateOf(0)}
    val zoom=zoomPercent/100f
    Column(modifier) {
        val current=session
        if(error!=null){Text(error!!,Modifier.padding(24.dp),fontSize=13.sp,color=Muted);return@Column}
        if(current==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Muted)};return@Column}
        PdfToolbar(page,current.pages,{page=it})
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val width=with(LocalDensity.current){maxWidth.toPx()}
            val bitmap by produceState<Pair<Int,Bitmap>?>(null,current,page,zoom,width) {
                try{
                    val pixels=(width*zoom).toInt().coerceIn(300,2000)
                    value=withContext(Dispatchers.IO){page to current.bitmap(page,pixels)}
                    delay(220)
                    for(next in listOf(page+1,page-1).filter{it in 0 until current.pages}) {
                        try{withContext(Dispatchers.IO){current.bitmap(next,pixels)}}
                        catch(e:CancellationException){throw e}
                        catch(_:Exception){/* A failed prefetch must not hide the current page. */}
                    }
                }
                catch(e:CancellationException){throw e}
                catch(_:Exception){error="此页面无法渲染。"}
            }
            val image=bitmap
            if(image==null)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(25.dp),color=Muted)}
            else PdfPageImage(image.second,image.first,zoom,{direction->
                if(image.first==page)page=(page+direction).coerceIn(0,current.pages-1)
            },Modifier.fillMaxSize())
        }
    }
}

@Composable internal fun PdfPageImage(image:Bitmap,page:Int,zoom:Float,onSwipe:(Int)->Unit,modifier:Modifier=Modifier) {
    val swipe by rememberUpdatedState(onSwipe)
    val horizontal=key(page){rememberScrollState()}
    val vertical=key(page){rememberScrollState()}
    val threshold=with(LocalDensity.current){72.dp.toPx()}
    val slide=with(LocalDensity.current){22.dp.toPx()}
    val maxDrag=with(LocalDensity.current){48.dp.toPx()}
    var previousPage by remember{mutableIntStateOf(page)}
    val offset=remember(page){Animatable(if(page==previousPage)0f else if(page>previousPage)slide else -slide)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(page){previousPage=page;if(offset.value!=0f)offset.animateTo(0f,tween(160))}
    BoxWithConstraints(modifier.testTag("pdf-page-viewport").clipToBounds()) {
        val displayWidth=maxWidth*zoom
        Box(Modifier.fillMaxSize().pointerInput(page,horizontal,threshold) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Final)
                scope.launch{offset.stop();offset.snapTo(0f)}
                val atLeft=horizontal.value==0
                val atRight=horizontal.value>=horizontal.maxValue
                var invalid=false
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Final)
                    if(event.changes.size>1)invalid=true
                    val change=event.changes.firstOrNull{it.id==down.id}?:break
                    val delta=change.position-down.position
                    if(abs(delta.y)>viewConfiguration.touchSlop&&abs(delta.y)>=abs(delta.x))invalid=true
                    if(!invalid&&abs(delta.x)>viewConfiguration.touchSlop&&abs(delta.x)>abs(delta.y)*1.5f&&
                        ((delta.x<0&&atRight)||(delta.x>0&&atLeft))) {
                        val drag=(delta.x*.35f).coerceIn(-maxDrag,maxDrag)
                        scope.launch{offset.snapTo(drag)}
                    }else if(invalid)scope.launch{offset.snapTo(0f)}
                    if(!change.pressed) {
                        if(!invalid&&abs(delta.x)>=threshold&&abs(delta.x)>abs(delta.y)*1.5f) {
                            if(delta.x<0&&atRight)swipe(1)
                            if(delta.x>0&&atLeft)swipe(-1)
                        }
                        scope.launch{offset.animateTo(0f,tween(130))}
                        break
                    }
                }
            }
        }.horizontalScroll(horizontal).verticalScroll(vertical)) {
            Image(image.asImageBitmap(),"PDF 第 ${page+1} 页",Modifier.width(displayWidth).height(displayWidth*(image.height.toFloat()/image.width))
                .graphicsLayer{translationX=offset.value})
        }
    }
}

@Composable internal fun PdfToolbar(page:Int,pages:Int,onPage:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("pdf-toolbar"),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick={onPage(page-1)},enabled=page>0,modifier=Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack,"上一页",Modifier.size(18.dp),tint=if(page>0)Ink else Muted.copy(alpha=.4f))
        }
        Text("${page+1} / $pages",Modifier.weight(1f),fontSize=10.sp,color=Muted,maxLines=1,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick={onPage(page+1)},enabled=page+1<pages,modifier=Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward,"下一页",Modifier.size(18.dp),tint=if(page+1<pages)Ink else Muted.copy(alpha=.4f))
        }
    }
}

@Composable internal fun PdfZoomControls(zoomPercent:Int,onZoom:(Int)->Unit) {
    val percentageWidth=36.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(Modifier.testTag("pdf-zoom-controls"),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick={onZoom((zoomPercent-10).coerceAtLeast(100))},enabled=zoomPercent>100,modifier=Modifier.width(28.dp).height(48.dp)) {
            Icon(Icons.Outlined.Remove,"缩小 10%",Modifier.size(16.dp),tint=if(zoomPercent>100)Ink else Muted.copy(alpha=.4f))
        }
        TextButton(onClick={onZoom(100)},modifier=Modifier.width(percentageWidth).heightIn(min=48.dp).semantics{contentDescription="恢复适宽，当前缩放 $zoomPercent%"},
            contentPadding=PaddingValues(0.dp)) {
            Text("$zoomPercent%",fontSize=11.sp,color=Ink,maxLines=1)
        }
        IconButton(onClick={onZoom((zoomPercent+10).coerceAtMost(300))},enabled=zoomPercent<300,modifier=Modifier.width(28.dp).height(48.dp)) {
            Icon(Icons.Outlined.Add,"放大 10%",Modifier.size(16.dp),tint=if(zoomPercent<300)Ink else Muted.copy(alpha=.4f))
        }
    }
}
