package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.GitHubException
import dev.grove.reader.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.Base64

class ImageDownloadTest {
    private val png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4l8AAAAASUVORK5CYII=")
    private val doc=Document(RepoId("owner","private"),"feature/docs","README.md","")
    private class Response(url:URL,val bytes:ByteArray):HttpURLConnection(url) {
        var beforeResponse:()->Unit={}
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getResponseCode():Int {beforeResponse();return 200}
        override fun getInputStream()=bytes.inputStream()
    }
    @Test fun privateImageSaveUsesAuthorizedApiWhileExternalImageDoesNot()=runBlocking {
        val root=Files.createTempDirectory("grove-image-download").toFile()
        val responses=mutableListOf<Response>()
        val api=GitHubClient(root,token={"test-token"},connectionFactory={Response(it,png).also(responses::add)})
        try {
            val image=api.imageForDownload(doc,doc.rawRoot+"assets/a%20b.png")
            assertArrayEquals(png,image.bytes);assertEquals("a b.png",image.fileName)
            assertEquals("api.github.com",responses.last().url.host)
            assertEquals("Bearer test-token",responses.last().getRequestProperty("Authorization"))
            api.imageForDownload(doc,"https://example.com/photo.jpg")
            assertNull(responses.last().getRequestProperty("Authorization"))
            assertEquals("image/png",image.mimeType)
        }finally{root.deleteRecursively()}
    }
    @Test fun previewDownloadsOriginalCachedBytesWithoutNetwork()=runBlocking {
        val root=Files.createTempDirectory("grove-save-cached").toFile()
        val file=root.resolve("cached").apply{writeBytes(png)}
        val preview=doc.copy(path="assets/picture.png",attachment=Attachment(PreviewKind.IMAGE,"image/png",file.absolutePath))
        val api=GitHubClient(root,connectionFactory={error("原图预览应使用已下载的图片")})
        try {
            val image=api.imageForDownload(preview,"https://appassets.androidplatform.net/attachment/cached")
            assertArrayEquals(png,image.bytes);assertEquals("picture.png",image.fileName)
        }finally{root.deleteRecursively()}
    }
    @Test fun embeddedImagesAreSupportedAndFileExtensionsFollowActualContent()=runBlocking {
        val root=Files.createTempDirectory("grove-save-data").toFile()
        val api=GitHubClient(root,connectionFactory={error("内嵌图片不应访问网络")})
        try {
            val image=api.imageForDownload(doc,"data:image/png;base64,"+Base64.getEncoder().encodeToString(png))
            assertArrayEquals(png,image.bytes);assertEquals("image.png",image.fileName)
            val svg=api.imageForDownload(doc,"data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%3E%3C/svg%3E")
            assertEquals("image/svg+xml",svg.mimeType)
            assertEquals("image.svg",svg.fileName)
        }finally{root.deleteRecursively()}
    }
    @Test fun invalidResourcesCannotBeSavedAsImages()=runBlocking {
        val root=Files.createTempDirectory("grove-save-invalid").toFile()
        val api=GitHubClient(root,connectionFactory={Response(it,"<html>login</html>".toByteArray())})
        try {
            for(url in listOf("file:///private/data.png","https://appassets.androidplatform.net/attachment/unknown","https://example.com/image.png")) {
                try{api.imageForDownload(doc,url);fail("不应保存无效图片：$url")}catch(_:GitHubException){}
            }
            assertEquals("bad_name.png",imageDownloadName("/bad:name.jpeg","image/png"))
        }finally{root.deleteRecursively()}
    }
    @Test fun switchingAccountsCancelsImageDownloadBeforeExport()=runBlocking {
        val root=Files.createTempDirectory("grove-save-account").toFile()
        var account="first"
        val api=GitHubClient(root,account={account},connectionFactory={Response(it,png).apply{beforeResponse={account="second"}}})
        try {
            try{api.imageForDownload(doc,"https://example.com/image.png");fail("账号切换后应取消图片导出")}
            catch(_:CancellationException){}
        }finally{root.deleteRecursively()}
    }
}
