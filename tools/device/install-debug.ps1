<#
.SYNOPSIS
  LC Android 一键装机（pm 主路径 + 纯净模式风险页 / 认证对话框两个 UI 兜底，市场临时禁用并 finally 恢复）。

.DESCRIPTION
  验收口径见 docs/qa/install-debug-verification.md（LC测试 维护，五场景 S1–S5）。本脚本：

  1) 记录**原版当前版本**（versionName/versionCode/lastUpdateTime）——设备可能被市场升到 1.6.3，
     基线仍冻结在 1.6.1，这里只留痕不改基线；
  2) 临时 `pm disable-user --user 0 <market>`（-SkipMarketDisable 可跳过），**finally 必 `pm enable` 并校验**；
  3) 安装：
     - `auto`（默认）：先 `pm install -r -t`；若返回 ABORTED/被拒，切 UI 路径；
     - `pm`：只走 pm，失败即失败；
     - `ui`：直接走 `adb install -r -t`（后台等确认）+ 轮询前台，自动点「继续安装/仍要安装」，
       遇到 UnifiedAuthenticationDialogActivity 从 secrets 文件注入 PIN；超时打印 INSTALL_AUTH_BLOCKED；
  4) 记录装机指纹（versionName/versionCode/lastUpdateTime/base.apk size+sha256），与本地 APK 比对。

.PARAMETER Device
  adb serial（别名 -DeviceId）。默认 $env:LC_DEVICE_ID，否则取唯一在线设备。

.PARAMETER Apk
  默认 app\android\app\build\outputs\apk\debug\app-debug.apk。

.PARAMETER InstallMode
  auto | pm | ui，默认 auto。

.PARAMETER PinFile
  锁屏 PIN 文件，默认 $env:LC_DEVICE_PIN_FILE，否则 E:\APPDetector\secrets\device-pin.txt。**PIN 永不打印**。

.PARAMETER UiTimeoutSeconds
  UI 路径最长等待，默认 120（S3 判据：超时不得死循环）。

.PARAMETER AdbPath
  指定 adb 可执行文件（离线自测用假 adb）。

.PARAMETER DryRun
  只打印将执行的命令，不碰设备。

.NOTES
  日志经 tests/lib/LogRedact.ps1 脱敏后落盘；PIN 值与 Authorization 永不进日志。
  用 pwsh 7 运行（LogRedact.ps1 是 UTF-8 无 BOM，Windows PowerShell 5.1 会读坏中文）。
#>
[CmdletBinding()]
param(
    [Alias('DeviceId')]
    [string]$Device = $(if ($env:LC_DEVICE_ID) { $env:LC_DEVICE_ID } else { '' }),
    [string]$Apk = '',
    [string]$Package = 'com.luopan.fengshui',
    [string]$OriginalPackage = 'space.xinzhi.globe',
    [string]$MarketPackage = 'com.huawei.appmarket',
    [ValidateSet('auto', 'pm', 'ui')]
    [string]$InstallMode = 'auto',
    [string]$PinFile = '',
    [int]$UiTimeoutSeconds = 120,
    [string]$OutDir = '',
    [string]$AdbPath = '',
    [switch]$Launch,
    [switch]$SkipMarketDisable,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path))
. (Join-Path $repoRoot 'tests\lib\LogRedact.ps1')

