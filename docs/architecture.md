# Architecture notes

## First vertical slice

The first version intentionally has only two application classes:

- `MainActivity`: editor view, toolbar, display controls, lifecycle, and
  debounced save scheduling.
- `LocalNoteStore`: the local note file and editor-state persistence.
- `EditorPreferences`: app-level font size, line spacing, and wrapping state.

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
