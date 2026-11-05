# M1 桌面客户端（Rust/egui）审查报告 · 第一批

| 项 | 值 |
|---|---|
| 模块 | M1 桌面客户端（Rust/egui），3 文件 1276 行 |
| 审查人 | LC审客户端 |
| 基线 | `develop` @ `d752efaf7936f887a70577681c703497e74e8b57`（tag `review-20261105`） |
| 工作区 | `E:\APPDetector\review-base`（只读） |
| 批次 | 第一批：阻断/高 + 必查项（三盘自洽、`--screenshot` 验收失真）；第二批：中/低（§9）+ 必查缺口（§10）+ 未覆盖文件清单（§11） |
| 条目计数 | 已证实 **7**（高 2 / 中 2 / 低 3）· 交叉引用 **2**（A-03→B-10、A-04→B-09，按裁定不单独计数）· 高度可疑 **1** · 待确认 **2** |
| 文件命名说明 | 已按技术总监裁定（第一批抽样追溯后）改名：**A=M1=`10-M1-desktop.md`**、B=M2=`11-M2-android.md`，与 `98-report-conventions.md` §2 一致。A 编号未变。 |

---

## 1 缺陷清单（已证实）

### A-01 [高] `--screenshot` 只走 `software.rs` 独立渲染器，且渲染/字体/保存三类失败全部静默、退出码仍为 0，验收图可假绿

**文件行号**
- `app/src/main.rs` L613-L616
- `app/src/software.rs` L237-L249、L264-L271、L310-L321
- `app/src/main.rs` L511-L515、L305-L311

**代码片段（逐字）**

`app/src/main.rs` L613-L616：

```rust
    if let Some(path) = screenshot {
        software::software_screenshot(&path);
        return Ok(());
    }
```

`app/src/software.rs` L237-L249：

```rust
pub fn software_screenshot(path: &str) {
    let (w, h) = (1100usize, 780usize);
    let mut cv = Canvas::new(w, h);
    cv.fill((24, 20, 16));

    let font = load_font();
    let gold = (220, 190, 130);
    let dim = (150, 130, 100);
    let white = (235, 228, 215);
    let heading = 0.0f32;
    let rd = reading_for(heading);

    if let Some(f) = &font {
```

`app/src/software.rs` L310-L321：

```rust
    // Save PNG.
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = image::save_buffer(
        path,
        &cv.buf,
        w as u32,
        h as u32,
        image::ColorType::Rgba8,
    );
}
```

`app/src/software.rs` L264-L271：

```rust
    // Compass: the same shared renderer the real egui window uses (src/dial.rs).
    let (cx, cy, r) = dial::central_dial(w as f32, h as f32);
    {
        let rings = ShowRings::default();
        let st = DialState { heading, bagua_mode: BaguaMode::HouTian, rings: &rings };
        let mut sc = SoftCanvas { cv: &mut cv, font: font.as_ref() };
        dial::draw_dial(&mut sc, &st, cx, cy, r);
    }
```

`app/src/main.rs` L511-L515（`--screenshot-ui` 用的真 UI 存图路径，同一失效模式）：

```rust
    if let Some(parent) = std::path::Path::new(path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let _ = image::save_buffer(path, &rgba, w as u32, h as u32, image::ColorType::Rgba8);
}
```

**触发条件**
1. 运行 `luopan --screenshot <path>`：入口固定走 `software_screenshot()`（`main.rs` L613-L616），**不创建窗口、不经过 `eframe::App::update`**，所以罗盘页的 7 个圈层开关（`main.rs` L541-L547）、先天/后天八卦切换（L538-L539）、拖拽后的朝向全部不参与出图；出图恒为 heading=0、`ShowRings::default()`、`BaguaMode::HouTian`（`software.rs` L246、L267-L268）。
2. 运行环境里 `C:/Windows/Fonts/*`、`/usr/share/fonts/**/NotoSansCJK*` 六个候选路径都不存在（`main.rs` L481-L488、`software.rs` L148-L155 硬编码）时，`font` 为 `None`，`if let Some(f) = &font`（`software.rs` L249、L274）两个文字块整体跳过，PNG 仍然写出——得到一张只有几何线条、没有任何文字的验收图。
3. 目标目录不可写、磁盘满、路径非法时，`image::save_buffer` 的 `Result` 被 `let _ =` 丢弃（`software.rs` L314、`main.rs` L514），函数返回 `()`，`main` 随后 `return Ok(())`（`main.rs` L615）→ 进程退出码 0。
4. `--screenshot-ui`（`main.rs` L604-L607、L628）在 45 秒内没收到 `egui::Event::Screenshot` 时直接关窗（`main.rs` L305-L311），同样不写文件、退出码 0。

**预期 vs 实际**
- 预期：验收截图工具在「没有产出文件」「产出文件无文字」「出图不是真 UI 配置」三种情况下必须以非 0 退出码失败，或在日志中明确报错；截图必须反映真窗口状态（`docs/design/round-c-diff.md` 只允许抗锯齿/字体微差，不允许整页配置被写死）。
- 实际：三种失败全部静默成功；出图内容由 `software.rs` 内硬编码常量决定，与用户实际看到的窗口状态无关。

**证据等级**：已证实（代码路径闭合：入口→渲染→保存→返回，无任何错误分支）。

**修复方向**
- `software.rs::software_screenshot` 改为返回 `Result<(), String>`：`save_buffer` 的错误、`load_font()` 为 `None`、面板逐行 `text()` 未画出时一律返回 `Err`；`main` 对 `Err` 走 `std::process::exit(2)`。
- `main.rs::save_png` 同样改为返回 `Result`，`handle_screenshot` 在超时分支与保存失败分支用非 0 退出码退出，而不是 `ViewportCommand::Close` 后静默结束。
- 出图参数由命令行注入（`--screenshot <path> --heading <deg> --bagua <xian|hou> --rings <mask>`），默认值必须与 `LuopanApp::new` 的初值同源，禁止在 `software.rs` 里另写一份 `heading = 0.0`。

