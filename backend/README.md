# LC backend（山水罗盘本地服务端）

案例库 / 云同步 / 分享链接 / 设置同步的本地服务骨架。**全部依赖在 Docker 内**，宿主机只留源码；单 SQLite 文件即全部状态，无云依赖可演示。

- 技术栈：Node 22（`node:http` + `node:sqlite`），**零 npm 依赖**，镜像基于 `node:22-bookworm-slim`
- 端口：`127.0.0.1:8787`（compose 仅绑定回环，避免未鉴权接口暴露）
- 数据：Docker volume `lc-backend_lc-data` → `/data/lc.sqlite`
- 契约：[`api.md`](api.md)（v1，所有字段/错误码/同步语义以此为准）

## 一键起

```powershell
cd E:\APPDetector\output\backend
docker compose up -d --build
docker compose ps
```

健康检查：

```powershell
curl.exe -s http://127.0.0.1:8787/health
```

契约自检（在容器内跑，不占宿主机 Node）：

```powershell
docker compose exec api node --no-warnings scripts/selfcheck.mjs
```

停止 / 清空数据：

```powershell
docker compose down       # 保留 volume
docker compose down -v    # 删除案例与设置数据
```

## 目录

```
backend/
  api.md              # API v1 契约（字段、错误码、同步/分享语义）
  docker-compose.yml  # 一键服务，含 healthcheck
  Dockerfile          # node:22-bookworm-slim，非 root 运行
  package.json        # 无 dependencies，仅脚本
  src/server.js       # HTTP 路由 / 校验 / 分享页
  src/db.js           # SQLite schema、案例/设置/分享/变更日志
  scripts/selfcheck.mjs # 全端点自检，exit 0 = PASS
```

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `PORT` | `8787` | 监听端口 |
| `HOST` | `0.0.0.0`（容器内） | 容器内监听地址 |
| `DB_PATH` | `/data/lc.sqlite` | SQLite 文件路径 |
| `PUBLIC_BASE_URL` | `http://127.0.0.1:8787` | 分享链接前缀；局域网演示改成 `http://<pc-ip>:8787` |
| `CORS_ORIGIN` | `*` | CORS 允许来源 |

## 契约摘要

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/health` | 存活 + DB 状态 |
| GET/POST | `/v1/cases` | 案例列表（增量 `since=rev`）/ 新建 |
| GET/PUT/DELETE | `/v1/cases/{id}` | 读取 / 字段合并更新 / 软删除 |
| GET/PUT | `/v1/settings/{owner_id}` | 设置读取 / 整对象覆盖 |
| GET | `/v1/sync/pull?since=<seq>` | 变更日志增量拉取 |
| POST | `/v1/sync/push` | 离线批量上传（最后写入胜出） |
| POST | `/v1/shares` | 创建分享链接 |
| GET/DELETE | `/v1/shares/{token}` | 分享快照 / 撤销 |
| GET | `/s/{token}` | 手机可打开的 HTML 分享页 |

完整请求/响应示例见 [`api.md`](api.md)。

## 边界

- v1 **无鉴权**，仅本机演示；公网部署前必须加 token 并收紧 CORS/端口。
- 服务端不维护罗盘数值：唯一数值源仍是 [`app/mechanics.json`](../app/mechanics.json)。
- 不代理天地图请求、不存图片二进制（见 `api.md` §7）。
