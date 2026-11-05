# MapSettingActivity（地图设置） · 逆向取证

- 类：`space.xinzhi.globe.ui.pages.setting.MapSettingActivity`（父类 `ui.base.BaseActivity`）
- DEX：`classes06.dex`；规模：87 方法 / 18 字段 / 10 个内部类
- 入口：**左侧抽屉「地图设置」**（`MainDrawerContent` 第 1 项，行中心 y≈677，x≈460）
  - 调用链：`MainActivity` 构造器建 `MainActivity$d("地图设置", R.drawable.svg_setting, lambda M1)` → `M1.invoke()` → `MainActivity.n1` → `MainActivity.D7`（`sget MapSettingActivity.u` → `MapSettingActivity$a.a(Context)`）
  - `MapSettingActivity$a.a(Context)`（code_units=16）：`new Intent(context, MapSettingActivity.class)` + `startActivity`，**无 extras**
  - **真机点击未完成验证**：取证期间另一进程反复 `adb install`，华为安装器抢焦点，多次点击被吞（见 `00-navigation-map.md` §0）

## 1. 页面结构与状态

`onCreate`（code_units=21）：`BaseActivity.onCreate` → `setContent` → `MapSettingActivity$e`；`onDestroy` 调 `MapWebController.release()`（本页持有 WebView 地图控制器 `t`）。

Activity 字段即页面状态（Compose 单页可变状态，**不逐次落盘，落盘在 `MapManager`**）：

| 字段 | 类型 | 作用（按 setter/getter 使用方式） |
|---|---|---|
| `d`–`m` | `MutableState`（9 个） | 各开关/下拉的 UI 选中态 |
| `n` | `MutableIntState` | 地图滤镜模式索引 |
| `o`–`s` | `MutableFloatState`（5 个） | 5 个栅格参数滑杆值 |
| `t` | `map.MapWebController` | 地图效果预览的 WebView 控制器 |

| 层级 | 方法 | code_units | 说明 |
|---|---|---|---|
| Activity 入口 | `onCreate(Bundle)` | 21 | `EdgeToEdge` 未显式调用（与 ToolsManager 不同） |
| 页面外壳 | `$e.f(Composer;I)` | 448 | trace `onCreate.<anonymous> (MapSettingActivity.kt:156)`；`rememberScrollState` + `createCompositionCoroutineScope`；`I.c(Modifier, "地图设置", onBack, null, content=$e$a, …)`；返回 = `$e.g` → `finish()` |
| 内容 | `$e$a.j(ColumnScope;Composer;I)` | 806 | trace `onCreate.<anonymous>.<anonymous> (MapSettingActivity.kt:159)` |
| 开关行 | `MapSettingActivity.J0(String, boolean, Function0, Composer, int)` | 463 | trace `SwitchItem (MapSettingActivity.kt:269)`：白底、水平 12dp / 垂直 11dp 内边距、Row 居中、文字 **15sp / Medium / `#FF23272B`**、右侧 Switch |
| 行间隔 | `O0(…)` | — | 每行后调用一次（j 中 8 行各跟一次） |

内容列（`$e$a.j` 顺序，逐标签）：

| # | 内容 | 交互 | 证据 |
|---|---|---|---|
| 0 | `地图滤镜` 面板（`C0`）：`模式` + `常规 / 自然 / 高亮 / 自定义` + `地图效果预览` | 切模式 → 预设参数；预览用 `d`/`t` | `C0` code_units=941，字符串 @对应 const-string；调用点 `P0 → C0` @0x0000 |
| 1 | `显示路网` | 开关 | @0x018d；toggle lambda `setting.Y`（→ `$e$a.i`） |
| 2 | `允许地图旋转` | 开关 | @0x01c8；lambda `setting.Z` |
| 3 | `回到上次浏览位置` | 开关 | @0x01fe；lambda `setting.a0` |
| 4 | `地图屏幕常亮` | 开关 | @0x0234；lambda `setting.b0` |
| 5 | `3D模式操作按钮` | 开关 | @0x026a；lambda `setting.c0` |
| 6 | `显示中心十字架` | 开关 | @0x02a0；lambda `setting.d0` |
| 7 | `坐标系` | 下拉（`CrsEnum` 全量） | @0x02da；lambda `setting.e0` |
| 8 | `经纬度显示格式` | 下拉（`LngLatFormatEnum` 全量） | @0x0314；lambda `setting.f0` |

下拉选中回调（逐指令）：

