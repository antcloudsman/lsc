# 山水罗盘 LC · 第二轮还原度复审报告（round2）

> **状态：定稿 v1.0.5。**（v1.0.5 判据细化：R2-007 含氧公式自洽 ±0.01、气压 ≠102.4；R2-030 关账观测点） 已并入两份 Stage 3 差异清单（客户端 24 行、地图 13 条，合计 37 条），按 `R2-001..R2-037` 统一编号、`EV-R2-001..037` 一一对应；已回填 `verify-round2.md` 抽样核验（**28/37 = 75.7%**，驳回 0 / 不成立 0 / 无法复现 0）；C-06 升 P0、C-09 定 P1、C-07 记 NC 均按总监 2026-11-06 裁定。
> **核验缺口**：`docs/fidelity/verify-round2.md`（LC审核验，进行中）未回填，§7「被核验否掉的条目」暂留位；所有条目核验状态记「未核」，不预判成立与否。
> **待补采缺口**：36/38 入口、`map-3d-mode`、`tool-contour` 四项按总监要求标「待补采/未确认」，未当已证实写（见 §10）。

**基线**：`develop` @ `a97f5bd`（`verify-round2.md` 合入点；差异清单输入基线 `1f4861b`，两提交间两份 findings 逐字节未变）。
**输入**：`docs/fidelity/findings-client.md`（`19bdc18`，321 行，24 行覆盖）、`docs/fidelity/findings-map.md`（13 条）。
**对照**：原版 APK `space.xinzhi.globe` 1.6.1，真机 L2E0222531014833，1228×2700、density 540、动画三项 0、heading 冻结 0°、地图 39.9042/116.4074 z=12。
**证据**：`docs/fidelity/orig/`（原版）、`docs/fidelity/lc/`（我方）；唯一索引 `docs/fidelity/lc-captures.md`；清单 `docs/fidelity/orig-inventory.md`（38 条 + U1–U7）；判据 `docs/fidelity/checksheet.md`。

## 0 成文规则（本稿的格式契约）

0.1 **编号**：`R2-001` 起顺序编号（不按级别分段，Stage 4 插条不重排）；`EV-R2-xxx` 与之一一对应。已知缺陷续 `K-01`；风险续 `R2-R-01`；待确认沿用字段而不另编号。
0.2 **每条五要素**（总监 Stage 4 验收线）：原版截图 · 我方截图 · 类别 · 严重度 · 代码锚点 · 修法；P0/P1 在 §5 追加「触发条件 / 预期 vs 实际 / 量化」。**代码片段原文统一由 §12 证据索引指向 findings 行号与代码锚点**，本稿不二次抄录，避免与 findings 双份漂移；终稿如需内联八字段，由总监裁定后回补。
0.3 **证据等级**只有三档：已证实 / 高度可疑 / 待确认；只有「已证实」计入 P0/P1/P2 正式条数，其余进 §10。
0.4 **量化口径**：位置 px、颜色 hex、字号 sp、文案逐字；所有表述必须可量化，不使用任何未量化的程度副词。
0.5 **已知缺陷必收**：K-01（用户直接提出，见 §8）。
0.6 **结案口径**：3D 入口 = 坐标点偏（真 bounds `[1027,2366]-[1222,2494]`，中心 `(1124,2430)`），不得写「入口失效」。

## 1 审查概述

本轮以原版 1.6.1 为唯一对照，逐页比对 LC 的页面、面板、按钮、样式、效果、业务逻辑。两份 findings 合计覆盖 **37 条**：客户端 24 行（清单客户端 22 行 + LC 增量 2 行）、地图侧 13 条（清单地图章节 16 行中的 13 条；其余行按证据不可用/缺失处理，见 §10）。

一句话结论：**用户最关心的结构问题已收口**（底部三 Tab 已删除、罗盘以浮层叠在地图中心、圆心差 ≤1.5px），但**「子页/面板的呈现载体」仍是主战场**——原版 4 处地图内浮层被改成独立 Activity 或居中 AlertDialog（海拔查询、收藏点、读盘、更多面板），另有 POI 面板、抽屉、搜索语义三类整块缺失/替换。

## 2 严重度统计

| 严重度 | 客户端 | 地图 | 合计 | 编号 |
|---|---|---|---|---|
| **P0（阻断）** | 5 | 2 | **7** | R2-004、006、007、014、018、025、030 |
| **P1（高）** | 9 | 4 | **13** | R2-003、005、010、011、013、015、019、021、024、026、027、029、033 |
| **P2（中）** | 2 | 3（P2·DI） | **5** | R2-001、008、032、034、035 |
| **DI（不计缺口）** | 4 | 含上 | **4** | R2-002、009、017、023 |
| **NC / 证据缺陷** | — | 3 | **3** | R2-028、031、036 |
| **待补采** | 2 | — | **2** | R2-020、022 |
| **未确认** | 1 | — | **1** | R2-012 |
| **缺失（待决策）** | — | 1 | **1** | R2-037 |
| **等价（不扣分）** | 1 | — | **1** | R2-016 |
| **合计** | **24** | **13** | **37** | — |

- 与两份 findings 的合计相符：客户端 5/9/2/4/3/1 = 24（`findings-client.md` §11（条数统计））；地图 2/4/3/3/1 = 13（P0 含 C-06）。
- **主口径 37 条**（`R2` 编号，可逐条追溯）；**现象去重 35 条**：`03(a)≡C-02`（`R2-003(a)`≡`R2-026`，同一读数面板、同一代码 `MainActivity.java:415-430`）、`03(c)≡C-08`（`R2-003(c)`≡`R2-032`，同一句角注，原生 `TextView` 与 `map.html:87` `#srcInfo` 两处实现）。抽样百分比按 37 条计，不按 35 重算。
- **优先修复面**：P0 7 + P1 13 = **20 条**；P2 5 条中 R2-035 未确认；DI 4、NC 3、待补采 2、未确认 1、缺失 1、等价 1 不计入还原度缺口。
- **总监裁定（2026-11-06，本稿已执行）**：① C-09 按行表定 **P1**（代码锚点 `MainActivity.java:471-480`、`561-569`）；② C-07 记 **NC·未确认（证据错态）**；③ **C-06 由 P1 升 P0**——`checksheet.md:61`「页面层级不同」= P0，与 client 10 同类不许两级。

### 2.1 DI 台账（本轮 7 条，编号 D-012…D-018）

> **编号口径**：顺延 `docs/TEAM_CHARTER.md` 已用至 **D-011**，本轮 7 条排 **D-012…D-018**；不另立 `DI-xx` 命名空间（总监 2026-11-06 裁定：同一份报告不留两套编号）。

| 编号 | 抽屉项（逐字） | 原版形态 | LC 本轮处理 | 登记依据 | 状态 |
|---|---|---|---|---|---|
| D-012 | 账户设置 | 原版 WebView 子页，内容未取证 | `InfoPageActivity` 通用信息页占位（不编造内容） | 内容未取证；不拿占位页充 1:1 | 已登记，不计「已关闭」 |
| D-013 | 投诉反馈 | 同上 | 同上 | 同上 | 已登记 |
| D-014 | 关于我们 | 同上 | 同上 | 同上 | 已登记 |
| D-015 | 联系我们 | 同上 | 同上 | 同上 | 已登记 |
| D-016 | 开具发票 | 同上 | 同上 | 同上 | 已登记 |
| D-017 | 帮助中心 | 同上 | 同上 | 同上 | 已登记 |
| D-018 | 用户协议 | 同上 | 同上 | 同上 | 已登记 |

