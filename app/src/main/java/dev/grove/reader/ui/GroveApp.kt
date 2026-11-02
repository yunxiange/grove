@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.grove.reader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.grove.reader.BuildConfig
import dev.grove.reader.GroveState
import dev.grove.reader.GroveViewModel
import dev.grove.reader.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Paper=Color(0xFFF9F7F1)
val Ink=Color(0xFF302E28)
val Muted=Color(0xFF706C63)
val Rule=Color(0xFFD8D1C4)
private val PaperColors=lightColorScheme(primary=Ink,onPrimary=Paper,secondary=Muted,background=Paper,
    surface=Paper,onSurface=Ink,onBackground=Ink,surfaceVariant=Color(0xFFEFEAE0),onSurfaceVariant=Muted,
    outline=Rule,outlineVariant=Rule,primaryContainer=Color(0xFFE8E2D5),onPrimaryContainer=Ink)

@Composable
fun GroveApp(model:GroveViewModel) {
    val s by model.state.collectAsStateWithLifecycle()
    val screens=key(s.username){rememberSaveableStateHolder()}
    val screenKey="${s.username}:${s.destination}:"+if(s.project==null)"home"else
        "${s.project!!.id.fullName}:${s.ref}:${s.tab}:"+when(s.tab){RepoTab.READ->s.document?.path?:"loading";RepoTab.ISSUES->s.issue?.number?:"list";RepoTab.FILES->"files"}
    // Keep list state above the navigation branches: repository details remove DiscoveryPage.
    val discoveryListState=rememberSaveable(s.category,s.language,s.newProjects,s.discoveryQuery,s.username,s.discoveryRevision,saver=LazyListState.Saver){LazyListState()}
    val discoveryCategoryScroll=rememberSaveable(s.username,saver=ScrollState.Saver){ScrollState(0)}
    val context=LocalContext.current
    val imageSaveLauncher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        model.finishImageSave(if(result.resultCode==Activity.RESULT_OK)result.data?.data else null)
    }
    LaunchedEffect(s.imageSavePrompt) {
        s.imageSavePrompt?.let{prompt->
            model.imagePickerOpened()
            try {imageSaveLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
                addCategory(Intent.CATEGORY_OPENABLE);type=prompt.mimeType;putExtra(Intent.EXTRA_TITLE,prompt.fileName)
            })}catch(_:ActivityNotFoundException){model.imageSaveFailed("未找到系统文件保存窗口，请检查设备设置。")}
        }
    }
    LaunchedEffect(s.imageSaveMessage) {
        s.imageSaveMessage?.let{Toast.makeText(context,it,Toast.LENGTH_LONG).show();model.clearImageSaveMessage()}
    }
    LaunchedEffect(s.imageSaving) {
        if(s.imageSaving)Toast.makeText(context,"正在保存图片…",Toast.LENGTH_SHORT).show()
    }
    var add by remember {mutableStateOf(false)}
    var settings by remember {mutableStateOf(false)}
    var branches by remember {mutableStateOf(false)}
    var toc by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    var imports by remember {mutableStateOf(false)}
    var importing by remember {mutableStateOf(false)}
    var addedRepos by remember {mutableStateOf(false)}
    var repositoryMore by remember(s.username,s.project?.id){mutableStateOf(false)}
    LaunchedEffect(imports,s.username){if(imports&&s.username.isNotBlank())model.loadMyRepositories()}
    var jump by remember(s.document?.key) {mutableStateOf<String?>(null)}
    val loadingFile=s.openingDocument!=null&&s.tab==RepoTab.READ
    fun openLink(url:String) {
        if(model.followLink(url))return
        val uri=Uri.parse(url)
        if(uri.scheme in listOf("https","http","mailto")) try { context.startActivity(Intent(Intent.ACTION_VIEW,uri)) } catch(_:ActivityNotFoundException) {}
    }
    MaterialTheme(colorScheme=PaperColors,typography=Typography(),shapes=Shapes(
        extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
        small=androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        medium=androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        large=androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        extraLarge=androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
        BackHandler(enabled=s.project!=null){model.back()}
        Scaffold(containerColor=Paper,bottomBar={if(!s.immersive||loadingFile)BottomNavigation(s.destination,model::navigate)}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                screens.SaveableStateProvider(screenKey) {
                if(s.project!=null) {
                    if(!s.immersive||loadingFile) {
                        val issues=s.tab==RepoTab.ISSUES
                        if(issues)CompactHeader("Issues",onBack=model::back,subtitle=s.project!!.id.fullName)
                        else RepositoryHeader(s.project!!.id,model::back,{repositoryMore=true}){RepositoryStar(s.project!!,s,model)}
                        HorizontalDivider(color=Rule)
                    }
                    if(s.loading&&!loadingFile)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp),color=Muted,trackColor=Rule)
                    if(!loadingFile)s.error?.let { ErrorNotice(it,model::retry) }
                    when(s.tab) {
                        RepoTab.READ -> ReaderPage(s,model,{settings=true},{toc=true},model::openFilePicker,::openLink,jump)
                        RepoTab.FILES -> FilesPage(s,model)
                        RepoTab.ISSUES -> IssuesPage(s,model,::openLink)
                    }
                } else {
                    when(s.destination) {
                        Destination.LIBRARY -> LibraryPage(s,model,onAdd={add=true},onProjects={importing=false;imports=true})
                        Destination.SAVED -> SavedPage(s,model)
                        Destination.DISCOVER -> DiscoveryPage(s,model,discoveryListState,discoveryCategoryScroll)
                        Destination.PROFILE -> ProfilePage(s,model,{settings=true},{about=true},{importing=false;imports=true},{addedRepos=true})
                    }
                }
                }
            }
        }
        if(add)AddRepositoryDialog({add=false},model::addRepository){add=false;importing=true;imports=true}
        s.filePicker?.let{picker->
            ModalBottomSheet(onDismissRequest=model::dismissFilePicker,containerColor=Paper,tonalElevation=0.dp,
                sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
                Column(Modifier.fillMaxWidth().heightIn(min=260.dp,max=540.dp)) {
                    var query by remember(picker.directory){mutableStateOf("")}
                    var search by remember(picker.directory){mutableStateOf(false)}
                    val focusManager=LocalFocusManager.current
                    CompactHeader("选择文件",subtitle=s.project?.id?.name,
                        onBack=if(picker.directory.isNotBlank())({model.loadPickerDirectory(picker.directory.substringBeforeLast('/',""))})else null) {
                        IconButton(onClick={search=!search;if(!search){query="";focusManager.clearFocus()}}) {
                            Icon(Icons.Outlined.Search,if(search)"收起文件搜索"else"搜索文件",Modifier.size(19.dp),tint=if(search)Ink else Muted)
                        }
                        IconButton(onClick=model::dismissFilePicker){Icon(Icons.Outlined.Close,"关闭文件面板",Modifier.size(19.dp))}
                    }
                    HorizontalDivider(color=Rule)
                    Row(Modifier.fillMaxWidth().padding(start=14.dp,end=22.dp),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={model.loadPickerDirectory("")},enabled=picker.directory.isNotBlank(),modifier=Modifier.weight(1f)) {
                            Text(if(picker.directory.isBlank())"/"else"/ ${picker.directory}",fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Text(s.ref,fontSize=10.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=100.dp))
                    }
                    if(search)InlineSearch(query,{query=it},"筛选当前目录","清除文件搜索")
                    if(picker.loading)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp),color=Muted,trackColor=Rule)
                    picker.error?.let{ErrorNotice(it){model.loadPickerDirectory(picker.directory)}}
                    val listState=rememberLazyListState()
                    LaunchedEffect(picker.directory){listState.scrollToItem(0)}
                    val files=picker.files.filter{it.name.contains(query,true)}
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=listState,contentPadding=PaddingValues(horizontal=22.dp,vertical=9.dp)) {
                        items(files,key={it.path}){file->RepositoryFileRow(file,file.path==s.document?.path){model.selectPickerFile(file)}}
                        if(files.isEmpty()&&!picker.loading&&picker.error==null)item {
                            Text(if(query.isBlank())"这个目录是空的。"else"没有匹配的文件。",Modifier.padding(vertical=24.dp),fontSize=12.sp,color=Muted)
                        }
                        if(picker.files.size>=1000)item{Text("此目录可能超过 GitHub Contents API 的显示上限。",Modifier.padding(vertical=15.dp),fontSize=11.sp,color=Muted)}
                    }
                }
            }
        }
        if(s.imageToSave!=null)ModalBottomSheet(onDismissRequest=model::dismissImageActions,containerColor=Paper) {
            Text("图片",Modifier.padding(start=24.dp,bottom=10.dp),fontFamily=FontFamily.Serif,fontSize=20.sp)
            Row(Modifier.fillMaxWidth().clickable(onClick=model::saveSelectedImage).padding(horizontal=24.dp,vertical=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.Download,null,Modifier.size(21.dp),tint=Muted)
                Text("保存图片",Modifier.padding(start=16.dp),fontSize=14.sp)
            }
            Text("保存原始图片到设备",Modifier.padding(start=24.dp,bottom=24.dp),fontSize=10.sp,color=Muted)
        }
        if(imports)ModalBottomSheet(onDismissRequest={imports=false},containerColor=Paper) {
            val listState=rememberLazyListState()
            LoadOnScrollEnd(listState,s.userRepoPage,s.moreUserRepos&&!s.loading&&s.error==null&&s.username.isNotBlank()){model.loadMyRepositories(true)}
            CompactHeader(if(importing)"导入 GitHub 仓库"else"我的项目") {IconButton(onClick={model.loadMyRepositories()}){Icon(Icons.Outlined.Refresh,"刷新")}}
            if(s.username.isBlank()){Text("登录后查看你的公开与私有仓库。",Modifier.padding(24.dp),fontSize=12.sp);Button(onClick=model::login,modifier=Modifier.padding(24.dp)){Text("登录 GitHub")}}
            if(s.loading)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Muted)
            s.error?.let{ErrorNotice(it,model::retry)}
            LazyColumn(Modifier.heightIn(max=430.dp).padding(horizontal=22.dp),state=listState){
                if(s.username.isNotBlank()&&s.userRepos.isEmpty()&&!s.loading&&s.error==null)item{EmptyNotice("暂时没有可访问的项目。","私有仓库需要先授予账号访问权限，再回来刷新。")}
                items(s.userRepos,key={it.id.fullName}){repo->ProjectRow(repo,onClick={imports=false;if(importing)model.addRepository(repo.id.fullName)else model.openRepository(repo.id)})}
                if(s.moreUserRepos)item{PageLoading(s.loading)}
            }
            if(s.username.isNotBlank()&&BuildConfig.GITHUB_APP_SLUG.isNotBlank())TextButton(onClick={openLink("https://github.com/apps/${BuildConfig.GITHUB_APP_SLUG}/installations/new")},modifier=Modifier.padding(14.dp)){Text("管理私有仓库访问权限",fontSize=11.sp)}
        }
        if(addedRepos)ModalBottomSheet(onDismissRequest={addedRepos=false},containerColor=Paper) {
            CompactHeader("已添加的仓库")
            LazyColumn(Modifier.heightIn(max=430.dp).padding(horizontal=22.dp)) {
                if(s.library.isEmpty())item{EmptyNotice("还没有手动添加的仓库。","使用仓库页右上角 + 添加，或从 GitHub 导入。")}
                items(s.library,key={it.fullName}){id->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)){ProjectRow(s.projects[id.fullName]?:Project(id),onClick={addedRepos=false;model.openRepository(id)})}
                        IconButton(onClick={model.removeRepository(id)}){Icon(Icons.Outlined.Close,"移除已添加的 ${id.fullName}",Modifier.size(18.dp))}
                    }
                }
            }
        }
        if(settings)ModalBottomSheet(onDismissRequest={settings=false},containerColor=Paper) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(17.dp)) {
                Text("阅读偏好",fontSize=20.sp,fontFamily=FontFamily.Serif)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("文字大小",Modifier.weight(1f),fontSize=13.sp)
                    IconButton(onClick={model.settings(font=s.fontSize-1)}){Icon(Icons.Outlined.Remove,"缩小文字")}
                    Text("${s.fontSize}",fontSize=14.sp)
                    IconButton(onClick={model.settings(font=s.fontSize+1)}){Icon(Icons.Outlined.Add,"放大文字")}
                }
                Text("纸面颜色",fontSize=12.sp,color=Muted)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected=!s.warm,onClick={model.settings(warm=false)},label={Text("纸白")})
                    FilterChip(selected=s.warm,onClick={model.settings(warm=true)},label={Text("暖纸")})
                }
                Spacer(Modifier.height(14.dp))
            }
        }
        if(repositoryMore&&s.project!=null)ModalBottomSheet(onDismissRequest={repositoryMore=false},containerColor=Paper,tonalElevation=0.dp) {
            CompactHeader("仓库操作",subtitle=s.project!!.id.fullName) {
                IconButton(onClick={repositoryMore=false}){Icon(Icons.Outlined.Close,"关闭仓库操作",Modifier.size(19.dp))}
            }
            HorizontalDivider(color=Rule)
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp)) {
                Text("当前分支：${s.ref}",Modifier.padding(top=18.dp,bottom=4.dp),fontSize=10.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                MoreAction(Icons.Outlined.AccountTree,"切换分支"){repositoryMore=false;branches=true;model.loadBranches()}
                MoreAction(Icons.Outlined.Adjust,"查看 Issues"){repositoryMore=false;model.selectTab(RepoTab.ISSUES)}
                Spacer(Modifier.height(24.dp))
            }
        }
        if(branches)ModalBottomSheet(onDismissRequest={branches=false},containerColor=Paper) {
            Text("切换分支",Modifier.padding(horizontal=24.dp,vertical=12.dp),fontFamily=FontFamily.Serif,fontSize=21.sp)
            if(s.loading&&s.branches.isEmpty())CircularProgressIndicator(Modifier.padding(24.dp).size(24.dp),color=Muted)
            LazyColumn(Modifier.heightIn(max=390.dp)) {
                items(s.branches) {ref-> Row(Modifier.fillMaxWidth().clickable {branches=false;model.chooseBranch(ref)}.padding(horizontal=24.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AccountTree,null,Modifier.size(17.dp));Spacer(Modifier.width(13.dp));Text(ref,Modifier.weight(1f),fontSize=13.sp)
                    if(s.ref==ref)Icon(Icons.Outlined.Check,"当前分支",Modifier.size(17.dp))
                }}
            }
            Text("显示前 100 个分支",Modifier.padding(24.dp),color=Muted,fontSize=10.sp)
        }
        if(toc)ModalBottomSheet(onDismissRequest={toc=false},containerColor=Paper) {
            Text("阅读目录",Modifier.padding(24.dp),fontSize=21.sp,fontFamily=FontFamily.Serif)
            if(s.headings.isEmpty())Text("当前文档暂无章节目录。",Modifier.padding(24.dp),fontSize=13.sp,color=Muted)
            LazyColumn(Modifier.heightIn(max=440.dp)) {items(s.headings){ h ->
                Text(h.text,Modifier.fillMaxWidth().clickable {jump=h.id;toc=false}.padding(start=(24+(h.level-1)*10).dp,end=24.dp,top=14.dp,bottom=14.dp),fontSize=13.sp,color=if(h.level==1)Ink else Muted)
            }}
        }
        if(about)AlertDialog(onDismissRequest={about=false},title={Text("关于 Grove",fontFamily=FontFamily.Serif)},text={Text("一座随身的开源图书馆。\n\n墨与纸 · ${BuildConfig.VERSION_NAME}\n\n仓库与讨论来自 GitHub。文档书签、阅读位置和偏好保存在本机。发现按累计 Star 或近 30 天新项目排序。",lineHeight=23.sp)},confirmButton={TextButton(onClick={about=false}){Text("知道了")}})
        if(s.authError!=null)AlertDialog(onDismissRequest=model::cancelLogin,title={Text("GitHub 登录")},text={Text(s.authError!!,lineHeight=24.sp)},confirmButton={TextButton(onClick=model::cancelLogin){Text("继续阅读")}})
        if(s.starError!=null)AlertDialog(onDismissRequest=model::clearStarError,title={Text("GitHub Star")},text={Text(s.starError!!,lineHeight=24.sp)},confirmButton={TextButton(onClick=model::clearStarError){Text("知道了")}})
        if(s.authDevice!=null)AlertDialog(onDismissRequest=model::cancelLogin,title={Text("授权 GitHub")},text={Column(verticalArrangement=Arrangement.spacedBy(15.dp)) {
            Text("在 GitHub 授权页输入以下代码：",fontSize=13.sp)
            SelectionContainer {Text(s.authDevice!!.userCode,fontFamily=FontFamily.Monospace,fontSize=27.sp)}
            Text("授权完成后，Grove 会自动登录。",fontSize=12.sp,color=Muted)
            LinearProgressIndicator(Modifier.fillMaxWidth(),color=Muted)
        }},confirmButton={TextButton(onClick={openLink(s.authDevice!!.verificationUri)}){Text("打开 GitHub")}},dismissButton={TextButton(onClick=model::cancelLogin){Text("取消")}})
    }
}

