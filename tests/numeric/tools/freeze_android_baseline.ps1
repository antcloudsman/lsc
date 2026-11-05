<#
.SYNOPSIS
  K-01 单页 UI 的 Android 真机基线冻结（布局改造后使用）。

.DESCRIPTION
  流程：先过 F-01/F-02 数值门禁 → 装包启动 → 地图态采左列 8 / 右列 10 控件位
  → 「更多」采 16 宫格 bottom sheet → 点「罗盘」采浮层态（截图为准，dump 可能只有 1 个节点）
  → 「隐藏」回地图态 → 输出截图 / dump / 控件表 / manifest / SHA256SUMS。

  控件位基准：`docs/evidence/original-apk/orig-main.xml`（1228×2700），按屏宽/屏高比例缩放后
  与真机 dump 最近节点匹配；容差 45px。左列 8 / 右列 10 / 更多 16 是 K-01 验收硬项，缺一即 exit 1。

.PARAMETER Apk
  待冻结的 APK（必须是 K-01 布局提交构建的 debug 包；heading 注入依赖 debug 广播）。

.PARAMETER OutDir
  证据目录，建议 `docs/qa/baseline-android/<Round>`（由调用方指定，脚本不拼默认轮次）。

.PARAMETER Round
  轮次名（如 `round-k01`）。manifest 标题、轮次行全部由它生成；不传则拒跑。

.PARAMETER Branch
  构建该 APK 的分支（如 `fix/p0-review-findings`）。不传时 manifest 写「未提供」。

.PARAMETER BuildHead
  构建该 APK 的提交 sha（完整或短 sha）。**必传**：脚本所在 worktree 的 HEAD 与 -Apk
  没有绑定关系，不能顶替；不传则明确报错退出，绝不静默取当前树 HEAD。

.PARAMETER Structure
  本轮的界面结构描述（如「单页地图 + 罗盘浮层，无底部导航」）。manifest 原样引用；
  不传时写「未提供」，脚本不臆造结构描述。

.PARAMETER PanelCropY
  SW-006 面板区像素 diff 的新裁切窗（形如 `1400-1850`）。K-01 后旧窗 y1850-2500 失效，
  不传则在 manifest 写「待客户端按新布局实测」，不得默认沿用旧值。

.PARAMETER PanelCropBasis
  裁切窗的测量依据（谁量、哪张截图、怎么定面板上缘）。与 PanelCropY 一起写进 manifest；
  缺依据时 manifest 明确标「未提供」，不许只给一个数字。

.PARAMETER SkipInstall
  跳过装包（设备上已是同一 APK 时用）。

.PARAMETER InjectHeading
  冻结航向（默认 0.0），避免 CompassView 每帧重绘导致 uiautomator idle 超时。

.EXAMPLE
  pwsh -File tests\numeric\tools\freeze_android_baseline.ps1 `
    -Apk E:\APPDetector\fix-p0\app\android\app\build\outputs\apk\debug\app-debug.apk `
    -OutDir docs\qa\baseline-android\round-k01 -PanelCropY 1400-1850

.NOTES
  退出码：0 = 采集完整；1 = 门禁失败 / 硬项缺失 / 设备前置失败。
#>
param(
    [string]$Device = "L2E0222531014833",
    [Parameter(Mandatory = $true)][string]$Apk,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [string]$Round = "",
    [string]$Branch = "",
    [string]$BuildHead = "",
    [string]$Structure = "",
    [switch]$SkipInstall,
    [float]$InjectHeading = 0.0,
    [string]$PanelCropY = "",
    [string]$PanelCropBasis = ""
)

$ErrorActionPreference = "Stop"

# ---- F-17：manifest 的轮次/构建来源/结构描述一律由参数生成，缺 BuildHead 直接拒跑 ----
function Fail([string]$message) {
    [Console]::Error.WriteLine("ERROR: $message")
    exit 1
}
if (-not $Round) {
    Fail "缺少 -Round（例：-Round round-k01）。manifest 标题与轮次必须能互相追溯，脚本不猜轮次。"
}
if (-not $BuildHead) {
    Fail "缺少 -BuildHead：必须传入构建该 APK 的提交 sha。脚本所在 worktree 的 HEAD 与 -Apk 无关，不能顶替（F-17）。"
}
if ($BuildHead -notmatch '^[0-9a-fA-F]{7,40}$') {
    Fail "-BuildHead 不是合法提交 sha：$BuildHead"
}
if (-not (Test-Path -LiteralPath $Apk)) {
    Fail "-Apk 不存在：$Apk"
}
if (-not $Branch) {
    Write-Host "WARN: 未传 -Branch，manifest 的构建分支将写「未提供」。"
}
$adb = "adb"
$Pkg = "com.luopan.fengshui"
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))

