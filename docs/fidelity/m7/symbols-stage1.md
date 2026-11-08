# M7 A轮 · 两个符号取证结案

> 对照基线 **原版 1.6.1**。取证手段：①原版 APK 全量代码/资源检索；②原版真机截图逐张像素检测（`docs/fidelity/orig/` 55 张）。
> 开工 `14:46` / 收工 `15:2x`。

---

# 一、屏幕中心符号 —— **原版有（像素证据），已量化，可实现**

## 1.1 形状（5× 放大目视 + 像素扫描，两份证据一致）

**四段互不相连的圆头短蓝线（上/下/左/右）+ 正中一个红色圆点。** 不是十字线（四段在中心处断开，中间留给红点），不是 drawable 三角形。

放大图：`docs/fidelity/m7/center-cross-zoom5x.png`（原图 `map-home-01.png` 中心 180×180 区域 5× 放大）。

## 1.1b 素材对照：`cross.png` 形状一致，但**不能直接复用**（`m7/symbol-assets-compare.png`）

原版 web 素材里有 `assets/webapp/assets/imgs/cross.png`（**52×52**，621 B），目视**四段圆头线 + 中心红点**——与实测形状**完全一致**。但三条理由决定不能直接贴图：

| 项 | 素材 `cross.png` | 截图实测 | 结论 |
| --- | --- | --- | --- |
| 尺寸 | 52×52 | 整体 114×114 设备 px | 需放大 2.19×，非 1:1 贴图 |
| 蓝线颜色 | `rgba(100,136,252,255)` = `#6488FC` | **`#009DFF`** rgb(0,157,255) | **颜色不同** ⇒ 运行时重新着色过 |
| 引用 | **只被 `global-population` 子页 bundle 引用**（byte 级检索：主图 `index.946c1624e0.bundle.js` 中 `cross.png` 出现 **0** 次；`mem_smali` 全树 0 次） | 主地图 21 张截图可见 | **主图不引用该素材** |

⇒ **按可观测像素重画（`#009DFF` + `#FF5163`），不要直接复用 `cross.png`**——直接贴会得到偏紫的 `#6488FC`，且尺寸差 2.19 倍。

## 1.1c 罗盘态也显示（`@LC地图` 核出，记入本文件避免两处各写一半）

除 `map-home-01` 等地图态外，**`compass-main-01.png` 也 HIT**（红点 138、四臂 40/41/41/41）⇒ 罗盘态下该符号同样显示。结合 §1.3 的「弹层/面板态 NONE」，**显隐条件是「主视图（地图态或罗盘态）显示、弹层/子页隐藏」**，与是否打开罗盘无关。

## 1.2 量化（原版真机 1228×2700，density 3.375）

| 项 | 设备 px | 折算 dp | 颜色 hex / RGB |
| --- | --- | --- | --- |
| 中心红点直径 | **13 × 13** | 3.85 | **`#FF5163`** = rgb(255,81,99) |
| 红点圆心 | **(614, 1350)** | — | **正好等于屏幕中心**（1228/2, 2700/2） |
| 每段蓝线长度 | **≈57** | ≈16.9 | **`#009DFF`** = rgb(0,157,255) |
| 蓝线粗细 | **7** | 2.07 | 同上 |
| 整体跨越（bbox） | **114 × 114** | ≈33.8 | — |
| 端点形状 | 圆头（round cap） | — | 由放大图可见 |

扫描原始输出（`map-home-01.png`）：

```
蓝色像素数 1063  bbox (557,1293,671,1407)
红色像素数 16    bbox (608,1344,620,1356)  宽 13 高 13
红点中心 614,1350        屏幕中心 614,1350
红点中心 RGB (255,81,99)
蓝线 RGB 样本 (0,157,255) (0,157,255)
水平线所在行：y 1347..1353（中心 y=1350）
垂直线所在列：x 611..617（中心 x=614）
```

## 1.3 出现条件（55 张原版截图逐张检测，判定式见 §3.2）

脚本实测汇总（`symbols-detect.py`）：**HIT=21 / NEEDLE=1 / NONE=33**。

- **有 = HIT（完整 4 臂 + 红点，L/R/U/D 均 ≈40、红点 ≈137 px）21 张**：`map-home-01`、`map-beijing-z12`、**`map-3d-mode`**、`poi-select-01/02`、`search-result-crs-01`、`right-3d`、`right-explore`、`map-mountains-entry`、`map-poi-sheet-01`、`favorite-list-01..04`、`favorite-mode-02`、`favorite-save-01`、`favorite-save-login-01`、`tool-favorite-01`、`tool-measure-01`、`compass-main-01`、`drawer-menu-01`
- **无 = NONE 33 张**：启动页、`more-sheet-*`、`map-layer-sheet`、`map-overlay-sheet`、`w3-sheet`、`search-result-01/02`、`search-typed-coords-01`、`setting-map-01`、`map-explore-page`、`tool-contour-01`、`tool-elevation-01`、`tool-hdmap-01`、`tool-track-01`、`tool-compass-01`、`map-jump-beijing`、`favorite-addpoint-01` 等
- **NEEDLE 1 张**（`win-02-compass`）：仅上/下臂（U/D≈31）命中
- 另有 **6 张「仅上下臂较长」**（`compass-overlay-hidden-map`、`drawer-menu`、`right-layer-sheet`、`tool-brush`、`tool-compass-page`、`tool-fishing`，U/D=31、L/R≈18）：形态是**罗盘竖向指针 + 少量横向元素**，按脚本判定式归 NONE（不满足「L/R<15」的纯针形条件）。**它们不是中心符号**，别误当「有」。

