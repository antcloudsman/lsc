# M6 Stage 5：客户端侧审计复核（LC审核验）

- 复核对象：`review/m6-audit-client` = `bc9181e` 的 `docs/fidelity/m6/audit-client-code.md`
- 复核基线：`origin/develop` = `ac2fee6`（与 `622cf28` 的 MainActivity 同为 4223 行，行号无漂移）
- 方法：`git show` 取提交内文本逐行比对；死代码用 `git grep -c` 逐符号核调用点
- 口径：**真**＝原文逐字一致且结论可用代码推演；**误**＝引用不符/前提不成立；**证据不足**＝缺必要证据

## 1. 逐条判定

| 编号 | 判定 | 复核依据（develop `ac2fee6` 原文） |
|---|---|---|
| P1-1 重载不重放底图源/坐南朝北 | **真** | 重放块 `3758-3770` 实测只有 `applyMapUiMode` / `applyPendingMapView` / `setRoadNet`(`:3764`) / `setMode('3d')`(`:3767`) / `refreshPlaceName`；点击侧 `:1550` `js("setBase('vec')")`、`:1551` `js("setBase('img')")`、`:1552` `js("toggleSitNorth()")` 均为**只在点击时下发**，重放清单无对应项。 |
| P1-2 搜索用上一次的地图中心 | **真** | `:3468` `final double flat = mapCenterLat, flng = mapCenterLng;` 先取值；`:3473` 才 `refreshMapCenterCache();`，而该方法 `:3292-3302` 用 `evaluateJavascript(..., callback)` **异步**写字段 → 本次必用旧值。初值 `:3267` `mapCenterLat = 39.9042, mapCenterLng = 116.4074;`；另一处写入 `updateCoordText()` 在 `:1714-1715`，调用点仅 `:1785`（`if (compassMode)`）与 `:1804`。 |
| D1 `clean()` 死代码 | **真（说明有小误）** | `MainActivity:4097` `private static String clean(String s) {`，`git grep -c "clean"` 在 MainActivity = **1**（只有定义）。**更正**：`CompassView:412` 不是 `clean()` 方法，而是内联 `bagua.labels[i].replace("\u200c", "")`；真正的第二份方法在 `CompassReadSheet:221-223`。所以"三份重复"应表述为"**两个方法副本 + 一处内联等价实现**"。 |
| D2 `wgs2gcj()` 死代码 | **真** | `:3606` `private static double[] wgs2gcj(...)`，MainActivity 内出现 **1** 次。 |
| D3 `gcj2bd()` 死代码 | **真** | `:3637` `private static double[] gcj2bd(...)`，出现 **1** 次。 |
| D4 `knowledgeEntries()` 死代码 | **真** | `:3235` `private java.util.List<String[]> knowledgeEntries() {`，出现 **1** 次。 |
| D5 `shareApp()` 死代码 | **真** | `:1940` `private void shareApp() {`，出现 **1** 次。 |
| D6 `setCompassHidden()` 死代码 | **真** | `:1763` `private void setCompassHidden(boolean hidden) {`，出现 **1** 次。 |
| L3 指针两份状态 | **真（隐患）** | `:873` `private boolean pointerOn = true;`、`:902` `case "指针开关": return pointerOn;`（图形）、`:1557` `compassView.setShowPointer(on);`（可见性）、`CompassView.java:45` `private boolean showPointer = true;`。审计自述"当前无已复现漂移"，与代码一致。 |
| L4 `mapMarker`/`poiMarker` 两份 | **真（隐患）** | `:65` `private MapMarker poiMarker;`；`:3742` `mapMarker = null;`（只清一个）；`:2688-2689` 两行都清。 |
| I1 可点区 158×158 vs 视觉 107×54 | **证据不足** | `:821` `private static final int COL_BTN_H = 158;`、`:971` `b.setClickable(true);`（整块容器）、`:976` `new LinearLayout.LayoutParams(107, 54)`（图形）。结构描述属实，但**缺原版右列开关节点 bounds**，无法判是否 1:1；总监已裁定记「未取证」，与本判定一致。 |
| I2 `我的` 只 toast | **不判（按口径实现）** | 审计自述符合 D-034 裁定，不属缺陷。 |
| A1 山脉兜底写死北京坐标 | **真** | `:2525` `queryMountainNamesForLayer(39.9042, 116.4074);`、`:2531` `double lat = 39.9042, lng = 116.4074;`。 |
| A2/A3 图标占位 | **真（有意占位）** | `:1080` `icon = new View(this); // 占位，不画假图标`、`:1137` 注释"留占位等形态口径"。 |
| A4 25 处「本步未实现」不可达 | **真（计数应为 26）** | `Select-String '本步未实现'` 命中 **26** 处；`FeatureFlags.java:37` `return p.getBoolean("enabled_" + id, true);` → 默认 **true**，stub 默认不走，结论成立。 |

