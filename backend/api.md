# LC Backend API v1（`/v1`）

> **状态：v1 已冻结为历史契约**（M4 起由 v2 取代）。**v2 契约草案见 [`api-v2.draft.md`](api-v2.draft.md)**：账号密码鉴权、会话 token、收藏点同步、投诉反馈、限流与日志脱敏；v2 实现后本文件归档为 `api-v1.md`，`/v1/*` 统一返回 410 `deprecated`。

山水罗盘本地服务端骨架：风水案例库、云同步、分享链接、用户设置同步。**本地优先**：单个 SQLite 文件即全部状态，无云依赖也能演示；全部依赖在 Docker 内，宿主机只留源码。

- 默认地址：`http://127.0.0.1:8787`（compose 只绑定回环地址）
- 数据：`/data/lc.sqlite`（Docker volume `lc-backend_lc-data`）
- 传输：JSON / UTF-8；时间戳一律 ISO-8601 UTC；请求体上限 1 MB
- 版本：`0.1.0`（骨架，契约以本文件为准）
- 实现：Node 22 标准库（`node:http` + `node:sqlite`），**无 npm 依赖**

## 1. 约定

### 1.1 身份（v1 无鉴权）

v1 是本地演示版，没有登录体系。`owner_id` 是客户端生成的**不透明设备/用户标识**，服务端只按它隔离数据：

- 格式：`[A-Za-z0-9._:-]{1,128}`（例如 `dev_L2E0222531014833`）
- 服务端不做身份校验，**绑定 127.0.0.1 仅本机可用**；上线前必须加 token 鉴权并改注释，见 §6。

### 1.2 通用响应

成功：`200` / `201`，资源包在命名字段里（`case`、`items`、`settings`、`share`、`sync`）。

错误：

```json
{ "error": { "code": "case_not_found", "message": "case case_x not found" } }
```

| 状态 | `code` | 含义 |
|---|---|---|
| 400 | `invalid_field` / `invalid_json` / `invalid_owner_id` / `invalid_case_id` / `invalid_limit` / `invalid_since` | 参数不合法 |
| 404 | `case_not_found` / `share_not_found` / `not_found` | 资源不存在 |
| 405 | `method_not_allowed` | 方法不支持 |
| 409 | `case_exists` | 客户端指定了已存在的 `id` |
| 413 | `payload_too_large` | 请求体超过 1 MB |
| 415 | `unsupported_media_type` | 请求体不是 `application/json` |
| 500 | `internal_error` | 未处理异常（服务端日志） |

CORS：`Access-Control-Allow-Origin: *`（`OPTIONS` 预检返回 204），供 WebView 内 H5 分享页/调试页直连。

### 1.3 案例对象（Case）

```json
{
  "id": "case_9f0c...",
  "owner_id": "dev_L2E0222531014833",
  "title": "西湖某宅 · 巽山乾向",
  "sit_degree": 135,
  "facing_degree": 315,
  "compass_type": "sanyuan",
  "location": { "lat": 30.25, "lng": 120.15, "name": "杭州", "address": "…", "coord_system": "wgs84" },
  "tags": ["住宅", "坐向"],
  "notes": "自由文本",
  "data": { "panel": { "mountains": "…" } },
  "created_at": "2026-11-05T02:00:00.000Z",
  "updated_at": "2026-11-05T02:00:00.000Z",
  "rev": 12,
  "deleted": false
}
```

字段规则：

| 字段 | 类型/范围 | 说明 |
|---|---|---|
| `title` | string, 1–200 | 必填 |
| `sit_degree` | number, [0, 360] | 坐向度数；`360` 归一为 `0` |
| `facing_degree` | number, [0, 360] | 朝向；不传由客户端自行按 `(sit+180)%360` 计算 |
| `compass_type` | string ≤ 64 | 盘面标识（对应 5 盘面，由客户端约定） |
| `location` | object / null | `lat` [-90,90]、`lng` [-180,180]，`name`/`address`/`coord_system` ≤ 200 |
| `tags` | string[] ≤ 32 项，每项 ≤ 32 字符 | |
| `notes` | string ≤ 20000 | 可为空 |
| `data` | object / null ≤ 256 KB | 客户端扩展字段（圈层快照、截图元数据等） |
| `rev` | integer | 服务端单调递增版本号，用于增量同步 |
| `deleted` | boolean | 软删除标记，默认列表不返回 |

