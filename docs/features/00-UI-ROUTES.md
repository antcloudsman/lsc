# 18 项缺失功能 · UI 路由与页面骨架约定（v2 · 无门槛版）

> 配套总纲：`docs/features/00-PLAN.md`。本版按总纲 §4.0「无门槛原则」（提交 `395006e`，用户明示）修订：
> **18 项全部免费、无门槛、默认开启**；功能开关只作调试/回退，不做可用性门槛。
> 本文只定落点、入口、id、骨架、开关；界面文案/尺寸以各 `F-xx-*.md` 单功能文档为准。
> 实现纪律：每项独立提交、默认免费开放、导出仅 debug（D-011）、不与 K-01/缺口线交叉提交。

## 0. 不可违反的免费口径（验收会直接判缺陷）

- 不出现「会员 / VIP / 专享 / 去开通 / 升级 / 试用 / 试看 / 解锁 / 付费 / 订阅」等字样与图标。
- 不做功能锁、权限门、解锁动画、次数/时长限制、模糊预览、付费墙。
- 原版靠 VIP 进入的页面（山脉/水系/超清/历史影像/钓点/叠加等）在 LC 里**直接打开**。
- 数据不可得时：页面照开，内容位给明确说明（图层类统一文案「该图层暂无免费数据源」），**不得**改用「请升级/开通」替代。
- 只不搬第三方会员数据集；功能入口与可做的部分必须保留（总纲 §4.0/§4.1）。

## 1. 本分支的代码事实（路由设计的前提）

- `MainActivity` 已是 K-01 单页结构，**没有底部导航、没有抽屉**（本分支 `Drawer` 代码为 0）。
- 已有入口（`MainActivity.java` 当前实际代码）：
  - 左列 8：`全地图工具 / 截屏 / 超清优化 / 海拔查询 / 罗盘 / 路网 / 等高线 / 更多`
  - 右列 10：`图层 / 叠加 / 山脉 / 水系 / 探索 / 指北针 / + / − / 定位 / 3D`
  - 罗盘态左列 6：`截屏 / 等高线 / 罗盘复位 / 罗盘锁定 / 读盘 / 贴地`
  - 罗盘态右列 4：`地图 / 实景 / 坐南朝北 / 指针开关`
  - 罗盘工具条 5：`切换 / 旋转 / 缩放 / 透明度 / 天支线`
  - 「更多」bottom sheet 16：`罗盘 / 超清地图 / 看山脉 / 看水系 / 海拔查询 / 天气 / 历史影像 / 等高线 / 收藏点 / 测量 / 画笔 / 钓点 / 轨迹记录 / 路网 / 地理影像 / 超清优化`
  - `CompassListActivity` 四组：自定义 / 常用 / 简易 / 综合（常用 5 项可用；自定义、简易、综合为占位）。
- 本分支 `AndroidManifest.xml` 只有 `MainActivity`、`CompassListActivity`。
- gap 线的 `SettingActivity / ToolsManagerActivity / InterestListActivity / SaveInterestActivity / LcUi` **尚未合入本分支**；涉及复用时先等 develop 合流，不在本分支复制第二份（见 §6）。

## 2. 总则

1. **入口位置 1:1**：原版在左/右列、罗盘工具条、「更多」格里的按钮，位置与文案不动。
2. **默认开放**：18 项没有「开关才能用」的说法；点击入口即进入 §3 落点。功能开关只在关闭时回退到旧 stub（调试用），不提示任何门槛。
3. **不新增底部导航**；子页优先复用现有入口，不把功能塞进抽屉（抽屉本分支尚未实现）。
4. **能覆盖就不开页**：地图交互类（测量/画笔/透明度等）在 `MainActivity` 内以「模式 + 浮层/底部面板」实现；有独立原版 Activity 的才建 Activity。
5. **数据不可得也不锁**：页面 + 明确占位（「该图层暂无免费数据源」），禁止假数据、禁止收费门。
6. 所有新增页必须有稳定 `lc_<feature>_*` id；`am start` 直达仅在 debug 变体（D-011）。

