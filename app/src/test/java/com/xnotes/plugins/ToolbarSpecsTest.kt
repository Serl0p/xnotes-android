package com.xnotes.plugins

import com.xnotes.core.tools.ToolbarItem
import org.junit.Assert.*
import org.junit.Test

class ToolbarSpecsTest {
    @Test fun nativeHomeIsAlwaysFirstAndUnsupportedToolsAreFiltered() {
        val plan = ToolbarSpecs.parse("""{"rows":[["pen","markup"],[{"type":"width","value":3}]],"collapsible":true}""", true, false)
        assertEquals(ToolbarNode.Native(ToolbarItem.HOME), plan.rows[0][0])
        assertEquals(ToolbarNode.Collapse, plan.rows[0][1])
        assertFalse(plan.rows.flatten().contains(ToolbarNode.Native(ToolbarItem.MARKUP)))
        assertEquals(2, plan.rows.size)
    }
    @Test fun rejectsUnknownControlsAndOutOfBoundsProperties() {
        for (raw in listOf("""{"rows":[["bad"]]}""","""{"rows":[[{"type":"web"}]]}""",
            """{"rows":[["pen"]],"style":{"spacing":99}}""","""{"rows":[["pen","pen"]]}""",
            """{"rows":[[{"type":"command","id":"core:delete","label":"X"}]]}""")) {
            assertTrue(raw, runCatching { ToolbarSpecs.parse(raw,false,true) }.isFailure)
        }
    }
    @Test fun perSurfaceOverridesAndStyle() {
        val plan = ToolbarSpecs.parse("""{"paged":{"rows":[["pen"]]},"pdf":{"rows":[["markup"]]},"infinite":{"rows":[["pan"]]},"style":{"position":"bottom"}}""", false,true)
        assertTrue(plan.rows.flatten().contains(ToolbarNode.Native(ToolbarItem.MARKUP)))
        assertEquals("bottom",plan.style.position)
    }
}
