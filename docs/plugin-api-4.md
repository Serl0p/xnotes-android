# Experimental API 4
Existing API 1–3 packages remain supported. Universal Exporter supersedes the two previous
exporters in the View menu while enabled; legacy installations and gestures are preserved.
Advanced PDF Annotations uses native markup comments, existing undo/history and a sidebar slot.
The selection overflow has an optional plugin action. Multi-page comments attach to the first
mark. No new document format or standalone location comment type.
PDF++ exports page links, not exact selection anchors; color mappings and numeric offset are user inputs.
Custom templates are literal token substitution (no eval). Sidemark/MRSF 1.0 sidecar is bundled
with its exact Markdown filename in a ZIP. Extract both files together, do not overwrite independent
vault comments. Anchors target first excerpt line, using native modification time for comment timestamp.
Structured JSON/CSV exports remain annotation-only.
JavaScript packages: API 4, type javascript, root manifest.json and main.js only, explicit
scripts.execute/ui.popup/tools.select/commands.register grants. Disabled by default. On command
invocation, main.js is a function body receiving context {activeTool,surface,tools,settings}.
Return {title,items:[{label,tool}]}; host validates and displays native modal menu. Only explicit
user selection changes the tool. No access to editor references, notes, arbitrary file/network,
HTML or callbacks. 32 KiB source, 16 KiB result, 16 MiB heap target and 2s evaluation deadline.
Requires a compatible Android System WebView sandbox and memory/termination features; no fallback.
No background execution or persistent JS state. Persistent string settings are scoped to package ID.
This is a narrow first scripting API, not a general-purpose Obsidian API or security audit.
