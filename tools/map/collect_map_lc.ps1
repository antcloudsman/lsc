# LC 侧地图模块 · Stage 2 对齐采集（一键复跑）
# 用法:
#   pwsh -File tools\map\collect_map_lc.ps1 -Apk <develop 构建的 app-debug.apk> -Commit <40位 sha>
# 前置: 真机空闲；本脚本会改三项动画为 0、装包、启动、切到地图 Tab、逐状态调好再调 collect。
# 采集口径: 1228x2700、动画 0、地图固定 39.9042/116.4074 z=12（用调试广播 setView 一次到位）。
param(
  [Parameter(Mandatory = $true)][string]$Apk,
  [Parameter(Mandatory = $true)][string]$Commit,
  [string]$Serial = 'L2E0222531014833',
  [string]$Pkg = 'com.luopan.fengshui',
  [int]$MapTabX = 615, [int]$MapTabY = 2525,
  [int]$ThreeDX = 155, [int]$ThreeDY = 576,
  [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

function Sh([string]$cmd) {
  if ($DryRun) { Write-Host "DRY: adb -s $Serial shell $cmd"; return }
  adb -s $Serial shell $cmd | Out-Null
}
function Tap([int]$x, [int]$y) {
  if ($DryRun) { Write-Host "DRY: tap $x,$y"; return }
  adb -s $Serial shell input tap $x $y | Out-Null
}
function Collect([string]$state, [string]$cid) {
  $argv = @('tools\fidelity\fidelity.py', 'collect', '--side', 'lc', '--state', $state,
    '--pkg', $Pkg, '--serial', $Serial, '--checksheet-id', $cid,
    '--build-commit', $Commit, '--apk', $Apk)
  if ($DryRun) { $argv += '--dry-run' }
  Write-Host ("collect: " + ($argv -join ' '))
  python @argv
  if ($LASTEXITCODE -ne 0) { throw "collect failed: $state" }
}

# 0) 工具能力闸门：检查表 §5 要求 meta 带 build.commit + apk.sha256
$help = (python tools\fidelity\fidelity.py collect --help 2>&1 | Out-String)
if ($help -notmatch '--build-commit' -or $help -notmatch '--apk') {
  Write-Host "FATAL: tools/fidelity/fidelity.py 缺 --build-commit/--apk，按检查表 §5 采集会整批作废。先合入该改动再跑。"
  exit 2
}
if ($Commit.Length -ne 40) { Write-Host "FATAL: -Commit 必须是 40 位 sha，当前 '$Commit'"; exit 2 }

# 1) 设备前置
Sh 'settings put global window_animation_scale 0'
Sh 'settings put global transition_animation_scale 0'
Sh 'settings put global animator_duration_scale 0'
Sh 'input keyevent KEYCODE_WAKEUP'
Sh 'wm dismiss-keyguard'
Start-Sleep -Seconds 1

# 2) 装包（push + pm install -r -t 口径）
if (-not $DryRun) {
  adb -s $Serial install -r -t $Apk | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "install failed" }
}
Sh "am force-stop $Pkg"
Sh 'am start -n com.luopan.fengshui/.MainActivity'
Start-Sleep -Seconds 4
Tap $MapTabX $MapTabY            # 地图 Tab
Start-Sleep -Seconds 2

# 3) 地图默认态
Collect 'map-home-01' 'ST-A-map-home'

# 4) 固定态：39.9042,116.4074 z=12（调试广播直接 setView，不用点 7 次放大）
$js = "setView(39.9042,116.4074,12)"
$bc = "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value `"$js`""
if ($DryRun) { Write-Host "DRY: $bc" } else { adb -s $Serial shell $bc | Out-Null }
Start-Sleep -Seconds 2
Collect 'map-beijing-z12' 'ST-A-map-fixed'

# 5) 3D 模式
Tap $ThreeDX $ThreeDY
Start-Sleep -Seconds 10
Collect 'map-3d-mode' 'ST-C-3d'

# 6) 3D 点选态（LC 已有，原版无对应态：仅 LC 侧证据，配对时记 NC/多余）
Tap 614 1500
Start-Sleep -Seconds 2
Collect 'map-3d-pick' 'ST-C-3d-pick'

Write-Host "DONE: map-home-01 / map-beijing-z12 / map-3d-mode / map-3d-pick"
Write-Host "缺口（LC 无入口，索引里按缺失登记，证据指向 map-home-01）：map-layer-sheet / map-overlay-sheet / map-mountains-entry / map-water-entry / tool-history-image-01 / tool-measure-01 / tool-brush / tool-track / tool-fishing / tool-elevation-01"
