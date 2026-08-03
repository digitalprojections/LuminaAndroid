param(
  [string]$PackageName = "com.oneimage.android",
  [string]$ServiceAccountEmail = "play-publisher@onestudio-78e34.iam.gserviceaccount.com",
  [string[]]$ExpectedProductIds = @(
    "small_pack_200",
    "medium_625",
    "large"
  )
)

$ErrorActionPreference = "Stop"

$gcloud = "C:\Users\denta\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
if (-not (Test-Path -LiteralPath $gcloud)) {
  $gcloud = "gcloud.cmd"
}

$tokenArgs = @("auth", "print-access-token", "--scopes=https://www.googleapis.com/auth/androidpublisher")
if (-not [string]::IsNullOrWhiteSpace($ServiceAccountEmail)) {
  $tokenArgs += "--account=$ServiceAccountEmail"
}

$tokenOutput = & $gcloud @tokenArgs
if ($LASTEXITCODE -ne 0) {
  throw "Could not obtain an Android Publisher access token from gcloud."
}

$token = ($tokenOutput -join "").Trim()
if ([string]::IsNullOrWhiteSpace($token)) {
  throw "Could not obtain an Android Publisher access token."
}

$encodedPackage = [uri]::EscapeDataString($PackageName)
$uri = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$encodedPackage/oneTimeProducts"

try {
  $response = Invoke-RestMethod -Method Get -Uri $uri -Headers @{ Authorization = "Bearer $token" }
} catch {
  $status = $_.Exception.Response.StatusCode.value__
  $body = ""
  if ($_.Exception.Response) {
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $body = $reader.ReadToEnd()
  }
  throw "Google Play one-time product list failed with HTTP $status. $body"
}

$products = if ($response -is [string] -or $null -eq $response) {
  @()
} else {
  @($response.oneTimeProducts)
}
$productsBySku = @{}
foreach ($product in $products) {
  if ($null -eq $product -or [string]::IsNullOrWhiteSpace($product.productId)) {
    continue
  }
  $productsBySku[$product.productId] = $product
}

$summary = foreach ($productId in $ExpectedProductIds) {
  $product = $productsBySku[$productId]
  if ($null -eq $product) {
    [pscustomobject]@{
      productId = $productId
      exists = $false
      status = "missing"
      purchaseType = $null
      defaultPrice = $null
    }
    continue
  }

  [pscustomobject]@{
    productId = $productId
    exists = $true
    purchaseOptionStates = @($product.purchaseOptions | ForEach-Object { $_.state }) -join ","
    purchaseOptionIds = @($product.purchaseOptions | ForEach-Object { $_.purchaseOptionId }) -join ","
    availableRegions = @(
      $product.purchaseOptions |
        ForEach-Object { $_.regionalPricingAndAvailabilityConfigs } |
        Where-Object { $_.availability -eq "AVAILABLE" }
    ).Count
  }
}

$summary | ConvertTo-Json -Depth 4