## 2. 健康检查

`GET /health`（同时支持 `/v1/health`）

```json
{
  "status": "ok",
  "service": "lc-backend",
  "version": "0.1.0",
  "time": "2026-11-05T02:00:00.000Z",
  "uptime_s": 42,
  "db": "ok",
  "cases": 3,
  "shares": 1
}
```

`status` 为 `ok` 当且仅当 SQLite 可读；`db` 为 `ok`/`error`。容器内 HEALTHCHECK 与 `scripts/smoke.ps1`（可选）都依赖此端点。

## 3. 案例库

### 3.1 列表

`GET /v1/cases?owner_id=<id>&since=<rev>&limit=<1..200>&include_deleted=0|1`

- `since`：只返回 `rev > since` 的案例（增量拉取；默认 0）
- `limit`：默认 100，最大 200
- `include_deleted=1`：包含软删除记录

```json
{ "items": [ { "id": "case_…", "rev": 3, "…": "…" } ], "count": 1 }
```

### 3.2 新建

`POST /v1/cases`

```json
{
  "owner_id": "dev_xxx",
  "title": "案例标题",
  "sit_degree": 135,
  "facing_degree": 315,
  "compass_type": "sanyuan",
  "tags": ["住宅"],
  "notes": "",
  "location": { "lat": 30.25, "lng": 120.15, "name": "杭州" },
  "data": {}
}
```

- 201 返回 `{ "case": {…} }`
- 可传 `id`（`[A-Za-z0-9_-]{6,64}`）用于**离线创建后上传**；已存在则 409，避免覆盖
- 不传 `id` 时服务端生成 `case_<32hex>`

