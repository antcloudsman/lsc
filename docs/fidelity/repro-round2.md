# LC 侧证据一致性核验与复现命令清单（Stage 4 · `repro-round2`）

- 口径基线：`1f4861b`（findings 基线）。证据与门禁现状另按 `origin/develop` **`3802ef8`** 复核（该点已并入客户端去重 `3286625`、`3ed1e53` 与 dump-only 豁免 `23da46b`）。
- 核验对象：`docs/fidelity/lc-captures.md`（索引）↔ `docs/fidelity/lc/`（29 PNG + 27 XML + 29 meta）↔ `docs/fidelity/findings-{client,map}.md`（37 条）。
- 设备：`L2E0222531014833` 期间 USB 离线（`adb devices` 空），**本文件不含任何本轮真机新采证据**；所有结论均来自仓库内证据与 `git`/XML/JSON 原生判据。

---

## 1. 结论（先给可引用的话）

| # | 结论 | 判据 |
|---|---|---|
| 1 | **三方对齐成立**：29/29 份 meta 的 `image.sha256`/`dump.sha256` 与实际文件逐字节一致；`build.commit` 全 40 位、`build.branch=develop`；两批各一组 APK | 见 §1.1、§1.2 |
| 2 | **采集批代码与基线同源**：`464ebb8→1f4861b`、`e88ed0c→1f4861b` 的 `app/android` 差异均为 **0 文件** | `git diff --name-only … -- app/android` → 0 |
| 3 | **索引计数自洽**：客户端章 14 PNG/13 XML/14 meta、地图章 15 PNG/14 XML/15 meta；38 行全落点（有 15 + 等价 1 + 地图 16 + 缺失 4 + 待补采 2） | §1.3 |
| 4 | **4 份证据状态不可用**（须重采，非产品缺陷）：`map-3d-mode` 与 `tool-contour-01` 逐字节同图；`map-water-entry`、`map-explore-page` 实为「看山脉」页 | §2 |
| 5 | **门禁现状**：lc 侧仅剩 1 组重复（`map-3d-mode`==`tool-contour-01`），orig 侧重复/0 字节已清零；`dump-only` 态单独成类 | §3 |
| 6 | **配对覆盖**：当前 `develop` 为 **23 compared / 36 未比较（并集 59）**，不是总监先前看到的 25/34——客户端去重删掉 2 个 lc stem 后 compared 少 2；36 条按原因分 6 类 | §4 |
| 7 | **可复跑性**：客户端 14/14 一条命令；地图 7/15 一条命令，其余 8 条给出逐步坐标命令；4 个待采项**阻塞于设备离线、脚本已就绪** | §5 |

---

## 1.1 meta 与文件的一致性（29/29）

```powershell
# 逐 stem 比 meta.image.sha256 / meta.dump.sha256 与实际文件
img_hash_ok=29
ALL_META_HASH_MATCH
```

| 采集批 | stem 数 | `build.commit` | `apk.sha256` | `apk.bytes` | 缺 dump 的 stem |
|---|---|---|---|---|---|
| 客户端章 | 14 | `e88ed0c2aa70272e36f6d03a0a6f12511d6bf532` | `8dd5bf13dbbd0763b961a42df8bb35706f743ae2338d3bbd336f748ed0ab1763` | 7,059,509 | `launch-probe-01`（索引已声明 `dump:null`） |
| 地图章 | 15 | `464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a` | `ccd5686f4c9b908d9c5c8a61fe7166d3f2bf01fcb165405e0a58e520ce7cbb7b` | 7,059,457 | `tool-measure-01`（索引已声明 png-only） |

- 29 份 meta 的字段集合完全一致（`tool,version,state,side,captured_at,serial,package,checksheet_id,build,apk,image,dump,device,notes`），`checksheet_id` 各态唯一。
- `image.width/height` 全为 **1228×2700**，与采集口径「统一 1228×2700」相符。
- **跨端只认 `build.commit`**（两批 APK 字节/哈希不同属打包非确定性），`apk.sha256` 只用于证明「本批证据装的是本批记录的那个包」。

## 1.2 采集批代码与 findings 基线是否同源

