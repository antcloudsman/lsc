# M6 Stage 1 · 地图侧实现现状与改动锚点

> 归属：LC地图（地图与三维引擎）。用途：Stage 1 回填「我方当前实现锚点」，并作为 Stage 2 改码的唯一入口。
> 纪律：**判据写内容锚点，不写行号**（行号会随提交漂移；本文件出现的行号只用于定位，冲突时以内容锚点为准）。
> 对齐 develop `3196162`。

## 1. 第 2 条 · 拖动方向（2a 符号 / 2b 逆旋转）

### 1.1 现状（代码级）
- 入口 `pan(dxPx, dyPx)`：**第一句就是 3D 分流** `if (mode === "3d") { pan3D(dxPx, dyPx); return; }`
  —— `pan` 与 `pan3D` 收**同一份实参**。
- 2D 分支落点（内容锚点）：`c.x -= dxPx / 256;` 与 `c.y -= dyPx / 256;`（`c` 为中心瓦片坐标）。
  develop `3196162` 上位于 `1776-1777`；**行号仅定位用**。
- 鼠标调用方传 `pan(lastX - e.clientX, lastY - e.clientY)`；触摸调用方传 `pan(-dx, -dy)`；两者都传**负位移**。
- 净效果：手指右移 Δ>0 → 传入 −Δ → `c.x += Δ/256` → 中心经度增大（东移）→ 固定经纬度屏幕 x 变小 → **内容左移**。
  四方向实测均反向（LC测试 `docs/qa/m6-drag-direction.md`；原版同手势同向），且严格线性、与缩放无关。

### 1.2 2a 唯一改法（判据）
- **只改** `pan()` 2D 分支的两行：`c.x -= dxPx/256` → `c.x += dxPx/256`，`c.y -= dyPx/256` → `c.y += dyPx/256`。
- **调用方（鼠标/触摸两处）与 `pan3D()` 一律不动。**
- 反例（禁止）：把调用方改成传正位移 → 2D 变正确，但 `pan3D` 也拿到 +Δ，`mx` 变正 → 中心东移 → **3D 由正确变反向**。
- 验收（2a 单独判）：四方向与手指同向，z=12、z=18 各一遍。

### 1.3 2b 逆旋转（独立判据，不与 2a 混判）
- 现状缺陷：`applyTransform()` 在 2D 下把整个 `#map` 按 `rotateZ(bearing)` 旋转（`bearing === 0` 时不设 transform），
  但 `pan()` 直接拿屏幕 `dxPx/dyPx` 当经纬度偏移，**没有按 bearing 逆旋转**。
  故 `bearing ≠ 0` 时拖动斜着走；实测 `bearing=30`、上滑 300px → `Δlng=0、Δlat=+0.000423`（纯竖直），屏幕内容却斜向移动。
- **现成正确参照（照它写）**：叠加层旋转补偿 `var rot = (mode === "2d") ? -bearing : 0;`；3D 的 `pan3D()` 自己也用 `bearing` 做了逆旋转。
- 改法：2D 分支落经纬度之前，先把 `(dxPx, dyPx)` 按 `-bearing` 旋转。
- 验收（2b 单独判）：`bearing=30` 与 `bearing=90` 两档，滑动 300px 后屏幕内容位移与手指**平行且同向**；任一档不平行即按「逆旋转失败」打回，不与 2a 混成一句「方向还是不对」。

## 2. pinch / 双击缩放：**未实现**
- `map.html` 内 `touches.length === 2`、`dblclick`、`doubleTap`、`pinch` **命中 0**。
- viewport 显式禁止缩放：`user-scalable=no`；`#map` 为 `touch-action:none`。
- 缩放入口只有滚轮与 `zoomIn()/zoomOut()` 按钮。
- 原生侧 `MainActivity` 无 `setBuiltInZoomControls / setSupportZoom / onTouchEvent / ScaleGestureDetector`。
- 结论：双指缩放与双击缩放均不存在；是否实现需原版取证后定，**不自创**。

