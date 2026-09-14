# Sound effects verification

The existing stack uses Compose, JUnit 4, Compose test APIs, Robolectric/Roborazzi, and explicit dependencies. This feature reuses the API, task, direct-transfer, and local result stores without adding a runtime framework.

Unit tests validate defaults, limits, pricing, and API payloads. Device controls tests check independent sliders, no advanced settings, and landscape access. The opt-in live test uses the device's signed-in account to create a two-second batch of three effects, check rotation, play/pause, and download.

Build with `-PlocalApiBaseUrl=http://127.0.0.1:3001/` and use `adb reverse tcp:3001 tcp:3001`. The override applies only to debug builds. Install in place to preserve app data. Screenshot QA is saved to the app external files folder under `sound-effects-qa`.

## Local verification, 2026-09-13

- USB device: MI 8 Lite, Android 10. Installed in place with existing account/data retained.
- Full Android unit suite: 56 tests pass. Batch matching and exclusive playback tests first reproduced their failures.
- Actual device runs generated three separate effects with adjustable duration and CFG. All three appeared in the result list and reopened in history with 3/3 local availability. Playback and all three Downloads were verified.
- Portrait and landscape inspected. Added horizontal system-bar insets for sound controls and removed image-only display controls from sound history.
- Compose device controls automation stalled before activity launch on this MIUI device. It is not counted as passing; direct USB interaction and screenshots were used instead.
- Run the native playback arbitration test against a previously generated local three-effect task with `scripts/verify-sound-effects-device.ps1 -AudioTaskId <task-id>`. It reuses local MP3 files and creates no generation task.

- Native USB playback test passed (1/1) on MI 8 Lite: starting each of three real MP3 players pauses the previous player; releasing an older player does not disturb the active one.

History filename follow-up (2026-09-14): history export now selects source-name preservation by sound_effects task type, independent of display title. Missing names retain the existing fallback. All 60 unit tests passed. Updated APK installed over USB. SoundHistoryExportDeviceTest could not export because the connected device had no persisted write permission for a selected folder; this device check is not counted as passing.
