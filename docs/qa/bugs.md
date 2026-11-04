# LC 缺陷台账

> 维护：LC测试。每条必须可复现（命令/文件/输出），不写「我点了一遍」。
> 初始快照：2026-11-05 02:20–02:35（UTC+8），HEAD `9dda4e2`；
> 2026-11-05 02:40 轮 B 修复 `409ad5f` 落地后已回归关闭 BUG-001/002。
> 数值口径以 `app/MECHANICS.md` + `app/mechanics.json` 为准。

## 汇总

| ID | 等级 | 模块 | 症状 | 责任人 | 状态 |
|---|---|---|---|---|---|
| BUG-001 | P0 | 两端读数面板 | 人盘/天盘读数恒等于地盘（三盘永远同一个山） | LC客户端 | **已修复（409ad5f）** |
| BUG-002 | P2 | 桌面 UI | 60 龙标签环整体偏 4.5°，指针处标签与读数不一致 | LC客户端 | **已修复（409ad5f）** |
| BUG-003 | P1 | 两端文字 | 圈层文字仍是教材推导，6/14 类与 DEX `rings[].labels` 不等价 | LC客户端（轮 B2） | **新建（2026-11-05 03:00）** |
| OBS-001 | — | Android UI | 5 个盘面按钮只渲染出 3 种不同盘面 | LC客户端 / LC逆向（差距矩阵） | 待归口 |
| OBS-002 | — | 桌面截图工具 | `--screenshot` 中心读数硬编码「正向」 | LC客户端 | 备忘（当前不触发） |
| QA-001 | — | 测试资产 | `test_divergences.py:36` 两行被并成一行（IndentationError），套件整体红 | LC测试 | **已修复**（2026-11-05 02:41 拆行，74 PASS） |

> 2026-11-05 02:40 更新：BUG-001 / BUG-002 已随 `409ad5f` 修复，单测 5 条 xfail 转正、
> 套件 74 项全绿；回归证据见各条目「修复与回归」。
>
> 02:41 补记：`409ad5f` 里 `test_divergences.py` 有一处拆行丢失（`def ...(self):        expr = ...`），
> 套件曾整体 ImportError；LC测试 已就地拆行（QA-001），重跑 `python tests\numeric\run_all.py` = 74 PASS。

---

## BUG-001（P0）人盘/天盘读数恒等于地盘

**复现**

```powershell
cd E:\APPDetector\output
python tests\numeric\tools\dump_readings.py
```

实际输出（本轮）：

```
h      地盘   人盘(spec/实际)   天盘(spec/实际)   Java实际   Rust实际
  0.00   子 癸/子   子/子   子子 子子
  3.00   子 癸/子   子/子   子子 子子
 15.00   癸 丑/癸   癸/癸   癸癸 癸癸
 22.50   丑 丑/丑   癸/丑   丑丑 丑丑
352.50   子 子/子   壬/子   子子 子子
```

**期望**（知识库与绘制口径，见 `app/MECHANICS.md:175-178`）
- 三盘相差 ±7.5°：标签 m 的中心在 `m*15 + offset`（地盘 0、人盘 −7.5、天盘 +7.5），
  顶部固定指针读当前航向 → 人盘 `⌊(h+15)/15⌋%24`、天盘 `⌊h/15⌋%24`；
- 任意航向三盘恒有**两个不同**读数（人盘恒为天盘的下一格，地盘介于两者之间）；
- 0° 时：地盘 子、人盘 **癸**、天盘 子；15° 时：地盘 癸、人盘 **丑**、天盘 **癸**。

**实际**
- 两端都只用 `plate(h + 7.5)` 一种映射，三个值恒等（`parse` 出的表达式：`['h + 7.5', 'h - 7.5 + 15']`，
  第二个即 `h + 7.5`）。0° 时人盘显示 子（应为 癸）；15° 时人盘 癸（应为 丑）、天盘 癸（应为 癸→正确，但 22.5° 起错）。
- 圈层绘制本身是分开画的（人盘 −7.5、天盘 +7.5），所以「盘面标签位置」与「读数文字」自相矛盾；
  当前 `checks/round-17/screenshot.png` 里人盘 子/癸 跨在指针两侧，读数却写「子山」。

**证据**
- `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:235-236`
  `MOUNTAINS[plate(r.heading + 7.5f)]` / `MOUNTAINS[plate(r.heading - 7.5f + 15f)]`；
  `plate()` 定义在同文件 `:252-256`（`floor(norm(x)/15)%24`）。
