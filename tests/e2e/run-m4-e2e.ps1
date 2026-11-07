<#
  M4 E2E 全链路（真机）：注册 → 登录 → 收藏 → 同步 → 反馈 → 登出 + 服务停机降级。
  用法：
    pwsh -File tests\e2e\run-m4-e2e.ps1 -Apk <apk> [-SkipInstall] [-SkipDegraded]
  证据：checks\m4-e2e-<时间戳>\ 下 step.jsonl / step.md / 每步 dump / 截图 / 脱敏 logcat。
  纪律：坐标全部当次 dump 解析；不写死控件位置；不改被测代码；容器只 stop/start，不 down -v。
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string]$OutRoot = '',
    [switch]$SkipInstall,
    [switch]$SkipClear,
    [switch]$SkipDegraded
)

$ErrorActionPreference = 'Continue'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $repo 'tests\lib\ui-drive.ps1')
Init-Ui -Device $Device -Package 'com.luopan.fengshui'

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if (-not $OutRoot) { $OutRoot = Join-Path $repo "checks\m4-e2e-$stamp" }
New-Item -ItemType Directory -Force -Path $OutRoot | Out-Null
$jsonl = Join-Path $OutRoot 'step.jsonl'
$mdPath = Join-Path $OutRoot 'step.md'
$script:fails = 0
$script:steps = 0
$user = 'e2e' + (Get-Random -Maximum 99999)
$pass = 'LcE2E!' + (Get-Random -Maximum 99999)
$fbText = 'e2e-auto-feedback'

function Record([string]$id, [string]$title, [bool]$ok, [string]$detail) {
    $script:steps++
    if (-not $ok) { $script:fails++ }
    $obj = [ordered]@{ ts = (Get-Date).ToString('HH:mm:ss.fff'); step = $id; title = $title; ok = $ok; detail = $detail }
    Add-Content -LiteralPath $jsonl -Value ($obj | ConvertTo-Json -Compress) -Encoding UTF8
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Add-Content -LiteralPath $mdPath -Value ("| {0} | {1} | {2} | {3} |" -f $id, $title, $mark, ($detail -replace '\|', '/')) -Encoding UTF8
    Write-Host ("[{0}] {1} {2} — {3}" -f $mark, $id, $title, $detail)
}

function Snap([string]$name) {
    $p = Join-Path $OutRoot "$name.png"
    Invoke-Adb @('exec-out', 'screencap', '-p') | Out-Null
    & adb -s $Device exec-out screencap -p > $p 2>$null
    $d = Get-UiDump
    if ($d) { $d.OuterXml | Set-Content -LiteralPath (Join-Path $OutRoot "$name.xml") -Encoding UTF8 }
    return $p
}

function EnsureForeground([string]$expect, [int]$timeoutSec = 15) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $f = Get-Focus
        if ($f -match [regex]::Escape($expect)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Tap-Seq([string]$text, [string]$id, [int]$wait = 1000) {
    $d = Get-UiDump
    $ok = Tap-Node $d $text $id $wait
    return $ok
}

Add-Content -LiteralPath $mdPath -Value "# M4 E2E 全链路（真机）`n`n| 步骤 | 标题 | 结果 | 详情 |`n|---|---|---|---|" -Encoding UTF8
Write-Host "out=$OutRoot user=$user"

# ---------- S0 前置 ----------
Invoke-Adb @('shell', 'input', 'keyevent', '224') | Out-Null
Invoke-Adb @('shell', 'wm', 'dismiss-keyguard') | Out-Null
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    Invoke-Adb @('shell', 'settings', 'put', 'global', $k, '0') | Out-Null
}
$rev = (Invoke-Adb @('reverse', 'tcp:8787', 'tcp:8787')) -join ' '
$revList = (Invoke-Adb @('reverse', '--list')) -join ';'
Record 'S0.1' 'adb reverse 8787' ($revList -match 'tcp:8787') $revList

if (-not $SkipInstall) {
    $inst = Join-Path $repo 'tools\device\install-debug.ps1'
    if (Test-Path $inst) {
        $out = & pwsh -NoProfile -File $inst -Apk $Apk -Device $Device 2>&1
        $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        $out | Set-Content -LiteralPath (Join-Path $OutRoot 'install.log') -Encoding UTF8
        Record 'S0.2' 'install-debug.ps1 装包' ($code -eq 0) ("exit=$code; " + (($out | Select-Object -Last 2) -join ' '))
    } else {
        Invoke-Adb @('push', $Apk, '/data/local/tmp/lc-e2e.apk') | Out-Null
        $o = (Invoke-Adb @('shell', 'pm', 'install', '-r', '-t', '/data/local/tmp/lc-e2e.apk')) -join ' '
        Record 'S0.2' 'pm install' ($o -match 'Success') $o
    }
}

# 装机指纹（证据与代码对应关系：必须记构建体指纹，不只记 commit）
$pkgPath = ((Invoke-Adb @('shell', 'pm', 'path', 'com.luopan.fengshui')) -join '') -replace 'package:', ''
$pkgPath = $pkgPath.Trim()
$devSha = if ($pkgPath) { (((Invoke-Adb @('shell', "sha256sum $pkgPath")) -join ' ') -split '\s+')[0] } else { '' }
$localSha = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
Record 'S0.3' '装机指纹' ($devSha -eq $localSha) ("device=$devSha local=$localSha")

