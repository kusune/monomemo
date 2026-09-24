# Changelog

## Unreleased

- Add an Android emulator smoke test that launches the main activity before a
  release is published.
- Apply display-setting changes live while the settings dialog is open, with
  `決定` to keep them and `キャンセル`/dismiss to restore the previous values.
- Add a bounded undo/redo timeline for all text edits, including paste and
  selection restoration. A new edit after undo starts a new branch.

## 0.1.0

- Initial local-first editor slice.
- Bundled Japanese-capable fixed-width BIZ UDGothic font.
- Autosaves after a short editing pause.
- Restores selection and scroll position on launch.

## 0.2.0

- Add the editor toolbar and display settings foundation.
- Add pinch font-size adjustment.
- Add configurable line spacing.
- Add visual line-wrapping toggle without changing the document text.

## 0.3.0

- Prefer character-oriented visual wrapping instead of dictionary wrapping.
- Add a toolbar action that pastes at the caret without replacing a selection.
- Display font-size controls in logical points (`pt`).
- Apply display-dialog changes only after pressing `決定`; `キャンセル` leaves them unchanged.
- Add software and license information to the hamburger menu.

## 0.3.1

- Fix a startup crash introduced by the character-wrapping initialization.
