<#
.SYNOPSIS
  Install the LC debug APK on a Huawei device, auto-confirming the install risk prompts.

.DESCRIPTION
  Main path (no UI):  adb push -> adb shell pm install -r -t
  Fallback (UI):      adb install, then drive PackageInstaller / AppGallery /
                      UnifiedAuthenticationDialogActivity via uiautomator dump.

  What it does, in order:
    1. pre-state: `pm path`, `dumpsys package` (lastUpdateTime / versionCode),
       market enabled state, `settings get secure pure_mode_state` (info only).
    2. temporarily disables the app market (only if it was enabled) and in
       `finally` restores the ORIGINAL state, including error paths.
    3. main path `adb push` + `pm install -r -t`; success requires `pm path`
       and `lastUpdateTime` (or pm path) to change.
    4. fallback `adb install` in a background job + UI loop: read
       `dumpsys window | mCurrentFocus`, dump the UI, tap only dictionary
       buttons by their bounds centre. No blind taps.
    5. auth dialog (`UnifiedAuthenticationDialogActivity`): type the lock-screen
       PIN from a repo-external secrets file / env var and ENTER. The PIN value
       is never printed or logged. A second auth prompt is a hard stop.
    6. final assertions: `pm path` non-empty, versionCode present, lastUpdateTime
       changed; logs elapsed ms and the path taken (pm / adb+ui / +pin).

  Nothing on the device is touched besides: the temp APK under
  /data/local/tmp, the install, the app market's enabled state (restored) and
  taps on the install dialogs. No other package is installed or cleared.

.PARAMETER ApkPath
  Full path to app-debug.apk (required).

.PARAMETER Serial
  adb serial. Default: $env:LC_DEVICE_ID, else the only connected device.

.PARAMETER PackageName
  App package to verify. Default: com.luopan.fengshui.

.PARAMETER MarketPackage
  Huawei app market package that can steal the foreground. Default: com.huawei.appmarket.

.PARAMETER PinFile
  Repo-external .properties file holding the lock-screen PIN.
  Default: $env:LC_DEVICE_PIN_FILE, else E:\APPDetector\secrets\device.properties

.PARAMETER PinKey
  Property name inside PinFile. Default: device.lock_pin
  Env override: DSH_SECRET_DEVICE_PIN

.PARAMETER OutDir
  Log/dump directory. Default: <repo>\checks\device-install-<timestamp> (git-ignored).

.PARAMETER UiTimeoutSec
  How long to drive the install UI before giving up. Default: 90.

.PARAMETER SkipMarketDisable
  Do not disable the app market during install.

.PARAMETER KeepMarketDisabledUntil
  Leave the app market disabled after the install instead of restoring it.
  Value: a parseable time (e.g. "2026-11-08 09:00" or "20:00") or the literal
  "manual". Use this when the market could roll the app back right after install
  (Huawei AppGallery auto-updates installed apps). The script logs and writes
  `market-kept-disabled.txt` with the exact restore command; default behaviour
  (no flag) is to restore the market's original state in `finally`.

.PARAMETER OriginalPackage
  Optional third-party package whose version is logged for troubleshooting
  (the frozen reverse-engineering baseline). Default: space.xinzhi.globe
  Note: the device may auto-update it (observed 1.6.1 -> 1.6.3 on 2026-11-07),
  so this is logged as information only, never asserted.

.PARAMETER AllowSameUpdateTime
  Accept unchanged lastUpdateTime when pmPath also did not change (only for
  devices whose package dump has coarse granularity). Default: strict.

