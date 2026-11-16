package com.xnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.plugins.ScriptMenu
import com.xnotes.plugins.ScriptMenuRuntime
import com.xnotes.plugins.ScriptMenuItem
import com.xnotes.plugins.ScriptMenuRequest
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlin.math.cos
import kotlin.math.sin

/** Composed inside the active canvas overlay, in the same coordinates as stylus hover. */
@Composable
internal fun ScriptPluginDialog(editor: Editor) {
    val request = editor.scriptMenuRequest ?: return
    if (!editor.declarativePlugins.scriptActive(request.pkg) || editor.canvasOpen != request.canvas) {
        LaunchedEffect(request) { editor.dismissScriptMenu() }
        return
    }
    val context = LocalContext.current
    var menu by remember(request) { mutableStateOf<ScriptMenu?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    LaunchedEffect(request) {
        try {
            menu = ScriptMenuRuntime.run(context, request)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Script stopped or rejected. " + (e.message ?: "Unknown error").take(240) }
    }
    val rich = menu
    if (error == null && rich != null && rich.layout == "radial" && request.pkg.paletteAccess) {
        val host: ToolPopupHost = if (editor.canvasOpen) editor.infinite else editor
        PluginPaletteOverlay(request, rich, host, editor::dismissScriptMenu,
            { id -> editor.chooseScriptTool(request,id) },
            { color, index -> editor.applyScriptInk(request,color,index) })
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val result = menu
        if (error == null && (result == null || (result.layout == "radial" && maxWidth >= 234.dp && maxHeight >= 234.dp))) {
            BackHandler { editor.dismissScriptMenu() }
            val density = LocalDensity.current
            val palette = LocalPalette.current
            val extent = if (result == null) 48.dp else 226.dp
            val half = extent.value / 2f
            val ax = with(density) { request.anchor.x.toFloat().toDp().value }
            val ay = with(density) { request.anchor.y.toFloat().toDp().value }
            val cx = if (maxWidth.value >= extent.value) ax.coerceIn(half, maxWidth.value - half) else maxWidth.value / 2
            val cy = if (maxHeight.value >= extent.value) ay.coerceIn(half, maxHeight.value - half) else maxHeight.value / 2
            // A full-surface sibling catches outside taps rather than passing them to native ink.
            Box(Modifier.fillMaxSize().clickable(onClickLabel = "Dismiss tool menu") { editor.dismissScriptMenu() })
            Box(Modifier.offset((cx - half).dp, (cy - half).dp).size(extent)) {
                if (result == null) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.menuBg.toComposeColor())
                        .clickable { editor.dismissScriptMenu() }, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    // Non-interactive background; only host-rendered controls invoke any action.
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(palette.menuBg.toComposeColor())
                        .border(1.dp, palette.border.toComposeColor(), CircleShape)
                        .clickable { editor.dismissScriptMenu() })
                    val radius = 81.0
                    val button = 54f
                    for ((i, item) in result.items.withIndex()) {
                        val angle = -Math.PI / 2 + i * 2 * Math.PI / result.items.size
                        val x = (half + radius * cos(angle)).toFloat() - button / 2
                        val y = (half + radius * sin(angle)).toFloat() - button / 2
                        val selected = item.tool == request.activeTool
                        Box(Modifier.offset(x.dp, y.dp).size(button.dp).clip(CircleShape)
                            .background(if (selected) palette.accent.toComposeColor().copy(alpha = 0.22f) else palette.panel.toComposeColor())
                            .border(1.dp, if (selected) palette.accent.toComposeColor() else palette.border.toComposeColor(), CircleShape)
                            .clickable(onClickLabel = item.label) { editor.chooseScriptTool(request, item.tool) },
                            contentAlignment = Alignment.Center) {
                            ScriptToolIcon(item, selected)
                        }
                    }
                    Box(Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape)
                        .background(palette.panel.toComposeColor()).clickable(onClickLabel = "Close tool menu") { editor.dismissScriptMenu() },
                        contentAlignment = Alignment.Center) {
                        Icon(XnotesIcons.close, "Close tool menu", tint = palette.text.toComposeColor(), modifier = Modifier.size(22.dp))
                    }
                }
            }
        } else {
            // Preserve API 4 list menus and fall back on tiny split panes or runtime errors.
            AlertDialog(onDismissRequest = editor::dismissScriptMenu,
                title = { Text(result?.title ?: request.pkg.name) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (error != null) Text(error.orEmpty())
                        else if (result != null) for (item in result.items) {
                            TextButton(modifier = Modifier.fillMaxWidth(),
                                onClick = { editor.chooseScriptTool(request, item.tool) }) {
                                Text(item.label + if (item.tool == request.activeTool) "  ✓" else "")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = editor::dismissScriptMenu) { Text("Close") } },
            )
        }
    }
}

@Composable
internal fun ScriptToolIcon(item: ScriptMenuItem, selected: Boolean) {
    val palette = LocalPalette.current
    val tint = if (selected) palette.accent.toComposeColor() else palette.text.toComposeColor()
    val stroke = when (item.tool) {
        "pen" -> R.drawable.ic_stroke_regular
        "dashed" -> R.drawable.ic_stroke_dashed
        "calligraphy" -> R.drawable.ic_stroke_calligraphy
        "speed" -> R.drawable.ic_stroke_speed
        "taper" -> R.drawable.ic_stroke_taper
        "highlighter" -> R.drawable.ic_stroke_highlighter
        else -> null
    }
    if (stroke != null) Icon(painterResource(stroke), item.label, tint = tint, modifier = Modifier.size(29.dp))
    else {
        val icon = when(item.tool) {
            "eraser" -> XnotesIcons.eraser
            "pan" -> XnotesIcons.pan
            "select" -> XnotesIcons.select
            "lasso" -> XnotesIcons.lasso
            "shape" -> XnotesIcons.shape
            "markup" -> XnotesIcons.markup
            else -> null
        }
        if (icon != null) Icon(icon, item.label, tint = tint, modifier = Modifier.size(25.dp))
        else Text(item.label.take(8), fontSize = 10.sp, color = tint)
    }
}