```powershell
git diff --name-only 464ebb8 1f4861b -- app/android   # → 0 files
git diff --name-only e88ed0c 1f4861b -- app/android   # → 0 files
```

两批之间 `app/` 唯一差异是文档 `app/MECHANICS.md`（R5 补证，非代码）。**结论：两批截图对 `1f4861b` 的代码仍然有效**，不需要因为提交号不同而作废。

## 1.3 索引 ↔ 文件落点

| 章节 | 行数 | 状态分布 | 实际文件 |
|---|---|---|---|
| §1 客户端 | 38 行（含地图行） | 有 15 / 等价 1 / 地图章节 16 / 缺失 4 / 待补采 2 | 客户端章 14 PNG + 13 XML + 14 meta |
| §4 地图章 | 16 行 | 实采 15（含 1 png-only、2 异常态）+ 缺失 1 | 地图章 15 PNG + 14 XML + 15 meta |
| §2 增量 | 2 条 | — | `dial-list-01`、`lc-knowledge-sheet`（各 3 文件） |

- 38 行**无空行**：每行要么指向 `docs/fidelity/lc/` 实文件，要么写 `缺失/待补采` 并给证据位置（行 02/05/12/34 缺失，行 36/38 待补采）。
- 「同态共用」两行按索引口径理解，不算缺文件：行 32 共用 31（`more-sheet-03-fresh`）、行 37 共用 17（`tool-favorite-01`）。
- 行 36/38 在 `1f4861b` 上仍写「缺失」，已由 `3286625`/`3ed1e53` 改为「待补采」并进 `develop`（当前索引统计行 = 有 15 + 等价 1 + 地图 16 + 缺失 4 + 待补采 2）。

---

## 2. 状态正确性核验（证据到底能不能用）

方法：以 `sha256`、dump 标题文案、节点集合做原生判断，不依赖设备。

| stem | 实测 | 结论 |
|---|---|---|
| `map-3d-mode` / `tool-contour-01` | PNG `f037f73577c5…` + XML `3d14b3c2539a…` **两侧逐字节相同** | 两行证据**同时失效**：拍摄时都不在 3D 可见态。`findings-map` C-04 判「证据缺陷、非渲染缺陷」成立 |
| `map-water-entry` | dump 标题 `看山脉` `[533,173][695,246]`、满屏山名节点（`盘山`/`猴山`/`横山`…） | **错态**：拍成了山脉页；水系页从未取证 |
| `map-explore-page` | 同上，标题也是 `看山脉` | **错态**：探索页从未取证 |
| `tool-measure-01` | `dump:null` + 无 `.xml` | 符合索引声明（持续动画致 idle 超时）；只能做 png 级比对，**不能做文字/bounds 量化** |
| `lc-knowledge-sheet` / `search-result-01` | 两份 dump **完全相同**（`c8a6a98e…`，22 节点，同一「搜索」弹层骨架） | 不是错误状态（同一弹层两种内容），但 **列表行（盘面/圈层/知识条目）不在 dump 里** → 该弹层的内容结论只能引 PNG，不能引 dump |
| 其余 24 个 stem | meta 哈希与文件一致、无同哈希组 | 可用（锚点见 §2.1） |

### 2.1 脚本点击坐标是否落在 dump bounds 内（16/16 PASS）

