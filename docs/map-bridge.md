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
- 3D 贴面取 **DEM 级别 +1** 的 6×6 瓦片，拼进 **2048×2048 POT 画布**（341.33px/瓦片，1.33× 上采样），UV 覆盖整块地形；POT 才能 `generateMipmap`。
- **D-005：3D 默认贴面 = 影像（`img`）**；在 3D 里点「地形」仍可切回 `ter`（晕渲贴面会与自研光照叠加出条纹，不推荐）。2D 的默认底图仍是 `ter`，未改 1:1 基线。
- 贴面过滤器：`MIN=LINEAR_MIPMAP_LINEAR`（三线性）、`MAG=LINEAR`，外加 `EXT_texture_filter_anisotropic`（上限 4×，不支持则忽略）。
- 天地图瓦片同样返回 `Access-Control-Allow-Origin: *`，可作 WebGL 纹理上传。
- 3D 贴面失败（断网/配额）时自动退回「高程配色 + 自研光照」，地形结构仍在。

### 2.3 key（仓库内零 key）

天地图两个 key 都是**用户个人 key**，存在仓库外（`secrets/tdt.properties`），由 LC服务端 在
**构建期注入** `BuildConfig.TDT_SERVER_KEY` / `BuildConfig.TDT_WEB_KEY`（见 `docs/BUILD-SECRETS.md`）。
仓库里**没有任何 key 字面量**（仓库是 public），`map.html` 也不再带 key。

分工与传递方式：

| key | 用途 | 传递/UA |
|---|---|---|
| `TDT_SERVER_KEY` | 原生侧 `api.tianditu.gov.cn`：逆地理、分类搜索、超清拼接瓦片 | `MapKeys.TDT` 引用；**不设 UA，用平台默认** |
| `TDT_WEB_KEY` | WebView 里的 WMTS 瓦片 | `MapAsset.url(hash)` 拼成 `map.html?tk=<webKey>#…` 交给 JS；WebView 自带浏览器 UA |

- `map.html` 顶部：`var KEY = new URLSearchParams(location.search).get("tk") || "";`，取不到就空 +
  一条 `console.warn`，**没有任何硬编码回退**。
- **query 在 hash 之前**：`map.html?tk=…` 是 search，`#in3d&measure=area` 是 hash，两者互不干扰。
- 旧的原版内置 key（`dd18…105e`）已于 2026-11-05 失效（天地图返回 `301001 非法key`），所有走它的
  山/水/钓点/逆地理请求当时全空——换成个人 key 后恢复。
- 日志与异常里的 URL 一律经 `LcHttp.redact()`，只留 host+path，不打印 `tk`。
- 自查：`grep dd1885` 在代码侧零命中；两个新 key 全仓零命中（构建期注入，不落盘）。

**UA（MD-10，2026-11-05 实测矩阵，伪装已默认关闭）**：

| 目标 | key | UA | 结果 |
|---|---|---|---|
| api.tianditu.gov.cn | 服务端 | Dalvik/Java 原生默认 | **200** |
| api.tianditu.gov.cn | 服务端 | 浏览器 Chrome UA | **403**（`301013 权限类型错误：Key 为服务器端`） |
| api.tianditu.gov.cn | 服务端 | 不带 UA | 403 |
| t0.tianditu.gov.cn WMTS | 网页端 | WebView 浏览器 UA | 200（image/jpg, 12,033 B） |
| t0.tianditu.gov.cn WMTS | 网页端 | Dalvik UA | 403 |

即：**按 key 类型用天然 UA 即可**；`LcHttp.setUaSpoof(true)` 保留为兜底开关，默认关。

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
| `setRoadNet(on)` | `true`/`false` | **F-04 路网视图**：`on` 切天地图 `vec`（路网图面 + `cva` 路名注记），`off` 恢复进入前底图；3D 下重建贴面；发 `roadnet` 事件（§5.5） |
| `getState()` | — | 返回 JSON（mode/lat/lng/zoom/pitch/bearing/fps/elev） |
| `getFps()` | — | 最近 1 s 的 rAF 实测帧率 |

