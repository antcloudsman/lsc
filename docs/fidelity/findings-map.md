# M3 地图侧对标审查（Stage 3 差异清单）

审查员：LC审地图　｜　基线：`develop` `3d7fcb309950f9c08e5a89273bf8652bd77c48e2`
审查对象：`docs/fidelity/lc-captures.md` §4 地图章节 16 行（清单行 04/06/07/18–30）
我方证据：`docs/fidelity/lc/`（采集构建 `464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a`，APK sha256 `ccd5686f4c9b908d9c5c8a61fe7166d3f2bf01fcb165405e0a58e520ce7cbb7b`）
原版证据：`docs/fidelity/orig/`（原版 `space.xinzhi.globe` 1.6.1，1228×2700）

## 0. 证据链与同源性

- **代码同源核验**：`git diff --stat 464ebb8 3d7fcb3 -- app/android/app/src/main/assets/map.html app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java app/android/app/src/main/java/com/luopan/fengshui/MapAsset.java` 输出为空（退出码 0），即地图页 HTML 与地图宿主 Activity 在两提交间逐字节相同；证据包（`464ebb8`）可直接用于判 `3d7fcb3` 的代码。
- **新增实机复现（本轮，唯一一次）**：同 APK（sha256 与证据包一致）在 L2E0222531014833 上冷启 → 冻结 heading 0° → 点右列 3D（真实中心 `(1124,2430)`）→ logcat 出 `LC3D webgl ok` / `LC3D mode=3d zoom=9 pitch=45` / `LC3D texture ok 36/36 img z9` / `LC3D fps=56`，截图由 2D 地形图变为卫星贴面 3D 地形（截图 sha256 `bf88d737bcf76cf542ba2c90b81f68521d4fe0ccff756e4ec162efd3161936c4`，2D 前态 `4aba085b98739d4c8b4ae5c35b4eb96537af1c2fe49d5abc531e746e58469e33`）。见 §2.C-04。
- 复现中断：随后设 z12 时设备 USB 掉线（`adb devices` 空），z12 态未复现；本报告不把「3D 在 z12 不可用」写成结论。

## 1. 结论摘要

| 类别 | 条数 | 编号 |
|---|---|---|
| 已证实差异（P0） | 1 | C-01 |
| 已证实差异（P1） | 5 | C-02、C-03、C-05、C-06、C-07 |
| 已证实差异（P2/DI） | 4 | C-08、C-09、C-10、C-11 |
| 证据不可用，未确认 | 5 行 | 行 18/19/20/22/23/25/27（C-04、C-12） |
| 缺失 | 1 行 | 行 30（C-13 相机权限） |
| 正面结论（M3 已知项） | 2 | 罗盘浮层已对齐；「双 WebView」不成立 |

**不给 P0 的一条**：`map-3d-mode` 的「3D 被 2D 覆盖」不成立为产品缺陷——同一 APK 上 3D 渲染链路实测可用；出问题的是证据本身（两态截图/两态 dump 逐字节相同），定级为 P1 证据缺陷，见 C-04。

## 2. 差异条目（五要素）

格式：`原版截图 | 我方截图 | 类别 | 严重度 | 代码锚点 | 修法`

