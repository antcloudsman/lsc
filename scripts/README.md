# scripts — LC 一键构建 / 回归

四个脚本都在**干净 shell** 里可直接跑（不需先设置 `JAVA_HOME` / `PATH`），输出统一为
`[PASS]` / `[FAIL]` 行 + 最后一行 `DESKTOP: PASS|FAIL`、`ANDROID: PASS|FAIL`、`SMOKE: PASS|FAIL`，
退出码 0/1 可被 CI 直接使用。产物全部落在 `checks\`（已在 `.gitignore`，不入库）。

## 桌面版质量红线

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-desktop.ps1
```

做三件事：

1. `cargo build`（`app\`）
2. 运行 `target\debug\luopan.exe --screenshot <out>\screenshot.png`，校验 PNG 签名与 **1100×780** 尺寸；
   文件小于 20 KB 视为空白渲染失败
3. 校验 `app\mechanics.json`：恰好 19 项、每项有硬来源（出现 `estimate/估计/未确认/…` 即失败）、无重名

参数：`-OutDir`（默认 `checks\desktop-<时间戳>`）、`-AppDir`、`-MinPngBytes`。

## Android 构建 + 真机冒烟

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build-android.ps1
powershell -File scripts\build-android.ps1 -SkipDevice   # 只构建 APK
```

1. 自动解析 JDK 17（`-JavaHome` → `JAVA_HOME` → 本机已知路径 → Android Studio jbr）与 Gradle 8.7
2. 缺 `app\android\local.properties` 时按 `ANDROID_HOME`/默认 SDK 路径生成（该文件本就被忽略）
3. `gradle assembleDebug --no-daemon`，校验 APK 存在且 > 100 KB，复制到输出目录
4. 真机：`adb logcat -c` → `install -r` → `am start -W` → 等待（默认 5 s）→ `pidof` 存活检查
   → `logcat -d` 扫描 `FATAL EXCEPTION` / `E AndroidRuntime` → 拉一张设备截图作证据

参数：`-DeviceId`（默认 `$env:LC_DEVICE_ID`，否则唯一在线设备）、`-SkipDevice`、
`-LogWaitSeconds`、`-OutDir`。

> 本轮真机只有 `L2E0222531014833` 且优先给 LC地图；抢设备时用 `-SkipDevice`，或在 `smoke.ps1` 上传 `-SkipDevice`。

## 一键全门禁

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1
powershell -File scripts\smoke.ps1 -SkipDevice
powershell -File scripts\smoke.ps1 -DeviceId L2E0222531014833
```

依次跑 4 个门禁，每个门禁独立子进程，一个红不会截断其它门禁：

| # | 门禁 | 命令 | 失败含义 |
|---|---|---|---|
| 1 | desktop | `scripts\build-desktop.ps1` | 桌面构建/截图/mechanics 基线红 |
| 2 | numeric | `python tests\numeric\run_all.py` | 数值回归红（含未清理的 xfail → unexpected success） |
| 3 | mech-src | `tools\mechanics\gen-mechanics.ps1 -Check` | 生成区与 `mechanics.json` 不同源/被手改 |
| 4 | android | `scripts\build-android.ps1` | APK 构建/真机启动/logcat 红 |

最后汇总 `desktop / numeric / mech-src / android / overall` 并写
`checks\smoke-<时间戳>\summary.txt`；全部绿才 `SMOKE: PASS`（exit 0）。

## 提交前路径守卫（手动，不装 hook）

共享工作区下提交前自查。**`-Branch` 必填且强校验**：HEAD 不在自己分支就拒绝提交（不是提示），
同时拦截构建产物/日志/APK，并可用 `-OwnedPaths` 限定本次只提交自己的路径：

```powershell
# 只看暂存了哪些东西、当前分支对不对
powershell -File scripts\check-staged-paths.ps1 -Branch fix/p0-review-findings

# 服务端提交示例：只允许 scripts/ 与 backend/，并校验提交信息以 [成员名] 结尾。
# 注意：经 powershell -File 传数组时用「逗号分隔的单引号字符串」，不要写成多个参数。
powershell -File scripts\check-staged-paths.ps1 -Branch fix/p0-review-findings `
  -OwnedPaths 'scripts/*,backend/*,.gitignore' `
  -CommitMessage 'feat(infra): ... [LC服务端]'
```

退出码 0 = 干净，1 = 分支不对/有产物/越界路径/提交信息缺 `[成员名]`。**故意不装 git hook**：共享工作区
长期有他人未提交改动，hook 会卡住所有人；合并窗口再启用。

## 产物

```
checks\smoke-<时间戳>\
  desktop\screenshot.png   # 1100×780 桌面截图
  desktop\cargo-build.log
  desktop\result.txt
  numeric.log              # 数值回归完整输出
  mechanics-source.log     # 生成区校验输出
  android\app-debug.apk    # 构建产物副本（check 目录，不入库）
  android\gradle-assembleDebug.log
  android\adb-install.log / adb-start.log / logcat.txt
  android\device-screenshot.png
  android\result.txt
  summary.txt
```
