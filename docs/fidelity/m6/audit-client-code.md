# M6 Stage 5 · 客户端代码审计（占位 / 死代码 / 逻辑矛盾 / 交互）

- 审计人：LC审客户端
- 基线：`develop` **`622cf28`**（`git rev-parse origin/develop` 实测）
- 范围：`MainActivity`（**4223 行**）、`M3PageActivity`、`M3WebActivity`、`CompassReadSheet`、`CompassCrosshairView`，以及与 `lc_*` 资源相关的布局/开关代码
- 方法：只读源码 + `git grep` 计数核对调用点；每条给出「文件:行号 + 原文 + 为什么是问题 + 影响面」；**未上机、未改一行**
- 结论：**P1 两条、P2 四条、登记一条、待确认一条**；其中 **L2 是现网可复现的状态不同步**，其余为维护性/占位类

---

## 1. P1（功能影响）

### P1-1 WebView 重载后不重放「底图源」与「坐南朝北」，开关却仍显示开

- **位置**：`MainActivity.java:3758-3770`（重放）vs `:1550-1552`（状态动作）
- **原文**（重放块）：
```
3761: applyMapUiMode(compassMode ? "compass" : "map");
3762: applyPendingMapView();
3764: view.evaluateJavascript("try{setRoadNet(" + roadNetOn + ")}catch(e){}", null);
3767: if (is3D) view.evaluateJavascript("try{setMode('3d')}catch(e){}", null);
```
- **对照**（只在点击时下发、重载后没有任何重放）：
```
1550: case "地图": js("setBase('vec')"); break;
1551: case "实景": js("setBase('img')"); break;
1552: case "坐南朝北": js("toggleSitNorth()"); break;
```
- **为什么是问题**：`map.html` 的 `mode` 刚被 `is3D` 重放修过（M6 第 8 条），但同一类状态的 `setBase`、`toggleSitNorth` **没有进入重放清单**。触发路径是**桥接 `url` 重载**（`MainActivity` 的 `case "url": loadUrl(url)`）或 **WebView 渲染进程重启**（`map.html` 内 `reload(` **0 命中**，`:3752` 那句只是注释，不是现存调用）；重载后 JS 侧底图与坐向回到默认，而 Java 侧 `compassMapMode` / `southNorthOn` 仍是开——**开关图形与画面相反**。
- **影响面**：罗盘态切「实景」后旋转屏幕/重载 → 画面回到矢量底图但「实景」仍蓝；「坐南朝北」同理失效。用户会认为开关失灵，且与 R2-032 角注条（`syncAttributionBar()` 从 JS 读源）一起出现「角注说地图、开关说实景」的双重矛盾。
- **修法**：照 `is3D` 的做法，在 `:3765` 之后补 `setBase(compassMapMode == MAP_MODE_REALITY ? 'img' : 'vec')` 与 `if (southNorthOn) toggleSitNorth()`（或改成幂等 `setSitNorth(bool)`，避免 toggle 语义在重放下翻转）。

### P1-2 搜索用的地图中心是「上一次」的缓存

- **位置**：`MainActivity.java:3468` 与 `:3473`；默认值 `:3267`
- **原文**：
```
3464: // 中心点走 Java 侧缓存（refreshMapCenterCache 维护），不把重跑门控在一次异步 JS 往返上：
3468: final double flat = mapCenterLat, flng = mapCenterLng;
3469: rows.add("检索中…");
...
3473: refreshMapCenterCache();
```
- **为什么是问题**：`refreshMapCenterCache()` 是异步的（`:3292-3303` 用 `evaluateJavascript` 回调写字段），而 `flat/flng` 在它**之前**就被取值。所以这次检索用的必然是**上一次**缓存；刷新结果只对下一次检索生效。注释说「中心点走缓存维护」，代码却把「取值」放在「刷新」之前。
- **另加**：`mapCenterLat/mapCenterLng` 初值是写死的北京 `39.9042 / 116.4074`（`:3267`），而 `updateCoordText()`（唯一另一处写入，`:1713-1715`）只在罗盘态/收藏态调用（`:1785`、`:1804`）。纯地图态下缓存不会随拖动更新。
- **影响面**：用户把地图拖到上海后**第一次**搜索「故宫」，仍按上一个中心（甚至冷启默认北京）排结果；第二次才正确。属可感知的功能偏差，且难以复现归因。
- **修法**：检索前先 `await` 一次 `getState()`（或把 `refreshMapCenterCache` 改成带回调、回调里再发请求）；纯地图态增加移动监听（`map.html` 的 moveend 事件回传）更新缓存。

---

## 2. 死代码（6 条，全部 `occurrence=1`，即只有定义无调用）

用 `git grep -c` 逐条核过，不是猜的：