---

### A-02 [高] 兼向邻山在正北区间回绕错误：显示「兼向坤」而应为「兼向壬」

**文件行号**
- `app/src/main.rs` L447-L455（失效点 L452）
- `app/src/dial.rs` L248-L253（消费点，用户可见）
- 交叉验证：`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` L236（同一逻辑的正确写法）

**代码片段（逐字）**

`app/src/main.rs` L447-L455：

```rust
fn rd_offset_label(rd: &Reading) -> String {
    let off = rd.offset;
    if off.abs() * 2.0 <= ZHENG_XIANG_DEG {
        format!("正向 ({:+.1}°)", off)
    } else {
        let neighbor = if off < 0.0 { rd.mountain.wrapping_sub(1) % 24 } else { (rd.mountain + 1) % 24 };
        format!("兼向{}({:+.1}°)", MOUNTAINS[neighbor], off)
    }
}
```

`app/src/dial.rs` L248-L253：

```rust
    cv.text_centered(
        Pos2::new(cx, cy + 34.0),
        &format!("{} · {}", crate::rd_offset_label(&rd), YINYANG[rd.mountain]),
        13.0,
        fg.gamma_multiply(0.8),
    );
```

`app/android/app/src/main/java/com/luopan/fengshui/CompassData.java` L236：

```java
                int n = offset < 0 ? (m + 23) % 24 : (m + 1) % 24;
```

**触发条件**
朝向 `h ∈ [352.5°, 355.5°)`（`reading_for` 给 `mountain = 0`（子）、`offset ∈ (-7.5, -4.5]`，`|off|*2 > 9` 判为兼向、`off < 0` 走负分支），盘心读数行触发（`dial.rs` L240-L253，每帧绘制）。

**预期 vs 实际**
- 预期：子山（索引 0）逆时针一侧的邻山是壬（索引 23），显示 `兼向壬(-6.0°)`。依据：`MOUNTAINS` 顺序为 子→癸→…→壬 递增 15°（`main.rs` L58-L63），Android 侧同逻辑写的是 `(m + 23) % 24`（`CompassData.java` L236）。
- 实际：`0usize.wrapping_sub(1) % 24 = usize::MAX % 24 = 15`（2⁶⁴−1 ≡ 15 mod 24），`MOUNTAINS[15]` 是「坤」（`main.rs` L58-L63：索引 0 子、1 癸 … 15 坤），显示 `兼向坤(-6.0°)` —— 坤的中心在 225°（索引 15 × 15°），而子山逆时针紧邻的壬中心在 345°，即错到对侧方向的邻山。

**证据等级**：已证实（纯整数回绕，可复现推演；同一语义在 Android 侧有正确实现可对照）。

**修复方向**
把 `main.rs` L452 的 `rd.mountain.wrapping_sub(1) % 24` 改成 `(rd.mountain + 23) % 24`（与 `CompassData.java` L236 同式）；并在 `mechanics_pin` 测试模块加 `reading_for(354.0)` 断言 `rd_offset_label` 含「壬」。

---

### A-03 [高 · 交叉引用，不单独计数] 同一朝向，盘心「正/兼向」与右栏「正/兼向」取自两套互不相容的来源，同屏给出两个答案（母条：**B-10**）

> 技术总监去重裁定：双盘读数口径矛盾归 B。本条是同一缺陷在桌面端的证据，**不单独计数**；99 中以 B-10 为母条，本条按 §7 证据索引交叉引用。裁决依据：`docs/review/98-report-conventions.md` §2 规则 3（同一事实取最直接证据为母条）。

**文件行号**
- `app/src/main.rs` L407（右栏取表格）、L447-L455（盘心取公式）
- `app/src/main.rs` L144（表格定义：`TYPE_14` `interval 3.0 / offset 0.0`，`labels[0] = 癸山九度外`）
- `app/src/main.rs` L134（`TYPE_4` 地盘 `offset 7.5`，`labels[0] = 癸山`）
- `app/src/dial.rs` L248-L253（盘心显示）

**代码片段（逐字）**

`app/src/main.rs` L405-L407：

```rust
        row(ui, "后天卦", ring_text_clean(2, rd.heading), Color32::WHITE);
        row(ui, "先天卦", ring_text_clean(1, rd.heading), Color32::WHITE);
        row(ui, "正/兼向", format!("{} ({:+.1}°)", ring_text_clean(14, rd.heading), rd.offset), Color32::WHITE);
```

`app/src/main.rs` L134（截取该行前 120 字符；整行为 24 个 label 的单行，完整原文见 L134）：

```rust
    RingDef { id: "TYPE_4", text: "地盘正针二十四山", interval: 15.0, offset: 7.5, labels: &["癸山", "丑山", "艮山", "寅山", "甲山", "卯山", "乙山", "辰山", "巽山", "巳山",
```

`app/src/main.rs` L144（截取该行前 120 字符；整行为 120 个 label 的单行，完整原文见 L144）：

```rust
    RingDef { id: "TYPE_14", text: "正兼向度数指标", interval: 3.0, offset: 0.0, labels: &["癸山九度外", "癸山九度内", "癸山九度内", "癸山九度内", "癸山九度外",
```

