param(
  [string]$PackageName = "com.oneimage.android",
  [string]$MappingPath = "$PSScriptRoot\..\app\build\outputs\mapping\release\mapping.txt",
  [string]$VersionCode = "",
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
  try {
    if ($null -eq $Body) {
      return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers
    }

    $json = $Body | ConvertTo-Json -Depth 12
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json" -Body $json
  } catch {
    $responseBody = ""
    if ($_.Exception.Response) {
      $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
      $responseBody = $reader.ReadToEnd()
    }
    throw "$($_.Exception.Message) $responseBody"
  }
}

function Get-AndroidPublisherToken {
  param(
    [string]$ServiceAccountKeyPath,
    [string]$ServiceAccountEmail
  )

  if ($env:GOOGLE_PLAY_ACCESS_TOKEN) {
    return $env:GOOGLE_PLAY_ACCESS_TOKEN.Trim()
  }

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
    return (& $gcloud auth print-access-token --account=$ServiceAccountEmail --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
  }

  return (& $gcloud auth print-access-token --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
}

function Get-LocalVersionCode {
  $versionFile = Resolve-Path -LiteralPath "$PSScriptRoot\..\version.properties"
  $versionProperties = @{}
  Get-Content -LiteralPath $versionFile | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]+)=(.+)$") {
      $versionProperties[$matches[1].Trim()] = $matches[2].Trim()
    }
  }

  return $versionProperties["VERSION_CODE"]
}

$resolvedMapping = Resolve-Path -LiteralPath $MappingPath
if ([string]::IsNullOrWhiteSpace($VersionCode)) {
  $VersionCode = Get-LocalVersionCode
}
if ([string]::IsNullOrWhiteSpace($VersionCode)) {
  throw "Version code was not provided and could not be read from version.properties."
}

$token = Get-AndroidPublisherToken -ServiceAccountKeyPath $ServiceAccountKeyPath -ServiceAccountEmail $ServiceAccountEmail
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
  $uploadUri = "$uploadBaseUrl/edits/$editId/apks/$VersionCode/deobfuscationFiles/proguard?uploadType=media"
  $upload = curl.exe -sS -X POST $uploadUri `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: text/plain" `
    --data-binary "@$resolvedMapping"
  if ($LASTEXITCODE -ne 0) {
    throw "curl failed while uploading deobfuscation file."
  }

  $uploadResult = $upload | ConvertFrom-Json
  $symbolType = $uploadResult.deobfuscationFile.symbolType
  if ([string]::IsNullOrWhiteSpace($symbolType)) {
    throw "Deobfuscation upload did not return a symbol type. Response: $upload"
  }

  $commitUri = "$baseUrl/edits/$editId`:commit"
  if ($ChangesNotSentForReview) {
    $commitUri = "$commitUri`?changesNotSentForReview=true"
  }
  $commit = Invoke-JsonRequest -Method "POST" -Uri $commitUri -Token $token

  [pscustomobject]@{
    packageName = $PackageName
    versionCode = $VersionCode
    deobfuscationFileType = "proguard"
    symbolType = $symbolType
    editId = $editId
    committed = [bool]$commit.id
    mappingPath = $resolvedMapping.Path
    mappingBytes = (Get-Item -LiteralPath $resolvedMapping).Length
  } | ConvertTo-Json -Depth 5
} catch {
  try {
    Invoke-JsonRequest -Method "DELETE" -Uri "$baseUrl/edits/$editId" -Token $token | Out-Null
  } catch {
    Write-Warning "Could not delete failed edit $editId."
  }
  throw
}
