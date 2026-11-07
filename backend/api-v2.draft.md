# LC Backend API v2 契约草案（`/v2`）· **DRAFT，未实现**

> 状态：草案（M4 Stage 1），**没有一行实现代码**。等 LC逆向 的原版账户/业务取证与 LC产品 的缺失模块清单对齐后再进 Stage 2 实现。
> 基线：仓库内 `backend/` v1 骨架（`0.1.0`，Node 22 + `node:sqlite`，无鉴权，`owner_id` 隔离）。
> 日期：2026-11-07 · 作者：LC服务端
> 读法：标 **[待对齐]** 的字段/语义等取证与产品清单确认；其余为可实现的定案草案。

## 0. 与 v1 的差异总览

| 维度 | v1（现状） | v2（本草案） |
|---|---|---|
| 身份 | 无鉴权，`owner_id` 由客户端自报 | 账号密码注册/登录，**服务端签发会话 token**，资源按 `user_id` 隔离 |
| 密码 | 无 | `scrypt`（`node:crypto`，无 npm 依赖），格式见 §1.3 |
| token | 无 | 32 字节随机、`base64url`，DB 只存 `sha256(token)`，30 天有效 |
| 收藏点 | 无独立实体（案例库 `cases`） | 新增 `favorites`（收藏点）+ 同步 |
| 投诉反馈 | 无 | 新增 `feedback`（问题反馈 / 订单投诉两级） |
| 同步 | `changes` 变更日志 + `since=seq` 游标 | 保留同一模型，实体扩展 `favorite` |
| 分享 | 匿名创建（`owner_id`） | 创建需登录，打开仍匿名（`/s/{token}`） |
| CORS | `Access-Control-Allow-Origin: *` | 默认不发 ACAO，`LC_CORS_ORIGIN` 白名单最小化 |
| 限流/日志 | 无 | 登录/注册/反馈限流 + 结构化脱敏日志 |
| 路由 | `/v1/*` | `/v2/*`；**`/v1/*` 一律 410 `deprecated`**（本仓客户端尚未接后端，可硬切） |
| 健康检查 | `/health` | `/health` 不变（容器 HEALTHCHECK 依赖），另加 `/v2/health` |

## 1. 鉴权与会话

### 1.1 账号

```json
{
  "id": "user_9f0c1d2e3a4b5c6d7e8f9012345678ab",
  "username": "laoliu",
  "display_name": "老刘",
  "created_at": "2026-11-07T06:00:00.000Z",
  "last_login_at": "2026-11-07T06:00:00.000Z",
  "status": "active"
}
```

| 字段 | 规则 |
|---|---|
| `username` | 3–32 字符，`[A-Za-z0-9._-]`，**大小写不敏感唯一**（入库保留原大小写，查重用小写） |
| `password` | 8–128 字符；不含空白首尾；不强制复杂度（本地自用），但禁止等于用户名 |
| `display_name` | 0–32 字符，可空（默认 = `username`） |

**[待对齐]** 原版是「手机号 + 密码」还是「账号 + 密码」：取证确认后决定是否加 `phone` 字段与 `+86` 规范化；草案先按 `username` 设计，`phone` 可作为 `username` 的一种（见 §6 迁移）。

### 1.2 会话与请求头

- 登录成功返回 `session.token`（32 字节随机，`base64url`，43 字符）与 `expires_at`（默认 30 天）。
- 之后所有受保护请求带 `Authorization: Bearer <token>`。
- **服务端只存 `sha256(token + LC_TOKEN_PEPPER)`**，DB 泄露不能直接冒用会话。
- 会话可在 `GET /v2/auth/sessions` 列出、按 id 撤销；**同时最多 10 个**，超限撤最早。
- `last_used_at` 每 ≥60s 才写一次（避免每次请求都写库）。

### 1.3 密码哈希（实现约束，写死）

```
scrypt$N=32768,r=8,p=1$<salt_b64 16B>$<hash_b64 64B>
```

- 每次注册/改密生成新 salt；校验用 `crypto.timingSafeEqual`。
- **禁止**明文、`md5/sha1/sha256(password)`、可逆加密；**禁止**在日志/错误信息里回显密码。
- 登录失败统一返回 `invalid_credentials`（不区分「用户不存在」/「密码错」），且用户不存在时也跑一次等价的 scrypt，防时间侧信道与枚举。

## 2. 端点

