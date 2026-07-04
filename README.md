# OneImage Android

This Android app is a native mobile frontend for selected OneImage backend workflows.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Optionally create a file named `.env` in the project directory and set `ONEIMAGE_API_BASE_URL` to the OneImage backend URL.
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
