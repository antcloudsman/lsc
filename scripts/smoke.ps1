<#
.SYNOPSIS
  LC one-command smoke: desktop gate + numeric tests + single-source check + Android gate.

.DESCRIPTION
  Each gate runs in its own process; one failure never aborts the others. The final
  summary lists every gate and returns exit code 0 only when all of them pass.

  Gates:
    1. desktop  : scripts\build-desktop.ps1 (cargo build + 1100x780 screenshot + mechanics baseline)
    2. numeric  : python tests\numeric\run_all.py (74 numeric/boundary tests, xfail-aware)
    3. source   : tools\mechanics\gen-mechanics.ps1 -Check (generated regions match mechanics.json)
    4. android  : scripts\build-android.ps1 (assembleDebug; install/launch/logcat unless -SkipDevice)

  Usage (from anywhere):
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1
    powershell -File scripts\smoke.ps1 -SkipDevice
    powershell -File scripts\smoke.ps1 -DeviceId L2E0222531014833

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
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

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
$numericScript = Join-Path $repoRoot 'tests\numeric\run_all.py'
$generatorScript = Join-Path $repoRoot 'tools\mechanics\gen-mechanics.ps1'

Write-Host "LC SMOKE | repo=$repoRoot"
Write-Host "LC SMOKE | host=$hostExe"
Write-Host "LC SMOKE | out=$OutDir"
Write-Host ''

function Invoke-Gate {
    param(
        [string]$Label,
        [string]$Exe,
        [string[]]$Arguments,
        [string]$LogPath
    )
    Write-Host "[GATE] $Label" -ForegroundColor Cyan
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Exe @Arguments 2>&1 | Tee-Object -FilePath $LogPath | ForEach-Object { Write-Host ($_.ToString()) }
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Resolve-Python {
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if ($cmd) { return [pscustomobject]@{ Exe = $cmd.Source; Prefix = @() } }
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { return [pscustomobject]@{ Exe = $py.Source; Prefix = @('-3') } }
    return $null
}

# --- 1/4 desktop ----------------------------------------------------------
$desktopArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $desktopScript, '-OutDir', $desktopOut)
& $hostExe @desktopArgs
$desktopCode = $LASTEXITCODE

# --- 2/4 numeric regression ----------------------------------------------
$numericLog = Join-Path $OutDir 'numeric.log'
$numericCode = 1
$numericDetail = ''
$python = Resolve-Python
if (-not $python) {
    Write-Host '[FAIL] Python 3.10+ not found on PATH; numeric suite cannot run' -ForegroundColor Red
} elseif (-not (Test-Path -LiteralPath $numericScript)) {
    Write-Host "[FAIL] missing numeric test entry: $numericScript" -ForegroundColor Red
} else {
    $numericArgs = @()
    $numericArgs += $python.Prefix
    $numericArgs += $numericScript
    $numericCode = Invoke-Gate 'numeric tests (tests\numeric\run_all.py)' $python.Exe $numericArgs $numericLog
    $detailLine = Select-String -LiteralPath $numericLog -Pattern '^tests=' | Select-Object -Last 1
    if ($detailLine) { $numericDetail = $detailLine.Line.Trim() }
}

# --- 3/4 single-source generator check -----------------------------------
$mechanicsLog = Join-Path $OutDir 'mechanics-source.log'
$mechanicsCode = 1
if (-not (Test-Path -LiteralPath $generatorScript)) {
    Write-Host "[FAIL] missing generator: $generatorScript" -ForegroundColor Red
} else {
    $generatorArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $generatorScript, '-Check')
    $mechanicsCode = Invoke-Gate 'mechanics single-source (gen-mechanics.ps1 -Check)' $hostExe $generatorArgs $mechanicsLog
}

# --- 4/4 android ----------------------------------------------------------
$androidArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $androidScript, '-OutDir', $androidOut)
if ($DeviceId) { $androidArgs += @('-DeviceId', $DeviceId) }
if ($SkipDevice) { $androidArgs += @('-SkipDevice') }
& $hostExe @androidArgs
$androidCode = $LASTEXITCODE

# --- summary -------------------------------------------------------------
function Get-Result([int]$Code) { if ($Code -eq 0) { 'PASS' } else { 'FAIL' } }
$desktopResult = Get-Result $desktopCode
$numericResult = Get-Result $numericCode
$mechanicsResult = Get-Result $mechanicsCode
$androidResult = Get-Result $androidCode
$overallCode = 0
foreach ($code in @($desktopCode, $numericCode, $mechanicsCode, $androidCode)) {
    if ($code -ne 0) { $overallCode = 1 }
}

Write-Host ''
Write-Host '================ LC SMOKE SUMMARY ================'
Write-Host ("desktop  : {0}  (logs: {1})" -f $desktopResult, $desktopOut)
if ($numericDetail) {
    Write-Host ("numeric  : {0}  ({1})" -f $numericResult, $numericDetail)
} else {
    Write-Host ("numeric  : {0}  (log: {1})" -f $numericResult, $numericLog)
}
Write-Host ("mech-src : {0}  (log: {1})" -f $mechanicsResult, $mechanicsLog)
if ($SkipDevice) {
    Write-Host ("android  : {0}  (build only, device skipped; logs: {1})" -f $androidResult, $androidOut)
} else {
    Write-Host ("android  : {0}  (logs: {1})" -f $androidResult, $androidOut)
}
Write-Host ("overall  : {0}" -f (Get-Result $overallCode))

$summary = @(
    'LC smoke',
    'time=' + (Get-Date).ToString('s'),
    "repo=$repoRoot",
    "desktop=$desktopResult",
    "numeric=$numericResult",
    "mech_src=$mechanicsResult",
    "android=$androidResult",
    "device_skipped=$([bool]$SkipDevice)",
    "overall=$(Get-Result $overallCode)",
    "out=$OutDir"
)
Set-Content -LiteralPath (Join-Path $OutDir 'summary.txt') -Value $summary -Encoding UTF8

if ($overallCode -eq 0) {
    Write-Host 'SMOKE: PASS' -ForegroundColor Green
    exit 0
} else {
    Write-Host 'SMOKE: FAIL' -ForegroundColor Red
    exit 1
}
