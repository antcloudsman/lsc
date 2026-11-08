# M6 Stage 5：地图侧代码审计（map.html 与原生桥接）

审阅：LC审地图　｜　基线：`origin/develop` = `622cf28`　｜　范围：`app/android/app/src/main/assets/map.html`（3214 行）与 `MainActivity.java` 中的地图桥接部分（4223 行）
方法：全文件检索 + 逐条回原文核准；每条给「文件:行号 / 原文 / 为什么是问题 / 影响面」，未上机、未改代码。

## 1. 状态不同步（影响最高，会直接造成“点了相反结果”）

### M-01 重载后 toggle 语义与真实状态错位：点「关」会变成「开」
- 原文：`MainActivity.java:1527` `case "等高线": js("toggleContour()"); break;`
  `MainActivity.java:1537` `js("toggleGround()");`
  `MainActivity.java:1964` `contour.setOnClickListener(v -> js("toggleContour()"));`
- JS 侧状态是页面变量、**重载即回默认**：`map.html:199` `var contourOn = false;`、`map.html:200` `var groundMode = false;`
- 而 `onPageFinished` 的重放清单只有 `setRoadNet(...)`（`MainActivity.java:3764`）与 `setMode('3d')`（`3767`），**不含 contour / ground**。
- 为什么是问题：原生用 **toggle（翻转）** 语义，却没有任何地方回读或重放真实值。页面一旦重载（`location.reload()`、WebView 进程回收后重建、崩溃恢复），JS 侧归零，原生按钮的点击语义仍按“翻转”执行 → **用户点「关闭等高线」，实际把它打开**；界面上也看不出（原生没有等高线状态显示）。
- 影响面：等高线（两处入口：左列 + 收藏点态）、贴地（左列）。任何一次页面重载后进入“先反后正”的错位，用户侧表现为“按钮失灵/点了相反”，且**无法通过再点一次自愈**（每次点都在两态间跳）。
- 修法：JS 已有 `setContour(on)`（`map.html:1202`）却未被使用。原生改为持有 `contourOn/groundMode` 内存态（与 `roadNetOn` 同法），点按钮走 `js("setContour(" + v + ")")`，并在 `onPageFinished` 重放；贴地需补一个 `setGround(on)`（现只有 `toggleGround()`，`1752`）。

### M-02 指针开关存两份，重载后两副本相反
- 原文：`map.html:193` `var needleOn = true;`（JS 默认开）
  `MainActivity.java:1549` `case "指北针": js("toggleNeedle()"); break;`
  `MainActivity.java:1562` `js("toggleNeedle()");`
  JS 翻转体：`map.html:1716-1717` `function toggleNeedle() { needleOn = !needleOn;`
- Java 侧另有一份 `pointerOn`（`MainActivity:860` 一带，随「指针开关」切换），**两份各自翻转、互不核对**；`onPageFinished` 不重放 `needleOn`。
- 为什么是问题：重载后 JS 的 `needleOn` 回到 `true`，Java 的 `pointerOn` 保持用户上次的值。此时开关图标显示“关”，而地图侧 `#needle` 已在按 `true` 渲染；用户点一次（Java 以为在关→JS 实际在关→表现为“点了一下才恢复到图标所示状态”），状态与显示**错一拍**。
- 影响面：指针/指北针两处入口、所有页面重载后。属“同一状态存两处”的典型：没有唯一权威源。
- 修法：与 M-01 同批改 set 语义（新增 `setNeedle(on)` 或直接用 `style` 直控），并在 `onPageFinished` 按 Java 值重放；或彻底改为“JS 为唯一权威 + 原生每次回读”。

### M-03 同一类状态存在两套规范（set+replay vs 只有 toggle）
- 已有正确做法：`map.html:1238 setRoadNet(on)`、`1263 setRidge(on)`、`1202 setContour(on)`；Java 侧 `roadNetOn` 有持久 + 重放（`3764`）、`is3D` 有重放（`3767`）。
- 落后做法：`toggleContour` / `toggleGround` / `toggleNeedle`（`1746` / `1752` / `1716`）只有翻转、无 set、无重放。
- 为什么是问题：代码库里**两套规范并存**，新改一处时无法从邻近代码推断“该用哪种”，M-01/M-02 就是这么产生的；同文件内 `setContour` 已存在而没人用，是最直接的证据。
- 影响面：契约维护成本 + 每次新增开关都可能重复踩坑。

## 2. 兜底吞异常（事件丢失无法定位）

### M-04 原生桥接把所有 JS 异常吞成空语句（32 处调用共用）
- 原文：`MainActivity.java:1250-1251`
  ```java
  private void js(String code) {        if (compassMapView == null) return;
      compassMapView.evaluateJavascript("try{" + code + "}catch(e){}", null);
  ```
