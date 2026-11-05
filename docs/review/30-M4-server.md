# M4 服务端与工程基建审查报告

| 项 | 值 |
|---|---|
| 审查范围 | M4（26 文件：`backend/**`、`scripts/**`、`tools/mechanics/gen-mechanics.ps1`、`.gitignore`/`.gitattributes`、`app/android` 全部 gradle 与容器配置） |
| 基线 | `develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`） |
| 工作区 | `E:\APPDetector\review-base` |
| 审查人 | LC审服务端 |
| 前缀 | E |
| 覆盖 | 26/26 文件逐行读完，行数合计 2812，与 `00-MATRIX.md` M4 合计一致 |
| 运行时验证 | 本机 `node v22.17.0` 起真实服务（临时 DB）实测 6 项；Docker 构建 + 容器运行实测（非 root / 健康检查 / 端口 / 卷属主）；未跑 `docker compose up` 本体、未跑 Android 构建与真机 |

---

# 批次一：阻断 / 高 + 必查项（已落盘，早期信号）

## 0. 一句话结论

后端骨架的路由、事务包装、SQL 参数化、分享 token 做法是对的；**0 阻断、4 条高**，全部已证实，其中 3 条有实机复现。真正的风险集中在四处：**无鉴权 + CORS `*` 构成跨域写入面**、**案例 id 是全局主键但「已存在」检查按 owner 过滤，撞主键时未捕获异常直接 500**、**`/v1/sync/push` 逐条独立提交、非原子，重试会重复建案**、**请求体超限时先销毁 socket，契约声明的 413 永远送不到客户端**。两个门禁各有一次「假 PASS/可绕过」：`smoke.ps1` 的 numeric 门禁对「0 测试/全 skip」判 PASS；`gen-mechanics.ps1 -Check` 可被大小写改写或诱饵标记绕过（均实测）。

## 1. 必查五问（逐条直接回答）

### ① 案例 CRUD 的 SQL 拼接 / 参数化 —— 无注入，结论：无缺陷

`backend/src/db.js` 全部 DML 均使用 `prepare(...).run/get/all` + `?` 占位（`db.js:111`、`118-119`、`131-132`、`137-139`、`148-158`、`182-195`、`207-208`、`215`、`229-232`、`244-246`、`251`、`265`、`270-272`）。唯一的不带参数 SQL 是常量 `INSERT_REV`（`db.js:56`）：

```js
const INSERT_REV = 'UPDATE meta SET value = CAST(value AS INTEGER) + 1 WHERE key = \'rev\'';
```

`db.exec(` 只出现在 `db.js:99/102/105/292-295`，参数全是字面量（`BEGIN IMMEDIATE`/`COMMIT`/`ROLLBACK`/PRAGMA/SCHEMA）。`server.js` 中用户输入的 `owner_id`、`case.id`、`token`、`limit`、`since` 全部经校验后以参数传入，无字符串拼接进 SQL。**SQL 注入：已验证否定。**

### ② 分享链接的枚举 / 越权 —— 不可枚举、无越权，结论：无缺陷

- token：`db.js:241` `const token = randomBytes(16).toString('hex');` → 128 bit 随机，32 hex，与 `backend/api.md:220` 一致，枚举不可行。
- 创建：`server.js:420` → `db.js:238-240`：`createShare` 先 `getCase(caseId, ownerId)`，非本人案例返回 `null` → 404（`server.js:421`）。
- 撤销：`db.js:265` `UPDATE shares SET revoked = 1 WHERE token = ? AND owner_id = ?`，跨 owner 撤销无效。实测跨 owner 撤销返回 404。
- 打开：`server.js:438-440` 无鉴权是**设计**（分享的意义），返回的 `case` 在 `db.js:260` 用 `{ includeOwner: false }`，不含 `owner_id`；实测 `ownerLeak=false`。
- 过期/撤销/案例软删：`db.js:252-255` 全部返回 `null` → 404。实测撤销后 404、案例软删后 `POST /v1/shares` 404。

**越权：已验证否定。** 仅有两个非缺陷观察：分享 token 无速率限制（128 bit 下无实际枚举意义）；分享 token 会进请求日志（见 E-14）。

### ③ `smoke.ps1` 是否存在假 PASS —— 存在，两条

1. **numeric 门禁只认退出码，0 测试或全 skip 也 PASS**（E-06）。`run_all.py` 用 `result.wasSuccessful()` 判定，unittest 对 skipped/空套件返回 True；`smoke.ps1` 拿到 exit 0 即记 PASS，且 `$numericDetail` 只用于打印，不参与判定。触发：任一 `@unittest.skip` 覆盖全部用例，或 `test_*.py` 被发现不到（改名/移动）；实际显示 `numeric : PASS`（详细行会同时打印 `skipped=N`，门禁仍绿）。
2. **`gen-mechanics.ps1 -Check` 可被手改绕过**（E-05，见下条），smoke 门禁 3 因此同样可假 PASS。

另有一条**依赖 M1 结论**的验收失真：`build-desktop.ps1` 只证明 `--screenshot` 产出了 1100×780 的 PNG，该路径是否走真实 UI 由 `app/src/software.rs` 决定（归 LC审客户端），本模块不能据此声称「UI 正常」（H-02）。

### ④ `gen-mechanics.ps1 -Check` 的防手改是否可被绕过 —— 可绕过，两种方式均实测 PASS

**方式 A：PowerShell `-eq` 大小写不敏感。** 比对语句：

```powershell
254:    $same = ($cur.text -replace "`r`n", "`n") -eq ($item.region -replace "`r`n", "`n")
```

`-eq` 对字符串默认大小写不敏感（本机实测 `'ABC' -eq 'abc'` → `True`）。把生成区内 `RingDef { id: "TYPE_1", ...` 改成 `id: "type_1"`（只改大小写），实测 `-Check` 仍输出 `PASS  Rust generated region is up to date`、`exit=0`。

**方式 B：`Get-Region` 只取文件里第一对标记。**

```powershell
234:function Get-Region([string]$text, [string]$path) {
235:    $marker = $text.IndexOf($Open)
236:    $end = $text.IndexOf($Close)
237:    if ($marker -lt 0 -or $end -lt 0 -or $end -lt $marker) { throw "missing GENERATED markers in $path" }
```

在真实生成区**之前**插入一对标记（放在 `/* ... */` 块注释里，内容为原始正确区），再手改真实区（实测把 `interval: 45.0` 改为 `45.5`），`-Check` 比对的是诱饵区，实测 `PASS` + `exit=0`；`grep` 确认真实区已是 `45.5`。

**影响**：`-Check` 声称「Hand edits inside the markers are rejected」（`gen-mechanics.ps1:148/184`），实际两处都可静默通过；门禁 3 只能挡住「逐字节无差别之外的偶然漂移」，挡不住刻意手改。定级 E-05（中）。

### ⑤ 容器只绑 127.0.0.1 是否真的成立 —— 宿主机端口发布层成立；Node 进程层不成立

- **成立的部分**：`backend/docker-compose.yml:8-10`

```yaml
    ports:
      # Local demo only: never expose the unauthenticated API on a public interface.
      - "127.0.0.1:8787:8787"
```

宿主机只把 8787 发布到回环地址，容器内必须监听 `0.0.0.0` 才能被转发，这是正确做法。

- **不成立的部分**：`backend/src/server.js:10`

```js
const HOST = process.env.HOST || '0.0.0.0';
```

compose 未设置 `HOST`，所以进程监听全部网卡。按 `backend/package.json:11` 的 `"start": "node --no-warnings src/server.js"` 直接在宿主机运行时，即 LAN 全网卡暴露，与 `backend/api.md:5`「默认地址 `http://127.0.0.1:8787`（compose 只绑定回环地址）」、`backend/api.md:18`「绑定 127.0.0.1 仅本机可用」的表述不一致（`backend/README.md:55` 的环境变量表写的是 `0.0.0.0`，三处文档口径不统一）。→ E-08。

- **root 运行**：`backend/Dockerfile:14-16` 有 `RUN mkdir -p /data && chown node:node /data` 与 `USER node`，**后端容器非 root，成立**。`app/android/Dockerfile`（构建用）与 `app/Dockerfile`（M7）默认 root，见 E-22 / 未覆盖说明。

## 2. 正式缺陷清单（阻断 / 高）

> 本批 4 条，等级均为「高」，证据等级均为「已证实」。0 条阻断。

