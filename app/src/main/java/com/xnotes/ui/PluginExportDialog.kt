package com.xnotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.xnotes.plugins.DocumentExports
import com.xnotes.plugins.ExportFormat
import com.xnotes.plugins.ExportGroup
import com.xnotes.plugins.ExportKind
import com.xnotes.plugins.ExportOptions
import com.xnotes.plugins.ExportPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Host-mediated one-time export. No persisted SAF grant or arbitrary destination path. */
@Composable
internal fun PluginExportDialog(editor: Editor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = editor.declarativePlugins
    var pending by remember { mutableStateOf<Pair<ExportKind, ExportPayload>?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val request = pending
        pending = null
        if (uri != null && request != null && manager.featureEnabled(request.first.feature)) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "Cannot open destination" }.use {
                            it.write(request.second.content.toByteArray(Charsets.UTF_8))
                        }
                    }
                    editor.say("Export saved")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { editor.say("Export failed: ${e.message ?: "Cannot write file"}") }
            }
        }
    }
    val request = editor.pluginExportRequest ?: return
    if (!manager.featureEnabled(request.first.feature) || editor.canvasOpen) return
    val (kind, doc) = request
    var options by remember(request) { mutableStateOf(ExportOptions(relativeLink = doc.fileName.orEmpty())) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    fun build(): ExportPayload? = try { DocumentExports.render(doc, kind, options) }
        catch (e: Exception) { error = e.message ?: "Cannot prepare export"; null }
    fun dismiss() { editor.dismissPluginExport() }
    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(kind.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${doc.title} · ${doc.annotations.size} native PDF annotations", fontSize = 13.sp)
                if (kind == ExportKind.ANNOTATIONS) {
                    Text("Format")
                    for (format in ExportFormat.entries) Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = options.format == format, onClick = { options = options.copy(format = format) })
                        Text(format.label)
                    }
                } else {
                    Text("Save beside your .xnote or enter the relative path from the Markdown file. Leave blank to omit the link.", fontSize = 12.sp)
                    OutlinedTextField(value = options.relativeLink, onValueChange = { options = options.copy(relativeLink = it) },
                        label = { Text("Relative .xnote path") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ExportCheck("Include typed text (plain text)", options.typed) { options = options.copy(typed = it) }
                }
                Text("Sort / group annotations")
                for (group in ExportGroup.entries) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = options.group == group, onClick = { options = options.copy(group = group) })
                    Text(group.label)
                }
                ExportCheck("Note and source PDF page numbers", options.pages) { options = options.copy(pages = it) }
                ExportCheck("Annotation color", options.colors) { options = options.copy(colors = it) }
                ExportCheck("Comments", options.comments) { options = options.copy(comments = it) }
                ExportCheck("Timestamps (UTC)", options.timestamps) { options = options.copy(timestamps = it) }
                Text("Freehand ink, OCR, audio, rich-text formatting and images are not exported. The original note stays unchanged.", fontSize = 12.sp)
                error?.let { Text(it, fontSize = 12.sp) }
            }
        },
        dismissButton = { TextButton(onClick = ::dismiss) { Text("Cancel") } },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    if (manager.featureEnabled(kind.feature)) build()?.let {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(it.fileName, it.content))
                            dismiss(); editor.say("Export copied")
                        } catch (e: Exception) { error = "Cannot copy export; try saving a file." }
                    }
                }) { Text("Copy") }
                TextButton(onClick = {
                    if (manager.featureEnabled(kind.feature) && pending == null) build()?.let {
                        pending = kind to it
                        dismiss()
                        try { save.launch(it.fileName) } catch (e: Exception) { pending = null; editor.say("Cannot open file picker") }
                    }
                }) { Text("Save as…") }
            }
        },
    )
}

@Composable
private fun ExportCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
