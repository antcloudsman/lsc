# M4 Stage 5 · 客户端安全与同步复核（登录/注册 · token · 离线同步）

- 复核人：LC审客户端
- 基线：`develop` **`f285cf0`**（`git rev-parse origin/develop` 实测 = `f285cf0ef97baab6b1cede1b3c488be7203c44eb`；总监上一条说 `24de04b`，实际主线已推进到 `f285cf0`，本报告按 `f285cf0`）
- 范围：总监派单五条 ①新增标注一致性 ②Keystore 与降级 ③双头与 `code==200` ④离线队列与 409 收敛（含「推成功未写回 `remote_id` 就崩」窗口）⑤明文策略只放行回环
- 方法：只读源码 + `git grep`，未改一行、未占设备；每条给文件 + 行号 + 原文
- 结论口径：五条里 **2 条通过、2 条部分通过、1 条不通过**；不通过项见 §4

---

## 0. 五条判定

| # | 项 | 判定 | 一句话 |
|---|---|---|---|
| 1 | 新增标注是否一致 | **部分通过** | 登录页标题与说明都标了「本项目新增」，账户设置页有依赖标注；但注册态标题仍写「账号登录（本项目新增）」，且新增页文案全硬编码 Java、绕开 copy 门禁 |
| 2 | Keystore 与降级 | **通过（3 个 P2 风险）** | AES/GCM-256 + Keystore 密钥 + 降级只留内存，符合红线；`apply()` 异步、UI 线程解密、`expires_at` 只存不校验 |
| 3 | 双头与 `code==200` | **通过（1 个防御洞）** | 两个头都发、服务端都认；只看 `code==200` 正确；`optInt("code", http)` 缺省回退 HTTP 状态，响应缺 `code` 时会把 200 当成功并返回 `data=null` |
| 4 | 离线队列与 409 收敛 | **不通过** | ①「推成功未写回 `remote_id`」窗口真实存在且服务端无幂等 → 重复创建；②拉回不还原坐向；③删除完全不同步 |
| 5 | 明文策略只放行回环 | **通过** | 只对 `127.0.0.1` / `localhost` 开明文；无全局 `usesCleartextTraffic`；唯一业务明文地址是 `LcApi.BASE` |

---

## 1. 新增标注一致性（部分通过）

**成立部分**
- 登录页标题 `账号登录（本项目新增）`：`LoginActivity.java:57`。
- 页脚说明逐字：「本页为本项目新增的账号密码登录，不是原版页面。原版的手机号验证码 / 一键登录 / 荣耀账号 / 微信登录只做入口占位，登记为 D-029 / D-030 / D-031，本轮不实现。」`LoginActivity.java:149`。
- 账户设置页顶部依赖标注：「账号密码登录/注册为本项目新增（原版无此页）…」`M3PageActivity.java:57`，渲染于 `M3PageActivity.java:120-130`（所有状态都显示）。
- 四个原版入口占位逐字、只给不可用说明、不触发外发请求：`LoginActivity.java:140-145`、`:194-211`；`M3PageActivity.java:450-478`。微信入口 `LoginActivity.java:119-125` 点击只写状态行，无网络调用。

**不成立部分**
1. **注册态标题与页脚不跟着变**：切到注册后提交按钮变「注册并登录」（`LoginActivity.java:180`），但标题常量 `账号登录（本项目新增）`（`:57`）不变；页脚仍写「账号密码登录」。注册是本轮新增的主功能，标题却仍是登录。P2。
2. **新增页文案绕开 copy 门禁**：`tests/copy/copy_gate.py` 只扫 `app/android/app/src/main/res/values/*.xml`（`copy_gate.py:31`、`:276`），而新增页的用户可见文案全部硬编码在 Java（`LoginActivity.java:49/57/70/71/76/81/88/97/119/133/140/142/144/149`，`M3PageActivity.java:434/441/452/462/472/487/496/505/514/541`）。门禁报 `COPY: PASS` 不代表这些文案被检查过——「新增」页若写进会员/付费类词，门禁不会拦。P2（一致性，不是本轮违规）。

