# SettingActivity（系统设置） · 逆向取证

- 类：`space.xinzhi.globe.ui.pages.setting.SettingActivity`
- DEX：`classes06.dex`（`E:\APPDetector\output\.inspect\unpack\mem_dump\classes06.dex`）
- 父类：`space.xinzhi.globe.ui.base.BaseActivity`
- 规模：2 字段 / 11 方法（`android-class-show` 输出）
- 结论：**原版 v1.6.1 里不可达（死代码）**，见 §1。

## 1. 入口（关键结论）

| 检查 | 结果 |
|---|---|
| 全 DEX 内对 `Lspace/xinzhi/globe/ui/pages/setting/SettingActivity;` 的引用 | **0**（`android-class-users-find` 对 `classes06.dex`；该 dex 是唯一包含此类的 DEX，python 全量扫描 `mem_dump\*.dex` 亦仅 classes06 命中） |
| 唯一启动器 `SettingActivity$a.a(Context)` 的调用者 | **0**（`android-class-users-find` 只返回它自身 `<init>` 与 `SettingActivity.<clinit>`） |
| 抽屉（`MainDrawerContent`，12 条）是否含此页 | 否。抽屉里是「地图设置 → MapSettingActivity」「账户设置 → AccountSettingActivity/LoginActivity」 |
| 字符串反射（`Class.forName("…SettingActivity")` / 路由名） | 无：dex 字符串池只有 `L…;` 描述符，无 `space.xinzhi.globe…SettingActivity` 明文路由 |

⇒ 结论：v1.6.1 的设置入口是**抽屉两个独立页**，不是这个「系统设置」聚合页；本页为历史遗留。当前 1:1 复刻**不应为它单开入口**，只需按差距矩阵登记「原版存在但不可达」。

## 2. 界面结构（Compose 方法链）

| 层级 | 方法（签名） | code_units | 关键点 |
|---|---|---|---|
| Activity 入口 | `onCreate(Landroid/os/Bundle;)V` | 21 | `setContent` → `SettingActivity$b`（composableLambda hash `0x6a2bb0a1`） |
| 页面外壳 | `SettingActivity$b.b(Landroidx/compose/runtime/Composer;I)V` | 112 | trace `SettingActivity.onCreate.<anonymous> (SettingActivity.kt:42)`；调 `ui.compose.I.c(Modifier, "系统设置", onBack, null, content, Composer, II)` |
| 内容 | `SettingActivity$b$a.c(Landroidx/compose/foundation/layout/ColumnScope;Composer;I)` | 346 | trace `…onCreate.<anonymous>.<anonymous> (SettingActivity.kt:43)`；列内 = 行1 + Divider + 行2 |
| 行（可点击项） | `SettingActivity.a0(Ljava/lang/String;Lkotlin/jvm/functions/Function0;Composer;I)V` | 467 | trace `SettingActivity.Item (SettingActivity.kt:64)` |
| 分隔线 | `SettingActivity.Y(Composer;I)V` | 242 | trace `SettingActivity.Divider (SettingActivity.kt:83)` |

页面外壳 `I.c` 的实参顺序（由 `$b.b` @0x005a-0x0063 还原）：`(Modifier, String title, Function0 onBack, Function3 subTitle?, Function3 content, Composer, int, int)`；`onBack` = `SettingActivity$b.c` → `Activity.finish()` @0x0000。

内容列（`$b$a.c`）：

| 顺序 | 内容 | 证据 |
|---|---|---|
| 0 | 12dp 竖向 Spacer | @0x00df-0x00eb `height-3ABfNKs(12dp)` |
| 1 | 行「**地图设置**」 | @0x0118 `const-string "地图设置"` → `SettingActivity.d0(…)`；点击 = `SettingActivity$b$a.b` → `d` → `MapSettingActivity$a.a(Context)` |
| 2 | Divider | @0x011d `SettingActivity.c0(…)` → `Y` |
| 3 | 行「**测绘资质**」 | @0x0145 `const-string "测绘资质"` → `d0`；点击 = `$b$a.a` → `e` → `MeasureProveActivity$a.a(Context)` |

行样式（`a0`）逐项：

| 属性 | 值 | 证据（偏移） |
|---|---|---|
| 宽度 | `fillMaxWidth` | @0x0054 |
| 点击 | `Modifier.clickable`，带 `n0$a(0x12c=300ms, onCLick, MutableLongState)`（自实现水波纹） | @0x0089-0x008f |
| 背景 | `LP8/a;->s()` = **`#FFFFFFFF`** | @0x0096；`P8.a.<clinit>` @0x004d-0x0056 |
| 内边距 | 水平 12dp / 垂直 15dp | @0x00a4, @0x00ab-0x00b2 |
| 排列 | Row，`centerVertically` + `Arrangement.Start` | @0x00b6-0x00c4 |
| 文本字号 | 15sp（`getSp(0xf)`）| @0x00aa-0x00b2 |
| 文本颜色 | **`0xFF23272B`** | @0x013d |
| 字重 | Medium | @0x0146-0x014b |
| 行尾 | `weight(1f)` Spacer + `R.drawable.svg_arrow_right` 箭头图 | @0x0195-0x01ac |
| 导航图标尺寸 | `Image` 参数 0x30(48)/0x7c(124)（Composer 标志位，非 dp） | @0x01a5-0x01a7 |

