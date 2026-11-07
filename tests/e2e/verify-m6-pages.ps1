# M6 第 4 条：四页 1:1 复验（原版 m3 dump vs 实际 dump）
# 用法：pwsh -NoProfile -File tests\e2e\verify-m6-pages.ps1 [-Device L2E0222531014833] [-OutDir <dir>] [-Pages account,complaint,about,contact]
# 判据：匹配到的文本节点 bounds 四边 Δ≤2px；缺失文本 = 失败；步进（--step）作为参考值输出
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = '',
    [string[]]$Pages = @('account', 'complaint', 'about', 'contact')
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
$refs = @{
    account   = 'docs/fidelity/m3/account-setting.xml'
    complaint = 'docs/fidelity/m3/complaint.xml'
    about     = 'docs/fidelity/m3/about.xml'
    contact   = 'docs/fidelity/m3/contact.xml'
}
$items = @{ account = 'lc_drawer_item_1'; complaint = 'lc_drawer_item_2'; about = 'lc_drawer_item_3'; contact = 'lc_drawer_item_4' }
$titles = @{ account = '账户设置'; complaint = '投诉反馈'; about = '关于我们'; contact = '联系我们' }

foreach ($pg in $Pages) {
    MainPage
    if (-not (TapId 'lc_menu_button')) { Log "[$pg] 打不开抽屉"; $script:fails++; continue }
    Start-Sleep -Milliseconds 900
    if (-not (TapId $items[$pg])) { Log ("[$pg] 找不到条目 " + $items[$pg]); $script:fails++; GoBack; continue }
    Start-Sleep -Milliseconds 1500
    $xml = UiDump
    Set-Content -LiteralPath (Join-Path $OutDir ("act-$pg.xml")) -Value $xml -Encoding UTF8
    & adb -s $Device exec-out screencap -p > (Join-Path $OutDir ("act-$pg.png"))
    @('account-setting', 'complaint', 'about', 'contact') | Out-Null
    # 回主界面（Activity/Dialog 都按两次返回兜底）
    GoBack; GoBack
    $ref = Join-Path $root ("docs\fidelity\m3\" + @{account='account-setting';complaint='complaint';about='about';contact='contact'}[$pg] + '.xml')
    $out = & python (Join-Path $root 'tests\lib\compare_page.py') $ref (Join-Path $OutDir ("act-$pg.xml")) --step 2>&1
    $txt = ($out | ForEach-Object { "$_" }) -join "`n"
    Add-Content -LiteralPath (Join-Path $OutDir 'compare.log') -Value ("== $pg ==`n" + $txt) -Encoding UTF8
    if ($pg -eq 'contact') {
        $blue = (& python (Join-Path $root 'tests\lib\blue_button.py') (Join-Path $OutDir 'act-contact.png') 168 1127 1059 1694 2>&1 | ForEach-Object { "$_" }) -join "`n"
        Add-Content -LiteralPath (Join-Path $OutDir 'compare.log') -Value ("contact蓝填充: " + $blue) -Encoding UTF8
        Log ("[contact] " + $blue)
    }
    $verdict = if ($txt -match 'VERDICT=PASS') { 'PASS' } else { 'FAIL' }
    if ($verdict -eq 'FAIL') { $script:fails++ }
    $maxd = [regex]::Match($txt, 'max_bounds_delta=(\d+)').Groups[1].Value
    $miss = [regex]::Match($txt, 'missing=(\d+)').Groups[1].Value
    $step = [regex]::Match($txt, 'step_y_median=([\d\.]+)').Groups[1].Value
    Log ("[$pg] " + $verdict + " max_delta=" + $maxd + "px missing=" + $miss + " step_y=" + $step)
}
Log ("fails=" + $script:fails + " out=" + $OutDir)
if ($script:fails -eq 0) { exit 0 } else { exit 1 }
