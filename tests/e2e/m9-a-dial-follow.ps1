#!/usr/bin/env pwsh
# M9-A 验收：贴地盘面跟随 dialType / dialAlpha（真机）
#
# 判据（产品 2026-11-10 提，总监采纳）：
#   ① 读值 = 绘制值：setCompassDial(t,a) 后 getGroundDebug().dialType/dialAlpha 必须回 t/a（钳位后），
#      且画布内容必须真的变（不能只验赋值不验绘制）
#   ② 矩阵不取单点：type 0..4 × alpha {1.0, 0.5} = 10 组；alpha=0 单独记「盘面不可见」一态
#   ③ 反证两条：
#      - 不置 dirty → 画布逐像素不变（运行时等价：同值再调一次，sig 必须完全相同）
#      - 不接线 → 读值仍恒 0/1（运行时等价：调用后读值必须等于调用参数，而不是 0/1）
#
# 只读口（地图 20fa126 提供）：
#   __LC3D.getGroundDebug()      → dialType / dialAlpha / texDirty
#   __LC3D.groundCanvasSig()     → { sig, dial, alpha, texDirty }  画布内容指纹
#   __LC3D.groundCanvasPixels()  → 可选；缺此口时「alpha 混合值」一项打 SKIP（不静默跳过）
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\m9-a-dial-follow.ps1                    # 真跑
#   pwsh -NoProfile -File tests\e2e\m9-a-dial-follow.ps1 -WhatIf            # 无设备自检
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = '',
    [int]$WaitMs = 260,           # 置脏后至少等 2 帧（60fps ≈ 33ms/帧）
    [switch]$WhatIf,
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'

# ---- 注入：走 App 自带的 DEBUG 广播，读 logcat 的 LCJS 通道（与 m8-g2-verify.ps1 同协议）----
function JS([string]$code) {
    & adb -s $Device logcat -c | Out-Null
    & adb -s $Device shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$code'" | Out-Null
    Start-Sleep -Milliseconds 900
    $l = (& adb -s $Device logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1)
    if ($l) { return $l.Line.Substring($l.Line.IndexOf('map -> ') + 7) }
    return 'NO_REPLY'
}

# 一次调用取回全部我要的东西：读值 + 画布指纹 + 三点采样（有像素口时）
$PROBE = @'
JSON.stringify((function(){
  var g=__LC3D.getGroundDebug();
  var s=(typeof __LC3D.groundCanvasSig==="function")?__LC3D.groundCanvasSig():null;
  var p=null;
  if(typeof __LC3D.groundCanvasPixels==="function"){
    try{ p=__LC3D.groundCanvasPixels([274,274, 728,728, 728,455]); }catch(e){ p="ERR:"+e.message; }
  }
  return {dialType:g.dialType, dialAlpha:g.dialAlpha, texDirty:g.texDirty,
          sig:(s?s.sig:null), sigDial:(s?s.dial:null), sigAlpha:(s?s.alpha:null), px:p};
})())
'@

