# tools/fidelity — 原版 / 我方还原度对比工具

第二轮复审的量化工具：把同一状态的**原版截图**与**我方截图**对齐、出差图、取色、量控件尺寸与位移，并批量生成 HTML 对照页。全部命令不依赖真机（只有 `collect` 需要），`selftest.py` 用合成图即可回归。

- 依赖：Python 3.10+、Pillow；`numpy` 为 `pair/report` 必需（ΔE2000、快速差图；本机 2.5.0 已装）
- 颜色差统一用 **CIEDE2000**（与 [`docs/fidelity/checksheet.md`](../../docs/fidelity/checksheet.md) 字段 `deltaE2000` 对齐），metrics 同时给 ΔE76 便于对照
- 证据目录与命名规范见 [`docs/fidelity/README.md`](../../docs/fidelity/README.md)
- 工具只读取/写入文件；不改 APK、不改原版数据、不抓 logcat（脱敏由采集人按项目规则处理）

## 0. 自检（无需设备）

```powershell
python tools\fidelity\selftest.py
```

合成 320×600 两张图（矩形右移 5 px）+ 两份 uiautomator dump，跑通 `pair / sample / measure / report / collect --dry-run`，末行 `SELFTEST: PASS (25 checks)`，exit 0。

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

缺一侧、尺寸不一致、解析失败都会在表里标 `missing-lc` / `missing-orig` / `error` 并列在控制台；`--strict` 时任一未比成返回 exit 1（用于 CI/门禁），默认 exit 0 只做报告。

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

## 6. 写发现时的硬要求

每条“差异”至少带：`state id` + 复现命令（或入口路径 + 坐标）+ 量化值（px / hex / sp / 文案逐字）。工具输出只是证据，结论里不要出现“差不多/基本一致/类似”；无法复现的不写进报告。