### E-01（高，已证实）无鉴权 + `Access-Control-Allow-Origin: *` → 任意网页可跨域读写本机案例库

**证据三件套**

`backend/src/server.js:13`

```js
const CORS_ORIGIN = process.env.CORS_ORIGIN || '*';
```

`backend/src/server.js:36`

```js
    'access-control-allow-origin': CORS_ORIGIN,
```

`backend/src/server.js:299-301`（写接口只校验 `owner_id` 的格式，不做任何身份校验）

```js
  if (pathname === '/v1/cases' && method === 'POST') {
    const body = await readJson(req);
    const ownerId = requireOwner(body.owner_id);
```

OPTIONS 预检也被放行（`server.js:254-258`）：

```js
  if (method === 'OPTIONS') {
    res.writeHead(204, baseHeaders());
    res.end();
    return;
  }
```

**触发条件**：用户在本机浏览器中打开任意第三方页面，页面脚本向 `http://127.0.0.1:8787` 发起 `GET/POST`。写入路径只需自造 `owner_id`（格式 `[A-Za-z0-9._:-]{1,128}`，`backend/api.md:17` 示例为 `dev_L2E0222531014833` 这种可猜的设备号）；读取路径需知道 `owner_id`，因 `ACAO: *` 响应可被跨域读取。

**预期 vs 实际**

- 预期：`backend/api.md:40` 说明 CORS `*` 仅供「WebView 内 H5 分享页/调试页直连」，`backend/api.md:18` 声明「仅本机可用」，语义上应只有本机 App/WebView 能调。
- 实际：CORS 不区分调用方，任何 origin 的页面都能通过预检并写入；`owner_id` 不是秘密，`/v1/cases?owner_id=` 一给就能整库读取。无鉴权是 v1 明示的非目标（`backend/api.md:256`），但文档没有说明「CORS `*` 把无鉴权暴露给了浏览器上下文」。

**定级说明**：不判阻断——宿主端口只发布到回环，攻击面限于本机浏览器上下文，且不能直接篡改已存在数据（只能按已知 `owner_id` 写）。但它是真实的跨域写入/读取面，判高。

**修复方向**（只给方向，不写代码）：默认 `CORS_ORIGIN` 收敛为 `null`（不下发 ACAO）或仅白名单 `app://`/`file://`/分享页实际 origin；写接口加本地 token（启动时生成、随 WebView 注入），`OPTIONS` 不再无条件 204。

---

### E-02（高，已证实）案例 `id` 是全局主键，但「已存在」检查按 owner 过滤 → 跨 owner 撞 id 返回 500，违背契约的 409

**证据三件套**

`backend/src/db.js:11-12`（主键是全局 `id`，不是 `(owner_id, id)`）

```sql
CREATE TABLE IF NOT EXISTS cases (
  id            TEXT PRIMARY KEY,
```

`backend/src/server.js:303-309`（只在**同一 owner** 下检查存在性）

```js
    if (body.id !== undefined) {
      if (typeof body.id !== 'string' || !CASE_ID_RE.test(body.id)) {
        fail(400, 'invalid_case_id', 'id must match [A-Za-z0-9_-]{6,64}');
      }
      if (store.getCase(body.id, ownerId, true)) fail(409, 'case_exists', `case ${body.id} already exists`);
    }
    const created = store.createCase(ownerId, fields, { id: body.id });
```

`backend/src/db.js:148-151`（撞主键时直接抛 SQLite 异常，`tx` 只回滚、不翻译）

```js
      this.db.prepare(
        `INSERT INTO cases (id, owner_id, title, sit_degree, facing_degree, compass_type, location, tags, notes, data, created_at, updated_at, rev, deleted)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`
      ).run(
```

**触发条件**：owner A 先用 id `X` 建案；owner B 用同一 id `X` 调 `POST /v1/cases`（或 `POST /v1/sync/push` 带该 id）。`getCase(X, B, true)` 返回 `null` → 走插入 → `UNIQUE constraint failed: cases.id` → 路由兜底 `500 internal_error`。

**预期 vs 实际**

- 预期：`backend/api.md:130`「可传 `id`……已存在则 409，避免覆盖」；`backend/api.md:35` 409 `case_exists`。
- 实际（实测）：

```
A create same id: 201
B create same id: 500 {"error":{"code":"internal_error","message":"internal server error"}}
server stderr: unhandled error: Error: UNIQUE constraint failed: cases.id
    at file:///E:/APPDetector/review-base/backend/src/db.js:151:9
```

**影响**：① 契约破裂（409 变 500）；② `sync.push` 整批在此处失败，离线创建的同 id 案例无法恢复；③ 同一用户换 `owner_id`（重装/换机）后，旧 id 全部无法再上传；④ 任何客户端可先用一个已知 id 占位，永久占用该 id 的全局命名空间（软删也不释放）。

**修复方向**：`cases` 主键改 `(owner_id, id)` 复合主键或在插入时 `INSERT OR IGNORE` + 冲突翻译为 409；`sync.push` 内同样处理。

---

### E-03（高，已证实）`/v1/sync/push` 非原子：逐条独立提交，后续条目校验失败时前序已落库；重试会重复建案

**证据三件套**

`backend/src/server.js:380-386`（循环内逐条校验，前序条目已各自提交）

```js
    for (let index = 0; index < cases.length; index += 1) {
      const item = cases[index];
      if (item === null || typeof item !== 'object' || Array.isArray(item)) {
        fail(400, 'invalid_field', `cases[${index}] must be an object`);
      }
      const fields = normalizeCaseFields(item, { requireTitle: true });
      const existing = item.id ? store.getCase(item.id, ownerId, true) : null;
```

`backend/src/server.js:387-399`（逐条写入，每条一个独立事务。L399 是 for 循环的收尾 `}`）

```js
      if (item.id !== undefined && (typeof item.id !== 'string' || !CASE_ID_RE.test(item.id))) {
        fail(400, 'invalid_case_id', `cases[${index}].id must match [A-Za-z0-9_-]{6,64}`);
      }
      if (existing && existing.deleted) {
        conflicts.push({ id: item.id, reason: 'deleted_on_server', server_rev: existing.rev });
        continue;
      }
      if (existing) {
        applied.push(store.updateCase(ownerId, item.id, fields));
      } else {
        applied.push(store.createCase(ownerId, fields, { id: item.id }));
      }
    }
```

`backend/src/db.js:144`（每条各自开事务，提交即不可回滚）

```js
    return this.tx(() => {
```

`backend/src/server.js:400-406`（settings 在**全部 cases 提交之后**才校验，同样能造成「cases 已落库、请求 400」）

```js
    let settings = null;
    if (body.settings !== undefined) {
      if (body.settings === null || typeof body.settings !== 'object' || Array.isArray(body.settings)) {
        fail(400, 'invalid_field', 'settings must be a JSON object');
      }
      settings = store.putSettings(ownerId, body.settings);
    }
```

**触发条件**：`POST /v1/sync/push` 的 `cases` 数组里第 N 条非法（如 `sit_degree: 999`），或 `settings` 非法；前 N-1 条已各自提交。

**预期 vs 实际**

- 预期：批量推送要么全成功、要么全不生效（或至少返回已应用清单让客户端可对账）；`backend/api.md:204` 承诺「v1 仅保证幂等」。
- 实际（实测：第 1 条合法无 id，第 2 条 `sit_degree: 999`）：

```
push.status: 400 {"error":{"code":"invalid_field","message":"sit_degree must be a number in [0, 360]"}}
list.after_partial: 200 count=2 titles=["A case","partial-ok-no-id"]
```

前一条已落库；客户端按 400 修正后**整批重试**，无 `id` 的条目会被再建一次（第二次重试后实测可到 3 条）。这直接破坏增量同步的幂等前提，属数据重复写入。

**修复方向**：整批包在一个 `BEGIN IMMEDIATE` 里（先全量校验/规范化，再统一写入），失败整体 ROLLBACK；或在文档中明确「部分成功」并返回 `applied` 供客户端去重，但客户端必须按 id 幂等。

---

### E-04（高，已证实）请求体超限时先 `req.destroy()`，契约声明的 413 永远送不到客户端

**证据三件套**

`backend/src/server.js:69-78`

```js
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new HttpError(413, 'payload_too_large', `request body exceeds ${limit} bytes`));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
```

`backend/src/server.js:476-479`（错误处理仍尝试写 JSON，但 socket 已被销毁）

