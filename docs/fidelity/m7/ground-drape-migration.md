# M7 A 轮 · 罗盘贴地迁移方案（A 路线：盘面搬进 map.html canvas 当栅格纹理）

> 归属：LC地图。状态：**方案定稿（2026-11-09 总监批 A1 / B1 / 800 基线），代码待实施**。
> 基线 `develop` = `ea82c77`（M7 收官顶点）。对照：原版 1.6.1（`index.946c1624e0.bundle.js` canvas source + `compass_ground` raster 图层）。
> 规则复用：不改冻结文献；证据与代码分离；实现方不自签。

---

## 1. 图元清单：谁在画 → 搬完谁在画

### 1.1 现在由原生 `CompassView.java`（544 行）绘制，**要搬进 canvas**

| # | 图元 | 当前实现（方法:行） | draw 调用 | 搬完后由 JS canvas 画 |
|---|---|---|---|---|
| 1 | 底盘位图（5 套盘面） | `drawArtwork` 135–153，`drawBitmap` + `save/rotate/restore` | `drawBitmap` ×1 | 是（位图需导出到 assets，见 §7 决策 A） |
| 2 | 外圈色环（金/多重环） | `drawOutlineRings` 154–192 | `drawCircle` ×4 + `drawLine` ×3 | 是 |
| 3 | 二十四山文字 + 五行着色（三盘） | `onDraw` 375–405 | `drawText` ×4（17sp/13sp/13sp/12sp） | 是（字色 `wuxingColor()` 一并搬） |
| 4 | 六十四卦名 | `onDraw` 406–415 | `drawText` ×1 | 是 |
| 5 | 刻度线（15°/每山） | `onDraw` 336–348 | `drawLine` ×1 + `drawText` ×1（11sp 度数） | 是 |
| 6 | 三盘分隔线 | `onDraw` 350–369 | `drawLine` ×3 | 是 |
| 7 | 三个 cell marker（彩点） | `drawCellMarker` 483–503，调用 371–373 | `drawCircle` ×3（内含） | 是 |
| 8 | 天支线（可选） | `drawTianzhiLines` 458–482 | `drawLine` ×n | 是 |
| 9 | 中心小圆 + 指北短线 | `onDraw` 424–430 | `drawCircle` ×1 + `drawLine` ×1 | 是 |
| 10 | 中心读数（`向 xx.x°` / 山·五行 / 坐·阴阳） | `onDraw` 441–452 | `drawText` ×3（30sp/17sp/13sp） | 是 |

**合计**：`drawLine` 10 处、`drawCircle` 4 处、`drawText` 12 处、`drawBitmap` 1 处、`save/rotate/restore` 1 组。
绘制相关代码 **约 259 行**（`onDraw` 148 + `drawArtwork` 19 + `drawOutlineRings` 39 + `drawTianzhiLines` 25 + `drawCellMarker` 21 + `drawText` 7）。

### 1.2 **不搬**，继续由原生绘制

| 图元 | 当前实现 | 理由 |
|---|---|---|
| 红色十字经线 + 中心/27% 处红三角 | `CompassCrosshairView.java`（78 行，`CSS_PX × density` = 6.75→7px，`TRIANGLE_TOP_RATIO 0.27`） | M6 第 1 条已真机验收（竖线 x612..616、横线 y1347..1351、交点 (614,1350)）；它是**屏幕层**，原版也是 viewport 对齐的 DOM 元素，不进地面栅格 |
| 竖向指针 `pointer.png` | 客户端 `pointerView`（M6 `d28cb9f` 门控已收口） | 原版指针是独立 DOM `<img>`（`pitchAlignment:"map"`），**不在盘面 canvas 内**；贴地态按 §7 决策 **B1 隐藏**（不倾斜、不画进 canvas） |
| 传感器/朝向与读数状态机 | `CompassView` 的 `setSensorHeading/reset/setFollowSensor/...` | 搬的只是绘制，状态与 API 保留 |

### 1.3 数据源（**硬规矩：唯一真相源**）

`CompassData.java`：7 个字符串数组（`MOUNTAINS / WUXING / YINYANG / HOUTIAN / XIANTIAN / JIAZI / NAYIN`）+ `RingDef[] RINGS` + 5 个步进常量（`MOUNTAIN_STEP 15`、`MOUNTAIN_HALF 7.5`、`EARTH_OFFSET 0`、`HUMAN_OFFSET -7.5`、`HEAVEN_OFFSET 7.5`）。
**做法**：构建期由 Gradle 任务导出 `assets/compass/rings.json`（带 sha256，与 `mechanics.json` 同一套路），**JS 不许手抄第二份**。这一条沿用 M6 已批的「数据单一化」口径。