Invoke-Adb @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
# 干净会话：E2E 必须从「未登录 + 空本地收藏」起跑，否则上一轮的 session/收藏会污染判据
# （实测踩过：残留登录态导致「立即登录」节点不存在，E1.3 假 FAIL）
if (-not $SkipClear) {
    $clearOut = (Invoke-Adb @('shell', 'pm', 'clear', 'com.luopan.fengshui')) -join ' '
    Record 'S0.35' '清应用数据（干净会话）' ($clearOut -match 'Success') ($clearOut.Trim())
} else {
    Record 'S0.35' '清应用数据（已由调用方完成）' $true 'SkipClear'
}
# pm clear 会连运行时权限一起清掉 -> 冷启动弹 GrantPermissionsActivity 挡住全流程；按 manifest 需要的一次性补回
$permMap = @(
    @('android.permission.ACCESS_FINE_LOCATION', 'allow'),
    @('android.permission.ACCESS_COARSE_LOCATION', 'allow'),
    @('android.permission.POST_NOTIFICATIONS', 'allow')
)
$permOk = @()
foreach ($pm in $permMap) {
    $r = (Invoke-Adb @('shell', 'pm', 'grant', 'com.luopan.fengshui', $pm[0])) -join ' '
    $permOk += ("{0}={1}" -f ($pm[0] -replace 'android.permission.', ''), $(if ($r -match 'Exception|Error') { 'ERR' } else { 'ok' }))
}
$remain = (Invoke-Adb @('shell', 'dumpsys', 'package', 'com.luopan.fengshui')) -join "`n"
$notGranted = ([regex]::Matches($remain, '(android\.permission\.[A-Z_]+): granted=false') | ForEach-Object { $_.Groups[1].Value }) -join ','
Record 'S0.36' '补回运行时权限（避免授权框挡流程）' ($notGranted -eq '') (($permOk -join ';') + "; not_granted=$notGranted")
Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
$readyDoc = Wait-AppReady 25
$dismissed = 0
for ($i = 0; $i -lt 8; $i++) {
    if ($readyDoc) { break }
    if (Dismiss-SystemDialogs) { $dismissed++; Start-Sleep -Seconds 2; $readyDoc = Wait-AppReady 25 }
    else { Start-Sleep -Seconds 2; $readyDoc = Wait-AppReady 15 }
}
Record 'S0.4' '启动到主界面并等待就绪' ([bool]$readyDoc) ("dismissed_dialogs=" + $dismissed + " focus=" + (Get-Focus))
Snap 's0-main'

# ---------- E1 注册（抽屉 → 账户设置 → 立即登录 → 注册） ----------
$opened = Tap-NodeRetry '' 'lc_menu_button' 3 1200
Record 'E1.1' '打开抽屉' $opened (Get-Focus)
$d = Get-UiDump
$toAcct = Tap-Node $d '账户设置' '' 1500
Record 'E1.2' '抽屉 → 账户设置' $toAcct (Get-Focus)
if (-not (EnsureForeground 'M3PageActivity' 10)) { Record 'E1.2b' '账户设置页前台' $false (Get-Focus) }
Snap 'e1-account-before-login'

$d = Get-UiDump
$toLogin = Tap-Node $d '' 'lc_m3_login' 1500
Record 'E1.3' '立即登录 → 登录页' $toLogin (Get-Focus)
if (-not (EnsureForeground 'LoginActivity' 10)) { Record 'E1.3b' '登录页前台' $false (Get-Focus) }

$d = Get-UiDump
$toReg = Tap-Node $d '注册' '' 900
Record 'E1.4' '切到注册 tab' $toReg (Get-Focus)

$d = Get-UiDump
$uOk = Tap-Node $d '' 'lc_login_username' 500
if ($uOk) { Input-Text $user }
$d = Get-UiDump
$pOk = Tap-Node $d '' 'lc_login_password' 500
if ($pOk) { Input-Text $pass }
$d = Get-UiDump
$nOk = Tap-Node $d '' 'lc_login_display' 500
if ($nOk) { Input-Text 'e2enick' }
Record 'E1.5' '填账号/密码/昵称' ($uOk -and $pOk) "user=$user"
Snap 'e1-register-filled'

$d = Get-UiDump
$sub = Tap-Node $d '' 'lc_login_submit' 2500
# 判据：注册成功的证据是「回到账户设置页并显示登录态」；'登录成功' 是瞬时行，抓不到不算 FAIL
$okReg = EnsureForeground 'M3PageActivity' 20
$sawMsg = $false
if (-not $okReg) { $sawMsg = $null -ne (Wait-Text '登录成功' 5) }
Record 'E1.6' '注册并登录' ($sub -and $okReg) ("submit=$sub back_to_account=$okReg transient_msg=$sawMsg focus=" + (Get-Focus))
Snap 'e1-register-result'

