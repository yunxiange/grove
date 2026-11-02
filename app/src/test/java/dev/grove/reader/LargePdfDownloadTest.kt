package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.GitHubException
import dev.grove.reader.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FilterInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.nio.file.Files

object PdfFixture {
    fun write(file:File,padding:Int=0):File=file.also {
        RandomAccessFile(file,"rw").use{out->
            out.setLength(0)
            fun text(value:String){out.write(value.toByteArray(Charsets.US_ASCII))}
            val offsets=mutableListOf(0L)
            fun obj(number:Int,value:String){offsets.add(out.filePointer);text("$number 0 obj\n$value\nendobj\n")}
            text("%PDF-1.4\n")
            obj(1,"<< /Type /Catalog /Pages 2 0 R >>")
            obj(2,"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            obj(3,"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << >> /Contents 4 0 R >>")
            val drawing="0 0 0 rg 20 20 100 100 re f\n"
            offsets.add(out.filePointer);text("4 0 obj\n<< /Length ${drawing.length.toLong()+padding} >>\nstream\n$drawing")
            val buffer=ByteArray(65536){32};var remaining=padding
            while(remaining>0){val n=minOf(remaining,buffer.size);out.write(buffer,0,n);remaining-=n}
            text("\nendstream\nendobj\n")
            val xref=out.filePointer
            text("xref\n0 5\n0000000000 65535 f \n")
            for(offset in offsets.drop(1))text("%010d 00000 n \n".format(java.util.Locale.ROOT,offset))
            text("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }
}

class LargePdfDownloadTest {
    @Test fun pdfOverTwentyAndOneHundredMbStreamsWithProgressAndReusesItsRevision()=runBlocking {
        val root=Files.createTempDirectory("grove-large-pdf").toFile()
        try {
            for(padding in listOf(21_000_000,105_000_000)) {
                val source=PdfFixture.write(root.resolve("source.pdf"),padding)
                assertTrue(source.length()>padding)
                val progress=mutableListOf<FileDownloadProgress>();var downloads=0
                val api=GitHubClient(root.resolve("cache"),token={"test-token"},account={"100"},connectionFactory={url->object:HttpURLConnection(url){
                    override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                    override fun getResponseCode()=200
                    override fun getContentLengthLong()=source.length()
                    override fun getInputStream():java.io.InputStream {
                        assertEquals("Bearer test-token",getRequestProperty("Authorization"));assertFalse(instanceFollowRedirects)
                        if(getRequestProperty("Accept")=="application/vnd.github.raw+json") {
                            downloads++
                            return object:FilterInputStream(source.inputStream()) {
                                private var first=true
                                override fun read(bytes:ByteArray,offset:Int,length:Int):Int {
                                    assertTrue(length<=65536)
                                    if(first){first=false;Thread.sleep(110)}
                                    return super.read(bytes,offset,length)
                                }
                            }
                        }
                        assertEquals("application/vnd.github.object+json",getRequestProperty("Accept"))
                        return JSONObject().put("path","guide.pdf").put("size",source.length()).put("encoding","none").put("sha","revision-$padding").toString().byteInputStream()
                    }
                }})
                val doc=api.document(RepoId("owner","private-repo"),"main","guide.pdf"){progress.add(it)}
                assertEquals(PreviewKind.PDF,doc.attachment!!.kind);assertEquals("application/pdf",doc.attachment.mimeType)
                assertEquals(source.length(),File(doc.attachment.localPath).length())
                assertEquals(0L,progress.first().downloadedBytes);assertEquals(source.length(),progress.last().downloadedBytes)
                assertTrue(progress.all{it.totalBytes==source.length()})
                assertTrue(progress.all{it.kind==PreviewKind.PDF})
                assertTrue(progress.any{it.downloadedBytes in 1 until source.length()})
                assertTrue(progress.zipWithNext().all{(a,b)->a.downloadedBytes<=b.downloadedBytes})
                assertEquals(doc.attachment.localPath,api.document(doc.repo,doc.ref,doc.path).attachment!!.localPath)
                assertEquals(1,downloads)
                assertFalse(root.resolve("cache").walkTopDown().any{it.extension=="part"})
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun unknownLengthPdfReportsBytesWithoutAPercentage()=runBlocking {
        val root=Files.createTempDirectory("grove-pdf-unknown-size").toFile()
        try {
            val source=PdfFixture.write(root.resolve("source.pdf"));val progress=mutableListOf<FileDownloadProgress>()
            val api=GitHubClient(root.resolve("cache"),token={"test-token"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=-1L
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return source.inputStream()
                    return JSONObject().put("encoding","none").toString().byteInputStream()
                }
            }})
            val doc=api.document(RepoId("owner","repo"),"main","guide.pdf"){progress.add(it)}
            assertEquals(source.length(),File(doc.attachment!!.localPath).length())
            assertEquals(0L,progress.first().downloadedBytes);assertEquals(source.length(),progress.last().downloadedBytes)
            assertTrue(progress.all{it.kind==PreviewKind.PDF&&it.totalBytes==null&&it.fraction==null})
        }finally{root.deleteRecursively()}
    }
    @Test fun invalidPdfSignatureIsNotCommittedAsAnAttachment()=runBlocking {
        val root=Files.createTempDirectory("grove-pdf-invalid-download").toFile()
        try {
            val api=GitHubClient(root,token={"test-token"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=-1L
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return "<html>sign in</html>".byteInputStream()
                    return JSONObject().put("encoding","none").toString().byteInputStream()
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","guide.pdf");fail("HTML 不应成为 PDF 缓存")}
            catch(e:GitHubException){assertTrue(e.message!!.contains("不是有效的 PDF"))}
            assertFalse(root.walkTopDown().any{it.isFile})
        }finally{root.deleteRecursively()}
    }
    @Test fun truncatedPdfWithOnlyAMetadataLengthDoesNotEnterTheCache()=runBlocking {
        val root=Files.createTempDirectory("grove-truncated-pdf").toFile()
        try {
            val api=GitHubClient(root,token={"test-token"},connectionFactory={url->object:HttpURLConnection(url){
                override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getContentLengthLong()=-1L
                override fun getInputStream():java.io.InputStream {
                    if(getRequestProperty("Accept")=="application/vnd.github.raw+json")return "%PDF-1.4\n".byteInputStream()
                    return JSONObject().put("size",1_000_001).put("encoding","none").toString().byteInputStream()
                }
            }})
            try{api.document(RepoId("owner","repo"),"main","guide.pdf");fail("未完成的 PDF 不能成为可用附件")}
            catch(e:GitHubException){assertTrue(e.message!!.contains("PDF 下载未完成"))}
            assertFalse(root.walkTopDown().any{it.isFile})
        }finally{root.deleteRecursively()}
    }
}
