# Sound effects verification

The existing stack uses Compose, JUnit 4, Compose test APIs, Robolectric/Roborazzi, and explicit dependencies. This feature reuses the API, task, direct-transfer, and local result stores without adding a runtime framework.

Unit tests validate defaults, limits, pricing, and API payloads. Device controls tests check independent sliders, no advanced settings, and landscape access. The opt-in live test uses the device's signed-in account to create a two-second batch of three effects, check rotation, play/pause, and download.

Build with `-PlocalApiBaseUrl=http://127.0.0.1:3001/` and use `adb reverse tcp:3001 tcp:3001`. The override applies only to debug builds. Install in place to preserve app data. Screenshot QA is saved to the app external files folder under `sound-effects-qa`.
