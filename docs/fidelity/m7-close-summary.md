# M7 关账摘要（补欠账 → 清质量债 → 竞品 P0）

- 作者：LC审成文｜日期：**2026-11-09**｜版本：**v1.3**（v1.0 `fa97c96`／v1.1 `b47fcdb`／v1.2 `3ffe142`，均已由本版取代）｜状态：**待 LC审核验 逐条签名**
- 唯一基线：`develop` = **`953fb8b`**（`Merge commit '5acfaf4' into develop`）；`app` 树 **`d1a186d29ac563226622d6bc053011e6f9370a8c`**（自 `fa97c96` 起文档改动不计入 `app`）
- 优先规则：本文件所有数**以最终树实测为准**；凡引自旧树的数，逐条标注「树 `xxxxxxx` + 时点」。旧数一律不当冻结基线。
- **树变更说明**：`fa97c96` → `b47fcdb` 只因两笔合并（`6acf66d` complaint 墨迹下移 4px、`32c69d8` 终态表），`app` 树由 `0fa8a2dc` 变为 `d1a186d`。**`map.html` blob 两棵树完全相同（`126060b4`）**，`MainActivity`/`KmlIo` 亦未变动。
- 上游：用户 2026-11-08 报的 8 项人工实测缺陷 + 竞品 P0 两项（KML 导入导出、离线瓦片缓存）
- 判据来源：`docs/fidelity/m6-close-summary.md` §1、`docs/product/m7-b-round-spec.md`（v1.9）、`docs/fidelity/m7/symbols-stage1.md`、`docs/qa/help-figures-audit.md`
- 本文件只做汇总与索引，**不改写任何条目的技术判定**；判定归技术总监，实测签名归 LC审核验。

---

## 1. 基线身份（可直接复查）

| 项 | 值 | 复查命令 |
|---|---|---|
| develop tip | `953fb8b` | `git log -1 --format='%h %s' 953fb8b` |
| app 子树 | `d1a186d29ac563226622d6bc053011e6f9370a8c`（`b47fcdb` / `3ffe142` / `953fb8b` 三棵同） | `git rev-parse 953fb8b:app` |
| `map.html` blob | `126060b4fd83b6d015b9427b460008ed2c0b6158`（与 `fa97c96`/`b47fcdb` 同） | `git rev-parse 3ffe142:app/android/app/src/main/assets/map.html` |
| `MainActivity` blob | `cda01f27ff`（真机读数有效性前提） | `git rev-parse 3ffe142:app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` |
| `KmlIo` blob | `b6215e7b36`（同上） | 同上，路径换 `KmlIo.java` |
| `help-center.md` blob | `2eaddc47738ed745178b0e1644e9e662467d76ca` | `git rev-parse 3ffe142:docs/product/help-center.md` |
| `index.html` 头部 `@` | `2eaddc47738ed745178b0e1644e9e662467d76ca` | 与上一行**逐字符相同** |
| `lc-help` webp 数 | **28**（`fig-32` 已撤下） | `git ls-tree -r --name-only 3ffe142 -- app/android/app/src/main/assets/lc-help \| grep -c '\.webp$'` |
| `index.html` 内 `fig-32` | **0** | 同上 grep `fig-32` |
| **包体基线（唯一）** | **`b47fcdb` 干净构建 / `app` 树 `d1a186d` / `12,349,195 B` / `sha256:f7e362649af19f938d25132f8b2824321084f5dcaa5844be00569398401e89eb`** | 见 §4、§7 第 3 条 |
| 同树重复构建（**仅说明波动**） | 同树三份为 `12,349,203` / `12,349,187` / `12,349,195 B`；**判据认 `blob:` 不认 sha** | — |
| ~~`12,349,219 B`~~ | 客户端自测包，**已废弃**，不作基线 | — |

---

## 2. 三轮结论

### 2.1 A 轮 · 补欠账（用户第 3 条「两个符号」、第 7 条「罗盘贴地」）

**取证结论（`docs/fidelity/m7/symbols-stage1.md`，[原版 1.6.1 对照]）**

