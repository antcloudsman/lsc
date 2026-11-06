# LC 侧证据索引（Stage 2「我方对齐采集」）

对照对象固定为 `docs/fidelity/orig-inventory.md`（`5e30718`，38 条，A–F 六节）。
本文件是「清单 id → 我方截图/dump」的唯一索引；地图侧章节由 LC地图 补在文末。

## 0. 构建来源（检查表 §5 硬门槛）

| 项 | 值 |
|---|---|
| 分支 | `develop` |
| 提交 | `e88ed0c2aa70272e36f6d03a0a6f12511d6bf532`（`e88ed0c`） |
| APK | `app/android/app/build/outputs/apk/debug/app-debug.apk`，7,059,509 B |
| APK sha256 | `8dd5bf13dbbd0763b961a42df8bb35706f743ae2338d3bbd336f748ed0ab1763` |
| 采集工具 | `tools/fidelity/fidelity.py`（`--build-branch/--build-commit/--apk`，meta 内 `build.*` + `apk.*`） |
| 设备 | `L2E0222531014833`，1228×2700，动画三项 0，罗盘 heading 冻结 `0°` |

`lc/*.meta.json` 每份都带 `build.branch=develop`、`build.commit`、`apk.sha256`、`apk.bytes`
（`launch-probe-01` 为冷启动探针，`dump` 为 `null`，见 §3 说明）。

**`cd47703` 作废原因**：该批 `compass-main-01` 装的包是 `fix/p0-review-findings` 构建
（`D15BE329…`，6,580,242 B），包内 `assets/map.html` 只有 64,905 B（develop 为 119,698 B，
少 54,793 B 地图代码，`*weather*` 资源 0 条），截图整屏黑底。本目录同名文件已用 develop 构建覆盖。

**跨端校验口径**：两端各自构建同一个 `e88ed0c`，APK 字节不同（客户端 7,059,509 B / `8DD5BF13…`，
地图 7,059,457 B / `CCD5686F…`，签名与打包非确定性所致），因此**只按 `build.commit` 判同源，
不拿 `apk.sha256` 互比**；`apk.sha256` 只用于证明「本批证据装的是本批记录的那个包」。

### 0.1 分批次采集的构建来源

不同批证据按当时 `develop` tip 构建，**一批一个 commit**，逐条以 `meta.build.commit` 为准：

| 批次 | 提交 | APK | 覆盖 stem |
|---|---|---|---|
| 客户端章节首批 | `e88ed0c`（`e88ed0c2aa70272e36f6d03a0a6f12511d6bf532`） | 7,059,509 B / `8DD5BF13…` | 14 个 stem（含 `map-home-01`、罗盘页、读盘、更多面板、设置、天气、收藏点空态、超清地图、增量 2 个） |
| 客户端章节 D2 | `bd0323b`（`bd0323b0e64a71f660ff322f33b31de42c36f432`） | 7,059,481 B / `381D163E…` | `tool-manage-01`、`favorite-addpoint-01` |
| 地图章节 | `464ebb8` + `bd0323b`（新采 `map-3d-img` 等） | 见 §4 口径行 | 见 §4 |

D2 采集注意：钓点页「加钓点」会写入 1 条自建钓点（`FishingActivity` 的 `SharedPreferences lc_fish/points`），
复跑时「本机自建」计数 +1；这是打开「保存收藏点」页的唯一入口，不是伪造数据。**该点只在钓点页渲染，
主地图不读它**（`map.html` 的标记只来自点图 `placeMarker`），因此不影响地图固定态截图。

### 0.2 基线纪律（跨批次对照与干净基线）

1. **不比图哈希，只比结构**：同一 UI 状态在不同时间点截图 SHA256 会变（瓦片/渲染时序），实测同一节点树
   `68469C5C…`（17:45）vs `AC06F0B5…`（19:49）。**跨批次只比控件 bounds / 文案 / 颜色**；orig↔lc 双图对照
   只在同一批次内做。`report` 的「不同 state 同图」判重是**同批次内**的检查，不受此影响。
2. **固定态基线会被「立向点」累积污染**：每次点地图都会新增一个 `立向点 向X 坐Y` 标记
   （`map.html:1730` `mk.title = "立向点 …"`），点 N 次就是 N 个，**隐藏态/固定态截图随之变化**。
   这也是 `client09` 在 D1 被判 FAIL 的真因（判据写死了旧 sha），不是行为改变，也不是钓点。
