# MAP-DELTA — 地图 / 3D 增量审查（`origin/develop` @ `bfb8492`）

> 审查人：LC审地图｜冻结基线：`d752efa`（tag `review-20261105`）｜增量端点：`origin/develop` = **`bfb8492`**
> 工作区：`E:\APPDetector\review-delta`（只读快照；实际分支为 `review/delta-client`，见 §0 说明）
> 方法：`git diff d752efa..bfb8492`；`map.html` 1605 → **2816 行**（+1235/−24，函数 87 → 160）；新增 Java 38 个，本报告覆盖指令点名 9 个 + 4 个被引用 helper（`MapKeys`/`MapPoints`/`LcHttp`/`LcFeatureActivity`）。
> 纪律：只读，不改代码、不提交；每条给「文件:行 + 代码原文 + 问题 + 建议」；未覆盖逐项声明（§6）。
> 证据等级：全部「已证实」（有代码路径或实测）；未达标的只进 §6 未覆盖/待确认。

## 0 结论摘要

- **无阻断级**；共 **14 条**：高 2、中 8、低 4。按指令四轴：合规 2、正确性 4、健壮性 4、安全 2、契约 2（性能并入健壮性/正确性条目）。
- **最该先修 3 条**：`MD-01`（原版 key 双处硬编码 + 失败日志泄 `tk`）、`MD-02`（历史影像显示 Esri 却署名天地图）、`MD-03`（缓存把透明注记转 JPEG → 黑块）。
- **正面确认（逐项查过，未发现问题）**：全树 **无** `21atcloud` / `striograph` / `21at.` 私有瓦片引用，`xinzhi` 仅出现在「不照搬」注释（`OverlayActivity.java:36`）；`GS(2026)1219` 仅出现在「不照抄」注释（`FishingActivity.java:38`）与文案；山脉/水系/钓点/叠加/历史影像的「免费近似、不可接入、审图号未确认」都在 UI 文案里显示（`strings_water.xml`、`strings_fish.xml:8`、`strings_history.xml:5-7`、`strings_geo.xml:7`，Bing 署名经 `creditView` 逐图显示）；5 个新 WebView 只 `loadUrl("file:///android_asset/map.html…")`，桥只暴露 `onEvent(String)`；全树 `http://` = 0；无头契约自测 PASS（原文见 §5）。
- **工作区分支说明**：指令要求 `-b review/delta-map`，但 `E:\APPDetector\review-delta` 已由他人以 `review/delta-client` 建好并停在 `bfb8492`。为避免切分支影响对方，我在同一快照只读审查、只写本文件；源码内容与指令要求一致，若必须分支名一致请另建 worktree。
- **快照与漂移**：本报告结论以 `bfb8492` 为准；审查期间 `origin/develop` 已推进到 `1c9239f`（`map.html` +19/−3，新增 `lc_measure_addCenter()`；`map-bridge.md` 新增 §11 F-12；F-15 客户端 `Track*` 8 文件为快照后新增）。漂移已逐条标注（MD-14、§6），未混入上述结论；F-15 地图侧 `lc_track_*` 在 `bfb8492` 内，已覆盖。

## 1 条目总表

| 编号 | 轴 | 级别 | 一句话 |
|---|---|---|---|
| MD-01 | 合规 | 高 | 原版天地图 key 仍硬编码两处；失败日志把含 `tk=` 的完整 URL 写进 logcat |
| MD-02 | 合规 | 高 | 显示 Esri 历史影像时，`#srcInfo`/`getAttribution()`/HUD 仍标「天地图地形」 |
| MD-03 | 正确性 | 中 | 瓦片缓存把带 alpha 的 PNG 注记重编码为 JPEG，命中缓存后注记变不透明块 |
| MD-04 | 正确性 | 中 | 2D 旋转/坐向模式下，测量/画笔/海拔打点未按 bearing 反变换，落点偏移 |
| MD-05 | 正确性 | 中 | 轨迹回放 follow 在 2D 每帧调 `render()`，全量重建瓦片层 |
| MD-06 | 性能 | 中 | 3D 每帧全量重建叠加/轨迹/测量 SVG（O(N) DOM 重建，静止也不停） |
| MD-07 | 正确性 | 中 | 海拔查询只读 DEM 缓存；纯 2D 下 `elevAt` 恒 `null`，契约未声明该前置 |
| MD-08 | 健壮性 | 中 | SuperMap：磁盘缓存无上限；任一瓦片失败整图作废；3072² 位图 + PNG100 体积 |
| MD-09 | 健壮性 | 中 | 叠加/钓点/轨迹桥入参无条数上限，逐点建 DOM 且每帧遍历 |
| MD-10 | 安全 | 中 | `LcHttp.UA` 用浏览器 UA 绕过天地图 WAF 403（注释自认），UA 写死机型 |
| MD-11 | 安全 | 低 | Wayback release id 来自外部 config，未校验直接拼进 `evaluateJavascript` 与 `<img src>` |
| MD-12 | 健壮性 | 低 | Java `onEvent` 缺字段坐标取 0；JS 端只验 number 不验范围 |
| MD-13 | 正确性 | 低 | `lc_track_progress` 0..1=比例、其余=绝对 ms，语义二义 |
| MD-14 | 契约 | 低 | 18 项新增 API/事件未写入 `docs/map-bridge.md`；`getState` 新字段只补了 `roadNet` |

