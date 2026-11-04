# 真机采集方法说明（dump / 截图 / 基线）

> 维护：LC测试。目的：把「uiautomator 在哪些页面能用、失败时长什么样」写死，
> 下一个人不用再试错。适用设备 `L2E0222531014833`（JAD-AL00 / Android 12 / 1228×2700）。

## 1. `uiautomator dump` 的 idle 条件与失败形态

`uiautomator dump` 要求窗口进入 idle 才写文件。LC 的罗盘页 `CompassView` 每个传感器事件
`invalidate()`，地图页 WebView 持续动画，所以：

```
adb shell uiautomator dump /sdcard/ui.xml
# ERROR: could not get idle state.
```

**关键坑**：命令可能仍以退出码 0 结束，但**远程文件根本没有生成**。如果 `/sdcard/ui.xml`
是上一次（甚至别的 App）留下的，`adb pull` 会成功并拿到**过期内容**——本轮就拉到过一份
别的 App 的 15851 字节 dump。

正确做法：

```powershell
adb shell rm -f /sdcard/ui.xml
adb shell uiautomator dump /sdcard/ui.xml
# 只有输出 `UI hierchary dumped to:` 才 pull；pull 后再校验根节点/文本属于 com.luopan
```

## 2. 哪些页面能稳定 dump

| 页面 | 能否 dump | 说明 |
|---|---|---|
| 首次校准弹窗 | ✅ | 静态 Dialog，`ui-first-run.xml` 6056B |
| 盘面列表 `CompassListActivity` | ✅ | 原生 Activity，`ui-dial-list.xml` 10572B |
| 知识 Tab | ⚠️ 不稳定 | 早先成功过（8586B）；D5 之后一次失败（地图 WebView 仍在动），别依赖 |
| 罗盘 Tab | ❌ | 传感器持续 invalidate |
| 地图 Tab | ❌ | WebView 动画 |

## 3. 传感器驱动页面怎么定位控件

1. **先跳到静态页**：要拿 Tab/盘面入口坐标时，先打开盘面列表或首次弹窗 dump，
   或在 `MainActivity` 上按设备已知坐标点、**用 Activity 名断言**（成功进入
   `CompassListActivity` 才算点对），别静默点空。
2. **注入冻结传感器后 dump**（P0-1 DebugBridge）：
   `am broadcast -a com.luopan.fengshui.DEBUG --ef heading 90` 冻住读数后立刻 dump；
   reset 后传感器恢复会再次 idle 超时，复验 reset 要「reset 后立刻再注入」再 dump。
3. 坐标兜底：`dumpsys activity top` 可看 View 树（不含 WebView 内部），或直接用
   dump 到的 `bounds="[x1,y1][x2,y2]"` 算中心，不写死像素。

## 4. 截图与基线

```powershell
adb -s L2E0222531014833 exec-out screencap -p > shot.png   # pwsh 7 字节安全
# 校验：文件 >10KB、尺寸 1228×2700
```

- 基线参考：`docs/qa/baseline-android/round-d5/`（D5 后重冻，见其 `manifest.md` 与
  `SHA256SUMS.txt`；旧 `docs/qa/baseline-android/` 为 02:56 版，不要再用）。
- 截图航向是**设备实际值**（基线为 273.85°），不是注入值；做像素 diff 必须同一航向、
  同一 P0-1 开关态，否则会全红。
- 本轮冻结物里 `logcat.txt` 不入库（日志），证据放 `checks/qa-baseline-d5/logcat.txt`。

## 5. 本轮已知未完成

- 知识页 dump 在 D5 后失败一次，未收录；需要时按 §1 先清远程文件、在知识页等 2s 再试。
- AT-P0-0 的「P0-1 总开关全关」变体基线还没拍，等客户端这轮真机验完后补拍罗盘页。


## 附：真机调试通道（LC客户端，2026-11-05）

1. **广播要整条加引号**：宿主机 → `adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'console.log(42)'"`。
   直接把 `&`/括号放外层，设备 `sh` 会截断或报语法错误；`result=0` 只表示广播发出，**不代表 receiver 收到**。
2. **可观测标记**：`DebugBridge` 收到任何命令都打 `LCDEBUG cmd=<name>`；`js` 命令带 `ValueCallback`，结果回写 `LCJS mapView -> <返回值>`。先看到 `LCDEBUG`、再看到 `LCJS`，才能说注入生效。
3. **历史坑（已修复）**：`1c51e22` 曾把 `url`/`js` 两个命令覆盖掉，表现为「有 `LCDEBUG`、页面无反应」；现已在 develop 上补回。
4. **`url` 命令的边界**：同文档只改 hash（`#in3d`）不触发重载；`file:///...map.html?t=7` 这种带 query 的变体会加载失败。需要重载时用 `js` 发 `location.reload()`，或让页面初始化时读 hash（地图侧待办）。
5. **dump 前置**：注入 heading 冻结传感器后再 dump；`rm -f /sdcard/ui.xml` 再 dump，只有出现 `UI hierchary dumped to:` 才 `pull`，并校验包名是 `com.luopan.fengshui`。