3. **干净基线做法**（采集前执行，已验证可用）：
   ```powershell
   adb -s L2E0222531014833 shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'clearMarkers()'"
   ```
   （`map.html:1734` `clearMarkers()`，会连带发 `pick-clear`）；或 `am force-stop` 后冷启动。
4. **D2 副作用边界**：`加钓点` 写的是 `lc_fish/points`（仅钓点页），**不写收藏点库、不画主地图**；
   要恢复干净钓点列表可用 `pm clear com.luopan.fengshui`（会一并清掉 heading/偏角等 prefs，需重设前置）。

## 1. 客户端章节：清单 38 条 → 我方证据

「状态」列：`有` = 我方存在同态证据；`缺失` = LC 无该入口/该能力，附证据位置；
`地图章节` = 属地图模块（LC地图 采，见文末章节）；`等价` = 形态不同但同功能。

| # | checksheet_id | 清单条目（原版 stem） | 状态 | 我方文件（`docs/fidelity/lc/`） | 说明 / 证据 |
|---|---|---|---|---|---|
| 01 | `ST-A-launch` | 启动页（`_launch-probe` 探针） | 等价 | `launch-probe-01.png`（无 dump） | LC 无独立 Splash：冷启动 2 s 内直接进入地图主界面；探针图即冷启动第 2 s 画面，`uiautomator` 在 WebView 首帧时 `could not get idle state`，故该 stem 只有 png + meta（`dump: null`） |
| 02 | `R2-002` | 开屏广告（`_launch-probe2.png`） | 缺失 | — | LC 无广告 SDK/开屏位；证据：`launch-probe-01.png` 冷启动直接是地图页，`more-sheet-03-fresh.xml` 条目里无任何广告入口 |
| 03 | `ST-A-map-home` | 地图主界面（`map-home-01`） | 有 | `map-home-01.png/.xml/.meta.json` | 默认位置（全国视野），左列 8 项、右列 10 项、顶栏 搜索/图层、底部角注条 |
| 04 | `ST-A-map-fixed` | 地图固定态（`map-beijing-z12`） | 地图章节 | — | 固定态由 LC地图 采（`setView(39.9042,116.4074,12)`） |
| 05 | `R2-004` | POI 详情面板（`map-poi-sheet-01`） | 缺失 | — | LC 无 POI 详情面板；证据：`map-home-01.xml` 全节点表无详情容器，地图点选只回 `MapMarker`（3D 点选）不弹面板 |
| 06 | `ST-A-search-empty` | 搜索页空结果（`search-result-01`） | 地图章节 | — | 顶栏「搜索」[1040,192]-[1215,306]（见行 03 dump），搜索面在 `map.html` 内，归地图章节 |
| 07 | `ST-A-search-coords` | 搜索页四坐标系（`search-result-02`） | 地图章节 | — | 同上 |
| 08 | `ST-A-compass-page` | 罗盘浮层页（`tool-compass-page`） | 有 | `tool-compass-page.png/.xml/.meta.json` | 24 山盘 + 度数 + 太极 + 读数面板；左列 6 项、右列 4 项、底栏 5 项 |
| 09 | `ST-A-compass-hidden` | 罗盘浮层（读数条 + 隐藏，`compass-overlay-hidden-map`） | 有 | `compass-overlay-hidden-map.png/.xml/.meta.json` | 点顶栏「隐藏」后盘面消失、地图与工具列保留；与 03 逐像素差 2,169,388 px（65.4%） |
| 10 | `ST-01-compass-main` | 海拔查询 POI 面板（`compass-main-01`） | 有 | `compass-main-01.png/.xml/.meta.json` | 左列「海拔查询」→ 点地图：纬度/经度/海拔 55.0 m/地址/含氧量 20.88 %/气压 1006.7 hPa/关闭 |
| 11 | `ST-A-hdmap` | 超清地图（VIP 引导，`tool-hdmap-01`） | 有 | `tool-hdmap-01.png/.xml/.meta.json` | LC 无 VIP 墙，直接是可用的「2× 放大（双三次）→ 生成超清图」页 |
| 12 | `R2-009` | 付费墙（`vip-paywall-01`） | 缺失 | — | LC 全功能免费，无付费墙/VIP 页；证据：`more-sheet-03-fresh.xml` 16 个工具条目全部可直接进入 |
| 13 | `ST-A-compass-page / ST-A-compass-columns` | 左列工具条（`tool-compass-page` + `win-02-compass`） | 有 | `win-02-compass.png/.xml/.meta.json` | 截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地，bounds 见 dump |
| 14 | `ST-A-compass-columns` | 右列（`win-02-compass`） | 有 | `win-02-compass.*` | 地图/实景/坐南朝北/指针开关 |
| 15 | `ST-A-compass-columns` | 底栏（`win-02-compass`） | 有 | `win-02-compass.*` | 切换/旋转/缩放/透明度/天支线 |
| 16 | `ST-A-read-sheet` | 智能读盘弹层（`w3-sheet`） | 有 | `w3-sheet.png/.xml/.meta.json` | 14 行「类型名 向：X 坐：Y」逐行完整（含先天卦位/洛书数/三盘/分金/七十二龙/六十龙） |
| 17 | `ST-A-favorite-mode` | 收藏点模式（`tool-favorite-01`） | 有 | `tool-favorite-01.png/.xml/.meta.json` | LC 打开的是独立列表页 `InterestListActivity`（`MainActivity.java:1417-1419`，F-11 默认开），空态文案 `lc_interest_empty`「暂无收藏点」，dump 仅 3 节点；不是原版「地图内收藏点模式 + 右列收藏列表」形态，形态差异见 findings-client P1-17 |
| 18 | `ST-C-measure` | 测量模式（`tool-measure-01`） | 地图章节 | — | 入口在行 31 dump「测量」[359,1372]-[613,1534] |
| 19 | `ST-C-brush` | 画笔模式（`tool-brush`） | 地图章节 | — | 入口「画笔」[613,1372]-[867,1534] |
| 20 | `ST-C-fishing` | 钓点（`tool-fishing`） | 地图章节 | — | 入口「钓点」[867,1372]-[1121,1534] |
| 21 | `ST-C-track` | 轨迹记录 → 登录墙（`tool-track`） | 地图章节 | — | 入口「轨迹记录」[106,1540]-[359,1702]；LC 无登录墙（见 38） |
| 22 | `ST-A-map-home` | 路网（图层面板，`map-home-01.xml`） | 地图章节 | — | 左列「路网」[6,1999]-[201,2127]（`map-home-01.xml`） |
| 23 | `R2-036` | 等高线首用引导（`tool-contour-01`） | 地图章节 | — | 左列「等高线」[6,2194]-[201,2322] |
| 24 | `ST-C-mountains` | 山脉（`map-mountains-entry`） | 地图章节 | — | 右列「山脉」[1027,1008]-[1222,1136] |
| 25 | `ST-C-water` | 水系（`map-water-entry`） | 地图章节 | — | 右列「水系」[1027,1203]-[1222,1331] |
| 26 | `ST-C-overlay` | 数据叠加面板（`map-overlay-sheet`） | 地图章节 | — | 右列「叠加」[1027,814]-[1222,942] |
| 27 | `ST-C-explore` | 探索页（`map-explore-page`） | 地图章节 | — | 右列「探索」[1027,1398]-[1222,1526] |
| 28 | `ST-C-layer` | 图层（`right-layer-sheet`） | 地图章节 | — | 右列「图层」[1027,619]-[1222,747] / 顶栏「图层」[865,192]-[1040,306] |
| 29 | `ST-C-3d` | 3D 模式（`map-3d-mode`） | 地图章节 | — | 右列「3D」[1027,2366]-[1222,2494] |
| 30 | `R2-037` | 相机权限弹窗（`right-layer-sheet.png`） | 地图章节 | — | 相机相关入口在相机/AR 路径，归地图章节 |
| 31 | `ST-D-more-default` | 地图工具面板（默认态，`more-sheet-03-fresh`） | 有 | `more-sheet-03-fresh.png/.xml/.meta.json` | 标题「地图工具」；16 项：罗盘/超清地图/看山脉/看水系/海拔查询/天气/历史影像/等高线/收藏点/测量/画笔/钓点/轨迹记录/路网/地理影像/超清优化，另有「设置」「功能开关（调试）」 |
| 32 | `R2-016` | 地图工具面板（滚动后，`more-sheet-02-scrolled`） | 有 | **与 31 共用** `more-sheet-03-fresh.png/.xml/.meta.json` | **实测不可滚动**：`input swipe 614 2300 614 1500 300` 后 dump 与 31 的 SHA256 完全相同（`EC23C862…5DC7`），面板一屏装下 16 项；同态共用一份证据，不另存第二张同图 |
| 33 | `ST-F-weather` | 天气页（`tool-weather-01`） | 有 | `tool-weather-01.png/.xml/.meta.json` | LC 有真实数据：城市 北京、14°、晴、西风 2 级、湿度 16 %、空气质量 19、7 日预报、数据源「中国天气网 · 更新 13:45」；原版为 VIP 门禁（U1） |
| 34 | `R2-018` | 抽屉菜单（`drawer-menu-03`） | 缺失 | — | LC 无汉堡抽屉/侧栏；证据：`map-home-01.xml` 顶栏只有 搜索[1040,192]-[1215,306]，无左上汉堡点击区（原版 [35,140]-[160,265] 处为地图控件） |
| 35 | `ST-E-map-setting` | 地图设置（`setting-map-01`） | 有 | `setting-map-01.png/.xml/.meta.json` | 地图滤镜/模式四 chip 常规[39,584]-[284,681]、自然[340,584]、高亮[641,584]、自定义[942,584]、地图效果预览、显示路网、允许地图旋转、回到上次浏览位置、屏幕常亮、3D 操作按钮、中心十字架、坐标系 WGS84 |
| 36 | `ST-E-tool-manage` | 工具管理（`tool-manage-01`） | 有 | `tool-manage-01.png/.xml/.meta.json` | D2 补采：`ToolsManagerActivity`。入口：地图左列「全地图工具」[6,432]-[201,560] 中心 `(103,496)`（`MainActivity.java:901-903`，F-01 默认开）。实测：标题栏 `‹`[0,122]-[176,298]、「首页展示功能」[41,373]-[1187,446]+说明、快捷入口 超清优化[92,768]/海拔查询[361,768]/罗盘[630,768]/路网[900,768]/等高线[92,1005]、「全部功能」[41,1213]-[1187,1286]、全部功能 16 项自 y1465 起 |
| 37 | `R2-021` | 收藏列表入口（`favorite-list-02`） | 有 | **与 17 共用** `tool-favorite-01.png/.xml/.meta.json` | LC 收藏点页只有空态「暂无收藏点」，**无独立收藏列表入口**（dump 仅 3 节点：返回/标题/空态文案）；原版行 37 的「收藏列表点击区 [1043,506]-[1201,664]」在 LC 不存在，同态共用一份证据 |
| 38 | `ST-E-favorite-save` | 收藏保存 → 登录墙（`favorite-addpoint-01`） | 有 | `favorite-addpoint-01.png/.xml/.meta.json` | D2 补采：`SaveInterestActivity`「保存收藏点」。入口：更多 → 钓点[867,1372]-[1121,1534] → 右上「加钓点」[904,325]-[1201,487] → 点自建标记「塘」[550,1394]-[678,1519] → 详情对话框「收藏地点」[147,1456]-[458,1618]（`FishingActivity.java:417-425`）。实测：名称(可空)[68,344]-[1160,487]、坐标 `39.904200, 116.407400`[655,528]、坐向 `坐180.0° 向0.0°`[841,646]、时间 `2026-11-06 19:37`[765,764]、保存[68,922]-[1160,1084]。**LC 无登录墙**：原版 38「添加点 → LoginActivity」对应 LC 本地保存表单，登录墙按 **DI** 登记 |