### 1.4 屏幕中心符号 —— **不在本方案范围（总监 11-08 裁定）**

逆向已定性「原版有」：红点 `13×13 #FF5163 @ (614,1350)`、四段蓝线 `≈57×7 #009DFF`、整体 `114×114`，**viewport 对齐、钉屏幕中心、不随 bearing/缩放/pitch 变**，地图 2D / 3D / 罗盘态均显示，弹层与子页隐藏。
因为它是 **viewport 覆盖层**，且不应随 WebView 加载成败存在，**由 @LC客户端 在原生层实现**（与 `CompassCrosshairView` 同层）。本文的贴地 canvas **不画它**，§1.1 图元保持十类。

---

## 2. 涉及行数

| 侧 | 改动 | 量级 |
|---|---|---|
| `map.html` | 新增 `drawCompass(ctx, size, …)` 图元绘制（§1.1 十类） | **≈ 320 行** |
| `map.html` | 贴地绘制通道：独立平面 quad + 纹理 + 混合 + 四角投影 | **≈ 90 行** |
| `map.html` | 状态/开关接线（`setGround` 内改走 canvas 路径） | **≈ 30 行** |
| 原生 | `CompassView` 绘制面下线（259 行），保留状态/传感器 API | −259 行（合计 544→≈285 行） |
| 原生 | `CompassCrosshairView` | **0（保留）** |
| 构建 | Gradle 导出 `rings.json`；dial 位图降采样进 assets | 见 §7 决策 A |

> 若 L1/L2/L3 全过、且确认不再需要原生盘面，第二阶段可把 `CompassView` 整体下线（再 −285 行）；**本方案先只下线绘制面，保留回滚能力**。

---

## 3. canvas 尺寸与 dpr

按原版逐字：`size = floor(2 × screen.width)`、`dpr = min(floor(dpr), 2)`。

| 量 | 值（本机 1228×2578 设备 px / density 3.375 / CSS 364×763） |
|---|---|
| `screen.width`（CSS px） | **364** |
| 画布 CSS 尺寸 `size` | `floor(2 × 364)` = **728** |
| `dpr` | `min(floor(3.375), 2)` = **2** |
| backing store | `728 × 2` = **1456 × 1456**（≈ 8.5 MB RGBA） |
| 盘面直径（画在画布内） | 273 CSS px（= 922 设备 px ÷ 3.375），居中 |
| 帮助中心/其它原有图 | 不动 |

⚠ **不能沿用 `resizeGL()` 的 `min(dpr, 2.5)` 逻辑按 `clientWidth×dpr` 算 backing**：那会得到 364×2.5≈910 的短边，盘面文字必糊（这是 M6 审地图指出并已批的坑）。**画布尺寸只由 `screen.width` 与 `dpr` 决定，固定 728 CSS / 1456 backing。**

---

## 4. 栅格纹理四角坐标算法（对齐原版 `toBounds`，半径 = 半边长）

原版逐字（`bundle.js` @41153）：
```js
this.map.unproject([screen.width/2, screen.height/2])
    .toBounds(floor(screen.width / map.transform.pixelsPerMeter))
// 输出顺序 [NW, NE, SE, SW]
```
拆成三句落到我方：

1. **中心** = 屏幕中心经纬度。我方 `center` 就是屏幕中心（我的相机以 center 为中心），直接用 `center`，等价于 `unproject([w/2, h/2])`。
2. **半径（米）= 屏宽米数**：`r_m = floor(screen.width_css × zMpp)`，其中
   `zMpp = 156543.03392 × cos(lat) / 2^zoom`（= `1 / pixelsPerMeter`，两者是同一个量）。
3. **`toBounds(radius)` 的 `radius` 是半边长**（逆向从原版自带 `lib/mapbox-gl.js` 定死）⇒
   **地面正方形边长 = 2 × r_m**，且 **边长在屏幕上 = 2 × screen.width = 728 CSS = 画布尺寸** ⇒
   **画布与地面正方形 1:1 对映，不做二次缩放**。

**四角（我方 3D）**：`[NW, NE, SE, SW] = 中心 ± r_m`（轴对齐，东向取 lng、北向取 lat），交给相机投影矩阵处理 bearing/pitch。
`lat±Δ` 中 `Δ = r_m / 111320`，`lng±Δ = r_m / (111320 × cos(lat))`。