| 符号 | 取证结果 | 量化（原版 1228×2700，density 3.375） |
|---|---|---|
| 屏幕中心符号 | **原版有**（55 张原版截图逐张检测 + 5× 放大目视） | 中心红点 **13×13** `#FF5163`；红线圆心 **(614,1350)** = 屏幕正中；四段蓝线 **≈57×7** `#009DFF`；整体 bbox **114×114** |
| 当前位置符号 | **原版有**（定位成功后出现；原「原版不可得」结论在同文件 §2.4 已作废） | 素材精确色匹配，连通域判据 **= 2**（圆 + 圈外独立小三角，不是水滴） |

- 素材 `assets/webapp/assets/imgs/cross.png` 虽形状一致但**不可直接复用**：颜色 `#6488FC` vs 实测 `#009DFF`、尺寸 52×52 vs 114×114（需 2.19×），且主图 bundle 对 `cross.png` 引用 **0 次**。
- **实现侧现状（已更正，见 §5.0 更正记录）**：两个符号**在原生层已实现**，不是页面层。
  - `app/android/app/src/main/java/com/luopan/fengshui/MapCenterSymbolView.java`（79 行）：`:47 linePaint.setColor(Color.rgb(0, 157, 255)); // #009DFF`、`:50 dotPaint.setColor(Color.rgb(255, 81, 99)); // #FF5163`，类头注释 `:14-15` 量值 13×13 / 圆心 (614,1350) / 蓝线 ≈57×7，与 §2.1 取证值一致。
  - `app/android/app/src/main/java/com/luopan/fengshui/LocationPointerView.java`（134 行）。
  - `MainActivity.java:35/37` 声明、`:679 centerSymbolView = new MapCenterSymbolView(this);`、`:688 locPointerView = new LocationPointerView(this);` 挂载。
  - 复验记录：`23c9fc9` 屏心符号 PASS、`1656e31` 定位点复验 PASS（引自 LC产品 终态表，本文件未复跑真机）。
  - **我的原判错在哪**：v1.0 只检索 `map.html`（页面层）就下「实现未落」结论，`009DFF`/`FF5163` 在 **Java 层**而非 web 层。这是「检索面不全当成了不存在」，与 goal 里「无证据不实现」同类的反向错误——**无证据也不等于不存在**，必须把检索范围写出来。

**第 7 条贴地**：`b47fcdb` 的 `map.html`（blob `126060b4`，与 `fa97c96` 同）里 `drape` 共 12 处，逐行核对全部是 **3D 地形贴面层**（`:663/1735/1813/1983/3551/3562/3565/4014/4029/4064` 等，语义为「3D 贴面纹理」），**没有一处是罗盘盘面贴地**。⇒ 与 `m6-close-summary.md:21` 一致：按钮与文案已 1:1，**效果未落地**。迁移方案（A 路线）见 `docs/fidelity/m7/ground-drape-migration.md`；终态表 §2.7 另给出四条代码依据（`setGround` 只切 3D + pitch 60°、`groundRenderer` 默认 `'overlay'`、canvas 档只能经调试钩子切、无产品按钮路径）。

### 2.2 C 轮 · 清质量债

| 门禁项 | 结果 | 数 |
|---|---|---|
| 审计 B 类 6 条 | 关闭 | 逐条见 `docs/fidelity/m6-close-summary.md` §1 与 M7 各笔提交 |
| 死代码 6 条 | grep 计数 0 | — |
| `MainActivity` 换行 | 归一 LF | CR=0（C 轮复核） |
| 包体 | 34.7 MB → **12,349,195 B ≈ 11.8 MB**（`b47fcdb` 干净构建，`app` 树 `d1a186d`） | 见 §1 |
| 帮助中心 29 图 | 转 WebP + 6 张裁切 + 撤 `fig-32` + 删 `res/` 死资源 `lc_compass_vip` | 现 **28** 张，`fig-32` 记 **CONFIRMED-MISSING** |
| 四道门禁 | 全 PASS | 见 §4 参数行 |

### 2.3 B 轮 · 竞品 P0

