# 50-M6 文档与数值档案审查（M6 文档/契约 + M7 数值档案/构建）

- 审查基线：`develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`），工作区 `E:\APPDetector\review-base`
- 审查范围：`docs\**` 全部 42 个文件、`app\MECHANICS.md`、`app\mechanics.json`、`app\README.md`、`app\Cargo.toml`、`app\Cargo.lock`
- 交付口径：本文件含第一批（G-01…G-14）与第二批（G-17…G-22）；其中 **G-01→B-03+B-04、G-06→C-01 已按总监 2026-11-05 裁定转交叉引用**，不计入 M6 正式数；G-15/G-16 为待确认。docs 42/42 文件触碰。
- 证据纪律：所有行号均回 `review-base` 现场确认；代码片段为原文；未读到的文件一律列入 §7「未覆盖范围」

---

## 0. 结论摘要（先给早期信号）

1. **数值档案本身是自洽的，且可复算**：`mechanics.json` 14 圈层共 **780 条** label，逐圈条数 `[8,8,8,24,72,120,60,24,24,120,64,64,64,120]` 与 `MECHANICS.md` R1.2 完全一致；`source` 里 **28 个 IEEE-754 十六进制常量全部解码正确**（0 处不符）；用独立脚本按 `getFaceText` 公式复算 `MECHANICS.md` R2.1 的 **14 圈 × 6 方位 = 84 格**，**0 处不符**。基线跑 `tests\numeric\run_all.py` = **86 tests PASS**，`gen-mechanics.ps1 -Check` = **PASS**，`check_mechanics.py` = `values=19 sourced=19 estimated=0`。这几项是「已证实成立」，不是缺陷。
2. **但验收/矩阵文档与基线代码大面积脱节**：`gap-matrix.md` 的「当前实现」列仍停在 `feature/lc-server-infra` 脏树（文件自述如此），A2/A3/A4/A7/B1/B7/D1 与 §H 的 P0-1…P0-5 仍写「缺失/待修」，而基线这些功能已经落地。`FINAL-REPORT.md` §4 又原样引用这组统计，于是同一套文档里「1:1 已闭环」与「缺失 24 项」并存。
3. **收官结论不可从基线复核**：`18/18 PASS` 的证据目录 `checks\at-final\` 被 `.gitignore` 忽略，204 个跟踪文件里没有任何结果表/截图/日志；`run-final.ps1` 的 `exit 0` 只保证 `fail=0 && blocked=0`，不校验「18 个用例是否都执行过」。
4. **「未确认 0 / 无一条说不清」是假统计**：同一份 `gap-matrix.md` 的 B8c、D4、J 段明写「算法未取证」「仍未取证」。这条与 §0 的收官口径直接冲突。
5. **原版布局档案只支持「罗盘叠加在地图上」，不支持「三 Tab」**：`MECHANICS.md:120-126/138-139` 证明原版是 WebView 地图 + Compose 罗盘覆盖层；`gap-matrix.md:121` 真机清点原版底栏是「切换/旋转/缩放/透明度/天支线」工具条，不是页面导航。基线 `activity_main.xml` 的三 Tab 是重建自创、且未在矩阵登记（只在 `PRD-v0.1.md:100` 以「原版主页结构（未逐页比对）」登记成「等价替代」——无证据）。用户提出的罗盘浮层缺陷**成立**，但「浮在地图中心」的具体几何档案未取证，需补证后再写死。

本批条目统计（按总监 2026-11-05「正式条目＝母条，交叉引用单列」口径）：**正式 18 条（高 3 / 中 9 / 低 6）；高度可疑 0 条；待确认 2 条（G-15、G-16）；交叉引用 2 条（G-01→B-03+B-04、G-06→C-01，不计入正式数）**。

第一批 12 条正式 + 第二批新增 6 条正式；G-01/G-06 按裁定转交叉引用，证据保留但不再单独计数。另有 16 条无证据断言（§4 单列）；交叉引用共 8 条（G-01、G-06 + §2.5 的 6 项）。

---

## 1. 条目清单（正式 18 + 交叉引用 2；按编号保留全部证据）

> 标「交叉引用」的 G-01、G-06 不计入正式数；其余 G-02…G-05、G-07…G-14、G-17…G-22 为正式条目。

### G-01【交叉引用→B-03+B-04；高｜不计入 M6 正式数】底部三 Tab 与原版首页结构不一致；罗盘浮层在顶部且遮图，地图页重复一份 WebView

> 计数口径：母条 B-03+B-04（M2，总监 2026-11-05 裁定）；本条只提供文档/首页结构面的证据，不单独计入 M6 正式数。

**现状（基线代码）**

- `app/android/app/src/main/res/layout/activity_main.xml:14-49`：底部导航三按钮
  ```xml
  <LinearLayout
      android:id="@+id/bottom_bar"
  ...
      <Button
          android:id="@+id/tab_compass"
  ...
      <Button
          android:id="@+id/tab_map"
  ...
      <Button
          android:id="@+id/tab_knowledge"
  ```
- `app/android/app/src/main/java/com/luopan/fengshui/MainActivity.java:79-90`：三个平级子页全部 addView，默认显示第 0 个
  ```java
  compassTab = buildCompassTab();
  mapView = buildMapTab();
  knowledgeTab = buildKnowledgeTab();

  content.addView(compassTab);
  content.addView(mapView);
  content.addView(knowledgeTab);
  ...
  showTab(0);
  ```
- `MainActivity.java:630-634`：`showTab()` 只做 `setVisibility(VISIBLE/GONE)` 三选一
  ```java
  private void showTab(int index) {
      compassTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
      mapView.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
      knowledgeTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
  }
  ```
- `MainActivity.java:263-274`：罗盘 Tab = 底图 WebView + `CompassView`，浮层是**顶部居中**、宽 78% 屏、上边距 16dp
  ```java
  compassMapView = createMapWebView();
  frame.addView(compassMapView, ...MATCH_PARENT, MATCH_PARENT));
  ...
  int csize = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
  FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(csize, csize);
  cp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
  cp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
  ```
- `MainActivity.java:567-569`：地图 Tab 是**第二个** `createMapWebView()`
  ```java
  private WebView buildMapTab() {
      return createMapWebView();
  }
  ```
- `MainActivity.java:391`：两个 WebView 都加载同一页 `map.html`
  ```java
  webView.loadUrl("file:///android_asset/map.html");
  ```
- `MainActivity.java:682-686`：读数同时推两个 WebView（双份状态）
  ```java
  for (WebView wv : new WebView[]{compassMapView, mapView}) {
      if (wv == null) continue;
      wv.evaluateJavascript("setHeading(" + r.heading + ")", null);
      wv.evaluateJavascript("setSitFace('" + face + "','" + sit + "')", null);
  }
  ```
- `CompassView.java:163-169`：罗盘圆盘用 α=210 的深色实心填充，压在底图上基本看不到地图
  ```java
  int bg = Color.argb(210, 24, 20, 16);
  ...
  paint.setColor(bg);
  canvas.drawCircle(cx, cy, r, paint);
  ```

**原版档案依据（成立的部分）**

- `app\MECHANICS.md:120-126`：
  ```
  - `CompassToolsKt.E0(...)` (44 insns) reveals the app architecture: the map is a
    **WebView** (`space.xinzhi.globe.map.MapWebController`) driven by a JS bridge
    (`space.xinzhi.globe.map.MapWebApiKt`), and the compass is a Compose `GraphicsLayer`
    overlay; ...
  ```
- `app\MECHANICS.md:138-139`：
  ```
  So the original "三维山水全景地图" is a WebView map with a 3D mode + rotation + street view +
  a compass/sit-face overlay
  ```
- `docs\gap-matrix.md:105`（D1 行）：「原版是「地图+罗盘合成图」」
- `docs\gap-matrix.md:121`（E6 行）：「左列仅 截屏/等高线/罗盘复位/罗盘锁定/读盘/贴地，右列仅 地图/实景/坐向朝北开关/指针开关，**底栏仅 切换/旋转/缩放/透明度/天支线**」
- `app\MECHANICS.md:466-468`：原版罗盘页按钮是**左列浮动**，坐标 `x≈132`、`读盘 y≈2070`，不是底部页签

**档案依据不足（必须写明）**

- 「罗盘浮动在**地图中心**」的具体几何：全部档案没有坐标/尺寸记载，`MECHANICS.md` 只证 overlay，不证中心 → **未取证**。
- 「点击『罗盘』按钮后出现」的交互入口：档案无记载（R4 只记载「读盘」按钮与弹层）→ **未取证**。
- `docs\gap-matrix.md` 46 行里**没有**「首页布局 / 底部导航」这一行；`PRD-v0.1.md:100` 把三 Tab 登记为「等价替代」，证据列却写「原版主页结构（**未逐页比对**）」——这是无证据的状态判定（见 §4）。

**第一手基线截图印证（重建版，非原版）**：`docs\qa\baseline-android\round-d5\tab-compass.png`（1228×2700，SHA256 已复算一致）可见：深色罗盘圆盘压在底图上半部、左上角地图控件（矢量/影像/地形/3D地形/旋转/放大+/缩小-/俯仰±/底图源/指针/坐南朝北）仍在、最底部是「罗盘 / 地图 / 知识」三 Tab、角注「底图：天地图地形 · 审图号：未确认」被面板带压住。**罗盘页本身已经是「地图 + 浮层」**，第二个「地图」Tab 只是同一 `map.html` 的另一个实例——这一点由截图直接可见，不需推演。

**文档面同因（并入 G-01，不另计）**：

- `PRD-v0.1.md:396` 把「真机窗口内采**三 Tab 截图** + `uiautomator dump`」写成基线采集项；
- `AT-P0-0-feature-switch.md:21` 把「`round-d5/`（**三 Tab 截图** + ui dump + probe.csv + SHA）」当作基线对照物；
- `docs\qa\baseline-android\manifest.md:31` 记录「只含原生视图（**三个 Tab**、盘面按钮、读数 TextView）」。

三处都把重建自创的三 Tab 布局固化成「基线」，后续 1:1 对照会以错为对——这不是措辞问题，是验收参照物选错。

**预期 vs 实际**

- 预期（按原版档案可证部分）：单一地图 WebView 全屏；罗盘作为覆盖层浮在地图上；盘面切换/读盘/知识库走原版的「切换」页、底部弹层、详情页；底部是工具按钮条。
- 实际：三 Tab 切页；罗盘 Tab 与地图 Tab 各有一份 `map.html` WebView；罗盘浮层顶部居中且 α=210 遮挡地图；`updateReading` 双推两份 WebView。

**影响**

1. 1:1 主路径（首页结构）不成立；
2. 两个 WebView 各持一份 2D/3D/底图/指针状态，用户在地图 Tab 的操作不会回到罗盘 Tab，反之亦然（`updateReading` 只单向推 heading/sitFace，模式/缩放/俯仰/底图源不同步）；
3. 底部菜单承担的是「页面切换」，而原版底栏是地图工具条，功能语义错位。

**定级：高（随母条 B-03+B-04；本条不单独计数）**（主路径布局错误 + 双实例状态分裂；不崩溃，故非阻断）。

**修复方向（代码级）**

1. 删 `activity_main.xml:14-49` 的 `bottom_bar` 三按钮与 `MainActivity:630-634` 的 `showTab()`；`content` 只保留一个 `compassTab`。
2. 只保留一个 WebView：删 `buildMapTab()`（`MainActivity:567-569`）；`updateReading:682-686` 的双推改为单推 `compassMapView`。
3. `CompassView` 改真浮层：`cp.gravity = CENTER`（位置以补证后的原版截图为准），去掉 `CompassView:163-169` 的实心填充或改环形/半透明，让地图可见；「罗盘」按钮改为 toggle 覆盖层显隐，而不是切页。
4. 盘面切换、读盘、知识库改原版入口：盘面走「切换」页（`gap-matrix.md:81` B1b）、读盘走底部弹层（`MECHANICS.md:445-472` R4/R4b）、知识库走 `CompassIntroduceActivity` 式详情（`gap-matrix.md:97` C2）。
5. 档案先补：`gap-matrix.md` 增加「首页布局/底部导航」行；`PRD-v0.1.md:100-101` 的 B-19/B-20 从「等价替代」改为「未确认」或「有意差异」，并写清缺的证据（原版首页截图 + 控件坐标）；「地图中心」几何在补证前不得写成需求。

---

### G-02【高】`gap-matrix.md` 的状态列与基线代码事实不符，且统计数字被 `FINAL-REPORT.md` 原样引用

**文件自述的免责句（不能用来抵消下面的错误）**

`docs\gap-matrix.md:6`：
```
- 「当前实现」一列指向 **当前工作树**（分支 `feature/lc-server-infra`，含各成员本轮未提交改动），行号为写入本矩阵时的实测行号；合入后如有漂移以代码为准。
```

但 `gap-matrix.md:9-18` 的 §00 收官统计、以及 `FINAL-REPORT.md:60-69` 的 §4 都把这组**过期状态**当成收官口径引用，且 `FINAL-REPORT` §5 宣称「剩余缺口全部登记」。逐条对比如下（全部已证实）：

| 矩阵行 | 文档原文（节选） | 基线事实 | 判定 |
|---|---|---|---|
| A2（:60） | 「文字按教材式 `wangxiang()` 重算（:107-114,153）｜**缺失**」 | `CompassData.java:100` 已有 TYPE_6 全 120 条表；`CompassData.java:226` `this.fenjinText = ringText(6, h);`；`MainActivity.java:646` 显示 `r.fenjinText` | 状态过期（至少读数文字已换表） |
| A3（:61） | 「文字 = 3 龙/山（`dragon72Slot` 上限 2）｜**缺失**」 | `CompassData.java:99` 有 TYPE_5 全 72 条表；`:227` `ringText(5, h)`；`MainActivity.java:646` 显示 | 同上 |
| A4（:62） | 「文字 `JIAZI[dragon60]`（单干支）｜**缺失**」 | `CompassData.java:101` 有 TYPE_7 全 60 条表；`:228` `ringText(7, h)`；`MainActivity.java:647` 显示 | 同上 |
| A7（:65） | 「`CompassView.java:208` 用教材 8 卦序……｜**缺失**」 | 基线 `CompassView.java:253-262` 已用 `RINGS[1]`（TYPE_2）画八卦环；`MainActivity.java:647` 显示 `houtianText/xiantianText`；基线 `CompassView.java:208` 是 72 龙刻度线，不是卦序 | 行号与状态均过期 |
| B1（:80） | 「`CompassData.java:190-192` 只有 5 个名字字符串；无 artwork、无 code、无 resId｜**缺失**」 | `CompassData.java:70-73` 有 5 名字；`CompassView.java:35-51` `setArtwork()` 载入 `dial_full_0..4`；`MainActivity.java:716-718` 启动即设置 | 状态过期 |
| B7（:87） | 「无「读数面板」概念；读数是一行文本｜**缺失**」 | `CompassReadSheet.java:55-58` 14 行 `向：/坐：`；`MainActivity.java:289-294` 「读盘」按钮 | 状态过期 |
| D1（:105） | 「`MainActivity.java:126` `captureMap()`；`:163-179` 抓**地图 tab** 的 `mapView`……｜**缺失**」 | 基线 `MainActivity.java:400-415` `captureMap()` 抓 `compassTab`，`PixelCopy` 优先；`:468-493` 有 `toDataURL` 退路 | 状态与行号均过期 |
| §H P0-1…P0-5（:147-151） | 五条 P0 缺口仍在「缺失清单」 | 基线：三盘 `MainActivity.java:643-644`、14 行表 `CompassReadSheet.java:55-58`、5 盘面 `CompassView.java:35-51`、截图 `MainActivity.java:400-415`、读数面板同一处 | 五条均已落地，清单未更新 |

**预期 vs 实际**

- 预期：收官矩阵的每行状态 = 基线代码事实；§00 统计 = 逐行加总。
- 实际：7 行状态 + 5 条 P0 清单停留在旧树；`§00` 仍写「已1:1 2 / 等价替代 15 / 缺失 24 / 有意省略 4 / 有意解除 1 / 未确认 0」（`gap-matrix.md:11-18`），`FINAL-REPORT.md:64-69` 原样引用。

**影响**：总监/用户无法据此判断 1:1 完成度；「缺失 24」与「1:1 闭环」互相打脸；后续排期会重复做已完成的活。

**修复方向**：按基线逐行重核 46 行；「当前实现」列改为符号引用（类/方法名）+ 当前行号；§00 统计重算并在表头写 `as-of d752efa`；`FINAL-REPORT` §4 要么重算，要么删除，不要引用过期统计。

**PRD 同因（并入本条，不另计）**：`docs\product\PRD-v0.1.md` 对齐表/基线欠账里 7 处仍是旧树：

| PRD 行 | 文档原文（节选） | 基线事实 |
|---|---|---|
| :85 | B-04「当前顶部 5 个按钮（=常用组）；分组页与离线占位未做」 | `MainActivity.java:284-288` 是「盘面（4 组 · 常用 5）」按钮 + `CompassListActivity` |
| :86 | B-05「`CompassData.Reading` 现算约 8 类；面板 14 行全量待做」 | `CompassReadSheet.java:55-58` 已 14 行 |
| :90 | B-09「`afb8acd` 已改为抓 `compassTab` + PixelCopy 优先（**待真机验收**）……修复待真机复验」 | `MainActivity.java:400-451` 已 PixelCopy；`AT-final-round.md:46` 记 P2-06-vis PASS |
| :95 | B-14「3D 点选 Java 桥未接（`LCAndroid`），真机未验」 | `MainActivity.java:377-383` + `LCAndroid.java:29-65` |
| :98 | B-17「`afb8acd` 已保存 `compassMapView` 并**改推它**」 | 基线 `MainActivity.java:682-686` 是**双推**两个 WebView |
| :120 | OBS-001「5 盘面只 3 种渲染；dial1/2/3 0 px 差异」 | `CompassView.java:35-51,175-176` + `MainActivity.java:716-718` 已按 artwork 区分 |
| :127 | 「14 圈文字表 6 类仍是教材值……修，轮 B2」 | `CompassData.java:99-108,226-231` 已换表 |

这 7 处与 G-02 的矩阵行同一根因（代码已落地、文档未回填），按去重口径并入本条。

---

### G-03【中】`FINAL-REPORT.md` 核心证据列指向不存在的生成物，复现路径指向非基线目录

**文档原文**

- `docs\FINAL-REPORT.md:12`：
  ```
  - 数值只有**一个来源**：`app/mechanics.json` v2 → 生成器 → 两端常量 + golden；780 条圈层文字全部来自原版 DEX 取证。
  ```
- `docs\FINAL-REPORT.md:23`：
  ```
  | 单一数值源 | 已落地 | `mechanics.json` v2 → `mechanics_gen.rs` / `MechanicsGen.java`（入库 + sha256 头 + `-Check`） |
  ```
- `docs\FINAL-REPORT.md:93`：
  ```
  cd E:\APPDetector\output
  ```

**代码事实**

- `tools\mechanics\gen-mechanics.ps1:3-7`：
  ```
  # Emits the marked GENERATED region inside:
  #   app/src/main.rs                                        (Rust tables + ring scalars)
  #   app/android/app/src/main/java/com/luopan/fengshui/CompassData.java
  ```
- `tools\mechanics\gen-mechanics.ps1:245-248`：只写两个 region（Rust / Java），没有 golden 输出。
- `git ls-files` 命中 `mechanics_gen` / `MechanicsGen` = **0**；仓库内无 `golden_readings.csv` 等生成好的 golden 数据（只有 `tests/at/golden_readsheet.py` 运行时计算）。
- `docs\design\round-d-b2.md:5-7` 自己写明了实际落点：
  ```
  `app/mechanics.json` 的 `rings[].labels`…… 生成器 `tools/mechanics/gen-mechanics.ps1` 现在把 14 个 `RingDef{id,text,interval,offset,labels}` 同时生成进：
  - `app/src/main.rs` 的生成区（`pub const RINGS`）
  - `CompassData.java` 的生成区（`RingDef` + `RINGS`）
  ```

**预期 vs 实际**：预期证据列的文件能在基线 `git ls-files` 里找到；实际 `FINAL-REPORT:23` 点名两个不存在的文件，`:12` 声称生成器产出 golden（未产出），`:93` 让复现者去 `E:\APPDetector\output`（开发脏树，非审查基线 `review-base`）。

**减轻事实（如实记录）**：`git diff --stat f688c60 d752efa -- app backend tests tools scripts` = **0 行**；`git diff --stat 55b4ca8 d752efa` 仅 5 个 docs 文件。即**代码未漂移**，报告引用的旧提交与基线代码等价。本条只判文档证据/复现路径错误，不判数值结论错误。

**定级：中**（文档证据链错误，但代码无漂移）。

**修复方向**：证据列改为 `app/src/main.rs` 生成区 + `CompassData.java` 生成区；删「golden」或补真实 golden 文件；复现命令改 `E:\APPDetector\review-base`（或写「以基线 worktree 为准」）。

---

### G-04【高】「18 项 AT 全绿」不能从基线复核：证据未入库，脚本不校验用例清单

**文档原文**

- `docs\FINAL-REPORT.md:11`：
  ```
  - 双端（Rust 桌面演示 + Android 正式产品）由 `develop` 一条历史构建，四门质量门禁全绿，收官 AT 18/18 PASS、0 FAIL、0 BLOCKED。
  ```
- `docs\qa\AT-final-round.md:21`：
  ```
  证据落在 `checks\at-final\<时间戳>\`（`table.tsv`、`notes.md`、各 `ui-*.xml`、截图），**不入库**。
  ```

