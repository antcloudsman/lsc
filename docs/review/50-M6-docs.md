# 50-M6 文档与数值档案审查（M6 文档/契约 + M7 数值档案/构建）v2 复核版

> 审查员：LC审文档｜审查基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（`review-20261105`）
> 工作区：`E:\APPDetector\review-base`（只读；本文件是唯一写入）
> 日期：2026-11-05｜编号前缀：G（沿用 `98-report-conventions.md` §2 与 `99-FINAL-REVIEW.md` 已分配的 G 编号；**新发现从 G-24 起**，不复用旧号）
> 本版取代同路径上一版（上一版保存在 git 历史 `78449e1:docs/review/50-M6-docs.md`）。本轮全部结论回基线代码/文档/`.inspect` 证据独立复核；上一版中经复核成立的条目保留原编号与主题，证据按本轮实测重写。

**与总监裁定的对齐（`99-FINAL-REVIEW.md:44-55`）**

- K-01 母条 = `B-03 + B-04`；本报告 `G-01` 仍是交叉引用，不单独计入 M6 正式数。
- `G-06` → `C-01`、`G-09` → `C-06`，保持交叉引用。
- `app/Dockerfile`、`app/docker-compose.yml`、`app/.dockerignore` 归属 **M4**（已由 M4 全读）；本报告只声明归属，不下结论（见 §8-10）。
- 证据等级仍只有三档：**已证实 / 高度可疑 / 待确认**；只有「已证实」进缺陷清单。

---

## 0. 结论摘要

**本轮亲手复核为真的声明（这些**不是**缺陷，先给可信面）：**

1. **780 条圈层文字地址全部真实**：`ring_label_addrs.json` 的 780 条 `smali_line` 逐条指向 `CompassReadTypeEnum.smali` 的 `const-string`，把 smali 的 `\uXXXX` 解码后与 `text` 比对 **780/780 相同（0 处不符）**；14 段 `code_off` 首末地址与 `MECHANICS.md:279-282` 逐段相等；`getTextList` 方法实际起止行 **630–7094**，与 `MECHANICS.md:248` 一致。
2. **86 项数值回归在基线全绿**：`python tests/numeric/run_all.py` → `tests=86 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0`，`RESULT: PASS`。
3. **生成区与 JSON 一致**：`pwsh -File tools/mechanics/gen-mechanics.ps1 -Check` → 两段 `PASS`；`mechanics.json` 实算 SHA256 `984c5fb618634b0a1202908243acfa1dfbd3d1e4efa0b34df2a778e0d31f34ca`，与两端生成头声明完全一致。
4. **库内证据哈希自洽**：`docs/qa/baseline-android/manifest.md` 5/5、`round-d5/SHA256SUMS.txt` 7/7、`variant-p01/SHA256SUMS.txt` 4/4 全部与文件实算相符（含字节数）。
5. **AT-P0-2 的 14 行、AT-B2 的 84 格**用 `mechanics.json` 独立复算，逐格一致（含 U+200C 与空格）；ZWNJ 只在 TYPE_1/TYPE_3 两行，与文档「逐字节 12/14、另 2 行仅差 ZWNJ」吻合。
6. **桌面几何自洽**：`dial.rs:60-69` 的 `central_dial(1100,780)` 实算 `(540,409,232)`，与 `FINAL-REPORT.md:36` 一致。
7. **人盘/天盘修复在两端成立**：`MainActivity.java:643-644` 为 `plate(h+15)`/`plate(h)`，`main.rs:399-400` 为 `(floor(h/15)+1)%24`/`floor(h/15)`，与 `MECHANICS.md:288-289` 的等价值一致。
8. **用户已知缺陷（K-01）成立**：原版真机截图显示罗盘浮层居中叠在地图上、底栏是工具条而非三 Tab；基线把浮层放顶部，且「罗盘」「地图」是两个加载同一 `map.html` 的 Tab。

**缺陷计数：正式 24 条（高 3 / 中 12 / 低 9）+ 交叉引用 3 条（G-01、G-06、G-09）+ 待确认 7 条（G-15、G-16 与 T-01…T-05）+ 无证据断言 10 条（§5 单列）。**

**最重的四条：**

- **G-04（高）**：收官「18/18」不可核验——记录结果的提交 `d286873` 里，脚本还是 DOM 判据（同提交文档自己记 `P2-06-vis FAIL`），能产出「判图 143」的脚本是更晚的 `76c4378`；证据目录不入库；脚本不校验「必须 18 项」。
- **G-02（高）**：`gap-matrix` 的「当前实现」状态与行号全面失效——人盘/天盘已修，矩阵仍记「缺失/P0」；A9 引用的 `MainActivity.java:235-236` 在基线是 `registerDebugBridge` 的两行。
- **G-03（中）**：`FINAL-REPORT` 声称入库的 `mechanics_gen.rs` / `MechanicsGen.java` 在基线不存在；实际生成器就地写 `main.rs` / `CompassData.java`。
- **G-24（中，新）**：「数值只有一个来源」未覆盖读数公式——生成器自己注明公式仍保留内联字面量。

---

## 1. 已知缺陷清单（必须收录，用户直接提出）

### K-01 / G-01（交叉引用→B-03+B-04）底部三 Tab 与原版首页结构不符；「罗盘」「地图」是同一页面的两种表现，罗盘浮层应居中叠加

**来源（用户原话，`98-report-conventions.md:151-153`）**

> 底部菜单里「罗盘」与「地图」其实是同一个页面的不同表现：参照原 APK 首页布局，罗盘应是点击「罗盘」按钮后浮动叠加在地图中心，配合地图模拟真实罗盘场景；整个底部菜单栏没有存在意义。要遮挡页面的组件与子页，应依原 APK 的设计逻辑放在合适位置、用合适的呈现方式。

**现状（基线代码事实）**

- `app/android/app/src/main/res/layout/activity_main.xml:14-49`：底部常驻三按钮 `tab_compass`/`tab_map`/`tab_knowledge`。
- `MainActivity.java:79-90`：
  ```java
  79: compassTab = buildCompassTab();
  80: mapView = buildMapTab();
  81: knowledgeTab = buildKnowledgeTab();
  ...
  87: tabCompass.setOnClickListener(v -> showTab(0));
  88: tabMap.setOnClickListener(v -> showTab(1));
  89: tabKnowledge.setOnClickListener(v -> showTab(2));
  90: showTab(0);
  ```
- `MainActivity.java:263-274`（罗盘 Tab = 同一张地图 + 浮层；浮层贴顶部）：
  ```java
  263: compassMapView = createMapWebView();
  270: int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
  272: cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
  273: cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
  ```

**原版档案依据（用户点名要核的 gap-matrix / MECHANICS MapWeb 章节）**

