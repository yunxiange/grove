package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.Document
import dev.grove.reader.domain.RepoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.Base64

class PrivateAccessTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4l8AAAAASUVORK5CYII=")

    private class Response(url: URL, private val bytes: ByteArray) : HttpURLConnection(url) {
        var beforeResponse: () -> Unit = {}
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode(): Int { beforeResponse(); return 200 }
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }

    @Test fun authenticatedRepositoryResponsesNeverBecomeAnonymousOfflineCache() = runBlocking {
        val root = Files.createTempDirectory("grove-private-api").toFile()
        var credential = "test-user-token"
        var online = true
        var observed: Response? = null
        val api = GitHubClient(root, token = { credential }, connectionFactory = { url ->
            if (!online) throw IOException("offline")
            Response(url, "{\"private\":true}".toByteArray()).also { observed = it }
        })
        try {
            assertTrue(api.request("/repos/owner/private").contains("true"))
            assertEquals("Bearer test-user-token", observed!!.getRequestProperty("Authorization"))
            assertFalse(observed!!.instanceFollowRedirects)
            assertTrue(root.listFiles().orEmpty().isEmpty())
            credential = ""
            online = false
            try { api.request("/repos/owner/private"); fail("访客不能读到上个账号的私有响应") }
            catch (_: IOException) { /* No cached private response was returned. */ }
        } finally { root.deleteRecursively() }
    }

    @Test fun inlinePrivateImageUsesAuthenticatedContentsEndpoint() {
        val root = Files.createTempDirectory("grove-private-image").toFile()
        var observed: Response? = null
        val api = GitHubClient(root, token = { "test-user-token" }, connectionFactory = { url ->
            Response(url, png).also { observed = it }
        })
        val doc = Document(RepoId("owner", "private"), "feature/docs", "README.md", "")
        try {
            assertEquals("image/png", api.imageResource(doc, doc.rawRoot + "assets/a%20b.png")!!.first)
            assertEquals("api.github.com", observed!!.url.host)
            assertEquals("/repos/owner/private/contents/assets/a%20b.png", observed!!.url.path)
            assertEquals("ref=feature%2Fdocs", observed!!.url.query)
            assertEquals("Bearer test-user-token", observed!!.getRequestProperty("Authorization"))
            assertNull(api.imageResource(doc, "https://example.com/image.png"))
        } finally { root.deleteRecursively() }
    }

    @Test fun externalImageNeverReceivesGithubCredentials() = runBlocking {
        val root = Files.createTempDirectory("grove-external-image").toFile()
        var observed: Response? = null
        val api = GitHubClient(root, token = { "test-user-token" }, connectionFactory = { url ->
            Response(url, png).also { observed = it }
        })
        try {
            api.remoteImage(RepoId("owner", "private"), "main", "https://example.com/image.png")
            assertNull(observed!!.getRequestProperty("Authorization"))
        } finally { root.deleteRecursively() }
    }

    @Test fun accountSwitchDuringImageDownloadCannotWriteIntoNextAccountsCache() = runBlocking {
        val root = Files.createTempDirectory("grove-image-switch").toFile()
        var account = "first"
        val api = GitHubClient(root, account = { account }, connectionFactory = { url ->
            Response(url, png).apply { beforeResponse = { account = "second" } }
        })
        try {
            try { api.remoteImage(RepoId("owner", "private"), "main", "https://example.com/image.png"); fail("切换账号应取消下载") }
            catch (_: CancellationException) { }
            assertFalse(root.resolve("attachments").exists())
        } finally { root.deleteRecursively() }
    }
}
