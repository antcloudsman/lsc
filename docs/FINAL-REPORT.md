# 山水罗盘 LC · 收官报告

> 日期：2026-11-05　|　分支：`develop` @ `f688c60`（166 个提交）　|　项目：`E:\APPDetector\output`
> 验收复验提交：`55b4ca8`（P2-06 可见性改为「判图不判 DOM」后重跑 18/18）；矩阵最终版由 `0910112` 并入。
> 结论：**1:1 复刻目标在「读数与圈层数值」维度已闭环且有 DEX 逐指令证据；盘面与地图按 B 方案做结构等价替代；剩余缺口全部登记为「有意省略 / 有意解除 / 有意差异 / 未做」，无一条是「说不清」。**

---

## 1. 一句话状态

- 双端（Rust 桌面演示 + Android 正式产品）由 `develop` 一条历史构建，四门质量门禁全绿，收官 AT 18/18 PASS、0 FAIL、0 BLOCKED。
- 数值只有**一个来源**：`app/mechanics.json` v2 → 生成器 → 两端常量 + golden；780 条圈层文字全部来自原版 DEX 取证。
- 原版核心读数缺陷已修：人盘/天盘不再恒等于地盘，六十龙落位与读数同源。

## 2. 交付物

### 2.1 数值与 1:1（核心）
| 项 | 状态 | 证据 |
|---|---|---|
| 14 类圈层 780 条文字 | 已取证 | `getTextList()` packed-switch，780 条逐条 code-unit 地址（`.inspect/derived/ring_label_addrs.json`） |
| 人盘/天盘读数 | 已修 | TYPE_8/9 `offset=0.0f` + labels 旋转；`人=(floor(h/15)+1)%24`、`天=floor(h/15)%24` |
| 六十龙落位 | 已修 | 刻度 `1.5+6k`、文字中心 `4.5+6k`，与读数共用 `(interval, offset)` |
| 单一数值源 | 已落地 | `mechanics.json` v2 → `mechanics_gen.rs` / `MechanicsGen.java`（入库 + sha256 头 + `-Check`） |
| 跨端回归 | 已落地 | `tests/numeric` 86 项：两端 RINGS 与 labels 780 条全等、读数公式、边界、golden |
| 5 盘面 | B 方案 | 四组结构 + 常用 5 项（原版 artwork）+ 简易/综合置灰占位 |

### 2.2 Android 产品面
- 盘面：`CompassListActivity` 四组；位图盘面模式（原版全尺寸 PNG 5 幅，≤1200px 采样，Java Heap 11.9 MB）。
- 读数：**智能读盘 14 行**（`向：/坐：` + 顶部向北/坐南 + 坐山/向山），全量显示，按 D-006 解除原版 11 行付费墙。
- 3D：Terrarium DEM 真高程 + 天地图影像贴面（2048 POT + mipmap）、等高线、贴地、指针开关、坐南朝北双标签、全屏退出、12 源底图列表（天地图 3 源可用，其余 9 源置灰）。
- 3D 点选：屏幕射线与 DEM 求交 → `LCAndroid.onEvent` → `MapMarker{lng,lat,elevM,headingAtPick,src}`；缺高程传 `null`，不联动罗盘。
- 截图：PixelCopy 抓罗盘页 → 存 `Pictures/LC/`、文件名 `坐向{坐}向{向}_yyyyMMdd_HHmmss`、无 GPS EXIF、直接拉起系统分享、**角注画进图内**。
- 地图合规：按源绑定审图号，当前显示「底图：天地图地形 · 审图号：未确认」；禁用第三方审图号。

### 2.3 桌面演示端
- `dial.rs` 单一盘面渲染器：egui 真 UI 与无头 `software.rs` 共用同一套绘制与 chrome 尺寸，`central_dial(1100,780)=(540,409,232)` 两端一致。
- 1100×780 截图纳入门禁；`--dump-rings <h>` 输出 14 行供逐字比对。

## 3. 质量门禁与最终验收

```
scripts/smoke.ps1 -SkipDevice
desktop  : PASS      # cargo build + 1100×780 截图 + mechanics 19 项
numeric  : PASS      # tests=86 failures=0 errors=0 expected_failures=0 unexpected_successes=0
mech-src : PASS      # gen-mechanics -Check：19/19 sourced，0 estimated，生成物与 JSON 一致
android  : PASS      # assembleDebug（真机腿单独跑）
```

收官 AT（`tests/at/run-final.ps1`，真机 `L2E0222531014833`）：