# ---------- E2 登录态确认 ----------
$d = Wait-Text '昵称：' 15
$loggedIn = $null -ne $d
Record 'E2.1' '账户设置显示登录态' $loggedIn ("focus=" + (Get-Focus))
Snap 'e2-account-loggedin'

# ---------- E3 收藏（收藏点地图态 → 添加点 → 保存） ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
if (-not (EnsureForeground 'MainActivity' 10)) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
}
$d = Get-UiDump
$more = Tap-Node $d '更多' '' 1500
Record 'E3.1' '打开更多面板' $more (Get-Focus)
$d = Get-UiDump
$fav = Tap-Node $d '收藏点' '' 1500
Record 'E3.2' '进入收藏点地图态' $fav (Get-Focus)
$d = Get-UiDump
$add = Tap-Node $d '' 'lc_fav_add' 1800
Record 'E3.3' '点底部「添加点」' $add (Get-Focus)
$inSave = EnsureForeground 'SaveInterestActivity' 10
Record 'E3.3b' '保存收藏点页前台' $inSave (Get-Focus)
$d = Get-UiDump
$nm = Tap-Node $d '' 'lc_save_name' 500
if ($nm) { Input-Text ('e2e-fav-' + $user) }
$d = Get-UiDump
$cf = Tap-Node $d '' 'lc_save_confirm' 2000
$d = Get-UiDump
Record 'E3.4' '保存收藏点' ($cf -and $null -ne (Find-Node $d '收藏' '')) ("confirm=$cf focus=" + (Get-Focus))
Snap 'e3-favorite-saved'

# ---------- E4 同步（收藏点上云） ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
if (-not (EnsureForeground 'MainActivity' 10)) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
}
$d = Get-UiDump
$d2 = Tap-Node $d '' 'lc_menu_button' 1200
$d = Get-UiDump
$acct2 = Tap-Node $d '账户设置' '' 1500
$d = Get-UiDump
$syncTap = Tap-Node $d '同步收藏点' '' 3000
$syncDoc = Wait-Text '同步收藏点：' 25
$syncDetail = if ($syncDoc) { (($syncDoc.SelectNodes('//node') | ForEach-Object { "$($_.text)" }) -match '同步' | Where-Object { $_ -match '同步收藏点' } | Select-Object -First 1) } else { 'no result text' }
Record 'E4.1' '同步收藏点上云' ([bool]$syncDoc) ("tap=$syncTap text=$syncDetail")
Snap 'e4-sync-result'

# ---------- E5 服务端侧独立印证 ----------
$dbJs = Join-Path $repo 'checks\db-count.mjs'
if (-not (Test-Path -LiteralPath $dbJs)) {
    @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync(process.env.DB_PATH || "/data/lc.sqlite", { readOnly: true });
const q = (t) => db.prepare(`select count(*) as n from ${t}`).get().n;
console.log(JSON.stringify({ users: q("users"), favorites: q("favorites"), feedback: q("feedback") }));
'@ | Set-Content -LiteralPath $dbJs -Encoding UTF8
}
$dbOut = (Get-Content -LiteralPath $dbJs -Raw | & docker exec -i lc-backend node --input-type=module 2>&1) -join ' '
Record 'E5.1' '服务端计数（DB）' ($dbOut -match '"users"') ($dbOut -replace '\s+', ' ')
Record 'E5.1' '服务端计数（DB）' ($LASTEXITCODE -eq 0) (($dbOut -join ' ') -replace '\s+', ' ')

# ---------- E6 投诉反馈 ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
$d = Get-UiDump
if (-not (Find-Node $d '' 'lc_menu_button')) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
    $d = Get-UiDump
}
$d = Get-UiDump
Tap-Node $d '' 'lc_menu_button' 1200 | Out-Null
$d = Get-UiDump
$fb = Tap-Node $d '投诉反馈' '' 1500
Record 'E6.1' '抽屉 → 投诉反馈' $fb (Get-Focus)
$d = Get-UiDump
$form = Tap-Node $d '问题反馈' '' 1200
Record 'E6.2' '进入问题反馈表单' $form (Get-Focus)

