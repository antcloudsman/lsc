# LC 审查 · 成文规范与编号索引（准备稿 v0.1）

> **性质：非交付物。** 本文件只定义 `99-FINAL-REVIEW.md` 的格式、编号、证据索引与一致性校验规则，并预填 K-01（用户直接提出的已知缺陷）底稿。
> **不含技术判断、不定级、不裁决。** 定级与修复方向由技术总监在模块报告齐后裁决；成文时本文件的规则并入 99，K-01 补全后进 99「已知缺陷清单」。
> 基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`），工作区 `E:\APPDetector\review-base`。

---

## 1. 基线核验（事实）

```
$ git -C E:\APPDetector\review-base rev-parse HEAD
d752efaf7936f887a70577681c703497e74e8b57
$ git -C E:\APPDetector\review-base tag --points-at HEAD
review-20261105
$ git -C E:\APPDetector\review-base status --porcelain
?? docs/review/
```

结论：HEAD 与审查基线一致，tag 指向同一提交；除本组待落盘的 `docs/review/` 外工作区干净。**报告引用行号一律以该 HEAD 的文件为准，不接受任何分支/工作区的行号。**

补充事实（影响证据索引，供总监与 LC审文档 核对）：`docs/qa/baseline-android/` 下的「基线截图」是 **LC 自己的 APK** 采集，不是原版 APK：

`docs/qa/baseline-android/manifest.md` L15-L15：

```markdown
| App | `com.luopan.fengshui`，versionName **1.0** / versionCode **1** |
```

`docs/qa/baseline-android/manifest.md` L77-L77：

```markdown
| HEAD | `ee1330a`（分支 `feature/地图-3d-terrain`） |
```

→ 该目录可作「LC 自身回归对照基线」，**不可作原版布局证据**；K-01 的档案依据只能取 `gap-matrix.md` / `MECHANICS.md` / `.inspect` 引用链。

---

## 2. 编号体系（预分配）

| 前缀 | 模块 | 来源报告（相对 `docs/review/`） | 编号区间 |
|---|---|---|---|
| `A` | M1 桌面客户端 | `10-M1-desktop.md` | A-01…A-99 |
| `B` | M2 Android 客户端 | `11-M2-android.md` | B-01…B-99 |
| `C` | M3 地图/3D | `20-M3-map.md` | C-01…C-99 |
| `E` | M4 服务端/基建 | `30-M4-server.md` | E-01…E-99 |
| `F` | M5 测试资产 | `40-M5-tests.md` | F-01…F-99 |
| `G` | M6+M7 文档/数值档案 | `50-M6-docs.md` | G-01…G-99 |
| `K` | 已知缺陷（用户/总监提出，非本轮新发现） | 99 内 | K-01…K-99 |
| `R` | 风险与改进建议（无缺陷证据的改进项） | 99 内 | R-01…R-99 |
| `Q` | 待确认（高度可疑/待确认） | 99 内 | Q-01…Q-99 |
| `EV` | 证据索引 | 99 内 | EV-001…EV-999 |

规则：

1. 前缀固定，**不允许跨模块借用**（M6 与 M7 共用 `G`，顺序按报告出现次序）。
2. 两位序号，一经总监签字不再复用；删条目保留空号并在证据索引注明「已撤，原因」。
3. 同一事实被多份模块报告覆盖时，取**最直接证据**的一条为母条，其余在证据索引写「见 A-03」，不重复编号。
4. `A/B/C/E/F/G` 与 `K/R/Q` 互不混编：`K` 不进正式缺陷清单；`R` 不进缺陷清单；`Q` 不进缺陷清单。
5. 缺陷清单内部编号连续，但**按 阻断→高→中→低 分组重排展示**，编号本身不因分级变化而改。
6. 编号、文件行号、证据索引三者必须能互查：99 中每条 → EV 表至少 1 行 → 回到基线原文。

---

## 3. 判据口径（照抄总监令，供逐条校验）

| 等级 | 口径 |
|---|---|
| 阻断 | 主路径功能错误 / 数据损坏 / 安全漏洞 |
| 高 | 主路径错误或明确安全风险 / 契约破裂 |
| 中 | 边缘场景或非主路径错误 |
| 低 | 可维护性、日志、文案 |

| 证据等级 | 判定 | 落点 |
|---|---|---|
| 已证实 | 有代码路径或可复现推演 | 缺陷清单 |
| 高度可疑 | 有代码依据但触发条件未完全确认 | 待确认清单（Q） |
| 待确认 | 线索不足 | 待确认清单（Q） |

映射硬规则：**只有「已证实」可进缺陷清单**；「高度可疑 / 待确认」一律进 Q 并写「缺什么证据、由谁补、补齐后的判据」。无代码证据的改进意见进 R，不得伪装成缺陷。

---

## 4. `99-FINAL-REVIEW.md` 章节模板（顺序固定，八节齐全）

```
# 山水罗盘 LC · 全量源码审查报告（终稿）

