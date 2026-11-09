# M7 关账摘要（补欠账 → 清质量债 → 竞品 P0）

- 作者：LC审成文｜日期：**2026-11-09**｜状态：**v1.0 待 LC审核验 逐条签名**
- 唯一基线：`develop` = **`fa97c96`**（`Merge commit '8aa69d1' into develop`）；`app` 树 **`0fa8a2dc41a96b517a83d3cc7bfe3965870782a0`**
- 优先规则：本文件所有数**以 `fa97c96` 实测为准**；凡引自旧树的数，逐条标注「树 `xxxxxxx`」。旧数一律不当冻结基线。
- 上游：用户 2026-11-08 报的 8 项人工实测缺陷 + 竞品 P0 两项（KML 导入导出、离线瓦片缓存）
- 判据来源：`docs/fidelity/m6-close-summary.md` §1、`docs/product/m7-b-round-spec.md`（v1.9）、`docs/fidelity/m7/symbols-stage1.md`、`docs/qa/help-figures-audit.md`
- 本文件只做汇总与索引，**不改写任何条目的技术判定**；判定归技术总监，实测签名归 LC审核验。

---

## 1. 基线身份（可直接复查）

| 项 | 值 | 复查命令 |
|---|---|---|
| develop tip | `fa97c96` | `git log -1 --format='%h %s' fa97c96` |
| app 子树 | `0fa8a2dc41a96b517a83d3cc7bfe3965870782a0` | `git rev-parse fa97c96:app` |
| `map.html` blob | `126060b4fd83b6d015b9427b460008ed2c0b6158` | `git rev-parse fa97c96:app/android/app/src/main/assets/map.html` |
| `help-center.md` blob | `2eaddc47738ed745178b0e1644e9e662467d76ca` | `git rev-parse fa97c96:docs/product/help-center.md` |
| `index.html` 头部 `@` | `2eaddc47738ed745178b0e1644e9e662467d76ca` | 与上一行**逐字符相同** |
| `lc-help` webp 数 | **28**（`fig-32` 已撤下） | `git ls-tree -r --name-only fa97c96 -- app/android/app/src/main/assets/lc-help \| grep -c '\.webp$'` |
| `index.html` 内 `fig-32` | **0** | 同上 grep `fig-32` |
| 最终 APK | **`12,349,187 B`** / `sha256:841311ef88e5357e54672f1e99c4b9eaaa7f9ff7d9eacd5e0e6cf7b30bceff1e` | 见 §7 一致性校验第 3 条 |

---

## 2. 三轮结论

### 2.1 A 轮 · 补欠账（用户第 3 条「两个符号」、第 7 条「罗盘贴地」）

**取证结论（`docs/fidelity/m7/symbols-stage1.md`，[原版 1.6.1 对照]）**

| 符号 | 取证结果 | 量化（原版 1228×2700，density 3.375） |
|---|---|---|
| 屏幕中心符号 | **原版有**（55 张原版截图逐张检测 + 5× 放大目视） | 中心红点 **13×13** `#FF5163`；红线圆心 **(614,1350)** = 屏幕正中；四段蓝线 **≈57×7** `#009DFF`；整体 bbox **114×114** |
| 当前位置符号 | **原版有**（定位成功后出现；原「原版不可得」结论在同文件 §2.4 已作废） | 素材精确色匹配，连通域判据 **= 2**（圆 + 圈外独立小三角，不是水滴） |

- 素材 `assets/webapp/assets/imgs/cross.png` 虽形状一致但**不可直接复用**：颜色 `#6488FC` vs 实测 `#009DFF`、尺寸 52×52 vs 114×114（需 2.19×），且主图 bundle 对 `cross.png` 引用 **0 次**。
- **实现侧现状（我自己的取数）**：`fa97c96` 的 `map.html` 里 `009DFF`、`FF5163`、`centerCross`、`center-cross` 命中均为 **0**；`userLocation`/`locationMarker`/`geolocate` 命中均为 **0**。⇒ 两个符号**取证完成、实现未落**（另见 §3 第 3 条的终态归属，以 LC产品 终态表为准）。

