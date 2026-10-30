package dev.grove.reader.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.grove.reader.domain.ImageDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageStore(private val resolver:ContentResolver) {
    @RequiresApi(29)
    suspend fun save(image:ImageDownload):String=withContext(Dispatchers.IO) {
        val gallery=image.mimeType in setOf("image/png","image/jpeg","image/gif","image/webp","image/bmp","image/avif")
        val directory=(if(gallery)Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS)+"/Grove"
        val collection=if(gallery)MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,image.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE,image.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH,directory)
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri=resolver.insert(collection,values)?:throw IOException("无法创建图片文件，请检查设备存储空间。")
        try {
            write(image,uri)
            currentCoroutineContext().ensureActive()
            val published=resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
            if(published<1)throw IOException("无法完成图片保存，请重试。")
            if(gallery)"已保存到相册 · Pictures/Grove"else"已保存到下载 · Download/Grove"
        }catch(e:Exception){runCatching{resolver.delete(uri,null,null)};throw e}
    }

    suspend fun saveToDocument(image:ImageDownload,uri:Uri)=withContext(Dispatchers.IO) {
        require(uri.scheme=="content"){"保存位置无效，请重试。"}
        try {write(image,uri);currentCoroutineContext().ensureActive()}
        catch(e:Exception){runCatching{DocumentsContract.deleteDocument(resolver,uri)};throw e}
    }

    private fun write(image:ImageDownload,uri:Uri) {
        val output=resolver.openOutputStream(uri,"w")?:throw IOException("无法写入图片，请检查设备存储空间。")
        output.use{it.write(image.bytes)}
    }
}
