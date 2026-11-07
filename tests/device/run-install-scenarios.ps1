<#
  装机五场景驱动（面向 develop 上 tools\device\install-debug.ps1 的 166 行实测版）。
  用法：pwsh -File tests\device\run-install-scenarios.ps1 -Apk <apk> [-Scenarios s1,s4,s5]
  每场景：清 logcat → 焦点轮询 → 跑脚本 → 记 exit/wall/focus 命中 → 脱敏 logcat → 复位核对。
  不改被测脚本。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutRoot = '',
    [string[]]$Scenarios = @('s1', 's4', 's5')
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutRoot) { $OutRoot = Join-Path $repo ('checks\install-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
$script = Join-Path $repo 'tools\device\install-debug.ps1'
$md = Join-Path $OutRoot 'SUMMARY.md'
Set-Content -LiteralPath $md -Value @('# 装机五场景（真机）', '', "apk=$Apk", "sha256=$((Get-FileHash $Apk -Algorithm SHA256).Hash.ToLower())", "bytes=$((Get-Item $Apk).Length)", "device=$Device", '') -Encoding UTF8

function Adb2([string[]]$p) { return @(& adb -s $Device @p 2>&1 | ForEach-Object { "$_" }) }

function Run-One([string]$name, [string]$title, [string[]]$extra) {
    $dir = Join-Path $OutRoot $name
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $focusLog = Join-Path $dir 'focus-poll.txt'
    $stop = Join-Path $dir '.stop'
    if (Test-Path $stop) { Remove-Item $stop -Force }
    $before = (Adb2 @('shell', 'dumpsys', 'package', 'com.luopan.fengshui')) -join "`n"
    $beforeTime = ([regex]::Match($before, 'lastUpdateTime=([^\s]+)')).Groups[1].Value
    $marketBefore = (Adb2 @('shell', 'pm', 'list', 'packages', '-d')) -join ';'

    $poller = Start-Job -ScriptBlock {
        param($Dev, $Out, $Stop)
        while (-not (Test-Path $Stop)) {
            $raw = (& adb -s $Dev shell dumpsys window 2>$null) -join "`n"
            $m = [regex]::Match($raw, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')
            $f = if ($m.Success) { $m.Groups[1].Value } else { 'none' }
            Add-Content -LiteralPath $Out -Value ((Get-Date).ToString('HH:mm:ss.fff') + "`t" + $f) -Encoding UTF8
            Start-Sleep -Milliseconds 300
        }
    } -ArgumentList $Device, $focusLog, $stop

    Adb2 @('logcat', '-c') | Out-Null
    $args = @('-NoProfile', '-File', $script, '-Apk', $Apk, '-Device', $Device) + $extra
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $out = & pwsh @args 2>&1
    $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    $sw.Stop()
    $out | ForEach-Object { "$_" } | Set-Content -LiteralPath (Join-Path $dir 'stdout.txt') -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dir 'exit.txt') -Value $code -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dir 'timing.txt') -Value ("wall_seconds=" + [math]::Round($sw.Elapsed.TotalSeconds, 1)) -Encoding UTF8

    New-Item -ItemType File -Path $stop -Force | Out-Null
    Wait-Job $poller -Timeout 5 | Out-Null
    Remove-Job $poller -Force -ErrorAction SilentlyContinue
    Remove-Item $stop -Force -ErrorAction SilentlyContinue

    $rawLog = Join-Path $dir 'logcat.raw.txt'
    Adb2 @('logcat', '-d') | Out-File -LiteralPath $rawLog -Encoding UTF8
    . (Join-Path $repo 'tests\lib\LogRedact.ps1')
    Invoke-RedactFile -Path $rawLog
    Move-Item -LiteralPath $rawLog -Destination (Join-Path $dir 'logcat.txt') -Force

    $after = (Adb2 @('shell', 'dumpsys', 'package', 'com.luopan.fengshui')) -join "`n"
    $afterTime = ([regex]::Match($after, 'lastUpdateTime=([^\s]+)')).Groups[1].Value
    $marketAfter = (Adb2 @('shell', 'pm', 'list', 'packages', '-d')) -join ';'
    $focus = @(Get-Content -LiteralPath $focusLog -Encoding UTF8 -ErrorAction SilentlyContinue)
    $uiHits = @($focus | Where-Object { $_ -match 'packageinstaller|InstallStaging|UnifiedAuthentication' })
    $uiFocused = @($uiHits | Where-Object { $_ -match 'packageinstaller|InstallStaging' }).Count
    $authSeen = @($uiHits | Where-Object { $_ -match 'UnifiedAuthentication' }).Count
    $taps = ([regex]::Matches(($out -join "`n"), '(?m)^tap ')).Count
    $tail = ($out | Select-Object -Last 3) -join ' | '
    Set-Content -LiteralPath (Join-Path $dir 'after.txt') -Value @("lastUpdateTime_before=$beforeTime", "lastUpdateTime_after=$afterTime", "market_disabled_before=$marketBefore", "market_disabled_after=$marketAfter") -Encoding UTF8

    return [pscustomobject]@{
        Name = $name; Title = $title; Exit = $code; Wall = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        Before = $beforeTime; After = $afterTime; Taps = $taps
        UiFocus = $uiFocused; AuthSeen = $authSeen
        MarketBefore = $marketBefore; MarketAfter = $marketAfter
        Tail = $tail; Out = ($out -join "`n")
    }
}

$results = @()

if ($Scenarios -contains 's1') {
    $r = Run-One 's1' 'pm 主路径（纯净模式下会转两页点按）' @()
    $ok = ($r.Exit -eq 0) -and ($r.Before -ne $r.After)
    Add-Content -LiteralPath $md -Value ("| S1 | pm 主路径 | {0} | exit={1} wall={2}s taps={3} ui_focus_samples={4} lastUpdate: {5} -> {6} |" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $r.Exit, $r.Wall, $r.Taps, $r.UiFocus, $r.Before, $r.After) -Encoding UTF8
    $results += $r
}

if ($Scenarios -contains 's3') {
    $r = Run-One 's3' '认证对话框（有则注 PIN，无则记 BLOCKED）' @()
    if ($r.AuthSeen -gt 0) { $verdict = if ($r.Exit -eq 0) { 'PASS' } else { 'BLOCKED' } }
    else { $verdict = 'BLOCKED(未出现 UnifiedAuthenticationDialogActivity)' }
    $pinInLog = [bool](Select-String -LiteralPath (Join-Path $OutRoot 's3\logcat.txt') -Pattern 'device-pin|PIN=' -ErrorAction SilentlyContinue)
    Add-Content -LiteralPath $md -Value ("| S3 | 认证对话框注 PIN | {0} | exit={1} auth_seen={2} PIN_IN_LOG={3} |" -f $verdict, $r.Exit, $r.AuthSeen, $pinInLog) -Encoding UTF8
    $results += $r
}

if ($Scenarios -contains 's4') {
    $r = Run-One 's4' '市场临时禁用 + finally 恢复' @()
    $restored = ($r.MarketAfter -notmatch 'com\.huawei\.appmarket')
    $ok = ($r.Exit -eq 0) -and $restored
    Add-Content -LiteralPath $md -Value ("| S4 | 市场禁用 + finally 恢复 | {0} | exit={1} restored={2} market_after='{3}' |" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $r.Exit, $restored, ($r.MarketAfter -replace '\s+', ' ')) -Encoding UTF8
    $results += $r
}

if ($Scenarios -contains 's5') {
    $fake = Join-Path $env:TEMP 'lc-fake.apk'
    Set-Content -LiteralPath $fake -Value 'not an apk' -NoNewline -Encoding ASCII
    $focusBefore = ((Adb2 @('shell', 'dumpsys', 'window')) -join "`n")
    $fb = ([regex]::Match($focusBefore, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')).Groups[1].Value
    $dir = Join-Path $OutRoot 's5'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $out = & pwsh -NoProfile -File $script -Apk $fake -Device $Device 2>&1
    $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    $sw.Stop()
    $out | ForEach-Object { "$_" } | Set-Content -LiteralPath (Join-Path $dir 'stdout.txt') -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dir 'exit.txt') -Value $code -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $dir 'timing.txt') -Value ("wall_seconds=" + [math]::Round($sw.Elapsed.TotalSeconds, 1)) -Encoding UTF8
    $focusAfterRaw = ((Adb2 @('shell', 'dumpsys', 'window')) -join "`n")
    $fa = ([regex]::Match($focusAfterRaw, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')).Groups[1].Value
    Set-Content -LiteralPath (Join-Path $dir 'focus-before-after.txt') -Value @("before=$fb", "after=$fa") -Encoding UTF8
    $misclick = $fa -match 'packageinstaller'
    $ok = ($code -ne 0) -and (-not $misclick)
    Add-Content -LiteralPath $md -Value ("| S5 | 非 APK 文件 | {0} | exit={1} wall={2}s focus: {3} -> {4} |" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $code, [math]::Round($sw.Elapsed.TotalSeconds, 1), $fb, $fa) -Encoding UTF8
}

Add-Content -LiteralPath $md -Value @('', '## 说明', '', '- `develop` 上 `tools\device\install-debug.ps1` 是 166 行实测版，**没有 `-InstallMode`**，因此 S1 与 S2 在本机是同一条路径（纯净模式开启时 `pm install` 也会落到 InstallStaging，需两次「继续安装」点按）。S2 不单独构造，判据并入 S1 的 `taps` 与 `ui_focus_samples`。', '- S3 只有在真机上出现 `UnifiedAuthenticationDialogActivity` 才可判；未出现记 BLOCKED（环境不具备），不当 FAIL。', '- 证据：每场景 `stdout.txt`/`exit.txt`/`timing.txt`/`focus-poll.txt`/`logcat.txt`（走 LogRedact）。') -Encoding UTF8

Write-Host ''
Get-Content -LiteralPath $md -Encoding UTF8 | Write-Host
Write-Host "out=$OutRoot"
