# OneImage Android

This Android app is a native mobile frontend for selected OneImage backend workflows.

## Release Versions

Android release versions are stored in `version.properties`.

Print the current version:

```powershell
.\gradlew.bat printVersion
```

Bump the version before a new Play Store build:

```powershell
.\gradlew.bat bumpPatchVersion
.\gradlew.bat bundleRelease
```

Use `bumpMinorVersion` or `bumpMajorVersion` instead when the release needs a larger version jump.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Optionally create a file named `.env` in the project directory and set `ONEIMAGE_API_BASE_URL` and `ONEIMAGE_WEB_APP_URL`.
5. Select the **app** run configuration.
6. Select the target emulator or physical device from Android Studio's device dropdown.
7. Click **Run** to build, install, and launch the debug app on the selected device.
