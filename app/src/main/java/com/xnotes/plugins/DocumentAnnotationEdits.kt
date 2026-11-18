package com.xnotes.plugins

import com.xnotes.core.history.Command
import com.xnotes.core.history.RemoveMarkup
import com.xnotes.core.history.ReplaceMarkup
import com.xnotes.core.model.Document

/**
 * Host-owned command factory. It never applies an edit itself; the native editor
 * must execute the command through its existing undo, dirty, rebake and save path.
 */
internal object DocumentAnnotationEdits {
    /** UI page numbers are 1-based, while native document pages are 0-based. */
    fun command(doc: Document, page: Int, id: String, comment: String?, remove: Boolean): Command? {
        val target = doc.pages.getOrNull(page - 1) ?: return null
        val mark = target.markups.firstOrNull { it.id == id } ?: return null
        if (remove) return RemoveMarkup(target, mark)
        val note = comment?.replace("\r\n", "\n")?.take(20_000)?.takeIf { it.isNotBlank() }
        if (note == mark.note) return null
        return ReplaceMarkup(target, mark, mark.copy(note = note, modified = System.currentTimeMillis()))
    }
}
