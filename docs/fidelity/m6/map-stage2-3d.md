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
| `dem-missing` | 任一 DEM 瓦片取不到（`tiles[i] === null`） | 不回落，停在 3D 但无 patch（灰底） |
| `texture-fail <ok>/36` | 贴面瓦片成功率 `< 30/36` | 不回落，地形在、无影像贴面（走高程配色） |
| `dem-edge` / `dem-edge-mesh` | DEM **局部**缺角（`demAt` 返回 null） | 不是整块失败：沿用上一帧高度 / 计数上报 |
| `null` | 成功（贴面 ok 即清） | — |

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
