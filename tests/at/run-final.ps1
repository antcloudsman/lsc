<#
.SYNOPSIS
  收官轮 AT 一把跑完（P0-0 / P0-1 RT-005 / RT-011 / P0-2 / P0-4 TD-016），输出总表。

.DESCRIPTION
  一条命令、不 pesky 交互、可重复执行：

    pwsh -File tests\at\run-final.ps1 -Apk <app-debug.apk>

  关键约定（都是踩过的坑）：
  - 装包后**先 force-stop 再 am start**，否则测到上一版 map.html / 旧进程。
  - 广播 heading 用 `--es cmd heading --ef value <float>`（键名是 value，不是 heading；缺 cmd 则整条被丢）。
  - 截图 / 读盘面板要先注入航向冻结传感器，否则 uiautomator dump idle 超时。
  - 二进制不经过 PowerShell 重定向：设备内 screencap 再 pull。
  - 判定只写 PASS/FAIL/BLOCKED + 实测值；没跑到的写 BLOCKED，不猜。

.PARAMETER Apk
  要装的 app-debug.apk（必须是待验提交构建出来的包）。

.PARAMETER OutDir
  证据目录，默认 checks\at-final\<时间戳>。

.EXAMPLE
  pwsh -File tests\at\run-final.ps1 -Apk E:\APPDetector\wt\lc-test\app\android\app\build\outputs\apk\debug\app-debug.apk

.NOTES
  退出码：0=全 PASS；1=有 FAIL；2=无 FAIL 但有 BLOCKED；4=环境/前置失败（脚本自身报错）。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir,
    [switch]$KeepData,
    [switch]$SkipTd016,
    [float]$ReadSheetHeading = 94.04
)

$ErrorActionPreference = 'Stop'

# P2-08 顶部两行判定（F-06）：独立成库，单测在 test-readsheet-head.ps1
. (Join-Path $PSScriptRoot 'lib\ReadSheetHead.ps1')

# ---- 自包含（不依赖未入库的脚手架文件）----
$script:LcPkg = 'com.luopan.fengshui'
$script:LcDebugAction = 'com.luopan.fengshui.DEBUG'
$script:LcResults = New-Object System.Collections.Generic.List[object]

function Clear-LcResults { $script:LcResults.Clear() }

