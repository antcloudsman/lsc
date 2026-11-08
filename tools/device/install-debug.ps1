#requires -Version 7
<#
.SYNOPSIS
  LC 一键装机：自动确认华为/安卓安装风险提示，全程无需人工点按。

.DESCRIPTION
  真机安装会被 PackageInstaller 的「风险提示」页与「安装确认」页拦住（实测两页，各点一次「继续安装」）。
  本脚本在后台启动 `adb install -r -t`，同时轮询前台窗口并点按字典内按钮；遇到认证对话框从
  仓库外 secrets 注入锁屏 PIN。安装期间临时禁用华为应用市场（避免被市场页面抢占/覆盖），
  结束后无条件恢复并校验。

  实测流程（设备 L2E0222531014833，2026-11-07）：
    InstallStaging「风险提示」→ 点「继续安装」(881,2524)
    PackageInstallerActivity → 点「继续安装」(614,2362)
    → Performing Streamed Install: Success → lastUpdateTime 变化

  退出码：0 INSTALL_OK / 2 INSTALL_FAILED / 3 INSTALL_AUTH_BLOCKED / 12 UI_NO_BUTTON / 15 MARKET_RESTORE_FAIL

.PARAMETER Apk
  APK 路径，默认 app\android\app\build\outputs\apk\debug\app-debug.apk。

.PARAMETER Device
  adb serial；默认取唯一在线设备。

.PARAMETER TimeoutSec
  总超时秒数，默认 180。

.PARAMETER PinFile
  锁屏 PIN 文件，默认 E:\APPDetector\secrets\device-pin.txt（不在仓库内，永不打印）。

.PARAMETER Owner
  设备租约持有者（默认 \Administrator@\ST-JACK-COMPUTE）。同一时间只允许一个持有者装包。

.PARAMETER LeaseMinutes
  租约有效期（分钟），默认 30。

.PARAMETER Force
  租约被他人持有且未过期时，强制接管（会打印 LEASE_TAKEOVER）。

.PARAMETER Release
  释放自己持有的租约；不是持有者时需配合 -Force。
.PARAMETER SkipMarketDisable
  跳过应用市场临时禁用（默认会禁用并在 finally 恢复）。

.EXAMPLE
  pwsh -File tools\device\install-debug.ps1
  pwsh -File tools\device\install-debug.ps1 -Apk path\to\app-debug.apk -TimeoutSec 150
#>
[CmdletBinding()]
param(
  [string]$Apk = 'app\android\app\build\outputs\apk\debug\app-debug.apk',
  [string]$Device = '',
  [int]$TimeoutSec = 180,
  [string]$PinFile = 'E:\APPDetector\secrets\device-pin.txt',
  [switch]$SkipMarketDisable,
  # 设备租约（M7 B 轮事故：一人装包把另一人的测量环境换掉，读数指向错误的包）
  [string]$Owner = '',
  [int]$LeaseMinutes = 30,
  [switch]$Force,
  [switch]$Release
)

$ErrorActionPreference = 'Stop'
$package = 'com.luopan.fengshui'
$market = 'com.huawei.appmarket'
$script:serial = ''
$script:marketDisabledByUs = $false

# ---- 设备租约（M7 B 轮事故）------------------------------------------------
# 事故：A 装完包在测，B 又在同一台机上装了自己的包，A 的读数就指向了错误的包。
# 靠口头约定 GUARD 不住，所以把“谁在用”变成脚本能拒绝的状态文件。
# 状态文件故意放在仓库外：多 worktree 并行时，租约必须是共享的一份。
$script:leasePath = if ($env:LC_DEVICE_LEASE) { $env:LC_DEVICE_LEASE } else { 'E:\APPDetector\device-lease.json' }

