# LC Backend API v2（`/v2`）

> 状态：**v2 定稿（已实现）**。M4 Stage 2 在 `backend/src` 落地，容器内自检 `scripts/selfcheck.mjs`（68 项）覆盖账号五链路 + 越权 + 会话过期 + 限流。v1 原文归档在 [`api-v1.md`](api-v1.md)（只读），`/v1/*` 统一返回 `410 deprecated`。
> 对齐依据：LC逆向 `docs/fidelity/m4/account-reverse.md`（`16f3018`）——基址/信封/认证头/登录/收藏/反馈全部按原版取证结果落；标注规则见 §0.2。
> 安全红线：全部依赖在 Docker 内（Node 22 + `node:sqlite`，无 npm 依赖）、只绑 `127.0.0.1`、密码 scrypt、token 不透明随机且只存哈希、日志脱敏。

## 0. 与原版协议的关系

### 0.1 已取证事实（引用即锚点，不重复取证）

| 项 | 原版值 | 出处 |
|---|---|---|
| 基址 | `https://globe.xinzhi.space` | `net/ApiHelper.smali` |
| 回包信封 | `ApiResult<T>{ code:int, msg:String, data:T }` | `net/ApiResult.smali` |
| **成功码** | **`200`**（`0xC8`，不是 0） | `ApiResult.smali:111` |
| 认证头 | **`token: <opaque>`**，无值时**不带该头** | `HeaderInterceptor.smali:388` |
| token 存储 | MMKV `AccountManager` / `currentAccount`，值是 `AccountModel` 的 Gson 整串 | `AccountManager` |
| 登录 | `POST api/user/login`，`LoginParameter` 11 字段；`loginType` ∈ `phone/phoneOauth/wechat/qq/honor/device` | `ApiService` / `LoginParameter$LoginType` |
| 注册 | **原版无注册页/注册接口**：手机号+验证码登录即注册 | manifest / `ApiService` 全量 |
| 收藏点 | `GET api/userCollectPoint/list`、`POST …/save`、`DELETE …/delete?id=`；`CollectPointModel` 13 字段 | `ApiService` / `CollectPointModel` |
| 投诉反馈 | `POST api/feedback/add`、`GET api/feedback/list`；`AddFeedbackReqModel(attachments, contactWay, orderNum, reasons, remark, type:int)`，实测 `type=2`、`remark` 恒空 | `ApiService` / `FeedbackViewModel` |

### 0.2 标注规则（本文强制）

- **【原版】**：有 DEX/真机证据，字段名/取值照抄，不改写。
- **【我方新增】**：原版没有（账号密码登录/注册；限流；rev 增量同步等），验收不得当原版缺失判 1:1。
- **【原版未取证】**：原版存在但未取到取值域/错误码，本文只写已知部分并标注。
- 我方新增字段**不得与【原版】同名列**，避免客户端误当原版语义。

## 1. 通用约定

### 1.1 信封

成功（HTTP 200/201）：

```json
{ "code": 200, "msg": "ok", "data": { } }
```

失败（HTTP 4xx/5xx，`data` 为 `null`）：

```json
{ "code": 401, "msg": "账号或密码不正确", "data": null }
```

- `code` 与服务端结果一致（成功固定 200，与原版一致）；`msg` 为人类可读中文；`data` 为资源包。**成功时 HTTP 可能是 200 或 201（如注册/新建收藏点/提交反馈），但信封 `code` 恒为 200**，客户端只看 `code==200` 即可，不看 HTTP 状态。
- 失败：`code` 与 HTTP 状态一致，`data` 为 `null`（唯一的例外是 409 版本冲突，见 §3.2）。

### 1.2 认证（【原版】头名 + 【我方】扩展）

- 受保护请求带 **`token: <opaque>`**。这是**我方 32 字节随机 token**（`base64url`，43 字符），非 JWT——与原版「不透明串 + 有效期」一致（总监 2026-11-07 定）。
- 服务端只存 `sha256(token + LC_TOKEN_PEPPER)`；`Authorization: Bearer <token>` 作为**我方扩展**同时接受（便于 curl/调试），两者等价。
- 服务端不存明文 token；DB 泄露不能直接冒用会话。
- pepper 来源（按优先级，永不入库）：环境变量 `LC_TOKEN_PEPPER` → 文件 `/run/secrets/lc_token_pepper`（Docker secret，`LC_TOKEN_PEPPER_FILE` 可改）→ `/data/.token-pepper`（首次启动生成，`0600`，仅容器内；compose 默认走这条，日志打 `pepper_generated` 提醒生产改用 secret）。

