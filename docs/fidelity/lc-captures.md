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

## 1. 客户端章节：清单 38 条 → 我方证据

「状态」列：`有` = 我方存在同态证据；`缺失` = LC 无该入口/该能力，附证据位置；
`地图章节` = 属地图模块（LC地图 采，见文末章节）；`等价` = 形态不同但同功能。

| # | checksheet_id | 清单条目（原版 stem） | 状态 | 我方文件（`docs/fidelity/lc/`） | 说明 / 证据 |
|---|---|---|---|---|---|
| 01 | `ST-A-launch` | 启动页（`_launch-probe.png`） | 等价 | `launch-probe-01.png`（无 dump） | LC 无独立 Splash：冷启动 2 s 内直接进入地图主界面；探针图即冷启动第 2 s 画面，`uiautomator` 在 WebView 首帧时 `could not get idle state`，故该 stem 只有 png + meta（`dump: null`） |
| 02 | `R2-002` | 开屏广告（`_launch-probe2.png`） | 缺失 | — | LC 无广告 SDK/开屏位；证据：`launch-probe-01.png` 冷启动直接是地图页，`more-sheet-03-fresh.xml` 条目里无任何广告入口 |
| 03 | `ST-A-map-home` | 地图主界面（`map-home-01`） | 有 | `map-home-01.png/.xml/.meta.json` | 默认位置（全国视野），左列 8 项、右列 10 项、顶栏 搜索/图层、底部角注条 |
| 04 | `ST-A-map-fixed` | 地图固定态（`map-beijing-z12`） | 地图章节 | — | 固定态由 LC地图 采（`setView(39.9042,116.4074,12)`） |
| 05 | `R2-004` | POI 详情面板（`map-poi-sheet-01`） | 缺失 | — | LC 无 POI 详情面板；证据：`map-home-01.xml` 全节点表无详情容器，地图点选只回 `MapMarker`（3D 点选）不弹面板 |
| 06 | `ST-A-search-empty` | 搜索页空结果（`search-result-01`） | 地图章节 | — | 顶栏「搜索」[1040,192]-[1215,306]（`map-home-01.xml`），搜索面在 `map.html` 内，归地图章节 |
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
| 18 | `ST-C-measure` | 测量模式（`tool-measure-01`） | 地图章节 | — | 入口在 `more-sheet-03-fresh.xml`「测量」[359,1372]-[613,1534] |
| 19 | `ST-C-brush` | 画笔模式（`tool-brush`） | 地图章节 | — | 入口「画笔」[613,1372]-[867,1534] |
| 20 | `ST-C-fishing` | 钓点（`tool-fishing`） | 地图章节 | — | 入口「钓点」[867,1372]-[1121,1534] |
| 21 | `ST-C-track` | 轨迹记录 → 登录墙（`tool-track`） | 地图章节 | — | 入口「轨迹记录」[106,1540]-[359,1702]；LC 无登录墙（见 38） |
| 22 | `ST-A-map-home` | 路网（图层面板，`map-home-01.xml`） | 地图章节 | — | 左列「路网」[6,1999]-[201,2127]（`map-home-01.xml`） |
| 23 | `ST-C-contour` | 等高线首用引导（`tool-contour-01`） | 地图章节 | — | 左列「等高线」[6,2194]-[201,2322] |
| 24 | `ST-C-mountains` | 山脉（`map-mountains-entry`） | 地图章节 | — | 右列「山脉」[1027,1008]-[1222,1136] |
| 25 | `ST-C-water` | 水系（`map-water-entry`） | 地图章节 | — | 右列「水系」[1027,1203]-[1222,1331] |
| 26 | `ST-C-overlay` | 数据叠加面板（`map-overlay-sheet`） | 地图章节 | — | 右列「叠加」[1027,814]-[1222,942] |
| 27 | `ST-C-explore` | 探索页（`map-explore-page`） | 地图章节 | — | 右列「探索」[1027,1398]-[1222,1526] |
| 28 | `ST-C-layer` | 图层（`right-layer-sheet`） | 地图章节 | — | 右列「图层」[1027,619]-[1222,747] / 顶栏「图层」[865,192]-[1040,306] |
| 29 | `ST-C-3d` | 3D 模式（`map-3d-mode`） | 地图章节 | — | 右列「3D」[1027,2366]-[1222,2494] |
| 30 | `R2-037` | 相机权限弹窗（`right-layer-sheet.png`） | 地图章节 | — | 相机相关入口在相机/AR 路径，归地图章节 |
| 31 | `ST-D-more-default` | 地图工具面板（默认态，`more-sheet-03-fresh`） | 有 | `more-sheet-03-fresh.png/.xml/.meta.json` | 标题「地图工具」；16 项：罗盘/超清地图/看山脉/看水系/海拔查询/天气/历史影像/等高线/收藏点/测量/画笔/钓点/轨迹记录/路网/地理影像/超清优化，另有「设置」「功能开关（调试）」 |
| 32 | `ST-D-more-default` | 地图工具面板（滚动后，`more-sheet-02-scrolled`） | 有 | **与 31 共用** `more-sheet-03-fresh.png/.xml/.meta.json` | **实测不可滚动**：`input swipe 614 2300 614 1500 300` 后 dump 与 31 的 SHA256 完全相同（`EC23C862…5DC7`），面板一屏装下 16 项；同态共用一份证据，不另存第二张同图 |
| 33 | `ST-F-weather` | 天气页（`tool-weather-01`） | 有 | `tool-weather-01.png/.xml/.meta.json` | LC 有真实数据：城市 北京、14°、晴、西风 2 级、湿度 16 %、空气质量 19、7 日预报、数据源「中国天气网 · 更新 13:45」；原版为 VIP 门禁（U1） |
| 34 | `R2-018` | 抽屉菜单（`drawer-menu-03`） | 缺失 | — | LC 无汉堡抽屉/侧栏；证据：`map-home-01.xml` 顶栏只有 搜索[1040,192]-[1215,306]，无左上汉堡点击区（原版 [35,140]-[160,265] 处为地图控件） |
| 35 | `ST-E-map-setting` | 地图设置（`setting-map-01`） | 有 | `setting-map-01.png/.xml/.meta.json` | 地图滤镜/模式四 chip 常规[39,584]-[284,681]、自然[340,584]、高亮[641,584]、自定义[942,584]、地图效果预览、显示路网、允许地图旋转、回到上次浏览位置、屏幕常亮、3D 操作按钮、中心十字架、坐标系 WGS84 |
| 36 | `R2-020` | 工具管理（`tool-manage-01`） | 待补采 | — | **入口存在，证据未采**：`MainActivity.java:901-903`（注释「F-01：默认开 → 工具管理页」）`startActivity(new Intent(this, ToolsManagerActivity.class))`；本次采集未进入该页，设备窗口补齐截图+dump |
| 37 | `ST-A-favorite-mode` | 收藏列表入口（`favorite-list-02`） | 有 | **与 17 共用** `tool-favorite-01.png/.xml/.meta.json` | LC 收藏点页只有空态「暂无收藏点」，**无独立收藏列表入口**（dump 仅 3 节点：返回/标题/空态文案）；原版行 37 的「收藏列表点击区 [1043,506]-[1201,664]」在 LC 不存在，同态共用一份证据 |
| 38 | `R2-022` | 收藏保存 → 登录墙（`favorite-addpoint-01`） | 待补采 | — | **入口存在，证据未采**：收藏保存在钓点页 `FishingActivity.java:417-425`（保存对话框「保存」→ `SaveInterestActivity`，带 `EXTRA_SITTING/FACING`），不在收藏点页、也不走「添加点」；LC 无登录墙。待补采 `SaveInterestActivity` 截图+dump |

