# M6 装机记录：统一走 tools\device\install-debug.ps1
# 记录：APK sha/大小、lastUpdateTime 前后、taps、MARKET_RESTORED、市场残留(pm list packages -d)、包是否在列、耗时
# 用法：pwsh -NoProfile -File tests\e2e\m6-install-record.ps1 -Apk <apk> [-Device L2E0222531014833] [-OutDir <dir>]
# 退出码：0 = 记录全部通过；1 = 有任一项不满足（结果仍写盘）；15 = 市场恢复失败（停机上报）
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = ''
)
$pkg = 'com.luopan.fengshui'
$market = 'com.huawei.appmarket'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $root ('checks\m6-install-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function AdbOut([string]$cmd) { (& adb -s $Device shell $cmd 2>&1 | ForEach-Object { "$_" }) -join "`n" }
function LastUpdate {
    $r = AdbOut "dumpsys package $pkg"
    if ($r -match 'lastUpdateTime=([^\r\n]+)') { return $Matches[1].Trim() }
    return '(未安装)'
}
function MarketDisabled { ((AdbOut 'pm list packages -d') -match [regex]::Escape($market)) -eq $true }

$sha = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $Apk).Length
$before = LastUpdate
$t0 = Get-Date

$raw = & pwsh -NoProfile -File (Join-Path $root 'tools\device\install-debug.ps1') -Apk $Apk -Device $Device 2>&1
$code = $LASTEXITCODE
$t1 = Get-Date

$after = LastUpdate
$marketDisabledAfter = MarketDisabled
$pkgListed = ((AdbOut "pm list packages $pkg") -match [regex]::Escape($pkg))
$log = ($raw | ForEach-Object { "$_" }) -join "`n"
$taps = if ($log -match 'taps=(\d+)') { [int]$Matches[1] } else { -1 }
$marketRestored = if ($log -match 'MARKET_RESTORED=(\w+)') { $Matches[1] } else { 'missing' }
$dur = [int](($t1 - $t0).TotalSeconds)

$checks = [ordered]@{
    'exit_0'                 = ($code -eq 0)
    'lastUpdate_changed'     = ($before -ne $after)
    'taps_ge_1'              = ($taps -ge 1)
    'MARKET_RESTORED_ok'     = ($marketRestored -eq 'ok')
    'market_not_disabled_after' = (-not $marketDisabledAfter)
    'package_listed'         = [bool]$pkgListed
}
$allOk = -not ($checks.Values -contains $false)

$lines = @()
$lines += "M6_INSTALL_RECORD"
$lines += "time=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') device=$Device"
$lines += "apk=$Apk"
$lines += "apk_sha256=$sha"
$lines += "apk_size=$size"
$lines += "lastUpdate_before=$before"
$lines += "lastUpdate_after=$after"
$lines += "install_exit=$code taps=$taps duration_s=$dur"
$lines += "market_restored=$marketRestored market_disabled_after=$marketDisabledAfter"
$lines += "package_listed=$pkgListed"
foreach ($k in $checks.Keys) { $lines += ("check {0}={1}" -f $k, $checks[$k]) }
$lines += "RESULT=" + $(if ($allOk) { 'PASS' } else { 'FAIL' })
$lines += "---- install-debug output ----"
$lines += $log
Set-Content -LiteralPath (Join-Path $OutDir 'RECORD.txt') -Value ($lines -join "`r`n") -Encoding UTF8

$json = [ordered]@{
    apk = $Apk; sha256 = $sha; size = $size; device = $Device
    before = $before; after = $after; exit = $code; taps = $taps
    market_restored = $marketRestored; market_disabled_after = $marketDisabledAfter
    package_listed = $pkgListed; checks = $checks; result = $(if ($allOk) { 'PASS' } else { 'FAIL' })
} | ConvertTo-Json -Depth 4
Set-Content -LiteralPath (Join-Path $OutDir 'record.json') -Value $json -Encoding UTF8

Write-Host ("M6_INSTALL_RECORD {0} exit={1} taps={2} market={3} sha={4}" -f $(if ($allOk) { 'PASS' } else { 'FAIL' }), $code, $taps, $marketRestored, $sha.Substring(0, 12))
Write-Host ("out=" + $OutDir)
if ($code -eq 15 -or $marketRestored -eq 'fail') { exit 15 }
if ($allOk) { exit 0 } else { exit 1 }