- 为什么是问题：`catch(e){}` 既不 `console.log` 也不回传值。任何一次 `js(...)` 里函数不存在、参数类型错、状态前置不满足，**都表现为“什么都没发生”**。本轮真实发生过：`resetCompass` 未落地时 `typeof resetCompass === undefined`，若调用处没写守卫，这里会静默吞掉，排查只能靠猜。
- 影响面：全部 32 处 `js("...")` 调用点（含 setView、toggle3D、setBase、各 toggle、resetCompass 等）。
- 修法：`catch(e){ console.log("LCJS ERROR " + e); }`，或改用带 `ValueCallback` 的重载把异常回传到 logcat；至少不要空吞。

### M-05 桥接事件回调吞异常，事件丢失无痕迹
- 原文：`map.html:2513` `} catch (e) { /* 桥未接入时静默 */ }`（`emit()` 内）
- 为什么是问题：这条静默同时覆盖了“桥真的没接”和“事件体里抛错”两种情况。后者（pick/fish/overlay 事件序列化失败等）会让原生永久收不到事件，而地图侧无任何日志。
- 影响面：`pick` / `pick-clear` / `fish-pick` / `overlay-layer` / `attribution` / `src-panel` 等全部 `emit` 事件。

### M-06 异步海拔失败把“没数据”和“出错”压成同一个值
- 原文：`map.html:1446-1447`
  ```js
  cb(elevAt(lat, lng));
  }).catch(function () {
  ```
  （`elevAtAsync`；reject 时回调 `null`）
- 为什么是问题：调用方拿到 `null` 无法区分“该点 DEM 没覆盖”与“DEM 拉取失败/超时”。前者正常，后者是故障，但表现相同。
- 影响面：海拔查询、立向点高程、以及任何用 `elevAtAsync` 的链路。

### M-07 DEM 单块失败原因被丢弃
- 原文：`map.html:2243` `jobs.push(demTile(z, x, y).catch(function () { return null; }));`
- 为什么是问题：失败被压成 `null`，后续只能报 `dem-missing`（“缺了”），**无法区分 DNS/404/超时/证书**——而这三类的处置完全不同（网络问题重试即可，404 说明瓦片不存在，需换源）。本轮 `dem-load` 死分支被删后，这个信息位彻底没有落点。
- 影响面：3D 地形加载失败的全部现场。

## 3. 占位与写死

### M-08 3D 参数是裸魔法数，改一处会静默改变贴面成功率
- 原文（分散在 `map.html`）：`var GRID = 96`、`PATCH_PX`、`setMode` 内 `if (zoom < 9) zoom = 9;`、`var zdem = Math.max(4, Math.min(12, Math.round(zoom) - 1));`、贴面成功判据 `if (ok >= 30)`（36 张里的 30 张）。
- 为什么是问题：`30/36` 与 `zoom-1` 是**经验值**，没有命名常量也没有对照文档；改 `GRID` 或 zoom 偏移会连带改变 `ok` 的分布而无人察觉（本轮 `buildMesh` 越界 193 顶点就是这类“参数耦合”的产物）。
- 影响面：3D 地形生成与贴面；任何调 3D 性能的改动。

### M-09 同一个“点选”手势有两个阈值
- 原文：`map.html:1849` `if (dragging && totalMove < 6) placeMarker(lastX, lastY);`（鼠标）
  `map.html:1962` `if (touchStart && touchStart.moved < 8) placeMarker(touchStart.x, touchStart.y);`（触摸）
- 为什么是问题：同一语义（“位移小于阈值视为点选”）两条路径用 6 与 8，真机走触摸、调试常用鼠标 → **同一手势在两条路径上判定不同**，复现问题时会出现“我这边能点、你那边点不到”。
- 影响面：立向点/打点的交互一致性；测试与实现的坐标判定口径。

### M-10 双指缩放与双击缩放不存在（登记为缺口，不是缺陷）
- 证据：`map.html` 内 `touches.length === 2`、`dblclick`、`doubleTap` **0 命中**；`meta viewport` 为 `maximum-scale=1, user-scalable=no`，`#map` 为 `touch-action:none`。
- 结论：缩放入口只有滚轮与按钮（`zoomIn/zoomOut`）。按本轮口径记**「未实现」**（代码无路径），不是「未验证」。

## 4. 已知但不在本条范围的项（避免重复上账）

- `#needle`（`map.html` 自加、原版零对应）与 `js("toggleNeedle()")` 分支：Stage 2 已裁定**挂起**，等第 3 条两个符号取证后同批处理。
- `demAt` 曾把缺角当 0、`buildMesh` 采样越界 193 顶点、`dem-load` 死分支：Stage 2 已由地图侧修复并验证，不计入本次审计。

## 5. 未覆盖

- 未上机复现任何一条（按设备协议，设备归 @LC测试）；本审计全部为代码级结论，均给出可核对的原文行号。
- 未审 `M3WebActivity` 的帮助中心 WebView 桥（属客户端审计范围）。
- 未审 3D 投影/`pick` 的数学正确性（属 M3 已定结论范围，本轮不重开）。
