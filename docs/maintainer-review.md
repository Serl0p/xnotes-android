# Maintainer review: experimental xNotes plugin support

This is a review snapshot, not a submitted pull request. It is based on upstream master
`22b6188342e004806f712e9658cbb792dcfe92b4` and the API 8 integration merge `386888df162599feacc6430f445e7868cb428d79`.
The full experimental fork and side-by-side debug APK are preserved separately.
The review snapshot excludes the fork-only debug name/icon resources and APK workflow;
these aren't needed for upstream adoption.

## Why
Specialized workflows (stylus gestures, annotation tools, exporters, configurable radial
menus and alternative toolbars) can be opt-in packages rather than permanent app features.
The prototype reuses native Compose widgets, existing ink/annotation models, history and
storage. The original note and canvas formats have not been intentionally changed.
There is no plugin marketplace or network installation.

## What is actually implemented
- Native command/event foundation and scoped registration.
- Local bounded ZIP packages, independent settings, install/enable/disable/uninstall and safe mode.
- Native feature adapters for stylus, tool settings, annotation management and export.
  These are host-compiled capabilities; adding a new native adapter still requires an APK.
- Declarative pen profiles and toolbar layouts; isolated JavaScript menu and toolbar descriptions.
  The JS interface is deliberately narrow: no raw editor/Android objects, arbitrary callbacks,
  document access, network or filesystem bridge. Not a complete general scripting SDK.
- Native visual settings schemas, bounded palette menu descriptors, toolbar fallbacks.
- Packaging utility, examples, and JVM tests.

## Upstream integration evidence
Four upstream commits, seven paths, no overlap with the 86 paths changed by the experimental
fork before this review snapshot (66 new, 20 modified; 4,530 added / 63 removed lines in
the comparison response). The actual two-parent integration commit was verified against both
source trees: all seven incoming file blobs match upstream exactly; the other fork blobs
are unchanged. The APK/test workflow was initiated on the integration commit:
https://github.com/Serl0p/xnotes-android/actions/runs/43997735801
See its live status and artifacts; compilation/unit testing is not device validation.
This review snapshot differs from the integration source only by fork-specific files and docs;
the review snapshot itself has not been separately compiled.

## Suggested review order (not a request to merge everything at once)
1. Small native command/event seam: review/plugin-foundation, `aea47ff46f76742d9f177fee7bb555481b9ce9b4`.
   This is an independently prepared candidate on latest upstream, not independently CI-verified.
2. Declarative package loader/store and opt-in management UI; agree on API ownership,
   compatibility/version policy and native fallback first.
3. Document/native feature adapters; coordinate hover-click/pen menu work with contributor
   and upstream PR #349 (open at time of review).
4. JS sandbox, descriptor validation, UI slots and example packages, once security model and
   supported Android/WebView capabilities are agreed.
5. Toolbar and palette/visual settings as incremental UI additions.

## Before broad distribution
- Review permissions and replacement/upgrade UX; verify isolate termination for runaway scripts,
  malformed/deep JSON and ZIP abuse, memory pressure, rapid requests and app recreation.
  Validate on multiple WebView versions and Android devices. No independent security audit yet.
- Regression-test no-plugin and safe-mode paths, both editor surfaces, orientation/split panes,
  stylus tap/double/hold/contact, undo/redo and original annotation/export behavior.
  Verify API 8 real wheel, visual settings and persistence, PDF++ page links and actual
  Sidemark import (not exact selection links).
- Measure startup, first popup latency, ink latency, memory and battery on device.
- Address naming, localization/accessibility and UI polish, agree SDK/version policy;
  document supported and unsupported plugin capabilities. Do not promise API stability yet.
- Agree licensing/attribution (especially PR #349), code ownership and maintenance plan.

## Documentation
See plugin-api-4.md, plugin-api-5.md, plugin-ui-api.md, plugin-api-8.md, ../sdk/README.md
and ../plugins/README.md. Older architecture notes describe earlier stages.
