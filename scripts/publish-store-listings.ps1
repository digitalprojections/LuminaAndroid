param(
  [string]$ListingPath = "$PSScriptRoot\..\store-listings.json",
  [string]$PackageName = "",
  [string[]]$Languages = @(),
  [switch]$IncludeUnpublishable,
  [switch]$ValidateOnly
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
    return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json; charset=utf-8" -Body $json
  } catch {
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    throw "$($_.Exception.Message) $($reader.ReadToEnd())"
  }
}

function Test-Listing {
  param([object]$Listing)

  if ([string]::IsNullOrWhiteSpace($Listing.language)) {
    throw "Listing is missing language."
  }
  if ([string]::IsNullOrWhiteSpace($Listing.title) -or $Listing.title.Length -gt 30) {
    throw "$($Listing.language) title must be 1-30 characters."
  }
  if ([string]::IsNullOrWhiteSpace($Listing.shortDescription) -or $Listing.shortDescription.Length -gt 80) {
    throw "$($Listing.language) shortDescription must be 1-80 characters."
  }
  if ([string]::IsNullOrWhiteSpace($Listing.fullDescription) -or $Listing.fullDescription.Length -gt 4000) {
    throw "$($Listing.language) fullDescription must be 1-4000 characters."
  }
}

$resolvedListingPath = Resolve-Path -LiteralPath $ListingPath
$config = Get-Content -LiteralPath $resolvedListingPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($PackageName)) {
  $PackageName = $config.packageName
}
if ([string]::IsNullOrWhiteSpace($PackageName)) {
  throw "PackageName is required."
}

$selected = @($config.listings | Where-Object {
  $languageMatch = $Languages.Count -eq 0 -or $Languages -contains $_.language
  $publishable = $IncludeUnpublishable -or $_.publish -ne $false
  $languageMatch -and $publishable
})

if ($selected.Count -eq 0) {
  throw "No listings selected."
}

$selected | ForEach-Object { Test-Listing $_ }

if ($ValidateOnly) {
  $selected | Select-Object language, title, shortDescription, publish | ConvertTo-Json -Depth 5
  exit 0
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
  $token = (& $gcloud auth print-access-token --scopes=https://www.googleapis.com/auth/androidpublisher).Trim()
}
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Could not obtain an Android Publisher access token from gcloud."
}

$encodedPackage = [uri]::EscapeDataString($PackageName)
$baseUrl = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$encodedPackage"
$edit = Invoke-JsonRequest -Method "POST" -Uri "$baseUrl/edits" -Token $token
$editId = $edit.id
if ([string]::IsNullOrWhiteSpace($editId)) {
  throw "Play edit was not created."
}

try {
  $results = foreach ($listing in $selected) {
    $body = @{
      title = $listing.title
      shortDescription = $listing.shortDescription
      fullDescription = $listing.fullDescription
    }
    $language = [uri]::EscapeDataString($listing.language)
    $updated = Invoke-JsonRequest -Method "PUT" -Uri "$baseUrl/edits/$editId/listings/$language" -Token $token -Body $body
    [pscustomobject]@{
      language = $updated.language
      title = $updated.title
      shortDescription = $updated.shortDescription
    }
  }

  $commit = Invoke-JsonRequest -Method "POST" -Uri "$baseUrl/edits/$editId`:commit" -Token $token
  [pscustomobject]@{
    packageName = $PackageName
    editId = $editId
    committed = [bool]$commit.id
    listings = $results
  } | ConvertTo-Json -Depth 8
} catch {
  try {
    Invoke-JsonRequest -Method "DELETE" -Uri "$baseUrl/edits/$editId" -Token $token | Out-Null
  } catch {
    Write-Warning "Could not delete failed edit $editId."
  }
  throw
}