⇒ **结论**：该符号是**地图主视图的常驻元素**，2D 与 **3D 都有**；弹层/面板/子页面态不显示。
⇒ **与 bearing 无关**：`right-3d`/`right-explore` 与 `map-3d-mode` 里形态一致（4 臂等长、无旋转迹象）；且它钉在屏幕中心（不随地图拖动移动）——因为 23 张不同地图位置/缩放的截图里圆心恒为 (614,1350)。

## 1.4 归因（谁画的）—— **未落到项目代码，判「引擎/原生覆盖层绘制」**

已检索面与结果：

| 检索面 | 目标 | 结果 |
| --- | --- | --- |
| 主图 bundle `index.946c1624e0.bundle.js` | `009dff` / `0,157,255` / `crosshair` / `ff5163` / `255,81,99` | **全部 0** |
| 全部 webjs/css（含 vendor） | 同上 | 仅 `lib/mapbox-gl.js`+`css/mapbox-gl.css` 有 `crosshair`（是 `cursor:crosshair`，4 处） |
| `mem_smali` 全树 | `0x009dff` / `0xff009dff` / `-0xff6201`（0xFF009DFF 的有符号形式）/ `9dff` | 仅 1 处 `0x60fe9dff`，是 **Compose 编译器的 group key 哈希**，非颜色 |
| `decoded/res` | `9dff` / 类名含 `cross`/`center` | `colors.xml` 无 `9dff`；`#FF5163` 出现在 `colors.xml`(`status1BgColor/status1TextColor`) 与 `drawable/svg_north_needle.xml`（**北针**填充色）；无 `CenterCross` 类 |
| 资源可用性 | `svg_topic_center_cross.xml`（62dp 十字+中心圆） | **全 DEX 零引用**（仅 `R$drawable` 声明），且其填充色是 `#000000`、尺寸 62dp≠33.8dp ⇒ **不是本符号** |

⇒ 该符号既不来自项目 web 代码，也不来自项目 Kotlin/Java 字面颜色，**最可能是地图渲染引擎（WebView 内的第三方地图 SDK）或原生覆盖层自带**。**具体绘制类本轮未定位**——按纪律记「未取证（绘制归属）」，但**像素规格已完整**，可直接按规格实现。

## 1.5 给实现方的规格（可直接用）

```
容器：屏幕正中，钉死不动（不随地图拖动/缩放/bearing 变化）
绘制：4 段圆头线 + 1 个实心圆
线：颜色 #009DFF，粗细 2.07dp，每段长 16.9dp，从中心向外留 gap（红点半径 + 一点）
点：颜色 #FF5163，直径 3.85dp，圆心 = 屏幕中心
整体跨度：33.8dp
层级：在地图之上、工具条/弹层之下（弹层出现时不显示）
```
**校验口径（可脚本复跑）**：在 1228×2700 截图上，中心 (614,1350) 直径 13px 内应检出 ≈137 个 `#FF5163` 像素；沿中心行/列向外 8..70px 各应检出 ≈40 个 `#009DFF` 像素；四臂长度差 ≤2px。

---

# 二、当前位置符号 —— **本轮检索面内：无**（建议结案，附唯一未覆盖面）

## 2.1 代码检索（全部零命中）

| 检索面 | 关键词 | 结果 |
| --- | --- | --- |
| 主图 bundle | `GeolocateControl` / `geolocate` / `geolocation` / `userLocation` / `watchPosition` / `getCurrentPosition` / `当前位置` | **全 0** |
| 全包（含 vendor） | `userLocation`=9、`MyLocation`=2、`geolocate`=96 | **全部落在 `lib/mapbox-gl.js`(.map) 与 `css/mapbox-gl.css`** ⇒ 第三方能力，未使用 |
| 资源引用 | `svg_locate`(`0x7f0803db`) | 被 `ui/compose/p1.1.smali:604`、`ui/tools/E1.smali:16470` 引用 ⇒ **工具按钮图标**（24dp 圆环+四向箭头），不是地图符号 |
| 资源引用 | `svg_locate_0/1/2` | **零引用**（定位按钮动画帧） |
| 资源引用 | `svg_location`(`0x7f0803df`，15×18dp 地图针) | 仅 `ui/pages/WeatherActivity.smali:8106` ⇒ **天气页城市图标** |
| 资源引用 | `svg_topic_center_cross` | **零引用** |

## 2.1b 素材对照：`locationPointer.png` 存在，**但主图不引用**

原版 web 素材 `assets/webapp/assets/imgs/locationPointer.png`（**68×68**，1978 B）= **经典定位点**：蓝色实心圆 + 白色描边 + 圆上方一个小三角（朝向指示）。见 `m7/symbol-assets-compare.png` 第 1 格。

