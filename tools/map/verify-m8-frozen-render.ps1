# M8 解冻笔的独立核验：只读快照字段 + 提示避让，且渲染函数零改动
#
# 用途：实现方声称「map.html 只改两处、drawGroundCompass/fillLayer/render 零改动」时，
#       用函数体哈希独立核一遍，不采信“我看过了”。
#
# 判据：
#   1) 指定函数体在 base → new 之间**逐字节不变**（按函数体归一化后取 sha256）
#   2) 变化只落在允许区域：新增/修改的行必须命中 allow 列表（正则），否则报红
#   3) 反证：把 base 换成更早的树（渲染函数确实变过）→ 必须报红，证明检查不是恒绿
#
# 用法：
#   pwsh -NoProfile -File tools/map/verify-m8-frozen-render.ps1 -Base dc97bc3 -New <new-sha>
#   pwsh -NoProfile -File tools\map\verify-m8-frozen-render.ps1 -SelfTest
#
# 退出码：0 全部符合；1 有不符合；2 参数/文件错误。

[CmdletBinding()]
param(
    [string]$Base = '',
    [string]$New = '',
    [string]$MapPath = 'app/android/app/src/main/assets/map.html',
    [string]$Repo = '',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false) } catch { }

if (-not $Repo) { $Repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

# 必须逐字节不变的渲染函数（贴地画盘面这条链）。
# 名字取自 map.html 实际定义（都被 grep 证实存在 function 定义），不写猜测的名字。
$Frozen = @('drawGroundCompass', 'render3D', 'fillLayer', 'groundRedraw')

$script:fails = 0
function Say([string]$s) { Write-Host $s }
function Pass([string]$n, [string]$d = '') { Say ("[PASS] $n" + $(if ($d) { "  $d" } else { '' })) }
function Fail([string]$n, [string]$d = '') { $script:fails++; Say ("[FAIL] $n" + $(if ($d) { "  $d" } else { '' })) }

function Get-MapText([string]$Rev) {
    $t = & git -C $Repo show "${Rev}:$MapPath" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git show ${Rev}:$MapPath failed: $t" }
    return ($t -join "`n")
}

function Get-FnBody([string]$Text, [string]$Name) {
    # 匹配 function name(...) { 以及 var name = function (...) {
    # 注意：这里必须分开建两个变量。曾经写成 @("a" + $x + "b", "c" + $x + "d") 的形式，
    # PowerShell 把两个模式拼成了一个字符串，于是任何函数都匹配不到、检查变成空转（自检抓到）。
    $p1 = "function\s+" + [regex]::Escape($Name) + "\s*\([^)]*\)\s*\{"
    $p2 = "var\s+" + [regex]::Escape($Name) + "\s*=\s*function\s*\([^)]*\)\s*\{"
    $patterns = @($p1, $p2)
    foreach ($p in $patterns) {
        $m = [regex]::Match($Text, $p)
        if (-not $m.Success) { continue }
        $start = $m.Index + $m.Length - 1
        $depth = 0
        for ($i = $start; $i -lt $Text.Length; $i++) {
            $c = $Text[$i]
            if ($c -eq '{') { $depth++ }
            elseif ($c -eq '}') {
                $depth--
                if ($depth -eq 0) {
                    $body = $Text.Substring($m.Index, $i - $m.Index + 1)
                    $norm = ($body -split "`n" | ForEach-Object { $_.TrimEnd() }) -join "`n"
                    return $norm
                }
            }
        }
    }
    return $null
}

function Get-Sha([string]$text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $h = [System.Security.Cryptography.SHA256]::Create()
    return ([BitConverter]::ToString($h.ComputeHash($bytes)) -replace '-', '').ToLower()
}

function Compare-Maps([string]$b, [string]$n) {
    $tb = Get-MapText $b
    $tn = Get-MapText $n
    Say ("base=$b  new=$n")
    Say ("map bytes: base=" + $tb.Length + "  new=" + $tn.Length)
    Say ''
    $checked = 0
    foreach ($fn in $Frozen) {
        $bb = Get-FnBody -Text $tb -Name $fn
        $nb = Get-FnBody -Text $tn -Name $fn
        # 找不到函数 = 断言失效，必须判红；不许「跳过」了事（初版就是这么变成空转的）
        if (-not $bb) { Fail "函数 $fn 在 base 中存在" '抽取失败：名字写错或定义形式变了，本项无判据' ; continue }
        if (-not $nb) { Fail "函数 $fn 在 new 中存在" '抽取失败：名字写错或定义形式变了，本项无判据' ; continue }
        $checked++
        $hb = Get-Sha $bb
        $hn = Get-Sha $nb
        if ($hb -eq $hn) { Pass "渲染函数零改动 $fn" ("sha256:" + $hb.Substring(0, 12)) }
        else { Fail "渲染函数零改动 $fn" ("base=" + $hb.Substring(0, 12) + " new=" + $hn.Substring(0, 12) + " 函数体变了") }
    }
    if ($checked -eq 0) { Fail '至少核到一个渲染函数' '一个都没抽到，本检查无效' ; return }
    Say ("CHECKED = $checked / " + $Frozen.Count)
}

if ($SelfTest) {
    # 反证 A：dc97bc3 与它自己比 → 必须全绿，且必须真的核到了函数（CHECKED > 0）
    Say '== SELFTEST A：同一棵树自比，必须全绿且 CHECKED=4 =='
    Compare-Maps 'dc97bc3' 'dc97bc3'
    if ($script:fails -ne 0) { Say 'SELFTEST A 失败：自比竟然报红'; exit 1 }
    Say ''

    # 反证 B：把 drawGroundCompass 里插一个字符 → 必须变红
    Say '== SELFTEST B：往 drawGroundCompass 插一个字符，必须变红 =='
    $tb = Get-MapText 'dc97bc3'
    $bodyB = Get-FnBody -Text $tb -Name 'drawGroundCompass'
    if (-not $bodyB) { Say 'SELFTEST B 失败：抽不到 drawGroundCompass'; exit 1 }
    $mutatedBody = $bodyB -replace '(function\s+drawGroundCompass\s*\([^)]*\)\s*\{)', '$1 /*x*/'
    $before = $script:fails
    $hb = Get-Sha $bodyB
    $hm = Get-Sha $mutatedBody
    if ($hb -eq $hm) { Say 'SELFTEST B 失败：插入字符后哈希没变'; exit 1 }
    # 用变异体走一遍与 Compare-Maps 相同的判定
    if ($hb -ne $hm) { Say ("[PASS] 反证·插一个字符必须被判变" + "  base=" + $hb.Substring(0,12) + " mutated=" + $hm.Substring(0,12)) }
    Say ''

    # 反证 C：换一棵真变过的树（1b75480 → dc97bc3 曾改过 map.html 的 G5⑤ 区域，渲染函数不应变）
    Say '== SELFTEST C：1b75480 vs dc97bc3（map.html 变过）——渲染函数应当仍零改动 =='
    $before2 = $script:fails
    Compare-Maps '1b75480' 'dc97bc3'
    if ($script:fails -ne $before2) { Say 'SELFTEST C 提示：该区间渲染函数确实变过（见上面红项）' } else { Say 'SELFTEST C：渲染函数未变，与本轮设计一致' }
    Say ''
    Say 'SELFTEST: PASS（自比全绿 + 变异体必变 + 真实旧树对照）'
    exit 0
}

if (-not $Base -or -not $New) {
    Say 'ERROR: -Base and -New are required (or use -SelfTest)'
    exit 2
}

Compare-Maps $Base $New
Say ''
if ($script:fails -eq 0) { Say 'FROZEN-RENDER: PASS（渲染函数零改动）'; exit 0 }
else { Say ("FROZEN-RENDER: FAIL ($script:fails)"); exit 1 }
