# tools/fidelity — 原版 / 我方还原度对比工具

第二轮复审的量化工具：把同一状态的**原版截图**与**我方截图**对齐、出差图、取色、量控件尺寸与位移，并批量生成 HTML 对照页。全部命令不依赖真机（只有 `collect` 需要），`selftest.py` 用合成图即可回归。

- 依赖：Python 3.10+、Pillow；`numpy` 为 `pair/report` 必需（ΔE2000、快速差图；本机 2.5.0 已装）
- 颜色差统一用 **CIEDE2000**（与 [`docs/fidelity/checksheet.md`](../../docs/fidelity/checksheet.md) 字段 `deltaE2000` 对齐），metrics 同时给 ΔE76 便于对照
- 证据目录与命名规范见 [`docs/fidelity/README.md`](../../docs/fidelity/README.md)
- 工具只读取/写入文件；不改 APK、不改原版数据、不抓 logcat（脱敏由采集人按项目规则处理）

## 0. 自检（无需设备）

```powershell
python tools\fidelity\selftest.py
python tools\fidelity\assets.py selftest
```

合成 320×600 两张图（矩形右移 5 px）+ 两份 uiautomator dump，跑通 `pair / sample / measure / report / collect --dry-run`，末行 `SELFTEST: PASS (81 checks)`，exit 0。`assets.py selftest` 在临时目录造一个假 APK，跑通提取/清单/校验/篡改检测/A-xx 依赖门禁（`SELFTEST: PASS (37 checks)`），同样不需要设备与原版 APK。

## 1. 一键采集（`collect`）

先由操作者把 App 调到目标状态并冻结（工具不注入状态）：

- 1228×2700、动画三项置 0（工具默认会写 `settings put global *_animation_scale 0`，`--skip-anim` 可跳过）
- 罗盘 heading 冻结 0°；地图固定 `39.9042,116.4074`、`z=12`；亮度/滤镜默认
- 原版 `space.xinzhi.globe` 只作对照，不改它的数据/配置

```powershell
# 原版侧
python tools\fidelity\fidelity.py collect --side orig --state compass-main-01 --pkg space.xinzhi.globe --checksheet-id ST-01-compass-main
# 我方侧（同一状态、同一 state id；checksheet §5 硬门槛：build 三字段缺一不可）
python tools\fidelity\fidelity.py collect --side lc --state compass-main-01 --pkg com.luopan.fengshui --checksheet-id ST-01-compass-main `
  --build-branch develop --build-commit <40位提交号> --apk app\android\app\build\outputs\apk\debug\app-debug.apk
# 先看将执行什么，不碰设备
python tools\fidelity\fidelity.py collect --side lc --state compass-main-01 --dry-run
```

`--state` 用证据文件 stem（与 `docs/fidelity/orig/` 现有命名一致，如 `compass-main-01`、`map-beijing-z12`）；checksheet 的行 id（`PG-xx` / `ST-xx-...`）通过 `--checksheet-id` 记进 `meta.json`，两套编号各自唯一、不混用。

**LC 侧构建来源强校验**：`--side lc` 必须给 `--build-commit`（40 位小写 sha）与 `--apk`；`--build-branch` 默认 `develop`、非 develop 拒绝。缺 commit/APK 直接报错退出，不落截图。meta 固定写：

```json
"build": { "branch": "develop", "commit": "<40hex>" },
"apk":   { "path": "...", "sha256": "<hex>", "bytes": 3127xxx }
```

这是 checksheet §5 的作废条件（反例 `cd47703`）；原版侧 `build`/`apk` 为 `null`。

写入 `docs/fidelity/{orig,lc}/<state>.png` + `.xml` + `.meta.json`（截图 SHA256、分辨率、设备型号/系统、包名、采集时间）。已有同名采集默认拒绝覆盖，确认要重拍加 `--force`。`--serial` 或 `ANDROID_SERIAL` 指定设备。

为什么不做批量：每个状态都必须人工调到同一冻结状态，批量循环会把“没调状态”的截图混进证据；批量只发生在比对阶段（`report`）。

## 2. 单对对齐与差图（`pair`）

```powershell
python tools\fidelity\fidelity.py pair `
  --state 01-compass-main `
  --orig docs\fidelity\orig\01-compass-main.png `
  --lc   docs\fidelity\lc\01-compass-main.png `
  --out  docs\fidelity\findings\01-compass-main `
  --threshold 8
```

