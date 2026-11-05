# K-01 客户端 ↔ map.html 调用契约（合流用）

> 作者：LC审客户端｜日期：2026-11-05｜适用：`k01/integration` 合流
> 事实源：`k01/integration` @ `b7aab72`（含 A10 修复）。行号随合流可能漂移，**以函数名为准**。
> 本文件按 2026-11-05 17:20 真机结果与 A10 修复更新（原 `fix/p0` 版本已过时）。

## 1 客户端实际调用点（只有这些）

| # | 调用 | 文件:行 | 触发时机 | 期望 |
|---|---|---|---|---|
| 1 | `setUiMode('compass')` | `MainActivity.java` `setCompassMode(true)` 内 | 点左列「罗盘」（`onToolClick` 的 `case "罗盘"`）或「更多→罗盘」后；**先切原生视图，再调 JS**，随后 `updateReading()` | `uiMode=compass`；地图自带 chrome 隐藏 |
| 2 | `setUiMode('map')` | 同函数 `on=false` 分支 | 点「隐藏」或返回键（`onBackPressed` 先退浮层） | `uiMode=map`；chrome 保持隐藏（见 A8） |
| 3 | `setUiMode(compassMode ? 'compass' : 'map')` | `MainActivity.java` `onPageFinished`（A10 修复 `b7aab72`） | **每次页面加载完成**（含 `location.reload()`） | 按**原生当前态**重放，不再恒设 map |
| 4 | `setHeading(float)` | `MainActivity.java` `updateReading()` | 每次读数刷新 | 指针/坐向 |
| 5 | `setSitFace(face,sit)` | 同上 | 每次读数刷新 | 坐向标签 |
| 6 | 读 `#srcInfo.textContent` | `MainActivity.java` `captureMap()` | 截图时 | 非空；写入 `lastAttribution`，由 `composeAttribution()` 画进截图 |

**客户端不调用 `getAttribution()`**（全 Java 目录 grep 0 命中）：角注走 DOM `#srcInfo`。`getAttribution()` 保留给地图自测与 `__LC3D.state().attribution`。

## 2 map.html 侧必须保留的行为

- `setUiMode(m)`：接受 `'map'|'compass'|'full'`；隐藏/显示 `#controls`、`#hint`、`#heading-label`、`#srcInfo`、`#fsExit`；`applyNeedleVisibility()`（compass 下 `#needle` 恒 `none`）；返回 `uiMode`。
- `getAttribution()`：返回 `{source, review, original, equivalent, text}`；`text` 形如「数据来自天地图地形（等价替代原版世纪空间）· 审图号：未确认」。
- `updateSrcInfo()`：底图切换时刷新 `#srcInfo`；**没有 `#srcInfo` 时，客户端截图会静默丢角注**。
- 其它被按钮调用的函数：`rotateBearing`、`zoomIn`、`openSources`、`toggleContour`、`toggleGround`、`locate`、`toggle3D`、`setHeading`、`setSitFace`（`js()` 的 try/catch 会吞掉缺失，表现为「点了没反应」）。

## 3 时序与未就绪处理

- **进入**：`setCompassMode(true)` → 原生视图先切 → `js("setUiMode('compass')")` → `updateReading()`。原生浮层**先于**地图模式生效。
- **退出**：`setCompassMode(false)` → 原生视图先隐藏 → `js("setUiMode('map')")`。
- **WebView 未就绪**：`js()` 只判 `compassMapView == null`，直接 `evaluateJavascript("try{...}catch(e){}")`，**无排队、无重试、无就绪标志**。冷启动未加载完时进入罗盘，`compass` 调用会落在 `about:blank` 上丢失；随后 `onPageFinished` 按原生态重放（A10 修复后）。
- **页面重载（A10）**：`onPageFinished` 从原生 `compassMode` 回灌 `setUiMode`，并立即 `updateReading()` 重推 heading/坐向。2026-11-05 17:20 实测「重载后 `uiMode` 被复位成 map」即此路径，已在 `b7aab72` 修复。

## 4 合流风险（已核实的事实）

1. `6af934a` / `d8bc53c` 的 `map.html` **没有** `setUiMode` / `getAttribution`（`git show` 验证：定义数 0）；`011ac7f` 与后续线有（定义数 2）。合流若采用旧线 map.html，客户端的 `setUiMode` 会被 try/catch 静默吞掉 → 罗盘态下地图 chrome 不隐藏。
2. 旧线若无 `#srcInfo`（或 `updateSrcInfo` 不刷新），截图角注为空 → `composeAttribution` 返回原图，`LCCAP` 无 `attribution drawn` 日志。
3. `setUiMode` 必须保留 `uiMode` 状态并在 `state()` 里返回（客户端/AT 用 `__LC3D.state().uiMode` 判定）。
4. 地图侧 `needle` 语义：compass 下 `#needle` 必须 none（北针由原生 `CompassView.setShowPointer` 负责），不得用 `state().needle` 代判罗盘北针。

## 5 合流后验收清单（客户端侧）

| # | 动作 | 通过判据 |
|---|---|---|
| A1 | 装 `k01/integration` debug，启动 | logcat `LCDEBUG bridge registered sdk=…` |
| A2 | `js "typeof setUiMode"` / `js "typeof getAttribution"` | 均为 `"function"` |
| A3 | `js "JSON.stringify(getAttribution())"` | 含 `source/review/original/equivalent/text`，`text` 非空 |
| A4 | `js "JSON.stringify(__LC3D.state())"` | `uiMode`、`attribution` 字段存在 |
| A5 | 点「罗盘」 | 浮层出现；`uiMode=="compass"`；`getComputedStyle(#controls).display=="none"` |
| A6 | 圆外**垂直**拖动 `input swipe 614 830 614 880 300` | `state().lat/lng` 变化（水平 50px 会压到左右工具列，不作判据） |
| A7 | 圆内拖动 `input swipe 614 1400 614 1450 300` | `state().lat/lng` 不变 |
| A8 | 点「隐藏」 | 浮层消失；`uiMode=="map"`。**不断言 `#controls` 恢复**——map/compass 下由原生壳承载控件，只有 full 才显示地图自带控件（2026-11-05 总监裁定） |
| A9 | 浮层态点「截屏」 | logcat `LCCAP attribution drawn: 数据来自…审图号：…`（非空） |
| A10 | 浮层态触发 `location.reload()` | 重载后 `state().uiMode=="compass"` 且浮层仍可见；点「隐藏」后 `uiMode=="map"`（A10 修复 `b7aab72` 后应 PASS） |

> 几何（2026-11-05 本地像素实测，方法：金环最小二乘圆拟合，避开红色十字线）：`k01-04-compass2.png` 圆心 (613.6,1349.5)、R=453.6、D=907.1（73.87% W）、圆心偏差 (−0.4,−0.5) px；`smoke3/k01-compass-overlay.png` 圆心 (613.3,1349.5)、D=906.7、偏差 (−0.7,−0.5) px。均远优于「圆心 ≤2% 屏宽」判据。

## 6 不做/不碰

- 不在本笔改 `map.html`；合流由 LC地图 执行。
- 设置入口：原版真实设置页是 `MapSettingActivity`（`SettingActivity` 为死代码，v1.6.1 0 引用）；入口在抽屉「地图设置」。K-01 浮层验收不涉及。
- 账户设置：原版未登录跳 `LoginActivity`，商业流程，按 D-001 省略。
- 「反盘（转 180°）」：原版无此控件，K-01 已删除；`CompassView.setHeadingOffset` 保留为**预留 API**（UI 未接入），P-10 ⑤ 判 N/A。