统计（按 §1 表逐行数）：**有 17 + 等价 1 + 地图章节 16 + 缺失 4 + 待补采 0 = 38**。缺失项均给证据位置；
D2 已把 36/38 从「待补采」补成实采（构建 `bd0323b`，见 §0.1），复现不了的仍退回「缺失」。

**同态共用**：行 32↔31（更多面板不可滚动）、行 37↔17（收藏点只有空态、无收藏列表入口）各共用一份证据，
重复文件已删除（`more-sheet-02-scrolled.*`、`favorite-list-02.*`），因此 `report` 的「不同 state 同图」硬检查
不会命中这两行，**不需要进 `duplicates-allow.json`**。客户端章节现有 **16 PNG + 15 XML + 16 meta**。

## 2. LC 增量证据（原版清单外，我方自创新增）

| stem | checksheet_id | 文件 | 说明 |
|---|---|---|---|
| `dial-list-01` | `ST-A-dial-list` | `dial-list-01.png/.xml/.meta.json` | 知识库 →「盘面（5 种，全部免费）」：二十四山(阴)/常用罗盘/三合罗盘(白)/三元罗盘… + 确定 |
| `lc-knowledge-sheet` | `LC-EXTRA-knowledge` | `lc-knowledge-sheet.png/.xml/.meta.json` | 知识库弹层：搜索框（盘面/圈层/知识条目）、盘面（5 种）、圈层（14 种读数）、二十四山/三盘/百二十分金/穿山七十二龙/透地平分六十龙 + 关闭 |

