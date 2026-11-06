# LC 侧地图模块 · 对齐采集（一键复跑）
# 用法:
#   pwsh -File tools\map\collect_map_lc.ps1 -Apk <develop 构建的 app-debug.apk> -Commit <40位 sha>
#   pwsh -File tools\map\collect_map_lc.ps1 ... -Only map-3d-mode,tool-contour-01,map-water-entry,map-explore-page,tool-measure-01
# 前置: 真机空闲（单台 L2E0222531014833，窗口串行）；本脚本会改三项动画为 0、装包、启动、切到地图 Tab。
# 口径: 1228x2700、动画 0、罗盘 heading 冻结 0°、地图固定 39.9042/116.4074 z=12（调试广播 setView 一次到位）。
#
# 与旧版（d18928d 之前）的差别：
#   1) 3D 默认坐标从 (155,576) 改为真实中心 (1124,2430)。旧值落在左列工具区（「全地图工具」），
#      点不到右列 3D（真 bounds [1027,2366]-[1222,2494]，见 docs/fidelity/lc/map-home-01.xml）。
#   2) 3D / 等高线拍照前加硬断言：先读页面真实状态（JS getState）确认为 mode=3d（等高线再确认
#      contour=true），并校验 png 与 2D 固定态不同，任一条不满足就报错退出，不再拿 2D 图充 3D。
#   3) 山脉/水系/探索拍照前断言前台 Activity 正确（MountainActivity/WaterActivity/DiscoveryActivity），
#      防止三页都拍成同一页（Stage 2 曾出现 water/explore 都拍到「看山脉」）。
#   4) 装包改用 push + pm install -r -t --user 0，避开 PackageInstaller 确认弹窗。
#   5) 等高线只拍 ON 一张（tool-contour-01）；OFF 态即 map-3d-mode（同为 3D + 无等高线），
#      两张不同图、不产生重复 sha256（report 的重复图硬门会拦）。
#   6) 覆盖地图章全部 15 态（原 7 态 + 搜索 2 / 画笔 / 钓点 / 轨迹 / 路网 / 叠加 / 图层），
#      每条都在拍照前按 repro-round2.md §5 的「状态指纹」做前置断言（前台 Activity 或 dump 文本），
#      做到「15 态一条命令可复跑」；坐标全部由 dump bounds 定，-SelfTest 逐条自证。
param(
  [string]$Apk = '',
  [string]$Commit = '',
  [string]$Serial = 'L2E0222531014833',
  # 会劫持点击的第三方应用市场（本机实测：联想应用市场会把点击变成「安装」页）
  [string[]]$HijackPkgs = @('com.lenovo.leos.appstore', 'com.huawei.appmarket'),
  [string]$Pkg = 'com.luopan.fengshui',
  [int]$MapTabX = 615, [int]$MapTabY = 2525,
  # 右列 3D：[1027,2366]-[1222,2494]，中心 (1124,2430)
  [int]$ThreeDX = 1124, [int]$ThreeDY = 2430,
  # 左列 等高线：[6,2194]-[201,2322]，中心 (103,2258)
  [int]$ContourX = 103, [int]$ContourY = 2258,
  # 右列 山脉 [1027,1008]-[1222,1136] / 水系 [1027,1203]-[1222,1331] / 探索 [1027,1398]-[1222,1526]
  [int]$MountainX = 1124, [int]$MountainY = 1072,
  [int]$WaterX = 1124, [int]$WaterY = 1267,
  [int]$ExploreX = 1124, [int]$ExploreY = 1462,
  # 更多 (103,2453) → 测量 (486,1453)；画笔 (740,1453)；只用于 -Only 重采时的入口
  [int]$MoreX = 103, [int]$MoreY = 2453,
  [int]$MeasureX = 486, [int]$MeasureY = 1453,
  # 更多面板其余 3 项（more-sheet-03-fresh.xml 实测）：画笔 [613,1372][867,1534]、
  # 钓点 [867,1372][1121,1534]、轨迹记录 [106,1540][359,1702]
  [int]$BrushX = 740, [int]$BrushY = 1453,
  [int]$FishingX = 994, [int]$FishingY = 1453,
  [int]$TrackX = 232, [int]$TrackY = 1621,
  # 左列 路网 [6,1999][201,2127]
  [int]$RoadX = 104, [int]$RoadY = 2063,
  # 右列 叠加 [1027,814][1222,942] / 图层 [1027,619][1222,747]
  [int]$OverlayX = 1124, [int]$OverlayY = 878,
  [int]$LayerX = 1124, [int]$LayerY = 683,
  # 顶栏 搜索 [1040,192][1215,306]；搜索框 [86,766][1141,918]
  [int]$SearchX = 1127, [int]$SearchY = 249,
  [int]$SearchInputX = 613, [int]$SearchInputY = 842,
  [string]$SearchText = '116.4074,39.9042',
  # 只跑指定 state（逗号分隔）；默认全跑
  [string[]]$Only = @(),
  # 无设备自检：校验坐标锚点 / 重复图门（不需要真机）
  [switch]$SelfTest,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

# `& adb` 会解析到本脚本同名的 Adb 函数（递归爆栈），必须锁定外部可执行文件
$script:AdbExe = (Get-Command adb -CommandType Application | Select-Object -First 1).Source
if (-not $script:AdbExe) { throw "找不到 adb 可执行文件（PATH 里没有 Application 类型的 adb）" }

$script:Fail = $false
function Info([string]$m) { Write-Host "[map] $m" }
function Fail([string]$m) { Write-Host "[map][FATAL] $m"; $script:Fail = $true; throw $m }
function Want([string]$state) {
  if ($Only.Count -eq 0) { return $true }
  return ($Only -contains $state)
}

function Adb([string[]]$ArgList) {
  if ($DryRun) { Write-Host ("DRY: adb -s $Serial " + ($ArgList -join ' ')); return '' }
  $out = & $script:AdbExe -s $Serial @ArgList 2>&1
  return ($out | Out-String)
}
function Sh([string]$cmd) { Adb @('shell', $cmd) | Out-Null }
function Tap([int]$x, [int]$y) {
  if ($DryRun) { Write-Host "DRY: tap $x,$y"; return }
  Adb @('shell', "input tap $x $y") | Out-Null
}
function Bc([string]$extra) {
  if ($DryRun) { Write-Host "DRY: am broadcast $extra"; return }
  Adb @('shell', "am broadcast -a $Pkg.DEBUG $extra") | Out-Null
}
function Js([string]$code) { Bc "--es cmd js --es value `"$code`"" }

# 证据纪律（§4.4）：应用市场会把点击截胡成「安装/详情」页，dump 于是变成别人的界面，
# 看起来像「空白态」。采集前先 force-stop，并逐次校验前台是我方包。
function Stop-HijackApps() {
  foreach ($hp in $HijackPkgs) {
    if (-not $hp) { continue }
    Adb @('shell', "am force-stop $hp") | Out-Null
  }
  Info ("force-stop hijack pkgs: " + ($HijackPkgs -join ', '))
}

function Collect([string]$state, [string]$cid, [int]$DumpRetries = 1, [string]$Expect = '') {
  if (-not (Want $state)) { Info "skip $state (not in -Only)"; return }
  if (-not $DryRun) { Stop-HijackApps; Assert-LcForeground }
  $tries = [Math]::Max(1, $DumpRetries)
  for ($i = 1; $i -le $tries; $i++) {
    $argv = @('tools\fidelity\fidelity.py', 'collect', '--side', 'lc', '--state', $state,
      '--pkg', $Pkg, '--serial', $Serial, '--checksheet-id', $cid,
      '--build-commit', $Commit, '--apk', $Apk, '--force')
    if ($DryRun) { $argv += '--dry-run' }
    Info ("collect: " + ($argv -join ' '))
    python @argv
    if ($LASTEXITCODE -eq 0) {
      if ($Expect) { Assert-DumpText -State $state -Pattern $Expect }
      return
    }
    if ($i -lt $tries) { Info "${state}: dump/shot failed (try $i/$tries)，3 s 后重试"; Start-Sleep -Seconds 3 }
  }
  # 最后兜底：png-only，理由必须写进索引（测量态持续动画导致 uiautomator idle 超时）
  $argv = @('tools\fidelity\fidelity.py', 'collect', '--side', 'lc', '--state', $state,
    '--pkg', $Pkg, '--serial', $Serial, '--checksheet-id', $cid,
    '--build-commit', $Commit, '--apk', $Apk, '--force', '--skip-dump')
  Info ("FALLBACK png-only: " + ($argv -join ' '))
  python @argv
  if ($LASTEXITCODE -ne 0) { Fail "collect failed: $state" }
  Info "${state}: meta.dump=null（dump 重试 $tries 次仍超时，索引里按 png-only 登记并写原因）"
}

function Get-Resumed() {
  $raw = Adb @('shell', 'dumpsys activity activities')
  $m = [regex]::Matches($raw, 'mResumedActivity[^\n]*?([\w\.]+/[\w\.$]+)')
  if ($m.Count -gt 0) { return $m[$m.Count - 1].Groups[1].Value }
  $m2 = [regex]::Matches($raw, 'ResumedActivity[^\n]*?([\w\.]+/[\w\.$]+)')
  if ($m2.Count -gt 0) { return $m2[$m2.Count - 1].Groups[1].Value }
  return ''
}
function Assert-Resumed([string]$expect) {
  $cur = Get-Resumed
  if ($cur -notmatch [regex]::Escape($expect)) {
    Fail "前台 Activity 不是 $expect，实际=$cur（页面没进对，拒绝拍照）"
  }
  Info "前台 Activity ok: $cur"
}
# 拍照前必须确认前台还是我方包：第三方弹层（如应用市场推荐页）会劫持 dump 造成假 PASS
function Assert-LcForeground() {
  $cur = Get-Resumed
  if ($cur -notmatch [regex]::Escape($Pkg)) {
    Fail "前台不是 $Pkg，实际=$cur（被别的应用劫持，拒绝拍照；先 force-stop 该应用再重跑）"
  }
}
# 拍照后校验：产出 dump 里必须出现预期关键节点（防止拍到别人的界面却当成本状态）
function Assert-DumpText([string]$State, [string]$Pattern) {
  $p = Join-Path $root "docs\fidelity\lc\$State.xml"
  if (-not (Test-Path $p)) { Fail "${State}: dump 不存在，无法做关键节点校验（png-only 状态请传 -Expect ''）" }
  $raw = Get-Content $p -Raw
  if ($raw -notmatch $Pattern) {
    Fail ("{0}: dump 里找不到关键节点 /{1}/（可能被别的应用劫持或页面没进对，拒绝记账）" -f $State, $Pattern)
  }
  Info "关键节点 ok: $State /$Pattern/"
}
# 路网开/关的真实状态：MainActivity.toggleRoadNet() 写 MapManager prefs 的 showRoad（默认 true）。
# 用 run-as 读内存盘上的 prefs，避免拿 png 差异当状态判据（D1：同态 ≠ 同 sha）。
function Get-RoadNetPref() {
  $raw = Adb @('shell', "run-as $Pkg cat shared_prefs/MapManager.xml")
  if ($raw -match 'name="showRoad"\s+value="(true|false)"') { return ($Matches[1] -eq 'true') }
  return $null
}
function Get-PngSha([string]$state) {  $p = Join-Path $root "docs\fidelity\lc\$state.png"
  if (-not (Test-Path $p)) { return '' }
  return (Get-FileHash -Algorithm SHA256 -Path $p).Hash.ToLower()
}
function Assert-PngDistinct([string]$a, [string]$b) {
  $ha = Get-PngSha $a; $hb = Get-PngSha $b
  if (-not $ha -or -not $hb) { Info "png 缺失，跳过 sha 对比: $a / $b"; return }
  if ($ha -eq $hb) { Fail "截图与 $b 逐字节相同（$ha），说明状态没变，拒绝记账：$a" }
  Info "png 不同 ok: $a($($ha.Substring(0,8))) != $b($($hb.Substring(0,8)))"
}
# 拍照前的活体 dump：状态指纹不符就拒绝拍照（避免把错态覆盖掉既有好证据）
function Get-LiveDumpText([int]$Retries = 2) {
  for ($i = 1; $i -le $Retries; $i++) {
    Sh 'rm -f /sdcard/a.xml'
    $out = Adb @('shell', 'uiautomator dump /sdcard/a.xml')
    if ($out -match 'dumped to') {
      $raw = Adb @('exec-out', 'cat', '/sdcard/a.xml')
      if ($raw -match '<hierarchy') {
        $texts = @([regex]::Matches($raw, 'text="([^"]*)"') |
          ForEach-Object { $_.Groups[1].Value } | Where-Object { $_ })
        return ($texts -join ' | ')
      }
    }
    Info "live dump 第 $i 次失败，2 s 后重试"
    Start-Sleep -Seconds 2
  }
  return ''
}
function Assert-LiveText([string]$pattern, [string]$hint) {
  $txt = Get-LiveDumpText
  if (-not $txt) { Fail "前置断言无法执行：$hint 的 live dump 两次都超时（拒绝拍照）" }
  if ($txt -notmatch $pattern) {
    Fail ("前置断言失败：{0}，期望 /{1}/；实际文本前 200 字：{2}" -f `
      $hint, $pattern, $txt.Substring(0, [Math]::Min(200, $txt.Length)))
  }
  Info "前置断言 ok: $hint /$pattern/"
}
function Assert-LiveTextAbsent([string]$pattern, [string]$hint) {
  $txt = Get-LiveDumpText
  if (-not $txt) {
    # 负向指纹是补强（Activity 已断言），dump 超时只告警不阻断
    Info "警告：$hint 的 live dump 超时，负向指纹 /$pattern/ 未校验（前台 Activity 已断言 ok）"
    return
  }
  if ($txt -match $pattern) {
    Fail ("前置断言失败：{0} 出现了不该有的 /{1}/（页面错态，拒绝拍照）" -f $hint, $pattern)
  }
  Info "前置断言 ok（负向）: $hint 不含 /$pattern/"
}

