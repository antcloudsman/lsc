# 收官全量门禁留档（2026-11-06）

- 出：LC服务端｜用途：收官报告 §6 的依据（可重跑门禁的原始输出留档）
- 基线：`develop` @ **`0dda97e`**（应用代码同 `857a471`：`git diff 857a471 0dda97e -- app/` = **0 文件**）
- 执行：2026-11-06 00:02–00:07（Asia/Shanghai），本机共享构建环境
- 判定：**PASS**（七项全绿；本文件不含真机项，真机 18 项证据见 `docs/features/00-FINAL.md` 与台账 v14）

环境：Windows｜JDK `17.0.21`（Temurin）｜Gradle `8.7 --no-daemon`｜Python `3.13.14`｜cargo `1.99.1`
真机 leg：**跳过**（`-SkipDevice`，本轮只验构建与自动化门禁）
天地图 key：仓库外 `E:\APPDetector\secrets\tdt.properties`（构建期注入，日志不含 key 值）

## 1. 结论表

| # | 项 | 命令 | 结果 | 关键输出行 |
|---|---|---|---|---|
| 1 | 文案/红线门禁 | `python tests\copy\copy_gate.py` | PASS | `COPY: PASS`（3/3 检查） |
| 2 | 密钥泄露门禁 | `python tests\secrets\secret_gate.py` | PASS | `SECRET: PASS`（2/2 检查） |
| 3 | 一键全门禁 | `scripts\smoke.ps1 -SkipDevice` | PASS | `SMOKE: PASS`（6/6 门禁 `overall : PASS`） |
| 4 | 数值回归 | `python tests\numeric\run_all.py` | PASS | `tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0` / `RESULT: PASS` |
| 5 | 桌面单元测试 | `cargo test`（`app\`） | PASS | `test result: ok. 5 passed; 0 failed` |
| 6 | Debug 打包 | `gradle :app:assembleDebug --no-daemon` | PASS | `BUILD SUCCESSFUL`；7,139,677 B |
| 7 | Release 打包 | `gradle :app:assembleRelease --no-daemon` | PASS | `BUILD SUCCESSFUL`；6,330,540 B（unsigned） |

## 2. 原始输出（逐行照抄）

### 2.1 copy 门禁（`tests\copy\copy_gate.py`）

```
[PASS] copy: 用户可见文案零命中 会员|付费|VIP|开通|升级|订阅|解锁|充值|去水印（28 个 values/*.xml，注释已剔除）
[PASS] audi: 发行面无原版审图号（扫 133 个已跟踪文件，注释与 docs/ · tests/ · *.md 已豁免）
[PASS] tiles: 发行面无原版私有瓦片域 21atcloud / xinzhi.space（扫 133 个已跟踪文件，注释与 docs/ · tests/ · *.md 已豁免）

COPY: PASS
```

### 2.2 secret 门禁（`tests\secrets\secret_gate.py`）

```
[PASS] key: 2 个 key × 384 个已跟踪文件，零命中（tdt.server.key←E:\APPDetector\secrets\tdt.properties，tdt.web.key←E:\APPDetector\secrets\tdt.properties，文件=E:\APPDetector\secrets\tdt.properties）
[PASS] hex: assets/*.html 与 *.java 无 32 位十六进制串

SECRET: PASS
```

> 注：`map.html` 旧第三方 key 已由地图侧清除，`hex` 启发式从「1 处 WARN」转为干净。

### 2.3 一键全门禁（`scripts\smoke.ps1 -SkipDevice`）

```
LC SMOKE | out=E:\APPDetector\develop-int\checks\smoke-20261106-000250
copy     : PASS  (log: ...\checks\smoke-20261106-000250\copy.log)
secret   : PASS  (log: ...\checks\smoke-20261106-000250\secret.log)
desktop  : PASS  (logs: ...\checks\smoke-20261106-000250\desktop)
numeric  : PASS  (tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0)
mech-src : PASS  (log: ...\checks\smoke-20261106-000250\mechanics-source.log)
android  : PASS  (build only, device skipped; logs: ...\checks\smoke-20261106-000250\android)
overall  : PASS
SMOKE: PASS
```

门禁内部 PASS 行（桌面/单源/Android key 注入）：

```
[PASS] cargo build exited 0
[PASS] screenshot 1100x780, 270394 bytes
[PASS] mechanics.json: 19 values, 0 estimated, 0 duplicate names
PASS  Rust generated region is up to date
PASS  Java generated region is up to date
PASS  all numeric values[] entries still appear in the end sources
[PASS] JAVA_HOME: C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9
[PASS] gradle: C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin\gradle.bat
[PASS] Android SDK: C:/Users/Administrator/AppData/Local/Android/Sdk
BUILD SUCCESSFUL in 44s
[PASS] assembleDebug OK, 6972 KB
```

### 2.4 数值回归（`tests\numeric\run_all.py`）

```
tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
CARGO: test-binary=app\target\debug\deps\luopan-a3ff9c2d47b522fa.exe sha256=ab116ad6bdb12a7057318f82e5e998c8009917d4f83e2c1b8fa0a0b1c5ab2cc8
CARGO: PASS
RESULT: PASS
```

（数值套件内含 Rust 固定测试 `CARGO| test result: ok. 5 passed; 0 failed`，与 §2.5 的独立 `cargo test` 一致。）

### 2.5 桌面单元测试（`cargo test`，在 `app\`）

```
running 5 tests
test result: ok. 5 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s
```

### 2.6 Debug 打包

```
BUILD SUCCESSFUL in 12s
34 actionable tasks: 34 up-to-date
DEBUG_APK path=E:\APPDetector\develop-int\app\android\app\build\outputs\apk\debug\app-debug.apk bytes=7139677 sha256=FF97AE8A522708E261B314E7B2BAB2F80DCD789268153F0A38C987DE5B65A435
```

### 2.7 Release 打包

```
BUILD SUCCESSFUL in 42s
43 actionable tasks: 43 executed
RELEASE_APK path=E:\APPDetector\develop-int\app\android\app\build\outputs\apk\release\app-release-unsigned.apk bytes=6330540 sha256=A9AF70965830531DCDB6915A2C3A529BA5723648961D6F962D7A0696C1B88562
```

## 3. 产物

| 类型 | 文件 | 字节数 | sha256 | 签名 |
|---|---|---|---|---|
| debug | `app-debug.apk` | 7,139,677 | `FF97AE8A522708E261B314E7B2BAB2F80DCD789268153F0A38C987DE5B65A435` | debug keystore |
| release | `app-release-unsigned.apk` | 6,330,540 | `A9AF70965830531DCDB6915A2C3A529BA5723648961D6F962D7A0696C1B88562` | **unsigned**（未配 signingConfig） |

- sha256 对应**本次产物**；APK 含签名/时间戳，重打可能不同，不作为可复现校验用。
- 两个 APK 都在 `app\android\app\build\outputs\`（git-ignored），**不入库**；本文件只留字节数与哈希。
- release 未签名：正式分发要先配签名（不在本轮范围）。

## 4. 口径与边界

1. **只覆盖可重跑门禁**：本文件是收官报告 §6 的技术依据；18 项功能验收的真机证据（截图/日志/读数）在 `docs/features/00-FINAL.md`、`docs/qa/` 各专项文件与台账 v14，不在此重复。
2. **跑的过程中 develop 前进过**：执行时 tip = `0dda97e`；跑完发现被产品推进到 `c4d6b20`（仅 `docs/features/00-FINAL.md`、`00-STATUS.md` 两个文件，`git diff --stat 0dda97e c4d6b20` 已核）。`app/` 树与 `857a471` **0 差异**，因此本结论对 `0dda97e` 基线有效，docs 改动不影响任何门禁输入。
3. **不臆造数据**：表中字节数、哈希、测试计数均来自本次命令输出；未给性能/容量结论（构建耗时只作记录，不推断）。
4. **secret 门禁的 key 来源**：仓库外 `E:\APPDetector\secrets\tdt.properties`；日志与本文均不含 key 值。
5. 真机 leg 本轮显式跳过，**不能**据此声称"真机启动无崩溃"；那部分以既有 18 项真机证据为准。

## 5. 复跑（照抄即可）

```powershell
# 0) 确认基线
git -C E:\APPDetector\develop-int log -1 --oneline        # 期望 0dda97e

# 1) 文案 + 密钥
python tests\copy\copy_gate.py
python tests\secrets\secret_gate.py

# 2) 六门禁（含桌面构建/截图、数值、单源、Android 构建）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke.ps1 -SkipDevice

# 3) 数值 + 桌面单测
python tests\numeric\run_all.py
Push-Location app; cargo test; Pop-Location

# 4) 打包与哈希
$env:JAVA_HOME='C:\Users\Administrator\tools\jdk17_tmp\jdk-17.0.21+9'
$gradle='C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin\gradle.bat'
& $gradle -p app\android :app:assembleDebug   --no-daemon
& $gradle -p app\android :app:assembleRelease --no-daemon
Get-ChildItem app\android\app\build\outputs\apk -Recurse -Filter *.apk |
  ForEach-Object { "{0} bytes={1} sha256={2}" -f $_.Name, $_.Length, (Get-FileHash $_.FullName -Algorithm SHA256).Hash }
```