**只有前两个是 MainActivity 当前已在调用的**（`MainActivity.updateReading()` 里 `evaluateJavascript("setHeading(...)")` / `setSitFace(...)`），其余为本次新增、客户端可直接用。**表中方法全部真实挂在 `window` 上，由 AT TD-015 逐项断言**（此前 `getState/sitNorth` 只在内部作用域，客户端 `js` 拿到 `undefined`，已修）。

### 3.1 window 级状态读取（客户端 `js` 断言用）

| 接口 | 返回 |
|---|---|
| `getState()` | 对象 `{mode, lat, lng, zoom, pitch, bearing, fps, elev, demZoom, hasTexture, sitMode, sitNorth, contour, ground, needle, base, drape, roadNet, uiMode, attribution, source}` |
| `getFps()` | 数字（最近 1 s rAF 实测帧率） |
| `window.sitNorth` | 只读布尔（`sitMode!==0`，即坐向对齐是否开启） |
| `window.__LC3D.*` | 兼容别名：`setView/state/fps/screenshot` |

### 4.1 启动参数（URL hash）

客户端改 `loadUrl` 即可直接进 3D，免去模拟点按钮：

| hash | 行为 |
|---|---|
| `#in3d` | 页面加载后直接进 3D（50 ms 后执行） |
| `&lat=..&lng=..&z=..` | 指定初始视点（同 `debugSetView`） |
| `&pitch=0..60` / `&bearing=0..359` | 初始俯仰/旋转 |
| `&contour` / `&ground` | 启动即开对应控件 |
| `&sit=1` / `&sit=0` | 启动即对齐：`1`=坐南朝北（向朝上）、`0`=坐北朝南（坐朝上）；裸 `&sit` 等同 `sit=1` |
| `&fs` | 启动即全屏（保留「退出全屏」） |
| `&srcpanel` / `&src=N` | 打开底图源面板 / 直接选第 N 个源（0 基） |
| `&roadnet` | 启动即开「路网视图」（F-04，切 `vec`） |
| `&uimode=canvas` / `&ui=none` | 回放页用：隐藏地图自带左侧工具列/底图控件/角注/HUD，只留地图与轨迹层（默认 `full` 不变）；也可运行期 `lc_setUiMode('canvas')` |

> **key 不在 hash 里**：网页端 key 走 `?tk=` 查询参数（`map.html?tk=<webKey>#in3d&measure=area`），
> 由 Java `MapAsset.url("#…")` 拼好；hash 语义与顺序不变。
> 例：`file:///android_asset/map.html#in3d`（3D 桥对拍用，无 key 时页面 console.warn 且不加载瓦片）。
> 自测入口 `#selftest` 仍保留（会等 DEM 就绪后自动点选一次）。

**hash 在页面初始化与 `hashchange` 时都会应用**，所以同文档导航只改 hash（不触发 onload）也能生效：客户端一次 `loadUrl("...#in3d&sit")` 即可。hash 片段按 token 匹配（`#in3d`/`&sit`/`&lat=`），不会被 `lat=` 之类误命中。
> 实机注意：部分 WebView 对「仅 fragment 不同」的 `loadUrl` 不导航、也不发 `hashchange`；若客户端发现页面无反应，改用 `evaluateJavascript("location.hash='#in3d&sit'")` 即可走同一路径。

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

### 5.2 JS 侧模拟桥与契约自测（客户端接桥前先对拍）

页面暴露 `window.__LCBridgeTest`，用假 `LCAndroid` 抓事件、跑契约断言，**不干扰真桥**（`install()` 会暂存/恢复原 `LCAndroid`）：

