# 轮 C · 桌面双渲染器统一与差异清单

> 验收口径（技术总监）：同一 heading 两张图，除顶栏/侧栏外逐元素一致 + 差异清单；数值/文字以 DEX 为准，半径布局以 Android 为参考、桌面自适应。
> 证据：`checks/round-c/desktop-software.png`（1100×780，`--screenshot`）与 `checks/round-c/desktop-ui.png`（1650×1170 = 1100×780 @1.5×，`--screenshot-ui`）。两者均 heading = 0.00°。

## 1. 统一方式

罗盘盘面只有一份绘制代码：`app/src/dial.rs` 的 `draw_dial()`，通过 `DialCanvas` trait 接到两个后端：

| 后端 | 文件 | 实现 |
|---|---|---|
| 真实窗口 | `app/src/main.rs` → `PainterCanvas` | egui `Painter`（抗锯齿文字/线） |
| 无头产物 | `app/src/software.rs` → `SoftCanvas` | fontdue 像素画布（`Canvas`） |

窗口 chrome 尺寸也共用：`dial::TOP_H = 38`、`dial::LEFT_W = 300`、`dial::RIGHT_W = 320`，`dial::central_dial(1100,780)` 给出 `(540, 409, 232)`。egui 侧用 `exact_height`/`exact_width` + 零 margin 的 CentralPanel 保证中央区正好是 480×742。

**几何实测**（Python 对两张图取外圈金环弦长反推）：

- software：中心 x=540.0、r=231.7
- ui（换算到 1100×780 逻辑坐标）：中心 x=539.7、r≈233.7（含 2px 描边）

中心一致，半径差 ≤2px（恰好是描边宽度）。

## 2. 逐元素对照

heading=0.00° 时两张图都包含且一致的元素：
度数刻度（5°/标签 30°）、120 分金 3°刻度、72 龙 5°刻度、60 龙 6°刻度（边界 1.5+6k）、64 卦 5.625°刻度、地盘/人盘/天盘三盘文字、八卦环 8 卦 + 五行、红三角「向」、底部「坐」、中心读数 `0.0° / 子山·水 / 正向 (+0.0°) · 阴`、十字线。
读数：地盘子山、人盘癸山、天盘子山；分金 戊子·霹雳火、七十二龙 丙子·涧下水、六十龙 癸亥·大海水。

## 3. 差异清单（均已知、均非数值差异）

| # | 差异 | 方向 | 原因 | 是否影响验收 |
|---|---|---|---|---|
| 1 | 字形/字宽/字高 | software 的 CJK 字比 egui 略宽、基线略低 | egui 自带字体排版 vs fontdue+simhei 逐字光栅化 | 否（元素与位置一致，非逐像素） |
| 2 | 线宽 | software 圆环/刻度一律 1px 抗锯齿；egui 有 1.0/1.5/2.0 三档 | `SoftCanvas` 的 `circle_stroke`/`line` 不带宽度参数 | 否，列入下一轮可选优化 |
| 3 | 抗锯齿 | 边缘灰度不同 | 两种光栅化后端 | 否 |
| 4 | 顶栏 | egui 有八卦切换 + 7 个圈层 checkbox；software 只有标题文字 | 顶栏不参与对照 | 否（验收已排除顶栏） |
| 5 | 左栏 | egui 是可折叠知识库；software 是 8 行纯文字 | 侧栏不参与对照 | 否（验收已排除侧栏） |
| 6 | 右栏控件 | egui 有朝向滑条、复位/坐向按钮；software 只有读数行 | 侧栏不参与对照 | 否（验收已排除侧栏） |
| 7 | 右栏行数 | software 已补齐「正/兼向」「坐」，与 egui 行集一致（不含滑条与按钮） | 本轮同步 | 否 |
| 8 | 截图分辨率 | software 1100×780；ui 1650×1170 | 系统 1.5× DPI；egui 按逻辑像素布局 | 否，比对时按 1.5 换算 |
| 9 | 中心读数小数 | 两者都是 `0.0°`；右栏「度数」均为 `0.00°` | 同一 `reading_for` | 否 |

## 4. 文案诚信（D-008）

- 桌面 `main.rs` 说明文字已改为「说明：分金/龙文字为教材式命名，待换原版表（轮 B2）。」
- `software.rs` 右下同款说明同为「（分金/龙文字仍为教材式命名，待轮 B2 换原版表）」。
- Android 侧全文检索 `核对一致|已与反编译`：**0 命中**（`CompassData.java` 只有知识库中性描述），无需改。

## 5. 回归影响

- `--screenshot` 仍输出 1100×780、非空、三角与读数完整；内容因统一布局而变化（盘面从 600px 缩到与真实窗口一致的 464px），**轮 C 新基线覆盖 `checks/round-c/`，未覆盖 `round-17`/`round-b`**。
- `--screenshot-ui` 现在与 `software.rs` 共用同一份盘面代码，后续改盘面只需改 `dial.rs` 一处。
- 数值测试 `tests/numeric` 的解析器需要从 `main.rs` 改读 `dial.rs`（圈层分支已搬到 `dial.rs`），归 LC测试（已同步）。