| 项 | 事实 |
| --- | --- |
| 素材 | 存在，68×68，样式就是「当前位置点」 |
| 引用面 | 被 **8 个子页 bundle** 引用：`973.bundle`、`AirportMap`、`EarthQuakeMap`、`globalLandCover`、`HarborMap`、`ScenicMap`、`special`、`global-population` |
| **主图** | `index.946c1624e0.bundle.js` 中 `locationPointer` 出现 **0** 次 |
| `mem_smali` | 全树 0 次（原生侧不引用该名字） |

⇒ **素材在、主图未使用**（与 `cross.png` 同类，但 `cross.png` 至少主图有像素；`locationPointer` 连像素都没有）。
⇒ 仍需设备窗口关闭「定位成功后是否出现跟随点」这一缺口（§2.3）。

## 2.2 截图检测

55 张原版截图中，**未发现任何“随坐标移动的定位点”形态**：中心符号是钉死不动的（§1.3 已证 23 张不同位置截图中圆心恒定），弹层/工具页均无独立定位点。

## 2.3 结论与唯一缺口（**已被 §2.4 实测推翻，作废**，保留作推导记录）

**原结论（作废）：原版在当前检索面内没有「随 GPS 移动的当前位置符号」**——代码零引用、55 张截图零观测。

**当时已写明的缺口**：如果原版**仅在“定位到当前位置成功后”才显示定位点**，而 55 张截图都没在该状态下拍，则可能漏掉；该缺口无法用静态手段关闭，需要一次设备窗口。

⇒ 该窗口已执行（`2026-11-08 15:0x`，@LC审核验），结果见 §2.4：**有**。原「原版不可得」结论**作废**。

## 2.4 【实测推翻 §2.3】当前位置点：**原版有**（定位成功后出现）

### 2.4.1 采集条件（如实记录）

- 包：`space.xinzhi.globe`，`versionName=1.6.3`，`lastUpdateTime=2026-11-07 13:31:33`（**版本漂移**，非 1.6.1 基线）
- `pm grant` 授予定位权限；`dumpsys location` 显示 network 与 gps 均 fix（hAcc=39m）；等 60 秒后截图
- 证据：`reports/M7-symbols/orig-gps-wait60.png` → 已入库 `m7/screens/orig-gps-located.png`

### 2.4.2 形状与位置

放大图：`m7/screens/location-dot-zoom6x.png`（中心 ±80px，6× 最近邻）。

**形状 = 蓝色实心圆 + 白色粗描边 + 一枚同色小三角（朝向指示）**，与素材 `locationPointer.png` 同构。
**位置：地图坐标处（不是屏幕中心）**，精确色定位得蓝圆心 ≈ **(161, 1483)**（屏幕左下方），随地图/定位变化。

### 2.4.3 归因：**这次落到素材，且是精确色匹配**

| 证据 | 数值 |
| --- | --- |
| 素材 `locationPointer.png` 主色 | **`#0077FF` = rgb(0,119,255)**，白描边 `#FFFFFF`（素材统计：蓝 588 px、白 292 px） |
| 截图中**精确等于** rgb(0,119,255) 的像素 | **726 px**，bbox `(140,1467)-(183,1498)` 44×32 |
| 结论 | 该符号就是 `locationPointer.png` **被实际使用**的实例 |

⇒ **修正 §2.1b 的判读**：主图 bundle 里检索不到 `locationPointer` 字符串，但截图出现它的**精确色**726 px ⇒ 引用是**动态拼接**的，**字符串检索不到 ≠ 未使用**。这是本轮第二次「静态检索被像素推翻」。

### 2.4.4 量测（素材 ↔ 截图）

| 项 | 素材（68×68） | 截图（设备 px） |
| --- | --- | --- |
| 蓝 bbox | 28 × 42（竖向，三角在上） | **47 × 35**（横向，三角在左上）— 与 @LC审核验 独立测量一致（他 `(139,1465)-(185,1499)`、我 `(140,1467)-(183,1498)`，≤2px） |
| 白 bbox | 34 × 49 | 54×41（审核验）/ 58×29（我）——**两者不可作规格**，差异来自「三角是否计入 + 纯白 vs 含抗锯齿」 |
| 三角朝向 | **正上（12 点）** | **左上（约 10–11 点）** |

⇒ ① **朝向在本次观测中与素材不同**——但这是**单点证据（一张截图对一张素材）**，只证明了一次朝向，**不能写成「已证实会随 bearing 转」**，等级为「**高度可疑**」；要升到「已证实」只需一个数据点：把地图转 90° 再截一张，看三角是否同步转 90°。② 设备侧蓝填充 **47×35 px**，换算 CSS ≈ **14×10 px**（注意：因含三角与椭圆填充，不能简单当圆直径）。

**实现尺寸口径（以 @LC审核验 的紧检区为准，已交叉验证）**：蓝圆 **47×35**、白描边略大一圈、三角同色。
**不要用 58×29 那个高度**——那是我一侧阈值把三角排除后的结果，按它做会把三角漏掉。

### 2.4.5 仍未取证（不得据此实现方向语义）

| 项 | 状态 | 原因 |
| --- | --- | --- |
| 旋转角与 bearing/heading 的**定量关系** | **未取证** | 只有 1 张截图，无法建立「bearing=X° → 三角指向 Y°」映射 |
| 三角表示「设备朝向」还是「地图方位」 | **未取证** | 同上 |
| 移动时是否脉冲/拖尾 | **未取证** | 单帧 |
| 蓝圆心与地理坐标的偏移容差 | **未取证** | 需 `getState()` 读中心坐标对照 |

