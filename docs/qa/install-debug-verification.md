# 装机脚本 `tools/device/install-debug.ps1` 验收 checklist（LC 测试）

> 维护：LC测试。适用：服务端交付的 debug 装机脚本（安装风险页自动确认 + 认证对话框注 PIN + 应用市场临时禁用并恢复）。
> 证据目录：`checks\install-debug-<提交号前缀>\`，SUMMARY 首行固定「`<提交号>` | `L2E0222531014833` | `verify/install-debug-checklist`」。
> 纪律：只验脚本、不改脚本（缺陷回给服务端）；每场景一条命令可复现，不写「我点了一遍」。

## 0. 前置（每次开跑前逐条打勾，缺一条不跑）

| # | 检查 | 命令 | 期望 |
|---|---|---|---|
| 0.1 | 脚本确实在目标分支 | `git ls-tree -r --name-only origin/<branch> \| Select-String 'tools/device/install-debug.ps1'` | 命中 1 行（否则脚本没交付，停） |
| 0.2 | 记下被测提交 | `git rev-parse --short origin/<branch>` | 写进 SUMMARY 首行 |
| 0.3 | 设备空闲（不是别人在用） | `adb shell dumpsys window \| Select-String mCurrentFocus` | **不是** `packageinstaller/InstallStaging`、不是别的 Bot 正在点的界面 |
| 0.4 | 唤醒 + 解锁（装包必需） | `adb shell input keyevent 224` → 上滑 → PIN（`E:\APPDetector\secrets\device-pin.txt`，**不打印**）→ `wm dismiss-keyguard` | `mCurrentFocus` 到桌面/本应用 |
| 0.5 | 动画三项置 0（dump 稳定） | `settings put global window_animation_scale 0` 等三项 | 收尾恢复 1 |
| 0.6 | 被测 APK 就绪 + 哈希 | `Get-FileHash <apk> -Algorithm SHA256` | 写进 SUMMARY（APK 绝对路径 + sha256 + 大小） |
| 0.7 | 基线快照 | `pm list packages -d`、`dumpsys package com.luopan.fengshui \| Select-String lastUpdateTime` | 存 `s0-baseline.txt`（场景 1/4 要用） |

## 1. 通用取证纪律（每场景都适用）

- **计时**：用 `Measure-Command` 包脚本调用，或脚本自报 `elapsed=`；SUMMARY 记「脚本 wall time」和「命令到装完的墙钟」两个数，单位秒、保留 1 位。
- **退出码**：每场景必须记 `$LASTEXITCODE`（PowerShell 里 `$LASTEXITCODE` 可能为 `$null`，用 `if ($null -eq $LASTEXITCODE) {0} else {$LASTEXITCODE}` 归一）。
- **dump**：一律「`rm -f /sdcard/a.xml` → dump → pull → 确认根节点是当前界面」，复用已踩过的坑（旧 dump 假 MISS）。
- **logcat**：先 `logcat -c`，跑完 `logcat -d` **必须走 `tests/lib/LogRedact.ps1`** 落盘（`Save-RedactedText`），不许手工拼 `tk=`；命名不许带 key 片段。
- **UI 路径判定**：脚本输出里出现的手动步骤（点了哪、注了什么）与 `logcat`/`dumpsys` 对齐；PIN 只记「已注入/未注入」，**值永不入证据**。
- 每场景目录：`s<N>/`，至少留 `stdout.txt`（脚本原始输出，脱敏后）、`exit.txt`、`timing.txt`、`logcat.txt`、`ui-*.xml`、`*.png`（如需）。

## 2. 五个场景

### S1 · `pm install -r -t` 主路径（不弹 UI）
- **构造**：设备已装同包（可先手工装一版旧包），然后跑脚本默认主路径。
- **命令**：`pwsh -File tools\device\install-debug.ps1 -Apk <apk> -Device L2E0222531014833`（参数以脚本实际 `-?` 为准，实际参数原样记 SUMMARY）。
- **期望**：一次 `Success`；全程无安装器 UI（脚本运行期间轮询 `mCurrentFocus` 不得出现 `packageinstaller`/`UnifiedAuthenticationDialogActivity`）；`lastUpdateTime` 与 `s0-baseline.txt` 不同；退出码 0。
- **判据**：`lastUpdateTime` 变化 + 无 UI + 退出码 0 → PASS；任一不满足 → FAIL（记 dump + logcat）。
- **关键证据**：`s1/before-after-lastUpdateTime.txt`、`s1/focus-poll.txt`（每 300ms 采样）、`s1/logcat.txt`。

### S2 · `adb install` 触发纯净模式风险页（脚本自动点「继续安装/仍要安装」）
- **构造**：走脚本的 UI 安装路径（若脚本默认走 `pm`，用脚本提供的强制 UI 参数；参数名以 `-?` 为准并记录）。本机纯净模式已知会拦 ADB 装包（`INSTALL_FAILED_ABORTED: User rejected permissions` 是 commit 阶段被拒，风险页是另一形态）。
- **期望**：脚本自己识别风险页并点「继续安装」或「仍要安装」（`ui-*.xml` 里能看到被点节点的 text/bounds），最终装上；退出码 0；不得需要人工点击。
- **判据**：装成功 + 有「自动点击那一下」的 dump 证据 + 无人工干预 → PASS；脚本卡在风险页（>90s 无进展）→ FAIL；页面根本没出现（纯净模式已关）→ 记 BLOCKED（环境不具备），不算脚本红，但要在 SUMMARY 写明如何复现。
- **关键证据**：`s2/risk-page.xml`（点之前）、`s2/risk-page-after.xml`（点之后）、`s2/logcat.txt`、`s2/timing.txt`。

### S3 · `UnifiedAuthenticationDialogActivity`（认证对话框注 PIN）
- **构造**：在会弹「请输入锁屏密码继续安装」的路径上跑（首次 `adb install` 或纯净模式风险页之后常见）。
- **期望（正常）**：脚本识别该 Activity → 注入锁屏凭据 → 装上，退出码 0。PIN 从 `E:\APPDetector\secrets\device-pin.txt` 读，**不得出现在 stdout/logcat/证据**。
- **期望（EMUI 吞注入）**：脚本必须在有限时间内停住并打印 `INSTALL_AUTH_BLOCKED`（或脚本内约定的等价常量），退出码非 0，**不得死循环重试**；同时把当时 `mCurrentFocus` 记进证据。
- **判据**：注入成功装上 → PASS；吞注入时按约定停 + 非零退出 + 不循环 → PASS（属于脚本健壮性）；吞注入却死循环或沉默超时（>120s 无输出）→ FAIL。
- **关键证据**：`s3/auth-dialog.xml`、`s3/stdout.txt`、`s3/exit.txt`、`s3/logcat.txt`（确认没把 PIN 打进日志）、`s3/focus.txt`。

### S4 · 应用市场抢前台 + 结束恢复
- **构造**：先让市场在前台（`adb shell am start -a android.intent.action.VIEW -d market://details?id=com.luopan.fengshui`，或沿用会拉起市场安装页的路径），再跑脚本。
- **期望**：脚本临时 `pm disable-user --user 0 com.huawei.appmarket`（或脚本实际用的包名）→ 装机流程不被抢前台 → **结束后在 `finally` 块恢复**（`pm enable`）。
- **判据**：装成功 + 结束时 `pm list packages -d` 里**没有** `com.huawei.appmarket`（即已启用）+ 运行中确曾禁用（脚本输出或 `pm list packages -d` 轮询证据）→ PASS；跑完市场仍禁用 → FAIL（并立即手工 `pm enable com.huawei.appmarket`，在 SUMMARY 记一条「已人工恢复」）。
- **关键证据**：`s4/disabled-during.txt`、`s4/restored-after.txt`（两条 `pm list packages -d` 原样）、`s4/stdout.txt`、`s4/exit.txt`。