@Composable private fun BottomNavigation(selected:Destination,onSelect:(Destination)->Unit) {
    Column {HorizontalDivider(color=Rule);NavigationBar(containerColor=Paper,tonalElevation=0.dp,windowInsets=WindowInsets.navigationBars) {
        Destination.entries.forEach {d->NavigationBarItem(selected=selected==d,onClick={onSelect(d)},icon={Icon(when(d){
            Destination.LIBRARY->Icons.AutoMirrored.Outlined.MenuBook
            Destination.SAVED->Icons.Outlined.BookmarkBorder
            Destination.DISCOVER->Icons.Outlined.Explore
            Destination.PROFILE->Icons.Outlined.PersonOutline
        },d.label,Modifier.size(21.dp))},label={Text(d.label,fontSize=10.sp,fontWeight=if(selected==d)FontWeight.Bold else FontWeight.Normal)},colors=NavigationBarItemDefaults.colors(indicatorColor=Ink,selectedIconColor=Paper,unselectedIconColor=Muted,selectedTextColor=Ink,unselectedTextColor=Muted))}
    }}
}

@Composable private fun CompactHeader(title:String,onBack:(()->Unit)?=null,subtitle:String?=null,actions:@Composable RowScope.()->Unit={}) {
    Row(Modifier.fillMaxWidth().heightIn(min=53.dp).padding(start=if(onBack!=null)6.dp else 22.dp,end=9.dp),verticalAlignment=Alignment.CenterVertically) {
        if(onBack!=null)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回",Modifier.size(19.dp))}
        Column(Modifier.weight(1f)) {
            if(subtitle!=null)Text(subtitle,fontSize=9.sp,color=Muted,maxLines=1)
            Text(title,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable private fun RepositoryHeader(id:RepoId,onBack:()->Unit,onMore:()->Unit,star:@Composable ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=53.dp).padding(start=6.dp,end=9.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回",Modifier.size(19.dp))}
        Row(Modifier.weight(1f).padding(end=8.dp).semantics(mergeDescendants=true){contentDescription=id.fullName}) {
            Text(id.owner,Modifier.widthIn(max=88.dp).alignByBaseline(),fontSize=12.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(" / ",Modifier.alignByBaseline(),fontSize=12.sp,color=Muted)
            Text(id.name,Modifier.weight(1f).alignByBaseline(),fontSize=16.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        star()
        IconButton(onClick=onMore){Icon(Icons.Outlined.MoreHoriz,"更多仓库操作",Modifier.size(22.dp))}
    }
}

@Composable private fun ErrorNotice(message:String,retry:()->Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFEEE8DC)).padding(start=19.dp,end=8.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(message,Modifier.weight(1f),fontSize=11.sp,lineHeight=18.sp,color=Ink)
        TextButton(onClick=retry){Text("重试",fontSize=11.sp)}
    }
}
@Composable private fun EmptyNotice(title:String,description:String,icon:ImageVector=Icons.AutoMirrored.Outlined.MenuBook) {
    Column(Modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Spacer(Modifier.height(12.dp));Icon(icon,null,Modifier.size(30.dp),tint=Muted)
        Text(title,fontSize=19.sp,fontFamily=FontFamily.Serif)
        Text(description,fontSize=12.sp,color=Muted,lineHeight=22.sp)
    }
}
@Composable private fun BookCover(id:RepoId,modifier:Modifier=Modifier,small:Boolean=false) {
    val dark=(id.fullName.hashCode() and 1)==0
    val bg=if(dark)Color(0xFF3A3832)else Color(0xFFDAD5C9)
    val fg=if(dark)Paper else Ink
    Column(modifier.background(bg).border(1.dp,Color(0xFFB9B0A0)).padding(if(small)7.dp else 16.dp),verticalArrangement=Arrangement.SpaceBetween) {
        Text(id.owner.uppercase(),color=fg.copy(alpha=.8f),fontSize=if(small)4.sp else 7.sp,maxLines=1)
        Text(id.name.replace('-','\n'),color=fg,fontFamily=FontFamily.Serif,fontSize=if(small)11.sp else 25.sp,lineHeight=if(small)13.sp else 29.sp,maxLines=if(small)3 else 4,overflow=TextOverflow.Ellipsis)
        Column {HorizontalDivider(Modifier.width(if(small)12.dp else 28.dp),color=fg.copy(alpha=.6f));Spacer(Modifier.height(6.dp));Text(if(small)"Grove" else "OPEN SOURCE EDITION",color=fg.copy(alpha=.8f),fontSize=if(small)5.sp else 6.sp,letterSpacing=1.sp)}
    }
}

@Composable private fun ColumnScope.LibraryPage(s:GroveState,model:GroveViewModel,onAdd:()->Unit,onProjects:()->Unit) {
    var search by remember {mutableStateOf(false)};var query by rememberSaveable {mutableStateOf("")};val grid=s.libraryGrid
    var sorting by remember {mutableStateOf(false)}
    CompactHeader("访问记录") {
        IconButton(onClick={search=!search}){Icon(Icons.Outlined.Search,"搜索最近项目",Modifier.size(20.dp))}
        IconButton(onClick=onAdd){Icon(Icons.Outlined.Add,"添加仓库",Modifier.size(20.dp))}
    }
    HorizontalDivider(color=Rule)
    if(search)OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=8.dp),placeholder={Text("搜索最近访问的项目",fontSize=12.sp)},singleLine=true)
    Row(Modifier.fillMaxWidth().padding(start=22.dp,end=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("${s.recentVisits.size} 个项目",Modifier.weight(1f),fontSize=10.sp,color=Muted)
        Box {
            TextButton(onClick={sorting=true}){Icon(Icons.AutoMirrored.Outlined.Sort,"排序最近项目",Modifier.size(15.dp));Text(" ${s.recentSort.label}",fontSize=10.sp)}
            DropdownMenu(expanded=sorting,onDismissRequest={sorting=false}) {
                RecentSort.entries.forEach{sort->DropdownMenuItem(text={Text(sort.label,fontSize=12.sp)},
                    trailingIcon={if(sort==s.recentSort)Icon(Icons.Outlined.Check,null,Modifier.size(16.dp))},
                    onClick={sorting=false;model.setRecentSort(sort)})}
            }
        }
        IconButton(onClick={model.setLibraryGrid(!grid)}){Icon(if(grid)Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,if(grid)"列表视图" else "封面视图",Modifier.size(17.dp))}
    }
    val visits=remember(s.recentVisits,s.projects,s.recentSort,query) {
        sortedRecentVisits(s.recentVisits,s.projects,s.recentSort).filter{
            (it.repo.fullName+" "+s.projects[it.repo.fullName]?.description.orEmpty()).contains(query,true)
        }
    }
    if(visits.isEmpty()) {
        if(s.recentVisits.isEmpty()) {
            EmptyNotice("还没有访问过项目。","打开一个项目后，它会出现在这里。",Icons.Outlined.History)
            Row(Modifier.align(Alignment.CenterHorizontally),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={model.navigate(Destination.DISCOVER)}){Text("去发现",fontSize=12.sp)}
                Button(onClick=onProjects){Text("我的项目",fontSize=12.sp)}
            }
        }else EmptyNotice("没有匹配的项目。","换个关键词再试试。",Icons.Outlined.Search)
    }
    if(grid)LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.weight(1f),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(22.dp),verticalArrangement=Arrangement.spacedBy(23.dp)) {
        gridItems(visits,key={it.repo.fullName}) {visit->val id=visit.repo
            Column(Modifier.clickable {model.openRepository(id)}) {
                BookCover(id,Modifier.fillMaxWidth().height(185.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(id.name,Modifier.weight(1f).padding(top=9.dp),fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    RecentProjectMenu(id,visit.pinned,{model.toggleRecentPin(id)}){model.removeRecentRepository(id)}
                }
                Text(id.owner,fontSize=9.sp,color=Muted,maxLines=1)
                Text((if(visit.pinned)"置顶 · "else"")+visitLabel(visit.visitedAt),Modifier.padding(top=4.dp),fontSize=9.sp,color=Muted)
            }
        }
    } else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=24.dp)) {
        items(visits,key={it.repo.fullName}){visit->
            val project=s.projects[visit.repo.fullName]
            Column(Modifier.fillMaxWidth().clickable{model.openRepository(visit.repo)}) {
                Row(Modifier.padding(vertical=18.dp),verticalAlignment=Alignment.Top) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(visit.repo.owner+if(project?.isPrivate==true)" · 私有"else"",fontSize=9.sp,color=Muted)
                        Text(visit.repo.name,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(!project?.description.isNullOrBlank())Text(project!!.description,fontSize=12.sp,lineHeight=20.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(listOfNotNull(if(visit.pinned)"置顶"else null,visitLabel(visit.visitedAt),project?.language?.takeIf{it.isNotBlank()},project?.let{"☆ ${formatStars(it.stars)}"}).joinToString("  ·  "),fontSize=9.sp,color=Muted)
                    }
                    RecentProjectMenu(visit.repo,visit.pinned,{model.toggleRecentPin(visit.repo)}){model.removeRecentRepository(visit.repo)}
                }
                HorizontalDivider(color=Rule)
            }
        }
    }
}

@Composable private fun RecentProjectMenu(repo:RepoId,pinned:Boolean,onPin:()->Unit,onRemove:()->Unit) {
    var expanded by remember(repo.fullName){mutableStateOf(false)}
    Box {
        IconButton(onClick={expanded=true},modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.MoreHoriz,"项目操作 ${repo.fullName}",Modifier.size(18.dp),tint=Muted)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            DropdownMenuItem(text={Text(if(pinned)"取消置顶"else"置顶项目",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.PushPin,null,Modifier.size(16.dp))},onClick={expanded=false;onPin()})
            DropdownMenuItem(text={Text("移除访问记录",fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Close,null,Modifier.size(16.dp))},onClick={expanded=false;onRemove()})
        }
    }
}

