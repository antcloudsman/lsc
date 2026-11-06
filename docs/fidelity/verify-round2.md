# 第二轮差异清单 · 抽样核验（Stage 4）

- 核验人：LC审核验（独立于两份清单的作者，只向技术总监负责）
- 审查基线：`develop` `a53c2a3`（派单基线 `1f4861b`；期间 `develop` 只增不改本文件对象）：
  `git diff --stat 1f4861b a53c2a3 -- docs/fidelity/findings-client.md docs/fidelity/findings-map.md` → **输出为空**（两份 findings 逐字节未变）
- 代码基准：`3d7fcb3`（两份 findings 的代码锚点基准，与 `develop` 当前代码同源）
- 核验对象：`docs/fidelity/findings-client.md`（24 行）+ `docs/fidelity/findings-map.md`（13 条）= **37 条**
- 证据基准：`docs/fidelity/lc/`、`docs/fidelity/orig/`（1228×2700，dump 根节点 `[0,0][1228,2578]`）
- 设备状态：`adb devices` → `List of devices attached`（**0 台在线**）。本文件全部判定不依赖真机；依赖真机的行为项单列 §6

**结论：抽样 28/37（75.7%），逐档覆盖 P0/P1/P2/DI/NC/缺失/等价；驳回 0 条、不成立 0 条、无法复现 0 条；其中 7 条「成立但须修正描述/锚点」，均不改结论、不改定级。另有跨报告重复 2 组、定级口径待统一 1 条、新线索 3 条（§4/§5/§7，不进缺陷清单）。**

---

## 0. 核验方法（每条都走完 5 步）

1. **锚点回源**：`git show 3d7fcb3:<file>` 取该提交的文件，核行号与片段是否逐字一致；行号不存在或不符 → 记「修正」。
2. **旁证排除**：对「缺失/无 X」类结论，在 `3d7fcb3` 的 Java/`map.html`/`res` 里做反查（`git grep`），确认不是被别处代码处理过。
3. **量化复算**：用 dump 的 `bounds` 重算中心/宽高/步距/差值，和报告写的数逐个对；容忍 1px 的包含端点计数口径。
4. **像素复核**：颜色类结论直接从 PNG 取点（`System.Drawing.Bitmap.GetPixel`），不看报告转述。
5. **定级复核**：按 `docs/fidelity/checksheet.md` §2 的 P0/P1/P2 定义与 §2 规则 1/规则 5（P0/P1 必须带量化值）判是否越级/降级。

---

## 1. 抽样覆盖矩阵

| 档位 | 总数 | 抽中 | 覆盖 | 未抽 |
|---|---|---|---|---|
| P0 | 6 | 6 | 05、09、10、17、34（客户端）／C-01（地图） | — |
| P1 | 14 | 13 | 03、08、13、14、16、31、35、37（客户端，8）／C-02、C-03、C-05、C-06、C-09（地图，5） | 增量-B |
| P2 | 5 | 3 | 01、11（客户端）／C-08（地图） | 其余 2 条 |
| NC | 3 | 2 | 15（客户端）、C-12（地图） | C-11 |
| DI | 4 | 1 | 02（客户端） | 12、33、增量-A |
| 待补采 | 2 | 0 | —（无证据，按定义不可复现） | 36、38 |
| 未确认 | 1 | 0 | — | C-07 |
| 缺失 | 1 | 1 | C-13 | — |
| 等价 | 1 | 1 | 32 | — |
| **合计** | **37** | **28** | **75.7%** | 9 |

> 「P1 14 / P2 5」按成文 v0.9（`5a46ed2`）统计口径；地图 C-09 计 P1、C-07 计未确认，与总监裁定一致。

---

## 2. 逐条判定表

图例：**成立**＝代码锚点与量化值均可复现；**成立·修正**＝结论成立但描述/锚点须改（见 §3 编号 R1–R7）。

