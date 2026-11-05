# AT-P0-1 · 读数可信包（校准 / 磁偏角 / 精度 / 倾斜 / 天池）验收用例

- 作者：LC产品｜执行：LC测试｜日期：2026-11-05
- 关联：`docs/product/PRD-v0.1.md`（v0.3）§3 P0-1；口径定义见 PRD 同节
- 状态：**未实现 → 当前 BLOCKED**；实现落地后按本文件直接执行
- 判定：`PASS` / `FAIL` / `BLOCKED` / `N/A`
- 证据目录：`docs/qa/runs/<yyyyMMdd-HHmm>-P0-1/`（logcat、ui-*.xml、probe.csv、shots/、notes.md）

---

## 0. 实现侧前置（缺任何一条 → BLOCKED）

| # | 要求 | 提供方 | 说明 |
|---|---|---|---|
| S1 | 纯静态类 `com.luopan.fengshui.SensorMath`（**不 import android.\***）：`normalize(float)`、`applyDeclination(float raw, float decl, boolean trueNorth)`、`tiltDegrees(float pitchDeg, float rollDeg)`、`isFlat(float tilt, float limit)` | LC客户端 | 供 JVM 对拍（复用 `tests/numeric` 的 javac 方式） |
| S2 | **统一 DebugBridge**（一个 receiver，与 P0-0 的 K5、P0-4 的 M2 共用）：`-a com.luopan.fengshui.DEBUG`，`--es cmd heading --ef value <float>`、`--es cmd accuracy --ei value <0..3>`、`--es cmd tilt --ef pitch --ef roll`、`--es cmd reset`。**注入时效：保持到 `reset` 或下一次真实传感器采样** | LC客户端 | 只进 debug，不进 release；不写时效，RT-002/004 会被下一帧传感器覆盖 |
| S3 | `readingView` id `reading_text`；状态条 id `sensor_status`，文本含「磁北/真北」+「精度高/中/低」 | LC客户端 | `uiautomator dump` 可直接读 |
| S4 | 设置落盘 `shared_prefs/lc_prefs.xml`：`declination`(float)、`true_north`(bool)、`calibration_done`(bool) | LC客户端 | `run-as cat` 校验；文件不存在 = 未实现 |
| S5 | **口径**：`H = normalize(raw + (trueNorth ? decl : 0))`；所有环与坐向都用 `H` 计算，不允许某处用 raw | LC客户端 | 这是 RT-002/003/004 的判定基础 |
| S8 | 倾斜注入：`--es cmd tilt --ef pitch <deg> --ef roll <deg>（S2 的子命令；LC测试 门禁里编号 S8）` | LC客户端 | RT-008a 靠它做硬门禁，手抬只截图 |

---

## 1. 环境与命令

```powershell
$DEV = 'L2E0222531014833'
$PKG = 'com.luopan.fengshui'
$JDK = 'C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'

pwsh -File scripts\build-android.ps1 -DeviceId $DEV -OutDir checks\at-p0-1

# 注入航向 / 精度 / 倾斜
adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 94.0
adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd accuracy --ei value 1
adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd tilt --ef pitch 12 --ef roll 0

# 读界面文本
adb -s $DEV shell uiautomator dump /sdcard/ui.xml
adb -s $DEV pull /sdcard/ui.xml .\ui.xml
Select-String -Path .\ui.xml -Pattern 'reading_text|sensor_status'

# 截图（别用 exec-out 重定向，PNG 会坏）
adb -s $DEV shell screencap -p /sdcard/shot.png
adb -s $DEV pull /sdcard/shot.png .\shot.png
```

> 执行器（LC测试 已落）：`pwsh -File tests\at\run-at.ps1 -Suite P0-1`，缺前置 exit 2。

### 1.1 B-02 验收：release 包必须无调试桥（S2 的负向用例）

S2 的 debug bridge 只在 `FLAG_DEBUGGABLE` 包内注册（`MainActivity.registerDebugBridge()` 首行门槛）。release 包上同一帧广播必须**零输出**——既没有 `bridge registered`，也没有 `LCJS`。步骤：