**2D 模式**：把同一张 canvas 画在屏幕中心；在 zoom 不变时边长恒为 728 CSS（= 2×屏宽），`bearing ≠ 0` 时由 `#map` 的 `rotateZ(bearing)` 自然带动（与瓦片同层），**不在四角里再转一次**（转两遍是 M6 审地图抓过的坑）。

**验收口径（已批，写进判据）**：
- 贴地边长 ÷ 屏宽米数当量 = **2**；
- `pitch 0→60°` 时「盘面绘制框宽 ÷ 屏宽」基本不变（若明显变形 ⇒ 误用了「屏幕四角 unproject」）。

---

## 5. 与原盘面逐像素一致的验证办法

分三层，**文字区单独掩膜判、不许整体放宽阈值**（M6 已批）：

**L1 几何（不依赖像素）**
- 8 个方向扫描线：盘心、外径、各环半径，**≤1px / ≤0.5°**；
- 基线：现网 `CompassView` 在 922×922 下渲染的图（`compass-main-01.png` 裁 922 方块）。

**L2 全图 diff**（比较对象订正过两次，见下；两次都是「比错了东西」，不是阈值问题）
- **订正①**：不能拿原生 922 盘面直接当 canvas 基线——贴地盘面在 canvas 里只有 **549 backing px**，原生盘面是 922 设备 px，清晰度口径不同，直接比会把「分辨率先天不同」判成「迁移错误」。
- **订正②（2026-11-09，审核验实测 + 总监裁定）**：**不能拿进包素材 `dial_full_0.webp` 当比较对象**。canvas 上画的是三层合成（`map.html` 的 `drawCompass`）：① `drawImage(dialImg)` 位图 edge-to-edge ② `drawOutlineRings()` 外圈/内环/30° 刻度 ③ 中心三行读数文本。拿裸位图比成品，必然把 ②③ 算成差异——实测就是这样：内圈差异 14.15%、外圈 0.32%（区域与缩放都对得上，差异集中在叠加层）。
  - 素材的正确用途是**锚点核验**：进包 800/q85 版 5 张 sha256 与 `dial-export-manifest.json` 逐张相等（验「素材对不对」），**不是**像素比对的基线。
  - 这一族错误本轮已出现四次（Compose 语义框比 View 布局框、只比顶边、546 推算值当画布尺寸、素材当成品），病根同一条：**比的两个东西不是同一种东西**。
- **尺寸口径以真机实测为准**：`canvasBacking = 1456`（728 CSS × dpr 2）、盘面直径 **549 backing px**（`GROUND_DIAL_FRAC 0.37725 × 1456`）。早先文里的 **546 作废**（那是「盘面占画布 0.375」的推算值，不是画布尺寸）。

**A 区判定参数（可复现定义，2026-11-10 定；照这几行就能复算出同一个数）**

```
比较域 D     = { p | baseline_alpha(p) > 250 }          # 阈值写死 250，不用 ">0"、不用 "≥255"
边缘过渡像素 = D 中 3×3 邻域内存在 alpha ≤ 250 的像素      # 抗锯齿/有损压缩造成的软边
有效域 E     = D \ 边缘过渡像素
分母         = |E|（**不是 549²，也不是 |D|**）
差异像素率   = |{ p ∈ E : ΔE2000(canvas_p, baseline_p) > 3 }| / |E|  ≤ 2%
关键色       = 仅金环（COMPASS_GOLD #dcbe82）与五行字色这两类像素中心，ΔE2000 ≤ 2
取样尺寸     = canvas 居中 **549×549** backing；baseline 用 **双线性**从 800 降到 549
```

**`opaque_frac` 的度量必须连参数一起写**（同一张图能算出两个百分比，就是因为参数不同）：
- 本文口径：`alpha > 250`、**全像素不抽样**（步长 1）、分母＝盘面外接方（549²）；
- `docs/evidence/original-apk/measurements.md` §9.1 的 `0.140`：原版 **1080×1080** 全像素、`opaque_frac` 定义为不透明占比，盘心 `alpha=0` —— **尺寸不同，不能与 800 版直接比**；
- 测试实测的 `12.1%`：800 版、**步长 4**、阈值 `alpha>250` —— **抽样会系统性偏低**，引用时必须标步长。

**L2a 按三层分区判**（三个区性质不同，各用各的基线；混在一起比等于把三类差异搅成一锅）

