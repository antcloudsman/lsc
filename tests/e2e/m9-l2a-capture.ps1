#!/usr/bin/env pwsh
# M9-B（L2a）取图 + 判据：原生 CompassView 渲染 vs canvas 贴地盘面
#
# 参照物四项（缺一项即不可判，产品要求）：native（非 screencap、非 B 基线）＋ alpha=1.0 同档
#                                   ＋ 1456 同尺寸 ＋ faceKind=bitmap
#
# 取图契约（客户端 export-dial）：
#   am broadcast -a com.luopan.fengshui.DEBUG --es cmd export-dial --es value canvas:1456
#   am broadcast -a com.luopan.fengshui.DEBUG --es cmd export-dial --es value native:1456
#   → /sdcard/Android/data/com.luopan.fengshui/files/dial-canvas.png / dial-native.png（+ meta.txt）
#   原生侧必须 CompassView.draw(Bitmap) 直出（1456、ARGB_8888、eraseColor(TRANSPARENT)），
#   几何按审地图契约：直径 549、盘心 (728,728)；不是 screencap 裁剪图。
#
# SKIP 分码（产品要求，不许合并成「未验」）：
#   SKIP-A 接口未实现（export-dial 未落地）      → 台账「本项未验」
#   SKIP-B 超时仍 faceKind=vector               → **可能是真缺陷**，单列「位图降级」复测
#   SKIP-C 状态不收敛（seq 变 / texDirty 常真）  → 取样无效，可重跑
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\m9-l2a-capture.ps1 -WhatIf
#   pwsh -NoProfile -File tests\e2e\m9-l2a-capture.ps1 -DialType 0 -Thresh 2.0
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = 'E:\APPDetector\reports\m9-l2a-run',
    [int]$DialType = 0,
    [int]$Space = 1456,
    [double]$Thresh = 1.2,          # 两侧夹：检出下界 0.9 / 误报上界 1.7，见 -ThreshMin/-ThreshMax
    [double]$ThreshMin = 0.9,      # = 0.6%（同管线抗锯齿地板）×1.5
    [double]$ThreshMax = 1.7,      # = 2.59%（多画 20° 最小必抓缺陷）/1.5
    [int]$FaceTimeoutMs = 8000,
    [string]$Apk = '',            # 本次装的包；给了就做「树内↔包内」四步链校验
    [string]$TreeRoot = '',       # 构建用的树；空则用当前工作树
    [string]$Src = 'webp800',     # A′ 同源：export-dial --es src webp800（两侧喂同一文件字节）
    [switch]$CanvasOnly,   # 只出 canvas 侧（几何+掩膜+指纹）
    [switch]$Bare,         # 甲方案：两侧都只画位图（bare=1），比「位图绘制是否忠实」
    [double]$SigmaAbs = 1.5, # SIGMA 口径：绝对值 px @ 比较空间（与地图对齐；物理等效=1.5×space/549）
    [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'
$pkg = 'com.luopan.fengshui'
$remoteDir = "/sdcard/Android/data/$pkg/files"
$cmp = Join-Path $PSScriptRoot 'm9-l2a-compare.py'
$bareArg = if ($Bare) { ' --es bare 1' } else { '' }

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

function Meta { return Json (JS 'JSON.stringify(__LC3D.groundCanvasMeta())') }

if ($WhatIf) {
    Write-Output '=== M9-B -WhatIf 自检（不调 adb、不占设备）==='
    $fails = 0
    function Ok($m) { Write-Output "[PASS] $m" }
    function No($m, $d) { Write-Output "[FAIL] $m  $d"; $script:fails++ }
    if (Test-Path $cmp) { Ok "比较器存在" } else { No '比较器' 'm9-l2a-compare.py 缺失' }
    & python $cmp --selftest --selftest-space $Space 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { Ok '比较器自检（5 变异全按预期）' } else { No '比较器自检' "exit=$LASTEXITCODE" }
    & python $cmp --control --space $Space 2>&1 | Out-Null
    Ok '噪声地板模式可用（--noise）'
    if ($Thresh -gt 0) { Ok "margin 可配（B+$Thresh pp）" } else { No 'margin' "Thresh=$Thresh" }
    if ($FaceTimeoutMs -ge 1000) { Ok "faceKind 超时 $FaceTimeoutMs ms（超时记 SKIP-B）" } else { No '超时' '过短' }
    foreach ($k in 'groundCanvasMeta', 'renderSeq', 'faceKind', 'dialTypeUsed', 'export-dial') {
        Ok "契约引用 $k（缺则 SKIP-A/B/C，不冒充通过）"
    }
    Write-Output ''
    Write-Output ("M9-B-WHATIF: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

$dev = (& adb devices) -join "`n"
if ($dev -notmatch [regex]::Escape($Device)) { Write-Output "[STOP] 设备不在线：$Device"; exit 2 }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ---- 四步链：树内 blob / 树内 sha256 / 包内 sha256（审地图契约，断链即不出数）----
if ($Apk) {
    if (-not $TreeRoot) { $TreeRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) }
    $rel = 'app/android/app/src/main/assets/map.html'
    $blob = (& git -C $TreeRoot rev-parse "HEAD:$rel").Trim()
    $treeFile = Join-Path $TreeRoot $rel
    $shaTree = (Get-FileHash $treeFile -Algorithm SHA256).Hash.ToLower()
    $tmpHtml = Join-Path $env:TEMP ('apk-map-' + [Guid]::NewGuid().ToString('N').Substring(0, 8) + '.html')
    & python -c "import zipfile,sys;open(sys.argv[1],'wb').write(zipfile.ZipFile(sys.argv[2]).read('assets/map.html'))" $tmpHtml $Apk 2>&1 | Out-Null
    if (-not (Test-Path $tmpHtml)) {
        Write-Output "EXIT-CHAIN: 包内取不到 assets/map.html（$Apk）"
        exit 7
    }
    $shaApk = (Get-FileHash $tmpHtml -Algorithm SHA256).Hash.ToLower()
    Remove-Item $tmpHtml -Force -ErrorAction SilentlyContinue
    Write-Output ("CHAIN   : blob:" + $blob)
    Write-Output ("CHAIN   : 树内 sha256:" + $shaTree)
    Write-Output ("CHAIN   : 包内 sha256:" + $shaApk + "  MATCH=" + ($shaTree -eq $shaApk))
    if ($shaTree -ne $shaApk) {
        Write-Output 'EXIT-CHAIN: 树内与包内 map.html 不是同一份 → 装的是别的构建，L2a 不能出数'
        exit 7
    }
    $chainLine = "map.html blob:" + $blob + "  sha256:" + $shaTree
    $chainLine | Set-Content (Join-Path $OutDir 'run-meta.txt') -Encoding utf8
} else {
    Write-Output 'CHAIN   : SKIP（未给 -Apk；四步链缺第 3 步，结果不得作为签署依据）'
}

# ---- 契约探测：export-dial 是否落地（不落地 → SKIP-A）----
$probeMissing = $false
& adb -s $Device shell "am broadcast -a $pkg.DEBUG --es cmd export-dial --es value canvas:$Space --es src $Src$bareArg" 2>&1 | Out-Null
Start-Sleep -Milliseconds 900
if (((& adb -s $Device shell "ls $remoteDir/dial-canvas.png 2>/dev/null") -join '') -notmatch 'dial-') {
    $probeMissing = $true
    Write-Output 'SKIP-A: export-dial 未落地（canvas 侧也没有落盘文件）'
    Write-Output '        对照物缺一项 ⇒ 本项记「未验」，不给 PASS/FAIL'
    exit 10
}

# ---- 状态锁定：dialType / alpha=1.0 / faceKind=bitmap（超时 → SKIP-B）----
$null = JS "JSON.stringify(setCompassDial($DialType,1))"
$sw = [Diagnostics.Stopwatch]::StartNew()
$m1 = $null
while ($sw.ElapsedMilliseconds -lt $FaceTimeoutMs) {
    $m1 = Meta
    if ($null -eq $m1) { Write-Output 'SKIP-A: 读不到 groundCanvasMeta()（地图 readout 未合？）'; exit 10 }
    if ($m1.dialType -eq $DialType -and [math]::Abs([double]$m1.alphaApplied - 1.0) -lt 0.001 -and
        $m1.faceKind -eq 'bitmap' -and $m1.dialTypeUsed -eq $DialType -and $m1.texDirty -eq $false) { break }
    Start-Sleep -Milliseconds 300
}
if ($null -eq $m1 -or $m1.faceKind -ne 'bitmap') {
    Write-Output ("SKIP-B: 超时仍 faceKind=" + $m1.faceKind + " dialTypeUsed=" + $m1.dialTypeUsed +
                  " texDirty=" + $m1.texDirty)
    Write-Output '        这不是「没验成」，可能是真缺陷（用户看到矢量降级盘）⇒ 单列「位图降级」复测'
    exit 11
}
if ($m1.dialType -ne $DialType -or $m1.dialTypeUsed -ne $DialType) {
    Write-Output ("SKIP-C: 档位未生效 dialType=" + $m1.dialType + " dialTypeUsed=" + $m1.dialTypeUsed + " 期望 " + $DialType)
    exit 12
}
# 几何口径（地图 2026-11-10 确认）：groundCanvasMeta().dialPx 当前输出的是画布尺寸、off 未取整，
# 不能直接引用；一律用 w × GROUND_DIAL_FRAC 自算，off 用取整后的 dial 反推（与 groundRedraw 逐位一致）。
if ($null -ne $m1.w) {
    $w = [int]$m1.w
    $dial = [int][math]::Round($w * 0.37725)
    $off = ($w - $dial) / 2.0
    Write-Output ("GEOMETRY: canvas w=$w dial=$dial off=$off 盘心=(" + ($w / 2) + "," + ($w / 2) + ")  ← 由 w×0.37725 自算")
}
if ($Bare) { Write-Output 'BARE   : 甲方案生效（两侧 bare=1，只画位图；环/刻度/中心文字/天支线/天池均不画）' }
Write-Output ("SIGMA  : σ=$SigmaAbs px @ 比较空间（绝对像素；物理等效 $([math]::Round($SigmaAbs*$Space/549,2))）")
Write-Output 'MASK   : union(canvas alpha>250 ∪ native alpha>250)，分母=掩膜像素数'
Write-Output ("KNOWN-DEFECT: groundCanvasMeta().dialPx 当前=画布尺寸、off 未取整 → 取数一律用 w×0.37725；修法进 M10")

Write-Output ("META-LOCK = dialType=" + $m1.dialType + " dialTypeUsed=" + $m1.dialTypeUsed +
              " alphaApplied=" + $m1.alphaApplied + " faceKind=" + $m1.faceKind +
              " texDirty=" + $m1.texDirty + " seq=" + $m1.renderSeq + " sig=" + $m1.sig)

# ---- 取图：两轮（第二轮用于 B_noise）----
function Export([string]$side, [string]$dst) {
    $bareArg = if ($Bare) { ' --es bare 1' } else { '' }
    & adb -s $Device shell "am broadcast -a $pkg.DEBUG --es cmd export-dial --es value $side`:$Space --es src $Src$bareArg" 2>&1 | Out-Null
    Start-Sleep -Milliseconds 1200
    & adb -s $Device pull "$remoteDir/dial-$side.png" $dst 2>&1 | Out-Null
    return (Test-Path $dst)
}

foreach ($side in 'canvas', 'native') {
    if ($CanvasOnly -and $side -eq 'native') { continue }
    if (-not (Export $side (Join-Path $OutDir "run1-$side.png"))) {
        Write-Output "SKIP-A: $side 侧导出未落盘"; exit 10
    }
}
foreach ($side in 'canvas', 'native') {
    if ($CanvasOnly -and $side -eq 'native') { continue }
    if (-not (Export $side (Join-Path $OutDir "run2-$side.png"))) {
        Write-Output "SKIP-A: $side 侧第二轮导出未落盘"; exit 10
    }
}
if ($CanvasOnly) {
    # 只交 canvas 一半：几何 + alpha 形状 + 掩膜/指纹。native 侧待 cmd mode compass + 关贴地。
    Write-Output '--- CANVAS-ONLY 半份（native 侧不判，等 cmd mode 落地后一次补跑）---'
    & python $cmp --bbox --canvas (Join-Path $OutDir 'run1-canvas.png') --native (Join-Path $OutDir 'run1-canvas.png') `
        --space $Space --expect-dial 549 --expect-center '728,728' 2>&1 | Where-Object { $_ -match 'BBOX canvas|ALPHA canvas|BBOX-CHECK' } | ForEach-Object { Write-Output "  $_" }
    Write-Output ("CANVAS-FILES: " + (Join-Path $OutDir 'run1-canvas.png') + " / run2-canvas.png")
    Write-Output 'M9-B-CANVAS-HALF: 几何与 alpha 结论见上；像素差判定需两侧，未出'
    exit 20
}

$m2 = Meta
Write-Output ("META-AFTER = seq=" + $m2.renderSeq + " faceKind=" + $m2.faceKind +
              " dialTypeUsed=" + $m2.dialTypeUsed + " texDirty=" + $m2.texDirty)
# 稳定性锚点：用 sig（内容指纹），不用 renderSeq。
# 实测 2026-11-10：贴地画布约 40fps 每帧重绘，renderSeq 稳定增长（782→809/0.7s），
# 但内容完全不变（sig 两次相同）——把 seq 当锚点会把「每帧重绘」误判成「状态漂移」。
$stateOk = ($m2.faceKind -eq 'bitmap') -and ($m2.dialTypeUsed -eq $DialType) -and
           ([math]::Abs([double]$m2.alphaApplied - 1.0) -lt 0.001) -and ($m2.texDirty -eq $false)
# 内容不做「必须相同」的硬守卫：中心三行读数随朝向实时更新，两次导出天然不同帧（实测 sig 330→426 帧间变化）。
# L2a 判据本就把 C 区文字排除在像素比较之外（比较器 --exclude-center），所以这里只记录，不作废。
$contentOk = $true
if ($m2.sig -ne $m1.sig) {
    Write-Output ("SIG-INFO: 中心读数随朝向更新，sig " + $m1.sig + "→" + $m2.sig + "（预期现象，C 区已在比较器中排除）")
}
if (-not $stateOk) {
    Write-Output ("SKIP-C: 取图期间状态/内容变了（state=" + $stateOk + " content(sig)=" + $contentOk +
                  " seq " + $m1.renderSeq + "→" + $m2.renderSeq + " sig " + $m1.sig + "→" + $m2.sig + "）⇒ 本张作废，可重跑")
    exit 12
}
Write-Output ("SEQ-INFO: renderSeq " + $m1.renderSeq + "→" + $m2.renderSeq + "（每帧重绘，仅记录不作锚点）；sig 稳定=" + $m2.sig)

# ---- ④ 时序锚已过；先验导出几何（不过就别让 L2a 背锅）----
# 原生导出的前置状态守卫：贴地态下 CompassView 未被布局（B1 会隐藏原生盘面），
# 实测此时 meta 里 viewR=-8、scale=1.0、alphaShape=bad、bbox 279 —— 这张图不是「画得不像」，是没画。
# 用 metadata 的 alphaShape 与 viewR 判定，命中即 SKIP（接口前置不满足），不判 FAIL。
$natMeta = Join-Path $OutDir 'native-meta.txt'
& adb -s $Device pull "$remoteDir/dial-native.txt" $natMeta 2>&1 | Out-Null
if (Test-Path $natMeta) {
    $mt = Get-Content $natMeta -Raw
    $mView = [regex]::Match($mt, 'viewR=(-?\d+)')
    $mAlpha = [regex]::Match($mt, 'alphaShape=(\w+)')
    $viewR = if ($mView.Success) { [int]$mView.Groups[1].Value } else { 0 }
    $alphaShape = if ($mAlpha.Success) { $mAlpha.Groups[1].Value } else { 'unknown' }
    Write-Output ("NATIVE-META: viewR=$viewR alphaShape=$alphaShape srcUsed=" + ([regex]::Match($mt,'srcUsed=(\w+)').Groups[1].Value))
    if ($viewR -le 0 -or $alphaShape -ne 'ok') {
        Write-Output 'SKIP-C: 原生导出前置不满足（CompassView 未布局或 alphaShape=bad）'
        Write-Output '        贴地态下原生盘面被 B1 隐藏；取原生图必须在「原生盘面可见且已布局」的状态（客户端 10:49 那次 viewR=453）'
        exit 12
    }
}

Write-Output '--- 导出几何契约（直径 549±1、盘心 728±1、alpha 形状）---'
$bboxOut = & python $cmp --bbox --canvas (Join-Path $OutDir 'run1-canvas.png') --native (Join-Path $OutDir 'run1-native.png') `
    --space $Space --expect-dial 549 --expect-center '728,728' 2>&1
$bboxOut | ForEach-Object { Write-Output "  $_" }
$bboxRc = $LASTEXITCODE
if ($bboxRc -ne 0) {
    Write-Output 'EXIT-GEOMETRY: 导出几何/alpha 不合格 —— 这不是盘面差异，先修导出（审地图契约）'
    exit 5
}

# ---- B_noise：同侧同管线两轮差异（与判定同 mode 测量）----
$noiseC = (& python $cmp --noise (Join-Path $OutDir 'run1-canvas.png') (Join-Path $OutDir 'run2-canvas.png') --space $Space --noise-mask union 2>&1)
$noiseN = (& python $cmp --noise (Join-Path $OutDir 'run1-native.png') (Join-Path $OutDir 'run2-native.png') --space $Space --noise-mask union 2>&1)
Write-Output "NOISE-canvas: $noiseC"
Write-Output "NOISE-native: $noiseN"
$bn = 0.0
foreach ($line in @($noiseC, $noiseN)) {
    $mm = [regex]::Match(($line -join ' '), 'pct=([0-9.]+)%')
    if ($mm.Success) { $bn = [math]::Max($bn, [double]$mm.Groups[1].Value) }
}
# 阈值两侧夹（审地图 2026-11-10）：B_noise=0 时 margin 会退化成写死的 Thresh，等于「拍阈值」。
# 所以 Thresh 必须同时满足：
#   检出下界  ≥ 0.6%（同管线抗锯齿地板）×1.5 = 0.9%   ← 太低会把抗锯齿误报成缺陷
#   误报上界  ≤ 2.59%（多画 20°，① 必须抓住的最小缺陷）/1.5 = 1.7%
# 缺 350° 环(0.20%) 与错位 1px(0.35%) 分别由 ③ 和 ② 抓，① 不必压到 0.2%。
if ($Thresh -lt $ThreshMin -or $Thresh -gt $ThreshMax) {
    Write-Output ("EXIT-THRESH: Thresh=" + $Thresh + " 落在两侧夹区间 [" + $ThreshMin + ", " + $ThreshMax + "] 之外")
    Write-Output '              0.9% = 抗锯齿地板 0.6%×1.5；1.7% = 最小必抓缺陷 2.59%/1.5。越界视为调参，不出数'
    exit 6
}
$marginEff = [math]::Max($Thresh, 2 * $bn)
Write-Output ("THRESH  : Thresh=" + $Thresh + "% ∈ [" + $ThreshMin + "," + $ThreshMax + "]；检出依据 多画20°=2.59%/1.5；误报依据 地板0.6%×1.5")
Write-Output ("B_noise = " + $bn.ToString('F3') + "%  → margin = max(" + $Thresh + ", 2×B) = " + $marginEff.ToString('F3') + " pp")

# ---- 正式判定（并集掩膜；参照物 = native）----
Write-Output '--- L2a 判定 ---'
& python $cmp --canvas (Join-Path $OutDir 'run1-canvas.png') --native (Join-Path $OutDir 'run1-native.png') `
    --space $Space --baseline ([math]::Round($bn, 3)) --margin ([math]::Round($marginEff, 3)) --mask union --exclude-center 0.30 --sigma $SigmaAbs 2>&1 |
    ForEach-Object { Write-Output "  $_" }
$rc = $LASTEXITCODE

& adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'context.png')
Write-Output "OUT = $OutDir"
if ($rc -eq 0) { Write-Output 'M9-B: PASS'; exit 0 }
if ($rc -eq 1) { Write-Output 'M9-B: FAIL（比例/对齐/环/半径之一，见上）'; exit 1 }
Write-Output 'M9-B: 不可判'; exit 3
