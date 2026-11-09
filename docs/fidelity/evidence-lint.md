# 证据「内容有效性」检查（evidence-lint）

> 定稿：2026-11-08　｜　成文：LC审成文　｜　触发：技术总监 2026-11-08 发现 `docs/fidelity/m3/help-offline.xml` 是 shell 报错产物
> 定位：补 `assets.py verify --strict` 的盲区。那个工具只校验**存在与 sha256**，查不出「文件在、内容是垃圾」。本文给出规则、工具与当前基线。

## 1 为什么需要这条

`docs/fidelity/m3/help-offline.xml` 只有 **47 B**，内容就一行：

```
cat: /sdcard/a.xml: No such file or directory
```

它是当年抓取失败、把 shell 报错存成文件的产物，**不是 dump**，但一直被当证据引用（并由此推出过「原版离线不可读」的结论，该结论现已作废）。
sha256 校验对它是 PASS —— 哈希只能证明「文件没被改过」，不能证明「文件是证据」。

## 2 检查规则（工具已实现）

工具：`tools/fidelity/evidence-lint.py`（新增，Pillow 用于 png，标准库 `xml.etree` 用于 xml）

### 2.1 `.xml`

| 检查 | 判定 |
|---|---|
| 0 字节 | **FAIL** |
| 无法 parse | **FAIL** |
| 首部命中 shell 报错（`cat:` / `adb:` / `No such file or directory` / `error:` / `not found` / `Permission denied` / `uiautomator:` / `cannot ` / `failed to `） | **FAIL**（附首行原文） |
| 根节点是 `<hierarchy>` 但 **0 个 `<node>`**（合法空树） | **FAIL**（`empty dump (0 <node> elements)`）｜只对 dump 生效，SVG/vector 根不判 |
| 根节点不是 `<hierarchy>` | 默认 **WARN**（素材 xml 如 Android vector 会命中；`--strict-root` 时升级） |

### 2.2 `.png`

| 检查 | 判定 |
|---|---|
| 0 字节 | **FAIL** |
| 无法解码 | **FAIL** |
| 尺寸 ≤ 0 | **FAIL** |
| 近似单色（抽样后主色占比 ≥ `--mono-ratio`，默认 0.995） | **FAIL**（全黑/全白/全透明/纯色分别标注） |
| 尺寸与声明不符（`--expect-size 1228x2700`） | **WARN**（不直接判死，避免裁剪图误报） |
| `min(w,h) < --min-png-side`（默认 600） | 跳过纯色检查（小图标本身就该是纯色） |

### 2.3 两条「不要做」的判据（采纳 LC审核验 2026-11-08 独立扫描的结论）

1. **不要用「文件小于 X KB」判垃圾**：审核验全量扫描 183 个 png，10 个 < 8 KB 的全是正常小图标（`pointer` / `ic_3d_white` / 分享图标…），用体积阈值会一次误报 10 个。
2. **不要只靠「纯色方差」判空屏**：`assets/compass/plate/compass5.png` 方差 1.75（近乎纯色），但它是盘面图层，纯色本来就合理 → 这类归「待确认」，不进缺陷；纯色判据只对**截图类** png（min side ≥ 600）生效。

### 2.4 源码换行符（`--eol`，2026-11-08 技术总监要求）

| 情形 | 判定 | 说明 |
|---|---|---|
| 纯 LF | pass | 本仓约定 |
| **混用**（既有 CRLF 又有裸 LF） | **FAIL** | 假 diff 的直接来源：编辑工具重排后一次三行改动被淹没成 203 行 |
| 纯 CRLF | WARN（`--eol-strict` 时 FAIL） | 存量债，需单独立项规范化 |

```
python tools/fidelity/evidence-lint.py --root docs/fidelity \
  --eol app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java
FAIL eol …/MainActivity.java: mixed EOL: 4117 CRLF + 203 bare LF (fake-diff risk)
```

