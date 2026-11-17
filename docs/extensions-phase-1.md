# Native extensions: Phase 1 (experimental)

This branch establishes a small native-only extension seam. It does not load external
code, install plugins, expose document data, add network permissions, or change note formats.

## Try it
Build the debug variant with the existing toolchain (JDK 17, NDK 27.0.12077973,
CMake 3.22.1), then open Preferences and assign **Pen** to a two-finger tap,
three-finger tap or a supported stylus tap. Switch to another tool and invoke the
gesture. The current surface should select the Pen. Repeat in a paged note and
an infinite canvas, including split view. Check that the six original actions
and None behave identically. Verify undo/redo and recording/ink sync.

## Native seam
- One CommandRegistry and EditorEvents per editor surface, exposed internally by
  NativeEditorExtensions. Existing gesture IDs are preserved; select_pen is new.
- Host commands call existing editor methods; no direct mutation of pages or GL.
- Native extension commands are owner-namespaced and removable. ExtensionGrant
  is a capability model for trusted Kotlin code only, not a security boundary.
- History's onPush remains unchanged for audio stamping; onChanged is a separate
  post-stack hook. Events are small, deferred via View.post, bounded and best effort.
  They are history notifications, not a complete semantic edit or save log.
  Clearing history drops pending events. No listeners means no queued delivery.
- The eventual untrusted runtime must authenticate plugin identity, manage grants,
  isolate execution, impose limits and marshal API calls onto the editor thread.
  Never let a script call the native registry directly or construct its own grant.

## Validation
Run `./gradlew testDebugUnitTest` (or filter to
`com.xnotes.plugins.NativeExtensionsTest`) and `./gradlew assembleDebug`.
The new JVM tests cover dispatch, namespacing, grant checks, duplicate/recursive
commands, deferred bounded events, listener exceptions, history/audio hook ordering,
clear and rejected scheduling. Device testing remains necessary.

## Not yet implemented
Plugin manager, package import, declarative macros, dynamic toolbar registration,
document API, lifecycle management for installed plugins and a sandboxed interpreter.