**基线事实**

- `.gitignore:7`：`/.inspect/`；`:2`：`/checks/`。`git ls-files` 无 `checks/at-final/**`、无 `table.tsv`/`notes.md`/验收截图。
- `tests\at\run-final.ps1:735-742`：
  ```powershell
  $pass = ($script:LcResults | Where-Object status -eq 'PASS').Count
  $fail = ($script:LcResults | Where-Object status -eq 'FAIL').Count
  $blocked = ($script:LcResults | Where-Object status -eq 'BLOCKED').Count
  $diag = ($script:LcResults | Where-Object status -eq 'DIAGNOSTIC').Count
  Log "TOTAL PASS=$pass FAIL=$fail BLOCKED=$blocked DIAG=$diag -> $notes"
  if ($fail -gt 0) { exit 1 }
  if ($blocked -gt 0) { exit 2 }
  exit 0
  ```
  脚本没有「预期用例 ID 清单」，也没有「已执行数 == 18」的断言；某个分支少跑一条 Assert3，仍然 `exit 0`。
- 脚本内不同 `Assert3` ID 去重后 **20 个**（含 `ENV` 与 `DIAGNOSTIC`）：`ENV, P2-01, P2-03, P2-04, P2-05, P2-06, P2-06-vis, P2-07, P2-08, RT-005-UI45, RT-011-diff, RT-011-mechanics, RT-011-numeric, SW-006-off-1, SW-006-off-2, SW-006-off-3, SW-006-off-3pix, SW-006-on-1, SW-006-on-2, TD-016`；「18」= 20 − ENV − DIAGNOSTIC。

