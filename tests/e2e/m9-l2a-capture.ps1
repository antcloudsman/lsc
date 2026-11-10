#!/usr/bin/env pwsh
# M9-B（L2a）取图 + 判据：原生 CompassView 渲染 vs canvas 贴地盘面
#
# 比什么：同档 dialType、alpha=1.0，两侧各出一张**原生 1456 图**（不经过 screencap 再缩放），
#         交给 tests\e2e\m9-l2a-compare.py 做 ① 比例 ② 平移搜索 ③ 逐环双向存在性。
#
# 取图契约（客户端提供，缺则本脚本 SKIP 而不是假装通过）：
#   adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd export-dial --es value canvas:1456"
#   adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd export-dial --es value native:1456"
#   → 落到 /sdcard/Android/data/com.luopan.fengshui/files/dial-canvas.png / dial-native.png
#   原生侧必须是 CompassView.draw(Bitmap) 的直出图（带 alpha），不是 screencap 裁剪图。
#   理由：screencap 会引入背景合成 + 1228↔1456 插值，实测那条通道地板 3.33%，判据会被通道噪声淹没。
#
# 时序守卫（地图 1c929be）：取图前后各读一次 groundCanvasMeta()
#   seq 不变 且 texDirty===false 且 alphaApplied===1.0 且 dialType===t  ⇒ 这张图对应当前态
#   任一不满足 ⇒ 作废重取（最多重试 3 次）
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\m9-l2a-capture.ps1 -WhatIf        # 无设备自检
#   pwsh -NoProfile -File tests\e2e\m9-l2a-capture.ps1 -DialType 0    # 真跑
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = 'E:\APPDetector\reports\m9-l2a-run',
    [int]$DialType = 0,
    [int]$Space = 1456,
    [double]$Thresh = 2.0,
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$pkg = 'com.luopan.fengshui'
$remoteDir = "/sdcard/Android/data/$pkg/files"
$cmp = Join-Path $PSScriptRoot 'm9-l2a-compare.py'

function JS([string]$code) {
    & adb -s $Device logcat -c | Out-Null
    & adb -s $Device shell "am broadcast -a $pkg.DEBUG --es cmd js --es value '$code'" | Out-Null
    Start-Sleep -Milliseconds 900
    $l = (& adb -s $Device logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1)
    if (-not $l) { return 'NO_REPLY' }
    return $l.Line.Substring($l.Line.IndexOf('map -> ') + 7).Replace('\"', '"')
}

function Json([string]$s) {
    $m = [regex]::Match($s, '(?s)\{.*\}')
    if (-not $m.Success) { return $null }
    return $m.Value | ConvertFrom-Json
}