if (-not $Apk) { $Apk = Join-Path $repoRoot 'app\android\app\build\outputs\apk\debug\app-debug.apk' }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot ("checks\install-debug-" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
if (-not $PinFile) {
    $PinFile = if ($env:LC_DEVICE_PIN_FILE) { $env:LC_DEVICE_PIN_FILE } else { 'E:\APPDetector\secrets\device-pin.txt' }
}
$log = [System.Collections.Generic.List[string]]::new()
$script:restoreFailed = $false
$script:pinInjected = $false

function Write-Log([string]$line) {
    # 任何一行落日志前都过 PIN 兜底：PIN 值哪怕被拼进命令输出也不落盘/不回显
    if ($script:pinValue) { $line = $line.Replace($script:pinValue, '<PIN>') }
    $log.Add($line)
    Write-Host $line
}

function Resolve-Adb {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($candidate in @(
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
            (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'))) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    throw 'adb not found (PATH / %LOCALAPPDATA%\Android\Sdk\platform-tools)'
}

function Invoke-Adb([string[]]$parts, [switch]$AllowFail) {
    $shown = "adb -s $Device " + ($parts -join ' ')
    if ($DryRun) {
        Write-Log "[DRY] $shown"
        return @()
    }
    $output = & $adb -s $Device @parts 2>&1
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $AllowFail) {
        throw "adb failed ($code): $shown`n$($output -join "`n")"
    }
    return @($output | ForEach-Object { "$_" })
}

function Get-Focus {
    $raw = (Invoke-Adb @('shell', 'dumpsys', 'window') -AllowFail) -join "`n"
    $m = [regex]::Match($raw, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

function Get-UiXml {
    $remote = '/sdcard/lc-install-ui.xml'
    Invoke-Adb @('shell', 'rm', '-f', $remote) | Out-Null
    Invoke-Adb @('shell', 'uiautomator', 'dump', $remote) -AllowFail | Out-Null
    if ($DryRun) { return $null }
    $local = Join-Path $OutDir 'ui-install.xml'
    Invoke-Adb @('pull', $remote, $local) -AllowFail | Out-Null
    Invoke-Adb @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
    if (-not (Test-Path $local)) { return $null }
    try {
        return [xml](Get-Content -Raw -Encoding UTF8 $local)
    } catch {
        return $null
    }
}

function Find-TapTarget([object]$xml, [string[]]$labels) {
    if ($null -eq $xml) { return $null }
    foreach ($node in $xml.SelectNodes('//node')) {
        $text = [string]$node.text
        $desc = [string]$node.'content-desc'
        foreach ($label in $labels) {
            if ($text -eq $label -or $desc -eq $label) {
                $bounds = [string]$node.bounds
                $m = [regex]::Match($bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
                if ($m.Success) {
                    $x = [int]( ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2 )
                    $y = [int]( ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2 )
                    if ($x -gt 0 -and $y -gt 0) {
                        return [pscustomobject]@{ X = $x; Y = $y; Label = $label; Bounds = $bounds }
                    }
                }
            }
        }
    }
    return $null
}

function Tap-Target($target) {
    if ($null -eq $target) { return $false }
    Write-Log ("UI_TAP: '{0}' at ({1},{2}) bounds={3}" -f $target.Label, $target.X, $target.Y, $target.Bounds)
    Invoke-Adb @('shell', 'input', 'tap', "$($target.X)", "$($target.Y)") -AllowFail | Out-Null
    return $true
}

function Get-DeviceVersion {
    $info = Get-PackageInfo $Package
    if ($null -eq $info) { return '' }
    return "$($info.LastUpdateTime)|$($info.VersionCode)"
}

function Get-PackageInfo([string]$pkg) {
    $raw = Invoke-Adb @('shell', 'dumpsys', 'package', $pkg) -AllowFail
    $text = ($raw -join "`n")
    if (-not $text.Trim() -or $text -match 'Unable to find package') { return $null }
    $versionName = ''; $versionCode = ''; $lastUpdate = ''; $state = ''
    foreach ($line in $raw) {
        if (-not $versionName -and $line -match 'versionName=(\S+)') { $versionName = $Matches[1] }
        if (-not $versionCode -and $line -match 'versionCode=(\d+)') { $versionCode = $Matches[1] }
        if (-not $lastUpdate -and $line -match 'lastUpdateTime=(\S+)') { $lastUpdate = $Matches[1] }
        if (-not $state -and $line -match 'enabled=(\d|default|disabled-user)') { $state = $Matches[1] }
    }
    return [pscustomobject]@{
        Package = $pkg; VersionName = $versionName; VersionCode = $versionCode
        LastUpdateTime = $lastUpdate; Enabled = $state
    }
}

function Show-Info($info) {
    if ($null -eq $info) { return 'not installed' }
    $name = if ($info.VersionName) { $info.VersionName } else { '<unknown>' }
    return ("{0} (versionCode={1}) lastUpdateTime={2} enabled={3}" -f $name, $info.VersionCode, $info.LastUpdateTime, $info.Enabled)
}

function Read-Pin {
    if (-not (Test-Path $PinFile)) { return '' }
    $pin = (Get-Content -Raw -Encoding UTF8 $PinFile).Trim()
    if ($pin -notmatch '^\d{4,12}$') { return '' }
    return $pin
}

function Invoke-UiInstall {
    param([string]$ApkPath, [int]$TimeoutSeconds)

    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    $outFile = Join-Path $OutDir 'adb-install.out'
    $errFile = Join-Path $OutDir 'adb-install.err'
    $pin = Read-Pin
    $script:pinValue = $pin
    if ($pin) { Write-Log 'PIN: loaded from secrets file (value never printed)' } else { Write-Log 'PIN: not available (auth dialog will end as INSTALL_AUTH_BLOCKED)' }

    $startArgs = @('-s', $Device, 'install', '-r', '-t', $ApkPath)
    if ($adb -like '*.ps1') {
        $proc = Start-Process -FilePath 'pwsh' -ArgumentList (@('-NoProfile', '-File', $adb) + $startArgs) `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile -NoNewWindow -PassThru
    } else {
        $proc = Start-Process -FilePath $adb -ArgumentList $startArgs `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile -NoNewWindow -PassThru
    }
    Write-Log ("UI_INSTALL: started pid={0} timeout={1}s" -f $proc.Id, $TimeoutSeconds)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $authSeen = $false
    $tapped = @{}
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
        $focus = Get-Focus
        if ($focus) {
            if ($focus -match 'UnifiedAuthenticationDialogActivity') {
                $authSeen = $true
                if ($pin) {
                    $xml = Get-UiXml
                    $field = Find-TapTarget $xml @('密码', 'PIN', '请输入')
                    if ($field) { Tap-Target $field | Out-Null }
                    Write-Log 'UI_AUTH: injecting PIN'
                    Invoke-Adb @('shell', 'input', 'text', $pin) -AllowFail | Out-Null
                    Invoke-Adb @('shell', 'input', 'keyevent', '66') -AllowFail | Out-Null
                    $script:pinInjected = $true
                } else {
                    Write-Log 'INSTALL_AUTH_BLOCKED: auth dialog needs PIN but no secrets file'
                    try { $proc.Kill() } catch { }
                    return [pscustomobject]@{ Ok = $false; AuthBlocked = $true; Output = 'auth dialog without pin' }
                }
            } elseif ($focus -match 'packageinstaller|InstallStaging|RiskApp|PermissionActivity') {
                $xml = Get-UiXml
                $target = Find-TapTarget $xml @('继续安装', '仍要安装', '安装', '确定', '允许')
                if ($target -and -not $tapped.ContainsKey($target.Bounds)) {
                    $tapped[$target.Bounds] = $true
                    Tap-Target $target | Out-Null
                }
            }
        }
        Start-Sleep -Milliseconds 700
    }

    if (-not $proc.HasExited) {
        try { $proc.Kill() } catch { }
        $reason = if ($authSeen) { 'INSTALL_AUTH_BLOCKED: auth dialog did not clear within timeout' } else { 'INSTALL_TIMEOUT: install UI did not finish within timeout' }
        Write-Log $reason
        return [pscustomobject]@{ Ok = $false; AuthBlocked = $authSeen; Output = $reason }
    }

    $proc.WaitForExit(5000) | Out-Null
    $text = ''
    if (Test-Path $outFile) { $text += (Get-Content -Raw -Encoding UTF8 $outFile) }
    if (Test-Path $errFile) { $text += (Get-Content -Raw -Encoding UTF8 $errFile) }
    $text = $text.Trim()
    Write-Log ("UI_INSTALL_OUTPUT: {0}" -f ($text -replace "`r?`n", ' | '))
    if ($text -match 'Success') { return [pscustomobject]@{ Ok = $true; AuthBlocked = $false; Output = $text } }
    return [pscustomobject]@{ Ok = $false; AuthBlocked = $false; Output = $text }
}

# --- 0. 设备 ---
if (-not (Test-Path $Apk)) { throw "apk not found: $Apk" }
if (-not $DryRun) {
    $adb = if ($AdbPath) { $AdbPath } else { Resolve-Adb }
    $devices = @(& $adb devices | Select-String 'device$' | ForEach-Object { ($_ -split '\s+')[0] })
    if ($Device) {
        if ($devices -notcontains $Device) { throw "device $Device not online (adb devices: $($devices -join ', '))" }
    } else {
        if ($devices.Count -eq 0) { throw 'no adb device online' }
        if ($devices.Count -gt 1) { throw "multiple devices ($($devices -join ', ')); pass -Device" }
        $Device = $devices[0]
    }
} elseif (-not $Device) {
    $Device = '<auto>'
}

$apkHash = (Get-FileHash -Algorithm SHA256 -Path $Apk).Hash.ToLowerInvariant()
$apkBytes = (Get-Item $Apk).Length
Write-Log ("INSTALL: device={0} pkg={1} mode={2}" -f $Device, $Package, $InstallMode)
Write-Log ("APK: {0} bytes sha256={1}" -f $apkBytes, $apkHash)

$marketDisabledHere = $false

try {
    # --- 1. 原版当前版本（版本漂移留痕）---
    if ($DryRun) {
        Write-Log ("ORIGINAL_VERSION: [DRY] would read dumpsys package {0}" -f $OriginalPackage)
    } else {
        $orig = Get-PackageInfo $OriginalPackage
        Write-Log ("ORIGINAL_VERSION: {0} -> {1}" -f $OriginalPackage, (Show-Info $orig))
        if ($orig -and $orig.VersionName -ne '1.6.1') {
            Write-Log ("WARN: original baseline was frozen at 1.6.1, device has {0} (recorded, baseline unchanged)" -f $orig.VersionName)
        }
    }

    # --- 2. 市场临时禁用 ---
    if (-not $SkipMarketDisable) {
        if ($DryRun) {
            Write-Log ("[DRY] adb -s {0} shell pm disable-user --user 0 {1}" -f $Device, $MarketPackage)
            Write-Log ("[DRY] finally + adb -s {0} shell pm enable {1}" -f $Device, $MarketPackage)
        } else {
            $market = Get-PackageInfo $MarketPackage
            Write-Log ("MARKET: {0} -> {1}" -f $MarketPackage, (Show-Info $market))
            if ($market -and $market.Enabled -ne 'disabled-user') {
                Invoke-Adb @('shell', 'pm', 'disable-user', '--user', '0', $MarketPackage) | Out-Null
                $marketDisabledHere = $true
                Write-Log 'MARKET: disabled (will be restored in finally)'
            } else {
                Write-Log 'MARKET: already disabled/absent; nothing to change'
            }
        }
    } else {
        Write-Log 'MARKET: -SkipMarketDisable, left untouched'
    }

    $before = if ($DryRun) { '' } else { Get-DeviceVersion }

    # --- 3. 安装 ---
    $installed = $false
    if ($InstallMode -in @('auto', 'pm')) {
        $remote = '/data/local/tmp/lc-app-debug.apk'
        Invoke-Adb @('push', $Apk, $remote) -AllowFail | Out-Null
        $out = Invoke-Adb @('shell', 'pm', 'install', '-r', '-t', $remote) -AllowFail
        $text = ($out -join "`n")
        Write-Log ("PM_INSTALL_OUTPUT: {0}" -f ($text -replace "`r?`n", ' | '))
        if ($DryRun) {
            Write-Log 'INSTALL: [DRY] would run pm install -r -t'
            $installed = $true
        } elseif ($text -match 'Success') {
            Write-Log 'INSTALL: Success (pm)'
            $installed = $true
        } else {
            Write-Log 'INSTALL: pm path did not succeed; see UI fallback decision'
        }
        Invoke-Adb @('shell', 'rm', '-f', $remote) -AllowFail | Out-Null
    }

    if (-not $installed -and -not $DryRun -and $InstallMode -in @('auto', 'ui')) {
        Write-Log 'INSTALL: switching to UI path (risk page / auth dialog)'
        $ui = Invoke-UiInstall -ApkPath $Apk -TimeoutSeconds $UiTimeoutSeconds
        if (-not $ui.Ok) {
            if ($ui.AuthBlocked) {
                Write-Log 'INSTALL_AUTH_BLOCKED: giving up without retry (S3 判据：不得死循环)'
                throw [System.Exception]::new('AUTH_BLOCKED')
            }
            throw [System.Exception]::new("ui install failed: $($ui.Output)")
        }
        Write-Log 'INSTALL: Success (ui)'
        $installed = $true
    } elseif (-not $installed -and $DryRun) {
        $installed = $true
    }

    if (-not $installed) {
        throw 'install failed (mode=' + $InstallMode + ')'
    }

    # --- 4. 装机指纹 ---
    if ($DryRun) {
        Write-Log ("INSTALLED: [DRY] would read dumpsys package {0} + pm path + sha256sum" -f $Package)
    } else {
        $after = Get-PackageInfo $Package
        Write-Log ("INSTALLED: {0} -> {1}" -f $Package, (Show-Info $after))
        if ($before -and $after -and $before -eq (Get-DeviceVersion)) {
            Write-Log 'WARN: lastUpdateTime/versionCode unchanged; install may have been a no-op (check S1 判据)'
        }
        $pathOut = (Invoke-Adb @('shell', 'pm', 'path', $Package) -AllowFail) -join "`n"
        $basePath = ($pathOut -split "`n" | Where-Object { $_ -match '^package:' } | Select-Object -First 1) -replace '^package:', ''
        if ($basePath) {
            $size = ((Invoke-Adb @('shell', 'stat', '-c', '%s', $basePath) -AllowFail) -join '').Trim()
            $sha = (((Invoke-Adb @('exec-out', 'sha256sum', $basePath) -AllowFail) -join '') -split '\s+')[0]
            Write-Log ("BASE_APK: {0} size={1} sha256={2}" -f $basePath, $size, $sha)
            if ($sha -and $sha.ToLowerInvariant() -ne $apkHash) {
                Write-Log 'WARN: installed base.apk sha256 != local apk (another install overwrote it?)'
            }
        }
    }

    # --- 5. 可选冷启 ---
    if ($Launch) {
        Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$Package/.SplashActivity") | Out-Null
        Write-Log 'LAUNCH: am start .SplashActivity'
    }
} catch {
    if ($_.Exception.Message -eq 'AUTH_BLOCKED') {
        Write-Log 'INSTALL: exit 3 (INSTALL_AUTH_BLOCKED)'
        $exitCode = 3
    } else {
        Write-Log ("ERROR: {0}" -f $_.Exception.Message)
        $exitCode = 1
    }
} finally {
    if ($marketDisabledHere -and -not $DryRun) {
        try {
            Invoke-Adb @('shell', 'pm', 'enable', $MarketPackage) | Out-Null
            $after = Get-PackageInfo $MarketPackage
            if ($after -and $after.Enabled -eq 'disabled-user') {
                $script:restoreFailed = $true
                Write-Log "RESTORE_FAILED: $MarketPackage still disabled-user after pm enable"
            } else {
                Write-Log ("MARKET: restored -> {0}" -f (Show-Info $after))
            }
        } catch {
            $script:restoreFailed = $true
            Write-Log ("RESTORE_FAILED: {0}" -f $_.Exception.Message)
        }
    }
}

if ($script:restoreFailed) {
    $exitCode = 1
    Write-Log 'RESULT: FAIL (market restore failed; fix manually: pm enable <market>)'
} else {
    if ($null -eq $exitCode) { $exitCode = 0 }
    Write-Log ("RESULT: {0}" -f $(if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }))
}

if (-not $DryRun) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    Save-RedactedText -Path (Join-Path $OutDir 'install.log') -Text ($log -join "`n")
    Write-Host ("LOG: {0}" -f (Join-Path $OutDir 'install.log'))
}
exit $exitCode