- `docs/gap-matrix.md:121`（E6，真机控件全量清点）：「…底栏仅 切换/旋转/缩放/透明度/**天支线**；`天支线` 是十二支线圈层，不是圈层开关」——原版底栏是**工具按钮**，没有「罗盘/地图/知识库」三 Tab。
- `app/MECHANICS.md:120-126`：原版 = WebView 地图（`MapWebController`）+ 罗盘 Compose `GraphicsLayer` **叠层**（原文「the compass is a Compose `GraphicsLayer` overlay」）。
- 原版真机截图（库外，只作引用）：`.inspect\derived\orig-shots\win-02-compass.png`，1228×2700，SHA256 `812c4b9b184af207e66c726eed84d77f1cb882d91b2c85dd93f73e61693b9435`。图中罗盘圆盘**居中叠在地图上**（十字线交点约在屏幕 50% 高度），左右两列悬浮工具（截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地；地图/实景/坐南朝北/指针开关/±/3D/定位），底栏「切换 旋转 缩放 透明度 天支线」，**全屏无三 Tab 导航**。
- `docs/product/PRD-v0.1.md:100`（B-19）自己承认依据不足：「`activity_main.xml` + `showTab()` ｜ 原版主页结构（未逐页比对） ｜ 等价替代」——在原版结构未比对的前提下给了「等价替代」。

**档案依据的缺口（必须补）**

- `gap-matrix` 与 `MECHANICS` 的 MapWeb 章节都**没有**记录「首页是否存在底部导航」「罗盘浮层的锚点（居中/顶部）」这两个布局事实；E6 只清点控件。`PRD` 因此无法引用文字档案，只能写「未逐页比对」。
- 「罗盘浮层居中」目前只有库外截图，`.inspect` 不入库（`TEAM_CHARTER.md:79`）。
- 建议：把「罗盘居中、无三 Tab、底栏工具条」写进 `gap-matrix` E6 或新增「首页布局」行，并把脱敏截图入库 `docs/qa/baseline-android/`。

**触发条件**：冷启动 → `showTab(0)`；点「地图」切到同一 `map.html` 的另一个实例。

**预期 vs 实际**
- 预期（原版）：地图铺满，罗盘浮层居中叠加；工具以左右悬浮列 + 底栏工具条呈现。
- 实际（基线）：罗盘 Tab 与地图 Tab 都加载 `map.html`（`MainActivity.java:391`），差别只有浮层与可见性；浮层 `gravity=TOP`、`topMargin=16dp`；底部是三页面导航按钮。
- 副作用：同一页面被两个 WebView 各加载一次；`MainActivity.java:682-686` 必须同时向两个 WebView 推 `setHeading/setSitFace`，是双实例的补丁痕迹。

**修复方向**：罗盘浮层改 `Gravity.CENTER`（直径按原版量取，截图约 0.72–0.78 屏宽）；取消三 Tab，恢复原版底栏工具条；「地图」改为同一地图页隐藏浮层的状态（原版右上「隐藏」）；知识库入口按 C2（圈层点击→KB）与 E1（设置页）归位；合并双 WebView。

---

## 2. 缺陷清单（正式 24 条：高 3 / 中 12 / 低 9）

> 编号沿用 99；`G-01`/`G-06`/`G-09` 见 §3 交叉引用。每条给：位置（路径:行 + 原文片段）／触发条件／预期 vs 实际／证据等级／影响／修复方向。

### 2.1 高（3 条）

#### G-02【高】`gap-matrix.md` 的状态列与行号在基线全面失效，且统计数字被 `FINAL-REPORT.md` 原样引用

**位置**
- `docs/gap-matrix.md:67`（A9）：「… `MainActivity.java:235` `plate(h+7.5)`；`plate()`=floor(h/15)，代数上恒等于地盘；桌面 `main.rs:549-550` 同 | **缺失**」
- `docs/gap-matrix.md:68`（A10）：「… `MainActivity.java:236` `plate(h−7.5+15)`，代数上仍等于地盘 | **缺失**」
- `docs/gap-matrix.md:147`（H P0-1）：「人盘/天盘读数恒等于地盘（`MainActivity.java:235-236`、`main.rs:549-550`）」
- 基线实际：`MainActivity.java:643-644`
  ```java
  CompassData.MOUNTAINS[plate(r.heading + 15f)],   // 人盘
  CompassData.MOUNTAINS[plate(r.heading)],         // 天盘
  ```
  `main.rs:399-400`：`let hm = ((rd.heading / 15.0).floor() as usize + 1) % 24;` / `let tm = ... % 24;`
- 行号逐行回读核对（全部错位）：`MainActivity.java:235-236` = `if (receiverRegistered) return;` / `try {`；`main.rs:549-550` = `ui.add_space(4.0);` / `});`；A1 引用的 `main.rs:181` 是空行、`CompassData.java:143` 是 `norm()` 前的空行；B1 引用的 `CompassData.java:190-192` 是 `Reading` 字段（盘面名在 `:70-73`）。
- 矩阵自己的免责：`gap-matrix.md:6`「『当前实现』一列指向**当前工作树**（分支 `feature/lc-server-infra`…），行号为写入本矩阵时的实测行号；合入后如有漂移以代码为准。」

**触发条件**：按矩阵核对「当前实现/缺口」。
**预期 vs 实际**：预期是「收官统计」的差值矩阵（`:9`「46 行全有状态」）；实际「当前实现」列与行号指向已不存在的分支快照，状态与人盘/天盘已修事实相反。免责声明能解释行号漂移，解释不了**状态**（缺失 vs 已修）相反。
**证据等级**：已证实。
**影响**：`FINAL-REPORT.md:60-69` 直接引用该矩阵统计作收官口径，读者会得到「P0 读数缺陷仍未修」的结论。
**修复方向**：按基线重写 A9/A10/H 的状态与「文件+方法名」定位；旧行号加删除线或标「快照失效」。

#### G-04【高】「18 项 AT 全绿」不能从基线复核：记录结果与当时提交的判据脚本不一致，证据未入库，脚本不校验用例清单

**位置**
- `docs/FINAL-REPORT.md:4`：「验收复验提交：`55b4ca8`（P2-06 可见性改为「判图不判 DOM」后重跑 18/18）」；`:11`：「收官 AT 18/18 PASS、0 FAIL、0 BLOCKED。」
- `docs/qa/AT-final-round.md:28`：「| 修后 | `d286873`（merge `111c4fb`），APK sha256 `A94DE24A…F70F189B` | **18 PASS / 0 FAIL / 0 BLOCKED / 1 诊断，exit 0** |」
- 同文件在 `d286873` 提交里的版本（`git show d286873:docs/qa/AT-final-round.md`）第 41 行：「| P2-06-vis | **FAIL** | `#srcInfo` 设备像素 x560–1195 / y2293–2349 → 落在底部读数面板带内，**分享图上被盖住** |」
- `git diff d286873 76c4378 -- tests/at/run-final.ps1`：`d286873` 用 `getBoundingClientRect()` 的 DOM 矩形判；`76c4378` 才改成「面板上方条带 y1400–1850 金色像素行 ≥100」的判图。
- `docs/qa/AT-final-round.md:21`：「证据落在 `checks\at-final\<时间戳>\`…**不入库**。」
- `tests/at/run-final.ps1:735-743`：只按已跑用例统计后 `exit 0/1/2`，**无「必须 18 项」断言**；`-SkipTd016`（`AT-final-round.md:17`）时 17 项也 exit 0。

**触发条件**：任何复核者按报告复现 18 项。
**预期 vs 实际**：预期「报告指定的提交 + 报告指定的脚本 + 入库证据」能复现；实际记录 18/0 的 `d286873` 上脚本是 DOM 判据、按该判据必 FAIL（同提交文档已记 FAIL），判图脚本只在更晚提交；报告未写脚本版本；证据不入库；脚本不强制清单。→ **报告层面的 18/18 不可核验**（设备端是否跑过见 Q/G-15）。
**证据等级**：已证实（文档与提交物不自洽）。
**影响**：收官最强结论失去可复核性。
**修复方向**：报告加「脚本 `@sha` + 证据 `table.tsv` 入库」；`run-final.ps1` 加 `-Expected 18`；若用未提交脚本跑过，补提交或写 diff。

#### G-05【高】「未确认 0 / 无一条说不清」与同文件未取证条目直接矛盾

**位置**
- `docs/FINAL-REPORT.md:5`：「…剩余缺口全部登记为「有意省略 / 有意解除 / 有意差异 / 未做」，**无一条是「说不清」**。」
- `docs/gap-matrix.md:90`（B8c）：「**算法未取证**：DEX 中未见宅卦计算类…究竟哪张表**未确认**」
- `docs/gap-matrix.md:179`：「## J. 未确认（不臆造，等证据）」；`:183`（D4）：「**相册路径与分享面板的具体实现仍未取证**」
- `docs/gap-matrix.md:9-18`：统计 `未确认 | 0`。

**触发条件**：把「收官」与「J 段未确认」对读。
**预期 vs 实际**：预期「未确认 0」意味着没有未结案项；实际 B8c/D4/J 明确列了未取证/未确认。两者不可能同时为真（「未做」与「说不清」是不同状态，B8c 是算法不知道，不是决定不做）。
**证据等级**：已证实（同两份文档内直接矛盾）。
**影响**：收官口径自相矛盾，读者无法判断哪些是「决定不做」、哪些是「还不知道」。
**修复方向**：要么把 B8c/D4 补证后关闭，要么把 FINAL-REPORT 改为「未确认 N 条，见矩阵 §J」，并把 §00 的 `未确认 0` 改成实际数。

### 2.2 中（12 条）

#### G-03【中】`FINAL-REPORT.md` 核心证据列指向不存在的生成物，复现路径指向非基线目录

**位置**
- `docs/FINAL-REPORT.md:23`：「| 单一数值源 | 已落地 | `mechanics.json` v2 → `mechanics_gen.rs` / `MechanicsGen.java`（入库 + sha256 头 + `-Check`） |」
- 实际生成器 `tools/mechanics/gen-mechanics.ps1:3-5`：
  ```powershell
  # Emits the marked GENERATED region inside:
  #   app/src/main.rs                                        (Rust tables + ring scalars)
  #   app/android/app/src/main/java/com/luopan/fengshui/CompassData.java
  ```
- 生成器写入路径：`gen-mechanics.ps1:21-23`；基线生成区：`main.rs:55-161`、`CompassData.java:12-123`。
- 全仓检索 `**/MechanicsGen.java`、`**/mechanics_gen.rs` = **0 命中**。
- 同条另一处：`FINAL-REPORT.md:93` 复现命令 `cd E:\APPDetector\output`（开发脏树），不是审查基线。

**触发条件**：复核者按报告路径找生成文件/跑复现。
**预期 vs 实际**：预期仓库有 `mechanics_gen.rs`/`MechanicsGen.java`；实际不存在，生成内容就地写两个手写文件的标记区。复现命令也指向基线外目录。
**证据等级**：已证实。
**影响**：报告核心证据列不可执行；`docs/design/single-source-of-truth.md:172-175` 计划过这两个文件，实现改成「就地生成区」但报告没跟。
**修复方向**：报告改为「`gen-mechanics.ps1` 就地生成 `main.rs`/`CompassData.java` 的 GENERATED 区」；复现命令改为 `review-base`。

#### G-07【中】「19 项全部有来源、0 项估计」是关键词扫描，不是溯源；其中 3 项与 DEX 语义冲突

**位置**
- `app/mechanics.json` 19 项 `source`：带十六进制地址 6 项、仅类/表无地址 7 项、仅 `knowledge base` 6 项（全表见 §6.2）。
- 3 项三盘 offset 与 DEX 冲突：`mechanics.json:25` `earth_plate_offset = 0`、`:33` `human_plate_offset = -7.5`、`:41` `heaven_plate_offset = 7.5`；而 `MECHANICS.md:287-289`（R1.3）记 TYPE_4 offset `7.5f`、TYPE_8 `0.0f`、TYPE_9 `0.0f`，并明说「原版不存在第四/第五套 ±7.5 的 offset 常量」（`:292`）。
- 检查器只做关键词：`tests/numeric/tools/check_mechanics.py:19`（`FORBIDDEN = ("估计","估算","estimate","estimated","unconfirmed","TODO","未确认")`）、`:26-31`（source 非空且不含黑名单即算 sourced）。
- 实跑：`mechanics.json version=2 values=19 sourced=19 estimated=0`。

**触发条件**：按 D-004/质量红线核对「19 项全部有来源」。
**预期 vs 实际**：预期「有来源」= 可指到 DEX 地址或实测；实际 6 项仅 KB、7 项无地址；三盘 offset 是**实现等价值**却被写作原版数值来源。字面「没有 estimated 标记」成立，证据强度不成立。
**证据等级**：已证实。
**影响**：若有人「按来源」把三盘 offset 改成 DEX 的 7.5/0/0，会直接改坏实现。
**修复方向**：三盘 source 改写为「DEX TYPE_4/8/9 offset + labels 旋转；本值为等价值（R1.3）」；给 6 项 KB 来源补 KB 页名；把「有地址/仅类型/仅 KB」三档写进 MECHANICS。

#### G-08【中】`MECHANICS.md` 的地址类声明依赖库外 `.inspect` 证据，基线内不可回溯（但库外复核为真）

**位置**
- `app/MECHANICS.md:231-234`：证据来自 `.inspect\unpack\mem_dump\classes06.dex` 的 baksmali；`:276-282` 逐条地址导成 `.inspect\derived\ring_label_addrs.json`。
- `docs/gap-matrix.md:41-46`：派生件标「中间产物，不入库」；`TEAM_CHARTER.md:79`：禁止提交 `.inspect`。

**触发条件**：新克隆/换机复核 780 条地址。
**预期 vs 实际**：预期基线内可自证；实际不可（`.inspect` 不在仓库）。**但本机库外证据存在且为真**：我复核 780/780 命中、14 段首末地址与文档逐段相等（§6.1）。
**证据等级**：已证实（不可回溯这一事实）；地址真实性也已证实（库外）。
**影响**：可维护性/可审计性；结论只能信文字或依赖本机 `.inspect`。
**修复方向**：入库不含 APK 的派生件（780 条文字 + code_off + smali 行号，约 76 KB），或在 MECHANICS 写「复核需 `.inspect`，其目录 SHA256 如下」。

#### G-10【中】用户可见的知识库文案与数值档案/DEX 表冲突（D-008 文案诚信）

**位置**
- `CompassData.java:273`：「圆周72等份，每份5度，**每山3龙**，用于确定来龙干支。」vs `MECHANICS.md:274`：「TYPE_5 是 12 组 × (5 龙 + 1「正X」) = 72，**不是**「每山 3 龙」」
- `CompassData.java:275`：「正向为每山15度范围；兼向为越过山界约3–7度的邻山过渡区。」vs `MECHANICS.md:186-187`「正向 = 中央 **9°**…兼向 = 外侧 **3°**」与 `CompassData.java:233` `Math.abs(offset) * 2f <= ZHENG_XIANG_DEG`（`ZHENG_XIANG_DEG = 9f`，`:122`）
- 桌面同文案：`main.rs:353`、`:355`。

**触发条件**：用户在知识库点开「穿山七十二龙」「正兼向」。
**预期 vs 实际**：D-008（`TEAM_CHARTER.md:111`）要求文案不得有未取证声明；实际 UI 把被 DEX 否定的教材说法当事实，且与同一 App 的 9° 判正向互相矛盾（读「15° 正向」但读数按 9° 判）。
**证据等级**：已证实。
**影响**：文案诚信红线未兑现；读数与说明打架，直接触发「读数可不可信」。
**修复方向**：两段文案按 `rings[].labels`/TYPE_14 改写（72 龙按「5 龙 + 正X」；正兼向按 9°/3°），或明确标「教材说法，原版以读数表为准」。

#### G-11【中】`MECHANICS.md` 早期章节与 R1/R2 补证矛盾且未标「已取代」，R2.2 仍写成待办

**位置**
- `MECHANICS.md:163-168`：「…The per-mountain *tables* are in the encrypted DEX and are marked **estimated**.」
- `MECHANICS.md:221-225`：「**分金/龙 naming (CONFIRMED, was "estimated")**…」
- `MECHANICS.md:247-251`：「R1.2 14 张文字表全部还原（780 条…）」「labels_status: confirmed_dex」
- `MECHANICS.md:372`：「### R2.2 轮 B2 换表清单（**当前仍是教材推导的圈**）」——基线两端 `RINGS` 已落表（`main.rs:130-145`、`CompassData.java:94-109`），B2 已完成（`docs/qa/atb2-result.md`）。
- 早期示例也被 DEX 否定：`:181-182`「子山: 甲子 丙子 戊子」，而 TYPE_5 在子山三格是 `丙子/戊子/庚子`（子山覆盖 `[352.5,7.5)`，i=70/71/0，由 `rings` 几何复算）。

**触发条件**：通读 MECHANICS 找「哪些还是估计」。
**预期 vs 实际**：预期同一档案只有一种当前状态；实际前 200 行说 estimated、R1 说 confirmed、R2 说「仍是教材推导」，无「已取代」标记。
**证据等级**：已证实。
**影响**：M7 核心人读档案自相矛盾，动摇「数值可追溯」的根基。
**修复方向**：给早期两节加「已被 R1/R2 取代」横幅；R2.2 标题改「已执行（B2，2026-11-05）」；修正 72 龙示例。

#### G-17【中】航向注入命令的键名在文档里自相矛盾：`--ef heading`（错）vs `--ef value`（对）

**位置**
- `docs/qa/AT-B2-ring-labels.md:23`：「注入能力：按 AT-P0-1 的 `com.luopan.fengshui.DEBUG`（extra **`heading`**）注入航向」
- `docs/qa/device-method.md:82-84`：「**`heading` 注入的 extra 键是 `value`，不是 `heading`**：`MainActivity` 用 `it.getFloatExtra("value", 0f)`，正确命令是 `am broadcast … --ef value 90`；`--ef heading 90` 会被忽略。」
- 代码：`MainActivity.java:170` `injectedHeading = it.getFloatExtra("value", 0f);`

**触发条件**：按 AT-B2 前置检查发 `--ef heading 90`。
**预期 vs 实际**：预期能注入；实际被忽略（`value` 缺省 0），B2-02/B2-03 会在航向 0 下跑，84 格全对不上。
**证据等级**：已证实。
**影响**：AT-B2 复跑会得到假 FAIL。
**修复方向**：AT-B2 改 `--ef value <deg>`（AT-P0-1 的 S2 本来就写对）。

#### G-18【中】AT-P0-2 的 P2-02（地图/3D 截图）在基线没有可执行入口，最终 18 项里被静默剔除

**位置**
- `docs/qa/AT-P0-2-capture-record.md:35-39`（P2-02）：「步骤：地图 Tab → 进 3D → 点「截图」。」
- 基线：`MainActivity.java:567-569` `buildMapTab()` 只返回 `createMapWebView()`，地图 Tab 没有截图按钮；唯一的「截图」按钮在罗盘 Tab 的 panel（`:318-320`），`captureMap()` 固定抓 `compassTab`（`:400-401`）。
- `docs/qa/AT-final-round.md:30-50` 的 18 项表里没有 P2-02。

**触发条件**：按 P2-02 去地图 Tab 找「截图」。
**预期 vs 实际**：预期有入口；实际没有。AT 收官表把 P2-02 静默去掉，没有任何「N/A/未做」记录。
**证据等级**：已证实。
**影响**：P0-2 的「地图/3D 截图」验收项实际未覆盖；「18 项全绿」里少了一条原始用例。
**修复方向**：要么在地图 Tab 加截图入口（并区分抓取对象），要么在 AT 与收官表写「P2-02 未做（缺入口）」。

#### G-19【中】AT-P0-1 的 RT-010 指向已被 `device-method.md` 宣布作废的旧基线

**位置**
- `docs/qa/AT-P0-1-reading-trust.md:82`（RT-010）：「与 `docs/qa/baseline-android/` 的同 heading 基线 dump **逐字相同**」
- `docs/qa/device-method.md:56-57`：「基线参考：`docs/qa/baseline-android/round-d5/`…**旧 `docs/qa/baseline-android/` 为 02:56 版，不要再用**」
- 两套基线确实不同：`manifest.md` 的 `tab-compass.png` 是 1,569,570 B（02:56 版），`round-d5` 的是 1,401,447 B（D5 重冻）。

**触发条件**：按 RT-010 拿「旧基线」比对。
**预期 vs 实际**：预期用当前基线；实际引用了作废基线，且旧基线的 P0-1 默认态/盘面与 D5 后不同（`round-d5/manifest.md:29-31`：默认磁北、常用罗盘、读数 273.85）。
**证据等级**：已证实。
**影响**：RT-010 会因基线不同而假 FAIL。
**修复方向**：RT-010 改指 `docs/qa/baseline-android/round-d5/`（或 variant-p01）。

#### G-24【中，新】「数值只有一个来源」未覆盖读数公式：生成器自认公式仍保留内联字面量

**位置**
- `docs/FINAL-REPORT.md:12`：「数值只有**一个来源**：`app/mechanics.json` v2 → 生成器 → 两端常量 + golden。」
- 生成器自认：`tools/mechanics/gen-mechanics.ps1:165-166`
  ```
  // Ring scalars (values[] / rings[]). The reading formulas keep their inline
  // literals for now and are pinned to these by the #[cfg(test)] module in main.rs.
  ```
- `app/src/main.rs:241-247`（公式内联 7.5/15.0/3.0/5.0/6.0/1.5）：
  ```rust
  let m = ((h + 7.5) % 360.0 / 15.0).floor() as usize % 24;
  let fenjin_slot = ((from_start / 3.0).floor() as usize).min(4);
  let dragon72_slot = ((from_start / 5.0).floor() as usize).min(2);
  let dragon60 = (((h - 1.5 + 360.0) % 360.0 / 6.0).floor() as usize) % 60;
  ```
- `CompassData.java:209-216`（Java 同式，用生成常量，但 `MOUNTAIN_STEP`/`MOUNTAIN_HALF` 仍分别派生）。
- pin 测试只断言「常量 == 字面量」：`main.rs:642-654`。

**触发条件**：只改 `mechanics.json` 的 `degrees_per_mountain`/`fenjin_interval` 等再生成。
**预期 vs 实际**：预期改 JSON → 两端读数随之变；实际生成区常量变了，公式字面量没变，`cargo test` 会红、Android 不会红——「单一来源」只对表/常量成立，对公式不成立。
**证据等级**：已证实。
**影响**：D-004 只落实一半；口径变更仍要手改两个文件。
**修复方向**：报告措辞降为「表与常量单源，公式待统一」；或把公式改为读生成常量，并让 pin 测试从 JSON 反推。

#### G-25【中，新】版本锚点四处不一致（166/162/168/170），三份 AT 仍写「未实现→BLOCKED」

**位置**
- `docs/FINAL-REPORT.md:3`：「`develop` @ `f688c60`（166 个提交）」；`:4`：「复验提交：`55b4ca8`」。
- `docs/product/PRD-v0.1.md:361`：「`develop` = `76c4378`（168 提交，收官…）」。
- 基线：`d752efa`（`00-MATRIX.md:8`）。`git rev-list --count` 实测：`f688c60=166`、`55b4ca8=162`、`76c4378=168`、`d752efa=170`。
- 三份 AT 状态行仍写未实现：`AT-P0-1-reading-trust.md:5`、`AT-P0-0-feature-switch.md:5`、`AT-P0-4-3d-probe.md:5` 均为「状态：**未实现 → 当前 BLOCKED**」；而基线已有 `SensorMath.java`、`DebugBridge`、隐藏开关、`LCAndroid` 桥。

**触发条件**：按报告/AT 找「最终代码」并复验。
**预期 vs 实际**：预期四份文档指向同一收官提交；实际四个锚点。代码面是好的：`git diff <a> 76c4378 -- app/` 在 `f688c60`、`55b4ca8`、`d286873`、`d752efa` 上全为空——错位在**文档/测试脚本**（`8cb57f3..76c4378` 的 `tests/at/run-final.ps1` 改动 43 行）。
**证据等级**：已证实。
**影响**：可追溯性受损；「18/18 在哪个提交上跑」无法确定（与 G-04 叠加）。
**修复方向**：统一写「代码锚点 = `76c4378`（`app/` 与 `d752efa` 相同），文档锚点 = `d752efa`」；AT 状态改「已实现，按 §用例执行」并注明实现提交。

#### G-26【中，新】P0-4 的「坡度/坡向/`probe()`/`state().vexag`」没有实现

**位置**
- `docs/product/PRD-v0.1.md:248`（P0-4 功能点 1）：「显示该点经纬度、**真实海拔**、**坡度（°）**、**坡向（八向 + 度数）**」
- `docs/qa/AT-P0-4-3d-probe.md:16`（M1）：「`window.__LC3D` 增：`probe(lat,lng)` → `{elev, slope, aspect, demZoom}`…`state()` 增 `vexag`、`demZoom`」
- 基线实际：`map.html` 全文检索无 `probe(`、无 `slope`；`aspect` 只作透视矩阵参数（`:959`/`:1006`）；`state()` 无 `vexag`（VEXAG 只在 `:521`/`:1307` 内部与 HUD 文案）。
- `docs/FINAL-REPORT.md:57`：「3D 点读 | TD-001…015 无头、TD-016 真机 | PASS」——只能对应 `AT-P0-4-3d-pick.md` 的 TD 含义（`:18-34`），不是 probe 的口径。

**触发条件**：按 PRD P0-4 验收「点选出坡度/坡向」。
**预期 vs 实际**：预期点选返回海拔+坡度+坡向、HUD 常显倍率；实际只有经纬度/海拔/heading/src 的 pick 契约，无坡度/坡向，`probe()` 不存在。
**证据等级**：已证实。
**影响**：P0-4 的验收范围被「pick 的 15 条」替换，坡度/坡向这个卖点既没实现也没失败记录（与 G-20 的编号冲突是同一根因）。
**修复方向**：若不做，在 PRD/矩阵登记「未做」；若做，先落 `probe()` 与 `state().vexag`，再按 probe 的 20 条执行。

#### G-28【中，新】许可与素材来源不一致：`Cargo.toml` 声明 MIT、仓库无 LICENSE/NOTICE，却内置原版 artwork

**位置**
- `app/Cargo.toml:6`：`license = "MIT"`。
- 仓库根无 `LICENSE`/`NOTICE`/`COPYING`（`git ls-tree d752efa --name-only` 无匹配）。
- 内置原版素材：`app/android/app/src/main/res/drawable-nodpi/dial_full_0.png`…`dial_full_4.png`（矩阵行 14-18）；`FINAL-REPORT.md:28`「位图盘面模式（**原版全尺寸 PNG 5 幅**…）」；`gap-matrix.md:70`（A12）「原版 14 套 artwork 是**版权资产**，能否复用需产品决策」。

**触发条件**：对外分发源码/APK，或他人按 MIT 复用。
**预期 vs 实际**：预期「代码 MIT、素材另行声明」或去掉 MIT；实际是无 LICENSE 文本的 MIT + 未声明出处的原版位图。**证据等级**：已证实（声明与素材事实）；A12 的决策是否已拍板属待确认。**影响**：自用不分发风险低；一旦按「可交付 APK」外发，MIT 与第三方素材冲突。**修复方向**：加 LICENSE（MIT 正文）+ NOTICE（素材来源与自用范围）；或在矩阵/报告登记「素材复用已拍板」。

### 2.3 低（9 条）

#### G-12【低】`bugs.md` 的 OBS-001/OBS-002 在基线已不成立，台账仍列「待归口/备忘」

**位置**
- `docs/qa/bugs.md:270-274`（OBS-001）：「`CompassView.java:18-22` 保存 `dialType`，但 `onDraw` 只在 `:156`…和 `:178`…用到它 ⇒ dial 1/2/3 渲染完全相同」
- 基线：`CompassView.java:34-51` `setArtwork(int dial)`、`:175-176` `if (artwork != null) drawArtwork(...)`；`MainActivity.java:711-719` `onResume` 里 `setArtwork(dial)`；5 幅 `dial_full_0..4.png` 均在 `drawable-nodpi`（矩阵行 14-18）。
- `docs/qa/bugs.md:276-279`（OBS-002）：「`software.rs:287` 固定输出 `"正向 · {YINYANG}"`」
- 基线：`software.rs:281-296` 用 `reading_for` + `ring_text_clean(14, …)`；`:296` 是 `("正/兼向", format!("{} ({:+.1}°)", crate::ring_text_clean(14, rd.heading), rd.offset))`；全文无「正向」字面量（grep 0 命中）。

**触发条件**：按台账认领 OBS-001/002。
**预期 vs 实际**：预期台账反映当前代码；实际两条都已被后续提交修掉，状态仍是「待归口」「备忘」。
**证据等级**：已证实。**影响**：重复修已修问题/误判 5 盘面仍缺。**修复方向**：关闭并写修复提交；若 dial1/2/3 仍有残留，重开一条带新证据的条目。

#### G-13【低】数值测试条数在文档里有 74 / 75 / 83 / 86 四个值，实际 86

**位置**
- `docs/qa/bugs.md:20`「74 PASS」、`:81`「`tests=74`」；`docs/qa/AT-B2-ring-labels.md:21`「`tests=75`」、`:141`「75 绿」；`docs/qa/AT-P0-1-reading-trust.md:83`「当前 83 PASS」；`docs/qa/AT-final-round.md:48`「`tests=86`」；`docs/FINAL-REPORT.md:24`「86 项」。
- 实跑：`tests=86 failures=0 … RESULT: PASS`。

**触发条件**：按 AT 文档核「全绿」条数。
**预期 vs 实际**：历史值可保留，但 AT 是要执行的文档，应写「以执行输出为准（基线 86）」；现在读者会怀疑自己少跑 11/12 项。
**证据等级**：已证实。**影响**：复跑噪声。**修复方向**：AT 统一写「条数以 `run_all.py` 输出为准；2026-11-05 基线 86」。

#### G-14【低】`app/README.md` 的 3D 实现描述与基线不符

**位置**
- `app/README.md:24`：「已用天地图地形底图 + **CSS 3D 透视**等价替换。要更真 3D 可接天地图 DEM + WebGL。」
- 基线：`map.html:521` `var VEXAG = 2.5;`、`:601` `getContext("webgl", {…preserveDrawingBuffer:true})`，3D 为 Terrarium DEM + WebGL 网格。

**触发条件**：新人按 README 理解 3D。
**预期 vs 实际**：预期描述当前架构；实际停在「CSS 透视 + 待接 DEM」。**证据等级**：已证实。**影响**：低。**修复方向**：改「WebGL + Terrarium DEM，VEXAG=2.5」；「APIKEY 内置」改「单个硬编码 key，待换个人 key」（见 G-11/G-06）。

#### G-20【低】AT-P0-4 两份文档的用例数与 ID 互相打架（14 vs 15 vs 20）

**位置**
- `docs/qa/AT-P0-4-3d-pick.md:14`「跑 **14 条**断言」，`:18-34` 列 TD-001…TD-015（15 条），`:11` 期望 `total=15`。
- `docs/qa/AT-P0-4-3d-probe.md:3-5` 的 TD-001…TD-020 是另一套含义（海拔/坡度/坡向/缺块）。
- 执行器 `tools/map/run_at_p04.ps1:29-36` 只抓 `LCAT-` 行，不自己数。

**触发条件**：按 AT 核对「total」。**预期 vs 实际**：14 是旧值；两套 TD 同名不同义。**证据等级**：已证实。**影响**：判定含糊（与 G-26 同根）。**修复方向**：pick 用 PK-xxx、probe 用 PR-xxx；数字改 15。

#### G-21【低】`variant-p01\manifest.md` 含 CR/BEL 控制字符，命令文本被吃掉字母

**位置**
- `docs/qa/baseline-android/round-d5/variant-p01/manifest.md`，字节实测：
  - `0x0D@537`，上下文 `done）；\n  2. 用 \run-as 把 shared_pr` → 应为「用 run-as 把」，`r` 被 CR 取代；
  - `0x07@612`，上下文 `false，\n     \x07m force-stop 后重` → 应为「am force-stop 后重」，`a` 被 BEL 取代。
- `docs/qa/baseline-android/round-d5/variant-p01/prefs-off.xml`：`0x0D@271`，文件尾 `… />\r\n</map>\r\r\n`（裸 CR）。
- 读取表现：`Get-Content` 读成 15 行，按 `\n` 计数是 14 行——同一文件两种行数。

**触发条件**：照该 manifest 复制命令（正是复现「全关基线」的两条关键步骤）。
**预期 vs 实际**：预期文本可原样执行；实际「用 un-as」「m force-stop」是坏的。**证据等级**：已证实。**影响**：唯一「全关」操作记录不可复现。**修复方向**：重写两行；`device-method.md` 坑清单加「控制字符」；`prefs-off.xml` 去掉多余 CR。

#### G-22【低】PRD「代码与 `8cb57f3` 零差异」表述不成立/不精确（按 `app/` 为真，按全仓库为假）

**位置**
- `docs/product/PRD-v0.1.md:361`：「…代码与 `8cb57f3` 零差异…」
- 实测：`git diff 8cb57f3 76c4378 -- app/` **为空**（产品代码确实零差异）；但 `git diff --stat 8cb57f3 76c4378` 显示 `tests/at/run-final.ps1` 有 43 行改动，另有 7 个文档改动。

**触发条件**：把这句话当「全仓库零差异」引用（G-04 的复现链正依赖它）。
**预期 vs 实际**：预期「零差异」范围明确；实际未限定，容易被读成全仓库。**证据等级**：已证实（范围歧义）。**影响**：低，但正是 G-04 的诱因之一。**修复方向**：改为「`app/` 与 `8cb57f3` 零差异（测试脚本与文档有改动）」。

#### G-23【低】`gap-matrix.md:132` 的「当前实现」列仍写坐向单态，与基线双态不符（99 已并入）

**位置**
- `docs/gap-matrix.md:132`（F5）：「LC 地图侧 `map.html` 实现「坐南朝北」单一语义…**没有标签翻转、没有坐北朝南模式**」
- 基线：`map.html:104-106` `0=关 1=坐南朝北…2=坐北朝南…`；`:342-345` `sitMode===2 ? 180 : 0`；`:370-373` 双标签 `btn.textContent = (sitMode === 2) ? "坐北朝南" : "坐南朝北"`；`:379-382` 三态 toggle。

**触发条件**：按 F5 判断地图侧能力。**预期 vs 实际**：预期与代码一致；实际代码已是三态（关/坐南朝北/坐北朝南），矩阵仍写单态。**证据等级**：已证实。**影响**：低（矩阵状态列又一处过期，与 G-02 同源）。**修复方向**：F5 改为「等价替代（已实现三态；第二态几何未取证）」。

#### G-27【低，新】AT-P0-0 的隐藏入口与落盘契约和实现不一致，按该文档执行只能 BLOCKED/FAIL

**位置**
- `docs/qa/AT-P0-0-feature-switch.md:15`（K1）：「连点「知识库」页签 7 次…」；`:17`（K3）：「落盘 `shared_prefs/lc_features.xml`，key 形如 `feature.record.enabled`」
- 基线：`MainActivity.java:121-147`（注释「long-press the reading text 7 times」；`:143` 计数 ≥7 弹窗）；`:303` `readingView.setOnLongClickListener(...)`；落盘 `:73-77` `lc_prefs` + `:131` `putBoolean("p0_1_enabled", …)`。
- 收官 AT 用的是实现口径：`AT-final-round.md:36`「`p0_1_enabled=false`」。

**触发条件**：按 SW-001 连点知识库；按 K3 找 `lc_features.xml`。
**预期 vs 实际**：预期按 AT 能打开/校验；实际入口是长按读数文本、落盘是 `lc_prefs.xml`。**证据等级**：已证实。**影响**：增量验收契约失真；`FINAL-REPORT.md:53` 却记 SW-006 PASS。**修复方向**：实现与 AT 二选一写死；PRD `:71`/`:147-155` 同步。

#### G-29【低，新】审图号角注文案与判据多口径：README 写「地图来源…待核对」，实现是「底图…未确认」；AT-P0-2 仍留 DOM 判据描述

**位置**
- `docs/product/attribution/README.md:27`：「界面/分享图只显示「地图来源：天地图 · 审图号待核对」。」
- 实现：`map.html:72` `<div id="srcInfo">底图：天地图地形 · 审图号：未确认</div>`；`:336` `el.textContent = "底图：" + s.name + " · 审图号：" + (s.review || "未确认")`。
- `PRD-v0.1.md:384` 已允许同义（「允许同义写法（『待核对』亦可）」）——README 未同步。
- `docs/qa/AT-P0-2-capture-record.md:70` 仍写「期望：`#srcInfo` 的屏幕区域**不与底部读数面板（y≈1850–2500）重叠**」（DOM 判据），与同文件 `:73-74`「**判图不判 DOM**…≥100 即 PASS」矛盾。

**触发条件**：按 attribution README 核文案；按 AT-P0-2 第 70 行判可见性。
**预期 vs 实际**：预期一处口径；实际 README、AT 旧判据、实现文案三处不同（PRD 已调和前两者，AT 第 70 行没改）。**证据等级**：已证实。**影响**：低；但审图号是唯一开放合规项，文案应唯一。**修复方向**：README 与实现统一；AT-P0-2 §P2-06b 的「期望」改成判图口径。

---

## 3. 交叉引用（不计入 M6 正式数）

| 编号 | 主题 | 母条 | 本条保留的内容 |
|---|---|---|---|
| G-01 | 底部三 Tab / 罗盘浮层位置 / 双 WebView | K-01（B-03+B-04） | 见 §1（代码事实 + 原版截图 + E6 档案依据 + 缺口） |
| G-06 | `map-bridge.md:41` 自述「原版内置天地图 key，未改动」与 D-006「个人 key」及 `FINAL-REPORT.md:75` 冲突 | C-01（M3 地图） | 代码事实：`map.html:92` 唯一硬编码 key `dd18858b…`；三处口径冲突；key 归属待确认（§4-T-03） |
| G-09 | `map-bridge.md` 的桥状态与代码相反，并声明了不存在的事件类型 | C-06（M3 地图） | 代码事实：`MainActivity.java:377-383` 已 `addJavascriptInterface`；`LCAndroid.java:29-30` 已解析；`map.html` 只 `emit({type:"pick"})`/`pick-clear`（`:472`、`:1004`），文档 §5 的 `ready/mode/error/fps` 页面从未发出 |

（G-06 的 `map.html:92` 与 `map-bridge.md:41`、`TEAM_CHARTER.md:107`、`FINAL-REPORT.md:75`、`PRD:367` 的五处对读见 §6.5。）

---

## 4. 待确认清单（Q / T）

> 按 98 §3：只有「已证实」进缺陷清单；以下不进。

### G-15（Q-15）18/18 是否真实发生
- 线索：`AT-final-round.md:28` 记 18/0；`d286873` 同提交文档记 P2-06-vis FAIL。
- 已掌握：报告/脚本/提交三者不自洽（已证实，见 G-04）；设备端结果无库内证据。
- 缺：`checks/at-final/20261105-0544/` 的 `table.tsv`/`notes.md`/`logcat`，或重跑（设备 `L2E0222531014833`）。
- 补齐后判据：18 项全 PASS 且 `table.tsv` 与脚本 `@sha` 一致 → Q 关闭；否则并入 G-04。

### G-16（Q-16）.inspect 的 780 条地址是否真的对应 DEX
- **复核结论：对应为真**。780/780 条 `smali_line` 命中 `const-string`，解码后文字完全相同；14 段首末 `code_off` 与文档逐段相等；`getTextList` 起止 630–7094 与文档一致。方法见 §6.1。
- 剩余风险：`.inspect` 不入库（G-08）。建议总监把 Q-16 关闭或降级为「归档风险」，并入 G-08。

### T-01（新）17/18 项之外，P2-02 是否曾单独跑过
- 线索：`AT-final-round.md` 18 项表无 P2-02；`MainActivity` 地图 Tab 无截图入口。
- 缺：任何 P2-02 的 `shot-3d.png`/`summary.md`。补齐后判据：有图且 HUD/山体正常 → 关闭 G-18 的「未覆盖」部分。

### T-02（新）天地图三源审图号
- 线索：`attribution/README.md:9-15` 五个官方渠道全部 HTTP 418（含事件 ID）；`PRD:376-382` 标未确认。
- 缺：能访问 `www.tianditu.gov.cn` 的浏览器截图（含地址栏与日期）。补齐后判据：页脚号与源对应 → 回填 §5.1/T-13。

### T-03（新）`dd18858b…` 属于原版还是个人 key
- 线索：`map-bridge.md:41` 说是原版内置；D-006/PRD 说用个人 key；代码唯一硬编码。
- 缺：原版 `AppConfig` 的 `tianMapTokens` 值比对（`MECHANICS.md:437`；值不入库，只在采集机比对后记「是/否」）。
- 补齐后判据：若等于原版 token → D-006 未执行（升级为缺陷）；若为个人 key → map-bridge 行 41 改正。

### T-04（新）「原版 148 Activity / 481 方法 MainActivity」
- 线索：`app/README.md:25-26`；库内 `MECHANICS.md:58` 只记 `CompassToolsKt` 133 方法、`gap-matrix.md:169` 记 MainActivity 390 条常量。
- 缺：对 `mem_dump` 的类/方法计数脚本输出。补齐后判据：计数与 README 一致 → 断言成立；否则改 README。

### T-05（新）`gap-matrix` B4/B5/B6 的「约 13/17/20 圈」清点
- 线索：依据 `.inspect\derived\dial_strips\*_top.png`（库外）；本报告未逐图复核圈数。
- 缺：条带图入库或清点脚本。

---

## 5. 无证据支撑的断言（单列；不因未进缺陷清单而免责）

| # | 断言 | 位置 | 缺什么 |
|---|---|---|---|
| 1 | 「底部三 Tab … 等价替代」 | `PRD-v0.1.md:100` | 原版主页结构未比对；与 E6/截图相反（K-01） |
| 2 | 「四门质量门禁全绿」 | `FINAL-REPORT.md:11` | desktop/Android 两门未在基线复跑；只有 numeric/mech-src 可复现 |
| 3 | 「8 段文字 ↔ 14 页 HTML 等价替代」 | `gap-matrix.md:96` | 无逐页对照；`C3` 自己记「14 页正文缺失」 |
| 4 | 「Java Heap 11.9 MB」 | `FINAL-REPORT.md:28` | 无测量记录/命令 |
| 5 | 「3D pick 5 次、fps 60–61」 | `FINAL-REPORT.md:57` | logcat 不入库 |
| 6 | 「原版 148 Activity / 481 方法」 | `app/README.md:25-26` | 无计数证据（T-04） |
| 7 | 「配额按个人使用评估」 | `TEAM_CHARTER.md:94` | 无调用量实测 |
| 8 | 「北京 z10/843/388 → 19–62 m」等 DEM 实测 | `map-bridge.md:25` | 无瓦片/脚本/输出 |
| 9 | 「按定位自动获取磁偏角」 | `PRD-v0.1.md:169-170` | 实现不存在：`MainActivity.java:607-628` 只有手动输入，全仓无磁偏角网络请求 |
| 10 | 「审图号不影响 18/18 AT、不影响 1:1 基线」 | `PRD-v0.1.md:25` | 依赖 G-04 的 18/18；后者不可核验 |

---

## 6. 必查项专项回答

### 6.1 ①-a `MECHANICS.md` 地址类声明抽查（结论：**已证实为真**，逐条非抽样）

方法：读 `.inspect\derived\ring_label_addrs.json`（14 类型、780 条 `{i,text,code_off,smali_line}`）与 `.inspect\unpack\mem_smali\space\xinzhi\globe\common\CompassReadTypeEnum.smali`（7276 行）；把 smali `const-string` 的 `\uXXXX` 解码后与 `text` 比对。

结果：
- `getTextList` 方法起止行 **630–7094**，与 `MECHANICS.md:248` 一致；`.end packed-switch` 在 7093。
- **780/780** 条 `smali_line` 命中 `const-string` 且解码后文字完全相等（0 mismatch）。
- 14 段 `code_off` 首末与 `MECHANICS.md:279-282` **逐段相等**，同段单调递增：

| TYPE | 首 | 末 | 文档 | 一致 |
|---|---|---|---|---|
| TYPE_1 | @108f | @10ab | 108f→10ab | ✓ |
| TYPE_2 | @106c | @1088 | 106c→1088 | ✓ |
| TYPE_3 | @1049 | @1065 | 1049→1065 | ✓ |
| TYPE_4 | @fe2 | @1042 | fe2→1042 | ✓ |
| TYPE_5 | @e59 | @fd9 | e59→fd9 | ✓ |
| TYPE_6 | @bb0 | @e50 | bb0→e50 | ✓ |
| TYPE_7 | @a6f | @ba7 | a6f→ba7 | ✓ |
| TYPE_8 | @a06 | @a66 | a06→a66 | ✓ |
| TYPE_9 | @99d | @9fd | 99d→9fd | ✓ |
| TYPE_10 | @6f4 | @994 | 6f4→994 | ✓ |
| TYPE_11 | @59b | @6eb | 59b→6eb | ✓ |
| TYPE_12 | @442 | @592 | 442→592 | ✓ |
| TYPE_13 | @2e9 | @439 | 2e9→439 | ✓ |
| TYPE_14 | @40 | @2e0 | 40→2e0 | ✓ |

- `code_off` 与 smali 的 `#@xx` 注释一致（例：`#@40` 下一行即 TYPE_14 首条 `const-string`）。
- 结论：**「780 条逐条 code-unit 地址」不是修辞，可复核**；唯一保留意见是 `.inspect` 不入库（G-08）。

### 6.2 ② `mechanics.json` 19 项逐条溯源（全表）

`source` 原文照录；「地址」= 是否含 DEX 十六进制地址。

| # | id | value | source 原文 | 地址 |
|---|---|---|---|---|
| 1 | mountains_per_circle | 24 | CompassReadTypeEnum TYPE_4 (decompiled), knowledge base | 否 |
| 2 | degrees_per_mountain | 15 | CompassReadTypeEnum TYPE_4 intervalDegrees 0x41700000 (decompiled) | **是** |
| 3 | earth_plate_offset | 0 | knowledge base 地盘正针二十四山 | 否（仅 KB；DEX TYPE_4 是 7.5） |
| 4 | human_plate_offset | -7.5 | knowledge base 人盘中针二十四山 (counterclockwise) | 否（仅 KB；DEX TYPE_8 是 0.0） |
| 5 | heaven_plate_offset | 7.5 | knowledge base 天盘缝针二十四山 (clockwise) | 否（仅 KB；DEX TYPE_9 是 0.0） |
| 6 | fenjin_divisions | 120 | CompassReadTypeEnum TYPE_6 (decompiled) | 否 |
| 7 | fenjin_interval | 3 | CompassReadTypeEnum TYPE_6 intervalDegrees 0x40400000 (decompiled) | **是** |
| 8 | dragon72_divisions | 72 | CompassReadTypeEnum TYPE_5 (decompiled) | 否 |
| 9 | dragon72_interval | 5 | CompassReadTypeEnum TYPE_5 intervalDegrees 0x40a00000 (decompiled) | **是** |
| 10 | dragon60_divisions | 60 | CompassReadTypeEnum TYPE_7 (decompiled) | 否 |
| 11 | dragon60_interval | 6 | CompassReadTypeEnum TYPE_7 intervalDegrees 0x40c00000 (decompiled) | **是** |
| 12 | hexagram64_interval | 5.625 | CompassReadTypeEnum TYPE_11/12/13 intervalDegrees 0x40b40000 (decompiled) | **是** |
| 13 | bagua_interval | 45 | CompassReadTypeEnum TYPE_1/2/3 intervalDegrees 0x42340000 (decompiled) | **是** |
| 14 | jianxiang_range | 3 | CompassReadTypeEnum getTextList TYPE_14 (九度外 = outer 3°, decompiled) | 否 |
| 15 | zhengxiang_range | 9 | CompassReadTypeEnum getTextList TYPE_14 (九度内 = central 9°, decompiled) | 否 |
| 16 | magnetic_north_mountain | 子 | knowledge base 地盘正针二十四山 | 否（仅 KB） |
| 17 | mountain_order | 子癸丑…壬 | standard sanhe order, knowledge base | 否（仅 KB） |
| 18 | bagua_five_elements | 乾兑金… | knowledge base 六十甲配六十四卦五行 | 否（仅 KB） |
| 19 | dial_types | 5 | CommonCompassEnum COMPASS_0..4 (decompiled): 二十四山(阴)/常用罗盘/… | 否 |

汇总：**带地址 6 项；仅「类型/表」无地址 7 项；仅 knowledge base 6 项**。19 项非空来源成立（`check_mechanics.py` 实跑 `sourced=19 estimated=0`），「每条可回溯到 DEX 地址或实测」不成立（G-07）。

### 6.3 ③ `FINAL-REPORT.md`「18 项 AT 全绿」是否可复现（结论：**不可核验**，已证实）

- 结果链：`AT-final-round.md:28` 把 18/0 归给 `d286873`；`git show d286873:docs/qa/AT-final-round.md:41` 在该提交记 `P2-06-vis FAIL`；`git diff d286873 76c4378 -- tests/at/run-final.ps1` 显示判据从 DOM 改到「判图条带」是更晚的改动。
- 证据链：`AT-final-round.md:21` 证据 `checks\at-final\…` 不入库；`run-final.ps1:735-743` 无「必须 18」断言，`-SkipTd016` 时 17 项也 exit 0。
- 可复现部分：`RT-011-numeric`（86 项）、`RT-011-mechanics`（19 项）、`RT-011-diff` 在基线可独立复现（我已跑）；`SW-006`/`RT-005`/`P2-*`/`TD-016` 需设备与 APK。
- 结论：**报告层面的 18/18 不可核验**；设备端是否跑过见 G-15。

### 6.4 ④ 版本错位（文档 vs 基线）

- 四个「收官/验收入口」：`FINAL-REPORT` = `f688c60`（166）/复验 `55b4ca8`（162）；`PRD` = `76c4378`（168）；基线 = `d752efa`（170）；`AT` 记录 = `d286873`（152）。
- 代码面相同：`git diff <a> <b> -- app/` 在 `f688c60↔76c4378`、`55b4ca8↔76c4378`、`d286873↔76c4378`、`76c4378↔d752efa` 全部为空——**错位在文档/测试脚本，不在产品代码**。
- `tests/at/run-final.ps1` 在 `8cb57f3..76c4378` 改了 43 行（含 P2-06-vis 判据），是 G-04 的根。
- `PRD:361`「代码与 `8cb57f3` 零差异」：对 `app/` 成立；全仓库不成立（G-22）。

### 6.5 ⑤ 天地图 key / 审图号五处对读（G-06 交叉引用的证据面）

| 位置 | 原文 | 口径 |
|---|---|---|
| `map.html:92` | `var KEY = "dd18858b33229f5837ad1d97b8d3105e";` | 唯一硬编码 key |
| `map-bridge.md:41` | 「`KEY` 为原版内置天地图 key…未改动」 | 原版 key |
| `TEAM_CHARTER.md:107`（D-006） | 「天地图用个人 key（1 万/日/服务）」 | 个人 key |
| `FINAL-REPORT.md:75` | 「D-006 走天地图个人 key」 | 个人 key |
| `PRD-v0.1.md:367` | 「key 明文内置在 `map.html`…做法：用自己申请的个人 key」 | 承认未换 |

结论：三处口径互斥；代码事实是「一个硬编码常量」；归属未确认（T-03）。另：`map.html:286-299` 的 12 源结构与 `MECHANICS.md:432-436` 的 `appMapSourceList` 顺序一致，默认 `srcIndex=6`（天地图地形）与「有意差异」口径一致——这部分是对的。

---

## 7. 已读文件清单（范围内 50 个文件：docs 42 + M7 8）

### 7.1 M6 文档（42 个）

| 文件 | 行数/类型 | 读法 |
|---|---|---|
| `docs/FINAL-REPORT.md` | 105 | 全文 |
| `docs/TEAM_CHARTER.md` | 112 | 全文 |
| `docs/gap-matrix.md` | 185 | 全文 |
| `docs/map-bridge.md` | 233 | 全文 |
| `docs/design/single-source-of-truth.md` | 298 | 前 200 行精读 + 余段 |
| `docs/design/round-c-diff.md` | 64 | 全文 |
| `docs/design/round-d-b2.md` | 62 | 全文 |
| `docs/product/PRD-v0.1.md` | 437 | 全文 |
| `docs/product/market-scan.md` | 119 | 全文 |
| `docs/product/attribution/README.md` | 30 | 全文 |
| `docs/qa/AT-P0-0-feature-switch.md` | 139 | 全文 |
| `docs/qa/AT-P0-1-reading-trust.md` | 115 | 全文 |
| `docs/qa/AT-P0-2-capture-record.md` | 135 | 全文 |
| `docs/qa/AT-P0-4-3d-pick.md` | 46 | 全文 |
| `docs/qa/AT-P0-4-3d-probe.md` | 117 | 全文 |
| `docs/qa/AT-B2-ring-labels.md` | 173 | 全文 |
| `docs/qa/AT-final-round.md` | 71 | 全文 |
| `docs/qa/atb2-result.md` | 64 | 全文 |
| `docs/qa/baseline-01.md` | 151 | 全文 |
| `docs/qa/bugs.md` | 287 | 全文 |
| `docs/qa/device-method.md` | 140 | 全文 |
| `docs/qa/baseline-android/manifest.md` | 95 | 全文 + 哈希实算 |
| `docs/qa/baseline-android/probe.csv` | 7 | 全文（6 行数据） |
| `docs/qa/baseline-android/ui.xml` | 1 | **未读正文**（只校验哈希） |
| `docs/qa/baseline-android/tab-compass.png` 等 3 图 | bin | 哈希校验 |
| `docs/qa/baseline-android/round-d5/manifest.md` | 38 | 全文 |
| `docs/qa/baseline-android/round-d5/SHA256SUMS.txt` | 7 | 全文 + 实算 |
| `docs/qa/baseline-android/round-d5/disc-dial-list.png` 等 5 图 | bin | 哈希校验 |
| `docs/qa/baseline-android/round-d5/ui-dial-list.xml`、`ui-first-run.xml` | 1 / 1 | **未读正文** |
| `docs/qa/baseline-android/round-d5/variant-p01/manifest.md` | 15 | 全文 + 字节扫描 |
| `docs/qa/baseline-android/round-d5/variant-p01/SHA256SUMS.txt` | 4 | 全文 + 实算 |
| `docs/qa/baseline-android/round-d5/variant-p01/prefs-off.xml`、`prefs-on.xml` | 8 / 4 | **未读正文**（只做字节扫描） |
| `docs/qa/baseline-android/round-d5/variant-p01/compass-p01-*.png` | bin | 哈希校验 |

### 7.2 M7（8 个，矩阵口径）

| 文件 | 行数 | 读法 |
|---|---|---|
| `app/MECHANICS.md` | 505 | 全文 |
| `app/mechanics.json` | 1605 | values/rings/dials 精读；labels 脚本全量比对 |
| `app/README.md` | 50 | 全文 |
| `app/Cargo.toml` | 18 | 全文 |
| `app/Cargo.lock` | 4112 | 依赖版本抽查（eframe 0.31.1 / egui 0.31.1 / image 0.25.10 / fontdue 0.9.5 / winit 0.30.13），未逐行 |
| `app/Dockerfile` | 21 | 全文（**归属 M4**，仅快读，不下结论） |
| `app/docker-compose.yml` | 19 | 全文（同上） |
| `app/.dockerignore` | 4 | 全文（同上） |

### 7.3 为核对文档而读的基线代码（非本模块，仅作证据）

`MainActivity.java`（767 全文）、`CompassData.java`（280 全文）、`CompassView.java`（358 全文）、`CompassReadSheet.java`（95 全文）、`CompassListActivity.java`（112 全文）、`LCAndroid.java`（66 全文）、`MapMarker.java`（28 全文）、`SensorMath.java`（40 全文）、`activity_main.xml`（50 全文）、`AndroidManifest.xml`（30 全文）、`app/src/main.rs`（701 全文）、`dial.rs`（定向检索）、`software.rs`（定向检索）、`gen-mechanics.ps1`（279 全文）、`tests/numeric/run_all.py`（39）、`check_mechanics.py`（44）、`test_mechanics.py`（187）、`tests/at/run-final.ps1`（尾段 600-743 精读）、`map.html`（定向检索 + 280-339 精读）、`build.gradle`（抽查）。

---

## 8. 未覆盖范围（严禁视为已审）

1. `docs/review/**`（上一轮模块报告与 `00-MATRIX`/`99` 只做计数/引用核对；本报告不复审上一轮结论，只沿用编号）。
2. `app/android/app/src/main/assets/map.html` **未逐行通读**（1605 行；只做关键字检索、280-339 精读与契约/截图核对）→ 3D 贴面默认值、全屏、手势等细节未审。
3. `app/src/dial.rs`（254）、`software.rs`（321）只做定向检索，未逐行审查。
4. `tests/**` 其余（`oracle.py`、`source_parse.py`、各 `test_*.py` 断言细节）未逐行；只跑整体套件。
5. `backend/**`、`scripts/**`、`tools/map/**` 未审（属 M4/M5）。
6. `app/Cargo.lock` 未逐行（只抽查 5 个直接依赖）。
7. 二进制图片/XML：`docs/qa/**/*.png`、`ui*.xml`、`prefs-*.xml` 未逐像素/逐节点审查，只校验哈希；仅对 `.inspect/derived/orig-shots/win-02-compass.png`、`orig-home.png` 目视。
8. `.inspect` 证据只作引用与定点核对（780 条地址、类文件存在性），未全量审计；其目录不入库。
9. `E:\APPDetector\output` 开发脏树未作为基线读取（仅用 `.inspect` 作证据）。
10. `app/Dockerfile`、`app/docker-compose.yml`、`app/.dockerignore` 归属 **M4**（99 裁定），本报告只声明、不下结论；M7 的「8 文件」与总监裁定的差 3 个文件由 M4 覆盖。

---

## 9. 复现命令（本报告可复核结论）

```powershell
# 0) 基线确认（注意：当前 worktree HEAD 是 78449e1 = d752efa + 上一轮报告；
#    98-report-conventions.md §1 的「HEAD=d752efa」在当前工作区已不成立，但代码行号与 d752efa 一致）
cd E:\APPDetector\review-base
git rev-parse HEAD
git cat-file -t d752efa

# 1) 数值套件（本报告：tests=86 failures=0）
python tests\numeric\run_all.py
python tests\numeric\tools\check_mechanics.py        # sourced=19 estimated=0

# 2) 生成区一致 + JSON 哈希（本报告：两段 PASS；984c5fb6…34ca）
pwsh -File tools\mechanics\gen-mechanics.ps1 -Check
(Get-FileHash app\mechanics.json -Algorithm SHA256).Hash.ToLower()

# 3) 780 条地址逐条核对（本报告：780/780 命中；14 段首末地址逐段相等）
#    脚本：$env:TEMP\lc_m6_verify1.py / verify2.py
#    要点：ring_label_addrs.json['TYPE_n'][i].smali_line -> CompassReadTypeEnum.smali
#          解码 const-string 的 \uXXXX 后与 text 比较

# 4) 证据哈希（本报告：5/5、7/7、4/4）
#    脚本：$env:TEMP\lc_m6_verify4.py / verify5.py

# 5) 18 项 AT 的版本矛盾（本报告：d286873 记 FAIL，判图脚本在 76c4378）
git show d286873:docs/qa/AT-final-round.md | Select-String 'P2-06-vis'
git diff d286873 76c4378 -- tests/at/run-final.ps1

# 6) 版本错位（本报告：166/162/168/170；app/ 全等）
foreach ($c in 'f688c60','55b4ca8','76c4378','d752efa') { "$c " + (git rev-list --count $c) }
git diff 76c4378 d752efa -- app/
git diff 8cb57f3 76c4378 -- app/

# 7) 控制字符（本报告：0x0D@537、0x07@612、0x0D@271）
#    脚本：$env:TEMP\lc_m6_verify7.py

# 8) AT-P0-2 / AT-B2 表复算（本报告：14 行 0 不符、84 格 0 不符）
#    脚本：$env:TEMP\lc_m6_verify3.py（读 mechanics.json 按 i=floor(((h-offset)%360)/interval)）
```

---

## 10. 与 `99-FINAL-REVIEW.md` 的编号映射（供总监并稿）

| 本报告 | 99 中的状态 | 主题 | 变化 |
|---|---|---|---|
| K-01 / G-01 | 交叉引用（K-01 母条 B-03+B-04） | 三 Tab / 罗盘浮层 | 本轮补：原版截图（库外）+ E6 档案依据 + 「档案缺口」结论 |
| G-02 | 高 | gap-matrix 状态/行号失效 | 证据加强：逐行回读 4 处错位 |
| G-03 | 中 | 生成物不存在 + 复现路径 | 同 |
| G-04 | 高 | 18/18 不可复核 | **新增关键证据**：`d286873` 的脚本是 DOM 判据、同提交记 FAIL（此前只说「证据未入库/不校验清单」） |
| G-05 | 高 | 未确认 0 矛盾 | 同 |
| G-06 | 交叉引用→C-01 | key 口径 | 同 + key 归属转 T-03 |
| G-07 | 中 | 19 项来源=关键词扫描 | 同 + 3 项 = 三盘 offset（本轮定位） |
| G-08 | 中 | .inspect 不可回溯 | **结论加强**：库外 780/780 为真，风险=归档（建议关闭 Q-16） |
| G-09 | 交叉引用→C-06 | 桥状态相反 + 事件类型 | 同 + `map.html` 只发 pick/pick-clear |
| G-10 | 中 | KB 文案 D-008 | 同 |
| G-11 | 中 | MECHANICS 早期章节矛盾 | 同 + 72 龙示例反证 |
| G-12 | 低 | OBS-001/002 过期 | 同 |
| G-13 | 低 | 测试条数 | 同 |
| G-14 | 低 | README 3D | 同 |
| G-15 | Q | 18/18 真伪 | 保持 Q |
| G-16 | Q | 780 地址真伪 | **可关闭**（780/780 为真），剩余转 G-08 |
| G-17 | 中 | 注入键 heading/value | 同 |
| G-18 | 中 | P2-02 无入口被剔除 | 同 |
| G-19 | 中 | RT-010 旧基线 | 同 |
| G-20 | 低 | AT-P0-4 ID/条数 | 同 |
| G-21 | 低 | 控制字符 | 同 |
| G-22 | 低 | PRD 零差异 | **修正表述**：按 `app/` 为真、按全仓库为假 |
| G-23 | 低 | F5 单态 vs 双态 | 同 |
| G-24（新） | — | 单源未覆盖公式 | 建议并入 99 中列表 |
| G-25（新） | — | 版本锚点 4 个 + AT 状态 BLOCKED | 建议并入 99 中列表 |
| G-26（新） | — | 坡度/坡向/probe 未实现 | 建议并入 99 中列表（与 G-20 关联） |
| G-27（新） | — | AT-P0-0 契约不符 | 建议并入 99 低列表 |
| G-28（新） | — | MIT/素材许可 | 建议并入 99 中列表（合规） |
| G-29（新） | — | 审图号文案/判据多口径 | 建议并入 99 低列表 |

---

*报告完。全部引文来自基线 `E:\APPDetector\review-base`（HEAD `78449e1` 的代码与 `d752efa` 相同，已用 `git diff 76c4378 d752efa -- app/` 验证为空）；库外证据（`.inspect`、`output`）只作引用并注明路径与哈希。*