| 项 | 结论 | 关键数 | 证据树 |
|---|---|---|---|
| ① KML 导出→导入往返 | **PASS** | `base` 12 点 vs `after[0:12]`/`after[12:24]` 各 24 个值，`max_abs_diff = 0.000e+00`（判据 ≤1e-6） | **`fa97c96`**（`kml-fa97c96/`） |
| ② 断网后可看已缓存区 | **PASS** | **最终树**（`map.html` blob `126060b4`）实测：`hit=60`、`shown=60`、`miss=0`、`net=60`、`netOk=0`、`online=false` ⇒ `shown==hit` 且离线期 `netOk` 冻结 | 最终树（LC审核验 2026-11-09 实测，时点 22:0x）⚠ 旧树 `96be059` 读数 `hit=24/shown=24` 已标时点 |
| ② 降级提示 | **PASS（三态）** | 未缓存文案、过期文案、二者并存文案由 `LCTileCachePolicy` 纯函数给出并有三态单测 | 树 `fa97c96`（政策层） |
| ② banner 底边避让 | **PASS** | `rect bottom=760.0000` = `innerHeight 800 − safeAreaBottom 32 − 8` | **最终树同 blob `126060b4`**（`fa97c96` 上测得的 T0/T1/T2 在 `b47fcdb`/`3ffe142` 上同 blob 有效）⚠ 见 §7 第 1 条 |
| ③ 天地图离线缓存条款 | **两段式**：门户《服务条款》《版权声明》**已查证·未涉及**（缓存/离线/下载各 0 命中）；API 控制台**未查证**（DNS 不解析） | **不写「条款允许」**，见 `docs/fidelity/m7/tdt-offline-terms.md`、`docs/fidelity/m7/offline-cache-orig.md` | `fa97c96` |
| ④ 四道门禁 | **PASS** | 见 §4 | **`b47fcdb`** |

---

## 3. 八条终态引用

> **规范文本以 `docs/product/` 下 LC产品 的八条终态表为准**（本文件不重写判定）。下表只做索引：给出每条在**取证侧**的出处，以及我在 `fa97c96` 上能自查到的实现侧事实。

| # | 用户要求 | 出处（取证/判定） | 我在 `fa97c96` 上的自查 |
|---|---|---|---|
| 1 | 罗盘贯穿屏幕的红色十字经线 | `m6-close-summary.md:15`（已验；原版 CSS `2px`/`#FF0000`，逆向 `2597222`） | — |
| 2 | 拖动方向与手指同向 | `m6-close-summary.md:16`（已验；2a 8/8、2b 三档角误差 0°、负向对照 10/10 FAIL 证明判据能抓缺陷） | — |
| 3 | 当前位置 / 屏幕中心两个符号 | `docs/fidelity/m7/symbols-stage1.md` §一、§2.4（**均为原版有**，已量化）+ `docs/product/m7-eight-final-states.md`（`32c69d8`）判 **已验** | 原生层 `MapCenterSymbolView.java:47/50`（`#009DFF`/`#FF5163`）、`LocationPointerView.java`、`MainActivity.java:679/688` 挂载 |
| 4 | 四页 1:1 排版 | `m6-close-summary.md:18` + 终态表（`32c69d8`）；投诉行按**墨迹**判：顶 367/367 差 0、两行间距 181 = 原版 181、不叠字 | 账户页见 §5 遗留 1（BLOCKED）；关于/联系见 `M7-b/about-fa97c96/`、`contact-fa97c96/`；投诉见 `reports/m7-complaint/` |
| 5 | 带插图的帮助中心 | `m6-close-summary.md:19` + `docs/qa/help-figures-audit.md`（17 正确 / 6 未裁切 / 6 错；`fig-32` CONFIRMED-MISSING） | 28 图、引用↔文件双向差集为空、`fig-32` 0 处 |
| 6 | 指南针按钮 = 地图立即回正北 | `m6-close-summary.md:20`（已验；`3d\|45\|106.7221\|12` → `2d\|0\|106.7221\|12\|0`） | — |
| 7 | 罗盘贴地效果 | `m6-close-summary.md:21`（**未实现**：按钮文案 1:1，缺四角算法） | `drape` 12 处全为 3D 贴面，非盘面贴地 |
| 8 | 右侧 3D 按钮功能 | `m6-close-summary.md:22`（已验 T1/T2；T3「无过渡动画」证据不可得——设备无 `screenrecord`） | — |