| # | 报告 | 编号 | 原判 | 判定 | 核验证据（文件:行 ／ 实测值） |
|---|---|---|---|---|---|
| 1 | client | 01 | P2 | 成立 | `MainActivity.java:133-137` `onCreate`→`setContentView`→`applyImmersiveFullscreen`，`:155` `compassTab = buildSinglePage()`，无 Splash 路由；`git grep -l 三维山水全景地图 3d7fcb3 -- res` → 0 文件；`lc/launch-probe-01.png` 存在（无 xml，与「冷启即地图」一致） |
| 2 | client | 02 | DI | 成立 | 依据 `docs/TEAM_CHARTER.md:102`（D-001 用户自用、非商用、不上架）；`lc/more-sheet-03-fresh.xml` 16 项无广告入口 |
| 3 | client | 03 | P1 | 成立 | (a) `readingView` 建于 `:416-430`，`setCompassMode`（`:1045-1063`）不引用它，无 `GONE` 分支；`lc/map-home-01.xml` 读数节点 `[13,87][722,293]`（709×206），`orig/map-home-01.xml` 无该节点；(b) `:633` `int frameH = contentFrameHeight(h)`、`:645-669` inset 为 0 时 `return h`（2700）→ 乘 2700/2578；实测 7 组逐项吻合（更多 2341.5→2453，`2342×2700/2578=2452.8`，Δ−111.5）；(c) `:534` 角注硬编码，dump `[0,2544][1228,2578]` |
| 4 | client | 05 | P0 | 成立 | `:1609-1615` 回调只写 `mapMarker = marker`（`:1611`）／`= null`（`:1613`）；`grep mapMarker` 全文件仅 `:54`（声明）、`:1611`、`:1613`，**无读取**；原版面板 8 个控件 bounds 全对上（名称 `[59,2135][1084,2257]`、距我 `[59,2283][203,2329]`、WGS84 `[85,2368][606,2414]`、收藏地点 `[477,2544][681,2578]`、导航 `[950,2544][1053,2578]`），面板高 `2578−2135=443` |
| 5 | client | 08 | P1 | 成立·修正 R1 | `CompassView.java:423-450`：只有固定十字（`:426-428`）、顶部红三角（`:434-436` `if (showPointer)`）、中心三行文字（`:442-450`），无 `pointer.png`；`git ls-tree -r 3d7fcb3 -- res | Select-String pointer` → 空；原版 `pointer` 节点 `[573,1188][654,1512]`（81×324）；顶栏：原版 搜索/图层/隐藏 各 77×43（`[719,209][796,252]` 等）、返回图标 `[26,115][184,273]`=158×158，LC 4 枚各 175×114（`[27,192][202,306]`、`[690,192][865,306]`、`[865,192][1040,306]`、`[1040,192][1215,306]`，0 间距） |
| 6 | client | 09 | P0 | 成立·修正 R2 | `:511-517` `hideButton = newToolButton("隐藏")`＋`onClick → setCompassMode(false)`（`:514`），文案写死；`setCompassMode`（`:1052-1060`）把地图左/右列置 `VISIBLE`、罗盘与罗盘工具列置 `GONE`；`lc/compass-overlay-hidden-map.xml` 实测：左列 8（全地图工具/截屏/超清优化/海拔查询/罗盘/路网/等高线/更多）＋右列 10（图层/叠加/山脉/水系/探索/指北针/+/−/定位/3D）＋读数块，无「显示」；`orig/compass-overlay-hidden-map.xml` 24 个文本节点，含「显示」`[1085,209][1162,252]` 与读数 `坐180.0° 向0.0°` `[105,397][350,436]` |
| 7 | client | 10 | P0 | 成立·修正 R3 | `:940-947` `case "海拔查询": … startActivity(new Intent(this, ElevationActivity.class))`；原版 `打点` `[974,2540][1069,2578]`、`气压：` `[718,2443][841,2489]`＋`102.4千帕` `[841,2387][1029,2491]`、`海拔高度` `[113,2297][277,2383]`；LC `lc/compass-main-01.xml`：`该点信息` `[27,325][1201,419]`、`气压：1006.7 hPa（估算）` `[47,2431][1181,2504]`、`关闭` `[47,2504][1181,2578]`；复算 1024−1006.7=**+17.3 hPa**、含氧量 +0.02pp、海拔 +1.0m，与报告一致 |
| 8 | client | 11 | P2 | 成立 | `res/values/strings_supermap.xml:5` 逐字：`把地图拖到想看的位置、放大到街道级，再点「生成超清图」：LC 会按天地图影像 z18（原生最高级）下载当前视野的瓦片并拼接成一张大图。没有云端超分，全部在本机完成。` |
| 9 | client | 13 | P1 | 成立 | 原版标签中心实测 1408.5／1594.5／1780.5／1966.5／2152.5／2338.5（步距 186，`orig/tool-compass-page.xml:25-30`）；LC `[6,936][201,1064]`→中心 1000 …… `[6,1636][201,1764]`→1700（步距 140）；Δ=−408.5…−638.5 与报告 6 个数逐一相符 |
| 10 | client | 14 | P1 | 成立·修正 R4 | 原版 475.5／659.5／843.5／1027.5；LC `[1027,1076][1222,1204]`→1140、1280、1420、1560；|Δ| 与报告相同，**符号与报告 §0 口径（`delta = 原版 − 我方`）相反** |
| 11 | client | 15 | NC | 成立·修正 R5 | `docs/fidelity/orig/win-02-compass.xml` **不存在**（该目录 127 个文件只有 `win-02-compass.png`）；报告描述的「5 项裁到 y2573–2578」实际在 `orig/tool-compass-page.xml:35-39`：`切换 [108,2573][203,2578]`、`旋转 [341,2573]`、`缩放 [574,2573]`、`透明度 [806,2573]`、`天支线 [1038,2573]`；LC 底栏 `[0,2416][245,2544]` 等 5 等分 245×128 核对无误 |
| 12 | client | 16 | P1 | 成立·修正 R7 | `CompassReadSheet.java:86-89` `new AlertDialog.Builder(activity).setView(scroll).setNegativeButton("关闭", null).show()`；像素实测 (600,1300)：原版 `#FFFFFF`、LC `#424242`；头部 `:46-53` 无「离宅」；`git grep 离宅 3d7fcb3 -- app/android/app/src` → 0；`git grep -c 点击查看` → 仅 `CompassReadSheet.java:20`（注释） |
| 13 | client | 17 | P0 | 成立 | `:1417-1419` `"收藏点".equals(t) … startActivity(new Intent(this, InterestListActivity.class))`；`lc/tool-favorite-01.xml` 仅 3 节点（返回/标题/暂无收藏点）；原版 `收藏列表 [1052,604][1192,643]`、`等高线 [1070,790][1175,829]`、`添加点 [538,2417][691,2496]`；三处量化 0/1、3 vs 5 成立 |
| 14 | client | 31 | P1 | 成立 | 原版 sheet 关闭区 `[0,0][1228,1350]`（顶 y=1350）、标题 `[46,1393][234,1472]`、编辑 `[1079,1393][1182,1472]`、cell `[23,1540][319,1771]`=296×231（同排 4 格、共 4 排）；LC 标题 `[140,926][606,999]`、格 `[106,1036][359,1198]`=253×162、`设置 [606,916][740,1010]`、`功能开关（调试） [740,916][1114,1010]`、`关闭 [884,1742][1100,1924]`；量化 标题 y −467、格 −43/−69、首格 x +83 全部复算一致 |
| 15 | client | 32 | 等价 | 成立 | `more-sheet-02-scrolled.xml` 与 `-03-fresh.xml` sha256 均 `EC23C862CD8499A5CB8868D41876862AF8157B125A203769837FBD52E4A56DC7`；png 亦同（`4104F30E…`）→「同态不可滚动」成立 |
| 16 | client | 34 | P0 | 成立 | `git grep -l DrawerLayout 3d7fcb3 -- java` → 0 文件；`:1430` 注释「K-01 单页布局没做抽屉，故暂时置于「更多」标题栏」；原版 `orig/drawer-menu-03.xml` 菜单 9 项（地图设置…用户协议）+ 账户/VIP 4 项 + `三维山水全景地图V 1.6.1 [150,2495][616,2574]`，与报告清单逐字一致 |
| 17 | client | 35 | P1 | 成立 | 原版 chip `[39,567][285,725]`=246×158（自然/高亮/自定义同高）；LC `[39,584][284,681]`=245×97；Δ 高 −61、y +17 复算一致；代码 `SettingActivity.java:37` 注释「chip 245×97px」、`:49-50` `CHIP_W_DP=72.6f/CHIP_H_DP=28.7f`、`:140` `LcUi.dp(this, CHIP_H_DP)` |
| 18 | client | 37 | P1 | 成立 | 入口 `:1417-1419`；`InterestListActivity.java:73-75` 空态 `LcUi.emptyView(this, R.id.lc_interest_empty, getString(R.string.lc_interest_empty))`；`lc/tool-favorite-01.xml` 3 节点 vs 原版 5 个文字控件（含 添加点） |
| 19 | map | C-01 | P0 | 成立 | `:504-506` `topSearchButton = newToolButton("搜索") … setOnClickListener(v -> showSearchDialog())`；`:1502-1509` 数据源 `knowledgeEntries()`、`:1509` `input.setHint("搜索：盘面 / 圈层 / 知识条目")`；原版 `orig/search-result-01.xml` 输入框 `[170,252][1039,375]`、4 个 tab `境内 [108,420][200,486]`／`境外 [415,420][507,486]`／`行政区域 [676,420][859,486]`／`收藏点 [1006,420][1143,486]`、返回 `[39,161][104,226]` → 「4 tab + 搜索历史 + 坐标输入」的修法有据 |
| 20 | map | C-02 | P1 | 成立·修正 R6 | `:415-430` 创建 `readingView` 并 `frame.addView`，`setCompassMode` 不引用；`lc/map-beijing-z12.xml` 读数块 `[13,87][722,293]`；原版同名位置是地名：`orig/map-beijing-z12.xml` `青海湖 [327,194][468,273]`、`orig/map-mountains-entry.xml` `哀牢山森林旅游区 [327,194][701,273]` |
| 21 | map | C-03 | P1 | 成立 | `map.html:47-55` `#srcPanel {position:absolute; inset:0; background:#00000099; z-index:20}`＋`.srcItem {display:block; width:100%; …}`（竖列表）；`:88-92` DOM（`<h3>底图源</h3>`＋`srcBox`＋关闭）；`:1472-1483` 12 个源逐条（含「需授权」×4、「需 key」×5）；`:1498-1513` `openSources()`/`closeSources()` 原文一致 |
| 22 | map | C-04 | P1（证据级） | 成立 | `lc/map-3d-mode.png` 与 `lc/tool-contour-01.png` sha256 均 `F037F73577C5B56D68D94364172EC6FC284DBBBE70464E3290A2F461BCB6FCB6`（逐字节相同）；`tools/map/collect_map_lc.ps1:12-13` `[int]$ThreeDX = 155, [int]$ThreeDY = 576`、`:75` `Tap $ThreeDX $ThreeDY`；真实 3D 按钮 `[1027,2366][1222,2494]`（中心 1124,2430）→ 脚本坐标确实点不到 3D 按钮 |
| 23 | map | C-05 | P1 | 成立 | `map.html:748` `function setOverlayLayer(id, on, opts)`、`:1203` `function setRoadNet(on)` 存在；原版 `orig/map-overlay-sheet.xml` 有 `行政区域（面）[98,2017][454,2096]`、`（线）[98,2187][454,2266]`、**`行政区域筛选 [98,2357][403,2436]`**、`全部关闭 [559,2553][747,2578]`、`全部开启 [925,2553][1113,2578]`；LC `lc/map-overlay-sheet.xml` 无「筛选」节点，面/线为 `无数据 [1067,1428][1187,1482]` + 说明段 `[41,1485][1187,1586]` → 缺筛选入口成立 |
| 24 | map | C-06 | P1 | 成立（定级待统一，§5） | `:965-968` `case "山脉": … openFeatureAtCenter(MountainActivity.class)`；`orig/map-mountains-entry.xml` 仍是地图页（地名 `哀牢山森林旅游区 [327,194][701,273]`、无子页标题、无「看山脉」节点）；`lc/map-mountains-entry.xml` 标题 `看山脉 [533,173][695,246]`＝整页 |
| 25 | map | C-08 | P2 | 成立 | `map.html:87` 逐字 `<div id="srcInfo">数据来自天地图地形（等价替代原版世纪空间）· 审图号：未确认</div>`；`:1526` 模板 `"数据来自" + s.name + "（等价替代原版世纪空间）· 审图号：" + (s.review || "未确认")`；`:1457` 该节点在 `fullScreen || uiMode !== "full"` 时 `display:none`；原版同一文案节点 `数据来自世纪空间 审图号：GS(2026)1219号` |
| 26 | map | C-09 | P1 | 成立 | LC 右列 `图层 [1027,619][1222,747]`=195×128、左列 `全地图工具 [6,432][201,560]`=195×128；原版同名列 `图层 [1081,632][1152,671]`=71×39、`超清优化 [42,1392][182,1431]`=140×39（图标节点在 dump 中无 bounds，报告已声明只比文字与按钮框）→ 体量差成立 |
| 27 | map | C-12 | NC | 成立 | `map.html:1635-1639` `toggleContour()`：`if (mode !== "3d" && contourOn) setMode("3d")`；`:1936-1953` 片元着色器 `vElev/100.0`、`vElev/500.0` 两级 + `fwidth` 抗锯齿；证据图与 3D 态逐字节同 → 缺「3D 可见态下开/关双图」，定 NC 正确 |
| 28 | map | C-13 | 缺失 | 成立 | `:471` 右列 10 项 `{"图层","叠加","山脉","水系","探索","指北针","+","−","定位","3D"}` 无相机；`git grep 相机 3d7fcb3 -- java res` → 0；`lc/*.xml` 含「相机」→ 0 命中 |

