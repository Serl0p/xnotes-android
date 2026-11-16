package com.xnotes.plugins

import java.net.URLEncoder
import java.time.Instant
import java.nio.charset.StandardCharsets

/** Detached, read-only values. No live note, URI, model objects or file handles. */
internal data class ExportAnnotation(
    val id: String, val notePage: Int, val pdfPage: Int?, val type: String,
    val color: String, val text: String, val comment: String?, val created: Long, val modified: Long,
)
internal data class ExportText(val page: Int?, val text: String)
internal data class ExportDocument(
    val title: String, val fileName: String?, val pageCount: Int,
    val annotations: List<ExportAnnotation>, val typedText: List<ExportText>,
)
internal enum class ExportKind(val native: NativeFeature) {
    ANNOTATIONS(NativeFeature.ANNOTATIONS), OBSIDIAN(NativeFeature.OBSIDIAN), UNIVERSAL(NativeFeature.UNIVERSAL);
    val feature get() = native.key
    val command get() = native.command
    val label get() = native.label
}
internal enum class ExportFormat(val extension: String, val label: String) {
    MARKDOWN("md", "Markdown"), TEXT("txt", "Plain text"), JSON("json", "JSON"), CSV("csv", "CSV"),
}
internal enum class ExportGroup(val label: String) { PAGE("Page"), COLOR("Color"), TYPE("Type"), TIME("Chronological") }
internal enum class ExportLayout(val label: String) { STANDARD("Standard"), PDF_CALLOUT("PDF++ callouts"), CUSTOM("Custom template") }
internal data class ExportOptions(
    val format: ExportFormat = ExportFormat.MARKDOWN,
    val group: ExportGroup = ExportGroup.PAGE,
    val pages: Boolean = true, val colors: Boolean = true, val comments: Boolean = true,
    val timestamps: Boolean = false, val typed: Boolean = true,
    val relativeLink: String = "",
    val includeAnnotations: Boolean = true,
    val layout: ExportLayout = ExportLayout.STANDARD,
    val pdfPath: String = "",
    val printedOffset: Int = 0,
    val colorMap: String = "",
    val sidecar: Boolean = false,
    val sidecarAuthor: String = "Me",
    val inlineComments: Boolean = true,
    val template: String = "> [!PDF|{{colorName}}] {{pdfLink}}\n> > {{text}}\n> {{comment}}",
)
internal data class ExportPayload(val fileName: String, val content: String, val format: ExportFormat, val sidecar: String? = null) {
    val suggestedName: String get() = if (sidecar == null) fileName else fileName.removeSuffix(".md") + ".zip"
    fun writeTo(output: java.io.OutputStream) {
        val review = sidecar
        if (review == null) output.write(content.toByteArray(Charsets.UTF_8))
        else java.util.zip.ZipOutputStream(output).use { zip ->
            for ((name, text) in listOf(fileName to content, "$fileName.review.yaml" to review)) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}

internal object DocumentExports {
    const val MAX_CHARS = 4_000_000
    const val MAX_ANNOTATIONS = 10000

    fun render(doc: ExportDocument, kind: ExportKind, opts: ExportOptions): ExportPayload {
        val format = if (kind == ExportKind.OBSIDIAN) ExportFormat.MARKDOWN else opts.format
        require(doc.annotations.size <= MAX_ANNOTATIONS) { "Too many annotations for a single export" }
        val annotations = if (opts.includeAnnotations) doc.annotations else emptyList()
        val selected = when (opts.group) {
            ExportGroup.PAGE -> annotations.sortedWith(compareBy({ it.notePage }, { it.created }, { it.id }))
            ExportGroup.COLOR -> annotations.sortedWith(compareBy({ it.color }, { it.notePage }, { it.created }))
            ExportGroup.TYPE -> annotations.sortedWith(compareBy({ it.type }, { it.notePage }, { it.created }))
            ExportGroup.TIME -> annotations.sortedBy { it.created }
        }
        if (kind == ExportKind.UNIVERSAL && format == ExportFormat.MARKDOWN && opts.layout != ExportLayout.STANDARD)
            return PdfCalloutExports.render(doc, selected, opts)
        val content = when (format) {
            ExportFormat.MARKDOWN, ExportFormat.TEXT -> human(doc, kind, opts, selected, format == ExportFormat.MARKDOWN)
            ExportFormat.JSON -> json(doc, opts, selected)
            ExportFormat.CSV -> csv(opts, selected)
        }
        require(content.length <= MAX_CHARS) { "Export is too large; no file was written" }
        val stem = doc.title.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '_' else it }.joinToString("").trim().take(120).ifBlank { "Untitled" }
        val suffix = when (kind) {
            ExportKind.OBSIDIAN -> " - Companion"
            ExportKind.UNIVERSAL -> " - Export"
            else -> " - Annotations"
        }
        return ExportPayload(stem + suffix + "." + format.extension, content, format)
    }

    private fun group(a: ExportAnnotation, o: ExportOptions) = when (o.group) {
        ExportGroup.PAGE -> if (o.pages) "Note page ${a.notePage}" else "Annotations"
        ExportGroup.COLOR -> if (o.colors) a.color else "Annotations"
        ExportGroup.TYPE -> a.type
        ExportGroup.TIME -> "Chronological order"
    }

    private fun human(doc: ExportDocument, kind: ExportKind, o: ExportOptions,
                      items: List<ExportAnnotation>, md: Boolean) = buildString {
        fun heading(s: String, level: Int) {
            val oneLine = s.replace('\n', ' ').replace('\r', ' ')
            append(if (md) "#".repeat(level) + " " + markdown(oneLine) else oneLine)
            append("\n\n")
        }
        fun quote(s: String) {
            for (line in s.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
                if (md) append("> ").append(markdown(line)) else append(line)
                append('\n')
            }
            append('\n')
        }
        heading(doc.title, 1)
        if (kind == ExportKind.OBSIDIAN || kind == ExportKind.UNIVERSAL) {
            val link = o.relativeLink.trim()
            if (link.isNotBlank()) append("[Open original xNotes file](<").append(relativePath(link)).append(">)\n\n")
            else if (kind == ExportKind.OBSIDIAN) append("No original note link specified.\n\n")
            append("Note pages: ").append(doc.pageCount).append("\n\n")
            if (o.typed && doc.typedText.isNotEmpty()) {
                heading("Typed text", 2)
                for (entry in doc.typedText) {
                    entry.page?.takeIf { o.pages }?.let { heading("Note page $it", 3) }
                    quote(entry.text)
                }
            }
        }
        heading("Annotations", 2)
        if (items.isEmpty()) append("No native PDF text annotations.\n")
        var prev: String? = null
        for (a in items) {
            val section = group(a, o)
            if (section != prev) { heading(section, 3); prev = section }
            val meta = mutableListOf(a.type)
            if (o.pages) {
                meta += "note p. ${a.notePage}"
                a.pdfPage?.let { meta += "source PDF p. $it" }
            }
            if (o.colors) meta += a.color
            if (o.timestamps) {
                meta += "created " + Instant.ofEpochMilli(a.created).toString()
                meta += "modified " + Instant.ofEpochMilli(a.modified).toString()
            }
            append(if (md) markdown(meta.joinToString(" · ")) else meta.joinToString(" · ")).append("\n\n")
            quote(a.text.ifBlank { "[No selected text stored]" })
            if (o.comments && !a.comment.isNullOrBlank()) { append("Comment:\n"); quote(a.comment) }
        }
    }

    private fun fields(a: ExportAnnotation, o: ExportOptions): List<Pair<String, Any?>> = buildList {
        add("id" to a.id); add("type" to a.type); add("text" to a.text)
        if (o.pages) { add("notePage" to a.notePage); add("pdfPage" to a.pdfPage) }
        if (o.colors) add("color" to a.color)
        if (o.comments) add("comment" to a.comment)
        if (o.timestamps) { add("created" to Instant.ofEpochMilli(a.created).toString()); add("modified" to Instant.ofEpochMilli(a.modified).toString()) }
    }
    private fun json(doc: ExportDocument, o: ExportOptions, items: List<ExportAnnotation>) = buildString {
        append("{\n  \"title\": ").append(jsonValue(doc.title)).append(",\n  \"annotations\": [")
        items.forEachIndexed { i, a ->
            if (i > 0) append(',')
            append("\n    {").append(fields(a, o).joinToString(", ") { (k, v) -> jsonValue(k) + ": " + jsonValue(v) }).append('}')
        }
        append("\n  ]\n}\n")
    }
    private fun csv(o: ExportOptions, items: List<ExportAnnotation>) = buildString {
        val header = fields(ExportAnnotation("", 1, null, "", "", "", null, 0, 0), o).map { it.first }
        append(header.joinToString(",") { csvCell(it) }).append("\r\n")
        for (a in items) append(fields(a, o).joinToString(",") { csvCell(it.second?.toString().orEmpty()) }).append("\r\n")
    }
    private fun csvCell(s: String): String {
        val dangerous = s.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || s.firstOrNull() in listOf('\t','\r','\n')
        val safe = if (dangerous) "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }
    private fun jsonValue(v: Any?): String = when (v) {
        null -> "null"
        is Number -> v.toString()
        else -> buildString {
            append('"')
            for (c in v.toString()) when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
    }
    internal fun markdown(s: String) = buildString {
        for (c in s) { if (c in "\\`*_{}[]()#+-.!|<>~&=") append('\\'); append(c) }
    }
    internal fun relativePath(path: String): String {
        require(path.length <= 500 && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') &&
            path.none { it.isISOControl() } && path.endsWith(".xnote", ignoreCase = true)) { "Enter a relative .xnote path" }
        require(path.split('/').none { it.isBlank() }) { "Invalid relative path" }
        return path.split('/').joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20") }
    }
}