**第 7 条贴地**：`map.html` 里 `drape` 共 12 处，逐行核对全部是 **3D 地形贴面层**（`:663/1735/1813/1983/3551/3562/3565/4014/4029/4064` 等，语义为「3D 贴面纹理」），**没有一处是罗盘盘面贴地**。⇒ 与 `m6-close-summary.md:21` 一致：按钮与文案已 1:1，**效果未落地**。迁移方案（A 路线）见 `docs/fidelity/m7/ground-drape-migration.md`。

### 2.2 C 轮 · 清质量债

| 门禁项 | 结果 | 数 |
|---|---|---|
| 审计 B 类 6 条 | 关闭 | 逐条见 `docs/fidelity/m6-close-summary.md` §1 与 M7 各笔提交 |
| 死代码 6 条 | grep 计数 0 | — |
| `MainActivity` 换行 | 归一 LF | CR=0（C 轮复核） |
| 包体 | 34.7 MB → **12,349,187 B ≈ 11.8 MB** | 见 §1 |
| 帮助中心 29 图 | 转 WebP + 6 张裁切 + 撤 `fig-32` + 删 `res/` 死资源 `lc_compass_vip` | 现 **28** 张，`fig-32` 记 **CONFIRMED-MISSING** |
| 四道门禁 | 全 PASS | 见 §4 参数行 |

### 2.3 B 轮 · 竞品 P0

| 项 | 结论 | 关键数 | 证据树 |
|---|---|---|---|
| ① KML 导出→导入往返 | **PASS** | `base` 12 点 vs `after[0:12]`/`after[12:24]` 各 24 个值，`max_abs_diff = 0.000e+00`（判据 ≤1e-6） | **`fa97c96`**（`kml-fa97c96/`） |
| ② 断网后可看已缓存区 | **PASS** | `hit=24`、`shown=24`（`shown/hit=1`）、离线期 `netOk` 零增长 | 树 **`96be059`**（`offline-96be059/`）⚠ 见 §7 第 1 条 |
| ② 降级提示 | **PASS（三态）** | 未缓存文案、过期文案、二者并存文案由 `LCTileCachePolicy` 纯函数给出并有三态单测 | 树 `fa97c96`（政策层） |
| ② banner 底边避让 | **PASS** | `rect bottom=760.0000` = `innerHeight 800 − safeAreaBottom 32 − 8` | 树 **`96be059`**；`fa97c96` 另有 `8aa69d1` 时序修复 ⚠ 见 §7 第 1 条 |
| ③ 天地图离线缓存条款 | **两段式**：门户《服务条款》《版权声明》**已查证·未涉及**（缓存/离线/下载各 0 命中）；API 控制台**未查证**（DNS 不解析） | **不写「条款允许」**，见 `docs/fidelity/m7/tdt-offline-terms.md`、`docs/fidelity/m7/offline-cache-orig.md` | `fa97c96` |
| ④ 四道门禁 | **PASS** | 见 §4 | `fa97c96` |

---

## 3. 八条终态引用

> **规范文本以 `docs/product/` 下 LC产品 的八条终态表为准**（本文件不重写判定）。下表只做索引：给出每条在**取证侧**的出处，以及我在 `fa97c96` 上能自查到的实现侧事实。