**修法**：`setMode()` 里同步改标题（登录/注册两态各一条文案）；新增页文案迁入 `values/strings_*.xml` 或在 `copy_gate.py` 增加 Java 字面量扫描（二选一，需产品定口径）。

---

## 2. Keystore 与降级（通过，3 个 P2 风险）

**成立部分（逐条对红线）**
- 算法与密钥：`AES/GCM/NoPadding`，256 位，密钥由 `AndroidKeyStore` 生成、不可导出（`LcSession.java:102-117`、`:119-133`）。每次加密取 `cipher.getIV()` 随机 IV（`:123`），明文不落盘（`:47-56`）。
- 存储：`SharedPreferences` 全部 `MODE_PRIVATE`（`LcSession.java:47`、`:65`、`:88`、`:97`）。
- 降级：`encrypt()` 任何异常返回 null（`:129-132`），`save()` 走 `volatileToken` 且**移除** `token_enc`（`:49-52`），日志只写 `keystore unavailable, token kept in memory only`（`:52`）——不含 token。符合「宁可重登也不落明文」。
- 解密失败即 `clear()`（`LcSession.java:68-72`），不留半残会话。
- token 不进 dump：账户页只渲染 `username`/`uid`（`M3PageActivity.java:494-501`），登录日志只打 `uid`（`LoginActivity.java:247-248`）。

**P2 风险（不阻塞关账，但建议本轮或 M5 收）**
1. **`apply()` 异步**：`save()`（`:51`、`:55-60`）与 `clear()`（`:97`）都用 `apply()`。登出时先调服务端 `logout`（`M3PageActivity.java:618-622`）再 `clear()`；若进程在 `clear()` 落盘前被杀，磁盘上仍有可解密的 token，下次启动会显示已登录，而服务端会话已撤销 → 所有业务请求 401。修法：`clear()` 用 `commit()`。
2. **UI 线程做 Keystore 解密**：`token()`（`LcSession.java:63-74`）在 `buildAccount()` 里被主线程直接调用（`M3PageActivity.java:429`）；Keystore 解密是系统调用，低端机可感知卡顿。修法：缓存进程内解密结果（`volatileToken` 已有，可同样缓存读出的值）。
3. **`expires_at` 只存不判**：`expires_at` 在 `LoginActivity.java:244` 取出、`LcSession.java:59` 存、`:87-89` 读，但**全仓没有任何一处拿它和当前时间比较**。过期后客户端仍按已登录渲染，靠服务端 401 兜底。修法：`isLoggedIn()` 里比对 `expires_at`，过期即 `clear()`。

**口径提醒**：`m4-plan.md` M4-02 ⑥ 写「token 存 MMKV 私有目录」，实现是 Keystore AES-GCM + SharedPreferences（更强）。这不是缺陷，但档案口径要改成实现事实，否则成文与代码对不上。

---

## 3. 双头与 `code==200`（通过，1 个防御洞）

**成立部分**
- 客户端两个头都发：`token: <opaque>` 与 `Authorization: Bearer <token>`（`LcApi.java:119-122`）。
- 服务端两者都认，`token` 优先：`backend/src/server.js:227-233`（`extractToken` 先读 `req.headers.token`，再读 Bearer）。
- 只看信封 `code`：`LcApi.java:142-145`（`code != 200` 抛 `ApiError`）；服务端成功码恒 200，注册/新建的 HTTP 201 也包 `code:200`（`server.js:117-119` `ok()` 固定 `{code:200,...}`，状态码另传）。
- 409 快照形状匹配：服务端 `server.js:474-476` 返回 `data:{server:existing}`；客户端 `FavoriteSync.java:64-66` 取 `e.data.optJSONObject("server")`，能对上。

**防御洞（P2）**
- `int code = env.optInt("code", http)`（`LcApi.java:142`）：响应体缺 `code` 字段时回退用 HTTP 状态。若 HTTP 200、body 是 `{}` 或非契约 JSON，`code=200` 判成功、`data` 为 null；这条会直接触发 §4-a 的 `remote_id=0` 损失路径（`FavoriteSync.java:57-61` 对 `point==null` 没有拒绝）。修法：`code` 字段缺失即视为失败（`optInt("code", -1)`），并在 `savePoint` 返回后加 `id<=0 → 计入 failed、保留 pending`。

