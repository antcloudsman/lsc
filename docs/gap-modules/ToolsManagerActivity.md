# ToolsManagerActivity（工具管理 / 全部功能） · 逆向取证

- 类：`space.xinzhi.globe.ui.pages.ToolsManagerActivity`（父类 `ui.base.BaseActivity`）
- DEX：`classes06.dex`；规模：11 方法 + 16 个内部类
- 入口：**主页左下「更多」→「地图工具」面板右上「编辑」**
  - 调用链：`MainActivity.F4`（`MainOther`, `MainActivity.kt:1754`）创建 lambda `space.xinzhi.globe.ui.pages.K0` → `K0.invoke()` @0x0002 → `MainActivity.T1` → `MainActivity.H4` @0x0002 `const-class ToolsManagerActivity` + `startActivity`
  - 真机点位：面板右上「编辑」≈ (1087,1039)；**本次点击被系统安装器抢占，未完成点击验证**（静态链已闭合）
- 反向：本页无对外跳转（只有 `finish()` 与内部增删/排序）

## 1. 界面结构

| 层级 | 方法 | code_units | 说明 |
|---|---|---|---|
| Activity 入口 | `onCreate(Landroid/os/Bundle;)V` | 27 | `EdgeToEdge.enable$default(activity, null, null, 3, null)` @0x0005；`setContent` → `ToolsManagerActivity$g` |
| 页面外壳 | `$g.b(Composer;I)V` | 123 | trace `onCreate.<anonymous> (ToolsManagerActivity.kt:63)`；`Modifier.background(P8.a.s()=#FFFFFFFF)` → `I.c(Modifier, title=null, onBack, null, content=$g$a, …)`；**标题传 null ⇒ 顶栏只有返回箭头**（未截图确认） |
| 内容 | `$g$a.c(ColumnScope;Composer;I)` | 972 | trace `onCreate.<anonymous>.<anonymous> (ToolsManagerActivity.kt:67)` |
| 首页网格 | `ToolsManagerActivity.Z(Composer;I)` | 250 | trace `GridList (ToolsManagerActivity.kt:169)` |
| 全部网格 | 由 `$g$a.d(LazyGridScope)` @0x0005 取 `ToolsManager.getAllMenuList()` | 51 | 4 列、点击 `addNow(index)` |

内容列（`$g$a.c`）自上而下：

| 顺序 | 元素 | 证据 |
|---|---|---|
| 根 | `fillMaxSize` + 背景 **`#FFF0F3F7`** + `verticalScroll` + Column | @0x0030-0x0052 |
| 外层内边距 | 顶部 10dp（`padding-qDBjuR0` 仅 top 生效） | @0x00da-0x00e9 |
| 卡片 1 | `fillMaxWidth` + 背景 `P8.a.s()`（白） + padding 12dp + Column | @0x00f2-0x010a |
| 卡片 1 标题 | `Text("首页展示功能")`，`FontWeight.Bold`，颜色 `P8.a.m()`=`#FF23272B` | @0x0185-0x01db |
| 卡片 1 说明 | `Text("以下功能将在首页展示快捷入口，在下方全部功能中点击即可快速添加，长按图标还可以对首页展示功能进行拖拽排序。")`，颜色 **`#FF657389`** | @0x01ea-0x0238 |
| 卡片 1 内容 | `GridList(nowMenuList)` | @0x0245-0x0249 |
| 间隔 | 10dp | @0x024f |
| 卡片 2 | 背景白 + padding 12dp；标题 `Text("全部功能")` Bold `#FF23272B`；8dp 间隔；`LazyVerticalGrid(allMenuList)` | @0x0267-0x0344 |
| 底部 | `navigationBarsPadding` + 20dp Spacer | @0x03a9-0x03b9 |

两个网格（`Z` / `$g$a.d`）：

| 参数 | 值 | 证据 |
|---|---|---|
| 列数 | `GridCells.Fixed(4)` | @0x007f-0x0085 |
| 单元格间距 | 水平/垂直各 10dp（`Arrangement.spacedBy(10dp)`） | @0x0085-0x00a1 |
| 网格内边距 | `PaddingValues(10dp)` | @0x0085-0x008c |
| 高度上限 | `heightIn(max = 1000dp)` | @0x0072-0x0079 |
| 列表状态 | `rememberLazyGridState()` | @0x0031-0x0036 |
| 首页网格可拖拽排序 | `sh.calvin.reorderable.o.h(LazyGridState, …)` 生成拖拽状态 | @0x0066（`Z`） |
| item key | `ToolsEnum.getIconResId()`（`b0` @0x0005） | 两处 `items(... key …)` 回调 |

