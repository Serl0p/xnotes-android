package com.xnotes.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xnotes.core.model.MarkupType

/**
 * Native document operations are intentionally behind a small port. No plugin script is
 * given this object, the open Document, storage paths, or mutable note models.
 *
 * Implementations MUST check the active paged note and validate permissions before
 * editing; the coordinator also checks feature activation at each public entry point.
 */
internal interface PluginDocumentPort {
    val active: Boolean
    fun featureEnabled(feature: String): Boolean
    fun executeNative(feature: NativeFeature, action: () -> Unit): Boolean
    fun annotationRows(): List<ExportAnnotation>
    fun editAnnotation(page: Int, id: String, comment: String?, remove: Boolean)
    fun captureSelection(): Boolean
    fun discardSelection()
    fun applySelectionComment(text: String, type: MarkupType): Boolean
    fun snapshot(kind: ExportKind): ExportDocument
    fun openSidebar()
    fun report(message: String)
}

/**
 * Pane-scoped plugin document UI state; all model-specific work belongs to the port.
 * Delegates are stable across replacement of xNotes' native document implementation.
 */
internal class PluginDocumentSession(private val port: PluginDocumentPort) {
    var panel by mutableStateOf<String?>(null)
        private set
    private var annotationsRevision by mutableStateOf(0)

    var commentOpen by mutableStateOf(false)
        private set

    var exportRequest by mutableStateOf<Pair<ExportKind, ExportDocument>?>(null)
        private set

    fun openPanel(id: String) {
        val feature = NativeFeature.ADVANCED_ANNOTATIONS
        if (!port.active || !port.featureEnabled(feature.key) || id != feature.command) return
        port.executeNative(feature) {
            panel = id
            port.openSidebar()
        }
    }

    fun closePanel() { panel = null }

    /** Recompose a visible annotation sidebar on undo/redo and markup changes. */
    fun onHistoryChanged() {
        if (panel != null) annotationsRevision++
    }

    fun annotationRows(): List<ExportAnnotation> {
        @Suppress("UNUSED_VARIABLE") val observed = annotationsRevision
        if (!port.active || !port.featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key)) return emptyList()
        return port.annotationRows()
    }

    fun editAnnotation(page: Int, id: String, comment: String?, remove: Boolean = false) {
        if (!port.active || !port.featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key)) return
        port.editAnnotation(page, id, comment, remove)
    }

    fun runSelectionAction(id: String) {
        if (id != "advanced-pdf-annotations:comment" ||
            !port.active || !port.featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key)) return
        if (port.captureSelection()) commentOpen = true
    }

    fun dismissComment() {
        port.discardSelection()
        commentOpen = false
    }

    fun saveComment(text: String, type: MarkupType) {
        // The port owns the captured selection and checks document identity before mutation.
        if (commentOpen && port.active && port.featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key) &&
            text.isNotBlank()) port.applySelectionComment(text.take(20_000), type)
        dismissComment()
    }

    fun resetPanels() {
        dismissComment()
        closePanel()
    }

    fun requestExport(kind: ExportKind) {
        if (!port.active || !port.featureEnabled(kind.feature)) return
        port.executeNative(kind.native) {
            runCatching { port.snapshot(kind) }
                .onSuccess { exportRequest = kind to it }
                .onFailure { port.report(it.message ?: "Cannot prepare note export") }
        }
    }

    fun dismissExport() { exportRequest = null }
}
