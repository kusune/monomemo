# MonoMemo

A minimal monospace text editor for Android with local-first cloud sync.

## Current status

The current editor slice is intentionally small:

- Japanese-capable editor surface
- fixed line spacing
- pinch font-size adjustment
- configurable line spacing
- visual line wrapping toggle
- character-oriented visual wrapping
- paste-at-cursor toolbar action
- undo/redo for text edits, including branching after undo
- long-press repeat for undo/redo toolbar actions
- minimal menu and display settings entry points
- software information in the main menu
- local autosave after editing pauses
- cursor and scroll position restoration

The bundled editor font is BIZ UDGothic, distributed under the SIL Open Font
License. See `THIRD_PARTY_LICENSES/BIZ_UDGOTHIC-OFL.txt`.

Cloud synchronization and additional font sources are planned for later
milestones and are not included in this first slice.

## Build

Open the project in Android Studio or run:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With an Android emulator available, the device-level startup smoke test can be
run with:

```bash
./gradlew connectedDebugAndroidTest
```