## 3. 第 3 条 · 两个符号现状
- **当前位置符号：无。** `locate()` 只改 `center` 与 `zoom`，**不落任何标记**。定位权限侧是通的（原生 `onGeolocationPermissionsShowPrompt` 直接放行）。
- **屏幕中心符号：无。**
- 现存「指针」是 `#needle`：`2px` 宽、高 `34vw`（max 140px）的**红色渐变竖条**，锚点 `left:50%; top:50%; margin-top:-34vw`，从屏幕中心向上贯穿 —— 用户所说「怪异的指针符号」即此物。
- 另有 `.marker`（14px 红圆，点选落点）与 `#heading-label`（顶部「向 N°」）。
- 两个符号的形态须等逆向给出原版 drawable 名/尺寸/锚点，**不自画**。

## 4. 第 8 条 · 3D 按钮事件链与失败点
- 事件链：原生右列「3D」→ `js("toggle3D()")`（`MainActivity` 的 `case "3D"` 分支）→ `toggle3D()` → `setMode("3d")`。
- `setMode("3d")` 前置：`zoom < 9` 时强制拉到 9；`drape = (base === "ter") ? "img" : base`（`ter` 自动切影像贴面）；隐藏瓦片层、显示 `glCanvas`；`initGL()` 失败则打 `LC3D ERROR webgl-unavailable` 并回落 2D。
- 三种失败：① WebGL 不可用（回落 2D）；② DEM（AWS Terrarium）瓦片拉不到；③ 贴面瓦片成功率不足（要求 `ok >= 30/36`，否则 `LC3D texture fail`）。
- **对用户全静默**：HUD 只在 `uiMode === "full"` 显示，而主地图页由原生注入 `applyMapUiMode(...)`，`uiMode` 恒为 `map` → `setHud(...)` 写了但看不见，用户只看到「什么也没发生」。
- 因此第 8 条除修渲染外，还须**让失败可见**（原生接 `WebChromeClient.onConsoleMessage`，把 `LC3D ERROR` / `LC3D texture fail` 转 toast 或提示条），否则三种失败在真机上不可分辨。
- 另一症状来源：原生右列那颗 `lc_ic_3d` 没有 selected 态（改文案/active 的是 WebView 内部的 `#btn_3d`），用户按了看不出开没开。

## 5. 第 7 条 · 贴地：通道归属与入口归属（两处都已更正）

### 5.1 三条通道（LC审地图 §核，采纳）
1. **`CompassView`（原生 Canvas）：无透视能力**，是纯 2D 正交绘制。`Matrix`/`setCameraDistance`/`Camera`/`perspective` 全仓命中 **0**，绘制只有 `canvas.save/rotate/restore` + `drawCircle/drawLine/drawBitmap/drawPath/drawText`。要表达「盘面随地面倾斜」，必须新增 `Matrix.setPolyToPoly` 四角映射或 `Camera + setCameraDistance` 伪 3D，或换渲染通道。
2. **地图 WebGL 的 `drape`（贴面）**：把影像/底图瓦片作**一张贴图**、按 UV 采样到 DEM 三角网格上，作用对象是**地形**，不是盘面。它只决定 3D 地形外观，与「罗盘贴地」无关。
3. **原版依赖哪条**：待逆向。可判的取证方法（LC审地图 给）：看原版倾斜截图中盘面**刻度文字的形状**——
   呈梯形/近大远小（真透视）→ 须新增渲染通道、第 7 条立项；仍正立、仅整体压扁/位移（仿射）→ 2D 图片变换，`CompassView` 加 `Matrix` 即可对齐，不必立项。

### 5.2 入口归属更正（重要，避免改名白做）
- **用户点的「贴地」不是 `map.html` 的 `#btn_ground`**。用户可见入口是**原生左列 6 项**里的「贴地」：
  列表含 `截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地`，点击走 `MainActivity` 的 `case "贴地": js("toggleGround()")`，图标 `lc_ic_tiedi`。
- `map.html` 的 `#btn_ground` 位于 `#controls` 内，而 `uiMode !== "full"` 时整个 `#controls` 隐藏；主地图页恒为 `uiMode="map"`，**用户根本点不到它**。
- 因此「摘掉『贴地』这个名字」这件事，**改 `map.html` 按钮文案对用户零效果**；要改的是**原生左列那颗按钮的文案/图标** —— 属客户端文件（`MainActivity` + 资源），**跨模块，需报技术总监由客户端执行**，我不擅自改。
- 待逆向补一条：**原版左列有没有这颗按钮、叫什么、点了什么效果**。

