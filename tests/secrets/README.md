# tests/secrets — 天地图 key 泄露闸门

仓库是 **PUBLIC**，任何 key 字面量进 git 都等于公开。这个门禁在提交/一键绿之前把「key 出现在已跟踪文件里」拦下来。

```powershell
# 单跑（默认从 E:\APPDetector\secrets\tdt.properties 读 key）
python tests\secrets\secret_gate.py

# 指定 key 文件 / 走环境变量
python tests\secrets\secret_gate.py --keys-file D:\ci\tdt.properties
$env:DSH_SECRET_TDT_SERVER_KEY='...'; $env:DSH_SECRET_TDT_WEB_KEY='...'; python tests\secrets\secret_gate.py

# fork PR 的 CI（拿不到 secrets）：只跑 hex 启发式
python tests\secrets\secret_gate.py --allow-missing

# 走一键门禁（secret 是第 2 个门禁）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1 -SkipDevice
```

输出：每个检查一行 `[PASS]` / `[FAIL]` / `[WARN]`，末行 `SECRET: PASS|FAIL`，退出码 0/1。
smoke 里日志落在 `checks\smoke-<时间戳>\secret.log`。

## 两条检查

| 规则 | 判据 | 失败含义 |
|---|---|---|
| `key` | 从 secrets 文件/环境变量读 `tdt.server.key`、`tdt.web.key`，对 `git ls-files` 每个文件做**字节级精确匹配** | key 进仓库了：删掉、**轮换 key**、查历史 |
| `hex` | `assets/*.html` 与 `*.java` 里出现 32 位十六进制串 → `[WARN]` | 疑似 key 明文；人判，不是红 |

- **只打 `文件:行: <哪个 key>`，绝不打 key 值。** 验证泄露只需要知道位置。
- `key` 命中是**不可豁免**的硬红：白名单只对 `hex` 告警生效。理由很直白——能豁免就等于允许泄露。
- `hex` 只看发页面和 Java 源码，因为别的文件里 32 位十六进制（内容哈希、uuid 变体）太容易撞；它兜的是「精确匹配没兜住的写法」：拼接、base64 解码后再出现、被塞进 JS 字符串。
- 读不到 key（文件不在、字段空）默认 **FAIL**：无法确认真实 key 是否泄露，按红处理。`--allow-missing` 只在 CI 上确实拿不到 secrets 时用，日志会 `[WARN]` 明示精确匹配被跳过。

## 白名单（怎么加）

`tests/secrets/allowlist.txt`，一行一条，**理由必填**：

```
<glob> <理由>
# glob：相对仓库根的 posix 路径，fnmatch 语义（`*` 会跨目录）
```

只压制 `hex` 告警，且是整个文件层面的（该文件里所有 32 位十六进制串都被视为已解释）。
适合：「第三方 SDK 固定 id、内容哈希，改不动也不该改」。不适合：任何真 key。
未命中的白名单条目会打 `[WARN] allowlist 未命中`（不判红），定期清。

## 已知限制

- 只扫 `git ls-files` 的**已跟踪**文件：刚建还没 `git add` 的文件不扫（但提交前 `git add` 后就会扫到）。
- 精确匹配只认 key 原样字节出现；换个编码（base64）、拆成两半拼接、从远程下发都能绕过——那些靠 `hex` 告警和代码评审，不靠这条。
- 门禁不联网、不校验 key 是否有效、不读 `.env` 之类隐式来源；来源只有上面写的三种。
- 构建产物（`checks\`、`app\android\build\`）不在扫描面：它们是 git-ignored，但**打出来的 APK 里含真 key**，别往外发（见 `docs/BUILD-SECRETS.md`）。