---

## 2 合规

### MD-01 原版天地图 key 仍硬编码两处；失败日志把含 `tk=` 的完整 URL 写进 logcat

- **级别**：高｜**证据等级**：已证实
- **位置**：`app/android/app/src/main/assets/map.html:107`、`app/android/app/src/main/java/com/luopan/fengshui/MapKeys.java:6-12`、`LcHttp.java:54`、`MapPoints.java:42/81/108`、`SuperMapActivity.java:397`
- **代码原文**：
  `map.html:107`：
```js
var KEY = "dd18858b33229f5837ad1d97b8d3105e";
```
  `MapKeys.java:6-8`：
```java
 * 注意：当前值沿用 `assets/map.html` 顶部内置的原版 key（归属第三方）。
 * 上线前应换 LC 自有 key（个人配额 1 万次/日/服务，见 docs/map-bridge.md §2.3）。
 * 换 key 时只改这一处 + map.html 的 KEY 常量。
```
  `MapKeys.java:12`：
```java
static final String TDT = "dd18858b33229f5837ad1d97b8d3105e";
```
  `LcHttp.java:54`：
```java
            Log.w(TAG, "GET bytes fail " + url + " : " + e);
```
- **问题**：
  1. 冻结基线 `C-01` 已判「原版 key 违反 D-006」，增量把同一 key **复制成 Java 第二处硬编码**（`MapKeys.TDT`），换 key 要改两处，注释自己也承认。
  2. `MapPoints` 三个接口 URL 都带 `&tk=" + MapKeys.TDT`，经 `LcHttp.bytes()` 发出；`bytes()` 失败时把**完整 URL（含 tk）**写进 logcat（`LcHttp.java:54`）。`SuperMapActivity` 的 `httpBytes` 不打印，但同一 key 已在 `tileUrl` 里。
- **触发条件**：任一 `api.tianditu.gov.cn` 调用失败（弱网/403/配额）→ logcat 出现 `GET bytes fail https://api.tianditu.gov.cn/...&tk=dd188…`；任何拿到 logcat 的通道（调试广播、日志收集）都能取走 key。
- **预期 vs 实际**：预期 key 只在构建注入、日志不含密钥；实际 key 明文两处 + 失败日志明文带出。
- **建议**：key 由构建注入（`BuildConfig`/asset 模板），`map.html` 与 Java 共用一份；`LcHttp.bytes()` 打印前脱敏（去掉 `tk` 或只打 host+path）；`MapKeys` 注释里的「上线前应换」变成发布门禁项。

### MD-02 显示 Esri 历史影像时，`#srcInfo`/`getAttribution()`/HUD 仍标「天地图地形」

- **级别**：高｜**证据等级**：已证实
- **位置**：`map.html:1040`、`map.html:1373-1397`、`map.html:2454`、`map.html:1103-1117`；对照 `HistoryImageActivity.java:266`、`strings_history.xml:5-7`
- **代码原文**：
```js
  anno = (b === "img") ? "cia" : (b === "ter" ? "cta" : (b === "wayback" ? "" : "cva"));
```
```js
function updateSrcInfo() {
  var i = sourceOfBase(base);
  if (i >= 0) srcIndex = i;
  var s = MAP_SOURCES[srcIndex] || { name: "天地图地形", review: "未确认" };
  var el = document.getElementById("srcInfo");
  if (el) el.textContent = attributionText(s);
}
```
```js
    "垂直放大 " + VEXAG + "× · 贴面 " + (drape === "img" ? "影像" : (drape === "vec" ? "矢量" : "地形")) +
```
- **问题**：`base="wayback"` 不在 `MAP_SOURCES`（`:1328-1341`），`sourceOfBase("wayback")` 返回 `-1` → `updateSrcInfo` 不改 `srcIndex`，继续显示第 7 项「天地图地形」；`getAttribution()` 同错；HUD 的贴面三元只认 img/vec，`drape="wayback"` 落到「地形」。实际画面是 Esri/Maxar 影像。
- **触发条件**：历史影像页点「更早/较新」→ `HistoryImageActivity:266` 发 `setWayback(true,'<release>')` → 页面 `setBase("wayback")`。
- **预期 vs 实际**：预期角注/署名显示「Esri Wayback · © Esri / Maxar / Earthstar Geographics」（`strings_history.xml:6` 已备好文案）；实际右下角与 `getState().attribution.text` 都是「数据来自天地图地形…」。客户端截图角注读的是 `#srcInfo.textContent`，**存下来的图也会写错来源**。
- **补充**：`strings_history.xml:6` 的 `lc_history_attribution` 全 Java 无引用（死文案）；页面里只有 `lc_history_note` 带 Esri 名字，地图页角注没有。
- **建议**：`MAP_SOURCES` 增 `{name:"Esri 历史影像", layer:"wayback", review:"不适用"}`，或 `updateSrcInfo/getAttribution` 按 `wbOn` 分支返回 Esri 文案；HUD 贴面加「历史影像(Esri)」；`lc_history_attribution` 接到页面上或删掉。

