# 山水罗盘 LC · 总报告骨架与全量条目索引（60 · v1.1）

> **性质：唯一计数账。** 本文件按技术总监 2026-11-05 裁定与新增 G-23 重算，输出正式条目（母条）、交叉引用、辅助清单三层计数、delta 表与 `99-FINAL-REVIEW.md` 骨架。
> **不下技术结论、不改模块报告原文、不新增/删除条目。** 级别与证据状态来自模块报告；裁定改变级别/归属的，逐条记在 §5 delta。
> 基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`），工作区 `E:\APPDetector\review-base`。
> 抽取快照：2026-11-05 07:02（UTC+8，订正后六份报告），SHA256 见 §1；旧 06:40 快照作废。

---

## 0 三层计数口径（总监裁定，本报告只认这一份账）

| 层级 | 定义 | 本稿条数 | 去向 |
|---|---|---|---|
| **L1 正式条目** | 同一缺陷只计一条母条；跨模块同因全部归并到母条 | **108**（阻断 1 / 高 20 / 中 47 / 低 40） | `99-FINAL-REVIEW.md` §2 |
| **L2 交叉引用** | 母条之外的同因表述，单独列出、**不进 L1**；已撤/已关闭同样单列 | 16 行（交叉引用 13 + 已撤 2 + 已关闭 1） | 99 §2 条末、本文件 §4.2 |
| **L3 辅助清单** | 待确认 Q / 风险 R / 已知缺陷 K 各自单列，不混入 L1 | Q 16 / R 4 / K 1 | 99 §3、§4、§5 |

执行规则：

1. **只认母条**：同一缺陷在多份报告出现时，只保留母条计入 L1。
2. **同因冲突取高**：跨模块定级有分歧时，母条取高一级，条目保留另一侧定级。
3. **母条归实现模块**：双端同源归 B（Android 主产品线）；只在桌面成立的（A-01/A-02）归 A。
4. **账从本文件出**：90/91 只验 delta；`113`（初版）、`110`（审计估算）、`107`（候选 v0.1）均作废。

**与 v1.0（107 条）的差异**：新增 `G-23`（低，`docs/gap-matrix.md:132`「当前实现」仍写坐向单态，与基线双态不符；来自 `90-verify.md` §11.9，总监定 G-23）。L1 107 → **108**，低 39 → 40。

---

## 1 来源报告与快照

**命名**：`10-M1-desktop.md`（A/M1）、`11-M2-android.md`（B/M2），与 `98-report-conventions.md` §2 一致。

| 前缀 | 文件（相对 `docs/review/`） | 行数 | SHA256（前 16 位） | 报告条目 | 报告自述（订正后） |
|---|---|---|---|---|---|
| A | `10-M1-desktop.md` | 624 | `ABC2C291C3DCDF3A` | 12 | 已证实 7 + 交叉引用 2（A-03/A-04）+ 高可疑 1 + 待确认 2 |
| B | `11-M2-android.md` | 1046 | `72F8D15352B1585F` | 24 | 已证实 20（阻断1/高4/中9/低7，含 B-18；本账按裁定把 B-18 归 C-12）+ 已撤 1 + 高可疑 1 + 待确认 2 |
| C | `20-M3-map.md` | 1180 | `3E37C5C75AE3310D` | 34 | 计条 31 + 交叉引用 3（C-02/C-07/C-16） |
| E | `30-M4-server.md` | 989 | `EC1AF12F39589FA6` | 29 | 正式 24（高4/中7/低13）+ E-06 交叉 + H-01 撤 + H-02 交叉 + Q-01 待确认 + Q-02 关闭 |
| F | `40-M5-tests.md` | 856 | `38E534C9F921364A` | 22 | 正式 14（高2/中7/低5）+ F-03 交叉 + Q 3 + R 4 |
| G | `50-M6-docs.md` | 1034 | `5F9D2760DCE5EA16` | 22 | 正式 18（高3/中9/低6）+ 交叉 2（G-01/G-06）+ 待确认 2 |
| — | `90-verify.md` §11.9 | — | — | +1 | 新立 `G-23`（低，总监定） |
| **合计** | | | | **144** | 143 条报告条目 + G-23 |

**M6/M7 归属声明（裁定）**：`app/Dockerfile`（21 行）、`app/docker-compose.yml`（19 行）、`app/.dockerignore`（4 行）由 **M4 全读**（M4 26/26、2812 行），覆盖成立；M6 写「归属 M4，未读、不下结论」，索引归属改 M4。这是「读一半当全量」的唯一一例。

---

## 2 编号与去重裁定

1. **沿用报告编号**：A/B/C/E/F/G 不改；已撤、已关闭保留编号；新立 `G-23`。
2. **Q 统一编号**：`Q-01…Q-16`，消 M4/M5 的 `Q-01` 重号，原编号保留（§4.3）。
3. **已落实裁定**：
   - `B-10` 升为高；`A-03`、`F-03` → 交叉引用 B-10。
   - `K-01` 母条 = `B-03` + `B-04`；`C-07`、`G-01` → 交叉引用 K-01。
   - `C-02`→B-02、`G-06`→C-01、`H-02`→A-01、`E-06`→F-01、`G-09`→C-06、`B-18`→C-12 全部转交叉引用。
   - `A-04`→B-09、`C-13`→B-11、`C-16`→B-05 按「母条归实现模块 + 冲突取高」合并。
   - `E-04` 维持高；`B-20` 保留（订正为四份后回清单）。
4. **未硬并**：`F-12` 与 `G-13` 证据侧重点不同，保留两条；`C-12` 保留为 `B-18` 母条。

---

## 3 计数对照

| 口径 | 阻断 | 高 | 中 | 低 | 无级别 | 合计 |
|---|---|---|---|---|---|---|
| 抽取的已证实条目（含 G-23；含交叉引用/已撤） | 1 | 21 | 51 | 41 | 7 | 121 |
| L1 正式条目（母条，本表为准） | 1 | 20 | 47 | 40 | 0 | 108 |
| 差额 | 0 | −1 | −4 | −1 | −7 | −13 |

差额 13 条 = 交叉引用 12（A-03、A-04、B-18、C-02、C-07、C-13、C-16、E-06、F-03、G-01、G-06、G-09）+ 已撤 1（B-01）。无级别 7 条为已撤/交叉引用条目标题未带等级（B-01、C-02、C-07、C-16、F-03、G-01、G-06）。`B-10` 中→高已在 L1 内体现。

分模块（L1）：A 7（高2/中2/低3）· B 19（阻断1/高4/中8/低6）· C 26（高5/中15/低6）· E 24（高4/中7/低13）· F 14（高2/中7/低5）· G 18（高3/中8/低7，含 G-23）。

辅助清单：L2 16 行（§4.2）· Q 16（§4.3）· R 4（§4.4）· K 1（§4.5）。L1+L2+Q+R+K = 108+16+16+4+1 = **145**（143 条报告条目 + G-23 + K-01）。

---

## 4 全量条目索引

### 4.1 正式缺陷条目（母条计入：108 条）

| 编号 | 级别 | 证据状态 | 涉及文件行号 | 母条/归并 | 来源报告 | 备注 |
|---|---|---|---|---|---|---|
| A-01 | 高 | 已证实 | 调用点 `app/src/main.rs:613-616`；实现 `app/src/software.rs:237-249`、`app/src/software.rs:310-321`（共16处） | 母条（桌面门禁） | 10-M1-desktop.md | [高] `--screenshot` 只走 `software.rs` 独立渲染器，… |
| A-02 | 高 | 已证实 | `app/src/main.rs:447-455`；`app/src/dial.rs:248-253`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:236`（共7处） | — | 10-M1-desktop.md | [高] 兼向邻山在正北区间回绕错误：显示「兼向坤」而应为「兼向壬」 |
| A-05 | 中 | 已证实 | `app/src/main.rs:316-321`；`app/src/dial.rs:65-70`；`app/src/main.rs:618-623`（共4处） | — | 10-M1-desktop.md | [中] 窗口没有最小尺寸约束，中央面板可用宽度可为负；`central_dial` … |
| A-06 | 中 | 已证实 | `app/src/main.rs:586-592`；`app/src/main.rs:604-607` | — | 10-M1-desktop.md | [中] 命令行参数解析：缺值/未知参数静默忽略，`--dump-rings` 非法数… |
| A-07 | 低 | 已证实 | `app/src/main.rs:324-329` | — | 10-M1-desktop.md | [低] 拖拽转盘只吃水平位移，垂直拖动完全无响应；灵敏度 0.5°/px 为硬编码常… |
| A-08 | 低 | 已证实 | `app/src/main.rs:374-377`；`app/src/dial.rs:241` | — | 10-M1-desktop.md | [低] 「朝向」滑块范围 `0.0..=360.0` 与归一化朝向冲突：可停在 36… |
| A-09 | 低 | 已证实 | `app/src/main.rs:182-190`；`app/src/dial.rs:12-17`；`app/src/software.rs:9-12`（共15处） | — | 10-M1-desktop.md | [低] 死代码与未使用导入：`wangxiang/borrow_branch/nay… |
| B-02 | 阻断 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:234-250`；`app/android/app/src/main/java/com/luopan/fengshui/DebugBridge.java:22`；`app/android/app/src/main/AndroidManifest.xml:1-30`（共11处） | 母条（导出广播注入） | 11-M2-android.md | [阻断] `DebugBridge` 用 `RECEIVER_EXPORTED` 注… |
| B-03 | 高 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:79-90`；`app/android/app/src/main/assets/map.html:1168-1182`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:262-265`（共8处） | — | 11-M2-android.md | [高] 三 Tab 造成同一页面两份实例：「地图」Tab 与「罗盘」Tab 的底图是… |
| B-04 | 高 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:267-274`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:126-154`；`app/android/app/src/main/assets/map.html:13`（共6处） | — | 11-M2-android.md | [高] 罗盘浮层不在「地图中心」：贴顶部、占屏宽 78%、整块吞掉触摸，且盖住 `m… |
| B-05 | 高 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:99-104`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:106-118`；`docs/gap-matrix.md:121`（共7处） | 母条（触摸停跟随，取高） | 11-M2-android.md | [高] 罗盘上任意点按（无需拖动）即 `followSensor = false`，… |
| B-10 | 高 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:298-299`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:641`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:229`（共7处） | 母条（升为高） | 11-M2-android.md | [高 · 母条] 「正/兼向」双源：盘心用公式 `Reading.jian`，读数行… |
| B-06 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:725-732`；`app/android/app/src/main/assets/map.html:1168-1182` | — | 11-M2-android.md | [中] 前后台切换不暂停 WebView、不销毁 WebView：`onPause`… |
| B-07 | 中 | 已证实 | `app/android/app/src/main/AndroidManifest.xml:19-22`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:270-274`；`app/android/app/src/main/AndroidManifest.xml:19-22`（共4处） | — | 11-M2-android.md | [中] `configChanges="orientation｜screenSize… |
| B-08 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:92-95`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:720-722`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:652-655`（共4处） | — | 11-M2-android.md | [中] 没有 rotation vector 传感器时零提示：读数行永远空白、盘心恒… |
| B-09 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:198-209`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:117-118`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:175-177`（共6处） | 母条（分金/72龙 offset） | 11-M2-android.md | [中 · 母条] 百二十分金 / 穿山七十二龙 刻度同样忽略 `offset`，刻度… |
| B-11 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:262-266`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:682-686`；`app/android/app/src/main/assets/map.html:424-429`（共23处） | 母条（双 WebView 注入/状态） | 11-M2-android.md | [中] 双 WebView 之间没有任何状态同步：同一「地图」在两页各有独立的中心/… |
| B-12 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:98-102`；`app/android/app/src/main/assets/map.html:68` | — | 11-M2-android.md | [中] 首启无条件请求定位权限，与用户动作无关；拒绝后无任何提示，`onReques… |
| B-13 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:744-749` | — | 11-M2-android.md | [中] 切到「地图」「知识」Tab 后传感器回调仍全速执行 `updateReadi… |
| B-14 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:385-390` | — | 11-M2-android.md | [中] WebView 对所有来源自动授予地理定位：任意页面（含经 B-02 注入的… |
| B-15 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:400-405` | — | 11-M2-android.md | [低] 截图入口固定在罗盘页：在「地图」/「知识」Tab 触发（按钮或 `captu… |
| B-16 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:456-461` | — | 11-M2-android.md | [低] `looksBlank` 只采 4 个像素判黑图：单点高光即可让全黑图通过 |
| B-17 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:737-743` | — | 11-M2-android.md | [低] `onSensorChanged` 内 `if (injectedHeadi… |
| B-19 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java:25-28`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:36-37`；`app/android/app/src/main/java/com/luopan/fengshui/CompassListActivity.java:87-92` | — | 11-M2-android.md | [低] `dial0.png` 与 `dial_full_0.png` 字节完全相同… |
| B-20 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/SensorMath.java:14-18`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:705-709`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:144-148`（共10处） | — | 11-M2-android.md | [低] 同一「角度归一化」实现被复制四份（Android 包内：`SensorMat… |
| B-21 | 低 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:640`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:656-659`；`docs/product/PRD-v0.1.md:100`（共13处） | — | 11-M2-android.md | [低] 读数行 `String.format` 未指定 Locale，状态条却用 `… |
| C-01 | 高 | 已证实 | `app/android/app/src/main/assets/map.html:92`；`docs/map-bridge.md:41`；`docs/TEAM_CHARTER.md:107`（共5处） | 母条（key 明文） | 20-M3-map.md | 天地图 APIKEY 明文入库、入包，且用的是原版 key（违反 D-006） |
| C-03 | 高 | 已证实 | `app/android/app/src/main/assets/map.html:518-520`；`app/android/app/src/main/assets/map.html:771-777`；`app/android/app/src/main/assets/map.html:578-595`（共10处） | — | 20-M3-map.md | 3D 地形块最外一行/列 193 个顶点高程恒为 0（每块边缘一圈悬崖，pick 命… |
| C-04 | 高 | 已证实 | `app/android/app/src/main/assets/map.html:424-434`；`app/android/app/src/main/assets/map.html:436-449`；`app/android/app/src/main/assets/map.html:494-502`（共4处） | — | 20-M3-map.md | 2D 拖拽方向与手指相反，且与 3D 拖拽方向相反 |
| C-05 | 高 | 已证实 | `app/android/app/src/main/assets/map.html:517`；`app/android/app/src/main/assets/map.html:751-810`；`app/android/app/src/main/assets/map.html:781-788`（共11处） | — | 20-M3-map.md | DEM 任一瓦片失败即整块拒绝，3D 无自动降级；旧块还会滞留在错误位置 |
| C-06 | 高 | 已证实 | `docs/map-bridge.md:112-117`；`docs/map-bridge.md:201`；`app/android/app/src/main/assets/map.html:1004-1010`（共8处） | 母条（map-bridge 契约） | 20-M3-map.md | `map-bridge.md` §5 的四类事件在实现中不存在，§5/§7 的桥状态… |
| C-08 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:1208-1233`；`app/android/app/src/main/assets/map.html:392-403`；`app/android/app/src/main/assets/map.html:209` | — | 20-M3-map.md | 「贴地」相机没有地形高度/遮挡约束，山地会把眼点压进山体；低缩放时「贴地」实际是数十… |
| C-09 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:751-776`；`app/android/app/src/main/assets/map.html:994-995`；`app/android/app/src/main/assets/map.html:1323-1330`（共4处） | — | 20-M3-map.md | 纬度 >≈84.8° 时 DEM 瓦片 y<0 全部为空 → 3D 必然失败；`se… |
| C-10 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:1122-1125`；`app/android/app/src/main/java/com/luopan/fengshui/LCAndroid.java:44-50` | — | 20-M3-map.md | 跨反经线时 pick 的 lng 可越界（<-180），页面已 emit，Java … |
| C-11 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:830`；`app/android/app/src/main/assets/map.html:1099`；`app/android/app/src/main/assets/map.html:1128`（共5处） | — | 20-M3-map.md | `VEXAG` 与 pick 是同一变换空间（无缺陷）；但 pick 的高度用逐像素… |
| C-12 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:40`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:377-383`；`app/android/app/src/main/java/com/luopan/fengshui/LCAndroid.java:29-61`（共5处） | 母条（mapMarker 共享未读） | 20-M3-map.md | 双 WebView：`__LC3D` 互不污染；Java 侧 `mapMarker`… |
| C-14 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:148-176`；`app/android/app/src/main/assets/map.html:436-449`；`app/android/app/src/main/assets/map.html:494-502` | — | 20-M3-map.md | 2D 每帧 innerHTML 重建约 30 个瓦片 `<img>`；无复用/无缓存… |
| C-15 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:601`；`app/android/app/src/main/assets/map.html:874`；`app/android/app/src/main/assets/map.html:531`（共4处） | — | 20-M3-map.md | 单实例 3D 资源量级约 40–60 MB（推算）；无 `webglcontextl… |
| C-22 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:455-465`；`app/android/app/src/main/assets/map.html:148-176`；`app/android/app/src/main/assets/map.html:424-434`（共4处） | — | 20-M3-map.md | 2D 立向点按屏幕像素定位，平移/缩放后不跟地图走 |
| C-23 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:237-240`；`app/android/app/src/main/assets/map.html:424-433`；`app/android/app/src/main/assets/map.html:986-993` | — | 20-M3-map.md | 2D 旋转/坐向后拖拽未按 bearing 分解 |
| C-24 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:405-411`；`app/android/app/src/main/assets/map.html:237-240`；`app/android/app/src/main/assets/map.html:344-346` | — | 20-M3-map.md | 2D 指针角度未减 bearing（3D 减了） |
| C-25 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:268-283`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:258-363`；`app/android/app/src/main/assets/map.html:66` | — | 20-M3-map.md | 「全屏」只隐藏 WebView 内控件，原生罗盘/面板/底栏仍在 |
| C-26 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:211`；`app/android/app/src/main/assets/map.html:331-337`；`app/android/app/src/main/assets/map.html:1307`（共4处） | — | 20-M3-map.md | 3D 默认贴面是影像，角注/截图角注仍显示「地形」 |
| C-27 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:1516-1517`；`app/android/app/src/main/assets/map.html:901`；`docs/qa/AT-P0-4-3d-pick.md:21-22`（共7处） | — | 20-M3-map.md | AT 断言弱于文档 + 真机脚本只数 ≥5 条 pick + 环境失败被判 FAIL |
| C-29 | 中 | 已证实 | `app/android/app/src/main/assets/map.html:1004-1010`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:385-390`；`docs/qa/AT-P0-4-3d-probe.md:17` | — | 20-M3-map.md | 页面错误不可观测：`emit` 静默吞异常、无 `window.onerror`、未… |
| C-34 | 中 | 已证实 | `docs/qa/AT-P0-4-3d-probe.md:5`；`app/android/app/src/main/assets/map.html:1334-1344`；`app/android/app/src/main/assets/map.html:1354-1362`（共15处） | — | 20-M3-map.md | 第二份 AT（probe）要求的 `__LC3D.probe/pick/state(… |
| C-17 | 低 | 已证实 | `docs/map-bridge.md:72`；`app/android/app/src/main/assets/map.html:467-473`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:31` | — | 20-M3-map.md | `map-bridge.md` 4 处实现细节漂移 |
| C-28 | 低 | 已证实 | `app/android/app/src/main/assets/map.html:1090-1102` | — | 20-M3-map.md | pick 射线离开地形块后不提前退出，单次点选固定跑满 ~1140 步 |
| C-30 | 低 | 已证实 | `app/android/app/src/main/assets/map.html:1486-1493`；`app/android/app/src/main/assets/map.html:1477-1483`；`app/android/app/src/main/assets/map.html:209`（共5处） | — | 20-M3-map.md | `#in3d&sit` 入口硬编码 `setHeading(90)`；`&z=<9`… |
| C-31 | 低 | 已证实 | `app/android/app/src/main/assets/map.html:207-210`；`app/android/app/src/main/assets/map.html:226-234` | — | 20-M3-map.md | 进 3D 强制 zoom≥9，返回 2D 不恢复原 zoom |
| C-32 | 低 | 已证实 | `app/android/app/src/main/assets/map.html:899-903`；`app/android/app/src/main/assets/map.html:1260`；`app/android/app/src/main/assets/map.html:1274` | — | 20-M3-map.md | 贴面构建 `ok<30` 即放弃且不重试 |
| C-33 | 低 | 已证实 | `app/android/app/src/main/assets/map.html:91-114`；`app/android/app/src/main/assets/map.html:1322-1560` | — | 20-M3-map.md | `map.html` 单文件 1605 行、20+ 全局可变状态、无模块边界；测试入… |
| E-01 | 高 | 已证实 | `backend/src/server.js:13`；`backend/src/server.js:36`；`backend/src/server.js:299-301`（共8处） | — | 30-M4-server.md | （高，已证实）无鉴权 + `Access-Control-Allow-Origin:… |
| E-02 | 高 | 已证实 | `backend/src/db.js:11-12`；`backend/src/server.js:303-309`；`backend/src/db.js:148-151`（共6处） | — | 30-M4-server.md | （高，已证实）案例 `id` 是全局主键，但「已存在」检查按 owner 过滤 → … |
| E-03 | 高 | 已证实 | `backend/src/server.js:380-386`；`backend/src/server.js:387-399`；`backend/src/db.js:144`（共5处） | — | 30-M4-server.md | （高，已证实）`/v1/sync/push` 非原子：逐条独立提交，后续条目校验失败… |
| E-04 | 高 | 已证实 | `backend/src/server.js:69-78`；`backend/src/server.js:476-479`；`backend/src/server.js:14`（共4处） | — | 30-M4-server.md | （高，已证实）请求体超限时先 `req.destroy()`，契约声明的 413 永… |
| E-05 | 中 | 已证实 | `tools/mechanics/gen-mechanics.ps1:234-243` | — | 30-M4-server.md | （中，已证实）`gen-mechanics.ps1 -Check` 可被大小写改写 … |
| E-07 | 中 | 已证实 | `scripts/build-desktop.ps1:147-150`；`scripts/README.md:18`；`tests/numeric/tools/check_mechanics.py:19` | — | 30-M4-server.md | （中，已证实）`build-desktop.ps1` 的「硬来源」正则只有英文，文档… |
| E-08 | 中 | 已证实 | `backend/src/server.js:10`；`backend/docker-compose.yml:10`；`backend/api.md:5`（共6处） | — | 30-M4-server.md | （中，已证实）`HOST` 默认 `0.0.0.0`，与文档「仅本机可用」只在 co… |
| E-09 | 中 | 已证实 | `backend/src/server.js:314-316`；`backend/src/server.js:316` | — | 30-M4-server.md | （中，已证实）畸形百分号编码抛 `URIError` → 500（应 400） |
| E-10 | 中 | 已证实 | `backend/src/server.js:89-92`；`backend/api.md:37` | — | 30-M4-server.md | （中，已证实）无 `Content-Type` 头时接受请求体，与 415 契约不符 |
| E-11 | 中 | 已证实 | `backend/api.md:244`；`backend/README.md:13` | — | 30-M4-server.md | （中，已证实）文档的「一键起」指向开发脏树 `E:\APPDetector\outp… |
| E-12 | 中 | 已证实 | `backend/scripts/selfcheck.mjs:24-27`；`backend/api.md:248` | — | 30-M4-server.md | （中，已证实）后端完全没有自动化测试，唯一自检依赖运行中的服务且不覆盖错误路径 |
| E-13 | 低 | 已证实 | `backend/src/db.js:116-120`；`backend/src/db.js:238-248`；`backend/src/server.js:160` | — | 30-M4-server.md | （低，已证实）`changes` 与 `shares` 无清理策略，长期无界增长 |
| E-14 | 低 | 已证实 | `backend/src/server.js:468-470` | — | 30-M4-server.md | （低，已证实）请求日志记录完整 URL，分享 token 与 `owner_id` … |
| E-15 | 低 | 已证实 | `backend/src/server.js:261-270`；`backend/src/db.js:124-125` | — | 30-M4-server.md | （低，已证实）`/health` 对未鉴权调用方暴露全局案例/分享计数 |
| E-16 | 低 | 已证实 | `backend/src/db.js:165-168` | — | 30-M4-server.md | （低，已证实）空 patch 的 PUT 也推进 `rev` 并写变更日志 |
| E-17 | 低 | 已证实 | `backend/src/db.js:264-267` | — | 30-M4-server.md | （低，已证实）重复撤销分享仍返回 200 |
| E-18 | 低 | 已证实 | `backend/api.md:95` | — | 30-M4-server.md | （低，已证实）`backend/api.md` 声称 `smoke.ps1` 依赖 … |
| E-19 | 低 | 已证实 | `scripts/build-android.ps1:94`；`app/android/README.md:14-16` | — | 30-M4-server.md | （低，已证实）Gradle wrapper 只有 properties、没有 `gr… |
| E-20 | 低 | 已证实 | `scripts/check-staged-paths.ps1:156-157` | — | 30-M4-server.md | （低，已证实）提交守卫把已跟踪的 `craft.json` 列为 always-de… |
| E-21 | 低 | 已证实 | `backend/scripts/selfcheck.mjs:92-96`；`backend/api.md:248` | — | 30-M4-server.md | （低，已证实）`selfcheck.mjs` 在生产卷上留测试数据 |
| E-22 | 低 | 已证实 | `app/android/docker-compose.yml:5-9` | — | 30-M4-server.md | （低，已证实）Android 构建镜像以 root 运行、bind-mount 工作… |
| E-23 | 低 | 已证实 | （报告未给文件行号） | — | 30-M4-server.md | （低，已证实）后端镜像基础标签浮动，构建不可完全复现 |
| E-24 | 低 | 已证实 | `backend/src/server.js:487-489`；`backend/src/db.js:98-103` | — | 30-M4-server.md | （低，已证实）全同步 SQLite 调用阻塞事件循环，且无限流/连接上限 |
| E-25 | 低 | 已证实 | `scripts/build-android.ps1:242` | — | 30-M4-server.md | （低，已证实）Android 真机门禁的 logcat 窗口只有 5 秒 |
| F-01 | 高 | 已证实 | `tests/numeric/run_all.py:31-35`；`tests/numeric/test_ends_parity.py:44-47`；`tests/numeric/test_sensor_math.py:46-51`（共8处） | 母条（数值门禁假绿） | 40-M5-tests.md | （高，已证实）无 JDK 时数值套件仍 `RESULT: PASS`，AT `RT-… |
| F-02 | 高 | 已证实 | `tests/numeric/test_b2_golden.py:90-97`；`app/src/main.rs:637-674`；`tools/mechanics/gen-mechanics.ps1:7-9`（共5处） | — | 40-M5-tests.md | （高，已证实）780 条圈层文字与 `ring_index` 无执行级断言，B2 验… |
| F-04 | 中 | 已证实 | `tests/numeric/test_ends_parity.py:99-105`；`tests/numeric/oracle.py:191`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:221`（共11处） | — | 40-M5-tests.md | （中，已证实）`test_ends_parity` 把 60 龙名锁在教材旧值 `癸… |
| F-05 | 中 | 已证实 | `tests/at/run-final.ps1:306-311`；`tests/numeric/tools/at_p0_0_check.ps1:73-78`；`tests/at/run-final.ps1:306-311`（共5处） | — | 40-M5-tests.md | （中，已证实）AT 缺 prefs 时按「代码默认 true」判 PASS（两处） |
| F-06 | 中 | 已证实 | `tests/at/run-final.ps1:490-501` | — | 40-M5-tests.md | （中，已证实）P2-08「顶部两行」宽松回退匹配后仍判 PASS |
| F-07 | 中 | 已证实 | `tests/at/run-final.ps1:530-547`；`docs/qa/AT-P0-2-capture-record.md:16` | — | 40-M5-tests.md | （中，已证实）P2-01 点了界面按钮但没产出文件，被判 BLOCKED 而非 FA… |
| F-08 | 中 | 已证实 | `tests/at/run-final.ps1:583-589` | — | 40-M5-tests.md | （中，已证实）P2-05 只校验分享抽屉存在，不校验被分享文件与相册一致 |
| F-09 | 中 | 已证实 | `tests/at/run-final.ps1:375-379`；`docs/qa/AT-final-round.md:20` | — | 40-M5-tests.md | （中，已证实）`PASS(等价路径)` 状态不进任何计数，汇总与退出码都看不见它 |
| F-10 | 中 | 已证实 | `docs/qa/AT-P0-0-feature-switch.md:51`；`docs/qa/AT-P0-1-reading-trust.md:48`；`docs/qa/AT-P0-4-3d-probe.md:53` | — | 40-M5-tests.md | （中，已证实）三份 AT 文档引用的 `tests\at\run-at.ps1` 在… |
| F-11 | 低 | 已证实 | `tests/numeric/test_mechanics.py:112-121` | — | 40-M5-tests.md | （低，已证实）圈层常量用子串存在性断言，「注释里写个 5.625」即可通过 |
| F-12 | 低 | 已证实 | `docs/qa/AT-P0-1-reading-trust.md:83`；`docs/qa/baseline-01.md:31`；`docs/qa/bugs.md:23`（共4处） | — | 40-M5-tests.md | （低，已证实）文档里的套件用例数（83 / 74）与实测 86 不一致 |
| F-13 | 低 | 已证实 | `tests/numeric/tools/freeze_android_baseline.ps1:70-80` | — | 40-M5-tests.md | （低，已证实）基线冻结脚本无条件声明「本基线含 disc-dial-list.png… |
| F-14 | 低 | 已证实 | `tests/numeric/tools/at_p0_0_check.ps1:110-124` | — | 40-M5-tests.md | （低，已证实）`at_p0_0_check.ps1` 计算了「磁北/真北」行却不参与… |
| F-15 | 低 | 已证实 | `tests/numeric/test_plates.py:65-72`；`tests/numeric/test_plates.py:71-72`；`tests/numeric/test_plates.py:75-79` | — | 40-M5-tests.md | （低，已证实）两处注释与基线事实不符（TYPE_9「推定」、JIAZI「xfail … |
| G-02 | 高 | 已证实 | `docs/gap-matrix.md:6`；`docs/gap-matrix.md:9-18`；`docs/FINAL-REPORT.md:60-69`（共29处） | — | 50-M6-docs.md | 【高】`gap-matrix.md` 的状态列与基线代码事实不符，且统计数字被 `F… |
| G-04 | 高 | 已证实 | `docs/FINAL-REPORT.md:11`；`docs/qa/AT-final-round.md:21`；`tests/at/run-final.ps1:735-742` | — | 50-M6-docs.md | 【高】「18 项 AT 全绿」不能从基线复核：证据未入库，脚本不校验用例清单 |
| G-05 | 高 | 已证实 | `docs/FINAL-REPORT.md:5`；`docs/FINAL-REPORT.md:69`；`docs/gap-matrix.md:18`（共6处） | — | 50-M6-docs.md | 【高】「未确认 0 / 无一条说不清」与同文件未取证条目直接矛盾 |
| G-03 | 中 | 已证实 | `docs/FINAL-REPORT.md:12`；`docs/FINAL-REPORT.md:23`；`docs/FINAL-REPORT.md:93`（共6处） | — | 50-M6-docs.md | 【中】`FINAL-REPORT.md` 核心证据列指向不存在的生成物，复现路径指向… |
| G-07 | 中 | 已证实 | `tests/numeric/tools/check_mechanics.py:19`；`tests/numeric/tools/check_mechanics.py:26-29`；`scripts/build-desktop.ps1:147-151`（共13处） | — | 50-M6-docs.md | 【中】「19 项全部有来源、0 项估计」是关键词扫描，不是溯源；其中 3 项与 DE… |
| G-08 | 中 | 已证实 | `app/MECHANICS.md:32-34`；`app/MECHANICS.md:276-282`；`docs/gap-matrix.md:29-46`（共5处） | — | 50-M6-docs.md | 【中】`MECHANICS.md` 的地址类声明依赖库外 `.inspect` 证据… |
| G-10 | 中 | 已证实 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:273`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:275`；`app/src/main.rs:353`（共6处） | — | 50-M6-docs.md | 【中】用户可见的知识库文案与数值档案/DEX 表冲突（D-008 文案诚信） |
| G-11 | 中 | 已证实 | `app/MECHANICS.md:166-168`；`app/MECHANICS.md:221-225`；`app/MECHANICS.md:372`（共7处） | — | 50-M6-docs.md | 【中】`MECHANICS.md` 早期章节与 R1/R2 补证矛盾且未标「已取代」… |
| G-17 | 中 | 已证实 | `docs/qa/device-method.md:43-44`；`docs/qa/AT-B2-ring-labels.md:23`；`docs/qa/AT-P0-1-reading-trust.md:34`（共5处） | — | 50-M6-docs.md | 【中】航向注入命令的键名在文档里自相矛盾：`--ef heading`（错）vs `… |
| G-18 | 中 | 已证实 | `docs/qa/AT-P0-2-capture-record.md:35-39`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:567-569`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:400-401`（共6处） | — | 50-M6-docs.md | 【中】AT-P0-2 的 P2-02（地图/3D 截图）在基线没有可执行入口，最终 … |
| G-19 | 中 | 已证实 | `docs/qa/AT-P0-1-reading-trust.md:82`；`docs/qa/device-method.md:56-57`；`docs/qa/baseline-android/manifest.md:3`（共4处） | — | 50-M6-docs.md | 【中】AT-P0-1 的 RT-010 指向已被 `device-method.md… |
| G-12 | 低 | 已证实 | `docs/qa/bugs.md:271-273`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:18-22`；`docs/qa/bugs.md:277-278`（共7处） | — | 50-M6-docs.md | 【低】`bugs.md` 的 OBS-001/OBS-002 在基线已不成立，台账仍… |
| G-13 | 低 | 已证实 | `scripts/smoke.ps1:11`；`docs/qa/AT-B2-ring-labels.md:21`；`docs/qa/AT-B2-ring-labels.md:141`（共7处） | — | 50-M6-docs.md | 【低】数值测试条数在文档里有 74 / 75 / 83 / 86 四个值，实际 86 |
| G-14 | 低 | 已证实 | `app/README.md:24`；`docs/map-bridge.md:13` | — | 50-M6-docs.md | 【低】`app/README.md` 的 3D 实现描述与基线不符 |
| G-20 | 低 | 已证实 | `docs/qa/AT-P0-4-3d-pick.md:11`；`docs/qa/AT-P0-4-3d-probe.md:6`；`docs/qa/AT-P0-4-3d-pick.md:40`（共4处） | — | 50-M6-docs.md | 【低】AT-P0-4 两份文档的用例数与 ID 互相打架 |
| G-21 | 低 | 已证实 | `docs/qa/baseline-android/round-d5/variant-p01/manifest.md:8-9` | — | 50-M6-docs.md | 【低】`variant-p01\manifest.md` 含 CR/BEL 控制字符… |
| G-22 | 低 | 已证实 | `docs/product/PRD-v0.1.md:361`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:237-243`；`docs/map-bridge.md:41` | — | 50-M6-docs.md | 【低】PRD「代码与 `8cb57f3` 零差异」不成立（测试脚本有 25/18 行… |
| G-23 | 低 | 已证实 | `docs/gap-matrix.md:132` | — | 90-verify.md | gap-matrix.md 的「当前实现」列仍写坐向单态，与基线双态不符（90 §1… |

### 4.2 交叉引用 / 已撤 / 已关闭（16 条，不进正式数）

| 编号 | 原级别 | 状态 | 涉及文件行号 | 母条 | 来源报告 | 备注 |
|---|---|---|---|---|---|---|
| A-03 | 高 | 交叉引用 | `app/src/main.rs:407`；`app/src/main.rs:144`；`app/src/main.rs:134`（共11处） | 交叉引用→B-10 | 10-M1-desktop.md | [高 · 交叉引用，不单独计数] 同一朝向，盘心「正/兼向」与右栏「正/兼向」取自两… |
| A-04 | 中 | 交叉引用 | `app/src/dial.rs:127-138`；`app/src/main.rs:135`；`app/src/dial.rs:154-157`（共21处） | 交叉引用→B-09 | 10-M1-desktop.md | [中 · 交叉引用，不单独计数] 百二十分金 / 穿山七十二龙 刻度网格忽略 `ri… |
| B-01 | — | 已撤 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:640` | — | 11-M2-android.md | [已撤·误报] 原判「读数行 `String.format` 实参错位」不成立 |
| B-18 | 低 | 交叉引用 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:40`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:377-383`；`app/android/app/src/main/assets/map.html:1006-1007`（共4处） | 交叉引用→C-12 | 11-M2-android.md | [低] D-005 单点标记未落地：`mapMarker` 只被赋值、全仓无读取点，… |
| C-02 | 高 | 交叉引用 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:184-205`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:240`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:373-383`（共7处） | 交叉引用→B-02 | 20-M3-map.md | 【交叉引用，不计条】导出调试广播可在地图 WebView 执行任意 JS / 加载任… |
| C-07 | 高 | 交叉引用 | `app/android/app/src/main/res/layout/activity_main.xml:14-49`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:69-90`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:258-274`（共8处） | 交叉引用→K-01（母条组 B-03/B-04） | 20-M3-map.md | 【交叉引用，不计条】罗盘与地图是同一 map.html 的两个 WebView 实例… |
| C-13 | 中 | 交叉引用 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:677-687`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:749` | 交叉引用→B-11 | 20-M3-map.md | 每个传感器样本向两个 WebView 各注入 2 条 JS（4 次/样本），含隐藏实… |
| C-16 | 中 | 交叉引用 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:99-104`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:127-135`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:113-118`（共9处） | 交叉引用→B-05 | 20-M3-map.md | 【交叉引用，不计条】罗盘覆盖层吃掉触摸且一触即停传感器跟随；「翻坐向」也停跟随，只有… |
| H-01 | — | 已撤 | （报告未给文件行号） | — | 30-M4-server.md | （已撤）命名卷由 root 建过导致 `USER node` 写不进 `/data`… |
| H-02 | — | 交叉引用 | `scripts/build-desktop.ps1:114-123`；`scripts/build-desktop.ps1:125-128`；`scripts/README.md:16-17` | 交叉引用→A-01 | 30-M4-server.md | （高度可疑）桌面门禁的 PASS 不能代表真实 UI 正常 |
| Q-02 | — | 已关闭 | `backend/docker-compose.yml:10`；`backend/src/server.js:112-177`；`backend/src/server.js:471-484`（共67处） | — | 30-M4-server.md | （已关闭）Docker 端口发布是否只回环 —— 实测只回环，无旁路 |
| E-06 | 中 | 交叉引用 | `scripts/smoke.ps1:105-108`；`tests/numeric/run_all.py:25-33` | 交叉引用→F-01 | 30-M4-server.md | （中，已证实）`smoke.ps1` numeric 门禁对 0 测试 / 全 sk… |
| F-03 | 高 | 交叉引用 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:226-238`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:287-299`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:301-307`（共15处） | 交叉引用→B-10 | 40-M5-tests.md | （交叉引用 → B-10；M5 正式数不计）正/兼向两条 UI 链路在同航向给出互斥… |
| G-01 | 高 | 交叉引用 | `app/android/app/src/main/res/layout/activity_main.xml:14-49`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:79-90`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:630-634`（共23处） | 交叉引用→K-01（母条组 B-03/B-04） | 50-M6-docs.md | 【交叉引用→B-03+B-04；高｜不计入 M6 正式数】底部三 Tab 与原版首页… |
| G-06 | 高 | 交叉引用 | `docs/map-bridge.md:41`；`docs/map-bridge.md:41`；`app/android/app/src/main/assets/map.html:92`（共6处） | 交叉引用→C-01 | 50-M6-docs.md | 【交叉引用→C-01；高｜不计入 M6 正式数】`map-bridge.md` 自述… |
| G-09 | 中 | 交叉引用 | `docs/map-bridge.md:112`；`docs/map-bridge.md:117`；`docs/map-bridge.md:201`（共8处） | 交叉引用→C-06 | 50-M6-docs.md | 【中】`map-bridge.md` 的桥状态与代码相反，并声明了不存在的事件类型 |

