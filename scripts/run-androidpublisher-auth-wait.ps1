param(
  [string]$OutDir = "C:\tmp\onestudio-play-auth"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$codePath = Join-Path $OutDir "code.txt"
$logPath = Join-Path $OutDir "auth.log"
$statusPath = Join-Path $OutDir "status.json"
$gcloudPath = "C:\Users\denta\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"

Remove-Item -LiteralPath $codePath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $statusPath -Force -ErrorAction SilentlyContinue

function Add-AuthLog {
  param([string]$Line)
  if ($null -ne $Line) {
    Add-Content -LiteralPath $logPath -Value $Line
  }
}

$process = New-Object System.Diagnostics.Process
$process.StartInfo.FileName = $gcloudPath
$process.StartInfo.Arguments = "auth application-default login --no-launch-browser --scopes=https://www.googleapis.com/auth/androidpublisher,https://www.googleapis.com/auth/cloud-platform"
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.RedirectStandardInput = $true
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.RedirectStandardError = $true
$process.EnableRaisingEvents = $true

Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -Action {
  if ($EventArgs.Data) {
    Add-Content -LiteralPath $Event.MessageData -Value $EventArgs.Data
  }
} -MessageData $logPath | Out-Null

Register-ObjectEvent -InputObject $process -EventName ErrorDataReceived -Action {
  if ($EventArgs.Data) {
    Add-Content -LiteralPath $Event.MessageData -Value $EventArgs.Data
  }
} -MessageData $logPath | Out-Null

[void]$process.Start()
$process.BeginOutputReadLine()
$process.BeginErrorReadLine()
Add-AuthLog "AUTH_PROCESS_ID=$($process.Id)"

try {
  while (-not $process.HasExited) {
    if (Test-Path -LiteralPath $codePath) {
      $code = (Get-Content -LiteralPath $codePath -Raw).Trim()
      if (-not [string]::IsNullOrWhiteSpace($code)) {
        $process.StandardInput.WriteLine($code)
        $process.StandardInput.Close()
        break
      }
    }
    Start-Sleep -Seconds 1
  }

  $process.WaitForExit()
  @{
    exitCode = $process.ExitCode
    finishedAt = (Get-Date).ToString("o")
  } | ConvertTo-Json | Set-Content -LiteralPath $statusPath
} catch {
  @{
    exitCode = -1
    error = $_.Exception.Message
    finishedAt = (Get-Date).ToString("o")
  } | ConvertTo-Json | Set-Content -LiteralPath $statusPath
  throw
}
