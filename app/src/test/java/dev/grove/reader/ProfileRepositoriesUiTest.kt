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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileRepositoriesUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app:Application
    private lateinit var model:GroveViewModel
    private fun project(name:String,stars:Int=0)=JSONObject().put("owner",JSONObject().put("login","owner"))
        .put("name",name).put("description","A quiet place for project notes.").put("language","Kotlin")
        .put("stargazers_count",stars).put("private",name=="gamma").put("default_branch","main")
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("accountId","100").putString("login","reader").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("profile-ui-test"),connectionFactory={url->
            val params=url.query.orEmpty().split('&').associate{it.substringBefore('=') to it.substringAfter('=')}
            val name=url.path.substringAfterLast('/')
            val body=when {
                url.path=="/search/repositories" -> JSONObject().put("items",JSONArray(listOf(project("paper-notes",4200),project("android-reader",3100),project("open-library",1800)))).toString()
                url.path=="/user/repos" -> JSONArray(listOf(project("alpha",10),project("beta",100),project("gamma",50))).toString()
                url.path=="/user/starred" -> {
                    val count=params["per_page"]?.toInt()?:30;val page=params["page"]?.toInt()?:1
                    JSONArray((0 until 205).drop((page-1)*count).take(count).map{project(if(it==204)"needle-final"else"star-$it",it)}).toString()
                }
                name=="readme" -> JSONObject().put("path","README.md").put("size",8).put("encoding","base64")
                    .put("content",Base64.getEncoder().encodeToString("# Reading notes".toByteArray())).toString()
                else -> project(name,when(name){"beta"->100;"gamma"->50;else->10}).toString()
            }.toByteArray()
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
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
    private fun open(name:String) {
        compose.runOnIdle{model.openRepository(RepoId("owner",name))};awaitLoad()
    }
    private fun snapshot(name:String) {
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file=File("build/reports/ui/$name.png").apply{parentFile?.mkdirs()}
            file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        }
    }
    @Test fun profileKeepsUsefulEntriesVisibleAndSettingsCollapsedAndOpensBookmarksDirectly() {
        open("alpha")
        compose.runOnIdle{model.progress(model.state.value.document!!.key,.45f);model.toggleBookmark();model.navigate(Destination.PROFILE)}
        compose.onNodeWithText("我的项目").assertIsDisplayed()
        compose.onNodeWithText("文档书签").assertIsDisplayed()
        compose.onNodeWithText("继续阅读").assertIsDisplayed()
        compose.onNodeWithText("阅读偏好").assertDoesNotExist()
        compose.onNodeWithText("仓库授权").assertDoesNotExist()
        snapshot("profile")
        compose.onNodeWithContentDescription("更多功能").performClick()
        compose.onNodeWithText("阅读偏好").assertIsDisplayed()
        compose.onNodeWithText("仓库授权").assertIsDisplayed()
        compose.onNodeWithText("关于 Grove").performClick()
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("文档书签").performClick()
        compose.onNodeWithText("文档书签 1").assertIsSelected()
        compose.onNodeWithText("README.md").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("unmatched")
        snapshot("bookmarks-empty")
        compose.onNodeWithText("没有匹配的书签。").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除收藏搜索").performClick()
        compose.onNodeWithText("README.md").assertIsDisplayed()
    }
    @Test fun myProjectsShowsAccountRepositoriesAndOpeningOneRecordsARecentVisit() {
        compose.runOnIdle{model.navigate(Destination.PROFILE)}
        compose.onNodeWithText("我的项目").performClick();awaitLoad()
        compose.onNodeWithText("alpha").assertIsDisplayed()
        compose.onNodeWithText("gamma").performClick();awaitLoad()
        assertEquals("gamma",model.state.value.recentVisits.first().repo.name)
        compose.runOnIdle{model.navigate(Destination.LIBRARY)}
        compose.onNodeWithText("gamma").assertIsDisplayed()
        assertTrue(ReadingStore(app,"100").recentVisits().any{it.repo.name=="gamma"})
    }
    @Test fun recentProjectsCanSortAndRemoveWithoutDeletingBookmarks() {
        open("beta");compose.runOnIdle{model.toggleBookmark()}
        open("gamma");open("alpha")
        compose.runOnIdle{model.navigate(Destination.LIBRARY)}
        assertEquals("alpha",model.state.value.recentVisits.first().repo.name)
        compose.onNodeWithContentDescription("排序最近项目").performClick()
        compose.onNodeWithText("Star 数").performClick()
        val betaY=compose.onNodeWithText("beta").fetchSemanticsNode().boundsInRoot.top
        val alphaY=compose.onNodeWithText("alpha").fetchSemanticsNode().boundsInRoot.top
        assertTrue(betaY<alphaY)
        snapshot("recent-projects")
        compose.onNodeWithContentDescription("项目操作 owner/beta").performClick()
        compose.onNodeWithText("移除访问记录").performClick()
        compose.onNodeWithText("beta").assertDoesNotExist()
        assertTrue(model.state.value.bookmarks.any{it.repo.name=="beta"})
        assertEquals(RecentSort.STARS,ReadingStore(app,"100").recentSort)
        assertFalse(ReadingStore(app,"100").recentVisits().any{it.repo.name=="beta"})
    }
    @Test fun favoritesSearchTraversesEveryStarPageAndShowsLastPageMatch() {
        compose.runOnIdle{model.openSaved(false)};awaitLoad()
        assertEquals(30,model.state.value.stars.size)
        compose.onNode(hasSetTextAction()).performTextInput("needle")
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));!model.state.value.starSearchBusy}
        compose.onNodeWithText("needle-final").assertIsDisplayed()
        assertEquals(205,model.state.value.starSearchScanned)
        assertTrue(model.state.value.starSearchComplete)
        snapshot("favorites-search")
    }
    @Test fun discoveryShowsCategoriesSortModesAndProjectResults() {
        compose.runOnIdle{model.navigate(Destination.DISCOVER)};awaitLoad()
        compose.onNodeWithText("高星精选").assertIsDisplayed()
        compose.onNodeWithText("新晋热门").assertIsDisplayed()
        compose.onNodeWithText("全部语言").assertIsDisplayed()
        compose.onNodeWithText("paper-notes").assertIsDisplayed()
        compose.onNodeWithText("android-reader").assertIsDisplayed()
        snapshot("discovery")
    }
}
