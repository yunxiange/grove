package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.RepoId
import dev.grove.reader.domain.PreviewKind
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class ImageFileTest {
    @Test fun repositoryPngOpensAsImageInsteadOfBinaryError() {
        val content = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4l8AAAAASUVORK5CYII="
        val json = JSONObject().put("path", "docs/example.png").put("size", 68)
            .put("encoding", "base64").put("content", content)
        val client = GitHubClient(File(System.getProperty("java.io.tmpdir"), "grove-image-test"))
        val decode = GitHubClient::class.java.getDeclaredMethod("decodeDocument", RepoId::class.java, String::class.java, JSONObject::class.java)
            .apply { isAccessible = true }
        val document = try {
            decode.invoke(client, RepoId("owner", "repo"), "main", json) as Document
        } catch (error: InvocationTargetException) {
            fail("点击仓库 PNG 图片不应出现二进制错误：${error.targetException.message}")
            return
        }
        assertEquals("docs/example.png", document.path)
        assertTrue("图片不应按 UTF-8 源码解码", document.content.isEmpty())
        assertEquals(PreviewKind.IMAGE,document.attachment?.kind)
        assertEquals("image/png",document.attachment?.mimeType)
        assertTrue(File(document.attachment!!.localPath).exists())
    }
}