> 说明：①**推荐给好友不入 DI**——它是真实动作（系统分享 `Chooser`），落点 `HwChooserActivity`；②「地图设置」不入 DI，直连 `SettingActivity`；③本表 7 条不改变 P0/P1/P2 统计（主口径仍 37 条）；④R2-018 关账时这 7 项标 DI、**不算「已关闭」**；⑤`InfoPageActivity` 仅限抽屉这 7 项使用，不得作为其他未实现功能的兜底（总监紧箍第 4 条）。

## 3 分页与分类

### 3.1 六类归并

| 类别 | 条数 | 编号 |
|---|---|---|
| 页面 | 5 | R2-001、020、021、022、024 |
| 面板 | 9 | R2-003、004、013、015、016、018、026、027、029 |
| 按钮 | 4 | R2-010、011、012、033 |
| 样式 | 3 | R2-008、019、032 |
| 效果 | 3 | R2-005、028、036 |
| 业务逻辑 | 13 | R2-002、006、007、009、014、017、023、025、030、031、034、035、037 |
| **合计** | **37** | — |

### 3.2 覆盖与移交

| 范围 | 清单行 | 本稿条数 | 去向 |
|---|---|---|---|
| 客户端章节 | 22 行 + 增量 2 行 | 24 | 全量覆盖（缺失行指向已有证据） |
| 地图章节 | 16 行（04/06/07/18–30） | 13 | 13 条入清单；行 18/19/20/22/23/25/27 因证据不可用列未确认（C-04/C-12 承载），行 30 为缺失（C-13） |
| 桌面 M1 | — | 0 | 不在本轮（见 §11） |

## 4 缺陷清单

| 口径 | 条数 | 说明 |
|---|---|---|
| **R2 编号口径** | **37** | 主口径，`R2-001..037` 可逐条追溯；抽样百分比按此口径（28/37 = 75.7%） |
| **现象去重口径** | **35** | 去重组：`03(a)≡C-02`（`R2-003(a)`≡`R2-026`）、`03(c)≡C-08`（`R2-003(c)`≡`R2-032`） |

> 表列＝五要素：原版证据 | 我方证据 | 类别 | 严重度 | 代码锚点（`develop 1f4861b`，与 `3d7fcb3` 同源） | 修法。「出处」为该条在 findings 中的行号，供 §12 索引复核。

### 4.1 阻断（P0，7 条）

| 编号 | 清单行 | 标题 | 原版证据 | 我方证据 | 类别 | 代码锚点 | 修法 | 出处 |
|---|---|---|---|---|---|---|---|---|
| R2-004 | 05 | POI 详情面板整块缺失 | `orig/map-poi-sheet-01.png/.xml` | 无（`lc/map-home-01.xml` 无详情容器） | 面板 | P0 | `MainActivity.java:1609-1615`（`mapMarker` 只赋值，全文件无读取） | 实现点选面板：名称/距我/海拔/`WGS84:…`/复制经纬度/收藏地点/导航 | findings-client:33 |
| R2-006 | 09 | 「隐藏」不是隐藏，是退出罗盘态 | `orig/compass-overlay-hidden-map.png/.xml` | `lc/compass-overlay-hidden-map.png/.xml` | 业务逻辑 | P0 | `MainActivity.java:511-517`、`1044-1062` | 「隐藏」只隐盘面，保留读数条与「显示」反向态；再点恢复，不退出罗盘态 | findings-client:35 |
| R2-007 | 10 | 海拔查询：地图浮层改独立 Activity，缺「打点」 | `orig/compass-main-01.png/.xml` | `lc/compass-main-01.png/.xml` | 业务逻辑 | P0 | `MainActivity.java:940-947,1382-1386`；`ElevationActivity.java:27-37,142-150,190-196` | 改回地图内浮层（不换页），补「打点」`[974,2540][1069,2578]`；**判据订正（2026-11-06，LC逆向 R10 `1871326`；审核验 `6ec7614` 细化）**：含氧量断言**公式自洽** `显示含氧 ≈ round(20.95 − 0.0016×显示海拔, 2)`、容差 ±0.01（56 m→20.86%、53 m→20.87% 均可），**不得写死 20.86**；海拔为服务端 `POST api/map/batchGet`，±2 m 仅同源判，异源只断言「字段+单位 m+一位小数+来源标注」；气压为原版后端 `GET api/weather/getPressure` 实时值（UI 仅 `round2`+「千帕」），判据为「字段+单位千帕+两位小数+来源标注」且**显式断言不得等于 102.4**；打点 `[974,2540][1069,2578]` 后图面点位须与查询点一致 | findings-client:36 |
| R2-014 | 17 | 收藏点：地图模式改列表页，缺「添加点」 | `orig/tool-favorite-01.png/.xml`、`favorite-list-02.xml` | `lc/tool-favorite-01.png/.xml`（`favorite-list-02` 已去重，与行 37 共用） | 业务逻辑 | P0 | `MainActivity.java:1417-1419`；`InterestListActivity.java:49-50,74-75` | 恢复收藏点地图态（右列收藏列表/等高线 + 底部「添加点」），列表作为其子页 | findings-client:43 |
| R2-018 | 34 | 抽屉菜单整块缺失 | `orig/drawer-menu-03.png/.xml` | 无（全工程无 `DrawerLayout`） | 面板 | P0 | `MainActivity.java:1430`；`SettingActivity.java:42` | 补抽屉 9 项 + 版本号；关账口径：地图设置→`SettingActivity`、推荐给好友→系统分享 `Chooser`（原版为子页，LC 走系统分享）、其余 7 项→`InfoPageActivity` 且 DI 台账 D-012…D-018、toast 0 个；登录/VIP 项按 DI 处理 | findings-client:47 |
| R2-025 | C-01 | 地图搜索被替换为知识检索 | `orig/search-result-01.png`、`02.png` | `lc/search-result-01.png`、`02.png` | 业务逻辑 | P0 | `MainActivity.java:504`、`1483-1509` | 恢复原版搜索语义（境内/境外/行政区域/收藏点 4 tab + 历史 + 坐标输入）；知识检索挪回知识弹层 | findings-map:35 |
| R2-030 | C-06 | 山脉入口行为不同（整页 vs 地图内） | `orig/map-mountains-entry.png` | `lc/map-mountains-entry.png` | 业务逻辑 | P0（核验订正：原 P1） | `MainActivity.java:965-968` | 按原版做地图内山峰/山名图层或半屏面板；**关账观测点（审核验 `6ec7614`）**：点山脉后 `mResumedActivity` 仍 `MainActivity`、dump 出现山名/图层节点、**不得出现整页标题「看山脉」**、关闭后可再开；原版基准 `orig/map-mountains-entry.xml`（仍在地图页，仅地名） | findings-map:40 |