### 1.3 错误码表

| HTTP | code | msg 示例 | 来源 |
|---|---|---|---|
| 200 | 200 | `ok` | 【原版】成功码 |
| 400 | 400 | `参数不合法：username` | 【我方】 |
| 401 | 401 | `账号或密码不正确` / `token 无效或已过期` | 【我方】（原版错误码表未取证） |
| 403 | 403 | `无权访问该资源` | 【我方】 |
| 404 | 404 | `资源不存在` | 【我方】 |
| 409 | 409 | `用户名已被占用` | 【我方】 |
| 413 | 413 | `请求体过大` | 【我方】 |
| 415 | 415 | `仅接受 application/json` | 【我方】 |
| 429 | 429 | `请求过于频繁`（带 `Retry-After`） | 【我方】 |
| 500 | 500 | `服务端内部错误` | 【我方】 |

> **原版未取证**：200 之外的业务错误码（含 `logoutOnAccountInvalid` 的触发码、验证码错误码）。本文不编造原版数值，客户端按「非 200 即失败 + 展示 `msg`」处理。

## 2. 账号与会话（【我方新增】，原版无密码登录）

> 原版的 `phone / phoneOauth / wechat / qq / honor / device` 六种 `loginType` 本轮**只做入口占位 + DI**（D-029 手机号验证码、D-030 一键/荣耀、D-031 微信；`qq` 入口文案未取证不占位）。本节是我方自建账号体系，**不是原版 1:1**。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v2/auth/register` | `{username, password, display_name?}` → 201 `{user, token, expires_at}` |
| POST | `/v2/auth/login` | `{username, password}` → 200 同上；失败 401、超限 429 |
| POST | `/v2/auth/logout` | 需 token；撤销当前会话 |
| POST | `/v2/auth/logout-all` | 撤销该用户全部会话 |
| POST | `/v2/auth/password` | `{current_password, new_password}`，成功后撤销其它会话 |
| GET | `/v2/auth/me` | 当前用户 |
| PATCH | `/v2/auth/me` | `{display_name}` |
| GET | `/v2/auth/sessions` | 会话列表（不含 token） |
| DELETE | `/v2/auth/sessions/{id}` | 撤销指定会话 |

```json
{
  "user": { "id": "user_9f0c…", "uid": 9, "username": "laoliu", "display_name": "老刘",
            "phone": null, "avatar": null, "created_at": "…", "last_login_at": "…" },
  "token": "3f9c…", "expires_at": "2026-12-07T06:00:00.000Z"
}
```

- `username` 3–32 `[A-Za-z0-9._-]`，大小写不敏感唯一；`password` 8–128；哈希格式 `scrypt$N=32768,r=8,p=1$<salt_b64>$<hash_b64>`（【我方】）。
- `user.phone/avatar` 为**原版 `AccountModel` 同名字段预留**（本轮恒 `null`，绑定能力属 D-029/031）。
- `user.uid` 是服务端自增**数字** id（【我方】，见 §3.1 的 `userId:Long` 类型对齐）；`user.id` 是 `user_…` 文本主键，只用于鉴权与内部引用。
- `token` 只在签发响应出现一次；默认 30 天（`LC_TOKEN_TTL_DAYS`），同用户最多 10 会话（超出自动撤销最旧）。
- 签发会话的端点：`register`（201）、`login`（200）。两者都返回同一个 `{user, token, expires_at}` 结构。

## 3. 收藏点（【原版】路径与字段）

### 3.1 对象（原版 `CollectPointModel` 13 字段，照抄）

```json
{
  "id": 1723456789012,
  "userId": 340992,
  "name": "西湖某宅",
  "address": "浙江省杭州市西湖区…",
  "latitude": 30.2501,
  "longitude": 120.1502,
  "des": "坐向：巽山乾向",
  "images": "[\"https://…\"]",
  "icon": "default",
  "style": "0",
  "overlay": 1,
  "dbId": 12,
  "createTime": "2026-11-07T06:00:00.000Z"
}
```

- 类型照原版：`id:Long`、`userId:Long`、`latitude/longitude:double`、`overlay:int`、`dbId:int`、`createTime:Date`，其余 string。
- `userId` 是服务端数字 `uid`（§2），由 token 决定，**客户端传什么都被忽略**。
- `images` 原版为字符串（格式未取证），我方原样透传、不解析。
- **【我方扩展，原版无此字段】**：`rev:int`（增量游标）、`deleted:bool`、`updatedAt`。只在 v2 响应里附带，客户端可忽略；**不作为原版字段**。

### 3.2 端点（路径与原版一致）

| 方法 | 路径 | 入参 |
|---|---|---|
| GET | `/v2/userCollectPoint/list` | `pageNum:int`（原版）、`overlay:bool?`（原版）；**我方扩展**：`since:int?`、`limit:int?`（增量） |
| POST | `/v2/userCollectPoint/save` | `CollectPointModel`（无 `id` 即新建；有 `id` 且属当前用户即更新） |
| DELETE | `/v2/userCollectPoint/delete?id=<long>` | `id`（query，原版） |

响应：

- `list` → `{"code":200,"msg":"ok","data":{"items":[…],"count":2,"cursor":42}}`（`cursor` = 本次最后一条 `rev`，可直接回填 `since`）。
- `save`（新建）→ HTTP 201 `{"data":{"point":{…}}}`；`save`（更新）→ HTTP 200 `{"data":{"point":{…}}}`。
- `delete` → `{"data":{"deleted":true,"id":12}}`。

同步与冲突（【我方新增】行为，原版同步时机未取证）：

- **增量**：`since=<cursor>` 只返回 `rev > since` 且未删除的点；`since` 与 `pageNum` 互斥使用（`since>0` 时忽略 `pageNum`）。
- **冲突**：`save` 带 `id` 且带 `rev` 时按乐观锁校验，`rev` 与库中不一致返回 409 `{code:409,msg:"版本冲突",data:{server:{…}}}`，`data.server` 是服务端当前快照，由客户端决定合并或丢弃；**不带 `rev` 则最后写入胜出**。
- **软删除不复活**：对已删除（或他人）的 `id` 调 `save`/`delete` 一律 404，不泄露存在性，也不复活记录。
- `pageNum` 默认 1，`limit` 默认 100 / 最大 200；`overlay=1|0` 过滤（不传=全部）。
- `userId` 服务端按 token 覆盖，**忽略客户端传值**（防越权）；`dbId` 原样存。
- 原版还有 `AddCollectParameter`（含 `addr/country/province/city/district/street/adCode/altitude/sceneCode/notify/notifyConfig/token`）作为**另一条写入链路**，调用方未定位：本文只登记，不实现（【原版未取证】）。

### 3.3 新建幂等（【我方新增】，与 `/v2/feedback/add` 同一套）

`POST /v2/userCollectPoint/save` 的**新建分支**（不带 `id`）可选读请求头 **`Idempotency-Key`**（≤128 字符，客户端用每行稳定的 `client_key`）：

- 首次：HTTP **201**，`data.deduplicated=false`；
- 同 key 再次（含崩在「服务端已建、客户端未写回 `remote_id`」的缝里重推）：HTTP **200**，`data.point` 是**第一次建的那一行**，`data.deduplicated=true`，**库里不新增行**；
- **同 key 但 body 不同**：仍返回第一次那一行（200），**不静默按新内容改写**——宁可让客户端据 `data.point` 收敛，也不静默丢内容；
- 不带 key：两次提交就是两行（不做隐式去重）。
- 唯一索引 `(user_id, idem_key)` 是最终仲裁，两个重试并发也只会建一行。
- **`Idempotency-Key` 只作用于新建分支**：客户端每行复用同一个 key，更新（带 `id`）时即使带该 key 也**照常更新**，不会被当成重放而丢掉编辑。
- 软删除的行**保留** `idem_key`：对已删行用同 key 重推仍返回该行（`deleted:true`），不复活，与 §3.2「删除不复活」一致。

## 4. 投诉反馈（【原版】`type:int`）

| 方法 | 路径 | 入参 |
|---|---|---|
| POST | `/v2/feedback/add` | `AddFeedbackReqModel` |
| GET | `/v2/feedback/list` | 无（分页由服务端默认 20 条，【我方】） |

```json
{
  "attachments": "",
  "contactWay": "wechat_abc",
  "orderNum": "",
  "reasons": "闪退",
  "remark": "",
  "type": 2
}
```

- 字段名与顺序照原版：`attachments, contactWay, orderNum, reasons, remark, type`。
- **`type` 是 int，只落已取证值 `2`**（`FeedbackViewModel.P`）；其余取值域【原版未取证】，**不得写 `problem|order` 当原版值**，也不得自造新值。传其它值 → 400（`type 仅支持 2（原版其余取值域未取证）`）。
- `remark` 在原版那条链路恒为 `""`（描述走 `reasons`）；我方**原样接收**，不强制改写，但默认空串。
- `attachments` 本轮只接受 `null`/空串：图片上传走 DI（`getUploadOssPreSignUrl` 属原版 OSS 链路，未取证）。
- 限流：每用户 5 次/小时（【我方】）。
- 响应：`add` → HTTP 201 `{"data":{"feedback":{…},"deduplicated":false}}`；`list` → `{"data":{"items":[…],"count":3}}`（本人最新 20 条，`limit` 可调 1–100）。
- **幂等**：可选请求头 `Idempotency-Key`（≤128 字符）。同用户同 key 重复提交返回首条记录（HTTP 200、`deduplicated:true`），不重复入库。

## 5. 账户设置相关接口（【原版】，本轮不实现）

以下为原版 Map 传参真实 key（`account-reverse.md` §5），**本轮只做页面展示 + DI 标注**，不开放接口：

| 原版接口 | Map key |
|---|---|
| `bindPhone` | `phone`、`code` |
| `changePhone` | `phone`、`code` |
| `verifyCurrentPhone` | `phone`、`code` |
| `updateNickname` | `nickname` |
| `bindAccount`（绑第三方） | `phone`、`smsCode`、`wxOpenid`、`wxUnionid`、`loginType="wechat"` |

未取证：`updateAvatar` 的 Map key（走 `uploadImage`/`updateCover` 链路）。

## 6. 安全基线（实现必须逐条落地）

1. API 只绑 `127.0.0.1`：compose 只发布 `127.0.0.1:8787`，容器内 `HOST=0.0.0.0` 仅用于端口映射。
2. 密码：`scrypt`（`node:crypto`，`N=32768,r=8,p=1`，16B salt，64B 输出）+ `timingSafeEqual`；**禁止**明文、`md5/sha1/sha256(pwd)`、可逆加密。
3. token：32 字节随机 `base64url`；DB 只存 `sha256(token+pepper)`；`LC_TOKEN_PEPPER` 走环境变量或仓库外 secrets，不入库。
4. 限流（内存滑窗，单进程，重启清零；`LC_RATE_LIMIT=0` 可关，仅本地调试用）：登录 每 IP 60/分 + 每 IP 600/时 + **每用户 5/分（连续输错 6 次即 429）**；注册 每 IP 100/时；改密 每用户 5/时；反馈 每用户 5/时。超限 429 并带 `Retry-After` 秒数。IP 级配额留有自检反复跑的余量，真正的输入爆破控制在「每用户 5/分」。
5. CORS：默认**不发** `Access-Control-Allow-Origin`；`LC_CORS_ORIGIN` 为逗号分隔白名单，按请求 `Origin` **精确匹配**才回填（并带 `Vary: Origin`），不用 `*`。
6. 日志：`Authorization`/`token`/`password` 永不打印；`/v2/auth/*` 不打印 body；统一 JSON 行 `{event:"http",rid,method,path,status,ms,user}`，**只记 pathname、不记 query**（避免 token 落日志）。
7. 请求体上限：认证类 16 KB、业务类 256 KB；超限 413。
8. 响应不回堆栈；`X-Request-Id` 回显便于对日志；成功信封 `code` 恒 200（HTTP 可 201），失败 `code` = HTTP 状态。

## 7. 数据模型与迁移（v1 → v2）

```sql
users(uid INTEGER PK AUTOINCREMENT, id TEXT UNIQUE, username TEXT, username_lc TEXT UNIQUE,
      display_name TEXT, phone TEXT, avatar TEXT, password_hash TEXT, status TEXT,
      created_at TEXT, last_login_at TEXT, failed_attempts INT, locked_until TEXT);
sessions(id TEXT PK, user_id TEXT, token_hash TEXT UNIQUE, device TEXT, created_at TEXT,
         last_used_at TEXT, expires_at TEXT, revoked INT);
favorites(id INTEGER PK AUTOINCREMENT, user_id TEXT, name TEXT, address TEXT, latitude REAL,
          longitude REAL, des TEXT, images TEXT, icon TEXT, style TEXT, overlay INT, db_id INT,
          create_time TEXT, rev INTEGER, deleted INT, updated_at TEXT);  -- 13 原版字段 + 4 我方扩展
feedback(id TEXT PK, user_id TEXT, attachments TEXT, contact_way TEXT, order_num TEXT,
         reasons TEXT, remark TEXT, type INT, status TEXT, created_at TEXT, idem_key TEXT);
cases/settings/shares/changes(...)  -- v1 原样保留，另加 user_id 列供显式认领
```

- `schema_migrations` 幂等迁移，已实现并在启动时执行：`001_init` → `002_users_sessions` → `003_favorites` → `004_feedback` → `005_legacy_user_id`（`ALTER TABLE cases/settings/shares ADD COLUMN user_id`）→ `006_favorites_idem`（`favorites` 加 `idem_key` + 唯一索引，见 §3.3）。跑过的库只记录不重跑，**v1 旧卷可直接升级**（实测：旧卷 `cases=2` 原样保留）。
- 收藏点用自身 `rev` 做增量，**不写 `changes` 变更日志**（`changes` 仍是 v1 案例库的游标表；v2 未开放 `/v2/cases`，案例库数据只保留待客户端接入时再排期）。
- v1 的 `cases/settings/shares` 保留，`user_id` 初始为 `NULL`，**不自动归属**（`owner_id` 不是身份凭证）；用一次性脚本显式认领：
  `docker compose exec api node --no-warnings scripts/claim-owner.mjs <owner_id> <username>` → 打印 `CLAIM: ok … cases/settings/shares` 条数。
- `/v1/*` 全量 `410 deprecated`（`{code:410,msg:"deprecated",data:{replacement:"/v2",doc:"backend/api.md"}}`，`/v1/health` → `/health`）；`/health` 与 `/` 不变。
- 切换：停服 → 备份 `lc.sqlite` → `docker compose up -d --build`（启动跑迁移）→ 容器内 `selfcheck.mjs` → 回滚 = 还原备份 + 上一镜像。
- 环境变量：`LC_TOKEN_TTL_DAYS=30`、`LC_TOKEN_PEPPER`（或 secrets 文件）、`LC_CORS_ORIGIN`（逗号白名单）、`LC_RATE_LIMIT=1`、`PORT`、`DB_PATH`。

## 8. 非目标与 DI

- 微信/QQ/荣耀/一键登录：只占位（D-029/030/031），不实现 OAuth。
- 图片附件上传（`attachments` 只收空）。
- 公网部署/TLS/多租户。
- `/v2/cases` 案例库接口（v1 数据保留，客户端接入后另排期）。
- 服务端下发罗盘常量（仍以 `app/mechanics.json` 为唯一来源）。
- 原版错误码表、`logoutOnAccountInvalid` 触发码、反馈 `type` 其余值域、收藏同步时机、`updateAvatar` key：**原版未取证**，不编造。

## 9. 运行与自检（容器内）

```powershell
cd <repo>\backend
docker compose up -d --build                 # 一键起；启动时自动跑 001–005 迁移
docker compose ps                            # 期望 Up (healthy)
curl.exe -s http://127.0.0.1:8787/health     # {"status":"ok",...,"schema":"006_favorites_idem"}
docker compose exec -T api node --no-warnings scripts/selfcheck.mjs   # 期望 SELFCHECK: PASS (80 checks)
```

`selfcheck.mjs` 覆盖（容器内跑，不是宿主机）：

| 组 | 用例 |
|---|---|
| 账号链路 | 注册（含重名 409、短密码/短用户名 400）→ 登录（正确/错误 401）→ `me` → 改昵称 → 会话列表/撤销 → 改密（错当前密码 401、旧密码失效、新密码可登）→ 登出 → `logout-all` |
| 会话 | 无 token/伪造 token 401；**过期**：脚本直接在容器内 SQLite 把该会话 `expires_at` 改到过去 → `/me` 401 |
| 收藏点 | 新建（客户端 `userId` 被覆盖）→ 列表 → `since=cursor` 增量 0 → 乐观锁 409 + 服务端快照 → 正常更新 → 软删除 → 删除后不可见/不可复活（404） |
| 越权 | B 的列表看不到 A 的收藏点与反馈；B 删/改 A 的收藏点 id → 404（拒绝且不泄露存在性） |
| 反馈 | `type=2` 通过、其它值 400、非空 `attachments` 400、`Idempotency-Key` 重复提交同一条 |
| 协议边 | `/v1/*` 410；非 JSON 415；超 256 KB 413；未知 Origin 不回 `Access-Control-Allow-Origin`；第 6 次错密码 429 + `Retry-After` |

**持久化实测**（`docker compose restart` 与 `down`+`up` 两种）：同一收藏点重启前后 `id=5 / name=持久化校验·西湖 / rev=21` 完全一致，重启前签发的 token 仍可用（pepper 落 `/data/.token-pepper`，`0600`）——不是「应该还在」，是同一行对比。
