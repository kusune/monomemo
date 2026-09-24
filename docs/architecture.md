# Architecture notes

## First vertical slice

The first version intentionally has only two application classes:

- `MainActivity`: editor view, toolbar, display controls, lifecycle, and
  debounced save scheduling.
- `LocalNoteStore`: the local note file and editor-state persistence.
- `EditorPreferences`: app-level font size, line spacing, and wrapping state.

Display settings edited from the dialog use a draft value that is applied to
the editor immediately. The positive action persists the draft; dismissing the
dialog without confirming restores the values from before the dialog opened.
Pinch zoom and the toolbar wrap action remain direct actions. The paste toolbar
action inserts clipboard text at the caret's end position without deleting a
selected range.

`EditHistory` stores reversible text replacements and caret positions for the
current activity session. It keeps up to 1,000 edits, supports undo/redo, and
discards the redo branch when a new edit is made after undo. The initial note
loaded when the activity starts is therefore the first undo boundary for that
session; the history is intentionally not persisted as part of the note file.
The toolbar undo and redo buttons also repeat their action while held, using
the platform long-press threshold and a fixed 100 ms repeat interval. Repeating
stops when the button is released or the corresponding history side is empty.

The editor is local-first. There is no cloud or multi-provider abstraction yet;
those should be introduced only when pCloud synchronization is implemented.

## Boundaries for the next milestone

Cloud synchronization must sit above `LocalNoteStore` and operate on a local
snapshot. It must not make the editor depend directly on pCloud SDK classes.
The first provider-specific code should therefore be isolated in a pCloud
adapter and a small synchronization worker.

Toolbar actions are fixed for now. The action buttons are created through a
small common helper, but there is intentionally no configurable action system
until there are enough actions to justify one.

The `androidTest` source set contains a device-level startup smoke test. It
does not attempt to verify every editing interaction, but it launches
`MainActivity` on an emulator and verifies that the editor view is present.
Both the normal CI workflow and the tagged-release workflow run this test
before publishing an APK.