**触发条件**
任意朝向，罗盘页同时绘制右栏 `reading_panel` 与盘心读数（`main.rs` L552-L557 右栏 + `dial.rs` L240-L253 盘心）。最直接的样例是 `heading = 0.00°`：
- 盘心：`reading_for(0.0)` → `mountain = 0`、`offset = 0.0` → `rd_offset_label` 走正向分支 → 显示 `正向 (+0.0°)`（`main.rs` L449-L450）。
- 右栏：`ring_index(14, 0.0)` = `floor(((0−0) mod 360)/3) = 0` → `labels[0]` = `癸山九度外` → 显示 `癸山九度外 (+0.0°)`（`main.rs` L144 + L407），而同一栏上一行是 `地盘正针 子山`（L395）。

**预期 vs 实际**
- 预期：同一朝向的「正/兼向」只能有一个结论，且必须与「地盘正针」的山名同侧（0° 属子山、正向）。
- 实际：同一屏出现「子山 + 正向」与「癸山九度外」两个互斥结论；且该表在 `offset = 0.0` 时比地盘（`offset = 7.5`）整体前移 7.5°：例如 `h = 3.0°` 时地盘为子山，表给的是「癸山九度内」，`h = 345°` 时地盘为壬山，表给的是「子山九度外」。

**证据等级**：已证实（两条读路径均在代码中，数值可复现推演）。**哪一侧是原版口径**进「待确认 A-Q01」，不影响本条成立：同屏互斥本身即缺陷。

**修复方向**
确定唯一来源后再改，二选一：(1) 若以表为准 → `main.rs::rd_offset_label` 删除，盘心改显示 `ring_text_clean(14, heading)`；(2) 若以公式为准 → 修正 `TYPE_14` 的 `offset`（在 `app/mechanics.json` 源头改并重新生成 `main.rs`/`CompassData.java`），使 5 格/山的边界与地盘 15° 格对齐；两侧不得同时显示两张来源。`CompassView.java` L298-L299 与 `MainActivity.java` L641 为同一缺陷的 Android 侧（母条 B-10）。

---

### A-04 [中 · 交叉引用，不单独计数] 百二十分金 / 穿山七十二龙 刻度网格忽略 `ring.offset`，刻度画在数据格中心而非格边界；同一函数内 60 龙却加了偏移（母条：**B-09**）

> 技术总监 2026-11-05 裁定：其余疑似同因组按「母条归实现模块（双端同源归 B）」合并。本条与 B-09 同根因（双端各写了一份「从 0 起刻度」），**不单独计数**；99 中以 B-09 为母条，本条按证据索引交叉引用。两侧同为「中」，合并后级别不变。

**文件行号**
- `app/src/dial.rs` L127-L138（分金刻度）、L140-L150（72 龙刻度）、L152-L164（60 龙刻度，加了偏移）
- `app/src/main.rs` L135（`TYPE_5` `offset 2.5`）、L136（`TYPE_6` `offset 1.5`）

**代码片段（逐字）**

`app/src/dial.rs` L127-L138：

```rust
    // 120 分金 ring (3° ticks) + 甲子 labels.
    if st.rings.fenjin {
        for d in (0..360).step_by(FENJIN_STEP as usize) {
            let p0 = screen_pos(cx, cy, r * 0.845, d as f32, heading);
            let p1 = screen_pos(cx, cy, r * 0.825, d as f32, heading);
            cv.line(p0, p1, Color32::from_rgb(140, 120, 90), 1.0);
        }
        ring(cv, cx, cy, r, 0.845, 0.82, Color32::from_rgb(120, 100, 70), 1.0);
        // B2: 120 cells x 5-char labels cannot fit r=232 (3 deg/cell); the table
        // text is shown in the reading panel, the ring keeps ticks for orientation.
        cell_marker(cv, cx, cy, r, 6, heading, 0.852, 0.813, Color32::from_rgb(255, 216, 120));
    }
```

`app/src/dial.rs` L154-L157（60 龙正确加了偏移，作为同函数内自相矛盾的证据）：

```rust
    if st.rings.dragon60 {
        for k in 0..60 {
            let d = DRAGON60_OFFSET + k as f32 * DRAGON60_STEP;
            let p0 = screen_pos(cx, cy, r * 0.78, d, heading);
```

`app/src/main.rs` L136（截取该行前 110 字符；整行为 120 个 label 的单行）：

```rust
    RingDef { id: "TYPE_6", text: "地盘正针百二十分金", interval: 3.0, offset: 1.5, labels: &["甲山 庚子", "甲山 壬子", "癸山 甲子",
```

**触发条件**
勾选「分金」或「七十二龙」（默认全开，`dial.rs` L37-L49），任意朝向绘制罗盘时。

**预期 vs 实际**
- 预期：刻度线落在表定义的数据格边界上，即 `ring.offset + k*interval`（分金 `1.5 + 3k`，72 龙 `2.5 + 5k`），与 `ring_index()`（`main.rs` L425-L431）划分的格一致；`cell_marker` 画在格中心（`ring_center`，L438-L441）。
- 实际：分金刻度画在 `0, 3, 6 …`（`d as f32`，无 offset），72 龙画在 `0, 5, 10 …`。因为格中心 = `1.5+(i+0.5)*3 = 3+3i` 与刻度 `3k` 完全重合，**每根刻度都落在格中心**，格子边界反而没有刻度；`cell_marker`（L137、L149）画在格中心，于是高亮线与普通刻度同角度叠在一起，用户会把刻度当成格界 → 读分金/72 龙时错半格（1.5°/2.5°）。

**证据等级**：已证实（刻度角度、格边界、格中心三者都由代码直接给出）。

**修复方向**
`dial.rs` L129-L133 与 L142-L146 的循环起点改为 `ring.offset + k*interval`（分金用 `RINGS[5].offset`、72 龙用 `RINGS[4].offset`，或直接读 `RINGS[n].offset`/`interval`，禁止再写字面量），使刻度与 `ring_index()` 同源；`cell_marker` 保持格中心不动。Android 侧 `CompassView.java` L198-L209 为同一缺陷的母条（B-09），两端改法相同。

---