| 区 | 区域 | 基线从哪来 | 判法 |
|---|---|---|---|
| **A 位图区** | **基线 `alpha > 250` 的像素**（实测为 **0.60r–1.00r 环带**；`0.00r–0.60r` 在源图里就是全透明，不是"没画"） | 进包 `dial_full_0.webp` **双线性**降到 549 | 差异像素 ≤2%、关键色 ΔE ≤2；分母是**基线不透明像素数**，不是 549²。同源 + edge-to-edge + `dialAlpha=1`，本应近似逐像素相等 |
| **B 环带区** | 外圈 + 3 条内环 + 30° 刻度（`drawOutlineRings` 画出的部分） | **由树内同一份 `map.html` 的 `drawOutlineRings()` 在同一尺寸画布上合成**，参数固定 | 同 A 的阈值；**不许另写一套等价实现当基线**，否则变成「两套实现比长相」 |
| **C 文字区** | 中心三行读数：`向 xx.x°` / `山·五行` / `坐·阴阳` | 无像素基线 | 只判「三行都在、不叠字、字号正确（30/17/13sp）」；**不比颜色**——读数随朝向变，文字内容也跟着变 |

- **盘面素材本身是镂空环（2026-11-10 实测，源图与导出都如此）**：`dial_full_0.png`（1080×1080）`alpha==0` 占 **81.77%**、`0.00r–0.60r` 全透明、`alpha==255` 14.03%、平均 alpha **41.6**；导出 `dial_full_0.webp`（800×800）平均 alpha **41.6**、各环带均值与源图逐带相同（55.8 / 101.6 / 61.6），只是 `alpha==255` 占比降到 8.72%（降采样 + 有损把 255 变成 254/253）。
  ⇒ 因此：**①** 中心透明是素材本来的样子，不是"只画了环、没画底盘"；**②** A 区的比较域必须按「基线 `alpha>250`」取，按半径硬取 `0.50r–0.70r` 会采到透明区、没有可比对象；**③** 不要跨尺寸比「不透明像素个数」（800 的 8.72% 与 1080 的 14.03% 不可直接比），要比就比同尺寸、同插值下的图。- **L2a 的比较对象**是「canvas 画出来的 vs 原生 `CompassView` 渲染到同尺寸」，不是素材。原生侧取图要求：贴地关、同 base、同 `dialType`/`dialAlpha`、同读数；可用 `setGroundBaseline(1)` 对同帧取「无盘面」图做背景差分，消除地图底色的影响。
- **L2b 贴地观感**：真机贴地截图 vs **原版贴地截图（L1）**，判整体观感与透明度；L1 拿不到就按三级降级规则只判我方自洽，关账写「无原版对照」。
- **两档 `dialAlpha`（30% / 100%）本轮不可达**：`compassDialAlpha` / `compassDialType` 在全仓**无赋值方**，贴地画布恒用 `dial_full_0` + alpha 1，所以 L3 的 20 态矩阵里 alpha 维度本轮只能跑 100% 一档。这条同时是**已知功能缺口**（贴地盘面不跟随「切换/透明度」按钮），已记入下一轮，不在本轮判据内。

**L3 状态矩阵（20 态）**
- 5 套盘面 × 天支线 开/关 × `dialAlpha` 30%/100% = 20 态，每态出图并人工抽查 + 与原生同态 diff。

**贴地专属（A 路线新增）**
- 真机双图 + dump：`pitch=0` 与 `pitch=60`、`bearing=0` 与 `bearing=90` 四组；
- 量化：盘面框宽/屏宽、盘心屏幕坐标与 `center` 的偏差、透明度合成值；
- 判据额外一条：**贴地态是平面四角、不随 DEM 起伏**（与相邻山顶/谷底高程无关）。

---

## 6. 回滚点

1. **开关**：`groundRenderer = "canvas" | "overlay"`。**变量的初始值仍是 `"overlay"`，但产品路径已经变了**：`setGround(true)` 会强制把 `groundRenderer` 置为 `"canvas"`，退出贴地时置回 `"overlay"`（`map.html` 的 `setGround`，M8 第二阶段接线）。所以「点贴地还是屏幕空间正圆」不再受这个初始值影响。
   **回滚方式**：把 `setGround()` 里那一行 `groundRenderer = on ? 'canvas' : 'overlay';` 改成 `'overlay'`，或直接删掉它，产品路径即退回纯 overlay（原生盘面），无需改素材、无需重建资产。
   调试钩子 `__LC3D.setGroundRenderer('overlay')` 仍然可用，但它只影响当前会话，不改这个默认路径。
