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
    [string]$OutDir,
    # 默认不自装：人工装包后只跑验收，避免与人工装包重复触发安装弹窗（K-01 纯净模式踩坑）。
    # 需要脚本自装时显式传 -Install；-SkipInstall 显式跳过（兼容既有调用点）。
    [switch]$Install,
    [switch]$SkipInstall,
    # 只跑第 4 段（地图功能）：M1–M3 会改前台/站点状态，需要干净复验 M4 时用。
    [switch]$OnlyM4
)

$ErrorActionPreference = 'Stop'
$Pkg = 'com.luopan.fengshui'
$Action = "$Pkg.DEBUG"
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# 证据脱敏：logcat 里 hwbr_engine_ContentUriUtils 会打出 map.html?tk=<web key>；落盘前统一
# 精确替换 + `tk=***` + 自检（自检不过 = 采集失败，不写文件）。见 tests/lib/LogRedact.ps1。
. (Join-Path $repo 'tests\lib\LogRedact.ps1')
$redactKeys = Get-TdtKeyValues

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
            if (Test-Path $p) {
                $raw = Get-Content $p -Raw -Encoding UTF8
                # 采完校验：dump 必须是本应用的界面（安装器/别的界面不算数）。
                # 权限弹窗（com.android.permissioncontroller）尤其要挡：`pm clear` 会清掉运行时权限，
                # 冷启动弹「是否允许…访问位置信息」，此时 dump 全是弹窗节点，外观和「应用界面没控件」一样。
                $own = $raw -match ('package="' + [regex]::Escape($Pkg) + '"')
                $dialog = ($raw -match 'package="com\.android\.permissioncontroller"') -or ($raw -match '是否允许.{0,20}访问')
                if ($own -and -not $dialog) {
                    return [pscustomobject]@{ ok = $true; xml = $raw; path = $p }
                }
                Remove-Item $p -ErrorAction SilentlyContinue
            }
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

function Get-UiNodes([string]$xml) {
    # 逐节点取 class/text/resource-id/bounds：判据要看「哪个控件」而不是「全文里有没有这个词」
    # （旧 M4-layer 用 -match '矢量|影像|地形' 被源名「天地图地形」命中，判出假绿）。
    foreach ($m in [regex]::Matches($xml, '<node\b[^>]*>')) {
        $n = $m.Value
        [pscustomobject]@{
            class  = [regex]::Match($n, 'class="([^"]*)"').Groups[1].Value
            text   = [regex]::Match($n, 'text="([^"]*)"').Groups[1].Value
            rid    = [regex]::Match($n, 'resource-id="([^"]*)"').Groups[1].Value
            bounds = [regex]::Match($n, 'bounds="([^"]*)"').Groups[1].Value
        }
    }
}