private fun visitLabel(timestamp:Long):String {
    if(timestamp==0L)return "上次阅读"
    val date=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val today=LocalDate.now()
    return when {
        System.currentTimeMillis()-timestamp in 0..59_999 -> "刚刚"
        date.toLocalDate()==today -> "今天 "+date.format(DateTimeFormatter.ofPattern("HH:mm"))
        date.toLocalDate()==today.minusDays(1) -> "昨天 "+date.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
    }
}

@Composable private fun ProjectRow(project:Project,onClick:()->Unit,index:Int?=null,starAction:(@Composable ()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Row(Modifier.padding(vertical=19.dp),horizontalArrangement=Arrangement.spacedBy(13.dp),verticalAlignment=Alignment.Top) {
            if(index!=null)Text("%02d".format(index),fontFamily=FontFamily.Serif,fontSize=16.sp,color=Muted)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(project.id.owner+(if(project.isPrivate)" · 私有"else""),fontSize=9.sp,color=Muted)
                Text(project.id.name,fontSize=20.sp,fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(project.description.isNotBlank())Text(project.description,fontSize=12.sp,lineHeight=21.sp,color=Muted,maxLines=3,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull(project.language.takeIf{it.isNotBlank()},"☆ ${formatStars(project.stars)}").joinToString("  ·  "),fontSize=10.sp,color=Muted)
            }
            if(starAction!=null)starAction() else Icon(Icons.Outlined.ChevronRight,null,Modifier.padding(top=14.dp).size(14.dp),tint=Muted)
        }
        HorizontalDivider(color=Rule)
    }
}
@Composable private fun RepositoryStar(project:Project,s:GroveState,model:GroveViewModel) {
    val key=project.id.fullName.lowercase()
    LaunchedEffect(key,s.username){model.ensureStarStatus(project.id)}
    val busy=key in s.starBusy;val starred=s.starStates[key]
    IconButton(onClick={model.toggleStar(project)},enabled=!busy,modifier=Modifier.size(48.dp)) {
        if(busy)CircularProgressIndicator(Modifier.size(16.dp),color=Muted,strokeWidth=1.dp)
        else Icon(if(starred==true)Icons.Outlined.Star else Icons.Outlined.StarBorder,
            if(s.username.isBlank())"登录后添加 Star" else if(starred==true)"取消 Star" else "添加 Star",Modifier.size(20.dp),tint=if(starred==true)Ink else Muted)
    }
}
private fun formatStars(stars:Int)=if(stars>=1000)"%.1fk".format(stars/1000f)else stars.toString()

