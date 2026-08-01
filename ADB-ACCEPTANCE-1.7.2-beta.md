# ADB acceptance evidence: QuickDaily 1.7.2-beta

Device: Xiaomi `22041211AC`, Android `16`, SDK `36`, serial `6XYPYTKF6DBIIB4L`.

Release APK SHA-256: `3E5F23A36B5FAE73A24CDB5F8D246C6A1BBDCDFB8DA70BF17AABDD35CA6DF735`.

## Key final evidence

```text
versionCode=59 minSdk=26 targetSdk=35
versionName=1.7.2-beta
SYSTEM_ALERT_WINDOW: allow

Window #19 Window{... u0 com.quickdaily}:
  mAttrs={(0,600)(950x840) gr=TOP CENTER sim={adjust=resize}
    pkg = com.quickdaily ... ty=APPLICATION_OVERLAY fmt=TRANSLUCENT
imeInputTarget ... com.quickdaily
```

The final Release process remained alive after Launcher start (`pidof com.quickdaily` returned a live PID) and `logcat -b crash` returned no new crash entry.

## Captured artifacts

- [Release overlay screenshot](adb-overlay-release.png)
- [Draft/repeat-focus screenshot](adb-overlay-draft.png)
- [Permission-denied screenshot](adb-overlay-denied.png)
- [Overlay UI tree](adb-ui-overlay.xml)
- [Restore UI tree](adb-ui-restore.xml)
- [Force-stop recovery UI tree](adb-ui-recover.xml)
- [Permission-denied UI tree](adb-ui-denied.xml)
- [MIUI sidebar screenshot](adb-sidebar.png)
- [Cold MIUI sidebar screenshot](adb-sidebar-cold.png)

## Scenario results

- Denied app-op: MainActivity stayed foreground and displayed “需要悬浮窗权限”; no loop.
- Granted app-op: Launcher created the foreground Service and the overlay; automatic focus exposed the IME.
- Repeat launch: log evidence showed `focus existing`; the non-empty draft remained intact.
- Close and force-stop: draft XML remained; relaunch restored the same text.
- Save: diary content and Widget refresh completed; the final fix left no `floating_draft_text` key.
- Hardware Back: after the final Service-hosted ComposeView fix, one Back event closed the overlay and returned to the launcher; the Service and overlay window disappeared.
- Home: `MainActivity` became the resumed Activity and the Service stopped.
- Sidebar cold tap: system Dock was visible, but no QuickDaily Activity start was capturable. Equivalent `MAIN + LAUNCHER` intent routing was separately verified.
