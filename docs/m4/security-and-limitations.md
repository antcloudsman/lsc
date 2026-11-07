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

三条均由 LC审客户端 复核、技术总监逐条进代码确认：

| # | 缺陷 | 代码锚点 | 后果 | 归属 |
|---|---|---|---|---|
| D1 | 推成功到写回 `remote_id` 之间无中间态/无事务，服务端 `save` 不读幂等键 | `FavoriteSync.java:57-61`；`server.js:464-483` | 崩在缝里 → 下次同步**重复建点** | 客户端（中间态+幂等键）+ 服务端（读 `Idempotency-Key`，复用 `db.js:130-133`） |
| D2 | 拉回硬编码坐向 | `GapDb.java:203-204`（`sitting=0/facing=180`），推上去的 `des` 在 `FavoriteSync.java:91-93` 被丢弃 | 换设备/重装后**所有收藏点坐向全错**（内容丢失） | 客户端 |
| D3 | `LcApi.deletePoint` 零调用点，本地硬删 | `GapDb.java:136-138`；`InterestListActivity.java:144` | 删除两端都不同步：本机删的复活、别处删的变幽灵点 | 客户端 + 契约补删除信号 |

> 复验判据（LC审客户端 2026-11-07 给出，摘要）：D1 = 同 key 两次 save 返回同 `point.id` 且行数 +1、并模拟「推成功不写回」再同步不得新增；D2 = 坐180/向0 的点换库同步后 dump 出现「坐180.0° 向0.0°」，`des` 缺失时的回落值要写死并测一条；D3 = 两个方向各测一次（A 删→B 全量拉不到；B 删→A 增量后有删除信号）。

## 5 已知限制（接受但须记录）

| 编号 | 限制 | 风险 | 处理 |
|---|---|---|---|
| L1 | pepper 与 SQLite **同卷**（默认 `/data/.token-pepper`） | 整卷泄露时「DB 泄露拿不到可用会话」不成立 | 写进本文；M5 改 `/run/secrets` 挂载（compose 已留 secrets 注释模板） |
| L2 | `.gitignore` **未挡** `.token-pepper` / `*.sqlite` | 一次误 `git add` 就可能入库 | 立即加两条忽略规则（预防性，归属 LC服务端）；本文记录缺口 |
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