**预期 vs 实际**：预期「18/18」有可追溯的结果表（哪 18 条、每条 PASS 的实测值、设备/APK sha）；实际基线只有脚本与一句结论，结果表在库外。任何人拿基线复跑都需要真机 `L2E0222531014833` + 同版本 APK，且跑完仍无法证明「18 条都执行过」。

**定级：高**（收官核心结论依赖不可复核的断言；不是「结论必假」，是「结论不可证实」）。

**修复方向**：把 `table.tsv` + `notes.md` + APK sha 摘要以文本形式入库（大图可只入 sha）；`run-final.ps1` 增加 `$expected = @('SW-006-on-1', …)` 与「执行集合 == 期望集合」断言；`FINAL-REPORT` 引用 `table.tsv` 的相对路径。

---

### G-05【高】「未确认 0 / 无一条说不清」与同文件未取证条目直接矛盾

**文档原文**

- `docs\FINAL-REPORT.md:5`：
  ```
  > 结论：**1:1 复刻目标在「读数与圈层数值」维度已闭环且有 DEX 逐指令证据；盘面与地图按 B 方案做结构等价替代；剩余缺口全部登记为「有意省略 / 有意解除 / 有意差异 / 未做」，无一条是「说不清」。**
  ```
- `docs\FINAL-REPORT.md:69`：
  ```
  | 未确认 | 0 | 上一轮 E6 已用真机控件清单结案 |
  ```
- `docs\gap-matrix.md:18`：
  ```
  | 未确认 | 0 | 最后一行 E6 已于本轮以真机控件清单结案 |
  ```

**同文件/同套文档的反证**

- `docs\gap-matrix.md:90`（B8c）：
  ```
  | B8c | 读盘弹层**宅卦**（原版 `向北 0.0° · 离宅`） | 实测出现「离宅」…… 但**算法未取证**：DEX 中未见宅卦计算类…… 究竟哪张表**未确认**。按总监裁决不臆造 | 逆向待办 R5（可选）……
  ```
- `docs\gap-matrix.md:183-184`（J 段）：
  ```
  - D4 已部分关闭：……**相册路径与分享面板的具体实现仍未取证**（LC 侧按 D-009 自定义 `Pictures/LC/`，属有意差异）。
  - **原版真机 10°/170°/250° 读数截图：已按总监裁决降级**（不再为它排真机）。
  ```
- `docs\FINAL-REPORT.md:76`：「审图号「未确认」占位」；`:78`「宅卦……无证据不臆造」。

**预期 vs 实际**：预期「未确认」计数为本套文档里所有「未取证/未确认」条目的并集；实际被写成 0，同时同一套文档至少还有 3 处「未取证」（宅卦算法、实景 SDK、相册/分享实现）与 1 处「未确认」（审图号）。「无一条是说不清」是修辞，不是事实。

**定级：高**（收官口径的核心统计错误）。

**修复方向**：把「未确认」计数改为逐条列出（至少 B8c、F2 子集、D4、审图号），或把统计口径改成「矩阵 46 行内未确认=0；矩阵外未确认≥4」并写明范围；删除「无一条是说不清」。

---

### G-06【交叉引用→C-01；高｜不计入 M6 正式数】`map-bridge.md` 自述使用「原版内置天地图 key，未改动」，与 D-006「个人 key」及矩阵「自有 key」冲突

> 计数口径：母条 C-01（M3，key 明文，总监 2026-11-05 裁定）；本条只判 `map-bridge.md:41` 与 D-006/矩阵「自有 key」的契约冲突，不单独计入 M6 正式数。

**文档原文**

- `docs\map-bridge.md:41`：
  ```
  `KEY` 为原版内置天地图 key（`dd18858b33229f5837ad1d97b8d3105e`），未改动。商用配额/换 key 属产品决策，未确认。
  ```
- `app/android/app/src/main/assets/map.html:92`：
  ```js
  var KEY = "dd18858b33229f5837ad1d97b8d3105e";
  ```

**契约冲突**

- `docs\TEAM_CHARTER.md:107`（D-006）：
  ```
  | D-006 | 2026-11-05 | 不收费：无会员/内购/广告 SDK；天地图用个人 key（1 万/日/服务），不自建瓦片服务、不申请商用授权 | … |
  ```
- `docs\gap-matrix.md:22`：
  ```
  2. **有意差异（合规/授权驱动）**：默认底图源 siJi_satellite → 天地图（F1，D-006）；审图号按源绑定且"未确认"占位（F1）；天地图 token 用自有 key。
  ```

**预期 vs 实际**：预期 LC 用用户自己的天地图 key（D-006）；实际文档明确写「原版内置 key，未改动」，代码里也是该值。同时把 key 明文写进 `docs/`，扩大泄露面。

**定级：高（随母条 C-01；本条不单独计数）**（合规/安全契约冲突；运行面密钥轮换与配额归 LC审地图）。

**交叉引用说明**：key 明文证据（`app\android\app\src\main\assets\map.html:92`）归 C-01；本条只保留「文档与 D-006/矩阵冲突」这一面。

**修复方向**：`map-bridge.md:41` 改为真实来源（自有 key / 环境注入）；key 改为构建期注入或 `local.properties`，文档不写明文；在矩阵登记该 key 的来源与配额口径。

---

### G-07【中】「19 项全部有来源、0 项估计」是关键词扫描，不是溯源；其中 3 项与 DEX 证据语义冲突

**门禁实现（决定「19/19」的含义）**

- `tests\numeric\tools\check_mechanics.py:19`：
  ```python
  FORBIDDEN = ("估计", "估算", "estimate", "estimated", "unconfirmed", "TODO", "未确认")
  ```
- `check_mechanics.py:26-29`：
  ```python
  for v in values:
      src = str(v.get("source", "")).strip()
      if not src or any(t.lower() in src.lower() for t in FORBIDDEN):
          estimated.append(v["name"])
  ```
- `scripts\build-desktop.ps1:147-151` 用同一套正则（`estimate|estimated|guess|todo|unknown|unconfirmed|tbd`）。
- `tools\mechanics\gen-mechanics.ps1:265-278` 的 `-Check` 只做两件事：生成区逐字比对、`values[].value` 字面量在两端源码出现；**不输出** `sourced=19`。
- 因此 `FINAL-REPORT.md:45` 的「gen-mechanics -Check：19/19 sourced，0 estimated，生成物与 JSON 一致」把两个不同脚本的结论混成一条（`19/19 sourced` 来自 `check_mechanics.py`，`生成物与 JSON 一致` 来自 `gen-mechanics -Check`）。

**语义冲突（数值档案内部）**

- `app/mechanics.json:30-44`：`human_plate_offset = -7.5`、`heaven_plate_offset = 7.5`，source 写 `knowledge base 人盘中针二十四山 (counterclockwise)`。
- `app/mechanics.json:1519-1520`（plates.note）：
  ```
  "note": "原版没有 ±7.5 的第四/第五个盘面常量：三盘差异全部编码在各自的 labels 顺序里，TYPE_8/TYPE_9 的 offsetDegrees 均为 0.0。",
  ```
- `app/MECHANICS.md:288-292` 同一结论；但 `MECHANICS.md:176-178` 又写「人盘……counterclockwise **−7.5°**；天盘……clockwise **+7.5°**」，与 :288 的「不存在 ±7.5 offset 常量」字面冲突（前者是绘制旋转量，后者是读数 offset，**同名不同义且档案未给机器可读区分**）。
- `app/mechanics.json:22-28`：`earth_plate_offset = 0`；同一文件 `:1521-1530` `plates.earth.offsetDegrees = 7.5`。两个「earth offset」在同一文件里是 0 与 7.5，只靠一段文字 note 解释。
- `app/mechanics.json:132-138`：`mountain_order` 的 source 是 `"standard sanhe order, knowledge base"`——「standard sanhe order」是**通用规则**，不是 DEX 也不是原版可观测行为，违反 `TEAM_CHARTER.md:50`：
  ```
  5. **不臆造**：任何数值、公式、行为结论必须能指向 DEX 反编译输出的类/方法/地址，或原版 APK 的可观测行为；无法证实的写"未确认"。
  ```

