# QuickDaily sound assets

The task-completion sounds are from the CC0 UI Sound Effects collection by Robin Lamb:

- Source page: https://opengameart.org/content/ui-sound-effects-button-clicks-user-feedback-notifications
- Download used: https://opengameart.org/sites/default/files/ui_wav.zip
- License shown on the source page: CC0

Files used by QuickDaily:

- `app/src/main/res/raw/task_completion_classic.wav` — source file `Ding.wav`, used for “经典”.
- `app/src/main/res/raw/task_completion_electronic.wav` — source file `click_1.wav`, displayed as “木鱼”.

The “蜂鸣” mode intentionally has no bundled file. It preserves the QuickDaily 1.9
implementation and calls Android's `MediaActionSound.FOCUS_COMPLETE` system sound.

The “系统” mode also has no bundled file. It resolves and plays the device's current
`RingtoneManager.TYPE_NOTIFICATION` sound at playback time; missing or failed system
sounds are logged without falling back to another mode.

The previously bundled urgent, crisp, and retro samples were removed because their
settings modes are no longer exposed.

The source files are bundled locally so task completion and settings previews do not require a network connection.