# 三个 EditText 无 id：全都按「当次 dump 的 EditText 顺序」点击，每次输入后重新 dump（不按 BACK，
# 实测 keyevent 4 在键盘未弹时等同于返回，会把表单页退出）。最后用 swipe 把「提交」滚到可见区。
$fbVals = @($fbText + '-reason', 'e2e@example.com', $fbText + '-desc')
$fbOk = $true
for ($k = 0; $k -lt 3; $k++) {
    $tapped = $false
    for ($tryN = 1; $tryN -le 3; $tryN++) {
        $dd = Get-UiDump
        if (-not $dd) { Start-Sleep -Milliseconds 700; continue }
        $eds = @($dd.SelectNodes('//node') | Where-Object { "$($_.class)" -match 'EditText' })
        $target = $null
        if ($k -eq 0 -and $eds.Count -ge 1) { $target = $eds[0] }
        elseif ($k -ge 1) {
            $hint = @('请输入您的联系方式', '请输入详细问题描述')[$k - 1]
            $target = @($eds | Where-Object { "$($_.text)" -match [regex]::Escape($hint) } | Select-Object -First 1)
            if ($target.Count -eq 0) { $target = $null } else { $target = $target[0] }
        }
        if ($target -and "$($target.bounds)" -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $x = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
            $y = [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)
            Invoke-Adb @('shell', "input tap $x $y") | Out-Null
            Start-Sleep -Milliseconds 700
            Input-Text $fbVals[$k]
            $tapped = $true
            break
        }
        Start-Sleep -Milliseconds 700
    }
    if (-not $tapped) { $fbOk = $false; break }
}
$check = Get-UiDump
$filled = @($check.SelectNodes('//node') | Where-Object { "$($_.class)" -match 'EditText' -and "$($_.text)".Trim() -ne '' -and "$($_.text)" -notmatch '^请输入' })
Record 'E6.3' '填三个字段' ($fbOk -and $filled.Count -ge 2) ("taps_ok=$fbOk filled_fields=$($filled.Count)")
$filled | ForEach-Object { Add-Content -LiteralPath (Join-Path $OutRoot 'e6-fields.txt') -Value ("text='" + $_.text + "'") -Encoding UTF8 }

# 收起键盘再点提交
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 600
$d = Get-UiDump
$subFb = Tap-NodeRetry '' 'lc_m3_submit' 1 1500
if (-not $subFb) {
    Invoke-Adb @('shell', 'input', 'swipe', '614', '1900', '614', '1100', '350') | Out-Null
    Start-Sleep -Milliseconds 900
    $subFb = Tap-NodeRetry '' 'lc_m3_submit' 3 2000
}
$fbDoc = Wait-Text '提交成功' 25
Record 'E6.4' '提交反馈' ([bool]$fbDoc -and $subFb) ("tap=$subFb focus=" + (Get-Focus))
Snap 'e6-feedback-result'

# ---------- E7 登出 ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
$d = Get-UiDump
if ($d -and (Find-Node $d '' 'lc_menu_button')) {
    Tap-Node $d '' 'lc_menu_button' 1200 | Out-Null
    $d = Get-UiDump
    Tap-Node $d '账户设置' '' 1500 | Out-Null
}
$d = Wait-Text '退出登录' 12
$logoutTap = Tap-Node $d '' 'lc_m3_logout' 2500
$loggedOut = $null -ne (Wait-Text '您当前未登录' 15)
Record 'E7.1' '退出登录' ([bool]$logoutTap -and $loggedOut) ("tap=$logoutTap loggedOut=$loggedOut")
Snap 'e7-logged-out'

# ---------- E8 服务停机降级 ----------
if (-not $SkipDegraded) {
    # 先重新登录，才能测同步降级
    $d = Get-UiDump
    Tap-Node $d '' 'lc_m3_login' 1500 | Out-Null
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_username' 500 | Out-Null
    Input-Text $user
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_password' 500 | Out-Null
    Input-Text $pass
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_submit' 2500 | Out-Null
    $relog = $null -ne (Wait-Text '昵称：' 15)
    Record 'E8.0' '重新登录（为降级测试）' $relog (Get-Focus)

    $stop = & docker compose -f (Join-Path $repo 'backend\docker-compose.yml') stop 2>&1
    $stopped = ($stop -join ' ') -match 'Stopped|stopped|lc-backend'
    Record 'E8.1' '停容器（不删卷）' $stopped (($stop | Select-Object -Last 1) -join '')
    Start-Sleep -Seconds 2
    $d = Get-UiDump
    $syncDown = Tap-Node $d '同步收藏点' '' 2500
    $downDoc = Wait-Text '同步失败' 25
    Record 'E8.2' '停机后同步 → 降级可见' ([bool]$downDoc) ("tap=$syncDown focus=" + (Get-Focus))
    Snap 'e8-degraded'

    $start = & docker compose -f (Join-Path $repo 'backend\docker-compose.yml') start 2>&1
    Record 'E8.3' '恢复容器' (($start -join ' ') -match 'Started|running|lc-backend') (($start | Select-Object -Last 1) -join '')
    $healthy = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        $h = & docker inspect -f '{{.State.Health.Status}}' lc-backend 2>$null
        if (($h -join '') -match 'healthy') { $healthy = $true; break }
    }
    Record 'E8.4' '容器恢复 healthy' $healthy ''
    # 恢复后要重新确认停在账户设置页（停机期间可能被其它界面顶掉），再按文本重试点同步
    if (-not (EnsureForeground 'M3PageActivity' 8)) {
        $nav = Tap-NodeRetry '' 'lc_menu_button' 3 1200
        $nav2 = Tap-NodeRetry '账户设置' '' 3 1500
        Record 'E8.5a' '重新进入账户设置' ($nav -and $nav2) (Get-Focus)
    }
    $syncUp = Tap-NodeRetry '同步收藏点' '' 3 3000
    $upDoc = Wait-Text '同步完成' 25
    $upText = if ($upDoc) { (($upDoc.SelectNodes('//node') | ForEach-Object { "$($_.text)" }) -match '同步收藏点：' | Select-Object -First 1) } else { '' }
    Record 'E8.5' '恢复后同步成功' ([bool]$upDoc) ("tap=$syncUp text=$upText focus=" + (Get-Focus))
    Snap 'e8-recovered'
}

