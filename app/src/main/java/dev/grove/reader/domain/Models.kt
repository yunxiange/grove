package dev.grove.reader.domain

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class RepoId(val owner: String, val name: String) {
    val fullName get() = "$owner/$name"
    companion object {
        fun parse(input: String): RepoId? {
            val value = input.trim().removeSuffix("/")
            val path = if (value.startsWith("https://") || value.startsWith("http://")) {
                val uri = runCatching { URI(value) }.getOrNull() ?: return null
                if (!uri.host.equals("github.com", ignoreCase = true) || uri.userInfo != null) return null
                uri.path.trim('/')
            } else value
            val parts = path.split('/')
            if (parts.size != 2 || !parts[0].matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}"))) return null
            val name = parts[1].removeSuffix(".git")
            if (!name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) || name == "." || name == "..") return null
            return RepoId(parts[0], name)
        }
    }
}

data class Project(val id: RepoId, val description: String = "", val stars: Int = 0,
    val language: String = "", val branch: String = "main", val isPrivate: Boolean = false)
data class RepoFile(val path: String, val folder: Boolean, val size: Long = 0) {
    val name get() = path.substringAfterLast('/')
    val markdown get() = name.endsWith(".md", true) || name.endsWith(".markdown", true)
}
enum class PreviewKind { IMAGE, PDF, SPREADSHEET, EPUB, MOBI;
    val ebook get()=this==EPUB||this==MOBI
}
data class Attachment(val kind: PreviewKind, val mimeType: String, val localPath: String)
data class FileDownloadProgress(val downloadedBytes:Long,val totalBytes:Long?,val kind:PreviewKind=PreviewKind.EPUB) {
    val fraction:Float? get()=totalBytes?.takeIf{it>0&&downloadedBytes<=it}?.let{(downloadedBytes.toDouble()/it).toFloat().coerceIn(0f,1f)}
}
data class DocumentTarget(val repo:RepoId,val ref:String,val path:String) {
    val key get()="${repo.fullName}:$ref:$path"
}
data class ImageRequest(val document:Document,val url:String)
data class ImageDownload(val bytes:ByteArray,val mimeType:String,val fileName:String)
data class ImageSavePrompt(val mimeType:String,val fileName:String)
data class Document(val repo: RepoId, val ref: String, val path: String, val content: String,
    val attachment: Attachment? = null, val originUrl: String? = null, val sourceTooLarge:Boolean=false) {
    val key get() = "${repo.fullName}:$ref:$path"
    val markdown get() = attachment == null && (path.endsWith(".md", true) || path.endsWith(".markdown", true))
    val svg get() = attachment?.kind==PreviewKind.IMAGE&&attachment.mimeType=="image/svg+xml"
    val rawRoot get() = "https://raw.githubusercontent.com/${repo.fullName}/${encodePath(ref)}/"
    val url get() = originUrl ?: (rawRoot + encodePath(path))
}
data class UserProfile(val id: String, val login: String)
data class RepositoryVisit(val repo:RepoId,val visitedAt:Long,val pinned:Boolean=false)
enum class RecentSort(val label:String) { VISITED("访问时间"), NAME("项目名称"), STARS("Star 数") }
data class Bookmark(val repo: RepoId, val ref: String, val path: String, val progress: Float = 0f) {
    val key get() = "${repo.fullName}:$ref:$path"
}
data class Issue(val number: Int, val title: String, val body: String, val author: String,
    val state: String, val comments: Int, val updated: String, val labels: List<String>, val url: String)
data class IssueComment(val author: String, val body: String, val created: String)
data class Heading(val id: String, val text: String, val level: Int)
enum class Destination(val label: String) { LIBRARY("足迹"), SAVED("收藏"), DISCOVER("发现"), PROFILE("我的") }
enum class RepoTab(val label: String) { READ("阅读"), FILES("文件"), ISSUES("Issues") }

enum class Category(val label: String, val qualifier: String) {
    ALL("全部", ""), AI("AI", "topic:artificial-intelligence"),
    ANDROID("Android", "topic:android"), FRONTEND("前端", "topic:frontend"),
    TOOLS("开发工具", "topic:developer-tools"), SELF_HOSTED("自托管", "topic:self-hosted"),
    LEARN("学习资源", "topic:education");
}

fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
fun encodePath(value: String): String = value.split('/').joinToString("/") { encode(it) }
fun discoveryQuery(category: Category, language: String, createdAfter: String?,keyword:String=""): String = buildList {
    if(keyword.isBlank())add("stars:>10")else add(keyword.trim())
    add("archived:false"); add("fork:false")
    if (category.qualifier.isNotBlank()) add(category.qualifier)
    if (language.isNotBlank()) add("language:$language")
    if (createdAfter != null) add("created:>=$createdAfter")
}.joinToString(" ")

fun resolveRepoPath(currentPath: String, link: String): String? {
    val uri = runCatching { URI(link) }.getOrNull() ?: return null
    if (uri.isAbsolute || uri.authority != null || uri.path.isNullOrEmpty()) return null
    val base = URI("https://grove.invalid/" + encodePath(currentPath))
    val resolved = base.resolve(uri).normalize()
    if (resolved.host != "grove.invalid") return null
    return resolved.path.removePrefix("/")
}
