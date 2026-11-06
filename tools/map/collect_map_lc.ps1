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
param(
  [string]$Apk = '',
  [string]$Commit = '',
  [string]$Serial = 'L2E0222531014833',
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
  # 只跑指定 state（逗号分隔）；默认全跑
  [string[]]$Only = @(),
  # 无设备自检：校验坐标锚点 / 重复图门（不需要真机）
  [switch]$SelfTest,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$script:Fail = $false
function Info([string]$m) { Write-Host "[map] $m" }
function Fail([string]$m) { Write-Host "[map][FATAL] $m"; $script:Fail = $true; throw $m }
function Want([string]$state) {
  if ($Only.Count -eq 0) { return $true }
  return ($Only -contains $state)
}

function Adb([string[]]$ArgList) {
  if ($DryRun) { Write-Host ("DRY: adb -s $Serial " + ($ArgList -join ' ')); return '' }
  $out = & adb -s $Serial @ArgList 2>&1
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

function Collect([string]$state, [string]$cid, [int]$DumpRetries = 1) {
  if (-not (Want $state)) { Info "skip $state (not in -Only)"; return }
  $tries = [Math]::Max(1, $DumpRetries)
  for ($i = 1; $i -le $tries; $i++) {
    $argv = @('tools\fidelity\fidelity.py', 'collect', '--side', 'lc', '--state', $state,
      '--pkg', $Pkg, '--serial', $Serial, '--checksheet-id', $cid,
      '--build-commit', $Commit, '--apk', $Apk, '--force')
    if ($DryRun) { $argv += '--dry-run' }
    Info ("collect: " + ($argv -join ' '))
    python @argv
    if ($LASTEXITCODE -eq 0) { return }
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
function Get-PngSha([string]$state) {
  $p = Join-Path $root "docs\fidelity\lc\$state.png"
  if (-not (Test-Path $p)) { return '' }
  return (Get-FileHash -Algorithm SHA256 -Path $p).Hash.ToLower()
}
function Assert-PngDistinct([string]$a, [string]$b) {
  $ha = Get-PngSha $a; $hb = Get-PngSha $b
  if (-not $ha -or -not $hb) { Info "png 缺失，跳过 sha 对比: $a / $b"; return }
  if ($ha -eq $hb) { Fail "截图与 $b 逐字节相同（$ha），说明状态没变，拒绝记账：$a" }
  Info "png 不同 ok: $a($($ha.Substring(0,8))) != $b($($hb.Substring(0,8)))"
}
function Wait-Logcat([string]$pattern, [int]$timeoutSec = 20) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    $logs = Adb @('logcat', '-d', '-s', 'LC3D:V', 'LCJS:V', 'LCDEBUG:V')
    if ($logs -match $pattern) { Info "logcat ok: $pattern"; return $logs }
    Start-Sleep -Milliseconds 700
  }
  $tail = (Adb @('logcat', '-d', '-s', 'LC3D:V', 'LCJS:V', 'LCDEBUG:V') -split "`n" | Select-Object -Last 12) -join ' | '
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
function Get-DumpBtnCenter([string]$xmlPath, [string]$text, [int]$xMax = 400) {
  if (-not (Test-Path $xmlPath)) { return $null }
  $doc = [xml](Get-Content $xmlPath -Raw)
  foreach ($n in @($doc.SelectNodes('//node'))) {
    if ($n.text -ne $text -or $n.clickable -ne 'true') { continue }
    if ($n.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { continue }
    $l = [int]$Matches[1]; $tp = [int]$Matches[2]; $r = [int]$Matches[3]; $bt = [int]$Matches[4]
    if ($xMax -gt 0 -and $l -ge $xMax) { continue }
    return [pscustomobject]@{ l = $l; t = $tp; r = $r; b = $bt
      cx = [int](($l + $r) / 2); cy = [int](($tp + $bt) / 2) }
  }
  return $null
}

# 无设备自检：坐标锚点来自 dump、重复图门必须能拦住 Stage 2 那对同图
if ($SelfTest) {
  $bad = @()
  $xml = Join-Path $root 'docs\fidelity\lc\map-home-01.xml'
  $checks = @(
    @{ name = '3D';     text = '3D';    x = $ThreeDX;   y = $ThreeDY;   xMax = 0 },
    @{ name = '等高线'; text = '等高线'; x = $ContourX;  y = $ContourY;  xMax = 400 },
    @{ name = '山脉';   text = '山脉';   x = $MountainX; y = $MountainY; xMax = 0 },
    @{ name = '水系';   text = '水系';   x = $WaterX;    y = $WaterY;    xMax = 0 },
    @{ name = '探索';   text = '探索';   x = $ExploreX;  y = $ExploreY;  xMax = 0 }
  )
  foreach ($c in $checks) {
    $btn = Get-DumpBtnCenter $xml $c.text $c.xMax
    if (-not $btn) { $bad += "$($c.name): dump 里找不到该按钮"; continue }
    $inside = ($c.x -ge $btn.l -and $c.x -le $btn.r -and $c.y -ge $btn.t -and $c.y -le $btn.b)
    if (-not $inside) {
      $bad += ("{0}: 脚本 ({1},{2}) 不在 dump bounds [{3},{4}]-[{5},{6}] 内" -f $c.name, $c.x, $c.y, $btn.l, $btn.t, $btn.r, $btn.b)
    } else {
      Write-Host ("PASS anchor {0} ({1},{2}) 落在 dump bounds [{3},{4}]-[{5},{6}]（算术中心 {7},{8}）" -f `
        $c.name, $c.x, $c.y, $btn.l, $btn.t, $btn.r, $btn.b, $btn.cx, $btn.cy)
    }
  }
  $hit3d = Get-DumpBtnCenter $xml '3D' 0
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
  Write-Host "SELFTEST: PASS ($($checks.Count + 3) checks)"
  exit 0
}

# 0) 工具能力闸门：检查表 §5 要求 meta 带 build.commit + apk.sha256
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
  $list = (adb devices | Out-String)
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
  Sh "am force-stop $Pkg"
  Sh "am start -n $Pkg/.MainActivity"
  Start-Sleep -Seconds 4          # 未完成 onResume 时广播会被忽略
  Tap $MapTabX $MapTabY
  Start-Sleep -Seconds 2
  # 罗盘 heading 冻结 0°，避免 dump 前传感器抖动导致 could not get idle state
  Bc '--es cmd heading --ef value 0'
  Start-Sleep -Milliseconds 500
}

# 3) 地图默认态 + 固定态
if (Want 'map-beijing-z12' -or Want 'map-3d-mode' -or Want 'tool-contour-01') {
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
  Collect 'map-beijing-z12' 'ST-A-map-fixed'
}

# 4) 3D：点真实中心 → 断言 mode=3d → 断言 png 与 2D 不同 → 拍照
if (Want 'map-3d-mode') {
  Adb @('logcat', '-c') | Out-Null
  Tap $ThreeDX $ThreeDY
  Wait-Logcat 'LC3D mode=3d' 25
  Wait-Logcat 'LC3D texture (ok|fail)' 30
  $st = Get-MapState
  Assert-Mode '3d' $st
  Collect 'map-3d-mode' 'ST-C-3d'
  Assert-PngDistinct 'map-3d-mode' 'map-beijing-z12'
}

# 5) 等高线：3D 可见态下开（OFF 态 = map-3d-mode）
if (Want 'tool-contour-01') {
  Adb @('logcat', '-c') | Out-Null
  Tap $ContourX $ContourY
  Wait-Logcat 'LC3D contour on=True' 20
  $st = Get-MapState
  Assert-Mode '3d' $st
  if (-not $st.contour) { Fail ("contour 断言失败：" + ($st | ConvertTo-Json -Compress)) }
  Collect 'tool-contour-01' 'ST-C-contour'
  Assert-PngDistinct 'tool-contour-01' 'map-3d-mode'
}

# 6) 山脉 / 水系 / 探索：断言前台 Activity 再拍
$pages = @(
  @{ state = 'map-mountains-entry'; cid = 'ST-C-mountains'; x = $MountainX; y = $MountainY; act = 'MountainActivity' },
  @{ state = 'map-water-entry'; cid = 'ST-C-water'; x = $WaterX; y = $WaterY; act = 'WaterActivity' },
  @{ state = 'map-explore-page'; cid = 'ST-C-explore'; x = $ExploreX; y = $ExploreY; act = 'DiscoveryActivity' }
)
foreach ($p in $pages) {
  if (-not (Want $p.state)) { continue }
  Enter-App
  Js 'setView(39.9042,116.4074,12)'
  Start-Sleep -Seconds 2
  Tap $p.x $p.y
  Start-Sleep -Seconds 3
  Assert-Resumed $p.act
  Collect $p.state $p.cid
}

# 7) 测量（地图内测量态）：dump 可能因持续动画 idle 超时，重试后兜底 png-only
if (Want 'tool-measure-01') {
  Enter-App
  Tap $MoreX $MoreY
  Start-Sleep -Seconds 2
  Tap $MeasureX $MeasureY
  Start-Sleep -Seconds 2
  Collect 'tool-measure-01' 'ST-C-measure' -DumpRetries 3
}

# 8) 收尾：释放设备
Sh "am force-stop $Pkg"
Sh 'input keyevent KEYCODE_HOME'
Sh 'settings put global window_animation_scale 1'
Sh 'settings put global transition_animation_scale 1'
Sh 'settings put global animator_duration_scale 1'
if (-not $DryRun) { adb forward --remove-all | Out-Null }
Info "released: force-stop + HOME, animations=1, forward cleared"
Info "DONE. 索引 §4 请按新证据回填；报告门禁: python tools\fidelity\fidelity.py report --root docs\fidelity"
