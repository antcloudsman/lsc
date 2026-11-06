# LC 客户端章节一键复跑：罗盘/浮层/面板/设置/天气/收藏点/超清地图
#
# 用法（仓库根执行，真机 L2E0222531014833 空闲时）：
#   pwsh -File docs\fidelity\collect_lc_client.ps1 -BuildCommit <40位develop提交号>
#
# 前置：APK 已用该提交从 develop 构建；真机解锁、动画三项会在 collect 里被置 0；
# 罗盘 heading 由调试广播冻结为 0°。任一步失败立即中止（不留无声证据）。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BuildCommit,
    [string]$Serial = 'L2E0222531014833',
    [string]$Apk = 'app\android\app\build\outputs\apk\debug\app-debug.apk',
    [string]$Tool = 'tools\fidelity\fidelity.py',
    [string]$Root = 'docs\fidelity',
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$env:PYTHONIOENCODING = 'utf-8'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ($BuildCommit -notmatch '^[0-9a-f]{40}$') { Write-Host "FATAL: -BuildCommit must be a 40-char lowercase sha"; exit 2 }
if (-not (Test-Path $Tool)) { Write-Host "FATAL: $Tool not found"; exit 2 }
if (-not (Select-String -Path $Tool -Pattern 'build-commit' -Quiet)) {
    Write-Host "FATAL: $Tool lacks --build-commit/--build-branch/--apk (checksheet §5); merge it first"
    exit 2
}
if (-not $SkipInstall) {
    if (-not (Test-Path $Apk)) { Write-Host "FATAL: APK not found: $Apk"; exit 2 }
    & adb -s $Serial push $Apk /data/local/tmp/lc-develop.apk | Out-Null
    $installed = & adb -s $Serial shell pm install -r -t /data/local/tmp/lc-develop.apk
    if ($installed -notmatch 'Success') { Write-Host "FATAL: install failed: $installed"; exit 2 }
    Write-Host "install: Success"
}

function Capture([string]$State, [string]$Id, [switch]$SkipDump) {
    $arguments = @($Tool, 'collect', '--root', $Root, '--side', 'lc', '--state', $State,
        '--pkg', 'com.luopan.fengshui', '--checksheet-id', $Id,
        '--build-commit', $BuildCommit, '--apk', $Apk, '--force')
    if ($SkipDump) { $arguments += '--skip-dump' }
    & python @arguments
    if ($LASTEXITCODE -ne 0) { Write-Host "FATAL: collect failed at $State"; exit 1 }
}

function Tap([int]$x, [int]$y, [int]$Wait = 2) {
    & adb -s $Serial shell input tap $x $y | Out-Null
    Start-Sleep -Seconds $Wait
}

function Back([int]$Wait = 2) {
    & adb -s $Serial shell input keyevent KEYCODE_BACK | Out-Null
    Start-Sleep -Seconds $Wait
}

# --- 冷启动探针（WebView 首帧 uiautomator 取不到 idle，该 stem 只留 png + meta） ---
& adb -s $Serial shell am force-stop com.luopan.fengshui | Out-Null
Start-Sleep -Seconds 1
& adb -s $Serial shell am start -n com.luopan.fengshui/.MainActivity | Out-Null
Start-Sleep -Seconds 2
Capture 'launch-probe-01' 'ST-A-launch' -SkipDump

# --- 地图主界面 + 罗盘浮层 ---
Start-Sleep -Seconds 2
Capture 'map-home-01' 'ST-A-map-home'
Tap 103 1868 3                                    # 左列「罗盘」
Capture 'tool-compass-page' 'ST-A-compass-page'
Capture 'win-02-compass' 'ST-A-compass-columns'
Tap 1127 249 3                                    # 顶栏「隐藏」
Capture 'compass-overlay-hidden-map' 'ST-A-compass-hidden'
Tap 1127 249 3                                    # 地图页「搜索」→ 知识库/搜索弹层
Capture 'lc-knowledge-sheet' 'LC-EXTRA-knowledge'
Tap 613 999 3                                     # 「盘面（5 种，全部免费）」
Capture 'dial-list-01' 'ST-A-dial-list'
Tap 992 1663 2                                    # 「确定」
Tap 992 2103 2                                    # 「关闭」
Tap 103 1868 3                                    # 再进罗盘
Tap 103 1560 3                                    # 左列「读盘」
Capture 'w3-sheet' 'ST-A-read-sheet'
Back
Tap 114 249 2                                     # 罗盘顶栏「返回」

# --- 地图工具面板（默认态；滚动后为同态，共用这一份证据，不另拍） ---
Tap 103 2453 3                                    # 左列「更多」
Capture 'more-sheet-03-fresh' 'ST-D-more-default'
& adb -s $Serial shell input swipe 614 2300 614 1500 300 | Out-Null
Start-Sleep -Seconds 2   # 预期无变化：面板不可滚动，输入行 32 引用同一份证据
Back
Tap 103 2453 2

# --- 设置 ---
Tap 673 963 3                                     # 「设置」
Capture 'setting-map-01' 'ST-E-map-setting'
Tap 88 210 2

# --- 天气 ---
Tap 103 2453 2
Tap 486 1285 4                                    # 「天气」
Capture 'tool-weather-01' 'ST-F-weather'
Tap 88 210 2

# --- 收藏点（空态页；行 37 收藏列表入口在 LC 不存在，共用这一份证据） ---
Tap 103 2453 2
Tap 232 1453 4                                    # 「收藏点」
Capture 'tool-favorite-01' 'ST-A-favorite-mode'
Tap 53 206 2

# --- 超清地图 ---
Tap 103 2453 2
Tap 486 1117 4                                    # 「超清地图」
Capture 'tool-hdmap-01' 'ST-A-hdmap'
Tap 88 210 2

# --- 海拔查询 POI 面板（先按基准固定视角） ---
& adb -s $Serial shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'setView(39.9042,116.4074,12)'" | Out-Null
Start-Sleep -Seconds 3
Tap 103 2453 2
Tap 232 1285 4                                    # 「海拔查询」
Tap 614 1400 4                                    # 点地图任意位置
Capture 'compass-main-01' 'ST-01-compass-main'
Tap 88 210 2

# --- 收尾：FATAL 检查 + 回桌面 ---
$fatal = & adb -s $Serial logcat -d -b crash | Select-String 'FATAL EXCEPTION'
if ($fatal) { Write-Host "FATAL: crash log found"; $fatal; exit 1 }
Write-Host "FATAL scan: clean"
& adb -s $Serial shell am force-stop com.luopan.fengshui | Out-Null
& adb -s $Serial shell input keyevent KEYCODE_HOME | Out-Null
Write-Host "DONE: client chapter captures -> $Root\lc"
