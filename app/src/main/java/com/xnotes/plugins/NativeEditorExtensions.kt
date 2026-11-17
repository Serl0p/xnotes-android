package com.xnotes.plugins

/**
 * Shared native entry points used by paged and infinite editors. The adapters retain existing
 * editor methods (including undo, render invalidation, audio stamping and tool side effects).
 */
class NativeEditorExtensions(
    schedule: (() -> Unit) -> Boolean,
    undo: () -> Unit,
    redo: () -> Unit,
    togglePan: () -> Unit,
    toggleEraser: () -> Unit,
    toggleLasso: () -> Unit,
    togglePrevious: () -> Unit,
    selectPen: () -> Unit,
    onError: (Exception) -> Unit = {},
) {
    val events = EditorEvents(schedule, onError)
    val commands = CommandRegistry { events.publish(EditorEvent.CommandExecuted(it)) }

    init {
        commands.registerBuiltIn("undo", undo)
        commands.registerBuiltIn("redo", redo)
        commands.registerBuiltIn("toggle_pan", togglePan)
        commands.registerBuiltIn("toggle_eraser", toggleEraser)
        commands.registerBuiltIn("toggle_lasso", toggleLasso)
        commands.registerBuiltIn("toggle_previous", togglePrevious)
        // A useful test of adding a new action without changing either gesture dispatcher.
        commands.registerBuiltIn("select_pen", selectPen)
    }

    fun historyChanged(operation: EditorEvent.Operation) {
        if (operation == EditorEvent.Operation.CLEAR) events.discardPending()
        events.publish(EditorEvent.HistoryChanged(operation))
    }
}