单元格内容：图标 `ToolsEnum.getIconResId()`（**mipmap**，如 `R.mipmap.ic_tool_compass`）+ 角标 `R.drawable.svg_menu_add`（未添加，＋）/ `svg_menu_added`（已添加，✓）+ 文字 `ToolsEnum.getRealName()`（`$g$a.e` @0x023f、`$a` @0x0245）。

## 2. 交互与业务逻辑

| 交互 | 方法 | code_units | 行为 |
|---|---|---|---|
| 全部功能里点某项 | `$g$a$a` → `ToolsManager.addNow(I)` | 5 / 116 | 未达上限且不重复 → 加入首页列表并 `saveJson()` |
| 首页列表点某项 | `$a$a` → `ToolsManager.removeNow(I)` | 5 / 9 | 从首页列表移除并 `saveJson()` |
| 首页列表长按拖动 | `$f` → `ToolsManager.moveMenu(I,I)` | 4 / 34 | 交换/插入后 `saveJson()` |
| 返回 | `$g.c` @0x0000 → `Activity.finish()` | 6 | — |

`ToolsManager.addNow(int)` 上限公式（逐指令，`space.xinzhi.globe.manager.ToolsManager`，code_units=116）：

```
cap = (screenHeight - dip(350)) / dip(56)          // 0x0000-0x0019
if (!AccountModelKt.isVip(currentAccount)) cap -= 1 // 0x001c-0x0029
if (nowMenuList.size() >= cap)
    toast("当前展示功能过多，请移除部分功能后尝试")   // 0x0068-0x0073
else if (allMenuList[i] 不在 nowMenuList（按 getText 比较）)
    nowMenuList.add(allMenuList[i]); saveJson()
```

> 常数：`350dp` 与 `56dp` 为 `ContextKt.dip(context, 350/56)` 的入参十进制常量（0x15e=350、0x38=56），**不是预估**。

## 3. 持久化与默认值

`ToolsManager`（`space.xinzhi.globe.manager.ToolsManager`，17 方法）：

| 项 | 值 | 证据 |
|---|---|---|
| MMKV 实例 ID | `"ToolsManager"` | `mmkv_delegate$lambda$0` @0x0000 |
| 生效 key | `"toolsJson2"` | `getToolsJson2` / `setToolsJson2` @0x0004 |
| 遗留 key | `"toolsJson"`（有 get/set，但当前流程不再写入） | `getToolsJson` / `setToolsJson` @0x0004 |
| 值格式 | JSON 数组，元素是 **中文名**（`ToolsEnum.getText()`），如 `["超清优化","海拔查询","罗盘","路网","等高线"]` | `<clinit>` @0x003a-0x0075；`saveJson` @0x0000-0x0025 |
| 默认首页列表（首次启动写入） | `SUPER_MAP_OLD, ALTITUDE, COMPASS, ROAD, ISOLINE` → **超清优化、海拔查询、罗盘、路网、等高线** | `<clinit>` @0x003f-0x0075 |
| 全部功能来源 | `allMenuList = ToolsEnum.getTools()` | `<clinit>` @0x0021-0x002b |

`ToolsEnum$Companion.getTools()`（code_units=49）过滤规则：

1. 从 `getEntries()` 复制全量 18 项；
2. 移除 `STREET`（景点）、`VR`（VR全景）；
3. `AppConfig.hidePassAuditFunction()` 为真 → 再移除 `COMPASS`；
4. `ChannelName.isRestrictedFeatureChannel(channelName)` 为真 → 再移除 `GEOGRAPHIC_IMAGERY`。

默认渠道 = **16 项**，与真机「地图工具」面板 16 格一致（截图 `51-relaunch.png`）。

## 4. 18 个工具枚举（`ToolsEnum`，供全局复用）

`<clinit>` code_units=319；构造器 `(String text, int iconResId, int mainIcon)`。

