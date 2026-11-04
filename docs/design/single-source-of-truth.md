# 单一数值源方案与两端实现差异审计（LC 客户端 · M0 第一轮）

> 范围：只读审计。对象 `app\src\main.rs`、`app\src\software.rs`、`app\android\...\CompassData.java`、`CompassView.java`、`MainActivity.java`、`app\mechanics.json`、`app\MECHANICS.md`、`craft.json`。
> 本轮**未改任何代码**，未提交。所有结论可按下文第 8 节的命令复现。
> 证据基准：git `4770c20`（main 基线）。

---

## 0. 结论摘要

1. **`mechanics.json` 目前不是两端的数据源。** 5 个源文件里没有任何一处读取它——`grep mechanics` 只命中文档（`app\README.md:9`、`docs\TEAM_CHARTER.md:17,60,74`、`docs\TEAM_CHARTER.md` 以外无代码）与 `craft.json` 的检查记录。它今天唯一的作用是让 craft 的 `mechanics` 检查项通过（`craft.json` 每个 round 的 `"check": "mechanics"` + 19 条 claim）。
2. **数值靠"手抄一致"维持。** 24山/五行/阴阳/先后天卦/60甲子/纳音/借支/旺相在两端各存一份，本轮脚本逐元素比对：7 张表 + 八卦五行**当前完全一致**（`identical=True`）。但这种一致没有任何自动校验兜底，改一处漏一处就会静默分叉。
3. **两端共同继承了 3 个确定性缺陷**（不是"两端不一致"，而是"两端一致地错"）：
   - 人盘、天盘的读数**恒等于地盘**（7200 点扫频：`人盘!=地盘 0 次`、`天盘!=地盘 0 次`），但圈层是按 −7.5°/+7.5° 画的 → 天盘读数与自己画的圈层在 **50% 的方位上矛盾**；
   - 六十龙圈层文字按 `k*6°` 落位，读数按 `(h−1.5)/6` 取整 → **25% 方位**指针下的字与读数不同；
   - 5 种盘面在 Android 上只 gate 了「细节环」和「64卦环」两层，5 个盘面实际只有 3 种视觉；桌面端根本没有盘面概念。
4. **桌面质量红线的截图不覆盖真实 UI。** `--screenshot` 走 `software.rs` 的独立软件渲染器（`main.rs:713-716`），它与 egui 真实界面在三盘半径、分金/龙文字、读数行等 6 处不同；`main.rs` 里给 eframe 用的截图代码（`231-256`）在正常启动时永远不会执行（`main.rs:728` 传 `None`）。
5. **建议方案**：`mechanics.json` v2（14 圈层描述 + 5 盘面组合 + 8 张表 + 现有 19 值）→ 一个生成器 → 两端 **checked-in 生成文件**（保留字面量以不破坏 craft mechanics 检查）→ 生成器同时产出 golden 向量供两端数值回归。工作量 **≈4.25 人日**（含逆向补 14 组文字表 1 人日），分两轮落地。

---

## 1. 审计方法与证据

- 静态阅读 5 个源码文件 + 2 个档案 + `craft.json`（检查项定义与历史 claim）。
- 编写一次性 Python 3.13 脚本（`%TEMP%\lc_audit\*.py`，**未入库**）：
  - 用正则从 Rust/Java 源码抽取 7 张常量表与八卦五行映射，逐元素比对；
  - 按两端源码里的表达式**逐字重写**读数公式，做 0–360° 步长 0.05°（7200 点）扫频，统计"读数 vs 圈层落位"矛盾率；
  - 60 龙标签落位单独扫频。
- 免构建、免 adb，不触碰真机与工作区。

---

## 2. ① 两端实现逐项差异对照

### 2.1 数值与常量（重复清单）

