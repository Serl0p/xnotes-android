package com.xnotes.plugins

import org.junit.Assert.*
import org.junit.Test

class PdfCalloutExportsTest {
    private val annotation = ExportAnnotation("a", 3, 2, "highlight", "#ffd700", "A passage", "My comment", 0, 1000)
    private val doc = ExportDocument("Test", "Test.xnote", 8, listOf(annotation), emptyList())
    @Test fun pageAndPrintedLabelAreSeparate() {
        val result = DocumentExports.render(doc, ExportKind.UNIVERSAL, ExportOptions(layout = ExportLayout.PDF_CALLOUT, pdfPath = "Books/Test.pdf", printedOffset = 140))
        assertTrue(result.content.contains("[[Books/Test.pdf#page=2|Test, p.142]]"))
        assertFalse(result.content.contains("selection="))
    }
    @Test fun sidecarTargetsActualMarkdown() {
        val result = DocumentExports.render(doc, ExportKind.UNIVERSAL, ExportOptions(layout = ExportLayout.PDF_CALLOUT, sidecar = true, inlineComments = false))
        assertNotNull(result.sidecar)
        assertTrue(result.sidecar!!.contains("selected_text: \"A passage\""))
        assertFalse(result.content.contains("My comment"))
        assertTrue(result.suggestedName.endsWith(".zip"))
        val expected = result.content.lines().indexOf("> > A passage") + 1
        assertTrue(result.sidecar!!.contains("line: $expected"))
    }
    @Test fun templateIsLiteralSubstitution() {
        val result = DocumentExports.render(doc, ExportKind.UNIVERSAL, ExportOptions(layout = ExportLayout.CUSTOM, template = "{{notePage}}: {{text}}"))
        assertTrue(result.content.contains("3: A passage"))
        assertNull(result.sidecar)
    }
}
