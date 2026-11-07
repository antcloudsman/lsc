# M4 · 安全说明与已知限制（定稿）

> 定稿日期：2026-11-07　｜　基线：`develop` `71ff5c8`　｜　成文：LC审成文
> 定位：把「安全红线怎么落地的」与「哪些还没落地、为什么不许算通过」写在一起，供 M4 关账与 M5 接手。**本文不新增判据**；判据在 `backend/api.md` §6、`docs/fidelity/verify-p0-judge.md` §26、`docs/fidelity/verify-m4-client.md`。

## 1 安全红线逐条落地情况

| 红线 | 要求 | 实现 | 判定 | 出处 |
|---|---|---|---|---|
| 密码哈希 | scrypt/bcrypt/argon2，禁明文/可逆 | `scrypt N=32768,r=8,p=1`，16B salt、64B key、`timingSafeEqual` 比对；存储串 `scrypt$N=…,r=…,p=…$salt$key` | **达成** | `backend/src/password.mjs` |
| token 不可猜 | 加密随机或签名 JWT | `randomBytes(32)` → base64url（43 字符），**不用 JWT**（可服务端吊销）；库内只存 `sha256(token+pepper)` | **达成** | `backend/src/token.mjs` |
| 密钥不入库 | 环境变量或 secrets | pepper 三级来源：`LC_TOKEN_PEPPER` → `LC_TOKEN_PEPPER_FILE`（默认 `/run/secrets/lc_token_pepper`）→ `/data/.token-pepper`（0600，demo 兜底）；**pepper 从不写进 SQLite** | 达成（兜底见 L1） | `token.mjs` |
| API 只绑回环 | 127.0.0.1 | 宿主机端口映射 `127.0.0.1:8787:8787`；容器内 `HOST=0.0.0.0` 仅为能被映射（`server.js:24-25`） | 达成（见 §2 说明） | `docker-compose.yml` |
| CORS 收窄 | 不放 `*` | 仅当 Origin 命中 `LC_CORS_ORIGIN` 白名单才回 `access-control-allow-origin`；默认空=不发 CORS 头 | **达成** | `server.js:92-99` |
| 登录限流 | 有 + 429 | 滑动窗：`loginUser 5/60s`、`loginIp 60/60s`、`loginIpHour 600/h`、`registerIp 100/h`、`passwordUser 5/h`、`feedbackUser 5/h`；429 带 `retry-after` | **达成** | `server.js:43-51,221` |
| 越权隔离 | 他人资源不可读写 | B 拿自己的 token 改/删 A 的收藏 → **404**，A 的记录原封不动 | **达成** | `verify-p0-judge.md` §26（独立复核） |
| 日志脱敏 | 密码/token 不进日志 | 服务端声明并实现脱敏；客户端侧用 `has-token_enc=True` 互证「无明文 token」 | 部分（见 §3 空真项） | `server.js:6`；`verify-p0-judge.md` §26 |
| 请求体上限 | 防大包 | `LIMIT_AUTH_BYTES = 16 KB`、`LIMIT_BUSINESS_BYTES = 256 KB` | 达成 | `server.js:30-31` |

**实测数据（独立复核，非自报）**：`SELFCHECK: PASS (68 checks)`；越权 `404`；限流第 6 次请求 `429`、响应头 `retry-after: 46`。

## 2 「只绑 127.0.0.1」的准确表述

- 代码里 `HOST` 默认 `0.0.0.0`，**这是容器内的监听地址**，不是对外暴露。
- 真正的边界在 `docker-compose.yml` 的 `ports: "127.0.0.1:8787:8787"` —— 宿主侧只绑回环。
- 因此：**安全边界靠配置而非代码**。任何人把 `ports` 改成 `8787:8787`（或 `0.0.0.0:`），服务立刻对整个局域网开放。M5 若引入外网访问，必须同时加 TLS 与鉴权审计，不能只改端口。

## 3 未覆盖 / 不能算通过（空真项）

