# M6-2 验收：拖动方向符号（2a）与 bearing 逆旋转（2b）
# 判据（总监裁定）：
#   2a   bearing=0，四方向：手指右→中心西移(Δlng·dx<0)、手指上→中心南移(Δlat·dy<0)；z=12/z=18 各一遍
#   2b   bearing=30/330/90：内容位移须与期望向量同向且夹角≤3°（330 用于抓左右镜像：逆旋转写反时 +30 仍 PASS、330 必露）
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
function AdbLine([string]$cmd) { ((& adb -s $Device shell $cmd 2>&1) -join "`n") }
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
        Ui = [regex]::Match($j, '"uiMode":"(\w+)"').Groups[1].Value
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
        if (-not $c1) { Verdict ("2a-z$z-" + $d.N) 'bearing=0 拖动符号' $false 'getState 读取失败（map 未加载或 js 注入被拒）'; continue }
        if ($c1.Ui -ne 'map') { Verdict ("2a-z$z-" + $d.N) 'bearing=0 拖动符号' $false ('uiMode=' + $c1.Ui + '（罗盘浮层会吞掉拖拽，先退出罗盘态）'); continue }
        Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2a-z{0}-{1}-before.png" -f $z, $d.N))
        Swipe $d.dx $d.dy
        $c2 = ReadState
        if (-not $c2) { Verdict ("2a-z$z-" + $d.N) 'bearing=0 拖动符号' $false 'after getState 读取失败'; continue }
        Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2a-z{0}-{1}-after.png" -f $z, $d.N))
        $dlng = $c2.Lng - $c1.Lng; $dlat = $c2.Lat - $c1.Lat
        # 同向=手指方向与内容方向一致 ⇒ 中心位移与手指相反
        $ok = $true
        # 轴向不对称：经度 中心位移与手指反向(Δlng·dx<0)；纬度 中心位移与手指同号(Δlat·dy>0)
        # 依据：手指上滑 dy<0 → 内容上移 → 中心南移 Δlat<0；手指右滑 dx>0 → 内容右移 → 中心西移 Δlng<0
        if ($d.dx -ne 0) { $ok = $ok -and ($dlng * $d.dx -lt 0) }
        if ($d.dy -ne 0) { $ok = $ok -and ($dlat * $d.dy -gt 0) }
        Verdict ("2a-z$z-" + $d.N) 'bearing=0 拖动符号' $ok ("finger=({0},{1}) dLng={2} dLat={3}" -f $d.dx, $d.dy, [math]::Round($dlng, 6), [math]::Round($dlat, 6))
    }
}

# ---------- 2b 逆旋转（bearing=30/90，上滑 300px） ----------
foreach ($b in 30, 330, 90) {
    Js "sitMode=0;bearing=$b;applyTransform()" | Out-Null
    Start-Sleep -Milliseconds 700
    $c1 = ReadState
    if (-not $c1) { Verdict ("2b-bearing$b") 'bearing≠0 时内容与手指平行同向' $false 'getState 读取失败'; continue }
    Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2b-b$b-before.png"))
    Swipe 0 -300
    $c2 = ReadState
    Invoke-Adb2 @('exec-out', 'screencap', '-p') > (Join-Path $OutDir ("2b-b$b-after.png"))
    $z = $c2.Zoom
    $kLng = 256.0 * [math]::Pow(2, $z) / 360.0
    $kLat = $kLng * (1.0 / [math]::Cos($c1.Lat * [math]::PI / 180.0))
    $dLng = $c2.Lng - $c1.Lng
    $dLat = $c2.Lat - $c1.Lat
    # 期望（内容跟手）：屏幕位移 (dx,dy) 经地图旋转 B 后写入地图坐标
    #   Δlng = -(dx·cosB + dy·sinB)/kLng ；Δlat = (dy·cosB - dx·sinB)/kLat
    $rad = [double]$b * [math]::PI / 180.0
    $dx = 0; $dy = -300
    $expLng = -($dx * [math]::Cos($rad) + $dy * [math]::Sin($rad)) / $kLng
    $expLat = ($dy * [math]::Cos($rad) - $dx * [math]::Sin($rad)) / $kLat
    # 方向一致性：角度误差 ≤3°（dot>0 且 |cross|/dot ≤ tan3°）
    $dot = $dLng * $expLng + $dLat * $expLat
    $cross = $dLng * $expLat - $dLat * $expLng
    $angErr = if ($dot -gt 0) { [math]::Atan2([math]::Abs($cross), $dot) * 180.0 / [math]::PI } else { 180 }
    $ok = ($dot -gt 0) -and ($angErr -le 3.0)
    Verdict ("2b-bearing$b") 'bearing≠0 时内容与手指平行同向（≤3°）' $ok ("bearing={0} dLng={1} dLat={2} ; 期望({3},{4}) ; 角误差={5}°" -f $b, [math]::Round($dLng, 6), [math]::Round($dLat, 6), [math]::Round($expLng, 6), [math]::Round($expLat, 6), [math]::Round($angErr, 2))
}
Js 'sitMode=0;bearing=0;applyTransform()' | Out-Null

$dump = (AdbLine "dumpsys package $pkg")
$lu = [regex]::Match($dump, 'lastUpdateTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})')
Log ("lastUpdate=" + $(if ($lu.Success) { $lu.Groups[1].Value } else { '(unavailable)' }))
Log ("fails=" + $script:fails + " out=" + $OutDir)
if ($script:fails -eq 0) { exit 0 } else { exit 1 }