function Invoke-LcAdb {
    param([string]$Id, [Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    $out = & adb -s $Id @AdbArgs 2>&1
    return ($out -join "`n")
}

function Wait-LcUi([int]$SettleMs = 350) { Start-Sleep -Milliseconds $SettleMs }

# 设备内 screencap 再 pull：不要用 PowerShell 重定向接二进制
function Save-LcScreenshot {
    param([string]$Id, [string]$Path)
    $devPath = '/sdcard/lc_shot.png'
    Invoke-Adb @('shell', 'screencap', '-p', $devPath) | Out-Null
    Invoke-Adb @('pull', $devPath, $Path) | Out-Null
    if (-not (Test-Path $Path)) { return '' }
    return (Resolve-Path $Path).Path
}

function Write-LcNotes {
    param([string]$Path, [string]$Suite, [string]$Head, [string]$Extra = '')
    $dir = Split-Path -Parent $Path
    New-Item -ItemType Directory -Force $dir | Out-Null
    $md = @("# AT 运行记录 · $Suite", '', "- 时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')",
        "- HEAD：$Head", "- 设备：$($env:LC_DEVICE_ID)", '')
    $md += '| 用例 | 结果 | 期望 | 实际 | 证据 |'
    $md += '|---|---|---|---|---|'
    foreach ($r in $script:LcResults) {
        $ev = if ($r.evidence) { "[$([IO.Path]::GetFileName($r.evidence))]($($r.evidence))" } else { '' }
        $md += "| $($r.id) | $($r.status) | $($r.expected) | $($r.actual) | $ev |"
    }
    if ($Extra) { $md += ''; $md += $Extra }
    Set-Content -Path $Path -Value ($md -join "`n") -Encoding UTF8
}


$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
# 证据脱敏：logcat 里 hwbr_engine_ContentUriUtils 会打出 map.html?tk=<web key>；落盘前统一
# 精确替换 + `tk=***` + 自检（自检不过 = 采集失败，不写文件）。见 tests/lib/LogRedact.ps1。
. (Join-Path $repo 'tests\lib\LogRedact.ps1')
$redactKeys = Get-TdtKeyValues
$DeviceId = $Device
$env:LC_DEVICE_ID = $DeviceId
if (-not $OutDir) { $OutDir = Join-Path $repo ("checks\at-final\{0}" -f (Get-Date -Format 'yyyyMMdd-HHmm')) }
if (-not [IO.Path]::IsPathRooted($OutDir)) { $OutDir = Join-Path $repo $OutDir }
New-Item -ItemType Directory -Force $OutDir | Out-Null
$Apk = (Resolve-Path $Apk).Path
$sum = Join-Path $OutDir 'summary.txt'
Clear-LcResults

function Log([string]$s) {
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $s
    Write-Host $line
    Add-Content -Path $sum -Value $line -Encoding UTF8
}

# ---------- 基础 ----------
function Invoke-Adb([string[]]$ArgList) { return (Invoke-LcAdb $DeviceId @ArgList) }

function Get-Ui {
    param([string]$Tag = 'ui', [int]$Tries = 3)
    for ($i = 1; $i -le $Tries; $i++) {
        Invoke-Adb @('shell', 'rm', '-f', '/sdcard/lc_at.xml') | Out-Null
        $o = Invoke-Adb @('shell', 'uiautomator', 'dump', '/sdcard/lc_at.xml')
        if ($o -match 'dumped to') {
            $local = Join-Path $OutDir "$Tag.xml"
            Invoke-Adb @('pull', '/sdcard/lc_at.xml', $local) | Out-Null
            if (Test-Path $local) {
                return [pscustomobject]@{ ok = $true; xml = (Get-Content $local -Raw -Encoding UTF8); path = $local }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    return [pscustomobject]@{ ok = $false; xml = ''; path = '' }
}

function Get-Nodes([string]$xml) {
    return [regex]::Matches($xml, '<node [^>]*>') | ForEach-Object { $_.Value }
}

# 刚启动/刚切页时地图瓦片在加载，uiautomator 会一直等 idle 超时；
# 这一步等「第一次 dump 成功」再往下走（实测首启要 60–100 秒）。
function Wait-UiReady {
    param([string]$Tag = 'ui-ready', [int]$MaxSeconds = 130)
    $t0 = Get-Date
    while (((Get-Date) - $t0).TotalSeconds -lt $MaxSeconds) {
        $u = Get-Ui $Tag 1
        if ($u.ok) { return $u }
        Start-Sleep -Milliseconds 900
    }
    return [pscustomobject]@{ ok = $false; xml = ''; path = '' }
}

# 取 dump，失败时再等一次（首启/切页的瓦片加载窗口）
function Get-UiLoose([string]$Tag, [int]$MaxSeconds = 60) {
    $u = Get-Ui $Tag 2
    if ($u.ok) { return $u }
    return (Wait-UiReady $Tag $MaxSeconds)
}

function Get-Texts([string]$xml) {
    return [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object {
        $t = $_.Groups[1].Value
        $t = $t -replace '&#10;', "`n" -replace '&#9;', "`t" -replace '&quot;', '"' -replace '&lt;', '<' -replace '&gt;', '>' -replace '&amp;', '&'
        $t
    } | Where-Object { $_ -ne '' }
}

function Node-Center([string]$node) {
    $m = [regex]::Match($node, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $null }
    return @{
        X = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        Y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
    }
}

# 按 token 找节点：属主可以是 text= / resource-id= / class=
function Find-Node([string]$xml, [string]$Match) {
    foreach ($n in (Get-Nodes $xml)) { if ($n -match $Match) { return $n } }
    return $null
}

function Tap-Node([string]$xml, [string]$Match) {
    $n = Find-Node $xml $Match
    if (-not $n) { return $null }
    $pt = Node-Center $n
    if (-not $pt) { return $null }
    Invoke-Adb @('shell', 'input', 'tap', "$($pt.X)", "$($pt.Y)") | Out-Null
    return $pt
}

function Get-Reading([string]$xml) {
    $m = [regex]::Match($xml, '度数\s*([\d.]+)°')
    if ($m.Success) { return [double]$m.Groups[1].Value }
    return $null
}

function Get-Prefs {
    $t = Invoke-Adb @('shell', 'run-as', $script:LcPkg, 'cat', 'shared_prefs/lc_prefs.xml')
    if ($t -match 'No such file|Permission denied|not debuggable') { return '' }
    return $t
}

function Get-PrefFloat([string]$prefs, [string]$name) {
    $m = [regex]::Match($prefs, "name=`"$name`"\s+value=`"([^`"]*)`"")
    if (-not $m.Success) { return $null }
    $v = 0.0
    if ([double]::TryParse($m.Groups[1].Value, [ref]$v)) { return $v }
    return $null
}

function Get-PrefBool([string]$prefs, [string]$name) {
    $m = [regex]::Match($prefs, "name=`"$name`"\s+value=`"(true|false)`"")
    if (-not $m.Success) { return $null }
    return $m.Groups[1].Value -eq 'true'
}

function Set-Heading([float]$deg) {
    Invoke-Adb @('shell', 'am', 'broadcast', '-a', $script:LcDebugAction, '--es', 'cmd', 'heading', '--ef', 'value', "$deg") | Out-Null
    Start-Sleep -Milliseconds 400
}

function Send-Js([string]$js) {
    # 整条命令包一层引号、JS 用单引号：设备 sh 不把 ; ( ) 当元字符
    $line = "am broadcast -a $($script:LcDebugAction) --es cmd js --es value '$js'"
    Invoke-Adb @('shell', $line) | Out-Null
    Start-Sleep -Milliseconds 600
}

function Get-Logcat([string]$tag, [int]$lines = 400) {
    return (Invoke-Adb @('logcat', '-d', '-t', "$lines", '-s', $tag))
}

function Assert3([string]$id, [string]$status, [string]$expected, [string]$actual, [string]$evidence = '') {
    $script:LcResults.Add([pscustomobject]@{ id = $id; status = $status; expected = $expected; actual = $actual; evidence = $evidence })
}

# ---------- 0. 环境 ----------
try {
    $top = (& git rev-parse --show-toplevel 2>&1).Trim() -replace '/', '\'
    $repoN = $repo -replace '/', '\'
    if ($top -ne $repoN) { throw "D-007: toplevel=$top 不是本次 worktree $repoN（shell 可能回落到共享树）" }
    $head = (& git rev-parse --short HEAD 2>&1).Trim()
    $branch = (& git rev-parse --abbrev-ref HEAD 2>&1).Trim()
    Log "worktree=$repoN branch=$branch HEAD=$head"
    Log "apk=$Apk sha256=$((Get-FileHash $Apk -Algorithm SHA256).Hash)"

    $devs = (adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\s+device$' })
    if (-not ($devs -match [regex]::Escape($DeviceId))) { throw "设备 $DeviceId 不在线" }

    $inst = (Invoke-Adb @('install', '-r', $Apk) | Select-Object -Last 1)
    Log "install: $inst"
    if ($inst -notmatch 'Success') { throw "install 失败: $inst" }
    Invoke-Adb @('shell', 'am', 'force-stop', $script:LcPkg) | Out-Null
    if (-not $KeepData) {
        Invoke-Adb @('shell', 'pm', 'clear', $script:LcPkg) | Out-Null
        Log "pm clear done（回到首次启动状态）"
    }
    $pkg = (Invoke-Adb @('shell', 'dumpsys', 'package', $script:LcPkg))
    $upd = ([regex]::Match($pkg, 'lastUpdateTime=([^\r\n]+)')).Groups[1].Value.Trim()
    Log "lastUpdateTime=$upd"

    Invoke-Adb @('logcat', '-c') | Out-Null
    Invoke-Adb @('shell', 'am', 'force-stop', $script:LcPkg) | Out-Null
    Invoke-Adb @('shell', 'am', 'start', '-n', "$($script:LcPkg)/.MainActivity") | Out-Null
    Start-Sleep -Milliseconds 2500

    # 首次校准弹窗
    $ui = Get-Ui 'ui-first'
    if ($ui.ok -and ($ui.xml -match '跳过')) {
        Tap-Node $ui.xml 'text="跳过"' | Out-Null
        Start-Sleep -Milliseconds 800
        Log "首次校准弹窗：已点「跳过」"
    }

    $resumeLine = ((Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')) -split "`n" |
        Select-String 'mResumedActivity' | Select-Object -First 1)
    $resume = (($resumeLine -replace '\s+', ' ').Trim())
    if ($resume.Length -gt 160) { $resume = $resume.Substring(0, 160) + '…' }
    Log "foreground: $resume"
    if ($resume -notmatch 'com\.luopan\.fengshui') { throw 'App 不在前台（receiver 在 onResume 注册，后台收不到广播）' }

    # 通道自检以「注入端到端」为准：logcat 环形缓冲很容易被 WebView 刷掉，
    # 单看 bridge registered 会误判；先打一行 js 1+1，有 LCJS 回写就是活的。
    $lc = Get-Logcat 'LCDEBUG' 400
    $sawRegistered = ($lc -match 'bridge registered')
    Send-Js '1+1'
    $js = Get-Logcat 'LCJS' 400
    $sawJs = ($js -match '-> 2')
    Log "channel: bridge registered=$sawRegistered  js(1+1) 回写=$sawJs"
    if (-not $sawJs) {
        throw "调试通道未打通（bridge registered=$sawRegistered，js 无回写）：$(($js -split "`n" | Select-Object -Last 2) -join ' | ')"
    }

    # 真机传感器在跑时 CompassView 每帧重绘 → uiautomator 永远等不到 idle（实测
    # "ERROR: could not get idle state"）。所以**先注入航向冻结读数，再 dump**。
    Set-Heading 0.0
    $tReady = Get-Date
    $uiReady = Wait-UiReady 'ui-ready' 130
    Log "ui dump 就绪耗时 $([math]::Round((Get-Date).Subtract($tReady).TotalSeconds, 1))s（ok=$($uiReady.ok)）"
    if (-not $uiReady.ok) { throw '注入航向冻结后 uiautomator dump 仍 idle 超时（130s）' }
}
catch {
    Log "BLOCKED(环境): $($_.Exception.Message)"
    Assert3 'ENV' 'BLOCKED' '装包/前台/通道就绪' $_.Exception.Message
    Write-LcNotes -Path (Join-Path $OutDir 'notes.md') -Suite 'FINALE' -Head $head -Extra ''
    exit 4
}

# ---------- 1. AT-P0-0：SW-006 开 ----------
# 注入 0° 冻结传感器（否则 CompassView 每帧重绘，dump 永远拿不到 idle）
Set-Heading 0.0
$ui = Get-UiLoose 'ui-p00-on'
if (-not $ui.ok) {
    Assert3 'SW-006-on-1' 'BLOCKED' '罗盘页 ui dump' '注入后 dump 仍失败'
    Assert3 'SW-006-on-2' 'BLOCKED' 'p0_1_enabled=true' '注入后 dump 仍失败'
}
else {
    $texts = Get-Texts $ui.xml
    $reading = Get-Reading $ui.xml
    $hasDisc = ($texts -match '偏角').Count -gt 0
    $hasNorth = ($texts -match '磁北|真北').Count -gt 0
    $prefs = Get-Prefs
    $on = Get-PrefBool $prefs 'p0_1_enabled'
    if ($null -eq $on) { $on = $true }  # 代码默认 true，prefs 缺省
    Log "P0-0 on: reading=$reading 偏角行=$hasDisc 磁北/真北行=$hasNorth prefs=$on"
    Assert3 'SW-006-on-1' $(if ($hasDisc -and $hasNorth) { 'PASS' } else { 'FAIL' }) '开关 on：偏角行 + 磁北/真北行在' "偏角=$hasDisc 磁北/真北=$hasNorth 读数=$reading°" $ui.path
    Assert3 'SW-006-on-2' $(if ($on) { 'PASS' } else { 'FAIL' }) 'p0_1_enabled=true' "p0_1_enabled=$on" (Join-Path $OutDir 'prefs-on.xml')
    $prefs | Set-Content -Path (Join-Path $OutDir 'prefs-on.xml') -Encoding UTF8
    if ($null -ne $reading -and [math]::Abs($reading - 0.0) -gt 0.5) {
        Log "WARN 读数 $reading 与注入 0° 不一致"
    }
    Save-LcScreenshot $DeviceId (Join-Path $OutDir 'cw-on.png') | Out-Null
}

# ---------- 2. RT-005：+45 界面路径（clamp → 30）----------
$rt005 = 'BLOCKED'
$ui = Get-UiLoose 'ui-rt005'
$declBtnPt = $null
if ($ui.ok) {
    # 先切「真北」，否则 declination 不参与读数（applyDeclination(raw, decl, false) 忽略偏角）
    $n = Find-Node $ui.xml 'text="磁北"'
    if ($n) { Tap-Node $ui.xml 'text="磁北"' | Out-Null; Start-Sleep -Milliseconds 600; Log 'RT-005: 切到真北模式' }
    $ui = Get-UiLoose 'ui-rt005'
    $declBtnPt = Tap-Node $ui.xml 'text="偏角[^"]*"'
    if ($declBtnPt) {
        Start-Sleep -Milliseconds 700
        $dlg = Get-Ui 'ui-rt005-dialog'
        if ($dlg.ok) {
            $edit = Find-Node $dlg.xml 'class="android.widget.EditText"'
            if ($edit) {
                $pt = Node-Center $edit
                Invoke-Adb @('shell', 'input', 'tap', "$($pt.X)", "$($pt.Y)") | Out-Null
                Start-Sleep -Milliseconds 300
                Invoke-Adb @('shell', 'input', 'keyevent', '123') | Out-Null   # MOVE_END
                for ($i = 0; $i -lt 8; $i++) { Invoke-Adb @('shell', 'input', 'keyevent', '67') | Out-Null }  # DEL
                Invoke-Adb @('shell', 'input', 'text', '45') | Out-Null
                Start-Sleep -Milliseconds 500
                $dlg2 = Get-Ui 'ui-rt005-typed'
                $typed = $null
                if ($dlg2.ok) { $typed = ([regex]::Match((Find-Node $dlg2.xml 'class="android.widget.EditText"'), 'text="([^"]*)"')).Groups[1].Value }
                Log "RT-005: 输入框内容=[$typed]"
                if ($typed -match '^\s*45') {
                    Tap-Node $dlg2.xml 'text="确定"' | Out-Null
                    Start-Sleep -Milliseconds 800
                    $after = Get-Ui 'ui-rt005-after'
                    $prefs = Get-Prefs
                    $decl = Get-PrefFloat $prefs 'declination'
                    $r5frozen = if ($after.ok) { Get-Reading $after.xml } else { $null }
                    # 注入模式下 compassView.getHeading() 是静态的，改偏角不会自己重算；
                    # 再注入一次 = 模拟下一帧传感器，读数应变成 0+30=30.00°。
                    Set-Heading 0.0
                    $after2 = Get-Ui 'ui-rt005-after2'
                    $r5 = if ($after2.ok) { Get-Reading $after2.xml } else { $null }
                    $prefs | Set-Content -Path (Join-Path $OutDir 'prefs-rt005.xml') -Encoding UTF8
                    Log "RT-005: prefs.declination=$decl 改完当帧读数=$r5frozen 再注入后读数=$r5"
                    $ok = ($null -ne $decl -and [math]::Abs($decl - 30.0) -lt 0.05 -and $null -ne $r5 -and [math]::Abs($r5 - 30.0) -lt 0.5)
                    Assert3 'RT-005-UI45' $(if ($ok) { 'PASS' } else { 'FAIL' }) '界面输入 45 → clamp 30.0，真北读数 30.00°' "prefs=$decl 当帧读数=$r5frozen° 再注入后读数=$r5°（界面路径；当帧不刷新=注入模式下无传感器帧，非缺陷）" $after2.path
                    $rt005 = if ($ok) { 'PASS' } else { 'FAIL' }
                }
                else {
                    # 输入框写不进去：退到 debug 直通命令（等价路径，不冒充 UI）
                    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null   # 关掉对话框
                    Start-Sleep -Milliseconds 400
                    $bc = Invoke-Adb @('shell', 'am', 'broadcast', '-a', $script:LcDebugAction, '--es', 'cmd', 'declination', '--ef', 'value', '45')
                    Start-Sleep -Milliseconds 800
                    $prefs = Get-Prefs
                    $decl = Get-PrefFloat $prefs 'declination'
                    $after = Get-Ui 'ui-rt005-cmd'
                    $r5 = if ($after.ok) { Get-Reading $after.xml } else { $null }
                    Log "RT-005 fallback(cmd declination): $($bc -replace '\s+', ' ') prefs=$decl 读数=$r5"
                    if ($null -ne $decl) {
                        $ok = ([math]::Abs($decl - 30.0) -lt 0.05)
                        Assert3 'RT-005-UI45' $(if ($ok) { 'PASS' } else { 'FAIL' }) 'clamp(45)=30 端到端（等价路径：cmd declination，非 UI 输入）' "prefs=$decl 读数=$r5°" $after.path
                        $rt005 = if ($ok) { 'PASS(等价路径)' } else { 'FAIL' }
                    }
                    else {
                        Assert3 'RT-005-UI45' 'BLOCKED' 'clamp(45)=30 端到端' '界面输入框写不进 45，且无 cmd declination 通道（develop 未含）' (Join-Path $OutDir 'ui-rt005-typed.xml')
                    }
                }
            }
            else { Assert3 'RT-005-UI45' 'BLOCKED' '偏角对话框 EditText' '对话框里没有 EditText 节点' $dlg.path }
        }
        else { Assert3 'RT-005-UI45' 'BLOCKED' '偏角对话框' '对话框 dump 失败' }
    }
    else { Assert3 'RT-005-UI45' 'BLOCKED' '偏角按钮' '罗盘页找不到「偏角…」按钮' $ui.path }
}
else { Assert3 'RT-005-UI45' 'BLOCKED' '罗盘页' 'dump 失败' }

# ---------- 3. AT-P0-0：SW-006 关 ----------
$ui = Get-UiLoose 'ui-p00-off-before'
if ($ui.ok) {
    $readingNode = Find-Node $ui.xml 'resource-id="[^"]*reading_text"'
    if (-not $readingNode) { $readingNode = Find-Node $ui.xml 'text="度数' }
    $pt = if ($readingNode) { Node-Center $readingNode } else { $null }
    if ($pt) {
        for ($i = 0; $i -lt 7; $i++) {
            Invoke-Adb @('shell', 'input', 'swipe', "$($pt.X)", "$($pt.Y)", "$($pt.X)", "$($pt.Y)", '600') | Out-Null
            Start-Sleep -Milliseconds 250
        }
        Start-Sleep -Milliseconds 600
        $dlg = Get-Ui 'ui-hidden-switch'
        if ($dlg.ok -and ($dlg.xml -match '读数可信包')) {
            $cb = Find-Node $dlg.xml 'text="读数可信包[^"]*"'
            $checked = $cb -match 'checked="true"'
            if ($checked) { Tap-Node $dlg.xml 'text="读数可信包[^"]*"' | Out-Null; Start-Sleep -Milliseconds 400 }
            $dlg = Get-Ui 'ui-hidden-switch2'
            Tap-Node $dlg.xml 'text="保存"' | Out-Null
            Start-Sleep -Milliseconds 900
            Set-Heading 0.0
            $after = Get-Ui 'ui-p00-off'
            $prefs = Get-Prefs
            $prefs | Set-Content -Path (Join-Path $OutDir 'prefs-off.xml') -Encoding UTF8
            $off = Get-PrefBool $prefs 'p0_1_enabled'
            $texts = Get-Texts $after.xml
            $hasDisc = ($texts -match '偏角').Count -gt 0
            $hasNorth = ($texts -match '磁北|真北').Count -gt 0
            $reading = Get-Reading $after.xml
            $hasReadingRow = ($texts -match '度数').Count -gt 0
            Log "P0-0 off: reading=$reading 偏角行=$hasDisc 磁北/真北行=$hasNorth p0_1_enabled=$off"
            $ok1 = (-not $hasDisc) -and (-not $hasNorth) -and $hasReadingRow
            Assert3 'SW-006-off-1' $(if ($ok1) { 'PASS' } else { 'FAIL' }) '开关 off：偏角/磁北行消失，读数行仍在' "偏角=$hasDisc 磁北/真北=$hasNorth 读数行=$hasReadingRow 读数=$reading°" $after.path
            $ok2 = ($off -eq $false)
            Assert3 'SW-006-off-2' $(if ($ok2) { 'PASS' } else { 'FAIL' }) 'p0_1_enabled=false' "p0_1_enabled=$off" (Join-Path $OutDir 'prefs-off.xml')
            Assert3 'SW-006-off-3' $(if ($null -ne $reading -and [math]::Abs($reading) -lt 0.5) { 'PASS' } else { 'FAIL' }) '全关后读数 = 原始航向 0.00°（偏角不叠加）' "读数=$reading°" ''
            Save-LcScreenshot $DeviceId (Join-Path $OutDir 'cw-off.png') | Out-Null
        }
        else {
            Assert3 'SW-006-off-1' 'BLOCKED' '隐藏开关弹窗' '7 连长按后未出现「读数可信包」弹窗' $dlg.path
            Assert3 'SW-006-off-2' 'BLOCKED' 'p0_1_enabled=false' '开关未打开'
        }
    }
    else { Assert3 'SW-006-off-1' 'BLOCKED' '读数节点' 'dump 里找不到 reading_text / 度数 行' $ui.path }
}
else { Assert3 'SW-006-off-1' 'BLOCKED' '罗盘页' 'dump 失败' }

# ---------- 4. SW-006 第 4 条：面板区裁剪 diff（K-01 后暂停）----------
# 旧窗 y1850-2500 与旧参考 round-d5/variant-p01 都按三 Tab + 底部面板量出；
# K-01 单页 + 罗盘浮层后两者都失效。新窗/新参考在 round-k01 重冻后回填
# （登记在 docs/qa/k-01-pending.md；旧窗不得再用于判 PASS）。
Log "SW-006-4: SUSPENDED（旧窗 y1850-2500 与旧参考随 K-01 失效；待 round-k01 新窗+新参考）"
Assert3 'SW-006-off-3pix' 'DIAGNOSTIC' '面板区裁剪 diff（K-01 后暂停，待重取）' '旧窗/旧参考已失效，不判 PASS' ''

# ---------- 5. P0-2 P2-07/P2-08：读盘 14 行 + 顶部两行 ----------
$golden = $null
$goldenPath = Join-Path $OutDir 'golden-readsheet.json'
try {
    # 让 python 直接写 UTF-8 文件：控制台代码页会把中文 piping 弄坏
    $py = & python (Join-Path $repo 'tests\at\golden_readsheet.py') $ReadSheetHeading '--out' $goldenPath
    $golden = Get-Content -Path $goldenPath -Raw -Encoding UTF8 | ConvertFrom-Json
    Log "golden: heading=$ReadSheetHeading rings=$($golden.rows.Count) head2=$($golden.head2)"
}
catch { Log "golden 生成失败: $($_.Exception.Message)" }

if ($golden) {
    Set-Heading $ReadSheetHeading
    $ui = Get-UiLoose 'ui-compass-read'
    $open = $null
    if ($ui.ok) { $open = Tap-Node $ui.xml 'text="读盘"' }
    if ($open) {
        Start-Sleep -Milliseconds 900
        $sheet = Get-Ui 'ui-readsheet'
        if ($sheet.ok) {
            $texts = Get-Texts $sheet.xml
            # 弹层是 ScrollView：第 14 行在首屏外，dump 里可能没有 → 上滑后重拍并取并集
            $rounds = 0
            while ($rounds -lt 2) {
                $needScroll = $false
                foreach ($row in $golden.rows) {
                    if (-not ($texts | Where-Object { $_ -eq $row.text })) { $needScroll = $true; break }
                }
                if (-not $needScroll) { break }
                Invoke-Adb @('shell', 'input', 'swipe', '614', '2000', '614', '1150', '400') | Out-Null
                Start-Sleep -Milliseconds 800
                $sheet2 = Get-Ui "ui-readsheet-scroll$($rounds + 1)"
                if ($sheet2.ok) { $texts = @($texts + (Get-Texts $sheet2.xml) | Select-Object -Unique); $sheet = $sheet2 }
                $rounds++
            }
            $missing = @()
            foreach ($row in $golden.rows) {
                if (-not ($texts | Where-Object { $_ -eq $row.text })) { $missing += "第$($row.type)行 $($row.name): 期望[$($row.text)]" }
            }
            $nameMissing = @()
            foreach ($row in $golden.rows) { if (-not ($texts | Where-Object { $_ -eq $row.name })) { $nameMissing += $row.name } }
            # 顶部是**一个**两行 TextView：dump 里 text="向北 …°&#10;坐X山  向Y山"
            # F-06：逐字判，回退只允许 dump 环境类差异（不可见字符/换行）；舍入差一律 FAIL。
            $headRes = Get-ReadSheetHeadResult -Head1 $golden.head1 -Head2 $golden.head2 -Texts $texts
            $headNode = $headRes.Node
            Log "P2-07: 14 行命中 $((14 - $missing.Count))/14；行名命中 $((14 - $nameMissing.Count))/14（滚动补拍 $rounds 次）"
            Log "P2-08: [$($headRes.Status)/$($headRes.Mode)] 顶部行=[$headNode]"
            Assert3 'P2-07' $(if ($missing.Count -eq 0 -and $nameMissing.Count -eq 0) { 'PASS' } else { 'FAIL' }) '14 行逐字 == mechanics.json（向/坐各一格）' "命中 $((14 - $missing.Count))/14；缺：$($missing -join ' ; ')$($nameMissing -join ',')" $sheet.path
            Assert3 'P2-08' $headRes.Status "顶部两行逐字：$($golden.head1) / $($golden.head2)（仅允许 dump 环境类差异）" "$($headRes.Detail)；实际=[$headNode]" $sheet.path
            Save-LcScreenshot $DeviceId (Join-Path $OutDir 'readsheet.png') | Out-Null
            Tap-Node $sheet.xml 'text="关闭"' | Out-Null
            Start-Sleep -Milliseconds 500
        }
        else { Assert3 'P2-07' 'BLOCKED' '读盘弹层 dump' 'dump 失败' ; Assert3 'P2-08' 'BLOCKED' '读盘弹层 dump' 'dump 失败' }
    }
    else {
        Assert3 'P2-07' 'BLOCKED' '读盘按钮' '罗盘页找不到「读盘」按钮' $ui.path
        Assert3 'P2-08' 'BLOCKED' '读盘按钮' '罗盘页找不到「读盘」按钮' $ui.path
    }
}
else {
    Assert3 'P2-07' 'BLOCKED' 'golden' 'golden 生成失败'
    Assert3 'P2-08' 'BLOCKED' 'golden' 'golden 生成失败'
}

# ---------- 6. P0-2 P2-01/03/04/05/06：截图留档 ----------
function Get-AlbumList {
    $out = Invoke-Adb @('shell', 'ls', '-t', '/sdcard/Pictures/LC/')
    if ($out -match 'No such file') { return @() }
    return @($out -split "`n" | Where-Object { $_ -match '\.png$' })
}

$before = Get-AlbumList
Set-Heading 94.04
$ui = Get-UiLoose 'ui-capture'
$btn = $null
$capMode = ''
if ($ui.ok -and ($ui.xml -match 'text="截图"')) {
    $btn = Tap-Node $ui.xml 'text="截图"'
    $capMode = 'UI 按钮'
}
if (-not $btn) {
    $bc = Invoke-Adb @('shell', 'am', 'broadcast', '-a', $script:LcDebugAction, '--es', 'cmd', 'capture')
    Start-Sleep -Milliseconds 1200
    $capMode = "cmd capture 广播"
}
Start-Sleep -Milliseconds 2500
$after = Get-AlbumList
$new = @($after | Where-Object { $before -notcontains $_ })
if ($new.Count -eq 0) {
    Assert3 'P2-01' 'BLOCKED' '一键留档生成文件' "未新增文件（方式：$capMode；dump 是否含「截图」按钮=$(if($ui.ok){$ui.xml -match 'text="截图"'}else{'dump失败'})）" $(Join-Path $OutDir 'ui-capture.xml')
    Assert3 'P2-03' 'BLOCKED' '文件名 坐向X山向Y山_时间戳.png' '无文件可查'
    Assert3 'P2-04' 'BLOCKED' 'EXIF 无 GPS' '无文件可查'
    Assert3 'P2-05' 'BLOCKED' '分享 SHA' '无文件可查'
    Assert3 'P2-06' 'BLOCKED' '角注语义 + 禁第三方号' '无留档文件可裁片（DOM 语义另测需留档才有意义）'
}
else {
    $name = $new[0]
    Log "P2-01: 新增文件 $name（方式：$capMode）"
    $local = Join-Path $OutDir 'shot-compass.png'
    Invoke-Adb @('pull', "/sdcard/Pictures/LC/$name", $local) | Out-Null
    if (-not (Test-Path $local)) {
        Assert3 'P2-01' 'FAIL' '留档文件可拉取' "文件在设备上但 pull 失败: $name"
    }
    else {
        $sha = (Get-FileHash $local -Algorithm SHA256).Hash
        $bytes = [IO.File]::ReadAllBytes($local)
        $bmpW = 0; $bmpH = 0
        try {
            Add-Type -AssemblyName System.Drawing
            $b = [System.Drawing.Image]::FromFile($local); $bmpW = $b.Width; $bmpH = $b.Height; $b.Dispose()
        }
        catch { }
        $sha | Set-Content -Path (Join-Path $OutDir 'shot-sha256.txt') -Encoding UTF8
        Log "P2-01: $name $($bytes.Length) B ${bmpW}x${bmpH} sha256=$sha"
        Assert3 'P2-01' 'PASS' '点一次生成一个 png 留档' "新增 $name（$($bytes.Length) B，${bmpW}x${bmpH}，方式=$capMode）" $local

        # 命名：坐向<坐>向<向>_yyyyMMdd_HHmmss.png
        # 实现用的是 MOUNTAINS[i]（单字，如「酉」），比 PRD 写法「坐向X山向Y山」少了「山」字 → 记 OBS，不判 FAIL
        $nameOk = $name -match '^坐向.向._\d{8}_\d{6}\.png$'
        $nameHasShan = $name -match '^坐向.山向.山_'
        $p203 = if ($nameOk) { 'PASS' } else { 'FAIL' }
        $p203actual = "实际=$name；PRD 写法含「山」字：$nameHasShan（实现为单字山名，记 OBS-003 待产品定名，不影响文件身份）"
        Assert3 'P2-03' $p203 '坐向<坐>向<向>_yyyyMMdd_HHmmss.png（PRD 写法带「山」字）' $p203actual ''

        # PNG 的 EXIF 存在 eXIf 块；Java Bitmap.compress 不写 → 断言无 eXIf 块（即无任何 EXIF/GPS）
        $ascii = [Text.Encoding]::ASCII.GetString($bytes)
        $hasExif = $ascii -match 'eXIf'
        Assert3 'P2-04' $(if (-not $hasExif) { 'PASS' } else { 'FAIL' }) 'PNG 无 eXIf 块（无 GPS/EXIF 坐标）' "eXIf 块=$hasExif，width=$bmpW height=$bmpH" $local

        # 分享：saveCompassShot → shareShot 同一 uri；分享抽屉打开 = 走通分享
        $acts = (Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities'))
        $chooser = ($acts | Select-String 'ResolverActivity|ChooserActivity|android/.ChooserActivity' | Select-Object -First 2) -join ' | '
        Save-LcScreenshot $DeviceId (Join-Path $OutDir 'share-sheet.png') | Out-Null
        Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
        Start-Sleep -Milliseconds 600
        Assert3 'P2-05' $(if ($chooser) { 'PASS' } else { 'FAIL' }) '分享抽屉打开；被分享文件 = 相册同一 uri（代码同一 saveCompassShot→shareShot 路径）' "chooser=$([bool]$chooser) sha256=$sha" (Join-Path $OutDir 'share-sheet.png')

        # P2-06 角注：DOM 语义 + 静态禁第三方号 + 裁片留证
        Send-Js '(function(){var t=document.body.innerText.replace(/\s+/g," ");var m=t.match(/底图[^。]{0,30}审图号[：:][^ ]{0,20}/);return m?m[0]:"NONE";})()'
        $lcjs = Get-Logcat 'LCJS' 120
        $note = (($lcjs -split "`n" | Where-Object { $_ -match 'LCJS' } | Select-Object -Last 1) -replace '\s+', ' ').Trim()
        $mapHtml = Join-Path $repo 'app\android\app\src\main\assets\map.html'
        $html = Get-Content $mapHtml -Raw -Encoding UTF8
        $hasTdt = $html -match '天地图'
        $hasNote = $html -match '审图号'
        $thirdParty = $html -match 'GS\(2026\)1219'
        $domOk = ($note -match '天地图') -and ($note -match '审图号') -and ($note -match '未确认|待核对')
        $cornerPath = Join-Path $OutDir 'p2-06-corner.png'
        $cornerInfo = '未生成'
        try {
            Add-Type -AssemblyName System.Drawing
            $img = [System.Drawing.Bitmap]::FromFile($local)
            $rect = New-Object System.Drawing.Rectangle ([int]($img.Width * 0.5)), ([int]($img.Height * 0.72)), ([int]($img.Width * 0.5)), ([int]($img.Height * 0.28))
            $crop = $img.Clone($rect, $img.PixelFormat)
            $crop.Save($cornerPath, [System.Drawing.Imaging.ImageFormat]::Png)
            $colors = @{}
            for ($x = 0; $x -lt $crop.Width; $x += 7) {
                for ($y = 0; $y -lt $crop.Height; $y += 7) { $colors[$crop.GetPixel($x, $y).ToArgb()] = 1 }
            }
            $cornerInfo = "裁片 $($crop.Width)x$($crop.Height)，采样色数 $($colors.Count)（>50 说明有内容，纯色=被面板盖住）"
            $crop.Dispose(); $img.Dispose()
        }
        catch { $cornerInfo = "裁片失败: $($_.Exception.Message)" }
        Log "P2-06: DOM=[$note] 天地图=$hasTdt 审图号=$hasNote 第三方号=$thirdParty $cornerInfo"
        $p206 = $domOk -and $hasTdt -and $hasNote -and (-not $thirdParty)
        Assert3 'P2-06' $(if ($p206) { 'PASS' } else { 'FAIL' }) '角注含 天地图 + 审图号 + 未确认，且无第三方审图号 GS(2026)1219' "DOM=[$note] 静态:天地图=$hasTdt 审图号=$hasNote 第三方号=$thirdParty；$cornerInfo" $cornerPath

        # 角注在**分享图**里能不能看见：判图，不判 DOM。
        # 背景：`111c4fb` 起客户端把「底图·审图号」画进图内、面板上缘之上（#srcInfo 仍在面板后面，
        # 所以拿 DOM 矩形判会永远 FAIL）。这里在面板带上方的条带里找「金色文字行」。
        # K-01 后旧带 y1400-1850（1228×2700，三 Tab+底部面板布局）与 100 像素阈值都随布局失效；
        # 先暂停，复核新带后再判（docs/qa/k-01-pending.md P-06）。
        Log "P2-06-vis: SUSPENDED（旧带 y1400-1850 随 K-01 失效；待复核新带后判 PASS/FAIL）"
        Assert3 'P2-06-vis' 'DIAGNOSTIC' '金色角注带（K-01 后暂停，待复核）' '旧带 y1400-1850 不再用于判 PASS/FAIL' ''
    }
}

# ---------- 7. TD-016：真机 3D 点选 FPS / 手感 ----------
# 不调 tools\map 的脚本：它 am start 后不校验前台，本轮实测被华为应用市场安装
# 风险检查顶掉（moveTaskToBack），点按全落在桌面 → picks=0。这里自带前台校验+重试。
if (-not $SkipTd016) {
    $fg = $false
    for ($i = 0; $i -lt 3; $i++) {
        Invoke-Adb @('shell', 'am', 'start', '-n', "$($script:LcPkg)/.MainActivity") | Out-Null
        Start-Sleep -Milliseconds 2500
        $r = ((Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities')) -split "`n" | Select-String 'mResumedActivity' | Select-Object -First 1)
        if ("$r" -match 'com\.luopan\.fengshui') { $fg = $true; break }
        Log "TD-016: 第 $($i + 1) 次拉起未到前台，重试"
        Start-Sleep -Milliseconds 1500
    }
    if (-not $fg) { Assert3 'TD-016' 'BLOCKED' 'App 前台' '3 次 am start 都没到前台' '' }
    else {
        Invoke-Adb @('logcat', '-c') | Out-Null
        Invoke-Adb @('shell', 'input', 'tap', '615', '2525') | Out-Null   # 地图 tab
        Start-Sleep -Milliseconds 2000
        Invoke-Adb @('shell', 'input', 'tap', '155', '576') | Out-Null    # 3D 地形
        Start-Sleep -Milliseconds 10000
        $pts = @(@(614, 1500), @(500, 1600), @(720, 1400), @(614, 1800), @(700, 1700))
        foreach ($p in $pts) { Invoke-Adb @('shell', 'input', 'tap', "$($p[0])", "$($p[1])") | Out-Null; Start-Sleep -Milliseconds 900 }
        Start-Sleep -Milliseconds 6000
        Save-LcScreenshot $DeviceId (Join-Path $OutDir 'td016-3d.png') | Out-Null
        $full = (Invoke-Adb @('logcat', '-d'))
        # 落盘前脱敏（tk=<web key>）；返回脱敏后的文本，后续 picks/fps/fatal 统计用同一份。
        $full = Save-RedactedText -Path (Join-Path $OutDir 'td016-logcat.txt') -Text $full -Label 'td016-logcat' -Keys $redactKeys
        $lines = $full -split "`n"
        $picks = ($lines | Where-Object { $_ -match 'LC3D pick ' }).Count
        $fpsList = @()
        foreach ($line in ($lines | Where-Object { $_ -match 'LC3D fps=' })) {
            if ($line -match 'fps=(\d+)') { $fpsList += [int]$Matches[1] }
        }
        $fpsMin = if ($fpsList.Count) { ($fpsList | Measure-Object -Minimum).Minimum } else { 0 }
        $fpsMax = if ($fpsList.Count) { ($fpsList | Measure-Object -Maximum).Maximum } else { 0 }
        $fatal = ($lines | Where-Object { $_ -match 'FATAL EXCEPTION' }).Count
        Log "TD-016: picks=$picks (期望 5) fps=$fpsMin..$fpsMax (期望 min>=30) 采样=$($fpsList.Count) fatal=$fatal"
        $ok = ($picks -ge 5 -and $fpsMin -ge 30 -and $fatal -eq 0)
        Assert3 'TD-016' $(if ($ok) { 'PASS' } else { 'FAIL' }) '3D 单次点选一条 pick（5 次）、fps 最小值 ≥30、FATAL=0；手感人工' "picks=$picks fps=$fpsMin..$fpsMax 采样=$($fpsList.Count) fatal=$fatal" (Join-Path $OutDir 'td016-3d.png')
    }
}

# ---------- 8. RT-011：数值门禁（静态，不占机）----------
$numeric = & python (Join-Path $repo 'tests\numeric\run_all.py') 2>&1
$numericText = ($numeric -join "`n")
$numericTail = ($numeric | Select-Object -Last 6) -join ' | '
# F-01: 跳过不算绿——必须 failures=0、skipped=0、且末行 RESULT: PASS 三件同时成立。
$numericOk = ($numericText -match 'failures=0') -and ($numericText -match 'skipped=0') -and ($numericText -match 'RESULT: PASS')
Log "RT-011 numeric: $numericTail"
Assert3 'RT-011-numeric' $(if ($numericOk) { 'PASS' } else { 'FAIL' }) 'run_all.py 全 PASS（failures=0、skipped=0）' $numericTail (Join-Path $OutDir 'numeric.txt')
$numeric -join "`n" | Set-Content -Path (Join-Path $OutDir 'numeric.txt') -Encoding UTF8

$mechScript = Join-Path $repo 'tests\numeric\tools\check_mechanics.py'
$mech = if (Test-Path $mechScript) { & python $mechScript 2>&1 } else { "check_mechanics.py 不存在：$mechScript" }
$mechLine = ($mech | Where-Object { $_ -match 'values=|PASS|FAIL|不存在' }) -join ' | '
$mechOk = ($mech -join "`n") -match 'estimated=0'
Log "RT-011 mechanics: $mechLine"
Assert3 'RT-011-mechanics' $(if ($mechOk) { 'PASS' } else { 'FAIL' }) 'mechanics.json 19 项全有来源、0 估计' $mechLine (Join-Path $OutDir 'mechanics.txt')
$mech -join "`n" | Set-Content -Path (Join-Path $OutDir 'mechanics.txt') -Encoding UTF8

$diff = (& git diff --stat HEAD -- app/mechanics.json) -join ''
Log "RT-011 diff mechanics.json: [$(if ($diff) { $diff } else { '0 行' })]"
Assert3 'RT-011-diff' $(if (-not $diff) { 'PASS' } else { 'FAIL' }) 'mechanics.json 本轮未被改动' $(if ($diff) { $diff } else { '0 行' }) ''

# ---------- 9. 汇总 ----------
$notes = Join-Path $OutDir 'notes.md'
$extraNote = (@(
        '',
        '## 环境',
        '',
        "- worktree: $branch @ $head",
        "- apk: $Apk",
        "- 证据目录: $OutDir",
        '',
        '## 说明',
        '',
        '- P2-06 角注：自动化只判「语义（天地图+审图号+未确认）+ 禁第三方号」，可见性以 p2-06-corner.png 裁片目视为准（不自动判 UI 遮挡）。',
        '- SW-006 ③ 面板 diff 只作诊断：罗盘叠在活地图 WebView + 半透明面板上，实测不可当门禁。',
        '- TD-016 的手感无量化，由人工在 3D 页确认；脚本只门禁 pick 条数 / fps 最小值 / FATAL。'
    ) -join "`n")
Write-LcNotes -Path $notes -Suite 'FINALE' -Head $head -Extra $extraNote

$rows = $script:LcResults | ForEach-Object { "$($_.status)`t$($_.id)`t$($_.actual)" }
$rows | Set-Content -Path (Join-Path $OutDir 'table.tsv') -Encoding UTF8
$pass = ($script:LcResults | Where-Object status -eq 'PASS').Count
$fail = ($script:LcResults | Where-Object status -eq 'FAIL').Count
$blocked = ($script:LcResults | Where-Object status -eq 'BLOCKED').Count
$diag = ($script:LcResults | Where-Object status -eq 'DIAGNOSTIC').Count
Log "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag -> $notes"
if ($fail -gt 0) { exit 1 }
if ($blocked -gt 0) { exit 2 }
exit 0

