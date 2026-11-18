package com.xnotes.plugins

import com.xnotes.core.geometry.Pt

/**
 * Commands supplied by gesture bindings and host-rendered UI are classified here.
 * This contract does not expose the editor model or Android views to a plugin.
 * Authorization remains in the host callbacks and DeclarativePluginManager.
 */
internal interface PluginCommandPort {
    fun openScript(pkg: DeclarativePackage, at: Pt?)
    fun openAnnotations(id: String)
    fun openPenMenu(at: Pt?)
    fun requestExport(kind: ExportKind)
    fun applyDeclarative(id: String)
    fun executeNative(id: String)
}

/** One dispatcher can serve both editors without depending on either editor implementation. */
internal class PluginCommandDispatcher(private val scriptFor: (String) -> DeclarativePackage?) {
    fun dispatch(id: String, at: Pt?, port: PluginCommandPort) {
        val script = scriptFor(id)
        when {
            script != null -> port.openScript(script, at)
            id == NativeFeature.ADVANCED_ANNOTATIONS.command -> port.openAnnotations(id)
            id == NativeFeature.PEN_MENU.command -> port.openPenMenu(at)
            else -> {
                val export = ExportKind.entries.firstOrNull { it.command == id }
                when {
                    export != null -> port.requestExport(export)
                    DeclarativePackages.isCommand(id) -> port.applyDeclarative(id)
                    else -> port.executeNative(id)
                }
            }
        }
    }
}