| # | 内容 | 桌面（Rust） | Android（Java） | 状态 |
|---|---|---|---|---|
| 1 | 24 山顺序 | `app/src/main.rs:19-22` | `CompassData.java:13-16` | 两份，值相同 |
| 2 | 五行表（24） | `main.rs:25-28` | `CompassData.java:19-22` | 两份，值相同 |
| 3 | 阴阳表（24） | `main.rs:32-35` | `CompassData.java:25-28` | 两份，值相同 |
| 4 | 后天卦（24） | `main.rs:38-41` | `CompassData.java:31-34` | 两份，值相同 |
| 5 | 先天卦（24） | `main.rs:44-47` | `CompassData.java:37-40` | 两份，值相同 |
| 6 | 八卦五行 | `main.rs:50-53`（数据）+`597-599`（查询） | `CompassView.java:275-284`（switch） | 两份，语义相同 |
| 7 | 60 甲子 | `main.rs:56-63` | `CompassData.java:43-50` | 两份，值相同 |
| 8 | 纳音（60） | `main.rs:66-73` | `CompassData.java:53-60` | 两份，值相同 |
| 9 | 地支/阳干/阴干 | `main.rs:76-78` | `CompassData.java:62-64` | 两份，值相同 |
| 10 | 借支表（寄宫） | `main.rs:81-97` | `CompassData.java:91-105` | 两份，同构 |
| 11 | 旺相取名 | `main.rs:100-107` | `CompassData.java:107-114` | 两份，同构 |
| 12 | 纳音查询 | `main.rs:109-111` | `CompassData.java:116-121` | 两份，同构 |
| 13 | 角度归一化 | `main.rs:144-150` | `CompassData.java:85-89` + `CompassView.java:83-87` + `MainActivity.java:258-262` | **4 份** |
| 14 | 读数结构 | `main.rs:128-171` | `CompassData.java:124-167` | 两份，同构 |

> 数值重复规模（正则粗计，含少量无关字面量）：`15` 31 处、`7.5` 26 处、`3` 26 处、`5` 37 处、`6` 16 处、`5.625` 9 处，分布在 5 个文件。

### 2.2 读数公式（逐项）

| # | 项 | 桌面 | Android | 是否一致 | 备注 |
|---|---|---|---|---|---|
| 15 | 地盘 24 山 index | `main.rs:154` `floor((h+7.5)%360/15)%24` | `CompassData.java:143` | 一致 | 与 TYPE_4（offset 7.5）等价 |
| 16 | 山内偏移 | `main.rs:156` | `CompassData.java:146` | 一致 | −7.5..7.5 |
| 17 | 分金槽位 | `main.rs:157-158` | `CompassData.java:147-148` | 一致 | 3°×5，`.min(4)` |
| 18 | 七十二龙槽位 | `main.rs:159` | `CompassData.java:149` | 一致 | 5°×3，`.min(2)` |
| 19 | 六十龙 index | `main.rs:160` | `CompassData.java:150` | 一致 | offset **1.5 只存在于代码**，`mechanics.json` 无此项 |
| 20 | 坐山 | `main.rs:161` | `CompassData.java:151` | 一致 | (m+12)%24 |
| 21 | 正/兼向判定 | `main.rs:576-584`（\|off\|×2≤9） | `CompassData.java:160-165` | 一致 | 阈值 9 两端硬编码；`mechanics.json` 的「兼向 3°」语义歧义（每侧 3° vs 合计 3°） |
| 22 | **人盘** | `main.rs:549` `floor((h+7.5)/15)` | `MainActivity.java:235` `plate(h+7.5)` | 一致，**但恒等于地盘** | 见 2.3 |
| 23 | **天盘** | `main.rs:550` `floor((h−7.5+15)/15)` | `MainActivity.java:236` `plate(h−7.5+15)` | 一致，**但恒等于地盘** | 同上；两式相等是代数恒等，不是巧合 |
| 24 | 两盘数值口径 | 无 DEX 依据，仅按 KB「逆 7.5 / 顺 7.5」 | 同左 | — | TYPE_8/9 的 `offsetDegrees` 均为 0.0，差异本应在文字表顺序里，档案未记录 → **未确认** |

**扫频实证**（0–360°，7200 点，复现命令见 §8）：

