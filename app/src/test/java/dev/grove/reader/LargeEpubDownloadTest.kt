package dev.grove.reader

import dev.grove.reader.data.EpubArchive
import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.GitHubException
import dev.grove.reader.domain.PreviewKind
import dev.grove.reader.domain.RepoId
import dev.grove.reader.domain.FileDownloadProgress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LargeEpubDownloadTest {
    private fun largeBook(root:File,paddingLength:Int=7_000_000):File=root.resolve("source.epub").also{file->
        ZipOutputStream(file.outputStream()).use{zip->
            for((name,value) in EpubFixture.entries()){
                val bytes=value.toByteArray();zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
            }
            val buffer=ByteArray(8192);val length=paddingLength
            val checksum=CRC32().apply{var left=length;while(left>0){val n=minOf(left,buffer.size);update(buffer,0,n);left-=n}}.value
            repeat(3){index->
                zip.putNextEntry(ZipEntry("OPS/media/padding-$index.bin").apply{method=ZipEntry.STORED;size=length.toLong();compressedSize=size;crc=checksum})
                var left=length;while(left>0){val n=minOf(left,buffer.size);zip.write(buffer,0,n);left-=n};zip.closeEntry()
            }
        }
    }
    @Test fun epubOverTwentyMbStreamsToAnAccountCacheAndReusesItsRevision()=runBlocking {
        val root=Files.createTempDirectory("grove-large-epub").toFile()
        try{
            val book=largeBook(root);assertTrue(book.length()>20_000_000)
            var rawRequests=0
            val progress=mutableListOf<FileDownloadProgress>()
            val api=GitHubClient(root.resolve("cache"),token={"test-token"},account={"100"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit
                override fun disconnect()=Unit
                override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=if(getRequestProperty("Accept")=="application/vnd.github.raw+json")book.length() else -1
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json") {
                        assertEquals("Bearer test-token",getRequestProperty("Authorization"));assertFalse(instanceFollowRedirects);rawRequests++
                        return object:FilterInputStream(book.inputStream()) {
                            private var first=true
                            override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                                assertTrue(length<=65536)
                                if(first){first=false;Thread.sleep(110)}
                                return super.read(bytes,offset,length)
                            }
                        }
                    }
                    assertEquals("application/vnd.github.object+json",getRequestProperty("Accept"))
                    return JSONObject().put("size",book.length()).put("path","books/large.epub").put("sha","revision-1").put("encoding","none").toString().byteInputStream()
                }
            }})
            val doc=api.document(RepoId("owner","repo"),"main","books/large.epub"){progress.add(it)}
            assertEquals(PreviewKind.EPUB,doc.attachment!!.kind)
            val cached=File(doc.attachment.localPath);assertEquals(book.length(),cached.length())
            assertEquals("纸上漫游",EpubArchive.open(cached).title)
            assertEquals(0L,progress.first().downloadedBytes)
            assertEquals(book.length(),progress.last().downloadedBytes)
            assertTrue(progress.all{it.totalBytes==book.length()})
            assertTrue(progress.zipWithNext().all{(before,after)->before.downloadedBytes<=after.downloadedBytes})
            assertTrue("下载过程中应有中间进度",progress.any{it.downloadedBytes in 1 until book.length()})
            assertEquals(1f,progress.last().fraction!!,0f)
            assertEquals(doc.attachment.localPath,api.document(doc.repo,doc.ref,doc.path).attachment!!.localPath)
            assertEquals(1,rawRequests)
            assertFalse(root.resolve("cache").walkTopDown().any{it.extension=="part"})
        }finally{root.deleteRecursively()}
    }
    @Test fun unknownLengthShowsDownloadedBytesWithoutInventingAPercentage()=runBlocking {
        val root=Files.createTempDirectory("grove-epub-unknown-length").toFile()
        try{
            val book=EpubFixture.bytes();val progress=mutableListOf<FileDownloadProgress>()
            val api=GitHubClient(root,token={"test"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=-1L
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return book.inputStream()
                    return JSONObject().put("encoding","none").toString().byteInputStream()
                }
            }})
            val doc=api.document(RepoId("owner","repo"),"main","book.epub"){progress.add(it)}
            assertEquals(book.size.toLong(),File(doc.attachment!!.localPath).length())
            assertEquals(0L,progress.first().downloadedBytes)
            assertEquals(book.size.toLong(),progress.last().downloadedBytes)
            assertTrue(progress.all{it.totalBytes==null&&it.fraction==null})
        }finally{root.deleteRecursively()}
    }
    @Test fun githubSizeRejectionIsDistinguishedFromPermissionFailures()=runBlocking {
        val root=Files.createTempDirectory("grove-epub-server-limit").toFile()
        try{
            for((message,sizeError) in listOf("This API returns blobs up to 100 MB in size" to true,"Resource not accessible by integration" to false)) {
                val api=GitHubClient(root,token={"test"},connectionFactory={url->object:HttpURLConnection(url){
                    override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                    override fun getResponseCode()=403
                    override fun getErrorStream()=JSONObject().put("message",message).toString().byteInputStream()
                }})
                try{api.document(RepoId("owner","repo"),"main","book.epub");fail("服务端拒绝时应显示错误")}
                catch(e:GitHubException){assertEquals(sizeError,e.message!!.contains("GitHub 服务端"))}
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun epubOverOneHundredMbDownloadsAndOpensWithoutAnAppSizeCap()=runBlocking {
        val root=Files.createTempDirectory("grove-epub-limit").toFile()
        try{
            val book=largeBook(root,35_000_000);assertTrue(book.length()>100_000_000)
            val api=GitHubClient(root,connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=book.length()
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return book.inputStream()
                    return JSONObject().put("size",book.length()).put("path","large.epub").put("encoding","none").toString().byteInputStream()
                }
            }})
            val doc=api.document(RepoId("owner","repo"),"main","large.epub")
            val cached=File(doc.attachment!!.localPath)
            assertEquals(book.length(),cached.length())
            assertEquals("纸上漫游",EpubArchive.open(cached).title)
        }finally{root.deleteRecursively()}
    }
    @Test fun accountSwitchDuringStreamingRemovesPartialFile()=runBlocking {
        val root=Files.createTempDirectory("grove-epub-cancel").toFile();var account="100"
        try{
            val api=GitHubClient(root,token={"test-token"},account={account},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return object:FilterInputStream(byteArrayOf(80,75,3,4,0,0).inputStream()) {
                        override fun read(bytes:ByteArray,offset:Int,length:Int):Int {account="200";return super.read(bytes,offset,length)}
                    }
                    return JSONObject().put("size",21_000_000).put("encoding","none").toString().byteInputStream()
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","large.epub");fail("切换账号后不应返回旧账号附件")}
            catch(_:kotlinx.coroutines.CancellationException){}
            assertFalse(root.walkTopDown().any{it.isFile})
        }finally{root.deleteRecursively()}
    }
    @Test fun interruptedDownloadDoesNotLeaveAnIncompleteBookInCache()=runBlocking {
        val root=Files.createTempDirectory("grove-epub-interrupted").toFile()
        try{
            val api=GitHubClient(root,token={"test-token"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=21_000_000L
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return byteArrayOf(80,75,3,4).inputStream()
                    return JSONObject().put("size",21_000_000).put("encoding","none").toString().byteInputStream()
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","large.epub");fail("未完成的下载不能成为可用附件")}
            catch(e:GitHubException){assertTrue(e.message!!.contains("下载未完成"))}
            assertFalse(root.walkTopDown().any{it.isFile})
        }finally{root.deleteRecursively()}
    }
}
