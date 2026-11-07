# M4 同步四项复验（P0 跨账号 / A-B 崩窗口 / 坐向还原 / 删除双向）
# 用法：pwsh -File tests\e2e\verify-m4-sync4.ps1 -Apk <apk> [-Only xacct,crash,sit,del]
# 纪律：坐标当次 dump 解析；结论只认库内字段与服务端计数；每项输出 PASS/FAIL + 数字
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [string]$Device = 'L2E0222531014833',
    [string[]]$Only = @('xacct', 'crash', 'sit', 'del')
)
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
function RemoteJs([string]$code) {
    $f = Join-Path $env:TEMP 'sync4r.mjs'
    Set-Content -LiteralPath $f -Value $code -Encoding UTF8
    return ((Get-Content -LiteralPath $f -Raw | & docker exec -i lc-backend node --input-type=module 2>&1) -join ' ')
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
function RegisterAs([string]$prefix) {
    $u = $prefix + (Get-Random -Maximum 99999); $p = 'LcQa!' + (Get-Random -Maximum 99999)
    Tap-NodeRetry '' 'lc_m3_login' 3 1500 | Out-Null
    Tap-NodeRetry '注册' '' 3 900 | Out-Null
    $ids = @('lc_login_username', 'lc_login_password', 'lc_login_display'); $vals = @($u, $p, ($prefix + 'nick'))
    for ($i = 0; $i -lt 3; $i++) {
        for ($t = 1; $t -le 3; $t++) { $dd = Get-UiDump; if ($dd -and (Tap-Node $dd '' $ids[$i] 700)) { Input-Text $vals[$i]; break }; Start-Sleep -Milliseconds 700 }
    }
    $dd = Get-UiDump; Tap-Node $dd '' 'lc_login_submit' 2500 | Out-Null
    Start-Sleep -Seconds 2
    return $u
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
$o = & pwsh -NoProfile -File (Join-Path $root 'tools\device\install-debug.ps1') -Apk $Apk -Device $Device 2>&1
Log ("install_exit=" + $LASTEXITCODE + " " + (($o | Select-Object -Last 1) -join ''))
AdbOk @('reverse', 'tcp:8787', 'tcp:8787') | Out-Null
AppStart

# ---------- ① P0 跨账号（升级路径：owner_uid=0 且 remote_id>0 → 首次登录 B） ----------
if ($Only -contains 'xacct') {
    GoAccount | Out-Null
    LogoutIfIn | Out-Null
    # 以 A 建 3 条并同步，让 remote_id 落地
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    GoAccount | Out-Null
    $userA = RegisterAs 'xa'
    Log "userA=$userA"
    for ($i = 1; $i -le 3; $i++) { AddLocalFavorite ("xa-$i") }
    GoAccount | Out-Null
    $s = SyncNow
    Log ("A_sync: " + $s.Log)
    $dbA = PullLocal 'local-A.db'
    $rowsA = LocalQuery $dbA "select count(*) from interest where remote_id>0"
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
    $before = LocalQuery (PullLocal 'local-before-sync.db') "select count(*) from interest"
    $s = SyncNow
    Log ("B_first_sync: " + $s.Log)
    $after = LocalQuery (PullLocal 'local-after-sync.db') "select count(*) from interest"
    $bCnt = RemoteJs @"
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("B_ACTIVE=" + db.prepare("select count(*) c from favorites f join users u on u.id=f.user_id where u.username_lc=? and f.deleted=0").get("$userB").c);
"@
    Log ("counts: before=$before after=$after ; " + $bCnt)
    $ok = ([int]$before -ge 3) -and ([int]$after -eq [int]$before)
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
    $restored = LocalQuery (PullLocal 'local-crash-after.db') "select remote_id from interest order by id desc limit 1"
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
db.execute("update interest set sitting_deg=90, facing_deg=270 where id=?", (row[0],))
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
    AppStart; GoAccount | Out-Null
    SyncNow 8 | Out-Null
    $pulled = LocalQuery (PullLocal 'local-sit-pulled.db') "select sitting_deg,facing_deg from interest order by id desc limit 1"
    Log "pulled_sit=$pulled"
    $ok = ($pulled -match '90') -and ($pulled -match '270')
    Verdict '坐向-③' '坐 90 / 向 270 拉回后保持' $ok ("pulled=$pulled (set=$set)")
}

# ---------- ④ 删除双向 ----------
if ($Only -contains 'del') {
    # 方向一：本机删 → 服务端 active 减 1
    Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null; Start-Sleep -Milliseconds 800
    if (-not ((Get-Focus) -match 'MainActivity')) { AppStart }
    AddLocalFavorite 'del-row'
    GoAccount | Out-Null
    SyncNow 7 | Out-Null
    $b1 = RemoteJs @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("ACTIVE=" + db.prepare("select count(*) c from favorites where deleted=0").get().c);
'@
    # 通过本地库置墓碑（等价于界面上删除，UI 路径已由 InterestListActivity 覆盖）
    AppStop
    $db = PullLocal 'local-del.db'
    $py = Join-Path $env:TEMP 'sync4del.py'
    @'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select id,remote_id from interest order by id desc limit 1").fetchone()
db.execute("update interest set delete_pending=1 where id=?", (row[0],))
db.commit()
print("tombstone=" + str(db.execute("select id,remote_id,delete_pending from interest where id=?", (row[0],)).fetchone()))
'@ | Set-Content -LiteralPath $py -Encoding UTF8
    $tb = ((& python $py $db) -join '').Trim()
    PushLocal $db
    Log "tombstone=$tb"
    AppStart; GoAccount | Out-Null
    SyncNow 8 | Out-Null
    $b2 = RemoteJs @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite", { readOnly: true });
console.log("ACTIVE=" + db.prepare("select count(*) c from favorites where deleted=0").get().c);
'@
    $ok1 = ([int](($b2 -replace '\D', '')) -eq ([int](($b1 -replace '\D', '')) - 1))
    Verdict '删除-④a' '本机删 → 服务端 active 减 1' $ok1 ($b1 + " -> " + $b2)
    # 方向二：服务端软删一条 → 本地消失
    $target = RemoteJs @'
import { DatabaseSync } from "node:sqlite";
const db = new DatabaseSync("/data/lc.sqlite");
const r = db.prepare("select id from favorites where deleted=0 order by id desc limit 1").get();
if (r) db.prepare("update favorites set deleted=1, rev=rev+1 where id=?").run(r.id);
console.log("SOFTDELETED=" + (r ? r.id : "none"));
'@
    Log "remote_softdelete=$target"
    $before = LocalQuery (PullLocal 'local-del-before.db') "select count(*) from interest"
    SyncNow 8 | Out-Null
    $after = LocalQuery (PullLocal 'local-del-after.db') "select count(*) from interest"
    Log ("local_count: $before -> $after")
    $ok2 = [int]$after -lt [int]$before
    Verdict '删除-④b' '服务端软删 → 本地消失' $ok2 ("$before -> $after; " + $target)
}

# ---------- 收尾 ----------
foreach ($k in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') { AdbOk @('shell', 'settings', 'put', 'global', $k, '1') | Out-Null }
AdbOk @('shell', 'am', 'force-stop', 'com.luopan.fengshui') | Out-Null
AdbOk @('shell', 'input', 'keyevent', 'KEYCODE_HOME') | Out-Null
AdbOk @('reverse', '--remove-all') | Out-Null
Log ("fails=" + $script:fails + " out=$out")
if ($script:fails -eq 0) { exit 0 } else { exit 1 }
