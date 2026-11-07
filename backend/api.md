# LC Backend API v2（`/v2`）

> 状态：**契约（v2，未实现）**。M4 起由本文件取代 v1；v1 原文归档在 [`api-v1.md`](api-v1.md)（只读），`/v1/*` 统一返回 `410 deprecated`。
> 对齐依据：LC逆向 `docs/fidelity/m4/account-reverse.md`（`16f3018`）——基址/信封/认证头/登录/收藏/反馈全部按原版取证结果落；标注规则见 §0.2。
> 安全红线：全部依赖在 Docker 内（Node 22 + `node:sqlite`）、只绑 `127.0.0.1`、密码 scrypt、token 不透明随机、日志脱敏。

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

- `code` 与 HTTP 状态一致（成功固定 200，与原版一致）；`msg` 为人类可读中文；`data` 为资源包。
- 兼容原版 `ApiResult` 的解析方式：客户端只看 `code==200`，不看 HTTP 状态。

### 1.2 认证（【原版】头名 + 【我方】扩展）

- 受保护请求带 **`token: <opaque>`**。这是**我方 32 字节随机 token**（`base64url`，43 字符），非 JWT——与原版「不透明串 + 有效期」一致（总监 2026-11-07 定）。
- 服务端只存 `sha256(token + LC_TOKEN_PEPPER)`；`Authorization: Bearer <token>` 作为**我方扩展**同时接受（便于 curl/调试），两者等价。
- 服务端不存明文 token；DB 泄露不能直接冒用会话。

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
  "user": { "id": "user_9f0c…", "username": "laoliu", "display_name": "老刘",
            "phone": null, "avatar": null, "created_at": "…", "last_login_at": "…" },
  "token": "3f9c…", "expires_at": "2026-12-07T06:00:00.000Z"
}
```

- `username` 3–32 `[A-Za-z0-9._-]`，大小写不敏感唯一；`password` 8–128；哈希格式 `scrypt$N=32768,r=8,p=1$<salt_b64>$<hash_b64>`（【我方】）。
- `user.phone/avatar` 为**原版 `AccountModel` 同名字段预留**（本轮恒 `null`，绑定能力属 D-029/031）。
- `token` 只在签发响应出现一次；默认 30 天（`LC_TOKEN_TTL_DAYS`），同用户最多 10 会话。

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
- `images` 原版为字符串（格式未取证），我方原样透传、不解析。
- **【我方扩展，原版无此字段】**：`rev:int`（增量游标）、`deleted:bool`、`updatedAt`。只在 v2 响应里附带，客户端可忽略；**不作为原版字段**。

### 3.2 端点（路径与原版一致）

| 方法 | 路径 | 入参 |
|---|---|---|
| GET | `/v2/userCollectPoint/list` | `pageNum:int`（原版）、`overlay:bool?`（原版）；**我方扩展**：`since:int?`、`limit:int?`（增量） |
| POST | `/v2/userCollectPoint/save` | `CollectPointModel`（无 `id` 即新建；有 `id` 且属当前用户即更新） |
| DELETE | `/v2/userCollectPoint/delete?id=<long>` | `id`（query，原版） |

- `userId` 服务端按 token 覆盖，**忽略客户端传值**（防越权）；`dbId` 原样存。
- 归属他人 `id` → 404（不泄露存在性）。
- 原版还有 `AddCollectParameter`（含 `addr/country/province/city/district/street/adCode/altitude/sceneCode/notify/notifyConfig/token`）作为**另一条写入链路**，调用方未定位：本文只登记，不实现（【原版未取证】）。
- 原版同步时机（登录后是否全量拉取/本地合并）未取证：我方按「登录后拉一次列表 + 后续 `since` 增量」实现（【我方新增】行为）。

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
- **`type` 是 int，只落已取证值 `2`**（`FeedbackViewModel.P`）；其余取值域【原版未取证】，**不得写 `problem|order` 当原版值**，也不得自造新值。
- `remark` 在原版那条链路恒为 `""`（描述走 `reasons`）；我方**原样接收**，不强制改写，但默认空串。
- `attachments` 本轮只接受 `null`/空串：图片上传走 DI（`getUploadOssPreSignUrl` 属原版 OSS 链路，未取证）。
- 限流：每用户 5 次/小时（【我方】）。

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
4. 限流（内存滑窗）：登录 每 IP 10/分 + 每用户 5/分 + 每 IP 50/时；注册 3/时/IP；改密 3/时/用户；反馈 5/时/用户；超限 429 + `Retry-After`。
5. CORS：默认不发 `Access-Control-Allow-Origin`；`LC_CORS_ORIGIN` 逗号白名单精确匹配，不用 `*`。
6. 日志：`Authorization`/`token`/`password` 永不打印；`/v2/auth/*` 永久屏蔽 body；统一 `logSafe(event,{user_id,path,status,ms})`。
7. 请求体上限：鉴权 16 KB、业务 256 KB、`sync/push` 1 MB；超限 413。
8. 响应不回堆栈；`code` 与 HTTP 状态一致；`X-Request-Id` 回显便于对日志。

## 7. 数据模型与迁移（v1 → v2）

```sql
users(id TEXT PK, username TEXT, username_lc TEXT UNIQUE, display_name TEXT, phone TEXT,
      avatar TEXT, password_hash TEXT, status TEXT, created_at TEXT, last_login_at TEXT,
      failed_attempts INT, locked_until TEXT);
sessions(id TEXT PK, user_id TEXT, token_hash TEXT UNIQUE, device TEXT, created_at TEXT,
         last_used_at TEXT, expires_at TEXT, revoked INT);
favorites(id INTEGER PK, user_id TEXT, name TEXT, address TEXT, latitude REAL, longitude REAL,
          des TEXT, images TEXT, icon TEXT, style TEXT, overlay INT, db_id INT,
          create_time TEXT, rev INTEGER, deleted INT, updated_at TEXT);   -- 13 原版字段 + 4 我方扩展
feedback(id TEXT PK, user_id TEXT, attachments TEXT, contact_way TEXT, order_num TEXT,
         reasons TEXT, remark TEXT, type INT, status TEXT, created_at TEXT, idem_key TEXT);
changes(...)  -- 沿用 v1，实体增加 favorite
```

- `schema_migrations` 幂等迁移：`001_init` → `002_users_sessions` → `003_favorites` → `004_feedback`。
- v1 的 `cases/settings/shares` 保留（改为 `user_id` 归属，v1 行 `user_id NULL` 不自动归属；一次性脚本 `claim-owner.mjs` 显式认领）。
- `/v1/*` 全量 `410 deprecated`（`{code:410,msg:"deprecated",data:{replacement:"/v2/…"}}`）；`/health` 不变。
- 切换：停服 → 备份 `lc.sqlite` → `docker compose up -d --build`（启动跑迁移）→ `selfcheck.mjs`（注册/登录/401/429/收藏/反馈/登出）→ 回滚 = 还原备份 + 上一镜像。
- 环境变量：`LC_TOKEN_TTL_DAYS=30`、`LC_TOKEN_PEPPER`、`LC_CORS_ORIGIN`、`LC_LOG_BODY=0`、`LC_RATE_LIMIT=1`。

## 8. 非目标与 DI

- 微信/QQ/荣耀/一键登录：只占位（D-029/030/031），不实现 OAuth。
- 图片附件上传（`attachments` 只收空）。
- 公网部署/TLS/多租户。
- 服务端下发罗盘常量（仍以 `app/mechanics.json` 为唯一来源）。
- 原版错误码表、`logoutOnAccountInvalid` 触发码、反馈 `type` 其余值域、收藏同步时机、`updateAvatar` key：**原版未取证**，不编造。