| 方法 | 说明 |
|---|---|
| `install()` / `uninstall()` | 安装/卸载假桥（`onEvent` 收 JSON 字符串存数组） |
| `events()` / `last(type)` / `reset()` | 取事件、取最后一个某类型事件、清空 |
| `assertContract()` | 对已抓事件跑断言，返回 `{pass, events, failures[]}` |
| `selfTest()` | 需已进 3D 且 DEM 就绪：连点 2 次 → 校验单点覆盖 → `clearMarkers()` → 跑 `assertContract()`，打 `LCBRIDGE selfTest PASS/FAIL {…}` |

断言项：JSON 可解析、`type` 为字符串、无 `NaN/Infinity`、`pick` 的 `lng∈[-180,180]`/`lat∈[-90,90]`/`heading∈[0,360)`/`src==="3d"`、`elev` 必须是**数字或 `null`**（缺失用 `null`，不用 0 当哨兵）、`pick-clear` 存在、两次点选后 DOM 里 `.pick3d` 只有 1 个（单点覆盖）。

无头自测（本机 Chrome，无需真机）：

```powershell
cd app\android\app\src\main\assets
python -m http.server 8765 --bind 127.0.0.1
& 'C:\Program Files\Google\Chrome\Application\chrome.exe' --headless=new `
  --enable-unsafe-swiftshader --enable-logging=stderr --v=0 `
  --virtual-time-budget=22000 --window-size=400,800 `
  "http://127.0.0.1:8765/map.html#selftest&bridge"
```

实测输出：`LCBRIDGE selfTest PASS {"pass":true,"events":3,"failures":[],"picks":2,"markersAfterTwoPicks":1}`（事件 = 2×pick + 1×pick-clear）。

## 5.3 地图页控件（原版 UI 清单对应）

| 按钮 | 行为 | 状态 |
|---|---|---|
| `指针` | 显隐 `#needle` 坐向指针（默认开） | 等价替代 |
| `坐南朝北 / 坐北朝南` | 单按钮双标签 toggle（原版 F5：`g.x()` 布尔驱动文案）。坐南朝北 `bearing=heading`（向朝上）、坐北朝南 `bearing=heading+180`（坐朝上）、再点关闭恢复原 bearing | **等价替代**（第二态几何原版未取证；LC 多一个「关闭」态） |
| `等高线` | 3D 着色器叠 100 m / 500 m 两级等高线（fwidth 抗锯齿）；2D 无 DEM 网格不显示 | 等价替代（原版为 WebView 图层，未取证） |
| `贴地` | 相机距离 ×0.35 + 俯仰 60°，低机位掠过地形；关闭恢复原俯仰 | **等价替代**（原版确切行为未取证） |

实景（街景）**未实现**：原版 `openStreetView` 是天地图街景还是第三方 SDK 未取证，取证前不做。

**全屏**：全屏会隐藏控件栏/提示/底图信息条，但保留右下角悬浮「退出全屏」，不会出现只能重启 App 的死路。

### 5.4 底图源（12 源）与审图号

按原版 `AppConfig.appMapSourceList` 的 12 源顺序建模（出处见 `app/MECHANICS.md` R3b）：

中科星图 / 吉林一号·全球 / 吉林一号·全国 / 世纪空间卫星 / **天地图卫星(img)** / **天地图地图(vec)** / **天地图地形(ter)** / 高德卫星 / 高德地图 / 腾讯卫星 / 腾讯地图 / 腾讯地形

- 只有天地图 3 源有 key 可用；其余 9 源**置灰**并标注「需授权 / 需 key」，不放假条目（D-006）。
- 默认源 = **天地图地形**（与现基线一致）。原版默认 `siJi_satellite`（世纪空间卫星）需授权，判「有意差异」。
- **审图号按源绑定**：`MAP_SOURCES[i].review`，真相未核对前一律填 `"未确认"`，界面右下角常显「底图：X · 审图号：Y」。**不得照抄世纪空间/高德的号**——那是合规问题，等 LC产品 核对天地图官方号后回填。
- 面板入口 = 控件栏「底图源」。

### 5.5 路网视图（F-04，2026-11-05）

原版左列「路网」是底图/叠加切换（`MapWebInterface` 33 个方法里没有路网专用方法），不是独立矢量图层。LC 实现：

