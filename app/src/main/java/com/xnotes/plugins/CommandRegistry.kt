package com.xnotes.plugins

/** Capabilities for the first, native-only extension API. These are not Android permissions. */
enum class ExtensionPermission { EXECUTE_COMMAND, OBSERVE_EDITOR }

/**
 * Host-issued grants will eventually come from the plugin manager. For now only trusted
 * Kotlin code constructs them; this is a policy model, NOT a sandbox or a security boundary.
 */
data class ExtensionGrant(val owner: String, val permissions: Set<ExtensionPermission>) {
    init { require(OWNER.matches(owner) && owner != "core") }
    companion object {
        private val OWNER = Regex("[a-z][a-z0-9.-]{0,63}")
    }
}

/** One registry belongs to one editor surface. All calls must run on the editor's UI thread. */
class CommandRegistry(private val onExecuted: (String) -> Unit = {}) {
    private data class Entry(val owner: String?, val action: () -> Unit)
    private val entries = linkedMapOf<String, Entry>()
    private val running = mutableSetOf<String>()

    fun registerBuiltIn(id: String, action: () -> Unit) {
        require(LOCAL_ID.matches(id))
        require(id !in entries) { "Duplicate command: $id" }
        entries[id] = Entry(null, action)
    }

    /** Registered commands are namespaced; disabling an extension removes its own entries only. */
    fun registerExtension(grant: ExtensionGrant, localId: String, action: () -> Unit): String {
        require(ExtensionPermission.EXECUTE_COMMAND in grant.permissions)
        require(LOCAL_ID.matches(localId))
        val id = "${grant.owner}:$localId"
        require(id !in entries) { "Duplicate command: $id" }
        entries[id] = Entry(grant.owner, action)
        return id
    }

    fun unregisterExtension(owner: String) {
        entries.entries.removeAll { it.value.owner == owner }
    }

    fun ids(): List<String> = entries.keys.toList()

    /** Native UI entry point. Unknown or recursively invoked commands are harmless no-ops. */
    fun execute(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (!running.add(id)) return false
        try {
            entry.action()
            onExecuted(id)
            return true
        } finally {
            running.remove(id)
        }
    }

    /** Future runtime bridge must authenticate the caller and supply a current host-issued grant. */
    fun executeForExtension(grant: ExtensionGrant, id: String): Boolean {
        if (ExtensionPermission.EXECUTE_COMMAND !in grant.permissions) return false
        val entry = entries[id] ?: return false
        if (entry.owner != null && entry.owner != grant.owner) return false
        return execute(id)
    }

    companion object {
        private val LOCAL_ID = Regex("[a-z][a-z0-9_.-]{0,63}")
    }
}
