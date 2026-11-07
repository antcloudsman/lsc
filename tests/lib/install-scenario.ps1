<#
  场景驱动：跑一次 install-debug.ps1，抓全证据（stdout/exit/timing/focus 轮询/脱敏 logcat）。
  用法：pwsh -File checks\run-scenario.ps1 -Name s1 -ScriptArgs @('-Apk','<apk>','-Device','L2E0222531014833')
  只做取证，不改被测脚本。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Name,
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutRoot = 'checks\install-debug-137845a',
    [string[]]$Extra = @()
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $repo (Join-Path $OutRoot $Name)
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$script = Join-Path $repo 'tools\device\install-debug.ps1'
$stdout = Join-Path $dir 'stdout.txt'
$focusPoll = Join-Path $dir 'focus-poll.txt'
$stopFlag = Join-Path $dir '.stop'

if (Test-Path $stopFlag) { Remove-Item $stopFlag -Force }
if (Test-Path $focusPoll) { Remove-Item $focusPoll -Force }

# 焦点轮询：300ms 采样，判「运行期间是否出现安装器/认证界面」
$poller = Start-Job -ScriptBlock {
    param($Dev, $Out, $Stop)
    while (-not (Test-Path $Stop)) {
        $raw = (& adb -s $Dev shell dumpsys window 2>$null) -join "`n"
        $m = [regex]::Match($raw, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')
        $focus = if ($m.Success) { $m.Groups[1].Value } else { 'none' }
        Add-Content -LiteralPath $Out -Value ((Get-Date).ToString('HH:mm:ss.fff') + "`t" + $focus) -Encoding UTF8
        Start-Sleep -Milliseconds 300
    }
} -ArgumentList $Device, $focusPoll, $stopFlag

# 清 logcat，跑脚本
& adb -s $Device logcat -c 2>$null
$args_ = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $script, '-Apk', $Apk, '-Device', $Device) + $Extra
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$output = & pwsh @args_ 2>&1
$code = $LASTEXITCODE
if ($null -eq $code) { $code = 0 }
$sw.Stop()
$output | ForEach-Object { "$_" } | Set-Content -LiteralPath $stdout -Encoding UTF8
Set-Content -LiteralPath (Join-Path $dir 'exit.txt') -Value $code -Encoding UTF8
Set-Content -LiteralPath (Join-Path $dir 'timing.txt') -Value ("wall_seconds=" + [math]::Round($sw.Elapsed.TotalSeconds, 1)) -Encoding UTF8

New-Item -ItemType File -Path $stopFlag -Force | Out-Null
Wait-Job $poller -Timeout 5 | Out-Null
Remove-Job $poller -Force -ErrorAction SilentlyContinue
Remove-Item $stopFlag -Force -ErrorAction SilentlyContinue

# logcat 落盘走 LogRedact（不手工拼 tk=）
$rawLog = Join-Path $dir 'logcat.raw.txt'
& adb -s $Device logcat -d 2>&1 | Out-File -LiteralPath $rawLog -Encoding UTF8
. (Join-Path $repo 'tests\lib\LogRedact.ps1')
Invoke-RedactFile -Path $rawLog
Move-Item -LiteralPath $rawLog -Destination (Join-Path $dir 'logcat.txt') -Force

# 装机指纹与焦点摘要
$after = (& adb -s $Device shell dumpsys package com.luopan.fengshui 2>$null) -join "`n"
$after | Select-String 'lastUpdateTime|versionName' | ForEach-Object { $_.Line.Trim() } |
    Set-Content -LiteralPath (Join-Path $dir 'after.txt') -Encoding UTF8
$uiHits = (Get-Content -LiteralPath $focusPoll -Encoding UTF8 -ErrorAction SilentlyContinue |
    Where-Object { $_ -match 'packageinstaller|InstallStaging|UnifiedAuthentication' }).Count
Set-Content -LiteralPath (Join-Path $dir 'focus-summary.txt') -Value ("samples=" + ((Get-Content -LiteralPath $focusPoll -Encoding UTF8 -ErrorAction SilentlyContinue).Count) + "`nui_hits=$uiHits") -Encoding UTF8

Write-Host ("[{0}] exit={1} wall={2}s ui_focus_samples={3} dir={4}" -f $Name, $code, [math]::Round($sw.Elapsed.TotalSeconds, 1), $uiHits, $dir)
exit $code
