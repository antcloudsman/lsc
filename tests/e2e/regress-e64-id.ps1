# E6.4 回归（a11y 合入后）：三字段按稳定 id 定位，判据仅「提交成功 dump + DB 增量」
. (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'tests\lib\ui-drive.ps1')
Init-Ui -Device 'L2E0222531014833' -Package 'com.luopan.fengshui'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$out = Join-Path $root 'checks\e64-id-regression'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Remove-Item (Join-Path $out 'run.log') -ErrorAction SilentlyContinue
function Log($s) { $line = (Get-Date -Format 'HH:mm:ss') + ' ' + $s; Write-Host $line; Add-Content -LiteralPath (Join-Path $out 'run.log') -Value $line -Encoding UTF8 }
function DbFb { ((Get-Content -LiteralPath (Join-Path $root 'checks\db-count.mjs') -Raw | & docker exec -i lc-backend node --input-type=module 2>&1) -join ' ') }

$user = 'e8' + (Get-Random -Maximum 99999); $pass = 'LcE8!' + (Get-Random -Maximum 99999)
Log ('DB_BEFORE=' + (DbFb))
Invoke-Adb @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
Start-Sleep -Seconds 5; Freeze-Heading 0
Log ('focus=' + (Get-Focus))

# 登录（未登录则注册）
Log ('drawer=' + (Tap-NodeRetry '' 'lc_menu_button' 3 1200))
Log ('account=' + (Tap-NodeRetry '账户设置' '' 3 1500))
$d = Get-UiDump
$logged = ($d -and (Find-Node $d '昵称：' ''))
if (-not $logged) {
    Log ('to_login=' + (Tap-NodeRetry '' 'lc_m3_login' 3 1500))
    Log ('reg=' + (Tap-NodeRetry '注册' '' 3 900))
    $ids = @('lc_login_username', 'lc_login_password', 'lc_login_display'); $vals = @($user, $pass, 'e8nick')
    for ($i = 0; $i -lt 3; $i++) { $ok = $false; for ($t = 1; $t -le 3; $t++) { $dd = Get-UiDump; if ($dd -and (Tap-Node $dd '' $ids[$i] 700)) { Input-Text $vals[$i]; $ok = $true; break }; Start-Sleep -Milliseconds 700 }; Log ("fill[{0}]={1}" -f $ids[$i], $ok) }
    $dd = Get-UiDump; Log ('submit_login=' + (Tap-Node $dd '' 'lc_login_submit' 2500))
    Start-Sleep -Seconds 2
    $d = Get-UiDump
    Log ('logged=' + [bool]($d -and (Find-Node $d '昵称：' '')))
}

# 投诉反馈
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 900
if (-not ((Get-Focus) -match 'MainActivity')) { Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null; Start-Sleep -Seconds 3; Freeze-Heading 0 }
Log ('drawer2=' + (Tap-NodeRetry '' 'lc_menu_button' 3 1200))
Log ('feedback=' + (Tap-NodeRetry '投诉反馈' '' 3 1500))
Log ('form=' + (Tap-NodeRetry '问题反馈' '' 3 1500))

$fbBefore = DbFb
Log ('DB_BEFORE_FEEDBACK=' + $fbBefore)
$fid = @('lc_m3_reason', 'lc_m3_contact', 'lc_m3_desc')
$fv = @('id-reason-1', 'id@example.com', 'id-desc-1')
for ($k = 0; $k -lt 3; $k++) {
    $ok = $false
    for ($t = 1; $t -le 3; $t++) {
        $dd = Get-UiDump
        if ($dd -and (Tap-Node $dd '' $fid[$k] 800)) { Input-Text $fv[$k]; $ok = $true; break }
        Start-Sleep -Milliseconds 700
    }
    $chk = Get-UiDump
    $node = if ($chk) { Find-Node $chk '' $fid[$k] } else { $null }
    Log ("id_fill[{0}]={1} value='{2}'" -f $fid[$k], $ok, $(if ($node) { $chk.SelectNodes('//node') | Where-Object { "$($_.'resource-id')" -eq "com.luopan.fengshui:id/$($fid[$k])" } | ForEach-Object { "$($_.text)" } | Select-Object -First 1 } else { '' }))
}
Invoke-Adb @('shell', 'input', 'swipe', '614', '1900', '614', '1000', '350') | Out-Null
Start-Sleep -Milliseconds 900
Log ('submit=' + (Tap-NodeRetry '' 'lc_m3_submit' 3 2500))
$hit = Wait-Text '提交成功' 25
if ($hit) {
    $t2 = ($hit.SelectNodes('//node') | ForEach-Object { "$($_.text)" } | Where-Object { $_ -match '提交成功' } | Select-Object -First 1)
    $b = ($hit.SelectNodes('//node') | Where-Object { "$($_.text)" -match '提交成功' } | Select-Object -First 1).bounds
    Log "E64=PASS text=$t2 bounds=$b"
    $hit.OuterXml | Set-Content -LiteralPath (Join-Path $out 'e64-success-id.xml') -Encoding UTF8
} else {
    $d3 = Get-UiDump
    $st = if ($d3) { ($d3.SelectNodes('//node') | ForEach-Object { "$($_.text)" } | Where-Object { $_ -match '请先|失败|提交中|成功' } | Select-Object -First 1) } else { 'no dump' }
    Log "E64=FAIL status=$st"
    if ($d3) { $d3.OuterXml | Set-Content -LiteralPath (Join-Path $out 'e64-fail-id.xml') -Encoding UTF8 }
}
Start-Sleep -Seconds 1
Log ('DB_AFTER_FEEDBACK=' + (DbFb))

