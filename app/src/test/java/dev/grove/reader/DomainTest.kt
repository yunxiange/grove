package dev.grove.reader

import dev.grove.reader.domain.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun acceptsRepositoryNamesAndCanonicalUrls() {
        assertEquals(RepoId("android","nowinandroid"),RepoId.parse("android/nowinandroid"))
        assertEquals(RepoId("owner","repo"),RepoId.parse("https://github.com/owner/repo.git/"))
    }
    @Test fun rejectsAmbiguousOrUnsafeRepositoryInputs() {
        listOf("https://github.com.evil.test/owner/repo","https://owner@github.com/owner/repo","javascript:alert(1)",
            "owner/repo/tree/main","../repo","owner/..","owner/repo/extra").forEach {assertNull(it,RepoId.parse(it))}
    }
    @Test fun bookmarksKeepBranchAndRepositoryInTheirIdentity() {
        val a=Bookmark(RepoId("a","repo"),"main","README.md")
        assertNotEquals(a.key,a.copy(ref="release/v2").key)
        assertNotEquals(a.key,a.copy(repo=RepoId("b","repo")).key)
    }
    @Test fun linksResolveAgainstDocumentRatherThanRepositoryRoot() {
        assertEquals("docs/guide.md",resolveRepoPath("docs/nested/README.md","../guide.md"))
        assertEquals("assets/a b.png",resolveRepoPath("docs/README.md","../assets/a%20b.png"))
        assertNull(resolveRepoPath("README.md","https://external.test/readme.md"))
        assertNull(resolveRepoPath("README.md","//external.test/path"))
    }
    @Test fun newProjectSearchUsesCreationDateAndDoesNotClaimStarGrowth() {
        val query=discoveryQuery(Category.ANDROID,"Kotlin","2026-09-29")
        assertTrue(query.contains("topic:android"));assertTrue(query.contains("language:Kotlin"))
        assertTrue(query.contains("created:>=2026-09-29"));assertTrue(query.contains("archived:false"))
        assertFalse(discoveryQuery(Category.ALL,"",null).contains("created:"))
    }
    @Test fun URLParametersAndFilePathsAreEncodedSeparately() {
        assertEquals("release%2Fv2",encode("release/v2"))
        assertEquals("docs/a%20b.md",encodePath("docs/a b.md"))
    }
    @Test fun discoveryKeywordsReachSearchWithoutTheFeaturedStarThreshold() {
        val query=discoveryQuery(Category.ANDROID,"Kotlin",null,"small reader")
        assertTrue(query.contains("small reader"));assertTrue(query.contains("topic:android"))
        assertTrue(query.contains("language:Kotlin"));assertFalse(query.contains("stars:>10"))
        assertTrue(discoveryQuery(Category.ALL,"",null).contains("stars:>10"))
    }
}
