# M8 G2 复跑驱动：把页内比对脚本注入设备并取回结果（无设备可 -WhatIf 自检）
#
# 判据参数（缺一不可，见 docs/fidelity/m8/ground-drape-acceptance.md）：
#   * 在 **1456 画布空间**比对，不在 549 裁切空间比（off=(1456-549)/2=453.5 是半像素）
#   * 基线复刻用同一 translate(off,off) + 同一 drawImage(img,0,0,549,549) + 默认 imageSmoothingQuality
#   * 比较域 baseline alpha>250；去掉 3×3 软边；分母=区内像素数
#   * 度量 CIEDE2000>3；A1 纯位图区（离环 >4px 且不在刻度带）≤2%；A2 环叠区（合成基线）仅作自洽
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\m8-g2-verify.ps1 -WhatIf        # 无设备自检（语法/引号安全/字段齐全）
#   pwsh -NoProfile -File tests\e2e\m8-g2-verify.ps1 -Device <sn>    # 真跑（要求 App 已在罗盘态且贴地已开）
#
# 前置（真跑）：App 处于罗盘态、贴地已开、状态为 groundCanvasOk=true / diag=drawn。
# 未满足时脚本会先读状态并中止，不会用错误状态产出数字。

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$Script = '',
    [int]$WaitMs = 12000,
    [string]$OutDir = '',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Script) { $Script = Join-Path $PSScriptRoot 'm8-g2-inpage.js' }
