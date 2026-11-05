# 山水罗盘 LC · 全量源码审查报告（99 · 候选稿 v0.2）

> **状态：候选稿。** 计数账已按技术总监 2026-11-05 裁定锁定（L1 正式条目 108 条）；条目正文的代码片段、触发条件、预期vs实际、修复方向在**订正批次并入后**补全并签字。行号一律以订正后为准，本稿不固化待订正行号。
> 基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`），工作区 `E:\APPDetector\review-base`。
> 计数唯一来源：`docs/review/60-FINAL.md` §0–§5；本稿与 60 的编号、级别、归属必须逐条一致。

---

## 1 审查概述

### 1.1 基线与范围

| 项 | 值 |
|---|---|
| 分支 / 提交 | `develop` @ `d752efaf7936f887a70577681c703497e74e8b57` |
| 标签 | `review-20261105` |
| 工作区 | `E:\APPDetector\review-base`（独立 worktree，只读） |
| 跟踪文件总数 | 204（源码约 1.1 万行） |
| 审查模块 | M1 桌面、M2 Android、M3 地图/3D、M4 服务端/基建、M5 测试资产、M6 文档/契约、M7 数值档案/构建；M8 研究工件仅盘点 |
| 审查矩阵 | `docs/review/00-MATRIX.md`（204 文件逐个列行数与归属） |

### 1.2 审查组织与批次

| 产物 | 审查人 | 内容 |
|---|---|---|
| `10-M1-desktop.md` | LC审客户端 | M1，A 前缀 |
| `11-M2-android.md` | LC审客户端 | M2，B 前缀 |
| `20-M3-map.md` | LC审地图 | M3，C 前缀 |
| `30-M4-server.md` | LC审服务端 | M4，E 前缀 |
| `40-M5-tests.md` | LC审测试 | M5，F 前缀 |
| `50-M6-docs.md` | LC审文档 | M6+M7，G 前缀 |
| `60-FINAL.md` | LC审成文 | 全量索引、三层计数、delta 表 |
| `90-verify.md` | LC审核验 | 阻断/高 26 条→去重后 20 正式 + 6 交叉引用；确认 17 / 修正 3 / 交叉引用 6 / 驳回 0（B-10 升高 1 条由成文侧并入） |
| `91-audit.md` | LC审证据 | 116 条已证实逐条回源：115 通过 / 1 剔除（B-20，已订正回补）/ 8 条边界订正；delta 复核见 §12 |

### 1.3 三层计数口径（总监裁定）

| 层级 | 定义 | 条数 |
|---|---|---|
| L1 正式条目（母条） | 同一缺陷只计一条，跨模块同因归并到母条 | **108**（阻断 1 / 高 20 / 中 47 / 低 40） |
| L2 交叉引用 | 同因的非母条表述；已撤/已关闭单列，不进 L1 | 16 行（交叉引用 13 + 已撤 2 + 已关闭 1） |
| L3 辅助清单 | 待确认 Q / 风险 R / 已知缺陷 K 各自单列 | Q 16 / R 4 / K 1 |

规则：正式数只认 L1；同因冲突取高；母条归实现模块（双端同源归 B）；90/91 只验 delta。L1+L2+Q+R+K = 108+16+16+4+1 = **145**（143 条报告条目 + G-23 + K-01）。`113`（旧索引）与 `110`（审计估算）作废，以 60 的 108 为准。

### 1.4 本轮裁决要点（全部已执行，详见 60 §5 delta）

1. **B-10 升为高**：双盘读数矛盾直接输出错读数；`A-03`、`F-03` 转交叉引用。
2. **K-01 母条 = B-03 + B-04**：`C-07`、`G-01` 转交叉引用。
3. **交叉引用**：`C-02`→B-02、`G-06`→C-01、`H-02`→A-01、`E-06`→F-01、`G-09`→C-06、`B-18`→C-12、`A-04`→B-09、`C-13`→B-11、`C-16`→B-05。
4. **E-04 维持高**：契约声明 413 永远送不到客户端，属契约破裂；要降须与 E-02 同降。
5. **B-20 保留**（低）：订正为「Android 包内四份 normalize（含 `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:120-124`）+ 跨端两份」，不改级别。
6. **M6/M7 覆盖**：`app/Dockerfile`、`app/docker-compose.yml`、`app/.dockerignore` 归属 M4（已全读），M6 只声明归属、不下结论。
7. **编辑口径**：M4「74 项」订正为 86；帧率/内存数字统一写「推算值，未实测」，具体数字只留一处并标假设（由 M2/M3 订正批次各交一句）。
8. **新立 G-23（低）**：`docs/gap-matrix.md:132` 的「当前实现」列仍写坐向单态，与基线 `app/android/app/src/main/assets/map.html:342-345`（sitMode=2→180°）、同文件 `:370-373`（双标签）的双态不符；来自 `90-verify.md` §11.9，总监定 G-23。L1 107→108。

### 1.5 与旧口径的差异

| 旧数 | 来源 | 作废原因 |
|---|---|---|
| 113 | 60 初版 | 未执行后续 6 条交叉引用裁定、B-10 未升级 |
| 110（高 21） | 91 审计估算 | 与裁定后的母条集不一致，仅作 delta 校验参考 |
| 107（高 20） | 60 v1.0 / 99 v0.1 | 未含 G-23；被 v0.2 取代 |
| **108（高 20）** | 60 v1.1 §0/§3 重算 | **本报告唯一账**（+G-23 低 1 条） |

### 1.6 快照哈希（本稿绑定）

| 文件 | 行数 | SHA256（前 16 位） |
|---|---|---|
| `10-M1-desktop.md` | 624 | `ABC2C291C3DCDF3A` |
| `11-M2-android.md` | 1046 | `72F8D15352B1585F` |
| `20-M3-map.md` | 1180 | `3E37C5C75AE3310D` |
| `30-M4-server.md` | 989 | `EC1AF12F39589FA6` |
| `40-M5-tests.md` | 856 | `38E534C9F921364A` |
| `50-M6-docs.md` | 1034 | `5F9D2760DCE5EA16` |
| `90-verify.md` | 399 | `D35FCAEB15C4FAA9` |
| `91-audit.md` | 475 | `E73176316EFD48B5` |
| `60-FINAL.md` | 315 | `7EBE9AF7ECBA0312` |

---

## 2 缺陷清单

> 本稿为候选稿：编号、级别、证据等级、母条归属已锁定；每条正文的八字段（文件行号·代码片段·触发条件·预期vs实际·证据等级·修复方向）在订正批次并入后逐条补全。行号以订正后为准。

### 2.1 阻断 1 + 高 20 = 21 条

| 编号 | 级别 | 标题 | 主文件行号 | 证据等级 | 母条/归并 | 来源 |
|---|---|---|---|---|---|---|
| A-01 | 高 | [高] `--screenshot` 只走 `software.rs` 独立渲染器，且渲染/字体/保存三… | `app/src/main.rs:613-616`（调用点；实现在 `app/src/software.rs` L237-249、L310-321） | 已证实 | 母条（桌面门禁） | 10-M1-desktop.md |
| A-02 | 高 | [高] 兼向邻山在正北区间回绕错误：显示「兼向坤」而应为「兼向壬」 | `app/src/main.rs:447-455` | 已证实 | — | 10-M1-desktop.md |
| B-02 | 阻断 | [阻断] `DebugBridge` 用 `RECEIVER_EXPORTED` 注册、无权限无签名无 … | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:234-250` | 已证实 | 母条（导出广播注入） | 11-M2-android.md |
| B-03 | 高 | [高] 三 Tab 造成同一页面两份实例：「地图」Tab 与「罗盘」Tab 的底图是同一个 `map.h… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:79-90` | 已证实 | — | 11-M2-android.md |
| B-04 | 高 | [高] 罗盘浮层不在「地图中心」：贴顶部、占屏宽 78%、整块吞掉触摸，且盖住 `map.html` 自… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:267-274` | 已证实 | — | 11-M2-android.md |
| B-05 | 高 | [高] 罗盘上任意点按（无需拖动）即 `followSensor = false`，传感器跟随被永久关闭… | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:99-104` | 已证实 | 母条（触摸停跟随，取高） | 11-M2-android.md |
| B-10 | 高 | [高 · 母条] 「正/兼向」双源：盘心用公式 `Reading.jian`，读数行用表 `jianTe… | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:298-299` | 已证实 | 母条（升为高） | 11-M2-android.md |
| C-01 | 高 | 天地图 APIKEY 明文入库、入包，且用的是原版 key（违反 D-006） | `app/android/app/src/main/assets/map.html:92` | 已证实 | 母条（key 明文） | 20-M3-map.md |
| C-03 | 高 | 3D 地形块最外一行/列 193 个顶点高程恒为 0（每块边缘一圈悬崖，pick 命中假地面） | `app/android/app/src/main/assets/map.html:771-777`（另 518-520、578-595） | 已证实 | — | 20-M3-map.md |
| C-04 | 高 | 2D 拖拽方向与手指相反，且与 3D 拖拽方向相反 | `app/android/app/src/main/assets/map.html:424-434` | 已证实 | — | 20-M3-map.md |
| C-05 | 高 | DEM 任一瓦片失败即整块拒绝，3D 无自动降级；旧块还会滞留在错误位置 | `app/android/app/src/main/assets/map.html:212-214`（另 799-809） | 已证实 | — | 20-M3-map.md |
| C-06 | 高 | `map-bridge.md` §5 的四类事件在实现中不存在，§5/§7 的桥状态已过期（契约破裂） | `docs/map-bridge.md:112-117` | 已证实 | 母条（map-bridge 契约） | 20-M3-map.md |
| E-01 | 高 | （高，已证实）无鉴权 + `Access-Control-Allow-Origin: *` → 任意网页… | `backend/src/server.js:13` | 已证实 | — | 30-M4-server.md |
| E-02 | 高 | （高，已证实）案例 `id` 是全局主键，但「已存在」检查按 owner 过滤 → 跨 owner 撞 … | `backend/src/db.js:11-12` | 已证实 | — | 30-M4-server.md |
| E-03 | 高 | （高，已证实）`/v1/sync/push` 非原子：逐条独立提交，后续条目校验失败时前序已落库；重试会… | `backend/src/server.js:387-399` | 已证实 | — | 30-M4-server.md |
| E-04 | 高 | （高，已证实）请求体超限时先 `req.destroy()`，契约声明的 413 永远送不到客户端 | `backend/src/server.js:69-78` | 已证实 | — | 30-M4-server.md |
| F-01 | 高 | （高，已证实）无 JDK 时数值套件仍 `RESULT: PASS`，AT `RT-011-numeri… | `tests/numeric/run_all.py:31-35` | 已证实 | 母条（数值门禁假绿） | 40-M5-tests.md |
| F-02 | 高 | （高，已证实）780 条圈层文字与 `ring_index` 无执行级断言，B2 验收②靠字符串存在性自… | `tests/numeric/test_b2_golden.py:90-97` | 已证实 | — | 40-M5-tests.md |
| G-02 | 高 | 【高】`gap-matrix.md` 的状态列与基线代码事实不符，且统计数字被 `FINAL-REPOR… | `docs/gap-matrix.md:6` | 已证实 | — | 50-M6-docs.md |
| G-04 | 高 | 【高】「18 项 AT 全绿」不能从基线复核：证据未入库，脚本不校验用例清单 | `docs/FINAL-REPORT.md:11` | 已证实 | — | 50-M6-docs.md |
| G-05 | 高 | 【高】「未确认 0 / 无一条说不清」与同文件未取证条目直接矛盾 | `docs/FINAL-REPORT.md:5` | 已证实 | — | 50-M6-docs.md |

