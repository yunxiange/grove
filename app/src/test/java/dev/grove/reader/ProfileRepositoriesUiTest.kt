package dev.grove.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.time.Duration
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileRepositoriesUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var app:Application
    private lateinit var model:GroveViewModel
    private val discoveryQueries=CopyOnWriteArrayList<String>()
    private var longReadme=false
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
            if(url.path=="/search/repositories")discoveryQueries.add(URLDecoder.decode(params["q"].orEmpty(),"UTF-8"))
            val body=when {
                url.path=="/search/repositories" -> JSONObject().put("items",JSONArray(listOf(project("paper-notes",4200),project("android-reader",3100),project("open-library",1800)))).toString()
                url.path=="/user/repos" -> JSONArray(listOf(project("alpha",10),project("beta",100),project("gamma",50))).toString()
                url.path=="/user/starred" -> {
                    val count=params["per_page"]?.toInt()?:30;val page=params["page"]?.toInt()?:1
                    JSONArray((0 until 205).drop((page-1)*count).take(count).map{project(if(it==204)"needle-final"else"star-$it",it)}).toString()
                }
                name=="readme" -> JSONObject().put("path","README.md").put("size",8).put("encoding","base64")
                    .put("content",Base64.getEncoder().encodeToString((if(longReadme)"# Reading notes\n\n"+"A long reading paragraph.\n".repeat(150)else"# Reading notes").toByteArray())).toString()
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
            val canvas=Canvas(image)
            view.draw(canvas)
            ShadowDialog.getLatestDialog()?.takeIf{it.isShowing}?.window?.decorView?.draw(canvas)
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
        compose.onNodeWithContentDescription("设置").performClick()
        snapshot("profile-settings")
        compose.onNodeWithText("阅读偏好").assertIsDisplayed()
        compose.onNodeWithText("缓存管理").assertIsDisplayed()
        compose.onNodeWithText("仓库与账号").assertIsDisplayed()
        compose.onNodeWithText("仓库授权").assertDoesNotExist()
        compose.onNodeWithText("已添加的仓库").assertDoesNotExist()
        compose.onNodeWithText("退出登录").assertDoesNotExist()
        compose.onNodeWithText("开发者选项").assertDoesNotExist()
        compose.onNodeWithText("关于 Grove").performClick()
        compose.onNodeWithText("版本 ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("开发者选项").assertIsDisplayed()
        snapshot("profile-about")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("阅读偏好").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭个人设置").performClick()
        compose.onNodeWithText("文档书签").performClick()
        compose.onNodeWithText("文档书签 1").assertIsSelected()
        compose.onNodeWithText("README.md").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索收藏").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("unmatched")
        snapshot("bookmarks-empty")
        compose.onNodeWithText("没有匹配的书签。").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除收藏搜索").performClick()
        compose.onNodeWithText("README.md").assertIsDisplayed()
    }
    @Test fun readingPreferencesReturnToSettingsAndKeepChanges() {
        compose.runOnIdle{model.navigate(Destination.PROFILE)}
        val originalFont=model.state.value.fontSize
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("阅读偏好").performClick()
        compose.onNodeWithText("文字大小").assertIsDisplayed()
        compose.onNodeWithContentDescription("放大文字").performClick()
        compose.onNodeWithText("暖纸").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("缓存管理").assertIsDisplayed()
        compose.onNodeWithText("仓库与账号").assertIsDisplayed()
        compose.onNodeWithText("文字大小").assertDoesNotExist()
        compose.onNodeWithText("阅读偏好").performClick()
        compose.onNodeWithText("暖纸").assertIsSelected()
        compose.onNodeWithText("${originalFont+1}").assertIsDisplayed()
        snapshot("profile-reading-preferences")
        compose.onNodeWithContentDescription("关闭个人设置").performClick()
        compose.onNodeWithText("我的项目").assertIsDisplayed()
        compose.onNodeWithText("文字大小").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(originalFont+1,ReadingStore(app,"100").fontSize)
            assertTrue(ReadingStore(app,"100").warm)
        }
    }
    @Test fun readerPreferencesCloseBackToTheSameDocument() {
        open("alpha")
        val key=model.state.value.document!!.key
        compose.onNodeWithContentDescription("阅读设置").performClick()
        compose.onNodeWithText("文字大小").assertIsDisplayed()
        snapshot("reader-reading-preferences")
        compose.onNodeWithContentDescription("关闭阅读偏好").performClick()
        compose.onNodeWithText("文字大小").assertDoesNotExist()
        compose.onNodeWithContentDescription("阅读设置").assertIsDisplayed()
        compose.runOnIdle{assertEquals(key,model.state.value.document!!.key)}
    }
    @Test fun accountActionsStayTogetherAndLogoutClosesTheSettingsSheet() {
        compose.runOnIdle{model.navigate(Destination.PROFILE)}
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("仓库与账号").performClick()
        compose.onNodeWithText("已添加的仓库").assertIsDisplayed()
        compose.onNodeWithText("仓库授权").assertIsDisplayed()
        compose.onNodeWithText("退出登录").assertIsDisplayed()
        compose.onNodeWithText("阅读偏好").assertDoesNotExist()
        compose.onNodeWithText("开发者选项").assertDoesNotExist()
        snapshot("profile-account")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("仓库授权").assertDoesNotExist()
        compose.onNodeWithText("仓库与账号").performClick()
        compose.onNodeWithText("退出登录").performClick()
        compose.runOnIdle{assertEquals("",model.state.value.username)}
        compose.onNodeWithContentDescription("关闭个人设置").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
    }
    @Test fun guestAccountSettingsKeepLocalRepositoriesWithoutShowingAuthorizationOrLogout() {
        compose.runOnIdle{model.logout();model.navigate(Destination.PROFILE)}
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("仓库与账号").performClick()
        compose.onNodeWithText("已添加的仓库").assertIsDisplayed()
        compose.onNodeWithText("尚未连接 GitHub").assertIsDisplayed()
        compose.onNodeWithText("仓库授权").assertDoesNotExist()
        compose.onNodeWithText("退出登录").assertDoesNotExist()
        compose.onNodeWithText("已添加的仓库").performClick()
        compose.onNodeWithText("还没有手动添加的仓库。").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭个人设置").assertDoesNotExist()
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
        compose.runOnIdle{model.back()}
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
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索收藏").performClick()
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
    @Test fun switchingToEmptyBookmarksNeverKeepsStarRows() {
        compose.runOnIdle{model.openSaved(false)};awaitLoad()
        compose.onNodeWithText("star-0").assertIsDisplayed()
        compose.onNodeWithText("文档书签 0").performClick().assertIsSelected()
        assertTrue(model.state.value.savedBookmarks)
        compose.onNodeWithText("star-0").assertDoesNotExist()
        compose.onNodeWithText("留一枚书签。").assertIsDisplayed()
        snapshot("saved-empty-bookmarks")
        compose.onNodeWithText("仓库 Star").performClick();awaitLoad()
        compose.onNodeWithText("star-0").assertIsDisplayed()
        compose.onNodeWithText("留一枚书签。").assertDoesNotExist()
    }
    @Test fun libraryViewChoiceSurvivesBottomNavigation() {
        open("alpha");compose.runOnIdle{model.back()}
        compose.onNodeWithContentDescription("封面视图").performClick()
        compose.onNodeWithContentDescription("列表视图").assertIsDisplayed()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithContentDescription("列表视图").assertIsDisplayed()
    }
    @Test fun libraryViewChoiceSurvivesViewModelRestartAndIsolatedFromGuest() {
        compose.runOnIdle{model.setLibraryGrid(true)}
        assertTrue(GroveViewModel(app).state.value.libraryGrid)
        compose.runOnIdle{model.logout()}
        assertFalse(model.state.value.libraryGrid)
        assertTrue(ReadingStore(app,"100").libraryGrid)
    }
    @Test fun recentProjectsCanPinAndUnpinInBothViews() {
        open("beta");open("alpha");compose.runOnIdle{model.back()}
        compose.onNodeWithContentDescription("项目操作 owner/beta").performClick()
        compose.onNodeWithText("置顶项目").performClick()
        assertTrue(ReadingStore(app,"100").recentVisits().first{it.repo.name=="beta"}.pinned)
        assertTrue(compose.onNodeWithText("beta").fetchSemanticsNode().boundsInRoot.top<compose.onNodeWithText("alpha").fetchSemanticsNode().boundsInRoot.top)
        snapshot("recent-pinned")
        compose.onNodeWithContentDescription("封面视图").performClick()
        compose.onNodeWithContentDescription("项目操作 owner/beta").performClick()
        compose.onNodeWithText("取消置顶").performClick()
        assertFalse(ReadingStore(app,"100").recentVisits().first{it.repo.name=="beta"}.pinned)
        snapshot("recent-cards")
    }
    @Test fun discoverySearchUsesGitHubAndKeepsFiltersThenClearsOnClose() {
        compose.runOnIdle{model.navigate(Destination.DISCOVER)};awaitLoad()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索发现").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("small reader")
        compose.waitUntil(15_000){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));!model.state.value.loading}
        assertTrue(discoveryQueries.last().contains("small reader"))
        assertFalse(discoveryQueries.last().contains("stars:>10"))
        compose.onNodeWithText("AI").performClick();awaitLoad()
        assertTrue(discoveryQueries.last().contains("topic:artificial-intelligence"))
        assertTrue(discoveryQueries.last().contains("small reader"))
        snapshot("discovery-search")
        compose.onNodeWithContentDescription("关闭发现搜索").performClick();awaitLoad()
        assertEquals("",model.state.value.discoveryQuery)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertTrue(discoveryQueries.last().contains("stars:>10"))
    }
    @Test fun bottomTabsRestoreProjectsAndReselectingFootprintsReturnsHome() {
        open("alpha")
        compose.onNodeWithText("我的").performClick()
        open("beta")
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithText("alpha").assertIsDisplayed()
        assertEquals("alpha",model.state.value.project!!.id.name)
        compose.onNodeWithText("足迹").performClick()
        compose.onNodeWithText("访问记录").assertIsDisplayed()
        assertNull(model.state.value.project)
        snapshot("footprint-navigation")
        compose.onNodeWithText("我的").performClick()
        assertEquals("beta",model.state.value.project!!.id.name)
    }
    @Test fun sourceScrollAndModeSurviveBottomNavigation() {
        longReadme=true;open("alpha")
        compose.onNodeWithText("源码").performClick()
        val scroll=compose.onNode(hasScrollAction())
        scroll.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,500f)}
        val before=scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before>0f)
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("足迹").performClick()
        assertTrue(model.state.value.raw)
        assertEquals(before,compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(),.001f)
    }
}
