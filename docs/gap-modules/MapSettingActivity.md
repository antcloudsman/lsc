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

> 真机复验（2026-11-05，截图见 §6）确认实际渲染顺序：**标题 → 「地图滤镜」区块（模式 chips → [仅「自定义」时展开「参数」5 滑杆] → 地图效果预览）→ 6 个开关 → 坐标系 → 经纬度显示格式**。即「参数」不属于常规/自然/高亮，只有选「自定义」才出现；「地图效果预览」始终在滤镜区块内、开关之前。
>
> 参数面板（`o0`，code_units=1456，调用者 `N0`/`z0`）内容与截图一致：`参数` + `恢复默认` + 5 滑杆（最小值/最大值/饱和度/对比度/色相旋转）+ 5 行说明。

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

## 6. 截图（真机，已交付）

- 目录：`E:\APPDetector\lc-gap-evidence\nav\mapsetting\`
- 索引：`INDEX.md`（每图 → 控件/状态/坐标范围/sha256）
- 口径：原版 **1.6.1 / 1228×2700 / density 540**，设备 `L2E0222531014833`，未登录态
- 20 张：整页 1 + 滤镜四态 4 + 6 开关开/关 12 + 坐标系/经纬度下拉 2 + 释放前主页 1

关键实测（与 DEX 取值互证）：

| 项 | 实测 | 对应 DEX |
|---|---|---|
| 默认模式 | **高亮**被选中 | `MapManager.getCurrentColorMode()` = `2` |
| 开关默认 | 路网 开 / 旋转 关 / 回上次 关 / 常亮 开 / 3D 关 / 十字架 开 | `MapManager` 六键缺席省值逐条一致 |
| 坐标系 | WGS84（下拉另含 GCJ02/BD09/CGCS2000） | `CrsEnum.default = WGS84` |
| 经纬度格式 | 度（下拉另含 度分/度分秒） | `LngLatFormatEnum.default = D` |
| 自定义参数初值 | 0.1 / 1.0 / 0.6 / 0.2 / 0 | `MapColorParamEnum.HIGHLIGHT` 五值 |

视觉量测（来自 `01-整页.png`）：页面白底 `#FFFFFF`；区块间隔条 **`#F0F3F7`**（与 DEX `HorizontalDivider` 颜色常量一致）；标题纯黑、字形带高 48px；行标签黑、字形带高 45px；开关 **156×78px**（x 1032–1188）；开关行距 **157px**；模式 chip 约 **245×97px**（中心 x 162/463/764/1065）。字体族与精确 sp **未量**。

## 7. 改动建议

1. **数值入档**：`MapColorParamEnum` 三组预设 + `currentColorMode` 默认 2 + 5 个栅格默认值，应在 `app/mechanics.json` 登记为「地图滤镜」小节（建议 key：`mapFilter.presets[]`、`mapFilter.defaultModeIndex`），并同步 `app/MECHANICS.md`。
2. 客户端实现 `MapSettingActivity` 时：开关默认值照抄 §4；`坐标系` 默认 WGS84、`经纬度格式` 默认 度；若做数据迁移保留 `gjc02` 键值。
3. 视觉：白底 SwitchItem、15sp Medium `#23272B`、行内边距 12/11dp，页面标题「地图设置」。
4. 差距矩阵：本页 = **缺失（P0，待派单客户端）**；依赖「抽屉入口」与「地图组 MapManager 等价物」。

## §参数面板滑杆量程（H0 / SeekBar 5 滑杆）

来源：`classes06.dex` 内 `MapSettingActivity` 及其内部类，纯静态（无设备）。

方法链：`o0`（trace `CustomParams (MapSettingActivity.kt:461)`）→ `H0`（trace `SeekBar (MapSettingActivity.kt:545)`）→ `setting.h0.b`（trace `ParamSeekBar (MapSettingActivity.kt:589)`，自定义 Canvas 滑杆，**不是** material3 `Slider`）。

`H0` 签名（`android-class-show`）：`H0(String, F, F, F, Function0, Function1, Composer, int, int)`，三个 float 依次为 **min / max / value**（由 5 个调用点的常量与 getter 位置关系确定，见下表）。

### 1. 五个滑杆的 min / max（H0 调用点常量）