统计（按 §1 表逐行数）：**有 15 + 等价 1 + 地图章节 16 + 缺失 4 + 待补采 2 = 38**。缺失项均给证据位置；
待补采项（36/38）给入口坐标与代码锚点，等设备窗口补图，复现不了的才退回「缺失」。

**同态共用**：行 32↔31（更多面板不可滚动）、行 37↔17（收藏点只有空态、无收藏列表入口）各共用一份证据，
重复文件已删除（`more-sheet-02-scrolled.*`、`favorite-list-02.*`），因此 `report` 的「不同 state 同图」硬检查
不会命中这两行，**不需要进 `duplicates-allow.json`**。客户端章节现有 **14 PNG + 13 XML + 14 meta**。

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
| 22 | `ST-C-roadnet` | `map-roadnet-on` | 实采（LC 侧同态） | png+xml+meta | 路网叠加开启；原版该行无独立 stem（对照物为 `map-home-01.xml`） |
| 23 | `ST-C-contour` | `tool-contour-01` | 实采（异常） | png+xml+meta | 等高线开启；`logcat` 有 `LC3D contour on=true uniform=1`，但截图与 3D 态逐字节相同（见 29） |
| 24 | `ST-C-mountains` | `map-mountains-entry` | 实采 | png+xml+meta | 「看山脉」页：视野内山名 120 个、山脊/刷新视野 |
| 25 | `ST-C-water` | `map-water-entry` | 实采 | png+xml+meta | 「看水系」页 |
| 26 | `ST-C-overlay` | `map-overlay-sheet` | 实采 | png+xml+meta | 「数据叠加」页：4 Tab + 公路(路网) + 全部开启/关闭 |
| 27 | `ST-C-explore` | `map-explore-page` | 实采 | png+xml+meta | 探索页 |
| 28 | `ST-C-layer` | `map-layer-sheet` | 实采 | png+xml+meta | 右列「图层」→「底图源」12 源面板（3 可用 / 9 置灰） |
| 29 | `ST-C-3d` | `map-3d-mode` | 实采（异常） | png+xml+meta | 点 3D 后 `logcat` `LC3D texture ok 36/36`、`fps=60 zoom=12 z=11`，但截图仍是 2D 路网图，且与 23 逐字节相同 → 3D 画面被 2D 层遮挡/未切换，待 Stage 3 复核 |
| 30 | `R2-037` | 相机权限弹窗 | 缺失 | — | LC 无相机/AR 入口（`map-home-01.xml` 全节点无相机相关控件） |