## 2 模块必查项逐项结论

| # | 必查项 | 结论 | 证据 |
|---|---|---|---|
| ① | 三盘读数与圈层自洽（`plate()` vs 圈层角度） | **部分命中**：三面 24 山与读数自洽（见下推演，无缺陷）；**分金/72 龙刻度与数据格错位**（A-04，母条 B-09）；**正/兼向双源**（A-03，母条 B-10） | `dial.rs` L96-L103、L174-L186；`main.rs` L235-L258、L394-L413 |
| ①补充 | `plate()` 与圈层角度是否同角向 | **命中（自洽）**：`plate()` 在 `m*15 + offset` 处画字，读数按山名索引取，二者同源 | 见下推演表 |
| ② | 传感器/WebView 生命周期与泄漏 | **不适用**：M1 无传感器、无 WebView；桌面生命周期相关只剩 `--screenshot-ui` 的超时关窗，已并入 A-01 | `main.rs` L288-L313 |
| ③ | `--screenshot` 走 `software.rs` 而非真 UI 导致验收失真 | **命中** | A-01 |
| ④ | 双 WebView 状态污染 | **不适用**（桌面端无 WebView，地图功能未实现，见 `main.rs` L1-L6 模块注释） | — |
| ⑤ | 触摸层级 | **不适用**（无底图、无叠加层）；桌面仅 `draw_compass` 的拖拽改朝向与 Grab 光标 | `main.rs` L315-L343 |

**① 三盘自洽推演（结论：自洽，无缺陷）**

| heading | `reading_for().mountain`（`main.rs` L241） | 地盘画字位置（`dial.rs` L176：`m*15+0`） | 人盘：读数（`main.rs` L399 `floor(h/15)+1`） | 人盘画字（`dial.rs` L180：`m*15−7.5`） | 天盘：读数（`main.rs` L400 `floor(h/15)`） | 天盘画字（`dial.rs` L184：`m*15+7.5`） |
|---|---|---|---|---|---|---|
| 0.0° | 0 子 | 子 @ 0° | 1 = 癸 | 癸 @ 7.5°（指针 0° 落在 [0,15) 格） | 0 = 子 | 子 @ 7.5° |
| 3.0° | 0 子 | 子 @ 0° | 1 = 癸 | 癸 @ 7.5° | 0 = 子 | 子 @ 7.5° |
| 15.0° | 1 癸 | 癸 @ 15° | 2 = 丑 | 丑 @ 22.5° | 1 = 癸 | 癸 @ 22.5° |
| 354.0° | 0 子 | 子 @ 0° | `floor(354/15)+1 = 24 → 0` = 子 | 子 @ 352.5°（i=0：`0*15−7.5 ≡ 352.5`，格 [345,360) 含 354°） | 23 = 壬 | 壬 @ 352.5°（`23*15+7.5`） |

人盘/天盘的「表偏移 1 个山」与「物理 ±7.5° 旋转」互为等效（`HUMAN_OFFSET = −7.5`、`HEAVEN_OFFSET = +7.5`，`main.rs` L151-L152），三种取法在格中心与格边界上一致；`mechanics_pin` 的边界向量（`main.rs` L678-L700）也覆盖了 0/7.4/7.5/90/180/270/352.5/359.99/360/720.5/−0.01。**该项未发现缺陷。**

---

## 3 高度可疑（不进正式缺陷清单）

### A-Q01 [高度可疑 · 中] `software.rs` 与 egui 真 UI 的布局一致性没有任何自动化判据，只靠注释与同名常量维持

- 线索：`main.rs` L525-L527 的注释声明「Fixed chrome so the window and the headless artifact (software.rs) agree on where the dial sits」；`dial.rs` L58-L70 定义 `TOP_H/LEFT_W/RIGHT_W` 与 `central_dial`，真 UI 侧实际用的是 `ui.available_size()` 反推（`main.rs` L316-L321、`draw_compass`），并不调用 `central_dial`。
- 已掌握证据：`main.rs` L316-L321、`dial.rs` L64-L70、`software.rs` L265。当前 egui 0.31.1 的 `SidePanel::exact_width` 把面板宽度（含内部 margin）设为给定值（`egui-0.31.1/src/containers/panel.rs` L246-L253、L294），因此 1100×780 下两侧面板与顶栏合计正好是 620/38，两套算法此刻结果相同（r=232）。
- 缺什么证据：没有任何测试或脚本比对「真 UI 截图里盘心像素坐标」与 `central_dial()` 的输出；一旦 egui 变更 `exact_*` 语义或改成 `min/max` 语义，偏差不会被任何检查发现。
- 由谁补：LC审测试（在 AT 里加像素坐标断言）或本模块在批次二加单测（用 `central_dial` 与假想的 panel rect 对照）。
- 补齐后的判据：PASS = 存在断言把 `central_dial(1100,780)` 的 `(cx,cy,r)` 与真窗口盘心 `(cx',cy',r')` 的误差锁在 ≤1px；FAIL = 无此类断言。

---

## 4 待确认（供 99 的 Q 编号，本报告用 A-Q02/A-Q03 占位）

### A-Q02 [待确认] `TYPE_14` 正兼向表的角向基准（0° 是子山中心还是子癸界）是原版口径还是重建引入

- 线索：A-03。表 `offset = 0.0` + `labels[0] = 癸山九度外` 意味着「癸山 = [0°,15°)」，与地盘 `TYPE_4` 的 `offset = 7.5`（癸山 = [7.5°,22.5°)）相差 7.5°。
- 已掌握证据：`main.rs` L134（TYPE_4 头）、L144（TYPE_14 头）、L407（右栏消费）；`CompassData.java` L98/L108（同表同值）。
- 缺什么证据：原版 DEX 中该 ring 的 `offsetDegrees` 与 labels 取用方式（是否在绘制时另加旋转），或原版真机「读盘」弹层在已知朝向下的 正兼向 行文字。
- 由谁补：LC审证据（`.inspect` 反编译产物）/ LC审文档（`MECHANICS.md` 数值来源）。
- 补齐后的判据：PASS = 原版在 0° 显示「正向/子山九度内」一类与地盘同侧的文字 → 判定表 offset 需修正；FAIL = 原版在 0° 显示「癸山九度外」→ 判定公式侧需修正、且 A-03 的两行需保持同源。