**计数（按终态三分类，以 `docs/product/m7-eight-final-states.md` @ `32c69d8` 为准）**：**已验 7**（第 1/2/3/4/5/6/8）｜**未实现 + 原因 1**（第 7 条贴地：缺四角算法，方案 5.0 人日，决策 A1/B1 待裁）｜**原版不可得 0**｜**BLOCKED 1 处**（第 4 条内账户页，三要素见 §5 遗留 1）｜**已知偏差 2 项**（about 底部两行整体低 3px；第 8 条 T3「无过渡动画」证据不可得）。

> 投诉行的「节点高 68 vs 79」**已从偏差清单移除**：那是 Compose 语义框与 View 布局框的不可比量，不是字高差（按墨迹实测字高差 1px）。终态表 L20 计数行与 L33 第 4 条已按此改口。

---

## 4. 门禁与 SMOKE（原始输出 + 参数行）

四条命令在 **最终树 `3ffe142`**（`app` 树 `d1a186d`，`git status --porcelain` 输出 **0 行**）上复跑，我本人执行；官方存档同时有两份：`E:\APPDetector\reports\M7-final-b47fcdb\gates\`（LC测试，`b47fcdb`，`app` 树同 `d1a186d`）与 `E:\APPDetector\reports\M7-final-fa97c96\gates\`（旧树，**已作废**）。**四条在 `b47fcdb` / `3ffe142` 上与我复跑一致，exit 全 0。**

```
$ git -C wt/lc-close3 status --porcelain   →  0 行

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
   EVIDENCE-LINT: scanned xml=155 png=138 other=147 fail=0 warn=0 allowed=3
   exit=0
   注：`other=146` 是**本文档入库前**的数；入库后为 **147**（我在 `3ffe142` 与 `953fb8b` 两次实测，均 147）；两者 fail 均 0。
   **更正一处预期**：合产品那份 `docs/product/m7-eight-final-states.md` v1.3（`084edc9`）**不会**把 147 变 148——该文件在 `docs/product/`，不在 `--root docs/fidelity` 的扫描范围内。我在 `953fb8b` 实测仍是 **147**。
   **判据只认 `fail=0` 与 `allowed=3`，`other` 只作参照**——否则会被目录里多一个 `.md` 判成异常。

④ python tools/fidelity/evidence-lint.py \
     --generated-html app/android/app/src/main/assets/lc-help/index.html \
     --source docs/product/help-center.md
   ROOT: SKIP evidence scan (only --generated-html/--source given; pass --root to lint evidence too)
   EVIDENCE-LINT: scanned xml=0 png=0 other=0 fail=0 warn=0 allowed=0
   exit=0
   头部 @ 2eaddc47738ed745178b0e1644e9e662467d76ca  ==  md_blob 2eaddc47…（逐字符相同）