**只检查显式传入的文件，不做全仓判死**：全仓 296/699 个源码/配置含 CR（存量债），一次性判红没有意义；改哪个文件就先查哪个。

**默认参数口径（技术总监 2026-11-08 定）**：默认跑法**不带** `--eol`；带 `--eol` 时**必须显式给文件列表**，裸 `--eol` 打印 `EOL: SKIP` 并跳过，绝不因「没给列表」而全仓判红。

```
# ① 默认（不查换行）
python tools/fidelity/evidence-lint.py --root docs/fidelity
EVIDENCE-LINT: … fail=0 … exit=0

# ② 裸 --eol（跳过，不判死）
python tools/fidelity/evidence-lint.py --root docs/fidelity --eol
EOL: SKIP (no explicit file list; pass --eol <file[,file...]>)

# ③ 只查本次改动碰到的文件
python tools/fidelity/evidence-lint.py --root docs/fidelity \
  --eol app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java
FAIL eol …: mixed EOL: 4117 CRLF + 203 bare LF (fake-diff risk)   exit=1
```

> 理由：**门禁只有精确才有人守，宽泛的红等于没有门禁**——存量混用随专项规范化一并改，到那时才让 `--eol` 支持全仓判。

### 2.5 扩展名与真实格式一致性（`--magic`，2026-11-08）

```bash
python tools/fidelity/evidence-lint.py --root <dir> --magic a.webp,b.webp
# .png 必须是 PNG（89 50 4E 47）；.webp 必须是 RIFF —— 不符即 FAIL
```

按 T-04 只查**显式传入的文件**，不做全仓判死。

**历史存量（另册登记，专项清）**：仓内 `docs/fidelity/assets/` 有 **19 个 `.png` 实为 WebP（magic `RIFF`）**，内容完好、可解码，属原版资源解码时的扩展名遗留：

| 目录 | 数量 | 文件 |
|---|---|---|
| `assets/compass/plate/` | 8 | `compass5/6/7/9/10/11/12/13.png` |
| `assets/map/source-thumb/` | 10 | `ic_base_map_*.png`（amap/custome/tdt/tecent/tencent/xintu 各图源缩略） |
| `assets/pages/account/icons/` | 1 | `ic_default_avatar.png` |

> 这批**不影响** `assets.py` 清单（它按 sha 记账、不看扩展名）；专项清时统一改 `.webp` 或按原版真实格式命名，改后重跑 `assets.py manifest/verify`。

### 2.6 同目录内图片字节查重（`DUP`，2026-11-09 技术总监要求）

**规则**：同一目录内，两张图片 sha256 相同 → **FAIL**（不是 warn）。跨目录复用不计——合法复用（如 `lc_splash_logo.webp` 同时存在于 `res/drawable-nodpi` 与 `docs/fidelity/assets/brand/`）本来就该存在。
判法通用：`同一目录内按内容 sha256 去重`，不写死任何目录名。

**为什么加这条**：`lc-help/` 29 张图里 10 张是别的图的副本，而四条既有门禁一条都报不出来——尺寸 1228×2700 全对、可解码、引用 29=29、blob 门禁只看 `index.html`。
最严重的一例是 `fig-32`（图注写「点一下『点击查看 ›』就地展开」）与 `fig-08`（未展开态）**字节完全相同**：等于用点击前的画面冒充展开后的画面。
所以判 FAIL 不判 warn：发现它这件事本身就说明这类重复会冒充别的证据。

```bash
python tools/fidelity/evidence-lint.py --root app/android/app/src/main/assets/lc-help --expect-size 1228x2700
# DUP: files=29 unique=22 duplicated=10 redundant=7
# EVIDENCE-LINT: … fail=7 …  → exit=1（真红）
```

三个数的口径：`files`=图片数、`unique`=唯一内容数、`duplicated`=落进重复组的文件数、`redundant`=删掉后可减少的文件数（=duplicated − 重复组数）。