- **接口**：`setRoadNet(on)`（`on` 为布尔）。`on` → `setBase('vec')`（天地图矢量图面含路网 + `cva` 路名注记）；`off` → 恢复进入前的 `base`（默认 `ter`）。3D 下走同一条 `setBase`，贴面会重建。
- **事件**（走 `LCAndroid.onEvent`）：
  ```json
  {"type":"roadnet","on":true,"mode":"vec"}
  ```
  `mode`：`"vec"`（打开）或 `"base"`（关闭/被其他底图切换顶掉）。
- **状态**：`getState().roadNet` 布尔；`window.__LC3D.setRoadNet` 同义。
- **边界**：天地图**没有实时路况服务**，本视图只画路网图面与路名，**不画任何红/黄/绿路况线**，文案统一叫「路网视图」。若以后引入免费路况源，另立项，不在此接口塞假数据。

## 6. 截图

- 3D canvas 创建时已设 `preserveDrawingBuffer:true`，页面内 `window.__LC3D.screenshot()` 可返回 `toDataURL('image/png')`。
- **注意**：`MainActivity.captureMap()` 用 `mapView.draw(canvas)` 抓 GONE 状态的 WebView（0×0）会失败；且硬件加速 WebView 的 WebGL 内容用软件 `View.draw` 通常抓不到。修复建议（下一轮，属客户端/地图交界）：
  1. 抓可见的 map WebView（当前 `mapView` 是地图 Tab 的实例，罗盘 Tab 里另有一个 `createMapWebView()` 局部实例）；
  2. 用 `PixelCopy`（API 24+）或页面 `toDataURL` → `addJavascriptInterface` 回传。
- 本轮证据图全部用 `adb exec-out screencap`（系统合成截图，含 WebGL）。

## 7. 已知限制（未确认/未实现，不粉饰）

1. 3D 点选已可用（§5.1），但 **Java 桥未接**：真机上只有 `console.log("LC3D pick …")`，事件不会到达客户端，等轮 C 后补 `addJavascriptInterface`。
2. 3D 不支持双指缩放（原 2D 也没有；只有按钮/滚轮）。
2.1 俯视 0° 近景：贴面已改 **POT 2048 + mipmap + 三线性**（D-009 画质项），canvas 渲染上限 `dpr≤2.5`（该机 dpr=3.375，仍有 ~1.35× 上采样）；**真机复验待补**（无头 Chrome 已验不破图，dpr 效果需真机）。
3. 3D 地形块固定 3×3 瓦片，转到地块外时重建立块，跨块瞬间可能有 <1 s 空窗（旧块继续渲染直到新块就绪，无白屏）。
4. 雾把地形块边缘遮住，**地平线不是无限远**：俯仰 45° 时远处 1/3 会进雾。
5. `ter`（地形晕渲）贴面会与自研光照叠加，建议 3D 用 `img`；是否改默认属产品决策，未改。
6. 定速 60 fps 上限受 WebView vsync；实测见 §8。
7. 等高线只在 3D 生效；2D 平铺瓦片没有高程网格，按钮会先切 3D。
8. 坐南朝北/贴地是等价实现，原版行为未取证（见 §5.3）。
9. **路网视图没有实时路况**：天地图公开服务无路况图层（`GetCapabilities` 每端点仅 1 个底图图层），只做 `vec` 路网图面 + `cva` 注记，不画假红绿线（见 §5.5）。

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
- **真机 `am` 参数（2026-11-05 实测）**：本机 `L2E0222531014833` 的 `am` **只认 `--ef`（float），不支持 `--ed`**（照抄 `--ed` 会被拒，报 `Unknown option: --ed`）。给 `heading`/`declination`/`pitch` 等 float extra 传值一律写 `--ef value <float>`，例如 `am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 90.0`；正确写法见 `tests/at/smoke-android.ps1` 的 `Set-Heading()` 与 `docs/qa/device-method.md`。全树 `--ed` 现为 0 命中，新增示例不得引入。