```js
    if (error instanceof HttpError) {
      sendJson(res, error.status, {
        error: { code: error.code, message: error.message, ...(error.details ? { details: error.details } : {}) },
      });
```

**触发条件**：任意 `POST`/`PUT` 请求体 > 1 MB（`BODY_LIMIT`，`server.js:14`）。

**预期 vs 实际**

- 预期：`backend/api.md:36` 413 `payload_too_large`，客户端可识别为「请求过大」并截断/分批。
- 实际（实测 1.1 MB 请求体）：

```
1.1MB body: FETCH_ERROR TypeError: fetch failed
```

客户端收到连接被重置，没有任何 413 JSON；客户端无法区分「网络故障」和「请求超大」，重试同样失败，只能靠猜。

**修复方向**：超限时不要 `req.destroy()` 立刻销毁；改为 `req.pause()` + 移除 data 监听 + 用 `res.writeHead(413)` 正常回包并 `Connection: close`，或读完丢弃后回 413。

## 3. 必查项附带：中危发现

### E-05（中，已证实）`gen-mechanics.ps1 -Check` 可被大小写改写 / 诱饵标记绕过

见 §1 ④ 的完整复现与代码引用（`tools/mechanics/gen-mechanics.ps1:234-243`、`:254`、`:255-257`）。两种方式实测均 `exit=0`：

```
=== 2) case-only hand edit inside generated region (TYPE_1 -> type_1) ===
PASS  Rust generated region is up to date
PASS  Java generated region is up to date
PASS  all numeric values[] entries still appear in the end sources
exit=0

=== 3) decoy marker pair above the real region; real region hand-edited (45.0 -> 45.5) ===
PASS  Rust generated region is up to date
PASS  Java generated region is up to date
PASS  all numeric values[] entries still appear in the end sources
exit=0
```

另外 `:265-276` 的「19 项数值仍出现在最终源码」检查是**近乎恒真**的：所有值本来就由生成器写进生成区，`$all.IndexOf($lit)` 必然命中，无法证明公式常量与 `mechanics.json` 同源（公式仍用内联字面量，`:165-166` 自认）。这属于设计层的弱检查，不单列缺陷。

**修复方向**：`-eq` 改 `-ceq`；`Get-Region` 必须校验标记对唯一（`IndexOf($Open, $marker+1)` 应为 -1）；数值检查应白名单确认「只在生成区出现一次」。

### E-06（中，已证实）`smoke.ps1` numeric 门禁对 0 测试 / 全 skip 判 PASS

> 2026-11-05 总监裁定：本条转交叉引用母条 **F-01**（M5），M4 不重复计数；保留为门禁侧证据。

**证据三件套**

`scripts/smoke.ps1:105-108`（只看退出码；`tests=` 行仅用于打印）

```powershell
    $numericCode = Invoke-Gate 'numeric tests (tests\numeric\run_all.py)' $python.Exe $numericArgs $numericLog
    $detailLine = Select-String -LiteralPath $numericLog -Pattern '^tests=' | Select-Object -Last 1
    if ($detailLine) { $numericDetail = $detailLine.Line.Trim() }
```

`tests/numeric/run_all.py:25-33`（空套件 / 全 skip 时 `wasSuccessful()` 为 True → exit 0）

```python
    suite = unittest.TestLoader().discover(str(HERE), pattern="test_*.py", top_level_dir=str(HERE))
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    print("-" * 72)
    print(f"tests={result.testsRun} failures={len(result.failures)} errors={len(result.errors)} "
          f"skipped={len(result.skipped)} expected_failures={len(result.expectedFailures)} "
          f"unexpected_successes={len(result.unexpectedSuccesses)}")
    if result.wasSuccessful():
        print("RESULT: PASS")
        return 0
```

**触发条件**：`tests/numeric` 下所有 `test_*.py` 被改名、移走，或全部加 `@unittest.skip`；`discover` 得到 0 个用例 → `tests=0 ... skipped=0` → exit 0。

**预期 vs 实际**：预期 smoke 在测试数为 0 或全部 skipped 时红；实际汇总打印 `numeric : PASS  (tests=0 ...)`，`SMOKE: PASS`。这是最典型的假 PASS：测试资产消失而门禁全绿。

**修复方向**：门禁解析 `tests=`/`skipped=` 行，要求 `tests >= 下限` 且 `skipped == 0`（或 `skipped <= 约定上限`），否则判 FAIL。

### E-07（中，已证实）`build-desktop.ps1` 的「硬来源」正则只有英文，文档声称中文词也会判失败

**证据三件套**

`scripts/build-desktop.ps1:147-150`

```powershell
        $estimated = @($values | Where-Object {
            ([string]$_.source).Trim().Length -eq 0 -or
            ([string]$_.source) -match '(?i)estimate|estimated|guess|todo|unknown|unconfirmed|tbd'
        })
```

`scripts/README.md:18`（声称中文也算）

```
3. 校验 `app\mechanics.json`：恰好 19 项、每项有硬来源（出现 `estimate/估计/未确认/…` 即失败）、无重名
```

**触发条件**：`mechanics.json` 某个 `values[].source` 写成 `估计`/`推测`/`未确认`/`待定`——正则不含任何 CJK 词，判定为「有硬来源」，门禁 PASS。

**预期 vs 实际**：预期与 README 一致，中文软来源也红；实际只拦英文。当前 19 项 `values[].source` 已逐项扫过，没有中文软来源（`mechanics.json:1603` 的「未确认」在 `note` 字段，不参与该检查），所以**当前数据没漏**，但门禁能力与文档不符，下次手填中文就会漏过。

**跨模块同因**：`tests/numeric/tools/check_mechanics.py:19` 是仓库里的第二套「硬来源」词表：

```python
FORBIDDEN = ("估计", "估算", "estimate", "estimated", "unconfirmed", "TODO", "未确认")
```

它含中文但缺 `guess/tbd/推测/待定`，且 `smoke.ps1` 不调用它。同一判据两处实现、两套词表；M4 以 `build-desktop.ps1` 为母条，M5 侧是否单列由 LC审测试 判定，本文不重复计数。

**修复方向**：正则补 `估计|推测|未确认|待定|估算`；或把来源格式约束为可解析的 `ref:` 前缀。

### E-08（中，已证实）`HOST` 默认 `0.0.0.0`，与文档「仅本机可用」只在 compose 端口发布层成立

见 §1 ⑤ 的代码引用（`backend/src/server.js:10`、`backend/docker-compose.yml:10`、`backend/package.json:11`、`backend/api.md:5/18`、`backend/README.md:55`）。

**触发条件**：不走 compose，直接 `npm start` / `node src/server.js`（`backend/package.json:11`，`backend/api.md:6` 的「运行与自检」只写 docker compose，没有声明「直跑会暴露」）。

**预期 vs 实际**：`backend/api.md:18`「绑定 127.0.0.1 仅本机可用」；实际进程默认监听全部网卡，局域网内其他机器可直接访问无鉴权 API。

**容器实测（2026-11-05，Docker 29.9.2 / Compose v5.6.1）**：`docker compose -f backend/docker-compose.yml config` 解析出 `host_ip: 127.0.0.1`、`published: "8787"`、卷名 `lc-backend_lc-data`；用同一 Dockerfile 构建镜像后以 `-p 127.0.0.1:18788:8787` 运行，`docker ps` 显示 `127.0.0.1:18788->8787/tcp`，宿主 `Get-NetTCPConnection -LocalPort 18788 -State Listen` 只有 `127.0.0.1` 一行（无 `::1`/`0.0.0.0`）；而容器内 `/proc/net/tcp` 命中 `00000000:2253`（0.0.0.0:8787）——**宿主只回环、进程监听全网卡，两层行为都被实测到**。

**修复方向**：`HOST` 默认改 `127.0.0.1`，compose 显式设 `HOST: 0.0.0.0`（容器内必须），并在两份 README 统一口径。

### E-09（中，已证实）畸形百分号编码抛 `URIError` → 500（应 400）

**证据三件套**

`backend/src/server.js:314-316`

```js
  const caseMatch = pathname.match(/^\/v1\/cases\/([^/]+)$/);
  if (caseMatch) {
    const caseId = decodeURIComponent(caseMatch[1]);
```

同样的裸 `decodeURIComponent` 还有 `:346`（settings）、`:436`（shares）、`:454`（`/s/`）。

**触发条件**：`GET /v1/cases/%E0%A4%A?owner_id=x`（不完整 UTF-8 百分号序列）。

**预期 vs 实际**（实测）：