### 4.2 高（P1，13 条）

| 编号 | 清单行 | 标题 | 原版证据 | 我方证据 | 类别 | 代码锚点 | 修法 | 出处 |
|---|---|---|---|---|---|---|---|---|
| R2-003 | 03 | 地图态常驻读数面板 + 工具列锚点整体漂移 | `orig/map-home-01.png/.xml` | `lc/map-home-01.png/.xml` | 面板 | P1 | `MainActivity.java:416-430`、`633-641`/`645-669`、`534` | 读数面板仅罗盘态可见；锚点用内容帧 2578；角注改中性文案并移入 strings | findings-client:32 |
| R2-005 | 08 | 罗盘浮层缺中心指针，读数条与返回重叠 | `orig/tool-compass-page.png/.xml` | `lc/tool-compass-page.png/.xml` | 效果 | P1 | `CompassView.java:423-450`；`MainActivity.java:416-430,492-523` | 补中心 `pointer.png`（位图 46×188，屏幕节点 81×324）；读数条正常态回 `[105,397][290,436]` 单行（隐藏态 `[105,397][350,436]`），不与返回重叠 | findings-client:34 |
| R2-010 | 13 | 罗盘左列 6 项位置/间距（步距 140 vs 186） | `orig/tool-compass-page.png/.xml` | `lc/win-02-compass.png/.xml` | 按钮 | P1 | `MainActivity.java:459-466,574-587` | 按原版标签中心 y=1408.5 起、步距 186 锚定 | findings-client:39 |
| R2-011 | 14 | 罗盘右列 4 项位置/间距（步距 140 vs 184） | 同上 | 同上 | 按钮 | P1 | `MainActivity.java:479-486` | 按原版 y=475.5 起、步距 184 锚定 | findings-client:40 |
| R2-013 | 16 | 智能读盘：深色对话框、缺「离宅」「点击查看」 | `orig/w3-sheet.png` | `lc/w3-sheet.png/.xml` | 面板 | P1 | `CompassReadSheet.java:46-53,55-84,86-89` | 改白色底部 sheet；补右上「离宅」；每行补「点击查看 ›」 | findings-client:42 |
| R2-015 | 31 | 更多面板改居中 AlertDialog，格位整体偏移 | `orig/more-sheet-03-fresh.png/.xml` | `lc/more-sheet-03-fresh.png/.xml` | 面板 | P1 | `MainActivity.java:1328-1426,1429-1459,1466-1469` | 改底部 sheet；格 296×231、顶 y=1350；删「功能开关（调试）」；补「编辑」 | findings-client:44 |
| R2-019 | 35 | 地图设置模式 chip 高度差 61px | `orig/setting-map-01.png/.xml` | `lc/setting-map-01.png/.xml` | 样式 | P1 | `SettingActivity.java:37,49-50,140` | chip 高改 158px（原版 [39,567][285,725]）；「地图滤镜」行改内联标签 | findings-client:48 |
| R2-021 | 37 | 收藏列表入口：子页 vs 独立页 | `orig/favorite-list-02.png/.xml` | `lc/tool-favorite-01.png/.xml`（与行 17 共用） | 页面 | P1 | `InterestListActivity.java:49-50,74-75` | 收藏列表作为收藏点模式子页；行内容待原版有数据态再比（NC） | findings-client:50 |
| R2-024 | 增量-B | 知识库内容与入口均非原版 | 无对应（原版 14 篇 HTML） | `lc/lc-knowledge-sheet.png/.xml` | 页面 | P1 | `MainActivity.java:1484-1491,1565-1598` | 按原版 `assets/compassIntroduce/` 复刻，或登记 DI | findings-client:53 |
| R2-026 | C-02 | 地图态左上信息块内容不符 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 面板 | P1 | `MainActivity.java:415-430` | 地图态隐藏读盘块，左上改示当前地名（青海湖在 `orig/map-beijing-z12.xml`、哀牢山在 `orig/map-mountains-entry.xml`，分属两张 dump）；读盘 9 行只在罗盘态显示 | findings-map:36 |
| R2-027 | C-03 | 底图源面板呈现方式不同（全屏遮罩+文字列表） | `orig/map-layer-sheet.png` | `lc/map-layer-sheet.png` | 面板 | P1 | `map.html:47-55,88-92,1472-1483,1498-1513` | 改回 3×4 缩略图网格（12 格 + 「+」），卡片底部条为源名 | findings-map:37 |
| R2-029 | C-05 | 数据叠加面板缺「行政区域筛选」等条目 | `orig/map-overlay-sheet.png` | `lc/map-overlay-sheet.png` | 面板 | P1 | `map.html:748,1203` | 补筛选入口；无数据行改禁用态而非说明段 | findings-map:39 |
| R2-033 | C-09 | 左右列按钮体量/半透明底（195×128 vs 文字 71×39） | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 按钮 | P1 | `MainActivity.java:471-480,561-569` | 图标+小字、收窄按钮、去大面积半透明底 | findings-map:43 |

### 4.3 中（P2，5 条）

| 编号 | 清单行 | 标题 | 原版证据 | 我方证据 | 类别 | 代码锚点 | 修法 | 出处 |
|---|---|---|---|---|---|---|---|---|
| R2-001 | 01 | 启动页缺失（品牌三行文案） | `orig/_launch-probe.png` | `lc/launch-probe-01.png` | 页面 | P2 | `MainActivity.java:133-175` | 补同构启动页，或由产品登记 DI | findings-client:30 |
| R2-008 | 11 | 超清页文案为实现口径 | `orig/tool-hdmap-01.xml` | `lc/tool-hdmap-01.png/.xml` | 样式 | P2 | `MainActivity.java:1376-1379`；`strings_supermap.xml:5` | 去「LC 会按」「原生最高级」「云端超分」等实现口径 | findings-client:197 |
| R2-032 | C-08 | 底部来源条审图号「未确认」 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 样式 | P2 | `map.html:87` | 换天地图官方审图号或按源分源显示；图源替换本身属 D-006 | findings-map:42 |
| R2-034 | C-10 | 轨迹记录登录墙（LC 直接历史轨迹页） | `orig/tool-track.png` | `lc/tool-track.png` | 业务逻辑 | P2·DI | 轨迹入口（`更多→轨迹记录`）；`TrackReplayActivity.java:159` | 登录墙按 D-006 属有意差异；空态文案与「轨迹设置/开始记录」对齐 | findings-map:44 |
| R2-035 | C-11 | 画笔/钓点入口形态未确认（原版 dump 是罗盘浮层页） | `orig/tool-brush.png`、`tool-fishing.png` | `lc/tool-brush.png`、`tool-fishing.png` | 业务逻辑 | P2·未确认 | `map.html:538,639` | 按原版复现路径重采后判定 | findings-map:45 |

### 4.4 低 / 不计入还原度缺口（12 条）

