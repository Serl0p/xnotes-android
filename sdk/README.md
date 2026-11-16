# xNotes plugin developer starter SDK
Read [toolbar API](../docs/plugin-ui-api.md), [API 4](../docs/plugin-api-4.md) and
[API 5](../docs/plugin-api-5.md). All sample plugins are in ../plugins.
This is a lightweight packaging tool and documented schema, not yet a full JS SDK
with TypeScript bindings, debugger or hot reload.

Use Python 3:
```sh
python sdk/pack.py plugins/adaptive-workspace --out dist
```
Import the .xplugin in xNotes Dev > Plugins. Enable and (for toolbar packages) select
Use this toolbar. Update by importing a new archive with same id, higher version.
For popup scripts, assign a gesture in Preferences > Input. A package is ZIP with its
files at root; do not compress the enclosing directory. The Android host is the authoritative
validator. This tool only checks basic structure and limits.