## 1 审查概述
   基线提交/标签、工作区、文件总数 204、模块 M1–M8 与审查人、审查轮次与批次、
   本报告与 6 份模块报告的对应关系、总监裁决日期与签字位。
   必须写：本报告条目总数（缺陷 x / 已知 x / 风险 x / 待确认 x）与证据索引行数。

## 2 缺陷清单（阻断 / 高 / 中 / 低 分组）
   每组先给该组条目数，再逐条：编号·标题·文件行号·代码片段·触发条件·预期vs实际·证据等级·修复方向。

## 3 已知缺陷清单
   K-01…；每条含：来源（用户/总监/既有台账 BUG-xxx）、文件行号、代码片段、
   触发条件、预期vs实际、现状定级（总监签）、修复方向、与正式缺陷条目的关系（是否合并/关联）。

## 4 风险与改进建议清单
   R-01…；每条含：问题描述、证据（代码/文档行号）、风险面、建议动作、优先级。
   本清单不出「等级」，只标优先级 P0/P1/P2。

## 5 待确认清单
   Q-01…；每条含：线索、已掌握证据（文件行号+片段）、缺什么证据、由谁补、
   补齐后的判定条件（PASS/FAIL 口径）。

## 6 未覆盖范围声明
   M1–M7 逐模块「已覆盖/部分覆盖/未覆盖」+ 原因；M8 例外；二进制/图片例外；
   基线外分支与未提交改动例外；每项写明由谁在何时补。

## 7 证据索引
   表：EV 编号 | 关联编号 | 文件（相对 review-base） | 行号 | 片段摘要（≤40 字） | 核验状态。

## 8 附录
   8.1 已读文件清单（文件 + 行数 + 模块）
   8.2 模块报告与编号映射表
   8.3 审查批次与时间线
```

硬性要求：八节标题齐全；每节先给计数；任何「见附件」「见模块报告」都必须给出可回溯的编号或路径，不得只写口头引用。

---

## 5. 单条缺陷的最小字段（缺一即退回）

| 字段 | 规范 |
|---|---|
| 编号 | 见 §2；与证据索引一致 |
| 标题 | 一句话，含对象与失效点，不含「可能」 |
| 文件行号 | 相对 `review-base` 的路径 + `L起-L止`；行号不得超出该文件总行数 |
| 代码片段 | 基线原文**逐字**，保留缩进；一次引用最多 15 行，超长只截一个语义完整区间 |
| 触发条件 | 输入/状态/调用路径，至少一项能落到具体函数或事件入口 |
| 预期 vs 实际 | 分两行写；预期必须能指向原版行为、档案条目或契约文档 |
| 证据等级 | 已证实 / 高度可疑 / 待确认；与所在清单匹配 |
| 修复方向 | 动作式：改哪个文件哪个函数、加什么约束或判据；禁止「建议优化」「可考虑」 |

片段规范：

1. 引用多段时每段单独标注行号，不使用「…」拼接跨段内容。
2. 片段中不得改写原文（含全角/半角、零宽字符、`U+200C` 这类不可见字符须原样保留）。
3. 若原文含制表符/中文注释，照抄；判据用 git 原生命令核对，不走管道拼中文。
4. 一条发现的证据跨多个文件时，主片段给最直接失效点，其余进证据索引。

---

## 6. 已知缺陷清单 · K-01 底稿（用户直接提出）

**来源**：用户在项目群直接提出（原话）：

> 底部菜单里「罗盘」与「地图」其实是同一个页面的不同表现：参照原 APK 首页布局，罗盘应是点击「罗盘」按钮后浮动叠加在地图中心，配合地图模拟真实罗盘场景；整个底部菜单栏没有存在意义。要遮挡页面的组件与子页，应依原 APK 的设计逻辑放在合适位置、用合适的呈现方式。

**状态**：底稿。定级、修复方向、与 A/B/C 条目的合并关系**均待技术总监裁决**；本节只摆基线代码事实与档案依据，不做技术结论。

### 6.1 现状（基线代码事实）

（1）底部固定三按钮，常驻布局

`app/android/app/src/main/res/layout/activity_main.xml` L14-L30：

```xml
    <LinearLayout
        android:id="@+id/bottom_bar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:background="@color/panel"
        android:paddingTop="6dp"
        android:paddingBottom="6dp">

        <Button
            android:id="@+id/tab_compass"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="@string/tab_compass"
            android:textColor="@color/text_primary"
            android:background="@android:color/transparent" />