| 编号 | 清单行 | 标题 | 类别 | 判定 | 代码锚点 | 依据 / 下一步 | 出处 |
|---|---|---|---|---|---|---|---|
| R2-002 | 02 | 开屏广告缺失 | 业务逻辑 | DI | 无广告 SDK | D-001 去商业化；不修 | findings-client:194 |
| R2-009 | 12 | 付费墙缺失 | 业务逻辑 | DI | 无 VIP 代码 | D-006/T-15；不修 | findings-client:195 |
| R2-012 | 15 | 底栏布局（原版未暴露按钮 bounds） | 按钮 | 未确认 | `MainActivity.java:529-531,672-677` | 原版底栏 5 项在 `orig/tool-compass-page.xml`（单行 XML；按文本/描述节点序第 35–39 个：切换/旋转/缩放/透明度/天支线；y2573–2578，未暴露按钮 bounds）；补采完整 bounds 后定级 | findings-client:41 |
| R2-016 | 32 | 更多面板滚动（原版需滚动，LC 一屏 16 项） | 面板 | 等价 | `MainActivity.java:1339-1343` | 功能等价不扣分；若按 R2-015 改回 sheet 需恢复滚动 | findings-client:45 |
| R2-017 | 33 | 天气页（原版 VIP+FLAG_SECURE，界面不可取） | 业务逻辑 | DI/NC | `MainActivity.java:1371-1373`；`WeatherActivity.java` | 原版不可比，LC 真实数据为增量；不扣分 | findings-client:196 |
| R2-020 | 36 | 工具管理：代码有入口，证据未采 | 页面 | **待补采** | `MainActivity.java:900-904`；`ToolsManagerActivity.java:24-43` | 从左列「全地图工具」(103,496) 补采双图后再判视觉 | findings-client:49 |
| R2-022 | 38 | 收藏保存：代码有入口，证据未采 | 页面 | **待补采**（登录墙 DI） | `FishingActivity.java:414-425`；`SaveInterestActivity.java:19-26` | 从「钓点→收藏地点」补采保存页；登录墙按 D-001 登记 | findings-client:198 |
| R2-023 | 增量-A | 盘面目录（原版服务端 ≥27 幅，LC 内置 5 幅） | 业务逻辑 | DI/NC | `CompassListActivity.java:111,468-498`；`MainActivity.java:1018-1019` | 5 幅常用盘面可 1:1；在线目录不可离线穷举，登记 DI | findings-client:199 |
| R2-028 | C-04 | `map-3d-mode`：证据态未生效（非渲染缺陷） | 效果 | **P1·证据级 / 待补采** | `map.html:1380-1419,1635-1639`；`tools/map/collect_map_lc.ps1:12,75` | 脚本默认 3D 坐标 `(155,576)` 错，真实中心 `(1124,2430)`；重采并双确认（按钮「返回 2D」+ `LC3D mode=3d`）。**状态说明**：`map-3d-mode` 被 `d18928d` 加回，`lc-captures.md:112` 仍标「实采（异常）」，实为**证据无效·待重采**，不得当可用 3D 证据引用 | findings-map:38 |
| R2-031 | C-07 | 探索入口（证据错态：拍成「看山脉」） | 业务逻辑 | **未确认·证据错态** | `MainActivity.java:916-922` | 重采探索页后再判；现有截图不可作证据 | findings-map:41 |
| R2-036 | C-12 | 等高线开启后无视觉变化 | 效果 | **未确认 / 待补采** | `map.html:1635-1639,1936-1953` | 须在 3D 可见态下重拍开/关双图；现有截图与 3D 态逐字节相同 | findings-map:46 |
| R2-037 | C-13 | 相机/AR 入口缺失 | 业务逻辑 | P2·缺失（待决策） | `MainActivity.java:471` | 补相机权限弹窗与入口，或书面登记「不做」决策 | findings-map:47 |

## 5 Top 差异（按影响排序，P0 全列）

### 5.1 R2-004（05）POI 详情面板整块缺失 · P0

- **触发**：地图态点地图任意点（`lc/map-home-01` 态）；再走 3D 点选。
- **预期（原版）**：底部卡片含 名称 [59,2135][1084,2257]、`距我：-` [59,2283][203,2329]、`海拔：56m` [301,2283][510,2329]、`WGS84:116.4074，39.9042` [85,2368][606,2414]、`复制经纬度`、`收藏地点` [477,2544][681,2578]、`导航` [950,2544][1053,2578]（`orig/map-poi-sheet-01.xml`）。
- **实际（LC）**：`MainActivity.java:1609-1615` 的回调只 `mapMarker = marker`；`mapMarker`（第 54 行声明）全文件无第二处读取，点地图无任何可见反馈。
- **量化**：动作入口 0/3（复制经纬度/收藏地点/导航）；面板高度 0 vs 原版约 443px（2135–2578）。
- **证据**：`findings-client:33`；证据 `orig/map-poi-sheet-01.*`、`lc/map-home-01.xml`。

### 5.2 R2-025（C-01）地图搜索被替换为知识检索 · P0

- **触发**：地图态点顶栏「搜索」。
- **预期（原版）**：境内/境外/行政区域/收藏点 4 个 tab + 搜索历史 + 坐标输入；输入框 `[170,252][1039,375]`、tab 行 `y=420..486`（`orig/search-result-01.xml`）。
- **实际（LC）**：`MainActivity.java:504` → `showSearchDialog()`，数据源为盘面/圈层/知识条目，hint「搜索：盘面 / 圈层 / 知识条目」（`1483-1509`）。
- **量化**：搜索对象从「地理实体」变为「本地知识条目」；4 tab 与历史 0 个。
- **证据**：`findings-map.md:33`；证据 `orig/search-result-01/02.png`、`lc/search-result-01/02.png`。

### 5.3 R2-018（34）抽屉菜单整块缺失 · P0

- **触发**：地图态寻找左上抽屉入口。
- **预期（原版）**：账户/VIP 区 4 项 + 菜单 9 项 + `三维山水全景地图V 1.6.1`（`orig/drawer-menu-03.xml`）。
- **实际（LC）**：全工程 `DrawerLayout` 0 处，代码自述「没做抽屉」（`MainActivity.java:1430`）；设置项临时挂在「更多」标题栏（`1445-1478`）。
- **量化**：菜单项 0/9（仅「地图设置」有等价入口）；缺失 7 项功能入口 + 版本号。
- **证据**：`findings-client:47`。

### 5.4 R2-007（10）海拔查询换页 + 缺「打点」 · P0

