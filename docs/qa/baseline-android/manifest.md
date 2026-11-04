# Android 基线（真机 L2E0222531014833）· 冻结版

- 冻结时间：2026-11-05 03:00（UTC+8），LC测试
- 用途：后续轮次（轮 C / 轮 B2 / M2）真机回归的对照基线。**本目录只增不改**；
  新一轮另建 `docs/qa/baseline-android/round-*/`，不得覆盖本目录。
- 采集窗口：按调度令限时 10 分钟（02:50–03:00）

## 1. 设备与环境

| 项 | 值 |
|---|---|
| 设备 | HUAWEI JAD-AL00（`L2E0222531014833`） |
| 系统 | Android 12，fingerprint `HUAWEI/JAD-AL00/HWJAD-Q:12/HUAWEIJAD-AL00/104.2.0.186C00:user/release-keys` |
| 屏幕 | 1228×2700（`wm size` Physical） |
| App | `com.luopan.fengshui`，versionName **1.0** / versionCode **1** |
| 安装 | `lastUpdateTime=2026-11-05 02:50:56`（轮 B 修复版 409ad5f 之后） |
| 进程 | `pid=21159`（冻结时） |
| JDK/Gradle | Temurin 17.0.21 / Gradle 8.7 |

## 2. 冻结物（SHA256）

| 文件 | 字节 | SHA256 |
|---|---|---|
| `tab-compass.png` | 1,569,570 | `8320B9C3CEB30E8C4FD27DA8823E3B78B77900DEDB9EDA50D7F869BD208BAC01` |
| `tab-map.png` | 1,950,936 | `1CFB9AD92175AF7203BB590523121BD66EFC36D4F914A692E49F8708C1F724C8` |
| `tab-knowledge.png` | 101,374 | `14AA1F93A1ABC6FEF3E8DE09D55E15AD76B5AEE1607E0CEB71FA5737547480E1` |
| `ui.xml` | 8,586 | `8CE7C65B756EE0F16227F8E9DE3B1DBB68793894AC1D455A61A2F560F3D9AD4D` |
| `probe.csv` | 375 | `A819C0058FBA8DEE97EDC6DCF55FE03E53158FC2EF75298CD8508DF76A10416A` |

- 三张图均为 **1228×2700** 原始分辨率（`adb exec-out screencap -p`，未缩放）。
- `ui.xml`：`uiautomator dump`。**只含原生视图**（三个 Tab、盘面按钮、读数 TextView）；
  `CompassView` 与地图 `WebView` 的内容不在 dump 里（自定义 View / WebView 不暴露节点）。
  采集时 App 在「知识」页，dump 成功（传感器持续 invalidate 时 uiautomator 会报
  `could not get idle state`，需在知识页等 UI 稳定）。
- `probe.csv`：来源 `checks/probe-tmp/probe.csv`（02:40:35），6 个航向的 `read` 输出；
  已用 `tests/numeric/oracle.py` 复核 **6/6 一致**（m/off/fs/d72/d60/sit/jian/fenjin 全对）。

## 3. 构建物

| 项 | 值 |
|---|---|
| APK | `app/android/app/build/outputs/apk/debug/app-debug.apk` |
| APK SHA256 | `17378FDF96C214E972EC8AA32F277C595C968099A266A2B180D7500CCBBCE36D` |
| APK 字节 | 3,230,773 |
| APK mtime | 2026-11-05 02:44:31 |
| APK 内 `assets/map.html` | SHA1 `1B2A1E6E452772D84286BB6BAE1ED0A933433F02`（43,041 B） |
| 构建命令 | `gradle assembleDebug --no-daemon` → `BUILD SUCCESSFUL` |

> 说明：APK 内 `map.html` 与工作区当前文件一致（含 `LC3D pick`，P0-4 JS 已在），
> 但与提交 `ee1330a` 的 blob 不同（`7e5bf77…`）——构建时工作区有未提交的 map.html 改动。
> 这是共享工作树的真实状态，按原样冻结，不重打。

## 4. 运行状态

- 冷启动（02:50 安装后）：`am start -W` → `TotalTime=818ms`（轮 B 首装实测，参考值）
- logcat（02:50 起，564,662 B）：`FATAL EXCEPTION` **0**，`ANR in com.luopan` **0**
  （原始日志：`checks/qa-device-01/logcat-baseline-2.txt`；更早一次 693,059 B 同样 0/0）
- 三盘读数（轮 B 真机验收，已随基线记录）：
  - heading 273.85°：地 酉 / 人 辛 / 天 酉，坐 卯（`checks/qa-device-01/01-compass.png`）
  - 坐向按钮 +180° 后 93.85°：地 卯 / 人 乙 / 天 卯，坐 酉
    （`checks/qa-device-01/09-readout-crop.png`）
- 3D：`LC3D fps=56–60 elev=52 pitch=45 bearing=0→345 zoom=9`，无 FATAL
  （真机 9 项冒烟，见 `checks/qa-device-01/`）

## 5. 源码锚点（冻结时工作区）

| 文件 | SHA256 |
|---|---|
| `app/src/main.rs` | `AFBD537B081187EADAA9BEFA022B780BAA3CFF99CA8BE399627BD34EA47B11D0` |
| `app/src/software.rs` | `2ECFE9DE81AB05364E6B8D01403398424313763283682C144C09DA3C6D1D4946` |
| `app/src/dial.rs` | `F01D7BF14FCDBBB2C42D4A2DE32DD9180E35126C909A1D95DEF6E1922C6C604B` |
| `CompassData.java` | `B2298054B299BE873CF62A91A664B86BFDB3D979C104ED2AAA5958B73FF50987` |
| `CompassView.java` | `65B2CD5AEA06F6F81092A9258E0D4A51258EDF9BAD9B8D80518202BF6205BD35` |
| `MainActivity.java` | `534A6C91FCBA6B6C4E2CBC9C0DFF4BDC7CE85A92C12A6CD41ADDAF9975F78A7E` |
| `assets/map.html` | `CD5C700A4D2E863D0415A61A9413D80CFC40C08484955F9A9DE5D6F6FE4AD623` |
| `app/mechanics.json` | `984C5FB618634B0A1202908243ACFA1DFBD3D1E4EFA0B34DF2A778E0D31F34CA` |
| HEAD | `ee1330a`（分支 `feature/地图-3d-terrain`） |

## 6. 复现命令

```powershell
$s='L2E0222531014833'
adb -s $s shell input tap 205 2640; adb -s $s exec-out screencap -p > tab-compass.png
adb -s $s shell input tap 612 2640; adb -s $s exec-out screencap -p > tab-map.png
adb -s $s shell input tap 1020 2640; adb -s $s exec-out screencap -p > tab-knowledge.png
adb -s $s shell uiautomator dump /sdcard/ui.xml; adb -s $s pull /sdcard/ui.xml ui.xml
adb -s $s logcat -d > logcat.txt
```

## 7. 已知限制

1. `ui.xml` 不含罗盘/地图内容；罗盘是自定义 `CompassView`，地图是 `WebView`。
2. 基线截图是**轮 B 后**的读数（人盘已正确）；轮 C 桌面改动不影响 Android APK。
3. 传感器持续驱动 UI，`uiautomator dump` 可能在 `main`/`map` Tab 失败；冻结时用知识页采集。
4. 基线 APK 的 `map.html` 是工作区未提交版（见 §3），重装 HEAD 版后需重新对照。
