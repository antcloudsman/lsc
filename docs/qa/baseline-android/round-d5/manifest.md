# Android 基线（真机 L2E0222531014833）· D5 重冻

- 冻结时间：2026-11-05 04:07:59（UTC+8），LC测试
- 设备：HUAWEI JAD-AL00 / Android 12 / `HUAWEI/JAD-AL00/HWJAD-Q:12/HUAWEIJAD-AL00/104.2.0.186C00:user/release-keys`
- App：versionName 1.0 (1)，lastUpdateTime 2026-11-05 04:04:56，pid 19561
- 构建：`develop` HEAD `87654af`（含 D5 9a5d075、双 WebView 57fe956、全屏退出/12 源 3a52ae9）
- APK：`app-debug.apk` 3397959 B，SHA256 `A4ACD6E4FAC5D2942380CC5A2A9688973E95F59B01EC2B38C6EB31E15CA46D4F`
- 冷启动：`pm clear` 后首次 `am start -W` TotalTime = 845 ms
- 屏幕：Physical size 1228x2700
- logcat：`FATAL EXCEPTION` **0** / `ANR in com.luopan` **0**

## 采集状态（可复现）

1. `adb shell pm clear com.luopan.fengshui` → 冷启动 → 首次校准弹窗点「跳过」（截图 `first-run-calibration.png`）。
2. P0-1 默认态：**磁北 / 偏角 +0.0°**；盘面 = **常用罗盘（当前）**；读数 273.85°（设备实际航向，非注入）。
3. 点「盘面（4 组 · 常用 5）」（614,1780）→ `CompassListActivity` 截图 `disc-dial-list.png` + `ui-dial-list.xml`。
4. 地图 Tab / 知识 Tab 各一张截图（底图默认天地图地形，审图号「未确认」）。

## 冻结物

- `disc-dial-list.png`：463656 B
- `first-run-calibration.png`：805991 B
- `logcat.txt`：571733 B
- `tab-compass.png`：1401447 B
- `tab-knowledge.png`：101842 B
- `tab-map.png`：1789704 B
- `ui-dial-list.xml`：10572 B
- `ui-first-run.xml`：6056 B

SHA256 见 `SHA256SUMS.txt`。

## 限制与口径

1. `ui-*.xml` 只含原生视图；CompassView/WebView 内容不在 dump 里。
2. 罗盘/地图页传感器持续 invalidate，`uiautomator` 会 idle 超时；本基线只在**静态页**（首次弹窗、盘面列表）取到 dump，知识页 dump 本次失败，未收录。采集方法与坑（含「失败但退出码 0、pull 到别家过期 dump」）见 `docs/qa/device-method.md`。
3. 截图航向是设备实际 273.85°，不是注入值；同一基线内三张图同一次会话、同一航向。
4. 本基线是 **D5 之后默认态**（含 P0-1 磁北/偏角行）。AT-P0-0 的 diff 请以本目录为参照；若需要「P0-1 总开关全关」的变体，另跑一次（总开关关闭后重拍罗盘页即可，盘面/地图页不受影响）。

