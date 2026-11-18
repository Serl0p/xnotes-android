# Native document plugin bridge (API 8, internal refactor)

This changes no `.xplugin` manifest, archive, API version, permission string,
stored setting, export format, toolbar schema, or JavaScript execution context.
Legacy Annotation Exporter and Obsidian Companion remain backed by their existing
native export scopes; Universal Exporter still includes those routes.

## Boundary

- `PluginDocumentSession` holds only pane-scoped, observable UI state:
  current annotation sidebar, captured-selection dialog visibility, export snapshot.
  It gates each action on active paged-document state and feature activation.
- `PluginDocumentPort` is an **internal Kotlin-only** contract. It exposes
  authorized commands, detached annotation/export records, and bounded user actions;
  it is not a JavaScript bridge or a way to access arbitrary files/documents.
- `EditorPluginDocumentPort` is the single editor-dependent adapter. It keeps
  live `Document` and `TextSelection` references native, validates identity
  before a pending comment is committed, and calls `Editor.applyMarkupEdit`
  so undo, dirty state, repaint and autosave remain on their existing native path.
- `DocumentAnnotationEdits` builds the same native remove/replace commands
  without executing them; `DocumentReadApi` still creates read-only snapshots.

The Editor retains small compatibility delegates for existing sidebar/dialog call
sites and calls `PluginDocumentSession.onHistoryChanged` after native history edits.
The async PDF-text markup path keeps its existing document/page-identity checks;
its plugin grant is rechecked by a native callback just before committing.

## Semantics intentionally unchanged

- PDF selection comment goes onto the first mark of a multi-page selection.
- Editing, deleting and undoing annotation comments use the existing native
  history and markup rebake paths.
- Universal/annotation/Obsidian exports use the same detached snapshot and
  renderer, retaining filename, sidecar, format and CreateDocument behavior.
- Safe mode, disabled packages, stale dialogs and absent paged documents cannot
  authorize fresh document actions.

## Device regression checklist

On a copy of a note containing PDF annotations:
1. Open the annotation sidebar, search/filter, edit/delete a comment, undo/redo.
2. Add a comment from a PDF text selection; navigate pages or switch note while
   the dialog is open, then cancel/save and confirm no wrong-note edit.
3. Export Markdown, text, JSON, CSV, PDF++ and sidecar; confirm audio and native
   page content are unaffected.
4. Disable annotations/exporter and enable safe mode during the flows; validate
   no unauthorized writes and native toolbar fallback.
5. Check paged and infinite canvases, split panes and existing installed packages.

CI validates compilation and unit behavior, not stylus hardware or runtime UI.