## 3. 路由表（F-01 … F-18）

列说明：「LC 落点」= 实现承载；「形式」= Activity / 覆盖层 / 仅开关；「开关 key」见 §5。

| # | 原版入口 | 原版承载 | LC 落点（免费开放） | 形式 | 新 Activity | 开关 key | 依据/状态 |
|---|---|---|---|---|---|---|---|
| F-01 | 左列 1「全地图工具」 | `ToolsManagerActivity` 派发 → `OpenVipActivity`（原版付费分发页） | **已定：工具管理页**（16 宫格「全部功能」+ 编辑/勾选/排序），无门槛；不实现付费分发页 | 页 | 复用 `ToolsManagerActivity`（gap 线） | `f01` | 导航表 §3.1；总监定案 2026-11-05 |
| F-02 | 左列 3、更多「超清优化/超清地图」 | 会员云端超清任务页 | `MainActivity` 底部面板：说明本地降级最大 z18（天地图 img + 本地放大），可直接执行；不写「会员/升级」 | 覆盖层 | — | `f02` | 总纲 §4.1（降级） |
| F-03 | 左列 4、更多「海拔查询」 | 海拔/地形查询子页（待探针） | 先按「地图点选 → 结果底部面板」设计；若单功能文档证实是独立页再改 Activity | 覆盖层（待定） | 待定 | `f03` | 待 `F-03` 文档 |
| F-04 | 左列 6「路网」 | 底图图层切换 | `MainActivity`：调 `toggleRoad()` + MapManager `showRoad` 持久化；**当前 map.html 无 road 接口，需 LC地图 新增** | 仅开关 | — | `f04` | 导航表 §3.1（应用内） |
| F-05 | 右列 2「叠加」 | 叠加层选择页（原版会员） | 底部面板：列出可用的免费叠加层；无可用项时给占位「该图层暂无免费数据源」，页面仍开 | 覆盖层 | — | `f05` | 原版入口 + 总纲 §4.1 |
| F-06 | 右列 3、更多「看山脉」 | `discovery.MountainActivity` | 独立页直接开放：天地图分类取点 / 现有 DEM 派生能做的部分；不可得的部分显式占位 | Activity | `MountainActivity` | `f06` | 探针已直达；总纲 §4.1 |
| F-07 | 右列 4、更多「看水系」 | `discovery.WaterActivity` | 独立页直接开放，同上 | Activity | `WaterActivity` | `f07` | 总纲 §4.1 |
| F-08 | 右列 5「探索」 | `discovery.DiscoveryActivity`（专题卡） | 独立页直接开放：免费可得的专题卡；无数据的卡位显式占位 | Activity | `DiscoveryActivity` | `f08` | 导航表 §3.2 |
| F-09 | 更多「天气」 | `WeatherActivity` | 独立页；接公开天气 API（待核），核不到则占位 | Activity | `WeatherActivity` | `f09` | 待 `F-09` 文档 |
| F-10 | 更多「历史影像」 | `HistoryImageActivity`（Google Earth 历年） | 独立页直接开放：天地图单期影像对比 / 自建快照能做则做；不可得处写「该图层暂无免费数据源」 | Activity | `HistoryImageActivity` | `f10` | 总纲 §4.1（近似/不支持，入口保留） |
| F-11 | 更多「收藏点」 | `InterestListActivity` / `SaveInterestActivity` | 复用 gap 线已实现的本地闭环（列表→跳图→删除；保存页） | Activity ×2 | `InterestListActivity` / `SaveInterestActivity`（已有） | `f11` | 缺口线 `feature/gap-client` |
| F-12 | 更多「测量」 | `MeasureProveActivity` + 地图测量 | `MainActivity` 测量模式 + 底部结果面板；若文档证实 MeasureProve 是独立换算页再补 Activity | 覆盖层 | 可选 `MeasureProveActivity` | `f12` | 待 `F-12` 文档 |
| F-13 | 更多「画笔」 | 地图标注/绘制 | `MainActivity` 绘制模式 + 底部工具条 | 覆盖层 | — | `f13` | 待 `F-13` 文档 |
| F-14 | 更多「钓点」 | 钓点图层（原版会员） | 独立页直接开放：天地图 POI 等免费源能取则取；不可得处显式占位，不放假点 | Activity | `FishPointActivity`（实现名） | `f14` | 总纲 §4.1 |
| F-15 | 更多「轨迹记录」 | `TrackListActivity` / `TrackSettingActivity` / `TrackReplayVideoActivity` | 三个独立页依次链路：列表 → 设置 → 回放 | Activity ×3 | `TrackListActivity` / `TrackSettingActivity` / `TrackReplayVideoActivity` | `f15` | 00-PLAN §1 |
| F-16 | 更多「地理影像」 | `GeographicImageryActivity` | 独立页直接开放 | Activity | `GeographicImageryActivity` | `f16` | 待 `F-16` 文档 |
| F-17 | 罗盘工具条「透明度」「天支线」 | 浮层透明度 / 天支线圈层 | `MainActivity` 罗盘态：透明度滑杆浮层 + 天支线开关（`CompassView` 自绘圈层） | 覆盖层 | — | `f17` | 00-PLAN §1（未实现） |
| F-18 | 盘面列表「自定义/简易/综合」 | `CompassListActivity` 目录 + 服务端目录 | 扩展现有 `CompassListActivity` 四组；本地内置目录（总纲 §4.1），自定义上传按文档 | 现有页扩展 | —（`CompassListActivity`） | `f18` | 数据依赖：服务端目录 → 本地内置 |

