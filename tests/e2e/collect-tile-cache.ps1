# B 轮离线缓存：设备轮取数（只采不计、不判 pass）
#
# 目的：设备只有一台、与其他成员串行，轮次时间宝贵——把「七个计数 + cacheExpect」的采数
#       固化成一条命令，避免审核验逐条手敲时漏取或标错网络状态。
#
# 设计约束（2026-11-09 技术总监派单）：
#   1) 原样输出，不做任何计算或汇总；
#   2) 每次采样都标明「取数时设备的网络状态」，避免事后分不清哪组是离线；
#   3) 恒等式 missAttemptsTotal = missKeyedAttempts + missNoKey + missUnsupported
#      只输出等号两侧原值，**脚本不判对错**（判断是审核验的活）；
#   4) 全程走 LogRedact，且 -WhatIf 可在无设备时自检语法与参数。
#
# 语义注意（2026-11-09 审核验真机实测，两处容易读错）：
#   * missAttemptsTotal 是「**miss 尝试**数」，**不含命中**——命中路径不经过任何 attempts 计数。
#     实测 hit=24、miss=28、missKeyedAttempts=36；不能拿它当「全部尝试数」，更不能除以它算命中率。
#   * miss 是唯一键口径；missKeyedAttempts 是原始次数口径。两者不同单位，不能互相反证。
#
# 用法：
#   pwsh -NoProfile -File tests\e2e\collect-tile-cache.ps1 -WhatIf                     # 无设备自检
#   pwsh -NoProfile -File tests\e2e\collect-tile-cache.ps1 -Label online               # 联网态取一次
#   pwsh -NoProfile -File tests\e2e\collect-tile-cache.ps1 -Label offline              # 断网态再取一次
#   pwsh -NoProfile -File tests\e2e\collect-tile-cache.ps1 -Label offline -OutDir <dir> # 指定证据目录
#
# 读值通道：DebugBridge 的 `js` 命令 → WebView.evaluateJavascript → logcat 标签 LCJS（"map -> …"）
#   am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "JSON.stringify(__LC3D.tileCache())"
#   am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "JSON.stringify(__LC3D.cacheExpect())"

[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = '',
    [ValidateSet('online', 'offline', 'unknown')]
    [string]$Label = 'unknown',
    [int]$WaitMs = 1800,
    [switch]$SelfTest,
    [string]$Pkg = 'com.luopan.fengshui'
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repo 'tests\lib\LogRedact.ps1')

if (-not $OutDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutDir = Join-Path $repo ("checks\m7-cache-$stamp")
}

$sevenKeys = @(
    'hit',
    'miss',
    'missAttemptsTotal',
    'missKeyedAttempts',
    'missNoKey',
    'missUnsupported',
    'netOk'
)

function Invoke-AdbLines {
    param([string[]]$ArgList)
    # 不命名为 Adb：PowerShell 不区分大小写，会递归到外部 adb.exe
    $out = & adb -s $Device @ArgList 2>&1
    return @($out)
}

function Build-JsBroadcastArgs {
    # 单独抽出来：-WhatIf 要能逐项打印这条 argv，才能看出引号层级对不对
    param([string]$Code)
    return @('shell', 'am', 'broadcast', '-a', "$Pkg.DEBUG", '-es', 'cmd', 'js', '-es', 'value', $Code)
}

function Send-JsAndRead {
    param([string]$Code)
    Invoke-AdbLines @('logcat', '-c') | Out-Null
    $b = Build-JsBroadcastArgs $Code
    if ($PSCmdlet.ShouldProcess("$Pkg (js)", "broadcast $Code")) {
        Invoke-AdbLines $b | Out-Null
    }
    Start-Sleep -Milliseconds $WaitMs
    $lines = Invoke-AdbLines @('logcat', '-d', '-s', 'LCJS')
    $hit = $lines | Where-Object { $_ -match 'map -> ' } | Select-Object -Last 1
    if (-not $hit) { return $null }
    $idx = $hit.IndexOf('map -> ')
    return $hit.Substring($idx + 7).Trim()
}

function Test-JsEcho {
    # 回声自检：先发 1+1，确认 logcat 真能打出 "map -> 2" 再发真查询。
    # JSON.stringify(...) 的输出自带双引号，要过 PowerShell → adb → sh 到 am broadcast --es value，
    # 引号极易被吃掉；被吃掉的后果与「没有数据」一样都是 MISSING，不先验回声就分辨不出来。
    $raw = Send-JsAndRead '1+1'
    if (-not $raw) {
        Write-Output '[ECHO] FAIL：1+1 没有回声（logcat 无 LCJS map -> 行）。广播链路断了，停止取数。'
        return $false
    }
    $val = $raw.Trim().Trim('"')
    if ($val -eq '2') {
        Write-Output "[ECHO] PASS：1+1 → $raw（广播与 logcat 链路正常）"
        return $true
    }
    Write-Output "[ECHO] FAIL：1+1 返回 $raw，不是 2。停止取数。"
    return $false
}