**已知待处置（2026-11-09 首次开启时的既有存量）**：

| 目录 | 重复对 | 结论（2026-11-09） |
|---|---|---|
| `docs/fidelity/m3/` | `complaint.png` = `complaint-tab1.png` | **合法重复·具名例外**：被报的是正本，真正冗余的是零引用的 `complaint-tab1.*`（改名残留） |
| `docs/fidelity/m3/stage3/` | `i-share-wx.png` = `i-share-circle.png` | **合法重复·具名例外**：原版同一 `HwResolverActivity` 面板采集两次（微信/朋友圈同一降级路径） |

两对都经 `docs/qa/dup-pairs-verdict.md` 判定、LC审核验 独立取 blob 终验 PASS，已登记进 `tools/fidelity/evidence-lint-allow.json`。
登记后 `--root docs/fidelity` → `fail=0 allowed=3 exit=0`（stderr 不再出现这两条 `FAIL dup`）。

> **工具输出补充**：`dup` 只报「同组里非 kept 的那些」，而 kept 是按字典序取的第一个，所以被报的**可能是正本**（`-` < `.`，`complaint-tab1.png` 反而被当 kept）。为此每条 dup 现在都带 `group`（整组文件名）与 `kept` 字段，控制台行尾也会打出 `[group: …; kept=…]`——写例外 reason 时据此说明「被报的是正本」，别读成「把被引用的证据豁免掉了」。

### 2.7 逐图声明尺寸（`--expect-size-map`，2026-11-09）

**先修一个空转**：`--expect-size` 在 2026-11-09 之前**只解析、不比较**——参数收下了，`check_png` 里根本没用它，所以「尺寸不符」从来没被查过。现在补实，并且：

- `--expect-size` = **未命中逐图表时的默认值**（整屏图 1228×2700）；
- `--expect-size-map <json>` = **逐图期望尺寸表**，键是「仓库相对路径 glob」或「basename glob」，值是 `WxH`；以 `_` 开头的键当注释跳过；
- **不符判 FAIL**（不是 warn）：声明了尺寸就代表「这张图应该长这样」，不符说明它不是它声称的那张。

逐图表在 `tools/fidelity/evidence-size-map.json`，6 条来自 `docs/qa/help-figures-audit.md`（LC审成文 2026-11-09 从原图逐张裁出、样张复核）：

| 图 | 声明尺寸 |
|---|---|
| `fig-07` 角注条 | 1228×68 |
| `fig-10` 工具条 5 项 | 1228×136 |
| `fig-11` 左列 6 项 | 201×1111 |
| `fig-12` 右列 4 开关 | 230×758 |
| `fig-14` 左列 8 项 | 201×2048 |
| `fig-17` 右列 10 项 | 201×1889 |

```bash
# 裁切前（按现网未裁的图跑）→ 6 条 declared/actual 不符，真红
python tools/fidelity/evidence-lint.py --root app/android/app/src/main/assets/lc-help \
  --expect-size 1228x2700 --expect-size-map tools/fidelity/evidence-size-map.json
# SIZE-MAP: 6 rule(s) … / FAIL png …fig-07.webp: declared 1228x68, actual 1228x2700 … → exit=1

# 裁切后 → 这 6 条应转绿（自测用同尺寸噪声图，见 §5）
```

> 落地顺序：**先裁图、再开这个参数**。图没裁就带上逐图表，门禁必红——这是设计如此，不是误报。

## 3 用法

> **路径基准（2026-11-08，技术总监要求）**：工具内一切相对路径（默认 root、默认豁免清单、`--eol`/`--magic` 的文件、豁免条目里的 `path`）**一律以「脚本所在仓库根」为基准**，不依赖 cwd。
> 理由：门禁工具会被复制、被别的脚本调用、在别的工作树里跑——**依赖 cwd 的工具，换个人跑就是另一个结果**，这种差异最容易被误读成「别人改坏了」。
> 实测：仓库根、`C:\` 两个目录下不带参数各跑一次，结果同为 `fail=0 warn=0 allowed=1 exit=0`。

```bash
# 全量证据（--root 默认 docs/fidelity，可省略）
python tools/fidelity/evidence-lint.py --allow tools/fidelity/evidence-lint-allow.json

