package dev.grove.reader

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class DocumentDownloadNavigationTest {
    private lateinit var model:GroveViewModel
    private lateinit var app:Application
    private val gates=ConcurrentHashMap<String,CountDownLatch>()
    private val calls=ConcurrentHashMap<String,AtomicInteger>()
    private val finished=ConcurrentHashMap<String,AtomicInteger>()
    private val failing=ConcurrentHashMap.newKeySet<String>()
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("grove.account",0).edit().clear().commit()
        app.getSharedPreferences("grove.reading",0).edit().clear().commit()
        app.cacheDir.listFiles().orEmpty().forEach{it.deleteRecursively()}
        val bytes=PdfFixture.write(app.cacheDir.resolve("source.pdf"),128000).readBytes()
        model=GroveViewModel(app)
        val api=GitHubClient(app.cacheDir.resolve("github"),token={"test"},connectionFactory={url->object:HttpURLConnection(url){
            val path=url.path.substringAfter("/contents/")
            val raw get()=getRequestProperty("Accept")=="application/vnd.github.raw+json"
            override fun connect()=Unit
            override fun disconnect(){if(raw)finished.getOrPut(path){AtomicInteger()}.incrementAndGet()}
            override fun usingProxy()=false
            override fun getResponseCode():Int {
                if(raw)calls.getOrPut(path){AtomicInteger()}.incrementAndGet()
                return 200
            }
            override fun getContentLengthLong()=bytes.size.toLong()
            override fun getInputStream():java.io.InputStream {
                if(raw)return object:java.io.FilterInputStream(bytes.inputStream()) {
                    override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                        check(gates.getOrPut(path){CountDownLatch(1)}.await(30,TimeUnit.SECONDS))
                        if(failing.contains(path))throw java.io.IOException("test network failure")
                        return super.read(buffer,offset,length)
                    }
                }
                return JSONObject().put("path",path).put("size",bytes.size).put("sha","revision-$path").put("encoding","none").toString().byteInputStream()
            }
        }})
        GroveViewModel::class.java.getDeclaredField("api").apply{isAccessible=true}.set(model,api)
    }
    @After fun cleanup(){gates.values.forEach{it.countDown()};model.logout()}
    private fun await(condition:()->Boolean) {
        val end=System.nanoTime()+10_000_000_000L
        do{shadowOf(Looper.getMainLooper()).idle();if(condition())return;Thread.sleep(2)}while(System.nanoTime()<end)
        fail("Document load did not reach expected state: ${model.state.value}")
    }
    private fun open(path:String) {
        gates.putIfAbsent(path,CountDownLatch(1))
        model.openBookmark(Bookmark(RepoId("owner","repo"),"main",path))
        await{model.state.value.downloadProgress!=null&&calls[path]?.get()==1}
    }
    @Test fun completingOffscreenDoesNotNavigateAndReturningUsesTheCompletedFile() {
        open("first.pdf");model.navigate(Destination.PROFILE)
        gates["first.pdf"]!!.countDown();await{finished["first.pdf"]?.get()==1}
        assertEquals(Destination.PROFILE,model.state.value.destination);assertNull(model.state.value.project)
        assertNull(model.state.value.document);assertNull(model.state.value.downloadProgress)
        model.navigate(Destination.LIBRARY);await{model.state.value.document?.path=="first.pdf"}
        assertEquals(1,calls["first.pdf"]!!.get());assertFalse(model.state.value.loading)
    }
    @Test fun returningBeforeCompletionReattachesWithoutStartingADuplicateDownload() {
        open("first.pdf")
        repeat(3){model.navigate(Destination.PROFILE);model.navigate(Destination.LIBRARY)}
        assertTrue(model.state.value.loading);assertNotNull(model.state.value.downloadProgress)
        assertEquals(1,calls["first.pdf"]!!.get())
        gates["first.pdf"]!!.countDown();await{model.state.value.document?.path=="first.pdf"}
        assertEquals(1,calls["first.pdf"]!!.get())
    }
    @Test fun documentTransferStartsForegroundServiceAndOutlivesItsActivityViewModel() {
        val models=ViewModelStore().apply{put("reader",model)}
        open("first.pdf")
        assertTrue(shadowOf(app).allStartedServices.any{it.component?.className==DocumentDownloadService::class.java.name})
        models.clear()
        gates["first.pdf"]!!.countDown();await{model.state.value.document?.path=="first.pdf"}
        assertEquals(1,calls["first.pdf"]!!.get())
    }
    @Test fun concurrentDownloadsUpdateOnlyTheirOwnTabs() {
        open("first.pdf");model.navigate(Destination.PROFILE);open("second.pdf")
        gates["first.pdf"]!!.countDown();await{finished["first.pdf"]?.get()==1}
        assertEquals("second.pdf",model.state.value.openingDocument?.path);assertNull(model.state.value.document)
        model.navigate(Destination.LIBRARY);await{model.state.value.document?.path=="first.pdf"}
        gates["second.pdf"]!!.countDown();await{finished["second.pdf"]?.get()==1}
        assertEquals("first.pdf",model.state.value.document?.path)
        model.navigate(Destination.PROFILE);await{model.state.value.document?.path=="second.pdf"}
    }
    @Test fun offscreenFailuresStayOnTheirOriginalRouteAndCanBeRetried() {
        failing.add("first.pdf");open("first.pdf");model.navigate(Destination.PROFILE)
        gates["first.pdf"]!!.countDown();await{finished["first.pdf"]?.get()==1}
        assertNull(model.state.value.error)
        model.navigate(Destination.LIBRARY);await{model.state.value.error!=null}
        assertEquals("first.pdf",model.state.value.openingDocument?.path)
        failing.clear();model.retry();await{model.state.value.document?.path=="first.pdf"}
        assertNull(model.state.value.error);assertEquals(2,calls["first.pdf"]!!.get())
    }
    @Test fun clearingCacheCancelsDetachedDownloadsAndRemovesTheirSavedRoute() {
        open("first.pdf");model.navigate(Destination.PROFILE);model.clearCache()
        gates["first.pdf"]!!.countDown();await{!model.state.value.cacheBusy}
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project);assertNull(model.state.value.openingDocument)
        assertFalse(app.cacheDir.walkTopDown().any{it.isFile})
    }
    @Test fun logoutDiscardsDetachedDownloadsWithoutRestoringOldAccountFiles() {
        open("first.pdf");model.navigate(Destination.PROFILE);model.logout()
        gates["first.pdf"]!!.countDown();await{finished["first.pdf"]?.get()==1}
        model.navigate(Destination.LIBRARY)
        assertNull(model.state.value.project);assertNull(model.state.value.document)
        assertNull(model.state.value.openingDocument)
        assertFalse(app.cacheDir.resolve("github/attachments").walkTopDown().any{it.isFile})
    }
}