### 5.3 处置（技术总监 11-08 裁定）
- `btn_ground` **删/改均不做，挂起**；只摘名字避免误导，**改文案不改行为**；等逆向结论再定删或改语义。
- 「近地视角」这个替代名我此前提过，**按裁定现在也不落**——没有原版证据就不加新名字。
- 贴地效果先出设计（地面投影/透视矩阵/雾参数），逆向给出「2D Canvas 变换 or 3D 渲染」后再定是否实现。**不许靠改名把第 7 条当完成。**
- 现实现的真实行为（供判据引用）：`toggleGround()` 把 `pitch` 推到 60° 并强制进 3D；渲染时 `if (groundMode) D *= 0.35`（机位距离压到 35%）。**没有地面投影、没有贴地平面、没有地平线。**

## 6. 第 6 条 · 罗盘复位（新增，逆向已定性为独立控件）

### 6.1 原版定性（LC逆向 11-08，采纳）
- **是两个控件，不是同一个**：`罗盘复位` 走 `CompassToolsKt.I0(g)` → `g.I()` → `MapWebApiKt.resetCompass()`（JS 桥 `resetCompass`）；
  `指针开关` 走 `Q0(vm)` → `MainViewModel.M0()`（`h0` 读、`b3` 写、`xor 1`）。**两者不读写同一状态位**。
- 原版给该按钮起的名字就是「罗盘复位」（埋点 `TAHelper.track("compass_ck","name","罗盘复位")`），不是显隐开关。
- **因此第 6 条是对 P1-33-C 的补充，不是推翻**：P1-33-C「指针开关 = `h0`」原样有效。
- 复位项不止朝向：`g.I()` 同方法体一并复位 `P/R/N=false`、`L=0`、`K=100`、`j0=0`、`Q=0.0`、`W=1.0f`、`V=0.0f`、`S=Zero`、`k0=false` → `M()`。
  只置 `bearing=0` 会留下倾斜与偏移，用户仍会觉得「没回正」。

### 6.2 我方现状（Stage 1 实测查询）
- `map.html` 全文件检索 `resetCompass`、`function resetAll`、`setBearing` → **命中 0**。**地图侧没有复位入口。**
- 原生 `case "罗盘复位"` 只调 `compassView.reset()`（盘面 `heading=0/offset=0/follow=true`），**`map.html` 完全不参与**。
- 用户症状：按「罗盘复位」→ 盘面回正、地图纹丝不动（bearing/pitch/zoom/偏移全留原样）。

### 6.3 阶段 2 要做（拆两半，我只做前半）
- **我（map.html）**：新增 `resetCompass()`，按 `g.I()` 清单复位地图侧状态；**但每个参数对应 bearing/pitch/scale/offset 的哪一项，须逆向补一句语义后再落**，我不猜着连。
- **原生侧（客户端）**：`case "罗盘复位"` 在现有 `compassView.reset()` 之外补 `js("resetCompass()")`；等我的函数签名定了再接。
- 验收分两条、不许混判：①盘面回正（读数 0°、偏移 0）；②**地图侧 bearing/pitch/zoom/偏移归位**（画面转正、不歪、倾斜消失）。

### 6.4 复位顺序与两个坑（LC审地图 给的可复位量清单 + 我核的代码路径）

**必须先清 `sitMode`，否则「复位后立刻又歪」** —— 这是真实代码路径，不是猜测：
`setHeading()` 内有 `if (sitMode) { bearing = (round(lastHeading) + (sitMode === 2 ? 180 : 0)) % 360; applyTransform(); }`，
而原生每帧 `onSensorChanged` 都会推 `setHeading`。所以只把 `bearing` 置 0、`sitMode` 仍停在 1/2 时，
**下一次传感器帧就把 bearing 改回 `heading`/`heading+180`**，用户看到「按了复位，盘面回正、地图一抖又歪」。

