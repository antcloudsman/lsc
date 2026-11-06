<#
.SYNOPSIS
  LC one-command smoke: desktop gate + numeric tests + single-source check + Android gate.

.DESCRIPTION
  Each gate runs in its own process; one failure never aborts the others. The final
  summary lists every gate and returns exit code 0 only when all of them pass.

  Gates:
    1. copy     : python tests\copy\copy_gate.py (user-visible strings zero paywall words + 2 static red lines)
    2. secret   : python tests\secrets\secret_gate.py (Tianditu keys must not appear in tracked files)
    3. desktop  : scripts\build-desktop.ps1 (cargo build + 1100x780 screenshot + mechanics baseline)
    4. numeric  : python tests\numeric\run_all.py (74 numeric/boundary tests, xfail-aware)
    5. source   : tools\mechanics\gen-mechanics.ps1 -Check (generated regions match mechanics.json)
    6. android  : scripts\build-android.ps1 (assembleDebug; install/launch/logcat unless -SkipDevice)

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
$copyScript = Join-Path $repoRoot 'tests\copy\copy_gate.py'
$secretScript = Join-Path $repoRoot 'tests\secrets\secret_gate.py'

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

# --- 1/5 copy gate (user-visible strings + static red lines) --------------
$copyLog = Join-Path $OutDir 'copy.log'
$copyCode = 1
$copyDetail = ''
$python = Resolve-Python
if (-not $python) {
    Write-Host '[FAIL] Python 3.10+ not found on PATH; copy gate cannot run' -ForegroundColor Red
} elseif (-not (Test-Path -LiteralPath $copyScript)) {
    Write-Host "[FAIL] missing copy gate: $copyScript" -ForegroundColor Red
} else {
    $copyArgs = @()
    $copyArgs += $python.Prefix
    $copyArgs += $copyScript
    $copyCode = Invoke-Gate 'copy gate (tests\copy\copy_gate.py)' $python.Exe $copyArgs $copyLog
    $copyDetail = (Select-String -LiteralPath $copyLog -Pattern '^\[FAIL\] (copy|audi|tiles):' |
        ForEach-Object { $_.Line -replace '^\[FAIL\]\s+', '' }) -join '; '
}

# --- 2/6 secret gate (Tianditu keys must not be committed) ----------------
$secretLog = Join-Path $OutDir 'secret.log'
$secretCode = 1
$secretDetail = ''
if (-not $python) {
    Write-Host '[FAIL] Python 3.10+ not found on PATH; secret gate cannot run' -ForegroundColor Red
} elseif (-not (Test-Path -LiteralPath $secretScript)) {
    Write-Host "[FAIL] missing secret gate: $secretScript" -ForegroundColor Red
} else {
    $secretArgs = @()
    $secretArgs += $python.Prefix
    $secretArgs += $secretScript
    $secretCode = Invoke-Gate 'secret gate (tests\secrets\secret_gate.py)' $python.Exe $secretArgs $secretLog
    $secretDetail = (Select-String -LiteralPath $secretLog -Pattern '^\[FAIL\] key:' |
        ForEach-Object { $_.Line -replace '^\[FAIL\]\s+', '' }) -join '; '
}

# --- 3/6 desktop ----------------------------------------------------------
$desktopArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $desktopScript, '-OutDir', $desktopOut)
& $hostExe @desktopArgs
$desktopCode = $LASTEXITCODE

# --- 4/6 numeric regression ----------------------------------------------
$numericLog = Join-Path $OutDir 'numeric.log'
$numericCode = 1
$numericDetail = ''
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

# --- 5/6 single-source generator check -----------------------------------
$mechanicsLog = Join-Path $OutDir 'mechanics-source.log'
$mechanicsCode = 1
if (-not (Test-Path -LiteralPath $generatorScript)) {
    Write-Host "[FAIL] missing generator: $generatorScript" -ForegroundColor Red
} else {
    $generatorArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $generatorScript, '-Check')
    $mechanicsCode = Invoke-Gate 'mechanics single-source (gen-mechanics.ps1 -Check)' $hostExe $generatorArgs $mechanicsLog
}

# --- 6/6 android ----------------------------------------------------------
$androidArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $androidScript, '-OutDir', $androidOut)
if ($DeviceId) { $androidArgs += @('-DeviceId', $DeviceId) }
if ($SkipDevice) { $androidArgs += @('-SkipDevice') }
& $hostExe @androidArgs
$androidCode = $LASTEXITCODE

# --- summary -------------------------------------------------------------
function Get-Result([int]$Code) { if ($Code -eq 0) { 'PASS' } elseif ($Code -eq 3) { 'SKIP' } else { 'FAIL' } }
$copyResult = Get-Result $copyCode
$secretResult = Get-Result $secretCode
$desktopResult = Get-Result $desktopCode
$numericResult = Get-Result $numericCode
$mechanicsResult = Get-Result $mechanicsCode
$androidResult = Get-Result $androidCode
$overallCode = 0
foreach ($code in @($copyCode, $secretCode, $desktopCode, $numericCode, $mechanicsCode, $androidCode)) {
    if ($code -eq 1) { $overallCode = 1 }
    elseif ($code -eq 3 -and $overallCode -eq 0) { $overallCode = 3 }
}
$desktopSkipReason = ''
$desktopResultFile = Join-Path $desktopOut 'result.txt'
if ($desktopCode -eq 3 -and (Test-Path -LiteralPath $desktopResultFile)) {
    $desktopSkipReason = ((Select-String -LiteralPath $desktopResultFile -Pattern '^reason=') | ForEach-Object { $_.Line -replace '^reason=', '' }) -join '; '
}

Write-Host ''
Write-Host '================ LC SMOKE SUMMARY ================'
if ($copyDetail) {
    Write-Host ("copy     : {0}  ({1}; log: {2})" -f $copyResult, $copyDetail, $copyLog)
} else {
    Write-Host ("copy     : {0}  (log: {1})" -f $copyResult, $copyLog)
}
if ($secretDetail) {
    Write-Host ("secret   : {0}  ({1}; log: {2})" -f $secretResult, $secretDetail, $secretLog)
} else {
    Write-Host ("secret   : {0}  (log: {1})" -f $secretResult, $secretLog)
}
if ($desktopResult -eq 'SKIP') {
    Write-Host ("desktop  : {0}  (环境缺口: {1}; logs: {2})" -f $desktopResult, $desktopSkipReason, $desktopOut)
} else {
    Write-Host ("desktop  : {0}  (logs: {1})" -f $desktopResult, $desktopOut)
}
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
    "copy=$copyResult",
    "secret=$secretResult",
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
} elseif ($overallCode -eq 3) {
    Write-Host 'SMOKE: SKIP (环境缺口，见上面 desktop 行)' -ForegroundColor Yellow
    exit 3
} else {
    Write-Host 'SMOKE: FAIL' -ForegroundColor Red
    exit 1
}
