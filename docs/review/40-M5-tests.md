# M5 测试资产审查报告

- **模块**：M5 测试资产（`tests/numeric/**`、`tests/at/**`，共 22 文件 / 2974 行，与 `00-MATRIX.md` L22、L101-L122 一致）
- **审查人**：LC审测试
- **基线**：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`）
- **工作区**：`E:\APPDetector\review-base`（只读；本次未改任何源码）
- **已读**：22/22 文件逐行读（清单见 §8）；另为取证读了 6 处业务/文档片段（见 §9，未做整文审查）
- **批次**：第一批（高 2 条 + 交叉引用 1 条 + 5 条必查项结论）已交；第二批（中 7 / 低 5，共 12 条）在 §4；第三批（复核补遗：中 1 / 低 3，F-16～F-19）与三处数值修正见 §12；第四批（f6e97dc / ac08694 两笔修复的独立复核 + F-20 + 1440/2267 口径并列 + Q-04/Q-05）见 §13；**文件级覆盖与必查项缺口见 §11**
- **实测环境**：Python 3.13.14；JDK 17 位于硬编码路径 `C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9`；`pwsh` 7.6.9；无真机、无 APK

**本批计数**：阻断 0 / 高 3（已证实 3，含 §13 的 F-20）/ 交叉引用 1（F-03→B-10，不计入 M5 正式数）/ 中 8（已证实 8，含 F-17）/ 低 8（已证实 8，含 F-16、F-18、F-19）/ 高度可疑待确认 4（Q-04 新增）/ 风险改进 6（R-05、R-06 新增）。

**复现总账**（本机实跑）：

```
LC numeric suite — python 3.13.14
tests=86 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
RESULT: PASS
```

---

## 1 结论摘要

M5 的 86 条数值用例在本机全绿，**但它绿的覆盖面比它自表述的小得多**：

1. 质量门禁把「跳过」当「通过」：无 JDK 时 9 条用例未执行（`testsRun` 86→77，`skipped` 只记 3 条记录），`run_all.py` 仍返回 0 且打印 `RESULT: PASS`，AT 的 `RT-011-numeric` 仍判 PASS（F-01）。
2. B2 换表的核心验收（780 条文字 + `ring_index` 取表语义）在 Python 套件里**只做字符串存在性检查**；Rust 侧唯一的真执行是 `main.rs` 里从不被本套件运行的 `assert_eq!`；我把 `ring_index` 换成语义错误的公式（内存注入，未改仓库文件），**整套 86 条仍 0 失败、`RESULT: PASS`**（F-02）。
3. 「读数 vs 圈层」两条正/兼向链路在同一航向给出互斥文字（同一屏：画布「兼向子」/ 面板「癸山九度内」），而数值套件只锁其中一条、AT golden 只锁另一条，双方各自 PASS（F-03，**已按裁定转交叉引用，母条 B-10**）。

必查项 5 条中，②④⑤ 未达标，①③ 部分达标。

---

## 2 必查项逐项结论（总监令点名的 5 条）

### ① `xfail/skip` 数量与是否掩盖真缺陷 —— 部分达标

- `@unittest.expectedFailure` 在基线 **0 条**（全仓 grep 仅注释/文档提及）。4 条三盘 xfail 已于 `409ad5f` 转正（`tests/numeric/test_divergences.py` L1-L7）。
- `skip` 共 **4 处**、2 类（运行时本机 0 条触发）：
  - `tests/numeric/test_ends_parity.py` L44-L47：无 `javac` → `raise unittest.SkipTest`，整类 6 条不执行；L145-L147 再派生 1 条跳过；
  - `tests/numeric/test_sensor_math.py` L46-L51：`SensorMath.java` 不存在或无 JDK → 整类 3 条不执行，且提示文案「P0-1 f2cf102 未合入」已过期（文件实际存在）。
- **是否掩盖**：当前本机 JDK 命中，未掩盖；但 `run_all.py` L31-L35 用 `wasSuccessful()` 判绿、`skipped` 不参与，AT `RT-011-numeric` 仅匹配 `failures=0` 与 `PASS`（`tests/at/run-final.ps1` L698）——**跳过即绿**，见 F-01。
- 流程风险：`tests/numeric/README.md` L60 把 `expectedFailure` 定为「以后新增缺陷的惯例」，`run_all.py` L7 定义「0 = 全绿（含已知缺陷 xfail）」，等于把缺陷长期留在绿套件里。当前无 xfail，记为 R-03。

### ② `source_parse.py` 正则脆弱性（代码一改静默跳过）—— 部分达标

- **结构漂移不静默**：`rust_let_expr`（L55-L60）、`ring_text_body`（L63-L71）、`fn_body`（L74-L79）、`java_method_body`（L82-L89）、`parse_rust_rings`（L92-L107）、`parse_java_rings`（L110-L124）、`rust_array`（L127-L132）、`java_array`（L135-L139）、`java_plate_exprs`（L142-L154）在找不到目标时**一律 `raise AssertionError`**，不会静默跳过。这是优点，已核实。
- **语义漂移静默**：`test_b2_golden.py` 拿到 `fn_body` 后只做 `assertIn` token 检查（L90-L97、L99-L107），函数体写法变了但 token 还在就过。变异实验见 F-02。
- **同名变量跨函数匹配**：`rust_let_expr` 用 `re.search` 在**整个 main.rs** 找首个 `let <name> =`（L56），而 `main.rs` 有两个 `let m =`（L241 读数、L387 面板）。当前按源码顺序恰好命中 L241，但无函数作用域约束；若上方新增同名 `let m`，`rust_reading` 会取错表达式（后果是数值不符/eval 报错，属噪声失败而非静默通过）。记 R-04。

### ③ AT 的 `BLOCKED/exit 2` 会不会被当 PASS —— 部分达标

- **退出码映射本身正确**：`tests/at/run-final.ps1` L740-L742 为 `FAIL→1 / BLOCKED→2 / 其余→0`；`tests/numeric/tools/at_p0_0_check.ps1` L144-L157 同理。未发现「BLOCKED 直接返回 0」。
- **但存在 4 条把「未验证」写成 PASS 的路径**：缺 prefs 默认 `true` 判 PASS（F-05）、P2-08 宽松回退仍 PASS（F-06）、P2-05 只查 chooser 存在（F-08）、`PASS(等价路径)` 不进任何计数（F-09）；
- **还有一条把「功能失败」降级成 BLOCKED**：P2-01 点了 UI 按钮但没产出文件，判 `BLOCKED` 而不是 `FAIL`（F-07），使 exit 2 而非 exit 1。
- **文档层**：`docs/qa/AT-P0-0-feature-switch.md` L51、`docs/qa/AT-P0-1-reading-trust.md` L48、`docs/qa/AT-P0-4-3d-probe.md` L53 均写「执行器（LC测试 已落）：`pwsh -File tests\at\run-at.ps1 -Suite …`，缺前置 exit 2」，而 **`tests/at/run-at.ps1` 在基线不存在**（`git ls-files tests/at` 只有 `golden_readsheet.py`、`run-final.ps1`）。见 F-10。

### ④ golden 是否来自原版证据而非实现自证 —— 未达标

- 780 条文字的 golden 只存在于 `app/mechanics.json`；`tools/mechanics/gen-mechanics.ps1` L7-L9 明示「`app/mechanics.json` is the only hand-edited source」，同一份 json **生成** Rust `RINGS` 与 Java `RINGS`（本报告 §4 F-02 已引）。
- 因此 `test_b2_golden.py` L59-L78「两端 RINGS == mechanics.json」是**生成器自证**（只能抓手工改生成区），不是与原版对拍；README L42 把它表述为「逐条一致（780）」，口径被放大。
- 唯一独立副本是 `test_b2_golden.py` L21-L26 的 4 个硬编码向量（0.5% 覆盖），其出处 `LC逆向 8368fbd` / `.inspect/derived/ring_label_addrs.json` **不在 review-base**（按纪律未读 `E:\APPDetector\output`）。基线内 `investigation.cany/evidence/` 只有 `getTextList` 的 offset 0（250 条）与 offset 1500（300 条）两段，合计 550/2144 条指令，覆盖不到 TYPE_7 分支（smali 4652-5135）。见 Q-01。
- `test_mechanics.py` L149-L159「14 张表 780 条，全部 confirmed_dex」只断言 `ring.get("labels_status") == "confirmed_dex"`（L157），这是 json 里**自填字符串**，任何错误标签都能带上该字符串通过；`labels_source` 字段无任何用例校验。

### ⑤ 是否覆盖「读数跨方位一致性」而非只测少数点 —— 未达标

- **实现侧航向点**：`oracle.CORPUS`（`tests/numeric/oracle.py` L107-L112）共 **40** 个（实测 `len(O.CORPUS)==40`；初稿写 41，§12.1 C-1 已更正），边界密集（0/4.5/5.625/7.5/352.5/359.99/负数/跨圈），但**不是全周扫描**；`test_ends_parity.py`、`test_divergences.py` 仅遍历这 40 点。
- **圈层文字**：`test_b2_golden.py` 只验 4 个向量（L21-L26、L41-L49），其余 776 条只验长度/同源。
- **绘制与读数一致性**：`tests/numeric/test_divergences.py` L5-L7 自述「圈层不再整圈画字，『指针处标签 == 读数』不变量随之失效——文字校验移到 test_b2_golden」，而 `test_b2_golden.py` 对 `ring_center` 仅断言函数体含 `"0.5"` 与 `offset`（L109-L114），**绘制角与取表下标的一致性没有任何执行级断言**。
- **AT 侧**：读盘面板只在单一航向 `94.04°` 校验（`run-final.ps1` L451-L461，默认 `$ReadSheetHeading = 94.04`），截图固定 `94.04`（L526），开关固定 `0`（L295）。即真机侧跨方位一致性 = 0 个额外点。
- 记 R-01、R-02（覆盖缺口，不出缺陷级）。

---

## 3 缺陷清单 · 第一批（高 + 交叉引用）

> 本批正式 2 条（高，均「已证实」）+ 1 条交叉引用（F-03→B-10，不计入 M5 正式数）。

### F-01（高，已证实）无 JDK 时数值套件仍 `RESULT: PASS`，AT `RT-011-numeric` 仍判 PASS

**文件行号**：`tests/numeric/run_all.py` L31-L35；`tests/numeric/test_ends_parity.py` L44-L47、L145-L147；`tests/numeric/test_sensor_math.py` L46-L51；`tests/at/run-final.ps1` L696-L700。

**代码片段原文**（`tests/numeric/run_all.py` L31-L35）：

```python
    if result.wasSuccessful():
        print("RESULT: PASS")
        return 0
    print("RESULT: FAIL")
    return 1
```

（`tests/numeric/test_ends_parity.py` L44-L47）：

```python
    @classmethod
    def setUpClass(cls):
        javac, java = find_jdk()
        if not javac:
            raise unittest.SkipTest("未找到 JDK（javac），跳过 Java 真机代码对拍")
```

（`tests/at/run-final.ps1` L698）：

```powershell
$numericOk = ($numeric -join "`n") -match 'failures=0' -and ($numeric -join "`n") -match 'PASS'
```

**触发条件**：机器上没有 `JAVA_HOME`、`PATH` 里也没有 `javac`，且硬编码路径 `C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9` 不存在（换机、清理临时目录、CI 容器）。`find_jdk()`（`test_ends_parity.py` L26-L37）返回 `(None, None)` → 两个 `setUpClass` 抛 `SkipTest`，`test_matches_java_earth_readings` 再派生跳过（L146-L147）。

**预期 vs 实际**：
- 预期：质量门禁在「真机代码对拍 / SensorMath 断言未运行」时不得判全绿；`RT-011-numeric` 判据为「套件全绿」，应校验 `skipped==0`（或对必需组跳过单独判 FAIL/BLOCKED）。
- 实际：`wasSuccessful()` 不把 skip 计为失败 → 打印 `RESULT: PASS`、返回 0；AT 只用正则找 `failures=0` 与 `PASS`，同样判 PASS。**唯一执行 Android 真机代码的 6 条 + SensorMath 3 条一起消失，门禁仍绿。**

**复现**（本机实跑，未改仓库；脚本把两组 `JAVA_CANDIDATES` 置空并让 `shutil.which` 返回 None）：

```
NO_JDK: testsRun=77 skipped=3 failures=0 errors=0 wasSuccessful=True
NO_JDK: run_all.py would print RESULT: PASS
  skip: setUpClass (test_ends_parity.JavaRealCodeTest) | 未找到 JDK（javac），跳过 Java 真机代码对拍
  skip: test_ends_parity.RustSourceTest.test_matches_java_earth_readings | Java 行未就绪
  skip: setUpClass (test_sensor_math.SensorMathJvmTest) | 未找到 JDK（javac），跳过 SensorMath JVM 断言
