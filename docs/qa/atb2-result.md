# AT-B2 验收记录（圈层文字换表 + 当前格高亮）

- 日期：2026-11-05 03:19–03:27（UTC+8）
- 被测：`develop` = `f16b755`（含 B2 `662c006`、高亮/`LCRING`/`--dump-rings` `0bd22f8`）
- 构建：`develop` 的 detached worktree；桌面 `cargo build`（exit 0）、Android `gradle assembleDebug`（BUILD SUCCESSFUL，APK 3,212,646 B）
- 设备：`L2E0222531014833`（HUAWEI JAD-AL00 / Android 12）
- 结论：**通过**

## 1. 桌面 `--dump-rings`（8 方位 × 14 环）

```powershell
cd E:\APPDetector\wt\lc-atb2\app
$env:CARGO_TARGET_DIR='E:\APPDetector\output\app\target'; cargo build
E:\APPDetector\output\app\target\debug\luopan.exe --dump-rings <h>   # h = 0/45/90/135/180/225/270/315
```

| 项 | 结果 |
|---|---|
| 行数 | 112（8×14） |
| 与 `rings[].labels` 比对 | `i` / `center` / `text` **0 不符** |
| `center` 口径 | 公式原值（TYPE_5/6 的 0° 输出 `360.00`，不归一化） |
| 证据 | `checks/qa-atb2/dump-rings.txt`、`compare-desktop.txt` |

## 2. 真机 `LCRING`（实际航向，非注入）

真机上拖动 `CompassView`（关闭传感器跟随），每停稳 1.5s 取日志：

```powershell
adb -s L2E0222531014833 install -r app-debug.apk
adb -s L2E0222531014833 logcat -c; adb -s L2E0222531014833 shell am start -W -n com.luopan.fengshui/.MainActivity
# 拖动后： adb -s L2E0222531014833 logcat -d | Select-String LCRING
```

| 项 | 结果 |
|---|---|
| 行数 | 616（44 次采样 × 14 环） |
| 实际航向 | 8 个：94.04 / 105.8 / 207.27 / 207.36 / 209.94 / 273.56 / 273.57 / 273.85 |
| 与 `rings[].labels` 比对 | `i` / `center` / `text` **0 不符**（按每行实际 h 复算下标，不引用客户端 golden） |
| 冷启动 | `am start -W` TotalTime 871ms |
| FATAL / ANR | **0 / 0** |
| 证据 | `checks/qa-atb2/device-logcat.txt`、`compare-device.txt`、`device-compass.png` |

## 3. 当前格高亮（两端）

| 端 | 结果 | 证据 |
|---|---|---|
| Android | 指针下金/绿/蓝 4px 亮线可辨 | `device-highlight-crop.png` |
| 桌面 | 指针处分金环金色标记可见；线宽/对比与 Android 不同（已在轮 C 差异清单内） | `desktop-compass.png`、`desktop-highlight-crop2.png` |

## 4. 四向量（AT-B2 指定）

| 位置 | 期望（`rings[].labels`） | 结果 |
|---|---|---|
| 60 龙 0° | 壬子 庚子 | PASS（桌面 dump + 真机 LCRING + 单测 `test_b2_golden`） |
| 60 龙 170° | 丙午 甲午 | PASS（同上） |
| 72 龙 0° | 戊子 | PASS（同上） |
| 120 分金 0° | 甲山 戊子 | PASS（同上） |

## 5. 限制与说明

1. 真机航向来自拖拽停稳后的实际值（`LCRING` 每条自带 h），不是注入；`LCRING` 有 2 条/秒节流。
2. 整圈文字按总监裁决**不画**（r=232、120 格 3° 弧长 10–12px 必然重叠），验收对象是
   「当前格高亮可辨 + 文字在读数面板」，不是「整圈文字不重叠」；整幅位图盘面排轮 E。
3. `toDataURL` 截图退路未在本轮触发（PixelCopy 路径正常）。