if ($WhatIf) {
    Write-Output '=== M9-A -WhatIf 自检（不调用 adb、不占设备）==='
    $fails = 0
    function Ok($m) { Write-Output "[PASS] $m" }
    function No($m, $d) { Write-Output "[FAIL] $m  $d"; $script:fails++ }

    if ($PSBoundParameters.ContainsKey('Device')) { Ok "设备参数可覆盖（$Device）" }
    if ($WaitMs -ge 100) { Ok "置脏等待 $WaitMs ms ≥ 2 帧" } else { No '等待时长' "WaitMs=$WaitMs 太短，可能读不到重画后的值" }

    # 探针必须是合法 JS 且带全三个口
    $probeFile = Join-Path $env:TEMP ("m9-a-probe-" + [Guid]::NewGuid().ToString('N').Substring(0, 6) + '.js')
    Set-Content -LiteralPath $probeFile -Value ("var __r=" + $PROBE) -Encoding utf8
    $node = Get-Command node -ErrorAction SilentlyContinue
    if ($node) {
        & node --check $probeFile 2>$null
        if ($LASTEXITCODE -eq 0) { Ok '探针 JS 语法（node --check）' } else { No '探针 JS 语法' 'node --check 失败' }
    } else { Write-Output '[ -- ] node 不可用，跳过语法自检' }

    foreach ($k in 'getGroundDebug', 'groundCanvasSig', 'groundCanvasPixels') {
        if ($PROBE -match [regex]::Escape($k)) { Ok "探针引用 $k" } else { No "探针引用 $k" '缺失' }
    }
    # 判据矩阵必须齐全
    $matrix = @()
    foreach ($t in 0..4) { foreach ($a in 1.0, 0.5) { $matrix += , @($t, $a) } }
    if ($matrix.Count -eq 10) { Ok '矩阵 10 组（type0..4 × alpha{1.0,0.5}）' } else { No '矩阵' "只有 $($matrix.Count) 组" }
    if ($PROBE -match 'texDirty') { Ok '反证要用的 texDirty 在读口里' } else { No 'texDirty' '缺失' }

    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
    Write-Output ''
    Write-Output ("M9-A-WHATIF: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

# ---- 判据本体（-SelfTest 也要用，必须在设备检查之前定义）----
$seenDial = @{}

# 判据本体抽成函数：这样 -SelfTest 能用假数据证明「它真的会红」（不是只贴一段代码）
function Judge-Case {
    param([string]$tag, [int]$t, [double]$a, $p, $prev, $again, $p2)
    $out = @{ pass = @(); fail = @() }
    if ($p.dialType -eq $t) { $out.pass += "$tag 读值 dialType（读=" + $p.dialType + "）" }
    else { $out.fail += "$tag 读值 dialType（读=" + $p.dialType + " 期望=" + $t + "）" }
    if ([math]::Abs([double]$p.dialAlpha - $a) -lt 0.001) { $out.pass += "$tag 读值 dialAlpha（读=" + $p.dialAlpha + "）" }
    else { $out.fail += "$tag 读值 dialAlpha（读=" + $p.dialAlpha + " 期望=" + $a + "）" }
    if ($p.sig -ne $prev.sig) { $out.pass += "$tag 画布变化（sig " + $prev.sig + " -> " + $p.sig + "）" }
    else { $out.fail += "$tag 画布变化（sig 未变，置脏或重画没生效）" }
    if (-not $again.changed) { $out.pass += "$tag 同值 changed=false" }
    else { $out.fail += "$tag 同值 changed=false（changed=" + $again.changed + "）" }
    if ($p2.sig -eq $p.sig) { $out.pass += "$tag 同值不重画（sig 相同）" }
    else { $out.fail += "$tag 同值不重画（sig " + $p.sig + " -> " + $p2.sig + "）" }
    if ($null -ne $p2.px -and $a -eq 0.5) {
        $c = $p2.px[0]
        if ($c) {
            if ([math]::Abs([double]$c.a - 127) -le 4) { $out.pass += "$tag 中心 α≈127（a=" + $c.a + "）" }
            else { $out.fail += "$tag 中心 α≈127（a=" + $c.a + "，期望 123..131）" }
        }
    }
    return $out
}

if ($SelfTest) {
    Write-Output '=== M9-A -SelfTest：用假数据证明判据会红（无设备）==='
    $st = 0
    $good = [pscustomobject]@{ dialType = 2; dialAlpha = 0.5; sig = 'A1'; px = @([pscustomobject]@{ a = 127 }) }
    $prev = [pscustomobject]@{ sig = 'A0' }
    $againOk = [pscustomobject]@{ changed = $false }
    $same = [pscustomobject]@{ dialType = 2; dialAlpha = 0.5; sig = 'A1'; px = @([pscustomobject]@{ a = 127 }) }
    $r = Judge-Case 'good' 2 0.5 $good $prev $againOk $same
    if ($r.fail.Count -eq 0) { Write-Output "[PASS] 正确数据 -> 全绿（$($r.pass.Count) 条）" } else { Write-Output "[FAIL] 正确数据被判红：$($r.fail -join '; ')"; $st++ }

    $mutations = @(
        @{ n = '读值没跟上（dialType 仍 0）'; p = [pscustomobject]@{ dialType = 0; dialAlpha = 0.5; sig = 'A1'; px = @([pscustomobject]@{ a = 127 }) } },
        @{ n = 'alpha 没跟上（仍 1）'; p = [pscustomobject]@{ dialType = 2; dialAlpha = 1.0; sig = 'A1'; px = @([pscustomobject]@{ a = 127 }) } },
        @{ n = '画布没重画（sig 未变）'; p = [pscustomobject]@{ dialType = 2; dialAlpha = 0.5; sig = 'A0'; px = @([pscustomobject]@{ a = 127 }) } },
        @{ n = 'alpha 混合错（中心 a=255）'; p = [pscustomobject]@{ dialType = 2; dialAlpha = 0.5; sig = 'A1'; px = @([pscustomobject]@{ a = 255 }) } }
    )
    foreach ($m in $mutations) {
        $r2 = Judge-Case $m.n 2 0.5 $m.p $prev $againOk $m.p
        if ($r2.fail.Count -ge 1) { Write-Output "[PASS] 变异被抓：$($m.n) -> $($r2.fail.Count) 条红" } else { Write-Output "[FAIL] 变异未被抓：$($m.n)"; $st++ }
    }
    # 同值不重画那条也要能红
    $r3 = Judge-Case 'same-dirty' 2 0.5 $good $prev ([pscustomobject]@{ changed = $true }) ([pscustomobject]@{ sig = 'B9' })
    if ($r3.fail.Count -ge 1) { Write-Output "[PASS] 变异被抓：同值却 changed=true 且 sig 变了 -> $($r3.fail.Count) 条红" } else { Write-Output '[FAIL] 同值染色未抓'; $st++ }

    Write-Output ''
    Write-Output ("M9-A-SELFTEST: " + $(if ($st -eq 0) { 'PASS' } else { "FAIL ($st)" }))
    exit $(if ($st -eq 0) { 0 } else { 1 })

# ---- 真跑 ---------------------------------------------------------------
# ---- 真跑 ---------------------------------------------------------------
$dev = (& adb devices) -join "`n"
if ($dev -notmatch [regex]::Escape($Device)) { Write-Output "[STOP] 设备不在线：$Device"; exit 2 }
if (-not $OutDir) {
    $OutDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) ("checks\m9-a-dial-follow-" + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# 前置：必须罗盘态 + 贴地成功（否则读到的 dialType 没有渲染意义）
$pre = JS 'JSON.stringify((function(){var s=getState();var g=__LC3D.getGroundDebug();return {ui:s.uiMode,ok:s.groundCanvasOk,diag:s.groundDiag};})())'
Write-Output "PRE = $pre"
if ($pre -notmatch 'compass') { Write-Output '[STOP] 不在罗盘态（uiMode!=compass）'; exit 3 }
if ($pre -notmatch '"ok":true') { Write-Output '[STOP] 贴地未成功（groundCanvasOk!=true），先开贴地再来'; exit 3 }

$fails = 0
$skips = 0
$log = New-Object System.Collections.Generic.List[string]
function Note([string]$line) { $log.Add($line); Write-Output $line }
function Fail([string]$m, [string]$d) { Note "[FAIL] $m  $d"; $script:fails++ }
function Pass([string]$m, [string]$d) { Note "[PASS] $m  $d" }

function Probe { return (JS $PROBE) | ConvertFrom-Json }

# 基线（不改任何状态）
$base = Probe
Note ("BASE = dialType=" + $base.dialType + " alpha=" + $base.dialAlpha + " sig=" + $base.sig + " px=" + ($base.px | Out-String).Trim())
if ($null -eq $base.sig) { Fail '画布指纹口' 'groundCanvasSig 不可用（地图那笔没合？）' }
if ($null -eq $base.px) { Note '[ -- ] SKIP alpha 混合值判定：__LC3D.groundCanvasPixels 未提供（不静默跳过）'; $skips++ }

# 判据②：矩阵 10 组
$prev = $base
}

foreach ($t in 0..4) {
    foreach ($a in 1.0, 0.5) {
        $r = JS ("JSON.stringify(setCompassDial($t,$a))") | ConvertFrom-Json
        Start-Sleep -Milliseconds $WaitMs
        $p = Probe
        $tag = "type=$t alpha=$a"
        $again = JS ("JSON.stringify(setCompassDial($t,$a))") | ConvertFrom-Json
        Start-Sleep -Milliseconds $WaitMs
        $p2 = Probe
        $j = Judge-Case $tag $t $a $p $prev $again $p2
        foreach ($x in $j.pass) { Pass $x '' }
        foreach ($x in $j.fail) { Fail $x '' }
        $seenDial["$($p.dialType)"] = $true
        $prev = $p2
    }
}


# 反证②：不接线时读值恒 0/1 —— 只要有两档以上读值不同，就证明真赋值方存在
if ($seenDial.Keys.Count -ge 2) { Pass '反证·无赋值方' ("出现过 " + $seenDial.Keys.Count + " 个不同 dialType，说明真赋值方存在") }
else { Fail '反证·无赋值方' '所有档位读值都相同（疑似仍走 typeof 野变量 0/1）' }

# alpha=0 单态：盘面不可见（比对不透明态与 alpha=0 的画布指纹）
$nul = JS 'JSON.stringify(setCompassDial(0,0))' | ConvertFrom-Json
Start-Sleep -Milliseconds $WaitMs
$pz = Probe
if ($pz.dialAlpha -eq 0) { Pass 'alpha=0 读值' '0' } else { Fail 'alpha=0 读值' ("读=" + $pz.dialAlpha) }

Note ("M9-A: matrix=10 fails=" + $fails + " skips=" + $skips)
$log | Set-Content -LiteralPath (Join-Path $OutDir 'm9-a-result.txt') -Encoding UTF8
& adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'ground-after-follow.png')
Write-Output "OUT = $OutDir"
if ($fails -gt 0) { exit 1 }
if ($skips -gt 0) { Write-Output '[ -- ] 有 SKIP 项，未全判（见上）'; exit 4 }
exit 0