# M6 第六轮 · 文档归档索引（Stage 6）

> 成文：LC审成文　｜　日期：2026-11-08　｜　基线：`develop` `a09e076`（地图幂等 set 合入、四道门禁全过）
> **本文件只做索引与归档前检查，不复制任何正文**——同一事实只留一份，避免出现第二个 `help-center.md` 那样的双份漂移。

## 1 八条缺陷 · 状态与权威出处

| # | 缺陷（用户人工实测提出） | 状态 | 权威文档 | 证据/判据锚点 |
|---|---|---|---|---|
| 1 | 罗盘红色十字经线 | **已验** | `docs/fidelity/m6/reverse-stage1.md`、`docs/qa/m6-stage2-verify.md` | Stage 2 判词 |
| 2 | 地图拖动方向相反 | **已验** | `docs/qa/m6-drag-direction.md` | 符号关系：手指 (dx,dy) 与内容 (dmx,dmy) 同向=正确；四向实测 + z12/z18 复测，原版同机对照 |
| 3 | 当前位置 / 屏幕中心两个符号 | **未取证（挂起）** | `docs/fidelity/m6/reverse-stage1.md` | 原版 web 层 `userLocation`/`locationMarker`/`geolocate` 零命中；**不自行绘制** |
| 4 | 四页 1:1 排版 | **已验** | `docs/fidelity/m6/client-four-pages-diff.md`、`docs/qa/m6-pages-verify.md` | 六项判据全绿（标题 Δ0、返回 115、投诉 352/532、账户 339/991、我的墨迹中心 Δ0.5） |
| 5 | 帮助中心带图操作文档 | **已验** | `docs/product/help-center.md`（唯一源）、`app/android/app/src/main/assets/lc-help/index.html`（生成物）、`docs/fidelity/m6-help-proofread.md`（我的校对结论） | 13 节 / 29 图 / 门禁 `exit=0` / 可达 + 飞行模式离线可读（审核验实测） |
| 6 | 指南针按钮语义 = 回正北 | **已验** | `docs/fidelity/m6/reverse-stage1.md` | 「罗盘复位」= 立即回正北、清锁定/跟随、3D 退 2D、不动缩放与中心 |
| 7 | 罗盘贴地效果 | **未实现** | `docs/fidelity/m6/map-canvas-migration.md` | 需把盘面搬进 `map.html` canvas 作 DEM 纹理；本轮未动工，**不得写「已修复」** |
| 8 | 右侧 3D 按钮功能 | **已验** | `docs/qa/m6-is3d-verify.md`、`docs/fidelity/m6/map-stage2-3d.md` | T1/T2/T3：重载保持、冷启回 2D、无过渡动画 |

## 2 其它交付物索引

| 类别 | 文档 | 说明 |
|---|---|---|
| 计划 | `docs/fidelity/m6-plan.md` | 八条逐条判据、分工、时间戳要求 |
| 阶段复核 | `docs/fidelity/m6/verify-four-pages-client.md`、`map-stage2-verify.md`、`map-side-anchors.md` | 客户端/地图各阶段复核 |
| 审计 | `docs/product/m6-audit-close-list.md` | **关账唯一认的清单**：两批 22 条 = 真 21 / 误 0 / 证据不足 1；A 类根因（重放不全 + 缺幂等 set）不拆成 5 条 |
| 竞品 | `docs/product/market-scan.md` v0.3 | 应用宝 + App Store 中国区（2026-11-05 抓取）；**方法局限已在文首声明**（聚合评分、未逐台实测），不得当结论引用 |
| 帮助中心图清单 | `docs/product/help-figures.md`、`help-center-internal.md` | 29 张唯一清单 + 内部材料（作者行/变更记录/删条说明已物理拆出发布物） |
| 证据规范 | `docs/fidelity/evidence-lint.md` | 内容有效性检查（xml parse/空树/shell 报错、png 空屏/尺寸）、具名例外清单 |
| 门禁 | `a09e076` 上四道 | `SMOKE: PASS`（六门）、`COPY: PASS`、`ASSETS: PASS`（103/103/103）、`FIDELITY: PASS`（exit=0、27/65 已比、checksheet_id 29/29） |

## 3 归档前检查（`evidence-lint.py`，2026-11-08）

### 3.1 `docs/fidelity`（develop 快照）

```
ALLOWLIST: 1 entry
ALLOWED xml docs/fidelity/m3/help-offline.xml  （47 B，shell 报错产物；具名豁免、文件保留作审计痕迹）
EVIDENCE-LINT: scanned xml=155 png=130 other=136  fail=0 warn=0 allowed=1   exit=0
```
**结论：仓库内证据目录干净**，唯一异常就是已登记的 `help-offline.xml`。

### 3.2 本轮新采证据（`E:\APPDetector\reports\`，24 个 `M6-*` 目录）

| 发现 | 数量 | 性质 | 处理建议 |
|---|---|---|---|
| **`M6-stage2/drag/*.png` 全部不可解码** | **22**（该目录 png 总数 27） | **真垃圾证据**：PNG 签名首字节 `0x89` 被替换成 UTF-8 替换符 `EF BF BD`（文件头 `efbfbd504e470d0a`），是二进制经文本转码的典型损坏；文件本身 2.5–3.8 MB，不是 0 字节 | **标作废或删除**；该批不是第 2 条结论的依据（结论用 `getState` 数值），但目录名会让人误以为可用 |
| `docs/fidelity/assets/**/*.png` 实为 WebP | 19 | **扩展名误命名**（magic `RIFF`），内容完好、可解码 | 改扩展名为 `.webp` 或加注，避免「png 打不开」误判 |
| 其余 23 个目录 | — | `fail=0 warn=0` | 可按现状归档 |

> 这条正是总监要的「第二个 `help-offline.xml`」检查：**它存在，但不在 `docs/fidelity`，而在 `reports/M6-stage2/drag`**——所以归档口径应以 `docs/fidelity` 为准，`reports` 只作过程产物，引用前必须过 lint。

## 4 已知限制与遗留（如实留档，不写成已完成）

1. **第 3 条两个符号未取证** → 挂起，不自行绘制；
2. **第 7 条贴地未实现** → 只记「未实现」，不得写「已修复」；
3. **`fig-08` 帮助图仍是未展开态** → 展开行为有两张实测截图作证据（`proof-rowtap-expanded.png` / `proof-bluetext-expanded.png`），但不入正文；
4. **包体 8.6 MB → 34.7 MB**（帮助图 29 张 + 素材），待优化；
5. **`M6-stage2/drag` 22 张图损坏**（见 §3.2），未清理前不得引用；
6. **VIP 门槛已删**（我方无 VIP 体系，属 DI，不判 1:1 差异）；账户页原版独有 6 行、`is3D` 不落 prefs 等 DI 项见 `m6-audit-close-list.md`。

## 5 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-08 | 建索引：八条状态与权威出处、交付物索引、归档前 lint（fidelity `fail=0/allowed=1`；reports 发现 22 张损坏 png + 19 个 WebP 误命名）、已知限制 6 条 |
