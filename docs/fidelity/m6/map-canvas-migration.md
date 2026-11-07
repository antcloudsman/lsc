# 第 7 条 A 路线：盘面绘制迁入 map.html canvas（迁移方案，待批）

作者：LC审地图　｜　基线：`origin/develop`（M6 Stage 2 期间）　｜　状态：**方案，未动代码**
目的：让同一张盘面 canvas 既能作屏幕浮层，又能作为 canvas 纹理按四角投影铺地（第 7 条「罗盘贴地」）。
依据：逆向 `docs/fidelity/m6/reverse-stage1.md` §7.1（原版 `compass` = `{type:"canvas"}` source + `compass_ground` raster 图层 + 四角 `coordinates`）。

## 1. 迁移范围

**迁**：`CompassView.java` 的绘制面（`onDraw` 及其调用链）。
**不迁**：`onTouchEvent`（53 行，`CompassView.java:247-299`）。它依赖 View 坐标系、`getWidth()/getHeight()`、`touchSlop`，且有一条关键语义——**圆内消费触摸、圆外返回 `false` 交给下层地图**。搬进 WebView 要把这条改成 JS 命中测试 + `pointer-events` 区域控制，风险高于收益。建议保留 `CompassView` 作为**透明触摸层**（`onDraw` 空实现 + 仍处理 `onTouchEvent`），绘制交给 canvas。

## 2. 图元清单（量化，源：`CompassView.java` 549 行）

| 图元 | canvas 调用点数 | 覆盖行 | 说明 |
|---|---|---|---|
| `drawLine` | 12 | 331/346/352/359/374/420/426/428/469/477/491 等 | 5° 刻度(72)、30° 长刻度(12)、分金、72 龙、60 龙、天支线、十字、中心轴 |
| `drawCircle` | 4 | 161/162/314/418 | 外圈描边、双环、中心小圆 |
| `drawPath` | 1 | 435 | 中心/顶部三角（`triangle(cx, top, half)`，半宽 10、高 16） |
| `drawBitmap` | 1 | 146 | 盘面位图（`dial_full_0..4`），随 `heading` 经 `save/rotate/restore` 旋转 |
| `drawText` | 定义 2 处（512/516），调用 10 处 | — | 度数（12 个）、山名、卦名、龙名等 |
| `save/rotate/restore` | 1 组 | 137/138/147 | 仅作用于位图层 |

绘制相关代码规模：`onDraw` 155 行（301–455）+ `drawArtwork` 18（135–152）+ `drawOutlineRings` 38（154–191）+ `drawTianzhiLines` 36（457–492）+ `drawText` 15（503–517）+ `triangle` 8（519–526）+ `pos` 4（498–501）+ `wuxingColor/guaWuxing` 19（528–546）≈ **293 行，占 549 行的 53%**；canvas 调用点 **21 个**。

**依赖数据**（必须同源，否则 JS/Java 两套表会漂移）：
- `CompassData`：`MOUNTAINS / WUXING / YINYANG / HOUTIAN / XIANTIAN / JIAZI / NAYIN` 七个字符串表、`RINGS`（`RingDef[]`，含 `interval/offset/labels`）、`FENJIN_STEP / DRAGON72_STEP / DRAGON60_OFFSET / DRAGON60_STEP`。
- 资源：`res/drawable-nodpi/dial_full_0.png … dial_full_4.png`（长边 ≤1200 采样），以及用户导入盘面（`setArtworkPath`）。

## 3. 三个必须先定的技术选择

**（a）分辨率：不能用 `clientWidth × dpr`。**
`map.html` 的 `resizeGL()` 把 dpr 截到 `min(dpr, 2.5)`；盘面在屏上是 **922 device px**（`0.7378×1228+16`，CSS ≈263.4 at dpr 3.5）。若沿用该公式，backing 只有 `263.4×2.5 ≈ 658 px`，是原生 922 的 **71.4%**，文字必然糊。
⇒ 盘面 canvas 的 `width/height` 固定为 **922**，CSS 尺寸 `922/dpr ≈ 263.4px`，不走 `resizeGL` 那套。

**（b）数据单一化**：构建期由 `CompassData` 生成 `assets/compass/rings.json`（含 mechanics 同源 sha256 头），JS 只读这份；禁止在 JS 里手抄第二份表。

**（c）触摸层去留**：建议保留原生透明层（见 §1），不改触摸语义。

## 4. 像素一致性验证（三层，缺一不可）

- **L1 几何量**：以真机 `compass-main-01.png`（1228×2700）裁出 922×922 方块为基准，沿 0/45/90/135/180/225/270/315 八个方向做扫描线，量外径、三环半径、刻度角、文字基线。判据：**位置误差 ≤1px、角度误差 ≤0.5°**。
- **L2 全图像素**：同尺寸 922×922 双图 diff。判据：**差异像素占比 ≤2%、平均 ΔE ≤3**；关键色抽样（金 `#DCBE82`、刻度 `#A0875A`、72 龙 `#5A6E82` 等）**ΔE ≤2**。
- **L3 状态矩阵**：5 种盘面 × 天支线开/关 × `dialAlpha`(30%/100%) = **20 态**，每态过 L2。
- 文字区域单列：Android `Canvas` 与 `Canvas2D` 字形度量不同，L2 的超标像素预计集中在文字；若 L2 因文字超标，改用「文字区域掩膜 + 其余区域 ≤1%」判定，**不许直接放宽全图阈值**。

## 5. 分步（每步独立验收，未过不进下一步）

1. **数据与资源**：导出 `rings.json` + 5 张 `dial_full_N.png` 到 `assets/compass/`。验收：JS 读出的表与 Java 逐项相等（键序与数值同时比）。
2. **静态绘制**：`heading=0` 下复刻全部图元（不含旋转）。验收：L1 + L2。
3. **动态**：旋转、`dialAlpha`、天支线。验收：L3 20 态。
4. **贴地接线**：canvas → 纹理 → 四角投影铺到地面。验收：第 7 条判据（见 §6）。

## 6. 缺口（不补齐不许判「已修复」）

- **原版 `getCoordinates()` 的四角怎么算**：逆向只给了「四角 `coordinates` 投影成 raster」，没给这四角是「屏幕四角 `unproject`」还是「以盘面半径换算的地面范围」。**未取证**，第 4 步开工前必须补，否则贴地范围会凭猜。
- **贴地时盘面是否跟随地形起伏**：原版是平面四角投影（不随高程），我方若贴在 DEM 网格上会跟着山起伏——这会比原版更"真"，但**不是 1:1**。需总监按「至少 1:1」口径裁定：**按原版平面投影**（推荐，可判）还是**按地形起伏**（更好看，但判据无法与原版对照）。

## 7. 工作量口径

迁移主体 = 293 行绘制代码 → JS（`Canvas2D`），加数据导出与 20 态验证。**不含**触摸迁移、不含贴地四角取证。人日由实现方按本清单估，我不代估。