```

说明：`skipped=3` 是 3 条 skip 记录（2 个类级 `setUpClass` + 1 条派生 `skipTest`）；`testsRun` 由 86 降到 77，即 **9 条用例实际未执行**（`JavaRealCodeTest` 6 条 + `SensorMathJvmTest` 3 条）。

**修复方向**：`run_all.py` 在 `skipped>0` 时返回非 0（或至少对 `test_ends_parity.JavaRealCodeTest`、`test_sensor_math.SensorMathJvmTest` 两组做「缺失即 FAIL」的必需组清单）；`run-final.ps1` L698 的 `$numericOk` 增加 `skipped=0` 匹配；`test_sensor_math.py` L48 的过期跳过文案改为「文件缺失即 FAIL」。

---

### F-02（高，已证实）780 条圈层文字与 `ring_index` 无执行级断言，B2 验收②靠字符串存在性自证

**文件行号**：`tests/numeric/test_b2_golden.py` L90-L97、L99-L107、L116-L128；`app/src/main.rs` L637-L674（仅作对照）；`tools/mechanics/gen-mechanics.ps1` L7-L9。

**代码片段原文**（`tests/numeric/test_b2_golden.py` L90-L97）：

```python
    def test_ring_index_uses_offset_interval(self):
        """索引公式在 `ring_index` 里：(normalize(h)-offset)/interval 取整。"""
        body = SP.fn_body(self.main_rs, "ring_index")
        for token in ("ring.offset", "ring.interval", "normalize_deg", "% n"):
            self.assertIn(token, body, f"ring_index 体缺 {token!r}")
        jbody = SP.java_method_body(self.java, "ringIndex")
        for token in ("r.offset", "r.interval", "Math.floor", ".length"):
            self.assertIn(token, jbody, f"Java ringIndex 体缺 {token!r}")
```

（`tests/numeric/test_b2_golden.py` L116-L121）：

```python
    def test_client_unit_vectors_present(self):
        for ring_type, heading, want in ((7, "0.0", "壬子 庚子"), (7, "170.0", "丙午 甲午"),
                                         (5, "0.0", "戊子"), (6, "0.0", "甲山 戊子")):
            pattern = (rf'assert_eq!\(ring_text\({ring_type}, {heading}\), '
                       rf'"{want}"')
            self.assertRegex(self.main_rs, pattern, f"main.rs 缺 TYPE_{ring_type} 向量断言")