```
PASS map-home-01         罗盘 (103,1868) ∈ [6,1804]-[201,1932]
PASS map-home-01         更多 (103,2453) ∈ [6,2389]-[201,2517]
PASS map-home-01         搜索 (1127,249) ∈ [1040,192]-[1215,306]
PASS tool-compass-page   隐藏 (1127,249) ∈ [1040,192]-[1215,306]
PASS tool-compass-page   返回 (114,249)  ∈ [27,192]-[202,306]
PASS tool-compass-page   读盘 (103,1560) ∈ [6,1496]-[201,1624]
PASS more-sheet-03-fresh 设置 (673,963)  ∈ [606,916]-[740,1010]
PASS more-sheet-03-fresh 天气 (486,1285) ∈ [359,1204]-[613,1366]
PASS more-sheet-03-fresh 收藏点 (232,1453) ∈ [106,1372]-[359,1534]
PASS more-sheet-03-fresh 超清地图 (486,1117) ∈ [359,1036]-[613,1198]
PASS more-sheet-03-fresh 海拔查询 (232,1285) ∈ [106,1204]-[359,1366]
PASS map-beijing-z12     3D (1124,2430)  ∈ [1027,2366]-[1222,2494]
PASS map-beijing-z12     等高线 (103,2258) ∈ [6,2194]-[201,2322]
PASS map-beijing-z12     山脉 (1124,1072) ∈ [1027,1008]-[1222,1136]
PASS map-beijing-z12     水系 (1124,1267) ∈ [1027,1203]-[1222,1331]
PASS map-beijing-z12     探索 (1124,1462) ∈ [1027,1398]-[1222,1526]
```

反例（历史缺陷，供复现时避坑）：3D 旧默认坐标 `(155,576)` **不在** 3D bounds 内，落在左列工具区——这是 `map-3d-mode` 采成 2D 的直接原因，已由 `c2ca931` 修正为 `(1124,2430)`。

---

## 3. 门禁命中（重复图 / 0 字节 / dump-only）

复跑命令（任选 ref 后执行；`report` 会做像素比对，耗时较长）：

```powershell
python tools\fidelity\fidelity.py report --root docs\fidelity --out checks\report-r2
# 只看证据完整性（快）：同一套规则，遍历 *.png，异名同 sha 记重复、0 字节记空图
python -c "import json,sys; from pathlib import Path; sys.path.insert(0,'tools/fidelity'); import fidelity as f; out={s:f._scan_image_integrity(Path('docs/fidelity')/s) for s in ('orig','lc')}; Path('checks/integrity.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')"
```

| ref | lc 重复组 | orig 重复组 | orig 0 字节 | 说明 |
|---|---|---|---|---|
| `1f4861b`（findings 基线） | **3**：`favorite-list-02`==`tool-favorite-01`、`map-3d-mode`==`tool-contour-01`、`more-sheet-02-scrolled`==`more-sheet-03-fresh` | 4（含 3 个空图同哈希 1 组） | **3**：`right-mountains-entry`、`tool-contour-guide-dismissed`、`tool-measure-mode` | 总监说的「9 处」即此点 |
| `c76d1b8`（客户端去重前） | 3 | 0 | 0 | orig 清理已并入 |
| **`3802ef8`（当前 develop）** | **1**：`map-3d-mode`==`tool-contour-01` | **0** | **0** | 客户端两组重复已删，orig 全清 |

### 3.1 `dump-only` 单独成类（不计入「未比较」）

| stem | 哪一侧有 png | 依据 | 处置 |
|---|---|---|---|
| `tool-weather-01` | 无 orig png（原版 `OpenVipActivity` 为 FLAG_SECURE，`screencap` 0 字节） | orig 有 `.xml` | `dump-only-allow.json` 登记，`report` 记 `dump-only`，只要求 dump |
| `map-water-entry` | 无 orig png（同上） | orig 有 `.xml` | 同上；**另：现有 lc 图是「看山脉」错态，需重采** |

- `right-mountains-entry`、`tool-contour-guide-dismissed`、`tool-measure-mode` 三态**两侧都没有 png**，本来就不在 png 并集里，不占 §4 的计数；它们靠 allow 注入为 `dump-only`。
- 0 字节 PNG 永远失败，豁免只跳过 png 配对，不能掩盖漏采（服务端 `selftest` 46 项覆盖）。

---

## 4. 未比较对的原因分类（36 条，`3802ef8`）

口径：`report` 以 **同名 stem** 配对（`orig/<stem>.png` ↔ `lc/<stem>.png`，`_` 开头的探针除外）；并集 59 = **23 compared + 30 missing-lc + 6 missing-orig = 36 未比较**。

> **与总监先前「25/59、34 对」的差异**：那是在 `ef6d0e4`（客户端去重尚未并入）读到的数。`3286625` 删掉 `favorite-list-02`、`more-sheet-02-scrolled` 两个 lc 文件后，这两对由 `compared` 变成 `missing-lc`，于是 **23/36**。这是口径变化，不是新增缺陷。

