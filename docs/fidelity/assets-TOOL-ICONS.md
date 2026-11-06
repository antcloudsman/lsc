# 原版工具图标三层对应（资源名 → APK 条目 → 尺寸/用途）

来源：原版 `space.xinzhi.globe` 1.6.1，APK `E:\APPDetector\input\swss.apk`（sha256 `2fd1fc159c92cbd6fea1b210621eb619dce87b051a8a9275c9330ffb0992bfe8`）。
APK 内 `res/` 条目名被混淆成 `res/6g.png` 这类短名，**不能凭短名判断内容**；本表用「`ToolsEnum` 里的 `sget R$mipmap;-><资源名>` + `mipmap-xxxhdpi` 实际像素 + `more-sheet-03-fresh.xml` 面板文案顺序」三层对齐后写成 `docs/fidelity/assets-sources.json`，由 `tools/fidelity/assets.py` 提取并校验。

| # | 资源名 | APK 条目 | 尺寸 | 字节 | 用途（面板文案/枚举） | ToolsEnum 锚点 |
|---|---|---|---|---|---|---|
| 1 | `ic_tool_compass` | `res/6g.png` | 184x184 | 13400 | 罗盘（`COMPASS`） | `ToolsEnum.smali:155（sget R$mipmap）` |
| 2 | `ic_tool_super_map` | `res/SH.png` | 184x184 | 15614 | 超清地图（`SUPER_MAP`） | `ToolsEnum.smali:179（sget R$mipmap）` |
| 3 | `ic_tool_mountain` | `res/QJ.png` | 184x184 | 13190 | 看山脉（`MOUNTAIN`） | `ToolsEnum.smali:203（sget R$mipmap）` |
| 4 | `ic_tool_water` | `res/vY.png` | 184x184 | 14709 | 看水系（`WATER`） | `ToolsEnum.smali:227（sget R$mipmap）` |
| 5 | `ic_tool_vr` | `res/JE.png` | 184x184 | 12911 | （当前面板未展示：more-sheet-03-fresh.xml 无此项）（`VR`） | `ToolsEnum.smali:251（sget R$mipmap）` |
| 6 | `ic_tool_altitude` | `res/KL.png` | 184x184 | 11328 | 海拔查询（`ALTITUDE`） | `ToolsEnum.smali:275（sget R$mipmap）` |
| 7 | `ic_tool_weather` | `res/vd.png` | 184x184 | 11476 | 天气（`WEATHER`） | `ToolsEnum.smali:299（sget R$mipmap）` |
| 8 | `ic_tool_history` | `res/Fe.png` | 184x184 | 11232 | 历史影像（`HISTORY`） | `ToolsEnum.smali:323（sget R$mipmap）` |
| 9 | `ic_tool_isoline` | `res/pM.png` | 184x184 | 17936 | 等高线（`ISOLINE`） | `ToolsEnum.smali:347（sget R$mipmap）` |
| 10 | `ic_tool_collect` | `res/dd.png` | 184x184 | 12757 | 收藏点（`COLLECT`） | `ToolsEnum.smali:371（sget R$mipmap）` |
| 11 | `ic_tools_street` | `res/5g.png` | 184x184 | 13054 | （当前面板未展示：dump 无「景点」项）（`STREET`） | `ToolsEnum.smali:395（sget R$mipmap）` |
| 12 | `ic_tool_measure` | `res/zv.png` | 184x184 | 15129 | 测量（`MEASURE`） | `ToolsEnum.smali:419（sget R$mipmap）` |
| 13 | `ic_tool_paint` | `res/B2.png` | 184x184 | 14970 | 画笔（`DRAW`） | `ToolsEnum.smali:443（sget R$mipmap）` |
| 14 | `ic_tool_fish` | `res/uU.png` | 184x184 | 14748 | 钓点（`FISH`） | `ToolsEnum.smali:467（sget R$mipmap）` |
| 15 | `ic_tools_track` | `res/fh.png` | 184x184 | 12726 | 轨迹记录（`TRACK_RECORD`） | `ToolsEnum.smali:491（sget R$mipmap）` |
| 16 | `ic_tools_road` | `res/TP.png` | 184x184 | 13893 | 路网（`ROAD`） | `ToolsEnum.smali:515（sget R$mipmap）` |
| 17 | `ic_tools_geographic_imagery` | `res/eS.png` | 184x184 | 14358 | 地理影像（`GEOGRAPHIC_IMAGERY`） | `ToolsEnum.smali:539（sget R$mipmap）` |
| 18 | `ic_tool_super_map_old` | `res/-d.png` | 184x184 | 13465 | 超清优化（`SUPER_MAP_OLD`） | `ToolsEnum.smali:563（sget R$mipmap）` |

说明：`VR`（`ic_tool_vr`）与 `STREET`（`ic_tools_street`）在 `ToolsEnum` 中存在、图标也在包里，但当前「地图工具」面板 dump（`more-sheet-03-fresh.xml`）的 16 项里没有它们，因此用途列标「当前面板未展示」，不臆造对应按钮。

复核命令：

```
python tools\fidelity\assets.py verify --dir docs\fidelity\assets --apk E:\APPDetector\input\swss.apk --strict
```
