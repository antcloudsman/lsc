# 轮 D · B2 文字换表（6/14 圈层）

## 1. 数据源

`app/mechanics.json` 的 `rings[].labels`（780 条，`labels_status=confirmed_dex`，LC逆向 `8368fbd` 逐条地址 `.inspect/derived/ring_label_addrs.json`）。生成器 `tools/mechanics/gen-mechanics.ps1` 现在把 14 个 `RingDef{id,text,interval,offset,labels}` 同时生成进：
- `app/src/main.rs` 的生成区（`pub const RINGS`）
- `CompassData.java` 的生成区（`RingDef` + `RINGS`）

## 2. 读数规则（与逆向手册一致）

```
i     = floor(((h − offset + 360) % 360) / interval)
text  = labels[i]
文字落位 = offset + (i + 0.5) × interval
```
两端实现：Rust `ring_text(type, heading)`、Java `CompassData.ringText(type, heading)`；`type` 为 1..14。

## 3. 生效范围

| 位置 | 改前 | 改后 |
|---|---|---|
| 桌面读数面板 分金/七十二龙/六十龙 | `wangxiang(borrow_branch())` / `JIAZI[]` | `ring_text(6/5/7)` |
| 桌面读数面板 正/兼向 | `"正向/兼向X (±°)"` 自算 | `ring_text(14)` + 偏移角 |
| 桌面/Android 后天卦/先天卦 | 24 山查表（`HOUTIAN_GUA[]`） | `ring_text(2/1)`，含 U+200C 清洗 |
| Android 读数面板 | `r.fenjin/r.dragon72/r.dragon60Name` + 纳音 | `r.fenjinText/r.dragon72Text/r.dragon60Text/r.jianText` |
| Android 八卦环 | 硬编码 8 卦 | `RINGS[1]`（TYPE_2）文字 + 中心角 |

## 4. 验收②（逆向四向量）

`app/src/main.rs` 的 `mechanics_pin::b2_vectors_match_dex_tables`（`cargo test` 绿）：
- 60 龙 0° = `壬子 庚子`；60 龙 170° = `丙午 甲午`；72 龙 0° = `戊子`；120 分金 0° = `甲山 戊子`。

## 5. 验收③（压字可读性）

几何实测：桌面盘面半径 `r=232`（1100×780 减去 300/320 侧栏），120 分金每格 3° → 弧长 10.2px；原版文字 5 字（`甲山 戊子`）横排需 ~32px、竖排也超出 0.025r 的圈带厚度。**当前尺寸下整圈文字必然重叠，无法靠缩字解决**（缩到 4px 已不可读）。

本轮处理：分金/72 龙/60 龙三圈**只画刻度、不画逐格文字**，当前格文字由右侧读数面板给出（与圈层同一张表）。八卦环 8 格仍整圈显示。这与 Android 现状一致（Android 本来就只有刻度）。

**遗留（P0-2 artwork 范畴）**：要恢复原版整圈文字，需要放大盘面（≥ ~1400px 直径）或缩放分级显隐；已并入 M2 的盘面 artwork 任务，不在本轮伪造。

## 6. 证据

- `checks/round-d/desktop-software.png`（1100×780）：0.00° 时 分金 `甲山 戊子`、七十二龙 `戊子`、六十龙 `壬子 庚子`、正/兼向 `癸山九度外`、后天卦 坎、先天卦 坤。
- `cargo test` 4/4（含四向量）。
- `gradle assembleDebug` EXIT=0（APK 3,226,518 B）；真机本轮未验（设备在 LC地图/LC逆向 队列）。

## 7. 当前格高亮（总监裁决补项）

- 桌面：`dial.rs::cell_marker()`，按 `i=ring_index(type,h)`、`center=ring_center(type,i)` 在圈带上画 4px 径向亮线；分金=金、72 龙=绿、60 龙=蓝。
- Android：`CompassView.drawCellMarker()` 同公式，4dp。
- 目的：只画刻度时用户能看出「指针在哪一格」，文字仍以读数面板为准。

## 8. 档案口径（D-008）

原版整圈文字是**整幅位图排版**的结果（`assets/webapp/assets/imgs/compass/`，14 幅 PNG，620–2251px），矢量端在本窗口尺寸（r=232）下**无法 1:1 复现**。因此：
- 矢量圈层 = **等价替代，未达 1:1**；
- 整圈文字的正解是 P0-2：直接使用原版位图盘面（轮 E），矢量层只保留指针/读数/当前格高亮。

## 9. AT-B2 需要的结构化日志

- Android debug 构建：`Log.d("LCRING", "type=.. h=.. i=.. center=.. text=..")`，14 环，节流 2 条/秒（传感器在 UI 速率），`isDebuggable()` 判断，不依赖 BuildConfig。
- 桌面：`luopan.exe --dump-rings <heading>` 输出同样 14 行，便于 6×14 逐字比对。