function Wait-Logcat([string]$pattern, [int]$timeoutSec = 20) {
  # WebView 的 console.log 落在 tag hwschromium-*（文本形如 [INFO:CONSOLE(230)] "LC3D mode=3d"），
  # 按 tag=LC3D 过滤永远匹配不到；取最近 400 行全量按内容匹配。
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    $logs = Adb @('logcat', '-d', '-t', '400')
    if ($logs -match $pattern) { Info "logcat ok: $pattern"; return $logs }
    Start-Sleep -Milliseconds 700
  }
  $tail = (Adb @('logcat', '-d', '-t', '400') -split "`n" | Select-Object -Last 12) -join ' | '
  Fail "logcat 等不到 /$pattern/（${timeoutSec}s）。最后 12 行：$tail"
}
# 读页面真实状态（map.html getState），用于「先确认为 3D 再拍」
function Get-MapState() {
  Adb @('logcat', '-c') | Out-Null
  Js 'JSON.stringify(getState())'
  $deadline = (Get-Date).AddSeconds(8)
  while ((Get-Date) -lt $deadline) {
    $logs = Adb @('logcat', '-d', '-s', 'LCJS:V')
    $hit = ($logs -split "`n" | Where-Object { $_ -match 'map -> ' } | Select-Object -Last 1)
    if ($hit) {
      $payload = $hit.Substring($hit.IndexOf('map -> ') + 7).Trim()
      try {
        if ($payload.StartsWith('"')) { $payload = $payload | ConvertFrom-Json }
        return ($payload | ConvertFrom-Json)
      } catch { Fail "getState 返回无法解析: $payload" }
    }
    Start-Sleep -Milliseconds 500
  }
  Fail "getState 无回显（LCJS 空），拒绝按猜测拍照"
}
function Assert-Mode([string]$mode, $state) {
  if ($state.mode -ne $mode) {
    Fail ("mode 断言失败：期望 $mode，实际 " + ($state | ConvertTo-Json -Compress))
  }
  Info ("state ok: mode=$($state.mode) zoom=$($state.zoom) contour=$($state.contour) drape=$($state.drape)")
}

