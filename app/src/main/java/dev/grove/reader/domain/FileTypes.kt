package dev.grove.reader.domain

data class PreviewType(val kind: PreviewKind, val mimeType: String)

fun decodeSvgSource(bytes:ByteArray):String {
    fun byteAt(i:Int)=bytes.getOrNull(i)?.toInt()?.and(255)
    val charset=when {
        byteAt(0)==255&&byteAt(1)==254 -> Charsets.UTF_16
        byteAt(0)==254&&byteAt(1)==255 -> Charsets.UTF_16
        byteAt(0)==60&&byteAt(1)==0 -> Charsets.UTF_16LE
        byteAt(0)==0&&byteAt(1)==60 -> Charsets.UTF_16BE
        else -> {
            val declaration=bytes.take(256).toByteArray().toString(Charsets.US_ASCII)
            val encoding=Regex("^\\s*<\\?xml\\s[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]",RegexOption.IGNORE_CASE)
                .find(declaration)?.groupValues?.get(1)
            runCatching{java.nio.charset.Charset.forName(encoding?:"UTF-8")}.getOrDefault(Charsets.UTF_8)
        }
    }
    return bytes.toString(charset)
}

fun imageDownloadName(path:String,mimeType:String):String {
    val extension=when(mimeType) {
        "image/png"->"png";"image/jpeg"->"jpg";"image/gif"->"gif";"image/webp"->"webp"
        "image/bmp"->"bmp";"image/x-icon"->"ico";"image/avif"->"avif";"image/svg+xml"->"svg"
        else->throw IllegalArgumentException("不支持的图片格式")
    }
    val name=path.substringAfterLast('/').substringBeforeLast('.',path.substringAfterLast('/'))
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").trim(' ','.').take(100).ifBlank{"Grove-image"}
    return "$name.$extension"
}

fun previewType(path: String, bytes: ByteArray): PreviewType? {
    fun signature(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 255) == values[it] }
    val extension=path.substringAfterLast('.').lowercase()
    val prefix=bytes.take(1024).toByteArray().toString(Charsets.UTF_8).trimStart('\uFEFF',' ','\n','\r','\t')
    val image = when {
        signature(137,80,78,71,13,10,26,10) -> "image/png"
        signature(255,216,255) -> "image/jpeg"
        prefix.startsWith("GIF87a") || prefix.startsWith("GIF89a") -> "image/gif"
        bytes.size>=12 && bytes.copyOfRange(0,4).toString(Charsets.US_ASCII)=="RIFF" && bytes.copyOfRange(8,12).toString(Charsets.US_ASCII)=="WEBP" -> "image/webp"
        signature(66,77) -> "image/bmp"
        signature(0,0,1,0) -> "image/x-icon"
        extension=="svg" && Regex("<svg(?=[\\s/>])",RegexOption.IGNORE_CASE)
            .containsMatchIn(decodeSvgSource(bytes.take(4096).toByteArray())) -> "image/svg+xml"
        bytes.size>=12 && bytes.copyOfRange(4,12).toString(Charsets.US_ASCII) in listOf("ftypavif","ftypavis") -> "image/avif"
        else -> null
    }
    if(image!=null)return PreviewType(PreviewKind.IMAGE,image)
    if(prefix.startsWith("%PDF-"))return PreviewType(PreviewKind.PDF,"application/pdf")
    if(extension=="epub" && signature(80,75))return PreviewType(PreviewKind.EPUB,"application/epub+zip")
    if(extension in listOf("xlsx","xlsm","xltx","xltm") && signature(80,75))return PreviewType(PreviewKind.SPREADSHEET,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    if(extension=="xls" && signature(208,207,17,224,161,177,26,225))return PreviewType(PreviewKind.SPREADSHEET,"application/vnd.ms-excel")
    if(extension in listOf("csv","tsv"))return PreviewType(PreviewKind.SPREADSHEET,if(extension=="csv")"text/csv" else "text/tab-separated-values")
    return null
}