产出（同一 `--out` 目录）：

| 文件 | 用途 |
|---|---|
| `side_by_side.png` | 左原版 / 右我方，带标签 |
| `diff.png` | 绝对 RGB 差 ×4（便于目检） |
| `diff_mask.png` | 超阈值像素标红、其余白底（只标几何位置） |
| `overlay.png` | 50% 混合，查重影/位移 |
| `metrics.json` | 尺寸、sha256、阈值、差异像素数、比例、bbox、均值/最大 RGB 差、均值/最大 ΔE2000（附 ΔE76） |

规则与参数：

- **尺寸必须一致**，不一致直接失败并打印两边分辨率；不支持缩放对齐（会破坏 px 可比性）。
- `--threshold N`：单通道最大绝对差 > N 才计为差异（默认 8，抑制抗锯齿噪声；0 = 严格逐像素）。阈值写入 metrics，报告里必须带。
- `--offset dx,dy`：我方截图整体相对原版平移后再比；重叠区外不计入指标。用于状态栏/导航栏导致的整体位移，须在 metrics 中留痕。负偏移要写成 `--offset=-5,0`（argparse 会把 `-5,0` 当选项）。
- `--crop x,y,w,h`：两侧同裁同一区域再比（需记录，不得只裁一边）。
- `--ignore-rect x,y,w,h`（可重复）：把区域从**指标**里排除（差图仍全图），用于双方都不该复刻的系统栏；必须记录，禁止用来抹掉真实差异。

ΔE2000 按 sRGB→Lab(D65)→CIEDE2000 计算，与 checksheet 的 `deltaE2000` 同口径；metrics 另附 ΔE76。指标是工具算的，不写“差不多”。

## 3. 取色（`sample`）

```powershell
# 指定坐标：x,y[,名称]，分号分隔
python tools\fidelity\fidelity.py sample --orig docs\fidelity\orig\01-compass-main.png `
  --lc docs\fidelity\lc\01-compass-main.png --points "614,300,title;614,1400,panel"

# 等距网格 6×8，输出 CSV
python tools\fidelity\fidelity.py sample --orig ... --lc ... --grid 6x8 --out checks\fidelity\colors.csv
```

每点输出 `#rrggbb`、RGB、`delta_e2000`（另附 `delta_e76`）；两点同色时 ΔE=0。要写进报告的取色，请同时记录坐标（x,y）与所在控件名，便于复现。

## 4. 尺寸量取（`measure`）

```powershell
# 采集时 collect 已带 dump；也可手工：
adb -s <serial> shell rm -f /sdcard/a.xml
adb -s <serial> shell uiautomator dump /sdcard/a.xml
adb -s <serial> pull /sdcard/a.xml docs\fidelity\orig\01-compass-main.xml

python tools\fidelity\fidelity.py measure `
  --state 01-compass-main `
  --orig-xml docs\fidelity\orig\01-compass-main.xml `
  --lc-xml   docs\fidelity\lc\01-compass-main.xml `
  --out docs\fidelity\findings\01-compass-main
```

产出 `controls-orig.csv` / `controls-lc.csv`（class、text、resource-id、content-desc、bounds、w/h、中心点、clickable/checked 等）与 `control-diff.csv`（按控件匹配后的 `dx/dy/dw/dh`，单位 px）。关键行为：

- resource-id 带包名前缀（`space.xinzhi.globe:id/title` vs `com.luopan.fengshui:id/title`），工具按 `/` 之后的本地 id 匹配，跨包可比。
- 无 id 的控件按 `class + text + content-desc` 匹配；同名控件按出现顺序配对，未配对的计入 `only_orig` / `only_lc`（summary 里给出数量），不静默丢弃。
- WebView 内部节点不会出现在 dump 里（CompassView 自绘同理），地图/罗盘内部差异只能看截图与坐标取色。

