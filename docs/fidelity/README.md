# docs/fidelity — 第二轮还原度复审证据目录

以原版 `space.xinzhi.globe` 1.6.1 为唯一对照，存原版/我方同状态截图、uiautomator dump 与比对产物。工具见 [`tools/fidelity/README.md`](../../tools/fidelity/README.md)。

## 目录

```
docs/fidelity/
  orig/                          # 原版采集（只读对照，不改原版数据）
    <state>.png                  # 1228×2700 截图
    <state>.xml                  # uiautomator dump（原生控件；WebView 内部不可见）
    <state>.meta.json            # 采集时间、设备、包名、checksheet_id、build 来源、sha256、分辨率
    _captures.tsv                # 采集流水（页面/时间/Activity/dump 状态），由采集人维护
  lc/                            # 我方采集，文件名与 orig 一一对应
    <state>.png / .xml / .meta.json
  checksheet.md                  # LC产品维护：字段定义 + 严重度 + 覆盖面
  orig-inventory.md              # LC逆向维护：≥30 页面/状态清单（入口路径/状态机）
  duplicates-allow.json          # 合法同态允许列表（reason/by 必填；只豁免重复图）
  dump-only-allow.json           # dump-only 豁免（reason/by 必填；只跳过 PNG 配对）
  lc-only-allow.json             # LC 增量/探针态豁免（不参与配对统计；reason/by 必填）
  assets-sources.json            # 原版素材 ↔ APK 条目清单（assets.py 的输入，含 APK sha256 pin）
  assets/                        # 原版素材（M2 Stage 1）：图标/pointer/14 篇 HTML
    MANIFEST.sha256              # sha256sum 兼容清单（工具生成，勿手改）
    INDEX.md                     # 人读索引：文件/字节/尺寸/sha256/APK 条目/类别
    PROVENANCE.json              # 每条的 APK 条目、entry_sha256、apk_sha256、宽高
  findings/                      # 工具生成，勿手改
    report/index.html            # 批量对照页
    report/summary.json|summary.md
    report/<state>/              # side_by_side.png / diff.png / diff_mask.png / metrics.json
```

## state 命名与两套编号

- **证据 stem（文件名）**：`<page-or-state>-<nn>`，小写短横线，如 `compass-main-01`、`map-beijing-z12`、`more-sheet-01`；两侧必须同名。已有 `docs/fidelity/orig/` 的 stem 是准绳，新采集沿用，不另起风格。
- **checksheet 行 id**：`PG-xx`（页面）/ `ST-xx-<状态>`（状态），由 [`checksheet.md`](checksheet.md) 定义、全局唯一不复用；采集时用 `collect --checksheet-id` 记进 `meta.json`，用于证据 ↔ 清单双向追溯。
- 一页多状态用同一 stem + 不同后缀区分：`settings-default-01`、`settings-dark-02`；不要用 `-final`、`-new` 这类不可排序命名。
- 每个 state 的截图、dump、meta 三件套齐全才算采集完成；缺 dump 的要在清单里标「截图-only」并说明原因（如纯 WebGL 页面）。

## 固定采集口径

| 项 | 值 |
|---|---|
| 分辨率 | 1228×2700（`collect` 会警告其它尺寸） |
| 系统动画 | window/transition/animator 三项 = 0（`collect` 默认写入） |
| 罗盘 | heading 冻结 0° |
| 地图 | 固定 `39.9042,116.4074`，`z=12` |
| 亮度/滤镜 | 默认，不额外调色 |
| 设备 | 单台真机，按项目群窗口串行使用 |
| LC 构建来源 | `meta.build.branch=develop`、`build.commit` 40 位、`meta.apk.sha256`/`apk.bytes`；缺一整批作废（checksheet §5） |

原版只作对照：不修改其数据、收藏、设置；采集结束按纪律释放设备。日志与 dump 不含 key/token；如需提交日志，先按项目 LogRedact 脱敏。

## 比对产物

`findings/` 由 `python tools/fidelity/fidelity.py report --root docs/fidelity` 生成，是可再生成的派生证据，不手工编辑；需要留档的结论写进审阅文档并引用 `state id` 与 metrics 数值。

`report` 带证据完整性硬检查：同一侧不同 state 的 PNG sha256 相同、或出现 0 字节 PNG，都会 exit 1 并在 `summary.json` 的 `integrity` 与 `index.html` 标出；不要用一张图冒充多个状态，缺图按「缺失」登记。合法同态（如「更多面板不可滚动」）写入 `duplicates-allow.json`，每条必须带 `reason` 与 `by`，缺字段直接报错；0 字节 PNG 不能豁免。

截图确实不可得（如 `FLAG_SECURE`）但 dump 存在的状态，写入 `dump-only-allow.json`：`report` 只跳过 PNG 配对、仍做控件量取；引用无 dump 的 state 或出现 0 字节 PNG 都会失败，不能拿来掩盖漏采。

只有 LC 侧存在、原版无对应状态的增量页/探针，写入 `lc-only-allow.json`：`report` 记 `lc-only`、不计入缺原版/缺 LC 统计；必须在 `lc/` 有 png 或 xml，否则报错。

## 快速命令

```powershell
# 采一侧（另一侧换 --side/--state 同名）
python tools\fidelity\fidelity.py collect --side orig --state compass-main-01 --pkg space.xinzhi.globe --checksheet-id ST-01-compass-main
python tools\fidelity\fidelity.py collect --side lc   --state compass-main-01 --pkg com.luopan.fengshui --checksheet-id ST-01-compass-main `
  --build-branch develop --build-commit <40位提交号> --apk app\android\app\build\outputs\apk\debug\app-debug.apk

# 批量对照页
python tools\fidelity\fidelity.py report --root docs\fidelity --threshold 8

# 无设备自检
python tools\fidelity\selftest.py
```
