# M6 Stage 2 · 第 8 条 3D 失败可读（地图侧）

> 归属：LC地图。代码 `feature/地图-m6-3d`。对齐 develop `394d7f5`。
> 前置：`e605df3`（2a/2b/resetCompass）已合入 develop `394d7f5`。

## 1. 做了什么（四个改动，都在 `map.html`）

### 1.1 `demAt()` 不再用 0 当哨兵（真缺陷，审地图 报出）
- 现状：`if (a === null || b === null || c === null || d === null) return 0;`
- 同文件 `elevAt` 早已立规矩「DEM 瓦片没加载时返回 null，**绝不返回 0 当哨兵**」，`demAt` 反着做。
- `demAt` 是**网格高度与相机目标高度**的来源 ⇒ 缺角时高度被当成海平面：画面静默变平/跳动，日志一条没有，与「本来就是平原」无法区分。
- 改为返回 `null`，并逐个调用点显式处理：
  | 调用点 | 原行为 | 现行为 |
  |---|---|---|
  | ray-march（拾取/落点）2 处 + 二分 2 处 | `null*VEXAG` → `0` | 缺角时 `continue` / `break`，跳过该步 |
  | `buildMesh` 网格高度 | 静默 0 | 缺角按 0 建面，但**计数**并一次性打 `LC3D WARN dem-edge-mesh <n>/<vc>` |
  | `render3D` 相机目标 | 静默 0 → 相机掉海平面 | **沿用上一帧高度** + 一次性打 `LC3D WARN dem-edge` |

### 1.2 网格边越界：每块地形远边一直被压平（由 1.1 暴露出来的第二缺陷）
- `buildMesh` 采样点 `p.ox*256 + i*step`，`step = PATCH_PX/N = 768/96 = 8`，`i` 取到 96 ⇒ 最大值 = `(ox+3)*256`，**已越出本 patch 的 3×3 DEM 块**（该块只到 `ox+2` 那块瓦片）。
- 命中顶点数 `97 + 97 − 1 = 193`（`N+1 = 97`，正是最后一行与最后一列），与实测 `193/9409` 逐一吻合（`9409 = 97²`）⇒ **结构性，不是偶发**。
- 原来这 193 个点被静默压成 0 ⇒ **每块地形的远边永远是一条被压平的边**。
- 修法：采样坐标夹回块内 `Math.min(..., p.ox*256 + PATCH_PX - 1.001)`。
  取 `-1.001` 而不是 `-0.001`，因为双线性要取 `floor(gx)` 与 `floor(gx)+1` 两个点，`767.999` 仍会摸到第 768 个 texel（越界）。
- 修后实测：`LC3D WARN dem-edge-mesh` **不再出现**（该类告警数量 0）。
- **已知代价（写进证据，避免复验误判）**：边缘 1 texel 高度等于相邻采样点的夹取复制，边界会有一条 1 texel 的「平边」——这是夹取的正常行为。真正无缝需网格取 `PATCH_PX-1` 个 texel 或 DEM 多加载一圈 4×4，本次不做（会牵动 `patch.mpp` 与四角坐标）。

### 1.3 删掉 `dem-load` 死分支（按总监裁定）
- 原代码：每个 job 都 `demTile(...).catch(function () { return null; })`，rejection 已被转成 resolve；
  `Promise.all(jobs).then(success, failure)` 的 `failure` **永不触发** ⇒ `console.log("LC3D ERROR dem-load")` 是死分支。
- 已删除该回调，并在原位置写明三态触发条件。
- 保留 `img.onerror = function () { fail(new Error("dem-load")); }`（那是 `demTile` 内部的 reject 原因串，不是 `last3dError` 的取值，删了会丢信息）。

### 1.4 三态（+1 个非整块态）触发条件，写死在代码注释里
| 取值 | 触发条件 | 收尾行为 |
|---|---|---|
| `webgl-unavailable` | `initGL()` 返回 false | **回落 `setMode("2d")`**（唯一回落的一态） |
| `dem-missing` | 任一 DEM 瓦片取不到（`tiles[i] === null`） | 不回落；停在 3D 且 **保留上一块 patch**（`hasTexture` / `demZoom` 不变），继续显示旧地形 |
| `texture-fail <ok>/36` | 贴面瓦片成功率 `< 30/36` | 不回落，地形在、无影像贴面（走高程配色） |
| `dem-edge` / `dem-edge-mesh` | DEM **局部**缺角（`demAt` 返回 null） | **不进 `last3dError`**，改写 `last3dWarn`（见 §1.5）：拖到 patch 边缘是正常操作，混进失败字段会把「成功」读成「失败」 |
| `null` | 成功（贴面 ok 即清） | — |

> `dem-missing` 实测会出现 `warn="dem-edge"`：那是沿用旧块时相机目标落到旧块边缘所致，属**局部降级**，只进 `last3dWarn`，不进 `last3dError`。旧代码里它会混进 `err`，这一档会被读成两个错误——这正是拆字段的理由。

## 2. 真机实测（构建指纹）

```
commit          feature/地图-m6-3d（在 develop 394d7f5 之上）
构建            clean assembleDebug
APK             8,595,205 B
apk.sha256      BEB23837317766FDD2F64D92EC18042FC11634296C7C2E57374D69C91E2B09A7
装机            tools/device/install-debug.ps1 → INSTALL_OK taps=2 / MARKET_RESTORED=ok
lastUpdateTime  02:43:26，device base.apk sha 与构建产物一致（装完即核）
```