| # | 标签（界面顺序） | H0 调用 | v2 = min（常量 @ 偏移） | v3 = max（常量 @ 偏移） | v4 = 当前值 |
|---|---|---|---|---|---|
| 1 | 图层亮度最小值 | `o0` @0x036c | `const/4 v2, 0` @0x0367 = **0.0f**（`0x00000000`） | `const/high16 v3, 0x3f800000` @0x036a = **1.0f** | `e1()` @0x02eb → v4 @0x02ee |
| 2 | 图层亮度最大值 | `o0` @0x03e1 | `const/4 v2, 0` @0x03de = **0.0f**（`0x00000000`） | `const/high16 v3, 0x3f800000` @0x03df = **1.0f** | `f1()` @0x036f → v4 @0x0372 |
| 3 | 图层饱和度 | `o0` @0x0457 | `const/high16 v2, 0xffffffffbf800000` @0x0453 = **-1.0f**（`0xbf800000`） | `const/high16 v3, 0x3f800000` @0x0455 = **1.0f** | `g1()` @0x03e4 → v4 @0x03e7 |
| 4 | 图层对比度 | `o0` @0x04cd | `const/high16 v2, 0xffffffffbf800000` @0x04c9 = **-1.0f** | `const/high16 v3, 0x3f800000` @0x04cb = **1.0f** | `h1()` @0x045a → v4 @0x045d |
| 5 | 图层色相旋转 | `o0` @0x0541 | `const/high16 v2, 0xffffffffc3340000` @0x053d = **-180.0f**（`0xc3340000`） | `const/high16 v3, 0x43340000` @0x053f = **180.0f** | `i1()` @0x04d0 → v4 @0x04d3 |

> 说明：反汇编里 `0xffffffffbf800000` 是 dex 对 `0xbf800000` 的 64 位符号扩展写法，实际 bit pattern 就是 `bf800000`（-1.0f）；`0xffffffffc3340000` 同理 = `c3340000`（-180.0f）。float 与 `MapColorParamEnum` 的字段序（min/max/saturation/contrast/hue）一一对应。

### 2. 当前值从哪来（v4）

5 个 getter 都是 `MutableFloatState.getFloatValue()`：

| getter | 字段 | 参数 |
|---|---|---|
| `e1()` @0x0000 `iget-object …->o` | `o` | 亮度最小 |
| `f1()` | `p` | 亮度最大 |
| `g1()` | `q` | 饱和度 |
| `h1()` | `r` | 对比度 |
| `i1()` | `s` | 色相旋转 |

`o..s` 的初值在 `MapSettingActivity.<init>` 里由 `mutableFloatStateOf(MapManager.getRasterBrightnessMin/getRasterBrightnessMax/getRasterSaturation/getRasterContrast/getRasterHueRotate)` 写入（对应偏移 0x0092/0x00bb/0x00e4/0x010d/0x0136）。因 `MapManager` 缺省取 `MapColorParamEnum.HIGHLIGHT`，所以自定义面板首次打开显示 **0.1 / 1.0 / 0.6 / 0.2 / 0**，与真机截图 `05-滤镜-自定义.png` 一致。

### 3. 刻度与步长（step）

- **H0 没有 step 入参**（签名只有三个 float）。`h0.b` 也只多一个 float：`h0.b(F, F, F, F, Function0, Function1, Modifier, Composer, int, int)`，其第 4 个 float 在 `H0` 的调用点恒为 **0f**（`const/4 v3, 0` @0x01d8，随后 `invoke-static/range {v0 .. v9}` @0x01e5），即"未传"，不是量程参数。
- 内部刻度：`h0.b` 对三个入参各乘 **100.0f**（`const/high16 v2, 0x42c80000` @0x00ec；`mul-float` @0x0122 / 0x0125 / 0x0127），即滑杆自己按 0..100 的比例画；
- 回写归一化：5 个 setter 都先 **÷100.0f**（`0x42c80000` @0x0002 + `div-float/2addr` @0x0004），再格式化：
  - `q0`（亮度最小）`"%.1f"` @0x0012 → `w1(F)` → `rasterBrightnessMin`
  - `s0`（亮度最大）`"%.1f"` → `x1(F)` → `rasterBrightnessMax`
  - `u0`（饱和度）`"%.1f"` → `y1(F)` → `rasterSaturation`
  - `w0`（对比度）`"%.1f"` → `z1(F)` → `rasterContrast`
  - `y0`（色相旋转）**`"%.0f"`** @0x0012 → `A1(F)` → `rasterHueRotate`
- 结论：**有效步长 = 0.1**（前四个参数），**色相 = 1°**（`%.0f` 取整）；不是由滑杆参数传入，而是 setter 舍入产生的。

### 4. H0 是否只在「自定义」出现

`H0` 本身是通用滑杆行（标签 + 滑杆 + 数值），**不做模式判断**；判断在滤镜面板 `C0`：