---

## 3. 须修正的 7 处描述（结论与定级均不变）

> 共同原因：跨状态/跨文件引用时把「同位置的另一份 dump」当成同一状态。改法都是把文件名与状态写死。

### R1 client 08：原版读数条的 bounds 混了两个状态

- 报告原文（`findings-client.md:121`）：`原版 坐180° 向0° 单行 [105,397][350,436]（隐藏态 坐180.0° 向0.0° [105,397][350,436]）`
- 实测：正常态 `orig/tool-compass-page.xml:14` = `[105,397][290,436] | 坐180° 向0°`（宽 185）；隐藏态 `orig/compass-overlay-hidden-map.xml:14` = `[105,397][350,436] | 坐180.0° 向0.0°`（宽 245）。两个状态被写成同一个 bounds。
- 应改为：`正常态 [105,397][290,436]；隐藏态 [105,397][350,436]`。

### R2 client 09：「2km 保留」在该状态不成立

- 报告原文（`:68`）：`读数条 坐180.0° 向0.0° [105,397][350,436] 与 128m、坐标、2km 保留`
- 实测：隐藏态 dump 只有 `128m`（`:15-19`）与坐标（`:20-24`），**无 2km 节点**；`2km` 出现在正常态 `orig/tool-compass-page.xml:15-19` 与 `orig/tool-favorite-01.xml:9-13`。
- 应改为：`与 128m、坐标保留`（去掉 2km）。

