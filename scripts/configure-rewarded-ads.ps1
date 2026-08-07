param(
  [Parameter(Mandatory = $true)]
  [string]$AdMobAppId,

  [Parameter(Mandatory = $true)]
  [string]$RewardedAdUnitId,

  [int]$RewardAmount = 10,
  [int]$AndroidStarterCredits = 0,
  [string]$OneImagePath = "..\..\OneImage"
)

$ErrorActionPreference = "Stop"

function Set-EnvValues {
  param(
    [Parameter(Mandatory = $true)]
    [string]$Path,

    [Parameter(Mandatory = $true)]
    [hashtable]$Values
  )

  $lines = @()
  if (Test-Path -LiteralPath $Path) {
    $lines = @(Get-Content -LiteralPath $Path)
  }

  foreach ($key in $Values.Keys) {
    $replacement = "$key=$($Values[$key])"
    $found = $false
    for ($index = 0; $index -lt $lines.Count; $index += 1) {
      if ($lines[$index] -match "^\s*$([regex]::Escape($key))=") {
        $lines[$index] = $replacement
        $found = $true
        break
      }
    }

    if (-not $found) {
      if ($lines.Count -gt 0 -and $lines[-1].Trim() -ne "") {
        $lines += ""
      }
      $lines += $replacement
    }
  }

  Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8
}

$androidEnv = Join-Path $PSScriptRoot "..\.env"
$backendEnv = Join-Path (Join-Path $PSScriptRoot $OneImagePath) ".env"

Set-EnvValues -Path $androidEnv -Values @{
  ADMOB_APP_ID = $AdMobAppId
  ADMOB_REWARDED_AD_UNIT_ID = $RewardedAdUnitId
  REWARDED_AD_CREDIT_AMOUNT = $RewardAmount
  ANDROID_STARTER_CREDITS = $AndroidStarterCredits
}

Set-EnvValues -Path $backendEnv -Values @{
  ADMOB_REWARDED_AD_UNIT_ID = $RewardedAdUnitId
  REWARDED_AD_CREDIT_AMOUNT = $RewardAmount
  ANDROID_STARTER_CREDITS = $AndroidStarterCredits
  ADMOB_REWARDED_SSV_KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json"
}

Write-Host "Configured rewarded ads for Android and backend env files."
