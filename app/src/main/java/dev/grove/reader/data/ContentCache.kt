package dev.grove.reader.data

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes

/** Operates only inside Android's temporary cache directory, without following links. */
class ContentCache(private val root:File) {
    fun size():Long {
        if(!root.exists())return 0
        var bytes=0L
        Files.walkFileTree(root.toPath(),object:SimpleFileVisitor<Path>() {
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {
                if(attrs.isRegularFile)bytes+=attrs.size()
                return FileVisitResult.CONTINUE
            }
        })
        return bytes
    }
    fun clear() {
        if(!root.exists())return
        val base=root.toPath()
        Files.walkFileTree(base,object:SimpleFileVisitor<Path>() {
            override fun visitFile(file:Path,attrs:BasicFileAttributes):FileVisitResult {Files.deleteIfExists(file);return FileVisitResult.CONTINUE}
            override fun postVisitDirectory(dir:Path,error:java.io.IOException?):FileVisitResult {
                if(error!=null)throw error
                if(dir!=base)Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