| 组 | 用例 | 结果 |
|---|---|---|
| 功能开关 | SW-006 on/off ①②③ | PASS（3 为诊断，不作门禁） |
| 读数可信 | RT-005 界面 45→30→30.00°、RT-011 三项 | PASS |
| 一键留档 | P2-01/03/04/05/06a/06b | PASS（EXIF 干净、分享 sha 一致、角注可见） |
| 读数面板 | P2-07 14 行 14/14、P2-08 顶部两行 | PASS |
| 3D 点读 | TD-001…015 无头、TD-016 真机 | PASS（picks=5, fps 60–61, FATAL 0） |
| 盘面/文字 | AT-B2：桌面 112 行、真机 616 行 | PASS（0 不符） |

## 4. 差距矩阵口径（`docs/gap-matrix.md` §00）

| 状态 | 行数 | 含义 |
|---|---|---|
| 已 1:1 | 2 | DEX 逐指令证据且实现同式 |
| 等价替代 | 15（+F5 后 16） | 行为等价、实现或素材不同 |
| 缺失 | 24 | 原版有、当前没有（多为低价值/未取证） |
| 有意省略 | 4 | 账号/发票/推荐、广告会员弹窗、圈层开关（重建自创） |
| 有意解除 | 1 | 读盘 14 行：原版付费墙，按 D-006 全显示 |
| 未确认 | 0 | 上一轮 E6 已用真机控件清单结案 |

## 5. 有意差异 / 未做（不粉饰清单）

| 项 | 定性 | 原因 |
|---|---|---|
| 默认底图源 `siJi_satellite` → 天地图 | 有意差异 | 世纪空间需授权，D-006 走天地图个人 key |
| 审图号「未确认」占位 | 有意差异 | 官方页 WAF 418（我方与产品均被拦），待用户在浏览器取页脚号回填 |
| 简易/综合组盘面内容 | 有意省略 | 目录来自 `GET api/compass/list`，离线不可穷举；B 方案保留结构 |
| 宅卦（「离宅」） | 未做 | 只传 heading 反推不了八宅，无证据不臆造 |
| 实景（街景入口） | 未做 | 是否第三方 SDK 未取证 |
| 记录库 CRUD | 未做 | 从未进入范围（T-16 出圈），AT 已摘除 P2-09…11；矩阵 §H 已登记为「增量·未做」 |
| 3D 立向点持久化 / 记录 | 未做 | 超出 P0-4 本轮范围 |

## 6. 过程治理

- 决策记录：`docs/TEAM_CHARTER.md` D-001…D-009（自用不上架、单真机、Android 主产品线、单一数值源、看山势与定坐向并重、不收费、worktree 提交隔离、文案诚信、磁偏角手动值 + 3D 倍率）。
- 提交纪律：独立 worktree + `scripts/check-staged-paths.ps1 -Branch`（强校验 HEAD、产物、越界路径、`[成员名]`）；禁止 `--amend`；补丁必须以 `git show develop:<path>` 为基线；提交前断言 `git rev-parse --show-toplevel`。
- 事故与修复（全部已恢复、已立规）：共享树 HEAD 被切导致 3 次提交串分支；一次 `--amend` 改写他人提交（内容已复原）；一次补丁打旧文件覆盖 `url/js` 命令（已补回并加可观测日志）。
- 真机采集方法沉淀：`docs/qa/device-method.md`（`uiautomator` idle、过期 dump、`adb shell` 吃 `&`、装包须 `force-stop`、`bridge registered` 被刷、通道用 `js 1+1 → LCJS` 判活、角注只能判图不能判 DOM）。

## 7. 复现

```powershell
cd E:\APPDetector\output
pwsh -File scripts\smoke.ps1 -SkipDevice        # 四门（桌面/数值/单源/Android 构建）
python tests\numeric\run_all.py                 # 86 项数值与跨端
pwsh -File tests\at\run-final.ps1 -Apk app\android\app\build\outputs\apk\debug\app-debug.apk   # 真机 18 项
pwsh -File tools\map\run_at_p04.ps1             # 3D 点读无头 15 项
```

## 8. 下一步建议（若要继续）

1. 取到天地图审图号后回填角注（一处常量 + 重跑 P2-06）。
2. 记录库 / 立向点持久化——原版有明确对应功能，属增量。
3. 实景与宅卦——必须先取证再开工，不猜。
4. 若要提升盘面 1:1：把「当前格高亮」在位图模式下按盘面 artwork 逐盘校准半径。