```
malformed-percent: 500 {"error":{"code":"internal_error","message":"internal server error"}}
server stderr: unhandled error: URIError: URI malformed
    at decodeURIComponent (<anonymous>)
    at route (file:///E:/APPDetector/review-base/backend/src/server.js:316:20)
```

预期 400 `invalid_case_id`（或 404），实际 500 且刷错误日志。

**修复方向**：包一层 `safeDecode`，`URIError` → 400 `invalid_field`。

### E-10（中，已证实）无 `Content-Type` 头时接受请求体，与 415 契约不符

**证据三件套**

`backend/src/server.js:89-92`

```js
  const type = String(req.headers['content-type'] || '');
  if (type && !type.includes('application/json')) {
    fail(415, 'unsupported_media_type', 'Content-Type must be application/json');
  }
```

**触发条件**：`POST /v1/cases` 不带 `Content-Type`、body 为合法 JSON。

**预期 vs 实际**：`backend/api.md:37` 声明「请求体不是 `application/json` → 415」；实际缺头时直接按 JSON 解析并成功 201。

**修复方向**：`if (!type.includes('application/json')) fail(415, ...)`（空头也拒），或把契约改成「缺头按 JSON 处理」。

### E-11（中，已证实）文档的「一键起」指向开发脏树 `E:\APPDetector\output\backend`，不是仓库内基线

**证据三件套**

`backend/api.md:244`

```powershell
cd E:\APPDetector\output\backend
```

`backend/README.md:13`（同一路径）

```powershell
cd E:\APPDetector\output\backend
```

**触发条件**：任何人按文档执行。

**预期 vs 实际**：预期在仓库的 `backend/` 目录构建/运行；实际切到 `output` 下的另一棵树（本轮明确禁止作为审查基线的那棵脏树），构建出的镜像/行为与基线不同源（无法保证一致），且换机/换盘符即失效。

**修复方向**：改成仓库相对路径（`cd backend`），或写明「从仓库根执行」。

### E-12（中，已证实）后端完全没有自动化测试，唯一自检依赖运行中的服务且不覆盖错误路径

**证据三件套**

`backend/package.json:10-14`（只有 start/dev/selfcheck，无 test）

```json
  "scripts": {
    "start": "node --no-warnings src/server.js",
    "dev": "node --no-warnings --watch src/server.js",
    "selfcheck": "node --no-warnings scripts/selfcheck.mjs"
  }
```

`backend/scripts/selfcheck.mjs:24-27`（只有计数，无断言框架/隔离）

```js
function check(condition, message) {
  if (condition) pass(message);
  else fail(message);
}
```

**触发条件**：任何修改后跑回归。

**预期 vs 实际**：`backend/api.md:248` 把 `selfcheck.mjs` 当契约自检；它不覆盖 409（同 owner 重复 id）、413、415、`limit`/`since` 边界、`sync.push` 冲突分支、跨 owner 撞 id、畸形 URL——本轮 E-02/E-03/E-04/E-09/E-10 若靠 selfcheck 全部漏检。且它必须对着一个已启动的服务跑，不是单元测试。

**修复方向**：加真单元测试（临时 DB 直接调 `Store`）+ 契约测试（起 server 后覆盖错误码）；selfcheck 增加上述分支。

## 4. 低危与工程债

### E-13（低，已证实）`changes` 与 `shares` 无清理策略，长期无界增长

`backend/src/db.js:116-120`（每次写都插一条，`payload` 为完整快照）

```js
  recordChange(ownerId, entity, entityId, op, payload, rev) {
    this.db.prepare(
      'INSERT INTO changes (owner_id, entity, entity_id, op, payload, rev, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).run(ownerId, entity, entityId, op, payload === null ? null : JSON.stringify(payload), rev, nowIso());
  }
```

`backend/src/db.js:238-248`（`createShare` 无每案例上限、无清理）

全库无 `DELETE FROM changes` / `DELETE FROM shares`。案例每改一次就留一份完整快照（`data` 上限 256 KB，`server.js:160`），长期同步的设备会持续膨胀；过期分享永不物理删除。

### E-14（低，已证实）请求日志记录完整 URL，分享 token 与 `owner_id` 落盘

`backend/src/server.js:468-470`

```js
  res.on('finish', () => {
    console.log(`${req.method} ${req.url} -> ${res.statusCode} ${Date.now() - started}ms`);
  });
```

`GET /s/<token>` 打开分享页时 token 明文进容器日志；任何能看日志的人可复放未过期分享链接。

### E-15（低，已证实）`/health` 对未鉴权调用方暴露全局案例/分享计数

`backend/src/server.js:261-270`

```js
    const health = store.health();
    sendJson(res, 200, {
      status: health.db === 'ok' ? 'ok' : 'degraded',
      service: SERVICE,
      version: VERSION,
      time: new Date().toISOString(),
      uptime_s: Math.round((Date.now() - startedAt) / 1000),
      db: health.db,
      cases: health.cases,
      shares: health.shares,
```

`backend/src/db.js:124-125`

```js
    const cases = this.db.prepare('SELECT COUNT(*) AS n FROM cases WHERE deleted = 0').get();
    const shares = this.db.prepare('SELECT COUNT(*) AS n FROM shares WHERE revoked = 0').get();
```

跨 owner 聚合计数，无鉴权可得（信息量小，记低）。

### E-16（低，已证实）空 patch 的 PUT 也推进 `rev` 并写变更日志

`backend/src/db.js:165-168` 与 `:180-181`

```js
  updateCase(ownerId, id, patch) {
    return this.tx(() => {
      const current = this.getCase(id, ownerId, true);
      if (!current || current.deleted) return null;
```

```js
      const now = nowIso();
      const rev = this.bumpRev();
```

`PUT /v1/cases/{id}` 只传 `owner_id`（合法空更新）也会产生一次全局 rev + 一条 `changes`，客户端每次同步都会多拉一条无变化的 update。

### E-17（低，已证实）重复撤销分享仍返回 200

`backend/src/db.js:264-267`

```js
  revokeShare(ownerId, token) {
    const result = this.db.prepare('UPDATE shares SET revoked = 1 WHERE token = ? AND owner_id = ?').run(token, ownerId);
    return Number(result.changes) > 0;
  }
```

SQLite 对「匹配但值未变」的行仍计 `changes=1`，第二次 `DELETE /v1/shares/{token}` 仍 200 `{revoked:true}`。契约未规定，记工程债。

### E-18（低，已证实）`backend/api.md` 声称 `smoke.ps1` 依赖 `/health`，实际 smoke 从不调用

`backend/api.md:95`

```
`status` 为 `ok` 当且仅当 SQLite 可读；`db` 为 `ok`/`error`。容器内 HEALTHCHECK 与 `scripts/smoke.ps1`（可选）都依赖此端点。
```

`scripts/smoke.ps1` 全文 174 行无任何 `/health` 请求（门禁只有 desktop/numeric/gen-mechanics/android）。文档不实，且掩盖了「后端起没起、契约通不通」根本没进门禁这一事实。

### E-19（低，已证实）Gradle wrapper 只有 properties、没有 `gradlew`/jar；构建脚本改用本机硬编码路径

```
git ls-files app/android → 仅 app/android/gradle/wrapper/gradle-wrapper.properties
```

`scripts/build-android.ps1:94`、`:104`、`:140`

```powershell
    $candidates.Add('C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9')
```

```powershell
    $known = 'C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin\gradle.bat'
```

```powershell
    else { $sdkDir = 'C:\Users\Administrator\AppData\Local\Android\Sdk' }
```

`gradle-wrapper.properties` 存在但 wrapper 不可用；脚本优先解析 `C:\Users\Administrator\...` 个人目录，换机/CI 走到 PATH 分支，构建环境不可复现。另 `app/android/README.md:14-16` 也写死了同一组个人路径。

### E-20（低，已证实）提交守卫把**已跟踪**的 `craft.json` 列为 always-deny

`scripts/check-staged-paths.ps1:156-157`

```powershell
    'feed.json',
    'craft.json',
```

`git ls-files craft.json` → 命中。`craft.json` 是基线受控文件（矩阵 #130，M8 研究工件），任何对其的合法更新都会被守卫拒绝；守卫注释称「always denied」的应是运行产物。另 `:13-14` 自认不装 git hook，守卫为纯手动，实际提交可完全绕过（流程债）。

### E-21（低，已证实）`selfcheck.mjs` 在**生产卷**上留测试数据