| 编号 | 原版截图 | 我方截图 | 类别 | 严重度 | 代码锚点 | 修法 |
|---|---|---|---|---|---|---|
| C-01 | `docs/fidelity/orig/search-result-01.png`；`orig/search-result-02.png` | `docs/fidelity/lc/search-result-01.png`；`lc/search-result-02.png` | 功能/业务逻辑：地图搜索被替换为知识检索 | **P0** | `MainActivity.java:504`（`topSearchButton` → `showSearchDialog()`）、`MainActivity.java:1483-1509`（数据=盘面/圈层/知识条目，hint「搜索：盘面 / 圈层 / 知识条目」） | 地图态顶栏「搜索」恢复原版语义：境内/境外/行政区域/收藏点 4 个 tab + 搜索历史 + 坐标输入（原版 `orig/search-result-01.xml`：输入框 `[170,252][1039,375]`、tab 行 `y=420..486`）；知识检索挪回知识弹层入口 |
| C-02 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 布局/效果：地图态左上角信息块内容不符 | **P1** | `MainActivity.java:415-430`（`readingView` 常驻，无 `GONE` 分支）、dump 实测 `[13,87][722,293]` | 地图态隐藏读盘块，左上改示当前地名（原版同名位置为地名 `[327,194][468,273]`「青海湖」/`[327,194][701,273]`「哀牢山森林旅游区」）；读盘 9 行只在罗盘态显示 |
| C-03 | `orig/map-layer-sheet.png` | `lc/map-layer-sheet.png` | 布局/结构：底图源面板呈现方式不同 | **P1** | `map.html:47-55`（`#srcPanel` 全屏遮罩 + 竖列表 CSS）、`map.html:88-92`（DOM）、`map.html:1472-1483`（12 源 + `需授权/需 key` 文案）、`map.html:1498-1513`（`openSources/closeSources`） | 改回原版 3×4 缩略图网格（12 格 + 末尾「+」），卡片底部条为源名；保留 D-006 的置灰与「需授权」说明，但不占满屏、不用文字列表 |
| C-04 | `orig/map-3d-mode.png` | `lc/map-3d-mode.png`（sha256 `f037f735…`，与 `lc/tool-contour-01.png` 相同） | 证据缺陷（采集态未生效），非渲染缺陷 | **P1（证据级）** | `map.html:1380-1419`（`toggle3D/setMode` 层显隐）、`map.html:1635-1639`（等高线自动进 3D）、`tools/map/collect_map_lc.ps1:12,75`（3D 默认点击 `155,576`，与真实按钮 `[1027,2366][1222,2494]` 不符） | 用真实中心 `(1124,2430)` 点 3D；截图前以按钮文案「返回 2D」+ logcat `LC3D mode=3d` 双确认；先退出 3D（显示「3D 地形」）再拍下一态。修脚本默认坐标 |
| C-05 | `orig/map-overlay-sheet.png` | `lc/map-overlay-sheet.png` | 功能/结构：数据叠加面板条目与文案 | **P1** | `map.html:748`（`setOverlayLayer`）、`map.html:1203`（`setRoadNet`）；LC dump `[41,1417]..[41,2092]` | 原版有「行政区域（面/线/筛选）」三级条目 + 底部「全部关闭/全部开启」；LC 把面/线做成「无数据」说明并缺「行政区域筛选」入口。按原版补筛选入口，无数据行改为禁用态而非说明段 |
| C-06 | `orig/map-mountains-entry.png` | `lc/map-mountains-entry.png` | 结构：山脉入口行为不同 | **P1** | `MainActivity.java:965-968`（`山脉` → `MountainActivity` 整页） | 原版点「山脉」后截图仍是地图页（无子页、无可见山名图层）；LC 直接跳整屏「看山脉」。先按原版做地图内山峰/山名图层或半屏面板，再评估是否保留整页 |
| C-07 | `orig/map-explore-page.png` | `lc/map-explore-page.png`（图为「看山脉」页，见 §4） | 结构：探索入口 | **P1（未确认，证据错态）** | `MainActivity.java:916-922`（`探索` → `DiscoveryActivity`） | 原版是 3 个分类（专题地图/环球地理/历史战争）+ 卡片列表；LC 的 `map-explore-page.*` 拍成了「看山脉」页，须重采后逐项比对 |
| C-08 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 文案：底部来源条 | **P2（DI 授权部分不计缺陷）** | `map.html:87`（`srcInfo` 文本） | 原版「数据来自世纪空间 审图号：GS(2026)1219号」；LC「数据来自天地图地形（等价替代原版世纪空间）· 审图号：未确认」。图源替换属 D-006，但「审图号：未确认」须换成天地图官方审图号或按源分源显示 |
| C-09 | `orig/map-beijing-z12.png` | `lc/map-beijing-z12.png` | 布局/按钮：左右列按钮体量与样式 | **P1** | `MainActivity.java:471-480`（右列 10 项与锚点 y）、`MainActivity.java:561-569`（`newToolButton`，`argb(120,24,20,16)` 底、9sp） | 实测：LC 右列按钮框 `195×128 px`（`[1027,619][1222,747]`）；原版同名标签文字仅 `71×39 px`（`[1081,632][1152,671]`），图标在文字上方（图标 bounds 不在 dump 中，故只比文字与按钮框）。LC 左列 `[6,432][201,560]` 同样 195×128。按原版改为图标+小字、收窄按钮、去掉大面积半透明底 |
| C-10 | `orig/tool-track.png` | `lc/tool-track.png` | 业务逻辑：轨迹记录登录墙 | **P2（DI：D-006 去登录墙）** | `MainActivity.java` 轨迹入口（`更多 → 轨迹记录`）；LC 页 `TrackReplayActivity.java:159` | 原版是手机号+验证码登录墙；LC 直接是「历史轨迹」页（空态「暂无历史轨迹记录」）。属有意差异，不计还原度；但空态文案与「轨迹设置/开始记录」需与原版功能页对齐 |
| C-11 | `orig/tool-brush.png`、`orig/tool-fishing.png` | `lc/tool-brush.png`、`lc/tool-fishing.png` | 结构：画笔/钓点入口 | **P2（未确认）** | `map.html:538`（`paintStart`）、`map.html:639`（`setFishLayer`） | 原版两态 dump 实际是罗盘浮层页（`坐240.0° 向60.0°`、左列 6 项），不是独立画笔/钓点页；LC 是专属页（画笔底栏 颜色/粗细/撤销/清除/导出 `[0,2369]..[1228,2504]`，钓点「公开水域 80 处」）。原版真实入口形态未确认，须按原版复现路径重采 |
| C-12 | `orig/tool-contour-01.png` | `lc/tool-contour-01.png` | 效果：等高线开启后无视觉变化 | **未确认** | `map.html:1635-1639`（`toggleContour` 只有 3D 才生效）、`map.html:1936-1953`（片元着色器 100/500 m 等高线） | 现存截图与 `map-3d-mode` 逐字节相同，说明拍摄时不在 3D 可见态；须在 3D 实测可见的前提下重拍（先点 3D 确认画面变化，再点等高线） |
| C-13 | 无（原版有相机权限弹窗，`right-layer-sheet.png`） | 无 | 缺失：相机/AR 入口 | **P2（缺失）** | `MainActivity.java:471` 右列 10 项无相机；`map-home-01.xml` 全节点无相机控件 | 按清单登记为缺失；若要 1:1，需补相机权限弹窗与入口，或书面登记「不做」决策 |