```

（另两按钮：`tab_map` L32-L39、`tab_knowledge` L41-L48；按钮文案见下。）

`app/android/app/src/main/res/values/strings.xml` L4-L6：

```xml
    <string name="tab_compass">罗盘</string>
    <string name="tab_map">地图</string>
    <string name="tab_knowledge">知识</string>
```

（2）三页面一次建好、同塞一个 `FrameLayout`，默认选第 0 个

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L66-L66：

```java
        setContentView(R.layout.activity_main);
```

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L79-L90：

```java
        compassTab = buildCompassTab();
        mapView = buildMapTab();
        knowledgeTab = buildKnowledgeTab();

        content.addView(compassTab);
        content.addView(mapView);
        content.addView(knowledgeTab);

        tabCompass.setOnClickListener(v -> showTab(0));
        tabMap.setOnClickListener(v -> showTab(1));
        tabKnowledge.setOnClickListener(v -> showTab(2));
        showTab(0);
```

（3）切页＝三选一显隐，罗盘与地图是两条互斥页面

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L630-L634：

```java
    private void showTab(int index) {
        compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }
```

（4）罗盘页本身已是「底图 WebView + 罗盘浮层」，但浮层贴顶部居中、且与地图页各持一个 WebView

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L262-L274：

```java
        // Base map (底图). Keep the reference: 轮 C 截图与坐向推送都指向这个 WebView.
        compassMapView = createMapWebView();
        frame.addView(compassMapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Floating compass overlay (罗盘盖在底图上).
        compassView = new CompassView(this);
        compassView.setOnHeadingChangeListener(h -> updateReading());
        int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
        cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        frame.addView(compassView, cp);
```

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L567-L569（地图页＝裸 WebView）：

```java
    private WebView buildMapTab() {
        return createMapWebView();
    }
```

（5）同一罗盘状态推两份 WebView 实例

`app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java` L677-L686：

```java
        // Push heading + sit/face to BOTH map WebViews: the compass tab's base map
        // (B-17) and the map tab. Injection goes through this same path, so
        // `am broadcast ... heading` must reach the map's bearing (坐南朝北).
        String face = CompassData.MOUNTAINS[r.mountain] + "山";
        String sit = CompassData.MOUNTAINS[r.sitting] + "山";
        for (WebView wv : new WebView[]{compassMapView, mapView}) {
            if (wv == null) continue;
            wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
            wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
        }
```

### 6.2 档案依据（原版首页布局）

（a）原版罗盘页控件清点：无底部三 Tab；「地图」是页面内右列按钮

`docs/gap-matrix.md` L121-L121：

```markdown
| E6 | 罗盘显示开关 | **原版没有「圈层显示开关」**：真机罗盘页控件已全量清点（`win-02-compass.png`/`left-column.png`）——左列仅 截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地，右列仅 地图/实景/**坐向朝北开关（标签在 坐北朝南↔坐南朝北 间切换，见 F5）**/指针开关，底栏仅 切换/旋转/缩放/透明度/**天支线**；`天支线` 是十二支线圈层，不是圈层开关。盘面切换走「切换」页（B1b） | 桌面 `ShowRings{earth,human,heaven,fenjin,dragon72,dragon60,bagua}`（`main.rs:210-232`）为用户可关；Android 无开关 | 有意省略（1:1 之外的自创功能，保留不影响基线） | 桌面 7 个开关是**重建自创**，原版无对应控件；列入「超出 1:1 的增量」而不是缺口。若要求严格 1:1，默认全开即可（当前默认即全开） | 无（文档口径，勿删功能） |
```

（b）原版罗盘页左列按钮真机坐标（全屏页面 + 浮动按钮列）

`app/MECHANICS.md` L466-L468：

```markdown
**行位置（真机截图坐标，1228×2700，`win-02-compass.png` 左列）**：截屏 y≈1340 / 等高线 1500 /
罗盘复位 1690 / 罗盘锁定 1880 / **读盘 2070** / 贴地 2250，按钮中心 x≈132。
（上一轮 30 分钟窗口两次点 2070 之外（1957/2196）→ 弹层没打开，**不是功能缺失**，记录纠正。）
```

（c）原版知识入口是「点圈层文字直达」，不是底部 Tab

`docs/gap-matrix.md` L97-L97：

```markdown
| C2 | 入口 | 点圈层文字 → `CompassToolsKt.a1(BaseActivity, boolean, CompassReadTypeEnum)`（`CompassToolsKt.smali:1539`）→ `CompassIntroduceActivity$a.a(Context, CompassReadTypeEnum)`（`:1568`），用枚举 `html` 字段（如 `地盘正针二十四山.html`）加载 | Android 知识 Tab 静态列表；桌面左侧 knowledge_panel 静态 | 等价替代 | 原版「从圈层直达对应页」，重建只能从 Tab 翻 | 客户端：读数行点击 → 对应 KB 条目 |
```

（d）PRD 自认「原版主页结构（未逐页比对）」——三 Tab 无逐页比对证据

`docs/product/PRD-v0.1.md` L100-L100：

```markdown
| B-19 | 底部三 Tab：罗盘 / 地图 / 知识库 | `activity_main.xml` + `showTab()` | 原版主页结构（未逐页比对） | 等价替代 | 低 |
```

### 6.3 K-01 待补项（成文时由总监裁决填入）

| 字段 | 现状 |
|---|---|
| 定级（阻断/高/中/低） | 待定——A/B/C 三份报告中该缺陷的定级与依据须一致，冲突由总监裁决 |
| 修复方向 | 待定——由 LC审客户端（UI 结构）、LC审地图（浮层叠加与单 WebView）分别给出动作式方案 |
| 与正式缺陷条目关系 | 待定——若 A/B/C 已产出同事实条目，K-01 保留为母条并交叉引用，不重复计数 |
| 验收判据 | 待定——须给可判定条件（如：无底部三 Tab；罗盘浮层中心与地图中心误差；单 WebView 实例数） |

> 说明：K-01 不因「已知」而豁免证据要求；它的现状代码事实与档案依据已在 6.1/6.2 落齐，缺的只有定级与修复方向。

---

## 7. 证据索引（模板）

表头固定：

```markdown
| EV | 关联编号 | 文件（相对 review-base） | 行号 | 片段摘要（≤40 字） | 核验状态 |
```

规则：

1. 每个 `A/B/C/E/F/G/K` 编号至少 1 行 EV；1 条发现对应多段证据时给多行（1:N），编号不变。
2. 「关联编号」写清是母条还是交叉引用（如 `见 B-07`）。
3. 「核验状态」三态：`未核` / `已核（逐字一致）` / `退回（不一致）`。**只有「已核」允许出现在 99 终稿**。
4. 核验方法（成文前逐条执行）：`read` 目标文件对应行区间，与报告片段逐字比对（含缩进、全角、不可见字符）；行号超出文件总行数直接判退回。
5. EV 表按编号排序，不按文件排序；同一文件多行不合并。

---

## 8. 成文前一致性校验清单（我逐项过，任一项不过即退回总监）

1. 六份模块报告齐全：`10-M1-desktop.md`、`11-M2-android.md`、`20-M3-map.md`、`30-M4-server.md`、`40-M5-tests.md`、`50-M6-docs.md`。
2. 编号唯一、前缀与模块匹配、无空号跳号（撤条除外，且注明原因）。
3. 每条缺陷三件套齐全：路径 + 行号 + 原文片段；行号未越界。
4. 片段与基线原文逐字一致（含缩进/全角/`U+200C`）。
5. 证据等级 ∈ {已证实, 高度可疑, 待确认}；只有「已证实」出现在缺陷清单。
6. 每条缺陷都有触发条件，且能落到具体函数/事件/输入。
7. 「预期 vs 实际」两行齐全，预期可回溯到原版行为/档案/契约文档。
8. 严重等级 ∈ {阻断, 高, 中, 低}；与总监裁决一致。
9. 模糊词扫描：`可能存在` / `建议检查` / `一般来说` / `似乎` / `大概` / `建议优化` / `可考虑` 一律退回。
10. 待确认清单每条写清：缺什么证据、由谁补、补齐后的判据。
11. 未覆盖范围声明覆盖 M1–M8 全部模块，且与 00-MATRIX 的模块表逐行对应。
12. 计数自洽：缺陷清单各级小计之和 = 缺陷总数；K/R/Q 计数与条目一致；EV 行数 ≥ 条目数（1:N 允许）。
13. 跨报告同一事实不重复编号；交叉引用可回溯。
14. 报告内所有中文为 UTF-8 直写；判据用 git 原生命令，不走管道拼中文。

---

## 9. 待确认清单（Q）字段模板

每条必须有：

- `Q-xx` 标题
- 线索（代码/文档位置）
- 已掌握证据（文件行号 + 片段；可链接到 EV）
- **缺什么证据**（具体到「哪个运行态/哪条命令/哪份原版资产」）
- **由谁补**（模块审查人/LC测试/LC逆向）
- 补齐后的判定条件（写明 PASS 与 FAIL 各是什么样）

缺任意一项 → 退回。

---

## 10. 未覆盖范围声明模板

```markdown
## 未覆盖范围声明

| 范围 | 状态 | 原因 | 补做安排 |
|---|---|---|---|
| M1 桌面客户端（3 文件） | 见 10-M1-desktop.md | … | … |
| M2 Android（25 文件） | … | … | … |
| M3 地图/3D（3 文件） | … | … | … |
| M4 服务端/基建（26 文件） | … | … | … |
| M5 测试资产（22 文件） | … | … | … |
| M6 文档/契约（42 文件） | … | … | … |
| M7 数值档案/构建（8 文件） | … | … | … |
| M8 研究工件（75 文件） | 不审，仅盘点 | 00-MATRIX §4 | — |
| 二进制/图片（`res/drawable*`、`*.png`） | 仅资源一致性 | 00-MATRIX §4 | — |
| 基线外分支/未提交改动 | 不在范围 | 00-MATRIX §4 | — |
```

模块状态只允许三档：`已覆盖` / `部分覆盖（列缺口）` / `未覆盖（写原因）`。禁止用「已审查」笼统带过。

---

## 11. 模块报告回传字段（我验收格式用）

每份模块报告必须含：

1. 报告头：模块、审查人、基线提交、已读文件数/清单（文件+行数）。
2. 缺陷条目（三件套 + 触发条件 + 预期vs实际 + 证据等级 + 修复方向）。
3. 高度可疑/待确认条目（按 §9 字段）。
4. 模块必查项逐项结论（总监令里点名的每条必查项，写「命中/未命中 + 证据」）。
5. 已读文件清单 + 行数 + 覆盖声明（未读必须写未覆盖）。
6. 本批条目计数：已证实 / 高度可疑 / 待确认 各几条。

缺 1/5/6 任一 → 退回；条目缺字段按 §5 逐条退回。

---

## 12. 成文流程与退回规则

流程：

1. 六份模块报告落盘（第一批「阻断/高」优先）。
2. 技术总监裁决：定级、合并、剔除、K-01 定级与修复方向。
3. 我做格式/编号/索引/一致性校验（§8 全部条目），逐条核片段。
4. 生成 `99-FINAL-REVIEW.md`（§4 八节）。
5. 总监抽样追溯通过 → 签字；未通过 → 按退回清单返工。

退回情形（不逐条商榷，整份退回）：

- 三件套缺失、行号越界、片段与原文不符；
- 「已证实」进缺陷清单但无触发条件/无代码路径；
- 缺陷清单出现「可能存在/建议检查/一般来说」；
- 已知缺陷 K-01 被降格为「风险」或「建议」；
- 未覆盖声明缺失或与实际阅读范围不符；
- 编号重复/越界/跨模块借用；
- 同一事实重复编号且无交叉引用。
