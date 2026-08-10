[CmdletBinding()]
param(
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$androidStudioJava = "C:\Program Files\Android\Android Studio\jbr"

if (Test-Path $androidStudioJava) {
    $env:JAVA_HOME = $androidStudioJava
}

Push-Location $projectRoot
try {
    if (-not $CompileOnly) {
        & cmd.exe /c .\gradlew.bat :app:testDebugUnitTest --tests "com.oneimage.android.ui.onboarding.*" --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Focused onboarding tests failed with exit code $LASTEXITCODE."
        }
    }

    & cmd.exe /c .\gradlew.bat :app:compileDebugKotlin --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "Debug Kotlin compilation failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