- 坐标系：`$e.a` → `$e.j(entries, activity, index)`：`MapSettingActivity.l1(enum)`（写 UI 状态）+ `MapManager.setCurrentCrs(enum)`
- 经纬度格式：`$e.h` → `m1(enum)` + `MapManager.setCurrentLngLatFormat(enum)`

参数面板（`o0`，code_units=1456，调用者 `N0`/`z0`）：

| 元素 | 说明 |
|---|---|
| 标题 `参数` + 按钮 `恢复默认` | 恢复默认 = 按当前模式预设回填 |
| `图层亮度最小值` / `图层亮度最大值` / `图层饱和度` / `图层对比度` / `图层色相旋转` | 5 个滑杆行（`H0` 被 `o0` 调用 5 次，@0x036c/0x03e1/0x0457/0x04cd/0x0541） |
| 说明文本 | `图层亮度最小值：设置栅格图层最暗部分的亮度，越大图层整体越亮\n图层亮度最大值：…\n图层饱和度：…\n图层对比度：…\n图层色相旋转：…` |
| 滑杆行 | `H0(String label, float min, float max, float value, Function0 reset, Function1 onChange, …)`，trace `图层色相旋转` |

写回通道：`MainViewModel` 之外直接调 `MapWebApiKt.rasterBrightnessMin/Max/Saturation/Contrast/HueRotate(MapWebController, float)` 推给 WebView（`MapSettingActivity` 内已核实的调用集合）。

> 参数面板与「地图滤镜-自定义」的嵌套关系未逐指令闭合（`o0` 由 `N0/z0` 组合，`C0` 由 `P0/E0` 组合），**需真机截图确认**。

## 2. 地图滤镜预设（新证实数值）

`space.xinzhi.globe.manager.MapColorParamEnum`（`<clinit>` code_units=71；构造器参数序 `(name, ordinal, brightnessMin, brightnessMax, saturation, contrast, hueRotate)`）：

| 枚举 | 中文（UI 顺序） | brightnessMin | brightnessMax | saturation | contrast | hueRotate | 证据 |
|---|---|---|---|---|---|---|---|
| `COMMON` | 常规（index 0） | 0.0 | 1.0 | 0.0 | 0.0 | 0.0 | @0x0002-0x000b |
| `NATURE` | 自然（index 1） | 0.0 | 1.0 | 0.4 | 0.1 | 0.0 | @0x0012-0x001f |
| `HIGHLIGHT` | 高亮（index 2） | 0.1 | 1.0 | 0.6 | 0.2 | 0.0 | @0x0026-0x0035 |

浮点常量原文：`0x3dcccccd`=0.1、`0x3e4ccccd`=0.2、`0x3ecccccd`=0.4、`0x3f19999a`=0.6、`0x3f800000`=1.0。

**默认模式 = HIGHLIGHT（高亮）**：`MapManager.getCurrentColorMode()` 用 `decodeInt("currentColorMode", 2)`，且 5 个栅格参数的 `decodeFloat` 缺省值全部取自 `MapColorParamEnum.HIGHLIGHT`（见 §4）。

## 3. 枚举值

`space.xinzhi.globe.common.CrsEnum`（`<clinit>` code_units=…）：

| 枚举 | code（持久化用） | display |
|---|---|---|
| `WGS84` | `wgs84` | WGS84 |
| `GCJ02` | **`gjc02`**（原版就是错拼，非笔误） | GCJ02 |
| `BD09` | `bd09` | BD09 |
| `CGCS2000` | `cgcs2000` | CGCS2000 |

默认 = `WGS84`（`CrsEnum.default` = 首个枚举实例，@0x0044）。

`space.xinzhi.globe.common.LngLatFormatEnum`：

| 枚举 | code | 中文 | 显示名 |
|---|---|---|---|
| `D` | `d` | 度 | D |
| `DM` | `dm` | 度分 | DM |
| `DMS` | `dms` | 度分秒 | DMS |

默认 = `D`（度）。

> 若 LC 要兼容原版 MMKV 数据（同一设备迁移），`gjc02` 这个错拼必须保留；仅内部实现可自行纠正。

## 4. 持久化键与默认值（`space.xinzhi.globe.manager.MapManager`）

MMKV 实例 ID：`"MapManager"`（`mmkv_delegate` @0x0000）。键与默认值逐条取证：

