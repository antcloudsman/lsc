# M4 Stage 5 五条同步判据真机复验（develop `525704c`）

- 结论：**六项 PASS / 0 FAIL / exit 0**（总监下发的五条 + 审核验点名的「孤儿墓碑归零」）
- 构建：`525704c`（develop 顶端，2026-11-07 21:44 合入 §33 + 幽灵行认领）
- 设备：`L2E0222531014833`（HUAWEI JAD-AL00 / Android 12 / 1228×2700）
- APK：`app-debug.apk` 8,852,423 B，sha256 `dfb35437a5e93c43b80a97fc0a0974e74d3d1ed374a3b86ad2f4f48500444085`
- 服务端：容器 `lc-backend`，`schema=006_favorites_idem`；`server.js`/`db.js` 与 develop 逐字节相同（sha256 `17e97436…` / `f54c0e05…`）
- 执行：`pwsh -NoProfile -File tests/e2e/verify-m4-sync4.ps1 -Apk <apk> -Only xacct,crash,sit,del,sec33,orphan`
- 原始证据：`E:\APPDetector\reports\M4-stage5-525704c\`（`run.log`/`table.md`/每条用例的本地库快照/§33 双图 + dump/注册页 dump）

## 判据与实测

| # | 判据 | 结果 | 关键数字 |
|---|---|---|---|
| ① | 升级/清数据后**首次同步直接登 B**，本地条数不减 | PASS | 构造 3 行 `owner_uid=0` 且 `remote_id>0`；`before=3 after=3`；B 服务端 `B_ACTIVE=0`；B 同步日志 `push=0 pull=0 purgedRemote=0` |
| ② | A/B 崩窗口（本地新建且已推过的行，清 `remote_id` 后重推不新建） | PASS | `restored_remote_id=346`（与清 0 前同一 id）；全库 `TOTAL_ACTIVE=84 -> 84`；`client_key` 保留 `9df43a8c…` |
| ③ | 坐 90 / 向 270 推→清本地→拉回仍为 90/270 | PASS | `set=(51, 90.0, 270.0, 347)` → `pulled=[(90.0, 270.0)]` |
| ④a | 本地库置墓碑 → 服务端**同一 remote_id** `deleted=0→1`（**同步机制**，非 UI 入口） | PASS | `rid=348`：`ROW_BEFORE=348:0 → ROW_AFTER=348:1` |
| ④b | 服务端软删 → 本地同一行消失 | PASS | `remote_id=347`：本地 `1 -> 0`，`SOFTDELETED=347:0` |
| ⑤a | B 侧收藏列表看不到 A 的行 | PASS | 覆盖层打开 `sublist_open=True`；`visA1=False visA2=False`；空列表文案命中；本地 A 的 2 行仍在（`owner_uid=111`）。注：覆盖层本就没有删除功能，「长按无确认框」是平凡真，**不能**当账号过滤的证据；过滤证据见「局限」第 1 条 |
| ⑤b | 切回 A：本地两行仍在、界面可见、服务端行未被删 | PASS | `local=2 visA1=True visA2=True`；`A_SRV_ACTIVE=2 A_SRV_IDS=349,350` |
| ⑥ | 孤儿墓碑（`owner_uid=0` + `remote_id>0`）能推出、服务端 `deleted=1`、本地归零 | PASS | `orphan_tombstone=(56, 350, 0, 1)` → `DELETED=1`、`local_left=0` |

## 构造口径（避免假通过）

- **① 必须「首次同步即登 B」**：A 注册→建 3 条→同步（`push=3`，`remote_id` 落地）→ 本地库把所有行 `owner_uid` 归 0（模拟升级前存量）→ 登出，**直接注册 B**（B 的第一次同步）。不先以 A 再同步一次。
- **② 必须用本地新建且已推过一次的行**：`client_key` 已持久化；只用 `remote_id=0/rev=0/sync_state=pending` 模拟崩窗口，不删 `client_key`。
- **③ 清本地后必须同时清增量游标**（`shared_prefs/lc_sync.xml`），否则 `since=cursor` 不返回老行，测出来的是「有旧游标的增量语义」而不是「新设备登录拉全量」。另外改坐向后要置 `sync_state='pending'` 才会进推送队列——只改 `sitting_deg/facing_deg` 的 clean 行不会被推。
- **④/⑥ 只按 `remote_id` 单行判**，不用 `/health favorites`（全库聚合）也不看 `purgedRemote` 数字。
- **⑤ 的可达入口**：`MainActivity` 的「收藏点 → 收藏列表」覆盖层（行来自 `listInterest(LcSession.uid)`）；`InterestListActivity` 是 `exported=false` 且**全仓零启动点**，`run-as am start` 也被 `SecurityException` 拒，用户进不去。

## 本轮修掉的三处测试脚本缺陷（不是产品缺陷）

1. **① 假失败根因**：`xacct` 在 `AppStart` 后多按了一次 back，把应用退到桌面（失败 dump 是 `com.huawei.android.launcher`），A 的注册请求未发出 → 三条行 `owner_uid=0`。已删除该 back，并在注册/登录前加「应用在前台」守卫、提交前 ESC 收键盘、提交后校验 `昵称：`。
2. **③ 假失败根因**：改坐向后未置 `sync_state='pending'`，clean 行不进推送队列；且清本地后未清游标。
3. **计数解析**：`LocalQuery` 返回 `[(n,)]`，`[int]` 直接转会抛错导致判定被跳过；新增 `LocalScalar` 取单值，④ 改为按 `remote_id` 单行判。


## 局限与说明

- ⑥ 会真实删掉一行服务端数据（本例 `remote_id=350`，A 的 `s33-a2`）：这是 **§32 允许孤儿墓碑推出的设计行为**，用当前账号推自己的孤儿墓碑，不构成跨账号删除。
- **删除入口**：`525704c` 时可达 UI 无删除入口（`M4-GAP-DELETE-UI`）；已在 `9e42ef1` 补到覆盖层并真机验过（见下节）。长按形态是**我方新增**（原版收藏 dump `long-clickable=true` 计数为 0，原版只有「编辑」按钮），登记有意差异（DI）；确认框用中性文案，`lc_interest_delete_*` 是 `strings_gap.xml` 的 gap 文案，非原版取证。
- ⑤ 未覆盖「列表页存活期间切账号」的理论路径（`LIST-SNAPSHOT`）：`InterestListActivity` 不可达（`exported=false`、零启动点、`run-as am start` 也被 `SecurityException` 拒），`onResume` 属防御性代码，设备不可复现。
- 本轮未复验 `LIMIT=200` 超限跳过与翻页认领（`adoptByRemoteIds` 只取第一页，属已知限制）。

## 增补（`9e42ef1`）：删除 UI 四条 + 回归六项

- 构建 `9e42ef1`（0d4f5dd：覆盖层长按删除），APK sha256 `39df3bc18e8440ae4ea45bc72fb2cbcdfe824a589b9a37280c6a7e5bba6f2a47`
- 证据：`E:\APPDetector\reports\M4-stage5-9e42ef1\`（`delui/`、`regress/`、`orphanui/` 三组，各带 run.log/table.md/库快照/dump+截图）

| 判据 | 结果 | 关键数字 |
|---|---|---|
| 长按删**已同步**行 | PASS | `LCFAV sublist delete id=65 remote=tombstone`；同步 `del=1`；服务端 `356:1`（deleted=1）；本地 `syn_left=0`、`pending_left=0` |
| 长按删**未同步**行（`remote_id=0`） | PASS | `LCFAV ... remote=local-only`；行立即消失（`un_left=0`）；服务端 delete 请求计数 `0->0`（未发请求） |
| 切 B 长按（入口已存在，非平凡） | PASS | B 列表「暂无收藏点」、长按无确认框 |
| 回归六项（P0①/②/③/④a④b/⑤§33/⑥） | PASS | ①3→3、B_ACTIVE=0；②restored 360、ACTIVE 93→93；③90/270；④a 362:0→1、④b 1→0；⑤A 363,364 可见、服务端未变；⑥364→deleted=1、本地 0 |
| 孤儿行不可见（补充） | PASS | `(77, owner=0, remote=365)` 不在列表（`vis_ou=False`，同页 A 的行可见）；同步 `adopted by remote ids=1`、`push=0`、服务端 `DELETED=0` |

**结论口径**：④ 可升级为「删除可经**可达 UI** 完成（我方形态）」，但**不得**写成「删除形态与原版一致」——长按是新增形态，原版未取证；文案为中性，非原版 gap string。