### 3.3 读取 / 更新 / 删除

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/cases/{id}?owner_id=<id>` | 200 `{case}`；软删除后 404 |
| PUT | `/v1/cases/{id}` | 部分更新：只改传入字段，body 需含 `owner_id`；200 `{case}` |
| DELETE | `/v1/cases/{id}?owner_id=<id>` | 软删除；200 `{deleted:true,id}` |

`PUT` 语义是**字段合并**（非全量替换）：未传字段保持原值；`location`/`data` 传 `null` 可清空。每次写/删都会推进 `rev` 并写入变更日志。

## 4. 设置同步

### 4.1 读取

`GET /v1/settings/{owner_id}`

```json
{ "owner_id": "dev_xxx", "settings": { "theme": "dark" }, "rev": 8, "updated_at": "…" }
```

无记录时返回 `settings: {}`、`rev: 0`、`updated_at: null`。

### 4.2 写入

`PUT /v1/settings/{owner_id}`

```json
{ "settings": { "theme": "dark", "active_compass": 3 } }
```

- `settings` 必须是 JSON 对象，≤ 256 KB
- 200 返回 `{ owner_id, settings, rev, updated_at }`
- 整对象覆盖（最后写入胜出），不做字段级合并

## 5. 同步与分享

### 5.1 增量拉取

`GET /v1/sync/pull?owner_id=<id>&since=<seq>&limit=<1..200>`

```json
{
  "sync": {
    "cursor": 42,
    "changes": [
      { "seq": 41, "entity": "case", "entity_id": "case_…", "op": "update", "payload": { "…": "case snapshot" }, "rev": 9, "changed_at": "…" },
      { "seq": 42, "entity": "case", "entity_id": "case_…", "op": "delete", "payload": null, "rev": 10, "changed_at": "…" }
    ]
  }
}
```

- 游标是变更日志自增 `seq`；客户端保存 `cursor`，下次传 `since=cursor`
- `op`：`create` / `update` / `delete`；`payload` 为写入时的**完整快照**（`delete` 为 `null`）
- 拉到 `changes` 为空表示已同步；`limit` 截断时 `cursor` 为本次最后一条

### 5.2 批量推送（离线同步）

`POST /v1/sync/push`

```json
{
  "owner_id": "dev_xxx",
  "cases": [ { "id": "case_offline_1", "title": "离线新建", "sit_degree": 90 } ],
  "settings": { "theme": "dark" }
}
```

- `cases` 内每条按 §1.3 校验；带 `id` 且服务端存在 → 合并更新，不存在 → 新建
- 服务端已软删除的同 id → 记入 `conflicts`（`reason: "deleted_on_server"`），不复活
- 200 返回 `{ "sync": { "applied": [case…], "conflicts": [ {id, reason, server_rev} ], "settings": {…}|null } }`
- **冲突策略**：最后写入胜出（服务端时间）；不做逐字段三方合并。v1 仅保证幂等，不保证跨设备无覆盖。

### 5.3 创建分享链接

`POST /v1/shares`

```json
{ "owner_id": "dev_xxx", "case_id": "case_…", "expires_in_days": 30 }
```

- `expires_in_days`：1–365，默认 30
- 201 返回：

```json
{
  "share": {
    "token": "9f7c…32hex",
    "url": "http://127.0.0.1:8787/s/9f7c…",
    "case_id": "case_…",
    "created_at": "…",
    "expires_at": "…"
  }
}
```

`PUBLIC_BASE_URL` 环境变量决定 `url` 前缀（局域网演示可设为 `http://<pc-ip>:8787`）。

### 5.4 打开 / 撤销分享

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/shares/{token}` | 200 `{share:{token,created_at,expires_at,case}}`；案例快照**不含 `owner_id`**；撤销/过期/删除 → 404 |
| GET | `/s/{token}` | 同一链接的 HTML 页面（`text/html`），供手机浏览器/微信内打开 |
| DELETE | `/v1/shares/{token}?owner_id=<id>` | 撤销；200 `{revoked:true,token}` |

分享是**读取快照**：打开时读取案例当前内容，之后案例更新会反映在链接里；撤销即时生效。

## 6. 运行与自检

```powershell
cd E:\APPDetector\output\backend
docker compose up -d --build
docker compose ps
curl.exe -s http://127.0.0.1:8787/health
docker compose exec api node --no-warnings scripts/selfcheck.mjs
docker compose down          # 数据保留在 volume；docker compose down -v 会清空
```

宿主机不需要 Node/JDK：`docker compose up` 一键起，`node:22-bookworm-slim` 镜像内运行。

## 7. 边界与非目标（v1）

- 无鉴权、无多租户、无限流、无审计日志 → 只绑 `127.0.0.1`，不可直接公网暴露
- 不存图片二进制（截图/缩略图后续走对象存储或分享页 base64，单独立项）
- 不做天地图代理（地图 key/配额由客户端与 LC地图 负责）
- 不替代 `mechanics.json` 数值源：罗盘常量仍以 `app/mechanics.json` 为唯一来源，服务端不下发
- SQLite 使用 Node 22 内置 `node:sqlite`（当前标记 experimental）；契约层不依赖其实现，后续可平移 Postgres

## 8. 后续增量（未实现，仅登记）

| 项 | 说明 | 依赖 |
|---|---|---|
| 鉴权 | device token / 登录，`owner_id` 改为服务端签发 | 产品确认账号体系 |
| 图片附件 | 案例截图上传/缩略图/分享页图集 | 对象存储或本地卷策略 |
| 协作案例 | 共享编辑、评论 | 权限模型 |
| 服务端推送 | 同步长连接/SSE | 客户端网络层 |
