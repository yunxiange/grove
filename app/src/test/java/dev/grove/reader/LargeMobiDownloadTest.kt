package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.nio.file.Files

class LargeMobiDownloadTest {
    @Test fun largeMobiAndAzw3StreamWithProgressAndReuseAccountScopedCache()=runBlocking {
        val root=Files.createTempDirectory("grove-large-mobi").toFile()
        try {
            for(extension in listOf("mobi","azw3")) {
                val original=listOf(File("../reader/fixtures/mobi/alice.$extension"),File("reader/fixtures/mobi/alice.$extension")).first{it.isFile}
                val source=root.resolve("source.$extension");original.copyTo(source)
                // Extend the final resource to exercise transport independently of parsing.
                RandomAccessFile(source,"rw").use{it.setLength(21_000_000)}
                val progress=mutableListOf<FileDownloadProgress>();var downloads=0
                val api=GitHubClient(root.resolve("cache"),token={"test-token"},account={"100"},connectionFactory={url->object:HttpURLConnection(url){
                    override fun connect()=Unit;override fun disconnect()=Unit;override fun usingProxy()=false
                    override fun getResponseCode()=200
                    override fun getContentLengthLong()=source.length()
                    override fun getInputStream():java.io.InputStream {
                        assertEquals("Bearer test-token",getRequestProperty("Authorization"))
                        if(getRequestProperty("Accept")=="application/vnd.github.raw+json"){downloads++;return source.inputStream()}
                        assertEquals("application/vnd.github.object+json",getRequestProperty("Accept"))
                        return JSONObject().put("path","book.$extension").put("size",source.length()).put("sha","revision-$extension").put("encoding","none").toString().byteInputStream()
                    }
                }})
                val doc=api.document(RepoId("owner","private-repo"),"main","book.$extension"){progress.add(it)}
                assertEquals(PreviewKind.MOBI,doc.attachment!!.kind)
                assertEquals(streamedPreviewType(doc.path)?.mimeType,doc.attachment.mimeType)
                assertEquals(source.length(),File(doc.attachment.localPath).length())
                assertTrue(progress.all{it.kind==PreviewKind.MOBI&&it.totalBytes==source.length()})
                assertEquals(0L,progress.first().downloadedBytes);assertEquals(source.length(),progress.last().downloadedBytes)
                assertEquals(doc.attachment.localPath,api.document(doc.repo,doc.ref,doc.path).attachment!!.localPath)
                assertEquals(1,downloads)
            }
        }finally{root.deleteRecursively()}
    }
}