### R3 client 10：「缺打点」须限定为「缺按钮」

- 报告原文（`:77`、`:84`）：`缺失动作 1 个（打点）`／`「添加点」0 vs 1` 类同口径
- 反查：LC 在海拔页有等价交互——`ElevationActivity.java:113` `view.evaluateJavascript("setBase('img');lc_elev_query(true);", null);`，`map.html:1715` `if (elevQuery.on) { /* F-03：海拔查询打点 */ }`。即「点图查该点」存在，缺的是原版底部「打点」按钮与其动作语义。
- 应改为：`无「打点」按钮；点图查询为等价路径（ElevationActivity.java:113 → map.html:1715），打点后动作（原版如何记录/使用该点）未复刻`。P0 依据是「地图内浮层 → 独立 Activity」的页面层级差，不受此句影响。

### R4 client 14：delta 符号与报告自身口径相反

- 报告 §0（`:6`）定义 `delta = 原版 − 我方`；13 行用了负值（−408.5…−638.5）。
- 14 行（`:138`）写 `delta：+664.5 / +620.5 / +576.5 / +532.5 px（LC 偏低）`，按自身口径应为 **−664.5 / −620.5 / −576.5 / −532.5**（475.5−1140=−664.5）。
- 应改为：统一负号，或把「LC 偏低」改成「|Δ| 664.5…」并说明取绝对值。