1. 四个模式 chip 都由 `L0(String label, int modeIndex, MapColorParamEnum preset, Function1 onChange, …)` 渲染，其中 `const-string "自定义"` @0x02b8 配 `const/4 v2, 3` @0x02ba（modeIndex=3）；
2. chip 之后 `invoke-virtual …->X0()I` @0x02c4 取当前 `currentColorMode`；`if-ne v1, v2, :02cc` @0x02c8 与 3 比较，决定可见性布尔 `v10`；
3. 该布尔作为 `AnimatedVisibilityKt.AnimatedVisibility(ColumnScope, Z visible, …)` @0x02f1 的第 2 参，内容 lambda = `MapSettingActivity$c`（`Ctrace MapPreview.<anonymous>.<anonymous> (MapSettingActivity.kt:371)`）→ `N0` → `o0`（CustomParams）。

⇒ **参数面板只在 `currentColorMode == 3`（自定义）时以 AnimatedVisibility 展开**；其他三态不组合 `o0`，因此也不存在滑杆。真机互证：`02/03/04` 三态无参数区，`05-自定义` 才有。

### 5. 给客户端的实现口径

| 参数 | 量程 | 显示/回写精度 |
|---|---|---|
| 图层亮度最小值 | 0.0 ～ 1.0 | 0.1（"%.1f"） |
| 图层亮度最大值 | 0.0 ～ 1.0 | 0.1 |
| 图层饱和度 | -1.0 ～ 1.0 | 0.1 |
| 图层对比度 | -1.0 ～ 1.0 | 0.1 |
| 图层色相旋转 | -180.0 ～ 180.0 | 1（"%.0f"） |

若用 material3 `Slider` 复刻，建议 `valueRange = min..max`、不传 `steps`（连续），并在 `onValueChangeFinished`/回写处按上表精度 round，才与原版一致。

## §「恢复默认」按钮（自定义参数区）

**结论：原版有该控件；点击后回填的是 `MapColorParamEnum.COMMON`（常规）预设 0 / 1 / 0 / 0 / 0，不是高亮预设。**

链路（逐指令）：

| 步骤 | 证据 |
|---|---|
| 控件 | `o0`（`CustomParams`）里一行：图标 `R.drawable.svg_refresh2`，文字 `const-string "恢复默认"` @0x02bd，文字色 `0xff42464d` @0x02ae，整行 `clickable`（`n0$a(0x12c=300ms)` 自绘水波纹）@0x01ee-0x0201 |
| 点击回调 | `o0` 的点击用 `v10`（= `o0` 的 Function0 入参，来自 N0）→ `setting.Q.invoke()` → `MapSettingActivity$c.a(activity)` → `MapSettingActivity$c.c(activity)` |
| 回填值来源 | `$c.c` code_units=80：@0x0000 `sget-object … MapColorParamEnum;->COMMON`，随后依次取 `getRasterBrightnessMin()` @0x0002、`getRasterBrightnessMax()` @0x0009、`getRasterSaturation()` @0x0010、`getRasterContrast()` @0x0017、`getRasterHueRotate()` @0x001e |
| 先写 UI 状态 | 同一个值立刻写入 5 个 `MutableFloatState`：`w1(F)` @0x0006（o）、`x1(F)` @0x000d（p）、`y1(F)` @0x0014（q）、`z1(F)` @0x001b（r）、`A1(F)` @0x0022（s）→ 滑杆位置随之下移/回位 |
| 再写 MapManager | 回读状态 `e1()/f1()/g1()/h1()/i1()` 后写 `MapManager.setRasterBrightnessMin/Max/Saturation/Contrast/HueRotate` @0x002b/0x0032/0x0039/0x0040/0x0047 |
| 最后推 WebView | `T0(activity)` @0x004a → `p1()`（5 个 `MapWebApiKt.raster*`） |

因此回填值是 **COMMON = brightnessMin 0.0 / brightnessMax 1.0 / saturation 0.0 / contrast 0.0 / hue 0.0**。

**重要差异**：`$c.c` 全程没有写 `currentColorMode`（没有 `setCurrentColorMode` 调用），所以点「恢复默认」后模式 chip 仍停在「自定义」，只是 5 个滑杆值变成常规预设。
（对照：切到「高亮」chip 走的是另一条路径 `L0(..., MapColorParamEnum.HIGHLIGHT, ...)`，会把模式与参数一起换成高亮。）

给客户端：若要 1:1，「恢复默认」应按 **常规 0/1/0/0/0** 回填且不改模式 chip；先前按高亮 0.1/1.0/0.6/0.2/0 的实现需要改。
