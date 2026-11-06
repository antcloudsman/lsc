<#
.SYNOPSIS
  logcat / dump / 文本落盘前的天地图 key 脱敏与自检（LC 测试）。

.DESCRIPTION
  round-acceptance 的 logcat.txt 里出过华为 WebView 内部日志把完整 URL 打出来：

    hwbr_engine_ContentUriUtils: final url after convert is file:///android_asset/map.html?tk=<32 位 web key>

  `tests/secrets/secret_gate.py` 对已跟踪文件做字节级精确匹配，这种证据文件一入库就 FAIL。
  本模块统一做三件事（顺序固定）：

  1) 精确替换：从 secrets 文件 / `DSH_SECRET_TDT_*` 读两个 key，出现处换成 `***REDACTED***`；
  2) 通用规则：`tk=[A-Za-z0-9]+` → `tk=***`，覆盖「换了 key 但 secrets 文件没同步」的情况；
     另打掉 `TDT_SERVER_KEY=<...>` / `TDT_WEB_KEY=<...>` 这类显式赋值写法；
  3) 落盘自检：脱敏后的文本里若还能匹配到任一 key（或 `tk=` 后仍有内容、或残留等于 key 的
     32 位十六进制串），直接判采集失败、打印 `LOGCAT_REDACT_FAIL`，**不写文件**。

  读 key 的优先级与 `tests/secrets/secret_gate.py` 一致：
    1. 环境变量 DSH_SECRET_TDT_SERVER_KEY / DSH_SECRET_TDT_WEB_KEY
    2. `--keys-file` 传入的路径 > 环境变量 TDT_KEYS_FILE > 默认 E:\APPDetector\secrets\tdt.properties
  拿不到 key 时不阻断采集（例如别人的机器没有 secrets 文件），但会打 WARN，并且仍然走通用
  `tk=` 规则 + `tk=` 残留自检。

  用法（在采集脚本里）：
    . (Join-Path $repo "tests\\lib\\LogRedact.ps1")
    $logText = (& $adb -s $Device logcat -d) -join "`n"
    Save-RedactedText -Path (Join-Path $OutDir "logcat.txt") -Text $logText      # 脱敏 + 自检 + 落盘
    Invoke-RedactFile -Path (Join-Path $OutDir "ui-map.xml")                    # 已有文件就地脱敏（不改内容则不动）

  退出语义：失败抛异常（调用方按「采集失败」处理），不会留下未脱敏文件。

.NOTES
  只读 secrets、只写调用方给的路径；不联网；任何情况下都不打印 key 值本身。
#>

$script:LogRedactSecretsFile = $env:TDT_KEYS_FILE
if (-not $script:LogRedactSecretsFile) { $script:LogRedactSecretsFile = "E:\APPDetector\secrets\tdt.properties" }
$script:LogRedactMarker = "***REDACTED***"

function Get-TdtKeyValues {
    <# 返回 key 值数组（只留长度 >= 16 的，避免把注释/空值当 key）。 #>
    param([string]$KeysFile = $script:LogRedactSecretsFile)

    $keys = New-Object System.Collections.Generic.List[string]
    foreach ($envName in @('DSH_SECRET_TDT_SERVER_KEY', 'DSH_SECRET_TDT_WEB_KEY')) {
        $v = [Environment]::GetEnvironmentVariable($envName)
        if ($v) { $v = $v.Trim() }
        if ($v -and $v.Length -ge 16) { $keys.Add($v) }
    }
    if (Test-Path -LiteralPath $KeysFile) {
        foreach ($line in (Get-Content -LiteralPath $KeysFile -Encoding UTF8)) {
            $t = "$line".Trim()
            if (-not $t -or $t.StartsWith('#') -or $t.StartsWith('!')) { continue }
            if ($t -match '^\s*([A-Za-z0-9_.]+)\s*[=:]\s*(.+?)\s*$') {
                $v = $Matches[2].Trim()
                if ($v.Length -ge 16) { $keys.Add($v) }
            }
        }
    }
    # 去重（大小写敏感：hex key 大小写不同即不同串，都要替换）
    $uniq = New-Object System.Collections.Generic.List[string]
    foreach ($k in $keys) { if (-not $uniq.Contains($k)) { $uniq.Add($k) } }
    return $uniq.ToArray()
}

function Invoke-RedactText {
    <# 纯函数：返回脱敏后的文本。Keys 为空时只做通用规则。 #>
    param(
        [Parameter(Mandatory = $false)][AllowNull()][string]$Text,
        [string[]]$Keys = @()
    )
    if ($null -eq $Text) { return $Text }
    $out = $Text
    if ($Keys) {
        foreach ($k in $Keys) {
            if ($k -and $k.Length -ge 8) { $out = $out.Replace($k, $script:LogRedactMarker) }
        }
    }
    # 通用规则：任何 tk=<token> 一律打掉（含 map.html?tk=、&tk=、?tk= 等写法）
    $out = [regex]::Replace($out, 'tk=[A-Za-z0-9]+', 'tk=***')
    # 显式赋值写法：TDT_SERVER_KEY=xxx / TDT_WEB_KEY: xxx
    $out = [regex]::Replace($out, '(?i)\b(TDT_(?:SERVER|WEB)_KEY\s*[=:]\s*)([A-Za-z0-9_\-]{8,})', '$1***')
    return $out
}

