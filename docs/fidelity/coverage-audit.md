# Stage 1 覆盖面校核（LC产品复跑 · `850919b`）

- 复核人：LC产品｜日期：2026-11-06｜基线：`fix/p0-review-findings` @ `850919b`（逆向在补证据，未提交部分单独标注）
- 对照标准：`docs/fidelity/checksheet.md` §5（四条可复核门槛）与 §6（A–H 类、去重 ≥30、字段完整率）
- 结论：**暂不 PASS**。计数达标（30/30），但 **E 类 0 条**、**13–16 四条证据未落具体文件**、**F 类无独立条目**；三条改完即过。

## 1. 条目数与分类

| 检查项 | 实测 | 判据 | 结果 |
|---|---|---|---|
| 去重页面/状态 | **30**（`^| \d\d |` 行） | ≥30 | ✅ |
| A 罗盘主界面与地图态 | 12（01–12） | 不为 0 | ✅ |
| B 罗盘浮层工具条 | 4（13–16） | — | ✅（证据待补，见 §3） |
| C 地图功能态 | 14（17–30） | — | ✅ |
| D 更多面板 | **0 个独立条目**；证据存在：`more-sheet-01/02/03`（默认/滚动/新开） | 不为 0 | ⚠️ 证据可映射 2 状态，但清单里没有 `PG/ST` 行，Stage 3 无法引用 |
| E 设置/工具管理/收藏列表/保存流程 | **0**（逆向 §3 U5 自认「本轮未到达」） | 不为 0 | ❌ |
| F 天气 | **0 个条目**；`tool-weather-01.xml` 存在（VIP 门禁证据） | 不为 0 | ❌（可补 1 条 `NC`，指到该 dump） |
| H 系统态 | 3（12 付费墙、21 登录墙、30 相机权限） | 不为 0 | ✅（缺空态/加载/错误，属加分项，不卡） |

## 2. 字段完整率

| 项 | 实测 | 判据 | 结果 |
|---|---|---|---|
| 有具体截图+dump 文件的条目 | **25/30 = 83.3%**（13–16 缺；22 只写「`map-home-01.xml` 等」属半条） | ≥90% | ❌ |
| 有入口路径 | 30/30 | 100% | ✅ |
| 有控件量化（auto 表同名小节） | 30/30 小节存在，但 13–15 为「旧轮实测值，待像素复核」 | — | ⚠️ 标了「待复核」可接受，但需在 Stage 2 前补像素值 |
| 交互/动效记录 | 静态交互有；动效按 U6 单列（动画置 0 未采） | — | ⚠️ 已知缺口，Stage 2 不阻塞 |

## 3. 引用文件核对（自动）

- 从清单抽取 27 个唯一文件 stem，逐一对照 `docs/fidelity/orig/`：**全部可解析**；唯一「缺失」是正则误报（`/sdcard/a.xml` 的 `a`）。
- 逆向正在补的两处（未提交，已落盘）：
  - `win-02-compass.png`（1,495,644 B）、`w3-sheet.png`（566,668 B）已拷入 `docs/fidelity/orig/`，`_captures.tsv` 已加行并标「历史轮 2026-11-05 再采集」；
  - **但** `orig-inventory.md` 第 48–51 行仍写「`win-02-compass` 系列历史图」「同上」「旧轮 `orig-shots/w3-sheet.png`」——文本未改，未满足「点名具体 dump 文件」。
- `_captures.tsv` 现为 **modified**、两个 png 为 **untracked**：提交前不能算交付。

## 4. 必改三项（照抄即可）

1. **13 行**：证据列改为 `tool-compass-page.png/.xml` + `win-02-compass.png`（历史轮 2026-11-05，已入目录）；量化列保留「旧轮中心值，待像素复核」并写清复核方法（ROI 深色块 + 图标中心）。
2. **14/15 行**：删除「同上」，分别点名 `tool-compass-page.xml`（右列/底栏 DOM 若不可见，则写 `win-02-compass.png` 历史图 + 像素量取）；**16 行**改为 `w3-sheet.png`（不要再写 `orig-shots/` 路径）。
3. **补 E 类 4 条 + F 类 1 条**（可来自下一窗口）：
   - E：设置首屏（汉堡抽屉）、工具管理（更多→编辑）、收藏列表（空/有数据任一）、保存坐向流程；
   - F：天气——写 `NC`，证据 `tool-weather-01.xml`，原因「VIP 门禁、截图 0 字节」。
   - D：把 `more-sheet-01/02/03` 归并为 1–2 条 `PG/ST` 行（默认态 + 滚动/编辑态），否则 Stage 3 无 ID 可引。

