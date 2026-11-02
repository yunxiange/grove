@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.grove.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grove.reader.data.EpubArchive
import dev.grove.reader.data.EpubBook
import dev.grove.reader.data.EpubLink
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.encodePath
import dev.grove.reader.domain.encode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

class EpubNavigation(val position:Int,val total:Int)

@Composable
fun EpubPreview(document:Document,fontSize:Int,warm:Boolean,initialProgress:Float,
    onProgress:(Float)->Unit,onLink:(String)->Unit,showContents:Boolean,onDismissContents:()->Unit,modifier:Modifier=Modifier,
    bottomBar:@Composable (EpubNavigation)->Unit = {}) {
    val file=File(document.attachment!!.localPath)
    var error by remember(document.key){mutableStateOf<String?>(null)}
    val book by produceState<EpubBook?>(null,file.absolutePath) {
        try{value=withContext(Dispatchers.IO){EpubArchive.open(file)}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=if(e is IllegalArgumentException)e.message else "EPUB 无法读取，文件可能已损坏。"}
    }
    var selectedPath by rememberSaveable(document.key){mutableStateOf<String?>(null)}
    var chapterProgress by rememberSaveable(document.key){mutableFloatStateOf(0f)}
    var anchor by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(anchor){if(anchor!=null){delay(500);anchor=null}}
    val current=book
    if(current==null) {
        Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            if(error!=null)Text(error!!,Modifier.padding(24.dp),fontSize=12.sp,color=Muted)
            else CircularProgressIndicator(Modifier.size(25.dp),color=Muted)
        }
        bottomBar(EpubNavigation(0,0))
        }
        return
    }
    val restored=(initialProgress.coerceIn(0f,1f)*current.chapters.size).coerceAtMost(current.chapters.size.toFloat())
    LaunchedEffect(current){if(selectedPath==null){
        val index=restored.toInt().coerceAtMost(current.chapters.lastIndex)
        chapterProgress=(restored-index).coerceIn(0f,1f);selectedPath=current.chapters[index].path
    }}
    if(selectedPath==null){Column(modifier){Spacer(Modifier.weight(1f));bottomBar(EpubNavigation(0,0))};return}
    val index=current.chapters.indexOfFirst{it.path==selectedPath}.coerceAtLeast(0)
    val chapter=current.chapters[index]
    val activePath by rememberUpdatedState(chapter.path)
    fun select(link:EpubLink) {
        val next=current.chapters.indexOfFirst{it.path==link.path}
        if(next<0)return
        if(chapter.path!=link.path){chapterProgress=0f;selectedPath=link.path;onProgress(next.toFloat()/current.chapters.size)}
        anchor=link.fragment
        onDismissContents()
    }
    val source by produceState<Pair<String,String>?>(null,file.absolutePath,chapter.path) {
        error=null
        try{value=withContext(Dispatchers.IO){chapter.path to EpubArchive.chapter(file,chapter.path)}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){error=e.message?:"EPUB 章节无法读取。"}
    }
    // Keep the title on the page still displayed while the next page is being read.
    val displayedChapter=current.chapters.firstOrNull{it.path==source?.first}?:chapter
    if(showContents)ModalBottomSheet(onDismissRequest=onDismissContents,containerColor=Paper,tonalElevation=0.dp) {
        Text(current.title,Modifier.padding(horizontal=24.dp,vertical=12.dp),fontFamily=FontFamily.Serif,fontSize=20.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(current.author,Modifier.weight(1f),fontSize=11.sp,color=Muted)
            Text("${index+1} / ${current.chapters.size} 章",fontSize=10.sp,color=Muted)
        }
        HorizontalDivider(Modifier.padding(top=14.dp),color=Rule)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            itemsIndexed(current.contents){_,entry->
                Text(entry.title,Modifier.fillMaxWidth().clickable{select(entry.link)}.padding(start=(24+entry.depth*14).dp,end=24.dp,top=15.dp,bottom=15.dp),
                    fontSize=13.sp,color=if(entry.link.path==chapter.path)Ink else Muted)
            }
        }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("epub-chapter-header"),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={select(EpubLink(current.chapters[index-1].path))},enabled=index>0,
                modifier=Modifier.width(64.dp).heightIn(min=48.dp).semantics{contentDescription="上一章"},contentPadding=PaddingValues(horizontal=4.dp)) {
                Text("上一章",fontSize=11.sp,lineHeight=16.sp)
            }
            Text(displayedChapter.title,Modifier.weight(1f).padding(horizontal=8.dp),fontFamily=FontFamily.Serif,fontSize=12.sp,lineHeight=18.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick={select(EpubLink(current.chapters[index+1].path))},enabled=index<current.chapters.lastIndex,
                modifier=Modifier.width(64.dp).heightIn(min=48.dp).semantics{contentDescription="下一章"},contentPadding=PaddingValues(horizontal=4.dp)) {
                Text("下一章",fontSize=11.sp,lineHeight=16.sp)
            }
        }
        // Keep the same reader mounted while the next chapter loads. Replacing it
        // with a spinner or keying it by chapter would reload the entire WebView.
        val loaded=source
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if(loaded!=null) {
            val displayedPath=loaded.first
            val displayedIndex=current.chapters.indexOfFirst{it.path==displayedPath}.coerceAtLeast(0)
            val url=EpubArchive.rootUrl(file)+encodePath(displayedPath)
            val currentAnchor=anchor.takeIf{displayedPath==chapter.path}
            val chapterDocument=document.copy(path=document.path+"/"+displayedPath,content=loaded.second,originUrl=url+(currentAnchor?.let{"#"+encode(it)}?:""))
            DocumentReader(chapterDocument,fontSize,warm,chapterProgress,{target->
                if(target.startsWith(EpubArchive.rootUrl(file))) {
                    val uri=runCatching{URI(target)}.getOrNull()
                    val relative=uri?.rawPath?.substringAfter("/epub/${file.name}/")
                    if(relative!=null)EpubArchive.resolve(displayedPath,"/"+relative+(uri.rawFragment?.let{"#$it"}?:""))?.let(::select)
                }else onLink(target)
            },{_,value->if(activePath==displayedPath){
                chapterProgress=value
                onProgress(((displayedIndex+value)/current.chapters.size).coerceIn(0f,1f))
            }},{_,_->},currentAnchor,Modifier.fillMaxSize(),onPageSwipe={direction->
                val next=displayedIndex+direction
                if(activePath==displayedPath&&error==null&&next in current.chapters.indices)select(EpubLink(current.chapters[next].path))
            })
        }else if(error==null)CircularProgressIndicator(Modifier.align(Alignment.Center).size(25.dp),color=Muted)
        if(loaded!=null&&loaded.first!=chapter.path&&error==null)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp),color=Muted,trackColor=Rule)
        if(error!=null)Text(error!!,Modifier.align(Alignment.TopCenter).padding(24.dp),fontSize=12.sp,color=Muted)
        }
        Row(Modifier.fillMaxWidth().heightIn(min=16.dp).padding(horizontal=20.dp).testTag("epub-chapter-progress"),verticalAlignment=Alignment.CenterVertically) {
            Text("本章 ${(chapterProgress*100).toInt()}%",Modifier.width(60.dp),fontSize=9.sp,lineHeight=12.sp,color=Muted)
            LinearProgressIndicator(progress={chapterProgress.coerceIn(0f,1f)},modifier=Modifier.weight(1f).height(2.dp),color=Ink,trackColor=Rule)
        }
        bottomBar(EpubNavigation(index+1,current.chapters.size))
    }
}
