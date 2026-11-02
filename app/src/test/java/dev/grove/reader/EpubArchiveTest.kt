package dev.grove.reader

import dev.grove.reader.data.EpubArchive
import dev.grove.reader.domain.PreviewKind
import dev.grove.reader.domain.previewType
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

object EpubFixture {
    fun frontMatterEntries()=entries().apply {
        this["OPS/book.opf"]=getValue("OPS/book.opf")
            .replace("<manifest>","""<manifest><item id="cover-page" href="text/front.xhtml" media-type="application/xhtml+xml"/><item id="preface" href="text/preface.xhtml" media-type="application/xhtml+xml"/>""")
            .replace("<spine>","""<spine><itemref idref="cover-page"/><itemref idref="preface"/>""")
            .replace("</package>","""<guide><reference type="cover" title="封面" href="text/front.xhtml"/></guide></package>""")
        this["OPS/text/front.xhtml"]="""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>纸上漫游</title></head><body><img src="../images/cover.svg"/></body></html>"""
        this["OPS/text/preface.xhtml"]="""<html xmlns="http://www.w3.org/1999/xhtml"><head><title>纸上漫游</title></head><body><h1>序言</h1><p>开始阅读之前。</p></body></html>"""
    }
    fun entries()=linkedMapOf(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
        "OPS/book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">urn:grove:reading-test</dc:identifier><dc:language>zh</dc:language><dc:title>纸上漫游</dc:title><dc:creator>Grove</dc:creator></metadata><manifest><item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/><item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" properties="nav" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>""",
        "OPS/nav.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="text/one.xhtml">第一章</a></li><li><a href="text/two.xhtml#detail">第二章</a></li></ol></nav></body></html>""",
        "OPS/text/one.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1>第一章</h1><p>纸面阅读。</p><img src="../images/cover.svg"/><a href="two.xhtml#detail">继续</a></body></html>""",
        "OPS/text/two.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><h1 id="detail">第二章</h1><p>继续阅读。</p></body></html>""",
        "OPS/images/cover.svg" to """<svg xmlns="http://www.w3.org/2000/svg" width="240" height="120"><rect width="240" height="120" fill="brown"/></svg>"""
    )
    fun bytes(entries:Map<String,String> = entries()):ByteArray=ByteArrayOutputStream().also{out->
        ZipOutputStream(out).use{zip->for((name,value) in entries){
            val bytes=value.toByteArray()
            val entry=ZipEntry(name).apply{if(name=="mimetype"){method=ZipEntry.STORED;size=bytes.size.toLong();compressedSize=size;crc=CRC32().apply{update(bytes)}.value}}
            zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry()
        }}
    }.toByteArray()
}