@Composable private fun ColumnScope.DiscoveryPage(s:GroveState,model:GroveViewModel,listState:LazyListState,categoryScroll:ScrollState) {
    LoadOnScrollEnd(listState,s.discoverPage,s.moreDiscoveries&&!s.loading&&s.error==null){model.discover(more=true)}
    var languages by remember {mutableStateOf(false)}
    var search by rememberSaveable(s.username){mutableStateOf(s.discoveryQuery.isNotBlank())}
    val pullState=rememberPullToRefreshState()
    CompactHeader("发现") {
        IconButton(onClick={search=!search;if(!search)model.searchDiscoveries("")}){Icon(if(search)Icons.Outlined.Close else Icons.Outlined.Search,if(search)"关闭发现搜索"else"搜索发现",Modifier.size(19.dp))}
    }
    HorizontalDivider(color=Rule)
    if(search)InlineSearch(s.discoveryQuery,model::searchDiscoveries,"搜索 GitHub 项目","清除发现搜索")
    Row(Modifier.fillMaxWidth().horizontalScroll(categoryScroll).padding(horizontal=20.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Category.entries.forEach {category->FilterChip(selected=s.category==category,onClick={model.discoveryFilter(category=category)},label={Text(category.label,fontSize=10.sp)},shape=androidx.compose.foundation.shape.RoundedCornerShape(2.dp))}
    }
    HorizontalDivider(color=Rule)
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick={model.discoveryFilter(newProjects=false)}){Text("高星精选",fontSize=11.sp,fontWeight=if(!s.newProjects)FontWeight.Bold else FontWeight.Normal,color=if(!s.newProjects)Ink else Muted)}
        TextButton(onClick={model.discoveryFilter(newProjects=true)}){Text("新晋热门",fontSize=11.sp,fontWeight=if(s.newProjects)FontWeight.Bold else FontWeight.Normal,color=if(s.newProjects)Ink else Muted)}
        Spacer(Modifier.weight(1f))
        Box {TextButton(onClick={languages=true}){Text(s.language.ifBlank{"全部语言"},fontSize=10.sp);Icon(Icons.Outlined.ExpandMore,null,Modifier.size(13.dp))}
            DropdownMenu(expanded=languages,onDismissRequest={languages=false}) {listOf("","Kotlin","Java","TypeScript","Python","Rust","Go").forEach {language->DropdownMenuItem(text={Text(language.ifBlank{"全部语言"},fontSize=12.sp)},onClick={languages=false;model.discoveryFilter(language=language)})}}
        }
    }
    HorizontalDivider(color=Rule)
    Text((if(s.discoveryQuery.isNotBlank())"GitHub 搜索 · "else"")+(if(s.newProjects)"近 30 天创建 · 按累计 Star 排序" else "${s.category.label}领域 · 按累计 Star 排序"),Modifier.padding(horizontal=22.dp,vertical=11.dp),fontSize=9.sp,color=Muted)
    if(s.loading&&!s.discoverRefreshing)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp),color=Muted,trackColor=Rule)
    s.error?.let {ErrorNotice(it,model::retry)}
    PullToRefreshBox(isRefreshing=s.discoverRefreshing,onRefresh={if(!s.loading)model.refreshDiscoveries()},state=pullState,
        modifier=Modifier.weight(1f).fillMaxWidth(),indicator={
            Indicator(modifier=Modifier.align(Alignment.TopCenter),isRefreshing=s.discoverRefreshing,state=pullState,containerColor=Paper,color=Ink)
        }) {
        LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=22.dp)) {
            if(s.discoveries.isEmpty()&&!s.loading&&s.error==null)item {EmptyNotice("暂时没有找到项目。","换一个分类或语言试试。",Icons.Outlined.Explore)}
            items(s.discoveries,key={it.id.fullName}) {p->ProjectRow(p,onClick={model.openRepository(p.id)},index=s.discoveries.indexOf(p)+1,starAction={RepositoryStar(p,s,model)})}
            if(s.moreDiscoveries)item {PageLoading(s.loading)}
        }
    }
}

