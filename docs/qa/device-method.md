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

- 基线参考：`docs/qa/baseline-android/round-k01/`（K-01 单页基线，见其 `manifest.md` 与
  `SHA256SUMS.txt`）；`round-d5/` 为 K-01 前三 Tab 基线，**已作废**，仅历史对照；
  更早的根目录 `docs/qa/baseline-android/`（02:56 版）不要再用。
- 截图航向是**设备实际值**（基线为 273.85°），不是注入值；做像素 diff 必须同一航向、
  同一 P0-1 开关态，否则会全红。
- 本轮冻结物里 `logcat.txt` 不入库（日志），证据放 `checks/qa-baseline-d5/logcat.txt`。

## 5. 本轮已知未完成

- 知识页 dump 在 D5 后失败一次，未收录；需要时按 §1 先清远程文件、在知识页等 2s 再试。
- AT-P0-0 的「P0-1 总开关全关」变体基线：`round-d5/variant-p01/` 随三 Tab 布局**作废**；
  K-01 后需在 `round-k01/` 按「单页 + 罗盘浮层」重采（见 `docs/qa/k-01-pending.md` P-04/P-05）。
  （同 87654af 构建、同航向 273.85°、on/off 同会话；见其 `manifest.md`）。

## 6. adb 调试广播的 shell 吃参数坑（2026-11-05 客户端实测）

- **现象**：`am broadcast … --es value 'file:///…/map.html#in3d&fs'` 在宿主机 PowerShell
  或设备 `sh` 下，未加引号的 `&` 被当后台符，整条命令被截断；`Broadcast completed:
  result=0` 只表示广播发出，**不代表 receiver 收到**。表现是 `LCDEBUG` 零输出、页面没导航。
- **正确写法**：整条命令包一层引号、值里避免裸 `&`/括号；例如
  `adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'console.log(42)'"`。
  还不行再给值里的 `&` 多一层转义。
- **诊断顺序**：先 `--es cmd js` 打一行 `console.log(42)`，看 `LCDEBUG cmd=js` 确认 receiver
  活着；再打地图 `state()`；最后才谈「没订阅」。抓日志用 `adb logcat -s LCDEBUG LC3D`。
- **`url` 命令的坑**：只改 hash 不会重载（同文档导航不触发 `onload`）；`?t=` 的 file:// 变体
  不加载。要靠 `url` 一次进 3D，必须让 `map.html` 在初始化时读 hash（地图侧已改）。
- **`js` 注入可观测性**：当前 `evaluateJavascript` 没带 `ValueCallback`，页面输出不回写，
  「注入到底生没生效」不可观测；客户端正改为带回调回写 logcat。
- **`heading` 注入的 extra 键是 `value`，不是 `heading`**：`MainActivity` 用
  `it.getFloatExtra("value", 0f)`，正确命令是
  `am broadcast -a com.luopan.fengshui.DEBUG --ef value 90`；`--ef heading 90` 会被忽略。
- **`dumpsys activity broadcasts` 的两个段落别混**：
  - `Broadcast Records` 里出现 `act=com.luopan.fengshui.DEBUG` **只说明广播发过/进过历史**，
    不等于 receiver 已注册；要确认注册，看 **`Registered Receivers:`** 段里有没有 `com.luopan`。
- **根因更正（2026-11-05 05:02 实测）**：之前「注册列表为空/通道死」的判断是错的。
  真正原因是广播**缺 `--es cmd <命令名>`**——`DebugBridge` 先取 `cmd` 再分发，缺了它既不打
  `LCDEBUG` 也不执行动作。补上 `--es cmd heading --ef value 90` 后立刻出现
  `LCDEBUG cmd=heading`、读数 90.00°，`ui dump` 也因传感器冻结而成功。
  同一包上 `--es cmd js --es value '1+1'` 返回 `LCJS mapView -> 2`，通道一直是活的。
- **教训**：`result=0`、`Broadcast Records`、`Registered Receivers` 三段证据要一起看，
  且先发一条 `js 1+1` 当「通道探针」；别用一条缺 `cmd` 的广播去推断 receiver 死了。



## 附B：真机调试通道（LC客户端，2026-11-05）

1. **广播要整条加引号**：宿主机 → `adb shell "am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value 'console.log(42)'"`。
   直接把 `&`/括号放外层，设备 `sh` 会截断或报语法错误；`result=0` 只表示广播发出，**不代表 receiver 收到**。