⇒ 实现口径：**形状/颜色/尺寸可做**；**旋转规则未定前不许自定方向语义**，按「接收 bearing 输入、默认朝上」实现并在台账写「旋转映射未取证」。

### 2.4.7 形状判定的硬判据：**连通域 = 2**（定案「圆 + 圈外独立小三角」，不是水滴）

@LC审核验 用椭圆拟合得出「无第三块、是水滴」。我改用**连通域标记**做判定（这是区分「水滴（连通）」与「圆+独立三角（分离）」的唯一清晰判据），结果**推翻水滴说**：

```
取精确蓝 #0077FF 掩膜（局部窗 110,1440–220,1540），8 邻域连通域标记：
蓝点总数 856 → 连通域 2 个
  #0 面积 789  bbox (140,1477)-(184,1499)  45 × 23   质心 (161.6, 1487.9)   ← 主体
  #1 面积  67  bbox (147,1466)-(160,1473)  14 × 8    质心 (152.4, 1470.2)   ← 独立小三角
```

⇒ **两块不连通**，`#1` 在主体**左上**、且完全位于主体 bbox 的上方（`y 1466..1473` < 主体顶 `1477`）。
⇒ 形状定案：**蓝色主体（45×23，纵横比 1.96）+ 白色描边 + 圈外一枚独立小三角（14×8）**。**不是水滴**。

> **尺寸口径以「精确色」为准（重要，别混用）**：本节 45×23 / 14×8 出自**容差阈值**（`r<25 && |g−119|≤14 && b≥240`，含抗锯齿边缘）；@LC审核验 用**精确等于 `(0,119,255)`** 得 **主体 44×22、三角 7×4、整体 44×32**。
> ⇒ **实现与判据一律用精确色口径：主体 44×22、三角 7×4、整体 44×32**；容差值只用于「拓扑判定（一块还是两块）」，**不用于定尺寸**。面积不跨脚本比较（789/67 vs 706/18 是阈值差）。
⇒ `#1` 与主体不连通的原因很具体：**白色描边从中间穿过**——三角画在描边外侧。

**三角方向（这次是可算的，不再是目视）**：

```
三角质心 − 主体质心 = (−9.2, −17.7)〔屏幕 y 向下〕
屏幕方向角（向上=0、顺时针为正）= atan2(−9.2, +17.7) = **−27.3°** ⇒ 向上偏左 27.3°
```

⇒ 这次角度**有确定性**（连通域质心差算出，不依赖阈值取点）；我先前那次「19°」作废，本条取代它。
⇒ 仍**给不出**「bearing=X° → 方向=Y°」的映射（不知道拍摄时 bearing），故实现口径不变：**接 bearing 输入、默认尖朝上、映射未取证**。

**给 @LC客户端 的最终形状口径（取代此前所有版本）**：
```
形状 = 蓝色椭圆主体（45×23 设备 px，≈13.3×6.8 CSS）+ 白色描边 + 圈外一枚独立小三角（14×8 设备 px，≈4.1×2.4 CSS）
颜色 = #0077FF 主色 + 纯白描边
三角方位 = 相对主体「向上偏左 27.3°」（本次观测）；实现按 bearing 输入、默认朝上
位置 = 地图坐标层，随拖动/缩放
```
**不要画成水滴**（尖端与主体必须分离、且分离处是白描边），也**不要画成「圆 + 贴着圆的三角」**——三角在描边**外侧**。

想用「圆外蓝点」把三角单独提取量化，**不成立**：把这枚符号的精确蓝点（847 px）按质心算距离，中位数 11.9、最大 23.2，取 `>1.6×中位` 当「圆外」得 96 点，但其 bbox `(140,1466)-(184,1495)` **横跨整个蓝 bbox 宽度**——原因是这枚标记的蓝色填充本身**偏椭圆**（bbox 47×35，宽>高），远处像素天然超阈值，被误当三角。所得「三角质心方向 19°」**不可信，不予采用**。

⇒ 结论：**三角的存在可由目视确认**（6× 放大图清楚可见，位于圆的左上、尖朝左上），**但其精确角度不由该法给出**。要量化旋转与 bearing 的映射，只能靠**同一位置多张不同 bearing 的截图**做序列比对。

---

# 六、贴地 canvas 里盘面占多少（回答 @LC地图 的「分支 A/B」，已定案 = **A**）

原版 `index.946c1624e0.bundle.js` 三处逐字：

```js
// ① 模块级常量（@39353）
let c = window.screen.width / 2 * 1.5,   // c = 0.75 × 屏宽（CSS）
    u = 1.2 * window.screen.width,       // u = 1.2 × 屏宽（CSS，缩放到 100 时的上限）

// ② 盘面绘制尺寸（@49552 getCompassParams）
size = (u - c) * this._compassScale / 100 + c

// ③ 画进 canvas（@41208 附近 updateCompass）
i = this.canvas.width / this.dpr          // 728 CSS
let a = (i - e) / 2, n = (o - e) / 2;     // e = size，居中
this.ctx.drawImage(this.compassUI.compassImg, a, n, e, e)   // 画成 e × e
```

代入本机（`window.screen.width` = 364 CSS）：