| 用例 | 结果 |
|---|---|
| 进入 3D | `getState()` → `"3d|null|true"`（mode / last3dError / hasTexture） |
| 贴面 | `LC3D texture ok 36/36 vec z9` |
| 网格边告警（修前） | `LC3D WARN dem-edge-mesh 193/9409` |
| 网格边告警（修后） | **无**（夹取生效） |
| 3D 帧率 | `fps=56`（进入后 `hud` 打印） |

## 3. 未完成：`dem-missing` 注入一档
- 计划：把 `TERRARIUM` 临时指到不可达地址 → `ensurePatch(true)` → 期望 `getState().last3dError === "dem-missing"`、`mode` 仍为 `3d`、`hasTexture=false`。
- **中断原因**：注入过程中设备被他人覆盖（`lastUpdateTime 02:44:17`、`9,093,512 B`、`sha 37ec203e…`，不是我的包），我的包已不在机上，继续测就是测别人的构建。
- 状态：**待窗口**；代码路径已验证存在（`last3dError = "dem-missing"` 唯一赋值点），但**未按「注入→读出」走通一次，故不写「已验」**。
- `webgl-unavailable` 按总监裁定走代码路径登记，不硬造。

## 4. 时间戳与轮次
- 开工 `2026-11-08 02:28` / 收工 `02:47` / 返工 3 轮（`dem-load` 删除、`demAt`→null、网格夹取 `-0.001`→`-1.001` 各一轮）。
- 期间两次被他人装包覆盖（`02:27:20`、`02:44:17`），已在证据里记录，未把别人的构建当自己的结果。

## 1.5 `last3dWarn` 与 `last3dError` 拆开（总监 11-08 裁定 A 方案）

- 背景：`buildMesh` 与 `render3D` 的局部降级原本写入 `last3dError`，而成功态判据是 `last3dError === null`。
  **拖到 patch 边缘是正常操作**，一触发就把「成功」读成「3D 失败」，`ok` 那条会误判 FAIL —— 字段混用比没有这个字段更危险。
- 现状（本提交后）：
  - `last3dError`：**只放三态** `webgl-unavailable` / `dem-missing` / `texture-fail <ok>/36`，贴面成功置 `null`；
  - `last3dWarn`：`dem-edge` / `dem-edge-mesh <n>`，**不影响成功态判定**，`getState()` 一并导出；
  - 贴面成功同时清 `last3dWarn`。
- 判据：`ok := last3dError === null`；局部降级看 `last3dWarn`，两者不互串。
- 另清掉 `205` 行粘错的重复注释（`var meshNull = 0;` 后误粘了 `last3dError` 的旧说明，定点 edit 的痕迹，不影响运行）。

## 1.6 本提交构建指纹

```
commit          9b7a486 + 本笔（feature/地图-m6-3d）
APK             8,595,333 B
apk.sha256      DF064302AD4932C9680BDE81EF5D26A6A9B523DAB2F48FD11C911ACC4093ADF7
zip 条目        518
真机            已由 LC测试 在 0113714 构建上复验（sha f31603d8… / lastUpdateTime 03:03:58），
                证据 docs/qa/m6-stage2-verify.md（feature/lc-test-m4-e2e = cfb5835）；实测值见 §2.1
```

### 1.6.1 给测试的注入口径（审地图 指出的缓存坑，必须照做）
`demTile` 命中 `DEM[k]` 就直接 `resolve`，**不发网络请求**；只改 `TERRARIUM` 不会失败。三句一起发：
```
js("TERRARIUM='https://127.0.0.1:1/'; DEM={}; DEM_PENDING={}; DEM_ORDER=[]; ensurePatch(true)")
```
恢复：改回 `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/` 并再清三张表，或重载页面。

## 2.1 真机复验实测值（LC测试，0113714 构建）

`
成功         last3dError=null              warn=null        mode=3d tex=true
dem-missing  last3dError="dem-missing"     warn="dem-edge"  mode=3d tex=true  demZoom=11
texture-fail last3dError="texture-fail 0/36" warn=null     mode=3d tex=false demZoom=11
恢复后       last3dError=null              warn=null        mode=3d tex=true
`

### 一条必须写进判据的行为（与之前口头推断不一致，以实测为准）
dem-missing **不回落、也不清空 patch**：mode 仍为 3d、hasTexture 仍为 	rue、demZoom 仍是**上一块**的值。
原因是代码在 startPatch 失败分支里只 loadingKey = null; return;，**旧的 patch 对象原样留着**，渲染继续用上一块地形。
因此判据**不能**写「dem-missing ⇒ patch == null / 灰底」——那会把正确实现判 FAIL。正确写法：
- mode === "3d"；
- last3dError 前缀匹配 dem-missing；
- **patch 保持上一块**（hasTexture 与 demZoom 不变），画面继续显示旧地形。

warn="dem-edge" 在该档出现是因为沿用旧 patch 时相机目标落到了旧块边缘，属 §1.5 定义的局部降级，**不影响成功态判定**（这正是拆字段的目的）。
webgl-unavailable 无法注入（需 GPU 不可用），仍按代码路径登记，不写「已验证」。