| 类别 | 条数 | 判定 | 处置 |
|---|---|---|---|
| **A 命名不对齐**（同一画面、stem 不同，无需重采） | **19** | 见下表 | 索引加「lctem → orig stem」映射，或按 `-state` 别名配对；不改证据 |
| **B 真缺失**（LC 无该页/该能力） | **9** | `drawer-menu` 6 张、`map-poi-sheet-01`、`poi-select-01/02` | 索引已标缺失，成文按缺失计，不重采 |
| **C 待补采**（入口在、证据未采） | **2** | `tool-manage-01`（36）、`favorite-addpoint-01`（38） | 等设备窗口补采 |
| **D LC 增量**（原版无对应态） | **3** | `dial-list-01`、`lc-knowledge-sheet`、`map-roadnet-on` | 不进「未比较」缺口，成文单列增量 |
| **E dump-only**（截图不可得、dump 可复现） | **2** | `tool-weather-01`、`map-water-entry` | `dump-only-allow.json` 登记 |
| **F 语义被替代**（LC 无同态画面） | **1** | `search-typed-coords-01`（LC 搜索被换成知识检索，`findings-client` C-01） | 按 P0 语义差异计，不重采 |

### 4.1 逐条清单（36）

| stem | 类别 | 清单行 | 说明 |
|---|---|---|---|
| `favorite-mode-01`、`favorite-mode-02`、`favorite-save-01`、`favorite-save-login-01` | A | 17 | 原版收藏点模式，lc 对应 `tool-favorite-01` |
| `favorite-list-01`…`favorite-list-04` | A | 37 | 原版收藏列表，lc 共用 `tool-favorite-01`（行 37 已标共用） |
| `more-sheet-01`、`more-sheet-02-scrolled`、`more-sheet-04-edit-entry` | A | 31/32 | lc 对应 `more-sheet-03-fresh`；`02-scrolled` 已按「不可滚动」与 31 共用 |
| `map-jump-beijing` | A | 04 | lc 对应 `map-beijing-z12`（固定态） |
| `right-3d` | A | 29 | lc 对应 `map-3d-mode`（该图待重采） |
| `right-explore` | A | 27 | lc 对应 `map-explore-page`（该图待重采） |
| `right-layer-sheet` | A | 28 | lc 对应 `map-layer-sheet` |
| `search-result-crs-01` | A | 07 | lc 对应 `search-result-02`（四坐标系结果） |
| `tool-compass-01` | A | 08/13 | lc 对应 `tool-compass-page`、`win-02-compass` |
| `tool-elevation-01` | A | 10 | lc 对应 `compass-main-01` |
| `launch-probe-01` | A | 01 | 原版 stem 是 `_launch-probe`（下划线开头），被配对规则排除 |
| `drawer-menu`、`drawer-menu-01`…`05` | B | 34 | LC 无抽屉/侧栏 |
| `map-poi-sheet-01`、`poi-select-01`、`poi-select-02` | B | 05 | LC 无 POI 详情面板 |
| `tool-manage-01` | C | 36 | `MainActivity.java:901-903` 入口存在 |
| `favorite-addpoint-01` | C | 38 | `FishingActivity.java:417-425` 入口存在 |
| `dial-list-01`、`lc-knowledge-sheet` | D | 增量 | LC 自创盘面目录/知识弹层 |
| `map-roadnet-on` | D | 22 | 原版无独立路网开态 stem（对照 `map-home-01.xml`） |
| `tool-weather-01`、`map-water-entry` | E | 33/25 | FLAG_SECURE：截图不可得、dump 可取 |
| `search-typed-coords-01` | F | 06/07 | LC 搜索语义被知识检索替代 |

> 映射依据：索引 §1/§4 的「原版 stem」列 + 两侧 stem 的语义对应。`map-jump-beijing`、`search-typed-coords-01` 两条**未逐像素确认**与 lc 同态（无 lc 同画面文件），标「映射未确认」，成文若要引用请先重采同名态。

---

## 5. 复现命令清单