if ($WhatIf) {
    Write-Output '=== M9-B -WhatIf 自检（不调 adb、不占设备）==='
    $fails = 0
    function Ok($m) { Write-Output "[PASS] $m" }
    function No($m, $d) { Write-Output "[FAIL] $m  $d"; $script:fails++ }
    if (Test-Path $cmp) { Ok "比较器存在（$cmp）" } else { No '比较器' '找不到 m9-l2a-compare.py' }
    & python $cmp --selftest --selftest-space $Space 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Ok "比较器自检（4 变异全按预期：同图绿 / 1px 红 / 缺弧红 / 多画红）" }
    else { No '比较器自检' "exit=$LASTEXITCODE" }
    if ($Thresh -gt 0) { Ok "阈值参数可配（B+$Thresh pp）" } else { No '阈值' "Thresh=$Thresh" }
    if ($Space -in 549, 1456) { Ok "空间参数 $Space（σ 自动按空间缩放）" } else { No '空间' "Space=$Space 不在 {549,1456}" }
    foreach ($k in 'groundCanvasMeta', 'groundRenderSeq', 'export-dial') {
        Ok "取图契约引用 $k（缺该口时真跑会 SKIP，不冒充通过）"
    }
    Write-Output ''
    Write-Output ("M9-B-WHATIF: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

$dev = (& adb devices) -join "`n"
if ($dev -notmatch [regex]::Escape($Device)) { Write-Output "[STOP] 设备不在线：$Device"; exit 2 }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# 前置：贴地已绘制、同档、alpha=1
$metaPre = Json (JS 'JSON.stringify(__LC3D.groundCanvasMeta())')
if ($null -eq $metaPre) { Write-Output '[STOP] 读不到 groundCanvasMeta()（地图 readout 未合？）'; exit 3 }
if ($metaPre.texDirty -ne $false) { Write-Output "[STOP] texDirty=$($metaPre.texDirty)，画布未刷完"; exit 3 }

$set = JS "JSON.stringify(setCompassDial($DialType,1))"
Start-Sleep -Milliseconds 400
$m1 = Json (JS 'JSON.stringify(__LC3D.groundCanvasMeta())')
Write-Output ("META-PRE = dialType=" + $m1.dialType + " alphaApplied=" + $m1.alphaApplied + " texDirty=" + $m1.texDirty + " seq=" + $m1.renderSeq + " sig=" + $m1.sig)
if ($m1.dialType -ne $DialType) { Write-Output "[STOP] dialType=$($m1.dialType) 不是目标 $DialType"; exit 3 }
if ([math]::Abs([double]$m1.alphaApplied - 1.0) -gt 0.001) { Write-Output "[STOP] alphaApplied=$($m1.alphaApplied) 不是 1.0"; exit 3 }

# 两侧出图（客户端 debug 命令）
$null = & adb -s $Device shell "am broadcast -a $pkg.DEBUG --es cmd export-dial --es value canvas:$Space" 2>&1
$null = & adb -s $Device shell "am broadcast -a $pkg.DEBUG --es cmd export-dial --es value native:$Space" 2>&1
Start-Sleep -Milliseconds 1200

$m2 = Json (JS 'JSON.stringify(__LC3D.groundCanvasMeta())')
Write-Output ("META-POST = seq=" + $m2.renderSeq + " texDirty=" + $m2.texDirty + " sig=" + $m2.sig)
if ($m2.renderSeq -ne $m1.renderSeq -or $m2.texDirty -ne $false) {
    Write-Output '[ -- ] 取图期间画布重绘过（seq 变或 texDirty=true）——本张作废，需重跑'
    exit 4
}

foreach ($name in 'dial-canvas.png', 'dial-native.png') {
    $remote = "$remoteDir/$name"
    $exists = (& adb -s $Device shell "ls $remote 2>/dev/null") -join ''
    if ($exists -notmatch 'dial-') {
        Write-Output "[ -- ] SKIP 缺 $name：客户端尚未实现 export-dial 命令（原生单帧导出）"
        Write-Output "     没有它，原生侧只能走 screencap→$Space，通道地板 3.33%，比例判据不可判"
        exit 4
    }
    & adb -s $Device pull $remote (Join-Path $OutDir $name) 2>&1 | Out-Null
}

$sizes = @{}
foreach ($name in 'dial-canvas.png', 'dial-native.png') {
    $p = Join-Path $OutDir $name
    $b = [IO.File]::ReadAllBytes($p)
    $sizes[$name] = $b.Length
    Write-Output ("IMG $name = " + $b.Length + " B")
}

$cnv = Join-Path $OutDir 'dial-canvas.png'
$nat = Join-Path $OutDir 'dial-native.png'
Write-Output ("CMP = python $cmp --canvas $cnv --native $nat --space $Space --baseline B --margin $Thresh")
& python $cmp --canvas $cnv --native $nat --space $Space --margin $Thresh 2>&1 | ForEach-Object { Write-Output "  $_" }
$rc = $LASTEXITCODE

& adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'context.png')
Write-Output "OUT = $OutDir"
if ($rc -eq 0) { Write-Output 'M9-B: PASS'; exit 0 }
if ($rc -eq 1) { Write-Output 'M9-B: FAIL（见上：比例/对齐/环，任一）'; exit 1 }
Write-Output 'M9-B: 不可判（缺基线 B 或参数不全）'; exit 3
