# LC 基线报告 01（M0 第一轮）

- 采样时间：2026-11-05 02:20–02:35（UTC+8）
- 仓库：`E:\APPDetector\output`，HEAD `9dda4e2`（分支 `feature/lc-server-infra`）
- 工作区：含并行未提交改动（`app/src/main.rs`、`CompassData.java`、`MainActivity.java`、
  `map.html`、`mechanics.json`、`docs/TEAM_CHARTER.md`、`tools/`、`docs/design|product/`、`tests/`）
- 环境：Windows，cargo/rustc 1.99.1，JDK 17.0.21（Temurin），Gradle 8.7，Python 3.13.14
- 真机：`L2E0222531014833`（**本轮按总监指令让给 LC地图，未做安装/启动冒烟**）

> 快照锚点（SHA256 前 16 位）：
> `main.rs=AB8CC463D2326B70`、`software.rs=E9BE2BE68B58E97C`、
> `CompassData.java=97EFD425EDD2A8E1`、`CompassView.java=6F6118FEB8BFDEDF`、
> `MainActivity.java=261C0DC11ED07166`、`MECHANICS.md=932969279A67CAC7`、
> `mechanics.json=AFB5BC26A0861F4E`（采样期间被 LC逆向 迭代 3 版：82999→F2C8→AFB5，
> 末版 19 项/0 估计已复验；`map.html` 采样时仍在变，未纳入数值基线）。
> 若当前哈希不同，说明工作区已被并行修改，基线需重跑。

## 1. 结论摘要

| 项 | 结果 | 证据 |
|---|---|---|
| 桌面 `cargo build` | ✅ 0 error（1 warning） | 见 §2.1；`app\target\debug\luopan.exe` |
| 桌面启动截图 1100×780 | ✅ | `checks\qa-baseline-01\desktop-screenshot.png`（1100×780，151,480 B） |
| `mechanics.json` 19 项 0 估计 | ✅ 19/19 有来源、0 估计 | `python tests\numeric\tools\check_mechanics.py` → PASS |
| 数值边界单测 | ✅ 74 项全绿（含 5 条已知缺陷 xfail） | `python tests\numeric\run_all.py` → `RESULT: PASS` |
| 两端数值同源 | ✅ 7 张表 + 14 圈层 + 5 盘面逐项一致 | `test_mechanics` / `test_ends_parity` |
| 三盘读数正确性 | ❌ **BUG-001（P0）** | `docs\qa\bugs.md`；`dump_readings.py` |
| Android `assembleDebug` | ✅ BUILD SUCCESSFUL | 见 §4 |
| Android 真机启动/冒烟 | ⏸ 未执行（真机让给 LC地图） | 待总监指令 |
| 地图帧率 / Android 内存 | ⏸ 未测（需真机） | 待第二轮 |

## 2. 桌面版（craft 三关）

### 2.1 构建

```powershell
cd E:\APPDetector\output\app
cargo build
# Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.20s
# CARGO_EXIT=0（本次为增量；改动后首次全量 2.3s）
# warning: `luopan` (bin "luopan") generated 1 warning
```

### 2.2 启动截图（软件渲染路径，`--screenshot`）

```powershell
cd E:\APPDetector\output
.\app\target\debug\luopan.exe --screenshot checks\qa-baseline-01\desktop-screenshot.png
# EXIT=0  WALL_MS=2084
# PNG=checks\qa-baseline-01\desktop-screenshot.png  SIZE=1100x780  BYTES=151480
# SHA256=E2DE3ADE794F9358...
```

说明：该路径走 `app/src/software.rs` 的 Software 渲染（无 GL/窗口），尺寸固定 1100×780；
画面与 `checks/round-17/screenshot.png` 字节级一致（SHA256 相同），说明本轮数值/绘制未回归。

### 2.3 `mechanics.json` 19 项 0 估计

```powershell
python tests\numeric\tools\check_mechanics.py
# mechanics.json version=2 values=19 sourced=19 estimated=0
# rings=14 dials=5 tables=branches,gua_wuxing,houtian,jiazi,mountains,nayin,wuxing,xiantian,yang_stems,yin_stems,yinyang
# PASS: 19 项全部有来源、0 项估计
```

### 2.4 数值单测

```powershell
python tests\numeric\run_all.py
# tests=74 failures=0 errors=0 skipped=0 expected_failures=5 unexpected_successes=0
# RESULT: PASS
```