```
sweep 7200 headings: 人盘!=地盘 0; 天盘!=地盘 0; 天盘读数!=天盘圈层 3600
h=   0.0 地盘=子 人盘=子 天盘读数=子 天盘圈层应读=子
h=   7.4 地盘=子 人盘=子 天盘读数=子 天盘圈层应读=子
h=  10.0 地盘=癸 人盘=癸 天盘读数=癸 天盘圈层应读=子
h= 352.5 地盘=子 人盘=子 天盘读数=子 天盘圈层应读=壬
```

即：三行读数**永远相同**；天盘圈层（`CompassView.java:197,202` 画在 `m*15−7.5` / `m*15+7.5`）与天盘读数在 50% 方位上指向不同山。人盘同理（码值 `floor((h+7.5)/15)`，圈层要求 `floor((h+15)/15)`，扫频 50% 不一致）。

> 这是**两端共同的读数缺陷**，不是"两端不一致"。修复需要 LC逆向 先定 TYPE_8/9 的文字顺序（见 §4.7 依赖），不能客户端自行拍板。

### 2.3 圈层绘制（半径/文字/开关）

| # | 圈层 | 桌面 `main.rs` | 桌面截图 `software.rs` | Android `CompassView` | 差异 |
|---|---|---|---|---|---|
| 25 | 度数环 | `296-313`（r=1.0） | `190-209` | `138-153` | 一致 |
| 26 | 百二十分金 刻度+字 | `315-331`，字半径 0.835 | `211-216` **只画刻度** | `156-162` **只画刻度** | 文字只有桌面真 UI 有 |
| 27 | 七十二龙 刻度+字 | `333-349`，字半径 0.80 | `217-221` 只画刻度 | `163-168` 只画刻度 | 同上 |
| 28 | 六十龙 刻度+字 | `350-364`，字在 `k*6°` | `222-226` 只画刻度 | `169-174` 只画刻度 | 文字落位偏 4.5°（见 2.4） |
| 29 | 64 卦 | `366-372`，**常显、无开关** | `228-234` | `177-186`，**仅 dial==4** | 三处策略不同；均无卦名文字 |
| 30 | 地盘 24 山 | `375-377`，半径 0.715 | `237-242`，0.715 | `189-194`，0.715 | 一致 |
| 31 | 人盘 24 山 | `379-381`，**r=0.58**，字 12.5 | `243-247`，**r=0.52**，字 13 | `196-200`，**r=0.52**，字 13 | 桌面真 UI 与另两者不同 |
| 32 | 天盘 24 山 | `383-385`，**r=0.44**，字 12.5 | `248-252`，**r=0.34**，字 13 | `201-205`，**r=0.34**，字 13 | 桌面真 UI 与另两者不同 |
| 33 | 八卦环 | `389-412`，r=0.30，8 字+五行，先天/后天可切 | `253-263`，r=0.18，先天/后天固定后天 | `207-214`，r=0.18，**只有后天、无五行** | 三种实现互不相同 |
| 34 | 十字线/向/坐 | `415-448`，有「向」「坐」字 | `265-271`，无「向」「坐」字 | `216-225`，无字 | 桌面截图缺标签 |
| 35 | 圆心读数 | `450-472`：度数/山+五行/正兼向+阴阳 | `273-292`：度数/山+五行/「正向」+阴阳 | `227-239`：度数/山+五行/正兼向+阴阳 | 桌面截图把正兼向写死「正向」 |

**结论：同一个桌面仓库里有两套渲染器，且与 Android 也不一致**（半径 0.58/0.44/0.30 vs 0.52/0.34/0.18）。craft 的 `app-screenshot` 检查只跑 `software.rs` 这套（证据：`checks/round-17/screenshot.png` 里没有顶栏、没有左侧知识库面板——那两样只在 egui 里）。

### 2.4 六十龙文字落位（可量化缺陷）

