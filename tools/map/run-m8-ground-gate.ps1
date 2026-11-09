# M8 贴地接线：静态断言 + 反证（门禁可执行）
#
# 目的：证明「产品路径能进 canvas 档」——不是只在调试钩子里能切。
#   正题：setGround()（产品按钮 toggleGround 的落点）里必须把 groundRenderer 切到 'canvas'，
#         关闭时切回 'overlay'；setGroundRenderer() 那条调试钩子不算数。
#   反证：把断言的对象故意改坏（改回 overlay / 删掉那行 / 只留调试钩子）→ 断言必须变红；
#         语法改坏 → node --check 必须变红。恒红的检查等于没有检查，所以正题也要能通过
#         （用一份「模拟已接线」的临时副本证明它确实能绿）。
#
# 用法：
#   pwsh -NoProfile -File tools\map\run-m8-ground-gate.ps1            # 跑正题 + 反证，全绿 exit 0
#   pwsh -NoProfile -File tools\map\run-m8-ground-gate.ps1 -OnlyPositive
#   pwsh -NoProfile -File tools\map\run-m8-ground-gate.ps1 -MapHtml <path> -OutDir <dir>
#
# 退出码：0 全部符合预期；1 有不符合项；2 参数/文件错误。

[CmdletBinding()]
param(
    [string]$MapHtml = '',
    [string]$OutDir = '',
    [switch]$OnlyPositive
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $MapHtml) { $MapHtml = Join-Path $repo 'app\android\app\src\main\assets\map.html' }
if (-not (Test-Path -LiteralPath $MapHtml)) {
    Write-Host "ERROR: map.html not found: $MapHtml" -ForegroundColor Red
    exit 2
}
$MapHtml = [System.IO.Path]::GetFullPath($MapHtml)
if (-not $OutDir) { $OutDir = Join-Path $repo ('checks\m8-ground-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$script:fails = 0
function Say([string]$s) { Write-Host $s; Add-Content -LiteralPath (Join-Path $OutDir 'gate.log') -Value $s -Encoding UTF8 }
function Pass([string]$name, [string]$detail = '') { Say ("[PASS] $name" + $(if ($detail) { "  $detail" } else { '' })) }
function Fail([string]$name, [string]$detail = '') { $script:fails++; Say ("[FAIL] $name" + $(if ($detail) { "  $detail" } else { '' })) }

# 取函数体：从 "function <name>(" 起，按花括号配对截到闭合处
function Get-FunctionBody {
    param([string]$Text, [string]$Name)
    $m = [regex]::Match($Text, "function\s+" + [regex]::Escape($Name) + "\s*\([^)]*\)\s*\{")
    if (-not $m.Success) { return $null }
    $start = $m.Index + $m.Length - 1   # 指向 '{'
    $depth = 0
    for ($i = $start; $i -lt $Text.Length; $i++) {
        $c = $Text[$i]
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') {
            $depth--
            if ($depth -eq 0) { return $Text.Substring($start, $i - $start + 1) }
        }
    }
    return $null
}

# 断言器：输入一段 map.html 文本，返回 PASS/FAIL 明细（不 exit，供正题与反证共用）
function Test-CanvasPath {
    param([string]$Text, [string]$Label)

    $results = @()
    $setGround = Get-FunctionBody -Text $Text -Name 'setGround'
    $toggle = Get-FunctionBody -Text $Text -Name 'toggleGround'
    $setRenderer = Get-FunctionBody -Text $Text -Name 'setGroundRenderer'

    if (-not $setGround) { $results += @{ ok = $false; name = "$Label setGround 函数存在"; detail = '未找到 function setGround(' ; }; return $results }
    $results += @{ ok = $true; name = "$Label setGround 函数存在"; detail = '' }

    if ($toggle -and $toggle -match 'setGround\(') {
        $results += @{ ok = $true; name = "$Label toggleGround 走产品路径 setGround()"; detail = '' }
    } else {
        $results += @{ ok = $false; name = "$Label toggleGround 走产品路径 setGround()"; detail = 'toggleGround 未调用 setGround(' }
    }

    # 核心断言：setGround 内必须有「把 groundRenderer 赋成 canvas」与「赋成 overlay」两条路径。
    # 直接赋值（groundRenderer = 'canvas'）与三元（groundRenderer = on ? 'canvas' : 'overlay'）
    # 都算数——但必须真的把这两个字面量赋给 groundRenderer，光出现字面量不算。
    # 只认「赋值」不认「比较」：groundRenderer === 'canvas' / == 'canvas' 是判断，不是切档。
    # 用 =(?!=) 排除 == 与 ===（这一条是反证抓出来的：初版会把 === 'canvas' 误当赋值，旧实现假绿）。
    $assigns = [regex]::Matches($setGround, 'groundRenderer\s*=(?!=)\s*([^;]+);')
    $canvasAssign = $null; $overlayAssign = $null
    foreach ($a in $assigns) {
        $rhs = $a.Groups[1].Value
        if ($rhs -match "['`"]canvas['`"]") { $canvasAssign = $rhs }
        if ($rhs -match "['`"]overlay['`"]") { $overlayAssign = $rhs }
    }
    $hasCanvas = ($null -ne $canvasAssign)
    $hasOverlay = ($null -ne $overlayAssign)

    $results += @{
        ok     = $hasCanvas
        name   = "$Label setGround 内切 canvas 档"
        detail = $(if ($hasCanvas) { "RHS: " + $canvasAssign.Trim() } else { "setGround 内没有把 groundRenderer 赋成 'canvas'（产品路径进不了 canvas 档）" })
    }
    $results += @{
        ok     = $hasOverlay
        name   = "$Label setGround 关闭回 overlay 档"
        detail = $(if ($hasOverlay) { "RHS: " + $overlayAssign.Trim() } else { "setGround 内没有把 groundRenderer 赋成 'overlay'" })
    }

    # 防「只在调试钩子里切」：canvas 写在 setGround 体里，且 setGround 体不等于 setGroundRenderer 体
    if ($setRenderer) {
        $hookAssigns = [regex]::Matches($setRenderer, 'groundRenderer\s*=(?!=)\s*[^;]+;')
        $hookCanvas = $false
        foreach ($h in $hookAssigns) { if ($h.Value -match "['`"]canvas['`"]") { $hookCanvas = $true } }
        $onlyInHook = (-not $hasCanvas) -and $hookCanvas
        $results += @{
            ok     = (-not $onlyInHook)
            name   = "$Label canvas 切档不在调试钩子独占"
            detail = $(if ($onlyInHook) { "只有 setGroundRenderer 能切 canvas（产品路径仍进不去）" } else { 'ok' })
        }
    }

    return $results
}

# node --check：把 map.html 里每个内联 <script> 块抽出来单独检查语法
function Test-NodeSyntax {
    param([string]$Text, [string]$Label, [string]$WorkDir)
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) { return @{ ok = $false; name = "$Label node --check"; detail = 'node 不在 PATH' } }

    $blocks = [regex]::Matches($Text, '(?is)<script\b[^>]*>(.*?)</script>')
    if ($blocks.Count -eq 0) { return @{ ok = $false; name = "$Label node --check"; detail = 'map.html 内无 <script> 块' } }

    $r = @()
    $idx = 0
    foreach ($b in $blocks) {
        $code = $b.Groups[1].Value
        if ($code.Trim().Length -eq 0) { continue }
        $idx++
        $f = Join-Path $WorkDir ("$Label-script$idx.js")
        [System.IO.File]::WriteAllText($f, $code, (New-Object System.Text.UTF8Encoding($false)))
        $out = & node --check $f 2>&1
        $code2 = $LASTEXITCODE
        $r += @{ ok = ($code2 -eq 0); name = "$Label node --check 块$idx（$($code.Length) 字符）"; detail = $(if ($code2 -eq 0) { '' } else { ($out -join ' ') }) }
    }
    return $r
}

# --- 正题：真实 map.html ----------------------------------------------------
Say "== M8 贴地接线门禁 =="
Say ("MAP_HTML = $MapHtml")
Say ("BYTES    = " + (Get-Item -LiteralPath $MapHtml).Length)
Say ''

$real = [System.IO.File]::ReadAllText($MapHtml)
$realResults = Test-CanvasPath -Text $real -Label 'real'
foreach ($r in $realResults) { if ($r.ok) { Pass $r.name $r.detail } else { Fail $r.name $r.detail } }

$synResults = Test-NodeSyntax -Text $real -Label 'real' -WorkDir $OutDir
foreach ($r in $synResults) { if ($r.ok) { Pass $r.name $r.detail } else { Fail $r.name $r.detail } }

# policy 单测（离线瓦片缓存策略，运行时同一份代码）
if (Test-Path (Join-Path $repo 'tools\fidelity\test-tilecache-policy.js')) {
    $pol = & node (Join-Path $repo 'tools\fidelity\test-tilecache-policy.js') 2>&1
    $polCode = $LASTEXITCODE
    ($pol -join "`n") | Set-Content -LiteralPath (Join-Path $OutDir 'tilecache-policy.log') -Encoding UTF8
    $line = ($pol | Select-String -Pattern 'RESULT:' | Select-Object -Last 1)
    if ($polCode -eq 0) { Pass 'tilecache policy 单测' ($line -replace '^\s*', '') } else { Fail 'tilecache policy 单测' (($pol | Select-Object -Last 3) -join ' | ') }
} else {
    Fail 'tilecache policy 单测' 'missing tools\fidelity\test-tilecache-policy.js'
}

if ($OnlyPositive) {
    Say ''
    Say ("M8-GROUND: " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL ($script:fails)" }))
    exit $(if ($script:fails -eq 0) { 0 } else { 1 })
}

# --- 反证：断言必须能变红，且必须能变绿 ------------------------------------
Say ''
Say '== 反证（故意改坏 → 必须变红；模拟已接线 → 必须变绿）=='

$tmp = Join-Path $OutDir 'negatives'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# 先构造一份「模拟已接线」的文本：往 setGround 里插入一行三元切档（与地图真实写法同形）
$implLine = "  groundRenderer = on ? 'canvas' : 'overlay';`n"
$fixed = [regex]::Replace($real, "(function\s+setGround\s*\(on\)\s*\{)", "`$1`n$implLine", 1)
[System.IO.File]::WriteAllText((Join-Path $tmp 'simulated-fixed.html'), $fixed, (New-Object System.Text.UTF8Encoding($false)))
$negFails = 0
function ExpectGreen([string]$text, [string]$label) {
    $rs = Test-CanvasPath -Text $text -Label $label
    $bad = @($rs | Where-Object { -not $_.ok })
    if ($bad.Count -eq 0) { Pass "反证·$label 断言应为绿" '模拟接线后全部通过' }
    else { $script:fails++; $negFails++; Fail "反证·$label 断言应为绿" (($bad | ForEach-Object { $_.name + ':' + $_.detail }) -join ' | ') }
}
function ExpectRed([string]$text, [string]$label, [string]$expectNamePart) {
    $rs = Test-CanvasPath -Text $text -Label $label
    $hit = @($rs | Where-Object { (-not $_.ok) -and ($_.name -like "*$expectNamePart*") })
    if ($hit.Count -gt 0) { Pass "反证·$label 应被断言抓住" (($hit | ForEach-Object { $_.detail }) -join ' | ') }
    else { $script:fails++; $negFails++; Fail "反证·$label 没被抓住" "期望 '$expectNamePart' 变红，实际没有" }
}

ExpectGreen $fixed 'N0-模拟接线'

# N1：切档改成两边都 overlay（用户按贴地仍回 overlay）
$n1 = [regex]::Replace($fixed, "(groundRenderer\s*=\s*on\s*\?\s*)'canvas'", "`$1'overlay'", 1)
ExpectRed $n1 'N1-切档写回overlay' 'setGround 内切 canvas 档'

# N2：整行删掉
$n2 = [regex]::Replace($fixed, "\s*groundRenderer\s*=\s*on\s*\?\s*'canvas'[^\n]*\n", "`n", 1)
ExpectRed $n2 'N2-删掉切档行' 'setGround 内切 canvas 档'

# N3：只留调试钩子（setGround 里的删掉，改由 setGroundRenderer 独占切档）
$n3 = [regex]::Replace($fixed, "\s*groundRenderer\s*=\s*on\s*\?\s*'canvas'[^\n]*\n", "`n", 1)
$n3 = [regex]::Replace($n3, "(function\s+setGroundRenderer\s*\(v\)\s*\{)", "`$1`n  groundRenderer = 'canvas';", 1)
ExpectRed $n3 'N3-只留调试钩子' 'canvas 切档不在调试钩子独占'

# N4：关闭不回 overlay（三元右边也写 canvas）
$n4 = [regex]::Replace($fixed, "(groundRenderer\s*=\s*on\s*\?\s*'canvas'\s*:\s*)'overlay'", "`$1'canvas'", 1)
ExpectRed $n4 'N4-关闭不回overlay' 'setGround 关闭回 overlay 档'

# N5：语法改坏 → node --check 必须红
$n5 = $fixed -replace '(function\s+setGround\s*\(on\)\s*\{)', '$1 if (('   # 制造未闭合括号
$synNeg = Test-NodeSyntax -Text $n5 -Label 'N5-broken-syntax' -WorkDir $tmp
$synBad = @($synNeg | Where-Object { -not $_.ok })
if ($synBad.Count -gt 0) { Pass '反证·N5-语法改坏 node --check 应红' (($synBad | ForEach-Object { $_.name }) -join ' | ') }
else { $script:fails++; $negFails++; Fail '反证·N5-语法改坏 node --check 应红' '语法改坏后 node --check 仍通过，检查无效' }

Say ''
Say ("NEGATIVES: " + $(if ($negFails -eq 0) { 'PASS（全部按预期红/绿）' } else { "FAIL ($negFails)" }))
Say ("M8-GROUND: " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL ($script:fails)" }))
Say ("OUT = $OutDir")
exit $(if ($script:fails -eq 0) { 0 } else { 1 })