- **触发**：左列「海拔查询」(103,1674)。
- **预期（原版）**：不换页，地图上叠底部面板：`56`、`海拔高度(m)`、地址、`经纬度:116.4074，39.9042`、`含氧量：20.86%`、`气压：102.4千帕`、`打点` [974,2540][1069,2578]。
- **实际（LC）**：进 `ElevationActivity` 独立页；`纬度：39.915562`/`经度：116.407374`/`海拔：55.0 m`/`含氧量：20.88 %（估算）`/`气压：1006.7 hPa（估算）`/`关闭`。
- **量化**：页面层级差 1 级；无「打点」按钮（点图查询为等价路径 `ElevationActivity.java:113 → map.html:1715`，打点后动作未复刻）；气压 +17.3 hPa 且单位「千帕→hPa」；含氧量 +0.02pp；海拔 +1.0m；坐标由 4 位单行变 6 位两行。
- **判据订正（2026-11-06，来源：LC逆向 R10 补证 `1871326`；**错在判据，不在实现**）**：
  - **含氧量**：原版本地公式 `20.95 − 0.0016×海拔(m)`（`mem_smali/space/xinzhi/globe/ui/controller/d.smali:263-314`，常量 282/288/294），56 m→`20.8604`→**20.86%**；断言**公式自洽** `round(20.95 − 0.0016×显示海拔, 2)`、容差 ±0.01，不写死数值。
  - **海拔**：原版 `POST api/map/batchGet`（服务端，非本地 DEM）；±2 m 仅同源可判，异源只断言「字段+单位 m+一位小数+来源标注」。
  - **气压**：原版 `GET globe.xinzhi.space/api/weather/getPressure`（`ApiService.smali:926-951`）→ `round2(value)` + 「千帕」（`ui/tools/o.1.smali:4930-4986`），**全链路无本地换算**；只断言「字段+单位千帕+两位小数+来源标注」并**显式断言不得等于 102.4**（禁凑；标准大气 56 m ≈100.7 kPa，差 1.7 kPa）。
  - **打点**：`[974,2540][1069,2578]` 不变；另判「打点后图面点位与查询点一致」（审核验 `6ec7614`）。
- **证据**：`findings-client:36`。

### 5.5 R2-014（17）收藏点模式被改成列表页 · P0

- **触发**：更多 →「收藏点」。
- **预期（原版）**：仍在地图，右列 `收藏列表` [1052,604][1192,643]、`等高线` [1070,790][1175,829]，底部 `添加点` [538,2417][691,2496]，比例尺与坐标保留。
- **实际（LC）**：`MainActivity.java:1417-1419` 直接 `startActivity(InterestListActivity)`；页内只有标题与空态（`InterestListActivity.java:49-50,74-75`），无「添加点」。
- **量化**：文字控件 3 vs 5；「添加点」0 vs 1；比例尺/坐标条缺失。
- **证据**：`findings-client:43`。

### 5.6 R2-006（09）「隐藏」= 退出罗盘态 · P0

- **触发**：罗盘态点顶栏「隐藏」(1127,249)。
- **预期（原版）**：盘面/指针消失、按钮变「显示」、读数条 `坐180.0° 向0.0°` [105,397][350,436] 与 `128m`、坐标保留（该态无 `2km` 节点）；左右工具列全部不显示（dump 24 节点）。
- **实际（LC）**：按钮写死「隐藏」并调 `setCompassMode(false)`（`511-517`），恢复地图左列 8 + 右列 10 + 读数面板（`1044-1062`）；无「显示」反向态。
- **量化**：隐藏态工具按钮 0 → 18；「显示」按钮 0 个；恢复罗盘多 1 步。
- **证据**：`findings-client:35`。

### 5.7 P1 中影响最大的三条

1. **R2-003（03）工具列锚点系统性漂移**：`frameH=2700` 而非内容帧 2578，锚点整体 ×1.0473；左列 7 项 delta −28.5～−111.5px、右列 5 项 −31.5～−66.5px，逐项 `×2700/2578` 复核吻合 ≤0.6px（`findings-client.md:104-111`）。**一处修正可消掉 12 项位置偏差。**
2. **R2-015（31）更多面板载体错误**：AlertDialog 标题 y=926 vs 原版 sheet 顶 y=1350（−467px），格 253×162 vs 296×231（−43/−69px），首格 x +83；且多「功能开关（调试）」「设置」、缺「编辑」（`:149-155`）。
3. **R2-013（16）读盘载体与内容**：白色 sheet（`#FFFFFF`）变深色居中对话框（`#424242`），缺「离宅」「点击查看 ›」；文案空格 2→4、1→2（`:141-147`）。

## 6 差异原因分析

### 6.1 技术债（实现与注释/原版证据自相矛盾）

| 证据 | 说明 | 出处 |
|---|---|---|
| R2-003 | 代码注释自称按内容帧 2578 锚定，实际 `contentFrameHeight()` 在沉浸全屏返回 2700 → 系统性 ×1.0473 | findings-client:32-111 |
| R2-019 | `CHIP_H_DP=28.7`（×3.375=96.9px）与注释「245×97px」自洽，但与原版 dump 158px 矛盾 → 按错值实现 | findings-client:48 |
| R2-010/011 | `buildColumn()` 用 `WRAP_CONTENT + CENTER_VERTICAL`，整列在 2700 窗口中居中，而原版左列在下半屏、右列在上半屏 | findings-client §3.3/§3.4 |
| R2-006 | `hideButton` 写死「隐藏」并复用退出罗盘的 `setCompassMode(false)`，没有隐藏态分支 | findings-client:35 |
| R2-018 | 代码注释自述「K-01 单页布局没做抽屉」，9 项菜单被临时挂到「更多」 | findings-client:47 |
| R2-028 | 采集脚本 `tools/map/collect_map_lc.ps1:12` 的 3D 默认坐标 `(155,576)` 与真实按钮 `(1124,2430)` 不符 | findings-map:38,61 |

### 6.2 数据源差异（外部数据不可 1:1）

| 证据 | 说明 | 依据 |
|---|---|---|
| R2-032 | 图源由世纪空间改为天地图，审图号写「未确认」 | findings-map:42（D-006 图源替换） |
| R2-025 | 原版搜索依赖地理数据服务；LC 本地只有盘面/圈层/知识条目 → 搜索语义被顶替 | findings-client:33；findings-map:35 |
| R2-023 | 原版盘面目录为服务端 ≥27 幅，LC 内置 5 幅（CommonCompassEnum 5 类） | findings-client:199,185 |
| R2-017 | 原版天气页为 VIP+FLAG_SECURE，界面不可取得；LC 真实数据为增量 | findings-client:196 |

### 6.3 设计取舍（决策已登记，不计缺口）

| 证据 | 取舍 | 决策依据 |
|---|---|---|
| R2-002 / R2-009 / R2-034 | 去广告、去付费墙、去轨迹登录墙 | D-001 去商业化、D-006/T-15 |
| R2-022（登录墙部分） | 无账号体系，收藏保存不设登录墙 | D-001 |
| R2-032（图源部分） | 天地图替换世纪空间 | D-006 |

> **未登记取舍（按技术债计）**：海拔查询/收藏点/读盘/更多面板四处「地图内浮层 → 独立 Activity / 居中对话框」的载体替换，两份 findings 均未引用任何 D-xxx 决策为其依据；属实现路径选择，不是已登记取舍。

## 7 核验结论与采集可复现性（28/37 核验 + 配对盘）

> 来源：`docs/fidelity/verify-round2.md`（`cddb93b`，已并入 `develop` `a97f5bd`）。抽样 **28/37 = 75.7%**，P0 6/6、P1 13/14、P2 3/5、NC 2/3、DI 1/4、缺失 1/1、等价 1/1 全覆盖；**驳回 0 / 不成立 0 / 无法复现 0**。判定口径：成立＝代码锚点与量化值均可复现；成立·修正＝结论成立、描述或锚点须改（R1–R7）。

### 7.1 结论汇总

