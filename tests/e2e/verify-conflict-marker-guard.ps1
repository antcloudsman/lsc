#!/usr/bin/env pwsh
# 冲突标记检查的可重复反证（evidence-lint 的 `check_conflict_markers`）
#
# 为什么要有这个脚本：M8 期间一份**带 `<<<<<<<` 的 markdown** 通过了全部五道门禁——
# 五道门禁都不看 markdown 结构，绿只代表它检查过的维度。修好工具还不算完，
# 必须能一条命令重放「只插开头必红 / 只插结尾必红 / 只插裸 ======= 必红 / 正常树绿」，
# 否则下一个人只能相信我的口头结论。
#
# 用法：pwsh -NoProfile -File tests\e2e\verify-conflict-marker-guard.ps1
# 退出码：0 = 全部按预期；1 = 有反证没变红或有正例变红
[CmdletBinding()]
param(
    [string]$RefCommit = 'cda6521',   # M8 事故版本（带 3 个标记），用于「真实事故能抓到」
    [switch]$KeepTemp
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path (Join-Path $root 'tools\fidelity\evidence-lint.py'))) {
    $root = (Get-Location).Path
}
$lint = Join-Path $root 'tools\fidelity\evidence-lint.py'
$env:PYTHONIOENCODING = 'utf-8'

function Invoke-Lint {
    param([string[]]$LintArgs)
    $out = & python $lint @LintArgs 2>&1
    return @{ Exit = $LASTEXITCODE; Text = ($out -join "`n") }
}

$fails = 0
$cases = 0
function Check([string]$name, [bool]$ok, [string]$detail) {
    $script:cases++
    if ($ok) {
        Write-Host ("[PASS] " + $name + "  " + $detail)
    } else {
        $script:fails++
        Write-Host ("[FAIL] " + $name + "  " + $detail)
    }
}

$tmpRoot = Join-Path $env:TEMP ("conflict-guard-" + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null

try {
    $srcDoc = Join-Path $root 'docs\fidelity\m8\INDEX.md'
    if (-not (Test-Path $srcDoc)) { $srcDoc = Join-Path $root 'docs\product\help-center.md' }
    $base = Get-Content $srcDoc -Raw -Encoding utf8

    # 三个反证：每种标记**单独**插入一处
    $negCases = @(
        @{ name = '只插开头 <<<<<<<'; body = "<<<<<<< HEAD`n" + $base; marker = '<<<<<<<' },
        @{ name = '只插结尾 >>>>>>>'; body = $base + "`n>>>>>>> feature/x"; marker = '>>>>>>>' },
        @{ name = '只插裸 ======='; body = "# t`n`n=======`n`ntail`n"; marker = '=======' }
    )
    foreach ($c in $negCases) {
        $dir = Join-Path $tmpRoot ([Guid]::NewGuid().ToString('N').Substring(0, 6))
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $file = Join-Path $dir 'doc.md'
        Set-Content -Path $file -Value $c.body -Encoding utf8 -NoNewline
        $changed = (Get-Content $file -Raw -Encoding utf8) -ne $base
        $r = Invoke-Lint @('--root', $dir)
        $red = ($r.Exit -eq 1) -and ($r.Text -match [regex]::Escape($c.marker)) -and ($r.Text -match 'FAIL conflict')
        Check $c.name $red ("exit=" + $r.Exit + " marker_in_output=" + ($r.Text -match [regex]::Escape($c.marker)) + " changed=" + $changed)
    }

    # 正例 1：正常证据树（docs\fidelity）必须绿且 fail=0
    $r = Invoke-Lint @('--root', (Join-Path $root 'docs\fidelity'))
    Check '正常树 docs\fidelity' (($r.Exit -eq 0) -and ($r.Text -match 'fail=0') -and ($r.Text -match 'CONFLICT: scanned=\d+ file\(s\) hits=0')) ("exit=" + $r.Exit)

    # 正例 2：lc-help（webp + index.html）二进制必须被跳过、不得误报
    $r = Invoke-Lint @('--root', (Join-Path $root 'app\android\app\src\main\assets\lc-help'), '--expect-size', '1228x2700', '--expect-size-map', (Join-Path $root 'tools\fidelity\evidence-size-map.json'))
    Check '正常树 lc-help（含 webp）' (($r.Exit -eq 0) -and ($r.Text -match 'fail=0')) ("exit=" + $r.Exit)

    # 正例 3：生成物单独跑（门禁④ 口径）必须绿、且明说扫了 1 个文件
    $r = Invoke-Lint @('--generated-html', (Join-Path $root 'app\android\app\src\main\assets\lc-help\index.html'), '--source', (Join-Path $root 'docs\product\help-center.md'))
    Check '门禁④ 生成物正常' (($r.Exit -eq 0) -and ($r.Text -match 'generated html scanned=1 hits=0')) ("exit=" + $r.Exit)

    # 反证 4：生成物里插标记 → 门禁④ 必须红（无 --root 时也不能漏）
    $dir = Join-Path $tmpRoot 'gen-html'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $html = Get-Content (Join-Path $root 'app\android\app\src\main\assets\lc-help\index.html') -Raw -Encoding utf8
    $html2 = $html -replace '(?m)^<!DOCTYPE html>', "<!DOCTYPE html>`n<<<<<<< HEAD"
    Set-Content -Path (Join-Path $dir 'index.html') -Value $html2 -Encoding utf8 -NoNewline
    $r = Invoke-Lint @('--generated-html', (Join-Path $dir 'index.html'), '--source', (Join-Path $root 'docs\product\help-center.md'))
    Check '门禁④ 生成物插标记' (($r.Exit -eq 1) -and ($r.Text -match 'FAIL conflict')) ("exit=" + $r.Exit + " changed=" + ($html2 -ne $html))

    # 反证 5：真实事故（M8 关账摘要带 3 个标记的版本）必须被抓到
    $dir = Join-Path $tmpRoot 'incident'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & git -C $root cat-file -e "${RefCommit}:docs/fidelity/m8/m8-close-summary.md" 2>$null
    $have = $LASTEXITCODE
    $ErrorActionPreference = $prevEap
    if ($have -eq 0) {
        $body = & git -C $root show "${RefCommit}:docs/fidelity/m8/m8-close-summary.md"
        Set-Content -Path (Join-Path $dir 'm8-close-summary.md') -Value ($body -join "`n") -Encoding utf8 -NoNewline
        $r = Invoke-Lint @('--root', $dir)
        $n = ([regex]::Matches($r.Text, 'FAIL conflict')).Count
        Check "真实事故 $RefCommit" (($r.Exit -eq 1) -and ($n -ge 3)) ("exit=" + $r.Exit + " conflict_fails=" + $n)
    } else {
        Check "真实事故 $RefCommit" $false "commit 不可用，无法重放"
    }
}
finally {
    if (-not $KeepTemp) { Remove-Item $tmpRoot -Recurse -Force -ErrorAction SilentlyContinue }
    else { Write-Host ("TEMP kept: " + $tmpRoot) }
}

Write-Host ("CONFLICT-GUARD: cases=" + $cases + " fails=" + $fails)
if ($fails -gt 0) { exit 1 }
Write-Host 'CONFLICT-GUARD: PASS'
exit 0