`backend/scripts/selfcheck.mjs:92-96`（推入 `case_push_<stamp>`，后续无删除）

```js
  const pushed = await call('POST', '/v1/sync/push', {
    owner_id: owner,
    cases: [{
      id: `case_push_${stamp}`,
      title: 'Pushed Case',
```

`:120` 只删主业 `caseId`，`case_push_*` 与 settings（`:81-83`）永久留在库里；`backend/api.md:248` 却让用户在生产容器里 `docker compose exec ... selfcheck.mjs`。每次自检污染一次数据，且 `owner` 每次不同，无法回收。

### E-22（低，已证实）Android 构建镜像以 root 运行、bind-mount 工作区、下载 SDK 不校验哈希

`app/android/Dockerfile:3`（无 `USER`，构建容器以 root 运行）

```dockerfile
FROM gradle:8.7-jdk17
```

`app/android/Dockerfile:11-12`（`wget` 后直接 `unzip`，无哈希校验）

```dockerfile
    && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q commandlinetools-linux-*.zip && rm commandlinetools-linux-*.zip \
```

`app/android/docker-compose.yml:5-9`（把宿主机工作区挂进来并回写 `local.properties`）

```yaml
    volumes:
      - .:/src
      - gradle-cache:/root/.gradle
    # Point AGP at the in-container SDK, then build.
    command: sh -c "echo 'sdk.dir=/opt/android-sdk' > local.properties && gradle assembleDebug --no-daemon"
```

构建产物/`local.properties` 以 root 写回宿主机（Linux 上会留下 root 属主），SDK zip 无 SHA256 校验。仅构建用途，记低。

### E-23（低，已证实）后端镜像基础标签浮动，构建不可完全复现

`backend/Dockerfile:2`

```dockerfile
FROM node:22-bookworm-slim
```

`backend/package.json:7-9`

```json
  "engines": {
    "node": ">=22.13"
  },
```

`node:22` 是浮标签，不同时间构建会解析到不同小版本（本轮构建日志解析到 `node:22-bookworm-slim@sha256:4b150b48…`；`engines >=22.13` 只约束下限）；`node:sqlite` 的可用性随小版本变化。固定到 digest 或 `node:22.17-bookworm-slim`。

### E-24（低，已证实）全同步 SQLite 调用阻塞事件循环，且无限流/连接上限

`backend/src/server.js:487-489`

```js
server.listen(PORT, HOST, () => {
  console.log(`${SERVICE} v${VERSION} listening on http://${HOST}:${PORT} (db=${DB_PATH})`);
});
```

`backend/src/db.js:98-103`（事务为同步阻塞调用）

```js
  tx(fn) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const result = fn();
      this.db.exec('COMMIT');
      return result;
```

`/v1/sync/push` 最多 500 条、每条一个事务，期间事件循环全部阻塞；未设 `server.maxConnections`、未配 `requestTimeout`/`headersTimeout`（依赖 Node 默认值）。单机自用可接受，记低。

### E-25（低，已证实）Android 真机门禁的 logcat 窗口只有 5 秒

`scripts/build-android.ps1:242`、`:264-280`

```powershell
        Start-Sleep -Seconds $LogWaitSeconds
```

```powershell
        $logcatText = Invoke-NativeText { & $adb -s $device logcat -d -v threadtime }
```

启动 5 秒后才扫一次 logcat，之后不再扫；6 秒后的延迟崩溃/主线程卡死不会判红。作为冒烟可接受，但不能当「无崩溃」证据。

## 5. 高度可疑

### H-01（已撤）命名卷由 root 建过导致 `USER node` 写不进 `/data` —— 实测不成立

原线索：`backend/Dockerfile:14-16` 的 `chown node:node /data` 只在镜像构建时执行；原假设是「若命名卷已由 root 创建/写过，`node` 用户写不进 `/data`」。原列「高度可疑」，本轮按纪律实测，不成立即撤。

`backend/Dockerfile:14-16`

```dockerfile
RUN mkdir -p /data && chown node:node /data