## 5. 批量 HTML 对照页（`report`）

```powershell
python tools\fidelity\fidelity.py report --root docs\fidelity --threshold 8
```

扫描 `docs/fidelity/orig/*.png` 与 `lc/*.png`（同名 state 配对），逐对生成差图与指标，另在 `docs/fidelity/findings/report/` 写出：

- `index.html`：汇总表（每 state 的差像素/比例/bbox/ΔE2000/控件匹配数）+ 每状态并排图与差图，可发给产品/审阅直接看
- `summary.json` / `summary.md`：机读/人读汇总
- `<state>/metrics.json`、`side_by_side.png`、`diff.png` 等

缺一侧、尺寸不一致、解析失败都会在表里标 `missing-lc` / `missing-orig` / `error` 并列在控制台。**退出码**：`missing-orig`/`error`、完整性失败、checksheet 冲突、未登记的 `missing-lc`、登记表里已 stale 的条目、或 `--require-checksheet-id` 下的覆盖缺失 → exit 1；**已在索引 §5/§6 登记的 `missing-lc` 属 known-red，控制台按分类汇总打印（含 stem 清单）但不计入失败**，因此无需任何开关就能对全量库出报告。`--allow-known-red` 只用于刻意压掉真实失败（完整性/覆盖/未登记缺口）出报告，**D3 最终核验不要加**。`--strict` 保留兼容但已无作用（默认就是严格）。

**checksheet_id 覆盖与缺图**：报告自动汇总每个「有原版证据的 state」的 `meta.json.checksheet_id`，写进 `summary.json.checksheet`（`required/with_id/missing/conflicts`）、`summary.md` 与 `index.html` 的「checksheet_id 覆盖」小节；orig/lc 两侧 id 不一致记 conflict。D3 的 38/38 校验命令：

```powershell
python tools\fidelity\fidelity.py report --root docs\fidelity --require-checksheet-id
```

退出码 0 = 覆盖齐全且无缺图/无完整性失败；缺 `checksheet_id`、orig/lc 冲突、缺图、重复图、0 字节图都会非 0。缺图清单同时列在 `summary.md` 的 state 表（`missing-orig`/`missing-lc`）。

**known-red §5 ∪ §6 门禁**：`report` 解析索引 §5/§6（列序 `stem｜清单行｜R2｜分类`，`--index` 可另指）。每个 `missing-lc` 必须在登记表里，否则 exit 1；`分类` 必须严格是 `真缺失`/`命名不一致`/`载体不同`/`证据不可得` 之一，带括号注释或其它值直接报错；一个 state 重复登记不同 R2/分类、或一行解析出多个 state 也报错。**R2 列允许 `—`、`-`、空、`N/A`**（表示该条目无独立 R2，例如 `tool-measure-mode` 在报告里没有对应编号），仍照常登记为 known-red。§5/§6 只做登记与分类统计，**不作为 `checksheet_id` 来源**（避免 P0 补采后 orig 的 `R2-*` 与 lc 的 `ST-*` 冲突）。`required` 会扣掉这两组，`checksheet_id` 覆盖只在未登记态上计算。计数与 stem 清单写进控制台和 `summary.json.known_red`（`registered/classes/unregistered_missing/stale`）；已登记态不再逐个打 `[FAIL]`，报告末尾那行 `N pair(s) not compared` 只在**未登记**缺口存在时才算 FAIL。登记表里仍是 `missing-lc`、但实际已可比对的态判 `stale` → exit 1，需回写索引（`--state` 过滤时只看本次选中的 state）。

**证据完整性硬检查**（不看 `--strict`，命中即 exit 1）：