@Composable private fun ColumnScope.SavedPage(s:GroveState,model:GroveViewModel) {
    val starTab=!s.savedBookmarks
    val starQuery=s.starSearchQuery
    var bookmarkQuery by rememberSaveable(s.username){mutableStateOf("")}
    val query=if(starTab)starQuery else bookmarkQuery
    var search by rememberSaveable(s.username,starTab){mutableStateOf(query.isNotBlank())}
    val searching=starQuery.isNotBlank()
    val stars=if(searching)s.starSearchResults else s.stars
    val bookmarks=remember(s.bookmarks,bookmarkQuery){s.bookmarks.filter{(it.repo.fullName+" "+it.path+" "+it.ref).contains(bookmarkQuery.trim(),true)}}
    val listState=rememberLazyListState()
    LaunchedEffect(query,starTab){listState.scrollToItem(0)}
    val pullState=rememberPullToRefreshState()
    LoadOnScrollEnd(listState,s.starPage,starTab&&!searching&&s.username.isNotBlank()&&s.moreStars&&!s.loading&&!s.starSearchBusy&&s.error==null){model.loadStars(true)}
    CompactHeader("收藏") {
        IconButton(onClick={search=!search;if(!search){if(starTab)model.searchStars("")else bookmarkQuery=""}}){Icon(if(search)Icons.Outlined.Close else Icons.Outlined.Search,if(search)"关闭收藏搜索"else"搜索收藏",Modifier.size(19.dp))}
    }
    Row(Modifier.padding(horizontal=15.dp)) {TextButton(onClick={model.selectSavedTab(false)},modifier=Modifier.semantics{selected=starTab}){Text("仓库 Star",fontSize=12.sp,fontWeight=if(starTab)FontWeight.Bold else FontWeight.Normal)}
        TextButton(onClick={model.selectSavedTab(true)},modifier=Modifier.semantics{selected=!starTab}){Text("文档书签 ${s.bookmarks.size}",fontSize=12.sp,fontWeight=if(!starTab)FontWeight.Bold else FontWeight.Normal)}}
    HorizontalDivider(color=Rule)
    if(search)InlineSearch(query,{if(starTab)model.searchStars(it)else bookmarkQuery=it},if(starTab)"搜索仓库名、作者或简介"else"搜索文件名、路径或仓库","清除收藏搜索")
    if(starTab&&s.username.isNotBlank()) {
        if((s.loading||s.starSearchBusy)&&!s.savedRefreshing)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp),color=Muted)
        if(!searching)s.error?.let {ErrorNotice(it,model::retry)}
        if(searching)s.starSearchError?.let{ErrorNotice(it){model.searchStars(starQuery,refresh=true)}}
        val summary=when {
            searching&&s.starSearchBusy -> "正在搜索全部 Star · 已检查 ${s.starSearchScanned} 个"
            searching&&s.starSearchComplete -> "找到 ${stars.size} 个仓库 · 已搜索全部 ${s.starSearchScanned} 个 Star"
            searching -> "搜索尚未完成 · 已检查 ${s.starSearchScanned} 个 Star"
            else -> "${stars.size} 个仓库"
        }
        Text(summary,Modifier.padding(horizontal=22.dp,vertical=5.dp),fontSize=9.sp,color=Muted)
    }
    key(s.username,starTab) {
    PullToRefreshBox(isRefreshing=s.savedRefreshing,onRefresh=model::refreshSaved,state=pullState,
        modifier=Modifier.weight(1f).fillMaxWidth(),indicator={
            Indicator(modifier=Modifier.align(Alignment.TopCenter),isRefreshing=s.savedRefreshing,state=pullState,containerColor=Paper,color=Ink)
        }) {
    if(starTab) {
        if(s.username.isBlank()) {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
            EmptyNotice("你的 Star，也是一份书单。","登录 GitHub 后查看收藏的仓库。\n文档书签可以直接在本机使用。",Icons.Outlined.StarBorder)
            Button(onClick=model::login,enabled=!s.authBusy){Text(if(s.authBusy)"正在授权…"else"登录 GitHub")}}}
        else {
            LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=22.dp)) {
                if(stars.isEmpty()&&!s.loading&&!s.starSearchBusy&&s.error==null&&(!searching||s.starSearchComplete))item{EmptyNotice(if(!searching)"还没有 Star 的仓库。"else"没有匹配的仓库。",if(!searching)"去发现页寻找喜欢的项目。"else"试试其他仓库名、作者或简介关键词。",Icons.Outlined.StarBorder)}
                items(stars,key={it.id.fullName}){p->ProjectRow(p,onClick={model.openRepository(p.id)},starAction={RepositoryStar(p,s,model)})}
                if(!searching&&s.moreStars&&s.loading)item {PageLoading(true)}
            }
        }
    }else LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=22.dp)) {
        if(bookmarks.isEmpty())item {EmptyNotice(if(query.isBlank())"留一枚书签。"else"没有匹配的书签。",if(query.isBlank())"阅读时点击书签，就能把这一页留在这里。"else"试试文件名、路径或仓库名。",Icons.Outlined.BookmarkBorder)}
        items(bookmarks,key={it.key}){bookmark->Column(Modifier.fillMaxWidth().clickable {model.openBookmark(bookmark)}.padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.BookmarkBorder,null,Modifier.size(17.dp),tint=Muted);Spacer(Modifier.width(10.dp));Text(bookmark.path.substringAfterLast('/'),fontSize=13.sp,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis)}
            Text("${bookmark.repo.fullName} · ${bookmark.ref}",fontSize=10.sp,color=Muted)
            HorizontalDivider(Modifier.padding(top=10.dp),color=Rule)
        }}
    }
    }
    }
}

@Composable private fun InlineSearch(query:String,onQuery:(String)->Unit,placeholder:String,clearLabel:String) {
    val focus=remember{FocusRequester()}
    val focusManager=LocalFocusManager.current
    LaunchedEffect(Unit){focus.requestFocus()}
    OutlinedTextField(value=query,onValueChange=onQuery,
        modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp).focusRequester(focus),singleLine=true,
        placeholder={Text(placeholder,fontSize=12.sp)},leadingIcon={Icon(Icons.Outlined.Search,null,Modifier.size(18.dp))},
        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus()}),
        trailingIcon={if(query.isNotEmpty())IconButton(onClick={onQuery("")}){Icon(Icons.Outlined.Close,clearLabel,Modifier.size(17.dp))}})
}