```

**SMOKE 六门（最终树，`summary.txt` 原件：`E:\APPDetector\reports\M7-final-b47fcdb\smoke-20261109-215326\summary.txt`，`app` 树 `d1a186d`）**

```
copy=PASS  secret=PASS  desktop=PASS  numeric=PASS  mech_src=PASS  android=PASS
device_skipped=True   overall=PASS
desktop: cargo_exit=0 / 截图 1100×780 (266,376 B) / mechanics_values=19 / mechanics_estimated=0 / backend=docker
numeric: tests=85 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0
android: build only（device skipped）→ app-debug.apk 12,349,195 B
smoke.log 结尾两行：`overall  : PASS` / `SMOKE: PASS`
```

- **`SMOKE_EXIT=0` 这个串不存在于日志里**（我逐行核过 `smoke.log`）。本文件一律写 **`SMOKE: PASS` / `overall=PASS`**，不写 `SMOKE_EXIT=0`；LC测试 已在最终一轮把退出码写入证据文件。
- **desktop 门已真跑通**（代理恢复后 docker 拉取 `rust:1.90-slim` 成功），「docker 拉不到镜像」的 BLOCKED **消除**；环境阻塞只剩**设备侧**。

---

## 5.0 更正记录（我自己的判错，必须留档）

| 项 | v1.0 的错误说法 | 事实 | 根因 |
|---|---|---|---|
| 第 3 条「两个符号」 | 「两个符号**取证完成、实现未落**」，依据是 `map.html` 里 `009DFF`/`FF5163`/`centerCross` 命中 0 | 两个符号**已在原生层实现**：`MapCenterSymbolView.java:47/50`、`LocationPointerView.java`、`MainActivity.java:679/688` 挂载；复验 `23c9fc9`、`1656e31` 均 PASS | **只检索了页面层（`map.html`）就当成全仓不存在**。检索面没写清，结论就不可信 |

**教训（与我一直在用的那条同源）**：报「没有」必须附**检索范围**；范围外的层（Java / DEX / 资源 XML / res）没搜过，就只能写「未在 X 层检索到」，不能写「不存在」。这与 goal 里「检索零命中才写原版不可得，且必须附检索范围与命令」是同一条规矩的另一面。

---

## 5. 遗留（逐条写清）

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
- 审图号：产品 5 条路径 **0 命中**；界面与代码默认值均**不允许**出现占位词（规则原文：「待核对 / 未确认 / 不适用」，此处为引用），**无真号就不显示该子句**。

---

## 5.5 已处置项（**不列为遗留**）

### complaint 一级两行墨迹对齐（`6acf66d`，`b47fcdb` 内含）

- **比较对象纠正**：`68 vs 79` 是 Compose 语义框（原版该页根节点为 `ComposeView`）与 View 布局框的不可比量，**不是字高**。按墨迹实测字高差 **1px**（46/45 vs 47/46）。按 79 调 padding 只会把框撑大、字形不变。
- **实际偏差在墨迹位置**：改前我方 363/544，原版 367/548，整体高 4px。
- **修法**：仅改行内上下内距 `24/24 → 32/24`，行高与步进（pitch 180）不动，居中后下移 4px。
- **判据（只认相对量，不认绝对数）**：**墨迹顶与原版逐值相等（差 0）、两行间距与原版相等（180 = 180）、不叠字、无 FATAL**。
- **绝对数必须带阈值，否则不可比**：同一张图在不同二值化阈值下「墨迹顶」会漂 **1px**——客户端口径（阈值另定）是 `367..413 h47` / 间距 `181`；LC审核验 口径（阈值 150）是 `368..412 h45` / 间距 `180`，原版同法 `368..411 h44`。**两套数的相对结论完全一致**，差 1px 只来自阈值。
- **台账规矩（写进本文件，不只修这一处）**：**任何墨迹/像素绝对数离开它的测量阈值就不可比；台账只记相对量，绝对数一律标阈值。**
- **笔的边界**：只动 `M3PageActivity.java`（`diff 6+/0−`），保持原混合行尾（CR 954 / LF 1144，未整篇改写）；**`map.html` 零改动**。
- 证据：`E:\APPDetector\reports\m7-complaint\`（`complaint-after.png`、`complaint.xml`、`after.png`、`after.xml`、入口路径图）。

---

## 5.6 设备租约操作规范（本轮踩过两次，进关账）

- **`-Release` 只在带该参数的树上有效**：`E:\APPDetector\wt\lcs-verify-stage4` 这个 worktree 的装机脚本**没有** `-Release` 参数——传了先报 `APK_NOT_FOUND`；若同时带 `-Apk` 则会**直接装包**（想释放却装了机）。
- **正确释放路径**二选一：① 在带 `-Release` 的树（如 `96be059`／`fa97c96` 那类）上跑；② 直接删 `device-lease.json`，但删之前必须确认没有他人正在使用该设备。
- 本轮实测记录：一次 `release exit=2`（在无该参数的树上），换树后才得到 `LEASE: none`。**这条比任何一次误装都值得记**。

---

## 6. 证据索引

| 目录 | 内容 | 备注 |
|---|---|---|
| **`E:\APPDetector\reports\M7-final-b47fcdb\`** | **18 个文件**（**最终树门禁原件**）：`gates/gate1..4`、`smoke.log`、`smoke-20261109-215326/`（六门日志 + `desktop/screenshot.png` 266,376 B + `android/app-debug.apk` **12,349,195 B** + `summary.txt`） | `b47fcdb`，`app` 树 `d1a186d`；**关账只引这一份** |
| `E:\APPDetector\reports\M7-final-fa97c96\` | **18 个文件**（同结构，`app-debug.apk` 12,349,187 B） | 树 `fa97c96`，**已作废**，仅留审计痕迹 |
| `E:\APPDetector\wt\lcs-verify-stage4\reports\M7-verify-final\` | `VERIFY-SIGN.md`（3,032 B、CR=0）+ `n-b47fcdb/`（LC审核验 复跑的四道门禁原始输出） | 终验方的独立复跑 |
| `E:\APPDetector\wt\lcs-verify-stage4\reports\M7-b\` | **62 个文件**：`kml-fa97c96/`（base.kml、after.kml）、`kml-96be059/`、`offline-96be059/`、`about-fa97c96/`、`contact-fa97c96/`、`banner-8604eac/`、`banner-a8bc74e/`、`install-fa97c96/`、`gate-4c1c406/`、`REPORT.md` | ⚠ **仓库根下 `reports\M7-b\` 不存在**，见 §7 第 2 条 |
| `E:\APPDetector\reports\M6-pages-96be059\` | **20 个文件**：`pages/`（四页 png+xml、`compare.log`、`run.log`）、`install-s1/`、`build/`、`app-debug-96be059.apk` | 树 `96be059`，**账户页 BLOCKED 的出处**；其 `VERDICT=FAIL` 均属旧树 |
| `docs/fidelity/m7/` | `symbols-stage1.md`（两个符号取证）、`ground-drape-migration.md`（贴地迁移方案）、`offline-cache-orig.md`、`tdt-offline-terms.md`、`screens/`（13 张取证图） | 树 `fa97c96` |
| `docs/qa/help-figures-audit.md` | 29 条图注↔画面核对表（17/6/6，`fig-32` CONFIRMED-MISSING） | 本轮由图核对产出 |
| `docs/qa/dup-pairs-verdict.md` | `docs/fidelity` 两对重复图逐对判定（均走具名例外） | 已由 LC审核验 终验 PASS |
| `docs/product/m7-eight-final-states.md` | **八条终态表**（LC产品，`32c69d8`，`blob:35793216e32f5eae427daaf3b7b53cb63a8f8813`，18,146 B / 161 行 / CR=0）；三个禁用终态词计数均为 **0** | **终态判定的规范文本**，本文件 §3 只做索引 |
| `E:\APPDetector\reports\m7-complaint\` | complaint 墨迹对齐证据（`complaint-after.png`、`complaint.xml`、`after.png/xml`、`main.png`、`drawer*.png`） | 客户端实机，`b47fcdb` 内含 |

---

## 7. 一致性校验：**三处已按裁定定性（不再挂）**

### 第 1 条 · ② 的计数数：**已按最终树补测并改写（v1.2 闭环）**

```
历史读数（树 96be059，已标时点）：hit=24 shown=24 miss=28 netOk=0 online=false
最终树读数（map.html blob 126060b4）：hit=60 shown=60 miss=0 net=60 netOk=0 online=false
                                      ↑ LC审核验 在最终树上实测（2026-11-09 22:0x 前后）