### 4.3 待确认清单（高度可疑 + 待确认：16 条）

| 统一编号 | 原编号 | 关联级别 | 证据状态 | 涉及文件行号 | 来源报告 | 备注 |
|---|---|---|---|---|---|---|
| Q-01 | A-Q01 | 中 | 高度可疑 | `app/src/main.rs:525-527`；`app/src/dial.rs:58-70`；`app/src/main.rs:316-321`（共6处） | 10-M1-desktop.md | [高度可疑 · 中] `software.rs` 与 egui 真 UI 的布局一致… |
| Q-02 | A-Q02 | — | 待确认 | `app/src/main.rs:134`；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:98` | 10-M1-desktop.md | [待确认] `TYPE_14` 正兼向表的角向基准（0° 是子山中心还是子癸界）是原… |
| Q-03 | A-Q03 | — | 待确认 | `app/src/main.rs:613-616`；`app/src/software.rs:314`；`docs/gap-matrix.md:107`（共13处） | 10-M1-desktop.md | [待确认] 验收流程是否以 `--screenshot` 产物作为通过判据（决定 A… |
| Q-04 | B-Q01 | 中 | 高度可疑 | `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:400-415` | 11-M2-android.md | [高度可疑 · 中] 截图失败路径在「非罗盘页触发」时必现，且 `looksBlan… |
| Q-05 | B-Q02 | — | 待确认 | `docs/gap-matrix.md:121`；`app/MECHANICS.md:466-468`；`docs/qa/baseline-android/manifest.md:15`（共4处） | 11-M2-android.md | [待确认] K-01 的「原版首页 = 地图 + 罗盘叠加」档案依据是否可回溯 |
| Q-06 | B-Q03 | — | 待确认 | `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:100`；`app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:198`；`docs/gap-matrix.md:105`（共10处） | 11-M2-android.md | [待确认] 分金/72 龙 表的 `offset` 是否应参与刻度绘制（A-04/B… |
| Q-07 | C-18 | 中 | 高度可疑 | `app/android/app/src/main/assets/map.html:624-625`；`app/android/app/src/main/assets/map.html:663-669`；`app/android/app/src/main/assets/map.html:690-702`（共6处） | 20-M3-map.md | `#extension GL_OES_standard_derivatives` 在… |
| Q-08 | C-19 | 低 | 高度可疑 | `app/android/app/src/main/assets/map.html:527`；`app/android/app/src/main/assets/map.html:1311-1320`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:726-732` | 20-M3-map.md | Tab 隐藏/App 后台时是否停 3D 渲染只靠 IntersectionObse… |
| Q-09 | C-20 | 中 | 待确认 | `app/android/app/src/main/assets/map.html:475-485`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:97-102`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:385-390`（共5处） | 20-M3-map.md | PRD 记录真机「点定位无可见变化」，基线代码未定位原因 |
| Q-10 | C-21 | 低 | 待确认 | `app/android/app/src/main/java/com/luopan/fengshui/CompassView.java:163-169`；`app/MECHANICS.md:120-126`；`docs/gap-matrix.md:121`（共16处） | 20-M3-map.md | 原版罗盘叠层的透明度/尺寸/是否居中无 review-base 内对照证据 |
| Q-11 | Q-01 | — | 待确认 | `scripts/smoke.ps1:128`；`scripts/smoke.ps1:134-135` | 30-M4-server.md | （待确认）`Get-Result([int]$Code)` 把 `$null` 当 … |
| Q-12 | Q-01 | — | 待确认 | `tests/numeric/test_mechanics.py:149-159`；`docs/FINAL-REPORT.md:20` | 40-M5-tests.md | 780 条圈层文字的 DEX 证据链不在基线 |
| Q-13 | Q-02 | — | 待确认 | `docs/FINAL-REPORT.md:11`；`docs/qa/AT-final-round.md:28`；`docs/qa/bugs.md:262`（共4处） | 40-M5-tests.md | 「18/18 PASS、0 BLOCKED」没有可复现的运行记录 |
| Q-14 | Q-03 | — | 待确认 | `app/MECHANICS.md:183-188` | 40-M5-tests.md | 原版 APK 在 7.5° 时「正兼向」行显示什么 |
| Q-15 | G-15 | — | 待确认 | `docs/qa/AT-final-round.md:23-28`；`docs/qa/bugs.md:258-265` | 50-M6-docs.md | 【待确认】`18/18 PASS` 这一次运行是否真实发生 |
| Q-16 | G-16 | — | 待确认 | `app/MECHANICS.md:276-282`；`docs/product/PRD-v0.1.md:100`；`docs/gap-matrix.md:7`（共57处） | 50-M6-docs.md | 【待确认】`.inspect` 的 780 条逐条地址是否真的对应 DEX |

### 4.4 风险与改进建议（R：4 条）

| 编号 | 优先级 | 涉及文件行号 | 来源报告 | 备注 |
|---|---|---|---|---|
| R-01 | P1 | （报告未给文件行号） | 40-M5-tests.md | （P1）86 条用例中 37 条只测 oracle 自身，不触达被测源码 |
| R-02 | P1 | （无既有文件；`test_sweep.py` 为建议新增） | 40-M5-tests.md | （P1）跨方位一致性只有 41 个航向 + 4 个文字向量，无全周/全格扫描 |
| R-03 | P2 | `tests/numeric/README.md:60`；`tests/numeric/run_all.py:7` | 40-M5-tests.md | （P2）把 `expectedFailure` 定为惯例、`run_all` 定义「… |
| R-04 | P2 | `tests/numeric/source_parse.py:55-60`；`app/src/main.rs:637-674`；`docs/qa/bugs.md:262`（共8处） | 40-M5-tests.md | （P2）`source_parse.rust_let_expr` 全文件按名字取首个… |

### 4.5 已知缺陷（K：1 条）

| 编号 | 级别 | 证据状态 | 涉及文件行号 | 母条 | 来源 | 备注 |
|---|---|---|---|---|---|---|
| K-01 | 高（B/C/G 三报告一致） | 已证实（随母条） | `app/android/app/src/main/res/layout/activity_main.xml:14-49`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:79-90`；`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:258-274`（共 12 处） | B-03+B-04 | B/C/G 三报告 | 底部三 Tab 与原版首页不一致；罗盘浮层非居中且遮图 |

