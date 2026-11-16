package com.xnotes.plugins

import org.junit.Assert.*
import org.junit.Test

class DocumentExportsTest {
    private val a = ExportAnnotation("id-1", 3, 8, "highlight", "#ffff00", "A \"quote\".\nNext line", "=1+1", 0, 1000)
    private val doc = ExportDocument("My Book", "My Book.xnote", 10, listOf(a), listOf(ExportText(2, "Typed [[link]]")))

    @Test fun noteAndSourceIndexesRemainDistinct() {
        val p = DocumentExports.render(doc, ExportKind.ANNOTATIONS, ExportOptions())
        assertEquals("My Book - Annotations.md", p.fileName)
        assertTrue(p.content.contains("note p\\. 3"))
        assertTrue(p.content.contains("source PDF p\\. 8"))
        assertTrue(p.content.contains("Next line"))
        assertFalse(p.content.contains("Typed"))
    }
    @Test fun jsonFieldsRespectOptions() {
        val p = DocumentExports.render(doc, ExportKind.ANNOTATIONS, ExportOptions(format = ExportFormat.JSON, pages = false, colors = false, comments = false))
        assertTrue(p.content.contains("\\\"quote\\\""))
        assertFalse(p.content.contains("notePage"))
        assertFalse(p.content.contains("comment"))
    }
    @Test fun csvProtectsFormulaCellsAndQuotes() {
        val p = DocumentExports.render(doc, ExportKind.ANNOTATIONS, ExportOptions(format = ExportFormat.CSV))
        assertTrue(p.content.contains("\"'=1+1\""))
        assertTrue(p.content.contains("\"\"quote\"\""))
    }
    @Test fun companionUsesRelativeLinkAndPlainTypedText() {
        val p = DocumentExports.render(doc, ExportKind.OBSIDIAN, ExportOptions(relativeLink = "../Notes/My Book.xnote"))
        assertTrue(p.content.contains("../Notes/My%20Book.xnote"))
        assertTrue(p.content.contains("Typed \\[\\[link\\]\\]"))
        assertEquals(ExportFormat.MARKDOWN, p.format)
    }
    @Test fun rejectsAbsoluteOrExternalLinks() {
        for (path in listOf("https://example.org/x.xnote", "/foo.xnote", "foo.md")) {
            try { DocumentExports.render(doc, ExportKind.OBSIDIAN, ExportOptions(relativeLink = path)); fail("unsafe link accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun emptyAnnotationsAreRepresented() {
        assertTrue(DocumentExports.render(doc.copy(annotations = emptyList()), ExportKind.ANNOTATIONS, ExportOptions()).content.contains("No native PDF text annotations"))
    }
    @Test fun blankLinkIsOmittedAndCatalogIsUnique() {
        assertFalse(DocumentExports.render(doc, ExportKind.OBSIDIAN, ExportOptions()).content.contains("](<"))
        assertEquals(NativeFeature.entries.size, NativeFeature.entries.map { it.owner }.distinct().size)
        assertEquals("annotation-exporter:export", NativeFeature.ANNOTATIONS.command)
    }
}