class EpubArchiveTest {
    private fun withBook(entries:Map<String,String> = EpubFixture.entries(),run:(java.io.File)->Unit) {
        val file=Files.createTempFile("grove-epub-",".epub").toFile()
        try{file.writeBytes(EpubFixture.bytes(entries));run(file)}finally{file.delete()}
    }
    @Test fun epub3UsesSpineOrderTitleContentsAndBoundedLocalImages()=withBook{file->
        assertEquals(PreviewKind.EPUB,previewType("book.epub",file.readBytes())?.kind)
        val book=EpubArchive.open(file)
        assertEquals("纸上漫游",book.title);assertEquals("Grove",book.author)
        assertEquals(listOf("OPS/text/one.xhtml","OPS/text/two.xhtml"),book.chapters.map{it.path})
        assertEquals("detail",book.contents.last().link.fragment)
        assertTrue(EpubArchive.chapter(file,book.chapters.first().path).contains("纸面阅读"))
        assertEquals("image/svg+xml",EpubArchive.resource(file,"OPS/images/cover.svg")!!.first)
        assertNull(EpubArchive.resource(file,"OPS/text/one.xhtml"))
    }
    @Test fun frontMatterMissingFromTheTocKeepsItsOwnTitles()=withBook(EpubFixture.frontMatterEntries()){file->
        val book=EpubArchive.open(file)
        assertEquals(listOf("封面","序言","第一章","第二章"),book.chapters.map{it.title})
        assertEquals(listOf("OPS/text/front.xhtml","OPS/text/preface.xhtml","OPS/text/one.xhtml","OPS/text/two.xhtml"),book.chapters.map{it.path})
    }
    @Test fun epub3LandmarksAndPageSemanticsIdentifyTheCoverWithoutGuessingFromItsIndex() {
        for(landmark in listOf(false,true)) {
            val entries=EpubFixture.frontMatterEntries().apply {
                this["OPS/book.opf"]=getValue("OPS/book.opf").replace(Regex("<guide>.*?</guide>"),"")
                if(landmark)this["OPS/nav.xhtml"]=getValue("OPS/nav.xhtml").replace("</body>","""<nav epub:type="landmarks"><ol><li><a epub:type="cover" href="text/front.xhtml">Cover</a></li></ol></nav></body>""")
                else this["OPS/text/front.xhtml"]=getValue("OPS/text/front.xhtml")
                    .replace("<body>","""<body xmlns:epub="http://www.idpf.org/2007/ops" epub:type="cover">""")
            }
            withBook(entries){assertEquals("封面",EpubArchive.open(it).chapters.first().title)}
        }
    }
    @Test fun pageDocumentTitleIsUsedAndUnnamedPagesDoNotReceiveInventedChapterNumbers() {
        for((html,expected) in listOf(
            """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>版权 &amp; 许可</title></head><body><p>出版信息。</p></body></html>""" to "版权 & 许可",
            """<html xmlns="http://www.w3.org/1999/xhtml"><body><img src="../images/cover.svg"/></body></html>""" to "未命名页面"
        )) {
            val entries=EpubFixture.frontMatterEntries().apply {
                this["OPS/book.opf"]=getValue("OPS/book.opf").replace(Regex("<guide>.*?</guide>"),"")
                this["OPS/text/front.xhtml"]=html
            }
            withBook(entries){assertEquals(expected,EpubArchive.open(it).chapters.first().title)}
        }
    }
    @Test fun epub2ReadsNcxWithoutLoadingExternalDoctype() {
        val entries=EpubFixture.entries().apply{
            remove("OPS/nav.xhtml")
            this["OPS/book.opf"]=getValue("OPS/book.opf").replace("version=\"3.0\"","version=\"2.0\"").replace("properties=\"nav\" media-type=\"application/xhtml+xml\"","media-type=\"application/x-dtbncx+xml\"").replace("href=\"nav.xhtml\"","href=\"toc.ncx\"").replace("<spine>","<spine toc=\"nav\">")
            this["OPS/toc.ncx"]="""<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "https://example.invalid/ncx.dtd"><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap><navPoint><navLabel><text>开篇</text></navLabel><content src="text/one.xhtml"/></navPoint></navMap></ncx>"""
        }
        withBook(entries){assertEquals("开篇",EpubArchive.open(it).contents.first().title)}
    }
    @Test fun encryptedAndUnsafeArchivesFailClearly() {
        for((path,content) in listOf("META-INF/encryption.xml" to "<encryption/>","../outside" to "unsafe")) {
            withBook(EpubFixture.entries()+mapOf(path to content)){file->
                try{EpubArchive.open(file);fail("不应接受加密或越界的 EPUB") }catch(_:IllegalArgumentException){}
            }
        }
        val entries=EpubFixture.entries().apply{this["META-INF/container.xml"]="""<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///etc/passwd">]><container>&secret;</container>"""}
        withBook(entries){file->try{EpubArchive.open(file);fail("内部实体应被拒绝")}catch(_:IllegalArgumentException){}}
    }
    @Test fun linksResolveInsideTheBookAndKeepFragments() {
        assertEquals("OPS/images/cover.svg",EpubArchive.resolve("OPS/text/one.xhtml","../images/cover.svg")?.path)
        assertNull(EpubArchive.resolve("OPS/text/one.xhtml","https://example.com/private"))
        assertNull(EpubArchive.resolve("OPS/text/one.xhtml","../../../outside"))
        assertNull(EpubArchive.resolve("OPS/text/one.xhtml","/%2e%2e/outside"))
    }
    @Test fun oversizedChapterIsRejectedBeforeRendering() {
        withBook(EpubFixture.entries()+mapOf("OPS/text/one.xhtml" to "x".repeat(2_000_001))){file->
            EpubArchive.open(file)
            try{EpubArchive.chapter(file,"OPS/text/one.xhtml");fail("超大章节不应进入 WebView")}catch(_:IllegalArgumentException){}
        }
    }
}
