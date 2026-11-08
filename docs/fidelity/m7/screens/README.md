# M7 A轮 · 两个符号证据截图（状态命名）

由 `docs/fidelity/orig/` 的原版真机截图裁出「屏幕正中 ±90px」区域，4× 最近邻放大。
全部由 `symbols-detect.py` 的同一判定式验证（见 `../symbols-stage1.md` §1.3）。

| 文件 | 来源 | 状态 | 判定 |
| --- | --- | --- | --- |
| `center-cross-map-2d.png` | `map-home-01.png` | 地图态 2D | **HIT**（红点 137 / 四臂 40,40,40,40） |
| `center-cross-map-3d.png` | `map-3d-mode.png` | 地图态 3D | **HIT**（136 / 41,41,41,40） |
| `center-cross-compass.png` | `compass-main-01.png` | 罗盘态 | **HIT**（138 / 40,41,41,41） |
| `center-cross-absent-sheet.png` | `more-sheet-01.png` | 弹层态 | **NONE**（全 0） |

复跑检测（仓库根目录）：

```powershell
python docs\fidelity\m7\symbols-detect.py --dir E:\APPDetector\fix-p0\docs\fidelity\orig
# 汇总：HIT=21  NEEDLE=1  NONE=33
```

量化规格与环境：`../symbols-stage1.md` §1.2（含色值、尺寸、dp 折算）。
