package dev.grove.reader.data

import android.content.Context
import dev.grove.reader.domain.*
import org.json.JSONArray
import org.json.JSONObject

class ReadingStore(context: Context, accountId: String = "anonymous") {
    private val prefs = context.getSharedPreferences(if(accountId=="anonymous")"grove.reading" else "grove.reading.user.$accountId", Context.MODE_PRIVATE)
    fun library(): List<RepoId> = array("library").mapNotNull { RepoId.parse(it.toString()) }
    fun saveLibrary(repos: List<RepoId>) { prefs.edit().putString("library", JSONArray(repos.map { it.fullName }).toString()).apply() }
    fun recentVisits():List<RepositoryVisit> {
        if(!prefs.contains("recentRepositories"))return resume()?.let{listOf(RepositoryVisit(it.repo,0))}.orEmpty()
        return array("recentRepositories").mapNotNull {value->
            val json=value as? JSONObject?:return@mapNotNull null
            val repo=RepoId.parse(json.optString("repo"))?:return@mapNotNull null
            RepositoryVisit(repo,json.optLong("visitedAt").coerceAtLeast(0))
        }.sortedByDescending{it.visitedAt}.distinctBy{it.repo.fullName.lowercase()}.take(30)
    }
    fun recordVisit(repo:RepoId,visitedAt:Long=System.currentTimeMillis()):List<RepositoryVisit> {
        val visits=(listOf(RepositoryVisit(repo,visitedAt.coerceAtLeast(0)))+recentVisits().filterNot{it.repo.fullName.equals(repo.fullName,true)}).take(30)
        saveVisits(visits);return visits
    }
    fun removeVisit(repo:RepoId):List<RepositoryVisit> {
        val visits=recentVisits().filterNot{it.repo.fullName.equals(repo.fullName,true)}
        saveVisits(visits);return visits
    }
    private fun saveVisits(visits:List<RepositoryVisit>) {
        prefs.edit().putString("recentRepositories",JSONArray(visits.map{JSONObject().put("repo",it.repo.fullName).put("visitedAt",it.visitedAt)}).toString()).apply()
    }
    var recentSort:RecentSort
        get()=runCatching{RecentSort.valueOf(prefs.getString("recentSort",RecentSort.VISITED.name)!!)}.getOrDefault(RecentSort.VISITED)
        set(value){prefs.edit().putString("recentSort",value.name).apply()}
    fun bookmarks(): List<Bookmark> = array("bookmarks").mapNotNull { parseBookmark(it as? JSONObject) }
    fun saveBookmarks(values: List<Bookmark>) { prefs.edit().putString("bookmarks", JSONArray(values.map(::bookmarkJson)).toString()).apply() }
    fun progress(key: String): Float = prefs.getFloat("position:$key", 0f)
    fun resume(): Bookmark? = runCatching { parseBookmark(JSONObject(prefs.getString("resume", "")!!)) }.getOrNull()
    fun saveProgress(value: Bookmark) {
        prefs.edit().putFloat("position:${value.key}", value.progress.coerceIn(0f,1f))
            .putString("resume", bookmarkJson(value).toString()).apply()
    }
    var fontSize: Int
        get() = prefs.getInt("font", 17)
        set(value) { prefs.edit().putInt("font", value).apply() }
    var warm: Boolean
        get() = prefs.getBoolean("warm", false)
        set(value) { prefs.edit().putBoolean("warm", value).apply() }
    private fun array(key: String): List<Any> = runCatching { JSONArray(prefs.getString(key,"[]")).let { a -> (0 until a.length()).map { a.get(it) } } }.getOrDefault(emptyList())
    private fun bookmarkJson(value: Bookmark) = JSONObject().put("repo",value.repo.fullName).put("ref",value.ref).put("path",value.path).put("progress",value.progress.toDouble())
    private fun parseBookmark(json: JSONObject?): Bookmark? {
        json ?: return null
        val id = RepoId.parse(json.optString("repo")) ?: return null
        return Bookmark(id,json.optString("ref","main"),json.optString("path"),json.optDouble("progress",0.0).toFloat())
    }
}