- **同一侧不同 state 的 PNG sha256 相同** → 报 `duplicate screenshot across states`，列出全部 state；同一张图冒充两个状态直接算证据缺陷（Stage 2 的 `map-3d-mode`↔`tool-contour-01` 就是这么被抓出来的）；
- **0 字节 PNG** → 报 `empty screenshot (0 bytes)`，空文件不是截图；
- `_` 前缀文件（如 `_launch-probe.png`）是探针，不参与配对与判重；
- 判重按侧独立（orig 自己比、lc 自己比），跨侧同图不判重；
- 完整性扫描**整个 root**，`--state` 只过滤配对，不会跳过其它 state 的重复图。

结果同时写进 `summary.json` 的 `integrity` 段与 `index.html` 的「证据完整性」小节。

### 5.1 合法同态允许列表

「不可滚动」「两态本来就共享同一画面」这类合法同图，写进 `docs/fidelity/duplicates-allow.json`（或用 `--allow-file` 指定）：

```json
{
  "allow": [
    {
      "pair": ["more-sheet-02-scrolled", "more-sheet-03-fresh"],
      "reason": "更多面板高度固定不可滚动，两态为同一画面；可滚动性本身按差异登记",
      "by": "LC客户端"
    }
  ]
}
```

规则：

- `pair` 必须是两个不同 state id；`reason`、`by` 必填，缺任一 `report` 直接报错退出（不允许静默放行）；
- 只有**整个重复组被允许列表成对连通覆盖**才放行：3 个 state 同图时写 `a-b`、`b-c` 两条即可（`a-c` 由同图传递成立），未覆盖到的 state 仍报错；
- 允许列表只豁免「重复图」；**0 字节 PNG 永远失败**，不能被允许；
- 允许列表引用不存在的 state 只打 warning（便于发现过期条目）；
- 未列入的重复仍 exit 1；允许项在 `summary.json` 里带 `"allowed": true` 和 reason/by，HTML 用绿色标「duplicate (allowed)」。

默认读 `<root>/duplicates-allow.json`；仓库内模板是空 `allow`，各负责人按归属自行追加并保留理由。

### 5.2 dump-only 豁免（截图确实不可得）

`docs/fidelity/dump-only-allow.json`（或 `--dump-only-file`）登记「有 dump、但没有可用截图」的状态：

```json
{
  "allow": [
    {
      "state": "tool-weather-01",
      "reason": "OpenVipActivity 为 FLAG_SECURE，screencap 返回 0 字节；dump 可取",
      "by": "LC逆向"
    }
  ]
}
```

规则：

- 豁免态**只要求 dump、跳过 PNG 配对**；两侧都有 dump 时仍做控件量取，`report` 状态记为 `dump-only`；
- `state` 必须在 `orig/` 或 `lc/` 至少有一份 XML，否则 `report` 直接报错——**豁免不能用来掩盖漏采**；
- 豁免态若出现 0 字节 PNG，仍按空证据失败；完整性检查不跳过；
- 若豁免态两侧 PNG 都存在，`report` 会忽略豁免、按正常配对比较并提示，避免拿豁免藏掉已有截图；
- `state`/`reason`/`by` 缺一即报错；允许列表条目引用不存在的目录会在启动校验时失败。

### 5.3 lc-only 豁免（LC 增量/探针态，原版无对应）

`docs/fidelity/lc-only-allow.json`（或 `--lc-only-file`）登记只有 LC 侧存在、原版没有对应状态的页面/探针：

```json
{
  "allow": [
    {
      "state": "lc-knowledge-sheet",
      "reason": "LC 增量知识库面板，原版无对应态",
      "by": "LC测试"
    }
  ]
}
```

规则：

- `report` 对列入状态记 `lc-only·增量不计配对`：**不参与** `missing-orig`/`missing-lc` 统计，也不进 compared 分母；`summary.counts.lc_only` 单列计数；
- `state` 必须在 `lc/` 有至少一份 png 或 xml，否则 `report` 直接报错——**不能拿它盖漏采**；
- 与 dump-only 名单互斥，同一 state 两边都列直接报错；
- `state`/`reason`/`by` 缺一即报错；
- 只该收录 LC 增量页面、探针态；原版存在但没采到的状态仍应记 missing，不得迁移到这里。