## 9. 待办（下一轮）

- Java 侧 `LCAndroid` 桥 + `MapMarker` 模型（客户端，轮 C 后）。
- 俯视 0° 贴面清晰度：POT 2048 + mipmap 已落（`d8bc53c` 之后），真机复验待补（见 §7 2.1）。
- 双指缩放。
- 截图链路修复（PixelCopy / toDataURL 回传），归口待技术总监定。
- DEM 境外源是否换国内源（产品已列为待拍板项），换源只需改 `TERRARIUM` 常量与解码函数。

## 10. K-01 单页 / 罗盘浮层增补（2026-11-05）

> 背景：原版首页取证见 `docs/evidence/original-apk/README.md`（真机 1228×2700 / density 540）。原版**没有底部导航、没有 Tab**：首页＝全屏地图＋左列 8 工具＋右列 10 工具；点左列「罗盘」是**同一页面**的半透明浮层，不是第二个页面、也不是第二个 WebView。本增补只定义地图页与原生壳的接口；布局本身归客户端。

### 10.1 职责边界

- `map.html`：地图层（瓦片/3D）、相机、坐向数据、角注**文本**；`full` 模式保留自带控件，供无头 AT 与回退。
- 原生壳（`MainActivity`/`CompassView`）：单页布局、左/右工具列、罗盘圆盘与透明度、圆外触摸透传、底部工具条、角注**渲染**。
- 罗盘浮层是自绘，`uiautomator` 只能拿到 1 个原生节点；浮层几何只能由截图测量，不从 dump 取。

### 10.2 新增接口（已挂 `window`，页面不重载）

| 方法 | 参数 | 行为 |
|---|---|---|
| `setUiMode(mode)` | `'full'\|'map'\|'compass'` | `full`=现状（保留 `#controls` 等，无头 AT/回退）；`map`=隐藏 `#controls/#hint/#heading-label/#srcInfo/#fsExit/#hud`，保留瓦片/canvas 与 `#needle`，地图可拖动缩放；`compass`=在 `map` 基础上再隐藏 `#needle`（罗盘浮层自绘准星/北针）。返回实际模式。 |
| `getAttribution()` | — | 返回 `{source, review, original:"世纪空间", equivalent:true, text}`；`text` 形如「数据来自天地图地形（等价替代原版世纪空间）· 审图号：未确认」。 |
| `getState().uiMode` | — | 当前模式，供 AT 断言。 |
| hash `&uimode=full\|map\|compass` | — | 启动与 `hashchange` 应用，供无头截图/复现。 |

`window.__LC3D` 同步增加 `setUiMode`、`attribution` 两个别名。

### 10.3 罗盘模式三件事的验收

1. **半透明透出**：`map`/`compass` 模式下 map 隐藏全部自带 chrome（`#controls/#hint/#heading-label/#needle/#srcInfo/#fsExit/#hud`），不画任何遮罩，只留瓦片/canvas；圆盘 face 按 `docs/evidence/original-apk/measurements.md` 的实测：**圆心区 face α 合成上界 0.15（无整盘填充），线稿 α 0.90–1.00**，不要用 `argb(210)` 整盘填充。验收：罗盘模式截图中心区域可辨认底图纹理；`#controls` 的 `display==="none"`。
2. **仍可拖动缩放**：`#controls` 不占位、文本层 `pointer-events:none`、`#map{touch-action:none}`；`CompassView` 圆盘**外**的触摸必须透传回 WebView（客户端只消费圆内）。验收：圆外 50px 拖动后 `getState().lng/lat` 变化；`zoomIn()/zoomOut()` 生效；一次拖动不触发 `pick`。
3. **审图号/数据来源**：`getAttribution().text` 必须是「数据来自X（等价替代原版世纪空间）· 审图号：未确认」；**禁止**出现原版 `GS(2026)1219`（该号与世纪空间绑定，LC 用天地图属等价替代，不得照抄）。原生底部栏用该 `text` 渲染；`#srcInfo` 在 `map/compass` 隐藏但 `textContent` 保留，`captureMap()` 的角注合成仍可读。