## 3. 一键复跑

```powershell
pwsh -File docs\fidelity\collect_lc_client.ps1 -BuildCommit e88ed0c2aa70272e36f6d03a0a6f12511d6bf532
```

脚本按本文件的顺序装包 → 置动画 0 → 冻结 heading 0° → 逐个状态 `collect`，参数与本文件 §0 完全一致；
每步失败即中止，不留无声证据。冷启动探针单独用 `--skip-dump`（`uiautomator` 在 WebView 首帧取不到 idle）。

## 4. 地图侧章节（LC地图）

采集口径：`--build-commit 464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a`（`develop` `464ebb8`）、APK sha256 `CCD5686F…`（7,059,457 B）、heading 冻结 0°、地图 `setView(39.9042,116.4074,12)`；脚本 `tools/map/collect_map_lc.ps1`（`7253788`）。`map-home-01` 复用 §1 第 03 行（同 commit）。

| 行 | checksheet_id | stem | 状态 | 证据 | 实测要点 |
|---|---|---|---|---|---|
| 04 | `ST-A-map-fixed` | `map-beijing-z12` | 实采 | png+xml+meta | 固定态北京 z12；左列 8、右列 10、底部角注条 `[0,2544][1228,2578]` |
| 06 | `ST-A-search-empty` | `search-result-01` | 实采 | png+xml+meta | 搜索页默认（空结果） |
| 07 | `ST-A-search-coords` | `search-result-02` | 实采 | png+xml+meta | 输入 `116.4074,39.9042` 后结果（四坐标系） |
| 18 | `ST-C-measure` | `tool-measure-01` | 实采（png-only） | png+meta | 测量模式；该页持续动画导致 `uiautomator dump` idle 超时，`--skip-dump`，meta `dump:null` |
| 19 | `ST-C-brush` | `tool-brush` | 实采 | png+xml+meta | 画笔模式 |
| 20 | `ST-C-fishing` | `tool-fishing` | 实采 | png+xml+meta | 钓点 |
| 21 | `ST-C-track` | `tool-track` | 实采 | png+xml+meta | 历史轨迹（无登录墙；空态「暂无历史轨迹记录」） |
| 22 | `ST-C-roadnet` | `map-roadnet-on` | 实采（LC 侧同态） | png+xml+meta | 路网叠加开启；原版该行无独立 stem（对照物见行 03 dump） |
| 23 | `R2-036` | `tool-contour-01` | 缺失（固定态不可得，待原版补采） | 补充证据 `map-contour-relief` | 固定态（39.9042/116.4074 z12）地形起伏≈0，等高线开关前后 canvas 渲染哈希完全相同（`13567:1033006902` 三次一致）；同一引擎在 40.35/116.00 z12 开启后哈希变化（`12547:4200479435`→`12575:898838800`），故能力存在、固定态不可见。原 `tool-contour-01.*` 与 3D 态逐字节相同：**D1 复核（`verify-round2.md` §3.9）确认两者都真在 3D**（`contour on=true uniform=1`），同图是平坦地形无可画等高线，不是状态没进；文件已删除以免被当成两态。**原版同态对照尚未取得**（原版 App 启动阶段 ANR，见 §4.2），R2-036 维持「未确认」 |
| 24 | `ST-C-mountains` | `map-mountains-entry` | 实采 | png+xml+meta | 「看山脉」页：视野内山名 120 个、山脊/刷新视野 |
| 25 | `ST-C-water` | `map-water-entry` | 实采 | png+xml+meta | 「看水系」页（D0 重采：`mResumedActivity=.WaterActivity`、dump 含 `看水系`、不含 `看山脉`） |
| 26 | `ST-C-overlay` | `map-overlay-sheet` | 实采 | png+xml+meta | 「数据叠加」页：4 Tab + 公路(路网) + 全部开启/关闭 |
| 27 | `ST-C-explore` | `map-explore-page` | 实采 | png+xml+meta | 「探索」页（D0 重采：`mResumedActivity=.DiscoveryActivity`、dump 含 `探索`/`专题地图`，不含 `看山脉`） |
| 28 | `ST-C-layer` | `map-layer-sheet` | 实采 | png+xml+meta | 右列「图层」→「底图源」12 源面板（3 可用 / 9 置灰） |
| 29 | `ST-C-3d` | `map-3d-mode` | 实采 | png+xml+meta | 点 3D `(1124,2430)` 后断言通过：`logcat LC3D mode=3d` + `texture ok 36/36`、`getState()` = `mode=3d zoom=12 pitch=45 contour=false drape=vec`、`fps=56~60`；png 与固定态不同（`9cf8b080…` vs `68469c5c…`）。**D1 复核订正**：旧证据 `f037f735…` 同样是真 3D（`drape=vec` + 平坦地形 + pitch45 → 画面近矢量路网），「实为 2D」的旧说法撤回；`vec` 贴面下 3D 与 2D 视觉难辨，是**证据可读性**问题，不是渲染缺陷。可读性补充证据见 §4.1 末行（卫星贴面 3D，`setBase('img')` → `drape=img`，同样断言 `mode=3d/pitch=45/zoom=12/hasTexture=true`） |
| 30 | `R2-037` | 相机权限弹窗 | 缺失 | — | LC 无相机/AR 入口（`map-home-01.xml` 全节点无相机相关控件） |

