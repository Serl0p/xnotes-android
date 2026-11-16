package com.xnotes.plugins

/** The host owns this catalog: a manifest can request a known capability, not define executable code.
 * API versions identify package schemas. Future runtime grants must be scoped and independently enforced. */
internal enum class NativeFeature(
    val key: String, val owner: String, val api: Int, val permissions: Set<String>,
    val localCommand: String?, val label: String, val explanation: String,
) {
    STYLUS("stylus.hover", "advanced-stylus", 2, setOf("input.stylus"), null,
        "Advanced Stylus Controls", "Recognize nearby Samsung stylus button gestures."),
    PEN_MENU("ui.pen_menu", "pen-menu", 2, setOf("ui.pen_menu", "commands.register"), "open",
        "Pen Menu: Open tool settings", "Open native tool settings."),
    ANNOTATIONS("document.annotations", "annotation-exporter", 3,
        setOf("document.annotations.read", "storage.export", "commands.register"), "export",
        "Annotation Exporter", "Read native text annotations and the active note title; copy or export on request."),
    OBSIDIAN("document.obsidian", "obsidian-companion", 3,
        setOf("document.annotations.read", "document.text.read", "document.metadata.read", "storage.export", "commands.register"), "export",
        "Obsidian Companion", "Read active note metadata, typed text and native annotations; copy or export on request."),
    ADVANCED_ANNOTATIONS("document.annotation_tools", "advanced-pdf-annotations", 4,
        setOf("document.annotations.read", "document.annotations.write", "ui.context_menu", "ui.panel", "navigation.page", "commands.register"), "open",
        "Advanced PDF Annotations", "List and edit native PDF annotations and comments; navigate to their pages."),
    UNIVERSAL("document.export", "universal-exporter", 4,
        setOf("document.annotations.read", "document.text.read", "document.metadata.read", "storage.export", "commands.register"), "export",
        "Universal Exporter", "Read active note metadata, typed text and native annotations; export only on request.");

    val command: String? get() = localCommand?.let { "$owner:$it" }
    companion object {
        fun find(key: String?) = entries.firstOrNull { it.key == key }
        fun byCommand(id: String) = entries.firstOrNull { it.command == id }
    }
}