| 量 | 值 |
| --- | --- |
| `c`（盘面默认 CSS 宽） | **0.75 × 364 = 273 CSS** |
| `u`（scale=100 上限） | 1.2 × 364 = 436.8 CSS |
| `_compassScale` 初值 | **1**（构造器 `this._compassScale=1`） |
| `size` @scale=1 | 273 + (436.8−273)×0.01 = **274.6 CSS** |
| `size` @scale=0 / 100 | 273 / 436.8 CSS |
| canvas CSS 尺寸 | 728（= 2×364） |
| **盘面占 canvas 比例** | 273~275 / 728 = **37.5%** |
| **盘面 backing px（dpr=2）** | **546~549 px** |

⇒ **分支 A 成立、分支 B 排除**：盘面在 canvas 里按**自己的 `size`（≈273 CSS）**画，用 `(i-e)/2` 居中，**不是**铺满 728。
⇒ 即使缩放到上限 `scale=100`，盘面也只有 436.8 CSS < 728，**永远不会铺满 canvas**。
⇒ **@LC地图 的 800 px 导出 = 546 backing 的 1.47× 过采样，够用，不需要按 1456 重导。**

## 6.1 顺带一个可作判据的常量：**原版盘面直径 = 0.75 × 屏宽**

`c = screen.width/2*1.5` 即 **0.75 × 屏宽**。本机 0.75 × 1228 = **921 设备 px**，与我方现网实测的 **922 设备 px 盘径**一致（±1px）。
⇒ 这条给 @LC审核验 的 `Q1 盘径` 判据提供了**原版出处**：`盘径 = 0.75 × 屏宽`，不再是“观测定值”。
⇒ 也解释了为什么我方 922 是对的：它不是巧合，是照了同一个比例。

## 6.2 贴地盘面「随不随 bearing 转」（回答 @LC审核验 的 Q4）

原版有三层，**不能只答「随 bearing 转」**：

### ① canvas 内容自身会转，但转角不是 bearing

```js
// updateCompass（@41208 附近）
let {size: e, opacity: t, bearing: r} = this.compassUI.getCompassParams();
this.ctx.translate(s, l);
this.ctx.rotate(r * Math.PI / 180);      // ← 内容旋转
this.ctx.translate(-s, -l);
this.ctx.drawImage(compassImg, a, n, e, e);
```

```js
// getCompassParams（@49552）
bearing: this.compass.getRotation()      // ← 不是 map.getBearing()
```

### ② `compass.getRotation()` 来自 `orientation`，且按 mode 位与地图反向

```js
// updateOrientation（@43692）
let r = this.orientation, i = this.compassUI;
2 & this.mode && (i.setRotation(r),  this.map.setBearing(-r));
4 & this.mode && (this.map.setBearing(r), i.setRotation(-r));
1 & this.mode && this.map.setBearing(r);
i.setGround(8 & mode);  i.setLock({ value: 16 & mode });
```

| mode 位 | 地图 bearing | 盘面内容旋转 | 屏幕上的净效果 |
| --- | --- | --- | --- |
| `1` | `+r` | 不设（保持原值） | 盘面**随地图一起转** |
| `2` | `−r` | `+r` | **两者抵消 ⇒ 盘面在屏幕上不转** |
| `4` | `+r` | `−r` | **两者抵消 ⇒ 盘面在屏幕上不转** |

### ③ 四角坐标还被投影矩阵转一次

`coordinates` 来自 `unproject` 的**地理四角**，地图 bearing 会让这块 quad 在屏幕上整体旋转——所以屏幕总转角 = **地图 bearing（投影）+ canvas 内容旋转**。上表「抵消」正是为此设计。

## 6.3 Q4 的正确判据（给 @LC审核验）

**不能只写「bearing 0/90 时盘面跟着转」**，那会把「盘面朝上」的实现判成错。应按模式分：

| 我方实现目标 | bearing 0→90 的期望 |
| --- | --- |
| **盘面随地图转**（等价原版 bit 1） | 屏幕上盘面**转 90°** |
| **盘面保持朝上**（等价原版 bit 2/4） | 屏幕上是 0°（canvas 内需反向旋转 `−bearing`） |

⇒ 建议 Q4 判据写成：**「bearing 0 与 90 两张贴地截图，盘面的「天池—外圈」方向向量夹角 = 90°（随地图转）或 0°（朝上不变），二选一必须与实现声明一致；不允许出现 ±45° 这类既非 0 又非 90 的结果」**——后者说明转了两遍或漏了一半。

## 6.4 原版贴地的**范围门控**（这条地图侧可能没有）

```js
// updateCompass 开头（@41208 附近）
let e = this.map.getCenter().lat, t = this.map.getZoom(),
    r = 75 > Math.abs(e) && t >= 6 && this._isGround;
if (this.compassUI.compassImg && r) { …绘制… }
```

⇒ **盘面只在「|纬度| < 75° 且 zoom ≥ 6 且 ground 开启」时才画**。超范围时 `if` 不成立，**canvas 不重绘**（保留上一帧内容或空白）。
⇒ 这是可判的边界行为：把地图移到纬度 76° 或 zoom 降到 5，原版**不再绘制贴地盘面**。建议进 @LC审核验 的判据（边界态）。

## 6.5 三个尺寸的确切出处（回答 @LC地图：`u` **不是画布**）

