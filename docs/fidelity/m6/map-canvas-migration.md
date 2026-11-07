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

**（a）分辨率：按原版口径 `floor(2×screen.width)`，且 `screen.width` 是 CSS px。**
原版画布（逆向 §7.1/§7.5.3）：`size = floor(2 * screen.width)`、`ctx dpr = min(floor(dpr), 2)`；viewport 是 `width=device-width, initial-scale=1`，故 **`screen.width` = 364 CSS px**（1228 / 3.375），**不是 1228**。
⇒ `size = floor(2×364) = 728` CSS px；`dpr = min(floor(3.375),2) = 2` ⇒ **backing = 1456×1456 ≈ 8.5 MB**。
**本方案早前写「2456 / 24.1 MB」是把 `screen.width` 当设备 px，偏大 1.69×（面积 2.87×），已作废。**
对照：我方盘面 `CompassView` 是 **922 device px = 273 CSS px**；若用 `resizeGL()` 的 `clientWidth×dpr`（dpr 截 2.5），盘面 backing 只有 `263.4×2.5 ≈ 658`，仅 922 的 **71.4%**，文字必糊——所以屏幕浮层按 **922 device px** 定，不跟 `resizeGL` 走。
⇒ 定稿：**屏幕浮层 canvas = 922（device px，≈3.4 MB）；贴地纹理 canvas = 728 CSS / backing 1456（≈8.5 MB）**。两者差 2.5 倍，不是 7.1 倍。

**（b）数据单一化**：构建期由 `CompassData` 生成 `assets/compass/rings.json`（含 mechanics 同源 sha256 头），JS 只读这份；禁止在 JS 里手抄第二份表。

**（c）触摸层去留**：建议保留原生透明层（见 §1），不改触摸语义。

**（d）同一套绘制、两张 canvas 实例（贴地与屏幕显示两用）**
- 绘制代码**只有一份**：`drawCompass(ctx, size, heading, dial, alpha, tianzhi)`，尺寸与坐标系由 `size` 参数化（现 `CompassView` 的 `cx/cy/r` 全部按 `size` 比例算，`r = size/2 - 8*(size/922)`）。
- **实例 1（屏幕浮层）**：`922×922`，CSS `922/dpr`，居中，随 `heading` 旋转（等价现 `drawArtwork` 的 `save/rotate/restore`）。
- **实例 2（贴地纹理）**：`728` CSS px（原版 `floor(2×screen.width)`，`screen.width=364` CSS）⇒ backing `728×2 = 1456`（dpr 截 2），注册为 `canvas` source，`animate:false`（**不自动重绘，必须显式重绘后 `setCoordinates`**），`raster-resampling:"linear"`。
- **四角 `coordinates` 算法（已按逆向 §7.5 原版逐字改写，替换本方案早前的等价路径）**：
  原版 `getCoordinates()` = `map.unproject([screen.width/2, screen.height/2]).toBounds(floor(screen.width / pixelsPerMeter))`，输出顺序 `[NW, NE, SE, SW]`。
  三句拆解：①中心 = **屏幕中心**反投影经纬度（用 `window.screen.*`，不是 `innerWidth`）；②半径(米) = `floor(screen.width / pixelsPerMeter)`（**随缩放变**，不是固定米数）；③交给 MapLibre 当轴对齐 bounds。
  **不在此处做 bearing 旋转** —— 原版把正方形 bounds 交给 source，旋转由地图投影矩阵处理；我方若在四角里再转一次，就是转两遍（本方案早前的写法已作废）。
  与我方现有量的等价性：`pixelsPerMeter = 1/zMpp`，`zMpp = 156543.03392·cos(lat)/2^zoom` ⇒ `screen.width / pixelsPerMeter = screen.width × zMpp`。**对照点**：我方按此算出的米数必须能复现 `floor(screen.width / pixelsPerMeter)`。
  视野/缩放变化时重算并 `setCoordinates` —— 不重算就等于把盘面钉死在旧位置，拖地图时盘面不跟手。
- **已确认（原 `待确认`，逆向从原版自带 `lib/mapbox-gl.js` 定死）**：`LngLat#toBounds(radius)` 的 `radius` 是**半边长**（bounds = 中心 ± radius）。
  ⇒ 贴地四边形**边长 = 2 × 屏宽米数**；而画布 `size = 2 × screen.width`，两者相等 ⇒ **画布与地面正方形 1:1 对映**（不做二次缩放），这也解释了原版为什么把画布取成 2×屏宽。
  验收口径（可先跑，比逐像素省事）：**贴地四边形边长 ÷ `screen.width` 的米数当量 = 2**；pitch 0→60° 变化时，盘面绘制框屏幕宽度 ÷ 屏宽应基本不变；若出现梯形或四角不等距，就是误用了「屏幕四角 `unproject`」。
- 内存口径：两实例同存约 **8.5 + 3.4 ≈ 11.9 MB**；若紧张，可只在进入贴地时创建 1456 backing 那张、退出即释放。

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
