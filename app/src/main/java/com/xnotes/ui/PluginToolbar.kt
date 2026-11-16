package com.xnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xnotes.core.tools.Tool
import com.xnotes.core.tools.ToolConversions
import com.xnotes.core.tools.ToolbarItem
import com.xnotes.core.tools.ToolbarLayout
import com.xnotes.plugins.ScriptMenuRuntime
import com.xnotes.plugins.ToolbarNode
import com.xnotes.plugins.ToolbarSpec
import com.xnotes.plugins.ToolbarSpecs
import com.xnotes.ui.icons.XnotesIcons
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

internal data class PluginChrome(
    val spec: ToolbarSpec, val collapsed: Boolean, val toggle: () -> Unit,
    val command: (String) -> Unit, val allowedCommands: Set<String>,
) {
    val rows get() = if (collapsed) listOf(listOf(ToolbarNode.Native(ToolbarItem.HOME), ToolbarNode.Collapse)) else spec.rows
}
internal val LocalPluginChrome = compositionLocalOf<PluginChrome?> { null }

@Composable
internal fun rememberPluginChrome(editor: Editor): PluginChrome? {
    val manager = editor.declarativePlugins
    val pkg = manager.toolbarProvider() ?: return null
    val canvas = editor.canvasOpen
    val pdf = !canvas && editor.hasPdf
    val settings = manager.scriptSettings(pkg)
    val context = LocalContext.current
    var spec by remember(pkg, canvas, pdf, settings) { mutableStateOf<ToolbarSpec?>(null) }
    var collapsed by remember(pkg, canvas, pdf) { mutableStateOf(false) }
    LaunchedEffect(pkg, canvas, pdf, settings) {
        try {
            val source = requireNotNull(pkg.toolbar)
            val raw = if (pkg.toolbarScript) {
                val input = JSONObject().put("surface", if (canvas) "infinite" else "paged")
                    .put("hasPdf", pdf).put("settings", JSONObject(settings))
                    .put("items", JSONArray(ToolbarSpecs.available(canvas,pdf).map { it.id })).toString()
                ScriptMenuRuntime.evaluate(context, source, input)
            } else source
            if (manager.toolbarProvider() === pkg)
                spec = ToolbarSpecs.parse(raw, canvas, pdf)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            android.util.Log.w("XnotesExtensions", "Toolbar plugin rejected", e)
            if (manager.toolbarProvider() === pkg) editor.say("Plugin toolbar unavailable; native toolbar restored")
        }
    }
    val value = spec ?: return null
    return PluginChrome(value, collapsed && value.collapsible, { collapsed = !collapsed },
        { id -> if (manager.toolbarProvider() === pkg && manager.activeCommands().any { it.first == id }) editor.runPluginUiCommand(id) },
        manager.activeCommands().map { it.first }.toSet())
}

/** Both original toolbar renderers keep ownership of native items. Each plugin row reuses a native frame. */
@Composable
internal fun PluginToolbarFrames(
    armed: Tool, host: ToolPopupHost, native: ToolbarLayout, select: (Tool) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    item: @Composable (ToolbarItem) -> Unit,
    tail: @Composable () -> Unit = {},
) {
    val chrome = LocalPluginChrome.current
    if (chrome == null) {
        ToolbarFrame(armed = armed, trailing = trailing) {
            native.visibleSections.forEachIndexed { i, section ->
                if (i > 0) Separator()
                section.visibleEntries.forEach { item(it.item) }
            }
            tail()
        }
        return
    }
    val body: @Composable () -> Unit = {
        chrome.rows.forEachIndexed { i, row ->
            ToolbarFrame(armed = armed, trailing = if (i == 0) trailing else null) {
                row.forEach { node ->
                    when (node) {
                        is ToolbarNode.Native -> item(node.item)
                        ToolbarNode.Collapse -> ToolbarIcon(XnotesIcons.menu,
                            if (chrome.collapsed) "Expand toolbar" else "Collapse toolbar") { chrome.toggle() }
                        is ToolbarNode.Width -> {
                            val enabled = armed.isStroke
                            ToolbarIcon(XnotesIcons.sliders, "Stroke width ${node.value}", enabled = enabled) {
                                if (armed.isStroke) host.updateToolConfig(armed,
                                    host.toolConfig(armed).copy(baseWidth = ToolConversions.snapWidth(armed, node.value)))
                            }
                        }
                        is ToolbarNode.Group -> {
                            var opened by remember(node) { mutableStateOf(false) }
                            Box {
                                ToolbarIcon(XnotesIcons.menu, node.label, active = node.tools.any { it.id == armed.id }) { opened = true }
                                DropdownMenu(expanded = opened, onDismissRequest = { opened = false }) {
                                    for (tool in node.tools) DropdownMenuItem(text = { Text(tool.id) }, onClick = {
                                        opened = false; Tool.fromId(tool.id)?.let(select)
                                    })
                                }
                            }
                        }
                        is ToolbarNode.Command -> ToolbarIcon(XnotesIcons.plugins, node.label,
                            enabled = node.id in chrome.allowedCommands) { chrome.command(node.id) }
                    }
                }
                if (i == 0) tail()
            }
        }
    }
    if (LocalToolbarLook.current.position.vertical) Row { body() } else Column { body() }
}