逐字（`index.946c1624e0.bundle.js`）：

```js
// @39353 —— 模块级常量
let c = window.screen.width / 2 * 1.5,   // c = 0.75 × 屏宽  → 盘面 CSS 尺寸
    u = 1.2 * window.screen.width;       // u = 1.2 × 屏宽   → 盘面在 scale=100 时的上限【不是画布】

// @51779 createCompass
t.style.width = `${c}px`;                // 盘面 DOM 宽 = c（默认 273 CSS）

// @49552 getCompassParams
size = (u - c) * this._compassScale / 100 + c;   // 盘面绘制尺寸（CSS），scale 初值 1 → 274.6

// @42203 —— 地面画布类构造
let r = this.size = Math.floor(2 * window.screen.width),   // 画布 CSS = 2 × 屏宽 = 728
    i = this.dpr  = Math.min(Math.floor(window.devicePixelRatio), 2);   // = 2
o.width = r * i; o.height = r * i;       // backing 1456 × 1456
o.style.width = `${r}px`;                // CSS 728

// @41208 updateCompass —— 盘面在画布里居中、按 size 绘制
let a = (i - e) / 2, n = (o - e) / 2;    // e = size
this.ctx.drawImage(compassImg, a, n, e, e);

// @41208 getCoordinates —— 地面足迹半径
radius = Math.floor(window.screen.width / pixelsPerMeter);   // 用 screen.width，不是 c
```

### 三个数各是什么（**别混用**）

| 符号 | 表达式 | 本机值（CSS） | 是什么 |
| --- | --- | --- | --- |
| `c` | `screen.width/2*1.5` | **273** | **盘面**默认尺寸 |
| `u` | `1.2 × screen.width` | **436.8** | **盘面**在 scale=100 时的上限（**不是画布**） |
| `size` | `(u−c)·scale/100 + c` | 274.6（scale=1） | **盘面**在纹理里的绘制尺寸 |
| 画布 | `2 × screen.width` | **728** | canvas CSS 尺寸（backing 1456） |
| `toBounds` 半径 | `screen.width / pixelsPerMeter` | 364 CSS 当量 | 地面足迹**半**边长 ⇒ 边长 728 = **画布** |

⇒ **盘面 / 画布 = 273 / 728 = 0.375**（backing：546 / 1456）。
⇒ `toBounds` 的半径对应的是**画布**（728），**与盘面尺寸无关**——这条最容易被误当成“半径按盘面算”。

### 6.5.1 给 @LC地图 的结论：画布 728 是对的，**要改的是盘面占画布的比例**

| 项 | 你现状 | 应为 |
| --- | --- | --- |
| 画布 | 728 CSS（=2×屏宽） | **不变，正确** |
| 盘面绘制尺寸 | `drawCompass(ctx, sz.px)` = **画满整张纹理**（1456 backing） | **`drawCompass(ctx, 546)` 居中**（= 0.375×1456） |
| `toBounds` 半径 | —— | 仍用 `screen.width / pixelsPerMeter`（对应 728 画布） |

自洽验算：`0.375 × 728 CSS = 273 CSS` ⇒ 屏幕 273×3.375 = **921 设备 px** ✓（正是盘径目标）。
**而且这个 921 只在 pitch = 0 时成立**——贴地平面在 pitch>0 会被透视压缩，屏幕上会变小，**不能拿 921 去判 pitch>0 的贴地截图**（见 §6.6）。

### 6.5.2 `e` 的精确值：**不是 `c`，是 `c + (u−c)·scale/100`**

@LC地图 问「`e` 是否就是 `c` = 273」。**不是**，逐字是 `getCompassParams().size = (u - c) * this._compassScale / 100 + c`，而构造器 `this._compassScale = 1`（**初值 1，不是 0**）：

| `_compassScale` | `size`（CSS） | backing（×2） |
| --- | --- | --- |
| 0 | 273.0 | 546.0 |
| **1（默认）** | **274.638** | **549.28** |
| 100 | 436.8 | 873.6 |

⇒ **默认态应为 549 backing，不是 546**；差 0.58%（`546` 是 scale=0 的值）。若实现按固定 546，属可接受近似，但**台账要写清是近似**；严格实现请按公式取值。
⇒ `dialBleed` 成立：原版是 `drawImage(img, a, n, e, e)` **整幅贴、无内缩**；`r = min(cx,cy) − 8f` 那套是**原生 `CompassView`** 的内缩，画布路径不该带。

## 6.6 Q1「盘径」判据的适用边界（重要，避免误判）

`盘径 = 0.75 × 屏宽` 这条**只对两张图成立**：

| 场景 | 屏上盘径 | 说明 |
| --- | --- | --- |
| 原生盘面（不贴地）/ 贴地且 **pitch = 0** | **921 设备 px**（0.75×1228） | 1:1 投影，纹理与屏幕同尺度 |
| **贴地且 pitch > 0** | **小于 921**，随 pitch 增大而压缩 | 地面平面透视压缩，是「贴地」的应有表现 |