### R5 client 15：引用的原版 dump 文件不存在

- 报告原文（`:224`）：``orig/win-02-compass.xml` 底栏 5 项只有文字节点、且被内容帧裁到 y2573–2578（仅 5px）`
- 实测：`docs/fidelity/orig/` 只有 `win-02-compass.png`，**无 `.xml`**（`Test-Path` False）；上述 5 个节点实际在 `orig/tool-compass-page.xml:35-39`：
  `切换 [108,2573][203,2578]`、`旋转 [341,2573][436,2578]`、`缩放 [574,2573][669,2578]`、`透明度 [806,2573][947,2578]`、`天支线 [1038,2573][1179,2578]`
- 结论（缺完整按钮 bounds → 未确认）不变，属定 NC 的正确处理。应改为引用 `orig/tool-compass-page.xml:35-39`，并把「原版底栏只有文字节点」限定为「该 dump 未暴露按钮 bounds」。

### R6 map C-02：地名 bounds 未标来源文件

- 报告原文（`findings-map.md:34`）：`原版同名位置为地名 [327,194][468,273]「青海湖」/[327,194][701,273]「哀牢山森林旅游区」`
- 实测：`青海湖` 在 `orig/map-beijing-z12.xml`；`哀牢山森林旅游区` 在 `orig/map-mountains-entry.xml`。两个数值都真，但被并列成同一 dump 的两个样本。
- 应改为分别标注文件；成文引用时不要写成「一张原版图里有两个地名」。

