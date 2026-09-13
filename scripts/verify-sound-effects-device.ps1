param([switch]$Live, [switch]$SkipBuild, [string]$AudioTaskId)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location $repo
try {
    if (!$SkipBuild) {
        & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest '-PlocalApiBaseUrl=http://127.0.0.1:3001/' --console=plain
        if ($LASTEXITCODE) { throw 'Android verification build failed.' }
    }
    & $adb reverse tcp:3001 tcp:3001
    & $adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE) { throw 'In-place install failed. Existing app data was retained.' }
    & $adb install -r 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if ($LASTEXITCODE) { throw 'Device test installation failed.' }
    $class = if ($AudioTaskId) { 'com.oneimage.android.SoundEffectsPlaybackDeviceTest' } elseif ($Live) { 'com.oneimage.android.SoundEffectsLiveDeviceTest' } else { 'com.oneimage.android.SoundEffectsControlsDeviceTest' }
    $extraArgs = if ($AudioTaskId) { @('-e', 'soundTaskId', $AudioTaskId) } else { @() }
    $testOutput = & $adb shell am instrument -w -r -e class $class @extraArgs -e soundEffectsLive $Live.ToString().ToLowerInvariant() 'com.oneimage.android.test/androidx.test.runner.AndroidJUnitRunner'
    $testOutput | Write-Output
    if ($LASTEXITCODE -or (($testOutput -join "`n") -notmatch 'OK \(\d+ tests?\)')) { throw 'Device instrumentation did not pass. See output above.' }
} finally { Pop-Location }