**指针开关（两针分离，AT 判据按模式分）**：地图模式右列「指针开关」→ `toggleNeedle()`，作用于 map 的 `#needle`；`compass` 模式下 `#needle` 已隐藏，`toggleNeedle()` **只改内部状态、屏幕无效果**，此时「指针开关」由原生 `CompassView.setShowPointer(bool)` 承担（罗盘自绘的北针/准星）。AT 判定：地图模式断言 `#needle` 的 `display`；罗盘模式只能靠截图（浮层自绘，`uiautomator` 取不到内部控件，见 README §8）。`getState().needle` 始终表示 map 页指针状态，**不代表**罗盘浮层北针。

### 10.4 调用时序（客户端只需两处）

- `onPageFinished` → `setUiMode('map')`；
- 点「罗盘」→ `setUiMode('compass')` + `CompassView.VISIBLE`；点「隐藏」/返回 → `setUiMode('map')` + `CompassView.GONE`。
- 3D/等高线/±/定位 → 现有 `toggle3D()/toggleContour()/zoomIn()/zoomOut()/locate()`；不重载页面，始终同一 WebView。

### 10.5 不变项与副作用

- `setHeading/setSitFace/setBase/setMode/toggle3D/setPitch/rotateBearing/setView/zoomIn/zoomOut/locate/clearMarkers/getState/getFps` 语义不变。
- `#srcInfo` 文案格式从「底图：X · 审图号：Y」改为「数据来自X（等价替代原版世纪空间）· 审图号：Y」；`MainActivity.captureMap()` 读 `textContent`，不受 `display:none` 影响。
- `map/compass` 模式下 `#hud` **恒为 `display:none`**（不是丢失）：D-009 要求的「垂直放大 2.5× + 贴面类型」由原生 3D 态在合适位置显示，不在 WebView 内重复。
- 双 WebView 删除后，`setHeading/setSitFace` 只推一个实例（客户端改）。

### 10.6 验证入口

- 无头：`tools/map/run_at_p04.ps1` 仍跑 TD-001…TD-015；`setUiMode/getAttribution` 已加入 TD-015 的 window API 列表（现 16 项，AT 文档的「14 个方法」需测试侧同步）。

## 11. F-12 测量接口（2026-11-05，含原版打点对齐）

> 背景：原版 F-12 **不是点哪打哪**，是「拖图把中心对到目标 → 点『添加点』」，原版 JS `addCenterPoint(){ map.getCenter() → eventClick }`（逆向 `2ba81d0` 查实，合入 `a1ec582`）。客户端按这个重排 UI（两模式 + 添加点/撤销/完成/返回 + 坐/向方位角），地图侧只提供下面四个方法。

| 方法 | 参数 | 行为 |
|---|---|---|
| `lc_measure_start(mode)` | `'distance'`\|`'area'` | 进入测量态并清空已有点，发一次 `measure`（0 点） |
| `lc_measure_addCenter()` | — | **新增**：取当前地图中心（`center`）作为新测量点，走与点选打点同一个 `measureAdd()`；返回 `true`/`false` |
| `lc_measure_undo()` | — | 去掉最后一个点 |
| `lc_measure_clear()` | — | 退出测量态、清点，发 `measure-clear` |

**`lc_measure_addCenter()` 的落点口径**（与点选打点一致，测量算法未改一行）：

- 2D：`center` 即瓦片反算出的经纬度中心，直接落点；
- 3D：`render3D` 里相机 `lookAt` 的 `target` 就是 `center` + 该点 DEM 高程，所以屏幕正中那一点与 `demIntersectScreen()` 拾取到的是同一地表位置；海拔用 `elevAt(center)` 同源取（DEM 未就绪时可为 `null`，属正常）；
- **不产生立向点**：该接口不经 `placeMarker()`，测量态下 `placeMarker` 也早已直接 return，所以不会同时插立向标记；
- 未进入测量态时按「距离测量」起步（等价 `lc_measure_start('distance')`），避免客户端按钮点了没反应；按钮在测量态里调用则沿用当前 `distance`/`area` 模式。

