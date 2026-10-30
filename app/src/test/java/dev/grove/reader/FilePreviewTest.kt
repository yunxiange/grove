package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64
import java.lang.reflect.InvocationTargetException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

class FilePreviewTest {
    private val png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4l8AAAAASUVORK5CYII=")
    private fun decode(client:GitHubClient,path:String,bytes:ByteArray):Document {
        val json=JSONObject().put("path",path).put("size",bytes.size).put("encoding","base64").put("content",Base64.getEncoder().encodeToString(bytes))
        return GitHubClient::class.java.getDeclaredMethod("decodeDocument",RepoId::class.java,String::class.java,JSONObject::class.java).apply{isAccessible=true}
            .invoke(client,RepoId("owner","private-repo"),"feature/docs",json) as Document
    }
    @Test fun previewRoutingUsesContentSignaturesBeforeBinaryRejection() {
        assertEquals("image/png",previewType("image.PNG",png)?.mimeType)
        assertEquals(PreviewKind.IMAGE,previewType("misnamed.md",png)?.kind)
        assertEquals(PreviewKind.PDF,previewType("guide.pdf","%PDF-1.7\n".toByteArray())?.kind)
        assertEquals(PreviewKind.SPREADSHEET,previewType("data.csv","name,value\nhello,123".toByteArray())?.kind)
        assertNull(previewType("archive.zip",byteArrayOf(80,75,0,0)))
    }
    @Test fun pdfAndExcelBypassUtf8BinaryDecoder() {
        val root=createTempDir(prefix="grove-files-");val api=GitHubClient(root)
        try {
            assertEquals(PreviewKind.PDF,decode(api,"guide.pdf","%PDF-1.7\n\u0000binary".toByteArray()).attachment?.kind)
            val output=ByteArrayOutputStream()
            ZipOutputStream(output).use{zip->zip.putNextEntry(ZipEntry("xl/workbook.xml"));zip.write("<workbook/>".toByteArray());zip.closeEntry()}
            assertEquals(PreviewKind.SPREADSHEET,decode(api,"data.xlsx",output.toByteArray()).attachment?.kind)
        }finally{root.deleteRecursively()}
    }
    @Test fun attachmentCachesAreSeparatedAndClearedPerAccount() {
        val root=createTempDir(prefix="grove-account-");var user="100"
        val api=GitHubClient(root,account={user})
        try {
            val a=decode(api,"image.png",png).attachment!!
            user="200";val b=decode(api,"image.png",png).attachment!!
            assertNotEquals(a.localPath,b.localPath)
            api.clearAccountFiles()
            assertTrue(File(a.localPath).exists());assertFalse(File(b.localPath).exists())
        }finally{root.deleteRecursively()}
    }
    @Test fun unknownBinaryStillReceivesClearUnsupportedMessage() {
        val root=createTempDir(prefix="grove-binary-")
        try {decode(GitHubClient(root),"archive.bin",byteArrayOf(1,0,2));fail("未知二进制不应作为文本返回")}
        catch(error:InvocationTargetException){assertTrue(error.targetException.message!!.contains("暂不支持"))}
        finally{root.deleteRecursively()}
    }
}
