package dev.grove.reader

import dev.grove.reader.data.GitHubClient
import dev.grove.reader.data.GitHubException
import dev.grove.reader.domain.RepoId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class StarApiTest {
    private class Response(url:URL,var status:Int=204):HttpURLConnection(url) {
        val body=ByteArrayOutputStream()
        fun bodyLength()=fixedContentLength
        override fun connect()=Unit
        override fun disconnect()=Unit
        override fun usingProxy()=false
        override fun getResponseCode()=status
        override fun getOutputStream()=body
    }
    private val repo=RepoId("owner","repository")

    @Test fun starAndUnstarUseAuthenticatedEmptyBodyMutations()=runBlocking {
        val root=Files.createTempDirectory("grove-star").toFile()
        val connections=mutableListOf<Response>()
        val api=GitHubClient(root,token={"test-token"},connectionFactory={Response(it).also(connections::add)})
        try {
            api.setStarred(repo,true);api.setStarred(repo,false)
            assertEquals(listOf("PUT","DELETE"),connections.map{it.requestMethod})
            assertEquals(0,connections.first().bodyLength());assertEquals(0,connections.first().body.size())
            connections.forEach {
                assertEquals("/user/starred/owner/repository",it.url.path)
                assertEquals("Bearer test-token",it.getRequestProperty("Authorization"))
                assertFalse(it.instanceFollowRedirects)
            }
            assertTrue(root.listFiles().orEmpty().isEmpty())
        }finally{root.deleteRecursively()}
    }
    @Test fun onlyStatus404MeansNotStarred()=runBlocking {
        val root=Files.createTempDirectory("grove-star-status").toFile()
        var status=204
        val api=GitHubClient(root,token={"test-token"},connectionFactory={Response(it,status)})
        try {
            assertTrue(api.isStarred(repo));status=404;assertFalse(api.isStarred(repo))
            status=403
            try{api.isStarred(repo);fail("权限失败不能当成未 Star")}
            catch(e:GitHubException){assertEquals(403,e.status)}
        }finally{root.deleteRecursively()}
    }
    @Test fun failedMutationsAreNotReportedAsSuccess()=runBlocking {
        val root=Files.createTempDirectory("grove-star-failure").toFile()
        val api=GitHubClient(root,token={"test-token"},connectionFactory={Response(it,403)})
        try {
            try{api.setStarred(repo,true);fail("写入被拒绝时应保留原状态")}
            catch(e:GitHubException){assertEquals(403,e.status);assertTrue(e.message!!.contains("Starring"))}
        }finally{root.deleteRecursively()}
    }
    @Test fun guestCannotSendStarMutation()=runBlocking {
        val root=Files.createTempDirectory("grove-star-guest").toFile()
        var requests=0
        val api=GitHubClient(root,connectionFactory={requests++;Response(it)})
        try {
            try{api.setStarred(repo,true);fail("访客应先登录")}
            catch(e:GitHubException){assertEquals(401,e.status)}
            assertEquals(0,requests)
        }finally{root.deleteRecursively()}
    }
}
