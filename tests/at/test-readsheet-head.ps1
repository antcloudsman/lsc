<#
  P2-08 顶部两行判定（F-06）的可重复自测。
    pwsh -File tests\at\test-readsheet-head.ps1
  退出码：0 = 7 个用例全部符合预期；1 = 有不符合。
#>
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\ReadSheetHead.ps1')

$h1 = '向北 94.0°    坐南 274.0°'
$h2 = '坐子山  向午山'
$combined = "$h1`n$h2"

$cases = @(
    @{ name = 'exact：整串逐字'; texts = @($combined); expect = 'PASS'; mode = 'exact' }
    @{ name = 'env：CRLF 换行'; texts = @("$h1`r`n$h2"); expect = 'PASS'; mode = 'env-normalized' }
    @{ name = 'env：零宽 + NBSP'; texts = @(("向北 94.0°$([char]0x200C)    坐南 274.0°" -replace ' ', [string][char]0x00A0) + "`n$h2"); expect = 'PASS'; mode = 'env-normalized' }
    @{ name = 'FAIL：舍入差 1.2 vs 1.1（旧回退会误判 PASS）'; texts = @("向北 1.2°    坐南 181.2°`n坐午山  向子山"); head1 = '向北 1.1°    坐南 181.1°'; head2 = '坐午山  向子山'; expect = 'FAIL'; mode = 'mismatch' }
    @{ name = 'FAIL：山名错'; texts = @("$h1`n坐丑山  向午山"); expect = 'FAIL'; mode = 'mismatch' }
    @{ name = 'FAIL：只有一行'; texts = @($h1); expect = 'FAIL'; mode = 'mismatch' }
    @{ name = 'FAIL：可见空格数量不同'; texts = @("向北 94.0°   坐南 274.0°`n$h2"); expect = 'FAIL'; mode = 'mismatch' }
)

$bad = 0
foreach ($c in $cases) {
    $a = if ($c.head1) { $c.head1 } else { $h1 }
    $b = if ($c.head2) { $c.head2 } else { $h2 }
    $r = Get-ReadSheetHeadResult -Head1 $a -Head2 $b -Texts $c.texts
    $ok = ($r.Status -eq $c.expect) -and ($r.Mode -eq $c.mode)
    if (-not $ok) { $bad++ }
    "{0}  {1}  status={2} mode={3} detail={4}" -f $(if ($ok) { 'OK  ' } else { 'FAIL' }), $c.name, $r.Status, $r.Mode, $r.Detail
}
"cases=$($cases.Count) bad=$bad"
if ($bad -gt 0) { exit 1 }
exit 0