### 5.0 前置（每次开跑固定）

```powershell
adb -s L2E0222531014833 get-state                     # 必须 device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb push <app-debug.apk> /data/local/tmp/lc.apk
adb shell pm install -r -t --user 0 /data/local/tmp/lc.apk      # 避开 PackageInstaller 确认框
adb shell am force-stop com.luopan.fengshui
adb shell am start -n com.luopan.fengshui/.MainActivity
Start-Sleep 4                                                    # 未完成 onResume 时广播会被忽略
adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 0   # 冻结 heading，否则 dump 报 could not get idle state
```

固定视角 / 读写状态（调试通道，判据是 `LCJS` 回显，不是「发出去就算」）：

```powershell
adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "setView(39.9042,116.4074,12)"
adb logcat -c; adb shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd js --es value "1+1"
adb logcat -d -s LCJS:V        # 期望一行 LCJS map -> 2；无回显=通道不通，禁止按猜测拍照
```

dump 必须「先删后拍」（否则会读到上一态的旧文件）：

```powershell
adb shell rm -f /sdcard/a.xml
adb shell uiautomator dump /sdcard/a.xml
adb pull /sdcard/a.xml
```

### 5.1 客户端章 14 态（一条命令，已含全部坐标）

```powershell
pwsh -File docs\fidelity\collect_lc_client.ps1 -BuildCommit e88ed0c2aa70272e36f6d03a0a6f12511d6bf532
# APK：app\android\app\build\outputs\apk\debug\app-debug.apk（7,059,509 B / 8dd5bf13…）
```

| # | stem | 清单行 | 动作（坐标 px） | 状态指纹（取自本批 dump，用于判「复拍是否到位」） |
|---|---|---|---|---|
| 1 | `launch-probe-01` | 01 | `force-stop` → `am start` → 2 s 截图 | 无 dump（WebView 首帧）；png 必得 |
| 2 | `map-home-01` | 03 | 直接截 | 左 8 右 10；`map-home-01.xml` 主节点 `[0,0][1228,2578]` |
| 3 | `tool-compass-page` | 08 | tap `(103,1868)`「罗盘」 | 左列 6 项（截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地） |
| 4 | `win-02-compass` | 13/14/15 | 同上（同一态连拍） | 右列 4 项（地图/实景/坐南朝北/指针开关） |
| 5 | `compass-overlay-hidden-map` | 09 | tap `(1127,249)`「隐藏」 | 盘面消失；与 `map-home-01` PNG 不同 |
| 6 | `lc-knowledge-sheet` | 增量 | tap `(1127,249)`「搜索」 | `搜索` / `搜索：盘面 / 圈层 / 知识条目` / `盘面（5 种，全部免费）` |
| 7 | `dial-list-01` | 增量 | tap `(613,999)` | `盘面（5 种，全部免费）` + 5 个盘面名 + `确定` |
| 8 | `w3-sheet` | 16 | `确定 (992,1663)` → `关闭 (992,2103)` → 罗盘 → `读盘 (103,1560)` | `智能读盘` / `向北 0.0°    坐南 180.0°` / `向：`×14 |
| 9 | `more-sheet-03-fresh` | 31/32 | tap `(103,2453)`「更多」 | `地图工具` + 16 项；滑 `614 2300 → 614 1500` 后应**不变**（不可滚动） |
| 10 | `setting-map-01` | 35 | 更多 → `设置 (673,963)` | `地图设置` / `地图滤镜` / `模式` |
| 11 | `tool-weather-01` | 33 | 更多 → `天气 (486,1285)` | `天气` / `城市` / `北京` |
| 12 | `tool-favorite-01` | 17/37 | 更多 → `收藏点 (232,1453)` | `收藏点` / `暂无收藏点` |
| 13 | `tool-hdmap-01` | 11 | 更多 → `超清地图 (486,1117)` | `超清地图` |
| 14 | `compass-main-01` | 10 | `setView(...)` → 更多 → `海拔查询 (232,1285)` → tap `(614,1400)` | `海拔查询` / `该点信息` |

### 5.2 地图章 7 态（脚本已覆盖，含前置断言）

