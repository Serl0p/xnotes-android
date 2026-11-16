# Samsung hover and Pen Menu (API 2)
Adapted from https://github.com/shardulvs/xnotes-android/pull/349 by franz-rosenzweig (MIT upstream).
Two opt-in manifest-only packages unlock native features: advanced-stylus (Samsung hover input)
and pen-menu (native settings popup). Neither runs scripts. Use Preferences > Input to assign a
single tap, double tap, or other gesture. Single click is immediate if double tap is None;
otherwise it waits 300 ms. Hold timing is Android's long press timeout; hold tool remains
the existing Android preference. Hover detection requires proximity; Bluetooth Air Actions are not added.
Existing key-based gestures remain unchanged. Test on real device, including hold-to-erase, hover
eraser, quick press/release, double-click, popup dismissal without ink, infinite canvas and safe mode.
The first UI supports native ink, eraser, select and PDF markup popups, not custom menus or diagnostics.
Debug launcher uses a separate label and icon; release assets are unchanged. Signing is unchanged.