---

## 3 正确性

### MD-03 瓦片缓存把带 alpha 的 PNG 注记重编码为 JPEG，命中缓存后注记变不透明块

- **级别**：中｜**证据等级**：已证实（实测瓦片带 alpha）
- **位置**：`map.html:247-259`（缓存写入）、`map.html:229-238`（缓存读取）、`map.html:119`（上限）
- **代码原文**：
```js
function __tileLoaded(img, url) {
  if (!tileCacheOn) return;
  try {
    var cv = document.createElement("canvas");
    cv.width = 256; cv.height = 256;
    cv.getContext("2d").drawImage(img, 0, 0);
    var d = cv.toDataURL("image/jpeg", 0.85);
```
```js
      var cached = tileCacheOn ? tileCache[url] : null;
      if (cached) {
        html += '<img class="tile" style="left:' + left + 'px;top:' + top + 'px;width:256px;height:256px" ' +
          'src="' + cached + '">';
```
- **实测**：天地图注记层返回 `image/png` 且带 `tRNS`：`cta` z10/843/388 有 **49,503 / 65,536** 个 alpha=0 像素；`cia` 同位置 **49,768** 个。base 层（`ter/img/vec`）是无 alpha 的图片。
- **问题**：`fillLayer` 对 base 与注记两层都挂 `__tileLoaded`；注记瓦片被 `drawImage` 到 canvas 后用 **JPEG** 编码，alpha 被丢弃（透明→黑），命中缓存时 `<img src>` 直接显示这张不透明图。注记层压在底图上，表现为整块黑/白底。
- **触发条件**：`enableTileCache(true)`（`SuperMapActivity.java:122` 已经在用）→ 平移/缩放触发第二次 `render()` → 缓存命中。
- **预期 vs 实际**：预期注记透明叠加；实际缓存命中后注记层变不透明色块。
- **建议**：注记层缓存用 `toDataURL("image/png")`（体积可接受，只有注记层）；或干脆缓存原始 URL 的 dataURL 不重编码；或 `tileCache` 只对 base 层生效。

### MD-04 2D 旋转/坐向模式下，测量/画笔/海拔打点未按 bearing 反变换

- **级别**：中｜**证据等级**：已证实
- **位置**：`map.html:1544-1559`（打点）、`map.html:477-495`（画笔）、`map.html:1279`（`applyTransform` 给 `#map` 加 `rotateZ(bearing)`）
- **代码原文**：
```js
function placeMarker(x, y) {
  if (paint.on) return;                       /* F-13：绘制中不吃点击 */
  var rect = mapEl.getBoundingClientRect();
  if (measure.on) {                           /* F-12：测量打点 */
    var ll = latLngOfScreen(x - rect.left, y - rect.top);
```
```js
function paintStart(cx, cy) {
  if (!paint.on) return;
  var rect = mapEl.getBoundingClientRect();
  var ll = latLngOfScreen(cx - rect.left, cy - rect.top);
```
- **问题**：2D 下 `#map` 被 `rotateZ(bearing)` 旋转后，`getBoundingClientRect()` 是**轴对齐包围盒**，`(x-rect.left, y-rect.top)` 既不是地图局部坐标、也没做 `-bearing` 反旋转。基线 `C-22/C-23` 已判旧版立向点/拖拽有同类问题；新增的测量、画笔、海拔打点把影响面扩大到 4 个功能。
- **触发条件**：2D 下点「旋转」或进「坐南朝北」使 `bearing≠0`，再测量/画笔/海拔点选。
- **预期 vs 实际**：预期落点跟手指下的地物一致；实际随 `bearing` 偏移（90° 时上下拖变东西移）。
- **建议**：抽一个 `screenToMap(x,y)`：用 `#map` 中心 + `-bearing` 反旋转，再换算瓦片坐标；测量/画笔/海拔/立向点统一走它（与基线 C-22/C-23 一并修）。