| 结论 | 条数 | 编号 |
|---|---|---|
| 成立（原样） | 20 | R2-001、002、003、004、008、010、014、015、016、018、019、021、025、027、028、029、032、033、036、037 |
| 成立·修正（R1–R7） | 7 | R2-005、006、007、011、012、013、026 |
| 成立·定级订正 | 1 | R2-030（C-06 由 P1 升 P0） |
| **被否掉** | **0** | 无驳回、无不成立、无无法复现 |

### 7.2 R1–R7 核验订正（已同步进 §4/§5，标「核验订正」）

| # | 条目 | 订正内容 | 本稿落点 |
|---|---|---|---|
| R1 | R2-005（client 08） | 原版读数条被写成同一 bounds：正常态 `[105,397][290,436]`、隐藏态 `[105,397][350,436]` | §4.2 R2-005 修法 |
| R2 | R2-006（client 09） | 隐藏态 dump 只有 `128m` 与坐标，**无 `2km` 节点**，「2km 保留」删除 | §5.6 预期 |
| R3 | R2-007（client 10） | 「缺打点」限定为「无『打点』按钮」；点图查询为等价路径 `ElevationActivity.java:113 → map.html:1715`，打点后动作未复刻 | §5.4 量化 |
| R4 | R2-011（client 14） | delta 符号按报告自身口径（原−我）应为 −664.5/−620.5/−576.5/−532.5；本稿未展示该组数值，引用时按负号 | §4.2 R2-011（口径注） |
| R5 | R2-012（client 15） | `orig/win-02-compass.xml` 不存在；5 个节点在 `orig/tool-compass-page.xml`（单行 XML；按文本/描述节点序第 35–39 个：切换/旋转/缩放/透明度/天支线；y2573–2578，未暴露按钮 bounds） | §4.4 R2-012 |
| R6 | R2-026（C-02） | 青海湖在 `orig/map-beijing-z12.xml`、哀牢山在 `orig/map-mountains-entry.xml`，分属两张 dump，不可写成一张图两个地名 | §4.2 R2-026 修法 |
| R7 | R2-013（client 16） | `pointer.png` 的 46×188 是位图尺寸（`MECHANICS.md:311`）；屏幕实测节点 81×324 | §4.2 R2-005 修法 |

> 回补状态：R1–R5、R7 由 LC审客户端、R6 由 LC审地图按归属回补到各自 findings；本稿已按核验原文同步，回补落地后行号以 findings 新值为准。

### 7.3 前轮已关闭 / 已改判（保留追溯）

| 原条目 | 原判定 | 现有结论 | 依据 |
|---|---|---|---|
| K-01（用户提出：底部三 Tab / 罗盘浮层） | 第一轮「高」缺陷 | **已修复关闭**：`MainActivity` 只有 `buildSinglePage()`，无 `showTab`/`BottomNavigationView`；罗盘浮层圆心 (614,1350) vs 原版 (613.5,1349.5) | findings-client §0/§7.1；findings-map §5 |
| 36「工具管理缺失」 | 索引判「缺失」 | **失实**：代码入口自 `e88ed0c` 存在（`MainActivity.java:903`），改判「待补采」 | findings-client §6.1 |
| 38「收藏保存缺失」 | 索引判「缺失」 | **失实**：`FishingActivity.java:419` 已有保存入口，改判「待补采」 | findings-client §6.2 |
| C-04「3D 被 2D 覆盖」 | 疑似 P0 渲染缺陷 | **不成立**：同 APK 实机复现 3D 可用（`LC3D mode=3d`、`texture ok 36/36`、`fps=56`），改判证据缺陷 | findings-map §3.1 |

### 7.4 未抽样 9 条（状态：未核，不得当已证实引用）

client 12、33、36、38、增量-A、增量-B；map C-07、C-10、C-11 → `R2-009、017、020、022、023、024、031、034、035`。

### 7.5 我方采集可复现性（引用 `repro-round2.md`，`843f931`，已并入主线）

- **配对盘（`lc-only-allow.json` 生效口径，`dd09cc6`）**：并集 **62 态 = 23 compared / 5 dump-only / 4 lc-only（增量，不计配对）/ 30 missing-lc（待补采 + 命名对齐）**；`missing-orig` 归零（5 条 `dump-only-allow.json` 豁免 + 4 条 `lc-only-allow.json` 豁免后）。
- **历史口径作废**：豁免前的未比较配对口径（23 compared + 30 missing-lc + 6 missing-orig = 59）已被本轮豁免取代，不再引用。
- **证据一致性**：29/29 份 meta 的 `image.sha256` + `dump.sha256` 与实际文件一致（ALL_META_HASH_MATCH）；客户端批 14 态 / `e88ed0c` / APK `8dd5bf13…` / 7,059,509 B；地图批 15 态 / `464ebb8` / APK `ccd5686f…` / 7,059,457 B。
- **代码同源**：`git diff --name-only 464ebb8 1f4861b -- app/android` = 0 files；`e88ed0c→1f4861b` 同样 0。
- **点击锚点**：16/16 落在对应 dump 的 bounds 内。
- **6 类原因（`repro-round2.md` §4，豁免前分类，供定位用）**：命名不对齐 19 ／ 真缺失 9 ／ 待补采 2 ／ LC 增量 3 ／ dump-only 2 ／ 语义被替代 1。`lc-only` 四条说明：`dial-list-01`、`lc-knowledge-sheet` 为 LC 增量页（原版无对应态）；`launch-probe-01` 是冷启动探针，原版有对应 `orig/_launch-probe.png`、只是下划线前缀不参与配对（**不是 LC 独有能力**）；`map-roadnet-on` 是「原版无独立 stem」而**非原版缺该态**（索引行 22 的对照物是 `orig/map-home-01.xml`）。
- **复现命令覆盖**：客户端 14/14；地图脚本 7/15，另 8 态（搜索 2、画笔、钓点、轨迹、路网、叠加、图层）给出逐步坐标命令，已交 LC地图 并入 `collect_map_lc.ps1`。
- **未通过项**（见 `repro-round2.md` §6）：3D≡等高线同图、水系与探索拍成「看山脉」、36/38 待补采、`tool-measure-01` 无 dump（「png 必得、dump 尽力」）、地图 8 态无脚本覆盖、索引缺 `checksheet_id` 映射。

## 8 已知缺陷清单

### K-01 底部「罗盘」「地图」是同一页面的不同表现；罗盘浮层应叠加在地图中心；底部菜单栏无存在意义

| 项 | 值 |
|---|---|
| 来源 | 用户直接提出 |
| 第一轮级别 | 高（母条 = B-03 + B-04，`docs/review/99-FINAL-REVIEW.md` §3） |
| 现状态 | **已修复（关闭）**：结构上已满足用户诉求 |
| 证据 | 代码 `MainActivity.java:155-156`（只 `buildSinglePage()`）、`899`（点「罗盘」→ `setCompassMode(true)`）、`390-409`（`compassView` 叠在地图 WebView 上）；截图 `lc/tool-compass-page.png`；关闭记录 `docs/qa/bugs.md:17` |
| 残留 | 用户所说「要遮挡页面的组件与子页按原版设计逻辑放」**未全部完成**：R2-007（海拔）、R2-014（收藏点）、R2-013（读盘）、R2-015（更多）仍是独立页/对话框；R2-018（抽屉）整块缺失 |

