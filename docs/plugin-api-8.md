# API 8 — visual settings and palette menus (experimental)
All previous API versions remain supported. No document format changes. The host retains
native UI, ink and sandbox ownership. API 8 javascript/toolbar/toolbar-javascript packages
may declare settingsUi keyed by existing settings fields (string values preserved).
The schema is optional; legacy text fields remain supported.

## Visual form
Each field declares type and label. Types: text, toggle ("true"/"false"), select (choices:
[{value,label}]), slider (integer min/max/step), tool-picker, tool-list (CSV native tool IDs,
min/max, unique entries), reorder-list (CSV choices, min/max), color (#RRGGBB or empty if
optional), color-list (CSV #RRGGBB, unique entries, min/max). Tool lists show native icons,
drag handles and move buttons. Changes are staged, validated and saved atomically; invalid
persisted values fall back to package defaults. Settings are scoped to plugin ID.
A tool-list can opt into preview:true: native interactive ring preview, using optional
conventional settings keys center, size, limit, active-first, colors if present. The preview
is illustrative; it does not evaluate JS or modify actual ink. Actual script may further
filter its tools for the surface. Reset defaults is staged until saved.

## Palette menu
API 8 menu scripts can request additional tools.color permission (the existing four grants
still required). Return a radial descriptor with optional center ("palette", "swatches",
"close"), size ("compact","regular","large"), and up to six colors in #RRGGBB.
A palette/swatches center requires tools.color, capped at ten tool entries; close-only
radials cap at eight. Native wheel uses original SpectrumWheel; drag previews locally;
tapping the preview/check applies to active native swatch. Existing native swatch taps select
that slot; custom colors update the active slot. These are explicit user actions, not JS calls,
and update normal ink/swatch settings. In palette mode favorites take precedence in the
six visible swatches; swatches mode shows native and custom colors. Close, outside tap,
Back or tool selection dismisses. Small panes use a list with the same palette.
The scope does not expose color data or mutation calls to scripts. Current context remains
activeTool/surface/tools/settings. No background JS, network or filesystem access.
Legacy API 4/5 scripts keep existing rendering unless upgraded. API 8 toolbar scripts reuse
the API 7 renderer. No full drag-and-drop toolbar designer or arbitrary widget tree yet.

## Regression checks
Install/enable Quick Tool Menu 2.0. Reorder and save, check actual menu; reopen settings
and app; try different centers and dimensions, custom color, cancel a wheel draft, apply
color and draw; native color persists via normal settings. Test pen hover and near edges,
canvas, paged note, small split panes, safe mode and previous plugins. Adaptive Workspace
1.1: change position, pen group, primary tools, rows, floating and background; confirm
appropriate context, one active provider and exact native fallback.
