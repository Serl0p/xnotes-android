package com.xnotes.ui

import com.xnotes.core.model.Document
import com.xnotes.core.model.MarkupType
import com.xnotes.core.pdf.TextSelection
import com.xnotes.plugins.DocumentAnnotationEdits
import com.xnotes.plugins.DocumentReadApi
import com.xnotes.plugins.ExportAnnotation
import com.xnotes.plugins.ExportDocument
import com.xnotes.plugins.ExportKind
import com.xnotes.plugins.NativeFeature
import com.xnotes.plugins.PluginDocumentPort

/**
 * The only editor-aware document plugin adapter. No live Document or selection escapes
 * into JavaScript or an .xplugin; the plugin host deals with detached export DTOs.
 */
internal class EditorPluginDocumentPort(private val editor: Editor) : PluginDocumentPort {
    private val activeDocument: Document?
        get() = editor.state.document.takeIf { editor.noteOpen && !editor.canvasOpen }

    override val active: Boolean get() = activeDocument != null

    override fun featureEnabled(feature: String): Boolean = editor.declarativePlugins.featureEnabled(feature)

    override fun executeNative(feature: NativeFeature, action: () -> Unit): Boolean =
        editor.declarativePlugins.executeNative(feature, editor.extensions.commands, action)

    override fun annotationRows(): List<ExportAnnotation> =
        activeDocument?.let(DocumentReadApi::annotationRows).orEmpty()

    override fun editAnnotation(page: Int, id: String, comment: String?, remove: Boolean) {
        val document = activeDocument ?: return
        if (!featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key)) return
        val edit = DocumentAnnotationEdits.command(document, page, id, comment, remove) ?: return
        editor.applyMarkupEdit(edit)
    }

    /** Hold a note identity with the selection so an old modal cannot edit a newly opened note. */
    private var selected: Pair<Document, TextSelection>? = null

    override fun captureSelection(): Boolean {
        val document = activeDocument ?: return false
        val selection = editor.pdfText.selection ?: return false
        selected = document to selection
        return true
    }

    override fun discardSelection() { selected = null }

    override fun applySelectionComment(text: String, type: MarkupType): Boolean {
        val (owner, selection) = selected ?: return false
        if (owner !== activeDocument || !featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key) ||
            text.isBlank()) return false
        // readPdfTexts can finish later; recheck the document and grant at commit time too.
        editor.markSelection(selection, type, text.take(20_000)) {
            owner === activeDocument && featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key)
        }
        return true
    }

    override fun snapshot(kind: ExportKind): ExportDocument {
        val document = requireNotNull(activeDocument) { "No active paged note" }
        check(featureEnabled(kind.feature)) { "Document export is not enabled" }
        return DocumentReadApi.snapshot(document, kind.native)
    }

    override fun openSidebar() { editor.sidebarVisible = true }
    override fun report(message: String) { editor.say(message) }
}