### A-Q03 [待确认] 验收流程是否以 `--screenshot` 产物作为通过判据（决定 A-01 是否升为阻断）

- 线索：A-01。`scripts/build-desktop.ps1`、`scripts/check-staged-paths.ps1` 是否调用 `--screenshot` 并只看退出码，属 M4 范围，本报告未读。
- 已掌握证据：`main.rs` L613-L616、`software.rs` L314（失败仍 exit 0）。
- 缺什么证据：`scripts/build-desktop.ps1`（195 行）中 `--screenshot` 的调用与判据原文。
- 由谁补：LC审服务端（M4 审查人）。
- 补齐后的判据：PASS = 脚本校验产物存在且非空 → A-01 维持「高」；FAIL = 只看退出码 → A-01 升「阻断」。

---

## 5 与文档一致性（D12）

| # | 文档引用 | 基线事实 | 判定 |
|---|---|---|---|
| 1 | `docs/gap-matrix.md` L107（D3）：「`main.rs:713-716` 走 `software::software_screenshot`」「`main.rs:231-256` egui 截图代码为死代码」 | `app/src/main.rs` 全文仅 701 行（与 `00-MATRIX.md` L45 一致），**不存在 L713-L716**；基线 `main.rs` L613-L616 才是 `software_screenshot` 入口，L231-L256 是 `reading_for` 与 `Reading` 结构，无截图代码；`handle_screenshot`（L288-L313）经 `--screenshot-ui`（L604-L607、L628）是活代码 | 文档行号与基线版本错位，且「死代码」判断在基线上不成立（已证实） |
| 2 | `docs/gap-matrix.md` L121（E6）：桌面开关在「`main.rs:210-232`」 | 基线 `main.rs` L210-L232 无 `ShowRings`；`ShowRings` 定义在 `app/src/dial.rs` L27-L49，勾选框在 `main.rs` L541-L547 | 行号错位（已证实） |
| 3 | `docs/gap-matrix.md` L131（F4）：桥调用在「`MainActivity.java:247-248`」 | 基线 `MainActivity.java` L247-L248 是 `registerDebugBridge()` 的 catch 分支；`setHeading/setSitFace` 注入在 L684-L685 | 行号错位（已证实，属 M2 文档交叉） |

影响：以这三处行号做抽样追溯必然对不上。建议 LC审文档 按基线全量重标行号；本报告所有行号均以基线 HEAD 现场 read 为准。

---

## 6 测试覆盖缺口（D10）

- 桌面端全部单测集中在一个模块：`app/src/main.rs` L637-L700，共 4 个 `#[test]`（`ring_scalars_match_reading_literals`、`table_lengths`、`b2_vectors_match_dex_tables`、`readings_pinned_at_boundaries`）。
- 无任何测试覆盖：`dial.rs`（254 行，含 A-04 的刻度角度）、`ring_index/ring_center` 的格边界、`rd_offset_label`（A-02 的 352.5–355.5° 区间无断言）、`software.rs`（321 行，零测试）。
- `readings_pinned_at_boundaries` 的向量集（L679-L692）覆盖 `-0.01/0/7.4/7.5/90/180/270/352.5/359.99/360/720.5`，但**没有一条落在 兼向 负分支**（`|off| > 4.5` 且 `off < 0`），因此 A-02 漏网；`352.5` 一条的 `offset = −7.5` 恰好 `|−7.5|*2 = 15 > 9` → 进兼向分支，但该断言只比对山名 `want`（L693），未断言 `rd_offset_label` 的邻山文字，故仍不覆盖。

---

## 7 已读文件清单与覆盖声明

| 文件（相对 review-base） | 行数 | 覆盖 |
|---|---|---|
| `app/src/main.rs` | 701 | 全文 |
| `app/src/dial.rs` | 254 | 全文 |
| `app/src/software.rs` | 321 | 全文 |
| `app/Cargo.toml` | 18 | 全文 |
| `app/Cargo.lock` | 4112 | 仅抽查 4 条依赖版本（egui/eframe 0.31.1、image 0.25.10、fontdue 0.9.5） |
| `C:\Users\Administrator\.cargo\registry\...\egui-0.31.1\src\containers\panel.rs`（依赖源码，非基线） | — | 仅读 L228-L297，用于核对 `exact_width` 的宽度语义 |
| `docs/review/00-MATRIX.md` | 253 | 全文 |
| `docs/review/98-report-conventions.md` | 432 | 全文 |
| `docs/gap-matrix.md` | 185 | 抽查 L100-L139 |

**覆盖声明（M1）**：M1 的 3 个源文件（`main.rs`、`dial.rs`、`software.rs`）**已全覆盖逐行阅读，M1 无未读文件**；逐文件「读了几行/为什么没读完」见 §11。**未执行** `cargo build/test`（避免在只读基线工作区 `E:\APPDetector\review-base` 生成 `target/` 污染共享只读区），故 A-09 的「零调用点」以 `grep` 结果为准、未经编译器实证（该条仍列为已证实：调用点是否存在是源码事实，与是否编译无关）。

---

## 8 第一批计数（第一批落盘时）