正确顺序（定稿）：
```
sitMode = 0; bearingBeforeSit = 0;      // 先摘掉坐向跟随
groundMode = false;                     // 清掉"近地视角"保存的 pitchBeforeGround
bearing = 0; pitch = 0;                 // 再复位角度
setMode("2d")   /* 若当前在 3d，必须走 setMode 以同步 #btn_3d 文案与 #pitchRange */;
applyTransform(); refreshNeedle(); updateHud();
```
- `groundMode` 同样有坑：它保存 `pitchBeforeGround`，直接置 `pitch` 而不清 `groundMode`，后续任何 `toggleGround()` 会把 pitch 还原成旧值。
- 别用裸赋值：至少要顺带 `applyTransform()`（清 `rotateZ`）、`render()` 或 3D 下 `ensurePatch(true)`、`updateHud()`、`refreshNeedle()`，并同步 `#pitchRange` 滑块与 `#btn_3d` 文案（后者由 `setMode` 维护）。

**⚠ `setZoom()` 不存在（全仓零命中）** —— 若判据写「zoom 复位必须走 `setZoom()`」，照字面实现会直接抛错。现网与缩放相关的只有三个：
| 入口 | 行为 |
|---|---|
| `zoomIn()` / `zoomOut()` | 每次 ±1；3D 下 `ensurePatch()`，否则 `render()`；再 `updateHud()` |
| `debugSetView(lat,lng,z,p,b)` | `zoom = clamp(2,18)`，随后 3D 下 `ensurePatch(true)+updateHud()`、2D 下 `applyTransform()+render()`（**这是目前唯一"设到指定级并刷新"的完整序列**） |
| `setView(...)` | 已在 `window` API 导出列表内，客户端可用；但它不是纯 zoom setter |

正确处理：若复位确需动 zoom，应**新增** `setZoom(z)`，内部复用 `debugSetView` 尾部那段序列（clamp → 3D 走 `ensurePatch`、2D 走 `applyTransform+render` → `updateHud`），这样才满足「不裸赋值」；不要直接写 `zoom = z`，否则 `#pitchRange`、`#btn_3d` 文案、瓦片层与 `getState()` 会与真实状态脱节。

**我方全部视图状态面（供逆向一对一映射，字段语义我不替逆向认）**：
`mode`(2d/3d)、`bearing`、`pitch`(0–60)、`zoom`、`center`、`sitMode`/`bearingBeforeSit`、`groundMode`/`pitchBeforeGround`、`needleOn`、`contourOn`、`ridgeOn`、`roadNet`、`base`/`anno`/`drape`。
候选映射（**待逆向确认，不作判据**）：`Q=0.0` 像 `bearing`；`P/R/N` 像三种布尔（`sitMode`/`groundMode`/`mode`）；`W/V/S` 像缩放与中心偏移；`K=100` 像百分比类，**不像 `zoom`**（我方 `zoom` 是整数级）。
仍待逆向补一句、且决定复位是「永久有效」还是「下一帧被传感器覆盖」的：**哪一项是"复位后是否继续跟随传感器"**。

**一条待裁语义**：原版 `g.I()` 里没有明显对应 `center/zoom` 的项（`L(0)/j0(0)` 也可能就是它们）。
我方建议默认**不复位 `center`**（否则用户丢失当前位置）；`zoom` 是否复位待逆向确认。此条写进判据，避免实现方各自猜。

## 7. 待逆向回填的三条（阻塞）
1. 第 6 条：指南针按钮 vs 指针开关是否同一控件（视图树位置 + handler DEX + 是否读写同一状态位）。
2. 第 3 条：`pointer` 竖向 81×324 与水平红蓝双三角，哪个是哪个（我方两份文档自相矛盾）。
3. 第 7 条：原版盘面倾斜靠 2D Canvas 变换还是 3D 渲染；**顺带确认原版左列有无「贴地」按钮及其名称与效果**。

## 8. 时间戳与轮次
- Stage 1 现状梳理：开工 `2026-11-08 01:00` / 收工 `01:24` / 返工 1 轮（入口归属写错：原以为是 `map.html` 的 `#btn_ground`，经 LC审地图 核实应为原生左列按钮，已更正 §5.2）。
- 本文件为新增文档，无代码改动。
