package dev.grove.reader

import dev.grove.reader.data.ContentCache
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ContentCacheTest {
    @Test fun clearOnlyDeletesTemporaryFilesAndDoesNotFollowLinks() {
        val parent=Files.createTempDirectory("grove-cache-test").toFile()
        try{
            val root=parent.resolve("cache").apply{mkdirs()}
            val preferences=parent.resolve("shared_prefs/account.xml").apply{parentFile!!.mkdirs();writeText("keep-login-and-bookmarks")}
            root.resolve("github/attachments/book.epub").apply{parentFile!!.mkdirs();writeBytes(ByteArray(1024))}
            root.resolve("response.json").writeBytes(ByteArray(50))
            Files.createSymbolicLink(root.resolve("outside").toPath(),preferences.parentFile!!.toPath())
            val cache=ContentCache(root)
            assertEquals(1074L,cache.size());cache.clear()
            assertEquals(0L,cache.size());assertEquals("keep-login-and-bookmarks",preferences.readText())
        }finally{parent.deleteRecursively()}
    }
}