2. **代码不回删**：迁移期**保留 `CompassView` 绘制面**（不删 259 行），只在 canvas 路径启用时隐藏原生盘面。L1–L3 全过后再单独立一笔删除，避免「一次改动里既搬家又删家」导致不可回退。
3. **素材不回删**：`dial_full_0..4.png` 原文件保留在 `res/`，assets 里的是**导出副本**；回滚只需关开关，无需恢复素材。
4. **验证留痕**：每层验证的图与 diff 结果落 `docs/fidelity/m7/ground/`，回滚判断有据可查。

---

## 7. 两个决策（2026-11-09 已批：A1 + B1）

**决策 A：`dial_full_0..4.png` 怎么进 WebView？**
它们在 `res/drawable-nodpi/`（2048×2048 左右，合计 3.1 MB），**WebView 读不到 `res/`**。三条路：
- **A1（已批、已执行）**：降采样到长边 **800**、WebP **q85**，导出到 `assets/compass/dial_full_N.webp`。
  **实测（不是我早先估的 300–600KB，那个估计错了）**：5 张合计 **1,200,656 B（1.15 MB）**，源 3,105,148 B，**省 61%**；
  参数扫描实数：1200/q90=2.31MB、1200/q80=1.81MB、1024/q85=1.67MB、900/q85=1.38MB、**800/q85=1.15MB**、900/q78=1.19MB。
  选 800 的理由：贴地时盘面直径 **549 backing px**（`GROUND_DIAL_FRAC 0.37725 × 1456 canvasBacking`；早期按「盘面占画布 0.375」推算出的 546 已作废，见 §5 L2），800 已是 **1.46× 过采样**，再大只增包体不提清晰度。
  **净包体影响**：迁移期 `res/` 原图与 assets 副本并存（+1.15MB）；L1–L3 全过、下线 `res/` 副本后 **净减约 1.9MB**。
  manifest（含每张 sha256 与尺寸）落 `docs/fidelity/m7/dial-export-manifest.json`，L2 基线直接引用它。
- A2：原尺寸原样复制进 assets（+3.1 MB 包体；M6 已因帮助中心图涨到 34.7 MB，C 轮正要压，不宜再加）。
- A3：原生解码后 base64 传 JS（每帧/每次切换传 3 MB，内存与帧率都不划算，且与「B 方案不做」同因）。
> 注（2026-11-09 已批）：A1 会损失位图细节，因此 **L2 diff 的基线就是进包的 800/q85 版**（与产物同源，逐张 sha256 见 manifest），1200 版仅作参考。早先那句「必须在 1200 采样后的基线上比」**作废**——按它比会把降采样差异判成迁移错误。

**决策 B：贴地态下的竖向指针（`pointer.png`）怎么办？**
原版指针是独立 DOM `<img>`，带 `pitchAlignment:"map"`，**由 MapLibre 帮它做地面透视**；我方是原生 `ImageView` 覆盖层，**做不到贴地倾斜**。三个选项：
- B1：贴地态**隐藏**原生指针，只保留地面盘面（最接近「原版贴地视觉」：盘面躺在地上，指针不在）。
- B2：保留指针但**不倾斜**（贴地时指针仍正立悬浮，与盘面错位，观感割裂）。
- B3：把指针也画进 canvas（改变原版「指针不在盘面 canvas 内」的事实）。
> **已批 B1**（2026-11-09）：贴地态隐藏原生竖向指针，只留地面盘面；台账按「**贴地态指针隐藏**」记一条已知可见差异。B2（保留但不倾斜）与 B3（画进 canvas）不采用。

---

## 8. 工作量与时间戳

| 段 | 内容 | 估 |
|---|---|---|
| 1 | 构建期导出 `rings.json` + dial 降采样（决策 A1） | 0.5 人日（含 Gradle 任务与 sha 记录） |
| 2 | `drawCompass` 十类图元搬 JS | 2.0 人日 |
| 3 | 贴地绘制通道（平面 quad + 纹理 + 混合 + 四角） | 1.0 人日 |
| 4 | L1/L2/L3 三层验证 + 20 态矩阵 | 1.0 人日 |
| 5 | 真机双图 + dump + patch（光照/混合顺序调参） | 0.5 人日 |
| **合计** | | **5.0 人日**（中心符号归原生层后回到原估） |

- 开工 `2026-11-08 14:45` / 收工 `15:12` / 返工 0 轮（本文为方案，未动代码）。
- 决策状态：**A1 已批**（800/q85，5 张合计 1,200,656 B；L2 基线用进包 800 版）、**B1 已批**（贴地态隐藏指针）。**无遗留待定项**。