统计：**实采 14 + 缺失 2 = 16**，无「待采」；另加 1 份补充证据 `map-contour-relief`（第 23 行的能力证明，不占清单行）。

### 4.1 D0 重采记录（2026-11-06 17:12–17:16）

一条命令复跑：`pwsh -File tools\map\collect_map_lc.ps1 -Apk <develop 464ebb8 构建 apk> -Commit 464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a -Only <state,...>`（脚本 `beb8c47`，采集前逐态断言）。

| stem | sha256（前 16） | 字节 | mtime | 断言 |
|---|---|---|---|---|
| `map-3d-mode` | `9cf8b080a998c9f6` | 1,847,514 | 17:14:53 | `LC3D mode=3d`、`texture ok 36/36`、`getState().mode=3d` |
| `map-water-entry` | `c1499a5703444e65` | 2,906,691 | 17:12:18 | `mResumedActivity=.WaterActivity`、dump 含 `看水系`、不含 `看山脉` |
| `map-explore-page` | `077b61228f1de6f2` | 173,185 | 17:12:36 | `mResumedActivity=.DiscoveryActivity`、dump 含 `探索`、不含 `看山脉` |
| `map-contour-relief` | `334427de4cc54059` | 2,572,309 | 17:15:36 | 40.35/116.00 z12、`contour=true`、画面含黑色等高线（补充证据） |
| `map-3d-img` | `0da7d801a5664235` | 3,100,385 | 19:29:43 | 固定态 3D + `setBase('img')` → `drape=img`、`mode=3d/pitch=45/zoom=12/hasTexture=true`（可读性补充证据） |

