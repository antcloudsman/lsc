# map-bridge — `assets/map.html` 与客户端接口约定

> 归属：LC地图。文件位置 `app\android\app\src\main\assets\map.html`。
> 本文只描述**地图 WebView 内部接口**与**数据源出处**；罗盘 UI / 读数逻辑不在本文范围。

## 1. 结构

`map.html` 一个页面里有两套渲染器，互斥切换：

| 模式 | 实现 | 用途 |
|---|---|---|
| `2d`（默认） | DOM `<img>` 拼天地图 WMTS 瓦片 | 原版 1:1 行为：矢量/影像/地形三底图 + 注记层 |
| `3d` | 自研 WebGL 地形（`#gl` canvas） | 真高程起伏、俯仰 0–60°、旋转、天地图贴面 |

切换入口：`setMode('2d'|'3d')`；控件按钮「3D 地形 / 返回 2D」。

## 2. 数据源（必须写清）

### 2.1 高程 DEM

- 源：**AWS Terrain Tiles（Mapzen/Tilezen Terrarium）**
  `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`
- 编码：`elev_m = (R*256 + G + B/256) - 32768`（逐像素，单位米）
- 原始数据：SRTM / GMTED2010 / NED / ETOPO1，公开数据；无需 API key。
- 实测校验：北京 z10/843/388 瓦片解出海拔 19–62 m（城市平原），本文件 3.3 节截图中心点 52 m；灵山一带 1537–1891 m。
- **天地图没有公开 DEM 瓦片**。天地图 `ter_w` 是「地形晕渲」栅格（本身已带光照），把晕渲图再贴到三维地形上会与自研光照叠加出条纹，因此 3D 贴面建议用影像（见 2.2）；DEM 只取用真实高程，不用晕渲图反推高程。
- CORS：该源返回 `Access-Control-Allow-Origin: *`，`crossOrigin="anonymous"` 后可 `getImageData` 解码，实测可用。

### 2.2 底图贴面（可选）

- 天地图 WMTS：`https://{t0..t7}.tianditu.gov.cn/{layer}_w/wmts?...&tk=<KEY>`
  - `layer`：`vec` 矢量 / `img` 影像 / `ter` 地形晕渲；注记 `cva`/`cia`/`cta`。
- 3D 贴面取 **DEM 级别 +1** 的瓦片（6×6 = 1536×1536 px，正好覆盖一块地形），UV 与屏幕 1:1。
- **D-005：3D 默认贴面 = 影像（`img`）**；在 3D 里点「地形」仍可切回 `ter`（晕渲贴面会与自研光照叠加出条纹，不推荐）。2D 的默认底图仍是 `ter`，未改 1:1 基线。
- 贴面过滤器：`LINEAR` + `EXT_texture_filter_anisotropic`（上限 4×，不支持则忽略）。
- 天地图瓦片同样返回 `Access-Control-Allow-Origin: *`，可作 WebGL 纹理上传。
- 3D 贴面失败（断网/配额）时自动退回「高程配色 + 自研光照」，地形结构仍在。

### 2.3 key

`KEY` 为原版内置天地图 key（`dd18858b33229f5837ad1d97b8d3105e`），未改动。商用配额/换 key 属产品决策，未确认。

## 3. 3D 引擎参数（可复核）

| 项 | 值 | 说明 |
|---|---|---|
| 地形块 | 3×3 DEM 瓦片 = 768×768 px | `PATCH_TILES=3` |
| 网格 | 96×96 顶点（9409 点 / 55296 索引 / 18432 三角形） | `GRID=96` |
| DEM 级别 | `clamp(round(zoom)-1, 4, 12)` | 一块覆盖约 1.6 屏 |
| 垂直放大 | `VEXAG=2.5` | 高程×2.5，HUD 明示「垂直放大 2.5×」 |
| 相机 | 透视 fov 50°，俯仰 0–60°，bearing 0–360° | 俯仰 0 = 正俯视 |
| 雾 | `[0.75R, 1.05R]`（R=地形块半径） | 遮挡地形块边缘 |
| 底图切换 | 3D 下 `setBase` 会重建贴面 | 影像/矢量/地形 |

