<#
.SYNOPSIS
  LC Android build + real-device smoke gate.

.DESCRIPTION
  Resolves JDK 17 / Gradle / Android SDK itself (no pre-set environment needed), builds
  app-debug.apk, then installs and launches it on the connected device, checking
  `adb logcat` for FATAL exceptions. Exit code 0 = PASS, 1 = FAIL.

  The device install step is delegated to tools\device\install-debug.ps1: adb push +
  `pm install -r -t` first, then an automated fallback for Huawei's install risk
  prompt / lock-screen auth dialog, with the app market disabled only for the
  install window and always restored (`MARKET_RESTORED=...` in the log).

  Device leg can be skipped with -SkipDevice (build-only mode).
  Only the single device given by -DeviceId is used; nothing else is touched.

.PARAMETER OutDir
  Output directory for logs, APK copy and device screenshot.
  Default: <repo>\checks\android-<timestamp> (git-ignored).

.PARAMETER DeviceId
  adb serial. Default: $env:LC_DEVICE_ID, else the only/first connected device.

.PARAMETER SkipDevice
  Build the APK only; do not install/launch/scan logcat.

.PARAMETER JavaHome
  Explicit JDK home. Default: $env:JAVA_HOME, then the known local JDK 17 path.

.PARAMETER GradleExe
  Explicit gradle launcher (.bat). Default: known local Gradle 8.7, then PATH.

.PARAMETER LogWaitSeconds
  How long to let the app run before scanning logcat. Default 5.
#>
[CmdletBinding()]
param(
    [string]$OutDir,
    [string]$DeviceId,
    [switch]$SkipDevice,
    [string]$JavaHome,
    [string]$GradleExe,
    [int]$LogWaitSeconds = 5
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
# 证据脱敏：真机 logcat 会带 map.html?tk=<web key>（华为 WebView 内部日志），落盘前统一脱敏 + 自检。
# 见 tests/lib/LogRedact.ps1；自检不过即抛 LOGCAT_REDACT_FAIL，不留未脱敏文件。
. (Join-Path $repoRoot 'tests\lib\LogRedact.ps1')
$redactKeys = Get-TdtKeyValues
$androidDir = Join-Path $repoRoot 'app\android'
if (-not $OutDir) { $OutDir = Join-Path $repoRoot ('checks\android-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$packageName = 'com.luopan.fengshui'
$activityName = 'com.luopan.fengshui/.MainActivity'

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

function Resolve-JavaHome([string]$Explicit) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($Explicit) { $candidates.Add($Explicit.Trim('"')) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME.Trim('"')) }
    $candidates.Add('C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9')
    if ($env:ProgramFiles) { $candidates.Add((Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')) }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin\java.exe'))) { return $candidate }
    }
    return $null
}

function Resolve-Gradle([string]$Explicit) {
    if ($Explicit -and (Test-Path -LiteralPath $Explicit)) { return (Get-Item -LiteralPath $Explicit).FullName }
    $known = 'C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin\gradle.bat'
    if (Test-Path -LiteralPath $known) { return $known }
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

Write-Info "repo: $repoRoot"
Write-Info "out : $OutDir"

# --- toolchain ------------------------------------------------------------
$javaHome = Resolve-JavaHome $JavaHome
if (-not $javaHome) {
    Add-Failure 'JDK 17 not found; pass -JavaHome <dir> or set JAVA_HOME'
} else {
    Add-Pass "JAVA_HOME: $javaHome"
}

$gradle = Resolve-Gradle $GradleExe
if (-not $gradle) {
    Add-Failure 'Gradle not found; pass -GradleExe <gradle.bat> or put gradle on PATH'
} else {
    Add-Pass "gradle: $gradle"
}

# local.properties is generated and git-ignored; create it only when absent.
$localProps = Join-Path $androidDir 'local.properties'
$sdkDir = $null
if (Test-Path -LiteralPath $localProps) {
    foreach ($line in Get-Content -LiteralPath $localProps) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') { $sdkDir = $Matches[1].Trim() }
    }
}
if (-not $sdkDir) {
    if ($env:ANDROID_HOME) { $sdkDir = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $sdkDir = $env:ANDROID_SDK_ROOT }
    else { $sdkDir = 'C:\Users\Administrator\AppData\Local\Android\Sdk' }
    Set-Content -LiteralPath $localProps -Value ('sdk.dir=' + ($sdkDir -replace '\\', '/')) -Encoding ASCII
    Write-Info "created $localProps -> $sdkDir"
}
if (-not (Test-Path -LiteralPath $sdkDir)) {
    Add-Failure "Android SDK not found: $sdkDir (fix sdk.dir in app\android\local.properties)"
} else {
    Add-Pass "Android SDK: $sdkDir"
}

