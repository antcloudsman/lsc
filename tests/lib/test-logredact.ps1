<#
.SYNOPSIS
  tests/lib/LogRedact.ps1 的自测（可重复执行，不打印任何 key 值）。

.DESCRIPTION
  用真实 key 造一条「华为 WebView 泄漏行」样本，跑 Save-RedactedText，断言：

    1. 脱敏前：样本里能匹配到 key 与 `tk=` —— 作为基线计数（应 > 0）；
    2. 脱敏后：key 命中 0、`tk=` 后残留 0，文件已落盘；
    3. 自检器本身有效：把未脱敏原文喂给 Test-RedactClean，必须报 key-literal（否则等于自检失灵）；
    4. 通用规则独立成立：即使 Keys 为空（模拟换 key 后 secrets 没同步），`tk=***` 也能打掉。

  样本里的 key 只来自 secrets 文件/环境变量，只写 $env:TEMP，绝不进仓库、绝不打印。

  用法：pwsh -File tests\lib\test-logredact.ps1
  退出码：0 = 全通过；1 = 有失败。
#>
param()

$ErrorActionPreference = 'Stop'
$lib = Join-Path $PSScriptRoot 'LogRedact.ps1'
. $lib

function Count-Hits([string]$Text, [string]$Needle) {
    if (-not $Needle) { return 0 }
    $n = 0; $i = 0
    while (($i = $Text.IndexOf($Needle, $i, [StringComparison]::Ordinal)) -ge 0) { $n++; $i += $Needle.Length }
    return $n
}
function Count-TkParams([string]$Text) {
    return ([regex]::Matches($Text, 'tk=[A-Za-z0-9]+')).Count
}

$keys = Get-TdtKeyValues
if (-not $keys -or $keys.Count -eq 0) {
    Write-Host "SKIP: 未取到 key（secrets 文件缺失/为空），无法做「真实 key」自测。"
    exit 1
}
Write-Host "keys loaded: $($keys.Count) 个（不打印值）"

$webKey = $keys[$keys.Count - 1]
$tmpDir = Join-Path $env:TEMP ("lc_logredact_" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
$outFile = Join-Path $tmpDir 'logcat.txt'

# 三种泄漏写法各来一条：URL 里的 tk=、显式赋值、裸 key（日志里有人 dump 配置）
$sampleLines = @(
    "11-06 12:00:00.000  1234  1300 D hwbr_engine_ContentUriUtils: final url after convert is file:///android_asset/map.html?tk=$webKey",
    "11-06 12:00:01.000  1234  1300 I LcHttp: GET https://t{s}.tianditu.gov.cn/vec_w/wmts?tk=$webKey&layer=vec",
    "11-06 12:00:02.000  1234  1300 D MapKeys: TDT_WEB_KEY=$webKey",
    "11-06 12:00:03.000  1234  1300 D Ops: raw key $webKey (should never be logged)"
)
$sample = $sampleLines -join "`n"

$beforeKey = Count-Hits $sample $webKey
$beforeTk = Count-TkParams $sample
Write-Host "before: key-hits=$beforeKey tk-params=$beforeTk"

$fail = 0
if ($beforeKey -lt 1 -or $beforeTk -lt 1) { Write-Host "FAIL: 样本基线计数为 0，自测无意义"; $fail++ }

# 2) 脱敏 + 落盘 + 自检
$redacted = $null
try {
    $redacted = Save-RedactedText -Path $outFile -Text $sample -Label 'selftest-logcat' -Keys $keys
} catch {
    Write-Host "FAIL: 正常样本应脱敏成功，却抛异常：$($_.Exception.Message)"
    $fail++
}
if ($redacted) {
    if (-not (Test-Path -LiteralPath $outFile)) { Write-Host "FAIL: 脱敏后文件未落盘"; $fail++ }
    $disk = Get-Content -LiteralPath $outFile -Raw -Encoding UTF8
    $afterKey = Count-Hits $disk $webKey
    $afterTk = Count-TkParams $disk
    $afterMarker = Count-Hits $disk '***REDACTED***'
    Write-Host "after:  key-hits=$afterKey tk-params=$afterTk redacted-markers=$afterMarker"
    if ($afterKey -ne 0) { Write-Host "FAIL: 脱敏后仍有 key 命中"; $fail++ }
    if ($afterTk -ne 0) { Write-Host "FAIL: 脱敏后仍有 tk= 参数残留"; $fail++ }
    if ($afterMarker -lt 1) { Write-Host "FAIL: 未见 ***REDACTED*** 标记"; $fail++ }
}

# 3) 自检器本身：未脱敏原文喂进去必须报 key-literal（否则自检等于摆设）
$problems = @(Test-RedactClean -Text $sample -Keys $keys)
Write-Host "self-check on raw sample: $($problems -join ',')"
if (-not ($problems -contains 'key-literal')) { Write-Host "FAIL: 自检器没认出未脱敏原文里的 key"; $fail++ }

# 4) 通用规则独立成立：Keys 为空（等价于换 key 后 secrets 没同步）也要打掉 tk=
$generic = Invoke-RedactText -Text $sample -Keys @()
$gKey = Count-Hits $generic $webKey
$gTk = Count-TkParams $generic
Write-Host "generic-rule(no keys): key-hits=$gKey tk-params=$gTk"
if ($gTk -ne 0) { Write-Host "FAIL: 通用 tk= 规则未生效"; $fail++ }

# 收尾：临时目录整删（里面有真实 key 的原文样本，不能留）
Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

if ($fail -gt 0) {
    Write-Host "LOGREDACT SELFTEST: FAIL ($fail)"
    exit 1
}
Write-Host "LOGREDACT SELFTEST: PASS"
exit 0