⇒ **@LC审核验：Q1 不能拿 921 去判 pitch>0 的贴地截图**；pitch>0 时应改为判「盘面中心 = 屏幕中心（或按 quad 中心偏移）」+ 「盘面随 bearing 的转角」（§6.3 那张表），尺寸判据只在 pitch=0 档使用。
⇒ @LC地图 你算出的「画满纹理时投影 525 CSS = 1774 device px」是**盘面占满画布**的必然结果（1.93×）；改按 0.375 后 pitch=0 给出 921、pitch=30 给出约 665，两者都对。

## 6.7 贴地层与浮层盘面是**互斥**的（原版出处，解决「两层盘面」）

逐字（`index.946c1624e0.bundle.js` @41924，贴地层类的 `update`）：

```js
p(this, "update", () => {
    if (this.isLock) return;
    let e = this.map.getCenter().lat,
        t = this.map.getZoom(),
        r = 75 > Math.abs(e) && t >= 6 && this._isGround;   // 贴地层是否可见
    this.setVisible(r);                              // 贴地层显隐
    this.compassUI.setCompassVisible({ value: !r });  // ← 浮层盘面：与贴地层【取反】
});
```

⇒ **原版在「贴地层可见」时，把浮层盘面隐藏**（`value: !r`）。**两者互斥，不是叠加。**
⇒ 而且**互斥与显示用的是同一个门控** `r = |lat| < 75 && zoom ≥ 6 && _isGround`——一个条件同时决定「贴地层亮」和「浮层灭」。

### 6.7.1 `setCompassVisible` 只隐藏**盘面图**，不隐藏十字线与指针

```js
p(this, "setCompassVisible", ({value: e}) => {
    this.compassImg && (this.compassImg.style.display = e ? "block" : "none");
});
```

⇒ 它只改 `compassImg`（盘面位图）的 `display`。**十字线（`compassLine`）与竖向指针（`pointer` img）都不受它控制**——
- 十字线：viewport 层，贴地态**仍显示**；
- 指针：`pointer` 独立 img，`pitchAlignment: "map"`（躺在地面上），贴地态**仍显示**，由 `setPointerVisible` 单独控制。

### 6.7.2 对我方 B1 的措辞修正（更准，不是「未取证」）

我方曾以「做不到倾斜」为由选择**贴地态隐藏指针**（B1，总监已批）。但按本节证据：**原版贴地态指针是显示的，并随地面倾斜**。
⇒ 台账应写：「**原版贴地时指针仍显示且随地面倾斜（`pitchAlignment:"map"`）；我方因原生 ImageView 无法倾斜，选择隐藏——属实现限制下的差异，非原版行为**」。
⇒ 这比原先「原版贴地时指针形态未取证」更准确——**原版怎么做的已经取证了**，未取证的是它的具体倾斜视觉效果（无 L1 截图）。

## 6.8 根因：原版**不用 `file://`**，用 `WebViewAssetLoader`（一次解掉 fetch 被拒 + canvas 污染）

@LC审核验 的 `Tainted canvases may not be loaded` 与 @LC地图 的 `fetch` 404/被拒，**是同一个根因**：我方页面跑在 `file:///android_asset/` 下。

原版逐字（`map/MapWebView.smali:185-233`）：

```smali
new-instance v4, Landroidx/webkit/WebViewAssetLoader$Builder;
invoke-direct {v4}, Landroidx/webkit/WebViewAssetLoader$Builder;-><init>()V
new-instance v5, Landroidx/webkit/WebViewAssetLoader$AssetsPathHandler;
invoke-direct {v5, v3}, Landroidx/webkit/WebViewAssetLoader$AssetsPathHandler;-><init>(Landroid/content/Context;)V
const-string v0, "/assets/"
invoke-virtual {v4, v0, v5}, ...addPathHandler(...)     → /assets/ → APK assets
const-string v0, "/res/"
invoke-virtual {v4, v0, v5}, ...addPathHandler(...)      → /res/    → APK res
... InternalStoragePathHandler(context, filesDir)        → 内部存储
.field private final assetLoader:Landroidx/webkit/WebViewAssetLoader;
```

页面地址（`MainActivity$P$a$d$a` 内的常量，与 `H5Helper` 一致）：

```
https://appassets.androidplatform.net/assets/webapp/index.html
```

⇒ 原版页面来源是 **`https://appassets.androidplatform.net/...`**（`WebViewAssetLoader` 的默认域），**不是 `file://`**。
⇒ 于是原版：
- `fetch('map/…json')` **可用**（同源 https，不是 file 源）；
- `<img src="assets/imgs/compass/compass.png">` 画进 2D 画布**不污染**（同源），`texImage2D` **正常**。

### 6.8.1 三条修法对比（推荐第 1 条）

| 方案 | 解决 fetch | 解决污染 | 与原版一致 | 备注 |
| --- | --- | --- | --- | --- |
| **① 上 `WebViewAssetLoader`，页址改 `https://appassets.androidplatform.net/assets/…`** | ✅ | ✅ | **✅（原版做法）** | 跨模块（客户端/地图），一次解两因 |
| ② `data:` 内联图片/JSON | ✅ | ✅ | ❌ 绕开 | 地图已做，可作过渡 |
| ③ `setAllowUniversalAccessFromFileURLs(true)` | ✅ | ✅ | ❌ 且**放松安全** | 不建议 |

⇒ **建议按 ① 做**：不仅因为它是原版做法，更因为它把「`file://` 特有的两道限制」一起消掉；②③ 都是在跟 `file://` 搏斗。