读数：`index = floor((h−1.5)%360/6)`，即第 k 格覆盖 `[1.5+6k, 7.5+6k)`，边界应在 `1.5+6k`、中心 `4.5+6k`。
绘制：`main.rs:358-363` 的文字仍在 `k*6°`（偏 4.5°）；三端的刻度（`main.rs:352-356`、`software.rs:222-226`、`CompassView.java:169-174`）也在 `k*6°`（偏 1.5°）。
扫频结果：`readout != drawn label for 1800/7200 = 25% of headings`（如 h=7.4° 读数 `甲子`，指针下最近的字是 `乙丑`）。

### 2.5 交互与功能

| # | 功能 | 桌面 | Android | 状态 |
|---|---|---|---|---|
| 36 | 5 盘面切换 | **无此概念**，只有 7 个圈层 checkbox（`main.rs:668-674`） | `CompassData.java:190-192` 5 个名字 + `CompassView.java:18/20-23`；`156` 控制细节环、`178` 控制 64 卦 | **空壳**：dial 1/2/3 画面完全相同，5 个盘面只有 3 种视觉；读数面板不受盘面影响 |
| 37 | 传感器 | 无（仅拖动，`main.rs:267-272`） | `MainActivity.java:281-289` rotation vector，`SensorManager.SENSOR_DELAY_UI` | 桌面为演示载体，可接受 |
| 38 | 拖动 | `-drag.x * 0.5`（`main.rs:270`） | 极角差（`CompassView.java:97-108`） | 手感不同，均可 |
| 39 | 复位 | 置 0（`main.rs:532`） | `reset()` 置 0 且恢复跟随（`CompassView.java:69-74`） | Android 复位后立刻被下一帧传感器覆盖，语义存疑 |
| 40 | 坐向 180° | `main.rs:535` | `MainActivity.java:121-123` | 一致 |
| 41 | 截图 | egui 截图路径在正常启动时**死代码**（`231-256`，`main.rs:728` 传 `None`） | `captureMap()`（`163-179`）`mapView.draw()` | **抓错对象**：`mapView` 是地图 tab 的第二个 WebView（`47`、`181-183`），罗盘盖在地图 tab 的**另一个** WebView 上（`77`，引用未保存），且截图时该 tab 通常 `GONE` → 抓到空白/无罗盘 |
| 42 | 坐向推送到地图 | — | `updateReading()`（`244-249`）只推给 `mapView`，罗盘页那个 WebView 收不到 | 罗盘页的地图不显示坐向 |
| 43 | 知识库 | 侧栏 8 条 `collapsing`（`498-519`） | `ListView` + `AlertDialog`（`185-219`），额外 2 行（5 盘面/14 圈层） | 原版是 `CompassIntroduceActivity` 列表→详情页；两端都是降级实现 |
| 44 | 字体 | 读系统路径 `simhei/Deng/msyh/simsun/Noto`（`609-632`），失败静默 | 系统默认字体 | 无内置字体，换机/换系统会 tofu |
| 45 | 显示精度 | `{:.2f}`（Rust 半偶舍入） | `%.2f`（Java HALF_UP） | 边界值（x.xx5）字符串可能差 0.01 |

### 2.6 "只有一端有"的清单

- 桌面有、Android 无：先天八卦环、八卦五行文字、分金/72龙/60龙**文字**、64 卦常显、正兼向带角度、阴阳行、先天卦行、「向/坐」字标。
- Android 有、桌面无：5 盘面按钮、传感器、地图/坐向推送、知识库额外 2 行（5 盘面、14 圈层）、`READ_TYPES` 清单。
- `CompassData.HEXAGRAM64_DEG`（`204`）只在 `CompassView` 用一次；桌面把 `5.625` 直接写在 `main.rs:368`、`software.rs:230`。

---

## 3. ② `mechanics.json` → 两端常量的单一数值源方案

### 3.1 目标与非目标

**目标**：任何读数/圈层数值只在 `app/mechanics.json` 出现一次；两端常量由生成器产出；手改生成物或漏改 JSON 都会在下一条命令里失败。
**非目标**：像素半径、配色、字体字号不进 JSON（纯 UI，craft 不查，且原版 Compose 常量未还原）；`map.html` 与地图桥不进本方案（LC地图 所有）。