## 6. 批量补 meta（`meta-backfill`）

`docs/fidelity/orig/` 里有些状态只有 png/xml、没有 `meta.json`，导致 `checksheet_id` 覆盖统计偏低。用工具从索引里的 `checksheet_id` 映射**生成** meta，禁止手写：

```powershell
# 先看会写哪些、跳过哪些
python tools\fidelity\fidelity.py meta-backfill --root docs\fidelity --side orig --dry-run

# 真写（只写缺失的 meta；已有 meta 默认跳过）
python tools\fidelity\fidelity.py meta-backfill --root docs\fidelity --side orig

# 复核覆盖
python tools\fidelity\fidelity.py report --root docs\fidelity --require-checksheet-id
```

规则写死：

- 输入只能来自「已有 png/xml」+「`<root>/lc-captures.md` 表格里的 `checksheet_id`」（`--index` 可另指）；`state` 取自文件名；
- `image.sha256`/`dump.sha256` 现算，`width/height` 从 PNG 读，**不提供任何手填 hash/尺寸的参数**；
- 映射冲突（同一 state 两个不同 id）直接报错，整次不写；映射找不到的 state 跳过并打印原因；
- **§5/§6 登记的 known-red 态一律跳过**，理由写 `known-red (index §5)` 或 `known-red (index §6)`；R2 编号只用于分类，不写进 `checksheet_id`；
- 一行索引里出现多个候选 state（如 `ST-C-3d` 行同时含 `map-3d-mode`/`map-3d-img`）时，工具**跳过并 warning**，不猜归属；先把索引改成一行一 state 再跑；
- 任一待写状态证据缺失/PNG 读不出 → **整次中止、不写半份 meta**；
- 已有 `meta.json` 默认跳过，`--force` 才覆盖；只写工具与 meta，不替你提交证据目录；
- 写完重跑 `report --require-checksheet-id`，`with_id` 应从当前值上升，38/38 时才 exit 0。

## 7. 原版素材入库与 sha256 清单（`assets.py`，无需设备）

原版素材（抽屉/更多/工具栏图标、罗盘 `pointer.png`、`assets/compassIntroduce/` 14 篇 HTML）必须**从原版 APK 直接取出**，每个文件可追溯到 APK 内一条 entry，并带 sha256，任何人拿到同一份 `swss.apk` 都能复跑复核。工具只用标准库，不依赖 aapt2/apktool/PIL，不碰设备。

```powershell
# 1) 找条目：按名字 / 扩展名 / 像素尺寸（pointer 就是 46x188）
python tools\fidelity\assets.py list-apk --apk E:\APPDetector\input\swss.apk --size 46x188
# e123222c3ba4      5329      46x188  assets/webapp/assets/imgs/compass/pointer.png

# 2) 按 sources 清单提取（默认落 docs/fidelity/assets/；--dry-run 先看）
python tools\fidelity\assets.py extract --apk E:\APPDetector\input\swss.apk --sources docs\fidelity\assets-sources.json --dry-run
python tools\fidelity\assets.py extract --apk E:\APPDetector\input\swss.apk --sources docs\fidelity\assets-sources.json

# 3) 生成 sha256 清单 + 人读索引
python tools\fidelity\assets.py manifest --dir docs\fidelity\assets

# 4) 校验：清单 / 元数据 / 与 APK 源逐条重算 sha256（+ A-01…A-12 依赖覆盖）
python tools\fidelity\assets.py verify --dir docs\fidelity\assets --apk E:\APPDetector\input\swss.apk --strict
```

`verify` 默认读 `<dir>/../assets-deps.json` 并按计划 §6.1 的 **A-01…A-12** 逐条打印覆盖情况，直接看「缺哪条」就能判 Stage 1 是否齐：