```

（`tools/mechanics/gen-mechanics.ps1` L7-L9）：

```powershell
# app/mechanics.json is the only hand-edited source. Never edit the generated regions:
#   pwsh -File tools/mechanics/gen-mechanics.ps1          # regenerate in place
#   pwsh -File tools/mechanics/gen-mechanics.ps1 -Check   # verify, exit 1 if stale
```

**触发条件**：`ring_index` / `ring_text` / `ring_center` 的函数体被改成语义错误的公式，但保留 `ring.offset`、`ring.interval`、`normalize_deg`、`% n`、`labels[`、`ring_index(` 这些 token；或 4 个向量断言文本仍在 `main.rs`（不删）。`run_all.py` 与 `run-final.ps1` 都不跑 `cargo test`（全仓 grep 只在文档出现 `cargo test`），因此 `main.rs` L671-L674 的 `assert_eq!` 只在人工 `cargo test` 时生效。

**预期 vs 实际**：
- 预期：B2 的核心不变量（指针航向 → 圈层下标 → 780 条文字）有可执行的实现级断言；改错 `ring_index` 必须变红。
- 实际：Python 只验 token/字符串存在；Rust 生成表与 `mechanics.json` 同源（生成器保证），`test_rust_rings_match_mechanics`（L59-L66）与 `test_java_rings_match_mechanics`（L68-L75）是生成器自证；改错 `ring_index` 后套件全绿。

**复现**（变异实验：用 `re.sub` 把 `ring_index` 函数体整体替换为错误公式，其余不动，再跑同一组 token 断言）：

```
MUT: ring_index rewritten to a wrong formula; token assertions pass = True
MUT: real index=59 label='壬子 庚子' ; mutated index=0 label='壬子 壬子'
MUT+FULL: testsRun=86 failures=0 errors=0 skipped=0 wasSuccessful=True
MUT+FULL: run_all.py would print RESULT: PASS
```

即 60 龙 0° 会从原版 `壬子 庚子` 变成 `壬子 壬子`，而 `test_b2_golden` 乃至**整套 86 条全部通过**（`MUT+FULL` 用内存注入的变异源码替换 `SP.read_text` 返回值，磁盘文件未改）。

**修复方向**：在 `tests/numeric` 增加以 `mechanics.json` 为输入的**实现级**复算（用 `source_parse` 解析出的 `ring_index` 公式在 Python 中求值，或把 `main.rs` 的 `assert_eq!` 纳入 `run_all.py` 调 `cargo test`）；把 `ring_index`/`ring_center` 的 token 检查升级为「对 14 圈层 × 全部格心 + 界前一格求值，与 `oracle.dex_index` 全等」；`ring_center` 断言 `offset+(i+0.5)*interval` 的数值而不是 `"0.5"` 子串。

---

### F-03（交叉引用 → B-10；M5 正式数不计）正/兼向两条 UI 链路在同航向给出互斥文字，测试资产无一致性断言

> **总监裁定**：「B-10 升为高，A-03、F-03 转交叉引用。」本条保留全部证据供 B-10 引用，级别随母条 B-10（高），M5 正式清单不再计入。

**文件行号**：`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` L226-L238；`CompassView.java` L287-L299；`MainActivity.java` L301-L307、L639-L647；`app/mechanics.json` L986-L999；`tests/numeric/oracle.py` L195-L200；`tests/numeric/test_ends_parity.py` L107-L110；`tests/numeric/test_b2_golden.py` L123-L128；`tests/at/golden_readsheet.py` L46-L59。

**代码片段原文**（`CompassData.java` L226-L238）：

```java
            this.fenjinText = ringText(6, h);
            this.dragon72Text = ringText(5, h);
            this.dragon60Text = ringText(7, h);
            this.jianText = ringText(14, h);
            this.houtianText = ringText(2, h);
            this.xiantianText = ringText(1, h);

            if (Math.abs(offset) * 2f <= ZHENG_XIANG_DEG) {
                this.jian = "正向";
            } else {
                int n = offset < 0 ? (m + 23) % 24 : (m + 1) % 24;
                this.jian = "兼向" + MOUNTAINS[n];
            }
```

（`CompassView.java` L296-L299）：

```java
        textPaint.setColor(gold);
        textPaint.setTextSize(13f * density());
        drawText(canvas, rd.jian + " · " + CompassData.YINYANG[rd.mountain],
                cx, cy + 34f * density(), textPaint);
```

（`MainActivity.java` L640-L642）：

```java
                String.format("度数 %.2f°  %s  %s\n地盘 %s山  人盘 %s山  天盘 %s山  坐 %s山\n分金 %s  七十二龙 %s\n六十龙 %s  后天卦 %s  先天卦 %s",
                        r.heading, r.jianText, CompassData.MOUNTAINS[r.mountain],
                        CompassData.MOUNTAINS[r.mountain],
```

**触发条件**：罗盘页任意航向。`CompassView` 中心文字用 `rd.jian`（按**地盘** `offset`、±4.5° 判正向，`oracle.py` L195-L200 同口径）；下方读数面板与读盘弹层用 `r.jianText = ringText(14,h)`（`mechanics.json` TYPE_14 `offset=0.0` + 癸起名单，实为**人盘**口径）。两处在同一屏幕同时可见。

**预期 vs 实际**：
- 预期：同一航向只有一个正/兼向口径，或两个口径用不同标签明确区分（如「地盘正兼向」/「人盘九度」）；测试至少断言二者不矛盾。
- 实际（逐点复算，`oracle` + `mechanics.json` TYPE_14）：

| 航向 | 地盘山 | 地盘 offset | `rd.jian`（画布中心） | `ringText(14,h)`（面板/读盘） |
|---|---|---|---|---|
| 0.0° | 子 | 0.00 | 正向 | 癸山九度外 |
| 4.5° | 子 | +4.50 | 正向 | 癸山九度内 |
| 7.5° | 癸 | −7.50 | **兼向子** | **癸山九度内**（=正向） |
| 15.0° | 癸 | 0.00 | 正向 | 丑山九度外 |
| 94.04° | 卯 | +4.04 | 正向 | 乙山九度内 |

  7.5° 时画布写「兼向子 · 阴」（`YINYANG[1]`）、面板写「度数 7.50°  癸山九度内」，**兼/正互斥、山名也不同**。TYPE_14 名单与「人盘」逐点吻合（0°→癸、15°→丑、94.04°→乙、180°→丁），即该 ring 是 −7.5° 口径，而 `rd.jian` 是地盘口径。
- 测试资产为何没发现：`test_ends_parity.py` L107-L110 只对 `row["jian"]` 断言；`test_b2_golden.py` L123-L128 只断言 `ringText(14, h)`/`ring_text_clean(14, rd.heading)` **字符串存在**；AT `golden_readsheet.py` L46-L59 的第 14 行由同一份 `mechanics.json` 生成，`run-final.ps1` L485-L500 只比对读盘弹层——两边各自 PASS。
- 定级说明：**已裁定**——母条 B-10（高），本条转交叉引用；M5 只保留「无一致性断言」的证据，正式清单不计本条。

**修复方向**：先在客户侧二选一（保留 TYPE_14 口径则删/改 `rd.jian`；保留地盘口径则把第 14 行换成地盘文案）；测试侧在 `tests/numeric` 增加 `CompassData.read(h).jian` 与 `read(h).jianText` 的互斥断言（或显式断言二者按不同盘面命名），并把 4.5/7.5/352.5 加入边界用例；AT 增加对 CompassView 中心文字的取值通道（改为可 dump 的 TextView 或截图裁片 OCR），否则该文字永远不在 AT 证据里。

---

## 4 缺陷清单 · 第二批（中 / 低）

> 12 条，全部「已证实」；格式同 §3（触发条件 / 预期 vs 实际 / 修复方向）。

### F-04（中，已证实）`test_ends_parity` 把 60 龙名锁在教材旧值 `癸亥`，修成 DEX 原值反而变红

**文件行号**：`tests/numeric/test_ends_parity.py` L99-L105；`tests/numeric/oracle.py` L191；`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` L221；`tests/numeric/test_formula.py` L116-L124；`tests/numeric/test_plates.py` L144-L150。

**代码片段原文**（`test_ends_parity.py` L99-L105）：

```python
    def test_sitting_and_names(self):
        for h, row in self.rows.items():
            want = O.earth_reading(h)
            self.assertEqual(int(row["sit"]), want["sit"], f"h={h} 坐")
            self.assertEqual(row["fenjin"], want["fenjin"], f"h={h} 分金名")
            self.assertEqual(row["d72name"], want["d72name"], f"h={h} 72龙名")
            self.assertEqual(row["d60name"], want["d60name"], f"h={h} 60龙名")
```

（`oracle.py` L190-L192）：

```python
        "d72name": wangxiang(borrow_branch(m), dragon72_slot),
        "d60name": JIAZI[dragon60],
    }
```

（`test_plates.py` L147-L149）：

```python
        # 只锁几何：60 龙 0° 落在第 59 格（offset 1.5），4.5° 落在第 0 格。
        # 文字 golden 在 test_b2_golden（原版 = rings[7].labels[59]='壬子 庚子'，
        # 当前教材值 JIAZI[59]='癸亥' 由那条 xfail 锁住，B2 换表后转正）。
```

**触发条件**：有人把 `CompassData.read().dragon60Name`（L221 `JIAZI[dragon60]`）改为 DEX 原表值（`mechanics.json` TYPE_7 labels[59]=`壬子 庚子`）。

**预期 vs 实际**：
- 预期：B2 换表后，60 龙的显示名以 `rings[7].labels` 为准（`test_b2_golden.py` L21-L26 已把 `壬子 庚子` 定为 golden）；旧字段若保留，测试应标注「教材值、非原版」而不是断言相等。
- 实际：`oracle.d60name = JIAZI[dragon60]`，`test_ends_parity` 断言 Java 同值；`test_formula.py` L124 还硬写 `self.assertEqual(O.JIAZI[59], "癸亥")`。修对 → 测试红。且 `test_plates.py` L148-L149 注释里「由那条 xfail 锁住」的 xfail **在基线已不存在**（0 条 expectedFailure），该分歧现在没有任何 xfail/注释级锁定，只有这两条断言在反向锁定。

**修复方向**：`oracle.earth_reading()["d60name"]` 改为 `dex_labels(7, dragon60)` 并同步 `test_ends_parity`；`test_formula.py` L124 改为断言 `O.dex_labels(7, 59) == "壬子 庚子"`，教材值只作为对照常量出现。

---

### F-05（中，已证实）AT 缺 prefs 时按「代码默认 true」判 PASS（两处）

**文件行号**：`tests/at/run-final.ps1` L306-L311；`tests/numeric/tools/at_p0_0_check.ps1` L73-L78。

**代码片段原文**（`run-final.ps1` L306-L311）：

```powershell
    $prefs = Get-Prefs
    $on = Get-PrefBool $prefs 'p0_1_enabled'
    if ($null -eq $on) { $on = $true }  # 代码默认 true，prefs 缺省
    Log "P0-0 on: reading=$reading 偏角行=$hasDisc 磁北/真北行=$hasNorth prefs=$on"
    Assert3 'SW-006-on-1' $(if ($hasDisc -and $hasNorth) { 'PASS' } else { 'FAIL' }) '开关 on：偏角行 + 磁北/真北行在' "偏角=$hasDisc 磁北/真北=$hasNorth 读数=$reading°" $ui.path
    Assert3 'SW-006-on-2' $(if ($on) { 'PASS' } else { 'FAIL' }) 'p0_1_enabled=true' "p0_1_enabled=$on" (Join-Path $OutDir 'prefs-on.xml')
```

（`at_p0_0_check.ps1` L75-L77）：

```powershell
$m = [regex]::Match($prefs, 'name="p0_1_enabled"\s+value="(true|false)"')
if ($m.Success) { $enabled = $m.Groups[1].Value -eq "true"; $prefsNote = "persisted=$($m.Groups[1].Value)" }
else { $enabled = $true; $prefsNote = "missing -> 代码默认 true" }
```

**触发条件**：`run-as` 失败（非 debuggable 包）、`shared_prefs/lc_prefs.xml` 不存在（`pm clear` 后未落到盘）、`Get-Prefs` 返回 `''`。此时 `$prefs` 为空字符串，正则不匹配 → `$null -eq $on` 成立 → `$on = $true`。

**预期 vs 实际**：
- 预期：`SW-006-on-2` 判据是「persisted 值 = true」，取不到值 = 证据缺失，应判 BLOCKED（AT 文档 `AT-P0-0-feature-switch.md` L43 明写「文件不存在 = 功能未实现，按 BLOCKED 记录，不要当成 false」）。
- 实际：取不到值直接按 `true` 判 PASS，并把 `prefs-on.xml` 写成空文件当证据；`at_p0_0_check.ps1` 同路径，且其 PASS 文案会打印 `p0_1_enabled=True`（L155）。

**修复方向**：两处改为 `if ($null -eq $on) { Assert3 … 'BLOCKED' …; }`，并把 `prefs.xml` 是否非空作为前置判据；AT 文档 L43 的规则同步到代码。

---

### F-06（中，已证实）P2-08「顶部两行」宽松回退匹配后仍判 PASS

**文件行号**：`tests/at/run-final.ps1` L490-L501。

**代码片段原文**：

```powershell
            # 顶部是**一个**两行 TextView：dump 里 text="向北 …°&#10;坐X山  向Y山"
            $headCombined = $golden.head1 + "`n" + $golden.head2
            $headNode = $texts | Where-Object { $_ -eq $headCombined } | Select-Object -First 1
            $hasHead1 = [bool]$headNode
            $hasHead2 = [bool]$headNode
            if (-not $headNode) {
                foreach ($t in $texts) { if ($t -match '向北' -and $t -match '坐南') { $headNode = $t; break } }
            }
```

**触发条件**：顶部节点文本与 golden 不完全一致（度数写错、山名错、空格差异），但仍含「向北」与「坐南」两个词。

**预期 vs 实际**：
- 预期：`P2-08` 判据是「顶部两行：`$golden.head1` / `$golden.head2`」（L501 的 expected 原文），必须逐字相等。
- 实际：回退分支把任意「向北…坐南…」节点认作命中，`$hasHead1` 变 true → PASS；且 `$hasHead2` 与 `$hasHead1` 是同一个布尔（L494），两行的独立性没实现。

**修复方向**：删除 L495-L497 的宽松回退，或把回退命中记为 `FAIL` 并把实际文本写进 actual；`$hasHead1/$hasHead2` 分别按 `head1`/`head2` 子串判定。

---

### F-07（中，已证实）P2-01 点了界面按钮但没产出文件，被判 BLOCKED 而非 FAIL

**文件行号**：`tests/at/run-final.ps1` L530-L547。

**代码片段原文**：

```powershell
if ($ui.ok -and ($ui.xml -match 'text="截图"')) {
    $btn = Tap-Node $ui.xml 'text="截图"'
    $capMode = 'UI 按钮'
}
if (-not $btn) {
    $bc = Invoke-Adb @('shell', 'am', 'broadcast', '-a', $script:LcDebugAction, '--es', 'cmd', 'capture')
    Start-Sleep -Milliseconds 1200
    $capMode = "cmd capture 广播"
}
Start-Sleep -Milliseconds 2500
$after = Get-AlbumList
$new = @($after | Where-Object { $before -notcontains $_ })
if ($new.Count -eq 0) {
    Assert3 'P2-01' 'BLOCKED' '一键留档生成文件' "未新增文件（方式：$capMode；dump 是否含「截图」按钮=$(if($ui.ok){$ui.xml -match 'text="截图"'}else{'dump失败'})）" $(Join-Path $OutDir 'ui-capture.xml')
```

**触发条件**：`$capMode='UI 按钮'`（按钮存在且已点击、或已走 `cmd capture` 广播），2.5s 后 `/sdcard/Pictures/LC/` 无新增 png。

**预期 vs 实际**：
- 预期：这是可判定的功能失败（入口存在、动作已触发、产物缺失）→ `FAIL`，退出码 1。AT 文档 `AT-P0-2-capture-record.md` L16 也把「证据缺」归 BLOCKED，但「动作已执行且无产物」是 FAIL 语义。
- 实际：一律记 `BLOCKED`（exit 2，无 FAIL），并把后续 P2-03/04/05/06 全部连带记 BLOCKED（L544-L547）——一次功能失败被记成「未测到」，`FAIL` 计数为 0。

**修复方向**：按 `$capMode` 分流：UI 按钮/广播已触发 → `FAIL`；dump 失败导致连按钮都没找到 → `BLOCKED`。连带项按同一根因标 `FAIL(根因 P2-01)` 或明确写「随 P2-01」。

---

### F-08（中，已证实）P2-05 只校验分享抽屉存在，不校验被分享文件与相册一致

**文件行号**：`tests/at/run-final.ps1` L583-L589。

**代码片段原文**：

```powershell
        $acts = (Invoke-Adb @('shell', 'dumpsys', 'activity', 'activities'))
        $chooser = ($acts | Select-String 'ResolverActivity|ChooserActivity|android/.ChooserActivity' | Select-Object -First 2) -join ' | '
        Save-LcScreenshot $DeviceId (Join-Path $OutDir 'share-sheet.png') | Out-Null
        Invoke-Adb @('shell', 'input', 'keyevent', '4') | Out-Null
        Start-Sleep -Milliseconds 600
        Assert3 'P2-05' $(if ($chooser) { 'PASS' } else { 'FAIL' }) '分享抽屉打开；被分享文件 = 相册同一 uri（代码同一 saveCompassShot→shareShot 路径）' "chooser=$([bool]$chooser) sha256=$sha" (Join-Path $OutDir 'share-sheet.png')
```

**触发条件**：任意能唤起系统分享面板的 App/页面（分享抽屉出现），即使分享的是另一张图或另一个文件。

**预期 vs 实际**：
- 预期：expected 写了两个条件——「分享抽屉打开」**且**「被分享文件 = 相册同一 uri」。
- 实际：只判 `$chooser` 非空；`$sha` 是相册文件的哈希（L558），与分享 Intent 的 `EXTRA_STREAM` 无任何关联，不能证明被分享的是文件；`dumpsys activity activities` 里出现 chooser 也只能证明面板开了。

**修复方向**：从 `dumpsys activity` 或 `logcat` 取 `Intent.EXTRA_STREAM` 的 uri，`ls -l` 比对该 uri 与 `Pictures/LC/<name>` 的 inode/size/sha；取不到 uri 时判 BLOCKED（证据缺失），不得仅凭 chooser 判 PASS。

---

### F-09（中，已证实）`PASS(等价路径)` 状态不进任何计数，汇总与退出码都看不见它

**文件行号**：`tests/at/run-final.ps1` L375-L379、L735-L742。

**代码片段原文**（L375-L379）：

```powershell
                    if ($null -ne $decl) {
                        $ok = ([math]::Abs($decl - 30.0) -lt 0.05)
                        Assert3 'RT-005-UI45' $(if ($ok) { 'PASS' } else { 'FAIL' }) 'clamp(45)=30 端到端（等价路径：cmd declination，非 UI 输入）' "prefs=$decl 读数=$r5°" $after.path
                        $rt005 = if ($ok) { 'PASS(等价路径)' } else { 'FAIL' }
                    }
```

（L735-L742）：

```powershell
$pass = ($script:LcResults | Where-Object status -eq 'PASS').Count
$fail = ($script:LcResults | Where-Object status -eq 'FAIL').Count
$blocked = ($script:LcResults | Where-Object status -eq 'BLOCKED').Count
$diag = ($script:LcResults | Where-Object status -eq 'DIAGNOSTIC').Count
Log "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag -> $notes"
if ($fail -gt 0) { exit 1 }
if ($blocked -gt 0) { exit 2 }
exit 0
```

**触发条件**：RT-005 的界面输入框写不进 `45`（`$typed` 不匹配 `^\s*45`），回退到 `cmd declination` 且 clamp 成功。

**预期 vs 实际**：
- 预期：每个用例状态 ∈ {PASS, FAIL, BLOCKED}（文档 `AT-final-round.md` L20 只定义了这三种 + 诊断）。用 debug 广播替代 UI 输入的用例，应以 `FAIL`/`BLOCKED` 明确处置，或新增独立用例 id。
- 实际：`Assert3` 写入的是 `PASS`（L377），但 `$rt005` 写 `PASS(等价路径)` 且**从未被读取**；若 `Assert3` 的那行被走到，则计入 PASS——而 expected 明说是「等价路径，非 UI 输入」，等于用非 UI 路径替 UI 用例判 PASS。反过来说，`table.tsv`/summary 里这一行与其它 PASS 无法区分，文档的「18/18」也就无法证明 RT-005 是走 UI 过的。基线运行记录不在仓库（Q-02），无法核对当轮到底走了哪条。

**修复方向**：`PASS(等价路径)` 改为独立 id（如 `RT-005-cmd`）且状态取 `BLOCKED`（UI 路径未验）；或保留 UI 路径必测、cmd 路径只作诊断（`DIAGNOSTIC`）。状态集合在 `Assert3` 里加白名单校验，出现集合外状态直接 `throw`。

---

### F-10（中，已证实）三份 AT 文档引用的 `tests\at\run-at.ps1` 在基线不存在

**文件行号**：`docs/qa/AT-P0-0-feature-switch.md` L51；`docs/qa/AT-P0-1-reading-trust.md` L48；`docs/qa/AT-P0-4-3d-probe.md` L53；反证：`git ls-files tests/at`（只有 2 个文件）。

**代码片段原文**（`docs/qa/AT-P0-1-reading-trust.md` L48）：

```markdown
> 执行器（LC测试 已落）：`pwsh -File tests\at\run-at.ps1 -Suite P0-1`，缺前置 exit 2。
```

（`git ls-files tests/at` 输出）：

```
tests/at/golden_readsheet.py
tests/at/run-final.ps1
```

**触发条件**：任何人按 AT 文档执行 `pwsh -File tests\at\run-at.ps1 -Suite P0-1`（或 P0-0 / P0-4）。

**预期 vs 实际**：
- 预期：文档承诺的执行器存在且支持 `-Suite P0-0/P0-1/P0-4`，缺前置 `exit 2`。
- 实际：文件不存在，命令直接报「找不到路径」；基线唯一的 AT 执行器是 `run-final.ps1`，参数里没有 `-Suite`（L29-L37 只有 `-Apk/-Device/-OutDir/-KeepData/-SkipTd016/-ReadSheetHeading`）。三份文档的「LC测试 已落」不成立。
- 归属说明：事实落在文档（G）与测试资产（F）交界；本条只主张「测试资产缺口：无分套件执行器」，建议母条由 G 报告出，本条降为交叉引用。

**修复方向**：补 `tests/at/run-at.ps1`（静态前置扫描 + `-Suite` 分派 + 缺前置 `exit 2`），或把三份文档改成 `run-final.ps1` 的真实用法并删掉不存在的执行器承诺。

---

### F-11（低，已证实）圈层常量用子串存在性断言，「注释里写个 5.625」即可通过

**文件行号**：`tests/numeric/test_mechanics.py` L112-L121。

**代码片段原文**：

```python
    def test_ring_constants_present_in_both_ends(self):
        """64 卦 5.625°、60 龙 offset 1.5 必须两端都在（数值同源的最低要求）。"""
        for text, label in ((self.rs, "main.rs"), (self.java, "CompassData.java")):
            self.assertIn("5.625", text, label)
            self.assertIn("1.5", text, label)
        self.assertIn("HEXAGRAM64_DEG = 5.625f", self.java)
```

**触发条件**：`main.rs`/`CompassData.java` 里 `HEXAGRAM64_DEG` 的值被改错，但任意注释/字符串仍含 `5.625`（该文件本来就大量出现 5.625，例如 `main.rs` 的 `RINGS` 文字、注释）。

**预期 vs 实际**：
- 预期：断言两端的**常量定义**等于 `mechanics.json` 的 `hexagram64_interval` 与 `TYPE_7.offset`。
- 实际：`assertIn("5.625", ...)` 对整份源码做子串匹配，几乎恒真；只有 Java 那行带 `= 5.625f` 略强，但仍可被注释满足。

**修复方向**：用正则锚定声明（`const HEXAGRAM64_STEP: f32 = 5.625;` / `float HEXAGRAM64_DEG = 5.625f;`），或直接解析常量值与 json 比较；删掉恒真断言。

---

### F-12（低，已证实）文档里的套件用例数（83 / 74）与实测 86 不一致

**文件行号**：`docs/qa/AT-P0-1-reading-trust.md` L83；`docs/qa/baseline-01.md` L31；`docs/qa/bugs.md` L23；实测输出见本报告 §10。

**代码片段原文**（`AT-P0-1-reading-trust.md` L83 表格单元）：

```markdown
| RT-011 | 数值红线 | `python tests/numeric/tools/check_mechanics.py`；`python tests/numeric/run_all.py`；`git diff --stat app/mechanics.json` | 19 项有来源 0 估计；套件全绿（**当前 83 PASS**）；mechanics 0 改动；三盘取值只来自 `CompassData`/`plate()`，P0-1 **不得**引入第三套偏移 | 三条命令输出 |
```

（`docs/qa/baseline-01.md` L31）：

```markdown
| 数值边界单测 | ✅ 74 项全绿（含 5 条已知缺陷 xfail） | `python tests\numeric\run_all.py` → `RESULT: PASS` |
```

**触发条件**：按文档核对当前套件规模。

**预期 vs 实际**：
- 预期：文档的「当前 N PASS」= 当前 `testsRun`。
- 实际：本机 `tests=86`；`AT-P0-1` 写 83、`baseline-01`/`bugs.md` 写 74 且称「含 5 条已知缺陷 xfail」（基线 0 条 xfail）。三处口径互不一致，读者无法用文档判断新用例是否被计入。
- 归属：母条建议 G（文档版本错位），本条只作测试侧交叉引用。

**修复方向**：文档改为不写死数字，引用 `run_all.py` 末行计数；或每次改测试时同步更新这三处。

---

### F-13（低，已证实）基线冻结脚本无条件声明「本基线含 disc-dial-list.png」，即使没采到

**文件行号**：`tests/numeric/tools/freeze_android_baseline.ps1` L70-L80、L128。

**代码片段原文**（L70-L80）：

```powershell
$node = FindNodeCenter $xml "盘面"
if ($node) {
    & $adb -s $Device shell input tap $node.x $node.y
    Start-Sleep -Milliseconds 1200
    Shot "disc-dial-list.png"
    $null = DumpUi "ui-dial-list.xml"
    & $adb -s $Device shell input keyevent 4
    Start-Sleep -Milliseconds 800
} else {
    Write-Warning "未找到「盘面」入口，跳过盘面列表截图"
}
```

（L128）：

```markdown
- 说明：D5 起底部「5 个盘面按钮」换成「盘面（4 组 · 常用 5）」入口，本基线含 ``disc-dial-list.png``。
```

**触发条件**：`FindNodeCenter $xml "盘面"` 返回 `$null`（入口文案改了、dump 打到了别的页）。

**预期 vs 实际**：
- 预期：`manifest.md` 的「冻结物」只列实际生成的文件（L130-L132 已按目录列举，L128 却写死）；入口缺失时脚本应失败或把缺失写进 manifest。
- 实际：只 `Write-Warning`，脚本继续、`exit 0`（L144 只判 fatal/anr/pid），manifest L128 仍断言「本基线含 `disc-dial-list.png`」。依赖该基线做布局证据的人会以为文件存在。

**修复方向**：把 L128 改为条件句（`if (Test-Path (Join-Path $OutDir 'disc-dial-list.png'))`），入口缺失时 `exit 1` 或写入 manifest 的「缺失」段。

---

### F-14（低，已证实）`at_p0_0_check.ps1` 计算了「磁北/真北」行却不参与判定

**文件行号**：`tests/numeric/tools/at_p0_0_check.ps1` L110-L124。

**代码片段原文**：

```powershell
$hasDecl = ($texts -match "偏角").Count -gt 0
$hasTrueNorth = ($texts -match "磁北").Count -gt 0 -or ($texts -match "真北").Count -gt 0

# 4) 判定
$failures = @()
if ($dumpOk) {
    Add-Content -Encoding UTF8 $result "ui rows: 偏角=$hasDecl 磁北/真北=$hasTrueNorth 读数=[$reading] (dump OK)"
    if ($reading -match '([\d.]+)') {
        $rv = [double]$Matches[1]
        if ([math]::Abs($rv - $Heading) -gt 0.5) {
            Add-Content -Encoding UTF8 $result "WARN 读数 $rv 与注入 $Heading 不一致（注入可能未生效，结论需人工复核）"
        }
    }
    if ($enabled -and -not $hasDecl) { $failures += "开关=on 但缺「偏角」行" }
    if (-not $enabled -and $hasDecl) { $failures += "开关=off 但仍显示「偏角」行" }
}
```

**触发条件**：P0-1 开关打开但「磁北/真北」行缺失（或开关关闭但仍显示）。

**预期 vs 实际**：
- 预期：`AT-P0-0` 的 ① 是「偏角/磁北行存在性」（脚本头 L3 原文），两行都该判。
- 实际：`$hasTrueNorth` 只写日志，`$failures` 只看 `$hasDecl`；读数偏差也只用 `WARN`（L119-L121），不改判。

**修复方向**：`$failures` 增加 `if ($enabled -and -not $hasTrueNorth) { … }` 与关闭态的反向判定；读数偏差超过 0.5° 时在行存在性仍成立的前提下明确标 `BLOCKED(注入未生效)`，不要只 WARN。

---

### F-15（低，已证实）两处注释与基线事实不符（TYPE_9「推定」、JIAZI「xfail 锁住」）

**文件行号**：`tests/numeric/test_plates.py` L65-L72、L147-L149；`app/mechanics.json` L993。

**代码片段原文**（`test_plates.py` L71-L72）：

```python
      注：TYPE_9 的文本名单顺序 MECHANICS.md 未导出，此处按「天盘 +7.5°」的
      知识库方位反推，属推定口径（DEX offset=0.0 已知）。
```

（`app/mechanics.json` TYPE_9 的 `labels_status` 与 `labels_source`，L993 邻域）：

```json
      "labels_status": "confirmed_dex",
```

**触发条件**：读者按注释判断 TYPE_9 名单可信度，或按 L148-L149 寻找「锁住 JIAZI 的 xfail」。

**预期 vs 实际**：
- 预期：注释反映基线事实。
- 实际：TYPE_9 的 `labels_source` 已写明 `getTextList() packed-switch key 9 → :pswitch_999，smali 第 4332-4491 行`逐条还原，`labels_status=confirmed_dex`——「推定口径」已过期；`L148-L149` 所指的 xfail 在基线 0 条（见 §2①）。两处注释会误导后续审查把已取证项当推定项、把已修项当被锁项。

**修复方向**：删/改这两处注释；`test_plates.py` L75-L79 的等价性用例改为引用 `labels_source` 行号。

---

## 5 待确认清单（Q）

### Q-01 780 条圈层文字的 DEX 证据链不在基线

- **线索**：`app/mechanics.json` 每条 ring 的 `labels_status: "confirmed_dex"` + `labels_source`（例：`TYPE_7` 在 mechanics.json L543-L545：`"壬子 庚子"` / `smali 第 4652-5135 行（mem_dump/classes06.dex）`）；`test_mechanics.py` L149-L159 只断言该自填字符串。
- **已掌握证据**：基线内 `investigation.cany/evidence/19/ev_19d82…json` 与 `…/05/ev_05803e…json` 是 `getTextList` 的 offset 0（250 条）与 offset 1500（300 条）两段，覆盖不到 TYPE_7 分支；`docs/FINAL-REPORT.md` L20 指向 `.inspect/derived/ring_label_addrs.json`，该文件在 `E:\APPDetector\output` 下，按纪律未读。
- **缺什么证据**：能覆盖 TYPE_4/5/6/7/14 的 `getTextList` const-string 清单（`.inspect/derived/ring_label_addrs.json` 或对应 smali 段）。
- **由谁补**：LC审证据 / LC逆向。
- **补齐后的判据**：抽 4 个已声明向量（60龙 0°/170°、72龙 0°、120分金 0°）逐字一致 → PASS；任一处不一致 → 该 ring 的 `labels` 与 4 向量断言作废，F-02 升级为阻断。

### Q-02 「18/18 PASS、0 BLOCKED」没有可复现的运行记录

- **线索**：`docs/FINAL-REPORT.md` L11、`docs/qa/AT-final-round.md` L28、`docs/qa/bugs.md` L262 均写 18 PASS / 0 FAIL / 0 BLOCKED / exit 0。
- **已掌握证据**：`tests/at/run-final.ps1` L740-L742 的退出码与 L735-L739 的计数逻辑可用；但基线内没有 `checks/at-final/<ts>/table.tsv`、`summary.txt`、`notes.md`（`git ls-files checks` 为空，`checks/` 未提交），且 `PASS(等价路径)` 不进计数（F-09）。
- **缺什么证据**：当轮的 `table.tsv`（19 行状态）+ `summary.txt`（含 APK sha256、HEAD、设备）+ `notes.md`。
- **由谁补**：LC审证据。
- **补齐后的判据**：19 行状态与文档「18 PASS + 1 DIAG」逐行一致 → PASS；出现 BLOCKED/`PASS(等价路径)`/未识别状态 → 文档的「0 BLOCKED」判 FAIL。

### Q-03 原版 APK 在 7.5° 时「正兼向」行显示什么

- **线索**：B-10（F-03 交叉引用）已证同一 App 内两处文字互斥（画布「兼向子」vs 面板「癸山九度内」），但哪一端符合原版未定。
- **已掌握证据**：`mechanics.json` TYPE_14 `offset=0.0` + 癸起名单，逐点对应**人盘**（0°→癸、15°→丑、94.04°→乙、180°→丁）；`MECHANICS.md` L183-L188 写「九度内 = 正向」；原版该 ring 的绘制半径/是否显示中心文字未在基线档案中。
- **缺什么证据**：原版 APK 在 7.5° / 15° 的真机截图（或 DEX 中 `CompassView` 对应绘制方法的反编译），确认原版是否有独立「正向/兼向」文字、其口径是哪一盘的。
- **由谁补**：LC审证据 / LC逆向。
- **补齐后的判据**：原版 7.5° 显示「癸山九度内」→ 删/改 LC 的 `rd.jian` 口径；原版显示「兼向子」→ 读盘第 14 行改地盘口径。两种结果对应不同修复方向。

---

## 6 风险与改进建议（R，不进缺陷清单）

### R-01（P1）86 条用例中 37 条只测 oracle 自身，不触达被测源码

统计（按文件用例数）：`test_formula` 16、`test_rings` 7、`test_plates` 14 = **37 条**只对 `oracle.py`/`MECHANICS.md` 求值，不读 `app/` 源码；`test_mechanics` 20、`test_b2_golden` 12、`test_ends_parity` 9、`test_divergences` 5 触达源码；`test_sensor_math` 3 走 javac。oracle 自身自洽有价值，但 43% 的绿色与「被测实现」无关，README L24-L42 的「覆盖清单」未区分这两类。

**建议**：覆盖清单加一列「触达源码（是/否）」；把 oracle-only 用例单独成组，套件输出分 `oracle_self=37 / impl=46 / java=3` 三类计数。

### R-02（P1）跨方位一致性只有 40 个航向 + 4 个文字向量，无全周/全格扫描

见 §2⑤。实现侧 `ring_index` 的 14×格数（8+8+8+24+72+120+60+24+24+120+64+64+64+120 = 780 格）没有任何一条用例遍历；绘制角 `ring_center` 无执行级断言。

**建议**：加 `test_sweep.py`：14 圈层逐格（780 次）用实现公式求值 vs `oracle.dex_index`；航向按 0.5° 步长全周（720 点）× 三盘/分金/龙，与 oracle 对拍；AT 读盘至少在 0°/7.5°/94.04°/352.5° 四个航向各跑一次。

### R-03（P2）把 `expectedFailure` 定为惯例、`run_all` 定义「含 xfail = 全绿」

`tests/numeric/README.md` L60 与 `run_all.py` L7。当前 0 条 xfail，风险在流程：缺陷被 `expectedFailure` 锁住后套件长期绿，只有「意外成功」才会提醒。建议 `run_all.py` 对 `expectedFailures>0` 打印醒目清单并要求 `--allow-xfail` 才返回 0。

### R-04（P2）`source_parse.rust_let_expr` 全文件按名字取首个 `let`，无函数作用域

`source_parse.py` L55-L60；`main.rs` 现存两个 `let m =`（L241、L387）。当前按顺序命中读数函数，但约束是隐式的。建议 `rust_let_expr` 接受 `fn_name`，先 `fn_body` 再在函数体内搜 `let`，把「取错函数」变成结构性失败。

---

## 7 未覆盖范围声明

| 范围 | 状态 | 原因 | 补做安排 |
|---|---|---|---|
| M5 22 文件 | **已覆盖（22/22 逐行读）** | 见 §8 | — |
| 真机 AT 实跑（`run-final.ps1` / `at_p0_0_check.ps1` / `freeze_android_baseline.ps1`） | **未覆盖（未执行）** | 本机无连接设备、无 `app-debug.apk`；脚本硬编码设备序列号 `L2E0222531014833` | 有设备时按 `AT-final-round.md` 跑，重点复验 F-05～F-09 |
| `cargo test`（`main.rs` L637-L674 的 `mechanics_pin`） | **未覆盖（未执行）** | 仓库无 Cargo 工程上下文/依赖缓存；`run_all.py` 与 AT 均不调用 | 由 LC审客户端/构建环境执行，核对 F-02 |
| `img_diff.py` 实跑 | **未覆盖** | 需两张 1228×2700 PNG；基线内 `docs/qa/baseline-android/**` 未纳入本次取证 | 有截图后补跑，确认硬编码 `0 1850 1228 2500` 的裁片坐标 |
| `docs/qa/**` 全文 | **部分覆盖（按需引用）** | 只读了与 AT 一致性相关的行（AT-P0-0 L51、AT-P0-1 L48/L83、AT-P0-4 L53、AT-final-round L20/L27-L28、bugs.md L262） | 全文归 M6（LC审文档） |
| `app/**` 业务源码 | **仅取证片段** | 为 B-10（F-03）/F-04 读了 `CompassData.java` L205-L254、`CompassView.java` L160-L313、`MainActivity.java` L625-L669、`CompassReadSheet.java` L40-L89、`main.rs` L232-L261/L383-L452/L637-L699 | 整文归 M1/M2（LC审客户端） |
| `E:\APPDetector\output`（含 `.inspect/derived/ring_label_addrs.json`） | **未读（按纪律禁止）** | 脏树/基线外 | 证据链由 LC审证据按 Q-01 补 |
| 开发分支 / 未提交改动 | **不在范围** | 基线为唯一有效版本 | — |

---

## 8 已读文件清单（22/22，行数按基线 `wc -l` 同口径）

| # | 文件（相对 `review-base`） | 行数 | 结论 |
|---|---|---|---|
| 1 | `tests/at/golden_readsheet.py` | 100 | 读盘 golden 由 `mechanics.json` 生成；`jfmt1` HALF_UP 与 Java 对齐；无独立证据 |
| 2 | `tests/at/run-final.ps1` | 743 | **20 个用例 ID**（`ENV`+19 个；全通过时 19 行 = 18 PASS + 1 DIAG；初稿写「19 个 `Assert3` 入口」，§12.1 C-2 已更正）；F-05～F-09、F-13 证据 |
| 3 | `tests/numeric/.gitignore` | 2 | 仅忽略 pycache |
| 4 | `tests/numeric/java/CompassProbe.java` | 45 | 直接调 `CompassData.read`，输出 CSV；无问题 |
| 5 | `tests/numeric/java/SensorMathProbe.java` | 27 | 三行 ASCII 输出；无问题 |
| 6 | `tests/numeric/oracle.py` | 246 | 公式/表参照；L191 `d60name=JIAZI` 见 F-04；自带 assert 在 `python -O` 下失效（低，未单列） |
| 7 | `tests/numeric/README.md` | 80 | L42「780 逐条一致」口径放大；L60 xfail 惯例 → R-03 |
| 8 | `tests/numeric/run_all.py` | 39 | F-01 主证据 |
| 9 | `tests/numeric/source_parse.py` | 198 | 结构漂移会 raise；语义漂移见 F-02；R-04 |
| 10 | `tests/numeric/test_b2_golden.py` | 132 | F-02 主证据；F-03 交叉引用证据 |
| 11 | `tests/numeric/test_divergences.py` | 62 | 三盘回归正常；L5-L7 自述不变量删除 |
| 12 | `tests/numeric/test_ends_parity.py` | 159 | F-01、F-04 主证据 |
| 13 | `tests/numeric/test_formula.py` | 163 | oracle 自洽；L124 锁 `癸亥` 见 F-04 |
| 14 | `tests/numeric/test_mechanics.py` | 187 | F-11；L157 只查自填 `labels_status` |
| 15 | `tests/numeric/test_plates.py` | 155 | F-15；正兼向仅地盘口径 |
| 16 | `tests/numeric/test_rings.py` | 87 | 14 圈层几何 vs MECHANICS.md（同源文档），无实现求值 |
| 17 | `tests/numeric/test_sensor_math.py` | 98 | F-01 跳过证据；3 条 JVM 断言本机通过 |
| 18 | `tests/numeric/tools/at_p0_0_check.ps1` | 160 | F-05、F-14 |
| 19 | `tests/numeric/tools/check_mechanics.py` | 44 | 字符串黑名单「0 估计」，不能验来源真伪 |
| 20 | `tests/numeric/tools/dump_readings.py` | 54 | 证据收集脚本；`exprs[0]/[1]` 顺序依赖同 `test_divergences` |
| 21 | `tests/numeric/tools/freeze_android_baseline.ps1` | 146 | F-13 |
| 22 | `tests/numeric/tools/img_diff.py` | 47 | 区域 diff；未实跑 |

合计 **2974 行**，与 `docs/review/00-MATRIX.md` M5 行一致。

---

## 9 取证性阅读（非 M5，仅引用，未做整文审查）

| 文件 | 读到的区间 | 用途 |
|---|---|---|
| `app/src/main.rs` | L232-L261、L383-L452、L637-L699 | 读数表达式、`ring_index/ring_text/ring_center`、`mechanics_pin` 向量 |
| `app/android/.../CompassData.java` | L205-L254（另 grep L181-L240） | F-03/F-04：`jian`/`jianText`/`dragon60Name` |
| `app/android/.../CompassView.java` | L160-L313 | F-03：中心文字用 `rd.jian`，且在所有分支都绘制 |
| `app/android/.../MainActivity.java` | L625-L669 | F-03：面板用 `r.jianText` |
| `app/android/.../CompassReadSheet.java` | L40-L89 | 读盘 14 行格式与 golden 对齐 |
| `tools/mechanics/gen-mechanics.ps1` | 全文 279 | F-02：`mechanics.json` 是唯一手改源，生成两端 |
| `app/mechanics.json` | TYPE_7 / TYPE_14 对象与 labels | F-03/F-04/Q-01 |
| `docs/review/00-MATRIX.md`、`98-report-conventions.md` | M5 行、格式规范 | 行数/格式口径 |

---

## 10 复核命令与实验记录

### 10.1 套件全绿（本机）

```
PS> cd E:\APPDetector\review-base
PS> python tests\numeric\run_all.py
...
Ran 86 tests in 1.655s

OK
tests=86 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
RESULT: PASS
```

### 10.2 无 JDK 复现（F-01，未改仓库，运行时把 `JAVA_CANDIDATES` 置空 + `shutil.which` 返回 None）

```
NO_JDK: testsRun=77 skipped=3 failures=0 errors=0 wasSuccessful=True
NO_JDK: run_all.py would print RESULT: PASS
  skip: setUpClass (test_ends_parity.JavaRealCodeTest) | 未找到 JDK（javac），跳过 Java 真机代码对拍
  skip: test_ends_parity.RustSourceTest.test_matches_java_earth_readings | Java 行未就绪
  skip: setUpClass (test_sensor_math.SensorMathJvmTest) | 未找到 JDK（javac），跳过 SensorMath JVM 断言
```

（`testsRun` 86→77 = 9 条未执行；`skipped=3` 是 3 条 skip 记录，见 F-01 说明。）

### 10.3 变异实验（F-02，仅内存中替换字符串，未写回源文件）

```
MUT: ring_index rewritten to a wrong formula; token assertions pass = True
MUT: real index=59 label='壬子 庚子' ; mutated index=0 label='壬子 壬子'
MUT+FULL: testsRun=86 failures=0 errors=0 skipped=0 wasSuccessful=True
MUT+FULL: run_all.py would print RESULT: PASS
```

### 10.4 正/兼向两链路复算（B-10 / F-03）

```
h=  0.00 地盘m= 0(子) off=+0.00 jian=正向 | ring14 idx=  0 label=癸山九度外 | 人盘=癸
h=  4.50 地盘m= 0(子) off=+4.50 jian=正向 | ring14 idx=  1 label=癸山九度内 | 人盘=癸
h=  7.50 地盘m= 1(癸) off=-7.50 jian=兼向子 | ring14 idx=  2 label=癸山九度内 | 人盘=癸
h= 15.00 地盘m= 1(癸) off=+0.00 jian=正向 | ring14 idx=  5 label=丑山九度外 | 人盘=丑
h= 94.04 地盘m= 6(卯) off=+4.04 jian=正向 | ring14 idx= 31 label=乙山九度内 | 人盘=乙
h=180.00 地盘m=12(午) off=+0.00 jian=正向 | ring14 idx= 60 label=丁山九度外 | 人盘=丁
```

### 10.5 基线核验

```
$ git rev-parse HEAD
d752efaf7936f887a70577681c703497e74e8b57
$ git tag --points-at HEAD
review-20261105
$ git status --porcelain
?? docs/review/
$ git ls-files tests/at tests/numeric | wc -l
22
```

---

## 11 第二批交付：文件级覆盖、部分读取与必查项缺口

### 11.1 M5 文件级覆盖（22/22，逐行读完）

- `tests/numeric/**` 20 文件 + `tests/at/**` 2 文件，**22/22 全部从第 1 行读到末行**，行数见 §8，合计 2974 行（与 `00-MATRIX.md` M5 合计一致）。
- **M5 内没有「读了一半」的文件**，也没有「未涉及」的文件；因此 M5 的「未覆盖」只发生在「未执行」维度（§7）：
  - 真机 AT 未跑（无设备/APK）；
  - `cargo test` 未跑（只读工作区不生成 `target/`）；
  - `img_diff.py` 未实跑（无成对 PNG）。
- 本次未改任何源码：`git status --porcelain` 仅 `?? docs/review/`。

### 11.2 M5 之外为取证读取的文件（部分读取，逐条给读了几行与原因）

| 文件（相对 review-base） | 总行数 | 本次读到 | 为什么没读完 | 归属 |
|---|---|---|---|---|
| `app/src/main.rs` | 701 | L232-L261、L383-L452 通读（100 行）；L637-L699 仅 grep 命中 | 只取证读数表达式、`ring_index/ring_text/ring_center`、`mechanics_pin` 向量；整文归 M1 | M1 |
| `app/android/.../CompassData.java` | 280 | L205-L254 通读（50 行）；其余仅 grep 命中 | 只取证 `jian/jianText/dragon60Name` 与 `ringIndex/ringText`；整文归 M2 | M2 |
| `app/android/.../CompassView.java` | 358 | L160-L313 通读（154 行） | 只取时刻度、中心读数、高亮；其余绘制分支未读，整文归 M2 | M2 |
| `app/android/.../MainActivity.java` | 767 | L625-L669 通读（45 行） | 只取证面板文案与读数拼接；整文归 M2 | M2 |
| `app/android/.../CompassReadSheet.java` | 95 | L40-L89 通读（50 行） | 只取证读盘 14 行/顶部两行格式；头 39 行未读 | M2 |
| `tools/mechanics/gen-mechanics.ps1` | 279 | 全文（279 行） | — | 构建 |
| `app/mechanics.json` | 1605 | L986-L1001 人工读（16 行）；`values/rings/labels` 经 Python 全量加载校验 | 人工只取证 TYPE_14/TYPE_7 两个对象；1605 行不逐行抄读，数据侧由脚本全量解析 | M7 |
| `investigation.cany/evidence/19/ev_19d82….json` | 1575 | 只解析 JSON 结构 + `result` 键（未逐行） | 只用于确认证据链深度（`getTextList` 250 条指令段）；完整取证归 M8/LC审证据 | M8 |
| `docs/qa/AT-P0-1-reading-trust.md` | 115 | L45-L50、L81-L84（10 行） | 只取证执行器承诺与 RT-011 判据；全文归 M6 | M6 |
| `docs/qa/AT-P0-0-feature-switch.md` | 139 | L49-L53（5 行） | 只取证执行器承诺 | M6 |
| `docs/qa/AT-P0-4-3d-probe.md` | 117 | L51-L55（5 行） | 只取证执行器承诺 | M6 |
| `docs/qa/AT-final-round.md` | 71 | 仅 grep 命中 L20、L27-L28（3 行） | 只取证「18/18、exit 2」口径 | M6 |
| `docs/qa/baseline-01.md` | 151 | 仅 grep 命中 L31（1 行） | 只取证「74 项」口径 | M6 |
| `docs/qa/bugs.md` | 287 | 仅 grep 命中 L23、L157-L166、L188、L262（12 行） | 只取证 74 项/60 龙旧值/xfail 口径 | M6 |
| `docs/FINAL-REPORT.md` | 105 | 仅 grep 命中 L4、L11、L20（3 行） | 只取证 18/18 与 780 条证据链引用 | M6 |
| `docs/review/00-MATRIX.md` | 253 | 只读 M5 相关行 L22、L29、L35、L101-L122（28 行） | 只核对 M5 文件数与行数 | 本组 |
| `docs/review/98-report-conventions.md` | 432 | 全文（432 行） | — | 本组 |

上表里没有「未涉及」的说法：每个文件都给出实际读到的行区间与未读原因；未逐行读的文件一律不进入 §3/§4 的结论，只作交叉引用。

### 11.3 必查项缺口（未闭环项、缺什么、谁补、补齐判据）

| 必查项 | 当前结论 | 缺口 | 谁补 | 补齐后的判据 |
|---|---|---|---|---|
| ① xfail/skip 掩盖 | 部分达标（0 xfail；4 处 skip 已定位） | F-01 的修复未做双环境回归（有/无 JDK） | LC测试（本轮后） | 无 JDK 时 `run_all` 非 0 且 `RT-011-numeric` 非 PASS；有 JDK 时仍 86/86 |
| ② source_parse 脆弱性 | 部分达标（结构漂移会 raise；语义漂移静默） | R-04：`rust_let_expr` 无函数作用域；F-02：token 级断言 | LC测试（改测试资产） | 变异 `ring_index` 后套件必须变红；同名 `let` 混入后不误取 |
| ③ AT BLOCKED 当 PASS | 部分达标（退出码正确；4 条假 PASS 路径） | F-05～F-09 仅代码路径复现，**未在真机触发** | LC测试（有设备时） | 构造 prefs 缺失 → 判 BLOCKED；构造无产出 → 判 FAIL；`PASS(等价路径)` 不进 PASS |
| ④ golden 来源 | 未达标（生成器自证 + 4 向量） | Q-01：`.inspect/derived/ring_label_addrs.json` 在基线外 | LC审证据 / LC逆向 | 4 向量与 dex const-string 逐字一致 → PASS；否则 F-02 升级阻断 |
| ⑤ 跨方位一致性 | 未达标（41 航向、4 文字向量） | R-02：无 780 格/全周扫描；`ring_center` 无执行级断言 | LC测试（新增 `test_sweep.py`） | 14 圈层 780 格 + 720 航向与 oracle 全等 |
| 18/18 可复现 | 无法从基线复核 | Q-02：`checks/at-final/<ts>/` 未入库 | LC审证据 | `table.tsv` 19 行与文档「18 PASS + 1 DIAG」逐行一致 |

### 11.4 第二批条目确认

- **F-04～F-15 共 12 条（中 7 / 低 5），全部「已证实」**，字段齐全（路径+行号+原文+触发条件+预期vs实际+修复方向），已在 §4 落盘。
- 母条归属：**F-03 已转交叉引用（母条 B-10，高）**；F-10、F-12 建议母条归 G（M6 文档），M5 只主张「测试资产缺口」。
- 本批未新增文件/行号引用；所有引用的行号均落在基线文件总行数内（本地机器核对 35 处引用，全部命中）。

---

## 12 第三批：复核补遗（F-16～F-19）+ 数值修正

> 触发：技术总监 2026-11-05 要求「回到 `E:\APPDetector\review-base` 重新确认行号」。本轮把 §3/§4 的 15 条发现的 60 处行号引用逐条回基线复核（结果见 §12.2），并用三组实测（Python 3.13.14 / JDK 17.0.21+9 / 4 万点数值扫描）发现 4 条新问题。本轮**只改本报告文件**，未触碰任何源码（`git status` 仅本文件）。

### 12.1 三处数值修正

**C-1（更正）** `oracle.CORPUS` 是 **40** 个航向，不是 41。实测：

```
CORPUS len = 40
unique = 40
```

受影响位置：§2⑤ 与 R-02（已就地改为 40 并留痕）。§2⑤「41 个」是初稿计数错误，结论方向不变（40 点仍非全周扫描）。

**C-2（更正）** `run-final.ps1` 的用例编号是 **20 个 ID**，不是「19 个 `Assert3` 入口」。实测 `Assert3` 共有 44 行/45 个调用点（含函数定义行、以及 L506 一行两个调用），去重后 ID 为：

```
ENV, P2-01, P2-03, P2-04, P2-05, P2-06, P2-06-vis, P2-07, P2-08,
RT-005-UI45, RT-011-diff, RT-011-mechanics, RT-011-numeric,
SW-006-off-1, SW-006-off-2, SW-006-off-3, SW-006-off-3pix, SW-006-on-1, SW-006-on-2, TD-016
```

全通过时输出 19 行（18 PASS + 1 DIAG），与 `docs/qa/AT-final-round.md` L28 的「18 PASS / 0 FAIL / 0 BLOCKED / 1 诊断」自洽；初稿把「入口」与「ID」混写，已改。

**C-3（口径说明，非错误）** §8 的行号/总行数是 `wc -l` 口径（文件末尾无换行时按实际行数计），22 文件合计 2974，与 `00-MATRIX.md` 的 M5 合计一致。若用 PowerShell 复核，注意 `Get-Content <f> | Measure-Object -Line` **会跳过空行**（`run-final.ps1` 得 681、`oracle.py` 得 192、22 文件合计 2546），与本报告口径不同；两者都对，但引用行号时请沿用 `wc -l`/`(Get-Content $f).Count` 口径。

### 12.2 行号复核记录（抽检 21 处，全部命中）

| 引用位置 | 断言内容 | 复核结果 |
|---|---|---|
| `run_all.py` L31-L35 | `wasSuccessful()` 判绿、返回 0/1 | ✅ 原文一致 |
| `test_ends_parity.py` L44-L47 / L145-L147 | 无 JDK → `SkipTest`；派生跳过 | ✅ |
| `test_sensor_math.py` L46-L51 | 文件缺失/无 JDK → 跳过 | ✅ |
| `run-final.ps1` L698 | `failures=0` + `PASS` 双正则 | ✅ |
| `run-final.ps1` L740-L742 | `FAIL→1 / BLOCKED→2 / 其余→0` | ✅ |
| `run-final.ps1` L306-L311 | 缺 prefs 时 `$on = $true` | ✅ |
| `run-final.ps1` L490-L501 | P2-08 宽松回退 + 两布尔同源 | ✅ |
| `run-final.ps1` L530-L547 | 无产物判 BLOCKED 并连带 4 项 | ✅ |
| `run-final.ps1` L583-L589 | 只判 chooser 存在 | ✅ |
| `run-final.ps1` L375-L379 | `PASS(等价路径)` 未进计数 | ✅ |
| `at_p0_0_check.ps1` L73-L78 | prefs 缺失默认 true | ✅ |
| `at_p0_0_check.ps1` L110-L124 | `$hasTrueNorth` 不参与判定 | ✅ |
| `at_p0_0_check.ps1` L144-L157 | 1/2/0 退出码 | ✅ |
| `test_b2_golden.py` L90-L97 / L116-L121 | token 断言 / 向量字符串存在性 | ✅ |
| `oracle.py` L191 | `d60name = JIAZI[dragon60]` | ✅ |
| `test_formula.py` L124 | 硬写 `JIAZI[59] == "癸亥"` | ✅ |
| `test_mechanics.py` L112-L121 / L157 | 子串断言 / 自填 `labels_status` | ✅ |
| `freeze_android_baseline.ps1` L70-L80、L128 | 无条件声明含 `disc-dial-list.png` | ✅ |
| `main.rs` L241/L387、L643-L650、L671-L674 | 两个 `let m`、常量与四向量 `assert_eq!` | ✅ |
| `CompassData.java` L221、`CompassReadSheet.java` L27-L29/L47-L49 | `JIAZI[dragon60]`、`%.1f` 头部两行 | ✅ |
| AT 文档三处 `run-at.ps1` 承诺（L51/L48/L53） | `git ls-files tests/at` 仅 2 个文件 | ✅ |

### F-16（低，已证实）读盘 golden 的 `%.1f` 按十进制 HALF_UP，与 Java `float` 格式化不等价（4% 航向会算错期望值）

**文件行号**：`tests/at/golden_readsheet.py` L22-L24；`app/android/app/src/main/java/com/luopan/fengshui/CompassReadSheet.java` L27-L29、L47-L49；`tests/at/run-final.ps1` L490-L501。

**代码片段原文**（`golden_readsheet.py` L22-L24）：

```python
def jfmt1(x):
    """Java String.format("%.1f") 是 HALF_UP，python round 是 half-even，这里对齐 Java。"""
    return str(Decimal(repr(float(x))).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))
```

（`CompassReadSheet.java` L27-L29、L47-L49）：

```java
public static void show(Activity activity, float heading) {
    float h = SensorMath.normalize(heading);
    float back = SensorMath.normalize(heading + 180f);
...
    head.setText(String.format(java.util.Locale.US,
            "向北 %.1f°    坐南 %.1f°\n坐%s山  向%s山",
```

**触发条件**：`run-final.ps1 -ReadSheetHeading <h>` 传入一个十进制末位为 `x5` 的航向（如 `1.15`、`1.05`、`0.35`）。`jfmt1` 用 `Decimal(repr(float(x)))`——取**十进制字面值**再 HALF_UP；Java 用 `String.format("%.1f", float)`——先按 float32 取实际二进制值再 HALF_UP。二者在 `1.15f`（实际 1.14999997615814208984375）这类值上方向相反。

**预期 vs 实际**（本机 JDK 17 实测 + golden 实跑）：

| 航向 | Java `%.1f` | golden `head1` |
|---|---|---|
| `1.15` | `1.1` | `向北 1.2°    坐南 181.2°` |
| `1.05` | `1.0` | `向北 1.1°    坐南 181.1°` |
| `94.04`（默认） | `94.0` | `向北 94.0°    坐南 274.0°`（一致） |

0.01° 网格全扫：**1440 / 36000 个航向（4.0%）** golden 与 Java 不一致（口径：**仅「向北」单值、字面值 repr**；若按 App 顶部两行算为 **2267**，两者都对、口径不同，见 §13.4 C-4）；首个不一致点是 `0.35`（golden `0.4`，Java `0.3`），前 10 个为 `0.35/0.45/0.65/0.95/1.05/1.15/1.55/1.65/2.05/2.35`。

**影响与现状**：当前被 F-06 的宽松回退掩盖——`P2-08` 先按整串精确比较（必不中），再回退到「含『向北』且含『坐南』即命中」→ 仍判 PASS。也就是说：**现在这条错 golden 不会报错，但按 F-06 收紧回退后，换任意非默认航向都会误判 FAIL**（F-16 与 F-06 必须同批修）。

**修复方向**：`jfmt1` 改为按 float32 实际值取整（`struct.unpack('f', struct.pack('f', x))` 后 `Decimal(实际值)` + `ROUND_HALF_UP`，与本报告 §12 实验脚本同法）；或 golden 只输出原始角度，比较时再在 PowerShell 侧用同一 `String.Format` 生成期望串。

### F-17（中，已证实）基线冻结脚本硬编码「D5 重冻 / develop HEAD / 5 个盘面按钮」，K-01 重冻会写出错误 manifest

**文件行号**：`tests/numeric/tools/freeze_android_baseline.ps1` L6-L7、L12、L107、L119、L124、L128。

**代码片段原文**：

```powershell
# L119
# Android 基线（真机 $Device）· D5 重冻
# L124
- 构建：``develop`` HEAD ``$head``；APK ``$($apkItem.Name)`` $($apkItem.Length) B，SHA256 ``$apkHash``
# L128
- 说明：D5 起底部「5 个盘面按钮」换成「盘面（4 组 · 常用 5）」入口，本基线含 ``disc-dial-list.png``。
```

（`$head` 的来源，L107）：

```powershell
$head = (& git -C (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) rev-parse --short HEAD 2>$null)
```

**触发条件**：按 `docs/product/PRD-v0.1.md` §6 的 K-01 重冻流程，用本脚本产出 `docs/qa/baseline-android/round-k01/`（技术总监 2026-11-05 已指定在 `fix-p0\tests\numeric\tools\` 上改该脚本）。

**预期 vs 实际**：
- 预期：manifest 的轮次名、结构描述、构建来源由参数给出；「构建：X HEAD」必须与 `-Apk` 的实际构建提交一致，否则基线溯源失效。
- 实际：三处写死——标题永远「D5 重冻」（K-01 重冻会产出 `round-k01/manifest.md` 标题写 D5）、说明永远描述「底部 5 个盘面按钮」（K-01 落地后底部导航已删除，描述与基线内容相反）、构建永远写 `develop`；`$head` 取的是**脚本所在 worktree** 的 HEAD（L107），与 `-Apk` 传入的包没有绑定校验——若 APK 由 `fix-p0` 构建而脚本在 `develop` 树运行，manifest 会把 `develop` 的 HEAD 记成该 APK 的构建提交。
- 与 F-13 的关系：F-13 是「无条件声明含 `disc-dial-list.png`」，本条是「轮次/结构/构建来源写死」，同属 manifest 失真，但修复点不同。

**修复方向**：新增 `-Round <round-k01>`、`-Branch <fix/p0-review-findings>`、`-BuildHead <sha>` 三个参数（无默认值时进 manifest 的「未提供」段）；`$head` 与 `-BuildHead` 不一致时 `throw`；L128 的结构说明改为参数或条件句。

### F-18（低，已证实）`check_mechanics.py` 的黑名单缺「待确认」，与 `test_mechanics` 判据不同源，单跑该工具会误判 PASS

**文件行号**：`tests/numeric/tools/check_mechanics.py` L19；`tests/numeric/test_mechanics.py` L11-L12；`tests/at/run-final.ps1` L703-L708。

**代码片段原文**（`check_mechanics.py` L19）：

```python
FORBIDDEN = ("估计", "估算", "estimate", "estimated", "unconfirmed", "TODO", "未确认")
```

（`test_mechanics.py` L11-L12）：

```python
FORBIDDEN_SOURCE_TOKENS = ("估计", "估算", "estimate", "estimated", "unconfirmed",
                           "TODO", "待确认", "未确认")
```

**触发条件**：`app/mechanics.json` 任一 `values[].source` 出现「待确认」四字。

**预期 vs 实际**：
- 预期：`RT-011-mechanics` 的判据（`run-final.ps1` L706 匹配 `estimated=0`）与 `test_mechanics` 的黑名单一致。
- 实际：`check_mechanics.py` 的黑名单少一个 token，会打印 `estimated=0` + `PASS`，`$mechOk` 为真 → `RT-011-mechanics` 判 PASS；同轮 `RT-011-numeric` 依赖 `test_mechanics` 才变红。两处判据不同源，单跑工具（或在别的流水线里只调工具）会得到「19 项 0 估计」的错误结论；README L60 的「0 估计」红线因此有两个版本。

**修复方向**：黑名单抽到共享模块（如 `oracle.py` 常量），`check_mechanics.py` 与 `test_mechanics.py` 都 import 它；`run-final.ps1` L706 的匹配串改为工具输出里的固定字段（`estimated=0 sourced=19` 两者同判）。

### F-19（低，已证实）`img_diff.py` 依赖 Pillow 未登记，缺库时诊断项静默失真

**文件行号**：`tests/numeric/tools/img_diff.py` L9-L11；`tests/numeric/README.md` L15-L16；`tests/at/run-final.ps1` L441-L446；`tests/numeric/tools/at_p0_0_check.ps1` L136-L139。

**代码片段原文**（`img_diff.py` L9-L11）：

```python
import sys

from PIL import Image
```

（`README.md` L15-L16 的依赖清单）：

```markdown
- 依赖：Python 3.10+；Java 对拍需要 JDK17（自动从 `JAVA_HOME` 或
  `C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9` 查找，找不到则跳过该组并计入 skipped）。
```

**触发条件**：干净机器/容器只有 Python 标准库（未装 Pillow），跑 `run-final.ps1` 第 4 段或 `at_p0_0_check.ps1` 的像素 diff。

**预期 vs 实际**：
- 预期：README 的依赖清单覆盖所有被调用的脚本；诊断项失败时在证据里写明「工具不可用」。
- 实际：依赖清单无 Pillow；两处调用都用 `$line = & python $helper ...` 直接接 stdout，Pillow 缺失时 `$line` 变成 traceback 文本，仍写进 `DIAGNOSTIC` 行（`run-final.ps1` L445-L446）/`pixel(diagnostic)`（`at_p0_0_check.ps1` L138），看不出「没测成」。`img_diff.py` 只在参数/读图失败时返回 2（L15-L27），import 失败直接抛异常（退出码 1），调用方也没查退出码。

**修复方向**：README 依赖清单补 Pillow（或把 `img_diff.py` 改为纯 stdlib 实现）；两个调用点检查 `$LASTEXITCODE`，非 0 时把诊断项写成 `BLOCKED(工具缺失: Pillow)`。

### 12.3 第三批计数

| 级别 | 数量 | 条目 |
|---|---|---|
| 中 | 1 | F-17 |
| 低 | 3 | F-16、F-18、F-19 |
| 合计 | 4 | 全部「已证实」 |

补遗后 M5 合计（截至第三批）：**高 2 / 中 8 / 低 8 / 交叉引用 1（F-03→B-10）/ 待确认 3 / 风险改进 4**；F 编号 19 条（F-03 不计入 M5 正式数，故**正式 18 条**）。§13 在此基础上再加 F-20、Q-04/Q-05、R-05/R-06。

---

## 13 第四批：两笔修复的独立复核（f6e97dc / ac08694）+ F-20

> 触发：技术总监 2026-11-05 12:23 交付 `f6e97dc`（F-17）与 `ac08694`（F-16+F-06），要求「审查角色独立复核，不能只有实现方一面之词」。本节所有数字均为本人本机独立重算：**不调用 fix-p0 新增的 verify 脚本**，用自写 Java 探针 + 自写 Python 口径脚本（见 §13.4）；仅在最后一步交叉执行他们的自测作为对照。

### F-20（高，已证实）冻结脚本 `function Adb` 与 `& $adb` 同名 → 无限递归，基线脚本从未执行到设备段

**文件行号**（基线 `review-base` HEAD `78449e1`；该文件最后一次变更是 `7b3175b`）：`tests/numeric/tools/freeze_android_baseline.ps1` L22-L23、L26、L31-L32、L56、L59-L61、L64、L72、L76、L84、L87、L94、L98-L102。

**代码片段原文**（L22-L26）：

```powershell
$adb = "adb"
function Adb { & $adb -s $Device @args }
function Shot([string]$name) {
    $path = Join-Path $OutDir $name
    & $adb -s $Device exec-out screencap -p > $path
```

**触发条件**：脚本执行后的第一条 adb 命令——`-SkipInstall` 时是 L59 `& $adb -s $Device shell am force-stop …`，否则是 L56 的 `install`。**任何一次运行都会触发，没有前置条件。**

**预期 vs 实际**：
- 预期：`& $adb` 启动 `adb.exe` 子进程，脚本走完装包 → 截图 → dump → manifest。
- 实际：PowerShell 命令解析顺序是 alias → **function** → cmdlet → 外部命令；`$adb` 的值是字符串 `"adb"`，`& $adb` 按名字解析时**先命中同名函数 `Adb`**（大小写不敏感），函数体里又执行 `& $adb` → 自递归，永远到不了 `adb.exe`。
- 实测（隔离探针：只含 `$adb = "adb"`、`function Adb { & $adb -s $Device @args }` 和一次 `& $adb -s $Device shell echo hi`，本机 `pwsh` 7.6.9）：**120 秒未返回**，进程 CPU **150.31 s**、工作集涨到 **10.1 GB**，期间**没有产生任何 adb 子进程**；强杀才恢复。
- 面：基线全仓 `function Adb` 只此 1 处；`& $adb` 共 19 处（L23/26/31/32/56/59/60/61/64/72/76/84/87/94/98/99/100/101/102）。

**「脚本没产出 round-d5」的反证**：`docs/qa/baseline-android/round-d5/manifest.md` 标题行「# Android 基线（真机 L2E0222531014833）· D5 重冻」与本脚本 L119 的模板串（`# Android 基线（真机 $Device）· D5 重冻`）一致，冻结时间写 **2026-11-05 04:07:59**；而 `7b3175b`（含 `function Adb`）提交时间为 **03:58:09**，`git show 7b3175b:tests/numeric/tools/freeze_android_baseline.ps1` 第 23 行即该函数。即 04:07 那批产物**不可能是这份提交版跑出来的**（要么当时跑的是 `output` 树里未提交的另一版，要么 manifest 系手写）——与「`output` 树两份漂移」一致。

**修复与验收**：`f6e97dc` 删除该函数；fix-p0 全仓 `function Adb` 命中 0 处，`& $adb` 仍指向字符串 `"adb"`，不会再被劫持。
**措辞更正**：提交说明写「删掉这条从未被调用的 Adb 函数」——不准确。它从未被**显式**调用，但被**每一条** `& $adb` 隐式命中，这正是故障根因；台账/报告引用时请按后者写，否则会被读成「删除死代码」。

### 13.1 F-17（f6e97dc）独立复核

| 验收点 | 我的复核方法与结果 | 结论 |
|---|---|---|
| `-Round` 必传 | 不传跑真脚本 → `ERROR: 缺少 -Round…`、exit 1、未产出 manifest | ✅ |
| `-BuildHead` 必传 | 不传 → `ERROR: 缺少 -BuildHead：必须传入构建该 APK 的提交 sha…`、exit 1 | ✅ |
| sha 合法性 | `-BuildHead zz-12` → `ERROR: -BuildHead 不是合法提交 sha：zz-12`、exit 1 | ✅ |
| `-Apk` 存在性 | 指向不存在路径 → `ERROR: -Apk 不存在：…`、exit 1 | ✅ |
| manifest 标题/构建来源由参数生成 | 代码核对：标题 `# Android 基线 · $Round`；`$head = $BuildHead`；`$branchShown`/`$structureNote` 缺省写「未提供」；「脚本运行树 HEAD」单列并标注「仅诊断」 | ✅ |
| 写死的 D5/develop/5 个盘面已删 | `Select-String 'D5|develop|5 个盘面'` 只剩 L365 一条历史目录说明（`round-d5/` 仅作历史对照） | ✅ |
| 门禁行改为解析实际输出 | 代码核对：`$gateSummary` 从 `numeric-gate.txt` 正则取 `tests=… failures=…`；`$gateOk` 另判 `RESULT: PASS` 与 `CARGO: PASS`，原文附 manifest 文末 | ✅ |
| 正路径实产 manifest | **未复现**：需真机；`docs/qa/baseline-android/` 下只有 `round-d5`，`round-f17-a/b` 未入库（Q-05） | ⏳ |
| `-BuildHead` 存在性 | **残余**：只做 `^[0-9a-fA-F]{7,40}$`，不 `git cat-file -e`；实测 `-BuildHead deadbeef1`（不存在的 sha）**通过参数校验并进入设备段** | R-06 |
| 设备在线前置 | **残余**：无 `adb devices` 断言；把不存在的序列号传给 `-Device`，脚本在 `adb -s FAKE0 logcat -c` 上**挂住 120 s 未返回**，而不是快速报错 | R-05 |

结论：F-17 的三项诉求（轮次可追溯、构建来源必须显式给出、结构描述不臆造）已实现，负路径可复现；正路径与 manifest 实产还需真机证据，按 Q-05 挂起。

### 13.2 F-16 + F-06（ac08694）独立复核

**F-16 数值口径（本人重算，不用他们的 verify 脚本）**：自写 Java 探针（`IndepHead.java`：`Float.parseFloat` → `norm(raw)` → `norm(raw+180f)` → `String.format(Locale.ROOT, "向北 %.1f°    坐南 %.1f°")`，即 `CompassReadSheet.show` 的 float 路径）跑 0.01° 网格 36000 点，再用自写 Python 逐字比较：

| 口径 | 不一致数 |
|---|---|
| 旧 golden（`Decimal(repr(double字面值))`）vs Java，**顶部两行** | **2267 / 36000** |
| 旧 golden vs Java，**仅「向北」单值** | **1440 / 36000** |
| 新口径（float32 精确值 HALF_UP，两行）vs Java | **0 / 36000** |
| 交叉执行他们的 `verify_readsheet_fmt.py` | `headings=36000 mismatches=0`、`RESULT: 0 mismatch`、exit 0 |

即 **1440 与 2267 都对，但口径不同**（§13.4 / C-4）。「仅向北 1800」这个数我**没有复现**（同网格同口径得 1440，`round()` 口径同），见 Q-04。

**F-06 判定收紧**：`run-final.ps1` L492 起改为 `Get-ReadSheetHeadResult`（`tests/at/lib/ReadSheetHead.ps1` L23-L77）：先 `-ceq` 整串逐字；回退只归一 CRLF/CR、U+200B/200C/200D、BOM、NBSP（NBSP→普通空格），**不合并/不删任何可见空格**；其余一律 FAIL，并把「首行逐字=…；次行逐字=…」写进 actual。旧「含『向北』且含『坐南』即命中」已删除（diff 已核），`$hasHead1/$hasHead2` 同布尔的双行独立性问题随之消失。
交叉执行 `pwsh -File tests\at\test-readsheet-head.ps1` → `cases=7 bad=0`、exit 0（含「舍入差 1.2 vs 1.1 判 FAIL」「可见空格数不同判 FAIL」「只有一行判 FAIL」）。

**门禁与用例数**：fix-p0 实跑 `python tests/numeric/run_all.py` → `tests=82 failures=0 errors=0 skipped=0` + `CARGO: 5 passed` + `RESULT: PASS`，exit 0；跑完 `git status --porcelain` 为空（未污染工作树）。
**86→82 不是覆盖缩水**：`test_b2_golden.py` 删掉的 4 条是 token 存在性用例（`test_ring_index_uses_offset_interval`、`test_ring_text_delegates_to_ring_index`、`test_current_cell_center_formula`、`test_client_unit_vectors_present`），替换为 `run_all.py` 真跑 `cargo test` 的 `b2_all_780_cells`（14 圈层逐格执行 `ring_index/ring_text/ring_center`，780 格 + 每格界前 0.01 共 780 次断言），且 `run_all.py` 新增 `REQUIRED_GROUPS`、`skipped>0 → FAIL`、`cargo 未通过 → FAIL`（L41-L51、L119-L132）。**覆盖率是上升的**。

**备注（不立条）**：`golden_readsheet.ring_index` 仍按 double 算（未 f32 化），而 `CompassData.ringIndex` 是「float 减法 → double 取模/除法」。我按两者语义对 14 圈层 × 36000 航向全扫：**0 差异**（14 张表格界都是 float32 可精确表示的 x.0 / x.5 / 5.625）。当前无影响，但这是第三处浮点口径不一致点；若以后出现非整格界（如 7.2°）会立刻爆，建议随 R-02 的 `test_sweep.py` 一起改成 f32。

### 13.3 复核用的一次性脚本（不进仓库）

- `C:\Users\Administrator\AppData\Local\Temp\indep_head\IndepHead.java`：自写 Java 探针（按 `CompassReadSheet.show` 的 float 路径生成顶部两行），stdin 读 36000 个航向。
- `C:\Users\Administrator\AppData\Local\Temp\indep_head\check.py`：自写三口径比较（旧 golden 两行 / 旧 golden 仅向北 / 新口径）。
- `C:\Users\Administrator\AppData\Local\Temp\indep_head\ring_idx.py`：14 圈层 × 36000 航向的 ring_index 语义扫描。
- 隔离递归探针：`%TEMP%\lc_adb_recursion\probe.ps1`（12 行，见 F-20）。
四个脚本只读基线/fix-p0 文件，**没有写回任何仓库文件**；跑完 fix-p0 的 `git status` 为空。

### 13.4 C-4 口径并列：1440 与 2267 是两个不同的计数对象

同一份「修复前 golden」在两个口径下得到两个数，二者都对、不可混用：

| # | 比较对象 | 比较范围 | 计数 |
|---|---|---|---|
| A | 旧 golden（`Decimal(repr(double))`）vs Java float32 | 仅「向北」一个值 | **1440 / 36000**（§12.3 F-16 初稿用的这个；本次独立重算复现） |
| B | 同上 | **顶部两行**（向北 + 坐南） | **2267 / 36000**（技术总监/LC测试用的这个；本次独立重算复现） |
| C | 旧 golden vs Java | 仅「坐南」一个值（B 中 A 之外的部分） | **827 / 36000**（= 2267 − 1440，本次推导） |
| D | 「仅向北 1800」 | — | **未复现** → Q-04 |

为什么 B > A：`back = norm(raw + 180f)` 在 float32 下引入独立的舍入差，坐南行额外贡献 827 个不一致，两行合并为 2267。
结论：**引用时必须带口径**——写「1440」要注明「仅向北、字面值 repr 口径」；写「2267」要注明「顶部两行、0.01° 全网格」。修复后两者都归零（新口径 0/36000）。

### 13.5 新增待确认与风险

**Q-04 「仅向北 1800」的口径不明**：技术总监转述与 LC测试 提交说明都出现 1800，但未给采样脚本；我按 0.01° 网格、旧 golden、仅向北重算得 1440（`round()` 口径同），B 中坐南新增 827。**缺什么证据**：产出 1800 的那段脚本（网格步长、是否含 +180、是否含 normalize）。**由谁补**：LC测试。**补齐判据**：脚本能复算出 1800，且与 A/B 的口径差被写明。

**Q-05 `round-f17-a/b` 的实产 manifest 不在仓库**：`f6e97dc` 的验收依赖两次真机运行（同一 APK、两个 `-BuildHead`），但 `docs/qa/baseline-android/` 下只有 `round-d5`，`checks/` 未入库。**缺什么证据**：两次运行的 `manifest.md` + `SHA256SUMS.txt`（或整目录）。**由谁补**：LC测试 / LC审证据。**补齐判据**：两份 manifest 标题分别等于各自 `-Round`、构建 HEAD 分别等于各自 `-BuildHead`、APK 路径与 sha256 完全相同。

**R-05（P2）冻结脚本没有「设备在线」前置校验**：脚本直接对 `$Device` 下命令，无 `adb devices` 断言。实测把不存在的序列号传给 `-Device`，脚本在 `adb -s FAKE0 logcat -c` 上挂住 120 s 未返回（adbd 侧等待），不是快速报错。建议开头加「`adb devices` 必须含 `$Device\s+device`，否则 `Fail '设备不在线'`」，并给每条 adb 调用加超时。

**R-06（P2）`-BuildHead` 只校验格式、不校验存在性**：`^[0-9a-fA-F]{7,40}$` 挡住 `zz-12`，挡不住 `deadbeef1`。实测后者通过参数校验并进入设备段（随后因假设备挂住，见 R-05）。建议 `git -C $repo cat-file -e "$BuildHead^{commit}"`，可选再校验 `-Branch` 与 `-BuildHead` 的包含关系（`git merge-base --is-ancestor`）。

### 13.6 第四批计数

| 级别 | 数量 | 条目 |
|---|---|---|
| 高 | 1 | F-20 |
| 待确认 | 2 | Q-04、Q-05 |
| 风险 | 2 | R-05、R-06 |
| 合计 | 5 | F-20 为「已证实」（代码路径 + 隔离探针实测） |

全报告合计：**高 3（F-01、F-02、F-20）/ 中 8 / 低 8 / 交叉引用 1（F-03→B-10）/ 待确认 5（Q-01～Q-05）/ 风险改进 6（R-01～R-06）**；F 编号 20 条，正式 19 条（F-03 不计）。


