<#
.SYNOPSIS
  LC desktop quality gate: cargo build + headless 1100x780 screenshot + mechanics baseline.

.DESCRIPTION
  Runs in a clean shell. Exit code 0 = PASS, 1 = FAIL.
  Writes logs/screenshot/result.txt into OutDir (default: <repo>\checks\desktop-<timestamp>,
  which is git-ignored). It never modifies tracked project files.

.PARAMETER OutDir
  Output directory for logs and the screenshot.

.PARAMETER AppDir
  Desktop crate directory. Default: <repo>\app

.PARAMETER MinPngBytes
  Reject screenshots smaller than this (a blank/failed render compresses to a few KB).
#>
[CmdletBinding()]
param(
    [string]$OutDir,
    [string]$AppDir,
    [int]$MinPngBytes = 20000
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $AppDir) { $AppDir = Join-Path $repoRoot 'app' }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot ('checks\desktop-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$script:failures = New-Object System.Collections.Generic.List[string]

function Add-Failure([string]$Message) {
    $script:failures.Add($Message)
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}
function Add-Pass([string]$Message) {
    Write-Host "[PASS] $Message" -ForegroundColor Green
}
function Write-Info([string]$Message) {
    Write-Host "[ -- ] $Message" -ForegroundColor DarkGray
}

# PowerShell 5.1 turns native stderr into ErrorRecords; with ErrorActionPreference=Stop
# that aborts the script. These helpers keep stderr as plain text and preserve exit codes.
function Invoke-NativeStream {
    param([scriptblock]$Command, [string]$LogPath)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Command 2>&1 | Tee-Object -FilePath $LogPath | ForEach-Object { $_.ToString() }
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Invoke-NativeText {
    param([scriptblock]$Command)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $Command 2>&1)
        return (($output | ForEach-Object { $_.ToString() }) -join "`n")
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Get-PngHeader([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 26) { return $null }
    $signature = ''
    for ($i = 0; $i -lt 8; $i++) { $signature += $bytes[$i].ToString('x2') }
    if ($signature -ne '89504e470d0a1a0a') { return $null }
    $width = ([int]$bytes[16] -shl 24) -bor ([int]$bytes[17] -shl 16) -bor ([int]$bytes[18] -shl 8) -bor [int]$bytes[19]
    $height = ([int]$bytes[20] -shl 24) -bor ([int]$bytes[21] -shl 16) -bor ([int]$bytes[22] -shl 8) -bor [int]$bytes[23]
    return [pscustomobject]@{
        Width  = $width
        Height = $height
        Bytes  = (Get-Item -LiteralPath $Path).Length
    }
}

Write-Info "repo: $repoRoot"
Write-Info "out : $OutDir"

# --- 1/3 cargo build -------------------------------------------------------
$cargoLog = Join-Path $OutDir 'cargo-build.log'
Write-Info '[1/3] cargo build'
Push-Location -LiteralPath $AppDir
try {
    Invoke-NativeStream { & cargo build } $cargoLog
    $cargoCode = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($cargoCode -ne 0) {
    Add-Failure "cargo build exited $cargoCode (log: $cargoLog)"
} else {
    Add-Pass 'cargo build exited 0'
}

$exe = Join-Path $AppDir 'target\debug\luopan.exe'
if (-not (Test-Path -LiteralPath $exe)) {
    Add-Failure "missing binary: $exe"
}

# --- 2/3 headless screenshot ----------------------------------------------
$shot = Join-Path $OutDir 'screenshot.png'
$png = $null
if (Test-Path -LiteralPath $exe) {
    Write-Info '[2/3] headless screenshot (1100x780)'
    if (Test-Path -LiteralPath $shot) { Remove-Item -LiteralPath $shot -Force }
    Invoke-NativeText { & $exe --screenshot $shot } | Out-Null
    $shotCode = $LASTEXITCODE
    if ($shotCode -ne 0) { Add-Failure "screenshot run exited $shotCode" }
    $png = Get-PngHeader $shot
    if ($null -eq $png) {
        Add-Failure "screenshot missing or not a PNG: $shot"
    } elseif ($png.Width -ne 1100 -or $png.Height -ne 780) {
        Add-Failure "screenshot is $($png.Width)x$($png.Height), expected 1100x780"
    } elseif ($png.Bytes -lt $MinPngBytes) {
        Add-Failure "screenshot is only $($png.Bytes) bytes (< $MinPngBytes), likely a blank render"
    } else {
        Add-Pass "screenshot 1100x780, $($png.Bytes) bytes ($shot)"
    }
}

# --- 3/3 mechanics.json baseline ------------------------------------------
Write-Info '[3/3] mechanics.json baseline (19 values, 0 estimated)'
$mechPath = Join-Path $AppDir 'mechanics.json'
$mechCount = 0
$mechEstimated = 0
$mechDuplicates = 0
if (-not (Test-Path -LiteralPath $mechPath)) {
    Add-Failure "missing $mechPath"
} else {
    try {
        $mech = Get-Content -LiteralPath $mechPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $values = @($mech.values)
        $mechCount = $values.Count
        if ($mechCount -ne 19) { Add-Failure "mechanics.json has $mechCount values, expected 19" }

        $estimated = @($values | Where-Object {
            ([string]$_.source).Trim().Length -eq 0 -or
            ([string]$_.source) -match '(?i)estimate|estimated|guess|todo|unknown|unconfirmed|tbd'
        })
        $mechEstimated = $estimated.Count
        if ($mechEstimated -gt 0) {
            Add-Failure "mechanics.json has $mechEstimated value(s) without a hard source"
            foreach ($item in $estimated) { Write-Host "       - $($item.name): $($item.source)" }
        }

        $uniqueNames = @($values | ForEach-Object { [string]$_.name } | Sort-Object -Unique)
        $mechDuplicates = $mechCount - $uniqueNames.Count
        if ($mechDuplicates -gt 0) { Add-Failure "mechanics.json has $mechDuplicates duplicate value name(s)" }

        if ($mechCount -eq 19 -and $mechEstimated -eq 0 -and $mechDuplicates -eq 0) {
            Add-Pass 'mechanics.json: 19 values, 0 estimated, 0 duplicate names'
        }
    } catch {
        Add-Failure "mechanics.json is not valid JSON: $($_.Exception.Message)"
    }
}

# --- result ---------------------------------------------------------------
$result = 'FAIL'
if ($script:failures.Count -eq 0) { $result = 'PASS' }
$summary = @(
    'LC desktop gate',
    'time=' + (Get-Date).ToString('s'),
    "repo=$repoRoot",
    "result=$result",
    "cargo_exit=$cargoCode",
    "screenshot=$shot",
    "png_width=$($png.Width)",
    "png_height=$($png.Height)",
    "png_bytes=$($png.Bytes)",
    "mechanics_values=$mechCount",
    "mechanics_estimated=$mechEstimated",
    "mechanics_duplicates=$mechDuplicates"
) + @($script:failures | ForEach-Object { "failure=$_" })
Set-Content -LiteralPath (Join-Path $OutDir 'result.txt') -Value $summary -Encoding UTF8

Write-Host ''
if ($result -eq 'PASS') {
    Write-Host 'DESKTOP: PASS' -ForegroundColor Green
    exit 0
} else {
    Write-Host "DESKTOP: FAIL ($($script:failures.Count) failed check(s))" -ForegroundColor Red
    exit 1
}