- 进入 3D 时若 `zoom<9` 自动落到 z9：大陆尺度下 8000 m 起伏相对视距 <0.3%，真高程也看不出立体感，属显示策略，不是数值修改。
- 高程值本身未做任何伪造；放大只作用于渲染几何。

## 4. 客户端 → 页面（`evaluateJavascript` 可调用）

| 方法 | 参数 | 行为 |
|---|---|---|
| `setHeading(deg)` | 度数浮点 | 罗盘向；控制 `#needle` 指针与 `向 x°` 标签 |
| `setSitFace(face, sit)` | 字符串，如 `"酉山"`,`"卯山"` | 顶部标签改为「向 酉山 · 坐 卯山」 |
| `setBase(b)` | `'vec'|'img'|'ter'` | 底图；3D 下重建贴面 |
| `setMode(m)` | `'2d'|'3d'` | 模式切换（等同按钮） |
| `toggle3D()` | — | 2D/3D 互换 |
| `setPitch(deg)` | 0–60 | 俯仰 |
| `rotateBearing(delta)` | 度，可负 | 相对旋转 |
| `setView(lat,lng,zoom)` | 数字 | 跳转（`window.__LC3D.setView` 支持 5 参含 pitch/bearing） |
| `zoomIn()` / `zoomOut()` | — | 缩放 |
| `locate()` | — | 浏览器 geolocation 定位（权限由 MainActivity 申请） |
| `clearMarkers()` | — | 清除 2D 立向点 |
| `getState()` | — | 返回 JSON（mode/lat/lng/zoom/pitch/bearing/fps/elev） |
| `getFps()` | — | 最近 1 s 的 rAF 实测帧率 |

**只有前两个是 MainActivity 当前已在调用的**（`MainActivity.updateReading()` 里 `evaluateJavascript("setHeading(...)")` / `setSitFace(...)`），其余为本次新增、客户端可直接用。

## 5. 页面 → 客户端（回调契约，D-005 / D-006）

```js
if (window.LCAndroid) LCAndroid.onEvent(JSON.stringify({type:"...", ...}));
```

- 当前 `MainActivity.createMapWebView()` **没有** `addJavascriptInterface`，`window.LCAndroid` 不存在，页面全程静默不报错。
- 客户端接入时新增（仅地图段）：
  ```java
  webView.addJavascriptInterface(new MapBridge(), "LCAndroid");
  ```
- 已定义事件：`ready`（WebGL 初始化完成）、`mode`（2D/3D 切换）、`error`（`{code:"dem|webgl|shader|texture"}`）、`fps`（每 5 s）。

### 5.1 3D 点选（P0-4，J**S 侧已完成，Java 桥待客户端轮 C 后补**）

点击 3D 地形 → 屏幕反投影射线 → 与 DEM 高度场步进求交（步长 `max(15m, DEM像素×1.5)`，二分 24 次）→ 输出：

```json
{"type":"pick","lng":116.407400,"lat":39.680162,"elev":33.1,"heading":0.0,"src":"3d"}
```

| 字段 | 说明 |
|---|---|
| `lng`/`lat` | 6 位小数，WGS84（与天地图瓦片同基准） |
| `elev` | **真实海拔米**（DEM 原始值，未乘 2.5 放大）；取不到时 **`null`，绝不传 0** |
| `heading` | 点选瞬间的罗盘向（`setHeading` 推入的值）；不联动、不回写罗盘 |
| `src` | 固定 `"3d"` |

清空：`{"type":"pick-clear"}`（点「清除标记」时发）。

客户端侧模型（D-005）：`MapMarker{lng, lat, elevM?, headingAtPick, src}`，**单点覆盖式**（新 pick 直接替换旧点，不累积）。
约定：**不联动罗盘**——点选只落标记，不回写方位（D-006）。

