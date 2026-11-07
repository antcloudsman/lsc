# M6-2 验收：拖动方向符号（2a）与 bearing 逆旋转（2b）
# 判据（总监裁定）：
#   2a   bearing=0，四方向：手指右→中心西移(Δlng·dx<0)、手指上→中心南移(Δlat·dy<0)；z=12/z=18 各一遍
#   2b   bearing=30/90：内容位移须与手指【平行且同向】(|dmx| ≤ 0.25·|dmy|、dmy<0)
# 用法：pwsh -NoProfile -File tests\e2e\m6-drag-verify.ps1 [-Device L2E0222531014833]
# 退出码：0 全过；1 有失败
[CmdletBinding()]
param(
    [string]$Device = 'L2E0222531014833',
    [string]$OutDir = ''
)
$pkg = 'com.luopan.fengshui'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutDir) { $OutDir = Join-Path $root ('checks\m6-drag-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$script:fails = 0
function Log($s) { $l = (Get-Date -Format 'HH:mm:ss') + ' ' + $s; Write-Host $l; Add-Content -LiteralPath (Join-Path $OutDir 'run.log') -Value $l -Encoding UTF8 }
function Verdict([string]$id, [string]$title, [bool]$ok, [string]$detail) {
    if (-not $ok) { $script:fails++ }
    Log (("{0} {1} {2} — {3}") -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $id, $title, $detail)
}
function Invoke-Adb2([string[]]$parts) { (& adb -s $Device @parts 2>&1 | ForEach-Object { "$_" }) }
function Js([string]$code) {
    Invoke-Adb2 @('shell', "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value '$code'") | Out-Null
    Start-Sleep -Milliseconds 550
}
function ReadState {
    Invoke-Adb2 @('shell', 'logcat', '-c') | Out-Null
    Js 'JSON.stringify(getState())'
    $line = (Invoke-Adb2 @('shell', 'logcat', '-d', '-s', 'LCJS:V') | Select-String 'map ->' | Select-Object -Last 1).Line
    if (-not $line) { return $null }
    $j = $line -replace '\\"', '"'
    return [pscustomobject]@{
        Lat = [double]([regex]::Match($j, '"lat":([-\d\.]+)').Groups[1].Value)
        Lng = [double]([regex]::Match($j, '"lng":([-\d\.]+)').Groups[1].Value)
        Brg = [int]([regex]::Match($j, '"bearing":(\d+)').Groups[1].Value)
        Zoom = [int]([regex]::Match($j, '"zoom":(\d+)').Groups[1].Value)
    }
}
function Swipe([int]$dx, [int]$dy, [int]$dur = 400) {
    $x1 = 614 - [int]($dx / 2); $y1 = 1300 - [int]($dy / 2)
    Invoke-Adb2 @('shell', ("input swipe {0} {1} {2} {3} {4}" -f $x1, $y1, ($x1 + $dx), ($y1 + $dy), $dur)) | Out-Null
    Start-Sleep -Milliseconds 1600
}

# 前置：应用在前台
$focus = (Invoke-Adb2 @('shell', 'dumpsys activity activities') | Select-String 'mResumedActivity').Line
if ($focus -notmatch [regex]::Escape($pkg)) {
    Invoke-Adb2 @('shell', 'am', 'start', '-n', "$pkg/.MainActivity") | Out-Null
    Start-Sleep -Seconds 5
}

# ---------- 2a 符号（bearing=0，z=18 与 z=12） ----------
foreach ($z in 18, 12) {
    Js "sitMode=0;bearing=0;setView(26.6068,106.7221,$z)" | Out-Null
    Start-Sleep -Milliseconds 700
    $dirs = @(
        @{ N = 'right'; dx = 400; dy = 0 },
        @{ N = 'left'; dx = -400; dy = 0 },
        @{ N = 'up'; dx = 0; dy = -400 },
        @{ N = 'down'; dx = 0; dy = 400 }
    )
    foreach ($d in $dirs) {
        $c1 = ReadState
        Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2a-z{0}-{1}-before.png" -f $z, $d.N))
        Swipe $d.dx $d.dy
        $c2 = ReadState
        Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2a-z{0}-{1}-after.png" -f $z, $d.N))
        $dlng = $c2.Lng - $c1.Lng; $dlat = $c2.Lat - $c1.Lat
        # 同向=手指方向与内容方向一致 ⇒ 中心位移与手指相反
        $ok = $true
        if ($d.dx -ne 0) { $ok = $ok -and ($dlng * $d.dx -lt 0) }
        if ($d.dy -ne 0) { $ok = $ok -and ($dlat * $d.dy -lt 0) }
        Verdict ("2a-z$z-" + $d.N) 'bearing=0 拖动符号' $ok ("finger=({0},{1}) dLng={2} dLat={3}" -f $d.dx, $d.dy, [math]::Round($dlng, 6), [math]::Round($dlat, 6))
    }
}

# ---------- 2b 逆旋转（bearing=30/90，上滑 300px） ----------
foreach ($b in 30, 90) {
    Js "sitMode=0;bearing=$b;applyTransform()" | Out-Null
    Start-Sleep -Milliseconds 700
    $c1 = ReadState
    Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2b-b$b-before.png"))
    Swipe 0 -300
    $c2 = ReadState
    Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2b-b$b-after.png"))
    $z = $c2.Zoom
    $kLng = 256.0 * [math]::Pow(2, $z) / 360.0
    $kLat = $kLng * (1.0 / [math]::Cos($c1.Lat * [math]::PI / 180.0))
    $dmx = -($c2.Lng - $c1.Lng) * $kLng      # 内容位移（屏幕 px，右正）
    $dmy = ($c2.Lat - $c1.Lat) * $kLat       # 内容位移（屏幕 px，下正）
    # 期望：手指上滑 ⇒ 内容上移 (dmy<0) 且 平行 (|dmx| <= 0.25|dmy|)
    $parallel = ([math]::Abs($dmx) -le 0.25 * [math]::Abs($dmy))
    $ok = ($dmy -lt 0) -and $parallel
    Verdict ("2b-bearing$b") 'bearing≠0 时内容与手指平行同向' $ok ("bearing={0} dLng={1} dLat={2} → dm=({3},{4}) px; 平行={5}" -f $b, [math]::Round($c2.Lng - $c1.Lng, 6), [math]::Round($c2.Lat - $c1.Lat, 6), [math]::Round($dmx, 1), [math]::Round($dmy, 1), $parallel)
}
Js 'sitMode=0;bearing=0;applyTransform()' | Out-Null

Log ("fails=" + $script:fails + " out=" + $OutDir)
if ($script:fails -eq 0) { exit 0 } else { exit 1 }
