param(
  [string]$PackageName = "com.oneimage.android",
  [string]$BundlePath = "$PSScriptRoot\..\app\build\outputs\bundle\release\app-release.aab",
  [string]$Track = "beta",
  [string]$ReleaseName = "",
  [string]$ReleaseNotes = "Adds Google Play credit purchases and clear paid-credit access messaging.",
  [string]$MappingPath = "$PSScriptRoot\..\app\build\outputs\mapping\release\mapping.txt",
  [switch]$UploadDeobfuscationFile,
  [string]$ServiceAccountKeyPath = "",
  [string]$ServiceAccountEmail = "",
  [switch]$ChangesNotSentForReview
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
    try {
      return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
    } catch {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      throw "$($_.Exception.Message) $($reader.ReadToEnd())"
    }
  }

  $json = $Body | ConvertTo-Json -Depth 12
  try {
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json" -Body $json
  } catch {
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    throw "$($_.Exception.Message) $($reader.ReadToEnd())"
  }
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

$token = ""
if ($env:GOOGLE_PLAY_ACCESS_TOKEN) {
  $token = $env:GOOGLE_PLAY_ACCESS_TOKEN.Trim()
}
if ([string]::IsNullOrWhiteSpace($token)) {
  $gcloud = "C:\Users\denta\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
  if (-not (Test-Path -LiteralPath $gcloud)) {
    $gcloud = "gcloud.cmd"
  }

  if (-not [string]::IsNullOrWhiteSpace($ServiceAccountKeyPath)) {
    $resolvedKey = Resolve-Path -LiteralPath $ServiceAccountKeyPath
    if ([string]::IsNullOrWhiteSpace($ServiceAccountEmail)) {
      $keyJson = Get-Content -LiteralPath $resolvedKey -Raw | ConvertFrom-Json
      $ServiceAccountEmail = $keyJson.client_email
    }

    & $gcloud auth activate-service-account $ServiceAccountEmail --key-file=$resolvedKey | Out-Null
    $token = (& $gcloud auth print-access-token --account=$ServiceAccountEmail --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
  } else {
    $token = (& $gcloud auth print-access-token --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
  }
}
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

  $deobfuscationFileUploaded = $false
  $deobfuscationSymbolType = $null
  $deobfuscationMappingPath = $null
  if ($UploadDeobfuscationFile) {
    $resolvedMapping = Resolve-Path -LiteralPath $MappingPath
    $deobfuscationMappingPath = $resolvedMapping.Path
    $deobfuscationUri = "$uploadBaseUrl/edits/$editId/apks/$($bundle.versionCode)/deobfuscationFiles/proguard?uploadType=media"
    $deobfuscationUpload = curl.exe -sS -X POST $deobfuscationUri `
      -H "Authorization: Bearer $token" `
      -H "Content-Type: application/octet-stream" `
      --data-binary "@$resolvedMapping"
    if ($LASTEXITCODE -ne 0) {
      throw "curl failed while uploading deobfuscation file."
    }

    $deobfuscationResult = $deobfuscationUpload | ConvertFrom-Json
    if ($deobfuscationResult.error) {
      throw "Deobfuscation upload failed. Response: $deobfuscationUpload"
    }

    $deobfuscationSymbolType = $deobfuscationResult.deobfuscationFile.symbolType
    if ([string]::IsNullOrWhiteSpace($deobfuscationSymbolType)) {
      throw "Deobfuscation upload did not return a symbol type. Response: $deobfuscationUpload"
    }
    $deobfuscationFileUploaded = $true
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
  $commitUri = "$baseUrl/edits/$editId`:commit"
  if ($ChangesNotSentForReview) {
    $commitUri = "$commitUri`?changesNotSentForReview=true"
  }
  $commit = Invoke-JsonRequest -Method "POST" -Uri $commitUri -Token $token

  [pscustomobject]@{
    packageName = $PackageName
    track = $trackResult.track
    versionName = $versionName
    localVersionCode = $versionCode
    uploadedVersionCode = $bundle.versionCode
    deobfuscationFileUploaded = $deobfuscationFileUploaded
    deobfuscationSymbolType = $deobfuscationSymbolType
    deobfuscationMappingPath = $deobfuscationMappingPath
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