---

## 5 delta 表（原编号 → 母条 → 级别 → 状态）

| 原编号 | 原级别 | 裁定后 | 母条 | 依据 |
|---|---|---|---|---|
| A-03 | 高 | 交叉引用 | B-10（高） | 双盘读数口径归 B |
| A-04 | 中 | 交叉引用 | B-09（中） | 双端同源归 B |
| F-03 | 高 | 交叉引用 | B-10（高） | M5 引用 B-10 |
| B-10 | 中 | 高 | —（母条） | 直接输出错读数，升为高 |
| C-02 | 高 | 交叉引用 | B-02（阻断） | DebugBridge 注入归 B-02 |
| C-07 | 高 | 交叉引用 | K-01（B-03+B-04） | 三 Tab 母条 |
| C-13 | 中 | 交叉引用 | B-11（中） | 双 WebView 注入/状态 |
| C-16 | 中 | 交叉引用 | B-05（高） | 触摸停跟随，取高 |
| G-01 | 高 | 交叉引用 | K-01（B-03+B-04） | 同上 |
| G-06 | 高 | 交叉引用 | C-01（高） | key 明文归 C |
| G-09 | 中 | 交叉引用 | C-06（高） | map-bridge 契约，取高 |
| E-06 | 中 | 交叉引用 | F-01（高） | 数值门禁假绿，取高 |
| B-18 | 低 | 交叉引用 | C-12（中） | mapMarker 共享未读 |
| H-02 | 高度可疑·中 | 交叉引用 | A-01（高） | 桌面验收图失真，Q 侧 |
| B-01 | — | 已撤 | — | 误报 |
| H-01 | — | 已撤 | — | 实测不成立 |
| M4 Q-02 | — | 已关闭 | — | 实测只回环 |
| G-23 | — | 新增·低 | — | 90 §11.9 新立，总监定 G-23（原账 107→108） |
| B-20 | 低 | 低（保留） | — | 漏 CompassView.java:120-124，订正后回清单 |
| E-04 | 高 | 高（维持） | — | 契约破裂；要降须与 E-02 同降 |