| # | 用户要求 | 出处（取证/判定） | 我在 `fa97c96` 上的自查 |
|---|---|---|---|
| 1 | 罗盘贯穿屏幕的红色十字经线 | `m6-close-summary.md:15`（已验；原版 CSS `2px`/`#FF0000`，逆向 `2597222`） | — |
| 2 | 拖动方向与手指同向 | `m6-close-summary.md:16`（已验；2a 8/8、2b 三档角误差 0°、负向对照 10/10 FAIL 证明判据能抓缺陷） | — |
| 3 | 当前位置 / 屏幕中心两个符号 | `docs/fidelity/m7/symbols-stage1.md` §一、§2.4（**均为原版有**，已量化） | `map.html` 无 `009DFF`/`FF5163`/`centerCross`；实现未落 |
| 4 | 四页 1:1 排版 | `m6-close-summary.md:18`（已验：三页标题 Δ0、返回 y115 Δ0、投诉 352/532 Δ−1 等） | 账户页见 §5 遗留 1（BLOCKED）；关于/联系见 `M7-b/about-fa97c96/`、`contact-fa97c96/` |
| 5 | 带插图的帮助中心 | `m6-close-summary.md:19` + `docs/qa/help-figures-audit.md`（17 正确 / 6 未裁切 / 6 错；`fig-32` CONFIRMED-MISSING） | 28 图、引用↔文件双向差集为空、`fig-32` 0 处 |
| 6 | 指南针按钮 = 地图立即回正北 | `m6-close-summary.md:20`（已验；`3d\|45\|106.7221\|12` → `2d\|0\|106.7221\|12\|0`） | — |
| 7 | 罗盘贴地效果 | `m6-close-summary.md:21`（**未实现**：按钮文案 1:1，缺四角算法） | `drape` 12 处全为 3D 贴面，非盘面贴地 |
| 8 | 右侧 3D 按钮功能 | `m6-close-summary.md:22`（已验 T1/T2；T3「无过渡动画」证据不可得——设备无 `screenrecord`） | — |

**计数（按终态三分类）**：已验 **5**（第 1/2/6/8 + 第 5 条按撤图后状态计）；未实现 + 原因 **3**（第 3 条「取证完成、实现未落」、第 7 条「缺四角算法」、第 4 条含账户页 BLOCKED 一项）；原版不可得 **0**（第 3 条原「不可得」结论已被 §2.4 实测推翻）。
**⚠ 第 3/4 条的最终分类以 LC产品 终态表为准**——第 4 条若按「四页」整体判，账户页 BLOCKED 会把它从「已验」拉成「BLOCKED」，两种切法计数不同，此处两说并列，不代产品拍板。

---

## 4. 门禁与 SMOKE（原始输出 + 参数行）

四条命令均在 **`fa97c96` 干净树**（`git status --porcelain` 输出 0 行）上复跑，我本人执行，输出与 `reports/M7-final-fa97c96/gates/` 存档一致。

```
$ git -C wt/lc-close-audit status --porcelain | Measure-Object -Line   →  0

① python tools/fidelity/assets.py verify
   [PASS] deps: 素材 15/15 齐；手工判据 4 项（A-09, A-10, A-11, A-12）
   [PASS] verify: 103 file(s), 103 manifest entr(ies), 103 provenance row(s); sha256 + provenance + APK source all match
   exit=0

② python tools/fidelity/evidence-lint.py \
     --root app/android/app/src/main/assets/lc-help \
     --expect-size 1228x2700 \
     --expect-size-map tools/fidelity/evidence-size-map.json
   SIZE-MAP: 6 rule(s) from …/tools/fidelity/evidence-size-map.json
   ALLOWLIST: 3 entry(ies) from …/tools/fidelity/evidence-lint-allow.json
   DUP: files=28 unique=28 duplicated=0 redundant=0
   EVIDENCE-LINT: scanned xml=0 png=28 other=1 fail=0 warn=0 allowed=0
   exit=0

③ python tools/fidelity/evidence-lint.py --root docs/fidelity
   ALLOWLIST: 3 entry(ies) from …
   DUP: files=138 unique=136 duplicated=4 redundant=2
   EVIDENCE-LINT: scanned xml=155 png=138 other=146 fail=0 warn=0 allowed=3
   exit=0

④ python tools/fidelity/evidence-lint.py \
     --generated-html app/android/app/src/main/assets/lc-help/index.html \
     --source docs/product/help-center.md
   ROOT: SKIP evidence scan (only --generated-html/--source given; pass --root to lint evidence too)
   EVIDENCE-LINT: scanned xml=0 png=0 other=0 fail=0 warn=0 allowed=0
   exit=0
   头部 @ 2eaddc47738ed745178b0e1644e9e662467d76ca  ==  md_blob 2eaddc47…（逐字符相同）
```

