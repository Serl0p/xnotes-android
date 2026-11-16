package com.xnotes.plugins

import com.xnotes.core.model.Document
import com.xnotes.core.model.Labelled
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TextItem

/** Host-only adapter; callers must authorize the feature and resolve the active surface first.
 * Keep model dependencies here, not in formatters or untrusted extension bridges. UI thread only. */
internal object DocumentReadApi {
    fun annotationRows(doc: Document): List<ExportAnnotation> =
        doc.pages.flatMapIndexed { i, page -> page.markups.map { mark ->
            ExportAnnotation(mark.id, i + 1, page.pdfPage?.plus(1), mark.type.id,
                Rgba.toHex(mark.color), mark.text, mark.note, mark.created, mark.modified)
        } }.take(DocumentExports.MAX_ANNOTATIONS)

    fun snapshot(doc: Document, feature: NativeFeature): ExportDocument {
        require(feature == NativeFeature.ANNOTATIONS || feature == NativeFeature.OBSIDIAN || feature == NativeFeature.UNIVERSAL)
        val typed = "document.text.read" in feature.permissions
        val metadata = "document.metadata.read" in feature.permissions
        val marks = mutableListOf<ExportAnnotation>()
        val texts = mutableListOf<ExportText>()
        var size = 0L
        fun account(text: String?) {
            size += text?.length ?: 0
            require(size <= DocumentExports.MAX_CHARS / 2) { "Note is too large for one export" }
        }
        if (typed) {
            doc.flow.plainText().takeIf { it.isNotBlank() }?.let {
                account(it); texts.add(ExportText(null, it))
            }
        }
        for ((i, page) in doc.pages.withIndex()) {
            for (mark in page.markups) {
                require(marks.size < DocumentExports.MAX_ANNOTATIONS) { "Too many annotations for one export" }
                account(mark.text); account(mark.note)
                marks.add(ExportAnnotation(mark.id, i + 1, page.pdfPage?.plus(1),
                    mark.type.id, Rgba.toHex(mark.color), mark.text, mark.note, mark.created, mark.modified))
            }
            if (typed) for (item in page.items) {
                val text = when (item) {
                    is TextItem -> item.text
                    is Labelled -> item.label?.text
                    else -> null
                }
                if (!text.isNullOrBlank()) { account(text); texts.add(ExportText(i + 1, text)) }
            }
        }
        val filename = if (metadata) doc.displayName?.takeIf { it.endsWith(".xnote", ignoreCase = true) } else null
        return ExportDocument(doc.title, filename, doc.pages.size, marks.toList(), texts.toList())
    }
}