if (-not (Test-Path -LiteralPath $Script)) { Write-Output "ERROR: missing $Script"; exit 2 }
if (-not $OutDir) { $OutDir = Join-Path $repo ('checks\m8-g2-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }

$js = [System.IO.File]::ReadAllText($Script)

# ---- 反证：故意改坏，自检必须变红（否则自检本身是空转）------------------
if ($SelfTest) {
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('m8-g2-selftest-' + [guid]::NewGuid().ToString('N') + '.js')
    $fails = 0
    function CheckBad([string]$text, [string]$label, [string]$expectPart) {
        [System.IO.File]::WriteAllText($tmp, $text, (New-Object System.Text.UTF8Encoding($false)))
        $sq = ($text.ToCharArray() | Where-Object { $_ -eq "'" }).Count
        $bt = ($text.ToCharArray() | Where-Object { $_ -eq [char]96 }).Count
        $nodeOk = $false
        $nodeOut = & node --check $tmp 2>&1
        if ($LASTEXITCODE -eq 0) { $nodeOk = $true }
        $bad = ($sq -gt 0) -or ($bt -gt 0) -or (-not $nodeOk)
        if ($bad) { Write-Output ("[PASS] 反证·$label 会被拦下  single=$sq backtick=$bt nodeOk=$nodeOk") }
        else { $script:fails++; Write-Output ("[FAIL] 反证·$label 没被拦下") }
    }
    # A) 插入一个单引号（模拟注入被 shell 吃掉）
    CheckBad ($js.Replace('var dial = 549;', "var dial = 549; var _q = '" + "x';")) 'A-插单引号' 'single_quotes'
    # B) 语法改坏
    CheckBad ($js.Replace('(function () {', '(function ( { i (')) 'B-语法改坏' 'node_check'
    Remove-Item -LiteralPath $tmp -ErrorAction SilentlyContinue
    Write-Output ''
    Write-Output ("G2-SELFTEST: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

# ---- 无设备自检（-WhatIf）------------------------------------------------
if (-not $PSCmdlet.ShouldProcess($Device, 'inject G2 script and read window.__g2')) {
    $fails = 0
    function Ok($n, $d = '') { Write-Output ("[PASS] $n" + $(if ($d) { "  $d" } else { '' })) }
    function No($n, $d = '') { $script:fails++; Write-Output ("[FAIL] $n" + $(if ($d) { "  $d" } else { '' })) }

    Write-Output "=== M8 G2 -WhatIf 自检（不调用 adb）==="
    Write-Output ("SCRIPT = $Script")
    Write-Output ("BYTES  = " + $js.Length)

    # 1) 注入安全：shell 单引号包裹，JS 里出现单引号/反引号会被吃掉或破坏
    $sq = ($js.ToCharArray() | Where-Object { $_ -eq "'" }).Count
    $bt = ($js.ToCharArray() | Where-Object { $_ -eq [char]96 }).Count
    if ($sq -eq 0) { Ok '注入安全：无双引号外的单引号' "single_quotes=$sq" } else { No '注入安全：JS 含单引号会被 shell 吃掉' "single_quotes=$sq" }
    if ($bt -eq 0) { Ok '注入安全：无反引号' "backticks=$bt" } else { No '注入安全：JS 含反引号' "backticks=$bt" }

    # 2) 语法
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) { No 'node --check' 'node 不在 PATH' }
    else {
        $out = & node --check $Script 2>&1
        if ($LASTEXITCODE -eq 0) { Ok 'node --check' 'exit 0' } else { No 'node --check' (($out | Select-Object -First 3) -join ' ') }
    }

    # 3) 输出字段齐全（签字要看的参数）
    foreach ($k in 'canvasW', 'canvasH', 'dial', 'off', 'imgW', 'imgH', 'smooth', 'A1', 'A2', 'bestHyp', 'hypTop3', 'CIEDE2000') {
        if ($js -match [regex]::Escape($k)) { Ok "输出字段 $k" } else { No "输出字段 $k" '脚本里没有该字段，签字时缺参数' }
    }

    # 4) 关键几何写死在脚本里（防“换个人跑就变”）
    if ($js -match 'translate\(off, off\)') { Ok '基线用 translate(off,off) 复刻' } else { No '基线 translate 复刻' '未找到 translate(off, off)' }
    if ($js -match 'img, 0, 0, dial, dial') { Ok '基线 drawImage(img,0,0,dial,dial)' } else { No '基线 drawImage 同构' '未找到同构 drawImage' }

    Write-Output ''
    Write-Output ("G2-WHATIF: " + $(if ($fails -eq 0) { 'PASS' } else { "FAIL ($fails)" }))
    exit $(if ($fails -eq 0) { 0 } else { 1 })
}

# ---- 真跑 ---------------------------------------------------------------
function JS([string]$code) {
    & adb -s $Device logcat -c | Out-Null
    & adb -s $Device shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$code'" | Out-Null
    Start-Sleep -Milliseconds 900
    $l = (& adb -s $Device logcat -d -s LCJS | Select-String 'map ->' | Select-Object -Last 1)
    if ($l) { return $l.Line.Substring($l.Line.IndexOf('map -> ') + 7) }
    return 'NO_REPLY'
}

$dev = (& adb devices) -join "`n"
if ($dev -notmatch [regex]::Escape($Device)) { Write-Output "[STOP] 设备不在线：$Device"; exit 2 }

# 前置检查：必须在罗盘态且贴地已成功，否则数字没有意义
$pre = JS 'JSON.stringify((function(){var s=getState();var g=__LC3D.getGroundDebug();return {ui:s.uiMode,ok:s.groundCanvasOk,diag:s.groundDiag,seq:s.groundSeq,hasQuad:g.hasQuad};})())'
Write-Output "PRE = $pre"
if ($pre -notmatch 'compass') { Write-Output '[STOP] 不在罗盘态（uiMode!=compass）'; exit 3 }
if ($pre -notmatch '"ok":true') { Write-Output '[STOP] 贴地未成功（groundCanvasOk!=true），先开贴地再来'; exit 3 }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
& adb -s $Device logcat -c | Out-Null
& adb -s $Device shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$js'" | Out-Null
Start-Sleep -Milliseconds $WaitMs

$res = JS 'JSON.stringify({r:(window.__g2||null)})'
Write-Output "G2 = $res"
$res | Set-Content -LiteralPath (Join-Path $OutDir 'g2-result.txt') -Encoding UTF8
& adb -s $Device exec-out screencap -p > (Join-Path $OutDir 'ground-state.png')
Write-Output "OUT = $OutDir"
if ($res -match 'NO_REPLY' -or $res -match 'null') { exit 1 }
exit 0