```

四棵树的 `map.html`（用来证明「同 blob」不是随口说的）：

```
96be059  map.html = 29c76f51283fbd9cecfc2263cf86e0c88b576140   ← 24/24 的来源
4c1c406  map.html = 08516700a932c66a8d55a74aeb70336fd0c87d52
fa97c96  map.html = 126060b4fd83b6d015b9427b460008ed2c0b6158
b47fcdb / 3ffe142  map.html = 126060b4fd83b6d015b9427b460008ed2c0b6158   ← 同 blob，60/60 的来源
```

⇒ **关系式两条都成立且已在最终树复现**：`shown == hit`（60=60）、离线期 `netOk` 冻结（`=0` 且 `net=60` 仍计尝试，说明冻结的是 `netOk` 不是 `net`）。总监另要求 LC审核验 用最终包（`12,349,195 B` / `sha256:f7e36264…`）再补一次 6 分钟确认；**他补测回执一到，即以回执数为最终判据覆盖本行**。

### 第 2 条 · `reports\M7-b\` 路径不存在（已按总监裁定改写）

任务书写的是相对路径 `reports\M7-b\`，**仓库根下不存在**。关账一律写**绝对路径**：`E:\APPDetector\wt\lcs-verify-stage4\reports\M7-b\`（62 个文件）。本文件 §6 已按绝对路径列全。

### 第 3 条 · 包体：**只留一个基线（总监已裁定）**

```
唯一基线：b47fcdb 干净构建 / app 树 d1a186d / 12,349,195 B
          sha256:f7e362649af19f938d25132f8b2824321084f5dcaa5844be00569398401e89eb   ← LC测试 门禁产物
