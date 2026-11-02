package dev.grove.reader

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.grove.reader.data.*
import dev.grove.reader.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.net.URI

data class FilePickerState(val directory:String,val files:List<RepoFile> = emptyList(),val loading:Boolean=true,val error:String?=null)

data class GroveState(
    val destination: Destination = Destination.LIBRARY,
    val library: List<RepoId> = emptyList(), val projects: Map<String,Project> = emptyMap(),
    val recentVisits:List<RepositoryVisit> = emptyList(), val recentSort:RecentSort=RecentSort.VISITED,val libraryGrid:Boolean=false,
    val savedBookmarks:Boolean=false, val savedRefreshing:Boolean=false,
    val starSearchQuery:String="",val starSearchResults:List<Project> = emptyList(),
    val starSearchBusy:Boolean=false,val starSearchComplete:Boolean=false,val starSearchScanned:Int=0,val starSearchError:String?=null,
    val bookmarks: List<Bookmark> = emptyList(), val resume: Bookmark? = null,
    val project: Project? = null, val ref: String = "main", val tab: RepoTab = RepoTab.READ,
    val document: Document? = null, val raw: Boolean = false, val immersive: Boolean = false,
    val progress: Float = 0f, val headings: List<Heading> = emptyList(),
    val directory: String = "", val files: List<RepoFile> = emptyList(), val branches: List<String> = emptyList(),
    val filePicker:FilePickerState?=null,
    val issues: List<Issue> = emptyList(), val issue: Issue? = null, val closed: Boolean = false,
    val issuePage: Int = 1, val moreIssues: Boolean = false,
    val comments: List<IssueComment> = emptyList(), val commentPage: Int = 1, val moreComments: Boolean = false,
    val issueProgress:Float=0f,
    val category: Category = Category.ALL, val language: String = "", val newProjects: Boolean = false,
    val discoveryQuery:String="",
    val discoveries: List<Project> = emptyList(), val discoverPage: Int = 1, val moreDiscoveries: Boolean = false,
    val discoverRefreshing: Boolean = false, val discoveryRevision: Int = 0,
    val imageToSave: ImageRequest? = null, val imageSaving: Boolean = false,
    val imageSavePrompt: ImageSavePrompt? = null, val imageSaveMessage: String? = null,
    val username: String = "", val userRepos:List<Project> = emptyList(), val userRepoPage:Int=1,val moreUserRepos:Boolean=false,val stars: List<Project> = emptyList(), val starPage: Int = 1, val moreStars: Boolean = false,
    val authDevice: DeviceAuthorization? = null, val authBusy: Boolean = false, val authError: String? = null,
    val starStates: Map<String,Boolean> = emptyMap(), val starBusy: Set<String> = emptySet(), val starError: String? = null,
    val fontSize: Int = 17, val warm: Boolean = false, val loading: Boolean = false, val error: String? = null,
    val downloadProgress:FileDownloadProgress?=null,
    val openingDocument:DocumentTarget?=null,
    val previewDiagnosticsEnabled:Boolean=false,
    val cacheBytes:Long?=null,val cacheBusy:Boolean=false,val cacheMessage:String?=null,
)

class GroveViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = TokenVault(application)
    private var store = ReadingStore(application,vault.accountId())
    private val auth = GitHubAuth(BuildConfig.GITHUB_CLIENT_ID,BuildConfig.GITHUB_APP_SLUG.isNotBlank())
    private val refreshLock=Any()
    private val api = GitHubClient(File(application.cacheDir,"github"),::validToken,vault::accountId)
    private fun validToken():String = synchronized(refreshLock) {
        val revision=vault.revision
        val session=vault.session();val now=System.currentTimeMillis()/1000
        if(session.accessToken.isBlank()||session.expiresAt==0L||now<session.expiresAt-60)return@synchronized session.accessToken
        if(session.refreshToken.isBlank()||(session.refreshExpiresAt>0&&now>=session.refreshExpiresAt))throw GitHubException("GitHub 登录已失效，请重新授权。",401)
        val fresh=auth.refreshBlocking(session)
        if(!vault.updateIfRevision(revision,fresh))throw CancellationException("登录状态已改变")
        fresh.accessToken
    }
    private val mutable = MutableStateFlow(GroveState(library=store.library(),recentVisits=store.recentVisits(),recentSort=store.recentSort,libraryGrid=store.libraryGrid,bookmarks=store.bookmarks(),resume=store.resume(),fontSize=store.fontSize,warm=store.warm,previewDiagnosticsEnabled=store.previewDiagnosticsEnabled))
    val state = mutable.asStateFlow()
    private var libraryJob:Job?=null
    private var requestJob: Job? = null
    private var filePickerJob:Job?=null
    private var authJob: Job? = null
    private var imageJob:Job?=null
    private var pendingImage:ImageDownload?=null
    private val imageStore=ImageStore(application.contentResolver)
    private val contentCache=ContentCache(application.cacheDir)
    private var cacheJob:Job?=null
    private val starJobs = mutableMapOf<String,Job>()
    private var starRevision = 0L
    private var starSearchJob:Job?=null
    private val starIndex=linkedMapOf<String,Project>()
    private var starIndexPage=1
    private var starIndexComplete=false
    private var lastSaved = 0L
    private var lastRequest: (suspend () -> Unit)? = null
    private val readingHistory = ArrayDeque<Document>()
    private data class TabSession(val screen:GroveState,val history:List<Document>,val retry:(suspend()->Unit)?,val pending:Boolean)
    private val tabSessions=mutableMapOf<Destination,TabSession>()
    private class DocumentLoad(val target:DocumentTarget,val account:String,
        var progress:FileDownloadProgress?=null,var busy:Boolean=true,var document:Document?=null,var error:String?=null,var job:Job?=null)
    private val documentLoads=mutableMapOf<String,DocumentLoad>()
    private fun cancelDocumentLoads(){documentLoads.values.forEach{it.job?.cancel()};documentLoads.clear()}
    init {
        mutable.update{it.copy(username=vault.login())}
        refreshLibrary()
    }
    private fun refreshLibrary() {
        libraryJob?.cancel();val account=vault.accountId();val repos=(mutable.value.recentVisits.map{it.repo}+mutable.value.library).distinctBy{it.fullName.lowercase()}
        libraryJob=viewModelScope.launch {
            for(id in repos) {
                try {val project=api.project(id);if(vault.accountId()==account)mutable.update{it.copy(projects=it.projects+(id.fullName to project))}}
                catch(e:CancellationException){throw e}catch(_:Exception){}
            }
        }
    }
    private fun switchAccount(profile:UserProfile?) {
        cancelDocumentLoads()
        dismissFilePicker()
        resetStarIndex()
        imageJob?.cancel();pendingImage=null
        starJobs.values.forEach { it.cancel() };starJobs.clear();starRevision++
        requestJob?.cancel();libraryJob?.cancel();readingHistory.clear();lastRequest=null;tabSessions.clear()
        store=ReadingStore(getApplication(),profile?.id?:"anonymous")
        mutable.value=GroveState(username=profile?.login.orEmpty(),library=store.library(),recentVisits=store.recentVisits(),recentSort=store.recentSort,libraryGrid=store.libraryGrid,bookmarks=store.bookmarks(),resume=store.resume(),fontSize=store.fontSize,warm=store.warm,previewDiagnosticsEnabled=store.previewDiagnosticsEnabled,destination=Destination.PROFILE)
        refreshLibrary()
    }
    private fun request(block: suspend () -> Unit) {
        lastRequest = block
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            mutable.update { it.copy(loading=true,downloadProgress=null,error=null,discoverRefreshing=false,savedRefreshing=false) }
            try { block() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(e is GitHubException&&e.status==401){api.clearAccountFiles();vault.clear();switchAccount(null)}
                mutable.update { it.copy(error=e.message ?: "内容加载失败，请重试。") }
            }
            finally { if(currentCoroutineContext().isActive) mutable.update { it.copy(loading=false,downloadProgress=null,discoverRefreshing=false,savedRefreshing=false) } }
        }
    }
    fun retry() { mutable.value.openingDocument?.let{startDocument(it,retry=true)}?:lastRequest?.let { request(it) } }
    fun removeRepository(id: RepoId) {
        val library=mutable.value.library.filterNot { it.fullName.equals(id.fullName,true) }
        store.saveLibrary(library);mutable.update { it.copy(library=library) }
    }
    private fun rememberVisit(id:RepoId) {
        val visits=store.recordVisit(id);mutable.update{it.copy(recentVisits=visits)}
    }
    fun removeRecentRepository(id:RepoId) {
        val visits=store.removeVisit(id);mutable.update{it.copy(recentVisits=visits)}
    }
    fun setRecentSort(sort:RecentSort) {store.recentSort=sort;mutable.update{it.copy(recentSort=sort)}}
    fun setLibraryGrid(grid:Boolean) {store.libraryGrid=grid;mutable.update{it.copy(libraryGrid=grid)}}
    fun toggleRecentPin(repo:RepoId) {
        val visit=mutable.value.recentVisits.firstOrNull{it.repo.fullName.equals(repo.fullName,true)}?:return
        val visits=store.setVisitPinned(repo,!visit.pinned);mutable.update{it.copy(recentVisits=visits)}
    }
    fun openSaved(bookmarks:Boolean) {
        mutable.update{it.copy(savedBookmarks=bookmarks)};switchDestination(Destination.SAVED,restore=false)
    }
    fun selectSavedTab(bookmarks:Boolean) {
        if(mutable.value.savedBookmarks==bookmarks)return
        requestJob?.cancel()
        mutable.update{it.copy(loading=false,downloadProgress=null,error=null,savedRefreshing=false)}
        mutable.update{it.copy(savedBookmarks=bookmarks)}
        if(bookmarks)pauseStarSearch()
        else if(mutable.value.starSearchQuery.isNotBlank())searchStars(mutable.value.starSearchQuery)
        else if(mutable.value.username.isNotBlank())loadStars()
    }
    fun navigate(destination: Destination) {
        if(destination==Destination.LIBRARY&&destination==mutable.value.destination&&mutable.value.project!=null) {
            returnToHome()
        } else switchDestination(destination,restore=true)
    }
    private fun switchDestination(destination:Destination,restore:Boolean) {
        if(restore&&destination==mutable.value.destination)return
        dismissFilePicker()
        pauseStarSearch()
        flushProgress()
        val previous=mutable.value
        tabSessions[previous.destination]=TabSession(previous,readingHistory.toList(),lastRequest,previous.loading)
        requestJob?.cancel();readingHistory.clear()
        if(!restore)tabSessions.remove(destination)
        val saved=tabSessions[destination]
        readingHistory.addAll(saved?.history.orEmpty());lastRequest=saved?.retry
        restoreRepositoryScreen(destination,saved?.screen)
        if(saved?.pending==true&&saved.retry!=null)request(saved.retry)
        else if(mutable.value.project==null) {
            if(destination==Destination.DISCOVER&&saved==null)discover()
            if(destination==Destination.SAVED&&!mutable.value.savedBookmarks&&mutable.value.username.isNotBlank()) {
                if(mutable.value.starSearchQuery.isNotBlank())searchStars(mutable.value.starSearchQuery)
                else if(saved==null||mutable.value.stars.isEmpty())loadStars()
            }
        }
    }
    // Restore only repository UI state. Account data, visits, bookmarks, Stars and filters stay shared and current.
    private fun restoreRepositoryScreen(destination:Destination,screen:GroveState?) {
        val saved=screen?:GroveState()
        mutable.update{current->current.copy(destination=destination,
            project=saved.project?.let{current.projects[it.id.fullName]?:it},ref=saved.ref,tab=saved.tab,document=saved.document,
            raw=saved.raw,immersive=saved.immersive,progress=saved.progress,headings=saved.headings,
            directory=saved.directory,files=saved.files,branches=saved.branches,
            issues=saved.issues,issue=saved.issue,closed=saved.closed,issuePage=saved.issuePage,moreIssues=saved.moreIssues,
            comments=saved.comments,commentPage=saved.commentPage,moreComments=saved.moreComments,issueProgress=saved.issueProgress,
            filePicker=null,loading=false,downloadProgress=null,openingDocument=saved.openingDocument,error=saved.error,discoverRefreshing=false,savedRefreshing=false)}
        saved.openingDocument?.let{target->documentLoads[target.key]?.let(::publishDocumentLoad)}
    }
    private fun returnToHome() {
        dismissFilePicker();flushProgress();requestJob?.cancel();readingHistory.clear();lastRequest=null
        val destination=mutable.value.destination
        tabSessions.remove(destination)
        restoreRepositoryScreen(destination,null)
    }
    fun addRepository(value: String): Boolean {
        val id = RepoId.parse(value) ?: return false
        val library = (listOf(id)+mutable.value.library).distinctBy { it.fullName.lowercase() }
        store.saveLibrary(library);mutable.update { it.copy(library=library) };openRepository(id)
        return true
    }
    fun openRepository(id: RepoId) {
        dismissFilePicker()
        pauseStarSearch()
        flushProgress();readingHistory.clear()
        mutable.update { it.copy(project=it.projects[id.fullName] ?: Project(id),ref="main",tab=RepoTab.READ,document=null,raw=false,
            immersive=false,openingDocument=null,directory="",files=emptyList(),issues=emptyList(),issue=null,branches=emptyList(),progress=0f,headings=emptyList()) }
        request {
            val project=api.project(id)
            mutable.update { it.copy(project=project,ref=project.branch,projects=it.projects+(id.fullName to project)+(project.id.fullName to project)) }
            rememberVisit(project.id)
            display(api.readme(id,project.branch))
        }
    }
    private fun display(document: Document) {
        rememberVisit(document.repo)
        val progress=store.progress(document.key)
        mutable.update { it.copy(document=document,openingDocument=null,downloadProgress=null,ref=document.ref,tab=RepoTab.READ,raw=false,progress=progress,headings=emptyList()) }
    }
    fun openBookmark(bookmark: Bookmark) {
        dismissFilePicker()
        flushProgress();readingHistory.clear()
        mutable.update { it.copy(project=it.projects[bookmark.repo.fullName] ?: Project(bookmark.repo),ref=bookmark.ref,
            document=null,tab=RepoTab.READ,immersive=false,issue=null,headings=emptyList()) }
        startDocument(DocumentTarget(bookmark.repo,bookmark.ref,bookmark.path))
    }
    fun resumeReading() { mutable.value.resume?.let { openBookmark(it);mutable.update { s -> s.copy(immersive=true) } } }
    fun openFile(path: String) {
        dismissFilePicker()
        val s=mutable.value;val project=s.project ?: return
        flushProgress();s.document?.let { readingHistory.addLast(it) }
        mutable.update { it.copy(document=null,tab=RepoTab.READ,headings=emptyList(),progress=0f) }
        startDocument(DocumentTarget(project.id,s.ref,path))
    }
    private fun publishDocumentLoad(load:DocumentLoad) {
        if(documentLoads[load.target.key]!==load||vault.accountId()!=load.account)return
        val s=mutable.value
        if(s.openingDocument?.key!=load.target.key||s.tab!=RepoTab.READ)return
        val doc=load.document
        if(doc!=null){mutable.update{it.copy(loading=false,error=null)};display(doc)}
        else mutable.update{it.copy(loading=load.busy,downloadProgress=load.progress,error=load.error)}
    }
    private fun startDocument(target:DocumentTarget,retry:Boolean=false) {
        requestJob?.cancel();lastRequest=null
        mutable.update{it.copy(openingDocument=target,document=null,tab=RepoTab.READ,loading=true,error=null,downloadProgress=null)}
        val existing=documentLoads[target.key]
        if(existing!=null&&existing.job?.isCancelled!=true&&(existing.busy||(existing.error!=null&&!retry))){publishDocumentLoad(existing);return}
        val load=DocumentLoad(target,vault.accountId(),existing?.progress)
        if(documentLoads.size>20) {
            val retained=(tabSessions.values.mapNotNull{it.screen.openingDocument?.key}+target.key).toSet()
            documentLoads.entries.removeAll{it.key !in retained&&it.value.job?.isCompleted==true}
        }
        documentLoads[target.key]=load;publishDocumentLoad(load)
        val read:suspend CoroutineScope.(String?)->Unit={transferId->
            try {
                val doc=api.document(target.repo,target.ref,target.path){progress->withContext(Dispatchers.Main.immediate){
                    if(transferId!=null)DocumentDownloadService.progress(transferId,progress)
                    if(documentLoads[target.key]===load){load.progress=progress;publishDocumentLoad(load)}
                }}
                ensureActive();load.document=doc;load.error=null
            }catch(e:CancellationException){
                if(generateSequence<Throwable>(e){it.cause}.any{it is DownloadPausedException})load.error="文件加载已暂停，点击重试继续。"
                else throw e
            }
            catch(e:Exception){
                if(e is GitHubException&&e.status==401&&vault.accountId()==load.account){
                    api.clearAccountFiles();vault.clear();switchAccount(null)
                }else load.error=e.message?:"文件加载失败，请重试。"
            }finally{if(currentCoroutineContext().isActive||load.error!=null){load.busy=false;publishDocumentLoad(load)}}
        }
        load.job=try {
            if(target.path.substringAfterLast('.').lowercase() in setOf("pdf","epub"))
                DocumentDownloadService.prepare(getApplication(),target.path.substringAfterLast('/')){id->read(id)}
            else viewModelScope.launch(start=CoroutineStart.LAZY){read(null)}
        }catch(_:Exception){
            load.busy=false;load.error="暂时无法启动文件加载，请返回应用后重试。";publishDocumentLoad(load);return
        }
        load.job!!.start()
    }
    fun cancelFileDownload() {
        val target=mutable.value.openingDocument?:return
        documentLoads[target.key]?.let{it.job?.cancel();it.busy=false;it.error=null}
        mutable.update{it.copy(openingDocument=null,downloadProgress=null,loading=false,error=null,immersive=false)}
        back()
    }
    fun back() {
        val s=mutable.value
        when {
            s.filePicker!=null -> dismissFilePicker()
            s.immersive&&s.openingDocument==null -> mutable.update { it.copy(immersive=false) }
            s.issue != null -> {
                requestJob?.cancel();lastRequest=null
                mutable.update { it.copy(issue=null,comments=emptyList(),loading=false,downloadProgress=null,error=null) }
            }
            s.tab==RepoTab.FILES && s.directory.isNotEmpty() -> loadDirectory(s.directory.substringBeforeLast('/',""))
            readingHistory.isNotEmpty() && s.tab==RepoTab.READ -> {
                requestJob?.cancel();lastRequest=null;flushProgress()
                mutable.update{it.copy(loading=false,downloadProgress=null,openingDocument=null,error=null)}
                display(readingHistory.removeLast())
            }
            s.tab!=RepoTab.READ -> selectTab(RepoTab.READ)
            else -> returnToHome()
        }
    }
    fun selectTab(tab: RepoTab) {
        if(mutable.value.tab==RepoTab.READ&&tab!=RepoTab.READ)flushProgress()
        dismissFilePicker()
        if(tab==RepoTab.READ&&mutable.value.openingDocument!=null) {
            requestJob?.cancel();lastRequest=null;mutable.update{it.copy(tab=tab,issue=null)}
            mutable.value.openingDocument?.let{documentLoads[it.key]?.let(::publishDocumentLoad)}
            return
        }
        if(tab==RepoTab.READ&&mutable.value.document!=null) {
            requestJob?.cancel();lastRequest=null
            mutable.update{it.copy(tab=RepoTab.READ,issue=null,error=null,loading=false,downloadProgress=null)}
            return
        }
        mutable.update { it.copy(tab=tab,issue=null,error=null) }
        when(tab) {
            RepoTab.FILES -> loadDirectory(mutable.value.directory)
            RepoTab.ISSUES -> loadIssues()
            RepoTab.READ -> if(mutable.value.document==null) mutable.value.project?.let { openRepository(it.id) }
        }
    }
    fun loadDirectory(path: String) {
        dismissFilePicker()
        val s=mutable.value;val project=s.project ?: return
        mutable.update { it.copy(directory=path,tab=RepoTab.FILES,files=emptyList()) }
        request { val files=api.directory(project.id,s.ref,path);mutable.update { it.copy(files=files) } }
    }
    fun openFilePicker() {
        val s=mutable.value
        if(s.tab!=RepoTab.READ||s.document==null)return
        loadPickerDirectory(if(s.document.originUrl!=null)""else s.document.path.substringBeforeLast('/',""))
    }
    fun loadPickerDirectory(path:String) {
        val s=mutable.value;val project=s.project?:return
        if(s.tab!=RepoTab.READ||s.document==null)return
        filePickerJob?.cancel()
        mutable.update{it.copy(filePicker=FilePickerState(path))}
        filePickerJob=viewModelScope.launch {
            try {
                val files=api.directory(project.id,s.ref,path)
                mutable.update{it.copy(filePicker=FilePickerState(path,files,loading=false))}
            }catch(e:CancellationException){throw e}
            catch(e:Exception) {
                if(e is GitHubException&&e.status==401){api.clearAccountFiles();vault.clear();switchAccount(null)}
                else mutable.update{it.copy(filePicker=FilePickerState(path,loading=false,error=e.message?:"文件列表加载失败，请重试。"))}
            }
        }
    }
    fun dismissFilePicker() {
        filePickerJob?.cancel();filePickerJob=null
        mutable.update{it.copy(filePicker=null)}
    }
    fun selectPickerFile(file:RepoFile) {
        if(mutable.value.filePicker==null)return
        if(file.folder)loadPickerDirectory(file.path)
        else if(file.path==mutable.value.document?.path)dismissFilePicker()
        else openFile(file.path)
    }
    fun loadBranches() { val id=mutable.value.project?.id ?: return
        request { val branches=api.branches(id);mutable.update { it.copy(branches=branches) } }
    }
    fun chooseBranch(ref: String) {
        dismissFilePicker()
        val s=mutable.value;val id=s.project?.id ?: return
        flushProgress();readingHistory.clear()
        mutable.update { it.copy(ref=ref,document=null,openingDocument=null,directory="",files=emptyList(),headings=emptyList()) }
        request { display(api.readme(id,ref)) }
    }
    fun toggleRaw() { mutable.update { it.copy(raw=!it.raw) } }
    fun toggleImmersive() { mutable.update { it.copy(immersive=!it.immersive) } }
    fun setHeadings(documentKey: String, headings: List<Heading>) { if(mutable.value.document?.key==documentKey) mutable.update { it.copy(headings=headings) } }
    fun progress(documentKey: String, progress: Float) {
        if(mutable.value.document?.key!=documentKey) return
        mutable.update { it.copy(progress=progress.coerceIn(0f,1f)) }
        if(System.currentTimeMillis()-lastSaved>1200) flushProgress()
    }
    fun flushProgress() {
        val s=mutable.value;val doc=s.document ?: return
        if((!doc.markdown&&doc.attachment?.kind!=PreviewKind.EPUB) || s.raw) return
        val bookmark=Bookmark(doc.repo,doc.ref,doc.path,s.progress)
        store.saveProgress(bookmark);mutable.update { it.copy(resume=bookmark) };lastSaved=System.currentTimeMillis()
    }
    fun toggleBookmark() {
        val s=mutable.value;val doc=s.document ?: return
        if(doc.originUrl!=null)return
        val value=Bookmark(doc.repo,doc.ref,doc.path,s.progress)
        val bookmarks=if(s.bookmarks.any { it.key==doc.key }) s.bookmarks.filterNot { it.key==doc.key } else listOf(value)+s.bookmarks
        store.saveBookmarks(bookmarks);mutable.update { it.copy(bookmarks=bookmarks) }
    }
    fun settings(font: Int=mutable.value.fontSize,warm: Boolean=mutable.value.warm) {
        store.fontSize=font.coerceIn(13,24);store.warm=warm;mutable.update { it.copy(fontSize=store.fontSize,warm=warm) }
    }
    fun setPreviewDiagnosticsEnabled(enabled:Boolean) {
        store.previewDiagnosticsEnabled=enabled
        mutable.update{it.copy(previewDiagnosticsEnabled=enabled)}
    }
    fun refreshCacheSize() {
        if(mutable.value.cacheBusy)return
        cacheJob?.cancel()
        cacheJob=viewModelScope.launch {
            mutable.update{it.copy(cacheBusy=true,cacheMessage=null)}
            try{val bytes=withContext(Dispatchers.IO){contentCache.size()};mutable.update{it.copy(cacheBytes=bytes)}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.update{it.copy(cacheMessage="暂时无法读取缓存大小，请重试。")}}
            finally{mutable.update{it.copy(cacheBusy=false)}}
        }
    }
    fun clearCache() {
        if(mutable.value.cacheBusy||mutable.value.authBusy)return
        cacheJob=viewModelScope.launch {
            mutable.update{it.copy(cacheBusy=true,cacheMessage=null)}
            try {
                flushProgress();val pickerJob=filePickerJob;dismissFilePicker()
                // Wait for blocking downloads to finish cancellation before deleting their files.
                for(job in listOf(requestJob,libraryJob,imageJob,starSearchJob,pickerJob)+documentLoads.values.map{it.job})job?.cancelAndJoin()
                documentLoads.clear()
                pendingImage=null
                tabSessions.entries.removeAll{it.value.screen.openingDocument!=null||it.value.screen.document?.attachment!=null||it.value.history.any{doc->doc.attachment!=null}}
                readingHistory.removeAll{it.attachment!=null}
                val bytes=withContext(Dispatchers.IO){contentCache.clear();contentCache.size()}
                mutable.update{it.copy(cacheBytes=bytes,cacheMessage="缓存已清理。",imageToSave=null,imageSaving=false,
                    loading=false,openingDocument=null,downloadProgress=null,starSearchBusy=false,savedRefreshing=false,discoverRefreshing=false)}
            }catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.update{it.copy(cacheMessage="部分缓存未能清理，请稍后重试。")}}
            finally{mutable.update{it.copy(cacheBusy=false)}}
        }
    }
    fun previewImage(url:String) {
        val doc=mutable.value.document?:return
        if(url.startsWith(doc.rawRoot)){followLink(url);return}
        flushProgress();readingHistory.addLast(doc)
        request{display(api.remoteImage(doc.repo,doc.ref,url))}
    }
    fun imageResource(doc:Document,url:String):Pair<String,ByteArray>?=api.imageResource(doc,url)
    fun showImageActions(document:Document,url:String) {
        if(!mutable.value.imageSaving)mutable.update{it.copy(imageToSave=ImageRequest(document,url))}
    }
    fun dismissImageActions(){mutable.update{it.copy(imageToSave=null)}}
    fun saveSelectedImage() {
        val target=mutable.value.imageToSave?:return
        if(mutable.value.imageSaving)return
        mutable.update{it.copy(imageToSave=null,imageSaving=true,imageSaveMessage=null)}
        imageJob=viewModelScope.launch {
            try {
                val image=api.imageForDownload(target.document,target.url)
                if(Build.VERSION.SDK_INT>=29) {
                    val message=imageStore.save(image)
                    mutable.update{it.copy(imageSaving=false,imageSaveMessage=message)}
                }else {
                    pendingImage=image
                    mutable.update{it.copy(imageSavePrompt=ImageSavePrompt(image.mimeType,image.fileName))}
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){imageSaveFailed(e.message?:"图片保存失败，请重试。")}
        }
    }
    fun imagePickerOpened(){mutable.update{it.copy(imageSavePrompt=null)}}
    fun imageSaveFailed(message:String) {
        pendingImage=null
        mutable.update{it.copy(imageSaving=false,imageSavePrompt=null,imageSaveMessage=message)}
    }
    fun finishImageSave(uri:Uri?) {
        val image=pendingImage;pendingImage=null
        mutable.update{it.copy(imageSavePrompt=null)}
        if(uri==null){mutable.update{it.copy(imageSaving=false)};return}
        if(image==null){imageSaveFailed("保存任务已失效，请重新长按图片。");return}
        imageJob=viewModelScope.launch {
            try {
                imageStore.saveToDocument(image,uri)
                mutable.update{it.copy(imageSaving=false,imageSaveMessage="图片已保存到所选位置")}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){imageSaveFailed(e.message?:"图片保存失败，请重试。")}
        }
    }
    fun clearImageSaveMessage(){mutable.update{it.copy(imageSaveMessage=null)}}
    fun loadMyRepositories(more:Boolean=false) {
        if(mutable.value.username.isBlank()){login();return}
        val s=mutable.value;val page=if(more)s.userRepoPage+1 else 1
        if(more&&(requestJob?.isActive==true||!s.moreUserRepos))return
        request{val projects=api.repositories(page);mutable.update{it.copy(userRepos=if(more)s.userRepos+projects else projects,userRepoPage=page,moreUserRepos=projects.size==30)}}
    }
    fun followLink(url: String): Boolean {
        val doc=mutable.value.document ?: return false
        if(url.startsWith(doc.rawRoot)) {
            val path=runCatching { URI(url).path.removePrefix(URI(doc.rawRoot).path) }.getOrNull() ?: return false
            if(path.isNotBlank()) { openFile(path);return true }
        }
        return false
    }
    fun discoveryFilter(category: Category=mutable.value.category,language: String=mutable.value.language,newProjects: Boolean=mutable.value.newProjects) {
        val s=mutable.value
        if(s.category==category&&s.language==language&&s.newProjects==newProjects)return
        mutable.update { it.copy(category=category,language=language,newProjects=newProjects,discoveries=emptyList(),discoverPage=1,moreDiscoveries=false) };discover()
    }
    fun refreshDiscoveries() {
        if(mutable.value.loading||mutable.value.discoverRefreshing)return
        discover(refresh=true)
    }
    fun searchDiscoveries(query:String) {
        val text=query.take(256)
        if(text==mutable.value.discoveryQuery)return
        mutable.update{it.copy(discoveryQuery=text,discoveries=emptyList(),discoverPage=1,moreDiscoveries=false,discoveryRevision=it.discoveryRevision+1)}
        discover(debounce=text.isNotBlank())
    }
    fun discover(more: Boolean=false,refresh:Boolean=false,debounce:Boolean=false) {
        val s=mutable.value;val page=if(more)s.discoverPage+1 else 1
        if(more&&(requestJob?.isActive==true||!s.moreDiscoveries))return
        request {
            if(debounce)delay(350)
            if(refresh)mutable.update{it.copy(discoverRefreshing=true)}
            val after=if(s.newProjects)LocalDate.now(ZoneOffset.UTC).minusDays(30).toString() else null
            val projects=api.discover(s.category,s.language,after,page,allowCache=!refresh,keyword=s.discoveryQuery)
            mutable.update { it.copy(discoveries=(if(more)s.discoveries+projects else projects).distinctBy { p->p.id.fullName },discoverPage=page,moreDiscoveries=projects.size==30&&page<33,
                discoveryRevision=it.discoveryRevision+if(refresh)1 else 0) }
        }
    }
    fun loadIssues(closed: Boolean=mutable.value.closed,more: Boolean=false) {
        val s=mutable.value;val id=s.project?.id ?: return;val page=if(more)s.issuePage+1 else 1
        if(more&&(requestJob?.isActive==true||!s.moreIssues))return
        mutable.update { it.copy(closed=closed,issues=if(more)it.issues else emptyList(),issue=null) }
        request { val (issues,hasMore)=api.issues(id,closed,page);mutable.update { it.copy(issues=if(more)s.issues+issues else issues,issuePage=page,moreIssues=hasMore) } }
    }
    fun openIssue(issue: Issue) { mutable.update { it.copy(issue=issue,comments=emptyList(),commentPage=1,moreComments=false,issueProgress=0f) };loadComments() }
    fun issueReadingProgress(key:String,value:Float) {
        val s=mutable.value;val issue=s.issue?:return;val project=s.project?:return
        if(key=="${project.id.fullName}:${s.ref}:issue-${issue.number}.md")mutable.update{it.copy(issueProgress=value.coerceIn(0f,1f))}
    }
    fun loadComments(more: Boolean=false) {
        val s=mutable.value;val id=s.project?.id ?: return;val issue=s.issue ?: return;val page=if(more)s.commentPage+1 else 1
        if(more&&(requestJob?.isActive==true||!s.moreComments))return
        request { val comments=api.comments(id,issue.number,page);mutable.update { it.copy(comments=if(more)s.comments+comments else comments,commentPage=page,moreComments=comments.size==30) } }
    }
    fun refreshSaved() {
        val s=mutable.value
        if(s.loading||s.savedRefreshing||s.starSearchBusy)return
        if(s.savedBookmarks) {
            mutable.update{it.copy(bookmarks=store.bookmarks(),resume=store.resume())}
        } else if(s.username.isNotBlank()) {
            if(s.starSearchQuery.isNotBlank())searchStars(s.starSearchQuery,refresh=true)
            else loadStars(refresh=true)
        }
    }
    fun loadStars(more: Boolean=false,refresh:Boolean=false) {
        if(mutable.value.username.isBlank())return
        if(more&&(requestJob?.isActive==true||!mutable.value.moreStars||mutable.value.starSearchBusy))return
        if(!more&&mutable.value.starSearchQuery.isBlank()) {
            resetStarIndex();mutable.update{it.copy(starSearchResults=emptyList(),starSearchScanned=0,starSearchComplete=false)}
        }
        val s=mutable.value;val page=if(more)s.starPage+1 else 1
        request {
            if(refresh)mutable.update{it.copy(savedRefreshing=true)}
            var revision:Long;var stars:List<Project>
            do {revision=starRevision;stars=api.starred(page)}while(revision!=starRevision)
            mutable.update {it.copy(stars=(if(more)it.stars+stars else stars).distinctBy{p->p.id.fullName.lowercase()},starPage=page,moreStars=stars.size==30,
                starStates=it.starStates+stars.associate{p->p.id.fullName.lowercase() to true})}
        }
    }
    private fun matchesStar(project:Project,query:String)=
        (project.id.fullName+" "+project.description+" "+project.language).contains(query.trim(),true)
    private fun pauseStarSearch() {
        starSearchJob?.cancel();starSearchJob=null
        mutable.update{it.copy(starSearchBusy=false,savedRefreshing=false)}
    }
    private fun resetStarIndex() {
        starSearchJob?.cancel();starSearchJob=null
        starIndex.clear();starIndexPage=1;starIndexComplete=false
    }
    fun searchStars(query:String,refresh:Boolean=false) {
        if(refresh)resetStarIndex()
        requestJob?.cancel()
        mutable.update{it.copy(loading=false,downloadProgress=null,error=null,starSearchQuery=query,
            starSearchResults=if(refresh)it.starSearchResults else starIndex.values.filter{p->matchesStar(p,query)},
            starSearchComplete=starIndexComplete,starSearchScanned=starIndex.size,starSearchError=null,
            savedRefreshing=refresh||it.savedRefreshing)}
        if(query.isBlank()||mutable.value.username.isBlank()){
            pauseStarSearch()
            if(query.isBlank()&&mutable.value.username.isNotBlank()&&mutable.value.stars.isEmpty())loadStars()
            return
        }
        if(starIndexComplete){mutable.update{it.copy(starSearchBusy=false)};return}
        mutable.update{it.copy(starSearchBusy=true)}
        if(starSearchJob?.isActive==true)return
        starSearchJob=viewModelScope.launch {
            try {
                delay(300)
                while(!starIndexComplete) {
                    val projects=api.starred(starIndexPage,100)
                    projects.forEach{starIndex[it.id.fullName.lowercase()]=it}
                    starIndexPage++
                    starIndexComplete=projects.size<100
                    mutable.update {s->s.copy(starSearchResults=starIndex.values.filter{matchesStar(it,s.starSearchQuery)},
                        starSearchScanned=starIndex.size,starSearchComplete=starIndexComplete,
                        starStates=s.starStates+projects.associate{it.id.fullName.lowercase() to true})}
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception) {
                if(e is GitHubException&&e.status==401){api.clearAccountFiles();vault.clear();switchAccount(null)}
                else mutable.update{it.copy(starSearchError=e.message?:"未能搜索全部 Star，请重试。",starSearchComplete=false)}
            }
            finally {if(currentCoroutineContext().isActive)mutable.update{it.copy(starSearchBusy=false,savedRefreshing=false)}}
        }
    }
    fun ensureStarStatus(id:RepoId) {
        val key=id.fullName.lowercase()
        if(mutable.value.username.isBlank()||mutable.value.starStates.containsKey(key)||starJobs.containsKey(key))return
        starOperation(id,reportError=false) {val starred=api.isStarred(id);mutable.update{it.copy(starStates=it.starStates+(key to starred))}}
    }
    fun toggleStar(project:Project) {
        if(mutable.value.username.isBlank()){login();return}
        val key=project.id.fullName.lowercase()
        if(starJobs.containsKey(key))return
        starOperation(project.id) {
            val previous=mutable.value.starStates[key]?:api.isStarred(project.id)
            val next=!previous
            api.setStarred(project.id,next)
            starRevision++
            fun updated(p:Project)=if(p.id.fullName.equals(project.id.fullName,true))p.copy(stars=(p.stars+if(next)1 else -1).coerceAtLeast(0))else p
            mutable.update {s->s.copy(starStates=s.starStates+(key to next),
                discoveries=s.discoveries.map(::updated),projects=s.projects.mapValues{updated(it.value)},
                project=s.project?.let(::updated),userRepos=s.userRepos.map(::updated),
                stars=if(next)(listOf(updated(s.project?.takeIf{it.id.fullName.equals(project.id.fullName,true)}?:project))+s.stars).distinctBy{it.id.fullName.lowercase()}
                else s.stars.filterNot{it.id.fullName.equals(project.id.fullName,true)})}
            resetStarIndex()
            mutable.update{it.copy(starSearchComplete=false,starSearchScanned=0,starSearchBusy=false,
                starSearchResults=if(next&&matchesStar(project,it.starSearchQuery))(listOf(updated(project))+it.starSearchResults).distinctBy{p->p.id.fullName.lowercase()}
                else it.starSearchResults.filterNot{p->p.id.fullName.equals(project.id.fullName,true)})}
            if(mutable.value.destination==Destination.SAVED&&mutable.value.project==null&&!mutable.value.savedBookmarks&&mutable.value.starSearchQuery.isNotBlank())searchStars(mutable.value.starSearchQuery)
        }
    }
    private fun starOperation(id:RepoId,reportError:Boolean=true,block:suspend()->Unit) {
        val key=id.fullName.lowercase();val account=vault.accountId()
        val job=viewModelScope.launch(start=CoroutineStart.LAZY) {
            mutable.update{it.copy(starBusy=it.starBusy+key)}
            try {block()}
            catch(e:CancellationException){throw e}
            catch(e:Exception) {
                if(vault.accountId()==account)mutable.update{it.copy(starError=if(reportError)e.message?:"Star 操作失败，请重试。" else it.starError,starStates=it.starStates-key)}
            }
            finally {
                if(currentCoroutineContext().isActive&&vault.accountId()==account) {
                    starJobs.remove(key);mutable.update{it.copy(starBusy=it.starBusy-key)}
                }
            }
        }
        starJobs[key]=job;job.start()
    }
    fun clearStarError(){mutable.update{it.copy(starError=null)}}
    fun login() {
        if(BuildConfig.GITHUB_CLIENT_ID.isBlank()) { mutable.update {it.copy(authError="此开发版本尚未启用 GitHub 登录。你可以先阅读公开仓库，文档书签和阅读记录仍会保存在本机。")};return }
        authJob?.cancel()
        authJob=viewModelScope.launch {
            mutable.update {it.copy(authBusy=true,authError=null)}
            try {
                val device=auth.begin();mutable.update {it.copy(authDevice=device)}
                val tokens=auth.awaitToken(device)
                val pendingApi=GitHubClient(File(getApplication<Application>().cacheDir,"github"),token={tokens.accessToken})
                val user=pendingApi.profile()
                flushProgress();requestJob?.cancel();libraryJob?.cancel();api.clearAccountFiles()
                vault.save(tokens);vault.identity(user)
                switchAccount(user)
                mutable.update {it.copy(username=user.login,authDevice=null)}
                if(mutable.value.destination==Destination.SAVED)loadStars()
            }catch(e: CancellationException){throw e}
            catch(e: Exception){mutable.update {it.copy(authError=e.message ?: "授权失败，请重试。",authDevice=null)}}
            finally {mutable.update {it.copy(authBusy=false)}}
        }
    }
    fun cancelLogin() {authJob?.cancel();mutable.update {it.copy(authDevice=null,authError=null,authBusy=false)}}
    fun logout() {flushProgress();cancelLogin();requestJob?.cancel();libraryJob?.cancel();api.clearAccountFiles();vault.clear();switchAccount(null)}
    fun clearError() {mutable.update {it.copy(error=null)}}
}
