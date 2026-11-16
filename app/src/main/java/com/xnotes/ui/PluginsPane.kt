package com.xnotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.plugins.DeclarativePluginManager
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlin.math.roundToInt

@Composable
internal fun PluginsPane(editor: Editor, compact: Boolean, sidebarOpen: Boolean,
    onShowSidebar: () -> Unit, onBackToHome: () -> Unit) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (compact) IconButton(onClick = onBackToHome) {
                Icon(XnotesIcons.prev, stringResource(R.string.back_to_home), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
            } else if (!sidebarOpen) IconButton(onClick = onShowSidebar) {
                Icon(XnotesIcons.menu, stringResource(R.string.show_sidebar), tint = palette.text.toComposeColor(), modifier = Modifier.size(24.dp))
            }
            Text("Plugins", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.text.toComposeColor())
        }
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CommunityPlugins(editor)
        }
    }
}

@Composable
internal fun StylusTimingSettings(manager: DeclarativePluginManager, onSave: (Long, Long) -> Unit) {
    val savedClick = manager.stylusClickMs()
    val savedDouble = manager.stylusDoubleMs()
    var click by remember(savedClick) { mutableStateOf(savedClick.toFloat()) }
    var double by remember(savedDouble) { mutableStateOf(savedDouble.toFloat()) }
    Text("Maximum click duration: ${click.roundToInt()} ms", fontSize = 13.sp)
    Slider(value = click, onValueChange = { click = it }, valueRange = 100f..1000f, steps = 35)
    Text("A longer press is a hold. Contact with the screen always retains native hold behavior.", fontSize = 12.sp)
    Text("Double-click interval: ${double.roundToInt()} ms", fontSize = 13.sp)
    Slider(value = double, onValueChange = { double = it }, valueRange = 150f..800f, steps = 25)
    Text("When double-click is mapped, a single click waits this interval.", fontSize = 12.sp)
    Row {
        TextButton(onClick = { onSave(click.roundToInt().toLong(), double.roundToInt().toLong()) }) { Text("Save timing") }
        TextButton(onClick = { onSave(android.view.ViewConfiguration.getLongPressTimeout().toLong().coerceIn(100L, 1000L), 300L) }) { Text("Reset timing") }
    }
}