## 2. 对审计文本的更正（不影响条目成立）

1. **开头「MainActivity（3961 行）」**：实测 **4223 行**（`622cf28` 与 `ac2fee6` 均为 4223）。文档数字错，但所有引用行逐字命中，条目不偏。
2. **D1 的「三份」**：见上，`CompassView:412` 是内联实现不是方法。
3. **A4 的「25 处」**：实测 **26** 处。
4. **P1-1 的触发例举**「`location.reload()`」：`map.html` 内 `.reload(` 命中 **0**；真实重载路径是 `MainActivity:372` 桥接 `case "url"` → `loadUrl(url)`，以及 WebView 渲染进程重启。**结论不变**，仅触发例举要改。

## 3. 与地图侧审计的合并（同一根因）

`P1-1`（底图源/坐南朝北）、地图侧 `M-01`（等高线/贴地）、`M-02`（指针）**是同一根因的三个表现**：`onPageFinished` 的 replay 块只补了 `setRoadNet` 与 `setMode('3d')`，其余开关状态只在点击时下发，且 `setBase` / `sitNorth` / `nestle` 系无幂等 set。合并计为**一条根因 + 五个表现面**（底图源、坐南朝北、等高线、贴地、指针），关账表按一条记，避免同一现象记五条。

## 4. 可直接进缺陷清单的子集（客户端侧）

**A 类（功能可观测）**

| 编号 | 一句话缺陷 | 预期 vs 实际 |
|---|---|---|
| P1-1 | 重载后「实景/坐南朝北」开关仍是开，画面回默认 | 预期保持用户选择 → 实际回矢量底图、南北向失效 |
| P1-2 | 拖动地图后第一次搜索按旧中心排序 | 预期按当前视野 → 实际按上一次缓存（冷启时是写死北京） |

**B 类（健壮/隐患，进清单但低优先）**

| 编号 | 一句话缺陷 |
|---|---|
| L3 | 指针开关两份状态（图形 `pointerOn` / 可见性 `compassView.showPointer`），当前同步、无漂移 |
| L4 | `mapMarker` 与 `poiMarker` 两份选中点，`:3742` 只清其一（当前用户不可达） |
| A1 | 山脉图层兜底按写死北京坐标查山名且无提示，用户可能把北京山名当当前视野 |

**C 类：不进缺陷清单**

- D1–D6（死代码）→ 清理单/技术债，不记缺陷；
- A2/A3（有意占位）→ 进验收表「已知占位」，不记缺陷；
- A4（26 处 stub 分支）→ 登记为占位分支，默认不可达；
- I1 → 证据不足（缺原版 bounds），按「未取证」；
- I2 → 按 D-034 口径实现，不判。

## 5. 未覆盖

- 未上机复现 P1-1/P1-2（设备本轮串行占用）；两条代码路径足以定「真」，但"用户实际撞到概率"未量化。
- 未复核 `lc_*` 资源与布局 XML 全量（审计声明范围为相关代码，非全资源）。