| 项 | 状态 | 说明 | 归属 |
|---|---|---|---|
| 认证对话框中 PIN 脱敏 | **未覆盖** | Stage 4 出现 `PIN_IN_LOG=False`，但本轮**从未出现认证对话框、未注入过 PIN**，该结论是空真（"没发生"不等于"挡住了"）。不许改成通过 | M5 补真实注入场景 |
| 客户端新增文案的 copy 门禁 | **未覆盖** | 登录页等新增页文案**硬编码在 Java**，`copy_gate.py:31` 只扫 `values/*.xml`，所以 `COPY: PASS` 不含这些文案 | M5 扩门禁扫描面 |
| 会话过期判定 | **未实现** | 客户端 `expires_at` 只存不判（全仓无比较），过期由服务端 401 体现 | M5 |
| 401 后清理会话 | **未实现** | `LcSession.java:63-74` 收到 401 不清会话 | M5 |
| 同步错误分类 | **未实现** | `M3PageActivity.java:528-533` 把 401/429 也显示成「连不上本地服务」 | M5 |

## 4 Stage 5 打回项（数据正确性，M4 不可关账的原因）

四条：D1–D3 由 LC审客户端 复核、技术总监逐条进代码确认；D4 由 LC审核验 从代码挖出、技术总监核 `GapDb:204` 后撤回「已修」结论。

| # | 缺陷 | 代码锚点 | 后果 | 归属 |
|---|---|---|---|---|
| D1 | 推成功到写回 `remote_id` 之间无中间态/无事务，服务端 `save` 不读幂等键 | `FavoriteSync.java:57-61`；`server.js:464-483` | 崩在缝里 → 下次同步**重复建点** | 客户端（中间态+幂等键）+ 服务端（读 `Idempotency-Key`，复用 `db.js:130-133`） |
| D2 | 拉回硬编码坐向 | `GapDb.java:203-204`（`sitting=0/facing=180`），推上去的 `des` 在 `FavoriteSync.java:91-93` 被丢弃 | 换设备/重装后**所有收藏点坐向全错**（内容丢失） | 客户端 |
| D3 | `LcApi.deletePoint` 零调用点，本地硬删 | `GapDb.java:136-138`；`InterestListActivity.java:144` | 删除两端都不同步：本机删的复活、别处删的变幽灵点 | 客户端 + 契约补删除信号 |
| **D4** | **`P0-XACCT-PURGE`：换账号后整批硬删本地收藏**（不可逆 + 静默 + 整批真实数据，三个特征全占） | 认领 `GapDb.java:201-204`（`update(…, "owner_uid=0", null)`，**没有 `remote_id=0` 条件**）；遍历 `FavoriteSync.java:157` `db.listInterest()`（无账号参数，全量本地行）；守卫 `:163` `owner != uid`；purge `:164`；拉取落地 `GapDb.java:291-320` `upsertRemoteInterest` **不写 `owner_uid`**（新拉回行为 0）；`setOwnerUid` 仅定义于 `GapDb.java:194`、**全仓零调用点** | 「升级后 / 清数据后**第一次同步就直接登 B**」时，A 的 `remote_id>0` 老行被整批认领给 B → B 的 list 里没有 → 对账硬删；**先以 A 同步过一次再切号会测不到，P0 假通过** | 客户端（见 §4.1 三条裁定） |

> 复验判据（LC审客户端 2026-11-07 给出，摘要）：D1 = 同 key 两次 save 返回同 `point.id` 且行数 +1、并模拟「推成功不写回」再同步不得新增；D2 = 坐180/向0 的点换库同步后 dump 出现「坐180.0° 向0.0°」，`des` 缺失时的回落值要写死并测一条；D3 = 两个方向各测一次（A 删→B 全量拉不到；B 删→A 增量后有删除信号）。

### 4.1 D4 修复裁定与判据陷阱（技术总监 2026-11-07 裁定，逐条落代码）

1. **`adoptUnownedRows` 只认领 `owner_uid=0 AND remote_id=0`**：`remote_id>0` 说明该行在**某个**账号的服务端已存在，本地无从判断主人，自动认领就是猜。
2. **`owner_uid` 在创建时刻写死**：`insertInterest`（本地新建）与 `upsertRemoteInterest`（拉取落地）都要写当前 uid——后者现在根本不写（签名里连 uid 都没有）。
3. **`setOwnerUid` 要么接上、要么删掉**（零调用点的 setter 是埋坑）。

