package com.xnotes.plugins

import com.xnotes.core.model.Document
import com.xnotes.core.model.MarkupType
import com.xnotes.core.model.Rgba
import com.xnotes.core.model.TextMarkup
import com.xnotes.core.pdf.TextQuad
import org.junit.Assert.*
import org.junit.Test

class DocumentAnnotationEditsTest {
    private fun fixture(): Pair<Document, TextMarkup> {
        val doc = Document.blank(count = 2)
        val markup = TextMarkup("a1", MarkupType.HIGHLIGHT, Rgba(200, 150, 20), 0.5,
            listOf(TextQuad(1f, 2f, 3f, 4f, 0)), "selected", "original", 1L, 1L)
        doc.pages[0].markups = listOf(markup)
        return doc to markup
    }

    @Test fun editPreservesAnnotationIdentityAndNativeUndo() {
        val (doc, original) = fixture()
        val cmd = requireNotNull(DocumentAnnotationEdits.command(doc, 1, "a1", "line 1\r\nline 2", false))
        cmd.redo()
        val modified = doc.pages[0].markups.single()
        assertEquals(original.id, modified.id)
        assertEquals(original.type, modified.type)
        assertEquals("line 1\nline 2", modified.note)
        cmd.undo()
        assertSame(original, doc.pages[0].markups.single())
    }

    @Test fun deletingUsesOriginalUndoableMarkupCommand() {
        val (doc, original) = fixture()
        val cmd = requireNotNull(DocumentAnnotationEdits.command(doc, 1, "a1", null, true))
        cmd.redo()
        assertTrue(doc.pages[0].markups.isEmpty())
        cmd.undo()
        assertSame(original, doc.pages[0].markups.single())
    }

    @Test fun invalidAndUnchangedAnnotationsDoNotGenerateCommands() {
        val (doc, _) = fixture()
        assertNull(DocumentAnnotationEdits.command(doc, 0, "a1", "note", false))
        assertNull(DocumentAnnotationEdits.command(doc, 3, "a1", "note", false))
        assertNull(DocumentAnnotationEdits.command(doc, 1, "unknown", "note", false))
        assertNull(DocumentAnnotationEdits.command(doc, 1, "a1", "original", false))
        val truncation = requireNotNull(DocumentAnnotationEdits.command(doc, 1, "a1", "x".repeat(20_100), false))
        truncation.redo()
        assertEquals(20_000, doc.pages[0].markups.single().note?.length)
    }
}
