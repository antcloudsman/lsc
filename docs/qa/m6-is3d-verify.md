# M6 is3D 三条验收（客户端 3D 状态跨重载/重建，不跨进程）

- 分支/构建：`feature/client-m6-crosshair` @ `81e6a44`（含 `9e4ec87` 原生持有 + `81e6a44` static + `isFinishing()` 清理）
- APK：sha256 `eddbfba0f2cfcd6d38517fcc28cb7291dbd208ac64b38c4e2c0505f7914296cf`，8,595,869 B
- 装机：`m6-install-record.ps1` → PASS（`INSTALL_OK taps=2`、`MARKET_RESTORED=ok`、`lastUpdateTime 2026-11-08 03:12`）
- 上机前置：机上 `pm path` size/sha 与构建产物一致、`uiMode=map`、市场未禁用
- 证据：`E:\APPDetector\reports\M6-is3d\states.txt`

## 判据与实测

| # | 判据 | 实测 | 结果 |
|---|---|---|---|
| T1 | 网页重载后保持 3D（修的那个 bug） | 点右列「3D」→ `mode=3d`；`js location.reload()` 10s 后 → `mode="3d"` | PASS |
| T2 | 冷启（杀进程）回 2D（不许多做持久化） | pid 26111 → `force-stop` → pid 空 → `am start` → pid 26524（新进程）→ `mode="2d"` | PASS |
| T3 | 退出主界面再进（**同进程**）回 2D | 3D 态 pid 26524 → `keyevent 4` 退出（MainActivity 记录数 0、前台 launcher、pid 仍 26524）→ `am start` → `mode="2d"` | PASS |

补充：源码 `putBoolean/getBoolean("is3d")` 命中 **0**，没有 prefs 持久化；`is3D` 为 `static Boolean sIs3D` + `onCreate` 恢复 + `isFinishing()` 清空。

## 结论

- T1 证明「重载静默回 2D」已修好（修复前必回 2d）；
- T2/T3 证明没有把持久化做多：**进程死、真退出都回 2D**，与原版 `ViewModel` 语义一致（T3 是同 pid 的强证据，裸 `static` 会在这一条挂）。
- 本验证在分支上完成（`81e6a44` 尚未合入 develop）；**合入后若该文件再有改动，需按同一脚本重跑一遍再签收**。
