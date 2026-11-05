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
| BUG-003 | P1 | 两端文字 | 圈层文字仍是教材推导，6/14 类与 DEX `rings[].labels` 不等价 | LC客户端（轮 B2） | **已修复/关闭（B2 `662c006`+`0bd22f8`，AT-B2 于 2026-11-05 通过）** |
| BUG-004 | P1 | Android P0-1 | 总开关关闭后只隐藏 UI，偏角仍在算（注入 94 显示 99.00°） | LC客户端 | **已修复/关闭（`20883cd`，RT-010 真机复验 PASS）** |
| BUG-005 | P2 | Android P0-2 | 一键留档的分享图里看不到审图号角注：`#srcInfo` 落在底部读数面板带内（设备像素 y2293–2349），被面板整体盖住 | LC客户端 | **已修复/关闭（`111c4fb` 截图合成，2026-11-05 05:46 复验 18/0/0）** |
| K-01 | P1（高） | Android 主界面结构 | 底部三 Tab（罗盘/地图/知识）；原版是单页全屏地图 + 罗盘同页浮层，无底部导航 | LC审客户端（主刀）/ LC审地图（配合） | **新建（2026-11-05 11:35）** |
| OBS-001 | — | Android UI | 5 个盘面按钮只渲染出 3 种不同盘面 | LC客户端 / LC逆向（差距矩阵） | 待归口 |
| OBS-002 | — | 桌面截图工具 | `--screenshot` 中心读数硬编码「正向」 | LC客户端 | 备忘（当前不触发） |
| OBS-003 | — | Android P0-2 | 留档文件名是「坐向酉向卯_…」，PRD 写「坐向X**山**向Y**山**_…」少了「山」字 | LC产品（定名） | 待口径（不影响文件身份） |
| QA-001 | — | 测试资产 | `test_divergences.py:36` 两行被并成一行（IndentationError），套件整体红 | LC测试 | **已修复**（2026-11-05 02:41 拆行，74 PASS） |

> 2026-11-05 02:40 更新：BUG-001 / BUG-002 已随 `409ad5f` 修复，单测 5 条 xfail 转正、
> 套件 74 项全绿；回归证据见各条目「修复与回归」。
>
> 02:41 补记：`409ad5f` 里 `test_divergences.py` 有一处拆行丢失（`def ...(self):        expr = ...`），
> 套件曾整体 ImportError；LC测试 已就地拆行（QA-001），重跑 `python tests\numeric\run_all.py` = 74 PASS。
>
> 03:55 更新：P0-1 RT-010 暴露的「总开关关掉仍算偏角」记入 **BUG-004**，已随 `20883cd` 修复并复验
> （raw 94 → 94.00°，`sensor_status` 节点 0）；BUG-003 已随 B2 `662c006`/`0bd22f8` 关闭。

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
**责任人/状态**：LC客户端（轮 B2），**已修复并关闭**。

**修复与回归（2026-11-05，AT-B2）**
- 修复：生成器把 14 个 `RingDef{id,text,interval,offset,labels}` 同时生成进两端
  （Rust `main.rs::RINGS` / Java `CompassData.RINGS`），780 条全部来自 `mechanics.json`；
  文字落位/读数 `ring_text/ringText` → `labels[i]`，`i=⌊((h−offset+360)%360)/interval⌋`，
  当前格中心 `offset+(i+0.5)×interval`（高亮用）。提交 `662c006`（换表）、`0bd22f8`（高亮+LCRING）。
- 单测（`test_b2_golden`）：Rust/Java `RINGS` 与 `rings[].labels` **780 条逐条相等**、四向量
  （60 龙 0°=壬子 庚子、170°=丙午 甲午、72 龙 0°=戊子、120 分金 0°=甲山 戊子）、
  `ring_index`/`ring_text` 分层语义、`ring_center` 公式、读数面板确用 `ring_text(1/2/5/6/7/14)`。
- 真机 AT-B2（`develop f16b755` 构建）：
  - 桌面 `--dump-rings` 8 方位 × 14 环 = **112 行，0 不符**；
  - 真机拖拽到 8 个实际航向，`LCRING` **616 行，0 不符**（i/center/text 全对）；
  - 两端当前格高亮可辨；`FATAL 0 / ANR 0`。
  - 证据：`checks/qa-atb2/`（含 `compare-desktop.txt`、`compare-device.txt`、高亮截图）。