覆盖清单见 `tests/numeric/README.md`；oracle 依据 `app/MECHANICS.md:88-103,149-161,170-205`
与 `app/mechanics.json` v2。两端实现是分别验证的：Java 用 JDK17 编译执行 `CompassData.java`
（40 个航向），Rust 从 `main.rs` 解析表达式求值，再互相对拍。

## 3. 性能基线（桌面）

| 指标 | 值 | 方法 |
|---|---|---|
| 窗口出现耗时 | 中位 **440 ms**（418 / 440 / 449，3 次） | `Start-Process` 后 10ms 轮询 `MainWindowHandle != 0` |
| 工作集内存 | 126.8–128.1 MB（3 次，启动后 2s） | `Process.WorkingSet64` |
| 私有内存 | 75.1–76.0 MB | `Process.PrivateMemorySize64` |
| 线程数 | 13 | `Process.Threads.Count` |
| `--screenshot` 全流程 | 2.08–2.61 s | 进程墙钟（含字体加载与 PNG 编码） |
| 可执行文件 | 10.4 MB（debug） | `app\target\debug\luopan.exe` |

未测：地图帧率、Android 启动耗时/内存（需真机，第二轮补）。

## 4. Android 构建与真机现状

```powershell
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
$env:PATH="$env:JAVA_HOME\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;$env:PATH"
cd E:\APPDetector\output\app\android
gradle assembleDebug --no-daemon
# BUILD SUCCESSFUL in 17s
# 33 actionable tasks: 33 up-to-date
# APK: app\build\outputs\apk\debug\app-debug.apk
#      GRADLE_EXIT=0  DURATION_SEC=18.2
#      APK_BYTES=3212339  mtime 02:32:37  SHA16=F012813636AFEFE5
```

- 上一版（02:29:43，`0616044D2A9C150F`，3,212,138 B）已被本版覆盖；两次均 `BUILD SUCCESSFUL`；
- 真机安装/启动/logcat 本轮**未执行**（真机让给 LC地图），故 `adb` 无 FATAL/ANR 结论；
- APK 未提交（`.gitignore` 已排除 `*.apk`）。

## 5. 已知缺陷

| ID | 等级 | 摘要 | 责任人 | 状态 |
|---|---|---|---|---|
| BUG-001 | P0 | 人盘/天盘读数恒等于地盘（两端） | LC客户端 | 新建 |
| BUG-002 | P2 | 桌面 60 龙标签环偏 4.5°，指针处标签 ≠ 读数 | LC客户端 | 新建 |

详细复现/证据/修复建议见 [docs/qa/bugs.md](bugs.md)。另有 2 条待归口观测（5 盘面只渲染 3 种、
截图工具硬编码「正向」），见同文件。

## 6. 风险与备注

1. **基线不稳定**：本轮 6 人并行改同一工作区，采样窗口内 `map.html` 与 `mechanics.json` 仍在变化
   （LC地图 / LC逆向）。本报告所有结论绑定 §0 的哈希；并行改动落地后需重跑 `run_all.py` + 本报告 §2。
2. **P0 未修**：BUG-001 会让读数面板与知识库/圈层绘制矛盾，M1 前必须修；修好后单测会由
   xfail 变 unexpected success（红），按 `tests/numeric/README.md` 的说明去掉 xfail。
3. **真机空窗**：Android 启动、罗盘转动、5 盘面、地图 9 项、logcat、帧率均未验证，属本轮
   已知阻塞（非失败），第二轮补做。
4. 桌面构建有 1 条 warning（未使用常量），不阻断红线；等 LC客户端 收口时清掉。

## 7. 复现（一条链）

```powershell
cd E:\APPDetector\output
python tests\numeric\tools\check_mechanics.py
python tests\numeric\run_all.py
cd app; cargo build; cd ..
.\app\target\debug\luopan.exe --screenshot checks\qa-baseline-01\desktop-screenshot.png
```

## 8. 下一轮待办（LC测试）

1. 等总监指令后做真机冒烟：`adb install -r` → 启动 → 罗盘转动 → 5 盘面 → 地图 9 项 →
   `adb logcat` 扫 FATAL/ANR，回填 §4 与 `bugs.md`。
2. BUG-001/002 修复后回归（xfail → 正常用例），并把三盘读数纳入 Android UI 断言。
3. 性能：Android 冷启动耗时、内存峰值、地图 rAF 帧率（与 LC地图 的测量方法对齐）。
4. 把 `run_all.py` 接进 `scripts\smoke.ps1`（LC服务端 的脚本落地后）。