### 3.2 数据模型（v2，向后兼容）

保留现有 `values[]` 的 `name/value/unit/source`（从 `craft.json` 的 claim 标题推断它按 `name`+`value` 逐条校验"是否被代码使用"；为稳妥，v2 **不改名、不删项**，只新增 `id` 与 `rings`/`dials`/`tables` 三段）：

```jsonc
{
  "version": 2,
  "values": [ { "id": "mountains_per_circle", "name": "mountains per circle", "value": 24, ... } ],
  "rings": [
    { "id": "TYPE_4", "text": "地盘正针二十四山", "interval": 15.0, "offset": 7.5,
      "labels": ["子","癸",...], "vip": false, "source": "CompassReadTypeEnum.<clinit>" },
    // ... TYPE_1..TYPE_14 共 14 条，含 TYPE_7 的 offset 1.5、TYPE_1..3 的 22.5、TYPE_11..13 的 5.625
  ],
  "dials": [
    { "id": "COMPASS_0", "text": "二十四山(阴)", "rings": ["TYPE_4","TYPE_6",...], "vip": false },
    // ... COMPASS_0..4
  ],
  "tables": {
    "mountains": [...24], "wuxing": [...24], "yinyang": [...24],
    "houtian": [...24], "xiantian": [...24],
    "jiazi": [...60], "nayin": [...60],
    "gua_wuxing": { "坎": "水", ... },
    "borrow_branch": { "子": "子", "癸": "子", ... },   // 或保留算法 + 参数
    "fenjin_120": ["癸山 甲子", ...120], "dragon_72": [...72], "dragon_60": [...60]
  }
}
```

`rings[].labels` 是**唯一需要 LC逆向 补的数据**（TYPE_1..14 各自的文字表）；现有 `borrow_branch + wangxiang` 的算法可以作为过渡期 fallback，但要在 `source` 里写清"算法等价替代"，不能算已验证。

### 3.3 生成器

- 位置：`scripts/gen-mechanics.ps1`（PowerShell 7，无第三方依赖；与 LC服务端 的 `scripts\*.ps1` 同目录，需总监分配归属；若不愿动 `scripts\`，退路是新建 `tools\`，同样需批准）。
- 输入：`app/mechanics.json`；输出 **三个文件，全部入库**：
  1. `app/src/mechanics_gen.rs` —— `pub const MOUNTAINS: [&str; 24]`、`WUXING`、`YINYANG`、`HOUTIAN_GUA`、`XIANTIAN_GUA`、`JIAZI`、`NAYIN`、`GUA_WUXING`、`pub const RINGS: [RingDef; 14]`（`struct RingDef { id: &'static str, text: &'static str, interval: f32, offset: f32, labels: &'static [&'static str] }`）。
  2. `app/android/app/src/main/java/com/luopan/fengshui/MechanicsGen.java` —— 同名常量 + `RingDef[]` + `DialDef[]`；`CompassData` 改为薄封装（保留 `MOUNTAINS` 等 public 字段的静态转发，避免动 `CompassView`/`MainActivity` 的调用面）。
  3. `tests/numeric/golden_readings.csv` —— 144 行（2.5° 步长，避开 .x5 边界）golden 向量，列：`heading,mountain,offset,fenjin_slot,dragon72_slot,dragon60,sitting,jian,plate_human,plate_heaven`。
- **为什么生成物入库而不是 build.rs**：craft 的 `mechanics` 检查是"这个数值是否被代码使用"的源码级检查（`craft.json` 里每个 value 一条 claim）。`build.rs` 在编译期读 JSON 会让字面量不出现在源码里，检查可能失守。入库 + 头部注释是风险最低的路径。

### 3.4 防手改

1. 生成文件首行：`// GENERATED FROM app/mechanics.json — DO NOT EDIT. json_sha256=<hash>`。
2. `gen-mechanics.ps1 -Check`：重新生成到临时目录并与工作区比对，不一致即非零退出；由 `scripts/smoke.ps1`（LC服务端 的串测脚本）调用，也进 CI。
3. `.gitattributes` 追加 `linguist-generated=true`，diff 时一眼可辨。
4. `scripts/verify-mechanics.ps1`：模拟 craft 的 mechanics 检查——对 `values[]` 每一条，断言其 `value` 字面量出现在 `app/src/*.rs`（与 craft 口径一致），保证 v2 新增项不会让三关掉红。
5. 代码评审约定：任何 PR 改到 `mechanics_gen.rs`/`MechanicsGen.java` 一律打回，只允许改 JSON 后重生成。