@Composable private fun ColumnScope.ProfilePage(s:GroveState,model:GroveViewModel,settings:()->Unit,about:()->Unit,projects:()->Unit,addedRepos:()->Unit) {
    val context=LocalContext.current
    var more by remember {mutableStateOf(false)}
    var developerOptions by remember {mutableStateOf(false)}
    var cacheManagement by remember {mutableStateOf(false)}
    CompactHeader("我的") {
        IconButton(onClick={more=true}){Icon(Icons.Outlined.MoreHoriz,"更多功能",Modifier.size(22.dp))}
    }
    if(more)ModalBottomSheet(onDismissRequest={more=false},containerColor=Paper,tonalElevation=0.dp) {
        CompactHeader("更多功能") {IconButton(onClick={more=false}){Icon(Icons.Outlined.Close,"关闭更多功能",Modifier.size(19.dp))}}
        HorizontalDivider(color=Rule)
        Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()).padding(horizontal=24.dp)) {
            MoreAction(Icons.Outlined.TextFields,"阅读偏好"){more=false;settings()}
            MoreAction(Icons.Outlined.FolderOpen,"已添加的仓库"){more=false;addedRepos()}
            MoreAction(Icons.Outlined.Storage,"缓存管理"){more=false;cacheManagement=true;model.refreshCacheSize()}
            if(s.username.isNotBlank()&&BuildConfig.GITHUB_APP_SLUG.isNotBlank())MoreAction(Icons.Outlined.Lock,"仓库授权"){
                more=false;contextOpen(context,"https://github.com/apps/${BuildConfig.GITHUB_APP_SLUG}/installations/new")
            }
            MoreAction(Icons.Outlined.Info,"关于 Grove"){more=false;about()}
            MoreAction(Icons.Outlined.Code,"开发者选项"){more=false;developerOptions=true}
            if(s.username.isNotBlank())MoreAction(Icons.AutoMirrored.Outlined.Logout,"退出登录"){more=false;model.logout()}
            Spacer(Modifier.height(18.dp))
        }
    }
    if(developerOptions)ModalBottomSheet(onDismissRequest={developerOptions=false},containerColor=Paper,tonalElevation=0.dp) {
        CompactHeader("开发者选项") {IconButton(onClick={developerOptions=false}){Icon(Icons.Outlined.Close,"关闭开发者选项",Modifier.size(19.dp))}}
        HorizontalDivider(color=Rule)
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp)) {
            Text("仅在排查预览显示问题时开启。",Modifier.padding(top=18.dp),fontSize=11.sp,color=Muted)
            Row(Modifier.fillMaxWidth().toggleable(value=s.previewDiagnosticsEnabled,role=Role.Switch,onValueChange=model::setPreviewDiagnosticsEnabled).padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end=16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("预览诊断",fontSize=13.sp,color=Ink)
                    Text("在图片和表格预览页显示诊断入口",fontSize=10.sp,color=Muted)
                }
                Switch(checked=s.previewDiagnosticsEnabled,onCheckedChange=null,colors=SwitchDefaults.colors(
                    checkedThumbColor=Paper,checkedTrackColor=Ink,checkedBorderColor=Ink,
                    uncheckedThumbColor=Muted,uncheckedTrackColor=Paper,uncheckedBorderColor=Rule))
            }
            HorizontalDivider(color=Rule)
            Spacer(Modifier.height(24.dp))
        }
    }
    if(cacheManagement)ModalBottomSheet(onDismissRequest={cacheManagement=false},containerColor=Paper,tonalElevation=0.dp) {
        CompactHeader("缓存管理") {IconButton(onClick={cacheManagement=false}){Icon(Icons.Outlined.Close,"关闭缓存管理",Modifier.size(19.dp))}}
        HorizontalDivider(color=Rule)
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("缓存大小",Modifier.weight(1f),fontSize=13.sp)
                if(s.cacheBusy)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=1.dp,color=Muted)
                else Text(s.cacheBytes?.let(::formatCacheSize)?:"—",fontSize=20.sp,fontFamily=FontFamily.Serif)
            }
            Text("清理临时文档和附件。登录状态、书签、足迹及阅读进度会保留；再次打开内容时会重新下载。",fontSize=11.sp,lineHeight=21.sp,color=Muted)
            s.cacheMessage?.let{Text(it,fontSize=11.sp,color=Muted)}
            Button(onClick=model::clearCache,enabled=!s.cacheBusy&&!s.authBusy&&(s.cacheBytes?:0)>0,modifier=Modifier.fillMaxWidth()){Text("一键清理")}
        }
    }
    HorizontalDivider(color=Rule)
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=23.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).border(1.dp,Rule,androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {
                Icon(Icons.Outlined.PersonOutline,null,Modifier.size(25.dp),tint=Muted)
            }
            Column(Modifier.weight(1f).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(s.username.ifBlank{"访客"},fontSize=23.sp,fontFamily=FontFamily.Serif,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(s.username.isBlank())"公开仓库可直接阅读"else"已连接 GitHub",fontSize=10.sp,color=Muted)
            }
            if(s.username.isBlank())TextButton(onClick=model::login,enabled=!s.authBusy){Text(if(s.authBusy)"授权中…"else"登录 GitHub",fontSize=11.sp)}
        }
        HorizontalDivider(color=Rule)
        PreferenceRow(Icons.Outlined.FolderOpen,"我的项目",if(s.username.isBlank())"登录后查看你的公开与私有仓库"else"查看我的公开与私有仓库",projects)
        PreferenceRow(Icons.Outlined.StarBorder,"GitHub Star","查看和管理已 Star 的仓库"){model.openSaved(false)}
        PreferenceRow(Icons.Outlined.BookmarkBorder,"文档书签","${s.bookmarks.size} 枚书签，随时回到保存的页面"){model.openSaved(true)}
        Text("继续阅读",Modifier.padding(top=26.dp,bottom=13.dp),fontSize=11.sp,color=Muted)
        val resume=s.resume
        if(resume==null) {
            Text("打开一份文档后，在这里接着读。",Modifier.padding(bottom=26.dp),fontSize=12.sp,lineHeight=21.sp,color=Muted)
        }else {
            Row(Modifier.fillMaxWidth().clickable(onClick=model::resumeReading).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                BookCover(resume.repo,Modifier.size(50.dp,73.dp),small=true)
                Column(Modifier.weight(1f).padding(horizontal=15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(resume.path.substringAfterLast('/'),fontFamily=FontFamily.Monospace,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(resume.repo.fullName,fontSize=10.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(progress={resume.progress},modifier=Modifier.weight(1f).height(2.dp),color=Muted,trackColor=Rule)
                        Text("${(resume.progress*100).toInt()}%",fontSize=9.sp,color=Muted)
                    }
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,"继续阅读文档",Modifier.size(18.dp),tint=Muted)
            }
        }
        Text("阅读位置和文档书签保存在本机",Modifier.padding(top=26.dp,bottom=24.dp),fontSize=9.sp,color=Muted)
    }
}
@Composable private fun MoreAction(icon:ImageVector,title:String,action:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(vertical=19.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(19.dp),tint=Muted)
        Text(title,Modifier.weight(1f).padding(horizontal=16.dp),fontSize=13.sp,color=Ink)
        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(14.dp),tint=Muted)
    }
    HorizontalDivider(color=Rule)
}
@Composable internal fun FileDownloadNotice(progress:FileDownloadProgress,running:Boolean=true) {
    val fraction=progress.fraction
    Column(Modifier.fillMaxWidth().testTag("file-download").padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(fraction!=null)LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(3.dp),color=Ink,trackColor=Rule)
        else if(running)LinearProgressIndicator(modifier=Modifier.fillMaxWidth().height(3.dp),color=Muted,trackColor=Rule)
        else LinearProgressIndicator(progress={0f},modifier=Modifier.fillMaxWidth().height(3.dp),color=Muted,trackColor=Rule)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(fraction!=null)"${formatCacheSize(progress.downloadedBytes)} / ${formatCacheSize(progress.totalBytes!!)}"
                else "已下载 ${formatCacheSize(progress.downloadedBytes)}",fontSize=10.sp,color=Muted,modifier=Modifier.weight(1f))
            if(fraction!=null)Text("${(fraction*100).toInt()}%",fontSize=10.sp,color=Muted)
            else if(!running)Text("等待重试",fontSize=10.sp,color=Muted)
        }
    }
}

private fun formatCacheSize(bytes:Long):String=when {
    bytes<1024->"$bytes B"
    bytes<1024*1024->"%.1f KB".format(java.util.Locale.ROOT,bytes/1024.0)
    bytes<1024L*1024*1024->"%.1f MB".format(java.util.Locale.ROOT,bytes/(1024.0*1024))
    else->"%.1f GB".format(java.util.Locale.ROOT,bytes/(1024.0*1024*1024))
}
@Composable private fun PreferenceRow(icon:ImageVector,title:String,description:String,click:()->Unit) {
    Column {Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=20.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(19.dp),tint=Muted);Column(Modifier.weight(1f).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(title,fontSize=13.sp);Text(description,fontSize=10.sp,color=Muted)}
        Icon(Icons.Outlined.ChevronRight,null,Modifier.size(14.dp),tint=Muted)
    };HorizontalDivider(color=Rule)}
}

