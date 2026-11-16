package com.xnotes.plugins

/** Host-rendered contribution descriptors; plugins do not receive native views. */
internal enum class PluginSlot { VIEW_MENU, PDF_SELECTION, SIDEBAR }
internal data class PluginContribution(val id: String, val label: String, val slot: PluginSlot)

internal fun DeclarativePluginManager.contributions(slot: PluginSlot): List<PluginContribution> {
    if (safeMode) return emptyList()
    return when (slot) {
        PluginSlot.VIEW_MENU -> buildList {
            for (kind in ExportKind.entries) if (featureEnabled(kind.feature))
                kind.command?.let { add(PluginContribution(it, kind.label, slot)) }
            if (featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key))
                add(PluginContribution("advanced-pdf-annotations:open", "Advanced PDF Annotations", slot))
            for (entry in installed) if (entry.enabled && entry.pkg.script != null)
                add(PluginContribution("${entry.pkg.id}:open", entry.pkg.name, slot))
        }
        PluginSlot.PDF_SELECTION -> if (featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key))
            listOf(PluginContribution("advanced-pdf-annotations:comment", "Add comment to selected text", slot)) else emptyList()
        PluginSlot.SIDEBAR -> if (featureEnabled(NativeFeature.ADVANCED_ANNOTATIONS.key))
            listOf(PluginContribution("advanced-pdf-annotations:open", "Annotations", slot)) else emptyList()
    }
}
