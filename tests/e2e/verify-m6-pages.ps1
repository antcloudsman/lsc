# M6 第 4 条：四页 1:1 复验（原版 m3 dump vs 实际 dump）
# 判据（F-01/F-02 修复后）——三条判据各自出 PASS/FAIL，不再只用 compare_page 的单一 VERDICT：
#   TEXT    文本缺失（白名单外）            BOUNDS  匹配节点 bounds 四边 Δ≤2px
#   STEP    匹配节点 y 步进中位数 = 期望±2   THEME   底色众数 / 纯白占比 / 字色占比
#   CARRIER contact 必须是 dialog 弹层；其余必须是 page（android:id/content 或全屏）
# 用法：pwsh -NoProfile -File tests\e2e\verify-m6-pages.ps1 [-Device L2E0222531014833] [-OutDir <dir>] `
#         [-Pages account,complaint,about,contact] [-ActDir <dir>]
#   -ActDir 提供 act-<page>.xml / act-<page>.png 时跳过设备（离线复核判据用）
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = '',
    [string[]]$Pages = @('account', 'complaint', 'about', 'contact'),
    [string]$ActDir = ''
)
$pkg = 'com.luopan.fengshui'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $root ('checks\m6-pages-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$script:fails = 0
function Log($s) { $l = (Get-Date -Format 'HH:mm:ss') + ' ' + $s; Write-Host $l; Add-Content -LiteralPath (Join-Path $OutDir 'run.log') -Value $l -Encoding UTF8 }
function Adb2([string[]]$p) { (& adb -s $Device @p 2>&1 | ForEach-Object { "$_" }) }
function UiDump {
    Adb2 @('shell', 'rm', '-f', '/sdcard/a.xml') | Out-Null
    Adb2 @('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/a.xml') | Out-Null
    Start-Sleep -Milliseconds 300
    return ((Adb2 @('shell', 'cat', '/sdcard/a.xml')) -join "`n")
}
function TapId([string]$id) {
    for ($i = 1; $i -le 3; $i++) {
        $xml = UiDump
        if ($xml -match ('resource-id="' + [regex]::Escape($pkg) + ':id/' + [regex]::Escape($id) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')) {
            $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            Adb2 @('shell', 'input', 'tap', "$x", "$y") | Out-Null
            return $true
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}
function MainPage {
    Adb2 @('shell', 'am', 'start', '-n', "$pkg/.MainActivity") | Out-Null
    Start-Sleep -Seconds 4
    Adb2 @('shell', 'am', 'broadcast', '-a', "$pkg.DEBUG", '--es', 'cmd', 'heading', '--ef', 'value', '0') | Out-Null
}
function GoBack {
    Adb2 @('shell', 'input', 'keyevent', '4') | Out-Null
    Start-Sleep -Milliseconds 700
}

$refFile = @{ account = 'account-setting'; complaint = 'complaint'; about = 'about'; contact = 'contact' }
$items = @{ account = 'lc_drawer_item_1'; complaint = 'lc_drawer_item_2'; about = 'lc_drawer_item_3'; contact = 'lc_drawer_item_4' }

# 每页判据参数（来源：client-four-pages-diff §0.3/§1.2、verify-four-pages-client §3）
# ignore  = 产品口径「M4 自建/非原版、不按 1:1 判」的文本（只影响缺失判定）
# region  = 步进取样的 act 侧 y 区间（行高节奏与文本无关）
$cfg = @{
    account   = @{ ignore = '昵称,沧海桑田,头像,绑定手机,去绑定,绑定微信,注销账户,账户ID,340992,退出登录'
                   step = 163; region = '300:2000'; carrier = 'page'; bg = 'F0F3F7'; text = '23272B'; white = $null }
    complaint = @{ ignore = ''; step = 180; region = '300:700'; carrier = 'page'; bg = 'F0F3F7'; text = '23272B'; white = $null }
    about     = @{ ignore = ''; step = 95;  region = '950:1200'; carrier = 'page'; bg = 'F0F3F7'; text = '23272B'; white = $null }
    contact   = @{ ignore = ''; step = $null; region = ''; carrier = 'dialog'; bg = '101215'; text = $null; white = 5 }
}

foreach ($pg in $Pages) {
    if (-not $ActDir) {
        MainPage
        if (-not (TapId 'lc_menu_button')) { Log "[$pg] 打不开抽屉"; $script:fails++; continue }
        Start-Sleep -Milliseconds 900
        if (-not (TapId $items[$pg])) { Log ("[$pg] 找不到条目 " + $items[$pg]); $script:fails++; GoBack; continue }
        Start-Sleep -Milliseconds 1500
        $xml = UiDump
        Set-Content -LiteralPath (Join-Path $OutDir ("act-$pg.xml")) -Value $xml -Encoding UTF8
        & adb -s $Device exec-out screencap -p > (Join-Path $OutDir ("act-$pg.png"))
        GoBack; GoBack
    }
    $actXml = if ($ActDir) { Join-Path $ActDir ("act-$pg.xml") } else { Join-Path $OutDir ("act-$pg.xml") }
    $actPng = if ($ActDir) { Join-Path $ActDir ("act-$pg.png") } else { Join-Path $OutDir ("act-$pg.png") }
    $ref = Join-Path $root ("docs\fidelity\m3\" + $refFile[$pg] + '.xml')

    $judgeArgs = @((Join-Path $root 'tests\lib\judge_page.py'), '--ref', $ref, '--act-xml', $actXml, '--act-png', $actPng,
        '--step-tol', '2', '--carrier', $cfg[$pg].carrier, '--theme-bg', $cfg[$pg].bg)
    if ($cfg[$pg].ignore) { $judgeArgs += @('--ignore-text', $cfg[$pg].ignore) }
    if ($null -ne $cfg[$pg].step) { $judgeArgs += @('--step', "$($cfg[$pg].step)") }
    if ($cfg[$pg].region) { $judgeArgs += @('--step-region', $cfg[$pg].region) }
    if ($cfg[$pg].text) { $judgeArgs += @('--theme-text', $cfg[$pg].text) }
    if ($null -ne $cfg[$pg].white) { $judgeArgs += @('--theme-white-min', "$($cfg[$pg].white)") }
    $out = & python @judgeArgs 2>&1
    $txt = ($out | ForEach-Object { "$_" }) -join "`n"
    Add-Content -LiteralPath (Join-Path $OutDir 'compare.log') -Value ("== $pg ==`n" + $txt) -Encoding UTF8

    $verdicts = @{}
    foreach ($k in 'TEXT', 'BOUNDS', 'STEP', 'THEME', 'CARRIER', 'VERDICT') {
        $mv = [regex]::Match($txt, "(?m)^$k=(PASS|FAIL|NA)")
        $verdicts[$k] = if ($mv.Success) { $mv.Groups[1].Value } else { 'ERROR' }
    }
    Log ("[$pg] " + (($verdicts.Keys | ForEach-Object { $_ + '=' + $verdicts[$_] }) -join ' '))
    if ($verdicts['VERDICT'] -ne 'PASS') { $script:fails++ }
}
Log ("fails=" + $script:fails + " out=" + $OutDir)
if ($script:fails -eq 0) { exit 0 } else { exit 1 }

