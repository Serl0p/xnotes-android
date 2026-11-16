# Declarative extensions (API 1)
Install a local ZIP renamed .xplugin from Preferences > Community plugins. The only accepted
entries are root-level manifest.json and profiles.json. No extraction, scripts or arbitrary paths.
The manifest requires apiVersion 1, type declarative, and tools.configure + commands.register.
See examples/pen-profiles for a working package. ZIP those two files at the archive root.

Each profile specifies a unique id, name, native stroke tool and optional width, color (#RRGGBB),
pressure, minPressure, neon, neonStrength, alpha, scale, stabilize, directionStrength,
speedStrength, taperMin, dashLength, dashGap. Unspecified values use native factory defaults.
Color omitted follows the toolbar. Apply replaces the selected native tool configuration for
future strokes; disable/remove/safe mode do not revert applied tool settings or edit existing ink.
Enabled profiles also appear as gesture options; stale bindings do nothing while disabled.
Packages are copied into private SharedPreferences, capped at 16 packages and 128 KiB each,
with at most 64 KiB per JSON member. The plugin manager validates on import and reload.
Updates/replacements (including downgrades) always require confirmation. No custom profile editor,
separate user data migrations, direct file-manager association, marketplace or JS runtime yet.
Safe mode is a persistent manually controlled switch, not crash detection.
The debug-only launcher label is xNotes Dev. Signing and icon work are separate.

Test: install sample, cancel confirmation, install disabled, enable, apply, assign gesture,
restart, disable and check the gesture is inert, re-enable, enter safe mode, uninstall.
Test both surfaces and split panes; verify ordinary drawing, save, undo and audio.
