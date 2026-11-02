package dev.grove.reader.data

import dev.grove.reader.domain.encodePath
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubLink(val path:String,val fragment:String?=null)
data class EpubChapter(val path:String,val title:String)
data class EpubContents(val title:String,val link:EpubLink,val depth:Int=0)
data class EpubBook(val title:String,val author:String,val chapters:List<EpubChapter>,val contents:List<EpubContents>)

/** Reads bounded resources directly from the archive; never extracts untrusted ZIP paths. */
object EpubArchive {
    private const val MAX_ENTRIES=2000
    private const val MAX_RESOURCE=10_000_000
    private val pageKindTitles=mapOf("cover" to "封面","titlepage" to "扉页","title-page" to "扉页",
        "halftitlepage" to "半扉页","copyright-page" to "版权页","copyright" to "版权页",
        "toc" to "目录","dedication" to "献词","epigraph" to "题词","preface" to "序言",
        "foreword" to "前言","introduction" to "引言","acknowledgments" to "致谢",
        "bibliography" to "参考文献","index" to "索引","colophon" to "刊记")
    private fun elements(root:Element,name:String):List<Element> = root.getElementsByTagNameNS("*",name).let{nodes->
        (0 until nodes.length).mapNotNull{nodes.item(it) as? Element}
    }
    private fun entryPath(value:String):String {
        require(value.isNotBlank()&&!value.startsWith('/')&&!value.contains('\\')&&!value.contains('\u0000')){"EPUB 文件路径无效。"}
        val parts=ArrayDeque<String>()
        for(part in value.split('/'))when(part){"","."->Unit;".."->{require(parts.isNotEmpty()){"EPUB 文件路径越界。"};parts.removeLast()};else->parts.addLast(part)}
        return parts.joinToString("/")
    }
    fun resolve(base:String,href:String):EpubLink? = runCatching {
        val uri=URI("https://epub.invalid/"+encodePath(base)).resolve(href)
        if(uri.scheme!="https"||uri.host!="epub.invalid"||uri.userInfo!=null)return null
        EpubLink(entryPath(uri.path.removePrefix("/")),uri.fragment)
    }.getOrNull()
    fun rootUrl(file:File)="https://appassets.androidplatform.net/epub/${file.name}/"
    fun resource(file:File,path:String):Pair<String,ByteArray>? {
        val extension=path.substringAfterLast('.').lowercase()
        val mime=when(extension){
            "png"->"image/png";"jpg","jpeg"->"image/jpeg";"gif"->"image/gif";"webp"->"image/webp"
            "svg"->"image/svg+xml";"avif"->"image/avif";"bmp"->"image/bmp"
            else->return null
        }
        return mime to ZipFile(file).use{read(it,entryPath(path),MAX_RESOURCE)}
    }
    private fun read(zip:ZipFile,path:String,limit:Int=2_000_000):ByteArray {
        val entry=zip.getEntry(path)?:throw IllegalArgumentException("EPUB 缺少必要文件。")
        require(!entry.isDirectory&&entry.size<=limit){"EPUB 单个文件超过预览限制。"}
        val bytes=zip.getInputStream(entry).use{input->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(output.size()<=limit){val n=input.read(buffer,0,minOf(buffer.size,limit+1-output.size()));if(n<0)break;output.write(buffer,0,n)}
            output.toByteArray()
        }
        require(bytes.size<=limit){"EPUB 单个文件超过预览限制。"}
        return bytes
    }
    private fun text(bytes:ByteArray):String {
        val utf16=bytes.size>1&&((bytes[0]==(-1).toByte()&&bytes[1]==(-2).toByte())||(bytes[0]==(-2).toByte()&&bytes[1]==(-1).toByte()))
        return bytes.toString(if(utf16)Charsets.UTF_16 else Charsets.UTF_8).removePrefix("\uFEFF")
    }
    private fun xml(bytes:ByteArray):Element {
        // EPUB 2 NCX often has an external DOCTYPE. Remove that declaration without
        // fetching it; internal entity declarations are rejected before parsing.
        val source=text(bytes).replace(Regex("<!DOCTYPE[^\\[>]*>",RegexOption.IGNORE_CASE),"")
        require(!Regex("<!DOCTYPE|<!ENTITY",RegexOption.IGNORE_CASE).containsMatchIn(source)){"EPUB 包含不支持的 XML 实体。"}
        val factory=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;isExpandEntityReferences=false}
        for(feature in listOf("http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"))runCatching{factory.setFeature(feature,false)}
        return factory.newDocumentBuilder().apply{setEntityResolver{_,_->InputSource(StringReader(""))}}
            .parse(InputSource(StringReader(source))).documentElement
    }
    private fun pageKindTitle(types:String):String?=types.split(Regex("\\s+")).firstNotNullOfOrNull{pageKindTitles[it.lowercase()]}
    private fun cleanTitle(value:String)=value.replace(Regex("\\s+")," ").trim().take(160).takeIf{it.isNotBlank()}
    private fun pageTitle(zip:ZipFile,path:String):String? = runCatching {
        // Spine entries also include covers and front matter. Read only unmatched page
        // titles within the existing resource bound; never turn a file index into a chapter number.
        val root=xml(read(zip,path))
        val body=elements(root,"body").firstOrNull()
        val semantic=(listOfNotNull(body)+elements(root,"section")).firstNotNullOfOrNull{
            pageKindTitle(it.getAttributeNS("http://www.idpf.org/2007/ops","type"))
        }
        semantic?:(1..6).firstNotNullOfOrNull{level->elements(body?:root,"h$level").firstNotNullOfOrNull{cleanTitle(it.textContent)}}
            ?:elements(root,"head").firstOrNull()?.let{head->elements(head,"title").firstNotNullOfOrNull{cleanTitle(it.textContent)}}
    }.getOrNull()
    fun chapter(file:File,path:String):String=ZipFile(file).use{text(read(it,entryPath(path)))}
    fun open(file:File):EpubBook=ZipFile(file).use{zip->
        val entries=zip.entries();var count=0;val names=hashSetOf<String>()
        while(entries.hasMoreElements()) {
            val e=entries.nextElement();require(++count<=MAX_ENTRIES){"EPUB 文件数量超过预览限制。"}
            val path=entryPath(e.name)
            require(path==e.name.trimEnd('/')&&names.add(path)){"EPUB 包含重复或不安全的文件路径。"}
        }
        require(zip.getEntry("META-INF/encryption.xml")==null){"暂不支持加密或受 DRM 保护的 EPUB。"}
        require(text(read(zip,"mimetype",100)).trim()=="application/epub+zip"){"这不是有效的 EPUB 文件。"}
        val container=xml(read(zip,"META-INF/container.xml"))
        val packagePath=elements(container,"rootfile").firstOrNull()?.getAttribute("full-path")?.let(::entryPath)
            ?:throw IllegalArgumentException("EPUB 缺少书籍描述。")
        val pkg=xml(read(zip,packagePath))
        val metadata=elements(pkg,"metadata").firstOrNull()
        val title=metadata?.let{elements(it,"title").firstOrNull()?.textContent?.trim()}.orEmpty().ifBlank{"EPUB"}
        val author=metadata?.let{elements(it,"creator").joinToString("、"){e->e.textContent.trim()}}.orEmpty()
        val manifest=elements(pkg,"manifest").firstOrNull()?.let{elements(it,"item")}.orEmpty()
        val items=manifest.associateBy{it.getAttribute("id")}
        val spine=elements(pkg,"spine").firstOrNull()?:throw IllegalArgumentException("EPUB 缺少阅读顺序。")
        val paths=elements(spine,"itemref").mapNotNull{ref->
            val item=items[ref.getAttribute("idref")]?:return@mapNotNull null
            if(item.getAttribute("media-type") !in listOf("application/xhtml+xml","text/html"))return@mapNotNull null
            resolve(packagePath,item.getAttribute("href"))?.path
        }.distinct()
        require(paths.isNotEmpty()&&paths.size<=500){"EPUB 没有可阅读的章节，或章节数量超过限制。"}
        val toc=mutableListOf<EpubContents>()
        val pageLabels=linkedMapOf<String,String>()
        for(guide in elements(pkg,"guide"))for(reference in elements(guide,"reference")) {
            val link=resolve(packagePath,reference.getAttribute("href"))?:continue
            val label=pageKindTitle(reference.getAttribute("type"))?:cleanTitle(reference.getAttribute("title"))?:continue
            if(link.path in paths&&link.fragment.isNullOrBlank())pageLabels.putIfAbsent(link.path,label)
        }
        val nav=manifest.firstOrNull{it.getAttribute("properties").split(' ').contains("nav")}
        if(nav!=null) {
            val path=resolve(packagePath,nav.getAttribute("href"))?.path
            if(path!=null) {
                val root=xml(read(zip,path))
                for(landmarks in elements(root,"nav").filter{it.getAttributeNS("http://www.idpf.org/2007/ops","type").split(' ').contains("landmarks")}) {
                    for(a in elements(landmarks,"a")) {
                        val link=resolve(path,a.getAttribute("href"))?:continue
                        val label=pageKindTitle(a.getAttributeNS("http://www.idpf.org/2007/ops","type"))?:cleanTitle(a.textContent)?:continue
                        if(link.path in paths&&link.fragment.isNullOrBlank())pageLabels.putIfAbsent(link.path,label)
                    }
                }
                val navigation=elements(root,"nav").firstOrNull{it.getAttributeNS("http://www.idpf.org/2007/ops","type").split(' ').contains("toc")}
                    ?:elements(root,"nav").firstOrNull()
                if(navigation!=null)for(a in elements(navigation,"a")) {
                    val link=resolve(path,a.getAttribute("href"))?:continue
                    if(link.path !in paths)continue
                    var parent=a.parentNode;var depth=0
                    while(parent!=null&&parent!=navigation){if(parent is Element&&parent.localName=="ol")depth++;parent=parent.parentNode}
                    toc.add(EpubContents(a.textContent.trim().ifBlank{"章节"},link,(depth-1).coerceIn(0,3)))
                }
            }
        }
        if(toc.isEmpty()) {
            val ncx=items[spine.getAttribute("toc")]?:manifest.firstOrNull{it.getAttribute("media-type")=="application/x-dtbncx+xml"}
            val path=ncx?.let{resolve(packagePath,it.getAttribute("href"))?.path}
            if(path!=null)for(point in elements(xml(read(zip,path)),"navPoint")) {
                val src=elements(point,"content").firstOrNull()?.getAttribute("src")?:continue
                val link=resolve(path,src)?:continue
                if(link.path in paths)toc.add(EpubContents(elements(point,"navLabel").firstOrNull()?.textContent?.trim().orEmpty().ifBlank{"章节"},link))
            }
        }
        val chapters=paths.map{path->
            require(zip.getEntry(path)!=null){"EPUB 章节文件缺失。"}
            val links=toc.filter{it.link.path==path}
            val pageName=links.firstOrNull{it.link.fragment.isNullOrBlank()}?.title
                ?:pageLabels[path]?:links.firstOrNull()?.title?:pageTitle(zip,path)?:"未命名页面"
            EpubChapter(path,pageName)
        }
        EpubBook(title,author,chapters,toc.ifEmpty{chapters.map{EpubContents(it.title,EpubLink(it.path))}})
    }
}