> 结论：**「底部菜单栏」这条已关闭**，不再计入差异；用户诉求中「子页呈现方式」的部分转入 R2-007/013/014/015/018 跟踪。

## 9 风险与改进建议清单

| 编号 | 优先级 | 风险 | 现状证据 | 建议 |
|---|---|---|---|---|
| R2-R-01 | P0 | 采集脚本 3D 默认坐标错误会持续产出错态证据 | `tools/map/collect_map_lc.ps1:12` `(155,576)`；真实 `(1124,2430)` | 脚本改默认坐标 + 拍前断言 `LC3D mode=3d`（LC地图进行中） |
| R2-R-02 | P0 | 重复图/0 字节图会污染对照页与门禁 | 门禁已查 9 处；`map-3d-mode`≡`tool-contour-01` 逐字节相同 | 服务端 allow 列表 + 删除空图（进行中） |
| R2-R-03 | P1 | 颜色只取 2 点（`#FFFFFF` vs `#424242`），其余页未做 ΔE2000 | findings-client §9（未覆盖·颜色/动效） | 下一轮补取色脚本与 ΔE 阈值 |
| R2-R-04 | P1 | 动效不可比（动画三项置 0，清单 U6） | 采集前置固定 | 单列「动效不覆盖」声明，避免被当作已比对 |
| R2-R-05 | P1 | 3D 在 z12 未复现（设备 USB 掉线） | findings-map:12 | 设备恢复后补 z12 的 3D 态 |
| R2-R-06 | P2 | `map.html` 内部 3D 投影/pick/DEM 降级未复核 | findings-map §6（未覆盖·3D 细节） | 归 M3 代码复审，不与本轮差异结论混用 |
| R2-R-07 | P2 | 两批证据 `build.commit`（`e88ed0c`/`464ebb8`）与基线 `3d7fcb3` 不同，靠 diff 0 行核同源 | findings-client:8；findings-map:10 | 重采统一到当前 develop，meta 单一 commit |

## 10 待确认清单

| 编号 | 缺什么证据 | 由谁补 | 补齐后的判据 |
|---|---|---|---|
| R2-020（36） | 我方「全地图工具」页截图 + dump | LC客户端 | 双图后在原版 `tool-manage-01` 同态下比布局 |
| R2-022（38） | 我方「钓点→收藏地点」保存页截图 + dump | LC客户端 | 双图后判保存页视觉；登录墙按 DI |
| R2-028（C-04） | 3D 重采截图 + dump（真实中心 `(1124,2430)`） | LC地图 | 截图异于 2D、logcat `LC3D mode=3d` |
| R2-036（C-12） | 3D 可见态下等高线开/关双图 | LC地图 | 两图差异可量化（线密度/颜色） |
| R2-012（15） | 原版底栏完整按钮 bounds（清单 U4） | LC逆向 | 像素量取深色条 ROI 后定级 |
| R2-031（C-07） | 探索页正确截图 + dump | LC地图 | 与原版 3 分类卡片列表逐项比 |
| R2-035（C-11） | 原版画笔/钓点真实入口形态 | LC逆向 | 按原版复现路径重采后判定 |
| R2-037（C-13） | 产品决策：是否补相机/AR 入口 | 产品 + 总监 | 补做，或书面登记「不做」 |
| R2-017（33） | 原版天气页界面（VIP+FLAG_SECURE 不可取） | 总监裁定 | 判 NC，不做 1:1 扣分 |
| R2-024（增量-B） | 原版 14 篇 HTML 是否复刻 | 产品 + 总监 | 复刻，或登记 DI |

> **3D 证据状态（核验 N3）**：`map-3d-mode` 在 `d18928d` 被加回，`a97f5bd` 上仍在（sha256 `F037F735…`），`lc-captures.md:112` 仍标「实采（异常）」；findings 与门禁均按「**证据无效·待重采**」处理，不得引用为可用 3D 证据。

## 11 未覆盖范围声明

1. **桌面 M1**（`app/src/main.rs`、`dial.rs`、`software.rs`）：本轮只发 Android 任务，未比对（见 `docs/review/10-M1-desktop.md` 冻结基线审查）。
2. **`map.html` 内部 3D 细节**（2,796 行、119,698 B）：只核到入口与证据态，未审投影/pick 射线求交/DEM 降级（findings-map §6（未覆盖·3D 细节））。
3. **颜色与动效**：仅读盘 sheet 取 2 点；ΔE2000 与转场动效未做（findings-client §9（未覆盖·颜色/动效）-272）。
4. **真机项**：本轮两份 findings 未占设备；设备 USB 掉线期间 3D z12 未复现（findings-map:12）。
5. **36/38**：无我方截图，未判视觉；15 原版 bounds 未取证。
6. **核验**：`verify-round2.md` 已回填（28/37 = 75.7%，驳回 0）；未抽样 9 条仍「未核」，真机 12 项待设备复现。

## 12 证据索引

> 每行 `EV-R2-xxx` ↔ `R2-xxx` 一一对应；「出处」为 findings 行号（可回源）；「代码锚点」与 §4 一致。核验状态取自 `verify-round2.md`（28 条已核、9 条未抽样）；「成立·修正」指结论成立、描述已按 R1–R7 订正。

