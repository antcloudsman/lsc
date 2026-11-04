# AT-P0-0 · 实验开关页（隐藏入口）验收用例

- 作者：LC产品｜执行：LC测试｜日期：2026-11-05
- 关联：`docs/product/PRD-v0.1.md`（v0.3）§3 P0-0、§0.1 T-3
- 状态：**未实现 → 当前 BLOCKED**；实现落地后按本文件直接执行
- 判定：`PASS` / `FAIL` / `BLOCKED`（前置缺失） / `N/A`（不适用）
- 证据目录：`docs/qa/runs/<yyyyMMdd-HHmm>-P0-0/`（logcat.txt、ui-*.xml、shots/*.png、notes.md）

---

## 0. 实现侧前置（缺任何一条 → BLOCKED）

| # | 要求 | 提供方 | 说明 |
|---|---|---|---|
| K1 | 隐藏入口：**连点「知识库」页签 7 次**（相邻两次间隔 <500ms）打开开关页；≤6 次不出现 | LC客户端 | 只改点击监听，不加可见图标 |
| K2 | 开关页标题「实验功能」；容器 id `feature_panel`；每行文本含 PRD 编号（如「P0-1 读数可信包」）；开关控件 id `switch_<feature_key>` | LC客户端 | 供 `uiautomator dump` 定位 |
| K3 | 开关状态落盘 `shared_prefs/lc_features.xml`，key 形如 `feature.record.enabled`（boolean） | LC客户端 | debug 包可 `run-as` 读 |
| K4 | 全关时页面无任何增量 UI（按钮/状态条/弹窗/新权限） | LC客户端 | 这是基线不回归的前提 |
| K5 | debug 航向注入（与 P0-1 的 `DebugBridge` 共用：`--es cmd heading --ef value <float>`，**保持到 `reset` 或下一次真实传感器采样**） | LC客户端 | SW-006 需要固定指针位置：罗盘针随传感器/动画走，不注入则逐像素 diff 必非 0 |

> 基线对照物：`docs/qa/baseline-android/round-d5/`（三 Tab 截图 + ui dump + probe.csv + SHA）+ `round-d5/variant-p01/`（P0-1 开/关两张罗盘页）。**整图像素 diff 已作废**：罗盘叠在活的 WebView 地图上、底部面板半透明，地图瓦片变化会把整张图污染（实测 on/off 整图 diff **886,847 px**、bbox 几乎全屏）。判「全关」按 SW-006 的三条新口径。

---

## 1. 环境与命令

```powershell
# 仓库根：E:\APPDetector\output
$DEV = 'L2E0222531014833'
$PKG = 'com.luopan.fengshui'

# 构建 + 安装 + 冒烟（自带设备检查）
pwsh -File scripts\build-android.ps1 -DeviceId $DEV -OutDir checks\at-p0-0

# 抓 UI 树（找文本/资源 id）
adb -s $DEV shell uiautomator dump /sdcard/ui.xml
adb -s $DEV pull /sdcard/ui.xml .\ui.xml

# 截图（PowerShell 直接用 `>` 重定向会把 PNG 写成文本，必须设备内截 + pull）
adb -s $DEV shell screencap -p /sdcard/shot.png
adb -s $DEV pull /sdcard/shot.png .\shot.png

# 读开关落盘（文件不存在 = 功能未实现，按 BLOCKED 记录，不要当成 false）
adb -s $DEV shell run-as $PKG cat shared_prefs/lc_features.xml

# 杀进程 / 清数据
adb -s $DEV shell am force-stop $PKG
adb -s $DEV shell pm clear $PKG
```

> 执行器（LC测试 已落）：`pwsh -File tests\at\run-at.ps1 -Suite P0-0`——先静态扫前置，缺则 exit 2 并在 `docs/qa/runs/<ts>-P0-0/notes.md` 列出缺哪条，全齐才碰真机。

按文本点击的小工具（本文件后续用例都用到）：

```powershell
function Tap-Text([string]$Text) {
  adb -s $DEV shell uiautomator dump /sdcard/ui.xml | Out-Null
  adb -s $DEV pull /sdcard/ui.xml .\ui.xml 2>$null | Out-Null
  $xml = Get-Content .\ui.xml -Raw
  $m = [regex]::Match($xml, 'text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if (-not $m.Success) { throw "找不到文本: $Text" }
  $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
  $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
  adb -s $DEV shell input tap $x $y
}
```

> 注意：`uiautomator` 看不到 WebView 内部 DOM（地图按钮），但底部三 Tab 是原生 `Button`，可以按文本点。

---

## 2. 用例

| ID | 层级 | 步骤 | 预期 | 证据 |
|---|---|---|---|---|
| SW-001 | 入口阈值 | 1) `pm clear` 后启动；2) 连点「知识库」**6 次**（间隔 300ms）；3) dump UI；4) 再点 1 次（共 7 次） | 6 次后 `feature_panel` **不存在**；第 7 次后出现，标题「实验功能」 | ui-6.xml / ui-7.xml |
| SW-002 | 面板内容 | 打开面板，dump UI | 行数 = 当前已实现增量数；每行含 PRD 编号 + 一行说明；**没有**「全部开启 / 全部关闭」总开关 | ui-7.xml + 截图 |
| SW-003 | 落盘 | 打开某开关 → `run-as cat` → `force-stop` → 重开 → 打开面板 | xml 中对应 key 为 `true`；重启后面板显示仍为开 | xml 片段 + 两张截图 |
| SW-004 | 关闭无残留 | 逐项打开再关闭（P0-1 状态条 / P0-2 存记录入口 / P0-4 3D 点选） | 打开时入口出现；关闭后入口/状态条/日志全部消失；无需重启 | 每个功能 2 张截图 |
| SW-005 | 不多要权限 | 基线 APK 与含开关页 APK 各执行 `dumpsys package $PKG`，对比 `requested permissions` | 权限集合完全相同 | 两份 dumpsys 片段 |
| SW-006 | 全关即基线（**三条新口径**，整图 diff 作废） | 1) 全关并注入固定航向（如 0.0，K5）；2) **行级**：`ui.xml` 比对——只允许「偏角/磁北」相关行消失，读数/山名/14 行文字不变；3) **落盘**：`run-as cat shared_prefs/lc_prefs.xml` 得 `p0_1_enabled=false`；4) **面板区裁剪 diff**：同航向、同贴面下裁 `y≈1850–2500`（1228×2700 屏实测）比像素，必须 0；地图区允许差异但需在备注写明「瓦片未冻结」 | 三条全过才 PASS。**整图 diff 不作数**（variant-p01 实测 886,847 px 全屏污染）；未注入航向的对比不作数 | `compass-p01-on/off.png`（round-d5/variant-p01）+ 面板区 diff 值 + `prefs-off.xml` + 行级比对结果 |
| SW-007 | 不干扰罗盘 | 面板打开停留 3s → 关闭 → 观察罗盘 5s | 面板期间无崩溃；关闭后罗盘继续跟转、读数随手机转动变化 | logcat 无 FATAL + 读数截图 |
| SW-008 | 重复进入 | 连续 3 次「7 连点 → 返回 → 7 连点」 | 每次都能打开/关闭，无 ANR、无重复面板叠加 | logcat + ui dump |
| SW-009 | 非 debug 卫生 | 若有 release 构建：执行 P0-1/P0-4 的 debug 广播 | release 包无响应、无日志（debug 广播不进 release） | logcat 空 |

**SW-006 的两段脚本**（Python + Pillow，环境自带）：

```python
# 1) 面板区裁剪 diff（同航向、同贴面；地图区不参与）
from PIL import Image, ImageChops
a = Image.open("compass-p01-on.png").convert("RGB")
b = Image.open("compass-p01-off.png").convert("RGB")
assert a.size == b.size, (a.size, b.size)
box = (0, 1850, a.size[0], min(2500, a.size[1]))   # 1228x2700 实测；其他机型按比例换算
d = ImageChops.difference(a.crop(box), b.crop(box))
print("panel diff pixels:", sum(1 for p in d.getdata() if p != (0, 0, 0)))
# 期望 0。非 0 时先确认两图同 heading、贴图已冻结，再判 FAIL
```

```python
# 2) ui.xml 行级：全关后不应再有「偏角 / 磁北」行；读数行必须与注入航向一致
import re
xml = open("ui-off.xml", encoding="utf-8").read()
texts = re.findall(r'text="([^"]*)"', xml)
assert not any(("偏角" in t or "磁北" in t) for t in texts), "P0-1 行未消失"
print("reading:", [t for t in texts if "°" in t][:3])
```

```powershell
# 3) 落盘：全关后 p0_1_enabled 必须为 false
adb -s $DEV shell run-as com.luopan.fengshui cat shared_prefs/lc_prefs.xml
# 期望包含：<boolean name="p0_1_enabled" value="false" />
```

---

## 3. 判定规则

- **PASS**：SW-001…SW-007 全过（SW-008/009 视构建类型）；每条都有证据文件。
- **FAIL**：任一预期不满足。特别注意两类必须判 FAIL：
  1. 全关状态下仍有可见差异（SW-006）—— 说明增量污染基线；
  2. 关掉功能后入口还残留（SW-004）—— 说明没有真正走开关。
- **BLOCKED**：K1–K4 任一未提供，在 notes.md 写明缺哪条，**不要**用「手工点 7 次试试」代替。
- **N/A**：SW-009 仅在存在 release 构建时执行。

---

## 4. 回归（每次改开关机制都要重跑）

1. `shell pm clear` → 冷启动三 Tab 均正常。
2. `scripts/build-android.ps1` 通过（无 FATAL）。
3. `python tests/numeric/run_all.py` 全绿（开关页不应碰读数链路）。

---

## 5. 风险与备注

- 连点阈值是 7 次、间隔 <500ms：若用户在真机上偶发误触，允许把「6 次不出现」作为硬边界，7 次必须出现。
- 面板 id 一旦定下不要随版本改；LC测试 的脚本按 id 找，改了就同步改本文件。