2. **可观测标记**：`DebugBridge` 收到任何命令都打 `LCDEBUG cmd=<name>`；`js` 命令带 `ValueCallback`，结果回写 `LCJS mapView -> <返回值>`。先看到 `LCDEBUG`、再看到 `LCJS`，才能说注入生效。
3. **历史坑（已修复）**：`1c51e22` 曾把 `url`/`js` 两个命令覆盖掉，表现为「有 `LCDEBUG`、页面无反应」；现已在 develop 上补回。
4. **`url` 命令的边界**：同文档只改 hash（`#in3d`）不触发重载；`file:///...map.html?t=7` 这种带 query 的变体会加载失败。需要重载时用 `js` 发 `location.reload()`，或让页面初始化时读 hash（地图侧待办）。
5. **dump 前置**：注入 heading 冻结传感器后再 dump；`rm -f /sdcard/ui.xml` 再 dump，只有出现 `UI hierchary dumped to:` 才 `pull`，并校验包名是 `com.luopan.fengshui`。

## 附C：收官轮一把跑完的实证（LC测试，2026-11-05 05:33）

一条命令：`pwsh -File tests\at\run-final.ps1 -Apk <app-debug.apk>`（18 项，exit 0/1/2/4）。

本轮踩到并已写进脚本的五个坑：

1. **不注入航向就 dump 一定超时**：真机传感器在跑 → `CompassView` 每帧重绘 →
   `uiautomator dump` 报 `ERROR: could not get idle state.`（约 11 秒后失败，且**不产出文件**）。
   先发 `--es cmd heading --ef value 0` 冻结，再 dump 就 2 秒成功。
   所以「dump 失败」的第一嫌疑是「没冻结」，不是「页面不可 dump」。
2. **`logcat` 环形缓冲会把注册日志刷掉**：`bridge registered` 常被 WebView 日志挤出去，
   拿它当通道判据会误判「通道死」。判据改成端到端探针：
   `--es cmd js --es value '1+1'` → 期望 `LCJS ... -> 2`；`bridge registered` 只作辅助证据。
3. **装完 `am start` 不一定在前台**：本轮 TD-016 一次失败就是华为应用市场的安装风险检查
   Activity 把我们的任务 `moveTaskToBack` 顶掉，点按全落在桌面（`picks=0`、`fps=0`）。
   点按前必须 `dumpsys activity activities | grep mResumedActivity` 校验前台，并重试拉起。
4. **`js` 广播的引号**：宿主机单引号包 JS、`--es value`，JS 内部只用双引号
   （如 `document.querySelector("[id=srcInfo]")`）；PS 里先把 JS 放单引号变量再拼进命令串，
   否则 PS 会在 JS 的内层双引号处提前截断（表现为广播发出、页面无反应、无 `LCJS`）。
5. **读盘弹层第 14 行在首屏外**：`ScrollView` 里的 14 行不会全进 dump，
   需 `input swipe 614 2000 614 1150 400` 上滑后重拍，两次取并集才 14/14。
6. **判「分享图上有没有角注」只能判图，不能判 DOM**：客户端 `111c4fb` 的做法是把
   「底图·审图号」画进图内（面板上缘之上），页面里的 `#srcInfo` 仍在面板后面、矩形不变。
   继续拿 DOM 矩形判会永远 FAIL。现在的判据：面板带上方条带 y1400–1850 内
   金色文字行 ≥100（无角注旧构建同区域实测 54，有角注 143）。
7. **中文不要经 PowerShell 管道进 Python / 也不要经管道出 Python（全组通用坑）**：
   本机实测 `$p = & python -c "print('子')"` 得到的是 `��`（长度 2）、`$p -eq '子'` 为 **False**——
   Python 在管道模式下按 locale（cp936）编码 stdout，PowerShell 侧按 UTF-8 解码，中文就废了。
   我 05:17 那轮被自己骗过一次：`golden` 打出来是 `�� 94.0��`，与 dump 比对结果 **0/14 全不符**，
   差点当成产品 bug。改成 **Python 直写 UTF-8 文件**（`golden_readsheet.py --out path`），
   PS 用 `Get-Content -Raw -Encoding UTF8` 读，同一数据立刻 **14/14**；
   退一步至少 `$env:PYTHONIOENCODING='utf-8'`。
   逆向那边的同类表现是 `git show` 进管道再让 Python 按 cp936 读 → 假「MISSING」。
   **结论：判据只用原生判断**（`git diff --quiet` / `git grep` / 直接读文件），中文不要走管道。