补充说明（不进 delta 计数）：

1. **E-04 维持高**：契约声明 413 却永远送不到客户端，属契约破裂；要降须与 E-02 同降。
2. **B-20 保留（低）**：审计剔除因漏 `CompassView.java:120-124`；客户端订正为「Android 包内四份 + 跨端两份（`app/src/main.rs:227`、`tests/numeric/oracle.py:122`）」，级别不变。
3. **M4「74 项」→ 86**：`run_all.py` 实测 `tests=86`，编辑订正，不改级别。
4. **帧率口径**：统一「推算值，未实测」；具体数字只留一处并标假设（B-03 已把 16.7 Hz 定为唯一出处，M3 引用须标「引自 B-03」）。
5. **G-23**：`gap-matrix.md:132` 的「当前实现」列仍写坐向单态，与基线 `sitMode` 双态不符；无功能风险，定低。

---

## 6 机械校验与路径

- 以 `git ls-files`（204 文件）为全集解析条目引用；未发现用基线不存在的文件充当证据；0 条真实行号越界。
- 本索引的路径已统一为**仓库相对全路径**（`app/...`、`backend/...`、`docs/...`、`tests/...`）；先前缩写（`MainActivity.java`、`map.html`、`docs/MECHANICS.md`、`manifest.md`、`README.md`）已全部解析。
- 假阳性：A-Q03 的 `main.rs:713-716` 是引述 gap-matrix 的过期行号；R-02 的 `test_sweep.py` 是建议新增；G-13 的「75」是用例数。
- 91 §12 订正项已并入条目的行号（A-01、B-02、B-10、B-20、C-03、C-05、C-30、E-03、F-09、C-34）。

---

## 7 `99-FINAL-REVIEW.md` 骨架

| 章节 | 内容 | 数据来源 |
|---|---|---|
| 1 概述 | 基线、三层口径、裁决要点、总数 108 | §0/§3 |
| 2 缺陷清单 | 108 条母条（阻断1+高20 共 21；中 47；低 40） | §4.1 + 订正后报告原文 |
| 3 已知缺陷 | K-01（母条 B-03+B-04） | §4.5 |
| 4 风险 | R-01…R-04 | §4.4 |
| 5 待确认 | Q-01…Q-16 | §4.3 |
| 6 未覆盖 | M1–M8、M6/M7 归属 | 各报告覆盖声明 |
| 7 证据索引 | EV-001…EV-109 | 回 `review-base` 重建 |
| 8 附录 | 已读清单、编号映射、时间线、签字位 | §1/§2 |

---

## 8 未决

1. `99-FINAL-REVIEW.md` 已刷 v0.2（108 条）。等 LC审证据按 91 §12 口径复核 delta；通过后由总监逐条抽样追溯、签字。
2. LC审地图 C-05（`map.html:212-214`）、C-30（`1480-1483`）微改已交，复核以订正后快照为准。
3. 本稿不宣布可签。
