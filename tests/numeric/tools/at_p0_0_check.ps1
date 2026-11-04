<#
.SYNOPSIS
  AT-P0-0「P0-1 总开关全关」核对（① 行存在性 + ② prefs 值；③ 像素 diff 仅诊断）。

.DESCRIPTION
  总监裁决后的新口径：
    ① 偏角/磁北行存在性（ui dump 行级）；
    ② `p0_1_enabled` 实际值（run-as 读 prefs）；
    ③ 面板区裁剪 diff —— 实测**不可做门禁**：罗盘叠在活的 WebView 地图上、面板半透明，
       本轮用基线 on/off 图实测面板区 diff 仍有 **68.48%**（886,847 px 全图）。
       因此本脚本只把它当诊断输出，除非先冻结地图（另立协议）。

  用法：
    pwsh -File tests/numeric/tools/at_p0_0_check.ps1 `
      -OutDir E:\APPDetector\output\checks\at-p0-0 `
      -ReferenceDir docs/qa/baseline-android/round-d5/variant-p01

  退出码：0 = ① ② 通过；1 = 判据失败；2 = BLOCKED（dump 取不到，无法判行存在性）。
#>
param(
    [string]$Device = "L2E0222531014833",
    [Parameter(Mandatory = $true)][string]$OutDir,
    [string]$ReferenceDir = "",
    [string]$Apk = "",
    [float]$Heading = 0.0
)

$ErrorActionPreference = "Stop"
$adb = "adb"
New-Item -ItemType Directory -Force $OutDir | Out-Null
$result = Join-Path $OutDir "result.txt"
"AT-P0-0 run $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Set-Content -Encoding UTF8 $result

# 0) 装包（可选）→ **force-stop 再启动**（install -r + am start 会复用旧进程/旧页面）
if ($Apk) {
    $install = (& $adb -s $Device install -r $Apk 2>&1 | Select-Object -Last 1)
    Add-Content -Encoding UTF8 $result ("install: " + (($install -replace '\s+', ' ').Trim()))
    if ($install -notmatch "Success") {
        Add-Content -Encoding UTF8 $result "BLOCKED: install 失败"
        Write-Host "AT-P0-0: BLOCKED — install 失败"
        exit 2
    }
}
& $adb -s $Device shell am force-stop com.luopan.fengshui
$pkg = ((& $adb -s $Device shell dumpsys package com.luopan.fengshui 2>&1) -join "`n")
$upd = ([regex]::Match($pkg, 'lastUpdateTime=([^\r\n]+)')).Groups[1].Value.Trim()
Add-Content -Encoding UTF8 $result "package: lastUpdateTime=$upd"
$null = & $adb -s $Device shell am start -n com.luopan.fengshui/.MainActivity 2>&1
Start-Sleep -Seconds 2
$resumed = ((& $adb -s $Device shell dumpsys activity activities 2>&1) |
    Select-String 'mResumedActivity' | Select-Object -First 1)
Add-Content -Encoding UTF8 $result ("foreground: " + (($resumed -replace '\s+', ' ').Trim()))
$blockReason = ""
if ($resumed -notmatch 'com\.luopan\.fengshui') {
    $blockReason = "App 不在前台（registerDebugBridge 在 onResume，后台收不到广播）"
}
$recv = ((& $adb -s $Device shell dumpsys activity broadcasts 2>&1) |
    Select-String 'com\.luopan\.fengshui\.DEBUG' | Select-Object -First 3) -join " | "
Add-Content -Encoding UTF8 $result ("receiver: " + $(if ($recv) { $recv } else { "dumpsys 未见 DEBUG receiver（可能未注册）" }))
& $adb -s $Device logcat -c
$bc = (& $adb -s $Device shell am broadcast -a com.luopan.fengshui.DEBUG --ef heading $Heading 2>&1) -join "`n"
Add-Content -Encoding UTF8 $result ("broadcast: " + (($bc -replace '\s+', ' ').Trim()))
Start-Sleep -Milliseconds 800
& $adb -s $Device logcat -d | Set-Content -Encoding UTF8 (Join-Path $OutDir "logcat-heading.txt")
$dbg = ((Get-Content (Join-Path $OutDir "logcat-heading.txt") -ErrorAction SilentlyContinue) |
    Select-String 'LCDEBUG' | Select-Object -Last 5) -join " | "
Add-Content -Encoding UTF8 $result ("LCDEBUG: " + $(if ($dbg) { $dbg } else { "none（广播未被接收或分支无日志）" }))
if (-not $dbg -and -not $blockReason) {
    $blockReason = "heading 广播后无 LCDEBUG（receiver 未收到/分支未处理）"
}

