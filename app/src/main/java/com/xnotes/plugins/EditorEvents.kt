package com.xnotes.plugins

/** Immutable, intentionally small messages. No live model, stroke samples or file handles. */
sealed interface EditorEvent {
    data class CommandExecuted(val id: String) : EditorEvent
    data class HistoryChanged(val operation: Operation) : EditorEvent
    enum class Operation { PUSH, UNDO, REDO, CLEAR }
}

/**
 * UI-thread confined, deferred delivery. The host's scheduler MUST enqueue a later callback
 * (never invoke it inline). This keeps subscribers off the synchronous history/ink path.
 * Best-effort bounded events are notifications, not an authoritative change log.
 */
class EditorEvents(
    private val schedule: (() -> Unit) -> Boolean,
    private val onError: (Exception) -> Unit = {},
    private val capacity: Int = 128,
) {
    init { require(capacity > 0) }
    private data class Listener(val owner: String, val callback: (EditorEvent) -> Unit)
    private val listeners = mutableListOf<Listener>()
    private val pending = ArrayDeque<EditorEvent>()
    private var scheduled = false

    fun subscribe(grant: ExtensionGrant, callback: (EditorEvent) -> Unit): () -> Unit {
        require(ExtensionPermission.OBSERVE_EDITOR in grant.permissions)
        val listener = Listener(grant.owner, callback)
        listeners.add(listener)
        return { listeners.remove(listener); Unit }
    }

    fun unsubscribeOwner(owner: String) {
        listeners.removeAll { it.owner == owner }
        if (listeners.isEmpty()) pending.clear()
    }

    fun discardPending() = pending.clear()

    fun publish(event: EditorEvent) {
        if (listeners.isEmpty()) return
        if (pending.size >= capacity) pending.removeFirst()
        pending.addLast(event)
        requestDrain()
    }

    private fun requestDrain() {
        if (scheduled || pending.isEmpty()) return
        scheduled = true
        if (!schedule(::drain)) {
            scheduled = false
            pending.clear()
        }
    }

    private fun drain() {
        try {
            // Process a snapshot-sized batch so events emitted by listeners wait for another turn.
            repeat(pending.size) {
                val event = pending.removeFirstOrNull() ?: return@repeat
                for (listener in listeners.toList()) {
                    if (listener !in listeners) continue
                    try {
                        listener.callback(event)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
            }
        } finally {
            scheduled = false
            requestDrain()
        }
    }
}