### R7 client 16：pointer 的 46×188 是位图尺寸，不是屏幕 bounds

- 报告原文（`:124`）：`补中心 pointer.png（46×188 红蓝针）`，未标量纲。
- 实测：`app/MECHANICS.md:312` = `+ pointer.png(46×188)`（位图）；原版 dump 中 `pointer` 节点 `[573,1188][654,1512]` = **81×324**（屏幕 px，同一元素）。
- 应改为：`pointer.png 位图 46×188（MECHANICS.md:312）；屏幕上实测节点 81×324（orig/tool-compass-page.xml，content-desc=pointer）`。成文不要拿 46×188 当屏幕尺寸。

---

## 4. 跨报告重复（成文去重提示，不属误判）

| 现象 | 出现处 | 建议 |
|---|---|---|
| LC 地图态左上读数面板常驻、遮挡地图 | client `03(a)`（P1，`[13,87][722,293]`）与 map `C-02`（P1，同一节点、同一代码 `:415-430`） | 同一现象两条计数会把 37 条虚增。建议合并为一条，或保留两条但明确写「与 03(a) 同源，不重复计入统计」 |
| LC 角注文案含开发口径 | client `03(c)`（P2，`:534` 原生 TextView）与 map `C-08`（P2，`map.html:87` `#srcInfo`） | 同一句文案的两处实现（原生角注 + 页内 `#srcInfo`），用户可见文本完全相同。建议合并为一条 P2，在正文注明两处实现位置 |

> 若按现象去重，成文的 37 条应降为 35 条；是否改口径由总监定，本文件只提供证据。

---

## 5. 定级口径待统一（1 条，需总监裁）

**map C-06「山脉」判 P1，与 client 10「海拔查询」判 P0 是同一类结构差异，定级不同。**

- `docs/fidelity/checksheet.md:61`：P0 = `结构、功能、业务逻辑不符｜页面层级/导航模型不同；功能缺失或行为相反`。
- `client 10`：原版「地图内浮层」→ LC「独立 Activity」→ 判 **P0**。
- `map C-06`：原版「点山脉后仍在地图页（仅地名变化）」→ LC「整页 `MountainActivity`（看山脉）」→ 判 **P1**。
- 两条的差别只是「原版有无独立子页」：08/10 那条原版是浮层（无页），C-06 原版也是地图内（无页）。按检查表字面，C-06 与 10 同级。
- 可选处置：① C-06 升 P0；② 在 `checksheet.md` §2 补一条细则（例：「原版无独立页、LC 新开整页」按 P1，仅当原版有等价浮层且功能可完成时）；③ 维持 P1 并在成文 §4 注明理由。我不改定级，只报差异。

---

## 6. 待真机复现清单（设备离线，`adb devices` 空）

以下 12 项的共同点：结论已由代码路径 + 已入库 dump 证实，但「点下去之后界面会怎样」还需真机确认。坐标均为 1228×2700、density 540、动画三项 0、`rm -f /sdcard/a.xml` 后再 dump。