**判据陷阱（测试必读，否则 P0 假通过）**

| 陷阱 | 正确做法 |
|---|---|
| 先以 A 同步一次再切 B | 必须测「升级/清数据后**第一次同步就直接登 B**」，本地 A 的 3 条一条都不许少 |
| 用 `/health` 的 `favorites` 判 B 的增量 | **禁用**：那是全库 `deleted=0` 合计（实测 46，别人跑测试就变）。只认两条口径：① B 的 token 打 `GET /v2/userCollectPoint/list` 取 `data.count`；② 库内 `SELECT COUNT(*) FROM favorites f JOIN users u ON u.id=f.user_id WHERE u.username_lc='<B>' AND f.deleted=0` |
| 用「拉回来的行」测崩窗口（D1 判据 B） | **不可用**：服务端 `list` 不回显 `idem_key`，拉回行 `client_key` 为 NULL，`clientKeyOf()` 会现场生成新键 → 服务端必然新建，测到的是「拉回行没键」不是崩窗口。必须用**本地新建且已推过一次**的行再清 `remote_id` |

## 5 已知限制（接受但须记录）

| 编号 | 限制 | 风险 | 处理 |
|---|---|---|---|
| L1 | pepper 与 SQLite **同卷**（默认 `/data/.token-pepper`） | 整卷泄露时「DB 泄露拿不到可用会话」不成立 | 写进本文；M5 改 `/run/secrets` 挂载（compose 已留 secrets 注释模板） |
| L2 | `.gitignore` 忽略规则 | ~~未挡~~ **已加固**：`*.sqlite` / `*.sqlite-wal` / `*.sqlite-shm` / `.token-pepper`（技术总监 2026-11-07 亲自加，`git check-ignore` 双验证） | 已消除；后续新增敏感文件仍须先加规则再落盘 |
| L3 | 限流窗口在**内存** | 容器重启计数清零 | 接受（单机自用，`ratelimit.mjs` 已注明取舍） |
| L4 | 无 TLS | 明文仅限回环（`network_security_config.xml:4-9` 只放 `127.0.0.1`/`localhost`） | 接受；不得把服务暴露到非回环接口 |
| L5 | 会话 TTL 30 天（`LC_TOKEN_TTL_DAYS`） | 长期有效 token | 接受；`logout-all` 可全撤 |
| L6 | 客户端 Keystore 三处 P2：`clear()/save()` 用 `apply()`（登出落盘前被杀 → 服务端已撤销、本地仍显示已登录）、`token()` 在 UI 线程解密、`expires_at` 只存不判 | 体验/边界问题，非数据错误 | M5 |

## 6 证据锚点

- 安全实现：`backend/src/password.mjs`、`token.mjs`、`ratelimit.mjs`、`server.js:30-31,43-51,92-99,221,227-233`
- 独立复核：`docs/fidelity/verify-p0-judge.md` §26（68 checks / 越权 404 / 429 + `retry-after: 46` / E6.4 两证据）
- 客户端复核：`docs/fidelity/verify-m4-client.md`（`db0479c`，基线 `f285cf0`）
- 关账摘要：`docs/fidelity/m4-close-summary.md`（八条对账：第 3 条「达成但有 3 处数据缺陷待修」，第 8 条「部分」）
- 纪律：`docs/fidelity/checksheet.md` §10（空真六问，含第 6 问「一次改动改了什么 ≠ 合并后文件里现在有什么」）

## 7 变更记录

| 日期 | 变更 | 依据 |
|---|---|---|
| 2026-11-07 | 新增 §4 **D4 `P0-XACCT-PURGE`**（换账号整批硬删）与 §4.1 三条修复裁定、三处判据陷阱；L2 改为「已加固」 | 代码回源：`GapDb:201-204`（仅 `owner_uid=0` 条件）、`GapDb:173`、`GapDb:291-320`（`upsertRemoteInterest` 不写 `owner_uid`）、`setOwnerUid` 零调用点（`git grep` 仅 `GapDb:194` 定义）、`FavoriteSync:157/163/164`；总监 2026-11-07 裁定；审核验 §30.4–§30.6 |
