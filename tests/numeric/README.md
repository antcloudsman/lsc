# LC 数值回归测试（`tests/numeric`）

面向「罗盘读数与圈层数值」的可重复自动化回归。**只读业务代码，不修改任何 `app/` 文件。**

## 一条命令

```powershell
cd E:\APPDetector\output
python tests\numeric\run_all.py
```

- 退出码 `0` = 全绿，`1` = 有失败/错误；
- 输出末行 `RESULT: PASS/FAIL` 与计数（tests / failures / errors / skipped / expected_failures /
  unexpected_successes）；
- 依赖：Python 3.10+；Java 对拍需要 JDK17（自动从 `JAVA_HOME` 或
  `C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9` 查找，找不到则跳过该组并计入 skipped）。

单看两盘读数对照（缺陷证据收集）：

```powershell
python tests\numeric\tools\dump_readings.py
```

## 覆盖清单

| # | 覆盖点 | 用例（文件） |
|---|---|---|
| 1 | 归一化 `v%=360; v<0 → +360`：0 / 360 / 720.5 / −0.01 / −360 / 1080.25 | `test_formula.NormalizeTest` |
| 2 | face 公式 `⌊((h−off+360)%360)/interval⌋`：14 圈层 index 范围、下边界含端点、上一点归前一格、359.99 = 末格、offset=0 与 >0 的 0° 行为 | `test_formula.FaceFormulaTest` |
| 3 | 24 山边界：7.499 / 7.5 / 15 / 22.5 / 352.499 / 352.5 / 359.99 / 360 / 367.5，跨圈 (720.5, 1080.25)，负角 | `test_formula.FaceFormulaTest` |
| 4 | back 公式 `(h+180)%360`：0/90/180/270/45/359.99，与 `(m+12)%24` 等价，跨 360 回绕 | `test_formula.BackFormulaTest` |
| 5 | 120 分金：120 格 × 3°、offset 1.5、每山 5 格 | `test_formula.SpecialRingsTest`、`test_plates.FenjinDragonSlotTest` |
| 6 | 72 龙：72 格 × 5°、offset 2.5、每山 3 龙 | 同上 |
| 7 | 60 龙：60 格 × 6°、offset **1.5**（0°→癸亥、1.5°→甲子）、60 格全覆盖 | 同上 |
| 8 | 64 卦：5.625° × 64 = 360°（二进制精确）、边界 5.625/359.99 | `test_formula.SpecialRingsTest` |
| 9 | 八卦：45° × 8、22.5° 边界、后天/先天 8 方位 | `test_formula.SpecialRingsTest` |
| 10 | 三盘：地盘 0 / 人盘 −7.5 / 天盘 +7.5，关键航向表、恒有 2 个不同读数、DEX 名单等价 | `test_plates` |
| 11 | 正兼向：中央 9°（±4.5 含端点）、外侧 3°（兼向邻山名） | `test_plates.ZhengJianTest` |
| 12 | 14 圈层参数与 `MECHANICS.md` 表格逐行对齐（防手误） | `test_rings.RingTableTest` |
| 13 | 两端同源：`mechanics.json` 19 项 / 0 估计；7 张表逐项；5 盘面名；`mechanics.json` v2 的 values/rings/dials/tables 四段 | `test_mechanics` |
| 14 | 真实代码对拍：Java `CompassData.read`（javac 编译执行，40 航向）＋ Rust `reading_for`（表达式从源码解析求值）＋ 两端一致 | `test_ends_parity` |
| 15 | B2 文字表：Rust/Java 生成的 `RINGS` 与 `rings[].labels` **逐条一致（780）**、`ring_text` 按 (offset,interval) 取表、四向量、读数面板确用 `ring_text` | `test_b2_golden` |

## oracle 依据（不臆造）

- `app/MECHANICS.md:88-103`：`CompassReadTypeEnum` 14 圈层的 `(intervalDegrees, offsetDegrees)`；
- `app/MECHANICS.md:149-161`：`getFaceText/getBackText` 公式与 60 龙 offset=1.5 的更正；
- `app/mechanics.json`：19 项数值 + v2 的 rings/dials/tables；
- `app/MECHANICS.md:170-205`：24 山顺序、三盘 ±7.5°、分金/龙命名、五行/阴阳/先后天八卦、六十甲子纳音。

`oracle.py` 不 import 业务代码；被测代码通过两条独立路径验证（见覆盖 14），
所以「oracle 写错」不会因为两端同错而静默通过——Java 与 Rust 是两支独立实现。

## 轮 B 回归

`409ad5f`（BUG-001/002 修复）后，4 条三盘 xfail 已转正常用例（`test_divergences`）；
B2（`662c006`）后圈层文字改走 `ring_text(type, h)`，圈层不再整圈画字，原「标签==读数」
不变量删除，文字改由 `test_b2_golden` 与 `rings[].labels` 逐条对拍。

**以后新增缺陷的惯例**：先写会失败的断言并用 `@unittest.expectedFailure` 锁定（套件保持绿），
缺陷修好后会变成 unexpected success（红）→ 删标记、更新 `docs/qa/bugs.md`，再提交。
60 龙读数公式 `(h-1.5)/6` 不许加补偿；文字落位 `offset+(i+0.5)×interval`。

## 目录

```
tests/numeric/
├─ run_all.py            # 唯一入口
├─ oracle.py             # 公式/表 oracle（只做数学，不读业务代码）
├─ source_parse.py       # 从 main.rs / CompassData.java / MainActivity.java 读表达式与表
├─ test_formula.py       # 公式与边界
├─ test_rings.py         # 14 圈层几何 + MECHANICS.md 对齐
├─ test_plates.py        # 三盘 + 正兼向 + 分金/龙格
├─ test_mechanics.py     # 19 项 0 估计 + 两端同源 + v2 四段
├─ test_ends_parity.py   # Java 真实代码 / Rust 源码驱动对拍
├─ test_divergences.py   # 轮 B 回归（三盘读数）
├─ test_b2_golden.py     # B2 文字表：RINGS/rings[].labels/ring_text/四向量
├─ java/CompassProbe.java# 测试探针（不进 APK）
└─ tools/dump_readings.py# 缺陷证据/读数对照
```