### 3.5 回归

- **跨端一致性（新增，当前完全缺）**：`tests/numeric/golden_readings.csv` 由生成器用独立实现算出；Rust 侧 `app/tests/mechanics.rs`、Android 侧 `src/test/java/.../MechanicsTest.java` 各自读同一 CSV 比对，离散字段必须全等、`offset` 容差 1e-4。这条测试能在 1 秒内抓住"改了一端忘另一端"。
- **craft 三关影响**：
  - `build`：不受影响（生成物是普通 `const`，无新依赖）。
  - `app-screenshot`：`software.rs` 改用生成常量后再跑 `--screenshot`，**期望 PNG 与 `checks/round-17/screenshot.png` 逐字节相同**；不同即说明生成器改变了数值，必须解释。这是本方案最强的回归证据。
  - `mechanics`：`values[]` 字段不改名不删项 + 生成物保留字面量 → 19 条 claim 不变；新增项（60龙 offset 1.5、5 盘面 ring 组合）要靠 `verify-mechanics.ps1` 自证"出现在源码里"。
- **两端 UI 对照**：桌面真 UI 与 `software.rs` 的差异（§2.3）建议本轮之后单独派单统一，否则"桌面截图 = 桌面实现"这个前提一直是假的。

### 3.6 迁移顺序（建议两轮）

1. **轮 A（生成器 + 常量）**：JSON v2 骨架（先只搬现有 19 值 + 8 张表，`rings` 用现有参数生成算法）→ 生成器 → 两端改用生成文件 → 用 `--screenshot` 字节比对 + Android 真机冒烟证明"行为零变化"。**这一轮不修任何缺陷。**
2. **轮 B（圈层表 + 回归）**：LC逆向 交 14 组 `labels` 与 5 盘面 ring 组合 → 生成 `RINGS/DIALS` → golden 向量 + 两端测试 → 再修 §2 的 P0 缺陷（三盘、5 盘面、60龙落位），每修一项先跑 golden 看差异是否只在预期方位。

### 3.7 工作量估计

| 项 | 人日 | 依赖 |
|---|---|---|
| JSON v2 schema + 迁移脚本 + 现有 19 值/表搬入 | 0.5 | 逆向确认字段命名 |
| 生成器（PowerShell，含 `-Check`、hash 头、CSV 输出） | 1.0 | — |
| Rust 侧接入（`main.rs`/`software.rs` 去重） | 0.5 | 轮 A |
| Java 侧接入（`CompassData`/`CompassView` 去重） | 0.5 | 轮 A |
| golden CSV + Rust/Java 测试 + 挂进 smoke | 1.0 | 轮 A |
| 逆向补 14 组 labels + 5 盘面组合 | 1.0 | LC逆向 |
| 三盘/60龙/5盘面修复（轮 B） | 0.75 | 上两项 |
| **合计** | **≈4.25** | |

---

## 4. ③ 1:1 缺口清单（按影响排序）