@Composable private fun ColumnScope.ReaderPage(s:GroveState,model:GroveViewModel,settings:()->Unit,toc:()->Unit,files:()->Unit,onLink:(String)->Unit,jump:String?) {
    s.openingDocument?.let{target->
        val kind=when(target.path.substringAfterLast('.').lowercase()) {
            "pdf"->PreviewKind.PDF;"epub"->PreviewKind.EPUB;else->null
        }
        if(kind==null) {
            if(s.loading)LinearProgressIndicator(Modifier.fillMaxWidth().height(1.dp).testTag("document-loading"),color=Muted,trackColor=Rule)
            s.error?.let{ErrorNotice(it,model::retry)}
            Spacer(Modifier.weight(1f))
            return
        }
        Column(Modifier.weight(1f).fillMaxWidth().testTag("file-loading-region")) {
            Text(target.path.substringAfterLast('/'),Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),
                fontFamily=FontFamily.Monospace,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            HorizontalDivider(Modifier.padding(horizontal=20.dp),color=Rule)
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                Column(Modifier.widthIn(max=360.dp).fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(if(s.loading)"文件加载中"else"文件加载失败",fontFamily=FontFamily.Serif,fontSize=18.sp,color=Ink)
                    Spacer(Modifier.height(12.dp))
                    FileDownloadNotice(s.downloadProgress?:FileDownloadProgress(0,null,kind),running=s.loading)
                    s.error?.let{ErrorNotice(it,model::retry)}
                    Text(if(s.loading)"可切换页面，下载会继续"else"重试时会优先接着已下载的部分继续",Modifier.padding(horizontal=22.dp,vertical=8.dp),fontSize=10.sp,color=Muted)
                    TextButton(onClick=model::cancelFileDownload){Text("取消加载",fontSize=11.sp,color=Muted)}
                }
            }
        }
        return
    }
    val doc=s.document
    if(doc==null) {if(!s.loading)EmptyNotice("打开一页，开始阅读。","可以先浏览文件，或重试加载 README。");return}
    val context=LocalContext.current
    val previewDiagnostics=remember(doc.key){PreviewDiagnostics(doc)}
    var showPreviewDiagnostics by remember(doc.key){mutableStateOf(false)}
    var epubContents by remember(doc.key){mutableStateOf(false)}
    if(showPreviewDiagnostics&&s.previewDiagnosticsEnabled)AlertDialog(onDismissRequest={showPreviewDiagnostics=false},containerColor=Paper,
        title={Text("预览诊断",fontFamily=FontFamily.Serif)},
        text={SelectionContainer{Text(previewDiagnostics.report(),Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),fontSize=11.sp,lineHeight=20.sp,fontFamily=FontFamily.Monospace)}},
        confirmButton={TextButton(onClick={
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("预览诊断",previewDiagnostics.report()))
            Toast.makeText(context,"诊断信息已复制",Toast.LENGTH_SHORT).show()
        }){Text("复制诊断")}},dismissButton={Row {
            TextButton(onClick={previewDiagnostics.softwareRendering=!previewDiagnostics.softwareRendering;showPreviewDiagnostics=false}){Text(if(previewDiagnostics.softwareRendering)"默认显示"else"兼容显示")}
            TextButton(onClick={showPreviewDiagnostics=false}){Text("关闭")}
        }})
    val sourceVertical=rememberSaveable(doc.key,saver=ScrollState.Saver){ScrollState(0)}
    val sourceHorizontal=rememberSaveable(doc.key,saver=ScrollState.Saver){ScrollState(0)}
    var pdfZoomPercent by rememberSaveable(doc.key,doc.attachment?.localPath){mutableIntStateOf(100)}
    ReaderDocumentHeader(doc,s.raw,s.immersive,s.bookmarks.any{it.key==doc.key},model::toggleRaw,model::toggleImmersive,model::toggleBookmark,
        pdfZoomPercent,{pdfZoomPercent=it})
    HorizontalDivider(Modifier.padding(horizontal=20.dp),color=Rule)
    if(doc.attachment?.kind==PreviewKind.PDF)PdfPreview(java.io.File(doc.attachment.localPath),Modifier.weight(1f).fillMaxWidth(),zoomPercent=pdfZoomPercent)
    else if(doc.attachment?.kind==PreviewKind.EPUB)key(doc.key,doc.attachment.localPath){EpubPreview(doc,s.fontSize,s.warm,s.progress,{model.progress(doc.key,it)},onLink,epubContents,{epubContents=false},Modifier.weight(1f).fillMaxWidth()) {navigation->
        EpubReadingToolbar(navigation,s.progress,files,{epubContents=true},settings)
    }}
    else if(s.raw&&doc.sourceTooLarge)Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
        Text("SVG 源码超过 1 MB，请返回预览，长按保存原文件后查看。",fontSize=12.sp,color=Muted,lineHeight=22.sp)
    }
    else if(s.raw||(!doc.markdown&&doc.attachment==null))Box(Modifier.weight(1f).fillMaxWidth()) {
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(sourceVertical).horizontalScroll(sourceHorizontal).padding(20.dp)) {
            Text(doc.content,fontSize=12.sp,lineHeight=21.sp,fontFamily=FontFamily.Monospace)
        }
        if(doc.attachment==null)ReaderBackToTop(sourceVertical)
    } else DocumentReader(doc,s.fontSize,s.warm,s.progress,onLink,model::progress,model::setHeadings,jump,Modifier.weight(1f).fillMaxWidth(),onImage=model::previewImage,imageResource=model::imageResource,onImageLongPress=model::showImageActions,diagnostics=previewDiagnostics.takeIf{s.previewDiagnosticsEnabled})
    if(doc.attachment?.kind!=PreviewKind.EPUB) {
    HorizontalDivider(color=Rule)
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=files){Icon(Icons.Outlined.AccountTree,null,Modifier.size(14.dp));Text(" 文件",fontSize=10.sp)}
        TextButton(onClick=toc,enabled=!s.raw&&doc.markdown){Icon(Icons.AutoMirrored.Outlined.List,null,Modifier.size(14.dp));Text(" 目录",fontSize=10.sp)}
        Spacer(Modifier.weight(1f));Text("${(s.progress*100).toInt()}%",fontSize=9.sp,color=Muted)
        if(doc.svg&&s.raw) {
            IconButton(onClick={
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(doc.path,doc.content))
                Toast.makeText(context,"SVG 源码已复制",Toast.LENGTH_SHORT).show()
            },enabled=!doc.sourceTooLarge){Icon(Icons.Outlined.ContentCopy,"复制 SVG 源码",Modifier.size(18.dp))}
            Text("SVG 源码",fontSize=9.sp,color=Muted)
        }else if(doc.attachment==null)IconButton(onClick=settings){Icon(Icons.Outlined.TextFields,"阅读设置",Modifier.size(18.dp))}
        else {
            Text(if(doc.attachment.kind==PreviewKind.SPREADSHEET)"只读表格"else if(doc.attachment.kind==PreviewKind.IMAGE)"双指缩放"else"PDF",fontSize=9.sp,color=Muted)
            if(s.previewDiagnosticsEnabled&&doc.attachment.kind!=PreviewKind.PDF)IconButton(onClick={showPreviewDiagnostics=true},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Info,"预览诊断",Modifier.size(18.dp),tint=Muted)}
        }
    }
    }
}

@Composable internal fun ReaderDocumentHeader(doc:Document,raw:Boolean,immersive:Boolean,bookmarked:Boolean,
    onToggleRaw:()->Unit,onToggleImmersive:()->Unit,onToggleBookmark:()->Unit,pdfZoomPercent:Int=100,onPdfZoom:(Int)->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(start=if(immersive)4.dp else 20.dp,end=7.dp).testTag("reader-document-header"),verticalAlignment=Alignment.CenterVertically) {
        if(immersive)IconButton(onClick=onToggleImmersive){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"退出专注阅读",Modifier.size(18.dp))}
        Text(doc.path.substringAfterLast('/'),Modifier.weight(1f),fontFamily=FontFamily.Monospace,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(doc.svg) {
            TextButton(onClick={if(raw)onToggleRaw()},modifier=Modifier.semantics{selected=!raw},contentPadding=PaddingValues(horizontal=8.dp)){
                Text("预览",fontSize=10.sp,color=if(!raw)Ink else Muted,fontWeight=if(!raw)FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick={if(!raw)onToggleRaw()},modifier=Modifier.semantics{selected=raw},contentPadding=PaddingValues(horizontal=8.dp)){
                Text("源码",fontSize=10.sp,color=if(raw)Ink else Muted,fontWeight=if(raw)FontWeight.Bold else FontWeight.Normal)
            }
        }else if(doc.markdown)TextButton(onClick=onToggleRaw){Text(if(raw)"阅读" else "源码",fontSize=10.sp)}
        if(doc.attachment?.kind==PreviewKind.PDF)PdfZoomControls(pdfZoomPercent,onPdfZoom)
        if(!immersive)IconButton(onClick=onToggleImmersive,modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Fullscreen,"专注阅读",Modifier.size(18.dp))}
        IconButton(onClick=onToggleBookmark,enabled=doc.originUrl==null,modifier=Modifier.size(36.dp)){Icon(if(bookmarked)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,"收藏文档",Modifier.size(18.dp))}
    }
}

@Composable private fun EpubReadingToolbar(navigation:EpubNavigation,progress:Float,files:()->Unit,contents:()->Unit,settings:()->Unit) {
    HorizontalDivider(color=Rule)
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp).testTag("epub-reading-toolbar"),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=files) {
            Icon(Icons.Outlined.AccountTree,null,Modifier.size(14.dp));Text(" 文件",fontSize=10.sp)
        }
        TextButton(onClick=contents,enabled=navigation.total>0,modifier=Modifier.heightIn(min=48.dp).semantics{
            contentDescription="章节目录，第 ${navigation.position} 章，共 ${navigation.total} 章"
        }) {
            Icon(Icons.AutoMirrored.Outlined.List,null,Modifier.size(14.dp));Text(" 目录",fontSize=10.sp)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            if(navigation.total>0) {
                Text("${navigation.position}/${navigation.total}章",fontSize=9.sp,color=Muted)
                Text("·",fontSize=9.sp,color=Muted)
            }
            Text("全书约${(progress*100).toInt()}%",fontSize=9.sp,color=Muted)
        }
        IconButton(onClick=settings,modifier=Modifier.size(48.dp)){Icon(Icons.Outlined.TextFields,"阅读设置",Modifier.size(18.dp))}
    }
}