### MD-05 轨迹回放 follow 在 2D 每帧调 `render()`，全量重建瓦片层

- **级别**：中｜**证据等级**：已证实
- **位置**：`map.html:934-937`（`trackAnim`）、`map.html:883-886`（`lc_track_progress`）、`map.html:200-217`（`render`）
- **代码原文**：
```js
  if (track.follow && p) {
    center = { lat: p.lat, lng: p.lng };
    if (mode === "3d") ensurePatch(); else render();
  }
```
```js
  fillLayer(baseLayer, base, x0, x1, y0, y1, tx, ty, n);
  fillLayer(annoLayer, anno, x0, x1, y0, y1, tx, ty, n);
```
- **问题**：`trackAnim` 每帧（60fps）在 2D 下调 `render()`，而 `render()` 用 `innerHTML` 全量重建约 30 个 `<img>`（≈1800 节点/s），随后又调 `updateTrack()/renderOverlays()`；`trackAnim` 自己末尾还会再调一次 `updateTrack()`。长轨迹/弱机必然掉帧、瓦片解码风暴。
- **触发条件**：轨迹回放 `follow=true`（默认）+ 2D 模式 + `lc_track_play`。
- **预期 vs 实际**：预期跟随时只平移相机；实际每帧重建整个瓦片层。
- **建议**：follow 改为「节流（如 200ms）改 center 后单次 render」，或对瓦片层用 CSS `translate` 复用已有节点；3D 下 `ensurePatch()` 本身有早退，问题只在 2D。

### MD-06 3D 每帧全量重建叠加/轨迹/测量 SVG（O(N) DOM 重建，静止也不停）

- **级别**：中｜**证据等级**：已证实
- **位置**：`map.html:2432-2437`（`render3D` 末尾）、`map.html:967-1024`（`updateTrack`）、`map.html:399-451`（`renderOverlays`）
- **代码原文**：
```js
  updatePickMarker(vp);
  updateOverlay(vp);
  updateFish(vp);
  updateOverlayLayers();
  updateTrack();
  renderOverlays();
```
```js
function updateTrack() {
  var svg = ensureTrackSvg();
  var w = mapEl.clientWidth, h = mapEl.clientHeight;
  svg.setAttribute("width", w);
  svg.setAttribute("height", h);
  while (svg.firstChild) svg.removeChild(svg.firstChild);
```
- **问题**：`render3D` 每帧（rAF）都调用 6 个更新函数；`updateTrack`/`renderOverlays` 每次都 `removeChild` 清空再逐点重建 SVG。轨迹 N 点时，每秒 60×N 次投影 + 节点创建；相机静止时也照做。叠加点/钓点同理（`updateOverlayLayers` 每帧遍历所有点写 style）。
- **触发条件**：3D 模式 + 任意轨迹/测量/画笔/叠加层存在。
- **预期 vs 实际**：预期只在相机/数据变化时更新；实际每帧全量重建。
- **建议**：加脏标记（`lastVP` 变化或数据变更才更新）；轨迹按段缓存、只重绘「已播放」变化段；静止帧跳过全部更新。

### MD-07 海拔查询只读 DEM 缓存；纯 2D 下 `elevAt` 恒 `null`，契约未声明该前置

- **级别**：中｜**证据等级**：已证实
- **位置**：`map.html:1223-1234`（`elevAt`）、`map.html:1648-1690`（`demTile` 只被 `startPatch` 调用）、`map.html:1218-1219`（`lonLatToWorld` 用 0 顶替 null）
- **代码原文**：
```js
function elevAt(lat, lng) {
  var z = patch ? patch.z : Math.max(4, Math.min(12, Math.round(zoom) - 1));
  var t = lonLatToTile(lng, lat, z);
  var gx = t.x * 256, gy = t.y * 256;
  var x0 = Math.floor(gx), y0 = Math.floor(gy);
  var fx = gx - x0, fy = gy - y0;
  var a = demPix(z, x0, y0), b = demPix(z, x0 + 1, y0);
```
```js
  var e = elevAt(lat, lng);
  return { x: x, y: (e === null ? 0 : e) * VEXAG, z: z };
```
- **问题**：`demPix` 只查已加载的 `DEM` 缓存；DEM 瓦片只由 3D 的 `startPatch` 拉取。纯 2D 会话里 4 个邻点全 `null` → `elevAt` 返回 `null`（空值处理本身正确，`ElevationActivity:203-207` 也显示「海拔未知」），但**桥契约没写「海拔查询必须已进 3D/已加载 DEM」**；`lonLatToWorld` 又把 `null` 换成 0，3D 点位/标记会贴在海平面。
- **触发条件**：2D 地图页 `lc_elev_query(true)` 后点图；或 3D 点位落在未覆盖 DEM 处。
- **预期 vs 实际**：预期海拔查询在任何模式可用或明确拒绝；实际 2D 下恒 `null`，且没有任何「先切 3D」提示。
- **建议**：`map-bridge.md` 写明前置；`elevAt` 缺块时可按需 `demTile()` 拉取后重试一次；`lonLatToWorld` 对 `null` 高程改为隐藏该点或标「高程未知」，不要用 0。

