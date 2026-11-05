package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.GitHubException
import dev.grove.reader.data.DownloadPausedException
import dev.grove.reader.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.security.MessageDigest

class ResumableDownloadTest {
    private fun blobSha(bytes:ByteArray)=MessageDigest.getInstance("SHA-1").apply{
        update("blob ${bytes.size}\u0000".toByteArray());update(bytes)
    }.digest().joinToString(""){"%02x".format(it)}
    private fun exercise(ignoreRange:Boolean=false,changedRevision:Boolean=false,badRange:Boolean=false,downloadUrl:String?=null,extension:String="pdf") = runBlocking {
        val root=Files.createTempDirectory("grove-resume").toFile()
        try {
            val original=if(extension=="pdf")PdfFixture.write(root.resolve("source.pdf"),200_000).readBytes()else
                listOf(File("../reader/fixtures/mobi/alice.$extension"),File("reader/fixtures/mobi/alice.$extension")).first{it.isFile}.readBytes()
            val replacement=PdfFixture.write(root.resolve("replacement.pdf"),210_000).readBytes()
            var version=original;var rawCalls=0;val ranges=mutableListOf<String?>();val cutoff=65536
            val api=GitHubClient(root.resolve("cache"),token={"test"},connectionFactory={url->object:HttpURLConnection(url){
                val raw get()=getRequestProperty("Accept")=="application/vnd.github.raw+json"
                var number=0
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode():Int {
                    if(!raw)return 200
                    number=++rawCalls;ranges.add(getRequestProperty("Range"))
                    return if(number>1&&!ignoreRange&&getRequestProperty("Range")!=null)206 else 200
                }
                private fun offset()=if(number>1&&!ignoreRange&&getRequestProperty("Range")!=null)cutoff else 0
                override fun getContentLengthLong()=(version.size-offset()).toLong()
                override fun getHeaderField(name:String):String?=if(name.equals("Content-Range",true)&&number>1&&offset()>0)
                    "bytes ${if(badRange)cutoff+1 else cutoff}-${version.lastIndex}/${version.size}" else null
                override fun getInputStream():InputStream {
                    if(!raw)return JSONObject().put("path","guide.$extension").put("size",version.size).put("sha",blobSha(version)).put("encoding","none").put("download_url",downloadUrl).toString().byteInputStream()
                    assertEquals("api.github.com",url.host)
                    if(number==1)return object:InputStream(){
                        var position=0
                        override fun read():Int=throw UnsupportedOperationException()
                        override fun read(buffer:ByteArray,start:Int,length:Int):Int {
                            if(position>=cutoff)throw IOException("test network interruption")
                            val n=minOf(length,cutoff-position);version.copyInto(buffer,start,position,position+n);position+=n;return n
                        }
                    }
                    return version.inputStream().apply{skip(offset().toLong())}
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","guide.$extension");fail("第一次下载应失败")}
            catch(_:GitHubException){}
            assertEquals(cutoff.toLong(),root.resolve("cache").walkTopDown().single{it.extension=="part"}.length())
            if(changedRevision)version=replacement
            val progress=mutableListOf<FileDownloadProgress>()
            val doc=api.document(RepoId("owner","repo"),"main","guide.$extension"){progress.add(it)}
            assertArrayEquals(version,File(doc.attachment!!.localPath).readBytes())
            assertEquals(if(changedRevision)null else "bytes=$cutoff-",ranges[1])
            if(!ignoreRange&&!changedRevision&&!badRange)assertEquals(cutoff.toLong(),progress.first().downloadedBytes)
            assertFalse(root.resolve("cache").walkTopDown().any{it.extension=="part"})
        }finally{root.deleteRecursively()}
    }
    @Test fun retryResumesTheSameRevisionFromItsSavedOffset()=exercise()
    @Test fun mobiAndAzw3RetryResumeTheSameRevisionAndValidateTheFullBlob() {
        for(extension in listOf("mobi","azw3"))exercise(extension=extension)
    }
    @Test fun ignoredRangesRestartWithoutAppendingDuplicateBytes()=exercise(ignoreRange=true)
    @Test fun changedFileRevisionsNeverReuseOldPartialBytes()=exercise(changedRevision=true)
    @Test fun invalidContentRangesRestartFromZero()=exercise(badRange=true)
    @Test fun untrustedRawUrlsNeverReceiveRequestsOrCredentials() {
        listOf("https://raw.githubusercontent.com.attacker.invalid/file.pdf","http://raw.githubusercontent.com/owner/repo/file.pdf",
            "https://user@raw.githubusercontent.com/owner/repo/file.pdf").forEach{exercise(downloadUrl=it)}
    }
    @Test fun retryUsesFreshRawDownloadUrlAndResumesAfterClientRecreation()=exerciseRaw()
    @Test fun anExpiredRawDownloadTokenKeepsPartialBytesForTheNextRetry()=exerciseRaw(expiredToken=true)
    private fun exerciseRaw(expiredToken:Boolean=false)=runBlocking {
        val root=Files.createTempDirectory("grove-raw-resume").toFile()
        try {
            val bytes=PdfFixture.write(root.resolve("source.pdf"),200_000).readBytes()
            val cutoff=65536
            var metadataCalls=0;var transfers=0
            val urls=mutableListOf<String>();val ranges=mutableListOf<String?>()
            fun client()=GitHubClient(root.resolve("cache"),token={"private-token"},connectionFactory={url->object:HttpURLConnection(url){
                val metadata get()=getRequestProperty("Accept")=="application/vnd.github.object+json"
                var offset=0
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode():Int {
                    if(metadata){metadataCalls++;return 200}
                    transfers++;urls.add(url.toString());ranges.add(getRequestProperty("Range"))
                    if(expiredToken&&transfers==2)return 403
                    if(url.host=="raw.githubusercontent.com") {
                        assertNull("GitHub bearer tokens must stay on the API host",getRequestProperty("Authorization"))
                        offset=getRequestProperty("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toInt()?:0
                    }
                    return if(offset>0)206 else 200
                }
                override fun getContentLengthLong()=(bytes.size-offset).toLong()
                override fun getHeaderField(name:String):String?=if(name.equals("Content-Range",true)&&offset>0)
                    "bytes $offset-${bytes.lastIndex}/${bytes.size}"else null
                override fun getInputStream():InputStream {
                    if(metadata)return JSONObject().put("path","guide.pdf").put("size",bytes.size)
                        .put("sha",blobSha(bytes)).put("encoding","none")
                        .put("download_url","https://raw.githubusercontent.com/owner/repo/main/guide.pdf?token=fresh-$metadataCalls")
                        .toString().byteInputStream()
                    if(transfers==1)return object:InputStream(){
                        var position=0
                        override fun read():Int=throw UnsupportedOperationException()
                        override fun read(buffer:ByteArray,start:Int,length:Int):Int {
                            if(position>=cutoff)throw IOException("connection lost while screen is off")
                            val n=minOf(length,cutoff-position);bytes.copyInto(buffer,start,position,position+n);position+=n;return n
                        }
                    }
                    return bytes.inputStream().apply{skip(offset.toLong())}
                }
            }})
            try{client().document(RepoId("owner","repo"),"main","guide.pdf");fail("transfer must fail")}
            catch(_:GitHubException){}
            if(expiredToken) {
                try{client().document(RepoId("owner","repo"),"main","guide.pdf");fail("expired download token must fail")}
                catch(_:GitHubException){}
                assertEquals(cutoff.toLong(),root.resolve("cache").walkTopDown().single{it.extension=="part"}.length())
            }
            val progress=mutableListOf<FileDownloadProgress>()
            val doc=client().document(RepoId("owner","repo"),"main","guide.pdf"){progress.add(it)}
            assertEquals("https://raw.githubusercontent.com/owner/repo/main/guide.pdf?token=fresh-${if(expiredToken)3 else 2}",urls.last())
            assertEquals("bytes=$cutoff-",ranges.last())
            assertEquals(cutoff.toLong(),progress.first().downloadedBytes)
            assertArrayEquals(bytes,File(doc.attachment!!.localPath).readBytes())
        }finally{root.deleteRecursively()}
    }
    @Test fun systemPauseDuringASocketFailureKeepsPartialBytesAndRetryResumes()=exerciseCancellation(systemPause=true)
    @Test fun aFullyDownloadedPartialIsVerifiedLocallyWithoutDownloadingAgain()=exerciseCancellation(systemPause=true,completeBeforePause=true)
    @Test fun explicitCancellationDiscardsPartialBytes()=exerciseCancellation(systemPause=false)
    private fun exerciseCancellation(systemPause:Boolean,completeBeforePause:Boolean=false)=runBlocking {
        val root=Files.createTempDirectory("grove-paused-resume").toFile()
        try {
            val bytes=PdfFixture.write(root.resolve("source.pdf"),200_000).readBytes()
            val cutoff=if(completeBeforePause)bytes.size else 65536;var transfers=0;var range:String?=null
            lateinit var interrupted:Deferred<Document>
            val api=GitHubClient(root.resolve("cache"),token={"test"},connectionFactory={url->object:HttpURLConnection(url){
                val raw get()=getRequestProperty("Accept")=="application/vnd.github.raw+json"
                var offset=0
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode():Int {
                    if(!raw)return 200
                    transfers++;range=getRequestProperty("Range")
                    offset=range?.substringAfter("bytes=")?.substringBefore('-')?.toInt()?:0
                    return if(offset>0)206 else 200
                }
                override fun getContentLengthLong()=(bytes.size-offset).toLong()
                override fun getHeaderField(name:String):String?=if(name.equals("Content-Range",true)&&offset>0)
                    "bytes $offset-${bytes.lastIndex}/${bytes.size}"else null
                override fun getInputStream():InputStream {
                    if(!raw)return JSONObject().put("path","guide.pdf").put("size",bytes.size).put("sha",blobSha(bytes)).put("encoding","none").toString().byteInputStream()
                    if(transfers==1)return object:InputStream(){
                        var position=0
                        override fun read():Int=throw UnsupportedOperationException()
                        override fun read(buffer:ByteArray,start:Int,length:Int):Int {
                            if(position>=cutoff){
                                interrupted.cancel(if(systemPause)DownloadPausedException()else CancellationException("user cancelled"))
                                throw IOException("socket closed during cancellation")
                            }
                            val n=minOf(length,cutoff-position);bytes.copyInto(buffer,start,position,position+n);position+=n;return n
                        }
                    }
                    return bytes.inputStream().apply{skip(offset.toLong())}
                }
            }})
            interrupted=async{api.document(RepoId("owner","repo"),"main","guide.pdf")}
            try{interrupted.await();fail("cancelled transfer must stop") }catch(_:CancellationException){}
            val partials=root.resolve("cache").walkTopDown().filter{it.extension=="part"}.toList()
            if(systemPause)assertEquals(cutoff.toLong(),partials.single().length())else assertTrue(partials.isEmpty())
            val progress=mutableListOf<FileDownloadProgress>()
            val doc=api.document(RepoId("owner","repo"),"main","guide.pdf"){progress.add(it)}
            if(completeBeforePause)assertEquals("completed bytes should not be downloaded again",1,transfers)
            else assertEquals(if(systemPause)"bytes=$cutoff-"else null,range)
            assertEquals(if(systemPause)cutoff.toLong()else 0L,progress.first().downloadedBytes)
            assertArrayEquals(bytes,File(doc.attachment!!.localPath).readBytes())
        }finally{root.deleteRecursively()}
    }
    @Test fun aBlobChecksumMismatchIsNotCommittedOrKeptForResume()=runBlocking {
        val root=Files.createTempDirectory("grove-checksum").toFile()
        try {
            val source=PdfFixture.write(root.resolve("source.pdf")).readBytes()
            val api=GitHubClient(root.resolve("cache"),token={"test"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=source.size.toLong()
                override fun getInputStream():InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return source.inputStream()
                    return JSONObject().put("path","guide.pdf").put("size",source.size).put("sha","0".repeat(40)).put("encoding","none").toString().byteInputStream()
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","guide.pdf");fail("校验失败的文件不应提交")}
            catch(e:GitHubException){assertTrue(e.message!!.contains("下载校验失败"))}
            assertFalse(root.resolve("cache").walkTopDown().any{it.isFile})
        }finally{root.deleteRecursively()}
    }
}