.NOTES
  Exit codes (documented in docs/qa/device-method.md):
    0  INSTALL_OK
    1  UNEXPECTED_ERROR
    2  BAD_INPUT              (APK missing / bad parameter)
    3  ADB_UNAVAILABLE        (adb not on PATH)
    4  DEVICE_NOT_FOUND       (no device / multiple devices without -Serial)
    10 INSTALL_FAILED         (pm and adb install both failed)
    11 VERIFY_FAILED          (installed but pm path / lastUpdateTime / versionCode check failed)
    12 UI_BUTTON_NOT_FOUND    (install UI shown, dictionary matched nothing; dump path logged)
    13 INSTALL_AUTH_BLOCKED   (auth still up after PIN, or no PIN configured)
    14 UI_TIMEOUT             (unused by the current loop; kept for callers)
    15 MARKET_RESTORE_FAILED  (we disabled the market and failed to re-enable it)

  Exit 0 means: the package on the device is the APK we pushed, verified by
  pm path + lastUpdateTime (+ versionCode logged).

  Market safety (device findings 2026-11-07):
    - Huawei AppGallery auto-updates installed apps and had rolled the original
      app from 1.6.1 to 1.6.3, so the market is disabled for the install window.
    - The whole install runs inside try/finally; `finally` re-enables only a
      market that THIS run disabled (an already-disabled market is left alone).
    - Ctrl+C / host exit is covered by a PowerShell.Exiting handler that enables
      the market as a last resort (unregistered once the normal restore ran).
    - The last line is always `MARKET_RESTORED=ok|fail|skipped`:
        ok      = restored (or never disabled here) and verified absent from `pm list packages -d`
        fail    = we disabled it and could not re-enable it -> exit 15
        skipped = not installed / already disabled by someone else / -SkipMarketDisable /
                  -KeepMarketDisabledUntil (intentional, see market-kept-disabled.txt)
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$Serial,
    [string]$PackageName = 'com.luopan.fengshui',
    [string]$MarketPackage = 'com.huawei.appmarket',
    [string]$PinFile,
    [string]$PinKey = 'device.lock_pin',
    [string]$OutDir,
    [int]$UiTimeoutSec = 90,
    [switch]$SkipMarketDisable,
    [switch]$AllowSameUpdateTime,
    [string]$KeepMarketDisabledUntil,
    [string]$OriginalPackage = 'space.xinzhi.globe'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $repoRoot ('checks\device-install-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$script:logPath = Join-Path $OutDir 'install.log'
if (Test-Path -LiteralPath $script:logPath) { Remove-Item -LiteralPath $script:logPath -Force }

# Buttons we are allowed to press, in priority order (specific before generic).
$script:ButtonDict = @('继续安装', '仍要安装', '确认安装', '安装', '允许', '同意', '我知道了', '继续')
$script:RemoteApk = '/data/local/tmp/lc-debug.apk'
$script:RemoteDump = '/sdcard/lc-install-ui.xml'

if (-not $PinFile) { $PinFile = $env:LC_DEVICE_PIN_FILE }
if (-not $PinFile) { $PinFile = 'E:\APPDetector\secrets\device.properties' }

$script:adb = $null
$script:serial = $null
$script:stage = New-Object System.Collections.Generic.List[string]
$script:pin = $null
$script:pinTried = $false
$script:weDisabledMarket = $false
$script:pmPathBefore = ''
$script:lastUpdateBefore = ''
$script:lastFocusLogged = $null
$script:keepMarketUntil = $null
$script:exitingHandler = $null
$script:marketRestoreState = 'skipped'
$script:inMainTry = $false

function Log([string]$Message) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss.fff'), $Message
    Write-Host $line
    Add-Content -LiteralPath $script:logPath -Value $line -Encoding UTF8
}

function Fail([int]$Code, [string]$Message) {
    Log ("FAIL [{0}] {1}" -f $Code, $Message)
    if ($script:inMainTry) { throw ('LCINSTALL_EXIT:' + $Code) }
    # Preflight failure: the market was never touched, but keep the output contract.
    Log 'MARKET_RESTORED=skipped'
    Write-Host 'MARKET_RESTORED=skipped'
    exit $Code
}

function Invoke-Adb {
    param([string[]]$Arguments)
    $argv = @()
    if ($script:serial) { $argv += @('-s', $script:serial) }
    $argv += $Arguments
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $script:adb @argv 2>&1)
        $exit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    $text = (($output | ForEach-Object { $_.ToString() }) -join "`n")
    return @{ text = $text; code = $exit }
}

function Get-Focus {
    $r = Invoke-Adb @('shell', 'dumpsys', 'window')
    $m = [regex]::Match($r.text, 'mCurrentFocus=Window\{[^}]*?\s(?<comp>[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)\}')
    if ($m.Success) { return $m.Groups['comp'].Value }
    return ''
}

function Get-FocusKind([string]$Component) {
    if (-not $Component) { return 'none' }
    if ($Component -match 'UnifiedAuthentication|unifiedauth|AuthenticationDialog|Keyguard') { return 'auth' }
    # Real-device flow (2026-11-07): risk page -> confirm page -> .InstallSuccess.
    # The success page has no install button; treat it as "done", never tap it.
    if ($Component -match 'InstallSuccess') { return 'done' }
    if ($Component -match '(?i)packageinstaller|appmarket|appgallery|install') { return 'ui' }
    if ($Component -match [regex]::Escape($PackageName)) { return 'app' }
    return 'other'
}

