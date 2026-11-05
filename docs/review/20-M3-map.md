# 20-M3 地图 / 3D 模块审查报告（第一批 + 第二批 + 复核新增）

> 审查基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`），工作区 `E:\APPDetector\review-base`。
> 范围（M3）：`app/android/app/src/main/assets/map.html`、`tools/map/**`、`docs/map-bridge.md`，以及对 Java 侧 JS 桥调用契约的只读核对（`MainActivity` / `LCAndroid` / `DebugBridge` / `MapMarker`，问题记在本模块）。
> 纪律：只读基线；每条发现给「文件相对路径 + 行号 + 代码原文」；证据等级只有 已证实 / 高度可疑 / 待确认；未读文件列入 §9「未覆盖文件逐文件清单」。
> 路径约定：`map.html`=`app/android/app/src/main/assets/map.html`；`MainActivity.java`/`CompassView.java`/`LCAndroid.java`/`DebugBridge.java`/`MapMarker.java`=`app/android/app/src/main/java/com/luopan/fengshui/` 下同名文件；`activity_main.xml`=`app/android/app/src/main/res/layout/activity_main.xml`。正文用简称，表/结论均按此展开为仓库相对路径。
> 本报告**不修改任何源码**，只写本文件。

## 0. 结论摘要

- **未发现「阻断」级**（无崩溃、无数据损坏路径）。
- **去重裁定（总监 2026-11-05）**：DebugBridge 导出广播注入归 **B-02**；三 Tab/罗盘浮层归 **B-03 + B-04**（K-01 母条）；触摸停跟随归 **B-05**。本报告 C-02、C-07、C-16 只作交叉引用、**不计条**；天地图 key 明文归 **C**（C-01），M6 只引 D-006 冲突。
- 第一批（去重后计条）**18 条：已证实 14 / 高度可疑 2 / 待确认 2**；高 5、中 10、低 3。
- 第二批 **13 条：已证实 13**（中 8 / 低 5）：C-22…C-34。
- 第三批（本轮复核新增）**3 条：已证实 3**（中 2 / 低 1）：C-35…C-37，见 §7.1。
- 合计 **34 条 + 3 条交叉引用**：已证实 30 / 高度可疑 2 / 待确认 2；高 5 / 中 20 / 低 9。
- **本轮复核（2026-11-05 第二轮）**：`git diff --stat review-20261105 HEAD -- app tools docs/map-bridge.md` 为空，源码与基线标签逐字一致；34 条逐条回源码核对行号与片段。订正 7 处：C-01 外部实测补可复现命令与本次结果、C-03 pick 出口补「elev:0」分支、C-08 贴地眼高公式与 z18 数值、C-09 纬度阈值 84.8°→84.93°、C-10 反经线越界量按 `patch.z` 分档、C-27 片段跨段拼接、§6.1 按钮计数 20→18 个按钮 + 1 个滑块。
  **下游计数影响**：`60-FINAL.md`/`99-FINAL-REVIEW.md` 记 M3 为「31 计条 + 3 交叉引用」，本轮新增 C-35…C-37 后应为「34 计条 + 3 交叉引用」，需由成文侧按 delta（+3：中 2 / 低 1）更新，编号不复用。
- 六项必查结论：① DEM 失败降级**不成立**（C-05）；② 相机/pick 边界有 3 处已证实问题（C-08/C-09/C-10）；③ `VEXAG` 与 pick **是同一变换空间**（无缺陷），另有精度偏差（C-11）；④ `__LC3D` **不互相污染**，但有 Java 侧共享字段与双份资源（C-12/C-13/C-19）；⑤ 天地图 key **明文、且是原版 key**（C-01），注入原语引 B-02；⑥ `map-bridge.md` 与实现**契约破裂**（C-06）。
- 已知缺陷「底部三 Tab / 罗盘浮层」：**成立**，级别 **高**（母条 B-03 + B-04；C-07 交叉引用，专项见 §6）。

### 0.1 条目总表

| 编号 | 严重度 | 证据等级 | 批次 | 一句话 |
|---|---|---|---|---|
| C-01 | 高 | 已证实 | 一 | 天地图 key 明文写在 assets 与文档，且是原版 key（违反 D-006「个人 key」） |
| C-02 | — | 已证实 | 交叉引用（母条 B-02，不计条） | 导出调试广播可在两个地图 WebView 执行任意 JS / 加载任意 URL；本条只留地图侧证据 |
| C-03 | 高 | 已证实 | 一 | 3D 地形块最外一行/列 193 个顶点高程恒为 0，边缘成「海平面悬崖」并污染 pick |
| C-04 | 高 | 已证实 | 一 | 2D 拖拽方向与手指相反，且与 3D 拖拽方向相反 |
| C-05 | 高 | 已证实 | 一 | DEM 任一瓦片失败即整块拒绝，3D 无自动降级，底图层又被隐藏 → 纯色空白 |
| C-06 | 高 | 已证实 | 一 | `map-bridge.md` §5 声明的 ready/mode/error/fps 事件实现中不存在，§5/§7 桥状态已过期 |
| C-07 | — | 已证实 | 交叉引用（母条 B-03+B-04，不计条；级别高） | 罗盘/地图是同一 map.html 的两个 WebView 实例；罗盘为 Tab 内常驻顶部覆盖层；三 Tab 无原版依据 |
| C-08 | 中 | 已证实 | 一 | 「贴地」相机无地形高度约束，山地会把眼点压进山体；低缩放时实际是数十公里高空 |
| C-09 | 中 | 已证实 | 一 | 纬度 >≈84.9° 时 DEM 瓦片 y<0 全部为空 → 3D 必然失败；`setView`/hash 的 lat 完全不校验 |
| C-10 | 中 | 已证实 | 一 | 跨反经线时 pick 的 lng 可越界（<-180），页面已 emit，Java 桥直接拒收 |
| C-11 | 中 | 已证实 | 一 | `VEXAG` 与 pick 同一变换空间（无缺陷）；但 pick 高度用逐像素双线性，与 96×96 渲染网格插值不一致 |
| C-12 | 中 | 已证实 | 一 | 双 WebView 的 `__LC3D` 互不污染；Java 侧 `mapMarker` 是唯一共享状态且从未被读取 |
| C-13 | 中 | 已证实 | 一 | 每个传感器样本向两个 WebView 各注入 2 条 JS（4 次/样本），含隐藏实例 |
| C-14 | 中 | 已证实 | 一 | 2D 每帧 innerHTML 重建约 30 个瓦片 `<img>`，无复用/无缓存，加载失败静默隐藏 |
| C-15 | 中 | 已证实 | 一 | 单实例 3D 资源量级约 40–60 MB（推算）；无 `webglcontextlost` 恢复路径 |
| C-16 | — | 已证实 | 交叉引用（母条 B-05，不计条；级别取高=高；核验已定：定性成立） | 罗盘覆盖层吃掉触摸且一触即停传感器跟随；翻坐向也停跟随，只有「复位」能恢复 |
| C-17 | 低 | 已证实 | 一 | `map-bridge.md` 4 处实现细节漂移（clearMarkers 语义、跨块白屏、证据路径指向 output 脏树、实例描述过期） |
| C-18 | 中 | 高度可疑 | 一 | `#extension GL_OES_standard_derivatives` 在 `getExtension` 之前编译；严格实现下 shader 失败会整体退回 2D，`#ifdef` 回退不可达 |
| C-19 | 低 | 高度可疑 | 一 | Tab 隐藏/App 后台时是否停 3D 渲染只靠 IntersectionObserver/visibilitychange，`onPause` 未 pause WebView |
| C-20 | 中 | 待确认 | 一 | PRD 记录真机「点定位无可见变化」，基线代码未定位原因；缺 logcat/geolocation 回调证据 |
| C-21 | 低 | 待确认 | 一 | 原版罗盘叠层的透明度/尺寸/是否居中无 review-base 内对照证据 |
| C-22 | 中 | 已证实 | 二（本批） | 2D 立向点按屏幕像素定位，平移/缩放后不跟地图走 |
| C-23 | 中 | 已证实 | 二（本批） | 2D 旋转/坐向后拖拽未按 bearing 分解，方向随旋转角偏离 |
| C-24 | 中 | 已证实 | 二（本批） | 2D 指针角度未减 bearing（3D 减了），地图旋转后指针不再指地图北 |
| C-25 | 中 | 已证实 | 二（本批） | 「全屏」只隐藏 WebView 内控件，原生罗盘/底部面板/底部栏仍在 |
| C-26 | 中 | 已证实 | 二（本批） | 3D 默认贴面是影像（drape=img），角注/截图角注仍显示「地形」（base=ter） |
| C-27 | 中 | 已证实 | 二（本批） | AT 断言弱于文档：TD-002 不查顶点数、TD-003 不查 36/36；真机脚本只数 ≥5 条 pick；环境失败被判 FAIL |
| C-28 | 低 | 已证实 | 二（本批） | pick 射线离开地形块后不提前退出，单次点选固定跑满 ~1140 步 ×4 次采样 |
| C-29 | 中 | 已证实 | 二（本批） | 页面错误不可观测：`emit` 静默吞异常、无 `window.onerror`、未覆写 `onConsoleMessage`，release 无上报 |
| C-30 | 低 | 已证实 | 二（本批） | `#in3d&sit` 入口硬编码 `setHeading(90)`；`&z=<9` 可绕过「3D 最小 z9」 |
| C-31 | 低 | 已证实 | 二（本批） | 进 3D 强制 zoom≥9，返回 2D 不恢复原 zoom（用户看一次 3D 后 2D 缩放被改） |
| C-32 | 低 | 已证实 | 二（本批） | 贴面构建 `ok<30` 即放弃且不重试（texCanvas 保持 null），只有 console 日志 |
| C-33 | 低 | 已证实 | 二（本批） | `map.html` 单文件 1605 行、20+ 全局可变状态、无模块边界；测试入口 `#at/#selftest` 混在生产代码 |
| C-34 | 中 | 已证实 | 二（本批） | 第二份 AT（probe）要求的 `__LC3D.probe/pick/state().vexag` 不存在，两份 AT 编号体系冲突 |
| C-35 | 低 | 已证实 | 三（复核新增） | `setSitFace` 用 `data-sit` 单向锁存顶部标签，`setHeading` 的度数分支成为死代码、无清除路径 |
| C-36 | 中 | 已证实 | 三（复核新增） | 2D 截图退路抓的是隐藏的 `#gl` 画布：API 24–25 的 2D 截图必为空白图 |
| C-37 | 中 | 已证实 | 三（复核新增） | 3D 高 zoom 网格与 DEM 级别脱钩：zoom 18 时单屏宽不足 1 个网格单元，地形退化为单个面片 |

---

## 1. 阻断 / 高（正式缺陷清单）

> 本节**计条 5 条**：C-01、C-03…C-06。C-02→B-02、C-07→B-03+B-04 为交叉引用，不计条。

### C-01 天地图 APIKEY 明文入库、入包，且用的是原版 key（违反 D-006）

- **严重度**：高　**证据等级**：已证实　**批次**：一
- **位置**：
  - `app/android/app/src/main/assets/map.html:92`
  - `docs/map-bridge.md:41`
  - `docs/TEAM_CHARTER.md:107`（D-006）
- **代码原文**：
```js
var KEY = "dd18858b33229f5837ad1d97b8d3105e";
```
  `docs/map-bridge.md:41`：
```md
`KEY` 为原版内置天地图 key（`dd18858b33229f5837ad1d97b8d3105e`），未改动。商用配额/换 key 属产品决策，未确认。
```
  `docs/TEAM_CHARTER.md:107`：
```md
| D-006 | 2026-11-05 | 不收费：无会员/内购/广告 SDK；天地图用个人 key（1 万/日/服务），不自建瓦片服务、不申请商用授权 | 避免触《地图管理条例》测绘资质与派生产品条款 |
```
- **触发条件**：任何人解包 APK、读取仓库或本文档即可拿到 key；每次瓦片请求 URL 都带 `&tk=`（`map.html:127-133`），3D 贴面同样走 `tileUrl`（`map.html:894`）。
- **预期 vs 实际**：
  - 预期：按 D-006 用**用户个人 key**，且 key 不进源码/不进 git 历史（构建注入或服务端代理）。
  - 实际：key 是**原版 APK 内置 key**（文档自认「未改动」），明文写在 assets 与文档；`git log -S` 显示该 key 自基线提交 `4770c20` 起就在历史里，删代码无法撤回，只能换 key。章程自身还有第二口径：`docs/TEAM_CHARTER.md:94`（§7 风险 3）把「天地图 APIKEY 为内置常量」当现状接受、只要求「避免异常高频请求拖垮个人配额」，与 D-006「用个人 key」相互矛盾（此矛盾即 M6 `G-06` 交叉引用的对象，不另计条）。
- **补充实测证据（2026-11-05 复核，非真机，单次 GET 不批量）**：命令如下（`<KEY>` = `map.html:92` 的值，此处不重复贴 key）：

```powershell
Invoke-WebRequest -Uri "https://t0.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX=10&TILEROW=388&TILECOL=843&tk=<KEY>" -Headers @{Origin="https://example.com"} -UseBasicParsing
```

  实测（z10/843/388 北京瓦片）：`STATUS=200`、`Content-Type=image/jpg`、`Bytes=21833`、`Access-Control-Allow-Origin=*`。说明该 key 未被原版包名/域名限制，任何客户端可直接用其配额。
- **影响**：① 配额是原版开发者的，被滥用/被封会同时打断 LC 全部地图（含 3D 贴面）；② 违反本团队 D-006；③ key 随公开仓库/APK 扩散，构成供应链式的第三方资源滥用。
- **修复方向**：向天地图申请 LC 自己的 key → 由构建脚本注入（如 `local.properties`→`BuildConfig`→`evaluateJavascript` 或 asset 模板替换），不写入 git；旧 key 立即在天地图控制台吊销/换新（历史已泄露）。

### C-02【交叉引用，不计条】导出调试广播可在地图 WebView 执行任意 JS / 加载任意 URL

> **总监去重裁定（2026-11-05）**：与 B-02 同因，**母条归 B**（LC审客户端）。本条只保留地图侧注入原语与 key 读取面的证据，供 B-02 与核验引用，不计入 M3 条目数、不进本模块正式缺陷清单。

- **严重度**：—（母条 B-02 为高）　**证据等级**：已证实（供 B-02 引用）　**批次**：交叉引用
- **位置**：
  - `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:184-205`（`url` / `js` 命令）
  - `MainActivity.java:240`（`RECEIVER_EXPORTED`）
  - `MainActivity.java:373-383`（JS 开启 + `LCAndroid` 桥）
  - `MainActivity.java:167-232`（`handleDebugCommand` 无 debug 门禁）
  - `app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java:22`（action 常量）
- **代码原文**：
```java
            case "url": {
                String url = it.getStringExtra("value");
                if (url != null) {
                    if (mapView != null) mapView.loadUrl(url);
                    if (compassMapView != null) compassMapView.loadUrl(url);
                }
                break;
            }
            case "js": {
                String code = it.getStringExtra("value");
                if (code != null) {
                    // ValueCallback -> logcat, so "did the injection run" is observable.
                    if (mapView != null) {
                        mapView.evaluateJavascript(code,
                                v -> android.util.Log.d("LCJS", "mapView -> " + v));
                    }
                    if (compassMapView != null) {
                        compassMapView.evaluateJavascript(code,
                                v -> android.util.Log.d("LCJS", "compass -> " + v));
                    }
                }
                break;
            }
```
```java
                registerReceiver(debugBridge, f, android.content.Context.RECEIVER_EXPORTED);
```
```java
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setGeolocationEnabled(true);
        webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
```
- **触发条件**：同机任意 App 发送广播，无需权限、无需用户点击：
  `am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "..."`，或 `--es cmd url --es value "javascript:..."`。
- **预期 vs 实际**：
  - 预期：调试命令只在 debug 包、且由签名权限保护；release 包不存在 `js`/`url` 通道。
  - 实际：接收器 `RECEIVER_EXPORTED` 且未声明任何权限；`handleDebugCommand` 内部没有 `isDebuggable()` 门禁（`isDebuggable()` 只用于环形日志，`MainActivity.java:664,693-695`），release 包同样可被执行。
- **利用后果（已证实的代码路径）**：注入的 JS 可读取 `map.html:92` 的 `KEY` 并外发（普通 `<img>` GET 不受 CORS 限制）；可调用 `window.LCAndroid.onEvent(...)` 伪造任意 `pick`；可用 `url` 命令把 WebView 导航到攻击者页面做 UI 欺骗（页面仍在 App 进程内）。
- **归属说明**：接收器本身属 M2（LC审客户端 已按总监要求核 DebugBridge 权限），本条只定「地图 WebView 侧的注入原语 + key 读取面」；修复需两侧同时做。
- **修复方向**：M2 侧把接收器改为 `RECEIVER_NOT_EXPORTED` 或加 `signature` 权限，并整段用 `if (!BuildConfig.DEBUG) return;` 包住；地图侧把 key 改为运行时注入（C-01），使注入 JS 拿不到常量。

### C-03 3D 地形块最外一行/列 193 个顶点高程恒为 0（每块边缘一圈悬崖，pick 命中假地面）

- **严重度**：高　**证据等级**：已证实（静态推导 + 按同函数复现）　**批次**：一
- **位置**：
  - `map.html:518-520`（`PATCH_TILES=3`、`PATCH_PX=768`、`GRID=96`）
  - `map.html:771-777`（只加载 3×3 瓦片）
  - `map.html:578-595`（`demPix`/`demAt`）
  - `map.html:812-848`（网格取样与顶点高度）
- **代码原文**：
```js
var PATCH_TILES = 3;                 // 每块地形 3×3 个 DEM 瓦片
var PATCH_PX = PATCH_TILES * 256;    // 768
var GRID = 96;                       // 网格 96×96 顶点
```
```js
  for (var j = 0; j < PATCH_TILES; j++) {
    for (var i = 0; i < PATCH_TILES; i++) {
      var x = wrapX(ox + i, n), y = oy + j;
      if (y < 0 || y >= n) { jobs.push(Promise.resolve(null)); continue; }
      jobs.push(demTile(z, x, y).catch(function () { return null; }));
    }
  }
```
```js
function demAt(z, gx, gy) {
  var x0 = Math.floor(gx), y0 = Math.floor(gy);
  var fx = gx - x0, fy = gy - y0;
  var a = demPix(z, x0, y0), b = demPix(z, x0 + 1, y0);
  var c = demPix(z, x0, y0 + 1), d = demPix(z, x0 + 1, y0 + 1);
  if (a === null || b === null || c === null || d === null) return 0;
  return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy;
}
```
```js
      H[j * (N + 1) + i] = demAt(p.z, p.ox * 256 + i * step, p.oy * 256 + j * step);
```
```js
      var h = H[k] * VEXAG;
```
- **推导**：`step = 768/96 = 8`；`i=N=96` 时取样像素 `px = ox*256 + 768 = (ox+3)*256`，落在第 4 块瓦片，而第 4 块没加载 → `demPix` 返回 `null` → `demAt` 命中 `return 0`。`j=N` 同理。于是右边缘一列 + 下边缘一行共 `97+97-1 = 193` 个顶点 `H=0`（海拔 0，再乘 `VEXAG` 仍为 0）。
- **复现**：把 `map.html:533-595` 的 `wrapX/demPix/demAt` 原样搬出，9 块瓦片填同一常量，按 `buildMesh` 取样 9409 点，结果「193 个样本不等于常量，且全部满足 i=96 或 j=96」。
- **触发条件**：任意 3D 视图；俯仰 45°/60° 时地形块边缘就在视锥内（雾段 `[0.75R,1.05R]` 只能遮一部分）。
- **预期 vs 实际**：
  - 预期：边缘顶点钳到最后一个有效像素，或补取第 4 列/行瓦片；边缘高度应连续。
  - 实际：每个块的最外一列/一行被压到 0，形成一圈向下的断崖；边缘法线用 0 高邻居求差（`map.html:831-842`），法线方向也是错的。
- **对 pick 的二次影响**（复核重写：pick 的高度场是逐像素 `demAt`，不是渲染网格，两者在此处并不一致）：
  - **可见悬崖只存在于网格**：`demAt` 的 x0+1 只有在落点进入最外 1 个 DEM 像素时才跨到未加载的第 4 块；网格上那圈约 `8 px × mpp`（z12≈234 m）宽的斜坡由 GPU 在 i=95→96 之间把真实高程插值到 0。于是同一块边缘出现两个面：pick 认为地面仍是真实高程，渲染却已下陷到 0 → 标记会浮在可见崖面之上（与 C-11 同源）。
  - **出口值两支，都违反 D-005**：射线贴边/掠射（y≈0 与边界相交）落到最外像素带时——
    - `elev: 0`：落点像素在已加载块内 → `elevRaw` 非 null（`map.html:1120`），但 `demAt` 因邻像素缺失返回 0（`map.html:1121,593`）→ emit `elev:0`；`LCAndroid.java:51-55` 只拒绝 NaN，0 被当成真实海平面存进 `MapMarker.elevM`。
    - `elev: null`：落点像素本身越界（第 4 块）→ `elevRaw` 为 null → emit `elev:null`。
  两支的经纬度都落在块边缘，页面与 Java 侧都无法区分「真实海平面」与「缺数据」。
- **产品 AT 已点名同一根因**：`docs/qa/AT-P0-4-3d-probe.md:20` 写「现 `demAt()` 缺块返回 0，会造『假海平面』」——基线 `map.html:593` 仍是 `return 0`，说明这是「要求了没实现」，不是评审新发现的口径分歧。
- **修复方向**：取样像素钳到 `[ox*256, (ox+3)*256-1]`（或 `demPix` 对缺邻居退化为最近有效像素，而不是 0）；同时把 `demAt` 的「缺数据」与「海拔 0」区分开，不要共用 `return 0`。

### C-04 2D 拖拽方向与手指相反，且与 3D 拖拽方向相反

- **严重度**：高　**证据等级**：已证实（代数推导）　**批次**：一
- **位置**：
  - `map.html:424-434`（2D `pan`）
  - `map.html:436-449`（鼠标入口，传 `lastX - clientX`）
  - `map.html:494-502`（触摸入口，传 `-dx`）
  - `map.html:980-993`（3D `pan3D`，同一符号约定）
- **代码原文**：
```js
function pan(dxPx, dyPx) {
  if (mode === "3d") { pan3D(dxPx, dyPx); return; }
  var n = Math.pow(2, zoom);
  var c = lonLatToTile(center.lng, center.lat, zoom);
  c.x -= dxPx / 256;
  c.y -= dyPx / 256;
  center.lng = c.x / n * 360 - 180;
```
```js
  pan(lastX - e.clientX, lastY - e.clientY);
```
```js
    pan(-dx, -dy);
```
```js
  /* 拖拽语义：手指向右=看西侧（中心西移），手指向下=看北侧（中心北移） */
  var mx = rx * dx * g - fx * dy * g;        // 东向
  var mz = rz * dx * g - fz * dy * g;        // 南向
  center.lng += mx / (111320 * Math.cos(center.lat * Math.PI / 180));
  center.lat -= mz / 110540;
