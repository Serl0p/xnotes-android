package com.xnotes.canvas

import com.xnotes.core.geometry.Pt

/** A second click cancels the scheduled single; no double binding means immediate single. */
internal class HoverClickSequence(
    private val schedule: (Runnable) -> Boolean,
    private val cancel: (Runnable) -> Unit,
    private val deliver: (String, Pt) -> Unit,
) {
    private var pending: Runnable? = null
    private var doubleAction: String? = null

    fun click(at: Pt, single: String, double: String) {
        if (double == "none") {
            reset()
            if (single != "none") deliver(single, at)
            return
        }
        if (pending != null && doubleAction == double) {
            reset()
            deliver(double, at)
            return
        }
        reset()
        doubleAction = double
        val task = Runnable {
            pending = null
            doubleAction = null
            if (single != "none") deliver(single, at)
        }
        pending = task
        if (!schedule(task)) reset()
    }

    fun reset() {
        pending?.let(cancel)
        pending = null
        doubleAction = null
    }
}