**历史后果**：`docs\qa\bugs.md:33-100`（BUG-001）证明「人盘/天盘按 ±7.5 offset 算读数」正是把这两个值当 offset 用导致的 P0；现在代码改用表旋转（`MainActivity.java:643-644`、`main.rs:399-400`），但档案里的同名值仍在，且被生成成 `HUMAN_OFFSET/HEAVEN_OFFSET` 常量（`CompassData.java:115-116`、`main.rs:151-152`，仅用于绘制）。后来者按 values[] 消费就会重犯。

**定级：中**（档案契约陷阱 + 门禁口径夸大；当前代码未被误导）。

**修复方向**：`mechanics.json` 增加 `kind: dex|kb|convention` 并按 kind 校验（convention 项必须显式白名单）；三盘字段拆名为 `plate_rotation_deg`（绘制）与 `ring_offset_deg`（读数）；`FINAL-REPORT:45` 拆成两条（`check_mechanics.py` / `gen-mechanics.ps1 -Check`）。

---

### G-08【中】`MECHANICS.md` 的地址类声明依赖库外 `.inspect` 证据，基线内不可回溯（但内部自洽性通过复算）

**声明示例**

- `app\MECHANICS.md:32-34`：
  ```
  - `JNI_OnLoad` @ `0x5370` — packer entry; control-flow-flattened (OLLVM) and
    string-encrypted (obfuscated strings; the only readable literal is `"gggg,"` = DEX magic).
  - `sub_1bf8c` @ `0x1bf8c` — the 37 KB decryptor (1235 basic blocks, 9281 instructions).
  ```
- `app\MECHANICS.md:276-282`：「逐条地址……已导成 `.inspect\derived\ring_label_addrs.json`（`{i, text, code_off, smali_line}`，780 条）」
- `docs\gap-matrix.md:29-46`：全部证据根指向 `.inspect\unpack\mem_dump`、`mem_smali`、`.inspect\derived\*`。

**基线事实**：`.gitignore:7` `/.inspect/`；`Test-Path E:\APPDetector\review-base\.inspect` = **False**；`git ls-files | Select-String 'inspect'` = **0**。上述地址、smali 行号、`ring_label_addrs.json`、`readings_check.txt` 均不在 204 个跟踪文件内。

**本次可做的复核（全部通过，如实记录）**

1. 780 条总数与逐圈条数：`[8,8,8,24,72,120,60,24,24,120,64,64,64,120]`，合计 780，与 `MECHANICS.md:253-268` 表一致。
2. 28 个十六进制常量（`0x41b40000` 等）按 IEEE-754 解码：与 `interval/offset` 声明值 **0 处不符**（0x41b40000=22.5、0x42340000=45、0x41700000=15、0x40f00000=7.5、0x40200000=2.5、0x40a00000=5、0x40400000=3、0x3fc00000=1.5、0x40c00000=6、0x40b40000=5.625）。
3. 用独立实现按 `i = (int)(((h − offset + 360) % 360) / interval)` 复算 `MECHANICS.md:352-367` R2.1 的 14 圈 × 6 方位 = 84 格：**0 处不符**（含 359.99° 的边界格）。
4. 14 段 smali 行区间 `724-1747 / 1748-2267 / … / 7022-7078` **连续、无重叠**，与 14 个 `packed-switch` 分支一一对应；`pswitch_xxx` 名与段级 `@code_off` 的差值呈 **+2（8 条表）/ +4（24 条以上表）** 两种固定模式，与数组长度的分组一致。这说明地址表不是随手拼的。

**仍不可证**：以上只能证明「档案自洽」，不能证明「地址真的指向 DEX 里的那些 `const-string`」——缺 `.inspect` 原件与 sha256 清单。

**定级：中**（核心证据链在库外，审阅者无法复核；不是「地址为假」）。

**修复方向**：入库一份 `docs\evidence\ring_label_addrs.json`（或压缩副本）+ `SHA256SUMS` + 生成命令；`MECHANICS.md` 顶部写「地址类证据的复核入口」。

---

### G-09【中】`map-bridge.md` 的桥状态与代码相反，并声明了不存在的事件类型

**文档原文**

- `docs\map-bridge.md:112`：
  ```
  - 当前 `MainActivity.createMapWebView()` **没有** `addJavascriptInterface`，`window.LCAndroid` 不存在，页面全程静默不报错。
  ```
- `docs\map-bridge.md:117`：
  ```
  - 已定义事件：`ready`（WebGL 初始化完成）、`mode`（2D/3D 切换）、`error`（`{code:"dem|webgl|shader|texture"}`）、`fps`（每 5 s）。
  ```
- `docs\map-bridge.md:201`：
  ```
  1. 3D 点选已可用（§5.1），但 **Java 桥未接**：真机上只有 `console.log("LC3D pick …")`，事件不会到达客户端，等轮 C 后补 `addJavascriptInterface`。
  ```
- `docs\map-bridge.md:229`：
  ```
  - Java 侧 `LCAndroid` 桥 + `MapMarker` 模型（客户端，轮 C 后）。
  ```

**代码事实**

- `MainActivity.java:377-383`：
  ```java
  webView.addJavascriptInterface(new LCAndroid((type, marker) -> {
      if ("pick".equals(type)) {
          mapMarker = marker;
      } else {
          mapMarker = null;
      }
  }), "LCAndroid");
  ```
- `LCAndroid.java:29-65` 实现 `@JavascriptInterface onEvent(String json)`，校验 lng/lat 范围、`elev` 允许 null；`MapMarker.java:7-21` 存在。
- `map.html` 实际只发两种事件：`:472` `emit({ type: "pick-clear" });`、`:1134` `type: "pick",`；`LCAndroid.java:40-42` 对其它类型只打 `"ignore type="`。

**预期 vs 实际**：预期契约文档描述当前实现；实际文档说「桥未接」，代码已接；文档说 `ready/mode/error/fps` 已定义，页面不发送、客户端也只认 `pick/pick-clear`。

**定级：中**（接口契约文档与实现相反，会误导客户端/地图重复实现）。

**修复方向**：更新 §5/§7/§9 的状态为「已接」并给 commit；事件表只保留实际存在的 `pick`/`pick-clear`，`ready/mode/error/fps` 标「未实现」；`map.html` 若确需这些事件，补 emit 并在客户端白名单。

---

### G-10【中】用户可见的知识库文案与数值档案/DEX 表冲突（D-008 文案诚信）

**代码原文**

- `app/android/app/src/main/java/com/luopan/fengshui/CompassData.java:273`：
  ```java
  "圆周72等份，每份5度，每山3龙，用于确定来龙干支。",
  ```
- `CompassData.java:275`：
  ```java
  "正向为每山15度范围；兼向为越过山界约3–7度的邻山过渡区。",
  ```
- `app/src/main.rs:353` / `:355` 同文：
  ```rust
  ("穿山七十二龙", "圆周72等份，每份5°，每山3龙，用于定来龙干支。"),
  ("正兼向", "正向=每山15°；兼向=越过山界3–7°的邻山过渡区。"),
  ```

**档案事实**

- `app/MECHANICS.md:274`：
  ```
  TYPE_5 是 12 组 × (5 龙 + 1「正X」) = 72，**不是**「每山 3 龙」。
  ```
- `app/mechanics.json:110-124`：`zhengxiang_range = 9`、`jianxiang_range = 3`；`app/mechanics.json` TYPE_14 labels 为「X山九度外/内」。
- `CompassData.java:233`：`if (Math.abs(offset) * 2f <= ZHENG_XIANG_DEG)` → 正向 = ±4.5°（合计 9°），不是「每山 15°」。

**预期 vs 实际**：预期知识库文案与实现/DEX 表一致；实际「每山 3 龙」「正向 15°、兼向 3–7°」都与档案相反。`TEAM_CHARTER.md:111`（D-008）要求「源码/UI 不得出现未取证的『已核对』声明」，这里是「已换表后文案未同步」，性质更重（用户直接看到错的知识）。

**定级：中**（用户可见错误文案，非主路径计算）。

**修复方向**：改为「72 等份 = 12 组 ×（5 龙 + 1 正）；正向 = 每山中央 9°（±4.5°），兼向 = 两侧各 3°」；两端同一份文案（可进 mechanics.json）。

---

### G-11【中】`MECHANICS.md` 早期章节与 R1/R2 补证矛盾且未标「已取代」，R2.2 仍写成待办

**矛盾 1：tables 是否 estimated**