- 已证实：**4**（高 3：A-01/A-02/A-03；中 1：A-04）——注：A-03 与 A-04 其后按技术总监去重裁定转为交叉引用（母条分别为 B-10、B-09），第一批当时的计数仅作历史记录
- 高度可疑：**1**（A-Q01）
- 待确认：**2**（A-Q02、A-Q03）
- 两批合计数见 §12。

---

## 9 第二批缺陷（中/低，已证实）

### A-05 [中] 窗口没有最小尺寸约束，中央面板可用宽度可为负；`central_dial` 用 `.max(320.0)` 兜底，窄窗时罗盘溢出面板并被裁切

**文件行号**：`app/src/main.rs` L316-L321、L618-L623；`app/src/dial.rs` L65-L70

**代码片段（逐字）**

`app/src/main.rs` L316-L321：

```rust
        let avail = ui.available_size();
        let size = avail.min_elem().max(320.0);
        let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::click_and_drag());
        let cx = rect.center().x;
        let cy = rect.center().y;
        let r = size * 0.5 - 8.0;
```

`app/src/dial.rs` L65-L70：

```rust
pub fn central_dial(w: f32, h: f32) -> (f32, f32, f32) {
    let avail_w = w - LEFT_W - RIGHT_W;
    let avail_h = h - TOP_H;
    let size = avail_w.min(avail_h).max(320.0);
    (LEFT_W + avail_w * 0.5, TOP_H + avail_h * 0.5, size * 0.5 - 8.0)
}
```

`app/src/main.rs` L618-L623（`NativeOptions` 未设 `with_min_inner_size`）：

```rust
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1100.0, 780.0])
            .with_title("罗盘 · 风水"),
        ..Default::default()
    };
```

**触发条件**：用户把窗口宽度拖到小于 `LEFT_W + RIGHT_W = 620`（`dial.rs` L61-L62）。

**预期 vs 实际**
- 预期：窗口应设最小尺寸（≥ `LEFT_W+RIGHT_W+320`），或在可用空间不足时等比缩小罗盘（`size = avail.min_elem().max(0.0)`）并保证不出面板。
- 实际：`avail_w` 为负（如窗口 400 宽 → `avail_w = −220`），`avail.min_elem()` 为负，`.max(320.0)` 把它抬到 320，于是向一个更窄的中央面板 `allocate_exact_size(320×320)` → 罗盘被面板 clip 裁掉两侧；同时 `central_dial` 的盘心 `x = 300 + (−220)/2 = 190` 落在左面板内部（`software.rs` 路径与真 UI 中心不一致）。无任何日志或最小尺寸拦截。

**证据等级**：已证实（无最小尺寸 + `.max(320)` 兜底 + 负可用宽度推演，均为代码事实）。

**修复方向**：`NativeOptions.viewport` 增加 `.with_min_inner_size([960.0, 640.0])`；`draw_compass` 的 `size` 改为 `avail.min_elem().min(1024.0).max(0.0)`，可用尺寸为 0 时直接 `return` 不分配。

---

### A-06 [中] 命令行参数解析：缺值/未知参数静默忽略，`--dump-rings` 非法数值回落 0.0，`--screenshot` 与 `--screenshot-ui` 同时给出时后者被静默丢弃

**文件行号**：`app/src/main.rs` L586-L592、L604-L607

**代码片段（逐字）**

`app/src/main.rs` L586-L592：

```rust
    while i < args.len() {
        if args[i] == "--screenshot" && i + 1 < args.len() {
            screenshot = Some(args[i + 1].clone());
            i += 2;
        } else if args[i] == "--dump-rings" && i + 1 < args.len() {
            let h: f32 = args[i + 1].parse().unwrap_or(0.0);
```

`app/src/main.rs` L604-L607：

```rust
        } else if args[i] == "--screenshot-ui" && i + 1 < args.len() {
            // Real egui window + deliverable PNG (needs a desktop session).
            screenshot_ui = Some(args[i + 1].clone());
            i += 2;
```

**触发条件**
1. `luopan --screenshot`（漏写路径）：`i + 1 < args.len()` 为假 → 落到末尾 `else { i += 1 }`（L608-L609）→ `screenshot` 保持 `None` → 静默进入真窗口模式。
2. `luopan --dump-rings abc`：`"abc".parse::<f32>()` 失败 → `unwrap_or(0.0)` → 以 0° 静默输出 14 行（工具对错误输入给出看似有效的结果）。
3. `luopan --screenshot a.png --screenshot-ui b.png`：`--screenshot` 分支先于 `--screenshot-ui`（L613-L616 的 `if let Some(path) = screenshot` 也先于 L628 的 `screenshot_ui`）→ 只出 `a.png`，`b.png` 被静默忽略。

**预期 vs 实际**
- 预期：参数数量/取值非法时 `stderr` 报用法并非 0 退出；互斥参数同时出现时报错。
- 实际：三种情况都静默成功（退出码 0），调用方无法从退出码区分「按参数执行」与「忽略参数执行」。

**证据等级**：已证实（三个分支的守卫与兜底均为代码事实）。

**修复方向**：解析改为严格模式——`--screenshot`/`--screenshot-ui` 缺值、`--dump-rings` 非数值、未知 `--*` 参数一律打印用法并 `exit 2`；`--screenshot` 与 `--screenshot-ui` 同时在命令行出现时报冲突错误。与 A-01、A-Q03 的验收判据合并执行。

---

### A-07 [低] 拖拽转盘只吃水平位移，垂直拖动完全无响应；灵敏度 0.5°/px 为硬编码常量

**文件行号**：`app/src/main.rs` L324-L329

**代码片段（逐字）**

```rust
        if response.dragged() {
            let d = response.drag_delta();
            // Screen-space drag to the right/left maps to heading change.
            let turn = -d.x * 0.5;
            self.heading = normalize_deg(self.heading + turn);
        }
```

**触发条件**：在罗盘上按住并上/下垂直拖动。