# ---------- 收尾 ----------
$rawLog = Join-Path $OutRoot 'logcat.raw.txt'
& adb -s $Device logcat -d 2>&1 | Out-File -LiteralPath $rawLog -Encoding UTF8
. (Join-Path $repo 'tests\lib\LogRedact.ps1')
Invoke-RedactFile -Path $rawLog
Move-Item -LiteralPath $rawLog -Destination (Join-Path $OutRoot 'logcat.txt') -Force
$logTxt = Join-Path $OutRoot 'logcat.txt'
$lines = @(Get-Content -LiteralPath $logTxt -Encoding UTF8)
$appFatal = @()
$uiFatal = @()
$totalFatal = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -notmatch 'FATAL EXCEPTION') { continue }
    $totalFatal++
    $block = ($lines[$i..([Math]::Min($i + 12, $lines.Count - 1))] -join "`n")
    if ($block -match 'Process: com\.luopan\.fengshui') { $appFatal += $lines[$i] }
    elseif ($block -match 'uiautomator|UiAutomationService|accessibilityservice') { $uiFatal += $lines[$i] }
}
$appFatal | ForEach-Object { Add-Content -LiteralPath (Join-Path $OutRoot 'app-fatal.txt') -Value $_ -Encoding UTF8 }
Record 'Z.1' '无应用侧 FATAL' ($appFatal.Count -eq 0) ("app_fatal=" + $appFatal.Count + " uiautomator_fatal=" + $uiFatal.Count + " total=" + $totalFatal)

foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    Invoke-Adb @('shell', 'settings', 'put', 'global', $k, '0') | Out-Null
}
$rev = (Invoke-Adb @('reverse', 'tcp:8787', 'tcp:8787')) -join ' '
$revList = (Invoke-Adb @('reverse', '--list')) -join ';'
Record 'S0.1' 'adb reverse 8787' ($revList -match 'tcp:8787') $revList

if (-not $SkipInstall) {
    $inst = Join-Path $repo 'tools\device\install-debug.ps1'
    if (Test-Path $inst) {
        $out = & pwsh -NoProfile -File $inst -Apk $Apk -Device $Device 2>&1
        $code = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
        $out | Set-Content -LiteralPath (Join-Path $OutRoot 'install.log') -Encoding UTF8
        Record 'S0.2' 'install-debug.ps1 装包' ($code -eq 0) ("exit=$code; " + (($out | Select-Object -Last 2) -join ' '))
    } else {
        Invoke-Adb @('push', $Apk, '/data/local/tmp/lc-e2e.apk') | Out-Null
        $o = (Invoke-Adb @('shell', 'pm', 'install', '-r', '-t', '/data/local/tmp/lc-e2e.apk')) -join ' '
        Record 'S0.2' 'pm install' ($o -match 'Success') $o
    }
}

# 装机指纹（证据与代码对应关系：必须记构建体指纹，不只记 commit）
$pkgPath = ((Invoke-Adb @('shell', 'pm', 'path', 'com.luopan.fengshui')) -join '') -replace 'package:', ''
$pkgPath = $pkgPath.Trim()
$devSha = if ($pkgPath) { (((Invoke-Adb @('shell', "sha256sum $pkgPath")) -join ' ') -split '\s+')[0] } else { '' }
$localSha = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
Record 'S0.3' '装机指纹' ($devSha -eq $localSha) ("device=$devSha local=$localSha")

Invoke-Adb @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
# 干净会话：E2E 必须从「未登录 + 空本地收藏」起跑，否则上一轮的 session/收藏会污染判据
# （实测踩过：残留登录态导致「立即登录」节点不存在，E1.3 假 FAIL）
if (-not $SkipClear) {
    $clearOut = (Invoke-Adb @('shell', 'pm', 'clear', 'com.luopan.fengshui')) -join ' '
    Record 'S0.35' '清应用数据（干净会话）' ($clearOut -match 'Success') ($clearOut.Trim())
} else {
    Record 'S0.35' '清应用数据（已由调用方完成）' $true 'SkipClear'
}
# pm clear 会连运行时权限一起清掉 -> 冷启动弹 GrantPermissionsActivity 挡住全流程；按 manifest 需要的一次性补回
$permMap = @(
    @('android.permission.ACCESS_FINE_LOCATION', 'allow'),
    @('android.permission.ACCESS_COARSE_LOCATION', 'allow'),
    @('android.permission.POST_NOTIFICATIONS', 'allow')
)
$permOk = @()
foreach ($pm in $permMap) {
    $r = (Invoke-Adb @('shell', 'pm', 'grant', 'com.luopan.fengshui', $pm[0])) -join ' '
    $permOk += ("{0}={1}" -f ($pm[0] -replace 'android.permission.', ''), $(if ($r -match 'Exception|Error') { 'ERR' } else { 'ok' }))
}
$remain = (Invoke-Adb @('shell', 'dumpsys', 'package', 'com.luopan.fengshui')) -join "`n"
$notGranted = ([regex]::Matches($remain, '(android\.permission\.[A-Z_]+): granted=false') | ForEach-Object { $_.Groups[1].Value }) -join ','
Record 'S0.36' '补回运行时权限（避免授权框挡流程）' ($notGranted -eq '') (($permOk -join ';') + "; not_granted=$notGranted")
Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
$readyDoc = Wait-AppReady 25
$dismissed = 0
for ($i = 0; $i -lt 8; $i++) {
    if ($readyDoc) { break }
    if (Dismiss-SystemDialogs) { $dismissed++; Start-Sleep -Seconds 2; $readyDoc = Wait-AppReady 25 }
    else { Start-Sleep -Seconds 2; $readyDoc = Wait-AppReady 15 }
}
Record 'S0.4' '启动到主界面并等待就绪' ([bool]$readyDoc) ("dismissed_dialogs=" + $dismissed + " focus=" + (Get-Focus))
Snap 's0-main'