| 编号 | 步骤（坐标取自我方/原版 dump） | 期望（判「成立」的观测） |
|---|---|---|
| client 05 | 地图态点地图任意点（如 `614 1350`）；再点 3D（`1124 2430`）后点图 | 无任何面板出现；logcat 无面板相关日志 → 成立 |
| client 09 | 罗盘态点「隐藏」（`1127 249`） | 回地图态：左列 8＋右列 10 出现、无「显示」→ 成立 |
| client 10 | 点「海拔查询」（`103 1674`）；进页后点图（`614 1350`） | 进 `ElevationActivity` 整页；点图回显该点 → 成立，且确认「打点」按钮不存在 |
| client 17 | 更多（`103 2453`）→ 收藏点（`232 1453`） | 进 `InterestListActivity` 列表页，地图消失 → 成立 |
| map C-01 | 地图态「搜索」（`1127 249`） | 弹知识检索（hint `搜索：盘面 / 圈层 / 知识条目`），非 POI 搜索 → 成立 |
| map C-03 | 右列「图层」（`1124 683`） | 整屏遮罩 + 竖列表 12 源，非 3×4 网格 → 成立 |
| map C-05 | 右列「叠加」（`1124 878`） | 面板无「行政区域筛选」行 → 成立 |
| map C-06 | 右列「山脉」（`1124 1072`） | 进整页「看山脉」→ 成立 |
| map C-07 | 右列「探索」（`1124 1462`） | 进 `DiscoveryActivity`；**顺带重采 `map-explore-page`**（现证据是看山脉页） |
| map C-04 | 点 3D（`1124 2430`） | logcat `LC3D mode=3d`，画面转卫星 3D；用来重采 `map-3d-mode` |
| map C-12 | 3D 可见态下开/关等高线（左列 `103 2258`） | 双图可见等高线差异，用来把 NC 转已证实 |
| map C-13 | 首次进图层/相机入口 | 原版有相机权限弹窗；确认 LC 无相机入口 |

真机项判「无法复现」的唯一条件：设备到手后按上表步骤执行，观测与「期望」相反——那时才改判，本文件不预先写「无法复现」。

---

## 7. 新线索（不新增缺陷条目，交总监决定）

### N1 叠加面板的「全部开启/全部关闭」顺序与位置与原版相反（C-05 之外）

- 原版：`全部关闭 [559,2553][747,2578]` 在左、`全部开启 [925,2553][1113,2578]` 在右，面板顶 `[0,0][1228,1646]`。
- LC：`全部开启 [41,2406][338,2568]` 在左、`全部关闭 [338,2406][635,2568]` 在右；面板首行（四个分类）`行政区域 [0,1215][307,1377]`。
- 差异：顺序对调 + 按钮 y 差 147px（2553→2406）+ 面板起点差 431px（原版 sheet 顶 1646 vs LC 首行 1215）。C-05 行只写了「缺筛选入口/无数据说明」，未覆盖这三项。是否补条由总监定。

### N2 地图侧用户可见文案含开发口径（与 client 11 P2 同类，未计入）

- `map.html:89` 面板内 `srchint`：`原版 12 源；无 key 的置灰（D-006），审图号须按源官方核对`——「（D-006）」是决策编号，出现在用户可见面板里。
- client 11 的修法里已把「实现口径入 UI」判 P2；地图侧同样问题（`map.html:89`、`:1526` 的 `等价替代原版世纪空间`）没有单列 P2。是否补条由总监定。

### N3 「3D 证据撤回」在 `develop` 被后续提交覆盖，与 findings/索引不一致

- `076816f`（撤下误采 `map-3d-mode`）确实在 `develop` 祖先链上（`git merge-base --is-ancestor 076816f origin/develop` → exit 0），该提交 `docs/fidelity/lc/map-3d-mode.png` 由 1,841,545 B 删为 0。
- 但 `d18928d`（「地图章节 16 行收口…登记 3D/等高线可见性异常」）又把同一组 `map-3d-mode.{png,xml,meta.json}` 加回；`a53c2a3` 上该文件仍在（sha256 `F037F735…`），`lc-captures.md:112` 仍写 `29 | map-3d-mode | 实采（异常）`。
- 影响：成文把 `map-3d-mode` 标「待补采/未确认」时，索引行状态是「实采（异常）」；且门禁的 lc 侧唯一重复组 `map-3d-mode == tool-contour-01` 正是这一对。建议 §7 与 §10 各加一句状态说明，别让读者以为「已采到可用 3D 证据」。

