# xNotes plugins

Source packages for xNotes Dev. Pack source files at archive root, not the enclosing folder.
Use `python sdk/pack.py plugins/<id> --out dist` from repository root. Import in Plugins.
API 6/7 toolbar providers: Minimalist Toolbar, Quick Pen Controls, Focus Toolbar, Adaptive
Workspace. Enable then choose Use this toolbar; only one at a time. Restore native toolbar or
safe mode to recover. See [UI API](../docs/plugin-ui-api.md) and [SDK](../sdk/README.md).
API 5: Quick Tool Menu 1.1 and Advanced Stylus Controls 1.1. Older packages remain supported.
Legacy Annotation Exporter and Obsidian Companion are superseded by Universal Exporter.

API 8: Quick Tool Menu 2.0 and Adaptive Workspace 1.1 add native visual configuration. Upgrade with the same IDs, enable again, then save settings. See [API 8](../docs/plugin-api-8.md).
