package dev.grove.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.ReadingStore
import dev.grove.reader.domain.*
import dev.grove.reader.ui.GroveApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PaginationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app:Application
    private lateinit var model:GroveViewModel
    private val calls=CopyOnWriteArrayList<Pair<String,Int>>()
    private var edition=0
    private var failRequests=false
    private var emptyStars=false
    private fun project(index:Int)=JSONObject().put("owner",JSONObject().put("login","owner"))
        .put("name",if(index==60)"needle-final"else"project-$index").put("description","edition-$edition")
        .put("default_branch","main")
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("accountId","100").putString("login","reader").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("pagination-ui"),connectionFactory={url->
            val params=url.query.orEmpty().split('&').associate{it.substringBefore('=') to it.substringAfter('=')}
            val page=params["page"]?.toInt()?:1
            val count=params["per_page"]?.toInt()?:30
            val path=url.path
            calls.add(path to page)
            val rows=(0 until 61).drop((page-1)*count).take(count)
            val body=when {
                path=="/search/repositories" -> JSONObject().put("items",JSONArray(rows.map(::project))).toString()
                path=="/user/repos"||path=="/user/starred" -> JSONArray(if(emptyStars)emptyList<JSONObject>()else rows.map(::project)).toString()
                path.endsWith("/issues") -> JSONArray(if(page==1)(0 until 30).map{JSONObject().put("pull_request",JSONObject())}else
                    rows.map{JSONObject().put("number",it+1).put("title","Discussion $it").put("user",JSONObject().put("login","author")).put("comments",0).put("state","open").put("html_url","https://github.com/owner/project/issues/${it+1}")}).toString()
                path.endsWith("/readme") -> JSONObject().put("path","README.md").put("size",8).put("encoding","base64")
                    .put("content",Base64.getEncoder().encodeToString("# Reader".toByteArray())).toString()
                else -> project(0).toString()
            }.toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode():Int {if(failRequests)throw java.io.IOException("offline");return 200}
                override fun getInputStream()=body.inputStream()
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
    }
    private fun awaitLoad() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.waitForIdle()
    }
    private fun awaitSearch() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));!model.state.value.starSearchBusy}
        compose.waitForIdle()
    }
    private fun list()=compose.onNode(hasScrollToIndexAction())
    private fun openStars() {compose.runOnIdle{model.openSaved(false)};awaitLoad()}
    private fun pull() {list().performTouchInput{swipeDown(startY=10f,endY=height-10f)}}
    private fun requestCount(path:String)=calls.count{it.first==path}
    @Test fun starsLoadOnScrollKeepPositionStopAtEndAndAvoidDuplicateRequests() {
        openStars()
        assertEquals(30,model.state.value.stars.size)
        list().performScrollToIndex(29);awaitLoad()
        assertEquals(60,model.state.value.stars.size)
        compose.onNodeWithText("project-29").assertIsDisplayed()
        list().performScrollToIndex(59);awaitLoad()
        assertEquals(61,model.state.value.stars.size)
        assertFalse(model.state.value.moreStars)
        compose.runOnIdle{model.loadStars(true);model.loadStars(true)};awaitLoad()
        assertEquals(listOf(1,2,3),calls.filter{it.first=="/user/starred"}.map{it.second})
        compose.onNodeWithText("加载更多").assertDoesNotExist()
    }
    @Test fun starPullRefreshReplacesPagesAndFailurePreservesResultsWithoutRetryLoop() {
        openStars();list().performScrollToIndex(29);awaitLoad();list().performScrollToIndex(0)
        edition=1;val before=requestCount("/user/starred");pull()
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();requestCount("/user/starred")>before};awaitLoad()
        assertEquals(30,model.state.value.stars.size);assertEquals(1,model.state.value.starPage)
        assertEquals("edition-1",model.state.value.stars.first().description)
        val old=model.state.value.stars;failRequests=true;pull();awaitLoad()
        assertEquals(old,model.state.value.stars);assertNotNull(model.state.value.error)
        assertFalse(model.state.value.savedRefreshing)
        val failed=requestCount("/user/starred");list().performScrollToIndex(29);compose.waitForIdle()
        assertEquals(failed,requestCount("/user/starred"))
        failRequests=false;compose.onNodeWithText("重试").performClick();awaitLoad()
        assertNull(model.state.value.error)
    }
    @Test fun emptyStarListSupportsPullRefresh() {
        emptyStars=true;openStars();assertTrue(model.state.value.stars.isEmpty())
        emptyStars=false;pull();awaitLoad();assertEquals(30,model.state.value.stars.size)
    }
    @Test fun failedNextPageRetainsRowsPausesUntilRetryAndRejectsConcurrentLoads() {
        openStars();failRequests=true
        compose.runOnIdle{model.loadStars(true);model.loadStars(true)};awaitLoad()
        assertEquals(30,model.state.value.stars.size);assertNotNull(model.state.value.error)
        assertEquals(listOf(1,2),calls.filter{it.first=="/user/starred"}.map{it.second})
        list().performScrollToIndex(29);compose.waitForIdle()
        assertEquals(2,requestCount("/user/starred"))
        failRequests=false;compose.onNodeWithText("重试").performClick();awaitLoad()
        assertEquals(60,model.state.value.stars.size)
        assertEquals(listOf(1,2,2),calls.filter{it.first=="/user/starred"}.map{it.second})
    }
    @Test fun repeatedPullRefreshWhileBusyOnlyStartsOneRequest() {
        openStars();val before=requestCount("/user/starred")
        compose.runOnIdle{model.refreshSaved();model.refreshSaved()};awaitLoad()
        assertEquals(before+1,requestCount("/user/starred"));assertFalse(model.state.value.savedRefreshing)
    }
    @Test fun pullRefreshRebuildsCompleteStarSearchAndKeepsQuery() {
        openStars();compose.onNodeWithContentDescription("搜索收藏").performClick();compose.onNode(hasSetTextAction()).performTextInput("needle");awaitSearch()
        compose.onNodeWithText("needle-final").assertIsDisplayed()
        assertEquals(61,model.state.value.starSearchScanned)
        val before=requestCount("/user/starred");edition=2;pull();awaitSearch()
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));requestCount("/user/starred")>before}
        awaitSearch()
        assertTrue(requestCount("/user/starred")>before)
        assertEquals("needle",model.state.value.starSearchQuery)
        assertTrue(model.state.value.starSearchComplete)
        assertEquals("edition-2",model.state.value.starSearchResults.single().description)
        assertFalse(model.state.value.savedRefreshing)
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/reports/ui/saved-refresh.png").apply{parentFile?.mkdirs()}.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun localBookmarkPullRefreshReloadsStorageAndRetainsSearch() {
        compose.runOnIdle{model.openSaved(true)}
        compose.onNodeWithContentDescription("搜索收藏").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("guide")
        val store=ReadingStore(app,"100")
        store.saveBookmarks(listOf(Bookmark(RepoId("owner","project"),"main","guide.md")))
        pull();compose.waitForIdle()
        compose.onNodeWithText("guide.md").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("guide")
        assertEquals(0,requestCount("/user/starred"))
    }
    @Test fun discoveryLoadsNextPageOnScroll() {
        compose.runOnIdle{model.navigate(Destination.DISCOVER)};awaitLoad()
        list().performScrollToIndex(29);awaitLoad()
        assertEquals(60,model.state.value.discoveries.size)
        assertEquals(listOf(1,2),calls.filter{it.first=="/search/repositories"}.map{it.second})
    }
    @Test fun myProjectsLoadsNextPageOnScroll() {
        compose.runOnIdle{model.navigate(Destination.PROFILE)}
        compose.onNodeWithText("我的项目").performClick();awaitLoad()
        list().performScrollToIndex(29);awaitLoad()
        assertEquals(60,model.state.value.userRepos.size)
        assertEquals(listOf(1,2),calls.filter{it.first=="/user/repos"}.map{it.second})
    }
    @Test fun issuePaginationSkipsPageContainingOnlyPullRequestsAndContinuesOnScroll() {
        compose.runOnIdle{model.openRepository(RepoId("owner","project"))};awaitLoad()
        compose.onNodeWithContentDescription("更多仓库操作").performClick()
        compose.onNodeWithText("查看 Issues").performClick();awaitLoad()
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();model.state.value.issuePage==2||model.state.value.error!=null};awaitLoad()
        assertNull(model.state.value.error)
        assertEquals(30,model.state.value.issues.size)
        list().performScrollToIndex(29);awaitLoad()
        assertEquals(31,model.state.value.issues.size);assertFalse(model.state.value.moreIssues)
    }
}
