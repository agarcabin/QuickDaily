# Plan review log: QuickDaily 1.7.2-beta

## Act 1 decision record

- Overlay is the single implementation for Launcher/sidebar quick notes; other entry points remain Activity-compatible.
- Draft state survives close, repeat launch and process/Service recovery. A non-empty draft wins over new prefill text.
- Save logic is shared; successful save clears the draft and closes the overlay. Close and Back preserve the draft.
- Home explicitly opens MainActivity; ordinary close returns to the previous system surface.
- Permission denial falls back to MainActivity with one prompt and a single retry after authorization.
- Attachments use a picker proxy Activity and return to the same Service-hosted editor.
- The window uses `TYPE_APPLICATION_OVERLAY` with a foreground `specialUse` Service and low-priority notification.

## Act 2 status

The configured grill reviewer CLI could not start on Windows (`Access is denied` with the configured `gpt-5.6-luna` model). No Codex review verdict was fabricated.

## Implementation findings resolved during ADB validation

1. A Service-hosted ComposeView initially crashed because the ViewTree lacked SavedState and ViewModel owners. A service-local owner now attaches/restores the saved-state registry before `ON_CREATE` and is destroyed with the window.
2. Successful save initially cleared the draft and then immediately re-persisted it from the common hide path. The saved close path now skips draft persistence after clearing.

## Evidence boundary

The first two Debug runs intentionally exposed and captured the above failures in the device crash buffer. The final Release run kept the process alive, showed the overlay window and IME target, and produced no new crash-buffer entries.