### 2.2 中（47 条）

| 编号 | 级别 | 标题 | 主文件行号 | 证据等级 | 母条/归并 | 来源 |
|---|---|---|---|---|---|---|
| A-05 | 中 | [中] 窗口没有最小尺寸约束，中央面板可用宽度可为负；`central_dial` 用 `.max(32… | `app/src/main.rs:316-321` | 已证实 | — | 10-M1-desktop.md |
| A-06 | 中 | [中] 命令行参数解析：缺值/未知参数静默忽略，`--dump-rings` 非法数值回落 0.0，`-… | `app/src/main.rs:586-592` | 已证实 | — | 10-M1-desktop.md |
| B-06 | 中 | [中] 前后台切换不暂停 WebView、不销毁 WebView：`onPause` 只停传感器与广播，… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:725-732` | 已证实 | — | 11-M2-android.md |
| B-07 | 中 | [中] `configChanges="orientation｜screenSize"` 让 Activ… | `app/android/app/src/main/AndroidManifest.xml:19-22` | 已证实 | — | 11-M2-android.md |
| B-08 | 中 | [中] 没有 rotation vector 传感器时零提示：读数行永远空白、盘心恒显示「0.0° 子山… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:92-95` | 已证实 | — | 11-M2-android.md |
| B-09 | 中 | [中 · 母条] 百二十分金 / 穿山七十二龙 刻度同样忽略 `offset`，刻度落在数据格中心而非边… | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:198-209` | 已证实 | 母条（分金/72龙 offset） | 11-M2-android.md |
| B-11 | 中 | [中] 双 WebView 之间没有任何状态同步：同一「地图」在两页各有独立的中心/缩放/底图源/坐向状… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:262-266` | 已证实 | 母条（双 WebView 注入/状态） | 11-M2-android.md |
| B-12 | 中 | [中] 首启无条件请求定位权限，与用户动作无关；拒绝后无任何提示，`onRequestPermissio… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:98-102` | 已证实 | — | 11-M2-android.md |
| B-13 | 中 | [中] 切到「地图」「知识」Tab 后传感器回调仍全速执行 `updateReading()`：隐藏页也… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:744-749` | 已证实 | — | 11-M2-android.md |
| B-14 | 中 | [中] WebView 对所有来源自动授予地理定位：任意页面（含经 B-02 注入的 URL）请求即放行 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:385-390` | 已证实 | — | 11-M2-android.md |
| C-08 | 中 | 「贴地」相机没有地形高度/遮挡约束，山地会把眼点压进山体；低缩放时「贴地」实际是数十公里高空 | `app/android/app/src/main/assets/map.html:1208-1233` | 已证实 | — | 20-M3-map.md |
| C-09 | 中 | 纬度 >≈84.8° 时 DEM 瓦片 y<0 全部为空 → 3D 必然失败；`setView`/has… | `app/android/app/src/main/assets/map.html:751-776` | 已证实 | — | 20-M3-map.md |
| C-10 | 中 | 跨反经线时 pick 的 lng 可越界（<-180），页面已 emit，Java 桥直接拒收 | `app/android/app/src/main/assets/map.html:1122-1125` | 已证实 | — | 20-M3-map.md |
| C-11 | 中 | `VEXAG` 与 pick 是同一变换空间（无缺陷）；但 pick 的高度用逐像素双线性，与 96×9… | `app/android/app/src/main/assets/map.html:830` | 已证实 | — | 20-M3-map.md |
| C-12 | 中 | 双 WebView：`__LC3D` 互不污染；Java 侧 `mapMarker` 是唯一共享状态且从… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:40` | 已证实 | 母条（mapMarker 共享未读） | 20-M3-map.md |
| C-14 | 中 | 2D 每帧 innerHTML 重建约 30 个瓦片 `<img>`；无复用/无缓存；失败静默隐藏 | `app/android/app/src/main/assets/map.html:148-176` | 已证实 | — | 20-M3-map.md |
| C-15 | 中 | 单实例 3D 资源量级约 40–60 MB（推算）；无 `webglcontextlost` 恢复路径 | `app/android/app/src/main/assets/map.html:601` | 已证实 | — | 20-M3-map.md |
| C-22 | 中 | 2D 立向点按屏幕像素定位，平移/缩放后不跟地图走 | `app/android/app/src/main/assets/map.html:455-465` | 已证实 | — | 20-M3-map.md |
| C-23 | 中 | 2D 旋转/坐向后拖拽未按 bearing 分解 | `app/android/app/src/main/assets/map.html:237-240` | 已证实 | — | 20-M3-map.md |
| C-24 | 中 | 2D 指针角度未减 bearing（3D 减了） | `app/android/app/src/main/assets/map.html:405-411` | 已证实 | — | 20-M3-map.md |
| C-25 | 中 | 「全屏」只隐藏 WebView 内控件，原生罗盘/面板/底栏仍在 | `app/android/app/src/main/assets/map.html:268-283` | 已证实 | — | 20-M3-map.md |
| C-26 | 中 | 3D 默认贴面是影像，角注/截图角注仍显示「地形」 | `app/android/app/src/main/assets/map.html:211` | 已证实 | — | 20-M3-map.md |
| C-27 | 中 | AT 断言弱于文档 + 真机脚本只数 ≥5 条 pick + 环境失败被判 FAIL | `app/android/app/src/main/assets/map.html:1516-1517` | 已证实 | — | 20-M3-map.md |
| C-29 | 中 | 页面错误不可观测：`emit` 静默吞异常、无 `window.onerror`、未覆写 `onCons… | `app/android/app/src/main/assets/map.html:1004-1010` | 已证实 | — | 20-M3-map.md |
| C-34 | 中 | 第二份 AT（probe）要求的 `__LC3D.probe/pick/state().vexag` 不… | `docs/qa/AT-P0-4-3d-probe.md:5` | 已证实 | — | 20-M3-map.md |
| E-05 | 中 | （中，已证实）`gen-mechanics.ps1 -Check` 可被大小写改写 / 诱饵标记绕过 | `tools/mechanics/gen-mechanics.ps1:234-243` | 已证实 | — | 30-M4-server.md |
| E-07 | 中 | （中，已证实）`build-desktop.ps1` 的「硬来源」正则只有英文，文档声称中文词也会判失败 | `scripts/build-desktop.ps1:147-150` | 已证实 | — | 30-M4-server.md |
| E-08 | 中 | （中，已证实）`HOST` 默认 `0.0.0.0`，与文档「仅本机可用」只在 compose 端口发布… | `backend/src/server.js:10`（另 `backend/docker-compose.yml:10`、`backend/README.md:55`） | 已证实 | — | 30-M4-server.md |
| E-09 | 中 | （中，已证实）畸形百分号编码抛 `URIError` → 500（应 400） | `backend/src/server.js:314-316` | 已证实 | — | 30-M4-server.md |
| E-10 | 中 | （中，已证实）无 `Content-Type` 头时接受请求体，与 415 契约不符 | `backend/src/server.js:89-92` | 已证实 | — | 30-M4-server.md |
| E-11 | 中 | （中，已证实）文档的「一键起」指向开发脏树 `E:\APPDetector\output\backend… | `backend/api.md:244` | 已证实 | — | 30-M4-server.md |
| E-12 | 中 | （中，已证实）后端完全没有自动化测试，唯一自检依赖运行中的服务且不覆盖错误路径 | `backend/scripts/selfcheck.mjs:24-27` | 已证实 | — | 30-M4-server.md |
| F-04 | 中 | （中，已证实）`test_ends_parity` 把 60 龙名锁在教材旧值 `癸亥`，修成 DEX … | `tests/numeric/test_ends_parity.py:99-105` | 已证实 | — | 40-M5-tests.md |
| F-05 | 中 | （中，已证实）AT 缺 prefs 时按「代码默认 true」判 PASS（两处） | `tests/at/run-final.ps1:306-311` | 已证实 | — | 40-M5-tests.md |
| F-06 | 中 | （中，已证实）P2-08「顶部两行」宽松回退匹配后仍判 PASS | `tests/at/run-final.ps1:490-501` | 已证实 | — | 40-M5-tests.md |
| F-07 | 中 | （中，已证实）P2-01 点了界面按钮但没产出文件，被判 BLOCKED 而非 FAIL | `tests/at/run-final.ps1:530-547` | 已证实 | — | 40-M5-tests.md |
| F-08 | 中 | （中，已证实）P2-05 只校验分享抽屉存在，不校验被分享文件与相册一致 | `tests/at/run-final.ps1:583-589` | 已证实 | — | 40-M5-tests.md |
| F-09 | 中 | （中，已证实）`PASS(等价路径)` 状态不进任何计数，汇总与退出码都看不见它 | `tests/at/run-final.ps1:375-379` | 已证实 | — | 40-M5-tests.md |
| F-10 | 中 | （中，已证实）三份 AT 文档引用的 `tests\at\run-at.ps1` 在基线不存在 | `docs/qa/AT-P0-0-feature-switch.md:51` | 已证实 | — | 40-M5-tests.md |
| G-03 | 中 | 【中】`FINAL-REPORT.md` 核心证据列指向不存在的生成物，复现路径指向非基线目录 | `docs/FINAL-REPORT.md:12` | 已证实 | — | 50-M6-docs.md |
| G-07 | 中 | 【中】「19 项全部有来源、0 项估计」是关键词扫描，不是溯源；其中 3 项与 DEX 证据语义冲突 | `tests/numeric/tools/check_mechanics.py:19` | 已证实 | — | 50-M6-docs.md |
| G-08 | 中 | 【中】`MECHANICS.md` 的地址类声明依赖库外 `.inspect` 证据，基线内不可回溯（但… | `app/MECHANICS.md:32-34` | 已证实 | — | 50-M6-docs.md |
| G-10 | 中 | 【中】用户可见的知识库文案与数值档案/DEX 表冲突（D-008 文案诚信） | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:273` | 已证实 | — | 50-M6-docs.md |
| G-11 | 中 | 【中】`MECHANICS.md` 早期章节与 R1/R2 补证矛盾且未标「已取代」，R2.2 仍写成待… | `app/MECHANICS.md:166-168` | 已证实 | — | 50-M6-docs.md |
| G-17 | 中 | 【中】航向注入命令的键名在文档里自相矛盾：`--ef heading`（错）vs `--ef value… | `docs/qa/device-method.md:43-44` | 已证实 | — | 50-M6-docs.md |
| G-18 | 中 | 【中】AT-P0-2 的 P2-02（地图/3D 截图）在基线没有可执行入口，最终 18 项里被静默剔除 | `docs/qa/AT-P0-2-capture-record.md:35-39` | 已证实 | — | 50-M6-docs.md |
| G-19 | 中 | 【中】AT-P0-1 的 RT-010 指向已被 `device-method.md` 宣布作废的旧基线 | `docs/qa/AT-P0-1-reading-trust.md:82` | 已证实 | — | 50-M6-docs.md |

### 2.3 低（40 条）

| 编号 | 级别 | 标题 | 主文件行号 | 证据等级 | 母条/归并 | 来源 |
|---|---|---|---|---|---|---|
| A-07 | 低 | [低] 拖拽转盘只吃水平位移，垂直拖动完全无响应；灵敏度 0.5°/px 为硬编码常量 | `app/src/main.rs:324-329` | 已证实 | — | 10-M1-desktop.md |
| A-08 | 低 | [低] 「朝向」滑块范围 `0.0..=360.0` 与归一化朝向冲突：可停在 360.00°，与 0°… | `app/src/main.rs:374-377` | 已证实 | — | 10-M1-desktop.md |
| A-09 | 低 | [低] 死代码与未使用导入：`wangxiang/borrow_branch/nayin_of` 全仓零… | `app/src/main.rs:182-190` | 已证实 | — | 10-M1-desktop.md |
| B-15 | 低 | [低] 截图入口固定在罗盘页：在「地图」/「知识」Tab 触发（按钮或 `capture` 广播）必失败 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:400-405` | 已证实 | — | 11-M2-android.md |
| B-16 | 低 | [低] `looksBlank` 只采 4 个像素判黑图：单点高光即可让全黑图通过 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:456-461` | 已证实 | — | 11-M2-android.md |
| B-17 | 低 | [低] `onSensorChanged` 内 `if (injectedHeading != null… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:737-743` | 已证实 | — | 11-M2-android.md |
| B-19 | 低 | [低] `dial0.png` 与 `dial_full_0.png` 字节完全相同（重复资源），且 4… | `app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java:25-28` | 已证实 | — | 11-M2-android.md |
| B-20 | 低 | [低] 同一「角度归一化」实现被复制四份（Android 包内：`SensorMath.normaliz… | `app/android/app/src/main/java/com/luopan/fengshui/SensorMath.java:14-18`（四份含 `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:120-124`） | 已证实 | — | 11-M2-android.md |
| B-21 | 低 | [低] 读数行 `String.format` 未指定 Locale，状态条却用 `Locale.US`… | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:640` | 已证实 | — | 11-M2-android.md |
| C-17 | 低 | `map-bridge.md` 4 处实现细节漂移 | `docs/map-bridge.md:72` | 已证实 | — | 20-M3-map.md |
| C-28 | 低 | pick 射线离开地形块后不提前退出，单次点选固定跑满 ~1140 步 | `app/android/app/src/main/assets/map.html:1090-1102` | 已证实 | — | 20-M3-map.md |
| C-30 | 低 | `#in3d&sit` 入口硬编码 `setHeading(90)`；`&z=<9` 可绕过「3D 最小… | `app/android/app/src/main/assets/map.html:1480-1483`（整块 1477-1483） | 已证实 | — | 20-M3-map.md |
| C-31 | 低 | 进 3D 强制 zoom≥9，返回 2D 不恢复原 zoom | `app/android/app/src/main/assets/map.html:207-210` | 已证实 | — | 20-M3-map.md |
| C-32 | 低 | 贴面构建 `ok<30` 即放弃且不重试 | `app/android/app/src/main/assets/map.html:899-903` | 已证实 | — | 20-M3-map.md |
| C-33 | 低 | `map.html` 单文件 1605 行、20+ 全局可变状态、无模块边界；测试入口混在生产代码 | `app/android/app/src/main/assets/map.html:91-114` | 已证实 | — | 20-M3-map.md |
| E-13 | 低 | （低，已证实）`changes` 与 `shares` 无清理策略，长期无界增长 | `backend/src/db.js:116-120` | 已证实 | — | 30-M4-server.md |
| E-14 | 低 | （低，已证实）请求日志记录完整 URL，分享 token 与 `owner_id` 落盘 | `backend/src/server.js:468-470` | 已证实 | — | 30-M4-server.md |
| E-15 | 低 | （低，已证实）`/health` 对未鉴权调用方暴露全局案例/分享计数 | `backend/src/server.js:261-270` | 已证实 | — | 30-M4-server.md |
| E-16 | 低 | （低，已证实）空 patch 的 PUT 也推进 `rev` 并写变更日志 | `backend/src/db.js:165-168` | 已证实 | — | 30-M4-server.md |
| E-17 | 低 | （低，已证实）重复撤销分享仍返回 200 | `backend/src/db.js:264-267` | 已证实 | — | 30-M4-server.md |
| E-18 | 低 | （低，已证实）`backend/api.md` 声称 `smoke.ps1` 依赖 `/health`，… | `backend/api.md:95` | 已证实 | — | 30-M4-server.md |
| E-19 | 低 | （低，已证实）Gradle wrapper 只有 properties、没有 `gradlew`/jar… | `scripts/build-android.ps1:94` | 已证实 | — | 30-M4-server.md |
| E-20 | 低 | （低，已证实）提交守卫把已跟踪的 `craft.json` 列为 always-deny | `scripts/check-staged-paths.ps1:156-157` | 已证实 | — | 30-M4-server.md |
| E-21 | 低 | （低，已证实）`selfcheck.mjs` 在生产卷上留测试数据 | `backend/scripts/selfcheck.mjs:92-96` | 已证实 | — | 30-M4-server.md |
| E-22 | 低 | （低，已证实）Android 构建镜像以 root 运行、bind-mount 工作区、下载 SDK 不… | `app/android/docker-compose.yml:5-9` | 已证实 | — | 30-M4-server.md |
| E-23 | 低 | （低，已证实）后端镜像基础标签浮动，构建不可完全复现 | （无） | 已证实 | — | 30-M4-server.md |
| E-24 | 低 | （低，已证实）全同步 SQLite 调用阻塞事件循环，且无限流/连接上限 | `backend/src/server.js:487-489` | 已证实 | — | 30-M4-server.md |
| E-25 | 低 | （低，已证实）Android 真机门禁的 logcat 窗口只有 5 秒 | `scripts/build-android.ps1:242` | 已证实 | — | 30-M4-server.md |
| F-11 | 低 | （低，已证实）圈层常量用子串存在性断言，「注释里写个 5.625」即可通过 | `tests/numeric/test_mechanics.py:112-121` | 已证实 | — | 40-M5-tests.md |
| F-12 | 低 | （低，已证实）文档里的套件用例数（83 / 74）与实测 86 不一致 | `docs/qa/AT-P0-1-reading-trust.md:83` | 已证实 | — | 40-M5-tests.md |
| F-13 | 低 | （低，已证实）基线冻结脚本无条件声明「本基线含 disc-dial-list.png」，即使没采到 | `tests/numeric/tools/freeze_android_baseline.ps1:70-80` | 已证实 | — | 40-M5-tests.md |
| F-14 | 低 | （低，已证实）`at_p0_0_check.ps1` 计算了「磁北/真北」行却不参与判定 | `tests/numeric/tools/at_p0_0_check.ps1:110-124` | 已证实 | — | 40-M5-tests.md |
| F-15 | 低 | （低，已证实）两处注释与基线事实不符（TYPE_9「推定」、JIAZI「xfail 锁住」） | `tests/numeric/test_plates.py:65-72` | 已证实 | — | 40-M5-tests.md |
| G-12 | 低 | 【低】`bugs.md` 的 OBS-001/OBS-002 在基线已不成立，台账仍列「待归口/备忘」 | `docs/qa/bugs.md:271-273` | 已证实 | — | 50-M6-docs.md |
| G-13 | 低 | 【低】数值测试条数在文档里有 74 / 75 / 83 / 86 四个值，实际 86 | `scripts/smoke.ps1:11` | 已证实 | — | 50-M6-docs.md |
| G-14 | 低 | 【低】`app/README.md` 的 3D 实现描述与基线不符 | `app/README.md:24` | 已证实 | — | 50-M6-docs.md |
| G-20 | 低 | 【低】AT-P0-4 两份文档的用例数与 ID 互相打架 | `docs/qa/AT-P0-4-3d-pick.md:11` | 已证实 | — | 50-M6-docs.md |
| G-21 | 低 | 【低】`variant-p01\manifest.md` 含 CR/BEL 控制字符，命令文本被吃掉字母 | `docs/qa/baseline-android/round-d5/variant-p01/manifest.md:8-9` | 已证实 | — | 50-M6-docs.md |
| G-22 | 低 | 【低】PRD「代码与 `8cb57f3` 零差异」不成立（测试脚本有 25/18 行差异） | `docs/product/PRD-v0.1.md:361` | 已证实 | — | 50-M6-docs.md |
| G-23 | 低 | gap-matrix.md 的「当前实现」列仍写坐向单态，与基线双态不符（90 §11.9 新立，总监定… | `docs/gap-matrix.md:132`；`app/android/app/src/main/assets/map.html:342-345`、同文件 `:370-373` | 已证实 | — | 90-verify.md |

---

## 3 已知缺陷清单

### K-01 底部三 Tab 与原版首页结构不一致；罗盘浮层不在图心且遮图

| 项 | 值 |
|---|---|
| 来源 | 用户直接提出（项目群原文） |
| 级别 | **高**（B/C/G 三报告一致，总监裁定母条 = B-03 + B-04） |
| 证据等级 | 已证实 |
| 母条 | `B-03`（三 Tab 两份 WebView 实例）+ `B-04`（浮层错位/遮图/吞触摸） |
| 交叉引用 | `C-07`、`G-01`（不计条） |

**用户原话**：

> 底部菜单里「罗盘」与「地图」其实是同一个页面的不同表现：参照原 APK 首页布局，罗盘应是点击「罗盘」按钮后浮动叠加在地图中心，配合地图模拟真实罗盘场景；整个底部菜单栏没有存在意义。要遮挡页面的组件与子页，应依原 APK 的设计逻辑放在合适位置、用合适的呈现方式。

**现状（基线代码事实）**：

`app/android/app/src/main/res/layout/activity_main.xml` L14-L30：

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

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L79-L90：

```java
        compassTab = buildCompassTab();
        mapView = buildMapTab();
        knowledgeTab = buildKnowledgeTab();

        content.addView(compassTab);
        content.addView(mapView);
        content.addView(knowledgeTab);

        tabCompass.setOnClickListener(v -> showTab(0));
        tabMap.setOnClickListener(v -> showTab(1));
        tabKnowledge.setOnClickListener(v -> showTab(2));
        showTab(0);
```

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L630-L634：

```java
    private void showTab(int index) {
        compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }
```

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L262-L274：

```java
        // Base map (底图). Keep the reference: 轮 C 截图与坐向推送都指向这个 WebView.
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

**档案依据（原版首页布局）**：

- `docs/gap-matrix.md` L121（E6）：原版罗盘页控件清点——左列 截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地，右列 地图/实景/坐向朝北/指针开关，底栏仅 切换/旋转/缩放/透明度/天支线；**「地图」是页面内右列按钮，不是底部页面导航**。
- `app/MECHANICS.md` L466-L468：原版罗盘页左列按钮真机坐标（x≈132，读盘 y≈2070），说明原版是整屏页面 + 浮动按钮列。
- `docs/gap-matrix.md` L97（C2）：原版知识入口是「点圈层文字 → `CompassIntroduceActivity`」，不是底部 Tab。
- `docs/product/PRD-v0.1.md` L100（B-19）：三 Tab 的现状被登记为「原版主页结构（未逐页比对）」——**无逐页比对证据**。

**预期 vs 实际**：

- 预期（按原版）：首页 = 地图 WebView + 罗盘覆盖层；「地图」按钮切换罗盘/纯地图；不存在的底部三 Tab。
- 实际：底部三按钮常驻；罗盘与地图各持一个 `map.html` WebView 实例；罗盘浮层贴顶部、占屏宽 78%（`heightPixels` 无关）、`Gravity.TOP` + 16dp，圆心不在屏心（B 报告按 1080×2340@3.0 复算圆心 y≈469，屏心 1170）；`ACTION_DOWN` 无条件吞触摸，遮住 `map.html` 控件列。

**修复方向**（引自 B 报告 §2.4 / C 报告 §6.4，成文时逐字并入）：删除底部三 Tab 与第二个 WebView 实例；罗盘浮层改居中并让圆外触摸透传；知识入口改为点圈层文字直达弹层；底图控件列按原版左右列布局承载。可判定验收标准 4 条见 `11-M2-android.md` §2.4。

**限制**：「浮在地图中心」的具体几何（透明度/尺寸/是否居中）在 `review-base` 内无原版对照证据（`C-21`，待确认）；本缺陷的「三 Tab 无依据 + 浮层遮图」结论不依赖该几何取证。

---

## 4 风险与改进建议清单

| 编号 | 优先级 | 标题 | 线索 | 来源 |
|---|---|---|---|---|
| R-01 | P1 | （P1）86 条用例中 37 条只测 oracle 自身，不触达被测源码 | （无） | 40-M5-tests.md |
| R-02 | P1 | （P1）跨方位一致性只有 41 个航向 + 4 个文字向量，无全周/全格扫描 | （无既有文件；`test_sweep.py` 为建议新增） | 40-M5-tests.md |
| R-03 | P2 | （P2）把 `expectedFailure` 定为惯例、`run_all` 定义「含 xfail = 全绿」 | `tests/numeric/README.md:60` | 40-M5-tests.md |
| R-04 | P2 | （P2）`source_parse.rust_let_expr` 全文件按名字取首个 `let`，无函数作用域 | `tests/numeric/source_parse.py:55-60` | 40-M5-tests.md |

---

## 5 待确认清单

> 每条写清「缺什么证据、由谁补、补齐后的判据」在成文时补全；候选稿保留统一编号与原编号映射。

| 统一编号 | 原编号 | 级别 | 证据状态 | 线索（文件行号） | 缺什么证据 / 由谁补 | 来源 |
|---|---|---|---|---|---|---|
| Q-01 | A-Q01 | 中 | 高度可疑 | `app/src/main.rs:525-527` | 见 `10-M1-desktop.md` 原 Q 段：缺证据、补证人、判据待并入 | 10-M1-desktop.md |
| Q-02 | A-Q02 | — | 待确认 | `app/src/main.rs:134` | 见 `10-M1-desktop.md` 原 Q 段：缺证据、补证人、判据待并入 | 10-M1-desktop.md |
| Q-03 | A-Q03 | — | 待确认 | `app/src/main.rs:613-616` | 见 `10-M1-desktop.md` 原 Q 段：缺证据、补证人、判据待并入 | 10-M1-desktop.md |
| Q-04 | B-Q01 | 中 | 高度可疑 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:400-415` | 见 `11-M2-android.md` 原 Q 段：缺证据、补证人、判据待并入 | 11-M2-android.md |
| Q-05 | B-Q02 | — | 待确认 | `docs/gap-matrix.md:121`；`app/MECHANICS.md:466-468`（原 `docs/MECHANICS.md` 笔误已改） | 见 `11-M2-android.md` 原 Q 段：缺证据、补证人、判据待并入 | 11-M2-android.md |
| Q-06 | B-Q03 | — | 待确认 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:100` | 见 `11-M2-android.md` 原 Q 段：缺证据、补证人、判据待并入 | 11-M2-android.md |
| Q-07 | C-18 | 中 | 高度可疑 | `app/android/app/src/main/assets/map.html:624-625` | 见 `20-M3-map.md` 原 Q 段：缺证据、补证人、判据待并入 | 20-M3-map.md |
| Q-08 | C-19 | 低 | 高度可疑 | `app/android/app/src/main/assets/map.html:527` | 见 `20-M3-map.md` 原 Q 段：缺证据、补证人、判据待并入 | 20-M3-map.md |
| Q-09 | C-20 | 中 | 待确认 | `app/android/app/src/main/assets/map.html:475-485` | 见 `20-M3-map.md` 原 Q 段：缺证据、补证人、判据待并入 | 20-M3-map.md |
| Q-10 | C-21 | 低 | 待确认 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:163-169` | 见 `20-M3-map.md` 原 Q 段：缺证据、补证人、判据待并入 | 20-M3-map.md |
| Q-11 | Q-01 | — | 待确认 | `scripts/smoke.ps1:128` | 见 `30-M4-server.md` 原 Q 段：缺证据、补证人、判据待并入 | 30-M4-server.md |
| Q-12 | Q-01 | — | 待确认 | `tests/numeric/test_mechanics.py:149-159` | 见 `40-M5-tests.md` 原 Q 段：缺证据、补证人、判据待并入 | 40-M5-tests.md |
| Q-13 | Q-02 | — | 待确认 | `docs/FINAL-REPORT.md:11` | 见 `40-M5-tests.md` 原 Q 段：缺证据、补证人、判据待并入 | 40-M5-tests.md |
| Q-14 | Q-03 | — | 待确认 | `app/MECHANICS.md:183-188` | 见 `40-M5-tests.md` 原 Q 段：缺证据、补证人、判据待并入 | 40-M5-tests.md |
| Q-15 | G-15 | — | 待确认 | `docs/qa/AT-final-round.md:23-28` | 见 `50-M6-docs.md` 原 Q 段：缺证据、补证人、判据待并入 | 50-M6-docs.md |
| Q-16 | G-16 | — | 待确认 | `app/MECHANICS.md:276-282`（另库外 `.inspect` 证据链） | 见 `50-M6-docs.md` 原 Q 段：缺证据、补证人、判据待并入 | 50-M6-docs.md |

---

## 6 未覆盖范围声明

| 范围 | 状态 | 依据 | 补做安排 |
|---|---|---|---|
| M1 桌面（3 文件/1276 行） | 已覆盖 | `10-M1-desktop.md` §7；90/91 审计确认完整 | — |
| M2 Android（25 文件/1901 行） | 已覆盖 | `11-M2-android.md` §7；审计确认完整 | — |
| M3 地图/3D（3 文件/1697 行） | 已覆盖 | `20-M3-map.md` §9；审计确认完整 | — |
| M4 服务端/基建（26 文件/2812 行） | 已覆盖 | `30-M4-server.md` §15；审计确认 26/26 | — |
| M5 测试资产（22 文件/2974 行） | 已覆盖 | `40-M5-tests.md` §11；审计确认 22/22 | — |
| M6 文档/契约（42 文件） | 部分覆盖 | `50-M6-docs.md` §6：24 个 Markdown 全读、PNG/XML 哈希核对 | 3 个容器文件归属 M4，补声明即可 |
| M7 数值档案/构建（8 文件） | 部分覆盖 | `50-M6-docs.md`；`app/Dockerfile`、`app/docker-compose.yml`、`app/.dockerignore` 由 M4 全读 | M6 写「归属 M4，未读、不下结论」 |
| M8 研究工件（75 文件/82258 行） | 不审，仅盘点 | `00-MATRIX.md` §4 | — |
| 二进制/图片（`res/drawable*`、`*.png`） | 仅资源一致性 | `00-MATRIX.md` §4；G 报告 10 PNG + 5 XML 哈希 15/15 一致 | — |
| 基线外分支/未提交改动 | 不在范围 | `00-MATRIX.md` §4 | — |

**未运行的验证**：桌面 `cargo build`、Android 真机、无头截图脚本均未在只读基线执行；帧率/内存类数字全部标「推算，非实测」（C 报告 §9.5、M4 报告 §10）。

---

## 7 证据索引

> 每条 L1 条目一行 EV；EV-108 为 K-01（沿用 v0.1 编号），v0.2 新增的 G-23 顺延为 EV-109。「未核」状态在订正批次并入后由 LC审证据按 91 的 delta 口径复核，通过后改「已核（逐字一致）」。

| EV | 关联编号 | 文件（相对 review-base） | 行号 | 片段摘要（≤40 字） | 核验状态 |
|---|---|---|---|---|---|
| EV-001 | A-01 | `app/src/main.rs` | 613-616 | [高] `--screenshot` 只走 `softwar… | 未核（待订正批次后） |
| EV-002 | A-02 | `app/src/main.rs` | 447-455 | [高] 兼向邻山在正北区间回绕错误：显示「兼向坤」而应为「兼… | 未核（待订正批次后） |
| EV-003 | A-05 | `app/src/main.rs` | 316-321 | [中] 窗口没有最小尺寸约束，中央面板可用宽度可为负；`ce… | 未核（待订正批次后） |
| EV-004 | A-06 | `app/src/main.rs` | 586-592 | [中] 命令行参数解析：缺值/未知参数静默忽略，`--dum… | 未核（待订正批次后） |
| EV-005 | A-07 | `app/src/main.rs` | 324-329 | [低] 拖拽转盘只吃水平位移，垂直拖动完全无响应；灵敏度 0… | 未核（待订正批次后） |
| EV-006 | A-08 | `app/src/main.rs` | 374-377 | [低] 「朝向」滑块范围 `0.0..=360.0` 与归一… | 未核（待订正批次后） |
| EV-007 | A-09 | `app/src/main.rs` | 182-190 | [低] 死代码与未使用导入：`wangxiang/borro… | 未核（待订正批次后） |
| EV-008 | B-02 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 234-250 | [阻断] `DebugBridge` 用 `RECEIVER… | 未核（待订正批次后） |
| EV-009 | B-03 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 79-90 | [高] 三 Tab 造成同一页面两份实例：「地图」Tab 与… | 未核（待订正批次后） |
| EV-010 | B-04 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 267-274 | [高] 罗盘浮层不在「地图中心」：贴顶部、占屏宽 78%、整… | 未核（待订正批次后） |
| EV-011 | B-05 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 99-104 | [高] 罗盘上任意点按（无需拖动）即 `followSens… | 未核（待订正批次后） |
| EV-012 | B-10 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 298-299 | [高 · 母条] 「正/兼向」双源：盘心用公式 `Readi… | 未核（待订正批次后） |
| EV-013 | B-06 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 725-732 | [中] 前后台切换不暂停 WebView、不销毁 WebVi… | 未核（待订正批次后） |
| EV-014 | B-07 | `app/android/app/src/main/AndroidManifest.xml` | 19-22 | [中] `configChanges="orientatio… | 未核（待订正批次后） |
| EV-015 | B-08 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 92-95 | [中] 没有 rotation vector 传感器时零提示… | 未核（待订正批次后） |
| EV-016 | B-09 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java` | 198-209 | [中 · 母条] 百二十分金 / 穿山七十二龙 刻度同样忽略… | 未核（待订正批次后） |
| EV-017 | B-11 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 262-266 | [中] 双 WebView 之间没有任何状态同步：同一「地图… | 未核（待订正批次后） |
| EV-018 | B-12 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 98-102 | [中] 首启无条件请求定位权限，与用户动作无关；拒绝后无任何… | 未核（待订正批次后） |
| EV-019 | B-13 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 744-749 | [中] 切到「地图」「知识」Tab 后传感器回调仍全速执行 … | 未核（待订正批次后） |
| EV-020 | B-14 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 385-390 | [中] WebView 对所有来源自动授予地理定位：任意页面… | 未核（待订正批次后） |
| EV-021 | B-15 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 400-405 | [低] 截图入口固定在罗盘页：在「地图」/「知识」Tab 触… | 未核（待订正批次后） |
| EV-022 | B-16 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 456-461 | [低] `looksBlank` 只采 4 个像素判黑图：单… | 未核（待订正批次后） |
| EV-023 | B-17 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 737-743 | [低] `onSensorChanged` 内 `if (i… | 未核（待订正批次后） |
| EV-024 | B-19 | `app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java` | 25-28 | [低] `dial0.png` 与 `dial_full_0… | 未核（待订正批次后） |
| EV-025 | B-20 | `app/android/app/src/main/java/com/luopan/fengshui/SensorMath.java` | 14-18 | [低] 同一「角度归一化」实现被复制四份（Android 包… | 未核（待订正批次后） |
| EV-026 | B-21 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 640 | [低] 读数行 `String.format` 未指定 Lo… | 未核（待订正批次后） |
| EV-027 | C-01 | `app/android/app/src/main/assets/map.html` | 92 | 天地图 APIKEY 明文入库、入包，且用的是原版 key（… | 未核（待订正批次后） |
| EV-028 | C-03 | `app/android/app/src/main/assets/map.html` | 518-520 | 3D 地形块最外一行/列 193 个顶点高程恒为 0（每块边… | 未核（待订正批次后） |
| EV-029 | C-04 | `app/android/app/src/main/assets/map.html` | 424-434 | 2D 拖拽方向与手指相反，且与 3D 拖拽方向相反 | 未核（待订正批次后） |
| EV-030 | C-05 | `app/android/app/src/main/assets/map.html` | 517 | DEM 任一瓦片失败即整块拒绝，3D 无自动降级；旧块还会滞… | 未核（待订正批次后） |
| EV-031 | C-06 | `docs/map-bridge.md` | 112-117 | `map-bridge.md` §5 的四类事件在实现中不存… | 未核（待订正批次后） |
| EV-032 | C-08 | `app/android/app/src/main/assets/map.html` | 1208-1233 | 「贴地」相机没有地形高度/遮挡约束，山地会把眼点压进山体；低… | 未核（待订正批次后） |
| EV-033 | C-09 | `app/android/app/src/main/assets/map.html` | 751-776 | 纬度 >≈84.8° 时 DEM 瓦片 y<0 全部为空 →… | 未核（待订正批次后） |
| EV-034 | C-10 | `app/android/app/src/main/assets/map.html` | 1122-1125 | 跨反经线时 pick 的 lng 可越界（<-180），页面… | 未核（待订正批次后） |
| EV-035 | C-11 | `app/android/app/src/main/assets/map.html` | 830 | `VEXAG` 与 pick 是同一变换空间（无缺陷）；但 … | 未核（待订正批次后） |
| EV-036 | C-12 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` | 40 | 双 WebView：`__LC3D` 互不污染；Java 侧… | 未核（待订正批次后） |
| EV-037 | C-14 | `app/android/app/src/main/assets/map.html` | 148-176 | 2D 每帧 innerHTML 重建约 30 个瓦片 `<i… | 未核（待订正批次后） |
| EV-038 | C-15 | `app/android/app/src/main/assets/map.html` | 601 | 单实例 3D 资源量级约 40–60 MB（推算）；无 `w… | 未核（待订正批次后） |
| EV-039 | C-22 | `app/android/app/src/main/assets/map.html` | 455-465 | 2D 立向点按屏幕像素定位，平移/缩放后不跟地图走 | 未核（待订正批次后） |
| EV-040 | C-23 | `app/android/app/src/main/assets/map.html` | 237-240 | 2D 旋转/坐向后拖拽未按 bearing 分解 | 未核（待订正批次后） |
| EV-041 | C-24 | `app/android/app/src/main/assets/map.html` | 405-411 | 2D 指针角度未减 bearing（3D 减了） | 未核（待订正批次后） |
| EV-042 | C-25 | `app/android/app/src/main/assets/map.html` | 268-283 | 「全屏」只隐藏 WebView 内控件，原生罗盘/面板/底栏… | 未核（待订正批次后） |
| EV-043 | C-26 | `app/android/app/src/main/assets/map.html` | 211 | 3D 默认贴面是影像，角注/截图角注仍显示「地形」 | 未核（待订正批次后） |
| EV-044 | C-27 | `app/android/app/src/main/assets/map.html` | 1516-1517 | AT 断言弱于文档 + 真机脚本只数 ≥5 条 pick +… | 未核（待订正批次后） |
| EV-045 | C-29 | `app/android/app/src/main/assets/map.html` | 1004-1010 | 页面错误不可观测：`emit` 静默吞异常、无 `windo… | 未核（待订正批次后） |
| EV-046 | C-34 | `docs/qa/AT-P0-4-3d-probe.md` | 5 | 第二份 AT（probe）要求的 `__LC3D.probe… | 未核（待订正批次后） |
| EV-047 | C-17 | `docs/map-bridge.md` | 72 | `map-bridge.md` 4 处实现细节漂移 | 未核（待订正批次后） |
| EV-048 | C-28 | `app/android/app/src/main/assets/map.html` | 1090-1102 | pick 射线离开地形块后不提前退出，单次点选固定跑满 ~1… | 未核（待订正批次后） |
| EV-049 | C-30 | `app/android/app/src/main/assets/map.html` | 1486-1493 | `#in3d&sit` 入口硬编码 `setHeading(… | 未核（待订正批次后） |
| EV-050 | C-31 | `app/android/app/src/main/assets/map.html` | 207-210 | 进 3D 强制 zoom≥9，返回 2D 不恢复原 zoom | 未核（待订正批次后） |
| EV-051 | C-32 | `app/android/app/src/main/assets/map.html` | 899-903 | 贴面构建 `ok<30` 即放弃且不重试 | 未核（待订正批次后） |
| EV-052 | C-33 | `app/android/app/src/main/assets/map.html` | 91-114 | `map.html` 单文件 1605 行、20+ 全局可变… | 未核（待订正批次后） |
| EV-053 | E-01 | `backend/src/server.js` | 13 | （高，已证实）无鉴权 + `Access-Control-A… | 未核（待订正批次后） |
| EV-054 | E-02 | `backend/src/db.js` | 11-12 | （高，已证实）案例 `id` 是全局主键，但「已存在」检查按… | 未核（待订正批次后） |
| EV-055 | E-03 | `backend/src/server.js` | 380-386 | （高，已证实）`/v1/sync/push` 非原子：逐条独… | 未核（待订正批次后） |
| EV-056 | E-04 | `backend/src/server.js` | 69-78 | （高，已证实）请求体超限时先 `req.destroy()`… | 未核（待订正批次后） |
| EV-057 | E-05 | `tools/mechanics/gen-mechanics.ps1` | 234-243 | （中，已证实）`gen-mechanics.ps1 -Che… | 未核（待订正批次后） |
| EV-058 | E-07 | `scripts/build-desktop.ps1` | 147-150 | （中，已证实）`build-desktop.ps1` 的「硬… | 未核（待订正批次后） |
| EV-059 | E-08 | `backend/src/server.js` | 10 | （中，已证实）`HOST` 默认 `0.0.0.0`，与文档… | 未核（待订正批次后） |
| EV-060 | E-09 | `backend/src/server.js` | 314-316 | （中，已证实）畸形百分号编码抛 `URIError` → 5… | 未核（待订正批次后） |
| EV-061 | E-10 | `backend/src/server.js` | 89-92 | （中，已证实）无 `Content-Type` 头时接受请求… | 未核（待订正批次后） |
| EV-062 | E-11 | `backend/api.md` | 244 | （中，已证实）文档的「一键起」指向开发脏树 `E:\APPD… | 未核（待订正批次后） |
| EV-063 | E-12 | `backend/scripts/selfcheck.mjs` | 24-27 | （中，已证实）后端完全没有自动化测试，唯一自检依赖运行中的服… | 未核（待订正批次后） |
| EV-064 | E-13 | `backend/src/db.js` | 116-120 | （低，已证实）`changes` 与 `shares` 无清… | 未核（待订正批次后） |
| EV-065 | E-14 | `backend/src/server.js` | 468-470 | （低，已证实）请求日志记录完整 URL，分享 token 与… | 未核（待订正批次后） |
| EV-066 | E-15 | `backend/src/server.js` | 261-270 | （低，已证实）`/health` 对未鉴权调用方暴露全局案例… | 未核（待订正批次后） |
| EV-067 | E-16 | `backend/src/db.js` | 165-168 | （低，已证实）空 patch 的 PUT 也推进 `rev`… | 未核（待订正批次后） |
| EV-068 | E-17 | `backend/src/db.js` | 264-267 | （低，已证实）重复撤销分享仍返回 200 | 未核（待订正批次后） |
| EV-069 | E-18 | `backend/api.md` | 95 | （低，已证实）`backend/api.md` 声称 `sm… | 未核（待订正批次后） |
| EV-070 | E-19 | `scripts/build-android.ps1` | 94 | （低，已证实）Gradle wrapper 只有 prope… | 未核（待订正批次后） |
| EV-071 | E-20 | `scripts/check-staged-paths.ps1` | 156-157 | （低，已证实）提交守卫把已跟踪的 `craft.json` … | 未核（待订正批次后） |
| EV-072 | E-21 | `backend/scripts/selfcheck.mjs` | 92-96 | （低，已证实）`selfcheck.mjs` 在生产卷上留测… | 未核（待订正批次后） |
| EV-073 | E-22 | `app/android/docker-compose.yml` | 5-9 | （低，已证实）Android 构建镜像以 root 运行、b… | 未核（待订正批次后） |
| EV-074 | E-23 | （无） | — | （低，已证实）后端镜像基础标签浮动，构建不可完全复现 | 未核（待订正批次后） |
| EV-075 | E-24 | `backend/src/server.js` | 487-489 | （低，已证实）全同步 SQLite 调用阻塞事件循环，且无限… | 未核（待订正批次后） |
| EV-076 | E-25 | `scripts/build-android.ps1` | 242 | （低，已证实）Android 真机门禁的 logcat 窗口… | 未核（待订正批次后） |
| EV-077 | F-01 | `tests/numeric/run_all.py` | 31-35 | （高，已证实）无 JDK 时数值套件仍 `RESULT: P… | 未核（待订正批次后） |
| EV-078 | F-02 | `tests/numeric/test_b2_golden.py` | 90-97 | （高，已证实）780 条圈层文字与 `ring_index`… | 未核（待订正批次后） |
| EV-079 | F-04 | `tests/numeric/test_ends_parity.py` | 99-105 | （中，已证实）`test_ends_parity` 把 60… | 未核（待订正批次后） |
| EV-080 | F-05 | `tests/at/run-final.ps1` | 306-311 | （中，已证实）AT 缺 prefs 时按「代码默认 true… | 未核（待订正批次后） |
| EV-081 | F-06 | `tests/at/run-final.ps1` | 490-501 | （中，已证实）P2-08「顶部两行」宽松回退匹配后仍判 PA… | 未核（待订正批次后） |
| EV-082 | F-07 | `tests/at/run-final.ps1` | 530-547 | （中，已证实）P2-01 点了界面按钮但没产出文件，被判 B… | 未核（待订正批次后） |
| EV-083 | F-08 | `tests/at/run-final.ps1` | 583-589 | （中，已证实）P2-05 只校验分享抽屉存在，不校验被分享文… | 未核（待订正批次后） |
| EV-084 | F-09 | `tests/at/run-final.ps1` | 375-379 | （中，已证实）`PASS(等价路径)` 状态不进任何计数，汇… | 未核（待订正批次后） |
| EV-085 | F-10 | `docs/qa/AT-P0-0-feature-switch.md` | 51 | （中，已证实）三份 AT 文档引用的 `tests\at\r… | 未核（待订正批次后） |
| EV-086 | F-11 | `tests/numeric/test_mechanics.py` | 112-121 | （低，已证实）圈层常量用子串存在性断言，「注释里写个 5.6… | 未核（待订正批次后） |
| EV-087 | F-12 | `docs/qa/AT-P0-1-reading-trust.md` | 83 | （低，已证实）文档里的套件用例数（83 / 74）与实测 8… | 未核（待订正批次后） |
| EV-088 | F-13 | `tests/numeric/tools/freeze_android_baseline.ps1` | 70-80 | （低，已证实）基线冻结脚本无条件声明「本基线含 disc-d… | 未核（待订正批次后） |
| EV-089 | F-14 | `tests/numeric/tools/at_p0_0_check.ps1` | 110-124 | （低，已证实）`at_p0_0_check.ps1` 计算了… | 未核（待订正批次后） |
| EV-090 | F-15 | `tests/numeric/test_plates.py` | 65-72 | （低，已证实）两处注释与基线事实不符（TYPE_9「推定」、… | 未核（待订正批次后） |
| EV-091 | G-02 | `docs/gap-matrix.md` | 6 | 【高】`gap-matrix.md` 的状态列与基线代码事实… | 未核（待订正批次后） |
| EV-092 | G-04 | `docs/FINAL-REPORT.md` | 11 | 【高】「18 项 AT 全绿」不能从基线复核：证据未入库，脚… | 未核（待订正批次后） |
| EV-093 | G-05 | `docs/FINAL-REPORT.md` | 5 | 【高】「未确认 0 / 无一条说不清」与同文件未取证条目直接… | 未核（待订正批次后） |
| EV-094 | G-03 | `docs/FINAL-REPORT.md` | 12 | 【中】`FINAL-REPORT.md` 核心证据列指向不存… | 未核（待订正批次后） |
| EV-095 | G-07 | `tests/numeric/tools/check_mechanics.py` | 19 | 【中】「19 项全部有来源、0 项估计」是关键词扫描，不是溯… | 未核（待订正批次后） |
| EV-096 | G-08 | `app/MECHANICS.md` | 32-34 | 【中】`MECHANICS.md` 的地址类声明依赖库外 `… | 未核（待订正批次后） |
| EV-097 | G-10 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` | 273 | 【中】用户可见的知识库文案与数值档案/DEX 表冲突（D-0… | 未核（待订正批次后） |
| EV-098 | G-11 | `app/MECHANICS.md` | 166-168 | 【中】`MECHANICS.md` 早期章节与 R1/R2 … | 未核（待订正批次后） |
| EV-099 | G-17 | `docs/qa/device-method.md` | 43-44 | 【中】航向注入命令的键名在文档里自相矛盾：`--ef hea… | 未核（待订正批次后） |
| EV-100 | G-18 | `docs/qa/AT-P0-2-capture-record.md` | 35-39 | 【中】AT-P0-2 的 P2-02（地图/3D 截图）在基… | 未核（待订正批次后） |
| EV-101 | G-19 | `docs/qa/AT-P0-1-reading-trust.md` | 82 | 【中】AT-P0-1 的 RT-010 指向已被 `devi… | 未核（待订正批次后） |
| EV-102 | G-12 | `docs/qa/bugs.md` | 271-273 | 【低】`bugs.md` 的 OBS-001/OBS-002… | 未核（待订正批次后） |
| EV-103 | G-13 | `scripts/smoke.ps1` | 11 | 【低】数值测试条数在文档里有 74 / 75 / 83 / … | 未核（待订正批次后） |
| EV-104 | G-14 | `app/README.md` | 24 | 【低】`app/README.md` 的 3D 实现描述与基… | 未核（待订正批次后） |
| EV-105 | G-20 | `docs/qa/AT-P0-4-3d-pick.md` | 11 | 【低】AT-P0-4 两份文档的用例数与 ID 互相打架 | 未核（待订正批次后） |
| EV-106 | G-21 | `docs/qa/baseline-android/round-d5/variant-p01/manifest.md` | 8-9 | 【低】`variant-p01\manifest.md` 含… | 未核（待订正批次后） |
| EV-107 | G-22 | `docs/product/PRD-v0.1.md` | 361 | 【低】PRD「代码与 `8cb57f3` 零差异」不成立（测… | 未核（待订正批次后） |
| EV-108 | K-01 | `app/android/app/src/main/res/layout/activity_main.xml` | 14-49 | 底部三 Tab/罗盘浮层（母条 B-03+B-04） | 未核（待订正批次后） |
| EV-109 | G-23 | `docs/gap-matrix.md`；`app/android/app/src/main/assets/map.html` | `132`；`342-345`；`370-373` | 档案写坐向单态/基线双态（低，v0.2 新增） | 未核（待订正批次后） |

---

## 8 附录

### 8.1 已读文件清单

| 报告 | 清单位置 | 备注 |
|---|---|---|
| `10-M1-desktop.md` | §7 | 3 文件全读 |
| `11-M2-android.md` | §7/§8 | 25 文件 + 跨模块取证读取 |
| `20-M3-map.md` | §9 | M3 3/3；交叉文件 11 个全读；部分读取逐条列「读了多少、为何停」 |
| `30-M4-server.md` | §15 | M4 26/26，跨模块仅读片段并逐文件说明 |
| `40-M5-tests.md` | §11 | 22/22 逐行 |
| `50-M6-docs.md` | §6 | 42 文件触碰：24 Markdown 全读、PNG/XML 哈希、probe.csv 全读 |

### 8.2 编号映射

- Q 统一编号：`Q-01…Q-16`，原编号（A-Q01/B-Q01/C-18/H-02/M4 Q-01/F Q-01/G-15…）逐条见 60 §4.3。
- delta：15 条转交叉引用/撤销/关闭 + B-10 升级 + E-04 维持，逐条见 60 §5。
- 交叉引用：13 条，见 60 §4.2。

### 8.3 时间线

| 时间（UTC+8） | 事件 |
|---|---|
| 11-05 06:04 | 基线冻结（`d752efa` / `review-20261105`） |
| 11-05 06:20 | 审查矩阵下发（204 文件） |
| 11-05 06:29–06:39 | 六份模块报告第一批+第二批落盘 |
| 11-05 06:40 | 60 索引快照（SHA256 见 60 §1） |
| 11-05 06:46 | 60 初版（113 条） |
| 11-05 06:5x | 总监裁定；60 重算为 107 条；99 候选稿 v0.1 |
| 11-05 07:02 | 六份报告订正；G-23 新立（低）；60 重算为 108 条 |
| 11-05 08:0x | 99 候选稿 v0.2（108 条、EV-109、路径统一） |
| 11-05 08:05 | 总监签字前核验：91 哈希绑定重绑（461/`2B8F87A2` → 475/`E7317631`，91 追加 §12 delta 后未重绑）；60 §4.1 108 行/259 处 `file:line` 机械核验 0 越界；抽样 13 条逐字回源全部命中 |

### 8.4 签字

| 角色 | 姓名 | 结论 | 日期 |
|---|---|---|---|
| 技术总监 | 技术总监 | **通过并签字**（108 条 L1 母条；机械核验 259 处引用 0 越界；13 条抽样逐字回源全中；未覆盖范围与已知缺口已在 §6 声明） | 2026-11-05 |
| 汇总成文 | LC审成文 | 候选稿 v0.2（108 条，545 行） | 2026-11-05 |

---

> **候选稿边界**：本稿锁定「哪些条目进正式清单、级别多少、母条是谁、总数多少」；不锁定待订正的代码行号与片段。订正批次并入、总监抽样追溯通过后，本稿升为终稿。

---

## 9 变更日志（v0.1 → v0.2）

| # | 位置 | 改动 |
|---|---|---|
| 1 | §1 状态句、§1.3 口径表、§7 EV、§9 | L1 107→108（低 39→40）；L1+L2+Q+R+K 144→145 |
| 2 | §2.3 低 | 插入 G-23（`docs/gap-matrix.md:132`，低） |
| 3 | §2.1 | 标题改为「阻断 1 + 高 20 = 21 条」；B-10 标题按订正后报告取「[高 · 母条]」 |
| 4 | §2 全表、§5、§7 | 路径统一为仓库相对全路径；A-01 标注调用点/实现；Q-05/Q-16/E-08 路径按 91 §12.4 改 |
| 5 | §1.2 | 90 改为「20 正式 + 6 交叉引用」；91 改为「116 已证实/115 通过/1 剔除已回补」 |
| 6 | §1.6（新增） | 绑定六份报告 + 90/91/60 当前哈希 |
| 7 | §7 | EV-108 保留 K-01，G-23 顺延 EV-109 |
| 8 | §2.3、§5、§7 | 证据复核意见：Q-05/Q-16 恢复「证据状态=待确认」并把两处路径合并回「线索」列；G-23 与 EV-109 补基线双态代码行 `map.html:342-345`、`:370-373` |

> 本版为 diff 版，不重写正文；八字段正文并入与签字仍在订正批次复核后。
