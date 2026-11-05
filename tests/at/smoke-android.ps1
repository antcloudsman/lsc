<#
  K-01 单页 UI 的 Android 真机冒烟（可重复执行）。

  用法：
    pwsh -File tests\at\smoke-android.ps1 -Apk app\android\app\build\outputs\apk\debug\app-debug.apk

  覆盖：启动 / 罗盘转动（heading 注入 0/90/180/270）/ 罗盘浮层 / 5 盘面切换 /
        地图（3D、旋转、坐南朝北、底图 实景·地图、图层、定位、截屏、立向探针）/
        logcat FATAL·ANR。

  判据：PASS / FAIL / BLOCKED（入口不存在或 dump 失败 = BLOCKED，不冒充 PASS）。
  退出码：0 = 全 PASS（允许 DIAG）；1 = 有 FAIL；2 = 无 FAIL 但有 BLOCKED / 环境失败。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir
)

$ErrorActionPreference = 'Stop'
$Pkg = 'com.luopan.fengshui'
$Action = "$Pkg.DEBUG"
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if (-not $OutDir) { $OutDir = Join-Path $repo ('checks\smoke-android-' + (Get-Date -Format 'yyyyMMdd-HHmm')) }
$OutDir = [IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force $OutDir | Out-Null
$Apk = (Resolve-Path $Apk).Path

$script:Results = New-Object System.Collections.Generic.List[object]

function Add-Result([string]$id, [string]$status, [string]$expected, [string]$actual, [string]$evidence = '') {
    $script:Results.Add([pscustomobject]@{ id = $id; status = $status; expected = $expected; actual = $actual; evidence = $evidence })
    Write-Host ("[{0}] {1}  {2}" -f $status, $id, $actual)
}

function Invoke-Adb([string[]]$ArgList) {
    return ((& adb -s $Device @ArgList 2>&1) -join "`n")
}

function Shot([string]$name) {
    $remote = "/sdcard/lc_smoke_$name"
    Invoke-Adb @('shell', 'screencap', '-p', $remote) | Out-Null
    Invoke-Adb @('pull', $remote, (Join-Path $OutDir $name)) | Out-Null
    return (Join-Path $OutDir $name)
}

function Get-Ui([string]$tag, [int]$tries = 5) {
    for ($i = 0; $i -lt $tries; $i++) {
        Invoke-Adb @('shell', 'rm', '-f', '/sdcard/lc_smoke.xml') | Out-Null
        $o = Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/lc_smoke.xml')
        if ($o -match 'dumped to') {
            Invoke-Adb @('pull', '/sdcard/lc_smoke.xml', (Join-Path $OutDir "$tag.xml")) | Out-Null
            $p = Join-Path $OutDir "$tag.xml"
            if (Test-Path $p) { return [pscustomobject]@{ ok = $true; xml = (Get-Content $p -Raw -Encoding UTF8); path = $p } }
        }
        Start-Sleep -Milliseconds 900
    }
    return [pscustomobject]@{ ok = $false; xml = ''; path = '' }
}

function Get-Texts([string]$xml) {
    return [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object {
        $t = $_.Groups[1].Value
        $t = $t -replace '&#10;', "`n" -replace '&#9;', "`t" -replace '&quot;', '"' -replace '&lt;', '<' -replace '&gt;', '>' -replace '&amp;', '&'
        $t
    } | Where-Object { $_ -ne '' }
}

function Tap-Text([string]$xml, [string]$text, [switch]$Last) {
    $hits = @()
    foreach ($m in [regex]::Matches($xml, '<node\b[^>]*>')) {
        $n = $m.Value
        $t = [regex]::Match($n, 'text="([^"]*)"').Groups[1].Value
        if ($t -ne $text) { continue }
        $b = [regex]::Match($n, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $b.Success) { continue }
        $hits += [pscustomobject]@{
            x = [int](([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2)
            y = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2)
        }
    }
    if ($hits.Count -eq 0) { return $false }
    $p = if ($Last) { $hits[$hits.Count - 1] } else { $hits[0] }
    Invoke-Adb @('shell', 'input', 'tap', "$($p.x)", "$($p.y)") | Out-Null
    return $true
}

function Set-Heading([float]$h) {
    Invoke-Adb @('shell', 'am', 'broadcast', '-a', $Action, '--es', 'cmd', 'heading', '--ef', 'value', "$h") | Out-Null
    Start-Sleep -Milliseconds 600
}

function Get-Reading([string]$xml) {
    $texts = Get-Texts $xml
    $line = $texts | Where-Object { $_ -match '度数' } | Select-Object -First 1
    if (-not $line) { return $null }
    $deg = [regex]::Match($line, '度数\s*([\d.]+)°').Groups[1].Value
    $earth = [regex]::Match($line, '地盘\s*(\S+?)山').Groups[1].Value
    return [pscustomobject]@{ deg = $deg; earth = $earth; text = $line }
}

function Invoke-Js([string]$code) {
    # 整条命令包一层单引号：设备 sh 不把 ? : ( ) 当元字符
    $line = "am broadcast -a $Action --es cmd js --es value '$code'"
    Invoke-Adb @('shell', $line) | Out-Null
    Start-Sleep -Milliseconds 700
    $log = Invoke-Adb @('logcat', '-d', '-t', '120', '-s', 'LCJS')
    $m = [regex]::Matches($log, 'map\s*->\s*(.+)$')
    if ($m.Count -eq 0) { return $null }
    return $m[$m.Count - 1].Groups[1].Value.Trim()
}

function Get-MapState([int]$Tries = 3) {
    # evaluateJavascript 回调拿到的是「JSON 字符串的 JSON 表示」，要解两层；
    # 3D↔2D 切换瞬间 WebView 可能在忙，取不到就重试
    for ($i = 0; $i -lt $Tries; $i++) {
        $raw = Invoke-Js "JSON.stringify(window.__LC3D?window.__LC3D.state():null)"
        if ($raw) {
            try {
                $v = $raw | ConvertFrom-Json
                if ($v -is [string]) { $v = $v | ConvertFrom-Json }
                if ($v) { return $v }
            } catch { }
        }
        Start-Sleep -Milliseconds 700
    }
    return $null
}

# ---------- 0. 环境 ----------
$devs = Invoke-Adb @('devices')
if ($devs -notmatch [regex]::Escape($Device)) {
    Add-Result 'ENV' 'BLOCKED' '设备在线' "设备 $Device 不在线"
    exit 2
}
$inst = (Invoke-Adb @('install', '-r', $Apk)).Trim().Split("`n")[-1]
if ($inst -notmatch 'Success') {
    Add-Result 'ENV' 'FAIL' 'install -r Success' $inst
    exit 1
}
Invoke-Adb @('shell', 'pm', 'grant', $Pkg, 'android.permission.ACCESS_FINE_LOCATION') | Out-Null
Invoke-Adb @('shell', 'pm', 'grant', $Pkg, 'android.permission.ACCESS_COARSE_LOCATION') | Out-Null
Invoke-Adb @('shell', 'am', 'force-stop', $Pkg) | Out-Null
Invoke-Adb @('logcat', '-c') | Out-Null
$start = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$Pkg/.MainActivity")
$total = [regex]::Match($start, 'TotalTime:\s*(\d+)').Groups[1].Value
Start-Sleep -Seconds 3
$pid0 = (Invoke-Adb @('shell', 'pidof', $Pkg)).Trim()
if (-not $pid0) {
    Add-Result 'ENV' 'FAIL' 'App 存活' '启动后进程不存在'
    exit 1
}
$ui = Get-Ui 'ui-first'
if ($ui.ok -and ($ui.xml -match '跳过')) { Tap-Text $ui.xml '跳过' | Out-Null; Start-Sleep -Milliseconds 700 }
Set-Heading 0
Add-Result 'ENV' $(if ($total) { 'PASS' } else { 'BLOCKED' }) '启动到前台' "pid=$pid0 TotalTime=${total}ms"

# ---------- 1. 罗盘转动 ----------
$expect = @{ 0 = '子'; 90 = '卯'; 180 = '午'; 270 = '酉' }
$rotateOk = 0
foreach ($h in 0, 90, 180, 270) {
    Set-Heading $h
    $u = Get-Ui "ui-heading$h"
    if (-not $u.ok) { Add-Result "M1-heading$h" 'BLOCKED' 'ui dump' 'dump 失败'; continue }
    $r = Get-Reading $u.xml
    $ok = $r -and ([double]$r.deg -eq [double]$h) -and ($r.earth -eq $expect[$h])
    if ($ok) { $rotateOk++ }
    Add-Result "M1-heading$h" $(if ($ok) { 'PASS' } else { 'FAIL' }) "度数 $h.00° 地盘 $($expect[$h])山" "度数=$($r.deg)° 地盘=$($r.earth)山" $u.path
    if ($h -eq 90) { Shot 'k01-heading90.png' | Out-Null }
}
if ($rotateOk -lt 4) { } # 单项已记

# ---------- 2. 罗盘浮层 ----------
$u = Get-Ui 'ui-map-mode'
$opened = $false
if ($u.ok) { $opened = Tap-Text $u.xml '罗盘' }
if (-not $opened) {
    Add-Result 'M2-compass-overlay' 'BLOCKED' '地图态「罗盘」入口' 'dump 里找不到「罗盘」或 dump 失败'
} else {
    Start-Sleep -Milliseconds 1000
    $c = Get-Ui 'ui-compass-overlay'
    $texts = if ($c.ok) { Get-Texts $c.xml } else { @() }
    $need = @('读盘', '隐藏', '切换', '坐南朝北', '罗盘复位')
    $miss = @($need | Where-Object { $texts -notcontains $_ })
    Shot 'k01-compass-overlay.png' | Out-Null
    Add-Result 'M2-compass-overlay' $(if ($miss.Count -eq 0) { 'PASS' } else { 'FAIL' }) '浮层含 读盘/隐藏/切换/坐南朝北/罗盘复位' "缺: $($miss -join ',')" (Join-Path $OutDir 'k01-compass-overlay.png')
}

# ---------- 3. 5 盘面切换 ----------
$dials = @('二十四山(阴)', '常用罗盘', '三合罗盘(白)', '三元罗盘(金)', '综合罗盘(白)')
$prefs = ''
for ($i = 0; $i -lt $dials.Count; $i++) {
    $u = Get-Ui "ui-dial-open$i"
    if ($u.ok -and ($u.xml -match '罗盘盘面')) {
        # 上一步没离开列表页：先返回，避免后面几步连环 BLOCKED
        Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
        Start-Sleep -Milliseconds 700
        $u = Get-Ui "ui-dial-open$i-b"
    }
    if ($u.ok -and ($u.xml -notmatch '切换')) { Tap-Text $u.xml '罗盘' | Out-Null; Start-Sleep -Milliseconds 700; $u = Get-Ui "ui-dial-open$i-c" }
    if (-not $u.ok -or -not (Tap-Text $u.xml '切换')) {
        Add-Result "M3-dial$i" 'BLOCKED' "盘面 $($dials[$i])" '浮层里找不到「切换」或 dump 失败'
        continue
    }
    Start-Sleep -Milliseconds 900
    $list = Get-Ui "ui-dial-list$i"
    if (-not $list.ok) { Add-Result "M3-dial$i" 'BLOCKED' '盘面列表' '列表 dump 失败'; continue }
    if ($i -eq 0) { Shot 'k01-dial-list.png' | Out-Null }
    # 列表里组标题「常用罗盘」与条目同名；条目在组标题之后 → 取最后一个同名节点
    $tapped = Tap-Text $list.xml $dials[$i] -Last
    if (-not $tapped) { $tapped = Tap-Text $list.xml ($dials[$i] + '（当前）') }
    Start-Sleep -Milliseconds 900
    $prefs = Invoke-Adb @('shell', 'run-as', $Pkg, 'cat', 'shared_prefs/lc_prefs.xml')
    $m = [regex]::Match($prefs, 'name="dial_index"\s+value="(\d+)"')
    $got = if ($m.Success) { [int]$m.Groups[1].Value } else { -1 }
    Add-Result "M3-dial$i" $(if ($tapped -and $got -eq $i) { 'PASS' } else { 'FAIL' }) "点「$($dials[$i])」→ dial_index=$i" "tapped=$tapped dial_index=$got" $list.path
}

# ---------- 4. 地图功能 ----------
$u = Get-Ui 'ui-back-to-map'
if ($u.ok -and (Tap-Text $u.xml '隐藏')) { Start-Sleep -Milliseconds 900 }
# M3 盘面列表返回后 MainActivity 会重新 resume，WebView 可能仍在加载：给足重试次数，
# 否则 M4 全组会被一次空返回判成 BLOCKED（2026-11-05 K-01 真机实测踩到）。
$s0 = Get-MapState 10
if (-not $s0) {
    Add-Result 'M4-base' 'BLOCKED' 'window.__LC3D.state()' 'JS 桥没有返回状态（map.html 未加载/注入失败）'
} else {
    # 4.1 3D
    $u = Get-Ui 'ui-map-tools'
    $ok3d = $u.ok -and (Tap-Text $u.xml '3D')
    Start-Sleep -Milliseconds 1500
    $s = Get-MapState
    Add-Result 'M4-3D' $(if ($ok3d -and $s -and $s.mode -eq '3d') { 'PASS' } else { 'FAIL' }) '点「3D」→ state.mode=3d' "mode=$($s.mode) pitch=$($s.pitch)" $u.path
    if ($s -and $s.mode -eq '3d') { Shot 'k01-3d.png' | Out-Null }

    # 4.2 回 2D，进罗盘浮层：旋转 / 坐南朝北 / 底图（这三项都在浮层工具里）
    $u = Get-Ui 'ui-map-tools2'
    if ($u.ok -and (Tap-Text $u.xml '3D')) { Start-Sleep -Milliseconds 1200 }

    $u = Get-Ui 'ui-reopen-compass'
    if ($u.ok) { Tap-Text $u.xml '罗盘' | Out-Null; Start-Sleep -Milliseconds 900 }
    $u = Get-Ui 'ui-compass-rotate'
    # M4-rotate 时序修正：基线必须紧邻「旋转」点击前读取，且给足重试。
    # 原实现把 b0 放在进罗盘前约 2s（且默认 3 次重试），3D→2D 后 WebView 仍在忙时会拿到空/旧值。
    $b0 = if ($u.ok) { (Get-MapState 10).bearing } else { $null }
    $rot = $u.ok -and (Tap-Text $u.xml '旋转')
    Start-Sleep -Milliseconds 800
    $b1 = (Get-MapState 10).bearing
    Add-Result 'M4-rotate' $(if ($rot -and ($null -ne $b0) -and ($null -ne $b1) -and (([double]$b1 - [double]$b0 + 360) % 360) -eq 15) { 'PASS' } else { 'FAIL' }) '点「旋转」→ bearing +15°' "before=$b0 after=$b1" $u.path

    $u = Get-Ui 'ui-compass-right'
    $sit = $u.ok -and (Tap-Text $u.xml '坐南朝北')
    Start-Sleep -Milliseconds 600
    $s1 = Get-MapState
    $sitOn = $sit -and $s1 -and $s1.sitNorth
    $u = Get-Ui 'ui-compass-right2'
    $img = $u.ok -and (Tap-Text $u.xml '实景')
    Start-Sleep -Milliseconds 800
    $s2 = Get-MapState
    $u = Get-Ui 'ui-compass-right3'
    $vec = $u.ok -and (Tap-Text $u.xml '地图')
    Start-Sleep -Milliseconds 800
    $s3 = Get-MapState
    Add-Result 'M4-sitNorth' $(if ($sitOn) { 'PASS' } else { 'FAIL' }) '点「坐南朝北」→ sitNorth=true' "sitNorth=$(if($s1){$s1.sitNorth}else{'null'})" $u.path
    Add-Result 'M4-base' $(if ($img -and $s2 -and $s2.base -eq 'img' -and $vec -and $s3 -and $s3.base -eq 'vec') { 'PASS' } else { 'FAIL' }) '点「实景」→base=img；点「地图」→base=vec' "img=$($s2.base) vec=$($s3.base)" $u.path

    # 4.4 图层 / 定位 / 截屏 / 立向
    $u = Get-Ui 'ui-map-tools3'
    if ($u.ok -and (Tap-Text $u.xml '隐藏')) { Start-Sleep -Milliseconds 800 }
    $u = Get-Ui 'ui-map-tools4'
    $layer = $u.ok -and (Tap-Text $u.xml '图层')
    Start-Sleep -Milliseconds 1200
    $lu = Get-Ui 'ui-layer-panel'
    $layerTexts = if ($lu.ok) { Get-Texts $lu.xml } else { @() }
    $layerOk = $layer -and ($layerTexts | Where-Object { $_ -match '矢量|影像|地形' })
    Add-Result 'M4-layer' $(if ($layerOk) { 'PASS' } else { 'FAIL' }) '点「图层」→ 出现 矢量/影像/地形' "texts=$((($layerTexts | Select-Object -First 8) -join ','))" $lu.path
    # 源面板是 WebView 覆盖层：返回键会直接退出 App，用 closeSources() 关
    Invoke-Js "closeSources();0" | Out-Null
    Start-Sleep -Milliseconds 600

    $u = Get-Ui 'ui-map-tools5'
    $loc = $u.ok -and (Tap-Text $u.xml '定位')
    Start-Sleep -Milliseconds 1500
    $u2 = Get-Ui 'ui-locate'
    $locTexts = if ($u2.ok) { Get-Texts $u2.xml } else { @() }
    Add-Result 'M4-locate' $(if ($loc) { 'PASS' } else { 'BLOCKED' }) '点「定位」触发 locate()' "tapped=$loc 页面未崩，dump=$($u2.ok)" $u2.path

    # 立向探针放在截屏之前：截屏会弹系统分享面板，盖住 App 后广播会丢
    $t = Invoke-Js "typeof setSitFace"
    Add-Result 'M4-lixiang' $(if ($t -match 'function') { 'PASS' } else { 'FAIL' }) 'map.html 提供 setSitFace(立向) 接口' "typeof setSitFace=$t" ''

    $before = (Invoke-Adb @('shell', 'ls', '/sdcard/Pictures/LC/')).Split("`n") | Where-Object { $_ -match '\.png$' }
    $u = Get-Ui 'ui-map-tools6'
    $cap = $u.ok -and (Tap-Text $u.xml '截屏')
    Start-Sleep -Seconds 3
    $after = (Invoke-Adb @('shell', 'ls', '/sdcard/Pictures/LC/')).Split("`n") | Where-Object { $_ -match '\.png$' }
    $new = @($after | Where-Object { $before -notcontains $_ })
    Add-Result 'M4-capture' $(if ($cap -and $new.Count -gt 0) { 'PASS' } else { 'FAIL' }) '点「截屏」→ /sdcard/Pictures/LC 新增 png' "new=$($new -join ',')" ''
}

# ---------- 5. logcat ----------
$log = Invoke-Adb @('logcat', '-d')
$log | Set-Content -Path (Join-Path $OutDir 'logcat.txt') -Encoding UTF8
$fatal = ([regex]::Matches($log, 'FATAL EXCEPTION')).Count
$anr = ([regex]::Matches($log, 'ANR in ' + [regex]::Escape($Pkg))).Count
Add-Result 'M5-logcat' $(if ($fatal -eq 0 -and $anr -eq 0) { 'PASS' } else { 'FAIL' }) 'FATAL=0 ANR=0' "FATAL=$fatal ANR=$anr" (Join-Path $OutDir 'logcat.txt')

# ---------- 汇总 ----------
$pass = @($script:Results | Where-Object status -eq 'PASS').Count
$fail = @($script:Results | Where-Object status -eq 'FAIL').Count
$blocked = @($script:Results | Where-Object status -eq 'BLOCKED').Count
$diag = @($script:Results | Where-Object status -eq 'DIAG').Count
$lines = @("# Android 冒烟（K-01）", "", "- 时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')", "- 设备：$Device", "- APK：$Apk（$((Get-FileHash $Apk -Algorithm SHA256).Hash)）", "", '| 用例 | 结果 | 期望 | 实际 |', '|---|---|---|---|')
foreach ($r in $script:Results) { $lines += "| $($r.id) | $($r.status) | $($r.expected) | $($r.actual) |" }
$lines += ''
$lines += "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag"
$lines | Set-Content -Path (Join-Path $OutDir 'summary.md') -Encoding UTF8
Write-Host "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag -> $OutDir"
if ($fail -gt 0) { exit 1 }
if ($blocked -gt 0) { exit 2 }
exit 0
