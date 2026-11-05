<#
  P2-08「顶部两行」判定（F-06 修复）。

  背景：读盘弹层顶部是**一个** TextView，文本 = head1 + "`n" + head2，例如
    "向北 94.0°    坐南 274.0°\n坐子山  向午山"
  run-final.ps1 的 Get-Texts 已把 uiautomator dump 的 `&#10;` 还原成换行。

  判据（收紧后）：
    1) PASS(exact)          整串逐字相等。
    2) PASS(env-normalized) 只允许「dump 环境类」差异：CRLF/CR、零宽字符、BOM、NBSP。
                            归一时**不动任何可见字符**（数字/度数/山名/空格数量）。
    3) FAIL                 其余一律 FAIL，并把实际文本写进 actual。
  旧实现「含『向北』且含『坐南』即命中」已删除——它会把 F-16 的舍入差异
  （1.1 vs 1.2）也判成 PASS。

  用法：
    . tests\at\lib\ReadSheetHead.ps1
    $r = Get-ReadSheetHeadResult -Head1 $g.head1 -Head2 $g.head2 -Texts $texts
    $r.Status   # PASS / FAIL
    $r.Mode     # exact / env-normalized / mismatch
#>

function Get-ReadSheetHeadResult {
    param(
        [Parameter(Mandatory = $true)][string]$Head1,
        [Parameter(Mandatory = $true)][string]$Head2,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][AllowNull()][string[]]$Texts
    )

    $combined = "$Head1`n$Head2"

    foreach ($t in $Texts) {
        if ($t -ceq $combined) {
            return [pscustomobject]@{
                Status = 'PASS'; Mode = 'exact'; Node = $t
                Line1 = $Head1; Line2 = $Head2
                Detail = '整串逐字命中'
            }
        }
    }

    # 环境类归一：只动不可见/转义类字符，可见字符一个都不动
    $envNorm = {
        param([string]$s)
        $s = $s -replace "`r`n", "`n" -replace "`r", "`n"
        $s = $s -replace [string][char]0x200B, '' -replace [string][char]0x200C, ''
        $s = $s -replace [string][char]0x200D, '' -replace [string][char]0xFEFF, ''
        $s = $s -replace [string][char]0x00A0, ' '
        return $s
    }
    $want = & $envNorm $combined
    foreach ($t in $Texts) {
        if ((& $envNorm $t) -ceq $want) {
            return [pscustomobject]@{
                Status = 'PASS'; Mode = 'env-normalized'; Node = $t
                Line1 = $Head1; Line2 = $Head2
                Detail = '仅 dump 环境类差异（换行/零宽/NBSP），归一后逐字命中'
            }
        }
    }

    # 失败：取最接近的「向北…坐南…」节点写证据，逐行给出匹配情况
    $near = $Texts | Where-Object { $_ -match '向北' -and $_ -match '坐南' } | Select-Object -First 1
    $line1 = ''; $line2 = ''
    if ($near) {
        $parts = $near -split "`n", 2
        $line1 = $parts[0]
        if ($parts.Count -gt 1) { $line2 = $parts[1] }
    }
    $detail = '未找到顶部两行节点'
    if ($near) {
        $detail = "首行逐字=$($line1 -ceq $Head1)；次行逐字=$($line2 -ceq $Head2)"
    }
    return [pscustomobject]@{
        Status = 'FAIL'; Mode = 'mismatch'; Node = $near
        Line1 = $line1; Line2 = $line2; Detail = $detail
    }
}
