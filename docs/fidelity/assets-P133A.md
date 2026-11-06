# P1-33-A 剩余 11 项图标：检索结果

来源：原版 APK `E:\APPDetector\input\swss.apk`（sha256 `2fd1fc15…`），解码用 apktool 2.9.3。
落库位置：矢量 `docs/fidelity/assets/icons/drawer/*.xml`（`mode=decoded`，资源 id + 原条目 sha + 解码 sha）；
位图 `docs/fidelity/assets/tools/*.png`。

| # | UI 项 | 结果 | 资源 | 依据 |
|---|---|---|---|---|
| 1 | 全地图工具 | **已确认** | `svg_tool_folder` → `icons/drawer/svg_tool_folder.xml` | 资源名直配；smali 无 `R$drawable` 引用（Compose 侧取资源） |
| 2 | 指北针 | **已确认（位图）** | `mipmap-xxxhdpi/ic_north_needle.png` | 红/灰针形，与右列指北针视觉一致 |
| 3 | 3D | **已确认（位图）** | `mipmap-xxxhdpi/ic_3d_white.png` | 白色立方线稿，与右列 3D 一致 |
| 4 | 罗盘复位 | **已确认** | `svg_compass_reset` | `CompassToolsKt.smali:9507`、`:9620` |
| 5 | 罗盘锁定 | **已确认** | `svg_compass_lock` | 资源名直配（`compass_lock`） |
| 6 | 读盘 | **已确认** | `svg_read_compass` | `CompassToolsKt.smali:9707`（邻近文案「智能读盘」） |
| 7 | 贴地 | **已确认** | `svg_tiedi` | `CompassToolsKt.smali:9805` |
| 8 | 实景 | **候选** | `svg_street` | 资源名直配（street=街景），但右列该按钮在 smali 里无 `R$drawable` 引用，需与截图比对确认 |
| 9 | 坐南朝北 | **候选** | `svg_surround` | 资源名直配（surround=环绕/朝向），同上无引用 |
| 10 | 指针开关 | **候选** | `svg_compass_switch` | `CompassToolsKt.smali:12170` 是罗盘底栏「切换」；右列「指针开关」无独立引用，暂按候选登记 |
| 11 | 地图 | **未确认** | — | 见下方检索清单 |

## 第 11 项（地图）检索清单

已检索、均无同名或语义对应的 drawable/mipmap：

- `res/drawable/svg_*`：`svg_geo_map`、`svg_layer`、`svg_draw_mode_map`、`svg_geo_imagery`、`svg_geo_point`、`svg_geo_info`、`svg_geo_share` —— 名字语义分别是图层/绘制/地理影像/点/信息/分享，**没有「地图」按钮专用图**
- `res/mipmap-*/ic_*`：`ic_geo_map`（折叠地图，出现在搜索结果/POI 场景）、`ic_draw_mode_map`（画笔模式）、`ic_map_point`（红色地标针）——均不对应右列「地图」按钮
- `CompassToolsKt.smali` 中「地图」文案（`\u5730\u56fe`，L10326）±60 行内**无任何 `R$drawable`/`R$mipmap` 引用**，同「实景」「坐南朝北」「指针开关」三处
- `assets/webapp/**`：仅 `cross.png`/`locationPointer.png`/`sprite*.png`（Mapbox sprite，地图要素图标），无工具条按钮

结论：右列这四个按钮的图标是 **Compose 内联绘制**（代码里画路径，不落资源），APK 中没有可直接导出的图。
要 1:1 复刻只能按截图重绘，但章程禁止「手绘/近似替代」，因此第 11 项记 **不可得（资源不存在，代码内联绘制）**，并把上面清单留档。