# 带声明尺寸 + JSON 落盘 + 严判
python tools/fidelity/evidence-lint.py \
  --expect-size 1228x2700 --strict --allow tools/fidelity/evidence-lint-allow.json \
  --json checks/evidence-lint.json

# 只看某批（如 M6 四页证据）
python tools/fidelity/evidence-lint.py --root docs/fidelity/m6
```

- 退出码：`0` 无 FAIL；`1` 有 FAIL（`--strict` 时 WARN 也算）；`2` 参数/路径错误。
- 默认跳过目录：`assets-vectors,assets,findings,probes,.git`（素材与生成物不是「采集证据」）。

### 3.1 具名例外清单（技术总监 2026-11-08 裁定）

**默认自动加载**：不带 `--allow` 时，工具自动读 `tools/fidelity/evidence-lint-allow.json`；因此**默认命令（默认 root）就是绿的**，不需要每次记着加参数、也不必改用窄 root。

```bash
python tools/fidelity/evidence-lint.py                # 默认 root + 默认清单
ALLOWED xml docs/fidelity/m3/help-offline.xml …
EVIDENCE-LINT: scanned xml=155 png=130 other=141 fail=0 warn=0 allowed=1   exit=0

python tools/fidelity/evidence-lint.py --allow <空清单>  # 反证：没有豁免就 red
FAIL xml docs/fidelity/m3/help-offline.xml: cat: /sdcard/a.xml: …          exit=1
```

**证据文件不删、不改**（动了就毁审计痕迹）。确实要放过某份垃圾证据时，登记到
`tools/fidelity/evidence-lint-allow.json`，由工具把该条从 FAIL 降为 **ALLOWED** 并打印理由：

```json
{"allow": [
  {"path": "docs/fidelity/m3/help-offline.xml",
   "reason": "47 B，仅一行 shell 报错，非 dump；据此推出的结论已作废",
   "by": "LC技术总监 2026-11-08 认定",
   "verdict": "非证据·作废；保留文件仅作审计痕迹，任何判据/台账不得引用"}
]}
```

- **四键必填**：`path` / `reason` / `by` / `verdict`，缺一直接 `ERROR` 并退出 2（防止用空理由蒙混）。
- 用法：`--allow tools/fidelity/evidence-lint-allow.json`。
- 判定分层：`FAIL` = 必须处理；`ALLOWED` = 有具名认定、可不处理但**永远打印**；`WARN` = 需人看。
- 哪天有人质疑「为什么放过它」，看这份 JSON 即可。

## 4 当前基线（2026-11-08，`develop` 树）

**带具名例外清单（推荐口径，Stage 4 用这条）**：

```
python tools/fidelity/evidence-lint.py --root docs/fidelity \
  --expect-size 1228x2700 --allow tools/fidelity/evidence-lint-allow.json
