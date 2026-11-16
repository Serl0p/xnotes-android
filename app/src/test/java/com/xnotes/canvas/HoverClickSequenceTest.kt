package com.xnotes.canvas

import com.xnotes.core.geometry.Pt
import org.junit.Assert.*
import org.junit.Test

class HoverClickSequenceTest {
    private val queue = mutableListOf<Runnable>()
    private val result = mutableListOf<String>()
    private val sequence = HoverClickSequence({ queue.add(it) }, { queue.remove(it) }, { action, _ -> result.add(action) })
    private val pt = Pt(1.0, 2.0)

    @Test fun singleWithoutDoubleIsImmediate() {
        sequence.click(pt, "undo", "none")
        assertEquals(listOf("undo"), result)
    }
    @Test fun twoClicksOnlyDeliverDouble() {
        sequence.click(pt, "undo", "redo")
        sequence.click(pt, "undo", "redo")
        assertTrue(queue.isEmpty())
        assertEquals(listOf("redo"), result)
    }
    @Test fun singleAfterWindowAndCancel() {
        sequence.click(pt, "undo", "redo")
        queue.removeAt(0).run()
        assertEquals(listOf("undo"), result)
        sequence.click(pt, "undo", "redo")
        sequence.reset()
        assertTrue(queue.isEmpty())
    }
}