# ---------- E1 注册（抽屉 → 账户设置 → 立即登录 → 注册） ----------
$opened = Tap-NodeRetry '' 'lc_menu_button' 3 1200
Record 'E1.1' '打开抽屉' $opened (Get-Focus)
$d = Get-UiDump
$toAcct = Tap-Node $d '账户设置' '' 1500
Record 'E1.2' '抽屉 → 账户设置' $toAcct (Get-Focus)
if (-not (EnsureForeground 'M3PageActivity' 10)) { Record 'E1.2b' '账户设置页前台' $false (Get-Focus) }
Snap 'e1-account-before-login'

$d = Get-UiDump
$toLogin = Tap-Node $d '' 'lc_m3_login' 1500
Record 'E1.3' '立即登录 → 登录页' $toLogin (Get-Focus)
if (-not (EnsureForeground 'LoginActivity' 10)) { Record 'E1.3b' '登录页前台' $false (Get-Focus) }

$d = Get-UiDump
$toReg = Tap-Node $d '注册' '' 900
Record 'E1.4' '切到注册 tab' $toReg (Get-Focus)

$d = Get-UiDump
$uOk = Tap-Node $d '' 'lc_login_username' 500
if ($uOk) { Input-Text $user }
$d = Get-UiDump
$pOk = Tap-Node $d '' 'lc_login_password' 500
if ($pOk) { Input-Text $pass }
$d = Get-UiDump
$nOk = Tap-Node $d '' 'lc_login_display' 500
if ($nOk) { Input-Text 'e2enick' }
Record 'E1.5' '填账号/密码/昵称' ($uOk -and $pOk) "user=$user"
Snap 'e1-register-filled'

$d = Get-UiDump
$sub = Tap-Node $d '' 'lc_login_submit' 2500
# 判据：注册成功的证据是「回到账户设置页并显示登录态」；'登录成功' 是瞬时行，抓不到不算 FAIL
$okReg = EnsureForeground 'M3PageActivity' 20
$sawMsg = $false
if (-not $okReg) { $sawMsg = $null -ne (Wait-Text '登录成功' 5) }
Record 'E1.6' '注册并登录' ($sub -and $okReg) ("submit=$sub back_to_account=$okReg transient_msg=$sawMsg focus=" + (Get-Focus))
Snap 'e1-register-result'

# ---------- E2 登录态确认 ----------
$d = Wait-Text '昵称：' 15
$loggedIn = $null -ne $d
Record 'E2.1' '账户设置显示登录态' $loggedIn ("focus=" + (Get-Focus))
Snap 'e2-account-loggedin'

# ---------- E3 收藏（收藏点地图态 → 添加点 → 保存） ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
if (-not (EnsureForeground 'MainActivity' 10)) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
}
$d = Get-UiDump
$more = Tap-Node $d '更多' '' 1500
Record 'E3.1' '打开更多面板' $more (Get-Focus)
$d = Get-UiDump
$fav = Tap-Node $d '收藏点' '' 1500
Record 'E3.2' '进入收藏点地图态' $fav (Get-Focus)
$d = Get-UiDump
$add = Tap-Node $d '' 'lc_fav_add' 1800
Record 'E3.3' '点底部「添加点」' $add (Get-Focus)
$inSave = EnsureForeground 'SaveInterestActivity' 10
Record 'E3.3b' '保存收藏点页前台' $inSave (Get-Focus)
$d = Get-UiDump
$nm = Tap-Node $d '' 'lc_save_name' 500
if ($nm) { Input-Text ('e2e-fav-' + $user) }
$d = Get-UiDump
$cf = Tap-Node $d '' 'lc_save_confirm' 2000
$d = Get-UiDump
Record 'E3.4' '保存收藏点' ($cf -and $null -ne (Find-Node $d '收藏' '')) ("confirm=$cf focus=" + (Get-Focus))
Snap 'e3-favorite-saved'