- `app\MECHANICS.md:166-168`：
  ```
  The ring *formulas* below come from the app's own bundled knowledge base
  (`assets/compassIntroduce/*.html`) — real app content. The per-mountain *tables* are in the
  encrypted DEX and are marked **estimated**.
  ```
- `app\MECHANICS.md:221-225`：
  ```
  - **分金/龙 naming (CONFIRMED, was "estimated"):** `getTextList()` TYPE_6 branch holds the
    full 120-分金 list ...
  ```
- `app/mechanics.json` 14 个 `rings[]` 全部 `"labels_status": "confirmed_dex"`。

**矛盾 2：R2.2 仍写「当前仍是教材推导」**

- `app\MECHANICS.md:372`：
  ```
  ### R2.2 轮 B2 换表清单（当前仍是教材推导的圈）
  ```
- `app\MECHANICS.md:376-382` 逐行写「现在：`wangxiang(...)` / `JIAZI[dragon60]` / 未实现」。
- 基线事实：`CompassData.java:99-108` 已有 TYPE_5/6/7/10/11/12/13/14 全表；`:226-231` 全部走 `ringText`；`main.rs:130-144` 同。R2.2 描述的是 B2 之前的状态。

**矛盾 3：三盘 offset 表述**

- `app\MECHANICS.md:176-178` 用「offset 0/−7.5/+7.5」；`:288-292` 说「原版不存在第四/第五套 ±7.5 的 offset 常量」。读者按 :176 实现就会重犯 BUG-001。

**预期 vs 实际**：预期档案是「追加式但过期段落有标记」的可交接文档；实际早期结论与补证结论并列且无取代标记，R2.2 把已完成的事写成待办。

**定级：中**（交接误导，历史上已造成 P0）。

**修复方向**：`MECHANICS.md` 顶部加版本声明「§早期结论与 R1/R2 冲突时以 R1/R2 为准，标记 `SUPERSEDED`」；:166-168、:372-384 加「已被 R1.2/R2 取代（commit 662c006/0bd22f8）」；:176-178 改为「绘制旋转量（非读数 offset）」。

---

### G-12【低】`bugs.md` 的 OBS-001/OBS-002 在基线已不成立，台账仍列「待归口/备忘」

**文档原文**

- `docs\qa\bugs.md:271-273`：
  ```
  ### OBS-001 5 个盘面只渲染 3 种
  `CompassView.java:18-22` 保存 `dialType`，但 `onDraw` 只在 `:156`（`dialType>0` 画分金/龙细环）
  和 `:178`（`dialType==4` 加 64 卦环）用到它 ⇒ dial 1/2/3 渲染完全相同，dial 0 只是隐藏细环。
  ```
- `docs\qa\bugs.md:277-278`：
  ```
  ### OBS-002 `--screenshot` 中心读数硬编码「正向」
  `app/src/software.rs:287` 固定输出 `"正向 · {YINYANG}"`，未用 `rd.jian`。
  ```

**基线事实**

- `CompassView.java:35-51` 新增 `setArtwork(int dial)`（`dial_full_0..4`，≤1200px 采样）；`:175-176` `if (artwork != null) drawArtwork(...)`；`MainActivity.java:716-718` `onResume` 即 `setDialType(dial); setArtwork(dial);` → 5 盘面已按位图区分。
- `software.rs:287` 现在是 `let rows = [`；`:296` 用 `crate::ring_text_clean(14, rd.heading)` 输出正/兼向，不再硬编码「正向」。

**定级：低**（台账卫生；会导致重复派单）。

**修复方向**：OBS-001 标「已随位图盘面关闭（`CompassView.setArtwork`）」；OBS-002 标「已随轮 C 统一渲染器关闭」。

---

### G-13【低】数值测试条数在文档里有 74 / 75 / 83 / 86 四个值，实际 86

**文档原文**

- `scripts\smoke.ps1:11`：
  ```
    2. numeric  : python tests\numeric\run_all.py (74 numeric/boundary tests, xfail-aware)
  ```
- `docs\qa\AT-B2-ring-labels.md:21`：
  ```
  - [ ] `python tests/numeric/run_all.py` → `tests=75 failures=0 errors=0`
  ```
- `docs\qa\AT-B2-ring-labels.md:141`：「`python tests/numeric/run_all.py` 75 绿」。
- `docs\qa\AT-P0-1-reading-trust.md:83`：「套件全绿（**当前 83 PASS**）」。
- `docs\FINAL-REPORT.md:24/44/95` 写 86；`docs\qa\baseline-01.md:31/76` 写 74（历史快照，已标时点）。

**实际**：基线跑 `python tests\numeric\run_all.py` → `tests=86 failures=0 errors=0 skipped=0 expected_failures=0 unexpected_successes=0`。

**预期 vs 实际**：同一套件在四份文档里有四个期望数；执行者按 74/75/83 判「全绿」时，86 的实际输出会被误读。

**定级：低**（测试文档口径漂移；不影响套件本身）。

**修复方向**：统一为 86，或去掉硬编码数字（从 `tests=` 行读取）；`AT-B2`/`AT-P0-1` 的旧数随状态一起更新。

---

### G-14【低】`app/README.md` 的 3D 实现描述与基线不符

**文档原文**

- `app\README.md:24`：
  ```
    2. **3D 地形引擎**：`libterrain.so` 内是 `vmInterpret`（Dalvik VM 解释器，即 VM 保护字节码），
      无法静态反编译；已用天地图地形底图 + CSS 3D 透视等价替换。要更真 3D 可接天地图 DEM + WebGL。
  ```

**代码事实**

- `docs\map-bridge.md:13`：3D 是「自研 WebGL 地形（`#gl` canvas）」。
- `map.html` 有 `#gl` canvas 与 WebGL 初始化（`map-bridge.md` §3 的 DEM/网格/纹理参数均对应 WebGL），不是 CSS 3D 透视。

**定级：低**（交接文档过期）。

**修复方向**：改为「天地图 DEM（Terrarium）+ 自研 WebGL 地形，垂直放大 2.5×（D-009）」。

---

### G-17【中】航向注入命令的键名在文档里自相矛盾：`--ef heading`（错）vs `--ef value`（对）

**文档原文**

- `docs\qa\device-method.md:43-44`：
  ```
  2. **注入冻结传感器后 dump**（P0-1 DebugBridge）：
     `am broadcast -a com.luopan.fengshui.DEBUG --ef heading 90` 冻住读数后立刻 dump；
  ```
- 同一文件 `:82-84` 又写：
  ```
  - **`heading` 注入的 extra 键是 `value`，不是 `heading`**：`MainActivity` 用
    `it.getFloatExtra("value", 0f)`，正确命令是
    `am broadcast -a com.luopan.fengshui.DEBUG --ef value 90`；`--ef heading 90` 会被忽略。
  ```
- `docs\qa\AT-B2-ring-labels.md:23`：
  ```
  - [ ] 注入能力：按 AT-P0-1 的 `com.luopan.fengshui.DEBUG`（extra `heading`）注入航向；……
  ```
- 正确的对照：`docs\qa\AT-P0-1-reading-trust.md:34`：
  ```
  adb -s $DEV shell am broadcast -a com.luopan.fengshui.DEBUG --es cmd heading --ef value 94.0
  ```

**代码事实**：`MainActivity.java:170` 是 `injectedHeading = it.getFloatExtra("value", 0f);`——键名确为 `value`。

**预期 vs 实际**：预期文档给出唯一可执行命令；实际 §3.2 与 AT-B2 用 `heading`，§6 与 AT-P0-1 用 `value`。按前者执行时读数不动，测试者会把「注入无效」误判为页面/功能 BLOCKED。

**定级：中**（测试方法错误；影响 RT-002/004 与 AT-B2 的判定）。

**修复方向**：`device-method.md:43-44` 与 `AT-B2:23` 统一改 `--es cmd heading --ef value <float>`；在 §3.2 加「键名以 §6 为准」的交叉引用。

---

### G-18【中】AT-P0-2 的 P2-02（地图/3D 截图）在基线没有可执行入口，最终 18 项里被静默剔除

**文档原文**

- `docs\qa\AT-P0-2-capture-record.md:35-39`（P2-02）：
  ```
  ### P2-02 地图 / 3D 截图

  - 步骤：地图 Tab → 进 3D → 点「截图」。
  - 期望：WebGL 山体正常渲染、非黑屏；HUD（FPS / 海拔 / 倍率 / 贴面）在；图尺寸与屏幕一致。
  ```

**代码事实**

- `MainActivity.java:567-569`：地图 Tab 只是 `createMapWebView()`，没有任何原生按钮：
  ```java
  private WebView buildMapTab() {
      return createMapWebView();
  }
  ```
- `MainActivity.java:400-401`：唯一截图入口抓的是 `compassTab`：
  ```java
  private void captureMap() {
      final View target = compassTab;
  ```
- 「截图」按钮只加在罗盘 Tab 的底部面板（`MainActivity.java:318-320`）；`map.html` 控件栏（`docs\map-bridge.md:167-174`）只有指针/坐向/等高线/贴地/全屏/底图源，没有截图按钮。
- `tests\at\run-final.ps1` 中 `P2-02` 出现 **0 次**；`docs\qa\AT-final-round.md:30-50` 的 18 行表中也没有 P2-02。

**预期 vs 实际**：预期 AT-P0-2 的每个用例都可执行并有结果；实际 P2-02 的步骤路径在基线不存在，最终 18 项把它悄悄排除——「18/18」并不覆盖 AT-P0-2 的全部用例。

**定级：中**（用例清单与最终结果不等；验收范围被缩小但未声明）。

**修复方向**：要么在地图 Tab 加截图入口（并把 `captureMap()` 的 target 参数化），要么把 P2-02 明确标「未实现/出圈」并从 AT 正文删除；`AT-final-round` 增加「本轮剔除项」一列。

---

### G-19【中】AT-P0-1 的 RT-010 指向已被 `device-method.md` 宣布作废的旧基线

**文档原文**

- `docs\qa\AT-P0-1-reading-trust.md:82`：
  ```
  | RT-010 | 全关回基线 | …… | 与 `docs/qa/baseline-android/` 的同 heading 基线 dump **逐字相同**；……
  ```
- `docs\qa\device-method.md:56-57`：
  ```
  - 基线参考：`docs/qa/baseline-android/round-d5/`（D5 后重冻，见其 `manifest.md` 与
    `SHA256SUMS.txt`；旧 `docs/qa/baseline-android/` 为 02:56 版，不要再用）。
  ```

**预期 vs 实际**：预期 RT-010 用当前有效基线；实际指向被明令弃用的旧目录。旧目录 `docs\qa\baseline-android\manifest.md:3` 记 03:00 冻结，`docs\qa\device-method.md:57` 又称其为「02:56 版」——两个时点本身也对不上；无论取哪个，它都不是 `round-d5` 那一次构建，逐字比对必然产生误判。

**定级：中**（验收对照物错误；可能把「基线不同」误判为「功能回归」）。

**修复方向**：RT-010 改指 `docs/qa/baseline-android/round-d5/`；若确需旧基线，写明原因与差异项。

---

### G-20【低】AT-P0-4 两份文档的用例数与 ID 互相打架

**文档原文**

- `docs\qa\AT-P0-4-3d-pick.md:11`：「期望末行：`LCAT-RESULT PASS total=15 fails=0`」；`:14`：「页面在 DEM/贴面就绪后跑 **14 条断言**」。
- `docs\qa\AT-P0-4-3d-probe.md:6`：「TD-016…TD-020 依赖 `LCAndroid` 桥……桥未接前判 BLOCKED」；`:21`（M6）：「桥未接前 **TD-016…TD-019** 判 BLOCKED」；`:98`：BLOCKED 只列 `M1/M2/M5`，未列 M6（桥）。

**代码事实**

- `map.html` 里 `ok("TD-` 共 **15** 处（TD-001…TD-015；`:1546` TD-014、`:1555` TD-015，没有 `ok("TD-016")`）。
- 两份文档的 `TD-016` 含义不同：`AT-P0-4-3d-pick.md:40` 的 TD-016 是「帧率与手感」（真机），`AT-P0-4-3d-probe.md:82` 的 TD-016 是「onEvent 契约」。

**预期 vs 实际**：预期同一 ID 在同一功能域里唯一、用例数与脚本一致；实际 14/15 两个数并存，TD-016 一码两义，BLOCKED 清单漏掉桥。

**定级：低**（执行者需自行消歧；不改变功能结论）。

**修复方向**：headless 统一写 15（TD-001…TD-015）；真机用例改用 `D-` 前缀或新段号；3d-probe 的 BLOCKED 列入 M6。

---

### G-21【低】`variant-p01\manifest.md` 含 CR/BEL 控制字符，命令文本被吃掉字母

**文件与字节**

- `docs\qa\baseline-android\round-d5\variant-p01\manifest.md:8-9`：
  ```
    2. 用 un-as 把 shared_prefs/lc_prefs.xml 的 p0_1_enabled 改为 false，
       m force-stop 后重启（prefs-off.xml 留档）；
  ```
- 原始字节：`用 \rrun-as`（0x0D 插在 `run-as` 前）、`\x07m force-stop`（0x07 BEL 顶掉了 `am` 的 `a`）；全文件 `CR` 计数 2、`BEL` 计数 1。
- 读取工具会把 `\r` 当换行，所以人看到的是「用 un-as」「m force-stop」。

**预期 vs 实际**：预期文档里的命令可直接复制；实际 `run-as`/`am force-stop` 都被破坏。

**定级：低**（文档可读性/可复现性；不影响冻结物哈希）。

**修复方向**：重写这两行为纯 UTF-8 的 `run-as` / `am force-stop`，并加一条「入库前扫控制字符」的检查。

---

### G-22【低】PRD「代码与 `8cb57f3` 零差异」不成立（测试脚本有 25/18 行差异）

**文档原文**

- `docs\product\PRD-v0.1.md:361`：
  ```
  > 前置状态：`develop` = `76c4378`（168 提交，收官；代码与 `8cb57f3` 零差异，判据复验 `55b4ca8`）；……
  ```

**基线事实**

- `git diff --stat 8cb57f3 76c4378 -- app backend tests tools scripts`：
  ```
   tests/at/run-final.ps1 | 43 +++++++++++++++++++++------------------
   1 file changed, 25 insertions(+), 18 deletions(-)
  ```
- `app/`、`backend/`、`tools/` 确实 0 差异；差异在 `tests/at/run-final.ps1`（P2-06-vis 判据修正）。

**预期 vs 实际**：预期「代码零差异」与 git 一致；实际测试脚本有改动。把测试脚本排除在「代码」之外可以自洽，但原文没有限定范围。

**定级：低**（口径不精确；不影响 app 结论）。

**修复方向**：改为「`app/backend/tools` 零差异；`tests/at/run-final.ps1` 有判据修正 25/18 行」。

---

## 2. 高度可疑清单

无。本批所有「高」条目均有代码路径或可直接复算的文档矛盾。

---

## 2.5 跨模块交叉引用（按总监去重裁定，不重复计数）

| 事项 | 本报告位置 | 母条 | 处理 |
|---|---|---|---|
| `DebugBridge` 导出广播 + `AT-P0-0:84` SW-009「release 包无响应」的预期 | G-01 的代码证据链 | M2 B-02 | 本报告不单列；只指出 AT 用例的预期与 `MainActivity.java:237-243`（无 `debuggable` 门槛）矛盾 |
| 天地图 key 明文 `dd18858b…` | G-06 | M3（C） | 本报告只判 `map-bridge.md:41` 与 D-006/矩阵的「自有 key」冲突，不重复计泄露面 |
| 同一 `map.html` 的两个 WebView 实例 | G-01 | M2 K-01 / B-03 / B-04 | 本报告只从「首页结构/浮层位置」判，不重复计双实例状态 |
| 正/兼向两条 UI 链路互斥（盘心公式 vs TYPE_14 表） | G-10（仅文案面） | M5 F-03 + M2 | 本报告只判知识库文案与档案冲突，不重复计数值链路 |
| `tests\at\run-at.ps1` 在基线不存在（AT-P0-0:51、AT-P0-1:48、AT-P0-4-probe:53、PRD:425 均称「已落」） | §4 第 15 条 | M5 Q-02 | 本报告只作文档引用核对，不另立条 |
| 数值套件 skip/xfail 仍打 PASS | G-13（仅条数） | M5 F-01 / F-02 | 本报告不判门禁逻辑，只判文档期望数不一致 |

**G-01、G-06 已按裁定在正文标为交叉引用**（母条分别为 B-03+B-04、C-01），不重复计入本报告正式数；上表 6 项为其余跨模块引用。

---

## 3. 待确认清单（不进缺陷清单）

### G-15【待确认】`18/18 PASS` 这一次运行是否真实发生

- 缺的证据：`checks\at-final\<时间戳>\table.tsv`、`notes.md`、`ui-*.xml`、`shot-compass.png`、APK sha256 对应的安装记录。
- 已有的线索：`docs\qa\AT-final-round.md:23-28` 记「修前 17/1，修后 18/0」；`docs\qa\bugs.md:258-265` 记复验构建 `d286873`、APK sha `A94DE24A…F70F189B`、截图 sha `AB8C7821…`；脚本与文案细节具体，**倾向于真跑过**，但基线内无原件。
- 缺什么才能定论：上述结果表或任一截图原图（含 sha 校验）入库。

### G-16【待确认】`.inspect` 的 780 条逐条地址是否真的对应 DEX

- 缺的证据：`.inspect\unpack\mem_dump\classes06.dex`（7.4 MB）、`.inspect\unpack\mem_smali\` 原件、`.inspect\derived\ring_label_addrs.json`、生成脚本 `.inspect\tools\extract_rings.py` 的可运行副本。
- 已有的线索：本报告 §G-08 的 4 项内部复算全部通过（条数、位模式、84 格、段区间连续性）。
- 缺什么才能定论：入库一份地址 JSON + smali 片段或 sha256 清单；或由持 `.inspect` 的人按 `app\MECHANICS.md:276-282` 随机抽 5 条地址回查。

---

## 4. 无证据支撑的断言（单列；不因未进缺陷清单而免责）

| # | 文件:行 | 原文（节选） | 缺什么证据 |
|---|---|---|---|
| 1 | `docs\product\PRD-v0.1.md:100` | 「B-19 底部三 Tab：罗盘 / 地图 / 知识库…… 等价替代」；证据列「原版主页结构（**未逐页比对**）」 | 原版首页截图/控件树；「等价」的判据。`gap-matrix.md:7` 自己规定「查不到原版行为的写未确认」，此处违反 |
| 2 | `docs\product\PRD-v0.1.md:101` | 「B-20 地图 Tab 独立浏览…… 等价替代」；证据列「原版地图页」 | 原版存在「独立地图页」的证据 |
| 3 | `docs\FINAL-REPORT.md:5` | 「剩余缺口全部登记为……无一条是『说不清』」 | 与 `gap-matrix.md:90/183-184` 的「未取证」矛盾（见 G-05） |
| 4 | `docs\FINAL-REPORT.md:69`、`docs\gap-matrix.md:18` | 「未确认｜0」 | 至少 B8c 宅卦算法、D4 相册/分享实现、实景 SDK、审图号未确认 |
| 5 | `app\README.md:9`、`docs\TEAM_CHARTER.md:60` | 「19 项，全部有来源、0 项估计」 | 门禁是关键词扫描（G-07）；`mountain_order` 来源是通用规则 |
| 6 | `app\README.md:11` | 「已实现（与原版功能一致）」 | A12/A13 盘面/指针为「等价替代」，B1b 盘面目录「有意省略」，3D 为等价替代；「功能一致」无逐项证据 |
| 7 | `app\MECHANICS.md:166-168` | 「per-mountain *tables* …… are marked **estimated**」 | 已被 R1.2 的 `confirmed_dex` 推翻（G-11） |
| 8 | `docs\map-bridge.md:117` | 「已定义事件：ready / mode / error / fps」 | 页面与客户端均未实现（G-09） |
| 9 | `docs\map-bridge.md:165` | 「实测输出：`LCBRIDGE selfTest PASS {"pass":true,"events":3,...}`」 | 无入库日志/截图；无头 Chrome 命令可复跑但未记录结果文件 |
| 10 | `docs\map-bridge.md:25` | 「实测校验：北京 z10/843/388 瓦片解出海拔 19–62 m……灵山一带 1537–1891 m」 | 无入库采样数据/瓦片哈希 |
| 11 | `docs\qa\atb2-result.md:36-38` | 「真机 `LCRING` 616 行……0 不符」 | `checks/qa-atb2/` 未入库；仅有文字记录 |
| 12 | `docs\qa\AT-final-round.md:39-50` | 18 行「PASS／实测值」表 | `checks\at-final\` 未入库（G-04/G-15） |
| 13 | `docs\product\PRD-v0.1.md:361` | 「代码与 `8cb57f3` 零差异」 | `tests/at/run-final.ps1` 有 25/18 行差异（G-22） |
| 14 | `docs\product\PRD-v0.1.md:396`、`docs\qa\AT-P0-0-feature-switch.md:21`、`docs\qa\baseline-android\manifest.md:31` | 把「三 Tab 截图/dump」当基线采集项与对照物 | 原版首页结构未取证；三 Tab 是重建自创（G-01） |
| 15 | `docs\qa\AT-P0-2-capture-record.md:35-39` | P2-02「地图 Tab → 进 3D → 点『截图』」 | 基线地图 Tab 无截图入口（G-18）；`run-final.ps1` 无 P2-02 |
| 16 | `docs\qa\AT-P0-1-reading-trust.md:83`、`docs\qa\AT-B2-ring-labels.md:21/141` | 「83 PASS」「75 绿」 | 实测 86（G-13） |

---

## 5. 必查项专项回答

### 5.1 `MECHANICS.md` 地址类声明抽查（结论：内部自洽，库外不可回溯）

| 地址类声明 | 位置 | 本次核验 | 结论 |
|---|---|---|---|
| 原生符号地址 `JNI_OnLoad@0x5370`、`sub_1bf8c@0x1bf8c`、`vmInterpret@0xfa30` 等 | `MECHANICS.md:32-49` | 依赖 `.inspect` 的 `.so`；基线无 | **待确认**（G-16） |
| 14 圈 smali 行区间 | `MECHANICS.md:253-268` | 724→7078 连续无重叠；与 14 个 pswitch 分支对应 | 内部自洽 |
| `pswitch_xxx` 与段级 `@code_off` | `MECHANICS.md:279-282` + `mechanics.json` `labels_source` | 14 组差值：8 条表的 3 组为 +2，其余 11 组为 +4（固定模式） | 内部自洽，原因未取证 |
| 780 条逐条 `code_off` | `.inspect\derived\ring_label_addrs.json`（点名） | 文件不在库；`git ls-files` 0 命中 | **不可回溯**（G-08） |
| 28 个十六进制浮点常量 | `mechanics.json` `rings[].source` | 全部按 IEEE-754 解码正确 | **通过** |
| R2.1 的 84 格期望读数 | `MECHANICS.md:352-367` | 独立复算 84/84 一致（含 359.99°） | **通过** |

### 5.2 `mechanics.json` 19 项逐条溯源

判定口径：`DEX` = source 含 `decompiled`/类名且值可由 `rings[]` 位模式或条数核验；`KB` = 原版内置知识库 HTML；`约定` = 通用规则（非原版证据）。

| # | id | value | source 原文（截断处标 …） | 类型 | 基线内可核验性 |
|---|---|---|---|---|---|
| 1 | mountains_per_circle | 24 | `CompassReadTypeEnum TYPE_4 (decompiled), knowledge base` | DEX+KB | 可由 TYPE_4 表 24 条核验 |
| 2 | degrees_per_mountain | 15 | `CompassReadTypeEnum TYPE_4 intervalDegrees 0x41700000 (decompiled)` | DEX | 0x41700000=15.0 通过 |
| 3 | earth_plate_offset | 0 | `knowledge base 地盘正针二十四山` | KB | 与同文件 `plates.earth.offsetDegrees=7.5` 同名不同义（G-07） |
| 4 | human_plate_offset | -7.5 | `knowledge base 人盘中针二十四山 (counterclockwise)` | KB | 与 `plates.note`「原版没有 ±7.5 offset」冲突（G-07） |
| 5 | heaven_plate_offset | 7.5 | `knowledge base 天盘缝针二十四山 (clockwise)` | KB | 同上 |
| 6 | fenjin_divisions | 120 | `CompassReadTypeEnum TYPE_6 (decompiled)` | DEX | TYPE_6 表 120 条，通过 |
| 7 | fenjin_interval | 3 | `CompassReadTypeEnum TYPE_6 intervalDegrees 0x40400000 (decompiled)` | DEX | 0x40400000=3.0 通过 |
| 8 | dragon72_divisions | 72 | `CompassReadTypeEnum TYPE_5 (decompiled)` | DEX | TYPE_5 表 72 条，通过 |
| 9 | dragon72_interval | 5 | `CompassReadTypeEnum TYPE_5 intervalDegrees 0x40a00000 (decompiled)` | DEX | 0x40a00000=5.0 通过 |
| 10 | dragon60_divisions | 60 | `CompassReadTypeEnum TYPE_7 (decompiled)` | DEX | TYPE_7 表 60 条，通过 |
| 11 | dragon60_interval | 6 | `CompassReadTypeEnum TYPE_7 intervalDegrees 0x40c00000 (decompiled)` | DEX | 0x40c00000=6.0 通过 |
| 12 | hexagram64_interval | 5.625 | `CompassReadTypeEnum TYPE_11/12/13 intervalDegrees 0x40b40000 (decompiled)` | DEX | 0x40b40000=5.625 通过 |
| 13 | bagua_interval | 45 | `CompassReadTypeEnum TYPE_1/2/3 intervalDegrees 0x42340000 (decompiled)` | DEX | 0x42340000=45.0 通过 |
| 14 | jianxiang_range | 3 | `CompassReadTypeEnum getTextList TYPE_14 (九度外 = outer 3°, decompiled)` | DEX 推导 | 由「九度外」文字反推，非直接常量；语义可争议 |
| 15 | zhengxiang_range | 9 | `CompassReadTypeEnum getTextList TYPE_14 (九度内 = central 9°, decompiled)` | DEX 推导 | 同上 |
| 16 | magnetic_north_mountain | 子 | `knowledge base 地盘正针二十四山` | KB | KB 语义，可接受 |
| 17 | mountain_order | 子癸丑…壬 | `standard sanhe order, knowledge base` | 约定 | **「standard sanhe order」不是 DEX 也不是原版可观测行为，违反章程 §4.5** |
| 18 | bagua_five_elements | 乾兑金… | `knowledge base 六十甲配六十四卦五行` | KB | 原版 HTML，可接受 |
| 19 | dial_types | 5 | `CommonCompassEnum COMPASS_0..4 (decompiled): …` | DEX | 与 `dials[]` 5 条一致，通过 |

**小结**：19 项里 6 项可直接用十六进制位模式核验、3 项用条数核验、2 项为 DEX 文字推导、6 项来自原版知识库、1 项（mountain_order）来自通用规则。因此「19/19 sourced」的 **sourced 含义是「source 字段非空且不含禁用词」**，不是「19 项都有 DEX/APK 证据」。这条口径必须写进报告，否则会让读者以为 19 项全是逐指令证据。

### 5.3 `FINAL-REPORT.md`「18 项 AT 全绿」是否可复现

- **结论：基线内不可复现**（G-04）。可复核的部分：`tests\numeric` 86 项 PASS、`gen-mechanics -Check` PASS、`check_mechanics.py` 19/19/0 均可现场复跑；AT 的 18 项依赖真机与库外证据，基线内只剩脚本。
- **18 项的组成**：`run-final.ps1` 内不同 `Assert3` ID 去重 20 个（含 `ENV`、`DIAGNOSTIC`），18 = 20 − 2。但 `AT-P0-2` 正文共 10 个 P2 用例，其中 **P2-02（地图/3D 截图）在 `run-final.ps1` 里 0 命中**、P2-12（可选）也不在，最终表同样没有——用例清单与结果表不等（G-18）。`AT-P0-4` 还有「14/15」「TD-016 一码两义」的编号问题（G-20）。
- 另有一条**正面**核对：`git diff --stat f688c60 d752efa -- app backend tests tools scripts` = 0；`55b4ca8..d752efa` 仅 docs 改动。即报告所引提交的**代码**与基线一致，验收对象没有代码漂移。
- 还发现一条**口径**问题：`run-final.ps1` 的 `P2-06-vis` 门槛是在看到「旧构建 54 / 修复后 143」之后取的 100（`docs\qa\AT-final-round.md:46,56-57`），属于**按结果调阈值**；它仍是有效的回归门槛（旧构建 54 < 100 < 143），但报告里应写「阈值由两版实测确定」，而不是当成先验判据。

### 5.4 原版布局档案依据（为「罗盘/地图同页」缺陷提供支撑）

**成立**：

1. `MECHANICS.md:120-126`：地图是 WebView（`MapWebController`），罗盘是 Compose `GraphicsLayer` **overlay**。
2. `MECHANICS.md:138-139`：原版是 WebView 地图 + 3D + 旋转 + 街景 + compass/sit-face overlay。
3. `gap-matrix.md:105`：原版截图是「地图+罗盘合成图」。
4. `gap-matrix.md:121`：原版底栏是「切换/旋转/缩放/透明度/天支线」工具条；左列/右列是浮动按钮（`MECHANICS.md:466-468` 给出 x≈132、读盘 y≈2070 的真机坐标）。
5. `MECHANICS.md:128-147`：`MapWebInterface` 33 方法、`setSitFace`、`onMapRotate`、`showFullScreen` 等，全部是「一个地图页 + 覆盖层」的桥，不存在「罗盘页/地图页」两套 WebView。

**不成立/未取证**：

1. 「罗盘浮在**地图中心**」：无坐标/尺寸证据（档案只证 overlay，不证位置）。
2. 「点击『罗盘』按钮后出现」：无交互证据（只有「读盘」按钮打开底部弹层）。
3. 三 Tab 的「等价替代」判定：`PRD-v0.1.md:100` 证据列自认「未逐页比对」，按 `gap-matrix.md:7` 应写「未确认」。

**结论**：用户的缺陷判断（罗盘与地图应是同一页面的不同表现、底部菜单无存在意义）**有档案支撑**；「浮在地图中心」需补一张原版首页截图+控件坐标后再写死。修复时按 G-01 的五步走。

### 5.5 必查项缺口（第二批收口）

| 必查项 | 现状 | 仍缺什么 |
|---|---|---|
| ① `MECHANICS.md` 地址 / `mechanics.json` 19 项来源 | 780 条、28 个位模式、84 格复算全过；19 项逐条已分类（6 项位模式、3 项条数、2 项文字推导、6 项 KB、1 项通用规则） | `.inspect` 的 DEX/smali 原件不在库 → 只能证「档案自洽」，不能证「地址真对应 DEX」（G-16） |
| ② 无证据断言 | 已列 16 条（§4），覆盖 3 份报告/文档的结论表 | 其中 4 条依赖库外运行记录（`map-bridge` 实测、AT-B2 616 行、18 行 AT 表、`LCBRIDGE selfTest`），基线内无法补 |
| ③ `FINAL-REPORT` 18/18 | 已证「基线内不可复核」；结果表/截图/日志未入库 | 真实运行记录（`table.tsv`/`notes.md`/APK sha）在库外；另发现 P2-02 不在最终 18 项（G-18）、AT-P0-4 14/15 与 TD-016 一码两义（G-20） |
| ④ 文档 vs 基线代码版本 | `app/backend` 自 `f688c60` 起 0 漂移；`55b4ca8..d752efa` 仅 docs | 文档状态回填缺口：矩阵 7 行 + §H 5 条 P0 + PRD 7 处（同一根因，按文件计 19 处）；PRD「代码零差异」口径不准（G-22） |
| 已知缺陷档案依据 | 原版「地图 WebView + 罗盘 overlay」成立；原版底栏=工具条成立 | 「地图中心」几何、「点击罗盘按钮」交互未取证（G-01 已标） |

---

## 6. 已读文件清单（行数取自 `Get-Content .Count`，与 `00-MATRIX.md` 抽查 23/23 一致）

**docs 42 个文件全部触碰（42/42）**：24 个 Markdown 全读、2 个 `SHA256SUMS.txt` 全读、1 个 `probe.csv` 全读；10 张 PNG 中 1 张目视（`round-d5/tab-compass.png`）+ 10/10 哈希复算；5 个单行 XML dump 只做哈希/体积校验（未逐节点解析）、1 个 `ui.xml` 的哈希与 `docs\qa\baseline-android\manifest.md:27` 一致。

| 文件 | 行数 | 覆盖方式 |
|---|---|---|
| `docs\FINAL-REPORT.md` | 105 | 全文 |
| `docs\gap-matrix.md` | 185 | 全文 |
| `docs\map-bridge.md` | 233 | 全文 |
| `docs\TEAM_CHARTER.md` | 112 | 全文 |
| `docs\design\single-source-of-truth.md` | 298 | 全文 |
| `docs\design\round-c-diff.md` | 64 | 全文 |
| `docs\design\round-d-b2.md` | 62 | 全文 |
| `docs\product\PRD-v0.1.md` | 437 | 全文（第二批补读 1-220/221-437） |
| `docs\product\market-scan.md` | 119 | 全文（第二批补读） |
| `docs\product\attribution\README.md` | 30 | 全文 |
| `docs\qa\AT-B2-ring-labels.md` | 173 | 全文（第二批补读） |
| `docs\qa\AT-final-round.md` | 71 | 全文 |
| `docs\qa\AT-P0-0-feature-switch.md` | 139 | 全文（第二批补读） |
| `docs\qa\AT-P0-1-reading-trust.md` | 115 | 全文（第二批补读） |
| `docs\qa\AT-P0-2-capture-record.md` | 135 | 全文（第二批补读） |
| `docs\qa\AT-P0-4-3d-pick.md` | 46 | 全文（第二批补读） |
| `docs\qa\AT-P0-4-3d-probe.md` | 117 | 全文（第二批补读） |
| `docs\qa\atb2-result.md` | 64 | 全文 |
| `docs\qa\baseline-01.md` | 151 | 全文 |
| `docs\qa\bugs.md` | 287 | 全文 |
| `docs\qa\device-method.md` | 140 | 全文（第二批补读） |
| `docs\qa\baseline-android\manifest.md` | 95 | 全文 |
| `docs\qa\baseline-android\round-d5\manifest.md` | 38 | 全文（第二批补读） |
| `docs\qa\baseline-android\round-d5\variant-p01\manifest.md` | 15 | 全文（第二批补读；含控制字符，见 G-21） |
| `docs\qa\baseline-android\probe.csv` | 7 | 全文 |
| `docs\qa\baseline-android\round-d5\SHA256SUMS.txt` | 7 | 全文 + 11 条哈希复算 |
| `docs\qa\baseline-android\round-d5\variant-p01\SHA256SUMS.txt` | 4 | 同上 |
| `docs\qa\baseline-android\ui.xml` | 1（单行） | 仅哈希（8,586 B，与 `docs\qa\baseline-android\manifest.md:27` 一致） |
| `docs\qa\baseline-android\round-d5\ui-dial-list.xml` | 1（单行） | 仅哈希（10,572 B） |
| `docs\qa\baseline-android\round-d5\ui-first-run.xml` | 1（单行） | 仅哈希（6,056 B） |
| `docs\qa\baseline-android\round-d5\variant-p01\prefs-off.xml` | 1（单行） | 仅哈希（274 B） |
| `docs\qa\baseline-android\round-d5\variant-p01\prefs-on.xml` | 1（单行） | 仅哈希（127 B） |
| `docs\qa\baseline-android\**\*.png`（10 张） | bin | 1 张目视（`round-d5/tab-compass.png`）；10/10 sha256 复算 |
| `docs\review\00-MATRIX.md` | 253 | 全文 |
| `app\MECHANICS.md` | 505 | 全文 |
| `app\mechanics.json` | 1605 | 全文 |
| `app\README.md` | 50 | 全文 |
| `app\Cargo.toml` | 18 | 全文 |
| `app\Cargo.lock` | 4112 | 只读校验（`cargo metadata --no-deps --locked --offline` 通过；400 包、0 git 源） |
| `app\src\main.rs` | 701 | 重点段（120-239、240-359、360-524、520-701） |
| `app\src\software.rs` | 321 | 255-314 + 关键字检索 |
| `app\src\dial.rs` | 254 | 抽读（常数与 `central_dial`） |
| `app\android\...\MainActivity.java` | 767 | 全文 |
| `app\android\...\CompassData.java` | 280 | 全文 |
| `app\android\...\CompassView.java` | 358 | 全文 |
| `app\android\...\CompassReadSheet.java` | 95 | 全文 |
| `app\android\...\LCAndroid.java` | 66 | 全文 |
| `app\android\...\MapMarker.java` | 28 | 全文 |
| `app\android\app\src\main\res\layout\activity_main.xml` | 50 | 全文 |
| `app\android\app\src\main\assets\map.html` | 1605 | 仅检索（key/桥事件/LCAndroid/emit/TD 断言计数） |
| `scripts\smoke.ps1` | 174 | 全文 |
| `scripts\build-desktop.ps1` | 195 | 抽读（130-174）+ 检索 |
| `tools\mechanics\gen-mechanics.ps1` | 279 | 全文 |
| `tools\map\run_at_p04.ps1` | 41 | 全文 |
| `tests\at\run-final.ps1` | 743 | 结构检索 + 699-743 |
| `tests\at\golden_readsheet.py` | 100 | 全文 |
| `tests\numeric\run_all.py` | 39 | 全文 |
| `tests\numeric\tools\check_mechanics.py` | 44 | 全文 |
| `tests\numeric\oracle.py` | 246 | 检索（读数/分金来源） |
| `tests\numeric\README.md` | 80 | 检索 |
| `.gitignore` | 39 | 全文 |

**M7 容器文件归属声明（总监 2026-11-05 裁定）**：`app\Dockerfile`（21 行）、`app\docker-compose.yml`（19 行）、`app\.dockerignore`（4 行），共 44 行——**归属 M4**（LC审服务端 已全文读，其覆盖声明含此 44 行）；**M6 未读、不下结论**，索引归属改 M4。这是证据审计指出的唯一「读一半当全量」项，不扩大化。

**现场可复现命令（本次已跑）**

```powershell
python tests\numeric\run_all.py                 # tests=86 ... RESULT: PASS
pwsh -File tools\mechanics\gen-mechanics.ps1 -Check   # 3 个 PASS，exit 0
python tests\numeric\tools\check_mechanics.py   # values=19 sourced=19 estimated=0
git ls-files | Measure-Object                   # 204
```

**证据完整性抽检**：`round-d5\SHA256SUMS.txt` + `variant-p01\SHA256SUMS.txt` 共 11 条记录（7 PNG + 4 XML）逐条复算 **11/11 一致**；旧基线 3 张 `tab-*.png` 与 `ui.xml` 的哈希与 `docs\qa\baseline-android\manifest.md:24-28` 一致（4/4）。合计 **10 张 PNG + 5 个 XML，15/15 哈希可复算**。这部分 QA 证据是可信的。

---

## 7. 未覆盖范围（严禁视为已审）

1. **5 个单行 XML dump 未逐节点解析**：`baseline-android/ui.xml`（8,586 B）、`round-d5/ui-dial-list.xml`（10,572 B）、`ui-first-run.xml`（6,056 B）、`variant-p01/prefs-off.xml`（274 B）、`prefs-on.xml`（127 B）。只做了哈希/体积校验；节点级内容（控件树、bounds）归 LC审证据/LC审测试。
2. **10 张 PNG 中 9 张未目视**：只复算 sha256（10/10 一致）。仅 `round-d5/tab-compass.png` 目视确认了 G-01 的浮层/三 Tab/角注遮挡；像素级布局与配色结论以 M2/M3 报告为准。
3. `app\Cargo.lock` 未逐包审计（只做 `--locked` 一致性与来源统计）。
4. `map.html` 的运行行为（DEM、pick、双 WebView 隔离、key 风险面）归 LC审地图，**本轮不判**；本报告只判 `map-bridge.md` 与代码的契约文字。
5. 桌面 `cargo build` / `cargo test` **未跑**（归 M1；本报告只核了 `central_dial(1100,780)=(540,409,232)` 一项，`dial.rs:60-69` 计算成立）。
6. 真机 AT 未跑（无设备窗口，归 M5）；本报告对 18/18 的判定是「不可从基线复核」，不是「已证伪」。
7. **AT 用例的每一条步骤未逐条执行**：本报告只静态核对文档、脚本与代码路径，没有在真机上点按；执行结果归 M5/证据审计。
8. `docs/qa/runs/` 与 `checks/` 不在库，任何「已跑」记录都无法核对。
9. `docs\product\market-scan.md` 的外部数据（评分/下载量/评论/版本号）未做二次抓取核验，只审其内部一致性与「未查证清单」的自洽性。
10. `investigation.cany/**`、`craft.json`、`usage.json`（M8）按矩阵约定不入审，**未审**。
11. `app\Dockerfile`（21 行）、`app\docker-compose.yml`（19 行）、`app\.dockerignore`（4 行）共 44 行：**归属 M4、M6 未读、不下结论**（见 §6 声明；M4 已全读，索引归属改 M4）。

---

## 8. 建议的抽样追溯入口（给总监）

按本报告抽 8 条，可直接回查：

1. G-01 → `activity_main.xml:14-49` + `MainActivity.java:263-274` + `MECHANICS.md:120-126` + `round-d5/tab-compass.png`
2. G-02 → `gap-matrix.md:60-62` + `CompassData.java:99-101,226-228` + `PRD-v0.1.md:85-98`
3. G-04 → `run-final.ps1:735-742` + `AT-final-round.md:21` + `.gitignore:2`
4. G-06 → `map-bridge.md:41` + `map.html:92` + `TEAM_CHARTER.md:107`
5. G-10 → `CompassData.java:273,275` + `MECHANICS.md:274` + `mechanics.json:110-124`
6. G-17 → `device-method.md:43-44` vs `:82-84` + `MainActivity.java:170`
7. G-18 → `AT-P0-2-capture-record.md:35-39` + `MainActivity.java:567-569,400-401`
8. G-21 → `docs\qa\baseline-android\round-d5\variant-p01\manifest.md:8-9`（原始字节含 `\r`/`\x07`）

（本报告只写文件，未改任何源码；`git status` 预期只多 `docs/review/` 未跟踪目录。）