```
- **推导**：两处调用都把「手指位移 Δ」转成 `-Δ` 再进 `pan`。
  - 3D：`dx=-Δ`，`mx=rx*dx*g=-Δg` → `center.lng` 减小（西移）→ 画面内容右移，跟随手指。
  - 2D：`dxPx=-Δ`，`c.x -= (-Δ)/256 = +Δ/256` → `center.lng` 增大（东移）→ 瓦片 `left = x*256 - tx` 整体左移，内容与手指反向。
- **触发条件**：2D 模式（默认模式）单指拖动，或鼠标拖动。
- **预期 vs 实际**：
  - 预期：两种模式语义一致（手指向右 → 地图内容右移，中心西移）。
  - 实际：2D 内容左移，3D 内容右移；同一次手势在两个模式里方向相反。
- **修复方向**：统一成 3D 的语义——2D 改成 `c.x += dxPx / 256; c.y += dyPx / 256;`（或把两处调用改成不取负），并用一次真机拖动回归锁死。

### C-05 DEM 任一瓦片失败即整块拒绝，3D 无自动降级；旧块还会滞留在错误位置

- **严重度**：高　**证据等级**：已证实　**批次**：一
- **位置**：
  - `map.html:517`（唯一 DEM 源：AWS S3）
  - `map.html:751-810`（`ensurePatch`/`startPatch`）
  - `map.html:781-788`（任一 `null` 即整块失败）
  - `map.html:799-809`（失败分支，`catch` 从 799 起）
  - `map.html:212-214`（3D 下隐藏 2D 底图层并显示 GL canvas）
  - `map.html:1198-1204`（无 patch 时只清屏）
  - `map.html:216-219`（只有 WebGL 失败才自动回 2D）
- **代码原文**：
```js
var TERRARIUM = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/";
```
```js
  Promise.all(jobs).then(function (tiles) {
    if (seq !== patchSeq) return;
    for (var i = 0; i < tiles.length; i++) {
      if (!tiles[i]) {
        console.log("LC3D ERROR dem-missing");
        setHud("DEM 加载失败");
        loadingKey = null;
        return;
      }
    }
```
```js
    } catch (e) {
      console.log("LC3D ERROR mesh " + e.message);
      setHud("地形构建失败");
      loadingKey = null;
    }
  }, function () {
    if (seq !== patchSeq) return;
    console.log("LC3D ERROR dem-load");
    setHud("DEM 加载失败");
    loadingKey = null;
  });
```
```js
    baseLayer.style.display = "none";
    annoLayer.style.display = "none";
    glCanvas.style.display = "block";
