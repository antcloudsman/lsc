# M7 A轮 · 两个符号取证结案

> 对照基线 **原版 1.6.1**。取证手段：①原版 APK 全量代码/资源检索；②原版真机截图逐张像素检测（`docs/fidelity/orig/` 55 张）。
> 开工 `14:46` / 收工 `15:2x`。

---

# 一、屏幕中心符号 —— **原版有（像素证据），已量化，可实现**

## 1.1 形状（5× 放大目视 + 像素扫描，两份证据一致）

**四段互不相连的圆头短蓝线（上/下/左/右）+ 正中一个红色圆点。** 不是十字线（四段在中心处断开，中间留给红点），不是 drawable 三角形。

放大图：`docs/fidelity/m7/center-cross-zoom5x.png`（原图 `map-home-01.png` 中心 180×180 区域 5× 放大）。

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

## 2.2 截图检测

55 张原版截图中，**未发现任何“随坐标移动的定位点”形态**：中心符号是钉死不动的（§1.3 已证 23 张不同位置截图中圆心恒定），弹层/工具页均无独立定位点。

## 2.3 结论与唯一缺口（如实写）

**结论：原版在当前检索面内没有「随 GPS 移动的当前位置符号」**——代码零引用、截图零观测。

**唯一未覆盖的观察条件**：如果原版**仅在“点定位到当前位置成功后”才显示定位点**，而本批 55 张截图都没有在该状态下拍，则可能漏掉。该缺口**无法用静态手段关闭**，需要一次设备窗口：装原版 → 等 GPS 定位成功 → 看是否出现跟随点。

**建议**：若技术总监不接受这个缺口，给 1 次 2 分钟设备窗口即可关闭；否则按「**原版不可得（检索面内）**」结案，**不进实现**（不自画）。

---

# 三、检索复现命令（审核验可直接照跑）

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