# ---------- E4 同步（收藏点上云） ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
if (-not (EnsureForeground 'MainActivity' 10)) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
}
$d = Get-UiDump
$d2 = Tap-Node $d '' 'lc_menu_button' 1200
$d = Get-UiDump
$acct2 = Tap-Node $d '账户设置' '' 1500
$d = Get-UiDump
$syncTap = Tap-Node $d '同步收藏点' '' 3000
$syncDoc = Wait-Text '同步收藏点：' 25
$syncDetail = if ($syncDoc) { (($syncDoc.SelectNodes('//node') | ForEach-Object { "$($_.text)" }) -match '同步' | Where-Object { $_ -match '同步收藏点' } | Select-Object -First 1) } else { 'no result text' }
Record 'E4.1' '同步收藏点上云' ([bool]$syncDoc) ("tap=$syncTap text=$syncDetail")
Snap 'e4-sync-result'

# ---------- E5 服务端侧独立印证 ----------
$dbJs = Join-Path $repo 'checks\db-count.mjs'
if (-not (Test-Path -LiteralPath $dbJs)) {
    @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync(process.env.DB_PATH || "/data/lc.sqlite", { readOnly: true });
const q = (t) => db.prepare(`select count(*) as n from ${t}`).get().n;
console.log(JSON.stringify({ users: q("users"), favorites: q("favorites"), feedback: q("feedback") }));
'@ | Set-Content -LiteralPath $dbJs -Encoding UTF8
}
$dbOut = (Get-Content -LiteralPath $dbJs -Raw | & docker exec -i lc-backend node --input-type=module 2>&1) -join ' '
Record 'E5.1' '服务端计数（DB）' ($dbOut -match '"users"') ($dbOut -replace '\s+', ' ')
Record 'E5.1' '服务端计数（DB）' ($LASTEXITCODE -eq 0) (($dbOut -join ' ') -replace '\s+', ' ')

# ---------- E6 投诉反馈 ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
$d = Get-UiDump
if (-not (Find-Node $d '' 'lc_menu_button')) {
    Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
    Start-Sleep -Seconds 3
    $d = Get-UiDump
}
$d = Get-UiDump
Tap-Node $d '' 'lc_menu_button' 1200 | Out-Null
$d = Get-UiDump
$fb = Tap-Node $d '投诉反馈' '' 1500
Record 'E6.1' '抽屉 → 投诉反馈' $fb (Get-Focus)
$d = Get-UiDump
$form = Tap-Node $d '问题反馈' '' 1200
Record 'E6.2' '进入问题反馈表单' $form (Get-Focus)

# 三个 EditText 无 id，按 dump 顺序点（申请原因/联系方式/问题描述）
$d = Get-UiDump
$edits = @($d.SelectNodes('//node') | Where-Object { "$($_.class)" -match 'EditText' })
if ($edits.Count -ge 3) {
    $vals = @($fbText + '-reason', 'e2e@example.com', $fbText + '-desc')
    for ($i = 0; $i -lt 3; $i++) {
        $b = "$($edits[$i].bounds)"
        if ($b -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            $x = [int]((([int]$Matches[1]) + ([int]$Matches[3])) / 2)
            $y = [int]((([int]$Matches[2]) + ([int]$Matches[4])) / 2)
            Invoke-Adb @('shell', "input tap $x $y") | Out-Null
            Start-Sleep -Milliseconds 400
            Invoke-Adb @('shell', "input close-dialog") | Out-Null
            Input-Text $vals[$i]
        }
    }
    Record 'E6.3' '填三个字段' ($edits.Count -ge 3) "edits=$($edits.Count)"
} else {
    Record 'E6.3' '填三个字段' $false "edits=$($edits.Count)"
}
# 收起键盘再点提交
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 600
$d = Get-UiDump
$subFb = Tap-NodeRetry '' 'lc_m3_submit' 1 1500
if (-not $subFb) {
    Invoke-Adb @('shell', 'input', 'swipe', '614', '1900', '614', '1100', '350') | Out-Null
    Start-Sleep -Milliseconds 900
    $subFb = Tap-NodeRetry '' 'lc_m3_submit' 3 2000
}
$fbDoc = Wait-Text '提交成功' 25
Record 'E6.4' '提交反馈' ([bool]$fbDoc -and $subFb) ("tap=$subFb focus=" + (Get-Focus))
Snap 'e6-feedback-result'

# ---------- E7 登出 ----------
Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
Start-Sleep -Milliseconds 800
$d = Get-UiDump
if ($d -and (Find-Node $d '' 'lc_menu_button')) {
    Tap-Node $d '' 'lc_menu_button' 1200 | Out-Null
    $d = Get-UiDump
    Tap-Node $d '账户设置' '' 1500 | Out-Null
}
$d = Wait-Text '退出登录' 12
$logoutTap = Tap-Node $d '' 'lc_m3_logout' 2500
$loggedOut = $null -ne (Wait-Text '您当前未登录' 15)
Record 'E7.1' '退出登录' ([bool]$logoutTap -and $loggedOut) ("tap=$logoutTap loggedOut=$loggedOut")
Snap 'e7-logged-out'