**必须独立 Activity**：F-06、F-07、F-08、F-09、F-10、F-11、F-14、F-15（3 个）、F-16。
**可留在 MainActivity 内做覆盖层**：F-02、F-03（待定）、F-04、F-05、F-12、F-13、F-17。
**现有页扩展**：F-18；F-01 复用工具管理页。

**地图接口缺口**：F-04 路网需要 `map.html` 的路网叠加开关（当前 0 命中）；F-06/F-07/F-10/F-16 的底图/图层能力也要按各自文档与 LC地图 对齐，客户端只调接口、不改 `map.html`。

## 4. id 规范

统一格式：`lc_<feature>_<element>[_<index>]`，index 从 0 起、按视觉顺序递增；同一窗口内不得重复使用同一 id。

### 4.1 feature 词表（与 F 编号一一对应）

| F# | feature 词 | 典型 id |
|---|---|---|
| F-02 | `supermap` | `lc_supermap_panel` / `lc_supermap_close` |
| F-03 | `altitude` | `lc_altitude_panel` / `lc_altitude_value` / `lc_altitude_close` |
| F-04 | `road` | `lc_road_toggle`（如需按钮态） |
| F-05 | `overlay` | `lc_overlay_panel` / `lc_overlay_item_0..N` / `lc_overlay_close` |
| F-06 | `mountain` | `lc_mountain_back` / `lc_mountain_title` / `lc_mountain_content` / `lc_mountain_empty` |
| F-07 | `water` | `lc_water_*`（同上） |
| F-08 | `discovery` | `lc_discovery_*` / `lc_discovery_card_0..N` |
| F-09 | `weather` | `lc_weather_*` |
| F-10 | `history` | `lc_history_*` / `lc_history_slider` |
| F-11 | `interest` | 已实现：`lc_interest_*` / `lc_save_*`（不变） |
| F-12 | `measure` | `lc_measure_panel` / `lc_measure_result` / `lc_measure_clear` / `lc_measure_close` |
| F-13 | `draw` | `lc_draw_panel` / `lc_draw_color_0..N` / `lc_draw_undo` / `lc_draw_clear` / `lc_draw_close` |
| F-14 | `fish` | `lc_fish_*` |
| F-15 | `track` | `lc_track_back` / `lc_track_title` / `lc_track_list` / `lc_track_item_0..N` / `lc_track_setting_*` / `lc_track_replay_*` |
| F-16 | `geo` | `lc_geo_*` |
| F-17 | `compass` | `lc_compass_alpha_panel` / `lc_compass_alpha_slider` / `lc_compass_alpha_close` / `lc_compass_tianzhi_toggle` |
| F-18 | `dial` | 已实现：`lc_dial_*`（补齐自定义/简易/综合组时沿用） |
| 开关页 | `featureflag` | `lc_featureflag_back` / `_title` / `_list` / `lc_feature_row_f01..f18` / `lc_feature_switch_f01..f18` |