Divider（`Y`）：全宽 → 背景 `LP8/a;->s()`（白）→ 水平 12dp 内边距 → `HorizontalDivider` 高 1dp、颜色 **`0xFFF0F3F7`**（@0x00c7 `const-wide 0xfff0f3f7`）。

## 3. 关键业务方法

| 方法 | 引用 | 地址/偏移 | 说明 |
|---|---|---|---|
| `onCreate` | `L…/SettingActivity;->onCreate(Landroid/os/Bundle;)V` | 方法内 code offset 0x0000-0x0014（code_units=21） | `BaseActivity.onCreate` → `setContent($b)` |
| `SettingActivity$b.c` | `L…/SettingActivity$b;->c(L…/SettingActivity;)Lkotlin/Unit;` | 0x0000 `Activity.finish()` | 返回按钮 |
| `SettingActivity$b$a.d` | `…$b$a;->d(…)Lkotlin/Unit;` | 0x0002 `MapSettingActivity$a.a` | 「地图设置」点击 |
| `SettingActivity$b$a.e` | `…$b$a;->e(…)Lkotlin/Unit;` | 0x0002 `MeasureProveActivity$a.a` | 「测绘资质」点击 |
| `SettingActivity$a.a` | `L…/SettingActivity$a;->a(Landroid/content/Context;)V` | 0x0005-0x000c `Intent(Context, SettingActivity.class)` + `startActivity` | 启动器（无调用者） |

> 注：`android-method-show` 给的是**方法内 code offset**（相对方法起始的指令地址），不是 dex 文件绝对偏移；本表按此口径标注。需要文件偏移时再用 dex parser 换算。

## 4. 依赖数据源

无网络请求、无 URL、无持久化 key。仅：

- `R.drawable.svg_arrow_right`
- 颜色常量（见 `P8.a` 调色板，下表）
- `BaseActivity.c`（int，Compose 的 `changed` 位宽常量，`SettingActivity.e` 就是它的拷贝，`<clinit>` @0x0008-0x000a）

## 5. `P8.a` 调色板（本页复用，顺带定死）

`P8.a.<clinit>`（code_units=298）逐项赋值；字段→getter 映射由各 getter 的 `sget-wide` 得到：

| getter | 字段 | 值 | 用途 |
|---|---|---|---|
| `s()` | h | `#FFFFFFFF` | 行/分隔线背景 |
| `m()` | n | `#FF23272B` | 主文字 |
| `q()` | o | `#FF2D52AC` | 主蓝 |
| `r()` | p | `#662D52AC` | 主蓝 40% |
| `n()` | s | `#FF9B9B9B` | 次级灰 |
| `f()` | t | `#FFF1F1F1` | 浅灰底 |
| `o()` | r | `#FF1C2532` | 深色底 |
| `v()` | q | `#FFFFFFFF` | 白 |
| `p()` | g | `#FF1C45A5` | 深蓝 |
| `t()` | j | `#5CFFFFFF` | 白 36% |
| `u()` | i | `#E6FFFFFF` | 白 90% |
| `b()` | k | `#FF000000` | 黑 |
| `d()` | l | `#CC000000` | 黑 80% |
| `c()` | m | `#4D000000` | 黑 30% |
| `a()` | v | `TextStyle(16sp, Medium)` | 文本样式 |
| `e()` | u | `CardColors` 全白 | 卡片 |

另有 M3 默认色（`a#D0BCFF`、`b#CCC2DC`、`c#EFB8C8`、`d#6650A4`、`e#625B71`、`f#7D5260`）一并取出，供主题对齐。

## 6. 截图

未取（本页不可达，无法在真机打开）。可用近似参考：`E:\APPDetector\lc-gap-evidence\nav\30-hamburger.png`（抽屉），sha256 `d11f388e1e717cf95f7cd91b87fd2042a240d9b73939f4c9ddbaf5108e45607a`。

## 7. 对 LC 的改动建议（写进差距矩阵，不由逆向改代码）

1. 差距矩阵登记：`ui.pages.setting.SettingActivity` = **原版存在但不可达 → 有意省略（等价替代：抽屉「地图设置/账户设置」）**。
2. 若客户端要做「系统设置」聚合页，可复用本页样式：白底行、右侧 `svg_arrow_right`、15sp Medium `#23272B`、浅灰分隔线 `#F0F3F7`。
3. `P8.a` 调色板建议作为 Android 端主题常量源（与 `mechanics.json` 无关，属视觉常量，建议放 `app/android/.../res/values/colors.xml` 并在本文档留出处）。