---

## 4 健壮性 / 安全 / 契约

### MD-08 SuperMap：磁盘缓存无上限；任一瓦片失败整图作废；3072² 位图 + PNG100 体积

- **级别**：中｜**证据等级**：已证实
- **位置**：`SuperMapActivity.java:400-404`（缓存文件）、`:327-333`（失败即全废）、`:272`（位图尺寸）、`:339-341`（2× 放大）、`:355-357`（PNG 100）
- **代码原文**：
```java
    private File tileFile(int x, int y) {
        File d = new File(getCacheDir(), "tdt_img18");
        if (!d.exists()) d.mkdirs();
        return new File(d, x + "_" + y + ".jpg");
    }
```
```java
        if (failed > 0) {
            out.recycle();
            progressPanel.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            failView.setVisibility(View.VISIBLE);
            failView.setText(getString(R.string.lc_supermap_fail, failed));
            return;
        }
```
```java
        final Bitmap out = Bitmap.createBitmap(cols * 256, rows * 256, Bitmap.Config.ARGB_8888);
```
- **问题**：① `cacheDir/tdt_img18` 只写不删，无大小/数量上限；② 任一瓦片失败（`failed>0`）就把整张 6×6 结果丢弃，一次瞬时失败=白跑；③ 6×6=1536² ARGB（9.4MB），2× 双三次后 3072²（**37.7MB**）+ 中间位图，低端机 OOM 风险；④ 结果用 `PNG 100%` 落盘到 `externalFiles/supermap/`，3072² 单图可达数十 MB，且无清理。
- **触发条件**：反复生成/弱网/单瓦片 5xx；或 2× 放大在 2GB 设备。
- **建议**：缓存 LRU + 总量上限（如 200MB）；失败瓦片允许占位/部分成功并提示「缺 N 块」；3072² 用 `RGB_565` 或分块保存；输出改 JPEG 95/WebP；`supermap/` 目录加保留策略。

### MD-09 叠加/钓点/轨迹桥入参无条数上限，逐点建 DOM 且每帧遍历

- **级别**：中｜**证据等级**：已证实
- **位置**：`map.html:666-686`（`setOverlayLayer`）、`:1148-1178`（`setOverlay`）、`:578-615`（`setFishLayer`）、`:822-873`（`lc_track_load`）
- **代码原文**：
```js
  if (opts.items) {
    var list = opts.items, items = [];
    for (var i = 0; i < list.length; i++) {
      var p = list[i];
      if (!p || typeof p.lat !== "number" || typeof p.lng !== "number") continue;
      items.push({ name: String(p.name || ""), lat: p.lat, lng: p.lng });
    }
    layer.items = items;
  }
```
- **问题**：三处都只校验「是数字」，**没有条数上限**；每个点建 1–2 个 DOM 节点，之后每帧（3D）或每次平移（2D）遍历。`lc_track_load` 同样不限制段数/点数。桥入参可被调试注入通道（基线 `B-02`）放大成 DoS。
- **触发条件**：客户端/注入传入 1e4–1e5 点；或长轨迹。
- **预期 vs 实际**：预期有限流/抽稀；实际线性建 DOM + 每帧遍历。
- **建议**：上限（如点位 2000、轨迹 20000 点）+ 超限抽稀并 `emit` 提示；`track` 用空间索引/抽稀后再投影。

### MD-10 `LcHttp.UA` 用浏览器 UA 绕过天地图 WAF 403（注释自认），UA 写死机型

- **级别**：中｜**证据等级**：已证实（注释与代码同源）
- **位置**：`LcHttp.java:24-31`、`:42`；`SuperMapActivity.java:418`；`MapPoints.java` 全部请求
- **代码原文**：
```java
     * UA 必须是**浏览器形态**：2026-11-05 实测天地图 WAF 对
     * `Luopan/1.0 (Android)` / `okhttp/4.12` / `Dalvik/2.1.0` 一律返回 **403**，
     * 换成 `Mozilla/5.0 (...) Chrome/120 Mobile Safari/537.36` 才是 200。
     * 这不是参数问题——所有「某地搜不到、北京能搜到」的空结果都由此引起。
```
```java
    static final String UA =
            "Mozilla/5.0 (Linux; Android 12; LC) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0 Mobile Safari/537.36";
```
- **问题**：这是**主动规避服务端访问策略**（WAF 403），不是普通兼容；且 UA 里写死 `Android 12; LC`，WAF 规则一变就再次 403。`MapPoints`/`SuperMap`/Bing/天气全用它，故障面大。
- **触发条件**：天地图调整 WAF/UA 规则；或从「自用」走向分发。
- **风险定位**：合规（ToS）+ 健壮性（脆弱依赖），不是「鉴权绕过」——当前没有绕过任何账号鉴权，绕的是 UA 拦截。
- **建议**：用天地图允许的服务端调用方式/申请对应配额；UA 可配置并记录 403 计数；把「WAF 绕过」从产品化路径里移除，至少在文档里登记为风险。**可接受边界与替代方案见附录 A。**