### 2.1 鉴权

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/v2/auth/register` | 无 | `{username, password, display_name?}` → 201 `{user, session}` |
| POST | `/v2/auth/login` | 无 | `{username, password, device?}` → 200 `{user, session}` |
| POST | `/v2/auth/logout` | Bearer | 撤销当前会话 → 200 `{revoked: true}` |
| POST | `/v2/auth/logout-all` | Bearer | 撤销该用户全部会话 → 200 `{revoked: N}` |
| POST | `/v2/auth/password` | Bearer | `{current_password, new_password}` → 200 `{changed: true, revoked_other_sessions: N}` |
| GET | `/v2/auth/me` | Bearer | 当前用户 `{user}` |
| PATCH | `/v2/auth/me` | Bearer | `{display_name}` → `{user}` |
| GET | `/v2/auth/sessions` | Bearer | `{items:[{id, device, created_at, last_used_at, expires_at, current}]}` |
| DELETE | `/v2/auth/sessions/{id}` | Bearer | 撤销指定会话 → `{revoked:true}` |

`session` 对象：`{token, expires_at, token_type:"Bearer"}`。`token` 只在签发响应里出现一次，之后不可再取。

### 2.2 收藏点（原版「收藏点」，M4 闭环主链路）

```json
{
  "id": "fav_5d2a9c7e1f0b3a4c8d9e0f1a2b3c4d5e",
  "name": "西湖某宅",
  "lat": 30.2501,
  "lng": 120.1502,
  "address": "浙江省杭州市西湖区…",
  "coord_system": "gcj02",
  "tags": ["住宅"],
  "note": "坐向：巽山乾向",
  "created_at": "…",
  "updated_at": "…",
  "rev": 7,
  "deleted": false
}
```

| 字段 | 规则 |
|---|---|
| `name` | 1–100，必填 |
| `lat` / `lng` | `[-90,90]` / `[-180,180]`，必填 |
| `address` | ≤ 200，可空 |
| `coord_system` | `wgs84` \| `gcj02`，默认 `gcj02`（**[待对齐]** 原版天地图坐标口径） |
| `tags` | ≤ 16 项 × ≤ 32 字符 |
| `note` | ≤ 2000，可空 |
| `rev` | 服务端单调递增（与案例库共用 `meta.rev` 计数器） |

端点：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v2/favorites?since=<rev>&limit=<1..200>&include_deleted=0\|1` | 增量拉取 `{items, count}` |
| POST | `/v2/favorites` | 201 `{favorite}`；可带客户端 `id`（离线新建）→ 已存在 409 |
| GET | `/v2/favorites/{id}` | 200 `{favorite}`；他人资源按 404（不泄露存在性） |
| PUT | `/v2/favorites/{id}` | 部分更新（字段合并），200 `{favorite}` |
| DELETE | `/v2/favorites/{id}` | 软删除，200 `{deleted:true, id}` |

服务端**不做坐标去重**（同一点可收藏多次），去重是客户端交互策略，**[待对齐]** 产品是否要「同名同坐标覆盖」。

### 2.3 投诉反馈

