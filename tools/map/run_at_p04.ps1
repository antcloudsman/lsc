# P0-4 3D 点选 AT 无头执行器（LC地图）
# 用法: pwsh -File tools\map\run_at_p04.ps1 [-Chrome <path>] [-Port 8791] [-View 'lat=40.02&lng=115.45&z=12']
# 原理: 本地 http 起静态服务 -> Chrome headless 打开 map.html#at -> 抓 console 的 LCAT/LCAT-RESULT
param(
  [string]$Chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe',
  [int]$Port = 8791,
  [string]$View = 'lat=40.02&lng=115.45&z=12'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$assets = Join-Path $root 'app\android\app\src\main\assets'
$shot = Join-Path $root 'checks\lc-map\at-run.png'

if (-not (Test-Path $Chrome)) { Write-Host "AT-P04: FAIL (chrome not found: $Chrome)"; exit 2 }
if (-not (Test-Path (Join-Path $assets 'map.html'))) { Write-Host "AT-P04: FAIL (map.html not found)"; exit 2 }

$srv = Start-Process -FilePath python -ArgumentList '-m', 'http.server', "$Port", '--bind', '127.0.0.1' `
  -WorkingDirectory $assets -PassThru -WindowStyle Hidden
try {
  Start-Sleep -Seconds 2
  $url = "http://127.0.0.1:$Port/map.html#at&$View"
  Write-Host "AT-P04: url=$url"
  $log = Join-Path $env:TEMP 'lc_at_p04.log'
  $cargs = "--headless=new --enable-unsafe-swiftshader --enable-logging=stderr --v=0 " +
    "--virtual-time-budget=34000 --window-size=500,800 --screenshot=`"$shot`" `"$url`""
  cmd /c "`"$Chrome`" $cargs > `"$log`" 2>&1" | Out-Null
  $out = Get-Content $log -ErrorAction SilentlyContinue
  if (-not $out) { Write-Host "AT-P04: FAIL (chrome produced no output)"; exit 2 }
  $cases = ($out | Select-String -Pattern 'LCAT (PASS|FAIL) TD-').Line
  $result = ($out | Select-String -Pattern 'LCAT-RESULT').Line
  if (-not $result) { Write-Host "AT-P04: FAIL (no LCAT-RESULT; page did not finish)"; exit 2 }
  $text = ($cases -join "`n")
  if ($text -match 'TD-') { Write-Host ($text -replace '^.*?\]\s*"', '' -replace '"$', '') }
  $r = ($result -replace '^.*?\]\s*"', '' -replace '"$', '')
  Write-Host $r
  Write-Host "AT-P04 manual: TD-016 (device FPS>=30 / touch feel) — run on L2E0222531014833"
  if ($r -match 'PASS') { exit 0 } else { exit 1 }
}
finally {
  if ($srv -and -not $srv.HasExited) { Stop-Process -Id $srv.Id -Force -ErrorAction SilentlyContinue }
}