行 18/19/20/22/27/25 的判定依据（证据不可用）：
- 行 18 `tool-measure-01`：LC 侧 `dump:null`（`--skip-dump`，动画致 idle 超时），无法做文字/bounds 量化；缺 dump，未确认。
- 行 19 `tool-brush`、行 20 `tool-fishing`：原版 dump 是罗盘浮层页（见 C-11），无同态可比；未确认。
- 行 22 `map-roadnet-on`：`lc-captures.md` 自述原版无独立 stem，对照物只能是 `map-home-01.xml`，无原版路网开态；未确认。
- 行 25 `map-water-entry`：原版是 VIP 付费墙（`年卡VIP ¥98`/`终身VIP ¥168`/`月卡VIP ¥68`），属 D-006/T-15 有意差异；但 LC 的 `map-water-entry.*` 拍成了「看山脉」页（§4），须重采。
- 行 27 `map-explore-page`：同上，LC 证据错态。

## 3. 重点两条的定级与代码锚点

### 3.1 `map-3d-mode`：不是「3D 被 2D 覆盖」，是证据态未生效

- 代码上 3D 层的显隐是成对切换的：`map.html:1397-1399` `baseLayer.style.display="none"; annoLayer.style.display="none"; glCanvas.style.display="block"`，2D 恢复在 `1413-1415`；`render()` 在 3D 下直接 return（`map.html:237-238`），瓦片只写进 `baseLayer`（`map.html:247-248`）。按代码路径，若真进了 3D，2D 瓦片不可能还画在屏上。
- 本轮实机复现（同 APK、同设备、1228×2700）：点 `(1124,2430)` 后 logcat 顺序为
  `LC3D webgl ok WebGL 1.0` → `LC3D mode=3d zoom=9 pitch=45` → `LC3D patch 8/209/96` → `LC3D texture ok 36/36 img z9` → `LC3D fps=56`，截图确实是 3D 卫星贴面地形（有山体起伏与红针），与 2D 前态 sha256 不同。结论：**渲染链路可用，不存在「画了但被 2D 覆盖」的代码路径**。
