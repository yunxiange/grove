package dev.grove.reader

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class TabNavigationTest {
    private lateinit var model:GroveViewModel
    private val requests=CopyOnWriteArrayList<String>()
    @Volatile private var failReadme=false
    @Volatile private var readmeGate:CountDownLatch?=null
    private val readmeStarted=CountDownLatch(1)
    private fun document(path:String)=JSONObject().put("path",path).put("size",30).put("encoding","base64")
        .put("content",Base64.getEncoder().encodeToString("# $path\n\nReading".toByteArray())).toString()
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().commit()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("tab-navigation-test").apply{deleteRecursively()},connectionFactory={url->
            requests.add(url.path)
            val body=when {
                url.path=="/search/repositories" -> JSONObject().put("items",JSONArray()).toString()
                url.path.endsWith("/readme") -> document("README.md")
                url.path.endsWith("/contents/docs") -> JSONArray(listOf(JSONObject().put("path","docs/guide.md").put("type","file"))).toString()
                url.path.endsWith("/contents/") -> JSONArray(listOf(JSONObject().put("path","docs").put("type","dir"))).toString()
                url.path.endsWith("/comments") -> JSONArray().toString()
                url.path.endsWith("/issues") -> JSONArray().toString()
                url.path.contains("/contents/") -> document(url.path.substringAfter("/contents/"))
                else -> JSONObject().put("name",url.path.substringAfterLast('/')).put("owner",JSONObject().put("login","owner")).put("default_branch","main").toString()
            }.toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=if(failReadme&&url.path.endsWith("/readme"))500 else 200
                override fun getInputStream():java.io.InputStream {
                    if(url.path.endsWith("/readme"))readmeGate?.let{readmeStarted.countDown();it.await(10,TimeUnit.SECONDS)}
                    return body.inputStream()
                }
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
    }
    @After fun releaseReadme(){readmeGate?.countDown()}
    private fun open(name:String){model.openRepository(RepoId("owner",name));finish()}
    private fun finish() {
        val end=System.nanoTime()+10_000_000_000L
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if(!model.state.value.loading)return
            Thread.sleep(2)
        }while(System.nanoTime()<end)
        fail("Navigation load did not complete")
    }
    @Test fun switchingBottomTabsReturnsToTheReadingPageInsteadOfTheHistoryHome() {
        model.openRepository(RepoId("owner","project"));finish()
        model.progress(model.state.value.document!!.key,.6f)
        val original=model.state.value.document
        model.navigate(Destination.PROFILE)
        model.navigate(Destination.LIBRARY);finish()
        assertEquals("足迹应恢复打开的项目",RepoId("owner","project"),model.state.value.project?.id)
        assertEquals(original,model.state.value.document)
        assertEquals(.6f,model.state.value.progress,.001f)
    }
    @Test fun eachTabKeepsItsOwnProjectAndReadingBackStackWithoutRefetching() {
        open("first");model.openFile("docs/guide.md");finish()
        model.navigate(Destination.PROFILE);open("second")
        val before=requests.size
        model.navigate(Destination.LIBRARY)
        assertEquals("first",model.state.value.project!!.id.name)
        assertEquals("docs/guide.md",model.state.value.document!!.path)
        assertEquals(before,requests.size)
        model.back()
        assertEquals("README.md",model.state.value.document!!.path)
        model.back();assertNull(model.state.value.project)
        model.navigate(Destination.PROFILE)
        assertEquals("second",model.state.value.project!!.id.name)
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project)
    }
    @Test fun reselectingFootprintsReturnsHomeFromADeepReadingStackAndKeepsProgress() {
        open("first");model.openFile("docs/guide.md");finish()
        model.openFile("docs/details.md");finish()
        val document=model.state.value.document!!
        model.progress(document.key,.65f)
        val before=requests.size
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project)
        assertNull(model.state.value.document)
        assertEquals(listOf(RepoId("owner","first")),model.state.value.recentVisits.map{it.repo})
        assertEquals(before,requests.size)
        model.navigate(Destination.PROFILE)
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project)
        open("second");model.back()
        assertNull("快速返回后不应留下旧文档返回记录",model.state.value.project)
        open("first");model.openFile(document.path);finish()
        assertEquals(.65f,model.state.value.progress,.001f)
    }
    @Test fun reselectingAnotherTabStillKeepsItsOpenProject() {
        model.navigate(Destination.PROFILE);open("first")
        val before=requests.size
        model.navigate(Destination.PROFILE)
        assertEquals("first",model.state.value.project!!.id.name)
        assertEquals(before,requests.size)
    }
    @Test fun reselectingFootprintsHomeDoesNotReloadOrClearVisits() {
        open("first");model.navigate(Destination.LIBRARY)
        val home=model.state.value
        val before=requests.size
        model.navigate(Destination.LIBRARY)
        assertEquals(home,model.state.value)
        assertEquals(before,requests.size)
    }
    @Test fun sharedBookmarksPreferencesAndVisitsNeverRollBackWithARestoredRoute() {
        open("first");model.toggleBookmark()
        model.navigate(Destination.PROFILE);open("second");model.toggleBookmark()
        model.settings(font=21);model.toggleRecentPin(RepoId("owner","first"))
        model.navigate(Destination.LIBRARY)
        assertEquals(21,model.state.value.fontSize)
        assertEquals(2,model.state.value.bookmarks.size)
        assertEquals(2,model.state.value.recentVisits.size)
        assertTrue(model.state.value.recentVisits.first{it.repo.name=="first"}.pinned)
    }
    @Test fun fileBrowserDirectoryAndIssueDetailRestoreIndependently() {
        open("first");model.loadDirectory("docs");finish()
        model.navigate(Destination.PROFILE);open("second")
        model.selectTab(RepoTab.ISSUES);finish()
        model.openIssue(Issue(7,"Discussion","body","author","open",0,"",emptyList(),"https://github.com/owner/second/issues/7"));finish()
        model.issueReadingProgress("owner/second:main:issue-7.md",.7f)
        model.navigate(Destination.LIBRARY)
        assertEquals(RepoTab.FILES,model.state.value.tab)
        assertEquals("docs",model.state.value.directory)
        assertEquals("docs/guide.md",model.state.value.files.single().path)
        model.navigate(Destination.PROFILE)
        assertEquals(7,model.state.value.issue!!.number)
        assertEquals(.7f,model.state.value.issueProgress,.001f)
    }
    @Test fun explicitBookmarksShortcutOpensItsRootEvenIfSavedHasAnOpenProject() {
        model.navigate(Destination.SAVED);open("from-saved")
        model.navigate(Destination.PROFILE)
        model.openSaved(true)
        assertNull(model.state.value.project)
        assertEquals(Destination.SAVED,model.state.value.destination)
        assertTrue(model.state.value.savedBookmarks)
    }
    @Test fun errorAndRetryRemainBoundToTheirOriginalProject() {
        failReadme=true;open("first");assertNotNull(model.state.value.error)
        model.navigate(Destination.PROFILE);failReadme=false;open("second")
        model.navigate(Destination.LIBRARY)
        assertNotNull(model.state.value.error)
        model.retry();finish()
        assertEquals("first",model.state.value.document!!.repo.name)
        assertNull(model.state.value.error)
    }
    @Test fun logoutClearsAllSuspendedRepositoryRoutes() {
        open("first");model.navigate(Destination.PROFILE);open("second")
        model.logout()
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project)
        assertNull(model.state.value.document)
        model.navigate(Destination.PROFILE)
        assertNull(model.state.value.project)
    }
    @Test fun returningToATabWithACancelledLoadResumesTheCorrectRequest() {
        readmeGate=CountDownLatch(1)
        model.openRepository(RepoId("owner","first"))
        val end=System.nanoTime()+10_000_000_000L
        while(readmeStarted.count>0&&System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(2)}
        assertEquals(0L,readmeStarted.count)
        model.navigate(Destination.PROFILE)
        assertNull(model.state.value.project)
        readmeGate!!.countDown()
        model.navigate(Destination.LIBRARY);finish()
        assertEquals("first",model.state.value.document!!.repo.name)
        assertEquals("README.md",model.state.value.document!!.path)
        assertTrue(requests.count{it.endsWith("/readme")}>=2)
    }
}
