package com.xnotes.plugins

import org.junit.Assert.*
import org.junit.Test

class ScriptMenuRuntimeTest {
    @Test fun api5RadialIsValidated() {
        val result = ScriptMenuRuntime.parseResult("""{"title":"Tools","layout":"radial","items":[{"label":"Pen","tool":"pen"}]}""", listOf("pen"))
        assertEquals("radial", result.layout)
        assertTrue(runCatching { ScriptMenuRuntime.parseResult("""{"title":"Tools","layout":"html","items":[{"label":"Pen","tool":"pen"}]}""", listOf("pen")) }.isFailure)
    }

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
