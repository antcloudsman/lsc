<#
  LC UI 驱动库（E2E 用）：dump 解析 + 按 text/resource-id 动态点按 + 等待文本。
  只做「看与点」，不改被测应用。所有坐标都来自当次 dump，不写死。
#>
$script:Dev = 'L2E0222531014833'
$script:Pkg = 'com.luopan.fengshui'
$script:DumpPath = '/sdcard/lc-e2e.xml'

function Init-Ui([string]$Device, [string]$Package) {
    if ($Device) { $script:Dev = $Device }
    if ($Package) { $script:Pkg = $Package }
}

function Invoke-Adb([string[]]$parts) {
    return @(& adb -s $script:Dev @parts 2>&1 | ForEach-Object { "$_" })
}

function Freeze-Heading([int]$value = 0) {
    # 已知坑：罗盘传感器每帧重绘 -> uiautomator 报 could not get idle state；先冻结再 dump
    Invoke-Adb @('shell', "am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value $value") | Out-Null
    Start-Sleep -Milliseconds 300
}

function Get-UiDump {
    Start-Sleep -Milliseconds 250   # 节流：连发 dump 会让 UiAutomation 注册冲突（uiautomator 自身 FATAL）
    Invoke-Adb @('shell', "rm -f $script:DumpPath") | Out-Null
    $r = (Invoke-Adb @('shell', "uiautomator dump $script:DumpPath")) -join ' '
    if ($r -notmatch 'dumped to') {
        Freeze-Heading 0
        Invoke-Adb @('shell', 'pkill -f uiautomator') | Out-Null
        Start-Sleep -Milliseconds 500
        Invoke-Adb @('shell', "rm -f $script:DumpPath") | Out-Null
        $r = (Invoke-Adb @('shell', "uiautomator dump $script:DumpPath")) -join ' '
        if ($r -notmatch 'dumped to') {
            Start-Sleep -Milliseconds 800
            Invoke-Adb @('shell', "rm -f $script:DumpPath") | Out-Null
            $r = (Invoke-Adb @('shell', "uiautomator dump $script:DumpPath")) -join ' '
            if ($r -notmatch 'dumped to') { return $null }
        }
    }
    $xml = (Invoke-Adb @('shell', "cat $script:DumpPath")) -join "`n"
    if ($xml -notmatch '<hierarchy') { return $null }
    try { return [xml]$xml } catch { return $null }
}

function Find-Node($doc, [string]$text, [string]$id) {
    if (-not $doc) { return $null }
    foreach ($n in $doc.SelectNodes('//node')) {
        $t = "$($n.text)"; $d = "$($n.'content-desc')"; $rid = "$($n.'resource-id')"
        $hit = $false
        if ($id) { $hit = ($rid -eq "$($script:Pkg):id/$id") }
        elseif ($text) { $hit = ($t -eq $text) -or ($t -like "*$text*") -or ($d -eq $text) }
        if ($hit) {
            if ($n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                $l=[int]$Matches[1]; $tp=[int]$Matches[2]; $rr=[int]$Matches[3]; $b=[int]$Matches[4]
                if ($rr -gt $l -and $b -gt $tp) {
                    return [pscustomobject]@{ X=[int](($l+$rr)/2); Y=[int](($tp+$b)/2);
                        L=$l; T=$tp; R=$rr; B=$b; Text=$t; Id=$rid }
                }
            }
        }
    }
    return $null
}

function Tap-Node($doc, [string]$text, [string]$id, [int]$wait = 900) {
    $n = Find-Node $doc $text $id
    if (-not $n) { return $false }
    Invoke-Adb @('shell', "input tap $($n.X) $($n.Y)") | Out-Null
    Start-Sleep -Milliseconds $wait
    return $true
}

function Wait-Text([string]$text, [int]$timeoutSec = 20) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $doc = Get-UiDump
        if (Find-Node $doc $text '') { return $doc }
        Start-Sleep -Milliseconds 700
    }
    return $null
}

function Get-Focus {
    $raw = (Invoke-Adb @('shell', 'dumpsys window')) -join "`n"
    $m = [regex]::Match($raw, 'mCurrentFocus=[^\n]*?([A-Za-z0-9._]+\/[A-Za-z0-9._$]+)')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}

function Input-Text([string]$s) {
    # adb input text：只支持 ASCII；空格用 %s
    $v = $s -replace ' ', '%s'
    Invoke-Adb @('shell', "input text '$v'") | Out-Null
    Start-Sleep -Milliseconds 400
}


function Tap-NodeRetry([string]$text, [string]$id, [int]$tries = 3, [int]$wait = 1200) {
    for ($i = 1; $i -le $tries; $i++) {
        $d = Get-UiDump
        if ($d -and (Tap-Node $d $text $id $wait)) { return $true }
        Start-Sleep -Milliseconds 600
    }
    return $false
}


function Wait-AppReady([int]$timeoutSec = 75) {
    # pm clear 后是冷启动：WebView/地图未就绪时 dump 会 could not get idle state。
    # 判据：能 dump 出主界面且找到左上抽屉按钮（lc_menu_button）。
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        Freeze-Heading 0
        $d = Get-UiDump
        if ($d -and (Find-Node $d '' 'lc_menu_button')) { return $d }
        Start-Sleep -Milliseconds 1200
    }
    return $null
}


function Dismiss-SystemDialogs {
    # pm clear 后首启会出现「校准罗盘」首用引导（跳过/开始校准），以及可能的授权框；
    # 这些属于首次启动副作用，不点掉会挡住全链路。只点「跳过」类按钮，不点「开始校准」。
    $d = Get-UiDump
    if (-not $d) { return $false }
    foreach ($label in @('跳过', '以后再说', '暂不', '允许')) {
        if (Tap-Node $d $label '' 800) { return $true }
    }
    return $false
}