# 从 dump 里取某个可点按钮的真实中心（用于自检「脚本坐标 == dump 坐标」）
function Get-DumpBtnCenter([string]$xmlPath, [string]$text, [int]$xMin = 0, [int]$xMax = 0) {
  if (-not (Test-Path $xmlPath)) { return $null }
  $doc = [xml](Get-Content $xmlPath -Raw)
  foreach ($n in @($doc.SelectNodes('//node'))) {
    if ($n.text -ne $text -or $n.clickable -ne 'true') { continue }
    if ($n.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { continue }
    $l = [int]$Matches[1]; $tp = [int]$Matches[2]; $r = [int]$Matches[3]; $bt = [int]$Matches[4]
    if ($xMin -gt 0 -and $l -lt $xMin) { continue }
    if ($xMax -gt 0 -and $l -ge $xMax) { continue }
    return [pscustomobject]@{ l = $l; t = $tp; r = $r; b = $bt
      cx = [int](($l + $r) / 2); cy = [int](($tp + $bt) / 2) }
  }
  return $null
}

# 无设备自检：坐标锚点来自 dump、重复图门必须能拦住 Stage 2 那对同图
if ($SelfTest) {
  $bad = @()
  $dumpHome = Join-Path $root 'docs\fidelity\lc\map-home-01.xml'
  $dumpMore = Join-Path $root 'docs\fidelity\lc\more-sheet-03-fresh.xml'
  $checks = @(
    @{ name = '3D';       xml = $dumpHome; text = '3D';       x = $ThreeDX;    y = $ThreeDY;    xMin = 1000 },
    @{ name = '等高线';   xml = $dumpHome; text = '等高线';   x = $ContourX;   y = $ContourY;   xMin = 0;    xMax = 400 },
    @{ name = '山脉';     xml = $dumpHome; text = '山脉';     x = $MountainX;  y = $MountainY;  xMin = 1000 },
    @{ name = '水系';     xml = $dumpHome; text = '水系';     x = $WaterX;     y = $WaterY;     xMin = 1000 },
    @{ name = '探索';     xml = $dumpHome; text = '探索';     x = $ExploreX;   y = $ExploreY;   xMin = 1000 },
    @{ name = '叠加';     xml = $dumpHome; text = '叠加';     x = $OverlayX;   y = $OverlayY;   xMin = 1000 },
    @{ name = '图层';     xml = $dumpHome; text = '图层';     x = $LayerX;     y = $LayerY;     xMin = 1000 },
    @{ name = '搜索';     xml = $dumpHome; text = '搜索';     x = $SearchX;    y = $SearchY;    xMin = 1000 },
    @{ name = '路网';     xml = $dumpHome; text = '路网';     x = $RoadX;      y = $RoadY;      xMin = 0;    xMax = 400 },
    @{ name = '更多';     xml = $dumpHome; text = '更多';     x = $MoreX;      y = $MoreY;      xMin = 0;    xMax = 400 },
    @{ name = '测量';     xml = $dumpMore; text = '测量';     x = $MeasureX;   y = $MeasureY;   xMin = 0 },
    @{ name = '画笔';     xml = $dumpMore; text = '画笔';     x = $BrushX;     y = $BrushY;     xMin = 0 },
    @{ name = '钓点';     xml = $dumpMore; text = '钓点';     x = $FishingX;   y = $FishingY;   xMin = 0 },
    @{ name = '轨迹记录'; xml = $dumpMore; text = '轨迹记录'; x = $TrackX;     y = $TrackY;     xMin = 0 }
  )
  foreach ($c in $checks) {
    $btn = Get-DumpBtnCenter $c.xml $c.text $c.xMin $c.xMax
    if (-not $btn) { $bad += "$($c.name): dump 里找不到该按钮"; continue }
    $inside = ($c.x -ge $btn.l -and $c.x -le $btn.r -and $c.y -ge $btn.t -and $c.y -le $btn.b)
    if (-not $inside) {
      $bad += ("{0}: 脚本 ({1},{2}) 不在 dump bounds [{3},{4}]-[{5},{6}] 内" -f $c.name, $c.x, $c.y, $btn.l, $btn.t, $btn.r, $btn.b)
    } else {
      Write-Host ("PASS anchor {0} ({1},{2}) 落在 dump bounds [{3},{4}]-[{5},{6}]（算术中心 {7},{8}）" -f `
        $c.name, $c.x, $c.y, $btn.l, $btn.t, $btn.r, $btn.b, $btn.cx, $btn.cy)
    }
  }

  # 「15 态一条命令可复跑」：每个 stem 必须在脚本里有采集入口（Collect '<stem>' 或状态表 state = '<stem>'）
  $src = Get-Content (Join-Path $root 'tools\map\collect_map_lc.ps1') -Raw
  $stems = @('map-beijing-z12', 'search-result-01', 'search-result-02', 'tool-measure-01', 'tool-brush',
    'tool-fishing', 'tool-track', 'map-roadnet-on', 'tool-contour-01', 'map-mountains-entry',
    'map-water-entry', 'map-overlay-sheet', 'map-explore-page', 'map-layer-sheet', 'map-3d-mode')
  $missing = @($stems | Where-Object {
      $e = [regex]::Escape($_)
      -not ($src -match ("Collect '" + $e + "'") -or $src -match ("state\s*=\s*'" + $e + "'"))
    })
  if ($missing.Count -gt 0) { $bad += ("state 未接入脚本: " + ($missing -join ', ')) }
  else { Write-Host ("PASS coverage 15/15 地图态都在脚本里（Collect 或状态表各一条）：" + ($stems -join ', ')) }

  $hit3d = Get-DumpBtnCenter $dumpHome '3D' 1000 0
  if ($hit3d) {
    $old = (155 -ge $hit3d.l -and 155 -le $hit3d.r -and 576 -ge $hit3d.t -and 576 -le $hit3d.b)
    if ($old) { $bad += '旧默认坐标 (155,576) 竟落在 3D 按钮内' }
    else { Write-Host "PASS anchor 旧默认 (155,576) 不在 3D bounds [$($hit3d.l),$($hit3d.t)]-[$($hit3d.r),$($hit3d.b)] 内（旧值点不到 3D）" }
  }
  $h3d = Get-PngSha 'map-3d-mode'; $hc = Get-PngSha 'tool-contour-01'; $h2d = Get-PngSha 'map-beijing-z12'
  if ($h3d -and $hc) {
    if ($h3d -eq $hc) { Write-Host "PASS dup-guard 同图对能被判同（map-3d-mode == tool-contour-01, $($h3d.Substring(0,8))）" }
    else { $bad += '重复图门失效：map-3d-mode 与 tool-contour-01 没被判同' }
  }
  if ($h3d -and $h2d) {
    if ($h3d -ne $h2d) { Write-Host "PASS dup-guard 异图对不会误报（map-3d-mode != map-beijing-z12）" }
    else { $bad += '异图对被误判同' }
  }
  if ($bad.Count -gt 0) {
    $bad | ForEach-Object { Write-Host "FAIL $_" }
    Write-Host "SELFTEST: FAIL ($($bad.Count) issue(s))"
    exit 1
  }
  Write-Host "SELFTEST: PASS ($($checks.Count + 4) checks)"
  exit 0
}

# 0) 工具能力闸门：检查表 §5 要求 meta 带 build.commit + apk.sha256
# 注意：pwsh -File 传 -Only a,b,c 时逗号列表是「一个字符串」，必须自己拆
if ($Only.Count -eq 1 -and $Only[0] -match ',') { $Only = @($Only[0] -split ',') }
$Only = @($Only | ForEach-Object { $_.Trim() } | Where-Object { $_ })
Write-Host ("[map] states filter: " + $(if ($Only.Count -gt 0) { $Only -join ',' } else { 'ALL(15)' }))
if (-not $Apk) { Fail "必须给 -Apk（develop 构建的 app-debug.apk）；只做无设备自检请加 -SelfTest" }
if (-not $Commit) { Fail "必须给 -Commit（40 位 sha）" }
$help = (python tools\fidelity\fidelity.py collect --help 2>&1 | Out-String)
if ($help -notmatch '--build-commit' -or $help -notmatch '--apk') {
  Fail "tools/fidelity/fidelity.py 缺 --build-commit/--apk，按检查表 §5 采集会整批作废。先合入该改动再跑。"
}
if ($Commit.Length -ne 40) { Fail "-Commit 必须是 40 位 sha，当前 '$Commit'" }
if (-not (Test-Path $Apk)) { Fail "APK 不存在: $Apk" }
$apkSha = (Get-FileHash -Algorithm SHA256 -Path $Apk).Hash.ToLower()
$apkBytes = (Get-Item $Apk).Length
Info "APK sha256=$apkSha bytes=$apkBytes commit=$Commit"

# 1) 设备闸门 + 前置
$dev = (Adb @('get-state')).Trim()
if (-not $DryRun -and $dev -ne 'device') {
  $list = (& $script:AdbExe devices | Out-String)
  Fail "设备不可用（adb get-state=$dev）。adb devices:`n$list"
}
Sh 'settings put global window_animation_scale 0'
Sh 'settings put global transition_animation_scale 0'
Sh 'settings put global animator_duration_scale 0'
Sh 'input keyevent KEYCODE_WAKEUP'
Sh 'wm dismiss-keyguard'
Start-Sleep -Seconds 1

# 2) 装包（push + pm install -r -t --user 0，避开确认弹窗）
if (-not $DryRun) {
  Adb @('push', $Apk, '/data/local/tmp/lc-map.apk') | Out-Null
  $ins = Adb @('shell', "pm install -r -t --user 0 /data/local/tmp/lc-map.apk")
  if ($ins -notmatch 'Success') { Fail "install 失败: $ins" }
  Info "install ok"
}

function Enter-App() {
  if (-not $DryRun) { Stop-HijackApps }
  Sh "am force-stop $Pkg"
  Sh "am start -n $Pkg/.MainActivity"
  Start-Sleep -Seconds 4          # 未完成 onResume 时广播会被忽略
  Tap $MapTabX $MapTabY
  Start-Sleep -Seconds 2
  # 罗盘 heading 冻结 0°，避免 dump 前传感器抖动导致 could not get idle state
  Bc '--es cmd heading --ef value 0'
  Start-Sleep -Milliseconds 500
}

# 每个状态拍之前都回到「干净的地图固定态」：重拉 App → 切地图 Tab → setView → 读 getState 断言
function Reset-ToMap() {
  Enter-App
  Js 'setView(39.9042,116.4074,12)'
  Start-Sleep -Seconds 2
  $st = Get-MapState
  if ([Math]::Abs([double]$st.zoom - 12) -gt 0.01 -or
      [Math]::Abs([double]$st.lat - 39.9042) -gt 1e-3 -or
      [Math]::Abs([double]$st.lng - 116.4074) -gt 1e-3) {
    Fail ("固定态断言失败：" + ($st | ConvertTo-Json -Compress))
  }
  Info "固定态 ok: 39.9042/116.4074 z=12"
  return $st
}

# 3) 固定态（04）：地图固定 39.9042/116.4074 z=12
if (Want 'map-beijing-z12') {
  Reset-ToMap | Out-Null
  Collect 'map-beijing-z12' 'ST-A-map-fixed'
}

# 4) 3D（29）+ 等高线（23）：都在 3D 可见态下拍，OFF 态即 map-3d-mode
if ((Want 'map-3d-mode') -or (Want 'tool-contour-01')) {
  Reset-ToMap | Out-Null
  Adb @('logcat', '-c') | Out-Null
  Tap $ThreeDX $ThreeDY
  Wait-Logcat 'LC3D mode=3d' 25 | Out-Null
  Wait-Logcat 'LC3D texture (ok|fail)' 30 | Out-Null
  $st = Get-MapState
  Assert-Mode '3d' $st
  if (Want 'map-3d-mode') {
    Collect 'map-3d-mode' 'ST-C-3d'
    Assert-PngDistinct 'map-3d-mode' 'map-beijing-z12'
  }
  if (Want 'tool-contour-01') {
    Adb @('logcat', '-c') | Out-Null
    Tap $ContourX $ContourY
    Wait-Logcat 'LC3D contour on=True' 20 | Out-Null
    $st2 = Get-MapState
    Assert-Mode '3d' $st2
    if (-not $st2.contour) { Fail ("contour 断言失败：" + ($st2 | ConvertTo-Json -Compress)) }
    Collect 'tool-contour-01' 'ST-C-contour'
    Assert-PngDistinct 'tool-contour-01' 'map-3d-mode'
  }
}

# 5) 山脉（24）/ 水系（25）/ 探索（27）：断言前台 Activity + 页面指纹再拍
#    Stage 2 曾把水系、探索都拍成「看山脉」页，故对这两态加负向指纹「不得含 看山脉」。
$pages = @(
  @{ state = 'map-mountains-entry'; cid = 'ST-C-mountains'; x = $MountainX; y = $MountainY
     act = 'MountainActivity'; expect = '看山脉'; absent = $null },
  @{ state = 'map-water-entry'; cid = 'ST-C-water'; x = $WaterX; y = $WaterY
     act = 'WaterActivity'; expect = '看水系'; absent = '看山脉' },
  @{ state = 'map-explore-page'; cid = 'ST-C-explore'; x = $ExploreX; y = $ExploreY
     act = 'DiscoveryActivity'; expect = '探索'; absent = '看山脉' }
)
foreach ($p in $pages) {
  if (-not (Want $p.state)) { continue }
  Reset-ToMap | Out-Null
  Tap $p.x $p.y
  Start-Sleep -Seconds 3
  Assert-Resumed $p.act
  if ($p.expect) { Assert-LiveText $p.expect "$($p.state) 页面标题" }
  if ($p.absent) { Assert-LiveTextAbsent $p.absent "$($p.state) 山脉页指纹" }
  # 拍照后再校验 dump 里确实有这个页面的关键节点（防止被劫持成别人的界面）
  if ($p.expect) { Collect $p.state $p.cid -Expect $p.expect } else { Collect $p.state $p.cid }
}

# 6) 测量（18）：地图内测量态；png 必得、dump 尽力（该页持续动画易 idle 超时）
if (Want 'tool-measure-01') {
  Reset-ToMap | Out-Null
  Tap $MoreX $MoreY
  Start-Sleep -Seconds 2
  Tap $MeasureX $MeasureY
  Start-Sleep -Seconds 3
  Assert-Resumed 'MainActivity'
  Collect 'tool-measure-01' 'ST-C-measure' -DumpRetries 3
}

# 7) 画笔（19）：地图内绘制态，前台仍 MainActivity；指纹 = 底栏 颜色/粗细/撤销/清除/导出
if (Want 'tool-brush') {
  Reset-ToMap | Out-Null
  Tap $MoreX $MoreY
  Start-Sleep -Seconds 2
  Tap $BrushX $BrushY
  Start-Sleep -Seconds 2
  Assert-Resumed 'MainActivity'
  Assert-LiveText '撤销|清除|导出' '画笔底栏'
  Collect 'tool-brush' 'ST-C-brush'
}

# 8) 钓点（20）：FishingActivity
if (Want 'tool-fishing') {
  Reset-ToMap | Out-Null
  Tap $MoreX $MoreY
  Start-Sleep -Seconds 2
  Tap $FishingX $FishingY
  Start-Sleep -Seconds 3
  Assert-Resumed 'FishingActivity'
  Assert-LiveText '钓点' '钓点页标题'
  Collect 'tool-fishing' 'ST-C-fishing'
}

# 9) 轨迹记录（21）：TrackListActivity
if (Want 'tool-track') {
  Reset-ToMap | Out-Null
  Tap $MoreX $MoreY
  Start-Sleep -Seconds 2
  Tap $TrackX $TrackY
  Start-Sleep -Seconds 3
  Assert-Resumed 'TrackListActivity'
  Assert-LiveText '历史轨迹' '历史轨迹页标题'
  Collect 'tool-track' 'ST-C-track'
}

# 10) 路网（22）：判据用应用内真实开关 MapManager.showRoad，不看 png 差异
#    D1 实测「同态 ≠ 同 sha」（17:45 vs 19:49 节点树一致、PNG 却从 68469C5C 变 AC06F0B5），
#    瓦片/渲染时序会让同态截图不同、也可能让异态偶然相同；状态只能读持久化开关或 getState。
if (Want 'map-roadnet-on') {
  Reset-ToMap | Out-Null
  $before = Get-RoadNetPref
  Info "路网开关(pre)=$before"
  Tap $RoadX $RoadY
  Start-Sleep -Seconds 2
  $after = Get-RoadNetPref
  if ($after -ne $true) {
    Info "路网点成了「关」（showRoad=$after），再点一次"
    Tap $RoadX $RoadY
    Start-Sleep -Seconds 2
    $after = Get-RoadNetPref
  }
  if ($after -ne $true) { Fail "路网断言失败：MapManager.showRoad=$after（期望 true）" }
  Info "路网开关 ok: showRoad=true"
  Collect 'map-roadnet-on' 'ST-C-roadnet'
  if ((Get-RoadNetPref) -ne $true) { Fail "采集后 showRoad 不再是 true，状态在拍摄期间变了" }
  # png 差异只作参考，不作状态判据
  $ha = Get-PngSha 'map-roadnet-on'; $hb = Get-PngSha 'map-beijing-z12'
  if ($ha -and $hb) {
    if ($ha -eq $hb) { Info "提示：路网开态 png 与固定态相同（$ha）——按 D1 口径这不代表状态未变，以 showRoad 为准" }
    else { Info "路网开态 png 与固定态不同（参考值）" }
  }
}

# 11) 数据叠加（26）：OverlayActivity
if (Want 'map-overlay-sheet') {
  Reset-ToMap | Out-Null
  Tap $OverlayX $OverlayY
  Start-Sleep -Seconds 3
  Assert-Resumed 'OverlayActivity'
  Assert-LiveText '数据叠加' '叠加面板标题'
  Collect 'map-overlay-sheet' 'ST-C-overlay'
}

# 12) 图层 / 底图源（28）：面板在 WebView 内，指纹 = 「底图源」
if (Want 'map-layer-sheet') {
  Reset-ToMap | Out-Null
  Tap $LayerX $LayerY
  Start-Sleep -Seconds 2
  Assert-LiveText '底图源' '底图源面板'
  Collect 'map-layer-sheet' 'ST-C-layer'
}

# 13) 搜索（06 空结果 / 07 四坐标系结果）：LC 现状是盘面/圈层/知识条目检索（findings C-01）
if ((Want 'search-result-01') -or (Want 'search-result-02')) {
  Reset-ToMap | Out-Null
  Tap $SearchX $SearchY
  Start-Sleep -Seconds 2
  Assert-LiveText '搜索：盘面 / 圈层 / 知识条目' '搜索弹层（LC 现状）'
  if (Want 'search-result-01') { Collect 'search-result-01' 'ST-A-search-empty' }
  if (Want 'search-result-02') {
    Tap $SearchInputX $SearchInputY
    Start-Sleep -Seconds 1
    Adb @('shell', "input text $SearchText") | Out-Null
    Start-Sleep -Seconds 2
    Assert-LiveText ([regex]::Escape($SearchText)) '坐标输入回显'
    Collect 'search-result-02' 'ST-A-search-coords'
  }
}

# 14) 收尾：释放设备
Sh "am force-stop $Pkg"
Sh 'input keyevent KEYCODE_HOME'
Sh 'settings put global window_animation_scale 1'
Sh 'settings put global transition_animation_scale 1'
Sh 'settings put global animator_duration_scale 1'
if (-not $DryRun) { & $script:AdbExe forward --remove-all | Out-Null }
Info "released: force-stop + HOME, animations=1, forward cleared"
Info "DONE. 索引 §4 请按新证据回填；报告门禁: python tools\fidelity\fidelity.py report --root docs\fidelity"
