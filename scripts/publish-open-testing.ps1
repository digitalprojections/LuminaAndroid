param(
  [string]$PackageName = "com.oneimage.android",
  [string]$BundlePath = "$PSScriptRoot\..\app\build\outputs\bundle\release\app-release.aab",
  [string]$Track = "beta",
  [string]$ReleaseName = "",
  [string]$ReleaseNotes = "Adds Google Play credit purchases and clear paid-credit access messaging."
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
  param(
    [string]$Method,
    [string]$Uri,
    [string]$Token,
    [object]$Body = $null
  )

  $headers = @{ Authorization = "Bearer $Token" }
  if ($null -eq $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
  }

  $json = $Body | ConvertTo-Json -Depth 12
  return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json" -Body $json
}

$resolvedBundle = Resolve-Path -LiteralPath $BundlePath
$versionFile = Resolve-Path -LiteralPath "$PSScriptRoot\..\version.properties"
$versionProperties = @{}
Get-Content -LiteralPath $versionFile | ForEach-Object {
  if ($_ -match "^\s*([^#][^=]+)=(.+)$") {
    $versionProperties[$matches[1].Trim()] = $matches[2].Trim()
  }
}

$versionCode = $versionProperties["VERSION_CODE"]
$versionName = $versionProperties["VERSION_NAME"]
if ([string]::IsNullOrWhiteSpace($ReleaseName)) {
  $ReleaseName = "GenStudio $versionName"
}

$token = (& gcloud.cmd auth print-access-token --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Could not obtain an Android Publisher access token from gcloud."
}

$encodedPackage = [uri]::EscapeDataString($PackageName)
$baseUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$encodedPackage"
$uploadBaseUrl = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$encodedPackage"

$edit = Invoke-JsonRequest -Method "POST" -Uri "$baseUrl/edits" -Token $token
$editId = $edit.id
if ([string]::IsNullOrWhiteSpace($editId)) {
  throw "Play edit was not created."
}

try {
  $uploadUri = "$uploadBaseUrl/edits/$editId/bundles?uploadType=media"
  $upload = curl.exe -sS -X POST $uploadUri `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: application/octet-stream" `
    --data-binary "@$resolvedBundle"
  $bundle = $upload | ConvertFrom-Json
  if (-not $bundle.versionCode) {
    throw "Bundle upload did not return a versionCode. Response: $upload"
  }

  $trackBody = @{
    track = $Track
    releases = @(
      @{
        name = $ReleaseName
        versionCodes = @("$($bundle.versionCode)")
        status = "completed"
        releaseNotes = @(
          @{
            language = "en-US"
            text = $ReleaseNotes
          }
        )
      }
    )
  }

  $trackResult = Invoke-JsonRequest -Method "PUT" -Uri "$baseUrl/edits/$editId/tracks/$Track" -Token $token -Body $trackBody
  $commit = Invoke-JsonRequest -Method "POST" -Uri "$baseUrl/edits/$editId`:commit" -Token $token

  [pscustomobject]@{
    packageName = $PackageName
    track = $trackResult.track
    versionName = $versionName
    localVersionCode = $versionCode
    uploadedVersionCode = $bundle.versionCode
    editId = $editId
    committed = [bool]$commit.id
    bundlePath = $resolvedBundle.Path
  } | ConvertTo-Json -Depth 5
} catch {
  try {
    Invoke-JsonRequest -Method "DELETE" -Uri "$baseUrl/edits/$editId" -Token $token | Out-Null
  } catch {
    Write-Warning "Could not delete failed edit $editId."
  }
  throw
}
