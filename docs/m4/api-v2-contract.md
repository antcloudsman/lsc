# M4 · API v2 契约与实现一致性（定稿）

> 定稿日期：2026-11-07　｜　基线：`develop` `71ff5c8`　｜　成文：LC审成文
> 定位：**契约正文不在此文**。契约唯一来源是 [`backend/api.md`](../../backend/api.md)（v2，205 行，已定稿）。本文只做三件事：冻结版本、列出「契约条款 → 实现锚点」的对应、声明未实现/未取证边界。

## 1 冻结声明

| 项 | 值 |
|---|---|
| 契约文件 | `backend/api.md`（v2）；v1 归档为 `backend/api-v1.md` |
| 前缀 | 业务接口全部 `/v2`；`/health` 不带版本前缀；旧 `/v1`、`/` 仅返回替代提示（`server.js:320-327`） |
| 状态 | **定稿**。此后若改契约须新开 v3 或在本文件追加「变更记录」，不得就地改写 v2 条款 |
| 实现基线 | `backend/src/{server.js,db.js,token.mjs,password.mjs,ratelimit.mjs}` |

## 2 已实现端点（逐条对 `server.js` 路由）

| 契约章节 | 端点 | 实现 |
|---|---|---|
| §1 通用 | `/health` | `server.js:294`（healthcheck 亦调用，`docker-compose.yml` healthcheck） |
| §2 账号（我方新增） | `POST /v2/auth/register` | `server.js` 路由表 |
| §2 | `POST /v2/auth/login` | 同上；限流 `loginUser: 5/60s`、`loginIp: 60/60s`、`loginIpHour: 600/h` |
| §2 | `POST /v2/auth/logout`、`POST /v2/auth/logout-all` | 同上 |
| §2 | `POST /v2/auth/password`（改密） | 同上；`passwordUser: 5/h` |
| §2 | `GET /v2/auth/me`、`GET /v2/auth/sessions` | 同上 |
| §3 收藏点（原版路径） | `POST /v2/userCollectPoint/list` | 同上 |
| §3 | `POST /v2/userCollectPoint/save` | `server.js:464-483` |
| §3 | `POST /v2/userCollectPoint/delete` | 同上（软删，`list` 过滤 `deleted=0`） |
| §4 反馈（`type:int`） | `POST /v2/feedback/add`、`POST /v2/feedback/list` | `server.js:506-524`（读 `idempotency-key`） |
| §5 账户设置 | — | **本轮不实现**，契约已声明（api.md §5） |

## 3 与实现的一致性核对

| 契约条款 | 契约要求 | 实现事实 | 结论 |
|---|---|---|---|
| 信封 | `{code,msg,data}`，成功码 200 | `fail()`/成功分支统一信封；客户端按 `code` 判成功（`LcApi.java:142-145`） | 一致 |
| 认证头 | 原版头名 `token`；我方兼容 `Authorization: Bearer` | `server.js:227-233`（`token` 优先，Bearer 也认） | 一致（超集） |
| 密码存储 | scrypt/bcrypt/argon2，禁明文/可逆 | `password.mjs`：`scrypt N=32768,r=8,p=1`、16B salt、64B key、`timingSafeEqual`；存储格式 `scrypt$N=…,r=…,p=…$salt$key` | 一致 |
| 会话 token | 不可猜测、可吊销 | `token.mjs`：32B `randomBytes` → base64url（43 字符），**非 JWT**；库内只存 `sha256(token+pepper)` | 一致 |
| pepper 来源 | 环境变量或 secrets 文件，不入库 | `token.mjs` 三级：`LC_TOKEN_PEPPER` → `LC_TOKEN_PEPPER_FILE`（默认 `/run/secrets/lc_token_pepper`）→ `/data/.token-pepper`（0600 首启生成，demo 兜底） | 一致，兜底有风险见 §4 |
| 限流 | 登录接口限流 + 429 | `ratelimit.mjs` 滑动窗；429 带 `retry-after`（`server.js:221`）；`LC_RATE_LIMIT=0` 可关（默认开） | 一致 |
| 绑定 | 只绑 127.0.0.1 | **容器内** `HOST=0.0.0.0`（`server.js:24-25`），**宿主机**端口映射 `127.0.0.1:8787:8787`（`docker-compose.yml`） | 一致（回环在宿主侧实现，见 §4） |
| CORS | 收窄 | 仅当 `LC_CORS_ORIGIN` 命中白名单才回 `access-control-allow-origin`（`server.js:92-99`）；默认空 = 不发 CORS 头 | 一致 |
| 反馈幂等 | 撞键返回既有行 | `db.js:130-133` 唯一索引 + `server.js:506-524` 读 `idempotency-key` | 一致 |
| 收藏点幂等 | 契约 §3 要求 client_id 幂等 | `/v2/userCollectPoint/save` **尚不读** `Idempotency-Key`（`server.js:464-483`） | **未达成，Stage 5 打回项**（见 §5） |

## 4 未实现 / 未取证的边界

1. **微信第三方登录**：仅逆向取证 + 入口占位 + DI（`D-031`），本轮不实现（契约 §8）。
2. **账户设置接口**（契约 §5）：只声明不实现。
3. **错误码表**：部分错误码标「未取证」，沿用 `backend/api.md` §1.3 的标注，不臆造。
4. **容器内 `0.0.0.0`**：不是「公开绑定」，但意味着**能否暴露取决于宿主端口映射**；任何人改 `docker-compose.yml` 的 `ports` 都会把服务暴露出去，此约束靠配置而非代码（已知限制见安全说明 §5）。

## 5 与 Stage 5 打回项的接口关系（不在本文修，只记契约侧要求）

| 打回项 | 契约侧应有动作 | 归属 |
|---|---|---|
| 收藏点重复创建 | `save` 读 `Idempotency-Key`，撞键返回既有行（复用 `db.js:130-133` 的模式） | LC服务端 |
| 删除两端不同步 | 契约需定义**删除信号**（tombstone 或以 `rev` 递增 + 删除标记），否则增量拉取永远学不到删除 | LC服务端（契约补充）+ LC客户端 |
| 坐向丢失 | `CollectPointModel.des` 已含「坐X° 向Y°」，契约无需改；由客户端拉回解析 | LC客户端 |

> 以上三条的关账判据见 `docs/fidelity/verify-m4-client.md` §6 与 LC审客户端 2026-11-07 给出的三条复验判据；本文不另立判据。

## 6 证据锚点

- 契约：`backend/api.md`（v2）、`backend/api-v1.md`（归档）
- 实现：`backend/src/server.js`、`db.js`、`token.mjs`、`password.mjs`、`ratelimit.mjs`
- 部署：`backend/docker-compose.yml`、`backend/Dockerfile`
- 自检：`backend/scripts/selfcheck.mjs`（容器内跑，68 checks，见 `docs/fidelity/m4-close-summary.md` §2 第 1 条）
