package com.xnotes.plugins

import com.xnotes.core.history.Command
import com.xnotes.core.history.History
import org.junit.Assert.*
import org.junit.Test

class NativeExtensionsTest {
    private val grant = ExtensionGrant("example", setOf(
        ExtensionPermission.EXECUTE_COMMAND, ExtensionPermission.OBSERVE_EDITOR,
    ))

    @Test fun sharedCommandsDispatchToTheirOwnSurface() {
        val calls = mutableListOf<String>()
        fun host(surface: String) = NativeEditorExtensions(
            schedule = { true },
            undo = { calls.add("$surface:undo") },
            redo = {}, togglePan = {}, toggleEraser = {}, toggleLasso = {}, togglePrevious = {},
            selectPen = { calls.add("$surface:pen") },
        )
        val page = host("page")
        val canvas = host("canvas")
        assertTrue(page.commands.execute("undo"))
        assertTrue(canvas.commands.execute("select_pen"))
        assertFalse(page.commands.execute("none"))
        assertFalse(canvas.commands.execute("unknown"))
        assertEquals(listOf("page:undo", "canvas:pen"), calls)
    }

    @Test fun namespacedCommandsAndPermissions() {
        val registry = CommandRegistry()
        var runs = 0
        registry.registerBuiltIn("undo") { runs++ }
        val id = registry.registerExtension(grant, "undo") { runs += 10 }
        assertEquals("example:undo", id)
        val other = ExtensionGrant("other", setOf(ExtensionPermission.EXECUTE_COMMAND))
        assertFalse(registry.executeForExtension(other, id))
        assertFalse(registry.executeForExtension(ExtensionGrant("denied", emptySet()), "undo"))
        assertTrue(registry.executeForExtension(grant, id))
        assertTrue(registry.execute("undo"))
        registry.unregisterExtension(grant.owner)
        assertFalse(registry.execute(id))
        assertEquals(11, runs)
    }

    @Test fun duplicateIdsAndRecursiveExecutionAreRejected() {
        val registry = CommandRegistry()
        registry.registerBuiltIn("undo") { assertFalse(registry.execute("undo")) }
        assertTrue(registry.execute("undo"))
        try {
            registry.registerBuiltIn("undo") {}
            fail("Duplicate command accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun eventsAreDeferredBoundedAndIsolateExceptions() {
        val jobs = ArrayDeque<() -> Unit>()
        val seen = mutableListOf<EditorEvent>()
        var errors = 0
        val events = EditorEvents({ jobs.addLast(it); true }, { errors++ }, capacity = 2)
        events.subscribe(grant) { error("test failure") }
        val unsubscribe = events.subscribe(grant) { seen.add(it) }
        val messages = (1..3).map { EditorEvent.CommandExecuted("command$it") }
        messages.forEach(events::publish)
        assertTrue(seen.isEmpty())
        assertEquals(1, jobs.size)
        jobs.removeFirst().invoke()
        assertEquals(messages.drop(1), seen)
        assertEquals(2, errors)
        unsubscribe()
        events.publish(messages.first())
        jobs.removeFirst().invoke()
        assertEquals(2, seen.size)
    }

    @Test fun historyNotificationPreservesAudioCallbackAndStackOrder() {
        val history = History()
        val changes = mutableListOf<Pair<History.Change, Boolean>>()
        var stamped = 0
        var count = 1
        val edit = object : Command {
            override fun redo() { count++ }
            override fun undo() { count-- }
        }
        history.onPush = { stamped++; assertFalse(history.canUndo) }
        history.onChanged = { changes.add(it to history.canUndo) }
        history.push(edit)
        history.undo()
        history.undo()
        history.redo()
        history.clear()
        assertEquals(1, count)
        assertEquals(1, stamped)
        assertEquals(listOf(
            History.Change.PUSH to true,
            History.Change.UNDO to false,
            History.Change.REDO to true,
            History.Change.CLEAR to false,
        ), changes)
    }

    @Test fun clearDropsQueuedHistoryFromPreviousDocument() {
        val tasks = ArrayDeque<() -> Unit>()
        val host = NativeEditorExtensions(
            schedule = { tasks.addLast(it); true },
            undo = {}, redo = {}, togglePan = {}, toggleEraser = {}, toggleLasso = {},
            togglePrevious = {}, selectPen = {},
        )
        val seen = mutableListOf<EditorEvent>()
        host.events.subscribe(grant) { seen.add(it) }
        host.historyChanged(EditorEvent.Operation.PUSH)
        host.historyChanged(EditorEvent.Operation.CLEAR)
        tasks.removeFirst().invoke()
        assertEquals(listOf(EditorEvent.HistoryChanged(EditorEvent.Operation.CLEAR)), seen)
    }

    @Test fun rejectedSchedulingDoesNotWedgeTheQueue() {
        var accept = false
        val tasks = ArrayDeque<() -> Unit>()
        val seen = mutableListOf<EditorEvent>()
        val events = EditorEvents({ if (accept) { tasks.addLast(it); true } else false })
        events.subscribe(grant) { seen.add(it) }
        events.publish(EditorEvent.CommandExecuted("lost"))
        accept = true
        val expected = EditorEvent.CommandExecuted("next")
        events.publish(expected)
        tasks.removeFirst().invoke()
        assertEquals(listOf(expected), seen)
    }
}
