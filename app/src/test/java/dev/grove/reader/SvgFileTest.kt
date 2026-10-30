package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.nio.file.Files
import java.util.Base64

class SvgFileTest {
    private fun api(root:File,bytes:ByteArray)=GitHubClient(root,connectionFactory={url->
        val body=JSONObject().put("path","images/drawing.svg").put("size",bytes.size)
            .put("encoding","base64").put("content",Base64.getEncoder().encodeToString(bytes)).toString().toByteArray()
        object:HttpURLConnection(url) {
            override fun connect()=Unit
            override fun disconnect()=Unit
            override fun usingProxy()=false
            override fun getResponseCode()=200
            override fun getInputStream()=body.inputStream()
        }
    })
    @Test fun svgKeepsPreviewBytesAndOriginalSourceIncludingUnicodeAndXmlEncoding()=runBlocking {
        val root=Files.createTempDirectory("grove-svg").toFile()
        val utf8="<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"><text>中文 &amp; SVG</text></svg>"
        val utf16=utf8.replace("UTF-8","UTF-16")
        try {
            for((text,bytes) in listOf("<svg/>" to "<svg/>".toByteArray(),utf8 to utf8.toByteArray(),utf16 to utf16.toByteArray(Charsets.UTF_16))) {
                val doc=api(root,bytes).document(RepoId("owner","repo"),"main","images/drawing.svg")
                assertTrue(doc.svg);assertFalse(doc.markdown);assertFalse(doc.sourceTooLarge)
                assertEquals(text,doc.content)
                assertArrayEquals(bytes,File(doc.attachment!!.localPath).readBytes())
            }
        }finally{root.deleteRecursively()}
    }
    @Test fun largeSvgRetainsPreviewAndDownloadWithoutLoadingHugeTextIntoSourceView()=runBlocking {
        val root=Files.createTempDirectory("grove-large-svg").toFile()
        val bytes=("<svg xmlns=\"http://www.w3.org/2000/svg\"><!--"+"x".repeat(1_000_000)+"--></svg>").toByteArray()
        try {
            val doc=api(root,bytes).document(RepoId("owner","repo"),"main","images/drawing.svg")
            assertTrue(doc.svg);assertTrue(doc.sourceTooLarge);assertEquals("",doc.content)
            assertArrayEquals(bytes,File(doc.attachment!!.localPath).readBytes())
        }finally{root.deleteRecursively()}
    }
}