| P | 缺口 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| P0-1 | 人盘/天盘读数恒等于地盘（两端） | `main.rs:549-550`；`MainActivity.java:235-236`；扫频 7200 点 0 例外；`checks/round-17/screenshot.png` 三行都是「子山」 | 核心读数错，且与自绘圈层矛盾 | 等逆向定 TYPE_8/9 文字顺序；先修"天盘 ≠ 地盘"与圈层自洽 |
| P0-2 | 5 盘面是空壳 | `CompassView.java:156,178`；`CompassData.java:190-192` | 原版核心功能（5 种盘面）名义存在、实际 3 种视觉 | 进 JSON `dials[].rings`，按盘面组合圈层 |
| P0-3 | 截图抓错 WebView | `MainActivity.java:47,77,163-179` | 「截图」功能拿不到罗盘+地图合成图 | 改为罗盘页那个 WebView 的引用；或参考原版 `getScreenshot(..., "compass", ...)` |
| P0-4 | 知识库无独立详情页 | 桌面 `498-519`；Android `185-219` | 原版 `CompassIntroduceActivity` 列表→详情，两端都是降级 | 产品/逆向定页面字段后做 Activity + 桌面详情区 |
| P0-5 | 14 圈层只落 8 类 | `CompassData.java:195-201` 列出 14 种，`CompassView` 只画 6 种刻度 + 三盘 + 八卦 | 缺洛书数、先天/后天卦位、天盘百二十分金、64卦内外卦、六十甲配64卦五行、正兼向指标 | 进 JSON `rings`，逐圈层补绘制 |
| P1-1 | 六十龙文字偏 4.5° | `main.rs:358-363`；扫频 25% | 指针下的字与读数不符 | 落位改 `k*6+4.5`（或按 JSON 中心） |
| P1-2 | 64 卦无文字、显示策略三样 | `main.rs:366-372`、`CompassView.java:177-186` | 圈层不完整 | 补 64 卦名，统一开关 |
| P1-3 | 两端读数面板字段不一致 | `main.rs:547-565` vs `MainActivity.java:230-241`（Android 缺阴阳、先天卦） | "两端读数完全一致"目标不成立 | 字段集进 JSON 或统一模板 |
| P1-4 | 桌面真 UI 与截图渲染器不一致 | `main.rs:379-385` vs `software.rs:243-252`（半径/文字） | 质量红线截图不覆盖真实 UI | 让 `software.rs` 与 egui 共用同一套绘制参数，或改截图路径 |
| P1-5 | Android 罗盘页地图收不到坐向 | `MainActivity.java:244-249` | 坐向指针在罗盘页不同步 | 保存罗盘页 WebView 引用，双推 |
| P2-1 | 字体依赖系统路径 | `main.rs:609-632`、`software.rs:146-163` | 换机 tofu | 内置一份 CJK 子集（需授权确认） |
| P2-2 | 传感器未做屏幕旋转重映射；60Hz `evaluateJavascript` | `MainActivity.java:281-289,244-249` | 横屏方位可能偏；每帧 JS 调用 | `remapCoordinateSystem` + 节流 |
| P2-3 | 舍入模式差异 | `main.rs:455` vs `CompassData` 消费方 `MainActivity.java:231` | 边界 0.01° 显示差 | 统一用同一种格式化策略 |

---

## 5. 未确认项（不臆造）

1. **TYPE_8（人盘）/ TYPE_9（天盘）的 `text` 列表顺序**。`MECHANICS.md:97-98` 只记了 `offsetDegrees = 0.0`，两盘差异必然在列表旋转里，档案未记录 → 人盘/天盘的正确读数**未确认**，修复前必须由 LC逆向 从 `getTextList()` 分支取证。
2. 原版 Compose 圈层的**逐像素半径与配色**（`CompassToolsKt` 混淆链）→ 未确认，当前按截图逼近。
3. 原版读数面板的**字段集合与顺序** → 未确认，只能确认 `getFaceText/getBackText` 公式。
4. 原版截图合成方式：只知调用 `MapWebApiKt.getScreenshot(..., "compass", ...)`（`MECHANICS.md:124`），具体抓取对象与时机未确认。
5. 原版 **5 盘面各自的圈层组合** → 未确认（只知道 5 个枚举与 14 种读数类型）。
6. 原版「复位」语义（清零点 vs 恢复传感器）→ 未确认。

---

## 6. 与 team 既定口径的一致性