- `app/src/main.rs` `reading_panel`：`hm = ((normalize_deg(rd.heading + 7.5) / 15.0).floor() as usize) % 24`
  （本轮快照 571 行）与 `tm = ... (rd.heading - 7.5 + 15.0) ...`（572 行），两式均化为 `(h+7.5)/15`。
- 绘制口径：`app/src/main.rs` `draw_plate` 调用处 `-7.5 / +7.5`；`CompassView.java:196-205` 同。
- 自动化：`python tests\numeric\run_all.py` → `expected failures=5`，其中
  `PlatesDivergenceTest` 的 4 条（Java/Rust × 人盘/天盘）全部 xfail。

**影响**：读数面板三盘、知识库「三盘相差 7.5°」、圈层绘制三者不一致；1:1 验收硬伤（P0）。

**修复与回归（409ad5f，2026-11-05）**
- 修复：`MainActivity.java` 三盘调用改为 `plate(r.heading + 15f)` / `plate(r.heading)`；
  `main.rs` 改成 `((rd.heading/15.0).floor() as usize + 1) % 24` / `((rd.heading/15.0).floor() as usize) % 24`；
  `software.rs` 同步。口径 = DEX `TYPE_8/9 offsetDegrees 0.0` + labels 表旋转（118357a）。
- 单测：`python tests\numeric\run_all.py` → `tests=74 failures=0 expected_failures=0 unexpected_successes=0`。
- 读数对照（`dump_readings.py`，spec/实际全一致）：
  ```
  h      地盘   人盘(spec/实际)   天盘(spec/实际)
    0.00   子 癸/癸   子/子
   15.00   癸 丑/丑   癸/癸
  352.50   子 子/子   壬/壬
  ```
- 截图：`checks\qa-roundb-01\desktop-screenshot.png`（1100×780，152048 B，
  SHA16=`DDA2691CDB57CBC7`）与 `checks\round-17\screenshot.png` 像素差 **909**：
  747 px 在 60 龙环（BUG-002 落位修正）、162 px 在右侧「人盘」行（0° 子→癸），
  无布局破坏（Pillow `ImageChops.difference` 统计）。

**修复建议（历史，已按此修）**
- 人盘：`⌊normalize(h+15)/15⌋ % 24`；天盘：`⌊normalize(h)/15⌋ % 24`（等价 DEX `TYPE_8`/`TYPE_9`
  的「offset 0 + 名单起点」口径，见 `tests/numeric/test_plates.py::DexListEquivalenceTest`）。
- Rust/Java 两端同改；`mechanics.json` 已有三盘 offset，可走 `tools/mechanics/gen-mechanics.ps1` 统一生成。

**修完怎么回归**：套件会由 xfail 变 **unexpected success（红）**；删掉
`test_divergences.py` 里对应 4 个 `@expectedFailure`，更新本表状态为「已修复」，重跑至全绿。

---

## BUG-002（P2）桌面 60 龙标签环偏 4.5°

**复现**

```powershell
cd E:\APPDetector\output
python tests\numeric\tools\dump_readings.py
```

输出第二张表（本轮）：

```
h=  0.00  读数=癸亥(k=59, 格中心 358.5°)  指针处标签=甲子(k=0, 画在 0.0°)
h=180.00  读数=癸巳(k=29, 格中心 178.5°)  指针处标签=甲午(k=30, 画在 180.0°)
```

**期望**：60 龙按 DEX `TYPE_7`（interval 6、offset 1.5）分格，第 k 格格中心
`= 1.5 + 3 + 6k = 4.5 + 6k`；标签应画在格中心，指针处标签 = 读数。
分金/72 龙就是这样画的（`main.rs` 标签角度 `m*15-6+3s` / `m*15-5+5s`，与读数格中心一致，
已由 `test_divergences.RingLabelAlignmentTest` 验证通过）。

**实际**：60 龙标签画在 `k*6`（0,6,12,…），整体少 4.5°；0° 时指针正对 甲子 标签，
读数却为 癸亥。除标签外，6° 刻度线也在 `k*6`，同样与格界（`1.5+6k`）差 1.5°。
约一半航向「指针处标签 ≠ 读数」。

**证据**
- `app/src/main.rs:380-381`：`for k in 0..60 { let deg = k as f32 * 6.0; ... }`；
- 同文件读数行 `:586`：`六十龙 {d60}`，`d60` 来自 `reading_for`（`(h-1.5)/6`）；
- 自动化：`Dragon60LabelDivergenceTest.test_label_under_pointer_matches_readout` → expected failure；
  相邻的 `RingLabelAlignmentTest`（分金/72 龙）为 ok，说明不是测试口径问题。
- 说明：`checks/qa-baseline-01/desktop-screenshot.png` 由 `software.rs` 渲染，未画 60 龙标签，
  因此该缺陷**无法从截图证明**，证据即代码 + 上表计算。

