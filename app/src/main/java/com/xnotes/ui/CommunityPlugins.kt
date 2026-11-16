package com.xnotes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.plugins.DeclarativePackage
import com.xnotes.plugins.DeclarativePackages
import com.xnotes.plugins.InstalledPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Phase 2: local declarative packages only. No Android code loading or network permissions. */
@Composable
internal fun CommunityPlugins(editor: Editor) {
    val context = LocalContext.current
    val manager = editor.declarativePlugins
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<DeclarativePackage?>(null) }
    var enableOnInstall by remember { mutableStateOf(true) }
    var remove by remember { mutableStateOf<InstalledPlugin?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun change(action: () -> Unit) {
        try { action() } catch (e: Exception) {
            android.util.Log.w("XnotesExtensions", "Plugin operation failed", e)
            notice = e.message ?: "Plugin operation failed"
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val pkg = withContext(Dispatchers.IO) {
                        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open file" }
                        input.use { DeclarativePackages.parse(DeclarativePackages.readLimited(it)) }
                    }
                    enableOnInstall = if (pkg.script != null) false else manager.installed.firstOrNull { it.pkg.id == pkg.id }?.enabled ?: true
                    pending = pkg
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    android.util.Log.w("XnotesExtensions", "Package rejected", e)
                    notice = (e.message ?: "Invalid plugin package").take(300)
                } finally { busy = false }
            }
        }
    }

    Text("Import .xplugin packages. Scripts run in an isolated sandbox on request. Review permissions before installation.", fontSize = 12.sp)
    TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) {
        Text(if (busy) "Validating…" else "Import plugin (.xplugin)")
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Safe mode")
            Text("Suspend all plugin commands; keep packages and settings.", fontSize = 12.sp)
        }
        Switch(checked = manager.safeMode, onCheckedChange = { value -> change { manager.updateSafeMode(value) } })
    }
    manager.loadError?.let { Text(it, fontSize = 12.sp) }
    if (manager.installed.isEmpty()) Text("No plugins installed.", fontSize = 12.sp)
    for (entry in manager.installed) {
        val pkg = entry.pkg
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pkg.name)
                Text("${pkg.version} · ${pkg.id}", fontSize = 12.sp)
                Text(pkg.description, fontSize = 12.sp)
            }
            Switch(checked = entry.enabled, onCheckedChange = { enabled -> change { manager.setEnabled(pkg.id, enabled) } })
        }
        if (entry.enabled && !manager.safeMode) {
            if (pkg.script != null) {
                Text("JavaScript · Assign its command in Input or open from View.", fontSize = 12.sp)
                for ((key, value) in manager.scriptSettings(pkg)) {
                    var draft by remember(pkg, key, value) { mutableStateOf(value) }
                    androidx.compose.material3.OutlinedTextField(value = draft, onValueChange = { draft = it.take(160) },
                        singleLine = true, label = { Text(key) }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { change { manager.writeScriptSetting(pkg, key, draft) } }) { Text("Save setting") }
                }
            }
            when (pkg.feature) {
                "stylus.hover" -> Text("Samsung hover clicks enabled. Set Stylus side button (tap) and Stylus double-tap in Input. With both mapped, a single click waits 300 ms.", fontSize = 12.sp)
                "ui.pen_menu" -> Text("Assign Pen Menu: Open tool settings to any tap gesture in Input. Select a pen first.", fontSize = 12.sp)
                "document.annotations" -> Text("Open a paged note, then View > Annotation Exporter.", fontSize = 12.sp)
                "document.obsidian" -> Text("Open a paged note, then View > Obsidian Companion.", fontSize = 12.sp)
                "document.annotation_tools" -> Text("View > Advanced PDF Annotations. Select PDF text and use the selection menu (…) to add a comment.", fontSize = 12.sp)
            }
            for (profile in pkg.profiles) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontSize = 13.sp)
                        Text("${profile.tool.id} · width ${profile.config.baseWidth}", fontSize = 11.sp)
                    }
                    TextButton(onClick = { change {
                        if (editor.active.runDeclarativeCommand("${pkg.id}:${profile.id}")) notice = "Applied ${profile.name}"
                    } }) { Text("Apply") }
                }
            }
        }
        TextButton(onClick = { remove = entry }) { Text("Uninstall") }
    }
    pending?.let { pkg ->
        val existing = manager.installed.firstOrNull { it.pkg.id == pkg.id }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (existing == null) "Install ${pkg.name}?" else "Replace ${pkg.name}?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pkg.id} · ${pkg.version}")
                    existing?.let { Text("Installed version: ${it.pkg.version}. Confirm replacement, even for the same version or a downgrade.") }
                    Text(pkg.description)
                    if (pkg.script != null) {
                        Text("Executable JavaScript. Permissions: isolated script execution, open a native popup, select drawing tools on your tap, register a command. No note content, filesystem or network bridge. Scripts are disabled by default.")
                    } else when (pkg.feature) {
                        "stylus.hover" -> Text("Requested permission: Recognize nearby stylus button gestures. No executable code.")
                        "ui.pen_menu" -> Text("Requested permissions: Open native tool settings and register a command. No executable code.")
                        "document.annotations", "document.obsidian", "document.annotation_tools" -> {
                            val native = com.xnotes.plugins.NativeFeature.find(pkg.feature)
                            Text("Requested permissions: " + (native?.explanation ?: "") + " Register a command. No scripts or background access.")
                        }
                        else -> {
                            Text("Requested permissions:\n• Configure pen tools\n• Register profile commands")
                            Text("No executable code. Applying a profile replaces that native tool's current configuration for future strokes.")
                            Text("Profiles: " + pkg.profiles.joinToString { it.name })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = enableOnInstall, onCheckedChange = { enableOnInstall = it })
                        Text("Enable after installation")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { change { manager.install(pkg, enableOnInstall); pending = null } }) {
                Text(if (existing == null) "Install" else "Replace")
            } },
        )
    }
    remove?.let { entry ->
        AlertDialog(
            onDismissRequest = { remove = null }, title = { Text("Uninstall ${entry.pkg.name}?") },
            text = { Text("Remove the package and its commands. Existing notes and last-applied pen settings are not reverted.") },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { change { manager.uninstall(entry.pkg.id); remove = null } }) { Text("Uninstall") } },
        )
    }
    notice?.let { message ->
        AlertDialog(onDismissRequest = { notice = null }, title = { Text("Community plugins") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } })
    }
}