# 1) prefs 值
$prefs = (& $adb -s $Device shell run-as com.luopan.fengshui cat shared_prefs/lc_prefs.xml 2>&1) -join "`n"
$prefs | Set-Content -Encoding UTF8 (Join-Path $OutDir "prefs.xml")
$m = [regex]::Match($prefs, 'name="p0_1_enabled"\s+value="(true|false)"')
if ($m.Success) { $enabled = $m.Groups[1].Value -eq "true"; $prefsNote = "persisted=$($m.Groups[1].Value)" }
else { $enabled = $true; $prefsNote = "missing -> 代码默认 true" }
Add-Content -Encoding UTF8 $result "p0_1_enabled: $enabled ($prefsNote)"

# 2) 截图
$shot = Join-Path $OutDir "compass.png"
& $adb -s $Device exec-out screencap -p > $shot
$shotBytes = (Get-Item $shot).Length

# 3) ui dump（清远端旧文件，最多 3 次；失败 = BLOCKED）
$dumpOk = $false
for ($i = 1; $i -le 3; $i++) {
    & $adb -s $Device shell rm -f /sdcard/atp00.xml | Out-Null
    $o = & $adb -s $Device shell uiautomator dump /sdcard/atp00.xml 2>&1
    if (($o -join "`n") -match "dumped to") {
        & $adb -s $Device pull /sdcard/atp00.xml (Join-Path $OutDir "ui-compass.xml") | Out-Null
        $dumpOk = $true
        break
    }
    Start-Sleep -Milliseconds 500
}
$texts = @()
if ($dumpOk) {
    $xml = Get-Content -Raw (Join-Path $OutDir "ui-compass.xml")
    $texts = [regex]::Matches($xml, 'text="([^"]*)"') | ForEach-Object { $_.Groups[1].Value }
}
# 健全性：dump 必须在罗盘页（有「度数」行）且航向已冻结到注入值；
# 否则「没有偏角行」可能是 dump 打到了别的页面/弹窗，不能当成「开关已关」。
$hasReading = ($texts -match "度数").Count -gt 0
$reading = ($texts | Where-Object { $_ -match "度数" } | Select-Object -First 1)
if ($dumpOk -and -not $hasReading) {
    $dumpOk = $false
    Add-Content -Encoding UTF8 $result "dump 无「度数」行（可能不在罗盘页/被弹窗遮挡）→ BLOCKED"
}
$hasDecl = ($texts -match "偏角").Count -gt 0
$hasTrueNorth = ($texts -match "磁北").Count -gt 0 -or ($texts -match "真北").Count -gt 0

# 4) 判定
$failures = @()
if ($dumpOk) {
    Add-Content -Encoding UTF8 $result "ui rows: 偏角=$hasDecl 磁北/真北=$hasTrueNorth 读数=[$reading] (dump OK)"
    if ($reading -match '([\d.]+)') {
        $rv = [double]$Matches[1]
        if ([math]::Abs($rv - $Heading) -gt 0.5) {
            Add-Content -Encoding UTF8 $result "WARN 读数 $rv 与注入 $Heading 不一致（注入可能未生效，结论需人工复核）"
        }
    }
    if ($enabled -and -not $hasDecl) { $failures += "开关=on 但缺「偏角」行" }
    if (-not $enabled -and $hasDecl) { $failures += "开关=off 但仍显示「偏角」行" }
} else {
    Add-Content -Encoding UTF8 $result "ui dump: BLOCKED (idle 超时，行存在性无法判)"
}
Add-Content -Encoding UTF8 $result "screenshot: $shotBytes B, heading (injected) = $Heading"

# 5) 像素 diff（诊断，不门禁）
$pixelInfo = "skipped"
if ($ReferenceDir) {
    $refName = if ($enabled) { "compass-p01-on.png" } else { "compass-p01-off.png" }
    $ref = Join-Path $ReferenceDir $refName
    if (Test-Path $ref) {
        $helper = Join-Path $PSScriptRoot "img_diff.py"
        $line = & python $helper $ref $shot 0 1850 1228 2500
        $pixelInfo = "$refName -> $line (诊断项：面板半透明+活地图，禁止当门禁)"
    } else { $pixelInfo = "reference not found: $ref" }
}
Add-Content -Encoding UTF8 $result "pixel(diagnostic): $pixelInfo"

# 6) 汇总
if ($failures.Count -gt 0) {
    Add-Content -Encoding UTF8 $result ("FAIL: " + ($failures -join "; "))
    Write-Host ("AT-P0-0: FAIL — " + ($failures -join "; "))
    exit 1
}
if (-not $dumpOk) {
    $why = if ($blockReason) { $blockReason } else { "ui dump idle 超时" }
    Add-Content -Encoding UTF8 $result "BLOCKED: $why；见 docs/qa/device-method.md"
    Write-Host "AT-P0-0: BLOCKED — $why"
    exit 2
}
Add-Content -Encoding UTF8 $result "PASS: ① 行存在性 ② prefs 值均符合（p0_1_enabled=$enabled）"
Write-Host "AT-P0-0: PASS (p0_1_enabled=$enabled)"
exit 0