---

## 8. 未覆盖范围（明确没做）

1. **未抽中的 9 条**：client 12（DI）、33（NC+DI）、36、38（待补采）、增量-A（DI/NC）、增量-B（P1）、map C-07（未确认）、C-10（DI）、C-11（P2/NC）。其中 36/38 无我方证据、C-07 证据错态，按定义不可复现。
2. **未做真机复现**：设备离线（`adb devices` 0 台），§6 的 12 项全部待复；未采任何新截图/dump。
3. **未做**：ΔE2000 全量色差、动效、3D 投影/pick 边界数学、`map.html` 逐行审（只按锚点读指定行）、桌面 M1。
4. **未核**：两份 findings 的 `§10 复现命令` 能否原样跑通（属 LC测试 的 `repro-round2.md`）；成文 `round2-report.md` 的统计（属成文职责，本文件只在 §4/§5 给证据）。

---

## 9. 证据与命令（可复跑）

```powershell
# 0) 基线与同源
git -C E:\APPDetector\review-base diff --stat 1f4861b a53c2a3 -- docs/fidelity/findings-client.md docs/fidelity/findings-map.md   # 空

# 1) 锚点回源（示例）
git -C E:\APPDetector\review-base show 3d7fcb3:app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java  # :504-517, :940-947, :1045-1063, :1417-1419, :1502-1509, :1609-1615
git -C E:\APPDetector\review-base show 3d7fcb3:app/android/app/src/main/assets/map.html                                  # :47-55, :87-92, :748, :1203, :1472-1483, :1498-1513, :1635-1639, :1936-1953
git -C E:\APPDetector\review-base show 3d7fcb3:tools/map/collect_map_lc.ps1                                               # :12-13, :75

# 2) 反查（缺失类结论）
git -C E:\APPDetector\review-base grep -c 'BottomNavigationView\|TabLayout\|showTab' 3d7fcb3 -- app/android/app/src   # exit 1 = 0 命中
git -C E:\APPDetector\review-base grep -n '离宅' 3d7fcb3 -- app/android/app/src                                        # 0
git -C E:\APPDetector\review-base grep -n '相机' 3d7fcb3 -- app/android/app/src/main/res app/android/app/src/main/java   # 0
git -C E:\APPDetector\review-base grep -l 'DrawerLayout' 3d7fcb3 -- app/android/app/src/main/java                       # 0 文件

# 3) 量化复算（dump → 文本+bounds）
#    PowerShell：按 //node 取 text/content-desc/bounds，输出到临时 txt 后逐条比
$doc=[xml](Get-Content docs\fidelity\lc\map-home-01.xml -Raw); $doc.SelectNodes('//node') | % { $_.GetAttribute('bounds'), $_.GetAttribute('text') }

# 4) 像素取点
Add-Type -AssemblyName System.Drawing
$b=[System.Drawing.Bitmap]::FromFile((Resolve-Path docs\fidelity\lc\w3-sheet.png)); $b.GetPixel(600,1300)   # #424242
$b=[System.Drawing.Bitmap]::FromFile((Resolve-Path docs\fidelity\orig\w3-sheet.png)); $b.GetPixel(600,1300) # #FFFFFF

# 5) 证据一致性
Get-FileHash docs\fidelity\lc\map-3d-mode.png, docs\fidelity\lc\tool-contour-01.png -Algorithm SHA256   # 同 F037F735…
Get-FileHash docs\fidelity\lc\more-sheet-02-scrolled.xml, docs\fidelity\lc\more-sheet-03-fresh.xml -Algorithm SHA256  # 同 EC23C862…
Test-Path docs\fidelity\orig\win-02-compass.xml   # False（R5）
```

抽样明细（本次实际抽到的 28 条编号）：client 01/02/03/05/08/09/10/11/13/14/15/16/17/31/32/34/35/37；map C-01/C-02/C-03/C-04/C-05/C-06/C-08/C-09/C-12/C-13。