- 3D 重复图已消：`map-3d-mode`(`9cf8b080…`) ≠ 任何其它 state；`tool-contour-01.*` 已删除。
- 采集期间 USB 瞬断一次（`adb: device not found`），重连后重跑补齐，所有 meta `build.commit` = `464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a`、`apk.sha256` = `ccd5686f…`（7,059,457 B）。
- 等高线「固定态不可见」判定依据不是肉眼：`canvas.toDataURL` 缩略图哈希在 contour off/on/off 三次均为 `13567:1033006902`；同一引擎在山区同参数下 off=`12547:4200479435`、on=`12575:898838800`。

### 4.2 原版同态对照补采受阻（2026-11-06 17:24–17:32 / 19:44–19:52 两轮）

为给第 23 行取「原版固定态 3D + 等高线」对照，按 `orig-inventory.md` 第 04 行口径启动原版 App（`space.xinzhi.globe`），**两轮均未进入地图页**：

| 尝试 | 观察 | 结果 |
|---|---|---|
| 17:24 轮：`am start -n space.xinzhi.globe/.ui.pages.SplashActivity`，静置 60 s | 停在启动图；`mResumedActivity` 始终 `SplashActivity`、`mCurrentFocus=null`；截图 sha `ff4b1aee4469afb1…`（2,714,248 B）不变 | 未进入地图 |
| 17:24 轮：点击 `(614,1350)` / `(1100,200)` / `BACK` | 弹出系统 ANR：「三维山水全景地图 无响应。是否将其关闭？」截图 sha `ddd0ff2558a3e415…` | ANR |
| 19:44 轮：**重启手机**后（解锁完成、`boot_completed=1`，未改原版任何数据/配置）再启动，静置 90 s | 仍停在同一启动图（sha 同上 `ff4b1aee…`）；`mCurrentFocus=null`；进程 `0.0% CPU`、`State: S`、18 线程；`logcat --pid` 最后一条是 `LoadedApk: createSharedLibrariesLoaders::sharedLibraries is null`，此后无 Activity/WebView 初始化日志 | 未进入地图 |
| 19:52 轮：`am start -W -n …SplashActivity` | `Status: timeout`、`LaunchState: UNKNOWN (-1)`、`WaitTime: 10064 ms`；随即系统窗口 `Application Not Responding: space.xinzhi.globe` 获焦 | ANR |