- 章程 §5.3 要求"两端常量与 `mechanics.json` 同源"：**当前未达成**，本方案是达成路径。
- 章程 §7.4 明确桌面版是"演示/自动验收载体"：因此 P1-4（桌面两套渲染器）优先级可按"验收证据是否可信"来看，而不是按"桌面产品完整度"。
- 差距矩阵归 LC逆向；本文件的 P0/P1 只是客户端视角的候选，最终以 `docs/gap-matrix.md` 为准。

---

## 7. 本轮交付边界

- 只新增本文件；未改 `app/**`、未切分支、未提交（共享工作区，避免干扰其余 5 人）。
- 临时审计脚本在 `%TEMP%\lc_audit\`，未入库。

---

## 8. 复现

```powershell
# 1) 数值源是否被读取（期望 0 命中 app/）
Select-String -Path app\src\*.rs,app\android\app\src\main\java\com\luopan\fengshui\*.java -Pattern 'mechanics'

# 2) 两端常量逐元素比对 + 三盘扫频（脚本在 %TEMP%，按需重建）
python %TEMP%\lc_audit\cmp_consts.py
python %TEMP%\lc_audit\sweep.py

# 3) 桌面截图证据（当前 checks/round-17/screenshot.png 即 software.rs 渲染，无顶栏/无知识库侧栏）
cd app; cargo build; .\target\debug\luopan.exe --screenshot ..\checks\round-17\screenshot.png

# 4) 真机现状（本轮未跑，等总监安排）
adb -s L2E0222531014833 shell am start -n com.luopan.fengshui/.MainActivity
```

---

## 9. 轮 A 落地记录（2026-11-05，实现与本文 §3.3 的一处偏离）

**偏离**：生成物没有落到新的 `mechanics_gen.rs` / `MechanicsGen.java`，而是在 `app/src/main.rs` 与 `CompassData.java` 内部用 `// >>> BEGIN/END GENERATED` 标记块原地生成。

**原因（可验证，不是偏好）**：`tests/numeric/source_parse.py` 与 `test_mechanics.py` 直接按正则从 `main.rs` / `CompassData.java` 的**文件正文**里抓常量表（`rust_array` / `java_array`）、并逐句求值 `reading_for` 与 `hm/tm` 的表达式。改成独立文件会让 LC测试 正在写的 74 项数值测试同时变红，且必须跨人改 `tests/numeric/`。标记块保住了同一套 19 项红线与 74 项测试，`-Check` 仍然能拦截手改。

**保留的字面量（轮 B 收口）**：
- Rust `reading_for` / `reading_panel` 的 `hm/tm` 仍写字面量（被 `source_parse.py` 解析），由 `main.rs` 末尾的 `mechanics_pin` 单测把它们钉死在生成常量上；
- Java `MainActivity.plate()` 仍写 `15f`（被 `java_plate_exprs` 求值），同样留注释；
- `CompassData.Reading` 已全部走生成常量（Java 侧由 CompassProbe **编译执行真代码**验证，不受文本解析限制）。

**验收证据**（本轮）：
- `cargo build` 0 error，`cargo test` 3/3 通过（`mechanics_pin`）；
- `luopan.exe --screenshot` 与 `checks/round-17/screenshot.png` **字节一致**（151480 B，SHA256 `e2de3ade…2f93`）；
- `python -m unittest discover -s tests\numeric`：74 项 OK（5 项已知缺陷 xfail）；
- `gradle assembleDebug` BUILD SUCCESSFUL；真机安装启动无 FATAL；
- 新增 `--screenshot-ui`：接上原 `handle_screenshot` 死代码，输出真 egui UI 图（`checks/round-a/screenshot-ui.png`，1650×1170 = 1100×780 @1.5× DPI）。

**轮 B 待办（已具备输入）**：LC逆向 已把 14 组 `rings[].labels`（780 条，`labels_status: confirmed_dex`）写进 `mechanics.json`；轮 B 由生成器产出 labels + `RING_*` 描述，改读数公式为人盘 `(floor(h/15)+1)%24`、天盘 `floor(h/15)%24`（offset 0），并让生成器同时输出 golden 向量。