function ConvertFrom-JsString {
    # evaluateJavascript 回调给的是「JSON 字符串字面量」，外层再包一层引号与转义
    param([string]$Raw)
    if (-not $Raw) { return $null }
    try { $inner = $Raw | ConvertFrom-Json } catch { return $null }
    if ($inner -is [string]) {
        try { return ($inner | ConvertFrom-Json) } catch { return $null }
    }
    return $inner
}

$plan = @"
[PLAN] device      = $Device
[PLAN] label       = $Label
[PLAN] outdir      = $OutDir
[PLAN] wait_ms     = $WaitMs
[PLAN] step 1      = 记录网络状态（airplane_mode_on / Active default network）
[PLAN] step 2      = 回声自检 1+1（确认 logcat 能打出 map -> 2，再发真查询）
[PLAN] step 3      = logcat -c → broadcast js JSON.stringify(__LC3D.tileCache()) → logcat -d -s LCJS
[PLAN] step 4      = logcat -c → broadcast js JSON.stringify(__LC3D.cacheExpect()) → logcat -d -s LCJS
[PLAN] step 5      = 原样打印：七个计数逐项 + cacheExpect.expected/present + 恒等式两侧原值
[PLAN] step 6      = 全量写入 <$OutDir>\sample-<label>-<ts>.txt（经 LogRedact）
[PLAN] 判读          = 本脚本不计算差值、不判 pass；恒等式是否相等由审核验算
"@

if (-not $PSCmdlet.ShouldProcess('device', "collect tile cache sample ($Label)")) {
    # -WhatIf：只做参数与语法自检，不碰 adb
    Write-Output $plan
    Write-Output ''
    Write-Output '[WHATIF] 最终 argv（逐项打印，用来核对引号层级；这是将要执行的精确参数）'
    $probe = Build-JsBroadcastArgs 'JSON.stringify(__LC3D.tileCache())'
    for ($i = 0; $i -lt $probe.Count; $i++) {
        $tag = if ($i -eq ($probe.Count - 1)) { '  (value=最后一个参数，含双引号)' } else { '' }
        Write-Output ("  [{0,2}] {1}{2}" -f $i, $probe[$i], $tag)
    }
    Write-Output ("  argv_count = {0}  （adb -s {1} 之后传这些）" -f $probe.Count, $Device)
    Write-Output '[WHATIF] adb 未被调用；参数与脚本语法自检通过'
    exit 0
}

if ($SelfTest) {
    # 无设备自检：证明「logcat 行 → 七个计数」这条解析链真的能跑通，而不是等到设备前才发现解析坏了
    $canned = '11-09 12:00:00.000  1234  1234 D LCJS    : map -> "{\"hit\":12,\"miss\":3,\"missAttemptsTotal\":20,\"missKeyedAttempts\":12,\"missNoKey\":5,\"missUnsupported\":3,\"netOk\":160}"'
    $idx = $canned.IndexOf('map -> ')
    $parsed = ConvertFrom-JsString $canned.Substring($idx + 7).Trim()
    $ok = $false
    if ($parsed) {
        $ok = ($parsed.hit -eq 12 -and $parsed.miss -eq 3 -and $parsed.missAttemptsTotal -eq 20 -and
               $parsed.missKeyedAttempts -eq 12 -and $parsed.missNoKey -eq 5 -and
               $parsed.missUnsupported -eq 3 -and $parsed.netOk -eq 160)
    }
    if ($ok) {
        Write-Output '[SELFTEST] PASS：canned LCJS 行解析出 7 个计数（hit=12 miss=3 total=20 keyed=12 nokey=5 unsup=3 netOk=160）'
        Write-Output '[SELFTEST] adb 未被调用'
        exit 0
    }
    Write-Output '[SELFTEST] FAIL：canonical 解析链没还原出预期值，别上设备'
    exit 1
}

