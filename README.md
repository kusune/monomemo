# MonoMemo

A minimal monospace text editor for Android with local-first cloud sync.

## Current status

The first vertical slice is intentionally small:

- Japanese-capable editor surface
- fixed line spacing
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
