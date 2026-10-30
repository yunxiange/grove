package dev.grove.reader.data

import dev.grove.reader.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class GitHubException(message: String, val status: Int? = null) : Exception(message)

class GitHubClient(private val cacheDir: File, private val token: () -> String = { "" },
    private val account: () -> String = { "anonymous" },
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    suspend fun request(path: String, allowCache: Boolean = true): String = withContext(Dispatchers.IO) {
        val credential = token()
        val key = MessageDigest.getInstance("SHA-256").digest((credential.hashCode().toString()+path).toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(cacheDir, "$key.json")
        val etagFile = File(cacheDir, "$key.etag")
        cacheDir.mkdirs()
        val connection = connectionFactory(URL("https://api.github.com$path"))
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Grove-Android/0.2")
            if (credential.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $credential")
            if (allowCache && credential.isBlank() && etagFile.exists() && file.exists()) connection.setRequestProperty("If-None-Match", etagFile.readText())
            val status = connection.responseCode
            if (status == 304 && credential.isBlank() && file.exists()) return@withContext file.readText()
            if (status !in 200..299) {
                val message = when {
                    status == 401 -> "登录已失效，请重新授权。"
                    status == 404 -> "未找到内容。若是私有仓库，请先登录并在「仓库授权」中允许 Grove 访问；组织仓库可能需要管理员批准。"
                    status == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0" -> "GitHub 请求额度已用完，请稍后重试。登录后可获得更高额度。"
                    status == 403 -> "无法访问，请检查仓库授权、组织访问策略或 GitHub 请求限制。"
                    status == 429 -> "GitHub 暂时限制了请求，请稍后重试。"
                    status == 422 -> "搜索条件暂不受支持，请调整筛选后重试。"
                    else -> "GitHub 暂时不可用（$status），请重试。"
                }
                throw GitHubException(message, status)
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (allowCache && credential.isBlank()) {
                file.writeText(body)
                connection.getHeaderField("ETag")?.let { etagFile.writeText(it) }
                trimCache()
            }
            body
        } catch (e: GitHubException) { throw e }
        catch (e: java.io.IOException) {
            // Cached public responses remain readable offline. HTTP permission failures never use stale data.
            if (allowCache && credential.isBlank() && file.exists()) file.readText()
            else throw GitHubException("网络连接失败，请检查网络后重试。")
        } finally { connection.disconnect() }
    }

    private fun trimCache() {
        val files = cacheDir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }.orEmpty()
        files.drop(100).forEach { it.delete(); File(cacheDir, it.nameWithoutExtension+".etag").delete() }
    }
    suspend fun project(id: RepoId) = parseProject(JSONObject(request("/repos/${id.fullName}")))
    suspend fun directory(id: RepoId, ref: String, path: String): List<RepoFile> {
        val array = JSONArray(request("/repos/${id.fullName}/contents/${encodePath(path)}?ref=${encode(ref)}"))
        return (0 until array.length()).map { i -> array.getJSONObject(i).let {
            RepoFile(it.getString("path"), it.getString("type") == "dir", it.optLong("size"))
        }}.sortedWith(compareByDescending<RepoFile> { it.folder }.thenBy { it.name.lowercase() })
    }
    suspend fun readme(id: RepoId, ref: String): Document = decodeDocument(id, ref,
        JSONObject(request("/repos/${id.fullName}/readme?ref=${encode(ref)}")))
    suspend fun document(id: RepoId, ref: String, path: String): Document {
        val expectedAccount=account()
        val route="/repos/${id.fullName}/contents/${encodePath(path)}?ref=${encode(ref)}"
        val json=JSONObject(request(route))
        if(json.optLong("size")>20_000_000)throw GitHubException("文件超过 20 MB，请在 GitHub 中打开。")
        return if(json.optString("encoding")=="base64")decodeDocument(id,ref,json)
        else withContext(Dispatchers.IO) {
            val bytes=download("https://api.github.com$route",true,20_000_000)
            if(account()!=expectedAccount)throw kotlinx.coroutines.CancellationException("账号已切换")
            decodeFile(id,ref,path,bytes,json.optString("sha"))
        }
    }
    private fun decodeDocument(id: RepoId, ref: String, json: JSONObject): Document {
        if (json.optLong("size") > 20_000_000 || json.optString("encoding") != "base64")
            throw GitHubException("文件超过预览大小限制，请在 GitHub 中打开。")
        val bytes = Base64.getMimeDecoder().decode(json.getString("content"))
        return decodeFile(id,ref,json.getString("path"),bytes,json.optString("sha"))
    }
    private fun decodeFile(id: RepoId, ref: String, path: String, bytes: ByteArray, revision: String=""): Document {
        val preview=previewType(path,bytes)
        if(preview!=null) {
            if(preview.kind!=PreviewKind.PDF && bytes.size>10_000_000)throw GitHubException("图片和表格预览支持至多 10 MB 的文件。")
            if(preview.kind==PreviewKind.SPREADSHEET && bytes.take(2)==listOf(80.toByte(),75.toByte()))validateWorkbook(bytes)
            val folder=File(cacheDir,"attachments/"+hash(account())).apply {mkdirs()}
            val file=File(folder,hash("${id.fullName}:$ref:$path:$revision"))
            if(!file.exists()||revision.isBlank())file.writeBytes(bytes)
            val svg=preview.mimeType=="image/svg+xml"
            val sourceTooLarge=svg&&bytes.size>1_000_000
            val source=if(svg&&!sourceTooLarge)decodeSvgSource(bytes)else""
            return Document(id,ref,path,source,Attachment(preview.kind,preview.mimeType,file.absolutePath),sourceTooLarge=sourceTooLarge)
        }
        if(bytes.size>1_000_000)throw GitHubException("文本文件超过 1 MB，请在 GitHub 中打开。")
        if (bytes.take(8000).any { it == 0.toByte() }) throw GitHubException("暂不支持这种二进制文件，请在浏览器中打开。")
        return Document(id, ref, path, bytes.toString(Charsets.UTF_8))
    }
    private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun validateWorkbook(bytes:ByteArray) {
        var total=0L;var entries=0
        ZipInputStream(bytes.inputStream()).use {zip->
            val buffer=ByteArray(16384)
            while(zip.nextEntry!=null) {
                if(++entries>3000)throw GitHubException("表格结构过于复杂，请在 Excel 中打开。")
                while(true) {val n=zip.read(buffer);if(n<0)break;total+=n
                    if(total>50_000_000)throw GitHubException("表格解压后过大，请在 Excel 中打开。")}
            }
        }
    }
    private fun download(url:String,authenticated:Boolean,limit:Int):ByteArray {
        val connection=connectionFactory(URL(url))
        try {
            connection.connectTimeout=15000;connection.readTimeout=25000
            connection.instanceFollowRedirects=!authenticated
            connection.setRequestProperty("User-Agent","Grove-Android/0.2")
            if(authenticated) {
                connection.setRequestProperty("Accept","application/vnd.github.raw+json")
                token().takeIf{it.isNotBlank()}?.let{connection.setRequestProperty("Authorization","Bearer $it")}
            }
            val status=connection.responseCode
            if(status !in 200..299)throw GitHubException(if(status==401||status==403)"无法读取文件，请检查登录和仓库授权。"else"无法下载文件（$status）。",status)
            if(connection.contentLengthLong>limit)throw GitHubException("文件超过预览大小限制。")
            val output=ByteArrayOutputStream()
            connection.inputStream.use {input->val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break
                if(output.size()+n>limit)throw GitHubException("文件超过预览大小限制。")
                output.write(buffer,0,n)}}
            return output.toByteArray()
        }finally{connection.disconnect()}
    }
    fun imageResource(document:Document,url:String):Pair<String,ByteArray>? {
        if(!url.startsWith(document.rawRoot))return null
        val path=java.net.URI(url).path.removePrefix(java.net.URI(document.rawRoot).path)
        val bytes=download("https://api.github.com/repos/${document.repo.fullName}/contents/${encodePath(path)}?ref=${encode(document.ref)}",true,10_000_000)
        val type=previewType(path,bytes)?.takeIf{it.kind==PreviewKind.IMAGE}?:return null
        return type.mimeType to bytes
    }
    suspend fun imageForDownload(document:Document,url:String):ImageDownload=withContext(Dispatchers.IO) {
        val expectedAccount=account()
        var path=document.path
        val attachment=document.attachment
        val localUrl=attachment?.let{"https://appassets.androidplatform.net/attachment/"+File(it.localPath).name}
        val bytes=when {
            attachment?.kind==PreviewKind.IMAGE&&url==localUrl -> {
                val file=File(attachment.localPath)
                if(!file.isFile)throw GitHubException("图片缓存已失效，请重新打开图片后保存。")
                if(file.length()>10_000_000)throw GitHubException("图片超过 10 MB 保存限制。")
                file.readBytes()
            }
            url.startsWith("data:image/") -> {
                if(url.length>30_000_000)throw GitHubException("图片超过 10 MB 保存限制。")
                val header=url.substringBefore(',');val payload=url.substringAfter(',',"")
                if(header.endsWith(";base64")&&payload.length>13_333_336)throw GitHubException("图片超过 10 MB 保存限制。")
                path="image."+if(header.startsWith("data:image/svg+xml"))"svg"else"bin"
                try {
                    if(header.endsWith(";base64"))Base64.getDecoder().decode(payload)
                    else java.net.URLDecoder.decode(payload.replace("+","%2B"),"UTF-8").toByteArray(Charsets.UTF_8)
                }catch(_:IllegalArgumentException){throw GitHubException("内嵌图片数据无法读取。")}
            }
            url.startsWith(document.rawRoot) -> {
                path=java.net.URI(url).path
                imageResource(document,url)?.second?:throw GitHubException("此内容不是可保存的图片。")
            }
            else -> {
                val uri=runCatching{java.net.URI(url)}.getOrNull()
                if(uri?.scheme!="https"||uri.host.isNullOrBlank()||uri.userInfo!=null||uri.host=="appassets.androidplatform.net")
                    throw GitHubException("暂不支持保存此图片地址。")
                path=uri.path.orEmpty()
                download(url,false,10_000_000)
            }
        }
        if(account()!=expectedAccount)throw kotlinx.coroutines.CancellationException("账号已切换")
        if(bytes.size>10_000_000)throw GitHubException("图片超过 10 MB 保存限制。")
        val type=previewType(path,bytes)?.takeIf{it.kind==PreviewKind.IMAGE}?:throw GitHubException("此内容不是可保存的图片。")
        ImageDownload(bytes,type.mimeType,imageDownloadName(path,type.mimeType))
    }
    suspend fun remoteImage(id:RepoId,ref:String,url:String):Document=withContext(Dispatchers.IO) {
        val expectedAccount=account()
        val uri=java.net.URI(url)
        if(uri.scheme!="https"||uri.userInfo!=null)throw GitHubException("暂不支持此图片地址。")
        val bytes=download(url,false,10_000_000)
        if(account()!=expectedAccount)throw kotlinx.coroutines.CancellationException("账号已切换")
        if(previewType(uri.path,bytes)?.kind!=PreviewKind.IMAGE)throw GitHubException("此链接没有返回可预览的图片。")
        decodeFile(id,ref,"external/${hash(url)}/${uri.path.substringAfterLast('/').ifBlank{"image"}}",bytes).copy(originUrl=url)
    }
    fun clearAccountFiles() {File(cacheDir,"attachments/"+hash(account())).deleteRecursively()}
    suspend fun repositories(page:Int):List<Project> = parseProjects(JSONArray(request("/user/repos?affiliation=owner,collaborator,organization_member&sort=updated&per_page=30&page=$page",false)))
    suspend fun profile():UserProfile {val json=JSONObject(request("/user",false));return UserProfile(json.getLong("id").toString(),json.getString("login"))}
    suspend fun branches(id: RepoId, page: Int = 1): List<String> {
        val array = JSONArray(request("/repos/${id.fullName}/branches?per_page=100&page=$page"))
        return (0 until array.length()).map { array.getJSONObject(it).getString("name") }
    }
    suspend fun discover(category: Category, language: String, createdAfter: String?, page: Int, allowCache:Boolean=true,keyword:String=""): List<Project> {
        val query = encode(discoveryQuery(category, language, createdAfter,keyword))
        val result = JSONObject(request("/search/repositories?q=$query&sort=stars&order=desc&per_page=30&page=$page",allowCache))
        return parseProjects(result.getJSONArray("items"))
    }
    suspend fun issues(id: RepoId, closed: Boolean, page: Int): Pair<List<Issue>, Boolean> {
        val array = JSONArray(request("/repos/${id.fullName}/issues?state=${if(closed)"closed" else "open"}&per_page=30&page=$page"))
        val issues = (0 until array.length()).map { array.getJSONObject(it) }.filterNot { it.has("pull_request") }.map(::parseIssue)
        return issues to (array.length() == 30)
    }
    suspend fun comments(id: RepoId, number: Int, page: Int): List<IssueComment> {
        val array = JSONArray(request("/repos/${id.fullName}/issues/$number/comments?per_page=30&page=$page"))
        return (0 until array.length()).map { array.getJSONObject(it).let { item ->
            IssueComment(item.getJSONObject("user").getString("login"), item.optString("body"), item.optString("created_at").take(10))
        }}
    }
    suspend fun starred(page: Int = 1,perPage:Int=30): List<Project> = parseProjects(JSONArray(request("/user/starred?per_page=${perPage.coerceIn(1,100)}&page=$page", false)))
    suspend fun isStarred(id: RepoId): Boolean = starRequest(id, "GET") == 204
    suspend fun setStarred(id: RepoId, starred: Boolean) { starRequest(id, if(starred) "PUT" else "DELETE") }
    private suspend fun starRequest(id: RepoId, method: String): Int = withContext(Dispatchers.IO) {
        val credential = token()
        if(credential.isBlank())throw GitHubException("请先登录 GitHub 后操作 Star。",401)
        val connection = connectionFactory(URL("https://api.github.com/user/starred/${id.fullName}"))
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000;connection.readTimeout=20_000
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            connection.setRequestProperty("User-Agent","Grove-Android/0.2")
            connection.setRequestProperty("Authorization","Bearer $credential")
            if(method=="PUT") {
                connection.doOutput=true;connection.setFixedLengthStreamingMode(0)
                connection.outputStream.use { }
            }
            val status=connection.responseCode
            when {
                status==204 -> status
                method=="GET"&&status==404 -> status
                status==401 -> throw GitHubException("登录已失效，请重新授权。",status)
                status==403 -> throw GitHubException("无法操作 Star，请检查 GitHub 授权是否包含 Starring 读写权限，或稍后重试。",status)
                else -> throw GitHubException("Star 操作失败（$status），请重试。",status)
            }
        } catch(e:java.io.IOException) {throw GitHubException("网络连接失败，Star 状态尚未确认，请重试。")}
        finally {connection.disconnect()}
    }
    suspend fun user(): String = JSONObject(request("/user", false)).getString("login")
    private fun parseProjects(array: JSONArray) = (0 until array.length()).map { parseProject(array.getJSONObject(it)) }
    private fun parseProject(json: JSONObject): Project = Project(
        RepoId(json.getJSONObject("owner").getString("login"), json.getString("name")),
        json.optString("description").takeUnless { it == "null" }.orEmpty(), json.optInt("stargazers_count"),
        json.optString("language").takeUnless { it == "null" }.orEmpty(), json.optString("default_branch", "main"), json.optBoolean("private"))
    private fun parseIssue(json: JSONObject) = Issue(json.getInt("number"), json.getString("title"),
        json.optString("body").takeUnless { it == "null" }.orEmpty(), json.getJSONObject("user").getString("login"),
        json.getString("state"), json.optInt("comments"), json.optString("updated_at").take(10),
        json.optJSONArray("labels")?.let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") } }.orEmpty(),
        json.getString("html_url"))
}
