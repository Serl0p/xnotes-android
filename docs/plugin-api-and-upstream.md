# Experimental plugin architecture and upstream maintenance

The native host retains rendering, editing, Android input, and storage ownership. The plugin
package manager validates bounded ZIP packages, persists installation state, and checks activation.
NativeFeatures catalogs host-owned feature IDs, schema versions, command IDs and declared scopes.
CommandRegistry routes an authorized invocation. DocumentReadApi snapshots the active paged model
on the UI thread; DocumentExports receives detached values and creates portable output. The native
dialog owns clipboard and one-time Android CreateDocument export. No app-private path, mutable model,
network access, or arbitrary executable code is exposed. The scope strings are a contract for
trusted native adapters — not a sandbox for arbitrary third-party code.

API 1 pen presets, API 2 stylus/menu and API 3 document export can coexist. New native features
need a reviewed adapter; general third-party scripts, arbitrary commands and UI aren't implemented.
For future APIs prefer additive, versioned, platform-neutral value models; permit explicit grants
and revocation checks at every bridge. Avoid touching codecs and renderer unless a feature requires it.

## For a possible upstream contribution
Keep fork branding and CI separate. Propose the small command/event seam first, opt-in declarative
package loader second, document snapshots/export third. Coordinate with upstream PR #349 rather
than duplicate its pending input changes. Include regression tests, disabled-by-default behavior,
and docs. Do not submit without discussing scope with maintainers.

## Sync policy
Keep upstream mirror separate from integration branch. When upstream advances, merge into a
temporary sync branch, review conflicts in Editor.kt, InfiniteEditor.kt, EditorActivity.kt,
Preferences.kt and canvas input; run assembleDebug and testDebugUnitTest; test on device.
Only promote a verified revision. Preserve known working tags/branches/APKs and external backup
of notes/settings. Never force-reset the working fork to upstream. Do not promise conflict-free
updates; small host hooks and tests reduce cost. Existing .xnote/.xcanvas codecs are unchanged.

## API 3 smoke test
In a paged PDF note, make a native text markup, add a comment, insert a blank page in front,
export all formats, check note vs source PDF page. Obsidian companion uses a relative link
(user chooses file destination); plain typed flow, boxes and labels, no OCR or handwriting.
Verify cancel, disabled/safe mode, existing notes, independent installs, both panes, and old plugins.