## 2.6 【补 §2.4.5】定位点旋转的定量关系 —— **已取证，不需要设备**

原版定位点实现在共享 chunk **`973.b4246bb948.bundle.js`**（主图 `index.946c1624e0.bundle.js` 引用 `973` 1 次）。逐字：

```js
_init() {
    let t = document.createElement("img");
    t.src = "./assets/imgs/locationPointer.png";
    t.className = "location-icon";
    return new Marker(t, {
        anchor: "center", occludedOpacity: 1,
        pitchAlignment: "map",
        rotationAlignment: "map"        // ← 旋转相对「地图」
    });
}
setBearing: ({ bearing: t }) => {
    this._targetRotation = t;
    if (!this._bearingFrame) {
        this._bearingFrame = requestAnimationFrame(() => {
            let t = this.map.getBearing(),
                e = this.toFixedN(this._targetRotation - t, 2);   // ← 减掉地图 bearing
            emit("setOrientation", { value: this.toFixedN(this._targetRotation, 2) });
            this._marker.setRotation(e);                          // marker 旋转 = 差值
            this._bearingFrame = null;
        });
    }
}
```

### 2.6.1 换算关系

`rotationAlignment:"map"` ⇒ 最终屏幕角 = **地图 bearing + marker 旋转值**，代入得：

```
屏幕角 = mapBearing + (target − mapBearing) = target
```

⇒ **三角指向角 = 传入的 `bearing` 值本身**，与地图 bearing 抵消无关。

### 2.6.2 正负号（顺时针还是逆时针）

MapLibre `Marker#setRotation(deg)` **顺时针为正**；素材 `locationPointer.png` 三角在**正上方**（§2.4.4）。
⇒ **`指向角 = +bearing`，顺时针，`0` = 正上方 = 正北**。**不是 `−bearing`。**

**交叉验证**：实测三角「向上偏左 27.3°」= 屏幕角 **−27.3° ≈ 332.7°** ⇒ 当时 heading ≈ 332.7°（NNW）。与 `target − mapBearing` 自洽。

### 2.6.3 是否随 2D/3D 或 bearing 变

| 因素 | 改不改指向角 | 依据 |
| --- | --- | --- |
| 地图 bearing | **不改** | `target − mapBearing` 被 `rotationAlignment:"map"` 加回 |
| 2D / 3D(pitch) | **不改角度**，只改形状（`pitchAlignment:"map"` 随地面倾斜压扁） | 两个 alignment 都是 map |

### 2.6.4 驱动来源

原生 `MainActivity.onSensorChanged` → `MapWebApiKt.setBearing(controller, heading)`（`MainActivity.smali:27567`），命令串 **`"setBearing"`**、载荷 `{"bearing": f}`；JS 侧 `973` 注册 `register({setBearing, setFollowMode})` 接住 ⇒ **三角指向 = 设备 heading**（绝对方位）。

### 2.6.5 给 @LC客户端 的实现口径（最终）

```
输入 = 设备 heading（度，0=正北，顺时针为正）
三角屏幕指向角 = +heading，0 = 正上方
与地图 bearing 无关；2D/3D 只改形状不改角度
```

# 七、检索复现命令（审核验可直接照跑）

## 3.1 代码检索

```powershell
# 主图 bundle 关键词计数
python -c "t=open(r'E:\APPDetector\output\.inspect\unpack\decoded\assets\webapp\js\index.946c1624e0.bundle.js',encoding='utf-8',errors='replace').read();
[print(k, t.count(k)) for k in ['GeolocateControl','geolocate','geolocation','userLocation','watchPosition','getCurrentPosition','009dff','crosshair','ff5163']]"

# 全 webapp 关键词（区分 vendor 与自有代码）
# 见 docs/fidelity/m7/symbols-search.ps1
```

## 3.2 截图检测判定式（§1.3 的来源）

对 `docs/fidelity/orig/*.png` 逐张：
- 红点：中心 13×13 内满足 `r>180 && g<110 && b<130` 的像素数 ≥ 40；
- 蓝臂：沿中心行/列向外 8..70px，满足 `b>180 && 120<g<190 && r<70` 的像素数，四向各 ≥ 15；
- 四项齐全 → 记「有」；仅 U/D → 记「罗盘竖向指针」；全 0 → 记「无」。

脚本：`docs/fidelity/m7/symbols-detect.py`

---

# 四、对我方实现的三条硬话

1. **屏幕中心符号可以做，且必须按 §1.5 的量化值做**——不是“照感觉画个十字”，颜色 `#009DFF`、红点 `#FF5163`、33.8dp 跨度、2.07dp 线宽、3.85dp 点径，任一项偏差 >10% 即判不符。
2. **它是地图态常驻、弹层态隐藏**（§1.3），不是罗盘态专属；2D/3D 都要有。
3. **当前位置符号不进实现**（§2.3），除非设备窗口关闭那个缺口。

# 五、时间戳

| 步骤 | 时间 |
| --- | --- |
| 代码/资源检索（含归因） | 14:46–15:05 |
| 截图逐张检测（55 张） | 15:05–15:12 |
| 5× 放大目视 + 精确量化 | 15:12–15:20 |
| 文档落盘 | 15:20–15:28 |

开工 `14:46` / 收工 `15:28` / 返工 0。