**预期 vs 实际**
- 预期：转盘类交互至少把垂直位移一并计入（例如按 `atan2` 绕心旋转），或明确只支持水平拖动并在 UI 上说明；灵敏度应为可读常量（如 `DEG_PER_PX`）而非魔法数。
- 实际：只用 `d.x`，垂直拖动 `turn = 0` → 用户看不到任何反馈（只有 `CursorIcon::Grab`，L330-L332），会误判功能失灵；`0.5` 为字面量，改灵敏度要改公式。

**证据等级**：已证实（只消费 `d.x`）。

**修复方向**：改为绕圆心角度差驱动（与 Android 侧 `CompassView.onTouchEvent` L134-L142 的 `atan2` 方案一致），或保留水平方案但把 `0.5` 提为 `const DEG_PER_PX: f32 = 0.5;` 并在标题栏注明「左/右拖动转向」。

---

### A-08 [低] 「朝向」滑块范围 `0.0..=360.0` 与归一化朝向冲突：可停在 360.00°，与 0° 同一朝向却显示不同、且 `坐向(转180°)` 行为随之改变

**文件行号**：`app/src/main.rs` L374-L377

**代码片段（逐字）**

```rust
        ui.horizontal(|ui| {
            ui.label("朝向");
            ui.add(egui::Slider::new(&mut self.heading, 0.0..=360.0).suffix("°"));
        });
```

**触发条件**：拖动滑块到最右端（`heading = 360.0`）。

**预期 vs 实际**
- 预期：朝向是循环量，滑块上限应为 `360.0` 的「开区间」（`0.0..=359.99`）或在写回时 `normalize_deg`，使 360° 与 0° 显示一致。
- 实际：`heading = 360.0` 时盘心显示 `360.0°`（`dial.rs` L241：`format!("{:.1}°", rd.heading)`；`reading_for` 内部已归一化为 0），而右栏「度数」显示 `360.00°`（L394 用 `rd.heading`）——同一朝向两个度数文本；此时按「坐向(转180°)」（L382-L384）得到 180°，与从 0° 出发的结果相同，尚不致错，但滑块与标题栏文案 `向/坐` 的基准不直观。

**证据等级**：已证实（范围与显示路径均在代码中）。

**修复方向**：滑块改为 `0.0..=359.9`，或在 `Slider` 之后立即 `self.heading = normalize_deg(self.heading);`。

---

### A-09 [低] 死代码与未使用导入：`wangxiang/borrow_branch/nayin_of` 全仓零调用点，`dial.rs`/`software.rs` 各带未使用导入；同一套干支逻辑在 Android 侧却是活代码

**文件行号**：`app/src/main.rs` L182-L190（`wangxiang`）、L164-L180（`borrow_branch`）、L192-L194（`nayin_of`）；`app/src/dial.rs` L12-L17（导入表含 `JIAZI`）；`app/src/software.rs` L9-L12（导入 5 个未使用符号）

**代码片段（逐字）**

`app/src/main.rs` L182-L190：

```rust
/// A 旺相 Jiazi name for a branch + slot (slot 0..5 for fenjin, 0..3 for dragons).
fn wangxiang(branch: usize, slot: usize) -> &'static str {
    // Odd-indexed branches are yin; even-indexed are yang.
    let stems = if branch % 2 == 0 { YANG_STEMS } else { YIN_STEMS };
    let stem = stems[slot % 5];
    // Return a static string by matching (we only have 12*5=60 combos, all in JIAZI).
    let target = format!("{}{}", stem, BRANCHES[branch]);
    JIAZI.iter().find(|j| **j == target).copied().unwrap_or("甲子")
}
```

`app/src/dial.rs` L12-L17：

```rust
use crate::{
    gua_color, gua_wuxing, reading_for, screen_pos, wuxing_color, RINGS,
    BAGUA_STEP, DRAGON60_OFFSET, DRAGON60_STEP, DRAGON72_STEP, EARTH_OFFSET, FENJIN_STEP,
    HEAVEN_OFFSET, HEXAGRAM64_STEP, HUMAN_OFFSET, JIAZI, MOUNTAIN_STEP, MOUNTAINS, WUXING,
    YINYANG,
};
```

`app/src/software.rs` L9-L12：

```rust
use crate::{
    borrow_branch, nayin_of, reading_for, HOUTIAN_GUA, JIAZI, MOUNTAINS, WUXING, XIANTIAN_GUA,
    YINYANG,
};
```

**触发条件**：静态代码事实，不需要运行态；`grep -rn "wangxiang\|borrow_branch\|nayin_of" app/src` 只命中定义行（`main.rs` L164/L183/L192）与 `software.rs` L10 的导入，无任何调用点。

**预期 vs 实际**
- 预期：`Reading` 的分金/72 龙/60 龙既已统一改由 `rings[].labels` 表取值（`main.rs` L410-L412），桌面就不应再保留公式实现；若保留，应被 `#[cfg(test)]` 或显式 `#[allow(dead_code)]` + TODO 标注。
- 实际：三个函数与 `JIAZI/NAYIN` 表留在非测试代码中且无调用点；`dial.rs` 导入 `JIAZI` 未用、`software.rs` 导入 `borrow_branch/nayin_of/HOUTIAN_GUA/XIANTIAN_GUA/JIAZI` 未用（编译器 `unused_imports` 级告警；本审查未执行编译，见 §11）。Android 侧 `CompassData.java` L219-L224 用的是同一套逻辑（活代码），桌面这份是「双实现中的死分支」——两边结果一旦分叉（表含「正癸」，公式只能产出干支，`CompassData.java` L99 vs L166-L173），无人会发现。

**证据等级**：已证实（调用点缺失是源码事实；「是否产生编译告警」未实测，不影响结论）。

