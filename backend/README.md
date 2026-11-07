# LC backend（山水罗盘本地服务端，v2）

自建账号体系 + 会话 + 收藏点云同步 + 投诉反馈的本地服务。**全部依赖在 Docker 内**，宿主机只留源码；单 SQLite 文件即全部状态，无云依赖也能演示。

- 技术栈：Node 22（`node:http` + `node:sqlite` + `node:crypto`），**零 npm 依赖**，镜像 `node:22-bookworm-slim`，非 root 运行
- 端口：`127.0.0.1:8787`（compose 只绑定回环）
- 数据：Docker volume `lc-backend_lc-data` → `/data/lc.sqlite`（重启/`down`+`up` 后仍在，已实测同一行对比）
- 契约：[`api.md`](api.md)（**v2 定稿**：账号/会话/收藏点/反馈/安全基线）；v1 归档在 [`api-v1.md`](api-v1.md)（只读，`/v1/*` 已 410）

## 一键起

```powershell
cd E:\APPDetector\fix-p0\backend
docker compose up -d --build      # 启动时自动跑迁移 001–005
docker compose ps                 # 期望 Up (healthy)
curl.exe -s http://127.0.0.1:8787/health
```

契约自检（在容器内跑，不占宿主机 Node）：

```powershell
docker compose exec -T api node --no-warnings scripts/selfcheck.mjs   # 期望 SELFCHECK: PASS (80 checks)
```

停止 / 清空数据：

```powershell
docker compose down       # 保留 volume
docker compose down -v    # 删库，下次启动重建空库
```

认领 v1 遗留数据（`owner_id` 不是身份凭证，必须显式认领）：

```powershell
docker compose exec api node --no-warnings scripts/claim-owner.mjs <owner_id> <username>
```

## 目录

```
backend/
  api.md                 # API v2 契约（定稿；账号/会话/收藏点/反馈/安全）
  api-v1.md              # API v1 契约（归档只读）
  docker-compose.yml     # 一键服务，含 healthcheck、回环绑定
  Dockerfile             # node:22-bookworm-slim，非 root 运行
  package.json           # 无 dependencies，仅脚本
  src/server.js          # 路由 / 校验 / 限流 / 信封 / 日志脱敏
  src/db.js              # 迁移与 SQLite 存储（用户/会话/收藏点/反馈/遗留案例库）
  src/password.mjs       # scrypt N=32768,r=8,p=1 + timingSafeEqual
  src/token.mjs          # 不透明随机 token、sha256(token+pepper)、pepper 来源
  src/ratelimit.mjs      # 内存滑动窗口（429 + Retry-After）
  scripts/selfcheck.mjs  # 账号五链路 + 越权 + 过期 + 限流，exit 0 = PASS
  scripts/claim-owner.mjs# v1 遗留行显式认领
```

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | `8787` | 监听端口 |
| `HOST` | `0.0.0.0`（容器内） | 容器内监听地址；对外只由 compose 映射到 `127.0.0.1` |
| `DB_PATH` | `/data/lc.sqlite` | SQLite 文件路径 |
| `LC_TOKEN_PEPPER` | 空 | token 哈希 pepper（≥16 字符）。空则用 `/run/secrets/lc_token_pepper`，再退到自动生成 `/data/.token-pepper`（0600） |
| `LC_TOKEN_PEPPER_FILE` | `/run/secrets/lc_token_pepper` | Docker secret 路径 |
| `LC_TOKEN_TTL_DAYS` | `30` | 会话有效期（1–365 天） |
| `LC_CORS_ORIGIN` | 空 | 逗号分隔白名单，精确匹配才回 `Access-Control-Allow-Origin`；空=不发 |
| `LC_RATE_LIMIT` | `1` | `0` 关闭限流（仅本地调试） |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:8787` | 预留：分享链接前缀（v2 未开放分享端点） |

## 契约摘要

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/health` | 存活 + DB + 迁移版本 + 各表计数 |
| POST | `/v2/auth/register` | 注册（用户名 3–32、密码 8–128）→ 201 `{user,token,expires_at}` |
| POST | `/v2/auth/login` | 登录；失败 401、超限 429 |
| POST | `/v2/auth/logout` / `logout-all` | 撤销当前 / 全部会话 |
| POST | `/v2/auth/password` | 改密（撤销其它会话） |
| GET/PATCH | `/v2/auth/me` | 当前用户 / 改昵称 |
| GET | `/v2/auth/sessions` | 会话列表（不含 token） |
| DELETE | `/v2/auth/sessions/{id}` | 撤销指定会话 |
| GET | `/v2/userCollectPoint/list` | 收藏点列表（`since` 增量、`overlay` 过滤） |
| POST | `/v2/userCollectPoint/save` | 新建 / 更新（带 `rev` 走乐观锁，409 带服务端快照） |
| DELETE | `/v2/userCollectPoint/delete?id=` | 软删除 |
| POST | `/v2/feedback/add` | 反馈提交（`type:int`，只落 2；`Idempotency-Key` 幂等） |
| GET | `/v2/feedback/list` | 本人反馈 |
| * | `/v1/*` | `410 deprecated` → `/v2` |

完整请求/响应示例与字段标注（【原版】/【我方新增】/【原版未取证】）见 [`api.md`](api.md)。

## 边界

- 只绑 `127.0.0.1`，仅供本机/局域网演示；公网部署前必须加 TLS、反向代理与更严格的限流。
- 密码只存 scrypt 哈希；token 只存 `sha256(token+pepper)`；日志只记 pathname，不记 query/body。
- 服务端不维护罗盘数值：唯一数值源仍是 [`app/mechanics.json`](../app/mechanics.json)。
- 不代理天地图请求、不存图片二进制（见 `api.md` §8）。
