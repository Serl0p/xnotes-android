package com.xnotes.plugins
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PluginSettingsSchemaTest {
    @Test fun schemaChecksDefaultsAndUserValues() {
        val fields=PluginSettingsSchema.read(JSONObject("""{"settingsUi":{"tools":{"type":"tool-list","label":"Tools","min":1,"max":3},"n":{"type":"slider","label":"Count","min":3,"max":9,"step":2},"colors":{"type":"color-list","label":"Colors","min":0,"max":2}}}"""),
            mapOf("tools" to "pen,eraser","n" to "3","colors" to ""))
        assertTrue(fields.getValue("tools").valid("eraser,pen"))
        assertFalse(fields.getValue("tools").valid("pen,pen"))
        assertFalse(fields.getValue("tools").valid("delete"))
        assertFalse(fields.getValue("n").valid("4"))
        assertTrue(fields.getValue("n").valid("9"))
        assertTrue(fields.getValue("colors").valid("#aabbcc,#123456"))
        assertFalse(fields.getValue("colors").valid("#aabbcc,#AABBCC"))
    }
    @Test fun paletteNeedsPermissionAndValidColors() {
        val raw="""{"layout":"radial","title":"Wheel","center":"palette","size":"large","colors":["#aabbcc"],"items":[{"label":"Pen","tool":"pen"}]}"""
        assertTrue(runCatching { ScriptMenuRuntime.parseResult(raw,listOf("pen")) }.isFailure)
        assertEquals("palette",ScriptMenuRuntime.parseResult(raw,listOf("pen"),true).center)
        assertTrue(runCatching { ScriptMenuRuntime.parseResult(raw.replace("#aabbcc","bad"),listOf("pen"),true) }.isFailure)
    }
    @Test fun oldMenuKeepsCloseCenter() {
        assertEquals("close",ScriptMenuRuntime.parseResult("""{"title":"Old","layout":"radial","items":[{"label":"Pen","tool":"pen"}]}""",listOf("pen")).center)
    }
}
