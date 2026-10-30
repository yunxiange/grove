package dev.grove.reader

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.GitHubClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class StarSearchTest {
    private lateinit var model:GroveViewModel
    private val pages=mutableListOf<Int>()
    private var failPage2=false
    @Before fun setup() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("grove.account",0).edit().clear().putString("accountId","100").putString("login","reader").commit()
        app.getSharedPreferences("grove.reading.user.100",0).edit().clear().commit()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("star-search-test"),connectionFactory={url->
            val params=url.query.orEmpty().split('&').associate{it.substringBefore('=') to it.substringAfter('=')}
            val page=params.getValue("page").toInt()
            assertEquals("100",params["per_page"])
            synchronized(pages){pages.add(page)}
            val projects=(0 until 205).drop((page-1)*100).take(100).map{index->
                JSONObject().put("owner",JSONObject().put("login","owner"))
                    .put("name",if(index==204)"needle-final"else"project-$index").put("description","description-$index")
            }
            val body=JSONArray(projects).toString().toByteArray()
            val status=if(page==2&&failPage2)500 else 200
            object:HttpURLConnection(url) {
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=status
                override fun getInputStream()=body.inputStream()
            }
        })
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
    }
    private fun finish() {
        val end=System.nanoTime()+10_000_000_000L
        do {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            if(!model.state.value.starSearchBusy)return
            Thread.sleep(2)
        }while(System.nanoTime()<end)
        fail("全部 Star 搜索未完成")
    }
    @Test fun searchFindsRepositoryOnLastPageWithoutAnyPreviouslyLoadedStars() {
        assertTrue(model.state.value.stars.isEmpty())
        model.searchStars("needle");finish()
        val s=model.state.value
        assertTrue(s.starSearchComplete);assertNull(s.starSearchError)
        assertEquals(205,s.starSearchScanned)
        assertEquals(listOf("needle-final"),s.starSearchResults.map{it.id.name})
        assertEquals(listOf(1,2,3),pages.toList())
    }
    @Test fun changingQueryReusesCompleteIndexAndMatchesOwnerAndDescription() {
        model.searchStars("needle");finish()
        model.searchStars("owner")
        assertEquals(205,model.state.value.starSearchResults.size)
        model.searchStars("DESCRIPTION-204")
        assertEquals("needle-final",model.state.value.starSearchResults.single().id.name)
        assertEquals(listOf(1,2,3),pages.toList())
    }
    @Test fun failedPageCannotPretendPartialResultsAreCompleteAndCanResume() {
        failPage2=true;model.searchStars("needle");finish()
        assertFalse(model.state.value.starSearchComplete)
        assertNotNull(model.state.value.starSearchError)
        assertEquals(100,model.state.value.starSearchScanned)
        failPage2=false;model.searchStars("needle");finish()
        assertTrue(model.state.value.starSearchComplete)
        assertEquals("needle-final",model.state.value.starSearchResults.single().id.name)
        assertEquals(listOf(1,2,2,3),pages.toList())
    }
    @Test fun loggingOutClearsIndexAndGuestCannotReadPreviousAccountSearch() {
        model.searchStars("needle");finish()
        model.logout()
        assertEquals("",model.state.value.starSearchQuery)
        assertTrue(model.state.value.starSearchResults.isEmpty())
        model.searchStars("owner")
        assertTrue(model.state.value.starSearchResults.isEmpty())
        assertFalse(model.state.value.starSearchComplete)
        assertEquals(listOf(1,2,3),pages.toList())
    }
}