**SMOKE 六门（`smoke-20261109-212940`，树 `fa97c96`）**

```
copy=PASS  secret=PASS  desktop=PASS  numeric=PASS  mech_src=PASS  android=PASS
device_skipped=True   overall=PASS
desktop: cargo_exit=0 / 截图 1100×780 (266,376 B) / mechanics_values=19 / mechanics_estimated=0 / backend=docker
numeric: tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
android: build only（device skipped）→ app-debug.apk 12,349,187 B
```

- **desktop 门本轮已真跑通**（代理恢复后 docker 拉取 `rust:1.90-slim` 成功），上一轮「docker 拉不到镜像」的 BLOCKED **消除**；环境阻塞只剩**设备侧**。

---

## 5. 遗留（四条，逐条写清）

### 遗留 1 · 账户页 BLOCKED（三要素齐）

| 要素 | 内容 |
|---|---|
| **原因** | 复验流程执行 `pm clear` 清理应用数据后，登录态被清空；原版账户页登录后才显示昵称/账户 ID/绑定等行，未登录态页面结构与已登录态不同，无法与已登录态基线逐行对位 |
| **缺什么证据** | 一次成功的登录路径（可用测试账号），使账户页回到已登录态后再采 dump + 截图；或明确授权的免 `pm clear` 复验流程 |
| **下轮怎么复测** | 装机后先进账户页确认登录态 → 不执行 `pm clear` → dump + 截图 → 按 `compare_page.py` 的 TEXT/BOUNDS/STEP/CARRIER/THEME 五判据跑 → 与 `docs/fidelity/m3/account-setting.*` 基线比；若仍无登录态，须先取得测试账号 |
| 证据 | 树 `96be059`：`reports/M6-pages-96be059/pages/compare.log` 中 `account` 段 `VERDICT=FAIL`（`STEP=FAIL`，`step_y_median=87.5 expect=163.0±2.0`）；该 FAIL 属登录态缺失所致，非排版缺陷 |

> **不是终态**：BLOCKED 必须在下一轮具名复测，不得并入「已验」。

### 遗留 2 · about 底部两行整体偏低 3px（已定位、已判 PASS、下轮修）

- **三条判据 PASS**：两行间距差 **0**、间隙 **37**、不叠字。
- **已知偏差**：整体落点比原版低 **3px**；`inkOff` 实测 **10 / 5**（预估 12 / 7）。
- **成因**：`inkTopOffset()` 用 `(控件高 − 文本块高)/2` 自行重算垂直居中，未计入 `includeFontPadding`，两行同差 3px（属系统性，不是随机误差）。
- **修法**：改用 `getLayout().getLineBaseline(0)` 取布局后真实基线，`inkTop = baseline + r.top`；`inkOff` 需移到 measure/layout 之后计算。
- **不阻塞本轮**：改 `M3PageActivity` 会改 `app` 树、使 `fa97c96` 上的 ①④ 与六门作废。
- 证据：`reports/M7-b/about-fa97c96/`（about.png / about.xml）；`reports/M6-pages-96be059/pages/compare.log` 的 `about` 段为**旧树**读数（`df6130a` 修墨迹对位之前），**不得引为终态**。

### 遗留 3 · 离线瓦片缓存是**新增能力**，不写「与原版 1:1」

- **逆向结论**：原版**没有**「浏览即缓存」能力（`docs/fidelity/m7/offline-cache-orig.md`）。
- 因此本条台账只能写「**我方新增能力**」，**禁止**写成「与原版一致 / 1:1」。
- 默认态：`capBytes 200×1024×1024`、`ttlMs 7 天`、`enabledSources {}`（默认全关）；删除只走容量 LRU，**7 天只标陈旧、不删数据**。
- 文案为两条互斥分支：未缓存 / 已缓存但可能过期；`stale` 与 `miss` 并存时追加「其中 N 块数据可能已过期」（`185051c`）。

### 遗留 4 · 天地图条款：两段式结论

