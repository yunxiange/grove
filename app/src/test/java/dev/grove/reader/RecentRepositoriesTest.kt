package dev.grove.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.ReadingStore
import dev.grove.reader.domain.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class RecentRepositoriesTest {
    private lateinit var context:Context
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        for(name in listOf("grove.reading","grove.reading.user.100","grove.reading.user.200"))context.getSharedPreferences(name,0).edit().clear().commit()
    }
    @Test fun visitsPersistWithMostRecentFirstAndCaseInsensitiveDeduplication() {
        val store=ReadingStore(context,"100")
        assertTrue(store.recentVisits().isEmpty())
        store.recordVisit(RepoId("owner","a"),100)
        store.recordVisit(RepoId("owner","b"),200)
        store.recordVisit(RepoId("OWNER","A"),300)
        val visits=ReadingStore(context,"100").recentVisits()
        assertEquals(listOf("OWNER/A","owner/b"),visits.map{it.repo.fullName})
        assertEquals(300L,visits.first().visitedAt)
        assertTrue(ReadingStore(context,"200").recentVisits().isEmpty())
        assertTrue(ReadingStore(context).recentVisits().isEmpty())
    }
    @Test fun removingVisitPreservesAddedRepositoryAndBookmarksAndAllowsRevisiting() {
        val repo=RepoId("owner","project");val store=ReadingStore(context,"100")
        val bookmark=Bookmark(repo,"main","README.md",.5f)
        store.saveLibrary(listOf(repo));store.saveBookmarks(listOf(bookmark));store.saveProgress(bookmark)
        store.recordVisit(repo,100)
        store.removeVisit(repo)
        assertTrue(ReadingStore(context,"100").recentVisits().isEmpty())
        assertEquals(listOf(repo),store.library());assertEquals(listOf(bookmark),store.bookmarks())
        store.recordVisit(repo,200)
        assertEquals(listOf(RepositoryVisit(repo,200)),store.recentVisits())
    }
    @Test fun historyIsBoundedAndSortPreferenceBelongsToAccount() {
        val store=ReadingStore(context,"100")
        for(i in 1..35)store.recordVisit(RepoId("owner","p$i"),i.toLong())
        assertEquals(30,store.recentVisits().size)
        assertEquals("p35",store.recentVisits().first().repo.name)
        store.recentSort=RecentSort.STARS
        assertEquals(RecentSort.STARS,ReadingStore(context,"100").recentSort)
        assertEquals(RecentSort.VISITED,ReadingStore(context,"200").recentSort)
    }
    @Test fun oldResumeProvidesOneRealVisitWithoutInventingDefaultProjects() {
        val store=ReadingStore(context,"100");val repo=RepoId("owner","previous")
        store.saveProgress(Bookmark(repo,"main","README.md",.5f))
        assertEquals(listOf(RepositoryVisit(repo,0)),store.recentVisits())
        store.removeVisit(repo)
        assertTrue(ReadingStore(context,"100").recentVisits().isEmpty())
    }
    @Test fun sortsByTimeNameAndKnownStarCountsWithoutChangingStoredOrder() {
        val a=RepoId("owner","alpha");val b=RepoId("owner","beta");val c=RepoId("owner","gamma")
        val visits=listOf(RepositoryVisit(c,300),RepositoryVisit(b,200),RepositoryVisit(a,100))
        val projects=mapOf(a.fullName to Project(a,stars=50),b.fullName to Project(b,stars=100))
        assertEquals(listOf(c,b,a),sortedRecentVisits(visits,projects,RecentSort.VISITED).map{it.repo})
        assertEquals(listOf(a,b,c),sortedRecentVisits(visits,projects,RecentSort.NAME).map{it.repo})
        assertEquals(listOf(b,a,c),sortedRecentVisits(visits,projects,RecentSort.STARS).map{it.repo})
        assertEquals(c,visits.first().repo)
    }
}
