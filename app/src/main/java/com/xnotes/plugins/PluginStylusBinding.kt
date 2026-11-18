package com.xnotes.plugins

import android.os.SystemClock
import android.view.View
import com.xnotes.canvas.HoverClickSequence
import com.xnotes.canvas.SideButtonClicks
import com.xnotes.core.geometry.Pt

/**
 * Wires the existing S-Pen click recognizer to host-provided capabilities.
 * The editor owns active-document checks, menu state and command authorization.
 * This adapter changes no raw Android event handling and keeps legacy timings.
 */
internal object PluginStylusBinding {
    fun attach(
        surface: View,
        side: SideButtonClicks,
        active: () -> Boolean,
        clickDelayMs: () -> Long,
        doubleDelayMs: () -> Long,
        singleAction: () -> String,
        doubleAction: () -> String,
        onAction: (String, Pt?) -> Unit,
        onTouch: () -> Boolean,
    ) {
        val sequence = HoverClickSequence(
            schedule = { task -> surface.postDelayed(task, doubleDelayMs()) },
            cancel = { task -> surface.removeCallbacks(task) },
            deliver = { action, at -> if (active()) onAction(action, at) },
            windowMs = doubleDelayMs,
            now = { SystemClock.uptimeMillis() },
        )
        side.clickTimeoutMs = { if (active()) clickDelayMs() else null }
        side.clicking = { active() && (singleAction() != "none" || doubleAction() != "none") }
        side.onClick = { at ->
            if (active()) sequence.click(at, singleAction(), doubleAction())
        }
        side.onContact = { sequence.reset() }
        side.touchClosesMenu = onTouch
    }
}
