package com.xnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.MarkupType
import com.xnotes.plugins.ExportAnnotation
import com.xnotes.plugins.PluginSlot
import com.xnotes.plugins.contributions
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** A generic sidebar slot. The native panel renderer is replaceable without touching the editor's layout. */
@Composable
internal fun PluginSidebar(editor: Editor): Boolean {
    val id = editor.pluginPanel ?: return false
    if (editor.declarativePlugins.contributions(PluginSlot.SIDEBAR).none { it.id == id }) return false
    when (id) {
        "advanced-pdf-annotations:open" -> AnnotationPanel(editor)
        else -> return false
    }
    return true
}

@Composable
private fun AnnotationPanel(editor: Editor) {
    val palette = LocalPalette.current
    val rows = editor.pluginAnnotationRows()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var editing by remember { mutableStateOf<ExportAnnotation?>(null) }
    var deleting by remember { mutableStateOf<ExportAnnotation?>(null) }
    val filtered = rows.filter {
        (filter == "All" || (filter == "Comments" && !it.comment.isNullOrBlank()) || it.type == filter) &&
            (it.text.contains(query, true) || it.comment.orEmpty().contains(query, true) || it.color.contains(query, true))
    }.sortedWith(compareBy({ it.notePage }, { it.created }))
    Column(Modifier.width(248.dp).fillMaxHeight().background(palette.panel.toComposeColor()).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Annotations", modifier = Modifier.weight(1f))
            TextButton(onClick = editor::closePluginPanel) { Text("Close") }
        }
        OutlinedTextField(query, { query = it.take(160) }, singleLine = true, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(onClick = { filter = "All" }) { Text(if (filter == "All") "All ✓" else "All") }
            TextButton(onClick = { filter = "Comments" }) { Text(if (filter == "Comments") "Comments ✓" else "Comments") }
        }
        TextButton(onClick = { filter = if (filter == "highlight") "All" else "highlight" }) { Text(if (filter == "highlight") "Highlights ✓" else "Highlights") }
        Text("${filtered.size} annotations", fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { "${it.notePage}:${it.id}" }) { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val c = runCatching { Color(android.graphics.Color.parseColor(a.color.take(7))) }.getOrDefault(Color.Gray)
                    Box(Modifier.size(10.dp).background(c))
                    TextButton(onClick = { editor.goToPage(a.notePage - 1) }) {
                        Text("Note p. ${a.notePage}" + (a.pdfPage?.let { " · PDF $it" } ?: ""), fontSize = 12.sp)
                    }
                }
                Text(a.text.ifBlank { "[No stored text]" }, fontSize = 13.sp)
                a.comment?.let { Text("Comment: $it", fontSize = 12.sp) }
                Row {
                    TextButton(onClick = { editing = a }) { Text(if (a.comment == null) "Comment" else "Edit") }
                    TextButton(onClick = { deleting = a }) { Text("Delete") }
                }
                HorizontalDivider()
            }
        }
    }
    editing?.let { a ->
        var text by remember(a) { mutableStateOf(a.comment.orEmpty()) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Annotation comment") },
            text = { OutlinedTextField(text, { text = it.take(20000) }, minLines = 3, label = { Text("Comment (blank removes it)") }) },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { editor.editPluginAnnotation(a.notePage, a.id, text); editing = null }) { Text("Save") } })
    }
    deleting?.let { a ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this annotation?") },
            text = { Text("Removes the mark and its comment. This can be undone.") },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            confirmButton = { TextButton(onClick = { editor.editPluginAnnotation(a.notePage, a.id, null, true); deleting = null }) { Text("Delete") } })
    }
}

@Composable
internal fun PluginSelectionCommentDialog(editor: Editor) {
    if (!editor.pluginCommentOpen || !editor.declarativePlugins.featureEnabled("document.annotation_tools")) return
    var text by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MarkupType.HIGHLIGHT) }
    AlertDialog(onDismissRequest = editor::dismissPluginComment, title = { Text("Add comment to PDF selection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(text, { text = it.take(20000) }, minLines = 3, label = { Text("Comment") })
                for (option in listOf(MarkupType.HIGHLIGHT, MarkupType.UNDERLINE)) Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(type == option, { type = option })
                    Text(option.id)
                }
                Text("Uses a native text mark. A multi-page selection stores the comment on its first mark.", fontSize = 12.sp)
            }
        },
        dismissButton = { TextButton(onClick = editor::dismissPluginComment) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { editor.savePluginComment(text, type) }) { Text("Save") } })
}