| 段落 | 结论 | 依据 |
|---|---|---|
| 门户《服务条款》《版权声明》 | **已查证 · 未涉及** | 正文写死在 `main.a15946b3.js`（1,135,375 B），对「缓存 / 离线 / 下载」检索**各 0 命中**；`/about/service` 返回 200 空壳 |
| API 控制台条款 | **未查证** | 域名 DNS 不解析，取不到正文 |
| 落地要求 | **不许写「条款允许离线缓存」** | 「未明文允许」≠「明文禁止」；本项目自用、非商用、不上架 |
| 三条验收项 | ① 默认全关（`sources=[]`、`items=0`）② 角注保留「数据来源：天地图」+ 仅在**有真号**时追加审图号 ③ 陈旧提示 | 版权声明「应注明来源」；条款二「对数据及时性不作担保」 |

- **`assets.py verify --apk` 指向原版 1.6.1**，所以 103/103/103 的含义是「我们从原版导出的素材库与原版一致」，**不等于**我们构建的 APK 内容一致——这两句在台账里**分两行写，不合并**。
- 审图号：产品 5 条路径 **0 命中**；界面与代码默认值均**不允许**出现「待核对 / 未确认 / 不适用」，**无真号就不显示该子句**。

---

## 6. 证据索引

| 目录 | 内容 | 备注 |
|---|---|---|
| `E:\APPDetector\reports\M7-final-fa97c96\` | **18 个文件**：`gates/gate1-assets.txt`、`gate2-lchelp.txt`、`gate3-fidelity.txt`、`gate4-help-sha.txt`；`smoke.log`；`smoke-20261109-212940/`（copy/secret/numeric/mech-src 日志、`desktop/`（cargo-build.log、docker-image.log、screenshot.png 266,376 B、result.txt）、`android/`（app-debug.apk 12,349,187 B、gradle 日志、result.txt）、`summary.txt`） | 树 `fa97c96`，四道门禁 + 六门 smoke 原件 |
| `E:\APPDetector\wt\lcs-verify-stage4\reports\M7-b\` | **62 个文件**：`kml-fa97c96/`（base.kml、after.kml）、`kml-96be059/`、`offline-96be059/`、`about-fa97c96/`、`contact-fa97c96/`、`banner-8604eac/`、`banner-a8bc74e/`、`install-fa97c96/`、`gate-4c1c406/`、`REPORT.md` | ⚠ **实际路径不在 `reports\M7-b\`**，见 §7 第 2 条 |
| `E:\APPDetector\reports\M6-pages-96be059\` | **20 个文件**：`pages/`（四页 png+xml、`compare.log`、`run.log`）、`install-s1/`、`build/`、`app-debug-96be059.apk` | 树 `96be059`，**账户页 BLOCKED 的出处**；其 `VERDICT=FAIL` 均属旧树 |
| `docs/fidelity/m7/` | `symbols-stage1.md`（两个符号取证）、`ground-drape-migration.md`（贴地迁移方案）、`offline-cache-orig.md`、`tdt-offline-terms.md`、`screens/`（13 张取证图） | 树 `fa97c96` |
| `docs/qa/help-figures-audit.md` | 29 条图注↔画面核对表（17/6/6，`fig-32` CONFIRMED-MISSING） | 本轮由图核对产出 |
| `docs/qa/dup-pairs-verdict.md` | `docs/fidelity` 两对重复图逐对判定（均走具名例外） | 已由 LC审核验 终验 PASS |

---

## 7. 一致性校验：**三处必须先解决，否则不得签字**

### 第 1 条 · ② 的 `hit=24 / shown=24 / banner 760.0000` **不是 `fa97c96` 的实测数**

```
证据文件只存在于 M7-b/REPORT.md 与其 offline-96be059/ 目录，该 REPORT 首行自述：
  「develop 顶点：96be059（app 树 b273a63c）」

实测三棵树的 map.html 各不相同：
  96be059  map.html = 29c76f51283fbd9cecfc2263cf86e0c88b576140
  4c1c406  map.html = 08516700a932c66a8d55a74aeb70336fd0c87d52
  fa97c96  map.html = 126060b4fd83b6d015b9427b460008ed2c0b6158