事件、hash、`getState().measure` 与 F-12 原约定是同一套，没有新增事件：`measure {mode, points, distanceM, areaM2, text}`、`measure-clear`；hash `&measure=dist|area`；`getState().measure {on, mode, points, distanceM, areaM2}`。

无头验收（脚本入库）：`tools/map/measure-center-test.html`，仓库根目录 `python -m http.server 8792` 后打开，实测 7/7 PASS：

```
MC1 addCenter 1 点 points=1 mode=distance on=true
MC2 两个中心点（39.90,116.40 → 39.90,116.41）points=2 distanceM=853（haversine 期望 853）
MC3 lc_measure_undo() → points=1 distanceM=0
MC4 markers=0（不产生立向点）events=measure,measure,measure,measure 载荷 points=2/853/"853 m"
MC5 area 模式三点 areaM2=474275（局部 ENU + 鞋带，未改）
MC6 3D 中心打点 points=1 center=40.0000,116.0000 elev=316.9 demZoom=11
MC7 2D 取高程 elev=53.8（DEM 未就绪时为 null）
```
- 截图：`map.html#in3d&uimode=compass&lat=40.02&lng=115.45&z=12`（或 `&uimode=map`）。

## 12. MAP-DELTA 修复（2026-11-05，MD-05/07/08/09/11 + F-14）

### 12.1 海拔查询的前置条件（MD-07，行为未改，只把契约说清）

`elevAt(lat,lng)` **只读已加载的 DEM 缓存**，而 DEM 瓦片只由 3D 的 `startPatch()` 拉取。所以：

- **3D 模式**：视野内 DEM 已加载，取得到高程；
- **纯 2D 且从未进过 3D**：返回 `null`（页面据此显示「该点无高程数据」，不是「0 米」）；
- **2D 但已进过 3D**：只有落在已加载 DEM 范围内的点有值，视野外仍是 `null`。

客户端要保证「海拔查询」有结果，进页时先 `setMode('3d')`（或用 hash `#in3d`）等 DEM 就绪；`ElevationActivity` 目前就是这么做的。
`lonLatToWorld()` 对 `null` 高程仍按 0 处理（3D 点位贴海平面），这条本轮未改，属 3D 点位后续修正项。

### 12.2 桥入参条数上限（MD-09）

| 接口 | 上限 | 超限行为 |
|---|---|---|
| `setOverlay(kind, points)` | 2000 点 | 按顺序截断，事件 `overlay` 增 `dropped`（被丢条数） |
| `setOverlayLayer(id,on,{items})` | 2000 点/层 | 同上，事件 `overlay-layer` 增 `dropped`；返回值也带 `dropped` |
| `setFishLayer(on, items)` | 500 点 | 事件 `fish` 增 `dropped` |
| `lc_track_load(segments)` | 200 段 / 20000 点 | 事件 `track-loaded` 增 `dropped`；`getState().track` 的 `segments/points` 是**实际收下**的数量 |

超限不报错、不静默：事件里一定带回 `dropped`，客户端可据此提示「数据过多，已截断 N 条」。

### 12.3 Wayback 释放号白名单（MD-11）

释放号来自外部 `waybackconfig.json`，**只接受纯数字 1–8 位**（实测 198 个时相都是 5 位：`43291`/`63116`/`26334`）。
两侧各校验一次：`HistoryImageActivity.isSafeReleaseId()` 在拼 `evaluateJavascript` 前；`map.html` 的
`wbSafeRelease()` 在 `setWayback/setWaybackRelease/tileUrl` 里。非法值一律退回默认时相 `43291`，不会进入 JS 字符串或 `<img src>`。

### 12.4 F-14 `fish-pick` 事件补 `address` 与类型码