```
[ -- ] deps: docs/fidelity/round2-plan.md §6.1（v1.11）（12 项）
[PASS] deps A-01 pointer.png（位图 46×188）: 1/1：compass/pointer.png
[PASS] deps A-03 更多面板 16 格图标: 18/16：tools/ic_tool_altitude.png, …
[FAIL] deps A-02 抽屉 9 项图标: 0/9（kind icon-drawer | prefix icons/drawer/）；树里现有 kind: bitmap, knowledge-html, tool-icon
[ -- ] deps A-11 128m 读数出处（DEX/可观测行为）: 手工判据 — 取证结论落文档，不在素材门禁内
[FAIL] deps: 素材 4/9 齐；缺 A-02, A-05, A-07, A-08, A-09；手工判据 3 项（A-10, A-11, A-12）
```

- 命中口径：`targets` 精确路径 ∪ `prefixes` 路径前缀 ∪ `kinds`（`PROVENANCE.json` 的 `kind`）∪ `globs`（仓库根相对，给 A-09 录屏用），`count` 是最少文件数；**缺项行会打出树里现有的 kind**，命名对不上时一眼能看出该改哪边；
- **`checksheet` 前缀**：一条素材可带 `meta.checksheet`（如 `A-02 抽屉 9 项图标`），依赖项写 `"checksheet": "A-02"` 就只算该 A 项——A-02（9 抽屉）与 A-04（15 左右列矢量）共用 `kind=icon-drawer`，靠它精确归属（否则 A-02 会显示 24/9）；
- 带 `manual` 的 A-10/A-11/A-12 是取证/真机项，不进素材门禁（挂起规则见计划 §6.1），只打印提示；
- 依赖未齐 → `verify` exit 1（Stage 1 素材门禁）；只想查完整性时加 `--no-deps`；`--deps <file>` 可换清单；
- `docs/fidelity/assets-deps.json` 是计划 §6.1 的机器镜像（口径以 LC产品 的计划为准，计划改动时同步）；
- `sources` 里除 `entry/target/kind/note` 外的字段（`resource`/`raster`/`size`/`use`/`enum`/`evidence`/`checksheet` …）会原样存进 `PROVENANCE.json` 的 `meta`，并出现在 `INDEX.md` 的「资源名 / 用途」列——**复核锚点不在工具里丢**；

`sources` 格式（`docs/fidelity/assets-sources.json` 已含 14 篇 HTML + pointer 的确定性部分，图标按逆向定位的 `res/*.png` 条目追加即可）：

```json
{
  "apk_sha256": "2fd1fc15…（原版 swss.apk 的 sha256，pin 住防换包）",
  "items": [
    { "entry": "assets/compassIntroduce/先天八卦_卦位.html",
      "target": "compass-introduce/先天八卦_卦位.html", "kind": "knowledge-html" },
    { "entry": "assets/webapp/assets/imgs/compass/pointer.png",
      "target": "compass/pointer.png", "kind": "bitmap", "note": "罗盘中心指针 46x188" }
  ]
}
```

### 7.1 解码素材（vector drawable / 二进制 XML）

APK 里 **`.svg` = 0**，`res/` 下 xml 全是编译后的二进制 AXML（魔数 `03000800`），客户端读不了；可读形态必须经 apktool 等解码，**解码产物不可能与原条目逐字节相等**。这类条目在 `sources` 里标 `"decoded": true`（等价写法 `"decode": "apktool"`），走 `stamp` 登记而不是 `extract` 复制。

**溯源三样 + 解码器版本**（总监 M2 Stage 1 定）：`PROVENANCE.json` 必须同时记「原条目 sha256 + 解码文件 sha256 + 资源 id」，外加 **解码工具名与版本**——不同 apktool 版本解出的 XML 不一样，不写死版本复核就失去意义。

```powershell
# 1) 先解码：产物放到 sources 的 target 路径（如 assets/icons/drawer/svg_layer.xml）
# 2) 登记：资源 id 会经 aapt2 dump resources 解析成 APK 内（混淆）条目再算 hash
python tools\fidelity\assets.py stamp --apk E:\APPDetector\input\swss.apk --sources docs\fidelity\assets-sources.json
# [PASS] stamp icons/drawer/svg_layer.xml <- 0x7f0803da -> res/Uy.xml (apktool 2.9.3, 1348 B, sha256 3fc8a1eda0fc…)
```

