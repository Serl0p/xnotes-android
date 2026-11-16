# Toolbar plugin API (6–7), experimental
API 6: manifest type "toolbar", apiVersion 6, permissions ["ui.toolbar","ui.controls"],
root files manifest.json and toolbar.json. API 7: type "toolbar-javascript", apiVersion 7,
permissions ["scripts.execute","ui.toolbar","ui.controls"], root manifest.json and main.js.
Both disabled by default. Enable and explicitly select Use this toolbar in Plugins. Only one
provider is active at a time; disabling, uninstalling, safe mode or a failed result restores
the original toolbar and its preferences. Plugins do not overwrite native layout preferences.
Tool width controls are deliberate native actions and do change the user's normal pen settings.
The script executes on entering/changing editor type or PDF presence, or changing settings,
not every tool change or render. It uses the existing isolated evaluator and deadline,
no document content or event callbacks. The result is cached in the current pane composition;
it may regenerate when the pane is recreated. No network, filesystem or arbitrary Compose code.

## Toolbar descriptor
```json
{"style":{"position":"top","size":"compact","floating":true,"radius":18,"spacing":1,"background":"#292929"},
 "collapsible":true,"rows":[["home","pen","eraser","undo"],["colors",{"type":"width","value":2.5}]]}
```
Use rows (1–3, each up to 36 entries, 64 entries total). Each row is horizontally
scrollable or becomes a vertical column at a side edge. No automatic wrap. Built-ins are
ToolbarItem IDs; paged/canvas-specific entries are filtered by host. Home is inserted
first and split-close stays pinned. Collapse adds a native recoverable toggle. Repeated native
items rejected. Optional paged/pdf/infinite objects may override style/rows/collapsible;
when supplying per-surface descriptors provide paged and infinite (pdf falls back to paged).
Style: position top/bottom/left/right, size compact/regular/comfortable, floating bool,
background #RRGGBB (only used if sufficient contrast with host palette), radius 0–32dp for
floating bars, spacing 0–8dp. Omitted fields preserve native position/size/floating.
Native control nodes:
- {"type":"width","value":2.5}: preset, snapped to current stroke tool's native width range;
  disabled on non-stroke tools.
- {"type":"group","label":"Pens","tools":["pen","dashed","calligraphy"]}: native tool menu.
- {"type":"command","id":"quick-tool-menu:open","label":"Quick tools"}: invokes already
  enabled plugin commands only on explicit tap. Unavailable commands are disabled.
No HTML, custom vector graphics, sliders, arbitrary actions, theme-wide styling, additional
toolbars floating independently, or direct access to native views yet. Multiple rows reuse
existing native toolbar frames; original tools keep their normal behavior.

## JavaScript input
main.js is a function body; global context has surface ("paged" or "infinite"), hasPdf
(boolean), items (available native item ids), settings (strings from manifest settings,
max eight, each 160 chars). Return a toolbar descriptor. No direct host bridge or writes.
See plugins/adaptive-workspace. Legacy API 4/5 popup scripts remain unchanged.

## Testing / upstream
Use safe mode before experimenting with layouts. Verify narrow screens and split panes,
floating bar cover and changing orientation, PDF vs ordinary paged note, infinite canvas,
undo and stylus behavior. Check disable/safe mode restore exact native layout. App-specific
toolbar style is separate from persisted native preferences. API is experimental, not
a security audit. Keep adapters in PluginToolbar and ToolbarSpecs; native rendering stays
in Toolbar, InfiniteToolbar and ToolbarFrame.