- 现存证据两文件字节相同（png `f037f735…`、xml `3d14b3c2…`），只可能是拍摄时并未呈现 3D 态。最可疑的两处：`tools/map/collect_map_lc.ps1:12` 的 3D 默认坐标 `(155,576)`（该点落在左列「全地图工具」区域，真实 3D 中心是 `(1124,2430)`）；以及等高线态会自动进 3D（`map.html:1638`），若在同一会话里再点一次「3D」，`toggle3D` 会把 3D 关回 2D（`map.html:1380-1382`），正好得到「与等高线态同图」。
- 缺的证据：当时的操作时序（是否重拉 App、点了哪些坐标）与完整 logcat；因此具体触发原因为「未确认」，但「产品 3D 不可用」可排除。

### 3.2 等高线：未确认，不能定级

- 等高线不是独立图层，是 3D 片元着色器里的等值线（`map.html:1936-1953`，100 m 与 500 m 两级，`fwidth` 抗锯齿），且开关会强制进 3D（`map.html:1635-1639`）。
- 现有证据里 `LC3D contour on=true uniform=1` 只能证明 uniform 已置 1，不能证明屏幕上是 3D；由于该截图与 3D 态逐字节相同（同一张 2D 图），本条只能记「未确认」，缺的是「3D 可见态下的等高线截图 + 同一视野的开/关双图」。

## 4. 证据质量缺陷（影响 Stage 3 验收，需重采）

| 证据 | 实测 | 应为 |
|---|---|---|
| `lc/map-3d-mode.png` / `.xml` | 与 `lc/tool-contour-01.png` / `.xml` 逐字节相同（png `f037f73577c5b56d68d94364172ec6fc284dbbbe70464e3290a2f461bcb6fcb6`；xml `3d14b3c2539a165d111a4bd1351a134f099f68a4a5bc1865de411467fdffcbb2`） | 3D 地形画面 |
| `lc/map-water-entry.png` / `.xml` | 标题「看山脉」`[533,173][695,246]`、副题「视野内山名 120 个」、满屏山名标签 | 「看水系」页 |
| `lc/map-explore-page.png` / `.xml` | 同上，也是「看山脉」页 | 探索页 |
| `lc/tool-measure-01.meta.json` | `dump:null`（动画致 `uiautomator` idle 超时） | 至少一份可量化的 dump |
| `lc/map-3d-mode.meta.json` 等 | `build.commit=464ebb8`，与 Stage 3 基线 `3d7fcb3` 不同 | 已核代码同源（§0），可接受；后续重采请直接标 `3d7fcb3` |

## 5. M3 已知项

1. **「双 WebView 互相污染」不成立**：原版 `orig/map-beijing-z12.xml` 只有 1 个 `WebView` + 1 个 `View Map`；LC `MainActivity.java:317` 注释与 `395` 只有一个 `compassMapView`。当前是「单 WebView + 原生 `CompassView` 叠层」（`MainActivity.java:400-409`），不存在两个 `__LC3D` 实例。
2. **罗盘浮层位置已对齐用户口径**：点「罗盘」不换页（`MainActivity.java:899` `setCompassMode(true)`），`CompassView` 以 `Gravity.CENTER`、直径 `0.7378×1228+16` 叠在地图 WebView 上（`MainActivity.java:405-409`），默认 `GONE`（`408`），退出走 `setCompassMode(false)`（`1045-1052`）。即「浮动叠加在地图中心、地图仍是底」已实现。
3. **底部三 Tab 已不存在**（K-01 已收口）：当前截图底部只有「磁北 / 偏角+0.0°」状态条，无「罗盘/地图/更多」三 Tab；用户「整个底部菜单栏没有存在意义」的诉求在结构上已满足，残留问题转为 C-02（地图态读盘块）与 C-09（按钮体量）。

## 6. 未覆盖范围

- 未重采任何一态（设备在复现中途掉线，`adb devices` 为空）；行 18/19/20/22/23/25/27 的量化留给重采后补。
- 未审 3D 投影/`pick` 射线求交的边界数学、`VEXAG` 与 `pick` 是否同一变换空间、DEM 瓦片失败降级——这些属 M3 代码复审项，不在 Stage 3「对标差异」范围，本轮未复核，勿引用为本轮结论。
- `map.html` 内部 3D 细节（贴地、山脊、历史影像、measure/brush/fish/track 的具体交互）只核到入口与证据态，未逐帧比对。