# ---------- E8 服务停机降级 ----------
if (-not $SkipDegraded) {
    # 先重新登录，才能测同步降级
    $d = Get-UiDump
    Tap-Node $d '' 'lc_m3_login' 1500 | Out-Null
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_username' 500 | Out-Null
    Input-Text $user
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_password' 500 | Out-Null
    Input-Text $pass
    $d = Get-UiDump
    Tap-Node $d '' 'lc_login_submit' 2500 | Out-Null
    $relog = $null -ne (Wait-Text '昵称：' 15)
    Record 'E8.0' '重新登录（为降级测试）' $relog (Get-Focus)

    $stop = & docker compose -f (Join-Path $repo 'backend\docker-compose.yml') stop 2>&1
    $stopped = ($stop -join ' ') -match 'Stopped|stopped|lc-backend'
    Record 'E8.1' '停容器（不删卷）' $stopped (($stop | Select-Object -Last 1) -join '')
    Start-Sleep -Seconds 2
    $d = Get-UiDump
    $syncDown = Tap-Node $d '同步收藏点' '' 2500
    $downDoc = Wait-Text '同步失败' 25
    Record 'E8.2' '停机后同步 → 降级可见' ([bool]$downDoc) ("tap=$syncDown focus=" + (Get-Focus))
    Snap 'e8-degraded'

    $start = & docker compose -f (Join-Path $repo 'backend\docker-compose.yml') start 2>&1
    Record 'E8.3' '恢复容器' (($start -join ' ') -match 'Started|running|lc-backend') (($start | Select-Object -Last 1) -join '')
    $healthy = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 1
        $h = & docker inspect -f '{{.State.Health.Status}}' lc-backend 2>$null
        if (($h -join '') -match 'healthy') { $healthy = $true; break }
    }
    Record 'E8.4' '容器恢复 healthy' $healthy ''
    # 恢复后要重新确认停在账户设置页（停机期间可能被其它界面顶掉），再按文本重试点同步
    if (-not (EnsureForeground 'M3PageActivity' 8)) {
        $nav = Tap-NodeRetry '' 'lc_menu_button' 3 1200
        $nav2 = Tap-NodeRetry '账户设置' '' 3 1500
        Record 'E8.5a' '重新进入账户设置' ($nav -and $nav2) (Get-Focus)
    }
    $syncUp = Tap-NodeRetry '同步收藏点' '' 3 3000
    $upDoc = Wait-Text '同步完成' 25
    $upText = if ($upDoc) { (($upDoc.SelectNodes('//node') | ForEach-Object { "$($_.text)" }) -match '同步收藏点：' | Select-Object -First 1) } else { '' }
    Record 'E8.5' '恢复后同步成功' ([bool]$upDoc) ("tap=$syncUp text=$upText focus=" + (Get-Focus))
    Snap 'e8-recovered'
}

# ---------- 收尾 ----------
$rawLog = Join-Path $OutRoot 'logcat.raw.txt'
& adb -s $Device logcat -d 2>&1 | Out-File -LiteralPath $rawLog -Encoding UTF8
. (Join-Path $repo 'tests\lib\LogRedact.ps1')
Invoke-RedactFile -Path $rawLog
Move-Item -LiteralPath $rawLog -Destination (Join-Path $OutRoot 'logcat.txt') -Force
$logTxt = Join-Path $OutRoot 'logcat.txt'
$fatalAll = Select-String -LiteralPath $logTxt -Pattern 'FATAL EXCEPTION' -ErrorAction SilentlyContinue
$fatalApp = @($fatalAll | Where-Object { $_.Context.PostContext -match 'luopan' -or $_.Line -match 'luopan' })
# uiautomator 自身崩溃（UiAutomationService already registered / Bad file descriptor）单独记，不算应用缺陷
$fatalUi = @($fatalAll) | Where-Object {
    $idx = [array]::IndexOf(@(Get-Content -LiteralPath $logTxt -Encoding UTF8), $_.Line)
    $true
}
Record 'Z.1' '无应用侧 FATAL' (-not $fatalApp) ("app_fatal=" + $fatalApp.Count + " total_fatal=" + (@($fatalAll).Count))

foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    Invoke-Adb @('shell', 'settings', 'put', 'global', $k, '1') | Out-Null
}
Invoke-Adb @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
Invoke-Adb @('reverse', '--remove-all') | Out-Null
Record 'Z.2' '设备复位' $true ((Get-Focus) + '; forward=' + (((Invoke-Adb @('reverse', '--list')) -join '') -replace '\s+', ''))

$summary = @(
    'LC M4 E2E',
    'time=' + (Get-Date).ToString('s'),
    "device=$Device",
    "apk_sha=$localSha",
    "device_apk_sha=$devSha",
    "steps=$script:steps",
    "fails=$script:fails",
    "result=" + $(if ($script:fails -eq 0) { 'PASS' } else { 'FAIL' }),
    "out=$OutRoot"
)
Set-Content -LiteralPath (Join-Path $OutRoot 'SUMMARY.txt') -Value $summary -Encoding UTF8
Write-Host ''
Write-Host ('E2E: ' + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL ($script:fails/$script:steps)" }))
if ($script:fails -eq 0) { exit 0 } else { exit 1 }