| EV | 编号 | 出处（文件:行） | 原版证据 | 我方证据 | 核验状态 |
|---|---|---|---|---|---|
| EV-R2-001 | R2-001 | findings-client:30 | `orig/_launch-probe.png` | `lc/launch-probe-01.png` | 成立 |
| EV-R2-002 | R2-002 | findings-client:194 | `orig/_launch-probe2.png` | 无 | 成立 |
| EV-R2-003 | R2-003 | findings-client:32 | `orig/map-home-01.png/.xml` | `lc/map-home-01.png/.xml` | 成立 |
| EV-R2-004 | R2-004 | findings-client:33 | `orig/map-poi-sheet-01.png/.xml` | 无 | 成立 |
| EV-R2-005 | R2-005 | findings-client:34 | `orig/tool-compass-page.png/.xml` | `lc/tool-compass-page.png/.xml` | 成立·修正 R1 |
| EV-R2-006 | R2-006 | findings-client:35 | `orig/compass-overlay-hidden-map.png/.xml` | `lc/compass-overlay-hidden-map.png/.xml` | 成立·修正 R2 |
| EV-R2-007 | R2-007 | findings-client:36 | `orig/compass-main-01.png/.xml` | `lc/compass-main-01.png/.xml` | 成立·修正 R3 |
| EV-R2-008 | R2-008 | findings-client:197 | `orig/tool-hdmap-01.xml` | `lc/tool-hdmap-01.png/.xml` | 成立 |
| EV-R2-009 | R2-009 | findings-client:195 | `orig/vip-paywall-01.xml` | 无 | 未核（未抽样） |
| EV-R2-010 | R2-010 | findings-client:39 | `orig/tool-compass-page.png/.xml` | `lc/win-02-compass.png/.xml` | 成立 |
| EV-R2-011 | R2-011 | findings-client:40 | 同上 | 同上 | 成立·修正 R4 |
| EV-R2-012 | R2-012 | findings-client:41 | `orig/win-02-compass.png` | `lc/win-02-compass.xml` | 成立·修正 R5 |
| EV-R2-013 | R2-013 | findings-client:42 | `orig/w3-sheet.png` | `lc/w3-sheet.png/.xml` | 成立·修正 R7 |
| EV-R2-014 | R2-014 | findings-client:43 | `orig/tool-favorite-01.png/.xml`、`favorite-list-02.xml` | `lc/tool-favorite-01.png/.xml`（`favorite-list-02` 已去重，与行 37 共用） | 成立 |
| EV-R2-015 | R2-015 | findings-client:44 | `orig/more-sheet-03-fresh.png/.xml` | `lc/more-sheet-03-fresh.png/.xml` | 成立 |
| EV-R2-016 | R2-016 | findings-client:45 | `orig/more-sheet-02-scrolled.xml` | `lc/more-sheet-03-fresh.xml`（与行 31 共用，已去重） | 成立 |
| EV-R2-017 | R2-017 | findings-client:196 | `orig/tool-weather-01.xml` | `lc/tool-weather-01.png/.xml` | 未核（未抽样） |
| EV-R2-018 | R2-018 | findings-client:47 | `orig/drawer-menu-03.png/.xml` | 无 | 成立 |
| EV-R2-019 | R2-019 | findings-client:48 | `orig/setting-map-01.png/.xml` | `lc/setting-map-01.png/.xml` | 成立 |
| EV-R2-020 | R2-020 | findings-client:49 | `orig/tool-manage-01.png/.xml` | 未采 | 未核（未抽样） |
| EV-R2-021 | R2-021 | findings-client:50 | `orig/favorite-list-02.png/.xml` | `lc/tool-favorite-01.png/.xml`（与行 17 共用） | 成立 |
| EV-R2-022 | R2-022 | findings-client:198 | `orig/favorite-addpoint-01.png/.xml` | 未采 | 未核（未抽样） |
| EV-R2-023 | R2-023 | findings-client:199 | 无对应 | `lc/dial-list-01.png/.xml` | 未核（未抽样） |
| EV-R2-024 | R2-024 | findings-client:53 | 无对应（14 篇 HTML） | `lc/lc-knowledge-sheet.png/.xml` | 未核（未抽样） |
| EV-R2-025 | R2-025 | findings-map:35 | `orig/search-result-01/02.png` | `lc/search-result-01/02.png` | 成立 |
| EV-R2-026 | R2-026 | findings-map:36 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 成立·修正 R6 |
| EV-R2-027 | R2-027 | findings-map:37 | `orig/map-layer-sheet.png` | `lc/map-layer-sheet.png` | 成立 |
| EV-R2-028 | R2-028 | findings-map:38 | `orig/map-3d-mode.png` | `lc/map-3d-mode.png` | 成立 |
| EV-R2-029 | R2-029 | findings-map:39 | `orig/map-overlay-sheet.png` | `lc/map-overlay-sheet.png` | 成立 |
| EV-R2-030 | R2-030 | findings-map:40 | `orig/map-mountains-entry.png` | `lc/map-mountains-entry.png` | 成立·定级订正 P0 |
| EV-R2-031 | R2-031 | findings-map:41 | `orig/map-explore-page.png` | `lc/map-explore-page.png` | 未核（未抽样） |
| EV-R2-032 | R2-032 | findings-map:42 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 成立 |
| EV-R2-033 | R2-033 | findings-map:43 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 成立 |
| EV-R2-034 | R2-034 | findings-map:44 | `orig/tool-track.png` | `lc/tool-track.png` | 未核（未抽样） |
| EV-R2-035 | R2-035 | findings-map:45 | `orig/tool-brush.png`、`tool-fishing.png` | `lc/tool-brush.png`、`tool-fishing.png` | 未核（未抽样） |
| EV-R2-036 | R2-036 | findings-map:46 | `orig/tool-contour-01.png` | `lc/tool-contour-01.png` | 成立 |
| EV-R2-037 | R2-037 | findings-map:47 | 无 | 无 | 成立 |

## 13 变更日志

| 版本 | 日期 | 改动 |
|---|---|---|
| v1.0.5 | 2026-11-06 | 按审核验 `6ec7614` 细化 R2-007 判据（含氧断言公式自洽 ±0.01、气压显式 ≠102.4、打点后点位一致）并补 R2-030 关账观测点 |
| v1.0.4 | 2026-11-06 | 同步 R2-007 判据订正（LC逆向 R10 `1871326`）：含氧量断言公式、海拔同源才 ±2 m、气压只断言字段+单位+来源、禁凑 102.4；R2-007 行与 §5.4 增「判据订正」块 |
| v1.0.3 | 2026-11-06 | 按总监最终裁定改 §2.1 为 **D-012…D-018 共 7 条**（顺延 CHARTER 已用至 D-011）；「推荐给好友」不占号，R2-018 行注「原版为子页，LC 走系统分享」 |
| v1.0.2 | 2026-11-06 | 按总监/复判口径改 §2.1 DI 台账为 **7 条 D-007…D-013**（推荐给好友不入 DI，属真实 Chooser 动作）；R2-018 关账口径同步为 7 项 |
| v1.0.1 | 2026-11-06 | 追加 §2.1 DI 台账 8 条（D-012…D-019：账户设置/投诉反馈/关于我们/联系我们/推荐给好友/开具发票/帮助中心/用户协议），编号因 D-007…D-011 已被 TEAM_CHARTER 占用而顺延；R2-018 行补关账口径（地图设置→SettingActivity、推荐给好友→Chooser、其余 7 项→InfoPageActivity + DI、toast 0） |
| v1.0 | 2026-11-06 | 回填 `verify-round2.md`：§7 填 28/37 核验结论（驳回 0）与 R1–R7 核验订正；C-06 升 P0 → **P0 7 / P1 13 / P2 5 / NC 3**；§2 增「现象去重 35」口径；§10 补 3D 证据状态；§12 填 37 条核验状态；§11 更新未覆盖 |
| v0.9 | 2026-11-06 | 执行总监裁定：C-09 回 **P1**（R2-033 移入 §4.2）、C-07 记 **NC·未确认（证据错态）**（R2-031 移入 §4.4）；统计改为 P0 6/P1 14/P2 5/NC 3，§4.2 14 条、§4.3 5 条、§4.4 12 条；基线更新为 `ef6d0e4`（v0.2 已并入主线） |
| v0.2 | 2026-11-06 | 汇总两份 findings：37 条 `R2-001..037`、`EV-R2` 一一对应；补严重度统计、六类归并、Top 差异、三类原因分析、被否条目留位、K-01 关闭记录、风险 7 条、待确认 10 条、未覆盖 6 条 |
| v0.1 | 2026-11-06 | 口径更正：`develop` 基线更新为 `464ebb8`；证据基线 190 文件（`lc/` 56） |
| v0.0 | 2026-11-06 | 建稿：路径裁定、取材口径、八字段契约、编号与证据索引规则、K-01 承继位 |