### 4.2 页面骨架固定 id

每个独立页至少具备：

| 元素 | id 后缀 | 说明 |
|---|---|---|
| 返回 | `_back` | 52dp 标题栏左侧 `‹`，点击 finish |
| 标题 | `_title` | 居中，文案以单功能文档为准 |
| 内容容器 | `_content`（或列表页 `_list`） | 可滚动 |
| 空态/无数据 | `_empty` | 无数据或「该图层暂无免费数据源」的明确说明 |
| 底部面板（可选） | `_sheet` + `_sheet_close` | 从底部升起，带 0.3 遮罩 |

### 4.3 覆盖层（MainActivity 内）的 id

覆盖层控件同样按 `lc_<feature>_*` 挂在 `MainActivity` 的浮层 FrameLayout 上；关闭后从视图树移除，AT 用 `_panel` / `_close` 定位。

## 5. 可开关方案（仅调试/回退）

### 5.1 存储

- SharedPreferences 文件：`lc_features`（与 `lc_prefs`、`MapManager`、`ToolsManager` 分开）。
- 键：`enabled_f01` … `enabled_f18`，布尔，**默认 true**（18 项全开）。
- 封装：`FeatureFlags`（`isEnabled(String f)`, `setEnabled(String f, boolean)`, `enabledCount()`）；f 取值 `f01`…`f18`。

### 5.2 开关页

- `FeatureFlagsActivity`：标题「功能开关（调试）」；列表按 F-01…F-18 顺序，18 行，每行显示 `F-xx 名称` + 当前状态 + 开关；id：行 `lc_feature_row_f01..f18`、开关 `lc_feature_switch_f01..f18`、列表 `lc_featureflag_list`；改动即时生效。
- 入口：**「更多」面板标题栏右侧「功能开关（调试）」**（与「编辑」并列；「编辑」= 工具管理，gap 线）。
- **不动 `MapSettingActivity`**：那页是对齐 `MapSettingActivity` 的 1:1 页面（8 行/6 开关/2 下拉），不塞 LC 自用增量行；功能开关只挂在「更多」面板标题栏。

### 5.3 关闭态行为（仅调试）

- 关闭某项 = 该项回退到**旧 stub 行为**（toast「原版为工具子页，本步未实现」），用于定位回归问题。
- 关闭态**不得**出现「未开通/请升级/会员」类提示；不得改变其他功能。
- 默认发布态 = 全开；关闭只能是本地调试，不作为发布基线。

## 6. 工具子页统一骨架

### 6.1 复用与依赖

- 骨架复用 gap 线的 `LcUi`（`toolbarLight` 52dp 白底居中标题、`blockGap` 11.6dp `#F0F3F7`、`dp()`）与浅色主题 `Theme.Luopan.Light`。
- 本分支尚未合入 gap 线：**实现前先等 `feature/gap-client` 合入 develop 并 rebase 本分支**；不在本分支复制第二份 `LcUi`，避免双份漂移。
- 本分支实现期间如需先行，只允许新增 `LcFeatureActivity` 基类并**引用** `LcUi`，不复制其实现。

### 6.2 页面骨架（独立 Activity）

