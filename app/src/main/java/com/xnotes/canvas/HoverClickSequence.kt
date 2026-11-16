package com.xnotes.canvas

import com.xnotes.core.geometry.Pt

/** A second click cancels the scheduled single; no double binding means immediate single. */
internal class HoverClickSequence(
    private val schedule: (Runnable) -> Boolean,
    private val cancel: (Runnable) -> Unit,
    private val deliver: (String, Pt) -> Unit,
    private val windowMs: () -> Long = { 300L },
    private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var pending: Runnable? = null
    private var doubleAction: String? = null
    private var expiresAt = 0L
    private var pendingSingle: (() -> Unit)? = null

    fun click(at: Pt, single: String, double: String) {
        if (double == "none") {
            reset()
            if (single != "none") deliver(single, at)
            return
        }
        val timestamp = now()
        if (pending != null && doubleAction == double && timestamp < expiresAt) {
            reset()
            deliver(double, at)
            return
        }
        // If Android has not yet dispatched the timer, an expired click is still a single.
        val prior = pendingSingle
        reset()
        prior?.invoke()
        doubleAction = double
        expiresAt = timestamp + windowMs()
        val finish = { if (single != "none") deliver(single, at) }
        pendingSingle = finish
        val task = Runnable {
            pending = null
            doubleAction = null
            pendingSingle = null
            finish()
        }
        pending = task
        if (!schedule(task)) reset()
    }

    fun reset() {
        pending?.let(cancel)
        pending = null
        doubleAction = null
        pendingSingle = null
        expiresAt = 0L
    }
}
