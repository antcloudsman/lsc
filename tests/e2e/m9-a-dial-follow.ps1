#!/usr/bin/env pwsh
# M9-A 验收：贴地盘面跟随 dialType / dialAlpha（真机）
#
# 判据（产品 2026-11-10 提，总监采纳；alpha 语义按地图 a343007「只乘一次」修订）：
#   ① 读值 = 绘制值：setCompassDial(t,a) 后
#        - getGroundDebug().dialType 必须回 t
#        - getGroundDebug().dialAlpha 必须回 a（钳位后）
#        - getGroundDebug().alphaApplied 必须等于 a（真正乘进 shader 的那个值，a343007 新增读口）
#   ② 画布确实重画：**按盘面类型分组**判 sig 必须变（a343007 起画布画不透明纹理，
#      sig 只随 dialType 变、不随 alpha 变——拿 alpha 组判 sig 会假红）
#   ③ 反证两条：
#      - 不置 dirty → 画布不变：同值再调一次，changed=false 且 sig 完全相同
#      - 不接线 → 读值恒 0/1：全程必须出现 ≥2 个不同 dialType
#   ④ 屏幕合成只乘一次（可选 -ScreenAlpha）：alpha=1 与 0.5 的盘面增量比 ≈0.50；
#      ≈0.25 即双次叠加（α²）。canvas 像素看 Garment 不出这一点，只能量屏幕。
#
# 只读口：__LC3D.getGroundDebug()（dialType/dialAlpha/alphaApplied/texDirty）、
#         __LC3D.groundCanvasSig()（画布指纹）、__LC3D.setGroundBaseline(on)（取无盘面基线）
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\m9-a-dial-follow.ps1 -WhatIf      # 无设备自检
#   pwsh -NoProfile -File tests\e2e\m9-a-dial-follow.ps1 -SelfTest    # 判据变异自检
#   pwsh -NoProfile -File tests\e2e\m9-a-dial-follow.ps1 -ScreenAlpha # 真跑 + 屏幕透明度比
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = '',
    [int]$WaitMs = 260,           # 置脏后至少等 2 帧（60fps ≈ 33ms/帧）
    [string]$RatioScript = 'E:\APPDetector\reports\m9-alpha-screen-ratio.py',
    [switch]$WhatIf,
    [switch]$SelfTest,
    [switch]$ScreenAlpha
)

$ErrorActionPreference = 'Stop'

function JS([string]$code) {
    & adb -s $Device logcat -c | Out-Null
    & adb -s $Device shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$code'" | Out-Null
    Start-Sleep -Milliseconds 900
    $l = (& adb -s $Device logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1)
    if ($l) { return $l.Line.Substring($l.Line.IndexOf('map -> ') + 7) }
    return 'NO_REPLY'
}

$PROBE = @'
JSON.stringify((function(){
  var g=__LC3D.getGroundDebug();
  var s=(typeof __LC3D.groundCanvasSig==="function")?__LC3D.groundCanvasSig():null;
  return {dialType:g.dialType, dialAlpha:g.dialAlpha,
          alphaApplied:(typeof g.alphaApplied==="number")?g.alphaApplied:null,
          texDirty:g.texDirty, sig:(s?s.sig:null)};
})())
'@

# ---- 判据本体（-SelfTest 也用它，必须在设备检查之前定义）----
function Judge-Case {
    param([string]$tag, [int]$t, [double]$a, $p, $prev, $again, $p2, [bool]$expectSigChange)
    $out = @{ pass = @(); fail = @(); skip = @() }
    if ($p.dialType -eq $t) { $out.pass += "$tag 读值 dialType（读=" + $p.dialType + "）" }
    else { $out.fail += "$tag 读值 dialType（读=" + $p.dialType + " 期望=" + $t + "）" }
    if ([math]::Abs([double]$p.dialAlpha - $a) -lt 0.001) { $out.pass += "$tag 读值 dialAlpha（读=" + $p.dialAlpha + "）" }
    else { $out.fail += "$tag 读值 dialAlpha（读=" + $p.dialAlpha + " 期望=" + $a + "）" }
    if ($null -ne $p.alphaApplied) {
        if ([math]::Abs([double]$p.alphaApplied - $a) -lt 0.001) { $out.pass += "$tag alphaApplied=参数（读=" + $p.alphaApplied + "）" }
        else { $out.fail += "$tag alphaApplied=参数（读=" + $p.alphaApplied + " 期望=" + $a + "）" }
    } else { $out.skip += "$tag alphaApplied 口不存在（a343007 未合？）" }
    if ($expectSigChange) {
        if ($p.sig -ne $prev.sig) { $out.pass += "$tag 换类型画布必变（sig " + $prev.sig + " -> " + $p.sig + "）" }
        else { $out.fail += "$tag 换类型画布必变（sig 未变，置脏或重画没生效）" }
    } else {
        if ($p.sig -eq $prev.sig) { $out.pass += "$tag 同类型只调 alpha：画布不变（sig 相同）" }
        else { $out.fail += "$tag 同类型只调 alpha：画布不应重画（sig " + $prev.sig + " -> " + $p.sig + "）" }
    }
    if (-not $again.changed) { $out.pass += "$tag 同值 changed=false" }
    else { $out.fail += "$tag 同值 changed=false（changed=" + $again.changed + "）" }
    if ($p2.sig -eq $p.sig) { $out.pass += "$tag 同值不重画（sig 相同）" }
    else { $out.fail += "$tag 同值不重画（sig " + $p.sig + " -> " + $p2.sig + "）" }
    return $out
}