**修复方向**：删除 `main.rs` L164-L194 三个函数与 `JIAZI/NAYIN` 常量（或整体 `#[cfg(test)]`），同步清理 `dial.rs` L15 与 `software.rs` L10 的未用导入；在 `mechanics_pin` 中改为直接断言 `RINGS[5]/[6]/[7]` 的表取值。

---

## 10 必查项缺口（第一批未闭环项）

| 必查项 | 状态 | 缺什么 | 由谁补 / 判据 |
|---|---|---|---|
| ① 三盘自洽 | 闭环（结论：三面自洽，另两处不自洽见 A-03→B-10、A-04→B-09） | — | — |
| ② 传感器/WebView 生命周期 | **不适用**（M1 无传感器/WebView），理由已写 §2 | — | — |
| ③ `--screenshot` 验收失真 | 代码层闭环（A-01） | 验收脚本是否只看退出码（A-Q03） | LC审服务端（M4） |
| ④ 双 WebView 状态污染 | **不适用**（M1 无 WebView） | — | — |
| ⑤ 触摸层级 | **不适用**（M1 无底图/叠加层） | — | — |

## 11 未覆盖文件清单（读了几行 / 为什么没读完）

M1 共 3 个文件，**3/3 逐行读完，无未覆盖文件**。下列是「我引用过但不属 M1」的文件，逐个说明阅读深度：

| 文件 | 总行数 | 我读了多少 | 为什么没读完 / 用途 |
|---|---|---|---|
| `app/Cargo.toml` | 18 | 18（全文） | 属 M7；只用于取依赖声明（`eframe = "0.31"`）。A-09 与 `Cargo.toml` 相关的配置项（`opt-level`、缺 `rust-version`）我不列条，交 M7 |
| `app/Cargo.lock` | 4112 | 4 条记录 | 属 M7；只需锁版本号（egui/eframe 0.31.1、fontdue 0.9.5、image 0.25.10）核对 A-Q01 的 egui 行为，不做依赖审计 |
| `app/MECHANICS.md` | 505 | **0 行** | 属 M7；A-Q02 需要的是原版 DEX 侧证据，不在此文件；本报告未引用其正文 |
| `app/mechanics.json` | 1605 | **0 行** | 属 M7；A-03/A-04 只用到 `main.rs` 内已生成的表（注释标明来源与 sha256，`main.rs` L56），未回源头比对 |
| `docs/gap-matrix.md` | 185 | 40 行（L100-L139） | 属 M6；只为 §5 的文档错位取证，不做全量文档审计 |
| `scripts/**`、`tests/**`、`app/assets/**` | — | **0 行** | 分属 M4/M5/M3；A-Q03 的判据要 M4 补 |

**未执行**：`cargo build` / `cargo test` / `cargo clippy`。原因：`E:\APPDetector\review-base` 是六名审查员共用的只读基线，`cargo` 会在其下生成 `target/`（数百 MB）并可能改 `Cargo.lock`，违反「不改任何源码 / 只读基线」纪律。A-Q01 因此保留「高度可疑」而非升为缺陷。

---

## 12 两批合计计数（已按技术总监 2026-11-05 裁定去重）

- 已证实：**7**
  - 高 **2**：A-01、A-02
  - 中 **2**：A-05、A-06
  - 低 **3**：A-07、A-08、A-09
- 交叉引用（不计数，共 **2** 条）：
  - **A-03** → 母条 **B-10**（`11-M2-android.md`；B-10 已按裁定升为高，两侧定级一致）
  - **A-04** → 母条 **B-09**（`11-M2-android.md`；两侧同为中，合并后级别不变）
- 历史口径提示：第一批落盘时为「已证实 4（高 3 / 中 1）」，若 99 按旧口径统计，把 A-03、A-04 加回即为 9 条（高 3 / 中 3 / 低 3）。
- 高度可疑：**1**（A-Q01）
- 待确认：**2**（A-Q02、A-Q03）
- 必查项：① 闭环（含两处不自洽）；②④⑤ 不适用（理由已写 §2）；③ 代码层闭环、验收脚本侧缺口在 A-Q03。
- 未覆盖：M1 无（3/3 全文）；跨模块引用文件的阅读深度见 §11。

---

## 13 订正记录（按技术总监 2026-11-05 裁定 + `91-audit.md` §3/§8.3 订正清单）

只列被改动的条目；未列条目与片段一字未动。

| 条目 | 证据原文（基线逐字） | 行号（原 → 订正后） | 改了什么 |
|---|---|---|---|
| **A-01** | `pub fn software_screenshot(path: &str) {` … `    if let Some(f) = &font {`（`app/src/software.rs` L237 起） | L237-L250 → **L237-L249** | 区间收到实际末行（L250 是 `cv.text(...)`，不在本段语义内），片段文字未动 |
| **A-01** | `    // Save PNG.` … `        image::ColorType::Rgba8,` / `    );` / `}`（`app/src/software.rs` L310 起） | L310-L320 → **L310-L321** | 区间放到实际末行（存图调用跨 L314-L320，函数闭合 `}` 在 L321），片段文字未动 |
| **A-03** | `        row(ui, "正/兼向", format!("{} ({:+.1}°)", ring_text_clean(14, rd.heading), rd.offset), Color32::WHITE);`（`app/src/main.rs` L407） | 无行号变化；级别「高」保留 | 转交叉引用：母条 B-10（M2），本条不单独计数；B-10 已由中升为高，两侧定级统一 |
| **A-04** | `        for d in (0..360).step_by(FENJIN_STEP as usize) {`（`app/src/dial.rs` L129，72 龙 L142 同构） | 无行号变化；级别「中」保留 | 转交叉引用：母条 B-09（M2），本条不单独计数；按裁定「双端同源归实现模块」，两侧同为中，合并后级别不变 |
