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
    [ValidateSet('auto', 'native', 'docker')][string]$Backend = 'auto',
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

# --- backend selection -----------------------------------------------------
# 本机没有 MSVC link.exe；仓库带 Docker 化桌面构建路径（app/docker-compose.yml）。
# auto = 原生优先，其次 docker，两者都不可用 -> SKIP（exit 3，不计 PASS）。
function Test-NativeToolchain {
    if (Get-Command link.exe -ErrorAction SilentlyContinue) { return $true }
    $vswhere = 'C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe'
    if (Test-Path -LiteralPath $vswhere) {
        $out = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
        if ($out) { return $true }
    }
    return $false
}
function Test-DockerEngine {
    try {
        & docker version --format '{{.Server.Version}}' 2>$null | Out-Null
        return ($LASTEXITCODE -eq 0)
    } catch { return $false }
}
$hasNative = Test-NativeToolchain
$hasDocker = Test-DockerEngine
$script:desktopBackend = 'skip'
$script:skipReason = ''
if ($Backend -eq 'native') {
    if ($hasNative) { $script:desktopBackend = 'native' } else { $script:skipReason = 'native 后端被指定，但 MSVC link.exe 未找到' }
} elseif ($Backend -eq 'docker') {
    if ($hasDocker) { $script:desktopBackend = 'docker' } else { $script:skipReason = 'docker 后端被指定，但 docker server 不可达' }
} else {
    if ($hasNative) { $script:desktopBackend = 'native' }
    elseif ($hasDocker) { $script:desktopBackend = 'docker' }
    else { $script:skipReason = 'MSVC link.exe 缺失（vswhere 空）且 docker 不可达' }
}
Write-Info "backend: $($script:desktopBackend)"
if ($script:desktopBackend -eq 'skip') {
    $msg = "[SKIP] desktop gate: $($script:skipReason)；这是环境缺口，不计 PASS"
    Write-Host $msg -ForegroundColor Yellow
    Set-Content -LiteralPath (Join-Path $OutDir 'result.txt') -Value @('LC desktop gate', 'result=SKIP', "reason=$($script:skipReason)") -Encoding UTF8
    Write-Host 'DESKTOP: SKIP' -ForegroundColor Yellow
    exit 3
}

# --- 1/3 cargo build -------------------------------------------------------
$cargoLog = Join-Path $OutDir 'cargo-build.log'
$compose = Join-Path $repoRoot 'app\docker-compose.yml'
$exe = $null
Write-Info "[1/3] cargo build ($($script:desktopBackend))"
if ($script:desktopBackend -eq 'docker') {
    $imageLog = Join-Path $OutDir 'docker-image.log'
    Invoke-NativeStream { & docker compose -f $compose build } $imageLog | Out-Null
    if ($LASTEXITCODE -ne 0) { Add-Failure "docker compose build exited $LASTEXITCODE (log: $imageLog)" }
    Invoke-NativeStream { & docker compose -f $compose run --rm build bash -c 'export PATH=/usr/local/cargo/bin:/usr/local/rustup/bin:$PATH; cargo build' } $cargoLog | Out-Null
    $cargoCode = $LASTEXITCODE
} else {
    Push-Location -LiteralPath $AppDir
    try {
        Invoke-NativeStream { & cargo build } $cargoLog
        $cargoCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $exe = Join-Path $AppDir 'target\debug\luopan.exe'
    if (-not (Test-Path -LiteralPath $exe)) {
        Add-Failure "missing binary: $exe"
    }
}
if ($cargoCode -ne 0) {
    Add-Failure "cargo build exited $cargoCode (log: $cargoLog)"
} else {
    Add-Pass "cargo build exited 0 ($($script:desktopBackend))"
}

# --- 2/3 headless screenshot ----------------------------------------------
$shot = Join-Path $OutDir 'screenshot.png'
$png = $null
Write-Info '[2/3] headless screenshot (1100x780)'
if (Test-Path -LiteralPath $shot) { Remove-Item -LiteralPath $shot -Force }
if ($script:desktopBackend -eq 'docker') {
    $containerShot = Join-Path $AppDir 'checks\desktop-shot.png'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $containerShot) | Out-Null
    if (Test-Path -LiteralPath $containerShot) { Remove-Item -LiteralPath $containerShot -Force }
    $shotLog = Join-Path $OutDir 'screenshot.log'
    Invoke-NativeStream { & docker compose -f $compose run --rm build bash -c 'Xvfb :99 -screen 0 1280x1024x24 >/tmp/xvfb.log 2>&1 & sleep 2; export DISPLAY=:99; ./target/debug/luopan --screenshot /src/checks/desktop-shot.png; exit $?' } $shotLog | Out-Null
    $shotCode = $LASTEXITCODE
    if ($shotCode -ne 0) { Add-Failure "screenshot run exited $shotCode (docker, log: $shotLog)" }
    if (Test-Path -LiteralPath $containerShot) { Copy-Item -LiteralPath $containerShot -Destination $shot -Force }
} elseif ($exe -and (Test-Path -LiteralPath $exe)) {
    Invoke-NativeText { & $exe --screenshot $shot } | Out-Null
    $shotCode = $LASTEXITCODE
    if ($shotCode -ne 0) { Add-Failure "screenshot run exited $shotCode" }
}
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
    "backend=$($script:desktopBackend)",
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