function Shot([string]$name) {
    $path = Join-Path $OutDir $name
    & $adb -s $Device exec-out screencap -p > $path
    if (-not (Test-Path $path) -or (Get-Item $path).Length -lt 10000) { throw "截图失败: $name" }
    return $path
}
function DumpUi([string]$file) {
    $remote = "/sdcard/lc_k01_ui.xml"
    for ($i = 1; $i -le 3; $i++) {
        & $adb -s $Device shell rm -f $remote | Out-Null
        $o = & $adb -s $Device shell uiautomator dump $remote 2>&1
        if (($o -join "`n") -match "dumped to") {
            & $adb -s $Device pull $remote (Join-Path $OutDir $file) 2>&1 | Out-Null
            if (Test-Path (Join-Path $OutDir $file)) {
                return [pscustomobject]@{ ok = $true; xml = (Get-Content -Raw (Join-Path $OutDir $file)) }
            }
        }
        Start-Sleep -Milliseconds 600
    }
    return [pscustomobject]@{ ok = $false; xml = "" }
}
function Get-NodeInfos([string]$xml) {
    $out = @()
    foreach ($m in [regex]::Matches($xml, '<node\b[^>]*>')) {
        $n = $m.Value
        $b = [regex]::Match($n, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $b.Success) { continue }
        $cx = ([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2
        $cy = ([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2
        $out += [pscustomobject]@{
            text    = [regex]::Match($n, 'text="([^"]*)"').Groups[1].Value
            desc    = [regex]::Match($n, 'content-desc="([^"]*)"').Groups[1].Value
            cls     = ([regex]::Match($n, 'class="([^"]*)"').Groups[1].Value -replace '.*\.', '')
            bounds  = $b.Value
            cx      = [int]$cx
            cy      = [int]$cy
            click   = ($n -match 'clickable="true"')
        }
    }
    return $out
}
function Get-Texts([string]$xml) {
    return [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
}
function Tap-Text([string]$xml, [string]$pattern) {
    $node = (Get-NodeInfos $xml) | Where-Object { $_.text -match $pattern -or $_.desc -match $pattern } | Select-Object -First 1
    if (-not $node) { return $false }
    & $adb -s $Device shell input tap "$($node.cx)" "$($node.cy)" | Out-Null
    Start-Sleep -Milliseconds 900
    return $true
}

# ---- 前置：F-01/F-02 数值门禁（不通过不许冻结）----
$gateOut = Join-Path $OutDir "numeric-gate.txt"
New-Item -ItemType Directory -Force $OutDir | Out-Null
try {
    $gate = & python (Join-Path $repo "tests\numeric\run_all.py") 2>&1
    $gateCode = $LASTEXITCODE
} catch {
    $gate = @("python 不可用: $($_.Exception.Message)")
    $gateCode = 1
}
$gateText = $gate -join "`n"
$gateText | Set-Content -Path $gateOut -Encoding UTF8
$gateSummary = ([regex]::Match($gateText, 'tests=\d+ failures=\d+ errors=\d+ skipped=\d+')).Value
if (-not $gateSummary) { $gateSummary = "未解析到 tests= 摘要行" }
$gateOk = ($gateCode -eq 0) -and ($gateText -match "(?m)^tests=\d+ failures=0 errors=0 skipped=0") `
    -and ($gateText -match "RESULT: PASS") -and ($gateText -match "CARGO: PASS")
Write-Host "gate: exit=$gateCode ok=$gateOk -> $gateOut"
if (-not $gateOk) {
    Write-Host "GATE FAIL：F-01/F-02 未通过，拒绝冻结基线。"
    exit 1
}

# ---- 设备与包 ----
if (-not $SkipInstall) {
    $install = (& $adb -s $Device install -r $Apk 2>&1 | Select-Object -Last 1)
    if ($install -notmatch "Success") { throw "install 失败: $install" }
}
& $adb -s $Device shell am force-stop $Pkg
& $adb -s $Device shell pm clear $Pkg | Out-Null
& $adb -s $Device logcat -c
$start = & $adb -s $Device shell am start -W -n "$Pkg/.MainActivity" 2>&1
$total = ([regex]::Match(($start -join "`n"), "TotalTime:\s*(\d+)")).Groups[1].Value
Start-Sleep -Seconds 4
$appPid = (& $adb -s $Device shell pidof $Pkg).Trim()
if (-not $appPid) { throw "App 未存活" }

# 前台校验：安装器/别的界面占前台时拒绝采基线（否则 dump 采到别的界面，证据整批作废）。
$focusDump = (& $adb -s $Device shell dumpsys window) -join "`n"
if ($focusDump -notmatch [regex]::Escape($Pkg)) {
    throw "前台不是 $Pkg（mCurrentFocus 未见应用，可能被安装器占用）；拒绝采基线"
}

$wm = (& $adb -s $Device shell wm size) -join " "
$density = (& $adb -s $Device shell wm density) -join " "
$sizeM = [regex]::Match($wm, "(\d+)x(\d+)")
if (-not $sizeM.Success) { throw "无法解析 wm size: $wm" }
$W = [int]$sizeM.Groups[1].Value; $H = [int]$sizeM.Groups[2].Value
$model = (& $adb -s $Device shell getprop ro.product.model).Trim()
$release = (& $adb -s $Device shell getprop ro.build.version.release).Trim()
$fingerprint = (& $adb -s $Device shell getprop ro.build.fingerprint).Trim()
$pkgDump = (& $adb -s $Device shell dumpsys package $Pkg 2>&1) -join "`n"
$verName = ([regex]::Match($pkgDump, "versionName=(\S+)")).Groups[1].Value
$verCode = ([regex]::Match($pkgDump, "versionCode=(\d+)")).Groups[1].Value
$lastUpdate = ([regex]::Match($pkgDump, "lastUpdateTime=([^\r\n]+)")).Groups[1].Value.Trim()
$apkItem = Get-Item $Apk
$apkHash = (Get-FileHash $Apk -Algorithm SHA256).Hash
$head = $BuildHead
$treeHead = (& git -C $repo rev-parse --short HEAD 2>$null)
if (-not $treeHead) { $treeHead = "unknown" }
$branchShown = if ($Branch) { $Branch } else { "未提供" }
$structureNote = if ($Structure) { $Structure } else { "未提供（本次未传 -Structure；脚本不臆造结构描述）" }

# 首次校准弹窗
$u = DumpUi "ui-first.xml"
if ($u.ok -and ($u.xml -match '跳过')) { Tap-Text $u.xml '^跳过' | Out-Null }

# 冻结航向（debug 包）
& $adb -s $Device shell am broadcast -a "$Pkg.DEBUG" --es cmd heading --ef value $InjectHeading 2>&1 | Out-Null
Start-Sleep -Milliseconds 800

$records = @()

# ---- 1) 地图态：左列 8 / 右列 10 ----
$mapUi = DumpUi "ui-map.xml"
if (-not $mapUi.ok) { throw "地图态 dump 失败" }
Shot "k01-map.png" | Out-Null
$nodes = Get-NodeInfos $mapUi.xml
$leftSlots = @(
    @{ n = "全地图工具"; x = 111; y = 233 },
    @{ n = "截屏"; x = 112; y = 754 },
    @{ n = "超清优化"; x = 112; y = 1412 },
    @{ n = "海拔查询"; x = 112; y = 1598 },
    @{ n = "罗盘"; x = 112; y = 1784 },
    @{ n = "路网"; x = 112; y = 1970 },
    @{ n = "等高线"; x = 112; y = 2156 },
    @{ n = "更多"; x = 112; y = 2342 }
)
$rightSlots = @(
    @{ n = "图层"; x = 1116; y = 652 },
    @{ n = "叠加"; x = 1116; y = 838 },
    @{ n = "山脉"; x = 1116; y = 1024 },
    @{ n = "水系"; x = 1116; y = 1210 },
    @{ n = "探索"; x = 1116; y = 1396 },
    @{ n = "指北针"; x = 1132; y = 1708 },
    @{ n = "放大+"; x = 1132; y = 1860 },
    @{ n = "缩小−"; x = 1132; y = 2014 },
    @{ n = "定位"; x = 1132; y = 2168 },
    @{ n = "3D"; x = 1134; y = 2320 }
)
function Match-Slots($mode, $group, $slots) {
    foreach ($s in $slots) {
        $sx = $s.x * $W / 1228
        $sy = $s.y * $H / 2700
        $best = $nodes | Where-Object { [math]::Abs($_.cx - $sx) -le 45 -and [math]::Abs($_.cy - $sy) -le 45 } |
            Sort-Object { [math]::Abs($_.cx - $sx) + [math]::Abs($_.cy - $sy) } | Select-Object -First 1
        $script:records += [pscustomobject]@{
            mode = $mode; group = $group; label = $s.n
            found = [bool]$best
            cx = $(if ($best) { $best.cx } else { "" })
            cy = $(if ($best) { $best.cy } else { "" })
            dx = $(if ($best) { [int]($best.cx - $sx) } else { "" })
            dy = $(if ($best) { [int]($best.cy - $sy) } else { "" })
            bounds = $(if ($best) { $best.bounds } else { "" })
        }
    }
}
Match-Slots "map" "left" $leftSlots
Match-Slots "map" "right" $rightSlots
$mapMissing = @($records | Where-Object { -not $_.found } | ForEach-Object { "$($_.group):$($_.label)" })

# ---- 2) 「更多」16 宫格 bottom sheet ----
$moreOk = Tap-Text $mapUi.xml '^更多$'
$moreMissing = @()
if ($moreOk) {
    $moreUi = DumpUi "ui-more.xml"
    if ($moreUi.ok) {
        Shot "k01-more.png" | Out-Null
        $moreTexts = Get-Texts $moreUi.xml
        $tools = @("罗盘", "超清地图", "看山脉", "看水系", "海拔查询", "天气", "历史影像", "等高线",
            "收藏点", "测量", "画笔", "钓点", "轨迹记录", "路网", "地理影像", "超清优化")
        foreach ($t in $tools) { if (-not ($moreTexts -contains $t)) { $moreMissing += $t } }
        & $adb -s $Device shell input keyevent 4 | Out-Null
        Start-Sleep -Milliseconds 800
    } else { $moreMissing = @("dump 失败") }
} else { $moreMissing = @("未找到「更多」入口") }

# ---- 3) 罗盘浮层态（截图为准，dump 可能只有 1 个节点）----
$compassOk = Tap-Text $mapUi.xml '^罗盘$'
$compassLabels = @()
if ($compassOk) {
    Start-Sleep -Milliseconds 1200
    Shot "k01-compass.png" | Out-Null
    $cu = DumpUi "ui-compass-mode.xml"
    if ($cu.ok) {
        $ct = Get-Texts $cu.xml
        $want = @("返回", "搜索", "图层", "隐藏", "地图", "实景", "坐南朝北", "指针开关",
            "截屏", "等高线", "罗盘复位", "罗盘锁定", "读盘", "贴地",
            "切换", "旋转", "缩放", "透明度", "天支线")
        $compassLabels = foreach ($t in $want) { [pscustomobject]@{ label = $t; found = ($ct -contains $t) } }
        # 指针开关 on/off 两张截图（是否生效由 AT/地图侧判定，基线只留证据）
        if ($ct -contains "指针开关") {
            Shot "k01-compass-needle-on.png" | Out-Null
            Tap-Text $cu.xml '^指针开关$' | Out-Null
            Shot "k01-compass-needle-off.png" | Out-Null
            Tap-Text $cu.xml '^指针开关$' | Out-Null
        }
        if ($ct -contains "隐藏") { Tap-Text $cu.xml '^隐藏$' | Out-Null }
    }
}
if ($compassOk) {
    Start-Sleep -Milliseconds 1200
    Shot "k01-map-after-hide.png" | Out-Null
    DumpUi "ui-map-after-hide.xml" | Out-Null
}

# ---- 4) 日志与产物 ----
& $adb -s $Device logcat -d | Set-Content -Encoding UTF8 (Join-Path $OutDir "logcat.txt")
$log = Get-Content -Raw (Join-Path $OutDir "logcat.txt")
$fatal = ([regex]::Matches($log, "FATAL EXCEPTION")).Count
$anr = ([regex]::Matches($log, "ANR in $Pkg")).Count

$records | Export-Csv -Path (Join-Path $OutDir "nodes-k01.csv") -NoTypeInformation -Encoding UTF8
$leftFound = @($records | Where-Object { $_.mode -eq "map" -and $_.group -eq "left" -and $_.found }).Count
$rightFound = @($records | Where-Object { $_.mode -eq "map" -and $_.group -eq "right" -and $_.found }).Count
$compassFound = @($compassLabels | Where-Object { $_.found }).Count
$moreFound = 16 - $moreMissing.Count
if (-not $PanelCropY) {
    $cropNote = "待客户端按 $Round 新布局实测；**不得沿用旧裁切窗**（旧窗已随上一轮 UI 失效）"
} else {
    $cropNote = "``$PanelCropY``（客户端实测后回填；旧窗作废）"
}
$cropBasisNote = if ($PanelCropBasis) { $PanelCropBasis } else { "未提供——重取时必须写明测量方法/取样点（谁量、哪张截图、怎么定面板上缘）" }

$rows = foreach ($f in (Get-ChildItem $OutDir -File | Where-Object { $_.Name -notin @("manifest.md", "SHA256SUMS.txt") } | Sort-Object Name)) {
    "{0}  {1}  {2}" -f (Get-FileHash $f.FullName -Algorithm SHA256).Hash, $f.Length, $f.Name
}
$rows | Set-Content -Encoding UTF8 (Join-Path $OutDir "SHA256SUMS.txt")

$gateBlock = $gateText.TrimEnd()

$manifest = @"
# Android 基线 · $Round

- 冻结时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')（UTC+8），LC测试
- 设备：$model / Android $release / ``$fingerprint``
- 屏幕：$wm；$density
- App：versionName $verName ($verCode)，lastUpdateTime $lastUpdate，pid $appPid
- 构建：``$branchShown`` HEAD ``$head``；APK ``$($apkItem.FullName)``（$($apkItem.Length) B），SHA256 ``$apkHash``
- 脚本运行树：``$repo`` HEAD ``$treeHead``（仅诊断；APK 的构建来源以上一行 ``-BuildHead`` 为准）
- 结构：$structureNote
- 冷启动：am start -W TotalTime = ${total} ms；注入 heading = $InjectHeading（debug 广播）
- 数值门禁：``$gateSummary`` + ``CARGO: PASS``（``numeric-gate.txt``；原始输出见文末）
- logcat：FATAL EXCEPTION **$fatal** / ANR **$anr**（``logcat.txt``）
- SW-006 面板裁切窗：$cropNote
- 裁切窗测量依据：$cropBasisNote
- 待跑清单：``docs/qa/k-01-pending.md``（设备放行后一次跑完；待跑不得写成已过）

## 采集结果（K-01 验收硬项）

| 组 | 预期 | 命中 | 缺失 |
|---|---|---|---|
| 地图态左列 | 8 | $leftFound | $(($mapMissing | Where-Object { $_ -like 'left:*' }) -join ' ; ') |
| 地图态右列 | 10 | $rightFound | $(($mapMissing | Where-Object { $_ -like 'right:*' }) -join ' ; ') |
| 「更多」16 宫格 | 16 | $moreFound | $($moreMissing -join ' ; ') |
| 罗盘态文字（dump 可读部分，非硬项） | 19 | $compassFound | 见 ``ui-compass-mode.xml``（自绘层不在 dump 属正常） |

## 冻结物（SHA256 见 ``SHA256SUMS.txt``）

$((Get-ChildItem $OutDir -File | Where-Object { $_.Name -notin @('manifest.md','SHA256SUMS.txt') } | Sort-Object Name | ForEach-Object { "- ``$($_.Name)``：$($_.Length) B" }) -join "`n")

## 限制

1. 罗盘浮层与圆盘为自绘/WebView 层，``uiautomator`` 只能拿到少量原生节点；圆心/直径/透明度以截图测量为准（``k01-compass.png``）。
2. 左列/右列按 ``docs/evidence/original-apk/orig-main.xml`` 的比例位匹配（容差 45px），不是按文案匹配；图标类控件也能计。
3. 历史基线目录（如 ``round-d5/`` 及更早）仅作历史对照，不得作为本轮基线或裁切参考；本轮基线以 ``$Round`` 为准。

## 数值门禁原始输出（F-01/F-02）

~~~text
$gateBlock
~~~
"@
$manifest | Set-Content -Encoding UTF8 (Join-Path $OutDir "manifest.md")

Write-Host "FROZEN: $OutDir"
Write-Host "left=$leftFound/8 right=$rightFound/10 more=$moreFound/16 compassText=$compassFound fatal=$fatal anr=$anr"
if ($mapMissing.Count -gt 0 -or $moreFound -lt 16 -or $fatal -gt 0 -or $anr -gt 0) { exit 1 }
exit 0
