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

## 3 用法

```bash
# 全量证据（跳过素材目录）
python tools/fidelity/evidence-lint.py --root docs/fidelity

# 带声明尺寸 + JSON 落盘 + 严判
python tools/fidelity/evidence-lint.py --root docs/fidelity \
  --expect-size 1228x2700 --strict --json checks/evidence-lint.json

# 只看某批（如 M6 四页证据）
python tools/fidelity/evidence-lint.py --root docs/fidelity/m6
```

- 退出码：`0` 无 FAIL；`1` 有 FAIL（`--strict` 时 WARN 也算）；`2` 参数/路径错误。
- 默认跳过目录：`assets-vectors,assets,findings,probes,.git`（素材与生成物不是「采集证据」）。

## 4 当前基线（2026-11-08，`develop` 树）

```
python tools/fidelity/evidence-lint.py --root docs/fidelity --expect-size 1228x2700
EVIDENCE-LINT: scanned xml=155 png=130 other=127 fail=1 warn=0
FAIL xml docs/fidelity/m3/help-offline.xml: cat: /sdcard/a.xml: No such file or directory
```

- **全库唯一垃圾证据就是 `help-offline.xml`**，与 LC审核验 独立扫描（347 个 xml、183 个 png）的结论一致：**孤例，不是系统性问题**。
- `warn=0` 说明本轮纳入范围的 130 张截图无「声明尺寸不符」。

## 5 自测（可复跑，证明判据不是空转）

用 `checks/seed-lint-selftest.py` 生成 8 个样本（不入库，临时目录），期望 6 FAIL / 2 PASS：

```
FAIL blank-black.png   : effectively black (blank screen)
FAIL blank-white.png   : effectively white (blank screen)
FAIL solid-color.png   : monochrome 1.0000 rgb=(12,34,56)
FAIL zero.png          : zero-byte
FAIL shell.xml         : cat: /sdcard/a.xml: No such file or directory
FAIL truncated.xml     : unparseable: unclosed token: line 1, column 32
（real.xml / real-shot.png 不报 = PASS）
EVIDENCE-LINT: scanned xml=3 png=5 fail=6 warn=0
```

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
| 2026-11-08 | 建工具与规则；首次全量基线：`xml=155 / png=130 / fail=1（help-offline.xml）/ warn=0`；自测 6 FAIL 2 PASS 通过 |
