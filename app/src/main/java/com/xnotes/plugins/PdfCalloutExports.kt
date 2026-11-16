package com.xnotes.plugins

import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.pow

/** Page links only: PDF++ selection indices cannot be derived reliably from xNotes quads. */
internal object PdfCalloutExports {
    private val palette = linkedMapOf(
        "yellow" to 0xffd700, "orange" to 0xff9e24, "red" to 0xf04a4a,
        "green" to 0x54b878, "blue" to 0x4b91e5, "purple" to 0xa06ad8,
        "pink" to 0xf38cc5, "gray" to 0x999999,
    )
    private val token = Regex("\\{\\{([a-zA-Z]+)\\}\\}")
    private fun colorName(hex: String, overrides: Map<String, String>): String {
        overrides[hex.lowercase()]?.let { return it }
        val rgb = hex.removePrefix("#").toIntOrNull(16) ?: return "yellow"
        fun distance(c: Int) = listOf(16,8,0).sumOf { shift ->
            (((rgb shr shift) and 255) - ((c shr shift) and 255)).toDouble().pow(2)
        }
        return palette.minByOrNull { distance(it.value) }?.key ?: "yellow"
    }

    fun render(doc: ExportDocument, annotations: List<ExportAnnotation>, o: ExportOptions): ExportPayload {
        require(o.pdfPath.length <= 500 && !o.pdfPath.startsWith("/") && !o.pdfPath.contains('\\') &&
            o.pdfPath.none { it.isISOControl() || it in "[]#|<>" || it == ':' } &&
            (o.pdfPath.isBlank() || o.pdfPath.endsWith(".pdf", true))) { "Enter a vault-relative PDF path, or leave empty" }
        require(o.printedOffset in -100000..100000)
        require(o.template.length <= 4000)
        require(o.sidecarAuthor.isNotBlank() && o.sidecarAuthor.length <= 100 && o.sidecarAuthor.none { it.isISOControl() })
        val overrides = o.colorMap.lines().filter { it.isNotBlank() }.associate { line ->
            val bits = line.split('=', limit = 2)
            require(bits.size == 2 && Regex("#[0-9a-fA-F]{6}").matches(bits[0].trim()) &&
                Regex("[a-zA-Z0-9_-]{1,40}").matches(bits[1].trim())) { "Color mapping: #RRGGBB=name, one per line" }
            bits[0].trim().lowercase() to bits[1].trim()
        }
        val stem = doc.title.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().take(120).ifBlank { "Untitled" }
        val filename = "$stem - Export.md"
        val lines = mutableListOf("# " + DocumentExports.markdown(doc.title.replace('\n',' ').replace('\r',' ')), "")
        val side = mutableListOf<String>()
        val sideEnabled = o.sidecar && o.comments && o.layout == ExportLayout.PDF_CALLOUT
        if (o.relativeLink.isNotBlank()) lines += listOf("[Open original xNotes file](<" + DocumentExports.relativePath(o.relativeLink.trim()) + ">)", "")
        if (o.typed && doc.typedText.isNotEmpty()) {
            lines += listOf("## Typed text", "")
            for (entry in doc.typedText) {
                lines += entry.text.lines().map { "> " + DocumentExports.markdown(it) }
                lines += ""
            }
        }
        lines += listOf("## Annotations", "")
        for (a in annotations) {
            val color = if (o.colors) colorName(a.color, overrides) else ""
            val pageLabel = a.pdfPage?.plus(o.printedOffset)?.toString() ?: a.notePage.toString()
            val label = if (o.pages) "p.$pageLabel" else "PDF"
            val link = if (o.pages && a.pdfPage != null && o.pdfPath.isNotBlank())
                "[[${o.pdfPath.trim()}#page=${a.pdfPage}|${o.pdfPath.substringAfterLast('/').removeSuffix(".pdf")}, $label]]"
                else if (o.pages) "Note page ${a.notePage}" + (a.pdfPage?.let { " · PDF page $it" } ?: "") else "Annotation"
            val quote = a.text.ifBlank { "[No selected text stored]" }.replace("\r\n","\n").replace('\r','\n')
                .lines().map { DocumentExports.markdown(it) }
            val comment = if (o.comments && (!sideEnabled || o.inlineComments)) a.comment.orEmpty() else ""
            if (o.layout == ExportLayout.CUSTOM) {
                val values = mapOf("colorName" to color, "colorHex" to if(o.colors) a.color else "", "pdfLink" to link,
                    "text" to quote.joinToString("\n> > "), "comment" to comment.lines().joinToString("\n> ") { DocumentExports.markdown(it) },
                    "notePage" to if(o.pages) a.notePage.toString() else "", "pdfPage" to if(o.pages) a.pdfPage?.toString().orEmpty() else "",
                    "pageLabel" to if(o.pages) pageLabel else "", "id" to a.id, "type" to a.type)
                lines += token.replace(o.template) { m -> values[m.groupValues[1]] ?: m.value }.lines()
                lines += ""
            } else {
                lines += "> [!PDF" + if (color.isBlank()) "] $link" else "|$color] $link"
                val start = lines.size + 1
                val selected = quote.joinToString("\n> > ")
                lines += quote.map { "> > $it" }
                if (sideEnabled && !a.comment.isNullOrBlank()) {
                    // Span of the first excerpt line (up to 4096 characters); exact Markdown source, not rendered text.
                    val anchor = quote.first().take(4096)
                    val hash = MessageDigest.getInstance("SHA-256").digest(anchor.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it.toInt() and 255) }
                    fun q(s: String) = JSONObject.quote(s)
                    side += "  - id: " + q("xnotes-${a.id}-${a.notePage}")
                    side += "    author: " + q(o.sidecarAuthor)
                    side += "    timestamp: " + q(Instant.ofEpochMilli(a.modified).toString())
                    side += "    text: " + q(a.comment)
                    side += "    resolved: false"
                    side += "    line: $start"
                    side += "    end_line: $start"
                    side += "    start_column: 4"
                    side += "    end_column: ${4 + anchor.length}"
                    side += "    selected_text: " + q(anchor)
                    side += "    selected_text_hash: " + q(hash)
                }
                if (o.timestamps) lines += "> " + Instant.ofEpochMilli(a.modified).toString()
                if (comment.isNotBlank()) {
                    lines += ">"
                    lines += comment.lines().map { "> " + DocumentExports.markdown(it) }
                }
                lines += ""
            }
        }
        if (annotations.isEmpty()) lines += "No native PDF text annotations."
        val markdown = lines.joinToString("\n") + "\n"
        val yaml = if (sideEnabled) "mrsf_version: \"1.0\"\ndocument: " + JSONObject.quote(filename) +
            if (side.isEmpty()) "\ncomments: []\n" else "\ncomments:\n" + side.joinToString("\n") + "\n" else null
        require(markdown.length + (yaml?.length ?: 0) <= DocumentExports.MAX_CHARS) { "Export is too large" }
        return ExportPayload(filename, markdown, ExportFormat.MARKDOWN, yaml)
    }
}