```json
{
  "apk_sha256": "2fd1fc15…",
  "decode": { "name": "apktool", "version": "2.9.3",
              "command": "apktool d -f -o <dir> E:\\APPDetector\\input\\swss.apk" },
  "items": [
    { "entry": "0x7f0803da",              // 资源 id（推荐）；也接受 "res/Uy.xml" 直写条目
      "apk_entry": "res/Uy.xml",          // 可选：没有 aapt2 时用它把 id 落到条目
      "target": "icons/drawer/svg_layer.xml",
      "kind": "icon-drawer", "decoded": true,
      "decoder": "apktool", "decoder_version": "2.9.3",   // 可省，省略则继承 decode 段
      "resource": "svg_layer", "use": "右列 图层", "evidence": "MainActivity.smali:15130…15527" }
  ]
}
```

- `extract` 遇到 `decoded` 条目会跳过并提示（`decoded item skipped ... run stamp`），其余条目照常复制；
- `stamp` 解析顺序：`entry` 是 `0x..` 资源 id → `aapt2 dump resources` 查 `res/*.xml` 条目（找不到再退回 `apk_entry`）；`entry` 直接是 `res/...` → 直接用。**解析不到条目就 FAIL**（没有原条目 sha 就无法复核）；
- **解码器版本不符直接拒绝**：item 的 `decoder`/`decoder_version` 与 `decode` 段 pin 住的名字/版本不一致 → `stamp` FAIL（selftest 覆盖）；两处都没写 → FAIL；
- `PROVENANCE.json` 里解码文件 `mode = "decoded"`：`entry` = 解析后的 APK 条目路径、`resource_id` = `0x…`、`entry_sha256` = **原条目** sha256、`file_sha256` = **解码文件** sha256、`decoder`/`decoder_version`、`meta.resource` = 资源名；
- `verify --apk` 对解码条目**两样都重算**：条目 sha256 变了（换了包/条目被改）→ FAIL；解码文件 sha256 与登记不一致 → FAIL；缺 `entry_sha256` 或缺 decoder 字段 → FAIL；`verify` 不带 `--apk` 时会提示「原条目不重算」；
- `INDEX.md` 的「载体」列区分 `位图` / `矢量/解码` / `文件`，WebP 尺寸也能读出（底图源缩略图就是 webp）。

产物与判定：

- `MANIFEST.sha256`（`sha256sum` 兼容，`<hash>  <相对路径>`）、`INDEX.md`（文件/载体/字节/尺寸/sha256/APK 条目/资源名/用途/类别）、`PROVENANCE.json`（每条：`mode`、`entry`、`entry_sha256`/`file_sha256`、`apk_sha256`、字节、宽高、kind/note/meta）**全部由工具生成，手改无效**；
- `extract` 幂等：同 hash 跳过；已有文件 hash 不同则拒绝，`--force` 才覆盖；`apk_sha256` 与 sources 不符直接拒绝；APK 里找不到的条目整次失败、一条不写；
- `manifest` 对 **0 字节文件**、**没有 provenance 的文件** exit 1；`verify` 对缺文件、清单外文件、hash 不符、尺寸/字节数不符、provenance 无 APK 条目、文件与 APK 条目不一致、`--strict` 下内容重复 exit 1；干净树打印 `sha256 + provenance + APK source all match`；
- 中文条目名会被 APK zip 按 cp437 误解码，`extract`/`list-apk` 已自动按 UTF-8 还原，**不要手工改名**；
- 仓库**不提交 APK**：`swss.apk` 只在 `E:\APPDetector\input\`，清单只记路径与 sha256。

## 8. 写发现时的硬要求

每条“差异”至少带：`state id` + 复现命令（或入口路径 + 坐标）+ 量化值（px / hex / sp / 文案逐字）。工具输出只是证据，结论里不要出现“差不多/基本一致/类似”；无法复现的不写进报告。