```
[标题栏 52dp：#FFFFFF，‹ 返回（30sp 黑）+ 标题（16sp 加粗居中）]
[可选区块间隔 11.6dp：#F0F3F7]
[内容区：ScrollView（白底）+ 卡片（白底 12dp 内边距）]
[可选底部面板：圆角顶部 sheet + 0.3 遮罩]
```

- 类命名沿用原版页面名（`WeatherActivity` 等），包名 `com.luopan.fengshui`。
- 基类 `LcFeatureActivity`（新建，非复制 LcUi）：
  - `setPageTitle(CharSequence)`、`contentContainer()`、`showBottomSheet(View)` / `dismissBottomSheet()`；
  - 默认返回 = finish；状态栏白底黑图标（主题同 `Theme.Luopan.Light`）；
  - 页面子类只填内容，不重复搭标题栏。
  - **不含任何锁/权限/解锁组件**；不引入「功能未开通」状态。
- 需要地图的页（F-06/F-07/F-10/F-16 等）：只调用 LC地图 的桥/`map.html` 既有接口，不直接改 `assets/map.html`；地图能力缺口记入单功能文档。
- 页面内不直接持有 `MainActivity` 引用；需要回传坐标/坐向时用 Intent extras（与 F-11 的 `lc_lat/lc_lng/lc_sitting/lc_facing` 同一套命名）。

### 6.3 无数据时的统一表达

- 图层类：`该图层暂无免费数据源`
- 非图层类：`该功能暂无免费数据源` + 已实现部分照常展示
- 不使用「会员专享/去开通/升级」；不画锁图标、不做模糊/遮挡。

### 6.4 Manifest / 导出

- 主 manifest：所有新 Activity `android:exported="false"`。
- `src/debug/AndroidManifest.xml`：仅 debug 用 `tools:replace="android:exported"` 覆盖为 true，供 AT `am start` 直达（D-011）。
- 命名示例：`com.luopan.fengshui/.WeatherActivity`、`.../.FeatureFlagsActivity`。

## 7. 实现顺序与依赖

1. **先合流**：gap 线（设置/工具管理/收藏点 + `LcUi`）进 develop → 本分支 rebase；否则骨架与 F-11 无法复用。
2. 按单功能文档到达顺序实现；**每项独立提交**，提交说明引用 `docs/features/<F-xx>.md` 的具体章节。
3. 建议批次（每批 ≤3 项，先低风险）：
   - 批次 A（无数据依赖）：F-04、F-05（免费层/占位）、F-17、F-18 本地部分；
   - 批次 B（覆盖层）：F-02、F-12、F-13；
   - 批次 C（独立页骨架）：F-09、F-16、F-14；
   - 批次 D（数据替代）：F-06、F-07、F-10、F-08；
   - 批次 E：F-15、F-11 对齐、F-01 等价替代、F-03。
4. 每批完成后：`assembleDebug/assembleRelease` 绿、D-011 复查、AT 用 `am start` 跑通、全开态截图与 K-01 基线对比（入口位置不变）。

## 8. 待决/风险（不自行拍板）

1. F-01 已定：工具管理页（16 宫格 + 编辑/勾选/排序），原版付费分发页不实现。
2. F-02 的 z18 降级是能力上限说明，非原版云端超清；文案不得写成「已实现云端超清」。
3. F-03/F-12 的承载形式（覆盖层 vs 独立页）等单功能文档；当前表内为暂定。
4. F-15 三个页面是否都需要独立入口，等 `F-15` 文档确认链路（列表→设置→回放）。
5. 抽屉尚未实现；设置页/功能开关是否迁入抽屉，等 K-01 后续与总监裁决。
6. 本分支无 gap 线代码时，F-11 无法实现，只能先留路由。
7. 数据替代矩阵（总纲 §4.1）里「公开天气 API」「天地图分类搜索」需 LC地图/服务端核对可用性与配额。

---

状态：2026-11-05 建立；v2 按 `395006e` 的 §4.0 无门槛原则修订（默认开启、无付费 UI、无数据不锁）。