- 说明：整圈文字因 r=232、120 格 3° 弧长 10–12px 无法不重叠，按总监裁决改为
  「只画刻度 + 当前格高亮 + 文字在读数面板」，整幅位图盘面排轮 E（等价替代，未达 1:1）。

**修复前记录（历史）**
- numeric 套件当时只锁几何 `(interval, offset, index)` 与两端同源，未把教材文字固化成 golden；
  B2 前用 1 条 xfail 锁四向量（`0b5fa0e`），换表后转正删除（`0615cd8`）。

## BUG-004（P1）P0-1 总开关关闭后仍叠加偏角（RT-010）

**复现**（真机，P0-1 `f2cf102` 版本）
1. 长按读数文本 7 次打开隐藏开关入口，关闭 `p0_1_enabled`（UI 上状态条/口径行消失）；
2. 注入 heading 94（`am broadcast -a com.luopan.fengshui.DEBUG --ef heading 94`）；
3. 读数为 **99.00°**（真北口径），而不是基线的 **94.00°**（地盘卯）。

**期望**：总开关全关 = 完全回到 P0-1 之前的基线路径，`H = normalize(raw)`，
不叠加偏角、不弹校准、状态条不存在（AT-P0-1 RT-010「全关回基线」）。

**实际**：开关只隐藏了 UI，航向仍走 `applyDeclination(raw, declination, …)`，
所以 raw 94 被 +5° 偏角显示成 99.00°；等于「看起来关了，实际还在改读数」。

**证据**
- 修复前 dump：`checks\at-p0-1\ui-60-rt010-off.xml`（节点隐藏但读数 99.00°）
- 修复后 dump：`checks\at-p0-1\ui-61-rt010-off-fixed.xml`（读数 **94.00°**、`sensor_status` 节点 0）
- 客户端 runbook：`checks\at-p0-1\RUNBOOK.md`「RT-010 · 总开关全关」

**修复**（LC客户端 `20883cd`）
- `p0_1_enabled ? SensorMath.applyDeclination(raw, decl, trueNorth) : SensorMath.normalize(raw)`；
- 新增 `lastRaw` 记录原始方位，`applyTrustState` 在开关切换时重新推一次 heading（注入态也立即回基线）；
- 未改 `SensorMath` 本身，数值公式不动（tilt 仍 `acos(cos p·cos r)=14.106`）。

**回归**：修复后 raw 94 → **94.00°**（地盘卯），状态条/口径行消失，RT-010 **PASS**；
RT-004（真北↔磁北连切 10 次 94.00↔99.00 无漂移）保持 PASS。
**责任人/状态**：LC客户端，**已修复并关闭**（2026-11-05）。
**备注**：这是「开关语义」缺陷，不在数值层；数值层（三盘/圈层/偏角公式）另有 `tests/numeric` 覆盖。

## K-01（高）底部三 Tab 与原版首页结构不符（罗盘应为地图同页浮层）