---

## 4. 离线队列与 409 收敛（不通过：3 个窗口）

### 4-a 推成功未写回 `remote_id` 就崩 → 重复创建【不通过】

**代码路径**：`FavoriteSync.java:37-78`，
```
57  JSONObject data = LcApi.savePoint(token, body);      // 服务端已插入，返回 201/code=200
58  JSONObject point = data == null ? null : data.optJSONObject("point");
59  long id = point == null ? remote : point.optLong("id", remote);
61  db.markInterestSynced(p.id, id, rev);                // 到这里才算“已同步”
```
网络成功与本地落库之间**没有先写「pushing」中间态、也没有事务**。若进程在这条缝里被杀（后台同步被系统回收、用户强退），本地行仍是 `sync_state='pending'`、`remote_id=0`，下次同步会**再建一条**。

**服务端没有幂等兜底**：`/v2/userCollectPoint/save` 新建分支（`backend/src/server.js:464-483`）直接 `createFavorite`，不读任何幂等键；对比反馈接口是有幂等的——`server.js:506-511` 读 `idempotency-key` 头、`db.js:130-133` 有 `UNIQUE INDEX (user_id, idem_key)`。而客户端**全仓 0 处发送幂等键**（`git grep -in 'idempot' -- app/android/app/src/main/java` → exit 1）。LC测试在 E6.4 帖出的反馈记录 `"idem_key":null` 正是这条的旁证。

**计划自己写了要幂等**：`docs/fidelity/m4-plan.md:230` ——「收藏同步冲突策略：未取证前按「`client_id` 幂等 + 时间戳后写覆盖」」。实现里既没有 `client_id`，也没有幂等键。**这是计划有、实现无**，不是口径分歧。

**修法（按代价从低到高，任一即可）**：
1. 服务端加 `idempotency-key`（复用 feedback 那套），客户端 `savePoint` 带 `本地行 id + 设备标识` 的稳定键；
2. 客户端在 POST 前先把本地行写 `sync_state='pushing'` 并生成 `client_uuid`，服务端对 `(user_id, client_uuid)` 建唯一索引；
3. 最省：POST 前先按 `(createTime, lat, lng)` 试拉一次匹配，命中即认领。

### 4-b 拉回不还原坐向 → 换机后坐向全错【不通过】

- 推上去时坐向写在 `des`：`FavoriteSync.java:50` `body.put("des", "坐" + … + "° 向" + … + "°")`。
- 拉回来时 `des` 被忽略：`FavoriteSync.java:91-93` 只取 `name/latitude/longitude`；`GapDb.upsertRemoteInterest` 硬编码 `sitting_deg=0`、`facing_deg=180`（`GapDb.java:203-204`）。
- 后果：同一账号在第二台设备（或重装后 cursor=0 全量拉）恢复出来的每个收藏点都是「坐0° 向180°」，与原机不同。**这不是显示问题，是数据丢失**，且当前没有任何测试覆盖（E2E 只在同机同库上跑）。
- 修法：拉取时解析 `des` 的 `坐X° 向Y°` 回填；更稳的做法是服务端把 `sitting/facing` 作为独立字段透出（契约加两个字段，或客户端把 `des` 当结构化串解析并做单测）。

### 4-c 删除两端都不同步 → 幽灵点【不通过】

- 本地删除是硬删：`InterestListActivity.java:144` → `GapDb.deleteInterest` 直接 `DELETE`（`GapDb.java:136-138`），不留墓碑。
- `LcApi.deletePoint`（`LcApi.java:90-92`）**零调用点**（`git grep -n 'deletePoint' -- app/android/app/src/main/java` 只有定义处）。契约里的 `DELETE /v2/userCollectPoint/delete` 客户端从未使用。
- 两个方向都错：
  - 本机删了、服务端还在 → 另一台设备或重装后**复活**；
  - 另一台设备删了（服务端软删 `db.js:489-498`，`rev` 递增但 `listFavorites` 过滤 `deleted=0`，`db.js:434-441`）→ 本机 `since=cursor` 增量拉永远看不到这条，**幽灵点永久留在本机**。