```json
{
  "id": "fb_1a2b3c4d5e6f708192a3b4c5d6e7f809",
  "category": "problem",
  "reason": "闪退",
  "contact": "wechat_abc",
  "description": "打开读盘页后旋转罗盘闪退",
  "order_ref": null,
  "app": { "version": "1.6.1", "build_commit": "<40hex?>" },
  "device": { "model": "L2E0222531014833", "sdk": 35 },
  "status": "received",
  "created_at": "…"
}
```

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v2/feedback` | 201 `{feedback}`；`Idempotency-Key` 头可选（同 key 5 分钟内返回同一结果） |
| GET | `/v2/feedback?limit=<1..50>&before=<iso>` | 本人反馈列表（对应「我的」页） |
| GET | `/v2/feedback/{id}` | 本人单条；他人 404 |

- `category`：`problem`（问题反馈）\| `order`（订单投诉）；**[待对齐]** 原版两级页面的实际枚举与文案。
- `reason` 1–100、`description` 10–5000、`contact` ≤ 100 可空。
- **v2 不做图片附件上传**：`提交` 时的「问题截图」只允许客户端本地选中，上传按 DI 登记（对象存储/卷策略单独立项），接口预留 `attachments: []` 字段但**只接受空数组**。
- 限流：每用户 5 条/小时；`status` 固定 `received`（不伪造厂商回执）。

### 2.4 案例库 / 设置 / 分享（沿用 v1 语义，改为鉴权）

| 方法 | 路径 | 变化 |
|---|---|---|
| GET/POST | `/v2/cases` | 鉴权后按 `user_id` 过滤，不再需要 `owner_id` 参数 |
| GET/PUT/DELETE | `/v2/cases/{id}` | 同上 |
| GET/PUT | `/v2/settings` | 路径去掉 `{owner_id}`，从 token 取用户 |
| GET | `/v2/sync/pull?since=<seq>&limit` | 实体扩展 `favorite` |
| POST | `/v2/sync/push` | `{favorites:[…], settings?, cases?}` |
| POST | `/v2/shares` | 需登录创建；`GET /v2/shares/{token}` 与 `/s/{token}` 仍匿名可读 |

## 3. 错误码（v2）

```json
{ "error": { "code": "invalid_credentials", "message": "账号或密码不正确", "request_id": "req_…" } }
```

| 状态 | code | 场景 |
|---|---|---|
| 400 | `invalid_json` / `invalid_field` / `invalid_limit` / `invalid_since` | 参数问题；`invalid_field` 带 `field` |
| 400 | `weak_password` / `invalid_username` | 注册/改密规则不满足 |
| 401 | `invalid_credentials` | 登录失败（不区分原因） |
| 401 | `token_missing` / `token_invalid` / `token_expired` | 未带/无效/过期 token |
| 403 | `forbidden` | 越权操作（**仅**在资源归属可安全暴露时；默认用 404） |
| 404 | `not_found` / `case_not_found` / `share_not_found` / `favorite_not_found` | 资源不存在或不属于当前用户 |
| 409 | `username_taken` / `favorite_exists` / `case_exists` | 唯一冲突 |
| 410 | `deprecated` | 访问 `/v1/*`（见 §6.4） |
| 413 | `payload_too_large` | json ≤ 256 KB；`sync/push` ≤ 1 MB |
| 415 | `unsupported_media_type` | 非 `application/json` |
| 429 | `rate_limited` | 带 `Retry-After` 秒数 |
| 500 | `internal_error` | 仅记日志，不回堆栈 |

## 4. 安全基线（实现必须逐条落地）

1. **绑定与暴露**：容器内 `HOST=0.0.0.0` 仅供 compose 端口映射；compose **只发布 `127.0.0.1:8787`**，禁止 `0.0.0.0:8787`。
2. **CORS**：默认不返回 `Access-Control-Allow-Origin`；`LC_CORS_ORIGIN` 逗号白名单，逐个精确匹配，不用 `*`；不允许 `credentials: true`（token 走 header）。
3. **限流**（内存滑窗，单实例足够；多实例再上 Redis）：
   - 登录：每 IP 10 次/分、每 `username` 5 次/分、每 IP 50 次/小时；
   - 注册：每 IP 3 次/小时；
   - 改密：每用户 3 次/小时；
   - 反馈：每用户 5 次/小时。
4. **日志脱敏**：绝不打印 `Authorization`、`password`、`token`；统一 `logSafe(event, {user_id, path, status, ms})`；请求体只在 `LC_LOG_BODY=1`（仅本机调试）下打印，且 `/v2/auth/*` 永久屏蔽。
5. **密钥**：`LC_TOKEN_PEPPER` 走环境变量或仓库外 secrets 文件（`E:\APPDetector\secrets\`），**不入库、不写进 compose 明文**；SQLite 本身无密码，靠 volume 权限与回环绑定。
6. **响应**：错误不回堆栈；`X-Request-Id` 由服务端生成并回显，便于对日志。
7. **请求体上限**：鉴权类 16 KB、一般 256 KB、`sync/push` 1 MB；超限 413。

## 5. 数据模型（v2）

```sql
-- 新增
users(id TEXT PRIMARY KEY, username TEXT NOT NULL, username_lc TEXT NOT NULL UNIQUE,
      display_name TEXT, password_hash TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'active',
      created_at TEXT NOT NULL, last_login_at TEXT, failed_attempts INTEGER NOT NULL DEFAULT 0,
      locked_until TEXT);
sessions(id TEXT PRIMARY KEY, user_id TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE,
         device TEXT, created_at TEXT NOT NULL, last_used_at TEXT, expires_at TEXT NOT NULL,
         revoked INTEGER NOT NULL DEFAULT 0);
favorites(id TEXT PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL, lat REAL NOT NULL,
          lng REAL NOT NULL, address TEXT, coord_system TEXT, tags TEXT, note TEXT,
          created_at TEXT NOT NULL, updated_at TEXT NOT NULL, rev INTEGER NOT NULL,
          deleted INTEGER NOT NULL DEFAULT 0);
feedback(id TEXT PRIMARY KEY, user_id TEXT NOT NULL, category TEXT NOT NULL, reason TEXT NOT NULL,
         contact TEXT, description TEXT NOT NULL, order_ref TEXT, app_json TEXT, device_json TEXT,
         attachments TEXT, status TEXT NOT NULL DEFAULT 'received', created_at TEXT NOT NULL,
         idem_key TEXT, idem_at TEXT);
schema_migrations(name TEXT PRIMARY KEY, applied_at TEXT NOT NULL);

-- 改造（保留 v1 列，新增 user_id；v1 行 user_id 为 NULL，v2 不返回）
cases     + user_id TEXT            -- 旧 owner_id 保留
settings  + user_id TEXT            -- 旧主键 owner_id 保留；v2 用 user_id 唯一
changes   + user_id TEXT            -- 旧 owner_id 保留
shares    + user_id TEXT            -- 旧 owner_id 保留
```

索引：`favorites(user_id, rev)`、`feedback(user_id, created_at)`、`sessions(user_id, revoked)`、`changes(user_id, seq)`。

## 6. 迁移方案（v1 → v2）

### 6.1 版本与分支

- 版本 `0.1.0` → `0.2.0`；实现分支 `feature/服务端-m4-auth`（Stage 2），一次落「schema + auth + favorites + feedback + 限流 + 日志」。
- 契约冻结后本文替换为 `backend/api.md` 的 v2 正式版；v1 内容归档到 `backend/api-v1.md`（只读）。

### 6.2 Schema 迁移

- 新增 `schema_migrations`，按 `001_init`（v1 基线标记）→ `002_users_sessions` → `003_user_id_columns` → `004_favorites` → `005_feedback` 顺序执行；**每次启动幂等跑**，已应用跳过。
- `ALTER TABLE … ADD COLUMN` 在 SQLite 上安全（无重写）；`settings` 需要 `(user_id)` 唯一索引，先加列再生索引，旧行 `user_id IS NULL` 不冲突（SQLite UNIQUE 允许多个 NULL）。
- v1 数据（`owner_id` 行）**不自动归属**任何账号；提供一次性脚本 `scripts/claim-owner.mjs --owner dev_x --username laoliu`（仅本地演示，需显式执行，默认不动数据）。

### 6.3 切换步骤（本机 Docker）

```powershell
cd backend
docker compose down                      # 停服务
docker run --rm -v lc-backend_lc-data:/data -v ${PWD}:/backup alpine \
  sh -c "cp /data/lc.sqlite /backup/lc.sqlite.v1.bak"   # 先备份
docker compose up -d --build             # 启动时自动跑 002–005
docker compose exec api node --no-warnings scripts/selfcheck.mjs   # 自检追加 auth/favorites/feedback 用例
```

- `selfcheck.mjs` 扩到 v2：注册 → 登录 → 未带 token 401 → 建收藏 → 拉回 → 提交反馈 → 登出 → token 失效 401。
- 回滚：`docker compose down` → 还原 `lc.sqlite.v1.bak` → 部署上一镜像 tag（v1 代码保留在 `0.1.0` tag）。

### 6.4 兼容策略

- `/v1/*` 全量返回 **410 `deprecated`**（带 `{replacement:"/v2/…"}`），不保留未鉴权入口——留着等于绕过本轮安全红线。
- `/health` 不变（容器 HEALTHCHECK、`scripts/smoke.ps1` 依赖）。
- 本仓 Android 客户端目前**未接后端**（全仓无 `8787`/`/v1/` 引用），因此硬切无客户端兼容成本；客户端接入在 M4 Stage 3。

### 6.5 环境变量增量

| 变量 | 默认 | 用途 |
|---|---|---|
| `LC_TOKEN_TTL_DAYS` | `30` | 会话有效期 |
| `LC_TOKEN_PEPPER` | 空（dev） | token 哈希加盐；生产必须给（secrets 文件） |
| `LC_CORS_ORIGIN` | 空 | 逗号白名单；空 = 不发 ACAO |
| `LC_LOG_BODY` | `0` | 仅本机调试；`/v2/auth/*` 始终屏蔽 |
| `LC_RATE_LIMIT` | `1` | `0` 仅压测用，启动时打 warning |

## 7. 待对齐清单（Stage 1 结束前必须回填）

1. **[逆向]** 原版登录方式：手机号/账号？是否强制绑定手机、验证码？错误码与提示文案（用于对齐 UI 文案，不照搬厂商接口）。
2. **[逆向]** 收藏点字段全集：是否有分类/图标/备注上限；是否云同步、增量口径。
3. **[逆向]** 投诉反馈两级页面的分类枚举、字段必填性、提交成功/失败提示。
4. **[逆向]** 微信登录入口形态（只做占位 + DI，不实现）。
5. **[产品]** 收藏点是否需要服务端去重、上限、以及「最近同步时间」展示要求。
6. **[产品]** 账户设置页「绑定手机/绑定微信/注销账户」的可见与可点范围（v2 只做展示 + DI 标注）。

## 8. 非目标（v2 仍不做）

- 图片/附件上传（DI，单独立项；`attachments` 只接受空数组）。
- 微信/第三方 OAuth 实际登录（只留入口占位与 DI）。
- 多设备冲突的三方合并（沿用最后写入胜出 + `conflicts` 回报）。
- 公网部署、TLS 终止、多租户与配额。
- 服务端下发罗盘常量（仍以 `app/mechanics.json` 为唯一来源）。