```
```js
  if (!patch || !patch.mesh) {
    gl.clearColor(0.47, 0.55, 0.62, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    return;
  }
```
- **触发条件**：进入 3D 后任一 DEM 瓦片两次失败（离线、S3 被阻断/限流、境外网络抖动、配额异常）。`demTile` 只有 2 次尝试、600 ms 间隔、无超时（`map.html:539-573`）。
- **预期 vs 实际**：
  - 预期：保持 2D 可见，或降级到更低级别 DEM/纯高程配色，或自动回 2D 并给出可恢复提示。
  - 实际：9 个 job 任一 `null` → HUD 写「DEM 加载失败」→ `patch` 不建立；因为 3D 下 `baseLayer/annoLayer` 已 `display:none`，画面只剩 `#788c9e` 清屏色；只有用户手动点「返回 2D」才恢复。首次失败后每次平移都会再试 9 块 ×2 次，持续打点。**且该提示只存活到下一次 FPS 刷新**：`loop` 每秒调一次 `updateHud()`（`map.html:1186-1189`），无 patch 时 `updateHud` 又写回「DEM 加载中…」（`map.html:1302`），故障信息 ≤1 s 即被覆盖成「加载中」。
- **旧块滞留（同一路径的第二种表现）**：若之前已有 `patch`，新的 `startPatch` 失败时旧 `patch` 不会被清空，`render3D` 继续画旧块；但相机目标 `target` 已按新 `center` 计算（`map.html:1213-1221`），于是屏幕上是**旧位置的地形**，比空白更具误导性。
- **文档冲突**：`docs/map-bridge.md:204` 写「跨块瞬间可能有 <1 s 空窗（旧块继续渲染直到新块就绪，无白屏）」——只覆盖成功路径；首块失败/换块失败时是纯色或错位旧块，文档没写。
- **产品 AT 的验收口径（反证「降级只是可选」）**：`docs/qa/AT-P0-4-3d-probe.md:78` 的 TD-012 明确要求「飞行模式 → 进 3D，≤5 s 内回落 2D 并提示；`LC3D ERROR dem-load` 出现；不无限『加载中』」。基线没有回落逻辑，这一条按现状必然 FAIL——缺降级是「要求了没实现」。
- **修复方向**：① 任一瓦片失败时把整块降级为「只有成功瓦片区域」或降到 `z-1` 再试；② 首块失败自动 `setMode('2d')` 并 toast；③ 换块失败时清空 `patch` 或冻结相机在旧块中心，避免错位；④ 给 `demTile` 加超时与失败计数日志（可被 AT 断言）。

### C-06 `map-bridge.md` §5 的四类事件在实现中不存在，§5/§7 的桥状态已过期（契约破裂）

- **严重度**：高　**证据等级**：已证实　**批次**：一
- **位置**：
  - `docs/map-bridge.md:112-117`（称无 `addJavascriptInterface`、并列 4 类事件）
  - `docs/map-bridge.md:201`（称 Java 桥未接）
  - `map.html:1004-1010`（唯一 emit 实现）
  - `map.html:472`、`map.html:1142`（仅两处 emit 调用）
  - `MainActivity.java:377-383`（桥实际已接）
- **代码原文**：
  `docs/map-bridge.md:112`：
```md
- 当前 `MainActivity.createMapWebView()` **没有** `addJavascriptInterface`，`window.LCAndroid` 不存在，页面全程静默不报错。
```
  `docs/map-bridge.md:117`：
```md
- 已定义事件：`ready`（WebGL 初始化完成）、`mode`（2D/3D 切换）、`error`（`{code:"dem|webgl|shader|texture"}`）、`fps`（每 5 s）。
```
  `docs/map-bridge.md:201`：
```md
1. 3D 点选已可用（§5.1），但 **Java 桥未接**：真机上只有 `console.log("LC3D pick …")`，事件不会到达客户端，等轮 C 后补 `addJavascriptInterface`。
```
  `map.html:472` / `1142`：
```js
  emit({ type: "pick-clear" });
```
```js
  emit(msg);
```
- **触发条件**：任何按 `docs/map-bridge.md` 实现客户端的开发者：会以为桥不存在、会等 `ready/error/fps` 事件判断加载状态。
- **预期 vs 实际**：
  - 预期：文档声明的事件与实现一一对应；桥状态与基线一致。
  - 实际：`emit` 只有 `pick` 与 `pick-clear` 两种；`ready/mode/error/fps` 全文 0 次发送；`MainActivity.createMapWebView()` 已在 `377-383` 调用 `addJavascriptInterface`。文档同时自相矛盾（§5 说没桥，§5.1/§7 说桥待补）。
- **影响**：这是接口契约文档，客户端按它写会拿到永远不触发的回调；同时 `error` 事件缺失使 C-05 这类失败无法被 Java 侧感知。
- **修复方向**：二选一——要么在 `initGL/setMode/startPatch` 失败与 `loop` 的 5 s 日志处补 `emit({type:"ready|mode|error|fps"})`；要么把文档 §5 的事件表删掉，只保留 `pick`/`pick-clear`，并更新 §5/§7.1 的桥状态。

### C-07【交叉引用，不计条】罗盘与地图是同一 map.html 的两个 WebView 实例；罗盘为 Tab 内常驻顶部覆盖层；三 Tab 无原版依据（已知缺陷）

> **总监去重裁定（2026-11-05）**：K-01 母条 = **B-03 + B-04**（LC审客户端），本条只保留地图侧证据与 §6 专项分析，**不计入 M3 条目数**；级别按母条为 **高**。

- **严重度**：—（母条 B-03+B-04 为高）　**证据等级**：已证实（供母条引用）　**批次**：交叉引用（专项展开见 §6）
- **位置**：
  - `app/android/app/src/main/res/layout/activity_main.xml:14-49`
  - `MainActivity.java:69-90`（建 3 个根视图 + `showTab(0)`）
  - `MainActivity.java:258-274`（罗盘 Tab：底图 WebView + CompassView 覆盖层 + 面板）
  - `MainActivity.java:369-393`（`createMapWebView`，两个实例都 load 同一 asset）
  - `MainActivity.java:567-569`（地图 Tab = 另一个 `createMapWebView()`）
  - `MainActivity.java:630-634`（`showTab` 只切可见性）
  - `CompassView.java:163-169`（圆盘面 82% 不透明）
- **代码原文**：
```xml
    <LinearLayout
        android:id="@+id/bottom_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="@color/panel"
        android:paddingTop="6dp"
        android:paddingBottom="6dp">

        <Button
            android:id="@+id/tab_compass"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/tab_compass"
            android:textColor="@color/text_primary"
            android:background="@android:color/transparent" />
```
  `tab_map`（`activity_main.xml:32-39`）、`tab_knowledge`（`:41-48`）为同构 Button。
```java
        compassMapView = createMapWebView();
        frame.addView(compassMapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Floating compass overlay (罗盘盖在底图上).
        compassView = new CompassView(this);
        compassView.setOnHeadingChangeListener(h -> updateReading());
        int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        frame.addView(compassView, cp);
```
```java
    private WebView buildMapTab() {
        return createMapWebView();
    }
```
```java
    private void showTab(int index) {
        compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }
```
```java
        int bg = Color.argb(210, 24, 20, 16);
        int gold = Color.rgb(220, 190, 130);

        // Face.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        canvas.drawCircle(cx, cy, r, paint);
```
- **触发条件**：启动 App（`showTab(0)`）后点底部三个按钮。
- **预期 vs 实际**：
  - 预期（原版/用户口径）：一个地图页，罗盘是可开关的浮层，叠在地图中心；底部是工具而非页面切换（详见 §6）。
  - 实际：Tab0 与 Tab1 的根视图都是 `map.html` 的两个 WebView 实例；Tab0 额外盖一个 78% 屏宽、TOP 居中、`topMargin=16dp` 的 CompassView（不在屏幕中心，圆盘面 82% 不透明）；Tab1 完全无罗盘；`showTab` 只改 `Visibility`，两个实例都活着。
- **影响**：双份 WebGL 上下文/DEM 缓存/瓦片请求；两个实例的 `zoom/mode/bearing` 各自独立，切换 Tab 视角不一致；底部栏 3 项里 2 项指向同一页面；用户已明确判定「整个底部菜单栏没有存在意义」。
- **修复方向**：见 §6.4（删 Tab、单 WebView、罗盘改居中可开关浮层、工具按原版左右列/底栏归位）。

---

## 2. 必查项衍生的中/低条目（已证实）

### C-08 「贴地」相机没有地形高度/遮挡约束，山地会把眼点压进山体；低缩放时「贴地」实际是数十公里高空

- **严重度**：中　**证据等级**：已证实　**批次**：一（必查②）
- **位置**：`map.html:1208-1233`（相机与眼点）、`map.html:392-403`（贴地开关）、`map.html:209`（3D 强制 z≥9）
- **代码原文**：
```js
  var zMpp = 156543.03392 * Math.cos(center.lat * Math.PI / 180) / Math.pow(2, zoom);
  var hCss = Math.max(1, glCanvas.clientHeight);
  var D = (hCss * zMpp) / (2 * Math.tan(FOV / 2));
  if (groundMode) D *= 0.35;          /* 贴地：机位压低到 35%，配合俯仰 60° */
```
```js
  var eye = {
    x: target.x - fh.x * D * sp,
    y: target.y + D * cp,
    z: target.z - fh.z * D * sp
  };
  var up = (pitch < 0.5) ? { x: Math.sin(b), y: 0, z: -Math.cos(b) } : { x: 0, y: 1, z: 0 };
```
- **触发条件**：3D +「贴地」+ 山区 + 较高 zoom；或目标点在山谷、相机背后有山脊。
- **预期 vs 实际**：
  - 预期：眼点至少高于所在地形，且「贴地」是低机位。
  - 实际：眼点只由 `D`/`pitch` 决定，从不查 `demAt(eye)`；贴地后 `pitch=60°`，`eye.y = target.y + 0.5·D`。按代码常量估算（h_css≈800、fov 50°）：`D = 800·zMpp/(2·tan25°) ≈ 858·zMpp`，贴地再 ×0.35，**眼高 = 0.175·D**。z9：`zMpp≈234 m` → `D≈2.0×10^5 m` → 眼高约 **3.5×10^4 m**；z18：`zMpp≈0.458 m` → `D≈393 m` → 眼高约 **69 m**（复核订正：原文写「约 135 m」是 `0.35·D`，漏乘 `cos60°`）。低缩放不是「贴地」，高缩放时若背后有 >69 m 的山脊则眼点直接进山体（近裁剪面 `near=max(1,D*0.01)`，不会剔除）。
- **修复方向**：眼点高度取 `max(计算值, demAt(eye)+clearance)`；「贴地」按真实米制高度定义而不是 `D×0.35`；进入贴地时限制最小 zoom。

### C-09 纬度 >≈84.9° 时 DEM 瓦片 y<0 全部为空 → 3D 必然失败；`setView`/hash 的 lat 完全不校验

- **严重度**：中　**证据等级**：已证实（数学推导）　**批次**：一（必查②）
- **位置**：`map.html:751-776`（`oy=floor(t.y)-1` 与 `y<0` 分支）、`map.html:994-995`（3D 钳 ±85）、`map.html:1323-1330`（`debugSetView` 不校验 lat）、`map.html:1475-1483`（hash 参数）
- **代码原文**：
```js
  var zdem = Math.max(4, Math.min(12, Math.round(zoom) - 1));
  var t = lonLatToTile(center.lng, center.lat, zdem);
  var ox = Math.floor(t.x) - 1, oy = Math.floor(t.y) - 1;
```
```js
      var x = wrapX(ox + i, n), y = oy + j;
      if (y < 0 || y >= n) { jobs.push(Promise.resolve(null)); continue; }
```
```js
  if (center.lat > 85) center.lat = 85;
  if (center.lat < -85) center.lat = -85;
```
```js
function debugSetView(lat, lng, z, p, b) {
  if (typeof lat === "number") center.lat = lat;
  if (typeof lng === "number") center.lng = lng;
  if (typeof z === "number") zoom = Math.max(2, Math.min(18, z));
```
- **推导**：以 3D 强制 z≥9 后的 `patch.z=8` 计，Web 墨卡托在 lat≈**84.93°** 时 `y<1`，`oy=floor(y)-1=-1`（`patch.z` 更大时阈值略升：z11≈85.04°；`&z<9` 绕过时 `patch.z=4`→82.68°）；3×3 中 `j=0` 的三块 `y=-1` 全部 `Promise.resolve(null)` → `startPatch` 的 `!tiles[i]` 分支必然触发 →「DEM 加载失败」。`pan3D` 钳到 85 仍在这个区间内，等于「允许拖到 85，但 3D 在那里永远失败」。
- **触发条件**：拖到高纬（或 `setView(90,...)` / `#in3d&lat=90`）。
- **预期 vs 实际**：预期极区给出降级/钳制提示；实际纯色空白，且 `setView` 不做 ±85 钳制，与 `pan3D` 不一致；2D 也无纬度钳制。
- **修复方向**：`debugSetView` 与 hash 解析统一钳到 `pan3D` 的 ±85；`oy<0` 时补 `wrapX` 或钳到 0，不要直接判整块失败。

### C-10 跨反经线时 pick 的 lng 可越界（<-180），页面已 emit，Java 桥直接拒收

- **严重度**：中　**证据等级**：已证实（数学推导）　**批次**：一（必查②）
- **位置**：`map.html:1122-1125`、`LCAndroid.java:44-50`
- **代码原文**：
```js
  var n = Math.pow(2, patch.z);
  var tx = gh.gx / 256, ty = gh.gy / 256;
  var lng = tx / n * 360 - 180;
  var lat = tileYToLat(ty, patch.z);
```
```java
            double lng = o.optDouble("lng", Double.NaN);
            double lat = o.optDouble("lat", Double.NaN);
            if (Double.isNaN(lng) || Double.isNaN(lat)
                    || lng < -180.0 || lng > 180.0 || lat < -90.0 || lat > 90.0) {
                Log.w("LCPICK", "reject out-of-range " + json);
                return;
            }
```
- **推导**：视点刚过 +180°（如 lng≈-179.9°）时 `t.x<1` → `ox=-1`，网格左边缘 `gx=-256`（`worldToDem` 的边界判断是 `< ox*256` 才返回 null，`-256` 可通过），`tx=-1` → `lng = -1/n*360-180 = -180 - 360/n`（`n=2^patch.z`；复核订正：`patch.z=4` → -202.5°、`patch.z=11`（zoom 12）→ -180.18°、`patch.z=12`（zoom 13）→ -180.09°），越界。
- **触发条件**：在 ±180° 附近点选地形块西边缘。
- **预期 vs 实际**：预期 lng 归一到 `[-180,180)`；实际页面照常 `emit` 并画标记，Java 记 `reject out-of-range` 丢弃 → 页面与 Java 状态不一致。
- **修复方向**：`lng` 计算后归一化（`((lng+180)%360+360)%360-180`），并在 `assertContract` 里加反经线用例。

### C-11 `VEXAG` 与 pick 是同一变换空间（无缺陷）；但 pick 的高度用逐像素双线性，与 96×96 网格插值不一致

- **严重度**：中　**证据等级**：已证实（数学）　**批次**：一（必查③）
- **位置**：`map.html:830`（网格）、`map.html:1099/1111`（射线高度）、`map.html:1128`（世界坐标）、`map.html:1121`（输出原始高程）、`map.html:1137`（输出）
- **代码原文**：
```js
      var h = H[k] * VEXAG;
```
```js
    var hh = demAt(patch.z, g.gx, g.gy) * VEXAG;
```
```js
    var hm = demAt(patch.z, gm.gx, gm.gy) * VEXAG;
```
```js
    world: { x: hx, y: (elev === null ? hy : elev * VEXAG), z: hz },
```
```js
    elev: (elev === null) ? null : Math.round(elev * 10) / 10,
```
- **结论（回答总监必查③）**：**是同一变换空间**。渲染顶点 `y = H·2.5`、射线求交 `hh=demAt·2.5`、落点世界坐标 `elev·2.5` 三处一致；输出 `elev` 是未乘 2.5 的原始米值（与文档 §5.1「elev 为真实海拔（未乘 2.5）」一致）。`VEXAG` 本身无缺陷。
- **但存在精度偏差（本条的实际问题）**：网格是 96×96 顶点（`step=8` 个 DEM 像素）后由 GPU 做三角面线性插值；`pick3D` 用 `demAt` 做逐像素双线性。两者不是同一高度场：单格 8 px × mpp（z12 约 293 m）内的地形起伏差，就是标记相对可见地表的悬浮/入地量；`pick.elev` 也与渲染表面高度不同。
- **触发条件**：陡峭地形 + 点选位置不在网格顶点。
- **预期 vs 实际**：预期标记贴合渲染表面（三角面插值）；实际用更细的 DEM 双线性值。量级场景相关（平原可忽略，山区可达数米到数十米）。
- **修复方向**：pick 的最终高度改为按网格三角面插值（或在 `buildMesh` 里保存 `H` 供 pick 用同一套双线性），保证与渲染同源。

### C-12 双 WebView：`__LC3D` 互不污染；Java 侧 `mapMarker` 是唯一共享状态且从未被读取

- **严重度**：中　**证据等级**：已证实　**批次**：一（必查④）
- **位置**：`MainActivity.java:40`、`MainActivity.java:377-383`、`LCAndroid.java:29-61`、`MapMarker.java:7-21`
- **代码原文**：
```java
    private MapMarker mapMarker;
```
```java
        webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
            if ("pick".equals(type)) {
                mapMarker = marker;
            } else {
                mapMarker = null;
            }
        }), "LCAndroid");
```
- **结论（回答总监必查④）**：
  - **JS 侧不污染**：两个 WebView 各自独立 JS 上下文，`window.__LC3D`、`DEM`、`patch`、`mode/zoom/bearing` 都是各自实例，`__LC3D` 没有跨实例共享通道。
  - **Java 侧有共享**：`mapMarker` 是 Activity 字段，两个 WebView 的 `LCAndroid` 监听器都写它；回调只带 `type/marker`，不带 WebView 身份，后到的 pick 覆盖先到的，无法区分来源。
  - **且从未被读取**：全文只有 40（声明）、379/381（赋值）三处；没有任何 UI/存储/罗盘联动消费它。`FINAL-REPORT.md:31` 把「`LCAndroid.onEvent` → `MapMarker`」写成已交付链路，但基线里它是死状态。
- **触发条件**：任一 WebView 发生 3D 点选（含 C-02 的注入伪造）。
- **预期 vs 实际**：预期每个实例独立持有/消费标记；实际写入即丢，`mapMarker` 无行为。
- **修复方向**：若要保留「立向点」跨页/跨 Tab 状态，按 WebView 实例区分来源并落到唯一 UI（建议单 WebView 后自然消失）；否则删除字段，避免「看起来接了、其实没接」。

### C-13 每个传感器样本向两个 WebView 各注入 2 条 JS（4 次/样本），含隐藏实例

- **严重度**：中　**证据等级**：已证实　**批次**：一（必查④/性能）
- **位置**：`MainActivity.java:677-687`、`MainActivity.java:749`
- **代码原文**：
```java
        // Push heading + sit/face to BOTH map WebViews: the compass tab's base map
        // (B-17) and the map tab. Injection goes through this same path, so
        // `am broadcast ... heading` must reach the map's bearing (坐南朝北).
        String face = CompassData.MOUNTAINS[r.mountain] + "山";
        String sit = CompassData.MOUNTAINS[r.sitting] + "山";
        for (WebView wv : new WebView[]{compassMapView, mapView}) {
            if (wv == null) continue;
            wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
            wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
```
- **触发条件**：App 在前台，`SENSOR_DELAY_UI` 的样本率（**推算值，未实测**；按标称 ~60 ms 计约 16.7 Hz，实际样本率随设备）每个样本调 `updateReading()`（`MainActivity.java:749`）。
- **预期 vs 实际**：预期只在「读数变化」或可见实例上推送，并合并为一次；实际每个样本 **4 次** `evaluateJavascript`（2 实例 × 2 方法），隐藏实例也执行，字符串每次重建。
- **影响**：主线程跨进程/JS 执行队列持续被占，低端机可感 jank 与耗电；这也是双 WebView 的直接代价。
- **修复方向**：单 WebView 后减半；再加「heading/face/sit 未变化不推」与 16–33 ms 节流，合并成一条 `window.__LC.setReading(h,face,sit)`。

### C-14 2D 每帧 innerHTML 重建约 30 个瓦片 `<img>`；无复用/无缓存；失败静默隐藏

- **严重度**：中　**证据等级**：已证实　**批次**：一（性能/离线）
- **位置**：`map.html:148-176`（`render`/`fillLayer`）、`map.html:436-449`（mousemove 每帧）、`map.html:494-502`（touchmove）
- **代码原文**：
```js
      html += '<img class="tile" style="left:' + left + 'px;top:' + top + 'px;width:256px;height:256px" ' +
        'src="' + tileUrl(layer, zoom, xx, yy) + '" onerror="this.style.display=\'none\'">';
    }
  }
  layerEl.innerHTML = html;
```
```js
  pan(lastX - e.clientX, lastY - e.clientY);
```
- **触发条件**：2D 拖动/缩放；断网或天地图失败。
- **预期 vs 实际**：预期复用瓦片 DOM 或做 LRU；实际每次 `pan` 都 `innerHTML` 全量替换（一个 400×800 视口约 3×5×2 层 = 30 个 `<img>`，全带新 `src`），按 60 fps 拖动**假设推算（未实测）**每秒重建 1800 个节点；`onerror` 直接 `display:none`，断网时只剩黑底 + 一行提示，没有任何「底图加载失败」状态。
- **影响**：拖动抖动/闪烁、重复解码、离线无解释；与 D-006「避免异常高频请求拖垮个人配额」也冲突（缓存命中靠浏览器，不受控）。
- **修复方向**：改瓦片池（按 `z/x/y` 复用节点，只改 `left/top/src`），加内存 LRU；失败给占位图并在 `#srcInfo` 显示「底图不可用」。

### C-15 单实例 3D 资源量级约 40–60 MB（推算）；无 `webglcontextlost` 恢复路径

- **严重度**：中　**证据等级**：已证实（无恢复路径）/ 推算（内存量级）　**批次**：一（资源）
- **位置**：`map.html:601`（`preserveDrawingBuffer:true`）、`map.html:874`（2048² 纹理）、`map.html:531,556-561`（DEM 缓存 90 块）、`map.html:1180-1196`（RAF）；全文无 `webglcontextlost`（grep 0 命中）
- **代码原文**：
```js
    var opts = { alpha: false, antialias: false, depth: true, preserveDrawingBuffer: true };
```
```js
  var TEX_TILES = 6, TEX_PX = 2048;              // POT 画布，才能开 mipmap
```
```js
          while (DEM_ORDER.length > 90) {
            var old = DEM_ORDER.shift();
            if (!(patch && old.indexOf(patch.z + "/") === 0)) delete DEM[old];
          }
```
- **量级推算**：2048² RGBA 纹理 16 MB + mipmap 约 5.3 MB + 90 块 DEM（90×65536×4 B）23.6 MB + 绘制缓冲（dpr 2.5、400×800 CSS ≈ 8 MB）+ 网格 ~0.4 MB ≈ **53 MB/实例**；两个 WebView 同时进过 3D 时约 2 倍。
- **触发条件**：低端机/双 WebView 都进 3D；App 后台被回收 GPU 上下文后返回。
- **预期 vs 实际**：预期监听 `webglcontextlost/restored` 并重建，或至少提示；实际无监听，上下文丢失后所有 `gl.*` 静默失败，画面冻结/黑屏，只有 `console.log` 无错误上报。
- **修复方向**：加 `webglcontextlost`（`preventDefault()`）+ `webglcontextrestored` 重建 program/buffer/texture；`preserveDrawingBuffer` 只在截图时开（或改用 `toDataURL` 前重绘一帧）；DEM 缓存上限按内存预算调。

### C-16【交叉引用，不计条】罗盘覆盖层吃掉触摸且一触即停传感器跟随；「翻坐向」也停跟随，只有「复位」能恢复

> **总监去重裁定（2026-11-05）**：与 B-05 同因，母条归 **B-05**；级别按「同因冲突取高」为 **高**（B-05 高）。LC审核验 单点复核（`90-verify.md` §11.2）已定：**C-16 定性成立，B-05 的定性句需修正**；本条保留叠加层触摸与 `followSensor` 的代码原文，供母条引用。

- **严重度**：—（母条 B-05 为高）　**证据等级**：已证实（供 B-05 引用）　**批次**：交叉引用
- **位置**：`CompassView.java:99-104`（`followSensor` 才更新）、`CompassView.java:127-135`（DOWN 即 `followSensor=false`）、`CompassView.java:113-118`（`setHeadingManually` 同样置 false）、`MainActivity.java:314-317`（翻坐向按钮）、`MainActivity.java:270-274`（覆盖层尺寸）
- **代码原文**：
```java
    public void setSensorHeading(float azimuth) {
        if (followSensor) {
            heading = azimuth;
            invalidate();
        }
    }
```
```java
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                followSensor = false;
                lastAngle = (float) Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
                return true;
```
```java
        Button flip = new Button(this);
        flip.setText(R.string.sitting_flip);
        flip.setOnClickListener(v ->
                compassView.setHeadingManually(normalize(compassView.getHeading() + 180f)));
```
- **触发条件**：在罗盘覆盖层（78% 屏宽）上按下/拖动，或点「翻坐向」。
- **预期 vs 实际**：预期只有显式「锁定」才停跟随，且界面有状态提示；实际任意 DOWN 或翻坐向都置 `followSensor=false`，之后传感器样本被 `setSensorHeading` 丢弃，但 `onSensorChanged` 仍每帧调 `updateReading()` 把**冻结的 heading** 推给地图（`MainActivity.java:748-749,684`）→ 盘面与地图一起停住，只有「复位」恢复。
- **说明（核验单点已定，90-verify §11.2）**：C-16 定性成立。`map.html:341-346` 的坐向翻转是每次 `setHeading` 按 `lastHeading` 重算 bearing，**本身持续跟随、不冻结**；冻结发生在 `CompassView.setHeadingManually`（`CompassView.java:113-118`）把 `followSensor=false`，之后 `onSensorChanged` 仍把冻结的 `r.heading` 推给地图（`MainActivity.java:748-749,684`），所以盘面与地图一起停在旧读数。原版 E6 清单里「罗盘锁定」是独立控件、「坐向朝北」是另一个双标签开关，**没有「转 180°」按钮**（`docs/gap-matrix.md:121,132`）；把冻结职责挂到「翻坐向」没有原版依据。原版 `g.x()Z` 的极性仍未取证，不能据此说已 1:1。主责 M2，本条登记地图侧后果。
- **修复方向**：① `ACTION_DOWN` 只进入拖动，不立即停跟随（拖动超过阈值再停）；②「翻坐向」在 `CompassView` 内落成**持久偏移量**（如 `headingOffset`，`setSensorHeading(az)` 时 `heading = normalize(az + headingOffset)`），与 `map.html:342-345` 的 `offDeg` 同构；**不要**用一次性 `setSensorHeading(heading+180)`（下一帧就被传感器覆盖），也不要改 `followSensor`；③ 显式锁定走独立控件（原版 E6 的「罗盘锁定」），并给「已锁定」提示。

### C-17 `map-bridge.md` 4 处实现细节漂移

- **严重度**：低　**证据等级**：已证实　**批次**：一（必查⑥）
- **位置**：`docs/map-bridge.md:72`、`:204`、`:216`、`:195`
- **代码/原文**：
  - `:72` `| clearMarkers() | — | 清除 2D 立向点 |` —— 实现 `clearMarkers()` 同时清 3D pick 并发 `pick-clear`（`map.html:467-473`）。
  - `:204` `跨块瞬间可能有 <1 s 空窗（旧块继续渲染直到新块就绪，无白屏）` —— 首块失败是纯色空白，换块失败是旧块错位（C-05）。
  - `:216` `cd E:\APPDetector\output\app\android; gradle assembleDebug --no-daemon` —— 证据命令指向开发脏树 `output`，不是审查基线 `review-base`，别人按此无法复现基线。
  - `:195` `（当前 mapView 是地图 Tab 的实例，罗盘 Tab 里另有一个 createMapWebView() 局部实例）` —— 基线里 `compassMapView` 是字段（`MainActivity.java:31`），不是局部实例；且桥已接，截图链路也已在 `captureMap()` 重写。
- **影响**：低（文档可维护性/复现性）；但 `:216` 的脏树路径会让任何复核者拿到错版本。
- **修复方向**：随 C-06 一并更新文档；证据命令改为 `E:\APPDetector\review-base`（或参数化）。

---

## 3. 高度可疑（单列，不进正式清单）

### C-18 `#extension GL_OES_standard_derivatives` 在 `getExtension` 之前编译

- **严重度**：中　**证据等级**：高度可疑　**批次**：一（必查 / 3D 可用性）
- **位置**：`map.html:624-625`、`map.html:663-669`、`map.html:690-702`、`map.html:731`、`map.html:216-219`
- **代码原文**：
```js
    "#extension GL_OES_standard_derivatives : enable",
```
```js
    "#ifdef GL_OES_standard_derivatives",
```
```js
    !!(gl.getExtension("OES_standard_derivatives")));
```
```js
    if (!initGL()) {
      console.log("LC3D ERROR webgl-unavailable");
      setHud("WebGL 不可用，已退回 2D");
```
- **已确认的代码事实**：`#extension` 指令在 shader 源码第 1 行，program 在 `690-697` 编译/链接；`getExtension("OES_standard_derivatives")` 直到 `731`（link 成功之后）才调用，且只用于日志。若目标 WebView 要求「先 `getExtension` 才允许该 directive」，编译会在 `684-687` 抛错 → `698-701` 把 `gl=null` → `initGL` false → `216-219` 报「WebGL 不可用」并退回 2D。
- **未确认**：实际设备是否容忍该顺序。现有「真机 FPS 56–60」只出现在文档（`PRD-v0.1.md:92`），基线内没有可复现脚本证明该顺序在目标 WebView 上通过。
- **缺什么证据**：目标机 WebView 版本的 logcat 中 `LC3D webgl ok ...` / `LC3D ERROR shader ...` 行；换一台低版本 WebView 复跑 `tools/map/run_at_p04.ps1`。
- **附带的已证实小问题**：shader/link 失败与「WebGL 不可用」共用同一提示（`216-219`），诊断信息错误；`#ifdef` 的 `w100/w500` 常量回退分支在 directive 失败时不可达（编译先失败）。
- **修复方向**：在 `initGL()` 一开始调 `gl.getExtension("OES_standard_derivatives")`（并保存结果决定是否加 directive），失败路径区分 `shader` 与 `webgl`。

### C-19 Tab 隐藏/App 后台时是否停 3D 渲染只靠 IntersectionObserver/visibilitychange，`onPause` 未 pause WebView

- **严重度**：低　**证据等级**：高度可疑　**批次**：一（必查④/资源）
- **位置**：`map.html:527`（`visible3D=true` 初值）、`map.html:1311-1320`、`MainActivity.java:726-732`（`onPause`）
- **代码原文**：
```js
var visible3D = true;
```
```js
if (window.IntersectionObserver) {
  new IntersectionObserver(function (es) {
    visible3D = !!(es[0] && es[0].isIntersecting);
  }, { threshold: 0.01 }).observe(glCanvas);
}
document.addEventListener("visibilitychange", function () {
  if (document.visibilityState === "hidden") visible3D = false;
  else visible3D = true;
});
```
```java
    protected void onPause() {
        unregisterDebugBridge();
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
```
- **已确认**：Java 侧只切 `View.GONE`（`showTab`），没有 `webView.onPause()/pauseTimers()`，也没有对页面调用「暂停」；页面唯一停止渲染的开关是 `visible3D`。
- **未确认**：GONE 的 WebView 是否仍派发 IntersectionObserver 回调；App 进入后台时 WebView 的 `document.visibilityState` 是否变 `hidden`。若不派发，隐藏实例会持续 `requestAnimationFrame`（`loop` 每帧执行 `render3D`），双实例同时抢 GPU。
- **缺什么证据**：真机在罗盘 Tab 进 3D 后切到地图 Tab，抓 10 s logcat，看是否仍有隐藏实例的 `LC3D fps=`；`dumpsys gfxinfo com.luopan.fengshui` 的帧活动。
- **修复方向**：`onPause/onResume` 调 `webView.onPause()/onResume()`；页面加 `window.__LC3D.pause()/resume()` 由 Java 显式调用，不完全依赖 IO。

---

## 4. 待确认（线索不足）

### C-20 PRD 记录真机「点定位无可见变化」，基线代码未定位原因

- **严重度**：中　**证据等级**：待确认　**批次**：一
- **位置**：`map.html:475-485`（`locate`）、`MainActivity.java:97-102`（权限申请）、`MainActivity.java:385-390`（geolocation 自动授权）、`docs/product/PRD-v0.1.md:96`
- **代码原文**：
```js
function locate() {
  if (!navigator.geolocation) { alert("此设备不支持定位"); return; }
  navigator.geolocation.getCurrentPosition(function (pos) {
    center = { lat: pos.coords.latitude, lng: pos.coords.longitude };
    zoom = Math.max(zoom, 12);
    if (mode === "3d") ensurePatch(true); else render();
    updateHud();
  }, function (err) {
    alert("定位失败: " + err.message);
  }, { enableHighAccuracy: true, timeout: 10000 });
}
```
  `PRD-v0.1.md:96`：`等价替代。**真机实测**：点击定位后地图无可见变化，**未确认**（geolocation 自动授权，无独立证据）`
- **代码事实**：成功回调里会改 `center` 并 `render()`，代码路径本身成立；`onGeolocationPermissionsShowPrompt` 也已授权。
- **缺什么证据**：真机点击定位后的 logcat（是否有 `定位失败` alert、`getCurrentPosition` 是否回调、WebView 的 geolocation 是否被系统定位开关拦截）。在拿到之前不能判定是页面 bug、权限 bug 还是设备定位不可用。
- **结论**：不写进正式缺陷清单，挂「待确认」。

### C-21 原版罗盘叠层的透明度/尺寸/是否居中无 review-base 内对照证据

- **严重度**：低　**证据等级**：待确认　**批次**：一
- **位置**：`CompassView.java:163-169`（本实现 82% 不透明、78% 屏宽、TOP 居中）；对照依据只有 `app/MECHANICS.md:120-126`（原版为 Compose `GraphicsLayer` 叠层）与 `docs/gap-matrix.md:121`（原版控件清单），**没有**原版叠层的尺寸/透明度/锚点数据。
- **缺什么证据**：原版 APK 首页截图与 View 层级 dump（在 `.inspect`，不在 review-base；本报告未读）。拿到后才能判定「居中/尺寸/透明度」的 1:1 值。
- **说明**：C-07 的「位置不在中心、三 Tab 无依据」不依赖此条，已由基线代码与档案证据成立；此条只影响修复后的像素级参数。

---

## 5. 六项必查对照

| 必查项 | 结论 | 条目 |
|---|---|---|
| ① DEM 瓦片加载失败的降级路径 | **不成立**：任一瓦片失败即整块拒绝；3D 下 2D 层已隐藏，只剩清屏色；只有 WebGL 初始化失败才自动回 2D；旧块失败会滞留错位。**贴面（天地图）失败降级成立**：`ok<30` → 不建纹理 → `useTex=0` 走高程配色（`map.html:899-903,1274`）。 | C-05（C-27 第二批） |
| ② 相机/投影/pick 边界（贴地平视、极区、地形空洞） | 贴地平视的 `up` 特判正确（`pitch<0.5` 用前向，避免万向锁，`map.html:1233`）；**贴地机位无地形约束**；**极区必然 DEM 失败且 lat 不钳制**；**反经线 lng 越界**；地形「空洞」在本实现表现为块边缘假 0 面（C-03）。 | C-08/C-09/C-10/C-03 |
| ③ `VEXAG` 与 pick 是否同一变换空间 | **是**：网格顶点 `H*2.5`、射线 `demAt*2.5`、落点 `elev*2.5` 三处一致；输出 `elev` 为原始值。无缺陷。另有「pick 用逐像素双线性、渲染用 96×96 网格插值」的精度偏差。 | C-11 |
| ④ 双 WebView 下 `__LC3D` 是否互相污染 | **不污染**：独立 JS 上下文，`window`/`__LC3D`/`DEM` 各自独立。**但**：Java 侧 `mapMarker` 共享且无人消费；每传感器样本 4 次注入（含隐藏实例）；隐藏实例是否停渲染未证实。 | C-12/C-13/C-19 |
| ⑤ 天地图 APIKEY 明文外泄面 | **三个面叠加**：① APK/仓库明文（含 git 历史，自基线 `4770c20`）；② 用的是原版 key，非 D-006 要求的个人 key；③ 同机任意 App 可经导出广播注入 JS 读走 key（母条 B-02，交叉引用）。实测该 key 未被包名限制（命令与结果见 C-01，带 Origin 请求返回 200 / image-jpg）。 | C-01（注入面引 B-02） |
| ⑥ `docs/map-bridge.md` 与实现一致性 | **契约破裂**：`ready/mode/error/fps` 不存在；§5/§7 称桥未接（实际已接）；另有 4 处细节漂移。 | C-06/C-17 |

---

## 6. 已知缺陷专项：底部菜单与罗盘叠加层

> 用户口径（2026-11-05）：底部菜单里「罗盘」与「地图」应是同一个页面的不同表现；参照原 APK 首页布局，罗盘应是点击「罗盘」按钮后浮动叠加在地图中心，配合地图模拟真实罗盘场景；整个底部菜单栏没有存在意义。要遮挡页面的组件与子页，应依原 APK 的设计逻辑放在合适位置、用合适的呈现方式。

> **计条归属（总监 2026-11-05）**：K-01 母条 = **B-03 + B-04**；本节与 C-07 为跨模块交叉引用（级别按母条为高），**不计入 M3 条目数**；本节保留地图侧代码证据与修复方向，供母条与成文引用。

### 6.1 现状（三 Tab 的代码证据）

1. **底部三 Tab 是布局里写死的三个 Button**：`activity_main.xml:14-49`（`tab_compass` / `tab_map` / `tab_knowledge`）。
2. **三个 Tab 对应三个根视图，启动时全部创建**：`MainActivity.java:79-85`（`buildCompassTab()` / `buildMapTab()` / `buildKnowledgeTab()`，`content.addView` ×3），`87-90` 三个点击监听 + `showTab(0)`。
3. **「罗盘」页 = 底图 WebView + 罗盘覆盖层 + 底部面板**：`MainActivity.java:263-274`（`compassMapView` 全屏 + `CompassView` 78% 宽、`Gravity.TOP|CENTER_HORIZONTAL`、`topMargin=16dp`），`277-363` 底部面板（盘面/读盘/复位/翻坐向/截图/真北/偏角/状态）。
4. **「地图」页 = 同一个 map.html 的第二个 WebView**：`MainActivity.java:567-569` `buildMapTab()` 直接 `return createMapWebView();`；`369-393` 的 `createMapWebView()` 统一 `loadUrl("file:///android_asset/map.html")`。两个实例都注册了 `LCAndroid` 桥。
5. **Tab 切换只改可见性**：`MainActivity.java:630-634`（`setVisibility(VISIBLE/GONE)`），没有任何页面暂停/状态同步。
6. **罗盘是常驻子视图，不是开关**：没有 `setVisibility(GONE)` 的代码路径；`CompassView` 画的是 `Color.argb(210,...)` 的实心圆盘（`CompassView.java:163-169`），82% 不透明，压在底图上。
7. **底部面板与 map.html 自带控件并存**：`map.html:49-69` 有 **18 个 `<button>` + 1 个 `#pitchRange` 滑块**（复核订正：原文写「20 个按钮」；`<button` 全文 21 处，另 3 处在 `#fsExit`/`#srcPanel`，不在 `#controls`），Android 底部面板又有一套（盘面/读盘/复位/翻/截图/真北/偏角/状态）→ 同一屏两套控制面。

### 6.2 与原版的差距（档案依据）

| 原版事实 | 出处 | LC 现状 | 差距 |
|---|---|---|---|
| 地图是 WebView（`MapWebController` + `MapWebInterface` 桥），罗盘是 Compose `GraphicsLayer` **叠在**地图上；截图走 `getScreenshot(mapWebController, …, "compass", …)` 抓「地图+罗盘」 | `app/MECHANICS.md:120-126` | 罗盘叠层**存在**（`CompassView` 在 `compassMapView` 之上），但只属于罗盘 Tab；地图 Tab 是另一份没有叠层的 map.html | 架构方向对，落点错了：叠层应属于唯一的地图页，而不是某个 Tab |
| 罗盘页控件：左列 `截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地`；右列 `地图/实景/坐向朝北/指针开关`；底栏 `切换/旋转/缩放/透明度/天支线` | `docs/gap-matrix.md:121`（E6） | 左/右列没有；这些按钮被塞进 `map.html#controls` 的左侧堆叠；底栏是三个页面 Tab；另加 Android 底部面板 | 控件集合与位置都不对；底栏语义从「工具」变成「页面切换」 |
| 原版桥方法：`setSitFace/setHeading/onMapRotate/onMapScroll/showFullScreen/setDrawBtnState/deleteMapElementByType/openStreetView` | `docs/gap-matrix.md:131`（F4） | 只调 `setHeading`/`setSitFace`（`MainActivity.java:684-685`） | 等价替代已登记；但与布局缺陷无关，不影响本条定级 |
| 「底部三 Tab：罗盘/地图/知识库」被 PRD 登记为「等价替代」，**依据栏写的是「原版主页结构（未逐页比对）」** | `docs/product/PRD-v0.1.md:100`（B-19） | 三个 Tab 实际是「同一页 ×2 + 原版没有的列表页」 | 自认未比对就判「等价替代」，不能作为 1:1 依据；违反章程 §2 目标 1 与 §4.5「不臆造」 |
| 知识库入口是「点圈层文字 → 对应 HTML 引入页」（`CompassIntroduceActivity`） | `docs/gap-matrix.md:97`（C2） | 独立第三 Tab（`buildKnowledgeTab()` 的 ListView，`MainActivity.java:571-605`） | 呈现方式与入口都不对 |

> 说明：原版 APK 截图/层级 dump 在 `E:\APPDetector\output\.inspect\`（review-base 之外，按纪律未读）。上表只引用 review-base 内的档案；这些档案本身由逆向基于原版 APK 产出，但本报告未在 APK 上二次核验（见 §9.4）。

### 6.3 缺陷定级

- **定级：高**（母条 B-03 + B-04；C-07 为交叉引用，不计入 M3；不是阻断：不影响读数数值、不崩溃、不损坏数据）。
- 理由：① 违反章程 §2 目标 1「菜单/页面/交互 1:1」且无「有意差异」登记；② 双 WebView 是实打实的资源翻倍与状态分裂；③ 用户已直接判定三 Tab 无存在意义；④ 罗盘覆盖层位置（TOP、78% 宽、82% 不透明）与「浮在地图中心、配合地图」相反。

### 6.4 修复方向（按原版设计逻辑，不是「优化建议」）

1. **删掉底部三 Tab**：移除 `activity_main.xml:14-49` 的 `bottom_bar` 与三个 Button；移除 `MainActivity` 的 `compassTab/mapView/knowledgeTab` 三根视图与 `showTab()`（`69-90`、`630-634`）。
2. **只保留一个地图 WebView**：`compassMapView` 与 `mapView` 合并为一个实例；删除 `buildMapTab()`；`updateReading()` 只推这一个实例（`682-686` 的 for 循环去掉）。
3. **罗盘改成「可开关的居中浮层」**：`CompassView` 保留一个实例，`Gravity.CENTER`，尺寸取 `min(屏宽,屏高)*0.78`（或原版实测值）；默认显示/隐藏按原版；用原版右列的「地图」按钮或用户要求的「罗盘」按钮切换 `setVisibility(VISIBLE/GONE)`；圆盘是否半透明按 C-21 取证后再定。
4. **工具归位**：按 E6 清单把 `截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地` 放左列，`地图/实景/坐向朝北/指针开关` 放右列，`切换/旋转/缩放/透明度/天支线` 放底栏；`map.html#controls` 与原生按钮二选一，禁止双控制面。
5. **知识库回到入口语义**：点圈层文字/读数行打开对应介绍页（C2），不占 Tab；`buildKnowledgeTab()` 可保留为设置/帮助子页，但不做一级导航。
6. **验收证据**：首页 View 层级 dump 只能有 1 个 `WebView`；切换「罗盘」只改 `CompassView` 可见性；切前后 `__LC3D.state()` 的 `zoom/mode/bearing` 不变（同一实例）；截图 = 地图 + 罗盘合成（原版 `getScreenshot(...,"compass",...)` 语义）。

---

## 7. 第二批（中/低，本批交付）

> 13 条全部「已证实」，均已回源码现场复核行号与片段。中 8 条（C-22…C-27、C-29、C-34），低 5 条（C-28、C-30…C-33）。
> **第三批（本轮复核新增）3 条：C-35（低）、C-36（中）、C-37（中）**，见 §7.1；合计口径变为「34 计条 + 3 交叉引用」。

### C-22 2D 立向点按屏幕像素定位，平移/缩放后不跟地图走

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:455-465`（落点）、`map.html:148-176`（render）、`map.html:424-434`（pan）
- **代码原文**：
```js
function placeMarker(x, y) {
  if (mode === "3d") { pick3D(x, y); return; }
  var rect = mapEl.getBoundingClientRect();
  var mk = document.createElement("div");
  mk.className = "marker";
  mk.style.left = (x - rect.left) + "px";
  mk.style.top = (y - rect.top) + "px";
```
- **触发条件**：2D 点图落一个立向点，然后拖动或缩放。
- **预期 vs 实际**：预期标记锚定经纬度（或瓦片坐标），随地图一起移动；实际 `left/top` 是屏幕像素，`render()/pan()` 只重建 `.tile`，从不重算 `.marker` → 标记钉在屏幕上，地图在它下面滑走。
- **影响**：「点图打出立向标记」在 2D 下没有地理意义；3D 的 pick 标记反而逐帧重投影（`map.html:1151-1160`），两种模式行为不一致。
- **修复方向**：落点时存 `{lng,lat}`（或 `{z,x,y}`），`render()` 末尾重投影所有 `.marker`。

### C-23 2D 旋转/坐向后拖拽未按 bearing 分解

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:237-240`（CSS 旋转）、`map.html:424-433`（pan）、对照 `map.html:986-993`（3D 分解）
- **代码原文**：
```js
function applyTransform() {
  if (mode === "3d") { mapEl.style.transform = ""; return; }
  mapEl.style.transform = (bearing === 0) ? "" : "rotateZ(" + bearing + "deg)";
}
```
```js
  c.x -= dxPx / 256;
  c.y -= dyPx / 256;
```
- **触发条件**：2D 下点「旋转」按钮或进入坐向模式（`bearing≠0`）后拖动。
- **预期 vs 实际**：预期屏幕位移先按 `-bearing` 旋转再叠加到经纬度；实际固定按屏幕东西/南北改 `c.x/c.y`。地图被 `rotateZ` 旋转后，拖动方向随 bearing 偏离，`bearing=90°` 时上下拖变成东西移。
- **修复方向**：复用 3D 的 `rx/rz` 分解（`map.html:986-993`），2D/3D 共用 `pan` 的 bearing 变换。

### C-24 2D 指针角度未减 bearing（3D 减了）

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:405-411`、`map.html:237-240`、`map.html:344-346`
- **代码原文**：
```js
function refreshNeedle() {
  var el = document.getElementById("needle");
  if (!el) return;
  /* 3D 下地图随 bearing 旋转，指针取 heading-bearing 才与屏幕北对齐 */
  var deg = (mode === "3d") ? (lastHeading - bearing) : lastHeading;
  el.style.transform = "rotate(" + deg + "deg)";
}
```
- **触发条件**：2D 下 `rotateBearing()` 或坐向模式（地图 `rotateZ(bearing)`）。
- **预期 vs 实际**：预期指针始终指向**地图北**（屏幕角度 `heading-bearing`）；3D 这样做了，2D 仍用 `lastHeading`，地图已转 `bearing`，指针就偏了 `bearing`。
- **修复方向**：去掉模式分支，统一 `deg = lastHeading - bearing`（2D/3D 同一公式）。

### C-25 「全屏」只隐藏 WebView 内控件，原生罗盘/面板/底栏仍在

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:268-283`；`MainActivity.java:258-363`（原生覆盖层/面板）
- **代码原文**：
```js
function toggleFullScreen() {
  fullScreen = !fullScreen;
  document.getElementById("controls").style.display = fullScreen ? "none" : "";
  document.getElementById("hint").style.display = fullScreen ? "none" : "";
  document.getElementById("srcInfo").style.display = fullScreen ? "none" : "";
  document.getElementById("fsExit").style.display = fullScreen ? "block" : "none";
```
- **触发条件**：罗盘页点「全屏」（`map.html:66`）。
- **预期 vs 实际**：预期全屏浏览地图；实际只隐藏 `#controls/#hint/#srcInfo`，`CompassView`（0.78 屏宽）、底部面板、底部三 Tab 都是原生 View，不参与 → 全屏后仍被罗盘和面板遮住大半。
- **修复方向**：全屏时 Java 侧同步隐藏 `compassView/bottomPanel/bottom_bar`（或在单页模型里由原生按钮统一管全屏，不放在 map.html）。

### C-26 3D 默认贴面是影像，角注/截图角注仍显示「地形」

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:211`、`map.html:331-337`、`map.html:1307`；`MainActivity.java:409-411`
- **代码原文**：
```js
    drape = (base === "ter") ? "img" : base;
```
```js
function updateSrcInfo() {
  var i = sourceOfBase(base);
  if (i >= 0) srcIndex = i;
  var s = MAP_SOURCES[srcIndex] || { name: "天地图地形", review: "未确认" };
  var el = document.getElementById("srcInfo");
  if (el) el.textContent = "底图：" + s.name + " · 审图号：" + (s.review || "未确认");
}
```
- **触发条件**：2D 默认 `base=ter` 时进 3D（`drape` 自动切成 `img`）。
- **预期 vs 实际**：预期角注与画面一致（影像）；实际 HUD 显示 `贴面 影像`（`map.html:1307`），右下角 `#srcInfo` 仍按 `base=ter` 写「底图：天地图地形」；截图角注读 `#srcInfo`（`MainActivity.java:409-411`）→ 存下来的图写「地形」但画面是影像。
- **影响**：合规呈现不一致；等审图号按源回填后会变成「错误的审图号」。
- **修复方向**：3D 下 `updateSrcInfo()` 用 `drape`；或角注写成「地形（3D 贴面：影像）」。

### C-27 AT 断言弱于文档 + 真机脚本只数 ≥5 条 pick + 环境失败被判 FAIL

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:1516-1517`、`map.html:901`；`docs/qa/AT-P0-4-3d-pick.md:21-22,45`；`tools/map/run_td015_device.ps1:39,50`；`tools/map/run_at_p04.ps1:31`
- **代码原文**：
```js
  ok("TD-002", !!(patch && patch.mesh && patch.indexCount > 0), "patch");
  ok("TD-003", !!(patch && patch.tex), "texture");
```
```js
    if (ok >= 30) { p.texCanvas = cv; console.log("LC3D texture ok " + ok + "/36 " + drape + " z" + zt); }
```
```powershell
$picks = ($txt | Select-String -Pattern 'LC3D pick ').Count
```
  `tools/map/run_td015_device.ps1:50`：
```powershell
if ($picks -ge 5 -and $fpsMin -ge 30 -and $fatal -eq 0) { Write-Host "TD015: PASS"; exit 0 }
```
- **触发条件**：贴面只成功 30–35 块、一次点击合成双事件、断网导致 TD-002/003 红。
- **预期 vs 实际**：文档说 TD-002 查「9409 顶点 / 55296 索引」、TD-003 查「36/36」，代码只查非空/`indexCount>0`，且 `ok>=30` 就建纹理；真机脚本只数总条数 ≥5，不校验 5 个不同落点（触摸+合成鼠标双发也能过）；`AT-P0-4-3d-pick.md:45` 说断网「属环境失败不是回归」，脚本却以 `exit 1` 报 FAIL（`run_at_p04.ps1:31` 只对「无 LCAT-RESULT」给 `exit 2`）。
- **修复方向**：TD-002 断 `indexCount===55296 && verts===9409`；TD-003 断 `36/36`（或明确 30/36 的降级不算 PASS）；真机脚本按坐标去重并断言恰好 5 条；DEM/贴面不可达时输出 `BLOCKED` + `exit 2`。

### C-28 pick 射线离开地形块后不提前退出，单次点选固定跑满 ~1140 步

- **严重度**：低　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:1090-1102`
- **代码原文**：
```js
  var maxT = lastD * 4;
  var step = Math.max(15, patch.mpp * 1.5);
  var above = -1, below = -1, t;
  for (t = 0; t <= maxT; t += step) {
    var px = ray.o.x + ray.d.x * t;
    var py = ray.o.y + ray.d.y * t;
    var pz = ray.o.z + ray.d.z * t;
    var g = worldToDem(px, pz);
    if (!g) continue;
```
- **触发条件**：在 3D 里点天空/块外（未命中），或每次正常点选。
- **预期 vs 实际**：预期射线离开地形块（`worldToDem` 返回 null）立即 break；实际 `continue` 跑满 `maxT=4D`。按 z9/z12 的 `mpp` 推算，单次约 1140 次步进 ×4 次 `demPix`，即使必不命中也要跑完。
- **修复方向**：`if (!g) { if (above >= 0) break; else continue; }` 或直接 `break`（射线一旦离开块就不会再回来，除非跨反经线，需单独处理）。

### C-29 页面错误不可观测：`emit` 静默吞异常、无 `window.onerror`、未覆写 `onConsoleMessage`

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:1004-1010`（emit）、全文无 `window.onerror`（grep 0）；`MainActivity.java:385-390`（WebChromeClient 只实现 geolocation）
- **代码原文**：
```js
function emit(obj) {
  try {
    if (window.LCAndroid && typeof window.LCAndroid.onEvent === "function") {
      window.LCAndroid.onEvent(JSON.stringify(obj));
    }
  } catch (e) { /* 桥未接入时静默 */ }
}
```
- **触发条件**：桥抛异常、页面 JS 运行时异常、shader/DEM/贴面失败。
- **预期 vs 实际**：预期 Java 侧能收到 `error`（文档 §5 也承诺了）、或至少 logcat 有结构化行；实际 `emit` 的 catch 静默；无 `window.onerror`；`WebChromeClient` 未覆写 `onConsoleMessage` → 页面的 `LC3D ERROR ...` 只进 WebView 的 logcat，release 包无任何 App 侧观测；AT 脚本又依赖 logcat 正则。
- **修复方向**：`emit` catch 里 `console.log`/`emit({type:"error"})`；加 `window.onerror`；`onConsoleMessage` 转发到 `LCJS` tag（probe AT 也是这么要求的，`AT-P0-4-3d-probe.md:17`）。

### C-30 `#in3d&sit` 入口硬编码 `setHeading(90)`；`&z=<9` 可绕过「3D 最小 z9」

- **严重度**：低　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:1486-1493`、`map.html:1477-1483`、`map.html:209`
- **代码原文**（第一段 `map.html:1486-1490`；第二段 `map.html:1480-1483`）：
```js
    if (hashHas(h, "sit")) {
      var msit = h.match(/[&#]sit=(\d)/);
      var want = msit ? parseInt(msit[1], 10) : 1;   // sit=1 坐南朝北（默认），sit=0 坐北朝南
      setHeading(90);
      if (sitMode === 0) bearingBeforeSit = bearing;
```
```js
    if (mlat || mlng || mz) {
      debugSetView(mlat ? parseFloat(mlat[1]) : null, mlng ? parseFloat(mlng[1]) : null,
        mz ? parseInt(mz[1], 10) : null, null, null);
    }
```
- **触发条件**：客户端按文档 `loadUrl("...#in3d&sit")`；或 `#in3d&z=5`。
- **预期 vs 实际**：预期 `sit` 只改 bearing 对齐、不改 heading；实际先 `setHeading(90)` 把航向伪造成正东，直到下一次传感器推送才纠正（无传感器/注入冻结时一直是 90°）；`&z=5` 经 `debugSetView` 把 zoom 设成 5，绕过 `setMode` 的 `zoom>=9`（`map.html:209`），3D 降到 z4 DEM。
- **修复方向**：去掉硬编码 90，改用参数 `&heading=` 或保留客户端推入值；`debugSetView` 在 3D 下把 z clamp 到 ≥9。

### C-31 进 3D 强制 zoom≥9，返回 2D 不恢复原 zoom

- **严重度**：低　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:207-210`（进 3D 强制 z9）、`map.html:226-234`（回 2D 分支，无恢复）
- **代码原文**：
```js
  if (mode === "3d") {
    /* 大陆尺度下真高程起伏相对视距过小，进入 3D 时至少落到 z9（见 docs/map-bridge.md） */
    if (zoom < 9) zoom = 9;
```
- **触发条件**：2D 在 z5 点「3D 地形」，再点「返回 2D」。
- **预期 vs 实际**：预期返回 2D 恢复进入前的 zoom；实际 `zoom` 被永久改成 9（无 `pre3DZoom`），用户看一次 3D 后 2D 的缩放级别被改。
- **修复方向**：进 3D 时保存 `zoomBefore3D`，回 2D 恢复（或明确产品口径：3D 后保持 z9，写进文档）。

### C-32 贴面构建 `ok<30` 即放弃且不重试

- **严重度**：低　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:899-903`、`map.html:1260`
- **代码原文**：
```js
  Promise.all(tasks).then(function () {
    if (seq !== patchSeq || patch !== p) return;
    if (ok >= 30) { p.texCanvas = cv; console.log("LC3D texture ok " + ok + "/36 " + drape + " z" + zt); }
    else { console.log("LC3D texture fail " + ok + "/36 " + drape + " z" + zt); }
  });
```
- **触发条件**：贴面 36 块中成功 <30（瞬时断网、天地图限流）。
- **预期 vs 实际**：预期失败后重试或给用户提示；实际 `texCanvas` 永远保持 null，`render3D` 一直走 `useTex=0` 的高程配色（`map.html:1274`），只有 console 一行，直到换块或用户切底图触发 `refreshPatchTexture`。地形结构仍在（降级本身正确），但「一次抖动就永久无贴面」不符合瞬态失败的恢复预期。
- **修复方向**：失败后 2–3 s 重试一次；HUD 显示「贴面不可用，已用高程配色」。

### C-33 `map.html` 单文件 1605 行、20+ 全局可变状态、无模块边界；测试入口混在生产代码

- **严重度**：低　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`map.html:91-114`（全局状态集中声明）、`map.html:1322-1560`（调试/AT 入口）
- **代码原文**：
```js
var KEY = "dd18858b33229f5837ad1d97b8d3105e";
var SUB = ["t0","t1","t2","t3","t4","t5","t6","t7"];
var base = "ter";              // vec=矢量 img=影像 ter=地形(默认)
var anno = "cta";              // 注记层
var mode = "2d";               // 2d | 3d
var bearing = 0;               // 地图旋转角
var pitch = 45;                // 3D 俯仰 0(俯视)–60°
```
- **触发条件**：任何后续地图改动/排查。
- **预期 vs 实际**：预期按职责拆成瓦片/3D/相机/pick/桥/AT 块，常量集中；实际单文件混 2D 瓦片、WebGL、DEM 缓存、相机、pick、hash 入口、桥自测、AT runner；`base/anno/mode/bearing/pitch/pick/lastVP/...` 20+ 全局可变；`#at`/`#selftest`/`__LCBridgeTest` 生产代码常驻（虽不影响用户，但可被任意注入调用）。
- **修复方向**：按块拆文件或至少分段封装 IIFE；常量表集中；AT/调试入口用 `location.hash` 之外的方式隔离（如仅 debug 包注入）。

### C-34 第二份 AT（probe）要求的 `__LC3D.probe/pick/state().vexag` 不存在；两份 AT 编号体系冲突

- **严重度**：中　**证据等级**：已证实　**批次**：二（本批）
- **位置**：`docs/qa/AT-P0-4-3d-probe.md:5,16,20,78`；`map.html:1334-1344`（`getState`）、`map.html:1354-1362`（`window.__LC3D`）
- **代码原文**：
  `AT-P0-4-3d-probe.md:16`：
```md
| M1 | `window.__LC3D` 增：`probe(lat,lng)` → `{elev, slope, aspect, demZoom}`（纯 DEM 采样，不依赖渲染；**必须回报 demZoom**，否则数值不可复现）；`pick(px,py)` → `{lat,lng,elev,slope,aspect,demZoom}`；缺块返回 `null`；`state()` 增 `vexag`、`demZoom` | LC地图 | 现有 `window.__LC3D`/`debugSetView` 已有，扩展即可。**允许降级实现**：射线沿 `demAt` 网格步进 + 二分，而非精确三角面求交；TD-007 仍硬断言，TD-008 放宽到 2 网格 |
```
  `AT-P0-4-3d-probe.md:5`：
```md
- 状态：**未实现 → 当前 BLOCKED**；实现落地后按本文件直接执行
```
  `map.html:1354-1362`：
```js
window.__LC3D = {
  setView: debugSetView,
  setMode: setMode,
  state: getState,
  fps: getFps,
  screenshot: function () {
    try { return glCanvas.toDataURL("image/png"); } catch (e) { return ""; }
  }
};
```
- **触发条件**：按 `AT-P0-4-3d-probe.md` 执行 TD-001…TD-020。
- **预期 vs 实际**：预期 `__LC3D` 提供 `probe/pick`，`getState()` 含 `vexag`；实际只有 `setView/setMode/state/fps/screenshot`，`getState()`（`map.html:1334-1344`）没有 `vexag`（有 `demZoom`）。该文档自判 `BLOCKED` 是诚实的，但同一功能存在两份编号体系不同的 AT（pick 文档 TD-001…016、probe 文档 TD-001…020，其中 TD-012 要求断网 ≤5 s 回落 2D），执行者无法判断以哪份为准。
- **影响**：第二套 AT 无法执行；C-05 的「断网回落 2D」在此文档里是硬性 TD-012（`:78`），说明缺降级不是「未要求」，而是「要求了没实现」。
- **修复方向**：由总监/测试定唯一 AT 规范（建议 probe 文档的 TD-001…020 为准，pick 文档的 TD-001…015 合并）；地图侧补 `probe/pick` 与 `state().vexag`，或把 probe 文档标为「未来」并从当前验收集移除。

---

## 7.1 第三批（本轮复核新增，已证实）

> 3 条均在本轮逐行复核时发现，不在上一版 31 条内；编号接 C-34。C-35 为显示口径缺陷，C-36/C-37 为主路径功能缺陷。

### C-35 `setSitFace` 用 `data-sit` 单向锁存顶部标签，`setHeading` 的度数分支成为死代码且无清除路径

- **严重度**：低　**证据等级**：已证实　**批次**：三（复核新增）
- **位置**：`map.html:349-352`（度数分支）、`map.html:413-418`（`setSitFace` 置位）、`map.html:363-384`（`applySit`/`toggleSitNorth` 均不清除）；调用方 `MainActivity.java:684-685`
- **代码原文**：
```js
  var lbl = document.getElementById("heading-label");
  if (lbl && lbl.getAttribute("data-sit") == null) {
    lbl.textContent = "向 " + (Math.round(lastHeading) % 360) + "°";
  }
```
```js
function setSitFace(face, sit) {
  var lbl = document.getElementById("heading-label");
  if (!lbl) return;
  lbl.setAttribute("data-sit", "1");
  lbl.textContent = "向 " + face + " · 坐 " + sit;
}
```
- **触发条件**：App 运行后第一个传感器样本——`updateReading()` 对两个 WebView 各推一次 `setSitFace(...)`（`MainActivity.java:682-686`），此后 `#heading-label` 的 `data-sit` 恒为 `"1"`；再点「坐南朝北」按钮循环 1→2→0 也不会清除它。
- **预期 vs 实际**：
  - 预期：标签随坐向对齐状态与航向变化；关闭对齐（`sitMode=0`）或未收到山名推送时，应回到「向 N°」或至少随 heading 更新。
  - 实际：`data-sit` 只置不清，`setHeading` 的度数分支（`:350-352`）在首个样本后**不可达**（死代码）；`applySit()`（`:363-377`）与 `toggleSitNorth()`（`:379-384`）都只改 `btn_sit` 文案/Bearing，不动标签。标签永久停在上一次推送的「向 X山 · 坐 Y山」，与按钮态（关/向朝上/坐朝上）脱钩。
- **影响**：低——不参与 pick 与读数计算；但界面显示口径与对齐状态不一致，排查「坐向为什么没转」时会误导（标签一直有山名，看不出对齐已关）。
- **修复方向**：`applySit()` 在 `sitMode===0` 时 `removeAttribute("data-sit")` 后调 `setHeading(lastHeading)`；或把 `setHeading` 与 `setSitFace` 合并为一个 `setReading(heading, face, sit, sitMode)`，由 `sitMode` 决定标签格式，避免两个入口争同一个节点。

### C-36 2D 截图退路抓的是隐藏的 `#gl` 画布：API 24–25 的 2D 截图必为空白图

- **严重度**：中　**证据等级**：已证实（代码路径 + Canvas 规范行为）　**批次**：三（复核新增）
- **位置**：
  - `MainActivity.java:434-451`（`SDK_INT>=26` 才用 PixelCopy，否则直接 `captureViaJs()`）
  - `MainActivity.java:468-493`（`querySelector('canvas')` + `toDataURL` + `length<32` 判据）
  - `map.html:11`（`#gl{display:none}`）、`map.html:47`（canvas 常驻 DOM）、`map.html:1359-1361`（`toDataURL`）
- **代码原文**：
  `MainActivity.java:449-451`：
```java
        } else {
            captureViaJs();
        }
```
  `MainActivity.java:473-474`：
```java
        compassMapView.evaluateJavascript(
                "(function(){var c=document.querySelector('canvas');return c?c.toDataURL('image/png'):'';})()",
```
  `MainActivity.java:477-479`：
```java
                        if (value == null || value.length < 32) {
                            toast("截图失败：地图未渲染");
                            return;
                        }
```
  `map.html:11`：
```css
  #gl { position:absolute; inset:0; width:100%; height:100%; display:none; }
```
- **触发条件**：2D 模式下点「截图」，且满足任一：① 设备 API 24–25（`build.gradle:11` `minSdk 24`，`SDK_INT>=26` 为假 → 必走 `captureViaJs`）；② API≥26 但 PixelCopy 返回非 SUCCESS 或 `looksBlank` 判空（`MainActivity.java:439-445`）→ 同样落到 `captureViaJs`。
- **预期 vs 实际**：
  - 预期：2D 截图包含底图瓦片；失败时提示「截图失败」。
  - 实际：`document.querySelector('canvas')` 命中的是唯一 canvas `#gl`；2D 下它从未 `resizeGL()`、从未绘制（`resizeGL` 仅在 3D 路径调用），保持默认 **300×150**，`toDataURL` 返回一张合法透明 PNG（长度远大于 32，通过判据）→ 解码成功 → 一张空白图被 `saveCompassShot()` 存进相册（角注照常画上）。若此前进过 3D，`preserveDrawingBuffer:true`（`map.html:601`）会保留最后一帧 3D 画面 → 2D 截库存下的是与当前视图无关的旧 3D 残影。
- **影响**：中——API 24–25 上，**2D** 模式的「截图」主功能产出空白图且无任何失败提示（3D 下 `#gl` 已 resize/绘制，同路径正常）；API≥26 的退路在 2D 下同样可能静默存空白，用户以为已截到地图。
- **修复方向**：`captureViaJs` 先判 `c && c.width>1 && c.height>1 && getComputedStyle(c).display!=="none"`，否则 `toast("截图失败：地图未渲染")` 并返回；2D 截图改用可见 WebView + PixelCopy，或按 `getState().mode` 走「3D 用 toDataURL、2D 必须 PixelCopy」的分流。

### C-37 3D 高 zoom 下网格分辨率与 DEM 级别脱钩：zoom 18 时单屏宽不足 1 个网格单元

- **严重度**：中　**证据等级**：已证实（代码常量 + 数学推导）　**批次**：三（复核新增）
- **位置**：`map.html:753`（DEM 级别 clamp 到 12）、`map.html:520`（`GRID=96`）、`map.html:813`（`step=PATCH_PX/N`）、`map.html:1208-1210`（相机距离 `D`）；对照 `docs/map-bridge.md:49`
- **代码原文**：
```js
  var zdem = Math.max(4, Math.min(12, Math.round(zoom) - 1));
```
```js
var GRID = 96;                       // 网格 96×96 顶点
```
```js
  var zMpp = 156543.03392 * Math.cos(center.lat * Math.PI / 180) / Math.pow(2, zoom);
  var hCss = Math.max(1, glCanvas.clientHeight);
  var D = (hCss * zMpp) / (2 * Math.tan(FOV / 2));
```
- **推导**（纬度 39.9°、真机 1228×2700 / dpr 3.375 → CSS 宽 364；WebView 高取整屏 800 减底栏约 60 → **h_css≈740**，取 800 时结论同向）：
  - 网格步长 = `768/96 = 8` 个 DEM 像素；`patch.z` 上限 12 → `mpp≈29.32 m/px` → **单元边长 234.6 m**。
  - 相机距离 `D≈858·zMpp`；zoom≥13 时 `Math.round(zoom)-1>12` 被 clamp 到 12，`D` 继续随 `zMpp` 缩小，而单元边长不变。
  - zoom 14：`zMpp≈7.33 m/px` → `D≈5.8 km`、单屏地面高≈5.4 km → 约 23 格（尚可）。
  - zoom 18：`zMpp≈0.458 m/px` → `D≈364 m`、单屏地面高≈339 m → 纵向 **1.45 格**；CSS 宽 364 px → 地面宽 167 m → 横向 **0.71 格**。整屏地形只由 1–2 个网格四边形（2–4 个三角形）拼成。
- **触发条件**：3D 内用「放大 +」、`#in3d&z=18` 或 `setView(lat,lng,18)` 进入 z≥14。
- **预期 vs 实际**：
  - 预期：放大后地形细节随 DEM 级别提升，至少维持每屏数十个网格单元；文档「一块覆盖约 1.6 屏」应可复算。
  - 实际：网格恒用 z12 DEM（`:753`），z≥14 后地形退化为低模，山脊与等高线在屏幕上是折线；等高线（100 m/500 m）在不足 2 格/屏时基本画不出。`docs/map-bridge.md:49` 的「一块覆盖约 1.6 屏」在 364 px 视口下 zoom≤13 已是约 4.2 屏，zoom>13 后更不成立。
- **影响**：中——主路径（放大看山势）可视质量失真；pick 与渲染仍同源（都 `demAt` z12），数值不因此错，但「看到的山形」与实际网格严重不符，用户无法判断点选落点。
- **修复方向**：把 `zdem` 上限从 12 提到实测可达的源级别（先验证 z13–z15 瓦片）；或按 `zoom-patch.z` 提高 `GRID`/细分；文档补「覆盖屏数 = `768·2^zoom/(2^patch.z·视口CSS宽)`」，不要写死 1.6 屏。

---

## 8. 必查项缺口（本批仍不能闭合的部分）

| 必查项 | 已闭合 | 仍未闭合 | 缺什么证据/动作 |
|---|---|---|---|
| ① DEM 失败降级 | 贴面失败降级成立（`map.html:899-903,1274`）；DEM 失败路径已证（C-05） | DEM 失败自动回 2D 未实现；首块失败/换块失败的画面差异未真机截图 | 飞行模式真机跑 `AT-P0-4-3d-probe.md:78` 的 TD-012：≤5 s 回落 2D + 提示；抓 `LC3D ERROR dem-load` |
| ② 相机/投影/pick 边界 | 贴地平视 `up` 特判正确；极区/反经线/贴地入山已证（C-08/C-09/C-10） | pitch 0° / 60° 的屏幕-经纬度误差未实测；pick 与渲染表面的高差未量化 | 真机按 probe TD-007/TD-008 对比 `pick.elev` 与 `probe.elev`（需先有 probe，C-34） |
| ③ `VEXAG` 与 pick 同空间 | 已闭合（C-11）：三处都乘 2.5，输出 elev 为原始值 | 网格插值 vs 逐像素双线性的偏差未量化 | 陡峭地形取 3 个点，比较 pick 世界 y 与三角面插值 y |
| ④ 双 WebView `__LC3D` | JS 上下文隔离已闭合（C-12） | 隐藏实例是否停 RAF 未实测（C-19）；共享 `mapMarker` 无消费已证但影响待定 | 真机切 Tab 后 10 s logcat 看隐藏实例 `LC3D fps=`；`dumpsys gfxinfo` |
| ⑤ 天地图 key | 明文 + 原版 key + 单次请求实测已闭合（C-01） | 日配额、是否绑包名/域名、被吊销的影响面 | 天地图控制台配额截图；换包名请求的 403 证据（当前只测了 200） |
| ⑥ `map-bridge.md` 一致性 | 契约破裂 + 4 处漂移已闭合（C-06/C-17） | 文档修订本身未做（归 M6/地图） | 按 C-06/C-17 清单更新 `docs/map-bridge.md`，再由证据审计复核 |



---

## 9. 未覆盖文件逐文件清单（读了几行 / 为什么停）

> 口径：行数用 `[System.IO.File]::ReadAllLines(...).Count` 实测；「读了」指逐行读过，「grep」指只按关键词命中，不算通读。审计可直接按此表复核。

### 9.1 M3 范围（100% 通读，无未覆盖）

| 文件 | 行数 | 读了 | 说明 |
|---|---|---|---|
| `app/android/app/src/main/assets/map.html` | 1605 | 1605 | 全文逐行；本报告 C-03/C-04/C-05/C-08…C-19/C-22…C-34 的源码依据 |
| `tools/map/run_at_p04.ps1` | 41 | 41 | 全文 |
| `tools/map/run_td015_device.ps1` | 51 | 51 | 全文 |

### 9.2 交叉文件（全读，用于契约/档案核对）

| 文件 | 行数 | 读了 | 用途 |
|---|---|---|---|
| `MainActivity.java` | 767 | 767 | Tab/布局、JS 桥调用、传感器推送、截图链路 |
| `LCAndroid.java` | 66 | 66 | pick 事件校验/字段 |
| `DebugBridge.java` | 42 | 42 | 广播接收器（C-02 交叉引用 B-02） |
| `MapMarker.java` | 28 | 28 | D-005 模型与消费情况 |
| `res/layout/activity_main.xml` | 50 | 50 | 底部三 Tab 布局 |
| `AndroidManifest.xml` | 30 | 30 | 权限/导出组件 |
| `app/android/app/build.gradle` | 38 | 38 | minSdk 24 / target 34 |
| `docs/map-bridge.md` | 233 | 233 | 契约主文档 |
| `docs/qa/AT-P0-4-3d-pick.md` | 46 | 46 | TD-001…016 断言口径 |
| `docs/qa/AT-P0-4-3d-probe.md` | 117 | 117 | 第二套 TD-001…020（C-34） |
| `docs/TEAM_CHARTER.md` | 112 | 112 | D-001…D-009（D-006 key 口径） |

### 9.3 部分读取（读了多少、为什么停）

| 文件 | 行数 | 读了 | 未读范围与原因 |
|---|---|---|---|
| `CompassView.java` | 358 | 200（30-229） | 未读 1-29（imports/字段声明）与 230-358（圈层绘制后半、`drawText/pos/triangle`）。原因：只核与地图叠层相关的 `setSensorHeading/onTouchEvent/onDraw` 前段（不透明度/触摸/停跟随）；圈层绘制与读数归 M2，不在 M3 定论 |
| `docs/review/00-MATRIX.md` | 253 | 120（1-120） | 未读 121-253（M4-M8 文件清单）。原因：M3 行（第 72-74 行）与模块统计已核对，其余按模块归属 |
| `docs/gap-matrix.md` | 185 | 约 35（120-154）+ 全文 grep | 未逐行读 1-119、155-185。原因：只取 F/G 地图段与 E6/F4/C2 条目作为原版布局档案 |
| `app/MECHANICS.md` | 505 | 45（108-152）+ 全文 grep | 未读其余段。原因：只取 `CompassToolsKt`/Map classes 的原版架构证据（WebView+overlay） |
| `docs/product/PRD-v0.1.md` | 437 | 30（78-107）+ 全文 grep | 未读 §其余。原因：只取 B-01…B-20 并核对 B-19「未逐页比对」 |
| `docs/design/round-c-diff.md` | 64 | 17（48-64） | 未读 1-47。原因：只取截图链路（B-09/B-17）作为 C-07 的旁证 |
| `docs/FINAL-REPORT.md` | 105 | 0，仅 grep | 命中第 31/57/75/97 行。原因：归 M6；本报告只交叉核对地图声明（`MapMarker` 链路、TD 计数） |
| `docs/design/single-source-of-truth.md` | 298 | 0，仅 grep | 原因：设计草案，不是基线事实源；只查 M3 相关词 |
| `docs/qa/bugs.md` | 287 | 0，仅 grep | 原因：M5 台账；只查地图/3D/定位相关 6 条，未与 M3 结论冲突 |

### 9.4 完全未读（按分工/范围，不声称审查）

| 模块/文件 | 规模 | 未读原因 |
|---|---|---|
| `app/src/main.rs` 701、`app/src/software.rs` 321、`app/src/dial.rs` 254 | 3 文件 / 1276 行 | M1 桌面端；桌面版没有地图，M3 不覆盖；地图相关契约只在 Android `map.html` |
| `CompassData.java` 280、`CompassListActivity.java` 112、`CompassReadSheet.java` 95、`SensorMath.java` 40 | 4 文件 / 527 行 | M2 读数/盘面/传感器；M3 只通过 `setHeading(deg)/setSitFace(face,sit)` 契约引用，未逐行读 |
| `tests/**` | 22 文件 / 2974 行 | M5；本报告只读 `docs/qa` 的两份 P0-4 AT（不算 tests 资产） |
| `backend/**`、`scripts/**`、`app/android/**` 构建配置（除 `build.gradle`） | 26 文件 / 2812 行（矩阵 M4） | M4；部署/构建不在 M3 |
| M6 文档其余（42 文件 / 3264 行，除 9.2/9.3 所列） | 约 30 文件 | M6；地图契约只以 `map-bridge.md`/`gap-matrix`/`PRD` 为限 |
| `app/mechanics.json`（45,931 B）、`tools/mechanics/gen-mechanics.ps1` 279 行 | M7 | 数值档案/生成器；M3 不需要（`map.html` 不读 mechanics.json） |
| `res/drawable-nodpi/*`（5 张 `dial_full_*.png`、4 张 `dial_*_min.webp`、`dial0.png`） | 二进制 | M2 盘面视觉；本报告未做像素比对。罗盘叠层视觉见 C-21 待确认 |
| `investigation.cany/**`、`craft.json`、`usage.json`、`.gitignore` 等 | M8/M4 | 研究工件/构建，不入 M3 |

### 9.5 未运行的验证（数字为推算，不是实测）

- 未运行 `tools/map/run_at_p04.ps1`（需本机 Chrome headless + Python HTTP + 外网）；未运行 `tools/map/run_td015_device.ps1`（需 D-002 单台真机，且在 LC测试 手上）。
- 未构建 APK、未安装、未抓 logcat；未做飞行模式/断网实测（C-05 的 TD-012 缺口）。
- **本轮已运行（2026-11-05 复核）**：① 天地图单瓦片 GET（C-01，命令与结果见该条）；② 用 Node 复现 C-03 的 193/9409 与 C-09/C-10/C-28/C-37 的数值推导（脚本放 `%TEMP%`，未写入基线工作区）。
- 因此：帧率、内存量级（C-15）、隐藏实例是否停渲染（C-19）、定位真机行为（C-20）、`#extension` 实际行为（C-18）均为**代码推算或高度可疑**，不作为已证实结论；C-36 的「空白图」由代码路径 + Canvas 规范推导成立，但未在 API 24 设备实拍（实拍只改「是否真落到该退路」的概率，不改结论）。



## 10. 已读文件清单与覆盖声明

| 文件（相对 review-base） | 行数 | 读取范围 | 覆盖声明 |
|---|---|---|---|
| `app/android/app/src/main/assets/map.html` | 1605 | 全文 | **已全量审读**（M3 主文件） |
| `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 767 | 全文 | **已全量审读**（JS 桥调用契约、Tab/布局） |
| `app/android/app/src/main/java/com/luopan/fengshui/LCAndroid.java` | 66 | 全文 | 已审读 |
| `app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java` | 42 | 全文 | 已审读 |
| `app/android/app/src/main/java/com/luopan/fengshui/MapMarker.java` | 28 | 全文 | 已审读 |
| `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 358 | 30-229 | **部分**；叠层/触摸相关段已读，圈层绘制后半未读（见 §9.3） |
| `app/android/app/src/main/res/layout/activity_main.xml` | 50 | 全文 | 已审读 |
| `app/android/app/src/main/AndroidManifest.xml` | 30 | 全文 | 已审读 |
| `app/android/app/build.gradle` | 38 | 全文 | 已审读（minSdk 24 / target 34） |
| `docs/map-bridge.md` | 233 | 全文 | **已全量审读**（契约） |
| `docs/qa/AT-P0-4-3d-pick.md` | 46 | 全文 | 已审读 |
| `docs/qa/AT-P0-4-3d-probe.md` | 117 | 全文 | 本批补读（C-34） |
| `tools/map/run_at_p04.ps1` | 41 | 全文 | 已审读 |
| `tools/map/run_td015_device.ps1` | 51 | 全文 | 已审读 |
| `docs/TEAM_CHARTER.md` | 112 | 全文 | 已审读（D-001…D-009） |
| `docs/review/00-MATRIX.md` | 253 | 1-120 | **部分**（M3 行 72-74 已核对） |
| `docs/gap-matrix.md` | 185 | 120-154 + grep | **部分**（地图/3D 段） |
| `app/MECHANICS.md` | 505 | 108-152 + grep | **部分**（CompassToolsKt/Map classes 段） |
| `docs/product/PRD-v0.1.md` | 437 | 78-107 + grep | **部分**（Android B-01…B-20） |
| `docs/design/round-c-diff.md` | 64 | 48-64 | **部分**（截图链路） |
| `docs/FINAL-REPORT.md` | 105 | grep | **仅检索**，未通读 |
| `docs/qa/bugs.md` | 287 | grep | **仅检索**（地图相关 6 条） |

> 覆盖声明：M3 的 3 个跟踪文件（`map.html`、`run_at_p04.ps1`、`run_td015_device.ps1`）与 `docs/map-bridge.md` 已 100% 通读；Java 侧契约文件除 `CompassView` 后半外已通读。其余文档为定向阅读，未读部分逐文件列在 §9.3/§9.4，未读即未审。

## 11. 复现与验证缺口（下一步，按可执行性排序）

1. **可用纯数学复现（不需真机）**：C-03（已用同函数复现 193/9409）、C-09（纬 84.93° → 9 块全空）、C-10（反经线 lng<-180）、C-15（内存量级）、C-28（pick 步数 1144）、C-37（zoom 18 单屏 <1 格）。可整理成一个 `checks/` 下的 JS/Python 推演脚本（不在基线工作区生成构建产物）。
2. **需真机（D-002 单台 L2E0222531014833，排队）**：C-05（飞行模式 TD-012：≤5 s 回 2D）、C-19（隐藏实例 RAF）、C-20（定位）、C-18（WebView 对 `#extension` 的行为）、C-22/C-23/C-24（触摸/旋转手感）。
3. **需服务端/控制台证据**：C-01（天地图日配额、绑包名/域名、吊销影响）。
4. **文档侧（归 M6/地图）**：按 C-06/C-17 更新 `map-bridge.md`；按 C-34 统一两份 P0-4 AT 的编号与状态。
5. **跨模块引用**：B-02（DebugBridge 注入）、B-03+B-04（三 Tab/罗盘浮层，C-07）、B-05（触摸停跟随，C-16）三条母条；M5 的 `tests/at/run-at.ps1` 缺失（Q-02）、M6 的 `FINAL-REPORT` 地图声明；本报告不重复计数。
