package dev.grove.reader

import android.content.*
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import dev.grove.reader.data.ImageStore
import dev.grove.reader.domain.ImageDownload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ImageStoreTest {
    private lateinit var provider:ImageProvider
    private lateinit var store:ImageStore
    class ImageProvider(val output:File):ContentProvider() {
        var inserted=ContentValues()
        var collection:Uri?=null
        var published=false
        var deleted=false
        var failWrite=false
        override fun onCreate()=true
        override fun getType(uri:Uri)="image/png"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri {
            collection=uri;inserted=ContentValues(values);return ContentUris.withAppendedId(uri,1)
        }
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int {
            published=values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING)==0;return 1
        }
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int {deleted=true;output.delete();return 1}
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(failWrite)throw java.io.FileNotFoundException("storage full")
            return ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
        }
    }
    @Before fun setup() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        provider=ImageProvider(context.cacheDir.resolve("saved-image"))
        provider.attachInfo(context,ProviderInfo().apply{authority="media"})
        ShadowContentResolver.registerProviderInternal("media",provider)
        store=ImageStore(context.contentResolver)
    }
    @Test fun imageIsWrittenBeforePublishingToPictures()=runBlocking {
        val image=ImageDownload(byteArrayOf(1,2,3),"image/png","photo.png")
        assertTrue(store.save(image).contains("相册"))
        assertArrayEquals(image.bytes,provider.output.readBytes())
        assertEquals("Pictures/Grove",provider.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals(1,provider.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        assertTrue(provider.published);assertFalse(provider.deleted)
    }
    @Test fun svgPreservesOriginalInDownloads()=runBlocking {
        store.save(ImageDownload("<svg/>".toByteArray(),"image/svg+xml","diagram.svg"))
        assertEquals("Download/Grove",provider.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("image/svg+xml",provider.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertTrue(provider.collection.toString().contains("downloads"))
        assertEquals("<svg/>",provider.output.readText())
    }
    @Test fun writeFailureRemovesUnfinishedMediaEntry()=runBlocking {
        provider.failWrite=true
        try{store.save(ImageDownload(byteArrayOf(1),"image/png","photo.png"));fail("写入失败不能报告保存成功")}
        catch(_:IOException){}
        assertTrue(provider.deleted);assertFalse(provider.published)
    }
    @Test fun documentPickerUriReceivesOriginalBytes()=runBlocking {
        val image=ImageDownload(byteArrayOf(4,5,6),"image/png","photo.png")
        store.saveToDocument(image,Uri.parse("content://media/documents/created-image"))
        assertArrayEquals(image.bytes,provider.output.readBytes())
    }
}