function Test-RedactClean {
    <# 返回问题列表；空数组表示干净。 #>
    param(
        [AllowNull()][string]$Text,
        [string[]]$Keys = @()
    )
    $problems = New-Object System.Collections.Generic.List[string]
    if ($null -eq $Text) { return @() }
    foreach ($k in $Keys) {
        if ($k -and $Text.IndexOf($k, [StringComparison]::Ordinal) -ge 0) { $problems.Add('key-literal') ; break }
    }
    # 32 位十六进制串：只要与已知 key 相等（忽略大小写）就算残留
    foreach ($m in [regex]::Matches($Text, '(?i)(?<![0-9a-f])[0-9a-f]{32}(?![0-9a-f])')) {
        foreach ($k in $Keys) {
            if ($k -and ($k -ieq $m.Value)) { $problems.Add('hex-key'); break }
        }
    }
    if ([regex]::IsMatch($Text, 'tk=[A-Za-z0-9]')) { $problems.Add('tk-param') }
    if ([regex]::IsMatch($Text, '(?i)\bTDT_(?:SERVER|WEB)_KEY\s*[=:]\s*[A-Za-z0-9_\-]{8,}')) { $problems.Add('tdt-env-assign') }
    return $problems.ToArray() | Select-Object -Unique
}

function Save-RedactedText {
    <#
      脱敏 → 自检 → 落盘。自检不过就抛异常（打印 LOGCAT_REDACT_FAIL），不写文件。
      返回脱敏后的文本，方便调用方继续在内存里用（例如统计 FATAL）。
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text,
        [string]$Label = "logcat",
        [string[]]$Keys = $null
    )
    $keysFile = $script:LogRedactSecretsFile
    if ($null -eq $Keys -or $Keys.Count -eq 0) { $Keys = Get-TdtKeyValues -KeysFile $keysFile }
    if (-not $Keys -or $Keys.Count -eq 0) {
        Write-Host "WARN: REDACT 未取到 key 值（secrets 文件 $keysFile 缺失/为空）；只跑通用 tk= 规则与 tk= 残留自检。"
    }
    $redacted = Invoke-RedactText -Text $Text -Keys $Keys
    $problems = Test-RedactClean -Text $redacted -Keys $Keys
    if ($problems -and $problems.Count -gt 0) {
        $detail = ($problems -join ',')
        [Console]::Error.WriteLine("LOGCAT_REDACT_FAIL: $Label 脱敏自检未通过（$detail）——已拒绝落盘：$Path")
        throw "LOGCAT_REDACT_FAIL: $Label ($detail)"
    }
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    Set-Content -LiteralPath $Path -Value $redacted -Encoding UTF8
    return $redacted
}

function Invoke-RedactFile {
    <# 对已落盘的文本文件就地脱敏（内容没变则不重写）。返回是否有改动。 #>
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string[]]$Keys = $null
    )
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    # 只处理文本类：跳过二进制（截图等），避免把 PNG 写成文本
    $ext = [IO.Path]::GetExtension($Path).ToLowerInvariant()
    if ($ext -in @('.png', '.jpg', '.jpeg', '.webp', '.gif', '.apk', '.zip', '.jar', '.so', '.dex')) { return $false }
    if ($null -eq $Keys -or $Keys.Count -eq 0) { $Keys = Get-TdtKeyValues }
    $text = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    if ($null -eq $text) { return $false }
    $redacted = Invoke-RedactText -Text $text -Keys $Keys
    if ($redacted -ceq $text) { return $false }
    $problems = Test-RedactClean -Text $redacted -Keys $Keys
    if ($problems -and $problems.Count -gt 0) {
        [Console]::Error.WriteLine("LOGCAT_REDACT_FAIL: 就地脱敏自检未通过（$($problems -join ',')) —— 拒绝写入：$Path")
        throw "LOGCAT_REDACT_FAIL: $Path"
    }
    Set-Content -LiteralPath $Path -Value $redacted -Encoding UTF8
    return $true
}

function Assert-NoKeyInNames {
    <#
      文件/目录名不得含 key 片段（连前 8 位 hex 都不许进文件名）。命中抛 LOGCAT_REDACT_FAIL。
      用于 SUMMARY / logcat.txt / ui*.xml / 截图命名纪律。
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [string[]]$Keys = $null
    )
    if ($null -eq $Keys -or $Keys.Count -eq 0) { $Keys = Get-TdtKeyValues }
    if (-not $Keys -or $Keys.Count -eq 0) { return }
    $items = @()
    if (Test-Path -LiteralPath $Path -PathType Container) {
        $items = Get-ChildItem -LiteralPath $Path -Recurse -Force | ForEach-Object { $_.FullName }
        $items += (Get-Item -LiteralPath $Path).FullName
    } else {
        $items = @($Path)
    }
    foreach ($name in $items) {
        foreach ($k in $Keys) {
            for ($len = 32; $len -ge 8; $len--) {
                if ($k.Length -lt $len) { continue }
                $frag = $k.Substring(0, $len)
                if ($name.IndexOf($frag, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    [Console]::Error.WriteLine("LOGCAT_REDACT_FAIL: 路径名含 key 片段（前 $len 位 hex）：$name")
                    throw "LOGCAT_REDACT_FAIL: 命名泄漏 $name"
                }
            }
        }
    }
}