- 修法：本地删除改软删 + `sync_state='deleted'`，同步时调 `deletePoint`，成功后物理删/标记；拉取侧要能拿到「已删除 id」（服务端在增量接口里回 tombstone 列表，或在 404/软删记录里带 `deleted:1`）。

### 4-d 409 收敛本身成立（补充说明）

- 路径：`FavoriteSync.java:63-71` → 服务端 `server.js:472-477`。收敛动作是「拿服务端 `id/rev` 把本地标记为 clean」，本地字段**不覆盖**；随后的拉取（`FavoriteSync.java:80-98`）会命中该 `rev`（服务端 `rev` 全局递增，必然 > cursor），由 `upsertRemoteInterest` 把本地字段覆盖成服务端值。
- 所以**不会永久分叉**，但用户的本地点编辑会被静默丢弃，只有摘要里的「冲突收敛 N」（`FavoriteSync.java:103`）能看出来。建议至少 toast「N 条冲突已采用服务端版本」，别让用户以为保存成功。

---

## 5. 明文策略只放行回环（通过）

- `AndroidManifest.xml:14` 引用 `@xml/network_security_config`，且**没有** `android:usesCleartextTraffic="true"`（`git grep` 0 命中）。
- `res/xml/network_security_config.xml:4-9`：只对 `127.0.0.1` 与 `localhost`（`includeSubdomains="false"`）开 `cleartextTrafficPermitted="true"`，其余域名走 Android 默认（targetSdk≥28 默认禁明文）。
- 唯一业务明文地址是 `LcApi.BASE = "http://127.0.0.1:8787"`（`LcApi.java:24`），落在放行名单内。
- 其余 `http://` 命中不是网络请求：`map.html:344` 与 `TrackStore.java:161` 是 XML/SVG 命名空间。
- 结论：**明文面 = 回环一条，符合「只放行回环」**。

---

## 6. 额外发现（不在五条内，但属客户端侧）

| 级别 | 位置 | 现象 | 修法 |
|---|---|---|---|
| P1 | `M3PageActivity.java:528-533` | 同步的 `catch (Throwable t)` 把 `LcApi.ApiError`（401/429/500）也显示成「同步失败：连不上本地服务」——**错误归类错**，用户会去查 adb reverse，实际是会话失效 | 按 §3 的 `ApiError` 分支分别提示；401 时 `LcSession.clear()` 并引导「立即登录」 |
| P1 | `LcSession.java:63-74` + `M3PageActivity.java:429` | 401 后不清会话，账户页仍显示已登录，每次操作都失败 | 统一在 `LcApi` 层拦截 401 → 清会话 + 回登录态 |
| P2 | `LcApi.java:97-106` | 反馈提交不带 `Idempotency-Key`，超时重提会产生重复记录（服务端已支持幂等，只是客户端没发） | 复用同一 `idem_key`（本地生成 UUID 存 prefs，提交成功清除） |
| P2 | `M3PageActivity.java:595-597` | 改密只校验下限 `n.length() < 8`，没校验上限 128（服务端有校验，故不出错，只多一次往返） | 与 `LoginActivity.java:232` 对齐成 8–128 |

---

## 7. 未覆盖 / 说明

1. **未上真机**：设备归 LC审核验；本报告不重复其设备断言，只做源码层复核。
2. **服务端安全**（哈希/越权/限流/日志）不属本单，未复核。
3. **`M3WebActivity` 的远端 WebView**、`InfoPageActivity` 内容未复核（与本单五条无关）。
4. **未做构建**：只读源码，未跑 `assembleDebug`；门禁由 LC测试 在 `f285cf0` 报六门 PASS。
5. **基线口径**：总监上一条写 `24de04b`，我取到的主线是 `f285cf0`（含 a11y 合入），两者之间客户端 Java 差异只有 `M3PageActivity`/`AndroidManifest` 的 a11y 改动，与本单五条无关。
6. 本报告未改任何源码，只新增本文件。