排查：`MemAvailable` = 3,415,784 kB（约 3.4 GB，非内存不足）；没有任何权限弹窗窗口（`dumpsys window windows` 无 `GrantPermissions` 行），`ACCESS_FINE_LOCATION`/`COARSE` 为 `granted=false` 但应用在弹窗之前就已阻塞；`logcat` 无 `FATAL EXCEPTION`。进程存活于 `MyWrapperProxyApplication`（vmInterpret 保护壳），主线程 0% CPU 等待。采集纪律要求原版「只作对照、不改数据」，故**未清数据**；按总监给的选项执行了「重启手机」，仍不通。

结论：原版启动阻塞在保护壳初始化，早于任何可交互界面，因此**原版「固定态 3D + 等高线开/关」对照在本环境无法取得**；第 23 行保持「缺失（固定态不可得，待原版补采）」，R2-036 维持 **NC/未确认**，不外推为「等价」或「缺陷」。LC 侧 `map-contour-relief` 只证明我方能力，不用于判定原版行为。

附：本目录 `orig-inventory.md` 第 80 行把原版 3D 入口记为 `(1125,1985)`，与 `orig/map-home-01.xml` 实测不符——原版右列 3D 的真实 bounds 是 `[1055,2242]-[1212,2399]`、中心 `(1134,2320)`（同列依次 `图层 613`、`叠加 799`、`山脉 985`、`水系 1171`、`探索 1357`、指北针 `1708`、`+ 1860`、`− 2014`、`定位 2168`）。后续复现请按 `(1134,2320)` 点。



**采集过程（可复现）**：`uiautomator dump` 在传感器活跃时报 `could not get idle state`，先 `am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 0` 冻结航向即稳定；装包后立即广播可能被忽略（应用未完成 onResume），需等 4 s。3D 按钮真实 bounds `[1027,2366]-[1222,2494]`、中心 `(1124,2430)`（点 `(1126,2187)` 落在 `−` 与 `定位` 之间的缝里）。

## 5. missing-lc 29 条 → R2 编号对齐（D3）

口径：`report` 按同名 stem 配对，下列 29 个 stem 只有原版图、没有我方同名图，计入 `missing-lc`。「分类」列是 Stage 1 判据 4 的两类拆分（另列载体差异）：**真缺失**＝LC 无该能力/形态，等 P0 实现后自然消失；**命名不一致**＝同一画面我方 stem 不同，重采时按原版 stem 改名即可闭合；**载体不同**＝LC 有等价载体、已按 DI 登记。四族 `drawer-menu`/`favorite-list`/`poi-select`/`search-*` 一律按真缺失记，不当命名问题处理。「依据」列说明该 stem 如何定位到清单行：**清单直引**＝清单/索引/报告正文写到过它；**同族采集**＝同一画面的另一次采集（映射用，不是差异定性）。

