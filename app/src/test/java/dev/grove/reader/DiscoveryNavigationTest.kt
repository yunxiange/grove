package dev.grove.reader

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.Destination
import dev.grove.reader.ui.GroveApp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w393dp-h851dp")
class DiscoveryNavigationTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var model:GroveViewModel
    private var version=0
    private var failSearch=false
    private var offline=false
    private var emptySearch=false
    private val searchPages=mutableListOf<Int>()
    private fun project(index:Int)=JSONObject().put("owner",JSONObject().put("login","owner"))
        .put("name","project-$index").put("description","edition-$version")
        .put("stargazers_count",1000-index).put("default_branch","main")
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().putString("library","[]").commit()
        model=GroveViewModel(app)
        val cache=app.cacheDir.resolve("discovery-test").apply{deleteRecursively();mkdirs()}
        val api=GitHubClient(cache,connectionFactory={url->
            val search=url.path=="/search/repositories"
            val page=url.query.orEmpty().split('&').find{it.startsWith("page=")}?.substringAfter('=')?.toInt()?:1
            if(search)synchronized(searchPages){searchPages.add(page)}
            val body=when {
                search -> JSONObject().put("items",JSONArray((0 until if(emptySearch)0 else 30).map{project(it+(page-1)*30)})).toString()
                url.path.endsWith("/readme") -> JSONObject().put("path","README.md").put("encoding","base64").put("size",8)
                    .put("content",Base64.getEncoder().encodeToString("# Reader".toByteArray())).toString()
                else -> project(url.path.substringAfterLast('-').toIntOrNull()?:0).toString()
            }.toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode():Int {
                    if(search&&offline)throw java.io.IOException("offline")
                    return if(search&&failSearch)500 else 200
                }
                override fun getInputStream()=body.inputStream()
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
        compose.setContent{GroveApp(model)}
        compose.runOnIdle{model.navigate(Destination.DISCOVER)}
        awaitLoad()
    }
    private fun awaitLoad() {
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idle();!model.state.value.loading}
        compose.waitForIdle()
    }
    private fun list()=compose.onNode(hasScrollToIndexAction())
    private fun position()=list().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun openingProjectAndReturningRestoresListPosition() {
        list().performScrollToIndex(18)
        list().performSemanticsAction(SemanticsActions.ScrollBy){it(0f,12f)}
        val before=position()
        compose.onNodeWithText("project-18").performClick()
        awaitLoad()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        assertEquals("返回发现页应保留项目索引和滚动偏移",before,position(),0.0001f)
        assertEquals("返回不应重新加载首页",listOf(1),searchPages.toList())
    }
    @Test fun switchingBottomTabsPreservesPosition() {
        list().performScrollToIndex(18)
        val before=position()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("发现").performClick()
        assertEquals(before,position(),0.0001f)
    }
    @Test fun changingCategoryResetsPosition() {
        list().performScrollToIndex(18)
        compose.onNodeWithText("AI").performClick()
        awaitLoad()
        assertEquals(0f,position(),0.0001f)
        assertEquals(dev.grove.reader.domain.Category.AI,model.state.value.category)
    }
    private fun pullToRefresh() {
        val before=searchPages.size
        list().performTouchInput{swipeDown(startY=10f,endY=height-10f)}
        compose.waitUntil(10_000){shadowOf(Looper.getMainLooper()).idle();searchPages.size>before}
        awaitLoad()
    }
    @Test fun pullRefreshReplacesPaginationAndKeepsFilters() {
        compose.onNodeWithText("AI").performClick();awaitLoad()
        compose.runOnIdle{model.discover(more=true)};awaitLoad()
        assertEquals(60,model.state.value.discoveries.size)
        version=1
        pullToRefresh()
        val s=model.state.value
        assertEquals(30,s.discoveries.size)
        assertEquals(1,s.discoverPage)
        assertEquals(dev.grove.reader.domain.Category.AI,s.category)
        assertEquals("edition-1",s.discoveries.first().description)
        assertEquals(1,searchPages.last())
        assertFalse(s.discoverRefreshing)
    }
    @Test fun refreshFailureKeepsExistingResultsAndStopsIndicator() {
        val before=model.state.value.discoveries
        offline=true
        pullToRefresh()
        val s=model.state.value
        assertEquals(before,s.discoveries)
        assertNotNull(s.error)
        assertFalse(s.discoverRefreshing)
        assertFalse(s.loading)
    }
    @Test fun emptyResultsCanStillBePulledToRefresh() {
        emptySearch=true
        compose.onNodeWithContentDescription("刷新发现").performClick();awaitLoad()
        assertEquals(0,model.state.value.discoveries.size)
        emptySearch=false
        pullToRefresh()
        assertEquals(30,model.state.value.discoveries.size)
    }
    @Test fun repeatedRefreshWhileLoadingMakesOnlyOneRequest() {
        val before=searchPages.size
        compose.runOnIdle{model.refreshDiscoveries();model.refreshDiscoveries()}
        awaitLoad()
        assertEquals(before+1,searchPages.size)
    }
}