```

⇒ 这三个数取自 `96be059`，而 `map.html` 在 `fa97c96` 上是**另一个 blob**（`8aa69d1` 还改了 `setSafeArea` 时序）。**按 goal「以 `fa97c96` 实测为准」这条，② 的断网可看与 banner 避让必须在最终树上补测**，否则关账表等于拿旧树的数当终态。
**请 LC审核验 在 `fa97c96` 上补一次断网上机读数**（`hit`/`shown`/`netOk`/`banner.bottom`），或由总监裁定「`map.html` 差异不影响该判据」并写明理由。

### 第 2 条 · `reports\M7-b\` 路径不存在

任务书写的证据目录 `reports\M7-b\` 在本机**不存在**；实际位置是 `E:\APPDetector\wt\lcs-verify-stage4\reports\M7-b\`（62 个文件）。关账表引用证据路径必须写**实际可打开**的那条，否则以后没人能复算。

### 第 3 条 · 包体字节数两个值不一致，且都称「终树」

```
reports/M7-final-fa97c96/smoke-…/android/app-debug.apk  实测 12,349,187 B
  sha256 = 841311ef88e5357e54672f1e99c4b9eaaa7f9ff7d9eacd5e0e6cf7b30bceff1e
LC测试 报 12,349,187 B；技术总监 报 12,349,203 B（差 16 B）
```
⇒ 差 16 字节说明是**两次不同构建**（很可能一次 clean、一次增量）。按总监自己定的规矩「冻结点包 sha + 字节数为唯一基线」，应当只保留一个值。**本文件采用证据文件实测的 `12,349,187 B`**，请总监确认或给出 `12,349,203 B` 那次的构建日志/sha，二者取一。

### 附 · 第 4 条（工具侧，不影响判定）：② 的「参数行」在原始输出里没有

```
gate2-lchelp.txt 全文只有 4 行，不含命令行；
我实测对照（同一棵 fa97c96 干净树）：
  带 --expect-size 1228x2700 + --expect-size-map  →  SIZE-MAP 6 rules / fail=0 / exit=0
  只带 --expect-size-map（不带 --expect-size）      →  SIZE-MAP 6 rules / fail=0 / exit=0   ← 输出完全相同
  两个尺寸参数都不带                                →  fail=0 / exit=0（完全不做尺寸比较）  ← 同上
  负向对照：--expect-size 999x999 + map             →  fail=22 / exit=1   ← 证明该参数确实生效
```
⇒ 「② 带了两个参数」这件事，**从存档输出本身无法证明**（三种跑法输出一致）；能证明的是**负向对照能让它变红**，即参数有效。建议 LC测试 在门禁输出里固定回显命令行（或 `SIZE-MAP` 行写明 `fallback=1228x2700`），否则下次又会重演「参数给了没执行」那类假绿。

---

## 8. 未覆盖范围

- **未做**：`fa97c96` 上的真机读数（KML 往返除外）——设备不在我手上，`hit/shown/netOk/banner` 一律未由我实测，仅引用他人证据并标注树。
- **未做**：账户页登录态复验（无测试账号、无设备）。
- **未改**：`help-center.md`、`docs/review/`、`docs/qa/baseline-android/` 一律未触碰；本文件为新增文件。
- **未核**：B 轮缓存七个数在 `fa97c96` 的构造性恒等式实测（`missAttemptsTotal = missKeyedAttempts + missNoKey + missUnsupported`）——代码层由 LC地图/LC审核验 静态核过，**真机交叉验未做**。
- **未核**：第 8 条 T3「无过渡动画」——设备无 `screenrecord`，与 `A-09 动效录屏` 同口径，记「证据不可得」，**不写成未实现**。

---

## 9. 时间戳

- 开工 **21:34** / 收工 **21:5x**（Asia/Shanghai）｜返工轮次 **0**
- 本文件不含二进制、不含构建产物；所有 hash 均以 `blob:` / `sha256:` 前缀标注。