$adb = $null
if (-not $SkipDevice) {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) { $adb = $adbCmd.Source } else { Add-Failure 'adb not found on PATH (use -SkipDevice for build-only)' }
}

# --- build ---------------------------------------------------------------
$apk = Join-Path $androidDir 'app\build\outputs\apk\debug\app-debug.apk'
if ($javaHome -and $gradle) {
    Write-Info '[1/2] gradle assembleDebug --no-daemon'
    $gradleLog = Join-Path $OutDir 'gradle-assembleDebug.log'
    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:PATH
    try {
        $env:JAVA_HOME = $javaHome
        $gradleBin = Split-Path -Parent $gradle
        $env:PATH = (Join-Path $javaHome 'bin') + ';' + $gradleBin + ';' + $oldPath
        Push-Location -LiteralPath $androidDir
        try {
            Invoke-NativeStream { & $gradle assembleDebug --no-daemon --console=plain } $gradleLog
            $gradleCode = $LASTEXITCODE
        } finally {
            Pop-Location
        }
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:PATH = $oldPath
    }
    if ($gradleCode -ne 0) {
        Add-Failure "gradle assembleDebug exited $gradleCode (log: $gradleLog)"
    } elseif (-not (Test-Path -LiteralPath $apk)) {
        Add-Failure "APK not found after build: $apk"
    } else {
        $apkSize = (Get-Item -LiteralPath $apk).Length
        if ($apkSize -lt 100KB) {
            Add-Failure "APK is only $apkSize bytes, expected > 100KB"
        } else {
            Add-Pass "assembleDebug OK, $([math]::Round($apkSize / 1KB)) KB ($apk)"
            Copy-Item -LiteralPath $apk -Destination (Join-Path $OutDir 'app-debug.apk') -Force
        }
    }
}