USER node
```

**实测（2026-11-05，Docker 29.9.2）**：新建命名卷 `lc-review-m4-rootvol`，挂入镜像后以 `--user root` 执行 `chown root:root /data`（同容器内 `ls -ldn /data` 显示 `0 0`）；再次挂载同一卷时 `ls -ldn /data` 回到 `1000 1000`、`stat` 显示 `node:node 755`，`node` 用户 `touch /data/probe` 退出码 0。Windows bind mount 场景目录为 `777 0 0`，`touch` 同样成功。Docker 在挂载点重建了镜像目录的属主，`USER node` 可写。

**结论**：原假设不成立，撤回，不进入缺陷/待确认清单；编号 H-01 保留为已撤。若未来把 `/data` 改为 Linux 主机上 `root:root 755` 的 bind mount，需重开此条（缺的证据：Linux 主机 bind mount 实测）。

### H-02（高度可疑）桌面门禁的 PASS 不能代表真实 UI 正常

> 2026-11-05 总监裁定：本条转交叉引用母条 **A-01**（M1），M4 不重复计数。

`scripts/build-desktop.ps1:114-123`（门禁的全部视觉判据就是「PNG 存在 + 1100×780 + >20 KB」，另见 `scripts/build-desktop.ps1:125-128` 的字节数判据）：

```powershell
if (Test-Path -LiteralPath $exe) {
    Write-Info '[2/3] headless screenshot (1100x780)'
    if (Test-Path -LiteralPath $shot) { Remove-Item -LiteralPath $shot -Force }
    Invoke-NativeText { & $exe --screenshot $shot } | Out-Null
    $shotCode = $LASTEXITCODE
    if ($shotCode -ne 0) { Add-Failure "screenshot run exited $shotCode" }
    $png = Get-PngHeader $shot
    if ($null -eq $png) {
        Add-Failure "screenshot missing or not a PNG: $shot"
    } elseif ($png.Width -ne 1100 -or $png.Height -ne 780) {
```

`scripts/README.md:16-17`（门禁自述的验收口径）：

```markdown
2. 运行 `target\debug\luopan.exe --screenshot <out>\screenshot.png`，校验 PNG 签名与 **1100×780** 尺寸；
   文件小于 20 KB 视为空白渲染失败
```

该截图是否走真实 UI 渲染路径，取决于 M1 的 `app/src/software.rs`（归 LC审客户端）；若截图由软件/离屏路径生成，`DESKTOP: PASS` 只证明「能出一张图」。**本模块不作结论**，等 M1 报告合并后回填；缺的证据是 M1 对 `--screenshot` 实际调用链的判定。

## 6. 待确认

### Q-01（待确认）`Get-Result([int]$Code)` 把 `$null` 当 0，`$null` 时单项汇总判 PASS

`scripts/smoke.ps1:128`

```powershell
function Get-Result([int]$Code) { if ($Code -eq 0) { 'PASS' } else { 'FAIL' } }
```

`[int]$null` = 0。若某门禁的 `$LASTEXITCODE` 未被赋值（子进程未真正启动等），该项汇总会打印 `PASS`；整体判定 `scripts/smoke.ps1:134-135` 用的是 `$code -ne 0`（`$null` 视为 FAIL），所以 `overall` 仍为 FAIL——只误导单项。**缺的证据**：未构造出 `$LASTEXITCODE` 未赋值的真实触发路径（四条门禁都会先启动子进程）。

### Q-02（已关闭）Docker 端口发布是否只回环 —— 实测只回环，无旁路

原线索：`backend/docker-compose.yml:10`

```yaml
      - "127.0.0.1:8787:8787"
```

compose 只声明 IPv4 回环，需确认 WSL2 mirrored 网络或 IPv6 是否有旁路。

**实测（2026-11-05）**：`docker compose -f backend/docker-compose.yml config` 解析为 `host_ip: 127.0.0.1`；以 `-p 127.0.0.1:18788:8787` 运行后 `docker ps` 显示 `127.0.0.1:18788->8787/tcp`，`Get-NetTCPConnection -LocalPort 18788 -State Listen` 仅一行 `127.0.0.1`（无 `::1`、无 `0.0.0.0`）。

**结论**：无旁路，关闭。原待确认项不进入 Q 清单。

---

# 批次二：其余维度全量展开

## 7. 维度覆盖（D1–D12）

| 维度 | 覆盖情况 | 结论 / 关联条目 |
|---|---|---|
| D1 功能正确性 | 26 文件全读；逐端点走查 cases/settings/sync/shares/`/s/`；实测 6 条路径 | 主路径可用；E-02、E-03、E-09 |
| D2 边界与异常 | 空 body、非法 JSON、数组/null、超限、畸形 URL、limit/since 边界、缺 Content-Type | E-04、E-09、E-10；`asDegree/asTags/asLocation/asData` 校验完整（`server.js:112-177`） |
| D3 并发与资源 | 单进程同步 SQLite、WAL、`busy_timeout=5000`、`BEGIN IMMEDIATE`、事件循环阻塞、无连接上限 | E-24；单进程下无并发写冲突 |
| D4 安全 | bind、CORS、鉴权、SQL 注入、分享越权、XSS、路径穿越、容器 root、token 熵 | E-01、E-08；**否定项**：SQL 注入、XSS、路径穿越、分享越权（见 §1） |
| D5 性能与扩展 | 索引 `idx_cases_owner_rev`/`idx_changes_owner_seq`/`idx_shares_case` 均存在；`changes`/`shares` 无界增长；同步阻塞 | E-13、E-24 |
| D6 数据一致性与事务 | `tx()` 正确回滚；rev 全局单调；推送非原子；id 全局主键；schema 无外键（`PRAGMA foreign_keys=ON` 实际无约束可执行） | E-02、E-03、E-16；软删由查询兜底，无悬挂引用 |
| D7 错误处理与日志 | `HttpError`/500 兜底正确；日志含 token；无 request id；未处理异常打栈 | E-09、E-14；`server.js:471-484` 兜底逻辑正确 |
| D8 接口契约一致性 | `backend/api.md` 逐端点比对实现 | E-02（409→500）、E-04（413 不可达）、E-10（415）、E-18（health 依赖不实）；其余端点字段/状态码一致 |
| D9 可维护性与耦合 | `server.js` 501 行单文件路由、`db.js` 298 行；无模块拆分；硬编码个人路径；无测试 | E-12、E-19 |
| D10 测试覆盖缺口 | 后端无单测；selfcheck 不覆盖错误路径；smoke 假 PASS；Android logcat 窗口 | E-06、E-07、E-12、E-21、E-25 |
| D11 配置与部署风险 | 浮标签、root 构建镜像、文档路径、无 CI、卷/备份、`PUBLIC_BASE_URL` 默认回环 | E-08、E-11、E-20、E-22、E-23、E-25；`git ls-files` 无任何 CI 配置 |
| D12 与文档一致性 | `backend/api.md` / `backend/README.md` / `scripts/README.md` / `app/android/README.md` 与实现逐条核 | E-07、E-11、E-18、E-19；`app/android/README.md:9` 的「APIKEY 已内置」属 M3 风险，仅交叉引用 |

### 已验证否定项（明确声明，避免被当成漏审）

- **SQL 注入**：无（§1 ①）。
- **分享枚举 / 越权**：无（§1 ②，含实测）。
- **存储型 XSS**：无。分享页所有插值经 `escapeHtml`（`server.js:197-204`），`title`/`rows`/`notes`/`created_at`/`expires_at` 全覆盖（`:206-246`）；响应头 `content-type: text/html; charset=utf-8`。
- **路径穿越**：无。服务端不提供静态文件，`/s/{token}` 只做 HTML 渲染；路径参数最终只进参数化 SQL。
- **后端容器 root**：不成立，`Dockerfile:16` `USER node`。
- **事务回滚**：`db.js:98-108` 在抛错时 `ROLLBACK`，逻辑正确。
- **固定窗口无未处理异常吞掉**：`server.js:471-484` 对上抛异常统一 500，不泄露内部信息。

## 8. 端点契约对照（`backend/api.md` vs 实现）

| # | 契约（api.md） | 实现 | 判定 |
|---|---|---|---|
| 1 | `GET /health`、`/v1/health` 200，`status/db` | `server.js:260-271` | 一致 |
| 2 | `GET /v1/cases` `since/limit/include_deleted`，默认 100 上限 200 | `server.js:287-296`、`:179-195` | 一致（`include_deleted` 非 `1` 一律当 0，契约只允许 0/1） |
| 3 | `POST /v1/cases` 201；重复 id 409 | `server.js:299-311` | **不一致**：跨 owner 撞 id → 500（E-02） |
| 4 | `GET/PUT/DELETE /v1/cases/{id}` | `server.js:314-341` | 一致；畸形 URL → 500（E-09） |
| 5 | settings GET/PUT，≤256 KB | `server.js:344-360` | 一致 |
| 6 | `GET /v1/sync/pull` cursor/changes | `server.js:363-369`、`db.js:269-286` | 一致 |
| 7 | `POST /v1/sync/push` applied/conflicts/settings | `server.js:371-408` | **不一致**：非原子部分写入（E-03） |
| 8 | `POST /v1/shares` 201，1–365 天，token 32 hex | `server.js:412-431`、`db.js:238-248` | 一致 |
| 9 | `GET /v1/shares/{token}` 快照无 owner_id | `server.js:437-441`、`db.js:250-262` | 一致（实测） |
| 10 | `GET /s/{token}` HTML | `server.js:452-461` | 一致 |
| 11 | `DELETE /v1/shares/{token}?owner_id` | `server.js:443-448` | 一致 |
| 12 | 413 / 415 错误码 | `server.js:65-103` | **不一致**：413 不可达（E-04）、缺头时 415 不触发（E-10） |
| 13 | 请求体上限 1 MB | `server.js:14` | 一致 |
| 14 | `PUBLIC_BASE_URL` 决定分享 URL 前缀 | `server.js:12`、`:425` | 一致（默认回环，局域网需显式设置，README 有写） |

## 9. 已读文件清单（M4 全部 26 个，行数与矩阵一致）

| # | 文件 | 行数 | 读完 |
|---|---|---|---|
| 1 | `.gitattributes` | 2 | 是 |
| 2 | `.gitignore` | 39 | 是 |
| 3 | `app/android/app/build.gradle` | 38 | 是 |
| 4 | `app/android/app/proguard-rules.pro` | 2 | 是 |
| 5 | `app/android/build.gradle` | 4 | 是 |
| 6 | `app/android/docker-compose.yml` | 12 | 是 |
| 7 | `app/android/Dockerfile` | 20 | 是 |
| 8 | `app/android/gradle.properties` | 4 | 是 |
| 9 | `app/android/gradle/wrapper/gradle-wrapper.properties` | 7 | 是 |
| 10 | `app/android/README.md` | 41 | 是 |
| 11 | `app/android/settings.gradle` | 16 | 是 |
| 12 | `backend/.dockerignore` | 6 | 是 |
| 13 | `backend/api.md` | 269 | 是 |
| 14 | `backend/docker-compose.yml` | 26 | 是 |
| 15 | `backend/Dockerfile` | 22 | 是 |
| 16 | `backend/package.json` | 15 | 是 |
| 17 | `backend/README.md` | 80 | 是 |
| 18 | `backend/scripts/selfcheck.mjs` | 145 | 是 |
| 19 | `backend/src/db.js` | 298 | 是 |
| 20 | `backend/src/server.js` | 501 | 是 |
| 21 | `scripts/build-android.ps1` | 312 | 是 |
| 22 | `scripts/build-desktop.ps1` | 195 | 是 |
| 23 | `scripts/check-staged-paths.ps1` | 212 | 是 |
| 24 | `scripts/README.md` | 93 | 是 |
| 25 | `scripts/smoke.ps1` | 174 | 是 |
| 26 | `tools/mechanics/gen-mechanics.ps1` | 279 | 是 |
| | **合计** | **2812** | 与 `00-MATRIX.md` M4 合计一致 |

**交叉引用（不计入本模块结论）**：`tests/numeric/run_all.py`（39 行全读，M5 #65）与 `tests/numeric/tools/check_mechanics.py`（44 行全读，M5 工具）仅为回答必查 ③ 与 E-07 口径对比读取；`app/Dockerfile`（21 行）、`app/docker-compose.yml`（19 行）、`app/.dockerignore`（4 行）按矩阵归 M7（LC审文档），完整读过但只在 D11 部署风险上交叉引用，正式结论以 LC审文档为准；`app/src/main.rs`（701 行）只读 grep 命中的生成区 L55-L144 与 `TYPE_*` 行（约 90 行）以验证 E-05，其余 611 行未读、未审 M1 逻辑；`app/android/app/src/main/AndroidManifest.xml`（30 行）只读 `Select-String` 命中的 L4-L27 权限/activity 行（约 10 行），未逐行读完，归 M2；`docs/review/00-MATRIX.md`（253 行）与 `docs/review/98-report-conventions.md`（432 行）全读，作为范围与格式依据。

## 10. 覆盖声明与未覆盖范围

**已覆盖**：M4 26/26 文件逐行读完（2812 行，与矩阵一致）；后端全部 14 个端点（含 `/`、`/health`、`/s/`）走查；5 个必查项全部给出代码级结论；SQL/分享越权/XSS/路径穿越/容器 root 给出否定证据。

**运行时实测（本机 `node v22.17.0`，临时 DB，未碰基线源码）**：跨 owner 撞 id → 500 + `UNIQUE constraint failed: cases.id`；1.1 MB body → 客户端 `fetch failed`（无 413）；`sync.push` 第 2 条非法 → 400 且第 1 条已落库（`count=2`）；畸形百分号 → 500 `URIError`；跨 owner 读案例 404 / 跨 owner 撤销分享 404 / 分享快照无 `owner_id`；`gen-mechanics -Check` 两种绕过均 `exit=0`。

**未覆盖（明确声明）**：

1. **容器构建与运行已实测（2026-11-05）**：`docker build -t lc-backend:review-m4 backend` 成功（基础镜像解析到 `node:22-bookworm-slim@sha256:4b150b48…`）；`docker run -p 127.0.0.1:18788:8787` 后 `id` 为 `uid=1000(node)`、`/health` 返回 200 `status:ok db:ok`、healthcheck 状态 `healthy`、`/data` 与 `lc.sqlite*` 属主 `1000:1000`、宿主仅回环监听、容器内 `/proc/net/tcp` 为 `00000000:2253`。**未执行**的一步是 `docker compose up`（会占用宿主 8787 并创建 `lc-backend_lc-data`），compose 文件仅做 `docker compose config` 解析。
2. **未执行 Android 构建与真机**：`gradle assembleDebug`、`adb install/logcat` 全链未跑；`build-android.ps1` 的结论来自代码走查（E-19、E-22、E-25）。
3. **未跑数值测试套件**：只读入口 `run_all.py` 的退出码语义，未执行 86 项用例（M5 范围；本机复跑 `tests=86`，见 `91-audit.md` §7.2）。
4. **未审 M1/M2/M3/M5/M6/M7/M8**：仅按上述交叉引用读取；桌面截图真实渲染路径（H-02）需 M1 结论回填。
5. **node:sqlite 在 Docker 镜像内的行为已实测**：容器 `/health` 返回 `db:ok`，说明镜像内 `node:sqlite` 可直接用；未单独读取镜像内 Node 小版本号（构建日志只给出基础镜像 digest）。
6. **未验证浏览器真实跨域行为**：E-01 的预检/读取能力来自 HTTP 语义与响应头，未用真实浏览器跨域复现（写入路径的 OPTIONS 204 + ACAO `*` + allow-headers 已齐全）。

---

## 11. 中/低条目字段补齐（§5 最小字段逐条）

> 批次一的 §2（高）与 §3（中）已含完整三件套；本表把 §3/§4 的中/低条目补齐「文件 L区间 + 触发条件 + 预期 vs 实际」，并给出母条归属。片段原文仍以各条正文为准（本表不重复贴代码）。

| 编号 | 级别 | 文件 L区间 | 触发条件 | 预期 | 实际 | 母条/去重 |
|---|---|---|---|---|---|---|
| E-05 | 中 | `tools/mechanics/gen-mechanics.ps1 L234-L243`、`L254-L257` | 生成区内只改大小写；或真实标记区前插入一对诱饵标记后手改真实区 | `-Check` 报 stale、exit 1 | 两种手改均 `PASS`、exit 0（实测） | M4 |
| E-06 | 中 | `scripts/smoke.ps1 L105-L108`、`L128-L136`；`tests/numeric/run_all.py L25-L33` | numeric 套件 0 个用例或全部 skip，`run_all.py` 仍 exit 0 | smoke 判 numeric 红 | 汇总打印 `numeric : PASS (tests=0 …)`、`SMOKE: PASS` | **交叉引用 F-01（M5 母条），M4 不重复计数** |
| E-07 | 中 | `scripts/build-desktop.ps1 L147-L150`；`scripts/README.md L18` | `values[].source` 填 `估计/推测/未确认/待定` | 判「无硬来源」、门禁红 | 只拦英文词，中文软来源判 PASS | M4（M5 侧 `check_mechanics.py L19` 为另一套词表，见 §11.6） |
| E-08 | 中 | `backend/src/server.js L10`；`backend/docker-compose.yml L10`；`backend/package.json L11`；`backend/api.md L5/L18` | 不走 compose，直接 `npm start`/`node src/server.js` | 只监听 127.0.0.1（`backend/api.md L18`） | 进程监听 0.0.0.0，LAN 可访问（容器实测 `/proc/net/tcp`=`00000000:2253`） | M4 |
| E-09 | 中 | `backend/src/server.js L314-L316`（同类 L346/L436/L454） | `GET /v1/cases/%E0%A4%A?owner_id=x` 等畸形百分号路径 | 400 `invalid_field` | 500 `internal_error` + `URIError` 栈（实测） | M4 |
| E-10 | 中 | `backend/src/server.js L89-L92` | `POST /v1/cases` 不带 `Content-Type`、body 为合法 JSON | 415 `unsupported_media_type`（`backend/api.md L37`） | 按 JSON 解析并 201 | M4 |
| E-11 | 中 | `backend/api.md L244`；`backend/README.md L13` | 任何按文档执行 `cd E:\APPDetector\output\backend` 的人 | 在仓库 `backend/` 构建基线代码 | 切到开发脏树，与基线不同源 | M4 |
| E-12 | 中 | `backend/package.json L10-L14`；`backend/scripts/selfcheck.mjs L24-L27` | 任何后端修改后的回归 | 有单测/契约测试覆盖错误码 | 无自动化测试；selfcheck 不覆盖 409/413/415/limit/sync 冲突 | M4 |
| E-13 | 低 | `backend/src/db.js L116-L120`、`L238-L248` | 每次案例写/设置写；每次创建分享 | 变更日志/分享有保留或清理策略 | 无任何 `DELETE FROM changes/shares`，无界增长 | M4 |
| E-14 | 低 | `backend/src/server.js L468-L470` | 任意请求，含 `GET /s/<token>` | 日志不落分享 token/`owner_id` | 完整 URL 明文进容器日志 | M4 |
| E-15 | 低 | `backend/src/server.js L261-L270`；`backend/src/db.js L124-L125` | 未鉴权 `GET /health` | 不暴露跨 owner 聚合计数 | 返回全局 `cases`/`shares` 计数 | M4 |
| E-16 | 低 | `backend/src/db.js L165-L168`、`L180-L181` | `PUT /v1/cases/{id}` 只传 `owner_id`（空更新） | 无变化不推进 `rev`、不写变更 | 仍 `bumpRev` + 插一条 `update` | M4 |
| E-17 | 低 | `backend/src/db.js L264-L267` | 对同一 token 重复 `DELETE /v1/shares/{token}` | 第二次 404 或幂等语义明确 | 第二次仍 200 `{revoked:true}` | M4 |
| E-18 | 低 | `backend/api.md L95`；`scripts/smoke.ps1` 全文 | 任何人认为 smoke 覆盖 `/health` | smoke 调 `/health` 并判红 | smoke 无任何 `/health` 调用 | M4 |
| E-19 | 低 | `scripts/build-android.ps1 L94`、`L104`、`L140`；`app/android/gradle/wrapper/gradle-wrapper.properties L1-L7` | 换机/CI 构建 Android | wrapper 可复现（`gradlew`/jar 齐全） | 只有 wrapper properties，无 `gradlew`/jar；脚本优先个人绝对路径 | M4 |
| E-20 | 低 | `scripts/check-staged-paths.ps1 L156-L157`；`craft.json`（跟踪） | 合法更新 `craft.json` 后提交 | 守卫放行受控文件 | 被 always-deny 拒绝；且守卫不装 hook，可完全绕过 | M4 |
| E-21 | 低 | `backend/scripts/selfcheck.mjs L92-L96`、`L120` | 在生产卷执行 `docker compose exec … selfcheck.mjs` | 自检后数据可回收 | `case_push_*` 与 settings 永久留存 | M4 |
| E-22 | 低 | `app/android/Dockerfile L3`、`L11-L12`；`app/android/docker-compose.yml L5-L9` | `docker compose up --build` 构建 APK | 非 root 构建、下载物可校验 | root 运行、bind-mount 回写、SDK zip 无哈希校验 | M4 |
| E-23 | 低 | `backend/Dockerfile L2`；`backend/package.json L7-L9` | 任意时间 `docker build` | 构建可复现到固定 Node 版本 | 浮标签，本次解析到 `node:22-bookworm-slim@sha256:4b150b48…` | M4 |
| E-24 | 低 | `backend/src/server.js L487-L489`；`backend/src/db.js L98-L103` | 大 `sync.push`（≤500 条）/并发请求 | 不阻塞事件循环、有连接上限 | 全同步 DB 调用阻塞；无 `maxConnections`/超时配置 | M4 |
| E-25 | 低 | `scripts/build-android.ps1 L242`、`L264-L280` | App 启动 5 秒后才崩溃 | 冒烟能判红 | 只扫启动后 5 秒内 logcat，之后崩溃漏判 | M4 |

## 12. 高度可疑 / 待确认（§9 字段）

| 编号 | 线索 | 已掌握证据 | 缺什么证据 | 由谁补 | 补齐后的判据 |
|---|---|---|---|---|---|
| H-02 | 桌面门禁只验 PNG，不验渲染路径 | `scripts/build-desktop.ps1 L114-L123`；`scripts/README.md L16-L17` | M1 对 `--screenshot` 调用链的判定（是否初始化真实 egui UI，还是只走 `software.rs`） | LC审客户端（在 10-M1-desktop.md 的 A-01 给结论） | FAIL：`--screenshot` 走独立软件渲染且不初始化主 UI → `DESKTOP: PASS` 不能代表 UI；PASS：走真实 UI → 本项关闭 |
| Q-01 | `Get-Result([int]$Code)` 把 `$null` 当 0 | `scripts/smoke.ps1 L128`、`L134-L136` | 一条能让 `$LASTEXITCODE` 在赋值前仍为 `$null` 的实际触发路径 | LC审服务端（本人在第二批后补一次启动失败实验） | FAIL：构造出「子进程未启动 + 汇总仍打印单项 PASS」；PASS：所有路径下 `& $hostExe` 必然赋值或直接终止脚本 |

## 13. 已撤 / 已关闭

| 编号 | 原级别 | 处置 | 依据 |
|---|---|---|---|
| H-01 | 高度可疑 | 已撤 | 实测 root 属主命名卷在重挂载时被 Docker 重置为 `node:node`，`node` 可写；Windows bind mount 为 777。原假设不成立（见 §5 H-01 实测记录） |
| Q-02 | 待确认 | 已关闭（无旁路） | 实测宿主仅 `127.0.0.1` 监听，无 `::1`/`0.0.0.0`（见 §6 Q-02） |

## 14. 必查项缺口（逐项写「命中/未命中 + 还缺什么」）

| 必查项 | 结论 | 已命中证据 | 缺口 / 补做 |
|---|---|---|---|
| ① 案例 CRUD 的 SQL 拼接/参数化 | 命中（无注入） | `db.js` 全部 `prepare(...).run/get/all`；`exec` 仅字面量 DDL/PRAGMA | 无 |
| ② 分享链接枚举/越权 | 命中（无越权） | 128 bit token（`db.js L241`）；create/revoke owner 作用域；实测跨 owner 404、快照无 `owner_id` | 未用真实浏览器跨域复现（不影响越权结论，分享 GET 本就公开） |
| ③ `smoke.ps1` 假 PASS | 命中 2 条 | E-06（numeric 计数不入门禁，母条 F-01）、E-05（-Check 绕过） | 未在干净 CI 机器跑完整 smoke（本机无 Java/Android 真机环境），门禁链只在代码层核实 |
| ④ `gen-mechanics.ps1 -Check` 防手改 | 命中（可绕过） | `L234-L243` 取第一对标记、`L254` 用 `-eq`；两种改写实测 exit 0 | 未对 Java 区重复做两类改写实测（同一 `Get-Region` 对两文件，机制一致） |
| ⑤ 容器只绑 127.0.0.1 / root 运行 | 命中（分层结论） | compose `host_ip 127.0.0.1`；实测宿主仅回环、进程 `0.0.0.0:8787`；`id`=1000(node)、health `healthy` | 未跑 `docker compose up` 本体（用等价 `docker run -p 127.0.0.1:18788:8787` 验证），未建 `lc-backend_lc-data` 卷 |

## 15. 未覆盖文件清单（读了几行、为什么没读完）

> 规则：M4 范围 26/26 全读；跨模块文件只读与 M4 结论直接相关的最小片段，且**不计入本模块结论**。不存在「读了一半却当全量」。

| 范围 | 文件数 | 状态 | 已读 | 未读完原因 / 补做 |
|---|---|---|---|---|
| M1 桌面客户端 | 3 | 未覆盖（仅交叉引用） | `app/src/main.rs` L55-L144（约 90/701 行），只读生成区标记与 `TYPE_*` id | 为 E-05 验证生成区边界；其余 611 行归 LC审客户端，未下任何 M1 结论 |
| M2 Android 客户端 | 25 | 未覆盖（仅交叉引用） | `app/android/app/src/main/AndroidManifest.xml` L4-L27（约 10/30 行），只读 `Select-String` 命中的权限/activity 行 | 为核对 `am start -n com.luopan.fengshui/.MainActivity` 与包名；其余 24 文件归 LC审客户端 |
| M3 地图/3D | 3 | 未覆盖 | 0 行 | 归 LC审地图 |
| M4 服务端/基建 | 26 | **已覆盖** | 26/26 文件、2812/2812 行（明细见 §9） | 无 |
| M5 测试资产 | 22 | 未覆盖（仅交叉引用） | `tests/numeric/run_all.py` 39/39 行、`tests/numeric/tools/check_mechanics.py` 44/44 行 | 为 E-06 取证与 E-07 口径对比；其余 20 文件归 LC审测试 |
| M6 文档/契约 | 42 | 未覆盖 | 0 行（`backend/api.md`/`backend/README.md` 属 M4 已全读） | 归 LC审文档 |
| M7 数值档案/构建 | 8 | 未覆盖（仅交叉引用） | `app/Dockerfile` 21/21、`app/docker-compose.yml` 19/19、`app/.dockerignore` 4/4 | 仅在 D11 部署风险上交叉引用，正式结论以 LC审文档为准 |
| M8 研究工件 | 75 | 不审，仅盘点 | 0 行 | `00-MATRIX.md` §4 |
| 二进制/图片 | — | 不审 | 0 行 | `00-MATRIX.md` §4 |
| 基线外分支/未提交改动 | — | 不在范围 | — | `00-MATRIX.md` §4 |
| 审查规范/矩阵 | 2 | 已读 | `00-MATRIX.md` 253/253、`98-report-conventions.md` 432/432 | 作为范围与格式依据 |

## 16. 计数与去重声明

- **M4 自计**：高 4（E-01…E-04，全部已证实）；中 7（E-05、E-07…E-12）；低 13（E-13…E-25）→ **缺陷 24 条**；本模块新计高度可疑 0 条（H-02 挂 A-01 交叉引用）；待确认 1（Q-01）。
- **交叉引用不计入 M4**：E-06（中）→ 母条 **F-01**（M5，`run_all.py` 的 `wasSuccessful()` 语义）；H-02（高度可疑）→ 母条 **A-01**（M1，`--screenshot` 渲染路径）。
- **已撤 / 已关闭**：H-01（root 属主卷假设实测不成立，已撤）；Q-02（实测无旁路，已关闭）。
- **编号总数自洽**：E-01…E-25（25）+ H-01/H-02（2）+ Q-01/Q-02（2）= 29，与正文条目数一致；无空号、无跳号、无跨模块借用。
- **与 B-02 / C 条无同因**：B-02 是 Android 导出广播注入（M2），E-01 是 HTTP 无鉴权 + CORS `*` 面（M4），二者入口、利用条件、修复点均不同；C 的地图 key 明文属 M3，与 M4 无交集。
- **E-07 的跨模块同因**：`tests/numeric/tools/check_mechanics.py L19` 是第二套「硬来源」词表（含中文、缺 `guess/tbd/推测/待定`），与 `build-desktop.ps1 L147-L150` 口径不一致；M4 以 build-desktop 为母条，M5 侧是否单列由 LC审测试 判定，本文不重复计数。
