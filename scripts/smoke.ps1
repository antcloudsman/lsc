<#
.SYNOPSIS
  LC one-command smoke: run the desktop gate and the Android gate, then print PASS/FAIL.

.DESCRIPTION
  Each gate runs in its own PowerShell process so a gate failure cannot abort the other one
  and so the whole thing works from a clean shell (no JAVA_HOME / PATH setup required).

  Usage (from anywhere):
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1
    powershell -File scripts\smoke.ps1 -SkipDevice
    powershell -File scripts\smoke.ps1 -DeviceId L2E0222531014833

  Exit code 0 = both gates PASS, 1 = at least one FAIL.
  Logs go to <repo>\checks\smoke-<timestamp>\ (git-ignored).

.PARAMETER OutDir
  Output directory. Default: <repo>\checks\smoke-<timestamp>

.PARAMETER SkipDevice
  Passed to build-android.ps1: build APK only, no install/launch/logcat.

.PARAMETER DeviceId
  adb serial for the Android device leg. Default: $env:LC_DEVICE_ID or the single connected device.
#>
[CmdletBinding()]
param(
    [string]$OutDir,
    [switch]$SkipDevice,
    [string]$DeviceId
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $repoRoot ('checks\smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$desktopOut = Join-Path $OutDir 'desktop'
$androidOut = Join-Path $OutDir 'android'
New-Item -ItemType Directory -Force -Path $desktopOut, $androidOut | Out-Null

$hostExe = (Get-Process -Id $PID).Path
$desktopScript = Join-Path $PSScriptRoot 'build-desktop.ps1'
$androidScript = Join-Path $PSScriptRoot 'build-android.ps1'

Write-Host "LC SMOKE | repo=$repoRoot"
Write-Host "LC SMOKE | host=$hostExe"
Write-Host "LC SMOKE | out=$OutDir"
Write-Host ''

$desktopArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $desktopScript, '-OutDir', $desktopOut)
& $hostExe @desktopArgs
$desktopCode = $LASTEXITCODE

Write-Host ''
$androidArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $androidScript, '-OutDir', $androidOut)
if ($DeviceId) { $androidArgs += @('-DeviceId', $DeviceId) }
if ($SkipDevice) { $androidArgs += @('-SkipDevice') }
& $hostExe @androidArgs
$androidCode = $LASTEXITCODE

$desktopResult = 'FAIL'
if ($desktopCode -eq 0) { $desktopResult = 'PASS' }
$androidResult = 'FAIL'
if ($androidCode -eq 0) { $androidResult = 'PASS' }

Write-Host ''
Write-Host '================ LC SMOKE SUMMARY ================'
Write-Host ("desktop : {0}  (logs: {1})" -f $desktopResult, $desktopOut)
if ($SkipDevice) {
    Write-Host ("android : {0}  (build only, device skipped; logs: {1})" -f $androidResult, $androidOut)
} else {
    Write-Host ("android : {0}  (logs: {1})" -f $androidResult, $androidOut)
}
Write-Host ("overall : {0}" -f $(if ($desktopCode -eq 0 -and $androidCode -eq 0) { 'PASS' } else { 'FAIL' }))

$summary = @(
    'LC smoke',
    'time=' + (Get-Date).ToString('s'),
    "repo=$repoRoot",
    "desktop=$desktopResult",
    "android=$androidResult",
    "device_skipped=$([bool]$SkipDevice)",
    "out=$OutDir"
)
Set-Content -LiteralPath (Join-Path $OutDir 'summary.txt') -Value $summary -Encoding UTF8

if ($desktopCode -eq 0 -and $androidCode -eq 0) {
    Write-Host 'SMOKE: PASS' -ForegroundColor Green
    exit 0
} else {
    Write-Host 'SMOKE: FAIL' -ForegroundColor Red
    exit 1
}