### MD-11 Wayback release id 来自外部 config，未校验直接拼进 `evaluateJavascript` 与 `<img src>`

- **级别**：低｜**证据等级**：已证实
- **位置**：`HistoryImageActivity.java:266`、`map.html:176-178`、`:229-238`
- **代码原文**：
```java
        web.evaluateJavascript("setWayback(true,'" + r.id + "')", null);
```
```js
    if (!wbRelease) return "";
    return "https://wayback.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/WMTS/1.0.0"
      + "/default028mm/MapServer/tile/" + wbRelease + "/" + z + "/" + y + "/" + x;
```
- **问题**：`r.id` 来自 `waybackconfig.json`（外部数据），只要求 `itemTitle` 匹配日期，**未校验 id 字符集**就拼进 JS 字符串；`tileUrl` 再把它拼进 HTML 属性。正常 config 的 key 是纯数字（实测 198 个：`43291/63116/26334/…`），但这条路径没有防线。
- **触发条件**：config 被篡改/中间人（HTTPS 下概率低）；或将来换非数字 id。
- **建议**：`r.id.matches("\\d{1,8}")` 才使用；JS 端 `setWayback` 再验一次；URL 拼接用 `encodeURIComponent`。

### MD-12 Java `onEvent` 缺字段坐标取 0；JS 端只验 number 不验范围

- **级别**：低｜**证据等级**：已证实
- **位置**：`ElevationActivity.java:185-188`；`map.html:676`、`:1154`、`:588`
- **代码原文**：
```java
            double lat = o.optDouble("lat", 0);
            double lng = o.optDouble("lng", 0);
            boolean hasElev = o.has("elev") && !o.isNull("elev");
            double elev = hasElev ? o.optDouble("elev", 0) : 0;
```
- **问题**：事件缺 `lat/lng` 时静默落到 `(0,0)`（几内亚湾）而不是丢弃；JS 三处点位只判 `typeof === "number"`，`lat=100` 也接受。基线 `LCAndroid` 对 pick 有范围校验，新增事件没有。
- **触发条件**：桥被注入伪造事件（基线 `B-02` 通道仍在）；或上游解析异常。
- **建议**：Java 统一 `if (!isFinite || lat<-90||lat>90||lng<-180||lng>180) return;`；JS 同步校验范围；缺字段用 `null` 并显式拒绝。

### MD-13 `lc_track_progress` 0..1=比例、其余=绝对 ms，语义二义

- **级别**：低｜**证据等级**：已证实
- **位置**：`map.html:875-894`
- **代码原文**：
```js
  track.t = (v >= 0 && v <= 1)
    ? track.t0 + v * (track.t1 - track.t0)
    : Math.max(track.t0, Math.min(track.t1, v));
```
- **问题**：同一个参数既可能是 0–1 进度、也可能是绝对毫秒；当 `t0≈0` 的合成轨迹或调用方传 `0/1 ms` 时会被误判成比例。契约（`map-bridge.md`）又没有这条 API，调用方只能猜。
- **建议**：拆成 `lc_track_seekProgress(0..1)` 与 `lc_track_seekMs(ms)`；文档写清。

### MD-14 18 项新增 API/事件未写入 `docs/map-bridge.md`；`getState` 新字段只补了 `roadNet`

