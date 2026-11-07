# M4 · 部署与备份说明（Docker volume）

> 定稿日期：2026-11-07　｜　基线：`develop` `71ff5c8`　｜　成文：LC审成文
> 适用：本机自用环境（单机 Docker，Node 22 镜像 + SQLite volume）。宿主机不装 node/npm/sqlite3，所有命令走 `docker compose`。

## 1 一键部署

```bash
cd backend
docker compose up -d --build
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8787/health   # 期望 200
```

- 服务名 `api`，容器名 `lc-backend`，镜像 `lc-backend:local`。
- 端口映射 **`127.0.0.1:8787:8787`**：宿主机侧只绑回环，同一局域网其他机器访问不到。
- 数据卷 **`lc-data` → `/data`**（compose 声明 `volumes: lc-data:`，永续命名卷）。
- 健康检查：容器内 `fetch('/health')`，10s 间隔、3s 超时、5 次重试（`docker-compose.yml` healthcheck）。
- 重启策略 `unless-stopped`；`init: true` 让 Node 收到信号能优雅退出。

## 2 卷里有什么（这两样必须一起备份）

| 路径（卷内） | 内容 | 能不能丢 |
|---|---|---|
| `/data/lc.sqlite` | 全部业务数据：`users / sessions / favorites / feedback / meta / changes` 等表 | 丢 = 账号、收藏、反馈全没 |
| `/data/lc.sqlite-wal`、`/data/lc.sqlite-shm` | WAL 日志与共享内存（库是 **WAL 模式**，`db.js:617` `PRAGMA journal_mode = WAL`） | 运行中漏拷 = 最近事务丢失 |
| `/data/.token-pepper` | 会话 token 的 pepper（`token.mjs` 第三级兜底，0600，首启生成） | **丢 = 所有旧 token 立即失效，全员被踢下线** |

> **铁律：`lc.sqlite` 与 `.token-pepper` 必须同一次备份、同一次恢复。**
> 原因：库内 `sessions` 只存 `sha256(token + pepper)`（`token.mjs`）。pepper 换一个，旧哈希就再也算不出来 —— 数据都还在，但**每个人的登录态全部作废**。这是自建服务最容易漏的一步。

## 3 备份（先停容器，避免 WAL 漏拷）

```bash
cd backend
mkdir -p ../backups
docker compose stop api
docker compose cp api:/data/lc.sqlite        ../backups/lc.sqlite
docker compose cp api:/data/.token-pepper    ../backups/.token-pepper
# 若存在则一并拷：
docker compose cp api:/data/lc.sqlite-wal    ../backups/lc.sqlite-wal    2>/dev/null || true
docker compose cp api:/data/lc.sqlite-shm    ../backups/lc.sqlite-shm    2>/dev/null || true
docker compose start api
```

- 备份目录建议 `backend/../backups/`，**不要入库**（`.gitignore` 目前未挡 `*.sqlite`，见安全说明 §5-L2）。
- 想不停机，就至少把 `-wal` / `-shm` 一起拷；只拷 `.sqlite` 会丢最近事务。
- 建议命名带日期：`lc-$(date +%Y%m%d-%H%M).sqlite`，并与 `.token-pepper` 同目录同批次。

## 4 恢复（两样一起还原）

```bash
cd backend
docker compose stop api
docker compose cp ../backups/lc.sqlite        api:/data/lc.sqlite
docker compose cp ../backups/.token-pepper    api:/data/.token-pepper
docker compose start api
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8787/health   # 期望 200
```

**恢复后必须验证一件事**：用备份批次**之前**登录的账号带旧 token 调一次 `GET /v2/auth/me`。

```bash
curl -sS -H "token: <旧token>" http://127.0.0.1:8787/v2/auth/me
```

- 返回 `code=200` + 用户信息 → 库与 pepper 配对正确，会话可用。
- 返回 401 → pepper 不是同一份（或没恢复），旧会话全废。此时**不要**重新登录凑过验收，先查明 pepper 来源：若部署时用了 `LC_TOKEN_PEPPER` 环境变量或 `/run/secrets/lc_token_pepper`，则**备份还需包含那一份**（见 §6）。

## 5 卷/镜像的其它操作

| 目的 | 命令 | 注意 |
|---|---|---|
| 看日志 | `docker compose logs -f api` | 日志已脱敏：不含密码、不含明文 token（见安全说明） |
| 进容器自检 | `docker compose exec api node /app/scripts/selfcheck.mjs` | 全链路自检，勿在宿主跑 |
| 看库 | `docker compose exec api node -e "…"` | 宿主无 sqlite3，一律容器内查 |
| 重建（保留数据） | `docker compose up -d --build` | 卷不动，数据仍在 |
| 彻底重来 | `docker compose down -v` | **`-v` 会删 `lc-data` 卷，不可逆**；执行前先按 §3 备份 |

## 6 pepper 的三种来源（决定备份范围）

`token.mjs` 解析顺序：

1. `LC_TOKEN_PEPPER`（环境变量，≥16 字符）
2. `LC_TOKEN_PEPPER_FILE`（默认 `/run/secrets/lc_token_pepper`）
3. `/data/.token-pepper`（首启生成，0600，demo 兜底）

| 实际来源 | 备份必须包含 |
|---|---|
| 环境变量 | 记下该值（存到仓库外的 secrets 目录），备份库 + 该值 |
| `/run/secrets/…` | 备份库 + 该 secrets 文件 |
| `/data/.token-pepper`（默认） | 备份库 + 卷内 `.token-pepper`（§3 已含） |

> 换 pepper 等于全员登出。**知道当前用的是哪一级，比备份本身还重要。**

## 7 已知限制（部署侧）

1. **pepper 与库同卷**（默认兜底路径）：整卷泄露时「DB 泄露拿不到可用会话」不再成立——攻击者拿到库和 pepper 就能验证 token 哈希。M5 改为 `/run/secrets` 挂载。→ 安全说明 §5-L1。
2. **`.gitignore` 未挡** `.token-pepper` / `*.sqlite`：目前靠人工不 `git add`，属预防性缺口。→ 安全说明 §5-L2。
3. **限流窗口在内存**：容器重启后计数清零（`ratelimit.mjs` 注释已声明），这是取舍不是缺陷。
4. **单机单实例**：SQLite + 本地卷，不做多副本/高可用；与本项目「用户自用、非商用」定位一致（`D-001`）。

## 8 证据锚点

- `backend/docker-compose.yml`（端口映射、卷、healthcheck、secrets 注释）
- `backend/src/db.js:617-618`（WAL + busy_timeout）
- `backend/src/token.mjs`（pepper 三级来源、`sha256(token+pepper)`、32B 随机）
- `backend/scripts/selfcheck.mjs`（容器内自检）
- 关账实测：`docs/fidelity/m4-close-summary.md` §2 第 1 条（同一行 `id=18/rev=44` 重启后仍在、token 仍可用）