| 键 | 类型 | 默认 | 对应 UI |
|---|---|---|---|
| `showRoad` | bool | **true** | 显示路网 |
| `isRotate` | bool | **false** | 允许地图旋转 |
| `backToLastMapCenter` | bool | **false** | 回到上次浏览位置 |
| `mapKeepScreenOn` | bool | **true** | 地图屏幕常亮 |
| `show3dControl` | bool | **false** | 3D模式操作按钮 |
| `showMapCenterCross` | bool | **true** | 显示中心十字架 |
| `currentCrs` | string | 空→`CrsEnum.default`=`wgs84` | 坐标系 |
| `currentLngLatFormat` | string | 空→`d` | 经纬度显示格式 |
| `currentColorMode` | int | **2**（HIGHLIGHT） | 地图滤镜模式 |
| `rasterBrightnessMin` | float | 0.1 | 参数滑杆 |
| `rasterBrightnessMax` | float | 1.0 | 参数滑杆 |
| `rasterSaturation` | float | 0.6 | 参数滑杆 |
| `rasterContrast` | float | 0.2 | 参数滑杆 |
| `rasterHueRotate` | float | 0.0 | 参数滑杆 |
| `lastBaseMap` / `lastMapCenter` | string | — | 回到上次位置 |
| `showStreet` / `overlay_*` / `customBaseMap` / `admin_boundary_list` | — | — | 叠加/边界（本页不写） |

每次 setter 同时发 EventBus 事件：`event_show_road_changed`、`event_is_rotate_changed`、`event_back_last_map_center_changed`、`event_lng_lat_format_changed`、`event_current_crs_changed`、`event_raster_brightness_min/max`、`event_raster_saturation`、`event_raster_contrast`、`event_raster_hue_rotate`、`event_show_3d_control_changed`、`event_show_center_cross_changed`。

## 5. 关键方法地址表

| 方法 | code_units | 关键偏移 |
|---|---|---|
| `MapSettingActivity.onCreate` | 21 | 0x0003 `new $e`；0x0011 `setContent` |
| `onDestroy` | 9 | 0x0000 `MapWebController.release()` |
| `$e.f` | 448 | 0x0017 trace kt:156；0x0079 `"地图设置"` |
| `$e$a.j` | 806 | 0x018d…0x0314 八个标签；每行 `R0`+`O0` |
| `J0`（SwitchItem） | 463 | 0x005f trace kt:269；0x0110 15sp；0x0116 `#23272B` |
| `C0`（地图滤镜） | 941 | `模式`/`常规`/`自然`/`高亮`/`自定义`/`地图效果预览` |
| `o0`（参数） | 1456 | `参数`/`恢复默认`/5 滑杆/说明 |
| `H0`（滑杆行） | 616 | `图层色相旋转` |
| `$e.j` | 17 | 0x0006 `l1`；0x000b `MapManager.setCurrentCrs` |
| `$e.h` | 17 | 0x0006 `m1`；0x000b `MapManager.setCurrentLngLatFormat` |
| `MapColorParamEnum.<clinit>` | 71 | 三组预设（§2） |
| `MapManager.getCurrentColorMode` | 12 | `decodeInt("currentColorMode", 2)` |
| `MapManager.getRaster*` | 17 各 | 缺省 = `HIGHLIGHT` 对应值 |

（偏移为方法内 code offset。）

## 6. 截图

**未取**（本页点击被安装器抢占）。待补：进入抽屉 →「地图设置」，截 3 屏（滤镜/开关/参数）。
现有可引用：抽屉基准图 `E:\APPDetector\lc-gap-evidence\nav\55-mapsetting.png`
sha256 `a31302c8dd00be32dde25994cdba75b22f072e770dd0b5e7320293e84ddebc88`。

## 7. 改动建议

1. **数值入档**：`MapColorParamEnum` 三组预设 + `currentColorMode` 默认 2 + 5 个栅格默认值，应在 `app/mechanics.json` 登记为「地图滤镜」小节（建议 key：`mapFilter.presets[]`、`mapFilter.defaultModeIndex`），并同步 `app/MECHANICS.md`。
2. 客户端实现 `MapSettingActivity` 时：开关默认值照抄 §4；`坐标系` 默认 WGS84、`经纬度格式` 默认 度；若做数据迁移保留 `gjc02` 键值。
3. 视觉：白底 SwitchItem、15sp Medium `#23272B`、行内边距 12/11dp，页面标题「地图设置」。
4. 差距矩阵：本页 = **缺失（P0，待派单客户端）**；依赖「抽屉入口」与「地图组 MapManager 等价物」。