同树重复构建波动：12,349,203 / 12,349,187 / 12,349,195 B（差几十字节）
判据口径：认 blob:，不认 sha256（重建即变）
已废弃：12,349,203（旧树）/ 12,349,219（客户端重建）
```

### 附 · 第 4 条（工具侧，不影响判定）：② 的「参数行」在原始输出里没有

```
gate2-lchelp.txt 全文只有 4 行，不含命令行；
我实测对照（`b47fcdb` 干净树，`status --porcelain` = 0 行）：
  带 --expect-size 1228x2700 + --expect-size-map  →  SIZE-MAP 6 rules / fail=0 / exit=0
  只带 --expect-size-map（不带 --expect-size）      →  SIZE-MAP 6 rules / fail=0 / exit=0   ← 输出完全相同
  两个尺寸参数都不带                                →  fail=0 / exit=0（完全不做尺寸比较）  ← 同上
  负向对照：--expect-size 999x999 + map             →  fail=22 / exit=1   ← 证明该参数确实生效
```
⇒ 「② 带了两个参数」这件事，**从存档输出本身无法证明**（三种跑法输出一致）；能证明的是**负向对照能让它变红**，即参数有效。建议 LC测试 在门禁输出里固定回显命令行（或 `SIZE-MAP` 行写明 `fallback=1228x2700`），否则下次又会重演「参数给了没执行」那类假绿。

### 附 5 · 本文档自身会改变 ③ 的计数

`docs/fidelity` 目录下多一个 `.md`，③ 的 `other` 计数就会从 **146 → 147**（我在 `3ffe142`、`953fb8b` 两次实测均为 147，`fail` 均 0）。**这不是异常**，但 LC审核验 复核时若按旧的 `other=146` 卡，会误判。请以「`fail=0`、`allowed=3`」为判据，`other` 只作参照。

反过来说：**`docs/product/` 下的文件不计入这个计数**——产品终态表 v1.3（`084edc9`）即便合入，`other` 仍是 147（我在 `953fb8b` 上实测）。这条写在案，免得下次又按「加一个 md 就 +1」去卡。

---

## 8. 未覆盖范围

- **未做**：最终树上的真机读数一律**不是我测的**（KML 往返除外，那一项我自己复算过 `0.000e+00`）；`hit/shown/netOk/banner` 引自 LC审核验 在最终树上的实测，已标时点（见 §7 第 1 条）。
- **未做**：账户页登录态复验（无测试账号、无设备）。
- **未改**：`help-center.md`、`docs/review/`、`docs/qa/baseline-android/` 一律未触碰；本文件每次改动都只动它自己（`git diff --name-only 953fb8b..HEAD` 只列它）。
- **未核**：B 轮缓存七个数在最终树的构造性恒等式实测（`missAttemptsTotal = missKeyedAttempts + missNoKey + missUnsupported`）——代码层由 LC地图/LC审核验 静态核过，**真机交叉验未做**。
- **未核**：第 8 条 T3「无过渡动画」——设备无 `screenrecord`，与 `A-09 动效录屏` 同口径，记「证据不可得」，**不写成未实现**。
- **未签**：本文件所有判定均由 LC产品 终态表给出、技术总监裁定，**我不自签**，逐条签名归 LC审核验。

---

## 9. 时间戳

- v1.0 开工 **21:34** / 收工 **21:56**（树 `fa97c96`）；v1.1 **21:52–22:0x**（`b47fcdb`）；v1.2 **22:0x**（`3ffe142`）；v1.3 **22:1x**（`953fb8b`，`app` 树始终 `d1a186d`）｜返工轮次 **3**（①`app` 树 `0fa8a2dc`→`d1a186d` 全量重挂；②总监裁定后 ②/包体/`SMOKE` 措辞三处改写；③合产品文档后核对 `other` 预期并按实测更正 147）
- 本文件不含二进制、不含构建产物；所有 hash 均以 `blob:` / `sha256:` 前缀标注。
- 禁词自检：本文档**没有**用那三个被禁止的终态词给任何一条定状态；唯一一处出现是「审图号占位词规则」的**原话引用**（已加「规则原文」标注），不是终态标签。