@Composable private fun ColumnScope.FilesPage(s:GroveState,model:GroveViewModel) {
    var query by remember(s.directory){mutableStateOf("")}
    var search by remember(s.directory){mutableStateOf(false)}
    val focusManager=LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
        if(s.directory.isNotBlank())IconButton(onClick={model.loadDirectory(s.directory.substringBeforeLast('/',""))}){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"上一级",Modifier.size(16.dp))}
        Text(s.directory.ifBlank{"/"},Modifier.weight(1f).padding(vertical=12.dp),fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        IconButton(onClick={search=!search;if(!search){query="";focusManager.clearFocus()}}) {
            Icon(Icons.Outlined.Search,if(search)"收起文件搜索"else"搜索文件",Modifier.size(19.dp),tint=if(search)Ink else Muted)
        }
    }
    if(search)InlineSearch(query,{query=it},"筛选当前目录","清除文件搜索")
    LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=20.dp,vertical=9.dp)) {
        items(s.files.filter{it.name.contains(query,true)},key={it.path}){f->RepositoryFileRow(f){if(f.folder)model.loadDirectory(f.path)else model.openFile(f.path)}}
        if(s.files.size>=1000)item{Text("此目录可能超过 GitHub Contents API 的显示上限。",Modifier.padding(15.dp),fontSize=11.sp,color=Muted)}
        if(s.files.isEmpty()&&!s.loading&&s.error==null)item{EmptyNotice("这个目录是空的。","可以返回上一级继续浏览。",Icons.Outlined.Folder)}
    }
}

@Composable private fun RepositoryFileRow(file:RepoFile,current:Boolean=false,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().semantics{selected=current}.background(if(current)Color(0xFFEFEAE0)else Color.Transparent)
        .clickable(onClick=onClick).padding(horizontal=if(current)8.dp else 0.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(if(file.folder)Icons.Outlined.Folder else if(file.name.endsWith(".pdf",true))Icons.Outlined.PictureAsPdf else if(file.name.substringAfterLast('.').lowercase() in listOf("png","jpg","jpeg","svg","gif","webp"))Icons.Outlined.Image else if(file.name.substringAfterLast('.').lowercase() in listOf("xlsx","xls","csv","tsv"))Icons.Outlined.TableChart else if(file.markdown)Icons.AutoMirrored.Outlined.MenuBook else Icons.Outlined.Description,null,Modifier.size(18.dp),tint=Muted)
        Text(file.name,Modifier.weight(1f).padding(horizontal=12.dp),fontSize=12.sp,fontWeight=if(current)FontWeight.Medium else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(current)Text("正在阅读",fontSize=9.sp,color=Muted)
        else if(!file.folder)Text(if(file.size>1024)"${file.size/1024} KB"else"${file.size} B",fontSize=9.sp,color=Muted)
        else Icon(Icons.Outlined.ChevronRight,null,Modifier.size(14.dp),tint=Muted)
    }
}

@Composable private fun ColumnScope.IssuesPage(s:GroveState,model:GroveViewModel,onLink:(String)->Unit) {
    val issue=s.issue
    if(issue!=null) {
        Row(Modifier.fillMaxWidth().padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick=model::back){Icon(Icons.Outlined.ChevronLeft,null,Modifier.size(14.dp));Text("Issues",fontSize=10.sp)};Spacer(Modifier.weight(1f));Text("#${issue.number} · ${issue.state}",fontSize=10.sp,color=Muted)}
        val body=remember(issue,s.comments) {buildString {
            append("# ${issue.title}\n\n${issue.author} · ${issue.updated}\n\n${issue.body}\n\n---\n\n## 讨论 (${issue.comments})\n\n")
            s.comments.forEach {append("### ${it.author}\n\n${it.created}\n\n${it.body}\n\n---\n\n")}
        }}
        val project=s.project ?: return
        DocumentReader(Document(project.id,s.ref,"issue-${issue.number}.md",body),s.fontSize,s.warm,s.issueProgress,onLink,model::issueReadingProgress,{_,_->},null,Modifier.weight(1f).fillMaxWidth(),onImage=model::previewImage,imageResource=model::imageResource,onImageLongPress=model::showImageActions,
            endPage=if(s.moreComments&&!s.loading&&s.error==null)s.commentPage else null,
            onEnd={model.loadComments(true)})
        if(s.moreComments)PageLoading(s.loading)
        return
    }
    val listState=key(s.project?.id,s.closed){rememberLazyListState()}
    LoadOnScrollEnd(listState,s.issuePage,s.moreIssues&&!s.loading&&s.error==null){model.loadIssues(more=true)}
    Row(Modifier.padding(horizontal=16.dp)) {TextButton(onClick={model.loadIssues(false)}){Text("Open",fontSize=12.sp,fontWeight=if(!s.closed)FontWeight.Bold else FontWeight.Normal)};TextButton(onClick={model.loadIssues(true)}){Text("Closed",fontSize=12.sp,fontWeight=if(s.closed)FontWeight.Bold else FontWeight.Normal)};Spacer(Modifier.weight(1f));Text("只读",Modifier.align(Alignment.CenterVertically),fontSize=9.sp,color=Muted)}
    LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(horizontal=22.dp)) {
        if(s.issues.isEmpty()&&!s.moreIssues&&!s.loading&&s.error==null)item {EmptyNotice("还没有讨论。","这里展示仓库的 Issues 与评论。",Icons.Outlined.Adjust)}
        items(s.issues,key={it.number}){item->Column(Modifier.fillMaxWidth().clickable {model.openIssue(item)}.padding(vertical=19.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(item.title,fontSize=14.sp,lineHeight=23.sp)
            Text("#${item.number} · ${item.author} · ${item.updated} · ${item.comments} 条评论",fontSize=10.sp,color=Muted)
            if(item.labels.isNotEmpty())Text(item.labels.take(3).joinToString("  /  "),fontSize=9.sp,color=Muted)
            HorizontalDivider(Modifier.padding(top=8.dp),color=Rule)
        }}
        if(s.moreIssues)item{PageLoading(s.loading)}
    }
}

@Composable internal fun LoadOnScrollEnd(state:LazyListState,page:Int,enabled:Boolean,onLoad:()->Unit) {
    val load by rememberUpdatedState(onLoad)
    LaunchedEffect(state,page,enabled) {
        if(enabled)snapshotFlow {
            val layout=state.layoutInfo
            layout.totalItemsCount>0&&(layout.visibleItemsInfo.lastOrNull()?.index?:-1)>=layout.totalItemsCount-3
        }.collect {nearEnd->if(nearEnd)load()}
    }
}
@Composable private fun PageLoading(loading:Boolean) {
    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        if(loading){CircularProgressIndicator(Modifier.size(13.dp),color=Muted,strokeWidth=1.dp);Spacer(Modifier.width(9.dp))}
        Text(if(loading)"正在加载…"else"上拉继续加载",fontSize=10.sp,color=Muted)
    }
}
@Composable private fun AddRepositoryDialog(dismiss:()->Unit,add:(String)->Boolean,importRepos:()->Unit) {
    var input by remember {mutableStateOf("")};var invalid by remember {mutableStateOf(false)}
    AlertDialog(onDismissRequest=dismiss,title={Text("添加仓库",fontFamily=FontFamily.Serif)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("输入仓库名称或 GitHub 链接。",fontSize=12.sp,color=Muted)
        TextButton(onClick=importRepos){Text("从我的 GitHub 仓库导入",fontSize=12.sp)}
        OutlinedTextField(input,{input=it;invalid=false},singleLine=true,placeholder={Text("owner/repository",fontSize=12.sp)},isError=invalid)
        if(invalid)Text("请输入有效的 owner/repository 或仓库主页链接。",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={if(add(input))dismiss()else invalid=true}){Text("添加")}},dismissButton={TextButton(onClick=dismiss){Text("取消")}})
}

private fun contextOpen(context:android.content.Context,url:String) {
    try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:ActivityNotFoundException){}
}