### S5 · 失败场景：非 APK 文件
- **构造**：`Set-Content "$env:TEMP\fake.apk" "not an apk"`（扩展名对、内容错）。
- **期望**：脚本明确报错（输出里能看出「不是合法 APK / 安装失败」），退出码非 0；**不弹任何安装 UI、不误点其它界面**。
- **判据**：退出码 ≠ 0 + 错误信息明确 + 运行前后 `mCurrentFocus` 相同 + 无 `packageinstaller` 焦点 → PASS；退出码 0 或静默/误点别的界面 → FAIL。
- **关键证据**：`s5/stdout.txt`、`s5/exit.txt`、`s5/focus-before-after.txt`。

## 3. 收尾（每轮固定）

1. `pm enable com.huawei.appmarket`（幂等；确认市场可用）。
2. 脚本没恢复的设备状态要补：动画三项恢复 1、`pm uninstall com.luopan.fengshui`（若本轮是验装机，可保留一版给后续轮；按总监当轮指示）、`am force-stop`、`input keyevent 3` 回桌面。
3. 证据目录过一遍 `LogRedact` 命名/内容自检；`sha256-all.txt` 全量哈希；SUMMARY 首行「提交号 | 设备号 | 分支」。
4. 报「释放真机」+ 通过/失败/阻塞清单。

## 4. SUMMARY 模板

```
<commit> | L2E0222531014833 | verify/install-debug-checklist
# 装机脚本验收（tools/device/install-debug.ps1）
- 脚本来源：origin/<branch> @ <commit>；参数：<实际参数原文>
- APK：<绝对路径>（<字节> B）sha256 <...>
- 基线：lastUpdateTime=<...>；pm list packages -d 快照见 s0-baseline.txt
| 场景 | 路径 | 耗时(s) | 退出码 | 结论 | 证据 |
|---|---|---|---|---|---|
| S1 pm 主路径 | pm | | | PASS/FAIL/BLOCKED | s1/ |
| S2 纯净模式风险页 | UI | | | | s2/ |
| S3 认证对话框 | PIN | | | | s3/ |
| S4 市场抢前台 | disable-user | | | | s4/ |
| S5 非 APK 失败 | — | | | | s5/ |
## 判决
- 通过 / 失败项 / 阻塞项 + 一句话结论（是否可宣布「以后装机统一走这个脚本」）
## 环境备注
- 纯净模式状态、市场恢复状态、EMUI 是否吞 PIN 注入、其它异常
```
