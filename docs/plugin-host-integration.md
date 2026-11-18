# Plugin host: native integration surface

This is an incremental refactor of API 8, not a new plugin API. The existing .xplugin
manifest, settings keys, permissions, command IDs, JS scope, safe mode and renderer are
unchanged.

## Integration seams

- `PluginCommandDispatcher` classifies toolbar/stylus/gesture command IDs. The
  `PluginCommandPort` callbacks are implemented by the native Editor. The editor
  owns native UI, command grants and document mutations; no plugin gets a live editor.
- `PluginStylusBinding` wires `SideButtonClicks` and `HoverClickSequence` to
  callbacks supplied by Editor. The actual side-button event recognition remains
  native, as do Samsung gating, current-pane checks and commands.
- `PluginToolbarHost` decouples toolbar evaluation from the full `Editor`
  class. `EditorPluginToolbarAdapter` is the *only* class that translates the
  editor model into that small toolbar contract. Paged/infinite tool rendering
  remains host-owned; compatibility with existing toolbar JSON/JS remains intact.
- `NativeEditorExtensions` is still the per-surface registry/event adapter;
  `DeclarativePluginManager` still owns persisted packages, activation, safe
  mode and settings. This refactor does not change their on-disk schema.

## Remaining native coupling

The annotation/export state and document mutation code in `Editor.kt` still
need a separate extraction, preferably behind a scoped document host port.
Do not move raw document or mutable model references into JS or plugin packages.
`EditorActivity.kt` still integrates the plugin composable overlay.
The native input and toolbar implementations still supply hardware-specific controls.

## Before promoting a build

Verify single/double S-Pen click and hold-to-erase on the Galaxy Tab S9 Ultra;
Pen Menu and Quick Tool Menu open/close; script and declarative commands;
native and plugin toolbars on paged/PDF/infinite notes and split panes; annotations
and Universal Exporter; safe mode and legacy packages. A successful CI build is
not evidence of runtime/device compatibility.

For upstream tracking see `.github/workflows/upstream-compatibility.yml` on
the fork default branch. The automation never updates this development branch.
