package com.xnotes.plugins

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

internal data class InstalledPlugin(val pkg: DeclarativePackage, val enabled: Boolean)

/** App-private, shared between panes. UI thread only; stores validated packages, not extracted files. */
internal class DeclarativePluginManager private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("declarative-plugins-v1", Context.MODE_PRIVATE)
    var installed by mutableStateOf<List<InstalledPlugin>>(emptyList())
        private set
    var safeMode by mutableStateOf(prefs.getBoolean("safe_mode", false))
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    init {
        try {
            val items = JSONArray(prefs.getString("packages", "[]") ?: "[]")
            require(items.length() <= MAX_PLUGINS)
            installed = (0 until items.length()).mapNotNull { i ->
                runCatching {
                    val item = items.getJSONObject(i)
                    val raw = item.getString("archive")
                    require(raw.length <= DeclarativePackages.MAX_ARCHIVE * 2)
                    InstalledPlugin(DeclarativePackages.parse(Base64.decode(raw, Base64.NO_WRAP)),
                        item.getBoolean("enabled"))
                }.onFailure {
                    loadError = "An installed package could not be loaded; it was skipped."
                    Log.w("XnotesExtensions", "Invalid stored package", it)
                }.getOrNull()
            }.distinctBy { it.pkg.id }
        } catch (e: Exception) {
            loadError = "Plugin storage could not be read. No plugins were activated."
            Log.w("XnotesExtensions", "Cannot load plugin store", e)
        }
    }

    fun install(pkg: DeclarativePackage, enable: Boolean) {
        val next = installed.filterNot { it.pkg.id == pkg.id } + InstalledPlugin(pkg, enable)
        require(next.size <= MAX_PLUGINS) { "At most $MAX_PLUGINS plugins may be installed" }
        save(next)
    }

    fun setEnabled(id: String, enabled: Boolean) =
        save(installed.map { if (it.pkg.id == id) it.copy(enabled = enabled) else it })

    fun uninstall(id: String) {
        if (selectedToolbar == id) useToolbar(null)
        save(installed.filterNot { it.pkg.id == id })
        val edit = prefs.edit()
        for (key in prefs.all.keys.filter { it.startsWith("setting:$id:") }) edit.remove(key)
        edit.apply()
        settingsRevision++
    }

    private var settingsRevision by mutableStateOf(0)
    fun scriptSettings(pkg: DeclarativePackage): Map<String, String> {
        @Suppress("UNUSED_VARIABLE") val observe = settingsRevision
        return pkg.scriptDefaults.mapValues { (key, default) -> prefs.getString("setting:${pkg.id}:$key", default) ?: default }
    }
    fun writeScriptSetting(pkg: DeclarativePackage, key: String, value: String) {
        require(installed.any { it.pkg === pkg } && key in pkg.scriptDefaults &&
            value.length <= 160 && value.none { it.isISOControl() }) { "Invalid plugin setting" }
        check(prefs.edit().putString("setting:${pkg.id}:$key", value).commit()) { "Cannot save setting" }
        settingsRevision++
    }

    fun stylusClickMs(): Long {
        @Suppress("UNUSED_VARIABLE") val observe = settingsRevision
        val default = android.view.ViewConfiguration.getLongPressTimeout().toLong().coerceIn(100L, 1000L)
        return prefs.getLong("setting:advanced-stylus:click-ms", default).coerceIn(100L, 1000L)
    }

    fun stylusDoubleMs(): Long {
        @Suppress("UNUSED_VARIABLE") val observe = settingsRevision
        return prefs.getLong("setting:advanced-stylus:double-ms", 300L).coerceIn(150L, 800L)
    }

    fun updateStylusTimings(click: Long, double: Long) {
        require(installed.any { it.pkg.feature == "stylus.hover" } && click in 100L..1000L && double in 150L..800L)
        check(prefs.edit().putLong("setting:advanced-stylus:click-ms", click)
            .putLong("setting:advanced-stylus:double-ms", double).commit()) { "Cannot save stylus timing" }
        settingsRevision++
    }

    private var selectedToolbar by mutableStateOf(prefs.getString("toolbar-provider", null))

    fun toolbarProvider(): DeclarativePackage? =
        if (safeMode) null else installed.firstOrNull { it.enabled && it.pkg.id == selectedToolbar && it.pkg.toolbar != null }?.pkg

    fun useToolbar(pkg: DeclarativePackage?) {
        require(pkg == null || installed.any { it.enabled && it.pkg === pkg && pkg.toolbar != null })
        val edit = prefs.edit()
        if (pkg == null) edit.remove("toolbar-provider") else edit.putString("toolbar-provider", pkg.id)
        check(edit.commit()) { "Could not save toolbar choice" }
        selectedToolbar = pkg?.id
    }

    fun scriptFor(command: String): DeclarativePackage? =
        if (safeMode) null else installed.firstOrNull {
            it.enabled && it.pkg.script != null && command == "${it.pkg.id}:open"
        }?.pkg

    fun scriptActive(pkg: DeclarativePackage): Boolean = scriptFor("${pkg.id}:open") === pkg

    fun executeScript(pkg: DeclarativePackage, registry: CommandRegistry, action: () -> Unit): Boolean {
        if (!scriptActive(pkg)) return false
        val grant = ExtensionGrant(pkg.id, setOf(ExtensionPermission.EXECUTE_COMMAND))
        val id = registry.registerExtension(grant, "open", action)
        return try { registry.executeForExtension(grant, id) }
        finally { registry.unregisterExtension(pkg.id) }
    }

    fun updateSafeMode(value: Boolean) {
        check(prefs.edit().putBoolean("safe_mode", value).commit()) { "Could not save safe mode" }
        safeMode = value
    }

    /** Disabled, removed, unknown and safe-mode commands are no-ops, including stale gesture IDs. */
    fun activeCommands(): List<Pair<String, String>> =
        if (safeMode) emptyList() else installed.filter { it.enabled }.flatMap { entry ->
            entry.pkg.profiles.map { "${entry.pkg.id}:${it.id}" to "${entry.pkg.name}: ${it.name}" } +
                (NativeFeature.find(entry.pkg.feature)?.let { native ->
                    native.command?.let { listOf(it to native.label) }
                } ?: emptyList()) +
                (if (entry.pkg.script != null) listOf("${entry.pkg.id}:open" to entry.pkg.name) else emptyList())
        }

    fun featureEnabled(feature: String): Boolean =
        !safeMode && installed.any { it.enabled && it.pkg.feature == feature }

    fun executeNative(feature: NativeFeature, registry: CommandRegistry, action: () -> Unit): Boolean {
        val local = feature.localCommand ?: return false
        if (!featureEnabled(feature.key)) return false
        val grant = ExtensionGrant(feature.owner, setOf(ExtensionPermission.EXECUTE_COMMAND))
        val id = registry.registerExtension(grant, local, action)
        return try { registry.executeForExtension(grant, id) }
        finally { registry.unregisterExtension(feature.owner) }
    }

    fun executePenMenu(registry: CommandRegistry, action: () -> Unit): Boolean =
        executeNative(NativeFeature.PEN_MENU, registry, action)

    fun execute(id: String, registry: CommandRegistry, apply: (PenProfile) -> Unit): Boolean {
        if (safeMode || !DeclarativePackages.isCommand(id)) return false
        val owner = id.substringBefore(':')
        val entry = installed.firstOrNull { it.enabled && it.pkg.id == owner } ?: return false
        val profile = entry.pkg.profiles.firstOrNull { it.id == id.substringAfter(':') } ?: return false
        // A scoped registration ensures an expired permission/disabled plugin never leaves a live
        // callback in either surface. All package commands resolve through this broker.
        val grant = ExtensionGrant(owner, setOf(ExtensionPermission.EXECUTE_COMMAND))
        val command = registry.registerExtension(grant, profile.id) { apply(profile) }
        return try { registry.executeForExtension(grant, command) }
        finally { registry.unregisterExtension(owner) }
    }

    private fun save(next: List<InstalledPlugin>) {
        val array = JSONArray()
        next.forEach {
            array.put(JSONObject().put("archive", Base64.encodeToString(it.pkg.bytes, Base64.NO_WRAP))
                .put("enabled", it.enabled))
        }
        check(prefs.edit().putString("packages", array.toString()).commit()) { "Could not save plugins" }
        installed = next
    }

    companion object {
        private const val MAX_PLUGINS = 16
        private var instance: DeclarativePluginManager? = null
        fun get(context: Context): DeclarativePluginManager =
            instance ?: DeclarativePluginManager(context.applicationContext).also { instance = it }
    }
}
