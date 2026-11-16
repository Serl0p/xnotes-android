package com.xnotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xnotes.plugins.ScriptMenu
import com.xnotes.plugins.ScriptMenuRuntime
import kotlinx.coroutines.CancellationException

@Composable
internal fun ScriptPluginDialog(editor: Editor) {
    val request = editor.scriptMenuRequest ?: return
    if (!editor.declarativePlugins.scriptActive(request.pkg)) {
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
    AlertDialog(onDismissRequest = editor::dismissScriptMenu,
        title = { Text(menu?.title ?: request.pkg.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (error != null) Text(error.orEmpty())
                else if (menu == null) Text("Running isolated JavaScript…")
                else for (item in menu!!.items) {
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
