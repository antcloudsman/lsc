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
| ④a | 本机删 → 服务端**同一 remote_id** `deleted=0→1` | PASS | `rid=348`：`ROW_BEFORE=348:0 → ROW_AFTER=348:1` |
| ④b | 服务端软删 → 本地同一行消失 | PASS | `remote_id=347`：本地 `1 -> 0`，`SOFTDELETED=347:0` |
| ⑤a | B 侧收藏列表看不到 A 的行、长按无删除入口 | PASS | 覆盖层打开 `sublist_open=True`；`visA1=False visA2=False`；空列表文案命中；长按后无删除/确认框；本地 A 的 2 行仍在（`owner_uid=111`） |
| ⑤b | 切回 A：本地两行仍在、界面可见、服务端行未被删 | PASS | `local=2 visA1=True visA2=True`；`A_SRV_ACTIVE=2 A_SRV_IDS=349,350` |
| ⑥ | 孤儿墓碑（`owner_uid=0` + `remote_id>0`）能推出、服务端 `deleted=1`、本地归零 | PASS | `orphan_tombstone=(56, 350, 0, 1)` → `DELETED=1`、`local_left=0` |

## 构造口径（避免假通过）

- **① 必须「首次同步即登 B」**：A 注册→建 3 条→同步（`push=3`，`remote_id` 落地）→ 本地库把所有行 `owner_uid` 归 0（模拟升级前存量）→ 登出，**直接注册 B**（B 的第一次同步）。不先以 A 再同步一次。
- **② 必须用本地新建且已推过一次的行**：`client_key` 已持久化；只用 `remote_id=0/rev=0/sync_state=pending` 模拟崩窗口，不删 `client_key`。
- **③ 清本地后必须同时清增量游标**（`shared_prefs/lc_sync.xml`），否则 `since=cursor` 不返回老行，测出来的是「有旧游标的增量语义」而不是「新设备登录拉全量」。另外改坐向后要置 `sync_state='pending'` 才会进推送队列——只改 `sitting_deg/facing_deg` 的 clean 行不会被推。
- **④/⑥ 只按 `remote_id` 单行判**，不用 `/health favorites`（全库聚合）也不看 `purgedRemote` 数字。
- **⑤ 的删除入口**：可点路径是 `MainActivity` 的「收藏点 → 收藏列表」页面内覆盖层（行来自 `listInterest(LcSession.uid)`）；`InterestListActivity`（长按删除所在页）是 `exported=false` 且**全仓零启动点**，外部拉不起来，故真机判据落在覆盖层：A 的行不在列表对象里 → 无可长按目标；长按列表区域无任何确认框。

## 本轮修掉的三处测试脚本缺陷（不是产品缺陷）

1. **① 假失败根因**：`xacct` 在 `AppStart` 后多按了一次 back，把应用退到桌面（失败 dump 是 `com.huawei.android.launcher`），A 的注册请求未发出 → 三条行 `owner_uid=0`。已删除该 back，并在注册/登录前加「应用在前台」守卫、提交前 ESC 收键盘、提交后校验 `昵称：`。
2. **③ 假失败根因**：改坐向后未置 `sync_state='pending'`，clean 行不进推送队列；且清本地后未清游标。
3. **计数解析**：`LocalQuery` 返回 `[(n,)]`，`[int]` 直接转会抛错导致判定被跳过；新增 `LocalScalar` 取单值，④ 改为按 `remote_id` 单行判。

## 局限与说明

- ⑥ 会真实删掉一行服务端数据（本例 `remote_id=350`，A 的 `s33-a2`）：这是 **§32 允许孤儿墓碑推出的设计行为**，测试用当前账号 A 推 A 自己的孤儿墓碑，不构成跨账号删除。
- ⑤ 未覆盖「收藏列表页存活期间切账号」的理论路径（`LIST-SNAPSHOT`，客户端已加 `onResume` 重载，未合入 `525704c`）；本条判据在 `383d54f` 合入后可补一轮衍生验证。
- 本轮未复验 `LIMIT=200` 超限跳过与翻页认领（`adoptByRemoteIds` 只取第一页，属已知限制，客户端已在注释与摘要登记）。