| # | 位置 | 原文 | 说明 / 影响 |
|---|---|---|---|
| D1 | `MainActivity.java:4097` | `private static String clean(String s) { return s == null ? "" : s.replace("\u200c", ""); }` | 读数改单行后不再使用；同名**方法**还有一份在 `CompassReadSheet:221-223`（共**两份**）；`CompassView:412` 是**内联** `.replace("\u200c","")`，不是方法 |
| D2 | `MainActivity.java:3606` | `private static double[] wgs2gcj(double lat, double lng)` | 与 `CoordConv.wgs84ToGcj02` 重复；两套转换并存，改一处另一处不会跟着变 |
| D3 | `MainActivity.java:3637` | `private static double[] gcj2bd(double lat, double lng)` | 同上，BD09 转换重复实现 |
| D4 | `MainActivity.java:3235` | `private java.util.List<String[]> knowledgeEntries()` | 知识弹层改走 asset HTML 后残留（`:3647` 注释已写「不再用我们自己写的…」） |
| D5 | `MainActivity.java:1940` | `private void shareApp()` | 抽屉「推荐给好友」已改走 `M3PageActivity` 页内分享（`:2040-2045`），此方法无人调用；另外 `:3993` 还有第三份 `ACTION_SEND` 实现 |
| D6 | `MainActivity.java:1763` | `private void setCompassHidden(boolean hidden)` | 与 `toggleCompassHidden()`（`:1754`）功能重叠，且**无人调用**；将来若有人用它，会和 `hideButton` 文案/图形两套逻辑打架 |

- **影响面**：不影响现网行为；但 D2/D3 是**两套坐标转换**、D1 是**两份方法 + 一处内联** ZWNJ 清理、D5 是**三份分享**——下一轮改口径时最容易只改一处。
- **修法**：删除 6 个方法；D2/D3 若确需保留，改为调用 `CoordConv` 的单实现。

---

## 3. 逻辑矛盾（状态存两处）

### L3 `指针开关` 有两份状态，图形读一份、可见性读另一份

- **位置**：`MainActivity.java:873`（`private boolean pointerOn = true;`）、`:902`（`case "指针开关": return pointerOn;`）、`:920`（`pointerOn = !pointerOn;`）vs `CompassView.java:45`（`private boolean showPointer = true;`）、`MainActivity.java:1557`（`compassView.setShowPointer(on)`）
- **为什么是问题**：开关**图形**由 `pointerOn` 决定，指针**实际可见性**由 `compassView.isShowPointer()` 决定；两者只在「点击开关」这一条路径里同时翻转（`:1553-1557`）。任何只改一份的路径（复位、持久化、后续重构）都会让「开关显示开、指针不显示」。
- **现状**：`CompassView.reset()` 不碰 `showPointer`，所以**当前没有已复现的漂移**——按纪律记为「逻辑隐患」，不写成现网 bug。
- **修法**：删 `pointerOn`，`switchStateOf("指针开关")` 直接返回 `compassView.isShowPointer()`，单一权威。

### L4 `mapMarker` 与 `poiMarker` 两份选中点

- **位置**：`MainActivity.java:65`（`poiMarker`）、`:3736/3742`（`mapMarker` 赋值/清空）、`:2688-2689`（清除按钮同时清两个）
- **原文**：
```
3742: mapMarker = null;          // JS 侧 marker 消失时只清 mapMarker
2688: mapMarker = null;          // 面板「清除标记」两个都清
2689: poiMarker = null;
```
- **为什么是问题**：`:3742` 只清 `mapMarker`，`poiMarker` 保留（面板已隐藏，`copyPoiLatLng` 等仍以 `poiMarker` 为数据源）。当前入口都要求面板可见，所以**用户不可达**；但两字段语义重叠，属「同一状态存两处」。
- **修法**：合并为一个 `selectedMarker`，清空时统一走一个方法。

---

## 4. 交互问题

### I1 开关按钮可点区 158×158，视觉只有 107×54（待确认）

- **位置**：`MainActivity.java:821`（`COL_BTN_H = 158`）、`:976`（`new LinearLayout.LayoutParams(107, 54)`）
- **原文**：
```
821: private static final int COL_BTN_H = 158;
976: LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(107, 54);
```
- **为什么是问题**：整块 158×158 都 `setClickable(true)`（`:971`），而用户看到的只有 107×54 药丸 + 小字；点空白区也会切换，属「可点区大于视觉」。
- **状态：待确认**——原版该控件的 clickable bounds 尚未取证（只有截图），若原版同为 158×158，则这是 1:1，不是缺陷。**缺的证据**：原版右列开关节点的 dump bounds。

### I2 `我的` 可点但只给 toast（行为已按裁定实现，不判缺陷）

- **位置**：`M3PageActivity.java:191-194`
```
191: mineBtn.setClickable(true);
192: mineBtn.setOnClickListener(v -> android.widget.Toast.makeText(this,
193:         "「我的」跳转目标未取证（D-034），本轮未接入",
```
- 符合「可点 + 明确未接入 + D 编号」裁定，登记为**已按口径实现**，不进缺陷清单。

