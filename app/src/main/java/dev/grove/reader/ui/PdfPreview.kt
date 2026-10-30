package dev.grove.reader.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt

private class PdfSession(file:File):AutoCloseable {
    private val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer=try{PdfRenderer(descriptor)}catch(e:Exception){descriptor.close();throw e}
    private var closed=false
    val pages get()=renderer.pageCount
    @Synchronized fun bitmap(page:Int,width:Int):Bitmap {
        check(!closed)
        return renderer.openPage(page).use {p->
            val factor=min(width.toFloat()/p.width,sqrt(4_000_000f/(p.width.toFloat()*p.height)))
            Bitmap.createBitmap((p.width*factor).toInt().coerceAtLeast(1),(p.height*factor).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also {
                it.eraseColor(android.graphics.Color.WHITE);p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
    @Synchronized override fun close(){if(!closed){closed=true;renderer.close()}}
}

@Composable
fun PdfPreview(file:File,modifier:Modifier=Modifier) {
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
        finally {opened?.close()}
    }
    var page by remember(file.absolutePath){mutableIntStateOf(0)}
    var zoom by remember(file.absolutePath){mutableFloatStateOf(1f)}
    Column(modifier) {
        val current=session
        if(error!=null){Text(error!!,Modifier.padding(24.dp),fontSize=13.sp,color=Muted);return@Column}
        if(current==null){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Muted)};return@Column}
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={page--},enabled=page>0){Text("上一页",fontSize=10.sp)}
            Text("${page+1} / ${current.pages}",Modifier.weight(1f),fontSize=10.sp,color=Muted)
            TextButton(onClick={page++},enabled=page+1<current.pages){Text("下一页",fontSize=10.sp)}
            TextButton(onClick={zoom=if(zoom>=3f)1f else zoom+.5f}){Text("${(zoom*100).toInt()}%",fontSize=10.sp)}
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val displayWidth=maxWidth
            val width=with(LocalDensity.current){maxWidth.toPx()}
            val bitmap by produceState<Bitmap?>(null,current,page,zoom,width) {
                try{value=withContext(Dispatchers.IO){current.bitmap(page,(width*zoom).toInt().coerceIn(300,2000))}}
                catch(e:CancellationException){throw e}
                catch(_:Exception){error="此页面无法渲染。"}
            }
            val image=bitmap
            if(image==null)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(25.dp),color=Muted)}
            else Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
                Image(image.asImageBitmap(),"PDF 第 ${page+1} 页",Modifier.width(displayWidth*zoom).height(displayWidth*zoom*(image.height.toFloat()/image.width)))
            }
        }
    }
}