**来源**：真机取证（技术总监 2026-11-05），证据提交 `a407d41`
→ `docs\evidence\original-apk\README.md`；原始截图在本机
`E:\APPDetector\evidence\original-apk\`（`orig-06-read.png` / `orig-08-drawer.png` / `orig-09-more.png`，
sha256 见 README §3）。审查报告同条：审查组冻结树 `E:\APPDetector\review-base\docs\review\99-FINAL-REVIEW.md`
L213（证据索引 EV-108）——该文件在 `fix-p0` 中不存在，引用必须带工作区名。
**登记**：本条由技术总监代登记（登记时 LC测试 尚未取得本树写权限）；2026-11-05 由 LC审测试 复核。

**复现**（同一台设备，先原版后 LC；命令可重复执行）
> 复现基线：`fix-p0@bc854ec`（K-01 修复前）。**K-01 合入后，用 `d752efa`/develop 跑本命令看不到三 Tab**
> （`showTab` 在 `d752efa` 为 L630、`bc854ec` 为 L634，当前树 0 命中）；复现旧三 Tab 行为请
> `git checkout bc854ec`（或独立 worktree）。取证引用一律用 `git show bc854ec:<path>`，不依赖工作树。
```powershell
$DEV = 'L2E0222531014833'
# 1) 原版：必须用输入包（商店构建 cold start ANR，不可作基线，见 README §2）
#    商店构建与输入包是不同包体/签名（110,781,555 B vs 100,655,304 B），
#    直接 -r 会 INSTALL_FAILED_UPDATE_INCOMPATIBLE；本机实测须先卸载
adb -s $DEV uninstall space.xinzhi.globe
adb -s $DEV install E:\APPDetector\input\swss.apk    # sha256 2FD1FC159C92…
adb -s $DEV shell monkey -p space.xinzhi.globe -c android.intent.category.LAUNCHER 1
# 主界面 → orig-08-drawer.png；点左列「罗盘」(112,1784) → orig-06-read.png（不换页，浮在地图中心）
# 2) LC 现状
pwsh -File scripts\build-android.ps1 -DeviceId $DEV -OutDir checks\k-01
# 底部出现 罗盘/地图/知识 三条 Tab；点「罗盘」整页切换，罗盘不回地图上层
```
```powershell
Select-String -Path app\android\app\src\main\res\layout\activity_main.xml -Pattern 'bottom_bar|tab_compass|tab_map|tab_knowledge'
Select-String -Path app\android\app\src\main\java\com\luopan\fengshui\MainActivity.java -Pattern 'showTab\('
```

**期望**（原版真机事实，取证 `a407d41`）
- 单页全屏地图，**无底部导航**；左列 8 个工具（全地图工具 / 截屏 / 超清优化 / 海拔查询 / **罗盘** / 路网 / 等高线 / 更多），右列 10 个控件（图层 / 叠加 / 山脉 / 水系 / 探索 / 指北针 / 放大 + / 缩小 − / 定位 / 3D）；
- 点「罗盘」**不换页**：罗盘圆盘浮在地图中心，地图半透可见；顶部栏变 返回 / 搜索 / 图层 / 隐藏，右侧开关 地图 / 实景 / 坐南朝北 / 指针开关，左列变 截屏 / 等高线 / 罗盘复位 / 罗盘锁定 / 读盘 / 贴地，底部工具条 切换 / 旋转 / 缩放 / 透明度 / 天支线（罗盘工具，不是页面导航）；
- 「更多」从底部升起 **bottom sheet**「地图工具」（16 宫格 + 编辑），地图仍在上方半透；
- 读盘（读数）/ 知识条目走弹层或抽屉，不占独立页面。

**实际（LC 基线，K-01 修复前；行号按 `fix-p0@bc854ec`，K-01 落地后 `showTab` 已删、行号会漂移，复核用 `git show bc854ec:<path>`）**
`activity_main.xml` 有 `bottom_bar` 与三个页签；`MainActivity.showTab()` 切换
`compassTab` / `mapView` / `knowledgeTab` 的 `VISIBILITY`，罗盘页与地图页是互斥整页，
罗盘不叠加在地图上层；无左列/右列工具列，无 bottom sheet。

**修复归属（D-010 授权）**：实现＝LC审客户端（主刀）/ LC审地图（配合），两者在 `fix-p0` 有写权限；
复核＝LC测试（改冻结脚本 + 重冻基线 + 真机冒烟）、LC审核验（独立核对条目）。
**证据**：`a407d41`（取证 README + 3 份 uiautomator dump）；截图 `orig-06/08/09.png`（本机，sha256 见 README §3）；
基线代码（`bc854ec`）`activity_main.xml` L15 `bottom_bar` + L24/L33/L42 `tab_*`、`MainActivity.java` L634 `showTab()`。
**验收产物（待产出）**：`docs\qa\baseline-android\round-k01\`（重冻后生成，当前不存在，不得当已有证据引用）与 `docs\qa\AT-*` 用例。
**责任人/状态**：LC审客户端（主刀）/ LC审地图（配合），**新建（2026-11-05 11:35）**。

## 观测（待归口，未计入缺陷）

### BUG-005（P2）分享图上看不到审图号角注（被读数面板盖住）

**复现**（真机 `L2E0222531014833`，develop `b79a54a` 构建，一条命令）
```
pwsh -File tests\at\run-final.ps1 -Apk app\android\app\build\outputs\apk\debug\app-debug.apk
# 脚本内：注入 heading 94.04 → 点「截图」→ 取 /sdcard/Pictures/LC/ 新文件 → 探 #srcInfo 矩形
```
**期望**：一键留档的分享图里能读到「底图：天地图地形 · 审图号：未确认」这一行（AT-P0-2 P2-06；
原版截图右下角带来源信息条）。

**实际**：
- 语义层齐：`js` 读 DOM 得 `底图：天地图地形 · 审图号：未确认`，`map.html` 里无 `GS(2026)1219` 第三方号；
- **分享图上不可见**：`#srcInfo` 的 `getBoundingClientRect()` = `left 165.78, top 679.26, w 188.37, h 16.74`，
  `dpr=3.375` → 设备像素 **x560–1195 / y2293–2349**，整块落在底部读数面板带（y≥1850）之下，
  被半透明面板干光；裁片 `p2-06-srcinfo-region.png` 里该区域只有「坐向（转180°）」「截图」两个按钮。

