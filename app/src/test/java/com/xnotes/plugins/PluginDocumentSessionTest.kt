package com.xnotes.plugins

import com.xnotes.core.model.MarkupType
import org.junit.Assert.*
import org.junit.Test

class PluginDocumentSessionTest {
    private class FakePort : PluginDocumentPort {
        override var active: Boolean = true
        val enabled = mutableSetOf(NativeFeature.ADVANCED_ANNOTATIONS.key, NativeFeature.UNIVERSAL.key)
        var allowSelection = true
        var sidebarOpened = 0
        var captured = 0
        var discarded = 0
        var saves = mutableListOf<String>()
        var edits = 0
        var snapshots = 0
        var reports = mutableListOf<String>()
        val rows = listOf(ExportAnnotation("mark", 1, 3, "highlight", "#eeaa00", "text", "note", 1L, 2L))
        override fun featureEnabled(feature: String) = feature in enabled
        override fun executeNative(feature: NativeFeature, action: () -> Unit): Boolean {
            if (!active || !featureEnabled(feature.key)) return false
            action()
            return true
        }
        override fun annotationRows() = rows
        override fun editAnnotation(page: Int, id: String, comment: String?, remove: Boolean) { edits++ }
        override fun captureSelection(): Boolean {
            captured++
            return allowSelection
        }
        override fun discardSelection() { discarded++ }
        override fun applySelectionComment(text: String, type: MarkupType): Boolean {
            saves += "${type.id}:$text"
            return true
        }
        override fun snapshot(kind: ExportKind): ExportDocument {
            snapshots++
            return ExportDocument("Test", null, 1, rows, emptyList())
        }
        override fun openSidebar() { sidebarOpened++ }
        override fun report(message: String) { reports += message }
    }

    @Test fun panelAndEditingUseCurrentPermissions() {
        val port = FakePort()
        val session = PluginDocumentSession(port)
        session.openPanel("advanced-pdf-annotations:open")
        assertEquals("advanced-pdf-annotations:open", session.panel)
        assertEquals(1, port.sidebarOpened)
        assertEquals(port.rows, session.annotationRows())
        session.editAnnotation(1, "mark", "changed")
        assertEquals(1, port.edits)
        session.onHistoryChanged()
        port.enabled.remove(NativeFeature.ADVANCED_ANNOTATIONS.key)
        assertTrue(session.annotationRows().isEmpty())
        session.editAnnotation(1, "mark", null, true)
        assertEquals(1, port.edits)
        session.resetPanels()
        assertNull(session.panel)
        assertFalse(session.commentOpen)
    }

    @Test fun selectionIsCapturedAndReleasedOnSaveOrCancel() {
        val port = FakePort()
        val session = PluginDocumentSession(port)
        port.allowSelection = false
        session.runSelectionAction("advanced-pdf-annotations:comment")
        assertFalse(session.commentOpen)
        port.allowSelection = true
        session.runSelectionAction("advanced-pdf-annotations:comment")
        assertTrue(session.commentOpen)
        session.saveComment("hello", MarkupType.HIGHLIGHT)
        assertEquals(listOf("highlight:hello"), port.saves)
        assertFalse(session.commentOpen)
        assertEquals(1, port.discarded)
        session.runSelectionAction("advanced-pdf-annotations:comment")
        port.enabled.clear()
        session.saveComment("not allowed", MarkupType.UNDERLINE)
        assertEquals(1, port.saves.size)
        assertEquals(2, port.discarded)
    }

    @Test fun exportReturnsDetachedSnapshotAndDoesNotBypassSafeMode() {
        val port = FakePort()
        val session = PluginDocumentSession(port)
        session.requestExport(ExportKind.UNIVERSAL)
        assertEquals("Test", session.exportRequest?.second?.title)
        assertEquals(1, port.snapshots)
        port.enabled.clear()
        session.requestExport(ExportKind.UNIVERSAL)
        assertEquals(1, port.snapshots)
        session.dismissExport()
        assertNull(session.exportRequest)
        port.active = false
        session.openPanel("advanced-pdf-annotations:open")
        assertNull(session.panel)
    }
}