| # | stem（orig） | 清单行 | R2 编号 | 分类 | 依据 |
|---|---|---|---|---|---|
| 01 | `drawer-menu` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 同族采集：行 34 抽屉菜单（drawer-menu-03 为直引） |
| 02 | `drawer-menu-01` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 同族采集：行 34 抽屉菜单 |
| 03 | `drawer-menu-02` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 同族采集：行 34 抽屉菜单 |
| 04 | `drawer-menu-03` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 清单直引 |
| 05 | `drawer-menu-04` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 同族采集：行 34 抽屉菜单 |
| 06 | `drawer-menu-05` | 34 | R2-018 | 真缺失（P0 实现后自然消失） | 同族采集：行 34 抽屉菜单 |
| 07 | `favorite-list-01` | 37 | R2-021 | 真缺失（P0 实现后自然消失） | 同族采集：行 37 收藏列表（favorite-list-02 直引） |
| 08 | `favorite-list-02` | 37 | R2-021 | 真缺失（P0 实现后自然消失） | 清单直引 |
| 09 | `favorite-list-03` | 37 | R2-021 | 真缺失（P0 实现后自然消失） | 同族采集：行 37 收藏列表 |
| 10 | `favorite-list-04` | 37 | R2-021 | 真缺失（P0 实现后自然消失） | 同族采集：行 37 收藏列表 |
| 11 | `favorite-mode-01` | 17 | R2-014 | 真缺失（P0 实现后自然消失） | 同族采集：行 17 收藏点模式 |
| 12 | `favorite-mode-02` | 17 | R2-014 | 真缺失（P0 实现后自然消失） | 同族采集：行 17 收藏点模式 |
| 13 | `favorite-save-01` | 38 | R2-022 | 载体不同（DI 登记） | 同族采集：行 38 收藏保存（favorite-addpoint-01 直引） |
| 14 | `favorite-save-login-01` | 38 | R2-022 | 载体不同（DI 登记） | 同族采集：行 38 收藏保存登录墙 |
| 15 | `map-jump-beijing` | 04 | R2-026 | 命名不一致（重采改名对齐） | 同族采集：行 04 固定态（lc 对应 map-beijing-z12） |
| 16 | `map-poi-sheet-01` | 05 | R2-004 | 真缺失（P0 实现后自然消失） | 清单直引 |
| 17 | `more-sheet-01` | 31 | R2-015 | 命名不一致（重采改名对齐） | 同族采集：行 31 更多面板（more-sheet-03-fresh 对应） |
| 18 | `more-sheet-02-scrolled` | 32 | R2-016 | 命名不一致（重采改名对齐） | 清单直引 |
| 19 | `more-sheet-04-edit-entry` | 31 | R2-015 | 命名不一致（重采改名对齐） | 同族采集：行 31 更多面板编辑入口 |
| 20 | `poi-select-01` | 05 | R2-004 | 真缺失（P0 实现后自然消失） | 同族采集：行 05 POI 详情（map-poi-sheet-01 直引） |
| 21 | `poi-select-02` | 05 | R2-004 | 真缺失（P0 实现后自然消失） | 同族采集：行 05 POI 详情 |
| 22 | `right-3d` | 29 | R2-028 | 命名不一致（重采改名对齐） | 同族采集：行 29 3D（lc 对应 map-3d-mode） |
| 23 | `right-explore` | 27 | R2-031 | 命名不一致（重采改名对齐） | 同族采集：行 27 探索（lc 对应 map-explore-page） |
| 24 | `right-layer-sheet` | 28 | R2-027 | 命名不一致（重采改名对齐） | 清单直引 |
| 25 | `search-result-crs-01` | 07 | R2-025 | 真缺失（P0 实现后自然消失） | 同族采集：行 07 四坐标系结果（lc 对应 search-result-02） |
| 26 | `search-typed-coords-01` | 06 | R2-025 | 真缺失（P0 实现后自然消失） | 同族采集：行 06 搜索页（lc 对应 search-result-01） |
| 27 | `tool-compass-01` | 08 | R2-005 | 命名不一致（重采改名对齐） | 同族采集：行 08 罗盘浮层（lc 对应 tool-compass-page） |
| 28 | `tool-contour-01` | 23 | R2-036 | 真缺失（P0 实现后自然消失） | 清单直引 |
| 29 | `tool-elevation-01` | 10 | R2-007 | 真缺失（P0 实现后自然消失） | 同族采集：行 10 海拔查询（lc 对应 compass-main-01） |

合计 **29** 条：清单直引 6 条、同族别名 23 条、未取证 0 条
