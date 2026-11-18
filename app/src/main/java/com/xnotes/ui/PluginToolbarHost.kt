package com.xnotes.ui

import com.xnotes.plugins.DeclarativePluginManager

/**
 * Narrow adapter between the native editor and the plugin toolbar renderer.
 * Keep references to Editor here, never in toolbar evaluation/rendering code.
 */
internal interface PluginToolbarHost {
    val manager: DeclarativePluginManager
    val canvas: Boolean
    val hasPdf: Boolean
    fun command(id: String)
    fun report(message: String)
}

internal class EditorPluginToolbarAdapter(private val editor: Editor) : PluginToolbarHost {
    override val manager: DeclarativePluginManager get() = editor.declarativePlugins
    override val canvas: Boolean get() = editor.canvasOpen
    override val hasPdf: Boolean get() = editor.hasPdf
    override fun command(id: String) = editor.runPluginUiCommand(id)
    override fun report(message: String) = editor.say(message)
}