ALLOWLIST: 1 entry(ies)
ALLOWED xml docs/fidelity/m3/help-offline.xml: 47 B，仅一行 shell 报错… [by LC技术总监…; verdict 非证据·作废…]
EVIDENCE-LINT: scanned xml=155 png=130 other=136 fail=0 warn=0 allowed=1
exit=0
```

**不带清单（反证：证明例外是「具名豁免」而不是「判据失效」）**：

```
EVIDENCE-LINT: scanned xml=155 png=130 other=136 fail=1 warn=0 allowed=0
FAIL xml docs/fidelity/m3/help-offline.xml: cat: /sdcard/a.xml: No such file or directory
exit=1
```

- **全库唯一垃圾证据就是 `help-offline.xml`**，与 LC审核验 独立扫描（347 个 xml、183 个 png）的结论一致：**孤例，不是系统性问题**。
- `warn=0` 说明本轮纳入范围的 130 张截图无「声明尺寸不符」。

## 5 自测（可复跑，证明判据不是空转）

用 `checks/seed-lint-selftest.py` 生成 8 个样本（不入库，临时目录），期望 6 FAIL / 2 PASS；另用 3 个 xml 单测空树规则：

```
FAIL blank-black.png   : effectively black (blank screen)
FAIL blank-white.png   : effectively white (blank screen)
FAIL solid-color.png   : monochrome 1.0000 rgb=(12,34,56)
FAIL zero.png          : zero-byte
FAIL shell.xml         : cat: /sdcard/a.xml: No such file or directory
FAIL truncated.xml     : unparseable: unclosed token: line 1, column 32
（real.xml / real-shot.png 不报 = PASS）
EVIDENCE-LINT: scanned xml=3 png=5 fail=6 warn=0

# 空树规则（合法但无内容）
FAIL empty.xml         : empty dump (0 <node> elements)     ← <hierarchy/> 也判死
（real.xml / vector.xml 不报 = PASS；vector 根不受空树规则影响）
EVIDENCE-LINT: scanned xml=3 png=0 fail=1 warn=0
```

> 空树用例由 LC审核验 2026-11-08 提出：`<hierarchy/>` 能 parse、但没有 `<node>`，旧规则会放过；现补为 FAIL，且只对 `hierarchy` 根生效。

## 6 与现有工具的关系

| 工具 | 管什么 | 不管什么 |
|---|---|---|
| `assets.py verify --strict` | 文件存在、sha256 与清单一致 | 文件内容是否可用 |
| `fidelity.py report` | 原版/我方配对、重复图、0 字节 | xml 是否是 shell 报错、png 是否空屏 |
| **`evidence-lint.py`（本文）** | **内容有效性**（parse / shell 报错 / 空屏 / 尺寸） | 配对与 sha 一致性（交给上面两个） |

> 三者互补，不互相替代。Stage 4 复验口径：**先 `assets.py`（齐不齐）→ 再 `evidence-lint.py`（能不能用）→ 最后 `fidelity.py report`（配不配得上）**。

## 7 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-08 | 补 --magic（扩展名↔magic 一致性，.png 必须 PNG、.webp 必须 RIFF）；图像扫描扩到 .webp；登记 19 个历史 WebP 误命名文件 |
| 2026-11-08 | --eol 收口为「必须显式文件列表」：裸 --eol 打印 EOL: SKIP 并跳过；默认跑法不带 --eol。三档实测：默认 exit0 / 裸 --eol SKIP exit0 / 指定 MainActivity FAIL exit1 |
| 2026-11-08 | 补 --eol：混用换行 FAIL、纯 CRLF WARN（--eol-strict 判 FAIL）；只查显式文件、不做全仓判死（存量 296/699 含 CR） |
| 2026-11-08 | 按 LC审核验 建议补两条：**空 dump（`<hierarchy>` 根、0 个 `<node>`）判 FAIL**（仅对 dump，SVG 不适用）；**`--root` 默认 `docs/fidelity`**（不再必填，避免上手 `exit 2`）。实测：真实证据 `fail=0 allowed=1`（带清单），`empty.xml` 单测 FAIL |
| 2026-11-08 | 增具名例外清单 `--allow`（四键必填，FAIL→ALLOWED 但永远打印）；基线更新为 `fail=0 / allowed=1 / exit 0`（带清单）与 `fail=1`（不带，反证）；证据文件不删不改 |
| 2026-11-08 | 建工具与规则；首次全量基线：`xml=155 / png=130 / fail=1（help-offline.xml）/ warn=0`；自测 6 FAIL 2 PASS 通过 |
