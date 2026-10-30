package dev.grove.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.ReadingStore
import dev.grove.reader.domain.RepoId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ReadingStoreTest {
    private lateinit var context:Context
    @Before fun setup() {
        context=ApplicationProvider.getApplicationContext()
        for(name in listOf("grove.reading","grove.reading.user.100","grove.reading.user.200"))
            context.getSharedPreferences(name,0).edit().clear().commit()
    }
    @Test fun newSignedInAccountStartsWithoutGuestExamples() {
        assertTrue(ReadingStore(context).library().isEmpty())
        assertTrue("登录账号的书库不应自动加入访客示例仓库",ReadingStore(context,"100").library().isEmpty())
    }
    @Test fun explicitlyAddedRepositoriesArePreservedAndIsolatedAcrossAccounts() {
        val repos=listOf(RepoId("me","private-repo"),RepoId("android","nowinandroid"))
        ReadingStore(context,"100").saveLibrary(repos)
        assertEquals(repos,ReadingStore(context,"100").library())
        assertTrue(ReadingStore(context,"200").library().isEmpty())
        assertTrue(ReadingStore(context).library().isEmpty())
    }
    @Test fun clearingLibraryDoesNotReinsertExamplesForGuestOrSignedInUser() {
        for(account in listOf("anonymous","100")) {
            ReadingStore(context,account).saveLibrary(emptyList())
            assertTrue(ReadingStore(context,account).library().isEmpty())
        }
    }
}
