# 装机场景真机验证（develop 9028cbe，install-debug.ps1 166 行版）

- APK：`app\android\app\build\outputs\apk\debug\app-debug.apk` 8,584,513 B sha256 `650506af…`
- 设备：`L2E0222531014833`；容器 `lc-backend` healthy（与本轮无交互）
- 脚本：`tools/device/install-debug.ps1`（develop 版；无 `-InstallMode`，S1/S2 同路径）

| 场景 | 结果 | 实测 |
|---|---|---|
| S1 pm 主路径（纯净模式需两页点按） | PASS | exit=0，wall_seconds=12.8，taps=2，lastUpdateTime 18:38:40 → 20:10:20 |
| S2 风险页自动点按 | PASS（并入 S1） | 同一次执行里两次 `tap '继续安装'`：`InstallStaging` 881,2524 → `PackageInstallerActivity` 614,2362；全程无人工介入 |
| S3 认证对话框注 PIN | BLOCKED（环境不具备） | 本轮未出现 `UnifiedAuthenticationDialogActivity`（S1 stdout 无该焦点、无 auth 分支）；不当 FAIL |
| S4 市场临时禁用 + finally 恢复 | PASS | 脚本内 `MARKET_RESTORED=ok`；跑完 `pm list packages -d` 无 `com.huawei.appmarket`（before=''; after=''） |
| S5 非 APK 文件 | PASS | exit=2，wall_seconds=5.7，taps=0（无误点），焦点 launcher→launcher 未变，失败原因 `INSTALL_PARSE_FAILED_NOT_APK`，lastUpdateTime 未变 |

## 结论

- 装机脚本在真机上按「纯净模式两页点按」路径可用：**无人工介入、两次点按、时间戳变化、市场必恢复**。
- S3 在本机未复现认证对话框，按 checklist 记 BLOCKED（环境不具备），不计脚本红。
- S5 失败路径干净：非 0 退出、错误信息可读、不误点其它界面。

## 证据

- `s1/stdout.txt`、`s1/exit.txt`、`s1/timing.txt`、`s1/market.txt`
- `s5/stdout.txt`、`s5/exit.txt`、`s5/timing.txt`
- 说明：本目录为直接执行取证（驱动封装版 `tests\device\run-install-scenarios.ps1` 在 Start-Job 封装上不稳定，未用于本次结论）