function Test-SrcPanelLayer([string]$PreXml, [string]$PanelXml) {
    # 「图层」判据：只认 map.html #srcPanel 真的打开了，且瓦片 URL 没被画成可见文字。
    # 抽成纯函数是为了能拿历史 dump 复算、做变异测试（断言必须能失败）。
    $preTexts = if ($PreXml) { Get-Texts $PreXml } else { @() }
    $prePanel = $preTexts -contains '底图源'      # 点之前面板必须关着，否则「点了才出现」无从判定
    $nodes = if ($PanelXml) { @(Get-UiNodes $PanelXml) } else { @() }
    $texts = @($nodes | Where-Object { $_.text -ne '' } | ForEach-Object { $_.text })
    $hasPanel = [bool]($nodes | Where-Object { $_.rid -eq 'srcPanel' }) -and ($texts -contains '底图源')
    $srcs = @($texts | Where-Object { $_ -match '^\d+\.\s' }).Count
    # 瓦片 URL 只允许挂在 Image 节点上（WebView 把 <img class="tile" src=…> 的 URL 暴露成 text）；
    # 一旦被 TextView/Button 画到屏幕上才是缺陷。
    $urlNodes = @($nodes | Where-Object { $_.text -match 'wmts\?SERVICE' })
    $urlShown = @($urlNodes | Where-Object { $_.bounds -ne '[0,0][0,0]' -and $_.class -match 'TextView|Button' }).Count
    [pscustomobject]@{
        prePanel = $prePanel; panel = $hasPanel; srcs = $srcs
        urlNodes = $urlNodes.Count; urlShown = $urlShown
        ok = ((-not $prePanel) -and $hasPanel -and ($srcs -ge 12) -and ($urlShown -eq 0))
    }
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
# 安装会话残留闸：18:42 有一条 `adb install` 挂住占着安装会话，后续安装全排队到超时、
# 系统报 `INSTALL_FAILED_ABORTED: User rejected permissions`——看着像系统拒装，其实是会话被占。
# 本机 pm 不支持 `list sessions`（unknown list type），所以查主机侧残留的 adb install 进程。
$pendingInstall = @(Get-CimInstance Win32_Process -Filter "Name='adb.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match '(\s|^)install(\s|$)' -and $_.CommandLine -notmatch 'fork-server' })
if ($pendingInstall.Count -gt 0) {
    Add-Result 'ENV' 'BLOCKED' '无挂起的 adb install 进程' ("发现 " + $pendingInstall.Count + " 个残留 install：" + (($pendingInstall | ForEach-Object { "pid=$($_.ProcessId)" }) -join ','))
    exit 2
}
# 安装段：默认不自装（人工装包 + 脚本只跑验收）。-Install 显式自装，-SkipInstall 显式跳过。
if ($Install -and -not $SkipInstall) {
    $inst = (Invoke-Adb @('install', '-r', $Apk)).Trim().Split("`n")[-1]
    if ($inst -notmatch 'Success') {
        Add-Result 'ENV' 'FAIL' 'install -r Success' $inst
        exit 1
    }
} else {
    Write-Host "ENV: 跳过安装段（默认不自装；人工装包或 -SkipInstall）"
}
Invoke-Adb @('shell', 'pm', 'grant', $Pkg, 'android.permission.ACCESS_FINE_LOCATION') | Out-Null
Invoke-Adb @('shell', 'pm', 'grant', $Pkg, 'android.permission.ACCESS_COARSE_LOCATION') | Out-Null
# 授权必须核实：`pm clear` 会清运行时权限（本机 EMUI 实测），静默失败会让冷启动被权限弹窗挡住，
# 后面 8 条全成 BLOCKED、看不出根因（smoke6 就是这样）。
$grantDump = Invoke-Adb @('shell', 'dumpsys', 'package', $Pkg)
if ($grantDump -notmatch 'ACCESS_FINE_LOCATION: granted=true') {
    Add-Result 'ENV' 'BLOCKED' '定位权限已授予' 'pm grant 未生效（ACCESS_FINE_LOCATION 仍为 denied）；冷启动会弹权限框，整批结果不可采信'
    exit 2
}
Invoke-Adb @('shell', 'am', 'force-stop', $Pkg) | Out-Null
Invoke-Adb @('logcat', '-c') | Out-Null
$start = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', "$Pkg/.MainActivity")
$start | Set-Content -Path (Join-Path $OutDir 'am-start-w.txt') -Encoding UTF8
$total = [regex]::Match($start, 'TotalTime:\s*(\d+)').Groups[1].Value
Start-Sleep -Seconds 3
$pid0 = (Invoke-Adb @('shell', 'pidof', $Pkg)).Trim()
if (-not $pid0) {
    Add-Result 'ENV' 'FAIL' 'App 存活' '启动后进程不存在'
    exit 1
}
# 前台校验：安装器/别的界面占前台时，后续 dump 全是别的界面，整批结果必须作废。
$focusDump = Invoke-Adb @('shell', 'dumpsys', 'window')
$focusLine = (($focusDump -split "`n") | Select-String 'mCurrentFocus' | Select-Object -First 1) -join ''
if ($focusLine -notmatch [regex]::Escape($Pkg)) {
    Add-Result 'ENV' 'BLOCKED' "mCurrentFocus 是 $Pkg" "前台不是应用（$focusLine）；可能被安装器占用，整批结果作废"
    exit 2
}
$ui = Get-Ui 'ui-first'
if ($ui.ok -and ($ui.xml -match '跳过')) { Tap-Text $ui.xml '跳过' | Out-Null; Start-Sleep -Milliseconds 700 }
# `-SkipInstall` 时磁盘包与设备上实际运行的包可能不是同一个：报告里的 APK 必须来自设备 pull 的 base.apk。
# （smoke6/m4-only-b 都吃过这个亏：summary 写的是磁盘新包 sha，设备跑的还是旧包。）
$devApkSha = ''; $devApkSize = 0
$remoteApk = ((Invoke-Adb @('shell', 'pm', 'path', $Pkg)) -replace 'package:', '').Trim()
$devTmp = Join-Path ([IO.Path]::GetTempPath()) ("lc_dev_" + [guid]::NewGuid().ToString('N') + ".apk")
if ($remoteApk) {
    Invoke-Adb @('pull', $remoteApk, $devTmp) | Out-Null
    if (Test-Path $devTmp) {
        $devApkSha = (Get-FileHash $devTmp -Algorithm SHA256).Hash
        $devApkSize = (Get-Item $devTmp).Length
        Remove-Item $devTmp -Force -ErrorAction SilentlyContinue
    }
}
$diskApkSha = (Get-FileHash $Apk -Algorithm SHA256).Hash
if ($devApkSha) {
    $apkLine = "设备运行 APK（pull 自 $remoteApk）：sha256 ``$devApkSha`` / $devApkSize B"
    if ($devApkSha -ne $diskApkSha) {
        Add-Result 'ENV-APK' 'DIAG' '设备运行包 = 指定包' "设备 $devApkSha ≠ 磁盘 $diskApkSha；本轮结果只对设备上那只包成立" ''
    }
} else {
    $apkLine = "设备 APK 未取到（pm path 为空）；磁盘包 sha256 ``$diskApkSha``（仅供参考）"
    Add-Result 'ENV-APK' 'BLOCKED' '能从设备 pull 出 base.apk' 'pm path 为空，报告无法绑定到实际运行的包' ''
}
Set-Heading 0
Add-Result 'ENV' $(if ($total) { 'PASS' } else { 'BLOCKED' }) '启动到前台' "pid=$pid0 TotalTime=${total}ms"

# ---------- 1. 罗盘转动 ----------
# -OnlyM4：M1–M3 会改变前台/站点状态（盘面列表、切换、返回），要单独复验 M4 时必须能跳过。
if (-not $OnlyM4) {
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
    # K-02：罗盘态顶部栏应有 返回/搜索/图层/隐藏 四项（原版 bugs.md:264；此前只有「隐藏」）
    $top = @('返回', '搜索', '图层', '隐藏')
    $topMiss = @($top | Where-Object { $texts -notcontains $_ })
    Add-Result 'M2-topbar' $(if ($topMiss.Count -eq 0) { 'PASS' } else { 'FAIL' }) '罗盘态顶部栏含 返回/搜索/图层/隐藏' "缺: $($topMiss -join ',')" $c.path
    # K-02：罗盘态「图层」必须能开出底图源面板（复用 openSources），否则该控件是死按钮
    if ($topMiss -notcontains '图层') {
        $layTapped = Tap-Text $c.xml '图层'
        Start-Sleep -Milliseconds 1200
        $cl = Get-Ui 'ui-compass-layer'
        $clv = Test-SrcPanelLayer -PreXml $c.xml -PanelXml $(if ($cl.ok) { $cl.xml } else { '' })
        $clSt = if (-not $cl.ok -or -not $layTapped) { 'BLOCKED' } elseif ($clv.ok) { 'PASS' } else { 'FAIL' }
        Add-Result 'M2-compass-layer' $clSt '罗盘态「图层」→ #srcPanel 出现（12 源）' "tapped=$layTapped panel=$($clv.panel) srcs=$($clv.srcs)" $cl.path
        Invoke-Js "closeSources();0" | Out-Null
        Start-Sleep -Milliseconds 600
    } else {
        Add-Result 'M2-compass-layer' 'BLOCKED' '罗盘态「图层」→ #srcPanel 出现（12 源）' '顶栏没有「图层」，无法判定' ''
    }
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
    # 第 0 盘：默认值就是 0，选中当前盘面时 prefs 可能根本没有这个键——「键缺失」与 dial_index=0 行为等价，
    # 不能把等价记成 FAIL（smoke7 的 M3-dial0 就是这么假红的）。
    $dialOk = $tapped -and (($got -eq $i) -or ($i -eq 0 -and $got -eq -1))
    $gotShown = if ($got -eq -1) { '缺失(默认0)' } else { "$got" }
    Add-Result "M3-dial$i" $(if ($dialOk) { 'PASS' } else { 'FAIL' }) "点「$($dials[$i])」→ dial_index=$i（第0盘允许键缺失=默认 0）" "tapped=$tapped dial_index=$gotShown" $list.path
}
}  # end if (-not $OnlyM4)

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
    if (-not $u.ok) {
        Add-Result 'M4-3D' 'BLOCKED' '点「3D」→ state.mode=3d' 'ui-map-tools dump 失败（uiautomator/前台异常），未判定' $u.path
    } else {
        $ok3d = Tap-Text $u.xml '3D'
        Start-Sleep -Milliseconds 1500
        $s = Get-MapState 10
        Add-Result 'M4-3D' $(if ($ok3d -and $s -and $s.mode -eq '3d') { 'PASS' } else { 'FAIL' }) '点「3D」→ state.mode=3d' "mode=$($s.mode) pitch=$($s.pitch)" $u.path
        if ($s -and $s.mode -eq '3d') { Shot 'k01-3d.png' | Out-Null }
    }

    # 4.2 回 2D，进罗盘浮层：旋转 / 坐南朝北 / 底图（这三项都在浮层工具里）
    $u = Get-Ui 'ui-map-tools2'
    if ($u.ok -and (Tap-Text $u.xml '3D')) { Start-Sleep -Milliseconds 1200 }

    $u = Get-Ui 'ui-reopen-compass'
    if ($u.ok) { Tap-Text $u.xml '罗盘' | Out-Null; Start-Sleep -Milliseconds 900 }
    $u = Get-Ui 'ui-compass-rotate'
    if (-not $u.ok) {
        Add-Result 'M4-rotate' 'BLOCKED' '点「旋转」→ bearing +15°' 'ui-compass-rotate dump 失败（uiautomator/未进罗盘态），未判定' $u.path
    } else {
        # M4-rotate 时序修正：基线必须紧邻「旋转」点击前读取，且给足重试。
        # 原实现把 b0 放在进罗盘前约 2s（且默认 3 次重试），3D→2D 后 WebView 仍在忙时会拿到空/旧值。
        $b0 = (Get-MapState 10).bearing
        $rot = Tap-Text $u.xml '旋转'
        Start-Sleep -Milliseconds 800
        $b1 = (Get-MapState 10).bearing
        Add-Result 'M4-rotate' $(if ($rot -and ($null -ne $b0) -and ($null -ne $b1) -and (([double]$b1 - [double]$b0 + 360) % 360) -eq 15) { 'PASS' } else { 'FAIL' }) '点「旋转」→ bearing +15°' "before=$b0 after=$b1" $u.path
    }

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
    # 「图层」的真行为：MainActivity.java:500 case "图层" → js("openSources()") → map.html:74 #srcPanel（12 源）。
    # 不是「矢量/影像/地形」——那三项是 map.html:51-53 的 #btn_vec/#btn_img/#btn_ter 底图按钮。
    # 旧判据 -match '矢量|影像|地形' 会被源名「7. 天地图地形」命中，smoke5 由此假绿（见 F-16）。
    $u = Get-Ui 'ui-map-tools3'
    if ($u.ok -and (Tap-Text $u.xml '隐藏')) { Start-Sleep -Milliseconds 800 }
    $u = Get-Ui 'ui-map-tools4'
    $layer = $u.ok -and (Tap-Text $u.xml '图层')
    Start-Sleep -Milliseconds 1200
    $lu = Get-Ui 'ui-layer-panel'
    $lv = Test-SrcPanelLayer -PreXml $(if ($u.ok) { $u.xml } else { '' }) -PanelXml $(if ($lu.ok) { $lu.xml } else { '' })
    $layerStatus = if (-not $lu.ok -or -not $layer) { 'BLOCKED' } elseif ($lv.ok) { 'PASS' } else { 'FAIL' }
    Add-Result 'M4-layer' $layerStatus '点「图层」→ 底图源 #srcPanel 出现（「底图源」+12 条源），瓦片 URL 未被可见文本显示' "tapped=$layer prePanel=$($lv.prePanel) panel=$($lv.panel) srcs=$($lv.srcs) urlNodes=$($lv.urlNodes) urlShown=$($lv.urlShown)" $lu.path
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
# 落盘先脱敏（tk=<web key>），返回脱敏后的文本，后续 FATAL/ANR 统计用同一份。
$log = Save-RedactedText -Path (Join-Path $OutDir 'logcat.txt') -Text $log -Label 'smoke-logcat' -Keys $redactKeys
$logLines = $log -split "`n"
# FATAL 只认本应用进程：uiautomator 自身的 FATAL 不算应用崩溃。
$fatal = 0
$fatalOther = 0
for ($i = 0; $i -lt $logLines.Count; $i++) {
    if ($logLines[$i] -match 'FATAL EXCEPTION') {
        $end = [math]::Min($i + 6, $logLines.Count - 1)
        $block = $logLines[$i..$end] -join "`n"
        if ($block -match ('Process:\s*' + [regex]::Escape($Pkg))) { $fatal++ } else { $fatalOther++ }
    }
}
$anr = ([regex]::Matches($log, 'ANR in ' + [regex]::Escape($Pkg))).Count
Add-Result 'M5-logcat' $(if ($fatal -eq 0 -and $anr -eq 0) { 'PASS' } else { 'FAIL' }) 'FATAL(app)=0 ANR(app)=0' "FATAL(app)=$fatal FATAL(other)=$fatalOther ANR=$anr" (Join-Path $OutDir 'logcat.txt')

# 其余文本产物（ui*.xml / summary.md 等）也过一遍脱敏；文件名不许带 key 片段。
foreach ($f in (Get-ChildItem $OutDir -File | Where-Object { $_.Extension -in @('.txt', '.xml', '.csv', '.md', '.log') })) {
    Invoke-RedactFile -Path $f.FullName -Keys $redactKeys | Out-Null
}
Assert-NoKeyInNames -Path $OutDir -Keys $redactKeys

# ---------- 汇总 ----------
$pass = @($script:Results | Where-Object status -eq 'PASS').Count
$fail = @($script:Results | Where-Object status -eq 'FAIL').Count
$blocked = @($script:Results | Where-Object status -eq 'BLOCKED').Count
$diag = @($script:Results | Where-Object status -eq 'DIAG').Count
$lines = @("# Android 冒烟（K-01）", "", "- 时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')", "- 设备：$Device", "- $apkLine", "- 磁盘 APK（参考）：``$Apk``（$diskApkSha）", "", '| 用例 | 结果 | 期望 | 实际 |', '|---|---|---|---|')
foreach ($r in $script:Results) { $lines += "| $($r.id) | $($r.status) | $($r.expected) | $($r.actual) |" }
$lines += ''
$lines += "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag"
$lines | Set-Content -Path (Join-Path $OutDir 'summary.md') -Encoding UTF8
Write-Host "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag -> $OutDir"
if ($fail -gt 0) { exit 1 }
if ($blocked -gt 0) { exit 2 }
exit 0