- **级别**：低｜**证据等级**：已证实
- **位置**：`docs/map-bridge.md:74`、`:83`、`:203`、`:252-268`（§10 K-01）；对照 `map.html:2481-2512`（`getState`）与全局函数
- **代码原文**（文档现状）：
```markdown
| `getState()` | 对象 `{mode, lat, lng, zoom, pitch, bearing, fps, elev, demZoom, hasTexture, sitMode, sitNorth, contour, ground, needle, base, drape, uiMode, attribution, source}` |
```
- **问题**：增量新增的 `setOverlay/setOverlayLayer/setOverlayOpacity/setContour`、`lc_measure_*`、`lc_draw_*`、`lc_track_*`、`setFishLayer`、`lc_elev_query/lc_queryElevation`、`setWayback*`、`setRidge`、`enableTileCache` 以及事件 `measure/measure-clear/paint/paint-undo/paint-clear/track-loaded/track-tick/track-clear/fish/fish-pick/overlay/overlay-pick/overlay-layer/elev/elev-query/ridge/roadnet/cache/wayback`，**文档一律没有**；`getState()` 实际已含 `roadNet/ridge/overlay/wayback/cache/measure/paint/fish/elevQuery/overlays/track` 等，文档只补到 `uiMode/attribution`（`roadNet` 在 §5.3 单条提到）。
- **触发条件**：客户端/AT 按 `map-bridge.md` 接入新功能 → 找不到接口与事件定义。
- **建议**：加「§11 增量功能 API 与事件」表（方法、参数、返回、事件 payload），`getState()` 字段表同步；由地图侧在该分支维护，变更时与实现同提交。
- **快照后进展（复核于 21:10）**：`origin/develop` 在 `bfb8492` 之后已推进到 `1c9239f`，其中 `map-bridge.md` 新增 **§11（F-12 测量，含 `lc_measure_addCenter`）**，本条对 F-12 已部分闭合；`lc_track_*/lc_draw_*/setFishLayer/setOverlay*/setWayback*/setRidge/enableTileCache` 与对应事件仍未写入。F-12 的 §11 表可作其余各节的模板。

---

## 5 验证记录（本轮实跑，可复现）

1. **无头契约自测**（合并版 `map.html` 从 `review-delta` 复制到 `%TEMP%` 跑，未改工作区）：
```
LC3D patch 11/1679/774 mpp=58.6 verts=9409
LC3D texture ok 36/36 img z12
LCBRIDGE selfTest PASS {"pass":true,"events":3,"failures":[],"picks":2,"markersAfterTwoPicks":1}
```
2. **注记瓦片 alpha 实测**：`cta`/`cia` 为带 `tRNS` 的 `image/png`，`alpha=0` 像素分别为 49,503 / 49,768（共 65,536）。
3. **Esri Wayback 可用性**：`waybackconfig.json` 198 个时相；取 key `43291` 拼 `.../tile/43291/11/388/843` → `200 image/jpeg`、`Access-Control-Allow-Origin: *`（说明 `crossorigin` 缓存路径本身可用，问题只在 MD-03 的重编码）。
4. **规模**：`map.html` 1605 → 2816 行（函数 87 → 160）；`git diff d752efa..bfb8492` 该文件 +1235/−24。

## 6 覆盖与未覆盖声明

**已读（本轮）**：
- `map.html` 新增面逐段读：`tileUrl`/`fillLayer`/`__tileLoaded`/`enableTileCache`（172-268）、测量与画笔（270-553）、钓点/海拔/叠加层（555-752）、轨迹（755-1024）、路网/山脊/Wayback/点标（1054-1234）、`elevAt`（1212-1234）、`getState`/`__LC3D`/`__LCBridgeTest`（2440-2570）、点击路由（1512-1625）、样式（1-96）。
- **F-15 地图侧已覆盖**：`lc_track_load`（`:822`）、`lc_track_progress`（`:875`）、`lc_track_play/pause/follow/clear`（`:896-924`）、`trackPosAt`（`:793`）、`trackAnim`（`:926`）、`updateTrack`（`:967`）、`trackPolyline`（`:952`）、`trackColor`（`:770`）逐行读过；对应条目 MD-05/06/09/13。该桥在 `bfb8492` 已存在（引入提交 `b58b2e6`，`4df602e` 是客户端接线合并、晚于本快照；两者 `map.html` 在本快照内无差异）。
- Java 全文：`HistoryImageActivity`（371 行）、`LcHttp`、`MapPoints`、`MapKeys`。
- Java 重点段：`SuperMapActivity`（KEY/瓦片/拼接/缓存/放大）、`ElevationActivity`（handle/JsSink/加载）、`MainActivity`（单页 WebView/`LCAndroid`/跳图/测量画笔入口）、`GeographicImageryActivity`（Bing 接口/署名渲染）、`OverlayActivity`（LAYERS/桥调用）、`MountainActivity`/`WaterActivity`/`FishingActivity`（MapPoints 调用/`setOverlay`/`setFishLayer`/坐标校验）。

**未逐行读（声明未审）**：
- `MountainActivity`/`WaterActivity`/`FishingActivity`/`OverlayActivity`/`GeographicImageryActivity` 的 UI 构建与列表交互后半（各文件约 150–250 行未逐行）；`SuperMapActivity` 的 UI 前段。
- 本模块之外的 29 个新增 Java（`DiscoveryActivity`、`SettingActivity`、`ToolsManagerActivity`、`GapDb`、天气/收藏点/盘面 store 等）未审——归 M2/M4。
- `res/layout`、`drawable`、`mipmap` 新增资源未做像素/一致性比对。
- 未跑 `gradle assembleDebug`、未装真机、未抓 logcat；未反编译 APK 复核资源。涉及「真机表现」的结论（MD-05/06 的帧率、MD-08 的 OOM）为代码路径推导，未量化。