function Read-Lease {
  if (-not (Test-Path -LiteralPath $script:leasePath)) { return $null }
  try { return (Get-Content -LiteralPath $script:leasePath -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { return $null }
}

function Write-Lease([string]$owner) {
  $now = Get-Date
  $obj = [ordered]@{
    owner   = $owner
    since   = $now.ToString('s')
    expires = $now.AddMinutes($LeaseMinutes).ToString('s')
    device  = $script:serial
    note    = 'install-debug.ps1'
  }
  ($obj | ConvertTo-Json) | Set-Content -LiteralPath $script:leasePath -Encoding UTF8
}

function Test-Lease([string]$me) {
  $l = Read-Lease
  if (-not $l) { return @{ ok = $true; reason = 'no-lease' } }
  $exp = [datetime]::MinValue
  [void][datetime]::TryParse([string]$l.expires, [ref]$exp)
  if ($exp -eq [datetime]::MinValue -or $exp -lt (Get-Date)) { return @{ ok = $true; reason = 'expired' } }
  if ([string]$l.owner -eq $me) { return @{ ok = $true; reason = 'mine' } }
  return @{ ok = $false; owner = [string]$l.owner; expires = $exp.ToString('HH:mm') }
}

$me = if ($Owner) { $Owner } else { "$env:USERNAME@$env:COMPUTERNAME" }

if ($Release) {
  $l = Read-Lease
  if (-not $l) { Write-Host 'LEASE: none'; exit 0 }
  if ([string]$l.owner -ne $me -and -not $Force) {
    Write-Host ("LEASE_BUSY: 设备由 {0} 持有（{1} 到期）；要强制释放请加 -Force" -f $l.owner, $l.expires)
    exit 3
  }
  Remove-Item -LiteralPath $script:leasePath -Force
  Write-Host ("LEASE_RELEASED: {0}" -f $l.owner)
  exit 0
}

$leaseChk = Test-Lease $me
if (-not $leaseChk.ok) {
  if ($Force) {
    Write-Host ("LEASE_TAKEOVER: 已强制接管（原持有者 {0}，{1} 到期）" -f $leaseChk.owner, $leaseChk.expires)
  } else {
    Write-Host ("LEASE_BUSY: 设备由 {0} 持有至 {1}；请先在群里交接，或加 -Force 强制接管" -f $leaseChk.owner, $leaseChk.expires)
    exit 3
  }
} else {
  Write-Host ("LEASE_OK: {0}（{1}）" -f $me, $leaseChk.reason)
}
Write-Lease $me

function Resolve-Serial {
  param([string]$want)
  if ($want) { return $want }
  $line = (& adb devices) | Where-Object { $_ -match '^\S+\s+device$' } | Select-Object -First 1
  if (-not $line) { throw 'NO_DEVICE: adb devices 看不到 device 状态的设备' }
  return ($line -split '\s+')[0]
}

function Invoke-Adb {
  param([string]$shellCmd)
  if ($script:serial) { & adb -s $script:serial shell $shellCmd 2>&1 } else { & adb shell $shellCmd 2>&1 }
}

function Get-Focus {
  $out = Invoke-Adb 'dumpsys window' | Select-String -Pattern 'mCurrentFocus=' | Select-Object -First 1
  if (-not $out) { return '' }
  $s = $out.Line.Trim()
  if ($s -match 'u0\s+([^\s}]+)') { return $matches[1] }
  return $s
}

function Get-UiNodes {
  Invoke-Adb 'rm -f /sdcard/a.xml' | Out-Null
  $dump = Invoke-Adb 'uiautomator dump /sdcard/a.xml'
  if (-not ($dump -match 'dumped to')) { return @() }
  $tmp = Join-Path $env:TEMP ('lc-ui-' + [guid]::NewGuid().ToString('N') + '.xml')
  & adb -s $script:serial pull /sdcard/a.xml $tmp 2>&1 | Out-Null
  if (-not (Test-Path $tmp)) { return @() }
  try { $doc = [xml](Get-Content $tmp -Raw) } catch { return @() } finally { Remove-Item $tmp -Force -ErrorAction SilentlyContinue }
  $list = @()
  foreach ($n in $doc.SelectNodes('//node')) {
    $t = $n.GetAttribute('text'); $b = $n.GetAttribute('bounds')
    if ([string]::IsNullOrWhiteSpace($t) -or [string]::IsNullOrWhiteSpace($b)) { continue }
    $m = [regex]::Match($b, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $m.Success) { continue }
    $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
    $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
    $list += [pscustomobject]@{ Text = $t; X = [int](($x1 + $x2) / 2); Y = [int](($y1 + $y2) / 2) }
  }
  return $list
}

function Get-LastUpdate {
  $r = Invoke-Adb "dumpsys package $package" | Select-String -Pattern 'lastUpdateTime=' | Select-Object -First 1
  if ($r) { return $r.Line.Trim() } else { return 'lastUpdateTime=(未安装)' }
}

$buttons = @('继续安装', '仍要安装', '确认安装', '安装', '允许', '同意', '我知道了', '继续', '跳过', '以后再说')
$authHint = '锁屏密码|请输入密码|verify your password'

if (-not (Test-Path -LiteralPath $Apk)) { Write-Host "APK_NOT_FOUND: $Apk"; exit 2 }
$apkFull = (Resolve-Path -LiteralPath $Apk).Path
$script:serial = Resolve-Serial $Device
$before = Get-LastUpdate
$marketWasDisabled = ((Invoke-Adb 'pm list packages -d') -join "`n") -match [regex]::Escape($market)
Write-Host "device=$script:serial apk=$apkFull"
# 装的人与测的人必须看到同一组指纹：只贴 lastUpdateTime 不足以排除"装的是旧构建"。
# 事故实例（M7 A 轮）：调用方拼错参数名，脚本静默回退默认路径，装上了 build/outputs 里
# 残留的回滚包（60DAC7F8），而调用方以为装的是刚构建的包（6F7A31E5）。
$apkSha = (Get-FileHash -LiteralPath $apkFull -Algorithm SHA256).Hash
$apkSize = (Get-Item -LiteralPath $apkFull).Length
Write-Host "apk_sha256=$apkSha"
Write-Host "apk_size=$apkSize"
Write-Host "apk_mtime=$((Get-Item -LiteralPath $apkFull).LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Host "before: $before"
Write-Host "market_was_disabled=$marketWasDisabled"

try {
  if (-not $SkipMarketDisable -and -not $marketWasDisabled) {
    Invoke-Adb "pm disable-user --user 0 $market" | Out-Null
    $script:marketDisabledByUs = $true
    Write-Host "market: disabled for install window"
  }

  $job = Start-Job -ScriptBlock { param($a, $s) & adb -s $s install -r -t $a 2>&1 } -ArgumentList $apkFull, $script:serial
  Write-Host "install job id=$($job.Id)"

  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  $taps = 0; $lastSig = ''; $authBlocked = $false; $noButton = $false
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 900
    $focus = Get-Focus
    if ($focus -notmatch 'packageinstaller|appmarket|UnifiedAuth|InstallSuccess|install') {
      if ($job.State -ne 'Running') { break }
      continue
    }
    if ($focus -match 'InstallSuccess') { break }
    $nodes = Get-UiNodes
    if ($nodes.Count -eq 0) { continue }
    $texts = ($nodes | ForEach-Object { $_.Text }) -join '|'
    $sig = "$focus#$texts"
    $pick = $null
    foreach ($label in $buttons) {
      $pick = $nodes | Where-Object { $_.Text -eq $label } | Select-Object -First 1
      if ($pick) { break }
    }
    if (-not $pick -and ($focus -match 'UnifiedAuth' -or $texts -match $authHint)) {
      if (Test-Path -LiteralPath $PinFile) {
        $pin = (Get-Content -LiteralPath $PinFile -Raw).Trim()
        Invoke-Adb "input text $pin" | Out-Null
        Invoke-Adb 'input keyevent 66' | Out-Null
        Write-Host 'auth: PIN injected'
        continue
      } else { $authBlocked = $true; break }
    }
    if (-not $pick) { $noButton = $true; continue }
    if ($sig -eq $lastSig) { continue }
    $lastSig = $sig
    Write-Host ("tap '{0}' at {1},{2} focus={3}" -f $pick.Text, $pick.X, $pick.Y, $focus)
    Invoke-Adb ("input tap {0} {1}" -f $pick.X, $pick.Y) | Out-Null
    $taps++
    if ($taps -ge 20) { break }
  }

  if ($job.State -eq 'Running') { Stop-Job $job -ErrorAction SilentlyContinue }
  $jobOut = Receive-Job $job -Keep 2>&1
  $jobOut | ForEach-Object { Write-Host ("job: " + $_) }
  Remove-Job $job -Force -ErrorAction SilentlyContinue
}
finally {
  if ($script:marketDisabledByUs) {
    Invoke-Adb "pm enable $market" | Out-Null
    Start-Sleep -Milliseconds 500
    $still = ((Invoke-Adb 'pm list packages -d') -join "`n") -match [regex]::Escape($market)
    if ($still) { Write-Host 'MARKET_RESTORED=fail'; $marketFail = $true }
    else { Write-Host 'MARKET_RESTORED=ok'; $marketFail = $false }
  } else {
    Write-Host 'MARKET_RESTORED=skipped'
    $marketFail = $false
  }
}

if ((Get-Focus) -match 'InstallSuccess') { Invoke-Adb 'input keyevent 4' | Out-Null }
$after = Get-LastUpdate
Write-Host "after: $after"

if ($authBlocked) { Write-Host 'INSTALL_AUTH_BLOCKED'; exit 3 }
if ($marketFail) { Write-Host 'MARKET_RESTORE_FAIL'; exit 15 }

$installed = ($jobOut -match 'Success') -or ($before -ne $after)
if ($installed) {
  $path = (Invoke-Adb "pm path $package" | Select-Object -First 1)
  Write-Host "pm: $path"
  Write-Host ("INSTALL_OK taps={0}" -f $taps)
  exit 0
}
if ($noButton -and $taps -eq 0) { Write-Host 'UI_NO_BUTTON'; exit 12 }
Write-Host("INSTALL_FAILED taps={0}" -f $taps)
exit 2