**影响**：桌面交互版视觉与读数自相矛盾；Android 不画 60 龙标签（只画刻度），暂不受影响。

**修复与回归（409ad5f，2026-11-05）**
- 修复：`main.rs` 刻度 `d = 1.5 + 6k`、标签 `deg = 1.5 + 6k + 3 = 4.5 + 6k`；`CompassView.java`
  的 6° 刻度同样改为 `DRAGON60_OFFSET + k*STEP`；**读数公式 `(h-1.5)/6` 未动**（符合逆向提醒）。
- 单测：`Dragon60LabelRegressionTest.test_label_under_pointer_matches_readout` 通过；
  全语料（0/1/1.5/3/4.5/7.5/180/352.5/359°）指针处标签 == 读数。
- `dump_readings.py` 第二张表全部 `OK`。

**修复建议（历史，已按此修）**：标签/刻度按格中心 `4.5 + 6k`、格界 `1.5 + 6k` 绘制，
`reading_for` 的 `d60` 不加任何补偿。

---

## BUG-003（P1）圈层文字仍是教材推导，未换 DEX 表

**复现**

```powershell
python -c "import json;d=json.load(open('app/mechanics.json',encoding='utf-8'));r={x['type']:x for x in d['rings']};print(r[7]['labels'][-5:])"
# ['乾亥 癸亥', '壬子 甲子', '壬子 丙子', '壬子 戊子', '壬子 庚子']  ← DEX TYPE_7
```

```powershell
Select-String -Path app\src\main.rs -Pattern 'JIAZI\[rd.dragon60\]|wangxiang\(borrow_branch'
# 60 龙读数 = JIAZI[59] = 癸亥（教材六十甲子），不是 DEX 表[59] = 壬子 庚子
```

**期望**（LC逆向 R2，`118357a`/`4c649af` + `rings[].labels` 780 条全量、逐条 code-unit 地址）：
- 60 龙文字形如「双山名+干支」：0°（k=59）应为 `壬子 庚子`；
- 72 龙是 12 组 ×（5 龙 + 1「正X」），120 分金带「X山」前缀，64 卦×3 / 八卦×3 / 正兼向均有原版表；
- 读数与圈层文字必须取同一张表，且与 `rings[].labels` 逐条一致。

**实际**：两端仍用 `wangxiang()` / `borrow_branch()` / `JIAZI[]` 推导；60 龙 0° 显示「癸亥」（教材），
桌面/Android 的说明文字原为「已与反编译 DEX 核对一致」——该声明已由轮 C `d435fe2` 改为
「教材式命名，待换原版表（轮 B2）」（D-008）。

**证据**
- `app/mechanics.json` `rings[].labels`（780 条 `confirmed_dex`）与 `.inspect\derived\ring_label_addrs.json`；
- 桌面 `main.rs` 60 龙标签 `JIAZI[k]`、72 龙/分金 `wangxiang(...)`；Android `CompassData.read()` 同；
- 轮 B 只修了几何（三盘/60 龙落位），文字未换表（LC逆向 02:55 复核结论）。

**影响**：14 类圈层里 6 类文字非 1:1；读数面板与圈层文字「看起来对、其实是教材值」。
**责任人/状态**：LC客户端（轮 B2），新建；换表后由 LC测试 按 `rings[].labels` 逐条断言。
**本轮不锁死**：numeric 套件只锁几何 `(interval, offset, index)` 与两端同源，未把教材文字
固化成 golden（避免把 BUG-003 永久洗成通过）。

## 观测（待归口，未计入缺陷）

### OBS-001 5 个盘面只渲染 3 种
`CompassView.java:18-22` 保存 `dialType`，但 `onDraw` 只在 `:156`（`dialType>0` 画分金/龙细环）
和 `:178`（`dialType==4` 加 64 卦环）用到它 ⇒ dial 1/2/3 渲染完全相同，dial 0 只是隐藏细环。
原版 `CommonCompassEnum` 5 个盘面各有 `resId`（`app/MECHANICS.md:74-81`），应各有差异。
建议由 LC逆向 在差距矩阵登记、LC客户端 排期；**真机确认待第二轮**（本轮真机让给 LC地图）。

### OBS-002 `--screenshot` 中心读数硬编码「正向」
`app/src/software.rs:287` 固定输出 `"正向 · {YINYANG}"`，未用 `rd.jian`。
当前 `software_screenshot()` 固定 `heading=0.0`（正向），故不产生错误结论；
若以后用它渲染非零航向会「假绿」。低优先级，改截图工具时一并处理。
