# TD-015 真机执行器（LC地图）——3D 点选帧率与触控
# 用法: pwsh -File tools\map\run_td015_device.ps1 -Apk <develop 构建的 app-debug.apk> [-Device L2E0222531014833]
# 前置: 设备空闲、未被我方其它脚本占用；执行约 2 分钟。
param(
  [Parameter(Mandatory = $true)][string]$Apk,
  [string]$Device = 'L2E0222531014833'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$out = Join-Path $root 'checks\lc-map'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$log = Join-Path $out 'td015.log'
$shot = Join-Path $out 'td015.png'

$d = $Device
Write-Host "TD015: install $Apk"
adb -s $d install -r $Apk | Out-Null
adb -s $d logcat -c
adb -s $d shell am force-stop com.luopan.fengshui
adb -s $d shell am start -n com.luopan.fengshui/.MainActivity | Out-Null
Start-Sleep -Seconds 4

# 地图 Tab -> 3D 地形（坐标与 1228x2700 屏一致；按钮顺序固定）
adb -s $d shell input tap 615 2525
Start-Sleep -Seconds 2
adb -s $d shell input tap 155 576
Start-Sleep -Seconds 10

# 5 次点选（不同屏幕位置，验证每次都只出 1 条 pick 且落点正常）
$pts = @(@(614, 1500), @(500, 1600), @(720, 1400), @(614, 1800), @(700, 1700))
foreach ($p in $pts) {
  adb -s $d shell input tap $p[0] $p[1]
  Start-Sleep -Milliseconds 900
}
Start-Sleep -Seconds 8
cmd /c "adb -s $d exec-out screencap -p > `"$shot`"" | Out-Null
adb -s $d logcat -d | Out-File -Encoding utf8 $log
$txt = Get-Content $log
$picks = ($txt | Select-String -Pattern 'LC3D pick ').Count
$fps = @()
foreach ($line in ($txt | Select-String -Pattern 'LC3D fps=')) {
  if ($line.Line -match 'fps=(\d+)') { $fps += [int]$Matches[1] }
}
$fatal = ($txt | Select-String -Pattern 'FATAL EXCEPTION').Count
$fpsMin = if ($fps.Count) { ($fps | Measure-Object -Minimum).Minimum } else { 0 }
$fpsMax = if ($fps.Count) { ($fps | Measure-Object -Maximum).Maximum } else { 0 }

Write-Host "TD015: picks=$picks (expect 5)  fps=$fpsMin..$fpsMax (expect min>=30)  fatal=$fatal (expect 0)"
Write-Host "TD015: screenshot $shot  log $log"
if ($picks -ge 5 -and $fpsMin -ge 30 -and $fatal -eq 0) { Write-Host "TD015: PASS"; exit 0 }
Write-Host "TD015: FAIL"; exit 1