# --- device leg ----------------------------------------------------------
$device = $null
if ($SkipDevice) {
    Write-Info '[2/2] device leg skipped (-SkipDevice)'
} else {
    Write-Info '[2/2] install -> launch -> logcat FATAL scan'
    if (-not $DeviceId) { $DeviceId = $env:LC_DEVICE_ID }
    if ($adb) {
        $deviceText = Invoke-NativeText { & $adb devices }
        $devices = @($deviceText -split "`n" | ForEach-Object {
            if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] }
        })
        if ($DeviceId) {
            if ($devices -contains $DeviceId) { $device = $DeviceId }
            else { Add-Failure "device $DeviceId is not connected (connected: $($devices -join ', '))" }
        } elseif ($devices.Count -eq 1) {
            $device = $devices[0]
        } elseif ($devices.Count -gt 1) {
            Add-Failure "multiple devices connected ($($devices -join ', ')); pass -DeviceId"
        } else {
            Add-Failure 'no adb device connected; pass -SkipDevice for build-only'
        }
    }

    $deviceReady = $false
    if ($device -and (Test-Path -LiteralPath $apk)) {
        Add-Pass "device: $device"

        Invoke-NativeText { & $adb -s $device logcat -c } | Out-Null

        # Install goes through tools\device\install-debug.ps1 (single implementation):
        # main path is adb push + pm install (no UI); the tool falls back to driving the
        # Huawei risk-prompt / auth dialog, and owns the app-market disable + restore.
        $installTool = Join-Path $repoRoot 'tools\device\install-debug.ps1'
        $installDir = Join-Path $OutDir 'install'
        if (-not (Test-Path -LiteralPath $installTool)) {
            Add-Failure "install tool missing: $installTool"
        } else {
            $installHost = (Get-Process -Id $PID).Path
            $installArgs = @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $installTool,
                '-ApkPath', $apk, '-Serial', $device, '-OutDir', $installDir
            )
            $installText = Invoke-NativeText { & $installHost @installArgs }
            $installCode = $LASTEXITCODE
            Set-Content -LiteralPath (Join-Path $OutDir 'adb-install.log') -Value $installText -Encoding UTF8
            $marketLine = @($installText -split "`n" | Where-Object { $_ -match '^MARKET_RESTORED=' })
            if ($installCode -eq 0) {
                Add-Pass ("install-debug.ps1: install + verify OK ({0})" -f ($marketLine -join ' '))
                $deviceReady = $true
            } else {
                Add-Failure ("install-debug.ps1 exited {0} ({1}); log: {2}" -f $installCode, ($marketLine -join ' '), (Join-Path $installDir 'install.log'))
            }
        }
    }

    if ($deviceReady) {
        $startLog = Invoke-NativeText { & $adb -s $device shell am start -W -n $activityName }
        Set-Content -LiteralPath (Join-Path $OutDir 'adb-start.log') -Value $startLog -Encoding UTF8
        if ($startLog -match 'Status: ok' -or $startLog -match 'Warning: Activity not started') {
            Add-Pass "am start: $activityName"
        } else {
            Add-Failure "am start did not report success: $($startLog.Trim())"
        }

        Start-Sleep -Seconds $LogWaitSeconds

        $pidText = (Invoke-NativeText { & $adb -s $device shell pidof $packageName }).Trim()
        if ($pidText -match '^\d+') {
            Add-Pass "process alive: $packageName pid=$pidText"
        } else {
            $psText = Invoke-NativeText { & $adb -s $device shell ps -A }
            if ($psText -match [regex]::Escape($packageName)) { Add-Pass "process alive (ps -A): $packageName" }
            else { Add-Failure "process $packageName is not running after launch" }
        }

        # Device screenshot is evidence only; failure to grab it must not fail the gate.
        $remoteShot = '/sdcard/lc-smoke.png'
        Invoke-NativeText { & $adb -s $device shell screencap -p $remoteShot } | Out-Null
        $localShot = Join-Path $OutDir 'device-screenshot.png'
        Invoke-NativeText { & $adb -s $device pull $remoteShot $localShot } | Out-Null
        if ((Test-Path -LiteralPath $localShot) -and (Get-Item -LiteralPath $localShot).Length -gt 5000) {
            Write-Info "device screenshot: $localShot"
        } else {
            Write-Info 'device screenshot not captured (not a gate failure)'
        }

        $logcatText = Invoke-NativeText { & $adb -s $device logcat -d -v threadtime }
        # 落盘前脱敏（tk=<web key>）；返回脱敏后的文本，后续 FATAL 统计用同一份。
        $logcatText = Save-RedactedText -Path (Join-Path $OutDir 'logcat.txt') -Text $logcatText -Label 'build-logcat' -Keys $redactKeys
        $logLines = @($logcatText -split "`n")
        $ourCrashes = New-Object System.Collections.Generic.List[string]
        for ($i = 0; $i -lt $logLines.Count; $i++) {
            if ($logLines[$i] -match 'FATAL EXCEPTION') {
                $end = [Math]::Min($i + 6, $logLines.Count - 1)
                $block = ($logLines[$i..$end] -join "`n")
                if ($block -match [regex]::Escape($packageName)) { $ourCrashes.Add($block) }
            }
        }
        if ($ourCrashes.Count -gt 0) {
            Add-Failure "logcat: $($ourCrashes.Count) FATAL EXCEPTION block(s) for $packageName"
            $ourCrashes | Select-Object -First 3 | ForEach-Object { Write-Host "       $_" -ForegroundColor Red }
        } else {
            Add-Pass 'logcat: no FATAL EXCEPTION for the app'
        }
    }
}

# --- result --------------------------------------------------------------
$result = 'FAIL'
if ($script:failures.Count -eq 0) { $result = 'PASS' }
$summary = @(
    'LC android gate',
    'time=' + (Get-Date).ToString('s'),
    "repo=$repoRoot",
    "result=$result",
    "java_home=$javaHome",
    "gradle=$gradle",
    "sdk=$sdkDir",
    "device=$device",
    "device_skipped=$([bool]$SkipDevice)",
    "apk=$apk"
) + @($script:failures | ForEach-Object { "failure=$_" })
Set-Content -LiteralPath (Join-Path $OutDir 'result.txt') -Value $summary -Encoding UTF8

Write-Host ''
if ($result -eq 'PASS') {
    if ($SkipDevice) {
        Write-Host 'ANDROID: PASS (build only; device leg skipped)' -ForegroundColor Green
    } else {
        Write-Host 'ANDROID: PASS' -ForegroundColor Green
    }
    exit 0
} else {
    Write-Host "ANDROID: FAIL ($($script:failures.Count) failed check(s))" -ForegroundColor Red
    exit 1
}
