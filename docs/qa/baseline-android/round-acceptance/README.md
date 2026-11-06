# Android 基线冻结 · round-acceptance

状态：**已冻结（2026-11-06）**——读数/截图/门禁结果均为本目录实测，见 `manifest.md` 与 `SHA256SUMS.txt`。

## 1 口径

| 项 | 值 |
|---|---|
| 轮次 | `round-acceptance`（收官验收轮） |
| 构建分支 | `develop` |
| 构建提交 | `f332b9c`（只含 QA 留档文档；应用代码同 `0dda97e`，客户端最后一笔功能提交为 `857a471`） |
| APK | `app/android/app/build/outputs/apk/debug/app-debug.apk`，clean build |
| APK 大小 | 7,059,441 B |
| APK sha256 | `a928023ff12334c903094a929d1e89a8152cd238ef9dc751974a4fc7cf7e07bb` |
| 设备 | `L2E0222531014833`（JAD-AL00 / 1228×2700） |
| 采集脚本 | `tests/numeric/tools/freeze_android_baseline.ps1` |

说明：APK 含签名与构建时间戳，**sha256 只对应本次产物**，不作为可复现校验（同 `docs/qa/final-gates-20261106.md` §备注）。
本机 clean build 与门禁轮产物（7,139,677 B）内容一致，逐条比对 APK 内条目仅 `classes3.dex` 差 8 B，体积差来自对齐/压缩，不是缺资源。

## 2 结构摘要（写入 manifest 的 `-Structure`）

单页全屏地图 + 罗盘浮层，无底部导航（K-01 起）：

- **左列 8**：全地图工具 / 截屏 / 超清优化 / 海拔查询 / 罗盘 / 路网 / 等高线 / 更多
- **右列 10**：图层 / 叠加 / 山脉 / 水系 / 探索 / 指北针 / + / − / 定位 / 3D
- **更多面板 16 格**（4×4）：罗盘 / 超清地图 / 看山脉 / 看水系 / 海拔查询 / 天气 / 历史影像 / 等高线 / 收藏点 / 测量 / 画笔 / 钓点 / 轨迹记录 / 路网 / 地理影像 / 超清优化；标题栏右侧「设置」「功能开关（调试）」
- **罗盘浮层**：左列 6（截屏 / 等高线 / 罗盘复位 / 罗盘锁定 / 读盘 / 贴地）、右列 4（地图 / 实景 / 坐南朝北 / 指针开关）、底部工具条 5（切换 / 旋转 / 缩放 / 透明度 / 天支线）
- **18 项入口**（按 `MainActivity` 的 `onToolClick` / 更多面板分支逐条核对，2026-11-06，HEAD `f332b9c`）：
  - F-01 全地图工具 → `ToolsManagerActivity`（左列）
  - F-02 左列「超清优化」/ 更多「超清地图」→ `SuperMapActivity`
  - F-03 左列「海拔查询」/ 更多「海拔查询」→ `ElevationActivity`
  - F-04 左列「路网」/ 更多「路网」→ `setRoadNet` 图层开关（不换页）
  - F-05 右列「叠加」→ `OverlayActivity`
  - F-06 右列「山脉」/ 更多「看山脉」→ `MountainActivity`（带 `lc_lat/lc_lng`）
  - F-07 右列「水系」/ 更多「看水系」→ `WaterActivity`（带 `lc_lat/lc_lng`）
  - F-08 右列「探索」→ `DiscoveryActivity`
  - F-09 更多「天气」→ `WeatherActivity`
  - F-10 更多「历史影像」→ `HistoryImageActivity`
  - F-11 更多「收藏点」→ `InterestListActivity`
  - F-12 更多「测量」→ 地图内测量态（不换页）
  - F-13 更多「画笔」→ 地图内绘制态（不换页）
  - F-14 更多「钓点」→ `FishingActivity`
  - F-15 更多「轨迹记录」→ `TrackListActivity`
  - F-16 更多「地理影像」→ `GeographicImageryActivity`
  - F-17 罗盘工具条「透明度」/「天支线」→ 罗盘显示面板（仅罗盘模式可见）
  - F-18 罗盘工具条「切换」→ `CompassListActivity`（盘面目录）

核对方式：从 `MainActivity` 提取 6 组文案数组（左 8、左罗盘 6、右 10、右罗盘 4、工具条 5、更多 16）
与 18 条 `case`/更多分支，逐项与本摘要比对，无差异。

## 3 运行命令（设备回来直接跑）

```powershell
# 0) 前置
$env:PATH="$env:JAVA_HOME\bin;C:\Users\Administrator\tools\gradle_tmp\gradle-8.7\bin;$env:PATH"
adb devices                                   # 必须先看到 L2E0222531014833
adb -s L2E0222531014833 shell settings put global window_animation_scale 0
adb -s L2E0222531014833 shell settings put global transition_animation_scale 0
adb -s L2E0222531014833 shell settings put global animator_duration_scale 0
# 唤醒解锁：keyevent 224 → swipe 614 2200 614 900 250 → 注 PIN（见 secrets/device-pin.txt，禁打印）
#          → keyevent 66 → wm dismiss-keyguard → dumpsys window | grep mCurrentFocus

# 1) 装包（绕开 PackageInstaller UI）
adb -s L2E0222531014833 push app\android\app\build\outputs\apk\debug\app-debug.apk /data/local/tmp/lc.apk
adb -s L2E0222531014833 shell pm install -r -t /data/local/tmp/lc.apk

# 2) 冻结（-SkipInstall：包已装好，脚本内 install 会再走一次 PackageInstaller）
pwsh -File tests\numeric\tools\freeze_android_baseline.ps1 `
  -Apk (Resolve-Path app\android\app\build\outputs\apk\debug\app-debug.apk) `
  -OutDir docs\qa\baseline-android\round-acceptance `
  -Round round-acceptance -Branch develop -BuildHead 0dda97e `
  -Structure "<第 2 节那行摘要>" -SkipInstall
```

## 4 本目录预期产物（脚本生成）

`manifest.md`、`numeric-gate.txt`、`am-start-w.txt`、`nodes-k01.csv`、`logcat.txt`、`SHA256SUMS.txt`，
截图 `k01-map.png` / `k01-more.png` / `k01-compass.png` / `k01-compass-needle-on.png` /
`k01-compass-needle-off.png` / `k01-map-after-hide.png`。

本目录共 **18 个文件**：6 张截图 + 5 份 UI dump + 7 份记录（`manifest.md`、`nodes-k01.csv`、
`numeric-gate.txt`、`am-start-w.txt`、`logcat.txt`、`SHA256SUMS.txt`、`README.md`）。
`SHA256SUMS.txt` 只列 16 项——**不含 `README.md` 与本清单自身**（清单无法自证），属算法约定，不是缺文件。