$devices = (& adb devices) -join "`n"
if ($devices -notmatch [regex]::Escape($Device)) {
    Write-Output "[STOP] 设备不在线：$Device（adb devices 未列出）。不取数、不猜值。"
    Write-Output 'ADB_DEVICES_BEGIN'
    Write-Output $devices.Trim()
    Write-Output 'ADB_DEVICES_END'
    exit 2
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ts = Get-Date -Format 'yyyy-MM-ddTHH:mm:ss'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

# --- step 1: 取数时的网络状态（必须随采样一起落盘，否则事后分不清哪组是离线） ---
$airplane = (Invoke-AdbLines @('shell', 'settings', 'get', 'global', 'airplane_mode_on') | Select-Object -First 1)
$conn = (Invoke-AdbLines @('shell', 'dumpsys', 'connectivity') | Where-Object { $_ -match 'Active default network|NetworkAgentInfo' } | Select-Object -First 3)

# --- step 2: 回声自检（先证明广播链路通，再发真查询；否则 MISSING 分不清是链路断还是钩子缺） ---
$echoOk = Test-JsEcho
if (-not $echoOk) {
    Write-Output 'STOP_AT_ECHO = 1'
    exit 3
}

# --- step 3/4: 两次数值 ---
$rawCache = Send-JsAndRead 'JSON.stringify(__LC3D.tileCache())'
$rawExpect = Send-JsAndRead 'JSON.stringify(__LC3D.cacheExpect())'

$cache = ConvertFrom-JsString $rawCache
$expect = ConvertFrom-JsString $rawExpect

$report = New-Object System.Collections.Generic.List[string]
$report.Add('=== B 轮离线缓存 · 设备取数（原样输出，脚本不做计算）===')
$report.Add("LABEL        = $Label")
$report.Add("TIME         = $ts")
$report.Add("DEVICE       = $Device")
$report.Add("AIRPLANE_ON  = $($airplane -join ' ')".TrimEnd())
$report.Add('NET_STATE_BEGIN')
foreach ($c in $conn) { $report.Add("  $c".TrimEnd()) }
$report.Add('NET_STATE_END')
$report.Add('')
$report.Add('RAW_TILECACHE_BEGIN')
$report.Add("  $rawCache")
$report.Add('RAW_TILECACHE_END')
$report.Add('RAW_CACHEEXPECT_BEGIN')
$report.Add("  $rawExpect")
$report.Add('RAW_CACHEEXPECT_END')
$report.Add('')

# --- step 4: 七个计数逐项原值（缺失就写 MISSING，不用 0 冒充） ---
if ($cache) {
    foreach ($k in $sevenKeys) {
        $v = $cache.PSObject.Properties[$k]
        if ($null -ne $v) { $report.Add("COUNTER $k = $($v.Value)") }
        else { $report.Add("COUNTER $k = MISSING") }
    }
} else {
    foreach ($k in $sevenKeys) { $report.Add("COUNTER $k = MISSING(no js reply)") }
}

# cacheExpect 的应有/命中
if ($expect) {
    $e = $expect.PSObject.Properties['expected']
    $p = $expect.PSObject.Properties['present']
    $report.Add("CACHEEXPECT expected = $(if ($null -ne $e) { $e.Value } else { 'MISSING' })")
    $report.Add("CACHEEXPECT present  = $(if ($null -ne $p) { $p.Value } else { 'MISSING' })")
} else {
    $report.Add('CACHEEXPECT expected = MISSING(no js reply)')
    $report.Add('CACHEEXPECT present  = MISSING(no js reply)')
}

# --- 恒等式两侧原值：只列，不算 ---
$report.Add('')
$report.Add('IDENTITY_FORM        = missAttemptsTotal = missKeyedAttempts + missNoKey + missUnsupported')
$report.Add('SEMANTICS            = missAttemptsTotal 只计 miss 尝试，不含 hit；miss=唯一键、missKeyedAttempts=原始次数，单位不同不可互证')
if ($cache) {
    $t = $cache.PSObject.Properties['missAttemptsTotal']
    $a = $cache.PSObject.Properties['missKeyedAttempts']
    $b = $cache.PSObject.Properties['missNoKey']
    $c = $cache.PSObject.Properties['missUnsupported']
    $fmt = { param($x) if ($null -ne $x) { $x.Value } else { 'MISSING' } }
    $report.Add("IDENTITY_LEFT        = $(& $fmt $t)")
    $report.Add("IDENTITY_RIGHT_TERMS = $(& $fmt $a) + $(& $fmt $b) + $(& $fmt $c)")
} else {
    $report.Add('IDENTITY_LEFT        = MISSING(no js reply)')
    $report.Add('IDENTITY_RIGHT_TERMS = MISSING(no js reply)')
}
$report.Add('IDENTITY_EVALUATED   = NO（脚本不判等号，留给审核验算）')

$text = ($report -join [Environment]::NewLine)
$target = Join-Path $OutDir "sample-$Label-$stamp.txt"
$redacted = Save-RedactedText -Path $target -Text $text -Label "cache-$Label"
Write-Output $redacted
Write-Output ''
Write-Output "SAMPLE_FILE = $target"
exit 0