**待确认（不进缺陷清单）**：
- MD-06 的实际帧率下降幅度、MD-08 的 OOM 触发阈值：需真机 `dumpsys gfxinfo` 与低内存设备实测。
- 全国版《服务条款》原文未取得（河南站 `usingHelp` 是 JS 应用，抓不到正文）；MD-10 的合规判断依据见附录 A。

---

## 附录 A — MD-10 评估：浏览器 UA 伪装的可接受边界

### A.1 实测（2026-11-05，22:1x，同一 key，本机 PowerShell）

| UA | 瓦片 WMTS（`t0/img_w`, z10/843/388） | 搜索 API（`api /v2/search`） |
|---|---|---|
| `Luopan/1.0 (Android)` | 403 | 403 |
| `okhttp/4.12` | 403 | 403 |
| `Dalvik/2.1.0` | 403 | 403 |
| `Mozilla/5.0 (Linux; Android 12; LC) … Chrome/120 Mobile Safari/537.36` | **200** | **200** |
| 无效 key（全 0）+ 浏览器 UA | **403 + JSON** `{"resolve":"请到API控制台重新申请Key","msg":"非法key","code":301001}` | — |
| 无 key + 浏览器 UA | **418** | — |

结论（实测支撑）：**UA 闸门在 key 校验之前、且与 key 是否有效无关**——同一个合法 key 换成非浏览器 UA 就是 403；无效 key 用浏览器 UA 反而能过 UA 闸门、拿到应用层「非法key」JSON。因此「换个人 key」不会消除 403，除非换**调用方式/凭据类型**。

### A.2 官方口径（外部资料）

- 福建省自然资源厅《天地图·福建④｜使用篇》（2026-10-09）明确：
  > 应用类型需要严格区分「浏览器端」和「服务端」，如果混用会导致请求失败。浏览器端用于前端网页展示，服务端用于后端接口调用（如 Java/Python 程序）。
  > 个人注册：申请应用许可无需审核，每个服务每天访问次数为 10000 次。
  来源：https://zrzyt.fujian.gov.cn/zwgk/xwdt/zrzyyw/202610/t20261009_7222216.htm
- 同类说明见「天地图山东」tk 使用说明（PDF）：https://www.sdmap.gov.cn/tdtsdservice/file/noLogin/developer_management_system_files/天地图山东开发资源及tk使用说明.pdf
- 说明：全国版《服务条款》正文未能抓取，以上为省级官方平台的使用口径；其「浏览器端/服务端严格区分」与天地图控制台的应用类型设置一致。

### A.3 结论：自用是否可接受

1. **性质**：这不是「鉴权绕过」（未绕过账号/密钥校验），而是**规避 WAF 的 UA 拒绝策略**，效果上把「服务端调用」伪装成「浏览器端」。这与 A.2 的官方口径**直接冲突**，不只是兼容写法。
2. **「自用、不商用、不上架」（D-001）降低但不消除本条**：D-001 约束的是分发与商业使用，不改变「调用方式是否符合平台规范」。
3. **个人 key 是否还需要伪装 UA**：**需要**（A.1 已证 UA 闸门独立于 key）。只有两种方式能去掉伪装：
   - 为 Java 原生 HTTP 申请**服务端 key**，按官方口径调用；WebView 地图继续用**浏览器端 key**（它本来就是浏览器，UA 真实，无需伪造）。
   - 若账号只能发浏览器端 key：把 API 调用改走 **WebView 内 `fetch`**（真实 WebView UA），结果经 `LCAndroid.onEvent` 回传；SuperMap 的瓦片批量下载同理。
4. **可接受边界（当前自用调试期）**：仅在「已验证非浏览器 UA 会被错误 403」的端点上启用；UA 必须可配置、可一键关闭；不得作为产品化/分发依赖，不得写入验收判据；发布前按 D-006 同款登记为已知风险。
5. **落地动作**：MD-01 换 key 时一并申请服务端 key；`LcHttp.UA` 抽成可配置常量，默认真实客户端标识 + 失败 403 计数（不刷屏）；长期把「UA 伪装」从代码路径移除。

### A.4 残余风险

- WAF 规则随时可能变（UA 名单、TLS 指纹、行为频率），伪装是**持续对抗**，不是一次性修复。
- 平台对使用规范有解释权；叠加 MD-01（原版第三方 key）后，风险不是相加而是相乘。
- 若未来转为分发/商用，本条从「可接受」升级为「必须移除」。