function Get-LastUpdateTime([string]$PkgDump) {
    $m = [regex]::Match($PkgDump, 'lastUpdateTime=([^\r\n]+)')
    if ($m.Success) { return $m.Groups[1].Value.Trim() }
    return ''
}

function Get-VersionCode([string]$PkgDump) {
    $m = [regex]::Match($PkgDump, 'versionCode=(\d+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

function Get-InstallState {
    $pm = Invoke-Adb @('shell', 'pm', 'path', $PackageName)
    $dumpText = (Invoke-Adb @('shell', 'dumpsys', 'package', $PackageName)).text
    return @{
        pmPath      = $pm.text.Trim()
        dump        = $dumpText
        lastUpdate  = Get-LastUpdateTime $dumpText
        versionCode = Get-VersionCode $dumpText
    }
}

function Test-InstalledChanged {
    $now = Get-InstallState
    if (-not $now.pmPath) { return $false }
    if (-not $script:pmPathBefore -and -not $script:lastUpdateBefore) { return $true }  # first install
    if ($AllowSameUpdateTime) { return $true }
    $pathChanged = ($script:pmPathBefore -and $now.pmPath -ne $script:pmPathBefore)
    $timeChanged = ($script:lastUpdateBefore -and $now.lastUpdate -ne $script:lastUpdateBefore)
    return [bool]($pathChanged -or $timeChanged)
}

function Invoke-UiDump([string]$Tag) {
    Invoke-Adb @('shell', 'rm', '-f', $script:RemoteDump) | Out-Null
    $r = Invoke-Adb @('shell', 'uiautomator', 'dump', $script:RemoteDump)
    # docs/qa/device-method.md: dump can exit 0 yet write nothing; never pull a stale file.
    if ($r.text -notmatch 'dumped to') { return $null }
    $local = Join-Path $OutDir ('ui-' + $Tag + '-' + (Get-Date -Format 'HHmmss') + '.xml')
    Invoke-Adb @('pull', $script:RemoteDump, $local) | Out-Null
    if (-not (Test-Path -LiteralPath $local)) { return $null }
    try { $xml = [xml](Get-Content -LiteralPath $local -Raw) } catch { return $null }
    return @{ path = $local; xml = $xml }
}

function Find-ButtonNode($Xml) {
    if (-not $Xml) { return $null }
    $nodes = @($Xml.SelectNodes('//node'))

    foreach ($word in $script:ButtonDict) {
        $candidates = @($nodes | Where-Object {
                ($_.'text' -eq $word) -or ($_.'content-desc' -eq $word)
            })
        foreach ($node in $candidates) {
            if ($node.enabled -eq 'true' -and $node.bounds -match '\[\d+,\d+\]\[\d+,\d+\]') {
                return @{ word = $word; node = $node }
            }
        }
    }
    # Decorated labels ("立即安装"): dictionary order keeps "继续安装" from being hit via "安装".
    foreach ($word in $script:ButtonDict) {
        $candidates = @($nodes | Where-Object {
                (($_.'text') -and ($_.'text'.Contains($word))) -or
                (($_.'content-desc') -and ($_.'content-desc'.Contains($word)))
            })
        foreach ($node in $candidates) {
            if ($node.enabled -eq 'true' -and $node.bounds -match '\[\d+,\d+\]\[\d+,\d+\]') {
                return @{ word = $word; node = $node }
            }
        }
    }
    return $null
}

function Tap-Node($Found) {
    $node = $Found.node
    if ($node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { return $false }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    if ($x -le 0 -or $y -le 0) { return $false }
    Invoke-Adb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
    $label = $node.'text'
    if (-not $label) { $label = $node.'content-desc' }
    Log ("tap '{0}' at ({1},{2})" -f $label, $x, $y)
    return $true
}

function Get-LockPin {
    $envPin = $env:DSH_SECRET_DEVICE_PIN
    if ($envPin) { return $envPin.Trim() }
    if (-not (Test-Path -LiteralPath $PinFile)) { return '' }
    foreach ($line in Get-Content -LiteralPath $PinFile) {
        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') {
            if ($Matches[1].Trim() -eq $PinKey) { return $Matches[2].Trim() }
        }
    }
    return ''
}

function Invoke-AuthPin([string]$Component) {
    if ($script:pinTried) {
        Fail 13 ("auth dialog came back after one PIN attempt ({0}); refusing blind retries (INSTALL_AUTH_BLOCKED)" -f $Component)
    }
    if (-not $script:pin) {
        Fail 13 ("auth dialog up ({0}) but no lock PIN configured; put '{1}=<pin>' in {2} or set DSH_SECRET_DEVICE_PIN (INSTALL_AUTH_BLOCKED)" -f $Component, $PinKey, $PinFile)
    }
    $script:pinTried = $true
    Log 'auth dialog detected: injecting PIN (value never logged) + ENTER'
    Invoke-Adb @('shell', 'input', 'text', $script:pin) | Out-Null
    Start-Sleep -Milliseconds 300
    Invoke-Adb @('shell', 'input', 'keyevent', '66') | Out-Null
    Start-Sleep -Seconds 2
    if (-not $script:stage.Contains('pin')) { [void]$script:stage.Add('pin') }
    $after = Get-Focus
    if ((Get-FocusKind $after) -eq 'auth') {
        Fail 13 ("still on auth dialog after PIN ({0}); device may be policy-locked (INSTALL_AUTH_BLOCKED)" -f $after)
    }
    Log ("auth cleared, focus now: {0}" -f $after)
}

function Restore-Market {
    if ($script:exitingHandler) {
        try { Unregister-Event -SubscriptionId $script:exitingHandler.Id -ErrorAction SilentlyContinue } catch { }
        $script:exitingHandler = $null
    }
    if (-not $script:weDisabledMarket) {
        $script:marketRestoreState = 'skipped'
        Log 'market: not disabled by this run, nothing to restore (existing state untouched)'
        return $true
    }
    $r = Invoke-Adb @('shell', 'pm', 'enable', $MarketPackage)
    $disabledList = (Invoke-Adb @('shell', 'pm', 'list', 'packages', '-d')).text
    $stillDisabled = @($disabledList -split "`n" | Where-Object { $_.Trim() -eq ('package:' + $MarketPackage) }).Count -gt 0
    if (-not $stillDisabled) {
        $script:marketRestoreState = 'ok'
        Log ("market restored: {0} re-enabled (pm enable said: {1})" -f $MarketPackage, ($r.text -replace "`r?`n", ' | ').Trim())
        return $true
    }
    $script:marketRestoreState = 'fail'
    Log ("FAIL [15] could not re-enable {0}: {1}" -f $MarketPackage, $r.text.Trim())
    return $false
}

# ---------------------------------------------------------------- preflight
$ApkPath = [System.IO.Path]::GetFullPath($ApkPath)
if (-not (Test-Path -LiteralPath $ApkPath)) { Fail 2 ("APK not found: {0}" -f $ApkPath) }
$apkItem = Get-Item -LiteralPath $ApkPath
if ($apkItem.Length -lt 100KB) { Fail 2 ("APK too small ({0} bytes): {1}" -f $apkItem.Length, $ApkPath) }

$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCmd) { Fail 3 'adb not found on PATH' }
$script:adb = $adbCmd.Source

if (-not $Serial) { $Serial = $env:LC_DEVICE_ID }
$deviceText = (Invoke-Adb @('devices')).text
$devices = @($deviceText -split "`n" | ForEach-Object { if ($_ -match '^(\S+)\s+device\s*$') { $Matches[1] } })
if ($Serial) {
    if ($devices -notcontains $Serial) { Fail 4 ("device {0} is not connected (connected: {1})" -f $Serial, ($devices -join ', ')) }
    $script:serial = $Serial
} elseif ($devices.Count -eq 1) {
    $script:serial = $devices[0]
} elseif ($devices.Count -gt 1) {
    Fail 4 ("multiple devices connected ({0}); pass -Serial" -f ($devices -join ', '))
} else {
    Fail 4 'no adb device connected'
}

Log ("repo: {0}" -f $repoRoot)
Log ("out : {0}" -f $OutDir)
Log ("apk : {0} ({1} bytes)" -f $ApkPath, $apkItem.Length)
Log ("device: {0}" -f $script:serial)

# ---------------------------------------------------------------- pre-state
$pre = Get-InstallState
$script:pmPathBefore = $pre.pmPath
$script:lastUpdateBefore = $pre.lastUpdate
Log ("pre: pmPath='{0}' lastUpdateTime='{1}' versionCode='{2}'" -f $pre.pmPath, $pre.lastUpdate, $pre.versionCode)

$pureMode = (Invoke-Adb @('shell', 'settings', 'get', 'secure', 'pure_mode_state')).text.Trim()
Log ("pure_mode_state={0} (info only; never modified)" -f $pureMode)

$marketPath = (Invoke-Adb @('shell', 'pm', 'path', $MarketPackage)).text.Trim()
$marketInstalled = [bool]$marketPath
$marketWasDisabled = $false
if ($marketInstalled) {
    $disabledList = (Invoke-Adb @('shell', 'pm', 'list', 'packages', '-d')).text
    $marketWasDisabled = @($disabledList -split "`n" | Where-Object { $_.Trim() -eq ('package:' + $MarketPackage) }).Count -gt 0
    $marketVersion = (Invoke-Adb @('shell', 'dumpsys', 'package', $MarketPackage)).text
    $marketVc = Get-VersionCode $marketVersion
    Log ("market {0}: installed, versionCode={1}, initiallyEnabled={2}" -f $MarketPackage, $marketVc, (-not $marketWasDisabled))
} else {
    Log ("market {0}: not installed on this device" -f $MarketPackage)
}

# Information only: the reverse-engineering baseline is frozen at 1.6.1, but the
# device's own copy of the original APK can be auto-updated by the app market
# (observed 1.6.3 on 2026-11-07). Never asserted, just logged for troubleshooting.
if ($OriginalPackage) {
    $origPath = (Invoke-Adb @('shell', 'pm', 'path', $OriginalPackage)).text.Trim()
    if ($origPath) {
        $origDump = (Invoke-Adb @('shell', 'dumpsys', 'package', $OriginalPackage)).text
        $origVn = [regex]::Match($origDump, 'versionName=([^\s]+)').Groups[1].Value
        $origVc = Get-VersionCode $origDump
        Log ("original-package {0}: versionName={1} versionCode={2} lastUpdateTime={3} (device value; frozen baseline may differ)" -f $OriginalPackage, $origVn, $origVc, (Get-LastUpdateTime $origDump))
    } else {
        Log ("original-package {0}: not installed on this device" -f $OriginalPackage)
    }
}

$script:keepMarketUntil = $null
if ($KeepMarketDisabledUntil) {
    if ($KeepMarketDisabledUntil -eq 'manual') {
        $script:keepMarketUntil = 'manual'
    } else {
        try {
            $script:keepMarketUntil = ([datetime]::Parse($KeepMarketDisabledUntil)).ToString('yyyy-MM-dd HH:mm:ss')
        } catch {
            Fail 2 ("-KeepMarketDisabledUntil expects a parseable time or 'manual', got: {0}" -f $KeepMarketDisabledUntil)
        }
    }
    Log ("market will be left disabled until: {0} (per -KeepMarketDisabledUntil)" -f $script:keepMarketUntil)
}

$started = Get-Date
$exitCode = 0
$installJob = $null
$jobResult = $null
$adbInstallLog = Join-Path $OutDir 'adb-install.log'

try {
    $script:inMainTry = $true
    # ------------------------------------------------ market off (temporary)
    if ($marketInstalled -and -not $SkipMarketDisable) {
        if ($marketWasDisabled) {
            Log 'market already disabled by the user; leaving its state alone'
        } else {
            $r = Invoke-Adb @('shell', 'pm', 'disable-user', '--user', '0', $MarketPackage)
            if ($r.text -match 'disabled-user|new state: disabled') {
                $script:weDisabledMarket = $true
                Log ("market disabled for the install: {0}" -f $MarketPackage)
                # Last-resort restore: PowerShell fires this when the host exits,
                # including Ctrl+C / window close. Unregistered once restored.
                $script:exitingHandler = Register-EngineEvent -SourceIdentifier PowerShell.Exiting `
                    -MessageData @{ adb = $script:adb; serial = $script:serial; market = $MarketPackage } `
                    -Action {
                        $d = $Event.MessageData
                        if ($d.adb -and $d.serial) {
                            & $d.adb -s $d.serial shell pm enable $d.market 2>&1 | Out-Null
                        }
                    }
            } else {
                Log ("WARN could not disable {0} (continuing; UI path copes): {1}" -f $MarketPackage, $r.text.Trim())
            }
        }
    } elseif ($SkipMarketDisable) {
        Log 'market disable skipped (-SkipMarketDisable)'
    }

    # ------------------------------------------------ main path: pm install
    Log ("push -> {0}" -f $script:RemoteApk)
    $push = Invoke-Adb @('push', $ApkPath, $script:RemoteApk)
    if ($push.code -ne 0) { Log ("WARN adb push exited {0}: {1}" -f $push.code, $push.text.Trim()) }

    Log 'main path: pm install -r -t (no UI expected)'
    $pm = Invoke-Adb @('shell', 'pm', 'install', '-r', '-t', $script:RemoteApk)
    Log ("pm install output: {0}" -f (($pm.text -replace "`r?`n", ' | ')).Trim())
    $pmOk = ($pm.text -match '(?m)^\s*Success') -and (Test-InstalledChanged)
    if ($pmOk) { [void]$script:stage.Add('pm'); Log 'pm install succeeded (no UI needed)' }

    # ------------------------------------------------ fallback: adb install + UI
    if (-not $pmOk) {
        Log 'fallback: adb install (this is where the risk prompt appears)'
        [void]$script:stage.Add('adb')
        $installJob = Start-Job -ScriptBlock {
            param($Adb, $Serial, $Apk)
            $out = @(& $Adb -s $Serial install -r -t $Apk 2>&1 | ForEach-Object { $_.ToString() })
            [pscustomobject]@{ exit = $LASTEXITCODE; text = ($out -join "`n") }
        } -ArgumentList $script:adb, $script:serial, $ApkPath
        Log ("adb install started (job {0})" -f $installJob.Id)

        $deadline = (Get-Date).AddSeconds($UiTimeoutSec)
        $noButton = 0
        $quietChecks = 0
        while ((Get-Date) -lt $deadline) {
            if (Test-InstalledChanged) { break }

            if ($installJob.State -ne 'Running' -and -not $jobResult) {
                $jobResult = Receive-Job -Job $installJob
                $jobText = ''
                if ($jobResult -and $jobResult.text) { $jobText = $jobResult.text }
                Log ("adb install finished: exit={0} output={1}" -f $jobResult.exit, ($jobText -replace "`r?`n", ' | '))
                Set-Content -LiteralPath $adbInstallLog -Value $jobText -Encoding UTF8
                $quietChecks = 0
            }

            $focus = Get-Focus
            $kind = Get-FocusKind $focus
            if ($focus -ne $script:lastFocusLogged) {
                Log ("focus={0} -> {1}" -f $focus, $kind)
                $script:lastFocusLogged = $focus
            }

            if ($kind -eq 'done') {
                # .InstallSuccess: installation is complete, nothing to press.
                Start-Sleep -Seconds 1
                continue
            }

            if ($kind -eq 'auth') {
                Invoke-AuthPin $focus
                continue
            }

            if ($kind -eq 'ui') {
                $dump = Invoke-UiDump 'install'
                if (-not $dump) { Start-Sleep -Seconds 1; continue }
                $found = Find-ButtonNode $dump.xml
                if ($found) {
                    if (-not $script:stage.Contains('ui')) { [void]$script:stage.Add('ui') }
                    if (-not (Tap-Node $found)) {
                        Fail 12 ("matched button '{0}' but bounds unusable (dump: {1})" -f $found.word, $dump.path)
                    }
                    $noButton = 0
                    Start-Sleep -Seconds 2
                    continue
                }
                $noButton++
                Log ("no known button on install UI (attempt {0}; dump: {1})" -f $noButton, $dump.path)
                if ($noButton -ge 3) {
                    Fail 12 ("install UI kept showing but none of [{0}] matched; dump: {1}" -f ($script:ButtonDict -join '/'), $dump.path)
                }
                Start-Sleep -Seconds 2
                continue
            }

            if ($installJob.State -ne 'Running' -and $kind -ne 'ui' -and $kind -ne 'auth') {
                $quietChecks++
                if ($quietChecks -ge 3) { break }
            }
            Start-Sleep -Seconds 1
        }
    }

    # ------------------------------------------------ final verification
    if (-not (Test-InstalledChanged)) {
        $reason = 'install did not complete'
        if ($jobResult) { $reason = ("adb install exit {0}: {1}" -f $jobResult.exit, (($jobResult.text -replace "`r?`n", ' | '))) }
        $focusEnd = Get-Focus
        if ((Get-FocusKind $focusEnd) -eq 'auth') { Fail 13 ("auth dialog still up at the end ({0})" -f $focusEnd) }
        Fail 10 ("{0}; focus='{1}'; logs: {2}" -f $reason, $focusEnd, $OutDir)
    }

    $post = Get-InstallState
    if (-not $post.pmPath) { Fail 11 'pm path came back empty after install' }
    if (-not $post.versionCode) { Fail 11 'dumpsys package has no versionCode after install' }
    if (-not $AllowSameUpdateTime) {
        $pathChanged = ($script:pmPathBefore -and $post.pmPath -ne $script:pmPathBefore)
        $timeChanged = ($script:lastUpdateBefore -and $post.lastUpdate -ne $script:lastUpdateBefore)
        if (-not $pathChanged -and -not $timeChanged -and $script:lastUpdateBefore) {
            Fail 11 ("neither pm path nor lastUpdateTime changed (lastUpdateTime={0}); cannot prove this APK was installed" -f $post.lastUpdate)
        }
    }

    # Leave the device on a clean foreground: dismiss the system InstallSuccess page.
    $focusAfter = Get-Focus
    if ((Get-FocusKind $focusAfter) -eq 'done') {
        Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
        Log 'dismissed InstallSuccess page (BACK)'
    }

    $elapsed = ((Get-Date) - $started).TotalMilliseconds
    $path = ($script:stage -join '+')
    Log ("[PASS] install: package={0} versionCode={1}" -f $PackageName, $post.versionCode)
    Log ("[PASS] install: pmPath {0} -> {1}" -f $script:pmPathBefore, $post.pmPath)
    Log ("[PASS] install: lastUpdateTime {0} -> {1}" -f $script:lastUpdateBefore, $post.lastUpdate)
    Log ("[PASS] install: path={0} elapsed_ms={1} pure_mode_state={2}" -f $path, [int]$elapsed, $pureMode)
    Write-Host ("INSTALL: PASS (path={0}, {1} ms)" -f $path, [int]$elapsed) -ForegroundColor Green
}
catch {
    $ex = $_.Exception.Message
    if ($ex -match 'LCINSTALL_EXIT:(\d+)') {
        $exitCode = [int]$Matches[1]
    } else {
        Log ("FAIL [1] unexpected: {0}" -f $ex)
        $exitCode = 1
    }
}
finally {
    $script:inMainTry = $false
    if ($installJob) {
        if ($installJob.State -eq 'Running') { try { Stop-Job -Job $installJob } catch { } }
        try { Remove-Job -Job $installJob -Force } catch { }
    }
    Invoke-Adb @('shell', 'rm', '-f', $script:RemoteApk) | Out-Null
    $script:marketRestoreState = 'skipped'
    if ($script:weDisabledMarket -and $script:keepMarketUntil) {
        if ($script:exitingHandler) {
            try { Unregister-Event -SubscriptionId $script:exitingHandler.Id -ErrorAction SilentlyContinue } catch { }
            $script:exitingHandler = $null
        }
        $restoreCmd = "adb -s $($script:serial) shell pm enable $MarketPackage"
        $note = @(
            'LC install-debug: app market intentionally left disabled',
            ("market package : {0}" -f $MarketPackage),
            ("kept disabled  : until {0} (-KeepMarketDisabledUntil)" -f $script:keepMarketUntil),
            ("restore with   : {0}" -f $restoreCmd),
            'why            : Huawei AppGallery auto-updates installed apps and can roll back this debug build',
            'note           : restore before handing the device back / before daily use'
        )
        Set-Content -LiteralPath (Join-Path $OutDir 'market-kept-disabled.txt') -Value $note -Encoding UTF8
        Log ("WARN market left DISABLED until {0}; restore with: {1}" -f $script:keepMarketUntil, $restoreCmd)
    } else {
        if (-not (Restore-Market)) { if ($exitCode -eq 0) { $exitCode = 15 } }
    }
}

$restoreLine = 'MARKET_RESTORED=' + $script:marketRestoreState
Log $restoreLine
Write-Host $restoreLine
if ($script:marketRestoreState -eq 'fail' -and $exitCode -eq 0) { $exitCode = 15 }

if ($exitCode -ne 0) {
    Write-Host ("INSTALL: FAIL (exit {0}, logs: {1})" -f $exitCode, $OutDir) -ForegroundColor Red
}
exit $exitCode