```powershell
# 1) 装 release 包（非 debuggable）
adb -s $DEV install -r .\app-release.apk
adb -s $DEV shell dumpsys package $PKG | Select-String 'flags='   # 期望：无 DEBUGGABLE
adb -s $DEV logcat -c
# 2) 发 heading 与 js 各一帧
adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 94.0
adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "1+1"
Start-Sleep -Milliseconds 500
# 3) 读日志：期望为空（PASS）
adb -s $DEV logcat -d -s LCDEBUG LCJS
```

判定：**PASS** = 上述 logcat 无任何 `bridge registered` / `LCJS` 行，且界面读数不被 94.0 冻结；**FAIL** = 出现任一 `LCDEBUG`/`LCJS` 行（说明 release 注册了桥）。对照组：同一命令在 debug 包上必须打印 `bridge registered sdk=... action=com.luopan.fengshui.DEBUG`，否则是门槛写反了。

**参考读数（JVM 真实现，不手算分金/龙）**：

```powershell
$cls = '.\checks\at-p0-1-classes'
New-Item -ItemType Directory -Force $cls | Out-Null
& "$JDK\bin\javac" -encoding UTF-8 -d $cls `
  app\android\app\src\main\java\com\luopan\fengshui\CompassData.java `
  app\android\app\src\main\java\com\luopan\fengshui\SensorMath.java `
  tests\numeric\java\CompassProbe.java
