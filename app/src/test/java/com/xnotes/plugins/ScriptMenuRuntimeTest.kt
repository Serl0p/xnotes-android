package com.xnotes.plugins

import org.junit.Assert.*
import org.junit.Test

class ScriptMenuRuntimeTest {
    @Test fun validMenu() {
        val result = ScriptMenuRuntime.parseResult("""{"title":"Tools","items":[{"label":"Pen","tool":"pen"}]}""", listOf("pen"))
        assertEquals("pen", result.items.single().tool)
    }
    @Test fun rejectsUnknownToolAndBridge() {
        for (raw in listOf(
            """{"title":"Tools","items":[{"label":"File","tool":"file"}]}""",
            """{"title":"Tools","items":[{"label":"Pen","tool":"pen","url":"https://example.net"}]}""",
            """{"title":"Tools","items":[]}""",
        )) {
            assertTrue(runCatching { ScriptMenuRuntime.parseResult(raw, listOf("pen")) }.isFailure)
        }
    }
}