```js
{ type:"fish-pick", name:"密云水库", kind:"库", typeCode:303,
  address:"北京市密云区", lat:40.49, lng:116.84 }
```

类型码按**天地图分类来源**映射（不按名字猜），与 F-14 文档的原版 payType 对齐：

| 来源 | 单字 | typeCode | 原版文案 |
|---|---|---|---|
| 天地图 260205 湖泊/水库 | 库 | 303 | 库钓 |
| 天地图 260204 河流 | 河 | 308 | 河钓 |
| 本机自建钓点 | 塘 | 0 | 其它（塘钓） |
| （无公开数据源，不产出） | 黑 | 309 | 黑坑 |

`address` 取自天地图分类搜索结果，没有就是空串。

### 12.5 轨迹跟随与光标（MD-05）

2D 下 `follow` 不再每帧 `render()`：光标是一个复用的 `<circle>`，逐帧只改 `cx/cy`；
当前点离开画面中心 30% 安全框时才平移相机并重绘瓦片；整条轨迹（已播放段着色）按 ~8 Hz 重绘。
3D 下 `ensurePatch()` 自带「瓦片没变就早退」，行为不变。

### 12.6 超清图（MD-08，Java 侧）

- 瓦片缓存 `cacheDir/tdt_img18`：**6000 张 / 200 MB** 双上限，开跑前按 `lastModified` 做 LRU 清理；
- 单瓦片失败重试（含首次最多 3 次，间隔 120/300 ms），三次都失败才计失败、才可能作废整图；
- 成品改 **JPEG 95**（`.jpg`，影像无 alpha），成品目录保留最新 20 张且 ≤ 200 MB。

### 12.7 回放页 `uimode=canvas`（2026-11-05）

轨迹回放页原来露出 map.html 左侧整列工具（影像/地形/放大…），还挡原生「1×」按钮。新增模式：

- `setUiMode('canvas')` / 别名 `lc_setUiMode('canvas')` / hash `&uimode=canvas` 或 `&ui=none`；
- 隐藏 `#controls`（左侧工具列+底图控件）、`#hint`、`#heading-label`、`#srcInfo`、`#needle`、`#hud`、`#srcPanel`，
  只留瓦片/canvas 与轨迹/测量覆盖层；相机的平移缩放**照常可用**；
- `getState().uiMode === "canvas"` 供 AT 断言；**默认（不传）仍是 `full`**，主页面行为不变；
- `TrackReplayActivity` 已改用 `MapAsset.url("#in3d&uimode=canvas")`。

无头验收：`tools/map/uimode-canvas-test.html`（6/6 PASS）——
`C1 uiMode=canvas` / `C2 controls/hint/srcInfo 全 none` / `C3 needle none` /
`C4 相机与轨迹层仍工作（trackNodes>0）` / `C5 'full'→lc_setUiMode('canvas') 生效` /
`C6 不带 uimode 的对照页仍是 full、#controls 可见`。

### 12.8 key 迁移与 MD-10 收口（2026-11-05）

- 旧的原版内置 key 已失效（天地图 `301001 非法key`，三种 UA 全 403），山/水/钓点/逆地理曾一起空——
  **不是 F-14 字段改动导致的回归**。
- 现方案：`MapKeys.TDT = BuildConfig.TDT_SERVER_KEY`（原生，默认 UA）；`MapAsset.url(hash)` 把
  `BuildConfig.TDT_WEB_KEY` 以 `?tk=` 交给 map.html；仓库零 key。
- MD-10 从「登记风险」改为**已消除**：`LcHttp` 默认不设 UA（伪装默认关），实测矩阵见 §2.3。
- PC 复核（服务端 key + `okhttp/4.12`）：geocoder `HTTP 200 status=0 province_code=156370000`；
  v2/search 山 260203/山东 `HTTP 200 infocode=1000 count=117`；湖 260205/湖南 `count=36`；
  WMTS 瓦片 `HTTP 200 image/jpg 12,033 B`。