'94','99','357','2','64','124' | Set-Content .\headings.txt -Encoding ASCII
& "$JDK\bin\java" -cp $cls com.luopan.fengshui.CompassProbe .\headings.txt .\probe.csv
Get-Content .\probe.csv
```

> 若 `SensorMath` 尚未落地，本段 `javac` 会失败——这本身就是 BLOCKED 的证据，不要跳过。

---

## 2. 用例

| ID | 层级 | 步骤 | 预期 | 证据 |
|---|---|---|---|---|
| RT-001 | JVM | 编译 S1；用 Python 实现 `normalize(raw + (trueNorth?decl:0))` 作 oracle，对 `applyDeclination` 采样 720 个航向 × decl∈{−30,−7.5,0,+5,+30} × 两口径 | 与 oracle 逐点差 ≤0.001°，无 NaN | probe 输出 + 脚本 |
| RT-002 | 界面·主用例 | 注入 raw=94.0；开校准；真北 +5.0；dump | 「度数 99.00°」；地盘 乙（`m=7`）、坐 辛；**人盘 乙、天盘 卯**（409ad5f 后的 `plate()` 结果）；分金/72 龙/60 龙 与 `probe.csv` 的 `99` 行逐字一致（换表前实测 `甲辰 / 甲辰 / 庚辰`；**轮 B2 换表后以 `mechanics.json` 的 `rings[].labels` 为准**）；后天卦 `震`。CSV 的 `m` 是索引不是山名，按 `MOUNTAINS[m]` 映射 | ui.xml + probe.csv |
| RT-003 | 界面·回绕 | raw=357.0、真北 +5.0 | 「度数 2.00°」；地盘 子、坐 午 | ui.xml |
| RT-004 | 界面·口径切换 | 同一 raw=94.0 下：真北↔磁北 来回切 10 次，每次 dump（每次切换前重新注入 heading，防时效过期） | 真北恒 99.00°（乙/辛），磁北恒 94.00°（卯/酉）；无累积漂移 | 10 份 dump 摘要 |
| RT-005 | 边界 | decl = −30 / +30；再尝试输入 +45（超界） | −30 → 64.00°（寅/申）；+30 → 124.00°（辰/戌）；+45 被夹到 +30（或拒绝并提示），不崩溃 | ui.xml + 截图 |
| RT-006 | 精度提示 | 注入 accuracy=1 → dump；再 =3 → dump | 1：`sensor_status` 含「精度低」且为黄色；3：不含提示 | 两份 ui.xml + 截图 |
| RT-007 | JVM·倾斜 | `tiltDegrees(0,0)` / `(10,0)` / `(0,10)` / `(10,10)` | 0 / 10 / 10 / **14.1（±0.1）**；公式固定为 `acos(cos p·cos r)`（总监 2026-11-05 裁决：`sqrt(p²+r²)=14.14`、`atan=14.00` 均不采用；旧口径 13.9 无出处，已废） | 脚本输出 |
| RT-008a | 界面·倾斜（**硬门禁**） | 注入 `tilt 0/0` → dump；`tilt 12/0` → dump；`reset` → dump | 0° 无提示；12° 出现「请端平」；reset 后消失 | 三份 ui.xml |
| RT-008b | 界面·倾斜（人工，只记录） | 真机平放/手抬 10–15° 各截一张 | 现象与 RT-008a 一致；角度以手机水平仪为准（±1°），**不判失败** | 两张截图 + notes |
| RT-009 | 8 字校准 | `pm clear` → 启动 → 首次进罗盘；点跳过；杀进程再进 | 首次出现一次引导；跳过后不再出现；`lc_prefs.xml` 的 `calibration_done=true` | 两张截图 + xml |
| RT-010 | 全关回基线 | 注入固定 heading → 关闭 P0-1 开关 → dump `reading_text` | 与 `docs/qa/baseline-android/` 的同 heading 基线 dump **逐字相同**；无 `sensor_status`；罗盘中心无天池/十字线 | 基线 ui.xml vs 全关 ui.xml |
| RT-011 | 数值红线 | `python tests/numeric/tools/check_mechanics.py`；`python tests/numeric/run_all.py`；`git diff --stat app/mechanics.json` | 19 项有来源 0 估计；套件全绿（**当前 83 PASS**）；mechanics 0 改动；三盘取值只来自 `CompassData`/`plate()`，P0-1 **不得**引入第三套偏移 | 三条命令输出 |
| RT-012 | 持久化 | 设 +5.0/真北 → `force-stop` → 重开 → 重新注入 raw=94.0 → dump | 仍是 +5.0、真北口径；读数 99.00° | xml + ui.xml |

### 已知缺陷与口径

- **人盘/天盘恒等于地盘（BUG-001）、六十龙落位偏 4.5°（BUG-002）已随 `409ad5f` 修复**（`docs/qa/bugs.md` 已回归关闭）。RT-002 的三盘期望按修复后结果写；若逆向后续更新表，以执行当时 `probe.csv`/基线为准，同步改本行。
- 本用例只判定「**校准值同步进全部环**」，不重新定义三盘语义；若 P0-1 为了好看再引入偏移 → RT-011 判 **FAIL**。

---

## 3. 判定规则

- **PASS**：RT-001…RT-007、RT-008a、RT-009…RT-012 全过；RT-008b 有截图（只记录，不判）。
- **FAIL**：任一预期不符。以下两条必须 FAIL：
  1. 只有「度数」变了、某个环没变（RT-002/003）——说明校准没进统一链路；
  2. 全关状态还有状态条/天池（RT-010）——增量污染基线。
- **BLOCKED**：S1–S5、S8 任一缺失，notes.md 写明缺哪条；**不要**用「人工拖动罗盘估角度」代替 S2/S8（误差 ±1° 会让 RT-002 失效）。

---

## 4. 回归

1. `scripts/build-android.ps1` 通过，logcat 无 FATAL。
2. `python tests/numeric/run_all.py` 全绿。
3. 开关全关：三 Tab 截图 diff=0（与 P0-0 的 SW-006 同脚本）。
4. 知识库页 5 盘面/14 圈层文案未被改动（`git diff` 检查 `KNOWLEDGE_*` 无变更）。

---

## 5. 风险与备注

- `mode`（磁北/真北）与 `declination`（值）必须分开存：否则「切回磁北」会把值清掉，RT-004 无从判定。
- 「按定位自动」取磁偏角依赖定位权限；无定位时按 S5 回退到手动值并在状态条提示「未定位，使用手动值」。这条不单独设用例，由实现说明 + 人工确认。