| ordinal | 枚举 | 中文 | iconResId（mipmap） | mainIcon（drawable） |
|---|---|---|---|---|
| 0 | COMPASS | 罗盘 | `ic_tool_compass` | `svg_compass` |
| 1 | SUPER_MAP | 超清地图 | `ic_tool_super_map` | `svg_super_map` |
| 2 | MOUNTAIN | 看山脉 | `ic_tool_mountain` | `svg_mountain2` |
| 3 | WATER | 看水系 | `ic_tool_water` | `svg_water2` |
| 4 | VR | VR全景 | `ic_tool_vr` | `svg_vr2` |
| 5 | ALTITUDE | 海拔查询 | `ic_tool_altitude` | `svg_altitude` |
| 6 | WEATHER | 天气 | `ic_tool_weather` | `svg_weather` |
| 7 | HISTORY | 历史影像 | `ic_tool_history` | `svg_history` |
| 8 | ISOLINE | 等高线 | `ic_tool_isoline` | `svg_isoline` |
| 9 | COLLECT | 收藏点 | `ic_tool_collect` | `svg_collect` |
| 10 | STREET | 景点 | `ic_tools_street` | `svg_street` |
| 11 | MEASURE | 测量 | `ic_tool_measure` | `svg_measure` |
| 12 | DRAW | 画笔 | `ic_tool_paint` | `svg_paint` |
| 13 | FISH | 钓点 | `ic_tool_fish` | `svg_fish_point` |
| 14 | TRACK_RECORD | 轨迹记录 | `ic_tools_track` | `svg_track` |
| 15 | ROAD | 路网 | `ic_tools_road` | `svg_road` |
| 16 | GEOGRAPHIC_IMAGERY | 地理影像 | `ic_tools_geographic_imagery` | `svg_geo_imagery` |
| 17 | SUPER_MAP_OLD | 超清优化 | `ic_tool_super_map_old` | `svg_super_map_old` |

> 字段序：`text`/`iconResId`/`mainIcon`；`getIconResId()` 给网格用（mipmap），`getMainIcon()` 给其它入口（drawable）。点击派发见 `00-navigation-map.md` §5。

## 5. 关键方法地址表

| 方法 | code_units | 关键偏移 |
|---|---|---|
| `ToolsManagerActivity.onCreate` | 27 | 0x0005 `EdgeToEdge.enable`；0x0017 `setContent` |
| `$g.b` | 123 | 0x0021 `P8.a.s()`；0x006e `I.c(…title=null…)` |
| `$g$a.c` | 972 | 0x0034 背景 `#F0F3F7`；0x019b `"首页展示功能"`；0x0205 说明文案；0x0307 `"全部功能"` |
| `Z` | 250 | 0x0022 trace `GridList`；0x007f `Fixed(4)`；0x0066 拖拽 |
| `$g$a.d` | 51 | 0x0005 `getAllMenuList` |
| `$g$a.e` | — | 0x023f `getRealName()` |
| `ToolsManager.<clinit>` | 192 | 0x002e `getToolsJson2`；0x003f-0x0069 默认 5 项；0x008c 回填 nowMenuList |
| `ToolsManager.addNow` | 116 | 0x0000-0x0029 上限；0x006e toast |
| `ToolsManager.saveJson` | 44 | 0x0028 `setToolsJson2` |

（偏移为方法内 code offset；dex 文件绝对偏移未换算。）

## 6. 依赖资源 / 数据源

- 图标：上表 mipmap / drawable；角标 `svg_menu_add`、`svg_menu_added`
- 文案：`R.string` 未使用，全部内联中文（`const-string`）
- 持久化：MMKV `ToolsManager` / key `toolsJson2`
- 网络：无
- 依赖第三方：`sh.calvin.reorderable`（Compose 拖拽排序）、`com.tencent.mmkv`、`com.alibaba.fastjson`

## 7. 截图

- 入口面板：`E:\APPDetector\lc-gap-evidence\nav\51-relaunch.png`（「地图工具」16 格 + 编辑）
  sha256 `e8b9c5630a0827f93822901d07d4461493fb31a0699baf7255f420e2f47ba091`
- **本页本身截图未取**（点击「编辑」时被系统安装器抢焦点）→ 需真机补拍

## 8. 改动建议

1. 客户端「工具管理」按本页两段式实现：`首页展示功能`（可拖拽）+ `全部功能`（点击添加），4 列、10dp 间距、卡片白底 12dp 内边距、页面底 `#F0F3F7`。
2. 首页快捷项持久化用同一语义（数组、按中文名匹配）；默认 5 项：超清优化/海拔查询/罗盘/路网/等高线。
3. 上限公式可直接照抄（VIP +1 格），toast 文案一致。
4. 差距矩阵：本页「缺失 → 待派单（客户端）」；入口依赖「更多→地图工具→编辑」三层，需与地图组对齐「更多」面板归属。
