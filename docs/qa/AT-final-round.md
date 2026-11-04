# 收官轮 AT 一把跑完（LC测试）

> 一条命令、可重复执行。脚本：`tests\at\run-final.ps1`；期望数据源：`app/mechanics.json`。
> 本轮运行：2026-11-05 05:33，develop `b79a54a`，设备 `L2E0222531014833`（1228×2700），
> APK sha256 `C3A91120…B7B141`（`app\android\app\build\outputs\apk\debug\app-debug.apk`）。

## 怎么跑

```powershell
# 1) 先出包（本机没有 gradlew，用仓库脚本解析 JDK17/Gradle/SDK）
pwsh -File scripts\build-android.ps1 -SkipDevice

# 2) 一把跑完 18 项（会 pm clear，回到首次启动状态）
pwsh -File tests\at\run-final.ps1 -Apk app\android\app\build\outputs\apk\debug\app-debug.apk

# 只跑非 TD-016 的部分（设备忙时）
pwsh -File tests\at\run-final.ps1 -Apk <apk> -SkipTd016
```

退出码：`0` 全 PASS / `1` 有 FAIL / `2` 有 BLOCKED 无 FAIL / `4` 环境（装包、前台、通道）不通过。
证据落在 `checks\at-final\<时间戳>\`（`table.tsv`、`notes.md`、各 `ui-*.xml`、截图），**不入库**。

## 本轮结果（两跑：修前 17/1，修后 18/0）

| 跑次 | 构建 | 结果 |
|---|---|---|
| 修前 | `b79a54a`，APK sha256 `C3A91120…B7B141` | 17 PASS / **1 FAIL**（P2-06-vis）/ 0 BLOCKED / 1 诊断 |
| 修后 | `d286873`（merge `111c4fb`），APK sha256 `A94DE24A…F70F189B` | **18 PASS / 0 FAIL / 0 BLOCKED / 1 诊断，exit 0** |

| 用例 | 结果 | 实测 | 判据来源 |
|---|---|---|---|
| SW-006-on-1 | PASS | 偏角行 + 磁北/真北行都在，读数 0.00° | AT-P0-0 ①②（新口径） |
| SW-006-on-2 | PASS | `p0_1_enabled=true`（prefs 缺省即默认 true） | 同上 |
| RT-005-UI45 | PASS | 界面输入 `45` → `prefs.declination=30`；真北口径再注入一帧后读数 **30.00°** | AT-P0-1 RT-005（端到端界面路径） |
| SW-006-off-1 | PASS | 偏角/磁北行消失、读数行仍在（0.00°） | AT-P0-0 ①② |
| SW-006-off-2 | PASS | `p0_1_enabled=false` | 同上 |
| SW-006-off-3 | PASS | 全关后读数 = 原始航向 0.00°（偏角不叠加） | AT-P0-1 RT-010 |
| SW-006-off-3pix | 诊断 | 面板区 y1850–2500 diff **5.46%**（活地图污染，不作门禁） | 总监裁决：降为诊断 |
| P2-07 | PASS | 智能读盘 14 行向/坐 **14/14**（剥离 U+200C 后逐字等于 `mechanics.json`，含滚动补拍） | AT-P0-2 P2-07 |
| P2-08 | PASS | 顶部两行 `向北 94.0° 坐南 274.0°` / `坐酉山 向卯山` | AT-P0-2 P2-08 |
| P2-01 | PASS | 点「截图」新增 `坐向酉向卯_20261105_054540.png`（2150066 B，1228×2376） | AT-P0-2 P2-01/03 |
| P2-03 | PASS | 命名模式 `坐向<坐>向<向>_yyyyMMdd_HHmmss.png`（产品已定名：不带「山」字） | AT-P0-2 P2-03 |
| P2-04 | PASS | PNG 无 `eXIf` 块（即无 EXIF/GPS 坐标） | AT-P0-2 P2-04 |
| P2-05 | PASS | 分享抽屉打开；被分享文件 sha256 `AB8C7821…`（相册同一文件） | AT-P0-2 P2-05 |
| P2-06 | PASS | DOM：`底图：天地图地形 · 审图号：未确认`；`map.html` 无 `GS(2026)1219` | AT-P0-2 P2-06a（语义） |
| P2-06-vis | **PASS（修后）** | 判图：面板上方条带 y1400–1850 最大金色像素行 **143 @ y1717**（修前构建同区域 54，门槛 100） | AT-P0-2 P2-06b（可见性）→ BUG-005 |
| TD-016 | PASS | 3D 点选 5 次 → `LC3D pick` 5 条、`LC3D fps=60..61`、FATAL 0；手感人工 | AT-P0-4 TD-016 |
| RT-011-numeric | PASS | `tests=86 failures=0 errors=0` → PASS | AT-P0-1 RT-011 |
| RT-011-mechanics | PASS | `values=19 sourced=19 estimated=0` | 质量红线 |
| RT-011-diff | PASS | `git diff HEAD -- app/mechanics.json` 0 行 | 质量红线 |

## 口径与已知边界

- **P2-06 分两条**：语义（DOM 文案 + 禁第三方号）自动判；**可见性判图、不判 DOM**——
  `111c4fb` 起客户端把「底图·审图号」画进图内、面板上缘之上，`#srcInfo` 仍在面板后面，
  拿 DOM 矩形判会永远 FAIL。现判据：面板带上方条带（y1400–1850）里金色文字行 ≥100
  （无角注旧构建同区域 54，有角注 143）。
- **RT-005 的「当帧不刷新」不是缺陷**：注入模式下 `compassView.getHeading()` 是静态值，改偏角后
  需下一帧传感器才重算；真机连续传感器不会有这个空窗。脚本因此再注入一次后判读数。
- **SW-006 ③ 面板像素 diff 只作诊断**：罗盘叠在活 WebView + 半透明面板上，实测 5.46% 波动与开关无关。
- **P2-09…11（记录库 CRUD）已出圈**：Android 侧零命中，登记「增量、本轮未做」，不在本轮表内。
- **TD-016 手感**无量化，脚本只门禁 pick 条数 / fps 最小值 / FATAL；手感由人工看 3D 页。

## 复跑要点（踩坑记录）

1. 装完**先 `pm clear` → `force-stop` → `am start`**，并校验 `mResumedActivity` 是我们的包：
   本轮一次 TD-016 失败就是华为应用市场的安装风险检查 Activity 把任务 `moveTaskToBack` 顶掉。
2. dump 前**必须注入 heading 冻结传感器**，否则 `uiautomator dump` 恒报
   `ERROR: could not get idle state.`（约 11 秒失败且不产出文件）。
3. 通道判据用端到端探针 `js 1+1 → LCJS ... -> 2`；`bridge registered` 会被 WebView 日志刷掉，只作辅助。
4. 读盘弹层第 14 行在首屏外，需上滑后重拍取并集。