if ($SelfTest) {
    Write-Output '=== M9-A -SelfTest：用假数据证明判据会红（无设备）==='
    $st = 0
    $prevType = [pscustomobject]@{ sig = 'T0' }
    $mk = { param($t, $a, $ap, $sig) [pscustomobject]@{ dialType = $t; dialAlpha = $a; alphaApplied = $ap; sig = $sig } }
    $againOk = [pscustomobject]@{ changed = $false }

    # 正例：换类型（sig 应变）
    $p = & $mk 2 1.0 1.0 'T2'
    $r = Judge-Case 'type-change' 2 1.0 $p $prevType $againOk $p $true
    if ($r.fail.Count -eq 0) { Write-Output "[PASS] 正确数据（换类型）-> 全绿 $($r.pass.Count) 条" } else { Write-Output "[FAIL] 正确数据被判红：$($r.fail -join '; ')"; $st++ }

    # 正例：同类型只调 alpha（sig 不应变）
    $prevA = & $mk 2 1.0 1.0 'T2'
    $pA = & $mk 2 0.5 0.5 'T2'
    $rA = Judge-Case 'alpha-only' 2 0.5 $pA $prevA $againOk $pA $false
    if ($rA.fail.Count -eq 0) { Write-Output "[PASS] 正确数据（只调 alpha）-> 全绿 $($rA.pass.Count) 条" } else { Write-Output "[FAIL] 只调 alpha 被判红：$($rA.fail -join '; ')"; $st++ }

    $mutations = @(
        @{ n = '读值没跟上(dialType 仍 0)'; p = (& $mk 0 0.5 0.5 'T2'); type = 2; sig = $true },
        @{ n = 'alphaApplied 没跟上(仍 1)'; p = (& $mk 2 0.5 1.0 'T2'); type = 2; sig = $false },
        @{ n = '换类型但画布没重画'; p = (& $mk 2 0.5 0.5 'T0'); type = 2; sig = $true },
        @{ n = '只调 alpha 却重画了画布'; p = (& $mk 2 0.5 0.5 'T9'); type = 2; sig = $false }
    )
    foreach ($m in $mutations) {
        $rr = Judge-Case $m.n $m.type 0.5 $m.p $(if ($m.sig) { $prevType } else { $prevA }) $againOk $m.p $m.sig
        if ($rr.fail.Count -ge 1) { Write-Output "[PASS] 变异被抓：$($m.n) -> $($rr.fail.Count) 条红" } else { Write-Output "[FAIL] 变异未被抓：$($m.n)"; $st++ }
    }
    $r3 = Judge-Case 'same-dirty' 2 0.5 (& $mk 2 0.5 0.5 'T2') $prevA ([pscustomobject]@{ changed = $true }) ([pscustomobject]@{ sig = 'B9' }) $false
    if ($r3.fail.Count -ge 1) { Write-Output "[PASS] 变异被抓：同值却 changed=true 且 sig 变了 -> $($r3.fail.Count) 条红" } else { Write-Output '[FAIL] 同值染色未抓'; $st++ }

    Write-Output ''
    Write-Output ("M9-A-SELFTEST: " + $(if ($st -eq 0) { 'PASS' } else { "FAIL ($st)" }))
    exit $(if ($st -eq 0) { 0 } else { 1 })
}