**证据**（`checks\at-final\20261105-0533\`，未入库）
- `shot-compass.png`（2139063 B，1228×2376，sha256 `3AA4FDE8…`）—— 留档全图
- `p2-06-srcinfo-region.png` —— 角注应出现的区域，被面板占满
- `summary.txt` / `table.tsv` 里 `P2-06-vis FAIL` 行

**建议修法**（客户端已有预案）：把信息条抬到面板之上（或截图合成时固定保留右下角），
不要求改 `map.html` 的数据源；修完重跑同一条命令，`P2-06-vis` 应变 PASS。

**责任人/状态**：LC客户端，**已修复/关闭**（`111c4fb` 截图合成，2026-11-05 05:46 复验通过）。

**修复与复验（2026-11-05 05:46）**
- 修法：`captureMap()` 先取 `#srcInfo.textContent`，再用 `composeAttribution` 把「底图·审图号」**画进图内、面板上缘之上**
  （12dp 金字 + 半透明底），不动 `map.html`；日志 `LCCAP attribution drawn: …`。
- 复验构建：我的运行分支 `d286873`（merge `111c4fb`），APK sha256 `A94DE24A…F70F189B`，真机 `L2E0222531014833`。
- 结果：`run-final.ps1` **18 PASS / 0 FAIL / 0 BLOCKED**（exit 0）；`P2-06-vis PASS`：
  面板上方条带 y1400–1850 最大金色像素行 **143 @ y=1717**（无角注的旧构建同区域 54 → 取 100 作门槛）；
  截图 `checks\at-final\20261105-0544\shot-compass.png`（2150066 B，sha256 `AB8C7821…`）里可见
  「底图：天地图地形 · 审图号：未确认」。
- **判据修正**：客户端改成「画进图里」之后，`#srcInfo` 仍在面板后面（DOM 矩形不变），
  继续用 DOM 矩形判会永远 FAIL；`run-final.ps1` 的 `P2-06-vis` 已改成**判图**（面板带上方金色文字行），
  语义（DOM 文案 + 禁第三方号）仍单列 `P2-06`，互不抵消。

### OBS-001 5 个盘面只渲染 3 种
`CompassView.java:18-22` 保存 `dialType`，但 `onDraw` 只在 `:156`（`dialType>0` 画分金/龙细环）
和 `:178`（`dialType==4` 加 64 卦环）用到它 ⇒ dial 1/2/3 渲染完全相同，dial 0 只是隐藏细环。
原版 `CommonCompassEnum` 5 个盘面各有 `resId`（`app/MECHANICS.md:74-81`），应各有差异。
建议由 LC逆向 在差距矩阵登记、LC客户端 排期；**真机确认待第二轮**（本轮真机让给 LC地图）。

### OBS-002 `--screenshot` 中心读数硬编码「正向」
`app/src/software.rs:287` 固定输出 `"正向 · {YINYANG}"`，未用 `rd.jian`。
当前 `software_screenshot()` 固定 `heading=0.0`（正向），故不产生错误结论；
若以后用它渲染非零航向会「假绿」。低优先级，改截图工具时一并处理。

### OBS-003 留档文件名比 PRD 少「山」字
真机实测（`b79a54a`，heading 94.04、开关全关）生成
`坐向酉向卯_20261105_053453.png`；`MainActivity.saveCompassShot()` 用的是
`CompassData.MOUNTAINS[i]`（单字「酉」「卯」），而 PRD/AT 的写法是「坐向X**山**向Y**山**_时间戳」。
不影响文件唯一性/相册可见性；**交 LC产品 定名**：要么改实现加「山」，要么改 PRD 写法。
自动化当前判「模式匹配（坐向<坐>向<向>_时间戳）」PASS，并把这个差异写进 `P2-03` 的实际值里。

