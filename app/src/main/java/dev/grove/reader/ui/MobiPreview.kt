@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.grove.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grove.reader.domain.Document
import org.json.JSONObject

data class MobiRequest(val section:Int=-1,val href:String?=null,val sequence:Int=0,val progress:Float=0f)
private data class MobiContents(val title:String,val index:Int,val href:String?=null,val depth:Int=0)
private data class MobiState(val bookTitle:String,val author:String,val title:String,val index:Int,val total:Int,val contents:List<MobiContents>)

/** Native navigation and persistence surround the local, bundled MOBI/KF8 parser. */
@Composable fun MobiPreview(document:Document,fontSize:Int,warm:Boolean,initialProgress:Float,
    onProgress:(Float)->Unit,onLink:(String)->Unit,showContents:Boolean,onDismissContents:()->Unit,modifier:Modifier=Modifier,
    bottomBar:@Composable (EpubNavigation)->Unit={}) {
    var state by remember(document.key){mutableStateOf<MobiState?>(null)}
    var requested by rememberSaveable(document.key){mutableIntStateOf(-1)}
    var href by rememberSaveable(document.key){mutableStateOf<String?>(null)}
    var sequence by rememberSaveable(document.key){mutableIntStateOf(0)}
    var presented by remember(document.key){mutableIntStateOf(-1)}
    var chapterProgress by rememberSaveable(document.key){mutableFloatStateOf(0f)}
    var error by remember(document.key){mutableStateOf<String?>(null)}
    val current=state
    val busy=presented!=sequence
    val initial=remember(document.key){initialProgress}
    fun select(index:Int,link:String?=null) {
        if(current==null||busy||index !in 0 until current.total)return
        requested=index;href=link;chapterProgress=0f;sequence++;error=null
        onDismissContents()
    }
    if(showContents&&current!=null)ModalBottomSheet(onDismissRequest=onDismissContents,containerColor=Paper,tonalElevation=0.dp) {
        Text(current.bookTitle,Modifier.padding(horizontal=24.dp,vertical=12.dp),fontFamily=FontFamily.Serif,fontSize=20.sp)
        Text(current.author,Modifier.padding(horizontal=24.dp),fontSize=11.sp,color=Muted)
        HorizontalDivider(Modifier.padding(top=14.dp),color=Rule)
        val entries=current.contents.ifEmpty{(0 until current.total).map{
            MobiContents(if(it==current.index)current.title.ifBlank{"页面 ${it+1}"} else "页面 ${it+1}",it)
        }}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(entries){entry->Text(entry.title,Modifier.fillMaxWidth().clickable(enabled=!busy){select(entry.index,entry.href)}
                .padding(start=(24+entry.depth*14).dp,end=24.dp,top=15.dp,bottom=15.dp),fontSize=13.sp,color=if(entry.index==current.index)Ink else Muted)}
        }
    }
    Column(modifier) {
        EbookChapterHeader(current?.title?:if(error==null)"正在读取电子书"else"无法读取电子书",current?.index?:0,current?.total?:0,
            {current?.let{select(it.index-1)}},{current?.let{select(it.index+1)}},enabled=!busy&&error==null)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DocumentReader(document,fontSize,warm,initial,onLink,{_,value->
                if(!busy&&error==null&&current!=null&&value.isFinite()) {
                    chapterProgress=value.coerceIn(0f,1f)
                    onProgress(((current.index+chapterProgress)/current.total).coerceIn(0f,1f))
                }
            },{_,_->},null,Modifier.fillMaxSize(),onPageSwipe={direction->current?.let{select(it.index+direction)}},
                bookRequest=MobiRequest(requested,href,sequence,chapterProgress),onBookLink={link->current?.let{select(it.index,link)}},
                onBookState={json->
                    runCatching {
                        val data=JSONObject(json)
                        if(data.has("error")){error=data.getString("error").take(300);presented=sequence}
                        else {
                            val total=data.getInt("total");val index=data.getInt("section")
                            require(total in 1..500&&index in 0 until total)
                            val array=data.getJSONArray("contents");require(array.length()<=2000)
                            val contents=(0 until array.length()).mapNotNull{i->val item=array.getJSONObject(i)
                                val target=item.getInt("index")
                                if(target !in 0 until total)null else MobiContents(item.getString("title").take(160),target,
                                    if(item.isNull("href"))null else item.getString("href").take(200),item.optInt("depth").coerceIn(0,3))}
                            state=MobiState(data.getString("bookTitle").take(160),data.optString("author").take(500),data.getString("title").take(160),index,total,contents)
                            chapterProgress=data.optDouble("progress",0.0).toFloat().takeIf{it.isFinite()}?.coerceIn(0f,1f)?:0f
                            presented=sequence;error=null
                            onProgress(((index+chapterProgress)/total).coerceIn(0f,1f))
                        }
                    }.onFailure{error="电子书目录无法读取。";presented=sequence}
                })
        }
        EbookChapterProgress(chapterProgress)
        bottomBar(EpubNavigation(if(current==null)0 else current.index+1,current?.total?:0))
    }
}