if ($WhatIf) {
    Write-Output '=== M9-A -WhatIf 自检（不调用 adb、不占设备）==='
    $fails = 0
    function Ok($m) { Write-Output "[PASS] $m" }
    function No($m, $d) { Write-Output "[FAIL] $m  $d"; $script:fails++ }
    if ($WaitMs -ge 100) { Ok "置脏等待 $WaitMs ms ≥ 2 帧" } else { No '等待时长' "WaitMs=$WaitMs 太短" }
    $probeFile = Join-Path $env:TEMP ("m9-a-probe-" + [Guid]::NewGuid().ToString('N').Substring(0, 6) + '.js')
    Set-Content -LiteralPath $probeFile -Value ("var __r=" + $PROBE) -Encoding utf8
    if (Get-Command node -ErrorAction SilentlyContinue) {
        & node --check $probeFile 2>$null
        if ($LASTEXITCODE -eq 0) { Ok '探针 JS 语法（node --check）' } else { No '探针 JS 语法' 'node --check 失败' }
    } else { Write-Output '[ -- ] node 不可用，跳过语法自检' }
    foreach ($k in 'getGroundDebug', 'groundCanvasSig', 'alphaApplied', 'texDirty') {
        if ($PROBE -match [regex]::Escape($k)) { Ok "探针引用 $k" } else { No "探针引用 $k" '缺失' }
    }
    $matrix = @(); foreach ($t in 0..4) { foreach ($a in 1.0, 0.5) { $matrix += , @($t, $a) } }
    if ($matrix.Count -eq 10) { Ok '矩阵 10 组（type0..4 × alpha{1.0,0.5}）' } else { No '矩阵' "只有 $($matrix.Count) 组" }
    if ($ScreenAlpha) {
        if (Test-Path $RatioScript) { Ok "屏幕透明度比脚本存在（$RatioScript）" } else { No '屏幕透明度比脚本' "找不到 $RatioScript" }
    }
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
    Write-Output ''
    Write-Output ("M9-A-WHATIF: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

# ---- 真跑 ---------------------------------------------------------------
$dev = (& adb devices) -join "`n"
if ($dev -notmatch [regex]::Escape($Device)) { Write-Output "[STOP] 设备不在线：$Device"; exit 2 }
if (-not $OutDir) {
    $OutDir = Join-Path 'E:\APPDetector\reports' ("m9-a-dial-follow-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$pre = JS 'JSON.stringify((function(){var s=getState();var g=__LC3D.getGroundDebug();return {ui:s.uiMode,ok:s.groundCanvasOk,diag:s.groundDiag,ground:g.ground};})())'
Write-Output "PRE = $pre"
if ($pre -notmatch '"ok":true') { Write-Output '[STOP] 贴地未成功（groundCanvasOk!=true），先开贴地再来'; exit 3 }

$fails = 0; $skips = 0
$log = New-Object System.Collections.Generic.List[string]
function Note([string]$l) { $log.Add($l); Write-Output $l }
function Fail([string]$m) { Note "[FAIL] $m"; $script:fails++ }
function Pass([string]$m) { Note "[PASS] $m" }
function Skip([string]$m) { Note "[ -- ] SKIP $m"; $script:skips++ }
function Probe { return (JS $PROBE) | ConvertFrom-Json }

$base = Probe
Note ("BASE = dialType=" + $base.dialType + " alpha=" + $base.dialAlpha + " alphaApplied=" + $base.alphaApplied + " sig=" + $base.sig)
$seenDial = @{}
$prev = $base
$prevType = $null

foreach ($t in 0..4) {
    foreach ($a in 1.0, 0.5) {
        $typeChanged = ($t -ne $prev.dialType)
        $r = JS ("JSON.stringify(setCompassDial($t,$a))") | ConvertFrom-Json
        Start-Sleep -Milliseconds $WaitMs
        $p = Probe
        $again = JS ("JSON.stringify(setCompassDial($t,$a))") | ConvertFrom-Json
        Start-Sleep -Milliseconds $WaitMs
        $p2 = Probe
        $tag = "type=$t alpha=$a"
        $j = Judge-Case $tag $t $a $p $prev $again $p2 $typeChanged
        foreach ($x in $j.pass) { Pass $x }
        foreach ($x in $j.fail) { Fail $x }
        foreach ($x in $j.skip) { Skip $x }
        $seenDial["$($p.dialType)"] = $true
        $prev = $p2
    }
}

if ($seenDial.Keys.Count -ge 2) { Pass ("反证·无赋值方：出现过 " + $seenDial.Keys.Count + " 个不同 dialType") }
else { Fail '反证·无赋值方：所有档位读值相同（疑似仍走 typeof 野变量 0/1）' }

# 屏幕合成只乘一次（可选）
if ($ScreenAlpha) {
    if (-not (Test-Path $RatioScript)) { Skip "屏幕透明度比：找不到 $RatioScript" }
    else {
        JS 'setCompassDial(2,1)' | Out-Null; Start-Sleep -Milliseconds 500
        & adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'alpha-100.png')
        JS 'setCompassDial(2,0.5)' | Out-Null; Start-Sleep -Milliseconds 500
        & adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'alpha-050.png')
        JS '__LC3D.setGroundBaseline(1)' | Out-Null; Start-Sleep -Milliseconds 600
        & adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'baseline-nodial.png')
        JS '__LC3D.setGroundBaseline(0)' | Out-Null; Start-Sleep -Milliseconds 300
        $ratio = & python $RatioScript (Join-Path $OutDir 'baseline-nodial.png') (Join-Path $OutDir 'alpha-100.png') (Join-Path $OutDir 'alpha-050.png') 2>&1
        $ratio | ForEach-Object { Note "  $_" }
        if ($LASTEXITCODE -eq 0) { Pass '屏幕合成单次（k≈0.50）' } else { Fail '屏幕合成比值不在 0.5±0.08（双次叠加 k≈0.25 即 α²）' }
    }
}

Note ("M9-A: matrix=10 fails=" + $fails + " skips=" + $skips)
$log | Set-Content -LiteralPath (Join-Path $OutDir 'm9-a-result.txt') -Encoding UTF8
& adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'ground-after-follow.png')
Write-Output "OUT = $OutDir"
if ($fails -gt 0) { exit 1 }
if ($skips -gt 0) { Write-Output '[ -- ] 有 SKIP 项，未全判'; exit 4 }
exit 0
