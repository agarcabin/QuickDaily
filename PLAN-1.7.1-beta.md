# Plan: QuickDaily 1.7.1-beta
_Locked via grill - by user + Codex_

## Goal
Fix QuickDaily's public diagnostic-file placement, eliminate permanent Loading states in collection widgets on OEM launchers, and make bottom timestamp insertion place a complete text-and-image entry after the prior entry's images. Deliver a locally verified 1.7.1-beta APK without changing GitHub state.

## Approach
1. Configure ADB and capture a baseline without clearing the phone's QuickDaily data; then run a read-only adversarial plan review before code changes.
2. Centralize the external diagnostic directory at `/storage/emulated/0/Documents/QuickDaily`, migrate only exact legacy `Documents/QuickDaily_log_*.txt` files without overwriting conflicts, and leave `qd-*` debug artifacts untouched.
3. Add a shared widget data/result layer and shared RemoteViews item rendering. Use `RemoteCollectionItems` on API 31+; retain API 26-30 `RemoteViewsService` with `BIND_REMOTEVIEWS`, explicit export behavior, terminal empty/error states, and result-level logging.
4. Add a pure diary-section append helper. Apply it to floating quick notes and shared-image appends, preserving frontmatter and existing above/cursor insertion behavior.
5. Bump the app to `1.7.1-beta` / versionCode 57, run unit/build checks, install with `adb install -r`, perform widget and image-flow verification, then deliver the local release APK.

## Key decisions & tradeoffs
- SharedPreferences remain app-private; only externally visible logs/diagnostic files use `Documents/QuickDaily`.
- API 31+ direct collection updates avoid OEM RemoteViewsService binding; older Android versions retain the service path for compatibility.
- Widget terminal states are explicit: configure repository, no diary/content, read failure, or no tasks; Loading is transient only.
- Bottom insertion means the end of the anchor Markdown section before the next heading, after all existing text/images and before trailing section whitespace.
- ADB testing uses `adb install -r`; no data wipe, Git commit, push, tag, or GitHub Release.

## Risks / open questions
- The connected phone's API level and launcher will determine which widget path can be verified directly; API 31+ is the primary OEM compatibility path.
- Existing dirty `PLAN.md` and `PLAN-REVIEW-LOG.md` must remain untouched; this task uses the versioned plan and review log files.

## Out of scope
- Deleting or migrating root-level `qd-*.xml/png` files.
- Moving SharedPreferences, Vault files, user attachments, or intentional Pictures exports.
- Remote GitHub publication or destructive device/app-data cleanup.
