# M6 Stage 5：地图侧审计复核（LC审核验）

- 复核对象：`review/m6-audit-map` = `34d041e` 的 `docs/fidelity/m6/map-audit-stage5.md`（M-01…M-10）
- 复核基线：`origin/develop` = `ac2fee6`（审计声明基线 `622cf28`；因分支已 rebase 到 `ac2fee6`，逐条按当前 develop 复核行号，**10/10 行号与原文命中**）
- 方法：`git show origin/develop:<file>` 取提交内文本逐行比对 + 计数（不读工作区、不用记忆）
- 结论口径：**真**＝原文逐字一致且逻辑可用代码推演；**误**＝引用不符/前提不成立；**证据不足**＝缺实测或对照

## 1. 逐条判定

| 编号 | 判定 | 复核依据（develop `ac2fee6` 实测原文） |
|---|---|---|
| M-01 重载后 toggle 语义错位 | **真** | `MainActivity.java:1527` `case "等高线": js("toggleContour()"); break;`；`:1537` `js("toggleGround()");`；`:1964` `contour.setOnClickListener(v -> js("toggleContour()"));`；`map.html:199` `var contourOn = false;`、`:200` `var groundMode = false;`；`onPageFinished` 重放块 `3758-3770` 只有 `applyMapUiMode` / `applyPendingMapView` / `setRoadNet`(`:3764`) / `setMode('3d')`(`:3767`) / `refreshPlaceName`，**无 contour、无 ground**；`setContour(on)` 定义在 `map.html:1202`，Java 侧 `setContour` 出现 **0 次**（setter 存在但无人调用）。 |
| M-02 指针开关两份状态 | **真** | Java 侧 `:873` `private boolean pointerOn = true;`、`:902` `case "指针开关": return pointerOn;`（图标状态）、`:920` `pointerOn = !pointerOn;`；JS 侧 `map.html:193` `var needleOn = true;`、`:1716-1717` `function toggleNeedle() { needleOn = !needleOn;`；`:1549`/`:1562` 均为 `js("toggleNeedle()")`；`onPageFinished` 不重放 needle → 图标与地图各持一份。 |
| M-03 两套规范并存 | **真（不单列缺陷）** | `setRoadNet`(`1238`)/`setRidge`(`1263`)/`setContour`(`1202`) 有 set；`toggleContour`(`1746`)/`toggleGround`(`1752`)/`toggleNeedle`(`1716`) 只有翻转。属 M-01/M-02 的根因描述，**并入其修法**，避免同一现象重复上账。 |
| M-04 `js()` 空吞异常 | **真** | `MainActivity.java:1250` `private void js(String code) { if (compassMapView == null) return;`、`:1251` `compassMapView.evaluateJavascript("try{" + code + "}catch(e){}", null);`；调用点计数 `js("` = **32**（与审计一致）。 |
| M-05 `emit()` 静默 | **真** | `map.html:2513` `} catch (e) { /* 桥未接入时静默 */ }`。 |
| M-06 海拔失败与无数据同值 | **真** | `map.html:1442-1449`：`elevAt` 命中即 `cb(e)`；未命中走 `demPrefetchFor(...).then(cb(elevAt(...))).catch(function () { cb(null); })` → 覆盖缺失与拉取失败**同为 `null`**。 |
| M-07 DEM 单块失败原因丢弃 | **真** | `map.html:2243` `jobs.push(demTile(z, x, y).catch(function () { return null; }));`。 |
| M-08 3D 裸魔法数 | **真（维护性，非功能缺陷）** | `map.html:1976` `var GRID = 96;`、`:1975` `var PATCH_PX = PATCH_TILES * 256;`、`:1467` `if (zoom < 9) zoom = 9;`、`:2221` `var zdem = Math.max(4, Math.min(12, Math.round(zoom) - 1));`、`:2398` `if (ok >= 30) {`，`:2402/:2404` 里 `/36` 是分母 → 审计写的「30/36」读数正确。 |
| M-09 点选阈值 6 vs 8 | **真（低严重度）** | `map.html:1849` `if (dragging && totalMove < 6) placeMarker(lastX, lastY);`（鼠标路径）与 `:1962` `if (touchStart && touchStart.moved < 8) placeMarker(touchStart.x, touchStart.y);`（触摸路径）。两条路径语义相同、阈值不同，属一致性问题，**不影响真机触摸行为**。 |
| M-10 无捏合/双击缩放 | **真（与 §13 重复）** | `map.html` 内 `pinch|dblclick|doubleTap`（忽略大小写）命中 **0**；`:5` `maximum-scale=1, user-scalable=no`、`:10` `#map { … touch-action:none; }`。该项已由 Stage 4 写进正文 §13「双击缩放 = 未实现」，**不重复上账**。 |

## 2. 对审计原文的一处更正（不影响结论）

M-01 把触发条件写成「`location.reload()`、WebView 进程回收…」：
- `map.html` 内 `location.reload` / `.reload(` 命中 **0**，这条例子不成立；
- 但**真实重载路径存在**：`MainActivity.java:372` 桥接命令 `case "url": … compassMapView.loadUrl(url);`（K-01 单页同 WebView 可被再次 `loadUrl`），且代码自身在 `:3752` 注释「页面重载（location.reload()）会把 JS 侧 uiMode 复位成默认值」——**作者已认定重载路径存在**。
- 结论仍为**真**，仅把触发例举改为「桥接 `url` 命令重载 / WebView 渲染进程重启」。

## 3. 可直接进缺陷清单的子集（关账只认这个）

**A 类：功能可观测（用户能撞到）**

| 编号 | 一句话缺陷 | 触发 | 预期 vs 实际 |
|---|---|---|---|
| M-01 | 页面重载后点「等高线/贴地」得到相反结果 | 桥接 `url` 重载或 WebView 渲染进程重启后点左侧「等高线」/「贴地」 | 预期「关闭」→ 实际打开；再点一次也不能回到用户意图 |
| M-02 | 指针开关图标与地图实际状态错位 | 同上重载后 | 图标显示「关」，地图按 JS 默认 `needleOn=true` 绘制 |

**B 类：健壮性/可诊断（影响定位，不影响单次功能）**

| 编号 | 一句话缺陷 |
|---|---|
| M-04 | 32 处 `js()` 调用的所有 JS 异常被 `catch(e){}` 静默吞掉，桥接侧零日志 |
| M-05 | `emit()` 吞异常，事件序列化失败时原生永久收不到事件且无痕迹 |
| M-06 | `elevAtAsync` 把「无 DEM 覆盖」与「拉取失败」压成同一个 `null` |
| M-07 | DEM 单块失败原因被丢弃，只剩 `dem-missing`，无法区分 404/超时/DNS |

**C 类：不进缺陷清单（并入或已登记）**

- M-03 → 并入 M-01/M-02 的修法（同一现象，不重复计数）；
- M-08 → 技术债（维护性），建议记 `docs` 侧而非缺陷清单；
- M-09 → 低严重度一致性问题，可随 M-01/M-02 同批顺手统一；
- M-10 → 已在正文 §13「双击缩放 = 未实现」，不重复上账。

## 4. 未覆盖

- 本人未上机复现 M-01/M-02（设备按协议串行，本轮由客户端侧占用）；两条的**代码路径**已足以定「真」，但「用户实际撞到概率」未量化——若要写进关账表的“影响面”，需一次真机重载复现。
- 未复核 3D 投影/`pick` 数学（审计亦声明不在范围）。
