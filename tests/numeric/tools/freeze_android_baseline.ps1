<#
.SYNOPSIS
  冻结 Android 真机基线到指定目录（三 Tab + 盘面入口 + ui dump + logcat + SHA256）。

.DESCRIPTION
  用于 D5 之后基线过期时的重冻。只读设备，不改 App 代码；输出目录只增不改。
  依赖：adb（设备已连接）、一个从 develop 构建的 APK。

.EXAMPLE
  pwsh -File tests/numeric/tools/freeze_android_baseline.ps1 `
    -Apk E:\APPDetector\wt\lc-baseline-r3\app\android\app\build\outputs\apk\debug\app-debug.apk `
    -OutDir E:\APPDetector\output\docs\qa\baseline-android\round-d5
#>
param(
    [string]$Device = "L2E0222531014833",
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$adb = "adb"
function Adb { & $adb -s $Device @args }
function Shot([string]$name) {
    $path = Join-Path $OutDir $name
    & $adb -s $Device exec-out screencap -p > $path
    if (-not (Test-Path $path) -or (Get-Item $path).Length -lt 10000) { throw "截图失败: $name" }
}
function DumpUi([string]$file) {
    $remote = "/sdcard/lc_ui.xml"
    $null = & $adb -s $Device shell uiautomator dump $remote 2>&1
    & $adb -s $Device pull $remote (Join-Path $OutDir $file) | Out-Null
    return (Get-Content -Raw (Join-Path $OutDir $file))
}
function FindNodeCenter([string]$xml, [string]$pattern) {
    foreach ($m in [regex]::Matches($xml, "<node\b[^>]*>")) {
        $tag = $m.Value
        $tm = [regex]::Match($tag, 'text="([^"]*)"')
        if (-not $tm.Success) { continue }
        $text = $tm.Groups[1].Value
        if ($text -notmatch $pattern) { continue }
        $bm = [regex]::Match($tag, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $bm.Success) { continue }
        $x = ([int]$bm.Groups[1].Value + [int]$bm.Groups[3].Value) / 2
        $y = ([int]$bm.Groups[2].Value + [int]$bm.Groups[4].Value) / 2
        return @{ x = [int]$x; y = [int]$y }
    }
    return $null
}

New-Item -ItemType Directory -Force $OutDir | Out-Null
$hashFile = Join-Path $OutDir "SHA256SUMS.txt"
Remove-Item $hashFile -ErrorAction SilentlyContinue

if (-not $SkipInstall) {
    $install = (& $adb -s $Device install -r $Apk 2>&1 | Select-Object -Last 1)
    if ($install -notmatch "Success") { throw "install 失败: $install" }
}
& $adb -s $Device shell am force-stop com.luopan.fengshui
& $adb -s $Device logcat -c
$start = & $adb -s $Device shell am start -W -n com.luopan.fengshui/.MainActivity 2>&1
$total = ([regex]::Match(($start -join "`n"), "TotalTime:\s*(\d+)")).Groups[1].Value
Start-Sleep -Seconds 4
$appPid = (& $adb -s $Device shell pidof com.luopan.fengshui).Trim()
if (-not $appPid) { throw "App 未存活" }

# 1) 罗盘页
Shot "tab-compass.png"
$xml = DumpUi "ui-compass.xml"
$node = FindNodeCenter $xml "盘面"
if ($node) {
    & $adb -s $Device shell input tap $node.x $node.y
    Start-Sleep -Milliseconds 1200
    Shot "disc-dial-list.png"
    $null = DumpUi "ui-dial-list.xml"
    & $adb -s $Device shell input keyevent 4
    Start-Sleep -Milliseconds 800
} else {
    Write-Warning "未找到「盘面」入口，跳过盘面列表截图"
}

# 2) 地图 / 知识
$node = FindNodeCenter $xml "地图"
if ($node) { & $adb -s $Device shell input tap $node.x $node.y; Start-Sleep -Milliseconds 1200; Shot "tab-map.png" }
$node = FindNodeCenter $xml "知识"
if ($node) {
    & $adb -s $Device shell input tap $node.x $node.y
    Start-Sleep -Milliseconds 1000
    Shot "tab-knowledge.png"
    $null = DumpUi "ui-knowledge.xml"
}

# 3) 日志与状态
& $adb -s $Device logcat -d | Set-Content -Encoding UTF8 (Join-Path $OutDir "logcat.txt")
$log = Get-Content -Raw (Join-Path $OutDir "logcat.txt")
$fatal = ([regex]::Matches($log, "FATAL EXCEPTION")).Count
$anr = ([regex]::Matches($log, "ANR in com.luopan")).Count
$wm = (& $adb -s $Device shell wm size) -join " "
$model = (& $adb -s $Device shell getprop ro.product.model).Trim()
$release = (& $adb -s $Device shell getprop ro.build.version.release).Trim()
$fingerprint = (& $adb -s $Device shell getprop ro.build.fingerprint).Trim()
$pkg = (& $adb -s $Device shell dumpsys package com.luopan.fengshui 2>&1) -join "`n"
$verName = ([regex]::Match($pkg, "versionName=(\S+)")).Groups[1].Value
$verCode = ([regex]::Match($pkg, "versionCode=(\d+)")).Groups[1].Value
$lastUpdate = ([regex]::Match($pkg, "lastUpdateTime=([^\r\n]+)")).Groups[1].Value.Trim()
$apkItem = Get-Item $Apk
$head = (& git -C (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) rev-parse --short HEAD 2>$null)
if (-not $head) { $head = "unknown" }

# 4) SHA256 + manifest
$rows = foreach ($f in (Get-ChildItem $OutDir -File | Where-Object { $_.Name -ne "manifest.md" -and $_.Name -ne "SHA256SUMS.txt" } | Sort-Object Name)) {
    $h = (Get-FileHash $f.FullName -Algorithm SHA256).Hash
    "{0}  {1}  {2}" -f $h, $f.Length, $f.Name
}
$rows | Set-Content -Encoding UTF8 $hashFile
$apkHash = (Get-FileHash $Apk -Algorithm SHA256).Hash

$manifest = @"
# Android 基线（真机 $Device）· D5 重冻

- 冻结时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')（UTC+8），LC测试
- 设备：$model / Android $release / ``$fingerprint``
- App：versionName $verName ($verCode)，lastUpdateTime $lastUpdate，pid $appPid
- 构建：``develop`` HEAD ``$head``；APK ``$($apkItem.Name)`` $($apkItem.Length) B，SHA256 ``$apkHash``
- 冷启动：am start -W TotalTime = ${total} ms
- 屏幕：$wm
- logcat：FATAL EXCEPTION **$fatal** / ANR **$anr**（``logcat.txt``）
- 说明：D5 起底部「5 个盘面按钮」换成「盘面（4 组 · 常用 5）」入口，本基线含 ``disc-dial-list.png``。

## 冻结物（SHA256 见 ``SHA256SUMS.txt``）

$((Get-ChildItem $OutDir -File | Where-Object { $_.Name -notin @('manifest.md','SHA256SUMS.txt') } | Sort-Object Name | ForEach-Object { "- ``$($_.Name)``：$($_.Length) B" }) -join "`n")

## 限制

1. ``ui-*.xml`` 只含原生视图；CompassView / WebView 内容不在 dump 里。
2. 传感器持续 invalidate，dump 在罗盘/地图页可能 idle 超时；本脚本在知识页采稳定 dump。
3. 盘面列表页用「盘面」按钮文本定位，坐标由 uiautomator 现算，不写死像素。
"@
$manifest | Set-Content -Encoding UTF8 (Join-Path $OutDir "manifest.md")

Write-Host "FROZEN: $OutDir"
Write-Host "artifacts: $((Get-ChildItem $OutDir -File).Count) files, fatal=$fatal anr=$anr cold=${total}ms pid=$appPid"
if ($fatal -gt 0 -or $anr -gt 0 -or -not $appPid) { exit 1 }
exit 0

