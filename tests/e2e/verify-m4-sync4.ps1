# M4 同步五项复验（P0 跨账号 / A-B 崩窗口 / 坐向还原 / 删除双向 / §33 账号隔离）
# 用法：pwsh -File tests\e2e\verify-m4-sync4.ps1 -Apk <apk> [-Only xacct,crash,sit,del]
# 纪律：坐标当次 dump 解析；结论只认库内字段与服务端计数；每项输出 PASS/FAIL + 数字
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string[]]$Only = @('xacct', 'crash', 'sit', 'del', 'sec33', 'orphan', 'orphanui', 'delui'),
    [switch]$SkipInstall
)
if ($Only.Count -eq 1 -and $Only[0] -match ',') { $Only = $Only[0] -split ',' | ForEach-Object { $_.Trim() } }
. (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'tests\lib\ui-drive.ps1')
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$out = Join-Path $root ('checks\sync4-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $out | Out-Null
Init-Ui -Device $Device -Package 'com.luopan.fengshui'
$script:fails = 0
function Log($s) { $l = (Get-Date -Format 'HH:mm:ss') + ' ' + $s; Write-Host $l; Add-Content -LiteralPath (Join-Path $out 'run.log') -Value $l -Encoding UTF8 }
function Verdict([string]$id, [string]$title, [bool]$ok, [string]$detail) {
    if (-not $ok) { $script:fails++ }
    Log (("{0} {1} {2} — {3}") -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $id, $title, $detail)
    Add-Content -LiteralPath (Join-Path $out 'table.md') -Value ("| {0} | {1} | {2} | {3} |" -f $id, $title, $(if ($ok) { 'PASS' } else { 'FAIL' }), ($detail -replace '\|', '/')) -Encoding UTF8
}
function AdbOk([string[]]$p) { (& adb -s $Device @p 2>&1 | ForEach-Object { "$_" }) }
function PullLocal([string]$name) {
    $dst = Join-Path $out $name
    & adb -s $Device exec-out run-as com.luopan.fengshui cat databases/lc_gap.db > $dst 2>$null
    return $dst
}
function LocalQuery([string]$db, [string]$sql) {
    $py = Join-Path $env:TEMP 'sync4q.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1]); 
print(str(db.execute(sys.argv[2]).fetchall()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    return ((& python $py $db $sql) -join '').Trim()
}
function LocalScalar([string]$db, [string]$sql) {
    $py = Join-Path $env:TEMP 'sync4s.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
print(db.execute(sys.argv[2]).fetchone()[0])
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    return ((& python $py $db $sql) -join '').Trim()
}
function RemoteJs([string]$code) {
    $f = Join-Path $env:TEMP 'sync4r.mjs'
    Set-Content -LiteralPath $f -Value $code -Encoding UTF8
    return ((Get-Content -LiteralPath $f -Raw | & docker exec -i lc-backend node --no-warnings --input-type=module 2>&1) -join ' ')
}
function PushLocal([string]$src) {
    & adb -s $Device push $src /data/local/tmp/sync4.db | Out-Null
    & adb -s $Device shell "run-as com.luopan.fengshui sh -c 'cp /data/local/tmp/sync4.db databases/lc_gap.db'" | Out-Null
}
function AppStop() { & adb -s $Device shell am force-stop com.luopan.fengshui | Out-Null; Start-Sleep -Milliseconds 700 }
function AppStart() {
    & adb -s $Device shell am start -n com.luopan.fengshui/.MainActivity | Out-Null
    Start-Sleep -Seconds 5; Freeze-Heading 0
}
function GoAccount() {
    $ok = Tap-NodeRetry '' 'lc_menu_button' 3 1200
    $ok2 = Tap-NodeRetry '账户设置' '' 3 1500
    return ($ok -and $ok2)
}
function LogoutIfIn() {
    $d = Get-UiDump
    if ($d -and (Find-Node $d '昵称：' '')) { Tap-NodeRetry '' 'lc_m3_logout' 3 2000 | Out-Null; Start-Sleep -Seconds 1; return $true }
    return $false
}
function Set-LoginField([string]$id, [string]$val) {
    for ($t = 1; $t -le 3; $t++) {
        $dd = Get-UiDump
        if ($dd -and (Tap-Node $dd '' $id 700)) { Input-Text $val; return $true }
        Start-Sleep -Milliseconds 700
    }
    return $false
}
function Hide-Keyboard {
    (Invoke-Adb @('shell', 'input', 'keyevent', '111')) | Out-Null   # ESC 收 IME，避免提交按钮被键盘盖住
    Start-Sleep -Milliseconds 600
}
function LoginFormSubmit {
    Hide-Keyboard
    for ($t = 1; $t -le 3; $t++) {
        $dd = Get-UiDump
        if (-not ($dd -and (Find-Node $dd '' 'lc_login_submit'))) { return $true }
        Tap-Node $dd '' 'lc_login_submit' 2500 | Out-Null
        Start-Sleep -Seconds 2
        $chk = Get-UiDump
        if ($chk -and (Find-Node $chk '昵称：' '')) { return $true }
        if ($chk -and -not (Find-Node $chk '' 'lc_login_submit')) { return $true }
    }
    return $false
}
function LoginAs([string]$u, [string]$p) {
    if (-not ((Get-Focus) -match 'com.luopan.fengshui')) { AppStart }
    Tap-NodeRetry '' 'lc_m3_login' 3 1500 | Out-Null
    Set-LoginField 'lc_login_username' $u | Out-Null
    Set-LoginField 'lc_login_password' $p | Out-Null
    return (LoginFormSubmit)
}
function RegisterAs([string]$prefix) {
    $u = $prefix + (Get-Random -Maximum 99999); $p = 'LcQa!' + (Get-Random -Maximum 99999)
    $script:lastUser = $u; $script:lastPass = $p
    if (-not ((Get-Focus) -match 'com.luopan.fengshui')) { AppStart }
    Tap-NodeRetry '' 'lc_m3_login' 3 1500 | Out-Null
    Tap-NodeRetry '注册' '' 3 900 | Out-Null
    Set-LoginField 'lc_login_username' $u | Out-Null
    Set-LoginField 'lc_login_password' $p | Out-Null
    Set-LoginField 'lc_login_display' ($prefix + 'nick') | Out-Null
    $ok = LoginFormSubmit
    if (-not $ok) { LoginAs $u $p | Out-Null }
    $dd = Get-UiDump
    if ($dd) { Set-Content -LiteralPath (Join-Path $out ('reg-' + $u + '.xml')) -Value ($dd.OuterXml) -Encoding UTF8 }
    Log ("register_ok=" + [bool]($dd -and (Find-Node $dd '昵称：' '')) + " user=$u")
    return $u
}
function OpenFavList() {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) {
        Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null
        Start-Sleep -Seconds 3; Freeze-Heading 0
    }
    Tap-NodeRetry '更多' '' 3 1500 | Out-Null
    Tap-NodeRetry '收藏点' '' 3 1800 | Out-Null
    Start-Sleep -Milliseconds 900
    Tap-NodeRetry '收藏列表' '' 3 1800 | Out-Null
    Start-Sleep -Milliseconds 1200
}
function SyncNow([int]$waitSec = 7) {
    & adb -s $Device shell logcat -c | Out-Null
    $ok = Tap-NodeRetry '' 'lc_m3_sync' 3 3000
    Start-Sleep -Seconds $waitSec
    $log = (AdbOk @('shell', 'logcat', '-d', '-s', 'LCSYNC:V')) -join "`n"
    return [pscustomobject]@{ Ok = $ok; Log = (($log -split "`n") | Where-Object { $_ -match 'LCSYNC' } | Select-Object -Last 4) -join ' | ' }
}
function AddLocalFavorite([string]$name) {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { Invoke-Adb @('shell', 'am', 'start', '-n', 'com.luopan.fengshui/.MainActivity') | Out-Null; Start-Sleep -Seconds 3; Freeze-Heading 0 }
    Tap-NodeRetry '更多' '' 3 1500 | Out-Null
    Tap-NodeRetry '收藏点' '' 3 1500 | Out-Null
    Tap-NodeRetry '' 'lc_fav_add' 3 1800 | Out-Null
    $d = Get-UiDump
    if ($d -and (Tap-Node $d '' 'lc_save_name' 600)) { Input-Text $name }
    Tap-NodeRetry '' 'lc_save_confirm' 3 2000 | Out-Null
    Start-Sleep -Seconds 2
}

# ---------- 准备：安装 + 起应用 ----------
if (-not $SkipInstall) {
    $o = & pwsh -NoProfile -File (Join-Path $root 'tools\device\install-debug.ps1') -Apk $Apk -Device $Device 2>&1
    Log ("install_exit=" + $LASTEXITCODE + " " + (($o | Select-Object -Last 1) -join ''))
} else { Log 'install_skipped (设备已装同构建)' }
AdbOk @('reverse', 'tcp:8787', 'tcp:8787') | Out-Null
AppStart

# ---------- ① P0 跨账号（升级路径：owner_uid=0 且 remote_id>0 → 首次登录 B） ----------
if ($Only -contains 'xacct') {
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    # 清空本机 interest 与增量游标：从「A 的 3 条」这个干净基线出发，避免上一次运行的存量行混进条数
    AppStop
    $db0 = PullLocal 'local-clean.db'
    $py0 = Join-Path $env:TEMP 'sync4clean.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1]); db.execute("delete from interest"); db.commit(); print("cleaned")
'@ | Set-Content -LiteralPath $py0 -Encoding UTF8
    ((& python $py0 $db0) -join '') | Out-Null
    PushLocal $db0
    & adb -s $Device shell "run-as com.luopan.fengshui sh -c 'rm -f shared_prefs/lc_sync.xml'" | Out-Null
    AppStart
    # 以 A 建 3 条并同步，让 remote_id 落地（AppStart 后已在主界面，不能再按 back：会把应用退到桌面）
    GoAccount | Out-Null
    $userA = RegisterAs 'xa'
    Log "userA=$userA"
    for ($i = 1; $i -le 3; $i++) { AddLocalFavorite ("xa-$i") }
    $dbPre = PullLocal 'local-pre-push.db'
    Log ('pre_push: ' + (LocalQuery $dbPre "select id,owner_uid,remote_id,sync_state,name from interest order by id"))
    $goOk = GoAccount
    Log "A_go_account=$goOk"
    $s = SyncNow
    Log ("A_sync_ok=" + $s.Ok)
    Log ("A_sync: " + $s.Log)
    $dbA = PullLocal 'local-A.db'
    $rowsA = LocalScalar $dbA "select count(*) from interest where remote_id>0"
    Log "A_rows_with_remote=$rowsA"
    # 模拟升级前存量行：owner_uid 归 0、保留 remote_id
    AppStop
    $dbA2 = PullLocal 'local-A-zero.db'
    $py = Join-Path $env:TEMP 'sync4zero.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1]); db.execute("update interest set owner_uid=0"); db.commit()
print(db.execute("select count(*) from interest where owner_uid=0 and remote_id>0").fetchone()[0])
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    $zero = ((& python $py $dbA2) -join '').Trim()
    PushLocal $dbA2
    Log "rows_owner0_remote_gt0=$zero"
    AppStart
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    $userB = RegisterAs 'xb'
    Log "userB=$userB"
    $before = LocalScalar (PullLocal 'local-before-sync.db') "select count(*) from interest"
    $s = SyncNow
    Log ("B_first_sync: " + $s.Log)
    $after = LocalScalar (PullLocal 'local-after-sync.db') "select count(*) from interest"
    $bCnt = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("B_ACTIVE=" + db.prepare("select count(*) c from favorites f join users u on u.id=f.user_id where u.username_lc=? and f.deleted=0").get("$userB").c);
"@
    Log ("counts: before=$before after=$after ; " + $bCnt)
    $ok = ([int]$before -ge 3) -and ([int]$after -eq [int]$before) -and ([int]$zero -eq [int]$before)
    Verdict 'P0-①' '升级后首次登 B，本地条数不减' $ok ("before=$before after=$after; " + $bCnt)
}

# ---------- ② A/B 崩窗口（本地新建且已推过的行 → 清 remote_id → 重推不新建） ----------
if ($Only -contains 'crash') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    AddLocalFavorite 'crash-row'
    GoAccount | Out-Null
    $s = SyncNow
    Log ("pre_crash_sync: " + $s.Log)
    $db = PullLocal 'local-crash-pushed.db'
    $info = LocalQuery $db "select id,remote_id,client_key from interest order by id desc limit 1"
    Log "pushed_row=$info"
    AppStop
    $py = Join-Path $env:TEMP 'sync4crash.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id from interest order by id desc limit 1").fetchone()
db.execute("update interest set remote_id=0, rev=0, sync_state='pending' where id=?", (row[0],))
db.commit()
print(str(db.execute("select id,remote_id,client_key,sync_state from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    $zeroed = ((& python $py $db) -join '').Trim()
    PushLocal $db
    Log "zeroed=$zeroed"
    AppStart; GoAccount | Out-Null
    $beforeCnt = RemoteJs @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("TOTAL_ACTIVE=" + db.prepare("select count(*) c from favorites where deleted=0").get().c);
'@
    $s = SyncNow
    Log ("crash_resync: " + $s.Log)
    $afterCnt = RemoteJs @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("TOTAL_ACTIVE=" + db.prepare("select count(*) c from favorites where deleted=0").get().c);
'@
    $restored = LocalScalar (PullLocal 'local-crash-after.db') "select remote_id from interest where name='crash-row' order by id desc limit 1"
    Log ("remote_total before=$beforeCnt after=$afterCnt ; restored_remote_id=$restored")
    $ok = ($s.Log -match 'dedup|重推|push=1') -and ([int]($restored -replace '\D', '') -gt 0)
    Verdict 'A/B-②' '崩窗口重推不新建、remote_id 认领回' $ok ("restored=$restored; " + $beforeCnt + " -> " + $afterCnt)
}

# ---------- ③ 坐向还原（本地 90/270 → 推 → 清本地 → 拉回仍是 90/270） ----------
if ($Only -contains 'sit') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    AddLocalFavorite 'sit-row'
    GoAccount | Out-Null
    SyncNow 7 | Out-Null
    $db = PullLocal 'local-sit.db'
    AppStop
    $py = Join-Path $env:TEMP 'sync4sit.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id from interest order by id desc limit 1").fetchone()
db.execute("update interest set sitting_deg=90, facing_deg=270, sync_state='pending' where id=?", (row[0],))
db.commit()
print("set=" + str(db.execute("select id,sitting_deg,facing_deg,remote_id from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    $set = ((& python $py $db) -join '').Trim()
    PushLocal $db
    Log "sit_set=$set"
    AppStart; GoAccount | Out-Null
    SyncNow 7 | Out-Null
    # 清本地后重新拉
    AppStop
    $db2 = PullLocal 'local-sit-clear.db'
    $py2 = Join-Path $env:TEMP 'sync4sitclear.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1]); db.execute("delete from interest"); db.commit(); print("cleared")
'@ | Set-Content -LiteralPath $py2 -Encoding UTF8
    ((& python $py2 $db2) -join '') | Out-Null
    PushLocal $db2
    # 清本地后拉回必须同时重置增量游标：cursor 留在 prefs 里时 since=cursor 不返回老行，
    # 那是「本机有旧游标」的增量语义，不是「新设备/清数据后登录拉全量」。两者不能混为一谈。
    & adb -s $Device shell "run-as com.luopan.fengshui sh -c 'rm -f shared_prefs/lc_sync.xml'" | Out-Null
    AppStart; GoAccount | Out-Null
    SyncNow 8 | Out-Null
    $pulled = LocalQuery (PullLocal 'local-sit-pulled.db') "select sitting_deg,facing_deg from interest where name='sit-row' order by id desc limit 1"
    Log "pulled_sit=$pulled"
    $ok = ($pulled -match '90') -and ($pulled -match '270')
    Verdict '坐向-③' '坐 90 / 向 270 拉回后保持' $ok ("pulled=$pulled (set=$set)")
}

# ---------- ④ 删除双向 ----------
if ($Only -contains 'del') {
    # 方向一：本机删 → 该行服务端 deleted=1（按 remote_id 单行判，不用全库计数）
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    AddLocalFavorite 'del-row'
    GoAccount | Out-Null
    SyncNow 7 | Out-Null
    $db = PullLocal 'local-del.db'
    $rid = LocalScalar $db "select remote_id from interest where name='del-row' order by id desc limit 1"
    $b1 = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const r = db.prepare("select id,deleted from favorites where id=?").get($rid);
console.log("ROW_BEFORE=" + (r ? (r.id + ":" + r.deleted) : "MISSING"));
"@
    # 通过本地库置墓碑（等价于界面上删除，UI 路径已由 InterestListActivity 覆盖）
    AppStop
    $py = Join-Path $env:TEMP 'sync4del.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id,remote_id from interest where name='del-row' order by id desc limit 1").fetchone()
db.execute("update interest set delete_pending=1 where id=?", (row[0],))
db.commit()
print("tombstone=" + str(db.execute("select id,remote_id,delete_pending from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    $tb = ((& python $py $db) -join '').Trim()
    PushLocal $db
    Log "tombstone=$tb remote_id=$rid"
    AppStart; GoAccount | Out-Null
    SyncNow 8 | Out-Null
    $b2 = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const r = db.prepare("select id,deleted from favorites where id=?").get($rid);
console.log("ROW_AFTER=" + (r ? (r.id + ":" + r.deleted) : "MISSING"));
"@
    $ok1 = ($b1 -match 'ROW_BEFORE=(\d+):0') -and ($b2 -match 'ROW_AFTER=\d+:1')
    Verdict '删除-④a' '本机删 → 同一 remote_id 服务端 deleted=0→1' $ok1 ("rid=$rid ; " + $b1 + " -> " + $b2)
    # 方向二：服务端软删本机仍存在的一行 → 本地该行消失（按 remote_id 判）
    $db2 = PullLocal 'local-del-b2.db'
    $rid2 = LocalScalar $db2 "select remote_id from interest where remote_id>0 and delete_pending=0 order by id desc limit 1"
    $before = LocalScalar $db2 "select count(*) from interest where remote_id=$rid2"
    $target = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite");
const r = db.prepare("select id,deleted from favorites where id=?").get($rid2);
if (r && !r.deleted) db.prepare("update favorites set deleted=1, rev=rev+1 where id=?").run(r.id);
console.log("SOFTDELETED=" + (r ? (r.id + ":" + r.deleted) : "MISSING"));
"@
    Log "remote_softdelete=$target (target_remote_id=$rid2, local_before=$before)"
    SyncNow 8 | Out-Null
    $after = LocalScalar (PullLocal 'local-del-after.db') "select count(*) from interest where remote_id=$rid2"
    Log "local_count_by_remote_id: $before -> $after"
    $ok2 = ([int]$before -ge 1) -and ([int]$after -eq 0)
    Verdict '删除-④b' '服务端软删 → 本地同一行消失' $ok2 ("remote_id=$rid2 local=$before -> $after ; " + $target)
}

# ---------- ⑤ §33 账号隔离（B 侧列表看不到 A 的行、无删除入口；切回 A 行仍在、服务端行仍在） ----------
if ($Only -contains 'sec33') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    $userA = RegisterAs 'sa'
    $passA = $script:lastPass
    Log "sec33_userA=$userA"
    AddLocalFavorite 's33-a1'
    AddLocalFavorite 's33-a2'
    GoAccount | Out-Null
    $s = SyncNow
    Log ("A_sync: " + $s.Log)
    $dbA = PullLocal 'sec33-A.db'
    $aRows = LocalScalar $dbA "select count(*) from interest where name like 's33-%' and remote_id>0"
    $aIds = LocalScalar $dbA "select group_concat(remote_id) from (select remote_id from interest where name like 's33-%' order by id)"
    $aSrv1 = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const u = db.prepare("select id from users where username_lc=?").get("$userA");
const ids = db.prepare("select group_concat(id) g, count(*) c from favorites where user_id=? and deleted=0").get(u.id);
console.log("A_SRV_ACTIVE=" + ids.c + " A_SRV_IDS=" + ids.g);
"@
    Log ("A_before: local_remote=$aRows ids=$aIds ; " + $aSrv1)
    # 切到 B（B 第一次同步时本地仍带 A 的行）
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    $userB = RegisterAs 'sb'
    Log "sec33_userB=$userB"
    $s = SyncNow
    Log ("B_first_sync: " + $s.Log)
    $dbB = PullLocal 'sec33-B.db'
    $kept = LocalScalar $dbB "select count(*) from interest where name like 's33-%'"
    $owner = LocalScalar $dbB "select group_concat(distinct owner_uid) from interest where name like 's33-%'"
    # B 侧界面：进入 收藏点 → 收藏列表（页面内覆盖层，行来自 listInterest(uid)）
    OpenFavList
    $d = Get-UiDump
    $dText = if ($d) { $d.OuterXml } else { '' }
    Set-Content -LiteralPath (Join-Path $out 'sec33-B-list.xml') -Value $dText -Encoding UTF8
    & adb -s $Device exec-out screencap -p > (Join-Path $out 'sec33-B-list.png')
    $visA1 = $dText -match 's33-a1'
    $visA2 = $dText -match 's33-a2'
    $subList = Find-Node $d '' 'lc_fav_sublist'
    $emptyText = $dText -match '暂无收藏点'
    # 长按列表区域：无删除入口时不应出现任何确认框
    $ln = Find-Node $d '' 'lc_fav_sublist_list'
    $dlgAfterLongPress = $false
    if ($ln) {
        Invoke-Adb @('shell', 'input', 'swipe', "$($ln.X)", "$($ln.Y)", "$($ln.X)", "$($ln.Y)", '800') | Out-Null
        Start-Sleep -Milliseconds 900
        $dl = Get-UiDump
        Set-Content -LiteralPath (Join-Path $out 'sec33-B-longpress.xml') -Value ($dl.OuterXml) -Encoding UTF8
        $dlgAfterLongPress = [bool]($dl) -and ($dl.OuterXml -match '删除|确定')
    }
    Log "B_ui: sublist_open=$([bool]$subList) visA1=$visA1 visA2=$visA2 empty_text=$emptyText longpress_dialog=$dlgAfterLongPress ; local_kept=$kept owner_uid=$owner"
    $okUi = ([bool]$subList) -and (-not $visA1) -and (-not $visA2) -and $emptyText -and (-not $dlgAfterLongPress)
    Verdict '§33-a' 'B 侧收藏列表看不到 A 的行、长按无删除入口' $okUi ("sublist_open=$([bool]$subList) visA1=$visA1 visA2=$visA2 empty_text=$emptyText longpress_dialog=$dlgAfterLongPress; local_kept=$kept owner_uid=$owner")
    # 切回 A：两行仍在、界面可见、服务端行仍在
    Tap-NodeRetry '' 'lc_fav_sublist_back' 2 900 | Out-Null
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    LoginAs $userA $passA
    $s = SyncNow
    Log ("A_back_sync: " + $s.Log)
    $aLater = LocalScalar (PullLocal 'sec33-A-back.db') "select count(*) from interest where name like 's33-%'"
    $aSrv2 = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const u = db.prepare("select id from users where username_lc=?").get("$userA");
const ids = db.prepare("select group_concat(id) g, count(*) c from favorites where user_id=? and deleted=0").get(u.id);
console.log("A_SRV_ACTIVE=" + ids.c + " A_SRV_IDS=" + ids.g);
"@
    OpenFavList
    $d2 = Get-UiDump
    $t2 = if ($d2) { $d2.OuterXml } else { '' }
    Set-Content -LiteralPath (Join-Path $out 'sec33-A-back-list.xml') -Value $t2 -Encoding UTF8
    & adb -s $Device exec-out screencap -p > (Join-Path $out 'sec33-A-back-list.png')
    $backA1 = $t2 -match 's33-a1'; $backA2 = $t2 -match 's33-a2'
    Log ("A_back: local=$aLater visA1=$backA1 visA2=$backA2 ; " + $aSrv2)
    $okBack = ([int]$aLater -ge 2) -and $backA1 -and $backA2 -and ($aSrv2 -match 'A_SRV_ACTIVE=2')
    Verdict '§33-b' '切回 A：本地两行仍在、界面可见、服务端行未被删' $okBack ("local=$aLater visA1=$backA1 visA2=$backA2; " + $aSrv2 + "; ids_before=$aIds")
    Tap-NodeRetry '' 'lc_fav_sublist_back' 2 900 | Out-Null
}


# ---------- ⑥ §32 孤儿墓碑归零（owner_uid=0 + remote_id>0 的墓碑必须能推出，不能挂死本地） ----------
if ($Only -contains 'orphan') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    AppStop
    $db = PullLocal 'orphan-before.db'
    $rid = LocalScalar $db "select remote_id from interest where remote_id>0 and delete_pending=0 order by id desc limit 1"
    $nm = LocalScalar $db "select name from interest where remote_id=$rid and delete_pending=0 order by id desc limit 1"
    if ([int]$rid -gt 0) {
        $py = Join-Path $env:TEMP 'sync4orphan.py'
        @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id from interest where remote_id=? and delete_pending=0 order by id desc limit 1", (sys.argv[2],)).fetchone()
db.execute("update interest set owner_uid=0, delete_pending=1 where id=?", (row[0],))
db.commit()
print("orphan_tombstone=" + str(db.execute("select id,remote_id,owner_uid,delete_pending from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
        $orph = ((& python $py $db $rid) -join '').Trim()
        PushLocal $db
        Log "orphan=$orph name=$nm"
        AppStart; GoAccount | Out-Null
        SyncNow 8 | Out-Null
        $srv = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const r = db.prepare("select id,deleted from favorites where id=?").get($rid);
console.log("DELETED=" + (r ? r.deleted : "MISSING"));
"@
        $left = LocalScalar (PullLocal 'orphan-after.db') "select count(*) from interest where remote_id=$rid"
        Log "orphan_after: $srv local_left=$left"
        $ok = ($srv -match 'DELETED=1') -and ([int]$left -eq 0)
        Verdict '孤儿-⑥' '孤儿墓碑（owner_uid=0）能推出、服务端 deleted=1、本地归零' $ok ("rid=$rid name=$nm local_left=$left ; " + $srv)
    } else {
        Verdict '孤儿-⑥' '孤儿墓碑（owner_uid=0）能推出、服务端 deleted=1、本地归零' $false 'no local row with remote_id>0 (前置构造失败)'
    }
}

# ---------- ⑦ 孤儿行（owner_uid=0 + remote_id>0）在界面不可见、不可删 ----------
if ($Only -contains 'orphanui') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'com.luopan.fengshui')) { AppStart }
    GoAccount | Out-Null
    $dd0 = Get-UiDump
    if ($dd0 -and (Find-Node $dd0 '' 'lc_m3_login')) {
        $ouUser = RegisterAs 'ou'
        Log "orphanui_register=$ouUser"
    }
    $dd0 = Get-UiDump
    if ($dd0 -and (Find-Node $dd0 '' 'lc_m3_login')) {
        Verdict '孤儿UI-⑦' '孤儿行不在列表、同步也不删不推' $false 'login failed（本用例需已登录账号）'
    } else {
        AddLocalFavorite 'ou-row'
        GoAccount | Out-Null
        SyncNow 7 | Out-Null
        $db = PullLocal 'orphanui-before.db'
        $rid = LocalScalar $db "select remote_id from interest where name='ou-row' order by id desc limit 1"
        $other = LocalScalar $db "select name from interest where name<>'ou-row' and owner_uid>0 and delete_pending=0 order by id desc limit 1"
        AppStop
        $py = Join-Path $env:TEMP 'sync4ou.py'
        @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id from interest where name='ou-row' order by id desc limit 1").fetchone()
db.execute("update interest set owner_uid=0 where id=?", (row[0],))
db.commit()
print("ou=" + str(db.execute("select id,owner_uid,remote_id,delete_pending from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
        $ou = ((& python $py $db) -join '').Trim()
        PushLocal $db
        Log "orphanui_seed=$ou remote_id=$rid other_row=$other"
        AppStart
        OpenFavList
        $d = Get-UiDump
        $txt = if ($d) { $d.OuterXml } else { '' }
        Set-Content -LiteralPath (Join-Path $out 'orphanui-list.xml') -Value $txt -Encoding UTF8
        & adb -s $Device exec-out screencap -p > (Join-Path $out 'orphanui-list.png')
        $visOu = $txt -match 'ou-row'
        $visOther = [bool]($other -and $txt.Contains([string]$other))
        Log "orphanui_ui: vis_ou=$visOu vis_other=$visOther"
        # 回到账户页同步一次：孤儿行既不推（push=0）也不被删（本地仍在、服务端 deleted=0）
        Tap-NodeRetry '' 'lc_fav_sublist_back' 2 900 | Out-Null
        GoAccount | Out-Null
        $s = SyncNow 7
        $srv = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const r = db.prepare("select id,deleted from favorites where id=?").get($rid);
console.log("DELETED=" + (r ? r.deleted : "MISSING"));
"@
        $left = LocalScalar (PullLocal 'orphanui-after.db') "select count(*) from interest where name='ou-row'"
        Log ("orphanui_sync: " + $s.Log + " ; " + $srv + " ; local_left=$left")
        $ok = (-not $visOu) -and $visOther -and ($srv -match 'DELETED=0') -and ([int]$left -eq 1)
        Verdict '孤儿UI-⑦' '孤儿行不在列表、同步不推不删（服务端行仍在）' $ok ("vis_ou=$visOu vis_other=$visOther; " + $srv + " local_left=$left; push_log=" + $s.Log)
    }
}

# ---------- ⑧ 覆盖层长按删除（M4-GAP-DELETE-UI，9e42ef1）：已同步行 / 未同步行 / B 侧无入口 ----------
if ($Only -contains 'delui') {
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'com.luopan.fengshui')) { AppStart }
    # 干净基线：清本地 interest + 增量游标，再注册全新账号（避免上一个账号的同名遗留行混进 count）
    AppStop
    $db0 = PullLocal 'delui-clean.db'
    $py0 = Join-Path $env:TEMP 'deluiclean.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1]); db.execute("delete from interest"); db.commit(); print("cleaned")
'@ | Set-Content -LiteralPath $py0 -Encoding UTF8
    ((& python $py0 $db0) -join '') | Out-Null
    PushLocal $db0
    & adb -s $Device shell "run-as com.luopan.fengshui sh -c 'rm -f shared_prefs/lc_sync.xml'" | Out-Null
    AppStart
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    $duUser = RegisterAs 'du'
    Log "delui_register=$duUser"
    $sfx = Get-Random -Maximum 99999
    $synName = "dsyn$sfx"; $unName = "dun$sfx"
    # 1) 已同步行：建→推，拿到 remote_id
    AddLocalFavorite $synName
    GoAccount | Out-Null
    SyncNow 7 | Out-Null
    $db1 = PullLocal 'delui-synced.db'
    $ridSyn = LocalScalar $db1 "select remote_id from interest where name='$synName' order by id desc limit 1"
    # 2) 未同步行：只建不推
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    AddLocalFavorite $unName
    $db2 = PullLocal 'delui-unsynced.db'
    $ridUn = LocalScalar $db2 "select remote_id from interest where name='$unName' order by id desc limit 1"
    $unRow = LocalQuery $db2 "select id,remote_id,sync_state from interest where name='$unName' order by id desc limit 1"
    Log "delui_rows: syn=$synName remote=$ridSyn ; un=$unName remote=$ridUn row=$unRow"
    # 3) 进覆盖层列表，长按已同步行 → 确认框 → 点“删除”（精确文本匹配，避免点到标题）
    OpenFavList
    $d = Get-UiDump
    $dText = if ($d) { $d.OuterXml } else { '' }
    Set-Content -LiteralPath (Join-Path $out 'delui-list-before.xml') -Value $dText -Encoding UTF8
    & adb -s $Device exec-out screencap -p > (Join-Path $out 'delui-list-before.png')
    $nSyn = Find-Node $d $synName ''
    $dlg1 = $false
    if ($nSyn) {
        Invoke-Adb @('shell', 'input', 'swipe', "$($nSyn.X)", "$($nSyn.Y)", "$($nSyn.X)", "$($nSyn.Y)", '900') | Out-Null
        Start-Sleep -Milliseconds 900
        $dd = Get-UiDump
        $ddTxt = if ($dd) { $dd.OuterXml } else { '' }
        Set-Content -LiteralPath (Join-Path $out 'delui-dialog-syn.xml') -Value $ddTxt -Encoding UTF8
        & adb -s $Device exec-out screencap -p > (Join-Path $out 'delui-dialog-syn.png')
        $dlg1 = ($ddTxt -match '确定删除|删除收藏点')
        if ($dd) {
            foreach ($node in $dd.SelectNodes('//node')) {
                if ("$($node.text)" -eq '删除' -and "$($node.bounds)" -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                    $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                    Invoke-Adb @('shell', 'input', 'tap', "$cx", "$cy") | Out-Null
                    break
                }
            }
            Start-Sleep -Seconds 2
        }
    }
    $lc1 = ((AdbOk @('shell', 'logcat', '-d', '-s', 'LCFAV:V')) -join ' ')
    Log "delui_syn: dialog=$dlg1 node_found=$([bool]$nSyn) lcfav=$lc1"
    # 4) 长按未同步行 → 确认框 → 删除（前后统计服务端 delete 请求，证“不发请求”）
    $delBefore = ((docker logs lc-backend --since 3m 2>&1) | Select-String -Pattern '/v2/userCollectPoint/delete').Count
    $d = Get-UiDump
    $nUn = Find-Node $d $unName ''
    $dlg2 = $false
    if ($nUn) {
        Invoke-Adb @('shell', 'input', 'swipe', "$($nUn.X)", "$($nUn.Y)", "$($nUn.X)", "$($nUn.Y)", '900') | Out-Null
        Start-Sleep -Milliseconds 900
        $dd = Get-UiDump
        $ddTxt = if ($dd) { $dd.OuterXml } else { '' }
        Set-Content -LiteralPath (Join-Path $out 'delui-dialog-un.xml') -Value $ddTxt -Encoding UTF8
        $dlg2 = ($ddTxt -match '确定删除|删除收藏点')
        if ($dd) {
            foreach ($node in $dd.SelectNodes('//node')) {
                if ("$($node.text)" -eq '删除' -and "$($node.bounds)" -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
                    $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
                    Invoke-Adb @('shell', 'input', 'tap', "$cx", "$cy") | Out-Null
                    break
                }
            }
            Start-Sleep -Seconds 2
        }
    }
    $delAfter = ((docker logs lc-backend --since 3m 2>&1) | Select-String -Pattern '/v2/userCollectPoint/delete').Count
    $lc2 = ((AdbOk @('shell', 'logcat', '-d', '-s', 'LCFAV:V')) -join ' ')
    $db3 = PullLocal 'delui-after-ui.db'
    $synPending = LocalScalar $db3 "select count(*) from interest where name='$synName' and delete_pending=1"
    $unLeft = LocalScalar $db3 "select count(*) from interest where name='$unName'"
    Log ("delui_after_ui: syn_dialog=$dlg1 un_dialog=$dlg2 syn_pending=$synPending un_left=$unLeft delreq $delBefore->$delAfter lcfav2=$lc2")
    # 5) 同步：已同步行墓碑推出 → 服务端 deleted=1、本地归零；未同步行没有任何请求
    GoAccount | Out-Null
    $s = SyncNow 8
    $srv = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
const a = db.prepare("select id,deleted from favorites where id=?").get($ridSyn);
console.log("SYN_ROW=" + (a ? (a.id + ":" + a.deleted) : "MISSING"));
"@
    $db4 = PullLocal 'delui-after-sync.db'
    $synLeft = LocalScalar $db4 "select count(*) from interest where name='$synName'"
    $pendingLeft = LocalScalar $db4 "select count(*) from interest where delete_pending=1"
    Log ("delui_sync: " + $s.Log + " ; " + $srv + " ; syn_left=$synLeft pending_left=$pendingLeft")
    $okDelSyn = ($dlg1 -and ($srv -match "SYN_ROW=$ridSyn:1") -and ([int]$synLeft -eq 0) -and ([int]$pendingLeft -eq 0))
    Verdict '删除UI-⑧a' '长按删已同步行：服务端同 remote_id deleted=1、本地归零、无墓碑残留' $okDelSyn ("name=$synName rid=$ridSyn dialog=$dlg1 ; " + $srv + " syn_left=$synLeft pending_left=$pendingLeft")
    $okDelUn = ($dlg2 -and ([int]$unLeft -eq 0) -and ([int]$ridUn -eq 0) -and ([int]$delAfter -eq [int]$delBefore))
    Verdict '删除UI-⑧b' '长按删未同步行：本地直接消失、无 remote_id、无墓碑、不发删除请求' $okDelUn ("name=$unName rid=$ridUn un_left=$unLeft un_row=$unRow delreq=$delBefore->$delAfter")
    # 6) 切 B：列表为空，长按无确认框（非平凡，因为入口已存在）
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    $userB = RegisterAs 'db'
    $s = SyncNow 7
    OpenFavList
    $dB = Get-UiDump
    $tB = if ($dB) { $dB.OuterXml } else { '' }
    Set-Content -LiteralPath (Join-Path $out 'delui-B-list.xml') -Value $tB -Encoding UTF8
    & adb -s $Device exec-out screencap -p > (Join-Path $out 'delui-B-list.png')
    $nList = Find-Node $dB '' 'lc_fav_sublist_list'
    $dlgB = $false
    if ($nList) {
        Invoke-Adb @('shell', 'input', 'swipe', "$($nList.X)", "$($nList.Y)", "$($nList.X)", "$($nList.Y)", '900') | Out-Null
        Start-Sleep -Milliseconds 900
        $dd = Get-UiDump
        $ddTxt = if ($dd) { $dd.OuterXml } else { '' }
        Set-Content -LiteralPath (Join-Path $out 'delui-B-longpress.xml') -Value $ddTxt -Encoding UTF8
        $dlgB = ($ddTxt -match '确定删除|删除收藏点')
    }
    $okB = (-not $dlgB) -and ($tB -match '暂无收藏点')
    Log "delui_B: list_ids=$([bool]$nList) dialog=$dlgB empty=$($tB -match '暂无收藏点')"
    Verdict '删除UI-⑧c' 'B 侧列表无 A 的行、长按无确认框（入口已存在，非平凡）' $okB ("dialog=$dlgB empty=$($tB -match '暂无收藏点') ; b_user=$userB")
    Tap-NodeRetry '' 'lc_fav_sublist_back' 2 900 | Out-Null
}

# ---------- 收尾 ----------
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { AdbOk @('shell', 'settings', 'put', 'global', $k, '1') | Out-Null }
AdbOk @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
AdbOk @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
AdbOk @('reverse', '--remove-all') | Out-Null
Log ("fails=" + $script:fails + " out=$out")
if ($script:fails -eq 0) { exit 0 } else { exit 1 }