```powershell
pwsh -File tools\map\collect_map_lc.ps1 -Apk <app-debug.apk> -Commit 464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a
# 无设备自检（本文件编写时实跑，exit 0）：pwsh -File tools\map\collect_map_lc.ps1 -SelfTest
#   → PASS anchor 5/5、PASS 旧默认(155,576) 不在 3D bounds、PASS 同图对判同、PASS 异图对不误报 → SELFTEST: PASS (8 checks)
```

| stem | 动作 | 期望（脚本内强制断言） |
|---|---|---|
| `map-beijing-z12` | `setView(39.9042,116.4074,12)` | `getState()` 的 lat/lng/zoom 与固定态一致（误差 ≤1e-3 / ≤0.01） |
| `map-3d-mode` | tap `(1124,2430)` | `LC3D mode=3d` + `LC3D texture (ok\|fail)`；`getState().mode==='3d'`；png ≠ 固定态 |
| `tool-contour-01` | tap `(103,2258)` | `LC3D contour on=True`；`getState()` 满足 `mode=3d && contour=true`；png ≠ `map-3d-mode` |
| `map-mountains-entry` | tap `(1124,1072)` | `mResumedActivity` = `MountainActivity` |
| `map-water-entry` | tap `(1124,1267)` | `mResumedActivity` = `WaterActivity` |
| `map-explore-page` | tap `(1124,1462)` | `mResumedActivity` = `DiscoveryActivity` |
| `tool-measure-01` | 更多 `(103,2453)` → `测量 (486,1453)` | **png 必得、dump 尽力**：`dump` 重试 3 次仍超时则降级 png-only 并把原因写进索引 |

### 5.3 地图章 8 态（**脚本未覆盖**，逐步手工命令）

> 复跑缺口：`tools/map/collect_map_lc.ps1` 只覆盖上表 7 态；下列 8 态需手工执行。坐标取自 `docs/fidelity/lc/map-home-01.xml` / `more-sheet-03-fresh.xml` 的实测 bounds。

```powershell
# 搜索页空结果（06）
adb shell input tap 1127 249                                  # 顶栏「搜索」 [1040,192][1215,306]
# 四坐标系结果（07）：输入框 [86,766][1141,918]，输入坐标后取结果
adb shell input tap 1127 249; adb shell input tap 613 842
adb shell input text "116.4074,39.9042"                       # 索引记录：结果态 dump 文本含该串
# 画笔（19）
adb shell input tap 103 2453; adb shell input tap 740 1453    # 更多 →「画笔」 [613,1372][867,1534]
# 钓点（20）
adb shell input tap 103 2453; adb shell input tap 994 1453    # 更多 →「钓点」 [867,1372][1121,1534]
# 轨迹记录（21）
adb shell input tap 103 2453; adb shell input tap 232 1621    # 更多 →「轨迹记录」 [106,1540][359,1702]
# 路网开（22）
adb shell input tap 104 2063                                  # 左列「路网」 [6,1999][201,2127]
# 数据叠加（26）
adb shell input tap 1124 878                                  # 右列「叠加」 [1027,814][1222,942]
# 图层/底图源（28）
adb shell input tap 1124 683                                  # 右列「图层」 [1027,619][1222,747]
```

| stem | 前台/日志期望（可判） |
|---|---|
| `search-result-01` | dump 含 `搜索` + `搜索：盘面 / 圈层 / 知识条目`（**注意：这是 LC 现状，与 findings C-01 的 P0 语义差异同源**） |
| `search-result-02` | dump 含 `116.4074,39.9042`（输入框回显） |
| `tool-brush` | 画笔为地图内交互态（`toggleDraw()`），前台仍 `MainActivity`；判断用底栏出现（颜色/粗细/撤销/清除/导出） |
| `tool-fishing` | `mResumedActivity` = `FishingActivity`；dump 含 `钓点` / `公开水域 80 处 + 本机自建 0 处` |
| `tool-track` | `mResumedActivity` = `TrackListActivity`；dump 含 `历史轨迹` / `轨迹设置` / `开始记录` |
| `map-roadnet-on` | `LCJS` 出现路网开关回显；PNG 与 `map-beijing-z12` 不同 |
| `map-overlay-sheet` | `mResumedActivity` = `OverlayActivity`；dump 含 `数据叠加` / `行政区域` |
| `map-layer-sheet` | `LCJS` 出现 `openSources`；面板含 12 源（3 可用 / 9 置灰） |