## 5. 复跑方法（逆向改完后，我按此 5 分钟复核）

```powershell
cd E:\APPDetector\fix-p0
# 1) 条目数
(Select-String -Path docs\fidelity\orig-inventory.md -Pattern '^\| \d\d \|').Count
# 2) 引用文件是否都在
$inv = Get-Content docs\fidelity\orig-inventory.md -Raw
$refs = [regex]::Matches($inv,'([A-Za-z0-9_\-]+)\.(png|xml)') | % { $_.Groups[1].Value } | Sort-Object -Unique
$files = Get-ChildItem docs\fidelity\orig -File | % { $_.BaseName }
$refs | ? { $files -notcontains $_ -and $_ -ne 'a' }
# 3) 是否还有「同上」「orig-shots」
Select-String -Path docs\fidelity\orig-inventory.md -Pattern '同上|orig-shots'
# 4) 分类计数（E/F 是否已补）
Select-String -Path docs\fidelity\orig-inventory.md -Pattern '设置|工具管理|收藏列表|天气'
```

## 6. 判定

- 当前：**不 PASS**（E=0、F=0、13–16 证据未点名、字段完整率 83.3% < 90%）。
- 改完 §4 三项后预期：条目 30+6=36、E/F 非 0、字段完整率 36/36=100%，直接 PASS。
- 风险的说明：E 类需要真机再开一个短窗口（汉堡抽屉四页 + 保存流程），若设备排队紧，允许把 E 类标「Stage 2 前补」——但需技术总监书面同意，不能由我方默认放宽。

## 7. 复核 v2（2026-11-06，`ef08ace` + D/F 新增；E 仍未补）

| 检查项 | 实测 | 判据 | 结果 |
|---|---|---|---|
| 去重条目 | **33**（新增 31 更多面板默认、32 滚动、33 天气受阻） | ≥30 | ✅ |
| A/B/C | 12 / 4 / 14 | 非 0 | ✅ |
| D 更多面板 | **2**（31 `more-sheet-03-fresh`、32 `more-sheet-02-scrolled`） | 非 0 | ✅ |
| F 天气 | **1**（33，`tool-weather-01.xml`，VIP 门禁） | 非 0 | ✅ |
| E 设置/工具管理/收藏列表与保存 | **0**（清单无 `### E.` 小节；U5 行 121 仍写「本轮未到达」） | 非 0 | ❌ |
| 13–16 点名 | `同上` 出现 **0** 次、`orig-shots` **0** 次；13/14/15 指 `tool-compass-page.png+.xml` + 历史轮 `win-02-compass.png`，16 指 `w3-sheet.png` | 点名具体文件 | ✅ |
| 字段完整率 | 需 png+dump 的 **27** 行中 **26** 行齐全 = **96.3%**；例外：01/02 图页无 dump、12/25/33 FLAG_SECURE 仅 dump、16 dump-BLOCKED | ≥90% | ✅（07 行仅 png，缺 `search-result-02.xml`，列为小缺口） |

**结论：不 PASS。** 唯一分类缺口是 **E 类 3 页**（设置 / 工具管理 / 收藏列表与保存），补完并加 `_captures.tsv` 行即可；顺带补 **07 行**的 dump（`search-result-02.xml`）。

补采提醒（已核，勿浪费窗口）：
- 现有 `drawer-menu*.png/.xml` 是地图主界面，不是抽屉内容（「设置/工具管理/收藏」命中 0），必须在点开 [35,140]–[160,265] 后重新截图+dump。
- 天气不需截图：`tool-weather-01.xml` 已含「待你解锁哦！」+「忍痛放弃」，按受阻行写即可（33 已这样处理）。