3D 标记渲染：页面内单个 DOM 红点，按 `vp` 矩阵逐帧投影到屏幕；`clearMarkers()` 一并清除并 `pick-clear`。

## 6. 截图

- 3D canvas 创建时已设 `preserveDrawingBuffer:true`，页面内 `window.__LC3D.screenshot()` 可返回 `toDataURL('image/png')`。
- **注意**：`MainActivity.captureMap()` 用 `mapView.draw(canvas)` 抓 GONE 状态的 WebView（0×0）会失败；且硬件加速 WebView 的 WebGL 内容用软件 `View.draw` 通常抓不到。修复建议（下一轮，属客户端/地图交界）：
  1. 抓可见的 map WebView（当前 `mapView` 是地图 Tab 的实例，罗盘 Tab 里另有一个 `createMapWebView()` 局部实例）；
  2. 用 `PixelCopy`（API 24+）或页面 `toDataURL` → `addJavascriptInterface` 回传。
- 本轮证据图全部用 `adb exec-out screencap`（系统合成截图，含 WebGL）。

## 7. 已知限制（未确认/未实现，不粉饰）

1. 3D 点选已可用（§5.1），但 **Java 桥未接**：真机上只有 `console.log("LC3D pick …")`，事件不会到达客户端，等轮 C 后补 `addJavascriptInterface`。
2. 3D 不支持双指缩放（原 2D 也没有；只有按钮/滚轮）。
2.1 俯视 0° 近景仍偏软：canvas 渲染上限 `dpr≤2.5`，该机 dpr=3.375 时被上采样 ~1.35×；且贴面为 NPOT（1536²）无法开 mipmap。M2 待办：改 POT 2048 + mipmap，或俯视时把贴面提一档（z+2）。
3. 3D 地形块固定 3×3 瓦片，转到地块外时重建立块，跨块瞬间可能有 <1 s 空窗（旧块继续渲染直到新块就绪，无白屏）。
4. 雾把地形块边缘遮住，**地平线不是无限远**：俯仰 45° 时远处 1/3 会进雾。
5. `ter`（地形晕渲）贴面会与自研光照叠加，建议 3D 用 `img`；是否改默认属产品决策，未改。
6. 定速 60 fps 上限受 WebView vsync；实测见 §8。

## 8. 验收与证据

```powershell
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
$env:PATH="$env:JAVA_HOME\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;$env:PATH"
cd E:\APPDetector\output\app\android; gradle assembleDebug --no-daemon
adb -s L2E0222531014833 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s L2E0222531014833 logcat -c
adb -s L2E0222531014833 shell am start -n com.luopan.fengshui/.MainActivity
# 地图 Tab → 3D 地形 → 放大到 z12 → 拖到山区
adb -s L2E0222531014833 logcat -d | Select-String "LC3D"
```

- 帧率测量方法：页面 `requestAnimationFrame` 计数，每满 1 s 取 `frames*1000/Δt`，HUD 显示并每 5 s 打 `console.log("LC3D fps=...")`，由 `logcat`（tag `hwschromium-*`，`[INFO:CONSOLE]`）取出。**不是估算值**。
- 证据文件：`checks\lc-map\final-45.png`（俯仰 45°）、`checks\lc-map\final-top0.png`（俯视 0°）、`checks\lc-map\*.png` 过程图；日志 `checks\lc-map\logcat-*.txt`。

## 9. 待办（下一轮）

- Java 侧 `LCAndroid` 桥 + `MapMarker` 模型（客户端，轮 C 后）。
- 俯视 0° 贴面清晰度：POT 2048 + mipmap，或俯视时贴面提一档（见 §7 2.1）。
- 双指缩放。
- 截图链路修复（PixelCopy / toDataURL 回传），归口待技术总监定。
- DEM 境外源是否换国内源（产品已列为待拍板项），换源只需改 `TERRARIUM` 常量与解码函数。
