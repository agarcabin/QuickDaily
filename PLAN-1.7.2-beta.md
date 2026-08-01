# Plan: QuickDaily 1.7.2-beta
_Locked via grill - by user + Codex_

## Goal

Refactor the desktop/launcher quick-note path into a recoverable `TYPE_APPLICATION_OVERLAY` editor backed by a foreground `specialUse` service, while retaining Activity compatibility for other entry points. Deliver a locally built and Xiaomi-verified `1.7.2-beta` APK without changing GitHub state.

## Scope

- Keep `versionName` at `1.7.2-beta`; bump `versionCode` from 58 to 59.
- Route Launcher and launcher-style `MAIN + LAUNCHER` intents through the overlay; keep share, widget, shortcut and Activity paths compatible.
- Share editor state, draft persistence, picker bridge, save use case and widget refresh behavior between Activity and Service.
- Handle missing overlay permission or service/window failure with one MainActivity authorization prompt and no redirect loop.
- Keep non-empty drafts on repeat launch, persist on close/recycle, clear only after a successful save, and use a low-priority closeable notification.

## Acceptance status

- Unit tests: passed with `:app:testDebugUnitTest`.
- Release build: passed with `:app:assembleRelease`.
- APK metadata: `versionName=1.7.2-beta`, `versionCode=59`.
- Xiaomi 22041211AC / Android 16 / SDK 36: overlay permission denial prompt, no-loop fallback, granted launch, `TYPE_APPLICATION_OVERLAY`, adaptive bounds, automatic focus/IME, close, hardware Back, Home, repeat-focus, draft persistence, Service force-stop recovery and save/Widget refresh verified.
- MIUI sidebar: Dock was opened by ADB and visually contained QuickDaily, but the cold icon tap did not produce a capturable QuickDaily Activity event. Equivalent `MAIN + LAUNCHER` routing through `MainActivity` was verified to start the overlay.
- HarmonyOS: not verified; device unavailable.

## Delivery boundary

Only local build, install and evidence capture were performed. No commit, tag, push, PR or GitHub release was performed. Existing dirty files, `PLAN.md` and `PLAN-REVIEW-LOG.md` were preserved.