---

## 5. 占位实现

| # | 位置 | 原文 | 性质 / 影响 |
|---|---|---|---|
| A1 | `MainActivity.java:2525`、`:2531` | `queryMountainNamesForLayer(39.9042, 116.4074);` / `double lat = 39.9042, lng = 116.4074;` | **写死坐标兜底**：WebView 为空或 `getState()` 解析失败时，山脉面板仍会按北京查山名。屏幕上没有「这是默认中心」的提示 → 用户可能把北京山名当成当前视野 |
| A2 | `MainActivity.java:1080` | `icon = new View(this);   // 占位，不画假图标` | 右列 10 控件中缺图标的项渲染为空白块；是**有意占位**（不造假），但用户看到的是空位，需在验收表里明确登记 |
| A3 | `MainActivity.java:1137` | `// 渲染出来是人形/立方体箭头/左右箭头，与原版开关图形不符，已撤掉，留占位等形态口径。` | 同上，开关图形占位 |
| A4 | `MainActivity.java` 共 **26 处** `toast(label + "：原版为工具子页，本步未实现")`（`:1452`…`:3142`） | 例：`:1452` | **默认不可达**：`FeatureFlags.java:37` `isEnabled` 默认 `true`，只有手动关掉 flag 才走 stub。属调试回退分支，**不判用户可见缺陷**，但 26 条字符串应登记为「占位分支」而非「已实现」 |

---

## 6. 已核无问题（覆盖说明，不是发现）

- `M3WebActivity`：加载中/失败/重试三态齐全（`:62-130`），失败不被 `onPageFinished` 清掉（`:114`）；`M3WebActivity`、`M3PageActivity` 在 manifest 均 `exported="false"`（`AndroidManifest.xml:40/47`），URL 来自 Intent 但不可被外部唤起。
- `CompassCrosshairView`：\(:45-46\) `setClickable(false)`/`setFocusable(false)`，不覆写 `onTouchEvent` → 触摸穿透，符合注释声明。
- `CompassReadSheet`：14 行由 `CompassData.RINGS` 驱动（`:55`），无写死读数；ZWNJ 清理自带一份。

---

## 7. 未覆盖 / 局限

1. 未上机：交互类（I1）只做静态比对，原版 clickable bounds 未取证，故标「待确认」。
2. 未审计 `map.html`（归 LC审地图）、`SettingActivity` 等本单范围外文件。
3. 死代码判定基于「同类名 occurrence=1」+ 人工确认，方法引用（`::name`）已一并排除；若存在反射调用则可能漏判（本仓无反射调用点，已 grep `getDeclaredMethod\|Class.forName` 0 命中）。
4. 未做内存/线程审计（传感器、WebView 生命周期在本单范围外）。

---

证据：`git show origin/develop:...MainActivity.java`（审计基线 `622cf28`，复核基线 `ac2fee6`；**4223 行**，两次取数一致，行号未漂移）；`FeatureFlags.java:37`；`AndroidManifest.xml:40/47`；`CompassView.java:45/83/88`；`CompassReadSheet.java:221-223`；`CoordConv.java:22/36`。未占设备、未改代码。

---

## 8. 复核更正记录（2026-11-08，按 LC审核验 复核 + 总监裁定）

| # | 原写法 | 更正后 | 我的复核命令 / 结果 |
|---|---|---|---|
| 1 | `MainActivity` **3961 行** | **4223 行** | `git show origin/develop:...MainActivity.java` 行数组 `Count=4223`（`Measure-Object -Line` 在管道输入下给出的 3961 不可靠，已弃用该口径） |
| 2 | `clean` **三份**（含 `CompassView:412`） | **两份方法**（`MainActivity:4097`、`CompassReadSheet:221`）＋`CompassView:412` 是**内联** `.replace` | `git grep -n 'String clean(String'` → 恰好 2 个方法定义 |
| 3 | `本步未实现` **25 处** | **26 处** | 同文件 `Select-String '本步未实现' \| Measure-Object` → Count=26 |
| 4 | 触发例举写 `location.reload()` | 改为「**桥接 `url` 重载 / 渲染进程重启**」；`map.html` 内 `reload(` **0 命中**，`:3752` 是注释 | `git show origin/develop:...map.html \| Select-String 'reload\('` → 0 |

- 四条都不改变任何条目的「真/证据不足」判定；引用行号逐字未漂移（复核在 `ac2fee6`、审计在 `622cf28`）。
- 关账口径我认同审核验的合并：**1 条根因（replay 块不全 + 缺幂等 set）→ 5 个表现面**，`P1-1` 只是其中之一，不与 `M-01/M-02` 重复计条。