统计：**实采 15（其中 1 个 png-only、2 个异常态）+ 缺失 1 = 16**，无「待采」。

**采集过程（可复现）**：`uiautomator dump` 在传感器活跃时报 `could not get idle state`，先 `am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 0` 冻结航向即稳定；装包后立即广播可能被忽略（应用未完成 onResume），需等 4 s。3D 按钮真实 bounds `[1027,2366]-[1222,2494]`、中心 `(1124,2430)`（点 `(1126,2187)` 落在 `−` 与 `定位` 之间的缝里）。

## 5. missing-lc 30 条 → R2 编号对齐（D3）

口径：`report` 按同名 stem 配对，下列 30 个 stem 只有原版图、没有我方同名图，计入 `missing-lc`。「依据」列区分两种来源：**清单直引**＝清单/索引/报告正文写到过该 stem；**同族别名**＝同一画面的另一次采集、文件名不同（命名不对齐类，不重采）。

| # | stem（orig） | 清单行 | R2 编号 | 依据 |
|---|---|---|---|---|
| 01 | `drawer-menu` | 34 | R2-018 | 同族别名：行 34 抽屉菜单（drawer-menu-03 为直引） |
| 02 | `drawer-menu-01` | 34 | R2-018 | 同族别名：行 34 抽屉菜单 |
| 03 | `drawer-menu-02` | 34 | R2-018 | 同族别名：行 34 抽屉菜单 |
| 04 | `drawer-menu-03` | 34 | R2-018 | 清单直引 |
| 05 | `drawer-menu-04` | 34 | R2-018 | 同族别名：行 34 抽屉菜单 |
| 06 | `drawer-menu-05` | 34 | R2-018 | 同族别名：行 34 抽屉菜单 |
| 07 | `favorite-addpoint-01` | 38 | R2-022 | 清单直引 |
| 08 | `favorite-list-01` | 37 | R2-021 | 同族别名：行 37 收藏列表（favorite-list-02 直引） |
| 09 | `favorite-list-02` | 37 | R2-021 | 清单直引 |
| 10 | `favorite-list-03` | 37 | R2-021 | 同族别名：行 37 收藏列表 |
| 11 | `favorite-list-04` | 37 | R2-021 | 同族别名：行 37 收藏列表 |
| 12 | `favorite-mode-01` | 17 | R2-014 | 同族别名：行 17 收藏点模式 |
| 13 | `favorite-mode-02` | 17 | R2-014 | 同族别名：行 17 收藏点模式 |
| 14 | `favorite-save-01` | 38 | R2-022 | 同族别名：行 38 收藏保存（favorite-addpoint-01 直引） |
| 15 | `favorite-save-login-01` | 38 | R2-022 | 同族别名：行 38 收藏保存登录墙 |
| 16 | `map-jump-beijing` | 04 | R2-026 | 同族别名：行 04 固定态（lc 对应 map-beijing-z12） |
| 17 | `map-poi-sheet-01` | 05 | R2-004 | 清单直引 |
| 18 | `more-sheet-01` | 31 | R2-015 | 同族别名：行 31 更多面板（more-sheet-03-fresh 对应） |
| 19 | `more-sheet-02-scrolled` | 32 | R2-016 | 清单直引 |
| 20 | `more-sheet-04-edit-entry` | 31 | R2-015 | 同族别名：行 31 更多面板编辑入口 |
| 21 | `poi-select-01` | 05 | R2-004 | 同族别名：行 05 POI 详情（map-poi-sheet-01 直引） |
| 22 | `poi-select-02` | 05 | R2-004 | 同族别名：行 05 POI 详情 |
| 23 | `right-3d` | 29 | R2-028 | 同族别名：行 29 3D（lc 对应 map-3d-mode） |
| 24 | `right-explore` | 27 | R2-031 | 同族别名：行 27 探索（lc 对应 map-explore-page） |
| 25 | `right-layer-sheet` | 28 | R2-027 | 清单直引 |
| 26 | `search-result-crs-01` | 07 | R2-025 | 同族别名：行 07 四坐标系结果（lc 对应 search-result-02） |
| 27 | `search-typed-coords-01` | 06 | R2-025 | 同族别名：行 06 搜索页（lc 对应 search-result-01） |
| 28 | `tool-compass-01` | 08 | R2-005 | 同族别名：行 08 罗盘浮层（lc 对应 tool-compass-page） |
| 29 | `tool-elevation-01` | 10 | R2-007 | 同族别名：行 10 海拔查询（lc 对应 compass-main-01） |
| 30 | `tool-manage-01` | 36 | R2-020 | 清单直引 |

合计 **30** 条：清单直引 7 条、同族别名 23 条、未取证 0 条