### 5.4 4 个待重采项（**阻塞于设备离线，脚本已就绪**）

```powershell
pwsh -File tools\map\collect_map_lc.ps1 -Apk <app-debug.apk> -Commit 464ebb8c50b427fb42b0ea0cbafabb1f4f03d58a `
  -Only map-3d-mode,tool-contour-01,map-water-entry,map-explore-page,tool-measure-01
```

| 项 | 现状 | 重采后判据 |
|---|---|---|
| `map-3d-mode` | 与 `tool-contour-01` 同图（2D） | `LC3D mode=3d` + `getState().mode==='3d'` + png ≠ 固定态 |
| `tool-contour-01` | 同一张 2D 图 | `mode=3d && contour=true` + png ≠ `map-3d-mode` |
| `map-water-entry` | 拍成「看山脉」页 | `mResumedActivity`=`WaterActivity` |
| `map-explore-page` | 拍成「看山脉」页 | `mResumedActivity`=`DiscoveryActivity` |
| `tool-measure-01` | png-only（可接受） | 可保留；若拿到 dump 则补文字量化 |

**这三条不是产品缺陷**：`findings-map` C-04 已在同一 APK 上实测 3D 渲染链路可用（`LC3D webgl ok`→`mode=3d`→`texture ok 36/36`），问题在采集态与脚本默认坐标，已由 `c2ca931` 修正。

---

## 6. 未通过 / 待处理清单（给成文 §7 引用）

| # | 项 | 类型 | 状态 |
|---|---|---|---|
| 1 | `map-3d-mode` == `tool-contour-01`（png+xml 逐字节同） | 证据重复 | **未通过**，等设备重采；门禁保持红灯（不用 allow 掩盖） |
| 2 | `map-water-entry` 拍成「看山脉」页 | 证据错态 | **未通过**，等设备重采 |
| 3 | `map-explore-page` 拍成「看山脉」页 | 证据错态 | **未通过**，等设备重采 |
| 4 | `tool-manage-01`（行 36）、`favorite-addpoint-01`（行 38） | 待补采 | **未通过**，入口已核、等设备 |
| 5 | `tool-measure-01` 无 dump | 能力受限（动画致 idle 超时） | 已知限制，png 级比对可用 |
| 6 | 地图 8 态无脚本覆盖（§5.3） | 复跑缺口 | 已有手工命令；建议补进 `collect_map_lc.ps1` |
| 7 | 索引未写 `checksheet_id` 映射，配对只能靠 stem | 索引口径 | 建议索引加一列，减少命名不对齐误判（§4 A 类 19 条） |

---

## 7. 本次核验的自检命令与输出（可重跑）

```powershell
# 1) meta ↔ 文件哈希
img_hash_ok=29
ALL_META_HASH_MATCH

# 2) 采集批代码同源
git diff --name-only 464ebb8 1f4861b -- app/android   # 0
git diff --name-only e88ed0c 1f4861b -- app/android   # 0

# 3) 证据完整性（同名异 sha / 0 字节）
lc:  duplicates=[map-3d-mode == tool-contour-01]  empty=[]
orig: duplicates=[]  empty=[]

# 4) 配对覆盖（3802ef8）
orig_png=53 lc_png=29 union=59 compared=23 missing_lc=30 missing_orig=6

# 5) 锚点坐标 ∈ dump bounds
16/16 PASS（§2.1）

# 6) 状态指纹
map-water-entry / map-explore-page → 标题「看山脉」[533,173][695,246]（错态）
lc-knowledge-sheet == search-result-01 dump（c8a6a98e…，22 节点）
```

> 纪律：中文不出 PowerShell 管道进 Python（`--out` 直写 UTF-8 后读文件），判据只用 `git diff --quiet` / `git grep` / XML·JSON 原生解析。
